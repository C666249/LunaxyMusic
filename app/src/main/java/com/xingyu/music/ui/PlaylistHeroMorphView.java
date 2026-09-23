package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Shared-object overlay for playlist-card <-> playlist-detail navigation.
 *
 * The overlay owns one continuous container, cover, title and metadata identity while the source
 * and destination pages exchange visual ownership underneath it.  Geometry is expressed in the
 * app-root coordinate space, so the transition is independent of ScrollView internals and can use
 * the exact reverse path when navigating back.
 */
public final class PlaylistHeroMorphView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint art = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF sourceContainer = new RectF();
    private final RectF targetContainer = new RectF();
    private final RectF sourceArtwork = new RectF();
    private final RectF targetArtwork = new RectF();
    private final RectF sourceTitle = new RectF();
    private final RectF targetTitle = new RectF();
    private final RectF sourceMeta = new RectF();
    private final RectF targetMeta = new RectF();
    private final RectF current = new RectF();
    private final RectF currentArtwork = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final Path artClip = new Path();
    private Bitmap bitmap;
    private BitmapShader shader;
    private String title = "";
    private String subtitle = "";
    private int accent = Ui.CYAN;
    private float progress;
    private float sourceTitleSizePx;
    private float targetTitleSizePx;
    private float sourceMetaSizePx;
    private float targetMetaSizePx;

    public PlaylistHeroMorphView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, Ui.dp(context, 1)));
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        meta.setTypeface(Typeface.DEFAULT);
    }

    public void configure(RectF fromContainer, RectF toContainer,
                          RectF fromArtwork, RectF toArtwork,
                          RectF fromTitle, RectF toTitle,
                          RectF fromMeta, RectF toMeta,
                          float fromTitleSizePx, float toTitleSizePx,
                          float fromMetaSizePx, float toMetaSizePx,
                          String title, String subtitle, int accent, Bitmap artwork) {
        sourceContainer.set(fromContainer);
        targetContainer.set(toContainer);
        sourceArtwork.set(fromArtwork);
        targetArtwork.set(toArtwork);
        sourceTitle.set(fromTitle);
        targetTitle.set(toTitle);
        sourceMeta.set(fromMeta);
        targetMeta.set(toMeta);
        sourceTitleSizePx = Math.max(1f, fromTitleSizePx);
        targetTitleSizePx = Math.max(1f, toTitleSizePx);
        sourceMetaSizePx = Math.max(1f, fromMetaSizePx);
        targetMetaSizePx = Math.max(1f, toMetaSizePx);
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.accent = accent;
        bitmap = artwork;
        shader = artwork == null || artwork.isRecycled() ? null
                : new BitmapShader(artwork, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        invalidate();
    }

    public void setProgress(float value) {
        progress = clamp(value);
        invalidate();
    }

    public float getProgress() { return progress; }

    /** Keep the shared container material tied to the resolved playlist artwork color. */
    public void setAccent(int color) {
        if (accent == color) return;
        accent = color;
        invalidate();
    }

    public void releaseArtwork() {
        bitmap = null;
        shader = null;
        art.setShader(null);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float p = smooth(progress);
        lerpRect(sourceContainer, targetContainer, p, current);
        lerpRect(sourceArtwork, targetArtwork, p, currentArtwork);
        // A shallow flight arc makes the source card visibly detach from the list before settling
        // into its larger detail geometry.  The offset is exactly zero at both endpoints, so push
        // and pop remain true geometric inverses.
        final float lift = -(float) Math.sin(Math.PI * p) * Ui.dp(getContext(), 11);
        current.offset(0f, lift);
        currentArtwork.offset(0f, lift);

        final boolean light = AppearanceSystem.isLight();
        int baseStart = light ? Color.rgb(250, 251, 254) : Color.rgb(12, 15, 22);
        int baseEnd = light ? mix(Color.WHITE, accent, .035f) : mix(Color.rgb(11, 15, 22), accent, .10f);
        fill.setStyle(Paint.Style.FILL);
        float airborne = (float) Math.sin(Math.PI * p);
        if (airborne > .001f) {
            RectF glow = new RectF(current);
            float spread = Ui.dp(getContext(), 7) * airborne;
            glow.inset(-spread, -spread);
            aura.setColor(withAlpha(accent, Math.round((light ? 16f : 24f) * airborne)));
            canvas.drawRoundRect(glow, Ui.dp(getContext(), 25), Ui.dp(getContext(), 25), aura);
        }
        int surfaceAlpha = light ? 246 : Math.round(168f + 64f * p);
        fill.setColor(withAlpha(mix(baseStart, baseEnd, p), surfaceAlpha));
        float radius = lerp(Ui.dp(getContext(), 17), Ui.dp(getContext(), 24), p);
        canvas.drawRoundRect(current, radius, radius, fill);

        edge.setColor(withAlpha(accent, light ? 44 : 54));
        RectF stroke = new RectF(current);
        stroke.inset(edge.getStrokeWidth() * .5f, edge.getStrokeWidth() * .5f);
        canvas.drawRoundRect(stroke, Math.max(0f, radius - edge.getStrokeWidth() * .5f),
                Math.max(0f, radius - edge.getStrokeWidth() * .5f), edge);

        drawArtwork(canvas, p);
        drawText(canvas, p, lift);
    }

    private void drawArtwork(Canvas canvas, float p) {
        float radius = lerp(Ui.dp(getContext(), 13), Ui.dp(getContext(), 19), p);
        if (shader == null || bitmap == null || bitmap.isRecycled()) {
            fill.setColor(withAlpha(accent, AppearanceSystem.isLight() ? 34 : 42));
            canvas.drawRoundRect(currentArtwork, radius, radius, fill);
            return;
        }
        float scale = Math.max(currentArtwork.width() / Math.max(1, bitmap.getWidth()),
                currentArtwork.height() / Math.max(1, bitmap.getHeight()));
        float dw = bitmap.getWidth() * scale;
        float dh = bitmap.getHeight() * scale;
        shaderMatrix.reset();
        shaderMatrix.setScale(scale, scale);
        shaderMatrix.postTranslate(currentArtwork.left + (currentArtwork.width() - dw) * .5f,
                currentArtwork.top + (currentArtwork.height() - dh) * .5f);
        shader.setLocalMatrix(shaderMatrix);
        art.setShader(shader);
        artClip.reset();
        artClip.addRoundRect(currentArtwork, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(artClip);
        canvas.drawRect(currentArtwork, art);
        canvas.restoreToCount(save);
        art.setShader(null);
    }

    private void drawText(Canvas canvas, float p, float lift) {
        int primary = Ui.TEXT;
        int secondary = Ui.TEXT_2;
        float titleX = lerp(sourceTitle.left, targetTitle.left, p);
        float titleTop = lerp(sourceTitle.top, targetTitle.top, p) + lift;
        float titleH = lerp(sourceTitle.height(), targetTitle.height(), p);
        float titleSize = lerp(sourceTitleSizePx, targetTitleSizePx, p);
        text.setTextSize(titleSize);
        text.setColor(primary);
        Paint.FontMetrics tfm = text.getFontMetrics();
        float titleBaseline = titleTop + Math.max(titleH * .68f, -tfm.ascent);
        drawEllipsized(canvas, title, titleX, titleBaseline, text,
                Math.max(1f, current.right - titleX - Ui.dp(getContext(), 14)));

        float metaX = lerp(sourceMeta.left, targetMeta.left, p);
        float metaTop = lerp(sourceMeta.top, targetMeta.top, p) + lift;
        float metaH = lerp(sourceMeta.height(), targetMeta.height(), p);
        float metaSize = lerp(sourceMetaSizePx, targetMetaSizePx, p);
        meta.setTextSize(metaSize);
        meta.setColor(secondary);
        Paint.FontMetrics mfm = meta.getFontMetrics();
        float metaBaseline = metaTop + Math.max(metaH * .68f, -mfm.ascent);
        drawEllipsized(canvas, subtitle, metaX, metaBaseline, meta,
                Math.max(1f, current.right - metaX - Ui.dp(getContext(), 14)));
    }

    private static void drawEllipsized(Canvas canvas, String value, float x, float baseline, Paint paint, float width) {
        if (value == null || value.isEmpty() || width <= 1f) return;
        String out = value;
        if (paint.measureText(out) > width) {
            final String ellipsis = "…";
            float allowed = Math.max(1f, width - paint.measureText(ellipsis));
            int count = paint.breakText(out, true, allowed, null);
            out = out.substring(0, Math.max(0, count)) + ellipsis;
        }
        canvas.drawText(out, x, baseline, paint);
    }

    private static void lerpRect(RectF a, RectF b, float p, RectF out) {
        out.set(lerp(a.left, b.left, p), lerp(a.top, b.top, p),
                lerp(a.right, b.right, p), lerp(a.bottom, b.bottom, p));
    }

    private static float smooth(float v) {
        float t = clamp(v);
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float p) { return a + (b - a) * p; }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }
    private static int mix(int a, int b, float t) {
        float p = clamp(t);
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * p),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * p),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * p));
    }
}
