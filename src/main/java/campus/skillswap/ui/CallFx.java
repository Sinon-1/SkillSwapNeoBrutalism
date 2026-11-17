package campus.skillswap.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Emotes ("reactions") and the raised-hand icon used in video calls (1-to-1 and group).
 * Everything is drawn with Java2D as chunky sticker art, so it looks the same on every computer and does not
 * depend on an emoji font (Java draws colour emoji badly on many systems).
 */
public final class CallFx {
    private CallFx() { }

    public static final String[] KEYS = {"THUMBS", "HEART", "LAUGH", "WOW", "IDEA", "HUH", "STAR", "PARTY"};
    public static final String[] LABELS = {"Good", "Love it", "Haha", "Wow", "Got it!", "Confused", "Great job", "Yay!"};
    static final long LIFE_MS = 2600, MIN_GAP_MS = 350;

    public static int indexOf(String k) { for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(k)) return i; return -1; }
    /** Emote keys also arrive from other computers, so only the known ones are ever drawn. */
    public static boolean valid(String k) { return k != null && indexOf(k) >= 0; }
    public static String label(String k) { int i = indexOf(k); return i < 0 ? "" : LABELS[i]; }

    // ------------------------------------------------------------------ drawing
    static final Color LINE = new Color(0x111111), SKIN = new Color(0xFFD43B), RED = new Color(0xFF4D6D), BLUE = new Color(0x4DABF7),
        ORANGE = new Color(0xFF922B), GREEN = new Color(0x51CF66), PINK = new Color(0xFF8FB3), GREY = new Color(0xADB5BD), WHITE = Color.WHITE;

    /** Draws one emote inside the square (x, y, s, s). */
    public static void paintEmote(Graphics2D g0, String key, double x, double y, double s) {
        Graphics2D g = (Graphics2D) g0.create(); setup(g); g.translate(x, y); g.scale(s / 100.0, s / 100.0);
        switch (key == null ? "" : key) {
            case "THUMBS": thumbs(g); break; case "HEART": heart(g); break; case "LAUGH": laugh(g); break; case "WOW": wow(g); break;
            case "IDEA": idea(g); break; case "HUH": huh(g); break; case "STAR": star(g); break; case "PARTY": party(g); break; default: break;
        }
        g.dispose();
    }
    /** The raised hand, drawn in the square (x, y, s, s). */
    public static void paintHand(Graphics2D g0, double x, double y, double s) {
        Graphics2D g = (Graphics2D) g0.create(); setup(g); g.translate(x, y); g.scale(s / 100.0, s / 100.0);
        Shape palm = new RoundRectangle2D.Double(24, 46, 54, 46, 18, 18);
        Shape f1 = new RoundRectangle2D.Double(25, 17, 13, 44, 13, 13), f2 = new RoundRectangle2D.Double(39, 8, 13, 52, 13, 13),
              f3 = new RoundRectangle2D.Double(53, 13, 13, 48, 13, 13), f4 = new RoundRectangle2D.Double(66, 26, 12, 38, 12, 12);
        Shape thumb = AffineTransform.getRotateInstance(Math.toRadians(-38), 26, 70).createTransformedShape(new RoundRectangle2D.Double(10, 52, 14, 36, 14, 14));
        Area all = new Area(palm); for (Shape sh : new Shape[]{f1, f2, f3, f4, thumb}) all.add(new Area(sh));
        halo(g, all);
        for (Shape sh : new Shape[]{thumb, f1, f2, f3, f4, palm}) part(g, sh, SKIN);
        g.setColor(SKIN); g.fill(new RoundRectangle2D.Double(27, 52, 48, 36, 12, 12));          // hides the finger bases so the hand reads as one shape
        g.dispose();
    }
    static void setup(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    static final BasicStroke OUT = new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND), THIN = new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    /** White "sticker" edge around the whole shape, so the art stays visible on dark and bright backgrounds. */
    static void halo(Graphics2D g, Shape s) {
        g.setColor(WHITE); g.setStroke(new BasicStroke(15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(s); g.fill(s);
    }
    static void part(Graphics2D g, Shape s, Color fill) { g.setColor(fill); g.fill(s); g.setColor(LINE); g.setStroke(OUT); g.draw(s); }
    static void line(Graphics2D g, double x1, double y1, double x2, double y2) { g.setColor(LINE); g.setStroke(OUT); g.draw(new Line2D.Double(x1, y1, x2, y2)); }
    static Path2D poly(double... p) { Path2D.Double d = new Path2D.Double(); d.moveTo(p[0], p[1]); for (int i = 2; i < p.length; i += 2) d.lineTo(p[i], p[i + 1]); d.closePath(); return d; }

    static void face(Graphics2D g) { Shape c = new Ellipse2D.Double(8, 8, 84, 84); halo(g, c); part(g, c, SKIN); }

    static void thumbs(Graphics2D g) {
        Shape cuff = new RoundRectangle2D.Double(6, 44, 22, 48, 8, 8), palm = new RoundRectangle2D.Double(30, 44, 62, 48, 18, 18), thumb = new RoundRectangle2D.Double(36, 8, 24, 52, 12, 12);
        Area all = new Area(cuff); all.add(new Area(palm)); all.add(new Area(thumb)); halo(g, all);
        part(g, thumb, SKIN); part(g, palm, SKIN); part(g, cuff, BLUE);
        g.setColor(SKIN); g.fill(new RoundRectangle2D.Double(38, 49, 40, 20, 10, 10));
        for (int i = 0; i < 3; i++) { g.setColor(LINE); g.setStroke(THIN); double yy = 64 + i * 9; g.draw(new Line2D.Double(60, yy, 82, yy)); }
    }
    static void heart(Graphics2D g) {
        Path2D.Double h = new Path2D.Double(); h.moveTo(50, 90); h.curveTo(6, 58, 4, 26, 26, 16); h.curveTo(40, 10, 50, 20, 50, 30); h.curveTo(50, 20, 60, 10, 74, 16); h.curveTo(96, 26, 94, 58, 50, 90); h.closePath();
        halo(g, h); part(g, h, RED);
        g.setColor(WHITE); g.fill(AffineTransform.getRotateInstance(Math.toRadians(-30), 24, 36).createTransformedShape(new Ellipse2D.Double(18, 26, 9, 17)));
    }
    static void laugh(Graphics2D g) {
        face(g); g.setStroke(OUT); g.setColor(LINE);
        g.draw(new Arc2D.Double(22, 32, 20, 16, 0, 180, Arc2D.OPEN)); g.draw(new Arc2D.Double(58, 32, 20, 16, 0, 180, Arc2D.OPEN));
        Shape mouth = new Arc2D.Double(24, 44, 52, 42, 180, 180, Arc2D.CHORD);
        g.setColor(new Color(0x5C1A1A)); g.fill(mouth);
        Shape old = g.getClip(); g.clip(mouth); g.setColor(PINK); g.fill(new Ellipse2D.Double(36, 72, 28, 22)); g.setClip(old);
        g.setColor(WHITE); g.fill(new Rectangle2D.Double(30, 65, 40, 6)); g.setColor(LINE); g.setStroke(OUT); g.draw(mouth);
        for (double tx : new double[]{9, 80}) { Shape t = new Ellipse2D.Double(tx, 46, 11, 17); g.setColor(BLUE); g.fill(t); g.setColor(LINE); g.setStroke(THIN); g.draw(t); }
    }
    static void wow(Graphics2D g) {
        face(g);
        for (double ex : new double[]{27, 57}) { Shape e = new Ellipse2D.Double(ex, 30, 16, 24); g.setColor(WHITE); g.fill(e); g.setColor(LINE); g.setStroke(THIN); g.draw(e); g.fill(new Ellipse2D.Double(ex + 4, 38, 8, 12)); }
        g.setStroke(OUT); g.setColor(LINE); g.draw(new Arc2D.Double(24, 16, 22, 14, 20, 140, Arc2D.OPEN)); g.draw(new Arc2D.Double(54, 16, 22, 14, 20, 140, Arc2D.OPEN));
        Shape mouth = new Ellipse2D.Double(39, 62, 22, 26); g.setColor(new Color(0x5C1A1A)); g.fill(mouth); g.setColor(LINE); g.draw(mouth);
    }
    static void idea(Graphics2D g) {
        Shape bulb = new Ellipse2D.Double(18, 6, 64, 64), neck = new RoundRectangle2D.Double(34, 56, 32, 22, 8, 8), base = new RoundRectangle2D.Double(36, 74, 28, 14, 7, 7);
        Area all = new Area(bulb); all.add(new Area(neck)); all.add(new Area(base)); halo(g, all);
        part(g, neck, GREY); part(g, base, new Color(0x868E96)); part(g, bulb, new Color(0xFFE066));
        g.setColor(WHITE); g.fill(AffineTransform.getRotateInstance(Math.toRadians(-35), 34, 24).createTransformedShape(new Ellipse2D.Double(28, 16, 9, 18)));
        g.setColor(LINE); g.setStroke(THIN); Path2D.Double fil = new Path2D.Double(); fil.moveTo(40, 58); fil.lineTo(44, 40); fil.lineTo(50, 50); fil.lineTo(56, 40); fil.lineTo(60, 58); g.draw(fil);
    }
    static void huh(Graphics2D g) {
        Area bub = new Area(new RoundRectangle2D.Double(6, 8, 88, 66, 26, 26)); Shape tail = poly(26, 68, 22, 94, 52, 72); bub.add(new Area(tail)); halo(g, bub);
        part(g, tail, BLUE); part(g, new RoundRectangle2D.Double(6, 8, 88, 66, 26, 26), BLUE);
        g.setColor(BLUE); g.fill(poly(28, 66, 25, 86, 48, 70));
        Font f = new Font("SansSerif", Font.BOLD, 62); Shape q = new TextLayout("?", f, g.getFontRenderContext()).getOutline(null); Rectangle2D b = q.getBounds2D();
        Shape qs = AffineTransform.getTranslateInstance(50 - b.getCenterX(), 41 - b.getCenterY()).createTransformedShape(q);
        g.setColor(LINE); g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(qs); g.setColor(WHITE); g.fill(qs);
    }
    static void star(Graphics2D g) {
        Path2D.Double p = new Path2D.Double(); for (int i = 0; i < 10; i++) { double r = i % 2 == 0 ? 46 : 20, a = Math.toRadians(-90 + i * 36), px = 50 + r * Math.cos(a), py = 54 + r * Math.sin(a); if (i == 0) p.moveTo(px, py); else p.lineTo(px, py); }
        p.closePath(); halo(g, p); part(g, p, new Color(0xFFC419));
        g.setColor(WHITE); g.fill(new Ellipse2D.Double(41, 32, 8, 8));
    }
    static void party(Graphics2D g) {
        Shape cone = poly(8, 94, 30, 40, 64, 70); Area all = new Area(cone); halo(g, all);
        for (Object[] c : new Object[][]{{70, 20, 8, PINK}, {88, 44, 7, GREEN}, {52, 12, 7, BLUE}, {86, 16, 6, SKIN}, {90, 68, 6, RED}, {72, 46, 5, BLUE}}) {
            Shape d = new Ellipse2D.Double((int) c[0] - (int) c[2], (int) c[1] - (int) c[2], 2 * (int) c[2], 2 * (int) c[2]); g.setColor(WHITE); g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(d); g.fill(d);
        }
        part(g, cone, ORANGE);
        g.setColor(LINE); g.setStroke(THIN); g.draw(new Line2D.Double(14, 76, 40, 66)); g.draw(new Line2D.Double(22, 62, 46, 54));
        for (Object[] c : new Object[][]{{70, 20, 8, PINK}, {88, 44, 7, GREEN}, {52, 12, 7, BLUE}, {86, 16, 6, SKIN}, {90, 68, 6, RED}, {72, 46, 5, BLUE}}) {
            Shape d = new Ellipse2D.Double((int) c[0] - (int) c[2], (int) c[1] - (int) c[2], 2 * (int) c[2], 2 * (int) c[2]); part(g, d, (Color) c[3]);
        }
    }

    // ------------------------------------------------------------------ icons for buttons
    public static Icon icon(String key, int size) {
        return new Icon() {
            public int getIconWidth() { return size; } public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) { paintEmote((Graphics2D) g, key, x, y, size); }
        };
    }
    public static Icon handIcon(int size) {
        return new Icon() {
            public int getIconWidth() { return size; } public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) { paintHand((Graphics2D) g, x, y, size); }
        };
    }

    /** The pop-up grid of emotes shown above the EMOTES button. */
    public static JPopupMenu palette(Consumer<String> pick) {
        JPopupMenu pm = new JPopupMenu(); pm.setBorder(new LineBorder(LINE, 3)); pm.setBackground(new Color(0x2A2A34));
        JPanel grid = new JPanel(new GridLayout(2, 4, 6, 6)); grid.setBackground(new Color(0x2A2A34)); grid.setBorder(new EmptyBorder(8, 8, 8, 8));
        for (int i = 0; i < KEYS.length; i++) {
            final String key = KEYS[i];
            JButton b = new JButton(LABELS[i], icon(key, 46)) {
                protected void paintComponent(Graphics g) { if (getModel().isRollover()) { g.setColor(new Color(0xFFD43B)); g.fillRect(0, 0, getWidth(), getHeight()); setForeground(LINE); } else setForeground(Color.WHITE); super.paintComponent(g); }
            };
            b.setFont(new Font("SansSerif", Font.BOLD, 11)); b.setVerticalTextPosition(SwingConstants.BOTTOM); b.setHorizontalTextPosition(SwingConstants.CENTER); b.setIconTextGap(3);
            b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(false); b.setRolloverEnabled(true); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.setBorder(new EmptyBorder(6, 6, 5, 6)); b.setToolTipText(LABELS[i]);
            b.addActionListener(e -> { pm.setVisible(false); pick.accept(key); });
            grid.add(b);
        }
        pm.add(grid); return pm;
    }
    /** Opens the palette just above (or below, if there is no room) the given button. */
    public static void showPalette(JComponent invoker, Consumer<String> pick) {
        JPopupMenu pm = palette(pick); Dimension d = pm.getPreferredSize();
        pm.show(invoker, (invoker.getWidth() - d.width) / 2, -d.height - 6);
    }

    static final Font BADGE_FONT = new Font("SansSerif", Font.BOLD, 13);
    static String badgeText(int order, String text) { return (order > 0 ? order + "  " : "") + (text == null ? "" : text); }
    /** Width in pixels that {@link #paintHandBadge} will use. */
    public static int badgeWidth(Graphics2D g, int order, String text) { String t = badgeText(order, text); return 40 + (t.isEmpty() ? 0 : g.getFontMetrics(BADGE_FONT).stringWidth(t) + 6); }
    /** Paints a hand badge (yellow tag with the hand, and the place in the queue when {@code order} > 0). */
    public static void paintHandBadge(Graphics2D g0, int x, int y, int order, String text) {
        Graphics2D g = (Graphics2D) g0.create(); setup(g); g.setFont(BADGE_FONT); FontMetrics fm = g.getFontMetrics();
        String t = badgeText(order, text); int w = 40 + (t.isEmpty() ? 0 : fm.stringWidth(t) + 6), h = 34;
        g.setColor(LINE); g.fillRoundRect(x + 3, y + 3, w, h, 12, 12); g.setColor(new Color(0xFFD43B)); g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(LINE); g.setStroke(new BasicStroke(2.5f)); g.drawRoundRect(x, y, w, h, 12, 12);
        paintHand(g, x + 4, y + 2, 30);
        if (!t.isEmpty()) g.drawString(t, x + 38, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }

    // ------------------------------------------------------------------ floating reactions
    /** Emotes that float up and fade out over the call picture. All methods run on the Swing thread. */
    public static class Layer {
        static class Item { String key, owner; long t0; double dx; }
        final List<Item> items = new ArrayList<>(); final Map<String, Long> lastBy = new HashMap<>();

        /** Adds an emote; returns false when this person is sending too fast (spam guard). */
        public boolean add(String key, String owner) {
            long now = System.currentTimeMillis(); Long last = lastBy.get(owner); if (last != null && now - last < MIN_GAP_MS) return false;
            lastBy.put(owner, now); prune(now); if (items.size() >= 14) items.remove(0);
            Item it = new Item(); it.key = key; it.owner = owner; it.t0 = now; it.dx = (Math.random() - 0.5) * 0.5; items.add(it); return true;
        }
        void prune(long now) { items.removeIf(i -> now - i.t0 >= LIFE_MS); }
        public boolean active() { prune(System.currentTimeMillis()); return !items.isEmpty(); }
        /** @param anchor where an owner's emotes start (the owner's picture); {@code size} is the emote size in pixels. */
        public void paint(Graphics2D g, Function<String, Rectangle> anchor, int size) {
            long now = System.currentTimeMillis();
            for (Item it : items) {
                double p = (now - it.t0) / (double) LIFE_MS; if (p < 0 || p >= 1) continue;
                Rectangle r = anchor.apply(it.owner); if (r == null) continue;
                double rise = 1 - Math.pow(1 - p, 2), pop = p < 0.12 ? 0.55 + 0.45 * (p / 0.12) : 1.0, sz = size * pop;
                double cx = r.x + r.width / 2.0 + it.dx * r.width + Math.sin(p * 9 + it.dx * 20) * 7, cy = r.y + r.height - size * 0.9 - rise * Math.min(r.height * 0.75, 230);
                float alpha = p > 0.7 ? (float) (1 - (p - 0.7) / 0.3) : 1f;
                Graphics2D g2 = (Graphics2D) g.create(); g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
                paintEmote(g2, it.key, cx - sz / 2, cy - sz / 2, sz); g2.dispose();
            }
        }
    }
}
