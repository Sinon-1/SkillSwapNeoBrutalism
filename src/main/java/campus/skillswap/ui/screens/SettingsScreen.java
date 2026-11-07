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
import campus.skillswap.net.LanSync;
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
import static campus.skillswap.ui.screens.FeedScreen.*;
import static campus.skillswap.ui.screens.SearchScreen.*;
import static campus.skillswap.ui.screens.CertsScreen.*;
import static campus.skillswap.ui.screens.BookingsScreen.*;
import static campus.skillswap.ui.screens.NearbyScreen.*;
import static campus.skillswap.ui.screens.MessagesScreen.*;
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Settings page (theme, database connection). */
public class SettingsScreen {

    // ====================== SETTINGS ======================
    public static JComponent settingsPage() {
        Pg p = pg("Settings", "Appearance and database connection", null);
        JPanel ap = card(); JPanel ai = vbox();
        put(ai, lbl("Appearance", Font.BOLD, 14, TXT)); put(ai, lbl("Choose how SkillSwap Campus looks on this computer. Your choice is remembered.", Font.PLAIN, 12, MUTED)); gap(ai, 6);
        JPanel r = row(); r.add(chip("Dark mode", dark, () -> { if (!dark) switchTheme(true); })); r.add(chip("Light mode", !dark, () -> { if (dark) switchTheme(false); })); put(ai, r);
        ap.add(ai); put(p.body, ap); gap(p.body, 10);
        JPanel dp = card(); JPanel di = vbox();
        put(di, lbl("Connection", Font.BOLD, 14, TXT)); put(di, lbl(LanSync.active() ? netText() : netText() + "   as   " + c("db.user", "postgres"), Font.PLAIN, 12, MUTED));
        put(di, lbl(!connected() ? "Status: not connected" : "Status: connected", Font.BOLD, 12, !connected() ? GOLD : GREEN)); gap(di, 6);
        JPanel dr = row();
        dr.add(ghost("Test connection", () -> { try { info(LanSync.active() ? "LAN connection is active: " + netText() : db != null && db.isValid(3) ? "Connection OK." : "Connection is not valid."); } catch (SQLException e) { info("Connection failed: " + e.getMessage()); } }));
        dr.add(ghost("Change connection", ConnectionScreen::changeConnection)); put(di, dr);
        dp.add(di); put(p.body, dp); gap(p.body, 10);
        JPanel ac = card(); JPanel ci = vbox();
        put(ci, lbl("Account", Font.BOLD, 14, TXT)); put(ci, lbl("Signed in as " + name(me) + " (" + me.email + ")", Font.PLAIN, 12, MUTED)); ac.add(ci); put(p.body, ac);
        return p.root;
    }
}
