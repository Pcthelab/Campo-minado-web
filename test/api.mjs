import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const server = spawn('java', ['-cp', 'out/test', 'visao.TelaPrincipal', '0'], { cwd: root, windowsHide: true });
let base;
let output = '';
try {
  base = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Servidor não iniciou em 10 segundos. Execute testar.ps1 antes.')), 10000);
    server.on('error', error => { clearTimeout(timeout); reject(error); });
    server.on('exit', code => { clearTimeout(timeout); reject(new Error(`Servidor encerrou: ${code}. ${output}`)); });
    server.stderr.on('data', data => { output += data; });
    server.stdout.on('data', data => {
      output += data;
      const match = output.match(/http:\/\/127\.0\.0\.1:\d+/);
      if (match) { clearTimeout(timeout); resolve(match[0]); }
    });
  });
  const post = async (path) => {
    const response = await fetch(base + path, { method: 'POST', headers: { 'X-Campo-Minado': '1' } });
    assert.equal(response.status, 200, path);
    return response.json();
  };
  const getState = async () => (await fetch(base + '/api/estado')).json();
  const action = (name, index, state) => post(`/api/${name}?linha=${Math.floor(index / state.colunas)}&coluna=${index % state.colunas}&partida=${state.partida}`);
  let state = await getState();
  assert.equal(state.minas, 50);
  assert.equal(state.campos.length, 480);
  assert(state.campos.every(c => !c.mina && c.numero === 0 && !c.aberto));
  for (const path of ['/', '/style.css', '/app.js', '/favicon.svg']) assert.equal((await fetch(base + path)).status, 200);
  assert.equal((await fetch(base + '/src/modelo/Campo.java')).status, 404);
  assert.equal((await fetch(base + '/api/novo', { method: 'POST' })).status, 403);
  assert.equal((await fetch(base + '/api/novo')).status, 405);
  assert.equal((await fetch(base + '/api/novo', { method: 'POST', headers: { 'X-Campo-Minado': '1', Origin: 'https://example.com' } })).status, 403);
  for (const [nivel, count, mines] of [['iniciante', 81, 10], ['intermediario', 256, 40], ['classico', 480, 50], ['especialista', 480, 99]]) {
    state = await post(`/api/novo?nivel=${nivel}`);
    assert.equal(state.campos.length, count);
    assert.equal(state.minas, mines);
    assert.equal(state.status, 'pronto');
  }
  state = await post('/api/novo?nivel=iniciante');
  const previousId = state.partida;
  state = await action('marcar', 0, state);
  assert.equal(state.bandeiras, 1);
  state = await action('abrir', 0, state);
  assert.equal(state.abertos, 0);
  assert.equal(state.status, 'pronto');
  state = await action('marcar', 0, state);
  assert.equal(state.bandeiras, 0);
  state = await action('abrir', 0, state);
  assert.equal(state.status, 'jogando');
  assert(state.campos[0].aberto && !state.campos[0].mina);
  assert(state.campos.filter(c => !c.aberto).every(c => !c.mina && c.numero === 0));
  const refreshed = await getState();
  assert.equal(refreshed.partida, state.partida);
  assert.deepEqual(refreshed.campos, state.campos);
  for (let i = 0; i < state.campos.length && state.status !== 'derrota'; i++) {
    if (!state.campos[i].aberto) state = await action('abrir', i, state);
  }
  assert.equal(state.status, 'derrota');
  assert.equal(state.campos.filter(c => c.mina).length, 10);
  assert.equal(state.campos.filter(c => c.explosao).length, 1);
  const finished = state;
  state = await action('marcar', 0, state);
  assert.deepEqual(state, finished);
  state = await post('/api/novo?nivel=classico');
  assert(state.campos.every(c => !c.aberto && !c.marcado));
  assert.equal(state.segundos, 0);
  const stale = await fetch(base + `/api/abrir?linha=0&coluna=0&partida=${previousId}`, { method: 'POST', headers: { 'X-Campo-Minado': '1' } });
  assert.equal(stale.status, 409);
  const invalid = await fetch(base + `/api/abrir?linha=-1&coluna=0&partida=${state.partida}`, { method: 'POST', headers: { 'X-Campo-Minado': '1' } });
  assert.equal(invalid.status, 400);
  assert.equal((await getState()).abertos, 0);
  console.log('OK: integração HTTP, quatro dificuldades, privacidade das minas, bandeiras, primeiro clique, derrota, reinício e validação de requisições.');
} finally {
  if (server.exitCode === null) { const exited = once(server, 'exit'); server.kill(); await exited; }
}
