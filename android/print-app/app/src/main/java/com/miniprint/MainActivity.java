package com.miniprint;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "quick_print";
    private static final String DRAFT_KEY = "draft";
    private static final String FONT_SIZE_KEY = "font_size";
    private static final String HISTORY_KEY = "history";
    private static final String FORMAT_MODE_KEY = "format_mode";
    private static final String FORMAT_AUTO = "auto";
    private static final String FORMAT_TEXT = "text";
    private static final String FORMAT_CSV = "csv";
    private static final int EDITOR_TEXT_SP = 18;
    private static final int HISTORY_LIMIT = 20;

    private static final int INK = Color.rgb(47, 41, 37);
    private static final int MUTED = Color.rgb(117, 104, 95);
    private static final int PAPER = Color.rgb(253, 239, 239);
    private static final int CARD = Color.rgb(255, 249, 246);
    private static final int LINE = Color.rgb(218, 208, 194);
    private static final int ACCENT = Color.rgb(138, 113, 94);
    private static final int ACCENT_DARK = Color.rgb(111, 88, 72);

    private final SimpleDateFormat fileDate = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA);
    private final SimpleDateFormat historyDate = new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA);

    private SharedPreferences preferences;
    private EditText editor;
    private TextView status;
    private Button formatButton;
    private int fontSize = 11;
    private String formatMode = FORMAT_AUTO;
    private boolean restoring = false;
    private boolean inSettings = false;
    private WebView printWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        fontSize = normalizePrintFontSize(preferences.getInt(FONT_SIZE_KEY, 11));
        formatMode = normalizeFormatMode(preferences.getString(FORMAT_MODE_KEY, FORMAT_AUTO));
        preferences.edit().putInt(FONT_SIZE_KEY, fontSize).apply();

        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        buildUi();
        restoreDraft();
        handleIncomingIntent(getIntent());
        focusEditor();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        if (printWebView != null) {
            printWebView.destroy();
            printWebView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (inSettings) {
            buildUi();
            restoreDraft();
            focusEditor();
            return;
        }
        super.onBackPressed();
    }

    private void buildUi() {
        inSettings = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(14), 0, dp(12));
        root.setBackgroundColor(PAPER);
        setContentView(root);

        root.addView(header(), matchWrap());

        editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(12);
        editor.setTextSize(EDITOR_TEXT_SP);
        editor.setTextColor(INK);
        editor.setHintTextColor(Color.rgb(146, 145, 135));
        editor.setHint("粘贴 AI 生成的文字，或从其它 App 分享到快印");
        editor.setPadding(dp(14), dp(14), dp(14), dp(14));
        editor.setBackground(panelBackground());
        editor.setSingleLine(false);
        editor.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!restoring) {
                    preferences.edit().putString(DRAFT_KEY, s.toString()).apply();
                    updateStatus();
                }
            }
        });
        root.addView(editor, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        status = new TextView(this);
        status.setTextColor(MUTED);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(16), dp(9), dp(16), dp(6));
        root.addView(status, matchWrap());

        root.addView(actionBar(), matchWrap());
        updateStatus();
    }

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), dp(10));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("快印");
        title.setTextColor(INK);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(title, matchWrap());
        row.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button history = smallButton("历史");
        history.setOnClickListener(v -> showHistory());
        row.addView(history, new LinearLayout.LayoutParams(dp(62), dp(34)));
        row.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));

        View settings = new SettingsIconButton(this);
        settings.setContentDescription("设置");
        settings.setOnClickListener(v -> showSettingsPage());
        row.addView(settings, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return row;
    }

    private void showSettingsPage() {
        if (editor != null) {
            preferences.edit().putString(DRAFT_KEY, editor.getText().toString()).apply();
        }
        hideKeyboard();
        inSettings = true;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(14), 0, dp(12));
        root.setBackgroundColor(PAPER);
        setContentView(root);

        root.addView(settingsHeader(), matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(18));
        scroll.addView(content, matchWrap());

        content.addView(settingsSectionTitle("关于"), matchWrap());
        content.addView(settingsCard(
                "快印",
                "快速接收分享文字，预览 A4 页面并调用 Android 系统打印。",
                "适合临时打印、AI 输出文本、CSV 表格和从其它 App 分享来的文字。"
        ), cardLayout());

        content.addView(settingsSectionTitle("数据管理"), matchWrap());
        content.addView(settingsCard(
                "本机草稿与历史",
                "草稿和最近 " + HISTORY_LIMIT + " 条历史只保存在当前手机。",
                "快印不参与日记同步，也不会把内容上传到电脑服务。"
        ), cardLayout());

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private View settingsHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), dp(10));

        TextView back = navIcon("‹", "返回");
        back.setOnClickListener(v -> {
            buildUi();
            restoreDraft();
            focusEditor();
        });
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(40)));

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextColor(INK);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        return row;
    }

    private TextView settingsSectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(INK);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(14), 0, dp(8));
        return title;
    }

    private LinearLayout settingsCard(String titleText, String bodyText, String footText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(panelBackground());

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
        foot.setTextColor(ACCENT_DARK);
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
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    private View actionBar() {
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(dp(10), 0, dp(10), 0);

        Button minus = iconButton("A-", "减小字号");
        minus.setOnClickListener(v -> changeFontSize(-1));
        Button plus = iconButton("A+", "增大字号");
        plus.setOnClickListener(v -> changeFontSize(1));
        Button clear = iconButton("×", "清空");
        clear.setOnClickListener(v -> confirmClear());
        Button share = iconButton("↗", "分享");
        share.setOnClickListener(v -> shareText());
        formatButton = smallButton(formatButtonLabel());
        formatButton.setContentDescription("选择预览格式");
        formatButton.setOnClickListener(v -> showFormatChooser());
        Button print = primaryButton("预览");
        print.setOnClickListener(v -> previewText());

        tools.addView(minus, new LinearLayout.LayoutParams(dp(38), dp(38)));
        tools.addView(space(6), new LinearLayout.LayoutParams(dp(6), 1));
        tools.addView(plus, new LinearLayout.LayoutParams(dp(38), dp(38)));
        tools.addView(space(6), new LinearLayout.LayoutParams(dp(6), 1));
        tools.addView(clear, new LinearLayout.LayoutParams(dp(38), dp(38)));
        tools.addView(space(6), new LinearLayout.LayoutParams(dp(6), 1));
        tools.addView(share, new LinearLayout.LayoutParams(dp(38), dp(38)));
        tools.addView(space(6), new LinearLayout.LayoutParams(dp(6), 1));
        tools.addView(formatButton, new LinearLayout.LayoutParams(dp(58), dp(38)));
        tools.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        tools.addView(print, new LinearLayout.LayoutParams(0, dp(38), 1));
        return tools;
    }

    private void restoreDraft() {
        restoring = true;
        editor.setText(preferences.getString(DRAFT_KEY, ""));
        editor.setSelection(editor.getText().length());
        editor.setTextSize(EDITOR_TEXT_SP);
        restoring = false;
        updateStatus();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String incoming = null;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            incoming = text == null ? null : text.toString();
        } else if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            incoming = text == null ? null : text.toString();
        }
        if (incoming == null || incoming.trim().isEmpty()) {
            return;
        }
        editor.setText(incoming.trim());
        editor.setSelection(editor.getText().length());
        preferences.edit().putString(DRAFT_KEY, editor.getText().toString()).apply();
        Toast.makeText(this, "已接收分享文字", Toast.LENGTH_SHORT).show();
    }

    private void changeFontSize(int delta) {
        fontSize = normalizePrintFontSize(fontSize + delta);
        preferences.edit().putInt(FONT_SIZE_KEY, fontSize).apply();
        updateStatus();
    }

    private void updateStatus() {
        if (status == null || editor == null) {
            return;
        }
        int chars = editor.getText().toString().length();
        FormatDecision format = decideFormat(editor.getText().toString());
        if (formatButton != null) {
            formatButton.setText(formatButtonLabel());
        }
        status.setText(format.statusLabel + " / " + fontSize + "pt / " + chars + " 字");
    }

    private void previewText() {
        String text = editor.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "先输入要预览的文字", Toast.LENGTH_SHORT).show();
            return;
        }
        saveHistory(text);
        showPrintPreview(text);
    }

    private void showPrintPreview(String text) {
        FormatDecision format = decideFormat(text);
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        root.setPadding(dp(12), dp(12), dp(12), dp(10));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, 0, dp(10));

        TextView title = new TextView(this);
        title.setText(format.title + "预览");
        title.setTextColor(INK);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button close = smallButton("返回");
        close.setOnClickListener(v -> dialog.dismiss());
        Button print = primaryButton("打印");
        print.setOnClickListener(v -> printPreparedText(text, format.csv));
        bar.addView(close, new LinearLayout.LayoutParams(dp(66), dp(36)));
        bar.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        bar.addView(print, new LinearLayout.LayoutParams(dp(76), dp(36)));
        root.addView(bar, matchWrap());

        WebView preview = new WebView(this);
        preview.setBackgroundColor(PAPER);
        preview.getSettings().setJavaScriptEnabled(true);
        preview.getSettings().setBuiltInZoomControls(true);
        preview.getSettings().setDisplayZoomControls(false);
        preview.loadDataWithBaseURL(null, buildPreviewHtml(text, format.csv), "text/html", "UTF-8", null);
        root.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> preview.destroy());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void printPreparedText(String text, boolean asCsv) {
        printWebView = new WebView(this);
        printWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
                PrintDocumentAdapter adapter = view.createPrintDocumentAdapter("快印-" + fileDate.format(new Date()));
                printManager.print("快印-" + fileDate.format(new Date()), adapter, a4PrintAttributes());
            }
        });
        printWebView.loadDataWithBaseURL(null, buildPrintHtml(text, asCsv), "text/html", "UTF-8", null);
    }

    private PrintAttributes a4PrintAttributes() {
        return new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
    }

    private String buildPrintHtml(String text, boolean asCsv) {
        if (asCsv) {
            return buildCsvPrintHtml(text);
        }
        return "<!doctype html><html><head><meta charset='utf-8'><style>"
                + "@page{size:A4;margin:" + printMarginMm() + "mm;}"
                + "html,body{margin:0;padding:0;color:#111;background:white;}"
                + ".content{white-space:pre-wrap;word-break:break-word;" + contentCss() + "}"
                + "</style></head><body><main class='content'>"
                + escapeHtml(text)
                + "</main></body></html>";
    }

    private String buildPreviewHtml(String text, boolean asCsv) {
        if (asCsv) {
            return buildCsvPreviewHtml(text);
        }
        int sheetWidth = 794;
        int sheetHeight = 1123;
        int wrapPadding = 12;
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "html,body{margin:0;padding:0;background:#dedbd2;color:#111;}"
                + "body{font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;}"
                + ".wrap{box-sizing:border-box;width:100vw;padding:" + wrapPadding + "px;}"
                + ".sheet{position:relative;box-sizing:border-box;width:100%;height:calc((100vw - " + (wrapPadding * 2) + "px) * 297 / 210);margin:0 auto 14px;background:white;box-shadow:0 2px 12px rgba(0,0,0,.16);overflow:hidden;}"
                + ".sheet:last-child{margin-bottom:0;}"
                + ".content{position:absolute;left:0;top:0;box-sizing:border-box;width:" + sheetWidth + "px;height:" + sheetHeight + "px;padding:" + previewMarginPx() + "px;overflow:hidden;white-space:pre-wrap;word-break:break-word;transform-origin:0 0;" + contentCss() + "}"
                + "</style></head><body><div id='pages' class='wrap'></div><script>"
                + "const source=\"" + escapeJs(text) + "\";"
                + "const pages=document.getElementById('pages');"
                + "function fitPage(sheet,article){const scale=sheet.clientWidth/" + sheetWidth + ";article.style.transform='scale('+scale+')';}"
                + "function makePage(){const sheet=document.createElement('main');sheet.className='sheet';const article=document.createElement('article');article.className='content';sheet.appendChild(article);pages.appendChild(sheet);fitPage(sheet,article);return article;}"
                + "let article=makePage();"
                + "if(!source.length){article.textContent=' ';}"
                + "for(let i=0;i<source.length;i++){const ch=source.charAt(i);article.textContent+=ch;if(article.scrollHeight>article.clientHeight+1){article.textContent=article.textContent.slice(0,-1);article=makePage();article.textContent=ch;}}"
                + "window.addEventListener('resize',()=>{document.querySelectorAll('.sheet').forEach(sheet=>fitPage(sheet,sheet.firstChild));});"
                + "</script></body></html>";
    }

    private int printMarginMm() {
        return 12;
    }

    private int previewMarginPx() {
        return Math.round(printMarginMm() * 96f / 25.4f);
    }

    private String contentCss() {
        return "font-family:serif;font-size:" + fontSize + "pt;line-height:1.6;";
    }

    private String tableCss() {
        return "table{width:100%;border-collapse:collapse;font-family:sans-serif;font-size:" + Math.max(8, fontSize - 1) + "pt;line-height:1.35;}"
                + "th,td{border:1px solid #444;padding:5px 6px;text-align:left;vertical-align:top;word-break:break-word;}"
                + "th{font-weight:700;background:#f0f0f0;}";
    }

    private int normalizePrintFontSize(int value) {
        return Math.max(9, Math.min(14, value));
    }

    private void shareText() {
        String text = editor.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "先输入要分享的文字", Toast.LENGTH_SHORT).show();
            return;
        }
        saveHistory(text);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, "分享文字"));
    }

    private void confirmClear() {
        if (editor.getText().toString().trim().isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空当前文字？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    editor.setText("");
                    preferences.edit().putString(DRAFT_KEY, "").apply();
                    updateStatus();
                })
                .show();
    }

    private void saveHistory(String text) {
        try {
            JSONArray array = new JSONArray(preferences.getString(HISTORY_KEY, "[]"));
            JSONArray next = new JSONArray();
            JSONObject current = new JSONObject();
            current.put("text", text);
            current.put("savedAt", System.currentTimeMillis());
            next.put(current);
            for (int i = 0; i < array.length() && next.length() < HISTORY_LIMIT; i++) {
                HistoryEntry entry = historyEntryAt(array, i);
                if (!entry.text.equals(text) && !entry.text.trim().isEmpty()) {
                    JSONObject item = new JSONObject();
                    item.put("text", entry.text);
                    item.put("savedAt", entry.savedAt);
                    next.put(item);
                }
            }
            preferences.edit().putString(HISTORY_KEY, next.toString()).apply();
        } catch (JSONException ignored) {
            try {
                JSONObject current = new JSONObject();
                current.put("text", text);
                current.put("savedAt", System.currentTimeMillis());
                preferences.edit().putString(HISTORY_KEY, new JSONArray().put(current).toString()).apply();
            } catch (JSONException nested) {
                preferences.edit().putString(HISTORY_KEY, "[]").apply();
            }
        }
    }

    private void showHistory() {
        List<HistoryEntry> items = historyItems();
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(12));
        root.setBackgroundColor(PAPER);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("历史文字");
        title.setTextColor(INK);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = new TextView(this);
        sub.setText(items.isEmpty() ? "暂无可选内容" : items.size() + " 条可选择");
        sub.setTextColor(MUTED);
        sub.setTextSize(13);
        titleBox.addView(title, matchWrap());
        titleBox.addView(sub, matchWrap());
        header.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button back = smallButton("返回");
        back.setOnClickListener(v -> dialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(dp(66), dp(36)));
        root.addView(header, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(2), 0, dp(6));
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        if (items.isEmpty()) {
            list.addView(emptyHistoryView(), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        } else {
            for (int i = 0; i < items.size(); i++) {
                final HistoryEntry item = items.get(i);
                LinearLayout card = historyCard(item, i);
                card.setOnClickListener(v -> {
                    editor.setText(item.text);
                    editor.setSelection(editor.getText().length());
                    preferences.edit().putString(DRAFT_KEY, editor.getText().toString()).apply();
                    dialog.dismiss();
                    focusEditor();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.bottomMargin = dp(10);
                list.addView(card, lp);
            }
        }

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private View emptyHistoryView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(18), dp(52), dp(18), dp(52));
        box.setBackground(panelBackground());

        TextView title = new TextView(this);
        title.setText("还没有历史");
        title.setTextColor(INK);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        TextView sub = new TextView(this);
        sub.setText("预览、打印或分享后会保留最近 " + HISTORY_LIMIT + " 条文字");
        sub.setTextColor(MUTED);
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, 0);
        box.addView(title, matchWrap());
        box.addView(sub, matchWrap());
        return box;
    }

    private LinearLayout historyCard(HistoryEntry entry, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(panelBackground());
        card.setClickable(true);

        TextView meta = new TextView(this);
        meta.setText(entry.savedAt > 0 ? historyDate.format(new Date(entry.savedAt)) : "早期记录");
        meta.setTextColor(ACCENT_DARK);
        meta.setTextSize(12);
        meta.setTypeface(Typeface.DEFAULT_BOLD);

        TextView body = new TextView(this);
        body.setText(historyPreview(entry.text));
        body.setTextColor(INK);
        body.setTextSize(16);
        body.setLineSpacing(dp(2), 1.0f);
        body.setMaxLines(4);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(7), 0, dp(8));

        TextView foot = new TextView(this);
        foot.setText(historyFoot(entry.text, index));
        foot.setTextColor(MUTED);
        foot.setTextSize(12);

        card.addView(meta, matchWrap());
        card.addView(body, matchWrap());
        card.addView(foot, matchWrap());
        return card;
    }

    private String historyPreview(String text) {
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.isEmpty() ? "空白内容" : compact;
    }

    private String historyFoot(String text, int index) {
        int chars = text.length();
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        String kind = isLikelyCsv(text) ? "疑似CSV表格" : "文字";
        return (index == 0 ? "最近使用 · " : "") + kind + " · " + chars + " 字 / " + lines + " 行";
    }

    private List<HistoryEntry> historyItems() {
        List<HistoryEntry> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(HISTORY_KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                HistoryEntry entry = historyEntryAt(array, i);
                if (!entry.text.trim().isEmpty()) {
                    items.add(entry);
                }
            }
        } catch (JSONException ignored) {
        }
        return items;
    }

    private HistoryEntry historyEntryAt(JSONArray array, int index) {
        JSONObject object = array.optJSONObject(index);
        if (object != null) {
            return new HistoryEntry(object.optString("text", ""), object.optLong("savedAt", 0L));
        }
        return new HistoryEntry(array.optString(index, ""), 0L);
    }

    private void showFormatChooser() {
        String[] labels = {"自动识别", "按文字", "按CSV表格"};
        String[] modes = {FORMAT_AUTO, FORMAT_TEXT, FORMAT_CSV};
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(formatMode)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("预览格式")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    formatMode = modes[which];
                    preferences.edit().putString(FORMAT_MODE_KEY, formatMode).apply();
                    updateStatus();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String formatButtonLabel() {
        if (FORMAT_TEXT.equals(formatMode)) {
            return "文字";
        }
        if (FORMAT_CSV.equals(formatMode)) {
            return "CSV";
        }
        return "自动";
    }

    private String normalizeFormatMode(String mode) {
        if (FORMAT_TEXT.equals(mode) || FORMAT_CSV.equals(mode)) {
            return mode;
        }
        return FORMAT_AUTO;
    }

    private FormatDecision decideFormat(String text) {
        if (FORMAT_TEXT.equals(formatMode)) {
            return new FormatDecision(false, "文字", "文字");
        }
        if (FORMAT_CSV.equals(formatMode)) {
            return new FormatDecision(true, "CSV表格", "CSV表格");
        }
        boolean csv = isLikelyCsv(text);
        return new FormatDecision(csv, csv ? "自动: CSV表格" : "自动: A4文字", csv ? "CSV表格" : "A4");
    }

    private boolean isLikelyCsv(String text) {
        return csvProfile(text).likely;
    }

    private CsvProfile csvProfile(String text) {
        List<List<String>> rows = parseCsv(text);
        if (rows.size() < 2) {
            return new CsvProfile(false, rows, 0);
        }

        int commonColumnCount = mostCommonColumnCount(rows);
        if (commonColumnCount < 2) {
            return new CsvProfile(false, rows, commonColumnCount);
        }

        int multiColumnRows = 0;
        int consistentRows = 0;
        int nonEmptyCells = 0;
        int totalCells = 0;
        int proseCells = 0;
        for (List<String> row : rows) {
            if (row.size() >= 2) {
                multiColumnRows++;
            }
            if (row.size() == commonColumnCount) {
                consistentRows++;
            }
            for (String cell : row) {
                totalCells++;
                String value = cell.trim();
                if (!value.isEmpty()) {
                    nonEmptyCells++;
                }
                if (looksLikeProseCell(value)) {
                    proseCells++;
                }
            }
        }
        int requiredRows = Math.max(2, (rows.size() * 3 + 3) / 4);
        boolean enoughRows = rows.size() >= 3 || commonColumnCount >= 3;
        boolean enoughFilledCells = totalCells > 0 && nonEmptyCells * 100 >= totalCells * 60;
        boolean lowProse = proseCells < Math.max(2, totalCells / 3);
        boolean headerOrLargeTable = hasHeaderLikeFirstRow(rows, commonColumnCount) || rows.size() >= 4;
        boolean likely = enoughRows
                && multiColumnRows >= requiredRows
                && consistentRows >= requiredRows
                && enoughFilledCells
                && lowProse
                && headerOrLargeTable;
        return new CsvProfile(likely, rows, commonColumnCount);
    }

    private int mostCommonColumnCount(List<List<String>> rows) {
        int bestColumns = 0;
        int bestCount = 0;
        for (List<String> candidate : rows) {
            int columns = candidate.size();
            int count = 0;
            for (List<String> row : rows) {
                if (row.size() == columns) {
                    count++;
                }
            }
            if (columns > 1 && (count > bestCount || (count == bestCount && columns > bestColumns))) {
                bestColumns = columns;
                bestCount = count;
            }
        }
        return bestColumns;
    }

    private boolean hasHeaderLikeFirstRow(List<List<String>> rows, int columns) {
        if (rows.isEmpty() || rows.get(0).size() != columns) {
            return false;
        }
        int shortLabels = 0;
        int textLabels = 0;
        for (String cell : rows.get(0)) {
            String value = cell.trim();
            if (value.isEmpty() || value.length() > 40 || looksLikeProseCell(value)) {
                return false;
            }
            if (value.length() <= 24) {
                shortLabels++;
            }
            if (!looksNumeric(value)) {
                textLabels++;
            }
        }
        return shortLabels == columns && textLabels > 0;
    }

    private boolean looksLikeProseCell(String value) {
        if (value.length() > 80) {
            return true;
        }
        return value.endsWith(".")
                || value.endsWith("!")
                || value.endsWith("?")
                || value.endsWith("。")
                || value.endsWith("！")
                || value.endsWith("？")
                || value.contains(". ")
                || value.contains("，")
                || value.contains("。");
    }

    private boolean looksNumeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!(ch >= '0' && ch <= '9') && ch != '.' && ch != '-' && ch != '+' && ch != '%' && ch != '/') {
                return false;
            }
        }
        return true;
    }

    private String buildCsvPrintHtml(String text) {
        List<List<String>> rows = parseCsv(text);
        return "<!doctype html><html><head><meta charset='utf-8'><style>"
                + "@page{size:A4;margin:" + printMarginMm() + "mm;}"
                + "html,body{margin:0;padding:0;color:#111;background:white;}"
                + tableCss()
                + "</style></head><body>"
                + csvTableHtml(rows, 0, rows.size(), true)
                + "</body></html>";
    }

    private String buildCsvPreviewHtml(String text) {
        List<List<String>> rows = parseCsv(text);
        int sheetWidth = 794;
        int sheetHeight = 1123;
        int wrapPadding = 12;
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "html,body{margin:0;padding:0;background:#dedbd2;color:#111;}"
                + "body{font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;}"
                + ".wrap{box-sizing:border-box;width:100vw;padding:" + wrapPadding + "px;}"
                + ".sheet{position:relative;box-sizing:border-box;width:100%;height:calc((100vw - " + (wrapPadding * 2) + "px) * 297 / 210);margin:0 auto 14px;background:white;box-shadow:0 2px 12px rgba(0,0,0,.16);overflow:hidden;}"
                + ".sheet:last-child{margin-bottom:0;}"
                + ".content{position:absolute;left:0;top:0;box-sizing:border-box;width:" + sheetWidth + "px;height:" + sheetHeight + "px;padding:" + previewMarginPx() + "px;overflow:hidden;transform-origin:0 0;}"
                + tableCss()
                + "</style></head><body><div id='pages' class='wrap'></div><script>"
                + "const rows=" + csvRowsJs(rows) + ";"
                + "const pages=document.getElementById('pages');"
                + "function fit(){document.querySelectorAll('.sheet').forEach(sheet=>{const content=sheet.firstChild;content.style.transform='scale('+(sheet.clientWidth/" + sheetWidth + ")+')';});}"
                + "function addRow(table,row,head){const tr=document.createElement('tr');row.forEach(cell=>{const el=document.createElement(head?'th':'td');el.textContent=cell;tr.appendChild(el);});table.appendChild(tr);return tr;}"
                + "function makePage(){const sheet=document.createElement('main');sheet.className='sheet';const content=document.createElement('section');content.className='content';const table=document.createElement('table');content.appendChild(table);sheet.appendChild(content);pages.appendChild(sheet);if(rows.length){addRow(table,rows[0],true);}fit();return {content,table};}"
                + "let page=makePage();"
                + "for(let i=1;i<rows.length;i++){const tr=addRow(page.table,rows[i],false);if(page.content.scrollHeight>page.content.clientHeight+1 && page.table.rows.length>2){page.table.removeChild(tr);page=makePage();addRow(page.table,rows[i],false);}}"
                + "fit();window.addEventListener('resize',fit);"
                + "</script></body></html>";
    }

    private String csvTableHtml(List<List<String>> rows, int start, int end, boolean firstRowHeader) {
        StringBuilder html = new StringBuilder();
        html.append("<table>");
        for (int i = start; i < end; i++) {
            appendCsvRow(html, rows.get(i), firstRowHeader && i == 0);
        }
        html.append("</table>");
        return html.toString();
    }

    private void appendCsvRow(StringBuilder html, List<String> row, boolean header) {
        html.append("<tr>");
        String tag = header ? "th" : "td";
        for (String cell : row) {
            html.append("<").append(tag).append(">")
                    .append(escapeHtml(cell))
                    .append("</").append(tag).append(">");
        }
        html.append("</tr>");
    }

    private String csvRowsJs(List<List<String>> rows) {
        StringBuilder js = new StringBuilder();
        js.append("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                js.append(",");
            }
            js.append("[");
            List<String> row = rows.get(i);
            for (int j = 0; j < row.size(); j++) {
                if (j > 0) {
                    js.append(",");
                }
                js.append("\"").append(escapeJs(row.get(j))).append("\"");
            }
            js.append("]");
        }
        js.append("]");
        return js.toString();
    }

    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString());
                addCsvRow(rows, row);
                row = new ArrayList<>();
                cell.setLength(0);
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        row.add(cell.toString());
        addCsvRow(rows, row);
        return rows;
    }

    private void addCsvRow(List<List<String>> rows, List<String> row) {
        boolean hasValue = false;
        for (String cell : row) {
            if (!cell.trim().isEmpty()) {
                hasValue = true;
                break;
            }
        }
        if (hasValue) {
            rows.add(row);
        }
    }

    private static class FormatDecision {
        final boolean csv;
        final String statusLabel;
        final String title;

        FormatDecision(boolean csv, String statusLabel, String title) {
            this.csv = csv;
            this.statusLabel = statusLabel;
            this.title = title;
        }
    }

    private static class CsvProfile {
        final boolean likely;
        final List<List<String>> rows;
        final int columnCount;

        CsvProfile(boolean likely, List<List<String>> rows, int columnCount) {
            this.likely = likely;
            this.rows = rows;
            this.columnCount = columnCount;
        }
    }

    private static class HistoryEntry {
        final String text;
        final long savedAt;

        HistoryEntry(String text, long savedAt) {
            this.text = text;
            this.savedAt = savedAt;
        }
    }

    private void focusEditor() {
        editor.postDelayed(() -> {
            editor.requestFocus();
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            manager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) {
            return;
        }
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setBackground(buttonBackground(ACCENT, ACCENT));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(ACCENT_DARK);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setBackground(buttonBackground(CARD, LINE));
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(ACCENT_DARK);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(buttonBackground(CARD, LINE));
        return button;
    }

    private TextView navIcon(String text, String description) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(30);
        view.setTextColor(ACCENT_DARK);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        return view;
    }

    private Button iconButton(String text, String description) {
        Button button = smallButton(text);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setContentDescription(description);
        return button;
    }

    private GradientDrawable buttonBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(7));
        return drawable;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(CARD);
        drawable.setStroke(dp(1), LINE);
        drawable.setCornerRadius(dp(8));
        return drawable;
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
            paint.setColor(ACCENT_DARK);
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8.0;
                float x1 = cx + (float) Math.cos(angle) * outer;
                float y1 = cy + (float) Math.sin(angle) * outer;
                float x2 = cx + (float) Math.cos(angle) * (outer + dp(3));
                float y2 = cy + (float) Math.sin(angle) * (outer + dp(3));
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
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

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeJs(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
    }

    private View space(int width) {
        View view = new View(this);
        view.setLayoutParams(new ViewGroup.LayoutParams(dp(width), 1));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
