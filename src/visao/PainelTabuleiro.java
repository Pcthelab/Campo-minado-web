package visao;

import modelo.Tabuleiro;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("serial")
public class PainelTabuleiro extends JPanel {

    public PainelTabuleiro(Tabuleiro tabuleiro) {
        setLayout(new GridLayout(tabuleiro.getLinhas(), tabuleiro.getColunas()));

        tabuleiro.paraCadaCampo(c -> add(new BotaoCampo(c)));
        tabuleiro.registrarObservador(e -> {
            SwingUtilities.invokeLater(() -> {
                if (e.isGanhou()) {
                    JOptionPane.showMessageDialog(
                            this,
                            "GANHOU! :)",
                            "Vitoria",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(
                            this,
                            "FOI DE VASCO, TENTE NOVAMENTE BOCA ABERTA :(",
                            "Derrota",
                            JOptionPane.ERROR_MESSAGE);
                }

                tabuleiro.reiniciar();
            });
        });
    }
}
