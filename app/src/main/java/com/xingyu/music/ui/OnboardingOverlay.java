package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Lightweight first-run coach-mark overlay for Lunaxy Music.
 *
 * This class is deliberately UI-only: it knows nothing about playback, sources or networking.
 * The real page stays underneath; the overlay dims it, cuts a rounded spotlight around a target
 * view, and points a small glass instruction card at that target.
 */
public final class OnboardingOverlay extends FrameLayout {
    public interface Callback {
        void onNext();
        void onSkip();
    }

    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF targetRect = new RectF();
    private final RectF expandedTargetRect = new RectF();
    private final LinearLayout card;
    private final TextView stepView;
    private final TextView titleView;
    private final TextView bodyView;
    private final TextView nextView;
    private final TextView skipView;

    private View target;
    private Callback callback;
    private int cardX;
    private int cardY;
    private int cardWidth;
    private int cardHeight;
    private boolean cardBelowTarget = true;

    public OnboardingOverlay(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        shadePaint.setColor(Color.argb(208, 0, 0, 0));
        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Ui.dp(context, 1.4f));
        borderPaint.setColor(Color.argb(215, Color.red(Ui.LYRIC_ICE_START), Color.green(Ui.LYRIC_ICE_START), Color.blue(Ui.LYRIC_ICE_START)));
        borderPaint.setShadowLayer(Ui.dp(context, 11), 0, 0, Color.argb(145, 105, 171, 255));

        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeWidth(Ui.dp(context, 2.0f));
        arrowPaint.setColor(Color.argb(235, 185, 232, 255));
        arrowPaint.setShadowLayer(Ui.dp(context, 7), 0, 0, Color.argb(120, 105, 171, 255));

        card = Ui.column(context);
        card.setPadding(Ui.dp(context, 17), Ui.dp(context, 15), Ui.dp(context, 17), Ui.dp(context, 14));
        card.setBackground(Ui.stroke(Color.argb(246, 9, 12, 18), 20,
                Color.argb(70, 201, 242, 255), context));
        card.setElevation(Ui.dp(context, 18));

        LinearLayout top = Ui.row(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        stepView = Ui.text(context, "1 / 5", 10.5f, Ui.CYAN, true);
        top.addView(stepView, new LinearLayout.LayoutParams(0, Ui.dp(context, 28), 1f));
        skipView = Ui.text(context, "跳过", 10.8f, Ui.DIM, true);
        skipView.setGravity(Gravity.CENTER);
        skipView.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), 0);
        skipView.setClickable(true);
        Ui.applyRipple(skipView, Color.argb(32, 255, 255, 255));
        skipView.setOnClickListener(v -> {
            if (callback != null) callback.onSkip();
        });
        top.addView(skipView, Ui.lp(Ui.dp(context, 58), Ui.dp(context, 28)));
        card.addView(top, Ui.lp(-1, Ui.dp(context, 28)));

        titleView = Ui.text(context, "欢迎使用 Lunaxy Music", 16.5f, Ui.TEXT, true);
        LinearLayout.LayoutParams titleLp = Ui.lp(-1, -2);
        titleLp.topMargin = Ui.dp(context, 4);
        card.addView(titleView, titleLp);

        bodyView = Ui.text(context, "", 12.2f, Ui.TEXT_2, false);
        bodyView.setGravity(Gravity.START);
        bodyView.setLineSpacing(Ui.dp(context, 2), 1f);
        LinearLayout.LayoutParams bodyLp = Ui.lp(-1, -2);
        bodyLp.topMargin = Ui.dp(context, 8);
        card.addView(bodyView, bodyLp);

        LinearLayout footer = Ui.row(context);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        nextView = Ui.text(context, "下一步  ›", 11.8f, Color.rgb(10, 14, 20), true);
        nextView.setGravity(Gravity.CENTER);
        nextView.setBackground(Ui.primaryFill(Ui.CYAN, 15, context));
        nextView.setClickable(true);
        Ui.applyRipple(nextView, Color.argb(32, 0, 0, 0));
        nextView.setOnClickListener(v -> {
            if (callback != null) callback.onNext();
        });
        footer.addView(nextView, Ui.lp(Ui.dp(context, 104), Ui.dp(context, 38)));
        LinearLayout.LayoutParams footerLp = Ui.lp(-1, Ui.dp(context, 42));
        footerLp.topMargin = Ui.dp(context, 12);
        card.addView(footer, footerLp);

        addView(card);
    }

    public void setStep(View target, String title, String body, int index, int total,
                        boolean last, Callback callback) {
        this.target = target;
        this.callback = callback;
        stepView.setText(Math.max(1, index) + " / " + Math.max(index, total));
        titleView.setText(title == null ? "" : title);
        bodyView.setText(body == null ? "" : body);
        nextView.setText(last ? "开始使用" : "下一步  ›");
        skipView.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        requestLayout();
        invalidate();
    }

    public void refreshTarget() {
        requestLayout();
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        cardWidth = Math.min(Ui.dp(getContext(), 326), Math.max(Ui.dp(getContext(), 244), width - Ui.dp(getContext(), 36)));
        int childW = MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY);
        int childH = MeasureSpec.makeMeasureSpec(Math.max(0, height - Ui.dp(getContext(), 42)), MeasureSpec.AT_MOST);
        card.measure(childW, childH);
        cardHeight = card.getMeasuredHeight();
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        updateTargetRect();
        int margin = Ui.dp(getContext(), 18);
        int gap = Ui.dp(getContext(), 22);
        float centerX = targetRect.isEmpty() ? getWidth() / 2f : targetRect.centerX();
        cardX = Math.round(centerX - cardWidth / 2f);
        cardX = Math.max(margin, Math.min(cardX, getWidth() - margin - cardWidth));

        int belowY = Math.round(targetRect.bottom) + gap;
        int aboveY = Math.round(targetRect.top) - gap - cardHeight;
        if (!targetRect.isEmpty() && belowY + cardHeight <= getHeight() - margin) {
            cardY = belowY;
            cardBelowTarget = true;
        } else if (!targetRect.isEmpty() && aboveY >= margin) {
            cardY = aboveY;
            cardBelowTarget = false;
        } else {
            cardY = Math.max(margin, (getHeight() - cardHeight) / 2);
            cardBelowTarget = cardY > targetRect.centerY();
        }
        card.layout(cardX, cardY, cardX + cardWidth, cardY + cardHeight);
    }

    private void updateTargetRect() {
        targetRect.setEmpty();
        if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return;
        int[] tl = new int[2];
        int[] ol = new int[2];
        target.getLocationOnScreen(tl);
        getLocationOnScreen(ol);
        targetRect.set(tl[0] - ol[0], tl[1] - ol[1],
                tl[0] - ol[0] + target.getWidth(), tl[1] - ol[1] + target.getHeight());
        float pad = Ui.dp(getContext(), 7);
        expandedTargetRect.set(targetRect.left - pad, targetRect.top - pad,
                targetRect.right + pad, targetRect.bottom + pad);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        updateTargetRect();
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), shadePaint);
        if (!targetRect.isEmpty()) {
            float radius = Math.min(Ui.dp(getContext(), 22), expandedTargetRect.height() / 2f);
            canvas.drawRoundRect(expandedTargetRect, radius, radius, clearPaint);
        }
        canvas.restoreToCount(save);

        if (!targetRect.isEmpty()) {
            float radius = Math.min(Ui.dp(getContext(), 22), expandedTargetRect.height() / 2f);
            canvas.drawRoundRect(expandedTargetRect, radius, radius, borderPaint);
            drawArrow(canvas);
        }
        super.dispatchDraw(canvas);
    }

    private void drawArrow(Canvas canvas) {
        float tx = targetRect.centerX();
        float ty = cardBelowTarget ? expandedTargetRect.bottom + Ui.dp(getContext(), 3)
                : expandedTargetRect.top - Ui.dp(getContext(), 3);
        float cx = Math.max(cardX + Ui.dp(getContext(), 28), Math.min(tx, cardX + cardWidth - Ui.dp(getContext(), 28)));
        float cy = cardBelowTarget ? cardY - Ui.dp(getContext(), 4) : cardY + cardHeight + Ui.dp(getContext(), 4);

        float bendY = (ty + cy) / 2f;
        Path path = new Path();
        path.moveTo(cx, cy);
        path.cubicTo(cx, bendY, tx, bendY, tx, ty);
        canvas.drawPath(path, arrowPaint);

        float dir = cardBelowTarget ? 1f : -1f;
        float head = Ui.dp(getContext(), 7);
        Path arrow = new Path();
        arrow.moveTo(tx, ty);
        arrow.lineTo(tx - head, ty + dir * head);
        arrow.moveTo(tx, ty);
        arrow.lineTo(tx + head, ty + dir * head);
        canvas.drawPath(arrow, arrowPaint);
    }
}
