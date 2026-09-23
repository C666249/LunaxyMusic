package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/**
 * High-clarity desktop lyric line with adaptive size and marquee for very long sentences.
 *
 * Single native text pass over a translucent contrast plate. No outline duplication,
 * shadow blur or forced texture layer; gradient colour preferences remain supported.
 */
public final class DesktopLyricLaneView extends TextView {
    private int start = Ui.LYRIC_ICE_START;
    private int end = Ui.LYRIC_ICE_END;
    private float preferredSp = 18.2f;
    private float minSp = 15.0f;
    private float outlinePx;
    private int outlineColor = Color.argb(205, 0, 0, 0);
    private LinearGradient gradient;
    private int gradientWidth = -1;
    private int gradientStart;
    private int gradientEnd;

    public DesktopLyricLaneView(Context context) {
        super(context);
        float d = Math.max(1f, getResources().getDisplayMetrics().density);
        outlinePx = Math.max(1.15f, .78f * d);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setIncludeFontPadding(false);
        setHorizontallyScrolling(true);
        setEllipsize(null);
        setMarqueeRepeatLimit(-1);
        setTextSize(preferredSp);
        // TextView reapplies its state-list colour on draw; an opaque base prevents the
        // theme's secondary-text alpha from dimming the gradient shader.
        setTextColor(Color.WHITE);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setAlpha(1f);
        // Overlay windows are rendered by OEM SystemUI/compositor stacks that are not identical.
        // Keep the text path explicit and hardware backed, while avoiding blurred shadow layers.
        setLayerType(View.LAYER_TYPE_NONE, null);
        setBackgroundColor(Color.TRANSPARENT);
        getPaint().clearShadowLayer();
        TextPaint p = getPaint();
        p.setAntiAlias(true);
        p.setSubpixelText(true);
        p.setDither(true);
        p.setHinting(Paint.HINTING_ON);
    }

    public void setGradientColors(int a, int b) {
        start = a;
        end = b;
        gradientWidth = -1;
        invalidate();
    }

    public void setOutline(float dp, int color) {
        float d = Math.max(1f, getResources().getDisplayMetrics().density);
        outlinePx = Math.max(.75f, dp * d);
        outlineColor = color;
        invalidate();
    }

    public void setPreferredTextSize(float sp, float minSp) {
        preferredSp = Math.max(12f, sp);
        this.minSp = Math.max(11f, Math.min(preferredSp, minSp));
        adapt();
    }

    @Override public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        post(this::adapt);
    }

    private void adapt() {
        int available = Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
        setTextSize(preferredSp);
        TextPaint p = getPaint();
        float width = p.measureText(getText() == null ? "" : getText().toString());
        if (width > available && available > 20) {
            float ratio = available / Math.max(1f, width);
            // Do not shrink a long desktop lyric into a thin, soft line. Once the readable floor
            // is reached, native marquee takes over instead of reducing glyph detail further.
            float target = Math.max(minSp, preferredSp * ratio * .985f);
            setTextSize(target);
            width = getPaint().measureText(getText() == null ? "" : getText().toString());
        }
        boolean marquee = width > available * 1.01f;
        setEllipsize(marquee ? TextUtils.TruncateAt.MARQUEE : null);
        setSelected(marquee);
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        gradientWidth = -1;
        post(this::adapt);
    }

    private Shader fillShader() {
        int width = Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
        if (gradient == null || gradientWidth != width || gradientStart != start || gradientEnd != end) {
            float left = getPaddingLeft();
            float right = Math.max(left + 1f, getWidth() - getPaddingRight());
            gradient = new LinearGradient(left, 0f, right, 0f, start, end, Shader.TileMode.CLAMP);
            gradientWidth = width;
            gradientStart = start;
            gradientEnd = end;
        }
        return gradient;
    }

    @Override protected void onDraw(Canvas canvas) {
        TextPaint p = getPaint();
        Shader oldShader = p.getShader();
        Paint.Style oldStyle = p.getStyle();
        float oldStrokeWidth = p.getStrokeWidth();
        int oldColor = p.getColor();
        Paint.Join oldJoin = p.getStrokeJoin();

        // One native text pass: no repeated TextView draw or scaled offscreen layer.
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(oldStrokeWidth);
        p.setShader(fillShader());
        super.onDraw(canvas);

        p.setShader(oldShader);
        p.setStyle(oldStyle);
        p.setStrokeWidth(oldStrokeWidth);
        p.setStrokeJoin(oldJoin);
        p.setColor(oldColor);
    }
}
