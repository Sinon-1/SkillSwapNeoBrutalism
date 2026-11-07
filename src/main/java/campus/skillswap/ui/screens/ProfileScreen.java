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
        Pg p = pg("My Profile & Rating", me.email, btn("Edit Profile", ProfileScreen::editProfile));
        JPanel hd = card(); hd.add(avatarWrap(name(me), 64), BorderLayout.WEST); JPanel inf = vbox(); JPanel nm = row(); nm.add(lbl(name(me), Font.BOLD, 18, TXT)); if (verified(me)) nm.add(tag("VERIFIED TUTOR", GREEN));
        nm.add(tag(me.publicRatings ? "RATINGS PUBLIC" : "RATINGS PRIVATE", MUTED)); put(inf, nm); put(inf, lbl(me.bio.isEmpty() ? "Add your year & course with Edit Profile" : me.bio, Font.PLAIN, 12, MUTED));
        put(inf, lbl(me.useRealName ? "Showing real name (username: " + me.username + ")" : "Showing username" + (me.realName.isEmpty() ? "" : " (real name hidden)"), Font.PLAIN, 11, MUTED)); hd.add(inf); put(p.body, hd); gap(p.body, 8);
        JPanel stats = new JPanel(new GridLayout(1, 3, 8, 0)); stats.setOpaque(false);
        long sess = S.sessions.stream().filter(s -> s.status.equals("COMPLETED") && involves(s, me)).count();
        String[][] sv = {{"" + sess, "Sessions"}, {"" + reviews(me).size(), "Reviews"}, {"" + learnedN(me), "Skill swaps"}};
        for (String[] s : sv) { JPanel c = card(); c.add(lbl(s[0], Font.BOLD, 26, TXT), BorderLayout.CENTER); c.add(lbl(s[1], Font.PLAIN, 11, MUTED), BorderLayout.SOUTH); stats.add(c); } put(p.body, stats); gap(p.body, 10);
        put(p.body, skillRow("Skills I teach", me.teach, GREEN)); gap(p.body, 6); put(p.body, skillRow("Want to learn", me.learn, GOLD)); gap(p.body, 8);
        JPanel acts = row(); acts.add(ghost("Sync ratings online", ProfileScreen::sync)); JCheckBox nb = check("Open to impromptu swap now", me.nearby); nb.addActionListener(e -> { me.nearby = nb.isSelected(); save(); }); acts.add(nb); put(p.body, acts); gap(p.body, 8);
        put(p.body, lbl("Recently reviewed", Font.BOLD, 13, MUTED)); gap(p.body, 4); List<Sess> rs = reviews(me);
        if (rs.isEmpty()) put(p.body, lbl("No reviews yet - complete a session to receive feedback.", Font.PLAIN, 12, MUTED));
        for (int i = rs.size() - 1; i >= 0; i--) { Sess s = rs.get(i); JPanel c = card(); c.add(avatarWrap(name(s.learner), 32), BorderLayout.WEST); JPanel iv = vbox(); put(iv, lbl(name(s.learner), Font.BOLD, 12, TXT)); put(iv, lbl(s.skill + (s.review.isEmpty() ? "" : " - " + s.review), Font.PLAIN, 12, MUTED)); c.add(iv); c.add(lbl(stars(s.rating), Font.PLAIN, 12, GOLD), BorderLayout.EAST); put(p.body, c); gap(p.body, 5); }
        return p.root;
    }
    public static JComponent skillRow(String title, List<String> list, Color col) {
        JPanel c = card(); c.add(lbl(title, Font.BOLD, 12, TXT), BorderLayout.NORTH); JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6)); r.setOpaque(false);
        for (String s : new ArrayList<>(list)) { JButton b = chip(s + "  \u00D7", false, () -> { list.remove(s); save(); show("Profile"); }); b.setForeground(col); b.setToolTipText("Click to remove"); r.add(b); }
        r.add(btn("+ Add", () -> { JComboBox<String> cb = combo(CAT.keySet().stream().map(x -> x.substring(0, 1).toUpperCase() + x.substring(1)).toArray(String[]::new)); cb.setEditable(true);
            if (ask("Add skill", form(new String[]{"Skill"}, new JComponent[]{cb}))) { String s = String.valueOf(cb.getSelectedItem()).trim(); if (!s.isEmpty() && list.stream().noneMatch(x -> x.equalsIgnoreCase(s))) { list.add(s); save(); show("Profile"); } } }));
        c.add(r); return c;
    }
    public static void editProfile() {
        JTextField rn = field(); rn.setText(me.realName); JTextArea bio = area(3, 22); bio.setText(me.bio);
        JCheckBox real = check("Show my real name publicly", me.useRealName), pub = check("Make my ratings public", me.publicRatings);
        if (!ask("Edit profile", form(new String[]{"Real name", "Bio / major", "", ""}, new JComponent[]{rn, scroller(bio), real, pub}))) return;
        me.realName = rn.getText().trim(); me.bio = bio.getText().trim(); me.useRealName = real.isSelected() && !me.realName.isEmpty(); me.publicRatings = pub.isSelected(); save(); show("Profile");
    }
    public static void profileDialog(User u) {
        if (u == null) return; StringBuilder sb = new StringBuilder(name(u) + (verified(u) ? "  [VERIFIED]" : "") + "\n" + (u.bio.isEmpty() ? "" : u.bio + "\n") + "\nTeaches: " + String.join(", ", u.teach) + "\nWants to learn: " + String.join(", ", u.learn) + "\nSessions taught: " + taught(u) + "\n");
        if (u.publicRatings) { sb.append("Rating: ").append(reviews(u).isEmpty() ? "no reviews yet" : String.format("%.1f", avg(u)) + " (" + reviews(u).size() + " reviews)").append("\n");
            for (Sess s : reviews(u)) sb.append("  ").append(stars(s.rating)).append(" ").append(s.review).append("\n"); } else sb.append("Ratings are private.\n");
        info(sb.toString());
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
