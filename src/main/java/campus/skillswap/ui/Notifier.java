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
import static campus.skillswap.ui.AppShell.*;
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

/** Toasts, system-tray notifications and session reminders. */
public class Notifier {

    public static void notifyUser(String title, String msg) {
        try {
            if (SystemTray.isSupported()) {
                if (tray == null) { BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); Graphics2D g = im.createGraphics();
                    g.setColor(PINK); g.fillOval(0, 0, 15, 15); g.dispose(); tray = new TrayIcon(im, "SkillSwap Campus"); SystemTray.getSystemTray().add(tray); }
                tray.displayMessage(title, msg, TrayIcon.MessageType.INFO);
            }
        } catch (Exception e) { /* tray unavailable */ }
        toast(title + " - " + msg);
    }
    public static void toast(String t) {
        if (frame == null || !frame.isShowing()) return;
        JWindow w = new JWindow(frame); JPanel p = new JPanel(new BorderLayout()); p.setBackground(YEL);
        p.setBorder(new CompoundBorder(new LineBorder(Color.BLACK, 3), new EmptyBorder(10, 14, 10, 14))); p.add(lbl("<html><body style='width:240px'>" + esc(t) + "</body></html>", Font.BOLD, 12, ONACC));
        w.setContentPane(p); w.pack(); Point o = frame.getLocationOnScreen();
        w.setLocation(o.x + frame.getWidth() - w.getWidth() - 20, o.y + frame.getHeight() - w.getHeight() - 50); w.setVisible(true);
        Timer x = new Timer(4000, e -> w.dispose()); x.setRepeats(false); x.start();
    }
    public static void checkReminders() {
        if (me == null) return; LocalDateTime now = LocalDateTime.now();
        for (Sess s : S.sessions) if (s.status.equals("CONFIRMED") && !remindedIds.contains(s.id) && involves(s, me) && s.at.isAfter(now.minusMinutes(5)) && s.at.isBefore(now.plusMinutes(60))) {
            remindedIds.add(s.id); notifyUser("Upcoming session", s.skill + " with " + name(other(s)) + " at " + s.at.format(DateTimeFormatter.ofPattern("h:mm a")));
        }
    }
}
