package org.telegram.classic.ui.Components.poll.attached;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.Components.poll.PollAttachedMedia;

// MurkaGram: stub (core-13) — data only, the 13.2.2 drawing code needs the new UI
public class PollAttachedMediaMusic extends PollAttachedMedia {
    public final MessageObject messageObject;

    public PollAttachedMediaMusic(MessageObject messageObject) {
        this.messageObject = messageObject;
    }
}
