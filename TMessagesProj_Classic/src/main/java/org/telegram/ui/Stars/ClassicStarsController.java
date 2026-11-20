package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;

import org.telegram.classic.ui.ActionBar.Theme;
import org.telegram.classic.ui.ChatActivity;
import org.telegram.classic.ui.Components.Bulletin;
import org.telegram.classic.ui.Components.BulletinFactory;
import org.telegram.classic.ui.Components.LayoutHelper;
import org.telegram.classic.ui.LaunchActivity;
import org.telegram.classic.ui.Stars.StarReactionsOverlay;
import org.telegram.classic.ui.Stars.StarsIntroActivity;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;

// MurkaGram: dual UI. Paid (star) reactions of the classic chat: the shared StarsController.PendingPaidReactions logic
// (sending, undo timer, balance) with the 12.2.10 undo bulletin and "stars needed" sheet built here from the classic UI
// (StarsController.PaidReactionsHost).
public final class ClassicStarsController {

    private ClassicStarsController() {
    }

    public static StarsController.PendingPaidReactions sendPaidReaction(StarsController self, MessageObject messageObject, ChatActivity chatActivity, long amount, boolean affect, boolean checkBalance, Long peer) {
        if (self == null || messageObject == null || chatActivity == null) {
            return null;
        }
        return self.sendPaidReaction(messageObject, new Host(chatActivity), amount, affect, checkBalance, peer);
    }

    // 12.2.10: pending.setOverlay(overlay)
    public static void setOverlay(StarsController.PendingPaidReactions pending, StarReactionsOverlay overlay) {
        if (pending != null && pending.hostBulletin instanceof ClassicBulletin) {
            ((ClassicBulletin) pending.hostBulletin).overlay = overlay;
        }
    }

    private static final class Host implements StarsController.PaidReactionsHost {

        private final ChatActivity chatActivity;

        Host(ChatActivity chatActivity) {
            this.chatActivity = chatActivity;
        }

        @Override
        public Context getContext() {
            if (chatActivity.getContext() != null) {
                return chatActivity.getContext();
            }
            if (LaunchActivity.instance != null && !LaunchActivity.instance.isFinishing()) {
                return LaunchActivity.instance;
            }
            return ApplicationLoader.applicationContext;
        }

        @Override
        public StarsController.PaidReactionsBulletin createBulletin(StarsController.PendingPaidReactions pending) {
            return new ClassicBulletin(chatActivity, getContext(), pending);
        }

        @Override
        public void showStarsNeeded(long stars, String name, Runnable whenPurchased) {
            final Context context = getContext();
            if (context == null) {
                return;
            }
            new StarsIntroActivity.StarsNeededSheet(context, chatActivity.getResourceProvider(), stars, StarsIntroActivity.StarsNeededSheet.TYPE_REACTIONS, name, whenPurchased, 0).show();
        }
    }

    // the 12.2.10 PendingPaidReactions bulletin
    private static final class ClassicBulletin implements StarsController.PaidReactionsBulletin {

        private final Bulletin bulletin;
        private final Bulletin.TwoLineAnimatedLottieLayout bulletinLayout;
        private final Bulletin.TimerView timerView;
        StarReactionsOverlay overlay;

        ClassicBulletin(ChatActivity chatActivity, Context context, StarsController.PendingPaidReactions pending) {
            bulletinLayout = new Bulletin.TwoLineAnimatedLottieLayout(context, chatActivity.themeDelegate);
            bulletinLayout.setAnimation(R.raw.stars_topup);
            final Bulletin.UndoButton bulletinButton = new Bulletin.UndoButton(context, true, false, chatActivity.themeDelegate);
            bulletinButton.setText(LocaleController.getString(R.string.StarsSentUndo));
            bulletinButton.setUndoAction(pending::cancel);
            timerView = new Bulletin.TimerView(context, chatActivity.themeDelegate);
            timerView.timeLeft = StarsController.REACTIONS_TIMEOUT;
            timerView.setColor(Theme.getColor(Theme.key_undo_cancelColor, chatActivity.themeDelegate));
            bulletinButton.addView(timerView, LayoutHelper.createFrame(20, 20, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
            bulletinButton.undoTextView.setPadding(dp(12), dp(8), dp(20 + 10), dp(8));
            bulletinLayout.setButton(bulletinButton);
            bulletin = BulletinFactory.of(chatActivity).create(bulletinLayout, -1);
            bulletin.hideAfterBottomSheet = false;
        }

        @Override
        public void setTexts(CharSequence title, CharSequence subtitle) {
            if (title != null) {
                bulletinLayout.titleTextView.setText(title);
            }
            if (subtitle != null) {
                bulletinLayout.subtitleTextView.cancelAnimation();
                bulletinLayout.subtitleTextView.setText(subtitle, true);
            }
        }

        @Override
        public void resetTimer() {
            timerView.timeLeft = StarsController.REACTIONS_TIMEOUT;
        }

        @Override
        public void show(Runnable onHide) {
            bulletin.show(true);
            bulletin.setOnHideListener(onHide);
        }

        @Override
        public void hide() {
            bulletin.hide();
        }

        @Override
        public void hideOverlay(MessageObject messageObject, boolean onlyIfShowing) {
            if (overlay != null && (!onlyIfShowing || overlay.isShowing(messageObject))) {
                overlay.hide();
            }
        }
    }
}
