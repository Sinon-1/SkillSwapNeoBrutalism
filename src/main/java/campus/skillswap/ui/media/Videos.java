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
    static void withVideoLan(Post po, Consumer<File> ok, Consumer<String> fail) {
        final int id = po.id; final long size = po.videoSize;
        final Path dir = DIR.resolve("cache"), file = dir.resolve("video_" + id + "_" + po.videoName.replaceAll("[^A-Za-z0-9._-]", "_"));
        try { if (Files.exists(file) && Files.size(file) == size) { ok.accept(file.toFile()); return; } } catch (IOException e) { /* download again */ }
        new Thread(() -> {
            try { Files.createDirectories(dir); Path tmp = dir.resolve(file.getFileName() + ".part"); LanSync.getVideo(id, tmp); Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                SwingUtilities.invokeLater(() -> ok.accept(file.toFile()));
            } catch (Exception e) { SwingUtilities.invokeLater(() -> fail.accept(nz(e.getMessage()).split("\n")[0])); }
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
    public static void openExternal(File f) {
        try { if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) { Desktop.getDesktop().open(f); return; } } catch (Exception e) { }
        info("Could not open a video player automatically.\nThe video is saved at:\n" + f.getAbsolutePath());
    }
    /** In-app player: only available when VideoPlayerFX.class (and JavaFX) are present; otherwise null and we use the default player. */
    public static JComponent fxPlayer(File f, int w, int h, Consumer<String> onFail) {
        try { return (JComponent) Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("create", File.class, int.class, int.class, Consumer.class).invoke(null, f, w, h, onFail); } catch (Throwable t) { return null; }
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
            try {
                File tmp = File.createTempFile("ssthumb", ".png"); tmp.delete();
                Process pr = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-ss", seek, "-i", video.getAbsolutePath(), "-frames:v", "1", "-vf", "scale='min(640,iw)':-2", tmp.getAbsolutePath()).redirectErrorStream(true).start();
                pr.getInputStream().readAllBytes();
                if (!pr.waitFor(20, TimeUnit.SECONDS)) pr.destroyForcibly();
                if (tmp.exists()) { BufferedImage im = javax.imageio.ImageIO.read(tmp); tmp.delete(); if (im != null) return im; }
            } catch (Exception e) { break; }     // ffmpeg not installed
        }
        try { return (BufferedImage) Class.forName("campus.skillswap.ui.media.VideoPlayerFX").getMethod("thumbnail", File.class, int.class, int.class).invoke(null, video, 640, 360); } catch (Throwable t) { return null; }
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
        put(box, lbl("<html><body style='width:260px'>" + esc(first) + "<br>Tip: use MP4 with H.264 video + AAC audio.</body></html>", Font.PLAIN, 11, MUTED)); gap(box, 6);
        JPanel bar = row(); bar.add(ghost("Open in video player", () -> openExternal(file))); bar.add(ghost("Back", Videos::stopInline)); put(box, bar);
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
