package campus.skillswap.ui.media;

import javax.swing.SwingUtilities;
import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import campus.skillswap.model.Msg;
import campus.skillswap.net.LanSync;
import static campus.skillswap.config.AppConfig.DIR;

public class ChatAttachments {
    public static final long MAX_FILE = 60L * 1024 * 1024;
    static final ConcurrentHashMap<Integer, Object> LOCKS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Integer, File> LOCAL = new ConcurrentHashMap<>();

    public static String kind(File f) {
        if (PickerPreview.isImage(f)) return "image";
        if (Videos.ChooserPreview.isVideo(f)) return "video";
        return "file";
    }

    public static String mime(File f) {
        try { String m = Files.probeContentType(f.toPath()); if (m != null && !m.isEmpty()) return m; } catch (Exception e) { }
        String k = kind(f);
        return k.equals("image") ? "image/jpeg" : k.equals("video") ? "video/mp4" : "application/octet-stream";
    }

    public static void attach(Msg m, File f) {
        m.fileName = f.getName(); m.fileKind = kind(f); m.fileMime = mime(f); m.fileSize = f.length();
    }

    public static void keepLocal(Msg m, File f) { if (f != null && f.isFile()) LOCAL.put(m.id, f); }

    public static String label(Msg m) {
        return m.hasFile() ? "image".equals(m.fileKind) ? "Photo" : "video".equals(m.fileKind) ? "Video" : "File" : "";
    }

    public static void upload(Msg m, File f, Runnable ok, Consumer<String> fail) {
        new Thread(() -> {
            try {
                if (LanSync.active()) LanSync.putMessageFile(m.id, f);
                else try (Connection cn = Videos.openXfer(); InputStream in = new BufferedInputStream(new FileInputStream(f));
                          PreparedStatement p = cn.prepareStatement("INSERT INTO message_files(message_id,data) VALUES(?,?) ON CONFLICT(message_id) DO UPDATE SET data=EXCLUDED.data")) {
                    p.setInt(1, m.id); p.setBinaryStream(2, in, f.length()); p.executeUpdate();
                }
                SwingUtilities.invokeLater(() -> { m.fileError = null; if (ok != null) ok.run(); });
            } catch (Throwable e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().split("\n")[0];
                SwingUtilities.invokeLater(() -> { m.fileError = msg; if (fail != null) fail.accept(msg); });
            }
        }, "chat-file-upload").start();
    }

    public static File cached(Msg m) {
        if (!m.hasFile()) return null;
        String safe = m.fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty()) safe = "attachment";
        return DIR.resolve("cache").resolve("chat_" + m.id + "_" + safe).toFile();
    }

    public static void withFile(Msg m, Consumer<File> ok, Consumer<String> fail) {
        File cache = cached(m);
        if (cache == null) { fail.accept("This message has no attachment"); return; }
        File local = LOCAL.get(m.id);
        if (local != null && local.isFile()) { ok.accept(local); return; }
        try {
            if (cache.exists() && (m.fileSize <= 0 || cache.length() == m.fileSize)) { ok.accept(cache); return; }
        } catch (Exception e) { }
        final int id = m.id;
        new Thread(() -> {
            synchronized (LOCKS.computeIfAbsent(id, k -> new Object())) {
                try {
                    if (cache.exists() && (m.fileSize <= 0 || cache.length() == m.fileSize)) {
                        SwingUtilities.invokeLater(() -> ok.accept(cache)); return;
                    }
                    Files.createDirectories(cache.getParentFile().toPath());
                    Path tmp = cache.toPath().resolveSibling(cache.getName() + "." + System.nanoTime() + ".part");
                    try {
                        if (LanSync.active()) LanSync.getMessageFile(id, tmp);
                        else try (Connection cn = Videos.openXfer(); PreparedStatement p = cn.prepareStatement("SELECT data FROM message_files WHERE message_id=?")) {
                            p.setInt(1, id);
                            try (ResultSet r = p.executeQuery()) {
                                if (!r.next()) throw new IOException("The attachment is not available yet");
                                try (InputStream in = r.getBinaryStream(1)) { Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); }
                            }
                        }
                        long got = Files.size(tmp);
                        if (m.fileSize > 0 && got != m.fileSize) throw new IOException("The attachment download was incomplete");
                        Files.move(tmp, cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } finally { Files.deleteIfExists(tmp); }
                    SwingUtilities.invokeLater(() -> ok.accept(cache));
                } catch (Throwable e) {
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().split("\n")[0];
                    SwingUtilities.invokeLater(() -> fail.accept(msg));
                }
            }
        }, "chat-file-download").start();
    }

    public static void removeCached(Msg m) {
        LOCAL.remove(m.id);
        File f = cached(m);
        if (f != null) { try { Files.deleteIfExists(f.toPath()); } catch (Exception e) { } }
    }
}
