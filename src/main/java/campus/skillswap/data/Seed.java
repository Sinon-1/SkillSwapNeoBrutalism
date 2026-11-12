package campus.skillswap.data;

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
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Demo accounts, posts and reviews created the first time a database is empty. */
public class Seed {

    // ---- seed data ----
    public static User addUser(String un, String real, String pw, String[] teach, String[] learn, boolean nearby, String bio) {
        User u = new User(); u.username = un; u.realName = real; u.useRealName = true; u.email = un + "@school.edu";
        u.passHash = hash(un, pw); u.teach.addAll(Arrays.asList(teach)); u.learn.addAll(Arrays.asList(learn));
        u.nearby = nearby; u.demo = true; u.bio = bio; S.users.put(un, u); return u;
    }
    public static void rated(String t, String l, String skill, int r, String review) {
        Sess s = new Sess(); s.id = nextId(); s.teacher = t; s.learner = l; s.skill = skill; s.mode = "Online";
        s.status = "COMPLETED"; s.requester = l; s.rating = r; s.review = review; s.at = LocalDateTime.now().minusDays(5 + s.id).truncatedTo(ChronoUnit.SECONDS); S.sessions.add(s);
    }
    public static void post(String a, String type, String skill, String desc) {
        Post p = new Post(); p.id = nextId(); p.author = a; p.type = type; p.skill = skill; p.category = cat(skill);
        p.desc = desc; p.time = System.currentTimeMillis() - (long) (Math.random() * 6e6); for (int i = 0; i < (int) (Math.random() * 7); i++) p.likes.add("seed" + i); S.posts.add(p);
    }
    public static void seed() {
        addUser("ramos", "Dr. Ramos", "demo", new String[]{"Java", "Python", "Calculus"}, new String[]{"Guitar"}, false, "Senior Programmer Major");
        addUser("alamag", "Dr. Alamag", "demo", new String[]{"Guitar", "Piano"}, new String[]{"Python"}, false, "Conservatory Junior");
        addUser("henry", "Dr. Henry Flores", "demo", new String[]{"Statistics", "Russian"}, new String[]{"Painting"}, false, "Math & Linguistics");
        addUser("jeb", "Dr. Jeb Ramos", "demo", new String[]{"Painting", "Cooking"}, new String[]{"Java"}, false, "Fine Arts Sophomore");
        addUser("chinu", "Dr. Chinu Po", "demo", new String[]{"Calculus", "Physics"}, new String[]{"Russian"}, false, "Physics Senior");
        addUser("dino", "Dr. Dino Ran", "demo", new String[]{"Python", "Guitar"}, new String[]{"Calculus"}, false, "CS Junior");
        addUser("jamesa", "James A.", "demo", new String[]{"Spanish"}, new String[]{"Java"}, false, "");
        addUser("kylet", "Kyle T.", "demo", new String[]{"Drawing"}, new String[]{"Python"}, false, "");
        String[] rev = {"Super patient and clear.", "Explained recursion perfectly.", "Great session, very organized.", "Would swap again!"};
        rated("ramos", "jamesa", "Java", 5, rev[0]); rated("ramos", "kylet", "Python", 5, rev[1]);
        rated("ramos", "alamag", "Calculus", 4, rev[2]); rated("ramos", "henry", "Java", 5, rev[3]);
        rated("alamag", "dino", "Guitar", 5, rev[0]); rated("alamag", "jeb", "Piano", 4, rev[2]); rated("alamag", "kylet", "Guitar", 5, rev[3]);
        rated("henry", "chinu", "Statistics", 4, rev[2]); rated("henry", "jeb", "Russian", 4, rev[0]);
        rated("jeb", "ramos", "Painting", 5, rev[1]);
        rated("chinu", "dino", "Calculus", 4, rev[0]); rated("chinu", "ramos", "Physics", 5, rev[3]); rated("chinu", "henry", "Physics", 4, rev[2]);
        rated("dino", "ramos", "Guitar", 5, rev[1]); rated("dino", "jeb", "Python", 5, rev[0]); rated("dino", "alamag", "Python", 5, rev[3]);
        post("ramos", "OFFER", "Java", "Senior programmer major. Happy to teach OOP, data structures and debugging. Looking for a guitar teacher!");
        post("alamag", "OFFER", "Guitar", "Conservatory junior - beginner-friendly guitar lessons, any genre.");
        post("henry", "REQUEST", "Painting", "I can teach Russian or Statistics in return for basic watercolor lessons.");
        post("chinu", "OFFER", "Calculus", "Exam season is coming - calculus I & II help, problem-set walkthroughs.");
        post("jeb", "REQUEST", "Java", "Art student who wants to learn Java. I can teach painting and cooking!");
        post("dino", "OFFER", "Python", "Python from zero: scripts, pandas, small games.");
    }
}
