package org.telegram.classic.ui.Components.poll.attached;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.poll.PollAttachedMedia;

// MurkaGram: stub (core-13) — data only, the 13.2.2 drawing code needs the new UI
public class PollAttachedMediaLocation extends PollAttachedMedia {
    public final TLRPC.MessageMedia media;

    public PollAttachedMediaLocation(TLRPC.MessageMedia location) {
        this.media = location;
    }
}
