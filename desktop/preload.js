const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('lunaxy', {
  search: q => ipcRenderer.invoke('music:search', q),
  resolve: song => ipcRenderer.invoke('music:resolve', song),
  lyric: song => ipcRenderer.invoke('music:lyric', song),
  pickLocal: () => ipcRenderer.invoke('local:pick'),
  minimize: () => ipcRenderer.invoke('window:minimize'),
  maximize: () => ipcRenderer.invoke('window:maximize'),
  close: () => ipcRenderer.invoke('window:close'),
  openExternal: url => ipcRenderer.invoke('open:external', url)
});
