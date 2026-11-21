package campus.skillswap.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static campus.skillswap.ui.Theme.*;

/** The group-call screen (Google Meet style): a grid of everyone in the call, click a tile to spotlight it. Not modal. */
public class MeetWindow extends JFrame {
    /** One person in the call. The call code fills in the picture and the mic / camera / speaking state. */
    public static class Tile {
        public final String key, name; public final boolean self;
        public volatile BufferedImage img; public volatile boolean cam = true, mic = true; public volatile long spoke;
        public Tile(String key, String name, boolean self) { this.key = key; this.name = name; this.self = self; }
    }
    public interface Actions { void mute(boolean on); void camera(boolean on); void leave(); }
    static final Color STAGE = new Color(0x14141A), CELL = new Color(0x23232D), BAR = new Color(0x1E1E26), RED = new Color(0xE5484D), OK = new Color(0x2FB344);

    final List<Tile> tiles = new CopyOnWriteArrayList<>(); final Actions act; final String title;
    volatile String status = "", notice = ""; volatile boolean muted, camOn, over; String spot;
    final JPanel stage, bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 12)); final javax.swing.Timer anim;

    public MeetWindow(String groupName, boolean camOn, Actions act) {
        super("Group call - " + groupName); this.act = act; this.title = groupName; this.camOn = camOn;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { if (over) dispose(); else act.leave(); } });
        stage = new JPanel() { protected void paintComponent(Graphics g0) { paintStage((Graphics2D) g0, getWidth(), getHeight()); } };
        stage.setPreferredSize(new Dimension(980, 600)); stage.setBackground(STAGE);
        stage.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { click(e.getX(), e.getY()); } });
        bar.setBackground(BAR); bar.setBorder(new javax.swing.border.MatteBorder(3, 0, 0, 0, INK));
        setLayout(new BorderLayout()); add(stage); add(bar, BorderLayout.SOUTH); buildBar(); pack(); setMinimumSize(new Dimension(560, 440)); iconify(this);
        anim = new javax.swing.Timer(400, e -> stage.repaint()); anim.start();          // lets the green "speaking" outline fade
    }
    @Override public void dispose() { anim.stop(); super.dispose(); }

    public void open() { setLocationRelativeTo(AppShell.frame != null && AppShell.frame.isShowing() ? AppShell.frame : null); setVisible(true); toFront(); }
    public Tile addTile(String key, String name, boolean self) { Tile t = new Tile(key, name, self); if (self) tiles.add(0, t); else tiles.add(t); stage.repaint(); return t; }
    public void removeTile(String key) { tiles.removeIf(t -> t.key.equals(key)); if (key.equals(spot)) spot = null; stage.repaint(); }
    public void refresh() { stage.repaint(); }
    public void setStatus(String s) { status = s; stage.repaint(); }
    public void setNotice(String s) { notice = s == null ? "" : s; stage.repaint(); }
    public void setCamOn(boolean on) { camOn = on; buildBar(); stage.repaint(); }
    public void ended(String msg) {
        over = true; status = msg; anim.stop(); bar.removeAll(); bar.add(cbtn("CLOSE", FIELD, TXT, this::dispose)); bar.revalidate(); bar.repaint(); stage.repaint();
        javax.swing.Timer t = new javax.swing.Timer(2500, e -> dispose()); t.setRepeats(false); t.start();
    }

    JButton cbtn(String t, Color bg, Color fg, Runnable r) {
        JButton b = btn(t, r); b.setBackground(bg); b.setForeground(fg); b.setFont(f(Font.BOLD, 14)); b.setBorder(new EmptyBorder(10, 20, 10, 20)); return b;
    }
    void buildBar() {
        bar.removeAll();
        bar.add(cbtn(muted ? "UNMUTE" : "MUTE", muted ? YEL : FIELD, muted ? ONACC : TXT, () -> { muted = !muted; act.mute(muted); buildBar(); }));
        bar.add(cbtn(camOn ? "CAMERA OFF" : "CAMERA ON", camOn ? FIELD : YEL, camOn ? TXT : ONACC, () -> act.camera(!camOn)));
        bar.add(cbtn("LEAVE", RED, Color.WHITE, act::leave)); bar.revalidate(); bar.repaint();
    }

    // ---- layout: grid, or one big tile + a strip when somebody is spotlighted ----
    List<Tile> ordered() { List<Tile> l = new ArrayList<>(tiles); if (spot != null) for (Tile t : l) if (t.key.equals(spot)) { l.remove(t); l.add(0, t); break; } return l; }
    List<Rectangle> layout(int n, int w, int h, boolean spotlight) {
        List<Rectangle> r = new ArrayList<>(); int pad = 8, top = 86;                              // room for the title overlay
        int ah = h - top - pad;
        if (spotlight && n > 1) {
            int mw = (int) (w * 0.74) - pad * 2; r.add(new Rectangle(pad, top, mw, ah)); int k = n - 1, sx = pad * 2 + mw, sw = w - sx - pad, sh = Math.min(sw * 3 / 4, (ah - pad * (k - 1)) / k);
            for (int i = 0; i < k; i++) r.add(new Rectangle(sx, top + i * (sh + pad), sw, sh));
        } else {
            int cols = (int) Math.ceil(Math.sqrt(n)), rows = (int) Math.ceil(n / (double) cols), cw = (w - pad * (cols + 1)) / cols, ch = (ah - pad * (rows - 1)) / rows;
            for (int i = 0; i < n; i++) { int rr = i / cols, cc = i % cols, inRow = Math.min(cols, n - rr * cols), off = (w - (inRow * cw + (inRow - 1) * pad)) / 2;   // last row is centred
                r.add(new Rectangle(off + cc * (cw + pad), top + rr * (ch + pad), cw, ch)); }
        }
        return r;
    }
    void click(int x, int y) {
        List<Tile> ts = ordered(); List<Rectangle> rs = layout(ts.size(), stage.getWidth(), stage.getHeight(), spot != null);
        for (int i = 0; i < rs.size(); i++) if (rs.get(i).contains(x, y)) { String k = ts.get(i).key; spot = k.equals(spot) ? null : k; stage.repaint(); return; }
    }

    void paintStage(Graphics2D g, int w, int h) {
        aa(g); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(STAGE); g.fillRect(0, 0, w, h);
        List<Tile> ts = ordered(); List<Rectangle> rs = layout(ts.size(), w, h, spot != null);
        for (int i = 0; i < ts.size(); i++) paintTile(g, ts.get(i), rs.get(i));
        g.setColor(Color.WHITE); g.setFont(f(Font.BOLD, 20)); g.drawString(title, 18, 34);
        g.setColor(new Color(0xD0D0DC)); g.setFont(f(Font.PLAIN, 13)); g.drawString(status, 18, 56);
        if (spot != null) { g.setColor(new Color(0x8C8CA0)); g.drawString("Click the big tile again to go back to the grid", 18, 74); }
        if (!notice.isEmpty()) { g.setFont(f(Font.PLAIN, 13)); FontMetrics fm = g.getFontMetrics(); int bw = Math.min(w - 32, fm.stringWidth(notice) + 24), by = h - 44;
            g.setColor(new Color(0, 0, 0, 190)); g.fillRoundRect(16, by, bw, 30, 12, 12); g.setColor(new Color(0xFFD43B)); g.drawString(notice, 28, by + 20); }
    }
    void paintTile(Graphics2D g, Tile t, Rectangle r) {
        Shape old = g.getClip(); g.setClip(r.x, r.y, r.width, r.height); g.setColor(CELL); g.fillRect(r.x, r.y, r.width, r.height);
        BufferedImage im = t.img;
        if (im != null && t.cam) {                                                                    // "cover": fill the tile, crop the edges
            double s = Math.max((double) r.width / im.getWidth(), (double) r.height / im.getHeight()); int iw = (int) (im.getWidth() * s), ih = (int) (im.getHeight() * s), ix = r.x + (r.width - iw) / 2, iy = r.y + (r.height - ih) / 2;
            if (t.self) g.drawImage(im, ix + iw, iy, -iw, ih, null); else g.drawImage(im, ix, iy, iw, ih, null);   // your own picture is mirrored
        } else {
            int d = Math.max(36, Math.min(110, Math.min(r.width, r.height) / 2)), x = r.x + (r.width - d) / 2, y = r.y + (r.height - d) / 2 - 10;
            g.setColor(Color.BLACK); g.fillOval(x + 4, y + 4, d, d); g.setColor(pop(t.key)); g.fillOval(x, y, d, d); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(3f)); g.drawOval(x, y, d, d);
            String nm = t.name.replace(" (You)", ""), ini = nm.isEmpty() ? "?" : nm.replaceAll("^(Dr\\. )", "").substring(0, 1).toUpperCase();
            g.setFont(f(Font.BOLD, d / 2)); FontMetrics m = g.getFontMetrics(); g.setColor(ONACC); g.drawString(ini, x + (d - m.stringWidth(ini)) / 2, y + (d + m.getAscent() - m.getDescent()) / 2);
        }
        g.setClip(old);
        // name tag with a mic-off mark
        g.setFont(f(Font.BOLD, 13)); FontMetrics fm = g.getFontMetrics(); int tw = fm.stringWidth(t.name) + (t.mic ? 20 : 40), tx = r.x + 10, ty = r.y + r.height - 34;
        g.setColor(new Color(0, 0, 0, 170)); g.fillRoundRect(tx, ty, tw, 24, 10, 10); g.setColor(Color.WHITE); g.drawString(t.name, tx + 10, ty + 17);
        if (!t.mic) { int cx = tx + tw - 22, cy = ty + 4; g.setColor(RED); g.fillOval(cx, cy, 16, 16); g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2f)); g.drawLine(cx + 4, cy + 12, cx + 12, cy + 4); }
        boolean speaking = t.mic && System.currentTimeMillis() - t.spoke < 700;
        g.setColor(speaking ? OK : new Color(0x3A3A48)); g.setStroke(new BasicStroke(speaking ? 4f : 2f)); g.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);
    }
}
