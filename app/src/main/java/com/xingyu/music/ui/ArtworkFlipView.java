package com.xingyu.music.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

/** Both faces stay laid out; lyrics keep their real text and scroll state throughout the flip. */
public final class ArtworkFlipView extends FrameLayout {
    private View front, back;
    private float progress;
    private boolean lyrics;
    private ValueAnimator animator;
    public interface ProgressListener { void onProgress(float progress); }
    private ProgressListener progressListener;
    public void setProgressListener(ProgressListener listener) { progressListener=listener; }
    public ArtworkFlipView(Context context) { super(context); setClipChildren(false); setClipToPadding(false); }
    public void setFaces(View front, View back) {
        this.front=front; this.back=back;
        addView(front,new LayoutParams(-1,-1)); addView(back,new LayoutParams(-1,-1));
        float distance=8000f*getResources().getDisplayMetrics().density;
        front.setCameraDistance(distance); back.setCameraDistance(distance); apply();
    }
    public boolean isLyricsVisible() { return lyrics; }
    public boolean isFlipping() { return animator!=null && animator.isRunning(); }
    public void showLyrics(boolean value) {
        lyrics=value;
        if(animator!=null) animator.cancel();
        animator=ValueAnimator.ofFloat(progress,value?1f:0f);
        animator.setDuration(Math.max(100,Math.round(420*Math.abs((value?1f:0f)-progress))));
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a->{ progress=(float)a.getAnimatedValue(); apply(); });
        animator.start();
    }
    private void apply() {
        if(front==null || back==null) return;
        boolean rear=progress>=.5f;
        front.setVisibility(rear?INVISIBLE:VISIBLE); back.setVisibility(rear?VISIBLE:INVISIBLE);
        front.setRotationY(-180f*progress); back.setRotationY(180f*(1f-progress));
        front.setImportantForAccessibility(rear?IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        back.setImportantForAccessibility(rear?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if(progressListener!=null) progressListener.onProgress(progress);
    }
    public void reset() {
        if(animator!=null) animator.cancel();
        lyrics=false; progress=0; apply();
    }
    @Override protected void onDetachedFromWindow() { reset(); super.onDetachedFromWindow(); }
}
