package campus.skillswap.ui.media;

import javax.sound.sampled.*;
import java.util.function.Consumer;

/** Microphone capture + speaker playback for calls (plain Java Sound, nothing to install). 16 kHz, mono, 16-bit, 20 ms chunks. */
public class Audio {
    public static final AudioFormat FMT = new AudioFormat(16000f, 16, 1, true, false);
    static final int CHUNK = 640;                       // 20 ms
    TargetDataLine mic; SourceDataLine spk; volatile boolean run; Thread cap;

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
    public void stop() {
        run = false;
        try { if (mic != null) { mic.stop(); mic.close(); } } catch (Exception e) { }
        try { if (spk != null) { spk.stop(); spk.close(); } } catch (Exception e) { }
        mic = null; spk = null;
    }
}
