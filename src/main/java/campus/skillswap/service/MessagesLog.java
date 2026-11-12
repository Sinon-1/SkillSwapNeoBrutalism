package campus.skillswap.service;

import campus.skillswap.model.Msg;
import static campus.skillswap.core.AppState.*;
import static campus.skillswap.data.Database.*;
import static campus.skillswap.ui.AppShell.*;

/** Adds call history lines ("Video call (2:31)", "Missed video call") to the chat, like Messenger. */
class MessagesLog {
    static void add(String to, String text) {
        Msg m = new Msg(); m.id = nextId(); m.from = me.username; m.to = to; m.text = text; m.time = System.currentTimeMillis(); S.msgs.add(m); save();
        if (page.equals("Messages") && busy == 0) show("Messages");
    }
}
