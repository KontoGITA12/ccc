const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('api', {
  targets: () => ipcRenderer.invoke('targets'),
  select: (t) => ipcRenderer.invoke('select', t),
  pick: () => ipcRenderer.invoke('pick'),
  status: () => ipcRenderer.invoke('status'),
  enable: () => ipcRenderer.invoke('enable'),
  disable: () => ipcRenderer.invoke('disable'),
  discord: () => ipcRenderer.send('discord'),
  exit: () => ipcRenderer.send('exit'),
  minimize: () => ipcRenderer.send('minimize'),
  onProgress: (cb) => ipcRenderer.on('progress', (_e, d) => cb(d)),
});
