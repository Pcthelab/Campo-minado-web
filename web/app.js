const $ = (selector) => document.querySelector(selector);
const icon = (name) => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;
const levelNames = { iniciante: 'Iniciante', intermediario: 'Intermediário', classico: 'Clássico', especialista: 'Especialista' };
const board = $('#board');
let state;
let flagMode = false;
let focusedIndex = 0;
let clockBase = 0;
let clockAt = performance.now();
let queue = Promise.resolve();
let pendingLevel;
let audioContext;
let toastTimeout;
const readStored = (key) => { try { return localStorage.getItem(key); } catch { return null; } };
const writeStored = (key, value) => { try { localStorage.setItem(key, value); } catch { /* O jogo também funciona sem armazenamento. */ } };
let soundEnabled = readStored('campo.som') === 'true';

function formatTime(seconds) {
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

function notify(message) {
  $('#message').textContent = message;
  $('#message').classList.add('visible');
  clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => $('#message').classList.remove('visible'), 6500);
}

async function request(path, post = false) {
  const response = await fetch(path, {
    method: post ? 'POST' : 'GET',
    headers: post ? { 'X-Campo-Minado': '1' } : {},
    signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.erro || 'Não foi possível concluir a jogada.');
  }
  return response.json();
}

function runRequest(path, post = false, effect = null) {
  queue = queue.then(async () => {
    try {
      const next = await request(path, post);
      const old = state;
      render(next);
      $('#message').classList.remove('visible');
      if (effect && (next.abertos !== old?.abertos || next.bandeiras !== old?.bandeiras)) playSound(effect);
    } catch (error) {
      notify(error.name === 'TimeoutError' || error instanceof TypeError
        ? 'Sem conexão com o jogo. Confira se o servidor Java está aberto e recarregue a página.' : error.message);
      // Recupera o estado se a resposta se perdeu depois de uma jogada aplicada.
      try { render(await request('/api/estado')); } catch { board.setAttribute('aria-busy', 'false'); }
    }
  });
  return queue;
}

function render(next) {
  const previous = state;
  const newBoard = !previous || previous.partida !== next.partida;
  state = next;
  clockBase = next.segundos;
  clockAt = performance.now();
  const totalSafe = next.linhas * next.colunas - next.minas;
  const percentage = Math.floor(next.abertos / totalSafe * 100);
  const ended = next.status === 'vitoria' || next.status === 'derrota';
  if (newBoard) {
    board.replaceChildren();
    board.style.setProperty('--cols', next.colunas);
    board.setAttribute('aria-rowcount', next.linhas);
    board.setAttribute('aria-colcount', next.colunas);
    focusedIndex = 0;
    for (let row = 0; row < next.linhas; row++) {
      const rowElement = document.createElement('div');
      rowElement.setAttribute('role', 'row');
      rowElement.style.display = 'contents';
      for (let col = 0; col < next.colunas; col++) {
        const index = row * next.colunas + col;
        const button = document.createElement('button');
        button.className = 'cell';
        button.dataset.index = index;
        button.setAttribute('role', 'gridcell');
        button.setAttribute('aria-rowindex', row + 1);
        button.setAttribute('aria-colindex', col + 1);
        button.tabIndex = index === 0 ? 0 : -1;
        rowElement.append(button);
      }
      board.append(rowElement);
    }
    $('#result-dialog').close();
    $('#board-scroll').scrollLeft = 0;
  }
  const buttons = board.querySelectorAll('.cell');
  next.campos.forEach((cell, index) => {
    const button = buttons[index];
    const old = !newBoard && previous.campos[index];
    const classes = ['cell'];
    let content = '';
    let description = 'fechada';
    if (cell.aberto) {
      classes.push('opened');
      if (cell.mina) { classes.push('mine'); content = icon('mine'); description = 'mina'; }
      else { classes.push(`number-${cell.numero}`); content = cell.numero || ''; description = cell.numero ? `${cell.numero} minas ao redor` : 'vazia'; }
      if (old && !old.aberto) classes.push('just-opened');
    }
    if (cell.marcado) { classes.push('flagged'); content = icon('flag'); description = 'com bandeira'; }
    if (ended && cell.marcado && !cell.mina) { classes.push('wrong-flag'); description = 'bandeira incorreta'; }
    if (cell.explosao) { classes.push('exploded'); description = 'mina detonada'; }
    button.className = classes.join(' ');
    if (button.innerHTML !== String(content)) button.innerHTML = content;
    button.setAttribute('aria-label', `Linha ${Math.floor(index / next.colunas) + 1}, coluna ${index % next.colunas + 1}: ${description}`);
    button.setAttribute('aria-disabled', ended || cell.aberto ? 'true' : 'false');
  });
  board.setAttribute('aria-busy', 'false');
  document.querySelectorAll('.level').forEach(button => {
    const active = button.dataset.level === next.nivel;
    button.classList.toggle('active', active);
    button.setAttribute('aria-pressed', active);
  });
  $('#level-title').textContent = levelNames[next.nivel];
  $('#dimensions').textContent = `${next.linhas} × ${next.colunas}`;
  $('#remaining').textContent = String(next.minas - next.bandeiras).padStart(3, '0');
  $('#flag-count').textContent = `${next.bandeiras} ${next.bandeiras === 1 ? 'bandeira' : 'bandeiras'}`;
  $('#progress-number').innerHTML = `${percentage}<span>%</span>`;
  $('#open-count').textContent = `${next.abertos} / ${totalSafe} casas`;
  $('#progress-fill').style.width = `${percentage}%`;
  $('.progress-track').setAttribute('aria-valuenow', percentage);
  $('#status-label').textContent = { pronto: 'PRONTO PARA COMEÇAR', jogando: 'PARTIDA EM ANDAMENTO', vitoria: 'CAMPO LIMPO!', derrota: 'MINA ENCONTRADA' }[next.status];
  $('#play-status').classList.toggle('lost', next.status === 'derrota');
  $('#timer-note').textContent = ended ? 'Finalizado.' : 'Sem pressa.';
  $('#board-hint').innerHTML = icon(ended ? (next.status === 'vitoria' ? 'check' : 'mine') : 'spark') +
    (next.status === 'vitoria' ? 'Boa! Todas as minas foram isoladas.' : next.status === 'derrota' ? 'Respira. A próxima partida te espera.' : next.status === 'pronto' ? 'O primeiro clique é sempre seguro.' : percentage === 100 ? 'Agora marque todas as minas para vencer.' : 'Confie nas pistas. Cada número conta.');
  updateClock();
  if (ended && (!previous || previous.partida !== next.partida || previous.status !== next.status)) {
    showResult(percentage);
  }
  updateRecord();
}

function updateClock() {
  const seconds = clockBase + (state?.status === 'jogando' ? Math.floor((performance.now() - clockAt) / 1000) : 0);
  $('#timer').textContent = formatTime(seconds);
}
setInterval(updateClock, 250);

function updateRecord() {
  const saved = readStored(`campo.recorde.${state.nivel}`);
  const best = saved === null ? null : Number(saved);
  const valid = best !== null && Number.isFinite(best) && best >= 0;
  $('#best-time').textContent = valid ? formatTime(best) : '— — : — —';
  $('#best-caption').textContent = valid ? `Seu recorde no modo ${levelNames[state.nivel].toLowerCase()}.` : 'Sua primeira vitória começa aqui.';
}

function showResult(percentage) {
  const won = state.status === 'vitoria';
  if (won) {
    const key = `campo.recorde.${state.nivel}`;
    const previous = readStored(key);
    if (previous === null || state.segundos < Number(previous)) writeStored(key, String(state.segundos));
  }
  const dialog = $('#result-dialog');
  dialog.classList.toggle('loss', !won);
  $('#result-icon').innerHTML = icon(won ? 'trophy' : 'mine');
  $('#result-eyebrow').textContent = won ? 'ESTRATÉGIA QUE DEU CERTO' : 'FAZ PARTE DO JOGO';
  $('#result-title').textContent = won ? 'Campo limpo. Mente afiada.' : 'Essa mina estava no caminho.';
  $('#result-description').textContent = won ? 'Todas as casas seguras abertas. Todas as minas marcadas. Que tal mais um desafio?' : 'O tabuleiro guarda uma nova chance. Respire fundo e tente outra vez.';
  $('#result-time').textContent = formatTime(state.segundos);
  $('#result-progress').textContent = `${percentage}%`;
  dialog.showModal();
  playSound(won ? 'win' : 'lose');
}

function act(index, marking = flagMode) {
  if (!state || !['pronto', 'jogando'].includes(state.status)) return;
  unlockSound();
  focusCell(index, false);
  const params = new URLSearchParams({ linha: Math.floor(index / state.colunas), coluna: index % state.colunas, partida: state.partida });
  runRequest(`/api/${marking ? 'marcar' : 'abrir'}?${params}`, true, marking ? 'flag' : 'open');
}

function focusCell(index, moveFocus = true) {
  const old = board.querySelector(`[data-index="${focusedIndex}"]`);
  const next = board.querySelector(`[data-index="${index}"]`);
  if (!next) return;
  if (old) old.tabIndex = -1;
  focusedIndex = index;
  next.tabIndex = 0;
  if (moveFocus) next.focus({ preventScroll: true });
  if (moveFocus) next.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}

board.addEventListener('click', event => {
  const button = event.target.closest('.cell');
  if (button) act(Number(button.dataset.index));
});
board.addEventListener('contextmenu', event => {
  event.preventDefault();
  const button = event.target.closest('.cell');
  if (button) act(Number(button.dataset.index), true);
});
board.addEventListener('keydown', event => {
  if (!state) return;
  const index = Number(event.target.closest('.cell')?.dataset.index);
  if (!Number.isInteger(index)) return;
  const row = Math.floor(index / state.colunas);
  const col = index % state.colunas;
  let target;
  if (event.key === 'ArrowLeft') target = row * state.colunas + Math.max(0, col - 1);
  if (event.key === 'ArrowRight') target = row * state.colunas + Math.min(state.colunas - 1, col + 1);
  if (event.key === 'ArrowUp') target = Math.max(0, row - 1) * state.colunas + col;
  if (event.key === 'ArrowDown') target = Math.min(state.linhas - 1, row + 1) * state.colunas + col;
  if (event.key === 'Home') target = row * state.colunas;
  if (event.key === 'End') target = (row + 1) * state.colunas - 1;
  if (target !== undefined) { event.preventDefault(); focusCell(target); }
  if (event.key.toLowerCase() === 'f') { event.preventDefault(); event.stopPropagation(); act(index, true); }
});

function setMode(marking) {
  flagMode = marking;
  $('#mode-open').classList.toggle('selected', !marking);
  $('#mode-open').setAttribute('aria-pressed', !marking);
  $('#mode-flag').classList.toggle('selected', marking);
  $('#mode-flag').setAttribute('aria-pressed', marking);
}
$('#mode-open').addEventListener('click', () => setMode(false));
$('#mode-flag').addEventListener('click', () => setMode(true));

function newGame(level = state?.nivel || 'classico') {
  $('#restart-dialog').close();
  $('#result-dialog').close();
  setMode(false);
  runRequest(`/api/novo?nivel=${encodeURIComponent(level)}`, true);
}
function askRestart(level) {
  pendingLevel = level || state?.nivel || 'classico';
  if (state && (state.status === 'jogando' || state.status === 'pronto' && state.bandeiras > 0)) $('#restart-dialog').showModal();
  else newGame(pendingLevel);
}
$('#new-game').addEventListener('click', () => askRestart());
document.querySelectorAll('.level').forEach(button => button.addEventListener('click', () => {
  if (button.dataset.level !== state?.nivel) askRestart(button.dataset.level);
}));
$('#cancel-restart').addEventListener('click', () => $('#restart-dialog').close());
$('#confirm-restart').addEventListener('click', () => newGame(pendingLevel));
$('#play-again').addEventListener('click', () => newGame());
$('#view-board').addEventListener('click', () => $('#result-dialog').close());
$('#help').addEventListener('click', () => $('#help-dialog').showModal());
$('.close-help').addEventListener('click', () => $('#help-dialog').close());
document.querySelectorAll('.close-dialog').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
document.addEventListener('keydown', event => {
  if (event.ctrlKey || event.altKey || event.metaKey || event.repeat || document.querySelector('dialog[open]')) return;
  if (event.key.toLowerCase() === 'r') { event.preventDefault(); askRestart(); }
  if (event.key.toLowerCase() === 'f') { event.preventDefault(); setMode(!flagMode); }
  if (event.key === '?') $('#help-dialog').showModal();
});
document.addEventListener('visibilitychange', () => { if (!document.hidden) runRequest('/api/estado'); });

function updateSoundButton() {
  $('#sound').setAttribute('aria-pressed', soundEnabled);
  $('#sound').setAttribute('aria-label', soundEnabled ? 'Desativar sons' : 'Ativar sons');
  $('#sound').title = soundEnabled ? 'Desativar sons' : 'Ativar sons';
  $('#sound-label').textContent = soundEnabled ? 'Som ligado' : 'Som desligado';
}
function unlockSound() {
  if (!soundEnabled) return;
  try {
    audioContext ||= new AudioContext();
    if (audioContext.state === 'suspended') audioContext.resume().catch(() => {});
  } catch { /* Áudio é opcional. */ }
}
function playSound(kind) {
  if (!soundEnabled || !audioContext) return;
  try {
    const notes = { open: [480], flag: [680], win: [523, 659, 784, 1047], lose: [220, 165, 110] }[kind];
    notes.forEach((frequency, index) => {
      const oscillator = audioContext.createOscillator();
      const gain = audioContext.createGain();
      const start = audioContext.currentTime + index * .12;
      oscillator.type = 'sine';
      oscillator.frequency.value = frequency;
      gain.gain.setValueAtTime(0, start);
      gain.gain.linearRampToValueAtTime(.055, start + .01);
      gain.gain.exponentialRampToValueAtTime(.001, start + .17);
      oscillator.connect(gain).connect(audioContext.destination);
      oscillator.start(start);
      oscillator.stop(start + .18);
    });
  } catch { /* Falhas de áudio não interrompem as jogadas. */ }
}
$('#sound').addEventListener('click', () => {
  soundEnabled = !soundEnabled;
  writeStored('campo.som', String(soundEnabled));
  unlockSound();
  updateSoundButton();
  playSound('flag');
});
updateSoundButton();
runRequest('/api/estado');
