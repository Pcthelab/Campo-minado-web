package modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class Tabuleiro implements CampoObservador {
    private final int linhas;
    private final int colunas;
    private final int minas;
    private final List<Campo> campos = new ArrayList<>();
    private final List<Consumer<ResultadoEvento>> observadores = new ArrayList<>();
    private boolean encerrado;
    private boolean ganhou;
    private boolean primeiraAbertura = true;
    private Campo explosao;

    public Tabuleiro(int linhas, int colunas, int minas) {
        if (linhas < 1 || colunas < 1 || linhas > 100 || colunas > 100
                || minas < 0 || minas >= linhas * colunas) {
            throw new IllegalArgumentException("Dimensões ou quantidade de minas inválidas.");
        }
        this.linhas = linhas;
        this.colunas = colunas;
        this.minas = minas;
        gerarCampos();
        associarOsVizinhos();
        sortearMinas();
    }

    public void paraCadaCampo(Consumer<Campo> funcao) { campos.forEach(funcao); }
    public void registrarObservador(Consumer<ResultadoEvento> observador) { observadores.add(observador); }

    private void notificarObservadores(boolean resultado) {
        observadores.forEach(o -> o.accept(new ResultadoEvento(resultado)));
    }

    public Campo getCampo(int linha, int coluna) {
        if (linha < 0 || linha >= linhas || coluna < 0 || coluna >= colunas) {
            throw new IllegalArgumentException("Campo fora do tabuleiro.");
        }
        return campos.get(linha * colunas + coluna);
    }

    public void abrir(int linha, int coluna) {
        Campo campo = getCampo(linha, coluna);
        if (encerrado || campo.isAberto() || campo.isMarcado()) return;
        // O primeiro clique sempre é seguro; a quantidade de minas é preservada.
        if (primeiraAbertura) {
            if (campo.isMinado()) {
                List<Campo> destinos = new ArrayList<>();
                campos.stream().filter(c -> c != campo && !c.isMinado()).forEach(destinos::add);
                Collections.shuffle(destinos);
                campo.desminar();
                destinos.get(0).minar();
            }
            primeiraAbertura = false;
        }
        campo.abrir();
    }

    public void alternarMarcacao(int linha, int coluna) {
        Campo campo = getCampo(linha, coluna);
        if (!encerrado) campo.alternarMarcacao();
    }

    private void gerarCampos() {
        for (int linha = 0; linha < linhas; linha++) {
            for (int coluna = 0; coluna < colunas; coluna++) {
                Campo campo = new Campo(linha, coluna);
                campo.registrarObservador(this);
                campos.add(campo);
            }
        }
    }

    private void associarOsVizinhos() {
        for (Campo campo : campos) {
            for (int dl = -1; dl <= 1; dl++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int l = campo.getLinha() + dl;
                    int c = campo.getColuna() + dc;
                    if ((dl != 0 || dc != 0) && l >= 0 && l < linhas && c >= 0 && c < colunas) {
                        campo.adicionarVizinho(getCampo(l, c));
                    }
                }
            }
        }
    }

    private void sortearMinas() {
        List<Campo> sorteados = new ArrayList<>(campos);
        Collections.shuffle(sorteados);
        sorteados.stream().limit(minas).forEach(Campo::minar);
    }

    // Regra original: abrir os campos seguros E marcar todas as minas.
    public boolean objetivoAlcado() { return campos.stream().allMatch(Campo::objetivoAlcado); }

    public void reiniciar() {
        encerrado = true;
        campos.forEach(Campo::reiniciar);
        sortearMinas();
        ganhou = false;
        primeiraAbertura = true;
        explosao = null;
        encerrado = false;
    }

    public int getLinhas() { return linhas; }
    public int getColunas() { return colunas; }
    public int getMinas() { return minas; }
    public boolean isEncerrado() { return encerrado; }
    public boolean isGanhou() { return ganhou; }
    public boolean isIniciado() { return !primeiraAbertura; }
    public boolean isExplosao(Campo campo) { return campo == explosao; }

    @Override
    public void eventoOcorreu(Campo campo, CampoEvento evento) {
        if (encerrado || evento == CampoEvento.REINICIAR) return;
        if (evento == CampoEvento.EXPLODIR) {
            encerrado = true;
            explosao = campo;
            campos.stream().filter(Campo::isMinado).filter(c -> !c.isMarcado())
                    .forEach(c -> c.setAberto(true));
            notificarObservadores(false);
        } else if (objetivoAlcado()) {
            encerrado = true;
            ganhou = true;
            notificarObservadores(true);
        }
    }
}
