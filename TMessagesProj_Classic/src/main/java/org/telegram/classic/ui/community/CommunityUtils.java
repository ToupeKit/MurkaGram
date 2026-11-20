package org.telegram.classic.ui.community;


import org.telegram.ui.community.CommunityChatType;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_communities;

// MurkaGram: partial (core-13) — only the helpers used by the core; the community UI is not taken
public class CommunityUtils {
    public static final boolean COLLAPSED_SUPPORT = false; // MurkaGram: our dialog list can't show collapsed communities

    private CommunityUtils() {

    }

    public static CommunityChatType getCommunityChatType(int currentAccount, long dialogId) {
        final long linkedCommunityId;
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        if (dialogId > 0) {
            user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user == null) {
                return null;
            }
            linkedCommunityId = user.linked_community_id;
        } else {
            chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat == null) {
                return null;
            }
            linkedCommunityId = chat.linked_community_id;
        }
        if (linkedCommunityId == 0) {
            return null;
        }

        final TLRPC.ChatFull communityFull = MessagesController.getInstance(currentAccount).getChatFull(linkedCommunityId);
        if (communityFull == null || communityFull.linked_peers == null) {
            return null;
        }

        for (TL_communities.CommunityPeer communityPeer : communityFull.linked_peers) {
            if (DialogObject.getPeerDialogId(communityPeer.peer) == dialogId) {
                return getCommunityChatType(chat, user,
                    user != null ? MessagesController.getInstance(currentAccount).getDialog(user.id) : null, communityPeer);
            }
        }

        return null;
    }

    public static CommunityChatType getCommunityChatType(TLRPC.Chat chat, TLRPC.User user, TLRPC.Dialog dialog, TL_communities.CommunityPeer peer)  {
        if (peer == null) {
            return null;
        }

        if (user != null) {
            return dialog != null ? CommunityChatType.YouAreIn : CommunityChatType.YouCanView;
        } else if (chat != null) {
            if (ChatObject.isInChat(chat)) {
                return CommunityChatType.YouAreIn;
            }
            if (ChatObject.isPublic(chat) || peer.can_view_history) {
                return CommunityChatType.YouCanView;
            }
            if (ChatObject.isCommunityPeerHidden(peer)) {
                return CommunityChatType.HiddenUnavailable;
            }
            return CommunityChatType.YouCanSendJoinRequest;
        }
        return null;
    }

    public static CharSequence buildServiceMessageText(MessageObject messageObject,
                                                       String communityName,
                                                       String userName,
                                                       boolean isChannel,
                                                       boolean isBot
    ) {
        final TLRPC.Message message = messageObject.messageOwner;
        final TLRPC.TL_messageActionChangeCommunity action = (TLRPC.TL_messageActionChangeCommunity) message.action;
        final boolean isUnknown = DialogObject.getPeerDialogId(message.peer_id) == DialogObject.getPeerDialogId(message.from_id);
        final boolean isRemoved = action.community_id == 0;

        if (isUnknown) {
            if (isRemoved) {
                return replaceTags(getString(isBot ?
                    R.string.CommunityServiceMessageBotRemovedUnknown : isChannel ?
                    R.string.CommunityServiceMessageChannelRemovedUnknown :
                    R.string.CommunityServiceMessageGroupRemovedUnknown));
            } else {
                return replaceTags(formatString(isBot ?
                    R.string.CommunityServiceMessageBotAddedUnknown : isChannel ?
                    R.string.CommunityServiceMessageChannelAddedUnknown :
                    R.string.CommunityServiceMessageGroupAddedUnknown, communityName));
            }
        } else if (messageObject.isOut()) {
            if (isRemoved) {
                return replaceTags(getString(isBot ?
                    R.string.CommunityServiceMessageBotYouRemoved : isChannel ?
                    R.string.CommunityServiceMessageChannelYouRemoved :
                    R.string.CommunityServiceMessageGroupYouRemoved));
            } else {
                return replaceTags(formatString(isBot ?
                    R.string.CommunityServiceMessageBotYouAdded : isChannel ?
                    R.string.CommunityServiceMessageChannelYouAdded :
                    R.string.CommunityServiceMessageGroupYouAdded, communityName));
            }
        } else {
            if (isRemoved) {
                return replaceTags(formatString(isBot ?
                    R.string.CommunityServiceMessageBotRemoved : isChannel ?
                    R.string.CommunityServiceMessageChannelRemoved :
                    R.string.CommunityServiceMessageGroupRemoved, userName));
            } else {
                return replaceTags(formatString(isBot ?
                    R.string.CommunityServiceMessageBotAdded : isChannel ?
                    R.string.CommunityServiceMessageChannelAdded :
                    R.string.CommunityServiceMessageGroupAdded, userName, communityName));
            }
        }
    }
}
