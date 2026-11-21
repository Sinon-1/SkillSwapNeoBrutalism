package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A student account. */
public class User implements java.io.Serializable {
    private static final long serialVersionUID = -4510827704168504928L;   // same as before lastSeen was added: keeps old lan-data.bin readable
    public String username, realName, email, passHash, bio = "";
    public boolean useRealName, publicRatings = true, nearby, demo;
    public List<String> teach = new ArrayList<>(), learn = new ArrayList<>();
    public int credits = 5;
    /** Profile picture: a small square JPEG (about 256 px), or null to show the coloured initial. */
    public byte[] photo;
    /** Nickname for this user in group chats (null = use normal name). */
    public String nickname;
    /** Personal nicknames for people in group chats, keyed by group id + ':' + username. Only the owner sees them. */
    public Map<String, String> gcNicknames = new LinkedHashMap<>();
    /** Epoch millis of the last heartbeat from this user's app (0 = signed out). Not part of the row fingerprint. */
    public long lastSeen;
    /** Campus moderator: can review reports and ban accounts. */
    public boolean moderator, banned;
    public String banReason = "", bannedBy = "";
    public long bannedAt;
}
