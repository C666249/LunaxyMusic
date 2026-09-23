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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Scene-triggered, one-tip-at-a-time coach mark for hidden Lunaxy interactions.
 * UI-only by design: no playback, download, provider, storage or network dependency.
 */
public final class ContextCoachOverlay extends FrameLayout {
    public interface Callback {
        void onLater();
        void onGotIt();
    }

    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF targetRect = new RectF();
    private final RectF haloRect = new RectF();

    private final LinearLayout card;
    private final TextView titleView;
    private final TextView bodyView;
    private final TextView laterView;
    private final TextView gotItView;

    private View target;
    private Callback callback;
    private int cardX;
    private int cardY;
    private int cardWidth;
    private int cardHeight;
    private boolean cardBelowTarget = true;

    public ContextCoachOverlay(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        // A black-glass scrim fits the starfield without introducing expensive live blur.
        shadePaint.setColor(Color.argb(205, 0, 0, 0));
        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Ui.dp(context, 1.5f));
        borderPaint.setColor(Color.argb(238, 123, 223, 255));
        borderPaint.setShadowLayer(Ui.dp(context, 13), 0, 0, Color.argb(158, 73, 205, 255));

        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeWidth(Ui.dp(context, 2.1f));
        arrowPaint.setColor(Color.argb(242, 190, 239, 255));
        arrowPaint.setShadowLayer(Ui.dp(context, 8), 0, 0, Color.argb(125, 74, 210, 255));

        card = Ui.column(context);
        card.setPadding(Ui.dp(context, 18), Ui.dp(context, 15), Ui.dp(context, 18), Ui.dp(context, 15));
        card.setBackground(Ui.stroke(Color.argb(248, 7, 10, 16), 22,
                Color.argb(76, 183, 238, 255), context));
        card.setElevation(Ui.dp(context, 20));

        TextView eyebrow = Ui.text(context, "✦  LUNAXY 小技巧", 10.7f, Ui.CYAN, true);
        eyebrow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(eyebrow, Ui.lp(-1, Ui.dp(context, 28)));

        titleView = Ui.text(context, "", 17f, Ui.TEXT, true);
        LinearLayout.LayoutParams titleLp = Ui.lp(-1, -2);
        titleLp.topMargin = Ui.dp(context, 4);
        card.addView(titleView, titleLp);

        bodyView = Ui.text(context, "", 12.2f, Ui.TEXT_2, false);
        bodyView.setLineSpacing(Ui.dp(context, 2), 1.04f);
        LinearLayout.LayoutParams bodyLp = Ui.lp(-1, -2);
        bodyLp.topMargin = Ui.dp(context, 8);
        card.addView(bodyView, bodyLp);

        LinearLayout footer = Ui.row(context);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        laterView = Ui.text(context, "稍后再说", 11.2f, Ui.DIM, true);
        laterView.setGravity(Gravity.CENTER);
        laterView.setClickable(true);
        Ui.applyRipple(laterView, Color.argb(28, 255, 255, 255));
        laterView.setOnClickListener(v -> { if (callback != null) callback.onLater(); });
        footer.addView(laterView, new LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1f));
        View gap = new View(context);
        footer.addView(gap, Ui.lp(Ui.dp(context, 8), 1));

        gotItView = Ui.text(context, "知道了", 12f, Color.rgb(6, 15, 20), true);
        gotItView.setGravity(Gravity.CENTER);
        gotItView.setBackground(Ui.gradient(new int[]{Color.rgb(71, 220, 183), Color.rgb(73, 196, 247)}, 16, context));
        gotItView.setClickable(true);
        Ui.applyRipple(gotItView, Color.argb(32, 0, 0, 0));
        gotItView.setOnClickListener(v -> { if (callback != null) callback.onGotIt(); });
        footer.addView(gotItView, new LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1.35f));
        LinearLayout.LayoutParams footerLp = Ui.lp(-1, Ui.dp(context, 44));
        footerLp.topMargin = Ui.dp(context, 14);
        card.addView(footer, footerLp);

        addView(card);
    }

    public void setTip(View target, String title, String body, Callback callback) {
        this.target = target;
        this.callback = callback;
        titleView.setText(title == null ? "" : title);
        bodyView.setText(body == null ? "" : body);
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
        cardWidth = Math.min(Ui.dp(getContext(), 344), Math.max(Ui.dp(getContext(), 252), width - Ui.dp(getContext(), 34)));
        card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height - Ui.dp(getContext(), 38)), MeasureSpec.AT_MOST));
        cardHeight = card.getMeasuredHeight();
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        updateTargetRect();
        int margin = Ui.dp(getContext(), 17);
        int gap = Ui.dp(getContext(), 22);
        float centerX = targetRect.isEmpty() ? getWidth() / 2f : targetRect.centerX();
        cardX = Math.round(centerX - cardWidth / 2f);
        cardX = Math.max(margin, Math.min(cardX, getWidth() - margin - cardWidth));

        int below = Math.round(haloRect.bottom) + gap;
        int above = Math.round(haloRect.top) - gap - cardHeight;
        if (!targetRect.isEmpty() && below + cardHeight <= getHeight() - margin) {
            cardY = below;
            cardBelowTarget = true;
        } else if (!targetRect.isEmpty() && above >= margin) {
            cardY = above;
            cardBelowTarget = false;
        } else {
            cardY = Math.max(margin, (getHeight() - cardHeight) / 2);
            cardBelowTarget = cardY > targetRect.centerY();
        }
        card.layout(cardX, cardY, cardX + cardWidth, cardY + cardHeight);
    }

    private void updateTargetRect() {
        targetRect.setEmpty();
        haloRect.setEmpty();
        if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0 || !target.isShown()) return;
        int[] tl = new int[2];
        int[] ol = new int[2];
        target.getLocationOnScreen(tl);
        getLocationOnScreen(ol);
        targetRect.set(tl[0] - ol[0], tl[1] - ol[1],
                tl[0] - ol[0] + target.getWidth(), tl[1] - ol[1] + target.getHeight());
        float padX = Ui.dp(getContext(), target.getWidth() < Ui.dp(getContext(), 120) ? 7 : 9);
        float padY = Ui.dp(getContext(), 7);
        haloRect.set(targetRect.left - padX, targetRect.top - padY,
                targetRect.right + padX, targetRect.bottom + padY);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        updateTargetRect();
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), shadePaint);
        if (!targetRect.isEmpty()) {
            float radius = autoRadius();
            canvas.drawRoundRect(haloRect, radius, radius, clearPaint);
        }
        canvas.restoreToCount(save);

        if (!targetRect.isEmpty()) {
            float radius = autoRadius();
            canvas.drawRoundRect(haloRect, radius, radius, borderPaint);
            drawArrow(canvas);
        }
        super.dispatchDraw(canvas);
    }

    private float autoRadius() {
        if (haloRect.isEmpty()) return Ui.dp(getContext(), 18);
        float half = haloRect.height() / 2f;
        // Pills/buttons get a capsule spotlight, larger cards get a calm rounded rectangle.
        if (haloRect.width() > haloRect.height() * 2.2f && haloRect.height() <= Ui.dp(getContext(), 72)) return half;
        return Math.min(Ui.dp(getContext(), 22), half);
    }

    private void drawArrow(Canvas canvas) {
        float tx = targetRect.centerX();
        float ty = cardBelowTarget ? haloRect.bottom + Ui.dp(getContext(), 3) : haloRect.top - Ui.dp(getContext(), 3);
        float cx = Math.max(cardX + Ui.dp(getContext(), 28), Math.min(tx, cardX + cardWidth - Ui.dp(getContext(), 28)));
        float cy = cardBelowTarget ? cardY - Ui.dp(getContext(), 4) : cardY + cardHeight + Ui.dp(getContext(), 4);
        float bendY = (ty + cy) / 2f;
        Path path = new Path();
        path.moveTo(cx, cy);
        path.cubicTo(cx, bendY, tx, bendY, tx, ty);
        canvas.drawPath(path, arrowPaint);

        float head = Ui.dp(getContext(), 7);
        float dir = cardBelowTarget ? 1f : -1f;
        Path arrow = new Path();
        arrow.moveTo(tx, ty);
        arrow.lineTo(tx - head, ty + dir * head);
        arrow.moveTo(tx, ty);
        arrow.lineTo(tx + head, ty + dir * head);
        canvas.drawPath(arrow, arrowPaint);
    }
}
