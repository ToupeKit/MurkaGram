package org.telegram.ui.Stars;

import org.telegram.classic.ui.ChatActivity;
import org.telegram.messenger.MessageObject;

// MurkaGram: dual UI. Paid (star) reactions: StarsController.PendingPaidReactions keeps a modern ChatActivity and builds its
// undo bulletin from it, so the classic chat cannot use it directly. TODO(E5/E6, plan §2.4 p.12): route through UiBridge.
public final class ClassicStarsController {

    private ClassicStarsController() {
    }

    public static StarsController.PendingPaidReactions sendPaidReaction(StarsController self, MessageObject messageObject, ChatActivity chatActivity, long amount, boolean affect, boolean checkBalance, Long peer) {
        return null;
    }
}
