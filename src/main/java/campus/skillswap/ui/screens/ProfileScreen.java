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
import static campus.skillswap.ui.screens.MessagesScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** My profile, edit profile, reports, sync. */
public class ProfileScreen {

    // ====================== PROFILE ======================
    public static JComponent profilePage() {
        Pg p = pg("My Profile & Rating", me.email, profileActions());
        JPanel hd = card(); hd.add(avatarWrap(name(me), 72), BorderLayout.WEST); JPanel inf = vbox(); JPanel nm = row(); nm.add(lbl(name(me), Font.BOLD, 18, TXT)); if (verified(me)) nm.add(tag("VERIFIED TUTOR", GREEN));
        if (me.moderator) nm.add(tag("MODERATOR", GREEN));
        nm.add(tag(me.publicRatings ? "RATINGS PUBLIC" : "RATINGS PRIVATE", MUTED)); put(inf, nm); put(inf, lbl(me.bio.isEmpty() ? "Add your year & course with Edit Profile" : me.bio, Font.PLAIN, 12, MUTED));
        put(inf, lbl("Username: " + me.username, Font.PLAIN, 11, MUTED)); hd.add(inf); put(p.body, hd); gap(p.body, 8);
        JPanel stats = new JPanel(new GridLayout(1, 3, 8, 0)); stats.setOpaque(false);
        long sess = S.sessions.stream().filter(s -> s.status.equals("COMPLETED") && involves(s, me)).count();
        String[][] sv = {{"" + sess, "Sessions"}, {"" + reviews(me).size(), "Reviews"}, {"" + learnedN(me), "Skill swaps"}};
        for (String[] s : sv) { JPanel c = card(); c.add(lbl(s[0], Font.BOLD, 26, TXT), BorderLayout.CENTER); c.add(lbl(s[1], Font.PLAIN, 11, MUTED), BorderLayout.SOUTH); stats.add(c); } put(p.body, stats); gap(p.body, 10);
        put(p.body, skillRow("Skills I teach", me.teach, GREEN)); gap(p.body, 6); put(p.body, skillRow("Want to learn", me.learn, GOLD)); gap(p.body, 8);
        JPanel acts = row(); acts.add(ghost("Sync ratings online", ProfileScreen::sync)); JCheckBox nb = check("Open to impromptu swap now", me.nearby); nb.addActionListener(e -> { me.nearby = nb.isSelected(); save(); }); acts.add(nb); put(p.body, acts); gap(p.body, 8);
        put(p.body, lbl("Recently reviewed", Font.BOLD, 13, MUTED)); gap(p.body, 4); List<Sess> rs = reviews(me);
        if (rs.isEmpty()) put(p.body, lbl("No reviews yet - complete a session to receive feedback.", Font.PLAIN, 12, MUTED));
        for (int i = rs.size() - 1; i >= 0; i--) {
            Sess s = rs.get(i); JPanel c = card(); c.add(avatarWrap(name(s.learner), 32), BorderLayout.WEST);
            JPanel iv = vbox(); put(iv, lbl(name(s.learner), Font.BOLD, 12, TXT));
            put(iv, lbl(s.skill + (s.review.isEmpty() ? "" : " - " + s.review), Font.PLAIN, 12, MUTED)); c.add(iv);
            JPanel rt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8)); rt.setOpaque(false);
            rt.add(new StarRating(15, true).value(s.rating)); rt.add(lbl(String.format("%.1f", (double) s.rating), Font.BOLD, 11, GOLD));
            c.add(rt, BorderLayout.EAST); put(p.body, c); gap(p.body, 5);
        }
        return p.root;
    }
    /** Header buttons: edit profile details and change the public username. */
    static JPanel profileActions() {
        JPanel a = row(); a.add(btn("Edit Profile", ProfileScreen::editProfile)); a.add(ghost("Set nickname", ProfileScreen::setNickname)); return a;
    }
    public static void changeUsername() {
        JTextField un = field(); un.setText(me.username); JPasswordField pw = pass();
        JPanel b = vbox();
        put(b, labeled("New username", un)); gap(b, 10);
        put(b, labeled("Current password", passRow(pw))); gap(b, 4);
        put(b, lbl("Your username is public. Your real name stays hidden.", Font.PLAIN, 11, MUTED));
        b.setPreferredSize(new Dimension(440, b.getPreferredSize().height));
        if (!dialog("Change username", "Pick a new username for your account", YEL, b, "SAVE", () -> {
            String name = un.getText().trim(), pass = new String(pw.getPassword());
            return name.length() < 3 ? "Username must be at least 3 characters."
                : name.equalsIgnoreCase(me.username) ? "That is already your username."
                : S.users.keySet().stream().anyMatch(x -> x.equalsIgnoreCase(name)) ? "That username is already taken."
                : !me.passHash.equals(hash(me.username, pass)) ? "Current password is incorrect." : null; })) return;
        String newName = un.getText().trim();
        me.passHash = hash(newName, new String(pw.getPassword()));
        renameUser(me.username, newName);
        show("Profile"); toast("Username changed to " + newName);
    }
    public static void setNickname() {
        JTextField nm = field(); nm.setText(me.nickname != null ? me.nickname : ""); JPanel b = vbox();
        put(b, labeled("Nickname", nm)); gap(b, 4);
        put(b, lbl("Leave blank to use your normal name.", Font.PLAIN, 11, MUTED));
        b.setPreferredSize(new Dimension(440, b.getPreferredSize().height));
        if (!dialog("Set nickname", "Enter a nickname for group chats", YEL, b, "SAVE", () -> {
            String n = nm.getText().trim(); return n.isEmpty() ? null : n.length() < 2 ? "Nickname must be at least 2 characters." : null; })) return;
        me.nickname = nm.getText().trim(); saveEdit(); show("Profile"); toast("Nickname set to " + me.nickname);
    }
    public static JComponent skillRow(String title, List<String> list, Color col) {
        JPanel c = card(); c.add(lbl(title, Font.BOLD, 12, TXT), BorderLayout.NORTH); JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6)); r.setOpaque(false);
        for (String s : new ArrayList<>(list)) { JButton b = chip(s + "  \u00D7", false, () -> { list.remove(s); save(); show("Profile"); }); b.setForeground(col); b.setToolTipText("Click to remove"); r.add(b); }
        r.add(btn("+ Add", () -> { JComboBox<String> cb = combo(CAT.keySet().stream().map(x -> x.substring(0, 1).toUpperCase() + x.substring(1)).toArray(String[]::new)); cb.setEditable(true);
            if (ask("Add skill", form(new String[]{"Skill"}, new JComponent[]{cb}))) { String s = String.valueOf(cb.getSelectedItem()).trim(); if (!s.isEmpty() && list.stream().noneMatch(x -> x.equalsIgnoreCase(s))) { list.add(s); save(); show("Profile"); } } }));
        c.add(r); return c;
    }
    /** Crops the picture to a centred square, shrinks it to 256 px and stores it as a small JPEG (about 20 KB). */
    static byte[] makePhoto(File f) throws IOException {
        BufferedImage src = javax.imageio.ImageIO.read(f);
        if (src == null) throw new IOException("That file is not a picture the app can read. Use JPG, PNG, GIF or BMP.");
        int side = Math.min(src.getWidth(), src.getHeight()); BufferedImage cur = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        while (cur.getWidth() / 2 >= 256) cur = squareScale(cur, cur.getWidth() / 2);      // halve step by step so it stays sharp
        cur = squareScale(cur, 256);
        javax.imageio.ImageWriter w = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
        javax.imageio.ImageWriteParam pr = w.getDefaultWriteParam(); pr.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT); pr.setCompressionQuality(0.86f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (javax.imageio.stream.ImageOutputStream io = javax.imageio.ImageIO.createImageOutputStream(bo)) { w.setOutput(io); w.write(null, new javax.imageio.IIOImage(cur, null, null), pr); } finally { w.dispose(); }
        return bo.toByteArray();
    }
    static BufferedImage squareScale(BufferedImage in, int n) {
        BufferedImage o = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB); Graphics2D g = o.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, n, n); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(in, 0, 0, n, n, null); g.dispose(); return o;
    }
    static JComponent sectionTitle(String t) {
        JPanel p = vbox(); p.setOpaque(false); put(p, lbl(t, Font.BOLD, 11, MUTED)); gap(p, 4);
        JPanel r = new JPanel(); r.setBackground(INK); r.setPreferredSize(new Dimension(1, 3)); r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 3)); r.setMinimumSize(new Dimension(1, 3)); p.add(r); return p;
    }
    public static void editProfile() {
        final byte[][] photo = {me.photo};
        JTextArea bio = area(4, 26); bio.setText(me.bio);
        JCheckBox pub = check("Make my ratings public", me.publicRatings);
        final JDialog dlg = new JDialog(frame, "Edit profile", true); iconify(dlg); final boolean[] saved = {false};
        JComponent av = avatarOf(name(me), 96, () -> photo[0]);
        JButton choose = btn("Choose photo...", null), remove = ghost("Remove", null); remove.setEnabled(photo[0] != null);
        JLabel msg = lbl("JPG or PNG. It is cropped to a square.", Font.PLAIN, 11, MUTED);
        choose.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(campus.skillswap.ui.media.FileFinder.lastDir()); fc.setDialogTitle("Choose a profile picture"); fc.setFileFilter(new FileNameExtensionFilter("Pictures (JPG, PNG, GIF, BMP)", "jpg", "jpeg", "png", "gif", "bmp"));
            campus.skillswap.ui.media.PickerPreview.install(fc); campus.skillswap.ui.media.FileFinder finder = campus.skillswap.ui.media.FileFinder.install(fc); fc.setPreferredSize(new Dimension(940, 620));
            if (fc.showOpenDialog(dlg) != JFileChooser.APPROVE_OPTION) { finder.stopPlayer(); return; }
            finder.stopPlayer();
            campus.skillswap.ui.media.FileFinder.remember(fc);
            try { File f = fc.getSelectedFile(); if (f == null || !f.isFile()) return; if (f.length() > 25L * 1024 * 1024) throw new IOException("That picture is over 25 MB. Pick a smaller one.");
                photo[0] = makePhoto(f); remove.setEnabled(true); msg.setText("Photo ready - press Save to keep it."); av.repaint();
            } catch (Exception ex) { msg.setText(nz(ex.getMessage()).split("\n")[0]); }
        });
        remove.addActionListener(e -> { photo[0] = null; remove.setEnabled(false); msg.setText("Photo removed - press Save to keep the change."); av.repaint(); });

        JPanel body = vbox(); body.setOpaque(true); body.setBackground(PANEL); body.setBorder(new EmptyBorder(20, 24, 8, 24));
        put(body, sectionTitle("PROFILE PHOTO")); gap(body, 12);
        JPanel top = new JPanel(new BorderLayout(18, 0)); top.setOpaque(false);
        JPanel avBox = new JPanel(new GridBagLayout()); avBox.setOpaque(false); avBox.add(av); top.add(avBox, BorderLayout.WEST);
        JPanel side = new JPanel(new GridBagLayout()); side.setOpaque(false); GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.gridy = 0; c.insets = new Insets(0, 0, 8, 0);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); btns.setOpaque(false); btns.add(choose); btns.add(remove); side.add(btns, c); c.gridy = 1; c.insets = new Insets(0, 0, 0, 0); side.add(msg, c);
        top.add(side, BorderLayout.CENTER); put(body, top); gap(body, 20);
        put(body, sectionTitle("ABOUT YOU")); gap(body, 8);
        // Username change section
        JTextField newUsername = field(); newUsername.setText(me.username);
        JPasswordField pw = pass();
        JPanel unPanel = new JPanel(new BorderLayout(6, 0)); unPanel.setOpaque(false);
        unPanel.add(lbl("Username: " + me.username, Font.PLAIN, 11, MUTED), BorderLayout.NORTH);
        unPanel.add(labeled("New username", newUsername), BorderLayout.CENTER);
        JPanel pwPanel = new JPanel(new BorderLayout(6, 0)); pwPanel.setOpaque(false);
        pwPanel.add(labeled("Current password (required to change username)", pw), BorderLayout.NORTH);
        pwPanel.add(lbl("Leave username unchanged if you don't want to rename.", Font.PLAIN, 10, MUTED), BorderLayout.SOUTH);
        JPanel unSection = new JPanel(new BorderLayout(6, 6)); unSection.setOpaque(false);
        unSection.add(unPanel, BorderLayout.NORTH); unSection.add(pwPanel, BorderLayout.SOUTH);
        put(body, unSection); gap(body, 14);
        JPanel fm = form(new String[]{"Bio / major"}, new JComponent[]{scroller(bio)}); put(body, fm); gap(body, 14);
        put(body, sectionTitle("PRIVACY")); gap(body, 6); JPanel pv = vbox(); pv.setOpaque(false); put(pv, pub); put(body, pv);

        JButton save = btn("Save changes", null);
        save.addActionListener(e -> {
            String newName = newUsername.getText().trim();
            if (!newName.equals(me.username)) {
                if (newName.length() < 3) { info("Username must be at least 3 characters."); return; }
                if (S.users.keySet().stream().anyMatch(x -> x.equalsIgnoreCase(newName))) { info("That username is already taken."); return; }
                String pwStr = new String(pw.getPassword());
                if (pwStr.isEmpty() || !me.passHash.equals(hash(me.username, pwStr))) { info("Current password is required and must be correct to change username."); return; }
            }
            saved[0] = true; dlg.dispose();
        });
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); foot.setOpaque(false); foot.setBorder(new EmptyBorder(14, 24, 18, 24)); foot.add(ghost("Cancel", dlg::dispose)); foot.add(save);
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(PANEL); root.add(body, BorderLayout.CENTER); root.add(foot, BorderLayout.SOUTH);
        dlg.setContentPane(root); dlg.getRootPane().setDefaultButton(save);
        dlg.getRootPane().registerKeyboardAction(e -> dlg.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dlg.pack(); dlg.setSize(Math.max(520, dlg.getWidth()), dlg.getHeight()); dlg.setResizable(false); dlg.setLocationRelativeTo(frame);
        busy++; try { dlg.setVisible(true); } finally { busy--; }
        if (!saved[0]) return;
        me.bio = bio.getText().trim(); me.publicRatings = pub.isSelected(); me.photo = photo[0];
        String newName = newUsername.getText().trim();
        if (!newName.equals(me.username)) {
            String pwStr = new String(pw.getPassword());
            me.passHash = hash(newName, pwStr);
            renameUser(me.username, newName);
        }
        save(); show("Profile"); toast("Profile updated");
    }
    /** Read-only profile card for another student, opened by clicking their avatar or name. */
    public static void profileDialog(User u) {
        if (u == null) return;
        stopInline();                                    // no video audio behind the profile card
        JDialog d = new JDialog(frame, "Profile - " + name(u), true); iconify(d);
        JPanel outer = new JPanel(new BorderLayout()); outer.setBackground(BG); outer.setBorder(new EmptyBorder(16, 16, 12, 12));
        JPanel card = new JPanel(new BorderLayout()); card.setBackground(PANEL); card.setBorder(new LineBorder(INK, BW));

        JPanel head = new JPanel(new BorderLayout(14, 0)); head.setBackground(YEL);
        head.setBorder(new CompoundBorder(new MatteBorder(0, 0, CB, 0, INK), new EmptyBorder(14, 20, 12, 20)));
        head.add(avatarWrap(name(u), 64), BorderLayout.WEST);
        JPanel ht = vbox(); JPanel nm = row();
        nm.add(lbl(name(u), Font.BOLD, 22, ONACC));
        if (verified(u)) nm.add(tag("VERIFIED TUTOR", GREEN));
        if (u.moderator) nm.add(tag("MODERATOR", BLUE));
        if (u.banned) nm.add(tag("BANNED", PINKT));
        put(ht, nm); put(ht, lbl("@" + u.username + (u.bio.isEmpty() ? "" : "  \u2022  " + u.bio), Font.PLAIN, 12, ONACC));
        if (u.banned && u.banReason != null && !u.banReason.isEmpty()) put(ht, lbl("Ban reason: " + u.banReason, Font.PLAIN, 11, ONACC));
        head.add(ht, BorderLayout.CENTER); card.add(head, BorderLayout.NORTH);

        JPanel body = vbox(); body.setBorder(new EmptyBorder(14, 20, 8, 20));
        long sess = S.sessions.stream().filter(s -> s.status.equals("COMPLETED") && involves(s, u)).count();
        JPanel stats = new JPanel(new GridLayout(1, 3, 8, 0)); stats.setOpaque(false);
        for (String[] sv : new String[][]{{"" + sess, "Sessions"}, {"" + reviews(u).size(), "Reviews"}, {"" + learnedN(u), "Skill swaps"}}) {
            JPanel c = card(); c.add(lbl(sv[0], Font.BOLD, 26, TXT), BorderLayout.CENTER); c.add(lbl(sv[1], Font.PLAIN, 11, MUTED), BorderLayout.SOUTH); stats.add(c);
        }
        put(body, stats); gap(body, 10);
        put(body, readSkillCard("Skills " + name(u).split("\\s+")[0] + " teaches", u.teach, GREEN)); gap(body, 6);
        put(body, readSkillCard("Wants to learn", u.learn, GOLD)); gap(body, 10);

        put(body, lbl("RATING & REVIEWS", Font.BOLD, 11, MUTED)); gap(body, 5);
        if (!u.publicRatings) {
            put(body, lbl("<html><body style='width:440px'>" + esc(name(u)) + " keeps their ratings private.</body></html>", Font.PLAIN, 12, MUTED));
        } else {
            List<Sess> rs = reviews(u);
            JPanel rc = card(); rc.add(rs.isEmpty() ? lbl("No reviews yet - be the first to book a session.", Font.PLAIN, 12, MUTED) : new StarRating(18, true).value(avg(u)), BorderLayout.WEST);
            if (!rs.isEmpty()) { JPanel rt = row(); rt.add(lbl(String.format("%.1f", avg(u)), Font.BOLD, 18, GOLD)); rt.add(lbl("(" + rs.size() + (rs.size() == 1 ? " review)" : " reviews)"), Font.PLAIN, 12, MUTED)); rc.add(rt, BorderLayout.CENTER); }
            put(body, rc); gap(body, 5);
            for (int i = rs.size() - 1; i >= 0; i--) {
                Sess s = rs.get(i); JPanel c = card(); c.add(avatarWrap(name(s.learner), 30), BorderLayout.WEST);
                JPanel iv = vbox(); put(iv, lbl(name(s.learner) + "  \u2022  " + s.skill, Font.BOLD, 12, TXT));
                put(iv, lbl(s.review.isEmpty() ? "(no comment)" : s.review, Font.PLAIN, 12, MUTED)); c.add(iv);
                JPanel rt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8)); rt.setOpaque(false);
                rt.add(new StarRating(14, true).value(s.rating)); rt.add(lbl(String.format("%.1f", (double) s.rating), Font.BOLD, 11, GOLD));
                c.add(rt, BorderLayout.EAST); put(body, c); gap(body, 5);
            }
        }

        JScrollPane sp = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null); sp.getViewport().setBackground(PANEL); sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setPreferredSize(new Dimension(560, Math.min(560, Math.max(240, body.getPreferredSize().height))));
        card.add(sp, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10)); foot.setOpaque(false); foot.setBorder(new EmptyBorder(0, 16, 2, 16));
        if (u == me) foot.add(btn("Open My Profile", () -> { d.dispose(); show("Profile"); }));
        else {
            foot.add(ghost("\u2691 Report", () -> { d.dispose(); report(u.username); }));
            foot.add(ghost("Message", () -> { d.dispose(); chatWith = u.username; draft = ""; show("Messages"); }));
            if (!u.teach.isEmpty()) foot.add(btn("BOOK SESSION", () -> { d.dispose(); book(u.username, me.username, u.teach.get(0)); }));
        }
        foot.add(ghost("Close", d::dispose)); card.add(foot, BorderLayout.SOUTH);
        outer.add(card); d.setContentPane(outer);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setResizable(false); d.pack(); d.setLocationRelativeTo(frame);
        busy++; try { d.setVisible(true); } finally { busy--; }
    }
    /** Read-only skill chips (the editable one on My Profile adds/removes skills). */
    static JPanel readSkillCard(String title, List<String> list, Color col) {
        JPanel c = card(); c.add(lbl(title, Font.BOLD, 12, TXT), BorderLayout.NORTH);
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6)); r.setOpaque(false);
        if (list.isEmpty()) r.add(lbl("Nothing listed yet", Font.PLAIN, 12, MUTED)); else for (String s : list) r.add(tag(s, col));
        c.add(r); return c;
    }
    /** Makes an avatar / name (and everything inside it) open that person's profile. */
    public static void profileLink(JComponent c, User u) { profileLink(c, u, null); }
    public static void profileLink(JComponent c, User u, Runnable before) {
        if (c == null || u == null) return;
        Runnable open = () -> { if (before != null) before.run(); if (u == me) show("Profile"); else profileDialog(u); };
        linkAll(c, open); c.setToolTipText("View " + name(u) + "'s profile");
    }
    static void linkAll(Component c, Runnable r) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { r.run(); e.consume(); } });
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) linkAll(k, r);
    }
    public static void report(String target) {
        JComboBox<String> r = combo(new String[]{"No-show", "Inappropriate behavior", "Harassment", "Spam / fake profile", "Other"}); JTextArea d = area(3, 22);
        if (!ask("Report " + name(target), form(new String[]{"Reason", "Details"}, new JComponent[]{r, scroller(d)}))) return;
        Report rp = new Report(); rp.id = nextId(); rp.by = me.username; rp.target = target; rp.reason = String.valueOf(r.getSelectedItem()); rp.details = d.getText().trim(); rp.time = System.currentTimeMillis(); S.reports.add(rp); save();
        toast("Report submitted to campus moderators");
    }
    public static void sync() {
        try { Files.createDirectories(DIR); StringBuilder j = new StringBuilder("{\"synced\":\"" + LocalDateTime.now() + "\",\"profiles\":[");
            boolean first = true; for (User u : S.users.values()) if (u.publicRatings && !u.demo) { if (!first) j.append(","); first = false;
                j.append("{\"name\":\"").append(name(u).replace("\"", "'")).append("\",\"rating\":").append(String.format("%.2f", avg(u))).append(",\"reviews\":").append(reviews(u).size()).append("}"); }
            j.append("]}"); Files.writeString(DIR.resolve("cloud-sync.json"), j.toString()); S.lastSync = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")); save();
            toast("Ratings & profile synced (simulated cloud: " + DIR.resolve("cloud-sync.json") + ")"); show(page);
        } catch (Exception e) { info("Offline - will sync later. Everything else keeps working against the database."); }
    }
}
