package org.telegram.messenger;

import org.telegram.messenger.support.LongSparseLongArray;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.bots.BotWebViewAttachedSheet;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.bots.WebViewRequestProps;

public class BotGuardHelper extends BaseController {
    private BotGuardHelper(int num) {
        super(num);
    }

    private final LongSparseLongArray queryIdToBotId = new LongSparseLongArray();

    public void openGuardBotWebApp(long dialogId, long guardBotId, long queryId) {
        openGuardBotWebApp(dialogId, guardBotId, queryId, false);
    }

    private void openGuardBotWebApp(long dialogId, long guardBotId, long queryId, boolean confirmed) {
        if (UiEdition.activity() == null) { // MurkaGram: dual UI
            return;
        }
        final UiHost lastFragment = UiEdition.bridge().lastFragment(); // MurkaGram: dual UI
        if (lastFragment == null) {
            return;
        }

        final TLRPC.User botUser = getMessagesController().getUser(guardBotId);

        if (!confirmed) {
            if (SharedPrefsHelper.isWebViewConfirmShown(currentAccount, guardBotId) || getMessagesController().whitelistedBots.contains(guardBotId)) {
                openGuardBotWebApp(dialogId, guardBotId, queryId, true);
            } else {
                UiEdition.bridge().showBotLaunchAlert(lastFragment, botUser, () -> { // MurkaGram: dual UI
                    openGuardBotWebApp(dialogId, guardBotId, queryId, true);
                    SharedPrefsHelper.setWebViewConfirmShown(currentAccount, guardBotId, true);
                }, () -> {});
            }
            return;
        }

        queryIdToBotId.put(queryId, guardBotId);

        final UiHost parentFragment = UiEdition.bridge().lastFragment(); // MurkaGram: dual UI
        UiEdition.bridge().openGuardBotWebApp(parentFragment, currentAccount, dialogId, guardBotId, queryId); // MurkaGram: dual UI
    }

    public void closeGuardBotWebApp(long dialogId, long queryId, TLRPC.JoinChatBotResult result) {
        final long guardBotId = queryIdToBotId.get(queryId, 0);

        getNotificationCenter().postNotificationName(NotificationCenter.guardBotDecisionResult, new GuardBotDecisionResultNotification(dialogId, guardBotId, queryId, result));
        if (BotWebViewSheet.activeSheets != null) {
            for (BotWebViewSheet sheet : BotWebViewSheet.activeSheets) {
                if (sheet.isGuardBotTab(dialogId, queryId)) {
                    sheet.dismiss();
                    break;
                }
            }
        }

        /*final ArrayList<BottomSheetTabs.WebTabData> tabs = bottomSheetTabs.getTabs(currentAccount);
        for (BottomSheetTabs.WebTabData tab : tabs) {
            if (tab.props != null && tab.props.type == BotWebViewAttachedSheet.TYPE_WEB_VIEW_GUARD && tab.props.peerId == dialogId) {
                if (tab.props.response instanceof TLRPC.TL_webViewResultUrl) {
                    final long bQueryId = ((TLRPC.TL_webViewResultUrl) tab.props.response).query_id;
                }
            }
        }*/
    }

    public static class GuardBotDecisionResultNotification {
        public final long dialogId;
        public final long guardBotId;
        public final long queryId;
        public final TLRPC.JoinChatBotResult result;

        public GuardBotDecisionResultNotification(long dialogId, long guardBotId, long queryId, TLRPC.JoinChatBotResult result) {
            this.dialogId = dialogId;
            this.guardBotId = guardBotId;
            this.queryId = queryId;
            this.result = result;
        }
    }



    private static volatile BotGuardHelper[] Instance = new BotGuardHelper[UserConfig.MAX_ACCOUNT_COUNT];
    public static BotGuardHelper getInstance(final int num) {
        BotGuardHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (BotForumHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new BotGuardHelper(num);
                }
            }
        }
        return localInstance;
    }
}
