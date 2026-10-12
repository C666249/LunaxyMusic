package com.xingyu.music.agent;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.xingyu.music.model.Song;
import com.xingyu.music.playback.PlaybackService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Small playback adapter owned by the Agent layer.
 *
 * It reuses the existing PlaybackService instead of creating a second player. Commands issued
 * before the service is bound are queued and replayed when the existing service connection arrives.
 */
public final class AgentPlaybackBridge implements AutoCloseable {
    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Action> pending = new ArrayDeque<>();
    private volatile PlaybackService playback;
    private boolean bound;
    private boolean binding;
    private boolean closed;

    private interface Action {
        void run(PlaybackService service);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            PlaybackService service = ((PlaybackService.LocalBinder) binder).getService();
            List<Action> waiting = new ArrayList<>();
            synchronized (AgentPlaybackBridge.this) {
                playback = service;
                bound = true;
                binding = false;
                while (!pending.isEmpty()) waiting.add(pending.removeFirst());
            }
            for (Action action : waiting) {
                try { action.run(service); } catch (Exception ignored) { }
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (AgentPlaybackBridge.this) {
                playback = null;
                bound = false;
                binding = false;
            }
        }
    };

    public AgentPlaybackBridge(Context context) {
        appContext = context.getApplicationContext();
        ensureBound();
    }

    public void playSong(Song song) {
        if (song == null) return;
        withService(service -> service.insertAndPlay(song));
    }

    public void playQueue(List<Song> songs, int startIndex) {
        if (songs == null || songs.isEmpty()) return;
        List<Song> copy = new ArrayList<>(songs);
        int safeIndex = Math.max(0, Math.min(startIndex, copy.size() - 1));
        withService(service -> service.playQueue(copy, safeIndex));
    }

    public void pause() { withService(PlaybackService::pause); }
    public void resume() { withService(PlaybackService::resume); }
    public void next() { withService(PlaybackService::next); }
    public void previous() { withService(PlaybackService::previous); }

    public Song currentSong() {
        PlaybackService service = playback;
        return service == null ? null : service.currentSong();
    }

    public List<Song> queueSnapshot() {
        PlaybackService service = playback;
        return service == null ? new ArrayList<>() : service.queueSnapshot();
    }

    public boolean isReady() {
        return playback != null;
    }

    private void withService(Action action) {
        if (action == null || closed) return;
        main.post(() -> {
            PlaybackService service = playback;
            if (service != null) {
                action.run(service);
                return;
            }
            synchronized (AgentPlaybackBridge.this) {
                if (closed) return;
                pending.addLast(action);
                while (pending.size() > 24) pending.removeFirst();
            }
            ensureBound();
        });
    }

    private void ensureBound() {
        if (closed) return;
        main.post(() -> {
            synchronized (AgentPlaybackBridge.this) {
                if (closed || bound || binding) return;
                binding = true;
            }
            Intent intent = new Intent(appContext, PlaybackService.class);
            try {
                if (Build.VERSION.SDK_INT >= 26) appContext.startForegroundService(intent);
                else appContext.startService(intent);
            } catch (Exception ignored) { }
            boolean ok = false;
            try {
                ok = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            } catch (Exception ignored) { }
            if (!ok) {
                synchronized (AgentPlaybackBridge.this) { binding = false; }
            }
        });
    }

    @Override public void close() {
        closed = true;
        main.post(() -> {
            try {
                if (bound || binding) appContext.unbindService(connection);
            } catch (Exception ignored) { }
            synchronized (AgentPlaybackBridge.this) {
                playback = null;
                bound = false;
                binding = false;
                pending.clear();
            }
        });
    }
}
