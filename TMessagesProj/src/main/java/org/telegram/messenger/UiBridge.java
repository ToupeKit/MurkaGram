package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Bundle;

import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.VideoPlayer;

import java.util.ArrayList;

// MurkaGram: dual UI. Everything the core needs from the ACTIVE UI (activity, current screen, viewers, calls, ...).
// Implementations: org.telegram.ui.ModernUiBridge (the code that was inline in the core, moved 1:1) and
// org.telegram.classic.ui.ClassicUiBridge (TMessagesProj_Classic). Get it with UiEdition.bridge().
public interface UiBridge {

    int COMPONENT_LAUNCH = 0;
    int COMPONENT_BUBBLE = 1;
    int COMPONENT_POPUP = 2;
    int COMPONENT_SHARE = 3;
    int COMPONENT_EXTERNAL = 4;

    // --- activity / screens
    Activity activity();                                   // LaunchActivity.instance

    Activity bubbleActivity();                             // BubbleActivity.instance

    Class<? extends Activity> component(int which);        // COMPONENT_* activity classes

    UiHost lastFragment();                                 // LaunchActivity.getLastFragment()

    UiHost safeLastFragment();                             // LaunchActivity.getSafeLastFragment()

    void clearFragments();                                 // logout of the current account

    void openChat(UiHost host, Bundle args);               // host.presentFragment(new ChatActivity(args))

    void openLoginWithCode(int account, String phone, TLRPC.auth_SentCode sentCode);

    // --- bulletins / sheets / alerts on a screen
    void showErrorBulletin(UiHost host, CharSequence text);

    void showSimpleBulletin(UiHost host, int rawRes, CharSequence text);

    void showSummaryLimitBulletin(UiHost host);

    void showTonNeededSheet(UiHost host, AmountUtils.Amount amount, Runnable onTopUp);

    boolean isKeyboardVisible(UiHost host);

    // --- bots
    void showBotLaunchAlert(UiHost host, TLRPC.User bot, Runnable onConfirm, Runnable onCancel);

    void openGuardBotWebApp(UiHost parentFragment, int currentAccount, long dialogId, long guardBotId, long queryId);

    // --- links
    boolean openAsInternalIntent(Context context, String url, boolean forceNotInternalForApps, boolean forceRequest, Browser.Progress progress);

    boolean openInTelegramBrowser(Context context, String url, Browser.Progress progress);

    // --- calls
    void showVoIP(Activity activity, int account);

    void voipOnStateChanged(int state);

    void voipOnScreenCastStart();

    void voipFinish();

    void voipMoveToBackIfLaunchedInBackground();

    void openGroupCall(AccountInstance accountInstance);    // GroupCallActivity.create(LaunchActivity.instance, ...)

    void joinConference(Activity activity, int account, TLRPC.InputGroupCall inputGroupCall, boolean videoCall, TLRPC.GroupCall preloadedCall);

    void showCallRestrictedSheet(UiHost host, int account, ArrayList<TLRPC.User> users, ArrayList<Long> premiumBlockedUsers, ArrayList<Long> premiumInviteBlockedUsers, String link);

    void showConferenceClosedBulletin(UiHost host);

    boolean isGroupCallLandscape();

    // --- media viewers
    boolean photoViewerVisible();                          // PhotoViewer.getInstance().isVisible()

    boolean photoViewerInjecting();

    void photoViewerInjectPlayer(VideoPlayer player);

    void photoViewerInjectSurface(SurfaceTexture surfaceTexture);

    float savedVideoProgress(MessageObject messageObject, boolean fast);

    void showChromecastBulletin(String host, String deviceName);

    boolean secretViewerVisible();

    MessageObject secretViewerMessage();

    void secretViewerSetOnClose(Runnable onClose);
}
