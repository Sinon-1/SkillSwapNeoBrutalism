package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A chat message. */
public class Msg implements java.io.Serializable {
    public int id; public String from, to, text; public long time;
    /** edited = text was changed after sending; deleted = unsent for everyone (text is wiped); hiddenBy = comma-separated users who removed it only for themselves. */
    public boolean edited, deleted; public String hiddenBy = "";
    public boolean hiddenFor(String user) { return hiddenBy != null && Arrays.asList(hiddenBy.split(",")).contains(user); }
    public void hideFor(String user) { if (!hiddenFor(user)) hiddenBy = (hiddenBy == null || hiddenBy.isEmpty()) ? user : hiddenBy + "," + user; }
}
