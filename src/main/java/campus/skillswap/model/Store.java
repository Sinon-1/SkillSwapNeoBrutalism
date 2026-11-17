package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** Snapshot of everything loaded from the database. */
public class Store implements java.io.Serializable {
    private static final long serialVersionUID = 466624548218135050L;   // same as before groups were added: keeps old lan-data.bin readable
    public Map<String, User> users = new LinkedHashMap<>();
    public List<Post> posts = new ArrayList<>(); public List<Sess> sessions = new ArrayList<>();
    public List<Msg> msgs = new ArrayList<>(); public List<Report> reports = new ArrayList<>();
    public String lastSync = "never";
    /** Group chats (an old lan-data.bin has none, so readResolve gives it an empty list). */
    public List<Group> groups = new ArrayList<>();
    private Object readResolve() { if (groups == null) groups = new ArrayList<>(); return this; }
}
