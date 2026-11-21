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
import static campus.skillswap.ui.Theme.*;
import static campus.skillswap.ui.Notifier.*;
import static campus.skillswap.ui.screens.LoginScreen.*;
import static campus.skillswap.ui.screens.ConnectionScreen.*;
import static campus.skillswap.ui.screens.SettingsScreen.*;
import static campus.skillswap.ui.screens.FeedScreen.*;
import static campus.skillswap.ui.screens.SearchScreen.*;
import static campus.skillswap.ui.screens.CertsScreen.*;
import static campus.skillswap.ui.screens.BookingsScreen.*;
import static campus.skillswap.ui.screens.NearbyScreen.*;
import static campus.skillswap.ui.screens.ModerationScreen.*;
import static campus.skillswap.ui.screens.MessagesScreen.*;
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Main window layout: sidebar, right-hand panel, page switching. */
public class AppShell {

    // ====================== APP STATE / SHELL ======================
    public static JFrame frame; public static JPanel root, content, sidebar, rightPanel; public static JScrollPane rightScroll; public static CardLayout rootCards = new CardLayout(); public static JLabel statusBar;
    public static String page = "Feed", feedFilter = "All", searchQ = "", searchCat = "All", bookTab = "Upcoming", chatWith = null, board = "Most Active", chatQ = "";
    public static TrayIcon tray; public static String feedQ = "", certFilter = "All", draft = ""; public static LocalDate dayFilter; public static final LinkedList<String> recent = new LinkedList<>();
    public static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("EEE, MMM d \u2022 h:mm a");
    // ====================== MAIN APP SHELL ======================
    public static final String[][] NAV = {{"Feed", "Skill Feed"}, {"Search", "Search Topics"}, {"Certs", "Verified Certificates"}, {"Bookings", "Bookings & Sessions"},
            {"Nearby", "Nearby Now"}, {"Messages", "Messages"}, {"Moderation", "Reports & Moderation"}, {"Profile", "My Profile & Rating"}, {"Settings", "Settings"}};
    public static JComponent appPanel() {
        JPanel p = new JPanel(new BorderLayout()); p.setBackground(BG);
        sidebar = new JPanel(); sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS)); sidebar.setBackground(PANEL); sidebar.setPreferredSize(new Dimension(224, 0));
        content = new JPanel(new BorderLayout()); content.setBackground(BG);
        rightPanel = new JPanel(); rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS)); rightPanel.setBackground(SIDE2);
        // rightPanel sits at the top of a BorderLayout so the scroll viewport can never stretch its children (that caused big empty gaps).
        // Tracking the viewport width keeps the rail from growing wider than its column (which made it scroll/clip sideways on small screens).
        JPanel rightWrap = new JPanel(new BorderLayout()) {
            public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
            public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 20; }
            public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 100; }
            public boolean getScrollableTracksViewportWidth() { return true; }
            public boolean getScrollableTracksViewportHeight() { return false; }
        };
        rightWrap.setBackground(SIDE2); rightWrap.add(rightPanel, BorderLayout.NORTH);
        rightScroll = new JScrollPane(rightWrap, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); rightScroll.setBorder(new MatteBorder(0, CB, 0, 0, INK));
        rightScroll.setPreferredSize(new Dimension(276, 0)); rightScroll.getViewport().setBackground(SIDE2);
        statusBar = lbl(" ", Font.BOLD, 11, TXT); statusBar.setBorder(new EmptyBorder(5, 12, 5, 12)); JPanel sb = new JPanel(new BorderLayout()); sb.setBackground(YEL); sb.setBorder(new MatteBorder(CB, 0, 0, 0, INK)); statusBar.setForeground(ONACC); sb.add(statusBar);
        p.add(sidebar, BorderLayout.WEST); p.add(content, BorderLayout.CENTER); p.add(rightScroll, BorderLayout.EAST); p.add(sb, BorderLayout.SOUTH); return p;
    }
    static JComponent errorPage(Throwable e) {
        JPanel p = card(); p.add(lbl("Page error", Font.BOLD, 16, TXT), BorderLayout.NORTH);
        p.add(lbl("<html><body style='width:340px'>" + esc(String.valueOf(e)) + "</body></html>", Font.PLAIN, 11, TXT), BorderLayout.CENTER);
        p.add(wide(ghost("Reload Page", () -> show(page))), BorderLayout.SOUTH); return p;
    }
    public static void show(String pgName) {
        if (me != null && me.moderator) pgName = "Moderation";
        stopInline(); page = pgName; content.removeAll();
        JComponent c;
        try {
            switch (pgName) {
                case "Search": c = searchPage(); break; case "Certs": c = certsPage(); break; case "Bookings": c = bookingsPage(); break;
                case "Nearby": c = nearbyPage(); break; case "Messages": c = messagesPage(); break; case "Moderation": c = moderationPage(); break; case "Profile": c = profilePage(); break;
                case "Settings": c = settingsPage(); break; default: c = feedPage();
            }
        } catch (Throwable e) { e.printStackTrace(); toast("Page error: " + e.getMessage()); c = errorPage(e); }
        content.add(c); content.revalidate(); content.repaint(); markNavSeen(pgName); buildSidebar();
        boolean side = !(pgName.equals("Messages") || pgName.equals("Settings") || pgName.equals("Moderation"));   // these use the full width
        rightScroll.setVisible(side); if (side) buildRight(); rightScroll.getParent().revalidate();
        try {
            if (me.moderator) statusBar.setText(netText() + "   |   Moderator: " + name(me) + "   |   Open reports: " + S.reports.stream().filter(r -> !r.resolved).count() + "   |   Last online sync: " + S.lastSync);
            else statusBar.setText(netText() + "   |   Signed in as " + name(me) + "   |   Credits: " + me.credits + "/" + MAX_CREDITS + "   |   Last online sync: " + S.lastSync);
        } catch (Throwable ignored) { /* status text is not worth breaking a page over */ }
    }
    static void logout() {
        stopInline(); closeReels(); campus.skillswap.service.Calls.shutdown(); campus.skillswap.service.Presence.goOffline(); save(); me = null; rootCards.show(root, "login");
    }
    /** Called after a refresh: a banned account is signed out immediately. */
    public static boolean sessionValid() {
        if (me == null) return false;
        if (!me.banned) return true;
        String why = me.banReason == null ? "" : me.banReason;
        campus.skillswap.service.Presence.goOffline(); campus.skillswap.service.Calls.shutdown(); closeReels(); stopInline();
        me = null; rootCards.show(root, "login");
        info("Your account has been banned by a campus moderator." + (why.isEmpty() ? "" : "\n\nReason: " + why));
        return false;
    }
    /** Logo mark from the same drawing used for the window/taskbar/installer icon, so brand art cannot drift. */
    static JComponent logoMark(int sz) {
        return new JComponent() {
            { Dimension d = new Dimension(sz, sz); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); }
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); aa(g);
                g.drawImage(appIcon(sz), 0, 0, null); g.dispose();
            }
        };
    }
    /** Counts for the left navigation; the badge disappears when that page is opened. */
    static long earnedCerts() { return me == null ? 0 : badges(me).stream().filter(b -> b[2].equals("Earned")).count(); }
    static long pendingRequests() { return me == null ? 0 : S.sessions.stream().filter(s -> s.status.equals("PENDING") && !s.requester.equals(me.username) && involves(s, me)).count(); }
    static String seenKey(String what) { return "nav.seen." + what + "." + (me == null ? "" : me.username); }
    static long seen(String what) { try { return Long.parseLong(c(seenKey(what), "0")); } catch (NumberFormatException e) { return 0; } }
    static long unseen(String what, long current) { return Math.max(0, current - seen(what)); }
    static void markSeen(String what, long current) {
        if (me == null) return;
        String k = seenKey(what), v = Long.toString(current);
        if (!v.equals(c(k, ""))) { cfg.setProperty(k, v); saveCfg(); }
    }
    static void markNavSeen(String pg) {
        if (pg.equals("Certs")) markSeen("certs", earnedCerts());
        else if (pg.equals("Bookings")) markSeen("bookings", pendingRequests());
    }
    /** Small circular counter used on sidebar items (unseen certificates, pending requests, unread messages). */
    static void addSidebarCount(RBtn b, long n) {
        if (n <= 0) return;
        String t = n > 99 ? "99+" : Long.toString(n);
        JLabel bd = new JLabel(t, SwingConstants.CENTER) { protected void paintComponent(Graphics g0) { Graphics2D g = (Graphics2D) g0.create(); aa(g); g.setColor(getBackground()); g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g.setColor(INK); g.setStroke(new BasicStroke(2f)); g.drawOval(1, 1, getWidth() - 3, getHeight() - 3); g.dispose(); super.paintComponent(g0); } };
        bd.setOpaque(false); bd.setBackground(PINK); bd.setForeground(ONACC); bd.setFont(f(Font.BOLD, 11));
        bd.setPreferredSize(new Dimension(Math.max(24, bd.getFontMetrics(bd.getFont()).stringWidth(t) + 10), 24));
        MouseAdapter click = new MouseAdapter() { public void mousePressed(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) b.doClick(); } };
        bd.addMouseListener(click); bd.setCursor(new Cursor(Cursor.HAND_CURSOR));
        JPanel wrap = new JPanel(new GridBagLayout()); wrap.setOpaque(false); wrap.add(bd); wrap.addMouseListener(click); wrap.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setLayout(new BorderLayout()); b.add(wrap, BorderLayout.EAST);
    }
    public static void buildSidebar() {
        if (me != null && me.moderator) { buildModeratorSidebar(); return; }
        sidebar.removeAll(); sidebar.setBorder(new CompoundBorder(new MatteBorder(0, 0, 0, CB, INK), new EmptyBorder(16, 12, 14, 12)));
        JPanel logo = new JPanel(new BorderLayout(10, 0)); logo.setOpaque(false); JPanel lw = new JPanel(new GridBagLayout()); lw.setOpaque(false); lw.add(logoMark(38));
        JPanel tx = vbox(); put(tx, lbl("Skillswap", Font.BOLD, 20, TXT)); put(tx, lbl("PEER LEARNING", Font.BOLD, 10, MUTED));
        logo.add(lw, BorderLayout.WEST); logo.add(tx); logo.setBorder(new EmptyBorder(0, 2, 18, 0)); logo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64)); put(sidebar, logo);
        for (String[] n : NAV) {
            if (n[0].equals("Moderation") && (me == null || !me.moderator)) continue;
            boolean on = n[0].equals(page);
            RBtn b = new RBtn(n[1], 0); b.flat(!on); b.setHorizontalAlignment(SwingConstants.LEFT); b.setFont(f(Font.BOLD, 13)); b.setForeground(on ? ONACC : TXT);
            b.setBackground(on ? YEL : PANEL); b.setBorder(new EmptyBorder(8, 12, 8, 12));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42)); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            if (n[0].equals("Certs")) {
                long unseen = unseen("certs", earnedCerts());
                if (unseen > 0) { addSidebarCount(b, unseen); b.setToolTipText(unseen + " new certificate" + (unseen == 1 ? "" : "s")); }
            }
            if (n[0].equals("Bookings")) {
                long unseen = unseen("bookings", pendingRequests());
                if (unseen > 0) { addSidebarCount(b, unseen); b.setToolTipText(unseen + " new swap request" + (unseen == 1 ? "" : "s")); }
            }
            if (n[0].equals("Messages")) {
                long unread = unreadCount();
                if (unread > 0) { addSidebarCount(b, unread); b.setToolTipText(unread + " unread message" + (unread == 1 ? "" : "s")); }
            }
            if (n[0].equals("Moderation")) {
                long open = S.reports.stream().filter(r -> !r.resolved).count();
                if (open > 0) b.setText(n[1] + "  (" + open + ")");
            }
            String t = n[0]; b.addActionListener(e -> show(t)); put(sidebar, b); gap(sidebar, 6);
        }
        sidebar.add(Box.createVerticalGlue());
        JButton out = ghost("Log Out", AppShell::logout); out.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42)); put(sidebar, out);
        sidebar.revalidate(); sidebar.repaint();
    }
    static void buildModeratorSidebar() {
        sidebar.removeAll(); sidebar.setBorder(new CompoundBorder(new MatteBorder(0, 0, 0, CB, INK), new EmptyBorder(16, 12, 14, 12)));
        JPanel logo = new JPanel(new BorderLayout(10, 0)); logo.setOpaque(false); JPanel lw = new JPanel(new GridBagLayout()); lw.setOpaque(false); lw.add(logoMark(38));
        JPanel tx = vbox(); put(tx, lbl("Skillswap", Font.BOLD, 20, TXT)); put(tx, lbl("MODERATOR CONSOLE", Font.BOLD, 10, MUTED));
        logo.add(lw, BorderLayout.WEST); logo.add(tx); logo.setBorder(new EmptyBorder(0, 2, 18, 0)); logo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64)); put(sidebar, logo);
        long open = S.reports.stream().filter(r -> !r.resolved).count(), banned = S.users.values().stream().filter(u -> u.banned).count();
        JPanel stats = card(); stats.setBorder(new EmptyBorder(8, 12, 8, 12)); JPanel si = vbox();
        put(si, lbl("REPORT QUEUE", Font.BOLD, 10, MUTED)); put(si, lbl(open + " open", Font.BOLD, 18, TXT));
        gap(si, 4); put(si, lbl(banned + " banned account" + (banned == 1 ? "" : "s"), Font.PLAIN, 11, MUTED));
        stats.add(si); stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, stats.getPreferredSize().height)); put(sidebar, stats); gap(sidebar, 14);
        RBtn b = new RBtn("Reports & Moderation", 0); b.setHorizontalAlignment(SwingConstants.LEFT); b.setFont(f(Font.BOLD, 13)); b.setForeground(ONACC);
        b.setBackground(YEL); b.setBorder(new EmptyBorder(8, 12, 8, 12)); b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42)); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (open > 0) b.setText("Reports & Moderation  (" + open + ")");
        b.addActionListener(e -> show("Moderation")); put(sidebar, b);
        sidebar.add(Box.createVerticalGlue());
        JButton out = ghost("Log Out", AppShell::logout); out.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42)); put(sidebar, out);
        sidebar.revalidate(); sidebar.repaint();
    }
    public static void buildRight() {
        rightPanel.removeAll(); rightPanel.setBorder(new EmptyBorder(16, 14, 16, 14));
        if (page.equals("Profile")) { buildRightProfile(); rightPanel.add(Box.createVerticalGlue()); rightPanel.revalidate(); rightPanel.repaint(); return; }
        put(rightPanel, lbl("TOP TUTORS", Font.BOLD, 14, TXT)); gap(rightPanel, 4);
        JPanel chips = row();
        for (String bd : new String[]{"Most Active", "Most Learned"}) chips.add(chip(bd, bd.equals(board), () -> { board = bd; buildRight(); }));
        put(rightPanel, chips);
        List<User> us = S.users.values().stream().filter(u -> !u.banned).collect(Collectors.toCollection(ArrayList::new));
        us.sort((x, y) -> board.equals("Most Active") ? Long.compare(taught(y), taught(x)) : Long.compare(learnedN(y), learnedN(x)));
        int rank = 1;
        for (User u : us.subList(0, Math.min(5, us.size()))) {
            JPanel r = card(); r.setBorder(new EmptyBorder(6, 8, 6, 4));
            JPanel av = avatarWrap(name(u), 36); r.add(av, BorderLayout.WEST);
            JPanel inf = vbox();
            JPanel nm = row(); nm.add(lbl(rank++ + ". " + name(u), Font.BOLD, 11, TXT)); if (verified(u)) nm.add(lbl("\u2713", Font.BOLD, 10, GREEN)); put(inf, nm);
            profileLink(av, u); profileLink(nm, u);
            put(inf, lbl(board.equals("Most Active") ? taught(u) + " taught" : learnedN(u) + " learned", Font.PLAIN, 10, MUTED));
            JPanel rt = row();
            if (u.publicRatings && !reviews(u).isEmpty()) { rt.add(new StarRating(12, true).value(avg(u))); rt.add(lbl(String.format("%.1f", avg(u)), Font.BOLD, 11, GOLD)); }
            else rt.add(lbl("No reviews yet", Font.PLAIN, 10, MUTED));
            put(inf, rt);
            r.add(inf, BorderLayout.CENTER);
            if (u != me && !u.teach.isEmpty()) {
                JButton bk = btn("Book", () -> book(u.username, me.username, u.teach.get(0)));
                bk.setFont(f(Font.BOLD, 11)); bk.setBorder(new EmptyBorder(4, 7, 4, 7));
                JPanel be = new JPanel(new GridBagLayout()); be.setOpaque(false); be.add(bk); r.add(be, BorderLayout.EAST);
            } else {
                JButton vw = ghost("View", () -> profileDialog(u));
                vw.setFont(f(Font.BOLD, 11)); vw.setBorder(new EmptyBorder(4, 7, 4, 7));
                JPanel be = new JPanel(new GridBagLayout()); be.setOpaque(false); be.add(vw); r.add(be, BorderLayout.EAST);
            }
            put(rightPanel, r); gap(rightPanel, 5);
        }
        gap(rightPanel, 14); put(rightPanel, lbl("CAMPUS BADGES", Font.BOLD, 14, TXT)); gap(rightPanel, 6);
        JPanel cr = card(); cr.setBorder(new EmptyBorder(6, 8, 6, 8)); JPanel ci = vbox();
        put(ci, lbl("Swap credits: " + me.credits + " / " + MAX_CREDITS, Font.BOLD, 12, TXT));
        gap(ci, 4); put(ci, bar(0, MAX_CREDITS, me.credits, PINK)); gap(ci, 4);
        put(ci, lbl("Learning costs 1, teaching earns 1.", Font.PLAIN, 10, MUTED)); cr.add(ci); put(rightPanel, cr); gap(rightPanel, 5);
        for (String[] b : badges(me)) if (b[2].equals("Earned")) { JPanel br = card(); br.setBorder(new EmptyBorder(6, 8, 6, 8)); br.add(lbl("\u2611 " + b[0], Font.BOLD, 12, TXT)); put(rightPanel, br); gap(rightPanel, 4); }
        rightPanel.add(Box.createVerticalGlue()); rightPanel.revalidate(); rightPanel.repaint();
    }
    public static void buildRightProfile() {
        put(rightPanel, lbl("MY RATING", Font.BOLD, 14, TXT)); gap(rightPanel, 6); List<Sess> rs = reviews(me);
        JPanel big = row(); big.add(lbl(rs.isEmpty() ? "-" : String.format("%.1f", avg(me)), Font.BOLD, 34, TXT));
        JPanel st2 = vbox(); st2.add(new StarRating(18, true).value(avg(me)));
        st2.add(lbl(rs.isEmpty() ? "no reviews yet" : rs.size() + (rs.size() == 1 ? " review" : " reviews"), Font.PLAIN, 10, MUTED)); big.add(st2); put(rightPanel, big);
        if (!me.publicRatings) put(rightPanel, lbl("Private - only you can see this", Font.PLAIN, 10, MUTED));
        for (int st = 5; st >= 1; st--) { final int s2 = st; long n = rs.stream().filter(x -> x.rating == s2).count(); JPanel r = new JPanel(new BorderLayout(8, 0)) { public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 22); } }; r.setOpaque(false);
            JLabel sl = lbl(st + " \u2605", Font.BOLD, 11, GOLD); sl.setPreferredSize(new Dimension(32, 16)); r.add(sl, BorderLayout.WEST); JProgressBar pb = bar(0, Math.max(1, rs.size()), (int) n, YEL);
            r.add(pb); r.add(lbl("" + n, Font.PLAIN, 11, MUTED), BorderLayout.EAST); put(rightPanel, r); gap(rightPanel, 3); }
        gap(rightPanel, 14); put(rightPanel, lbl("MY BADGES", Font.BOLD, 14, TXT)); gap(rightPanel, 6);
        for (String[] b : badges(me)) { JPanel br = card(); br.setBorder(new EmptyBorder(6, 8, 6, 8)); boolean ok = b[2].equals("Earned"); br.add(lbl((ok ? "\u2605 " : "\u2606 ") + b[0], Font.BOLD, 12, ok ? TXT : MUTED)); put(rightPanel, br); gap(rightPanel, 4); }
        gap(rightPanel, 8); JButton sh = btn("Share Profile", AppShell::shareProfile); sh.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38)); put(rightPanel, sh);
    }
    public static void shareProfile() {
        List<Sess> rs = reviews(me);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("SkillSwap Campus - " + name(me) + " | Teaches: " + String.join(", ", me.teach) + " | Wants: " + String.join(", ", me.learn)
                + (me.publicRatings && !rs.isEmpty() ? " | Rating " + String.format("%.1f", avg(me)) : "")), null);
        toast("Profile summary copied to clipboard");
    }
}
