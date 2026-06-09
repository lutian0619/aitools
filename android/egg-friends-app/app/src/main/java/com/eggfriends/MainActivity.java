package com.eggfriends;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "egg_friends";
    private static final String DEVICE_ID = "device_id";
    private static final String SELECTED_FRIEND = "selected_friend";
    private static final String AI_LEVEL = "ai_level";
    private static final String DISCOVERY_MESSAGE = "aitools-discovery";
    private static final int SERVICE_PORT = 8788;
    private static final int MODE_PVP = 1;
    private static final int MODE_AI = 2;
    private static final int INK = Color.rgb(47, 41, 37);
    private static final int MUTED = Color.rgb(117, 104, 95);
    private static final int PAPER = Color.rgb(253, 239, 239);
    private static final int CARD = Color.rgb(255, 249, 246);
    private static final int LINE = Color.rgb(218, 208, 194);
    private static final int ACCENT = Color.rgb(138, 113, 94);
    private static final float EGG_ARENA_WIDTH = 1200f;
    private static final float EGG_FLOOR_Y = 0f;
    private static final float EGG_MAX_HP = 140f;
    private static final float EGG_MAX_ENERGY = 100f;
    private static final float EGG_MAX_GUARD = 115f;
    private static final int EGG_WIN_ROUNDS = 2;
    private static final long EGG_ROUND_MS = 60000L;
    private static final float EGG_GRAVITY = 3600f;
    private static final float EGG_JUMP_VELOCITY = 1080f;
    private static final float EGG_AIR_PASS_HEIGHT = 10f;
    private static final float EGG_MOVE_SPEED = 360f;
    private static final float EGG_BLOCK_MOVE_SPEED = 120f;
    private static final float EGG_GUARD_RECOVERY = 26f;
    private static final float EGG_GUARD_DRAIN = 20f;
    private static final float EGG_ENERGY_RECOVERY = 4.5f;
    private static final float EGG_SKILL_COST = 35f;
    private static final float EGG_ULTIMATE_COST = 100f;
    private static final float EGG_SPECIAL_DASH = 190f;
    private static final long EGG_LIGHT_ACTIVE_MS = 200L;
    private static final long EGG_LIGHT_COOLDOWN_MS = 330L;
    private static final long EGG_HEAVY_ACTIVE_MS = 330L;
    private static final long EGG_HEAVY_COOLDOWN_MS = 570L;
    private static final long EGG_SPECIAL_ACTIVE_MS = 470L;
    private static final long EGG_SPECIAL_COOLDOWN_MS = 820L;
    private static final long EGG_BLOCK_HURT_MS = 80L;
    private static final long EGG_HIT_HURT_MS = 250L;
    private static final long EGG_GUARD_BREAK_HURT_MS = 430L;
    private static final long EGG_GUARD_BREAK_LABEL_MS = 650L;
    private static final long INPUT_BUFFER_MS = 135L;
    private static final String[] FRIEND_NAMES = new String[]{"Egg", "小米", "Meo", "小红"};
    private static final FighterStats[] FRIEND_STATS = new FighterStats[]{
            new FighterStats("均衡", 260f, 120f, 360f, 3, 7, 10),
            new FighterStats("疾行", 225f, 105f, 430f, 3, 6, 9),
            new FighterStats("铁壁", 305f, 145f, 300f, 3, 7, 10),
            new FighterStats("重击", 245f, 112f, 335f, 4, 9, 12)
    };

    private final AtomicBoolean alive = new AtomicBoolean(false);
    private final Object localGameLock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EggGameView gameView;
    private String deviceId;
    private volatile String serviceUrl = "";
    private volatile String roomId = "pvp-default";
    private volatile int gameMode = MODE_AI;
    private volatile boolean joined = false;
    private volatile String connectionText = "正在寻找电脑服务";
    private volatile long lastStateAt = 0L;
    private volatile InputState input = new InputState();
    private volatile GameState state = GameState.empty();
    private volatile boolean settingsOpen = false;
    private volatile int selectedFriend = 0;
    private volatile long jumpBufferedUntil = 0L;
    private volatile long lightBufferedUntil = 0L;
    private volatile long heavyBufferedUntil = 0L;
    private volatile long specialBufferedUntil = 0L;
    private volatile long ultimateBufferedUntil = 0L;
    private volatile long readyBufferedUntil = 0L;
    private volatile long restartBufferedUntil = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.rgb(18, 24, 31));
        getWindow().setNavigationBarColor(Color.rgb(18, 24, 31));
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        deviceId = preferences.getString(DEVICE_ID, "");
        selectedFriend = Math.max(0, Math.min(FRIEND_NAMES.length - 1, preferences.getInt(SELECTED_FRIEND, 0)));
        if (deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            preferences.edit().putString(DEVICE_ID, deviceId).apply();
        }
        gameView = new EggGameView(this);
        state = newLocalAiGame(savedAiLevel());
        setContentView(gameView);
        alive.set(true);
        startNetworkLoop();
    }

    @Override
    protected void onDestroy() {
        alive.set(false);
        super.onDestroy();
    }

    private void startNetworkLoop() {
        Thread thread = new Thread(() -> {
            while (alive.get()) {
                try {
                    if (gameMode == MODE_AI) {
                        tickLocalAiGame();
                        sleep(33);
                        continue;
                    }
                    if (serviceUrl.isEmpty()) {
                        connectionText = "正在寻找电脑服务";
                        String found = discoverService();
                        if (found != null) {
                            serviceUrl = found;
                            joined = false;
                        } else {
                            sleep(900);
                            continue;
                        }
                    }
                    if (!joined) {
                        roomId = targetRoomId();
                        JSONObject payload = new JSONObject();
                        payload.put("deviceId", deviceId);
                        payload.put("roomId", roomId);
                        payload.put("mode", gameMode == MODE_AI ? "ai" : "pvp");
                        payload.put("name", localFriendName());
                        JSONObject response = postJson(serviceUrl + "/api/egg-friends/join", payload);
                        applyState(response);
                        joined = response.optBoolean("ok", false) && response.optInt("playerSlot", 0) > 0;
                    } else {
                        InputState snapshot = bufferedInputSnapshot();
                        roomId = targetRoomId();
                        JSONObject payload = new JSONObject();
                        payload.put("deviceId", deviceId);
                        payload.put("roomId", roomId);
                        payload.put("mode", gameMode == MODE_AI ? "ai" : "pvp");
                        payload.put("name", localFriendName());
                        payload.put("left", snapshot.left);
                        payload.put("right", snapshot.right);
                        payload.put("jump", snapshot.jump);
                        payload.put("light", snapshot.light);
                        payload.put("heavy", snapshot.heavy);
                        payload.put("special", snapshot.special);
                        payload.put("ultimate", snapshot.ultimate);
                        payload.put("block", snapshot.block);
                        payload.put("ready", snapshot.ready);
                        payload.put("restart", snapshot.restart);
                        JSONObject response = postJson(serviceUrl + "/api/egg-friends/input", payload);
                        if (!response.optBoolean("ok", true) && "not joined".equals(response.optString("error"))) {
                            joined = false;
                        }
                        applyState(response.optJSONObject("state") != null ? response.optJSONObject("state") : response);
                        if (snapshot.restart) {
                            InputState next = input.copy();
                            next.restart = false;
                            input = next;
                            restartBufferedUntil = 0L;
                        }
                    }
                    connectionText = "已连接 " + serviceUrl.replace("http://", "");
                    sleep(45);
                } catch (Exception error) {
                    connectionText = "连接中断，重新发现";
                    serviceUrl = "";
                    joined = false;
                    sleep(700);
                }
            }
        }, "egg-friends-net");
        thread.start();
    }

    private void applyState(JSONObject json) {
        if (json == null) return;
        GameState next = GameState.fromJson(json);
        if (next.ok) {
            state = next;
            roomId = next.roomId;
            lastStateAt = System.currentTimeMillis();
            mainHandler.post(() -> gameView.invalidate());
        }
    }

    private String targetRoomId() {
        if (gameMode == MODE_AI) {
            String compact = deviceId.replace("-", "");
            if (compact.length() > 12) compact = compact.substring(0, 12);
            return "ai-" + compact;
        }
        return "pvp-default";
    }

    private String discoverService() {
        String udp = discoverServiceByUdp();
        if (udp != null) return udp;
        for (String prefix : localIpv4Prefixes()) {
            String candidate = probeService("http://" + prefix + ".1:" + SERVICE_PORT);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private String discoverServiceByUdp() {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        targets.add("255.255.255.255");
        for (String prefix : localIpv4Prefixes()) {
            targets.add(prefix + ".255");
        }
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(650);
            byte[] payload = DISCOVERY_MESSAGE.getBytes(StandardCharsets.UTF_8);
            for (int round = 0; round < 2; round++) {
                for (String target : targets) {
                    try {
                        DatagramPacket packet = new DatagramPacket(payload, payload.length, InetAddress.getByName(target), SERVICE_PORT);
                        socket.send(packet);
                    } catch (Exception ignored) {
                    }
                }
            }
            long deadline = System.currentTimeMillis() + 2300L;
            byte[] buffer = new byte[2048];
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    String body = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(body);
                    LinkedHashSet<String> candidates = new LinkedHashSet<>();
                    addCandidate(candidates, json.optString("url", ""));
                    JSONArray urls = json.optJSONArray("urls");
                    if (urls != null) {
                        for (int i = 0; i < urls.length(); i++) addCandidate(candidates, urls.optString(i, ""));
                    }
                    addCandidate(candidates, "http://" + response.getAddress().getHostAddress() + ":" + SERVICE_PORT);
                    for (String candidate : candidates) {
                        String found = probeService(candidate);
                        if (found != null) return found;
                    }
                } catch (SocketTimeoutException ignored) {
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void addCandidate(LinkedHashSet<String> candidates, String value) {
        String url = normalizeServiceUrl(value);
        if (!url.isEmpty()) candidates.add(url);
    }

    private String probeService(String baseUrl) {
        HttpURLConnection connection = null;
        try {
            String url = normalizeServiceUrl(baseUrl);
            if (url.isEmpty()) return null;
            connection = (HttpURLConnection) new URL(url + "/api/status").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(850);
            connection.setReadTimeout(850);
            if (connection.getResponseCode() != 200) return null;
            JSONObject status = new JSONObject(readSmallBody(connection, 4096));
            return "aitools".equals(status.optString("app", "")) ? url : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONObject postJson(String target, JSONObject payload) throws Exception {
        HttpURLConnection connection = null;
        try {
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(target).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(900);
            connection.setReadTimeout(900);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            String body = readSmallBody(connection, 64 * 1024);
            if (code < 200 || code >= 300) throw new IllegalStateException(body);
            return new JSONObject(body);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readSmallBody(HttpURLConnection connection, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        try (InputStream input = connection.getInputStream()) {
            int count;
            while ((count = input.read(buffer)) != -1 && output.size() < limit) {
                output.write(buffer, 0, count);
            }
        }
        return output.toString("UTF-8");
    }

    private String normalizeServiceUrl(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return "";
        if (!text.startsWith("http://") && !text.startsWith("https://")) text = "http://" + text;
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private List<String> localIpv4Prefixes() {
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    String host = address.getHostAddress();
                    int dot = host.lastIndexOf('.');
                    if (dot > 0 && isPrivateIpv4(host)) prefixes.add(host.substring(0, dot));
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(prefixes);
    }

    private boolean isPrivateIpv4(String host) {
        return host.startsWith("10.") || host.startsWith("192.168.") || host.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    private void requestReconnect() {
        serviceUrl = "";
        joined = false;
        connectionText = "正在重新发现";
    }

    private void selectMode(int mode) {
        if (gameMode == mode) return;
        gameMode = mode;
        roomId = targetRoomId();
        joined = false;
        input = new InputState();
        clearInputBuffers();
        if (mode == MODE_AI) {
            synchronized (localGameLock) {
                state = newLocalAiGame(Math.max(1, state.aiLevel));
            }
            connectionText = "本机人机练习";
        } else {
            state = GameState.empty();
            connectionText = serviceUrl.isEmpty() ? "正在寻找电脑服务" : "已连接 " + serviceUrl.replace("http://", "");
        }
        mainHandler.post(() -> gameView.invalidate());
    }

    private void showSettingsPage() {
        settingsOpen = true;
        mainHandler.post(() -> gameView.invalidate());
    }

    private void hideSettingsPage() {
        settingsOpen = false;
        mainHandler.post(() -> gameView.invalidate());
    }

    private String localFriendName() {
        int index = Math.max(0, Math.min(FRIEND_NAMES.length - 1, selectedFriend));
        return FRIEND_NAMES[index];
    }

    private static FighterStats fighterStatsForName(String name) {
        for (int i = 0; i < FRIEND_NAMES.length; i++) {
            if (FRIEND_NAMES[i].equals(name)) return FRIEND_STATS[i];
        }
        return FRIEND_STATS[0];
    }

    private int savedAiLevel() {
        return Math.max(1, Math.min(3, getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(AI_LEVEL, 1)));
    }

    private void saveAiLevel(int level) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(AI_LEVEL, Math.max(1, Math.min(3, level)))
                .apply();
    }

    private InputState bufferedInputSnapshot() {
        InputState snapshot = input.copy();
        long now = System.currentTimeMillis();
        snapshot.jump = snapshot.jump || now < jumpBufferedUntil;
        snapshot.light = snapshot.light || now < lightBufferedUntil;
        snapshot.heavy = snapshot.heavy || now < heavyBufferedUntil;
        snapshot.special = snapshot.special || now < specialBufferedUntil;
        snapshot.ultimate = snapshot.ultimate || now < ultimateBufferedUntil;
        snapshot.ready = snapshot.ready || now < readyBufferedUntil;
        snapshot.restart = snapshot.restart || now < restartBufferedUntil;
        return snapshot;
    }

    private void clearInputBuffers() {
        jumpBufferedUntil = 0L;
        lightBufferedUntil = 0L;
        heavyBufferedUntil = 0L;
        specialBufferedUntil = 0L;
        ultimateBufferedUntil = 0L;
        readyBufferedUntil = 0L;
        restartBufferedUntil = 0L;
    }

    private void selectFriend(int index) {
        int nextIndex = Math.max(0, Math.min(FRIEND_NAMES.length - 1, index));
        if (selectedFriend == nextIndex) return;
        selectedFriend = nextIndex;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(SELECTED_FRIEND, selectedFriend).apply();
        joined = false;
        input = new InputState();
        clearInputBuffers();
        if (gameMode == MODE_AI) {
            synchronized (localGameLock) {
                int level = Math.max(1, state.aiLevel);
                state = newLocalAiGame(level);
            }
            connectionText = "本机人机练习";
        }
        mainHandler.post(() -> gameView.invalidate());
    }

    private GameState newLocalAiGame(int aiLevel) {
        GameState game = GameState.empty();
        game.roomId = targetRoomId();
        game.mode = "ai";
        game.playerSlot = 1;
        game.status = "waiting";
        game.message = "点开始挑战蛋卷教练";
        game.winRounds = EGG_WIN_ROUNDS;
        game.aiLevel = Math.max(1, Math.min(3, aiLevel));
        game.roundMs = EGG_ROUND_MS;
        game.roundMsRemaining = EGG_ROUND_MS;
        game.arenaWidth = EGG_ARENA_WIDTH;
        game.players[0] = new PlayerState(1);
        game.players[1] = new PlayerState(2);
        resetLocalPlayer(game.players[0], localFriendName(), true);
        resetLocalPlayer(game.players[1], "蛋卷教练", true);
        game.players[1].ready = true;
        game.updatedAt = System.currentTimeMillis();
        return game;
    }

    private void resetLocalPlayer(PlayerState player, String name, boolean resetWins) {
        int wins = resetWins ? 0 : player.wins;
        FighterStats stats = fighterStatsForName(name);
        player.name = name;
        player.connected = true;
        player.ready = player.slot == 2;
        player.archetype = stats.archetype;
        player.maxHp = stats.maxHp;
        player.maxGuard = stats.maxGuard;
        player.moveSpeed = stats.moveSpeed;
        player.lightDamage = stats.lightDamage;
        player.heavyDamage = stats.heavyDamage;
        player.specialDamage = stats.specialDamage;
        player.hp = stats.maxHp;
        player.energy = 28f;
        player.guard = stats.maxGuard;
        player.wins = wins;
        player.x = player.slot == 1 ? 290f : 910f;
        player.y = EGG_FLOOR_Y;
        player.vx = 0f;
        player.vy = 0f;
        player.facing = player.slot == 1 ? 1 : -1;
        player.attack = "";
        player.combo = 0;
        player.blocking = false;
        player.hurt = false;
        player.hitSpark = false;
        player.guardBroken = false;
        player.attackUntil = 0L;
        player.attackHit = false;
        player.cooldownUntil = 0L;
        player.hurtUntil = 0L;
        player.blockUntil = 0L;
        player.comboUntil = 0L;
        player.hitSparkUntil = 0L;
        player.guardBreakUntil = 0L;
        player.skillUntil = 0L;
        player.ultimateUntil = 0L;
        player.invincibleUntil = 0L;
        player.input = new InputState();
    }

    private void tickLocalAiGame() {
        synchronized (localGameLock) {
            GameState game = state;
            if (!"ai".equals(game.mode) || game.players[0] == null || game.players[1] == null) {
                game = newLocalAiGame(savedAiLevel());
                state = game;
            }
            long now = System.currentTimeMillis();
            float dt = Math.min(0.05f, Math.max(0.001f, (now - game.updatedAt) / 1000f));
            game.updatedAt = now;
            game.roomId = targetRoomId();
            game.playerSlot = 1;
            game.mode = "ai";
            game.players[0].input = bufferedInputSnapshot();
            game.players[0].ready = game.players[0].ready || game.players[0].input.ready;
            game.players[1].connected = true;
            game.players[1].ready = true;
            if (game.players[0].input.restart && ("ended".equals(game.status) || "match-ended".equals(game.status))) {
                if ("match-ended".equals(game.status)) resetLocalMatch(game);
                resetLocalRound(game, true);
                InputState next = input.copy();
                next.restart = false;
                next.ready = false;
                input = next;
                restartBufferedUntil = 0L;
                readyBufferedUntil = 0L;
            }
            if ("waiting".equals(game.status) && game.players[0].ready) {
                game.status = "countdown";
                game.countdownUntil = now + 900L;
                game.countdownMs = 900L;
                game.message = "准备开打";
            }
            if ("countdown".equals(game.status)) {
                game.countdownMs = Math.max(0L, game.countdownUntil - now);
                if (game.countdownMs <= 0L) {
                    game.status = "running";
                    game.message = "开打";
                    game.roundEndsAt = now + EGG_ROUND_MS;
                    game.roundMsRemaining = EGG_ROUND_MS;
                }
            }
            if ("running".equals(game.status)) {
                game.roundMsRemaining = Math.max(0L, game.roundEndsAt - now);
                if (game.roundMsRemaining <= 0L) {
                    finishLocalRound(game, scoreLocalWinner(game), true);
                } else {
                    int steps = Math.max(1, (int) Math.ceil(dt / 0.016f));
                    float step = dt / steps;
                    for (int i = 0; i < steps && "running".equals(game.status); i++) {
                        simulateLocalStep(game, step, now);
                    }
                    game.roundMsRemaining = Math.max(0L, game.roundEndsAt - now);
                }
            } else if (!"countdown".equals(game.status)) {
                game.roundMsRemaining = EGG_ROUND_MS;
                game.countdownMs = 0L;
            }
            refreshLocalFlags(game, now);
            connectionText = "本机人机练习";
            lastStateAt = now;
        }
        mainHandler.post(() -> gameView.invalidate());
    }

    private void resetLocalMatch(GameState game) {
        game.players[0].wins = 0;
        game.players[1].wins = 0;
        game.matchWinner = 0;
        game.roundWinner = 0;
    }

    private void resetLocalRound(GameState game, boolean keepReady) {
        int p1Wins = game.players[0].wins;
        int p2Wins = game.players[1].wins;
        resetLocalPlayer(game.players[0], localFriendName(), true);
        resetLocalPlayer(game.players[1], "蛋卷教练", true);
        game.players[0].wins = p1Wins;
        game.players[1].wins = p2Wins;
        game.players[0].ready = keepReady;
        game.players[1].ready = true;
        game.roundWinner = 0;
        game.matchWinner = 0;
        game.status = keepReady ? "countdown" : "waiting";
        game.message = keepReady ? "准备开打" : "点开始挑战蛋卷教练";
        game.countdownUntil = keepReady ? System.currentTimeMillis() + 900L : 0L;
        game.roundEndsAt = 0L;
        game.roundMsRemaining = EGG_ROUND_MS;
    }

    private void simulateLocalStep(GameState game, float dt, long now) {
        PlayerState human = game.players[0];
        PlayerState ai = game.players[1];
        updateLocalAi(game, ai, human, now);
        faceLocalPlayers(human, ai);
        updateLocalPlayer(human, ai, dt, now);
        updateLocalPlayer(ai, human, dt, now);
        resolveLocalPush(human, ai);
        for (PlayerState player : game.players) {
            player.x = clamp(player.x, 60f, EGG_ARENA_WIDTH - 60f);
            if (player.y < EGG_FLOOR_Y) {
                player.y = EGG_FLOOR_Y;
                player.vy = 0f;
            }
        }
        checkLocalHit(human, ai, now);
        checkLocalHit(ai, human, now);
        if (human.hp <= 0f || ai.hp <= 0f) {
            finishLocalRound(game, knockoutLocalWinner(game), false);
        }
    }

    private void updateLocalAi(GameState game, PlayerState ai, PlayerState human, long now) {
        float distance = human.x - ai.x;
        float abs = Math.abs(distance);
        long remaining = game.roundEndsAt > 0L ? Math.max(0L, game.roundEndsAt - now) : EGG_ROUND_MS;
        int level = Math.max(1, Math.min(3, game.aiLevel));
        boolean behind = ai.hp + ai.guard * 0.08f < human.hp + human.guard * 0.08f - 8f;
        boolean ahead = ai.hp > human.hp + 14f;
        boolean urgent = remaining < 15000L && behind;
        float preferredRange = level == 1 ? 138f : (ahead && !urgent ? 160f : 108f);
        float levelPressure = level == 3 ? 1.36f : (level == 2 ? 1.08f : 0.76f);
        float pressure = (urgent ? 1.55f : (behind ? 1.25f : (ahead ? 0.82f : 1f))) * levelPressure;
        float specialRate = level == 3 ? 0.22f : (level == 2 ? 0.15f : 0.07f);
        float lightRate = level == 3 ? 0.30f : (level == 2 ? 0.23f : 0.14f);
        float heavyRate = level == 3 ? 0.16f : (level == 2 ? 0.11f : 0.06f);
        boolean canUltimate = ai.energy >= 100f && (behind || level == 3) && Math.random() < 0.18f * pressure;
        boolean canSpecial = !canUltimate && ai.energy >= 42f && Math.random() < specialRate * pressure;
        boolean shouldBackstep = ahead && abs < 95f && !human.attack.isEmpty() && Math.random() < (0.22f + level * 0.05f);
        float blockBase = level == 3 ? 0.50f : (level == 2 ? 0.42f : 0.28f);
        InputState next = new InputState();
        next.left = shouldBackstep ? distance > 0f : abs > preferredRange && distance < 0f;
        next.right = shouldBackstep ? distance < 0f : abs > preferredRange && distance > 0f;
        next.jump = (urgent || "special".equals(human.attack) || level == 3) && abs < 185f && Math.random() < (0.032f + level * 0.009f) * pressure;
        next.light = !canSpecial && !canUltimate && abs < 118f && Math.random() < lightRate * pressure;
        next.heavy = !canSpecial && !canUltimate && abs < 170f && Math.random() < heavyRate * pressure;
        next.special = canSpecial;
        next.ultimate = canUltimate;
        next.block = abs < 190f && !human.attack.isEmpty() && ai.guard > 15f
                && Math.random() < (blockBase + (ahead ? 0.14f : 0f) + ("special".equals(human.attack) ? 0.18f : 0f));
        ai.input = next;
    }

    private void updateLocalPlayer(PlayerState player, PlayerState opponent, float dt, long now) {
        InputState controls = player.input == null ? new InputState() : player.input;
        boolean stunned = now < player.hurtUntil;
        boolean attacking = now < player.attackUntil;
        boolean grounded = player.y <= EGG_FLOOR_Y + 0.5f;
        if (player.comboUntil <= now) player.combo = 0;
        if (!controls.block || stunned || attacking) {
            player.guard = clamp(player.guard + EGG_GUARD_RECOVERY * dt, 0f, player.maxGuard);
        }
        player.energy = clamp(player.energy + EGG_ENERGY_RECOVERY * dt, 0f, EGG_MAX_ENERGY);
        if (controls.block && grounded && !attacking && player.guard > 5f) {
            player.blockUntil = now + 120L;
                player.guard = clamp(player.guard - EGG_GUARD_DRAIN * dt, 0f, player.maxGuard);
        }
        if (!stunned && !attacking) {
            int direction = 0;
            if (controls.left) direction -= 1;
            if (controls.right) direction += 1;
                float speedBoost = now < player.skillUntil || now < player.ultimateUntil ? skillSpeedBoost(player) : 1f;
                float blockSpeed = Math.max(92f, player.moveSpeed * speedBoost * (EGG_BLOCK_MOVE_SPEED / EGG_MOVE_SPEED));
                player.vx = direction * (now < player.blockUntil ? blockSpeed : player.moveSpeed * speedBoost);
            if (direction != 0) player.facing = direction > 0 ? 1 : -1;
            if (controls.jump && grounded) player.vy = EGG_JUMP_VELOCITY;
            if (controls.ultimate && player.energy >= EGG_ULTIMATE_COST && now >= player.cooldownUntil) {
                useLocalUltimate(player, now);
            } else if (controls.special && player.energy >= EGG_SKILL_COST && now >= player.cooldownUntil) {
                useLocalSkill(player, now);
            }
            if ((controls.light || controls.heavy) && now >= player.cooldownUntil) {
                player.attack = controls.heavy ? "heavy" : "light";
                player.attackUntil = now + (controls.heavy ? EGG_HEAVY_ACTIVE_MS : EGG_LIGHT_ACTIVE_MS);
                player.cooldownUntil = now + (controls.heavy ? EGG_HEAVY_COOLDOWN_MS : EGG_LIGHT_COOLDOWN_MS);
                player.attackHit = false;
                player.vx *= 0.35f;
            }
        } else if (attacking) {
            player.vx *= 0.82f;
        } else {
            player.vx *= 0.72f;
        }
        player.vy -= EGG_GRAVITY * dt;
        player.x += player.vx * dt;
        player.y += player.vy * dt;
        if (player.attackUntil <= now) player.attack = "";
    }

    private void useLocalSkill(PlayerState player, long now) {
        player.energy = clamp(player.energy - EGG_SKILL_COST, 0f, EGG_MAX_ENERGY);
        player.cooldownUntil = now + 430L;
        if ("Egg".equals(player.name)) {
            player.hp = Math.min(player.maxHp, player.hp + 24f);
            player.cooldownUntil = Math.max(now, player.cooldownUntil - 160L);
            player.skillUntil = now + 1200L;
        } else if ("小米".equals(player.name)) {
            player.skillUntil = now + 3000L;
        } else if ("小红".equals(player.name)) {
            player.skillUntil = now + 4000L;
        } else if ("Meo".equals(player.name)) {
            player.hp = Math.min(player.maxHp, player.hp + 18f);
            player.skillUntil = now + 5000L;
        } else {
            player.skillUntil = now + 1800L;
        }
    }

    private void useLocalUltimate(PlayerState player, long now) {
        player.energy = 0f;
        player.cooldownUntil = now + 680L;
        if ("Egg".equals(player.name)) {
            player.ultimateUntil = now + 5000L;
        } else if ("小米".equals(player.name)) {
            player.ultimateUntil = now + 4000L;
            player.cooldownUntil = now + 160L;
        } else if ("小红".equals(player.name)) {
            player.attack = "ultimate";
            player.attackUntil = now + 520L;
            player.attackHit = false;
            player.vx += player.facing * (EGG_SPECIAL_DASH + 90f);
        } else if ("Meo".equals(player.name)) {
            player.hp = Math.min(player.maxHp, player.hp + 28f);
            player.ultimateUntil = now + 5000L;
            player.invincibleUntil = now + 2400L;
        } else {
            player.ultimateUntil = now + 3000L;
        }
    }

    private float skillSpeedBoost(PlayerState player) {
        if ("小米".equals(player.name)) return 1.6f;
        if ("Egg".equals(player.name)) return 1.18f;
        return 1f;
    }

    private float damageMultiplier(PlayerState player, long now) {
        if ("小红".equals(player.name) && now < player.skillUntil) return 1.45f;
        if ("Egg".equals(player.name) && now < player.ultimateUntil) return 1.65f;
        return 1f;
    }

    private float incomingDamageMultiplier(PlayerState defender, long now) {
        if (now < defender.invincibleUntil) return 0f;
        if ("Meo".equals(defender.name) && now < defender.skillUntil) return 0.55f;
        return 1f;
    }

    private void checkLocalHit(PlayerState attacker, PlayerState defender, long now) {
        if (attacker.attack.isEmpty() || attacker.attackHit || now >= attacker.attackUntil) return;
        float range = "ultimate".equals(attacker.attack) ? 190f : ("heavy".equals(attacker.attack) ? 150f : 112f);
        int baseDamage = "ultimate".equals(attacker.attack)
                ? attacker.specialDamage + 10
                : ("heavy".equals(attacker.attack) ? attacker.heavyDamage : attacker.lightDamage);
        int damage = Math.round((baseDamage + Math.min(2, attacker.combo) + (attacker.y > 20f ? 1 : 0)) * damageMultiplier(attacker, now));
        damage = Math.round(damage * incomingDamageMultiplier(defender, now));
        boolean vertical = Math.abs(attacker.y - defender.y) < ("ultimate".equals(attacker.attack) ? 120f : 95f);
        boolean forward = attacker.facing > 0 ? defender.x >= attacker.x : defender.x <= attacker.x;
        boolean close = Math.abs(attacker.x - defender.x) <= range;
        if (!vertical || !forward || !close) return;
        boolean blocking = now < defender.blockUntil && defender.facing == -attacker.facing && defender.guard > 0f;
        int finalDamage = blocking ? (int) Math.ceil(damage * 0.28f) : damage;
        if (blocking) {
            defender.guard = clamp(defender.guard - damage * ("ultimate".equals(attacker.attack) ? 2.1f : 1.45f), 0f, defender.maxGuard);
            defender.energy = clamp(defender.energy + 7f, 0f, EGG_MAX_ENERGY);
            if (defender.guard <= 0f) {
                finalDamage = Math.max(finalDamage, 5);
                defender.hurtUntil = now + EGG_GUARD_BREAK_HURT_MS;
                defender.blockUntil = 0L;
                defender.guardBreakUntil = now + EGG_GUARD_BREAK_LABEL_MS;
                defender.vx = attacker.facing * 300f;
                defender.vy = Math.max(defender.vy, 190f);
            }
        }
        defender.hp = Math.max(0f, defender.hp - finalDamage);
        if (defender.hurtUntil < now + (blocking ? EGG_BLOCK_HURT_MS : EGG_HIT_HURT_MS)) {
            defender.hurtUntil = now + (blocking ? EGG_BLOCK_HURT_MS : EGG_HIT_HURT_MS);
        }
        defender.vx = attacker.facing * (blocking ? 125f : ("ultimate".equals(attacker.attack) ? 390f : 280f));
        defender.vy = Math.max(defender.vy, blocking ? 72f : ("ultimate".equals(attacker.attack) ? 210f : 145f));
        defender.hitSparkUntil = now + 180L;
        attacker.energy = clamp(attacker.energy + 8f, 0f, EGG_MAX_ENERGY);
        attacker.combo = attacker.comboUntil > now ? attacker.combo + 1 : 1;
        attacker.comboUntil = now + 1600L;
        attacker.attackHit = true;
    }

    private void finishLocalRound(GameState game, PlayerState winner, boolean timeout) {
        game.roundEndsAt = 0L;
        game.roundMsRemaining = EGG_ROUND_MS;
        game.roundWinner = winner == null ? 0 : winner.slot;
        if (winner == null) {
            game.status = "ended";
            game.message = "平局，重开这一局";
        } else {
            game.aiLevel = winner.slot == 1 ? Math.min(3, game.aiLevel + 1) : Math.max(1, game.aiLevel - 1);
            saveAiLevel(game.aiLevel);
            winner.wins += 1;
            if (winner.wins >= EGG_WIN_ROUNDS) {
                game.status = "match-ended";
                game.matchWinner = winner.slot;
                game.message = winner.name + " 赢下整场";
            } else {
                game.status = "ended";
                game.message = timeout ? winner.name + " 时间判定获胜" : winner.name + " 赢下这一局";
            }
        }
        game.players[0].ready = false;
        game.players[1].ready = true;
        game.players[0].input = new InputState();
        game.players[1].input = new InputState();
    }

    private PlayerState knockoutLocalWinner(GameState game) {
        PlayerState a = game.players[0];
        PlayerState b = game.players[1];
        if (a.hp <= 0f && b.hp <= 0f) return scoreLocalWinner(game);
        return a.hp > b.hp ? a : b;
    }

    private PlayerState scoreLocalWinner(GameState game) {
        PlayerState a = game.players[0];
        PlayerState b = game.players[1];
        int aScore = Math.round(a.hp * 100f + a.guard * 6f + a.energy * 2f);
        int bScore = Math.round(b.hp * 100f + b.guard * 6f + b.energy * 2f);
        if (aScore == bScore) return null;
        return aScore > bScore ? a : b;
    }

    private void faceLocalPlayers(PlayerState a, PlayerState b) {
        if (Math.abs(a.x - b.x) < 8f) return;
        a.facing = a.x < b.x ? 1 : -1;
        b.facing = b.x < a.x ? 1 : -1;
    }

    private void resolveLocalPush(PlayerState a, PlayerState b) {
        float minDistance = 84f;
        float overlap = minDistance - Math.abs(a.x - b.x);
        if (overlap <= 0f) return;
        if (a.y > EGG_AIR_PASS_HEIGHT || b.y > EGG_AIR_PASS_HEIGHT) return;
        float sign = a.x <= b.x ? -1f : 1f;
        a.x += sign * overlap * 0.5f;
        b.x -= sign * overlap * 0.5f;
    }

    private void refreshLocalFlags(GameState game, long now) {
        for (PlayerState player : game.players) {
            player.blocking = now < player.blockUntil;
            player.hurt = now < player.hurtUntil;
            player.hitSpark = now < player.hitSparkUntil;
            player.guardBroken = now < player.guardBreakUntil;
            player.skillActive = now < player.skillUntil;
            player.ultimateActive = now < player.ultimateUntil;
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private class EggGameView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF leftButton = new RectF();
        private final RectF rightButton = new RectF();
        private final RectF jumpButton = new RectF();
        private final RectF lightButton = new RectF();
        private final RectF heavyButton = new RectF();
        private final RectF specialButton = new RectF();
        private final RectF ultimateButton = new RectF();
        private final RectF blockButton = new RectF();
        private final RectF readyButton = new RectF();
        private final RectF reconnectButton = new RectF();
        private final RectF settingsButton = new RectF();
        private final RectF pvpButton = new RectF();
        private final RectF aiButton = new RectF();
        private final RectF[] friendButtons = createFriendButtons();
        private final RectF closeSettingsButton = new RectF();
        private boolean localHurtFeedbackActive = false;
        private long lastHurtHapticAt = 0L;
        private final Runnable ticker = new Runnable() {
            @Override
            public void run() {
                invalidate();
                if (alive.get()) mainHandler.postDelayed(this, 16);
            }
        };

        EggGameView(Context context) {
            super(context);
            setFocusable(true);
            mainHandler.post(ticker);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            GameState snapshot = state;
            drawStage(canvas, w, h);
            layoutButtons(w, h);
            float margin = dp(36);
            float ground = h - dp(72);
            float scale = (w - margin * 2f) / Math.max(1f, snapshot.arenaWidth);
            drawHud(canvas, snapshot, w);
            for (PlayerState player : snapshot.players) {
                if (player.connected) drawFighter(canvas, player, margin + player.x * scale, ground - player.y * scale, scale);
            }
            if (!isFighting(snapshot.status)) drawMenuOverlay(canvas, snapshot, w, h);
            if ("ended".equals(snapshot.status) || "match-ended".equals(snapshot.status)) {
                drawResultBanner(canvas, snapshot, w, h);
            }
            if ("countdown".equals(snapshot.status)) drawCountdownOverlay(canvas, snapshot, w, h);
            drawStatus(canvas, snapshot, w, h);
            drawControls(canvas);
            triggerLocalHurtFeedback(snapshot);
            if (settingsOpen) drawSettingsOverlay(canvas, w, h);
        }

        private boolean isFighting(String status) {
            return "running".equals(status) || "countdown".equals(status);
        }

        private void drawStage(Canvas canvas, int w, int h) {
            canvas.drawColor(Color.rgb(14, 20, 28));
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(18, 26, 35));
            canvas.drawRect(0, 0, w, h * 0.38f, paint);
            int winner = state.matchWinner > 0 ? state.matchWinner : state.roundWinner;
            if (winner > 0) {
                paint.setColor(winner == 1 ? Color.argb(54, 91, 196, 177) : Color.argb(54, 255, 122, 88));
                canvas.drawRect(0, 0, w, h * 0.38f, paint);
            }
            paint.setColor(Color.rgb(24, 34, 45));
            canvas.drawRect(0, h * 0.38f, w, h - dp(84), paint);
            paint.setColor(Color.rgb(30, 42, 54));
            canvas.drawRect(0, h * 0.38f, w, h * 0.405f, paint);

            paint.setColor(Color.argb(46, 255, 244, 210));
            canvas.drawRect(w * 0.18f, h * 0.49f, w * 0.82f, h * 0.505f, paint);
            paint.setColor(Color.argb(42, 120, 196, 178));
            canvas.drawRect(w * 0.24f, h * 0.60f, w * 0.76f, h * 0.615f, paint);

            paint.setColor(Color.rgb(21, 42, 49));
            canvas.drawRect(0, h - dp(82), w, h, paint);
            paint.setColor(Color.rgb(245, 196, 88));
            canvas.drawRect(0, h - dp(84), w, h - dp(76), paint);
            paint.setColor(Color.argb(90, 255, 244, 210));
            canvas.drawRect(w * 0.18f, h - dp(76), w * 0.82f, h - dp(72), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(70, 255, 244, 210));
            canvas.drawOval(new RectF(w * 0.18f, h - dp(128), w * 0.82f, h - dp(38)), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawHud(Canvas canvas, GameState snapshot, int w) {
            PlayerState p1 = snapshot.players[0];
            PlayerState p2 = snapshot.players[1];
            drawHealth(canvas, dp(24), dp(18), w * 0.36f, p1, false);
            drawHealth(canvas, w - dp(24) - w * 0.36f, dp(18), w * 0.36f, p2, true);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.argb(205, 10, 14, 20));
            RectF scoreBox = new RectF(w / 2f - dp(70), dp(18), w / 2f + dp(70), dp(62));
            canvas.drawRoundRect(scoreBox, dp(8), dp(8), paint);
            paint.setTextSize(sp(18));
            int seconds = snapshot.roundSeconds();
            boolean urgent = "running".equals(snapshot.status) && seconds <= 10;
            paint.setColor(urgent ? Color.rgb(255, 122, 88) : Color.rgb(245, 196, 88));
            canvas.drawText(String.valueOf(seconds), w / 2f, dp(40), paint);
            paint.setTextSize(sp(11));
            paint.setColor(Color.rgb(255, 244, 210));
            String matchPoint = matchPointLabel(snapshot);
            canvas.drawText(matchPoint.isEmpty() ? p1.wins + " : " + p2.wins + " / " + snapshot.winRounds : matchPoint, w / 2f, dp(57), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawHealth(Canvas canvas, float x, float y, float width, PlayerState player, boolean mirror) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(210, 10, 14, 20));
            canvas.drawRoundRect(new RectF(x, y, x + width, y + dp(20)), dp(6), dp(6), paint);
            float hpWidth = width * Math.max(0f, Math.min(1f, player.hp / Math.max(1f, player.maxHp)));
            paint.setColor(player.slot == 1 ? Color.rgb(91, 196, 177) : Color.rgb(255, 122, 88));
            if (mirror) {
                canvas.drawRoundRect(new RectF(x + width - hpWidth, y, x + width, y + dp(20)), dp(6), dp(6), paint);
            } else {
                canvas.drawRoundRect(new RectF(x, y, x + hpWidth, y + dp(20)), dp(6), dp(6), paint);
            }
            paint.setColor(Color.WHITE);
            paint.setTextSize(sp(12));
            paint.setTextAlign(mirror ? Paint.Align.RIGHT : Paint.Align.LEFT);
            canvas.drawText((player.connected ? player.name : "等待加入") + "  " + Math.round(player.hp), mirror ? x + width : x, y + dp(36), paint);
            drawMeter(canvas, x, y + dp(44), width, player.energy / 100f, Color.rgb(245, 196, 88), mirror);
            drawMeter(canvas, x, y + dp(55), width, player.guard / Math.max(1f, player.maxGuard), Color.rgb(120, 154, 231), mirror);
        }

        private void drawMeter(Canvas canvas, float x, float y, float width, float ratio, int color, boolean mirror) {
            float height = dp(6);
            ratio = Math.max(0f, Math.min(1f, ratio));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(165, 10, 14, 20));
            canvas.drawRoundRect(new RectF(x, y, x + width, y + height), dp(3), dp(3), paint);
            paint.setColor(color);
            float fill = width * ratio;
            if (mirror) {
                canvas.drawRoundRect(new RectF(x + width - fill, y, x + width, y + height), dp(3), dp(3), paint);
            } else {
                canvas.drawRoundRect(new RectF(x, y, x + fill, y + height), dp(3), dp(3), paint);
            }
        }

        private void drawFighter(Canvas canvas, PlayerState player, float x, float ground, float scale) {
            int style = fighterStyle(player);
            float bodyW = style == 1 ? dp(76) : (style == 2 ? dp(48) : (style == 3 ? dp(70) : (style == 4 ? dp(70) : dp(60))));
            float bodyH = style == 1 ? dp(74) : (style == 2 ? dp(88) : (style == 3 ? dp(78) : (style == 4 ? dp(80) : dp(84))));
            int body = style == 1 ? Color.rgb(255, 217, 205)
                    : (style == 2 ? Color.rgb(245, 255, 226) : (style == 4 ? Color.rgb(238, 224, 255) : Color.rgb(255, 247, 223)));
            int band = style == 1 ? Color.rgb(226, 62, 58)
                    : (style == 2 ? Color.rgb(156, 190, 112) : (style == 3 ? Color.rgb(207, 96, 76) : (style == 4 ? Color.rgb(150, 118, 204) : Color.rgb(91, 196, 177))));
            int hat = style == 1 ? Color.rgb(97, 35, 35)
                    : (style == 2 ? Color.rgb(67, 88, 96) : (style == 4 ? Color.rgb(58, 47, 82) : Color.rgb(46, 63, 77)));
            int foot = style == 1 ? Color.rgb(255, 198, 92)
                    : (style == 2 ? Color.rgb(120, 154, 231) : (style == 4 ? Color.rgb(96, 80, 135) : band));
            if (player.hurt) body = Color.rgb(255, 204, 193);
            paint.setStyle(Paint.Style.FILL);
            if (player.hitSpark) {
                paint.setColor(Color.argb(140, 245, 196, 88));
                canvas.drawCircle(x, ground - dp(46), dp(50), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setColor(Color.argb(190, 255, 244, 210));
                canvas.drawLine(x - dp(38), ground - dp(46), x + dp(38), ground - dp(46), paint);
                canvas.drawLine(x, ground - dp(84), x, ground - dp(8), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(Color.argb(95, 0, 0, 0));
            canvas.drawOval(new RectF(x - dp(34), ground - dp(10), x + dp(34), ground + dp(6)), paint);
            float dir = player.facing >= 0 ? 1f : -1f;
            if (style == 4) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(dp(5));
                paint.setColor(Color.rgb(96, 80, 135));
                android.graphics.Path tail = new android.graphics.Path();
                tail.moveTo(x - dir * dp(28), ground - dp(36));
                tail.cubicTo(x - dir * dp(56), ground - dp(44), x - dir * dp(52), ground - dp(70), x - dir * dp(34), ground - dp(62));
                canvas.drawPath(tail, paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(body);
            RectF oval = new RectF(x - bodyW / 2f, ground - bodyH, x + bodyW / 2f, ground);
            canvas.drawOval(oval, paint);
            if (style == 4) {
                paint.setColor(hat);
                android.graphics.Path leftEar = new android.graphics.Path();
                leftEar.moveTo(x - dp(26), ground - dp(72));
                leftEar.lineTo(x - dp(12), ground - dp(96));
                leftEar.lineTo(x - dp(2), ground - dp(72));
                leftEar.close();
                canvas.drawPath(leftEar, paint);
                android.graphics.Path rightEar = new android.graphics.Path();
                rightEar.moveTo(x + dp(2), ground - dp(72));
                rightEar.lineTo(x + dp(12), ground - dp(96));
                rightEar.lineTo(x + dp(26), ground - dp(72));
                rightEar.close();
                canvas.drawPath(rightEar, paint);
                paint.setColor(Color.rgb(255, 205, 218));
                canvas.drawCircle(x - dp(12), ground - dp(80), dp(4), paint);
                canvas.drawCircle(x + dp(12), ground - dp(80), dp(4), paint);
            }
            paint.setColor(Color.argb(88, 255, 255, 255));
            canvas.drawOval(new RectF(x - dp(18), ground - dp(74), x + dp(10), ground - dp(44)), paint);
            paint.setColor(band);
            canvas.drawRoundRect(new RectF(x - dp(28), ground - dp(43), x + dp(28), ground - dp(32)), dp(5), dp(5), paint);
            paint.setColor(hat);
            if (style == 1 || style == 3) {
                canvas.drawRoundRect(new RectF(x - dp(28), ground - dp(72), x + dp(28), ground - dp(61)), dp(8), dp(8), paint);
                paint.setColor(style == 3 ? Color.rgb(255, 244, 210) : Color.rgb(255, 198, 92));
                canvas.drawCircle(x + dp(25), ground - dp(66), dp(5), paint);
            } else if (style == 2) {
                canvas.drawRoundRect(new RectF(x - dp(18), ground - dp(80), x + dp(18), ground - dp(66)), dp(9), dp(9), paint);
                paint.setColor(Color.rgb(245, 196, 88));
                canvas.drawCircle(x - dp(22), ground - dp(66), dp(4), paint);
                paint.setColor(Color.rgb(156, 190, 112));
                canvas.drawRect(x - dp(4), ground - dp(92), x + dp(4), ground - dp(80), paint);
                canvas.drawOval(new RectF(x + dp(2), ground - dp(100), x + dp(18), ground - dp(86)), paint);
            } else if (style == 4) {
                canvas.drawRoundRect(new RectF(x - dp(31), ground - dp(70), x + dp(31), ground - dp(58)), dp(7), dp(7), paint);
                paint.setColor(Color.rgb(245, 196, 88));
                canvas.drawRect(x - dp(12), ground - dp(76), x + dp(12), ground - dp(70), paint);
            } else {
                canvas.drawRoundRect(new RectF(x - dp(22), ground - dp(78), x + dp(22), ground - dp(66)), dp(8), dp(8), paint);
            }
            paint.setColor(Color.rgb(31, 41, 51));
            float eyeShift = player.facing >= 0 ? dp(2) : -dp(2);
            canvas.drawCircle(x - dp(12) + eyeShift, ground - dp(52), dp(4), paint);
            canvas.drawCircle(x + dp(12) + eyeShift, ground - dp(52), dp(4), paint);
            if (style == 1) {
                paint.setStrokeWidth(dp(2.3f));
                paint.setColor(Color.rgb(97, 35, 35));
                canvas.drawLine(x - dp(18) + eyeShift, ground - dp(61), x - dp(6) + eyeShift, ground - dp(58), paint);
                canvas.drawLine(x + dp(6) + eyeShift, ground - dp(58), x + dp(18) + eyeShift, ground - dp(61), paint);
            }
            if (style == 4) {
                paint.setStrokeWidth(dp(1.6f));
                paint.setColor(Color.rgb(58, 47, 82));
                canvas.drawLine(x - dp(7), ground - dp(45), x - dp(31), ground - dp(50), paint);
                canvas.drawLine(x - dp(7), ground - dp(41), x - dp(31), ground - dp(39), paint);
                canvas.drawLine(x + dp(7), ground - dp(45), x + dp(31), ground - dp(50), paint);
                canvas.drawLine(x + dp(7), ground - dp(41), x + dp(31), ground - dp(39), paint);
            }
            paint.setColor(Color.rgb(255, 162, 132));
            canvas.drawCircle(x - dp(21), ground - dp(43), dp(5), paint);
            canvas.drawCircle(x + dp(21), ground - dp(43), dp(5), paint);
            paint.setStrokeWidth(dp(4));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            float armY = ground - dp(38);
            paint.setColor(player.blocking ? Color.rgb(242, 197, 104) : band);
            boolean winnerPose = state.roundWinner == player.slot || state.matchWinner == player.slot;
            if (winnerPose) {
                canvas.drawLine(x + dir * dp(20), armY, x + dir * dp(34), armY - dp(34), paint);
                canvas.drawLine(x - dir * dp(20), armY, x - dir * dp(34), armY - dp(34), paint);
            } else {
                canvas.drawLine(x + dir * dp(23), armY, x + dir * dp(52), armY - dp(10), paint);
                canvas.drawLine(x - dir * dp(20), armY + dp(4), x - dir * dp(40), armY + dp(18), paint);
            }
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
            if (style == 1 && !winnerPose) {
                paint.setColor(Color.rgb(226, 62, 58));
                canvas.drawCircle(x + dir * dp(53), armY - dp(10), dp(7), paint);
                canvas.drawCircle(x - dir * dp(40), armY + dp(18), dp(6), paint);
            }
            if (style == 2) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(Color.argb(145, 156, 190, 112));
                canvas.drawLine(x - dir * dp(44), ground - dp(56), x - dir * dp(64), ground - dp(56), paint);
                canvas.drawLine(x - dir * dp(40), ground - dp(42), x - dir * dp(58), ground - dp(42), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(foot);
            canvas.drawOval(new RectF(x - dp(26), ground - dp(5), x - dp(6), ground + dp(6)), paint);
            canvas.drawOval(new RectF(x + dp(6), ground - dp(5), x + dp(26), ground + dp(6)), paint);
            if (!player.attack.isEmpty()) {
                boolean special = player.attack.equals("special");
                paint.setColor(special ? Color.argb(205, 245, 196, 88)
                        : colorWithAlpha(band, 185));
                float reach = special ? dp(118) : (player.attack.equals("heavy") ? dp(84) : dp(62));
                canvas.drawOval(new RectF(x + dir * dp(26), ground - dp(70), x + dir * reach, ground - dp(18)), paint);
                if (special) {
                    paint.setColor(Color.argb(150, 255, 255, 255));
                    canvas.drawCircle(x + dir * dp(82), ground - dp(44), dp(11), paint);
                }
            }
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(12));
            paint.setColor(Color.WHITE);
            canvas.drawText(player.name, x, ground + dp(22), paint);
            if (player.combo > 1) {
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(sp(14));
                paint.setColor(Color.rgb(245, 196, 88));
                canvas.drawText(player.combo + " HIT", x, ground - dp(96), paint);
                paint.setTypeface(Typeface.DEFAULT);
            }
            if (player.guardBroken) {
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(sp(15));
                paint.setColor(Color.rgb(255, 122, 88));
                canvas.drawText("破防", x, ground - dp(116), paint);
                paint.setTypeface(Typeface.DEFAULT);
            }
            if (state.matchWinner == player.slot) {
                drawCrown(canvas, x, ground - dp(100));
            }
        }

        private void drawStatus(Canvas canvas, GameState snapshot, int w, int h) {
            if ("running".equals(snapshot.status) || "waiting".equals(snapshot.status)
                    || "ended".equals(snapshot.status) || "match-ended".equals(snapshot.status)) return;
            String text = connectionText;
            if (System.currentTimeMillis() - lastStateAt < 1400) {
                if ("waiting".equals(snapshot.status)) text = snapshot.message;
                if ("countdown".equals(snapshot.status)) text = "准备 " + Math.max(1, (snapshot.countdownMs + 999) / 1000);
                if ("running".equals(snapshot.status)) text = snapshot.playerSlot > 0 ? "你是 P" + snapshot.playerSlot : "观战中";
                if ("ended".equals(snapshot.status)) text = snapshot.message + "，点开始再来";
                if ("match-ended".equals(snapshot.status)) text = snapshot.message + "，点开始开新赛";
            }
            paint.setColor(Color.argb(205, 11, 16, 23));
            RectF panel = new RectF(w / 2f - dp(190), h - dp(62), w / 2f + dp(190), h - dp(20));
            canvas.drawRoundRect(panel, dp(10), dp(10), paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(sp(15));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, w / 2f, h - dp(33), paint);
        }

        private void layoutButtons(int w, int h) {
            float bottom = h - dp(16);
            boolean fighting = isFighting(state.status);
            leftButton.set(dp(30), bottom - dp(58), dp(88), bottom);
            rightButton.set(dp(102), bottom - dp(58), dp(160), bottom);
            jumpButton.set(w - dp(390), bottom - dp(54), w - dp(338), bottom);
            blockButton.set(w - dp(332), bottom - dp(54), w - dp(280), bottom);
            specialButton.set(w - dp(274), bottom - dp(54), w - dp(222), bottom);
            ultimateButton.set(w - dp(216), bottom - dp(54), w - dp(164), bottom);
            lightButton.set(w - dp(158), bottom - dp(54), w - dp(106), bottom);
            heavyButton.set(w - dp(100), bottom - dp(54), w - dp(48), bottom);
            if (fighting) {
                readyButton.setEmpty();
                reconnectButton.setEmpty();
                pvpButton.setEmpty();
                aiButton.setEmpty();
                for (RectF rect : friendButtons) rect.setEmpty();
            } else {
                float friendTop = h / 2f + dp(2);
                float friendW = dp(88);
                float gap = dp(7);
                float totalFriendW = friendButtons.length * friendW + (friendButtons.length - 1) * gap;
                float friendLeft = w / 2f - totalFriendW / 2f;
                for (int i = 0; i < friendButtons.length; i++) {
                    friendButtons[i].set(friendLeft + i * (friendW + gap), friendTop, friendLeft + i * (friendW + gap) + friendW, friendTop + dp(48));
                }
                pvpButton.set(w / 2f - dp(168), h / 2f + dp(60), w / 2f - dp(5), h / 2f + dp(100));
                aiButton.set(w / 2f + dp(5), h / 2f + dp(60), w / 2f + dp(168), h / 2f + dp(100));
                readyButton.set(w / 2f - dp(164), h / 2f + dp(114), w / 2f + dp(164), h / 2f + dp(160));
                reconnectButton.setEmpty();
            }
            settingsButton.set(w - dp(54), dp(72), w - dp(18), dp(108));
            closeSettingsButton.set(w / 2f - dp(58), h / 2f + dp(116), w / 2f + dp(58), h / 2f + dp(154));
        }

        private void drawControls(Canvas canvas) {
            InputState snapshot = input;
            boolean fighting = isFighting(state.status);
            if (fighting) {
                drawButton(canvas, leftButton, "←", snapshot.left);
                drawButton(canvas, rightButton, "→", snapshot.right);
                drawButton(canvas, jumpButton, "跳", snapshot.jump);
                drawButton(canvas, blockButton, "防", snapshot.block);
                drawButton(canvas, lightButton, "轻", snapshot.light);
                drawButton(canvas, heavyButton, "重", snapshot.heavy);
                drawSpecialButton(canvas, specialButton, "技", snapshot.special, localPlayer().energy >= EGG_SKILL_COST);
                drawSpecialButton(canvas, ultimateButton, "奥", snapshot.ultimate, localPlayer().energy >= EGG_ULTIMATE_COST);
            }
            boolean ended = "ended".equals(state.status) || "match-ended".equals(state.status);
            if (!fighting) {
                for (int i = 0; i < friendButtons.length; i++) {
                    drawFriendButton(canvas, friendButtons[i], FRIEND_NAMES[i], selectedFriend == i);
                }
                drawModeButton(canvas, pvpButton, "二人对战", gameMode == MODE_PVP);
                drawModeButton(canvas, aiButton, "人机练习", gameMode == MODE_AI);
                drawSmallButton(canvas, readyButton, ended ? "再来" : "开始", snapshot.ready || snapshot.restart);
            }
            drawSettingsIcon(canvas, settingsButton);
        }

        private void drawMenuOverlay(Canvas canvas, GameState snapshot, int w, int h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(172, 10, 14, 20));
            canvas.drawRect(0, 0, w, h, paint);

            RectF panel = new RectF(w / 2f - dp(210), h / 2f - dp(112), w / 2f + dp(210), h / 2f + dp(166));
            paint.setColor(Color.argb(238, 18, 25, 34));
            canvas.drawRoundRect(panel, dp(10), dp(10), paint);
            paint.setColor(Color.rgb(245, 196, 88));
            canvas.drawRoundRect(new RectF(panel.left, panel.top, panel.right, panel.top + dp(5)), dp(4), dp(4), paint);

            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(25));
            paint.setColor(Color.rgb(255, 244, 210));
            canvas.drawText("Egg和他的朋友们", w / 2f, panel.top + dp(46), paint);

            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(sp(14));
            paint.setColor(Color.rgb(202, 214, 218));
            String status = connectionText;
            if (System.currentTimeMillis() - lastStateAt < 1600) status = snapshot.message;
            canvas.drawText(status, w / 2f, panel.top + dp(78), paint);
            if (gameMode == MODE_AI) {
                paint.setTextSize(sp(12));
                paint.setColor(Color.rgb(120, 196, 178));
                canvas.drawText(aiStyleLabel(snapshot.aiLevel), w / 2f, panel.top + dp(102), paint);
            }
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawResultBanner(Canvas canvas, GameState snapshot, int w, int h) {
            RectF banner = new RectF(w / 2f - dp(196), dp(76), w / 2f + dp(196), dp(146));
            int winner = snapshot.matchWinner > 0 ? snapshot.matchWinner : snapshot.roundWinner;
            String title = winner == 0 ? "平局" : (snapshot.matchWinner > 0 ? "整场胜利" : "本局胜利");
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(225, 10, 14, 20));
            canvas.drawRoundRect(banner, dp(8), dp(8), paint);
            paint.setColor(winner == 2 ? Color.rgb(255, 122, 88) : (snapshot.matchWinner > 0 ? Color.rgb(245, 196, 88) : Color.rgb(120, 196, 178)));
            canvas.drawRoundRect(new RectF(banner.left, banner.top, banner.right, banner.top + dp(4)), dp(4), dp(4), paint);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(14));
            paint.setColor(winner == 0 ? Color.rgb(202, 214, 218) : Color.rgb(245, 196, 88));
            canvas.drawText(title, w / 2f, banner.top + dp(27), paint);
            paint.setTextSize(sp(16));
            paint.setColor(Color.rgb(255, 244, 210));
            canvas.drawText(snapshot.message, w / 2f, banner.top + dp(52), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawCountdownOverlay(Canvas canvas, GameState snapshot, int w, int h) {
            int seconds = Math.max(1, (int) ((snapshot.countdownMs + 999L) / 1000L));
            RectF badge = new RectF(w / 2f - dp(58), h * 0.38f - dp(50), w / 2f + dp(58), h * 0.38f + dp(50));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(210, 10, 14, 20));
            canvas.drawRoundRect(badge, dp(10), dp(10), paint);
            paint.setColor(Color.rgb(245, 196, 88));
            canvas.drawRoundRect(new RectF(badge.left, badge.top, badge.right, badge.top + dp(4)), dp(4), dp(4), paint);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(13));
            paint.setColor(Color.rgb(255, 244, 210));
            canvas.drawText("准备", w / 2f, badge.top + dp(25), paint);
            paint.setTextSize(sp(42));
            paint.setColor(Color.rgb(245, 196, 88));
            canvas.drawText(String.valueOf(seconds), w / 2f, badge.top + dp(74), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawModeButton(Canvas canvas, RectF rect, String label, boolean selected) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? Color.rgb(245, 196, 88) : Color.argb(218, 13, 19, 28));
            canvas.drawRoundRect(rect, dp(6), dp(6), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(selected ? Color.argb(210, 255, 244, 210) : Color.argb(110, 255, 255, 255));
            canvas.drawRoundRect(rect, dp(6), dp(6), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? Color.rgb(30, 38, 45) : Color.WHITE);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(13));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, rect.centerX(), rect.centerY() + dp(5), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawFriendButton(Canvas canvas, RectF rect, String label, boolean selected) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? Color.rgb(120, 196, 178) : Color.argb(210, 13, 19, 28));
            canvas.drawRoundRect(rect, dp(6), dp(6), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(selected ? Color.argb(220, 255, 244, 210) : Color.argb(100, 255, 255, 255));
            canvas.drawRoundRect(rect, dp(6), dp(6), paint);
            drawFriendAvatar(canvas, rect.left + dp(18), rect.centerY() + dp(10), fighterStyleForName(label), 0.34f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? Color.rgb(12, 19, 26) : Color.WHITE);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(11));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(label, rect.left + dp(38), rect.centerY() - dp(2), paint);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(sp(8.5f));
            paint.setColor(selected ? Color.rgb(48, 54, 58) : Color.rgb(202, 214, 218));
            canvas.drawText(fighterStatsForName(label).archetype, rect.left + dp(38), rect.centerY() + dp(13), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private RectF[] createFriendButtons() {
            RectF[] buttons = new RectF[FRIEND_NAMES.length];
            for (int i = 0; i < buttons.length; i++) buttons[i] = new RectF();
            return buttons;
        }

        private void drawSettingsIcon(Canvas canvas, RectF rect) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(rect.centerX(), rect.centerY(), dp(8), paint);
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2d * i / 8d;
                float x1 = rect.centerX() + (float) Math.cos(angle) * dp(11);
                float y1 = rect.centerY() + (float) Math.sin(angle) * dp(11);
                float x2 = rect.centerX() + (float) Math.cos(angle) * dp(15);
                float y2 = rect.centerY() + (float) Math.sin(angle) * dp(15);
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawSettingsOverlay(Canvas canvas, int w, int h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(210, 15, 20, 27));
            canvas.drawRect(0, 0, w, h, paint);
            RectF panel = new RectF(w / 2f - dp(230), h / 2f - dp(130), w / 2f + dp(230), h / 2f + dp(170));
            paint.setColor(PAPER);
            canvas.drawRoundRect(panel, dp(8), dp(8), paint);
            paint.setColor(LINE);
            canvas.drawRect(panel.left, panel.top + dp(58), panel.right, panel.top + dp(59), paint);
            paint.setColor(INK);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(22));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("设置", panel.left + dp(24), panel.top + dp(38), paint);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(sp(15));
            paint.setColor(MUTED);
            canvas.drawText("当前：" + (gameMode == MODE_AI ? "人机练习" : "二人局域网对战"), panel.left + dp(24), panel.top + dp(86), paint);
            String service = gameMode == MODE_AI ? "本机运行" : (serviceUrl.isEmpty() ? "未连接" : serviceUrl);
            canvas.drawText("服务：" + service, panel.left + dp(24), panel.top + dp(116), paint);
            paint.setColor(ACCENT);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("关于", panel.left + dp(24), panel.top + dp(164), paint);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(INK);
            canvas.drawText("横屏实时格斗，支持二人对战和人机练习。", panel.left + dp(24), panel.top + dp(196), paint);
            paint.setColor(CARD);
            canvas.drawRoundRect(closeSettingsButton, dp(7), dp(7), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(LINE);
            canvas.drawRoundRect(closeSettingsButton, dp(7), dp(7), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(INK);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(14));
            canvas.drawText("返回", closeSettingsButton.centerX(), closeSettingsButton.centerY() + dp(5), paint);
        }

        private void drawButton(Canvas canvas, RectF rect, String label, boolean pressed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? Color.rgb(242, 197, 104) : Color.argb(108, 255, 255, 255));
            canvas.drawOval(rect, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.4f));
            paint.setColor(Color.argb(135, 255, 255, 255));
            canvas.drawOval(rect, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(24, 31, 39));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(21));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, rect.centerX(), rect.centerY() + dp(9), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawSpecialButton(Canvas canvas, RectF rect, String label, boolean pressed, boolean ready) {
            paint.setStyle(Paint.Style.FILL);
            if (pressed) {
                paint.setColor(Color.rgb(242, 197, 104));
            } else if (ready) {
                paint.setColor(Color.argb(190, 245, 196, 88));
            } else {
                paint.setColor(Color.argb(82, 255, 255, 255));
            }
            canvas.drawOval(rect, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.4f));
            paint.setColor(ready ? Color.argb(190, 255, 244, 210) : Color.argb(110, 255, 255, 255));
            canvas.drawOval(rect, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ready ? Color.rgb(24, 31, 39) : Color.argb(170, 24, 31, 39));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(21));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, rect.centerX(), rect.centerY() + dp(9), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private PlayerState localPlayer() {
            int index = Math.max(0, Math.min(1, state.playerSlot - 1));
            return state.players[index];
        }

        private String aiStyleLabel(int level) {
            if (level >= 3) return "蛋卷教练 Lv.3 压迫";
            if (level == 2) return "蛋卷教练 Lv.2 稳健";
            return "蛋卷教练 Lv.1 入门";
        }

        private String matchPointLabel(GameState snapshot) {
            if ("match-ended".equals(snapshot.status)) return "";
            PlayerState p1 = snapshot.players[0];
            PlayerState p2 = snapshot.players[1];
            int target = Math.max(1, snapshot.winRounds - 1);
            if (p1.wins >= target && p2.wins >= target) return "双赛点";
            if (p1.wins >= target) return p1.name + " 赛点";
            if (p2.wins >= target) return p2.name + " 赛点";
            return "";
        }

        private void drawCrown(Canvas canvas, float x, float y) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(245, 196, 88));
            float[] points = new float[]{
                    x - dp(24), y + dp(14),
                    x - dp(17), y - dp(7),
                    x - dp(6), y + dp(9),
                    x, y - dp(12),
                    x + dp(6), y + dp(9),
                    x + dp(17), y - dp(7),
                    x + dp(24), y + dp(14),
                    x + dp(24), y + dp(21),
                    x - dp(24), y + dp(21),
            };
            android.graphics.Path crown = new android.graphics.Path();
            crown.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) crown.lineTo(points[i], points[i + 1]);
            crown.close();
            canvas.drawPath(crown, paint);
            paint.setColor(Color.rgb(255, 244, 210));
            canvas.drawCircle(x, y - dp(12), dp(3), paint);
            canvas.drawCircle(x - dp(17), y - dp(7), dp(2.5f), paint);
            canvas.drawCircle(x + dp(17), y - dp(7), dp(2.5f), paint);
        }

        private int fighterStyle(PlayerState player) {
            return fighterStyleForName(player.name);
        }

        private int fighterStyleForName(String name) {
            if ("小红".equals(name)) return 1;
            if ("小米".equals(name)) return 2;
            if ("蛋卷教练".equals(name)) return 3;
            if ("Meo".equals(name)) return 4;
            return 0;
        }

        private int colorWithAlpha(int color, int alpha) {
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }

        private void drawFriendAvatar(Canvas canvas, float x, float ground, int style, float scale) {
            float bodyW = dp((style == 1 ? 64 : (style == 2 ? 44 : (style == 4 ? 62 : 54))) * scale);
            float bodyH = dp((style == 1 ? 62 : (style == 2 ? 72 : (style == 4 ? 66 : 70))) * scale);
            int body = style == 1 ? Color.rgb(255, 217, 205)
                    : (style == 2 ? Color.rgb(245, 255, 226) : (style == 4 ? Color.rgb(238, 224, 255) : Color.rgb(255, 247, 223)));
            int band = style == 1 ? Color.rgb(226, 62, 58)
                    : (style == 2 ? Color.rgb(156, 190, 112) : (style == 4 ? Color.rgb(150, 118, 204) : Color.rgb(91, 196, 177)));
            int hat = style == 1 ? Color.rgb(97, 35, 35)
                    : (style == 2 ? Color.rgb(67, 88, 96) : (style == 4 ? Color.rgb(58, 47, 82) : Color.rgb(46, 63, 77)));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(90, 0, 0, 0));
            canvas.drawOval(new RectF(x - dp(15 * scale), ground - dp(3 * scale), x + dp(15 * scale), ground + dp(3 * scale)), paint);
            paint.setColor(body);
            canvas.drawOval(new RectF(x - bodyW / 2f, ground - bodyH, x + bodyW / 2f, ground), paint);
            if (style == 4) {
                paint.setColor(hat);
                android.graphics.Path leftEar = new android.graphics.Path();
                leftEar.moveTo(x - dp(14 * scale), ground - dp(54 * scale));
                leftEar.lineTo(x - dp(6 * scale), ground - dp(70 * scale));
                leftEar.lineTo(x + dp(1 * scale), ground - dp(54 * scale));
                leftEar.close();
                canvas.drawPath(leftEar, paint);
                android.graphics.Path rightEar = new android.graphics.Path();
                rightEar.moveTo(x - dp(1 * scale), ground - dp(54 * scale));
                rightEar.lineTo(x + dp(6 * scale), ground - dp(70 * scale));
                rightEar.lineTo(x + dp(14 * scale), ground - dp(54 * scale));
                rightEar.close();
                canvas.drawPath(rightEar, paint);
            }
            paint.setColor(band);
            canvas.drawRoundRect(new RectF(x - dp(13 * scale), ground - dp(22 * scale), x + dp(13 * scale), ground - dp(16 * scale)), dp(3 * scale), dp(3 * scale), paint);
            paint.setColor(hat);
            if (style == 1) {
                canvas.drawRoundRect(new RectF(x - dp(13 * scale), ground - dp(35 * scale), x + dp(13 * scale), ground - dp(29 * scale)), dp(4 * scale), dp(4 * scale), paint);
                paint.setColor(Color.rgb(255, 198, 92));
                canvas.drawCircle(x + dp(12 * scale), ground - dp(32 * scale), dp(2.6f * scale), paint);
            } else if (style == 2) {
                canvas.drawRoundRect(new RectF(x - dp(10 * scale), ground - dp(39 * scale), x + dp(10 * scale), ground - dp(32 * scale)), dp(4 * scale), dp(4 * scale), paint);
                paint.setColor(Color.rgb(156, 190, 112));
                canvas.drawRect(x - dp(2.5f * scale), ground - dp(48 * scale), x + dp(2.5f * scale), ground - dp(39 * scale), paint);
                canvas.drawOval(new RectF(x + dp(1 * scale), ground - dp(54 * scale), x + dp(11 * scale), ground - dp(44 * scale)), paint);
            } else if (style == 4) {
                canvas.drawRoundRect(new RectF(x - dp(14 * scale), ground - dp(35 * scale), x + dp(14 * scale), ground - dp(29 * scale)), dp(3 * scale), dp(3 * scale), paint);
                paint.setColor(Color.rgb(245, 196, 88));
                canvas.drawRect(x - dp(6 * scale), ground - dp(39 * scale), x + dp(6 * scale), ground - dp(35 * scale), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.2f * scale));
                paint.setColor(Color.rgb(58, 47, 82));
                canvas.drawLine(x - dp(4 * scale), ground - dp(24 * scale), x - dp(18 * scale), ground - dp(27 * scale), paint);
                canvas.drawLine(x + dp(4 * scale), ground - dp(24 * scale), x + dp(18 * scale), ground - dp(27 * scale), paint);
                paint.setStyle(Paint.Style.FILL);
            } else {
                canvas.drawRoundRect(new RectF(x - dp(11 * scale), ground - dp(38 * scale), x + dp(11 * scale), ground - dp(32 * scale)), dp(4 * scale), dp(4 * scale), paint);
            }
            if (style == 1) {
                paint.setColor(Color.rgb(226, 62, 58));
                canvas.drawCircle(x - dp(18 * scale), ground - dp(20 * scale), dp(4 * scale), paint);
                canvas.drawCircle(x + dp(18 * scale), ground - dp(20 * scale), dp(4 * scale), paint);
            }
        }

        private void drawSmallButton(Canvas canvas, RectF rect, String label, boolean pressed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? Color.rgb(91, 183, 165) : Color.rgb(91, 196, 177));
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.2f));
            paint.setColor(Color.argb(180, 255, 244, 210));
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(12, 19, 26));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(16));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, rect.centerX(), rect.centerY() + dp(5), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int pointerIndex = event.getActionIndex();
            if (settingsOpen) {
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                    if (closeSettingsButton.contains(event.getX(pointerIndex), event.getY(pointerIndex))) hideSettingsPage();
                }
                return true;
            }
            InputState next = new InputState();
            for (int i = 0; i < event.getPointerCount(); i++) {
                if ((action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) && i == pointerIndex) continue;
                float x = event.getX(i);
                float y = event.getY(i);
                if (leftButton.contains(x, y)) next.left = true;
                if (rightButton.contains(x, y)) next.right = true;
                if (jumpButton.contains(x, y)) next.jump = true;
                if (blockButton.contains(x, y)) next.block = true;
                if (lightButton.contains(x, y)) next.light = true;
                if (heavyButton.contains(x, y)) next.heavy = true;
                if (specialButton.contains(x, y)) next.special = true;
                if (ultimateButton.contains(x, y)) next.ultimate = true;
                if (readyButton.contains(x, y)) {
                    if ("ended".equals(state.status) || "match-ended".equals(state.status)) next.restart = true;
                    next.ready = true;
                }
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                    bufferActionTouch(event.getX(pointerIndex), event.getY(pointerIndex));
                    boolean fighting = isFighting(state.status);
                    if (!fighting) {
                        for (int friendIndex = 0; friendIndex < friendButtons.length; friendIndex++) {
                            if (friendButtons[friendIndex].contains(event.getX(pointerIndex), event.getY(pointerIndex))) {
                                selectFriend(friendIndex);
                            }
                        }
                    }
                    if (!fighting && pvpButton.contains(event.getX(pointerIndex), event.getY(pointerIndex))) selectMode(MODE_PVP);
                    if (!fighting && aiButton.contains(event.getX(pointerIndex), event.getY(pointerIndex))) selectMode(MODE_AI);
                    if (settingsButton.contains(event.getX(pointerIndex), event.getY(pointerIndex))) showSettingsPage();
                }
            }
            if (action == MotionEvent.ACTION_CANCEL) next = new InputState();
            input = next;
            invalidate();
            return true;
        }

        private void bufferActionTouch(float x, float y) {
            long until = System.currentTimeMillis() + INPUT_BUFFER_MS;
            boolean action = false;
            if (jumpButton.contains(x, y)) {
                jumpBufferedUntil = until;
                action = true;
            }
            if (lightButton.contains(x, y)) {
                lightBufferedUntil = until;
                action = true;
            }
            if (heavyButton.contains(x, y)) {
                heavyBufferedUntil = until;
                action = true;
            }
            if (specialButton.contains(x, y)) {
                specialBufferedUntil = until;
                action = true;
            }
            if (ultimateButton.contains(x, y)) {
                ultimateBufferedUntil = until;
                action = true;
            }
            if (readyButton.contains(x, y)) {
                readyBufferedUntil = until;
                if ("ended".equals(state.status) || "match-ended".equals(state.status)) restartBufferedUntil = until;
                action = true;
            }
            if (action) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }

        private void triggerLocalHurtFeedback(GameState snapshot) {
            if (snapshot.playerSlot <= 0) return;
            int index = Math.max(0, Math.min(1, snapshot.playerSlot - 1));
            PlayerState player = snapshot.players[index];
            boolean hurtNow = player.hurt || player.hitSpark;
            long now = System.currentTimeMillis();
            if (hurtNow && !localHurtFeedbackActive && now - lastHurtHapticAt > 180L) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                lastHurtHapticAt = now;
            }
            localHurtFeedbackActive = hurtNow;
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        private float sp(float value) {
            return value * getResources().getDisplayMetrics().scaledDensity;
        }
    }

    private static class InputState {
        boolean left;
        boolean right;
        boolean jump;
        boolean light;
        boolean heavy;
        boolean special;
        boolean ultimate;
        boolean block;
        boolean ready;
        boolean restart;

        InputState copy() {
            InputState copy = new InputState();
            copy.left = left;
            copy.right = right;
            copy.jump = jump;
            copy.light = light;
            copy.heavy = heavy;
            copy.special = special;
            copy.ultimate = ultimate;
            copy.block = block;
            copy.ready = ready;
            copy.restart = restart;
            return copy;
        }
    }

    private static class GameState {
        boolean ok;
        String roomId = "default";
        int playerSlot;
        String status = "offline";
        String message = "";
        String mode = "ai";
        int matchWinner;
        int roundWinner;
        int winRounds = 2;
        int aiLevel = 1;
        long roundMs = 60000L;
        long roundMsRemaining = 60000L;
        float arenaWidth = 1200f;
        long countdownMs;
        long countdownUntil;
        long roundEndsAt;
        long updatedAt;
        PlayerState[] players = new PlayerState[]{new PlayerState(1), new PlayerState(2)};

        static GameState empty() {
            GameState state = new GameState();
            state.ok = true;
            return state;
        }

        static GameState fromJson(JSONObject json) {
            GameState state = empty();
            state.ok = json.optBoolean("ok", false);
            state.roomId = json.optString("roomId", "default");
            state.playerSlot = json.optInt("playerSlot", 0);
            state.status = json.optString("status", "offline");
            state.message = json.optString("message", "");
            state.mode = json.optString("mode", "ai");
            state.matchWinner = json.optInt("matchWinner", 0);
            state.roundWinner = json.optInt("roundWinner", 0);
            state.winRounds = json.optInt("winRounds", 2);
            state.aiLevel = json.optInt("aiLevel", 1);
            state.roundMs = json.optLong("roundMs", 60000L);
            state.roundMsRemaining = json.optLong("roundMsRemaining", state.roundMs);
            state.arenaWidth = (float) json.optDouble("arenaWidth", 1200d);
            state.countdownMs = json.optLong("countdownMs", 0L);
            JSONArray array = json.optJSONArray("players");
            if (array != null) {
                for (int i = 0; i < Math.min(2, array.length()); i++) {
                    state.players[i] = PlayerState.fromJson(array.optJSONObject(i), i + 1);
                }
            }
            return state;
        }

        int roundSeconds() {
            return (int) Math.max(0L, (roundMsRemaining + 999L) / 1000L);
        }
    }

    private static class PlayerState {
        int slot;
        String name = "";
        String archetype = "均衡";
        boolean connected;
        boolean ready;
        float maxHp = 100f;
        float hp = 100f;
        float energy = 28f;
        float maxGuard = 100f;
        float guard = 100f;
        float moveSpeed = 360f;
        int lightDamage = 8;
        int heavyDamage = 17;
        int specialDamage = 10;
        int wins;
        float x;
        float y;
        float vx;
        float vy;
        int facing = 1;
        String attack = "";
        int combo;
        boolean blocking;
        boolean hurt;
        boolean hitSpark;
        boolean guardBroken;
        boolean skillActive;
        boolean ultimateActive;
        long attackUntil;
        boolean attackHit;
        long cooldownUntil;
        long hurtUntil;
        long blockUntil;
        long comboUntil;
        long hitSparkUntil;
        long guardBreakUntil;
        long skillUntil;
        long ultimateUntil;
        long invincibleUntil;
        InputState input = new InputState();

        PlayerState(int slot) {
            this.slot = slot;
            this.name = slot == 1 ? "Egg" : "朋友";
            this.x = slot == 1 ? 290f : 910f;
        }

        static PlayerState fromJson(JSONObject json, int fallbackSlot) {
            PlayerState state = new PlayerState(fallbackSlot);
            if (json == null) return state;
            state.slot = json.optInt("slot", fallbackSlot);
            state.name = json.optString("name", state.name);
            FighterStats stats = fighterStatsForName(state.name);
            state.archetype = json.optString("archetype", stats.archetype);
            state.maxHp = (float) json.optDouble("maxHp", stats.maxHp);
            state.maxGuard = (float) json.optDouble("maxGuard", stats.maxGuard);
            state.moveSpeed = (float) json.optDouble("moveSpeed", stats.moveSpeed);
            state.lightDamage = json.optInt("lightDamage", stats.lightDamage);
            state.heavyDamage = json.optInt("heavyDamage", stats.heavyDamage);
            state.specialDamage = json.optInt("specialDamage", stats.specialDamage);
            state.connected = json.optBoolean("connected", false);
            state.ready = json.optBoolean("ready", false);
            state.hp = (float) json.optDouble("hp", state.maxHp);
            state.energy = (float) json.optDouble("energy", 28d);
            state.guard = (float) json.optDouble("guard", state.maxGuard);
            state.wins = json.optInt("wins", 0);
            state.x = (float) json.optDouble("x", state.x);
            state.y = (float) json.optDouble("y", 0d);
            state.facing = json.optInt("facing", state.slot == 1 ? 1 : -1);
            state.attack = json.optString("attack", "");
            state.combo = json.optInt("combo", 0);
            state.blocking = json.optBoolean("blocking", false);
            state.hurt = json.optBoolean("hurt", false);
            state.hitSpark = json.optBoolean("hitSpark", false);
            state.guardBroken = json.optBoolean("guardBroken", false);
            state.skillActive = json.optBoolean("skillActive", false);
            state.ultimateActive = json.optBoolean("ultimateActive", false);
            return state;
        }
    }

    private static class FighterStats {
        final String archetype;
        final float maxHp;
        final float maxGuard;
        final float moveSpeed;
        final int lightDamage;
        final int heavyDamage;
        final int specialDamage;

        FighterStats(String archetype, float maxHp, float maxGuard, float moveSpeed,
                     int lightDamage, int heavyDamage, int specialDamage) {
            this.archetype = archetype;
            this.maxHp = maxHp;
            this.maxGuard = maxGuard;
            this.moveSpeed = moveSpeed;
            this.lightDamage = lightDamage;
            this.heavyDamage = heavyDamage;
            this.specialDamage = specialDamage;
        }
    }
}
