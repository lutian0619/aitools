package com.minitracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "daily_grid";
    private static final String ENTRIES_KEY = "entries";
    private static final String SERVICE_URL_KEY = "service_url";
    private static final String DEVICE_ID_KEY = "device_id";
    private static final String PROJECT_SCHEMA = "life-tracker.project.v1";
    private static final String RECORD_SCHEMA = "life-tracker.record.v2";
    private static final String DISCOVERY_MESSAGE = "aitools-discovery";
    private static final int SERVICE_PORT = 8788;

    private static final int INK = Color.rgb(47, 41, 37);
    private static final int MUTED = Color.rgb(117, 104, 95);
    private static final int PAPER = Color.rgb(253, 239, 239);
    private static final int CARD = Color.rgb(255, 249, 246);
    private static final int LINE = Color.rgb(218, 208, 194);
    private static final int ACCENT = Color.rgb(138, 113, 94);
    private static final int DANGER = Color.rgb(139, 46, 37);

    private final String[] kindValues = {"number", "check"};
    private final String[] kindNames = {"数值", "打卡"};
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    private SharedPreferences preferences;
    private LinearLayout root;
    private LinearLayout pageHost;
    private JSONArray entries = new JSONArray();
    private String serviceUrl = "";
    private String deviceId = "";
    private String currentProjectId = "";
    private String selectedRecordDate = "";
    private Date recordVisibleMonth = new Date();
    private int currentTab = 0;
    private boolean inSettings = false;
    private boolean inProjectDetail = false;
    private boolean inAnnualStats = false;
    private long detailProjectId = 0L;
    private String detailTrendDate = "";
    private int annualYear = Calendar.getInstance().get(Calendar.YEAR);
    private boolean projectFormOpen = false;
    private long editingProjectId = 0L;
    private long editingRecordId = 0L;
    private String projectKind = "number";

    private EditText projectNameInput;
    private EditText projectUnitInput;
    private String projectStatMode = "sum";
    private TextView projectNumberButton;
    private TextView projectCheckButton;
    private TextView projectSumButton;
    private TextView projectAvgButton;
    private LinearLayout projectUnitBlock;
    private LinearLayout projectStatBlock;
    private EditText amountInput;
    private EditText contentInput;
    private LinearLayout amountBlock;
    private LinearLayout contentBlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        serviceUrl = preferences.getString(SERVICE_URL_KEY, "");
        deviceId = ensureDeviceId();
        entries = readEntries();
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        buildMainPage();
    }

    @Override
    public void onBackPressed() {
        if (inSettings) {
            buildMainPage();
            return;
        }
        if (inAnnualStats) {
            showProjectDetail(detailProjectId);
            return;
        }
        if (inProjectDetail) {
            buildMainPage();
            return;
        }
        super.onBackPressed();
    }

    private void buildMainPage() {
        inSettings = false;
        inProjectDetail = false;
        inAnnualStats = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));
        root.setBackgroundColor(PAPER);
        setContentView(root);
        root.addView(header("日常格", true), matchWrapWithBottom(10));
        root.addView(tabBar(), matchWrapWithBottom(10));
        ScrollView scroll = new ScrollView(this);
        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pageHost, matchWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        renderCurrentTab();
    }

    private LinearLayout header(String title, boolean withSettings) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(iconText(""), new LinearLayout.LayoutParams(dp(44), dp(42)));
        TextView titleView = label(title, 24, INK);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView right = iconText(withSettings ? "⚙" : "");
        right.setContentDescription("设置");
        if (withSettings) right.setOnClickListener(v -> showSettingsPage());
        row.addView(right, new LinearLayout.LayoutParams(dp(44), dp(42)));
        return row;
    }

    private View tabBar() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        tabs.setBackground(roundRect(CARD, LINE, 12));
        String[] names = {"记录", "日历", "事项"};
        for (int i = 0; i < names.length; i++) {
            final int tab = i;
            TextView item = label(names[i], 13, tab == currentTab ? CARD : INK);
            item.setGravity(Gravity.CENTER);
            item.setTypeface(Typeface.DEFAULT_BOLD);
            item.setBackground(tab == currentTab ? roundRect(ACCENT, ACCENT, 10) : transparentBackground());
            item.setOnClickListener(v -> {
                currentTab = tab;
                buildMainPage();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1);
            tabs.addView(item, params);
        }
        return tabs;
    }

    private void renderCurrentTab() {
        pageHost.removeAllViews();
        if (currentTab == 0) renderRecordPage();
        if (currentTab == 1) renderCalendarPage();
        if (currentTab == 2) renderProjectPage();
    }

    private void renderRecordPage() {
        List<JSONObject> projects = activeProjects();
        selectedRecordDate = today();
        syncRecordVisibleMonth();
        if (projects.isEmpty()) {
            pageHost.addView(emptyCard("先添加事项", ""), matchWrapWithBottom(10));
            Button add = primaryButton("添加事项");
            add.setOnClickListener(v -> {
                currentTab = 2;
                buildMainPage();
            });
            pageHost.addView(add, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
            return;
        }
        TextView dayTitle = label("今天 · " + selectedRecordDate, 20, INK);
        dayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        pageHost.addView(dayTitle, matchWrapWithBottom(8));
        for (JSONObject project : projects) {
            pageHost.addView(dailyProjectRow(project), matchWrapWithBottom(8));
        }
    }

    private void renderCalendarPage() {
        List<JSONObject> projects = activeProjects();
        if (selectedRecordDate.trim().isEmpty()) selectedRecordDate = today();
        if (projects.isEmpty()) {
            pageHost.addView(emptyCard("先添加事项", ""), matchWrapWithBottom(10));
            Button add = primaryButton("添加事项");
            add.setOnClickListener(v -> {
                currentTab = 2;
                buildMainPage();
            });
            pageHost.addView(add, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
            return;
        }
        pageHost.addView(recordCalendar(), matchWrapWithBottom(10));
        TextView dayTitle = label(formatHistoryDate(selectedRecordDate), 20, INK);
        dayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        pageHost.addView(dayTitle, matchWrapWithBottom(8));
        for (JSONObject project : projects) {
            pageHost.addView(dailyProjectRow(project), matchWrapWithBottom(8));
        }
    }

    private View projectPicker(List<JSONObject> projects) {
        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < projects.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                host.addView(row, matchWrapWithBottom(8));
            }
            JSONObject project = projects.get(i);
            String id = String.valueOf(project.optLong("id"));
            Button button = smallButton(project.optString("name", "事项"));
            if (id.equals(currentProjectId)) {
                button.setTextColor(ACCENT);
                button.setBackground(roundRect(Color.rgb(244, 223, 208), ACCENT, 8));
            }
            button.setOnClickListener(v -> {
                currentProjectId = id;
                editingRecordId = 0L;
                renderCurrentTab();
            });
            row.addView(button, new LinearLayout.LayoutParams(0, dp(40), 1));
            if (i % 2 == 0) row.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        }
        return host;
    }

    private View recordCalendar() {
        LinearLayout box = panel();
        Calendar month = Calendar.getInstance();
        month.setTime(recordVisibleMonth);
        month.set(Calendar.DAY_OF_MONTH, 1);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = smallButton("‹");
        prev.setOnClickListener(v -> {
            moveRecordMonth(-1);
        });
        Button todayButton = smallButton("今天");
        todayButton.setOnClickListener(v -> {
            selectedRecordDate = today();
            syncRecordVisibleMonth();
            currentProjectId = "";
            editingRecordId = 0L;
            renderCurrentTab();
        });
        TextView title = label(month.get(Calendar.YEAR) + " 年 " + (month.get(Calendar.MONTH) + 1) + " 月", 16, INK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        Button next = smallButton("›");
        next.setOnClickListener(v -> {
            moveRecordMonth(1);
        });
        head.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(38)));
        head.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1));
        head.addView(todayButton, new LinearLayout.LayoutParams(dp(72), dp(38)));
        head.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        head.addView(next, new LinearLayout.LayoutParams(dp(42), dp(38)));
        box.addView(head, matchWrapWithBottom(8));

        LinearLayout weekdays = new LinearLayout(this);
        weekdays.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"日", "一", "二", "三", "四", "五", "六"};
        for (String name : names) {
            TextView day = label(name, 12, MUTED);
            day.setGravity(Gravity.CENTER);
            day.setTypeface(Typeface.DEFAULT_BOLD);
            weekdays.addView(day, new LinearLayout.LayoutParams(0, dp(26), 1));
        }
        box.addView(weekdays, matchWrap());

        Calendar cursor = (Calendar) month.clone();
        cursor.add(Calendar.DAY_OF_MONTH, -cursor.get(Calendar.DAY_OF_WEEK) + 1);
        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 7; col++) {
                String date = dayFormat.format(cursor.getTime());
                int count = recordsForDay(date).size();
                boolean future = date.compareTo(today()) > 0;
                TextView cell = label(cursor.get(Calendar.DAY_OF_MONTH) + "\n" + (count > 0 ? count : " "), 12,
                        future ? Color.rgb(180, 176, 166) : (cursor.get(Calendar.MONTH) == month.get(Calendar.MONTH) ? INK : Color.rgb(170, 168, 158)));
                cell.setGravity(Gravity.CENTER);
                cell.setAlpha(future ? 0.45f : 1f);
                cell.setBackground(calendarCellBackground(date, count));
                cell.setOnClickListener(v -> {
                    if (date.compareTo(today()) > 0) {
                        toast("不能记录未来日期");
                        return;
                    }
                    selectedRecordDate = date;
                    syncRecordVisibleMonth();
                    currentProjectId = "";
                    editingRecordId = 0L;
                    renderCurrentTab();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
                params.setMargins(dp(1), dp(1), dp(1), dp(1));
                row.addView(cell, params);
                cursor.add(Calendar.DAY_OF_MONTH, 1);
            }
            box.addView(row, matchWrap());
        }
        return box;
    }

    private GradientDrawable calendarCellBackground(String date, int count) {
        int fill = CARD;
        int stroke = LINE;
        if (date.equals(selectedRecordDate)) {
            fill = ACCENT;
            stroke = ACCENT;
        } else if (date.equals(today())) {
            stroke = ACCENT;
        } else if (count > 0) {
            fill = Color.rgb(248, 231, 219);
        }
        return roundRect(fill, stroke, 8);
    }

    private void moveRecordMonth(int diff) {
        shiftRecordMonth(diff);
        renderCurrentTab();
    }

    private void shiftRecordMonth(int diff) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(recordVisibleMonth);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.add(Calendar.MONTH, diff);
        recordVisibleMonth = calendar.getTime();
    }

    private void syncRecordVisibleMonth() {
        try {
            Date date = dayFormat.parse(selectedRecordDate);
            if (date != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                recordVisibleMonth = calendar.getTime();
            }
        } catch (Exception ignored) {
        }
    }

    private View dailyProjectRow(JSONObject project) {
        JSONObject recordEntry = recordForProjectDate(project.optLong("id"), selectedRecordDate);
        boolean recorded = recordEntry != null;
        LinearLayout card = panel();
        if (recorded) card.setBackground(recordedBackground());
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView state = label(recorded ? "✓" : "", 14, recorded ? Color.WHITE : MUTED);
        state.setGravity(Gravity.CENTER);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setBackground(roundRect(recorded ? ACCENT : CARD, recorded ? ACCENT : LINE, 999));
        row.addView(state, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(10), 0, dp(8), 0);
        TextView title = label(project.optString("name", "事项"), 18, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView summary = label(recordSummary(project, recordEntry), 13, recorded ? INK : MUTED);
        text.addView(title, matchWrap());
        text.addView(summary, matchWrap());
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        String kind = normalizeProjectKind(project.optString("kind", "number"));
        if ("check".equals(kind)) {
            Button toggle = recorded ? smallButton("修改") : primaryButton("打卡");
            toggle.setOnClickListener(v -> {
                currentProjectId = String.valueOf(project.optLong("id"));
                editingRecordId = recorded ? recordEntry.optLong("id") : 0L;
                renderCurrentTab();
            });
            row.addView(toggle, new LinearLayout.LayoutParams(dp(86), dp(40)));
        } else {
            boolean editing = String.valueOf(project.optLong("id")).equals(currentProjectId);
            Button edit = editing ? smallButton("收起") : (recorded ? smallButton("修改") : primaryButton("打卡"));
            edit.setOnClickListener(v -> {
                if (editing) {
                    currentProjectId = "";
                    editingRecordId = 0L;
                } else {
                    currentProjectId = String.valueOf(project.optLong("id"));
                    editingRecordId = recorded ? recordEntry.optLong("id") : 0L;
                }
                renderCurrentTab();
            });
            row.addView(edit, new LinearLayout.LayoutParams(dp(86), dp(40)));
        }
        card.addView(row, matchWrap());
        if (String.valueOf(project.optLong("id")).equals(currentProjectId)) {
            LinearLayout.LayoutParams params = matchWrap();
            params.setMargins(0, dp(10), 0, 0);
            card.addView(recordEditor(project), params);
        }
        return card;
    }

    private GradientDrawable recordedBackground() {
        return roundRect(Color.rgb(244, 223, 208), ACCENT, 8);
    }

    private JSONObject recordForProjectDate(long projectId, String date) {
        for (JSONObject entry : activeRecords()) {
            JSONObject record = parseBody(entry);
            if (String.valueOf(projectId).equals(record.optString("projectId")) && date.equals(record.optString("date"))) {
                return entry;
            }
        }
        return null;
    }

    private String recordSummary(JSONObject project, JSONObject recordEntry) {
        if (recordEntry == null) return "这天还没有记录";
        JSONObject record = parseBody(recordEntry);
        String kind = normalizeProjectKind(project.optString("kind", "number"));
        if ("check".equals(kind)) {
            String content = record.optString("content", "");
            return content.isEmpty() ? "已打卡" : "已打卡 · " + content;
        }
        if ("number".equals(kind)) {
            String value = record.isNull("amount") ? "" : trimNumber(record.optDouble("amount", 0)) + record.optString("unit", project.optString("unit", ""));
            String content = record.optString("content", "");
            return content.isEmpty() ? value : value + " · " + content;
        }
        return clean(record.optString("content", ""), "已记录");
    }

    private void toggleCheckRecord(JSONObject project, JSONObject recordEntry) {
        if (recordEntry != null) {
            deleteEntry(recordEntry.optLong("id"));
            return;
        }
        try {
            long now = System.currentTimeMillis();
            JSONObject record = new JSONObject();
            record.put("schema", RECORD_SCHEMA);
            record.put("projectId", String.valueOf(project.optLong("id")));
            record.put("projectName", project.optString("name", "事项"));
            record.put("kind", "check");
            record.put("date", selectedRecordDate);
            record.put("content", "");
            record.put("note", "");
            record.put("amount", JSONObject.NULL);
            record.put("unit", "");
            JSONObject entry = baseEntry(now, null, now);
            entry.put("createdAt", dateToTime(selectedRecordDate, now));
            entry.put("tags", new JSONArray().put("record").put(project.optLong("id")).put(selectedRecordDate));
            entry.put("body", record.toString());
            mergeEntry(entry);
            saveEntries();
            toast("已完成");
            renderCurrentTab();
        } catch (Exception error) {
            toast("保存失败");
        }
    }

    private View recordEditor(JSONObject project) {
        JSONObject existing = recordForProjectDate(project.optLong("id"), selectedRecordDate);
        if (existing != null) editingRecordId = existing.optLong("id");
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(38), dp(2), 0, 0);
        amountInput = input("", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        contentInput = input("", InputType.TYPE_CLASS_TEXT);
        if (existing != null) {
            JSONObject record = parseBody(existing);
            amountInput.setText(record.isNull("amount") ? "" : trimNumber(record.optDouble("amount", 0)));
            contentInput.setText(record.optString("content", ""));
        }
        amountBlock = field("数值" + unitSuffix(project.optString("unit", "")), amountInput);
        contentBlock = field("说明（可选）", contentInput);
        panel.addView(amountBlock, matchWrapWithBottom(10));
        panel.addView(contentBlock, matchWrapWithBottom(12));
        applyRecordVisibility(project.optString("kind", "number"));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button save = primaryButton(existing == null ? "保存记录" : "保存修改");
        save.setOnClickListener(v -> saveRecord());
        Button cancel = smallButton("取消");
        cancel.setOnClickListener(v -> {
            currentProjectId = "";
            editingRecordId = 0L;
            renderCurrentTab();
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(actions, matchWrap());
        if (existing != null) {
            Button delete = smallButton("删除记录");
            delete.setTextColor(DANGER);
            delete.setOnClickListener(v -> confirmDeleteRecord(existing.optLong("id")));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(42)
            );
            params.setMargins(0, dp(8), 0, 0);
            panel.addView(delete, params);
        }
        return panel;
    }

    private void renderProjectPage() {
        Button add = primaryButton("新增事项");
        add.setOnClickListener(v -> {
            projectFormOpen = true;
            editingProjectId = 0L;
            projectKind = "number";
            projectStatMode = "sum";
            renderCurrentTab();
        });
        if (!projectFormOpen) pageHost.addView(add, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        if (projectFormOpen) pageHost.addView(projectForm(), matchWrapWithBottom(12));
        TextView title = label("全部事项", 18, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(12), 0, dp(8));
        pageHost.addView(title, matchWrap());
        for (JSONObject project : activeProjects()) {
            pageHost.addView(projectCard(project), matchWrapWithBottom(8));
        }
    }

    private View projectForm() {
        LinearLayout form = editingProjectId == 0L ? panel() : new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        if (editingProjectId != 0L) {
            form.setPadding(0, dp(10), 0, 0);
        }
        if (editingProjectId == 0L) {
            form.addView(sectionHead("自定义", "新增事项"), matchWrapWithBottom(12));
        }
        projectNameInput = input("", InputType.TYPE_CLASS_TEXT);
        projectUnitInput = input("", InputType.TYPE_CLASS_TEXT);
        form.addView(field("事项名称", projectNameInput), matchWrapWithBottom(10));
        form.addView(field("记录类型", projectKindSelector()), matchWrapWithBottom(10));
        projectUnitBlock = field("默认单位", projectUnitInput);
        form.addView(projectUnitBlock, matchWrapWithBottom(10));
        projectStatBlock = field("统计方式", projectStatSelector());
        form.addView(projectStatBlock, matchWrapWithBottom(10));
        Button save = primaryButton(editingProjectId == 0L ? "保存事项" : "更新事项");
        save.setOnClickListener(v -> saveProject());
        Button cancel = smallButton("取消");
        cancel.setOnClickListener(v -> {
            projectFormOpen = false;
            editingProjectId = 0L;
            projectKind = "number";
            projectStatMode = "sum";
            renderCurrentTab();
        });
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1));
        form.addView(actions, matchWrap());
        if (editingProjectId != 0L) {
            Button delete = smallButton("删除事项");
            delete.setTextColor(DANGER);
            delete.setOnClickListener(v -> confirmDeleteProject(editingProjectId));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(42)
            );
            params.setMargins(0, dp(8), 0, 0);
            form.addView(delete, params);
        }
        applyProjectVisibility();
        return form;
    }

    private View projectCard(JSONObject project) {
        LinearLayout card = panel();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(project.optString("name", "事项"), 18, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        text.addView(title, matchWrap());
        text.addView(label(projectMeta(project), 12, MUTED), matchWrap());
        JSONObject latest = latestRecordForProject(project.optLong("id"));
        if (latest != null) text.addView(label("最近 " + parseBody(latest).optString("date", ""), 12, ACCENT), matchWrap());
        text.setOnClickListener(v -> showProjectDetail(project.optLong("id")));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        boolean editing = editingProjectId == project.optLong("id");
        Button edit = smallButton(editing ? "收起" : "编辑");
        edit.setOnClickListener(v -> {
            if (editingProjectId == project.optLong("id")) {
                editingProjectId = 0L;
                projectKind = "number";
                projectStatMode = "sum";
            } else {
                projectFormOpen = false;
                editingProjectId = project.optLong("id");
                projectKind = normalizeProjectKind(project.optString("kind", "number"));
                projectStatMode = project.optString("statMode", "sum").equals("avg") ? "avg" : "sum";
            }
            renderCurrentTab();
        });
        row.addView(edit, new LinearLayout.LayoutParams(dp(76), dp(40)));
        card.addView(row, matchWrap());
        if (editing) {
            LinearLayout form = (LinearLayout) projectForm();
            projectNameInput.setText(project.optString("name", ""));
            projectUnitInput.setText(project.optString("unit", ""));
            projectStatMode = project.optString("statMode", "sum").equals("avg") ? "avg" : "sum";
            applyProjectVisibility();
            card.addView(form, matchWrap());
        }
        return card;
    }

    private View projectSummaryCard(JSONObject project, List<JSONObject> records) {
        LinearLayout card = panel();
        TextView kind = label(projectMeta(project), 12, ACCENT);
        kind.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(kind, matchWrapWithBottom(6));
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(miniStat("总记录", String.valueOf(records.size())), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        stats.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        stats.addView(miniStat("本月", String.valueOf(monthRecordCount(records))), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        stats.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        stats.addView(miniStat("最近", latestDate(records)), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(stats, matchWrapWithBottom(12));
        return card;
    }

    private View miniStat(String title, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(9), dp(10), dp(9));
        box.setBackground(roundRect(Color.rgb(248, 231, 219), LINE, 8));
        TextView top = label(title, 11, MUTED);
        TextView main = label(value, 16, INK);
        main.setTypeface(Typeface.DEFAULT_BOLD);
        main.setSingleLine(true);
        box.addView(top, matchWrap());
        box.addView(main, matchWrap());
        return box;
    }

    private View detailCalendar(JSONObject project) {
        LinearLayout box = panel();
        Calendar month = Calendar.getInstance();
        month.setTime(recordVisibleMonth);
        month.set(Calendar.DAY_OF_MONTH, 1);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = iconText("‹");
        prev.setTextSize(28);
        prev.setOnClickListener(v -> {
            shiftRecordMonth(-1);
            showProjectDetail(project.optLong("id"));
        });
        TextView title = label(month.get(Calendar.YEAR) + " 年 " + (month.get(Calendar.MONTH) + 1) + " 月", 16, INK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView next = iconText("›");
        next.setTextSize(28);
        next.setOnClickListener(v -> {
            shiftRecordMonth(1);
            showProjectDetail(project.optLong("id"));
        });
        head.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(36)));
        head.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1));
        head.addView(next, new LinearLayout.LayoutParams(dp(42), dp(36)));
        box.addView(head, matchWrapWithBottom(8));
        box.addView(calendarWeekHeader(), matchWrap());
        Calendar cursor = (Calendar) month.clone();
        cursor.add(Calendar.DAY_OF_MONTH, -cursor.get(Calendar.DAY_OF_WEEK) + 1);
        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 7; col++) {
                String date = dayFormat.format(cursor.getTime());
                JSONObject record = recordForProjectDate(project.optLong("id"), date);
                boolean inMonth = cursor.get(Calendar.MONTH) == month.get(Calendar.MONTH);
                boolean selected = date.equals(selectedRecordDate);
                boolean today = date.equals(today());
                boolean future = date.compareTo(today()) > 0;
                int cellColor = future ? Color.rgb(180, 176, 166) : (selected ? Color.WHITE : (inMonth ? INK : Color.rgb(176, 176, 166)));
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setAlpha(future ? 0.45f : 1f);
                cell.setBackground(dayBackground(selected, record != null, today));
                TextView dayNumber = label(String.valueOf(cursor.get(Calendar.DAY_OF_MONTH)), 12, cellColor);
                dayNumber.setGravity(Gravity.CENTER);
                dayNumber.setTypeface(record != null ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                TextView mark = label(record != null ? "✓" : "", 11, selected ? Color.WHITE : ACCENT);
                mark.setGravity(Gravity.CENTER);
                mark.setTypeface(Typeface.DEFAULT_BOLD);
                cell.addView(dayNumber, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(19)));
                cell.addView(mark, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(15)));
                cell.setOnClickListener(v -> {
                    if (date.compareTo(today()) > 0) {
                        toast("不能记录未来日期");
                        return;
                    }
                    selectedRecordDate = date;
                    syncRecordVisibleMonth();
                    currentProjectId = String.valueOf(project.optLong("id"));
                    editingRecordId = record == null ? 0L : record.optLong("id");
                    currentTab = 1;
                    buildMainPage();
                });
                row.addView(cell, calendarCellLayout());
                cursor.add(Calendar.DAY_OF_MONTH, 1);
            }
            box.addView(row, matchWrap());
        }
        return box;
    }

    private View numberChartCard(JSONObject project, List<JSONObject> records) {
        LinearLayout card = panel();
        TextView title = label("趋势", 18, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, matchWrapWithBottom(8));

        TrendWindow window = trendWindow(detailTrendDate);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = iconText("‹");
        prev.setTextSize(28);
        prev.setOnClickListener(v -> {
            detailTrendDate = clampTrendDate(shiftTrendDate(detailTrendDate, -1));
            renderProjectDetail(projectById(detailProjectId));
        });
        TextView windowTitle = label(window.title, 14, INK);
        windowTitle.setGravity(Gravity.CENTER);
        windowTitle.setTypeface(Typeface.DEFAULT_BOLD);
        TextView next = iconText("›");
        next.setTextSize(28);
        boolean canNext = !trendWindow(shiftTrendDate(detailTrendDate, 1)).start.after(parseDay(today()));
        next.setEnabled(canNext);
        next.setAlpha(canNext ? 1f : 0.35f);
        next.setOnClickListener(v -> {
            if (!next.isEnabled()) return;
            detailTrendDate = clampTrendDate(shiftTrendDate(detailTrendDate, 1));
            renderProjectDetail(projectById(detailProjectId));
        });
        nav.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(34)));
        nav.addView(windowTitle, new LinearLayout.LayoutParams(0, dp(34), 1));
        nav.addView(next, new LinearLayout.LayoutParams(dp(42), dp(34)));
        card.addView(nav, matchWrapWithBottom(8));

        List<JSONObject> displayRecords = trendWindowRecords(records, window);
        if (displayRecords.isEmpty()) {
            TextView empty = label("这个时间段还没有数值", 13, MUTED);
            empty.setGravity(Gravity.CENTER);
            card.addView(empty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)));
            return card;
        }
        double latest = displayRecords.get(displayRecords.size() - 1).optDouble("amount", 0);
        String unit = project.optString("unit", "");
        String caption = trimNumber(latest) + unit + " · " + displayRecords.size() + " 天有记录";
        TextView hint = label(caption, 13, MUTED);
        hint.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(hint, matchWrapWithBottom(4));
        card.addView(new BarChartView(this, displayRecords, window.days, unit, window.startText, window.endText, hint, caption), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(156)));
        return card;
    }

    private View detailRecordRow(JSONObject project, JSONObject recordEntry) {
        JSONObject record = parseBody(recordEntry);
        LinearLayout card = panel();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView date = label(record.optString("date", ""), 15, INK);
        date.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(date, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(top, matchWrapWithBottom(4));
        String summary = recordSummary(project, recordEntry);
        card.addView(label(summary, 14, MUTED), matchWrap());
        return card;
    }

    private void showProjectDetail(long projectId) {
        inProjectDetail = true;
        inAnnualStats = false;
        detailProjectId = projectId;
        detailTrendDate = today();
        selectedRecordDate = today();
        syncRecordVisibleMonth();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));
        root.setBackgroundColor(PAPER);
        setContentView(root);

        JSONObject project = projectById(projectId);
        String titleText = project == null ? "事项" : project.optString("name", "事项");
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = iconText("‹");
        back.setTextSize(30);
        back.setOnClickListener(v -> buildMainPage());
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(42)));
        TextView title = label(titleText, 22, INK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        head.addView(iconText(""), new LinearLayout.LayoutParams(dp(44), dp(42)));
        root.addView(head, matchWrapWithBottom(10));

        ScrollView scroll = new ScrollView(this);
        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pageHost, matchWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        renderProjectDetail(project);
    }

    private void renderProjectDetail(JSONObject project) {
        pageHost.removeAllViews();
        if (project == null) {
            pageHost.addView(emptyCard("事项不存在", ""), matchWrap());
            return;
        }
        List<JSONObject> records = recordsForProject(project.optLong("id"));
        pageHost.addView(projectSummaryCard(project, records), matchWrapWithBottom(10));
        Button annual = smallButton("年度统计");
        annual.setOnClickListener(v -> showAnnualStats(project.optLong("id")));
        pageHost.addView(annual, matchWrapWithBottom(10));
        if ("number".equals(normalizeProjectKind(project.optString("kind", "number")))) {
            pageHost.addView(numberChartCard(project, records), matchWrapWithBottom(10));
        } else {
            pageHost.addView(detailCalendar(project), matchWrapWithBottom(10));
        }
        TextView history = label("记录", 18, INK);
        history.setTypeface(Typeface.DEFAULT_BOLD);
        history.setPadding(0, dp(4), 0, dp(8));
        pageHost.addView(history, matchWrap());
        if (records.isEmpty()) {
            pageHost.addView(emptyCard("还没有记录", ""), matchWrap());
            return;
        }
        for (JSONObject record : records) pageHost.addView(detailRecordRow(project, record), matchWrapWithBottom(8));
    }

    private void showAnnualStats(long projectId) {
        inAnnualStats = true;
        inProjectDetail = false;
        detailProjectId = projectId;
        annualYear = Calendar.getInstance().get(Calendar.YEAR);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));
        root.setBackgroundColor(PAPER);
        setContentView(root);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = iconText("‹");
        back.setTextSize(30);
        back.setOnClickListener(v -> showProjectDetail(detailProjectId));
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(42)));
        TextView title = label("年度统计", 22, INK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        head.addView(iconText(""), new LinearLayout.LayoutParams(dp(44), dp(42)));
        root.addView(head, matchWrapWithBottom(10));
        ScrollView scroll = new ScrollView(this);
        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pageHost, matchWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        renderAnnualStats();
    }

    private void renderAnnualStats() {
        pageHost.removeAllViews();
        JSONObject project = projectById(detailProjectId);
        if (project == null) {
            pageHost.addView(emptyCard("事项不存在", ""), matchWrap());
            return;
        }
        List<JSONObject> yearRecords = recordsForProject(detailProjectId);
        List<JSONObject> filtered = new ArrayList<>();
        String prefix = String.valueOf(annualYear) + "-";
        for (JSONObject entry : yearRecords) {
            if (parseBody(entry).optString("date", "").startsWith(prefix)) filtered.add(entry);
        }
        pageHost.addView(sectionHead(kindLabel(project.optString("kind", "number")), project.optString("name", "事项")), matchWrapWithBottom(12));
        LinearLayout yearBar = new LinearLayout(this);
        yearBar.setOrientation(LinearLayout.HORIZONTAL);
        TextView prev = iconText("‹");
        prev.setTextSize(28);
        prev.setOnClickListener(v -> {
            annualYear -= 1;
            renderAnnualStats();
        });
        TextView year = label(String.valueOf(annualYear), 20, INK);
        year.setGravity(Gravity.CENTER);
        year.setTypeface(Typeface.DEFAULT_BOLD);
        TextView next = iconText("›");
        next.setTextSize(28);
        next.setOnClickListener(v -> {
            annualYear += 1;
            renderAnnualStats();
        });
        yearBar.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(36)));
        yearBar.addView(year, new LinearLayout.LayoutParams(0, dp(36), 1));
        yearBar.addView(next, new LinearLayout.LayoutParams(dp(42), dp(36)));
        pageHost.addView(yearBar, matchWrapWithBottom(10));
        pageHost.addView(annualMonthlyCard(project, filtered), matchWrapWithBottom(10));
        pageHost.addView(annualGridCard(project, filtered), matchWrapWithBottom(10));
    }

    private View annualMonthlyCard(JSONObject project, List<JSONObject> records) {
        LinearLayout card = panel();
        TextView title = label("按月统计", 18, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, matchWrapWithBottom(8));
        String summary = annualMonthSummary(records);
        TextView selected = label(summary, 13, MUTED);
        card.addView(selected, matchWrapWithBottom(8));
        card.addView(new AnnualMonthChartView(this, project, records, selected, summary), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)));
        return card;
    }

    private View annualGridCard(JSONObject project, List<JSONObject> records) {
        LinearLayout card = panel();
        TextView title = label("全年一览", 18, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, matchWrapWithBottom(8));
        card.addView(label(annualYear + " 年共有 " + annualRecordDays(records).size() + " 天有记录", 13, MUTED), matchWrapWithBottom(8));
        card.addView(new AnnualGridView(this, project, records), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));
        return card;
    }

    private LinkedHashSet<String> annualRecordDays(List<JSONObject> records) {
        LinkedHashSet<String> days = new LinkedHashSet<>();
        for (JSONObject entry : records) days.add(parseBody(entry).optString("date", ""));
        days.remove("");
        return days;
    }

    private String annualMonthSummary(List<JSONObject> records) {
        LinkedHashSet<String> months = new LinkedHashSet<>();
        for (JSONObject entry : records) {
            String date = parseBody(entry).optString("date", "");
            if (date.length() >= 7) months.add(date.substring(0, 7));
        }
        return months.isEmpty() ? annualYear + " 年暂无记录" : annualYear + " 年共有 " + months.size() + " 个月有记录";
    }

    private float[] annualMonthValues(JSONObject project, List<JSONObject> records) {
        float[] values = new float[12];
        int[] counts = new int[values.length];
        boolean number = "number".equals(normalizeProjectKind(project.optString("kind", "number")));
        for (JSONObject entry : records) {
            JSONObject record = parseBody(entry);
            Date parsed = parseDay(record.optString("date", ""));
            if (parsed == null) continue;
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parsed);
            int index = calendar.get(Calendar.MONTH);
            if (number) {
                if (!record.isNull("amount")) {
                    values[index] += (float) record.optDouble("amount", 0);
                    counts[index]++;
                }
            } else {
                values[index] += 1f;
            }
        }
        if (number && "avg".equals(project.optString("statMode", "sum"))) {
            for (int i = 0; i < values.length; i++) {
                if (counts[i] > 0) values[i] = values[i] / counts[i];
            }
        }
        return values;
    }

    private String[] annualMonthLabels(JSONObject project, List<JSONObject> records, float[] values) {
        String[] labels = new String[12];
        int[] entries = new int[12];
        for (JSONObject entry : records) {
            JSONObject record = parseBody(entry);
            Date parsed = parseDay(record.optString("date", ""));
            if (parsed == null) continue;
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parsed);
            entries[calendar.get(Calendar.MONTH)]++;
        }
        boolean number = "number".equals(normalizeProjectKind(project.optString("kind", "number")));
        String unit = number ? project.optString("unit", "") : "次";
        for (int i = 0; i < labels.length; i++) {
            String prefix = annualYear + " 年 " + (i + 1) + " 月";
            labels[i] = entries[i] == 0 ? prefix + " 无记录" : prefix + " " + trimNumber(values[i]) + unit;
        }
        return labels;
    }

    private int daysInMonth(int year, int monthZeroBased) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, monthZeroBased, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private void showSettingsPage() {
        inSettings = true;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));
        root.setBackgroundColor(PAPER);
        setContentView(root);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = iconText("‹");
        back.setTextSize(30);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> buildMainPage());
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(42)));
        TextView title = label("设置", 24, INK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        head.addView(iconText(""), new LinearLayout.LayoutParams(dp(44), dp(42)));
        root.addView(head, matchWrapWithBottom(12));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        content.addView(sectionTitle("电脑服务"), matchWrap());
        EditText syncUrl = input(serviceUrl, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        syncUrl.setHint("自动探测或输入 http://电脑IP:8788");
        syncUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                serviceUrl = normalizeServiceUrl(syncUrl.getText().toString());
                preferences.edit().putString(SERVICE_URL_KEY, serviceUrl).apply();
            }
        });
        content.addView(syncUrl, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        Button detect = smallButton("自动探测电脑服务");
        detect.setOnClickListener(v -> {
            serviceUrl = normalizeServiceUrl(syncUrl.getText().toString());
            preferences.edit().putString(SERVICE_URL_KEY, serviceUrl).apply();
            detectService();
        });
        content.addView(detect, fullButtonLayout());
        Button sync = primaryButton("同步");
        sync.setOnClickListener(v -> {
            serviceUrl = normalizeServiceUrl(syncUrl.getText().toString());
            preferences.edit().putString(SERVICE_URL_KEY, serviceUrl).apply();
            syncNow();
        });
        content.addView(sync, matchWrapWithBottom(14));

        content.addView(sectionTitle("数据管理"), matchWrap());
        content.addView(settingsCard(
                "本地数据",
                activeProjects().size() + " 个事项 / " + activeRecords().size() + " 条记录",
                "每条记录都能选择日期，支持补打；同步只在你手动点击时执行。"
        ), matchWrapWithBottom(14));

        content.addView(sectionTitle("关于"), matchWrap());
        content.addView(settingsCard(
                "日常格",
                "自定义事项的日常记录工具，支持补打、按事项查看趋势、Web 客户端和手机同步。",
                appVersionText()
        ), matchWrap());
    }

    private void saveProject() {
        String name = projectNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            toast("请填写事项名称");
            return;
        }
        try {
            long now = System.currentTimeMillis();
            JSONObject existing = entryById(editingProjectId);
            JSONObject project = new JSONObject();
            projectKind = normalizeProjectKind(projectKind);
            project.put("schema", PROJECT_SCHEMA);
            project.put("name", name);
            project.put("kind", projectKind);
            project.put("unit", "number".equals(projectKind) ? projectUnitInput.getText().toString().trim() : "");
            project.put("statMode", "number".equals(projectKind) && "avg".equals(projectStatMode) ? "avg" : "sum");
            project.put("note", "");
            JSONObject entry = baseEntry(editingProjectId == 0L ? now : editingProjectId, existing, now);
            entry.put("tags", new JSONArray().put("project").put(projectKind));
            entry.put("body", project.toString());
            mergeEntry(entry);
            saveEntries();
            currentProjectId = String.valueOf(entry.optLong("id"));
            boolean wasEditing = editingProjectId != 0L;
            editingProjectId = 0L;
            projectFormOpen = false;
            projectKind = "number";
            projectStatMode = "sum";
            toast("已保存事项");
            if (wasEditing) {
                currentTab = 2;
                renderCurrentTab();
                return;
            }
            currentTab = 0;
            buildMainPage();
        } catch (Exception error) {
            toast("保存失败");
        }
    }

    private void saveRecord() {
        JSONObject project = currentProject();
        if (project == null) {
            toast("请先添加事项");
            return;
        }
        try {
            String kind = normalizeProjectKind(project.optString("kind", "number"));
            String date = clean(selectedRecordDate, today());
            if (date.compareTo(today()) > 0) {
                toast("不能记录未来日期");
                return;
            }
            String content = contentInput == null ? "" : contentInput.getText().toString().trim();
            String amountText = amountInput == null ? "" : amountInput.getText().toString().trim();
            if ("number".equals(kind) && amountText.isEmpty()) {
                toast("请填写数值");
                return;
            }
            JSONObject record = new JSONObject();
            record.put("schema", RECORD_SCHEMA);
            record.put("projectId", String.valueOf(project.optLong("id")));
            record.put("projectName", project.optString("name", "事项"));
            record.put("kind", kind);
            record.put("date", date);
            record.put("content", content);
            record.put("note", "");
            if ("check".equals(kind)) {
                record.put("amount", JSONObject.NULL);
                record.put("unit", "");
            } else {
                record.put("amount", Double.parseDouble(amountText));
                record.put("unit", project.optString("unit", ""));
            }
            long now = System.currentTimeMillis();
            JSONObject existing = entryById(editingRecordId);
            JSONObject entry = baseEntry(editingRecordId == 0L ? now : editingRecordId, existing, now);
            entry.put("createdAt", existing == null ? dateToTime(record.optString("date", date), now) : existing.optLong("createdAt", now));
            entry.put("tags", new JSONArray().put("record").put(project.optLong("id")).put(record.optString("date", date)));
            entry.put("body", record.toString());
            mergeEntry(entry);
            saveEntries();
            editingRecordId = 0L;
            currentProjectId = "";
            toast("已保存记录");
            renderCurrentTab();
        } catch (Exception error) {
            toast("保存失败，请检查数值和日期");
        }
    }

    private JSONObject baseEntry(long id, JSONObject existing, long now) throws Exception {
        JSONObject entry = new JSONObject();
        entry.put("id", id);
        entry.put("createdAt", existing == null ? now : existing.optLong("createdAt", now));
        entry.put("updatedAt", now);
        entry.put("deletedAt", 0);
        entry.put("version", existing == null ? 1 : existing.optInt("version", 1) + 1);
        entry.put("deviceId", deviceId);
        entry.put("attachments", new JSONArray());
        return entry;
    }

    private void loadProject(JSONObject projectEntry) {
        JSONObject project = parseBody(projectEntry);
        editingProjectId = projectEntry.optLong("id");
        projectKind = normalizeProjectKind(project.optString("kind", "number"));
        projectStatMode = project.optString("statMode", "sum").equals("avg") ? "avg" : "sum";
        projectFormOpen = true;
        currentTab = 2;
        renderCurrentTab();
        projectNameInput.setText(project.optString("name", ""));
        projectUnitInput.setText(project.optString("unit", ""));
        projectStatMode = project.optString("statMode", "sum").equals("avg") ? "avg" : "sum";
        applyProjectVisibility();
    }

    private void loadRecord(JSONObject recordEntry) {
        JSONObject record = parseBody(recordEntry);
        editingRecordId = recordEntry.optLong("id");
        currentProjectId = record.optString("projectId", "");
        selectedRecordDate = record.optString("date", today());
        syncRecordVisibleMonth();
        currentTab = 0;
        renderCurrentTab();
    }

    private void deleteProject(long id) {
        JSONObject entry = entryById(id);
        if (entry == null) return;
        deleteEntry(id);
    }

    private void confirmDeleteProject(long id) {
        new AlertDialog.Builder(this)
                .setTitle("删除这个事项？")
                .setMessage("已有记录会保留为历史数据，但这个事项不会再出现在打卡入口。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    editingProjectId = 0L;
                    projectFormOpen = false;
                    currentTab = 2;
                    deleteProject(id);
                })
                .show();
    }

    private void confirmDeleteRecord(long id) {
        new AlertDialog.Builder(this)
                .setTitle("删除这条记录？")
                .setMessage("删除后会同步为已删除记录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteRecord(id))
                .show();
    }

    private void deleteRecord(long id) {
        try {
            JSONObject entry = entryById(id);
            if (entry == null) return;
            entry.put("deletedAt", System.currentTimeMillis());
            entry.put("updatedAt", System.currentTimeMillis());
            entry.put("version", entry.optInt("version", 1) + 1);
            saveEntries();
            currentProjectId = "";
            editingRecordId = 0L;
            toast("已删除");
            if (inProjectDetail) {
                showProjectDetail(detailProjectId);
            } else {
                renderCurrentTab();
            }
        } catch (Exception error) {
            toast("删除失败");
        }
    }

    private void deleteEntry(long id) {
        try {
            JSONObject entry = entryById(id);
            if (entry == null) return;
            entry.put("deletedAt", System.currentTimeMillis());
            entry.put("updatedAt", System.currentTimeMillis());
            entry.put("version", entry.optInt("version", 1) + 1);
            saveEntries();
            toast("已删除");
            renderCurrentTab();
        } catch (Exception error) {
            toast("删除失败");
        }
    }

    private void detectService() {
        toast("正在探测电脑服务");
        new Thread(() -> {
            String found = findService();
            runOnUiThread(() -> {
                if (found.isEmpty()) {
                    toast("没有找到电脑服务");
                    return;
                }
                serviceUrl = found;
                preferences.edit().putString(SERVICE_URL_KEY, serviceUrl).apply();
                syncNow();
            });
        }).start();
    }

    private String findService() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, serviceUrl);
        addCandidate(candidates, "http://10.0.2.2:" + SERVICE_PORT);
        addCandidate(candidates, "http://10.0.3.2:" + SERVICE_PORT);

        for (String candidate : candidates) {
            if (probeService(candidate)) return normalizeServiceUrl(candidate);
        }

        candidates.addAll(discoverByUdp());
        for (String prefix : localIpv4Prefixes()) {
            for (int suffix = 1; suffix <= 254; suffix++) {
                addCandidate(candidates, "http://" + prefix + "." + suffix + ":" + SERVICE_PORT);
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(24);
        CompletionService<String> completion = new ExecutorCompletionService<>(pool);
        int submitted = 0;
        for (String candidate : candidates) {
            completion.submit((Callable<String>) () -> probeService(candidate) ? normalizeServiceUrl(candidate) : "");
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
                    if (found != null && !found.isEmpty()) return found;
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return "";
    }

    private List<String> discoverByUdp() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        targets.add("255.255.255.255");
        for (String prefix : localIpv4Prefixes()) targets.add(prefix + ".255");
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(700);
            byte[] payload = DISCOVERY_MESSAGE.getBytes(StandardCharsets.UTF_8);
            for (int round = 0; round < 2; round++) {
                for (String target : targets) {
                    try {
                        socket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName(target), SERVICE_PORT));
                    } catch (Exception ignored) {
                    }
                }
            }
            long deadline = System.currentTimeMillis() + 2500L;
            while (System.currentTimeMillis() < deadline) {
                try {
                    byte[] buffer = new byte[8192];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    JSONObject json = new JSONObject(new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8));
                    addCandidate(result, json.optString("url", ""));
                    JSONArray urls = json.optJSONArray("urls");
                    if (urls != null) for (int i = 0; i < urls.length(); i++) addCandidate(result, urls.optString(i));
                    addCandidate(result, "http://" + response.getAddress().getHostAddress() + ":" + SERVICE_PORT);
                } catch (SocketTimeoutException timeout) {
                    break;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(result);
    }

    private boolean probeService(String baseUrl) {
        try {
            String url = normalizeServiceUrl(baseUrl);
            if (url.isEmpty()) return false;
            JSONObject status = getJson(url + "/api/tools/tracker/status");
            return "tracker".equals(status.optString("id"));
        } catch (Exception error) {
            return false;
        }
    }

    private void syncNow() {
        if (serviceUrl.trim().isEmpty()) {
            detectService();
            return;
        }
        toast("正在同步");
        new Thread(() -> {
            try {
                JSONObject remote = getJson(serviceUrl + "/api/tools/tracker/entries");
                JSONArray remoteEntries = remote.optJSONArray("entries");
                if (remoteEntries != null) {
                    for (int i = 0; i < remoteEntries.length(); i++) {
                        JSONObject entry = remoteEntries.optJSONObject(i);
                        if (entry != null) mergeEntry(entry);
                    }
                }
                saveEntries();
                JSONObject payload = new JSONObject();
                payload.put("entries", entries);
                postJson(serviceUrl + "/api/tools/tracker/entries", payload);
                runOnUiThread(() -> {
                    toast("同步完成");
                    if (inSettings) showSettingsPage(); else renderCurrentTab();
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast("同步失败"));
            }
        }).start();
    }

    private JSONObject getJson(String target) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(target).openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(2500);
            connection.setRequestMethod("GET");
            return new JSONObject(readResponse(connection));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONObject postJson(String target, JSONObject payload) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(target).openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream output = connection.getOutputStream();
            output.write(body);
            output.close();
            return new JSONObject(readResponse(connection));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        try (java.io.InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            if (input != null) while ((read = input.read(buffer)) >= 0) out.write(buffer, 0, read);
        }
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return out.toString("UTF-8");
    }

    private JSONArray readEntries() {
        try {
            return new JSONArray(preferences.getString(ENTRIES_KEY, "[]"));
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    private void saveEntries() {
        preferences.edit().putString(ENTRIES_KEY, entries.toString()).apply();
    }

    private boolean mergeEntry(JSONObject incoming) {
        JSONObject normalized = normalizeEntry(incoming);
        long id = normalized.optLong("id", 0L);
        if (id == 0L) return false;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject current = entries.optJSONObject(i);
            if (current != null && current.optLong("id") == id) {
                if (normalized.optInt("version", 1) > current.optInt("version", 1)
                        || normalized.optLong("updatedAt") >= current.optLong("updatedAt")) {
                    try {
                        entries.put(i, normalized);
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }
        }
        entries.put(normalized);
        return true;
    }

    private JSONObject normalizeEntry(JSONObject entry) {
        JSONObject next = new JSONObject();
        try {
            long id = entry.optLong("id", System.currentTimeMillis());
            next.put("id", id);
            next.put("createdAt", entry.optLong("createdAt", id));
            next.put("updatedAt", entry.optLong("updatedAt", id));
            next.put("deletedAt", entry.optLong("deletedAt", 0L));
            next.put("version", Math.max(1, entry.optInt("version", 1)));
            next.put("deviceId", entry.optString("deviceId", ""));
            next.put("tags", entry.optJSONArray("tags") == null ? new JSONArray() : entry.optJSONArray("tags"));
            next.put("attachments", entry.optJSONArray("attachments") == null ? new JSONArray() : entry.optJSONArray("attachments"));
            next.put("body", entry.optString("body", ""));
        } catch (Exception ignored) {
        }
        return next;
    }

    private JSONObject entryById(long id) {
        if (id == 0L) return null;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry != null && entry.optLong("id") == id) return entry;
        }
        return null;
    }

    private List<JSONObject> activeProjects() {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null || entry.optLong("deletedAt", 0L) > 0L) continue;
            JSONObject body = parseBody(entry);
            if (PROJECT_SCHEMA.equals(body.optString("schema"))) {
                try {
                    JSONObject project = new JSONObject(body.toString());
                    project.put("kind", normalizeProjectKind(project.optString("kind", "number")));
                    project.put("statMode", "avg".equals(project.optString("statMode", "sum")) ? "avg" : "sum");
                    project.put("id", entry.optLong("id"));
                    result.add(project);
                } catch (Exception ignored) {
                }
            }
        }
        Collections.sort(result, (a, b) -> a.optString("name").compareTo(b.optString("name")));
        return result;
    }

    private List<JSONObject> activeRecords() {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null || entry.optLong("deletedAt", 0L) > 0L) continue;
            if (RECORD_SCHEMA.equals(parseBody(entry).optString("schema"))) result.add(entry);
        }
        Collections.sort(result, (a, b) -> Long.compare(b.optLong("createdAt", b.optLong("id")), a.optLong("createdAt", a.optLong("id"))));
        return result;
    }

    private List<JSONObject> recordsForProject(long projectId) {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject entry : activeRecords()) {
            if (String.valueOf(projectId).equals(parseBody(entry).optString("projectId"))) result.add(entry);
        }
        return result;
    }

    private List<JSONObject> recordsForDay(String day) {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject entry : activeRecords()) {
            if (day.equals(parseBody(entry).optString("date"))) result.add(entry);
        }
        return result;
    }

    private JSONObject projectById(long projectId) {
        for (JSONObject project : activeProjects()) {
            if (project.optLong("id") == projectId) return project;
        }
        return null;
    }

    private JSONObject latestRecordForProject(long projectId) {
        List<JSONObject> records = recordsForProject(projectId);
        return records.isEmpty() ? null : records.get(0);
    }

    private String projectMeta(JSONObject project) {
        return kindLabel(normalizeProjectKind(project.optString("kind", "number"))) + unitSuffix(project.optString("unit", ""))
                + " · " + recordsForProject(project.optLong("id")).size() + " 条记录";
    }

    private int monthRecordCount(List<JSONObject> records) {
        Calendar calendar = Calendar.getInstance();
        String month = String.format(Locale.CHINA, "%04d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1);
        int count = 0;
        for (JSONObject entry : records) {
            if (parseBody(entry).optString("date", "").startsWith(month)) count++;
        }
        return count;
    }

    private String latestDate(List<JSONObject> records) {
        if (records.isEmpty()) return "-";
        return parseBody(records.get(0)).optString("date", "-").replaceFirst("^\\d{4}-", "");
    }

    private List<Float> numberValues(List<JSONObject> records) {
        List<Float> values = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0; i--) {
            JSONObject record = parseBody(records.get(i));
            if (!record.isNull("amount")) values.add((float) record.optDouble("amount", 0));
        }
        return values;
    }

    private List<JSONObject> numberRecords(List<JSONObject> records) {
        List<JSONObject> result = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0; i--) {
            JSONObject record = parseBody(records.get(i));
            if (!record.isNull("amount")) result.add(record);
        }
        return result;
    }

    private TrendWindow trendWindow(String anchorDate) {
        Date anchor = parseDay(clean(anchorDate, today()));
        if (anchor == null) anchor = new Date();
        Calendar start = Calendar.getInstance();
        start.setTime(anchor);
        Calendar end = Calendar.getInstance();
        end.setTime(anchor);
        start.set(Calendar.DAY_OF_MONTH, 1);
        end.setTime(start.getTime());
        end.add(Calendar.MONTH, 1);
        end.add(Calendar.DAY_OF_MONTH, -1);
        String startText = dayFormat.format(start.getTime());
        String endText = dayFormat.format(end.getTime());
        String title = start.get(Calendar.YEAR) + " 年 " + (start.get(Calendar.MONTH) + 1) + " 月";
        return new TrendWindow(start.getTime(), end.getTime(), startText, endText, Math.max(1, daysBetween(startText, endText) + 1), title);
    }

    private List<JSONObject> trendWindowRecords(List<JSONObject> records, TrendWindow window) {
        Map<String, JSONObject> byDate = new LinkedHashMap<>();
        for (JSONObject record : numberRecords(records)) {
            String date = record.optString("date", "");
            if (date.compareTo(window.startText) < 0 || date.compareTo(window.endText) > 0) continue;
            byDate.put(date, record);
        }
        List<JSONObject> result = new ArrayList<>();
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(window.start);
        for (int i = 0; i < window.days; i++) {
            String date = dayFormat.format(cursor.getTime());
            JSONObject record = byDate.get(date);
            if (record != null) {
                try {
                    JSONObject point = new JSONObject(record.toString());
                    point.put("dayIndex", i);
                    result.add(point);
                } catch (Exception ignored) {
                }
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        return result;
    }

    private String shiftTrendDate(String date, int delta) {
        Date base = parseDay(clean(date, today()));
        if (base == null) base = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(base);
        calendar.add(Calendar.MONTH, delta);
        calendar.set(Calendar.DAY_OF_MONTH, Math.min(calendar.get(Calendar.DAY_OF_MONTH), 28));
        return dayFormat.format(calendar.getTime());
    }

    private String clampTrendDate(String date) {
        String next = clean(date, today());
        return next.compareTo(today()) > 0 ? today() : next;
    }

    private Date parseDay(String date) {
        try {
            return dayFormat.parse(date);
        } catch (Exception error) {
            return null;
        }
    }

    private JSONObject currentProject() {
        for (JSONObject project : activeProjects()) {
            if (String.valueOf(project.optLong("id")).equals(currentProjectId)) return project;
        }
        return null;
    }

    private void ensureProjectSelection(List<JSONObject> projects) {
        for (JSONObject project : projects) {
            if (String.valueOf(project.optLong("id")).equals(currentProjectId)) return;
        }
        currentProjectId = String.valueOf(projects.get(0).optLong("id"));
    }

    private JSONObject parseBody(JSONObject entry) {
        try {
            return new JSONObject(entry.optString("body", "{}"));
        } catch (Exception error) {
            return new JSONObject();
        }
    }

    private LinearLayout projectKindSelector() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        row.setBackground(roundRect(Color.rgb(244, 223, 208), LINE, 8));
        projectNumberButton = projectKindOption("数值", "number");
        projectCheckButton = projectKindOption("打卡", "check");
        row.addView(projectNumberButton, new LinearLayout.LayoutParams(0, dp(34), 1));
        row.addView(space(3), new LinearLayout.LayoutParams(dp(3), 1));
        row.addView(projectCheckButton, new LinearLayout.LayoutParams(0, dp(34), 1));
        updateProjectKindButtons();
        return row;
    }

    private LinearLayout projectStatSelector() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        row.setBackground(roundRect(Color.rgb(244, 223, 208), LINE, 8));
        projectSumButton = projectStatOption("累计", "sum");
        projectAvgButton = projectStatOption("平均", "avg");
        row.addView(projectSumButton, new LinearLayout.LayoutParams(0, dp(34), 1));
        row.addView(space(3), new LinearLayout.LayoutParams(dp(3), 1));
        row.addView(projectAvgButton, new LinearLayout.LayoutParams(0, dp(34), 1));
        updateProjectStatButtons();
        return row;
    }

    private TextView projectStatOption(String text, String mode) {
        TextView button = label(text, 14, INK);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setOnClickListener(v -> {
            projectStatMode = "avg".equals(mode) ? "avg" : "sum";
            updateProjectStatButtons();
        });
        return button;
    }

    private TextView projectKindOption(String text, String kind) {
        TextView button = label(text, 14, INK);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setOnClickListener(v -> {
            projectKind = kind;
            applyProjectVisibility();
        });
        return button;
    }

    private void updateProjectKindButtons() {
        if (projectNumberButton == null || projectCheckButton == null) return;
        styleProjectKindButton(projectNumberButton, "number".equals(normalizeProjectKind(projectKind)));
        styleProjectKindButton(projectCheckButton, "check".equals(normalizeProjectKind(projectKind)));
    }

    private void updateProjectStatButtons() {
        if (projectSumButton == null || projectAvgButton == null) return;
        styleProjectKindButton(projectSumButton, !"avg".equals(projectStatMode));
        styleProjectKindButton(projectAvgButton, "avg".equals(projectStatMode));
    }

    private void styleProjectKindButton(TextView button, boolean selected) {
        button.setTextColor(selected ? CARD : INK);
        button.setBackground(selected ? roundRect(ACCENT, ACCENT, 8) : transparentBackground());
    }

    private String normalizeProjectKind(String kind) {
        return "number".equals(kind) ? "number" : "check";
    }

    private void applyProjectVisibility() {
        if (projectUnitBlock != null) {
            projectUnitBlock.setVisibility("number".equals(normalizeProjectKind(projectKind)) ? View.VISIBLE : View.GONE);
        }
        if (projectStatBlock != null) {
            projectStatBlock.setVisibility("number".equals(normalizeProjectKind(projectKind)) ? View.VISIBLE : View.GONE);
        }
        updateProjectKindButtons();
        updateProjectStatButtons();
    }

    private void applyRecordVisibility(String kind) {
        boolean check = "check".equals(normalizeProjectKind(kind));
        amountBlock.setVisibility(check ? View.GONE : View.VISIBLE);
        contentBlock.setVisibility(View.VISIBLE);
    }

    private long dateToTime(String date, long fallback) {
        try {
            Date parsed = dayFormat.parse(date);
            return parsed == null ? fallback : parsed.getTime() + 12L * 60L * 60L * 1000L;
        } catch (Exception error) {
            return fallback;
        }
    }

    private int daysBetween(String start, String end) {
        try {
            Date left = dayFormat.parse(start);
            Date right = dayFormat.parse(end);
            if (left == null || right == null) return 0;
            return Math.max(0, Math.round((right.getTime() - left.getTime()) / 86400000f));
        } catch (Exception error) {
            return 0;
        }
    }

    private String today() {
        return dayFormat.format(new Date());
    }

    private String formatHistoryDate(String date) {
        if (date == null || date.trim().isEmpty()) return today();
        if (date.equals(today())) return date + " 今天";
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        if (date.equals(dayFormat.format(yesterday.getTime()))) return date + " 昨天";
        return date;
    }

    private String shortDate(String date) {
        if (date == null) return "";
        return date.replaceFirst("^\\d{4}-", "");
    }

    private String clean(String value, String fallback) {
        String next = value == null ? "" : value.trim();
        return next.isEmpty() ? fallback : next;
    }

    private String kindLabel(String kind) {
        kind = normalizeProjectKind(kind);
        for (int i = 0; i < kindValues.length; i++) {
            if (kindValues[i].equals(kind)) return kindNames[i];
        }
        return "打卡";
    }

    private String unitSuffix(String unit) {
        return unit == null || unit.trim().isEmpty() ? "" : " · " + unit.trim();
    }

    private String trimNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001) return String.valueOf(Math.round(value));
        return String.format(Locale.CHINA, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    private float niceAxisMax(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0f) return 1f;
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        double normalized = value / magnitude;
        double step = normalized <= 2 ? 2 : normalized <= 4 ? 4 : normalized <= 6 ? 6 : normalized <= 8 ? 8 : 10;
        return Math.max(2f, (float) Math.ceil(step * magnitude));
    }

    private float[] adaptiveAxisBounds(List<Float> values) {
        if (values.isEmpty()) return new float[]{0f, 1f};
        boolean hasValue = false;
        float min = 0f;
        float max = 0f;
        for (float value : values) {
            if (Float.isNaN(value) || Float.isInfinite(value)) continue;
            if (!hasValue) {
                min = value;
                max = value;
                hasValue = true;
                continue;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (!hasValue) return new float[]{0f, 1f};
        float spread = max - min;
        if (spread <= 0f) {
            float padding = Math.max(Math.abs(max) * 0.08f, 1f);
            return niceAxisBounds(min - padding, max + padding);
        }
        float padding = spread * 0.12f;
        float lower = min - padding;
        float upper = max + padding;
        if (min >= 0f && lower < 0f) lower = 0f;
        if (max <= 0f && upper > 0f) upper = 0f;
        return niceAxisBounds(lower, upper);
    }

    private float[] niceAxisBounds(float lower, float upper) {
        if (Float.isNaN(lower) || Float.isInfinite(lower) || Float.isNaN(upper) || Float.isInfinite(upper)) {
            return new float[]{0f, 1f};
        }
        if (lower == upper) {
            lower -= 1f;
            upper += 1f;
        }
        float span = Math.max(0.0001f, Math.abs(upper - lower));
        float step = niceAxisStep(span / 4f);
        float niceMin = (float) Math.floor(lower / step) * step;
        float niceMax = (float) Math.ceil(upper / step) * step;
        if (niceMax <= niceMin) niceMax = niceMin + step;
        return new float[]{niceMin, niceMax};
    }

    private float niceAxisStep(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0f) return 1f;
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        double normalized = value / magnitude;
        if (normalized <= 1) return (float) magnitude;
        if (normalized <= 2) return (float) (2 * magnitude);
        if (normalized <= 5) return (float) (5 * magnitude);
        return (float) (10 * magnitude);
    }

    private String ensureDeviceId() {
        String id = preferences.getString(DEVICE_ID_KEY, "");
        if (id.isEmpty()) {
            id = "android-daily-grid-" + System.currentTimeMillis();
            preferences.edit().putString(DEVICE_ID_KEY, id).apply();
        }
        return id;
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

    private LinearLayout field(String title, View input) {
        return field(label(title, 12, MUTED), input);
    }

    private LinearLayout field(TextView title, View input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title, matchWrapWithBottom(5));
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private EditText input(String value, int inputType) {
        EditText editText = new EditText(this);
        editText.setText(value);
        editText.setTextColor(INK);
        editText.setTextSize(16);
        editText.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        editText.setInputType(inputType);
        editText.setPadding(dp(10), 0, dp(10), 0);
        editText.setMinHeight(dp(42));
        editText.setBackground(roundRect(CARD, LINE, 8));
        return editText;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(13), dp(14), dp(13));
        panel.setBackground(roundRect(CARD, LINE, 8));
        return panel;
    }

    private View sectionHead(String kicker, String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView top = label(kicker, 12, MUTED);
        top.setTypeface(Typeface.DEFAULT_BOLD);
        TextView main = label(title, 21, INK);
        main.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(top, matchWrap());
        box.addView(main, matchWrap());
        return box;
    }

    private TextView paragraph(String text) {
        TextView view = label(text, 14, INK);
        view.setLineSpacing(dp(2), 1.0f);
        return view;
    }

    private View statCard(String label, String value) {
        LinearLayout card = panel();
        card.addView(label(label, 12, MUTED), matchWrap());
        TextView number = label(value, 20, INK);
        number.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(number, matchWrap());
        return card;
    }

    private View emptyCard(String title, String body) {
        LinearLayout card = panel();
        TextView head = label(title, 17, INK);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head, matchWrap());
        if (body != null && !body.trim().isEmpty()) card.addView(label(body, 14, MUTED), matchWrap());
        return card;
    }

    private View settingsCard(String title, String value, String note) {
        LinearLayout card = panel();
        TextView head = label(title, 17, INK);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        TextView main = label(value, 14, MUTED);
        main.setLineSpacing(dp(2), 1.0f);
        main.setPadding(0, dp(8), 0, 0);
        TextView small = label(note, 13, ACCENT);
        small.setLineSpacing(dp(2), 1.0f);
        small.setPadding(0, dp(8), 0, 0);
        card.addView(head, matchWrap());
        card.addView(main, matchWrap());
        card.addView(small, matchWrap());
        return card;
    }

    private LinearLayout calendarWeekHeader() {
        LinearLayout weekdays = new LinearLayout(this);
        weekdays.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"日", "一", "二", "三", "四", "五", "六"};
        for (String name : names) {
            TextView day = label(name, 11, MUTED);
            day.setGravity(Gravity.CENTER);
            day.setTypeface(Typeface.DEFAULT_BOLD);
            weekdays.addView(day, calendarCellLayout());
        }
        return weekdays;
    }

    private GradientDrawable dayBackground(boolean selected, boolean hasRecord, boolean today) {
        if (selected) return roundRect(ACCENT, ACCENT, 9);
        if (today) return roundRect(CARD, ACCENT, 9);
        if (hasRecord) return roundRect(Color.rgb(244, 223, 208), Color.rgb(205, 187, 167), 9);
        return roundRect(CARD, LINE, 9);
    }

    private TextView sectionTitle(String text) {
        TextView title = label(text, 13, MUTED);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(8));
        return title;
    }

    private LinearLayout.LayoutParams fullButtonLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        params.setMargins(0, dp(8), 0, dp(8));
        return params;
    }

    private String appVersionText() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return "日常格 " + info.versionName + " / " + versionCode;
        } catch (Exception ignored) {
            return "日常格";
        }
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private TextView iconText(String text) {
        TextView view = label(text, 22, ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(INK);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundRect(CARD, LINE, 8));
        return button;
    }

    private Button primaryButton(String text) {
        Button button = smallButton(text);
        button.setTextColor(Color.WHITE);
        button.setBackground(roundRect(ACCENT, ACCENT, 8));
        return button;
    }

    private GradientDrawable roundRect(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable transparentBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(0, Color.TRANSPARENT);
        return drawable;
    }

    private View space(int dp) {
        View view = new View(this);
        view.setBackgroundColor(Color.TRANSPARENT);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithBottom(int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams calendarCellLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
        params.setMargins(dp(1), dp(1), dp(1), dp(1));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private class TrendWindow {
        final Date start;
        final Date end;
        final String startText;
        final String endText;
        final int days;
        final String title;

        TrendWindow(Date start, Date end, String startText, String endText, int days, String title) {
            this.start = start;
            this.end = end;
            this.startText = startText;
            this.endText = endText;
            this.days = days;
            this.title = title;
        }
    }

    private class BarChartView extends View {
        private final List<JSONObject> chartRecords;
        private final List<Float> values = new ArrayList<>();
        private final int windowDays;
        private final String unit;
        private final String startDate;
        private final String endDate;
        private final TextView selectedLabel;
        private final String defaultLabel;
        private int selectedIndex = -1;
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BarChartView(Context context, List<JSONObject> records, int windowDays, String unit, String startDate, String endDate, TextView selectedLabel, String defaultLabel) {
            super(context);
            this.chartRecords = records;
            this.windowDays = Math.max(1, windowDays);
            this.unit = unit == null ? "" : unit;
            this.startDate = startDate == null ? "" : startDate;
            this.endDate = endDate == null ? "" : endDate;
            this.selectedLabel = selectedLabel;
            this.defaultLabel = defaultLabel == null ? "" : defaultLabel;
            for (JSONObject record : records) values.add((float) record.optDouble("amount", 0));
            setClickable(true);
            barPaint.setColor(Color.rgb(200, 161, 90));
            barPaint.setStyle(Paint.Style.FILL);
            selectedPaint.setColor(ACCENT);
            selectedPaint.setStyle(Paint.Style.FILL);
            gridPaint.setColor(Color.rgb(232, 224, 208));
            gridPaint.setStrokeWidth(dp(1));
            labelPaint.setColor(MUTED);
            labelPaint.setTextSize(dp(11));
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
            datePaint.setColor(MUTED);
            datePaint.setTextSize(dp(10));
            datePaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP || chartRecords.isEmpty()) return true;
            float left = dp(26);
            float right = getWidth() - dp(18);
            float nearestDistance = Float.MAX_VALUE;
            int nearestIndex = 0;
            for (int i = 0; i < chartRecords.size(); i++) {
                int dayIndex = chartRecords.get(i).optInt("dayIndex", i);
                float ratio = windowDays <= 1 ? 0.5f : dayIndex / (windowDays - 1f);
                float x = left + (right - left) * ratio;
                float distance = Math.abs(event.getX() - x);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestIndex = i;
                }
            }
            if (selectedIndex == nearestIndex) {
                selectedIndex = -1;
                selectedLabel.setText(defaultLabel);
                invalidate();
                return true;
            }
            selectedIndex = nearestIndex;
            JSONObject record = chartRecords.get(nearestIndex);
            selectedLabel.setText(record.optString("date", "") + " · " + trimNumber(record.optDouble("amount", 0)) + unit);
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (values.isEmpty()) return;
            float left = dp(26);
            float right = getWidth() - dp(18);
            float top = dp(30);
            float bottom = getHeight() - dp(34);
            for (int i = 0; i < 3; i++) {
                float y = top + (bottom - top) * i / 2f;
                canvas.drawLine(left, y, right, y, gridPaint);
            }
            float[] axis = adaptiveAxisBounds(values);
            float baseMin = axis[0];
            float baseMax = axis[1];
            float range = Math.max(0.1f, baseMax - baseMin);
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(trimNumber(baseMax) + unit, getWidth() - dp(2), top + dp(4), labelPaint);
            canvas.drawText(trimNumber((baseMax + baseMin) / 2f) + unit, getWidth() - dp(2), top + (bottom - top) / 2f + dp(4), labelPaint);
            canvas.drawText(trimNumber(baseMin) + unit, getWidth() - dp(2), bottom + dp(4), labelPaint);
            float slot = (right - left) / windowDays;
            float barWidth = Math.max(dp(4), Math.min(dp(18), slot * 0.55f));
            for (int i = 0; i < chartRecords.size(); i++) {
                JSONObject record = chartRecords.get(i);
                float value = values.get(i);
                int dayIndex = record.optInt("dayIndex", i);
                float ratio = windowDays <= 1 ? 0.5f : dayIndex / (windowDays - 1f);
                float x = left + (right - left) * ratio;
                float y = bottom - (bottom - top) * (value - baseMin) / range;
                float height = Math.max(dp(3), bottom - y);
                Paint paint = i == selectedIndex ? selectedPaint : barPaint;
                canvas.drawRoundRect(x - barWidth / 2f, y, x + barWidth / 2f, y + height, dp(4), dp(4), paint);
            }
            int last = values.size() - 1;
            canvas.drawText(shortDate(startDate), left, bottom + dp(20), datePaint);
            canvas.drawText(shortDate(endDate), right, bottom + dp(20), datePaint);
        }
    }

    private class AnnualMonthChartView extends View {
        private final JSONObject project;
        private final float[] values;
        private final String[] labels;
        private final TextView selectedLabel;
        private final String defaultLabel;
        private int selectedIndex = -1;
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AnnualMonthChartView(Context context, JSONObject project, List<JSONObject> records, TextView selectedLabel, String defaultLabel) {
            super(context);
            this.project = project;
            this.values = annualMonthValues(project, records);
            this.labels = annualMonthLabels(project, records, values);
            this.selectedLabel = selectedLabel;
            this.defaultLabel = defaultLabel == null ? "" : defaultLabel;
            setClickable(true);
            barPaint.setColor(Color.rgb(200, 161, 90));
            selectedPaint.setColor(ACCENT);
            gridPaint.setColor(Color.rgb(232, 224, 208));
            gridPaint.setStrokeWidth(dp(1));
            textPaint.setColor(MUTED);
            textPaint.setTextSize(dp(10));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float left = dp(24);
            float right = getWidth() - dp(14);
            float slot = (right - left) / values.length;
            int index = Math.max(0, Math.min(values.length - 1, (int) ((event.getX() - left) / slot)));
            if (selectedIndex == index) {
                selectedIndex = -1;
                selectedLabel.setText(defaultLabel);
                invalidate();
                return true;
            }
            selectedIndex = index;
            selectedLabel.setText(labels[index]);
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(24);
            float right = getWidth() - dp(14);
            float top = dp(22);
            float bottom = getHeight() - dp(28);
            canvas.drawLine(left, top, right, top, gridPaint);
            canvas.drawLine(left, bottom, right, bottom, gridPaint);
            float max = 1f;
            for (float value : values) max = Math.max(max, value);
            float axisMax = niceAxisMax(max);
            float slot = (right - left) / values.length;
            float width = Math.max(dp(16), Math.min(dp(28), slot * 0.55f));
            for (int i = 0; i < values.length; i++) {
                float height = values[i] <= 0 ? 0 : (bottom - top) * values[i] / axisMax;
                float x = left + i * slot + slot / 2f;
                Paint paint = i == selectedIndex ? selectedPaint : barPaint;
                canvas.drawRoundRect(x - width / 2f, bottom - height, x + width / 2f, bottom, dp(3), dp(3), paint);
            }
            textPaint.setTextAlign(Paint.Align.RIGHT);
            String unit = "number".equals(normalizeProjectKind(project.optString("kind", "number"))) ? project.optString("unit", "") : "次";
            canvas.drawText(trimNumber(axisMax) + unit, getWidth() - dp(2), top + dp(4), textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("1月", left, bottom + dp(18), textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("12月", right, bottom + dp(18), textPaint);
        }
    }

    private class AnnualGridView extends View {
        private final LinkedHashSet<String> litDays;
        private final Paint monthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint litPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AnnualGridView(Context context, JSONObject project, List<JSONObject> records) {
            super(context);
            this.litDays = annualRecordDays(records);
            monthPaint.setColor(INK);
            monthPaint.setTextSize(dp(11));
            monthPaint.setTypeface(Typeface.DEFAULT_BOLD);
            emptyPaint.setColor(Color.rgb(244, 241, 233));
            litPaint.setColor(Color.rgb(200, 161, 90));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float labelWidth = dp(34);
            float gap = dp(2);
            float cell = Math.max(dp(5), (getWidth() - labelWidth - gap * 30) / 31f);
            float rowHeight = getHeight() / 12f;
            for (int month = 0; month < 12; month++) {
                float y = rowHeight * month + rowHeight * 0.66f;
                canvas.drawText((month + 1) + "月", 0, y, monthPaint);
                int days = daysInMonth(annualYear, month);
                for (int day = 1; day <= days; day++) {
                    String date = String.format(Locale.CHINA, "%04d-%02d-%02d", annualYear, month + 1, day);
                    float x = labelWidth + (day - 1) * (cell + gap);
                    float top = rowHeight * month + dp(5);
                    Paint paint = litDays.contains(date) ? litPaint : emptyPaint;
                    canvas.drawRoundRect(x, top, x + cell, top + cell, dp(1), dp(1), paint);
                }
            }
        }
    }
}
