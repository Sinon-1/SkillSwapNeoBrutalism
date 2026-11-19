package campus.skillswap.ui.screens;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.Path2D;
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
import campus.skillswap.ui.Theme.RBtn;

import static campus.skillswap.config.AppConfig.*;
import static campus.skillswap.core.AppState.*;
import static campus.skillswap.data.Database.*;
import static campus.skillswap.data.Seed.*;
import static campus.skillswap.service.Skills.*;
import static campus.skillswap.service.Stats.*;
import static campus.skillswap.service.Security.*;
import static campus.skillswap.ui.Theme.*;
import static campus.skillswap.ui.Theme.frame;
import static campus.skillswap.ui.AppShell.*;
import static campus.skillswap.ui.AppShell.frame;
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

    // ====================== GROUP CHATS ======================
    /** Marks a chat line that is a notice ("Ana created the group", "Group video call ended") rather than typed text. */
    public static final String SYS = "\u0001";
    public static Group groupOf(String key) { if (!Group.isKey(key)) return null; for (Group g : S.groups) if (g.key().equals(key)) return g; return null; }
    static boolean validChat(String key) { if (key == null) return false; if (Group.isKey(key)) { Group g = groupOf(key); return g != null && g.has(me.username); } User u = S.users.get(key); return u != null && !u.banned; }

    /** Notification for a message that just arrived (direct or in one of my groups). Also moves lastMsgSeen forward. */
    public static void notifyNew(Msg m, long cutoff) {
        if (me == null || m.time <= cutoff || m.from.equals(me.username)) return;
        boolean direct = m.to.equals(me.username); Group g = direct ? null : groupOf(m.to);
        if (!direct && (g == null || !g.has(me.username))) return;
        lastMsgSeen = Math.max(lastMsgSeen, m.time);
        if (g != null && m.text.startsWith(SYS)) return;                                   // group notices do not pop up
        if (page.equals("Messages") && (direct ? m.from.equals(chatWith) : m.to.equals(chatWith))) return;
        notifyUser(g == null ? "New message" : "New message in " + g.name, name(m.from) + ": " + m.text.replace(SYS, ""));
    }
    public static JComponent groupAvatar(Group g, int sz) {
        return new JComponent() {
            { Dimension d = new Dimension(sz, sz); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); }
            protected void paintComponent(Graphics g0) {
                Graphics2D g2 = (Graphics2D) g0.create(); aa(g2); int d = sz - 4;
                g2.setColor(INK); g2.fillOval(4, 4, d, d); g2.setColor(pop(g.key())); g2.fillOval(0, 0, d, d);
                g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, d, d)); g2.setColor(ONACC);
                int hr = Math.round(d * 0.11f);                                              // three people: one in front, two behind
                g2.fillOval(Math.round(d * 0.18f), Math.round(d * 0.30f), hr * 2, hr * 2); g2.fillOval(Math.round(d * 0.82f) - hr * 2, Math.round(d * 0.30f), hr * 2, hr * 2);
                g2.fillOval(Math.round(d * 0.06f), Math.round(d * 0.62f), Math.round(d * 0.4f), Math.round(d * 0.5f)); g2.fillOval(Math.round(d * 0.54f), Math.round(d * 0.62f), Math.round(d * 0.4f), Math.round(d * 0.5f));
                g2.setColor(pop(g.key())); g2.fillOval(d / 2 - hr - 3, Math.round(d * 0.17f) - 3, hr * 2 + 6, hr * 2 + 6); g2.fillOval(Math.round(d * 0.25f) - 3, Math.round(d * 0.55f) - 3, Math.round(d * 0.5f) + 6, Math.round(d * 0.6f) + 6);
                g2.setColor(ONACC); g2.fillOval(d / 2 - hr, Math.round(d * 0.17f), hr * 2, hr * 2); g2.fillOval(Math.round(d * 0.25f), Math.round(d * 0.55f), Math.round(d * 0.5f), Math.round(d * 0.6f));
                g2.setClip(null); g2.setColor(INK); g2.setStroke(new BasicStroke(3f)); g2.drawOval(1, 1, d - 3, d - 3); g2.dispose();
            }
        };
    }
    static String membersLine(Group g) {
        List<String> n = new ArrayList<>(); for (String u : g.members) n.add(u.equals(me.username) ? "You" : name(u));
        long on = g.members.stream().filter(u -> !u.equals(me.username) && campus.skillswap.service.Presence.isOnline(S.users.get(u))).count();
        String list = String.join(", ", n); if (list.length() > 60) list = list.substring(0, 57) + "...";
        return g.members.size() + " members" + (on > 0 ? "  \u2022  " + on + " online" : "") + "  \u2022  " + list;
    }
    static void closeDialog(Component c) { Window w = SwingUtilities.getWindowAncestor(c); if (w != null) w.dispose(); }
    /** Scrollable list of check boxes, one per user. */
    static JScrollPane pickList(List<User> users, Set<String> picked, Map<String, JCheckBox> boxes) {
        JPanel l = vbox(); l.setOpaque(true); l.setBackground(FIELD); l.setBorder(new EmptyBorder(6, 8, 6, 8));
        for (User u : users) { JCheckBox cb = check(name(u) + (campus.skillswap.service.Presence.isOnline(u) ? "   (online)" : ""), picked.contains(u.username)); boxes.put(u.username, cb); put(l, cb); gap(l, 4); }
        JScrollPane sp = scroller(l); sp.setPreferredSize(new Dimension(330, 210)); sp.getVerticalScrollBar().setUnitIncrement(16); return sp;
    }
    public static void newGroup() {
        String err = "", nm = ""; Set<String> picked = new LinkedHashSet<>();
        List<User> others = S.users.values().stream().filter(u -> u != me).sorted((x, y) -> name(x).compareToIgnoreCase(name(y))).collect(Collectors.toList());
        if (others.size() < 2) { info("You need at least 2 other students on SkillSwap to start a group chat."); return; }
        while (true) {
            JTextField nameF = field(); nameF.setText(nm); Map<String, JCheckBox> boxes = new LinkedHashMap<>(); JScrollPane sp = pickList(others, picked, boxes);
            JPanel body = new JPanel(new BorderLayout(0, 8)); body.setBackground(PANEL);
            if (!err.isEmpty()) body.add(lbl("<html><body style='width:320px'>" + esc(err) + "</body></html>", Font.BOLD, 11, PINKT), BorderLayout.NORTH);
            JPanel mid = new JPanel(new BorderLayout(0, 6)); mid.setBackground(PANEL); mid.add(lbl("Group name", Font.BOLD, 12, TXT), BorderLayout.NORTH); mid.add(nameF);
            JPanel low = new JPanel(new BorderLayout(0, 6)); low.setBackground(PANEL); low.add(lbl("Add people (at least 2)", Font.BOLD, 12, TXT), BorderLayout.NORTH); low.add(sp);
            JPanel col = new JPanel(new BorderLayout(0, 10)); col.setBackground(PANEL); col.add(mid, BorderLayout.NORTH); col.add(low); body.add(col);
            if (!ask("New group chat", body)) return;
            nm = nameF.getText().trim(); picked = new LinkedHashSet<>(); for (Map.Entry<String, JCheckBox> e : boxes.entrySet()) if (e.getValue().isSelected()) picked.add(e.getKey());
            if (nm.isEmpty()) { err = "Give the group a name."; continue; }
            if (nm.length() > 40) nm = nm.substring(0, 40);
            if (picked.size() < 2) { err = "Pick at least 2 people (use a normal chat for just one person)."; continue; }
            Group g = new Group(); g.id = nextId(); g.name = nm; g.creator = me.username; g.created = System.currentTimeMillis(); g.members.add(me.username); g.members.addAll(picked);
            S.groups.add(g); sendMsg(g.key(), SYS + name(me) + " created the group");
            chatWith = g.key(); draft = ""; editing = 0; show("Messages"); return;
        }
    }
    static void groupMembers(Group g) {
        final Runnable[] after = new Runnable[1];
        JPanel body = new JPanel(new BorderLayout(0, 10)); body.setBackground(PANEL); body.setBorder(new EmptyBorder(2, 2, 2, 2));
        JPanel list = vbox();
        for (String un : g.members) {
            User u = S.users.get(un); if (u == null) continue;
            JPanel r = new JPanel(new BorderLayout(10, 0)); r.setOpaque(false); r.setBorder(new EmptyBorder(4, 0, 4, 0)); r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            JPanel av = new JPanel(new GridBagLayout()); av.setOpaque(false); av.add(person(ring(un), 36, () -> u.photo)); r.add(av, BorderLayout.WEST);
            JPanel nm = row(); nm.add(lbl(name(u) + (un.equals(me.username) ? " (you)" : ""), Font.BOLD, 13, TXT)); if (un.equals(g.creator)) nm.add(tag("CREATOR", GOLD));
            if (campus.skillswap.service.Presence.isOnline(u)) nm.add(tag("ONLINE", GREEN)); r.add(nm);
            if (g.creator.equals(me.username) && !un.equals(me.username)) { JButton rm = ghost("Remove", () -> { after[0] = () -> removeMember(g, un); }); rm.setFont(f(Font.BOLD, 11)); rm.addActionListener(e -> closeDialog(rm)); r.add(rm, BorderLayout.EAST); }
            put(list, r);
        }
        JScrollPane sp = scroller(list); sp.setPreferredSize(new Dimension(380, Math.min(300, 60 + g.members.size() * 46))); sp.getViewport().setBackground(PANEL);
        body.add(lbl(g.name, Font.BOLD, 18, TXT), BorderLayout.NORTH); body.add(sp);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); btns.setOpaque(false);
        JButton add = btn("Add people", () -> after[0] = () -> addPeople(g)); add.addActionListener(e -> closeDialog(add)); btns.add(add);
        if (g.creator.equals(me.username)) { JButton rn = ghost("Rename", () -> after[0] = () -> renameGroup(g)); rn.addActionListener(e -> closeDialog(rn)); btns.add(rn); }
        JButton lv = ghost("Leave group", () -> after[0] = () -> leaveGroup(g)); lv.addActionListener(e -> closeDialog(lv)); btns.add(lv);
        body.add(btns, BorderLayout.SOUTH);
        busy++; try { JOptionPane.showMessageDialog(frame, body, "Group members", JOptionPane.PLAIN_MESSAGE); } finally { busy--; }
        if (after[0] != null) after[0].run();
    }
    static void addPeople(Group g) {
        List<User> cand = S.users.values().stream().filter(u -> !g.has(u.username)).sorted((x, y) -> name(x).compareToIgnoreCase(name(y))).collect(Collectors.toList());
        if (cand.isEmpty()) { info("Everyone on SkillSwap is already in this group."); return; }
        Map<String, JCheckBox> boxes = new LinkedHashMap<>(); JScrollPane sp = pickList(cand, new HashSet<>(), boxes);
        if (!ask("Add people to " + g.name, sp)) return;
        List<String> added = new ArrayList<>(); for (Map.Entry<String, JCheckBox> e : boxes.entrySet()) if (e.getValue().isSelected()) { g.members.add(e.getKey()); added.add(name(e.getKey())); }
        if (added.isEmpty()) return;
        sendMsg(g.key(), SYS + name(me) + " added " + String.join(", ", added)); show("Messages");
    }
    static void renameGroup(Group g) {
        JTextField t = field(); t.setText(g.name); JPanel p = form(new String[]{"New name"}, new JComponent[]{t}); p.setPreferredSize(new Dimension(320, 50));
        if (!ask("Rename group", p)) return; String n = t.getText().trim(); if (n.isEmpty() || n.equals(g.name)) return; if (n.length() > 40) n = n.substring(0, 40);
        String old = g.name; g.name = n; sendMsg(g.key(), SYS + name(me) + " renamed the group from " + old + " to " + n); show("Messages");
    }
    static void removeMember(Group g, String un) {
        if (!ask("Remove member", lbl("<html><body style='width:280px'>Remove " + esc(name(un)) + " from " + esc(g.name) + "?</body></html>", Font.PLAIN, 13, TXT))) return;
        sendMsg(g.key(), SYS + name(me) + " removed " + name(un)); g.members.remove(un); save(); show("Messages");
    }
    static void leaveGroup(Group g) {
        if (!ask("Leave group", lbl("<html><body style='width:280px'>Leave " + esc(g.name) + "? You will not see its messages any more unless someone adds you again.</body></html>", Font.PLAIN, 13, TXT))) return;
        if (campus.skillswap.service.GroupCalls.activeGroupId() == g.id) campus.skillswap.service.GroupCalls.leave();
        sendMsg(g.key(), SYS + name(me) + " left the group"); g.members.remove(me.username);
        if (g.creator.equals(me.username) && !g.members.isEmpty()) g.creator = g.members.get(0);
        save(); chatWith = null; draft = ""; editing = 0; show("Messages");
    }

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
    public static JComponent msgRow(Msg m, boolean mine, String who) { return msgRow(m, mine, who, false); }
    /** Centered grey tag for a notice line ("Ana created the group"). */
    static JComponent sysRow(String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) { public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); } };
        p.setOpaque(false); p.setBorder(new EmptyBorder(8, 0, 8, 0)); p.add(tag(text.substring(1), GREEN)); return p;
    }
    /** @param showName true in group chats: puts the sender's name above other people's bubbles. */
    public static JComponent msgRow(Msg m, boolean mine, String who, boolean showName) {
        if (m.text != null && m.text.startsWith(SYS) && !m.deleted) return sysRow(m.text);
        JComponent row = msgRow0(m, mine, who);
        if (!showName || mine || m.deleted) return row;
        JLabel nl = lbl(who, Font.BOLD, 11, MUTED); nl.setBorder(new EmptyBorder(6, 4, 0, 0));
        JPanel v = new JPanel(new BorderLayout()); v.setOpaque(false); v.add(nl, BorderLayout.NORTH); v.add(row); return v;
    }
    static JComponent msgRow0(Msg m, boolean mine, String who) {
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
        put(in, tag("SESSION CONFIRMED", GREEN)); gap(in, 8); put(in, lbl(cs.skill + " " + whenText(cs.at), Font.BOLD, 14, TXT)); put(in, lbl(cs.mode + (cs.videoCall ? " - video call" : ""), Font.PLAIN, 14, MUTED));
        JButton join = btn(BookingsScreen.joinLabel(cs), () -> BookingsScreen.join(cs));
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
    public static JComponent groupRow(Group g, long last, boolean sel) {
        Color rowBg = sel ? YEL : leftBg(); Color nameC = sel ? ONACC : TXT;
        JPanel r = new JPanel(new BorderLayout(10, 0)); r.setBackground(rowBg); r.setBorder(new CompoundBorder(new MatteBorder(0, 0, BW, 0, INK), new EmptyBorder(10, 0, 10, 14))); r.setCursor(new Cursor(Cursor.HAND_CURSOR));
        JPanel bar = new JPanel(); bar.setPreferredSize(new Dimension(6, 0)); bar.setBackground(sel ? INK : rowBg);
        JPanel av = new JPanel(new GridBagLayout()); av.setOpaque(false); av.add(groupAvatar(g, 62));
        JPanel west = new JPanel(new BorderLayout(10, 0)); west.setOpaque(false); west.add(bar, BorderLayout.WEST); west.add(av);
        JPanel mid = new JPanel(new GridBagLayout()); mid.setOpaque(false); GridBagConstraints gc = new GridBagConstraints(); gc.anchor = GridBagConstraints.WEST; gc.weightx = 1;
        JPanel tv = vbox(); put(tv, lbl(g.name, Font.BOLD, 16, nameC)); boolean inCall = campus.skillswap.service.GroupCalls.inRoom(g.id) || !campus.skillswap.service.GroupCalls.liveUsers(g).isEmpty();
        put(tv, lbl("GROUP \u2022 " + g.members.size() + " members" + (inCall ? "  \u25CF IN A CALL" : ""), Font.BOLD, 10, inCall ? (sel ? ONACC : GREEN) : (sel ? ONACC : MUTED))); mid.add(tv, gc);
        r.add(west, BorderLayout.WEST); r.add(mid);
        if (last > 0) { JPanel e = new JPanel(new BorderLayout()); e.setOpaque(false); e.add(lbl(tshort(last), Font.BOLD, 12, nameC), BorderLayout.NORTH); r.add(e, BorderLayout.EAST); }
        String key = g.key(); r.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent ev) { chatWith = key; draft = ""; editing = 0; show("Messages"); } });
        return r;
    }
    /** Square call button whose label is a drawn icon; the tooltip keeps the action name accessible. */
    static JButton callButton(String tip, Icon icon, boolean primary, Runnable r) {
        JButton b = primary ? btn("", r) : ghost("", r);
        b.setIcon(icon); b.setText(""); b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        b.setBorder(new EmptyBorder(9, 12, 9, 12));
        if (primary) { b.setBackground(GREEN); b.setForeground(ONACC); }
        return b;
    }
    /** Telephone receiver icon, drawn so it matches the app without depending on emoji fonts. */
    static Icon phoneIcon(int sz, Color color) {
        return new Icon() {
            public int getIconWidth() { return sz; }
            public int getIconHeight() { return sz; }
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g);
                g.translate(x, y); g.scale(sz / 24.0, sz / 24.0); g.setColor(color);
                Path2D p = new Path2D.Float();
                p.moveTo(22, 16.92); p.lineTo(22, 19.92); p.quadTo(22, 21.92, 19.82, 21.92);
                p.quadTo(13.5, 21.5, 11.19, 18.85); p.quadTo(6.0, 16.0, 5.19, 12.85);
                p.quadTo(2.3, 9.0, 2.12, 4.18); p.quadTo(2.12, 2, 4.11, 2); p.lineTo(7.11, 2);
                p.quadTo(9.11, 2, 9.11, 3.72); p.quadTo(9.4, 5.3, 9.81, 6.53);
                p.quadTo(10.2, 7.8, 9.36, 8.64); p.lineTo(8.09, 9.91);
                p.quadTo(12.0, 13.9, 14.09, 15.91); p.lineTo(15.36, 14.64);
                p.quadTo(16.4, 13.8, 17.47, 14.19); p.quadTo(18.9, 14.6, 20.28, 14.89);
                p.quadTo(22, 15.1, 22, 16.92); p.closePath();
                g.fill(p); g.dispose();
            }
        };
    }
    /** Video-camera icon: body, lens and side cone. */
    static Icon videoIcon(int sz, Color color) {
        return new Icon() {
            public int getIconWidth() { return sz; }
            public int getIconHeight() { return sz; }
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g); double s = sz;
                g.translate(x, y); g.setColor(color);
                g.setStroke(new BasicStroke(Math.max(1.8f, sz * 0.085f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int arc = (int) Math.round(s * 0.12);
                g.drawRoundRect((int) Math.round(s * 0.10), (int) Math.round(s * 0.31), (int) Math.round(s * 0.52), (int) Math.round(s * 0.38), arc, arc);
                g.fillOval((int) Math.round(s * 0.27), (int) Math.round(s * 0.43), (int) Math.round(s * 0.15), (int) Math.round(s * 0.15));
                Path2D lens = new Path2D.Float();
                lens.moveTo(s * 0.61, s * 0.40); lens.lineTo(s * 0.87, s * 0.30); lens.lineTo(s * 0.87, s * 0.70); lens.lineTo(s * 0.61, s * 0.60); lens.closePath();
                g.fill(lens); g.dispose();
            }
        };
    }
    /** Header of a one-to-one chat: picture, name, online state and the call / booking buttons. */
    static JPanel dmHeader(User o) {
        JPanel h = new JPanel(new BorderLayout(14, 0)); h.setBackground(BG); h.setBorder(new CompoundBorder(new MatteBorder(0, 0, CB, 0, divider()), new EmptyBorder(14, 26, 14, 26)));
        JPanel hav = new JPanel(new GridBagLayout()); hav.setOpaque(false); hav.add(person(ring(o.username), 58, () -> o.photo));
        JPanel hi = new JPanel(new GridBagLayout()); hi.setOpaque(false); JPanel hv = vbox();
        put(hv, lbl(name(o), Font.BOLD, 20, TXT)); put(hv, lbl("\u25CF " + (campus.skillswap.service.Presence.isOnline(o) ? "Online" : "Offline"), Font.PLAIN, 16, campus.skillswap.service.Presence.isOnline(o) ? GREEN : MUTED)); GridBagConstraints g = new GridBagConstraints(); g.anchor = GridBagConstraints.WEST; g.weightx = 1; hi.add(hv, g);
        JPanel hb = new JPanel(new GridBagLayout()); hb.setOpaque(false); JPanel hbr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); hbr.setOpaque(false);
        hbr.add(ghost("\u2691", () -> report(o.username)));
        hbr.add(callButton("Voice call", phoneIcon(22, TXT), false, () -> campus.skillswap.service.Calls.start(o.username, false)));
        hbr.add(callButton("Video call", videoIcon(22, ONACC), true, () -> campus.skillswap.service.Calls.start(o.username, true)));
        if (!o.teach.isEmpty()) { JButton bk = btn("Book Session", () -> book(o.username, me.username, o.teach.get(0))); bk.setFont(f(Font.BOLD, 16)); bk.setBorder(new EmptyBorder(10, 18, 10, 18)); hbr.add(bk); }
        hb.add(hbr); h.add(hav, BorderLayout.WEST); h.add(hi); h.add(hb, BorderLayout.EAST); return h;
    }
    /** Header of a group chat: group picture, name, members line, Members and Group call buttons. */
    static JPanel groupHeader(Group gr) {
        JPanel h = new JPanel(new BorderLayout(14, 0)); h.setBackground(BG); h.setBorder(new CompoundBorder(new MatteBorder(0, 0, CB, 0, divider()), new EmptyBorder(14, 26, 14, 26)));
        JPanel hav = new JPanel(new GridBagLayout()); hav.setOpaque(false); hav.add(groupAvatar(gr, 58));
        JPanel hi = new JPanel(new GridBagLayout()); hi.setOpaque(false); JPanel hv = vbox();
        put(hv, lbl(gr.name, Font.BOLD, 20, TXT)); put(hv, lbl(membersLine(gr), Font.PLAIN, 13, MUTED)); GridBagConstraints g = new GridBagConstraints(); g.anchor = GridBagConstraints.WEST; g.weightx = 1; hi.add(hv, g);
        JPanel hb = new JPanel(new GridBagLayout()); hb.setOpaque(false); JPanel hbr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); hbr.setOpaque(false);
        hbr.add(ghost("Members", () -> groupMembers(gr)));
        final boolean inThis = campus.skillswap.service.GroupCalls.inRoom(gr.id); final List<String> liveU = campus.skillswap.service.GroupCalls.liveUsers(gr); final boolean running = !inThis && !liveU.isEmpty(), isFull = running && campus.skillswap.service.GroupCalls.full(gr);
        String callText = inThis ? "Back to call" : isFull ? "Call is full" : running ? "Join call" : "";
        JButton vc = callButton(callText.isEmpty() ? "Group call" : callText, videoIcon(22, ONACC), true, () -> campus.skillswap.service.GroupCalls.start(gr));
        vc.setText(callText); vc.setIconTextGap(callText.isEmpty() ? 0 : 8);
        if (!callText.isEmpty()) { vc.setFont(f(Font.BOLD, 16)); vc.setBorder(new EmptyBorder(10, 18, 10, 18)); }
        vc.setEnabled(!isFull); if (isFull) vc.setDisabledIcon(videoIcon(22, MUTED)); hbr.add(vc);
        hb.add(hbr); h.add(hav, BorderLayout.WEST); h.add(hi); h.add(hb, BorderLayout.EAST);
        if (!inThis && !running) return h;
        // "call in progress" strip under the header, like Messenger / Meet: who is in it and a Join button
        JPanel north = new JPanel(new BorderLayout()); north.setBackground(BG); north.add(h);
        JPanel ban = new JPanel(new BorderLayout(10, 0)); ban.setBackground(YEL); ban.setBorder(new CompoundBorder(new MatteBorder(0, 0, CB, 0, divider()), new EmptyBorder(8, 26, 8, 26)));
        String who = liveU.stream().map(u -> name(u)).collect(Collectors.joining(", "));
        int n = campus.skillswap.service.GroupCalls.liveCount(gr);
        ban.add(lbl(inThis ? "\u25CF You are in this call" + (who.isEmpty() ? "" : " with " + who) : "\u25CF Call in progress (" + n + " of " + campus.skillswap.service.GroupCalls.MAX_PEOPLE + "): " + who, Font.BOLD, 13, ONACC));
        if (!isFull) { JButton jb = ghost(inThis ? "Open" : "Join", () -> campus.skillswap.service.GroupCalls.start(gr)); jb.setFont(f(Font.BOLD, 11)); jb.setBorder(new EmptyBorder(4, 14, 4, 14)); ban.add(jb, BorderLayout.EAST); }
        north.add(ban, BorderLayout.SOUTH); return north;
    }
    public static JComponent messagesPage() {
        JPanel rt = new JPanel(new BorderLayout()); rt.setBackground(BG);
        Map<String, Long> lastT = new HashMap<>();
        for (Msg m : S.msgs) { if (m.from.equals(me.username)) lastT.merge(m.to, m.time, Math::max); else if (m.to.equals(me.username)) lastT.merge(m.from, m.time, Math::max); else if (Group.isKey(m.to)) lastT.merge(m.to, m.time, Math::max); }
        List<Object> convos = new ArrayList<>();
        for (User u : S.users.values()) if (u != me && !u.banned) convos.add(u);
        for (Group g : S.groups) if (g.has(me.username)) { convos.add(g); lastT.putIfAbsent(g.key(), g.created); }
        java.util.function.Function<Object, String> keyOf = c -> c instanceof User ? ((User) c).username : ((Group) c).key();
        java.util.function.Function<Object, String> titleOf = c -> c instanceof User ? name((User) c) : ((Group) c).name;
        convos.sort((x, y) -> { int k = Long.compare(lastT.getOrDefault(keyOf.apply(y), 0L), lastT.getOrDefault(keyOf.apply(x), 0L)); return k != 0 ? k : titleOf.apply(x).compareToIgnoreCase(titleOf.apply(y)); });
        if (!validChat(chatWith) && !convos.isEmpty()) chatWith = keyOf.apply(convos.get(0));

        // ---- left: title, new group button, search pill, conversation list ----
        JPanel left = new JPanel(new BorderLayout()); left.setBackground(leftBg()); left.setPreferredSize(new Dimension(300, 0)); left.setBorder(new MatteBorder(0, 0, 0, CB, divider()));
        JPanel top = vbox(); top.setBorder(new EmptyBorder(18, 16, 14, 16));
        JPanel titleRow = new JPanel(new BorderLayout(8, 0)); titleRow.setOpaque(false); titleRow.add(lbl("Messages", Font.BOLD, 30, TXT));
        JButton ng = btn("+ Group", MessagesScreen::newGroup); ng.setToolTipText("Start a group chat"); JPanel ngw = new JPanel(new GridBagLayout()); ngw.setOpaque(false); ngw.add(ng); titleRow.add(ngw, BorderLayout.EAST);
        put(top, titleRow); gap(top, 8);
        JTextField q = pill("Search chats", true); q.setText(chatQ); q.addActionListener(e -> { chatQ = q.getText().trim(); show("Messages"); }); put(top, q);
        left.add(top, BorderLayout.NORTH);
        JPanel list = vbox();
        for (Object c : convos) {
            if (!chatQ.isEmpty() && !titleOf.apply(c).toLowerCase().contains(chatQ.toLowerCase())) continue;
            long lt = lastT.getOrDefault(keyOf.apply(c), 0L); boolean sel = keyOf.apply(c).equals(chatWith);
            put(list, c instanceof User ? convoRow((User) c, lt, sel) : groupRow((Group) c, lt, sel));
        }
        JPanel lw = new JPanel(new BorderLayout()); lw.setOpaque(false); lw.add(list, BorderLayout.NORTH);
        JScrollPane ls = new JScrollPane(lw, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ls.setBorder(null); ls.getViewport().setBackground(leftBg()); ls.getVerticalScrollBar().setUnitIncrement(18); left.add(ls);
        rt.add(left, BorderLayout.WEST);

        // ---- right: chat (one person or a group) ----
        JPanel right = new JPanel(new BorderLayout()); right.setBackground(BG);
        final boolean grp = Group.isKey(chatWith); final Group gr = grp ? groupOf(chatWith) : null; final User o = grp || chatWith == null ? null : S.users.get(chatWith);
        if (chatWith == null || (grp ? gr == null : o == null)) {
            JPanel e = new JPanel(new GridBagLayout()); e.setOpaque(false); e.add(lbl("Select a conversation to start chatting", Font.PLAIN, 14, MUTED)); right.add(e);
        } else {
            final String target = chatWith;
            right.add(grp ? groupHeader(gr) : dmHeader(o), BorderLayout.NORTH);

            JPanel msgs = vbox(); msgs.setBorder(new EmptyBorder(16, 30, 16, 30));
            List<Msg> conv = S.msgs.stream().filter(m -> grp ? m.to.equals(target) : ((m.from.equals(me.username) && m.to.equals(o.username)) || (m.from.equals(o.username) && m.to.equals(me.username)))).filter(m -> !m.hiddenFor(me.username)).collect(Collectors.toList());
            LocalDate lastDay = null;
            for (Msg m : conv) {
                LocalDate d = Instant.ofEpochMilli(m.time).atZone(ZoneId.systemDefault()).toLocalDate();
                if (!d.equals(lastDay)) { put(msgs, dayDivider(dayLabel(d))); lastDay = d; }
                boolean mine = m.from.equals(me.username);
                put(msgs, grp ? msgRow(m, mine, name(m.from), true) : msgRow(m, mine, name(o), false));
            }
            if (conv.isEmpty()) { put(msgs, dayDivider("TODAY")); put(msgs, lbl(grp ? "No messages yet - say hi to the group!" : "No messages yet - say hi to " + name(o) + "!", Font.PLAIN, 13, MUTED)); }
            if (!grp) { Sess cs = S.sessions.stream().filter(s -> s.status.equals("CONFIRMED") && involves(s, me) && involves(s, o)).reduce((x, y) -> y).orElse(null); if (cs != null) put(msgs, sessionBubble(cs)); }
            JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false); wrap.add(msgs, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(wrap); sp.setBorder(null); sp.getViewport().setBackground(BG); sp.getVerticalScrollBar().setUnitIncrement(18); right.add(sp);
            Timer st = new Timer(40, e -> sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum())); st.setRepeats(false); st.start();

            JTextField in = pill("Type a message...", false); in.setText(draft);
            in.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { draft = in.getText(); } public void removeUpdate(javax.swing.event.DocumentEvent e) { draft = in.getText(); } public void changedUpdate(javax.swing.event.DocumentEvent e) { draft = in.getText(); } });
            final Msg em = editing != 0 ? msgById(editing) : null; if (editing != 0 && (em == null || em.deleted || !em.from.equals(me.username) || !em.to.equals(target))) editing = 0;
            Runnable send = () -> { String t = in.getText().trim(); if (t.isEmpty()) return; draft = "";
                if (t.startsWith(SYS)) t = t.substring(1).trim();                      // typed text can never pose as a notice line
                if (t.isEmpty()) return;
                if (editing != 0) { Msg tm = msgById(editing); editing = 0; if (tm != null && !tm.deleted && !t.equals(tm.text)) { tm.text = t; tm.edited = true; save(); } }
                else sendMsg(target, t);
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
