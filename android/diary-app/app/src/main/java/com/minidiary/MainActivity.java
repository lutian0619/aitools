package com.minidiary;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.security.MessageDigest;
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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "mini_diary";
    private static final String ENTRIES_KEY = "entries";
    private static final String SQLITE_MIGRATED_KEY = "sqlite_migrated";
    private static final String SYNC_URL_KEY = "sync_url";
    private static final String LAST_SYNC_REMOTE_UPDATED_AT_KEY = "last_sync_remote_updated_at";
    private static final String LAST_SYNC_LOCAL_SIGNATURE_KEY = "last_sync_local_signature";
    private static final String DEVICE_ID_KEY = "device_id";
    private static final String CURRENT_AUTHOR_KEY = "current_author";
    private static final String DEFAULT_AUTHOR = "小妈妈";
    private static final String DISCOVERY_MESSAGE = "one-page-diary-discovery";
    private static final int SYNC_PORT = 8788;
    private static final int REQUEST_EXPORT = 42;
    private static final int REQUEST_EXPORT_JSON = 43;
    private static final int REQUEST_PICK_IMAGE = 44;
    private static final int REQUEST_EXPORT_ZIP = 45;
    private static final int REQUEST_IMPORT_ZIP = 46;
    private static final int TAB_WRITE = 0;
    private static final int TAB_CALENDAR = 1;
    private static final int TAB_TAGS = 2;
    private static final int PAGE_SIZE = 30;

    private static final int INK = Color.rgb(47, 41, 37);
    private static final int MUTED = Color.rgb(117, 104, 95);
    private static final int PAPER = Color.rgb(253, 239, 239);
    private static final int CARD = Color.rgb(255, 249, 246);
    private static final int LINE = Color.rgb(218, 208, 194);
    private static final int ACCENT = Color.rgb(138, 113, 94);
    private static final Pattern TAG_PATTERN = Pattern.compile("#([\\p{L}\\p{N}_-]+)");
    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile("!\\[图片\\]\\(attachment:([^)]+)\\)");

    private final SimpleDateFormat displayDate = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA);
    private final SimpleDateFormat dayTitle = new SimpleDateFormat("yyyy.MM.dd", Locale.CHINA);
    private final SimpleDateFormat monthTitleFormat = new SimpleDateFormat("yyyy 年 M 月", Locale.CHINA);
    private final SimpleDateFormat fileDate = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA);
    private final SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.CHINA);

    private SharedPreferences preferences;
    private DiaryDatabase database;
    private LinearLayout root;
    private LinearLayout pageHost;
    private LinearLayout tabBar;
    private TextView headerBack;
    private TextView headerTitle;
    private View headerSettings;
    private TextView tabWrite;
    private TextView tabCalendar;
    private TextView tabTags;
    private EditText bodyInput;
    private Button saveButton;
    private ImageButton clearButton;
    private LinearLayout writerTagHints;
    private LinearLayout writerImagePreview;
    private TextView monthTitle;
    private LinearLayout calendarGrid;
    private LinearLayout calendarEntries;
    private LinearLayout tagFilters;
    private LinearLayout tagResults;
    private TextView tagCount;
    private String selectedAuthor = null;

    private final Calendar visibleMonth = Calendar.getInstance(Locale.CHINA);
    private int currentTab = TAB_WRITE;
    private int detailReturnTab = TAB_WRITE;
    private int settingsReturnTab = TAB_WRITE;
    private boolean inDetail = false;
    private boolean inSettings = false;
    private int calendarListLimit = PAGE_SIZE;
    private int tagListLimit = PAGE_SIZE;
    private long selectedDayStart = -1L;
    private long editingId = 0L;
    private String editingOriginalBody = "";
    private String selectedTag = null;
    private String pendingExportText;
    private boolean pendingZipExport = false;
    private boolean stylingBodyText = false;
    private boolean stylingAttachmentImages = false;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        deviceId = ensureDeviceId();
        database = new DiaryDatabase(this);
        migrateLegacyEntriesIfNeeded();
        selectedDayStart = startOfDay(System.currentTimeMillis());
        visibleMonth.setTimeInMillis(selectedDayStart);

        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(10));
        root.setBackgroundColor(PAPER);
        setContentView(root);

        root.addView(header(), matchWrap());
        root.addView(tabs(), matchWrap());

        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        bodyInput = new EditText(this);
        bodyInput.setHint("今天...");
        bodyInput.setGravity(Gravity.TOP | Gravity.START);
        bodyInput.setTextSize(19);
        bodyInput.setMinLines(12);
        bodyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bodyInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        bodyInput.setBackground(editorBackground());
        bodyInput.setTextColor(INK);
        bodyInput.setHintTextColor(Color.rgb(151, 151, 141));
        bodyInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                styleTagsInEditor(s);
                styleAttachmentsInEditor(s);
                if (currentTab == TAB_WRITE && writerTagHints != null) {
                    renderWriterTagHints();
                }
                updateEditState();
            }
        });

        switchTab(TAB_WRITE);
        handleIncomingShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingShare(intent);
    }

    @Override
    protected void onDestroy() {
        cleanupUnreferencedAttachmentFiles();
        if (database != null) {
            database.close();
        }
        super.onDestroy();
    }

    private String ensureDeviceId() {
        String existing = preferences.getString(DEVICE_ID_KEY, "");
        if (existing != null && !existing.trim().isEmpty()) {
            return existing;
        }
        String next = "android-" + System.currentTimeMillis() + "-" + Math.abs((int) (Math.random() * 100000));
        preferences.edit().putString(DEVICE_ID_KEY, next).apply();
        return next;
    }

    private String currentAuthor() {
        return cleanAuthor(preferences.getString(CURRENT_AUTHOR_KEY, DEFAULT_AUTHOR));
    }

    private String cleanAuthor(String value) {
        if (value == null) {
            return DEFAULT_AUTHOR;
        }
        String author = value.trim();
        return author.isEmpty() ? DEFAULT_AUTHOR : author;
    }

    @Override
    public void onBackPressed() {
        if (inSettings) {
            switchTab(settingsReturnTab);
            return;
        }
        if (inDetail) {
            switchTab(detailReturnTab);
            return;
        }
        if (hasUnsavedDraft()) {
            confirmDiscardDraft(() -> {
                clearEditorOnly();
                MainActivity.super.onBackPressed();
            });
            return;
        }
        super.onBackPressed();
    }

    private LinearLayout header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(10));

        headerBack = new TextView(this);
        headerBack.setText("‹");
        headerBack.setTextSize(30);
        headerBack.setGravity(Gravity.CENTER);
        headerBack.setTextColor(ACCENT);
        headerBack.setVisibility(View.GONE);
        headerBack.setOnClickListener(v -> handleHeaderBack());
        row.addView(headerBack, new LinearLayout.LayoutParams(dp(44), dp(44)));

        headerTitle = new TextView(this);
        headerTitle.setText("一页日记");
        headerTitle.setTextSize(22);
        headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        headerTitle.setTextColor(INK);
        row.addView(headerTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        headerSettings = new SettingsIconButton(this);
        headerSettings.setContentDescription("设置");
        headerSettings.setOnClickListener(v -> requestSettingsPage());
        row.addView(headerSettings, new LinearLayout.LayoutParams(dp(40), dp(40)));

        return row;
    }

    private LinearLayout tabs() {
        LinearLayout row = new LinearLayout(this);
        tabBar = row;
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(borderedBackground(CARD, LINE, dp(12)));
        row.setPadding(dp(3), dp(3), dp(3), dp(3));

        tabWrite = tabButton("今天", TAB_WRITE);
        tabCalendar = tabButton("日历", TAB_CALENDAR);
        tabTags = tabButton("标签", TAB_TAGS);

        row.addView(tabWrite, tabLayout());
        row.addView(tabCalendar, tabLayout());
        row.addView(tabTags, tabLayout());
        return row;
    }

    private TextView tabButton(String text, int tab) {
        TextView tabView = new TextView(this);
        tabView.setText(text);
        tabView.setTextSize(13);
        tabView.setTypeface(Typeface.DEFAULT_BOLD);
        tabView.setGravity(Gravity.CENTER);
        tabView.setOnClickListener(v -> requestSwitchTab(tab));
        return tabView;
    }

    private void requestSwitchTab(int tab) {
        if (tab == currentTab && !inDetail) {
            return;
        }
        if (!inDetail && currentTab == TAB_WRITE && hasUnsavedDraft()) {
            confirmDiscardDraft(() -> {
                clearEditorOnly();
                switchTab(tab);
            });
            return;
        }
        switchTab(tab);
    }

    private void requestSettingsPage() {
        if (!inDetail && currentTab == TAB_WRITE && hasUnsavedDraft()) {
            confirmDiscardDraft(() -> {
                clearEditorOnly();
                showSettingsPage();
            });
            return;
        }
        showSettingsPage();
    }

    private void showSettingsPage() {
        settingsReturnTab = inDetail ? detailReturnTab : currentTab;
        inDetail = false;
        inSettings = true;
        hideKeyboard();
        pageHost.removeAllViews();
        if (tabBar != null) {
            tabBar.setVisibility(View.GONE);
        }
        if (headerBack != null) {
            headerBack.setVisibility(View.VISIBLE);
        }
        if (headerTitle != null) {
            headerTitle.setText("设置");
        }
        if (headerSettings != null) {
            headerSettings.setVisibility(View.GONE);
        }
        buildSettingsPage();
    }

    private void handleHeaderBack() {
        if (inSettings) {
            switchTab(settingsReturnTab);
            return;
        }
        if (inDetail) {
            switchTab(detailReturnTab);
        }
    }

    private void switchTab(int tab) {
        currentTab = tab;
        inDetail = false;
        inSettings = false;
        if (tabBar != null) {
            tabBar.setVisibility(View.VISIBLE);
        }
        if (headerBack != null) {
            headerBack.setVisibility(View.GONE);
        }
        if (headerTitle != null) {
            headerTitle.setText("一页日记");
        }
        if (headerSettings != null) {
            headerSettings.setVisibility(View.VISIBLE);
        }
        pageHost.removeAllViews();
        updateTabs();
        updateEditState();

        if (tab == TAB_WRITE) {
            buildWritePage();
            focusWriter();
        } else if (tab == TAB_CALENDAR) {
            buildCalendarPage();
        } else {
            buildTagPage();
        }
    }

    private void updateTabs() {
        if (tabWrite == null) {
            return;
        }
        styleTab(tabWrite, currentTab == TAB_WRITE);
        styleTab(tabCalendar, currentTab == TAB_CALENDAR);
        styleTab(tabTags, currentTab == TAB_TAGS);
    }

    private void styleTab(TextView tabView, boolean selected) {
        tabView.setTextColor(selected ? CARD : INK);
        tabView.setBackground(selected ? solidBackground(ACCENT, dp(10)) : transparentBackground());
        tabView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void buildWritePage() {
        detach(bodyInput);
        if (editingId == 0L && bodyInput.getText().toString().trim().isEmpty() && allEntries().isEmpty()) {
            TextView empty = sectionTitle("写下今天。");
            empty.setTextSize(15);
            empty.setTextColor(MUTED);
            empty.setPadding(0, dp(2), 0, dp(8));
            pageHost.addView(empty, matchWrap());
        }
        pageHost.addView(bodyInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        writerTagHints = new LinearLayout(this);
        writerTagHints.setOrientation(LinearLayout.VERTICAL);
        writerTagHints.setPadding(0, dp(8), 0, 0);
        pageHost.addView(writerTagHints, matchWrap());
        renderWriterTagHints();

        writerImagePreview = null;

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, 0);
        pageHost.addView(actions, matchWrap());

        actions.addView(lightIconButton(android.R.drawable.ic_menu_gallery, "插入图片", v -> pickImage()), fixedActionLayout(52));
        clearButton = lightIconButton(android.R.drawable.ic_menu_close_clear_cancel, editingId == 0L ? "清空" : "取消编辑", v -> clearEditor());
        actions.addView(clearButton, fixedActionLayout(52));
        saveButton = darkButton(editingId == 0L ? "保存" : "更新", v -> saveEntry());
        actions.addView(saveButton, actionLayout(1));
        updateEditState();
    }

    private void buildCalendarPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());
        pageHost.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        content.addView(calendarHeader(), matchWrap());
        content.addView(authorFilterChips(() -> {
            calendarListLimit = PAGE_SIZE;
            switchTab(TAB_CALENDAR);
        }), matchWrap());

        calendarGrid = new LinearLayout(this);
        calendarGrid.setOrientation(LinearLayout.VERTICAL);
        content.addView(calendarGrid, matchWrap());

        TextView title = sectionTitle("当天日记");
        title.setPadding(0, dp(14), 0, dp(8));
        content.addView(title, matchWrap());

        calendarEntries = new LinearLayout(this);
        calendarEntries.setOrientation(LinearLayout.VERTICAL);
        content.addView(calendarEntries, matchWrap());

        renderCalendar();
        renderCalendarEntries();
    }

    private LinearLayout calendarHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(8));

        header.addView(lightButton("‹", v -> moveMonth(-1)), squareButtonLayout());

        monthTitle = new TextView(this);
        monthTitle.setTextSize(16);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setTextColor(INK);
        monthTitle.setGravity(Gravity.CENTER);
        header.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(42), 1));

        header.addView(lightButton("全部", v -> {
            selectedDayStart = -1L;
            calendarListLimit = PAGE_SIZE;
            renderCalendar();
            renderCalendarEntries();
        }), actionLayout(0.9f));
        header.addView(lightButton("›", v -> moveMonth(1)), squareButtonLayout());

        return header;
    }

    private void buildTagPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());
        pageHost.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        content.addView(sectionTitle("筛选"), matchWrap());
        content.addView(authorFilterChips(() -> {
            clearUnavailableSelectedTag();
            tagListLimit = PAGE_SIZE;
            switchTab(TAB_TAGS);
        }), matchWrap());
        tagFilters = new LinearLayout(this);
        tagFilters.setOrientation(LinearLayout.VERTICAL);
        content.addView(tagFilters, matchWrap());

        tagCount = sectionTitle("全部日记");
        tagCount.setPadding(0, dp(12), 0, dp(8));
        content.addView(tagCount, matchWrap());

        tagResults = new LinearLayout(this);
        tagResults.setOrientation(LinearLayout.VERTICAL);
        content.addView(tagResults, matchWrap());

        renderTagContent();
    }

    private void buildSettingsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());
        pageHost.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        content.addView(sectionTitle("写作者"), matchWrap());
        EditText authorInput = new EditText(this);
        authorInput.setSingleLine(true);
        authorInput.setText(currentAuthor());
        authorInput.setHint(DEFAULT_AUTHOR);
        authorInput.setTextSize(14);
        authorInput.setTextColor(INK);
        authorInput.setHintTextColor(Color.rgb(151, 151, 141));
        authorInput.setPadding(dp(12), 0, dp(12), 0);
        authorInput.setBackground(editorBackground());
        authorInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                preferences.edit().putString(CURRENT_AUTHOR_KEY, cleanAuthor(s.toString())).apply();
            }
        });
        content.addView(authorInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        ));
        TextView authorHint = sectionTitle("新日记会默认记到这个名字下，旧日记默认是小妈妈。");
        authorHint.setTypeface(Typeface.DEFAULT);
        authorHint.setPadding(0, dp(6), 0, dp(12));
        content.addView(authorHint, matchWrap());

        content.addView(sectionTitle("电脑服务"), matchWrap());
        EditText syncUrl = new EditText(this);
        syncUrl.setSingleLine(true);
        syncUrl.setText(preferences.getString(SYNC_URL_KEY, ""));
        syncUrl.setHint("自动探测或输入 http://电脑IP:8788");
        syncUrl.setTextSize(14);
        syncUrl.setTextColor(INK);
        syncUrl.setHintTextColor(Color.rgb(151, 151, 141));
        syncUrl.setPadding(dp(12), 0, dp(12), 0);
        syncUrl.setBackground(editorBackground());
        syncUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                preferences.edit().putString(SYNC_URL_KEY, syncUrl.getText().toString().trim()).apply();
            }
        });
        content.addView(syncUrl, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        Button detectButton = lightButton("自动探测电脑服务", v -> detectSyncService(syncUrl));
        LinearLayout.LayoutParams detectParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        detectParams.setMargins(0, dp(8), 0, 0);
        content.addView(detectButton, detectParams);

        LinearLayout lan = new LinearLayout(this);
        lan.setOrientation(LinearLayout.HORIZONTAL);
        lan.setPadding(0, dp(8), 0, dp(10));
        lan.addView(darkButton("同步", v -> syncWithService(syncUrl.getText().toString())), actionLayout(1));
        content.addView(lan, matchWrap());

        content.addView(sectionTitle("数据管理"), matchWrap());
        content.addView(settingsCard(
                "本机日记与附件",
                "日记正文、标签、图片附件保存在当前手机。",
                "可通过导出、备份 ZIP 和导入能力迁移数据；同步只在你手动点击时执行。"
        ), cardLayout());

        content.addView(sectionTitle("关于"), matchWrap());
        content.addView(settingsCard(
                "一页日记",
                "本地优先日记，支持图片、标签、Web 客户端和手机同步。",
                appVersionText()
        ), cardLayout());
    }

    private void saveEntry() {
        String body = bodyInput.getText().toString().trim();
        if (body.isEmpty()) {
            toast("还没有内容");
            return;
        }

        long now = System.currentTimeMillis();
        long id = editingId == 0L ? now : editingId;
        boolean updated = editingId != 0L && hasEntry(id);

        try {
            String author = editingId == 0L ? currentAuthor() : authorForEntry(id);
            saveEntryToDatabase(id, id, now, body, author);
            selectedDayStart = startOfDay(editingId == 0L ? now : editingId);
            visibleMonth.setTimeInMillis(selectedDayStart);
            clearEditorOnly();
            JSONObject saved = entryById(id);
            if (saved != null) {
                showReadOnlyEntry(saved);
            }
            toast(updated ? "已更新" : "已保存");
        } catch (Exception e) {
            toast("保存失败");
        }
    }

    private void handleIncomingShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (sharedText == null || sharedText.toString().trim().isEmpty()) {
            toast("分享内容为空");
            return;
        }
        String body = buildSharedDiaryBody(
                sharedText.toString(),
                intent.getStringExtra(Intent.EXTRA_SUBJECT)
        );
        long now = System.currentTimeMillis();
        long id = now;
        while (entryById(id, true) != null) {
            id++;
        }
        try {
            saveEntryToDatabase(id, id, now, body, currentAuthor());
            selectedDayStart = startOfDay(id);
            visibleMonth.setTimeInMillis(selectedDayStart);
            JSONObject saved = entryById(id);
            if (saved != null) {
                showReadOnlyEntry(saved);
            }
            toast("已从分享保存");
        } catch (Exception e) {
            toast("分享保存失败");
        }
        intent.setAction(null);
    }

    private String buildSharedDiaryBody(String text, String subject) {
        String body = text == null ? "" : text.trim();
        String cleanSubject = subject == null ? "" : subject.trim();
        if (!cleanSubject.isEmpty() && !body.contains(cleanSubject)) {
            body = cleanSubject + "\n\n" + body;
        }
        if (extractTags(body).contains("分享")) {
            return body;
        }
        return body + "\n\n#分享";
    }

    private JSONObject buildEntry(long id, String body, String createdAt) throws JSONException {
        JSONObject entry = new JSONObject();
        entry.put("id", id);
        entry.put("createdAt", createdAt == null ? isoDate.format(new Date(id)) : createdAt);
        entry.put("updatedAt", isoDate.format(new Date(System.currentTimeMillis())));
        entry.put("author", DEFAULT_AUTHOR);
        entry.put("tags", new JSONArray(extractTags(body)));
        entry.put("body", body);
        return entry;
    }

    private JSONObject normalizeEntry(JSONObject entry) throws JSONException {
        JSONObject next = new JSONObject(entry.toString());
        next.put("author", cleanAuthor(entry.optString("author", DEFAULT_AUTHOR)));
        next.put("tags", new JSONArray(entryTags(entry)));
        return next;
    }

    private void exportFile(boolean json) {
        String filename = "one-page-diary-all-" + fileDate.format(new Date()) + (json ? ".json" : ".md");
        pendingExportText = json ? buildJsonExport() : buildMarkdown();
        pendingZipExport = false;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(json ? "application/json" : "text/markdown");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try {
            startActivityForResult(intent, json ? REQUEST_EXPORT_JSON : REQUEST_EXPORT);
        } catch (ActivityNotFoundException e) {
            toast("当前手机不支持文件导出");
        }
    }

    private void exportZip() {
        String filename = "one-page-diary-backup-" + fileDate.format(new Date()) + ".zip";
        pendingExportText = null;
        pendingZipExport = true;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try {
            startActivityForResult(intent, REQUEST_EXPORT_ZIP);
        } catch (ActivityNotFoundException e) {
            toast("当前手机不支持文件导出");
        }
    }

    private void importZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_ZIP);
        } catch (ActivityNotFoundException e) {
            toast("当前手机不支持文件导入");
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (ActivityNotFoundException e) {
            toast("当前手机不支持选择图片");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                insertPickedImage(data.getData());
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_ZIP) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importZipFromUri(data.getData());
            }
            return;
        }
        if ((requestCode != REQUEST_EXPORT && requestCode != REQUEST_EXPORT_JSON && requestCode != REQUEST_EXPORT_ZIP)
                || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null || (!pendingZipExport && pendingExportText == null)) {
                toast("导出失败");
                return;
            }
            if (requestCode == REQUEST_EXPORT_ZIP) {
                writeZipBackup(output);
            } else {
                output.write(pendingExportText.getBytes(StandardCharsets.UTF_8));
            }
            output.flush();
            toast(requestCode == REQUEST_EXPORT_ZIP ? "已备份 ZIP" : "已批量导出");
        } catch (Exception e) {
            toast("导出失败");
        }
    }

    private void insertPickedImage(Uri uri) {
        File dir = attachmentsDir();
        if (!dir.exists() && !dir.mkdirs()) {
            toast("图片保存失败");
            return;
        }

        String filename = "img-" + fileDate.format(new Date()) + "-" + System.currentTimeMillis() + "." + imageExtension(uri);
        File target = new File(dir, filename);
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) {
                toast("图片读取失败");
                return;
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
            insertAttachmentToken(filename);
            toast("已插入图片");
        } catch (Exception e) {
            toast("图片保存失败");
        }
    }

    private void importZipFromUri(Uri uri) {
        try (InputStream rawInput = getContentResolver().openInputStream(uri)) {
            if (rawInput == null) {
                toast("导入失败");
                return;
            }
            ImportResult result = importZipFromStream(rawInput);
            switchTab(TAB_TAGS);
            toast("已导入 " + result.entryCount + " 篇，图片 " + result.attachmentCount + " 张");
        } catch (Exception e) {
            toast("导入失败");
        }
    }

    private int importDiaryJson(String diaryJson) throws JSONException {
        if (diaryJson == null || diaryJson.trim().isEmpty()) {
            return 0;
        }
        JSONArray entries;
        String trimmed = diaryJson.trim();
        if (trimmed.startsWith("{")) {
            entries = new JSONObject(trimmed).optJSONArray("entries");
        } else {
            entries = new JSONArray(trimmed);
        }
        if (entries == null) {
            return 0;
        }

        int count = 0;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            if (mergeImportedEntry(entry)) {
                count++;
            }
        }
        return count;
    }

    private boolean mergeImportedEntry(JSONObject remote) throws JSONException {
        long id = remote.optLong("id", System.currentTimeMillis());
        String body = remote.optString("body", "");
        String author = cleanAuthor(remote.optString("author", DEFAULT_AUTHOR));
        long createdAt = parseImportedTime(remote.opt("createdAt"), id);
        long updatedAt = parseImportedTime(remote.opt("updatedAt"), createdAt);
        long deletedAt = parseImportedTime(remote.opt("deletedAt"), 0L);
        int remoteVersion = Math.max(1, remote.optInt("version", 1));
        String remoteDeviceId = remote.optString("deviceId", "");

        JSONObject local = entryById(id, true);
        if (local == null) {
            insertImportedEntry(id, createdAt, updatedAt, deletedAt, remoteVersion, remoteDeviceId, author, body);
            return true;
        }

        int localVersion = Math.max(1, local.optInt("version", 1));
        String localBody = local.optString("body", "");
        long localDeletedAt = parseImportedTime(local.opt("deletedAt"), 0L);
        boolean localDeleted = localDeletedAt > 0L;
        boolean remoteDeleted = deletedAt > 0L;

        if (localBody.equals(body) && localDeleted == remoteDeleted) {
            if (remoteVersion > localVersion) {
                insertImportedEntry(id, createdAt, updatedAt, deletedAt, remoteVersion, remoteDeviceId, author, body);
                return true;
            }
            return false;
        }

        if (remoteVersion > localVersion) {
            insertImportedEntry(id, createdAt, updatedAt, deletedAt, remoteVersion, remoteDeviceId, author, body);
            return true;
        }
        if (remoteVersion < localVersion) {
            return false;
        }

        if (!remoteDeleted && !localDeleted) {
            insertConflictEntry(remote, body, createdAt, updatedAt, remoteVersion, remoteDeviceId, author);
            return true;
        }

        if (remoteDeleted && !localDeleted) {
            return false;
        }
        if (!remoteDeleted) {
            insertConflictEntry(remote, body, createdAt, updatedAt, remoteVersion, remoteDeviceId, author);
            return true;
        }
        return false;
    }

    private void insertImportedEntry(long id, long createdAt, long updatedAt, long deletedAt, int version, String importedDeviceId, String author, String body) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("created_at", createdAt);
            values.put("updated_at", updatedAt);
            values.put("deleted_at", deletedAt);
            values.put("version", version);
            values.put("device_id", importedDeviceId == null || importedDeviceId.isEmpty() ? deviceId : importedDeviceId);
            values.put("author", cleanAuthor(author));
            values.put("body", body == null ? "" : body);
            db.insertWithOnConflict(DiaryDatabase.ENTRIES_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            db.delete(DiaryDatabase.TAGS_TABLE, "entry_id = ?", new String[]{String.valueOf(id)});
            db.delete(DiaryDatabase.ATTACHMENTS_TABLE, "entry_id = ?", new String[]{String.valueOf(id)});
            if (deletedAt <= 0L) {
                for (String tag : extractTags(body)) {
                    ContentValues tagValues = new ContentValues();
                    tagValues.put("tag", tag);
                    tagValues.put("entry_id", id);
                    db.insertWithOnConflict(DiaryDatabase.TAGS_TABLE, null, tagValues, SQLiteDatabase.CONFLICT_IGNORE);
                }
                for (String filename : extractAttachmentNames(body)) {
                    ContentValues attachmentValues = new ContentValues();
                    attachmentValues.put("entry_id", id);
                    attachmentValues.put("filename", filename);
                    attachmentValues.put("created_at", updatedAt);
                    db.insertWithOnConflict(DiaryDatabase.ATTACHMENTS_TABLE, null, attachmentValues, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void insertConflictEntry(JSONObject remote, String body, long createdAt, long updatedAt, int remoteVersion, String remoteDeviceId, String author) {
        long id = System.currentTimeMillis();
        while (entryById(id, true) != null) {
            id++;
        }
        String conflictBody = "#冲突\n来自另一端的版本\n\n" + body;
        insertImportedEntry(id, createdAt, updatedAt, 0L, remoteVersion + 1, remoteDeviceId, author, conflictBody);
    }

    private void syncWithService(String baseUrl) {
        toast("开始同步");
        new Thread(() -> {
            try {
                String url = resolveSyncService(baseUrl);
                preferences.edit().putString(SYNC_URL_KEY, url).apply();
                Map<String, JSONObject> remoteManifest = fetchRemoteManifest(url);
                List<JSONObject> localEntries = allEntriesForExport();
                Map<String, JSONObject> localManifest = localManifest(localEntries);
                Map<String, JSONObject> localById = entriesById(localEntries);

                List<String> downloadIds = new ArrayList<>();
                JSONArray uploadEntries = new JSONArray();

                for (Map.Entry<String, JSONObject> remote : remoteManifest.entrySet()) {
                    JSONObject local = localManifest.get(remote.getKey());
                    if (local == null || shouldDownloadRemote(local, remote.getValue())) {
                        downloadIds.add(remote.getKey());
                    }
                }

                for (Map.Entry<String, JSONObject> local : localManifest.entrySet()) {
                    JSONObject remote = remoteManifest.get(local.getKey());
                    if (remote == null || shouldUploadLocal(local.getValue(), remote)) {
                        uploadEntries.put(localById.get(local.getKey()));
                    }
                }

                ImportResult pullResult = downloadIds.isEmpty() ? new ImportResult(0, 0) : pullRemoteEntries(url, downloadIds);
                int pushed = uploadEntries.length();
                if (pushed > 0) {
                    pushLocalEntries(url, uploadEntries);
                }

                preferences.edit()
                        .putString(LAST_SYNC_REMOTE_UPDATED_AT_KEY, String.valueOf(System.currentTimeMillis()))
                        .putString(LAST_SYNC_LOCAL_SIGNATURE_KEY, String.valueOf(System.currentTimeMillis()))
                        .apply();

                ImportResult finalPullResult = pullResult;
                int finalPushed = pushed;
                runOnUiThread(() -> {
                    if (finalPullResult.entryCount == 0 && finalPushed == 0) {
                        toast("已是最新");
                    } else {
                        toast("同步完成 · 拉取 " + finalPullResult.entryCount + " 篇，提交 " + finalPushed + " 篇");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("同步失败：" + friendlyError(e)));
            }
        }).start();
    }

    private String resolveSyncService(String baseUrl) {
        String current = normalizeSyncUrl(baseUrl);
        if (!current.isEmpty() && probeSyncService(current) != null) {
            return current;
        }
        String found = findSyncService(current);
        if (found == null) {
            throw new IllegalStateException("没有找到电脑服务");
        }
        return found;
    }

    private Map<String, JSONObject> localManifest(List<JSONObject> entries) throws Exception {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (JSONObject entry : entries) {
            JSONObject manifest = new JSONObject();
            String id = String.valueOf(entry.optLong("id"));
            manifest.put("id", id);
            manifest.put("version", Math.max(1, entry.optInt("version", 1)));
            manifest.put("deletedAt", entry.opt("deletedAt"));
            manifest.put("author", entryAuthor(entry));
            manifest.put("bodyHash", sha256(entry.optString("body", "")));
            manifest.put("attachments", entry.optJSONArray("attachments") == null ? new JSONArray() : entry.optJSONArray("attachments"));
            result.put(id, manifest);
        }
        return result;
    }

    private Map<String, JSONObject> entriesById(List<JSONObject> entries) {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (JSONObject entry : entries) {
            result.put(String.valueOf(entry.optLong("id")), entry);
        }
        return result;
    }

    private boolean shouldDownloadRemote(JSONObject local, JSONObject remote) {
        int localVersion = Math.max(1, local.optInt("version", 1));
        int remoteVersion = Math.max(1, remote.optInt("version", 1));
        if (remoteVersion > localVersion) {
            return true;
        }
        return remoteVersion == localVersion
                && (!remote.optString("bodyHash", "").equals(local.optString("bodyHash", ""))
                || !sameDeletedState(local, remote)
                || !cleanAuthor(remote.optString("author", DEFAULT_AUTHOR)).equals(cleanAuthor(local.optString("author", DEFAULT_AUTHOR))));
    }

    private boolean shouldUploadLocal(JSONObject local, JSONObject remote) {
        int localVersion = Math.max(1, local.optInt("version", 1));
        int remoteVersion = Math.max(1, remote.optInt("version", 1));
        if (localVersion > remoteVersion) {
            return true;
        }
        return localVersion == remoteVersion
                && !cleanAuthor(local.optString("author", DEFAULT_AUTHOR)).equals(cleanAuthor(remote.optString("author", DEFAULT_AUTHOR)));
    }

    private boolean sameDeletedState(JSONObject left, JSONObject right) {
        return !left.isNull("deletedAt") == !right.isNull("deletedAt");
    }

    private Map<String, JSONObject> fetchRemoteManifest(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url + "/api/manifest").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("元数据失败 " + code);
            }
            JSONObject body = new JSONObject(readResponseBody(connection, 1024 * 1024));
            if (!"one-page-diary".equals(body.optString("app", ""))) {
                throw new IllegalStateException("不是一页日记服务");
            }
            Map<String, JSONObject> result = new LinkedHashMap<>();
            JSONArray entries = body.optJSONArray("entries");
            if (entries != null) {
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.optJSONObject(i);
                    if (entry != null) {
                        result.put(entry.optString("id", String.valueOf(entry.optLong("id"))), entry);
                    }
                }
            }
            return result;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ImportResult pullRemoteEntries(String url, List<String> ids) throws Exception {
        JSONObject payload = new JSONObject();
        JSONArray idArray = new JSONArray();
        for (String id : ids) {
            idArray.put(id);
        }
        payload.put("ids", idArray);
        JSONObject response = postJson(url + "/api/entries/query", payload);
        saveAttachmentPayload(response.optJSONObject("attachments"));
        JSONArray entries = response.optJSONArray("entries");
        int changed = 0;
        if (entries != null) {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry != null && mergeImportedEntry(entry)) {
                    changed++;
                }
            }
        }
        return new ImportResult(changed, response.optJSONObject("attachments") == null ? 0 : response.optJSONObject("attachments").length());
    }

    private void pushLocalEntries(String url, JSONArray entries) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("entries", entries);
        payload.put("attachments", attachmentPayloadForEntries(entries));
        postJson(url + "/api/entries", payload);
    }

    private JSONObject attachmentPayloadForEntries(JSONArray entries) throws Exception {
        JSONObject attachments = new JSONObject();
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            JSONArray entryAttachments = entry == null ? null : entry.optJSONArray("attachments");
            if (entryAttachments == null) {
                continue;
            }
            for (int j = 0; j < entryAttachments.length(); j++) {
                String name = entryAttachments.optString(j, "");
                if (isSafeAttachmentName(name)) {
                    names.add(name);
                }
            }
        }
        for (String name : names) {
            File file = attachmentFile(name);
            if (file.exists() && file.isFile()) {
                attachments.put(name, Base64.encodeToString(readFileBytes(file), Base64.NO_WRAP));
            }
        }
        return attachments;
    }

    private void saveAttachmentPayload(JSONObject attachments) throws Exception {
        if (attachments == null) {
            return;
        }
        JSONArray names = attachments.names();
        if (names == null) {
            return;
        }
        File dir = attachmentsDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("attachments dir");
        }
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, "");
            if (isSafeAttachmentName(name)) {
                try (FileOutputStream output = new FileOutputStream(attachmentFile(name))) {
                    output.write(Base64.decode(attachments.optString(name, ""), Base64.DEFAULT));
                }
            }
        }
    }

    private JSONObject postJson(String url, JSONObject payload) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("请求失败 " + code);
            }
            return new JSONObject(readResponseBody(connection, 80 * 1024 * 1024));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponseBody(HttpURLConnection connection, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream input = connection.getInputStream()) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maxBytes) {
                    throw new IllegalStateException("响应过大");
                }
                output.write(buffer, 0, count);
            }
        }
        return output.toString("UTF-8");
    }

    private byte[] readFileBytes(File file) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }

    private String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return builder.toString();
    }

    private String friendlyError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "请检查电脑服务地址";
        }
        if (message.length() > 30) {
            return message.substring(0, 30);
        }
        return message;
    }

    private String uploadLatestZip(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url + "/api/latest").openConnection();
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/zip");
            try (OutputStream output = connection.getOutputStream()) {
                writeZipBackup(output);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("上传失败 " + code);
            }
            String body = readResponseBody(connection, 4096);
            JSONObject result = body.trim().isEmpty() ? new JSONObject() : new JSONObject(body);
            return result.optString("updatedAt", "");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private RemoteStatus fetchRemoteStatus(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url + "/api/status").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(6000);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("服务不可用 " + code);
            }
            JSONObject status = new JSONObject(readResponseBody(connection, 16 * 1024));
            RemoteStatus remoteStatus = diaryRemoteStatus(status);
            if (remoteStatus == null) {
                throw new IllegalStateException("不是一页日记服务");
            }
            return remoteStatus;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ImportResult downloadAndMerge(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url + "/api/latest").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);
            int code = connection.getResponseCode();
            if (code == 404) {
                return new ImportResult(0, 0);
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("download " + code);
            }
            try (InputStream input = connection.getInputStream()) {
                return importZipFromStream(input);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void syncDownload(String baseUrl) {
        String url = normalizeSyncUrl(baseUrl);
        preferences.edit().putString(SYNC_URL_KEY, url).apply();
        toast("开始下载");
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                ImportResult result = downloadAndMerge(url);
                ImportResult finalResult = result;
                runOnUiThread(() -> {
                    switchTab(TAB_TAGS);
                    toast("已下载 " + finalResult.entryCount + " 篇，图片 " + finalResult.attachmentCount + " 张");
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("下载失败"));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast("当前手机无法打开链接");
        }
    }

    private void detectSyncService(EditText syncUrl) {
        String current = syncUrl.getText().toString();
        toast("开始探测");
        new Thread(() -> {
            String found = findSyncService(current);
            runOnUiThread(() -> {
                if (found == null) {
                    toast("没有找到电脑服务");
                    return;
                }
                syncUrl.setText(found);
                preferences.edit().putString(SYNC_URL_KEY, found).apply();
                toast("已找到 " + found);
            });
        }).start();
    }

    private String findSyncService(String currentUrl) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String current = normalizeSyncUrl(currentUrl);
        if (!current.isEmpty()) {
            candidates.add(current);
        }
        candidates.add("http://10.0.2.2:" + SYNC_PORT);
        candidates.add("http://10.0.3.2:" + SYNC_PORT);

        for (String candidate : candidates) {
            String found = probeSyncService(candidate);
            if (found != null) {
                return found;
            }
        }

        String discovered = discoverSyncServiceByUdp();
        if (discovered != null) {
            return discovered;
        }

        for (String prefix : localIpv4Prefixes()) {
            for (int i = 1; i <= 254; i++) {
                candidates.add("http://" + prefix + "." + i + ":" + SYNC_PORT);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CompletionService<String> completion = new ExecutorCompletionService<>(pool);
        int submitted = 0;
        for (String candidate : candidates) {
            completion.submit((Callable<String>) () -> probeSyncService(candidate));
            submitted++;
        }
        long deadline = System.currentTimeMillis() + 9000L;
        try {
            for (int received = 0; received < submitted && System.currentTimeMillis() < deadline; received++) {
                Future<String> future = completion.poll(500, TimeUnit.MILLISECONDS);
                if (future == null) {
                    received--;
                    continue;
                }
                try {
                    String found = future.get();
                    if (found != null) {
                        return found;
                    }
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

    private String discoverSyncServiceByUdp() {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        targets.add("255.255.255.255");
        for (String prefix : localIpv4Prefixes()) {
            targets.add(prefix + ".255");
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(700);
            byte[] payload = DISCOVERY_MESSAGE.getBytes(StandardCharsets.UTF_8);
            for (String target : targets) {
                try {
                    DatagramPacket packet = new DatagramPacket(
                            payload,
                            payload.length,
                            InetAddress.getByName(target),
                            SYNC_PORT
                    );
                    socket.send(packet);
                } catch (Exception ignored) {
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
                    String app = json.optString("app", "");
                    if (!"one-page-diary".equals(app) && !"aitools".equals(app)) {
                        continue;
                    }
                    LinkedHashSet<String> responseCandidates = new LinkedHashSet<>();
                    addSyncCandidate(responseCandidates, json.optString("url", ""));
                    JSONArray urls = json.optJSONArray("urls");
                    if (urls != null) {
                        for (int i = 0; i < urls.length(); i++) {
                            addSyncCandidate(responseCandidates, urls.optString(i, ""));
                        }
                    }
                    addSyncCandidate(responseCandidates, "http://" + response.getAddress().getHostAddress() + ":" + SYNC_PORT);
                    for (String candidate : responseCandidates) {
                        String found = probeSyncService(candidate);
                        if (found != null) {
                            return found;
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<String> localIpv4Prefixes() {
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    String host = address.getHostAddress();
                    int dot = host.lastIndexOf('.');
                    if (dot > 0 && isPrivateIpv4(host)) {
                        prefixes.add(host.substring(0, dot));
                    }
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

    private String probeSyncService(String baseUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(baseUrl + "/api/status").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(900);
            connection.setReadTimeout(900);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            String body = readResponseBody(connection, 16 * 1024);
            return isDiaryServiceStatus(body) ? baseUrl : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isDiaryServiceStatus(String body) {
        try {
            JSONObject status = new JSONObject(body);
            return diaryRemoteStatus(status) != null;
        } catch (Exception ignored) {
        }
        return false;
    }

    private RemoteStatus diaryRemoteStatus(JSONObject status) {
        if ("one-page-diary".equals(status.optString("app", ""))) {
            return new RemoteStatus(status.optBoolean("hasLatest", false), status.optString("updatedAt", ""));
        }
        if (!"aitools".equals(status.optString("app", ""))) {
            return null;
        }
        JSONArray tools = status.optJSONArray("tools");
        if (tools == null) {
            return null;
        }
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null) {
                continue;
            }
            if ("diary".equals(tool.optString("id", "")) || "one-page-diary".equals(tool.optString("app", ""))) {
                JSONObject sync = tool.optJSONObject("sync");
                if (sync == null || !sync.optBoolean("enabled", false)) {
                    return null;
                }
                return new RemoteStatus(sync.optBoolean("hasLatest", false), sync.optString("updatedAt", ""));
            }
        }
        return null;
    }

    private void addSyncCandidate(LinkedHashSet<String> candidates, String raw) {
        String normalized = normalizeSyncUrl(raw);
        if (!normalized.isEmpty()) {
            candidates.add(normalized);
        }
    }

    private String normalizeSyncUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            return "";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        try {
            URL parsed = new URL(url);
            StringBuilder base = new StringBuilder();
            base.append(parsed.getProtocol()).append("://").append(parsed.getHost());
            if (parsed.getPort() > 0) {
                base.append(":").append(parsed.getPort());
            } else {
                base.append(":").append(SYNC_PORT);
            }
            return base.toString();
        } catch (Exception ignored) {
        }
        return url;
    }

    private ImportResult importZipFromStream(InputStream rawInput) throws Exception {
        File dir = attachmentsDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("attachments dir");
        }

        String diaryJson = null;
        int attachmentCount = 0;
        try (ZipInputStream zip = new ZipInputStream(rawInput)) {
            ZipEntry zipEntry;
            byte[] buffer = new byte[8192];
            while ((zipEntry = zip.getNextEntry()) != null) {
                String name = zipEntry.getName();
                if ("diary.json".equals(name)) {
                    ByteArrayOutputStream text = new ByteArrayOutputStream();
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        text.write(buffer, 0, count);
                    }
                    diaryJson = text.toString("UTF-8");
                } else if (name.startsWith("attachments/")) {
                    String filename = name.substring("attachments/".length());
                    if (isSafeAttachmentName(filename)) {
                        try (FileOutputStream output = new FileOutputStream(attachmentFile(filename))) {
                            int count;
                            while ((count = zip.read(buffer)) != -1) {
                                output.write(buffer, 0, count);
                            }
                            attachmentCount++;
                        }
                    }
                }
                zip.closeEntry();
            }
        }
        return new ImportResult(importDiaryJson(diaryJson), attachmentCount);
    }

    private long parseImportedTime(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            try {
                return Long.parseLong(text);
            } catch (Exception ignored) {
            }
            try {
                Date parsed = isoDate.parse(text);
                return parsed == null ? fallback : parsed.getTime();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private void insertAttachmentToken(String filename) {
        Editable editable = bodyInput.getText();
        int cursor = bodyInput.getSelectionStart();
        if (cursor < 0) {
            cursor = editable.length();
        }
        String before = cursor == 0 || editable.charAt(cursor - 1) == '\n' ? "" : "\n";
        String after = cursor >= editable.length() || editable.charAt(cursor) == '\n' ? "" : "\n";
        String token = before + "![图片](attachment:" + filename + ")" + after;
        editable.insert(cursor, token);
        bodyInput.setSelection(Math.min(editable.length(), cursor + token.length()));
        styleAttachmentsInEditor(editable);
    }

    private String imageExtension(Uri uri) {
        String type = getContentResolver().getType(uri);
        if ("image/png".equals(type)) {
            return "png";
        }
        if ("image/webp".equals(type)) {
            return "webp";
        }
        if ("image/gif".equals(type)) {
            return "gif";
        }
        return "jpg";
    }

    private JSONArray readEntries() {
        JSONArray entries = new JSONArray();
        for (JSONObject entry : allEntries()) {
            entries.put(entry);
        }
        return entries;
    }

    private JSONArray readLegacyEntries() {
        String raw = preferences.getString(ENTRIES_KEY, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void migrateLegacyEntriesIfNeeded() {
        if (preferences.getBoolean(SQLITE_MIGRATED_KEY, false)) {
            return;
        }

        JSONArray legacy = readLegacyEntries();
        try {
            for (int i = legacy.length() - 1; i >= 0; i--) {
                JSONObject entry = legacy.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                long id = entry.optLong("id", 0L);
                String body = entry.optString("body", "").trim();
                if (id <= 0L || body.isEmpty()) {
                    continue;
                }
                saveEntryToDatabase(id, id, id, body, DEFAULT_AUTHOR);
            }
            preferences.edit().putBoolean(SQLITE_MIGRATED_KEY, true).apply();
        } catch (Exception ignored) {
        }
    }

    private void saveEntryToDatabase(long id, long createdAt, long updatedAt, String body, String author) {
        List<String> previousAttachments = attachmentNamesForEntry(id);
        Set<String> nextAttachments = new LinkedHashSet<>(extractAttachmentNames(body));
        int version = entryVersion(id) + 1;
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues entryValues = new ContentValues();
            entryValues.put("id", id);
            entryValues.put("created_at", createdAt);
            entryValues.put("updated_at", updatedAt);
            entryValues.put("deleted_at", 0L);
            entryValues.put("version", version);
            entryValues.put("device_id", deviceId);
            entryValues.put("author", cleanAuthor(author));
            entryValues.put("body", body);
            db.insertWithOnConflict(DiaryDatabase.ENTRIES_TABLE, null, entryValues, SQLiteDatabase.CONFLICT_REPLACE);

            db.delete(DiaryDatabase.TAGS_TABLE, "entry_id = ?", new String[]{String.valueOf(id)});
            for (String tag : extractTags(body)) {
                ContentValues tagValues = new ContentValues();
                tagValues.put("tag", tag);
                tagValues.put("entry_id", id);
                db.insertWithOnConflict(DiaryDatabase.TAGS_TABLE, null, tagValues, SQLiteDatabase.CONFLICT_IGNORE);
            }

            db.delete(DiaryDatabase.ATTACHMENTS_TABLE, "entry_id = ?", new String[]{String.valueOf(id)});
            for (String filename : nextAttachments) {
                ContentValues attachmentValues = new ContentValues();
                attachmentValues.put("entry_id", id);
                attachmentValues.put("filename", filename);
                attachmentValues.put("created_at", updatedAt);
                db.insertWithOnConflict(DiaryDatabase.ATTACHMENTS_TABLE, null, attachmentValues, SQLiteDatabase.CONFLICT_IGNORE);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        for (String filename : previousAttachments) {
            if (!nextAttachments.contains(filename) && !isAttachmentReferenced(filename)) {
                deleteAttachmentFile(filename);
            }
        }
    }

    private boolean hasEntry(long id) {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ENTRIES_TABLE,
                new String[]{"id"},
                "id = ? AND deleted_at = 0",
                new String[]{String.valueOf(id)},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    private int entryVersion(long id) {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ENTRIES_TABLE,
                new String[]{"version"},
                "id = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow("version"));
            }
        }
        return 0;
    }

    private String authorForEntry(long id) {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ENTRIES_TABLE,
                new String[]{"author"},
                "id = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return cleanAuthor(cursor.getString(cursor.getColumnIndexOrThrow("author")));
            }
        }
        return currentAuthor();
    }

    private JSONObject entryById(long id) {
        return entryById(id, false);
    }

    private JSONObject entryById(long id, boolean includeDeleted) {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ENTRIES_TABLE,
                null,
                includeDeleted ? "id = ?" : "id = ? AND deleted_at = 0",
                new String[]{String.valueOf(id)},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return entryFromCursor(cursor);
            }
        } catch (JSONException ignored) {
        }
        return null;
    }

    private JSONObject entryFromCursor(Cursor cursor) throws JSONException {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        long updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        long deletedAt = cursor.getLong(cursor.getColumnIndexOrThrow("deleted_at"));
        int version = cursor.getInt(cursor.getColumnIndexOrThrow("version"));
        String storedDeviceId = cursor.getString(cursor.getColumnIndexOrThrow("device_id"));
        String author = cursor.getString(cursor.getColumnIndexOrThrow("author"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));

        JSONObject entry = new JSONObject();
        entry.put("id", id);
        entry.put("createdAt", isoDate.format(new Date(createdAt)));
        entry.put("updatedAt", isoDate.format(new Date(updatedAt)));
        entry.put("deletedAt", deletedAt <= 0L ? JSONObject.NULL : deletedAt);
        entry.put("version", version <= 0 ? 1 : version);
        entry.put("deviceId", storedDeviceId == null || storedDeviceId.isEmpty() ? deviceId : storedDeviceId);
        entry.put("author", cleanAuthor(author));
        entry.put("tags", new JSONArray(tagsForEntry(id)));
        entry.put("attachments", new JSONArray(attachmentNamesForEntry(id)));
        entry.put("body", body == null ? "" : body);
        return entry;
    }

    private List<String> tagsForEntry(long entryId) {
        List<String> tags = new ArrayList<>();
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.TAGS_TABLE,
                new String[]{"tag"},
                "entry_id = ?",
                new String[]{String.valueOf(entryId)},
                null,
                null,
                "tag ASC"
        )) {
            while (cursor.moveToNext()) {
                tags.add(cursor.getString(cursor.getColumnIndexOrThrow("tag")));
            }
        }
        return tags;
    }

    private List<String> attachmentNamesForEntry(long entryId) {
        List<String> attachments = new ArrayList<>();
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ATTACHMENTS_TABLE,
                new String[]{"filename"},
                "entry_id = ?",
                new String[]{String.valueOf(entryId)},
                null,
                null,
                "created_at ASC, filename ASC"
        )) {
            while (cursor.moveToNext()) {
                attachments.add(cursor.getString(cursor.getColumnIndexOrThrow("filename")));
            }
        }
        return attachments;
    }

    private boolean isAttachmentReferenced(String filename) {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ATTACHMENTS_TABLE,
                new String[]{"filename"},
                "filename = ?",
                new String[]{filename},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    private boolean isAttachmentReferencedByOtherEntry(String filename, long entryId) {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ATTACHMENTS_TABLE,
                new String[]{"filename"},
                "filename = ? AND entry_id != ?",
                new String[]{filename, String.valueOf(entryId)},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    private List<String> extractAttachmentNames(String body) {
        Set<String> attachments = new LinkedHashSet<>();
        Matcher matcher = ATTACHMENT_PATTERN.matcher(body == null ? "" : body);
        while (matcher.find()) {
            String filename = matcher.group(1).trim();
            if (isSafeAttachmentName(filename)) {
                attachments.add(filename);
            }
        }
        return new ArrayList<>(attachments);
    }

    private boolean isSafeAttachmentName(String filename) {
        return filename != null
                && !filename.trim().isEmpty()
                && !filename.contains("/")
                && !filename.contains("\\")
                && !filename.contains("..");
    }

    private File attachmentsDir() {
        return new File(getFilesDir(), "attachments");
    }

    private File attachmentFile(String filename) {
        return new File(attachmentsDir(), filename);
    }

    private void deleteAttachmentFile(String filename) {
        File file = attachmentFile(filename);
        if (file.exists()) {
            file.delete();
        }
    }

    private void deleteUnreferencedAttachmentsInBody(String body) {
        for (String filename : extractAttachmentNames(body)) {
            if (!isAttachmentReferenced(filename)) {
                deleteAttachmentFile(filename);
            }
        }
    }

    private void cleanupUnreferencedAttachmentFiles() {
        File[] files = attachmentsDir().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && !isAttachmentReferenced(file.getName())) {
                file.delete();
            }
        }
    }

    private void renderCalendar() {
        calendarGrid.removeAllViews();
        monthTitle.setText(monthTitleFormat.format(visibleMonth.getTime()));

        LinearLayout weekNames = new LinearLayout(this);
        weekNames.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"日", "一", "二", "三", "四", "五", "六"};
        for (String name : names) {
            TextView cell = new TextView(this);
            cell.setText(name);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(11);
            cell.setTextColor(MUTED);
            weekNames.addView(cell, calendarCellLayout());
        }
        calendarGrid.addView(weekNames, matchWrap());

        Calendar first = (Calendar) visibleMonth.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        first = startOfDayCalendar(first);
        int firstWeekday = first.get(Calendar.DAY_OF_WEEK) - 1;
        Calendar cursor = (Calendar) first.clone();
        cursor.add(Calendar.DAY_OF_MONTH, -firstWeekday);

        Map<Long, Integer> dayCounts = countEntriesByDay();
        for (int row = 0; row < 6; row++) {
            LinearLayout week = new LinearLayout(this);
            week.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 7; col++) {
                long dayStart = cursor.getTimeInMillis();
                boolean inMonth = cursor.get(Calendar.MONTH) == visibleMonth.get(Calendar.MONTH);
                int count = dayCounts.containsKey(dayStart) ? dayCounts.get(dayStart) : 0;
                boolean selected = dayStart == selectedDayStart;
                Button day = lightButton(dayText(cursor, count), v -> {
                    selectedDayStart = dayStart;
                    calendarListLimit = PAGE_SIZE;
                    visibleMonth.setTimeInMillis(dayStart);
                    renderCalendar();
                    renderCalendarEntries();
                });
                day.setTextSize(12);
                day.setTextColor(selected ? CARD : (inMonth ? INK : Color.rgb(176, 176, 166)));
                day.setBackground(dayBackground(selected, count > 0));
                week.addView(day, calendarCellLayout());
                cursor.add(Calendar.DAY_OF_MONTH, 1);
            }
            calendarGrid.addView(week, matchWrap());
        }
    }

    private void renderCalendarEntries() {
        calendarEntries.removeAllViews();
        List<JSONObject> entries = selectedDayStart == -1L ? filterEntriesByAuthor(allEntries()) : entriesForDay(selectedDayStart);
        if (entries.isEmpty()) {
            calendarEntries.addView(emptyText("这天没有日记"), matchWrap());
            return;
        }
        renderEntryList(calendarEntries, entries, calendarListLimit, () -> {
            calendarListLimit += PAGE_SIZE;
            renderCalendarEntries();
        });
    }

    private void renderTagContent() {
        if (tagFilters == null || tagResults == null) {
            return;
        }
        renderTagChips();
        List<JSONObject> entries = entriesForTag(selectedTag);
        tagCount.setText(selectedTag == null ? "全部日记 · " + entries.size() : "#" + selectedTag + " · " + entries.size());
        tagResults.removeAllViews();
        if (entries.isEmpty()) {
            tagResults.addView(emptyText(selectedTag == null ? "还没有日记" : "没有这个标签的日记"), matchWrap());
            return;
        }
        renderEntryList(tagResults, entries, tagListLimit, () -> {
            tagListLimit += PAGE_SIZE;
            renderTagContent();
        });
    }

    private void renderEntryList(LinearLayout container, List<JSONObject> entries, int limit, Runnable loadMore) {
        int shown = Math.min(entries.size(), limit);
        for (int i = 0; i < shown; i++) {
            container.addView(entryCard(entries.get(i)), matchWrap());
        }
        if (shown < entries.size()) {
            Button more = lightButton("加载更多 · 还剩 " + (entries.size() - shown), v -> loadMore.run());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44)
            );
            params.setMargins(0, dp(2), 0, dp(12));
            container.addView(more, params);
        }
    }

    private void renderTagChips() {
        tagFilters.removeAllViews();
        Map<String, Integer> counts = countTags();
        FlowLayout flow = filterFlow(tagFilters, "标签");
        int total = filterEntriesByAuthor(allEntries()).size();
        flow.addView(chip("全部标签 " + total, selectedTag == null, v -> {
            selectedTag = null;
            tagListLimit = PAGE_SIZE;
            renderTagContent();
        }), filterChipWrapLayout());

        if (counts.isEmpty()) {
            TextView empty = emptyText("暂无标签");
            empty.setPadding(0, dp(6), 0, dp(10));
            tagFilters.addView(empty, matchWrap());
            return;
        }

        for (Map.Entry<String, Integer> tag : counts.entrySet()) {
            String name = tag.getKey();
            flow.addView(chip("#" + name + " " + tag.getValue(), name.equals(selectedTag), v -> {
                selectedTag = name;
                tagListLimit = PAGE_SIZE;
                renderTagContent();
            }), filterChipWrapLayout());
        }
    }

    private LinearLayout authorFilterChips(Runnable refresh) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(8));

        Map<String, Integer> counts = countAuthors();
        FlowLayout flow = filterFlow(box, "写作者");

        int total = 0;
        for (Integer count : counts.values()) {
            total += count;
        }
        flow.addView(chip("全部作者 " + total, selectedAuthor == null, v -> {
            selectedAuthor = null;
            refresh.run();
        }), filterChipWrapLayout());

        for (Map.Entry<String, Integer> author : counts.entrySet()) {
            String name = author.getKey();
            flow.addView(chip(name + " " + author.getValue(), name.equals(selectedAuthor), v -> {
                selectedAuthor = name;
                refresh.run();
            }), filterChipWrapLayout());
        }
        return box;
    }

    private void renderWriterTagHints() {
        writerTagHints.removeAllViews();
        Map<String, Integer> counts = countTags();
        if (counts.isEmpty()) {
            return;
        }

        String prefix = currentTagPrefix();
        if (prefix == null) {
            return;
        }
        List<String> matches = new ArrayList<>();
        for (String tag : counts.keySet()) {
            if (prefix.isEmpty() || tag.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                matches.add(tag);
            }
        }
        if (matches.isEmpty()) {
            return;
        }

        TextView label = new TextView(this);
        label.setText(prefix.isEmpty() ? "常用标签" : "标签联想");
        label.setTextSize(12);
        label.setTextColor(MUTED);
        label.setPadding(0, 0, 0, dp(4));
        writerTagHints.addView(label, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        writerTagHints.addView(row, matchWrap());

        int shown = 0;
        for (String tag : matches) {
            if (shown == 6) {
                break;
            }
            if (shown > 0 && shown % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                writerTagHints.addView(row, matchWrap());
            }
            row.addView(chip("#" + tag, false, v -> insertTagSuggestion(tag)), chipLayout());
            shown++;
        }
    }

    private void renderWriterImagePreview() {
        writerImagePreview.removeAllViews();
        List<String> attachments = extractAttachmentNames(bodyInput.getText().toString());
        if (attachments.isEmpty()) {
            return;
        }

        TextView label = new TextView(this);
        label.setText("图片");
        label.setTextSize(12);
        label.setTextColor(MUTED);
        label.setPadding(0, 0, 0, dp(4));
        writerImagePreview.addView(label, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        writerImagePreview.addView(row, matchWrap());

        int shown = 0;
        for (String filename : attachments) {
            if (shown == 6) {
                break;
            }
            if (shown > 0 && shown % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                writerImagePreview.addView(row, matchWrap());
            }
            row.addView(attachmentThumb(filename), thumbLayout());
            shown++;
        }
    }

    private View attachmentThumb(String filename) {
        ImageView image = new ImageView(this);
        File file = attachmentFile(filename);
        if (file.exists()) {
            image.setImageURI(Uri.fromFile(file));
        }
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAdjustViewBounds(false);
        image.setBackground(borderedBackground(CARD, LINE, dp(10)));
        image.setPadding(dp(1), dp(1), dp(1), dp(1));
        image.setOnClickListener(v -> {
            int position = bodyInput.getText().toString().indexOf("![图片](attachment:" + filename + ")");
            if (position >= 0) {
                bodyInput.requestFocus();
                bodyInput.setSelection(position);
            }
        });
        image.setOnLongClickListener(v -> {
            confirmRemoveAttachment(filename);
            return true;
        });
        return image;
    }

    private void confirmRemoveAttachment(String filename) {
        new AlertDialog.Builder(this)
                .setMessage("从正文移除这张图片？")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> removeAttachmentToken(filename))
                .show();
    }

    private void removeAttachmentToken(String filename) {
        String token = "![图片](attachment:" + filename + ")";
        Editable editable = bodyInput.getText();
        String body = editable.toString();
        int position = body.indexOf(token);
        if (position < 0) {
            return;
        }
        int end = position + token.length();
        if (position > 0 && body.charAt(position - 1) == '\n') {
            position--;
        }
        if (end < body.length() && body.charAt(end) == '\n') {
            end++;
        }
        editable.delete(position, end);
        renderWriterImagePreview();
    }

    private String currentTagPrefix() {
        int cursor = bodyInput.getSelectionStart();
        if (cursor < 0) {
            return null;
        }
        String text = bodyInput.getText().toString();
        int hash = text.lastIndexOf('#', Math.max(0, cursor - 1));
        if (hash < 0) {
            return null;
        }
        for (int i = hash + 1; i < cursor; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                return null;
            }
        }
        return text.substring(hash + 1, cursor);
    }

    private void insertTagSuggestion(String tag) {
        Editable editable = bodyInput.getText();
        int cursor = bodyInput.getSelectionStart();
        if (cursor < 0) {
            cursor = editable.length();
        }
        String text = editable.toString();
        int hash = text.lastIndexOf('#', Math.max(0, cursor - 1));
        if (hash >= 0) {
            boolean valid = true;
            for (int i = hash + 1; i < cursor; i++) {
                if (Character.isWhitespace(text.charAt(i))) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                editable.replace(hash, cursor, "#" + tag + " ");
                bodyInput.setSelection(Math.min(editable.length(), hash + tag.length() + 2));
                return;
            }
        }
        String prefix = editable.length() == 0 || Character.isWhitespace(editable.charAt(editable.length() - 1)) ? "" : " ";
        editable.append(prefix).append("#").append(tag).append(" ");
        bodyInput.setSelection(editable.length());
    }

    private View entryCard(JSONObject entry) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(14), dp(12), dp(14), dp(12));
        item.setBackground(cardBackground(editingId == entry.optLong("id")));

        TextView meta = new TextView(this);
        meta.setText(formatEntryDate(entry) + authorLine(entry) + tagLine(entry) + attachmentLine(entry));
        meta.setTextSize(12);
        meta.setTextColor(MUTED);
        item.addView(meta, matchWrap());

        TextView text = new TextView(this);
        text.setText(coloredTagText(preview(entry.optString("body", ""))));
        text.setTextSize(15);
        text.setTextColor(INK);
        text.setPadding(0, dp(6), 0, 0);
        item.addView(text, matchWrap());

        item.setOnClickListener(v -> showReadOnlyEntry(entry));
        item.setOnLongClickListener(v -> {
            confirmDelete(entry.optLong("id"));
            return true;
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        item.setLayoutParams(params);
        return item;
    }

    private void showReadOnlyEntry(JSONObject entry) {
        detailReturnTab = currentTab;
        inDetail = true;
        pageHost.removeAllViews();
        if (tabBar != null) {
            tabBar.setVisibility(View.GONE);
        }
        if (headerBack != null) {
            headerBack.setVisibility(View.VISIBLE);
        }
        if (headerTitle != null) {
            headerTitle.setText("阅读");
        }
        if (headerSettings != null) {
            headerSettings.setVisibility(View.VISIBLE);
        }
        hideKeyboard();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, 0);
        pageHost.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, 0, dp(10));
        content.addView(top, matchWrap());

        TextView title = new TextView(this);
        title.setText(formatEntryDate(entry));
        title.setTextSize(14);
        title.setTextColor(MUTED);
        title.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

        top.addView(darkButton("编辑", v -> loadEntry(entry)), actionLayout(0.8f));

        TextView meta = new TextView(this);
        meta.setText((entryAuthor(entry) + tagLine(entry)).trim());
        meta.setTextSize(13);
        meta.setTextColor(MUTED);
        meta.setPadding(dp(4), 0, dp(4), dp(8));
        if (!meta.getText().toString().isEmpty()) {
            content.addView(meta, matchWrap());
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout bodyContent = new LinearLayout(this);
        bodyContent.setOrientation(LinearLayout.VERTICAL);
        bodyContent.setPadding(dp(4), dp(4), dp(4), dp(4));
        renderBodyWithAttachments(bodyContent, entry.optString("body", ""));
        scroll.addView(bodyContent, matchWrap());
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(0, dp(8), 0, 0);
        content.addView(bottom, matchWrap());
        bottom.addView(lightButton("删除", v -> confirmDelete(entry.optLong("id"))), actionLayout(1));
    }

    private void renderBodyWithAttachments(LinearLayout container, String body) {
        Matcher matcher = ATTACHMENT_PATTERN.matcher(body == null ? "" : body);
        int cursor = 0;
        while (matcher.find()) {
            addBodyText(container, body.substring(cursor, matcher.start()));
            addAttachmentImage(container, matcher.group(1));
            cursor = matcher.end();
        }
        addBodyText(container, body == null ? "" : body.substring(cursor));
    }

    private void addBodyText(LinearLayout container, String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            return;
        }
        TextView body = new TextView(this);
        body.setText(coloredTagText(clean));
        body.setTextSize(17);
        body.setTextColor(INK);
        body.setLineSpacing(dp(3), 1.0f);
        body.setPadding(0, 0, 0, dp(10));
        container.addView(body, matchWrap());
    }

    private void addAttachmentImage(LinearLayout container, String filename) {
        if (!isSafeAttachmentName(filename)) {
            return;
        }
        File file = attachmentFile(filename);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (!file.exists() || options.outWidth <= 0 || options.outHeight <= 0) {
            TextView missing = emptyText("图片不存在");
            missing.setPadding(0, dp(8), 0, dp(8));
            container.addView(missing, matchWrap());
            return;
        }
        ZoomImageView image = new ZoomImageView(this);
        image.setImageURI(Uri.fromFile(file));
        image.setBackground(borderedBackground(CARD, LINE, dp(10)));
        image.setPadding(dp(1), dp(1), dp(1), dp(1));
        image.setOnClickListener(v -> showImagePreview(file));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(240)
        );
        params.setMargins(0, 0, 0, dp(10));
        container.addView(image, params);
    }

    private void showImagePreview(File file) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        top.setPadding(dp(12), dp(10), dp(12), dp(6));
        preview.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button close = plainButton("关闭", v -> dialog.dismiss());
        close.setTextColor(Color.WHITE);
        close.setBackground(transparentBackground());
        top.addView(close, new LinearLayout.LayoutParams(dp(72), dp(42)));

        ZoomImageView image = new ZoomImageView(this);
        image.setImageURI(Uri.fromFile(file));
        image.setBackgroundColor(Color.BLACK);
        preview.addView(image, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        dialog.setContentView(preview, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(solidBackground(Color.BLACK, 0));
        }
        dialog.setOnShowListener(d -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                shownWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
        });
        dialog.show();
    }

    private static class ZoomImageView extends ImageView {
        private static final float MAX_SCALE = 5.0f;
        private static final float DOUBLE_TAP_SCALE = 2.5f;

        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private float currentScale = 1.0f;
        private float lastX;
        private float lastY;

        ZoomImageView(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.MATRIX);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float factor = detector.getScaleFactor();
                    float nextScale = currentScale * factor;
                    if (nextScale < 1.0f) {
                        factor = 1.0f / currentScale;
                        nextScale = 1.0f;
                    } else if (nextScale > MAX_SCALE) {
                        factor = MAX_SCALE / currentScale;
                        nextScale = MAX_SCALE;
                    }
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    currentScale = nextScale;
                    constrainImage();
                    return true;
                }
            });
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    performClick();
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (currentScale > 1.05f) {
                        resetImage();
                    } else {
                        zoomTo(DOUBLE_TAP_SCALE, e.getX(), e.getY());
                    }
                    return true;
                }
            });
            setClickable(true);
        }

        @Override
        public void setImageURI(Uri uri) {
            super.setImageURI(uri);
            resetImage();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            gestureDetector.onTouchEvent(event);
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && currentScale > 1.0f) {
                        float x = event.getX();
                        float y = event.getY();
                        matrix.postTranslate(x - lastX, y - lastY);
                        lastX = x;
                        lastY = y;
                        constrainImage();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            resetImage();
        }

        private void zoomTo(float scale, float focusX, float focusY) {
            float factor = scale / currentScale;
            matrix.postScale(factor, factor, focusX, focusY);
            currentScale = scale;
            constrainImage();
        }

        private void resetImage() {
            matrix.reset();
            currentScale = 1.0f;
            if (getDrawable() != null && getWidth() > 0 && getHeight() > 0) {
                int drawableWidth = getDrawable().getIntrinsicWidth();
                int drawableHeight = getDrawable().getIntrinsicHeight();
                if (drawableWidth > 0 && drawableHeight > 0) {
                    float scale = Math.min(
                            (float) getWidth() / drawableWidth,
                            (float) getHeight() / drawableHeight
                    );
                    float dx = (getWidth() - drawableWidth * scale) / 2.0f;
                    float dy = (getHeight() - drawableHeight * scale) / 2.0f;
                    matrix.setScale(scale, scale);
                    matrix.postTranslate(dx, dy);
                }
            }
            setImageMatrix(matrix);
        }

        private void constrainImage() {
            if (getDrawable() == null) {
                return;
            }
            RectF rect = new RectF(
                    0,
                    0,
                    getDrawable().getIntrinsicWidth(),
                    getDrawable().getIntrinsicHeight()
            );
            matrix.mapRect(rect);

            float dx;
            if (rect.width() <= getWidth()) {
                dx = (getWidth() - rect.width()) / 2.0f - rect.left;
            } else if (rect.left > 0) {
                dx = -rect.left;
            } else if (rect.right < getWidth()) {
                dx = getWidth() - rect.right;
            } else {
                dx = 0;
            }

            float dy;
            if (rect.height() <= getHeight()) {
                dy = (getHeight() - rect.height()) / 2.0f - rect.top;
            } else if (rect.top > 0) {
                dy = -rect.top;
            } else if (rect.bottom < getHeight()) {
                dy = getHeight() - rect.bottom;
            } else {
                dy = 0;
            }

            matrix.postTranslate(dx, dy);
            setImageMatrix(matrix);
        }
    }

    private void loadEntry(JSONObject entry) {
        if (hasUnsavedDraft() && editingId != entry.optLong("id", 0L)) {
            confirmDiscardDraft(() -> {
                clearEditorOnly();
                doLoadEntry(entry);
            });
            return;
        }
        doLoadEntry(entry);
    }

    private void doLoadEntry(JSONObject entry) {
        editingId = entry.optLong("id", 0L);
        String body = entry.optString("body", "");
        editingOriginalBody = body;
        bodyInput.setText(body);
        bodyInput.setSelection(bodyInput.getText().length());
        selectedDayStart = startOfDay(editingId);
        visibleMonth.setTimeInMillis(selectedDayStart);
        switchTab(TAB_WRITE);
        toast("编辑历史日记");
    }

    private void clearEditor() {
        if (hasUnsavedDraft()) {
            confirmDiscardDraft(() -> {
                clearEditorOnly();
                switchTab(TAB_WRITE);
            });
            return;
        }
        clearEditorOnly();
        switchTab(TAB_WRITE);
    }

    private void clearEditorOnly() {
        deleteUnreferencedAttachmentsInBody(bodyInput.getText().toString());
        editingId = 0L;
        editingOriginalBody = "";
        bodyInput.setText("");
        updateEditState();
    }

    private boolean hasUnsavedDraft() {
        if (bodyInput == null) {
            return false;
        }
        String body = bodyInput.getText().toString();
        if (editingId == 0L) {
            return !body.trim().isEmpty();
        }
        return !body.equals(editingOriginalBody);
    }

    private void confirmDiscardDraft(Runnable action) {
        new AlertDialog.Builder(this)
                .setMessage("当前内容还没有保存，放弃这次修改？")
                .setNegativeButton("继续写", null)
                .setPositiveButton("放弃", (dialog, which) -> action.run())
                .show();
    }

    private void confirmDelete(long id) {
        new AlertDialog.Builder(this)
                .setMessage("删除这篇日记？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteEntry(id))
                .show();
    }

    private void deleteEntry(long id) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            List<String> attachments = attachmentNamesForEntry(id);
            for (String filename : attachments) {
                if (!isAttachmentReferencedByOtherEntry(filename, id)) {
                    deleteAttachmentFile(filename);
                }
            }
            db.delete(DiaryDatabase.ATTACHMENTS_TABLE, "entry_id = ?", new String[]{String.valueOf(id)});
            db.delete(DiaryDatabase.TAGS_TABLE, "entry_id = ?", new String[]{String.valueOf(id)});
            ContentValues tombstone = new ContentValues();
            tombstone.put("updated_at", System.currentTimeMillis());
            tombstone.put("deleted_at", System.currentTimeMillis());
            tombstone.put("version", entryVersion(id) + 1);
            tombstone.put("device_id", deviceId);
            db.update(DiaryDatabase.ENTRIES_TABLE, tombstone, "id = ?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (editingId == id) {
            clearEditorOnly();
        }
        switchTab(currentTab);
        toast("已删除");
    }

    private List<JSONObject> allEntries() {
        return entriesFromDatabase(false);
    }

    private List<JSONObject> allEntriesForExport() {
        return entriesFromDatabase(true);
    }

    private List<JSONObject> entriesFromDatabase(boolean includeDeleted) {
        List<JSONObject> result = new ArrayList<>();
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ENTRIES_TABLE,
                null,
                includeDeleted ? null : "deleted_at = 0",
                null,
                null,
                null,
                "created_at DESC, id DESC"
        )) {
            while (cursor.moveToNext()) {
                result.add(entryFromCursor(cursor));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private List<JSONObject> entriesForTag(String tag) {
        if (tag == null) {
            return filterEntriesByAuthor(allEntries());
        }

        List<JSONObject> result = new ArrayList<>();
        String sql = "SELECT e.* "
                + "FROM " + DiaryDatabase.ENTRIES_TABLE + " e "
                + "JOIN " + DiaryDatabase.TAGS_TABLE + " t ON t.entry_id = e.id "
                + "WHERE t.tag = ? AND e.deleted_at = 0 "
                + "ORDER BY e.created_at DESC, e.id DESC";
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(sql, new String[]{tag})) {
            while (cursor.moveToNext()) {
                JSONObject entry = entryFromCursor(cursor);
                if (matchesSelectedAuthor(entry)) {
                    result.add(entry);
                }
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private List<JSONObject> entriesForDay(long dayStart) {
        List<JSONObject> result = new ArrayList<>();
        long dayEnd = dayStart + 24L * 60L * 60L * 1000L;
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ENTRIES_TABLE,
                null,
                "created_at >= ? AND created_at < ? AND deleted_at = 0",
                new String[]{String.valueOf(dayStart), String.valueOf(dayEnd)},
                null,
                null,
                "created_at DESC, id DESC"
        )) {
            while (cursor.moveToNext()) {
                JSONObject entry = entryFromCursor(cursor);
                if (matchesSelectedAuthor(entry)) {
                    result.add(entry);
                }
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private String buildMarkdown() {
        JSONArray entries = readEntries();
        StringBuilder builder = new StringBuilder("# 一页日记\n\n");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            builder.append("## ").append(formatEntryDate(entry)).append(" · ").append(entryAuthor(entry)).append("\n\n");
            builder.append(markdownBody(entry.optString("body", "").trim())).append("\n\n");
        }
        return builder.toString();
    }

    private String markdownBody(String body) {
        Matcher matcher = ATTACHMENT_PATTERN.matcher(body == null ? "" : body);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String filename = matcher.group(1);
            String replacement = isSafeAttachmentName(filename) ? "![图片](attachments/" + filename + ")" : "[图片]";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String buildJsonExport() {
        JSONObject envelope = new JSONObject();
        JSONArray normalized = new JSONArray();
        for (JSONObject entry : allEntriesForExport()) {
            try {
                normalized.put(normalizeEntry(entry));
            } catch (JSONException ignored) {
            }
        }
        try {
            envelope.put("app", "one-page-diary");
            envelope.put("version", 2);
            envelope.put("deviceId", deviceId);
            envelope.put("exportedAt", System.currentTimeMillis());
            envelope.put("entries", normalized);
            return envelope.toString();
        } catch (JSONException e) {
            return normalized.toString();
        }
    }

    private void writeZipBackup(OutputStream output) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addZipText(zip, "diary.json", buildJsonExport());
            addZipText(zip, "diary.md", buildMarkdown());
            for (String filename : allReferencedAttachments()) {
                File file = attachmentFile(filename);
                if (file.exists() && file.isFile()) {
                    addZipFile(zip, "attachments/" + filename, file);
                }
            }
        }
    }

    private void addZipText(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void addZipFile(ZipOutputStream zip, String name, File file) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                zip.write(buffer, 0, count);
            }
        }
        zip.closeEntry();
    }

    private List<String> allReferencedAttachments() {
        Set<String> attachments = new LinkedHashSet<>();
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ATTACHMENTS_TABLE,
                new String[]{"filename"},
                null,
                null,
                null,
                null,
                "filename ASC"
        )) {
            while (cursor.moveToNext()) {
                String filename = cursor.getString(cursor.getColumnIndexOrThrow("filename"));
                if (isSafeAttachmentName(filename)) {
                    attachments.add(filename);
                }
            }
        }
        return new ArrayList<>(attachments);
    }

    private List<String> extractTags(String body) {
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = TAG_PATTERN.matcher(body == null ? "" : body);
        while (matcher.find()) {
            String tag = matcher.group(1).trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return new ArrayList<>(tags);
    }

    private void styleTagsInEditor(Editable editable) {
        if (stylingBodyText) {
            return;
        }
        stylingBodyText = true;

        ForegroundColorSpan[] colorSpans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : colorSpans) {
            editable.removeSpan(span);
        }
        StyleSpan[] styleSpans = editable.getSpans(0, editable.length(), StyleSpan.class);
        for (StyleSpan span : styleSpans) {
            editable.removeSpan(span);
        }

        Matcher matcher = TAG_PATTERN.matcher(editable.toString());
        while (matcher.find()) {
            editable.setSpan(new ForegroundColorSpan(ACCENT), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            editable.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        stylingBodyText = false;
    }

    private void styleAttachmentsInEditor(Editable editable) {
        if (stylingAttachmentImages) {
            return;
        }
        stylingAttachmentImages = true;
        ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);
        for (ImageSpan span : spans) {
            editable.removeSpan(span);
        }
        Matcher matcher = ATTACHMENT_PATTERN.matcher(editable.toString());
        while (matcher.find()) {
            ImageSpan span = imageSpanForAttachment(matcher.group(1));
            if (span != null) {
                editable.setSpan(span, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        stylingAttachmentImages = false;
    }

    private ImageSpan imageSpanForAttachment(String filename) {
        if (!isSafeAttachmentName(filename)) {
            return null;
        }
        File file = attachmentFile(filename);
        if (!file.exists()) {
            return null;
        }
        int maxWidth = Math.max(dp(180), getResources().getDisplayMetrics().widthPixels - dp(64));
        int maxHeight = dp(220);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            return null;
        }
        float scale = Math.min((float) maxWidth / bitmap.getWidth(), (float) maxHeight / bitmap.getHeight());
        scale = Math.min(1f, scale);
        int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
        int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        BitmapDrawable drawable = new BitmapDrawable(getResources(), scaled);
        drawable.setBounds(0, 0, width, height);
        return new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
    }

    private int imageSampleSize(int width, int height, int targetWidth, int targetHeight) {
        int sample = 1;
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
            sample *= 2;
        }
        return sample;
    }

    private Spannable coloredTagText(String text) {
        Spannable spannable = Spannable.Factory.getInstance().newSpannable(text);
        Matcher matcher = TAG_PATTERN.matcher(text);
        while (matcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(ACCENT), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private List<String> entryTags(JSONObject entry) {
        Set<String> tags = new LinkedHashSet<>(extractTags(entry.optString("body", "")));
        JSONArray stored = entry.optJSONArray("tags");
        if (stored != null) {
            for (int i = 0; i < stored.length(); i++) {
                String tag = stored.optString(i, "").replace("#", "").trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private Map<Long, Integer> countEntriesByDay() {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                DiaryDatabase.ENTRIES_TABLE,
                new String[]{"created_at", "author"},
                "deleted_at = 0",
                null,
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                if (selectedAuthor != null && !selectedAuthor.equals(cleanAuthor(cursor.getString(cursor.getColumnIndexOrThrow("author"))))) {
                    continue;
                }
                long day = startOfDay(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
                Integer count = counts.get(day);
                counts.put(day, count == null ? 1 : count + 1);
            }
        }
        return counts;
    }

    private Map<String, Integer> countTags() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JSONObject entry : filterEntriesByAuthor(allEntries())) {
            for (String tag : entryTags(entry)) {
                Integer count = counts.get(tag);
                counts.put(tag, count == null ? 1 : count + 1);
            }
        }
        List<String> names = new ArrayList<>(counts.keySet());
        Collections.sort(names);
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (String name : names) {
            sorted.put(name, counts.get(name));
        }
        return sorted;
    }

    private Map<String, Integer> countAuthors() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JSONObject entry : allEntries()) {
            String author = entryAuthor(entry);
            Integer count = counts.get(author);
            counts.put(author, count == null ? 1 : count + 1);
        }
        List<String> names = new ArrayList<>(counts.keySet());
        Collections.sort(names);
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (String name : names) {
            sorted.put(name, counts.get(name));
        }
        return sorted;
    }

    private void clearUnavailableSelectedTag() {
        if (selectedTag != null && !countTags().containsKey(selectedTag)) {
            selectedTag = null;
        }
    }

    private List<JSONObject> filterEntriesByAuthor(List<JSONObject> entries) {
        if (selectedAuthor == null) {
            return entries;
        }
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject entry : entries) {
            if (matchesSelectedAuthor(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    private boolean matchesSelectedAuthor(JSONObject entry) {
        return selectedAuthor == null || selectedAuthor.equals(entryAuthor(entry));
    }

    private String entryAuthor(JSONObject entry) {
        return cleanAuthor(entry == null ? DEFAULT_AUTHOR : entry.optString("author", DEFAULT_AUTHOR));
    }

    private void moveMonth(int amount) {
        visibleMonth.add(Calendar.MONTH, amount);
        renderCalendar();
        renderCalendarEntries();
    }

    private String dayText(Calendar day, int count) {
        String text = String.valueOf(day.get(Calendar.DAY_OF_MONTH));
        if (count == 1) {
            return text + "\n·";
        }
        if (count > 1) {
            return text + "\n" + count;
        }
        return text + "\n ";
    }

    private long startOfDay(long millis) {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        calendar.setTimeInMillis(millis <= 0L ? System.currentTimeMillis() : millis);
        return startOfDayCalendar(calendar).getTimeInMillis();
    }

    private Calendar startOfDayCalendar(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private String formatEntryDate(JSONObject entry) {
        long id = entry.optLong("id", 0L);
        if (id <= 0L) {
            return "未知时间";
        }
        return displayDate.format(new Date(id));
    }

    private String tagLine(JSONObject entry) {
        List<String> tags = entryTags(entry);
        if (tags.isEmpty()) {
            return "";
        }
        return "  " + formatTags(tags);
    }

    private String authorLine(JSONObject entry) {
        return "  " + entryAuthor(entry);
    }

    private String attachmentLine(JSONObject entry) {
        JSONArray attachments = entry.optJSONArray("attachments");
        int count = attachments == null ? extractAttachmentNames(entry.optString("body", "")).size() : attachments.length();
        if (count <= 0) {
            return "";
        }
        return "  " + count + " 图";
    }

    private String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                builder.append(" ");
            }
            builder.append("#").append(tags.get(i));
        }
        return builder.toString();
    }

    private String preview(String body) {
        String preview = ATTACHMENT_PATTERN.matcher(body == null ? "" : body).replaceAll("[图片]");
        preview = preview.replace('\n', ' ').trim();
        if (preview.length() > 82) {
            return preview.substring(0, 82) + "...";
        }
        return preview;
    }

    private void updateEditState() {
        if (saveButton != null) {
            saveButton.setText(editingId == 0L ? "保存" : "更新");
        }
        if (clearButton != null) {
            clearButton.setContentDescription(editingId == 0L ? "清空" : "取消编辑");
        }
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(MUTED);
        title.setPadding(0, 0, 0, dp(8));
        return title;
    }

    private TextView filterLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(MUTED);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(2), 0, dp(4), 0);
        return label;
    }

    private FlowLayout filterFlow(LinearLayout container, String labelText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, 0, 0, dp(6));
        container.addView(row, matchWrap());

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
        );
        labelParams.setMargins(0, dp(1), dp(8), dp(3));
        row.addView(filterLabel(labelText), labelParams);

        FlowLayout flow = new FlowLayout(this);
        row.addView(flow, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        return flow;
    }

    private LinearLayout settingsCard(String titleText, String bodyText, String footText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(cardBackground(false));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(INK);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, matchWrap());

        TextView body = new TextView(this);
        body.setText(bodyText);
        body.setTextColor(MUTED);
        body.setTextSize(14);
        body.setLineSpacing(dp(2), 1.0f);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body, matchWrap());

        TextView foot = new TextView(this);
        foot.setText(footText);
        foot.setTextColor(ACCENT);
        foot.setTextSize(13);
        foot.setLineSpacing(dp(2), 1.0f);
        foot.setPadding(0, dp(8), 0, 0);
        card.addView(foot, matchWrap());
        return card;
    }

    private LinearLayout.LayoutParams cardLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private String appVersionText() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return "一页日记 " + info.versionName + " / " + versionCode;
        } catch (Exception ignored) {
            return "一页日记";
        }
    }

    private TextView emptyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(144, 145, 137));
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(18), 0, dp(18));
        return view;
    }

    private Button darkButton(String text, View.OnClickListener listener) {
        Button button = plainButton(text, listener);
        button.setTextColor(CARD);
        button.setBackground(solidBackground(ACCENT, dp(10)));
        return button;
    }

    private Button lightButton(String text, View.OnClickListener listener) {
        Button button = plainButton(text, listener);
        button.setTextColor(INK);
        button.setBackground(borderedBackground(CARD, LINE, dp(10)));
        return button;
    }

    private ImageButton lightIconButton(int imageResource, String description, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(imageResource);
        button.setColorFilter(INK);
        button.setBackground(borderedBackground(CARD, LINE, dp(10)));
        button.setContentDescription(description);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setOnClickListener(listener);
        return button;
    }

    private Button chip(String text, boolean selected, View.OnClickListener listener) {
        Button button = plainButton(text, listener);
        button.setTextSize(11);
        button.setTextColor(selected ? CARD : INK);
        button.setBackground(selected ? solidBackground(ACCENT, dp(8)) : borderedBackground(CARD, LINE, dp(8)));
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private Button plainButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable solidBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable borderedBackground(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable transparentBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(dp(10));
        return drawable;
    }

    private GradientDrawable editorBackground() {
        return borderedBackground(CARD, LINE, dp(12));
    }

    private GradientDrawable dayBackground(boolean selected, boolean hasEntry) {
        if (selected) {
            return solidBackground(ACCENT, dp(10));
        }
        return borderedBackground(hasEntry ? Color.rgb(235, 242, 241) : CARD, LINE, dp(10));
    }

    private GradientDrawable cardBackground(boolean selected) {
        return borderedBackground(selected ? Color.rgb(235, 242, 241) : CARD, selected ? ACCENT : LINE, dp(10));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams tabLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams actionLayout(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), weight);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams fixedActionLayout(int widthDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(widthDp), dp(44));
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams squareButtonLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(42), dp(42));
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams calendarCellLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(1), dp(1), dp(1), dp(1));
        return params;
    }

    private LinearLayout.LayoutParams chipLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(34), 1);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private LinearLayout.LayoutParams filterChipLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1);
        params.setMargins(dp(2), dp(1), dp(2), dp(3));
        return params;
    }

    private ViewGroup.MarginLayoutParams filterChipWrapLayout() {
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(32)
        );
        params.setMargins(dp(2), dp(1), dp(4), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams thumbLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(64), 1);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private void detach(View view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private void focusWriter() {
        bodyInput.requestFocus();
        bodyInput.postDelayed(() -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(bodyInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 250);
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && bodyInput != null) {
            manager.hideSoftInputFromWindow(bodyInput.getWindowToken(), 0);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class DiaryDatabase extends SQLiteOpenHelper {
        private static final String DB_NAME = "one_page_diary.db";
        private static final int DB_VERSION = 4;
        private static final String ENTRIES_TABLE = "diary_entries";
        private static final String TAGS_TABLE = "diary_tags";
        private static final String ATTACHMENTS_TABLE = "diary_attachments";

        DiaryDatabase(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + ENTRIES_TABLE + " ("
                    + "id INTEGER PRIMARY KEY, "
                    + "created_at INTEGER NOT NULL, "
                    + "updated_at INTEGER NOT NULL, "
                    + "deleted_at INTEGER NOT NULL DEFAULT 0, "
                    + "version INTEGER NOT NULL DEFAULT 1, "
                    + "device_id TEXT NOT NULL DEFAULT '', "
                    + "author TEXT NOT NULL DEFAULT '小妈妈', "
                    + "body TEXT NOT NULL"
                    + ")");
            db.execSQL("CREATE TABLE " + TAGS_TABLE + " ("
                    + "tag TEXT NOT NULL, "
                    + "entry_id INTEGER NOT NULL, "
                    + "PRIMARY KEY(tag, entry_id)"
                    + ")");
            db.execSQL("CREATE INDEX idx_diary_entries_created ON " + ENTRIES_TABLE + "(created_at DESC)");
            db.execSQL("CREATE INDEX idx_diary_tags_tag ON " + TAGS_TABLE + "(tag)");
            db.execSQL("CREATE INDEX idx_diary_tags_entry ON " + TAGS_TABLE + "(entry_id)");
            createAttachmentsTable(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                createAttachmentsTable(db);
            }
            if (oldVersion < 3) {
                addColumn(db, ENTRIES_TABLE, "deleted_at INTEGER NOT NULL DEFAULT 0");
                addColumn(db, ENTRIES_TABLE, "version INTEGER NOT NULL DEFAULT 1");
                addColumn(db, ENTRIES_TABLE, "device_id TEXT NOT NULL DEFAULT ''");
            }
            if (oldVersion < 4) {
                addColumn(db, ENTRIES_TABLE, "author TEXT NOT NULL DEFAULT '小妈妈'");
            }
        }

        private static void addColumn(SQLiteDatabase db, String table, String definition) {
            try {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + definition);
            } catch (Exception ignored) {
            }
        }

        private static void createAttachmentsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + ATTACHMENTS_TABLE + " ("
                    + "entry_id INTEGER NOT NULL, "
                    + "filename TEXT NOT NULL, "
                    + "created_at INTEGER NOT NULL, "
                    + "PRIMARY KEY(entry_id, filename)"
                    + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_diary_attachments_entry ON " + ATTACHMENTS_TABLE + "(entry_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_diary_attachments_filename ON " + ATTACHMENTS_TABLE + "(filename)");
        }
    }

    private static class ImportResult {
        final int entryCount;
        final int attachmentCount;

        ImportResult(int entryCount, int attachmentCount) {
            this.entryCount = entryCount;
            this.attachmentCount = attachmentCount;
        }
    }

    private final class FlowLayout extends ViewGroup {
        FlowLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            int availableWidth = widthMode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : maxWidth;
            int lineWidth = getPaddingLeft();
            int lineHeight = 0;
            int totalHeight = getPaddingTop();
            int measuredWidth = getPaddingLeft() + getPaddingRight();

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                if (lineWidth > getPaddingLeft() && lineWidth + childWidth + getPaddingRight() > availableWidth) {
                    measuredWidth = Math.max(measuredWidth, lineWidth + getPaddingRight());
                    totalHeight += lineHeight;
                    lineWidth = getPaddingLeft();
                    lineHeight = 0;
                }
                lineWidth += childWidth;
                lineHeight = Math.max(lineHeight, childHeight);
            }

            measuredWidth = Math.max(measuredWidth, lineWidth + getPaddingRight());
            totalHeight += lineHeight + getPaddingBottom();
            setMeasuredDimension(
                    resolveSize(widthMode == MeasureSpec.UNSPECIFIED ? measuredWidth : maxWidth, widthMeasureSpec),
                    resolveSize(totalHeight, heightMeasureSpec)
            );
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int availableRight = right - left - getPaddingRight();
            int x = getPaddingLeft();
            int y = getPaddingTop();
            int lineHeight = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                if (x > getPaddingLeft() && x + lp.leftMargin + childWidth + lp.rightMargin > availableRight) {
                    x = getPaddingLeft();
                    y += lineHeight;
                    lineHeight = 0;
                }
                int childLeft = x + lp.leftMargin;
                int childTop = y + lp.topMargin;
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                x += lp.leftMargin + childWidth + lp.rightMargin;
                lineHeight = Math.max(lineHeight, lp.topMargin + childHeight + lp.bottomMargin);
            }
        }

        @Override
        protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
            return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }

        @Override
        protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams params) {
            return new MarginLayoutParams(params);
        }

        @Override
        public ViewGroup.LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
            return new MarginLayoutParams(getContext(), attrs);
        }

        @Override
        protected boolean checkLayoutParams(ViewGroup.LayoutParams params) {
            return params instanceof MarginLayoutParams;
        }
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

    private static class RemoteStatus {
        final boolean hasLatest;
        final String updatedAt;

        RemoteStatus(boolean hasLatest, String updatedAt) {
            this.hasLatest = hasLatest;
            this.updatedAt = updatedAt == null ? "" : updatedAt;
        }
    }
}
