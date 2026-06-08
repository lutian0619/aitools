package com.streetduel;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "street_duel";
    private static final int MAX_HAND = 5;
    private static final int WIN_SCORE = 2;
    private static final int CONTROL_SCORE = 5;
    private static final int MAX_BREATHERS = 2;

    private static final int INK = Color.rgb(47, 41, 37);
    private static final int MUTED = Color.rgb(117, 104, 95);
    private static final int PAPER = Color.rgb(253, 239, 239);
    private static final int CARD = Color.rgb(255, 249, 246);
    private static final int LINE = Color.rgb(218, 208, 194);
    private static final int ACCENT = Color.rgb(138, 113, 94);
    private static final int WARM = Color.rgb(205, 187, 167);
    private static final int GOLD = Color.rgb(200, 161, 90);
    private static final int STEEL = Color.rgb(47, 55, 63);
    private static final int RED = Color.rgb(183, 67, 52);
    private static final int GREEN = Color.rgb(42, 132, 88);

    private static final Card[] CARD_POOL = new Card[] {
            c("拳馆老手", 2, 4, "稳牌", "没有花活，直接堆战力。", Skill.NONE),
            c("重装保镖", 3, 5, "守点", "输了也能让据点保持争夺。", Skill.SHIELD),
            c("街头黑客", 2, 2, "断电", "让对手本回合战力 -2。", Skill.JAM),
            c("突击车手", 3, 3, "抢先", "打未占领据点时战力 +3。", Skill.RUSH),
            c("地下拳王", 4, 7, "压制", "高战力，消耗也高。", Skill.NONE),
            c("冷枪手", 3, 4, "瞄准", "如果对手消耗更高，额外 +3。", Skill.SNIPE),
            c("爆破队", 4, 4, "破局", "平局时赢下对拼，并额外推进 1 格。", Skill.BREAK_TIE),
            c("情报贩子", 1, 1, "补给", "本回合后额外抽 1 张，并刺探对手动向。", Skill.DRAW),
            c("维修班", 1, 2, "充能", "本回合后能量 +1。", Skill.CHARGE),
            c("铁门封锁", 2, 1, "封锁", "推进时额外 +1 格，并让对手少抽 1 张。", Skill.LOCK),
            c("短刀队", 2, 3, "反打", "遇到更高基础战力时，战力 +2，且额外推进 1 格。", Skill.COUNTER),
            c("无人机手", 3, 3, "侦察", "打对手已领先据点时战力 +4。", Skill.SCOUT)
    };

    private static final String[] DISTRICT_NAMES = {"天台", "车库", "桥口"};
    private static final String[] DISTRICT_NOTES = {"视野开阔，适合抢先压点。", "空间狭窄，守住就难翻。", "双方必争的撤离口。"};
    private static final String[] DISTRICT_TRAITS = {
            "匹配 +1；强攻多推",
            "匹配 +1；对拼放缓",
            "匹配 +1；错点多推"
    };
    private static final String[] EVENT_NAMES = {"霓虹雨夜", "拥堵夜市", "地铁末班", "车库警报"};
    private static final String[] EVENT_TEXTS = {
            "断电、瞄准、侦察 +1 战。",
            "1 消耗牌推进 +1 格。",
            "错点推进 +1 格。",
            "守点、封锁、充能推进 +1 格。"
    };

    private final Random random = new Random();
    private SharedPreferences preferences;
    private LinearLayout root;
    private LinearLayout districtList;
    private LinearLayout handRow;
    private TextView statusText;
    private TextView logText;
    private TextView selectedText;
    private TextView deckText;
    private TextView eventText;
    private Button focusButton;
    private Button scoutButton;
    private Button guardButton;
    private boolean inSubPage = false;

    private final List<Card> playerDeck = new ArrayList<>();
    private final List<Card> aiDeck = new ArrayList<>();
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> aiHand = new ArrayList<>();
    private final int[] owners = new int[DISTRICT_NAMES.length];
    private final int[] control = new int[DISTRICT_NAMES.length];
    private Card selectedCard;
    private int turn;
    private int playerEnergy;
    private int aiEnergy;
    private int playerSkipDraw;
    private int aiSkipDraw;
    private int playerBreathers;
    private int aiBreathers;
    private int playerWins;
    private int aiWins;
    private int matchEvent;
    private boolean aiBreathedThisTurn;
    private int guardedDistrict = -1;
    private String lastLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        buildHome();
    }

    @Override
    public void onBackPressed() {
        if (inSubPage) {
            buildHome();
            return;
        }
        super.onBackPressed();
    }

    private void buildHome() {
        inSubPage = false;
        root = baseRoot();
        setContentView(root);
        root.addView(header("街区对决", false, true), matchWrapWithBottom(10));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());

        LinearLayout hero = panel(STEEL, STEEL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView meta = label("单机策略卡牌 · 3 个据点 · 5 格控制 · 12 张基础牌", 13, GOLD);
        TextView title = label("抢点、压制、反打", 31, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(6), 0, dp(6));
        TextView body = label("每回合只做一个决定：选手牌，压据点。场地和街区事件会改变战力与推进，AI 同时出牌抢节奏。", 15, Color.rgb(245, 235, 218));
        hero.addView(meta, matchWrap());
        hero.addView(title, matchWrap());
        hero.addView(body, matchWrap());
        hero.addView(new StreetSceneView(this), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(92)
        ));
        content.addView(hero, matchWrapWithBottom(12));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("战绩", recordLine(), ACCENT), new LinearLayout.LayoutParams(0, dp(76), 1));
        stats.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        stats.addView(statCard("胜率", winRateLine(), WARM), new LinearLayout.LayoutParams(0, dp(76), 1));
        stats.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        stats.addView(statCard("牌库", CARD_POOL.length + " 种", GOLD), new LinearLayout.LayoutParams(0, dp(76), 1));
        content.addView(stats, matchWrapWithBottom(14));

        content.addView(difficultyPanel(), matchWrapWithBottom(12));
        content.addView(bigButton("开始" + difficultyName() + "局", ACCENT, Color.WHITE, v -> startGame()), buttonLayout());

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        secondary.addView(secondaryButton("查看牌库", v -> showCardLibrary()), new LinearLayout.LayoutParams(0, dp(42), 1));
        secondary.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        secondary.addView(secondaryButton("规则速览", v -> showRules()), new LinearLayout.LayoutParams(0, dp(42), 1));
        content.addView(secondary, matchWrapWithBottom(10));

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void startGame() {
        inSubPage = true;
        for (int i = 0; i < owners.length; i++) {
            owners[i] = 0;
            control[i] = 0;
        }
        playerDeck.clear();
        aiDeck.clear();
        playerHand.clear();
        aiHand.clear();
        buildDeck(playerDeck);
        buildDeck(aiDeck);
        Collections.shuffle(playerDeck);
        Collections.shuffle(aiDeck);
        turn = 1;
        playerEnergy = 3;
        aiEnergy = 3;
        playerSkipDraw = 0;
        aiSkipDraw = 0;
        playerBreathers = 0;
        aiBreathers = 0;
        playerWins = 0;
        aiWins = 0;
        matchEvent = random.nextInt(EVENT_NAMES.length);
        selectedCard = null;
        lastLog = "街区事件：" + EVENT_NAMES[matchEvent] + "，" + EVENT_TEXTS[matchEvent] + "\n选一张手牌，再点一个据点。";
        drawCards(playerDeck, playerHand, MAX_HAND);
        drawCards(aiDeck, aiHand, MAX_HAND);
        buildGame();
        refreshGame();
    }

    private void buildDeck(List<Card> deck) {
        for (Card card : CARD_POOL) {
            deck.add(copy(card));
            deck.add(copy(card));
        }
    }

    private void buildGame() {
        root = baseRoot();
        setContentView(root);
        root.addView(header("对决中 · " + difficultyName(), true, false), matchWrapWithBottom(10));

        LinearLayout score = new LinearLayout(this);
        score.setOrientation(LinearLayout.HORIZONTAL);
        score.setGravity(Gravity.CENTER_VERTICAL);
        statusText = chip("", ACCENT, Color.WHITE);
        deckText = chip("", CARD, INK);
        eventText = chip("", GOLD, INK);
        score.addView(statusText, new LinearLayout.LayoutParams(0, dp(38), 1));
        score.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        score.addView(deckText, new LinearLayout.LayoutParams(0, dp(38), 1));
        score.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        score.addView(eventText, new LinearLayout.LayoutParams(0, dp(38), 1));
        root.addView(score, matchWrapWithBottom(10));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());

        districtList = new LinearLayout(this);
        districtList.setOrientation(LinearLayout.HORIZONTAL);
        TextView districtTitle = sectionTitle("地盘");
        content.addView(districtTitle, matchWrap());
        content.addView(districtList, matchWrapWithBottom(10));

        TextView logTitle = sectionTitle("战报");
        content.addView(logTitle, matchWrap());
        LinearLayout info = panel();
        info.setPadding(dp(14), dp(12), dp(14), dp(12));
        selectedText = label("", 15, INK);
        selectedText.setTypeface(Typeface.DEFAULT_BOLD);
        logText = label("", 14, MUTED);
        logText.setPadding(0, dp(6), 0, 0);
        info.addView(selectedText, matchWrap());
        info.addView(logText, matchWrap());
        content.addView(info, matchWrapWithBottom(10));

        TextView handTitle = sectionTitle("手牌");
        content.addView(handTitle, matchWrap());
        LinearLayout tacticRow = new LinearLayout(this);
        tacticRow.setOrientation(LinearLayout.HORIZONTAL);
        focusButton = secondaryButton("蓄势", v -> takeBreatherTurn());
        scoutButton = secondaryButton("侦察", v -> takeScoutTurn());
        guardButton = secondaryButton("防守", v -> takeGuardTurn());
        tacticRow.addView(focusButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        tacticRow.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        tacticRow.addView(scoutButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        tacticRow.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        tacticRow.addView(guardButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        content.addView(tacticRow, matchWrap());

        handRow = new LinearLayout(this);
        handRow.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(handRow, matchWrap());

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void refreshGame() {
        if (inSubPage && statusText != null && !ensurePlayerCanAct()) return;
        playerWins = countOwner(1);
        aiWins = countOwner(2);
        statusText.setText(String.format(Locale.CHINA, "第 %d 回合 · %d:%d", turn, playerWins, aiWins));
        deckText.setText(String.format(Locale.CHINA, "能量 %d · 牌库 %d", playerEnergy, playerDeck.size()));
        eventText.setText(EVENT_NAMES[matchEvent]);
        updateTacticButtons();
        selectedText.setText(selectedCard == null
                ? "未选择手牌"
                : "已选：" + selectedCard.name + " [" + selectedCard.cost + "/" + selectedCard.power + "] · " + selectedCard.skillName + "： " + selectedCard.text);
        logText.setText(lastLog);
        renderDistricts();
        renderHand();
    }

    private void renderDistricts() {
        districtList.removeAllViews();
        for (int i = 0; i < DISTRICT_NAMES.length; i++) {
            final int index = i;
            String owner = owners[i] == 1 ? "我方控制" : owners[i] == 2 ? "对手控制" : "争夺中";
            int stroke = owners[i] == 1 ? ACCENT : owners[i] == 2 ? RED : LINE;
            int fill = owners[i] == 0 ? CARD : owners[i] == 1 ? Color.rgb(231, 246, 238) : Color.rgb(255, 232, 222);
            LinearLayout card = panel(fill, stroke);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            card.setClickable(owners[i] == 0);
            card.setEnabled(owners[i] == 0);
            card.setOnClickListener(v -> playTurn(index));

            card.addView(new DistrictSceneView(this, i, owners[i]), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(54)
            ));

            TextView name = label(DISTRICT_NAMES[i], 17, INK);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setGravity(Gravity.CENTER);
            TextView badge = chip(owner, stroke == LINE ? GOLD : stroke, stroke == LINE ? INK : Color.WHITE);

            TextView note = label(DISTRICT_NOTES[i], 10, MUTED);
            note.setGravity(Gravity.CENTER);
            note.setPadding(0, dp(4), 0, 0);
            note.setMaxLines(2);
            TextView trait = label(DISTRICT_TRAITS[i], 10, owners[i] == 0 ? ACCENT : MUTED);
            trait.setGravity(Gravity.CENTER);
            trait.setTypeface(Typeface.DEFAULT_BOLD);
            trait.setPadding(0, dp(4), 0, 0);
            trait.setMaxLines(2);
            TextView progress = chip(controlLine(i), progressColor(i), progressColor(i) == GOLD ? INK : Color.WHITE);
            card.addView(name, matchWrap());
            card.addView(badge, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(28)
            ));
            card.addView(note, matchWrap());
            card.addView(trait, matchWrap());
            card.addView(progress, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(28)
            ));
            districtList.addView(card, districtLayout(i));
        }
    }

    private void renderHand() {
        handRow.removeAllViews();
        for (Card card : playerHand) {
            boolean selected = selectedCard == card;
            boolean affordable = card.cost <= playerEnergy;
            LinearLayout button = panel(selected ? Color.rgb(244, 223, 208) : CARD, selected ? WARM : affordable ? LINE : Color.rgb(215, 196, 166));
            button.setPadding(dp(9), dp(10), dp(9), dp(10));
            button.setClickable(affordable);
            button.setEnabled(affordable);
            button.setAlpha(affordable ? 1f : 0.58f);
            button.setOnClickListener(v -> {
                selectedCard = card;
                refreshGame();
            });

            TextView stats = chip(card.cost + "/" + card.power, selected ? WARM : STEEL, Color.WHITE);
            TextView name = label(card.name, 12, affordable ? INK : MUTED);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);

            TextView skill = label(card.skillName, 11, selected ? WARM : ACCENT);
            skill.setTypeface(Typeface.DEFAULT_BOLD);
            skill.setGravity(Gravity.CENTER);
            skill.setPadding(0, dp(6), 0, 0);

            TextView costLabel = label("耗/战", 9, MUTED);
            costLabel.setGravity(Gravity.CENTER);
            button.addView(new CardArtView(this, card, true), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(42)
            ));
            button.addView(stats, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(26)
            ));
            button.addView(costLabel, matchWrap());
            button.addView(skill, matchWrap());
            button.addView(name, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1
            ));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(handCardWidth(), dp(178));
            params.setMargins(0, 0, dp(4), dp(8));
            handRow.addView(button, params);
        }
    }

    private void playTurn(int district) {
        if (!ensurePlayerCanAct()) return;
        if (selectedCard == null) {
            lastLog = "先选一张手牌，再选择要争夺的据点。";
            refreshGame();
            return;
        }
        if (selectedCard.cost > playerEnergy) {
            lastLog = "能量不够，换一张低消耗牌。";
            refreshGame();
            return;
        }
        Card playerCard = selectedCard;
        aiBreathedThisTurn = false;
        Card aiCard = chooseAiCard();
        if (aiCard == null) {
            finishGame(true, "对手无牌可出，你控制街区。");
            return;
        }
        int aiDistrict = chooseAiDistrict(district, aiCard);
        boolean sameDistrict = aiDistrict == district;
        int playerPower = effectivePower(playerCard, aiCard, district, true);
        int aiPower = effectivePower(aiCard, playerCard, aiDistrict, false);
        String playerBreakdown = powerBreakdown(playerCard, aiCard, district, true, sameDistrict && aiCard.skill == Skill.JAM);
        String aiBreakdown = powerBreakdown(aiCard, playerCard, aiDistrict, false, sameDistrict && playerCard.skill == Skill.JAM);
        if (sameDistrict && playerCard.skill == Skill.JAM) aiPower = Math.max(0, aiPower - 2);
        if (sameDistrict && aiCard.skill == Skill.JAM) playerPower = Math.max(0, playerPower - 2);

        playerEnergy -= playerCard.cost;
        aiEnergy -= aiCard.cost;
        playerHand.remove(playerCard);
        aiHand.remove(aiCard);

        String result;
        if (sameDistrict) {
            int winner = resolveWinner(playerCard, aiCard, playerPower, aiPower);
            if (winner == 1) {
                int gain = clashGain(playerPower - aiPower);
                if (playerCard.skill == Skill.BREAK_TIE) gain += 1;
                if (playerCard.skill == Skill.LOCK) gain += 1;
                gain += counterProgressBonus(playerCard, aiCard);
                if (aiCard.skill == Skill.SHIELD) gain = Math.max(1, gain - 1);
                int beforeTerrain = gain;
                gain = districtClashGain(district, gain, playerPower - aiPower);
                gain = eventProgressGain(playerCard, gain, false);
                applyControl(district, gain);
                result = "你用「" + playerCard.name + "」压过「" + aiCard.name + "」，在" + DISTRICT_NAMES[district] + "推进 " + gain + " 格。";
                result += districtClashNote(district, beforeTerrain, gain);
                result += eventProgressNote(playerCard, false);
                result += counterProgressNote(playerCard, aiCard);
                if (owners[district] == 1) result += " 你控制了" + DISTRICT_NAMES[district] + "。";
                if (playerCard.skill == Skill.LOCK) aiSkipDraw = 1;
            } else if (winner == 2) {
                int gain = clashGain(aiPower - playerPower);
                if (aiCard.skill == Skill.BREAK_TIE) gain += 1;
                if (aiCard.skill == Skill.LOCK) gain += 1;
                gain += counterProgressBonus(aiCard, playerCard);
                if (playerCard.skill == Skill.SHIELD) gain = Math.max(1, gain - 1);
                int beforeTerrain = gain;
                gain = districtClashGain(district, gain, aiPower - playerPower);
                String terrainNote = districtClashNote(district, beforeTerrain, gain);
                gain = eventProgressGain(aiCard, gain, false);
                gain = guardedOpponentGain(district, gain);
                applyControl(district, -gain);
                result = "对手用「" + aiCard.name + "」压过你，在" + DISTRICT_NAMES[district] + "推进 " + gain + " 格。";
                result += terrainNote;
                result += eventProgressNote(aiCard, false);
                result += counterProgressNote(aiCard, playerCard);
                if (owners[district] == 2) result += " 对手控制了" + DISTRICT_NAMES[district] + "。";
                if (aiCard.skill == Skill.LOCK) {
                    playerSkipDraw = 1;
                    result += " 你的下次抽牌被封锁。";
                }
            } else {
                result = DISTRICT_NAMES[district] + "打成平局，控制线没有变化。";
            }
        } else {
            int playerGain = laneGain(playerPower);
            int aiGain = laneGain(aiPower);
            if (playerCard.skill == Skill.LOCK) playerGain += 1;
            if (aiCard.skill == Skill.LOCK) aiGain += 1;
            playerGain += counterProgressBonus(playerCard, aiCard);
            aiGain += counterProgressBonus(aiCard, playerCard);
            int playerBeforeTerrain = playerGain;
            int aiBeforeTerrain = aiGain;
            playerGain = districtLaneGain(district, playerGain);
            aiGain = districtLaneGain(aiDistrict, aiGain);
            String playerTerrainNote = districtLaneNote(district, playerBeforeTerrain, playerGain);
            String aiTerrainNote = districtLaneNote(aiDistrict, aiBeforeTerrain, aiGain);
            playerGain = eventProgressGain(playerCard, playerGain, true);
            aiGain = eventProgressGain(aiCard, aiGain, true);
            applyControl(district, playerGain);
            aiGain = guardedOpponentGain(aiDistrict, aiGain);
            applyControl(aiDistrict, -aiGain);
            result = "双方错开据点。你在" + DISTRICT_NAMES[district] + "推进 " + playerGain + " 格，对手在" + DISTRICT_NAMES[aiDistrict] + "推进 " + aiGain + " 格。";
            result += playerTerrainNote;
            result += aiTerrainNote;
            result += eventProgressNote(playerCard, true);
            result += eventProgressNote(aiCard, true);
            result += counterProgressNote(playerCard, aiCard);
            result += counterProgressNote(aiCard, playerCard);
            if (owners[district] == 1) result += " 你控制了" + DISTRICT_NAMES[district] + "。";
            if (owners[aiDistrict] == 2) result += " 对手控制了" + DISTRICT_NAMES[aiDistrict] + "。";
            if (playerCard.skill == Skill.LOCK) aiSkipDraw = 1;
            if (aiCard.skill == Skill.LOCK) playerSkipDraw = 1;
        }

        afterTurnDraw(playerCard, aiCard);
        selectedCard = null;
        turn++;
        guardedDistrict = -1;
        lastLog = result
                + "\n你：" + playerCard.name + " -> " + playerBreakdown + " = " + playerPower
                + "\n对手：" + aiCard.name + " -> " + aiBreakdown + " = " + aiPower;
        if (playerCard.skill == Skill.DRAW) lastLog += intelligenceLine();
        if (aiBreathedThisTurn) lastLog += "\n对手触发喘息：获得 2 点能量并补抽 1 张。";
        if (countOwner(1) >= WIN_SCORE) {
            finishGame(true, lastLog);
            return;
        }
        if (countOwner(2) >= WIN_SCORE) {
            finishGame(false, lastLog);
            return;
        }
        if (openDistricts() == 0) {
            finishGame(countOwner(1) >= countOwner(2), lastLog);
            return;
        }
        if (playerHand.isEmpty() && playerDeck.isEmpty()) {
            finishGame(countOwner(1) > countOwner(2), lastLog + "\n你的牌打空了。");
            return;
        }
        refreshGame();
    }

    private void takeBreatherTurn() {
        if (playerBreathers >= MAX_BREATHERS) {
            lastLog = "本局战术次数已经用完，必须出牌争夺。";
            refreshGame();
            return;
        }
        aiBreathedThisTurn = false;
        Card aiCard = chooseAiCard();
        if (aiCard == null) {
            finishGame(true, "对手无牌可出，你控制街区。");
            return;
        }
        int aiDistrict = chooseAiDistrict(-1, aiCard);
        int aiPower = effectivePower(aiCard, aiCard, aiDistrict, false);
        int aiGain = laneGain(aiPower);
        if (aiCard.skill == Skill.LOCK) aiGain += 1;
        int beforeTerrain = aiGain;
        aiGain = districtLaneGain(aiDistrict, aiGain);
        String terrainNote = districtLaneNote(aiDistrict, beforeTerrain, aiGain);
        aiGain = eventProgressGain(aiCard, aiGain, true);
        aiGain = guardedOpponentGain(aiDistrict, aiGain);
        applyControl(aiDistrict, -aiGain);
        if (aiCard.skill == Skill.LOCK) playerSkipDraw = 1;

        aiEnergy -= aiCard.cost;
        aiHand.remove(aiCard);
        playerBreathers++;
        playerEnergy = Math.min(7, playerEnergy + 2);
        drawCards(playerDeck, playerHand, Math.min(MAX_HAND, playerHand.size() + 1));
        afterAiSoloTurn(aiCard);
        selectedCard = null;
        turn++;

        lastLog = "你选择蓄势 " + playerBreathers + "/" + MAX_BREATHERS + "：获得 2 点能量并补抽 1 张。"
                + "\n对手用「" + aiCard.name + "」在" + DISTRICT_NAMES[aiDistrict] + "推进 " + aiGain + " 格。"
                + "\n对手：" + aiCard.name + " -> " + powerBreakdown(aiCard, aiCard, aiDistrict, false, false) + " = " + aiPower;
        lastLog += terrainNote;
        lastLog += eventProgressNote(aiCard, true);
        if (aiBreathedThisTurn) lastLog += "\n对手触发喘息：获得 2 点能量并补抽 1 张。";
        if (owners[aiDistrict] == 2) lastLog += "\n对手控制了" + DISTRICT_NAMES[aiDistrict] + "。";
        if (countOwner(2) >= WIN_SCORE) {
            finishGame(false, lastLog);
            return;
        }
        if (openDistricts() == 0) {
            finishGame(countOwner(1) >= countOwner(2), lastLog);
            return;
        }
        refreshGame();
    }

    private void takeScoutTurn() {
        if (!spendTactic("侦察")) return;
        Card aiCard = peekAiCard();
        if (aiCard == null) {
            lastLog = "你选择侦察 " + playerBreathers + "/" + MAX_BREATHERS + "：对手当前没有可用手牌，可能会喘息。";
            refreshGame();
            return;
        }
        int likely = chooseAiDistrict(-1, aiCard);
        playerEnergy = Math.min(7, playerEnergy + 1);
        drawCards(playerDeck, playerHand, Math.min(MAX_HAND, playerHand.size() + 1));
        selectedCard = null;
        lastLog = "你选择侦察 " + playerBreathers + "/" + MAX_BREATHERS + "：获得 1 点能量并补抽 1 张。"
                + "\n情报显示：对手更可能用「" + aiCard.name + "」争夺" + DISTRICT_NAMES[likely] + "。";
        refreshGame();
    }

    private void takeGuardTurn() {
        if (!spendTactic("防守")) return;
        guardedDistrict = mostThreatenedDistrict();
        playerEnergy = Math.min(7, playerEnergy + 1);
        selectedCard = null;
        lastLog = "你选择防守 " + playerBreathers + "/" + MAX_BREATHERS + "：本回合守住" + DISTRICT_NAMES[guardedDistrict] + "，对手若推进此处将少 2 格。";
        refreshGame();
    }

    private boolean spendTactic(String name) {
        if (playerBreathers < MAX_BREATHERS) {
            playerBreathers++;
            return true;
        }
        lastLog = "本局战术次数已经用完，无法" + name + "。";
        refreshGame();
        return false;
    }

    private void afterAiSoloTurn(Card aiCard) {
        aiEnergy = Math.min(7, aiEnergy + 2);
        if (aiCard.skill == Skill.CHARGE) aiEnergy = Math.min(7, aiEnergy + 1);
        int aiDraw = aiCard.skill == Skill.DRAW ? 2 : 1;
        if (aiSkipDraw > 0) {
            aiDraw = 0;
            aiSkipDraw = 0;
        }
        drawCards(aiDeck, aiHand, Math.min(MAX_HAND, aiHand.size() + aiDraw));
    }

    private void afterTurnDraw(Card playerCard, Card aiCard) {
        playerEnergy = Math.min(7, playerEnergy + 2);
        aiEnergy = Math.min(7, aiEnergy + 2);
        if (playerCard.skill == Skill.CHARGE) playerEnergy = Math.min(7, playerEnergy + 1);
        if (aiCard.skill == Skill.CHARGE) aiEnergy = Math.min(7, aiEnergy + 1);
        int playerDraw = playerCard.skill == Skill.DRAW ? 2 : 1;
        int aiDraw = aiCard.skill == Skill.DRAW ? 2 : 1;
        if (playerSkipDraw > 0) {
            playerDraw = 0;
            playerSkipDraw = 0;
        }
        if (aiSkipDraw > 0) {
            aiDraw = 0;
            aiSkipDraw = 0;
        }
        drawCards(playerDeck, playerHand, Math.min(MAX_HAND, playerHand.size() + playerDraw));
        drawCards(aiDeck, aiHand, Math.min(MAX_HAND, aiHand.size() + aiDraw));
    }

    private int effectivePower(Card self, Card other, int district, boolean player) {
        return self.power + skillBonus(self, other, district, player) + districtBonus(self, district) + eventBonus(self);
    }

    private int skillBonus(Card self, Card other, int district, boolean player) {
        int bonus = 0;
        if (self.skill == Skill.RUSH && owners[district] == 0) bonus += 3;
        if (self.skill == Skill.SNIPE && other.cost > self.cost) bonus += 3;
        if (self.skill == Skill.COUNTER && other.power > self.power) bonus += 2;
        if (self.skill == Skill.SCOUT && countOwner(player ? 2 : 1) > countOwner(player ? 1 : 2)) bonus += 4;
        return bonus;
    }

    private String powerBreakdown(Card self, Card other, int district, boolean player, boolean jammed) {
        int bonus = skillBonus(self, other, district, player);
        int terrain = districtBonus(self, district);
        int event = eventBonus(self);
        String text = "基础 " + self.power;
        if (bonus > 0) text += " + 技能 " + bonus;
        if (terrain > 0) text += " + 地形 " + terrain;
        if (event > 0) text += " + 事件 " + event;
        if (jammed) text += " - 断电 2";
        return text;
    }

    private int districtBonus(Card self, int district) {
        if (district == 0 && (self.skill == Skill.RUSH || self.skill == Skill.SCOUT || self.skill == Skill.SNIPE)) return 1;
        if (district == 1 && (self.skill == Skill.SHIELD || self.skill == Skill.LOCK || self.skill == Skill.CHARGE)) return 1;
        if (district == 2 && (self.skill == Skill.BREAK_TIE || self.skill == Skill.COUNTER || self.skill == Skill.RUSH)) return 1;
        return 0;
    }

    private int eventBonus(Card self) {
        if (matchEvent == 0 && (self.skill == Skill.JAM || self.skill == Skill.SNIPE || self.skill == Skill.SCOUT)) return 1;
        return 0;
    }

    private int eventProgressGain(Card self, int gain, boolean lane) {
        if (matchEvent == 1 && self.cost == 1) return gain + 1;
        if (matchEvent == 2 && lane) return gain + 1;
        if (matchEvent == 3 && (self.skill == Skill.SHIELD || self.skill == Skill.LOCK || self.skill == Skill.CHARGE)) return gain + 1;
        return gain;
    }

    private String eventProgressNote(Card self, boolean lane) {
        if (matchEvent == 1 && self.cost == 1) return " 拥堵夜市，小牌额外推进。";
        if (matchEvent == 2 && lane) return " 地铁末班，错点额外推进。";
        if (matchEvent == 3 && (self.skill == Skill.SHIELD || self.skill == Skill.LOCK || self.skill == Skill.CHARGE)) {
            return " 车库警报，战术牌额外推进。";
        }
        return "";
    }

    private int clashGain(int diff) {
        if (diff >= 5) return 3;
        if (diff >= 2) return 2;
        return 1;
    }

    private int laneGain(int power) {
        return power >= 6 ? 2 : 1;
    }

    private int counterProgressBonus(Card self, Card other) {
        return self.skill == Skill.COUNTER && other.power > self.power ? 1 : 0;
    }

    private String counterProgressNote(Card self, Card other) {
        return counterProgressBonus(self, other) > 0 ? " 反打成功，额外推进。" : "";
    }

    private int districtClashGain(int district, int gain, int diff) {
        if (district == 0 && diff >= 2) return gain + 1;
        if (district == 1) return Math.max(1, gain - 1);
        return gain;
    }

    private int districtLaneGain(int district, int gain) {
        if (district == 2) return gain + 1;
        return gain;
    }

    private String districtClashNote(int district, int before, int after) {
        if (before == after) return "";
        if (district == 0) return " 天台高处强攻，额外推进。";
        if (district == 1) return " 车库掩体密集，推进放缓。";
        return "";
    }

    private String districtLaneNote(int district, int before, int after) {
        if (before == after) return "";
        if (district == 2) return " 桥口要道空档，错点额外推进。";
        return "";
    }

    private void applyControl(int district, int delta) {
        if (owners[district] != 0) return;
        control[district] = Math.max(-CONTROL_SCORE, Math.min(CONTROL_SCORE, control[district] + delta));
        if (control[district] >= CONTROL_SCORE) {
            owners[district] = 1;
            control[district] = CONTROL_SCORE;
        } else if (control[district] <= -CONTROL_SCORE) {
            owners[district] = 2;
            control[district] = -CONTROL_SCORE;
        }
    }

    private int guardedOpponentGain(int district, int gain) {
        if (district != guardedDistrict) return gain;
        int reduced = Math.max(0, gain - 2);
        guardedDistrict = -1;
        return reduced;
    }

    private int resolveWinner(Card playerCard, Card aiCard, int playerPower, int aiPower) {
        if (playerPower > aiPower) return 1;
        if (aiPower > playerPower) return 2;
        if (playerCard.skill == Skill.BREAK_TIE && aiCard.skill != Skill.BREAK_TIE) return 1;
        if (aiCard.skill == Skill.BREAK_TIE && playerCard.skill != Skill.BREAK_TIE) return 2;
        return 0;
    }

    private Card chooseAiCard() {
        List<Card> playable = new ArrayList<>();
        for (Card card : aiHand) {
            if (card.cost <= aiEnergy) playable.add(card);
        }
        if (playable.isEmpty()) {
            if (aiBreathers >= MAX_BREATHERS) return null;
            aiBreathers++;
            aiBreathedThisTurn = true;
            aiEnergy = Math.min(7, aiEnergy + 2);
            drawCards(aiDeck, aiHand, Math.min(MAX_HAND, aiHand.size() + 1));
            for (Card card : aiHand) {
                if (card.cost <= aiEnergy) playable.add(card);
            }
        }
        if (playable.isEmpty()) return null;
        if (difficultyValue() == 0) {
            return playable.get(random.nextInt(playable.size()));
        }
        Collections.sort(playable, (a, b) -> Integer.compare(scoreCard(b), scoreCard(a)));
        if (difficultyValue() >= 2) {
            return playable.get(0);
        }
        int pick = random.nextInt(Math.min(3, playable.size()));
        return playable.get(pick);
    }

    private Card peekAiCard() {
        List<Card> playable = new ArrayList<>();
        for (Card card : aiHand) {
            if (card.cost <= aiEnergy) playable.add(card);
        }
        if (playable.isEmpty()) return null;
        Collections.sort(playable, (a, b) -> Integer.compare(scoreCard(b), scoreCard(a)));
        return playable.get(0);
    }

    private String intelligenceLine() {
        Card peek = peekAiCard();
        if (peek == null) return "\n情报显示：对手可能需要喘息。";
        int likely = chooseAiDistrict(-1, peek);
        return "\n情报显示：对手可能用「" + peek.name + "」争夺" + DISTRICT_NAMES[likely] + "。";
    }

    private int scoreCard(Card card) {
        int score = card.power * 2 - card.cost + eventBonus(card) * 3;
        if (card.skill == Skill.BREAK_TIE || card.skill == Skill.JAM || card.skill == Skill.RUSH) score += 3;
        if (card.skill == Skill.DRAW || card.skill == Skill.CHARGE) score += 2;
        return score;
    }

    private int chooseAiDistrict(int playerDistrict, Card aiCard) {
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] == 0) open.add(i);
        }
        if (open.isEmpty()) return 0;
        int difficulty = difficultyValue();
        int readChance = difficulty == 0 ? 18 : difficulty == 1 ? 38 : 72;
        if (open.contains(playerDistrict) && random.nextInt(100) < readChance) {
            return playerDistrict;
        }
        if (difficulty >= 1 && countOwner(1) > countOwner(2) && random.nextBoolean()) {
            return open.get(open.size() - 1);
        }
        if (difficulty >= 1) {
            int best = open.get(0);
            int bestScore = -1;
            for (int district : open) {
                int score = districtBonus(aiCard, district) * 4 + aiProjectedControlScore(district, aiCard) + (district == 1 ? 1 : 0);
                if (score > bestScore) {
                    best = district;
                    bestScore = score;
                }
            }
            if (bestScore > 0 && random.nextInt(100) < (difficulty == 2 ? 88 : 62)) return best;
        }
        return open.get(random.nextInt(open.size()));
    }

    private int aiDistrictUrgency(int district) {
        if (control[district] <= -4) return 14;
        if (control[district] >= 4) return 13;
        if (control[district] <= -3) return 9;
        if (control[district] >= 3) return 8;
        if (control[district] < 0) return 3;
        if (control[district] > 0) return 2;
        return 0;
    }

    private int aiProjectedControlScore(int district, Card card) {
        int power = effectivePower(card, card, district, false);
        int gain = districtLaneGain(district, laneGain(power) + (card.skill == Skill.LOCK ? 1 : 0));
        gain = eventProgressGain(card, gain, true);
        if (control[district] < 0 && Math.abs(control[district]) + gain >= CONTROL_SCORE) return 22;
        if (control[district] > 0 && control[district] + gain >= CONTROL_SCORE) return 20;
        return aiDistrictUrgency(district) + gain * 2;
    }

    private int mostThreatenedDistrict() {
        int best = 0;
        int bestScore = -1;
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] != 0) continue;
            int score = Math.max(0, -control[i]) * 3 + Math.max(0, control[i]);
            if (score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    private void finishGame(boolean won, String reason) {
        int games = preferences.getInt("games", 0) + 1;
        int wins = preferences.getInt("wins", 0) + (won ? 1 : 0);
        preferences.edit()
                .putInt("games", games)
                .putInt("wins", wins)
                .putInt("bestTurn", Math.max(preferences.getInt("bestTurn", 0), won ? Math.max(1, 8 - turn) : 0))
                .apply();
        buildResult(won, reason);
    }

    private void buildResult(boolean won, String reason) {
        inSubPage = true;
        root = baseRoot();
        setContentView(root);
        root.addView(header("结算", true, false), matchWrapWithBottom(12));
        LinearLayout panel = panel();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(20), dp(18), dp(20));
        TextView title = label(won ? "控制街区" : "失去街区", 34, won ? ACCENT : RED);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        TextView score = label(String.format(Locale.CHINA, "据点 %d:%d · 用时 %d 回合", countOwner(1), countOwner(2), turn), 16, INK);
        score.setGravity(Gravity.CENTER);
        score.setPadding(0, dp(8), 0, dp(8));
        TextView detail = label(reason, 14, MUTED);
        detail.setGravity(Gravity.CENTER);
        panel.addView(title, matchWrap());
        panel.addView(score, matchWrap());
        panel.addView(detail, matchWrap());
        root.addView(panel, matchWrapWithBottom(14));
        root.addView(bigButton("再打一局", ACCENT, Color.WHITE, v -> startGame()), buttonLayout());
        root.addView(bigButton("查看牌库", CARD, INK, v -> showCardLibrary()), buttonLayout());
        root.addView(bigButton("回首页", CARD, INK, v -> buildHome()), buttonLayout());
    }

    private void showCardLibrary() {
        inSubPage = true;
        root = baseRoot();
        setContentView(root);
        root.addView(header("牌库", true, true), matchWrapWithBottom(12));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());

        LinearLayout intro = panel(STEEL, STEEL);
        intro.setPadding(dp(14), dp(13), dp(14), dp(13));
        TextView title = label("基础套牌", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView body = label("每种牌在套牌中有 2 张。消耗越高不代表越稳，技能时机才是关键。", 14, Color.rgb(245, 235, 218));
        body.setPadding(0, dp(6), 0, 0);
        intro.addView(title, matchWrap());
        intro.addView(body, matchWrap());
        content.addView(intro, matchWrapWithBottom(10));

        for (Card card : CARD_POOL) {
            content.addView(cardPreview(card), matchWrapWithBottom(8));
        }

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void showRules() {
        inSubPage = true;
        root = baseRoot();
        setContentView(root);
        root.addView(header("规则", true, true), matchWrapWithBottom(12));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());
        content.addView(ruleCard("目标", "场上有天台、车库、桥口 3 个据点。每个据点有 5 格控制进度，先完全控制 2 个据点获胜。"), matchWrapWithBottom(10));
        content.addView(ruleCard("出牌", "每回合你选择 1 张手牌，再选择 1 个未控制据点。AI 会同时出牌和选点。选到同一个据点就比较战力，赢家按差距推进 1-3 格；选到不同据点则双方各自推进 1-2 格。"), matchWrapWithBottom(10));
        content.addView(ruleCard("牌面", "牌名后的 [消耗/战力] 表示出牌消耗和基础点数。技能会改变战力、推进、抽牌或封锁对手。能量每回合回 2 点，最多 7 点。"), matchWrapWithBottom(10));
        content.addView(ruleCard("战术", "你可以使用蓄势、侦察、防守。蓄势会跳过出牌，获得 2 点能量并补抽 1 张，但对手会推进；侦察预判对手目标；防守让最危险据点少受 2 格推进。战术和喘息合计每局最多 2 次。"), matchWrapWithBottom(10));
        content.addView(ruleCard("场地", "命中场地牌型时 +1 战。天台强攻时额外推进；车库同点对拼推进放缓；桥口双方错点时额外推进。"), matchWrapWithBottom(10));
        content.addView(ruleCard("街区事件", "每局开局随机一种事件。霓虹雨夜强化特定牌战力；拥堵夜市让低消耗牌多推进；地铁末班强化错点推进；车库警报强化守点、封锁、充能的推进。"), matchWrapWithBottom(10));
        content.addView(ruleCard("难度", "新手更随机，标准会评估牌面，强硬会更常预判你的据点并优先打高价值牌。"), matchWrapWithBottom(10));
        content.addView(ruleCard("策略", "高消耗牌不一定最好。同点对拼能大幅推进，错点只能稳扎稳打。低消耗牌能抢节奏，断电和破局适合关键对拼，补给和充能适合拖到后期。"), matchWrapWithBottom(10));
        content.addView(bigButton("看牌库", CARD, INK, v -> showCardLibrary()), buttonLayout());
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private LinearLayout ruleCard(String titleText, String bodyText) {
        LinearLayout card = panel();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView title = label(titleText, 19, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView body = label(bodyText, 15, MUTED);
        body.setPadding(0, dp(7), 0, 0);
        card.addView(title, matchWrap());
        card.addView(body, matchWrap());
        return card;
    }

    private LinearLayout cardPreview(Card cardData) {
        LinearLayout row = panel();
        row.setPadding(dp(13), dp(11), dp(13), dp(11));
        row.setOrientation(LinearLayout.HORIZONTAL);

        row.addView(new CardArtView(this, cardData, false), new LinearLayout.LayoutParams(
                dp(88),
                dp(108)
        ));
        row.addView(space(10), new LinearLayout.LayoutParams(dp(10), 1));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(cardData.name, 19, INK);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        TextView stats = chip(String.format(Locale.CHINA, "%d 耗 / %d 战", cardData.cost, cardData.power), STEEL, Color.WHITE);
        top.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(stats, new LinearLayout.LayoutParams(dp(104), dp(32)));

        TextView skill = label(cardData.skillName + "： " + cardData.text, 14, MUTED);
        skill.setPadding(0, dp(7), 0, 0);
        copy.addView(top, matchWrap());
        copy.addView(skill, matchWrap());
        row.addView(copy, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        return row;
    }

    private LinearLayout statCard(String titleText, String valueText, int accentColor) {
        LinearLayout card = panel();
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        TextView title = label(titleText, 12, MUTED);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView value = label(valueText, 17, accentColor);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setPadding(0, dp(5), 0, 0);
        value.setSingleLine(false);
        card.addView(title, matchWrap());
        card.addView(value, matchWrap());
        return card;
    }

    private LinearLayout difficultyPanel() {
        LinearLayout panel = panel();
        panel.setPadding(dp(13), dp(12), dp(13), dp(13));
        TextView title = label("对手强度", 16, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView body = label(difficultyDescription(), 13, MUTED);
        body.setPadding(0, dp(5), 0, dp(10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(difficultyButton(0, "新手"), new LinearLayout.LayoutParams(0, dp(42), 1));
        row.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        row.addView(difficultyButton(1, "标准"), new LinearLayout.LayoutParams(0, dp(42), 1));
        row.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        row.addView(difficultyButton(2, "强硬"), new LinearLayout.LayoutParams(0, dp(42), 1));

        panel.addView(title, matchWrap());
        panel.addView(body, matchWrap());
        panel.addView(row, matchWrap());
        return panel;
    }

    private Button difficultyButton(int value, String text) {
        Button button = smallButton(text);
        boolean selected = difficultyValue() == value;
        button.setTextColor(selected ? Color.WHITE : INK);
        int fill = selected ? difficultyColor(value) : CARD;
        button.setBackground(buttonBackground(fill, selected ? fill : LINE));
        button.setOnClickListener(v -> {
            preferences.edit().putInt("difficulty", value).apply();
            buildHome();
        });
        return button;
    }

    private void showSettingsPage() {
        inSubPage = true;
        root = baseRoot();
        setContentView(root);
        root.addView(header("设置", true, false), matchWrapWithBottom(12));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, matchWrap());
        content.addView(sectionTitle("数据管理"), matchWrap());
        content.addView(ruleCard("本机战绩", "街区对决只在手机本地保存局数和胜场，不连接电脑服务，也不提供 APK 更新入口。安装和更新交给工具市场。"), matchWrapWithBottom(10));
        content.addView(bigButton("清空战绩", WARM, Color.WHITE, v -> confirmReset()), buttonLayout());
        content.addView(sectionTitle("关于"), matchWrap());
        content.addView(ruleCard("街区对决", "一款轻量街头主题卡牌游戏。提供单机 AI 对战、12 种基础牌、3 个据点、5 格控制进度和街区事件。"), matchWrapWithBottom(10));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void confirmReset() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(panelBackground());
        TextView title = label("清空战绩？", 22, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView body = label("只会清空本机胜场和局数，不影响游戏牌库。", 14, MUTED);
        body.setPadding(0, dp(8), 0, dp(14));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = smallButton("取消");
        cancel.setOnClickListener(v -> dialog.dismiss());
        Button reset = smallButton("清空");
        reset.setTextColor(Color.WHITE);
        reset.setBackground(buttonBackground(WARM, WARM));
        reset.setOnClickListener(v -> {
            preferences.edit()
                    .remove("games")
                    .remove("wins")
                    .remove("bestTurn")
                    .apply();
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
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) shown.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void drawCards(List<Card> deck, List<Card> hand, int targetSize) {
        while (hand.size() < targetSize && !deck.isEmpty()) {
            hand.add(deck.remove(0));
        }
    }

    private boolean ensurePlayerCanAct() {
        if (hasPlayable(playerHand, playerEnergy)) return true;
        if (playerBreathers < MAX_BREATHERS) {
            playerBreathers++;
            playerEnergy = Math.min(7, playerEnergy + 2);
            drawCards(playerDeck, playerHand, Math.min(MAX_HAND, playerHand.size() + 1));
            lastLog = "你没有可用手牌，触发喘息 " + playerBreathers + "/" + MAX_BREATHERS + "：获得 2 点能量并补抽 1 张。";
            selectedCard = null;
            if (hasPlayable(playerHand, playerEnergy)) return true;
        }
        finishGame(countOwner(1) > countOwner(2), "你没有可用手牌，街区争夺被迫结束。");
        return false;
    }

    private boolean hasPlayable(List<Card> hand, int energy) {
        for (Card card : hand) {
            if (card.cost <= energy) return true;
        }
        return false;
    }

    private int countOwner(int owner) {
        int count = 0;
        for (int value : owners) if (value == owner) count++;
        return count;
    }

    private int openDistricts() {
        int count = 0;
        for (int value : owners) if (value == 0) count++;
        return count;
    }

    private String controlLine(int district) {
        if (owners[district] == 1) return "已控 " + CONTROL_SCORE + "/" + CONTROL_SCORE;
        if (owners[district] == 2) return "失守 " + CONTROL_SCORE + "/" + CONTROL_SCORE;
        if (control[district] > 0) return "我方 " + control[district] + "/" + CONTROL_SCORE;
        if (control[district] < 0) return "对手 " + Math.abs(control[district]) + "/" + CONTROL_SCORE;
        return "胶着 0/" + CONTROL_SCORE;
    }

    private int progressColor(int district) {
        if (owners[district] == 1 || control[district] > 0) return ACCENT;
        if (owners[district] == 2 || control[district] < 0) return RED;
        return GOLD;
    }

    private String recordLine() {
        int games = preferences.getInt("games", 0);
        int wins = preferences.getInt("wins", 0);
        if (games == 0) return "还没开战";
        return String.format(Locale.CHINA, "%d 胜 / %d 局", wins, games);
    }

    private String winRateLine() {
        int games = preferences.getInt("games", 0);
        if (games == 0) return "--";
        int wins = preferences.getInt("wins", 0);
        return String.format(Locale.CHINA, "%d%%", Math.round(wins * 100f / games));
    }

    private int difficultyValue() {
        return preferences.getInt("difficulty", 1);
    }

    private String difficultyName() {
        int value = difficultyValue();
        if (value == 0) return "新手";
        if (value == 2) return "强硬";
        return "标准";
    }

    private String difficultyDescription() {
        int value = difficultyValue();
        if (value == 0) return "AI 出牌更随意，适合熟悉消耗、战力和技能。";
        if (value == 2) return "AI 会更常预判你的据点，并优先使用高价值牌。";
        return "AI 会评估牌面，但仍保留一定失误空间。";
    }

    private int difficultyColor(int value) {
        if (value == 0) return GOLD;
        if (value == 2) return STEEL;
        return ACCENT;
    }

    private LinearLayout baseRoot() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(14));
        layout.setBackgroundColor(PAPER);
        return layout;
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
        if (withBack) title.setPadding(dp(12), 0, 0, 0);
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

    private TextView sectionTitle(String text) {
        TextView view = label(text, 15, MUTED);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private LinearLayout panel() {
        return panel(CARD, LINE);
    }

    private LinearLayout panel(int fill, int stroke) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(buttonBackground(fill, stroke));
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
        button.setBackground(buttonBackground(bg, bg == CARD ? LINE : bg));
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

    private Button secondaryButton(String text, View.OnClickListener listener) {
        Button button = smallButton(text);
        button.setTextSize(13);
        button.setTextColor(MUTED);
        button.setOnClickListener(listener);
        return button;
    }

    private void updateTacticButtons() {
        if (focusButton == null || scoutButton == null || guardButton == null) return;
        int remaining = Math.max(0, MAX_BREATHERS - playerBreathers);
        focusButton.setText("蓄势 " + remaining);
        scoutButton.setText("侦察 " + remaining);
        guardButton.setText("防守 " + remaining);
        focusButton.setEnabled(remaining > 0);
        scoutButton.setEnabled(remaining > 0);
        guardButton.setEnabled(remaining > 0);
        float alpha = remaining > 0 ? 1f : 0.52f;
        focusButton.setAlpha(alpha);
        scoutButton.setAlpha(alpha);
        guardButton.setAlpha(alpha);
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

    private LinearLayout.LayoutParams districtLayout(int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dp(214),
                1
        );
        params.setMargins(0, 0, index == DISTRICT_NAMES.length - 1 ? 0 : dp(8), dp(8));
        return params;
    }

    private int handCardWidth() {
        float density = getResources().getDisplayMetrics().density;
        int screenDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        int usableDp = screenDp - 36 - 16;
        return dp(Math.max(58, usableDp / MAX_HAND));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static Card c(String name, int cost, int power, String skillName, String text, Skill skill) {
        return new Card(name, cost, power, skillName, text, skill);
    }

    private static Card copy(Card card) {
        return new Card(card.name, card.cost, card.power, card.skillName, card.text, card.skill);
    }

    private enum Skill {
        NONE,
        SHIELD,
        JAM,
        RUSH,
        SNIPE,
        BREAK_TIE,
        DRAW,
        CHARGE,
        LOCK,
        COUNTER,
        SCOUT
    }

    private static class Card {
        final String name;
        final int cost;
        final int power;
        final String skillName;
        final String text;
        final Skill skill;

        Card(String name, int cost, int power, String skillName, String text, Skill skill) {
            this.name = name;
            this.cost = cost;
            this.power = power;
            this.skillName = skillName;
            this.text = text;
            this.skill = skill;
        }
    }

    private final class StreetSceneView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        StreetSceneView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float pad = dp(6);
            rect.set(pad, dp(12), w - pad, h - pad);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(253, 239, 239));
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);

            paint.setColor(Color.rgb(44, 48, 45));
            rect.set(pad, h * 0.58f, w - pad, h - pad);
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);

            drawBuilding(canvas, w * 0.1f, h * 0.24f, w * 0.25f, h * 0.62f, ACCENT);
            drawBuilding(canvas, w * 0.32f, h * 0.12f, w * 0.5f, h * 0.62f, WARM);
            drawBuilding(canvas, w * 0.58f, h * 0.2f, w * 0.78f, h * 0.62f, GOLD);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(GOLD);
            canvas.drawLine(w * 0.08f, h * 0.82f, w * 0.92f, h * 0.82f, paint);
            paint.setColor(Color.WHITE);
            for (int i = 0; i < 5; i++) {
                float x = w * (0.18f + i * 0.15f);
                canvas.drawLine(x, h * 0.74f, x + w * 0.06f, h * 0.74f, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(RED);
            canvas.drawCircle(w * 0.84f, h * 0.5f, dp(8), paint);
            paint.setColor(STEEL);
            rect.set(w * 0.82f, h * 0.5f, w * 0.86f, h * 0.72f);
            canvas.drawRoundRect(rect, dp(3), dp(3), paint);
        }

        private void drawBuilding(Canvas canvas, float left, float top, float right, float bottom, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, dp(5), dp(5), paint);
            paint.setColor(Color.rgb(244, 223, 208));
            float bw = right - left;
            float bh = bottom - top;
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    rect.set(left + bw * (0.18f + col * 0.38f), top + bh * (0.18f + row * 0.28f),
                            left + bw * (0.32f + col * 0.38f), top + bh * (0.3f + row * 0.28f));
                    canvas.drawRoundRect(rect, dp(2), dp(2), paint);
                }
            }
        }
    }

    private final class DistrictSceneView extends View {
        private final int district;
        private final int owner;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        DistrictSceneView(Context context, int district, int owner) {
            super(context);
            this.district = district;
            this.owner = owner;
            setContentDescription(DISTRICT_NAMES[district] + "场景");
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float pad = dp(2);
            int sky = owner == 1 ? Color.rgb(231, 246, 238) : owner == 2 ? Color.rgb(255, 232, 222) : Color.rgb(244, 223, 208);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(sky);
            rect.set(pad, pad, w - pad, h - pad);
            canvas.drawRoundRect(rect, dp(7), dp(7), paint);
            if (district == 0) {
                drawRooftop(canvas, w, h);
            } else if (district == 1) {
                drawGarage(canvas, w, h);
            } else if (district == 2) {
                drawBridge(canvas, w, h);
            } else if (district == 3) {
                drawMarket(canvas, w, h);
            } else {
                drawSubway(canvas, w, h);
            }
            if (owner != 0) drawFlag(canvas, w, h, owner == 1 ? ACCENT : RED);
        }

        private void drawRooftop(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(STEEL);
            rect.set(w * 0.12f, h * 0.56f, w * 0.9f, h * 0.8f);
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            paint.setColor(ACCENT);
            rect.set(w * 0.18f, h * 0.34f, w * 0.38f, h * 0.56f);
            canvas.drawRoundRect(rect, dp(3), dp(3), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(GOLD);
            canvas.drawLine(w * 0.46f, h * 0.5f, w * 0.82f, h * 0.28f, paint);
            canvas.drawLine(w * 0.82f, h * 0.28f, w * 0.82f, h * 0.62f, paint);
        }

        private void drawGarage(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(STEEL);
            rect.set(w * 0.1f, h * 0.28f, w * 0.9f, h * 0.82f);
            canvas.drawRoundRect(rect, dp(5), dp(5), paint);
            paint.setColor(Color.rgb(244, 223, 208));
            rect.set(w * 0.2f, h * 0.44f, w * 0.8f, h * 0.82f);
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(LINE);
            for (int i = 0; i < 3; i++) {
                float y = h * (0.52f + i * 0.1f);
                canvas.drawLine(w * 0.23f, y, w * 0.77f, y, paint);
            }
        }

        private void drawBridge(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(STEEL);
            canvas.drawLine(w * 0.14f, h * 0.7f, w * 0.86f, h * 0.7f, paint);
            canvas.drawLine(w * 0.24f, h * 0.32f, w * 0.24f, h * 0.76f, paint);
            canvas.drawLine(w * 0.76f, h * 0.32f, w * 0.76f, h * 0.76f, paint);
            paint.setStrokeWidth(dp(2));
            paint.setColor(ACCENT);
            canvas.drawLine(w * 0.24f, h * 0.32f, w * 0.5f, h * 0.7f, paint);
            canvas.drawLine(w * 0.76f, h * 0.32f, w * 0.5f, h * 0.7f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(GOLD);
            canvas.drawCircle(w * 0.5f, h * 0.7f, dp(4), paint);
        }

        private void drawMarket(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(WARM);
            rect.set(w * 0.12f, h * 0.36f, w * 0.88f, h * 0.5f);
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            paint.setColor(STEEL);
            rect.set(w * 0.16f, h * 0.5f, w * 0.84f, h * 0.82f);
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            paint.setColor(GOLD);
            for (int i = 0; i < 4; i++) {
                canvas.drawCircle(w * (0.22f + i * 0.18f), h * 0.28f, dp(4), paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(244, 223, 208));
            canvas.drawLine(w * 0.24f, h * 0.62f, w * 0.76f, h * 0.62f, paint);
        }

        private void drawSubway(Canvas canvas, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(STEEL);
            rect.set(w * 0.18f, h * 0.28f, w * 0.82f, h * 0.78f);
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            paint.setColor(ACCENT);
            rect.set(w * 0.24f, h * 0.38f, w * 0.76f, h * 0.56f);
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            paint.setColor(Color.rgb(244, 223, 208));
            canvas.drawCircle(w * 0.34f, h * 0.72f, dp(5), paint);
            canvas.drawCircle(w * 0.66f, h * 0.72f, dp(5), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(GOLD);
            canvas.drawLine(w * 0.18f, h * 0.86f, w * 0.82f, h * 0.86f, paint);
        }

        private void drawFlag(Canvas canvas, float w, float h, int color) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(STEEL);
            canvas.drawLine(w * 0.82f, h * 0.18f, w * 0.82f, h * 0.64f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            rect.set(w * 0.82f, h * 0.18f, w * 0.98f, h * 0.38f);
            canvas.drawRoundRect(rect, dp(2), dp(2), paint);
        }
    }

    private final class CardArtView extends View {
        private final Card card;
        private final boolean compact;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        CardArtView(Context context, Card card, boolean compact) {
            super(context);
            this.card = card;
            this.compact = compact;
            setContentDescription(card.name + "插画");
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float pad = dp(compact ? 3 : 5);
            float radius = dp(7);
            int accent = artAccent(card);

            rect.set(pad, pad, w - pad, h - pad);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(244, 223, 208));
            canvas.drawRoundRect(rect, radius, radius, paint);

            rect.set(pad, h * 0.64f, w - pad, h - pad);
            paint.setColor(compact ? Color.rgb(244, 223, 208) : Color.rgb(218, 208, 194));
            canvas.drawRoundRect(rect, radius, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(LINE);
            rect.set(pad, pad, w - pad, h - pad);
            canvas.drawRoundRect(rect, radius, radius, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent);
            float cx = w / 2f;
            float cy = h * (compact ? 0.48f : 0.46f);
            float size = Math.min(w, h) * (compact ? 0.22f : 0.28f);
            drawArtSymbol(canvas, cx, cy, size, accent);
        }

        private void drawArtSymbol(Canvas canvas, float cx, float cy, float size, int accent) {
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.FILL);
            switch (card.skill) {
                case SHIELD:
                    drawPerson(canvas, cx - size * 0.36f, cy + size * 0.08f, size * 0.72f, accent);
                    rect.set(cx - size * 0.08f, cy - size * 0.72f, cx + size * 0.64f, cy + size * 0.42f);
                    paint.setColor(STEEL);
                    canvas.drawRoundRect(rect, dp(4), dp(4), paint);
                    paint.setColor(GOLD);
                    canvas.drawCircle(cx + size * 0.28f, cy - size * 0.12f, size * 0.16f, paint);
                    break;
                case JAM:
                    rect.set(cx - size * 0.78f, cy - size * 0.28f, cx + size * 0.32f, cy + size * 0.4f);
                    paint.setColor(STEEL);
                    canvas.drawRoundRect(rect, dp(4), dp(4), paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(3));
                    paint.setColor(GOLD);
                    canvas.drawLine(cx + size * 0.22f, cy - size * 0.72f, cx - size * 0.04f, cy - size * 0.08f, paint);
                    canvas.drawLine(cx - size * 0.04f, cy - size * 0.08f, cx + size * 0.52f, cy - size * 0.08f, paint);
                    canvas.drawLine(cx + size * 0.52f, cy - size * 0.08f, cx + size * 0.1f, cy + size * 0.72f, paint);
                    break;
                case RUSH:
                    rect.set(cx - size * 0.9f, cy - size * 0.2f, cx + size * 0.62f, cy + size * 0.32f);
                    paint.setColor(accent);
                    canvas.drawRoundRect(rect, dp(5), dp(5), paint);
                    paint.setColor(STEEL);
                    canvas.drawCircle(cx - size * 0.54f, cy + size * 0.42f, size * 0.18f, paint);
                    canvas.drawCircle(cx + size * 0.4f, cy + size * 0.42f, size * 0.18f, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(2));
                    paint.setColor(GOLD);
                    canvas.drawLine(cx + size * 0.72f, cy - size * 0.14f, cx + size, cy - size * 0.14f, paint);
                    canvas.drawLine(cx + size * 0.72f, cy + size * 0.12f, cx + size * 1.12f, cy + size * 0.12f, paint);
                    break;
                case SNIPE:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(3));
                    paint.setColor(STEEL);
                    canvas.drawCircle(cx, cy, size * 0.58f, paint);
                    canvas.drawLine(cx - size * 0.82f, cy, cx + size * 0.82f, cy, paint);
                    canvas.drawLine(cx, cy - size * 0.82f, cx, cy + size * 0.82f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(accent);
                    canvas.drawCircle(cx, cy, size * 0.18f, paint);
                    break;
                case BREAK_TIE:
                    paint.setColor(GOLD);
                    canvas.drawCircle(cx, cy, size * 0.62f, paint);
                    paint.setColor(WARM);
                    canvas.drawCircle(cx - size * 0.28f, cy - size * 0.1f, size * 0.36f, paint);
                    canvas.drawCircle(cx + size * 0.28f, cy + size * 0.12f, size * 0.38f, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(3));
                    paint.setColor(STEEL);
                    canvas.drawLine(cx - size * 0.84f, cy + size * 0.56f, cx + size * 0.84f, cy - size * 0.56f, paint);
                    break;
                case DRAW:
                    drawStackedCards(canvas, cx, cy, size, accent);
                    break;
                case CHARGE:
                    rect.set(cx - size * 0.7f, cy - size * 0.34f, cx + size * 0.5f, cy + size * 0.34f);
                    paint.setColor(STEEL);
                    canvas.drawRoundRect(rect, dp(4), dp(4), paint);
                    rect.set(cx + size * 0.5f, cy - size * 0.14f, cx + size * 0.72f, cy + size * 0.14f);
                    canvas.drawRoundRect(rect, dp(2), dp(2), paint);
                    paint.setColor(GREEN);
                    rect.set(cx - size * 0.58f, cy - size * 0.22f, cx + size * 0.22f, cy + size * 0.22f);
                    canvas.drawRoundRect(rect, dp(3), dp(3), paint);
                    break;
                case LOCK:
                    paint.setColor(STEEL);
                    for (int i = -1; i <= 1; i++) {
                        rect.set(cx + i * size * 0.38f - size * 0.08f, cy - size * 0.7f,
                                cx + i * size * 0.38f + size * 0.08f, cy + size * 0.62f);
                        canvas.drawRoundRect(rect, dp(3), dp(3), paint);
                    }
                    paint.setStrokeWidth(dp(3));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(accent);
                    canvas.drawLine(cx - size * 0.84f, cy - size * 0.06f, cx + size * 0.84f, cy - size * 0.06f, paint);
                    canvas.drawLine(cx - size * 0.84f, cy + size * 0.34f, cx + size * 0.84f, cy + size * 0.34f, paint);
                    break;
                case COUNTER:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(4));
                    paint.setColor(STEEL);
                    canvas.drawLine(cx - size * 0.72f, cy + size * 0.56f, cx + size * 0.56f, cy - size * 0.72f, paint);
                    canvas.drawLine(cx + size * 0.72f, cy + size * 0.56f, cx - size * 0.56f, cy - size * 0.72f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(accent);
                    canvas.drawCircle(cx, cy, size * 0.2f, paint);
                    break;
                case SCOUT:
                    paint.setColor(STEEL);
                    rect.set(cx - size * 0.36f, cy - size * 0.2f, cx + size * 0.36f, cy + size * 0.2f);
                    canvas.drawRoundRect(rect, dp(5), dp(5), paint);
                    paint.setColor(accent);
                    canvas.drawCircle(cx - size * 0.72f, cy, size * 0.22f, paint);
                    canvas.drawCircle(cx + size * 0.72f, cy, size * 0.22f, paint);
                    paint.setColor(GOLD);
                    canvas.drawCircle(cx, cy, size * 0.1f, paint);
                    break;
                default:
                    drawPerson(canvas, cx, cy + size * 0.06f, size, accent);
                    if ("地下拳王".equals(card.name)) {
                        paint.setColor(GOLD);
                        canvas.drawCircle(cx, cy - size * 0.92f, size * 0.16f, paint);
                    }
                    break;
            }
        }

        private void drawPerson(Canvas canvas, float cx, float cy, float size, int accent) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(STEEL);
            canvas.drawCircle(cx, cy - size * 0.48f, size * 0.22f, paint);
            rect.set(cx - size * 0.26f, cy - size * 0.2f, cx + size * 0.26f, cy + size * 0.48f);
            paint.setColor(accent);
            canvas.drawRoundRect(rect, dp(5), dp(5), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(STEEL);
            canvas.drawLine(cx - size * 0.26f, cy + size * 0.02f, cx - size * 0.58f, cy + size * 0.18f, paint);
            canvas.drawLine(cx + size * 0.26f, cy + size * 0.02f, cx + size * 0.58f, cy - size * 0.1f, paint);
        }

        private void drawStackedCards(Canvas canvas, float cx, float cy, float size, int accent) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(STEEL);
            rect.set(cx - size * 0.68f, cy - size * 0.46f, cx + size * 0.18f, cy + size * 0.56f);
            canvas.drawRoundRect(rect, dp(5), dp(5), paint);
            paint.setColor(accent);
            rect.set(cx - size * 0.26f, cy - size * 0.6f, cx + size * 0.62f, cy + size * 0.42f);
            canvas.drawRoundRect(rect, dp(5), dp(5), paint);
            paint.setColor(GOLD);
            canvas.drawCircle(cx + size * 0.2f, cy - size * 0.12f, size * 0.16f, paint);
        }

        private int artAccent(Card card) {
            switch (card.skill) {
                case JAM:
                case SCOUT:
                    return ACCENT;
                case RUSH:
                case BREAK_TIE:
                case COUNTER:
                    return WARM;
                case SHIELD:
                case LOCK:
                case SNIPE:
                    return STEEL;
                case DRAW:
                case CHARGE:
                    return GREEN;
                default:
                    return "地下拳王".equals(card.name) ? RED : ACCENT;
            }
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
