package com.xingyu.music.desktop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.xingyu.music.MainActivity;
import com.xingyu.music.R;
import com.xingyu.music.data.LyricCacheStore;
import com.xingyu.music.data.LyricParser;
import com.xingyu.music.data.NeteaseApi;
import com.xingyu.music.data.QQLyricApi;
import com.xingyu.music.model.LyricLine;
import com.xingyu.music.model.PlaybackSnapshot;
import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;
import com.xingyu.music.playback.PlaybackService;
import com.xingyu.music.ui.DesktopLyricLaneView;
import com.xingyu.music.ui.IconView;
import com.xingyu.music.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lunaxy desktop lyrics: stable Lyric Lane + compact floating controls.
 *
 * The lyric rendering path deliberately returns to the V3 stable TextView/native-marquee logic.
 * The compact lock/font/colour palette is kept as an independent control layer so changing controls
 * never owns lyric animation or playback state. Playback/provider routing remains untouched.
 */
public final class DesktopLyricService extends Service {
    private static final String PREFS = "lunaxy_desktop_lyric_v1";
    private static final String UI_PREFS = "xingyu_ui_settings";
    public static final String ACTION_REFRESH = "com.xingyu.music.desktop.REFRESH";
    public static final String ACTION_ENABLE = "com.xingyu.music.desktop.ENABLE";
    public static final String ACTION_STOP = "com.xingyu.music.desktop.STOP";
    public static final String ACTION_TOGGLE_LOCK = "com.xingyu.music.desktop.TOGGLE_LOCK";
    public static final String ACTION_UNLOCK = "com.xingyu.music.desktop.UNLOCK";
    private static final String CHANNEL = "lunaxy_desktop_lyrics";
    private static final int NOTIFICATION_ID = 4301;

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LOCKED = "locked";
    private static final String KEY_DOUBLE = "double_line";
    private static final String KEY_Y = "y";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_COLOR_COVER = "cover_color";
    private static final String KEY_COLOR_PRESET = "color_preset";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final String KEY_LAYOUT_VERSION = "layout_version";

    private static final String PRESET_COVER = "cover";
    private static final String PRESET_CUSTOM = "custom";
    private static final String PRESET_ICE = "ice";
    private static final String PRESET_MINT = "mint";
    private static final String PRESET_GOLD = "gold";
    private static final String PRESET_VIOLET = "violet";
    private static final String PRESET_PINK = "pink";
    private static final String PRESET_SILVER = "silver";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService lyricIo = Executors.newSingleThreadExecutor();
    private final NeteaseApi netease = new NeteaseApi();
    private final QQLyricApi qq = new QQLyricApi();

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private LinearLayout root;
    private DesktopLyricLaneView currentLine;
    private DesktopLyricLaneView nextLine;
    private LinearLayout controlPanel;
    private PlaybackService playback;
    private boolean bound;
    private boolean locked;
    private boolean doubleLine;
    private int lyricToken;
    private String songKey = "";
    private String renderedLine = "";
    private List<LyricLine> lines = new ArrayList<>();
    private float downRawX;
    private float downRawY;
    private long downAt;
    private int startY;
    private boolean dragging;

    private final Runnable hideControls = this::hideControlPanel;

    private final ServiceConnection playbackConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            playback = ((PlaybackService.LocalBinder) binder).getService();
            bound = true;
            main.postDelayed(() -> { if (playback != null) playback.refreshSystemSurface(); }, 80L);
        }
        @Override public void onServiceDisconnected(ComponentName name) { bound = false; playback = null; }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try { updateFromPlayback(); } catch (Exception ignored) { }
            main.postDelayed(this, 180L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            prefs(this).edit().putBoolean(KEY_ENABLED, false).apply();
            stopSelf();
            return;
        }
        SharedPreferences p = prefs(this);
        if (p.getInt(KEY_LAYOUT_VERSION, 0) < 5) {
            // V5 keeps the proven V3 lyric renderer while preserving top/double-line/cover defaults.
            p.edit().remove(KEY_Y)
                    .putBoolean(KEY_DOUBLE, true)
                    .putBoolean(KEY_COLOR_COVER, true)
                    .putString(KEY_COLOR_PRESET, PRESET_COVER)
                    .putFloat(KEY_FONT_SCALE, 1f)
                    .putInt(KEY_LAYOUT_VERSION, 5)
                    .apply();
        }
        p.edit().putBoolean(KEY_ENABLED, true).apply();
        locked = p.getBoolean(KEY_LOCKED, false);
        doubleLine = p.getBoolean(KEY_DOUBLE, true);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildOverlay();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        bindService(new Intent(this, PlaybackService.class), playbackConnection, BIND_AUTO_CREATE);
        main.post(ticker);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) { disableAndStop(); return START_NOT_STICKY; }
        if (ACTION_TOGGLE_LOCK.equals(action)) prefs(this).edit().putBoolean(KEY_LOCKED, !locked(this)).apply();
        else if (ACTION_UNLOCK.equals(action)) prefs(this).edit().putBoolean(KEY_LOCKED, false).apply();
        else if (ACTION_ENABLE.equals(action)) prefs(this).edit().putBoolean(KEY_ENABLED, true).apply();
        if (root != null) applyPrefs();
        updateForegroundNotification();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    public static boolean enabled(Context c) { return c != null && prefs(c).getBoolean(KEY_ENABLED, false); }
    public static boolean locked(Context c) { return c != null && prefs(c).getBoolean(KEY_LOCKED, false); }
    public static boolean doubleLine(Context c) { return c == null || prefs(c).getBoolean(KEY_DOUBLE, true); }
    public static boolean useCoverColor(Context c) { return c == null || prefs(c).getBoolean(KEY_COLOR_COVER, true); }
    public static float fontScale(Context c) { return c == null ? 1f : clampScale(prefs(c).getFloat(KEY_FONT_SCALE, 1f)); }
    public static String colorPreset(Context c) { return c == null ? PRESET_COVER : prefs(c).getString(KEY_COLOR_PRESET, PRESET_COVER); }

    public static void start(Context c) {
        if (c == null || !Settings.canDrawOverlays(c)) return;
        prefs(c).edit().putBoolean(KEY_ENABLED, true).putBoolean(KEY_DOUBLE, prefs(c).getBoolean(KEY_DOUBLE, true)).apply();
        startServiceCompat(c, new Intent(c, DesktopLyricService.class).setAction(ACTION_ENABLE));
    }

    public static void stop(Context c) {
        if (c == null) return;
        prefs(c).edit().putBoolean(KEY_ENABLED, false).apply();
        startServiceCompat(c, new Intent(c, DesktopLyricService.class).setAction(ACTION_STOP));
    }

    public static void toggleFromSystemSurface(Context c) {
        if (c == null || !Settings.canDrawOverlays(c)) return;
        if (!enabled(c)) start(c); else setLocked(c, !locked(c));
    }

    public static void setLocked(Context c, boolean value) {
        if (c == null) return;
        prefs(c).edit().putBoolean(KEY_LOCKED, value).apply();
        if (enabled(c)) refresh(c);
    }

    public static void setDoubleLine(Context c, boolean value) {
        if (c == null) return;
        prefs(c).edit().putBoolean(KEY_DOUBLE, value).apply();
        if (enabled(c)) refresh(c);
    }

    public static void setUseCoverColor(Context c, boolean value) {
        if (c == null) return;
        SharedPreferences.Editor e = prefs(c).edit().putBoolean(KEY_COLOR_COVER, value);
        if (value) e.putString(KEY_COLOR_PRESET, PRESET_COVER);
        else if (PRESET_COVER.equals(colorPreset(c))) e.putString(KEY_COLOR_PRESET, PRESET_CUSTOM);
        e.apply();
        if (enabled(c)) refresh(c);
    }

    public static void setColorPreset(Context c, String preset) {
        if (c == null) return;
        String value = normalizePreset(preset);
        prefs(c).edit().putString(KEY_COLOR_PRESET, value).putBoolean(KEY_COLOR_COVER, PRESET_COVER.equals(value)).apply();
        if (enabled(c)) refresh(c);
    }

    public static void adjustFontScale(Context c, float delta) {
        if (c == null) return;
        float next = clampScale(fontScale(c) + delta);
        prefs(c).edit().putFloat(KEY_FONT_SCALE, next).apply();
        if (enabled(c)) refresh(c);
    }

    public static void resetPosition(Context c) {
        if (c == null) return;
        prefs(c).edit().remove(KEY_Y).apply();
        if (enabled(c)) refresh(c);
    }

    public static void setAccent(Context c, int accent) {
        if (c == null) return;
        prefs(c).edit().putInt(KEY_ACCENT, accent).apply();
        if (enabled(c)) refresh(c);
    }

    public static void refreshStyle(Context c) { if (c != null && enabled(c)) refresh(c); }

    private static void refresh(Context c) { startServiceCompat(c, new Intent(c, DesktopLyricService.class).setAction(ACTION_REFRESH)); }
    private static void startServiceCompat(Context c, Intent i) {
        try {
            Context app = c.getApplicationContext();
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i); else app.startService(i);
        } catch (Exception ignored) { }
    }
    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private void buildOverlay() {
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = prefs(this).getInt(KEY_Y, defaultTopY());

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 3), Ui.dp(this, 18), Ui.dp(this, 3));
        root.setBackgroundColor(Color.TRANSPARENT);

        currentLine = new DesktopLyricLaneView(this);
        currentLine.setPreferredTextSize(18.6f, 15.8f);
        currentLine.setOutline(.82f, Color.argb(215, 0, 0, 0));
        root.addView(currentLine, new LinearLayout.LayoutParams(-1, Ui.dp(this, 32)));

        nextLine = new DesktopLyricLaneView(this);
        nextLine.setPreferredTextSize(14.9f, 13.9f);
        nextLine.setOutline(.68f, Color.argb(195, 0, 0, 0));
        root.addView(nextLine, new LinearLayout.LayoutParams(-1, Ui.dp(this, 26)));

        controlPanel = buildControlPanel();
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(this, 292), Ui.dp(this, 92));
        cp.topMargin = Ui.dp(this, 5);
        root.addView(controlPanel, cp);

        root.setOnTouchListener(this::handleDrag);
        wm.addView(root, lp);
        applyPrefs();
    }

    private int defaultTopY() {
        int status = 0;
        try {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) status = getResources().getDimensionPixelSize(id);
        } catch (Exception ignored) { }
        return Math.max(Ui.dp(this, 34), status + Ui.dp(this, 18));
    }

    private boolean handleDrag(View v, MotionEvent e) {
        if (locked || lp == null || wm == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                downAt = System.currentTimeMillis();
                startY = lp.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                if (!dragging && System.currentTimeMillis() - downAt >= 210L
                        && Math.hypot(dx, dy) > Ui.dp(this, 5)) dragging = true;
                if (dragging) {
                    hideControlPanel();
                    int sh = getResources().getDisplayMetrics().heightPixels;
                    lp.y = Math.max(Ui.dp(this, 20), Math.min(sh - Ui.dp(this, 150), startY + Math.round(dy)));
                    try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float moveX = e.getRawX() - downRawX;
                float moveY = e.getRawY() - downRawY;
                long held = System.currentTimeMillis() - downAt;
                if (dragging) prefs(this).edit().putInt(KEY_Y, lp.y).apply();
                else if (e.getActionMasked() == MotionEvent.ACTION_UP && held < 520L
                        && Math.hypot(moveX, moveY) < Ui.dp(this, 8)) toggleControlPanel();
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    private void applyPrefs() {
        if (root == null) return;
        SharedPreferences p = prefs(this);
        locked = p.getBoolean(KEY_LOCKED, false);
        doubleLine = p.getBoolean(KEY_DOUBLE, true);
        float scale = clampScale(p.getFloat(KEY_FONT_SCALE, 1f));
        currentLine.setPreferredTextSize(18.6f * scale, 15.8f * scale);
        nextLine.setPreferredTextSize(14.9f * scale, 13.9f * scale);
        nextLine.setVisibility(doubleLine ? View.VISIBLE : View.GONE);
        // V3 renderer invariant: the lyric lane itself is always visible when the service is alive.
        currentLine.setVisibility(View.VISIBLE);
        currentLine.animate().cancel();
        currentLine.setAlpha(1f);
        currentLine.setTranslationY(0f);
        if (doubleLine) { nextLine.setAlpha(.78f); nextLine.setTranslationY(0f); }
        applyLyricPalette();
        if (locked) hideControlPanel();
        if (lp != null) {
            if (locked) lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            lp.alpha = 1f;
            lp.x = 0;
            lp.y = p.contains(KEY_Y) ? p.getInt(KEY_Y, lp.y) : defaultTopY();
        }
        updateWindow();
        updateForegroundNotification();
        if (playback != null) playback.refreshSystemSurface();
    }

    private void applyLyricPalette() {
        if (currentLine == null || nextLine == null) return;
        SharedPreferences p = prefs(this);
        SharedPreferences ui = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        String preset = normalizePreset(p.getString(KEY_COLOR_PRESET, PRESET_COVER));
        boolean cover = p.getBoolean(KEY_COLOR_COVER, true) || PRESET_COVER.equals(preset);
        int start;
        int end;
        if (cover) {
            int base = readableAccent(p.getInt(KEY_ACCENT, Ui.CYAN));
            start = Ui.mix(base, Color.WHITE, .05f);
            end = Ui.mix(base, Color.WHITE, .20f);
        } else {
            int[] pair = presetPair(preset);
            if (pair != null) {
                start = readableAccent(pair[0]);
                end = readableAccent(pair[1]);
            } else {
                start = readableAccent(ui.getInt("lyric_gradient_start", Ui.LYRIC_ICE_START));
                end = readableAccent(ui.getInt("lyric_gradient_end", Ui.LYRIC_ICE_END));
            }
        }
        currentLine.setGradientColors(start, end);
        int secondary = Ui.mix(end, Color.WHITE, .30f);
        nextLine.setGradientColors(secondary, secondary);
        nextLine.setAlpha(.78f);
    }

    private static int readableAccent(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(.26f, Math.min(.76f, hsv[1]));
        hsv[2] = Math.max(.90f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    private LinearLayout buildControlPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(Ui.dp(this, 8), Ui.dp(this, 7), Ui.dp(this, 8), Ui.dp(this, 7));
        panel.setBackground(Ui.glass(150, 18, 20, this));
        panel.setElevation(Ui.dp(this, 7));
        panel.setVisibility(View.GONE);
        panel.setAlpha(0f);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER);
        FrameLayout lockButton = miniIconButton(IconView.Type.UNLOCK, "锁定歌词", () -> {
            hideControlPanel();
            main.postDelayed(() -> {
                prefs(this).edit().putBoolean(KEY_LOCKED, true).apply();
                applyPrefs();
            }, 80L);
        });
        top.addView(lockButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 37), 1f));
        TextView bigger = miniTextButton("A+", "增大字号", () -> {
            setFontScaleLocal(fontScale(this) + .08f);
            keepControlsAlive();
        });
        top.addView(bigger, spacedControlLp());
        TextView smaller = miniTextButton("A−", "减小字号", () -> {
            setFontScaleLocal(fontScale(this) - .08f);
            keepControlsAlive();
        });
        top.addView(smaller, spacedControlLp());
        FrameLayout close = miniIconButton(IconView.Type.CLOSE, "关闭桌面歌词", this::disableAndStop);
        top.addView(close, spacedControlLp());
        panel.addView(top, new LinearLayout.LayoutParams(-1, Ui.dp(this, 37)));

        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setGravity(Gravity.CENTER);
        String[] presets = new String[]{PRESET_ICE, PRESET_MINT, PRESET_GOLD, PRESET_VIOLET, PRESET_PINK, PRESET_SILVER, PRESET_COVER};
        int[] dots = new int[]{0xFF7EDBFF, 0xFF66E3BC, 0xFFFFD37F, 0xFFBEA7FF, 0xFFFF91C8, 0xFFF0F2F6, Color.WHITE};
        for (int i = 0; i < presets.length; i++) {
            final String preset = presets[i];
            View dot = colorDot(preset, dots[i], PRESET_COVER.equals(preset));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 31), Ui.dp(this, 31));
            if (i > 0) dp.leftMargin = Ui.dp(this, 5);
            colors.addView(dot, dp);
        }
        LinearLayout.LayoutParams colorsLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 31));
        colorsLp.topMargin = Ui.dp(this, 5);
        panel.addView(colors, colorsLp);
        return panel;
    }

    private LinearLayout.LayoutParams spacedControlLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 37), 1f);
        lp.leftMargin = Ui.dp(this, 6);
        return lp;
    }

    private FrameLayout miniIconButton(IconView.Type type, String description, Runnable action) {
        FrameLayout button = Ui.iconButton(this, type, 35, Ui.TEXT, Color.argb(20, 255, 255, 255));
        button.setContentDescription(description);
        button.setOnClickListener(v -> { keepControlsAlive(); action.run(); });
        return button;
    }

    private TextView miniTextButton(String label, String description, Runnable action) {
        TextView button = Ui.text(this, label, 12.2f, Ui.TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(Ui.glass(34, 12, 16, this));
        button.setClickable(true);
        Ui.applyRipple(button, Color.TRANSPARENT);
        button.setOnClickListener(v -> { keepControlsAlive(); action.run(); });
        return button;
    }

    private View colorDot(String preset, int color, boolean rainbow) {
        FrameLayout shell = new FrameLayout(this);
        String selected = normalizePreset(prefs(this).getString(KEY_COLOR_PRESET, PRESET_COVER));
        boolean on = preset.equals(selected) || (rainbow && prefs(this).getBoolean(KEY_COLOR_COVER, true));
        shell.setBackground(on
                ? Ui.stroke(Color.argb(16,255,255,255), 16, Color.argb(205,255,255,255), this)
                : Ui.round(Color.TRANSPARENT, 16, this));
        View dot = new View(this);
        if (rainbow) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFFFF7A8A,0xFFFFD66E,0xFF73E6A8,0xFF72CCFF,0xFFB69BFF,0xFFFF91CE});
            g.setShape(GradientDrawable.OVAL);
            dot.setBackground(g);
        } else {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color);
            dot.setBackground(g);
        }
        shell.addView(dot, frame(Ui.dp(this, 20), Ui.dp(this, 20), Gravity.CENTER));
        shell.setClickable(true);
        shell.setOnClickListener(v -> {
            prefs(this).edit().putString(KEY_COLOR_PRESET, preset)
                    .putBoolean(KEY_COLOR_COVER, PRESET_COVER.equals(preset)).apply();
            applyLyricPalette();
            rebuildControlPanel(true);
        });
        return shell;
    }

    private FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    private void setFontScaleLocal(float scale) {
        prefs(this).edit().putFloat(KEY_FONT_SCALE, clampScale(scale)).apply();
        applyPrefs();
    }

    private void rebuildControlPanel(boolean show) {
        if (root == null || controlPanel == null) return;
        int index = root.indexOfChild(controlPanel);
        root.removeView(controlPanel);
        controlPanel = buildControlPanel();
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(this, 292), Ui.dp(this, 92));
        cp.topMargin = Ui.dp(this, 5);
        if (index < 0 || index > root.getChildCount()) root.addView(controlPanel, cp);
        else root.addView(controlPanel, index, cp);
        if (show && !locked) showControlPanel();
    }

    private void toggleControlPanel() {
        if (locked || controlPanel == null) return;
        if (controlPanel.getVisibility() == View.VISIBLE) hideControlPanel(); else showControlPanel();
    }

    private void showControlPanel() {
        if (locked || controlPanel == null) return;
        main.removeCallbacks(hideControls);
        controlPanel.animate().cancel();
        controlPanel.setVisibility(View.VISIBLE);
        controlPanel.setAlpha(0f);
        controlPanel.setTranslationY(-Ui.dp(this, 3));
        controlPanel.animate().alpha(1f).translationY(0f).setDuration(150L).start();
        main.postDelayed(hideControls, 3600L);
        updateWindow();
    }

    private void hideControlPanel() {
        main.removeCallbacks(hideControls);
        if (controlPanel == null || controlPanel.getVisibility() != View.VISIBLE) return;
        controlPanel.animate().cancel();
        controlPanel.animate().alpha(0f).translationY(-Ui.dp(this, 2)).setDuration(120L).withEndAction(() -> {
            if (controlPanel != null) {
                controlPanel.setVisibility(View.GONE);
                controlPanel.setTranslationY(0f);
                updateWindow();
            }
        }).start();
    }

    private void keepControlsAlive() {
        main.removeCallbacks(hideControls);
        if (controlPanel != null && controlPanel.getVisibility() == View.VISIBLE) main.postDelayed(hideControls, 3600L);
    }

    private static float clampScale(float value) { return Math.max(.78f, Math.min(1.34f, value)); }
    private static String normalizePreset(String preset) {
        String p = preset == null ? PRESET_COVER : preset.trim().toLowerCase(Locale.ROOT);
        switch (p) {
            case PRESET_ICE:
            case PRESET_MINT:
            case PRESET_GOLD:
            case PRESET_VIOLET:
            case PRESET_PINK:
            case PRESET_SILVER:
            case PRESET_CUSTOM:
            case PRESET_COVER:
                return p;
            default:
                return PRESET_COVER;
        }
    }

    private static int[] presetPair(String preset) {
        switch (normalizePreset(preset)) {
            case PRESET_ICE: return new int[]{0xFF70D6FF, 0xFFB9ECFF};
            case PRESET_MINT: return new int[]{0xFF50DDB0, 0xFF9AF0D3};
            case PRESET_GOLD: return new int[]{0xFFFFC95F, 0xFFFFE5A1};
            case PRESET_VIOLET: return new int[]{0xFFB397FF, 0xFFDCCFFF};
            case PRESET_PINK: return new int[]{0xFFFF82BC, 0xFFFFBEDC};
            case PRESET_SILVER: return new int[]{0xFFF4F6FA, 0xFFC7CFDA};
            default: return null;
        }
    }

    private void disableAndStop() {
        prefs(this).edit().putBoolean(KEY_ENABLED, false).apply();
        if (playback != null) playback.refreshSystemSurface();
        stopSelf();
    }

    private void updateWindow() {
        try { if (wm != null && root != null) wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "桌面歌词", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 4301,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent lock = PendingIntent.getService(this, 4302,
                new Intent(this, DesktopLyricService.class).setAction(ACTION_TOGGLE_LOCK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close = PendingIntent.getService(this, 4303,
                new Intent(this, DesktopLyricService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Lunaxy Music · 桌面歌词")
                .setContentText(locked ? "已锁定 · 触摸穿透" : "未锁定 · 可移动")
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setContentIntent(open)
                // The icon describes CURRENT state; tapping still toggles it.
                .addAction(new Notification.Action.Builder(locked ? R.drawable.ic_lyric_lock_system : R.drawable.ic_lyric_unlock_system,
                        locked ? "已锁定" : "未锁定", lock).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_lyric_close_system, "关闭", close).build())
                .build();
    }

    private void updateForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private void updateFromPlayback() {
        if (root == null) return;
        applyLyricPalette();
        if (playback == null) { showIdle("等待播放", ""); return; }
        PlaybackSnapshot s = playback.snapshot();
        Song song = s == null ? null : s.song;
        if (song == null) { showIdle("等待播放", ""); return; }
        if (!song.key().equals(songKey)) {
            songKey = song.key();
            renderedLine = "";
            loadLyrics(song);
        }
        if (lines.isEmpty()) {
            setCurrentAnimated(song.title);
            nextLine.setText(song.artist);
            return;
        }
        int idx = LyricParser.activeIndex(lines, s.positionMs);
        if (idx < 0) idx = 0;
        if (idx >= lines.size()) idx = lines.size() - 1;
        LyricLine line = lines.get(idx);
        String current = line.text == null || line.text.trim().isEmpty() ? song.title : line.text.trim();
        setCurrentAnimated(current);
        nextLine.setText(idx + 1 < lines.size() ? safeLine(lines.get(idx + 1).text) : "");
    }

    private void setCurrentAnimated(String text) {
        String value = text == null ? "" : text;
        if (value.equals(renderedLine)) return;
        renderedLine = value;
        currentLine.animate().cancel();
        currentLine.setAlpha(1f);
        currentLine.setTranslationY(0f);
        currentLine.setText(value);
        nextLine.animate().cancel();
        nextLine.setAlpha(1f);
        nextLine.setTranslationY(0f);
    }

    private static String safeLine(String s) { return s == null ? "" : s.trim(); }
    private void showIdle(String title, String sub) { setCurrentAnimated(title); nextLine.setText(sub == null ? "" : sub); }

    private void loadLyrics(Song song) {
        int token = ++lyricToken;
        lines = new ArrayList<>();
        LyricCacheStore cache = new LyricCacheStore(this);
        String cached = cache.get(song);
        if (cached != null && !cached.trim().isEmpty()) lines = LyricParser.parse(cached);
        lyricIo.submit(() -> {
            String lrc = cached == null ? "" : cached;
            String yrc = "";
            try {
                SourceVariant wy = song.variant("wy");
                if (wy != null && !wy.sourceId.isEmpty()) {
                    NeteaseApi.LyricData d = netease.lyricData(wy.sourceId);
                    if (!d.lrc.isEmpty()) lrc = d.lrc;
                    yrc = d.yrc;
                } else {
                    SourceVariant tx = song.variant("tx");
                    if (tx != null && !tx.sourceId.isEmpty()) lrc = qq.lyric(tx.sourceId);
                }
            } catch (Exception ignored) { }
            String finalLrc = lrc;
            String finalYrc = yrc;
            List<LyricLine> parsed = LyricParser.parseEnhanced(finalLrc, finalYrc);
            if (!finalLrc.trim().isEmpty()) cache.put(song, finalLrc);
            main.post(() -> {
                if (token != lyricToken || !song.key().equals(songKey)) return;
                if (!parsed.isEmpty()) lines = parsed;
            });
        });
    }

    @Override public void onDestroy() {
        main.removeCallbacks(ticker);
        main.removeCallbacks(hideControls);
        if (bound) try { unbindService(playbackConnection); } catch (Exception ignored) { }
        bound = false;
        playback = null;
        if (wm != null && root != null) try { wm.removeView(root); } catch (Exception ignored) { }
        lyricIo.shutdownNow();
        root = null;
        super.onDestroy();
    }
}
