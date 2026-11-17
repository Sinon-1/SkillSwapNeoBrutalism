package campus.skillswap.ui.media;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Microphone capture + speaker playback for calls (plain Java Sound, nothing to install). 16 kHz, mono, 16-bit, 20 ms chunks. */
public class Audio {
    public static final AudioFormat FMT = new AudioFormat(16000f, 16, 1, true, false);
    static final int CHUNK = 640;                       // 20 ms
    TargetDataLine mic; SourceDataLine spk; volatile boolean run; Thread cap, mixer;
    final Map<String, ArrayDeque<byte[]>> mixQ = new ConcurrentHashMap<>();   // group calls: one queue per speaker

    /** Starts capture and playback. Returns a warning for the user if the microphone or speaker is missing, else null. */
    public String start(Consumer<byte[]> onChunk) {
        run = true; String warn = null;
        try { spk = AudioSystem.getSourceDataLine(FMT); spk.open(FMT, CHUNK * 12); spk.start(); }
        catch (Exception e) { spk = null; warn = "No speaker available - you will not hear the other person."; }
        try {
            mic = AudioSystem.getTargetDataLine(FMT); mic.open(FMT, CHUNK * 10); mic.start();
            cap = new Thread(() -> {
                byte[] buf = new byte[CHUNK];
                while (run) { int n = mic.read(buf, 0, CHUNK); if (n <= 0) continue; byte[] c = new byte[n]; System.arraycopy(buf, 0, c, 0, n); onChunk.accept(c); }
            }, "call-mic"); cap.setDaemon(true); cap.start();
        } catch (Exception e) { mic = null; warn = (warn == null ? "" : warn + " ") + "No microphone available - the other person will not hear you."; }
        return warn;
    }
    /** Plays one received chunk; if the buffer is already full the chunk is dropped so delay never builds up. */
    public void play(byte[] b) { SourceDataLine l = spk; if (l != null && l.available() >= b.length) l.write(b, 0, b.length); }

    // ---------------- group calls: several people talk at once ----------------
    /** Queues a chunk from one speaker; the mixer adds all speakers together (a single line cannot be fed by many threads). */
    public void play(String from, byte[] b) {
        if (spk == null) return;
        ArrayDeque<byte[]> q = mixQ.computeIfAbsent(from, k -> new ArrayDeque<>());
        synchronized (q) { q.addLast(b); while (q.size() > 8) q.removeFirst(); }       // never let delay build up
        startMixer();
    }
    synchronized void startMixer() {
        if (mixer != null || !run) return;
        mixer = new Thread(this::mixLoop, "call-mix"); mixer.setDaemon(true); mixer.start();
    }
    /** Keeps about 40-60 ms queued in the speaker line and always writes exactly one 20 ms block per turn, so speakers stay aligned. */
    void mixLoop() {
        int[] acc = new int[CHUNK / 2]; byte[] out = new byte[CHUNK]; Set<String> primed = new HashSet<>();
        while (run) {
            SourceDataLine l = spk; if (l == null) return;
            if (l.getBufferSize() - l.available() >= CHUNK * 3) { try { Thread.sleep(4); } catch (InterruptedException e) { return; } continue; }
            Arrays.fill(acc, 0);
            for (Map.Entry<String, ArrayDeque<byte[]>> en : mixQ.entrySet()) {
                byte[] c = null; ArrayDeque<byte[]> q = en.getValue();
                synchronized (q) {
                    if (!primed.contains(en.getKey())) { if (q.size() >= 2) primed.add(en.getKey()); }        // small jitter buffer before a speaker starts
                    if (primed.contains(en.getKey())) { c = q.pollFirst(); if (c == null) primed.remove(en.getKey()); }
                }
                if (c == null) continue;
                for (int i = 0; i + 1 < c.length && i / 2 < acc.length; i += 2) acc[i / 2] += (short) ((c[i] & 0xFF) | (c[i + 1] << 8));
            }
            for (int i = 0; i < acc.length; i++) { int v = Math.max(-32768, Math.min(32767, acc[i])); out[2 * i] = (byte) v; out[2 * i + 1] = (byte) (v >> 8); }
            l.write(out, 0, CHUNK);
        }
    }
    /** Loudness of a 16-bit little-endian chunk (0 - 32767); used for the "speaking" highlight. */
    public static int rms(byte[] pcm) {
        if (pcm == null || pcm.length < 2) return 0; long sum = 0; int n = pcm.length / 2;
        for (int i = 0; i + 1 < pcm.length; i += 2) { int v = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8)); sum += (long) v * v; }
        return (int) Math.sqrt(sum / (double) n);
    }

    public void stop() {
        run = false;
        try { if (mic != null) { mic.stop(); mic.close(); } } catch (Exception e) { }
        try { if (spk != null) { spk.stop(); spk.close(); } } catch (Exception e) { }
        mic = null; spk = null; mixer = null; mixQ.clear();
    }
}
