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
    /** Epoch millis of the last heartbeat from this user's app (0 = signed out). Not part of the row fingerprint. */
    public long lastSeen;
}
