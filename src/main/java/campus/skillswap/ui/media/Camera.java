package campus.skillswap.ui.media;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.*;
import campus.skillswap.config.AppConfig;

/**
 * Webcam capture through ffmpeg (the same optional ffmpeg the reels use): ffmpeg reads the camera and pipes
 * small MJPEG frames to us. Windows = DirectShow, macOS = AVFoundation, Linux = Video4Linux.
 * USB cameras are tried with several settings (default, 640x480, raw MJPEG, 720p), and every attempt is written to
 * ~/.skillswap/camera.log so a failure can be diagnosed.
 */
public class Camera {
    static final String[] OUT = {"-an", "-vf", "fps=12,scale=480:-2", "-pix_fmt", "yuvj420p", "-q:v", "7", "-f", "image2pipe", "-c:v", "mjpeg", "-"};
    volatile boolean run; volatile Process pr;

    public static Path logFile() { return AppConfig.DIR.resolve("camera.log"); }

    public void start(Consumer<byte[]> onJpeg, Consumer<String> onFail) {
        run = true; Thread t = new Thread(() -> loop(onJpeg, onFail), "call-camera"); t.setDaemon(true); t.start();
    }
    public void stop() { run = false; Process p = pr; if (p != null) p.destroyForcibly(); }

    void loop(Consumer<byte[]> onJpeg, Consumer<String> onFail) {
        StringBuilder log = new StringBuilder("=== camera start " + new Date() + " ===\nos: " + System.getProperty("os.name") + "\n");
        try { loop2(onJpeg, onFail, log); } finally { writeLog(log); }
    }
    void loop2(Consumer<byte[]> onJpeg, Consumer<String> onFail, StringBuilder log) {
        String ff = Videos.ffmpeg();
        if (ff == null) { log.append("ffmpeg not found\n"); if (run) onFail.accept("ffmpeg was not found. Run scripts/get-ffmpeg once, then restart SkillSwap."); return; }
        log.append("ffmpeg: ").append(ff).append('\n');
        List<List<String>> attempts = inputs(ff, log);
        if (attempts.isEmpty()) { if (run) onFail.accept("ffmpeg could not find any camera. Check it is plugged in and that the system allows desktop apps to use the camera (Windows: Settings > Privacy > Camera)."); return; }
        String firstErr = null;
        for (List<String> in : attempts) {
            if (!run) return;
            List<String> cmd = new ArrayList<>(); cmd.add(ff); cmd.addAll(Arrays.asList("-hide_banner", "-loglevel", "error")); cmd.addAll(in); cmd.addAll(Arrays.asList(OUT));
            log.append("\n> ").append(String.join(" ", in)).append('\n');
            AtomicBoolean got = new AtomicBoolean(); List<String> errs = Collections.synchronizedList(new ArrayList<>()); Process p = null;
            try {
                p = new ProcessBuilder(cmd).start(); pr = p; final Process fp = p;
                Thread drain = new Thread(() -> { try (BufferedReader r = new BufferedReader(new InputStreamReader(fp.getErrorStream()))) { String l; while ((l = r.readLine()) != null) if (errs.size() < 40) errs.add(l); } catch (IOException e) { } });
                drain.setDaemon(true); drain.start();
                Thread dog = new Thread(() -> { try { Thread.sleep(7000); } catch (InterruptedException e) { return; } if (!got.get()) fp.destroyForcibly(); });   // opened but never delivered a frame
                dog.setDaemon(true); dog.start();
                parse(p.getInputStream(), f -> { if (got.compareAndSet(false, true)) log.append("  first frame received\n"); onJpeg.accept(f); }, () -> run);
            } catch (IOException e) { errs.add(String.valueOf(e.getMessage())); }
            finally { if (p != null) p.destroyForcibly(); }
            synchronized (errs) { for (String e : errs) log.append("  ").append(e).append('\n'); }
            if (got.get()) { if (run) onFail.accept("The camera stopped sending pictures (is another program using it?)."); return; }
            if (errs.isEmpty()) log.append("  no frames and no error (camera opened but sent nothing)\n");
            if (firstErr == null) firstErr = friendly(new ArrayList<>(errs));
        }
        if (run) onFail.accept(firstErr != null ? firstErr : "The camera opened but sent no pictures. Close other programs that use it and try again.");
    }

    /** Turns ffmpeg's error lines into something a person can act on. */
    public static String friendly(List<String> lines) {
        String all = String.join("\n", lines).toLowerCase();
        if (all.contains("could not run graph") || all.contains("busy") || all.contains("in use") || all.contains("i/o error") || all.contains("could not set video options"))
            return "The camera could not be opened - another program (browser, Zoom, Teams, the Camera app) is probably using it. Close it and try again.";
        if (all.contains("permission denied") || all.contains("access is denied") || all.contains("not authorized") || all.contains("not permitted"))
            return "The system blocked camera access. Allow it (Windows: Settings > Privacy > Camera > let desktop apps use the camera; macOS: System Settings > Privacy > Camera).";
        if (all.contains("could not find video device") || all.contains("no such file") || all.contains("no such device") || all.contains("no capture devices"))
            return "No camera was found. Check it is plugged in.";
        if (lines.isEmpty()) return null;
        String pick = lines.get(lines.size() - 1); for (String l : lines) if (l.toLowerCase().contains("error") || l.toLowerCase().contains("could not")) { pick = l; break; }
        pick = pick.replaceAll("^\\[[^\\]]*\\]\\s*", ""); return pick.length() > 150 ? pick.substring(0, 150) : pick;
    }

    /** Cuts a stream of back-to-back JPEGs (FFD8 ... FFD9) into single frames. */
    public static void parse(InputStream raw, Consumer<byte[]> onFrame, BooleanSupplier run) throws IOException {
        InputStream in = new BufferedInputStream(raw, 1 << 16); ByteArrayOutputStream buf = new ByteArrayOutputStream(64 * 1024);
        int prev = -1, b; boolean inFrame = false;
        while (run.getAsBoolean() && (b = in.read()) >= 0) {
            if (!inFrame) { if (prev == 0xFF && b == 0xD8) { inFrame = true; buf.reset(); buf.write(0xFF); buf.write(0xD8); } }
            else { buf.write(b); if (prev == 0xFF && b == 0xD9) { onFrame.accept(buf.toByteArray()); inFrame = false; } }
            prev = b;
        }
    }

    static List<List<String>> inputs(String ff, StringBuilder log) {
        String os = System.getProperty("os.name", "").toLowerCase(); List<List<String>> l = new ArrayList<>();
        if (os.contains("win")) {
            List<String> cams = dshowCameras(ff, log); log.append("cameras found: ").append(cams).append('\n'); int n = 0;
            for (String d : cams) {
                String v = "video=" + d;
                l.add(Arrays.asList("-f", "dshow", "-i", v));                                                                              // camera's own default
                l.add(Arrays.asList("-f", "dshow", "-video_size", "640x480", "-framerate", "30", "-i", v));                                // common USB mode
                l.add(Arrays.asList("-f", "dshow", "-vcodec", "mjpeg", "-video_size", "640x480", "-framerate", "30", "-i", v));            // USB cams that only offer MJPEG
                l.add(Arrays.asList("-f", "dshow", "-vcodec", "mjpeg", "-video_size", "1280x720", "-framerate", "30", "-i", v));
                l.add(Arrays.asList("-f", "dshow", "-rtbufsize", "100M", "-video_size", "320x240", "-i", v));
                if (++n == 2) break;
            }
        } else if (os.contains("mac")) {
            for (String idx : new String[]{"0", "1"}) {
                l.add(Arrays.asList("-f", "avfoundation", "-framerate", "30", "-video_size", "640x480", "-i", idx));
                l.add(Arrays.asList("-f", "avfoundation", "-pixel_format", "uyvy422", "-framerate", "30", "-i", idx));
                l.add(Arrays.asList("-f", "avfoundation", "-i", idx));
            }
        } else {
            for (String dev : new String[]{"/dev/video0", "/dev/video1"}) {
                l.add(Arrays.asList("-f", "v4l2", "-i", dev));
                l.add(Arrays.asList("-f", "v4l2", "-input_format", "mjpeg", "-video_size", "640x480", "-i", dev));
            }
        }
        return l;
    }
    /** Names of the DirectShow video devices, read from ffmpeg's device list. */
    static List<String> dshowCameras(String ff, StringBuilder log) {
        List<String> lines = new ArrayList<>();
        try {
            Process p = new ProcessBuilder(ff, "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) { String line; while ((line = r.readLine()) != null) lines.add(line); }
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS); p.destroyForcibly();
        } catch (Exception e) { log.append("device list failed: ").append(e).append('\n'); }
        if (log != null) { log.append("-- device list --\n"); for (String s : lines) log.append(s).append('\n'); log.append("-----------------\n"); }
        return parseDshow(lines);
    }
    /** Handles both ffmpeg list formats: new ("Name" (video)) and old (sections "DirectShow video devices"). */
    public static List<String> parseDshow(List<String> lines) {
        List<String> names = new ArrayList<>(); Pattern q = Pattern.compile("\"([^\"]+)\""); boolean vid = false;
        for (String line : lines) {
            if (line.contains("DirectShow video devices")) vid = true; else if (line.contains("DirectShow audio devices")) vid = false;
            if (line.contains("Alternative name") || line.contains("(audio)") || line.contains("(none)")) continue;
            if (line.contains("(video)") || vid) { Matcher m = q.matcher(line); if (m.find() && !names.contains(m.group(1))) names.add(m.group(1)); }
        }
        return names;
    }
    static void writeLog(StringBuilder log) {
        try { Files.createDirectories(AppConfig.DIR); Files.write(logFile(), log.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); } catch (Exception e) { /* best effort */ }
    }
}
