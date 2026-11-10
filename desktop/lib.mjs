// Musik einlesen: Ordner durchsuchen, Tags lesen (mit Zwischenspeicher), Cover finden.
// Ohne Electron-Abhängigkeit, damit es sich auch einzeln testen lässt.
import fs from 'node:fs';
import path from 'node:path';
import { parseFile } from 'music-metadata';

export const AUDIO_EXT = new Set(['.mp3', '.flac', '.m4a', '.aac', '.ogg', '.oga', '.opus', '.wav', '.wma', '.aiff', '.aif', '.wv', '.mp4']);

/* Stabile Zahl aus Text (bleibt bei jedem Einlesen gleich, damit Playlists erhalten bleiben) */
export function stableId(s) {
  let h1 = 5381, h2 = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    h1 = ((h1 << 5) + h1 + c) | 0;
    h2 = (c + (h2 << 6) + (h2 << 16) - h2) | 0;
  }
  return (h1 >>> 0) * 2097152 + ((h2 >>> 0) & 0x1fffff);
}

async function walk(dir, out, seen, depth = 0) {
  if (depth > 15) return;
  let ents;
  try { ents = await fs.promises.readdir(dir, { withFileTypes: true }); } catch { return; }
  for (const e of ents) {
    if (e.name.startsWith('.')) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) await walk(p, out, seen, depth + 1);
    else if (e.isFile() && AUDIO_EXT.has(path.extname(e.name).toLowerCase()) && !seen.has(p)) { seen.add(p); out.push(p); }
  }
}

/* Liefert Titel im selben Format wie die Android-App:
   [id, titel, interpret, album, albumId, dauerMs, nr, jahr, ordner/, datei, albumInterpret, voller Pfad] */
export async function scanFolders(folders, cache, onProgress) {
  const files = [], seen = new Set();
  for (const f of folders) await walk(f, files, seen);
  const out = [];
  let done = 0, i = 0;
  const next = async () => {
    while (i < files.length) {
      const p = files[i++];
      let st;
      try { st = await fs.promises.stat(p); } catch { done++; continue; }
      const c = cache[p];
      let t;
      if (c && c.m === st.mtimeMs && c.s === st.size) t = c.t;
      else {
        t = ['', '', '', 0, 0, 0, ''];
        try {
          const md = await parseFile(p, { duration: false, skipCovers: true });
          const cm = md.common;
          const disk = cm.disk && cm.disk.no ? cm.disk.no : 0;
          const tr = cm.track && cm.track.no ? cm.track.no : 0;
          t = [cm.title || '', (cm.artists && cm.artists.join(', ')) || cm.artist || '', cm.album || '',
            Math.round((md.format.duration || 0) * 1000), tr ? disk * 1000 + tr : 0, cm.year || 0, cm.albumartist || ''];
        } catch { /* Datei ohne lesbare Tags: Dateiname wird Titel */ }
        cache[p] = { m: st.mtimeMs, s: st.size, t };
      }
      const dir = path.dirname(p) + '/';
      const file = path.basename(p);
      const album = t[2] || path.basename(path.dirname(p));
      out.push([stableId(p), t[0] || file.replace(/\.[^.]+$/, ''), t[1], album, stableId(dir + '|' + album),
        t[3], t[4], t[5], dir, file, t[6], p]);
      done++;
      if (onProgress && done % 200 === 0) onProgress(done, files.length);
    }
  };
  await Promise.all(Array.from({ length: 8 }, next));
  // Einträge entfernen, deren Dateien es nicht mehr gibt
  for (const k of Object.keys(cache)) if (!seen.has(k)) delete cache[k];
  return out;
}

/* Cover: eingebettet in der Datei, sonst cover.jpg / folder.jpg im Album-Ordner */
export async function findCover(p) {
  try {
    const md = await parseFile(p, { duration: false, skipCovers: false });
    const pic = md.common.picture && md.common.picture[0];
    if (pic && pic.data && pic.data.length) return Buffer.from(pic.data);
  } catch { /* weiter mit Bilddatei */ }
  const dir = path.dirname(p);
  let ents = [];
  try { ents = await fs.promises.readdir(dir); } catch { return null; }
  const pics = ents.filter(n => /\.(jpe?g|png|webp)$/i.test(n));
  const pref = pics.find(n => /^(cover|folder|front|album)\./i.test(n)) || pics[0];
  if (!pref) return null;
  try { return await fs.promises.readFile(path.join(dir, pref)); } catch { return null; }
}
