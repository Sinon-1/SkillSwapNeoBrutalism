package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A feed post (offer/request), optionally with a reel video. */
public class Post implements java.io.Serializable {
    private static final long serialVersionUID = -7930209640883248915L;   // keep LAN data created before videoStamp readable
    public int id; public String author, type, skill, category, desc, videoName; public long time, videoSize;
    /** Changes whenever the reel video is uploaded/replaced, so cached downloads and conversions are not reused for a different file. */
    public long videoStamp;
    public Set<String> likes = new HashSet<>();
}
