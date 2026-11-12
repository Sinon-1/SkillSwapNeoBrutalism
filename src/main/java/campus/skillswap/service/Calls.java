package campus.skillswap.service;

import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import campus.skillswap.model.*;
import campus.skillswap.net.*;
import campus.skillswap.ui.CallWindow;
import campus.skillswap.ui.CallWindow.Mode;
import campus.skillswap.ui.media.*;
import static campus.skillswap.core.AppState.*;
import static campus.skillswap.data.Database.*;
import static campus.skillswap.service.Stats.name;
import static campus.skillswap.ui.AppShell.page;
import static campus.skillswap.ui.Notifier.*;

/**
 * Video / voice calls between two signed-in students.
 * Setup messages (ring, decline, hang up) travel through the shared database or the LAN host;
 * the picture and sound then go straight from computer to computer over one TCP connection (see CallLink).
 * Everything here runs on the Swing thread except the small network/capture threads, which hop back with invokeLater.
 */
public class Calls {
    static final int RING_MS = 40_000;
    static int state;                                     // 0 idle, 1 calling, 2 being called, 3 in the call
    static String callId, peer, remoteHost; static int remotePort;
    static boolean outgoing, video, camOn, muted;
    static long startedAt;
    static ServerSocket ss; static CallLink link; static CallWindow win; static Camera cam; static Audio audio;
    static javax.swing.Timer ringTimer, tickTimer, beepTimer;

    public static boolean inCall() { return state != 0; }

    // ---------------------------------------------------------------- placing a call
    public static void start(String to, boolean withVideo) {
        if (me == null) return;
        if (!connected()) { toast("Connect to the database or a LAN host first"); return; }
        if (state != 0) { toast("You are already in a call"); if (win != null) win.toFront(); return; }
        User u = S.users.get(to); if (u == null) return;
        if (!Presence.isOnline(u)) { toast(name(u) + " is offline right now"); return; }
        final ServerSocket mine;
        try { mine = new ServerSocket(0); } catch (IOException e) { toast("Could not start the call: " + e.getMessage()); return; }
        ss = mine; callId = Long.toString(System.nanoTime(), 36) + Integer.toString(new Random().nextInt(1296), 36);
        peer = to; outgoing = true; video = withVideo; camOn = withVideo; muted = false; state = 1; final String id = callId;
        Thread t = new Thread(() -> acceptLoop(mine, id), "call-accept"); t.setDaemon(true); t.start();
        sendSig("INVITE", to, id, myIp(), mine.getLocalPort(), withVideo);
        win = new CallWindow(to, name(u), withVideo, ACTIONS); win.setMode(Mode.OUTGOING, withVideo ? "Video calling..." : "Calling..."); win.open();
        ringTimer = new javax.swing.Timer(RING_MS, e -> { if (state == 1 && id.equals(callId)) { sendSig("CANCEL", peer, id, "", 0, video); endSession("No answer", "Missed " + kind()); } });
        ringTimer.setRepeats(false); ringTimer.start();
    }
    static String kind() { return video ? "video call" : "voice call"; }
    static String cap(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    static void acceptLoop(ServerSocket mine, String id) {
        try {
            while (true) {
                Socket c = mine.accept();
                try {
                    c.setSoTimeout(5000); String hello = new DataInputStream(c.getInputStream()).readUTF(); c.setSoTimeout(0);
                    if (!hello.equals(id)) { c.close(); continue; }
                    SwingUtilities.invokeLater(() -> { if (state == 1 && id.equals(callId)) beginMedia(c); else try { c.close(); } catch (IOException e) { } });
                    return;
                } catch (IOException e) { try { c.close(); } catch (IOException x) { } }
            }
        } catch (IOException e) { /* server closed: call over */ }
    }

    // ---------------------------------------------------------------- incoming
    static void incoming(CallSig s) {
        state = 2; callId = s.callId; peer = s.from; remoteHost = s.host; remotePort = s.port; video = s.video; camOn = s.video; muted = false; outgoing = false; final String id = s.callId;
        win = new CallWindow(peer, name(peer), s.video, ACTIONS); win.setMode(Mode.INCOMING, s.video ? "Incoming video call" : "Incoming voice call"); win.open();
        notifyUser("Incoming " + (s.video ? "video" : "voice") + " call", name(peer) + " is calling you");
        beepTimer = new javax.swing.Timer(2000, e -> Toolkit.getDefaultToolkit().beep()); beepTimer.setInitialDelay(0); beepTimer.start();
        ringTimer = new javax.swing.Timer(RING_MS, e -> { if (state == 2 && id.equals(callId)) endSession("Missed call", null); });
        ringTimer.setRepeats(false); ringTimer.start();
    }

    static final CallWindow.Actions ACTIONS = new CallWindow.Actions() {
        public void accept() {
            if (state != 2) return;
            stopTimers(); final String host = remoteHost, id = callId; final int port = remotePort;
            win.setMode(Mode.OUTGOING, "Connecting...");
            Thread t = new Thread(() -> {
                try {
                    Socket c = new Socket(); c.connect(new InetSocketAddress(host, port), 6000);
                    DataOutputStream o = new DataOutputStream(c.getOutputStream()); o.writeUTF(id); o.flush();
                    SwingUtilities.invokeLater(() -> { if (state == 2 && id.equals(callId)) beginMedia(c); else try { c.close(); } catch (IOException e) { } });
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> { if (state == 2 && id.equals(callId)) { sendSig("DECLINE", peer, id, "", 0, video);
                        endSession("Could not connect to " + name(peer) + ". Both computers must be on the same network and the firewall must allow SkillSwap.", null); } });
                }
            }, "call-connect"); t.setDaemon(true); t.start();
        }
        public void decline() { if (state != 2) return; sendSig("DECLINE", peer, callId, "", 0, video); endSession("Declined", null); }
        public void hangup() {
            if (state == 1) { sendSig("CANCEL", peer, callId, "", 0, video); endSession("Cancelled", "Missed " + kind()); }
            else if (state == 2) decline();
            else if (state == 3) { if (link != null) link.bye(); sendSig("END", peer, callId, "", 0, video); endActive("Call ended"); }
        }
        public void mute(boolean on) { muted = on; }
        public void camera(boolean on) { if (state != 3) return; if (on) startCamera(link); else { stopCamera(); if (link != null) link.sendCam(false); } camOn = on; win.setLocalCam(on); }
    };

    // ---------------------------------------------------------------- the connected call
    static void beginMedia(Socket c) {
        final CallLink l;
        try { l = new CallLink(c); } catch (IOException e) { endSession("Connection failed", null); return; }
        stopTimers(); closeServer();
        state = 3; startedAt = System.currentTimeMillis(); link = l;
        l.onVideo = jpeg -> { BufferedImage im = decode(jpeg); CallWindow w = win; if (im != null && w != null) w.setRemote(im); };
        l.onAudio = b -> { Audio a = audio; if (a != null) a.play(b); };
        l.onCam = on -> { CallWindow w = win; if (w != null) w.setRemoteCam(on); };
        l.onClose = () -> SwingUtilities.invokeLater(() -> { if (link == l && state == 3) endActive("Call ended"); });
        audio = new Audio(); String warn = audio.start(chunk -> { if (!muted) l.sendAudio(chunk); }); if (warn != null) toast(warn);
        win.setLocalCam(camOn); win.setMode(Mode.ACTIVE, "Connected"); l.start();
        if (camOn) startCamera(l);
        tickTimer = new javax.swing.Timer(1000, e -> { if (win != null && state == 3) win.setStatus(dur((System.currentTimeMillis() - startedAt) / 1000)); }); tickTimer.start();
    }
    static void startCamera(CallLink l) {
        stopCamera(); if (l == null) return; if (win != null) win.setNotice("");
        final Camera cm = new Camera(); cam = cm; l.sendCam(true);
        cm.start(jpeg -> { l.sendVideo(jpeg); BufferedImage im = decode(jpeg); CallWindow w = win; if (im != null && w != null) w.setLocal(im); },
            err -> SwingUtilities.invokeLater(() -> { if (cam == cm && state == 3) { camOn = false; toast("Camera problem - details are shown in the call window"); win.setNotice("Camera: " + err + "  (log: " + Camera.logFile() + ")"); win.setLocalCam(false); l.sendCam(false); } }));
    }
    static void stopCamera() { if (cam != null) { cam.stop(); cam = null; } }
    static BufferedImage decode(byte[] jpeg) { try { return ImageIO.read(new ByteArrayInputStream(jpeg)); } catch (IOException e) { return null; } }
    static String dur(long s) { return String.format(s >= 3600 ? "%d:%02d:%02d" : "%2$d:%3$02d", s / 3600, (s / 60) % 60, s % 60).trim(); }

    // ---------------------------------------------------------------- ending
    static void endActive(String msg) {
        long secs = (System.currentTimeMillis() - startedAt) / 1000;
        endSession(msg + " - " + dur(secs), cap(kind()) + " (" + dur(secs) + ")");
    }
    /** Tear everything down, show the reason in the call window and (for the caller) add a line to the chat. */
    static void endSession(String windowMsg, String chatLine) {
        if (state == 0) return;
        final String who = peer; final boolean wasCaller = outgoing; CallWindow w = win;
        state = 0; stopTimers(); stopCamera(); closeServer();
        if (audio != null) { audio.stop(); audio = null; }
        if (link != null) { CallLink l = link; link = null; l.close(); }
        win = null; callId = null;
        if (w != null) w.ended(windowMsg);
        if (chatLine != null && wasCaller && me != null) {
            MessagesLog.add(who, chatLine);
        }
    }
    static void stopTimers() {
        if (ringTimer != null) { ringTimer.stop(); ringTimer = null; }
        if (beepTimer != null) { beepTimer.stop(); beepTimer = null; }
        if (tickTimer != null) { tickTimer.stop(); tickTimer = null; }
    }
    static void closeServer() { if (ss != null) { try { ss.close(); } catch (IOException e) { } ss = null; } }
    /** Called on log out / window close. */
    public static void shutdown() {
        if (state == 0) return;
        if (state == 1) sendSig("CANCEL", peer, callId, "", 0, video);
        else if (state == 2) sendSig("DECLINE", peer, callId, "", 0, video);
        else if (state == 3) { if (link != null) link.bye(); sendSig("END", peer, callId, "", 0, video); }
        endSession("Call ended", null);
    }

    // ---------------------------------------------------------------- setup messages
    public static void onSignal(CallSig s) {
        if (me == null || s == null || !s.to.equals(me.username)) return;
        if (Math.abs(System.currentTimeMillis() - s.ts) > 90_000) return;           // old leftovers
        boolean mine = s.callId != null && s.callId.equals(callId);
        switch (s.kind) {
            case "INVITE": if (state != 0) sendSig("BUSY", s.from, s.callId, "", 0, s.video); else incoming(s); break;
            case "CANCEL": if (state == 2 && mine) { endSession("Missed call", null); notifyUser("Missed call", name(s.from) + " tried to call you"); } break;
            case "DECLINE": if (state == 1 && mine) endSession(name(peer) + " declined the call", cap(kind()) + " declined"); break;
            case "BUSY": if (state == 1 && mine) endSession(name(peer) + " is on another call", "Missed " + kind()); break;
            case "END": if (state == 3 && mine) endActive("Call ended"); break;
            default: break;
        }
    }
    static void sendSig(String kind, String to, String id, String host, int port, boolean vid) {
        CallSig s = new CallSig(); s.callId = id; s.from = me.username; s.to = to; s.kind = kind; s.host = host; s.port = port; s.video = vid; s.ts = System.currentTimeMillis();
        if (LanSync.active()) { LanSync.signal(s); return; }
        if (db == null) return;
        try (PreparedStatement p = db.prepareStatement("INSERT INTO call_signals(call_id,from_user,to_user,kind,host,port,video,ts) VALUES(?,?,?,?,?,?,?,?)")) {
            p.setString(1, id); p.setString(2, s.from); p.setString(3, to); p.setString(4, kind); p.setString(5, host); p.setInt(6, port); p.setBoolean(7, vid); p.setLong(8, s.ts); p.executeUpdate();
        } catch (SQLException e) { toast("Call signal failed: " + nz(e.getMessage()).split("\n")[0]); }
        if (kind.equals("INVITE")) try (PreparedStatement d = db.prepareStatement("DELETE FROM call_signals WHERE ts<?")) { d.setLong(1, System.currentTimeMillis() - 600_000); d.executeUpdate(); } catch (SQLException e) { }
    }

    // ---- database mode: look for new setup messages every second
    static ScheduledExecutorService sigPoller; static Connection sigConn; static long lastSigId;
    public static void startSignalPoller() {
        if (sigPoller != null) return;
        sigPoller = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "call-poll"); t.setDaemon(true); return t; });
        sigPoller.scheduleWithFixedDelay(Calls::pollSignals, 2, 1, TimeUnit.SECONDS);
    }
    static void pollSignals() {
        Connection c = dbRead; User m = me;
        try {
            if (c == null || LanSync.active()) { sigConn = null; return; }
            List<CallSig> got = new ArrayList<>();
            synchronized (RL) {
                try {
                    if (sigConn != c || m == null) {                                  // new connection or signed out: skip everything that is already there
                        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT COALESCE(MAX(id),0) FROM call_signals")) { r.next(); lastSigId = r.getLong(1); }
                        sigConn = c;
                    } else try (PreparedStatement ps = c.prepareStatement("SELECT id,call_id,from_user,kind,host,port,video,ts FROM call_signals WHERE to_user=? AND id>? ORDER BY id")) {
                        ps.setString(1, m.username); ps.setLong(2, lastSigId);
                        try (ResultSet r = ps.executeQuery()) { while (r.next()) { lastSigId = Math.max(lastSigId, r.getLong(1)); CallSig s = new CallSig(); s.callId = r.getString(2); s.from = r.getString(3); s.to = m.username;
                            s.kind = r.getString(4); s.host = nz(r.getString(5)); s.port = r.getInt(6); s.video = r.getBoolean(7); s.ts = r.getLong(8); got.add(s); } }
                    }
                    c.commit();
                } catch (SQLException e) { try { c.rollback(); } catch (SQLException x) { } throw e; }
            }
            for (CallSig s : got) SwingUtilities.invokeLater(() -> onSignal(s));
        } catch (Exception e) { /* hiccup: next tick */ }
    }

    /** The address the other person should connect to: the network card that reaches the database / LAN host. */
    static String myIp() {
        try {
            InetAddress a = null;
            if (LanSync.active()) a = LanSync.localAddr();
            else if (db != null) try (DatagramSocket d = new DatagramSocket()) { d.connect(InetAddress.getByName(campus.skillswap.config.AppConfig.c("db.host", "localhost")), Integer.parseInt(campus.skillswap.config.AppConfig.c("db.port", "5432"))); a = d.getLocalAddress(); }
            if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isAnyLocalAddress()) return a.getHostAddress();
        } catch (Exception e) { /* fall through */ }
        return LanSync.lanIp();
    }
}
