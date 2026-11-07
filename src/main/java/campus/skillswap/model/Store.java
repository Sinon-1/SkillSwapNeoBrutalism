package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** Snapshot of everything loaded from the database. */
public class Store implements java.io.Serializable {
    public Map<String, User> users = new LinkedHashMap<>();
    public List<Post> posts = new ArrayList<>(); public List<Sess> sessions = new ArrayList<>();
    public List<Msg> msgs = new ArrayList<>(); public List<Report> reports = new ArrayList<>();
    public String lastSync = "never";
}
