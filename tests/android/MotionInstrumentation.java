package com.xingyu.music.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import com.xingyu.music.MainActivity;
import com.xingyu.music.data.PlaybackSessionStore;
import com.xingyu.music.download.OfflineStore;
import com.xingyu.music.model.Song;
import com.xingyu.music.playback.PlaybackService;
import com.xingyu.music.ui.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;

/** Runs only in the disposable com.xingyu.music.motionqa application, never user installations. */
public final class MotionInstrumentation extends Instrumentation {
    interface Action { void run() throws Exception; }
    interface Condition { boolean get() throws Exception; }
    private MainActivity activity;
    private PlaybackService playback;
    private File output;
    private final StringBuilder report = new StringBuilder();
    private int checks;
    private boolean focused;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); focused = "true".equals(arguments.getString("focused")); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(getTargetContext().getPackageName().equals("com.xingyu.music.motionqa"), "isolated test identity");
            output = new File(getTargetContext().getExternalFilesDir(null), "motion-qa");
            output.mkdirs();
            fixtures();
            Intent intent = new Intent().setClassName(getTargetContext(), "com.xingyu.music.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity) startActivitySync(intent);
            await(() -> get(activity, "playback") != null, "playback service bound");
            ui(() -> playback = (PlaybackService) get(activity, "playback"));
            await(() -> playback.currentSong() != null, "restored test queue");
            SystemClock.sleep(800);
            testGestureOwnership();
            testArtworkFaces();
            testLyricTapOwnership();
            testDesktopText();
            testStack();
            testContinuousArtwork();
            if (!focused) {
            testQueue();
            testHeroCancellation();
            testMiniGeometry();
            testSwipeCancellation();
            testManualOrder();
            testLongDrag();
            }
            ui(() -> { invoke(activity, "openNowPlaying"); });
            SystemClock.sleep(900);
            screenshot("final-player.png");
            testBackgroundCompletion();
            report.append("PASS: ").append(checks).append(" Android assertions\n");
            result.putString("stream", report.toString());
            writeReport();
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
            report.append("FAIL\n").append(trace);
            try { screenshot("failure.png"); writeReport(); } catch (Throwable ignored) { }
            result.putString("stream", report.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void testLyricTapOwnership() throws Exception {
        ui(() -> invoke(activity,"toggleArtworkLyrics")); SystemClock.sleep(500);
        ui(() -> {
            View scroll=(View)get(activity,"lyricScroll"); long t=SystemClock.uptimeMillis();
            event(scroll,0,t,0,150,220); event(scroll,2,t,70,150,130); event(scroll,1,t,120,150,130);
            check(((ArtworkFlipView)get(activity,"artworkFlip")).isLyricsVisible(),"lyric drag never flips to record");
        });
        SystemClock.sleep(2300);
        ui(() -> {
            View scroll=(View)get(activity,"lyricScroll"); long t=SystemClock.uptimeMillis();
            event(scroll,0,t,0,150,220); event(scroll,1,t,50,150,220);
        });
        SystemClock.sleep(500);
        ui(() -> check(!((ArtworkFlipView)get(activity,"artworkFlip")).isLyricsVisible(),"plain lyric tap returns to record without button"));
    }
    private void testBackgroundCompletion() throws Exception {
        final int[] expected={0};
        final PlaybackService.Listener[] listener={null};
        ui(() -> {
            listener[0]=(PlaybackService.Listener)get(playback,"listener");
            expected[0]=(playback.currentQueueIndex()+1)%playback.queueSnapshot().size();
            playback.setListener(null);
            playback.setPlayMode(PlaybackService.MODE_SEQUENTIAL);
            playback.resume();
        });
        await(() -> playback.snapshot().playing,"local audio playing before backgrounding");
        ui(() -> {
            activity.moveTaskToBack(true);
            playback.seekTo(179400);
        });
        await(() -> playback.currentQueueIndex()==expected[0] && playback.snapshot().playing,"background natural completion advances without Activity listener");
        ui(() -> {
            expected[0]=(playback.currentQueueIndex()+1)%playback.queueSnapshot().size();
            ((androidx.media3.common.Player)get(playback,"player")).stop();
            Field error=PlaybackService.class.getDeclaredField("lastError"); error.setAccessible(true); error.set(playback,"QA failed route");
            invoke(playback,"notifyState");
        });
        await(() -> playback.currentQueueIndex()==expected[0] && playback.snapshot().playing,"failed track advances from service without Activity");
        ui(() -> {
            invoke(playback,"holdTransitionWakeLock"); playback.pause();
            check(!((android.os.PowerManager.WakeLock)get(playback,"transitionWakeLock")).isHeld(),"pause releases transition wake lock");
        });
    }
    private void testDesktopText() throws Exception {
        final DesktopLyricLaneView[] lane={null};
        ui(() -> {
            lane[0]=new DesktopLyricLaneView(activity);
            lane[0].setText("让每一句歌词，都清晰可见");
            lane[0].setPadding(16,12,16,12);
            ((ViewGroup)activity.getWindow().getDecorView()).addView(lane[0],new ViewGroup.LayoutParams(-1,150));
            check(lane[0].getLayerType()==View.LAYER_TYPE_NONE,"desktop text has no forced texture layer");
            check(lane[0].getShadowRadius()==0f,"desktop text has no blur shadow");
            check(lane[0].getCurrentTextColor()==Color.WHITE,"desktop gradient uses opaque text base");
            check(((android.graphics.drawable.ColorDrawable)lane[0].getBackground()).getColor()==Color.TRANSPARENT,"desktop lyric background is transparent");
        });
        SystemClock.sleep(300); screenshot("desktop-text.png");
        ui(() -> ((ViewGroup)lane[0].getParent()).removeView(lane[0]));
    }
    private void testArtworkFaces() throws Exception {
        ui(() -> invoke(activity,"openNowPlaying"));
        SystemClock.sleep(700);
        ui(() -> {
            VinylStackView stack=(VinylStackView)get(activity,"nowVinylStack");
            check(!stack.isExpanded(),"default is single record");
            VinylRecordView[] records=(VinylRecordView[])get(stack,"records");
            check(records[1].getAlpha()==0 && records[3].getAlpha()==0,"resting neighbours hidden");
            invoke(activity,"applyTrackSwipeProgress",-1000f,4f);
            check((int)get(activity,"trackBrowseWindowOffset")==1,"single mode limits swipe to one track");
            invoke(activity,"restoreTrackSwipeSurface");
        });
        SystemClock.sleep(300);
        ui(() -> {
            invoke(activity,"toggleArtworkLyrics");
        });
        SystemClock.sleep(500);
        ui(() -> {
            ArtworkFlipView flip=(ArtworkFlipView)get(activity,"artworkFlip");
            View lyric=(View)get(activity,"lyricPanel");
            check(flip.isLyricsVisible() && lyric.getVisibility()==View.VISIBLE,"tap reveals lyric face");
            check(Math.abs(lyric.getRotationY())<.001f,"lyrics end upright, never mirrored");
            check(lyric.getParent()==flip,"lyrics occupy artwork area");
            check(lyric instanceof FadingLyricsFrame && ((ViewGroup)lyric).getChildCount()==2,"lyrics have soft edges and no return or settings buttons");
            invoke(activity,"toggleArtworkLyrics");
        });
        SystemClock.sleep(500);
        screenshot("single-record.png");
        ui(() -> {
            invoke(activity,"toggleArtworkLyrics");
        });
        SystemClock.sleep(500);
        ui(() -> {
            LinearLayout box=(LinearLayout)get(activity,"lyricBox"); box.removeAllViews();
            for(int i=0;i<5;i++) {
                LyricLineView line=new LyricLineView(activity);
                line.setText(new String[]{"在安静的夜里","沿着星光慢慢前行","把这一刻留在心底","听见远方的回声","下一段旅程将要开始"}[i]);
                line.setActiveLine(i==2); line.setFocusDistance(Math.abs(i-2)); line.setKaraokeProgress(.65f);
                box.addView(line,new LinearLayout.LayoutParams(-1,110));
            }
        });
        SystemClock.sleep(350); screenshot("lyric-face.png");
        ui(() -> {
            invoke(activity,"toggleArtworkLyrics");
        });
        SystemClock.sleep(500);
        ui(() -> invoke(activity,"toggleVinylBrowse"));
        SystemClock.sleep(350);
        ui(() -> check(((VinylStackView)get(activity,"nowVinylStack")).isExpanded(),"long press expands record browsing"));
    }
    private void testContinuousArtwork() throws Exception {
        ui(() -> invoke(activity, "openNowPlaying"));
        SystemClock.sleep(800);
        ui(() -> {
            invoke(activity, "applyTrackSwipeProgress", -500f, 4.2f);
            String[] before = ((String[]) get(activity, "stackArtworkKeys")).clone();
            invoke(activity, "loadVinylStackNeighbours");
            check(java.util.Arrays.equals(before, (String[]) get(activity, "stackArtworkKeys")), "playback tick preserves browse window");
            for (ImageView loader : (ImageView[]) get(activity, "stackCoverLoaders"))
                check(loader != null, "all rotated slots retain an artwork loader");
            String travellingKey = before[3];
            invoke(activity, "applyTrackSwipeProgress", -600f, 5.2f);
            Bitmap delivered = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            delivered.eraseColor(Color.MAGENTA);
            invoke(activity, "applyStackArtwork", travellingKey, delivered, Color.MAGENTA);
            Object stack = get(activity, "nowVinylStack");
            check(get(invoke(stack, "currentRecord"), "cover") == delivered, "late neighbour artwork follows song into center");
            Bitmap obsolete = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            invoke(activity, "applyStackArtwork", "removed-song-key", obsolete, Color.RED);
            check(get(invoke(stack, "currentRecord"), "cover") == delivered, "unrelated stale artwork cannot replace center");
        });
        SystemClock.sleep(800);
        ui(() -> {
            invoke(activity, "applyTrackSwipeProgress", 500f, 3.2f);
            invoke(activity, "resetTrackBrowsePreviewToPlayback");
        });
        SystemClock.sleep(500);
        ui(() -> {
            Object stack = get(activity, "nowVinylStack");
            check(get(activity, "nowVinyl") == invoke(stack, "currentRecord"), "cancel restores actual center reference");
            check(get(invoke(stack, "currentRecord"), "cover") != null, "cancel reloads real center artwork");
            invoke(activity, "restoreTrackSwipeSurface");
        });
        SystemClock.sleep(400);
    }
    private void ui(Action action) throws Exception {
        final Throwable[] failure = {null};
        runOnMainSync(() -> { try { action.run(); } catch (Throwable e) { failure[0] = e; } });
        if (failure[0] != null) throw new Exception(failure[0]);
    }
    private void await(Condition condition, String label) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 8000;
        final boolean[] ready = {false};
        while (SystemClock.uptimeMillis() < deadline) {
            ui(() -> ready[0] = condition.get());
            if (ready[0]) { check(true, label); return; }
            SystemClock.sleep(80);
        }
        ui(() -> {
            if(playback!=null) report.append("DIAGNOSTIC ").append(playback.diagnostics())
                .append("\nindex=").append(playback.currentQueueIndex()).append(" pos=").append(playback.snapshot().positionMs)
                .append(" playing=").append(playback.snapshot().playing).append(" requested=").append(get(playback,"playbackRequested")).append('\n');
        });
        check(false, label + " timed out");
    }
    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++; report.append("PASS ").append(label).append('\n');
        if(output!=null) try { writeReport(); } catch(Exception ignored) { }
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Object invoke(Object object, String name, Object... args) throws Exception {
        for (Method method : object.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != args.length) continue;
            method.setAccessible(true); return method.invoke(object, args);
        }
        throw new NoSuchMethodException(name);
    }
    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            T match = find(((ViewGroup)view).getChildAt(i), type);
            if (match != null) return match;
        }
        return null;
    }
    private void screenshot(String name) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new IOException("Screenshot unavailable");
        try (FileOutputStream stream = new FileOutputStream(new File(output, name))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
    }
    private void writeReport() throws Exception {
        try (FileOutputStream stream = new FileOutputStream(new File(output, "android-results.txt"))) {
            stream.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private void fixtures() throws Exception {
        Context context = getTargetContext();
        context.getSharedPreferences("lunaxy_onboarding", 0).edit().putBoolean("first_run_guide_v1_done", true).commit();
        android.content.SharedPreferences.Editor coach = context.getSharedPreferences("lunaxy_context_coach_v1", 0).edit();
        for (Field field : MainActivity.class.getDeclaredFields()) {
            if (field.getName().startsWith("COACH_") && field.getType() == String.class) {
                field.setAccessible(true); coach.putBoolean((String)field.get(null), true);
            }
        }
        coach.commit();
        File wav = new File(context.getFilesDir(), "qa-silence.wav");
        int bytes = 8000 * 2 * 180;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + bytes).put("WAVEfmt ".getBytes()).putInt(16)
                .putShort((short)1).putShort((short)1).putInt(8000).putInt(16000)
                .putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(bytes);
        try (FileOutputStream out = new FileOutputStream(wav)) { out.write(header.array()); out.write(new byte[bytes]); }
        List<Song> songs = new ArrayList<>();
        String[] names = {"Blue Hour", "Quiet Orbit", "Lunar Tide", "Afterglow", "Velvet Sky", "Silver Rain", "Lost in Bloom", "Paper Moon"};
        for (int i = 0; i < 18; i++) {
            Bitmap art = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(art); Paint p = new Paint(3);
            int color = Color.HSVToColor(new float[]{(i * 47 + 10) % 360, .55f, .80f});
            p.setShader(new LinearGradient(0, 0, 400, 400, color, Color.rgb(15, 20, 39), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 400, 400, p); p.setShader(null);
            p.setColor(Color.argb(70,255,255,255)); c.drawCircle(260,150,110,p);
            p.setColor(Color.WHITE); p.setTextSize(27); c.drawText(names[i % 8],28,325,p);
            p.setTextSize(12); c.drawText("LUNAXY  /  MOTION TEST " + (i+1),28,351,p);
            File image = new File(context.getFilesDir(), "qa-cover-" + i + ".png");
            try (FileOutputStream out = new FileOutputStream(image)) { art.compress(Bitmap.CompressFormat.PNG,100,out); }
            Song song = new Song("wy", "qa" + i, names[i % 8] + (i >= 8 ? " " + (i+1) : ""), "Lunaxy Test Ensemble",
                    "Motion Studies", image.toURI().toString().replace("file:/", "file:///"), 180000);
            songs.add(song);
            new OfflineStore(context).putLocalCopy(song, wav, "audio/wav");
        }
        new PlaybackSessionStore(context).save(songs, 2, 18000, 180000, true);
    }
    private void event(View view, int action, long base, long delta, float x, float y) {
        MotionEvent event = MotionEvent.obtain(base,base+delta,action,x,y,0);
        view.dispatchTouchEvent(event); event.recycle();
    }
    private void testGestureOwnership() throws Exception {
        ui(() -> {
            SwipeAwareScrollView scroll = new SwipeAwareScrollView(activity);
            float d = activity.getResources().getDisplayMetrics().density;
            int w = (int)(400*d), h=(int)(800*d);
            scroll.addView(new View(activity),new android.widget.ScrollView.LayoutParams(w,h));
            scroll.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
            scroll.layout(0,0,w,h);
            final int[] calls = new int[3];
            scroll.setSwipeListener(new SwipeAwareScrollView.SwipeListener() {
                public void onSwipeLeft() { }
                public void onSwipeRight() { }
                public void onSwipeProgress(float dx,float fraction) { calls[0]++; }
                public void onSwipeCommit(int direction,float velocity) { calls[1]++; }
                public void onSwipeCancelled() { calls[2]++; }
            });
            long t=SystemClock.uptimeMillis();
            event(scroll,0,t,0,220*d,200*d); event(scroll,2,t,500,190*d,200*d);
            event(scroll,2,t,1700,100*d,200*d); event(scroll,1,t,1800,100*d,200*d);
            check(calls[1]==1,"slow 1.8-second horizontal drag commits");
            event(scroll,0,t+2000,0,4*d,200*d); event(scroll,2,t+2000,100,100*d,200*d); event(scroll,1,t+2000,180,140*d,200*d);
            check(calls[1]==1,"system-back edge does not switch tracks");
            event(scroll,0,t+3000,0,220*d,200*d); event(scroll,2,t+3000,100,218*d,250*d);
            event(scroll,2,t+3000,200,100*d,260*d); event(scroll,1,t+3000,300,80*d,260*d);
            check(calls[1]==1,"vertical gesture cannot turn into a track swipe");
            event(scroll,0,t+4000,0,220*d,200*d); event(scroll,2,t+4000,100,160*d,200*d); event(scroll,3,t+4000,150,160*d,200*d);
            check(calls[2]==1 && calls[1]==1,"ACTION_CANCEL rolls back without a commit");
        });
    }
    private void testStack() throws Exception {
        ui(() -> { invoke(activity,"openNowPlaying"); });
        SystemClock.sleep(1100);
        final VinylRecordView[] incoming = {null};
        ui(() -> {
            VinylStackView stack=(VinylStackView)get(activity,"nowVinylStack");
            VinylRecordView[] records=(VinylRecordView[])get(stack,"records");
            incoming[0]=records[3];
            check(records.length==5,"five retained circular records");
            check(records[0].getAlpha()>.10f && records[4].getAlpha()>.10f,"both far edges visible");
            check(stack.currentRecord()==get(activity,"nowVinyl"),"Hero target is the current view");
            View parent=stack;
            while(parent.getParent() instanceof ViewGroup && parent.getParent()!=get(activity,"playerOverlay")) {
                ViewGroup group=(ViewGroup)parent.getParent();
                check(!group.getClipChildren(),"no internal clip wall: "+group.getClass().getSimpleName()); parent=group;
            }
            invoke(activity,"applyTrackSwipeProgress",-stack.getWidth()*.095f,.5f);
        });
        screenshot("player-half-swipe.png");
        ui(() -> invoke(activity,"commitTrackSwipe",1,0f));
        await(() -> playback.currentQueueIndex()==3 && !(boolean)get(activity,"trackSwipeAnimating"),"swipe commits once and finishes adoption");
        ui(() -> {
            VinylStackView stack=(VinylStackView)get(activity,"nowVinylStack");
            check(stack.currentRecord()==incoming[0],"incoming view survives commit without crossfade rebuild");
            check(stack.currentRecord()==get(activity,"nowVinyl"),"Hero target follows adopted center");
            check(Math.abs(stack.currentRecord().getTranslationX())<.01f,"adopted center aligned");
            playback.pause();
        });
        SystemClock.sleep(400); screenshot("player-stack.png");
    }
    private ListView queueList() throws Exception { return find((View)get(activity,"modalOverlay"),ListView.class); }
    private void dragQueue(boolean current, int direction, long hold) throws Exception {
        final Object[] controller={null}; final float[] y={0}; final int[] from={0}; final Song[] moved={null};
        ui(() -> {
            ListView list=queueList();
            int index=current?playback.currentQueueIndex():Math.max(0,playback.currentQueueIndex()-1);
            // Keep one-slot drag away from the acceleration zone; long-drag test covers edges.
            list.setSelection(Math.max(0,index-(direction<0?2:1))); from[0]=index; moved[0]=playback.queueSnapshot().get(index);
        });
        SystemClock.sleep(150);
        ui(() -> {
            ListView list=queueList(); View source=list.getChildAt(from[0]-list.getFirstVisiblePosition());
            Object holder=source.getTag(); View handle=(View)get(holder,"drag");
            controller[0]=get(list.getAdapter(),"reorderController");
            int[] loc=new int[2]; source.getLocationOnScreen(loc); y[0]=loc[1]+source.getHeight()*.5f;
            invoke(controller[0],"begin",handle,source,from[0],y[0]);
            check((boolean)get(activity,"reorderGestureActive"),"reorder owns gesture");
        });
        long start=SystemClock.uptimeMillis();
        do {
            ui(() -> {
                ListView list=queueList(); float stride=list.getChildAt(0).getHeight()+list.getDividerHeight();
                invoke(controller[0],"update",y[0]+direction*stride*1.4f);
            });
            SystemClock.sleep(80);
        } while(SystemClock.uptimeMillis()-start<hold);
        ui(() -> {
            check((int)get(controller[0],"target")==from[0]+direction,"drag crosses target hysteresis");
            invoke(controller[0],"finish",true);
        });
        await(() -> !(boolean)get(activity,"reorderGestureActive"),"drop finishes after actual layout");
        ui(() -> {
            ListView list=queueList();
            check(playback.queueSnapshot().contains(moved[0]),"drag preserves song");
            check(playback.queueSnapshot().get(from[0]+direction)==moved[0],"queue song actually moves to target");
            for(int i=0;i<list.getChildCount();i++) {
                View row=list.getChildAt(i);
                check(Math.abs(row.getTranslationY())<.01f && row.getAlpha()==1f,"settled row has no stale displacement");
                View content=((ViewGroup)row).getChildAt(0);
                check(content.getAlpha()==1f,"settled row content visible");
                if(list.getFirstVisiblePosition()+i==playback.currentQueueIndex()) {
                    check(content.getBackground() instanceof FluidTrackHaloDrawable,"current-row halo survives rebind");
                    Object state=get(content.getBackground(),"state");
                    check(((PlaybackHighlightState)state).songKey.equals(playback.currentSong().key()),"halo belongs to current song");
                }
            }
        });
    }
    private void testQueue() throws Exception {
        ui(() -> invoke(activity,"showPlaybackQueue")); SystemClock.sleep(500);
        ui(() -> {
            check(queueList() instanceof VelocityListView,"queue supports velocity scrolling");
            // Android auto-hides the thumb on short lists; force it only for this API check.
            check(!queueList().isFastScrollEnabled(),"idle queue does not intercept reorder with a fast thumb");
            queueList().setFastScrollEnabled(true); queueList().setFastScrollAlwaysVisible(true);
            check(queueList().isFastScrollEnabled(),"native fast thumb is available");
            ((VelocityListView)queueList()).suspendFastScroll();
        });
        screenshot("queue-before.png");
        dragQueue(false,1,300); dragQueue(true,1,300); dragQueue(true,-1,300);
        screenshot("queue-after.png");
        ui(() -> invoke(activity,"hideModal")); SystemClock.sleep(400);
    }
    private void testHeroCancellation() throws Exception {
        ui(() -> { invoke(activity,"closeNowPlaying"); invoke(activity,"openNowPlaying"); });
        SystemClock.sleep(70);
        ui(() -> invoke(activity,"closeNowPlaying"));
        SystemClock.sleep(600);
        ui(() -> check(((View)get(activity,"playerOverlay")).getVisibility()!=View.VISIBLE,"Back during Hero stays closed"));
    }
    private void testMiniGeometry() throws Exception {
        ui(() -> { invoke(activity,"openNowPlaying"); }); SystemClock.sleep(600);
        ui(() -> {
            invoke(activity,"closeNowPlaying");
            invoke(activity,"applyMiniFullMorphProgress",.5f);
            View center=(View)get(activity,"nowVinyl");
            float x=center.getTranslationX(), y=center.getTranslationY();
            invoke(activity,"applyMiniFullMorphProgress",.5f);
            check(Math.abs(x-center.getTranslationX())<1f && Math.abs(y-center.getTranslationY())<1f,
                    "same Mini progress produces same geometry, no transformed-coordinate feedback");
            invoke(activity,"closeNowPlaying");
        });
    }
    private void testLongDrag() throws Exception {
        ui(() -> { invoke(activity,"openNowPlaying"); }); SystemClock.sleep(600);
        ui(() -> { playback.resume(); invoke(activity,"showPlaybackQueue"); });
        await(() -> playback.snapshot().playing,"local fixture audio is actually playing");
        SystemClock.sleep(400);
        final Object[] controller={null}; final float[] topBottom=new float[2];
        ui(() -> {
            ListView list=queueList();
            int index=playback.currentQueueIndex();
            View source=list.getChildAt(index-list.getFirstVisiblePosition());
            controller[0]=get(list.getAdapter(),"reorderController");
            View handle=(View)get(source.getTag(),"drag"); int[] loc=new int[2];
            source.getLocationOnScreen(loc);
            invoke(controller[0],"begin",handle,source,index,loc[1]+source.getHeight()*.5f);
            list.getLocationOnScreen(loc);
            topBottom[0]=loc[1]+12; topBottom[1]=loc[1]+list.getHeight()-12;
        });
        long start=SystemClock.uptimeMillis();
        while(SystemClock.uptimeMillis()-start<65000) {
            float y=topBottom[(SystemClock.uptimeMillis()-start)/16000%2==0?1:0];
            ui(() -> invoke(controller[0],"update",y));
            SystemClock.sleep(160);
        }
        ui(() -> invoke(controller[0],"finish",true));
        await(() -> !(boolean)get(activity,"reorderGestureActive"),"65-second edge-scroll drag settles without crash");
        ui(() -> {
            check(playback.queueSnapshot().size()==18,"long drag preserves all queue entries");
            check(playback.snapshot().playing,"long drag does not interrupt playback");
            playback.pause();
            invoke(activity,"hideModal");
        });
        SystemClock.sleep(400);
        writeReport();
    }
    private void testSwipeCancellation() throws Exception {
        final int[] index={0};
        ui(() -> { invoke(activity,"openNowPlaying"); index[0]=playback.currentQueueIndex(); });
        SystemClock.sleep(600);
        ui(() -> {
            VinylStackView stack=(VinylStackView)get(activity,"nowVinylStack");
            invoke(activity,"applyTrackSwipeProgress",-stack.getWidth()*.05f,.25f);
            invoke(activity,"commitTrackSwipe",1,0f);
            invoke(activity,"closeNowPlaying");
        });
        SystemClock.sleep(500);
        ui(() -> check(playback.currentQueueIndex()==index[0],"Back cancels pending swipe without changing song"));
    }
    private void testManualOrder() throws Exception {
        ui(() -> invoke(activity,"showManualSongOrder","QA collection",playback.queueSnapshot(),null));
        SystemClock.sleep(400);
        final Object[] control={null}; final Song[] moved={null};
        ui(() -> {
            ListView list=queueList(); View source=list.getChildAt(0);
            control[0]=get(list.getAdapter(),"reorderController");
            moved[0]=(Song)list.getAdapter().getItem(0);
            int[] loc=new int[2];source.getLocationOnScreen(loc);
            float center=loc[1]+source.getHeight()*.5f;
            invoke(control[0],"begin",get(source.getTag(),"drag"),source,0,center);
            invoke(control[0],"update",center+(source.getHeight()+list.getDividerHeight())*1.4f);
        });
        SystemClock.sleep(250);
        ui(() -> {
            check((int)get(control[0],"target")==1,"manual drag crosses target hysteresis");
            invoke(control[0],"finish",true);
        });
        await(() -> !(boolean)get(activity,"reorderGestureActive"),"manual playlist reorder finishes");
        ui(() -> {
            ListView list=queueList();
            check(list.getAdapter().getItem(1)==moved[0],"manual playlist stores moved song at target");
            for(int i=0;i<list.getChildCount();i++) {
                View row=list.getChildAt(i);
                check(((ViewGroup)row).getChildAt(0).getAlpha()==1f,"manual playlist content remains visible");
            }
            invoke(activity,"hideModal");
        });
        SystemClock.sleep(350);
    }
}
