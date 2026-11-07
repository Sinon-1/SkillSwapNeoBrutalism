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

/** Login, account creation and password reset. */
public class LoginScreen {

    // ====================== LOGIN ======================
    /** Chunky icon tile: bright fill, ink border, hard shadow. 0 = graduation cap, 1 = swap arrows, 2 = magnifier. */
    public static JComponent iconTile(int type) {
        final Color fill = POP[new int[]{0, 1, 3}[type % 3]];
        return new JComponent() {
            { setPreferredSize(new Dimension(80, 80)); setMinimumSize(new Dimension(80, 80)); }
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0; aa(g); int d = 74;
                g.setColor(INK); g.fillRect(6, 6, d, d); g.setColor(fill); g.fillRect(0, 0, d, d); frame(g, 0, 0, d, d, 3, INK);
                g.setColor(ONACC); g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (type == 2) { g.drawOval(19, 15, 28, 28); g.drawLine(43, 41, 55, 55); }
                else if (type == 1) { g.drawLine(15, 26, 48, 26); g.fillPolygon(new int[]{46, 59, 46}, new int[]{18, 26, 34}, 3); g.drawLine(59, 47, 26, 47); g.fillPolygon(new int[]{28, 15, 28}, new int[]{39, 47, 55}, 3); }
                else { g.drawPolygon(new int[]{37, 63, 37, 11}, new int[]{17, 29, 41, 29}, 4); g.drawArc(23, 35, 28, 18, 180, 180); g.drawLine(63, 29, 63, 44); }
            }
        };
    }
    static void block(Graphics2D g, Shape s, Color fill) {
        g.translate(8, 8); g.setColor(INK); g.fill(s); g.translate(-8, -8); g.setColor(fill); g.fill(s); g.setColor(INK); g.setStroke(new BasicStroke(4f)); g.draw(s);
    }
    public static JComponent loginPanel() {
        JPanel bg = new JPanel(new GridBagLayout()) {
            protected void paintComponent(Graphics g0) {   // playful background blocks
                super.paintComponent(g0); Graphics2D g = (Graphics2D) g0.create(); aa(g); int W = getWidth(), H = getHeight();
                block(g, new java.awt.geom.Ellipse2D.Float(W * 0.07f, H * 0.10f, 150, 150), YEL);
                block(g, java.awt.geom.AffineTransform.getRotateInstance(Math.toRadians(14), W * 0.12f + 60, H * 0.70f + 60).createTransformedShape(new Rectangle((int) (W * 0.12f), (int) (H * 0.70f), 120, 120)), PINK);
                block(g, new java.awt.geom.Ellipse2D.Float(W * 0.82f, H * 0.64f, 130, 130), POP[3]);
                block(g, java.awt.geom.AffineTransform.getRotateInstance(Math.toRadians(-10), W * 0.85f + 50, H * 0.12f + 50).createTransformedShape(new Rectangle((int) (W * 0.85f), (int) (H * 0.12f), 100, 100)), POP[2]);
                g.dispose();
            }
        };
        bg.setBackground(BG);
        JPanel c = new Card() { public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); d.width = Math.max(d.width, 440); return d; } };
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS)); c.setBorder(new EmptyBorder(22, 30, 20, 30));
        JPanel tp = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)); tp.setOpaque(false); tp.add(lbl("SKILLSWAP", Font.BOLD, 32, TXT)); put(c, tp);
        JPanel sp = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)); sp.setOpaque(false); sp.add(lbl("Swap skills. Learn from your peers.", Font.PLAIN, 13, MUTED)); put(c, sp); gap(c, 14);
        JPanel icons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0)); icons.setOpaque(false); for (int i = 0; i < 3; i++) icons.add(iconTile(i)); put(c, icons); gap(c, 18);
        JTextField u = field(); JPasswordField p = pass();
        put(c, lbl("Username or school email", Font.BOLD, 12, TXT)); gap(c, 4); put(c, u); gap(c, 10); put(c, lbl("Password", Font.BOLD, 12, TXT)); gap(c, 4); put(c, p); gap(c, 4);
        JButton forgot = ghost("<html><u>Forgot password?</u></html>", LoginScreen::forgot); ((RBtn) forgot).flat(true); forgot.setBackground(PANEL); forgot.setFont(f(Font.BOLD, 11));
        JPanel fr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); fr.setOpaque(false); fr.add(forgot); put(c, fr); gap(c, 8);
        Runnable go = () -> {
            String key = u.getText().trim(); String pw = new String(p.getPassword());
            User f = S.users.values().stream().filter(x -> x.username.equalsIgnoreCase(key) || x.email.equalsIgnoreCase(key)).findFirst().orElse(null);
            if (f == null || !f.passHash.equals(hash(f.username, pw))) { JOptionPane.showMessageDialog(frame, "Incorrect username/email or password.", "Sign in failed", JOptionPane.ERROR_MESSAGE); return; }
            u.setText(""); p.setText(""); enter(f);
        };
        p.addActionListener(e -> go.run()); u.addActionListener(e -> go.run());
        JButton si = btn("SIGN IN", go); si.setFont(f(Font.BOLD, 14)); si.setBorder(new EmptyBorder(10, 12, 10, 12)); put(c, wide(si)); gap(c, 10);
        JButton reg = ghost("CREATE ACCOUNT", LoginScreen::register); reg.setFont(f(Font.BOLD, 14)); reg.setBorder(new EmptyBorder(10, 12, 10, 12)); put(c, wide(reg)); gap(c, 14);
        JLabel net = lbl(netText(), Font.BOLD, 11, !connected() ? GOLD : GREEN); JPanel nl = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)); nl.setOpaque(false); nl.add(net); put(c, nl); gap(c, 6);
        JPanel nr = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0)); nr.setOpaque(false);
        JButton cs = ghost("Connection settings", () -> { changeConnection(); net.setText(netText()); }), th = ghost(dark ? "Light mode" : "Dark mode", () -> switchTheme(!dark));
        for (JButton b : new JButton[]{cs, th}) { b.setFont(f(Font.BOLD, 11)); b.setBorder(new EmptyBorder(4, 10, 4, 10)); nr.add(b); }
        put(c, nr); gap(c, 10);
        JPanel ft = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)); ft.setOpaque(false); ft.add(lbl("Contact us: SkillSwap support | Privacy | T&C", Font.PLAIN, 10, MUTED)); put(c, ft);
        bg.add(c); return bg;
    }
    public static void enter(User u) {
        me = u; page = "Feed"; lastMsgSeen = System.currentTimeMillis(); draft = ""; chatWith = null; rootCards.show(root, "app"); show("Feed"); checkReminders();
        notifyUser("Welcome, " + name(u), "You have " + u.credits + " swap credits.");
    }
    public static void register() {
        JTextField em = field(), un = field(), rn = field(); JPasswordField p1 = pass(), p2 = pass();
        JCheckBox real = check("Show my real name publicly (otherwise username)", false), pub = check("Make my ratings public", true);
        JPanel f = form(new String[]{"School email", "Username", "Real name", "Password", "Confirm", "", ""}, new JComponent[]{em, un, rn, p1, p2, real, pub});
        f.setPreferredSize(new Dimension(420, 270));
        if (!ask("Create account", f)) return;
        String email = em.getText().trim().toLowerCase(), user = un.getText().trim(), pw = new String(p1.getPassword());
        String err = !email.matches("^[\\w.+-]+@[\\w.-]*edu[\\w.-]*$") ? "Please use your school email (a domain containing .edu)."
                : user.length() < 3 ? "Username must be at least 3 characters." : S.users.keySet().stream().anyMatch(x -> x.equalsIgnoreCase(user)) || S.users.values().stream().anyMatch(x -> x.email.equals(email)) ? "That username or email is already registered."
                : pw.length() < 6 ? "Password must be at least 6 characters." : !pw.equals(new String(p2.getPassword())) ? "Passwords do not match." : null;
        if (err != null) { JOptionPane.showMessageDialog(frame, err, "Cannot create account", JOptionPane.WARNING_MESSAGE); return; }
        String code = String.format("%06d", new Random().nextInt(1000000)); notifyUser("SkillSwap verification", "Your code is " + code);
        String in = JOptionPane.showInputDialog(frame, "Enter the 6-digit code sent to " + email + "\n(Demo: no mail server is configured - your code is " + code + ")");
        if (in == null || !in.trim().equals(code)) { info("Verification failed. Account not created."); return; }
        User u = new User(); u.username = user; u.email = email; u.realName = rn.getText().trim(); u.useRealName = real.isSelected() && !u.realName.isEmpty();
        u.publicRatings = pub.isSelected(); u.passHash = hash(user, pw); S.users.put(user, u); save(); info("Account created! Add the skills you teach and want to learn in My Profile."); enter(u); show("Profile");
    }
    public static void forgot() {
        JTextField em = field(); JPasswordField np = pass();
        if (!ask("Reset password", form(new String[]{"School email", "New password"}, new JComponent[]{em, np}))) return;
        User u = S.users.values().stream().filter(x -> x.email.equalsIgnoreCase(em.getText().trim())).findFirst().orElse(null);
        if (u == null || np.getPassword().length < 6) { info("Email not found, or password shorter than 6 characters."); return; }
        String code = String.format("%06d", new Random().nextInt(1000000));
        String in = JOptionPane.showInputDialog(frame, "Enter the code sent to your school email\n(Demo code: " + code + ")");
        if (in != null && in.trim().equals(code)) { u.passHash = hash(u.username, new String(np.getPassword())); save(); info("Password updated."); } else info("Wrong code.");
    }
}
