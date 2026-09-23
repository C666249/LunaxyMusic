package com.xingyu.music.ui;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small dependency-free line icon renderer so the UI does not rely on emoji glyphs. */
public final class IconView extends View {
    public enum Type { HOME, SEARCH, PLAYLIST, HEART, PLAY, PAUSE, NEXT, PREV, PLUS, BACK, MUSIC, MORE, CLOSE, DELETE, DRAG, SHUFFLE, REPEAT, REPEAT_ONE, LAYERS, PALETTE, WEATHER, DOWNLOAD, LOCK, UNLOCK, LOCATE, BELL, SCAN, MIC, TUNE, GRID }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Type type;
    private int color;
    private float strokeDp = 1.72f;
    // V92 motion state. 0=Play outline, 1=Pause bars. HEART uses favoriteFill 0..1.
    private float playbackMorph;
    private float favoriteFill;
    private ValueAnimator morphAnimator;
    private ValueAnimator favoriteAnimator;

    public IconView(Context context, Type type, int color) {
        super(context);
        this.type = type;
        this.color = color;
        this.playbackMorph = type == Type.PAUSE ? 1f : 0f;
        setClickable(false);
    }

    public void setType(Type type) {
        this.type = type;
        if (type == Type.PLAY) playbackMorph = 0f;
        else if (type == Type.PAUSE) playbackMorph = 1f;
        invalidate();
    }

    /** Continuous Play ↔ Pause morph instead of swapping two unrelated glyphs. */
    public void setPlaybackState(boolean playing, boolean animate) {
        float target = playing ? 1f : 0f;
        if (morphAnimator != null) morphAnimator.cancel();
        type = playing ? Type.PAUSE : Type.PLAY;
        if (!animate || Math.abs(playbackMorph - target) < .01f) {
            playbackMorph = target; invalidate(); return;
        }
        final float start = playbackMorph;
        morphAnimator = ValueAnimator.ofFloat(0f, 1f);
        morphAnimator.setDuration(SpringMotion.isReducedMotion() ? SpringMotion.selectionDuration() : 210L);
        morphAnimator.setInterpolator(SpringMotion.PRESS);
        morphAnimator.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            playbackMorph = start + (target - start) * t;
            invalidate();
        });
        morphAnimator.start();
    }

    /** Heart outline ↔ fill morph used by player and song rows. */
    public void setFavoriteState(boolean favorite, boolean animate) {
        float target = favorite ? 1f : 0f;
        if (favoriteAnimator != null) favoriteAnimator.cancel();
        if (!animate || Math.abs(favoriteFill - target) < .01f) {
            favoriteFill = target; invalidate(); return;
        }
        final float start = favoriteFill;
        favoriteAnimator = ValueAnimator.ofFloat(0f, 1f);
        favoriteAnimator.setDuration(SpringMotion.isReducedMotion() ? SpringMotion.selectionDuration() : (favorite ? 250L : 190L));
        favoriteAnimator.setInterpolator(favorite ? SpringMotion.SOFT : SpringMotion.SNAPPY);
        favoriteAnimator.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            favoriteFill = start + (target - start) * t;
            invalidate();
        });
        favoriteAnimator.start();
    }
    public void setIconColor(int color) { this.color = color; invalidate(); }
    public void setStrokeDp(float strokeDp) { this.strokeDp = strokeDp; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float s = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;
        float d = getResources().getDisplayMetrics().density;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeDp * d);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();

        switch (type) {
            case HOME: {
                float r = s * .28f;
                path.moveTo(cx - r, cy - s * .02f);
                path.lineTo(cx, cy - r);
                path.lineTo(cx + r, cy - s * .02f);
                path.moveTo(cx - r * .78f, cy - s * .01f);
                path.lineTo(cx - r * .78f, cy + r * .72f);
                path.lineTo(cx + r * .78f, cy + r * .72f);
                path.lineTo(cx + r * .78f, cy - s * .01f);
                c.drawPath(path, paint);
                break;
            }
            case SEARCH: {
                float r = s * .20f;
                c.drawCircle(cx - s * .05f, cy - s * .05f, r, paint);
                c.drawLine(cx + r * .48f, cy + r * .48f, cx + s * .28f, cy + s * .28f, paint);
                break;
            }
            case PLAYLIST: {
                float left = cx - s * .27f, right = cx + s * .09f;
                for (int i = -1; i <= 1; i++) {
                    float y = cy + i * s * .16f;
                    c.drawLine(left, y, right, y, paint);
                }
                c.drawLine(cx + s * .17f, cy - s * .23f, cx + s * .17f, cy + s * .17f, paint);
                c.drawCircle(cx + s * .08f, cy + s * .23f, s * .09f, paint);
                break;
            }
            case HEART: {
                path.moveTo(cx, cy + s * .27f);
                path.cubicTo(cx - s * .34f, cy + s * .05f, cx - s * .32f, cy - s * .23f, cx - s * .13f, cy - s * .23f);
                path.cubicTo(cx - s * .02f, cy - s * .23f, cx, cy - s * .13f, cx, cy - s * .10f);
                path.cubicTo(cx, cy - s * .13f, cx + s * .02f, cy - s * .23f, cx + s * .13f, cy - s * .23f);
                path.cubicTo(cx + s * .32f, cy - s * .23f, cx + s * .34f, cy + s * .05f, cx, cy + s * .27f);
                path.close();
                if (favoriteFill > .001f) {
                    paint.setStyle(Paint.Style.FILL);
                    int oldAlpha = paint.getAlpha();
                    paint.setAlpha(Math.max(1, Math.min(255, Math.round(255f * favoriteFill))));
                    float pop = 1f + (float)Math.sin(favoriteFill * Math.PI) * .045f;
                    c.save(); c.scale(pop, pop, cx, cy); c.drawPath(path, paint); c.restore();
                    paint.setAlpha(oldAlpha);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setAlpha(Math.max(90, Math.round(255f * (1f - favoriteFill * .45f))));
                c.drawPath(path, paint);
                paint.setAlpha(255);
                break;
            }
            case PLAY:
            case PAUSE: {
                drawPlaybackMorph(c, cx, cy, s, playbackMorph);
                break;
            }
            case NEXT:
            case PREV: {
                boolean rev = type == Type.PREV;
                float dir = rev ? -1f : 1f;
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(cx - dir * s * .17f, cy - s * .19f);
                path.lineTo(cx + dir * s * .09f, cy);
                path.lineTo(cx - dir * s * .17f, cy + s * .19f);
                path.close();
                c.drawPath(path, paint);
                float x = cx + dir * s * .17f;
                c.drawRoundRect(new RectF(x - s * .025f, cy - s * .19f, x + s * .025f, cy + s * .19f), s * .02f, s * .02f, paint);
                break;
            }
            case PLUS: {
                c.drawLine(cx - s * .22f, cy, cx + s * .22f, cy, paint);
                c.drawLine(cx, cy - s * .22f, cx, cy + s * .22f, paint);
                break;
            }
            case BACK: {
                c.drawLine(cx + s * .18f, cy - s * .23f, cx - s * .10f, cy, paint);
                c.drawLine(cx - s * .10f, cy, cx + s * .18f, cy + s * .23f, paint);
                break;
            }
            case MUSIC: {
                c.drawLine(cx + s * .12f, cy - s * .28f, cx + s * .12f, cy + s * .10f, paint);
                c.drawLine(cx + s * .12f, cy - s * .28f, cx - s * .12f, cy - s * .22f, paint);
                c.drawCircle(cx - s * .20f, cy + s * .18f, s * .10f, paint);
                c.drawCircle(cx + s * .04f, cy + s * .12f, s * .10f, paint);
                break;
            }
            case MORE: {
                paint.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * s * .18f, cy, s * .035f, paint);
                break;
            }
            case CLOSE: {
                c.drawLine(cx - s * .20f, cy - s * .20f, cx + s * .20f, cy + s * .20f, paint);
                c.drawLine(cx + s * .20f, cy - s * .20f, cx - s * .20f, cy + s * .20f, paint);
                break;
            }
            case DELETE: {
                float top = cy - s * .13f;
                float bottom = cy + s * .24f;
                c.drawLine(cx - s * .15f, top, cx - s * .11f, bottom, paint);
                c.drawLine(cx + s * .15f, top, cx + s * .11f, bottom, paint);
                c.drawLine(cx - s * .11f, bottom, cx + s * .11f, bottom, paint);
                c.drawLine(cx - s * .22f, cy - s * .19f, cx + s * .22f, cy - s * .19f, paint);
                c.drawLine(cx - s * .07f, cy - s * .27f, cx + s * .07f, cy - s * .27f, paint);
                break;
            }
            case DRAG: {
                for (int i = -1; i <= 1; i++) {
                    float y = cy + i * s * .13f;
                    c.drawLine(cx - s * .20f, y, cx + s * .20f, y, paint);
                }
                break;
            }
            case SHUFFLE: {
                float l = cx - s * .25f, r = cx + s * .23f;
                path.moveTo(l, cy - s * .15f);
                path.cubicTo(cx - s * .05f, cy - s * .15f, cx + s * .02f, cy + s * .15f, r, cy + s * .15f);
                path.moveTo(l, cy + s * .15f);
                path.cubicTo(cx - s * .05f, cy + s * .15f, cx + s * .02f, cy - s * .15f, r, cy - s * .15f);
                c.drawPath(path, paint);
                c.drawLine(r - s * .07f, cy - s * .22f, r, cy - s * .15f, paint);
                c.drawLine(r - s * .07f, cy - s * .08f, r, cy - s * .15f, paint);
                c.drawLine(r - s * .07f, cy + s * .08f, r, cy + s * .15f, paint);
                c.drawLine(r - s * .07f, cy + s * .22f, r, cy + s * .15f, paint);
                break;
            }
            case WEATHER: {
                // Sun behind a soft cloud: compact, dependency-free weather glyph.
                c.drawCircle(cx - s * .10f, cy - s * .08f, s * .12f, paint);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2d * i / 8d;
                    float x1 = cx - s * .10f + (float)Math.cos(a) * s * .18f;
                    float y1 = cy - s * .08f + (float)Math.sin(a) * s * .18f;
                    float x2 = cx - s * .10f + (float)Math.cos(a) * s * .23f;
                    float y2 = cy - s * .08f + (float)Math.sin(a) * s * .23f;
                    c.drawLine(x1, y1, x2, y2, paint);
                }
                RectF cloud = new RectF(cx - s * .25f, cy + s * .01f, cx + s * .24f, cy + s * .22f);
                c.drawRoundRect(cloud, s * .10f, s * .10f, paint);
                c.drawArc(new RectF(cx - s * .14f, cy - s * .09f, cx + s * .10f, cy + s * .15f), 190f, 160f, false, paint);
                break;
            }
            case LAYERS: {
                // Open/native-style stacked cards glyph.  It belongs to Lunaxy's own line-icon
                // family instead of importing Apple-owned SF Symbols onto Android.
                RectF back = new RectF(cx - s * .22f, cy - s * .12f, cx + s * .22f, cy + s * .18f);
                c.drawRoundRect(back, s * .055f, s * .055f, paint);
                RectF front = new RectF(cx - s * .18f, cy - s * .22f, cx + s * .18f, cy + s * .08f);
                c.drawRoundRect(front, s * .055f, s * .055f, paint);
                break;
            }
            case PALETTE: {
                // Compact painter-palette outline in Lunaxy's own icon geometry.
                RectF oval = new RectF(cx - s * .27f, cy - s * .23f, cx + s * .27f, cy + s * .23f);
                c.drawOval(oval, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - s * .12f, cy - s * .07f, s * .033f, paint);
                c.drawCircle(cx, cy - s * .13f, s * .033f, paint);
                c.drawCircle(cx + s * .13f, cy - s * .03f, s * .033f, paint);
                c.drawCircle(cx - s * .03f, cy + s * .10f, s * .033f, paint);
                // Small thumb indentation gives the glyph a recognisable painter-palette silhouette.
                paint.setStyle(Paint.Style.STROKE);
                c.drawArc(new RectF(cx + s * .03f, cy + s * .02f, cx + s * .25f, cy + s * .22f), 128f, 120f, false, paint);
                break;
            }
            case TUNE: {
                // Material/Lucide-inspired adjustments glyph, redrawn in Lunaxy's line language.
                float left = cx - s * .25f, right = cx + s * .25f;
                float y1 = cy - s * .18f, y2 = cy, y3 = cy + s * .18f;
                c.drawLine(left, y1, right, y1, paint);
                c.drawLine(left, y2, right, y2, paint);
                c.drawLine(left, y3, right, y3, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - s * .09f, y1, s * .055f, paint);
                c.drawCircle(cx + s * .11f, y2, s * .055f, paint);
                c.drawCircle(cx - s * .02f, y3, s * .055f, paint);
                break;
            }
            case GRID: {
                // Compact four-node launcher. Secondary tools grow from this stable source.
                paint.setStyle(Paint.Style.FILL);
                float o = s * .13f, r = s * .055f;
                c.drawCircle(cx - o, cy - o, r, paint);
                c.drawCircle(cx + o, cy - o, r, paint);
                c.drawCircle(cx - o, cy + o, r, paint);
                c.drawCircle(cx + o, cy + o, r, paint);
                break;
            }
            case MIC: {
                RectF capsule = new RectF(cx - s * .115f, cy - s * .28f, cx + s * .115f, cy + s * .08f);
                c.drawRoundRect(capsule, s * .12f, s * .12f, paint);
                c.drawArc(new RectF(cx - s * .22f, cy - s * .08f, cx + s * .22f, cy + s * .25f), 0f, 180f, false, paint);
                c.drawLine(cx, cy + s * .25f, cx, cy + s * .34f, paint);
                c.drawLine(cx - s * .12f, cy + s * .34f, cx + s * .12f, cy + s * .34f, paint);
                break;
            }
            case DOWNLOAD: {
                c.drawLine(cx, cy - s * .27f, cx, cy + s * .10f, paint);
                c.drawLine(cx - s * .13f, cy - s * .01f, cx, cy + s * .13f, paint);
                c.drawLine(cx + s * .13f, cy - s * .01f, cx, cy + s * .13f, paint);
                c.drawLine(cx - s * .23f, cy + s * .24f, cx + s * .23f, cy + s * .24f, paint);
                break;
            }
            case LOCK: {
                RectF body = new RectF(cx - s * .20f, cy - s * .02f, cx + s * .20f, cy + s * .25f);
                c.drawRoundRect(body, s * .05f, s * .05f, paint);
                RectF shackle = new RectF(cx - s * .13f, cy - s * .24f, cx + s * .13f, cy + s * .08f);
                c.drawArc(shackle, 190f, 160f, false, paint);
                break;
            }
            case UNLOCK: {
                RectF body = new RectF(cx - s * .20f, cy - s * .02f, cx + s * .20f, cy + s * .25f);
                c.drawRoundRect(body, s * .05f, s * .05f, paint);
                RectF shackle = new RectF(cx - s * .13f, cy - s * .25f, cx + s * .13f, cy + s * .08f);
                c.drawArc(shackle, 188f, 96f, false, paint);
                c.drawLine(cx + s * .11f, cy - s * .20f, cx + s * .23f, cy - s * .25f, paint);
                break;
            }
            case BELL: {
                // Slim notification-bell glyph used by the offline ringtone action.
                float top = cy - s * .22f;
                float left = cx - s * .19f, right = cx + s * .19f;
                RectF dome = new RectF(left, top, right, cy + s * .16f);
                c.drawArc(dome, 205f, 130f, false, paint);
                c.drawLine(left + s * .025f, cy - s * .02f, left + s * .025f, cy + s * .12f, paint);
                c.drawLine(right - s * .025f, cy - s * .02f, right - s * .025f, cy + s * .12f, paint);
                c.drawLine(left - s * .02f, cy + s * .13f, right + s * .02f, cy + s * .13f, paint);
                c.drawArc(new RectF(cx - s * .075f, cy + s * .10f, cx + s * .075f, cy + s * .26f), 12f, 156f, false, paint);
                break;
            }
            case SCAN: {
                // Audio scan: four restrained finder corners around a small music note.
                float o = s * .25f, arm = s * .10f;
                c.drawLine(cx - o, cy - o + arm, cx - o, cy - o, paint);
                c.drawLine(cx - o, cy - o, cx - o + arm, cy - o, paint);
                c.drawLine(cx + o - arm, cy - o, cx + o, cy - o, paint);
                c.drawLine(cx + o, cy - o, cx + o, cy - o + arm, paint);
                c.drawLine(cx - o, cy + o - arm, cx - o, cy + o, paint);
                c.drawLine(cx - o, cy + o, cx - o + arm, cy + o, paint);
                c.drawLine(cx + o - arm, cy + o, cx + o, cy + o, paint);
                c.drawLine(cx + o, cy + o, cx + o, cy + o - arm, paint);
                c.drawLine(cx + s * .06f, cy - s * .16f, cx + s * .06f, cy + s * .10f, paint);
                c.drawLine(cx + s * .06f, cy - s * .16f, cx - s * .07f, cy - s * .12f, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - s * .11f, cy + s * .12f, s * .065f, paint);
                c.drawCircle(cx + s * .01f, cy + s * .08f, s * .065f, paint);
                break;
            }
            case LOCATE: {
                // Compact playlist locator: target ring + center dot, inspired by navigation
                // affordances while keeping Lunaxy's thin line-icon language.
                float r = s * .23f;
                c.drawCircle(cx, cy, r, paint);
                c.drawLine(cx, cy - s * .34f, cx, cy - r, paint);
                c.drawLine(cx, cy + r, cx, cy + s * .34f, paint);
                c.drawLine(cx - s * .34f, cy, cx - r, cy, paint);
                c.drawLine(cx + r, cy, cx + s * .34f, cy, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, s * .055f, paint);
                break;
            }
            case REPEAT:
            case REPEAT_ONE: {
                float l = cx - s * .22f, r = cx + s * .22f, t = cy - s * .14f, b = cy + s * .14f;
                path.moveTo(l + s * .05f, t);
                path.lineTo(r - s * .04f, t);
                path.cubicTo(r + s * .08f, t, r + s * .08f, b, r - s * .02f, b);
                path.moveTo(r - s * .08f, b - s * .07f);
                path.lineTo(r, b);
                path.lineTo(r - s * .08f, b + s * .07f);
                path.moveTo(r - s * .05f, b);
                path.lineTo(l + s * .04f, b);
                path.cubicTo(l - s * .08f, b, l - s * .08f, t, l + s * .02f, t);
                path.moveTo(l + s * .08f, t - s * .07f);
                path.lineTo(l, t);
                path.lineTo(l + s * .08f, t + s * .07f);
                c.drawPath(path, paint);
                if (type == Type.REPEAT_ONE) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(s * .25f);
                    paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    c.drawText("1", cx, cy + s * .09f, paint);
                }
                break;
            }
        }
    }
    private void drawPlaybackMorph(Canvas c, float cx, float cy, float s, float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        paint.setStyle(Paint.Style.FILL);

        // Left polygon: a play triangle continuously folds into the left pause bar.
        float[][] a = {
                {-0.11f,-0.19f},{0.20f,0f},{-0.11f,0.19f},{-0.11f,-0.19f}
        };
        float[][] b = {
                {-0.16f,-0.19f},{-0.04f,-0.19f},{-0.04f,0.19f},{-0.16f,0.19f}
        };
        path.reset();
        path.moveTo(cx + lerp(a[0][0],b[0][0],p)*s, cy + lerp(a[0][1],b[0][1],p)*s);
        for (int i=1;i<4;i++) path.lineTo(cx + lerp(a[i][0],b[i][0],p)*s, cy + lerp(a[i][1],b[i][1],p)*s);
        path.close(); c.drawPath(path, paint);

        // Right pause bar grows from the former triangle tip, so no glyph ever disappears.
        float x0 = lerp(.12f,.04f,p), x1 = lerp(.12f,.16f,p);
        float y0 = lerp(0f,-.19f,p), y1 = lerp(0f,.19f,p);
        float radius = s * .025f * p;
        c.drawRoundRect(new RectF(cx + x0*s, cy + y0*s, cx + x1*s, cy + y1*s), radius, radius, paint);
    }

    private static float lerp(float a, float b, float t) { return a + (b-a)*t; }

}
