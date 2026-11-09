const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('api', {
  status: () => ipcRenderer.invoke('status'),
  enable: () => ipcRenderer.invoke('enable'),
  disable: () => ipcRenderer.invoke('disable'),
  discord: () => ipcRenderer.send('discord'),
  exit: () => ipcRenderer.send('exit'),
  minimize: () => ipcRenderer.send('minimize'),
  onProgress: (cb) => ipcRenderer.on('progress', (_e, d) => cb(d)),
});
