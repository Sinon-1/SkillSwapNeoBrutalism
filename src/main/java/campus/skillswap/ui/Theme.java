package campus.skillswap.ui;

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
import static campus.skillswap.config.AppConfig.*;
import static campus.skillswap.core.AppState.*;
import static campus.skillswap.data.Database.*;
import static campus.skillswap.data.Seed.*;
import static campus.skillswap.service.Skills.*;
import static campus.skillswap.service.Stats.*;
import static campus.skillswap.service.Security.*;
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


/**
 * Neobrutalist design system for SkillSwap Campus.
 * Thick ink borders, hard offset shadows (no blur, no rounded corners), flat saturated fills and chunky type.
 * Every screen styles itself through the helpers below, so changing a value here restyles the whole app.
 */
public class Theme {

    // ====================== DESIGN TOKENS ======================
    /** Bright accent fills. Text that sits ON these is always ONACC (near-black) so it stays readable in both themes. */
    public static final Color PINK = new Color(0xFF5C93), YEL = new Color(0xFFD43B), ONACC = new Color(0x111111);
    public static Color ONPINK = ONACC;
    /** Theme colours: BG page, PANEL cards, PANEL2 sunken areas, SIDE2 right rail, INK borders/shadows, FIELD inputs. */
    public static Color BG, PANEL, PANEL2, SIDE2, TXT, MUTED, INK, FIELD, GREEN, GOLD, BLUE, PINKT;
    public static boolean dark = false;
    public static final int BW = 2;          // border width: buttons, inputs, tags
    public static final int CB = 3, CS = 4;  // card border width and hard-shadow offset
    public static final int SH = 3;          // button hard-shadow offset
    /** Bright pastel fills used for avatars / tiles, picked by name. */
    public static final Color[] POP = {new Color(0xFF8FB3), new Color(0xFFD43B), new Color(0x69DB7C), new Color(0x74C0FC), new Color(0xB197FC), new Color(0xFFA94D), new Color(0x63E6BE)};
    public static Color pop(String s) { return POP[Math.floorMod(s.hashCode(), POP.length)]; }
    /** A light, bright version of any colour, used as the fill behind tags and swatches. */
    public static Color pastel(Color c) { float[] h = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null); return Color.getHSBColor(h[0], 0.42f, 1f); }

    public static void applyTheme(boolean d) {
        dark = d;
        if (d) { BG = new Color(0x15151B); PANEL = new Color(0x23232B); PANEL2 = new Color(0x30303B); SIDE2 = new Color(0x1B1B24); TXT = new Color(0xF6F1E7); MUTED = new Color(0xB9B4A8);
            INK = new Color(0xF6F1E7); FIELD = new Color(0x2D2D38); GREEN = new Color(0x5FE3A1); GOLD = new Color(0xFFD54A); BLUE = new Color(0x8FB2FF); PINKT = new Color(0xFF8AB4); }
        else { BG = new Color(0xFFF3D6); PANEL = Color.WHITE; PANEL2 = new Color(0xF0E5C8); SIDE2 = new Color(0xE9E2FF); TXT = new Color(0x111111); MUTED = new Color(0x4D4A44);
            INK = new Color(0x111111); FIELD = Color.WHITE; GREEN = new Color(0x0B7A47); GOLD = new Color(0x8A5A00); BLUE = new Color(0x1D4FD7); PINKT = new Color(0xC2185B); }
        UIManager.put("OptionPane.background", PANEL); UIManager.put("Panel.background", PANEL); UIManager.put("OptionPane.messageForeground", TXT);
        UIManager.put("Label.foreground", TXT); UIManager.put("ComboBox.selectionBackground", PINK); UIManager.put("ComboBox.selectionForeground", ONACC);
        UIManager.put("ComboBox.background", FIELD); UIManager.put("ComboBox.foreground", TXT);
        // dialog buttons (JOptionPane): flat, chunky, no Metal gradient
        UIManager.put("Button.gradient", null); UIManager.put("Button.background", PINK); UIManager.put("Button.foreground", ONACC); UIManager.put("Button.select", PANEL2);
        UIManager.put("Button.focus", INK); UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 12));
        UIManager.put("Button.border", new javax.swing.plaf.BorderUIResource(new CompoundBorder(new LineBorder(INK, BW), new EmptyBorder(6, 16, 6, 16))));
        UIManager.put("ToolTip.background", YEL); UIManager.put("ToolTip.foreground", ONACC); UIManager.put("ToolTip.border", new javax.swing.plaf.BorderUIResource(new LineBorder(INK, BW)));
        UIManager.put("ScrollBarUI", NbScrollBarUI.class.getName());
    }
    public static void switchTheme(boolean d) {
        applyTheme(d); cfg.setProperty("theme", d ? "dark" : "light"); saveCfg();
        root.removeAll(); root.add(loginPanel(), "login"); root.add(appPanel(), "app");
        if (me != null) { rootCards.show(root, "app"); show(page); } else rootCards.show(root, "login");
        root.revalidate(); root.repaint();
    }
    public static Color leftBg() { return PANEL; }
    public static Color divider() { return INK; }

    // ====================== UI HELPERS ======================
    public static Font f(int st, int sz) { return new Font("SansSerif", st, sz); }
    public static JLabel lbl(String t, int st, int sz, Color c) { JLabel l = new JLabel(t); l.setFont(f(st, sz)); l.setForeground(c); return l; }
    public static JPanel vbox() { JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setOpaque(false); return p; }
    /** Horizontal flow row. Capped at its own height so a BoxLayout parent can never stretch it (this was the cause of the big empty gaps). */
    public static JPanel row() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4)) { public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); } };
        p.setOpaque(false); return p;
    }
    public static void put(JPanel box, JComponent c) { c.setAlignmentX(0f); box.add(c); }
    public static void gap(JPanel box, int h) { box.add(Box.createVerticalStrut(h)); }
    public static void aa(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); }
    /** Square outline of thickness t drawn inside (x, y, w, h). */
    public static void frame(Graphics g, int x, int y, int w, int h, int t, Color c) {
        g.setColor(c); g.fillRect(x, y, w, t); g.fillRect(x, y + h - t, w, t); g.fillRect(x, y, t, h); g.fillRect(x + w - t, y, t, h);
    }

    // ---------------- buttons ----------------
    /**
     * Neobrutalist button: flat fill (getBackground), 2px ink border and a hard offset shadow.
     * Hover nudges it toward the shadow, press sinks it flat. {@code flat(true)} drops the border/shadow (used for inactive nav items).
     */
    public static class RBtn extends JButton {
        boolean flat; public Color disabledBg;
        public RBtn(String t, int arc) {
            super(t); setContentAreaFilled(false); setOpaque(false); setBorderPainted(false); setFocusPainted(false); setRolloverEnabled(true);
            setBorder(new EmptyBorder(6, 14, 6, 14));
        }
        public RBtn flat(boolean v) { flat = v; repaint(); return this; }
        public Insets getInsets() { Insets i = super.getInsets(); return new Insets(i.top + BW, i.left + BW, i.bottom + BW + SH, i.right + BW + SH); }
        public Insets getInsets(Insets in) { Insets i = getInsets(); in.set(i.top, i.left, i.bottom, i.right); return in; }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); int w = getWidth() - SH, h = getHeight() - SH;
            boolean down = getModel().isArmed() && getModel().isPressed(), hov = getModel().isRollover() && isEnabled();
            int o = flat ? 0 : down ? SH : hov ? 1 : 0;
            Color bg = isEnabled() ? getBackground() : (disabledBg != null ? disabledBg : PANEL2);
            if (flat) {
                if (hov || down) { g.setColor(PANEL2); g.fillRect(0, 0, w, h); frame(g, 0, 0, w, h, BW, INK); } else { g.setColor(bg); g.fillRect(0, 0, w, h); }
            } else {
                g.setColor(INK); g.fillRect(SH, SH, w, h);                    // hard shadow
                g.setColor(bg); g.fillRect(o, o, w, h); frame(g, o, o, w, h, BW, INK);
            }
            if (isFocusOwner() && !flat) { g.setColor(INK); g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 2f}, 0f)); g.drawRect(o + BW + 2, o + BW + 2, w - 2 * BW - 5, h - 2 * BW - 5); }
            g.translate(o, o); super.paintComponent(g); g.dispose();
        }
    }
    public static JButton btn(String t, Runnable r) {
        RBtn b = new RBtn(t, 0); b.setFont(f(Font.BOLD, 12)); b.setForeground(ONPINK); b.setBackground(PINK);
        b.setBorder(new EmptyBorder(6, 14, 6, 14)); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); if (r != null) b.addActionListener(e -> r.run()); return b;
    }
    public static JButton ghost(String t, Runnable r) { JButton b = btn(t, r); b.setBackground(FIELD); b.setForeground(TXT); return b; }
    /** Tab / filter chip: yellow when selected, white when not. */
    /** Let a button grow to the full width of its (vertical) parent. */
    public static JButton wide(JButton b) { b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height)); return b; }
    public static JButton chip(String t, boolean on, Runnable r) { JButton b = ghost(t, r); if (on) { b.setBackground(YEL); b.setForeground(ONACC); } return b; }

    // ---------------- tags, cards, avatars ----------------
    public static JLabel tag(String t, Color c) {
        JLabel l = lbl(t, Font.BOLD, 10, ONACC); l.setOpaque(true); l.setBackground(pastel(c));
        l.setBorder(new CompoundBorder(new LineBorder(INK, BW), new EmptyBorder(1, 6, 1, 6))); return l;
    }
    /** Panel with a thick border and a hard shadow. Painted by hand so the shadow never leaks into the card's own background. */
    public static class Card extends JPanel {
        public Card() { super(new BorderLayout(10, 4)); setOpaque(false); setBackground(PANEL); setBorder(new EmptyBorder(10, 12, 10, 12)); }
        public Insets getInsets() { Insets i = super.getInsets(); return new Insets(i.top + CB, i.left + CB, i.bottom + CB + CS, i.right + CB + CS); }
        public Insets getInsets(Insets in) { Insets i = getInsets(); in.set(i.top, i.left, i.bottom, i.right); return in; }
        public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        protected void paintComponent(Graphics g) {
            int w = getWidth() - CS, h = getHeight() - CS;
            g.setColor(INK); g.fillRect(CS, CS, w, h); g.setColor(getBackground()); g.fillRect(0, 0, w, h); frame(g, 0, 0, w, h, CB, INK);
        }
    }
    public static JPanel card() { return new Card(); }
    public static JComponent avatar(String n, int sz) {
        String[] w = n.trim().split("\\s+"); String s = w[w.length - 1].isEmpty() ? "?" : w[w.length - 1].substring(0, 1).toUpperCase();
        final Color fill = pop(n);
        return new JComponent() {
            { Dimension d = new Dimension(sz, sz); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); }
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0; aa(g); int d = sz - 3;
                g.setColor(INK); g.fillOval(3, 3, d, d); g.setColor(fill); g.fillOval(0, 0, d, d);
                g.setColor(INK); g.setStroke(new BasicStroke(2.5f)); g.drawOval(1, 1, d - 2, d - 2);
                g.setColor(ONACC); g.setFont(f(Font.BOLD, d / 2)); FontMetrics m = g.getFontMetrics();
                g.drawString(s, (d - m.stringWidth(s)) / 2, (d + m.getAscent() - m.getDescent()) / 2);
            }
        };
    }
    public static JPanel avatarWrap(String n, int sz) { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); p.setOpaque(false); p.add(avatar(n, sz)); return p; }
    public static JComponent swatch(Color c, int sz) {
        return new JComponent() {
            { Dimension d = new Dimension(sz, sz); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); }
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0; int d = sz - 3;
                g.setColor(INK); g.fillRect(3, 3, d, d); g.setColor(pastel(c)); g.fillRect(0, 0, d, d); frame(g, 0, 0, d, d, BW, INK);
                g.setColor(c); g.fillRect(d / 4, d / 4, d / 2, d / 2); frame(g, d / 4, d / 4, d / 2, d / 2, BW, INK);
            }
        };
    }
    public static JPanel swatchWrap(Color c, int sz) { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); p.setOpaque(false); p.add(swatch(c, sz)); return p; }

    // ---------------- inputs ----------------
    static class NField extends JTextField { public Dimension getMaximumSize() { Dimension d = super.getMaximumSize(); d.height = getPreferredSize().height; return d; } }
    static class NPass extends JPasswordField { public Dimension getMaximumSize() { Dimension d = super.getMaximumSize(); d.height = getPreferredSize().height; return d; } }
    public static Color focusFill() { return dark ? new Color(0x3B3A22) : new Color(0xFFF6BF); }
    /** Applies the input look: field fill, 2px ink border, and a yellow highlight while focused. */
    public static <T extends javax.swing.text.JTextComponent> T styleInput(T t, int vpad, int hpad) {
        t.setBackground(FIELD); t.setForeground(TXT); t.setCaretColor(TXT); t.setFont(f(Font.PLAIN, 13));
        t.setBorder(new CompoundBorder(new LineBorder(INK, BW), new EmptyBorder(vpad, hpad, vpad, hpad)));
        t.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { t.setBackground(focusFill()); } public void focusLost(FocusEvent e) { t.setBackground(FIELD); } });
        return t;
    }
    public static JTextField field() { return styleInput(new NField(), 7, 10); }
    public static JPasswordField pass() { return styleInput(new NPass(), 7, 10); }
    /** Password field with a chunky eye button on its right edge that shows / hides the typed text. */
    public static JPanel passRow(JPasswordField p) {
        final char dot = p.getEchoChar();
        JPanel row = new JPanel(new BorderLayout()) { public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); } };
        row.setBackground(FIELD); row.setBorder(new LineBorder(INK, BW));
        p.setBorder(new EmptyBorder(7, 10, 7, 10));
        JToggleButton eye = new JToggleButton() {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g); int w = getWidth(), h = getHeight();
                g.setColor(isSelected() ? YEL : getModel().isRollover() ? PANEL2 : FIELD); g.fillRect(0, 0, w, h);
                g.setColor(INK); g.fillRect(0, 0, BW, h);
                int cx = (w + BW) / 2, cy = h / 2;
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                java.awt.geom.Path2D.Float eyeShape = new java.awt.geom.Path2D.Float();
                eyeShape.moveTo(cx - 10, cy); eyeShape.quadTo(cx, cy - 11, cx + 10, cy); eyeShape.quadTo(cx, cy + 11, cx - 10, cy);
                Color ink = isSelected() ? ONACC : INK; g.setColor(ink); g.draw(eyeShape); g.fillOval(cx - 3, cy - 3, 6, 6);
                if (!isSelected()) g.drawLine(cx - 9, cy + 9, cx + 9, cy - 9);   // slashed = hidden
                g.dispose();
            }
        };
        eye.setPreferredSize(new Dimension(40, 10)); eye.setFocusable(false); eye.setContentAreaFilled(false); eye.setBorderPainted(false); eye.setRolloverEnabled(true);
        eye.setCursor(new Cursor(Cursor.HAND_CURSOR)); eye.setToolTipText("Show password");
        eye.addActionListener(e -> { boolean on = eye.isSelected(); p.setEchoChar(on ? (char) 0 : dot); eye.setToolTipText(on ? "Hide password" : "Show password"); });
        row.add(p, BorderLayout.CENTER); row.add(eye, BorderLayout.EAST); return row;
    }
    public static JTextArea area(int rows, int cols) { JTextArea t = styleInput(new JTextArea(rows, cols), 6, 8); t.setLineWrap(true); t.setWrapStyleWord(true); return t; }
    public static JScrollPane scroller(JComponent c) { JScrollPane s = new JScrollPane(c); s.setBorder(new LineBorder(INK, BW)); return s; }
    public static JTextField searchField(String hint) {
        JTextField t = new NField() { protected void paintComponent(Graphics g) { super.paintComponent(g);
            if (getText().isEmpty()) { g.setColor(MUTED); g.setFont(getFont()); g.drawString(hint, getInsets().left, (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2); } } };
        styleInput(t, 9, 12); t.setFont(f(Font.PLAIN, 13)); return t;
    }
    /** Square checkbox with a chunky tick. */
    static class BoxIcon implements Icon {
        final boolean on; BoxIcon(boolean on) { this.on = on; }
        public int getIconWidth() { return 20; } public int getIconHeight() { return 20; }
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g); g.setColor(on ? YEL : FIELD); g.fillRect(x, y, 20, 20); frame(g, x, y, 20, 20, BW, INK);
            if (on) { g.setColor(ONACC); g.setStroke(new BasicStroke(3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)); g.drawPolyline(new int[]{x + 5, x + 9, x + 15}, new int[]{y + 10, y + 14, y + 6}, 3); }
            g.dispose();
        }
    }
    public static JCheckBox check(String t, boolean v) {
        JCheckBox c = new JCheckBox(t, v); c.setOpaque(false); c.setForeground(TXT); c.setFont(f(Font.BOLD, 12));
        c.setIcon(new BoxIcon(false)); c.setSelectedIcon(new BoxIcon(true)); c.setFocusPainted(false); c.setIconTextGap(8); return c;
    }
    /** Round radio with a chunky dot, matching the square checkbox. */
    static class DotIcon implements Icon {
        final boolean on; DotIcon(boolean on) { this.on = on; }
        public int getIconWidth() { return 20; } public int getIconHeight() { return 20; }
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g); g.setColor(on ? YEL : FIELD); g.fillOval(x, y, 19, 19);
            g.setColor(INK); g.setStroke(new BasicStroke(2.5f)); g.drawOval(x + 1, y + 1, 17, 17); if (on) { g.setColor(ONACC); g.fillOval(x + 6, y + 6, 8, 8); } g.dispose();
        }
    }
    public static JRadioButton radio(String t, boolean v) {
        JRadioButton r = new JRadioButton(t, v); r.setOpaque(false); r.setForeground(TXT); r.setFont(f(Font.BOLD, 12));
        r.setIcon(new DotIcon(false)); r.setSelectedIcon(new DotIcon(true)); r.setFocusPainted(false); r.setIconTextGap(8); return r;
    }
    public static class NbComboUI extends javax.swing.plaf.basic.BasicComboBoxUI {
        protected JButton createArrowButton() {
            JButton b = new JButton() {
                public void paint(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0; aa(g); g.setColor(YEL); g.fillRect(0, 0, getWidth(), getHeight()); g.setColor(INK); g.fillRect(0, 0, BW, getHeight());
                    int cx = getWidth() / 2 + 1, cy = getHeight() / 2; g.setColor(ONACC); g.fillPolygon(new int[]{cx - 5, cx + 5, cx}, new int[]{cy - 3, cy - 3, cy + 4}, 3);
                }
                public Dimension getPreferredSize() { return new Dimension(26, 26); }
            };
            b.setFocusable(false); b.setBorder(null); return b;
        }
    }
    public static <T> JComboBox<T> combo(T[] items) {
        JComboBox<T> c = new JComboBox<>(items); c.setUI(new NbComboUI()); c.setBackground(FIELD); c.setForeground(TXT); c.setFont(f(Font.PLAIN, 13)); c.setBorder(new LineBorder(INK, BW)); return c;
    }

    // ---------------- scrollbars and progress ----------------
    public static class NbScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        public static javax.swing.plaf.ComponentUI createUI(JComponent c) { return new NbScrollBarUI(); }
        static JButton none() { JButton b = new JButton(); Dimension d = new Dimension(0, 0); b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d); return b; }
        protected JButton createDecreaseButton(int o) { return none(); }
        protected JButton createIncreaseButton(int o) { return none(); }
        public Dimension getPreferredSize(JComponent c) { return scrollbar.getOrientation() == Adjustable.VERTICAL ? new Dimension(15, 48) : new Dimension(48, 15); }
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(PANEL2); g.fillRect(r.x, r.y, r.width, r.height); g.setColor(INK);
            if (scrollbar.getOrientation() == Adjustable.VERTICAL) g.fillRect(r.x, r.y, BW, r.height); else g.fillRect(r.x, r.y, r.width, BW);
        }
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            boolean v = scrollbar.getOrientation() == Adjustable.VERTICAL; int x = r.x + (v ? 3 : 1), y = r.y + (v ? 1 : 3), w = r.width - (v ? 4 : 2), h = r.height - (v ? 2 : 4);
            g.setColor(isThumbRollover() || isDragging ? PINK : YEL); g.fillRect(x, y, w, h); frame(g, x, y, w, h, BW, INK);
        }
    }
    /** Chunky progress bar with a fixed height (a plain JProgressBar gets stretched by BoxLayout). */
    public static JProgressBar bar(int min, int max, int val, Color fill) {
        JProgressBar b = new JProgressBar(min, max) {
            public Dimension getPreferredSize() { return new Dimension(100, 16); }
            public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 16); }
        };
        b.setValue(val); b.setBorderPainted(false); b.setOpaque(false); b.setAlignmentX(0f);
        b.setUI(new javax.swing.plaf.basic.BasicProgressBarUI() {
            protected void paintDeterminate(Graphics g, JComponent c) {
                int w = c.getWidth(), h = c.getHeight(); g.setColor(PANEL2); g.fillRect(0, 0, w, h);
                int amt = (int) ((w - 2 * BW) * progressBar.getPercentComplete()); g.setColor(fill); g.fillRect(BW, BW, amt, h - 2 * BW); frame(g, 0, 0, w, h, BW, INK);
                if (amt > 0 && amt < w - 2 * BW) { g.setColor(INK); g.fillRect(BW + amt - 1, BW, BW, h - 2 * BW); }
            }
            protected void paintIndeterminate(Graphics g, JComponent c) { paintDeterminate(g, c); }
        });
        return b;
    }

    // ---------------- forms, dialogs, page scaffold ----------------
    public static JPanel form(String[] labels, JComponent[] comps) {
        JPanel p = new JPanel(new GridBagLayout()); p.setBackground(PANEL); GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5); c.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < labels.length; i++) {
            c.gridy = i; c.gridx = 0; c.weightx = 0; if (!labels[i].isEmpty()) p.add(lbl(labels[i], Font.BOLD, 12, TXT), c);
            c.gridx = 1; c.weightx = 1; p.add(comps[i], c);
        }
        return p;
    }
    public static boolean ask(String title, JComponent body) {
        busy++; try { return JOptionPane.showConfirmDialog(frame, body, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION; } finally { busy--; }
    }
    public static void info(String m) { busy++; try { JOptionPane.showMessageDialog(frame, m, "SkillSwap Campus", JOptionPane.INFORMATION_MESSAGE); } finally { busy--; } }
    public static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    public static String ago(long t) {
        long m = (System.currentTimeMillis() - t) / 60000; return m < 1 ? "just now" : m < 60 ? m + "m ago" : m < 1440 ? m / 60 + "h ago" : m / 1440 + "d ago";
    }
    public static String tshort(long t) { long m = (System.currentTimeMillis() - t) / 60000; return m < 1 ? "now" : m < 60 ? m + "m" : m < 1440 ? m / 60 + "h" : m / 1440 + "d"; }
    public static class Pg { public JPanel root = new JPanel(new BorderLayout()), body = vbox(); }
    /** Standard page: heavy title bar with a thick rule underneath, then a scrolling body. */
    public static Pg pg(String title, String sub, JComponent action) {
        Pg p = new Pg(); p.root.setBackground(BG);
        JPanel h = new JPanel(new BorderLayout()); h.setBackground(BG); h.setBorder(new CompoundBorder(new MatteBorder(0, 0, CB, 0, INK), new EmptyBorder(16, 20, 12, 20)));
        JPanel t = vbox(); put(t, lbl(title, Font.BOLD, 26, TXT)); put(t, lbl(sub, Font.PLAIN, 12, MUTED)); h.add(t, BorderLayout.CENTER);
        if (action != null) { JPanel a = new JPanel(new GridBagLayout()); a.setOpaque(false); a.add(action); h.add(a, BorderLayout.EAST); }
        JPanel wrap = new JPanel(new BorderLayout()); wrap.setBackground(BG); wrap.setBorder(new EmptyBorder(16, 20, 20, 20)); wrap.add(p.body, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); sp.setBorder(null); sp.getViewport().setBackground(BG); sp.getVerticalScrollBar().setUnitIncrement(18);
        p.root.add(h, BorderLayout.NORTH); p.root.add(sp, BorderLayout.CENTER); return p;
    }
    public static Color catColor(String c) {
        switch (c) { case "Tech": return BLUE; case "Music": return PINK; case "Arts": return new Color(0xFF9800); case "Academics": return GREEN;
            case "Languages": return new Color(0xAB47BC); case "Cooking": return GOLD; default: return new Color(0x607D8B); }
    }
}
