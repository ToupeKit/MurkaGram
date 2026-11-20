package org.telegram.classic.ui.Components.poll.attached;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.poll.PollAttachedMedia;

// MurkaGram: stub (core-13) — data only, the 13.2.2 drawing code needs the new UI
public class PollAttachedMediaSticker extends PollAttachedMedia {
    public final TLRPC.Document sticker;
    public final Object parent;
    public final boolean isEmoji;

    public PollAttachedMediaSticker(TLRPC.Document sticker, Object parent) {
        this.sticker = sticker;
        this.parent = parent;
        this.isEmoji = MessageObject.isAnimatedEmoji(sticker);
    }
}
