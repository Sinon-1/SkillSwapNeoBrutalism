package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A tutoring session between a teacher and a learner. */
public class Sess implements java.io.Serializable {
    private static final long serialVersionUID = -711163742617427901L;   // unchanged by videoCall, so saved LAN data still loads
    /** True when the learner asked for the session to be held as an in-app video call (Online sessions only). */
    public boolean videoCall;
    public int id, rating; public String teacher, learner, skill, mode, status, requester, review = "";
    public LocalDateTime at;
}
