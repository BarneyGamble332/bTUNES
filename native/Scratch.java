package de.bernhard.musik;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/* Scratchen wie am echten Plattenspieler:
   Ein paar Sekunden rund um die aktuelle Stelle werden in rohe Tondaten entpackt.
   Ein eigener Tonausgang spielt sie dann mit der Geschwindigkeit und Richtung ab,
   mit der der Finger die Platte dreht – auch rückwärts. */
class Scratch {
    private short[] pcm;          // Mono, 16 Bit
    private int rate;             // Abtastrate
    private long startUs;         // Zeit des ersten Samples im Lied
    private volatile double target;   // Ziel-Position (Samples), kommt vom Finger
    private volatile double head;     // aktuelle Abspielposition (Samples)
    private volatile boolean running;
    private Thread thread;
    private AudioTrack track;

    long windowStartMs() { return startUs / 1000; }
    long windowEndMs() { return startUs / 1000 + (pcm == null ? 0 : pcm.length * 1000L / rate); }

    /* Abschnitt [posMs - before, posMs + after] entpacken */
    void load(Context ctx, Uri uri, long posMs, long beforeMs, long afterMs) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(ctx, uri, null);
            int ti = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) { ti = i; fmt = f; break; }
            }
            if (ti < 0) throw new Exception("Keine Tonspur");
            ex.selectTrack(ti);
            long fromUs = Math.max(0, posMs - beforeMs) * 1000L;
            long toUs = (posMs + afterMs) * 1000L;
            ex.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();

            int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            short[] out = new short[(int) ((toUs - fromUs) / 1000000.0 * rate) + rate];
            int n = 0;
            long firstUs = -1;
            boolean inEnd = false, outEnd = false;
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            long guard = System.currentTimeMillis();
            while (!outEnd && System.currentTimeMillis() - guard < 4000) {
                if (!inEnd) {
                    int ii = codec.dequeueInputBuffer(5000);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        int sz = ex.readSampleData(ib, 0);
                        if (sz < 0 || ex.getSampleTime() > toUs + 200000) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inEnd = true;
                        } else {
                            codec.queueInputBuffer(ii, 0, sz, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = codec.dequeueOutputBuffer(bi, 5000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (out.length < (int) ((toUs - fromUs) / 1000000.0 * rate) + rate)
                        out = java.util.Arrays.copyOf(out, (int) ((toUs - fromUs) / 1000000.0 * rate) + rate);
                } else if (oi >= 0) {
                    if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEnd = true;
                    if (bi.size > 0 && bi.presentationTimeUs <= toUs) {
                        ByteBuffer ob = codec.getOutputBuffer(oi);
                        ob.position(bi.offset);
                        ob.limit(bi.offset + bi.size);
                        ShortBuffer sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer();
                        int frames = sb.remaining() / channels;
                        // Anfang: Samples vor dem Fenster überspringen
                        int skip = 0;
                        if (bi.presentationTimeUs < fromUs) {
                            skip = (int) ((fromUs - bi.presentationTimeUs) / 1000000.0 * rate);
                            if (skip >= frames) { codec.releaseOutputBuffer(oi, false); continue; }
                        }
                        if (firstUs < 0) firstUs = bi.presentationTimeUs + (long) (skip * 1000000.0 / rate);
                        for (int f = 0; f < frames; f++) {
                            int sum = 0;
                            for (int c = 0; c < channels; c++) sum += sb.get();
                            if (f < skip) continue;
                            if (n < out.length) out[n++] = (short) (sum / channels);
                        }
                    } else if (bi.presentationTimeUs > toUs) {
                        outEnd = true;
                    }
                    codec.releaseOutputBuffer(oi, false);
                }
            }
            if (n == 0) throw new Exception("Nichts entpackt");
            pcm = java.util.Arrays.copyOf(out, n);
            startUs = firstUs < 0 ? fromUs : firstUs;
        } finally {
            try { if (codec != null) { codec.stop(); codec.release(); } } catch (Exception ignore) {}
            ex.release();
        }
    }

    void start(long posMs) {
        head = target = msToSample(posMs);
        int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack.Builder b = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(Math.max(min, 2048))
            .setTransferMode(AudioTrack.MODE_STREAM);
        if (android.os.Build.VERSION.SDK_INT >= 26) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
        track = b.build();
        track.play();
        running = true;
        thread = new Thread(this::loop, "scratch");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    void move(long posMs) { target = msToSample(posMs); }

    long stop() {
        running = false;
        try { if (thread != null) thread.join(300); } catch (InterruptedException ignore) {}
        try { if (track != null) { track.pause(); track.flush(); track.release(); } } catch (Exception ignore) {}
        track = null;
        return sampleToMs(head);
    }

    private double msToSample(long ms) { return (ms - startUs / 1000.0) * rate / 1000.0; }
    private long sampleToMs(double s) { return Math.round(startUs / 1000.0 + s * 1000.0 / rate); }

    /* Der Kopf läuft der Fingerposition weich hinterher: schnelles Drehen = schneller (und höher),
       Finger still = Stille, rückwärts drehen = rückwärts */
    private void loop() {
        final int block = 192;
        short[] buf = new short[block];
        double vel = 0;
        double h = head;
        double fade = 0;
        final double follow = rate * 0.045;
        while (running) {
            double desired = (target - h) / follow;
            if (desired > 6) desired = 6;
            if (desired < -6) desired = -6;
            double nv = vel + (desired - vel) * 0.3;
            for (int i = 0; i < block; i++) {
                double v = vel + (nv - vel) * i / block;
                h += v;
                int k = (int) Math.floor(h);
                double fr = h - k;
                double s = 0;
                if (k >= 0 && k + 1 < pcm.length) s = pcm[k] * (1 - fr) + pcm[k + 1] * fr;
                // langsam = leiser, so knackt es beim Anhalten nicht
                double g = Math.min(1.0, Math.abs(v) * 1.5);
                fade += (g - fade) * 0.01;
                buf[i] = (short) Math.max(-32768, Math.min(32767, s * fade));
            }
            vel = nv;
            head = h;
            track.write(buf, 0, block);
        }
    }
}
