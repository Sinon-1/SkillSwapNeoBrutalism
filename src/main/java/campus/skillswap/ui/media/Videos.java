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
    /** Size of the video in a feed post (16:9). Bigger than the old little thumbnail, still fits the card. */
    public static final int FEED_VIDEO_W = 400, FEED_VIDEO_H = 225;
    /** Feed autoplay only downloads videos up to this size; bigger ones stay as thumbnails until clicked. */
    static final long AUTOPLAY_MAX = 12L * 1024 * 1024;
    /** A stuck download or conversion must fail with a readable message instead of hanging forever. */
    static final int DOWNLOAD_TIMEOUT_SECONDS = 120;
    static final long FFMPEG_TIMEOUT_MS = 20L * 60 * 1000;
    public static String[] xfer;                                // host, port, db, user, password: video transfers use their own connection
    /** null if the in-app player (JavaFX) should handle this file; otherwise a short reason why it probably won't. */
    public static String playProblem(File f) {
        String n = f.getName().toLowerCase();
        if (!(n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".mov"))) return "the in-app player only plays MP4 / M4V / MOV files";
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length(); int chunk = (int) Math.min(len, 512L * 1024);
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
    public static Connection openXfer() throws SQLException {
        return open(xfer[0], xfer[1], xfer[2], xfer[3], xfer[4],
                "connectTimeout=10&loginTimeout=10&socketTimeout=" + DOWNLOAD_TIMEOUT_SECONDS);
    }

    /** Upload in the background so the app stays responsive; the video appears on every computer once the upload finishes. */
    public static void uploadVideo(int postId, File f) { if (LanSync.active()) uploadVideoLan(postId, f); else uploadVideoPg(postId, f); }
    static void uploadVideoLan(int postId, File f) {
        final long len = f.length(); toast("Uploading video (" + mb(len) + ")...");
        new Thread(() -> {
            try {
                LanSync.putVideo(postId, f);
                final long stamp = System.currentTimeMillis();
                SwingUtilities.invokeLater(() -> { Post cur = postById(postId); if (cur != null) { cur.videoName = f.getName(); cur.videoSize = len; cur.videoStamp = stamp; saveEdit(); }
                    toast("Your video is live!"); if (me != null && page.equals("Feed") && !videoOpen()) show("Feed"); });
            } catch (Exception e) { SwingUtilities.invokeLater(() -> toast("Video upload failed: " + nz(e.getMessage()).split("\n")[0])); }
        }, "video-upload").start();
    }
    static void uploadVideoPg(int postId, File f) {
        final long len = f.length(); toast("Uploading video (" + mb(len) + ")...");
        new Thread(() -> {
            final long stamp = System.currentTimeMillis();
            try (Connection cn = openXfer(); InputStream in = new BufferedInputStream(new FileInputStream(f));
                 PreparedStatement p = cn.prepareStatement("INSERT INTO post_videos(post_id,file_name,size_bytes,updated_at,data) VALUES(?,?,?,?,?) ON CONFLICT(post_id) DO UPDATE SET file_name=EXCLUDED.file_name,size_bytes=EXCLUDED.size_bytes,updated_at=EXCLUDED.updated_at,data=EXCLUDED.data")) {
                p.setInt(1, postId); p.setString(2, f.getName()); p.setLong(3, len); p.setLong(4, stamp); p.setBinaryStream(5, in, (int) len); p.executeUpdate();
                SwingUtilities.invokeLater(() -> { Post cur = postById(postId);
                    if (cur != null) { cur.videoName = f.getName(); cur.videoSize = len; cur.videoStamp = stamp; ver++; snap.put("p:" + postId, pfp(cur)); }
                    toast("Your video is live!"); if (me != null && page.equals("Feed") && !videoOpen()) show("Feed"); });
            } catch (Exception e) { SwingUtilities.invokeLater(() -> toast("Video upload failed: " + nz(e.getMessage()).split("\n")[0])); }
        }, "video-upload").start();
    }
    /** Download (once) to ~/.skillswap/cache, then hand the local file to the callback on the EDT. */
    /** The file exactly as uploaded (used for thumbnails, which must not trigger a conversion). */
    public static void withRawVideo(Post po, Consumer<File> ok, Consumer<String> fail) { if (LanSync.active()) withVideoLan(po, ok, fail); else withVideoPg(po, ok, fail); }
    /** Like withRawVideo, but anything the in-app player can't decode (MKV, AVI, WebM, HEVC, MOV with odd codecs...) is converted to MP4 first, once, and cached. */
    public static void withVideo(Post po, Consumer<File> ok, Consumer<String> fail) {
        withRawVideo(po, file -> new Thread(() -> {
            try {
                File use = playable(po, file);
                SwingUtilities.invokeLater(() -> { setStatus(" "); ok.accept(use); });
            } catch (Throwable e) {
                // Never hide a failed conversion by handing the original file to the player: it will only fail later,
                // usually with a much less useful message. Tell the person what happened and what they can do.
                final String m = prepareErrorMessage(e);
                SwingUtilities.invokeLater(() -> { setStatus(" "); fail.accept(m); });
            }
        }, "video-prepare").start(), fail);
    }
    /** A readable message for a failed preparation/conversion, including a next step. */
    static String prepareErrorMessage(Throwable e) {
        String m = nz(e.getMessage()).split("\\R")[0].trim();
        if (m.isEmpty()) m = e.getClass().getSimpleName();
        return "Could not prepare this video for playback: " + m
                + " Try another file, or convert it to MP4 (H.264 video + AAC audio) and upload it again.";
    }
    static final Map<Integer, Object> CONV_LOCKS = new ConcurrentHashMap<>();
    static boolean videoCompatible(String[] i) {
        return i != null && "h264".equals(i[0]) && i[1] != null && i[1].matches("yuvj?420p") && i[3] == null && !"avc3".equalsIgnoreCase(i[4]);
    }
    static boolean audioCompatible(String[] i) {
        return i != null && (i[2] == null || ("aac".equals(i[2]) && (i[5] == null || !i[5].toUpperCase(Locale.ROOT).contains("HE-AAC"))));
    }
    /** True when the ffmpeg probe says the player can decode this stream set (H.264 yuv420p + AAC, or no audio). */
    static boolean playerCompatible(String[] i) { return videoCompatible(i) && audioCompatible(i); }
    static boolean needsConversion(File f) {
        String n = f.getName().toLowerCase(); String ext = n.substring(n.lastIndexOf('.') + 1); boolean box = ext.equals("mp4") || ext.equals("m4v") || ext.equals("mov");
        if (ffmpeg() != null) return !(box && playerCompatible(probe(f)));
        if (!box) return true; String d = diagnose(f); return !(d.contains("H.264") && (d.contains("AAC") || d.contains("none")));
    }
    /** Part of every playback cache name: a new upload gets a new stamp even when its size and name stay the same. */
    static String cacheStamp(Post po) { return po.videoStamp > 0 ? Long.toString(po.videoStamp) : "0"; }
    static Path playableCacheFile(Post po) { return DIR.resolve("cache").resolve("play").resolve("v" + po.id + "_" + cacheStamp(po) + "_" + po.videoSize + "_v3.mp4"); }
    static Path repairedCacheFile(Post po) { return DIR.resolve("cache").resolve("play").resolve("v" + po.id + "_" + cacheStamp(po) + "_" + po.videoSize + "_v3_fixed.mp4"); }
    /** Returns a file the player can play: the original if it already is MP4 H.264/AAC, otherwise a cached converted copy. Runs on a background thread. */
    static File playable(Post po, File file) throws Exception { return playable(po, file, false); }
    /**
     * @param force build a clean H.264 + AAC copy even if the file looks compatible. Used as a second chance when
     *              the player rejects a file that the quick format check thought was fine.
     */
    static File playable(Post po, File file, boolean force) throws Exception {
        Path dir = DIR.resolve("cache").resolve("play");
        Path out = playableCacheFile(po);
        Path fixed = repairedCacheFile(po);
        String base = out.getFileName().toString().replaceFirst("\\.mp4$", "");
        if (Files.exists(fixed) && Files.size(fixed) > 0) return fixed.toFile();                 // a previous repair worked: never rebuild it
        if (!force && Files.exists(out) && Files.size(out) > 0) return out.toFile();
        synchronized (CONV_LOCKS.computeIfAbsent(po.id, k -> new Object())) {
            if (Files.exists(fixed) && Files.size(fixed) > 0) return fixed.toFile();
            if (!force && Files.exists(out) && Files.size(out) > 0) return out.toFile();
            if (!force && !needsConversion(file)) return file;
            String ff = ensureFfmpeg(); Files.createDirectories(dir);
            Path target = force ? fixed : out;
            Path tmp = dir.resolve(base + (force ? "_fixed" : "") + ".part.mp4");
            String[] info = probe(file);
            boolean videoOk = !force && videoCompatible(info);
            boolean audioOk = audioCompatible(info);
            List<String> audio = info[2] == null ? Arrays.asList("-an")
                    : (audioOk && !force) ? Arrays.asList("-c:a", "copy")
                    : Arrays.asList("-c:a", "aac", "-b:a", "160k", "-ac", "2");
            List<List<String>> plans = new ArrayList<>();
            if (videoOk) plans.add(Arrays.asList("-c:v", "copy"));
            plans.add(Arrays.asList("-vf", "scale=min(1920\\,iw):min(1920\\,ih):force_original_aspect_ratio=decrease:force_divisible_by=2,format=yuv420p", "-c:v", "libx264", "-preset", "veryfast", "-crf", "22"));
            Exception lastErr = null;
            for (List<String> plan : plans) {
                List<String> cmd = new ArrayList<>(Arrays.asList(ff, "-y", "-i", file.getAbsolutePath(), "-map", "0:v:0", "-map", "0:a:0?")); cmd.addAll(plan); cmd.addAll(audio);
                cmd.addAll(Arrays.asList("-movflags", "+faststart", "-progress", "pipe:1", "-nostats", tmp.toString()));
                try { runFfmpeg(cmd, pct -> SwingUtilities.invokeLater(() -> setStatus("Preparing video for playback... " + pct + "%"))); }
                catch (Exception e) { lastErr = e; continue; }
                if (Files.exists(tmp) && Files.size(tmp) > 0) {
                    if (!playerCompatible(probe(tmp.toFile()))) {
                        lastErr = new IOException("ffmpeg produced a file the player cannot decode (" + diagnose(tmp.toFile()) + ").");
                        continue;
                    }
                    moveReplacing(tmp, target); SwingUtilities.invokeLater(() -> setStatus(" ")); return target.toFile();
                }
            }
            Files.deleteIfExists(tmp);
            throw lastErr != null ? lastErr : new IOException("ffmpeg could not convert this file.");
        }
    }
    /** Windows keeps a file locked while the player still has it open; wait briefly for the old copy to be released. */
    static void moveReplacing(Path from, Path to) throws IOException {
        for (int i = 0; ; i++) {
            try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); return; }
            catch (IOException e) {
                if (i >= 19) throw e;
                try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
            }
        }
    }
    /** Second chance for a file the JavaFX player rejected: always rebuild it, then try playback again. */
    static File repaired(Post po, File file) throws Exception { return playable(po, file, true); }
    /** ffmpeg path; on Windows it is downloaded automatically (once, ~30 MB) into ~/.skillswap if missing. Other systems: install it with brew / apt. */
    static synchronized String ensureFfmpeg() throws IOException {
        String ff = ffmpeg(); if (ff != null) return ff;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) throw new IOException("ffmpeg is not installed. Install it (macOS: brew install ffmpeg, Linux: sudo apt install ffmpeg) and restart SkillSwap.");
        SwingUtilities.invokeLater(() -> toast("Downloading the media converter (ffmpeg, about 30 MB) - one time only..."));
        Files.createDirectories(DIR); Path zip = DIR.resolve("ffmpeg-download.zip");
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip").openConnection();
            c.setInstanceFollowRedirects(true); c.setConnectTimeout(15000); c.setReadTimeout(60000); long len = c.getContentLengthLong(), got = 0; int last = -1;
            int code = c.getResponseCode();
            if (code != java.net.HttpURLConnection.HTTP_OK) throw new IOException("the download server returned HTTP " + code);
            try (InputStream in = c.getInputStream(); OutputStream o = Files.newOutputStream(zip)) {
                byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) { o.write(b, 0, n); got += n; int pct = len > 0 ? (int) (got * 100 / len) : -1; if (pct != last) { last = pct; final int pp = pct; SwingUtilities.invokeLater(() -> setStatus("Downloading media converter... " + (pp >= 0 ? pp + "%" : ""))); } }
            }
            if (len > 0 && got != len) throw new IOException("the download stopped after " + mb(got) + " of " + mb(len));
            boolean copied = false;
            try (java.util.zip.ZipInputStream zi = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
                java.util.zip.ZipEntry e; while ((e = zi.getNextEntry()) != null) if (e.getName().toLowerCase().endsWith("/bin/ffmpeg.exe")) { Files.copy(zi, DIR.resolve("ffmpeg.exe"), StandardCopyOption.REPLACE_EXISTING); copied = true; break; }
            }
            if (!copied) throw new IOException("the downloaded archive did not contain ffmpeg.exe");
        } catch (IOException e) { throw new IOException("Could not download ffmpeg (" + nz(e.getMessage()) + "). Check your internet connection, or run scripts\\get-ffmpeg.bat."); }
        finally { Files.deleteIfExists(zip); SwingUtilities.invokeLater(() -> setStatus(" ")); }
        ffmpegExe = null; ffmpegLastMiss = 0; ff = ffmpeg();
        if (ff == null) throw new IOException("ffmpeg could not be installed. Run scripts\\get-ffmpeg.bat or: winget install Gyan.FFmpeg");
        return ff;
    }
    static final Map<Integer, Object> DL_LOCKS = new ConcurrentHashMap<>();
    /** True while a video player is on screen (inline or full-screen reels): incoming LAN/DB changes must not rebuild the UI then. */
    public static boolean videoOpen() { return inlineLoading || inlineFx != null || feedFx != null || Reels.reelDlg != null; }
    static String downloadErrorMessage(Throwable e) {
        String m = nz(e.getMessage()).split("\\R")[0].trim();
        String low = m.toLowerCase(Locale.ROOT);
        boolean timeout = e instanceof java.net.SocketTimeoutException || e instanceof java.sql.SQLTimeoutException
                || low.contains("timed out") || low.contains("timeout");
        if (timeout) return "The video download timed out. Check your connection and try again" + (m.isEmpty() ? "." : " (" + m + ")");
        return "Could not download the video: " + (m.isEmpty() ? "no details were returned. Try again." : m);
    }
    static void withVideoLan(Post po, Consumer<File> ok, Consumer<String> fail) {
        final int id = po.id; final long size = po.videoSize;
        final Path dir = DIR.resolve("cache"), file = videoCacheFile(po);
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
                } catch (Throwable e) { final String m = downloadErrorMessage(e); SwingUtilities.invokeLater(() -> fail.accept(m)); }
            }
        }, "video-download").start();
    }
    static void withVideoPg(Post po, Consumer<File> ok, Consumer<String> fail) {
        final int id = po.id; final long size = po.videoSize;
        final Path dir = DIR.resolve("cache"), file = videoCacheFile(po);
        try { if (Files.exists(file) && Files.size(file) == size) { ok.accept(file.toFile()); return; } } catch (IOException e) { /* download again */ }
        new Thread(() -> {
            synchronized (DL_LOCKS.computeIfAbsent(id, k -> new Object())) {       // one download per video: thumbnail + play used to overwrite the same file
                try {
                    Files.createDirectories(dir);
                    if (!(Files.exists(file) && Files.size(file) == size)) {
                        Path tmp = dir.resolve(file.getFileName() + "." + System.nanoTime() + ".part");
                        try {
                            try (Connection cn = openXfer(); PreparedStatement p = cn.prepareStatement("SELECT data FROM post_videos WHERE post_id=?")) {
                                p.setInt(1, id);
                                try (ResultSet r = p.executeQuery()) {
                                    if (!r.next()) throw new IOException("Video not found on the server");
                                    try (InputStream in = r.getBinaryStream(1); OutputStream out = Files.newOutputStream(tmp)) {
                                        byte[] buf = new byte[1024 * 1024]; int n;
                                        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                                    }
                                }
                            }
                            long got = Files.size(tmp);
                            if (size > 0 && got != size) throw new IOException("Video download was incomplete (" + mb(got) + " of " + mb(size) + "). Try again.");
                            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                        } finally { try { Files.deleteIfExists(tmp); } catch (IOException e) { } }
                    }
                    SwingUtilities.invokeLater(() -> ok.accept(file.toFile()));
                } catch (Exception e) { final String m = downloadErrorMessage(e); SwingUtilities.invokeLater(() -> fail.accept(m)); }
            }
        }, "video-download").start();
    }
    static boolean playerWarm;
    /** Warms the optional JavaFX player after the feed is drawn, so the first tap is not stuck starting JavaFX. */
    public static void prewarmPlayer() {
        if (playerWarm) return;
        playerWarm = true;
        SwingUtilities.invokeLater(() -> {
            try { Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("prewarm").invoke(null); } catch (Throwable ignored) { }
        });
    }
    /** In-app player: only available when VideoPlayerFX.class (and JavaFX) are present; otherwise null and the app shows a message. */
    public static JComponent fxPlayer(File f, int w, int h, Consumer<String> onFail) { return fxPlayer(f, w, h, onFail, false, null); }
    /** Same, but a player that starts muted (feed autoplay) so scrolling never blasts sound. */
    public static JComponent fxPlayer(File f, int w, int h, Consumer<String> onFail, boolean startMuted) { return fxPlayer(f, w, h, onFail, startMuted, null); }
    /** @param onFullscreen when not null, the player shows a full-screen button that runs this. */
    public static JComponent fxPlayer(File f, int w, int h, Consumer<String> onFail, boolean startMuted, Runnable onFullscreen) {
        Consumer<String> wrapped = why -> onFail.accept(friendly(why) + "  [" + diagnose(f) + "]");
        try { return (JComponent) Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("create", File.class, int.class, int.class, Consumer.class, boolean.class, Runnable.class).invoke(null, f, w, h, wrapped, startMuted, onFullscreen); } catch (Throwable t) { return null; }
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
    static boolean ffmpegWarm;
    /** Checks for ffmpeg in the background so opening a feed or the New Post dialog never waits on a process scan. */
    public static void prewarmFfmpeg() {
        if (ffmpegWarm) return;
        ffmpegWarm = true;
        Thread t = new Thread(() -> ffmpeg(), "ffmpeg-check"); t.setDaemon(true); t.start();
    }
    /** What is inside a video, read with ffmpeg: {video codec, pixel format, audio codec or null, "rotated" or null, video tag, audio profile}. */
    static String[] probe(File f) {
        String[] r = {null, null, null, null, null, null};
        try {
            Process pr = new ProcessBuilder(ffmpeg(), "-hide_banner", "-i", f.getAbsolutePath()).redirectErrorStream(true).start();
            String out = new String(pr.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!pr.waitFor(15, TimeUnit.SECONDS)) pr.destroyForcibly();
            for (String line : out.split("\\R")) {
                if (r[0] == null && line.contains("Video:") && !line.contains("attached pic")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("Video: (\\w+)").matcher(line); if (m.find()) r[0] = m.group(1);
                    m = java.util.regex.Pattern.compile("\\b(yuvj?\\d{3}p\\d*(?:le|be)?|nv12|p010le|gbrp\\w*|rgb\\w*|bgr\\w*|pal8)\\b").matcher(line); if (m.find()) r[1] = m.group(1);
                    m = java.util.regex.Pattern.compile("\\((\\w{4})\\s*/").matcher(line); if (m.find()) r[4] = m.group(1);   // avc1 / avc3 / hvc1 ...
                }
                if (r[2] == null && line.contains("Audio:")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("Audio: (\\w+)").matcher(line); if (m.find()) r[2] = m.group(1);
                    m = java.util.regex.Pattern.compile("Audio: \\w+ \\(([^)]+)\\)").matcher(line); if (m.find()) r[5] = m.group(1);   // LC / HE-AACv2 ...
                }
                if (line.contains("displaymatrix") || line.matches("(?i).*\\brotate\\s*:\\s*(90|180|270).*")) r[3] = "rotated";
            }
        } catch (Exception e) { /* unknown: re-encode to be safe */ }
        return r;
    }
    static void runFfmpeg(List<String> cmd, Consumer<Integer> progress) throws Exception { runFfmpeg(cmd, progress, FFMPEG_TIMEOUT_MS); }
    /** Runs ffmpeg, draining its output and killing it when it stops making progress for too long. */
    static void runFfmpeg(List<String> cmd, Consumer<Integer> progress, long timeoutMs) throws Exception {
        Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        final java.util.concurrent.atomic.AtomicReference<String> last = new java.util.concurrent.atomic.AtomicReference<>("");
        final java.util.concurrent.atomic.AtomicReference<Throwable> readError = new java.util.concurrent.atomic.AtomicReference<>();
        Thread reader = new Thread(() -> {
            double dur = 0;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line; java.util.regex.Pattern dp = java.util.regex.Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
                while ((line = r.readLine()) != null) {
                    if (!line.contains("=")) last.set(line);
                    java.util.regex.Matcher m = dp.matcher(line); if (dur == 0 && m.find()) dur = Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Double.parseDouble(m.group(3));
                    if (line.startsWith("out_time_ms=") && dur > 0) { try { progress.accept((int) Math.max(0, Math.min(99, Long.parseLong(line.substring(12).trim()) / 1e6 / dur * 100))); } catch (NumberFormatException e) { /* N/A */ } }
                }
            } catch (IOException e) { readError.set(e); }
        }, "ffmpeg-output");
        reader.setDaemon(true); reader.start();
        if (!pr.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            pr.destroyForcibly();
            try { pr.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            try { reader.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            String tail = last.get().isEmpty() ? "" : " Last message: " + last.get();
            String limit = timeoutMs >= 60000 ? (timeoutMs / 60000) + " minutes" : Math.max(1, timeoutMs / 1000) + " seconds";
            throw new IOException("ffmpeg did not finish within " + limit + ". A very long or damaged video can cause this - trim it or try a different file." + tail);
        }
        try { reader.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (readError.get() != null) throw new IOException("Could not read ffmpeg output: " + nz(readError.get().getMessage()));
        if (pr.exitValue() != 0) {
            String tail = last.get().isEmpty() ? "" : " " + last.get();
            throw new IOException("ffmpeg could not convert this file (exit code " + pr.exitValue() + ")." + tail);
        }
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
        // A unique output name lets two uploads with the same file name convert at the same time without overwriting each other.
        File out = dir.resolve(base + "-" + System.nanoTime() + ".mp4").toFile();
        String[] info = probe(in);
        boolean videoOk = videoCompatible(info);      // 8-bit H.264, not rotated by metadata
        boolean audioOk = audioCompatible(info);
        List<String> audio = info[2] == null ? Arrays.asList("-an") : audioOk ? Arrays.asList("-c:a", "copy") : Arrays.asList("-c:a", "aac", "-b:a", "192k", "-ac", "2");
        List<List<String>> plans = new ArrayList<>();
        if (videoOk) plans.add(Arrays.asList("-c:v", "copy"));
        for (int[] q : new int[][]{{18, 1920}, {22, 1920}, {26, 1280}, {30, 1280}})
            plans.add(Arrays.asList("-vf", "scale=min(" + q[1] + "\\,iw):min(" + q[1] + "\\,ih):force_original_aspect_ratio=decrease:force_divisible_by=2,format=yuv420p", "-c:v", "libx264", "-preset", "fast", "-crf", "" + q[0]));
        Exception lastErr = null; boolean tooBig = false;
        for (List<String> plan : plans) {
            List<String> cmd = new ArrayList<>(Arrays.asList(ff, "-y", "-i", in.getAbsolutePath(), "-map", "0:v:0", "-map", "0:a:0?")); cmd.addAll(plan); cmd.addAll(audio);
            cmd.addAll(Arrays.asList("-movflags", "+faststart", "-progress", "pipe:1", "-nostats", out.getAbsolutePath()));
            try { runFfmpeg(cmd, progress); } catch (Exception e) { lastErr = e; continue; }
            if (out.exists() && out.length() > 0 && out.length() <= MAX_VIDEO) {
                String[] produced = probe(out);
                if (playerCompatible(produced)) { progress.accept(100); return out; }
                lastErr = new IOException("ffmpeg produced a file the app cannot play (" + diagnose(out) + ").");
                continue;
            }
            tooBig = out.exists() && out.length() > MAX_VIDEO;
        }
        try { Files.deleteIfExists(out.toPath()); } catch (IOException e) { /* cleanup only */ }
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
            } catch (Exception e) {
                String m = nz(e.getMessage()).split("\\R")[0].trim();
                SwingUtilities.invokeLater(() -> { setStatus(" "); toast("Video conversion failed: " + (m.isEmpty() ? "unknown error." : m + " Try a shorter clip, or convert it to MP4 (H.264 video + AAC audio) first.")); });
            }
        }, "video-convert").start();
    }

    /** Plain-English version of a JavaFX media error. */
    static String friendly(String why) {
        String w = nz(why);
        if (w.contains("MEDIA_INVALID")) return "The player could not decode this file (its video/audio format is not supported, or the file is damaged).";
        if (w.contains("UNSUPPORTED")) return "This video format is not supported by the player.";
        if (w.contains("PLAYBACK_ERROR") || w.contains("PLAYBACK_HALTED")) return "The player stopped while playing this file - it may be damaged or use an unsupported audio/video stream.";
        if (w.contains("INACCESSIBLE") || w.contains("IO")) return "The video file could not be read.";
        return w.split("\n")[0];
    }
    /** Looks inside an MP4/MOV and reports what it contains, e.g. "Video: H.264, audio: AAC", or what is wrong with the file. */
    public static String diagnose(File f) {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length(); if (len < 16) return "file is empty or too small";
            int chunk = (int) Math.min(len, 512L * 1024); byte[] head = new byte[chunk], tail = new byte[chunk];
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
    /** Sends a command ("toggle", "back", "fwd", "mute") to the in-app player's controls. */
    public static void fxCmd(JComponent c, String cmd) { try { Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("command", JComponent.class, String.class).invoke(null, c, cmd); } catch (Throwable t) { } }
    public static void fxStop(JComponent c) { try { Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("stop", JComponent.class).invoke(null, c); } catch (Throwable t) { } }


    // ---- thumbnails: a real frame from the video, generated once and cached in ~/.skillswap/cache ----
    static final long THUMB_AUTO_MAX = AUTOPLAY_MAX;               // bigger videos only get a preview after they were opened once (saves data)
    /** Recently used video previews (LRU, capped so a long session cannot keep every frame in memory). */
    static final Map<String, BufferedImage> thumbs = Collections.synchronizedMap(new LinkedHashMap<String, BufferedImage>(64, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) { return size() > 80; }
    });
    static final Set<String> thumbBusy = ConcurrentHashMap.newKeySet(), thumbFailed = ConcurrentHashMap.newKeySet();
    static final ExecutorService thumbPool = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "video-thumb"); t.setDaemon(true); return t; });
    static String thumbKey(Post po) { return po.id + "_" + cacheStamp(po); }
    static Path videoCacheFile(Post po) { return DIR.resolve("cache").resolve("video_" + po.id + "_" + cacheStamp(po) + "_" + po.videoName.replaceAll("[^A-Za-z0-9._-]", "_")); }
    static boolean videoCached(Post po) { try { Path f = videoCacheFile(po); return Files.exists(f) && Files.size(f) == po.videoSize; } catch (IOException e) { return false; } }

    // ---- smooth playback copy: a small, fast-start MP4 made once while the video is being prefetched ----
    /** One conversion at a time so a feed full of videos cannot peg the CPU. */
    static final ExecutorService feedPrepPool = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "video-smooth"); t.setDaemon(true); return t; });
    static final Map<Integer, Object> FEED_PREP_LOCKS = new ConcurrentHashMap<>();
    static Path feedCopyFile(Post po) { return DIR.resolve("cache").resolve("play").resolve("feed_" + po.id + "_" + cacheStamp(po) + "_" + po.videoSize + ".mp4"); }
    static File feedCopy(Post po) {
        try { Path p = feedCopyFile(po); return Files.exists(p) && Files.size(p) > 0 ? p.toFile() : null; }
        catch (IOException e) { return null; }
    }
    /** True for files the in-app player is unlikely to decode (MKV, HEVC, AV1, odd MOV codecs...). */
    static boolean needsEarlyPrep(File f) {
        String n = f.getName().toLowerCase();
        boolean box = n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".mov");
        return !box || playProblem(f) != null;
    }
    /** Builds a 720p-ish H.264/AAC copy with the index at the front; that is what fixes freezing/errors on high-bitrate uploads. */
    static File makeFeedCopy(Post po, File raw) throws Exception {
        File ready = feedCopy(po); if (ready != null) return ready;
        String ff = ffmpeg();
        if (ff == null) throw new IOException("This video needs converting to MP4 before it can play, but ffmpeg is not installed. Run scripts\\get-ffmpeg.bat (Windows) or install ffmpeg, then try again.");
        synchronized (FEED_PREP_LOCKS.computeIfAbsent(po.id, k -> new Object())) {
            ready = feedCopy(po); if (ready != null) return ready;
            Path out = feedCopyFile(po), tmp = out.resolveSibling(out.getFileName() + ".part.mp4");
            Files.createDirectories(out.getParent());
            String[] info = probe(raw);
            List<String> cmd = new ArrayList<>(Arrays.asList(ff, "-y", "-i", raw.getAbsolutePath(), "-map", "0:v:0", "-map", "0:a:0?",
                    "-vf", "scale=min(1280\\,iw):min(1280\\,ih):force_original_aspect_ratio=decrease:force_divisible_by=2,format=yuv420p",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "23"));
            if (info[2] == null) cmd.add("-an");
            else cmd.addAll(Arrays.asList("-c:a", "aac", "-b:a", "128k", "-ac", "2"));
            cmd.addAll(Arrays.asList("-movflags", "+faststart", "-progress", "pipe:1", "-nostats", tmp.toString()));
            try { runFfmpeg(cmd, pct -> { }); } catch (Exception e) { Files.deleteIfExists(tmp); throw e; }
            if (!Files.exists(tmp) || Files.size(tmp) == 0) { Files.deleteIfExists(tmp); throw new IOException("ffmpeg did not produce a playable copy of this video."); }
            if (!playerCompatible(probe(tmp.toFile()))) {
                String problem = diagnose(tmp.toFile());
                Files.deleteIfExists(tmp);
                throw new IOException("The prepared copy is still not in a format the player supports (" + problem + ").");
            }
            moveReplacing(tmp, out);
            return out.toFile();
        }
    }
    /** Called by prefetch: prepares the smooth copy before the person presses play. */
    static void prepareFeedAsync(Post po, File raw) {
        if (po == null || raw == null || ffmpeg() == null || videoOpen() || feedCopy(po) != null) return;
        feedPrepPool.submit(() -> {
            if (videoOpen()) return;                                     // do not steal CPU while a video is playing
            try { makeFeedCopy(po, raw); } catch (Throwable e) { /* optional prefetch; playback prepares again and reports errors */ }
        });
    }
    /**
     * Gets the best file for watch-in-app playback. Uses the smooth copy when it exists; waits for it only when
     * the original is a format the player probably cannot read, so normal MP4s still start immediately.
     */
    static void withPlaybackVideo(Post po, Consumer<File> ok, Consumer<String> fail) {
        withRawVideo(po, raw -> {
            File ready = feedCopy(po);
            if (ready != null || !needsEarlyPrep(raw)) { ok.accept(ready != null ? ready : raw); return; }
            SwingUtilities.invokeLater(() -> setStatus("Preparing video for playback..."));
            feedPrepPool.submit(() -> {
                try {
                    File made = makeFeedCopy(po, raw);
                    SwingUtilities.invokeLater(() -> { setStatus(" "); ok.accept(made); });
                } catch (Throwable e) {
                    // Do not silently play the original file here: it is exactly the file the player cannot read.
                    final String m = prepareErrorMessage(e);
                    SwingUtilities.invokeLater(() -> { setStatus(" "); fail.accept(m); });
                }
            });
        }, fail);
    }
    /** Loads (or creates) the preview image for a video post, then runs done on the EDT. Returns immediately. */
    public static void thumbnail(Post po, Runnable done) {
        final String key = thumbKey(po);
        if (thumbs.containsKey(key) || thumbFailed.contains(key) || !thumbBusy.add(key)) return;
        final Path png = DIR.resolve("cache").resolve("thumb_" + key + "_" + po.videoSize + ".png");
        thumbPool.submit(() -> {
            try { if (Files.exists(png)) { BufferedImage im = javax.imageio.ImageIO.read(png.toFile()); if (im != null) { thumbs.put(key, im); thumbBusy.remove(key); SwingUtilities.invokeLater(done); return; } } } catch (Exception e) { /* regenerate */ }
            if (!videoCached(po) && po.videoSize > THUMB_AUTO_MAX) { thumbBusy.remove(key); return; }
            SwingUtilities.invokeLater(() -> withRawVideo(po, file -> thumbPool.submit(() -> {
                BufferedImage im = grabFrame(file);
                if (im == null) thumbFailed.add(key);
                else { thumbs.put(key, im); try { Files.createDirectories(png.getParent()); javax.imageio.ImageIO.write(im, "png", png.toFile()); } catch (Exception e) { /* cache only */ } }
                thumbBusy.remove(key); if (im != null) SwingUtilities.invokeLater(done);
            }), err -> { thumbBusy.remove(key); thumbFailed.add(key); }));
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
    public static JComponent inlineFx; public static int inlineTok; public static Runnable inlineRestore; public static boolean inlineLoading;
    /** The post card that currently owns the inline player (its feed tile/autoplay must leave it alone). */
    public static JPanel inlineHolder;
    public static void stopInline() {
        inlineTok++; inlineLoading = false; inlineHolder = null; if (inlineFx != null) { fxStop(inlineFx); inlineFx = null; }
        if (inlineRestore != null) { Runnable r = inlineRestore; inlineRestore = null; r.run(); }
        stopFeedAutoplay();
    }

    // ---- feed autoplay: whichever post's video is on screen plays by itself (muted) while scrolling ----
    static final List<FeedVideo> feedVideos = new ArrayList<>();
    static FeedVideo feedPlaying, feedFxOwner; static JScrollPane feedScroll; static Timer feedTimer; static JComponent feedFx;
    /** Large feed videos are downloaded quietly while visible, so pressing play does not wait for the download. */
    static final Set<Integer> PREFETCHING = ConcurrentHashMap.newKeySet();
    /** Keep background prefetching gentle: at most two videos are fetched ahead of the viewer. */
    static final Semaphore PREFETCH_SLOTS = new Semaphore(2, true);
    /** Called when the feed is rebuilt: forgets the old tiles and stops any autoplay. */
    public static void resetFeedAutoplay() { stopFeedAutoplay(); feedVideos.clear(); feedScroll = null; }
    /** Watches the feed scroll position; when scrolling pauses, the most visible video starts (muted). */
    public static void autoplayFeed(JScrollPane sp) {
        if (sp == null) return;
        prewarmPlayer();
        feedScroll = sp;
        sp.getVerticalScrollBar().addAdjustmentListener(e -> feedSoon());
        sp.getViewport().addComponentListener(new ComponentAdapter() { public void componentResized(ComponentEvent e) { feedSoon(); } });
        feedSoon();
    }
    static void feedSoon() {
        if (feedTimer == null) { feedTimer = new Timer(180, e -> feedTick()); feedTimer.setRepeats(false); }
        feedTimer.restart();
    }
    static void feedTick() {
        if (feedScroll == null || feedVideos.isEmpty() || !feedScroll.isShowing()) return;
        if (inlineFx != null || inlineLoading || Reels.reelDlg != null) return;       // a deliberate player is open
        Rectangle vis = feedScroll.getViewport().getViewRect(); if (vis.isEmpty()) return;
        FeedVideo best = null; double bestRatio = 0;
        for (FeedVideo v : feedVideos) {
            if (v.failed || !v.holder.isShowing() || v.holder == inlineHolder) continue;
            Point at = SwingUtilities.convertPoint(v.holder, 0, 0, feedScroll.getViewport());
            Rectangle r = new Rectangle(at, v.holder.getSize()), in = r.intersection(vis);
            if (in.isEmpty()) continue;
            prefetch(v.post);                       // keep videos near the viewport ready without a flood of downloads
            double ratio = (double) in.width * in.height / Math.max(1, r.width * r.height);
            if (ratio > bestRatio) { bestRatio = ratio; best = v; }
        }
        if (best == null || bestRatio < 0.55) { stopFeedAutoplay(); return; }
        if (best == feedPlaying) return;
        stopFeedAutoplay(); feedPlaying = best; best.playMuted();
    }
    /** Stops the autoplaying feed video (the thumbnail comes back) without forgetting the tiles. */
    public static void stopFeedAutoplay() {
        feedPlaying = null;
        for (FeedVideo v : feedVideos) v.stop();
        if (feedFx != null) { fxStop(feedFx); feedFx = null; feedFxOwner = null; }
    }
    /** Caches a video in the background while it is on screen; playback then starts from the local file at once. */
    static void prefetch(Post po) {
        if (po == null || videoOpen() || videoCached(po) || !PREFETCHING.add(po.id)) return;
        if (!PREFETCH_SLOTS.tryAcquire()) { PREFETCHING.remove(po.id); return; }
        Runnable done = () -> { PREFETCHING.remove(po.id); PREFETCH_SLOTS.release(); };
        withRawVideo(po, f -> { prepareFeedAsync(po, f); done.run(); }, err -> done.run());
    }
    /** A post's video tile in the feed, which can swap itself for a muted player. */
    static class FeedVideo {
        final Post post; final JPanel holder; JComponent fx; boolean loading, failed; int tok;
        FeedVideo(Post post, JPanel holder) { this.post = post; this.holder = holder; }
        void showTile() {
            holder.removeAll(); holder.add(videoTile(post, FEED_VIDEO_W, FEED_VIDEO_H, false, () -> playInline(post.id, holder)), BorderLayout.WEST);
            holder.revalidate(); holder.repaint();
        }
        void playMuted() {
            if (fx != null || loading) return;
            if (!videoCached(post) && needsTapToPlay(post)) { prefetch(post); return; }   // don't autoplay huge reels; the tile says to tap and prepares them when tapped
            loading = true; final int my = ++tok;
            withPlaybackVideo(post, file -> {
                loading = false;
                if (my != tok || feedPlaying != this) return;
                // Do not play muted if an inline player is active in the same holder
                if (inlineHolder != null && inlineHolder == holder) { failed(); return; }
                JComponent p = fxPlayer(file, Math.min(FEED_VIDEO_W, holder.getWidth()), Math.min(FEED_VIDEO_H, holder.getHeight()), msg -> { if (my == tok) failed(); }, true,
                        () -> { stopFeedAutoplay(); openReels(post.id); });
                if (p == null) { failed(); return; }
                fx = p; feedFx = p; feedFxOwner = this;
                holder.removeAll(); holder.add(p, BorderLayout.WEST); holder.revalidate(); holder.repaint();
            }, err -> { if (my == tok) { loading = false; failed(); } });
        }
        void failed() { failed = true; if (feedPlaying == this) feedPlaying = null; stop(); }
        void stop() {
            tok++; loading = false;
            if (fx != null) { fxStop(fx); fx = null; if (holder.isShowing()) showTile(); }
            if (feedFxOwner == this) { feedFx = null; feedFxOwner = null; }
        }
    }
    /** Playback failed: stay in the app, say why, and let the person choose to open their own video player. */
    static void failInline(JPanel holder, File file, String why, Runnable restore) {
        inlineLoading = false; if (inlineFx != null) { fxStop(inlineFx); inlineFx = null; }
        inlineRestore = restore;
        String first = nz(why).split("\n")[0]; if (first.length() > 160) first = first.substring(0, 160) + "...";
        JPanel box = vbox(); put(box, lbl("This video can't be played inside the app.", Font.BOLD, 13, TXT));
        put(box, lbl("<html><body style='width:260px'>" + esc(first) + "<br>Tip: convert it to MP4 with H.264 video + AAC audio, then upload it again.</body></html>", Font.PLAIN, 11, MUTED)); gap(box, 6);
        JPanel bar = row(); bar.add(ghost("Back", Videos::stopInline)); put(box, bar);
        holder.removeAll(); holder.add(box, BorderLayout.WEST); holder.revalidate(); holder.repaint();
    }
    public static JComponent inlineVideo(Post po) {
        JPanel holder = new JPanel(new BorderLayout()); holder.setOpaque(false);
        FeedVideo v = new FeedVideo(po, holder); v.showTile();
        feedVideos.add(v);
        // Do not fetch every video in the feed: the scroll listener and hover prefetch only the ones near the viewer.
        return holder;
    }
    public static void playInline(int id, JPanel holder) {
        Post po = postById(id); if (po == null) return;
        stopInline(); final int tok = ++inlineTok; inlineLoading = true;
        Runnable restore = () -> { holder.removeAll(); holder.add(videoTile(po, FEED_VIDEO_W, FEED_VIDEO_H, false, () -> playInline(id, holder)), BorderLayout.WEST); holder.revalidate(); holder.repaint(); };
        inlineRestore = restore;
        holder.removeAll(); holder.add(lbl("Loading video...", Font.PLAIN, 12, MUTED), BorderLayout.WEST); holder.revalidate(); holder.repaint();
        withPlaybackVideo(po, file -> {
            if (tok != inlineTok) return;
            showInline(holder, po, file, tok, restore, false);
        }, err -> { if (tok != inlineTok) return; inlineLoading = false; failInline(holder, null, err, restore); });
    }
    /** Puts the player in the post card. If the player can't decode the file, repair it once and retry automatically. */
    static void showInline(JPanel holder, Post po, File file, int tok, Runnable restore, boolean repaired) {
        if (tok != inlineTok) return;
        // Size the player like the post's video tile (400x225, 16:9) so the feed video is not tiny anymore.
        // holder.getWidth() can still be 0 before the first layout: fall back to the tile width.
        int avail = holder.getWidth();
        int vw = Math.min(avail > 0 ? avail : FEED_VIDEO_W, FEED_VIDEO_W);
        int vh = Math.round(vw * (float) FEED_VIDEO_H / FEED_VIDEO_W);
        final boolean[] failed = {false};
        JComponent fx = fxPlayer(file, vw, vh, msg -> {
            if (tok != inlineTok || failed[0]) return;
            failed[0] = true;
            if (inlineFx != null) { fxStop(inlineFx); inlineFx = null; }
            if (repaired) failInline(holder, file, msg, restore);
            else repairInline(holder, po, file, tok, restore, msg);
        }, false, () -> { stopInline(); openReels(po.id); });
        if (fx == null) { failInline(holder, file, "The in-app player (JavaFX) is not available. Start the app with scripts\\run-dev.bat.", restore); return; }
        inlineLoading = false; inlineFx = fx;
        // Track which holder has the inline player so autoplay/other tiles stay clear
        inlineHolder = holder;
        JPanel box = new JPanel(new BorderLayout(0, 4)); box.setOpaque(false); box.add(fx);
        JPanel bar = row(); bar.add(ghost("\u2715 Close video", Videos::stopInline)); bar.add(ghost("Full screen", () -> { stopInline(); openReels(po.id); })); bar.add(lbl("Click to pause  \u2022  double-click sides to skip 10s", Font.PLAIN, 11, MUTED)); box.add(bar, BorderLayout.SOUTH);
        holder.removeAll(); holder.add(box, BorderLayout.WEST); holder.revalidate(); holder.repaint();
    }
    /** The player rejected a file: re-encode it to a clean H.264 + AAC MP4 in the background, then try once more. */
    static void repairInline(JPanel holder, Post po, File file, int tok, Runnable restore, String why) {
        inlineLoading = true;
        holder.removeAll(); holder.add(lbl("Fixing this video so it plays...", Font.PLAIN, 12, MUTED), BorderLayout.WEST); holder.revalidate(); holder.repaint();
        new Thread(() -> {
            try {
                File fixed = repaired(po, file);
                SwingUtilities.invokeLater(() -> { if (tok == inlineTok) showInline(holder, po, fixed, tok, restore, true); });
            } catch (Throwable e) {
                String m = prepareErrorMessage(e);
                SwingUtilities.invokeLater(() -> { if (tok == inlineTok) failInline(holder, file, why + " " + m, restore); });
            }
        }, "video-repair").start();
    }

    /** Large videos are not downloaded/autoplayed just because they scrolled into view; the tile says so and starts playback when tapped. */
    static boolean needsTapToPlay(Post po) { return po != null && po.videoSize > AUTOPLAY_MAX; }
    static String feedTileCaption(Post po) { return needsTapToPlay(po) ? "Tap to play (large video)" : "Tap to watch"; }
    public static JComponent videoTile(Post po, int w, int h, boolean meta, Runnable onClick) {
        final Color base = catColor(po.category);
        final String cap = meta ? name(po.author) : feedTileCaption(po);
        final String sub = meta ? "\u2665 " + po.likes.size() + "   \u2022   " + mb(po.videoSize)
                : needsTapToPlay(po) ? mb(po.videoSize) + "  \u2022  tap to start" : mb(po.videoSize);
        JComponent t = new JComponent() {
            { Dimension d = new Dimension(w, h); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); setCursor(new Cursor(Cursor.HAND_CURSOR));
              if (!meta && needsTapToPlay(po)) setToolTipText("Large video (" + mb(po.videoSize) + ") - click to start playback"); }
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int W = getWidth() - 5, H = getHeight() - 5; BufferedImage im = thumbs.get(thumbKey(po));
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
        t.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { onClick.run(); } public void mouseEntered(MouseEvent e) { prefetch(po); } });
        if (!thumbs.containsKey(thumbKey(po))) thumbnail(po, t::repaint);
        return t;
    }

    // ---- full-screen style reel viewer (always dark, like a reels app) ----
}
