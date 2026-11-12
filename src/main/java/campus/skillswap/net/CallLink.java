package campus.skillswap.net;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * One direct TCP connection between the two people in a call.
 * Frames: [type:1][length:4][payload]. Video frames are JPEGs, audio frames are 16 kHz mono 16-bit PCM.
 * Only the newest video frame is ever queued, so a slow network lowers the frame rate instead of delaying the sound.
 */
public class CallLink {
    public static final int VIDEO = 1, AUDIO = 2, CAM = 3, BYE = 4;
    public volatile Consumer<byte[]> onVideo, onAudio;
    public volatile Consumer<Boolean> onCam;
    public volatile Runnable onClose;
    public volatile boolean byeReceived;
    final Socket s; final DataInputStream in; final DataOutputStream out;
    final AtomicBoolean closed = new AtomicBoolean(); final AtomicReference<byte[]> pendingVideo = new AtomicReference<>();
    final Object wl = new Object(), vl = new Object();

    public CallLink(Socket s) throws IOException {
        this.s = s; s.setTcpNoDelay(true);
        in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 64 * 1024));
        out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 64 * 1024));
    }
    public void start() {
        Thread r = new Thread(this::readLoop, "call-reader"); r.setDaemon(true); r.start();
        Thread v = new Thread(this::videoLoop, "call-video-send"); v.setDaemon(true); v.start();
    }
    void readLoop() {
        try {
            while (!closed.get()) {
                int t = in.read(); if (t < 0) break;
                int n = in.readInt(); if (n < 0 || n > 4_000_000) break;
                byte[] b = new byte[n]; in.readFully(b);
                switch (t) {
                    case VIDEO: { Consumer<byte[]> c = onVideo; if (c != null) c.accept(b); break; }
                    case AUDIO: { Consumer<byte[]> c = onAudio; if (c != null) c.accept(b); break; }
                    case CAM: { Consumer<Boolean> c = onCam; if (c != null && n > 0) c.accept(b[0] == 1); break; }
                    case BYE: byeReceived = true; close(); return;
                    default: break;
                }
            }
        } catch (IOException e) { /* connection ended */ }
        finally { close(); }
    }
    void videoLoop() {
        while (!closed.get()) {
            byte[] f = pendingVideo.getAndSet(null);
            if (f == null) { synchronized (vl) { try { if (pendingVideo.get() == null && !closed.get()) vl.wait(500); } catch (InterruptedException e) { return; } } continue; }
            write(VIDEO, f);
        }
    }
    void write(int type, byte[] b) {
        synchronized (wl) {
            if (closed.get()) return;
            try { out.write(type); out.writeInt(b.length); out.write(b); out.flush(); } catch (IOException e) { close(); }
        }
    }
    public void sendAudio(byte[] b) { write(AUDIO, b); }
    public void sendVideo(byte[] jpeg) { pendingVideo.set(jpeg); synchronized (vl) { vl.notify(); } }
    public void sendCam(boolean on) { write(CAM, new byte[]{(byte) (on ? 1 : 0)}); }
    public void bye() { write(BYE, new byte[0]); }
    public boolean isClosed() { return closed.get(); }
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { s.close(); } catch (IOException e) { }
        synchronized (vl) { vl.notifyAll(); }
        Runnable r = onClose; if (r != null) r.run();
    }
}
