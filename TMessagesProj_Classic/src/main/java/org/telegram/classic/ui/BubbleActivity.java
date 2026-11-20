/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.classic.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.classic.ui.ActionBar.BaseFragment;
import org.telegram.classic.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.classic.ui.ActionBar.INavigationLayout;
import org.telegram.classic.ui.ActionBar.Theme;
import org.telegram.classic.ui.Components.LayoutHelper;
import org.telegram.classic.ui.Components.PasscodeView;
import org.telegram.classic.ui.Components.ThemeEditorView;

import java.util.ArrayList;

public class BubbleActivity extends BasePermissionsActivity implements INavigationLayout.INavigationLayoutDelegate {
    private boolean murkaRedirected; // MurkaGram: dual UI, this instance only handed its intent over to the modern edition


    public static BubbleActivity instance;

    private boolean finished;
    private ArrayList<BaseFragment> mainFragmentsStack = new ArrayList<>();

    private PasscodeView passcodeView;
    public INavigationLayout actionBarLayout;
    protected DrawerLayoutContainer drawerLayoutContainer;

    private Intent passcodeSaveIntent;
    private boolean passcodeSaveIntentIsNew;
    private int passcodeSaveIntentAccount;
    private int passcodeSaveIntentState;
    private boolean passcodeSaveIntentIsRestore;

    private Runnable lockRunnable;

    private long dialogId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // MurkaGram: dual UI, the modern edition is active (a stale intent of the classic one): hand it over
        if (!org.telegram.messenger.UiEdition.isClassic()) {
            murkaRedirected = true;
            super.onCreate(null);
            org.telegram.messenger.UiEdition.handOver(this, org.telegram.ui.ModernUiBridge.getInstance().component(org.telegram.messenger.UiBridge.COMPONENT_BUBBLE));
            return;
        }
        ClassicUiBridge.ensureModernResources(this); // MurkaGram: dual UI, SHARED classes draw with the modern Theme
        ApplicationLoader.postInitApplication();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages);
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        if (SharedConfig.passcodeHash.length() > 0 && !SharedConfig.allowScreenCapture) {
            try {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                AndroidUtilities.logFlagSecure();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        super.onCreate(savedInstanceState);

        if (SharedConfig.passcodeHash.length() != 0 && SharedConfig.appLocked) {
            SharedConfig.lastPauseTime = (int) (SystemClock.elapsedRealtime() / 1000);
        }

        AndroidUtilities.fillStatusBarHeight(this, false);
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);

        actionBarLayout = INavigationLayout.newLayout(this, false);
        actionBarLayout.setInBubbleMode(true);
        actionBarLayout.setRemoveActionBarExtraHeight(true);

        drawerLayoutContainer = new DrawerLayoutContainer(this);
        drawerLayoutContainer.setAllowOpenDrawer(false, false);
        setContentView(drawerLayoutContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        RelativeLayout launchLayout = new RelativeLayout(this);
        drawerLayoutContainer.addView(launchLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        launchLayout.addView(actionBarLayout.getView(), LayoutHelper.createRelative(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);
        actionBarLayout.setFragmentStack(mainFragmentsStack);
        actionBarLayout.setDelegate(this);

        passcodeView = new PasscodeView(this);
        drawerLayoutContainer.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeOtherAppActivities, this);

        actionBarLayout.removeAllFragments();

        handleIntent(getIntent(), false, savedInstanceState != null, false, UserConfig.selectedAccount, 0);
        instance = this;
    }

    private void showPasscodeActivity() {
        if (passcodeView == null) {
            return;
        }
        SharedConfig.appLocked = true;
        if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(false, false);
        } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(false, true);
        } else if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(false, true);
        }
        passcodeView.onShow(true, false);
        SharedConfig.isWaitingForPasscodeEnter = true;
        drawerLayoutContainer.setAllowOpenDrawer(false, false);
        passcodeView.setDelegate(view -> {
            SharedConfig.isWaitingForPasscodeEnter = false;
            if (passcodeSaveIntent != null) {
                handleIntent(passcodeSaveIntent, passcodeSaveIntentIsNew, passcodeSaveIntentIsRestore, true, passcodeSaveIntentAccount, passcodeSaveIntentState);
                passcodeSaveIntent = null;
            }
            drawerLayoutContainer.setAllowOpenDrawer(true, false);
            actionBarLayout.showLastFragment();

            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.passcodeDismissed, view);
        });
    }

    private boolean handleIntent(final Intent intent, final boolean isNew, final boolean restore, final boolean fromPassword, final int intentAccount, int state) {
        if (!fromPassword && (AndroidUtilities.needShowPasscode(true) || SharedConfig.isWaitingForPasscodeEnter)) {
            showPasscodeActivity();
            passcodeSaveIntent = intent;
            passcodeSaveIntentIsNew = isNew;
            passcodeSaveIntentIsRestore = restore;
            passcodeSaveIntentAccount = intentAccount;
            passcodeSaveIntentState = state;
            UserConfig.getInstance(intentAccount).saveConfig(false);
            return false;
        }
        currentAccount = intent.getIntExtra("currentAccount", UserConfig.selectedAccount);
        if (!UserConfig.isValidAccount(currentAccount)) {
            finish();
            return false;
        }
        BaseFragment chatActivity = null;
        if (intent.getAction() != null && intent.getAction().startsWith("com.tmessages.openchat")) {
            long chatId = intent.getLongExtra("chatId", 0);
            long userId = intent.getLongExtra("userId", 0);
            Bundle args = new Bundle();
            if (userId != 0) {
                dialogId = userId;
                args.putLong("user_id", userId);
            } else {
                dialogId = -chatId;
                args.putLong("chat_id", chatId);
            }
            chatActivity = new ChatActivity(args);
            chatActivity.setInBubbleMode(true);
            chatActivity.setCurrentAccount(currentAccount);
        }
        if (chatActivity == null) {
            finish();
            return false;
        }
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats, dialogId);
        actionBarLayout.removeAllFragments();
        actionBarLayout.addFragmentToStack(chatActivity);
        AccountInstance.getInstance(currentAccount).getNotificationsController().setOpenedInBubble(dialogId, true);
        AccountInstance.getInstance(currentAccount).getConnectionsManager().setAppPaused(false, false);
        actionBarLayout.showLastFragment();

        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (murkaRedirected) { super.onNewIntent(intent); return; } // MurkaGram: dual UI, trampoline
        super.onNewIntent(intent);
        handleIntent(intent, true, false, false, UserConfig.selectedAccount, 0);
    }

    private void onFinish() {
        if (finished) {
            return;
        }
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        finished = true;
        instance = null;
    }

    public void presentFragment(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation) {
        return actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true, false);
    }

    @Override
    protected void onPause() {
        if (murkaRedirected) { super.onPause(); return; } // MurkaGram: dual UI, trampoline
        super.onPause();
        actionBarLayout.onPause();
        ApplicationLoader.externalInterfacePaused = true;
        onPasscodePause();
        if (passcodeView != null) {
            passcodeView.onPause();
        }
        instance = null;
    }

    @Override
    protected void onDestroy() {
        if (murkaRedirected) { super.onDestroy(); return; } // MurkaGram: dual UI, trampoline
        super.onDestroy();
        if (currentAccount != -1) {
            AccountInstance.getInstance(currentAccount).getNotificationsController().setOpenedInBubble(dialogId, false);
            AccountInstance.getInstance(currentAccount).getConnectionsManager().setAppPaused(false, false);
        }
        onFinish();
        instance = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (murkaRedirected) { super.onActivityResult(requestCode, resultCode, data); return; } // MurkaGram: dual UI, trampoline
        super.onActivityResult(requestCode, resultCode, data);
        ThemeEditorView editorView = ThemeEditorView.getInstance();
        if (editorView != null) {
            editorView.onActivityResult(requestCode, resultCode, data);
        }
        if (actionBarLayout.getFragmentStack().size() != 0) {
            BaseFragment fragment = actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1);
            fragment.onActivityResultFragment(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (murkaRedirected) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); return; } // MurkaGram: dual UI, trampoline
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!checkPermissionsResult(requestCode, permissions, grantResults)) return;

        if (actionBarLayout.getFragmentStack().size() != 0) {
            BaseFragment fragment = actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1);
            fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }

        VoIPFragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        if (murkaRedirected) { super.onResume(); return; } // MurkaGram: dual UI, trampoline
        super.onResume();
        actionBarLayout.onResume();
        ApplicationLoader.externalInterfacePaused = false;
        onPasscodeResume();
        if (passcodeView.getVisibility() != View.VISIBLE) {
            actionBarLayout.onResume();
        } else {
            actionBarLayout.dismissDialogs();
            passcodeView.onResume();
        }
        instance = this;
    }

    private void onPasscodePause() {
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (SharedConfig.passcodeHash.length() != 0) {
            SharedConfig.lastPauseTime = (int) (SystemClock.elapsedRealtime() / 1000);
            lockRunnable = new Runnable() {
                @Override
                public void run() {
                    if (lockRunnable == this) {
                        if (AndroidUtilities.needShowPasscode(true)) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("lock app");
                            }
                            showPasscodeActivity();
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("didn't pass lock check");
                            }
                        }
                        lockRunnable = null;
                    }
                }
            };
            if (SharedConfig.appLocked) {
                AndroidUtilities.runOnUIThread(lockRunnable, 1000);
            } else if (SharedConfig.autoLockIn != 0) {
                AndroidUtilities.runOnUIThread(lockRunnable, (long) SharedConfig.autoLockIn * 1000 + 1000);
            }
        } else {
            SharedConfig.lastPauseTime = 0;
        }
        SharedConfig.saveConfig();
    }

    private void onPasscodeResume() {
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (AndroidUtilities.needShowPasscode(true)) {
            showPasscodeActivity();
        }
        if (SharedConfig.lastPauseTime != 0) {
            SharedConfig.lastPauseTime = 0;
            SharedConfig.saveConfig();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        if (murkaRedirected) { super.onConfigurationChanged(newConfig); return; } // MurkaGram: dual UI, trampoline
        AndroidUtilities.checkDisplaySize(this, newConfig);
        AndroidUtilities.setPreferredMaxRefreshRate(getWindow());
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        if (murkaRedirected) { super.onBackPressed(); return; } // MurkaGram: dual UI, trampoline
        if (mainFragmentsStack.size() == 1) {
            super.onBackPressed();
            return;
        }
        if (passcodeView.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
        } else if (drawerLayoutContainer.isDrawerOpened()) {
            drawerLayoutContainer.closeDrawer(false);
        } else {
            actionBarLayout.onBackPressed();
        }
    }

    @Override
    public void onLowMemory() {
        if (murkaRedirected) { super.onLowMemory(); return; } // MurkaGram: dual UI, trampoline
        super.onLowMemory();
        actionBarLayout.onLowMemory();
    }

    @Override
    public boolean needCloseLastFragment(INavigationLayout layout) {
        if (layout.getFragmentStack().size() <= 1) {
            onFinish();
            finish();
            return false;
        }
        return true;
    }
}
