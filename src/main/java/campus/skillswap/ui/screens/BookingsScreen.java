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
        JComboBox<String> day = combo(new String[7]), time = combo(new String[10]), mode = combo(new String[]{"Online", "In person"}); List<LocalDate> days = new ArrayList<>();
        DefaultComboBoxModel<String> dm = new DefaultComboBoxModel<>(); for (int i = 0; i < 7; i++) { LocalDate d = LocalDate.now().plusDays(i); days.add(d); dm.addElement(d.format(DateTimeFormatter.ofPattern("EEE, MMM d"))); } day.setModel(dm);
        DefaultComboBoxModel<String> tm = new DefaultComboBoxModel<>(); for (int h = 9; h < 19; h++) tm.addElement(LocalTime.of(h, 0).format(DateTimeFormatter.ofPattern("h:mm a"))); time.setModel(tm);
        if (!ask("Book: " + skill + " (" + name(teacher) + " teaching, " + name(learner) + " learning)", form(new String[]{"Day", "Time", "Mode"}, new JComponent[]{day, time, mode}))) return;
        Sess s = new Sess(); s.id = nextId(); s.teacher = teacher; s.learner = learner; s.skill = skill; s.mode = String.valueOf(mode.getSelectedItem()); s.status = "PENDING"; s.requester = me.username;
        s.at = LocalDateTime.of(days.get(day.getSelectedIndex()), LocalTime.of(9 + time.getSelectedIndex(), 0)); S.sessions.add(s); save();
        String o = teacher.equals(me.username) ? learner : teacher; toast("Request sent to " + name(o));
        bookTab = "Upcoming"; show("Bookings");
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
        Pg p = pg("Bookings & Sessions", "Manage your tutoring and skill-swap sessions", btn("+ New Booking", () -> {
            List<String> opts = new ArrayList<>(); for (User u : S.users.values()) if (u != me) for (String s : u.teach) opts.add(name(u) + " - " + s + "|" + u.username + "|" + s);
            if (opts.isEmpty()) return; JComboBox<String> cb = combo(opts.stream().map(x -> x.split("\\|")[0]).toArray(String[]::new));
            if (ask("Choose a tutor", form(new String[]{"Tutor & skill"}, new JComponent[]{cb}))) { String[] x = opts.get(cb.getSelectedIndex()).split("\\|"); book(x[1], me.username, x[2]); } }));
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
        h.add(lbl(name(o), Font.BOLD, 13, TXT)); h.add(tag(s.status, s.status.equals("CONFIRMED") || s.status.equals("COMPLETED") ? GREEN : s.status.equals("PENDING") ? GOLD : MUTED)); h.add(tag(iLearn ? "YOU LEARN" : "YOU TEACH", BLUE)); put(inf, h);
        put(inf, lbl(s.skill + " \u2022 1-on-1 \u2022 " + s.at.format(DF) + " \u2022 " + s.mode, Font.PLAIN, 12, MUTED));
        if (s.status.equals("COMPLETED") && iLearn) put(inf, lbl(s.rating > 0 ? "Your rating: " + stars(s.rating) : "Not rated yet", Font.PLAIN, 11, GOLD));
        c.add(inf); JPanel b = row(); b.setPreferredSize(new Dimension(230, 60));
        switch (s.status) {
            case "PENDING":
                if (s.requester.equals(me.username)) b.add(ghost("Cancel request", () -> { s.status = "CANCELLED"; save(); show("Bookings"); }));
                else { b.add(btn("Accept", () -> { if (confirm(s)) notifyUser("Swap accepted", "Session with " + name(o) + " confirmed"); else info(name(s.learner) + " has no swap credits left."); show("Bookings"); }));
                    b.add(ghost("Decline", () -> { s.status = "DECLINED"; save(); show("Bookings"); })); } break;
            case "CONFIRMED":
                b.add(btn("Join", () -> info("Session room: SS-" + (1000 + s.id) + "\n" + (s.mode.equals("Online") ? "Open the campus meeting room with this code." : "Meet in person at the agreed campus spot."))));
                b.add(ghost("Complete", () -> complete(s))); b.add(ghost("Reschedule", () -> reschedule(s)));
                b.add(ghost("Cancel", () -> { User l = S.users.get(s.learner); l.credits = Math.min(MAX_CREDITS, l.credits + 1); s.status = "CANCELLED"; save(); show("Bookings"); })); break;
            case "COMPLETED": if (iLearn && s.rating == 0) b.add(btn("Rate", () -> { rate(s); show("Bookings"); })); break;
            default: }
        b.add(ghost("\u2691", () -> report(o))); c.add(b, BorderLayout.EAST); return c;
    }
    public static void reschedule(Sess s) {
        JComboBox<String> day = combo(new String[0]), time = combo(new String[0]); List<LocalDate> days = new ArrayList<>(); DefaultComboBoxModel<String> dm = new DefaultComboBoxModel<>();
        for (int i = 0; i < 7; i++) { LocalDate d = LocalDate.now().plusDays(i); days.add(d); dm.addElement(d.format(DateTimeFormatter.ofPattern("EEE, MMM d"))); } day.setModel(dm);
        DefaultComboBoxModel<String> tm = new DefaultComboBoxModel<>(); for (int h = 9; h < 19; h++) tm.addElement(LocalTime.of(h, 0).format(DateTimeFormatter.ofPattern("h:mm a"))); time.setModel(tm);
        if (!ask("Reschedule " + s.skill, form(new String[]{"New day", "New time"}, new JComponent[]{day, time}))) return;
        s.at = LocalDateTime.of(days.get(day.getSelectedIndex()), LocalTime.of(9 + time.getSelectedIndex(), 0)); remindedIds.remove(s.id); save(); toast("Session rescheduled"); show("Bookings");
    }
}
