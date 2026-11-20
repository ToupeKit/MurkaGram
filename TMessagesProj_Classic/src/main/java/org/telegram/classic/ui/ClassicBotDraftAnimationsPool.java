package org.telegram.classic.ui;

import android.util.SparseIntArray;

import androidx.annotation.Nullable;

import org.telegram.messenger.BotForumHelper;

// MurkaGram: dual UI. Copy of BotForumHelper.BotDraftAnimationsPool (core-13) holding the classic MultiLayoutTypingAnimator.
public class ClassicBotDraftAnimationsPool {
    private final BotForumHelper.DialogTopicIdKeyMap<MultiLayoutTypingAnimator> animators = new BotForumHelper.DialogTopicIdKeyMap<>();
    private final SparseIntArray ids = new SparseIntArray();   // messageId -> pendingId;

    @Nullable
    public MultiLayoutTypingAnimator getAnimator(long dialogId, int messageId, boolean allowCreate) {
        final int animatorId = messageId > 0 ? ids.get(messageId, 0) : messageId;
        if (animatorId == 0) {
            return null;
        }

        MultiLayoutTypingAnimator animator = animators.get(dialogId, 0, animatorId);
        if (animator == null && allowCreate) {
            animator = new MultiLayoutTypingAnimator();
            animators.put(dialogId, 0, animatorId, animator);
        }

        return animator;
    }

    public void bind(int pendingMessageId, int messageId) {
        ids.put(messageId, pendingMessageId);
    }

    public void removeAnimator(long dialogId, int messageId) {
        final int animatorId = messageId > 0 ? ids.get(messageId, 0) : messageId;
        if (animatorId == 0) {
            return;
        }

        animators.remove(dialogId, 0, animatorId);
    }
}
