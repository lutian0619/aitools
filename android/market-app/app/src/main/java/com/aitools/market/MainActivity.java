package com.aitools.market;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "aitools_market";
    private static final String SERVICE_URL_KEY = "service_url";
    private static final String DISCOVERY_MESSAGE = "aitools-discovery";
    private static final int SERVICE_PORT = 8788;

    private static final int INK = Color.rgb(47, 41, 37);
    private static final int MUTED = Color.rgb(117, 104, 95);
    private static final int PAPER = Color.rgb(253, 239, 239);
    private static final int CARD = Color.rgb(255, 249, 246);
    private static final int LINE = Color.rgb(218, 208, 194);
    private static final int ACCENT = Color.rgb(138, 113, 94);
    private static final int ACCENT_WARM = Color.rgb(205, 187, 167);
    private static final int WARN = Color.rgb(164, 92, 32);

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
    private SharedPreferences preferences;
    private LinearLayout root;
    private LinearLayout listHost;
    private TextView serviceStatus;
    private TextView serviceUrlText;
    private String serviceUrl = "";
    private JSONArray tools = new JSONArray();
    private boolean inSettings = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        serviceUrl = preferences.getString(SERVICE_URL_KEY, "");
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        buildMainPage();
        if (serviceUrl.trim().isEmpty()) {
            detectService();
        } else {
            refreshTools();
        }
    }

    @Override
    public void onBackPressed() {
        if (inSettings) {
            buildMainPage();
            renderTools();
            return;
        }
        super.onBackPressed();
    }

    private void buildMainPage() {
        inSettings = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(14));
        root.setBackgroundColor(PAPER);
        setContentView(root);

        root.addView(header("工具市场", true), matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("统一发现、安装和打开本地工具。");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle, matchWrap());

        LinearLayout statusPanel = new LinearLayout(this);
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setPadding(dp(14), dp(12), dp(14), dp(12));
        statusPanel.setBackground(panelBackground());
        serviceStatus = label("服务状态", 13, MUTED);
        serviceUrlText = label("未连接", 16, INK);
        serviceUrlText.setTypeface(Typeface.DEFAULT_BOLD);
        statusPanel.addView(serviceStatus, matchWrap());
        statusPanel.addView(serviceUrlText, matchWrap());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(10), 0, 0);
        Button detect = smallButton("自动探测");
        detect.setOnClickListener(v -> detectService());
        Button refresh = smallButton("刷新");
        refresh.setOnClickListener(v -> refreshTools());
        actionRow.addView(detect, new LinearLayout.LayoutParams(0, dp(40), 1));
        actionRow.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        actionRow.addView(refresh, new LinearLayout.LayoutParams(0, dp(40), 1));
        statusPanel.addView(actionRow, matchWrap());
        root.addView(statusPanel, matchWrapWithBottom(14));

        ScrollView scroll = new ScrollView(this);
        listHost = new LinearLayout(this);
        listHost.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listHost, matchWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        renderTools();
        updateServiceViews("等待连接");
    }

    private LinearLayout header(String titleText, boolean withSettings) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(INK);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (withSettings) {
            View settings = new SettingsIconButton(this);
            settings.setContentDescription("设置");
            settings.setOnClickListener(v -> showSettingsPage());
            row.addView(settings, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        return row;
    }

    private void showSettingsPage() {
        inSettings = true;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(14));
        root.setBackgroundColor(PAPER);
        setContentView(root);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = navIcon("‹", "返回");
        back.setOnClickListener(v -> {
            buildMainPage();
            renderTools();
        });
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(40)));
        TextView title = label("设置", 24, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(12), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(head, matchWrapWithBottom(12));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());

        content.addView(sectionTitle("数据管理"), matchWrap());
        content.addView(settingsCard(
                "本机偏好",
                "工具市场只保存电脑服务地址，不参与日记同步，也不读取各工具的私有数据。",
                "服务连接、安装和更新操作都在首页完成。"
        ), cardLayout());

        content.addView(sectionTitle("关于"), matchWrap());
        content.addView(settingsCard(
                "工具市场",
                "统一管理 aitools 下的 Android 工具，负责发现服务、下载安装包和打开已安装工具。",
                appVersionText()
        ), cardLayout());

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void refreshTools() {
        String base = normalizeServiceUrl(serviceUrl);
        if (base.isEmpty()) {
            toast("先自动探测电脑服务");
            return;
        }
        updateServiceViews("刷新中");
        new Thread(() -> {
            try {
                JSONObject status = fetchStatus(base);
                JSONArray nextTools = status.optJSONArray("tools");
                if (nextTools == null) nextTools = new JSONArray();
                JSONArray finalTools = nextTools;
                runOnUiThread(() -> {
                    serviceUrl = base;
                    preferences.edit().putString(SERVICE_URL_KEY, base).apply();
                    tools = finalTools;
                    updateServiceViews("已连接");
                    renderTools();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    updateServiceViews("连接失败");
                    toast("刷新失败");
                });
            }
        }).start();
    }

    private void detectService() {
        updateServiceViews("探测中");
        new Thread(() -> {
            String found = findService(serviceUrl);
            runOnUiThread(() -> {
                if (found == null) {
                    updateServiceViews("未找到服务");
                    toast("没有找到电脑服务");
                    return;
                }
                serviceUrl = found;
                preferences.edit().putString(SERVICE_URL_KEY, found).apply();
                updateServiceViews("已找到");
                refreshTools();
            });
        }).start();
    }

    private void renderTools() {
        if (listHost == null) return;
        listHost.removeAllViews();
        if (tools.length() == 0) {
            TextView empty = label("还没有工具数据。连接电脑服务后会显示可安装工具。", 14, MUTED);
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            listHost.addView(empty, matchWrap());
            return;
        }
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null) continue;
            listHost.addView(toolCard(tool), cardLayout());
        }
    }

    private View toolCard(JSONObject tool) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(panelBackground());

        String id = tool.optString("id", "");
        String name = tool.optString("name", id);
        String description = tool.optString("description", "");
        String packageName = tool.optString("packageName", "");
        JSONObject apk = tool.optJSONObject("apk");
        boolean hasApk = apk != null && apk.optBoolean("hasApk", false);
        boolean installed = isInstalled(packageName);
        boolean isSelf = getPackageName().equals(packageName);

        TextView title = label(name, 20, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, matchWrap());

        TextView desc = label(description, 13, MUTED);
        desc.setLineSpacing(dp(2), 1.0f);
        desc.setPadding(0, dp(6), 0, dp(10));
        card.addView(desc, matchWrap());

        String status = installed ? installedText(packageName, isSelf) : "未安装";
        TextView meta = label(status + " / " + apkText(apk), 13, installed ? ACCENT : WARN);
        meta.setTypeface(Typeface.DEFAULT_BOLD);
        meta.setPadding(0, 0, 0, dp(10));
        card.addView(meta, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        if (installed && !isSelf) {
            Button open = primaryButton("打开");
            open.setOnClickListener(v -> openPackage(packageName));
            actions.addView(open, new LinearLayout.LayoutParams(0, dp(40), 1));
            actions.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        }
        Button install = installed ? smallButton(hasApk ? "更新" : "无安装包") : primaryButton(hasApk ? "安装" : "无安装包");
        install.setEnabled(hasApk);
        install.setOnClickListener(v -> openApk(id));
        actions.addView(install, new LinearLayout.LayoutParams(0, dp(40), 1));
        card.addView(actions, matchWrap());
        return card;
    }

    private void updateServiceViews(String state) {
        if (serviceStatus != null) {
            serviceStatus.setText("服务状态 / " + state);
            serviceStatus.setTextColor("已连接".equals(state) || "已找到".equals(state) ? ACCENT : MUTED);
        }
        if (serviceUrlText != null) {
            serviceUrlText.setText(serviceUrl.trim().isEmpty() ? "未连接" : serviceUrl);
        }
    }

    private JSONObject fetchStatus(String baseUrl) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(baseUrl + "/api/status").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1200);
            connection.setReadTimeout(1200);
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("status " + connection.getResponseCode());
            }
            String body = readSmallBody(connection, 256 * 1024);
            JSONObject status = new JSONObject(body);
            if (!"aitools".equals(status.optString("app", ""))) {
                throw new IllegalStateException("not aitools");
            }
            return status;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String findService(String currentUrl) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String current = normalizeServiceUrl(currentUrl);
        if (!current.isEmpty()) candidates.add(current);
        candidates.add("http://10.0.2.2:" + SERVICE_PORT);
        candidates.add("http://10.0.3.2:" + SERVICE_PORT);

        for (String candidate : candidates) {
            String found = probeService(candidate);
            if (found != null) return found;
        }

        String discovered = discoverServiceByUdp();
        if (discovered != null) return discovered;

        for (String prefix : localIpv4Prefixes()) {
            for (int i = 1; i <= 254; i++) {
                candidates.add("http://" + prefix + "." + i + ":" + SERVICE_PORT);
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(24);
        CompletionService<String> completion = new ExecutorCompletionService<>(pool);
        int submitted = 0;
        for (String candidate : candidates) {
            completion.submit((Callable<String>) () -> probeService(candidate));
            submitted++;
        }
        long deadline = System.currentTimeMillis() + 8000L;
        try {
            for (int received = 0; received < submitted && System.currentTimeMillis() < deadline; received++) {
                Future<String> future = completion.poll(500, TimeUnit.MILLISECONDS);
                if (future == null) {
                    received--;
                    continue;
                }
                try {
                    String found = future.get();
                    if (found != null) return found;
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
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
            socket.setSoTimeout(700);
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
            long deadline = System.currentTimeMillis() + 2500L;
            byte[] buffer = new byte[1024];
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    String body = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(body);
                    LinkedHashSet<String> responseCandidates = new LinkedHashSet<>();
                    addCandidate(responseCandidates, json.optString("url", ""));
                    JSONArray urls = json.optJSONArray("urls");
                    if (urls != null) {
                        for (int i = 0; i < urls.length(); i++) {
                            addCandidate(responseCandidates, urls.optString(i, ""));
                        }
                    }
                    addCandidate(responseCandidates, "http://" + response.getAddress().getHostAddress() + ":" + SERVICE_PORT);
                    for (String candidate : responseCandidates) {
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

    private String probeService(String baseUrl) {
        HttpURLConnection connection = null;
        try {
            String url = normalizeServiceUrl(baseUrl);
            if (url.isEmpty()) return null;
            connection = (HttpURLConnection) new URL(url + "/api/status").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(900);
            connection.setReadTimeout(900);
            if (connection.getResponseCode() != 200) return null;
            String body = readSmallBody(connection, 4096);
            JSONObject status = new JSONObject(body);
            return "aitools".equals(status.optString("app", "")) ? url : null;
        } catch (Exception ignored) {
            return null;
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
        return host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    private void openPackage(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            toast("无法打开这个工具");
            return;
        }
        startActivity(intent);
    }

    private void openApk(String toolId) {
        String base = normalizeServiceUrl(serviceUrl);
        if (base.isEmpty()) {
            toast("先自动探测电脑服务");
            return;
        }
        openUrl(base + "/api/tools/" + Uri.encode(toolId) + "/apk");
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast("当前手机无法打开链接");
        }
    }

    private boolean isInstalled(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return false;
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String installedText(String packageName, boolean self) {
        if (self) return "当前应用";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            long versionCode = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return "已安装 " + info.versionName + " / " + versionCode;
        } catch (Exception ignored) {
            return "已安装";
        }
    }

    private String apkText(JSONObject apk) {
        if (apk == null || !apk.optBoolean("hasApk", false)) return "APK 未构建";
        long size = apk.optLong("size", 0L);
        String time = apk.optString("updatedAt", "");
        String suffix = time.isEmpty() ? "" : " / " + formatTime(time);
        return "APK " + formatSize(size) + suffix;
    }

    private String formatSize(long size) {
        if (size <= 0) return "0 KB";
        if (size < 1024 * 1024) return Math.max(1, Math.round(size / 1024f)) + " KB";
        return String.format(Locale.CHINA, "%.1f MB", size / 1024f / 1024f);
    }

    private String formatTime(String iso) {
        try {
            String normalized = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) + "+0000" : iso;
            normalized = normalized.replaceAll("\\.(\\d{3})\\d+", ".$1");
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
            return timeFormat.format(parser.parse(normalized));
        } catch (Exception ignored) {
            return iso;
        }
    }

    private String normalizeServiceUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        try {
            URL parsed = new URL(url);
            StringBuilder base = new StringBuilder();
            base.append(parsed.getProtocol()).append("://").append(parsed.getHost());
            if (parsed.getPort() > 0) base.append(":").append(parsed.getPort());
            else base.append(":").append(SERVICE_PORT);
            return base.toString();
        } catch (Exception ignored) {
        }
        return url;
    }

    private void addCandidate(LinkedHashSet<String> candidates, String raw) {
        String normalized = normalizeServiceUrl(raw);
        if (!normalized.isEmpty()) candidates.add(normalized);
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private TextView sectionTitle(String text) {
        TextView title = label(text, 15, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(14), 0, dp(8));
        return title;
    }

    private LinearLayout settingsCard(String titleText, String bodyText, String footText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(panelBackground());
        TextView title = label(titleText, 17, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, matchWrap());
        TextView body = label(bodyText, 14, MUTED);
        body.setLineSpacing(dp(2), 1.0f);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body, matchWrap());
        TextView foot = label(footText, 13, ACCENT);
        foot.setLineSpacing(dp(2), 1.0f);
        foot.setPadding(0, dp(8), 0, 0);
        card.addView(foot, matchWrap());
        return card;
    }

    private String appVersionText() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return "工具市场 " + info.versionName + " / " + versionCode;
        } catch (Exception ignored) {
            return "工具市场";
        }
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(ACCENT);
        button.setBackground(buttonBackground(CARD, LINE));
        return button;
    }

    private TextView navIcon(String text, String description) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(30);
        view.setTextColor(ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        return view;
    }

    private Button primaryButton(String text) {
        Button button = smallButton(text);
        button.setTextColor(Color.rgb(255, 250, 242));
        button.setBackground(buttonBackground(ACCENT, ACCENT));
        return button;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(CARD);
        drawable.setStroke(dp(1), LINE);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable buttonBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams matchWrapWithBottom(int bottomDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(bottomDp));
        return params;
    }

    private LinearLayout.LayoutParams cardLayout() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private LinearLayout.LayoutParams fullButtonLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        params.setMargins(0, dp(8), 0, 0);
        return params;
    }

    private View space(int size) {
        View view = new View(this);
        view.setMinimumWidth(size);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class SettingsIconButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SettingsIconButton(Context context) {
            super(context);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float outer = dp(8);
            float inner = dp(5);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(ACCENT);
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8.0;
                float x1 = cx + (float) Math.cos(angle) * outer;
                float y1 = cy + (float) Math.sin(angle) * outer;
                float x2 = cx + (float) Math.cos(angle) * (outer + dp(3));
                float y2 = cy + (float) Math.sin(angle) * (outer + dp(3));
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawCircle(cx, cy, inner, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, dp(2), paint);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
