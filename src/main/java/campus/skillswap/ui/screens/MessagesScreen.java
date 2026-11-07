package campus.skillswap.ui.screens;

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
import static campus.skillswap.ui.Theme.*;
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
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Chat. */
public class MessagesScreen {

    // ====================== MESSAGES ======================
    public static void sendMsg(String to, String text) {
        Msg m = new Msg(); m.id = nextId(); m.from = me.username; m.to = to; m.text = text; m.time = System.currentTimeMillis(); S.msgs.add(m); save();
    }
    public static final Color[] RING = POP;
    public static Color ring(String un) { return RING[Math.floorMod(un.hashCode(), RING.length)]; }

    /** Round profile picture with a coloured ring and a person silhouette. */
    public static JComponent person(Color ring, int sz) {
        return new JComponent() {
            { Dimension d = new Dimension(sz, sz); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); }
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g); int d = sz - 4;
                g.setColor(INK); g.fillOval(4, 4, d, d); g.setColor(ring); g.fillOval(0, 0, d, d);
                g.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, d, d)); g.setColor(ONACC);
                int hr = Math.round(d * 0.17f); g.fillOval(d / 2 - hr, Math.round(d * 0.22f), hr * 2, hr * 2);
                g.fillOval(Math.round(d * 0.2f), Math.round(d * 0.58f), Math.round(d * 0.6f), Math.round(d * 0.6f));
                g.setClip(null); g.setColor(INK); g.setStroke(new BasicStroke(3f)); g.drawOval(1, 1, d - 3, d - 3); g.dispose();
            }
        };
    }
    /** Rounded search / input pill. */
    public static JTextField pill(String hint, boolean icon) {
        JTextField t = new JTextField() {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g); int w = getWidth(), h = getHeight();
                g.setColor(hasFocus() ? focusFill() : FIELD); g.fillRect(0, 0, w, h); frame(g, 0, 0, w, h, BW, INK);
                if (icon) { g.setColor(TXT); g.setStroke(new BasicStroke(2.5f)); g.drawOval(13, h / 2 - 8, 11, 11); g.drawLine(22, h / 2 + 2, 27, h / 2 + 7); }
                if (getText().isEmpty()) { g.setColor(MUTED); g.setFont(getFont()); g.drawString(hint, icon ? 36 : 14, (h + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2); }
                g.dispose(); super.paintComponent(g0);
            }
            public Dimension getMaximumSize() { Dimension d = super.getMaximumSize(); d.height = getPreferredSize().height; return d; }
        };
        t.setOpaque(false); t.setForeground(TXT); t.setCaretColor(TXT); t.setFont(f(Font.PLAIN, 14)); t.setBorder(new EmptyBorder(10, icon ? 36 : 14, 10, 14));
        t.addFocusListener(new FocusAdapter() { public void focusGained(FocusEvent e) { t.repaint(); } public void focusLost(FocusEvent e) { t.repaint(); } }); return t;
    }
    public static JPanel bubble(JComponent content, Color bg, int vpad, int hpad, int arc) {
        JPanel b = new JPanel(new BorderLayout()) {
            protected void paintComponent(Graphics g) { int w = getWidth() - 4, h = getHeight() - 4; g.setColor(INK); g.fillRect(4, 4, w, h); g.setColor(getBackground()); g.fillRect(0, 0, w, h); frame(g, 0, 0, w, h, BW, INK); }
        };
        b.setOpaque(false); b.setBackground(bg); b.setBorder(new EmptyBorder(vpad, hpad, vpad + 4, hpad + 4)); b.add(content); return b;
    }
    public static JLabel wrapLabel(String text, Color c, int size) {
        String h = esc(text); JLabel l = lbl("<html>" + h + "</html>", Font.PLAIN, size, c);
        if (l.getPreferredSize().width > 340) l.setText("<html><body style='width:340px'>" + h + "</body></html>"); return l;
    }
    public static JComponent hline() {
        return new JComponent() { { setPreferredSize(new Dimension(10, 3)); }
            protected void paintComponent(Graphics g) { g.setColor(INK); g.fillRect(0, getHeight() / 2 - 1, getWidth(), 2); } };
    }
    public static JComponent dayDivider(String text) {
        JPanel p = new JPanel(new GridBagLayout()); p.setOpaque(false); p.setBorder(new EmptyBorder(4, 0, 10, 0)); GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.gridy = 0; g.gridx = 0; p.add(hline(), g);
        g.gridx = 1; g.weightx = 0; g.insets = new Insets(0, 14, 0, 14); p.add(tag(text, GOLD), g);
        g.gridx = 2; g.weightx = 1; g.insets = new Insets(0, 0, 0, 0); p.add(hline(), g); return p;
    }
    public static String dayLabel(LocalDate d) {
        LocalDate t = LocalDate.now(); return d.equals(t) ? "TODAY" : d.equals(t.minusDays(1)) ? "YESTERDAY" : d.format(DateTimeFormatter.ofPattern("MMM d, yyyy")).toUpperCase();
    }
    public static String whenText(LocalDateTime at) {
        LocalDate t = LocalDate.now(); String tm = at.format(DateTimeFormatter.ofPattern("h:mm a"));
        return at.toLocalDate().equals(t) ? "Today, " + tm : at.toLocalDate().equals(t.plusDays(1)) ? "Tomorrow, " + tm : at.format(DF);
    }
    public static JComponent bubbleRow(String text, boolean mine, long time) {
        JPanel b = bubble(wrapLabel(text, mine ? ONPINK : TXT, 14), mine ? PINK : PANEL, 12, 18, 0);
        b.setToolTipText(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a")));
        JPanel r = new JPanel(new BorderLayout()); r.setOpaque(false); r.setBorder(new EmptyBorder(6, 0, 6, 0)); r.add(b, mine ? BorderLayout.EAST : BorderLayout.WEST); return r;
    }
    public static JComponent sessionBubble(Sess cs) {
        JPanel c = new JPanel(new BorderLayout()) {
            protected void paintComponent(Graphics g) { int w = getWidth() - 5, h = getHeight() - 5; g.setColor(INK); g.fillRect(5, 5, w, h); g.setColor(PANEL); g.fillRect(0, 0, w, h); frame(g, 0, 0, w, h, 3, INK); }
        };
        c.setOpaque(false); c.setBorder(new EmptyBorder(12, 14, 17, 19)); JPanel in = vbox();
        put(in, tag("SESSION CONFIRMED", GREEN)); gap(in, 8); put(in, lbl(cs.skill + " " + whenText(cs.at), Font.BOLD, 14, TXT)); put(in, lbl(cs.mode, Font.PLAIN, 14, MUTED));
        JButton join = btn("Join", () -> info("Session room: SS-" + (1000 + cs.id) + "\n" + (cs.mode.equals("Online") ? "Open the campus meeting room with this code." : "Meet in person at the agreed campus spot.")));
        join.setFont(f(Font.BOLD, 11)); join.setBorder(new EmptyBorder(5, 14, 5, 14));
        JPanel e = new JPanel(new BorderLayout()); e.setOpaque(false); e.add(join, BorderLayout.SOUTH); c.add(in); c.add(e, BorderLayout.EAST); c.setPreferredSize(new Dimension(380, 124));
        JPanel r = new JPanel(new BorderLayout()); r.setOpaque(false); r.setBorder(new EmptyBorder(8, 0, 8, 0)); r.add(c, BorderLayout.WEST); return r;
    }
    public static JComponent convoRow(User u, long last, boolean sel) {
        Color rowBg = sel ? YEL : leftBg(); Color nameC = sel ? ONACC : TXT;
        JPanel r = new JPanel(new BorderLayout(10, 0)); r.setBackground(rowBg); r.setBorder(new CompoundBorder(new MatteBorder(0, 0, BW, 0, INK), new EmptyBorder(10, 0, 10, 14))); r.setCursor(new Cursor(Cursor.HAND_CURSOR));
        JPanel bar = new JPanel(); bar.setPreferredSize(new Dimension(6, 0)); bar.setBackground(sel ? INK : rowBg);
        JPanel av = new JPanel(new GridBagLayout()); av.setOpaque(false); av.add(person(ring(u.username), 62));
        JPanel west = new JPanel(new BorderLayout(10, 0)); west.setOpaque(false); west.add(bar, BorderLayout.WEST); west.add(av);
        JPanel mid = new JPanel(new GridBagLayout()); mid.setOpaque(false); GridBagConstraints g = new GridBagConstraints(); g.anchor = GridBagConstraints.WEST; g.weightx = 1;
        mid.add(lbl(name(u), Font.BOLD, 16, nameC), g);
        r.add(west, BorderLayout.WEST); r.add(mid);
        if (last > 0) { JPanel e = new JPanel(new BorderLayout()); e.setOpaque(false); e.add(lbl(tshort(last), Font.BOLD, 12, nameC), BorderLayout.NORTH); r.add(e, BorderLayout.EAST); }
        String un = u.username; r.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent ev) { chatWith = un; draft = ""; show("Messages"); } });
        return r;
    }
    public static JComponent messagesPage() {
        JPanel rt = new JPanel(new BorderLayout()); rt.setBackground(BG);
        Map<String, Long> lastT = new HashMap<>();
        for (Msg m : S.msgs) { if (m.from.equals(me.username)) lastT.merge(m.to, m.time, Math::max); else if (m.to.equals(me.username)) lastT.merge(m.from, m.time, Math::max); }
        List<User> others = S.users.values().stream().filter(u -> u != me).sorted((x, y) -> { int k = Long.compare(lastT.getOrDefault(y.username, 0L), lastT.getOrDefault(x.username, 0L)); return k != 0 ? k : name(x).compareToIgnoreCase(name(y)); }).collect(Collectors.toList());
        if ((chatWith == null || !S.users.containsKey(chatWith)) && !others.isEmpty()) chatWith = others.get(0).username;

        // ---- left: title, search pill, conversation list ----
        JPanel left = new JPanel(new BorderLayout()); left.setBackground(leftBg()); left.setPreferredSize(new Dimension(300, 0)); left.setBorder(new MatteBorder(0, 0, 0, CB, divider()));
        JPanel top = vbox(); top.setBorder(new EmptyBorder(18, 16, 14, 16));
        put(top, lbl("Messages", Font.BOLD, 30, TXT)); gap(top, 8);
        JTextField q = pill("Search chats", true); q.setText(chatQ); q.addActionListener(e -> { chatQ = q.getText().trim(); show("Messages"); }); put(top, q);
        left.add(top, BorderLayout.NORTH);
        JPanel list = vbox();
        for (User u : others) { if (!chatQ.isEmpty() && !name(u).toLowerCase().contains(chatQ.toLowerCase())) continue; put(list, convoRow(u, lastT.getOrDefault(u.username, 0L), u.username.equals(chatWith))); }
        JPanel lw = new JPanel(new BorderLayout()); lw.setOpaque(false); lw.add(list, BorderLayout.NORTH);
        JScrollPane ls = new JScrollPane(lw, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ls.setBorder(null); ls.getViewport().setBackground(leftBg()); ls.getVerticalScrollBar().setUnitIncrement(18); left.add(ls);
        rt.add(left, BorderLayout.WEST);

        // ---- right: chat ----
        JPanel right = new JPanel(new BorderLayout()); right.setBackground(BG);
        if (chatWith == null || !S.users.containsKey(chatWith)) {
            JPanel e = new JPanel(new GridBagLayout()); e.setOpaque(false); e.add(lbl("Select a conversation to start chatting", Font.PLAIN, 14, MUTED)); right.add(e);
        } else {
            final User o = S.users.get(chatWith);
            JPanel h = new JPanel(new BorderLayout(14, 0)); h.setBackground(BG); h.setBorder(new CompoundBorder(new MatteBorder(0, 0, CB, 0, divider()), new EmptyBorder(14, 26, 14, 26)));
            JPanel hav = new JPanel(new GridBagLayout()); hav.setOpaque(false); hav.add(person(ring(o.username), 58));
            JPanel hi = new JPanel(new GridBagLayout()); hi.setOpaque(false); JPanel hv = vbox();
            put(hv, lbl(name(o), Font.BOLD, 20, TXT)); put(hv, lbl("\u25CF " + (o.nearby ? "Online" : "Offline"), Font.PLAIN, 16, o.nearby ? GREEN : MUTED)); GridBagConstraints g = new GridBagConstraints(); g.anchor = GridBagConstraints.WEST; g.weightx = 1; hi.add(hv, g);
            JPanel hb = new JPanel(new GridBagLayout()); hb.setOpaque(false); JPanel hbr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); hbr.setOpaque(false);
            hbr.add(ghost("\u2691", () -> report(o.username)));
            if (!o.teach.isEmpty()) { JButton bk = btn("Book Session", () -> book(o.username, me.username, o.teach.get(0))); bk.setFont(f(Font.BOLD, 16)); bk.setBorder(new EmptyBorder(10, 18, 10, 18)); hbr.add(bk); }
            hb.add(hbr); h.add(hav, BorderLayout.WEST); h.add(hi); h.add(hb, BorderLayout.EAST); right.add(h, BorderLayout.NORTH);

            JPanel msgs = vbox(); msgs.setBorder(new EmptyBorder(16, 30, 16, 30));
            List<Msg> conv = S.msgs.stream().filter(m -> (m.from.equals(me.username) && m.to.equals(o.username)) || (m.from.equals(o.username) && m.to.equals(me.username))).collect(Collectors.toList());
            LocalDate lastDay = null;
            for (Msg m : conv) {
                LocalDate d = Instant.ofEpochMilli(m.time).atZone(ZoneId.systemDefault()).toLocalDate();
                if (!d.equals(lastDay)) { put(msgs, dayDivider(dayLabel(d))); lastDay = d; }
                put(msgs, bubbleRow(m.text, m.from.equals(me.username), m.time));
            }
            if (conv.isEmpty()) { put(msgs, dayDivider("TODAY")); put(msgs, lbl("No messages yet - say hi to " + name(o) + "!", Font.PLAIN, 13, MUTED)); }
            Sess cs = S.sessions.stream().filter(s -> s.status.equals("CONFIRMED") && involves(s, me) && involves(s, o)).reduce((x, y) -> y).orElse(null);
            if (cs != null) put(msgs, sessionBubble(cs));
            JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false); wrap.add(msgs, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(wrap); sp.setBorder(null); sp.getViewport().setBackground(BG); sp.getVerticalScrollBar().setUnitIncrement(18); right.add(sp);
            Timer st = new Timer(40, e -> sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum())); st.setRepeats(false); st.start();

            JTextField in = pill("Type a message...", false); in.setText(draft);
            in.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { draft = in.getText(); } public void removeUpdate(javax.swing.event.DocumentEvent e) { draft = in.getText(); } public void changedUpdate(javax.swing.event.DocumentEvent e) { draft = in.getText(); } });
            Runnable send = () -> { String t = in.getText().trim(); if (!t.isEmpty()) { draft = ""; sendMsg(o.username, t); show("Messages"); } }; in.addActionListener(e -> send.run());
            SwingUtilities.invokeLater(() -> { in.requestFocusInWindow(); in.setCaretPosition(in.getText().length()); });
            JButton sendB = btn("Send", send); sendB.setFont(f(Font.BOLD, 14)); sendB.setBorder(new EmptyBorder(10, 22, 10, 22));
            JPanel bar = new JPanel(new BorderLayout(10, 0)); bar.setBackground(BG); bar.setBorder(new CompoundBorder(new MatteBorder(CB, 0, 0, 0, divider()), new EmptyBorder(12, 26, 12, 26))); bar.add(in); bar.add(sendB, BorderLayout.EAST); right.add(bar, BorderLayout.SOUTH);
        }
        rt.add(right); return rt;
    }
}
