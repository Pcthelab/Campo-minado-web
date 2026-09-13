# Campo Minado (Java Swing) 

Projeto finalizado que implementa o clássico jogo Campo Minado com uma interface gráfica completa em Java Swing e aplicação do padrão de projeto Observer.

##  O Jogo em Ação

O jogo conta com um tabuleiro dinâmico e interações completas de cliques para abrir campos e marcar bandeiras:


![](src/IMG/PADRÃO.png)

###  Tela de Derrota (Personalizada)
Se você bobear e clicar em uma mina... já sabe!
![Mensagem de derrota](src/IMG/LOSS.png)

###  Tela de Vitória
O momento glorioso de limpar o tabuleiro e isolar todas as bombas:
![Tela de vitória](src/IMG/WIN.png)

---

##  Tecnologias Utilizadas
* **Java 26** 
* **Java Swing** (Interface Gráfica)

##  Conceitos Praticados
* **Padrão de Projeto Observer:** Utilizado para conectar a lógica do tabuleiro com os botões da tela, garantindo que a interface gráfica reaja instantaneamente a cada clique ou alteração de estado.
* **Expressões Lambda & Streams:** Aplicação de programação funcional para percorrer a matriz de campos e filtrar os vizinhos com um código muito mais limpo e moderno.
* **Tratamento de Eventos (Event Handling):** Gerenciamento de cliques do mouse (esquerdo para abrir, direito para colocar marcação).
