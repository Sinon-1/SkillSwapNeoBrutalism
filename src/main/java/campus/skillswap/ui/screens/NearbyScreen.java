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
import static campus.skillswap.ui.screens.CertsScreen.*;
import static campus.skillswap.ui.screens.BookingsScreen.*;
import static campus.skillswap.ui.screens.MessagesScreen.*;
import static campus.skillswap.ui.screens.ProfileScreen.*;
import static campus.skillswap.ui.media.Videos.*;
import static campus.skillswap.ui.media.Reels.*;

/** "Nearby now" board. */
public class NearbyScreen {

    // ====================== NEARBY NOW ======================
    public static JComponent nearbyPage() {
        Pg p = pg("Nearby Now", "Students currently open to an impromptu swap", null);
        JCheckBox me2 = check("I'm open to an impromptu swap right now", me.nearby); me2.addActionListener(e -> { me.nearby = me2.isSelected(); save(); show("Nearby"); });
        JPanel c0 = card(); c0.add(me2); put(p.body, c0); gap(p.body, 8); int n = 0;
        for (User u : S.users.values()) if (u != me && u.nearby) { n++; JPanel c = card(); c.add(avatarWrap(name(u), 40), BorderLayout.WEST); JPanel inf = vbox(); JPanel h = row();
            h.add(lbl(name(u), Font.BOLD, 13, TXT)); h.add(tag("\u25CF ONLINE", GREEN)); put(inf, h); put(inf, lbl("Teaches: " + String.join(", ", u.teach) + "   \u2022   Wants: " + String.join(", ", u.learn), Font.PLAIN, 12, MUTED)); c.add(inf);
            JPanel b = row(); if (!u.teach.isEmpty()) b.add(btn("SWAP NOW", () -> book(u.username, me.username, u.teach.get(0)))); b.add(ghost("Message", () -> { chatWith = u.username; show("Messages"); })); c.add(b, BorderLayout.EAST); put(p.body, c); gap(p.body, 8); }
        if (n == 0) put(p.body, lbl("Nobody else is open right now. Check back soon!", Font.PLAIN, 12, MUTED));
        return p.root;
    }
}
