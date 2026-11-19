package campus.skillswap.ui.screens;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.*;
import campus.skillswap.model.*;
import static campus.skillswap.core.AppState.*;
import static campus.skillswap.data.Database.*;
import static campus.skillswap.data.Seed.*;
import static campus.skillswap.service.Stats.*;
import static campus.skillswap.ui.Theme.*;
import static campus.skillswap.ui.AppShell.*;
import static campus.skillswap.ui.Notifier.*;
import static campus.skillswap.ui.screens.ProfileScreen.*;

/** Campus moderator tools: review reports, ban accounts and undo bans. */
public class ModerationScreen {

    static final Color DANGER = new Color(0xB42318);

    public static JComponent moderationPage() {
        Pg p = pg("Reports & Moderation", "Review reports and keep the campus safe", me != null && me.moderator ? btn("Refresh", () -> show("Moderation")) : null);
        if (me == null || !me.moderator) {
            JPanel c = card(); c.add(lbl("Moderator access only", Font.BOLD, 16, TXT), BorderLayout.NORTH);
            c.add(lbl("Sign in with a campus moderator account to review reports.", Font.PLAIN, 12, MUTED));
            put(p.body, c);
            return p.root;
        }
        JPanel banner = card(); JPanel bi = vbox();
        JPanel bh = row(); bh.add(lbl("Signed in as " + name(me), Font.BOLD, 14, TXT)); bh.add(tag("MODERATOR", GREEN));
        put(bi, bh); put(bi, lbl("Reports are shown newest first. Banning closes all open reports for that account.", Font.PLAIN, 12, MUTED));
        banner.add(bi); put(p.body, banner); gap(p.body, 10);

        List<Report> open = S.reports.stream().filter(r -> !r.resolved).sorted(Comparator.comparingLong((Report r) -> r.time).reversed()).collect(Collectors.toList());
        put(p.body, lbl("OPEN REPORTS  (" + open.size() + ")", Font.BOLD, 13, MUTED)); gap(p.body, 4);
        if (open.isEmpty()) {
            JPanel c = card(); c.add(lbl("No open reports. Nice work!", Font.PLAIN, 12, MUTED)); put(p.body, c);
        } else for (Report r : open) { put(p.body, reportCard(r)); gap(p.body, 8); }

        gap(p.body, 10);
        List<User> banned = S.users.values().stream().filter(u -> u.banned).sorted(Comparator.comparing(u -> name(u).toLowerCase())).collect(Collectors.toList());
        put(p.body, lbl("BANNED ACCOUNTS  (" + banned.size() + ")", Font.BOLD, 13, MUTED)); gap(p.body, 4);
        if (banned.isEmpty()) { JPanel c = card(); c.add(lbl("No accounts are currently banned.", Font.PLAIN, 12, MUTED)); put(p.body, c); }
        else for (User u : banned) { put(p.body, bannedCard(u)); gap(p.body, 8); }
        return p.root;
    }

    static JComponent reportCard(Report r) {
        User target = S.users.get(r.target), reporter = S.users.get(r.by);
        JPanel c = card();
        JPanel top = new JPanel(new BorderLayout(10, 0)); top.setOpaque(false);
        top.add(avatarWrap(target == null ? r.target : name(target), 38), BorderLayout.WEST);
        JPanel inf = vbox();
        JPanel h = row();
        h.add(lbl(target == null ? r.target : name(target), Font.BOLD, 14, TXT));
        h.add(tag(r.reason.toUpperCase(), GOLD));
        if (target != null && target.banned) h.add(tag("BANNED", DANGER));
        if (target != null && target.moderator) h.add(tag("MODERATOR", GREEN));
        put(inf, h);
        put(inf, lbl("Reported by @" + r.by + (reporter == null ? "" : " (" + name(reporter) + ")") + "  \u2022  " + ago(r.time), Font.PLAIN, 11, MUTED));
        String details = r.details == null || r.details.trim().isEmpty() ? "No extra details were provided." : r.details;
        put(inf, lbl("<html><body style='width:650px'>" + esc(details) + "</body></html>", Font.PLAIN, 12, TXT));
        top.add(inf, BorderLayout.CENTER);
        c.add(top, BorderLayout.CENTER);

        JPanel actions = row();
        if (target != null) actions.add(ghost("View profile", () -> modProfile(target)));
        if (target != null && target.banned) actions.add(btn("UNBAN ACCOUNT", () -> unban(target.username)));
        else if (target != null && target.moderator) actions.add(tag("CANNOT BAN MODERATOR", MUTED));
        else actions.add(dangerButton("BAN ACCOUNT", () -> ban(r.target, r.reason)));
        actions.add(ghost("Dismiss report", () -> dismiss(r)));
        c.add(actions, BorderLayout.SOUTH);
        return c;
    }

    static JComponent bannedCard(User u) {
        JPanel c = card();
        JPanel top = new JPanel(new BorderLayout(10, 0)); top.setOpaque(false);
        top.add(avatarWrap(name(u), 36), BorderLayout.WEST);
        JPanel inf = vbox(); JPanel h = row();
        h.add(lbl(name(u), Font.BOLD, 13, TXT)); h.add(tag("BANNED", DANGER));
        put(inf, h);
        put(inf, lbl("@" + u.username + "  \u2022  banned " + ago(u.bannedAt) + (u.bannedBy == null || u.bannedBy.isEmpty() ? "" : " by @" + u.bannedBy), Font.PLAIN, 11, MUTED));
        if (u.banReason != null && !u.banReason.isEmpty()) put(inf, lbl("<html><body style='width:650px'>Reason: " + esc(u.banReason) + "</body></html>", Font.PLAIN, 12, TXT));
        top.add(inf);
        c.add(top, BorderLayout.CENTER);
        JPanel actions = row(); actions.add(ghost("View profile", () -> modProfile(u))); actions.add(btn("UNBAN ACCOUNT", () -> unban(u.username)));
        c.add(actions, BorderLayout.SOUTH);
        return c;
    }

    static JButton dangerButton(String text, Runnable r) {
        JButton b = btn(text, r); b.setBackground(DANGER); b.setForeground(Color.WHITE); return b;
    }

    /** Rich read-only profile shown to moderators, with moderation actions in the footer. */
    public static void modProfile(User u) {
        if (u == null) return;
        final JDialog d = new JDialog(frame, "Profile - " + name(u), true);
        JPanel outer = new JPanel(new BorderLayout()); outer.setBackground(BG); outer.setBorder(new EmptyBorder(14, 14, 10, 10));
        Card card = new Card(); card.setLayout(new BorderLayout());
        Color band = u.banned ? DANGER : u.moderator ? GREEN : YEL;
        Color fg = u.banned ? Color.WHITE : ONACC;
        JPanel head = new JPanel(new BorderLayout(14, 0)); head.setBackground(band); head.setBorder(new CompoundBorder(new MatteBorder(0, 0, CB, 0, INK), new EmptyBorder(16, 22, 14, 22)));
        JPanel av = new JPanel(new GridBagLayout()); av.setOpaque(false); av.add(avatarWrap(name(u), 76)); head.add(av, BorderLayout.WEST);
        JPanel hi = vbox(); JPanel nm = row(); nm.add(lbl(name(u), Font.BOLD, 24, fg));
        if (verified(u)) nm.add(tag("VERIFIED", GREEN));
        if (u.moderator) nm.add(tag("MODERATOR", GREEN));
        if (u.banned) nm.add(tag("BANNED", DANGER));
        put(hi, nm);
        put(hi, lbl("@" + u.username + "  \u2022  " + (u.banned ? "Banned account" : u.moderator ? "Campus moderator" : "Student account"), Font.BOLD, 12, fg));
        if (u.email != null && !u.email.isEmpty()) put(hi, lbl(u.email, Font.PLAIN, 11, fg));
        head.add(hi, BorderLayout.CENTER); card.add(head, BorderLayout.NORTH);

        JPanel body = vbox(); body.setBackground(PANEL); body.setBorder(new EmptyBorder(16, 22, 12, 22));
        JPanel stats = new JPanel(new GridLayout(1, 4, 8, 0)); stats.setOpaque(false);
        String[][] sv = {{"" + taught(u), "Sessions taught"}, {"" + u.teach.size(), "Skills offered"}, {"" + u.learn.size(), "Wants to learn"}, {reviews(u).isEmpty() ? "-" : String.format("%.1f", avg(u)), "Rating"}};
        for (String[] s : sv) { JPanel c = card(); c.add(lbl(s[0], Font.BOLD, 24, TXT), BorderLayout.CENTER); c.add(lbl(s[1], Font.PLAIN, 11, MUTED), BorderLayout.SOUTH); stats.add(c); }
        put(body, stats); gap(body, 14);

        put(body, sectionTitle("ABOUT")); gap(body, 6);
        put(body, lbl("<html><body style='width:640px'>" + (u.bio == null || u.bio.isEmpty() ? "No bio provided." : esc(u.bio)) + "</body></html>", Font.PLAIN, 13, TXT)); gap(body, 14);

        if (u.banned) {
            JPanel bc = card(); bc.setBorder(new EmptyBorder(10, 12, 10, 12)); JPanel bi = vbox(); JPanel bh = row();
            bh.add(lbl("BANNED ACCOUNT", Font.BOLD, 13, DANGER));
            if (u.bannedBy != null && !u.bannedBy.isEmpty()) bh.add(lbl("by @" + u.bannedBy + "  \u2022  " + ago(u.bannedAt), Font.PLAIN, 11, MUTED));
            put(bi, bh); if (u.banReason != null && !u.banReason.isEmpty()) put(bi, lbl("<html><body style='width:620px'>Reason: " + esc(u.banReason) + "</body></html>", Font.PLAIN, 12, TXT));
            bc.add(bi); put(body, bc); gap(body, 14);
        }

        put(body, sectionTitle("TEACHES")); gap(body, 6); put(body, skillTags(u.teach, GREEN)); gap(body, 12);
        put(body, sectionTitle("WANTS TO LEARN")); gap(body, 6); put(body, skillTags(u.learn, GOLD)); gap(body, 14);

        JPanel acct = card(); acct.setBorder(new EmptyBorder(10, 12, 10, 12)); JPanel ai = vbox();
        put(ai, lbl("ACCOUNT DETAILS", Font.BOLD, 12, TXT)); gap(ai, 4);
        put(ai, lbl("Username: @" + u.username + "     Credits: " + u.credits + "     Ratings: " + (u.publicRatings ? "Public" : "Private") + "     Nearby: " + (u.nearby ? "On" : "Off"), Font.PLAIN, 12, MUTED));
        acct.add(ai); put(body, acct); gap(body, 14);

        List<Report> history = S.reports.stream().filter(r -> r.target.equals(u.username)).sorted(Comparator.comparingLong((Report r) -> r.time).reversed()).limit(8).collect(Collectors.toList());
        put(body, sectionTitle("REPORTS AGAINST THIS ACCOUNT  (" + history.size() + ")")); gap(body, 6);
        if (history.isEmpty()) put(body, lbl("No reports have been filed against this account.", Font.PLAIN, 12, MUTED));
        else for (Report r : history) { put(body, reportHistoryRow(r)); gap(body, 5); }

        JScrollPane sp = new JScrollPane(body); sp.setBorder(null); sp.getViewport().setBackground(PANEL); sp.getVerticalScrollBar().setUnitIncrement(18);
        card.add(sp, BorderLayout.CENTER);
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); foot.setOpaque(false); foot.setBorder(new EmptyBorder(8, 22, 14, 22));
        foot.add(ghost("Close", d::dispose));
        if (u.banned) foot.add(btn("UNBAN ACCOUNT", () -> { unban(u.username); if (!u.banned) d.dispose(); }));
        else if (u.moderator || u.username.equals(me.username)) foot.add(tag(u.moderator ? "MODERATOR - CANNOT BAN" : "THIS IS YOUR ACCOUNT", MUTED));
        else foot.add(dangerButton("BAN ACCOUNT", () -> { ban(u.username, ""); if (u.banned) d.dispose(); }));
        card.add(foot, BorderLayout.SOUTH);

        outer.add(card); d.setContentPane(outer);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(820, 680); d.setResizable(true); d.setLocationRelativeTo(frame);
        busy++; try { d.setVisible(true); } finally { busy--; }
    }

    static JComponent skillTags(List<String> skills, Color col) {
        JPanel c = card(); c.setBorder(new EmptyBorder(6, 10, 6, 10)); JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4)); r.setOpaque(false);
        if (skills.isEmpty()) r.add(lbl("None listed.", Font.PLAIN, 12, MUTED)); else for (String s : skills) r.add(tag(s, col));
        c.add(r); return c;
    }

    static JComponent reportHistoryRow(Report r) {
        JPanel c = card(); c.setBorder(new EmptyBorder(8, 10, 8, 10)); JPanel inf = vbox(); JPanel h = row();
        h.add(tag(r.resolved ? "RESOLVED" : "OPEN", r.resolved ? GREEN : GOLD)); h.add(lbl(r.reason, Font.BOLD, 12, TXT));
        h.add(lbl(ago(r.time) + "  \u2022  filed by @" + r.by, Font.PLAIN, 11, MUTED)); put(inf, h);
        if (r.details != null && !r.details.trim().isEmpty()) put(inf, lbl("<html><body style='width:620px'>" + esc(r.details) + "</body></html>", Font.PLAIN, 12, MUTED));
        c.add(inf); return c;
    }

    /** Explicit confirmation dialog before a ban. Returns the ban reason, or null when cancelled. */
    static String banReasonDialog(User u, String initialReason) {
        final JDialog d = new JDialog(frame, "Confirm ban", true); final String[] out = {null};
        JPanel outer = new JPanel(new BorderLayout()); outer.setBackground(BG); outer.setBorder(new EmptyBorder(14, 14, 10, 10));
        Card card = new Card(); card.setLayout(new BorderLayout());
        JPanel head = new JPanel(); head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS)); head.setBackground(DANGER);
        head.setBorder(new CompoundBorder(new MatteBorder(0, 0, CB, 0, INK), new EmptyBorder(14, 24, 12, 24)));
        put(head, lbl("CONFIRM BAN", Font.BOLD, 24, Color.WHITE)); gap(head, 2); put(head, lbl("This takes effect immediately.", Font.PLAIN, 12, Color.WHITE));
        card.add(head, BorderLayout.NORTH);

        JPanel body = vbox(); body.setBackground(PANEL); body.setBorder(new EmptyBorder(18, 24, 8, 24));
        JPanel warn = card(); warn.setBorder(new EmptyBorder(10, 12, 10, 12)); JPanel wi = vbox();
        put(wi, lbl("You are about to ban " + name(u) + " (@" + u.username + ")", Font.BOLD, 14, TXT));
        put(wi, lbl("<html><body style='width:500px'>They will be signed out immediately and cannot sign in until a moderator unbans them. Every open report about this account will be closed.</body></html>", Font.PLAIN, 12, MUTED));
        warn.add(wi); put(body, warn); gap(body, 12);
        put(body, lbl("Ban reason (shown to the account)", Font.BOLD, 12, TXT)); gap(body, 3);
        JTextArea why = area(3, 30); why.setText(initialReason == null ? "" : initialReason); why.setPreferredSize(new Dimension(500, 74)); put(body, scroller(why)); gap(body, 8);
        JCheckBox ack = check("I understand this will sign the account out immediately.", false); put(body, ack); gap(body, 3);
        JLabel err = lbl(" ", Font.BOLD, 11, DANGER); put(body, err);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); foot.setOpaque(false); foot.setBorder(new EmptyBorder(8, 24, 14, 24));
        foot.add(ghost("CANCEL", d::dispose));
        foot.add(dangerButton("YES, BAN ACCOUNT", () -> {
            if (!ack.isSelected()) { err.setText("Tick the confirmation box to continue."); return; }
            String text = why.getText().trim();
            if (text.isEmpty()) { err.setText("Enter a short reason for the ban."); return; }
            out[0] = text; d.dispose();
        }));
        card.add(body, BorderLayout.CENTER); card.add(foot, BorderLayout.SOUTH);
        outer.add(card); d.setContentPane(outer);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setResizable(false); d.pack(); d.setSize(Math.max(600, d.getWidth()), d.getHeight()); d.setLocationRelativeTo(frame);
        busy++; try { d.setVisible(true); } finally { busy--; }
        return out[0];
    }

    public static void ban(String target, String reportReason) {
        if (me == null || !me.moderator) { info("Only campus moderators can ban accounts."); return; }
        User u = S.users.get(target);
        if (u == null) { info("That account no longer exists."); return; }
        if (u.username.equals(me.username)) { info("You cannot ban your own account."); return; }
        if (u.moderator) { info("Moderator accounts cannot be banned."); return; }
        String reason = banReasonDialog(u, reportReason);
        if (reason == null) return;
        long now = System.currentTimeMillis();
        u.banned = true; u.banReason = reason; u.bannedBy = me.username; u.bannedAt = now;
        for (Report r : S.reports) if (r.target.equals(target) && !r.resolved) { r.resolved = true; r.resolvedBy = me.username; r.resolvedAt = now; }
        save(); show("Moderation"); toast(name(u) + " was banned");
    }

    public static void unban(String target) {
        if (me == null || !me.moderator) { info("Only campus moderators can unban accounts."); return; }
        User u = S.users.get(target);
        if (u == null) { info("That account no longer exists."); return; }
        if (!u.banned) { info("That account is not banned."); return; }
        JPanel body = vbox(); put(body, lbl("Unban " + name(u) + "?", Font.BOLD, 18, TXT)); gap(body, 4);
        put(body, lbl("They will be able to sign in again.", Font.PLAIN, 12, MUTED));
        if (!ask("Unban account", body)) return;
        u.banned = false; u.banReason = ""; u.bannedBy = ""; u.bannedAt = 0;
        save(); show("Moderation"); toast(name(u) + " was unbanned");
    }

    public static void dismiss(Report r) {
        if (me == null || !me.moderator || r == null) return;
        r.resolved = true; r.resolvedBy = me.username; r.resolvedAt = System.currentTimeMillis();
        save(); show("Moderation"); toast("Report dismissed");
    }
}
