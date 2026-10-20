package ir.quoridorfa.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Iterator;

import ir.quoridorfa.core.GameCore;
import ir.quoridorfa.core.QuoridorAi;

/**
 * Quoridor FA — main activity. Programmatic UI (no XML layouts), full RTL Persian.
 */
public class MainActivity extends Activity implements BoardView.GameCallback {

    // Palette
    static final int COL_BG      = 0xFF0A1626;
    static final int COL_CARD    = 0xFF14243D;
    static final int COL_CARD_ON = 0xFF1C3557;
    static final int COL_BTN     = 0xFF1E3A5F;
    static final int COL_ACCENT  = 0xFF14B8A6;
    static final int COL_ACCENT2 = 0xFFEF5350;
    static final int COL_TEXT    = 0xFFEAF2FB;
    static final int COL_TEXT_DIM= 0xFF9FB3CC;

    private static Typeface fontRegular, fontBold, fontMedium;

    private FrameLayout root;
    private LinearLayout menuPanel, gamePanel;

    private BoardView boardView;
    private TextView topName, topWalls, bottomName, bottomWalls, statusText;
    private LinearLayout topCard, bottomCard;
    private Button btnMode, btnWallDir, btnUndo, btnMenu, btnSound, btnDiffEasy, btnDiffMed, btnDiffHard;

    private GameCore game;
    private final QuoridorAi ai = new QuoridorAi();
    private boolean vsAi = true;
    private int difficulty = QuoridorAi.MEDIUM;
    private boolean soundOn = true;
    private boolean boardLocked = false;
    private boolean wallMode = false;
    private boolean wallHorizontal = true;
    private final ArrayDeque<int[]> undoStack = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator tone;          // created lazily — constructor can fail on some devices
    private boolean toneBroken = false;

    private static final String[] DIFF_NAMES = {"آسان", "متوسط", "سخت"};

    public static Typeface fontReg() { return fontRegular; }
    public static Typeface fontBold() { return fontBold; }
    public static Typeface fontMed()  { return fontMedium; }

    public static String fa(int n) {
        String s = String.valueOf(n);
        char[] d = {'۰','۱','۲','۳','۴','۵','۶','۷','۸','۹'};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '0' && c <= '9' ? d[c - '0'] : c);
        }
        return b.toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Global safety net: ANY uncaught crash (any thread, any callback) must
        // never leave the user with a silent black screen — show diagnostics.
        final Thread.UncaughtExceptionHandler systemHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            try {
                                showFatalError(e);
                            } catch (Throwable ignored) { }
                        }
                    });
                    Thread.sleep(400);   // give the UI one frame
                } catch (Throwable ignored) { }
                if (systemHandler != null) systemHandler.uncaughtException(t, e);
            }
        });
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable t) {
            // NEVER leave the user with a black screen: show what went wrong.
            showFatalError(t);
        }
    }

    private void buildUi() {
        try {
            fontRegular = Typeface.createFromAsset(getAssets(), "fonts/Vazirmatn-Regular.ttf");
            fontBold    = Typeface.createFromAsset(getAssets(), "fonts/Vazirmatn-Bold.ttf");
            fontMedium  = Typeface.createFromAsset(getAssets(), "fonts/Vazirmatn-Medium.ttf");
        } catch (Exception e) {
            fontRegular = Typeface.DEFAULT;
            fontBold = Typeface.DEFAULT_BOLD;
            fontMedium = Typeface.DEFAULT;
        }
        // ToneGenerator is NOT created here — its constructor can crash on
        // some devices; it is built lazily on first beep, fully guarded.

        root = new FrameLayout(this);
        root.setBackgroundColor(COL_BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        buildMenuPanel();
        buildGamePanel();
        setContentView(root);
        showMenu();
    }

    /** Last-resort screen: visible diagnostics instead of a silent black screen. */
    private void showFatalError(Throwable t) {
        try {
            android.widget.ScrollView sc = new android.widget.ScrollView(this);
            LinearLayout ll = new LinearLayout(this);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setPadding(dp(20), dp(40), dp(20), dp(20));
            ll.setBackgroundColor(COL_BG);
            TextView title = new TextView(this);
            title.setText("متأسفانه خطایی رخ داد\nلطفاً از این صفحه عکس بگیرید و برای ما بفرستید");
            title.setTextColor(COL_ACCENT2);
            title.setTextSize(16);
            title.setTypeface(fontBold != null ? fontBold : Typeface.DEFAULT_BOLD);
            TextView detail = new TextView(this);
            detail.setText(android.util.Log.getStackTraceString(t));
            detail.setTextColor(COL_TEXT_DIM);
            detail.setTextSize(11);
            detail.setPadding(0, dp(16), 0, 0);
            ll.addView(title);
            ll.addView(detail);
            sc.addView(ll);
            setContentView(sc);
        } catch (Throwable ignored) {
        }
    }

    // ================= menu =================

    private TextView menuTitle(String t, int size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setTypeface(bold ? fontBold : fontMedium);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private Button menuButton(String label, int bg, int fg, float sizeDp) {
        Button b = new Button(this, null, 0);
        b.setText(label);
        b.setTextSize(sizeDp);
        b.setTextColor(fg);
        b.setTypeface(fontBold);
        b.setAllCaps(false);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(14));
        b.setBackground(gd);
        b.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildMenuPanel() {
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setPadding(dp(28), dp(40), dp(28), dp(28));
        menuPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        menuPanel.setBackgroundColor(COL_BG);

        TextView title = menuTitle("کوریدور", 44, COL_ACCENT, true);
        TextView sub = menuTitle("بازی استراتژیک کلاسیک ۹×۹ — فارسی", 15, COL_TEXT_DIM, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(18);
        sub.setLayoutParams(tp);

        // decorative mini-board
        View deco = new MenuArt(this);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(120), dp(120));
        dp2.topMargin = dp(18);
        deco.setLayoutParams(dp2);

        Button btnAi = menuButton("بازی با ربات", COL_ACCENT, 0xFF06251F, 18);
        Button btn2p = menuButton("بازی دونفره", COL_BTN, COL_TEXT, 18);
        btnAi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startGame(true); }
        });
        btn2p.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startGame(false); }
        });

        // difficulty row
        LinearLayout diffRow = new LinearLayout(this);
        diffRow.setOrientation(LinearLayout.HORIZONTAL);
        diffRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        drp.topMargin = dp(14);
        diffRow.setLayoutParams(drp);

        TextView diffLabel = new TextView(this);
        diffLabel.setText("سطح ربات:");
        diffLabel.setTextColor(COL_TEXT_DIM);
        diffLabel.setTypeface(fontMedium);
        diffLabel.setTextSize(14);
        diffLabel.setPadding(0, 0, dp(10), 0);

        btnDiffEasy = diffBtn(DIFF_NAMES[0], QuoridorAi.EASY);
        btnDiffMed  = diffBtn(DIFF_NAMES[1], QuoridorAi.MEDIUM);
        btnDiffHard = diffBtn(DIFF_NAMES[2], QuoridorAi.HARD);
        diffRow.addView(diffLabel);
        diffRow.addView(btnDiffEasy);
        diffRow.addView(btnDiffMed);
        diffRow.addView(btnDiffHard);

        btnSound = menuButton("صدا: روشن", COL_BTN, COL_TEXT, 15);
        btnSound.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                soundOn = !soundOn;
                btnSound.setText(soundOn ? "صدا: روشن" : "صدا: خاموش");
            }
        });

        Button btnHelp = menuButton("راهنما و قوانین", COL_BTN, COL_TEXT, 15);
        btnHelp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHelp(); }
        });

        TextView ver = menuTitle("نسخه " + fa(1) + "." + fa(0) + "." + fa(1) + " — Quoridor FA", 12, COL_TEXT_DIM, false);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.topMargin = dp(16);
        ver.setLayoutParams(vp);

        ScrollViewWrap wrap = new ScrollViewWrap(this);
        wrap.addView(menuPanel);
        // NOTE: menuPanel gets added to wrap; we add wrap to root on demand
        menuWrap = wrap;
    }

    private LinearLayout menuWrapHolder;
    private Object menuWrapObj;

    private View menuWrap;

    private void showMenu() {
        root.removeAllViews();
        root.addView(menuWrap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private Button diffBtn(String label, final int level) {
        Button b = new Button(this, null, 0);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(fontBold);
        b.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { difficulty = level; refreshDiffButtons(); }
        });
        styleDiff(b, level == difficulty);
        return b;
    }

    private void refreshDiffButtons() {
        styleDiff(btnDiffEasy, difficulty == QuoridorAi.EASY);
        styleDiff(btnDiffMed,  difficulty == QuoridorAi.MEDIUM);
        styleDiff(btnDiffHard, difficulty == QuoridorAi.HARD);
    }

    private void styleDiff(Button b, boolean on) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(on ? COL_ACCENT : COL_BTN);
        gd.setCornerRadius(dp(10));
        b.setBackground(gd);
        b.setTextColor(on ? 0xFF06251F : COL_TEXT);
    }

    private void showHelp() {
        String msg = "هدف: مهرهٔ خود را زودتر از حریف به ردیف مقابل صفحه برسانید.\n\n"
                + "در هر نوبت یکی از دو کار را انجام دهید:\n"
                + "• حرکت: یک خانه به طرفین (و در صورت مجاورت با حریف، پرش روی آن) — خانه‌های مجاز با نقطه نشان داده می‌شوند.\n"
                + "• دیوار: ۱۰ دیوار دارید؛ با حالت «دیوار» انگشت را روی صفحه بکشید تا دیوار افقی یا عمودی ساخته شود.\n\n"
                + "قانون طلایی: هیچ دیواری نمی‌تواند راه حریف را کاملاً ببندد.\n\n"
                + "برد: اولین بازیکنی که به ردیف مقابل برسد برنده است.";
        new AlertDialog.Builder(this)
                .setTitle("راهنما")
                .setMessage(msg)
                .setPositiveButton("فهمیدم", null)
                .show();
    }

    // ================= game =================

    private void buildGamePanel() {
        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setPadding(dp(10), dp(8), dp(10), dp(10));
        gamePanel.setBackgroundColor(COL_BG);

        // top card = player 1 (AI or second player)
        topCard = playerCard();
        topName = (TextView) topCard.getTag(R.id.tag_name);
        topWalls = (TextView) topCard.getTag(R.id.tag_walls);
        LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        tclp.topMargin = dp(4);
        gamePanel.addView(topCard, tclp);

        // status line
        statusText = new TextView(this);
        statusText.setTextSize(13);
        statusText.setTextColor(COL_TEXT_DIM);
        statusText.setTypeface(fontMedium);
        statusText.setGravity(Gravity.CENTER);
        gamePanel.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // board
        boardView = new BoardView(this);
        boardView.setCallback(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.topMargin = dp(6);
        blp.bottomMargin = dp(6);
        gamePanel.addView(boardView, blp);

        // bottom card = player 0 (human)
        bottomCard = playerCard();
        bottomName = (TextView) bottomCard.getTag(R.id.tag_name);
        bottomWalls = (TextView) bottomCard.getTag(R.id.tag_walls);
        gamePanel.addView(bottomCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        // controls
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(8);
        controls.setLayoutParams(clp);

        btnMode = ctrlBtn("حالت: حرکت");
        btnWallDir = ctrlBtn("دیوار: افقی");
        btnUndo = ctrlBtn("بازگشت");
        btnMenu = ctrlBtn("منو");
        btnMode.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                wallMode = !wallMode;
                btnMode.setText(wallMode ? "حالت: دیوار" : "حالت: حرکت");
                boardView.setWallMode(wallMode);
                boardView.refresh();
            }
        });
        btnWallDir.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                wallHorizontal = !wallHorizontal;
                btnWallDir.setText(wallHorizontal ? "دیوار: افقی" : "دیوار: عمودی");
                boardView.setDefaultWallH(wallHorizontal);
            }
        });
        btnUndo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doUndo(); }
        });
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        });
        controls.addView(btnMode);
        controls.addView(btnWallDir);
        controls.addView(btnUndo);
        controls.addView(btnMenu);
        gamePanel.addView(controls);
    }

    private LinearLayout playerCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), 0, dp(14), 0);

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(16);
        dot.setTag(R.id.tag_dot);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);

        TextView name = new TextView(this);
        name.setTextSize(15);
        name.setTypeface(fontBold);
        name.setTextColor(COL_TEXT);
        name.setTag(R.id.tag_name);

        TextView walls = new TextView(this);
        walls.setTextSize(12);
        walls.setTypeface(fontMedium);
        walls.setTextColor(COL_TEXT_DIM);
        walls.setTag(R.id.tag_walls);

        texts.addView(name);
        texts.addView(walls);
        card.addView(dot);
        card.addView(texts);
        card.setTag(R.id.tag_card, new GradientDrawable());
        return card;
    }

    private Button ctrlBtn(String label) {
        Button b = new Button(this, null, 0);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(COL_TEXT);
        b.setTypeface(fontBold);
        b.setAllCaps(false);
        b.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(COL_BTN);
        gd.setCornerRadius(dp(10));
        b.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, dp(42), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void startGame(boolean vsAiMode) {
        try {
            startGameInner(vsAiMode);
        } catch (Throwable t) {
            showFatalError(t);
        }
    }

    private void startGameInner(boolean vsAiMode) {
        vsAi = vsAiMode;
        game = new GameCore();
        undoStack.clear();
        wallMode = false;
        boardLocked = false;
        btnMode.setText("حالت: حرکت");
        boardView.setWallMode(false);
        topName.setText(vsAi ? "ربات (" + DIFF_NAMES[difficulty] + ")" : "بازیکن ۲ (سرخ)");
        bottomName.setText(vsAi ? "شما (فیروزه‌ای)" : "بازیکن ۱ (فیروزه‌ای)");
        topName.setTextColor(COL_ACCENT2);
        bottomName.setTextColor(COL_ACCENT);
        ((TextView) topCard.getTag(R.id.tag_dot)).setTextColor(COL_ACCENT2);
        ((TextView) bottomCard.getTag(R.id.tag_dot)).setTextColor(COL_ACCENT);
        root.removeAllViews();
        root.addView(gamePanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refreshUi();
        maybeScheduleAi();
    }

    private void refreshUi() {
        boardView.setGame(game);
        topWalls.setText("دیوارها: " + fa(game.wallsLeft[1]));
        bottomWalls.setText("دیوارها: " + fa(game.wallsLeft[0]));
        boolean topTurn = game.turn == 1 && game.winner == -1;
        boolean bottomTurn = game.turn == 0 && game.winner == -1;
        styleCard(topCard, topTurn);
        styleCard(bottomCard, bottomTurn);
        String st;
        if (game.winner != -1) st = "پایان بازی";
        else if (boardLocked) st = "ربات در حال فکر کردن…";
        else st = game.turn == 0 ? "نوبت بازیکن فیروزه‌ای" : "نوبت بازیکن سرخ";
        statusText.setText(st);
    }

    private void styleCard(LinearLayout card, boolean active) {
        GradientDrawable gd = (GradientDrawable) card.getTag(R.id.tag_card);
        gd.setColor(active ? COL_CARD_ON : COL_CARD);
        gd.setCornerRadius(dp(14));
        gd.setStroke(active ? dp(2) : 0, active ? COL_ACCENT : 0);
        card.setBackground(gd);
    }

    private void beep(int toneId) {
        if (!soundOn || toneBroken) return;
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
            tone.startTone(toneId, 150);
        } catch (Throwable t) {
            toneBroken = true;   // audio must never crash the game
        }
    }

    // ---------- GameCallback ----------

    @Override
    public void onPawnMoveRequested(int r, int c) {
        if (boardLocked || game == null || game.winner != -1 || game.turn != 0) return;
        undoStack.push(game.snapshot());
        if (game.applyPawnMove(r, c)) {
            boardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            beep(ToneGenerator.TONE_PROP_BEEP);
            afterAction();
        } else {
            undoStack.pop();
        }
    }

    @Override
    public void onWallRequested(boolean horizontal, int r, int c) {
        if (boardLocked || game == null || game.winner != -1 || game.turn != 0) return;
        if (game.wallsLeft[0] <= 0) {
            Toast.makeText(this, "دیوارهای شما تمام شده است!", Toast.LENGTH_SHORT).show();
            return;
        }
        undoStack.push(game.snapshot());
        if (game.placeWall(horizontal, r, c)) {
            boardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            beep(ToneGenerator.TONE_PROP_BEEP2);
            afterAction();
        } else {
            undoStack.pop();
            Toast.makeText(this, "این دیوار مجاز نیست (راه حریف نباید بسته شود)", Toast.LENGTH_SHORT).show();
        }
    }

    private void afterAction() {
        refreshUi();
        if (game.winner != -1) {
            announceWin();
            return;
        }
        maybeScheduleAi();
    }

    private void maybeScheduleAi() {
        if (!vsAi || game.winner != -1 || game.turn != 1) return;
        boardLocked = true;
        refreshUi();
        final GameCore gameRef = game;   // guard vs. restarts while AI thinks
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final int[] action;
                        try {
                            action = ai.chooseAction(gameRef, 1, difficulty);
                        } catch (Throwable t) {
                            handler.post(new Runnable() {
                                @Override public void run() {
                                    boardLocked = false;
                                    refreshUi();
                                }
                            });
                            return;
                        }
                        handler.post(new Runnable() {
                            @Override public void run() {
                                try {
                                    boardLocked = false;
                                    if (game == gameRef && action != null
                                            && game.winner == -1 && game.turn == 1) {
                                        undoStack.push(game.snapshot());
                                        game.applyAction(action);
                                        beep(action[0] == GameCore.ACT_MOVE
                                                ? ToneGenerator.TONE_PROP_BEEP
                                                : ToneGenerator.TONE_PROP_BEEP2);
                                    }
                                    refreshUi();
                                    if (game.winner != -1) announceWin();
                                } catch (Throwable t) {
                                    showFatalError(t);
                                }
                            }
                        });
                    }
                }).start();
            }
        }, 350);
    }

    private void doUndo() {
        if (boardLocked || undoStack.isEmpty()) return;
        game.restore(undoStack.pop());
        if (vsAi && game.turn == 1 && !undoStack.isEmpty()) {
            game.restore(undoStack.pop());
        }
        game.winner = -1;
        beep(ToneGenerator.TONE_PROP_NACK);
        refreshUi();
    }

    private void announceWin() {
        String winnerName;
        if (game.winner == 0) {
            winnerName = vsAi ? "شما بردید! 🏆" : "بازیکن فیروزه‌ای برد! 🏆";
        } else {
            winnerName = vsAi ? "ربات برد!" : "بازیکن سرخ برد! 🏆";
        }
        beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD);
        new AlertDialog.Builder(this)
                .setTitle("پایان بازی")
                .setMessage(winnerName + "\n\nتعداد حرکت‌ها: " + fa(game.moveCount))
                .setPositiveButton("بازی دوباره", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        startGame(vsAi);
                    }
                })
                .setNegativeButton("منو", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        showMenu();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // simple wrapper so we can reference the menu scroll view
    private class ScrollViewWrap extends android.widget.ScrollView {
        ScrollViewWrap(android.content.Context c) {
            super(c);
            setVerticalScrollBarEnabled(false);
            setFillViewport(true);
        }
    }

    private static class MenuArt extends View {
        MenuArt(android.content.Context c) { super(c); }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            float w = getWidth(), h = getHeight();
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            // board base
            p.setColor(0xFF4E342E);
            canvas.drawRoundRect(dp2(w * 0.06f), dp2(w * 0.06f), w - dp2(w * 0.06f), h - dp2(w * 0.06f), dp2(w * 0.12f), dp2(w * 0.12f), p);
            p.setColor(0xFFEAD9B8);
            float m = w * 0.18f;
            canvas.drawRoundRect(dp2(m), dp2(m), w - dp2(m), h - dp2(m), dp2(w * 0.06f), dp2(w * 0.06f), p);
            // grid lines
            p.setColor(0x665D4037);
            p.setStrokeWidth(Math.max(2f, w * 0.02f));
            float span = (w - 2 * m) / 3f;
            for (int i = 1; i < 3; i++) {
                canvas.drawLine(m + span * i, m, m + span * i, h - m, p);
                canvas.drawLine(m, m + span * i, w - m, m + span * i, p);
            }
            // wall bar
            p.setColor(0xFF3E2723);
            canvas.drawRoundRect(dp2(m + span * 0.15f), dp2(m + span * 1f - w * 0.035f),
                    dp2(w - m - span * 0.15f), dp2(m + span * 1f + w * 0.035f),
                    dp2(w * 0.03f), dp2(w * 0.03f), p);
            // pawns
            p.setColor(0xFF0F9D8F);
            canvas.drawCircle(w / 2f, m + span * 1.5f, span * 0.32f, p);
            p.setColor(0xFFD32F2F);
            canvas.drawCircle(w / 2f, m + span * 0.5f, span * 0.32f, p);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(w * 0.025f);
            p.setColor(0xFFFFFFFF);
            canvas.drawCircle(w / 2f, m + span * 1.5f, span * 0.32f, p);
            canvas.drawCircle(w / 2f, m + span * 0.5f, span * 0.32f, p);
        }

        private static float dp2(float v) { return v; }
    }
}
