"use strict";
const test = require("node:test");
const assert = require("node:assert");
const { vlcCandidates, findVlc, vlcArgs, launchVlc, windowsRegistryDir } = require("../externalplayer.cjs");

const fsOf = (...files) => (p) => files.includes(p);

test("findVlc Windows : Program Files, puis (x86), puis registre", () => {
  const env = { ProgramFiles: "C:\\Program Files", "ProgramFiles(x86)": "C:\\Program Files (x86)" };
  assert.deepStrictEqual(findVlc({ platform: "win32", env, run: null }, fsOf("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe")), { command: "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe", prefix: [] });
  assert.strictEqual(findVlc({ platform: "win32", env, run: null }, fsOf("C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe"))?.command, "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe");
  assert.strictEqual(findVlc({ platform: "win32", env, run: null }, fsOf()), null);
  // Installation dans un dossier non standard : seul le registre la connaît.
  const run = () => ({ status: 0, stdout: "\r\nHKEY_LOCAL_MACHINE\\SOFTWARE\\VideoLAN\\VLC\r\n    (Default)    REG_SZ    D:\\Apps\\VLC\\vlc.exe\r\n" });
  assert.strictEqual(windowsRegistryDir(run), "D:\\Apps\\VLC");
  assert.strictEqual(findVlc({ platform: "win32", env, run }, fsOf("D:\\Apps\\VLC\\vlc.exe"))?.command, "D:\\Apps\\VLC\\vlc.exe");
});

test("findVlc macOS : /Applications/VLC.app", () => {
  const bin = "/Applications/VLC.app/Contents/MacOS/VLC";
  assert.deepStrictEqual(findVlc({ platform: "darwin", env: {}, home: "/Users/x" }, fsOf(bin)), { command: bin, prefix: [] });
  assert.strictEqual(findVlc({ platform: "darwin", env: {}, home: "/Users/x" }, fsOf()), null);
  assert.strictEqual(findVlc({ platform: "darwin", env: {}, home: "/Users/x" }, fsOf("/Users/x/Applications/VLC.app/Contents/MacOS/VLC"))?.command, "/Users/x/Applications/VLC.app/Contents/MacOS/VLC");
});

test("findVlc Linux : PATH, snap, flatpak", () => {
  const env = { PATH: "/opt/bin:/usr/bin" };
  assert.strictEqual(findVlc({ platform: "linux", env, home: "/home/x" }, fsOf("/opt/bin/vlc"))?.command, "/opt/bin/vlc");
  assert.strictEqual(findVlc({ platform: "linux", env, home: "/home/x" }, fsOf("/snap/bin/vlc"))?.command, "/snap/bin/vlc");
  assert.deepStrictEqual(findVlc({ platform: "linux", env, home: "/home/x" }, fsOf("/var/lib/flatpak/app/org.videolan.VLC")), { command: "flatpak", prefix: ["run", "org.videolan.VLC"] });
  assert.deepStrictEqual(findVlc({ platform: "linux", env, home: "/home/x" }, fsOf("/home/x/.local/share/flatpak/app/org.videolan.VLC")), { command: "flatpak", prefix: ["run", "org.videolan.VLC"] });
  assert.strictEqual(findVlc({ platform: "linux", env, home: "/home/x" }, fsOf()), null);
  // PATH prime sur flatpak.
  assert.strictEqual(findVlc({ platform: "linux", env, home: "/home/x" }, fsOf("/usr/bin/vlc", "/var/lib/flatpak/app/org.videolan.VLC"))?.command, "/usr/bin/vlc");
  assert.ok(vlcCandidates({ platform: "linux", env: {}, home: "" }).length > 0);
});

test("vlcArgs : identité avant l'adresse, séparateur --, adresses refusées", () => {
  assert.deepStrictEqual(vlcArgs("http://h/live/u/p/1.ts", { userAgent: "UA/1", referer: "http://r/", title: "Chaîne" }),
    ["--http-user-agent=UA/1", "--http-referrer=http://r/", "--meta-title=Chaîne", "--", "http://h/live/u/p/1.ts"]);
  assert.deepStrictEqual(vlcArgs("https://h/a.m3u8"), ["--", "https://h/a.m3u8"]);
  assert.strictEqual(vlcArgs("file:///etc/passwd"), null);
  assert.strictEqual(vlcArgs("--extraintf=x"), null);
  assert.strictEqual(vlcArgs("http://h/\n--x"), null);
  assert.strictEqual(vlcArgs(42), null);
  assert.ok(!vlcArgs("http://h/x", { userAgent: "a\n--bad" })[0].includes("\n"));
});

test("launchVlc : processus détaché, sans journaliser l'adresse ; faux si adresse refusée", () => {
  let call = null;
  const logs = [];
  const orig = console.log; console.log = (...a) => logs.push(a);
  const spawnFn = (cmd, args, opts) => { call = { cmd, args, opts }; return { on() {}, unref() { call.unref = true; } }; };
  try {
    assert.strictEqual(launchVlc({ command: "flatpak", prefix: ["run", "org.videolan.VLC"] }, { url: "http://h/u/p/1.ts", userAgent: "UA" }, spawnFn), true);
  } finally { console.log = orig; }
  assert.strictEqual(call.cmd, "flatpak");
  assert.deepStrictEqual(call.args.slice(0, 3), ["run", "org.videolan.VLC", "--http-user-agent=UA"]);
  assert.strictEqual(call.opts.detached, true);
  assert.strictEqual(call.unref, true);
  assert.deepStrictEqual(logs, []);
  assert.strictEqual(launchVlc({ command: "vlc", prefix: [] }, { url: "ftp://x" }, spawnFn), false);
  assert.strictEqual(launchVlc(null, { url: "http://x" }, spawnFn), false);
  assert.strictEqual(launchVlc({ command: "vlc", prefix: [] }, { url: "http://x" }, () => { throw new Error("boom http://x"); }), false);
});
