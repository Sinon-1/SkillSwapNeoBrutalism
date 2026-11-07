package campus.skillswap.ui.media;

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

/** Full-screen reels viewer. */
public class Reels {

    public static JDialog reelDlg; public static List<Integer> reelIds = new ArrayList<>(); public static int reelIdx, reelTok; public static JComponent reelFx; public static long lastWheel;
    public static final Color REEL_MUTED = new Color(0xB8BFE0);
    public static void fixSize(JComponent c, int w, int h) { Dimension d = new Dimension(w, h); c.setPreferredSize(d); c.setMinimumSize(d); c.setMaximumSize(d); }
    public static JButton reelBtn(String t, Runnable r) {
        RBtn b = new RBtn(t, 30); b.setFont(f(Font.BOLD, 13)); b.setForeground(Color.WHITE); b.setBackground(new Color(0x262B3D)); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(0, 0, 0, 0)); fixSize(b, 76, 46); b.setAlignmentX(0.5f); if (r != null) b.addActionListener(e -> r.run()); return b;
    }
    public static void openReels(int postId) {
        reelIds = videoPosts().stream().map(p -> p.id).collect(Collectors.toList()); if (reelIds.isEmpty()) return;
        reelIdx = Math.max(0, reelIds.indexOf(postId));
        if (reelDlg == null) {
            reelDlg = new JDialog(frame, "Reels", false); reelDlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); reelDlg.setSize(620, 850); reelDlg.setLocationRelativeTo(frame);
            reelDlg.getContentPane().setBackground(Color.BLACK);
            reelDlg.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { closeReels(); } });
            JRootPane rp = reelDlg.getRootPane();
            rp.registerKeyboardAction(e -> reelStep(1), KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            rp.registerKeyboardAction(e -> reelStep(-1), KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            rp.registerKeyboardAction(e -> closeReels(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            reelDlg.addMouseWheelListener(e -> { long n = System.currentTimeMillis(); if (n - lastWheel > 450) { lastWheel = n; reelStep(e.getWheelRotation() > 0 ? 1 : -1); } });
        }
        renderReel(); reelDlg.setVisible(true);
    }
    public static void reelStep(int d) { int n = reelIdx + d; if (n < 0 || n >= reelIds.size()) return; reelIdx = n; renderReel(); }
    public static void closeReels() {
        reelTok++; if (reelFx != null) { fxStop(reelFx); reelFx = null; } if (reelDlg != null) { reelDlg.dispose(); reelDlg = null; }
    }
    public static void renderReel() {
        if (reelFx != null) { fxStop(reelFx); reelFx = null; }
        Post po = postById(reelIds.get(reelIdx)); if (po == null) { closeReels(); return; }
        reelDlg.setContentPane(buildReel(po, ++reelTok)); reelDlg.revalidate(); reelDlg.repaint();
    }
    public static JComponent reelPoster(Post po, File file) {
        final Color base = catColor(po.category);
        JPanel p = new JPanel(new GridBagLayout()) { protected void paintComponent(Graphics g0) { Graphics2D g = (Graphics2D) g0.create();
            g.setPaint(new GradientPaint(0, 0, base.darker(), 0, getHeight(), Color.BLACK)); g.fillRect(0, 0, getWidth(), getHeight()); g.dispose(); } };
        JPanel v = vbox(); JButton play = btn("\u25B6  Play video", () -> openExternal(file)); play.setFont(f(Font.BOLD, 16)); play.setBorder(new EmptyBorder(12, 26, 12, 26));
        JLabel nm = lbl(po.videoName, Font.PLAIN, 12, Color.WHITE), hint = lbl("Opens in your default video player", Font.PLAIN, 11, REEL_MUTED);
        play.setAlignmentX(0.5f); nm.setAlignmentX(0.5f); hint.setAlignmentX(0.5f); v.add(play); gap(v, 14); v.add(nm); gap(v, 4); v.add(hint); p.add(v); return p;
    }
    public static JPanel buildReel(Post po, int tok) {
        final int pid = po.id; final Color W = Color.WHITE;
        JPanel main = new JPanel(new BorderLayout(0, 10)); main.setBackground(Color.BLACK); main.setBorder(new EmptyBorder(12, 16, 14, 16));
        JPanel tb = new JPanel(new BorderLayout()); tb.setOpaque(false); tb.add(lbl("Reels", Font.BOLD, 20, W), BorderLayout.WEST);
        JPanel tr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); tr.setOpaque(false); tr.add(lbl((reelIdx + 1) + " / " + reelIds.size(), Font.PLAIN, 12, REEL_MUTED));
        JButton x = reelBtn("\u2715", Reels::closeReels); fixSize(x, 44, 34); tr.add(x); tb.add(tr, BorderLayout.EAST); main.add(tb, BorderLayout.NORTH);

        JPanel stage = new JPanel(new GridBagLayout()); stage.setBackground(new Color(0x07090F)); fixSize(stage, 360, 540);
        stage.add(lbl("Loading video...", Font.PLAIN, 14, REEL_MUTED));
        JPanel act = new JPanel(); act.setLayout(new BoxLayout(act, BoxLayout.Y_AXIS)); act.setOpaque(false); fixSize(act, 76, 540);
        JButton like = reelBtn("\u2665 " + po.likes.size(), null); like.setForeground(po.likes.contains(me.username) ? PINK : W);
        like.addActionListener(e -> { Post cur = postById(pid); if (cur == null) return; if (!cur.likes.remove(me.username)) cur.likes.add(me.username); save();
            like.setText("\u2665 " + cur.likes.size()); like.setForeground(cur.likes.contains(me.username) ? PINK : W); if (page.equals("Feed")) show("Feed"); });
        act.add(like); act.add(Box.createVerticalStrut(10));
        if (!po.author.equals(me.username)) {
            final String au = po.author; final boolean offer = po.type.equals("OFFER");
            act.add(reelBtn("Chat", () -> { closeReels(); chatWith = au; draft = ""; show("Messages"); })); act.add(Box.createVerticalStrut(10));
            act.add(reelBtn(offer ? "Book" : "Teach", () -> { if (offer) book(au, me.username, po.skill); else book(me.username, au, po.skill); })); act.add(Box.createVerticalStrut(10));
            act.add(reelBtn("\u2691", () -> report(au)));
        }
        act.add(Box.createVerticalGlue());
        JButton up = reelBtn("\u25B2", () -> reelStep(-1)), dn = reelBtn("\u25BC", () -> reelStep(1)); up.setEnabled(reelIdx > 0); dn.setEnabled(reelIdx < reelIds.size() - 1);
        act.add(up); act.add(Box.createVerticalStrut(10)); act.add(dn);
        JPanel mid = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0)); mid.setOpaque(false); mid.add(stage); mid.add(act); main.add(mid);

        JPanel info = vbox(); JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); who.setOpaque(false);
        who.add(avatar(name(po.author), 36)); who.add(lbl(name(po.author), Font.BOLD, 15, W)); User a = S.users.get(po.author); if (a != null && verified(a)) who.add(tag("VERIFIED", GREEN));
        who.add(tag(po.type.equals("OFFER") ? "OFFERING" : "REQUESTING", po.type.equals("OFFER") ? GREEN : GOLD)); put(info, who); gap(info, 6);
        if (!po.desc.isEmpty()) { put(info, wrapLabel(po.desc, W, 13)); gap(info, 4); }
        put(info, lbl("#" + po.skill.replace(" ", "") + "  #" + po.category + "  #reel", Font.BOLD, 11, PINK)); main.add(info, BorderLayout.SOUTH);

        withVideo(po, file -> { if (tok != reelTok) return; stage.removeAll(); stage.setLayout(new BorderLayout()); JComponent fx = fxPlayer(file, 360, 540);
                if (fx != null) { reelFx = fx; stage.add(fx); } else stage.add(reelPoster(po, file)); stage.revalidate(); stage.repaint(); },
            err -> { if (tok != reelTok) return; stage.removeAll(); stage.add(lbl("<html><body style='width:300px'>Could not load video: " + esc(err) + "</body></html>", Font.PLAIN, 13, PINK)); stage.revalidate(); stage.repaint(); });
        return main;
    }
}
