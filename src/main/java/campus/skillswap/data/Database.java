package campus.skillswap.data;

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
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** PostgreSQL layer: schema, load snapshot, save changes, background polling. */
public class Database {

    // ====================== POSTGRESQL LAYER ======================
    public static Connection db, dbRead;                 // db = writes (EDT), dbRead = snapshot reads (background poller)
    public static final Object RL = new Object();
    public static volatile int ver;                      // bumped on every local save, used to discard stale polls
    public static int busy, localId = 1;                 // busy > 0 while a modal dialog is open (don't swap data underneath it)
    public static Map<String, String> snap = new HashMap<>();   // row fingerprints as last seen in the database
    public static ScheduledExecutorService poller;

    public static final String[] DDL = {
        "CREATE SEQUENCE IF NOT EXISTS ss_id_seq",
        "CREATE TABLE IF NOT EXISTS users(ord BIGSERIAL, username TEXT PRIMARY KEY, real_name TEXT NOT NULL DEFAULT '', email TEXT NOT NULL UNIQUE,"
            + " pass_hash TEXT NOT NULL, bio TEXT NOT NULL DEFAULT '', use_real_name BOOLEAN NOT NULL DEFAULT FALSE, public_ratings BOOLEAN NOT NULL DEFAULT TRUE,"
            + " nearby BOOLEAN NOT NULL DEFAULT FALSE, demo BOOLEAN NOT NULL DEFAULT FALSE, credits INT NOT NULL DEFAULT 5)",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen BIGINT NOT NULL DEFAULT 0",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS photo BYTEA",
        "CREATE TABLE IF NOT EXISTS user_skills(ord BIGSERIAL, username TEXT NOT NULL REFERENCES users(username) ON DELETE CASCADE,"
            + " kind CHAR(1) NOT NULL, skill TEXT NOT NULL, PRIMARY KEY(username, kind, skill))",
        "CREATE TABLE IF NOT EXISTS posts(id INT PRIMARY KEY, author TEXT NOT NULL, post_type TEXT NOT NULL, skill TEXT NOT NULL,"
            + " category TEXT NOT NULL, descr TEXT NOT NULL, created BIGINT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS post_likes(post_id INT NOT NULL REFERENCES posts(id) ON DELETE CASCADE, username TEXT NOT NULL, PRIMARY KEY(post_id, username))",
        "CREATE TABLE IF NOT EXISTS post_videos(post_id INT PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE, file_name TEXT NOT NULL, size_bytes BIGINT NOT NULL, data BYTEA NOT NULL)",
        "CREATE TABLE IF NOT EXISTS sessions(id INT PRIMARY KEY, teacher TEXT NOT NULL, learner TEXT NOT NULL, skill TEXT NOT NULL, sess_mode TEXT NOT NULL,"
            + " status TEXT NOT NULL, requester TEXT NOT NULL, review TEXT NOT NULL DEFAULT '', rating INT NOT NULL DEFAULT 0, starts_at TIMESTAMP NOT NULL)",
        "CREATE TABLE IF NOT EXISTS messages(id INT PRIMARY KEY, from_user TEXT NOT NULL, to_user TEXT NOT NULL, body TEXT NOT NULL, created BIGINT NOT NULL)",
        "ALTER TABLE messages ADD COLUMN IF NOT EXISTS edited BOOLEAN NOT NULL DEFAULT FALSE",
        "ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE",
        "ALTER TABLE messages ADD COLUMN IF NOT EXISTS hidden_by TEXT NOT NULL DEFAULT ''",
        "CREATE INDEX IF NOT EXISTS idx_messages_pair ON messages(from_user, to_user, created)",
        "CREATE TABLE IF NOT EXISTS reports(id INT PRIMARY KEY, by_user TEXT NOT NULL, target TEXT NOT NULL, reason TEXT NOT NULL,"
            + " details TEXT NOT NULL DEFAULT '', created BIGINT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS call_signals(id BIGSERIAL PRIMARY KEY, call_id TEXT NOT NULL, from_user TEXT NOT NULL, to_user TEXT NOT NULL, kind TEXT NOT NULL,"
            + " host TEXT NOT NULL DEFAULT '', port INT NOT NULL DEFAULT 0, video BOOLEAN NOT NULL DEFAULT FALSE, ts BIGINT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS idx_call_signals_to ON call_signals(to_user, id)"
    };

    public static String nz(String s) { return s == null ? "" : s; }
    public static String j(Object... a) { return Arrays.stream(a).map(String::valueOf).collect(Collectors.joining("\u0001")); }

    public static Connection open(String host, String port, String name, String user, String pw) throws SQLException {
        String base = "jdbc:postgresql://" + host + ":" + port + "/";
        try { return DriverManager.getConnection(base + name, user, pw); }
        catch (SQLException e) {
            if (!"3D000".equals(e.getSQLState()) || !name.matches("\\w+")) throw e;     // 3D000 = database does not exist yet -> create it
            try (Connection a = DriverManager.getConnection(base + "postgres", user, pw); Statement s = a.createStatement()) { s.execute("CREATE DATABASE " + name); }
            return DriverManager.getConnection(base + name, user, pw);
        }
    }
    public static void closeDb() {
        LanSync.stop();
        try { if (db != null) db.close(); } catch (Exception e) { } try { if (dbRead != null) dbRead.close(); } catch (Exception e) { } db = null; dbRead = null;
    }
    public static void schema(Connection cn) throws SQLException { try (Statement s = cn.createStatement()) { for (String q : DDL) s.execute(q); } }
    public static boolean connected() { return db != null || LanSync.active(); }
    public static int nextId() {
        if (LanSync.active()) return LanSync.newId();
        if (db == null) return localId++;
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT nextval('ss_id_seq')")) { r.next(); return r.getInt(1); }
        catch (SQLException e) { throw new IllegalStateException("Database error: " + e.getMessage(), e); }
    }

    public static Store fetch() throws SQLException {
        synchronized (RL) {
            Store st = new Store();
            try (Statement s = dbRead.createStatement()) {
                try (ResultSet r = s.executeQuery("SELECT username,real_name,email,pass_hash,bio,use_real_name,public_ratings,nearby,demo,credits,last_seen,photo FROM users ORDER BY ord")) {
                    while (r.next()) { User u = new User(); u.username = r.getString(1); u.realName = nz(r.getString(2)); u.email = r.getString(3); u.passHash = r.getString(4); u.bio = nz(r.getString(5));
                        u.useRealName = r.getBoolean(6); u.publicRatings = r.getBoolean(7); u.nearby = r.getBoolean(8); u.demo = r.getBoolean(9); u.credits = r.getInt(10); u.lastSeen = r.getLong(11); u.photo = r.getBytes(12); st.users.put(u.username, u); } }
                try (ResultSet r = s.executeQuery("SELECT username,kind,skill FROM user_skills ORDER BY ord")) {
                    while (r.next()) { User u = st.users.get(r.getString(1)); if (u != null) (r.getString(2).equals("T") ? u.teach : u.learn).add(r.getString(3)); } }
                try (ResultSet r = s.executeQuery("SELECT id,author,post_type,skill,category,descr,created FROM posts ORDER BY id")) {
                    while (r.next()) { Post p = new Post(); p.id = r.getInt(1); p.author = r.getString(2); p.type = r.getString(3); p.skill = r.getString(4); p.category = r.getString(5); p.desc = r.getString(6); p.time = r.getLong(7); st.posts.add(p); } }
                Map<Integer, Post> pm = new HashMap<>(); for (Post p : st.posts) pm.put(p.id, p);
                try (ResultSet r = s.executeQuery("SELECT post_id,username FROM post_likes")) { while (r.next()) { Post p = pm.get(r.getInt(1)); if (p != null) p.likes.add(r.getString(2)); } }
                try (ResultSet r = s.executeQuery("SELECT post_id,file_name,size_bytes FROM post_videos")) { while (r.next()) { Post p = pm.get(r.getInt(1)); if (p != null) { p.videoName = r.getString(2); p.videoSize = r.getLong(3); } } }
                try (ResultSet r = s.executeQuery("SELECT id,teacher,learner,skill,sess_mode,status,requester,review,rating,starts_at FROM sessions ORDER BY id")) {
                    while (r.next()) { Sess x = new Sess(); x.id = r.getInt(1); x.teacher = r.getString(2); x.learner = r.getString(3); x.skill = r.getString(4); x.mode = r.getString(5); x.status = r.getString(6);
                        x.requester = r.getString(7); x.review = nz(r.getString(8)); x.rating = r.getInt(9); x.at = r.getTimestamp(10).toLocalDateTime(); st.sessions.add(x); } }
                try (ResultSet r = s.executeQuery("SELECT id,from_user,to_user,body,created,edited,deleted,hidden_by FROM messages ORDER BY created,id")) {
                    while (r.next()) { Msg m = new Msg(); m.id = r.getInt(1); m.from = r.getString(2); m.to = r.getString(3); m.text = r.getString(4); m.time = r.getLong(5); m.edited = r.getBoolean(6); m.deleted = r.getBoolean(7); m.hiddenBy = nz(r.getString(8)); st.msgs.add(m); } }
                try (ResultSet r = s.executeQuery("SELECT id,by_user,target,reason,details,created FROM reports ORDER BY id")) {
                    while (r.next()) { Report x = new Report(); x.id = r.getInt(1); x.by = r.getString(2); x.target = r.getString(3); x.reason = r.getString(4); x.details = nz(r.getString(5)); x.time = r.getLong(6); st.reports.add(x); } }
                try (ResultSet r = s.executeQuery("SELECT v FROM meta WHERE k='last_sync'")) { if (r.next()) st.lastSync = r.getString(1); }
                dbRead.commit();
            } catch (SQLException e) { try { dbRead.rollback(); } catch (Exception x) { } throw e; }
            return st;
        }
    }
    /** One short fingerprint per row; save() only writes rows whose fingerprint changed, so two clients rarely overwrite each other. */
    public static Map<String, String> fingerprints(Store st) {
        Map<String, String> m = new HashMap<>();
        for (User u : st.users.values()) m.put("u:" + u.username, j(nz(u.realName), u.email, u.passHash, nz(u.bio), u.useRealName, u.publicRatings, u.nearby, u.demo, u.credits, u.photo == null ? 0 : Arrays.hashCode(u.photo), String.join("\u0002", u.teach), String.join("\u0002", u.learn)));
        for (Post p : st.posts) m.put("p:" + p.id, pfp(p));
        for (Sess s : st.sessions) m.put("s:" + s.id, j(s.teacher, s.learner, s.skill, s.mode, s.status, s.requester, nz(s.review), s.rating, s.at.truncatedTo(ChronoUnit.SECONDS)));
        for (Msg x : st.msgs) m.put("m:" + x.id, j(x.text, x.edited, x.deleted, nz(x.hiddenBy)));
        for (Report x : st.reports) m.put("r:" + x.id, "");
        m.put("meta:sync", st.lastSync);
        return m;
    }
    public static String pfp(Post p) { return j(p.author, p.type, p.skill, p.category, p.desc, p.time, new TreeSet<>(p.likes), nz(p.videoName), p.videoSize); }
    public static boolean chg(String k, Map<String, String> now) { return !now.get(k).equals(snap.get(k)); }
    public static void save() {
        if (LanSync.active()) { LanSync.save(); return; }
        if (db == null) return;
        ver++; Map<String, String> now = fingerprints(S);
        try {
            db.setAutoCommit(false);
            try {
                for (User u : S.users.values()) if (chg("u:" + u.username, now)) writeUser(u);
                for (Post p : S.posts) if (chg("p:" + p.id, now)) writePost(p);
                for (Sess s : S.sessions) if (chg("s:" + s.id, now)) writeSess(s);
                for (Msg m : S.msgs) if (chg("m:" + m.id, now)) writeMsg(m);
                for (Report r : S.reports) if (chg("r:" + r.id, now)) writeReport(r);
                if (chg("meta:sync", now)) try (PreparedStatement p = db.prepareStatement("INSERT INTO meta(k,v) VALUES('last_sync',?) ON CONFLICT(k) DO UPDATE SET v=EXCLUDED.v")) { p.setString(1, S.lastSync); p.executeUpdate(); }
                for (String k : snap.keySet()) if (k.startsWith("p:") && !now.containsKey(k))
                    try (PreparedStatement p = db.prepareStatement("DELETE FROM posts WHERE id=?")) { p.setInt(1, Integer.parseInt(k.substring(2))); p.executeUpdate(); }
                db.commit(); snap = now;
            } catch (SQLException e) { db.rollback(); throw e; }
            finally { db.setAutoCommit(true); }
        } catch (SQLException e) { toast("Database error: " + nz(e.getMessage()).split("\n")[0]); }
    }
    public static void writeUser(User u) throws SQLException {
        try (PreparedStatement p = db.prepareStatement("INSERT INTO users(username,real_name,email,pass_hash,bio,use_real_name,public_ratings,nearby,demo,credits,photo) VALUES(?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT(username) DO UPDATE SET real_name=EXCLUDED.real_name,email=EXCLUDED.email,pass_hash=EXCLUDED.pass_hash,bio=EXCLUDED.bio,use_real_name=EXCLUDED.use_real_name,"
                + "public_ratings=EXCLUDED.public_ratings,nearby=EXCLUDED.nearby,demo=EXCLUDED.demo,credits=EXCLUDED.credits,photo=EXCLUDED.photo")) {
            p.setString(1, u.username); p.setString(2, nz(u.realName)); p.setString(3, u.email); p.setString(4, u.passHash); p.setString(5, nz(u.bio)); p.setBoolean(6, u.useRealName);
            p.setBoolean(7, u.publicRatings); p.setBoolean(8, u.nearby); p.setBoolean(9, u.demo); p.setInt(10, u.credits); p.setBytes(11, u.photo); p.executeUpdate();
        }
        try (PreparedStatement d = db.prepareStatement("DELETE FROM user_skills WHERE username=?")) { d.setString(1, u.username); d.executeUpdate(); }
        try (PreparedStatement p = db.prepareStatement("INSERT INTO user_skills(username,kind,skill) VALUES(?,?,?) ON CONFLICT DO NOTHING")) {
            for (String s : u.teach) { p.setString(1, u.username); p.setString(2, "T"); p.setString(3, s); p.addBatch(); }
            for (String s : u.learn) { p.setString(1, u.username); p.setString(2, "L"); p.setString(3, s); p.addBatch(); }
            p.executeBatch();
        }
    }
    public static void writePost(Post po) throws SQLException {
        try (PreparedStatement p = db.prepareStatement("INSERT INTO posts(id,author,post_type,skill,category,descr,created) VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                + "author=EXCLUDED.author,post_type=EXCLUDED.post_type,skill=EXCLUDED.skill,category=EXCLUDED.category,descr=EXCLUDED.descr,created=EXCLUDED.created")) {
            p.setInt(1, po.id); p.setString(2, po.author); p.setString(3, po.type); p.setString(4, po.skill); p.setString(5, po.category); p.setString(6, po.desc); p.setLong(7, po.time); p.executeUpdate();
        }
        try (PreparedStatement d = db.prepareStatement("DELETE FROM post_likes WHERE post_id=?")) { d.setInt(1, po.id); d.executeUpdate(); }
        try (PreparedStatement p = db.prepareStatement("INSERT INTO post_likes(post_id,username) VALUES(?,?) ON CONFLICT DO NOTHING")) {
            for (String l : po.likes) { p.setInt(1, po.id); p.setString(2, l); p.addBatch(); } p.executeBatch();
        }
    }
    public static void writeSess(Sess s) throws SQLException {
        try (PreparedStatement p = db.prepareStatement("INSERT INTO sessions(id,teacher,learner,skill,sess_mode,status,requester,review,rating,starts_at) VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                + "teacher=EXCLUDED.teacher,learner=EXCLUDED.learner,skill=EXCLUDED.skill,sess_mode=EXCLUDED.sess_mode,status=EXCLUDED.status,requester=EXCLUDED.requester,review=EXCLUDED.review,"
                + "rating=EXCLUDED.rating,starts_at=EXCLUDED.starts_at")) {
            p.setInt(1, s.id); p.setString(2, s.teacher); p.setString(3, s.learner); p.setString(4, s.skill); p.setString(5, s.mode); p.setString(6, s.status); p.setString(7, s.requester);
            p.setString(8, nz(s.review)); p.setInt(9, s.rating); p.setTimestamp(10, Timestamp.valueOf(s.at)); p.executeUpdate();
        }
    }
    public static void writeMsg(Msg m) throws SQLException {
        try (PreparedStatement p = db.prepareStatement("INSERT INTO messages(id,from_user,to_user,body,created,edited,deleted,hidden_by) VALUES(?,?,?,?,?,?,?,?) "
                + "ON CONFLICT(id) DO UPDATE SET body=EXCLUDED.body,edited=EXCLUDED.edited,deleted=EXCLUDED.deleted,hidden_by=EXCLUDED.hidden_by")) {
            p.setInt(1, m.id); p.setString(2, m.from); p.setString(3, m.to); p.setString(4, nz(m.text)); p.setLong(5, m.time);
            p.setBoolean(6, m.edited); p.setBoolean(7, m.deleted); p.setString(8, nz(m.hiddenBy)); p.executeUpdate();
        }
    }
    public static void writeReport(Report r) throws SQLException {
        try (PreparedStatement p = db.prepareStatement("INSERT INTO reports(id,by_user,target,reason,details,created) VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING")) {
            p.setInt(1, r.id); p.setString(2, r.by); p.setString(3, r.target); p.setString(4, r.reason); p.setString(5, nz(r.details)); p.setLong(6, r.time); p.executeUpdate();
        }
    }
    /** Load everything from PostgreSQL (seeding demo data on an empty database). */
    public static void initData() throws Exception {
        if (LanSync.active()) { LanSync.initData(); return; }
        S = fetch();
        if (S.users.isEmpty()) { S = new Store(); snap = new HashMap<>(); seed(); save(); } else snap = fingerprints(S);
        ver++;
    }
    /** Background poll: other computers' changes show up within ~3 seconds. */
    public static void startPolling() {
        if (poller != null) return;
        poller = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "db-poll"); t.setDaemon(true); return t; });
        poller.scheduleWithFixedDelay(() -> {
            try { if (dbRead == null) return; int v = ver; Store st = fetch(); Map<String, String> fp = fingerprints(st); SwingUtilities.invokeLater(() -> applyRemote(st, fp, v)); }
            catch (Exception e) { /* connection hiccup: try again next tick */ } }, 3, 3, TimeUnit.SECONDS);
    }
}
