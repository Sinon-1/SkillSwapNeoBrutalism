package campus.skillswap.model;

import java.util.*;

/**
 * A group chat. Its messages are ordinary Msg rows whose "to" is {@link #key()} ("g:" + id),
 * so the existing messages table, editing, unsend and sync all work for groups without changes.
 */
public class Group implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public static final String PREFIX = "g:";
    public int id; public String name = "", creator = ""; public long created;
    public List<String> members = new ArrayList<>();

    public String key() { return PREFIX + id; }
    public boolean has(String user) { return user != null && members.contains(user); }
    public static boolean isKey(String s) { return s != null && s.startsWith(PREFIX); }
}
