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

/** Connect-to-PostgreSQL dialog and live refresh when other computers change data. */
public class ConnectionScreen {

    public static boolean connectLoop(String err) {
        cfg.setProperty("conn.mode", "pg"); saveCfg();
        return connectPostgres(err);
    }
    /**
     * Tries the connection saved in ~/.skillswap/config.properties without showing a dialog.
     * Returns null when it worked, or a message for the connection dialog when it did not.
     */
    public static String autoConnect() {
        String host = c("db.host", "").trim();
        if (host.isEmpty()) return "";
        String port = c("db.port", "5432").trim();
        String name = c("db.name", "postgres").trim().toLowerCase();
        String user = c("db.user", "postgres").trim();
        String pass = c("db.password", System.getenv().getOrDefault("SKILLSWAP_DB_PASSWORD", ""));
        if (pass.isEmpty()) return "No database password is saved. Enter it once and tick \"Remember password on this computer\".";
        Connection a = null, b = null;
        try {
            Class.forName("org.postgresql.Driver");
            DriverManager.setLoginTimeout(10);
            a = open(host, port, name, user, pass);
            schema(a);
            b = open(host, port, name, user, pass);
            b.setAutoCommit(false);
            b.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            closeDb();
            db = a; dbRead = b; xfer = new String[]{host, port, name, user, pass};
            cfg.setProperty("conn.mode", "pg"); saveCfg();
            return null;
        } catch (Exception ex) {
            try { if (a != null) a.close(); if (b != null) b.close(); } catch (Exception ignored) { }
            return "Could not connect to the saved database: " + ex.getMessage();
        }
    }
    static boolean connectHost() {
        JTextField po = field(); po.setText(c("lan.port", String.valueOf(LanSync.DEFAULT_PORT)));
        if (!ask("Host on this network", form(new String[]{"Port"}, new JComponent[]{po}))) return false;
        try {
            int port = Integer.parseInt(po.getText().trim()); closeDb(); LanSync.startHost(port); cfg.setProperty("lan.port", "" + port); saveCfg();
            info("You are hosting!\n\nOthers on the same Wi-Fi start the app, choose 'Join a host' and enter:\n\n    Address:   " + LanSync.lanIp() + "\n    Port:         " + port
                + "\n    Join code:  " + LanSync.code + "\n\nKeep this app open while they use it.\nIf Windows/macOS asks, allow Java through the firewall.");
            return true;
        } catch (Exception ex) { info("Could not start hosting: " + ex.getMessage()); return false; }
    }
    static boolean connectJoin() {
        JTextField h = field(), po = field(), cd = field(); h.setText(c("lan.host", "")); po.setText(c("lan.port", String.valueOf(LanSync.DEFAULT_PORT)));
        if (!ask("Join a host", form(new String[]{"Host address", "Port", "Join code"}, new JComponent[]{h, po, cd}))) return false;
        try {
            closeDb(); LanSync.join(h.getText().trim(), Integer.parseInt(po.getText().trim()), cd.getText().trim());
            cfg.setProperty("lan.host", h.getText().trim()); cfg.setProperty("lan.port", po.getText().trim()); saveCfg();
            info("Connected to the host. Create an account or sign in."); return true;
        } catch (Exception ex) { info("Could not join: " + ex.getMessage()); return false; }
    }
    /** Connection form with a label above each field, so nothing gets clipped when the dialog opens. */
    static JPanel dbForm(JTextField h, JTextField po, JTextField dn, JTextField us, JPasswordField pw, JCheckBox rem) {
        JPanel f = vbox(); f.setBackground(PANEL);
        put(f, lbl("Supabase: copy the values from Project Settings > Database.", Font.PLAIN, 11, MUTED));
        gap(f, 10);
        dbField(f, "Host", h);
        dbField(f, "Port", po);
        dbField(f, "Database", dn);
        dbField(f, "User", us);
        dbField(f, "Password", pw);
        gap(f, 2); put(f, rem);
        f.setPreferredSize(new Dimension(420, f.getPreferredSize().height));
        return f;
    }
    static void dbField(JPanel box, String label, JComponent comp) {
        put(box, lbl(label, Font.BOLD, 12, TXT)); gap(box, 3); put(box, comp); gap(box, 9);
    }
    public static boolean connectPostgres(String err) {
        try { Class.forName("org.postgresql.Driver"); }
        catch (ClassNotFoundException e) {
            info("PostgreSQL JDBC driver not found.\n\nBuild and start the app with Maven so the driver is on the classpath:\n"
                    + "  mvn exec:java        (or run scripts/run-dev.sh / run-dev.bat)");
            return false;
        }
        while (true) {
            JTextField h = field(), po = field(), dn = field(), us = field(); JPasswordField pw = pass(); JCheckBox rem = check("Remember password on this computer", false);
            h.setText(c("db.host", "localhost")); po.setText(c("db.port", "5432")); dn.setText(c("db.name", "skillswap")); us.setText(c("db.user", "postgres"));
            pw.setText(c("db.password", System.getenv().getOrDefault("SKILLSWAP_DB_PASSWORD", ""))); rem.setSelected(!c("db.password", "").isEmpty());
            JPanel f = dbForm(h, po, dn, us, pw, rem);
            JPanel wrap = new JPanel(new BorderLayout(0, 8)); wrap.setBackground(PANEL);
            if (!err.isEmpty()) wrap.add(lbl("<html><body style='width:340px'>" + esc(err) + "</body></html>", Font.BOLD, 11, PINKT), BorderLayout.NORTH);
            wrap.add(f);
            if (!ask("Connect to PostgreSQL / Supabase", wrap)) return false;
            String host = h.getText().trim(), port = po.getText().trim(), name = dn.getText().trim().toLowerCase(), user = us.getText().trim(), pass = new String(pw.getPassword());
            Connection a = null, b = null;
            try {
                a = open(host, port, name, user, pass); schema(a); b = open(host, port, name, user, pass);
                b.setAutoCommit(false); b.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                closeDb(); db = a; dbRead = b; xfer = new String[]{host, port, name, user, pass};
                cfg.setProperty("db.host", host); cfg.setProperty("db.port", port); cfg.setProperty("db.name", name); cfg.setProperty("db.user", user);
                if (rem.isSelected()) cfg.setProperty("db.password", pass); else cfg.remove("db.password");
                saveCfg(); return true;
            } catch (Exception ex) {
                try { if (a != null) a.close(); if (b != null) b.close(); } catch (Exception x) { }
                err = "Could not connect: " + ex.getMessage();
            }
        }
    }
    public static void applyRemote(Store st, Map<String, String> fp, int v) {
        for (User ru : st.users.values()) { User cu = S.users.get(ru.username); if (cu != null && cu != me) cu.lastSeen = ru.lastSeen; }   // presence only: no full reload
        if (busy > 0 || videoOpen() || v != ver || fp.equals(snap)) { campus.skillswap.service.Presence.refresh(); return; }   // wait while a video is playing
        Map<Integer, String> old = new HashMap<>(); for (Sess s : S.sessions) old.put(s.id, s.status); long cutoff = lastMsgSeen;
        S = st; snap = fp;
        if (me != null) {
            me = S.users.get(me.username); if (me == null) { rootCards.show(root, "login"); return; }
            if (!sessionValid()) return;
            for (Msg m : S.msgs) MessagesScreen.notifyNew(m, cutoff);
            for (Sess s : S.sessions) if (involves(s, me)) { String o = old.get(s.id); boolean req = s.requester.equals(me.username);
                if (o == null && s.status.equals("PENDING") && !req) notifyUser("New swap request", name(s.requester) + " wants a " + s.skill + " session" + (s.videoCall ? " (video call requested)" : ""));
                else if ("PENDING".equals(o) && req && s.status.equals("CONFIRMED")) notifyUser("Swap accepted", name(other(s)) + " accepted your " + s.skill + " session");
                else if ("PENDING".equals(o) && req && s.status.equals("DECLINED")) notifyUser("Swap declined", name(other(s)) + " declined your " + s.skill + " request"); }
            show(page);
        }
    }
    public static String netText() {
        if (LanSync.active()) return LanSync.status();
        return db == null ? "Database: not connected" : "PostgreSQL: " + c("db.host", "localhost") + ":" + c("db.port", "5432") + "/" + c("db.name", "skillswap");
    }
    public static void changeConnection() {
        if (!connectLoop("")) return;
        try { initData(); me = null; rootCards.show(root, "login"); toast("Connected to " + netText()); }
        catch (Exception e) { info("Could not load data: " + e.getMessage()); }
    }
}
