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

    private final AtomicBoolean alive = new AtomicBoolean(false);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.rgb(18, 24, 31));
        getWindow().setNavigationBarColor(Color.rgb(18, 24, 31));
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        deviceId = preferences.getString(DEVICE_ID, "");
        if (deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            preferences.edit().putString(DEVICE_ID, deviceId).apply();
        }
        gameView = new EggGameView(this);
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
                        payload.put("name", "Egg");
                        JSONObject response = postJson(serviceUrl + "/api/egg-friends/join", payload);
                        applyState(response);
                        joined = response.optBoolean("ok", false) && response.optInt("playerSlot", 0) > 0;
                    } else {
                        InputState snapshot = input.copy();
                        roomId = targetRoomId();
                        JSONObject payload = new JSONObject();
                        payload.put("deviceId", deviceId);
                        payload.put("roomId", roomId);
                        payload.put("mode", gameMode == MODE_AI ? "ai" : "pvp");
                        payload.put("name", "Egg");
                        payload.put("left", snapshot.left);
                        payload.put("right", snapshot.right);
                        payload.put("jump", snapshot.jump);
                        payload.put("light", snapshot.light);
                        payload.put("heavy", snapshot.heavy);
                        payload.put("special", snapshot.special);
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
        state = GameState.empty();
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
        private final RectF blockButton = new RectF();
        private final RectF readyButton = new RectF();
        private final RectF reconnectButton = new RectF();
        private final RectF settingsButton = new RectF();
        private final RectF pvpButton = new RectF();
        private final RectF aiButton = new RectF();
        private final RectF closeSettingsButton = new RectF();
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
            drawStatus(canvas, snapshot, w, h);
            drawControls(canvas);
            if (settingsOpen) drawSettingsOverlay(canvas, w, h);
        }

        private boolean isFighting(String status) {
            return "running".equals(status) || "countdown".equals(status);
        }

        private void drawStage(Canvas canvas, int w, int h) {
            canvas.drawColor(Color.rgb(14, 20, 28));
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(19, 28, 38));
            canvas.drawRect(0, h * 0.30f, w, h, paint);

            paint.setColor(Color.argb(34, 245, 196, 88));
            canvas.drawCircle(w / 2f, h * 0.40f, w * 0.34f, paint);
            paint.setColor(Color.argb(42, 120, 196, 178));
            canvas.drawCircle(w / 2f, h * 0.42f, w * 0.22f, paint);

            paint.setColor(Color.rgb(43, 56, 68));
            canvas.drawRect(0, h * 0.36f, w, h * 0.39f, paint);
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
            paint.setColor(Color.argb(44, 120, 196, 178));
            for (int i = 1; i < 4; i++) {
                float y = h * (0.42f + i * 0.08f);
                canvas.drawLine(0, y, w, y, paint);
            }
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
            paint.setColor(Color.rgb(245, 196, 88));
            canvas.drawText(String.valueOf(snapshot.roundSeconds()), w / 2f, dp(40), paint);
            paint.setTextSize(sp(11));
            paint.setColor(Color.rgb(255, 244, 210));
            canvas.drawText(p1.wins + " : " + p2.wins + " / " + snapshot.winRounds, w / 2f, dp(57), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawHealth(Canvas canvas, float x, float y, float width, PlayerState player, boolean mirror) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(210, 10, 14, 20));
            canvas.drawRoundRect(new RectF(x, y, x + width, y + dp(20)), dp(6), dp(6), paint);
            float hpWidth = width * Math.max(0f, Math.min(1f, player.hp / 100f));
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
            drawMeter(canvas, x, y + dp(55), width, player.guard / 100f, Color.rgb(120, 154, 231), mirror);
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
            float bodyW = dp(60);
            float bodyH = dp(84);
            int body = player.slot == 1 ? Color.rgb(255, 247, 223) : Color.rgb(255, 228, 199);
            int band = player.slot == 1 ? Color.rgb(91, 196, 177) : Color.rgb(255, 122, 88);
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
            paint.setColor(body);
            RectF oval = new RectF(x - bodyW / 2f, ground - bodyH, x + bodyW / 2f, ground);
            canvas.drawOval(oval, paint);
            paint.setColor(Color.argb(88, 255, 255, 255));
            canvas.drawOval(new RectF(x - dp(18), ground - dp(74), x + dp(10), ground - dp(44)), paint);
            paint.setColor(band);
            canvas.drawRoundRect(new RectF(x - dp(28), ground - dp(43), x + dp(28), ground - dp(32)), dp(5), dp(5), paint);
            paint.setColor(player.slot == 1 ? Color.rgb(46, 63, 77) : Color.rgb(80, 49, 45));
            canvas.drawRoundRect(new RectF(x - dp(22), ground - dp(78), x + dp(22), ground - dp(66)), dp(8), dp(8), paint);
            paint.setColor(Color.rgb(31, 41, 51));
            float eyeShift = player.facing >= 0 ? dp(2) : -dp(2);
            canvas.drawCircle(x - dp(12) + eyeShift, ground - dp(52), dp(4), paint);
            canvas.drawCircle(x + dp(12) + eyeShift, ground - dp(52), dp(4), paint);
            paint.setColor(Color.rgb(255, 162, 132));
            canvas.drawCircle(x - dp(21), ground - dp(43), dp(5), paint);
            canvas.drawCircle(x + dp(21), ground - dp(43), dp(5), paint);
            paint.setStrokeWidth(dp(4));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            float armY = ground - dp(38);
            float dir = player.facing >= 0 ? 1f : -1f;
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
            paint.setColor(player.slot == 1 ? Color.rgb(91, 196, 177) : Color.rgb(255, 122, 88));
            canvas.drawOval(new RectF(x - dp(26), ground - dp(5), x - dp(6), ground + dp(6)), paint);
            canvas.drawOval(new RectF(x + dp(6), ground - dp(5), x + dp(26), ground + dp(6)), paint);
            if (!player.attack.isEmpty()) {
                boolean special = player.attack.equals("special");
                paint.setColor(special ? Color.argb(205, 245, 196, 88)
                        : (player.slot == 1 ? Color.argb(185, 91, 196, 177) : Color.argb(185, 255, 122, 88)));
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
            jumpButton.set(w - dp(338), bottom - dp(58), w - dp(280), bottom);
            blockButton.set(w - dp(274), bottom - dp(58), w - dp(216), bottom);
            specialButton.set(w - dp(210), bottom - dp(58), w - dp(152), bottom);
            lightButton.set(w - dp(146), bottom - dp(58), w - dp(88), bottom);
            heavyButton.set(w - dp(82), bottom - dp(58), w - dp(24), bottom);
            if (fighting) {
                readyButton.setEmpty();
                reconnectButton.setEmpty();
                pvpButton.setEmpty();
                aiButton.setEmpty();
            } else {
                pvpButton.set(w / 2f - dp(164), h / 2f + dp(10), w / 2f, h / 2f + dp(50));
                aiButton.set(w / 2f, h / 2f + dp(10), w / 2f + dp(164), h / 2f + dp(50));
                readyButton.set(w / 2f - dp(164), h / 2f + dp(68), w / 2f + dp(164), h / 2f + dp(116));
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
                drawSpecialButton(canvas, specialButton, snapshot.special, localPlayer().energy >= 55f);
            }
            boolean ended = "ended".equals(state.status) || "match-ended".equals(state.status);
            if (!fighting) {
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

            RectF panel = new RectF(w / 2f - dp(210), h / 2f - dp(104), w / 2f + dp(210), h / 2f + dp(136));
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
            paint.setTypeface(Typeface.DEFAULT);
        }

        private void drawResultBanner(Canvas canvas, GameState snapshot, int w, int h) {
            RectF banner = new RectF(w / 2f - dp(190), dp(82), w / 2f + dp(190), dp(126));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(225, 10, 14, 20));
            canvas.drawRoundRect(banner, dp(8), dp(8), paint);
            paint.setColor(snapshot.matchWinner > 0 ? Color.rgb(245, 196, 88) : Color.rgb(120, 196, 178));
            canvas.drawRoundRect(new RectF(banner.left, banner.top, banner.right, banner.top + dp(4)), dp(4), dp(4), paint);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(15));
            paint.setColor(Color.rgb(255, 244, 210));
            canvas.drawText(snapshot.message, w / 2f, banner.centerY() + dp(5), paint);
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

        private void drawSettingsIcon(Canvas canvas, RectF rect) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(170, 15, 20, 27));
            canvas.drawRoundRect(rect, dp(7), dp(7), paint);
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
            canvas.drawText("房间：" + (gameMode == MODE_AI ? "人机练习" : "二人局域网对战"), panel.left + dp(24), panel.top + dp(86), paint);
            canvas.drawText("规则：三局两胜；轻击快，重击远，必杀消耗能量；防御会耗护盾。", panel.left + dp(24), panel.top + dp(116), paint);
            canvas.drawText("服务：" + (serviceUrl.isEmpty() ? "未连接" : serviceUrl), panel.left + dp(24), panel.top + dp(146), paint);
            paint.setColor(ACCENT);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("关于", panel.left + dp(24), panel.top + dp(184), paint);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(INK);
            canvas.drawText("Egg和他的朋友们：横屏实时格斗，支持二人对战和人机练习。", panel.left + dp(24), panel.top + dp(216), paint);
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

        private void drawSpecialButton(Canvas canvas, RectF rect, boolean pressed, boolean ready) {
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
            canvas.drawText("必", rect.centerX(), rect.centerY() + dp(9), paint);
            paint.setTypeface(Typeface.DEFAULT);
        }

        private PlayerState localPlayer() {
            int index = Math.max(0, Math.min(1, state.playerSlot - 1));
            return state.players[index];
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
                if (readyButton.contains(x, y)) {
                    if ("ended".equals(state.status) || "match-ended".equals(state.status)) next.restart = true;
                    next.ready = true;
                }
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                    boolean fighting = isFighting(state.status);
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
        long roundMs = 60000L;
        long roundMsRemaining = 60000L;
        float arenaWidth = 1200f;
        long countdownMs;
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
        boolean connected;
        boolean ready;
        float hp = 100f;
        float energy = 28f;
        float guard = 100f;
        int wins;
        float x;
        float y;
        int facing = 1;
        String attack = "";
        int combo;
        boolean blocking;
        boolean hurt;
        boolean hitSpark;

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
            state.connected = json.optBoolean("connected", false);
            state.ready = json.optBoolean("ready", false);
            state.hp = (float) json.optDouble("hp", 100d);
            state.energy = (float) json.optDouble("energy", 28d);
            state.guard = (float) json.optDouble("guard", 100d);
            state.wins = json.optInt("wins", 0);
            state.x = (float) json.optDouble("x", state.x);
            state.y = (float) json.optDouble("y", 0d);
            state.facing = json.optInt("facing", state.slot == 1 ? 1 : -1);
            state.attack = json.optString("attack", "");
            state.combo = json.optInt("combo", 0);
            state.blocking = json.optBoolean("blocking", false);
            state.hurt = json.optBoolean("hurt", false);
            state.hitSpark = json.optBoolean("hitSpark", false);
            return state;
        }
    }
}
