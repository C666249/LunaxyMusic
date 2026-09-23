package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.*;
import android.widget.FrameLayout;

/** Transparent viewport with soft alpha edges, independent of the page background. */
public final class FadingLyricsFrame extends FrameLayout {
    private final Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);
    public FadingLyricsFrame(Context context) {
        super(context);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        mask.setShader(new LinearGradient(0,0,0,Math.max(1,h),
                new int[]{Color.TRANSPARENT,Color.BLACK,Color.BLACK,Color.TRANSPARENT},
                new float[]{0,.12f,.88f,1},Shader.TileMode.CLAMP));
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        int save=canvas.saveLayer(0,0,getWidth(),getHeight(),null);
        super.dispatchDraw(canvas);
        canvas.drawRect(0,0,getWidth(),getHeight(),mask);
        canvas.restoreToCount(save);
    }
}
