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
import static campus.skillswap.ui.screens.LoginScreen.*;
import static campus.skillswap.ui.screens.ConnectionScreen.*;
import static campus.skillswap.ui.screens.SettingsScreen.*;
import static campus.skillswap.ui.screens.FeedScreen.*;
import static campus.skillswap.ui.screens.SearchScreen.*;
import static campus.skillswap.ui.screens.BookingsScreen.*;
import static campus.skillswap.ui.screens.NearbyScreen.*;
import static campus.skillswap.ui.screens.MessagesScreen.*;
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** Verified certificates / badges. */
public class CertsScreen {

    // ====================== CERTIFICATES ======================
    public static JComponent certsPage() {
        List<String[]> bs = badges(me); long earned = bs.stream().filter(b -> b[2].equals("Earned")).count(), prog = bs.stream().filter(b -> b[2].equals("In progress")).count(), lock = bs.size() - earned - prog;
        Pg p = pg("Verified Certificates", "Credentials earned by teaching and reviews", btn("+ Request", () -> {
            if (verified(me)) info("You already hold the Top Rated Tutor verification badge. Great work!");
            else info("Verification is automatic once you have 3+ reviews with a 4.5+ average.\nYou currently have " + reviews(me).size() + " review(s), average " + String.format("%.1f", avg(me)) + ".\nComplete more sessions to qualify."); }));
        JPanel stats = new JPanel(new GridLayout(1, 3, 8, 0)); stats.setOpaque(false);
        Object[][] sv = {{"" + earned, "Earned", GREEN}, {"" + prog, "In progress", GOLD}, {"" + lock, "Locked", MUTED}};
        for (Object[] s : sv) { JPanel c = card(); c.add(lbl((String) s[0], Font.BOLD, 22, (Color) s[2]), BorderLayout.CENTER); c.add(lbl((String) s[1], Font.PLAIN, 11, MUTED), BorderLayout.SOUTH); stats.add(c); }
        put(p.body, stats); gap(p.body, 8);
        JPanel chips = row(); for (String x : new String[]{"All", "Earned", "In progress", "Locked"}) chips.add(chip(x, x.equals(certFilter), () -> { certFilter = x; show("Certs"); })); put(p.body, chips); gap(p.body, 6);
        for (String[] b : bs) {
            if (!certFilter.equals("All") && !b[2].equals(certFilter)) continue; boolean ok = b[2].equals("Earned"); Color sc = ok ? GREEN : b[2].equals("In progress") ? GOLD : MUTED;
            JPanel c = card(); c.add(swatchWrap(sc, 46), BorderLayout.WEST); JPanel inf = vbox(); JPanel h = row(); h.add(lbl(b[0], Font.BOLD, 14, TXT)); h.add(tag(b[2].toUpperCase(), sc)); put(inf, h);
            put(inf, lbl(ok ? "Verified by SkillSwap Peer Learning Office" : b[1], Font.PLAIN, 12, MUTED));
            put(inf, lbl(ok ? "Issued " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMM yyyy")) : "Progress: " + b[3], Font.PLAIN, 11, MUTED)); c.add(inf);
            if (ok) { JPanel r = row(); r.add(ghost("View", () -> info("CERTIFICATE OF ACHIEVEMENT\n\n" + name(me) + "\nhas earned: " + b[0] + "\n" + b[1] + "\n\nIssued " + LocalDate.now() + " by SkillSwap Campus")));
                r.add(ghost("Share", () -> { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(name(me) + " earned " + b[0] + " on SkillSwap Campus"), null); toast("Copied to clipboard"); })); c.add(r, BorderLayout.EAST); }
            put(p.body, c); gap(p.body, 8);
        }
        return p.root;
    }
}
