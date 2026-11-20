package org.telegram.classic.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UiBridge;
import org.telegram.messenger.UiHost;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.classic.ui.ActionBar.ActionBarLayout;
import org.telegram.classic.ui.ActionBar.BaseFragment;
import org.telegram.classic.ui.ActionBar.BottomSheetTabs;
import org.telegram.classic.ui.ActionBar.Theme;
import org.telegram.classic.ui.Components.AlertsCreator;
import org.telegram.classic.ui.Components.Bulletin;
import org.telegram.classic.ui.Components.BulletinFactory;
import org.telegram.classic.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.classic.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.classic.ui.Components.voip.VoIPHelper;
import org.telegram.classic.ui.TON.TONIntroActivity;

import java.util.ArrayList;

// MurkaGram: dual UI. The classic (12.2.10) UiBridge: the same code as org.telegram.ui.ModernUiBridge, compiled against
// the classic UI (same class names in org.telegram.classic.ui). Hosts are always classic BaseFragments here.
// Registered by ApplicationLoaderImpl (TMessagesProj_App); active when UiEdition.current() is "classic".
public final class ClassicUiBridge implements UiBridge {

    public ClassicUiBridge() {
    }

    private static boolean modernResourcesCreated;

    // SHARED classes (MessageObject layouts, reactions, emoji, avatars) draw with the paints of the MODERN Theme, which no
    // modern screen creates in the classic edition (plan §2.4 p.9). Called at the start of every classic activity.
    public static void ensureModernResources(Context context) {
        if (modernResourcesCreated) {
            return;
        }
        modernResourcesCreated = true;
        org.telegram.messenger.ApplicationLoader.postInitApplication();
        org.telegram.ui.ActionBar.Theme.createCommonResources(context);
        org.telegram.ui.ActionBar.Theme.createDialogsResources(context);
        org.telegram.ui.ActionBar.Theme.createChatResources(context, false);
    }

    // MurkaGram: dual UI, remote themes go to the classic Theme (shared "themeconfig" prefs, see UiBridge)
    @Override
    public void loadRemoteThemes(int account) {
        Theme.loadRemoteThemes(account, false);
        Theme.checkCurrentRemoteTheme(false);
    }

    @Override
    public void themeUpdated(TLRPC.TL_theme theme, int account) {
        Theme.setThemeUploadInfo(null, null, theme, account, true);
    }

    // --- activity / screens

    @Override
    public Activity activity() {
        return LaunchActivity.instance;
    }

    @Override
    public Activity bubbleActivity() {
        return BubbleActivity.instance;
    }

    @Override
    public Class<? extends Activity> component(int which) {
        switch (which) {
            case COMPONENT_BUBBLE:
                return BubbleActivity.class;
            case COMPONENT_POPUP:
                return PopupNotificationActivity.class;
            case COMPONENT_SHARE:
                return ShareActivity.class;
            case COMPONENT_EXTERNAL:
                return ExternalActionActivity.class;
            default:
                return LaunchActivity.class;
        }
    }

    @Override
    public UiHost lastFragment() {
        return LaunchActivity.getLastFragment();
    }

    @Override
    public UiHost safeLastFragment() {
        return LaunchActivity.getSafeLastFragment();
    }

    // MessagesController (account removal)
    @Override
    public void clearFragments() {
        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.clearFragments();
        }
    }

    // SavedMessagesController.openSavedMessages / openSavedMessagesReminders
    @Override
    public void openChat(UiHost host, Bundle args) {
        ((BaseFragment) host).presentFragment(new ChatActivity(args));
    }

    // BillingController (TL_inputStorePaymentAuthCode)
    @Override
    public void openLoginWithCode(int account, String phone, TLRPC.auth_SentCode sentCode) {
        LoginActivity fragment = LaunchActivity.findFragment(LoginActivity.class);
        if (fragment == null) {
            fragment = new LoginActivity(account);
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null) {
                lastFragment.presentFragment(fragment);
            }
        }
        fragment.open(phone, sentCode);
    }

    // --- bulletins / sheets / alerts

    // UserNameResolver (FLOOD_WAIT)
    @Override
    public void showErrorBulletin(UiHost host, CharSequence text) {
        BulletinFactory.of((BaseFragment) host).createErrorBulletin(text).show();
    }

    // FactCheckController, WidgetMedia.copyText
    @Override
    public void showSimpleBulletin(UiHost host, int rawRes, CharSequence text) {
        BulletinFactory.of((BaseFragment) host).createSimpleBulletin(rawRes, text).show();
    }

    // TranslateController (SUMMARY_FLOOD_PREMIUM)
    @Override
    public void showSummaryLimitBulletin(UiHost host) {
        final BaseFragment lastFragment = (BaseFragment) host;
        BulletinFactory.of(lastFragment)
            .createSimpleBulletin(R.raw.star_premium_2, getString(R.string.SummaryLimit), getString(R.string.SummaryLimitUpgrade), () -> {
                lastFragment.presentFragment(new PremiumPreviewFragment("summarize_limit"));
            })
            .setDuration(Bulletin.DURATION_PROLONG)
            .show(true);
    }

    // SendMessagesHelper (BALANCE_TOO_LOW for a TON dice)
    @Override
    public void showTonNeededSheet(UiHost host, AmountUtils.Amount amount, Runnable onTopUp) {
        final BaseFragment lastFragment = (BaseFragment) host;
        new TONIntroActivity.StarsNeededSheet(
            lastFragment.getContext(),
            lastFragment.getResourceProvider(),
            amount,
            false,
            onTopUp
        ).show();
    }

    // FactCheckController.openFactCheckEditor
    @Override
    public boolean isKeyboardVisible(UiHost host) {
        final BaseFragment fragment = (BaseFragment) host;
        return fragment != null && fragment.getFragmentView() instanceof SizeNotifierFrameLayout && ((SizeNotifierFrameLayout) fragment.getFragmentView()).measureKeyboardHeight() > dp(20);
    }

    // --- bots

    // BotGuardHelper.openGuardBotWebApp
    @Override
    public void showBotLaunchAlert(UiHost host, TLRPC.User bot, Runnable onConfirm, Runnable onCancel) {
        AlertsCreator.createBotLaunchAlert((BaseFragment) host, bot, onConfirm, onCancel);
    }

    // BotGuardHelper.openGuardBotWebApp. MurkaGram: dual UI, the classic BotWebViewSheet got the 13.2.2 guard mode (TYPE_WEB_VIEW_GUARD)
    @Override
    public void openGuardBotWebApp(UiHost host, int currentAccount, long dialogId, long guardBotId, long queryId) {
        final BaseFragment parentFragment = (BaseFragment) host;
        if (LaunchActivity.instance == null) {
            return;
        }
        final Theme.ResourcesProvider resourcesProvider = null;

        final org.telegram.classic.ui.bots.WebViewRequestProps props = org.telegram.classic.ui.bots.WebViewRequestProps.of(currentAccount, dialogId, guardBotId,
            null, null, org.telegram.classic.ui.bots.BotWebViewSheet.TYPE_WEB_VIEW_GUARD, 0, 0,
            false, null, false, null, null, 0, false, false);
        props.queryId = queryId;

        final org.telegram.classic.ui.bots.BotWebViewSheet webViewSheet = new org.telegram.classic.ui.bots.BotWebViewSheet(LaunchActivity.instance, resourcesProvider);
        webViewSheet.setDefaultFullsize(false);
        webViewSheet.setNeedsContext(true);
        webViewSheet.setParentActivity(LaunchActivity.instance);
        webViewSheet.requestWebView(parentFragment, props);
        webViewSheet.show();
    }

    // --- links

    // Browser.openAsInternalIntent
    @Override
    public boolean openAsInternalIntent(Context context, String url, boolean forceNotInternalForApps, boolean forceRequest, Browser.Progress progress) {
        if (url == null) return false;
        LaunchActivity activity = null;
        if (AndroidUtilities.findActivity(context) instanceof LaunchActivity) {
            activity = (LaunchActivity) AndroidUtilities.findActivity(context);
        } else if (LaunchActivity.instance != null) {
            activity = LaunchActivity.instance;
        } else {
            return false;
        }
        if (activity == null) return false;
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        ComponentName componentName = new ComponentName(context.getPackageName(), LaunchActivity.class.getName());
        intent.setComponent(componentName);
        intent.putExtra(android.provider.Browser.EXTRA_CREATE_NEW_TAB, true);
        intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        intent.putExtra(LaunchActivity.EXTRA_FORCE_NOT_INTERNAL_APPS, forceNotInternalForApps);
        intent.putExtra(LaunchActivity.EXTRA_FORCE_REQUEST, forceRequest);
        activity.onNewIntent(intent, progress);
        return true;
    }

    // Browser.openInTelegramBrowser
    @Override
    public boolean openInTelegramBrowser(Context context, String url, Browser.Progress progress) {
        if (LaunchActivity.instance != null) {
            BottomSheetTabs tabs = LaunchActivity.instance.getBottomSheetTabs();
            if (tabs != null && tabs.tryReopenTab(url) != null) {
                return true;
            }
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        // as in core-13: the classic BaseFragment has no getArticleViewer(), createArticleViewer() below reuses an open one
        if (fragment != null && fragment.getParentLayout() instanceof ActionBarLayout) {
            fragment = ((ActionBarLayout) fragment.getParentLayout()).getSheetFragment();
        }
        if (fragment == null) return false;
        fragment.createArticleViewer(false).open(url, progress);
        return true;
    }

    // --- calls

    // VoIPService, VoIPGroupNotification
    @Override
    public void showVoIP(Activity activity, int account) {
        VoIPFragment.show(activity, account);
    }

    // VoIPPreNotificationService, VoIPGroupNotification
    @Override
    public void voipOnStateChanged(int state) {
        if (VoIPFragment.getInstance() != null) {
            VoIPFragment.getInstance().onStateChanged(state);
        }
    }

    // VoIPService
    @Override
    public void voipOnScreenCastStart() {
        if (VoIPFragment.getInstance() != null) {
            VoIPFragment.getInstance().onScreenCastStart();
        }
    }

    // VoIPService, VoIPGroupNotification
    @Override
    public void voipFinish() {
        if (VoIPFragment.getInstance() != null) {
            VoIPFragment.getInstance().finish();
        }
    }

    // VoIPPreNotificationService (missed/declined call that was launched in background)
    @Override
    public void voipMoveToBackIfLaunchedInBackground() {
        final LaunchActivity activity = LaunchActivity.instance;
        if (activity != null && activity.voipLaunchedInBackground && VoIPService.getSharedInstance() == null) {
            activity.voipLaunchedInBackground = false;
            final VoIPFragment fragment = VoIPFragment.getInstance();
            if (fragment != null) {
                fragment.finish();
            }
            activity.moveTaskToBack(true);
        }
    }

    // VoIPService
    @Override
    public void openGroupCall(AccountInstance accountInstance) {
        GroupCallActivity.create(LaunchActivity.instance, accountInstance, null, null, false, null);
    }

    // VoIPGroupNotification.answer
    @Override
    public void joinConference(Activity activity, int account, TLRPC.InputGroupCall inputGroupCall, boolean videoCall, TLRPC.GroupCall preloadedCall) {
        VoIPHelper.joinConference(activity, account, inputGroupCall, videoCall, preloadedCall);
    }

    // VoIPService (TYPE_CALL_RESTRICTED)
    @Override
    public void showCallRestrictedSheet(UiHost host, int currentAccount, ArrayList<TLRPC.User> users2, ArrayList<Long> premiumBlockedUsers, ArrayList<Long> premiumInviteBlockedUsers, String link) {
        final BaseFragment fragment = (BaseFragment) host;
        final LimitReachedBottomSheet restricterdUsersBottomSheet = new LimitReachedBottomSheet(fragment, fragment.getContext(), LimitReachedBottomSheet.TYPE_CALL_RESTRICTED, currentAccount, new DarkBlueThemeResourcesProvider());
        restricterdUsersBottomSheet.setRestrictedUsers(null, users2, premiumBlockedUsers, premiumInviteBlockedUsers, link);
        restricterdUsersBottomSheet.show();
    }

    // VoIPService (GROUPCALL_INVALID)
    @Override
    public void showConferenceClosedBulletin(UiHost host) {
        BulletinFactory.of((BaseFragment) host)
            .createSimpleBulletin(R.raw.linkbroken, getString(R.string.ConferenceClosed))
            .show()
            .hideAfterBottomSheet = false;
    }

    // ChatObject
    @Override
    public boolean isGroupCallLandscape() {
        return GroupCallActivity.isLandscapeMode;
    }

    // --- media viewers

    // MediaController
    @Override
    public boolean photoViewerVisible() {
        return PhotoViewer.getInstance().isVisible();
    }

    // MediaController
    @Override
    public boolean photoViewerInjecting() {
        return PhotoViewer.hasInstance() && PhotoViewer.getInstance().isInjectingVideoPlayer();
    }

    // MediaController
    @Override
    public void photoViewerInjectPlayer(VideoPlayer player) {
        PhotoViewer.getInstance().injectVideoPlayer(player);
    }

    // MediaController
    @Override
    public void photoViewerInjectSurface(SurfaceTexture surfaceTexture) {
        PhotoViewer.getInstance().injectVideoPlayerSurface(surfaceTexture);
    }

    // MessageObject.getVideoSavedProgress
    @Override
    public float savedVideoProgress(MessageObject messageObject, boolean fast) {
        return fast ? PhotoViewer.getSavedProgressFast(messageObject) : PhotoViewer.getSavedProgress(messageObject);
    }

    // ChromecastController
    @Override
    public void showChromecastBulletin(String host, String deviceName) {
        PhotoViewer.getInstance().showChromecastBulletin(host, deviceName);
    }

    // MessagesController (one-time media)
    @Override
    public boolean secretViewerVisible() {
        return SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible();
    }

    // MessagesController (one-time media)
    @Override
    public MessageObject secretViewerMessage() {
        return SecretMediaViewer.getInstance().getCurrentMessageObject();
    }

    // MessagesController (one-time media)
    @Override
    public void secretViewerSetOnClose(Runnable onClose) {
        SecretMediaViewer.getInstance().setOnClose(onClose);
    }
}
