package campus.skillswap;

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
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Application entry point. */
public class Main {

    public static void main(String[] a) {
        loadCfg(); applyTheme("dark".equals(c("theme", "light")));
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); if (frame != null && frame.isShowing()) SwingUtilities.invokeLater(() -> toast("Error: " + e.getMessage())); });
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("SkillSwap Campus - Peer Learning"); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { closeDb(); } });
            frame.setSize(1280, 780); frame.setMinimumSize(new Dimension(1050, 650)); frame.setLocationRelativeTo(null);
            if (!connectLoop("")) System.exit(0);
            try { initData(); } catch (Exception ex) { info("Could not load data from PostgreSQL:\n" + ex.getMessage()); System.exit(1); }
            root = new JPanel(rootCards); root.add(loginPanel(), "login"); root.add(appPanel(), "app");
            frame.setContentPane(root); frame.setVisible(true); rootCards.show(root, "login");
            startPolling(); new Timer(30000, e -> checkReminders()).start();
        });
    }
}
