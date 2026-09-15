package modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Testes de regressão executáveis apenas com o JDK. */
public final class TabuleiroTeste {
    private static int verificacoes;

    public static void main(String[] args) {
        vizinhanca();
        bandeirasECascata();
        sorteioEPrimeiroClique();
        vitoria();
        derrotaEReinicio();
        parametros();
        System.out.println("OK: " + verificacoes + " verificações do modelo.");
    }

    private static void verificar(boolean condicao, String descricao) {
        verificacoes++;
        if (!condicao) throw new AssertionError(descricao);
    }

    private static List<Campo> campos(Tabuleiro tabuleiro) {
        List<Campo> resultado = new ArrayList<>();
        tabuleiro.paraCadaCampo(resultado::add);
        return resultado;
    }

    private static Tabuleiro fixo() {
        Tabuleiro tabuleiro = new Tabuleiro(3, 3, 1);
        tabuleiro.paraCadaCampo(Campo::desminar);
        tabuleiro.getCampo(2, 2).minar();
        return tabuleiro;
    }

    private static void vizinhanca() {
        Tabuleiro tabuleiro = fixo();
        verificar(tabuleiro.getCampo(1, 1).minasNaVizinhanca() == 1, "Conta mina diagonal");
        verificar(tabuleiro.getCampo(1, 2).minasNaVizinhanca() == 1, "Conta mina ortogonal");
        verificar(tabuleiro.getCampo(0, 0).minasNaVizinhanca() == 0, "Não conta mina distante");
        verificar(!tabuleiro.getCampo(0, 0).adicionarVizinho(tabuleiro.getCampo(0, 0)), "Não é vizinho de si mesmo");
    }

    private static void bandeirasECascata() {
        Tabuleiro tabuleiro = fixo();
        List<CampoEvento> eventos = new ArrayList<>();
        tabuleiro.getCampo(0, 0).registrarObservador((c, e) -> eventos.add(e));
        tabuleiro.alternarMarcacao(0, 0);
        tabuleiro.abrir(0, 0);
        verificar(!tabuleiro.isIniciado(), "Bandeira bloqueia a primeira abertura");
        verificar(!tabuleiro.getCampo(0, 0).isAberto(), "Casa marcada não abre");
        tabuleiro.alternarMarcacao(0, 0);
        verificar(eventos.equals(List.of(CampoEvento.MARCAR, CampoEvento.DESMARCAR)), "Observer preserva marcação e desmarcação");
        tabuleiro.alternarMarcacao(0, 1);
        tabuleiro.abrir(0, 0);
        verificar(campos(tabuleiro).stream().filter(Campo::isAberto).count() == 5, "Abertura em cascata respeita bandeiras e fronteiras numeradas");
        verificar(!tabuleiro.getCampo(0, 1).isAberto(), "Cascata não abre casa marcada");
        tabuleiro.alternarMarcacao(0, 1);
        tabuleiro.abrir(0, 1);
        verificar(campos(tabuleiro).stream().filter(Campo::isAberto).count() == 8, "Remover bandeira permite abrir a região restante");
        tabuleiro.alternarMarcacao(0, 0);
        verificar(!tabuleiro.getCampo(0, 0).isMarcado(), "Casa aberta não aceita bandeira");
    }

    private static void sorteioEPrimeiroClique() {
        for (int tentativa = 0; tentativa < 50; tentativa++) {
            Tabuleiro tabuleiro = new Tabuleiro(16, 30, 50);
            verificar(campos(tabuleiro).stream().filter(Campo::isMinado).count() == 50, "Sorteia exatamente 50 minas");
            Campo mina = campos(tabuleiro).stream().filter(Campo::isMinado).findFirst().orElseThrow();
            tabuleiro.abrir(mina.getLinha(), mina.getColuna());
            verificar(mina.isAberto() && !mina.isMinado() && !tabuleiro.isEncerrado(), "Primeiro clique em mina é protegido");
            verificar(campos(tabuleiro).stream().filter(Campo::isMinado).count() == 50, "Proteção mantém quantidade de minas");
        }
        Tabuleiro vazio = new Tabuleiro(3, 3, 0);
        verificar(campos(vazio).stream().noneMatch(Campo::isMinado), "Zero minas não sorteia uma mina acidental");
        vazio.abrir(0, 0);
        verificar(vazio.isGanhou(), "Tabuleiro sem minas abre em cascata e vence");
    }

    private static void vitoria() {
        Tabuleiro tabuleiro = fixo();
        AtomicInteger eventos = new AtomicInteger();
        tabuleiro.registrarObservador(e -> { verificar(e.isGanhou(), "Notifica vitória"); eventos.incrementAndGet(); });
        tabuleiro.abrir(0, 0);
        verificar(!tabuleiro.isEncerrado(), "Regra original exige bandeiras além das casas abertas");
        tabuleiro.alternarMarcacao(2, 2);
        verificar(tabuleiro.isGanhou() && tabuleiro.objetivoAlcado(), "Reconhece vitória");
        tabuleiro.alternarMarcacao(2, 2);
        tabuleiro.abrir(2, 2);
        verificar(tabuleiro.getCampo(2, 2).isMarcado(), "Bloqueia alterações após vitória");
        verificar(eventos.get() == 1, "Emite vitória uma única vez");
    }

    private static void derrotaEReinicio() {
        Tabuleiro tabuleiro = fixo();
        AtomicInteger eventos = new AtomicInteger();
        tabuleiro.registrarObservador(e -> { verificar(!e.isGanhou(), "Notifica derrota"); eventos.incrementAndGet(); });
        tabuleiro.abrir(1, 1);
        tabuleiro.abrir(2, 2);
        verificar(tabuleiro.isEncerrado() && !tabuleiro.isGanhou(), "Mina encerra a partida");
        verificar(tabuleiro.isExplosao(tabuleiro.getCampo(2, 2)), "Registra mina detonada");
        verificar(tabuleiro.getCampo(2, 2).isAberto(), "Revela a mina");
        tabuleiro.abrir(0, 0);
        tabuleiro.alternarMarcacao(0, 0);
        verificar(!tabuleiro.getCampo(0, 0).isAberto() && !tabuleiro.getCampo(0, 0).isMarcado(), "Bloqueia jogadas após derrota");
        verificar(eventos.get() == 1, "Emite derrota uma única vez");
        tabuleiro.reiniciar();
        verificar(!tabuleiro.isEncerrado() && !tabuleiro.isIniciado(), "Reinício limpa estado da partida");
        verificar(campos(tabuleiro).stream().noneMatch(c -> c.isAberto() || c.isMarcado()), "Reinício limpa as casas");
        verificar(campos(tabuleiro).stream().filter(Campo::isMinado).count() == 1, "Reinício preserva quantidade de minas");
        verificar(eventos.get() == 1, "Reinício não emite resultados espúrios");
    }

    private static void parametros() {
        int[][] invalidos = {{0, 3, 1}, {3, -1, 1}, {3, 3, -1}, {3, 3, 9}, {101, 3, 1}};
        for (int[] entrada : invalidos) {
            boolean falhou = false;
            try { new Tabuleiro(entrada[0], entrada[1], entrada[2]); } catch (IllegalArgumentException e) { falhou = true; }
            verificar(falhou, "Rejeita parâmetros inválidos");
        }
        boolean falhou = false;
        try { fixo().abrir(3, 0); } catch (IllegalArgumentException e) { falhou = true; }
        verificar(falhou, "Rejeita coordenadas inválidas");
    }
}
