package com.minimetronome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "metronome";
    private static final String BPM_KEY = "bpm";
    private static final String METER_KEY = "meter";
    private static final int MIN_BPM = 40;
    private static final int MAX_BPM = 220;
    private static final Meter[] METERS = new Meter[] {
            new Meter("单拍", 1, false),
            new Meter("2 / 4", 2, true),
            new Meter("3 / 4", 3, true),
            new Meter("4 / 4", 4, true),
            new Meter("6 / 8", 6, true)
    };

    private static final int INK = Color.rgb(47, 41, 37);
    private static final int MUTED = Color.rgb(117, 104, 95);
    private static final int PAPER = Color.rgb(253, 239, 239);
    private static final int CARD = Color.rgb(255, 249, 246);
    private static final int LINE = Color.rgb(218, 208, 194);
    private static final int ACCENT = Color.rgb(138, 113, 94);
    private static final int ACCENT_WARM = Color.rgb(205, 187, 167);
    private static final int ACCENT_GOLD = Color.rgb(200, 161, 90);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private SoundEngine soundEngine;
    private TextView bpmText;
    private TextView tempoText;
    private TextView beatText;
    private SeekBar bpmSeek;
    private Button playButton;
    private Button meterButton;
    private int bpm = 90;
    private int beat = 0;
    private int meterIndex = 0;
    private boolean playing = false;
    private boolean inSettings = false;
    private long nextTickAt = 0L;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!playing) return;
            Meter meter = currentMeter();
            int beatInMeasure = beat % meter.beats;
            boolean downbeat = meter.accentDownbeat && beatInMeasure == 0;
            soundEngine.play(downbeat);
            beatText.setText(meterLabel(meter, beatInMeasure));
            beat++;
            nextTickAt += Math.max(60, Math.round(60000f / bpm));
            handler.postAtTime(this, nextTickAt);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        bpm = clamp(preferences.getInt(BPM_KEY, 90), MIN_BPM, MAX_BPM);
        meterIndex = clamp(preferences.getInt(METER_KEY, 0), 0, METERS.length - 1);
        soundEngine = new SoundEngine();

        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        buildUi();
        updateTempoViews();
    }

    @Override
    protected void onDestroy() {
        stop();
        soundEngine.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (inSettings) {
            buildUi();
            updateTempoViews();
            return;
        }
        super.onBackPressed();
    }

    private void buildUi() {
        inSettings = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(PAPER);
        setContentView(root);

        root.addView(header(), matchWrap());

        ScrollView workScroll = new ScrollView(this);
        workScroll.setFillViewport(true);
        LinearLayout work = new LinearLayout(this);
        work.setOrientation(LinearLayout.VERTICAL);
        workScroll.addView(work, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("调好速度，点开始。");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        work.addView(subtitle, matchWrap());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(panelBackground());
        work.addView(panel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        beatText = new TextView(this);
        beatText.setText(currentMeter().name);
        beatText.setTextColor(ACCENT);
        beatText.setTextSize(18);
        beatText.setTypeface(Typeface.DEFAULT_BOLD);
        beatText.setGravity(Gravity.CENTER);
        beatText.setBackground(pillBackground(Color.rgb(244, 223, 208), LINE));
        beatText.setPadding(dp(14), dp(7), dp(14), dp(7));
        panel.addView(beatText, matchWrap());

        bpmText = new TextView(this);
        bpmText.setTextColor(INK);
        bpmText.setTextSize(68);
        bpmText.setTypeface(Typeface.DEFAULT_BOLD);
        bpmText.setGravity(Gravity.CENTER);
        bpmText.setIncludeFontPadding(false);
        bpmText.setPadding(0, dp(20), 0, 0);
        panel.addView(bpmText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView bpmLabel = new TextView(this);
        bpmLabel.setText("BPM");
        bpmLabel.setTextColor(MUTED);
        bpmLabel.setTextSize(14);
        bpmLabel.setTypeface(Typeface.DEFAULT_BOLD);
        bpmLabel.setGravity(Gravity.CENTER);
        panel.addView(bpmLabel, matchWrap());

        tempoText = new TextView(this);
        tempoText.setTextColor(ACCENT_WARM);
        tempoText.setTextSize(22);
        tempoText.setTypeface(Typeface.DEFAULT_BOLD);
        tempoText.setGravity(Gravity.CENTER);
        tempoText.setPadding(0, dp(14), 0, dp(18));
        panel.addView(tempoText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        bpmSeek = new SeekBar(this);
        bpmSeek.setMax(MAX_BPM - MIN_BPM);
        bpmSeek.setProgress(bpm - MIN_BPM);
        bpmSeek.setPadding(0, 0, 0, dp(18));
        bpmSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) setBpm(MIN_BPM + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(bpmSeek, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout stepRow = new LinearLayout(this);
        stepRow.setOrientation(LinearLayout.HORIZONTAL);
        stepRow.setGravity(Gravity.CENTER);
        stepRow.addView(stepButton("-5", -5), new LinearLayout.LayoutParams(0, dp(44), 1));
        stepRow.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        stepRow.addView(stepButton("-1", -1), new LinearLayout.LayoutParams(0, dp(44), 1));
        stepRow.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        stepRow.addView(stepButton("+1", 1), new LinearLayout.LayoutParams(0, dp(44), 1));
        stepRow.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        stepRow.addView(stepButton("+5", 5), new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(stepRow, matchWrap());

        meterButton = new Button(this);
        meterButton.setTextSize(16);
        meterButton.setTypeface(Typeface.DEFAULT_BOLD);
        meterButton.setTextColor(INK);
        meterButton.setBackground(secondaryButtonBackground());
        meterButton.setOnClickListener(v -> showMeterPicker());
        LinearLayout.LayoutParams meterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        meterParams.setMargins(0, dp(14), 0, 0);
        panel.addView(meterButton, meterParams);

        playButton = new Button(this);
        playButton.setText("开始");
        playButton.setTextSize(18);
        playButton.setTypeface(Typeface.DEFAULT_BOLD);
        playButton.setTextColor(Color.rgb(255, 250, 242));
        playButton.setBackground(primaryButtonBackground());
        playButton.setOnClickListener(v -> {
            if (playing) {
                stop();
            } else {
                start();
            }
        });
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        playParams.setMargins(0, dp(18), 0, 0);
        panel.addView(playButton, playParams);

        root.addView(workScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("节拍器");
        title.setTextColor(INK);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        View settings = new SettingsIconButton(this);
        settings.setContentDescription("设置");
        settings.setOnClickListener(v -> showSettingsPage());
        row.addView(settings, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return row;
    }

    private void showSettingsPage() {
        stop();
        inSettings = true;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(PAPER);
        setContentView(root);

        root.addView(settingsHeader(), matchWrap());

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(18));
        scroll.addView(content, matchWrap());

        content.addView(settingsSectionTitle("速度"), matchWrap());
        content.addView(settingsCard(
                "当前速度",
                bpm + " BPM / " + tempoName(bpm),
                "速度会保存在当前手机，下次打开继续使用。"
        ), cardLayout());

        content.addView(settingsSectionTitle("拍号"), matchWrap());
        content.addView(settingsCard(
                "当前拍号：" + currentMeter().name,
                "播放页的拍号按钮会打开列表，可选择单拍、2/4、3/4、4/4、6/8。",
                "单拍模式没有强弱拍，适合练习稳定速度。"
        ), cardLayout());

        content.addView(settingsSectionTitle("关于"), matchWrap());
        content.addView(settingsCard(
                "节拍器",
                "简洁手机节拍器，支持 BPM 调速、意大利语速度名称和常用拍号。",
                appVersionText()
        ), cardLayout());

        content.addView(settingsSectionTitle("数据管理"), matchWrap());
        content.addView(settingsCard(
                "本机偏好",
                "节拍器只保存当前手机上的速度和拍号偏好。",
                "节拍器不连接电脑服务，不参与同步，只保存本机速度和拍号偏好。"
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
        row.setPadding(0, 0, 0, dp(12));

        TextView back = navIcon("‹", "返回");
        back.setOnClickListener(v -> {
            buildUi();
            updateTempoViews();
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
        foot.setTextColor(ACCENT);
        foot.setTextSize(13);
        foot.setLineSpacing(dp(2), 1.0f);
        foot.setPadding(0, dp(8), 0, 0);
        card.addView(foot, matchWrap());
        return card;
    }

    private String appVersionText() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return "节拍器 " + info.versionName + " / " + versionCode;
        } catch (Exception ignored) {
            return "节拍器";
        }
    }

    private LinearLayout.LayoutParams cardLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    private Button stepButton(String text, int delta) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(INK);
        button.setBackground(secondaryButtonBackground());
        button.setOnClickListener(v -> setBpm(bpm + delta));
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(ACCENT);
        button.setBackground(secondaryButtonBackground());
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

    private void start() {
        playing = true;
        beat = 0;
        nextTickAt = android.os.SystemClock.uptimeMillis();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        playButton.setText("停止");
        tick.run();
    }

    private void stop() {
        playing = false;
        handler.removeCallbacks(tick);
        beat = 0;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        beatText.setText(currentMeter().name);
        if (playButton != null) playButton.setText("开始");
    }

    private void setBpm(int value) {
        bpm = clamp(value, MIN_BPM, MAX_BPM);
        preferences.edit().putInt(BPM_KEY, bpm).apply();
        if (bpmSeek != null && bpmSeek.getProgress() != bpm - MIN_BPM) {
            bpmSeek.setProgress(bpm - MIN_BPM);
        }
        updateTempoViews();
    }

    private void updateTempoViews() {
        bpmText.setText(String.valueOf(bpm));
        tempoText.setText(tempoName(bpm));
        if (meterButton != null) {
            meterButton.setText("拍号：" + currentMeter().name);
        }
        if (!playing && beatText != null) {
            beatText.setText(currentMeter().name);
        }
    }

    private void showMeterPicker() {
        Dialog dialog = new Dialog(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(panelBackground());

        TextView title = new TextView(this);
        title.setText("选择拍号");
        title.setTextColor(INK);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("单拍不会强调首拍，其它拍号会强调每小节第一拍。");
        hint.setTextColor(MUTED);
        hint.setTextSize(13);
        hint.setLineSpacing(dp(2), 1.0f);
        hint.setPadding(0, dp(6), 0, dp(12));
        card.addView(hint, matchWrap());

        for (int i = 0; i < METERS.length; i++) {
            final int index = i;
            TextView option = meterOption(METERS[i].name, index == meterIndex);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setOnClickListener(v -> {
                selectMeter(index);
                dialog.dismiss();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
            );
            params.setMargins(0, 0, 0, dp(7));
            card.addView(option, params);
        }

        TextView close = meterOption("取消", false);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(ACCENT);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        closeParams.setMargins(0, dp(2), 0, 0);
        card.addView(close, closeParams);

        dialog.setContentView(card);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(360)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private TextView meterOption(String text, boolean selected) {
        TextView option = new TextView(this);
        option.setText(selected ? "✓  " + text : text);
        option.setTextSize(16);
        option.setTypeface(Typeface.DEFAULT_BOLD);
        option.setTextColor(selected ? ACCENT : INK);
        option.setPadding(dp(14), 0, dp(14), 0);
        option.setBackground(selected
                ? optionBackground(Color.rgb(244, 223, 208), ACCENT)
                : optionBackground(CARD, LINE));
        return option;
    }

    private void selectMeter(int index) {
        meterIndex = clamp(index, 0, METERS.length - 1);
        preferences.edit().putInt(METER_KEY, meterIndex).apply();
        beat = 0;
        updateTempoViews();
    }

    private Meter currentMeter() {
        return METERS[meterIndex];
    }

    private String meterLabel(Meter meter, int beatInMeasure) {
        if (meter.beats == 1) return "单拍";
        return (beatInMeasure + 1) + " / " + meter.beats;
    }

    private String tempoName(int value) {
        if (value < 45) return "Larghissimo";
        if (value < 50) return "Grave";
        if (value < 56) return "Largo";
        if (value < 66) return "Larghetto";
        if (value < 76) return "Adagio";
        if (value < 84) return "Andante";
        if (value < 98) return "Andantino";
        if (value < 110) return "Moderato";
        if (value < 120) return "Allegretto";
        if (value < 156) return "Allegro";
        if (value < 176) return "Vivace";
        if (value < 200) return "Presto";
        return "Prestissimo";
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(CARD);
        drawable.setStroke(dp(1), LINE);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable pillBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable primaryButtonBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { ACCENT, Color.rgb(111, 88, 72) }
        );
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable secondaryButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(255, 249, 246));
        drawable.setStroke(dp(1), LINE);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable optionBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private View space(int size) {
        View view = new View(this);
        view.setMinimumWidth(size);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Meter {
        final String name;
        final int beats;
        final boolean accentDownbeat;

        Meter(String name, int beats, boolean accentDownbeat) {
            this.name = name;
            this.beats = beats;
            this.accentDownbeat = accentDownbeat;
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

    private static final class SoundEngine {
        private static final int SAMPLE_RATE = 44100;
        private final short[] strongClick = makeClick(1120, 0.62);
        private final short[] softClick = makeClick(820, 0.5);
        private final AudioTrack strongTrack = createTrack(strongClick);
        private final AudioTrack softTrack = createTrack(softClick);

        void play(boolean strong) {
            AudioTrack track = strong ? strongTrack : softTrack;
            track.pause();
            track.flush();
            track.setPlaybackHeadPosition(0);
            track.play();
        }

        void shutdown() {
            strongTrack.release();
            softTrack.release();
        }

        private static AudioTrack createTrack(short[] samples) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            int minBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            AudioTrack track = new AudioTrack(
                    attributes,
                    format,
                    Math.max(minBuffer, samples.length * 2),
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );
            track.write(samples, 0, samples.length);
            return track;
        }

        private static short[] makeClick(double frequency, double volume) {
            int count = (int) (SAMPLE_RATE * 0.055);
            short[] data = new short[count];
            for (int i = 0; i < count; i++) {
                double t = i / (double) SAMPLE_RATE;
                double envelope = Math.exp(-t * 72);
                double tone = Math.sin(2 * Math.PI * frequency * t);
                double overtone = 0.45 * Math.sin(2 * Math.PI * frequency * 2.01 * t);
                double body = 0.18 * Math.sin(2 * Math.PI * 260 * t);
                data[i] = (short) (Short.MAX_VALUE * volume * envelope * (tone + overtone + body) / 1.63);
            }
            return data;
        }
    }
}
