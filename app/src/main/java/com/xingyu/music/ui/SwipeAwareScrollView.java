package com.xingyu.music.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.VelocityTracker;
import android.widget.ScrollView;

/**
 * Now-playing page ScrollView that passively observes horizontal cover swipes.
 *
 * V19 also supports a hard gesture-exclusion child (the lyric viewport): a touch stream
 * beginning inside that rectangle is never intercepted for page scrolling and never treated
 * as a track-swipe. This makes the boundary deterministic: inside = lyrics, outside = page.
 */
public final class SwipeAwareScrollView extends ScrollView {
    public interface SwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
        default void onSwipeProgress(float dxPx, float fraction) { }
        default void onSwipeCancelled() { }
        /** direction: +1 = next/left, -1 = previous/right; velocityX is px/s. */
        default void onSwipeCommit(int direction, float velocityX) {
            if (direction > 0) onSwipeLeft(); else onSwipeRight();
        }
    }

    private SwipeListener listener;
    private View gestureExclusionView;
    private float downX;
    private float downY;
    private boolean tracking;
    private boolean horizontalGesture;
    private boolean exclusionGesture;
    private VelocityTracker velocityTracker;
    private float swipeSensitivity = 1f;

    public void setSwipeSensitivity(float value) { swipeSensitivity = value; }

    public SwipeAwareScrollView(Context context) {
        super(context);
    }

    public void setSwipeListener(SwipeListener listener) {
        this.listener = listener;
    }

    /** Every gesture beginning inside this child belongs to that child, not this page. */
    public void setGestureExclusionView(View view) {
        gestureExclusionView = view;
    }

    private boolean insideGestureExclusion(MotionEvent event) {
        if (gestureExclusionView == null || !gestureExclusionView.isShown() || gestureExclusionView.getAlpha() < .08f) return false;
        int[] location = new int[2];
        gestureExclusionView.getLocationOnScreen(location);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= location[0]
                && rawX < location[0] + gestureExclusionView.getWidth()
                && rawY >= location[1]
                && rawY < location[1] + gestureExclusionView.getHeight();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            exclusionGesture = insideGestureExclusion(event);
        }
        if (exclusionGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                exclusionGesture = false;
            }
            return false;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        final float density = getResources().getDisplayMetrics().density;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                horizontalGesture = false;
                if (velocityTracker != null) velocityTracker.recycle();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(ev);
                exclusionGesture = insideGestureExclusion(ev);
                // Track switching is intentionally limited to the upper artwork/header area.
                float top = 54f * density;
                float bottom = 500f * density;
                tracking = !exclusionGesture && getScrollY() <= 120f * density
                        && downY >= top && downY <= bottom
                        && downX > 24f * density && downX < getWidth() - 24f * density;
                break;
            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(ev);
                if (tracking && listener != null) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    float slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (!horizontalGesture && Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) {
                        tracking = false;
                    } else if (!horizontalGesture && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        horizontalGesture = true;
                        MotionEvent cancel = MotionEvent.obtain(ev);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (horizontalGesture) {
                        float fraction = Math.abs(dx) / VinylGesturePolicy.slotDistance(getWidth(), swipeSensitivity);
                        listener.onSwipeProgress(dx, fraction);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (velocityTracker != null) velocityTracker.addMovement(ev);
                boolean owned = horizontalGesture;
                if (tracking && horizontalGesture && listener != null) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    float vx = 0f;
                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000);
                        vx = velocityTracker.getXVelocity();
                    }
                    listener.onSwipeProgress(dx, Math.abs(dx) / VinylGesturePolicy.slotDistance(getWidth(), swipeSensitivity));
                    if (VinylGesturePolicy.shouldCommit(dx, dy, vx, getWidth(), density, swipeSensitivity)) {
                        int direction = (Math.abs(dx) >= 18f * density ? dx : vx) < 0f ? 1 : -1;
                        listener.onSwipeCommit(direction, vx);
                    } else listener.onSwipeCancelled();
                }
                tracking = false;
                horizontalGesture = false;
                if (velocityTracker != null) { velocityTracker.recycle(); velocityTracker = null; }
                if (owned) {
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    exclusionGesture = false;
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                if (horizontalGesture && listener != null) listener.onSwipeCancelled();
                tracking = false;
                horizontalGesture = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (velocityTracker != null) { velocityTracker.recycle(); velocityTracker = null; }
                break;
        }
        boolean handled = super.dispatchTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_UP
                || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            exclusionGesture = false;
        }
        return handled;
    }
}
