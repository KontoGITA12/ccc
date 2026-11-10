const { app, BrowserWindow, ipcMain, shell } = require('electron');
const fs = require('fs');
const path = require('path');

const MC = '1.21.4';
const LOADER = '0.16.10';
const DISCORD = 'https://discord.gg/2Wp7etPD9';
const PROFILE_ID = `fabric-loader-${LOADER}-${MC}`;
let win;

const mcDir = () => path.join(process.env.APPDATA || require('os').homedir(), '.minecraft');
const modsDir = () => path.join(mcDir(), 'mods');
const resPath = (f) => app.isPackaged ? path.join(process.resourcesPath, 'res', f) : path.join(__dirname, 'resources', f);
const list = (d) => { try { return fs.readdirSync(d); } catch { return []; } };
const progress = (text, pct, kind) => win && win.webContents.send('progress', { text, pct, kind });

function status() {
  const mc = fs.existsSync(mcDir());
  const fabric = list(path.join(mcDir(), 'versions')).some((v) => v.startsWith('fabric-loader-') && v.endsWith('-' + MC));
  const mods = list(modsDir());
  return {
    mc, fabric,
    api: mods.some((f) => /^fabric-api.*\.jar$/i.test(f)),
    mod: mods.some((f) => /^ghostvisual.*\.jar$/i.test(f)),
  };
}

async function download(url, dest) {
  const r = await fetch(url, { headers: { 'User-Agent': 'GhostVisualLauncher/1.0 (made by shvga)' } });
  if (!r.ok) throw new Error('Pobieranie nie powiodło się (' + r.status + ')');
  fs.writeFileSync(dest, Buffer.from(await r.arrayBuffer()));
}

async function installFabric() {
  const vdir = path.join(mcDir(), 'versions', PROFILE_ID);
  fs.mkdirSync(vdir, { recursive: true });
  const r = await fetch(`https://meta.fabricmc.net/v2/versions/loader/${MC}/${LOADER}/profile/json`);
  if (!r.ok) throw new Error('Nie udało się pobrać Fabric (' + r.status + ')');
  fs.writeFileSync(path.join(vdir, PROFILE_ID + '.json'), await r.text());
  const lp = path.join(mcDir(), 'launcher_profiles.json');
  let data = { profiles: {}, version: 3 };
  try { data = JSON.parse(fs.readFileSync(lp, 'utf8')); } catch {}
  data.profiles = data.profiles || {};
  const now = new Date().toISOString();
  data.profiles['ghostvisual-fabric'] = {
    name: 'Attention (Fabric ' + MC + ')', type: 'custom', created: now, lastUsed: now,
    icon: 'Grass', lastVersionId: PROFILE_ID,
  };
  fs.writeFileSync(lp, JSON.stringify(data, null, 2));
}

async function installApi() {
  const q = `https://api.modrinth.com/v2/project/fabric-api/version?game_versions=${encodeURIComponent(JSON.stringify([MC]))}&loaders=${encodeURIComponent('["fabric"]')}`;
  const r = await fetch(q, { headers: { 'User-Agent': 'GhostVisualLauncher/1.0 (made by shvga)' } });
  const vers = await r.json();
  if (!vers.length) throw new Error('Nie znaleziono Fabric API dla ' + MC);
  const f = vers[0].files.find((x) => x.primary) || vers[0].files[0];
  await download(f.url, path.join(modsDir(), f.filename));
}

async function enable() {
  try {
    if (!fs.existsSync(mcDir())) throw new Error('Nie znaleziono folderu .minecraft. Uruchom raz Minecraft Launcher.');
    fs.mkdirSync(modsDir(), { recursive: true });
    let s = status();
    if (!s.fabric) { progress('Instaluję Fabric Loader…', 25); await installFabric(); }
    s = status();
    if (!s.api) { progress('Pobieram Fabric API…', 55); await installApi(); }
    progress('Instaluję GhostVisual…', 85);
    for (const f of list(modsDir())) if (/^ghostvisual.*\.jar$/i.test(f)) fs.unlinkSync(path.join(modsDir(), f));
    fs.copyFileSync(resPath('ghostvisual.jar'), path.join(modsDir(), 'ghostvisual.jar'));
    progress('Gotowe! Uruchom Minecraft i wybierz profil „Attention (Fabric ' + MC + ')”.', 100, 'ok');
    return { ok: true, status: status() };
  } catch (e) {
    progress('Błąd: ' + e.message, 0, 'err');
    return { ok: false, status: status() };
  }
}

async function disable() {
  let n = 0;
  for (const f of list(modsDir())) if (/^ghostvisual.*\.jar$/i.test(f)) { fs.unlinkSync(path.join(modsDir(), f)); n++; }
  progress(n ? 'GhostVisual wyłączony.' : 'GhostVisual nie był włączony.', 0, 'ok');
  return { ok: true, status: status() };
}

app.whenReady().then(() => {
  win = new BrowserWindow({
    width: 820, height: 580, frame: false, transparent: true, resizable: false, hasShadow: false,
    backgroundColor: '#00000000', title: 'Attention', show: false,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true },
  });
  win.setMenu(null);
  win.loadFile('index.html');
  win.once('ready-to-show', () => win.show());
  ipcMain.handle('status', status);
  ipcMain.handle('enable', enable);
  ipcMain.handle('disable', disable);
  ipcMain.on('discord', () => shell.openExternal(DISCORD));
  ipcMain.on('exit', () => app.quit());
  ipcMain.on('minimize', () => win.minimize());
});
app.on('window-all-closed', () => app.quit());
