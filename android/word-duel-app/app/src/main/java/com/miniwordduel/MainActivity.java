package com.miniwordduel;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "word_duel";
    private static final int ROUND_SIZE = 10;
    private static final int QUESTION_SECONDS = 12;

    private static final int INK = Color.rgb(47, 41, 37);
    private static final int MUTED = Color.rgb(117, 104, 95);
    private static final int PAPER = Color.rgb(253, 239, 239);
    private static final int CARD = Color.rgb(255, 249, 246);
    private static final int LINE = Color.rgb(218, 208, 194);
    private static final int ACCENT = Color.rgb(138, 113, 94);
    private static final int ACCENT_DARK = Color.rgb(111, 88, 72);
    private static final int WARM = Color.rgb(205, 187, 167);
    private static final int GOLD = Color.rgb(200, 161, 90);
    private static final int GREEN = Color.rgb(42, 132, 88);
    private static final int RED = Color.rgb(190, 73, 58);

    private static final Question[] QUESTIONS = new Question[] {
            q("affect_effect_1", "affect", "effect", "affect", "这场雨会影响比赛结果。", "The rain will ___ the result of the game.", "affect 通常作动词，表示“影响”；effect 多作名词，表示“结果、影响”。", 1, "词性"),
            q("affect_effect_2", "affect", "effect", "effect", "这项政策产生了明显效果。", "The new policy had a clear ___ on prices.", "effect 在这里是名词，表示“影响、效果”。", 1, "词性"),
            q("economic_economical_1", "economic", "economical", "economic", "这个城市发展很快。", "The city has seen rapid ___ growth.", "economic 表示“经济的、经济方面的”。", 1, "形容词"),
            q("economic_economical_2", "economic", "economical", "economical", "这辆车很省油。", "This car is ___ to run.", "economical 表示“节约的、省钱的”。", 1, "形容词"),
            q("historic_historical_1", "historic", "historical", "historic", "这是一次有历史意义的会议。", "It was a ___ meeting for the country.", "historic 强调“有历史意义的”。", 1, "形容词"),
            q("historic_historical_2", "historic", "historical", "historical", "他喜欢读历史小说。", "He likes reading ___ novels.", "historical 表示“历史上的、与历史有关的”。", 1, "形容词"),
            q("rise_raise_1", "rise", "raise", "rise", "太阳从东方升起。", "The sun will ___ in the east.", "rise 是不及物动词，表示“升起、上升”。", 1, "动词"),
            q("rise_raise_2", "rise", "raise", "raise", "老师让学生举手。", "The teacher asked students to ___ their hands.", "raise 是及物动词，表示“举起、提高”。", 1, "动词"),
            q("alone_lonely_1", "alone", "lonely", "alone", "他一个人住，但并不难过。", "He lives ___, but he does not feel sad.", "alone 表示“独自一人”，不一定孤独。", 1, "形容词"),
            q("alone_lonely_2", "alone", "lonely", "lonely", "搬到新城市后她感到孤独。", "She felt ___ after moving to a new city.", "lonely 强调情感上的“孤独”。", 1, "形容词"),
            q("adapt_adopt_1", "adapt", "adopt", "adapt", "学生需要适应新学校。", "Students need to ___ to the new school.", "adapt 表示“适应、改编”。", 2, "形近"),
            q("adapt_adopt_2", "adapt", "adopt", "adopt", "他们决定采纳这个计划。", "They decided to ___ the plan.", "adopt 表示“采纳、收养”。", 2, "形近"),
            q("aboard_abroad_1", "aboard", "abroad", "aboard", "所有乘客都已经登机。", "All passengers are now ___ the plane.", "aboard 表示“在船、飞机等交通工具上”。", 2, "形近"),
            q("aboard_abroad_2", "aboard", "abroad", "abroad", "她计划明年出国学习。", "She plans to study ___ next year.", "abroad 表示“在国外、到国外”。", 2, "形近"),
            q("accept_except_1", "accept", "except", "accept", "请接受我的道歉。", "Please ___ my apology.", "accept 是动词，表示“接受”。", 1, "形近"),
            q("accept_except_2", "accept", "except", "except", "除了汤姆，大家都来了。", "Everyone came ___ Tom.", "except 是介词，表示“除了”。", 1, "形近"),
            q("advise_suggest_1", "advise", "suggest", "advise", "医生建议他多休息。", "The doctor ___ him to take more rest.", "advise 可接 somebody to do；suggest 通常不这样接。", 2, "搭配"),
            q("advise_suggest_2", "advise", "suggest", "suggest", "她建议早点出发。", "She ___ leaving earlier.", "suggest 常接动名词或从句，表示“建议”。", 2, "搭配"),
            q("continual_continuous_1", "continual", "continuous", "continual", "不断的打扰让他无法学习。", "The ___ interruptions made it hard to study.", "continual 常指反复发生，中间可有间断。", 2, "形容词"),
            q("continual_continuous_2", "continual", "continuous", "continuous", "雨连续下了三个小时。", "There was ___ rain for three hours.", "continuous 强调不间断地持续。", 2, "形容词"),
            q("principal_principle_1", "principal", "principle", "principal", "校长今天发表了讲话。", "The ___ gave a speech today.", "principal 可作名词“校长”，也可表示“主要的”。", 2, "形近"),
            q("principal_principle_2", "principal", "principle", "principle", "诚实是一个重要原则。", "Honesty is an important ___.", "principle 表示“原则、原理”。", 2, "形近"),
            q("personal_personnel_1", "personal", "personnel", "personal", "不要问太私人的问题。", "Do not ask too many ___ questions.", "personal 表示“个人的、私人的”。", 2, "形近"),
            q("personal_personnel_2", "personal", "personnel", "personnel", "公司正在招聘人事经理。", "The company is hiring a ___ manager.", "personnel 表示“人员、人事部门”。", 2, "形近"),
            q("compliment_complement_1", "compliment", "complement", "compliment", "她称赞了他的演讲。", "She paid him a ___ on his speech.", "compliment 表示“称赞、赞美”。", 3, "形近"),
            q("compliment_complement_2", "compliment", "complement", "complement", "这条领带很配他的西装。", "The tie is a perfect ___ to his suit.", "complement 表示“补充物、相配的东西”。", 3, "形近"),
            q("desert_dessert_1", "desert", "dessert", "desert", "骆驼适合生活在沙漠里。", "Camels are suited to life in the ___.", "desert 是“沙漠”。", 1, "拼写"),
            q("desert_dessert_2", "desert", "dessert", "dessert", "饭后我们吃了甜点。", "We had ice cream for ___.", "dessert 是“甜点”，有两个 s。", 1, "拼写"),
            q("weather_whether_1", "weather", "whether", "weather", "今天的天气很好。", "The ___ is nice today.", "weather 表示“天气”。", 1, "形近"),
            q("weather_whether_2", "weather", "whether", "whether", "我不知道他是否会来。", "I do not know ___ he will come.", "whether 表示“是否”。", 1, "形近"),
            q("loose_lose_1", "loose", "lose", "loose", "这件外套太宽松了。", "This coat is too ___.", "loose 是形容词，表示“松的、宽松的”。", 1, "拼写"),
            q("loose_lose_2", "loose", "lose", "lose", "不要丢失你的钥匙。", "Do not ___ your keys.", "lose 是动词，表示“丢失、失败”。", 1, "拼写"),
            q("quiet_quite_1", "quiet", "quite", "quiet", "图书馆里很安静。", "The library is very ___.", "quiet 表示“安静的”。", 1, "拼写"),
            q("quiet_quite_2", "quiet", "quite", "quite", "这道题相当难。", "The problem is ___ difficult.", "quite 表示“相当、十分”。", 1, "拼写"),
            q("stationary_stationery_1", "stationary", "stationery", "stationary", "汽车静止了几分钟。", "The car remained ___ for several minutes.", "stationary 表示“静止的、不动的”。", 3, "拼写"),
            q("stationary_stationery_2", "stationary", "stationery", "stationery", "她买了一些文具。", "She bought some ___.", "stationery 表示“文具”。", 3, "拼写"),
            q("maybe_may_be_1", "maybe", "may be", "maybe", "也许他已经回家了。", "___ he has gone home.", "maybe 是副词，表示“也许”。", 1, "搭配"),
            q("maybe_may_be_2", "maybe", "may be", "may be", "他可能在图书馆。", "He ___ in the library.", "may be 是情态动词加 be，表示“可能是/在”。", 1, "搭配"),
            q("lie_lay_1", "lie", "lay", "lie", "他想躺下休息。", "He wants to ___ down and rest.", "lie 表示“躺”，常用 lie down。", 2, "动词"),
            q("lie_lay_2", "lie", "lay", "lay", "请把书放在桌上。", "Please ___ the book on the desk.", "lay 表示“放置”，是及物动词。", 2, "动词"),
            q("pour_poor_1", "pour", "poor", "pour", "请把水倒进杯子里。", "Please ___ the water into the cup.", "pour 是动词，表示“倒、倾泻”。", 1, "音近"),
            q("pour_poor_2", "pour", "poor", "poor", "这个地区很贫穷。", "This is a ___ area.", "poor 表示“贫穷的、差的”。", 1, "音近"),
            q("sensitive_sensible_1", "sensitive", "sensible", "sensitive", "她对批评很敏感。", "She is very ___ to criticism.", "sensitive 表示“敏感的”。", 2, "形容词"),
            q("sensitive_sensible_2", "sensitive", "sensible", "sensible", "这是一个明智的决定。", "It is a ___ decision.", "sensible 表示“明智的、合理的”。", 2, "形容词"),
            q("efficient_effective_1", "efficient", "effective", "efficient", "这个方法节省时间和精力。", "This method is very ___.", "efficient 强调效率高、少浪费。", 2, "形容词"),
            q("efficient_effective_2", "efficient", "effective", "effective", "这个药很有效。", "The medicine is ___.", "effective 强调能达到效果。", 2, "形容词"),
            q("worth_worthy_1", "worth", "worthy", "worth", "这本书值得一读。", "This book is ___ reading.", "worth 后常接名词或动名词。", 2, "搭配"),
            q("worth_worthy_2", "worth", "worthy", "worthy", "这个问题值得认真关注。", "The problem is ___ of serious attention.", "worthy 常与 of 搭配，表示“值得的”。", 2, "搭配"),
            q("late_lately_1", "late", "lately", "late", "他今天早上迟到了。", "He was ___ this morning.", "late 可作形容词或副词，表示“迟、晚”。", 1, "副词"),
            q("late_lately_2", "late", "lately", "lately", "最近你见过他吗？", "Have you seen him ___?", "lately 表示“最近、近来”。", 1, "副词"),
            q("hard_hardly_1", "hard", "hardly", "hard", "他学习很努力。", "He studies ___.", "hard 可作副词，表示“努力地”。", 1, "副词"),
            q("hard_hardly_2", "hard", "hardly", "hardly", "我几乎听不见他说话。", "I can ___ hear him.", "hardly 表示“几乎不”。", 1, "副词"),
            q("near_nearly_1", "near", "nearly", "near", "学校在公园附近。", "The school is ___ the park.", "near 表示“在附近、接近”。", 1, "副词"),
            q("near_nearly_2", "near", "nearly", "nearly", "差不多所有学生都到了。", "___ all the students have arrived.", "nearly 表示“几乎、差不多”。", 1, "副词"),
            q("successful_successive_1", "successful", "successive", "successful", "这次活动非常成功。", "The activity was very ___.", "successful 表示“成功的”。", 2, "形容词"),
            q("successful_successive_2", "successful", "successive", "successive", "他们连续三年赢得比赛。", "They won the game for three ___ years.", "successive 表示“连续的、接连的”。", 2, "形容词"),
            q("considerable_considerate_1", "considerable", "considerate", "considerable", "这项工程花费巨大。", "The project cost a ___ amount of money.", "considerable 表示“相当大的”。", 3, "形容词"),
            q("considerable_considerate_2", "considerable", "considerate", "considerate", "他总是很体贴。", "He is always ___ to others.", "considerate 表示“体贴的、考虑周到的”。", 3, "形容词")
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private SharedPreferences preferences;
    private LinearLayout root;
    private List<Question> activeDeck = new ArrayList<>();
    private TextView progressText;
    private TextView scoreText;
    private TextView timerText;
    private TextView zhPromptText;
    private TextView sentenceText;
    private TextView resultText;
    private TextView explanationText;
    private Button optionOne;
    private Button optionTwo;
    private Button nextButton;
    private int questionIndex = 0;
    private int score = 0;
    private int roundCorrect = 0;
    private int streak = 0;
    private int bestStreak = 0;
    private int secondsLeft = QUESTION_SECONDS;
    private boolean answered = false;
    private boolean inSubPage = false;

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (answered || activeDeck.isEmpty()) return;
            secondsLeft--;
            updateTimerText();
            if (secondsLeft <= 0) {
                answer(null);
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        buildHome();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (inSubPage) {
            handler.removeCallbacks(timerTick);
            buildHome();
            return;
        }
        super.onBackPressed();
    }

    private void buildHome() {
        inSubPage = false;
        handler.removeCallbacks(timerTick);
        root = baseRoot();
        setContentView(root);

        root.addView(header("相似词决斗", false, true), matchWrap());
        TextView subtitle = label("看句子选词，错题会自动留下来反复打。", 14, MUTED);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle, matchWrap());

        LinearLayout hero = panel();
        hero.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView deck = label(String.format(Locale.CHINA, "%d 道题 · %d 组词对", QUESTIONS.length, pairCount()), 13, MUTED);
        TextView record = label(recordLine(), 28, INK);
        record.setTypeface(Typeface.DEFAULT_BOLD);
        record.setPadding(0, dp(6), 0, dp(4));
        TextView hint = label("每局 10 题，每题 12 秒。先把最常错的词打掉。", 14, MUTED);
        hero.addView(deck, matchWrap());
        hero.addView(record, matchWrap());
        hero.addView(hint, matchWrap());
        root.addView(hero, matchWrapWithBottom(14));

        root.addView(bigButton("开始闯关", ACCENT, Color.WHITE, v -> startRound(false)), buttonLayout());
        root.addView(bigButton("错题复习", WARM, Color.WHITE, v -> startRound(true)), buttonLayout());
        root.addView(bigButton("词对图鉴", CARD, INK, v -> showAtlas()), buttonLayout());

    }

    private void startRound(boolean reviewOnly) {
        activeDeck = reviewOnly ? reviewDeck() : challengeDeck();
        if (activeDeck.isEmpty()) {
            showEmptyReview();
            return;
        }
        questionIndex = 0;
        score = 0;
        roundCorrect = 0;
        streak = 0;
        bestStreak = 0;
        buildQuiz(reviewOnly);
        showQuestion();
    }

    private void buildQuiz(boolean reviewOnly) {
        inSubPage = true;
        root = baseRoot();
        setContentView(root);
        root.addView(header(reviewOnly ? "错题复习" : "闯关模式", true, true), matchWrap());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        progressText = chip("第 1/10 题", ACCENT, Color.WHITE);
        scoreText = chip("0 分", CARD, INK);
        timerText = chip("12s", GOLD, INK);
        top.addView(progressText, new LinearLayout.LayoutParams(0, dp(38), 1));
        top.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        top.addView(scoreText, new LinearLayout.LayoutParams(0, dp(38), 1));
        top.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        top.addView(timerText, new LinearLayout.LayoutParams(0, dp(38), 1));
        root.addView(top, matchWrapWithBottom(12));

        LinearLayout card = panel();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        zhPromptText = label("", 22, INK);
        zhPromptText.setTypeface(Typeface.DEFAULT_BOLD);
        sentenceText = label("", 19, ACCENT_DARK);
        sentenceText.setPadding(0, dp(14), 0, 0);
        card.addView(zhPromptText, matchWrap());
        card.addView(sentenceText, matchWrap());
        root.addView(card, matchWrapWithBottom(14));

        optionOne = optionButton();
        optionTwo = optionButton();
        root.addView(optionOne, buttonLayout());
        root.addView(optionTwo, buttonLayout());

        LinearLayout feedback = panel();
        feedback.setPadding(dp(14), dp(12), dp(14), dp(12));
        resultText = label("", 18, INK);
        resultText.setTypeface(Typeface.DEFAULT_BOLD);
        explanationText = label("", 15, MUTED);
        explanationText.setPadding(0, dp(6), 0, 0);
        feedback.addView(resultText, matchWrap());
        feedback.addView(explanationText, matchWrap());
        root.addView(feedback, matchWrapWithBottom(12));

        nextButton = bigButton("下一题", ACCENT, Color.WHITE, v -> nextQuestion());
        root.addView(nextButton, buttonLayout());
    }

    private void showQuestion() {
        handler.removeCallbacks(timerTick);
        answered = false;
        secondsLeft = QUESTION_SECONDS;
        Question q = activeDeck.get(questionIndex);
        boolean answerFirst = random.nextBoolean();
        String first = answerFirst ? q.answer : q.other();
        String second = answerFirst ? q.other() : q.answer;

        progressText.setText(String.format(Locale.CHINA, "第 %d/%d 题", questionIndex + 1, activeDeck.size()));
        scoreText.setText(score + " 分");
        updateTimerText();
        zhPromptText.setText(q.zhPrompt);
        sentenceText.setText(q.enSentence);
        resultText.setText("选出最自然的英文词。");
        resultText.setTextColor(INK);
        explanationText.setText(" ");
        nextButton.setVisibility(View.GONE);

        configureOption(optionOne, first, q);
        configureOption(optionTwo, second, q);
        handler.postDelayed(timerTick, 1000);
    }

    private void configureOption(Button button, String word, Question q) {
        button.setText(word);
        button.setEnabled(true);
        button.setTextColor(INK);
        button.setBackground(optionBackground(false, false, false));
        button.setOnClickListener(v -> answer(word));
    }

    private void answer(String choice) {
        if (answered) return;
        answered = true;
        handler.removeCallbacks(timerTick);
        Question q = activeDeck.get(questionIndex);
        boolean correct = q.answer.equals(choice);
        if (correct) {
            roundCorrect++;
            score += 10 + Math.min(streak, 5) * 2;
            streak++;
            bestStreak = Math.max(bestStreak, streak);
            increment("correct." + q.id);
            increment("totalCorrect");
        } else {
            streak = 0;
            increment("wrong." + q.id);
            increment("totalWrong");
        }
        preferences.edit()
                .putInt("bestScore", Math.max(preferences.getInt("bestScore", 0), score))
                .putInt("bestStreak", Math.max(preferences.getInt("bestStreak", 0), bestStreak))
                .apply();

        markOption(optionOne, q, choice);
        markOption(optionTwo, q, choice);
        scoreText.setText(score + " 分");
        if (correct) {
            resultText.setText("答对了 · 连击 " + streak);
            resultText.setTextColor(GREEN);
        } else if (choice == null) {
            resultText.setText("超时了 · 正确答案是 " + q.answer);
            resultText.setTextColor(RED);
        } else {
            resultText.setText("答错了 · 正确答案是 " + q.answer);
            resultText.setTextColor(RED);
        }
        explanationText.setText(q.explanation);
        nextButton.setText(questionIndex == activeDeck.size() - 1 ? "查看结果" : "下一题");
        nextButton.setVisibility(View.VISIBLE);
    }

    private void markOption(Button button, Question q, String choice) {
        String word = button.getText().toString();
        boolean isAnswer = q.answer.equals(word);
        boolean isChosen = word.equals(choice);
        button.setEnabled(false);
        button.setTextColor(isAnswer ? Color.WHITE : INK);
        button.setBackground(optionBackground(isAnswer, isChosen, true));
    }

    private void nextQuestion() {
        if (questionIndex >= activeDeck.size() - 1) {
            showResult();
            return;
        }
        questionIndex++;
        showQuestion();
    }

    private void showResult() {
        inSubPage = true;
        handler.removeCallbacks(timerTick);
        root = baseRoot();
        setContentView(root);
        root.addView(header("本局结果", true, true), matchWrap());

        LinearLayout panel = panel();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(20), dp(18), dp(20));
        TextView scoreBig = label(score + " 分", 56, ACCENT);
        scoreBig.setTypeface(Typeface.DEFAULT_BOLD);
        scoreBig.setGravity(Gravity.CENTER);
        TextView pass = label(score >= 80 ? "通关" : "再刷一轮", 22, score >= 80 ? GREEN : WARM);
        pass.setTypeface(Typeface.DEFAULT_BOLD);
        pass.setGravity(Gravity.CENTER);
        TextView detail = label(String.format(Locale.CHINA, "答对 %d/%d · 最长连击 %d", roundCorrect, activeDeck.size(), bestStreak), 15, MUTED);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(8), 0, 0);
        panel.addView(scoreBig, matchWrap());
        panel.addView(pass, matchWrap());
        panel.addView(detail, matchWrap());
        root.addView(panel, matchWrapWithBottom(14));

        root.addView(bigButton("再来一局", ACCENT, Color.WHITE, v -> startRound(false)), buttonLayout());
        root.addView(bigButton("刷错题", WARM, Color.WHITE, v -> startRound(true)), buttonLayout());
        root.addView(bigButton("回首页", CARD, INK, v -> buildHome()), buttonLayout());
    }

    private void showAtlas() {
        inSubPage = true;
        handler.removeCallbacks(timerTick);
        root = baseRoot();
        setContentView(root);
        root.addView(header("词对图鉴", true, true), matchWrap());

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());

        Set<String> seen = new HashSet<>();
        for (Question q : QUESTIONS) {
            String key = q.pairOne + "/" + q.pairTwo;
            if (seen.contains(key)) continue;
            seen.add(key);
            LinearLayout card = panel();
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView pair = label(key, 20, INK);
            pair.setTypeface(Typeface.DEFAULT_BOLD);
            TextView meta = label(String.format(Locale.CHINA, "等级 %d · %s · 错 %d 次", q.level, q.tag, pairWrongCount(q.pairOne, q.pairTwo)), 13, MUTED);
            TextView sample = label(sampleForPair(q.pairOne, q.pairTwo), 14, ACCENT_DARK);
            sample.setPadding(0, dp(8), 0, 0);
            card.addView(pair, matchWrap());
            card.addView(meta, matchWrap());
            card.addView(sample, matchWrap());
            content.addView(card, matchWrapWithBottom(10));
        }
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void showEmptyReview() {
        inSubPage = true;
        root = baseRoot();
        setContentView(root);
        root.addView(header("错题复习", true, true), matchWrap());
        LinearLayout panel = panel();
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView title = label("现在没有错题", 24, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView body = label("先打一局闯关，答错的题会自动进入复习池。", 15, MUTED);
        body.setPadding(0, dp(8), 0, 0);
        panel.addView(title, matchWrap());
        panel.addView(body, matchWrap());
        root.addView(panel, matchWrapWithBottom(14));
        root.addView(bigButton("开始闯关", ACCENT, Color.WHITE, v -> startRound(false)), buttonLayout());
    }

    private List<Question> challengeDeck() {
        List<Question> deck = new ArrayList<>();
        Collections.addAll(deck, QUESTIONS);
        Collections.shuffle(deck);
        Collections.sort(deck, (a, b) -> Integer.compare(priority(b), priority(a)));
        if (deck.size() > ROUND_SIZE) {
            deck = new ArrayList<>(deck.subList(0, ROUND_SIZE));
        }
        Collections.shuffle(deck);
        return deck;
    }

    private List<Question> reviewDeck() {
        List<Question> deck = new ArrayList<>();
        for (Question q : QUESTIONS) {
            if (wrongCount(q) > 0) deck.add(q);
        }
        Collections.sort(deck, new Comparator<Question>() {
            @Override
            public int compare(Question a, Question b) {
                return Integer.compare(wrongCount(b), wrongCount(a));
            }
        });
        if (deck.size() > ROUND_SIZE) {
            deck = new ArrayList<>(deck.subList(0, ROUND_SIZE));
        }
        Collections.shuffle(deck);
        return deck;
    }

    private int priority(Question q) {
        return wrongCount(q) * 8 - correctCount(q) + q.level;
    }

    private String sampleForPair(String one, String two) {
        for (Question q : QUESTIONS) {
            if (q.pairOne.equals(one) && q.pairTwo.equals(two)) {
                return q.enSentence + " -> " + q.answer;
            }
        }
        return "";
    }

    private int pairWrongCount(String one, String two) {
        int count = 0;
        for (Question q : QUESTIONS) {
            if (q.pairOne.equals(one) && q.pairTwo.equals(two)) count += wrongCount(q);
        }
        return count;
    }

    private int pairCount() {
        Set<String> pairs = new HashSet<>();
        for (Question q : QUESTIONS) pairs.add(q.pairOne + "/" + q.pairTwo);
        return pairs.size();
    }

    private int wrongCount(Question q) {
        return preferences.getInt("wrong." + q.id, 0);
    }

    private int correctCount(Question q) {
        return preferences.getInt("correct." + q.id, 0);
    }

    private void increment(String key) {
        preferences.edit().putInt(key, preferences.getInt(key, 0) + 1).apply();
    }

    private String recordLine() {
        int totalCorrect = preferences.getInt("totalCorrect", 0);
        int totalWrong = preferences.getInt("totalWrong", 0);
        int bestScore = preferences.getInt("bestScore", 0);
        if (totalCorrect + totalWrong == 0) return "还没开打";
        return "最佳 " + bestScore + " 分";
    }

    private void confirmReset() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(panelBackground());
        TextView title = label("重置所有答题记录？", 21, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView body = label("会清空分数、错题和连击记录，题库不会删除。", 14, MUTED);
        body.setPadding(0, dp(8), 0, dp(14));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = smallButton("取消");
        cancel.setOnClickListener(v -> dialog.dismiss());
        Button reset = smallButton("重置");
        reset.setTextColor(Color.WHITE);
        reset.setBackground(buttonBackground(WARM, WARM));
        reset.setOnClickListener(v -> {
            preferences.edit().clear().apply();
            dialog.dismiss();
            buildHome();
        });
        row.addView(cancel, new LinearLayout.LayoutParams(0, dp(42), 1));
        row.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        row.addView(reset, new LinearLayout.LayoutParams(0, dp(42), 1));
        box.addView(title, matchWrap());
        box.addView(body, matchWrap());
        box.addView(row, matchWrap());
        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) {
            shown.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private LinearLayout baseRoot() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(14));
        layout.setBackgroundColor(PAPER);
        return layout;
    }

    private void showSettingsPage() {
        inSubPage = true;
        handler.removeCallbacks(timerTick);
        root = baseRoot();
        setContentView(root);
        root.addView(header("设置", true, false), matchWrapWithBottom(12));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());

        content.addView(sectionTitle("数据管理"), matchWrap());
        content.addView(settingsCard(
                "本机记录",
                "相似词决斗只在手机本地保存答题次数、错题次数、最佳分数和最长连击。",
                "当前没有电脑服务同步、APK 更新或远程题库入口；安装和更新交给工具市场。"
        ), matchWrapWithBottom(12));
        Button reset = bigButton("重置记录", WARM, Color.WHITE, v -> confirmReset());
        content.addView(reset, buttonLayout());

        content.addView(sectionTitle("关于"), matchWrap());
        content.addView(settingsCard(
                "相似词决斗",
                String.format(Locale.CHINA, "内置 %d 道高一易混词题，覆盖 %d 组词对。", QUESTIONS.length, pairCount()),
                "第一版目标是把真实易错点玩起来；后续可以把题库迁到电脑端 JSON 维护。"
        ), matchWrapWithBottom(12));

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private TextView sectionTitle(String text) {
        TextView view = label(text, 15, MUTED);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private LinearLayout settingsCard(String titleText, String bodyText, String footText) {
        LinearLayout card = panel();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView title = label(titleText, 18, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView body = label(bodyText, 14, MUTED);
        body.setPadding(0, dp(6), 0, dp(8));
        TextView foot = label(footText, 13, ACCENT_DARK);
        card.addView(title, matchWrap());
        card.addView(body, matchWrap());
        card.addView(foot, matchWrap());
        return card;
    }

    private LinearLayout header(String titleText, boolean withBack, boolean withSettings) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (withBack) {
            TextView back = navIcon("‹", "返回");
            back.setOnClickListener(v -> buildHome());
            row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(40)));
        }
        TextView title = label(titleText, withBack && !withSettings ? 24 : 28, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        if (withBack) {
            title.setPadding(dp(12), 0, 0, 0);
        }
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (withSettings) {
            View settings = new SettingsIconButton(this);
            settings.setContentDescription("设置");
            settings.setOnClickListener(v -> showSettingsPage());
            row.addView(settings, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        return row;
    }

    private TextView navIcon(String text, String desc) {
        TextView view = label(text, 34, ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(desc);
        return view;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(panelBackground());
        return panel;
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    private TextView chip(String text, int bg, int fg) {
        TextView chip = label(text, 14, fg);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(pillBackground(bg, bg == CARD ? LINE : bg));
        return chip;
    }

    private Button bigButton(String text, int bg, int fg, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(17);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(fg);
        button.setAllCaps(false);
        int stroke = bg == CARD ? LINE : bg;
        button.setBackground(buttonBackground(bg, stroke));
        button.setOnClickListener(listener);
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(INK);
        button.setAllCaps(false);
        button.setBackground(buttonBackground(CARD, LINE));
        return button;
    }

    private Button optionButton() {
        Button button = new Button(this);
        button.setTextSize(28);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private void updateTimerText() {
        timerText.setText(secondsLeft + "s");
        timerText.setTextColor(secondsLeft <= 4 ? Color.WHITE : INK);
        timerText.setBackground(pillBackground(secondsLeft <= 4 ? WARM : GOLD, secondsLeft <= 4 ? WARM : GOLD));
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(CARD);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), LINE);
        return drawable;
    }

    private GradientDrawable buttonBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable optionBackground(boolean correct, boolean chosen, boolean finalState) {
        int fill = CARD;
        int stroke = LINE;
        if (finalState && correct) {
            fill = GREEN;
            stroke = GREEN;
        } else if (finalState && chosen) {
            fill = Color.rgb(255, 232, 222);
            stroke = RED;
        }
        return buttonBackground(fill, stroke);
    }

    private GradientDrawable pillBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(999));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private View space(int dp) {
        View view = new View(this);
        view.setBackgroundColor(Color.TRANSPARENT);
        return view;
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

    private LinearLayout.LayoutParams buttonLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static Question q(String id, String pairOne, String pairTwo, String answer, String zhPrompt, String enSentence, String explanation, int level, String tag) {
        return new Question(id, pairOne, pairTwo, answer, zhPrompt, enSentence, explanation, level, tag);
    }

    private static class Question {
        final String id;
        final String pairOne;
        final String pairTwo;
        final String answer;
        final String zhPrompt;
        final String enSentence;
        final String explanation;
        final int level;
        final String tag;

        Question(String id, String pairOne, String pairTwo, String answer, String zhPrompt, String enSentence, String explanation, int level, String tag) {
            this.id = id;
            this.pairOne = pairOne;
            this.pairTwo = pairTwo;
            this.answer = answer;
            this.zhPrompt = zhPrompt;
            this.enSentence = enSentence;
            this.explanation = explanation;
            this.level = level;
            this.tag = tag;
        }

        String other() {
            return answer.equals(pairOne) ? pairTwo : pairOne;
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
}
