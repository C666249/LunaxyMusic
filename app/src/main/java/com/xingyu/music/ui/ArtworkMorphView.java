package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Shared-artwork overlay used by song-card / Mini Player -> Full Player transitions.
 *
 * V92.2 keeps all draw helpers allocated once. V92 rebuilt BitmapShader/Matrix/RectF on every
 * frame; that allocation spike became visible exactly when the overlay grew to the large record.
 */
public final class ArtworkMorphView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final RectF bounds = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private Bitmap bitmap;
    private BitmapShader shader;
    private float progress;
    private float startRadiusPx;

    public ArtworkMorphView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setBitmap(Bitmap value) {
        bitmap = value;
        shader = value == null || value.isRecycled()
                ? null : new BitmapShader(value, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        invalidate();
    }

    public void setStartRadiusDp(float dp) {
        startRadiusPx = Ui.dp(getContext(), dp);
        invalidate();
    }

    public void setMorphProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || shader == null || bitmap.isRecycled() || getWidth() <= 0 || getHeight() <= 0) return;
        float w = getWidth(), h = getHeight();
        float radius = startRadiusPx + (Math.min(w, h) * .5f - startRadiusPx) * progress;
        bounds.set(0f, 0f, w, h);
        clip.reset();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);

        float scale = Math.max(w / bitmap.getWidth(), h / bitmap.getHeight());
        float dw = bitmap.getWidth() * scale, dh = bitmap.getHeight() * scale;
        shaderMatrix.reset();
        shaderMatrix.setScale(scale, scale);
        shaderMatrix.postTranslate((w - dw) * .5f, (h - dh) * .5f);
        shader.setLocalMatrix(shaderMatrix);
        paint.setShader(shader);
        canvas.drawRect(bounds, paint);
        paint.setShader(null);
        canvas.restoreToCount(save);
    }
}
