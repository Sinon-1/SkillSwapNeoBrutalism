package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A chat message. */
public class Msg implements java.io.Serializable {
    private static final long serialVersionUID = 3770658303684082163L;
    public int id; public String from, to, text; public long time;
    /** edited = text was changed after sending; deleted = unsent for everyone (text is wiped); hiddenBy = comma-separated users who removed it only for themselves. */
    public boolean edited, deleted; public String hiddenBy = "";
    public String fileName = "", fileKind = "", fileMime = ""; public long fileSize; public transient String fileError;
    public boolean hasFile() { return fileName != null && !fileName.isEmpty(); }
    public String fileLabel() { return "video".equals(fileKind) ? "video" : "image".equals(fileKind) ? "photo" : "file"; }
    public boolean hiddenFor(String user) { return hiddenBy != null && Arrays.asList(hiddenBy.split(",")).contains(user); }
    public void hideFor(String user) { if (!hiddenFor(user)) hiddenBy = (hiddenBy == null || hiddenBy.isEmpty()) ? user : hiddenBy + "," + user; }
}
