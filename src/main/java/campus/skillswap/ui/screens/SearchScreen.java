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
import static campus.skillswap.ui.screens.CertsScreen.*;
import static campus.skillswap.ui.screens.BookingsScreen.*;
import static campus.skillswap.ui.screens.NearbyScreen.*;
import static campus.skillswap.ui.screens.MessagesScreen.*;
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Search topics and tutor results. */
public class SearchScreen {

    // ====================== SEARCH ======================
    public static JComponent searchPage() {
        Pg p = pg("Search Topics", "Find a peer tutor by skill or category", null);
        JTextField q = searchField("Search topics (e.g., Java, Guitar)"); q.setText(searchQ);
        Runnable go = () -> { searchQ = q.getText().trim(); if (!searchQ.isEmpty()) { recent.remove(searchQ); recent.addFirst(searchQ); while (recent.size() > 5) recent.removeLast(); } show("Search"); };
        q.addActionListener(e -> go.run());
        JPanel sr = new JPanel(new BorderLayout(6, 0)); sr.setOpaque(false); sr.add(q); sr.add(btn("Search", go), BorderLayout.EAST); put(p.body, sr); gap(p.body, 6);
        if (!recent.isEmpty()) { put(p.body, lbl("Recent searches", Font.PLAIN, 11, MUTED)); JPanel rr = row(); for (String s : recent) rr.add(chip(s, false, () -> { searchQ = s; show("Search"); })); put(p.body, rr); }
        JPanel chips = row(); for (String c : CATS) chips.add(chip(c, c.equals(searchCat), () -> { searchCat = c; show("Search"); })); put(p.body, chips); gap(p.body, 8);
        if (!searchQ.isEmpty() || !searchCat.equals("All")) {
            put(p.body, lbl("Results", Font.BOLD, 13, MUTED)); gap(p.body, 4); int n = 0;
            for (User u : S.users.values()) { if (u == me) continue;
                for (String s : u.teach) if ((searchQ.isEmpty() || s.toLowerCase().contains(searchQ.toLowerCase())) && (searchCat.equals("All") || cat(s).equals(searchCat))) { put(p.body, tutorCard(u, s)); gap(p.body, 6); n++; } }
            if (n == 0) put(p.body, lbl("No tutors found. Try posting a request in the Skill Feed!", Font.PLAIN, 12, MUTED));
            gap(p.body, 10);
        }
        put(p.body, lbl("Trending Topics", Font.BOLD, 13, MUTED)); gap(p.body, 4);
        Map<String, Integer> cnt = new TreeMap<>(); for (User u : S.users.values()) if (u != me) for (String s : u.teach) cnt.merge(s, 1, Integer::sum);
        List<Map.Entry<String, Integer>> es = new ArrayList<>(cnt.entrySet()); es.sort((x, y) -> y.getValue() - x.getValue());
        JPanel g = new JPanel(new GridLayout(0, 2, 10, 10)); g.setOpaque(false);
        for (Map.Entry<String, Integer> en : es) {
            Color cc = catColor(cat(en.getKey())); JPanel t = card(); t.add(swatchWrap(cc, 34), BorderLayout.WEST);
            JPanel mid = vbox(); put(mid, lbl(en.getKey(), Font.BOLD, 13, TXT)); put(mid, lbl(en.getValue() + (String.valueOf(en.getValue()).equals("1") ? " tutor" : " tutors"), Font.PLAIN, 11, MUTED)); t.add(mid); t.add(tag(cat(en.getKey()).toUpperCase(), cc), BorderLayout.EAST);
            t.setCursor(new Cursor(Cursor.HAND_CURSOR)); t.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent ev) { searchQ = en.getKey(); show("Search"); } }); g.add(t);
        }
        put(p.body, g); return p.root;
    }
    public static JComponent tutorCard(User u, String skill) {
        JPanel c = card(); c.add(avatarWrap(name(u), 40), BorderLayout.WEST); JPanel inf = vbox(); JPanel h = row();
        h.add(lbl(name(u), Font.BOLD, 13, TXT)); if (verified(u)) h.add(tag("VERIFIED", GREEN)); h.add(tag(skill, PINK)); if (u.nearby) h.add(tag("NEARBY NOW", GOLD)); put(inf, h);
        put(inf, lbl(u.publicRatings ? (reviews(u).isEmpty() ? "No reviews yet" : stars(avg(u)) + " " + String.format("%.1f", avg(u)) + " (" + reviews(u).size() + ")") : "Ratings are private", Font.PLAIN, 11, GOLD));
        if (!u.bio.isEmpty()) put(inf, lbl(u.bio, Font.PLAIN, 11, MUTED)); c.add(inf);
        JPanel b = row(); b.add(btn("BOOK", () -> book(u.username, me.username, skill))); b.add(ghost("Message", () -> { chatWith = u.username; show("Messages"); })); b.add(ghost("View", () -> profileDialog(u))); c.add(b, BorderLayout.EAST); return c;
    }
}
