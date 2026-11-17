package campus.skillswap.service;

import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import campus.skillswap.model.*;
import campus.skillswap.net.CallLink;
import campus.skillswap.ui.CallFx;
import campus.skillswap.ui.GroupCallWindow;
import campus.skillswap.ui.GroupCallWindow.Mode;
import campus.skillswap.ui.GroupCallWindow.Tile;
import campus.skillswap.ui.media.*;
import static campus.skillswap.core.AppState.*;
import static campus.skillswap.data.Database.*;
import static campus.skillswap.service.Stats.name;
import static campus.skillswap.ui.Notifier.*;

/**
 * Group video calls (up to {@link #MAX_PEOPLE} people), meant for tutoring sessions with a small class.
 *
 * Every pair of people is connected directly (a "mesh"), using the same CallLink as 1-to-1 calls, so there is no server.
 * Setup messages travel through the database / LAN host like 1-to-1 call setup, as CallSig rows:
 *  - GJOIN  "I am in the call of this group, here is my address"        (sent to every online member)
 *  - GHERE  "so am I, here is my address"                                (answer to GJOIN / GRING)
 *  - GRING  "nobody was in the call, please join"                         (rings members who are not in the call)
 *  - GEND   "the person who rang has left before anyone joined"
 * callId is "G" + groupId + ":" + ticket, where the ticket is a random secret of the sender's listening socket.
 * Of every pair, the person whose user name sorts later opens the connection, so there are never two connections.
 * In GJOIN / GHERE the CallSig.video flag means "I have been in this call for a few seconds" (see established()).
 */
public class GroupCalls {
    public static final int MAX_PEOPLE = 6;
    static final int RING_MS = 40_000, PROBE_MS = 3_000;
    static final String SYS = "\u0001";                    // marks a chat line that is a notice, not typed text

    static class Peer { final String user; final CallLink link; final Tile tile; Peer(String u, CallLink l, Tile t) { user = u; link = l; tile = t; } }

    static boolean active, starter, ringSent, sawEstablished, everHadPeers, camOn, muted, handUp;
    static Group group; static String ticket, ip; static int port; static long startedAt;
    static ServerSocket ss; static GroupCallWindow win; static Tile mine; static Camera cam; static Audio audio;
    static final Map<String, Peer> peers = new LinkedHashMap<>();
    static final Set<String> connecting = new HashSet<>();
    static final List<CallLink> links = new CopyOnWriteArrayList<>();      // read by the camera / microphone threads
    static javax.swing.Timer probeTimer, tickTimer;
    // incoming ring
    static GroupCallWindow ringWin; static Group ringGroup; static javax.swing.Timer ringTimer, beepTimer;

    public static boolean active() { return active; }
    public static int activeGroupId() { return active && group != null ? group.id : -1; }
    public static void focus() { GroupCallWindow w = win; if (w != null) { w.setState(java.awt.Frame.NORMAL); w.toFront(); } }

    // ---------------------------------------------------------------- starting / joining
    /** The "Group call" button: joins the call of this group if one is running, otherwise starts one and rings the others. */
    public static void start(Group g) {
        if (me == null || g == null || !g.has(me.username)) return;
        if (!connected()) { toast("Connect to the database or a LAN host first"); return; }
        if (active) { if (group.id == g.id) focus(); else toast("You are already in a call"); return; }
        if (Calls.inCall()) { toast("You are already in a call"); return; }
        if (online(g).isEmpty()) { toast("Nobody else in " + g.name + " is online right now"); return; }
        join(g, true);
    }
    static List<String> online(Group g) {
        List<String> l = new ArrayList<>();
        for (String u : g.members) if (!u.equals(me.username) && Presence.isOnline(S.users.get(u))) l.add(u);
        return l;
    }
    static String myCallId() { return "G" + group.id + ":" + ticket; }
    static boolean established() { return active && System.currentTimeMillis() - startedAt >= PROBE_MS; }
    static void announce(String kind) { for (String u : online(group)) Calls.sendSig(kind, u, myCallId(), ip, port, established()); }

    static void join(Group g, boolean ring) {
        final ServerSocket mineSock;
        try { mineSock = new ServerSocket(0); } catch (IOException e) { toast("Could not start the call: " + e.getMessage()); return; }
        closeRing(null);
        ss = mineSock; port = mineSock.getLocalPort(); ip = Calls.myIp(); ticket = Long.toString(new java.security.SecureRandom().nextLong() & Long.MAX_VALUE, 36);
        group = g; active = true; starter = ring; ringSent = false; sawEstablished = false; everHadPeers = false; camOn = true; muted = false; handUp = false;
        startedAt = System.currentTimeMillis(); peers.clear(); connecting.clear(); links.clear();
        mine = new Tile(me.username, name(me), true);
        win = new GroupCallWindow(g, me.username, ACTIONS); win.addTile(mine); win.setMode(Mode.ACTIVE, "Looking for the others..."); win.open();
        final String tk = ticket; Thread t = new Thread(() -> acceptLoop(mineSock, tk), "gcall-accept"); t.setDaemon(true); t.start();
        audio = new Audio();
        String warn = audio.start(chunk -> {
            if (muted) return; if (Audio.rms(chunk) > 700) mine.speakUntil = System.currentTimeMillis() + 500;
            for (CallLink l : links) l.sendAudio(chunk);
        });
        if (warn != null) toast(warn);
        startCamera();
        announce("GJOIN");
        if (ring) { probeTimer = new javax.swing.Timer(PROBE_MS, e -> { if (active && !sawEstablished) ring(); }); probeTimer.setRepeats(false); probeTimer.start(); }
        tickTimer = new javax.swing.Timer(1000, e -> updateStatus()); tickTimer.start();
    }
    /** Nobody who was already in the call answered: ring the members who are not in it and leave a note in the chat. */
    static void ring() {
        if (ringSent || group == null) return;
        ringSent = true; announce("GRING"); updateStatus();
        MessagesLog.add(group.key(), SYS + name(me) + " started a group video call");
    }
    static void updateStatus() {
        GroupCallWindow w = win; if (w == null || !active) return;
        long secs = (System.currentTimeMillis() - startedAt) / 1000;
        w.setStatus(peers.isEmpty() ? (ringSent ? "Ringing the group... " : "Looking for the others... ") + Calls.dur(secs) : (peers.size() + 1) + " in call - " + Calls.dur(secs));
    }

    // ---------------------------------------------------------------- setup messages
    public static void onSignal(CallSig s) {
        if (me == null || s.callId == null || s.from == null || s.from.equals(me.username)) return;
        int colon = s.callId.indexOf(':'); if (colon < 2) return;
        final int gid; try { gid = Integer.parseInt(s.callId.substring(1, colon)); } catch (NumberFormatException e) { return; }
        final String tk = s.callId.substring(colon + 1);
        Group g = null; for (Group x : S.groups) if (x.id == gid) g = x;
        if (g == null || !g.has(me.username) || !g.has(s.from)) return;                       // members only
        boolean here = active && group.id == gid;
        switch (s.kind) {
            case "GJOIN": if (here) onPresent(s, tk, true); break;
            case "GRING": if (here) onPresent(s, tk, true); else if (!active && !Calls.inCall() && ringWin == null) incoming(g, s.from); break;
            case "GHERE": if (here) onPresent(s, tk, false); break;
            case "GEND": if (ringWin != null && ringGroup.id == gid) closeRing("Missed group call"); break;
            default: break;
        }
    }
    /** Someone in the same group call announced itself (reply = true: tell them we are here too). */
    static void onPresent(CallSig s, String tk, boolean reply) {
        if (s.video) sawEstablished = true;
        if (reply) Calls.sendSig("GHERE", s.from, myCallId(), ip, port, established());
        if (peers.containsKey(s.from) || connecting.contains(s.from)) return;
        if (peers.size() + connecting.size() + 1 >= MAX_PEOPLE) return;
        if (me.username.compareTo(s.from) > 0) connect(s.from, s.host, s.port, tk);          // the later name connects, the other one waits for it
    }

    // ---------------------------------------------------------------- connections
    static void connect(final String user, final String host, final int prt, final String tk) {
        connecting.add(user); final String session = ticket, myName = me.username;
        Thread t = new Thread(() -> {
            Socket c = null; IOException last = null;
            for (int attempt = 0; attempt < 3 && c == null; attempt++) {                              // the first try can fail while a firewall question is open
                try { Socket x = new Socket(); x.connect(new InetSocketAddress(host, prt), 5000); DataOutputStream o = new DataOutputStream(x.getOutputStream()); o.writeUTF(tk); o.writeUTF(myName); o.flush(); c = x; }
                catch (IOException e) { last = e; try { Thread.sleep(1500); } catch (InterruptedException ie) { break; } }
            }
            final Socket fc = c; final IOException err = last;
            SwingUtilities.invokeLater(() -> {
                connecting.remove(user);
                if (!active || !session.equals(ticket)) { close(fc); return; }
                if (fc != null) addPeer(user, fc);
                else if (win != null) win.setNotice("Could not connect to " + name(user) + ". Both computers must be on the same network and the firewall must allow SkillSwap." + (err != null ? " (" + err.getMessage() + ")" : ""));
            });
        }, "gcall-connect"); t.setDaemon(true); t.start();
    }
    static void close(Socket c) { if (c != null) try { c.close(); } catch (IOException e) { } }

    static void acceptLoop(ServerSocket sock, final String tk) {
        try {
            while (true) {
                final Socket c = sock.accept();
                Thread h = new Thread(() -> {
                    try {
                        c.setSoTimeout(5000); DataInputStream in = new DataInputStream(c.getInputStream()); String t = in.readUTF(), user = in.readUTF(); c.setSoTimeout(0);
                        if (!t.equals(tk)) { c.close(); return; }
                        SwingUtilities.invokeLater(() -> {
                            if (active && tk.equals(ticket) && group.has(user) && !user.equals(me.username) && !peers.containsKey(user) && peers.size() + 1 < MAX_PEOPLE) addPeer(user, c); else close(c);
                        });
                    } catch (IOException e) { close(c); }
                }, "gcall-hello"); h.setDaemon(true); h.start();
            }
        } catch (IOException e) { /* socket closed: call over */ }
    }

    static void addPeer(final String user, Socket c) {
        final CallLink l; try { l = new CallLink(c); } catch (IOException e) { close(c); return; }
        final Tile t = new Tile(user, name(user), false); final Peer p = new Peer(user, l, t);
        l.onVideo = jpeg -> { BufferedImage im = Calls.decode(jpeg); GroupCallWindow w = win; if (im != null) { t.img = im; if (w != null) w.repaintStage(); } };
        l.onAudio = b -> { Audio a = audio; if (a != null) a.play(user, b); if (Audio.rms(b) > 700) t.speakUntil = System.currentTimeMillis() + 500; };
        l.onCam = on -> { t.cam = on; GroupCallWindow w = win; if (w != null) w.repaintStage(); };
        l.onMic = on -> { t.mic = on; GroupCallWindow w = win; if (w != null) w.repaintStage(); };
        l.onEmote = key -> SwingUtilities.invokeLater(() -> { if (win != null && active && peers.get(user) == p) win.addEmote(user, key); });
        l.onHand = up -> SwingUtilities.invokeLater(() -> { if (win != null && active && peers.get(user) == p) win.setHand(user, up); });
        l.onClose = () -> SwingUtilities.invokeLater(() -> removePeer(user, l));
        peers.put(user, p); links.add(l); everHadPeers = true; win.addTile(t); l.start();
        l.sendCam(camOn); l.sendMic(!muted); if (handUp) l.sendHand(true);                            // tell the newcomer our current state
        win.flash(name(user) + " joined"); updateStatus();
    }
    static void removePeer(String user, CallLink l) {
        Peer p = peers.get(user); if (!active || p == null || p.link != l) return;
        peers.remove(user); links.remove(l); if (win != null) { win.removeTile(user); win.flash(name(user) + " left"); } updateStatus();
    }

    // ---------------------------------------------------------------- camera / controls
    static void startCamera() {
        stopCamera(); if (win != null) win.setNotice(""); final Camera cm = new Camera(); cam = cm; camOn = true; mine.cam = true;
        for (CallLink l : links) l.sendCam(true);
        cm.start(jpeg -> { for (CallLink l : links) l.sendVideo(jpeg); BufferedImage im = Calls.decode(jpeg); GroupCallWindow w = win; if (im != null) { mine.img = im; if (w != null) w.repaintStage(); } },
            err -> SwingUtilities.invokeLater(() -> { if (cam == cm && active) { camOn = false; mine.cam = false; mine.img = null; toast("Camera problem - details are shown in the call window");
                win.setNotice("Camera: " + err + "  (log: " + Camera.logFile() + ")"); win.setLocalCam(false); for (CallLink l : links) l.sendCam(false); } }));
    }
    static void stopCamera() { if (cam != null) { cam.stop(); cam = null; } }

    static final GroupCallWindow.Actions ACTIONS = new GroupCallWindow.Actions() {
        public void accept() { }
        public void decline() { }
        public void leave() { GroupCalls.leave(); }
        public void mute(boolean on) { muted = on; if (mine != null) mine.mic = !on; for (CallLink l : links) l.sendMic(!on); }
        public void camera(boolean on) {
            if (!active) return;
            if (on) startCamera(); else { stopCamera(); camOn = false; mine.cam = false; mine.img = null; for (CallLink l : links) l.sendCam(false); }
            win.setLocalCam(on);
        }
        public void emote(String key) { if (CallFx.valid(key)) for (CallLink l : links) l.sendEmote(key); }
        public void hand(boolean up) { handUp = up; for (CallLink l : links) l.sendHand(up); }
    };

    // ---------------------------------------------------------------- leaving
    public static void leave() {
        if (!active) return;
        final Group g = group; final boolean alone = peers.isEmpty(), had = everHadPeers, rang = ringSent; final long secs = (System.currentTimeMillis() - startedAt) / 1000;
        if (alone && rang) for (String u : online(g)) Calls.sendSig("GEND", u, myCallId(), ip, port, false);   // stop the ringing of people who did not join
        active = false; stopTimers(); stopCamera();
        for (CallLink l : links) l.bye();
        if (audio != null) { audio.stop(); audio = null; }
        if (ss != null) { try { ss.close(); } catch (IOException e) { } ss = null; }
        for (CallLink l : links) l.close();
        links.clear(); peers.clear(); connecting.clear();
        GroupCallWindow w = win; win = null; if (w != null) w.ended("You left the call");
        if (me != null) {
            if (had && alone) MessagesLog.add(g.key(), SYS + "Group video call ended (" + Calls.dur(secs) + ")");           // the last person to leave writes the summary
            else if (!had && rang) MessagesLog.add(g.key(), SYS + "Missed group video call from " + name(me));
        }
    }
    static void stopTimers() {
        if (probeTimer != null) { probeTimer.stop(); probeTimer = null; }
        if (tickTimer != null) { tickTimer.stop(); tickTimer = null; }
    }
    /** Called on log out / window close. */
    public static void shutdown() { closeRing(null); leave(); }

    // ---------------------------------------------------------------- incoming ring
    static void incoming(Group g, String from) {
        ringGroup = g;
        ringWin = new GroupCallWindow(g, me.username, RING_ACTIONS); ringWin.setCaller(name(from)); ringWin.setMode(Mode.RINGING, g.members.size() + " members"); ringWin.open();
        notifyUser("Group video call", name(from) + " started a call in " + g.name);
        beepTimer = new javax.swing.Timer(2000, e -> Toolkit.getDefaultToolkit().beep()); beepTimer.setInitialDelay(0); beepTimer.start();
        final GroupCallWindow mineWin = ringWin;
        ringTimer = new javax.swing.Timer(RING_MS, e -> { if (ringWin == mineWin) closeRing("Missed group call"); }); ringTimer.setRepeats(false); ringTimer.start();
    }
    /** Closes the ringing window; with a message it stays visible for a moment, with null it just disappears. */
    static void closeRing(String msg) {
        if (ringTimer != null) { ringTimer.stop(); ringTimer = null; }
        if (beepTimer != null) { beepTimer.stop(); beepTimer = null; }
        GroupCallWindow w = ringWin; ringWin = null; ringGroup = null;
        if (w != null) { if (msg != null) w.ended(msg); else w.dispose(); }
    }
    static final GroupCallWindow.Actions RING_ACTIONS = new GroupCallWindow.Actions() {
        public void accept() { Group g = ringGroup; if (g == null) return; closeRing(null); if (active || Calls.inCall()) { toast("You are already in a call"); return; } join(g, false); }
        public void decline() { closeRing(null); }
        public void leave() { closeRing(null); }
        public void mute(boolean on) { } public void camera(boolean on) { } public void emote(String key) { } public void hand(boolean up) { }
    };
}
