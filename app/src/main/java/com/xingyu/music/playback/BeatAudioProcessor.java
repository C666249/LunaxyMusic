package com.xingyu.music.playback;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Pass-through PCM observer used only for Xingyu Music's local visualizer.
 *
 * It never changes the audible stream. While Media3 is decoding PCM, the processor
 * samples a light-weight mono representation and maintains four smoothed values:
 * overall energy, low-frequency energy, high-frequency energy and an onset/beat pulse.
 * This avoids microphone/RECORD_AUDIO permission and keeps the visualizer synchronized
 * with the exact audio that ExoPlayer is already playing.
 */
@UnstableApi
public final class BeatAudioProcessor extends BaseAudioProcessor {
    private volatile float energy;
    private volatile float bass;
    private volatile float treble;
    private volatile float beat;

    private float lowPassState;
    private float slowEnvelope = 0.06f;
    private float fastEnvelope = 0.06f;
    private long lastBeatNs;

    @Override
    protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat inputAudioFormat)
            throws AudioProcessor.UnhandledAudioFormatException {
        // Audio processors are invoked only on decoded PCM. Keep the exact format so the
        // stream is a true pass-through and playback quality/position are untouched.
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining <= 0) return;

        ByteBuffer analysis = inputBuffer.duplicate().order(ByteOrder.nativeOrder());
        analyze(analysis);

        ByteBuffer output = replaceOutputBuffer(remaining);
        output.put(inputBuffer); // consumes the caller's buffer as required by AudioProcessor
        output.flip();
    }

    private void analyze(ByteBuffer buffer) {
        AudioProcessor.AudioFormat fmt = inputAudioFormat;
        if (fmt == null || fmt == AudioProcessor.AudioFormat.NOT_SET || fmt.channelCount <= 0) return;

        final int encoding = fmt.encoding;
        final int channels = Math.max(1, fmt.channelCount);
        final int sampleRate = Math.max(8000, fmt.sampleRate);
        final int frameBytes = Math.max(1, fmt.bytesPerFrame);
        final int totalFrames = buffer.remaining() / frameBytes;
        if (totalFrames <= 0) return;

        // Roughly <= 4k mono samples/second is sufficient for a responsive visual envelope.
        final int stride = Math.max(1, sampleRate / 4000);
        double sumSq = 0d;
        double bassSq = 0d;
        double highSq = 0d;
        int samples = 0;

        // One-pole low-pass around ~180 Hz. We are not trying to perform studio FFT analysis;
        // this is intentionally tiny and stable enough to make the cover pulse on kicks/bass.
        final float cutoff = 180f;
        final float dt = 1f / sampleRate;
        final float rc = 1f / (2f * (float) Math.PI * cutoff);
        final float alpha = dt / (rc + dt);

        int base = buffer.position();
        int limit = buffer.limit();
        for (int frame = 0; frame < totalFrames; frame += stride) {
            int pos = base + frame * frameBytes;
            if (pos >= limit) break;
            float mono = readMono(buffer, pos, encoding, channels, frameBytes);
            if (!Float.isFinite(mono)) continue;
            mono = Math.max(-1f, Math.min(1f, mono));

            // Account for skipped frames in the low-pass time constant.
            float a = 1f - (float) Math.pow(1f - alpha, stride);
            lowPassState += a * (mono - lowPassState);
            float high = mono - lowPassState;

            sumSq += mono * mono;
            bassSq += lowPassState * lowPassState;
            highSq += high * high;
            samples++;
        }
        if (samples == 0) return;

        float rms = (float) Math.sqrt(sumSq / samples);
        float lowRms = (float) Math.sqrt(bassSq / samples);
        float highRms = (float) Math.sqrt(highSq / samples);

        // Musical-looking normalization: ordinary mastered music tends to live in this band.
        float normalized = squash(rms * 4.5f);
        float lowNorm = squash(lowRms * 6.2f);
        float highNorm = squash(highRms * 5.0f);

        fastEnvelope += (normalized - fastEnvelope) * (normalized > fastEnvelope ? .44f : .17f);
        slowEnvelope += (normalized - slowEnvelope) * .035f;

        float onset = Math.max(0f, fastEnvelope - slowEnvelope * 1.13f);
        float candidate = Math.min(1f, onset * 4.8f + Math.max(0f, lowNorm - bass) * 1.8f);
        long now = System.nanoTime();
        boolean refractoryOk = now - lastBeatNs > 115_000_000L;
        float nextBeat;
        if (candidate > .23f && refractoryOk) {
            nextBeat = Math.min(1f, .38f + candidate);
            lastBeatNs = now;
        } else {
            nextBeat = beat * .78f;
        }

        energy = smooth(energy, normalized, normalized > energy ? .52f : .18f);
        bass = smooth(bass, lowNorm, lowNorm > bass ? .50f : .16f);
        treble = smooth(treble, highNorm, highNorm > treble ? .42f : .15f);
        beat = Math.max(nextBeat, candidate * .55f);
    }

    private static float readMono(ByteBuffer b, int framePos, int encoding, int channels, int frameBytes) {
        try {
            if (encoding == C.ENCODING_PCM_16BIT) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) sum += b.getShort(framePos + c * 2) / 32768f;
                return sum / channels;
            }
            if (encoding == C.ENCODING_PCM_FLOAT) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) sum += b.getFloat(framePos + c * 4);
                return sum / channels;
            }
            if (encoding == C.ENCODING_PCM_8BIT) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) sum += ((b.get(framePos + c) & 0xff) - 128) / 128f;
                return sum / channels;
            }
            if (encoding == C.ENCODING_PCM_32BIT) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) sum += b.getInt(framePos + c * 4) / 2147483648f;
                return sum / channels;
            }
            if (encoding == C.ENCODING_PCM_24BIT) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) {
                    int p = framePos + c * 3;
                    int v = (b.get(p) & 0xff) | ((b.get(p + 1) & 0xff) << 8) | (b.get(p + 2) << 16);
                    sum += v / 8388608f;
                }
                return sum / channels;
            }
        } catch (Exception ignored) { }
        // Unknown PCM representation: do not guess from arbitrary bytes.
        return 0f;
    }

    private static float squash(float v) {
        v = Math.max(0f, v);
        return v / (1f + v);
    }

    private static float smooth(float old, float next, float factor) {
        return old + (next - old) * factor;
    }

    public float energy() { return energy; }
    public float bass() { return bass; }
    public float treble() { return treble; }
    public float beat() { return beat; }

    public void resetVisuals() {
        energy = bass = treble = beat = 0f;
        lowPassState = 0f;
        fastEnvelope = slowEnvelope = .06f;
        lastBeatNs = 0L;
    }
}
