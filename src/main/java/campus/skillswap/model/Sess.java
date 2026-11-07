package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A tutoring session between a teacher and a learner. */
public class Sess implements java.io.Serializable {
    public int id, rating; public String teacher, learner, skill, mode, status, requester, review = "";
    public LocalDateTime at;
}
