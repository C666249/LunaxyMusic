package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

/**
 * Draws the previous appearance everywhere except inside an expanding source-origin circle.
 * The already-updated interface sits beneath it, so the new theme appears to grow from the control
 * the user actually touched.  This is decorative only; business state changes immediately.
 */
public final class ThemeRevealOverlay extends View {
    private final Bitmap oldFrame;
    private final Path hole = new Path();
    private float centerX;
    private float centerY;
    private float radius;

    public ThemeRevealOverlay(Context context, Bitmap oldFrame, float centerX, float centerY) {
        super(context);
        this.oldFrame = oldFrame;
        this.centerX = centerX;
        this.centerY = centerY;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    public void setRadius(float value) {
        radius = Math.max(0f, value);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (oldFrame == null || oldFrame.isRecycled()) return;
        int save = canvas.save();
        hole.reset();
        hole.addCircle(centerX, centerY, radius, Path.Direction.CW);
        canvas.clipOutPath(hole);
        canvas.drawBitmap(oldFrame, 0f, 0f, null);
        canvas.restoreToCount(save);
    }
}
