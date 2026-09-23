package com.xingyu.music.download;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Local-only ringtone clipper. It never touches network/provider code.
 *
 * MP3 is cut on frame boundaries without re-encoding. AAC-in-MP4/M4A is remuxed
 * with MediaExtractor/MediaMuxer. Unsupported containers fail closed and the UI
 * can still offer the full downloaded song.
 */
public final class AudioClipper {
    public static final class ClipResult {
        public final File file;
        public final String mime;
        public final long startMs;
        public final long endMs;
        public final boolean temporary;
        ClipResult(File file, String mime, long startMs, long endMs, boolean temporary) {
            this.file = file; this.mime = mime == null ? "" : mime;
            this.startMs = Math.max(0L, startMs); this.endMs = Math.max(this.startMs, endMs);
            this.temporary = temporary;
        }
        public long durationMs() { return Math.max(0L, endMs - startMs); }
        public void cleanup() { if (temporary && file != null) try { file.delete(); } catch (Exception ignored) { } }
    }

    private AudioClipper() { }

    public static long durationMs(File source) {
        if (source == null || !source.isFile()) return 0L;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(source.getAbsolutePath());
            String raw = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return raw == null ? 0L : Math.max(0L, Long.parseLong(raw));
        } catch (Exception ignored) { return 0L; }
        finally { try { mmr.release(); } catch (Exception ignored) { } }
    }

    public static boolean canClip(OfflineStore.Record record) {
        if (record == null || !record.exists()) return false;
        String p = record.path == null ? "" : record.path.toLowerCase(Locale.ROOT);
        String m = record.mime == null ? "" : record.mime.toLowerCase(Locale.ROOT);
        return p.endsWith(".mp3") || m.contains("mpeg")
                || p.endsWith(".m4a") || p.endsWith(".mp4") || m.contains("mp4") || m.contains("m4a") || m.contains("aac");
    }

    public static ClipResult clip(android.content.Context context, OfflineStore.Record record, long startMs, long endMs) throws Exception {
        if (context == null || record == null || !record.exists()) throw new Exception("离线文件不存在");
        long total = durationMs(record.file());
        if (total <= 0L) total = Math.max(0L, record.song.durationMs);
        long start = Math.max(0L, Math.min(startMs, total > 0 ? total : startMs));
        long end = endMs <= 0L ? total : Math.max(start, Math.min(endMs, total > 0 ? total : endMs));
        if (total > 0 && start <= 350L && end >= total - 350L) {
            return new ClipResult(record.file(), normalizedMime(record.mime, record.path), 0L, total, false);
        }
        if (end - start < 1000L) throw new Exception("截取片段至少需要 1 秒");
        File dir = new File(context.getCacheDir(), "ringtone_clips");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建本地裁剪缓存");
        String lower = record.path == null ? "" : record.path.toLowerCase(Locale.ROOT);
        String mime = record.mime == null ? "" : record.mime.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3") || mime.contains("mpeg")) {
            File out = new File(dir, "clip_" + System.nanoTime() + ".mp3");
            long frameDuration = trimMp3(record.file(), out, start, end);
            verifyFrameDuration(frameDuration, end - start);
            return new ClipResult(out, "audio/mpeg", start, end, true);
        }
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4") || mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac")) {
            File out = new File(dir, "clip_" + System.nanoTime() + ".m4a");
            try {
                trimMp4Audio(record.file(), out, start, end);
                verifyDuration(out, end - start);
                return new ClipResult(out, "audio/mp4", start, end, true);
            } catch (Exception e) {
                try { out.delete(); } catch (Exception ignored) { }
                throw new Exception("当前音频封装暂不支持无损片段裁剪，可选择整首设置", e);
            }
        }
        throw new Exception("当前音频格式暂不支持片段裁剪，可选择整首设置");
    }

    private static void verifyFrameDuration(long actual, long wantedMs) throws Exception {
        if (actual <= 0L) throw new Exception("裁剪后的 MP3 没有有效音频帧");
        // Raw frame slicing intentionally does not rewrite Xing/VBR headers. Some Android metadata
        // readers therefore report the ORIGINAL duration for the clipped file even though playback
        // is correct. Validate the sum of frames we actually wrote instead of trusting stale header
        // metadata; keep the same conservative tolerance used by the container path.
        long tolerance = Math.max(1800L, Math.min(4500L, wantedMs / 8L));
        if (Math.abs(actual - wantedMs) > tolerance) throw new Exception("裁剪后的 MP3 帧时长校验失败");
    }

    private static void verifyDuration(File file, long wantedMs) throws Exception {
        long actual = durationMs(file);
        if (actual <= 0L) throw new Exception("裁剪后的音频无法读取");
        long tolerance = Math.max(1800L, Math.min(4500L, wantedMs / 8L));
        if (Math.abs(actual - wantedMs) > tolerance) throw new Exception("裁剪后的音频时长校验失败");
    }

    private static void trimMp4Audio(File source, File out, long startMs, long endMs) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int audioTrack = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { audioTrack = i; format = f; break; }
            }
            if (audioTrack < 0 || format == null) throw new Exception("没有可裁剪的音频轨");
            extractor.selectTrack(audioTrack);
            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = muxer.addTrack(format);
            muxer.start();
            long startUs = startMs * 1000L, endUs = endMs * 1000L;
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            int max = 512 * 1024;
            try { if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) max = Math.max(max, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)); }
            catch (Exception ignored) { }
            ByteBuffer buffer = ByteBuffer.allocateDirect(max);
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            boolean wrote = false;
            while (true) {
                long sampleTime = extractor.getSampleTime();
                if (sampleTime < 0 || sampleTime >= endUs) break;
                if (sampleTime < startUs) { if (!extractor.advance()) break; continue; }
                buffer.clear();
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.offset = 0; info.size = size; info.presentationTimeUs = Math.max(0L, sampleTime - startUs); info.flags = extractor.getSampleFlags();
                muxer.writeSampleData(outTrack, buffer, info); wrote = true;
                if (!extractor.advance()) break;
            }
            if (!wrote) throw new Exception("所选时间范围没有音频数据");
        } finally {
            try { extractor.release(); } catch (Exception ignored) { }
            if (muxer != null) { try { muxer.stop(); } catch (Exception ignored) { } try { muxer.release(); } catch (Exception ignored) { } }
        }
    }

    private static long trimMp3(File source, File out, long startMs, long endMs) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(source, "r"); BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
            long pos = skipId3v2(raf);
            long length = raf.length();
            double timeMs = 0d;
            double writtenMs = 0d;
            boolean wrote = false;
            byte[] header = new byte[4];
            while (pos + 4 <= length) {
                raf.seek(pos);
                if (raf.read(header) != 4) break;
                Mp3Frame f = parseFrame(header);
                if (f == null || f.length < 4 || pos + f.length > length) { pos++; continue; }
                double frameStart = timeMs;
                double frameEnd = frameStart + f.durationMs;
                if (frameEnd > startMs && frameStart < endMs) {
                    byte[] data = new byte[f.length];
                    raf.seek(pos); raf.readFully(data); bos.write(data); wrote = true; writtenMs += f.durationMs;
                }
                timeMs = frameEnd;
                pos += f.length;
                if (frameStart >= endMs) break;
            }
            bos.flush();
            if (!wrote || out.length() < 1024L) throw new Exception("没有找到可裁剪的 MP3 音频帧");
            return Math.max(1L, Math.round(writtenMs));
        } catch (Exception e) {
            try { out.delete(); } catch (Exception ignored) { }
            throw e;
        }
    }

    private static long skipId3v2(RandomAccessFile raf) throws Exception {
        if (raf.length() < 10) return 0L;
        byte[] h = new byte[10]; raf.seek(0); raf.readFully(h);
        if (h[0] != 'I' || h[1] != 'D' || h[2] != '3') return 0L;
        int size = ((h[6] & 0x7f) << 21) | ((h[7] & 0x7f) << 14) | ((h[8] & 0x7f) << 7) | (h[9] & 0x7f);
        return Math.min(raf.length(), 10L + size);
    }

    private static final class Mp3Frame {
        final int length; final double durationMs;
        Mp3Frame(int length, double durationMs) { this.length = length; this.durationMs = durationMs; }
    }

    private static Mp3Frame parseFrame(byte[] h) {
        int b0 = h[0] & 0xff, b1 = h[1] & 0xff, b2 = h[2] & 0xff;
        if (b0 != 0xff || (b1 & 0xe0) != 0xe0) return null;
        int versionBits = (b1 >> 3) & 0x3;
        int layerBits = (b1 >> 1) & 0x3;
        if (versionBits == 1 || layerBits != 1) return null; // MPEG reserved / not Layer III
        int bitrateIndex = (b2 >> 4) & 0xf;
        int sampleIndex = (b2 >> 2) & 0x3;
        int padding = (b2 >> 1) & 0x1;
        if (bitrateIndex == 0 || bitrateIndex == 15 || sampleIndex == 3) return null;
        int[] br1 = {0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0};
        int[] br2 = {0,8,16,24,32,40,48,56,64,80,96,112,128,144,160,0};
        int bitrate = (versionBits == 3 ? br1 : br2)[bitrateIndex];
        int[] sr = {44100,48000,32000};
        int sampleRate = sr[sampleIndex];
        if (versionBits == 2) sampleRate /= 2; else if (versionBits == 0) sampleRate /= 4;
        if (bitrate <= 0 || sampleRate <= 0) return null;
        boolean mpeg1 = versionBits == 3;
        int frameLength = (mpeg1 ? 144000 : 72000) * bitrate / sampleRate + padding;
        int samples = mpeg1 ? 1152 : 576;
        return new Mp3Frame(frameLength, samples * 1000.0 / sampleRate);
    }

    private static String normalizedMime(String given, String path) {
        String g = given == null ? "" : given.trim(); if (!g.isEmpty()) return g;
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".m4a") || p.endsWith(".mp4")) return "audio/mp4";
        if (p.endsWith(".flac")) return "audio/flac";
        if (p.endsWith(".ogg")) return "audio/ogg";
        if (p.endsWith(".wav")) return "audio/wav";
        return "audio/mpeg";
    }
}
