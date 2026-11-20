package org.telegram.classic.ui.Components.poll.attached;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.Components.poll.PollAttachedMedia;

// MurkaGram: stub (core-13) — data only, the 13.2.2 drawing code needs the new UI
public class PollAttachedMediaGallery extends PollAttachedMedia {
    public final MediaController.PhotoEntry photoEntry;
    public final SendMessagesHelper.SendingMediaInfo sendingMediaInfo;

    public PollAttachedMediaGallery(SendMessagesHelper.SendingMediaInfo sendingMediaInfo) {
        this.sendingMediaInfo = sendingMediaInfo;
        this.photoEntry = null;
    }
}
