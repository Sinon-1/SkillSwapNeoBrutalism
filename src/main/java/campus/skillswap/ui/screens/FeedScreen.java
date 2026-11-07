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
import static campus.skillswap.ui.screens.SearchScreen.*;
import static campus.skillswap.ui.screens.CertsScreen.*;
import static campus.skillswap.ui.screens.BookingsScreen.*;
import static campus.skillswap.ui.screens.NearbyScreen.*;
import static campus.skillswap.ui.screens.MessagesScreen.*;
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Skill feed: posts, reels, matches, new post. */
public class FeedScreen {

    // ====================== FEED ======================
    public static String snippet(String skill) {
        switch (skill.toLowerCase()) {
            case "java": return "for (Skill s : teach) {\n    if (want.contains(s)) swap(s);\n}";
            case "python": return "for s in teach:\n    if s in want:\n        swap(s)";
            case "javascript": return "teach.filter(s => want.has(s))\n     .forEach(swap);";
            default: return null;
        }
    }
    public static JComponent feedPage() {
        Pg p = pg("Skill Feed", "Offers and requests from your campus", newPostButtons());
        JTextField q = searchField("Search topics (e.g., Java, Guitar)"); q.setText(feedQ); q.addActionListener(e -> { feedQ = q.getText().trim(); show("Feed"); }); put(p.body, q); gap(p.body, 8);
        JPanel chips = row(); for (String x : new String[]{"All", "Offers", "Requests", "Matches", "Reels"}) chips.add(chip(x, x.equals(feedFilter), () -> { feedFilter = x; show("Feed"); })); put(p.body, chips); gap(p.body, 6);
        if (feedFilter.equals("Matches")) {
            List<Match> ms = matches();
            if (ms.isEmpty()) put(p.body, lbl("No matches yet - add skills you want to learn in My Profile & Rating.", Font.PLAIN, 12, MUTED));
            for (Match m : ms) { put(p.body, matchCard(m)); gap(p.body, 8); }
            return p.root;
        }
        if (feedFilter.equals("Reels")) {
            List<Post> vs = videoPosts();
            if (vs.isEmpty()) put(p.body, lbl("No reels yet - tap + New Reel to share the first video.", Font.PLAIN, 12, MUTED));
            JPanel g = new JPanel(new GridLayout(0, 4, 12, 12)); g.setOpaque(false); for (Post po : vs) g.add(videoTile(po, 170, 300, true, () -> openReels(po.id))); put(p.body, g);
            return p.root;
        }
        List<Post> ps = new ArrayList<>(S.posts); Collections.reverse(ps); int n = 0;
        for (Post po : ps) {
            if (feedFilter.equals("Offers") && !po.type.equals("OFFER")) continue; if (feedFilter.equals("Requests") && !po.type.equals("REQUEST")) continue;
            if (!feedQ.isEmpty() && !(po.skill + " " + po.desc + " " + po.category + " " + name(po.author)).toLowerCase().contains(feedQ.toLowerCase())) continue;
            put(p.body, postCard(po)); gap(p.body, 8); n++;
        }
        if (n == 0) put(p.body, lbl("No posts match. Try another search or create a post.", Font.PLAIN, 12, MUTED));
        return p.root;
    }
    public static JComponent matchCard(Match m) {
        JPanel c = card(); c.add(avatarWrap(name(m.u), 40), BorderLayout.WEST); JPanel inf = vbox(); JPanel h = row();
        h.add(lbl(name(m.u), Font.BOLD, 13, TXT)); h.add(m.iTeach.isEmpty() ? tag("CAN TEACH YOU", BLUE) : tag("PERFECT SWAP", GREEN)); if (verified(m.u)) h.add(tag("VERIFIED", GREEN)); put(inf, h);
        put(inf, lbl("Teaches you: " + String.join(", ", m.theyTeach) + (m.iTeach.isEmpty() ? "" : "   \u2022   Wants to learn from you: " + String.join(", ", m.iTeach)), Font.PLAIN, 12, MUTED));
        c.add(inf, BorderLayout.CENTER); JPanel b = row(); b.add(btn("REQUEST SWAP", () -> book(m.u.username, me.username, m.theyTeach.get(0)))); b.add(ghost("Message", () -> { chatWith = m.u.username; draft = ""; show("Messages"); }));
        c.add(b, BorderLayout.EAST); return c;
    }
    public static JComponent postCard(Post po) {
        User a = S.users.get(po.author); boolean mine = po.author.equals(me.username); boolean offer = po.type.equals("OFFER");
        JPanel c = card(); c.add(avatarWrap(name(po.author), 40), BorderLayout.WEST); JPanel inf = vbox(); JPanel h = row();
        h.add(lbl(name(po.author), Font.BOLD, 13, TXT)); if (a != null && verified(a)) h.add(tag("VERIFIED", GREEN)); h.add(tag(offer ? "OFFERING" : "REQUESTING", offer ? GREEN : GOLD)); put(inf, h);
        if (a != null && !a.bio.isEmpty()) put(inf, lbl(a.bio, Font.PLAIN, 11, MUTED));
        put(inf, lbl("#" + po.skill.replace(" ", "") + "  #" + po.category + (offer ? "  #teaching" : "  #learning"), Font.BOLD, 11, PINKT));
        put(inf, lbl("<html><body style='width:300px'>" + esc(po.desc) + "</body></html>", Font.PLAIN, 12, MUTED));
        String sn = snippet(po.skill);
        if (sn != null) { JTextArea ta = new JTextArea(sn); ta.setEditable(false); ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); ta.setBackground(Color.BLACK); ta.setForeground(new Color(0x3DDC97)); ta.setBorder(new CompoundBorder(new LineBorder(INK, BW), new EmptyBorder(8, 10, 8, 10))); gap(inf, 4); put(inf, ta); }
        if (po.videoName != null) { gap(inf, 6); put(inf, inlineVideo(po)); gap(inf, 4); }
        JPanel foot = row(); JButton like = ghost("\u2665 " + po.likes.size(), () -> { });
        like.addActionListener(e -> { Post cur = postById(po.id); if (cur == null) return; if (!cur.likes.remove(me.username)) cur.likes.add(me.username); save();
            like.setText("\u2665 " + cur.likes.size()); like.setForeground(cur.likes.contains(me.username) ? PINKT : TXT); });
        like.setForeground(po.likes.contains(me.username) ? PINKT : TXT); foot.add(like); foot.add(lbl(ago(po.time), Font.PLAIN, 11, MUTED)); put(inf, foot);
        c.add(inf, BorderLayout.CENTER);
        JPanel ea = vbox(); ea.setPreferredSize(new Dimension(232, 96));
        if (a != null && a.publicRatings && !reviews(a).isEmpty()) put(ea, lbl("\u2605 " + String.format("%.1f", avg(a)) + " rating", Font.BOLD, 11, GOLD)); gap(ea, 6);
        if (mine) put(ea, wide(ghost("Delete", () -> { S.posts.remove(po); save(); show("Feed"); })));
        else {
            put(ea, wide(btn(offer ? "BOOK SESSION" : "OFFER TO TEACH", () -> { if (offer) book(po.author, me.username, po.skill); else book(me.username, po.author, po.skill); }))); gap(ea, 6);
            JPanel sm = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)) { public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); } }; sm.setOpaque(false); sm.add(ghost("Message", () -> { chatWith = po.author; draft = ""; show("Messages"); })); sm.add(ghost("View", () -> profileDialog(a))); sm.add(ghost("\u2691", () -> report(po.author))); put(ea, sm);
        }
        c.add(ea, BorderLayout.EAST); return c;
    }
    public static JComponent newPostButtons() {
        JPanel a = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)); a.setOpaque(false);
        a.add(ghost("+ New Reel", () -> newPost(true))); a.add(btn("+ New Post", () -> newPost(false))); return a;
    }
    public static void newPost(boolean reel) {
        JComboBox<String> type = combo(new String[]{"OFFER (I can teach)", "REQUEST (I want to learn)"});
        JComboBox<String> skill = combo(CAT.keySet().stream().map(s -> s.substring(0, 1).toUpperCase() + s.substring(1)).toArray(String[]::new)); skill.setEditable(true);
        JTextArea d = area(4, 24);
        File[] pick = new File[1]; JLabel vl = lbl("No video selected", Font.PLAIN, 12, MUTED);
        JButton vb = ghost("Choose video...", null);
        vb.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(); fc.setFileFilter(new FileNameExtensionFilter("Video files (mp4, mov, m4v, webm, mkv, avi)", "mp4", "mov", "m4v", "webm", "mkv", "avi"));
            if (fc.showOpenDialog(vb) != JFileChooser.APPROVE_OPTION) return; File f = fc.getSelectedFile();
            if (f.length() > MAX_VIDEO) { info("That video is " + mb(f.length()) + ". The limit is " + mb(MAX_VIDEO) + " - trim or compress it first."); return; }
            pick[0] = f; vl.setText(f.getName() + " (" + mb(f.length()) + ")"); });
        JPanel vrow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); vrow.setOpaque(false); vrow.add(vb); vrow.add(vl);
        if (!ask(reel ? "New reel" : "New post", form(new String[]{"Type", "Skill", "Details", "Video"}, new JComponent[]{type, skill, scroller(d), vrow}))) return;
        String sk = String.valueOf(skill.getSelectedItem()).trim(); if (sk.isEmpty()) return;
        if (reel && pick[0] == null) { info("A reel needs a video - choose one and try again."); return; }
        Post po = new Post(); po.id = nextId(); po.author = me.username; po.type = type.getSelectedIndex() == 0 ? "OFFER" : "REQUEST"; po.skill = sk; po.category = cat(sk);
        String txt = d.getText().trim(); po.desc = txt.isEmpty() ? (pick[0] != null ? "" : "(no details)") : txt; po.time = System.currentTimeMillis(); S.posts.add(po);
        List<String> l = po.type.equals("OFFER") ? me.teach : me.learn; if (l.stream().noneMatch(x -> x.equalsIgnoreCase(sk))) l.add(sk);
        save(); feedFilter = "All"; show("Feed"); toast("Post published");
        if (pick[0] != null) uploadVideo(po.id, pick[0]);
    }
}
