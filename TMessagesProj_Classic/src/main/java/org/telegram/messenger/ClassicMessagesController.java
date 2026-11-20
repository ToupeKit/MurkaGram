package org.telegram.messenger;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.util.Consumer;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import me.vkryl.core.BitwiseUtils;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.classic.ui.ActionBar.ActionBarLayout;
import org.telegram.classic.ui.ActionBar.AlertDialog;
import org.telegram.classic.ui.ActionBar.BaseFragment;
import org.telegram.classic.ui.ActionBar.Theme;
import org.telegram.classic.ui.Cells.CheckBoxCell;
import org.telegram.classic.ui.ChatActivity;
import org.telegram.classic.ui.ChatReactionsEditActivity;
import org.telegram.classic.ui.ChatRightsEditActivity;
import org.telegram.classic.ui.Components.AlertsCreator;
import org.telegram.classic.ui.Components.BulletinFactory;
import org.telegram.classic.ui.Components.ImageUpdater;
import org.telegram.classic.ui.Components.JoinCallAlert;
import org.telegram.classic.ui.Components.LayoutHelper;
import org.telegram.classic.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.classic.ui.Components.SwipeGestureSettingsView;
import org.telegram.classic.ui.Components.TranscribeButton;
import org.telegram.classic.ui.DialogsActivity;
import org.telegram.classic.ui.EditWidgetActivity;
import org.telegram.classic.ui.LaunchActivity;
import org.telegram.classic.ui.MainTabsActivity;
import org.telegram.classic.ui.PremiumPreviewFragment;
import org.telegram.classic.ui.ProfileActivity;
import org.telegram.classic.ui.SecretMediaViewer;
import org.telegram.classic.ui.ThemeActivity;
import org.telegram.classic.ui.TopicsFragment;
import org.telegram.classic.ui.Wallet.GramWallet;
import org.telegram.classic.ui.Wallet.GramWalletStorage;
import org.telegram.classic.ui.bots.BotWebViewAttachedSheet;
import org.telegram.classic.ui.bots.BotWebViewSheet;
import org.telegram.classic.ui.bots.WebViewRequestProps;
import org.telegram.classic.ui.community.CommunityUtils;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.messenger.support.LongSparseLongArray;
import org.telegram.messenger.utils.EphemeralMessagesHelper;
import org.telegram.messenger.voip.GroupCallMessagesController;
import org.telegram.messenger.voip.VoIPDebugToSend;
import org.telegram.messenger.voip.VoIPPreNotificationService;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_chatlists;
import org.telegram.tgnet.tl.TL_communities;
import org.telegram.tgnet.tl.TL_ephemeral;
import org.telegram.tgnet.tl.TL_forum;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Stars.BotStarsController;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.community.CommunityChatType;
import static org.telegram.classic.ui.Stories.HighlightMessageSheet.parseTiers;
import static org.telegram.classic.ui.Stories.HighlightMessageSheet.parseTiersString;
import static org.telegram.classic.ui.Stories.HighlightMessageSheet.tiersEqual;
import static org.telegram.classic.ui.Stories.HighlightMessageSheet.tiersToString;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.NotificationsController.TYPE_CHANNEL;
import static org.telegram.messenger.NotificationsController.TYPE_PRIVATE;
import static org.telegram.messenger.NotificationsController.TYPE_REACTIONS_MESSAGES;
import static org.telegram.messenger.Utilities.tryParseLong;
import static org.telegram.ui.Stars.StarsController.findAttribute;

// MurkaGram: dual UI. Methods of MessagesController whose parameters are UI types, copied from the core-13 (12.2.10 UI) version
// so that the classic UI can call them with its own types. Generated by murka-plans/tools/dual/classic_k10.py.
public final class ClassicMessagesController {

    private ClassicMessagesController() {
    }

    // MurkaGram: dual UI, core-13 MessagesController.addUsersToChat
    public static void addUsersToChat(MessagesController self, TLRPC.Chat currentChat, BaseFragment baseFragment, ArrayList<TLRPC.User> users, int fwdCount, Consumer<TLRPC.User> onAddUser, Consumer<TLRPC.User> onRestricted, Runnable onComplete) {
        final int count = users.size();
        final int[] processed = new int[1];
        processed[0] = 0;
        TLRPC.TL_messages_invitedUsers totalInvitedUsers = new TLRPC.TL_messages_invitedUsers();
        totalInvitedUsers.updates = new TLRPC.TL_updates();
        long chatId = currentChat.id;
        for (int a = 0; a < count; a++) {
            final TLRPC.User user = users.get(a);
            addUserToChat(self, chatId, user, fwdCount, null, baseFragment, false, () -> {
                if (onAddUser != null) {
                    onAddUser.accept(user);
                }
            }, err -> {
                if (onRestricted != null) {
                    onRestricted.accept(user);
                }
                return !(err != null && "USER_PRIVACY_RESTRICTED".equals(err.text));
            }, invitedUsers -> {
                if (invitedUsers != null) {
                    totalInvitedUsers.missing_invitees.addAll(invitedUsers.missing_invitees);
                }
                processed[0]++;
                if (processed[0] >= count) {
                    if (!totalInvitedUsers.missing_invitees.isEmpty()) {
                        AndroidUtilities.runOnUIThread(() -> {
                            AlertsCreator.checkRestrictedInviteUsers(self.currentAccount, currentChat, totalInvitedUsers);
                        });
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            });
            self.putUser(user, false);
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.addUserToChat
    public static void addUserToChat(MessagesController self, long chatId, TLRPC.User user, int forwardCount, String botHash, BaseFragment fragment, Runnable onFinishRunnable) {
        addUserToChat(self, chatId, user, forwardCount, botHash, fragment, false, onFinishRunnable, null);
    }

    // MurkaGram: dual UI, core-13 MessagesController.addUserToChat
    public static void addUserToChat(MessagesController self, 
        long chatId,
        TLRPC.User user,
        int forwardCount,
        String botHash,
        BaseFragment fragment,
        boolean ignoreIfAlreadyExists,
        Runnable onFinishRunnable,
        MessagesController.ErrorDelegate onError
    ) {
        addUserToChat(self, chatId, user, forwardCount, botHash, fragment, ignoreIfAlreadyExists, onFinishRunnable, onError, null);
    }

    // MurkaGram: dual UI, core-13 MessagesController.addUserToChat
    public static void addUserToChat(MessagesController self, 
        long chatId,
        TLRPC.User user,
        int forwardCount,
        String botHash,
        BaseFragment fragment,
        boolean ignoreIfAlreadyExists,
        Runnable onFinishRunnable,
        MessagesController.ErrorDelegate onError,
        Utilities.Callback<TLRPC.TL_messages_invitedUsers> processInvitedUsers
    ) {
        if (user == null) {
            if (onError != null) {
                onError.run(null);
            }
            if (processInvitedUsers != null) {
                processInvitedUsers.run(null);
            }
            return;
        }

        TLObject request;

        final TLRPC.Chat chat = self.getChat(chatId); final boolean isChannel = ChatObject.isChannel(chat);
        final boolean isMegagroup = isChannel && chat.megagroup;
        TLRPC.InputUser inputUser = self.getInputUser(user);
        if (botHash == null || isChannel && !isMegagroup) {
            if (isChannel) {
                if (inputUser instanceof TLRPC.TL_inputUserSelf) {
                    if (self.joiningToChannels.contains(chatId)) {
                        if (onError != null) {
                            onError.run(null);
                        }
                        return;
                    }
                    TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
                    req.channel = self.getInputChannel(chatId);
                    request = req;
                    self.joiningToChannels.add(chatId);
                } else {
                    TLRPC.TL_channels_inviteToChannel req = new TLRPC.TL_channels_inviteToChannel();
                    req.channel = self.getInputChannel(chatId);
                    req.users.add(inputUser);
                    request = req;
                }
            } else {
                TLRPC.TL_messages_addChatUser req = new TLRPC.TL_messages_addChatUser();
                req.chat_id = chatId;
                req.fwd_limit = forwardCount;
                req.user_id = inputUser;
                request = req;
            }
        } else {
            TLRPC.TL_messages_startBot req = new TLRPC.TL_messages_startBot();
            req.bot = inputUser;
            if (isChannel) {
                req.peer = self.getInputPeer(-chatId);
            } else {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = chatId;
            }
            req.start_param = botHash;
            req.random_id = Utilities.random.nextLong();
            request = req;
        }

        self.getConnectionsManager().sendRequest(request, (response, error) -> {
            if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                AndroidUtilities.runOnUIThread(() -> self.joiningToChannels.remove(chatId));
            }
            if (error != null) {
                if ("JOIN_GUARD_TIMEOUT".equals(error.text)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (processInvitedUsers != null) {
                            processInvitedUsers.run(null);
                        }
                        if (onFinishRunnable != null) {
                            onFinishRunnable.run();
                        }
                    });
                    return;
                }
                if ("PRIVACY_PREMIUM_REQUIRED".equals(error.text)) {
                    TLRPC.TL_messages_invitedUsers invitedUsers = new TLRPC.TL_messages_invitedUsers();
                    invitedUsers.updates = new TLRPC.TL_updates();
                    TLRPC.TL_missingInvitee missingInvitee = new TLRPC.TL_missingInvitee();
                    missingInvitee.user_id = user.id;
                    missingInvitee.premium_would_allow_invite = true;
                    missingInvitee.premium_required_for_pm = true;
                    invitedUsers.missing_invitees.add(missingInvitee);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (processInvitedUsers != null) {
                            processInvitedUsers.run(invitedUsers);
                        } else {
                            AlertsCreator.checkRestrictedInviteUsers(self.currentAccount, self.getChat(chatId), invitedUsers);
                        }
                    });
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (processInvitedUsers != null) {
                        processInvitedUsers.run(null);
                    }
                });
                if ("USER_ALREADY_PARTICIPANT".equals(error.text) && ignoreIfAlreadyExists) {
                    if (onFinishRunnable != null) {
                        AndroidUtilities.runOnUIThread(onFinishRunnable);
                    }
                    return;
                }
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        boolean handleErrors = onError.run(error);
                        if (handleErrors) {
                            AlertsCreator.processError(self.currentAccount, error, fragment, request, isChannel && !isMegagroup);
                        }
                    });
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (onError == null) {
                        AlertsCreator.processError(self.currentAccount, error, fragment, request, isChannel && !isMegagroup);
                    }
                    if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                        self.getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
                    }
                });
                return;
            }
            boolean hasJoinMessage = false;
            TLRPC.Updates updates;
            TLRPC.TL_messages_invitedUsers invitedUsers;

            if (response instanceof TLRPC.TL_chatInviteJoinResultOk) {
                invitedUsers = null;
                updates = ((TLRPC.TL_chatInviteJoinResultOk) response).updates;
            } else if (response instanceof TLRPC.TL_chatInviteJoinResultWebView) {
                final TLRPC.TL_chatInviteJoinResultWebView resultWebView = (TLRPC.TL_chatInviteJoinResultWebView) response;
                AndroidUtilities.runOnUIThread(() -> {
                    self.putUsers(resultWebView.users, false);
                    BotGuardHelper.getInstance(self.currentAccount).openGuardBotWebApp(-chatId,
                        resultWebView.bot_id, resultWebView.query_id);
                });

                return;
            } else if (response instanceof TLRPC.TL_messages_invitedUsers) {
                invitedUsers = (TLRPC.TL_messages_invitedUsers) response;
                updates = invitedUsers.updates;
            } else if (response instanceof TLRPC.Updates) {
                invitedUsers = null;
                updates = (TLRPC.Updates) response;
            } else {
                FileLog.e("unexpected " + response + " in addUserToChat");
                AndroidUtilities.runOnUIThread(() -> {
                    if (processInvitedUsers != null) {
                        processInvitedUsers.run(null);
                    }
                });
                return;
            }
            for (int a = 0; a < updates.updates.size(); a++) {
                TLRPC.Update update = updates.updates.get(a);
                if (update instanceof TL_update.TL_updateNewChannelMessage) {
                    if (((TL_update.TL_updateNewChannelMessage) update).message.action instanceof TLRPC.TL_messageActionChatAddUser) {
                        hasJoinMessage = true;
                        break;
                    }
                }
            }
            self.processUpdates(updates, false);
            if (invitedUsers != null && !invitedUsers.missing_invitees.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    self.putUsers(updates.users, false);
                    self.putChats(updates.chats, false);
                    if (processInvitedUsers != null) {
                        processInvitedUsers.run(invitedUsers);
                    } else {
                        AlertsCreator.checkRestrictedInviteUsers(self.currentAccount, self.getChat(chatId), invitedUsers);
                    }
                });
                return;
            }
            if (isChannel) {
                if (!hasJoinMessage && inputUser instanceof TLRPC.TL_inputUserSelf) {
                    self.generateJoinMessage(chatId, true);
                }
                AndroidUtilities.runOnUIThread(() -> self.loadFullChat(chatId, 0, true), 1000);
            }
            if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                self.getMessagesStorage().updateDialogsWithDeletedMessages(-chatId, chatId, new ArrayList<>(), null);
            }
            if (onFinishRunnable != null) {
                AndroidUtilities.runOnUIThread(onFinishRunnable);
            }
            if (processInvitedUsers != null) {
                AndroidUtilities.runOnUIThread(() -> processInvitedUsers.run(null));
            }
            if (UserObject.isUserSelf(user) && !ChatObject.isChannelAndNotMegaGroup(chat) && hasJoinMessage) {
                AndroidUtilities.runOnUIThread(() -> {
                    self.getNotificationCenter().postNotificationName(NotificationCenter.joinedGroup, chatId);
                });
            }
        });
    }

    // MurkaGram: dual UI, core-13 MessagesController.checkCanOpenChat
    public static boolean checkCanOpenChat(MessagesController self, Bundle bundle, BaseFragment fragment) {
        return checkCanOpenChat(self, bundle, fragment, null);
    }

    // MurkaGram: dual UI, core-13 MessagesController.checkCanOpenChat
    public static boolean checkCanOpenChat(MessagesController self, Bundle bundle, BaseFragment fragment, MessageObject originalMessage) {
        return checkCanOpenChat(self, bundle, fragment, originalMessage, null);
    }

    // MurkaGram: dual UI, core-13 MessagesController.checkCanOpenChat
    public static boolean checkCanOpenChat(MessagesController self, Bundle bundle, BaseFragment fragment, MessageObject originalMessage, Browser.Progress progress) {
        if (bundle == null || fragment == null) {
            return true;
        }
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        long userId = bundle.getLong("user_id", 0);
        long chatId = bundle.getLong("chat_id", 0);
        int messageId = bundle.getInt("message_id", 0);
        long dialogId = 0;
        if (userId != 0) {
            dialogId = userId;
            user = self.getUser(userId);
        } else if (chatId != 0) {
            dialogId = -chatId;
            chat = self.getChat(chatId);
        }
        if (user == null && chat == null) {
            return true;
        }
        String reason;
        if (chat != null) {
            reason = self.getRestrictionReason(chat.restriction_reason);
        } else {
            reason = self.getRestrictionReason(user.restriction_reason);
        }
        if (reason != null) {
            showCantOpenAlert(fragment, reason);
            return false;
        }
        if (messageId != 0 && originalMessage != null && chat != null && chat.access_hash == 0) {
            long did = originalMessage.getDialogId();
            if (!DialogObject.isEncryptedDialog(did)) {
                final AlertDialog progressDialog;
                if (progress != null) {
                    progressDialog = null;
                    progress.init();
                } else {
                    progressDialog = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                }
                TLObject req;
                if (did < 0) {
                    chat = self.getChat(-did);
                }
                if (did > 0 || !ChatObject.isChannel(chat)) {
                    TLRPC.TL_messages_getMessages request = new TLRPC.TL_messages_getMessages();
                    request.id.add(originalMessage.getId());
                    req = request;
                } else {
                    chat = self.getChat(-did);
                    TLRPC.TL_channels_getMessages request = new TLRPC.TL_channels_getMessages();
                    request.channel = self.getInputChannel(chat);
                    request.id.add(originalMessage.getId());
                    req = request;
                }
                int reqId = self.getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (response != null) {
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                if (progressDialog != null) {
                                    progressDialog.dismiss();
                                }
                                if (progress != null) {
                                    progress.end();
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                            self.putUsers(res.users, false);
                            self.putChats(res.chats, false);
                            self.getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                            fragment.presentFragment(new ChatActivity(bundle), true);
                        });
                    }
                });
                if (progressDialog != null) {
                    progressDialog.setOnCancelListener(dialog -> {
                        self.getConnectionsManager().cancelRequest(reqId, true);
                        fragment.setVisibleDialog(null);
                    });
                    fragment.setVisibleDialog(progressDialog);
                    progressDialog.show();
                } else {
                    progress.onCancel(() -> {
                        self.getConnectionsManager().cancelRequest(reqId, true);
                        fragment.setVisibleDialog(null);
                    });
                    progress.init();
                }
                return false;
            }
        } else if (messageId != 0 && originalMessage != null && progress != null) {
            progress.init();
            progress.onCancel(self.ensureMessagesLoaded(-chatId, messageId, new MessagesController.MessagesLoadedCallback() {
                @Override
                public void onMessagesLoaded(boolean fromCache) {
                    progress.end();
                    fragment.presentFragment(new ChatActivity(bundle));
                }

                @Override
                public void onError() {
                    progress.end();
                    fragment.presentFragment(new ChatActivity(bundle));
                }
            }));
            return false;
        }
        return true;
    }

    // MurkaGram: dual UI, core-13 MessagesController.checkSensitive
    public static void checkSensitive(MessagesController self, BaseFragment fragment, long did, Runnable done, Runnable canceled) {
        ArrayList<TLRPC.RestrictionReason> reasons = null;
        if (did < 0) {
            TLRPC.Chat chat = self.getChat(-did);
            reasons = chat != null ? chat.restriction_reason : null;
        } else if (did >= 0) {
            TLRPC.User user = self.getUser(did);
            reasons = user != null ? user.restriction_reason : null;
        }
        if (!self.isSensitive(reasons) || self.sensitiveAgreed.contains(did)) {
            if (done != null) {
                done.run();
            }
            return;
        }
        Context _context0 = fragment.getContext();
        if (_context0 == null) _context0 = AndroidUtilities.findActivity(LaunchActivity.instance);
        if (_context0 == null) _context0 = LaunchActivity.instance;
        if (_context0 == null) _context0 = ApplicationLoader.applicationContext;
        final Context context0 = _context0;
        final AlertDialog progressDialog = context0 == null ? null : new AlertDialog(context0, AlertDialog.ALERT_TYPE_SPINNER);
        if (progressDialog != null) {
            progressDialog.showDelayed(200);
        }
        self.getContentSettings(settings -> {
            if (progressDialog != null) {
                progressDialog.dismissUnless(200);
            }
            Context _context1 = fragment.getContext();
            if (_context1 == null) _context1 = AndroidUtilities.findActivity(LaunchActivity.instance);
            if (_context1 == null) _context1 = LaunchActivity.instance;
            if (_context1 == null) _context1 = ApplicationLoader.applicationContext;
            final Context context = _context1;
            final BaseFragment lastFragment0 = LaunchActivity.getSafeLastFragment();
            final Theme.ResourcesProvider resourcesProvider = lastFragment0 == null ? null : lastFragment0.getResourceProvider();

            final boolean[] always = new boolean[1];
            final FrameLayout frameLayout = new FrameLayout(context);
            final boolean needsVerification = self.config.needAgeVideoVerification.get() && !TextUtils.isEmpty(self.verifyAgeBotUsername);
            final boolean cannotView = !(settings != null && settings.sensitive_can_change) && needsVerification;
            if (needsVerification) {
                always[0] = true;
            } else if (settings != null && settings.sensitive_can_change) {
                CheckBoxCell checkbox = new CheckBoxCell(context, 1, resourcesProvider);
                checkbox.setBackground(Theme.getSelectorDrawable(false));
                checkbox.setText(getString(R.string.MessageShowSensitiveContentAlways), "", always[0], false);
                checkbox.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
                frameLayout.addView(checkbox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                checkbox.setOnClickListener(v -> {
                    CheckBoxCell cell1 = (CheckBoxCell) v;
                    always[0] = !always[0];
                    cell1.setChecked(always[0], true);
                });
            }
            final boolean[] doneSet = new boolean[1];
            AlertDialog.Builder alert = new AlertDialog.Builder(context, resourcesProvider)
                .setTitle(getString(R.string.MessageShowSensitiveContentChannelTitle))
                .setMessage(getString(cannotView ? R.string.MessageShowSensitiveContentChannelTextClosed : R.string.MessageShowSensitiveContentChannelText))
                .setView(frameLayout).setCustomViewOffset(9)
                .setNegativeButton(getString(cannotView ? R.string.MessageShowSensitiveContentChannelTextClosedButton : R.string.Cancel), null)
                .setOnDismissListener(di -> {
                    if (!doneSet[0] && canceled != null) {
                        canceled.run();
                    }
                });
            if (!cannotView) {
                alert.setPositiveButton(getString(R.string.MessageShowSensitiveContentButton), (di, w) -> {
                    if (needsVerification || (always[0] && settings != null && settings.sensitive_can_change)) {
                        ThemeActivity.verifyAge(context, self.currentAccount, passed -> {
                            final BaseFragment lastFragment2 = LaunchActivity.getSafeLastFragment();
                            if (passed) {
                                self.sensitiveAgreed.add(did);
                                self.setContentSettings(true);
                                if (lastFragment2 != null) {
                                    BulletinFactory.of(lastFragment2)
                                        .createSimpleBulletinDetail(R.raw.chats_infotip, AndroidUtilities.replaceArrows(AndroidUtilities.premiumText(getString(R.string.SensitiveContentSettingsToast), () -> {
                                            if (lastFragment2 != null) {
                                                lastFragment2.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC).highlightSensitiveRow());
                                            }
                                        }), true))
                                        .show(true);
                                }
                                doneSet[0] = true;
                                if (done != null) {
                                    done.run();
                                }
                            } else {
                                if (lastFragment2 != null) {
                                    BulletinFactory.of(lastFragment2)
                                        .createSimpleBulletin(R.raw.error, getString(R.string.AgeVerificationFailedTitle), getString(R.string.AgeVerificationFailedText))
                                        .show();
                                }
                            }
                        }, resourcesProvider);
                    } else {
                        self.sensitiveAgreed.add(did);
                        doneSet[0] = true;
                        if (done != null) {
                            done.run();
                        }
                    }
                });
            }
            alert.show();
        });
    }

    // MurkaGram: dual UI, core-13 MessagesController.convertToGigaGroup
    public static void convertToGigaGroup(MessagesController self, final Context context, TLRPC.Chat chat, BaseFragment fragment, MessagesStorage.BooleanCallback convertRunnable) {
        TLRPC.TL_channels_convertToGigagroup req = new TLRPC.TL_channels_convertToGigagroup();
        req.channel = self.getInputChannel(chat);
        AlertDialog progressDialog = context != null ? new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER) : null;
        int reqId = self.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                if (context != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!((Activity) context).isFinishing()) {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                }
                TLRPC.Updates updates = (TLRPC.Updates) response;
                self.processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> {
                    if (convertRunnable != null) {
                        convertRunnable.run(true);
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    if (convertRunnable != null) {
                        convertRunnable.run(false);
                    }
                    if (context != null) {
                        if (!((Activity) context).isFinishing()) {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            AlertsCreator.processError(self.currentAccount, error, fragment, req, false);
                        }
                    }
                });
            }
        });
        if (progressDialog != null) {
            progressDialog.setOnCancelListener(dialog -> self.getConnectionsManager().cancelRequest(reqId, true));
            try {
                progressDialog.showDelayed(400);
            } catch (Exception ignore) {

            }
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.convertToMegaGroup
    public static void convertToMegaGroup(MessagesController self, Context context, long chatId, BaseFragment fragment, MessagesStorage.LongCallback convertRunnable) {
        convertToMegaGroup(self, context, chatId, fragment, convertRunnable, null);
    }

    // MurkaGram: dual UI, core-13 MessagesController.convertToMegaGroup
    public static void convertToMegaGroup(MessagesController self, Context context, long chatId, BaseFragment fragment, MessagesStorage.LongCallback convertRunnable, Runnable errorRunnable) {
        TLRPC.TL_messages_migrateChat req = new TLRPC.TL_messages_migrateChat();
        req.chat_id = chatId;
        AlertDialog progressDialog = context != null ? new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER) : null;
        int reqId = self.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                if (context != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!((Activity) context).isFinishing()) {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                }
                TLRPC.Updates updates = (TLRPC.Updates) response;
                self.processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> {
                    if (convertRunnable != null) {
                        TLRPC.Chat prevChat = null;
                        for (int a = 0; a < updates.chats.size(); a++) {
                            TLRPC.Chat chat = updates.chats.get(a);
                            if (chatId == chat.id) {
                                prevChat = chat;
                                break;
                            }
                        }
                        if (prevChat != null && prevChat.migrated_to != null) {
                            long newChatId = prevChat.migrated_to.channel_id;
                            TLRPC.Chat newChat = null;
                            for (int a = 0; a < updates.chats.size(); a++) {
                                TLRPC.Chat chat = updates.chats.get(a);
                                if (newChatId == chat.id) {
                                    newChat = chat;
                                    break;
                                }
                            }
                            if (newChat != null) {
                                convertRunnable.run(newChatId);
                            }
                        }
                    }
                });
            } else {
                if (errorRunnable != null) {
                    errorRunnable.run();
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (convertRunnable != null) {
                        convertRunnable.run(0);
                    }
                    if (context != null) {
                        if (!((Activity) context).isFinishing()) {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            AlertsCreator.processError(self.currentAccount, error, fragment, req, false);
                        }
                    }
                });
            }
        });
        if (progressDialog != null) {
            progressDialog.setOnCancelListener(dialog -> self.getConnectionsManager().cancelRequest(reqId, true));
            try {
                progressDialog.show();
            } catch (Exception ignore) {

            }
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.createChat
    public static int createChat(MessagesController self, String title, ArrayList<Long> selectedContacts, String about, int type, boolean forImport, Location location, String locationAddress, int ttlPeriod, BaseFragment fragment) {
        if (type == ChatObject.CHAT_TYPE_CHAT && !forImport) {
            TLRPC.TL_messages_createChat req = new TLRPC.TL_messages_createChat();
            req.title = title;
            if (ttlPeriod >= 0) {
                req.ttl_period = ttlPeriod;
                req.flags |= 1;
            }
            for (int a = 0; a < selectedContacts.size(); a++) {
                TLRPC.User user = self.getUser(selectedContacts.get(a));
                if (user == null) {
                    continue;
                }
                req.users.add(self.getInputUser(user));
            }
            return self.getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        AlertsCreator.processError(self.currentAccount, error, fragment, req);
                        self.getNotificationCenter().postNotificationName(NotificationCenter.chatDidFailCreate);
                    });
                    return;
                }
                if (response instanceof TLRPC.TL_messages_invitedUsers) {
                    TLRPC.TL_messages_invitedUsers res = (TLRPC.TL_messages_invitedUsers) response;
                    self.processUpdates(res.updates, false);
                    AndroidUtilities.runOnUIThread(() -> {
                        self.putUsers(res.updates.users, false);
                        self.putChats(res.updates.chats, false);
                        if (res.updates.chats != null && !res.updates.chats.isEmpty()) {
                            self.getNotificationCenter().postNotificationName(NotificationCenter.chatDidCreated, res.updates.chats.get(0).id);
                            AlertsCreator.checkRestrictedInviteUsers(self.currentAccount, res.updates.chats.get(0), res);
                        } else {
                            self.getNotificationCenter().postNotificationName(NotificationCenter.chatDidFailCreate);
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        } else if (forImport || type == ChatObject.CHAT_TYPE_CHANNEL || type == ChatObject.CHAT_TYPE_MEGAGROUP || type == ChatObject.CHAT_TYPE_FORUM) {
            TLRPC.TL_channels_createChannel req = new TLRPC.TL_channels_createChannel();
            req.title = title;
            req.about = about != null ? about : "";
            req.for_import = forImport;
            if (forImport || type == ChatObject.CHAT_TYPE_MEGAGROUP || type == ChatObject.CHAT_TYPE_FORUM) {
                req.megagroup = true;
            } else {
                req.broadcast = true;
            }
            req.forum = type == ChatObject.CHAT_TYPE_FORUM;
            if (location != null) {
                req.geo_point = new TLRPC.TL_inputGeoPoint();
                req.geo_point.lat = location.getLatitude();
                req.geo_point._long = location.getLongitude();
                req.address = locationAddress;
                req.flags |= 4;
            }
            return self.getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        AlertsCreator.processError(self.currentAccount, error, fragment, req);
                        self.getNotificationCenter().postNotificationName(NotificationCenter.chatDidFailCreate);
                    });
                    return;
                }
                TLRPC.Updates updates = (TLRPC.Updates) response;
                self.processUpdates(updates, false);
                AndroidUtilities.runOnUIThread(() -> {
                    self.putUsers(updates.users, false);
                    self.putChats(updates.chats, false);
                    if (updates.chats != null && !updates.chats.isEmpty()) {
                        self.getNotificationCenter().postNotificationName(NotificationCenter.chatDidCreated, updates.chats.get(0).id);
                    } else {
                        self.getNotificationCenter().postNotificationName(NotificationCenter.chatDidFailCreate);
                    }
                });
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
        return 0;
    }

    // MurkaGram: dual UI, core-13 MessagesController.openApp
    public static void openApp(MessagesController self, TLRPC.User bot, int classGuid) {
        openApp(self, null, bot, null, classGuid, null);
    }

    // MurkaGram: dual UI, core-13 MessagesController.openApp
    public static void openApp(MessagesController self, BaseFragment _fragment, TLRPC.User bot, String param, int classGuid, Browser.Progress progress) {
        openApp(self, _fragment, bot, param, classGuid, progress, false, false);
    }

    // MurkaGram: dual UI, core-13 MessagesController.openApp
    public static void openApp(MessagesController self, BaseFragment _fragment, TLRPC.User bot, String param, int classGuid, Browser.Progress progress, boolean botCompact, boolean botFullscreen) {
        if (bot == null) return;

        boolean[] cancelled = new boolean[] { false };
        if (progress != null) {
            progress.onCancel(() -> cancelled[0] = true);
            progress.init();
        }

        final TL_bots.BotInfo[] botInfo = new TL_bots.BotInfo[1];
        Runnable open = () -> {
            BaseFragment fragment = _fragment != null ? _fragment : LaunchActivity.getSafeLastFragment();
            if (fragment == null) return;
            if (progress != null) {
                progress.end();
            }
            if (cancelled[0]) return;
            final String restriction_reason = self.getRestrictionReason(bot.restriction_reason);
            if (!TextUtils.isEmpty(restriction_reason)) {
                showCantOpenAlert(fragment, restriction_reason);
                return;
            }
            if (bot.bot_has_main_app) {
                long peerId = bot.id;
                if (fragment instanceof ChatActivity) {
                    peerId = ((ChatActivity) fragment).getDialogId();
                } else if (fragment instanceof ProfileActivity) {
                    peerId = ((ProfileActivity) fragment).getDialogId();
                }
                if (fragment.getParentLayout() instanceof ActionBarLayout) {
                    fragment = ((ActionBarLayout) fragment.getParentLayout()).getSheetFragment();
                }
                AndroidUtilities.hideKeyboard(fragment.getFragmentView());
                WebViewRequestProps props = WebViewRequestProps.of(self.currentAccount, peerId, bot.id, null, null, BotWebViewAttachedSheet.TYPE_WEB_VIEW_BOT_MAIN, 0, 0L, false, null, false, param, bot, 0, botCompact, botFullscreen);
                if (LaunchActivity.instance != null && LaunchActivity.instance.getBottomSheetTabs() != null && LaunchActivity.instance.getBottomSheetTabs().tryReopenTab(props) != null) {
                    return;
                }
                BotWebViewSheet webViewSheet = new BotWebViewSheet(fragment.getContext(), fragment.getResourceProvider());
                webViewSheet.setDefaultFullsize(true);
                webViewSheet.setNeedsContext(false);
                webViewSheet.setParentActivity(fragment.getParentActivity());
                webViewSheet.requestWebView(fragment, props);
                webViewSheet.show();
            } else if (botInfo[0] != null && botInfo[0].menu_button instanceof TL_bots.TL_botMenuButton) {
                if (fragment.getParentLayout() instanceof ActionBarLayout) {
                    fragment = ((ActionBarLayout) fragment.getParentLayout()).getSheetFragment();
                }
                TL_bots.TL_botMenuButton btn = (TL_bots.TL_botMenuButton) botInfo[0].menu_button;
                AndroidUtilities.hideKeyboard(fragment.getFragmentView());
                WebViewRequestProps props = WebViewRequestProps.of(self.currentAccount, bot.id, bot.id, btn.text, btn.url, BotWebViewAttachedSheet.TYPE_BOT_MENU_BUTTON, 0, 0L, false, null, false, param, bot, 0, botCompact, botFullscreen);
                if (LaunchActivity.instance != null && LaunchActivity.instance.getBottomSheetTabs() != null && LaunchActivity.instance.getBottomSheetTabs().tryReopenTab(props) != null) {
                    return;
                }
                BotWebViewSheet webViewSheet = new BotWebViewSheet(fragment.getContext(), fragment.getResourceProvider());
                webViewSheet.setDefaultFullsize(false);
                webViewSheet.setNeedsContext(true);
                webViewSheet.setParentActivity(fragment.getParentActivity());
                webViewSheet.requestWebView(fragment, props);
                webViewSheet.show();
            } else {
                fragment.presentFragment(ChatActivity.of(bot.id));
            }
        };

        botInfo[0] = self.getMediaDataController().getBotInfoCached(bot.id, bot.id);
        if (bot.bot_has_main_app) {
            open.run();
        } else if (botInfo[0] == null) {
            self.getMediaDataController().loadBotInfo(bot.id, bot.id, false, classGuid, _botInfo -> {
                if (cancelled[0]) return;
                botInfo[0] = _botInfo;
                if (botInfo[0] == null) {
                    TLRPC.UserFull userFull = self.getUserFull(bot.id);
                    if (userFull == null) {
                        if (cancelled[0]) return;
                        self.loadFullUser(bot, classGuid, true, userFull1 -> {
                            if (cancelled[0]) return;
                            if (userFull1 != null) {
                                botInfo[0] = userFull1.bot_info;
                            }
                            AndroidUtilities.runOnUIThread(open);
                        });
                    } else {
                        botInfo[0] = userFull.bot_info;
                        open.run();
                    }
                } else {
                    open.run();
                }
            });
        } else {
            open.run();
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.openByUserName
    public static void openByUserName(MessagesController self, String username, BaseFragment fragment, int type) {
        openByUserName(self, username, fragment, type, null);
    }

    // MurkaGram: dual UI, core-13 MessagesController.openByUserName
    public static void openByUserName(MessagesController self, String username, BaseFragment fragment, int type, Browser.Progress progress) {
        if (username == null || fragment == null) {
            return;
        }
        // MurkaGram: «Защита от мошенников» — юзернейм похож на официальный
        if (MurkaAntiScam.isEnabled() && fragment.getParentActivity() != null) {
            String murkaOfficial = MurkaAntiScam.findImpersonated(username);
            if (murkaOfficial != null) {
                org.telegram.messenger.ClassicMurkaAntiScam.showWarning(fragment, murkaOfficial, username, () -> murkaOpenByUserName(self, murkaOfficial, fragment, type, null), () -> murkaOpenByUserName(self, username, fragment, type, progress));
                return;
            }
        }
        murkaOpenByUserName(self, username, fragment, type, progress);
    }

    // MurkaGram: dual UI, core-13 MessagesController.openChatOrProfileWith
    public static void openChatOrProfileWith(MessagesController self, TLRPC.User user, TLRPC.Chat chat, BaseFragment fragment, int type, boolean closeLast) {
        if (user == null && chat == null || fragment == null) {
            return;
        }
        String reason;
        if (chat != null) {
            reason = self.getRestrictionReason(chat.restriction_reason);
        } else {
            reason = self.getRestrictionReason(user.restriction_reason);
            if (type != 3 && user.bot) {
                type = 1;
                BaseFragment lastFragment = LaunchActivity.getLastFragment();
                if (lastFragment.getLastStoryViewer() == null) {
                    closeLast = true;
                }
            }
        }
        boolean doNotCloseLast = false;
        BaseFragment lastFragment = LaunchActivity.getLastFragment();
        if (lastFragment instanceof DialogsActivity || lastFragment instanceof MainTabsActivity) {
            doNotCloseLast = true;
        }
        if (reason != null) {
            showCantOpenAlert(fragment, reason);
        } else {
            Bundle args = new Bundle();
            if (chat != null) {
                args.putLong("chat_id", chat.id);
            } else {
                args.putLong("user_id", user.id);
            }
            if (type == 0) {
                fragment.presentFragment(new ProfileActivity(args));
            } else if (type == 2) {
                if (ChatObject.isForum(chat)) {
                    fragment.presentFragment(TopicsFragment.getTopicsOrChat(fragment, args), !doNotCloseLast, true);
                } else {
                    fragment.presentFragment(new ChatActivity(args), !doNotCloseLast, true);
                }
            } else {
                if (ChatObject.isForum(chat)) {
                    fragment.presentFragment(TopicsFragment.getTopicsOrChat(fragment, args), closeLast && !doNotCloseLast);
                } else {
                    fragment.presentFragment(new ChatActivity(args), closeLast && !doNotCloseLast);
                }
            }
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.saveTheme
    public static void saveTheme(MessagesController self, Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent, boolean night, boolean unsave) {
        TLRPC.TL_theme info = accent != null ? accent.info : themeInfo.info;
        if (info != null) {
            TL_account.saveTheme req = new TL_account.saveTheme();
            TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
            inputTheme.id = info.id;
            inputTheme.access_hash = info.access_hash;
            req.theme = inputTheme;
            req.unsave = unsave;
            self.getConnectionsManager().sendRequest(req, (response, error) -> {

            });
            self.getConnectionsManager().resumeNetworkMaybe();
        }
        if (!unsave) {
            installTheme(self, themeInfo, accent, night);
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.saveThemeToServer
    public static void saveThemeToServer(MessagesController self, Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent) {
        if (themeInfo == null) {
            return;
        }
        String key;
        File pathToWallpaper;
        if (accent != null) {
            key = accent.saveToFile().getAbsolutePath();
            pathToWallpaper = accent.getPathToWallpaper();
        } else {
            key = themeInfo.pathToFile;
            pathToWallpaper = null;
        }
        if (key == null) {
            return;
        }
        if (self.uploadingThemes.containsKey(key)) {
            return;
        }
        self.uploadingThemes.put(key, accent != null ? accent : themeInfo);
        Utilities.globalQueue.postRunnable(() -> {
            String thumbPath = Theme.createThemePreviewImage(key, pathToWallpaper != null ? pathToWallpaper.getAbsolutePath() : null, accent);
            AndroidUtilities.runOnUIThread(() -> {
                if (thumbPath == null) {
                    self.uploadingThemes.remove(key);
                    return;
                }
                self.uploadingThemes.put(thumbPath, accent != null ? accent : themeInfo);
                if (accent == null) {
                    themeInfo.uploadingFile = key;
                    themeInfo.uploadingThumb = thumbPath;
                } else {
                    accent.uploadingFile = key;
                    accent.uploadingThumb = thumbPath;
                }
                self.getFileLoader().uploadFile(key, false, true, ConnectionsManager.FileTypeFile);
                self.getFileLoader().uploadFile(thumbPath, false, true, ConnectionsManager.FileTypePhoto);
            });
        });
    }

    // MurkaGram: dual UI, core-13 MessagesController.saveWallpaperToServer
    public static void saveWallpaperToServer(MessagesController self, File path, Theme.OverrideWallpaperInfo info, boolean install, long taskId) {
        if (self.uploadingWallpaper != null) {
            File finalPath = new File(ApplicationLoader.getFilesDirFixed(), info.originalFileName);
            if (path != null && (path.getAbsolutePath().equals(self.uploadingWallpaper) || path.equals(finalPath))) {
                self.uploadingWallpaperInfo = org.telegram.classic.ui.ActionBar.ThemeCompat.toModern(info); org.telegram.classic.ui.ClassicState.uploadingWallpaperInfo = info; // MurkaGram: dual UI
                return;
            }
            self.getFileLoader().cancelFileUpload(self.uploadingWallpaper, false);
            self.uploadingWallpaper = null;
            self.uploadingWallpaperInfo = null; org.telegram.classic.ui.ClassicState.uploadingWallpaperInfo = null; // MurkaGram: dual UI
        }
        if (path != null) {
            self.uploadingWallpaper = path.getAbsolutePath();
            self.uploadingWallpaperInfo = org.telegram.classic.ui.ActionBar.ThemeCompat.toModern(info); org.telegram.classic.ui.ClassicState.uploadingWallpaperInfo = info; // MurkaGram: dual UI
            self.getFileLoader().uploadFile(self.uploadingWallpaper, false, true, ConnectionsManager.FileTypePhoto);
        } else if (!info.isDefault() && !info.isColor() && info.wallpaperId > 0 && !info.isTheme()) {
            TLRPC.InputWallPaper inputWallPaper = getInputWallpaper(info);
            TLRPC.TL_wallPaperSettings settings = getWallpaperSetting(info);

            TLObject req;
            if (install) {
                TL_account.installWallPaper request = new TL_account.installWallPaper();
                request.wallpaper = inputWallPaper;
                request.settings = settings;
                req = request;
            } else {
                TL_account.saveWallPaper request = new TL_account.saveWallPaper();
                request.wallpaper = inputWallPaper;
                request.settings = settings;
                req = request;
            }

            long newTaskId;
            if (taskId != 0) {
                newTaskId = taskId;
            } else {
                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(1024);
                    data.writeInt32(21);
                    data.writeBool(info.isBlurred);
                    data.writeBool(info.isMotion);
                    data.writeInt32(info.color);
                    data.writeInt32(info.gradientColor1);
                    data.writeInt32(info.rotation);
                    data.writeDouble(info.intensity);
                    data.writeBool(install);
                    data.writeString(info.slug);
                    data.writeString(info.originalFileName);
                    data.limit(data.position());
                } catch (Exception e) {
                    FileLog.e(e);
                }
                newTaskId = self.getMessagesStorage().createPendingTask(data);
            }

            self.getConnectionsManager().sendRequest(req, (response, error) -> self.getMessagesStorage().removePendingTask(newTaskId));
        }
        if ((info.isColor() || info.gradientColor2 != 0) && info.wallpaperId <= 0) {
            TLRPC.WallPaper wallPaper;
            if (info.isColor()) {
                wallPaper = new TLRPC.TL_wallPaperNoFile();
            } else {
                wallPaper = new TLRPC.TL_wallPaper();
                wallPaper.slug = info.slug;
                wallPaper.document = new TLRPC.TL_documentEmpty();
            }
            if (info.wallpaperId == 0) {
                wallPaper.id = Utilities.random.nextLong();
                if (wallPaper.id > 0) {
                    wallPaper.id = -wallPaper.id;
                }
            } else {
                wallPaper.id = info.wallpaperId;
            }
            wallPaper.dark = MotionBackgroundDrawable.isDark(info.color, info.gradientColor1, info.gradientColor2, info.gradientColor3);
            wallPaper.flags |= 4;
            wallPaper.settings = new TLRPC.TL_wallPaperSettings();
            wallPaper.settings.blur = info.isBlurred;
            wallPaper.settings.motion = info.isMotion;
            if (info.color != 0) {
                wallPaper.settings.background_color = info.color;
                wallPaper.settings.flags |= 1;
                wallPaper.settings.intensity = (int) (info.intensity * 100);
                wallPaper.settings.flags |= 8;
            }
            if (info.gradientColor1 != 0) {
                wallPaper.settings.second_background_color = info.gradientColor1;
                wallPaper.settings.rotation = AndroidUtilities.getWallpaperRotation(info.rotation, true);
                wallPaper.settings.flags |= 16;
            }
            if (info.gradientColor2 != 0) {
                wallPaper.settings.third_background_color = info.gradientColor2;
                wallPaper.settings.flags |= 32;
            }
            if (info.gradientColor3 != 0) {
                wallPaper.settings.fourth_background_color = info.gradientColor3;
                wallPaper.settings.flags |= 64;
            }
            ArrayList<TLRPC.WallPaper> arrayList = new ArrayList<>();
            arrayList.add(wallPaper);
            self.getMessagesStorage().putWallpapers(arrayList, -3);
            self.getMessagesStorage().getWallpapers();
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.setDefaultBannedRole
    public static void setDefaultBannedRole(MessagesController self, long chatId, TLRPC.TL_chatBannedRights rights, boolean isChannel, BaseFragment parentFragment) {
        if (rights == null) {
            return;
        }
        TLRPC.TL_messages_editChatDefaultBannedRights req = new TLRPC.TL_messages_editChatDefaultBannedRights();
        req.peer = self.getInputPeer(-chatId);
        req.banned_rights = rights;
        self.getConnectionsManager().sendRequestTypedAndProcessUpdates(req, AndroidUtilities::runOnUIThread, (response, error) -> {
            if (error == null) {
                AndroidUtilities.runOnUIThread(() -> self.loadFullChat(chatId, 0, true), 1000);
            } else {
                AlertsCreator.processError(self.currentAccount, error, parentFragment, req, isChannel);
            }
        });
    }

    // MurkaGram: dual UI, core-13 MessagesController.setParticipantBannedRole
    public static void setParticipantBannedRole(MessagesController self, long chatId, TLRPC.User user, TLRPC.Chat chat, TLRPC.TL_chatBannedRights rights, boolean isChannel, BaseFragment parentFragment) {
        setParticipantBannedRole(self, chatId, user, chat, rights, isChannel, parentFragment, null);
    }

    // MurkaGram: dual UI, core-13 MessagesController.setParticipantBannedRole
    public static void setParticipantBannedRole(MessagesController self, long chatId, TLRPC.User user, TLRPC.Chat chat, TLRPC.TL_chatBannedRights rights, boolean isChannel, BaseFragment parentFragment, Runnable whenDone) {
        if (user == null && chat == null || rights == null) {
            return;
        }
        TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
        req.channel = self.getInputChannel(chatId);
        if (user != null) {
            req.participant = self.getInputPeer(user);
        } else {
            req.participant = self.getInputPeer(chat);
        }
        req.banned_rights = rights;
        self.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                self.processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> self.loadFullChat(chatId, 0, true), 1000);
                if (whenDone != null) {
                    AndroidUtilities.runOnUIThread(whenDone);
                }
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(self.currentAccount, error, parentFragment, req, isChannel));
            }
        });
    }

    // MurkaGram: dual UI, core-13 MessagesController.setUserAdminRole
    public static void setUserAdminRole(MessagesController self, long chatId, TLRPC.User user, TLRPC.TL_chatAdminRights rights, String rank, boolean isChannel, BaseFragment parentFragment, boolean addingNew, boolean forceAdmin, String botHash, Runnable onSuccess) {
        setUserAdminRole(self, chatId, user, rights, rank, isChannel, parentFragment, addingNew, forceAdmin, botHash, onSuccess, null);
    }

    // MurkaGram: dual UI, core-13 MessagesController.setUserAdminRole
    public static void setUserAdminRole(MessagesController self, long chatId, TLRPC.User user, TLRPC.TL_chatAdminRights rights, String rank, boolean isChannel, BaseFragment parentFragment, boolean addingNew, boolean forceAdmin, String botHash, Runnable onSuccess, MessagesController.ErrorDelegate onError) {
        if (user == null || rights == null) {
            return;
        }
        TLRPC.Chat chat = self.getChat(chatId);
        final boolean isCommunity = ChatObject.isCommunity(chat);
        if (ChatObject.isChannel(chat)) {
            TLRPC.TL_channels_editAdmin req = new TLRPC.TL_channels_editAdmin();
            req.channel = self.getInputChannel(chat);
            req.user_id = self.getInputUser(user);
            req.admin_rights = rights;
            if (!TextUtils.isEmpty(rank)) {
                req.flags |= 1;
                req.rank = rank;
            }
            RequestDelegate requestDelegate = (response, error) -> {
                if (error == null) {
                    self.processUpdates((TLRPC.Updates) response, false);
                    AndroidUtilities.runOnUIThread(() -> {
                        self.loadFullChat(chatId, 0, true);
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    }, 1000);
                } else {
                    if (error != null && "USER_PRIVACY_RESTRICTED".equals(error.text) && !ChatObject.isCommunity(chat) && ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE)) {
                        AndroidUtilities.runOnUIThread(() -> {
                            BaseFragment lastFragment = LaunchActivity.getLastFragment();
                            if (lastFragment != null && lastFragment.getParentActivity() != null) {
                                LimitReachedBottomSheet restricterdUsersBottomSheet = new LimitReachedBottomSheet(lastFragment, lastFragment.getParentActivity(), LimitReachedBottomSheet.TYPE_ADD_MEMBERS_RESTRICTED, self.currentAccount, null);
                                ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                                users.add(user);
                                restricterdUsersBottomSheet.setRestrictedUsers(chat, users, null, null, null);
                                restricterdUsersBottomSheet.show();
                            }
                            onError.run(error);
                        });
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(self.currentAccount, error, parentFragment, req, isChannel, isCommunity));
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.run(error));
                    }
                }
            };
            if (!user.bot && addingNew) {
                addUserToChat(self, chatId, user, 0, botHash, parentFragment, true, () -> self.getConnectionsManager().sendRequest(req, requestDelegate), onError);
            } else {
                self.getConnectionsManager().sendRequest(req, requestDelegate);
            }
        } else {
            TLRPC.TL_messages_editChatAdmin req = new TLRPC.TL_messages_editChatAdmin();
            req.chat_id = chatId;
            req.user_id = self.getInputUser(user);
            req.is_admin = forceAdmin || rights.change_info || rights.delete_messages || rights.ban_users || rights.invite_users || rights.pin_messages || rights.add_admins || rights.manage_call;
            RequestDelegate requestDelegate = (response, error) -> {
                if (error == null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        self.loadFullChat(chatId, 0, true);
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    }, 1000);
                } else {
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(self.currentAccount, error, parentFragment, req, false));
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.run(error));
                    }
                }
            };
            if (req.is_admin || addingNew || !TextUtils.isEmpty(botHash)) {
                addUserToChat(self, chatId, user, 0, botHash, parentFragment, true, () -> self.getConnectionsManager().sendRequest(req, requestDelegate), onError);
            } else {
                self.getConnectionsManager().sendRequest(req, requestDelegate);
            }
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.showCantOpenAlert
    public static void showCantOpenAlert(BaseFragment fragment, String reason) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.DialogNotAvailable));
        Map<String, Integer> colorsReplacement = new HashMap<>();
        colorsReplacement.put("info1", fragment.getThemedColor(Theme.key_dialogTopBackground));
        colorsReplacement.put("info2", fragment.getThemedColor(Theme.key_dialogTopBackground));
        builder.setTopAnimation(R.raw.not_available, AlertsCreator.NEW_DENY_DIALOG_TOP_ICON_SIZE, false, fragment.getThemedColor(Theme.key_dialogTopBackground), colorsReplacement);
        builder.setTopAnimationIsNew(true);
        builder.setPositiveButton(LocaleController.getString(R.string.Close), null);
        builder.setMessage(reason);
        fragment.showDialog(builder.create());
    }

    // MurkaGram: dual UI, core-13 MessagesController.updateChannelUserName
    public static void updateChannelUserName(MessagesController self, BaseFragment fragment, long chatId, String userName, Runnable runnable, Runnable onError) {
        TLRPC.TL_channels_updateUsername req = new TLRPC.TL_channels_updateUsername();
        req.channel = self.getInputChannel(chatId);
        req.username = userName;
        self.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_boolTrue || error != null && "USERNAME_NOT_MODIFIED".equals(error.text)) {
                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.Chat chat = self.getChat(chatId);
                    boolean hasMain = false;
                    if (chat.usernames != null) {
                        for (int i = 0; i < chat.usernames.size(); ++i) {
                            TLRPC.TL_username u = chat.usernames.get(i);
                            if (u != null && u.editable) {
                                hasMain = true;
                                u.username = userName;
                                break;
                            }
                        }
                    }
                    if (!hasMain) {
                        if (chat.usernames == null || chat.usernames.size() >= 1) {
                            if (chat.usernames == null) {
                                chat.usernames = new ArrayList<>();
                            }
                            TLRPC.TL_username u = new TLRPC.TL_username();
                            u.username = userName;
                            u.active = true;
                            u.editable = true;
                            chat.usernames.add(0, u);
                        } else {
                            chat.username = userName;
                        }
                    }
                    ArrayList<TLRPC.Chat> arrayList = new ArrayList<>();
                    arrayList.add(chat);
                    self.getMessagesStorage().putUsersAndChats(null, arrayList, true, true);
                    self.getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
                    if (runnable != null) {
                        runnable.run();
                    }
                });
            } else {
                AlertsCreator.processError(UserConfig.selectedAccount, error, fragment, req);
                if (onError != null) {
                    onError.run();
                }
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    // MurkaGram: dual UI, core-13 MessagesController.murkaOpenByUserName
    public static void murkaOpenByUserName(MessagesController self, String username, BaseFragment fragment, int type, Browser.Progress progress) {
        if (username == null || fragment == null) {
            return;
        }
        TLObject object = self.getUserOrChat(username);
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        if (object instanceof TLRPC.User) {
            user = (TLRPC.User) object;
            if (user.min) {
                user = null;
            }
        } else if (object instanceof TLRPC.Chat) {
            chat = (TLRPC.Chat) object;
            if (chat.min) {
                chat = null;
            }
        }
        if (user != null) {
            openChatOrProfileWith(self, user, null, fragment, type, false);
        } else if (chat != null) {
            openChatOrProfileWith(self, null, chat, fragment, 1, false);
        } else {
            if (fragment.getParentActivity() == null) {
                return;
            }
            AlertDialog[] progressDialog = new AlertDialog[] {
                new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER)
            };
            boolean[] canceled = new boolean[]{false};
            self.getMessagesController().getUserNameResolver().resolve(username, (peerId) -> {
                try {
                    if (progress != null) {
                        progress.end();
                    } else {
                        progressDialog[0].dismiss();
                    }
                } catch (Exception ignored) {}
                progressDialog[0] = null;
                fragment.setVisibleDialog(null);
                if (canceled[0]) {
                    return;
                }
                if (peerId != null) {
                    if (peerId < 0) {
                        openChatOrProfileWith(self, null, self.getChat(-peerId), fragment, 1, false);
                    } else {
                        openChatOrProfileWith(self, self.getUser(peerId), null, fragment, type, false);
                    }
                } else {
                    if (fragment.getParentActivity() != null) {
                        try {
                            if (fragment instanceof ChatActivity) {
                                ((ChatActivity) fragment).shakeContent();
                            }
                            BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.NoUsernameFound)).show();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            });
            if (progress != null) {
                progress.onCancel(() -> canceled[0] = true);
                progress.init();
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    if (progressDialog[0] == null) {
                        return;
                    }
                    progressDialog[0].setOnCancelListener(dialog -> canceled[0] = true);
                    fragment.showDialog(progressDialog[0]);
                }, 500);
            }
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.installTheme
    public static void installTheme(MessagesController self, Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent, boolean night) {
        TLRPC.TL_theme info = accent != null ? accent.info : themeInfo.info;
        String slug = accent != null ? accent.patternSlug : themeInfo.slug;
        boolean isBlured = accent == null && themeInfo.isBlured;
        boolean isMotion = accent != null ? accent.patternMotion : themeInfo.isMotion;

        TL_account.installTheme req = new TL_account.installTheme();
        req.dark = night;
        if (info != null) {
            req.format = "android";
            TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
            inputTheme.id = info.id;
            inputTheme.access_hash = info.access_hash;
            req.theme = inputTheme;
            req.flags |= 2;
        }
        self.getConnectionsManager().sendRequest(req, (response, error) -> {

        });

        if (!TextUtils.isEmpty(slug)) {
            TL_account.installWallPaper req2 = new TL_account.installWallPaper();
            TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
            inputWallPaperSlug.slug = slug;
            req2.wallpaper = inputWallPaperSlug;
            req2.settings = new TLRPC.TL_wallPaperSettings();
            req2.settings.blur = isBlured;
            req2.settings.motion = isMotion;
            self.getConnectionsManager().sendRequest(req2, (response, error) -> {

            });
        }
    }

    // MurkaGram: dual UI, core-13 MessagesController.getInputWallpaper
    public static TLRPC.InputWallPaper getInputWallpaper(Theme.OverrideWallpaperInfo info) {
        TLRPC.InputWallPaper inputWallPaper;
        if (info.wallpaperId > 0) {
            TLRPC.TL_inputWallPaper inputWallPaperId = new TLRPC.TL_inputWallPaper();
            inputWallPaperId.id = info.wallpaperId;
            inputWallPaperId.access_hash = info.accessHash;
            inputWallPaper = inputWallPaperId;
        } else {
            TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
            inputWallPaperSlug.slug = info.slug;
            inputWallPaper = inputWallPaperSlug;
        }
        return inputWallPaper;
    }

    // MurkaGram: dual UI, core-13 MessagesController.getWallpaperSetting
    public static TLRPC.TL_wallPaperSettings getWallpaperSetting(Theme.OverrideWallpaperInfo info) {
        TLRPC.TL_wallPaperSettings settings = new TLRPC.TL_wallPaperSettings();
        settings.blur = info.isBlurred;
        settings.motion = info.isMotion;
        if (info.color != 0) {
            settings.background_color = info.color & 0x00ffffff;
            settings.flags |= 1;
            settings.intensity = (int) (info.intensity * 100);
            settings.flags |= 8;
        } else if (info.intensity > 0) {
            settings.intensity = (int) (info.intensity * 100);
            settings.flags |= 8;
        }
        if (info.gradientColor1 != 0) {
            settings.second_background_color = info.gradientColor1 & 0x00ffffff;
            settings.rotation = AndroidUtilities.getWallpaperRotation(info.rotation, true);
            settings.flags |= 16;
        }
        if (info.gradientColor2 != 0) {
            settings.third_background_color = info.gradientColor2 & 0x00ffffff;
            settings.flags |= 32;
        }
        if (info.gradientColor3 != 0) {
            settings.fourth_background_color = info.gradientColor3 & 0x00ffffff;
            settings.flags |= 64;
        }
        return settings;
    }

}
