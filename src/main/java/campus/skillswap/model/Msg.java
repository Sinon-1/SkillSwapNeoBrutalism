package campus.skillswap.model;

import java.time.LocalDateTime;
import java.util.*;

/** A chat message. */
public class Msg implements java.io.Serializable { public int id; public String from, to, text; public long time; }
