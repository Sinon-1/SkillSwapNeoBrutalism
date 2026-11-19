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
import static campus.skillswap.ui.screens.NearbyScreen.*;
import static campus.skillswap.ui.screens.MessagesScreen.*;
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Booking, accepting, rescheduling, completing and rating sessions. */
public class BookingsScreen {

    // ====================== BOOKINGS ======================
    public static void book(String teacher, String learner, String skill) {
        if (learner.equals(me.username) && me.credits < 1) { info("You need at least 1 swap credit to request a session.\nTeach a session to earn credits!"); return; }
        JComboBox<String> day = combo(new String[7]), time = combo(new String[10]), mode = combo(new String[]{"In person", "Offline", "Online"}); List<LocalDate> days = new ArrayList<>();
        DefaultComboBoxModel<String> dm = new DefaultComboBoxModel<>(); for (int i = 0; i < 7; i++) { LocalDate d = LocalDate.now().plusDays(i); days.add(d); dm.addElement(d.format(DateTimeFormatter.ofPattern("EEE, MMM d"))); } day.setModel(dm);
        DefaultComboBoxModel<String> tm = new DefaultComboBoxModel<>(); for (int h = 9; h < 19; h++) tm.addElement(LocalTime.of(h, 0).format(DateTimeFormatter.ofPattern("h:mm a"))); time.setModel(tm);
        String tutorOrOther = name(teacher.equals(me.username) ? learner : teacher);
        JCheckBox vid = check("Request a video call with " + tutorOrOther, false); vid.setEnabled(false);
        JLabel hint = lbl(modeHint("In person"), Font.PLAIN, 11, MUTED);
        boolean iLearn = learner.equals(me.username);
        mode.addActionListener(e -> {
            String m = String.valueOf(mode.getSelectedItem()); boolean on = "Online".equals(m);
            vid.setEnabled(on); vid.setSelected(on); hint.setText(modeHint(m));
        });
        JPanel head = new JPanel(new BorderLayout(10, 0)); head.setBackground(PANEL2);
        head.setBorder(new CompoundBorder(new LineBorder(INK, BW), new EmptyBorder(10, 12, 10, 12)));
        JPanel hi = vbox(); put(hi, lbl(skill, Font.BOLD, 17, TXT));
        put(hi, lbl(iLearn ? "Session with " + tutorOrOther : "You teach " + tutorOrOther, Font.PLAIN, 12, MUTED));
        head.add(hi, BorderLayout.CENTER); head.add(tag(iLearn ? "YOU LEARN" : "YOU TEACH", iLearn ? BLUE : GREEN), BorderLayout.EAST);
        JPanel when = new JPanel(new GridLayout(1, 2, 10, 0)); when.setOpaque(false);
        when.add(fieldCol("Day", day)); when.add(fieldCol("Time", time));
        JPanel f = vbox(); f.setBackground(PANEL);
        put(f, head); gap(f, 12); put(f, when); gap(f, 10);
        put(f, fieldCol("Mode", mode)); gap(f, 8); put(f, vid); gap(f, 4); put(f, hint);
        f.setPreferredSize(new Dimension(440, f.getPreferredSize().height));
        if (!ask("Book " + skill, f)) return;
        Sess s = new Sess(); s.id = nextId(); s.teacher = teacher; s.learner = learner; s.skill = skill; s.mode = String.valueOf(mode.getSelectedItem()); s.status = "PENDING"; s.requester = me.username; s.videoCall = "Online".equals(s.mode) && vid.isSelected();
        s.at = LocalDateTime.of(days.get(day.getSelectedIndex()), LocalTime.of(9 + time.getSelectedIndex(), 0)); S.sessions.add(s); save();
        String o = teacher.equals(me.username) ? learner : teacher; toast("Request sent to " + name(o) + (s.videoCall ? " (video call requested)" : ""));
        bookTab = "Upcoming"; show("Bookings");
    }
    /** Label above an input in the booking dialog. */
    static JPanel fieldCol(String label, JComponent comp) {
        JPanel c = vbox(); c.setOpaque(false);
        put(c, lbl(label, Font.BOLD, 12, TXT)); gap(c, 3);
        comp.setMaximumSize(new Dimension(Integer.MAX_VALUE, comp.getPreferredSize().height));
        put(c, comp);
        return c;
    }
    static String modeHint(String m) {
        String body = m.equals("Online") ? "Hold the session as an in-app video call. The other person must accept the request first."
                : m.equals("Offline") ? "No live meeting: arrange the details in Messages."
                : "Meet on campus at the agreed spot; use Messages to settle where.";
        return "<html><body style='width:400px'>" + body + "</body></html>";
    }
    static boolean bookingMatch(User u, String q) {
        if (q == null || q.trim().isEmpty()) return true;
        String text = q.toLowerCase().trim(), hay = (name(u) + " " + u.username + " " + String.join(" ", u.teach)).toLowerCase();
        return hay.contains(text);
    }
    /** "+ New Booking": search the person you want to request, then pick the skill they should teach you. */
    public static void newBooking() {
        List<User> tutors = S.users.values().stream().filter(u -> u != me && !u.banned && !u.teach.isEmpty())
                .sorted(Comparator.comparing(u -> name(u).toLowerCase())).collect(Collectors.toList());
        if (tutors.isEmpty()) { info("No one is offering a skill to book yet."); return; }
        while (true) {
            JTextField q = searchField("Search by name or username...");
            PickList results = new PickList();
            JScrollPane scroll = new JScrollPane(results, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(new LineBorder(INK, BW)); scroll.getViewport().setBackground(PANEL); scroll.getVerticalScrollBar().setUnitIncrement(18);
            scroll.setPreferredSize(new Dimension(480, 228));
            JComboBox<String> skill = combo(new String[0]); skill.setEnabled(false);
            JLabel picked = lbl("Pick a person above, then choose what they should teach you.", Font.PLAIN, 11, MUTED);
            final User[] sel = new User[1]; final Runnable[] refresh = new Runnable[1];
            final Runnable summary = () -> picked.setText(sel[0] == null
                    ? "Pick a person above, then choose what they should teach you."
                    : "<html><body style='width:470px'><b>" + esc(name(sel[0])) + "</b> can teach <b>" + esc(String.valueOf(skill.getSelectedItem())) + "</b>.</body></html>");
            final java.util.function.Consumer<User> choose = u -> {
                sel[0] = u; skill.setModel(new DefaultComboBoxModel<>(u.teach.toArray(new String[0])));
                skill.setEnabled(!u.teach.isEmpty()); if (skill.getItemCount() > 0) skill.setSelectedIndex(0);
                summary.run(); refresh[0].run();
            };
            refresh[0] = () -> {
                String text = q.getText().trim();
                if (sel[0] != null && !bookingMatch(sel[0], text)) { sel[0] = null; skill.setModel(new DefaultComboBoxModel<>()); skill.setEnabled(false); summary.run(); }
                results.removeAll(); int n = 0;
                for (User u : tutors) { if (!bookingMatch(u, text)) continue; results.add(personRow(u, u == sel[0], () -> choose.accept(u))); gap(results, 6); n++; }
                if (n == 0) { JLabel none = lbl("No account matches \"" + text + "\".", Font.PLAIN, 12, MUTED); none.setBorder(new EmptyBorder(18, 12, 18, 12)); results.add(none); }
                results.revalidate(); results.repaint();
            };
            q.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh[0].run(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh[0].run(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh[0].run(); }
            });
            skill.addActionListener(e -> { if (sel[0] != null) summary.run(); });
            refresh[0].run();

            JPanel top = vbox(); top.setOpaque(false);
            put(top, lbl("Who do you want to book?", Font.BOLD, 18, TXT)); gap(top, 2);
            put(top, lbl("Search by display name or username, pick the person, then choose the skill.", Font.PLAIN, 11, MUTED)); gap(top, 10); put(top, q);
            JPanel foot = vbox(); foot.setOpaque(false); put(foot, fieldCol("Skill to learn", skill)); gap(foot, 8); put(foot, picked);
            JPanel body = new JPanel(new BorderLayout(0, 10)); body.setOpaque(false);
            body.add(top, BorderLayout.NORTH); body.add(scroll, BorderLayout.CENTER); body.add(foot, BorderLayout.SOUTH);
            body.setPreferredSize(new Dimension(520, 450));
            if (!ask("New booking", body)) return;
            if (sel[0] == null || skill.getSelectedItem() == null) { info("Choose a person and a skill first."); continue; }
            book(sel[0].username, me.username, String.valueOf(skill.getSelectedItem()));
            return;
        }
    }
    /** Result row in the "New booking" search: click anywhere on it to select that person. */
    static JComponent personRow(User u, boolean selected, Runnable onClick) {
        Color bg = selected ? pastel(YEL) : PANEL2, fg = selected ? ONACC : TXT, sub = selected ? ONACC : MUTED;
        JPanel row = new JPanel(new BorderLayout(10, 0)); row.setOpaque(true); row.setBackground(bg);
        row.setBorder(new CompoundBorder(new LineBorder(INK, BW), new EmptyBorder(7, 10, 7, 10)));
        row.add(avatarWrap(name(u), 34), BorderLayout.WEST);
        JPanel inf = vbox(); JPanel h = row();
        h.add(lbl(name(u), Font.BOLD, 13, fg)); h.add(lbl("@" + u.username, Font.PLAIN, 11, sub)); if (verified(u)) h.add(tag("VERIFIED", GREEN));
        put(inf, h); put(inf, lbl(String.join("  \u2022  ", u.teach), Font.PLAIN, 11, sub));
        row.add(inf, BorderLayout.CENTER);
        row.add(lbl(selected ? "SELECTED" : "\u2192", Font.BOLD, 11, selected ? ONACC : BLUE), BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(58, row.getPreferredSize().height)));
        clickAll(row, onClick);
        return row;
    }
    static void clickAll(Component c, Runnable r) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { r.run(); } });
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) clickAll(k, r);
    }
    /** A BoxLayout column that always fits the scroll pane's width (rows have a fixed height). */
    static class PickList extends JPanel implements Scrollable {
        PickList() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 20; }
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 100; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    public static boolean confirm(Sess s) {
        User l = S.users.get(s.learner); if (l.credits < 1) return false; l.credits--; s.status = "CONFIRMED"; save(); return true;
    }
    public static void rate(Sess s) {
        JComboBox<String> r = combo(new String[]{"5 - Excellent", "4 - Good", "3 - OK", "2 - Poor", "1 - Bad"}); JTextArea t = area(3, 22);
        if (!ask("Rate your session with " + name(s.teacher), form(new String[]{"Rating", "Feedback"}, new JComponent[]{r, scroller(t)}))) return;
        s.rating = 5 - r.getSelectedIndex(); s.review = t.getText().trim(); save(); toast("Thanks for your feedback!");
    }
    public static void complete(Sess s) {
        s.status = "COMPLETED"; User t = S.users.get(s.teacher); t.credits = Math.min(MAX_CREDITS, t.credits + 1); save();
        if (s.learner.equals(me.username)) rate(s); else toast("Session logged. +1 credit earned!"); show("Bookings");
    }
    public static JComponent bookingsPage() {
        Pg p = pg("Bookings & Sessions", "Manage your tutoring and skill-swap sessions", btn("+ New Booking", BookingsScreen::newBooking));
        JPanel days = row(); LocalDate td = LocalDate.now();
        for (int i = 0; i < 7; i++) { LocalDate d = td.plusDays(i); long n = S.sessions.stream().filter(s -> s.status.equals("CONFIRMED") && involves(s, me) && s.at.toLocalDate().equals(d)).count(); boolean on = d.equals(dayFilter);
            days.add(chip(d.format(DateTimeFormatter.ofPattern("EEE d")) + (n > 0 ? " \u2022" + n : ""), on, () -> { dayFilter = on ? null : d; show("Bookings"); })); }
        put(p.body, days);
        List<Sess> mine = S.sessions.stream().filter(s -> involves(s, me) && (dayFilter == null || s.at.toLocalDate().equals(dayFilter))).sorted(Comparator.comparing((Sess s) -> s.at)).collect(Collectors.toList());
        List<Sess> inc = mine.stream().filter(s -> s.status.equals("PENDING") && !s.requester.equals(me.username)).collect(Collectors.toList());
        long up = mine.stream().filter(s -> s.status.equals("CONFIRMED") || (s.status.equals("PENDING") && s.requester.equals(me.username))).count();
        JPanel chips = row(); for (String t : new String[]{"Upcoming", "Past", "Requests"}) chips.add(chip(t + (t.equals("Requests") ? " (" + inc.size() + ")" : t.equals("Upcoming") ? " (" + up + ")" : ""), t.equals(bookTab), () -> { bookTab = t; show("Bookings"); })); put(p.body, chips); gap(p.body, 6);
        List<Sess> shown = mine.stream().filter(s -> bookTab.equals("Upcoming") ? (s.status.equals("CONFIRMED") || (s.status.equals("PENDING") && s.requester.equals(me.username)))
                : bookTab.equals("Past") ? (s.status.equals("COMPLETED") || s.status.equals("CANCELLED") || s.status.equals("DECLINED")) : inc.contains(s)).collect(Collectors.toList());
        if (bookTab.equals("Past")) Collections.reverse(shown);
        if (shown.isEmpty()) put(p.body, lbl("Nothing here yet. Book a tutor from the Skill Feed or Search Topics.", Font.PLAIN, 12, MUTED));
        for (Sess s : shown) { put(p.body, sessionCard(s)); gap(p.body, 8); }
        return p.root;
    }
    public static JComponent sessionCard(Sess s) {
        String o = other(s); boolean iLearn = s.learner.equals(me.username);
        JPanel c = card(); c.add(avatarWrap(name(o), 40), BorderLayout.WEST); JPanel inf = vbox(); JPanel h = row();
        h.add(lbl(name(o), Font.BOLD, 13, TXT)); h.add(tag(s.status, s.status.equals("CONFIRMED") || s.status.equals("COMPLETED") ? GREEN : s.status.equals("PENDING") ? GOLD : MUTED)); h.add(tag(iLearn ? "YOU LEARN" : "YOU TEACH", BLUE)); if (s.videoCall) h.add(tag("VIDEO CALL", GOLD)); put(inf, h);
        put(inf, lbl(s.skill + " \u2022 1-on-1 \u2022 " + s.at.format(DF) + " \u2022 " + s.mode + (s.videoCall ? " (video call" + (s.status.equals("PENDING") && !s.requester.equals(me.username) ? " requested" : "") + ")" : ""), Font.PLAIN, 12, MUTED));
        if (s.status.equals("COMPLETED") && iLearn) put(inf, lbl(s.rating > 0 ? "Your rating: " + stars(s.rating) : "Not rated yet", Font.PLAIN, 11, GOLD));
        c.add(inf); JPanel b = new JPanel(new GridLayout(0, 3, 6, 6)); b.setOpaque(false);
        switch (s.status) {
            case "PENDING":
                if (s.requester.equals(me.username)) b.add(ghost("Cancel request", () -> { s.status = "CANCELLED"; save(); show("Bookings"); }));
                else { b.add(btn("Accept", () -> { if (confirm(s)) notifyUser("Swap accepted", "Session with " + name(o) + " confirmed"); else info(name(s.learner) + " has no swap credits left."); show("Bookings"); }));
                    b.add(ghost("Decline", () -> { s.status = "DECLINED"; save(); show("Bookings"); })); } break;
            case "CONFIRMED":
                b.add(btn(joinLabel(s), () -> join(s)));
                b.add(ghost("Complete", () -> complete(s))); b.add(ghost("Reschedule", () -> reschedule(s)));
                b.add(ghost("Cancel", () -> { User l = S.users.get(s.learner); l.credits = Math.min(MAX_CREDITS, l.credits + 1); s.status = "CANCELLED"; save(); show("Bookings"); })); break;
            case "COMPLETED": if (iLearn && s.rating == 0) b.add(btn("Rate", () -> { rate(s); show("Bookings"); })); break;
            default: }
        b.add(ghost("\u2691", () -> report(o))); JPanel bw = new JPanel(new BorderLayout()); bw.setOpaque(false); bw.add(b, BorderLayout.NORTH); c.add(bw, BorderLayout.EAST); return c;
    }
    /** Button text for a confirmed session, depending on how it is held. */
    public static String joinLabel(Sess s) { return s.mode.equals("Online") ? (s.videoCall ? "Video call" : "Join") : s.mode.equals("Offline") ? "Message" : "Details"; }
    /** Online + video call: rings the other person right now. Online: meeting-room code. Offline: opens the chat. In person: meeting details. */
    public static void join(Sess s) {
        String o = other(s);
        if (s.mode.equals("Online") && s.videoCall) { campus.skillswap.service.Calls.start(o, true); return; }
        if (s.mode.equals("Online")) { info("Session room: SS-" + (1000 + s.id) + "\nOpen the campus meeting room with this code."); return; }
        if (s.mode.equals("Offline")) { chatWith = o; draft = ""; show("Messages"); toast("Arrange the session with " + name(o) + " here"); return; }
        info("In-person session with " + name(o) + "\n" + s.at.format(DF) + "\nMeet at the agreed campus spot (use Messages to settle where).");
    }
    public static void reschedule(Sess s) {
        JComboBox<String> day = combo(new String[0]), time = combo(new String[0]); List<LocalDate> days = new ArrayList<>(); DefaultComboBoxModel<String> dm = new DefaultComboBoxModel<>();
        for (int i = 0; i < 7; i++) { LocalDate d = LocalDate.now().plusDays(i); days.add(d); dm.addElement(d.format(DateTimeFormatter.ofPattern("EEE, MMM d"))); } day.setModel(dm);
        DefaultComboBoxModel<String> tm = new DefaultComboBoxModel<>(); for (int h = 9; h < 19; h++) tm.addElement(LocalTime.of(h, 0).format(DateTimeFormatter.ofPattern("h:mm a"))); time.setModel(tm);
        if (!ask("Reschedule " + s.skill, form(new String[]{"New day", "New time"}, new JComponent[]{day, time}))) return;
        s.at = LocalDateTime.of(days.get(day.getSelectedIndex()), LocalTime.of(9 + time.getSelectedIndex(), 0)); remindedIds.remove(s.id); save(); toast("Session rescheduled"); show("Bookings");
    }
}
