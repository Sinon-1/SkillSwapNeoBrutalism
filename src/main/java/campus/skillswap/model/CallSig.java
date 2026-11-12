package campus.skillswap.model;

/** A short call-setup message (ring, cancel, decline, busy, hang up). Media itself never goes through this. */
public class CallSig implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public String callId, from, to, kind, host = "";   // kind: INVITE | CANCEL | DECLINE | BUSY | END
    public int port;
    public boolean video;
    public long ts;
}
