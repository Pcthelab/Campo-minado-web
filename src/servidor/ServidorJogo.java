package servidor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import modelo.Tabuleiro;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Servidor local, sem dependências externas e sem componentes gráficos Java. */
public final class ServidorJogo {
    private Tabuleiro tabuleiro = new Tabuleiro(16, 30, 50);
    private String dificuldade = "classico";
    private long inicio;
    private long fim;
    private long partida = System.currentTimeMillis();
    private final Path web;

    private ServidorJogo(Path web) { this.web = web.toAbsolutePath().normalize(); }

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        ServidorJogo jogo = new ServidorJogo(Path.of(System.getProperty("campo.web", "web")));
        if (!Files.isRegularFile(jogo.web.resolve("index.html"))) {
            throw new IOException("Pasta web não encontrada. Execute na raiz do projeto ou configure -Dcampo.web.");
        }
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", porta), 0);
        servidor.createContext("/", jogo::atender);
        servidor.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> servidor.stop(0)));
        System.out.println("Campo Minado pronto! Abra http://127.0.0.1:" + servidor.getAddress().getPort());
        System.out.println("Pressione Ctrl+C para encerrar.");
    }

    private synchronized void atender(HttpExchange requisicao) throws IOException {
        try {
            requisicao.getResponseHeaders().set("Cache-Control", "no-store");
            requisicao.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            requisicao.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'");
            String caminho = requisicao.getRequestURI().getPath();
            if (caminho.startsWith("/api/")) {
                api(requisicao, caminho);
            } else {
                if (!"GET".equals(requisicao.getRequestMethod())) {
                    responder(requisicao, 405, "text/plain", "Método não permitido.");
                    return;
                }
                // Apenas os arquivos públicos conhecidos são servidos.
                String arquivo = switch (caminho) {
                    case "/", "/index.html" -> "index.html";
                    case "/style.css" -> "style.css";
                    case "/app.js" -> "app.js";
                    case "/favicon.svg" -> "favicon.svg";
                    default -> null;
                };
                if (arquivo == null) {
                    responder(requisicao, 404, "text/plain", "Não encontrado.");
                    return;
                }
                String tipo = arquivo.endsWith("css") ? "text/css" : arquivo.endsWith("js")
                        ? "text/javascript" : arquivo.endsWith("svg") ? "image/svg+xml" : "text/html";
                responder(requisicao, 200, tipo, Files.readString(web.resolve(arquivo), StandardCharsets.UTF_8));
            }
        } catch (IllegalArgumentException e) {
            responder(requisicao, 400, "application/json", "{\"erro\":\"Parâmetros inválidos.\"}");
        } catch (Exception e) {
            System.err.println("Falha ao atender requisição: " + e.getMessage());
            responder(requisicao, 500, "application/json", "{\"erro\":\"Não foi possível concluir a jogada.\"}");
        } finally {
            requisicao.close();
        }
    }

    private void api(HttpExchange requisicao, String caminho) throws IOException {
        if (caminho.equals("/api/estado")) {
            if (!requisicao.getRequestMethod().equals("GET")) {
                responder(requisicao, 405, "application/json", "{\"erro\":\"Use GET.\"}");
                return;
            }
        } else {
            if (!requisicao.getRequestMethod().equals("POST")) {
                responder(requisicao, 405, "application/json", "{\"erro\":\"Use POST.\"}");
                return;
            }
            // Cabeçalho não simples impede comandos vindos de outros sites via navegador.
            String origem = requisicao.getRequestHeaders().getFirst("Origin");
            String origemLocal = "http://" + requisicao.getRequestHeaders().getFirst("Host");
            if (!"1".equals(requisicao.getRequestHeaders().getFirst("X-Campo-Minado"))
                    || (origem != null && !origem.equals(origemLocal))) {
                responder(requisicao, 403, "application/json", "{\"erro\":\"Origem não permitida.\"}");
                return;
            }
            Map<String, String> parametros = parametros(requisicao.getRequestURI().getRawQuery());
            switch (caminho) {
                case "/api/novo" -> novo(parametros.getOrDefault("nivel", dificuldade));
                case "/api/abrir", "/api/marcar" -> {
                    // Não aplica cliques atrasados a uma nova partida.
                    if (Long.parseLong(parametros.getOrDefault("partida", "0")) != partida) {
                        responder(requisicao, 409, "application/json", "{\"erro\":\"A partida mudou. Tente novamente.\"}");
                        return;
                    }
                    int linha = Integer.parseInt(parametros.getOrDefault("linha", "-1"));
                    int coluna = Integer.parseInt(parametros.getOrDefault("coluna", "-1"));
                    if (caminho.equals("/api/abrir")) {
                        boolean iniciado = tabuleiro.isIniciado();
                        tabuleiro.abrir(linha, coluna);
                        if (!iniciado && tabuleiro.isIniciado()) inicio = System.nanoTime();
                    } else {
                        tabuleiro.alternarMarcacao(linha, coluna);
                    }
                    if (tabuleiro.isEncerrado() && fim == 0) fim = System.nanoTime();
                }
                default -> {
                    responder(requisicao, 404, "application/json", "{\"erro\":\"Rota não encontrada.\"}");
                    return;
                }
            }
        }
        responder(requisicao, 200, "application/json", estado());
    }

    private void novo(String nivel) {
        Tabuleiro novo = switch (nivel) {
            case "iniciante" -> new Tabuleiro(9, 9, 10);
            case "intermediario" -> new Tabuleiro(16, 16, 40);
            case "classico" -> new Tabuleiro(16, 30, 50);
            case "especialista" -> new Tabuleiro(16, 30, 99);
            default -> throw new IllegalArgumentException("Nível inválido.");
        };
        tabuleiro = novo;
        dificuldade = nivel;
        inicio = fim = 0;
        partida++;
    }

    private String estado() {
        StringBuilder casas = new StringBuilder();
        int[] contagem = new int[2];
        tabuleiro.paraCadaCampo(c -> {
            if (c.isMarcado()) contagem[0]++;
            if (c.isAberto() && !c.isMinado()) contagem[1]++;
            if (!casas.isEmpty()) casas.append(',');
            casas.append("{\"aberto\":").append(c.isAberto())
                    .append(",\"marcado\":").append(c.isMarcado())
                    .append(",\"mina\":").append(c.isMinado() && (c.isAberto() || tabuleiro.isEncerrado()))
                    .append(",\"explosao\":").append(tabuleiro.isExplosao(c))
                    .append(",\"numero\":").append(c.isAberto() && !c.isMinado() ? c.minasNaVizinhanca() : 0)
                    .append('}');
        });
        long segundos = inicio == 0 ? 0 : ((fim == 0 ? System.nanoTime() : fim) - inicio) / 1_000_000_000;
        String status = tabuleiro.isEncerrado() ? (tabuleiro.isGanhou() ? "vitoria" : "derrota")
                : tabuleiro.isIniciado() ? "jogando" : "pronto";
        return "{\"partida\":" + partida + ",\"nivel\":\"" + dificuldade + "\",\"linhas\":" + tabuleiro.getLinhas()
                + ",\"colunas\":" + tabuleiro.getColunas() + ",\"minas\":" + tabuleiro.getMinas()
                + ",\"bandeiras\":" + contagem[0] + ",\"abertos\":" + contagem[1]
                + ",\"segundos\":" + segundos + ",\"status\":\"" + status + "\",\"campos\":[" + casas + "]}";
    }

    private static Map<String, String> parametros(String query) {
        Map<String, String> resultado = new HashMap<>();
        if (query != null) {
            for (String parte : query.split("&")) {
                String[] par = parte.split("=", 2);
                if (par.length == 2) resultado.put(par[0], URLDecoder.decode(par[1], StandardCharsets.UTF_8));
            }
        }
        return resultado;
    }

    private static void responder(HttpExchange requisicao, int status, String tipo, String conteudo) throws IOException {
        byte[] bytes = conteudo.getBytes(StandardCharsets.UTF_8);
        requisicao.getResponseHeaders().set("Content-Type", tipo + "; charset=utf-8");
        requisicao.sendResponseHeaders(status, bytes.length);
        requisicao.getResponseBody().write(bytes);
    }
}
