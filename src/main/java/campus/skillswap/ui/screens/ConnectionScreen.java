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
        while (true) {
            String last = c("conn.mode", "host");
            JRadioButton rh = radio("Host on this Wi-Fi / LAN  (no database needed - best for testing)", last.equals("host"));
            JRadioButton rj = radio("Join a host on this Wi-Fi / LAN", last.equals("join"));
            JRadioButton rp = radio("Shared PostgreSQL database  (for real deployment)", last.equals("pg"));
            ButtonGroup bg = new ButtonGroup(); JPanel col = new JPanel(); col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS)); col.setBackground(PANEL);
            for (JRadioButton r : new JRadioButton[]{rh, rj, rp}) { bg.add(r); r.setOpaque(false); r.setForeground(TXT); r.setFont(f(Font.BOLD, 13)); r.setBorder(new EmptyBorder(6, 4, 6, 4)); col.add(r); }
            JPanel wrap = new JPanel(new BorderLayout(0, 8)); wrap.setBackground(PANEL);
            if (!err.isEmpty()) wrap.add(lbl("<html><body style='width:340px'>" + esc(err) + "</body></html>", Font.BOLD, 11, PINKT), BorderLayout.NORTH);
            wrap.add(col);
            if (!ask("How do you want to connect?", wrap)) return false;
            String m = rh.isSelected() ? "host" : rj.isSelected() ? "join" : "pg"; cfg.setProperty("conn.mode", m); saveCfg(); err = "";
            if (m.equals("host") ? connectHost() : m.equals("join") ? connectJoin() : connectPostgres("")) return true;
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
            JPanel f = form(new String[]{"Host", "Port", "Database", "User", "Password", ""}, new JComponent[]{h, po, dn, us, pw, rem}); f.setPreferredSize(new Dimension(380, 230));
            JPanel wrap = new JPanel(new BorderLayout(0, 8)); wrap.setBackground(PANEL);
            if (!err.isEmpty()) wrap.add(lbl("<html><body style='width:340px'>" + esc(err) + "</body></html>", Font.BOLD, 11, PINKT), BorderLayout.NORTH);
            wrap.add(f);
            if (!ask("Connect to PostgreSQL", wrap)) return false;
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
        if (busy > 0 || videoOpen() || v != ver || fp.equals(snap)) return;   // wait while a video is playing
        Map<Integer, String> old = new HashMap<>(); for (Sess s : S.sessions) old.put(s.id, s.status); long cutoff = lastMsgSeen;
        S = st; snap = fp;
        if (me != null) {
            me = S.users.get(me.username); if (me == null) { rootCards.show(root, "login"); return; }
            for (Msg m : S.msgs) if (m.to.equals(me.username) && m.time > cutoff) { lastMsgSeen = Math.max(lastMsgSeen, m.time);
                if (!(page.equals("Messages") && m.from.equals(chatWith))) notifyUser("New message", name(m.from) + ": " + m.text); }
            for (Sess s : S.sessions) if (involves(s, me)) { String o = old.get(s.id); boolean req = s.requester.equals(me.username);
                if (o == null && s.status.equals("PENDING") && !req) notifyUser("New swap request", name(s.requester) + " wants a " + s.skill + " session");
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
