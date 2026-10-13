package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Rim waves for the Agent's origin-centered circular page reveal. */
public final class AgentRippleWaveView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float originX, originY, maximumRadius, density;
    private float progress;

    public AgentRippleWaveView(Context context, float x, float y, float radius) {
        super(context);
        originX = x;
        originY = y;
        maximumRadius = radius;
        density = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress <= 0f || progress >= 1f) return;
        float radius = maximumRadius * progress;
        float fade = 1f - progress;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.argb(Math.round(37f * fade), 132, 217, 255));
        paint.setStrokeWidth(15f * density);
        canvas.drawCircle(originX, originY, radius, paint);
        paint.setColor(Color.argb(Math.round(177f * fade), 196, 237, 255));
        paint.setStrokeWidth(1.8f * density);
        canvas.drawCircle(originX, originY, radius, paint);
        paint.setColor(Color.argb(Math.round(108f * fade), 169, 156, 254));
        paint.setStrokeWidth(2.2f * density);
        canvas.drawCircle(originX, originY, Math.max(0f, radius - 13f * density), paint);
        if (progress > .14f) {
            paint.setColor(Color.argb(Math.round(62f * fade), 163, 204, 252));
            paint.setStrokeWidth(1.1f * density);
            canvas.drawCircle(originX, originY, Math.max(0f, radius - 30f * density), paint);
        }
    }
}
