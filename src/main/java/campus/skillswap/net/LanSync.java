package campus.skillswap.net;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.ToIntFunction;
import javax.swing.SwingUtilities;
import campus.skillswap.model.*;
import static campus.skillswap.config.AppConfig.*;
import static campus.skillswap.core.AppState.*;
import static campus.skillswap.data.Database.*;
import static campus.skillswap.data.Seed.*;
import static campus.skillswap.service.Stats.*;
import static campus.skillswap.ui.Notifier.*;
import static campus.skillswap.ui.AppShell.*;
import static campus.skillswap.ui.media.Videos.*;

/**
 * Database-free test/classroom mode: one computer HOSTS, the others JOIN over the same Wi-Fi / LAN.
 * <ul>
 *   <li>The host keeps the data (saved to ~/.skillswap/lan-data.bin) and relays every change to all joined computers.</li>
 *   <li>Changes travel as small row-level deltas (same row fingerprints the PostgreSQL mode uses).</li>
 *   <li>Reel videos use short side connections; the host stores them in ~/.skillswap/lan-videos.</li>
 *   <li>A 4-digit join code (shown on the host) keeps other people on the network out.</li>
 * </ul>
 * If two people edit the very same row at the very same moment, the last one wins.
 */
public class LanSync {
    public static final int DEFAULT_PORT = 5577;
    static final long MAX_VID = 70L * 1024 * 1024;
    public static volatile String mode = "off";                    // "off" | "host" | "client"
    public static String address = "", code = "", peerHost = ""; public static int peerPort;
    static ServerSocket server; static Socket csock; static DataOutputStream cout;
    static final List<DataOutputStream> peers = Collections.synchronizedList(new ArrayList<>());
    static final ExecutorService NET = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "lan-net"); t.setDaemon(true); return t; });
    static final List<Delta> pending = new ArrayList<>();            // received changes waiting for a quiet moment (EDT only)
    static javax.swing.Timer flusher;
    static final Path VID = DIR.resolve("lan-videos"), DATA = DIR.resolve("lan-data.bin");

    /** The changed rows of one save. */
    public static class Delta implements Serializable {
        List<User> users = new ArrayList<>(); List<Post> posts = new ArrayList<>(); List<Sess> sessions = new ArrayList<>();
        List<Msg> msgs = new ArrayList<>(); List<Report> reports = new ArrayList<>(); List<Integer> delPosts = new ArrayList<>(); String lastSync;
        boolean empty() { return users.isEmpty() && posts.isEmpty() && sessions.isEmpty() && msgs.isEmpty() && reports.isEmpty() && delPosts.isEmpty() && lastSync == null; }
    }

    public static boolean active() { return !mode.equals("off"); }
    public static String status() {
        return mode.equals("host") ? "LAN host " + address + "  (join code " + code + ", " + peers.size() + " joined)" : "Joined LAN host " + address;
    }
    public static String lanIp() {
        try { for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) { if (!ni.isUp() || ni.isLoopback()) continue;
            for (InetAddress a : Collections.list(ni.getInetAddresses())) if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress(); } } catch (Exception e) { }
        return "127.0.0.1";
    }

    // ---------- serialization (only our own classes may be read from the network) ----------
    static byte[] pack(Object o) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream(); ObjectOutputStream s = new ObjectOutputStream(b)) { s.writeObject(o); s.flush(); return b.toByteArray(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    static Object unpack(byte[] d) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(d))) {
            in.setObjectInputFilter(info -> {
                if (info.streamBytes() > 80_000_000 || info.depth() > 40) return ObjectInputFilter.Status.REJECTED;
                Class<?> c = info.serialClass(); if (c == null) return ObjectInputFilter.Status.UNDECIDED;
                String n = c.getName();
                return c.isArray() || n.startsWith("campus.skillswap.model.") || n.startsWith("campus.skillswap.net.LanSync$") || n.startsWith("java.util.")
                    || n.startsWith("java.lang.") || n.startsWith("java.time.") ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED; });
            return in.readObject();
        }
    }
    static void copyN(InputStream in, OutputStream out, long n) throws IOException {
        byte[] buf = new byte[64 * 1024];
        while (n > 0) { int r = in.read(buf, 0, (int) Math.min(buf.length, n)); if (r < 0) throw new EOFException("Connection closed during transfer"); out.write(buf, 0, r); n -= r; }
    }

    // ---------- host ----------
    public static void startHost(int port) throws Exception {
        ServerSocket ss = new ServerSocket(port);                    // fails right away if the port is already in use
        stop(); server = ss; code = String.format("%04d", new Random().nextInt(10000)); mode = "host"; address = lanIp() + ":" + port;
        try (InputStream in = Files.newInputStream(DATA)) { S = (Store) unpack(in.readAllBytes()); }
        catch (Exception e) { S = new Store(); seed(); }
        persist(); snap = fingerprints(S);
        Thread t = new Thread(() -> { while (!ss.isClosed()) { try { Socket c = ss.accept(); Thread h = new Thread(() -> handle(c), "lan-client"); h.setDaemon(true); h.start(); } catch (IOException e) { break; } } }, "lan-accept");
        t.setDaemon(true); t.start();
    }
    static void persist() { try { Files.createDirectories(DIR); Files.write(DATA, pack(S)); } catch (Exception e) { /* best effort */ } }

    static void handle(Socket c) {
        int kind = 0;
        try {
            c.setTcpNoDelay(true); c.setSoTimeout(15000);
            DataInputStream in = new DataInputStream(new BufferedInputStream(c.getInputStream())); DataOutputStream out = new DataOutputStream(new BufferedOutputStream(c.getOutputStream()));
            kind = in.read(); String cd = in.readUTF();
            if (!cd.equals(code)) { out.writeInt(-1); out.flush(); return; }
            if (kind == 'S') {
                SwingUtilities.invokeAndWait(() -> { try { flush(true); byte[] snapshot = pack(S); synchronized (out) { out.writeInt(snapshot.length); out.write(snapshot); out.flush(); peers.add(out); } } catch (IOException e) { throw new UncheckedIOException(e); } });
                c.setSoTimeout(0);
                try { while (true) { int n = in.readInt(); if (n <= 0 || n > 50_000_000) break; byte[] b = new byte[n]; in.readFully(b); Delta d = (Delta) unpack(b); relay(b, out); SwingUtilities.invokeLater(() -> onDelta(d)); } }
                finally { peers.remove(out); }
            } else if (kind == 'P') {
                int id = in.readInt(); long len = in.readLong(); if (len < 0 || len > MAX_VID) throw new IOException("Video too large");
                Files.createDirectories(VID); Path tmp;
                tmp = VID.resolve(id + "." + System.nanoTime() + ".part"); try (OutputStream o = Files.newOutputStream(tmp)) { copyN(in, o, len); } catch (IOException ex) { Files.deleteIfExists(tmp); throw ex; }
                Files.move(tmp, VID.resolve(id + ".bin"), StandardCopyOption.REPLACE_EXISTING); out.writeInt(1); out.flush();
            } else if (kind == 'G') {
                int id = in.readInt(); Path p = VID.resolve(id + ".bin");
                if (!Files.exists(p)) out.writeLong(-1); else { out.writeLong(Files.size(p)); Files.copy(p, out); }
                out.flush();
            }
        } catch (Exception e) { /* client went away */ }
        finally { try { c.close(); } catch (IOException e) { } }
    }
    static void relay(byte[] b, DataOutputStream except) {
        NET.submit(() -> { for (DataOutputStream p : new ArrayList<>(peers)) { if (p == except) continue;
            try { synchronized (p) { p.writeInt(b.length); p.write(b); p.flush(); } } catch (IOException e) { peers.remove(p); } } });
    }

    // ---------- client ----------
    public static void join(String host, int port, String joinCode) throws Exception {
        Socket s = new Socket(); s.connect(new InetSocketAddress(host, port), 4000); s.setTcpNoDelay(true);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream())); DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        out.write('S'); out.writeUTF(joinCode); out.flush(); s.setSoTimeout(10000);
        int n = in.readInt(); if (n < 0) { s.close(); throw new IOException("Wrong join code"); }
        byte[] b = new byte[n]; in.readFully(b); s.setSoTimeout(0);
        Store st = (Store) unpack(b);
        stop(); S = st; snap = fingerprints(st); csock = s; cout = out; mode = "client"; peerHost = host; peerPort = port; code = joinCode; address = host + ":" + port;
        Thread r = new Thread(() -> {
            try { while (true) { int k = in.readInt(); byte[] d = new byte[k]; in.readFully(d); Delta dl = (Delta) unpack(d); SwingUtilities.invokeLater(() -> onDelta(dl)); } } catch (Exception e) { }
            SwingUtilities.invokeLater(() -> { if (csock == s && mode.equals("client")) { mode = "off"; toast("Lost connection to the host"); } }); }, "lan-reader");
        r.setDaemon(true); r.start();
    }
    static void sendUp(byte[] b) {
        DataOutputStream o = cout; if (o == null) return;
        NET.submit(() -> { try { synchronized (o) { o.writeInt(b.length); o.write(b); o.flush(); } }
            catch (IOException e) { SwingUtilities.invokeLater(() -> { if (cout == o) { mode = "off"; cout = null; toast("Lost connection to the host"); } }); } });
    }
    public static void stop() {
        mode = "off"; peers.clear();
        try { if (server != null) server.close(); } catch (IOException e) { } try { if (csock != null) csock.close(); } catch (IOException e) { }
        server = null; csock = null; cout = null;
    }

    // ---------- called by Database ----------
    public static void initData() { snap = fingerprints(S); ver++; if (flusher == null) { flusher = new javax.swing.Timer(1000, e -> flush(false)); flusher.start(); } }
    public static int newId() {
        int id; do { id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE); } while (idUsed(id)); return id;
    }
    static boolean idUsed(int id) {
        return S.posts.stream().anyMatch(x -> x.id == id) || S.sessions.stream().anyMatch(x -> x.id == id) || S.msgs.stream().anyMatch(x -> x.id == id) || S.reports.stream().anyMatch(x -> x.id == id);
    }
    /** Find the rows that changed since the last save and send just those. */
    public static void save() {
        ver++; Map<String, String> now = fingerprints(S); Delta d = new Delta();
        for (User u : S.users.values()) if (chg("u:" + u.username, now)) d.users.add(u);
        for (Post p : S.posts) if (chg("p:" + p.id, now)) d.posts.add(p);
        for (Sess s : S.sessions) if (chg("s:" + s.id, now)) d.sessions.add(s);
        for (Msg m : S.msgs) if (chg("m:" + m.id, now)) d.msgs.add(m);
        for (Report r : S.reports) if (chg("r:" + r.id, now)) d.reports.add(r);
        if (chg("meta:sync", now)) d.lastSync = S.lastSync;
        for (String k : snap.keySet()) if (k.startsWith("p:") && !now.containsKey(k)) d.delPosts.add(Integer.parseInt(k.substring(2)));
        snap = now; if (d.empty()) return;
        byte[] b = pack(d);
        if (mode.equals("host")) { persist(); relay(b, null); } else sendUp(b);
    }

    // ---------- applying changes that arrived from others ----------
    static void onDelta(Delta d) { pending.add(d); flush(false); }
    static <T> void upsert(List<T> l, T x, ToIntFunction<T> id) {
        for (int i = 0; i < l.size(); i++) if (id.applyAsInt(l.get(i)) == id.applyAsInt(x)) { l.set(i, x); return; }
        l.add(x);
    }
    static void apply(Delta d) {
        for (User u : d.users) S.users.put(u.username, u);
        for (Post p : d.posts) upsert(S.posts, p, x -> x.id);
        for (Sess s : d.sessions) upsert(S.sessions, s, x -> x.id);
        for (Msg m : d.msgs) upsert(S.msgs, m, x -> x.id);
        for (Report r : d.reports) upsert(S.reports, r, x -> x.id);
        if (d.lastSync != null) S.lastSync = d.lastSync;
        for (int id : d.delPosts) S.posts.removeIf(p -> p.id == id);
    }
    /** Apply waiting changes; if a dialog is open or a video is playing, wait (retried every second). */
    static void flush(boolean force) {
        if (pending.isEmpty()) return;
        if (!force && (busy > 0 || videoOpen())) return;
        List<Delta> ds = new ArrayList<>(pending); pending.clear();
        Map<Integer, String> old = new HashMap<>(); for (Sess s : S.sessions) old.put(s.id, s.status);
        long cutoff = lastMsgSeen;
        for (Delta d : ds) apply(d);
        snap = fingerprints(S); ver++;
        if (mode.equals("host")) persist();
        if (me == null) return;
        me = S.users.get(me.username); if (me == null) { rootCards.show(root, "login"); return; }
        if (force) return;
        for (Delta d : ds) {
            for (Msg m : d.msgs) if (m.to.equals(me.username) && m.time > cutoff) { lastMsgSeen = Math.max(lastMsgSeen, m.time);
                if (!(page.equals("Messages") && m.from.equals(chatWith))) notifyUser("New message", name(m.from) + ": " + m.text); }
            for (Sess s : d.sessions) if (involves(s, me)) { String o = old.get(s.id); boolean req = s.requester.equals(me.username);
                if (o == null && s.status.equals("PENDING") && !req) notifyUser("New swap request", name(s.requester) + " wants a " + s.skill + " session");
                else if ("PENDING".equals(o) && req && s.status.equals("CONFIRMED")) notifyUser("Swap accepted", name(other(s)) + " accepted your " + s.skill + " session");
                else if ("PENDING".equals(o) && req && s.status.equals("DECLINED")) notifyUser("Swap declined", name(other(s)) + " declined your " + s.skill + " request"); }
        }
        show(page);
    }

    // ---------- reel videos ----------
    public static void putVideo(int postId, File f) throws IOException {
        if (mode.equals("host")) { Files.createDirectories(VID); Path t = VID.resolve(postId + "." + System.nanoTime() + ".part"); Files.copy(f.toPath(), t); Files.move(t, VID.resolve(postId + ".bin"), StandardCopyOption.REPLACE_EXISTING); return; }
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(peerHost, peerPort), 4000); s.setSoTimeout(60000);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream())); DataInputStream in = new DataInputStream(s.getInputStream());
            out.write('P'); out.writeUTF(code); out.writeInt(postId); out.writeLong(f.length()); Files.copy(f.toPath(), out); out.flush();
            if (in.readInt() != 1) throw new IOException("Host refused the video");
        }
    }
    public static void getVideo(int postId, Path dest) throws IOException {
        if (mode.equals("host")) { Path p = VID.resolve(postId + ".bin"); if (!Files.exists(p)) throw new IOException("Video not found on the host"); Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING); return; }
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(peerHost, peerPort), 4000); s.setSoTimeout(60000);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream())); DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out.write('G'); out.writeUTF(code); out.writeInt(postId); out.flush();
            long n = in.readLong(); if (n < 0) throw new IOException("Video not found on the host");
            try (OutputStream o = Files.newOutputStream(dest)) { copyN(in, o, n); }
        }
    }
}
