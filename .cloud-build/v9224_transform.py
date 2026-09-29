from pathlib import Path

ROOT = Path('.')

def replace_once(path, old, new):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected 1 occurrence, got {count}: {old[:80]!r}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

replace_once('app/build.gradle.kts', 'versionCode = 1008', 'versionCode = 1009')
replace_once('app/build.gradle.kts', 'versionName = "92.9.23-cloud1"', 'versionName = "92.9.24-cloud2"')
replace_once('app/build.gradle.kts', 'LunaxyMusic-Cloud-92.9.23-debug.apk', 'LunaxyMusic-Cloud-92.9.24-debug.apk')
replace_once('app/build.gradle.kts', 'LunaxyMusic-Cloud-92.9.23-release.apk', 'LunaxyMusic-Cloud-92.9.24-release.apk')

main = Path('app/src/main/java/com/xingyu/music/MainActivity.java')
text = main.read_text(encoding='utf-8')

def rep(old, new, expected=1):
    global text
    count = text.count(old)
    if count != expected:
        raise SystemExit(f'MainActivity: expected {expected}, got {count}: {old[:96]!r}')
    text = text.replace(old, new, expected)

rep('import com.xingyu.music.ui.ThemeRevealOverlay;\n',
    'import com.xingyu.music.ui.ThemeRevealOverlay;\nimport com.xingyu.music.ui.TransportRippleView;\n')
rep('private final List<LiquidGlassDrawable> playerDetailLiquidGlass = new ArrayList<>();\n',
    'private final List<LiquidGlassDrawable> playerDetailLiquidGlass = new ArrayList<>();\n    private TransportRippleView nowPlayRipple;\n')
rep('? Math.max(.18f, starfieldBackgroundGlow * .86f)\n                    : Math.max(.24f, starfieldBackgroundGlow);',
    '? Math.max(.055f, starfieldBackgroundGlow * .28f)\n                    : Math.max(.16f, starfieldBackgroundGlow * .72f);')

control_slot = '''    private FrameLayout controlSlot(View child, int childSizeDp) {
        FrameLayout slot = new FrameLayout(this);
        slot.addView(child, Ui.frame(Ui.dp(this, childSizeDp), Ui.dp(this, childSizeDp), Gravity.CENTER));
        return slot;
    }
'''
primary_slot = control_slot + '''
    private FrameLayout primaryControlSlot(View child, int childSizeDp) {
        FrameLayout slot = new FrameLayout(this);
        slot.setClipChildren(false);
        slot.setClipToPadding(false);
        nowPlayRipple = new TransportRippleView(this);
        slot.addView(nowPlayRipple, Ui.frame(Ui.dp(this, 92), Ui.dp(this, 92), Gravity.CENTER));
        slot.addView(child, Ui.frame(Ui.dp(this, childSizeDp), Ui.dp(this, childSizeDp), Gravity.CENTER));
        return slot;
    }
'''
rep(control_slot, primary_slot)
rep('''        // The ACTION_DOWN press already compresses the button. This short post-click bloom makes
        // Play/Pause feel like the dominant transport without adding a second bouncy gesture.
        control.animate().scaleX(1.035f).scaleY(1.035f)
''',
    '''        // The ACTION_DOWN press already compresses the button. Release adds one restrained
        // low-alpha optical ripple; it should read as a surface response, not decoration.
        if (nowPlayRipple != null) nowPlayRipple.pulse(nowAccent);
        control.animate().scaleX(1.022f).scaleY(1.022f)
''')

for old, new in [
    ('stylePlayerDetailLiquidButton(back, 21, Ui.PURPLE, false);', 'back.setBackground(Ui.playerIconRipple(Ui.PURPLE, 21, this));'),
    ('stylePlayerDetailLiquidButton(starStudio, 21, Ui.CYAN, false);', 'starStudio.setBackground(Ui.playerIconRipple(Ui.CYAN, 21, this));'),
    ('stylePlayerDetailLiquidButton(more, 21, Ui.PURPLE, false);', 'more.setBackground(Ui.playerIconRipple(Ui.PURPLE, 21, this));'),
    ('stylePlayerDetailLiquidButton(desktopLyricQuick, 21, Ui.CYAN, false);', 'desktopLyricQuick.setBackground(Ui.playerIconRipple(Ui.CYAN, 21, this));'),
    ('stylePlayerDetailLiquidButton(nowFavoriteButton, 21, Ui.PINK, false);', 'nowFavoriteButton.setBackground(Ui.playerIconRipple(Ui.PINK, 21, this));'),
    ('stylePlayerDetailLiquidButton(addToPlaylist, 21, Ui.PURPLE, false);', 'addToPlaylist.setBackground(Ui.playerIconRipple(Ui.PURPLE, 21, this));'),
    ('stylePlayerDetailLiquidButton(modeButton, 23, initialModeAccent, false);', 'modeButton.setBackground(Ui.playerIconRipple(initialModeAccent, 23, this));'),
    ('stylePlayerDetailLiquidButton(prev, 25, Ui.BLUE, false);', 'prev.setBackground(Ui.playerIconRipple(Ui.BLUE, 25, this));'),
    ('stylePlayerDetailLiquidButton(next, 25, Ui.BLUE, false);', 'next.setBackground(Ui.playerIconRipple(Ui.BLUE, 25, this));'),
    ('stylePlayerDetailLiquidButton(queueButton, 23, Ui.CYAN, false);', 'queueButton.setBackground(Ui.playerIconRipple(Ui.CYAN, 23, this));'),
]: rep(old, new)

rep('modeButton.setForeground(Ui.playerIconRipple(accent, 23, this));',
    'modeButton.setBackground(Ui.playerIconRipple(accent, 23, this));')
rep('if (nowModeButton != null) nowModeButton.setForeground(Ui.playerIconRipple(accent, 23, this));',
    'if (nowModeButton != null) nowModeButton.setBackground(Ui.playerIconRipple(accent, 23, this));', expected=2)
rep('LinearLayout controls = Ui.row(this);\n        controls.setGravity(Gravity.CENTER_VERTICAL);',
    'LinearLayout controls = Ui.row(this);\n        controls.setGravity(Gravity.CENTER_VERTICAL);\n        controls.setClipChildren(false);\n        controls.setClipToPadding(false);')
rep('controls.addView(controlSlot(play, 66), new LinearLayout.LayoutParams(0, Ui.dp(this, 76), 1.18f));',
    'controls.addView(primaryControlSlot(play, 66), new LinearLayout.LayoutParams(0, Ui.dp(this, 76), 1.18f));')
main.write_text(text, encoding='utf-8')

glass = Path('app/src/main/java/com/xingyu/music/ui/LiquidGlassDrawable.java')
g = glass.read_text(encoding='utf-8')
for old, new in [
    ('(primary ? 132f : 112f)', '(primary ? 104f : 88f)'),
    ('(primary ? 52f : 42f)', '(primary ? 30f : 24f)'),
    ('int top = Color.argb(primary ? 58 : 43, 255, 255, 255);', 'int top = Color.argb(primary ? 30 : 22, 255, 255, 255);'),
    ('int mid = Color.argb(primary ? 31 : 22, 244, 248, 255);', 'int mid = Color.argb(primary ? 11 : 8, 244, 248, 255);'),
    ('int bottom = Color.argb(primary ? 40 : 27,', 'int bottom = Color.argb(primary ? 16 : 11,'),
    ('new int[]{Color.argb(primary ? 36 : 25, ar, ag, ab), Color.argb(0, ar, ag, ab)},', 'new int[]{Color.argb(primary ? 13 : 9, ar, ag, ab), Color.argb(0, ar, ag, ab)},'),
    ('Color.argb(primary ? 104 : 88, 255, 255, 255),', 'Color.argb(primary ? 58 : 46, 255, 255, 255),'),
    ('Color.argb(primary ? 30 : 24, 255, 255, 255),', 'Color.argb(primary ? 16 : 12, 255, 255, 255),'),
    ('Color.argb(primary ? 112 : 86,255,255,255),', 'Color.argb(primary ? 58 : 44,255,255,255),'),
    ('Color.argb(primary ? 28 : 18,255,255,255),', 'Color.argb(primary ? 14 : 10,255,255,255),'),
    ('Color.argb(primary ? 45 : 32,cr,cg,cb)', 'Color.argb(primary ? 22 : 16,cr,cg,cb)'),
]:
    c = g.count(old)
    if c != 1:
        raise SystemExit(f'LiquidGlassDrawable: expected 1, got {c}: {old!r}')
    g = g.replace(old, new, 1)
glass.write_text(g, encoding='utf-8')

Path('app/src/main/java/com/xingyu/music/ui/TransportRippleView.java').write_text(r'''package com.xingyu.music.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Restrained one-shot ripple for the primary transport control. */
public final class TransportRippleView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final float density;
    private ValueAnimator animator;
    private float progress = 1f;
    private int accent = Color.rgb(145, 196, 255);

    public TransportRippleView(Context context) {
        super(context);
        density = Math.max(.1f, context.getResources().getDisplayMetrics().density);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.15f));
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAlpha(0f);
    }

    public void pulse(int accentColor) {
        accent = accentColor;
        if (animator != null) animator.cancel();
        progress = 0f;
        setAlpha(1f);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(340L);
        animator.setInterpolator(new DecelerateInterpolator(1.35f));
        animator.addUpdateListener(a -> { progress = (Float) a.getAnimatedValue(); invalidate(); });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) { progress = 1f; setAlpha(0f); animator = null; }
            @Override public void onAnimationCancel(android.animation.Animator animation) { setAlpha(0f); }
        });
        animator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress >= 1f || getWidth() <= 0 || getHeight() <= 0) return;
        float cx = getWidth() * .5f, cy = getHeight() * .5f;
        float min = Math.min(getWidth(), getHeight());
        float radius = min * (.355f + .100f * progress);
        float fade = 1f - progress;
        int alpha = Math.round(48f * fade * fade);
        int r = Math.round(Color.red(accent) * .48f + 255f * .52f);
        int g = Math.round(Color.green(accent) * .48f + 255f * .52f);
        int b = Math.round(Color.blue(accent) * .48f + 255f * .52f);
        ringPaint.setColor(Color.argb(alpha, r, g, b));
        ringPaint.setStrokeWidth(dp(1.05f + .25f * (1f - progress)));
        canvas.drawCircle(cx, cy, radius, ringPaint);
    }

    private float dp(float v) { return v * density; }
}
''', encoding='utf-8')

print('V92.9.24 cloud visual transform applied')
