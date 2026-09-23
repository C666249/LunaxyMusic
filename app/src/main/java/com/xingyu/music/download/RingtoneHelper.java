package com.xingyu.music.download;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Android 10+ export of an app-owned offline file to MediaStore, then set/verify a system tone. */
public final class RingtoneHelper {
    public static final class SetResult {
        public final Uri uri;
        public final boolean verified;
        public final String title;
        SetResult(Uri uri, boolean verified, String title) { this.uri = uri; this.verified = verified; this.title = title == null ? "" : title; }
    }

    private RingtoneHelper() { }
    public static boolean canWriteSystemSettings(Context c){return c!=null&&Settings.System.canWrite(c);}
    public static int typeFromChoice(int choice){return choice==1?RingtoneManager.TYPE_NOTIFICATION:choice==2?RingtoneManager.TYPE_ALARM:RingtoneManager.TYPE_RINGTONE;}
    public static String typeLabel(int type){return type==RingtoneManager.TYPE_NOTIFICATION?"通知音":type==RingtoneManager.TYPE_ALARM?"闹钟音":"来电铃声";}

    public static Uri exportAndSet(Context context, OfflineStore.Record record, int type) throws Exception {
        return exportAndSetVerified(context, record, type, record == null ? null : record.file(), record == null ? "" : record.mime, 0L,
                record == null || record.song == null ? 0L : record.song.durationMs).uri;
    }

    public static SetResult exportAndSetVerified(Context context, OfflineStore.Record record, int type,
                                                 File source, String sourceMime, long startMs, long endMs) throws Exception {
        if(context==null||record==null||!record.exists()||source==null||!source.isFile())throw new Exception("离线文件不存在");
        if(Build.VERSION.SDK_INT<29)throw new Exception("实验版铃声设置暂支持 Android 10 及以上");
        if(!Settings.System.canWrite(context))throw new SecurityException("需要允许修改系统设置");
        boolean clipped = startMs > 350L || (endMs > 0L && record.song.durationMs > 0L && endMs < record.song.durationMs - 350L);
        String ext=extension(source.getAbsolutePath(),sourceMime), base=safeName(record.song.title+" - "+record.song.artist + (clipped ? " · " + clock(startMs) + "-" + clock(endMs) : ""));
        ContentValues v=new ContentValues();
        v.put(MediaStore.Audio.Media.DISPLAY_NAME,base+ext); v.put(MediaStore.Audio.Media.TITLE,base);
        v.put(MediaStore.Audio.Media.ARTIST,record.song.artist); v.put(MediaStore.Audio.Media.MIME_TYPE,mime(sourceMime,ext));
        v.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_RINGTONES+"/Lunaxy Music");
        v.put(MediaStore.Audio.Media.IS_RINGTONE,1); v.put(MediaStore.Audio.Media.IS_NOTIFICATION,1); v.put(MediaStore.Audio.Media.IS_ALARM,1); v.put(MediaStore.Audio.Media.IS_PENDING,1);
        ContentResolver cr=context.getContentResolver(); Uri uri=cr.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v);
        if(uri==null)throw new Exception("系统媒体库无法创建铃声");
        try(FileInputStream in=new FileInputStream(source); OutputStream out=cr.openOutputStream(uri,"w")){
            if(out==null)throw new Exception("无法写入系统媒体库"); byte[] buf=new byte[64*1024]; int n; while((n=in.read(buf))>=0){if(n>0)out.write(buf,0,n);} out.flush();
        }catch(Exception e){try{cr.delete(uri,null,null);}catch(Exception ignored){}throw e;}
        ContentValues done=new ContentValues();done.put(MediaStore.Audio.Media.IS_PENDING,0);cr.update(uri,done,null,null);
        RingtoneManager.setActualDefaultRingtoneUri(context,type,uri);
        Uri actual = RingtoneManager.getActualDefaultRingtoneUri(context,type);
        boolean verified = sameUri(uri, actual);
        if (!verified) throw new Exception("系统没有确认新的" + typeLabel(type) + "，请到系统声音设置检查");
        return new SetResult(uri, true, currentToneTitle(context, type));
    }

    public static Uri currentToneUri(Context context, int type) {
        try { return context == null ? null : RingtoneManager.getActualDefaultRingtoneUri(context, type); }
        catch (Exception ignored) { return null; }
    }

    public static String currentToneTitle(Context context, int type) {
        if (context == null) return "未设置";
        Uri uri = currentToneUri(context, type);
        if (uri == null) return "静音 / 未设置";
        try {
            Ringtone r = RingtoneManager.getRingtone(context, uri);
            if (r != null) {
                String title = r.getTitle(context);
                if (title != null && !title.trim().isEmpty()) return title.trim();
            }
        } catch (Exception ignored) { }
        try (Cursor c = context.getContentResolver().query(uri, new String[]{MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String title = c.getString(0); if (title != null && !title.trim().isEmpty()) return title.trim();
                String name = c.getString(1); if (name != null && !name.trim().isEmpty()) return name.trim();
            }
        } catch (Exception ignored) { }
        return "系统声音";
    }

    public static boolean isCurrent(Context context, int type, Uri uri) {
        return uri != null && sameUri(uri, currentToneUri(context, type));
    }

    private static boolean sameUri(Uri a, Uri b) {
        if (a == null || b == null) return false;
        return a.equals(b) || a.toString().equals(b.toString());
    }
    private static String clock(long ms){long total=Math.max(0L,ms)/1000L;return String.format(Locale.ROOT,"%02d_%02d",total/60L,total%60L);}
    private static String extension(String path,String mime){String l=path==null?"":path.toLowerCase(java.util.Locale.ROOT);if(l.endsWith(".flac"))return ".flac";if(l.endsWith(".m4a")||l.endsWith(".mp4"))return ".m4a";if(l.endsWith(".ogg"))return ".ogg";if(l.endsWith(".wav"))return ".wav";if(mime!=null&&mime.contains("flac"))return ".flac";if(mime!=null&&(mime.contains("mp4")||mime.contains("m4a")||mime.contains("aac")))return ".m4a";return ".mp3";}
    private static String mime(String given,String ext){if(given!=null&&!given.trim().isEmpty())return given;if(".flac".equals(ext))return "audio/flac";if(".m4a".equals(ext))return "audio/mp4";if(".ogg".equals(ext))return "audio/ogg";if(".wav".equals(ext))return "audio/wav";return "audio/mpeg";}
    private static String safeName(String s){String x=s==null?"Lunaxy":s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();return x.length()>96?x.substring(0,96):x;}
}
