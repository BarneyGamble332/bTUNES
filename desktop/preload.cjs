// Brücke zwischen Oberfläche und Linux-Teil (Dateien, Ordner, Downloads)
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktop', {
  platform: process.platform,
  scan: () => ipcRenderer.invoke('scan'),
  art: (id, size) => ipcRenderer.invoke('art', id, size),
  getFolders: () => ipcRenderer.invoke('getFolders'),
  setFolders: f => ipcRenderer.invoke('setFolders', f),
  pickFolder: t => ipcRenderer.invoke('pickFolder', t),
  download: (k, u) => ipcRenderer.invoke('download', k, u),
  cancelDownload: k => ipcRenderer.invoke('cancelDownload', k),
  deleteDownload: k => ipcRenderer.invoke('deleteDownload', k),
  listDownloads: () => ipcRenderer.invoke('listDownloads'),
  openExternal: u => ipcRenderer.invoke('openExternal', u),
  icy: u => ipcRenderer.invoke('icy', u),
  saveFile: (n, t) => ipcRenderer.invoke('saveFile', n, t),
  asrPaths: () => ipcRenderer.invoke('asrPaths'),
  asrWasm: () => ipcRenderer.invoke('asrWasm'),
  findPdfFolders: () => ipcRenderer.invoke('findPdfFolders'),
  listPdfs: f => ipcRenderer.invoke('listPdfs', f),
  readPdf: p => ipcRenderer.invoke('readPdf', p),
  toggleMax: () => ipcRenderer.invoke('toggleMax'),
  onDl: cb => ipcRenderer.on('dl', (e, v) => cb(v)),
  onScan: cb => ipcRenderer.on('scanProgress', (e, v) => cb(v)),
});
