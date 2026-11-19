package campus.skillswap.net;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * One direct TCP connection between the two people in a call.
 * Frames: [type:1][length:4][payload]. Video frames are JPEGs, audio frames are 16 kHz mono 16-bit PCM,
 * EMOTE carries the emote key, HAND / MIC / CAM carry one on/off byte.
 * Only the newest video frame is ever queued, so a slow network lowers the frame rate instead of delaying the sound.
 */
public class CallLink {
    public static final int VIDEO = 1, AUDIO = 2, CAM = 3, BYE = 4, EMOTE = 5, HAND = 6, MIC = 7, SCREEN = 8, SCREEN_OFF = 9;
    public volatile Consumer<byte[]> onVideo, onAudio, onScreen;
    public volatile Consumer<Boolean> onCam, onHand, onMic;
    public volatile Consumer<String> onEmote;
    public volatile Runnable onClose, onScreenOff;
    public volatile boolean byeReceived;
    volatile boolean screenOn;
    final Socket s; final DataInputStream in; final DataOutputStream out;
    final AtomicBoolean closed = new AtomicBoolean(); final AtomicReference<byte[]> pendingVideo = new AtomicReference<>(), pendingScreen = new AtomicReference<>();
    final Object wl = new Object(), vl = new Object(), sl = new Object();

    public CallLink(Socket s) throws IOException {
        this.s = s; s.setTcpNoDelay(true);
        in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 64 * 1024));
        out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 64 * 1024));
    }
    public void start() {
        Thread r = new Thread(this::readLoop, "call-reader"); r.setDaemon(true); r.start();
        Thread v = new Thread(this::videoLoop, "call-video-send"); v.setDaemon(true); v.start();
        Thread s = new Thread(this::screenLoop, "call-screen-send"); s.setDaemon(true); s.start();
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
                    case EMOTE: { Consumer<String> c = onEmote; if (c != null && n > 0 && n <= 32) c.accept(new String(b, java.nio.charset.StandardCharsets.UTF_8)); break; }
                    case HAND: { Consumer<Boolean> c = onHand; if (c != null && n > 0) c.accept(b[0] == 1); break; }
                    case MIC: { Consumer<Boolean> c = onMic; if (c != null && n > 0) c.accept(b[0] == 1); break; }
                    case SCREEN: { Consumer<byte[]> c = onScreen; if (c != null) c.accept(b); break; }
                    case SCREEN_OFF: { Runnable r = onScreenOff; if (r != null) r.run(); break; }
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
    void screenLoop() {
        while (!closed.get()) {
            byte[] f = pendingScreen.getAndSet(null);
            if (f == null) { synchronized (sl) { try { if (pendingScreen.get() == null && !closed.get()) sl.wait(500); } catch (InterruptedException e) { return; } } continue; }
            synchronized (wl) { if (!closed.get() && screenOn) write(SCREEN, f); }
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
    public void beginScreen() { pendingScreen.set(null); synchronized (wl) { if (!closed.get()) screenOn = true; } }
    public void sendScreen(byte[] jpeg) { if (!screenOn) return; pendingScreen.set(jpeg); synchronized (sl) { sl.notify(); } }
    public void sendScreenOff() { pendingScreen.set(null); synchronized (wl) { screenOn = false; write(SCREEN_OFF, new byte[0]); } }
    public void sendCam(boolean on) { write(CAM, new byte[]{(byte) (on ? 1 : 0)}); }
    public void sendEmote(String key) { write(EMOTE, key.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public void sendHand(boolean up) { write(HAND, new byte[]{(byte) (up ? 1 : 0)}); }
    public void sendMic(boolean on) { write(MIC, new byte[]{(byte) (on ? 1 : 0)}); }
    public void bye() { write(BYE, new byte[0]); }
    public boolean isClosed() { return closed.get(); }
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { s.close(); } catch (IOException e) { }
        synchronized (vl) { vl.notifyAll(); }
        synchronized (sl) { sl.notifyAll(); }
        Runnable r = onClose; if (r != null) r.run();
    }
}
