package campus.skillswap.ui.media;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static campus.skillswap.config.AppConfig.*;

/**
 * Notification sounds. Choices live in ~/.skillswap/config.properties:
 * "default" (built-in sound), "off", or the absolute path of a custom audio file.
 * General notifications and calls have separate sounds.
 */
public final class Sound {

    public static final String DEFAULT = "default", OFF = "off";
    static Clip current, callLoop;
    static long ringGeneration;

    public enum Kind {
        GENERAL("notify.sound", "Default chime", "my-notification.wav"),
        CALL("notify.call.sound", "Default ringtone", "my-call.wav");
        final String key, defaultName, recordName;
        Kind(String key, String defaultName, String recordName) { this.key = key; this.defaultName = defaultName; this.recordName = recordName; }
        public String defaultName() { return defaultName; }
    }

    public static String setting(Kind k) { return c(k.key, DEFAULT); }
    public static void setSetting(Kind k, String v) {
        String val = v == null || v.isBlank() ? DEFAULT : v;
        cfg.setProperty(k.key, val);
        if (!OFF.equalsIgnoreCase(val)) cfg.setProperty(k.key + ".last", val);
        saveCfg();
    }
    public static boolean isOff(Kind k) { return OFF.equalsIgnoreCase(setting(k)); }
    /** Restores the sound that was active before it was turned off, or the built-in sound. */
    public static void setEnabled(Kind k, boolean on) {
        if (!on) { setSetting(k, OFF); if (k == Kind.CALL) stop(); return; }
        String last = c(k.key + ".last", DEFAULT);
        if (last == null || last.isBlank() || OFF.equalsIgnoreCase(last)) last = DEFAULT;
        setSetting(k, last);
    }
    /** Absolute path of the user's own sound, or null for the built-in sound / off. */
    public static String customPath(Kind k) {
        String s = setting(k);
        return s == null || s.isBlank() || s.equalsIgnoreCase(DEFAULT) || s.equalsIgnoreCase(OFF) ? null : s;
    }
    public static String label(Kind k) {
        if (isOff(k)) return "Off";
        String p = customPath(k);
        if (p == null) return k.defaultName;
        File f = new File(p);
        return f.getName() + (f.isFile() ? "" : "  (file not found)");
    }

    public static String setting() { return setting(Kind.GENERAL); }
    public static void setSetting(String v) { setSetting(Kind.GENERAL, v); }
    public static boolean isOff() { return isOff(Kind.GENERAL); }
    public static String customPath() { return customPath(Kind.GENERAL); }
    public static String label() { return label(Kind.GENERAL); }

    /** Plays the selected sound. Safe from any thread and never shows errors to the user. */
    public static void playNotification(Kind k) {
        if (isOff(k)) return;
        String p = customPath(k);
        Thread t = new Thread(() -> {
            try { if (p == null) playDefault(k); else playFile(new File(p)); }
            catch (Exception e) { try { playDefault(k); } catch (Exception ignored) { /* no audio device */ } }
        }, "notify-sound");
        t.setDaemon(true); t.start();
    }
    public static void playNotification() { playNotification(Kind.GENERAL); }

    /** Plays the current sound and reports a problem (missing file, unsupported format, no speakers) on the EDT. */
    public static void test(Kind k, Consumer<String> result) {
        Thread t = new Thread(() -> {
            String err = null;
            try {
                if (isOff(k)) err = "This sound is off.";
                else {
                    String p = customPath(k);
                    if (p == null) playDefault(k); else playFile(new File(p));
                }
            } catch (Exception e) { err = friendly(e); }
            String m = err; SwingUtilities.invokeLater(() -> result.accept(m));
        }, "notify-sound-test");
        t.setDaemon(true); t.start();
    }
    public static void test(Consumer<String> result) { test(Kind.GENERAL, result); }

    /** Starts the selected call sound looping until {@link #stop()} is called. */
    public static void startCallLoop() {
        if (isOff(Kind.CALL)) return;
        final long gen;
        synchronized (Sound.class) { gen = ++ringGeneration; }
        String p = customPath(Kind.CALL);
        Thread t = new Thread(() -> {
            try {
                Clip clip = p == null ? ringClip() : openClip(new File(p));
                installLoop(clip, gen);
            } catch (Exception e) {
                try { installLoop(ringClip(), gen); } catch (Exception ignored) { /* no audio device */ }
            }
        }, "call-ring");
        t.setDaemon(true); t.start();
    }

    public static boolean isPlaying() {
        synchronized (Sound.class) {
            return (current != null && current.isRunning()) || (callLoop != null && callLoop.isRunning());
        }
    }

    /** Stops whatever notification sound is currently playing, including a looping call ring. */
    public static void stop() {
        Clip a, b;
        synchronized (Sound.class) {
            ringGeneration++;
            a = current; b = callLoop; current = null; callLoop = null;
        }
        closeQuiet(a); closeQuiet(b);
    }

    // ---------------------------------------------------------------- recording your own sound

    /** Records 5 seconds from the microphone into ~/.skillswap/sounds/my-notification.wav or my-call.wav. */
    public static void recordMySound(Kind kind, Consumer<File> ok, Consumer<String> fail) {
        Thread t = new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                if (!AudioSystem.isLineSupported(info)) throw new IOException("No microphone was found on this computer.");
                TargetDataLine line;
                try { line = (TargetDataLine) AudioSystem.getLine(info); line.open(fmt); }
                catch (LineUnavailableException e) { throw new IOException("The microphone is busy or unavailable. Close other recording apps and try again."); }
                line.start();
                ByteArrayOutputStream pcm = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                long end = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < end) {
                    int n = line.read(buf, 0, buf.length);
                    if (n > 0) pcm.write(buf, 0, n);
                }
                line.stop(); line.close();
                byte[] data = pcm.toByteArray();
                if (data.length < fmt.getFrameSize() * 1000) throw new IOException("Nothing was recorded. Check that the microphone is working and allowed for desktop apps.");
                int peak = 0;
                for (int i = 0; i + 1 < data.length; i += 2) peak = Math.max(peak, Math.abs((short) ((data[i] & 0xFF) | (data[i + 1] << 8))));
                if (peak < 200) throw new IOException("Nothing was heard. Check the microphone is not muted and try again.");
                Path dir = DIR.resolve("sounds"); Files.createDirectories(dir);
                File dest = dir.resolve(kind.recordName).toFile();
                AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(data), fmt, data.length / fmt.getFrameSize()), AudioFileFormat.Type.WAVE, dest);
                SwingUtilities.invokeLater(() -> ok.accept(dest));
            } catch (Exception e) {
                String m = e.getMessage() == null ? "Recording failed." : e.getMessage();
                SwingUtilities.invokeLater(() -> fail.accept(m));
            }
        }, "notify-record");
        t.setDaemon(true); t.start();
    }
    public static void recordMySound(Consumer<File> ok, Consumer<String> fail) { recordMySound(Kind.GENERAL, ok, fail); }

    // ---------------------------------------------------------------- playing

    static void playFile(File f) throws Exception { start(openClip(f)); }

    static Clip openClip(File f) throws Exception {
        if (f == null || !f.isFile()) throw new IOException("The sound file was not found:\n" + (f == null ? "" : f.getAbsolutePath()));
        try { return openClipFile(f); }
        catch (Exception e) {
            try { return openClipFile(convert(f, false)); }
            catch (Exception converted) {
                throw converted.getMessage() == null || converted.getMessage().isBlank() ? e : converted;
            }
        }
    }

    /** Checks that an audio file can be used, converting MP3/M4A/OGG and other formats once (downloading ffmpeg if needed on Windows). Runs in the background. */
    public static void prepare(File f, Consumer<String> done) {
        Thread t = new Thread(() -> {
            String err = null;
            try {
                if (f == null || !f.isFile()) throw new IOException("That file was not found.");
                if (!decodable(f)) convert(f, true);
            } catch (Exception e) { err = friendly(e); }
            String m = err; SwingUtilities.invokeLater(() -> done.accept(m));
        }, "notify-sound-prepare");
        t.setDaemon(true); t.start();
    }

    static boolean decodable(File f) {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(f)) { return in.getFormat() != null; }
        catch (Exception e) { return false; }
    }

    static Clip openClipFile(File f) throws Exception {
        Clip clip = AudioSystem.getClip();
        try (AudioInputStream in = AudioSystem.getAudioInputStream(f)) { clip.open(in); }
        catch (Exception e) { closeQuiet(clip); throw e; }
        return clip;
    }

    static void playDefault(Kind k) throws Exception {
        if (k == Kind.CALL) playRing(); else playChime();
    }

    /** Built-in two-note chime, generated in memory so the app ships no audio file. */
    static void playChime() throws Exception {
        AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
        byte[] pcm = chime(fmt.getSampleRate());
        Clip clip = AudioSystem.getClip();
        clip.open(fmt, pcm, 0, pcm.length);
        start(clip);
    }

    static Clip ringClip() throws Exception {
        AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
        byte[] pcm = ring(fmt.getSampleRate());
        Clip clip = AudioSystem.getClip();
        clip.open(fmt, pcm, 0, pcm.length);
        return clip;
    }

    /** Built-in ring-ring for calls, clearly different from the message chime. */
    static void playRing() throws Exception { start(ringClip()); }

    static void installLoop(Clip clip, long gen) {
        synchronized (Sound.class) {
            if (gen != ringGeneration) { closeQuiet(clip); return; }
            Clip old = callLoop;
            callLoop = clip;
            closeQuiet(old);
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    synchronized (Sound.class) { if (callLoop == clip) callLoop = null; }
                    closeQuiet(clip);
                }
            });
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    static void start(Clip clip) {
        synchronized (Sound.class) {
            Clip old = current;
            current = clip;
            closeQuiet(old);
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    synchronized (Sound.class) { if (current == clip) current = null; }
                    closeQuiet(clip);
                }
            });
            clip.start();
        }
    }

    static void closeQuiet(Clip c) {
        if (c == null) return;
        try { c.stop(); c.close(); } catch (Exception ignored) { }
    }

    /** A short, bright "ding-dong" (A5 then E6) with a soft attack and decay. */
    static byte[] chime(float rate) {
        double[][] notes = {{880.00, 0.11}, {1318.51, 0.23}};
        int gap = (int) (rate * 0.012), total = 0;
        for (double[] n : notes) total += (int) (rate * n[1]) + gap;
        byte[] out = new byte[total * 2];
        int p = 0;
        for (double[] n : notes) {
            int count = (int) (rate * n[1]);
            for (int i = 0; i < count; i++) {
                double t = i / rate, env = Math.min(1, t / 0.006) * Math.exp(-4.2 * t / n[1]);
                double v = Math.sin(2 * Math.PI * n[0] * t) + 0.28 * Math.sin(4 * Math.PI * n[0] * t);
                short s = (short) (v * env * 0.26 * 32767);
                out[p++] = (byte) (s & 0xFF); out[p++] = (byte) ((s >> 8) & 0xFF);
            }
            for (int i = 0; i < gap; i++) { out[p++] = 0; out[p++] = 0; }
        }
        return out;
    }

    /** Two bursts of a classic 440 + 480 Hz ring, with a soft trill. */
    static byte[] ring(float rate) {
        double total = 1.28;
        int n = (int) (rate * total), p = 0;
        byte[] out = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / rate, env = 0;
            if (t < 0.34) env = edge(t, 0.34);
            else if (t >= 0.44 && t < 0.78) env = edge(t - 0.44, 0.34);
            double v = 0;
            if (env > 0) {
                double tone = Math.sin(2 * Math.PI * 440 * t) + Math.sin(2 * Math.PI * 480 * t);
                double trill = 0.55 + 0.45 * Math.sin(2 * Math.PI * 18 * t);
                v = tone * trill * env * 0.16;
            }
            short s = (short) (v * 32767);
            out[p++] = (byte) (s & 0xFF); out[p++] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }

    static double edge(double t, double dur) { return Math.min(1, t / 0.012) * Math.min(1, (dur - t) / 0.03); }

    // ---------------------------------------------------------------- other formats (MP3, M4A, OGG...) via ffmpeg

    static File convert(File in, boolean download) throws Exception {
        String ff = Videos.ffmpeg();
        if (ff == null && download) { try { ff = Videos.ensureFfmpeg(); } catch (Exception ignored) { /* reported below */ } }
        if (ff == null) {
            boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
            throw new IOException("This audio format needs the free ffmpeg converter. " + (win
                    ? "Pick the sound again so the app can download it automatically."
                    : "Install it (macOS: brew install ffmpeg, Linux: sudo apt install ffmpeg) and restart SkillSwap."));
        }
        Path dir = DIR.resolve("cache").resolve("sounds");
        Files.createDirectories(dir);
        String base = in.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        Path out = dir.resolve("snd-" + Integer.toHexString(in.getAbsolutePath().hashCode()) + "-" + base + ".wav");
        if (Files.isRegularFile(out) && Files.size(out) > 0 && Files.getLastModifiedTime(out).toMillis() >= in.lastModified()) return out.toFile();
        Path tmp = dir.resolve(out.getFileName() + ".part");
        Files.deleteIfExists(tmp);
        try {
            Process pr = new ProcessBuilder(ff, "-y", "-i", in.getAbsolutePath(), "-vn", "-ac", "1", "-ar", "44100",
                    "-c:a", "pcm_s16le", "-f", "wav", tmp.toString()).redirectErrorStream(true).start();
            String log = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean done = pr.waitFor(20, TimeUnit.SECONDS);
            if (!done) { pr.destroyForcibly(); throw new IOException("Converting this sound took too long. Try a shorter file."); }
            if (pr.exitValue() != 0 || !Files.isRegularFile(tmp) || Files.size(tmp) == 0)
                throw new IOException("Could not read this audio file (" + bestLine(log) + ").");
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            return out.toFile();
        } finally { try { Files.deleteIfExists(tmp); } catch (IOException ignored) { } }
    }

    /** First useful error line from ffmpeg's log (the first lines are just the version banner). */
    static String bestLine(String log) {
        if (log == null) return "unknown error";
        String best = "";
        for (String raw : log.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String low = line.toLowerCase(java.util.Locale.ROOT);
            if (low.contains("error") || low.contains("invalid") || low.contains("unable") || low.contains("unsupported") || low.contains("no such")) best = line;
        }
        if (!best.isEmpty()) return best.length() > 160 ? best.substring(0, 160) + "\u2026" : best;
        String[] lines = log.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) if (!lines[i].isBlank()) { String l = lines[i].trim(); return l.length() > 160 ? l.substring(0, 160) + "\u2026" : l; }
        return "unknown error";
    }

    // ---------------------------------------------------------------- audio files and previews

    /** True for sound files the picker should preview (WAV, MP3, M4A, OGG, FLAC...). */
    public static boolean isAudio(File f) {
        if (f == null || f.getName() == null) return false;
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        int i = n.lastIndexOf('.');
        if (i < 0 || i + 1 >= n.length()) return false;
        return switch (n.substring(i + 1)) {
            case "wav", "wave", "mp3", "mp2", "aiff", "aif", "aifc", "au", "snd", "m4a", "m4b", "aac",
                 "ogg", "oga", "opus", "flac", "wma", "caf", "amr", "ac3" -> true;
            default -> false;
        };
    }

    static Clip previewClip;
    static long previewGeneration;

    /** Plays a sound once for the file picker preview. Replaces any preview already playing. */
    public static void playPreview(File f, Consumer<String> fail, Runnable done) {
        final long gen;
        synchronized (Sound.class) { gen = ++previewGeneration; }
        Thread t = new Thread(() -> {
            try {
                Clip c = openClip(f);
                synchronized (Sound.class) {
                    if (gen != previewGeneration) { closeQuiet(c); return; }
                    closeQuiet(previewClip); previewClip = c;
                }
                c.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) {
                        synchronized (Sound.class) { if (previewClip == c) previewClip = null; }
                        closeQuiet(c);
                        if (done != null) SwingUtilities.invokeLater(done);
                    }
                });
                c.start();
            } catch (Exception e) {
                String m = friendly(e);
                if (fail != null) SwingUtilities.invokeLater(() -> fail.accept(m));
            }
        }, "sound-preview");
        t.setDaemon(true); t.start();
    }

    public static void stopPreview() {
        Clip c;
        synchronized (Sound.class) { previewGeneration++; c = previewClip; previewClip = null; }
        closeQuiet(c);
    }

    static String friendly(Exception e) {
        if (e instanceof UnsupportedAudioFileException || e instanceof IllegalArgumentException)
            return "That audio format is not supported. Try a WAV file, or an MP3/M4A/OGG file (the app converts those automatically).";
        if (e instanceof LineUnavailableException) return "No audio output device is available right now.";
        String m = e.getMessage();
        return m == null || m.isBlank() ? "Could not play that sound." : m.split("\\R")[0];
    }
}
