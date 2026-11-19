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
import campus.skillswap.ui.media.Sound;
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
        Pg p = pg("Settings", "Appearance, notification sounds and database connection", null);
        JPanel ap = card(); JPanel ai = vbox();
        put(ai, lbl("Appearance", Font.BOLD, 14, TXT)); put(ai, lbl("Choose how SkillSwap Campus looks on this computer. Your choice is remembered.", Font.PLAIN, 12, MUTED)); gap(ai, 6);
        JPanel r = row(); r.add(chip("Dark mode", dark, () -> { if (!dark) switchTheme(true); })); r.add(chip("Light mode", !dark, () -> { if (dark) switchTheme(false); })); put(ai, r);
        ap.add(ai); put(p.body, ap); gap(p.body, 10);
        JPanel sp = card(); JPanel si = vbox();
        put(si, lbl("Notification sounds", Font.BOLD, 14, TXT));
        put(si, lbl("Choose what plays when messages, reminders or calls arrive. Turn each sound on or off, pick a file, or record your own.", Font.PLAIN, 12, MUTED)); gap(si, 8);
        put(si, soundRow(Sound.Kind.GENERAL)); gap(si, 8);
        put(si, soundRow(Sound.Kind.CALL)); gap(si, 4);
        put(si, lbl("WAV files play directly. MP3, M4A and OGG need ffmpeg installed.", Font.PLAIN, 11, MUTED));
        sp.add(si); put(p.body, sp); gap(p.body, 10);
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
    /** One on/off row with its own default, file, recording and test controls. */
    static JPanel soundRow(Sound.Kind kind) {
        JPanel box = vbox();
        boolean call = kind == Sound.Kind.CALL, off = Sound.isOff(kind);
        put(box, lbl(call ? "CALLS" : "MESSAGES & REMINDERS", Font.BOLD, 11, MUTED));
        JPanel head = row();
        JCheckBox on = check("Sound on", !off);
        on.addActionListener(e -> { Sound.setEnabled(kind, on.isSelected()); show("Settings"); });
        head.add(on);
        head.add(lbl("Current: " + Sound.label(kind), Font.PLAIN, 12, off ? MUTED : TXT));
        put(box, head);
        JPanel actions = row();
        JButton def = ghost("Default " + (call ? "ringtone" : "chime"), () -> { Sound.setSetting(kind, Sound.DEFAULT); show("Settings"); Sound.test(kind, err -> { if (err != null) info(err); }); });
        JButton own = ghost("My own sound...", () -> chooseSound(kind));
        JButton rec = ghost("Record 5 sec...", () -> recordSound(kind));
        JButton test = ghost("Test", () -> Sound.test(kind, err -> { if (err != null) info(err); else toast(call ? "Call ringtone is working" : "Notification sound is working"); }));
        if (off) { def.setEnabled(false); own.setEnabled(false); rec.setEnabled(false); test.setEnabled(false); }
        actions.add(def); actions.add(own); actions.add(rec); actions.add(test);
        put(box, actions);
        return box;
    }
    /** Pick any audio file; it is checked (and MP3/M4A/OGG converted once) before it is kept. */
    static void chooseSound(Sound.Kind kind) {
        boolean call = kind == Sound.Kind.CALL;
        JFileChooser fc = new JFileChooser(campus.skillswap.ui.media.FileFinder.lastDir());
        fc.setDialogTitle(call ? "Choose your call ringtone" : "Choose your notification sound");
        fc.setFileFilter(new FileNameExtensionFilter("Audio files (MP3, M4A, WAV, OGG, FLAC, AIFF, WMA...)",
                "mp3", "mp2", "m4a", "m4b", "aac", "wav", "wave", "ogg", "oga", "opus", "flac", "wma",
                "aiff", "aif", "aifc", "au", "snd", "caf", "amr", "ac3"));
        campus.skillswap.ui.media.FileFinder finder = campus.skillswap.ui.media.FileFinder.install(fc); fc.setPreferredSize(new Dimension(900, 580));
        int res = fc.showOpenDialog(frame); finder.stopPlayer();
        if (res != JFileChooser.APPROVE_OPTION) return;
        campus.skillswap.ui.media.FileFinder.remember(fc);
        File f = fc.getSelectedFile();
        if (f == null || !f.isFile()) return;
        if (f.length() > 20L * 1024 * 1024) { info("Please choose a sound under 20 MB."); return; }
        toast("Checking this sound...");
        Sound.prepare(f, problem -> {
            if (problem != null) { info("That sound can't be used:\n" + problem); return; }
            String before = Sound.setting(kind);
            Sound.setSetting(kind, f.getAbsolutePath());
            show("Settings");
            Sound.test(kind, err -> {
                if (err != null) { Sound.setSetting(kind, before); show("Settings"); info("That sound could not be used:\n" + err); }
                else toast(call ? "Call ringtone set" : "Notification sound set");
            });
        });
    }
    /** Records a short custom sound from the microphone. */
    static void recordSound(Sound.Kind kind) {
        boolean call = kind == Sound.Kind.CALL;
        String what = call ? "ringtone" : "sound";
        if (JOptionPane.showConfirmDialog(frame, "Record a 5-second " + what + " from your microphone?\n\nPress OK, then make the sound you want. It starts immediately.", "Record your own " + what,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        toast("Recording... make your " + what + " now");
        Sound.recordMySound(kind, f -> {
            Sound.setSetting(kind, f.getAbsolutePath());
            show("Settings");
            Sound.test(kind, err -> { if (err != null) info("Recorded, but it could not be played:\n" + err); else toast(call ? "Your call ringtone is ready" : "Your notification sound is ready"); });
        }, err -> info("Could not record a sound:\n" + err));
    }
}
