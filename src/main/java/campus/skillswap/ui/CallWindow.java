package campus.skillswap.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import static campus.skillswap.ui.Theme.*;

/** The call screen: remote video (or avatar), your own picture in the corner, and the call buttons. Not modal. */
public class CallWindow extends JFrame {
    public interface Actions { void accept(); void decline(); void hangup(); void mute(boolean on); void camera(boolean on); void screen(boolean on); void emote(String key); void hand(boolean up); }
    public enum Mode { OUTGOING, INCOMING, ACTIVE, ENDED }
    static final Color STAGE = new Color(0x14141A), BAR = new Color(0x1E1E26), RED = new Color(0xE5484D), OK = new Color(0x2FB344);

    final Actions act; final String peerKey, peerName;
    volatile BufferedImage remote, local, remoteScreen, localScreen; volatile boolean remoteCam = true, localCam, muted, localHand, remoteHand, localSharing, remoteSharing;
    final CallFx.Layer fx = new CallFx.Layer(); final javax.swing.Timer fxTimer;
    volatile String status = "", notice = ""; Mode mode = Mode.OUTGOING; final JPanel stage, bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 12));

    public CallWindow(String peerKey, String peerName, boolean cameraOn, Actions act) {
        super("Call with " + peerName); this.act = act; this.peerKey = peerKey; this.peerName = peerName; this.localCam = cameraOn;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() { public void windowClosing(java.awt.event.WindowEvent e) {
            if (mode == Mode.ENDED) dispose(); else if (mode == Mode.INCOMING) act.decline(); else act.hangup(); } });
        stage = new JPanel() { protected void paintComponent(Graphics g0) { paintStage((Graphics2D) g0, getWidth(), getHeight()); } };
        stage.setPreferredSize(new Dimension(860, 560)); stage.setBackground(STAGE);
        bar.setBackground(BAR); bar.setBorder(new javax.swing.border.MatteBorder(3, 0, 0, 0, INK));
        setLayout(new BorderLayout()); add(stage); add(bar, BorderLayout.SOUTH); pack(); setMinimumSize(new Dimension(520, 420));
        fxTimer = new javax.swing.Timer(33, e -> { if (fx.active()) stage.repaint(); }); fxTimer.start();     // animates floating emotes
    }
    @Override public void dispose() { fxTimer.stop(); super.dispose(); }

    /** An emote from the other person floats up over the call picture. */
    public void showEmote(String key, boolean mine) { if (CallFx.valid(key) && fx.add(key, mine ? "me" : "peer")) stage.repaint(); }
    public void setRemoteHand(boolean up) { remoteHand = up; stage.repaint(); }
    void pickEmote(String key) { if (fx.add(key, "me")) { act.emote(key); stage.repaint(); } }

    public void open() { setLocationRelativeTo(frame_()); setVisible(true); toFront(); }
    static Component frame_() { return AppShell.frame != null && AppShell.frame.isShowing() ? AppShell.frame : null; }

    public void setMode(Mode m, String text) {
        mode = m; status = text; setAlwaysOnTop(m == Mode.INCOMING); buildBar(); stage.repaint();
        if (m == Mode.INCOMING) toFront();
    }
    public void setStatus(String t) { status = t; stage.repaint(); }
    public void setNotice(String t) { notice = t == null ? "" : t; stage.repaint(); }
    public void setRemote(BufferedImage im) { remote = im; stage.repaint(); }
    public void setLocal(BufferedImage im) { local = im; stage.repaint(); }
    public void setRemoteCam(boolean on) { remoteCam = on; stage.repaint(); }
    public void setLocalCam(boolean on) { localCam = on; if (!on) local = null; buildBar(); stage.repaint(); }
    public void setRemoteScreen(BufferedImage im) { remoteScreen = im; remoteSharing = im != null; stage.repaint(); }
    public void stopRemoteScreen() { remoteScreen = null; remoteSharing = false; stage.repaint(); }
    public void setLocalScreen(BufferedImage im) { localScreen = im; stage.repaint(); }
    public void setLocalSharing(boolean on) { localSharing = on; if (!on) localScreen = null; buildBar(); stage.repaint(); }
    public void ended(String msg) {
        mode = Mode.ENDED; status = msg; setAlwaysOnTop(false); buildBar(); stage.repaint();
        javax.swing.Timer t = new javax.swing.Timer(2500, e -> dispose()); t.setRepeats(false); t.start();
    }

    JButton cbtn(String t, Color bg, Color fg, Runnable r) {
        JButton b = btn(t, r); b.setBackground(bg); b.setForeground(fg); b.setFont(f(Font.BOLD, 14)); b.setBorder(new EmptyBorder(10, 20, 10, 20)); return b;
    }
    void buildBar() {
        bar.removeAll();
        switch (mode) {
            case INCOMING:
                bar.add(cbtn("DECLINE", RED, Color.WHITE, act::decline)); bar.add(cbtn("ACCEPT", OK, Color.WHITE, act::accept)); break;
            case OUTGOING:
                bar.add(cbtn("CANCEL", RED, Color.WHITE, act::hangup)); break;
            case ACTIVE: {
                JButton[] mb = new JButton[1], cb = new JButton[1];
                bar.add(cbtn(muted ? "UNMUTE" : "MUTE", muted ? YEL : FIELD, muted ? ONACC : TXT, () -> { muted = !muted; act.mute(muted); buildBar(); }));
                bar.add(cbtn(localCam ? "CAMERA OFF" : "CAMERA ON", localCam ? FIELD : YEL, localCam ? TXT : ONACC, () -> act.camera(!localCam)));
                bar.add(cbtn(localSharing ? "STOP SHARE" : "SHARE SCREEN", localSharing ? YEL : FIELD, localSharing ? ONACC : TXT, () -> act.screen(!localSharing)));
                JButton em = cbtn("EMOTES", FIELD, TXT, null); em.setIcon(CallFx.icon("LAUGH", 22)); em.setIconTextGap(8); em.addActionListener(e -> CallFx.showPalette(em, this::pickEmote)); bar.add(em);
                JButton hd = cbtn(localHand ? "LOWER HAND" : "RAISE HAND", localHand ? YEL : FIELD, localHand ? ONACC : TXT, () -> { localHand = !localHand; act.hand(localHand); buildBar(); stage.repaint(); });
                hd.setIcon(CallFx.handIcon(22)); hd.setIconTextGap(8); bar.add(hd);
                bar.add(cbtn("END CALL", RED, Color.WHITE, act::hangup)); break;
            }
            default: bar.add(cbtn("CLOSE", FIELD, TXT, this::dispose));
        }
        bar.revalidate(); bar.repaint();
    }

    void paintStage(Graphics2D g, int w, int h) {
        aa(g); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(STAGE); g.fillRect(0, 0, w, h);
        BufferedImage r = remote; boolean sharing = mode == Mode.ACTIVE && remoteSharing;
        if (sharing) {
            BufferedImage sc = remoteScreen;
            if (sc != null) {
                double s = Math.min((double) w / sc.getWidth(), (double) h / sc.getHeight()); int iw = (int) (sc.getWidth() * s), ih = (int) (sc.getHeight() * s);
                g.setColor(Color.BLACK); g.fillRect((w - iw) / 2, (h - ih) / 2, iw, ih); g.drawImage(sc, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
            } else {
                g.setColor(new Color(0xB8B8C4)); g.setFont(f(Font.BOLD, 15)); String t = "Connecting to " + peerName + "'s screen...";
                g.drawString(t, (w - g.getFontMetrics().stringWidth(t)) / 2, h / 2);
            }
        } else if (r != null && remoteCam && mode == Mode.ACTIVE) {
            double s = Math.min((double) w / r.getWidth(), (double) h / r.getHeight()); int iw = (int) (r.getWidth() * s), ih = (int) (r.getHeight() * s);
            g.drawImage(r, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
        } else {
            int d = Math.min(150, Math.min(w, h) / 3), x = (w - d) / 2, y = h / 2 - d / 2 - 20;
            g.setColor(INK == null ? Color.BLACK : Color.BLACK); g.fillOval(x + 5, y + 5, d, d);
            g.setColor(pop(peerKey)); g.fillOval(x, y, d, d); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(4f)); g.drawOval(x, y, d, d);
            String ini = peerName.isEmpty() ? "?" : peerName.replaceAll("^(Dr\\. )", "").substring(0, 1).toUpperCase();
            g.setFont(f(Font.BOLD, d / 2)); FontMetrics m = g.getFontMetrics(); g.setColor(ONACC); g.drawString(ini, x + (d - m.stringWidth(ini)) / 2, y + (d + m.getAscent() - m.getDescent()) / 2);
            if (mode == Mode.ACTIVE && !remoteCam) { g.setFont(f(Font.PLAIN, 14)); g.setColor(new Color(0xB8B8C4)); String t = peerName + " turned the camera off"; g.drawString(t, (w - g.getFontMetrics().stringWidth(t)) / 2, y + d + 70); }
        }
        // name + status, top left
        g.setColor(new Color(0, 0, 0, 120)); g.fillRoundRect(16, 14, Math.max(190, 28 + g.getFontMetrics(f(Font.BOLD, 20)).stringWidth(peerName)), 62, 14, 14);
        g.setColor(Color.WHITE); g.setFont(f(Font.BOLD, 20)); g.drawString(peerName, 28, 42);
        g.setColor(new Color(0xD0D0DC)); g.setFont(f(Font.PLAIN, 13)); g.drawString(status, 28, 64);
        if (sharing) {
            g.setFont(f(Font.BOLD, 13)); String t = peerName + " is sharing a screen";
            int tw = g.getFontMetrics().stringWidth(t) + 28; g.setColor(new Color(0, 0, 0, 170)); g.fillRoundRect(w - tw - 16, 16, tw, 30, 15, 15);
            g.setColor(YEL); g.drawString(t, w - tw - 2, 36);
        }
        if (!notice.isEmpty()) {   // e.g. why the camera does not work; wrapped over the bottom of the stage
            g.setFont(f(Font.PLAIN, 13)); FontMetrics fm = g.getFontMetrics(); java.util.List<String> ls = new java.util.ArrayList<>(); StringBuilder cur = new StringBuilder();
            for (String wd : notice.split(" ")) { if (fm.stringWidth(cur + " " + wd) > Math.min(w - 60, 620) && cur.length() > 0) { ls.add(cur.toString()); cur.setLength(0); } cur.append(cur.length() > 0 ? " " : "").append(wd); }
            if (cur.length() > 0) ls.add(cur.toString());
            int bh = ls.size() * 18 + 16, by = h - bh - 20; g.setColor(new Color(0, 0, 0, 170)); g.fillRoundRect(16, by, Math.min(w - 32, 650), bh, 14, 14);
            g.setColor(new Color(0xFFD43B)); for (int i = 0; i < ls.size(); i++) g.drawString(ls.get(i), 28, by + 22 + i * 18);
        }
        // own picture, bottom right
        if (mode == Mode.ACTIVE) {
            int pw = Math.max(120, Math.min(200, w / 4)), ph = pw * 3 / 4, px = w - pw - 18, py = h - ph - 18; BufferedImage l = localSharing ? localScreen : local;
            g.setColor(Color.BLACK); g.fillRect(px + 4, py + 4, pw, ph); g.setColor(new Color(0x2A2A34)); g.fillRect(px, py, pw, ph);
            if (l != null && (localSharing || localCam)) { double s = Math.max((double) pw / l.getWidth(), (double) ph / l.getHeight()); int iw = (int) (l.getWidth() * s), ih = (int) (l.getHeight() * s);
                Shape old = g.getClip(); g.setClip(px, py, pw, ph);
                if (localSharing) g.drawImage(l, px + (pw - iw) / 2, py + (ph - ih) / 2, iw, ih, null);
                else g.drawImage(l, px + (pw + iw) / 2, py + (ph - ih) / 2, -iw, ih, null);
                g.setClip(old); }
            else { g.setColor(new Color(0xB8B8C4)); g.setFont(f(Font.BOLD, 12)); String t = localSharing ? "Starting screen..." : localCam ? "Starting camera..." : "Camera off"; g.drawString(t, px + (pw - g.getFontMetrics().stringWidth(t)) / 2, py + ph / 2 + 4); }
            if (localSharing) { g.setFont(f(Font.BOLD, 10)); g.setColor(YEL); String t = "YOUR SCREEN"; g.drawString(t, px + 8, py + 16); }
            g.setColor(Color.WHITE); g.setStroke(new BasicStroke(3f)); g.drawRect(px, py, pw, ph);
            // raised hands + floating emotes
            if (remoteHand) { String t = peerName + " raised a hand"; CallFx.paintHandBadge(g, w - 20 - CallFx.badgeWidth(g, 0, t), 14, 0, t); }
            if (localHand) CallFx.paintHandBadge(g, px + 8, py + 8, 0, "");
            final Rectangle meR = new Rectangle(px, py, pw, ph), peerR = new Rectangle(w / 8, 0, w * 3 / 4, h - 10);
            fx.paint(g, who -> who.equals("me") ? meR : peerR, 84);
        }
    }
}
