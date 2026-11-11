// bTUNES für Linux: Fenster, Zugriff auf Musikordner, Cover, Podcast-Downloads.
// Die Oberfläche ist dieselbe index.html wie in der Android-App.
import { app, BrowserWindow, ipcMain, dialog, shell, nativeImage } from 'electron';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { scanFolders, findCover } from './lib.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
app.setName('bTUNES');
// Grafikkarte auch dann nutzen, wenn Chromium sie vorsichtshalber sperrt (z. B. NVIDIA unter Linux) – sonst ruckelt die drehende Platte
app.commandLine.appendSwitch('ignore-gpu-blocklist');
app.commandLine.appendSwitch('enable-gpu-rasterization');

if (!app.requestSingleInstanceLock()) app.quit();

const dataDir = () => app.getPath('userData');
const readJSON = (f, d) => { try { return JSON.parse(fs.readFileSync(path.join(dataDir(), f), 'utf8')); } catch { return d; } };
const writeJSON = (f, v) => { try { fs.writeFileSync(path.join(dataDir(), f), JSON.stringify(v)); } catch { /* egal */ } };

/* Musikordner: Standard ist ~/Musik (bzw. ~/Music), sonst selbst gewählt */
function getFolders() {
  const s = readJSON('settings.json', {});
  if (Array.isArray(s.folders)) return s.folders;
  const guess = [app.getPath('music'), path.join(os.homedir(), 'Musik'), path.join(os.homedir(), 'Music')]
    .filter((p, i, a) => p && a.indexOf(p) === i && fs.existsSync(p));
  return guess.slice(0, 1);
}
function setFolders(f) { const s = readJSON('settings.json', {}); s.folders = f; writeJSON('settings.json', s); }

let win;
const paths = new Map(); // Titel-Nummer -> Datei

function createWindow() {
  const st = readJSON('window.json', {});
  win = new BrowserWindow({
    width: st.w || 460, height: st.h || 940, x: st.x, y: st.y,
    minWidth: 360, minHeight: 620,
    backgroundColor: '#14110f', title: 'bTUNES', autoHideMenuBar: true,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      // Die Seite ist lokal; abgeschaltet, damit News- und Podcast-Feeds anderer Seiten geladen werden können
      webSecurity: false,
    },
  });
  win.loadFile(path.join(__dirname, 'app', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('file:')) { e.preventDefault(); shell.openExternal(url); } });
  win.on('close', () => { const b = win.getBounds(); writeJSON('window.json', { w: b.width, h: b.height, x: b.x, y: b.y }); });
}

app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());

ipcMain.handle('getFolders', () => getFolders());
ipcMain.handle('setFolders', (e, f) => { setFolders(f.filter(x => typeof x === 'string')); return getFolders(); });
ipcMain.handle('pickFolder', async (e, title) => {
  const r = await dialog.showOpenDialog(win, { title: typeof title === 'string' ? title : 'Musikordner wählen', properties: ['openDirectory', 'multiSelections'], defaultPath: os.homedir() });
  return r.canceled ? [] : r.filePaths;
});

ipcMain.handle('scan', async (e) => {
  const cache = readJSON('tags.json', {});
  const tracks = await scanFolders(getFolders(), cache, (n, total) => e.sender.send('scanProgress', { n, total }));
  writeJSON('tags.json', cache);
  paths.clear();
  for (const t of tracks) paths.set(t[0], t[11]);
  return { tracks, folders: getFolders() };
});

ipcMain.handle('art', async (e, id, size) => {
  const p = paths.get(id);
  if (!p) return null;
  const buf = await findCover(p);
  if (!buf) return null;
  let img = nativeImage.createFromBuffer(buf);
  if (img.isEmpty()) return null;
  const s = img.getSize();
  if (Math.max(s.width, s.height) > size * 1.3) img = img.resize(s.width >= s.height ? { width: size, quality: 'good' } : { height: size, quality: 'good' });
  return 'data:image/jpeg;base64,' + img.toJPEG(85).toString('base64');
});

/* ---------- Podcast-Downloads ---------- */
const dlDir = () => { const d = path.join(dataDir(), 'Podcasts'); fs.mkdirSync(d, { recursive: true }); return d; };
const safe = k => String(k).replace(/[^A-Za-z0-9._-]/g, '_');
const running = new Map();

ipcMain.handle('download', async (e, key, url) => {
  const ac = new AbortController();
  running.set(key, ac);
  const out = path.join(dlDir(), safe(key) + '.audio'), part = out + '.part';
  try {
    const r = await fetch(url, { signal: ac.signal, redirect: 'follow', headers: { 'User-Agent': 'bTUNES/1.0 (Linux)' } });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const total = +r.headers.get('content-length') || 0;
    const ws = fs.createWriteStream(part);
    const reader = r.body.getReader();
    let got = 0, last = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      got += value.length;
      if (!ws.write(value)) await new Promise(res => ws.once('drain', res));
      if (Date.now() - last > 400) { last = Date.now(); e.sender.send('dl', { key, got, total }); }
    }
    await new Promise((res, rej) => ws.end(err => (err ? rej(err) : res())));
    fs.renameSync(part, out);
    return { path: out, size: fs.statSync(out).size };
  } catch (err) {
    try { fs.unlinkSync(part); } catch { /* egal */ }
    throw new Error(ac.signal.aborted ? 'abgebrochen' : 'Download fehlgeschlagen: ' + err.message);
  } finally { running.delete(key); }
});
ipcMain.handle('cancelDownload', (e, key) => { const a = running.get(key); if (a) a.abort(); });
ipcMain.handle('deleteDownload', (e, key) => { try { fs.unlinkSync(path.join(dlDir(), safe(key) + '.audio')); } catch { /* egal */ } });
ipcMain.handle('listDownloads', () => {
  const files = [];
  for (const n of fs.readdirSync(dlDir())) {
    const p = path.join(dlDir(), n);
    if (n.endsWith('.part')) { try { fs.unlinkSync(p); } catch { /* egal */ } continue; }
    if (!n.endsWith('.audio')) continue;
    files.push({ name: n.slice(0, -6), path: p, size: fs.statSync(p).size });
  }
  let free = 0;
  try { const s = fs.statfsSync(dlDir()); free = s.bavail * s.bsize; } catch { /* egal */ }
  return { files, free };
});
ipcMain.handle('openExternal', (e, url) => { if (/^https?:/.test(url)) shell.openExternal(url); });

/* ---------- Zeitungen: Ordner mit PDF-Dateien finden und lesen ---------- */
const SKIP_DIRS = new Set(['node_modules', 'snap', 'flatpak', '__pycache__', 'site-packages', 'Trash']);
async function walkPdfDirs(dir, depth, out, st) {
  if (depth > 8 || st.n++ > 40000) return;
  let ents;
  try { ents = await fs.promises.readdir(dir, { withFileTypes: true }); } catch { return; }
  let count = 0, latest = 0;
  for (const e of ents) {
    if (e.name.startsWith('.') || SKIP_DIRS.has(e.name)) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) await walkPdfDirs(p, depth + 1, out, st);
    else if (e.isFile() && /\.pdf$/i.test(e.name)) {
      count++;
      try { latest = Math.max(latest, (await fs.promises.stat(p)).mtimeMs); } catch { /* egal */ }
    }
  }
  if (count) out.push({ path: dir, count, latest });
}
async function walkPdfFiles(dir, depth, out, seen) {
  if (depth > 6) return;
  let ents;
  try { ents = await fs.promises.readdir(dir, { withFileTypes: true }); } catch { return; }
  for (const e of ents) {
    if (e.name.startsWith('.')) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) await walkPdfFiles(p, depth + 1, out, seen);
    else if (e.isFile() && /\.pdf$/i.test(e.name) && !seen.has(p)) {
      seen.add(p);
      try { const s = await fs.promises.stat(p); out.push({ path: p, name: e.name, size: s.size, mtime: s.mtimeMs, dir }); } catch { /* egal */ }
    }
  }
}
// Durchsucht den persönlichen Ordner (und eingehängte Laufwerke unter /run/media und /media)
ipcMain.handle('findPdfFolders', async () => {
  const roots = [os.homedir()];
  for (const base of [path.join('/run/media', os.userInfo().username), '/media', '/mnt']) {
    try { for (const n of fs.readdirSync(base)) roots.push(path.join(base, n)); } catch { /* gibt es nicht */ }
  }
  const out = [], st = { n: 0 };
  for (const r of roots) await walkPdfDirs(r, 0, out, st);
  return { folders: out };
});
ipcMain.handle('listPdfs', async (e, folders) => {
  const out = [], seen = new Set();
  for (const f of folders || []) if (typeof f === 'string') await walkPdfFiles(f, 0, out, seen);
  return { files: out };
});
ipcMain.handle('readPdf', async (e, p) => {
  if (typeof p !== 'string' || !/\.pdf$/i.test(p)) throw new Error('Keine PDF-Datei');
  return fs.promises.readFile(p);
});
// Großes Fenster zum Zeitunglesen (und wieder zurück)
ipcMain.handle('toggleMax', () => { if (win.isMaximized()) win.unmaximize(); else win.maximize(); return win.isMaximized(); });

/* ---------- Radio: laufenden Titel aus dem Sender-Stream lesen (ICY-Metadaten) ---------- */
ipcMain.handle('icy', async (e, url) => {
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), 12000);
  try {
    const r = await fetch(url, { signal: ac.signal, headers: { 'Icy-MetaData': '1', 'User-Agent': 'bTUNES/1.0 (Linux)' } });
    const metaint = +r.headers.get('icy-metaint') || 0;
    if (!metaint || !r.body) { ac.abort(); return ''; }
    const reader = r.body.getReader();
    let buf = Buffer.alloc(0);
    while (buf.length < metaint + 1 + 4080) {
      const { done, value } = await reader.read();
      if (done) break;
      buf = Buffer.concat([buf, Buffer.from(value)]);
      if (buf.length > metaint) {
        const len = buf[metaint] * 16;
        if (buf.length >= metaint + 1 + len) {
          const meta = buf.slice(metaint + 1, metaint + 1 + len).toString('utf8');
          const m = meta.match(/StreamTitle='([^']*)';/);
          ac.abort();
          return m ? m[1].trim() : '';
        }
      }
    }
    ac.abort();
    return '';
  } catch { return ''; } finally { clearTimeout(timer); }
});

/* ---------- Orga: Datei speichern (z. B. Kalender-Export) ---------- */
ipcMain.handle('saveFile', async (e, name, text) => {
  const r = await dialog.showSaveDialog(win, { defaultPath: path.join(app.getPath('downloads'), name) });
  if (r.canceled || !r.filePath) return '';
  fs.writeFileSync(r.filePath, text, 'utf8');
  return r.filePath;
});

/* ---------- Diktieren: Whisper-Spracherkennung läuft lokal; die Programmteile liegen in app/asr ---------- */
ipcMain.handle('asrPaths', () => {
  const dir = path.join(__dirname, 'app', 'asr').replace('app.asar' + path.sep, 'app.asar.unpacked' + path.sep);
  return {
    lib: pathToFileURL(path.join(dir, 'transformers.min.js')).href,
    mjs: pathToFileURL(path.join(dir, 'ort-wasm-simd-threaded.asyncify.mjs')).href,
    wasm: pathToFileURL(path.join(dir, 'ort-wasm-simd-threaded.asyncify.wasm')).href,
    model: 'onnx-community/whisper-base',
  };
});
ipcMain.handle('asrWasm', () => {
  const dir = path.join(__dirname, 'app', 'asr').replace('app.asar' + path.sep, 'app.asar.unpacked' + path.sep);
  return fs.readFileSync(path.join(dir, 'ort-wasm-simd-threaded.asyncify.wasm'));
});

