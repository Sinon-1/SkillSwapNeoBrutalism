package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A feed post (offer/request), optionally with a reel video. */
public class Post implements java.io.Serializable {
    public int id; public String author, type, skill, category, desc, videoName; public long time, videoSize;
    public Set<String> likes = new HashSet<>();
}
