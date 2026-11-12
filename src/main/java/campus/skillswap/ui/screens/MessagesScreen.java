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
    /** Id of the message currently being edited in the composer (0 = none), like Messenger's edit mode. */
    static int editing = 0;
    public static final long EDIT_WINDOW_MS = 15 * 60 * 1000L;   // Messenger only allows edits for 15 minutes; unsend has no limit
    public static Msg msgById(int id) { for (Msg m : S.msgs) if (m.id == id) return m; return null; }
    public static void editMsg(Msg m) { editing = m.id; draft = m.text; show("Messages"); }
    public static void cancelEdit() { editing = 0; draft = ""; show("Messages"); }
    public static void unsend(Msg m) {
        if (!m.from.equals(me.username) || m.deleted) return;
        if (!ask("Unsend message", lbl("<html><body style='width:280px'>Unsend this message for everyone in the chat? They will see that you unsent a message.</body></html>", Font.PLAIN, 13, TXT))) return;
        m.deleted = true; m.text = ""; m.edited = false; if (editing == m.id) { editing = 0; draft = ""; } save(); show("Messages");
    }
    public static void removeForMe(Msg m) {
        if (!ask("Remove for you", lbl("<html><body style='width:280px'>Remove this message from your chat? The other person will still see it.</body></html>", Font.PLAIN, 13, TXT))) return;
        m.hideFor(me.username); if (editing == m.id) { editing = 0; draft = ""; } save(); show("Messages");
    }
    /** Right-click / "..." menu for one message. */
    static JPopupMenu msgMenu(Msg m, boolean mine) {
        JPopupMenu pm = new JPopupMenu(); pm.setBorder(new LineBorder(INK, BW)); pm.setBackground(PANEL);
        java.util.function.BiConsumer<String, Runnable> add = (t, r) -> { JMenuItem it = new JMenuItem(t); it.setFont(f(Font.BOLD, 12)); it.setForeground(TXT); it.setBackground(PANEL); it.setBorder(new EmptyBorder(8, 14, 8, 22)); it.addActionListener(e -> r.run()); pm.add(it); };
        if (!m.deleted) add.accept("Copy", () -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(m.text), null));
        if (mine && !m.deleted) {
            if (System.currentTimeMillis() - m.time <= EDIT_WINDOW_MS) add.accept("Edit", () -> editMsg(m));
            else { JMenuItem d = new JMenuItem("Edit (15 min limit passed)"); d.setEnabled(false); d.setFont(f(Font.PLAIN, 12)); d.setBorder(new EmptyBorder(8, 14, 8, 22)); pm.add(d); }
            add.accept("Unsend", () -> unsend(m));
        }
        add.accept("Remove for you", () -> removeForMe(m));
        return pm;
    }
    public static final Color[] RING = POP;
    public static Color ring(String un) { return RING[Math.floorMod(un.hashCode(), RING.length)]; }

    /** Round profile picture: the user's photo when they have one, otherwise a coloured ring with a person silhouette. */
    public static JComponent person(Color ring, int sz) { return person(ring, sz, () -> null); }
    public static JComponent person(Color ring, int sz, java.util.function.Supplier<byte[]> photo) {
        return new JComponent() {
            { Dimension d = new Dimension(sz, sz); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); }
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g); int d = sz - 4; BufferedImage im = decodePhoto(photo.get());
                g.setColor(INK); g.fillOval(4, 4, d, d);
                if (im != null) {
                    g.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, d, d)); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int s2 = Math.min(im.getWidth(), im.getHeight()); g.drawImage(im, 0, 0, d, d, (im.getWidth() - s2) / 2, (im.getHeight() - s2) / 2, (im.getWidth() + s2) / 2, (im.getHeight() + s2) / 2, null);
                } else {
                    g.setColor(ring); g.fillOval(0, 0, d, d);
                    g.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, d, d)); g.setColor(ONACC);
                    int hr = Math.round(d * 0.17f); g.fillOval(d / 2 - hr, Math.round(d * 0.22f), hr * 2, hr * 2);
                    g.fillOval(Math.round(d * 0.2f), Math.round(d * 0.58f), Math.round(d * 0.6f), Math.round(d * 0.6f));
                }
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
    /** Placeholder shown where a message was unsent. */
    static JComponent unsentRow(boolean mine, String who) {
        JLabel l = lbl(mine ? "You unsent a message" : who + " unsent a message", Font.ITALIC, 12, MUTED);
        JPanel b = new JPanel(new BorderLayout()) { protected void paintComponent(Graphics g) { Graphics2D g2 = (Graphics2D) g.create(); g2.setColor(INK);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f)); g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3); g2.dispose(); } };
        b.setOpaque(false); b.setBorder(new EmptyBorder(8, 14, 8, 14)); b.add(l);
        JPanel r = new JPanel(new BorderLayout()); r.setOpaque(false); r.setBorder(new EmptyBorder(6, 0, 6, 0)); r.add(b, mine ? BorderLayout.EAST : BorderLayout.WEST); return r;
    }
    /** Chat row for a real message: right-click it (or use the "..." button) for Edit / Unsend / Remove for you. */
    public static JComponent msgRow(Msg m, boolean mine, String who) {
        JPopupMenu pm = msgMenu(m, mine);
        if (m.deleted) { JComponent r = unsentRow(mine, who); r.addMouseListener(popupOn(pm)); return r; }
        JPanel content = vbox(); put(content, wrapLabel(m.text, mine ? ONPINK : TXT, 14));
        if (m.edited) { JLabel ed = lbl("edited", Font.ITALIC, 10, mine ? ONPINK : MUTED); put(content, ed); }
        JPanel b = bubble(content, mine ? PINK : PANEL, 12, 18, 0);
        b.setToolTipText(Instant.ofEpochMilli(m.time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a")));
        JButton more = ghost("\u22EF", null); ((RBtn) more).flat(true); more.setBackground(BG); more.setFont(f(Font.BOLD, 16)); more.setBorder(new EmptyBorder(2, 8, 2, 8)); more.setToolTipText("Message options");
        more.addActionListener(e -> pm.show(more, 0, more.getHeight()));
        JPanel side = new JPanel(new GridBagLayout()); side.setOpaque(false); side.add(more);
        JPanel line = new JPanel(new BorderLayout(6, 0)); line.setOpaque(false); line.add(b); line.add(side, mine ? BorderLayout.WEST : BorderLayout.EAST);
        JPanel r = new JPanel(new BorderLayout()); r.setOpaque(false); r.setBorder(new EmptyBorder(6, 0, 6, 0)); r.add(line, mine ? BorderLayout.EAST : BorderLayout.WEST);
        MouseAdapter ma = popupOn(pm); b.addMouseListener(ma); for (Component c : content.getComponents()) c.addMouseListener(ma);
        return r;
    }
    static MouseAdapter popupOn(JPopupMenu pm) {
        return new MouseAdapter() {
            public void mousePressed(MouseEvent e) { pop(e); } public void mouseReleased(MouseEvent e) { pop(e); }
            void pop(MouseEvent e) { if (e.isPopupTrigger()) pm.show(e.getComponent(), e.getX(), e.getY()); }
        };
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
        JPanel av = new JPanel(new GridBagLayout()); av.setOpaque(false); av.add(person(ring(u.username), 62, () -> u.photo));
        JPanel west = new JPanel(new BorderLayout(10, 0)); west.setOpaque(false); west.add(bar, BorderLayout.WEST); west.add(av);
        JPanel mid = new JPanel(new GridBagLayout()); mid.setOpaque(false); GridBagConstraints g = new GridBagConstraints(); g.anchor = GridBagConstraints.WEST; g.weightx = 1;
        mid.add(lbl(name(u), Font.BOLD, 16, nameC), g);
        r.add(west, BorderLayout.WEST); r.add(mid);
        if (last > 0) { JPanel e = new JPanel(new BorderLayout()); e.setOpaque(false); e.add(lbl(tshort(last), Font.BOLD, 12, nameC), BorderLayout.NORTH); r.add(e, BorderLayout.EAST); }
        String un = u.username; r.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent ev) { chatWith = un; draft = ""; editing = 0; show("Messages"); } });
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
            JPanel hav = new JPanel(new GridBagLayout()); hav.setOpaque(false); hav.add(person(ring(o.username), 58, () -> o.photo));
            JPanel hi = new JPanel(new GridBagLayout()); hi.setOpaque(false); JPanel hv = vbox();
            put(hv, lbl(name(o), Font.BOLD, 20, TXT)); put(hv, lbl("\u25CF " + (campus.skillswap.service.Presence.isOnline(o) ? "Online" : "Offline"), Font.PLAIN, 16, campus.skillswap.service.Presence.isOnline(o) ? GREEN : MUTED)); GridBagConstraints g = new GridBagConstraints(); g.anchor = GridBagConstraints.WEST; g.weightx = 1; hi.add(hv, g);
            JPanel hb = new JPanel(new GridBagLayout()); hb.setOpaque(false); JPanel hbr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); hbr.setOpaque(false);
            hbr.add(ghost("\u2691", () -> report(o.username)));
            hbr.add(ghost("Voice call", () -> campus.skillswap.service.Calls.start(o.username, false)));
            { JButton vc = btn("Video call", () -> campus.skillswap.service.Calls.start(o.username, true)); vc.setBackground(GREEN); vc.setForeground(ONACC); vc.setFont(f(Font.BOLD, 16)); vc.setBorder(new EmptyBorder(10, 18, 10, 18)); hbr.add(vc); }
            if (!o.teach.isEmpty()) { JButton bk = btn("Book Session", () -> book(o.username, me.username, o.teach.get(0))); bk.setFont(f(Font.BOLD, 16)); bk.setBorder(new EmptyBorder(10, 18, 10, 18)); hbr.add(bk); }
            hb.add(hbr); h.add(hav, BorderLayout.WEST); h.add(hi); h.add(hb, BorderLayout.EAST); right.add(h, BorderLayout.NORTH);

            JPanel msgs = vbox(); msgs.setBorder(new EmptyBorder(16, 30, 16, 30));
            List<Msg> conv = S.msgs.stream().filter(m -> (m.from.equals(me.username) && m.to.equals(o.username)) || (m.from.equals(o.username) && m.to.equals(me.username))).filter(m -> !m.hiddenFor(me.username)).collect(Collectors.toList());
            LocalDate lastDay = null;
            for (Msg m : conv) {
                LocalDate d = Instant.ofEpochMilli(m.time).atZone(ZoneId.systemDefault()).toLocalDate();
                if (!d.equals(lastDay)) { put(msgs, dayDivider(dayLabel(d))); lastDay = d; }
                put(msgs, msgRow(m, m.from.equals(me.username), name(o)));
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
            final Msg em = editing != 0 ? msgById(editing) : null; if (editing != 0 && (em == null || em.deleted || !em.from.equals(me.username) || !em.to.equals(o.username))) editing = 0;
            Runnable send = () -> { String t = in.getText().trim(); if (t.isEmpty()) return; draft = "";
                if (editing != 0) { Msg tm = msgById(editing); editing = 0; if (tm != null && !tm.deleted && !t.equals(tm.text)) { tm.text = t; tm.edited = true; save(); } }
                else sendMsg(o.username, t);
                show("Messages"); }; in.addActionListener(e -> send.run());
            if (editing != 0) in.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelEdit"); in.getActionMap().put("cancelEdit", new AbstractAction() { public void actionPerformed(ActionEvent e) { cancelEdit(); } });
            SwingUtilities.invokeLater(() -> { in.requestFocusInWindow(); in.setCaretPosition(in.getText().length()); });
            JButton sendB = btn(editing != 0 ? "Save" : "Send", send); sendB.setFont(f(Font.BOLD, 14)); sendB.setBorder(new EmptyBorder(10, 22, 10, 22));
            JPanel bar = new JPanel(new BorderLayout(10, 0)); bar.setBackground(BG); bar.setBorder(new CompoundBorder(new MatteBorder(CB, 0, 0, 0, divider()), new EmptyBorder(12, 26, 12, 26))); bar.add(in); bar.add(sendB, BorderLayout.EAST);
            JPanel south = new JPanel(new BorderLayout()); south.setBackground(BG);
            if (editing != 0) {
                JPanel eb = new JPanel(new BorderLayout(10, 0)); eb.setBackground(YEL); eb.setBorder(new CompoundBorder(new MatteBorder(CB, 0, 0, 0, divider()), new EmptyBorder(8, 26, 8, 26)));
                String orig = em.text.length() > 60 ? em.text.substring(0, 60) + "..." : em.text;
                eb.add(lbl("\u270E Editing message: " + orig, Font.BOLD, 12, ONACC)); JButton cx = ghost("Cancel", MessagesScreen::cancelEdit); cx.setFont(f(Font.BOLD, 11)); cx.setBorder(new EmptyBorder(4, 12, 4, 12)); eb.add(cx, BorderLayout.EAST);
                south.add(eb, BorderLayout.NORTH);
            }
            south.add(bar); right.add(south, BorderLayout.SOUTH);
        }
        rt.add(right); return rt;
    }
}
