package com.xingyu.music.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.ListView;

/** Native one-to-one dragging, with smoothly reduced friction for deliberate fast flings. */
public final class VelocityListView extends ListView {
    private VelocityTracker tracker;
    private final Runnable hideThumb=()->suspendFastScroll();
    public void suspendFastScroll() {
        removeCallbacks(hideThumb); setFastScrollAlwaysVisible(false); setFastScrollEnabled(false);
    }
    public VelocityListView(Context context) {
        super(context);
        suspendFastScroll();
        setVerticalScrollbarPosition(SCROLLBAR_POSITION_RIGHT);
        setScrollBarStyle(SCROLLBARS_OUTSIDE_OVERLAY);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof android.view.ViewGroup
                && getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup parent = (android.view.ViewGroup) getParent();
            int gutter = Math.max(0, Math.min(parent.getPaddingLeft(), parent.getPaddingRight())
                    - Math.round(4 * getResources().getDisplayMetrics().density));
            android.view.ViewGroup.MarginLayoutParams params =
                    (android.view.ViewGroup.MarginLayoutParams) getLayoutParams();
            params.leftMargin = -gutter;
            params.rightMargin = -gutter;
            setPadding(gutter, getPaddingTop(), gutter, getPaddingBottom());
            setLayoutParams(params);
        }
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            removeCallbacks(hideThumb);
            if(event.getX()<getWidth()-16*getResources().getDisplayMetrics().density) suspendFastScroll();
            recycle(); tracker=VelocityTracker.obtain();
            setFriction(ViewConfiguration.getScrollFriction());
        }
        if(tracker!=null) tracker.addMovement(event);
        float speed=0;
        if(action==MotionEvent.ACTION_UP && tracker!=null) {
            tracker.computeCurrentVelocity(1000);
            speed=Math.abs(tracker.getYVelocity()/getResources().getDisplayMetrics().density);
            setFriction(ViewConfiguration.getScrollFriction()*Player926Policy.friction(
                    tracker.getYVelocity()/getResources().getDisplayMetrics().density));
        }
        boolean handled=super.dispatchTouchEvent(event);
        if(action==MotionEvent.ACTION_UP) {
            if(speed>1800f) { setFastScrollEnabled(true); setFastScrollAlwaysVisible(true); }
            if(isFastScrollEnabled()) postDelayed(hideThumb,1600);
        } else if(action==MotionEvent.ACTION_CANCEL) suspendFastScroll();
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) recycle();
        return handled;
    }
    private void recycle() { if(tracker!=null) { tracker.recycle(); tracker=null; } }
    @Override protected void onDetachedFromWindow() { recycle(); super.onDetachedFromWindow(); }
}
