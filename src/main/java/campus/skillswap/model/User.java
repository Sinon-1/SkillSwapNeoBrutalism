package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A student account. */
public class User implements java.io.Serializable {
    public String username, realName, email, passHash, bio = "";
    public boolean useRealName, publicRatings = true, nearby, demo;
    public List<String> teach = new ArrayList<>(), learn = new ArrayList<>();
    public int credits = 5;
}
