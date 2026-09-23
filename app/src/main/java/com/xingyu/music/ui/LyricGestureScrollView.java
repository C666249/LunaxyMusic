package com.xingyu.music.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/**
 * Dedicated lyric viewport that owns every touch stream beginning inside the lyric panel.
 *
 * Why this exists instead of a normal OnTouchListener: lyric rows are clickable so a row
 * can become the touch target before the ScrollView's listener sees ACTION_DOWN. Once that
 * happens, an outer ScrollView may intercept the vertical drag and move the whole page.
 * dispatchTouchEvent() observes the stream before child dispatch, immediately locks all
 * ancestors out, while still delegating to ScrollView so taps on lyric rows keep working.
 */
public final class LyricGestureScrollView extends ScrollView {
    public interface GestureObserver {
        void onDown(MotionEvent event);
        void onMove(MotionEvent event, boolean movedBeyondSlop);
        void onUp(MotionEvent event, boolean movedBeyondSlop, boolean cancelled);
    }

    private final int touchSlop;
    private GestureObserver observer;
    private float downY;
    private float downX;
    private boolean movedBeyondSlop;

    public LyricGestureScrollView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setGestureObserver(GestureObserver observer) {
        this.observer = observer;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downY = event.getY();
            downX = event.getX();
            movedBeyondSlop = false;
            // Lock the outer now-playing ScrollView before clickable lyric children see DOWN.
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            if (observer != null) observer.onDown(event);
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dy = event.getY() - downY;
            if (!movedBeyondSlop && (Math.abs(dy) > touchSlop || Math.abs(event.getX()-downX)>touchSlop)) {
                movedBeyondSlop = true;
            }
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            if (observer != null) observer.onMove(event, movedBeyondSlop);
        }

        boolean handled = super.dispatchTouchEvent(event);

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (observer != null) observer.onUp(event, movedBeyondSlop,
                    action == MotionEvent.ACTION_CANCEL);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            movedBeyondSlop = false;
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // ScrollView keeps its native tap-vs-drag behavior. Ancestors are already locked by
        // dispatchTouchEvent(), so a vertical drag inside this viewport can only scroll lyrics.
        return super.onInterceptTouchEvent(event);
    }
}
