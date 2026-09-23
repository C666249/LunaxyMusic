package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight artwork loader with a two-level cache.
 *
 * V85 keeps the existing 24 MB in-memory LRU and adds an app-cache disk layer for remote artwork.
 * The disk cache stores image bytes only; it never stores or interacts with resolved audio URLs.
 */
public final class ImageLoader {
    public interface Callback { void onLoaded(Bitmap bitmap, int accentColor); }

    private static final long DISK_MAX_BYTES = 160L * 1024L * 1024L;
    private static final long NETWORK_MAX_BYTES = 24L * 1024L * 1024L;
    private static final String DISK_DIR = "lunaxy_cover_cache_v85";

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, Object> URL_LOCKS = new ConcurrentHashMap<>();
    private static final AtomicInteger DISK_WRITES = new AtomicInteger();

    private ImageLoader() {}

    public static void load(String url, ImageView target, Callback cb) {
        if (url == null || url.trim().isEmpty()) { target.setImageDrawable(null); return; }
        final String key = url.trim();
        target.setTag(key);

        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            target.setImageBitmap(hit);
            if (cb != null) cb.onLoaded(hit, accent(hit));
            return;
        }

        final Context app = target.getContext().getApplicationContext();
        EXEC.submit(() -> {
            // Recycler/ListView rows can be rebound many times during a long reorder. If this
            // request became stale while waiting in the executor queue, abandon it before disk
            // decode or network work. This prevents a long edge-scroll drag from building a large
            // backlog of artwork jobs that can pressure memory and the main thread.
            if (!key.equals(target.getTag())) return;
            Bitmap loaded;
            if (isLocal(key)) {
                loaded = loadLocal(target, key);
                if (loaded != null) CACHE.put(key, loaded);
            } else {
                Object perUrlLock = URL_LOCKS.computeIfAbsent(key, ignored -> new Object());
                try {
                    synchronized (perUrlLock) {
                        // Another ImageView may have completed while this request was waiting.
                        if (!key.equals(target.getTag())) return;
                        loaded = CACHE.get(key);
                        if (loaded == null) loaded = readDisk(app, key);
                        if (loaded == null && key.equals(target.getTag())) loaded = downloadToDisk(app, key);
                        if (loaded != null) CACHE.put(key, loaded);
                    }
                } finally {
                    URL_LOCKS.remove(key, perUrlLock);
                }
            }

            final Bitmap out = loaded;
            MAIN.post(() -> {
                if (!key.equals(target.getTag())) return;
                if (out != null) {
                    target.setImageBitmap(out);
                    if (cb != null) cb.onLoaded(out, accent(out));
                }
            });
        });
    }

    private static boolean isLocal(String value) {
        return value.startsWith("content://") || value.startsWith("file://");
    }

    private static Bitmap loadLocal(ImageView target, String value) {
        try (InputStream in = target.getContext().getContentResolver().openInputStream(Uri.parse(value))) {
            return in == null ? null : BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap readDisk(Context context, String url) {
        File file = diskFile(context, url);
        if (!file.isFile() || file.length() <= 0L) return null;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) {
                // Corrupt/partial artwork must never poison later loads.
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                return null;
            }
            // lastModified is the LRU access clock; a disk hit therefore survives longer than cold art.
            //noinspection ResultOfMethodCallIgnored
            file.setLastModified(System.currentTimeMillis());
            return bitmap;
        } catch (Exception ignored) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        }
    }

    private static Bitmap downloadToDisk(Context context, String url) {
        HttpURLConnection connection = null;
        File temp = null;
        try {
            File dir = diskDir(context);
            File dest = diskFile(context, url);
            temp = new File(dir, dest.getName() + ".tmp-" + Thread.currentThread().getId());
            //noinspection ResultOfMethodCallIgnored
            temp.delete();

            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(16000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) SunflowerMusic/1.0");
            connection.setDoInput(true);
            connection.connect();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 400) return null;
            long declared = connection.getContentLengthLong();
            if (declared > NETWORK_MAX_BYTES) return null;

            long total = 0L;
            byte[] buffer = new byte[32 * 1024];
            try (InputStream raw = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                int n;
                while ((n = raw.read(buffer)) >= 0) {
                    if (n == 0) continue;
                    total += n;
                    if (total > NETWORK_MAX_BYTES) throw new IllegalStateException("artwork too large");
                    out.write(buffer, 0, n);
                }
                out.flush();
            }
            if (total <= 0L) return null;

            Bitmap bitmap = BitmapFactory.decodeFile(temp.getAbsolutePath());
            if (bitmap == null) return null;

            if (dest.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dest.delete();
            }
            boolean moved = temp.renameTo(dest);
            if (!moved) copyFile(temp, dest);
            //noinspection ResultOfMethodCallIgnored
            dest.setLastModified(System.currentTimeMillis());

            int writes = DISK_WRITES.incrementAndGet();
            if (writes == 1 || writes % 12 == 0) trimDiskCache(dir);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
            if (temp != null && temp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    private static void copyFile(File source, File dest) throws Exception {
        byte[] buffer = new byte[32 * 1024];
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n > 0) out.write(buffer, 0, n);
            }
            out.flush();
        }
    }

    private static File diskDir(Context context) {
        File dir = new File(context.getCacheDir(), DISK_DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private static File diskFile(Context context, String url) {
        return new File(diskDir(context), sha256(url) + ".img");
    }

    private static void trimDiskCache(File dir) {
        try {
            File[] files = dir.listFiles(pathname -> pathname.isFile() && pathname.getName().endsWith(".img"));
            if (files == null || files.length == 0) return;
            long total = 0L;
            for (File file : files) total += Math.max(0L, file.length());
            if (total <= DISK_MAX_BYTES) return;

            Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            long target = Math.round(DISK_MAX_BYTES * 0.88d);
            for (File file : files) {
                if (total <= target) break;
                long size = Math.max(0L, file.length());
                if (file.delete()) total -= size;
            }
        } catch (Exception ignored) { }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static int accent(Bitmap b) {
        if (b == null || b.getWidth() == 0 || b.getHeight() == 0) return Color.rgb(185,166,255);
        long r=0,g=0,bl=0,n=0; int sx=Math.max(1,b.getWidth()/24), sy=Math.max(1,b.getHeight()/24);
        for(int y=0;y<b.getHeight();y+=sy) for(int x=0;x<b.getWidth();x+=sx){ int c=b.getPixel(x,y); float[] hsv=new float[3]; Color.colorToHSV(c,hsv); if(hsv[2]<0.15f)continue; float w=0.45f+hsv[1]; r+=(long)(Color.red(c)*w);g+=(long)(Color.green(c)*w);bl+=(long)(Color.blue(c)*w);n+=(long)(100*w); }
        if(n<=0)return Color.rgb(185,166,255);
        int rr=(int)Math.min(255,r*100/n), gg=(int)Math.min(255,g*100/n), bb=(int)Math.min(255,bl*100/n);
        float[] hsv=new float[3];Color.RGBToHSV(rr,gg,bb,hsv);hsv[1]=Math.max(.35f,Math.min(.72f,hsv[1]));hsv[2]=Math.max(.72f,Math.min(.98f,hsv[2]));return Color.HSVToColor(hsv);
    }
}
