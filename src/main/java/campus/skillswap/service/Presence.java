package campus.skillswap.service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import campus.skillswap.model.*;
import campus.skillswap.net.LanSync;
import static campus.skillswap.core.AppState.*;
import static campus.skillswap.data.Database.*;
import static campus.skillswap.ui.AppShell.*;
import static campus.skillswap.ui.media.Videos.videoOpen;

/**
 * Real online/offline status. Every signed-in app sends a heartbeat every BEAT_MS;
 * a user counts as online only while their last heartbeat is younger than TTL_MS.
 * "Nearby now" additionally requires the user to have switched on "open to an impromptu swap".
 */
public class Presence {
    public static final int BEAT_MS = 15_000;
    public static final long TTL_MS = 45_000;
    static Set<String> shown = new HashSet<>();

    public static boolean isOnline(User u) { return u != null && u.lastSeen > 0 && System.currentTimeMillis() - u.lastSeen < TTL_MS; }
    public static boolean isNearby(User u) { return u != null && u.nearby && isOnline(u); }

    /** Snapshot of who is online / nearby right now (used to decide whether a screen needs redrawing). */
    public static Set<String> state(Store st) {
        Set<String> s = new HashSet<>();
        for (User u : st.users.values()) { if (isOnline(u)) s.add(u.username); if (isNearby(u)) s.add("N:" + u.username); }
        return s;
    }

    /** Called every BEAT_MS on the Swing thread while the app is open. */
    public static void tick() { beat(); refresh(); }

    public static void beat() {
        if (me == null || !connected()) return;
        long now = System.currentTimeMillis(); me.lastSeen = now;
        write(me.username, now, false);
    }

    /** Mark the signed-in user offline and not-nearby (logout / window close). */
    public static void goOffline() {
        if (me == null || !connected()) return;
        me.lastSeen = 0; me.nearby = false;
        write(me.username, 0, true);
    }

    static void write(String user, long ts, boolean sync) {
        if (LanSync.active()) { LanSync.presence(user, ts, sync); return; }
        if (db == null) return;
        try (PreparedStatement p = db.prepareStatement(ts == 0 ? "UPDATE users SET last_seen=0, nearby=FALSE WHERE username=?" : "UPDATE users SET last_seen=? WHERE username=?")) {
            if (ts == 0) p.setString(1, user); else { p.setLong(1, ts); p.setString(2, user); }
            p.executeUpdate();
        } catch (SQLException e) { /* best effort: the next heartbeat retries */ }
    }

    /** Redraw the current page only if somebody's online/nearby state actually changed. */
    public static void refresh() {
        if (me == null || S == null) return;
        Set<String> now = state(S);
        if (now.equals(shown)) return;
        shown = now;
        if (busy > 0 || videoOpen()) { shown = new HashSet<>(); return; }       // retry on the next tick
        if (page.equals("Nearby") || page.equals("Messages") || page.equals("Search")) show(page);
    }
}
