package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A report/flag filed against a user. */
public class Report implements java.io.Serializable {
    public int id; public String by, target, reason, details; public long time;
    public boolean resolved; public String resolvedBy = ""; public long resolvedAt;
}
