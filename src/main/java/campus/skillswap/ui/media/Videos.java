package campus.skillswap.ui.media;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.filechooser.FileNameExtensionFilter;
import campus.skillswap.model.*;
import campus.skillswap.net.LanSync;
import static campus.skillswap.config.AppConfig.*;
import static campus.skillswap.core.AppState.*;
import static campus.skillswap.data.Database.*;
import static campus.skillswap.data.Seed.*;
import static campus.skillswap.service.Skills.*;
import static campus.skillswap.service.Stats.*;
import static campus.skillswap.service.Security.*;
import static campus.skillswap.ui.Theme.*;
import static campus.skillswap.ui.AppShell.*;
import static campus.skillswap.ui.Notifier.*;
import static campus.skillswap.ui.screens.LoginScreen.*;
import static campus.skillswap.ui.screens.ConnectionScreen.*;
import static campus.skillswap.ui.screens.SettingsScreen.*;
import static campus.skillswap.ui.screens.FeedScreen.*;
import static campus.skillswap.ui.screens.SearchScreen.*;
import static campus.skillswap.ui.screens.CertsScreen.*;
import static campus.skillswap.ui.screens.BookingsScreen.*;
import static campus.skillswap.ui.screens.NearbyScreen.*;
import static campus.skillswap.ui.screens.MessagesScreen.*;
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Reels.*;

/** Reel upload/download (PostgreSQL BYTEA), inline and external playback helpers. */
public class Videos {

    // ====================== VIDEO / REELS ======================
    public static final long MAX_VIDEO = 60L * 1024 * 1024;     // videos live in PostgreSQL (BYTEA), so keep them short
    public static String[] xfer;                                // host, port, db, user, password: video transfers use their own connection
    /** null if the in-app player (JavaFX) should handle this file; otherwise a short reason why it probably won't. */
    public static String playProblem(File f) {
        String n = f.getName().toLowerCase();
        if (!(n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".mov"))) return "the in-app player only plays MP4 / M4V / MOV files";
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length(); int chunk = (int) Math.min(len, 6L * 1024 * 1024);
            for (long pos : new long[]{0, len - chunk}) {          // the codec name sits in the file header (or at the very end)
                r.seek(pos); byte[] b = new byte[chunk]; r.readFully(b); String t = new String(b, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (t.contains("hvc1") || t.contains("hev1")) return "it uses H.265 / HEVC video (common for iPhone and some Android recordings)";
                if (t.contains("av01") || t.contains("vp09")) return "it uses AV1 / VP9 video";
            }
        } catch (IOException e) { /* unreadable: let the upload report it */ }
        return null;
    }
    public static String mb(long b) { return String.format("%.1f MB", b / 1048576.0); }
    public static Post postById(int id) { return S.posts.stream().filter(p -> p.id == id).findFirst().orElse(null); }
    public static List<Post> videoPosts() { return S.posts.stream().filter(p -> p.videoName != null).sorted((a, b) -> Long.compare(b.time, a.time)).collect(Collectors.toList()); }
    public static Connection openXfer() throws SQLException { return open(xfer[0], xfer[1], xfer[2], xfer[3], xfer[4]); }

    /** Upload in the background so the app stays responsive; the video appears on every computer once the upload finishes. */
    public static void uploadVideo(int postId, File f) { if (LanSync.active()) uploadVideoLan(postId, f); else uploadVideoPg(postId, f); }
    static void uploadVideoLan(int postId, File f) {
        final long len = f.length(); toast("Uploading video (" + mb(len) + ")...");
        new Thread(() -> {
            try {
                LanSync.putVideo(postId, f);
                SwingUtilities.invokeLater(() -> { Post cur = postById(postId); if (cur != null) { cur.videoName = f.getName(); cur.videoSize = len; save(); }
                    toast("Your video is live!"); if (me != null && page.equals("Feed") && inlineFx == null) show("Feed"); });
            } catch (Exception e) { SwingUtilities.invokeLater(() -> toast("Video upload failed: " + nz(e.getMessage()).split("\n")[0])); }
        }, "video-upload").start();
    }
    static void uploadVideoPg(int postId, File f) {
        final long len = f.length(); toast("Uploading video (" + mb(len) + ")...");
        new Thread(() -> {
            try (Connection cn = openXfer(); InputStream in = new BufferedInputStream(new FileInputStream(f));
                 PreparedStatement p = cn.prepareStatement("INSERT INTO post_videos(post_id,file_name,size_bytes,data) VALUES(?,?,?,?) ON CONFLICT(post_id) DO UPDATE SET file_name=EXCLUDED.file_name,size_bytes=EXCLUDED.size_bytes,data=EXCLUDED.data")) {
                p.setInt(1, postId); p.setString(2, f.getName()); p.setLong(3, len); p.setBinaryStream(4, in, (int) len); p.executeUpdate();
                SwingUtilities.invokeLater(() -> { Post cur = postById(postId);
                    if (cur != null) { cur.videoName = f.getName(); cur.videoSize = len; ver++; snap.put("p:" + postId, pfp(cur)); }
                    toast("Your video is live!"); if (me != null && page.equals("Feed") && inlineFx == null) show("Feed"); });
            } catch (Exception e) { SwingUtilities.invokeLater(() -> toast("Video upload failed: " + nz(e.getMessage()).split("\n")[0])); }
        }, "video-upload").start();
    }
    /** Download (once) to ~/.skillswap/cache, then hand the local file to the callback on the EDT. */
    public static void withVideo(Post po, Consumer<File> ok, Consumer<String> fail) { if (LanSync.active()) withVideoLan(po, ok, fail); else withVideoPg(po, ok, fail); }
    static final Map<Integer, Object> DL_LOCKS = new ConcurrentHashMap<>();
    /** True while a video player is on screen (inline or full-screen reels): incoming LAN/DB changes must not rebuild the UI then. */
    public static boolean videoOpen() { return inlineFx != null || Reels.reelDlg != null; }
    static void withVideoLan(Post po, Consumer<File> ok, Consumer<String> fail) {
        final int id = po.id; final long size = po.videoSize;
        final Path dir = DIR.resolve("cache"), file = dir.resolve("video_" + id + "_" + po.videoName.replaceAll("[^A-Za-z0-9._-]", "_"));
        try { if (Files.exists(file) && Files.size(file) == size) { ok.accept(file.toFile()); return; } } catch (IOException e) { /* download again */ }
        new Thread(() -> {
            synchronized (DL_LOCKS.computeIfAbsent(id, k -> new Object())) {       // one download per video at a time (double clicks, thumbnail + play)
                try {
                    Files.createDirectories(dir);
                    if (!(Files.exists(file) && Files.size(file) == size)) {
                        Path tmp = dir.resolve(file.getFileName() + "." + System.nanoTime() + ".part");
                        try { LanSync.getVideo(id, tmp); long got = Files.size(tmp);
                            if (size > 0 && got != size) throw new IOException("Video download was incomplete (" + mb(got) + " of " + mb(size) + "). Try again.");
                            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                        } finally { try { Files.deleteIfExists(tmp); } catch (IOException e) { } }
                    }
                    SwingUtilities.invokeLater(() -> ok.accept(file.toFile()));
                } catch (Throwable e) { SwingUtilities.invokeLater(() -> fail.accept(nz(e.getMessage()).split("\n")[0])); }
            }
        }, "video-download").start();
    }
    static void withVideoPg(Post po, Consumer<File> ok, Consumer<String> fail) {
        final int id = po.id; final long size = po.videoSize;
        final Path dir = DIR.resolve("cache"), file = dir.resolve("video_" + id + "_" + po.videoName.replaceAll("[^A-Za-z0-9._-]", "_"));
        try { if (Files.exists(file) && Files.size(file) == size) { ok.accept(file.toFile()); return; } } catch (IOException e) { /* download again */ }
        new Thread(() -> {
            try (Connection cn = openXfer(); PreparedStatement p = cn.prepareStatement("SELECT data FROM post_videos WHERE post_id=?")) {
                p.setInt(1, id);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new IOException("Video not found on the server");
                    Files.createDirectories(dir); Path tmp = dir.resolve(file.getFileName() + ".part");
                    try (InputStream in = r.getBinaryStream(1)) { Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); }
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                SwingUtilities.invokeLater(() -> ok.accept(file.toFile()));
            } catch (Exception e) { SwingUtilities.invokeLater(() -> fail.accept(nz(e.getMessage()).split("\n")[0])); }
        }, "video-download").start();
    }
    /** In-app player: only available when VideoPlayerFX.class (and JavaFX) are present; otherwise null and the app shows a message. */
    public static JComponent fxPlayer(File f, int w, int h, Consumer<String> onFail) {
        Consumer<String> wrapped = why -> onFail.accept(friendly(why) + "  [" + diagnose(f) + "]");
        try { return (JComponent) Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("create", File.class, int.class, int.class, Consumer.class).invoke(null, f, w, h, wrapped); } catch (Throwable t) { return null; }
    }
    // ---- ffmpeg: converts any video to MP4 (H.264 + AAC) so it always plays in the app ----
    static String ffmpegExe; static long ffmpegLastMiss;
    /** Path of a working ffmpeg, or null. Looks in lib/ffmpeg (see scripts/get-ffmpeg), then ~/.skillswap, then the system PATH. */
    public static synchronized String ffmpeg() {
        if (ffmpegExe != null) return ffmpegExe;
        if (System.currentTimeMillis() - ffmpegLastMiss < 20000) return null;          // do not keep spawning processes while it is missing
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "ffmpeg.exe" : "ffmpeg";
        for (String c : new String[]{Paths.get("lib", "ffmpeg", exe).toAbsolutePath().toString(), DIR.resolve(exe).toString(), "ffmpeg"}) {
            try { Process pr = new ProcessBuilder(c, "-version").redirectErrorStream(true).start(); pr.getInputStream().readAllBytes();
                if (pr.waitFor(10, TimeUnit.SECONDS) && pr.exitValue() == 0) { ffmpegExe = c; return c; } } catch (Exception e) { /* try the next place */ }
        }
        ffmpegLastMiss = System.currentTimeMillis(); return null;
    }
    /** What is inside a video, read with ffmpeg: {video codec, pixel format, audio codec or null, "rotated" or null}. */
    static String[] probe(File f) {
        String[] r = {null, null, null, null};
        try {
            Process pr = new ProcessBuilder(ffmpeg(), "-hide_banner", "-i", f.getAbsolutePath()).redirectErrorStream(true).start();
            String out = new String(pr.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); pr.waitFor(15, TimeUnit.SECONDS);
            for (String line : out.split("\\R")) {
                if (r[0] == null && line.contains("Video:") && !line.contains("attached pic")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("Video: (\\w+)").matcher(line); if (m.find()) r[0] = m.group(1);
                    m = java.util.regex.Pattern.compile("\\b(yuvj?\\d{3}p\\d*(?:le|be)?|nv12|p010le|gbrp\\w*|rgb\\w*|bgr\\w*|pal8)\\b").matcher(line); if (m.find()) r[1] = m.group(1);
                }
                if (r[2] == null && line.contains("Audio:")) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("Audio: (\\w+)").matcher(line); if (m.find()) r[2] = m.group(1); }
                if (line.contains("displaymatrix") || line.matches("(?i).*\\brotate\\s*:\\s*(90|180|270).*")) r[3] = "rotated";
            }
        } catch (Exception e) { /* unknown: re-encode to be safe */ }
        return r;
    }
    static void runFfmpeg(List<String> cmd, Consumer<Integer> progress) throws Exception {
        Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start(); double dur = 0; String last = "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line; java.util.regex.Pattern dp = java.util.regex.Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
            while ((line = r.readLine()) != null) {
                if (!line.contains("=")) last = line;
                java.util.regex.Matcher m = dp.matcher(line); if (dur == 0 && m.find()) dur = Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Double.parseDouble(m.group(3));
                if (line.startsWith("out_time_ms=") && dur > 0) { try { progress.accept((int) Math.max(0, Math.min(99, Long.parseLong(line.substring(12).trim()) / 1e6 / dur * 100))); } catch (NumberFormatException e) { /* N/A */ } }
            }
        }
        if (pr.waitFor() != 0) throw new IOException("ffmpeg could not convert this file. " + last);
    }
    /**
     * Makes any video playable in the app without hurting quality: parts that are already fine (H.264 video, AAC audio) are copied untouched,
     * only what the player can't read is re-encoded (high quality, original size up to 1920 px). Falls back to smaller/lighter settings only if the result is over the size limit.
     */
    public static File convertForApp(File in, Consumer<Integer> progress) throws Exception {
        String ff = ffmpeg(); if (ff == null) throw new IOException("ffmpeg was not found");
        Path dir = DIR.resolve("cache").resolve("convert"); Files.createDirectories(dir);
        try (Stream<Path> st = Files.list(dir)) { for (Path p : (Iterable<Path>) st::iterator) if (System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis() > 86400000L) Files.deleteIfExists(p); } catch (IOException e) { /* cleanup only */ }
        String base = in.getName().replaceAll("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9._-]", "_"); if (base.isEmpty()) base = "video";
        File out = dir.resolve(base + ".mp4").toFile(); if (out.getAbsolutePath().equalsIgnoreCase(in.getAbsolutePath())) out = dir.resolve(base + "_app.mp4").toFile();
        String[] info = probe(in);
        boolean videoOk = "h264".equals(info[0]) && info[1] != null && info[1].matches("yuvj?420p") && info[3] == null;      // 8-bit H.264, not rotated by metadata
        List<String> audio = info[2] == null ? Arrays.asList("-an") : "aac".equals(info[2]) ? Arrays.asList("-c:a", "copy") : Arrays.asList("-c:a", "aac", "-b:a", "192k", "-ac", "2");
        List<List<String>> plans = new ArrayList<>();
        if (videoOk) plans.add(Arrays.asList("-c:v", "copy"));
        for (int[] q : new int[][]{{18, 1920}, {22, 1920}, {26, 1280}, {30, 1280}})
            plans.add(Arrays.asList("-vf", "scale=min(" + q[1] + "\\,iw):min(" + q[1] + "\\,ih):force_original_aspect_ratio=decrease:force_divisible_by=2,format=yuv420p", "-c:v", "libx264", "-preset", "fast", "-crf", "" + q[0]));
        Exception lastErr = null; boolean tooBig = false;
        for (List<String> plan : plans) {
            List<String> cmd = new ArrayList<>(Arrays.asList(ff, "-y", "-i", in.getAbsolutePath(), "-map", "0:v:0", "-map", "0:a:0?")); cmd.addAll(plan); cmd.addAll(audio);
            cmd.addAll(Arrays.asList("-movflags", "+faststart", "-progress", "pipe:1", "-nostats", out.getAbsolutePath()));
            try { runFfmpeg(cmd, progress); } catch (Exception e) { lastErr = e; continue; }
            if (out.exists() && out.length() > 0 && out.length() <= MAX_VIDEO) { progress.accept(100); return out; }
            tooBig = out.exists() && out.length() > MAX_VIDEO;
        }
        if (tooBig) throw new IOException("Even compressed, the video is over the " + mb(MAX_VIDEO) + " limit - trim it first.");
        throw lastErr != null ? lastErr : new IOException("ffmpeg could not convert this file.");
    }
    public static void setStatus(String t) { try { if (statusBar != null) statusBar.setText(t); } catch (Throwable e) { /* ignore */ } }
    /** Converts in the background (progress shown in the status bar), then uploads the converted video. */
    public static void convertAndUpload(int postId, File f) {
        toast("Converting your video so it plays in the app...");
        new Thread(() -> {
            try {
                final File out = convertForApp(f, pct -> SwingUtilities.invokeLater(() -> setStatus("Converting video... " + pct + "%")));
                if (out.length() > MAX_VIDEO) throw new IOException("The converted video is " + mb(out.length()) + ", over the " + mb(MAX_VIDEO) + " limit - trim it first.");
                SwingUtilities.invokeLater(() -> { setStatus("Video converted - uploading..."); uploadVideo(postId, out); });
            } catch (Exception e) { SwingUtilities.invokeLater(() -> { setStatus(" "); toast("Video conversion failed: " + nz(e.getMessage()).split("\n")[0]); }); }
        }, "video-convert").start();
    }

    /** Plain-English version of a JavaFX media error. */
    static String friendly(String why) {
        String w = nz(why);
        if (w.contains("MEDIA_INVALID")) return "The player could not decode this file (its video/audio format is not supported, or the file is damaged).";
        if (w.contains("UNSUPPORTED")) return "This video format is not supported by the player.";
        if (w.contains("INACCESSIBLE") || w.contains("IO")) return "The video file could not be read.";
        return w.split("\n")[0];
    }
    /** Looks inside an MP4/MOV and reports what it contains, e.g. "Video: H.264, audio: AAC", or what is wrong with the file. */
    public static String diagnose(File f) {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length(); if (len < 16) return "file is empty or too small";
            int chunk = (int) Math.min(len, 6L * 1024 * 1024); byte[] head = new byte[chunk], tail = new byte[chunk];
            r.seek(0); r.readFully(head); r.seek(len - chunk); r.readFully(tail);
            String box = new String(head, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!java.util.Arrays.asList("ftyp", "moov", "mdat", "wide", "free", "skip").contains(box)) return "this does not look like a real MP4 file - it may be damaged or the upload was cut off";
            String t = new String(head, java.nio.charset.StandardCharsets.ISO_8859_1) + new String(tail, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!t.contains("moov")) return "the file has no index (moov) - it is incomplete, the upload may have been cut off";
            String v = t.contains("hvc1") || t.contains("hev1") ? "H.265/HEVC" : t.contains("avc1") || t.contains("avc3") ? "H.264" : t.contains("av01") ? "AV1" : t.contains("vp09") ? "VP9" : t.contains("mp4v") ? "MPEG-4 Part 2" : "unknown";
            String a = t.contains("mp4a") ? "AAC" : t.contains("ac-3") || t.contains("ec-3") ? "Dolby AC-3" : t.contains("Opus") ? "Opus" : t.contains("alac") ? "ALAC" : t.contains("samr") ? "AMR" : "none/unknown";
            return "Video: " + v + ", audio: " + a + ", " + mb(len);
        } catch (IOException e) { return "could not read the file"; }
    }
    public static JComponent fxPlayer(File f, int w, int h) {
        try { return (JComponent) Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("create", File.class, int.class, int.class).invoke(null, f, w, h); } catch (Throwable t) { return null; }
    }
    public static void fxStop(JComponent c) { try { Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("stop", JComponent.class).invoke(null, c); } catch (Throwable t) { } }


    // ---- thumbnails: a real frame from the video, generated once and cached in ~/.skillswap/cache ----
    static final long THUMB_AUTO_MAX = 25L * 1024 * 1024;          // bigger videos only get a preview after they were opened once
    static final Map<Integer, BufferedImage> thumbs = new ConcurrentHashMap<>();
    static final Set<Integer> thumbBusy = ConcurrentHashMap.newKeySet(), thumbFailed = ConcurrentHashMap.newKeySet();
    static final ExecutorService thumbPool = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "video-thumb"); t.setDaemon(true); return t; });
    static Path videoCacheFile(Post po) { return DIR.resolve("cache").resolve("video_" + po.id + "_" + po.videoName.replaceAll("[^A-Za-z0-9._-]", "_")); }
    static boolean videoCached(Post po) { try { Path f = videoCacheFile(po); return Files.exists(f) && Files.size(f) == po.videoSize; } catch (IOException e) { return false; } }
    /** Loads (or creates) the preview image for a video post, then runs done on the EDT. Returns immediately. */
    public static void thumbnail(Post po, Runnable done) {
        final int id = po.id; if (thumbs.containsKey(id) || thumbFailed.contains(id) || !thumbBusy.add(id)) return;
        final Path png = DIR.resolve("cache").resolve("thumb_" + id + "_" + po.videoSize + ".png");
        thumbPool.submit(() -> {
            try { if (Files.exists(png)) { BufferedImage im = javax.imageio.ImageIO.read(png.toFile()); if (im != null) { thumbs.put(id, im); thumbBusy.remove(id); SwingUtilities.invokeLater(done); return; } } } catch (Exception e) { /* regenerate */ }
            if (!videoCached(po) && po.videoSize > THUMB_AUTO_MAX) { thumbBusy.remove(id); return; }
            SwingUtilities.invokeLater(() -> withVideo(po, file -> thumbPool.submit(() -> {
                BufferedImage im = grabFrame(file);
                if (im == null) thumbFailed.add(id);
                else { thumbs.put(id, im); try { Files.createDirectories(png.getParent()); javax.imageio.ImageIO.write(im, "png", png.toFile()); } catch (Exception e) { /* cache only */ } }
                thumbBusy.remove(id); if (im != null) SwingUtilities.invokeLater(done);
            }), err -> { thumbBusy.remove(id); thumbFailed.add(id); }));
        });
    }
    /** First usable frame of a video: tries ffmpeg (any format) if installed, then the JavaFX player if present. */
    static BufferedImage grabFrame(File video) {
        for (String seek : new String[]{"0.5", "0"}) {
            String ff = ffmpeg(); if (ff == null) break;
            try {
                File tmp = File.createTempFile("ssthumb", ".png"); tmp.delete();
                Process pr = new ProcessBuilder(ff, "-v", "error", "-y", "-ss", seek, "-i", video.getAbsolutePath(), "-frames:v", "1", "-vf", "scale='min(640,iw)':-2", tmp.getAbsolutePath()).redirectErrorStream(true).start();
                pr.getInputStream().readAllBytes();
                if (!pr.waitFor(20, TimeUnit.SECONDS)) pr.destroyForcibly();
                if (tmp.exists()) { BufferedImage im = javax.imageio.ImageIO.read(tmp); tmp.delete(); if (im != null) return im; }
            } catch (Exception e) { break; }     // ffmpeg not installed
        }
        try { return (BufferedImage) Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("thumbnail", File.class, int.class, int.class).invoke(null, video, 640, 360); } catch (Throwable t) { return null; }
    }

    /** Preview box for the New post / New reel dialog: plays the chosen video right there (in-app), so you can see what you are about to upload. */
    public static class Preview extends JPanel {
        JComponent cur; File shown;
        public Preview() {
            super(new GridBagLayout()); setBackground(Color.BLACK); setBorder(new LineBorder(INK, BW));
            Dimension d = new Dimension(364, 207); setPreferredSize(d); setMinimumSize(d); msg("Your video preview appears here");
        }
        void put(JComponent c, boolean fill) { removeAll(); setLayout(fill ? new BorderLayout() : new GridBagLayout()); add(c); revalidate(); repaint(); }
        void msg(String m) { put(lbl("<html><body style='width:300px;text-align:center'>" + esc(m) + "</body></html>", Font.PLAIN, 12, Color.WHITE), false); }
        public void stop() { if (cur != null) { fxStop(cur); cur = null; } }
        public void show(File f) {
            stop(); shown = f; msg("Loading preview...");
            JComponent fx = fxPlayer(f, 360, 203, why -> { if (shown != f) return; stop(); msg("This video can't be previewed or played in the app. " + nz(why).split("\n")[0]); });
            if (fx != null) { cur = fx; put(fx, true); return; }
            msg("The in-app player (JavaFX) is not available. Start the app with scripts\\run-dev.bat.");
            new Thread(() -> { BufferedImage im = grabFrame(f); if (im == null) return;
                SwingUtilities.invokeLater(() -> { if (shown != f) return; JLabel pic = new JLabel(new ImageIcon(im.getScaledInstance(360, -1, Image.SCALE_SMOOTH))); put(pic, false); }); }, "preview-frame").start();
        }
    }

    /** Side panel for the "Choose video" file picker: click a file and its picture, size and format check appear before you pick it. */
    public static class ChooserPreview extends JPanel implements java.beans.PropertyChangeListener {
        static final ExecutorService pool = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "chooser-preview"); t.setDaemon(true); return t; });
        final JPanel stage = new JPanel(new GridBagLayout()); final JLabel info = lbl(" ", Font.BOLD, 11, TXT), warn = lbl(" ", Font.PLAIN, 11, MUTED); final JButton play;
        JComponent cur; File sel; int tok;
        public ChooserPreview(JFileChooser fc) {
            super(new BorderLayout(0, 8)); setBackground(PANEL); setBorder(new EmptyBorder(8, 10, 8, 10)); setPreferredSize(new Dimension(318, 0));
            stage.setBackground(Color.BLACK); stage.setBorder(new LineBorder(INK, BW)); Dimension d = new Dimension(296, 166); stage.setPreferredSize(d); stage.setMinimumSize(d);
            play = ghost("\u25B6  Play preview", () -> playNow()); play.setEnabled(false);
            JPanel top = vbox(); put(top, lbl("Preview", Font.BOLD, 14, TXT)); gap(top, 6); put(top, stage); gap(top, 8); put(top, play);
            JPanel bot = vbox(); gap(bot, 8); put(bot, info); gap(bot, 2); put(bot, warn);
            JPanel col = new JPanel(new BorderLayout()); col.setOpaque(false); col.add(top, BorderLayout.NORTH); col.add(bot, BorderLayout.CENTER); add(col);
            msg("Click a video file to see it here"); fc.addPropertyChangeListener(this);
        }
        void show(JComponent c, boolean fill) { stage.removeAll(); stage.setLayout(fill ? new BorderLayout() : new GridBagLayout()); stage.add(c); stage.revalidate(); stage.repaint(); }
        void msg(String m) { show(lbl("<html><body style='width:250px;text-align:center'>" + esc(m) + "</body></html>", Font.PLAIN, 12, Color.WHITE), false); }
        public void stop() { tok++; if (cur != null) { fxStop(cur); cur = null; } }
        static boolean isVideo(File f) { String n = f.getName().toLowerCase(); for (String x : new String[]{".mp4", ".mov", ".m4v", ".webm", ".mkv", ".avi", ".wmv", ".flv", ".mpg", ".mpeg", ".3gp", ".ts", ".mts", ".m2ts", ".ogv"}) if (f.isFile() && n.endsWith(x)) return true; return false; }
        public void propertyChange(java.beans.PropertyChangeEvent e) {
            String p = e.getPropertyName();
            if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(p)) load((File) e.getNewValue()); else if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(p)) load(null);
        }
        void load(File f) {
            stop(); sel = f; final int my = tok;
            if (f == null || !isVideo(f)) { info.setText(" "); warn.setText(" "); play.setEnabled(false); msg("Click a video file to see it here"); return; }
            info.setText(f.getName() + "  (" + mb(f.length()) + ")"); String prob = playProblem(f); warn.setText(prob == null ? "Plays in the app" : ffmpeg() != null ? "Will be converted automatically when you upload" : "<html><body style='width:262px'>\u26A0 May not play in the app: " + esc(prob.replaceAll("\\s*\\(.*\\)", "")) + "</body></html>");
            play.setEnabled(true); msg("Loading preview...");
            pool.submit(() -> { BufferedImage im = grabFrame(f);
                SwingUtilities.invokeLater(() -> { if (my != tok) return;
                    if (im == null) { msg("No picture available for this file. Press Play preview."); return; }
                    double sc = Math.min(292.0 / im.getWidth(), 166.0 / im.getHeight()); show(new JLabel(new ImageIcon(im.getScaledInstance(Math.max(1, (int) (im.getWidth() * sc)), Math.max(1, (int) (im.getHeight() * sc)), Image.SCALE_SMOOTH))), false); }); });
        }
        void playNow() {
            if (sel == null) return; final File f = sel; tok++; final int my = tok;
            JComponent fx = fxPlayer(f, 292, 166, why -> { if (my != tok) return; stop(); msg("Can't play this video in the app. " + nz(why).split("\n")[0]); });
            if (fx == null) { msg("The in-app player (JavaFX) is not available. Start the app with scripts\\run-dev.bat."); return; }
            cur = fx; show(fx, true);
        }
    }

    // ---- inline player: the video plays inside the post card itself ----
    public static JComponent inlineFx; public static int inlineTok; public static Runnable inlineRestore;
    public static void stopInline() {
        inlineTok++; if (inlineFx != null) { fxStop(inlineFx); inlineFx = null; }
        if (inlineRestore != null) { Runnable r = inlineRestore; inlineRestore = null; r.run(); }
    }
    /** Playback failed: stay in the app, say why, and let the person choose to open their own video player. */
    static void failInline(JPanel holder, File file, String why, Runnable restore) {
        if (inlineFx != null) { fxStop(inlineFx); inlineFx = null; }
        inlineRestore = restore;
        String first = nz(why).split("\n")[0]; if (first.length() > 160) first = first.substring(0, 160) + "...";
        JPanel box = vbox(); put(box, lbl("This video can't be played inside the app.", Font.BOLD, 13, TXT));
        put(box, lbl("<html><body style='width:260px'>" + esc(first) + "<br>Tip: convert it to MP4 with H.264 video + AAC audio, then upload it again.</body></html>", Font.PLAIN, 11, MUTED)); gap(box, 6);
        JPanel bar = row(); bar.add(ghost("Back", Videos::stopInline)); put(box, bar);
        holder.removeAll(); holder.add(box, BorderLayout.WEST); holder.revalidate(); holder.repaint();
    }
    public static JComponent inlineVideo(Post po) {
        JPanel holder = new JPanel(new BorderLayout()); holder.setOpaque(false);
        holder.add(videoTile(po, 260, 146, false, () -> playInline(po.id, holder)), BorderLayout.WEST); return holder;
    }
    public static void playInline(int id, JPanel holder) {
        Post po = postById(id); if (po == null) return;
        stopInline(); final int tok = ++inlineTok;
        Runnable restore = () -> { holder.removeAll(); holder.add(videoTile(po, 260, 146, false, () -> playInline(id, holder)), BorderLayout.WEST); holder.revalidate(); holder.repaint(); };
        inlineRestore = restore;
        holder.removeAll(); holder.add(lbl("Loading video...", Font.PLAIN, 12, MUTED), BorderLayout.WEST); holder.revalidate(); holder.repaint();
        withVideo(po, file -> {
            if (tok != inlineTok) return;
            JComponent fx = fxPlayer(file, 480, 360, msg -> { if (tok == inlineTok) failInline(holder, file, msg, restore); });
            if (fx == null) { failInline(holder, file, "The in-app player (JavaFX) is not available. Start the app with scripts\\run-dev.bat.", restore); return; }
            inlineFx = fx; JPanel box = new JPanel(new BorderLayout(0, 4)); box.setOpaque(false); box.add(fx);
            JPanel bar = row(); bar.add(ghost("\u2715 Close video", Videos::stopInline)); bar.add(lbl("Click the video to pause / resume", Font.PLAIN, 11, MUTED)); box.add(bar, BorderLayout.SOUTH);
            holder.removeAll(); holder.add(box, BorderLayout.WEST); holder.revalidate(); holder.repaint();
        }, err -> { if (tok != inlineTok) return; inlineRestore = null; restore.run(); toast("Could not load video: " + err); });
    }

    public static JComponent videoTile(Post po, int w, int h, boolean meta, Runnable onClick) {
        final Color base = catColor(po.category);
        final String cap = meta ? name(po.author) : "Tap to watch", sub = meta ? "\u2665 " + po.likes.size() + "   \u2022   " + mb(po.videoSize) : mb(po.videoSize);
        JComponent t = new JComponent() {
            { Dimension d = new Dimension(w, h); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); setCursor(new Cursor(Cursor.HAND_CURSOR)); }
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int W = getWidth() - 5, H = getHeight() - 5; BufferedImage im = thumbs.get(po.id);
                g.setColor(INK); g.fillRect(5, 5, W, H); g.setColor(pastel(base)); g.fillRect(0, 0, W, H);
                if (im != null) {   // real video frame, cropped to fill the tile
                    double sc = Math.max((double) W / im.getWidth(), (double) H / im.getHeight()); int dw = (int) Math.ceil(im.getWidth() * sc), dh = (int) Math.ceil(im.getHeight() * sc);
                    Shape old = g.getClip(); g.setClip(0, 0, W, H); g.drawImage(im, (W - dw) / 2, (H - dh) / 2, dw, dh, null); g.setClip(old);
                }
                int bar = 46, vh = H - bar;                       // caption band under the picture
                g.setColor(pastel(base)); g.fillRect(0, vh, W, bar); g.setColor(ONACC); g.fillRect(0, vh, W, 3);
                frame(g, 0, 0, W, H, 3, INK);
                int r = Math.max(14, Math.min(W, vh) / 5), cx = W / 2, cy = vh / 2;
                g.setColor(Color.WHITE); g.fillOval(cx - r, cy - r, 2 * r, 2 * r); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(3f)); g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
                g.fillPolygon(new int[]{cx - r / 3, cx - r / 3, cx + r / 2}, new int[]{cy - r / 2, cy + r / 2, cy}, 3);
                g.setColor(ONACC); g.setFont(f(Font.BOLD, 12)); g.drawString(cap, 12, H - 26); g.setFont(f(Font.PLAIN, 11)); g.drawString(sub, 12, H - 10); g.dispose();
            }
        };
        t.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { onClick.run(); } });
        if (!thumbs.containsKey(po.id)) thumbnail(po, t::repaint);
        return t;
    }

    // ---- full-screen style reel viewer (always dark, like a reels app) ----
}
