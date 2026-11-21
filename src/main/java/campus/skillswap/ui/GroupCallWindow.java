package campus.skillswap.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import campus.skillswap.model.Group;
import static campus.skillswap.ui.Theme.*;

/**
 * The group call screen: a grid with one picture per person (camera or coloured initial), a green border around whoever is
 * speaking, a yellow hand badge (numbered in the order the hands went up) and emotes that float up from the sender's picture.
 * Not modal. Everything here runs on the Swing thread; {@link Tile} fields are volatile because network threads update them.
 */
public class GroupCallWindow extends JFrame {
    public interface Actions { void accept(); void decline(); void leave(); void mute(boolean on); void camera(boolean on); void screen(boolean on); void emote(String key); void hand(boolean up); }
    public enum Mode { RINGING, ACTIVE, ENDED }

    /** One person in the call. */
    public static class Tile {
        public final String user, name; public final boolean me;
        public volatile BufferedImage img, photo, screen; public volatile boolean cam = true, mic = true, sharing; public volatile long speakUntil;
        public Tile(String user, String name, boolean me) { this.user = user; this.name = name; this.me = me; }
    }

    static final Color STAGE = CallWindow.STAGE, BAR = CallWindow.BAR, RED = CallWindow.RED, OK = CallWindow.OK, TILE = new Color(0x2A2A34), TILE_LINE = new Color(0x3A3A46);
    final Actions act; final String groupKey, groupName, meUser; String caller = "";
    final List<Tile> tiles = new CopyOnWriteArrayList<>();
    final LinkedHashSet<String> hands = new LinkedHashSet<>();           // user names in the order the hands went up
    final CallFx.Layer fx = new CallFx.Layer();
    final Map<String, Rectangle> rects = new HashMap<>();                // where each picture was drawn last time (for emote start points)
    volatile String status = "", notice = "", flash = ""; volatile long flashUntil;
    Mode mode = Mode.ACTIVE; boolean muted, camOn = true, handUp, localSharing;
    final JPanel stage, bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12)); final javax.swing.Timer timer;

    public GroupCallWindow(Group g, String meUser, Actions act) {
        super("Group call - " + g.name); this.act = act; this.groupKey = g.key(); this.groupName = g.name; this.meUser = meUser;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() { public void windowClosing(java.awt.event.WindowEvent e) {
            if (mode == Mode.ENDED) dispose(); else if (mode == Mode.RINGING) act.decline(); else act.leave(); } });
        stage = new JPanel() { protected void paintComponent(Graphics g0) { paintStage((Graphics2D) g0, getWidth(), getHeight()); } };
        stage.setPreferredSize(new Dimension(960, 600)); stage.setBackground(STAGE);
        bar.setBackground(BAR); bar.setBorder(new javax.swing.border.MatteBorder(3, 0, 0, 0, INK));
        setLayout(new BorderLayout()); add(stage); add(bar, BorderLayout.SOUTH); pack(); setMinimumSize(new Dimension(660, 470)); iconify(this);
        timer = new javax.swing.Timer(250, e -> stage.repaint()); timer.start();       // speaking highlight, clock, floating emotes
    }
    @Override public void dispose() { timer.stop(); super.dispose(); }

    public void open() { setLocationRelativeTo(CallWindow.frame_()); setVisible(true); toFront(); }
    public void setCaller(String c) { caller = c == null ? "" : c; }
    public void repaintStage() { stage.repaint(); }

    // ---------------------------------------------------------------- state set by GroupCalls
    public void setMode(Mode m, String text) { mode = m; status = text; setAlwaysOnTop(m == Mode.RINGING); buildBar(); stage.repaint(); if (m == Mode.RINGING) toFront(); }
    public void setStatus(String t) { status = t; stage.repaint(); }
    public void setNotice(String t) { notice = t == null ? "" : t; stage.repaint(); }
    /** A short message at the bottom ("Ben joined"). */
    public void flash(String t) { flash = t; flashUntil = System.currentTimeMillis() + 3500; stage.repaint(); }
    public void addTile(Tile t) { tiles.add(t); stage.repaint(); }
    public void removeTile(String user) { tiles.removeIf(t -> t.user.equals(user)); hands.remove(user); stage.repaint(); }
    public void setHand(String user, boolean up) { if (up) hands.add(user); else hands.remove(user); stage.repaint(); }
    /** An emote from another person. */
    public void addEmote(String user, String key) { if (CallFx.valid(key) && fx.add(key, user)) stage.repaint(); }
    public void setLocalCam(boolean on) { camOn = on; buildBar(); stage.repaint(); }
    public void setLocalSharing(boolean on) { localSharing = on; buildBar(); stage.repaint(); }
    public void ended(String msg) {
        mode = Mode.ENDED; status = msg; setAlwaysOnTop(false); buildBar(); stage.repaint();
        javax.swing.Timer t = new javax.swing.Timer(2500, e -> dispose()); t.setRepeats(false); t.start();
    }

    // ---------------------------------------------------------------- buttons
    void pickEmote(String key) { if (fx.add(key, meUser)) { act.emote(key); stage.repaint(); } }
    JButton cbtn(String t, Color bg, Color fg, Runnable r) {
        JButton b = btn(t, r); b.setBackground(bg); b.setForeground(fg); b.setFont(f(Font.BOLD, 14)); b.setBorder(new EmptyBorder(10, 18, 10, 18)); return b;
    }
    void buildBar() {
        bar.removeAll();
        switch (mode) {
            case RINGING: bar.add(cbtn("DECLINE", RED, Color.WHITE, act::decline)); bar.add(cbtn("JOIN CALL", OK, Color.WHITE, act::accept)); break;
            case ACTIVE: {
                bar.add(cbtn(muted ? "UNMUTE" : "MUTE", muted ? YEL : FIELD, muted ? ONACC : TXT, () -> { muted = !muted; act.mute(muted); buildBar(); }));
                bar.add(cbtn(camOn ? "CAMERA OFF" : "CAMERA ON", camOn ? FIELD : YEL, camOn ? TXT : ONACC, () -> act.camera(!camOn)));
                bar.add(cbtn(localSharing ? "STOP SHARE" : "SHARE SCREEN", localSharing ? YEL : FIELD, localSharing ? ONACC : TXT, () -> act.screen(!localSharing)));
                JButton em = cbtn("EMOTES", FIELD, TXT, null); em.setIcon(CallFx.icon("LAUGH", 22)); em.setIconTextGap(8); em.addActionListener(e -> CallFx.showPalette(em, this::pickEmote)); bar.add(em);
                JButton hd = cbtn(handUp ? "LOWER HAND" : "RAISE HAND", handUp ? YEL : FIELD, handUp ? ONACC : TXT, () -> { handUp = !handUp; setHand(meUser, handUp); act.hand(handUp); buildBar(); });
                hd.setIcon(CallFx.handIcon(22)); hd.setIconTextGap(8); bar.add(hd);
                bar.add(cbtn("LEAVE", RED, Color.WHITE, act::leave)); break;
            }
            default: bar.add(cbtn("CLOSE", FIELD, TXT, this::dispose));
        }
        bar.revalidate(); bar.repaint();
    }

    // ---------------------------------------------------------------- painting
    static String initial(String n) { String s = n == null ? "" : n.replaceAll("^(Dr\\. )", ""); return s.isEmpty() ? "?" : s.substring(0, 1).toUpperCase(); }
    static String clip(Graphics2D g, String s, int max) { FontMetrics fm = g.getFontMetrics(); if (fm.stringWidth(s) <= max) return s; while (s.length() > 1 && fm.stringWidth(s + "...") > max) s = s.substring(0, s.length() - 1); return s + "..."; }

    void paintStage(Graphics2D g, int w, int h) {
        aa(g); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(STAGE); g.fillRect(0, 0, w, h); rects.clear();
        if (mode != Mode.ACTIVE) { paintCenter(g, w, h); return; }
        // title + clock, top left
        g.setFont(f(Font.BOLD, 20)); int bw = Math.max(190, 28 + g.getFontMetrics().stringWidth(groupName));
        g.setColor(new Color(0, 0, 0, 120)); g.fillRoundRect(16, 14, bw, 62, 14, 14);
        g.setColor(Color.WHITE); g.drawString(groupName, 28, 42); g.setColor(new Color(0xD0D0DC)); g.setFont(f(Font.PLAIN, 13)); g.drawString(status, 28, 64);
        paintTiles(g, w, h);
        // queue of raised hands, top right
        if (!hands.isEmpty()) {
            int x = w - 16, shown = 0; List<String> hs = new ArrayList<>(hands);
            for (int i = Math.min(hs.size(), 3) - 1; i >= 0; i--) { String nm = nameOf(hs.get(i)); int bwid = CallFx.badgeWidth(g, i + 1, nm); x -= bwid + 8; CallFx.paintHandBadge(g, x, 18, i + 1, nm); shown++; }
            if (hs.size() > shown) { g.setColor(Color.WHITE); g.setFont(f(Font.BOLD, 12)); String more = "+" + (hs.size() - shown) + " more"; g.drawString(more, x - 8 - g.getFontMetrics().stringWidth(more), 40); }
        }
        // messages at the bottom
        int by = h - 16;
        if (System.currentTimeMillis() < flashUntil && !flash.isEmpty()) {
            g.setFont(f(Font.BOLD, 13)); FontMetrics fm = g.getFontMetrics(); int tw = fm.stringWidth(flash) + 28;      // top centre, clear of the pictures
            g.setColor(new Color(0, 0, 0, 190)); g.fillRoundRect((w - tw) / 2, 28, tw, 30, 16, 16); g.setColor(Color.WHITE); g.drawString(flash, (w - tw) / 2 + 14, 48);
        }
        if (!notice.isEmpty()) {
            g.setFont(f(Font.PLAIN, 13)); FontMetrics fm = g.getFontMetrics(); List<String> ls = new ArrayList<>(); StringBuilder cur = new StringBuilder();
            for (String wd : notice.split(" ")) { if (fm.stringWidth(cur + " " + wd) > Math.min(w - 60, 620) && cur.length() > 0) { ls.add(cur.toString()); cur.setLength(0); } cur.append(cur.length() > 0 ? " " : "").append(wd); }
            if (cur.length() > 0) ls.add(cur.toString());
            int nh = ls.size() * 18 + 16, ny = by - nh - 6; g.setColor(new Color(0, 0, 0, 190)); g.fillRoundRect(16, ny, Math.min(w - 32, 650), nh, 14, 14);
            g.setColor(new Color(0xFFD43B)); for (int i = 0; i < ls.size(); i++) g.drawString(ls.get(i), 28, ny + 22 + i * 18);
        }
        fx.paint(g, rects::get, Math.max(48, Math.min(96, w / 11)));
    }
    String nameOf(String user) { for (Tile t : tiles) if (t.user.equals(user)) return t.name; return user; }
    int handOrder(String user) { int i = 1; for (String u : hands) { if (u.equals(user)) return i; i++; } return 0; }

    void paintTiles(Graphics2D g, int w, int h) {
        int n = tiles.size(); if (n == 0) return;
        Tile shared = null; for (Tile t : tiles) if (t.sharing) { shared = t; break; }
        if (shared != null) { paintSpotlight(g, shared, w, h); return; }
        int top = 88, gap = 12, cols = n <= 1 ? 1 : n <= 4 ? 2 : 3, rows = (n + cols - 1) / cols, aw = w - 32, ah = h - top - 16;
        int tw = (aw - gap * (cols - 1)) / cols, th = (ah - gap * (rows - 1)) / rows;
        if (tw * 3 > th * 4) tw = th * 4 / 3; else th = tw * 3 / 4;                       // 4:3 pictures
        int y = top + (ah - (rows * th + (rows - 1) * gap)) / 2, i = 0;
        for (int r = 0; r < rows; r++) {
            int inRow = Math.min(cols, n - r * cols), x = 16 + (aw - (inRow * tw + (inRow - 1) * gap)) / 2;
            for (int c = 0; c < inRow; c++, i++) { paintTile(g, tiles.get(i), x, y, tw, th); x += tw + gap; }
            y += th + gap;
        }
    }
    void paintSpotlight(Graphics2D g, Tile shared, int w, int h) {
        int top = 88, gap = 12, n = tiles.size();
        int strip = Math.min(132, Math.max(96, h / 5)), areaH = Math.max(140, h - top - strip - 24);
        Rectangle area = new Rectangle(16, top, w - 32, areaH);
        g.setColor(Color.BLACK); g.fillRect(area.x, area.y, area.width, area.height);
        BufferedImage sc = shared.screen;
        if (sc != null) {
            double s = Math.min((double) area.width / sc.getWidth(), (double) area.height / sc.getHeight());
            int iw = (int) (sc.getWidth() * s), ih = (int) (sc.getHeight() * s);
            g.drawImage(sc, area.x + (area.width - iw) / 2, area.y + (area.height - ih) / 2, iw, ih, null);
        } else {
            g.setColor(new Color(0xB8B8C4)); g.setFont(f(Font.BOLD, 15)); String m = "Connecting to " + shared.name + "'s screen...";
            g.drawString(m, area.x + (area.width - g.getFontMetrics().stringWidth(m)) / 2, area.y + area.height / 2);
        }
        frame(g, area.x, area.y, area.width, area.height, 3, YEL);
        g.setFont(f(Font.BOLD, 13)); String badge = (shared.me ? "You are" : shared.name + " is") + " sharing a screen";
        int bw = g.getFontMetrics().stringWidth(badge) + 28; g.setColor(new Color(0, 0, 0, 170)); g.fillRoundRect(w - bw - 16, 16, bw, 30, 15, 15);
        g.setColor(YEL); g.drawString(badge, w - bw - 2, 36);
        int th = Math.min(strip - 10, 96), tw = th * 4 / 3;
        if (n * tw + (n - 1) * gap > w - 32) { tw = Math.max(48, (w - 32 - gap * (n - 1)) / n); th = tw * 3 / 4; }
        int x = (w - (n * tw + (n - 1) * gap)) / 2, y = h - th - 14;
        for (Tile t : tiles) { paintTile(g, t, x, y, tw, th); x += tw + gap; }
        rects.put(shared.user, area);
    }
    void paintTile(Graphics2D g, Tile t, int x, int y, int w, int h) {
        rects.put(t.user, new Rectangle(x, y, w, h));
        boolean hand = hands.contains(t.user), speaking = t.mic && System.currentTimeMillis() < t.speakUntil;
        g.setColor(TILE); g.fillRect(x, y, w, h);
        BufferedImage im = t.img, ph = t.photo;
        if (t.cam && im != null) {
            double s = Math.max((double) w / im.getWidth(), (double) h / im.getHeight()); int iw = (int) Math.ceil(im.getWidth() * s), ih = (int) Math.ceil(im.getHeight() * s);
            Shape old = g.getClip(); g.setClip(x, y, w, h);
            if (t.me) g.drawImage(im, x + (w + iw) / 2, y + (h - ih) / 2, -iw, ih, null); else g.drawImage(im, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih, null);   // own picture mirrored, like a mirror
            g.setClip(old);
        } else if (ph != null) {
            double s = Math.max((double) w / ph.getWidth(), (double) h / ph.getHeight()); int iw = (int) Math.ceil(ph.getWidth() * s), ih = (int) Math.ceil(ph.getHeight() * s);
            Shape old = g.getClip(); g.setClip(x, y, w, h);
            g.drawImage(ph, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih, null);
            g.setClip(old);
        } else {
            int d = Math.max(40, Math.min(120, Math.min(w, h) * 5 / 11)), cx = x + (w - d) / 2, cy = y + (h - d) / 2 - 8;
            g.setColor(Color.BLACK); g.fillOval(cx + 4, cy + 4, d, d); g.setColor(pop(t.user)); g.fillOval(cx, cy, d, d); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(3.5f)); g.drawOval(cx, cy, d, d);
            g.setFont(f(Font.BOLD, d / 2)); FontMetrics m = g.getFontMetrics(); String ini = initial(t.name); g.setColor(ONACC);
            g.drawString(ini, cx + (d - m.stringWidth(ini)) / 2, cy + (d + m.getAscent() - m.getDescent()) / 2);
        }
        // name tag
        g.setFont(f(Font.BOLD, 12)); String label = clip(g, t.me ? t.name + " (you)" : t.name, w - 70); int lw = g.getFontMetrics().stringWidth(label) + 18;
        g.setColor(new Color(0, 0, 0, 165)); g.fillRoundRect(x + 8, y + h - 32, lw, 24, 12, 12); g.setColor(Color.WHITE); g.drawString(label, x + 17, y + h - 15);
        if (!t.mic) micOff(g, x + w - 22, y + h - 20);
        frame(g, x, y, w, h, hand || speaking ? 4 : 2, hand ? YEL : speaking ? OK : TILE_LINE);
        if (hand) CallFx.paintHandBadge(g, x + 8, y + 8, handOrder(t.user), "");
    }
    static void micOff(Graphics2D g, int cx, int cy) {
        g.setColor(RED); g.fillOval(cx - 13, cy - 13, 26, 26); g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fillRoundRect(cx - 3, cy - 9, 6, 11, 6, 6); g.drawArc(cx - 6, cy - 6, 12, 11, 180, 180); g.drawLine(cx, cy + 5, cx, cy + 8); g.drawLine(cx - 8, cy - 8, cx + 8, cy + 8);
    }
    /** Ringing / ended screen: big group badge and a few lines of text. */
    void paintCenter(Graphics2D g, int w, int h) {
        int d = 150, x = (w - d) / 2, y = h / 2 - d / 2 - 50;
        g.setColor(Color.BLACK); g.fillOval(x + 5, y + 5, d, d); g.setColor(pop(groupKey)); g.fillOval(x, y, d, d); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(4f)); g.drawOval(x, y, d, d);
        g.setFont(f(Font.BOLD, d / 2)); FontMetrics m = g.getFontMetrics(); String ini = initial(groupName); g.setColor(ONACC); g.drawString(ini, x + (d - m.stringWidth(ini)) / 2, y + (d + m.getAscent() - m.getDescent()) / 2);
        g.setColor(Color.WHITE); g.setFont(f(Font.BOLD, 26)); String t = clip(g, groupName, w - 80); g.drawString(t, (w - g.getFontMetrics().stringWidth(t)) / 2, y + d + 50);
        g.setColor(new Color(0xD0D0DC)); g.setFont(f(Font.PLAIN, 15));
        String l2 = mode == Mode.RINGING && !caller.isEmpty() ? caller + " started a group video call" : status; g.drawString(l2, (w - g.getFontMetrics().stringWidth(l2)) / 2, y + d + 78);
        if (mode == Mode.RINGING) g.drawString(status, (w - g.getFontMetrics().stringWidth(status)) / 2, y + d + 100);
    }
}
