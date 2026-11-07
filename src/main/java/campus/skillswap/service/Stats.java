package campus.skillswap.service;

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
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Ratings, badges, matching engine and other derived values. */
public class Stats {

    public static Sess sess(int id) { return S.sessions.stream().filter(x -> x.id == id).findFirst().orElse(null); }
    // ---- derived stats ----
    public static List<Sess> reviews(User u) {
        return S.sessions.stream().filter(s -> s.status.equals("COMPLETED") && s.teacher.equals(u.username) && s.rating > 0).collect(Collectors.toList());
    }
    public static double avg(User u) { return reviews(u).stream().mapToInt(s -> s.rating).average().orElse(0); }
    public static boolean verified(User u) { return reviews(u).size() >= 3 && avg(u) >= 4.5; }
    public static long taught(User u) { return S.sessions.stream().filter(s -> s.status.equals("COMPLETED") && s.teacher.equals(u.username)).count(); }
    public static long learnedN(User u) { return S.sessions.stream().filter(s -> s.status.equals("COMPLETED") && s.learner.equals(u.username)).count(); }
    public static String name(User u) { return u.useRealName && !u.realName.isEmpty() ? u.realName : u.username; }
    public static String name(String un) { User u = S.users.get(un); return u == null ? un : name(u); }
    public static String stars(double a) { int n = (int) Math.round(a); return "\u2605".repeat(n) + "\u2606".repeat(5 - n); }
    public static List<String> inter(List<String> a, List<String> b) {
        return a.stream().filter(x -> b.stream().anyMatch(y -> y.equalsIgnoreCase(x))).collect(Collectors.toList());
    }
    public static List<Match> matches() {
        List<Match> r = new ArrayList<>();
        for (User u : S.users.values()) {
            if (u == me) continue;
            Match m = new Match(); m.u = u; m.theyTeach = inter(u.teach, me.learn); m.iTeach = inter(me.teach, u.learn);
            if (!m.theyTeach.isEmpty()) r.add(m);
        }
        r.sort((x, y) -> y.iTeach.size() - x.iTeach.size());
        return r;
    }
    public static List<String[]> badges(User u) {
        List<String[]> b = new ArrayList<>(); long t = taught(u), l = learnedN(u);
        b.add(new String[]{"Peer Tutor Level I", "Teach 1 completed session", t >= 1 ? "Earned" : "Locked", t + "/1 sessions"});
        b.add(new String[]{"Peer Tutor Level II", "Teach 5 completed sessions", t >= 5 ? "Earned" : t >= 1 ? "In progress" : "Locked", t + "/5 sessions"});
        b.add(new String[]{"Top Rated Tutor", "3+ reviews with a 4.5+ average", verified(u) ? "Earned" : reviews(u).size() > 0 ? "In progress" : "Locked",
                reviews(u).size() + "/3 reviews, avg " + String.format("%.1f", avg(u))});
        b.add(new String[]{"Curious Learner", "Learn from 3 different sessions", l >= 3 ? "Earned" : l >= 1 ? "In progress" : "Locked", l + "/3 sessions"});
        b.add(new String[]{"Skill Collector", "List 3 or more skills you teach", u.teach.size() >= 3 ? "Earned" : u.teach.size() > 0 ? "In progress" : "Locked", u.teach.size() + "/3 skills"});
        return b;
    }
    public static boolean involves(Sess s, User u) { return s.teacher.equals(u.username) || s.learner.equals(u.username); }
    public static String other(Sess s) { return s.teacher.equals(me.username) ? s.learner : s.teacher; }
}
