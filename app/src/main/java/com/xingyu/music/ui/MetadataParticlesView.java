package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.TextView;
import java.util.ArrayList;

/** Small, bounded glyph samples disperse and retrace their paths when the face is reversed. */
public final class MetadataParticlesView extends View {
    private final ArrayList<float[]> points=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;
    public MetadataParticlesView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    public void capture(TextView... labels) {
        points.clear(); int[] origin=new int[2]; getLocationOnScreen(origin);
        for(TextView label:labels) {
            if(label==null || label.getWidth()==0 || label.getHeight()==0) continue;
            Bitmap bitmap=Bitmap.createBitmap(label.getWidth(),label.getHeight(),Bitmap.Config.ARGB_8888);
            label.draw(new Canvas(bitmap)); int[] location=new int[2]; label.getLocationOnScreen(location);
            int step=Math.max(4,Math.round(3*getResources().getDisplayMetrics().density));
            for(int y=0;y<bitmap.getHeight();y+=step) for(int x=0;x<bitmap.getWidth();x+=step) {
                if(Color.alpha(bitmap.getPixel(x,y))>120 && points.size()<450)
                    points.add(new float[]{x+location[0]-origin[0],y+location[1]-origin[1]});
            }
        }
    }
    public void setProgress(float value) { progress=value; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        float d=getResources().getDisplayMetrics().density;
        paint.setColor(Color.WHITE); paint.setAlpha(Math.round(150f*(float)Math.sin(Math.PI*progress)));
        for(int i=0;i<points.size();i++) {
            float[] point=points.get(i); float dx=(float)Math.sin(i*2.4)*24*d*progress;
            float dy=-(12+(i%11)*3)*d*progress;
            canvas.drawCircle(point[0]+dx,point[1]+dy,.7f*d,paint);
        }
    }
}
