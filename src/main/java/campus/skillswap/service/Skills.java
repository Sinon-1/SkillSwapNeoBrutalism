package campus.skillswap.service;

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

/** Skill categories. */
public class Skills {

    // ---- skill categories ----
    public static final Map<String, String> CAT = new LinkedHashMap<>();
    static {
        String[][] d = {{"Tech", "Java", "Python", "JavaScript", "C++", "Web Design"}, {"Music", "Guitar", "Piano", "Singing"},
                {"Arts", "Painting", "Drawing", "Photography"}, {"Academics", "Calculus", "Statistics", "Physics", "Chemistry"},
                {"Languages", "Russian", "Spanish", "Japanese"}, {"Cooking", "Baking", "Cooking"}};
        for (String[] r : d) for (int i = 1; i < r.length; i++) CAT.put(r[i].toLowerCase(), r[0]);
    }
    public static String cat(String s) { return CAT.getOrDefault(s.toLowerCase().trim(), "Other"); }
    public static final String[] CATS = {"All", "Tech", "Music", "Arts", "Academics", "Languages", "Cooking", "Other"};
}
