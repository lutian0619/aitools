const http = require("http");
const crypto = require("crypto");
const dgram = require("dgram");
const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const Database = require("better-sqlite3");

const serverRoot = __dirname;
const projectRoot = path.resolve(serverRoot, "..");
const webRoot = path.join(projectRoot, "web");
const tools = loadTools();
const toolsById = new Map(tools.map((tool) => [tool.id, tool]));
const port = Number(process.env.PORT || 8788);
const discoveryMessages = new Set(["aitools-discovery", "one-page-diary-discovery"]);
const sqliteStores = new Map();
const eggRooms = new Map();
const DEFAULT_DIARY_AUTHOR = "小妈妈";
const EGG_ROOM_ID = "default";
const EGG_AI_DEVICE_ID = "__egg_ai__";
const EGG_ARENA_WIDTH = 1200;
const EGG_FLOOR_Y = 0;
const EGG_MAX_HP = 100;
const EGG_MAX_ENERGY = 100;
const EGG_MAX_GUARD = 100;
const EGG_WIN_ROUNDS = 2;
const EGG_ROUND_MS = 60_000;
const EGG_GRAVITY = 2900;
const EGG_JUMP_VELOCITY = 720;
const EGG_MOVE_SPEED = 340;
const EGG_BLOCK_MOVE_SPEED = 120;
const EGG_GUARD_RECOVERY = 26;
const EGG_GUARD_DRAIN = 20;
const EGG_ENERGY_RECOVERY = 4.5;
const EGG_SPECIAL_COST = 55;
const EGG_SPECIAL_DASH = 190;
const EGG_LIGHT_ACTIVE_MS = 200;
const EGG_LIGHT_COOLDOWN_MS = 330;
const EGG_HEAVY_ACTIVE_MS = 330;
const EGG_HEAVY_COOLDOWN_MS = 570;
const EGG_SPECIAL_ACTIVE_MS = 470;
const EGG_SPECIAL_COOLDOWN_MS = 820;

const types = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".zip": "application/zip",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".png": "image/png",
  ".gif": "image/gif",
  ".webp": "image/webp",
};

for (const tool of tools) {
  ensureToolStorage(tool);
}

const diaryTool = toolsById.get("diary");
for (const tool of tools) {
  if (tool.sync && tool.sync.enabled) {
    initializeStateStore(tool);
  }
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);

  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,HEAD,PUT,POST,OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    });
    res.end();
    return;
  }

  if (url.pathname === "/") {
    redirect(res, "/web/");
    return;
  }

  if (url.pathname === "/api/status") {
    sendJson(res, platformStatus(), { compact: true });
    return;
  }

  if (url.pathname === "/api/tools") {
    sendJson(res, { ok: true, tools: tools.map(publicToolInfo) });
    return;
  }

  if (url.pathname.startsWith("/api/egg-friends")) {
    handleEggFriendsApi(req, res, url);
    return;
  }

  const toolMatch = url.pathname.match(/^\/api\/tools\/([^/]+)(?:\/(.*))?$/);
  if (toolMatch) {
    handleToolApi(req, res, decodeURIComponent(toolMatch[1]), toolMatch[2] || "");
    return;
  }

  if (handleDiaryCompatibility(req, res, url.pathname)) {
    return;
  }

  if (handleLegacyApk(req, res, url.pathname)) {
    return;
  }

  serveWeb(url.pathname, res);
});

server.listen(port, "0.0.0.0", () => {
  console.log(`aitools 局域网服务: http://0.0.0.0:${port}/web/`);
});

const udpServer = dgram.createSocket("udp4");
udpServer.on("message", (message, rinfo) => {
  const text = message.toString("utf8").trim();
  if (!discoveryMessages.has(text)) return;
  const host = localAddressForRemote(rinfo.address);
  const hosts = localAddressesForRemote(rinfo.address);
  const payload = Buffer.from(JSON.stringify({
    ok: true,
    app: text === "one-page-diary-discovery" ? "one-page-diary" : "aitools",
    platform: "aitools",
    host,
    port,
    url: `http://${host}:${port}`,
    urls: hosts.map((address) => `http://${address}:${port}`),
    tools: tools.map((tool) => ({
      id: tool.id,
      name: tool.name,
      app: tool.app,
      packageName: tool.packageName || "",
    })),
  }));
  udpServer.send(payload, rinfo.port, rinfo.address);
});
udpServer.on("error", (error) => {
  console.error(`局域网自动发现启动失败: ${error.message}`);
});
udpServer.bind(port, "0.0.0.0", () => {
  udpServer.setBroadcast(true);
  console.log(`局域网自动发现: udp://0.0.0.0:${port}`);
});

function loadTools() {
  const configFile = path.join(serverRoot, "config", "tools.json");
  return JSON.parse(fs.readFileSync(configFile, "utf8"));
}

function ensureToolStorage(tool) {
  const stateName = tool.sync && tool.sync.stateName ? tool.sync.stateName : tool.id;
  const dir = path.join(projectRoot, "sync", stateName);
  fs.mkdirSync(dir, { recursive: true });
  if (tool.sync && tool.sync.enabled) {
    fs.mkdirSync(path.join(dir, "history"), { recursive: true });
    fs.mkdirSync(path.join(dir, "state"), { recursive: true });
    fs.mkdirSync(path.join(dir, "state", "attachments"), { recursive: true });
  }
}

function handleToolApi(req, res, toolId, action) {
  const tool = toolsById.get(toolId);
  if (!tool) {
    sendText(res, 404, "tool not found");
    return;
  }

  if (action === "" || action === "status") {
    sendJson(res, toolStatus(tool));
    return;
  }

  if (action === "apk" && (req.method === "GET" || req.method === "HEAD")) {
    serveApk(req, res, tool);
    return;
  }

  if (!tool.sync || !tool.sync.enabled) {
    sendText(res, 404, "tool sync is not enabled");
    return;
  }

  if (action === "latest" && (req.method === "GET" || req.method === "HEAD")) {
    serveLatestZip(req, res, tool);
    return;
  }

  if (action === "latest" && (req.method === "PUT" || req.method === "POST")) {
    receiveLatestZip(req, res, tool);
    return;
  }

  if (action === "manifest" && req.method === "GET") {
    const state = loadState(tool);
    sendJson(res, {
      ok: true,
      app: tool.app,
      updatedAt: state.updatedAt || null,
      entries: state.entries.map(entryManifest),
    });
    return;
  }

  if (action === "entries/query" && req.method === "POST") {
    readJson(req, res, (payload) => {
      const wanted = new Set((payload.ids || []).map(String));
      const entries = loadState(tool).entries.filter((entry) => wanted.has(String(entry.id)));
      sendJson(res, { ok: true, entries, attachments: attachmentsForEntries(tool, entries) });
    });
    return;
  }

  if (action === "entries" && req.method === "GET") {
    const state = loadState(tool);
    sendJson(res, {
      ok: true,
      app: tool.app,
      updatedAt: state.updatedAt || null,
      entries: state.entries,
    });
    return;
  }

  if (action === "entries" && req.method === "POST") {
    readJson(req, res, (payload) => {
      const state = loadState(tool);
      const entries = Array.isArray(payload.entries) ? payload.entries : [];
      const attachments = payload.attachments && typeof payload.attachments === "object" ? payload.attachments : {};
      for (const [name, encoded] of Object.entries(attachments)) {
        if (isSafeAttachmentName(name) && typeof encoded === "string") {
          fs.writeFileSync(path.join(stateAttachmentsDir(tool), name), Buffer.from(encoded, "base64"));
        }
      }
      let changed = false;
      for (const entry of entries) {
        if (mergeEntry(state, normalizeEntry(entry))) {
          changed = true;
        }
      }
      if (changed || entries.length || Object.keys(attachments).length) {
        saveState(tool, state);
        writeLatestZipFromState(tool, state);
      }
      sendJson(res, { ok: true, updatedAt: state.updatedAt || new Date().toISOString(), entries: state.entries.map(entryManifest) });
    });
    return;
  }

  sendText(res, 404, "not found");
}

function handleDiaryCompatibility(req, res, pathname) {
  if (!diaryTool) return false;
  if (pathname === "/api/latest" && (req.method === "GET" || req.method === "HEAD")) {
    serveLatestZip(req, res, diaryTool);
    return true;
  }
  if (pathname === "/api/latest" && (req.method === "PUT" || req.method === "POST")) {
    receiveLatestZip(req, res, diaryTool);
    return true;
  }
  if (pathname === "/api/manifest" && req.method === "GET") {
    const state = loadState(diaryTool);
    sendJson(res, {
      ok: true,
      app: diaryTool.app,
      updatedAt: state.updatedAt || null,
      entries: state.entries.map(entryManifest),
    });
    return true;
  }
  if (pathname === "/api/entries/query" && req.method === "POST") {
    readJson(req, res, (payload) => {
      const wanted = new Set((payload.ids || []).map(String));
      const entries = loadState(diaryTool).entries.filter((entry) => wanted.has(String(entry.id)));
      sendJson(res, { ok: true, entries, attachments: attachmentsForEntries(diaryTool, entries) });
    });
    return true;
  }
  if (pathname === "/api/entries" && req.method === "POST") {
    handleToolApi(req, res, "diary", "entries");
    return true;
  }
  if (pathname === "/api/entries" && req.method === "GET") {
    const state = loadState(diaryTool);
    sendJson(res, {
      ok: true,
      app: diaryTool.app,
      updatedAt: state.updatedAt || null,
      entries: state.entries,
    });
    return true;
  }
  const attachmentMatch = pathname.match(/^\/api\/attachments\/([^/]+)$/);
  if (attachmentMatch && (req.method === "GET" || req.method === "HEAD")) {
    serveAttachment(req, res, diaryTool, decodeURIComponent(attachmentMatch[1]));
    return true;
  }
  return false;
}

function handleLegacyApk(req, res, pathname) {
  if (!isApkPath(pathname) || (req.method !== "GET" && req.method !== "HEAD")) {
    return false;
  }
  const tool = toolsById.get("diary") || tools[0];
  serveApk(req, res, tool);
  return true;
}

function handleEggFriendsApi(req, res, url) {
  if (url.pathname === "/api/egg-friends/status" && req.method === "GET") {
    const room = eggRoom(EGG_ROOM_ID);
    tickEggRoom(room);
    sendJson(res, eggPublicState(room, url.searchParams.get("deviceId") || ""));
    return;
  }

  if (url.pathname === "/api/egg-friends/join" && req.method === "POST") {
    readJson(req, res, (payload) => {
      const room = eggRoom(String(payload.roomId || EGG_ROOM_ID));
      tickEggRoom(room);
      const deviceId = safeEggId(payload.deviceId || crypto.randomUUID());
      const name = safeEggName(payload.name || payload.nickname || "Egg");
      const slot = joinEggRoom(room, deviceId, name);
      sendJson(res, eggPublicState(room, deviceId, { slot }));
    });
    return;
  }

  if (url.pathname === "/api/egg-friends/input" && req.method === "POST") {
    readJson(req, res, (payload) => {
      const room = eggRoom(String(payload.roomId || EGG_ROOM_ID));
      tickEggRoom(room);
      const deviceId = safeEggId(payload.deviceId || "");
      const player = room.players.find((item) => item && item.deviceId === deviceId);
      if (!player) {
        sendJson(res, { ok: false, error: "not joined", state: eggPublicState(room, deviceId) });
        return;
      }
      player.lastSeen = Date.now();
      player.name = safeEggName(payload.name || player.name);
      player.input = {
        left: Boolean(payload.left),
        right: Boolean(payload.right),
        up: Boolean(payload.up || payload.jump),
        light: Boolean(payload.light),
        heavy: Boolean(payload.heavy),
        special: Boolean(payload.special),
        block: Boolean(payload.block),
      };
      if (payload.ready) player.ready = true;
      if (payload.restart && (room.status === "ended" || room.status === "match-ended")) {
        if (room.status === "match-ended") resetEggMatch(room);
        resetEggRound(room, true);
      }
      maybeStartEggRoom(room);
      tickEggRoom(room);
      sendJson(res, eggPublicState(room, deviceId));
    });
    return;
  }

  sendText(res, 404, "not found");
}

function eggRoom(roomId) {
  const id = roomId || EGG_ROOM_ID;
  let room = eggRooms.get(id);
  if (room) return room;
  room = {
    id,
    mode: id.startsWith("ai-") ? "ai" : "pvp",
    status: "waiting",
    message: "等待第二位朋友加入",
    roundWinner: 0,
    matchWinner: 0,
    updatedAt: Date.now(),
    countdownUntil: 0,
    roundEndsAt: 0,
    players: [newEggPlayer(1), newEggPlayer(2)],
  };
  eggRooms.set(id, room);
  return room;
}

function newEggPlayer(slot) {
  return {
    slot,
    deviceId: "",
    name: slot === 1 ? "Egg" : "朋友",
    connected: false,
    ready: false,
    x: slot === 1 ? 290 : 910,
    y: EGG_FLOOR_Y,
    vx: 0,
    vy: 0,
    facing: slot === 1 ? 1 : -1,
    hp: EGG_MAX_HP,
    energy: 28,
    guard: EGG_MAX_GUARD,
    wins: 0,
    combo: 0,
    comboUntil: 0,
    hitSparkUntil: 0,
    attack: "",
    attackUntil: 0,
    attackHit: false,
    cooldownUntil: 0,
    hurtUntil: 0,
    blockUntil: 0,
    lastSeen: 0,
    input: {},
  };
}

function joinEggRoom(room, deviceId, name) {
  expireEggPlayers(room);
  let player = room.players.find((item) => item.deviceId === deviceId);
  if (!player && room.mode === "ai") {
    player = room.players[0];
    if (player.deviceId && player.deviceId !== deviceId) {
      room.players[0] = newEggPlayer(1);
      player = room.players[0];
    }
  }
  if (!player) {
    player = room.players.find((item) => !item.connected);
    if (!player) return 0;
    const oldWins = player.wins || 0;
    Object.assign(player, newEggPlayer(player.slot), { wins: oldWins });
  }
  player.deviceId = deviceId;
  player.name = name;
  player.connected = true;
  player.lastSeen = Date.now();
  player.ready = room.status === "running";
  if (room.mode === "ai") {
    const ai = room.players[1];
    if (!ai.connected || ai.deviceId !== EGG_AI_DEVICE_ID) {
      Object.assign(ai, newEggPlayer(2), {
        deviceId: EGG_AI_DEVICE_ID,
        name: "蛋卷教练",
        connected: true,
        ready: true,
        lastSeen: Date.now(),
      });
    }
  }
  maybeStartEggRoom(room);
  return player.slot;
}

function maybeStartEggRoom(room) {
  const connected = room.players.filter((player) => player.connected);
  if (connected.length < 2) {
    room.status = "waiting";
    room.message = room.mode === "ai" ? "点开始挑战蛋卷教练" : "等待第二位朋友加入";
    room.countdownUntil = 0;
    return;
  }
  if (room.status === "waiting") {
    room.message = room.mode === "ai" ? "点开始挑战蛋卷教练" : "双方点开始";
  }
  const ready = room.mode === "ai"
    ? room.players[0].connected && room.players[0].ready
    : connected.every((player) => player.ready);
  if (room.status === "waiting" && ready) {
    room.status = "countdown";
    room.countdownUntil = Date.now() + 1200;
    room.message = "准备开打";
  }
}

function resetEggRound(room, keepReady) {
  const wins = room.players.map((player) => player.wins || 0);
  const connected = room.players.map((player) => player.connected);
  const deviceIds = room.players.map((player) => player.deviceId);
  const names = room.players.map((player) => player.name);
  for (let i = 0; i < room.players.length; i++) {
    Object.assign(room.players[i], newEggPlayer(i + 1), {
      wins: wins[i],
      connected: connected[i],
      deviceId: deviceIds[i],
      name: names[i],
      ready: keepReady && connected[i],
      lastSeen: connected[i] ? Date.now() : 0,
    });
  }
  room.roundWinner = 0;
  room.matchWinner = 0;
  room.status = keepReady ? "countdown" : "waiting";
  room.countdownUntil = keepReady ? Date.now() + 900 : 0;
  room.roundEndsAt = 0;
  room.updatedAt = Date.now();
}

function resetEggMatch(room) {
  for (const player of room.players) {
    player.wins = 0;
  }
  room.roundWinner = 0;
  room.matchWinner = 0;
  room.roundEndsAt = 0;
}

function tickEggRoom(room) {
  expireEggPlayers(room);
  const now = Date.now();
  let dt = Math.min(0.05, Math.max(0.001, (now - room.updatedAt) / 1000));
  room.updatedAt = now;
  if (room.status === "countdown" && now >= room.countdownUntil) {
    room.status = "running";
    room.message = "开打";
    room.roundEndsAt = now + EGG_ROUND_MS;
  }
  if (room.status !== "running") return;
  if (room.roundEndsAt && now >= room.roundEndsAt) {
    finishEggRound(room, eggTimeoutWinner(room), "timeout");
    return;
  }
  const steps = Math.max(1, Math.ceil(dt / 0.016));
  const step = dt / steps;
  for (let i = 0; i < steps; i++) {
    simulateEggStep(room, step, now);
  }
}

function simulateEggStep(room, dt, now) {
  const a = room.players[0];
  const b = room.players[1];
  if (room.mode === "ai") updateEggAi(room, b, a, now);
  if (!a.connected || !b.connected) {
    room.status = "waiting";
    room.message = "等待对手回到房间";
    return;
  }
  faceEggPlayers(a, b);
  updateEggPlayer(a, b, dt, now);
  updateEggPlayer(b, a, dt, now);
  resolveEggPush(a, b);
  for (const player of room.players) {
    player.x = clamp(player.x, 60, EGG_ARENA_WIDTH - 60);
    if (player.y < EGG_FLOOR_Y) {
      player.y = EGG_FLOOR_Y;
      player.vy = 0;
    }
  }
  checkEggHit(a, b, now);
  checkEggHit(b, a, now);
  if (a.hp <= 0 || b.hp <= 0) {
    finishEggRound(room, eggKnockoutWinner(room), "ko");
  }
}

function finishEggRound(room, winner, reason) {
  room.roundEndsAt = 0;
  room.roundWinner = winner ? winner.slot : 0;
  if (!winner) {
    room.status = "ended";
    room.message = "平局，重开这一局";
  } else {
    winner.wins += 1;
    if (winner.wins >= EGG_WIN_ROUNDS) {
      room.status = "match-ended";
      room.matchWinner = winner.slot;
      room.message = `${winner.name} 赢下整场`;
    } else {
      room.status = "ended";
      room.message = reason === "timeout" ? `${winner.name} 时间判定获胜` : `${winner.name} 赢下这一局`;
    }
  }
  for (const player of room.players) {
    player.ready = false;
    player.input = {};
  }
}

function eggKnockoutWinner(room) {
  const [a, b] = room.players;
  if (a.hp <= 0 && b.hp <= 0) return eggScoreWinner(room);
  return a.hp > b.hp ? a : b;
}

function eggTimeoutWinner(room) {
  return eggScoreWinner(room);
}

function eggScoreWinner(room) {
  const [a, b] = room.players;
  const aScore = Math.round(a.hp * 100 + a.guard * 6 + a.energy * 2);
  const bScore = Math.round(b.hp * 100 + b.guard * 6 + b.energy * 2);
  if (aScore === bScore) return null;
  return aScore > bScore ? a : b;
}

function updateEggPlayer(player, opponent, dt, now) {
  const input = player.input || {};
  const stunned = now < player.hurtUntil;
  const attacking = now < player.attackUntil;
  const grounded = player.y <= EGG_FLOOR_Y + 0.5;
  if (player.comboUntil <= now) player.combo = 0;
  if (!input.block || stunned || attacking) {
    player.guard = clamp(player.guard + EGG_GUARD_RECOVERY * dt, 0, EGG_MAX_GUARD);
  }
  player.energy = clamp(player.energy + EGG_ENERGY_RECOVERY * dt, 0, EGG_MAX_ENERGY);
  if (input.block && grounded && !attacking && player.guard > 5) {
    player.blockUntil = now + 120;
    player.guard = clamp(player.guard - EGG_GUARD_DRAIN * dt, 0, EGG_MAX_GUARD);
  }
  if (!stunned && !attacking) {
    let direction = 0;
    if (input.left) direction -= 1;
    if (input.right) direction += 1;
    player.vx = direction * (now < player.blockUntil ? EGG_BLOCK_MOVE_SPEED : EGG_MOVE_SPEED);
    if (direction !== 0) player.facing = direction > 0 ? 1 : -1;
    if (input.up && grounded) player.vy = EGG_JUMP_VELOCITY;
    const canSpecial = input.special && player.energy >= EGG_SPECIAL_COST;
    if ((canSpecial || input.light || input.heavy) && now >= player.cooldownUntil) {
      player.attack = canSpecial ? "special" : (input.heavy ? "heavy" : "light");
      if (canSpecial) {
        player.energy = clamp(player.energy - EGG_SPECIAL_COST, 0, EGG_MAX_ENERGY);
        player.vx += player.facing * EGG_SPECIAL_DASH;
      }
      player.attackUntil = now + (canSpecial ? EGG_SPECIAL_ACTIVE_MS : (input.heavy ? EGG_HEAVY_ACTIVE_MS : EGG_LIGHT_ACTIVE_MS));
      player.cooldownUntil = now + (canSpecial ? EGG_SPECIAL_COOLDOWN_MS : (input.heavy ? EGG_HEAVY_COOLDOWN_MS : EGG_LIGHT_COOLDOWN_MS));
      player.attackHit = false;
      player.vx *= canSpecial ? 0.62 : 0.35;
    }
  } else if (attacking) {
    player.vx *= 0.82;
  } else {
    player.vx *= 0.72;
  }
  player.vy -= EGG_GRAVITY * dt;
  player.x += player.vx * dt;
  player.y += player.vy * dt;
  if (player.attackUntil <= now) player.attack = "";
}

function checkEggHit(attacker, defender, now) {
  if (!attacker.attack || attacker.attackHit || now >= attacker.attackUntil) return;
  const range = attacker.attack === "special" ? 218 : (attacker.attack === "heavy" ? 150 : 112);
  const baseDamage = attacker.attack === "special" ? 24 : (attacker.attack === "heavy" ? 17 : 8);
  const damage = baseDamage + Math.min(6, attacker.combo * 2) + (attacker.y > 20 ? 2 : 0);
  const vertical = Math.abs(attacker.y - defender.y) < (attacker.attack === "special" ? 120 : 95);
  const forward = attacker.facing > 0 ? defender.x >= attacker.x : defender.x <= attacker.x;
  const close = Math.abs(attacker.x - defender.x) <= range;
  if (!vertical || !forward || !close) return;
  const blocking = now < defender.blockUntil && defender.facing === -attacker.facing && defender.guard > 0;
  let finalDamage = blocking ? Math.ceil(damage * 0.28) : damage;
  if (blocking) {
    defender.guard = clamp(defender.guard - damage * (attacker.attack === "special" ? 2.1 : 1.45), 0, EGG_MAX_GUARD);
    defender.energy = clamp(defender.energy + 7, 0, EGG_MAX_ENERGY);
    if (defender.guard <= 0) {
      finalDamage = Math.max(finalDamage, 9);
      defender.hurtUntil = now + 470;
      defender.blockUntil = 0;
      roomlessGuardBreak(defender, attacker);
    }
  }
  defender.hp = Math.max(0, defender.hp - finalDamage);
  if (defender.hurtUntil < now + (blocking ? 90 : 230)) {
    defender.hurtUntil = now + (blocking ? 90 : 230);
  }
  defender.vx = attacker.facing * (blocking ? 120 : (attacker.attack === "special" ? 360 : 260));
  defender.vy = Math.max(defender.vy, blocking ? 80 : (attacker.attack === "special" ? 220 : 155));
  defender.hitSparkUntil = now + 180;
  attacker.energy = clamp(attacker.energy + (attacker.attack === "special" ? 8 : 15), 0, EGG_MAX_ENERGY);
  attacker.combo = attacker.comboUntil > now ? attacker.combo + 1 : 1;
  attacker.comboUntil = now + 1600;
  attacker.attackHit = true;
}

function roomlessGuardBreak(defender, attacker) {
  defender.vx = attacker.facing * 300;
  defender.vy = Math.max(defender.vy, 190);
}

function faceEggPlayers(a, b) {
  if (Math.abs(a.x - b.x) < 8) return;
  a.facing = a.x < b.x ? 1 : -1;
  b.facing = b.x < a.x ? 1 : -1;
}

function resolveEggPush(a, b) {
  const minDistance = 84;
  const overlap = minDistance - Math.abs(a.x - b.x);
  if (overlap <= 0) return;
  const sign = a.x <= b.x ? -1 : 1;
  a.x += sign * overlap * 0.5;
  b.x -= sign * overlap * 0.5;
}

function expireEggPlayers(room) {
  const now = Date.now();
  for (const player of room.players) {
    if (room.mode === "ai" && player.deviceId === EGG_AI_DEVICE_ID) continue;
    if (player.connected && now - player.lastSeen > 45000) {
      Object.assign(player, newEggPlayer(player.slot), { wins: player.wins || 0 });
    }
  }
}

function updateEggAi(room, ai, human, now) {
  ai.connected = true;
  ai.ready = true;
  ai.lastSeen = now;
  if (room.status !== "running") {
    ai.input = {};
    return;
  }
  const distance = human.x - ai.x;
  const abs = Math.abs(distance);
  const remaining = room.roundEndsAt ? Math.max(0, room.roundEndsAt - now) : EGG_ROUND_MS;
  const behind = ai.hp + ai.guard * 0.08 < human.hp + human.guard * 0.08 - 8;
  const ahead = ai.hp > human.hp + 14;
  const urgent = remaining < 15_000 && behind;
  const preferredRange = ahead && !urgent ? 155 : 118;
  const pressure = urgent ? 1.55 : (behind ? 1.25 : (ahead ? 0.82 : 1));
  const canSpecial = ai.energy >= 62 && abs < 235 && Math.random() < 0.16 * pressure;
  const shouldBackstep = ahead && abs < 95 && human.attack && Math.random() < 0.28;
  ai.input = {
    left: shouldBackstep ? distance > 0 : abs > preferredRange && distance < 0,
    right: shouldBackstep ? distance < 0 : abs > preferredRange && distance > 0,
    up: (urgent || human.attack === "special") && abs < 185 && Math.random() < 0.045 * pressure,
    light: !canSpecial && abs < 118 && Math.random() < 0.24 * pressure,
    heavy: !canSpecial && abs < 170 && Math.random() < 0.12 * pressure,
    special: canSpecial,
    block: abs < 190 && human.attack && ai.guard > 15 && Math.random() < (ahead ? 0.68 : (human.attack === "special" ? 0.72 : 0.50)),
  };
}

function eggPublicState(room, deviceId, extra = {}) {
  const slot = extra.slot || (room.players.find((player) => player.deviceId === deviceId) || {}).slot || 0;
  return {
    ok: true,
    roomId: room.id,
    mode: room.mode,
    playerSlot: slot,
    status: room.status,
    message: room.message,
    roundWinner: room.roundWinner || 0,
    matchWinner: room.matchWinner || 0,
    winRounds: EGG_WIN_ROUNDS,
    roundMs: EGG_ROUND_MS,
    roundMsRemaining: room.status === "running" && room.roundEndsAt ? Math.max(0, room.roundEndsAt - Date.now()) : EGG_ROUND_MS,
    arenaWidth: EGG_ARENA_WIDTH,
    floorY: EGG_FLOOR_Y,
    now: Date.now(),
    countdownMs: room.status === "countdown" ? Math.max(0, room.countdownUntil - Date.now()) : 0,
    players: room.players.map((player) => ({
      slot: player.slot,
      name: player.name,
      connected: player.connected,
      ready: player.ready,
      hp: Math.max(0, Math.round(player.hp)),
      energy: Math.round(player.energy || 0),
      guard: Math.round(player.guard || 0),
      wins: player.wins || 0,
      x: Math.round(player.x),
      y: Math.round(player.y),
      facing: player.facing,
      attack: player.attack,
      combo: player.combo || 0,
      blocking: Date.now() < player.blockUntil,
      hurt: Date.now() < player.hurtUntil,
      hitSpark: Date.now() < player.hitSparkUntil,
    })),
  };
}

function safeEggId(value) {
  const text = String(value || "").trim();
  return text.replace(/[^A-Za-z0-9._:-]/g, "").slice(0, 80) || crypto.randomUUID();
}

function safeEggName(value) {
  const text = String(value || "").trim().replace(/\s+/g, " ");
  return text.slice(0, 12) || "Egg";
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function serveWeb(requestPath, res) {
  const pathname = requestPath === "/web" ? "/web/" : requestPath;
  let relative;
  if (pathname === "/web/") {
    relative = "index.html";
  } else if (pathname.startsWith("/web/")) {
    relative = pathname.slice("/web/".length);
  } else if (pathname.startsWith("/tools/")) {
    relative = pathname.slice(1);
  } else {
    sendText(res, 404, "not found");
    return;
  }
  serveStatic(path.join(webRoot, relative), webRoot, res);
}

function serveStatic(file, allowedRoot, res) {
  const normalized = path.normalize(file);
  const relative = path.relative(allowedRoot, normalized);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    sendText(res, 403, "forbidden");
    return;
  }
  const target = fs.existsSync(normalized) && fs.statSync(normalized).isDirectory()
    ? path.join(normalized, "index.html")
    : normalized;
  if (!fs.existsSync(target) || !fs.statSync(target).isFile()) {
    sendText(res, 404, "not found");
    return;
  }
  res.writeHead(200, {
    "Content-Type": types[path.extname(target)] || "application/octet-stream",
    "Cache-Control": "no-store",
  });
  fs.createReadStream(target).pipe(res);
}

function platformStatus() {
  return {
    ok: true,
    app: "aitools",
    port,
    tools: tools.map((tool) => toolStatus(tool)),
  };
}

function publicToolInfo(tool) {
  const status = toolStatus(tool);
  return {
    id: tool.id,
    name: tool.name,
    app: tool.app,
    packageName: tool.packageName || "",
    description: tool.description || "",
    webPath: tool.webPath || null,
    syncEnabled: Boolean(tool.sync && tool.sync.enabled),
    apk: status.apk,
  };
}

function toolStatus(tool) {
  const apk = findApk(tool);
  const sync = tool.sync && tool.sync.enabled ? syncStatus(tool) : { enabled: false };
  return {
    ok: true,
    id: tool.id,
    name: tool.name,
    app: tool.app,
    packageName: tool.packageName || "",
    description: tool.description || "",
    webPath: tool.webPath || null,
    apk: {
      hasApk: Boolean(apk),
      size: apk ? fs.statSync(apk).size : 0,
      updatedAt: apk ? fs.statSync(apk).mtime.toISOString() : null,
      download: `/api/tools/${encodeURIComponent(tool.id)}/apk`,
    },
    sync,
  };
}

function syncStatus(tool) {
  const latest = latestZipPath(tool);
  return {
    enabled: true,
    hasLatest: fs.existsSync(latest),
    size: fs.existsSync(latest) ? fs.statSync(latest).size : 0,
    updatedAt: fs.existsSync(latest) ? fs.statSync(latest).mtime.toISOString() : null,
  };
}

function findApk(tool) {
  const candidates = Array.isArray(tool.apkCandidates) ? tool.apkCandidates : [];
  return candidates
    .map((candidate) => path.resolve(projectRoot, candidate))
    .find((file) => fs.existsSync(file) && fs.statSync(file).isFile());
}

function serveApk(req, res, tool) {
  const apk = findApk(tool);
  if (!apk) {
    sendText(res, 404, `${tool.name} APK not found. Run scripts/build-${tool.id}-apk.sh first.`);
    return;
  }
  const stat = fs.statSync(apk);
  res.writeHead(200, {
    "Content-Type": "application/vnd.android.package-archive",
    "Content-Disposition": `attachment; filename="${tool.apkFileName || `${tool.id}-debug.apk`}"`,
    "Access-Control-Allow-Origin": "*",
    "Cache-Control": "no-store",
    "Content-Length": stat.size,
  });
  if (req.method === "HEAD") {
    res.end();
    return;
  }
  fs.createReadStream(apk).pipe(res);
}

function serveLatestZip(req, res, tool) {
  const latest = latestZipPath(tool);
  if (!fs.existsSync(latest)) {
    sendText(res, 404, "no latest.zip");
    return;
  }
  const stat = fs.statSync(latest);
  res.writeHead(200, {
    "Content-Type": "application/zip",
    "Content-Disposition": `attachment; filename="${tool.id}-latest.zip"`,
    "Access-Control-Allow-Origin": "*",
    "Content-Length": stat.size,
  });
  if (req.method === "HEAD") {
    res.end();
    return;
  }
  fs.createReadStream(latest).pipe(res);
}

function receiveLatestZip(req, res, tool) {
  const dir = syncDir(tool);
  const latest = latestZipPath(tool);
  const temp = path.join(dir, `upload-${Date.now()}.zip`);
  const output = fs.createWriteStream(temp);
  req.pipe(output);
  req.on("error", () => {
    safeUnlink(temp);
    sendText(res, 500, "upload failed");
  });
  output.on("finish", () => {
    if (fs.existsSync(latest)) {
      const stamp = new Date().toISOString().replace(/[:.]/g, "-");
      fs.copyFileSync(latest, path.join(historyDir(tool), `latest-${stamp}.zip`));
    }
    fs.renameSync(temp, latest);
    bootstrapStateFromLatest(tool, true);
    sendJson(res, { ok: true, size: fs.statSync(latest).size, updatedAt: new Date().toISOString() });
  });
}

function syncDir(tool) {
  const stateName = tool.sync && tool.sync.stateName ? tool.sync.stateName : tool.id;
  return path.join(projectRoot, "sync", stateName);
}

function historyDir(tool) {
  return path.join(syncDir(tool), "history");
}

function stateDir(tool) {
  return path.join(syncDir(tool), "state");
}

function latestZipPath(tool) {
  return path.join(syncDir(tool), "latest.zip");
}

function stateDbPath(tool) {
  return path.join(stateDir(tool), `${tool.id}.sqlite`);
}

function stateAttachmentsDir(tool) {
  return path.join(stateDir(tool), "attachments");
}

function readJson(req, res, callback) {
  const chunks = [];
  let size = 0;
  req.on("data", (chunk) => {
    size += chunk.length;
    if (size > 80 * 1024 * 1024) {
      sendText(res, 413, "payload too large");
      req.destroy();
      return;
    }
    chunks.push(chunk);
  });
  req.on("end", () => {
    try {
      const text = Buffer.concat(chunks).toString("utf8");
      callback(text.trim() ? JSON.parse(text) : {});
    } catch (error) {
      sendText(res, 400, "invalid json");
    }
  });
  req.on("error", () => sendText(res, 500, "request failed"));
}

function stateStore(tool) {
  const dbFile = stateDbPath(tool);
  let db = sqliteStores.get(dbFile);
  if (db) return db;
  db = new Database(dbFile);
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  db.exec(`
    CREATE TABLE IF NOT EXISTS metadata (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS entries (
      id TEXT PRIMARY KEY,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      deleted_at INTEGER,
      version INTEGER NOT NULL,
      device_id TEXT NOT NULL,
      author TEXT NOT NULL DEFAULT '小妈妈',
      tags TEXT NOT NULL,
      attachments TEXT NOT NULL,
      body TEXT NOT NULL,
      body_hash TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_entries_created_at ON entries(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_entries_updated_at ON entries(updated_at DESC);
    CREATE INDEX IF NOT EXISTS idx_entries_deleted_at ON entries(deleted_at);
  `);
  ensureEntryColumn(db, "author", "author TEXT NOT NULL DEFAULT '小妈妈'");
  sqliteStores.set(dbFile, db);
  return db;
}

function ensureEntryColumn(db, name, definition) {
  const columns = db.prepare("PRAGMA table_info(entries)").all().map((column) => column.name);
  if (!columns.includes(name)) {
    db.exec(`ALTER TABLE entries ADD COLUMN ${definition}`);
  }
}

function initializeStateStore(tool) {
  const dbFile = stateDbPath(tool);
  if (fs.existsSync(dbFile)) {
    const db = stateStore(tool);
    const row = db.prepare("SELECT COUNT(*) AS count FROM entries").get();
    if (row && row.count > 0) return;
  }
  bootstrapStateFromLatest(tool, true);
}

function loadState(tool) {
  const db = stateStore(tool);
  const updatedAt = db.prepare("SELECT value FROM metadata WHERE key = ?").get("updatedAt");
  const rows = db.prepare("SELECT * FROM entries ORDER BY created_at ASC, id ASC").all();
  return {
    app: tool.app,
    version: 2,
    updatedAt: updatedAt ? updatedAt.value : null,
    entries: rows.map(rowToEntry),
  };
}

function saveState(tool, state) {
  const db = stateStore(tool);
  state.updatedAt = new Date().toISOString();
  const write = db.transaction((nextState) => {
    db.prepare("DELETE FROM entries").run();
    const insert = db.prepare(`
      INSERT INTO entries (
        id, created_at, updated_at, deleted_at, version, device_id, author, tags, attachments, body, body_hash
      ) VALUES (
        @id, @createdAt, @updatedAt, @deletedAt, @version, @deviceId, @author, @tags, @attachments, @body, @bodyHash
      )
    `);
    for (const entry of nextState.entries.map(normalizeEntry)) {
      insert.run(entryToRow(entry));
    }
    db.prepare(`
      INSERT INTO metadata (key, value) VALUES (?, ?)
      ON CONFLICT(key) DO UPDATE SET value = excluded.value
    `).run("updatedAt", nextState.updatedAt);
    db.prepare(`
      INSERT INTO metadata (key, value) VALUES (?, ?)
      ON CONFLICT(key) DO UPDATE SET value = excluded.value
    `).run("app", tool.app);
    db.prepare(`
      INSERT INTO metadata (key, value) VALUES (?, ?)
      ON CONFLICT(key) DO UPDATE SET value = excluded.value
    `).run("version", "2");
  });
  write(state);
}

function entryToRow(entry) {
  return {
    id: String(entry.id),
    createdAt: timeValue(entry.createdAt, entry.id),
    updatedAt: timeValue(entry.updatedAt, entry.createdAt || entry.id),
    deletedAt: entry.deletedAt ? timeValue(entry.deletedAt, null) : null,
    version: Math.max(1, Number(entry.version || 1)),
    deviceId: entry.deviceId || "",
    author: diaryAuthor(entry.author),
    tags: JSON.stringify(Array.isArray(entry.tags) ? entry.tags : []),
    attachments: JSON.stringify(Array.isArray(entry.attachments) ? entry.attachments : []),
    body: entry.body || "",
    bodyHash: sha256(entry.body || ""),
  };
}

function rowToEntry(row) {
  return {
    id: numericId(row.id),
    createdAt: Number(row.created_at || row.id || Date.now()),
    updatedAt: Number(row.updated_at || row.created_at || row.id || Date.now()),
    deletedAt: row.deleted_at ? Number(row.deleted_at) : null,
    version: Math.max(1, Number(row.version || 1)),
    deviceId: row.device_id || "",
    author: diaryAuthor(row.author),
    tags: parseJsonArray(row.tags),
    attachments: parseJsonArray(row.attachments),
    body: row.body || "",
  };
}

function numericId(id) {
  const value = Number(id);
  return Number.isSafeInteger(value) && String(value) === String(id) ? value : id;
}

function parseJsonArray(value) {
  try {
    const parsed = JSON.parse(value || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
}

function normalizeEntry(entry) {
  const body = typeof entry.body === "string" ? entry.body : "";
  const id = Number(entry.id || Date.now());
  const createdAt = timeValue(entry.createdAt, id);
  const updatedAt = timeValue(entry.updatedAt, createdAt);
  return {
    id,
    createdAt,
    updatedAt,
    deletedAt: entry.deletedAt ? timeValue(entry.deletedAt, null) : null,
    version: Math.max(1, Number(entry.version || 1)),
    deviceId: typeof entry.deviceId === "string" ? entry.deviceId : "",
    author: diaryAuthor(entry.author),
    tags: Array.isArray(entry.tags) ? entry.tags : extractTags(body),
    attachments: Array.isArray(entry.attachments) ? entry.attachments.filter(isSafeAttachmentName) : extractAttachments(body),
    body,
  };
}

function diaryAuthor(value) {
  return typeof value === "string" && value.trim() ? value.trim() : DEFAULT_DIARY_AUTHOR;
}

function timeValue(value, fallback) {
  if (value === null || value === undefined || value === "") {
    return fallback === null ? null : timeValue(fallback, Date.now());
  }
  if (typeof value === "number" && Number.isFinite(value)) return value;
  const numeric = Number(value);
  if (Number.isFinite(numeric)) return numeric;
  const parsed = Date.parse(String(value));
  if (Number.isFinite(parsed)) return parsed;
  return fallback === null ? null : timeValue(fallback, Date.now());
}

function entryManifest(entry) {
  return {
    id: String(entry.id),
    version: Math.max(1, Number(entry.version || 1)),
    updatedAt: entry.updatedAt || entry.createdAt || null,
    deletedAt: entry.deletedAt || null,
    deviceId: entry.deviceId || "",
    author: diaryAuthor(entry.author),
    bodyHash: sha256(entry.body || ""),
    attachments: Array.isArray(entry.attachments) ? entry.attachments : [],
  };
}

function mergeEntry(state, incoming) {
  const index = state.entries.findIndex((entry) => String(entry.id) === String(incoming.id));
  if (index < 0) {
    state.entries.push(incoming);
    return true;
  }
  const current = state.entries[index];
  const currentManifest = entryManifest(current);
  const incomingManifest = entryManifest(incoming);
  if (incomingManifest.version > currentManifest.version) {
    state.entries[index] = incoming;
    return true;
  }
  if (incomingManifest.version < currentManifest.version) {
    return false;
  }
  if (incomingManifest.bodyHash === currentManifest.bodyHash && Boolean(incoming.deletedAt) === Boolean(current.deletedAt)) {
    state.entries[index] = incoming;
    return true;
  }
  return false;
}

function attachmentsForEntries(tool, entries) {
  const result = {};
  const names = new Set(entries.flatMap((entry) => Array.isArray(entry.attachments) ? entry.attachments : []));
  for (const name of names) {
    if (!isSafeAttachmentName(name)) continue;
    const file = path.join(stateAttachmentsDir(tool), name);
    if (fs.existsSync(file) && fs.statSync(file).isFile()) {
      result[name] = fs.readFileSync(file).toString("base64");
    }
  }
  return result;
}

function serveAttachment(req, res, tool, filename) {
  if (!isSafeAttachmentName(filename)) {
    sendText(res, 400, "bad attachment name");
    return;
  }
  const file = path.join(stateAttachmentsDir(tool), filename);
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) {
    sendText(res, 404, "attachment not found");
    return;
  }
  res.writeHead(200, {
    "Content-Type": types[path.extname(file).toLowerCase()] || "application/octet-stream",
    "Access-Control-Allow-Origin": "*",
    "Cache-Control": "no-store",
  });
  if (req.method === "HEAD") {
    res.end();
    return;
  }
  fs.createReadStream(file).pipe(res);
}

function bootstrapStateFromLatest(tool, force = false) {
  const dbFile = stateDbPath(tool);
  const latest = latestZipPath(tool);
  if (!force && fs.existsSync(dbFile)) return;
  if (!fs.existsSync(latest)) return;
  const temp = path.join(syncDir(tool), `import-${Date.now()}`);
  try {
    fs.rmSync(temp, { recursive: true, force: true });
    fs.mkdirSync(temp, { recursive: true });
    childProcess.execFileSync("unzip", ["-oq", latest, "-d", temp], { stdio: "ignore" });
    const stateFileName = `${tool.id}.json`;
    const stateJson = path.join(temp, stateFileName);
    const fallbackJson = path.join(temp, "diary.json");
    const importFile = fs.existsSync(stateJson) ? stateJson : fallbackJson;
    if (!fs.existsSync(importFile)) return;
    const raw = JSON.parse(fs.readFileSync(importFile, "utf8"));
    const entries = Array.isArray(raw) ? raw : raw.entries;
    if (!Array.isArray(entries)) return;
    const state = {
      app: tool.app,
      version: 2,
      updatedAt: fs.statSync(latest).mtime.toISOString(),
      entries: entries.map(normalizeEntry),
    };
    fs.rmSync(stateAttachmentsDir(tool), { recursive: true, force: true });
    fs.mkdirSync(stateAttachmentsDir(tool), { recursive: true });
    const attachmentsDir = path.join(temp, "attachments");
    if (fs.existsSync(attachmentsDir)) {
      for (const name of fs.readdirSync(attachmentsDir)) {
        if (isSafeAttachmentName(name)) {
          fs.copyFileSync(path.join(attachmentsDir, name), path.join(stateAttachmentsDir(tool), name));
        }
      }
    }
    saveState(tool, state);
  } catch (error) {
  } finally {
    fs.rmSync(temp, { recursive: true, force: true });
  }
}

function writeLatestZipFromState(tool, state) {
  const latest = latestZipPath(tool);
  if (fs.existsSync(latest)) {
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    fs.copyFileSync(latest, path.join(historyDir(tool), `latest-${stamp}.zip`));
  }
  const files = [
    {
      name: `${tool.id}.json`,
      data: Buffer.from(JSON.stringify({
        app: tool.app,
        version: 2,
        exportedAt: Date.now(),
        entries: state.entries,
      }), "utf8"),
    },
    { name: `${tool.id}.md`, data: Buffer.from(buildMarkdown(tool, state.entries), "utf8") },
  ];
  const names = new Set(state.entries.filter((entry) => !entry.deletedAt).flatMap((entry) => entry.attachments || []));
  for (const name of names) {
    if (!isSafeAttachmentName(name)) continue;
    const file = path.join(stateAttachmentsDir(tool), name);
    if (fs.existsSync(file) && fs.statSync(file).isFile()) {
      files.push({ name: `attachments/${name}`, data: fs.readFileSync(file) });
    }
  }
  fs.writeFileSync(latest, writeZip(files));
}

function buildMarkdown(tool, entries) {
  return `# ${tool.name}\n\n` + entries
    .filter((entry) => !entry.deletedAt)
    .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0))
    .map((entry) => `## ${entry.createdAt || entry.id}\n\n${String(entry.body || "").replace(/!\[图片\]\(attachment:([^)]+)\)/g, "![图片](attachments/$1)")}\n`)
    .join("\n");
}

function writeZip(files) {
  const localParts = [];
  const centralParts = [];
  let offset = 0;
  for (const file of files) {
    const name = Buffer.from(file.name, "utf8");
    const data = Buffer.from(file.data);
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0, 6);
    local.writeUInt16LE(0, 8);
    local.writeUInt16LE(0, 10);
    local.writeUInt16LE(0, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    localParts.push(local, name, data);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0, 8);
    central.writeUInt16LE(0, 10);
    central.writeUInt16LE(0, 12);
    central.writeUInt16LE(0, 14);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(data.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt16LE(0, 30);
    central.writeUInt16LE(0, 32);
    central.writeUInt16LE(0, 34);
    central.writeUInt16LE(0, 36);
    central.writeUInt32LE(0, 38);
    central.writeUInt32LE(offset, 42);
    centralParts.push(central, name);
    offset += local.length + name.length + data.length;
  }
  const centralSize = centralParts.reduce((sum, part) => sum + part.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(0, 4);
  end.writeUInt16LE(0, 6);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(centralSize, 12);
  end.writeUInt32LE(offset, 16);
  end.writeUInt16LE(0, 20);
  return Buffer.concat([...localParts, ...centralParts, end]);
}

const crcTable = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function sha256(text) {
  return crypto.createHash("sha256").update(String(text)).digest("hex");
}

function extractTags(body) {
  return Array.from(new Set(String(body || "").matchAll(/#([\p{L}\p{N}_-]+)/gu))).map((match) => match[1]);
}

function extractAttachments(body) {
  return Array.from(new Set(String(body || "").matchAll(/!\[图片\]\(attachment:([^)]+)\)/g))).map((match) => match[1]).filter(isSafeAttachmentName);
}

function isSafeAttachmentName(name) {
  return typeof name === "string" && /^[A-Za-z0-9._-]+$/.test(name) && !name.includes("..");
}

function isApkPath(pathname) {
  return pathname === "/app.apk"
    || pathname === "/api/app.apk"
    || pathname === "/apk"
    || pathname === "/download/app.apk"
    || pathname === "/app-debug.apk";
}

function localAddressForRemote(remoteAddress) {
  return localAddressesForRemote(remoteAddress)[0] || "127.0.0.1";
}

function localAddressesForRemote(remoteAddress) {
  const remote = normalizeRemoteAddress(remoteAddress);
  if (!remote || remote === "127.0.0.1" || remote === "::1") {
    return ["127.0.0.1"];
  }
  const addresses = [];
  for (const interfaces of Object.values(os.networkInterfaces())) {
    for (const item of interfaces || []) {
      if (item.family === "IPv4" && !item.internal) {
        addresses.push(item.address);
      }
    }
  }
  const remotePrefix = remote.split(".").slice(0, 3).join(".");
  const sameSubnet = addresses.filter((address) => address.startsWith(remotePrefix + "."));
  const ordered = [...sameSubnet, ...addresses.filter((address) => !sameSubnet.includes(address))];
  return ordered.length ? ordered : ["127.0.0.1"];
}

function normalizeRemoteAddress(address) {
  if (!address) return "";
  return String(address).replace(/^::ffff:/, "");
}

function sendJson(res, data, options = {}) {
  res.writeHead(200, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Cache-Control": "no-store",
  });
  res.end(options.compact ? JSON.stringify(data) : JSON.stringify(data, null, 2));
}

function sendText(res, code, text) {
  res.writeHead(code, {
    "Content-Type": "text/plain; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Cache-Control": "no-store",
  });
  res.end(text);
}

function redirect(res, location) {
  res.writeHead(302, { Location: location });
  res.end();
}

function safeUnlink(file) {
  try {
    fs.unlinkSync(file);
  } catch (error) {
  }
}
