package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONObject;
import org.telegram.classic.ui.ActionBar.AlertDialog;
import org.telegram.classic.ui.ActionBar.BaseFragment;
import org.telegram.classic.ui.ActionBar.Theme;
import org.telegram.classic.ui.ActionBar.ThemeBind;
import org.telegram.classic.ui.Cells.ChatMessageCell;
import org.telegram.classic.ui.ChatActivity;
import org.telegram.classic.ui.Components.AlertsCreator;
import org.telegram.classic.ui.Components.Bulletin;
import org.telegram.classic.ui.Components.LayoutHelper;
import org.telegram.classic.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.classic.ui.Components.poll.attached.PollAttachedMediaFile;
import org.telegram.classic.ui.Components.poll.attached.PollAttachedMediaGallery;
import org.telegram.classic.ui.Components.poll.attached.PollAttachedMediaLink;
import org.telegram.classic.ui.Components.poll.attached.PollAttachedMediaLocation;
import org.telegram.classic.ui.Components.poll.attached.PollAttachedMediaMusic;
import org.telegram.classic.ui.Components.poll.attached.PollAttachedMediaSticker;
import org.telegram.classic.ui.LaunchActivity;
import org.telegram.classic.ui.PaymentFormActivity;
import org.telegram.classic.ui.Stars.StarsIntroActivity;
import org.telegram.classic.ui.TON.TONIntroActivity;
import org.telegram.classic.ui.TwoStepVerificationActivity;
import org.telegram.classic.ui.TwoStepVerificationSetupActivity;
import org.telegram.classic.ui.bots.BotWebViewSheet;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.messenger.utils.EphemeralMessagesHelper;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.messenger.utils.tlutils.TLKeyboardHelper;
import org.telegram.messenger.utils.tlutils.TlUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_ephemeral;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimatedFileNative;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.Reactions.ReactionsUtils;
import org.telegram.ui.Components.poll.PollAttachedMedia;
import org.telegram.ui.Components.poll.PollAttachedMediaPack;
import org.telegram.ui.Components.poll.PollSendParams;
import org.telegram.ui.Components.voip.AnimatedFileInfo;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stories.MessageMediaStoryFull;

// MurkaGram: dual UI. Methods of SendMessagesHelper whose parameters are UI types, copied from the core-13 (12.2.10 UI) version
// so that the classic UI can call them with its own types. Generated by murka-plans/tools/dual/classic_k10.py.
public final class ClassicSendMessagesHelper {

    private ClassicSendMessagesHelper() {
    }

    // MurkaGram: dual UI, core-13 SendMessagesHelper.editMessage
    public static void editMessage(SendMessagesHelper self, MessageObject messageObject, TLRPC.TL_photo photo, VideoEditedInfo videoEditedInfo, TLRPC.TL_document document, String path, TLRPC.PhotoSize cover, HashMap<String, String> params, boolean retry, boolean hasMediaSpoilers, Object parentObject) {
        editMessage(self, messageObject, null, photo, videoEditedInfo, document, path, cover, params, retry, hasMediaSpoilers, parentObject);
    }

    // MurkaGram: dual UI, core-13 SendMessagesHelper.editMessage
    public static void editMessage(SendMessagesHelper self, MessageObject messageObject, TLRPC.TL_inputPollAnswer pollToAddOptionMessageObject, TLRPC.TL_photo photo, VideoEditedInfo videoEditedInfo, TLRPC.TL_document document, String path, TLRPC.PhotoSize cover, HashMap<String, String> params, boolean retry, boolean hasMediaSpoilers, Object parentObject) {
        if (messageObject == null) {
            return;
        }
        if (params == null) {
            params = new HashMap<>();
        }

        TLRPC.Message newMsg = messageObject.messageOwner;
        messageObject.cancelEditing = false;

        int pollAddingIndex = -1;

        try {
            int type = -1;
            SendMessagesHelper.DelayedMessage delayedMessage = null;
            long peer = messageObject.getDialogId();
            boolean supportsSendingNewEntities = true;
            if (DialogObject.isEncryptedDialog(peer)) {
                int encryptedId = DialogObject.getEncryptedChatId(peer);
                TLRPC.EncryptedChat encryptedChat = self.getMessagesController().getEncryptedChat(encryptedId);
                if (encryptedChat == null || AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) < 101) {
                    supportsSendingNewEntities = false;
                }
            }

            if (retry) {
                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage || messageObject.messageOwner.media == null || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty) {
                    type = 1;
                } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                    photo = (TLRPC.TL_photo) messageObject.messageOwner.media.photo;
                    type = 2;
                } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                    document = (TLRPC.TL_document) messageObject.messageOwner.media.document;
                    if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
                        type = 3;
                    } else {
                        type = 7;
                    }
                    videoEditedInfo = messageObject.videoEditedInfo;
                } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaToDo) {
                    type = 10;
                }
                params = newMsg.params;
                if (parentObject == null && params != null && params.containsKey("parentObject")) {
                    parentObject = params.get("parentObject");
                }
                messageObject.editingMessage = newMsg.message;
                messageObject.editingMessageEntities = newMsg.entities;
                path = newMsg.attachPath;
            } else {
                messageObject.previousMedia = newMsg.media;
                messageObject.previousMessage = newMsg.message;
                messageObject.previousMessageEntities = newMsg.entities;
                messageObject.previousAttachPath = newMsg.attachPath;

                TLRPC.MessageMedia media = newMsg.media;
                if (media == null) {
                    media = new TLRPC.TL_messageMediaEmpty();
                }
                final SerializedData serializedDataCalc = new SerializedData(true);
                self.writePreviousMessageData(newMsg, serializedDataCalc);
                final SerializedData prevMessageData = new SerializedData(serializedDataCalc.length());
                self.writePreviousMessageData(newMsg, prevMessageData);
                if (params == null) {
                    params = new HashMap<>();
                }
                params.put("prevMedia", Base64.encodeToString(prevMessageData.toByteArray(), Base64.DEFAULT));
                prevMessageData.cleanup();

                if (pollToAddOptionMessageObject != null) {
                    TLRPC.TL_messageMediaPoll messageMediaPoll = (TLRPC.TL_messageMediaPoll) newMsg.media;
                    TLRPC.TL_pollAnswer pollAnswer = new TLRPC.TL_pollAnswer();
                    pollAnswer.text = pollToAddOptionMessageObject.text;
                    pollAnswer.option = pollToAddOptionMessageObject.option;
                    pollAddingIndex = messageMediaPoll.poll.answers.size();
                    messageMediaPoll.poll.answers.add(pollAnswer);

                    messageObject.documentToPollAddOption = document;

                    if (photo != null) {
                        pollAnswer.media = new TLRPC.TL_messageMediaPhoto();
                        pollAnswer.media.flags |= 3;
                        pollAnswer.media.photo = photo;
                        pollAnswer.media.spoiler = hasMediaSpoilers;
                        type = 2;
                        if (path != null && path.length() > 0 && path.startsWith("http")) {
                            newMsg.attachPath = path;
                            PollAttachedMediaPack.setAttachPath(newMsg, path, pollAddingIndex);
                        } else {
                            TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
                            newMsg.attachPath = FileLoader.getInstance(self.currentAccount).getPathToAttach(location1, true).toString();
                            PollAttachedMediaPack.setAttachPath(newMsg, FileLoader.getInstance(self.currentAccount).getPathToAttach(location1, true).toString(), pollAddingIndex);
                        }
                    } else if (document != null) {
                        pollAnswer.media = new TLRPC.TL_messageMediaDocument();
                        pollAnswer.media.flags |= 3;
                        pollAnswer.media.document = document;
                        pollAnswer.media.spoiler = hasMediaSpoilers;
                        if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
                            type = 3;
                        } else {
                            type = 7;
                        }
                        if (videoEditedInfo != null) {
                            String ve = videoEditedInfo.getString();
                            params.put("ve", ve);
                        }
                        newMsg.attachPath = path;
                        PollAttachedMediaPack.setAttachPath(newMsg, path, pollAddingIndex);
                        if (cover instanceof ImageLoader.PhotoSizeFromPhoto) {
                            ImageLoader.PhotoSizeFromPhoto s = (ImageLoader.PhotoSizeFromPhoto) cover;
                            pollAnswer.media.flags |= 512;
                            pollAnswer.media.video_cover = s.photo;
                        } else if (cover != null) {
                            TLRPC.TL_photo coverPhoto = new TLRPC.TL_photo();
                            coverPhoto.date = self.getConnectionsManager().getCurrentTime();
                            coverPhoto.sizes.add(cover);
                            coverPhoto.file_reference = new byte[0];
                            pollAnswer.media.video_cover = coverPhoto;
                            pollAnswer.media.flags |= 512;
                        }
                    } else if (pollToAddOptionMessageObject.input_media != null) {
                        type = 11;
                    } else {
                        type = 1;
                    }
                } else if (photo != null) {
                    newMsg.media = new TLRPC.TL_messageMediaPhoto();
                    newMsg.media.flags |= 3;
                    newMsg.media.photo = photo;
                    newMsg.media.spoiler = hasMediaSpoilers;
                    type = 2;
                    if (path != null && path.length() > 0 && path.startsWith("http")) {
                        newMsg.attachPath = path;
                    } else {
                        TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
                        newMsg.attachPath = FileLoader.getInstance(self.currentAccount).getPathToAttach(location1, true).toString();
                    }
                } else if (document != null) {
                    newMsg.media = new TLRPC.TL_messageMediaDocument();
                    newMsg.media.flags |= 3;
                    newMsg.media.document = document;
                    newMsg.media.spoiler = hasMediaSpoilers;
                    if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
                        type = 3;
                    } else {
                        type = 7;
                    }
                    if (videoEditedInfo != null) {
                        String ve = videoEditedInfo.getString();
                        params.put("ve", ve);
                    }
                    newMsg.attachPath = path;
                    if (cover instanceof ImageLoader.PhotoSizeFromPhoto) {
                        ImageLoader.PhotoSizeFromPhoto s = (ImageLoader.PhotoSizeFromPhoto) cover;
                        newMsg.media.flags |= 512;
                        newMsg.media.video_cover = s.photo;
                    } else if (cover != null) {
                        TLRPC.TL_photo coverPhoto = new TLRPC.TL_photo();
                        coverPhoto.date = self.getConnectionsManager().getCurrentTime();
                        coverPhoto.sizes.add(cover);
                        coverPhoto.file_reference = new byte[0];
                        newMsg.media.video_cover = coverPhoto;
                        newMsg.media.flags |= 512;
                    }
                } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaToDo) {
                    type = 10;
                } else {
                    type = 1;
                }

                newMsg.params = params;
                newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_EDITING;
                newMsg.errorNewPriceStars = 0;
                newMsg.errorAllowedPriceStars = 0;
            }
            if (newMsg.attachPath == null) {
                newMsg.attachPath = "";
            }
            newMsg.local_id = 0;
            if ((messageObject.type == MessageObject.TYPE_VIDEO || videoEditedInfo != null || messageObject.type == MessageObject.TYPE_VOICE) && !TextUtils.isEmpty(newMsg.attachPath)) {
                messageObject.attachPathExists = true;
            }
            if (messageObject.videoEditedInfo != null && videoEditedInfo == null) {
                videoEditedInfo = messageObject.videoEditedInfo;
            }

            if (!retry) {
                if (messageObject.editingMessage != null) {
                    String oldMessge = newMsg.message;
                    newMsg.message = messageObject.editingMessage.toString();
                    messageObject.caption = null;
                    if (type == 1) {
                        if (messageObject.editingMessageEntities != null) {
                            newMsg.entities = messageObject.editingMessageEntities;
                            newMsg.flags |= 128;
                        } else if (!TextUtils.equals(oldMessge, newMsg.message)) {
                            newMsg.flags &=~ 128;
                        }
                        if (messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia) {
                            messageObject.generateCaption();
                        }
                    } else {
                        if (messageObject.editingMessageEntities != null) {
                            newMsg.entities = messageObject.editingMessageEntities;
                            newMsg.flags |= 128;
                        } else {
                            CharSequence[] message = new CharSequence[]{messageObject.editingMessage};
                            ArrayList<TLRPC.MessageEntity> entities = self.getMediaDataController().getEntities(message, supportsSendingNewEntities);
                            if (entities != null && !entities.isEmpty()) {
                                newMsg.entities = entities;
                                newMsg.flags |= 128;
                            } else if (!TextUtils.equals(oldMessge, newMsg.message)) {
                                newMsg.flags &=~ 128;
                            }
                        }
                        messageObject.generateCaption();
                    }
                }

                ArrayList<TLRPC.Message> arr = new ArrayList<>();
                arr.add(newMsg);
                self.getMessagesStorage().putMessages(arr, false, true, false, 0, messageObject.scheduled ? 1 : 0, 0);
                self.getMessagesController().getTopicsController().processEditedMessage(newMsg);

                messageObject.type = -1;
                messageObject.setType();
                if (type == 1) {
                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                        messageObject.generateCaption();
                    } else {
                        messageObject.resetLayout();
                        messageObject.checkLayout();
                    }
                }
                messageObject.createMessageSendInfo(pollToAddOptionMessageObject != null);
                ArrayList<MessageObject> arrayList = new ArrayList<>();
                arrayList.add(messageObject);
                self.getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, peer, arrayList);
            }

            String originalPath = null;
            if (params != null && params.containsKey("originalPath")) {
                originalPath = params.get("originalPath");
            }

            boolean performMediaUpload = false, performCoverUpload = false;

            if (type >= 1 && type <= 3 || type >= 5 && type <= 8 || type == 10 || type == 11) {
                TLRPC.InputMedia inputMedia = null;
                if (type == 11) {
                    inputMedia = pollToAddOptionMessageObject.input_media;
                } else if (type == 1) {
                    if ((newMsg.media == null || newMsg.media instanceof TLRPC.TL_messageMediaEmpty || newMsg.media != null && newMsg.media.webpage instanceof TLRPC.TL_webPageEmpty) && !messageObject.editingMessageSearchWebPage) {
                        inputMedia = new TLRPC.TL_inputMediaEmpty();
                    } else if (newMsg != null && newMsg.media != null && newMsg.media.webpage != null) {
                        TLRPC.TL_inputMediaWebPage inputWebpage = new TLRPC.TL_inputMediaWebPage();
                        inputWebpage.url = newMsg.media.webpage.url;
                        inputWebpage.force_small_media = newMsg.media.force_small_media;
                        inputWebpage.force_large_media = newMsg.media.force_large_media;
                        inputMedia = inputWebpage;
                    }
                } else if (type == 2) {
                    TLRPC.TL_inputMediaUploadedPhoto uploadedPhoto = new TLRPC.TL_inputMediaUploadedPhoto();
                    uploadedPhoto.spoiler = hasMediaSpoilers;
                    if (params != null) {
                        String masks = params.get("masks");
                        if (masks != null) {
                            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(masks));
                            int count = serializedData.readInt32(false);
                            for (int a = 0; a < count; a++) {
                                uploadedPhoto.stickers.add(TLRPC.InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false));
                            }
                            uploadedPhoto.flags |= 1;
                            serializedData.cleanup();
                        }
                    }

                    if (photo.access_hash == 0) {
                        inputMedia = uploadedPhoto;
                        performMediaUpload = true;
                    } else {
                        TLRPC.TL_inputMediaPhoto media = new TLRPC.TL_inputMediaPhoto();
                        media.id = new TLRPC.TL_inputPhoto();
                        media.id.id = photo.id;
                        media.id.access_hash = photo.access_hash;
                        media.id.file_reference = photo.file_reference;
                        if (media.id.file_reference == null) {
                            media.id.file_reference = new byte[0];
                        }
                        media.spoiler = hasMediaSpoilers;
                        inputMedia = media;
                    }

                    delayedMessage = self.new DelayedMessage(peer);
                    delayedMessage.type = 0;
                    delayedMessage.obj = messageObject;
                    delayedMessage.originalPath = originalPath;
                    delayedMessage.parentObject = parentObject;
                    delayedMessage.inputUploadMedia = uploadedPhoto;
                    delayedMessage.performMediaUpload = performMediaUpload;
                    if (path != null && path.length() > 0 && path.startsWith("http")) {
                        delayedMessage.httpLocation = path;
                    } else {
                        delayedMessage.photoSize = photo.sizes.get(photo.sizes.size() - 1);
                        delayedMessage.locationParent = photo;
                    }
                } else if (type == 3) {
                    TLRPC.TL_inputMediaUploadedDocument uploadedDocument = new TLRPC.TL_inputMediaUploadedDocument();
                    uploadedDocument.spoiler = hasMediaSpoilers;
                    if (params != null) {
                        String masks = params.get("masks");
                        if (masks != null) {
                            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(masks));
                            int count = serializedData.readInt32(false);
                            for (int a = 0; a < count; a++) {
                                uploadedDocument.stickers.add(TLRPC.InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false));
                            }
                            uploadedDocument.flags |= 1;
                            serializedData.cleanup();
                        }
                    }
                    uploadedDocument.mime_type = document.mime_type;
                    uploadedDocument.attributes = document.attributes;
                    if (!messageObject.isGif() && (videoEditedInfo == null || !videoEditedInfo.muted)) {
                        uploadedDocument.nosound_video = true;
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("nosound_video = true");
                        }
                    }
                    if (document.access_hash == 0) {
                        inputMedia = uploadedDocument;
                        performMediaUpload = true;
                    } else {
                        TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                        media.id = new TLRPC.TL_inputDocument();
                        media.id.id = document.id;
                        media.id.access_hash = document.access_hash;
                        media.id.file_reference = document.file_reference;
                        if (media.id.file_reference == null) {
                            media.id.file_reference = new byte[0];
                        }
                        media.spoiler = hasMediaSpoilers;
                        inputMedia = media;
                    }

                    delayedMessage = self.new DelayedMessage(peer);
                    delayedMessage.type = 1;
                    delayedMessage.obj = messageObject;
                    delayedMessage.originalPath = originalPath;
                    delayedMessage.parentObject = parentObject;
                    delayedMessage.inputUploadMedia = uploadedDocument;
                    if (!document.thumbs.isEmpty()) {
                        TLRPC.PhotoSize photoSize = document.thumbs.get(0);
                        if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                            delayedMessage.photoSize = photoSize;
                            delayedMessage.locationParent = document;
                        }
                    }
                    delayedMessage.videoEditedInfo = videoEditedInfo;

                    if (cover instanceof ImageLoader.PhotoSizeFromPhoto) {
                        ImageLoader.PhotoSizeFromPhoto s = (ImageLoader.PhotoSizeFromPhoto) cover;
                        uploadedDocument.video_cover = s.inputPhoto;
                        uploadedDocument.flags |= 64;
                    } else if (cover != null) {
                        TLRPC.PhotoSize photoSize = cover;
                        if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                            delayedMessage.coverPhotoSize = photoSize;
                            performCoverUpload = true;
                        }
                    }
                    delayedMessage.performMediaUpload = performMediaUpload;
                    delayedMessage.performCoverUpload = performCoverUpload;
                } else if (type == 7) {
                    boolean http = false;
                    TLRPC.InputMedia uploadedDocument = new TLRPC.TL_inputMediaUploadedDocument();
                    uploadedDocument.mime_type = document.mime_type;
                    uploadedDocument.attributes = document.attributes;
                    uploadedDocument.spoiler = hasMediaSpoilers;

                    if (document.access_hash == 0) {
                        inputMedia = uploadedDocument;
                        performMediaUpload = uploadedDocument instanceof TLRPC.TL_inputMediaUploadedDocument;
                    } else {
                        TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                        media.id = new TLRPC.TL_inputDocument();
                        media.id.id = document.id;
                        media.id.access_hash = document.access_hash;
                        media.id.file_reference = document.file_reference;
                        if (media.id.file_reference == null) {
                            media.id.file_reference = new byte[0];
                        }
                        media.spoiler = hasMediaSpoilers;
                        inputMedia = media;
                    }
                    if (!http) {
                        delayedMessage = self.new DelayedMessage(peer);
                        delayedMessage.originalPath = originalPath;
                        delayedMessage.type = 2;
                        delayedMessage.obj = messageObject;
                        if (!document.thumbs.isEmpty() && (videoEditedInfo == null || !videoEditedInfo.isSticker)) {
                            TLRPC.PhotoSize photoSize = document.thumbs.get(0);
                            if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                                delayedMessage.photoSize = photoSize;
                                delayedMessage.locationParent = document;
                            }
                        }
                        delayedMessage.parentObject = parentObject;
                        delayedMessage.inputUploadMedia = uploadedDocument;
                        delayedMessage.performMediaUpload = performMediaUpload;
                    }
                } else if (type == 10) {
                    TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
                    if (media instanceof TLRPC.TL_messageMediaToDo) {
                        final TLRPC.TL_messageMediaToDo m = (TLRPC.TL_messageMediaToDo) media;
                        TLRPC.TL_inputMediaTodo inputTodo = new TLRPC.TL_inputMediaTodo();
                        inputTodo.todo = m.todo;
                        inputMedia = inputTodo;
                    }
                }
                if (inputMedia instanceof TLRPC.TL_inputMediaEmpty && (messageObject.type == MessageObject.TYPE_TEXT || messageObject.type == MessageObject.TYPE_EMOJIS)) {
                    inputMedia = null;
                }

                TLObject reqSend;

                if (pollToAddOptionMessageObject != null) {
                    final TLRPC.TL_messages_addPollAnswer req = new TLRPC.TL_messages_addPollAnswer();
                    pollToAddOptionMessageObject.input_media = inputMedia;
                    req.answer = pollToAddOptionMessageObject;
                    req.msg_id = messageObject.getId();
                    req.peer = self.getMessagesController().getInputPeer(peer);
                    if (delayedMessage != null) {
                        delayedMessage.sendRequest = req;
                    }
                    reqSend = req;
                } else {
                    final TLRPC.TL_messages_editMessage request;
                    if (messageObject.isEphemeral()) {
                        final TL_ephemeral.TL_editMessage request2 = new TL_ephemeral.TL_editMessage();
                        request2.id = MessageObject.ephemeralMessageIdUnpack(messageObject.getId());
                        request2.receiver_id = new TLRPC.TL_inputUserEmpty();
                        request2.welcome = true;
                        request = request2;
                    } else {
                        request = new TLRPC.TL_messages_editMessage();
                        request.id = messageObject.getId();
                    }
                    request.peer = self.getMessagesController().getInputPeer(peer);
                    request.invert_media = messageObject.messageOwner.invert_media;
                    if (inputMedia != null) {
                        request.flags |= 16384;
                        request.media = inputMedia;
                    } else if (!messageObject.editingMessageSearchWebPage) {
                        request.no_webpage = true;
                    }
                    if (messageObject.scheduled) {
                        request.schedule_date = messageObject.messageOwner.date;
                        request.flags |= 32768;
                        if (messageObject.messageOwner.schedule_repeat_period != 0) {
                            request.schedule_repeat_period = messageObject.messageOwner.schedule_repeat_period;
                            request.flags |= TLObject.FLAG_18;
                        }
                    }
                    if ((messageObject.messageOwner.flags & 1073741824) != 0) {
                        request.quick_reply_shortcut_id = messageObject.messageOwner.quick_reply_shortcut_id;
                        request.flags |= 131072;
                    }
                    if (messageObject.editingMessage != null) {
                        request.message = messageObject.editingMessage.toString();
                        request.flags |= 2048;
                        request.no_webpage = !messageObject.editingMessageSearchWebPage;
                        if (messageObject.editingMessageEntities != null) {
                            request.entities = messageObject.editingMessageEntities;
                            request.flags |= 8;
                        } else {
                            CharSequence[] message = new CharSequence[]{messageObject.editingMessage};
                            ArrayList<TLRPC.MessageEntity> entities = self.getMediaDataController().getEntities(message, supportsSendingNewEntities);
                            if (entities != null && !entities.isEmpty()) {
                                request.entities = entities;
                                request.flags |= 8;
                            }
                        }
                        messageObject.editingMessage = null;
                        messageObject.editingMessageEntities = null;
                    }

                    if (delayedMessage != null) {
                        delayedMessage.sendRequest = request;
                    }
                    reqSend = request;
                }

                if (type == 1 || type == 11) {
                    self.performSendMessageRequest(reqSend, messageObject, null, delayedMessage, parentObject, params, messageObject.scheduled);
                } else if (type == 2) {
                    if (performMediaUpload || performCoverUpload) {
                        self.performSendDelayedMessage(delayedMessage);
                    } else {
                        self.performSendMessageRequest(reqSend, messageObject, originalPath, null, true, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 3) {
                    if (performMediaUpload || performCoverUpload) {
                        self.performSendDelayedMessage(delayedMessage);
                    } else {
                        self.performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 6) {
                    self.performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                } else if (type == 7) {
                    if (performMediaUpload || performCoverUpload) {
                        self.performSendDelayedMessage(delayedMessage);
                    } else {
                        self.performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 8) {
                    if (performMediaUpload || performCoverUpload) {
                        self.performSendDelayedMessage(delayedMessage);
                    } else {
                        self.performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 10) {
                    self.performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            self.revertEditingMessageObject(messageObject);
        }
    }

    // MurkaGram: dual UI, core-13 SendMessagesHelper.editMessage
    public static int editMessage(SendMessagesHelper self, MessageObject messageObject, String message, boolean searchLinks, final BaseFragment fragment, ArrayList<TLRPC.MessageEntity> entities, int scheduleDate, int scheduleRepeatPeriod) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return 0;
        }

        final TLRPC.TL_messages_editMessage req;
        if (messageObject.isEphemeral()) {
            final TL_ephemeral.TL_editMessage request2 = new TL_ephemeral.TL_editMessage();
            request2.id = MessageObject.ephemeralMessageIdUnpack(messageObject.getId());
            request2.receiver_id = new TLRPC.TL_inputUserEmpty();
            request2.welcome = true;
            req = request2;
        } else {
            req = new TLRPC.TL_messages_editMessage();
            req.id = messageObject.getId();
        }

        req.peer = self.getMessagesController().getInputPeer(messageObject.getDialogId());
        if (message != null) {
            req.message = message;
            req.flags |= 2048;
            req.no_webpage = !searchLinks;
        }
        if (messageObject.messageOwner != null && (messageObject.messageOwner.flags & 1073741824) != 0) {
            req.quick_reply_shortcut_id = messageObject.messageOwner.quick_reply_shortcut_id;
            req.flags |= 131072;
        }
        if (entities != null) {
            req.entities = entities;
            req.flags |= 8;
        }
        if (scheduleDate != 0) {
            req.schedule_date = scheduleDate;
            req.flags |= 32768;
            if (scheduleRepeatPeriod != 0) {
                req.schedule_repeat_period = scheduleRepeatPeriod;
                req.flags |= TLObject.FLAG_18;
            }
        }
        return self.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                self.getMessagesController().processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> self.getMessagesController().murkaScheduleGhostOffline()); // MurkaGram: режим призрака — офлайн после правки сообщения
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(self.currentAccount, error, fragment, req));
            }
        });
    }

    // MurkaGram: dual UI, core-13 SendMessagesHelper.prepareSendingBotContextResult
    public static void prepareSendingBotContextResult(BaseFragment fragment, AccountInstance accountInstance, TLRPC.BotInlineResult result, HashMap<String, String> params, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, org.telegram.ui.ChatActivity.ReplyQuote quote, boolean notify, int scheduleDate, int scheduleRepeatPeriod, SendMessageChatArguments sendMessageChatArguments, long stars) {
        prepareSendingBotContextResult(fragment, accountInstance, result, params, dialogId, replyToMsg, replyToTopMsg, storyItem, quote, notify, scheduleDate, scheduleRepeatPeriod, sendMessageChatArguments, stars, 0);
    }

    // MurkaGram: dual UI, core-13 SendMessagesHelper.prepareSendingBotContextResult
    public static void prepareSendingBotContextResult(BaseFragment fragment, AccountInstance accountInstance, TLRPC.BotInlineResult result, HashMap<String, String> params, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, org.telegram.ui.ChatActivity.ReplyQuote quote, boolean notify, int scheduleDate, int scheduleRepeatPeriod, SendMessageChatArguments sendMessageChatArguments, long stars, long monoForumPeerId) {
        if (result == null) {
            return;
        }
        if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaAuto) {
            new Thread(() -> {
                boolean isEncrypted = DialogObject.isEncryptedDialog(dialogId);
                String finalPath = null;
                TLRPC.TL_document document = null;
                TLRPC.TL_photo photo = null;
                TLRPC.TL_game game = null;
                if ("game".equals(result.type)) {
                    if (isEncrypted) {
                        return; //doesn't work in secret chats for now
                    }
                    game = new TLRPC.TL_game();
                    game.title = result.title;
                    game.description = result.description;
                    game.short_name = result.id;
                    game.photo = result.photo;
                    if (game.photo == null) {
                        game.photo = new TLRPC.TL_photoEmpty();
                    }
                    if (result.document instanceof TLRPC.TL_document) {
                        game.document = result.document;
                        game.flags |= 1;
                    }
                } else if (result instanceof TLRPC.TL_botInlineMediaResult) {
                    if (result.document != null) {
                        if (result.document instanceof TLRPC.TL_document) {
                            document = (TLRPC.TL_document) result.document;
                        }
                    } else if (result.photo != null) {
                        if (result.photo instanceof TLRPC.TL_photo) {
                            photo = (TLRPC.TL_photo) result.photo;
                        }
                    }
                } else if (result.content != null) {
                    String ext = ImageLoader.getHttpUrlExtension(result.content.url, null);
                    if (TextUtils.isEmpty(ext)) {
                        ext = FileLoader.getExtensionByMimeType(result.content.mime_type);
                    } else {
                        ext = "." + ext;
                    }
                    File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.content.url) + ext);
                    if (f.exists()) {
                        finalPath = f.getAbsolutePath();
                    } else {
                        finalPath = result.content.url;
                    }
                    switch (result.type) {
                        case "audio":
                        case "voice":
                        case "file":
                        case "video":
                        case "sticker":
                        case "gif": {
                            document = new TLRPC.TL_document();
                            document.id = 0;
                            document.size = 0;
                            document.dc_id = 0;
                            document.mime_type = result.content.mime_type;
                            document.file_reference = new byte[0];
                            document.date = accountInstance.getConnectionsManager().getCurrentTime();
                            TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                            document.attributes.add(fileName);

                            switch (result.type) {
                                case "gif": {
                                    fileName.file_name = "animation.gif";
                                    if (finalPath.endsWith("mp4")) {
                                        document.mime_type = "video/mp4";
                                        document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                                    } else {
                                        document.mime_type = "image/gif";
                                    }
                                    try {
                                        int side = isEncrypted ? 90 : 320;
                                        Bitmap bitmap;
                                        if (finalPath.endsWith("mp4")) {
                                            bitmap = SendMessagesHelper.createVideoThumbnail(finalPath, MediaStore.Video.Thumbnails.MINI_KIND);
                                            if (bitmap == null && result.thumb instanceof TLRPC.TL_webDocument && "video/mp4".equals(result.thumb.mime_type)) {
                                                ext = ImageLoader.getHttpUrlExtension(result.thumb.url, null);
                                                if (TextUtils.isEmpty(ext)) {
                                                    ext = FileLoader.getExtensionByMimeType(result.thumb.mime_type);
                                                } else {
                                                    ext = "." + ext;
                                                }
                                                f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + ext);
                                                bitmap = SendMessagesHelper.createVideoThumbnail(f.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
                                            }
                                        } else {
                                            bitmap = ImageLoader.loadBitmap(finalPath, null, side, side, true);
                                        }
                                        if (bitmap != null) {
                                            TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, side, side, side > 90 ? 80 : 55, false);
                                            if (thumb != null) {
                                                document.thumbs.add(thumb);
                                                document.flags |= 1;
                                            }
                                            bitmap.recycle();
                                        }
                                    } catch (Throwable e) {
                                        FileLog.e(e);
                                    }
                                    break;
                                }
                                case "voice": {
                                    TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                                    audio.duration = MessageObject.getInlineResultDuration(result);
                                    audio.voice = true;
                                    fileName.file_name = "audio.ogg";
                                    document.attributes.add(audio);

                                    break;
                                }
                                case "audio": {
                                    TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                                    audio.duration = MessageObject.getInlineResultDuration(result);
                                    audio.title = result.title;
                                    audio.flags |= 1;
                                    if (result.description != null) {
                                        audio.performer = result.description;
                                        audio.flags |= 2;
                                    }
                                    fileName.file_name = "audio.mp3";
                                    document.attributes.add(audio);

                                    break;
                                }
                                case "file": {
                                    int idx = result.content.mime_type.lastIndexOf('/');
                                    if (idx != -1) {
                                        fileName.file_name = "file." + result.content.mime_type.substring(idx + 1);
                                    } else {
                                        fileName.file_name = "file";
                                    }
                                    break;
                                }
                                case "video": {
                                    fileName.file_name = "video.mp4";
                                    TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
                                    int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                                    attributeVideo.w = wh[0];
                                    attributeVideo.h = wh[1];
                                    attributeVideo.duration = MessageObject.getInlineResultDuration(result);
                                    attributeVideo.supports_streaming = true;
                                    document.attributes.add(attributeVideo);
                                    try {
                                        if (result.thumb != null) {
                                            String thumbPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb.url, "jpg")).getAbsolutePath();
                                            Bitmap bitmap = ImageLoader.loadBitmap(thumbPath, null, 90, 90, true);
                                            if (bitmap != null) {
                                                TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                                if (thumb != null) {
                                                    document.thumbs.add(thumb);
                                                    document.flags |= 1;
                                                }
                                                bitmap.recycle();
                                            }
                                        }
                                    } catch (Throwable e) {
                                        FileLog.e(e);
                                    }
                                    break;
                                }
                                case "sticker": {
                                    TLRPC.TL_documentAttributeSticker attributeSticker = new TLRPC.TL_documentAttributeSticker();
                                    attributeSticker.alt = "";
                                    attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                    document.attributes.add(attributeSticker);
                                    TLRPC.TL_documentAttributeImageSize attributeImageSize = new TLRPC.TL_documentAttributeImageSize();
                                    int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                                    attributeImageSize.w = wh[0];
                                    attributeImageSize.h = wh[1];
                                    document.attributes.add(attributeImageSize);
                                    fileName.file_name = "sticker.webp";
                                    try {
                                        if (result.thumb != null) {
                                            String thumbPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb.url, "webp")).getAbsolutePath();
                                            Bitmap bitmap = ImageLoader.loadBitmap(thumbPath, null, 90, 90, true);
                                            if (bitmap != null) {
                                                TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                                if (thumb != null) {
                                                    document.thumbs.add(thumb);
                                                    document.flags |= 1;
                                                }
                                                bitmap.recycle();
                                            }
                                        }
                                    } catch (Throwable e) {
                                        FileLog.e(e);
                                    }
                                    break;
                                }
                            }
                            if (fileName.file_name == null) {
                                fileName.file_name = "file";
                            }
                            if (document.mime_type == null) {
                                document.mime_type = "application/octet-stream";
                            }
                            if (document.thumbs.isEmpty()) {
                                TLRPC.PhotoSize thumb = new TLRPC.TL_photoSize();
                                int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                                thumb.w = wh[0];
                                thumb.h = wh[1];
                                thumb.size = 0;
                                thumb.location = new TLRPC.TL_fileLocationUnavailable();
                                thumb.type = "x";

                                document.thumbs.add(thumb);
                                document.flags |= 1;
                            }
                            break;
                        }
                        case "photo": {
                            if (f.exists()) {
                                photo = accountInstance.getSendMessagesHelper().generatePhotoSizes(finalPath, null);
                            }
                            if (photo == null) {
                                photo = new TLRPC.TL_photo();
                                photo.date = accountInstance.getConnectionsManager().getCurrentTime();
                                photo.file_reference = new byte[0];
                                TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                                int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                                photoSize.w = wh[0];
                                photoSize.h = wh[1];
                                photoSize.size = 1;
                                photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                                photoSize.type = "x";
                                photo.sizes.add(photoSize);
                            }
                            break;
                        }
                    }
                }
                final String finalPathFinal = finalPath;
                final TLRPC.TL_document finalDocument = document;
                final TLRPC.TL_photo finalPhoto = photo;
                final TLRPC.TL_game finalGame = game;
                if (params != null && result.content != null) {
                    params.put("originalPath", result.content.url);
                }
                final Bitmap[] precahcedThumb = new Bitmap[1];
                final String[] precachedKey = new String[1];

                if (isEncrypted && document != null) {
                    for (int i = 0; i < document.attributes.size(); ++i) {
                        if (document.attributes.get(i) instanceof TLRPC.TL_documentAttributeVideo) {
                            TLRPC.TL_documentAttributeVideo attr = (TLRPC.TL_documentAttributeVideo) document.attributes.get(i);
                            TLRPC.TL_documentAttributeVideo_layer159 attr2 = new TLRPC.TL_documentAttributeVideo_layer159();
                            attr2.flags = attr.flags;
                            attr2.round_message = attr.round_message;
                            attr2.supports_streaming = attr.supports_streaming;
                            attr2.duration = attr.duration;
                            attr2.w = attr.w;
                            attr2.h = attr.h;
                            document.attributes.set(i, attr2);
                        }
                    }
                }

                if (MessageObject.isGifDocument(document)) {
                    TLRPC.PhotoSize photoSizeThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                    File gifFile = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(document);
                    if (!gifFile.exists()) {
                        gifFile = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(document, true);
                    }
                    SendMessagesHelper.ensureMediaThumbExists(accountInstance, isEncrypted, document, gifFile.getAbsolutePath(), null, 0);
                    precachedKey[0] = SendMessagesHelper.getKeyForPhotoSize(accountInstance, photoSizeThumb, precahcedThumb, true, true);
                }
                TLRPC.InputPeer sendToPeer = !DialogObject.isEncryptedDialog(dialogId) ? accountInstance.getMessagesController().getInputPeer(dialogId) : null;
                if (sendToPeer != null && sendToPeer.user_id != 0 && accountInstance.getMessagesController().getUserFull(sendToPeer.user_id) != null &&
                        accountInstance.getMessagesController().getUserFull(sendToPeer.user_id).voice_messages_forbidden && document != null) {

                    if (MessageObject.isVoiceDocument(finalDocument)) {
                        AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSendMediaAlert(7, fragment, null));
                        return;
                    } else if (MessageObject.isRoundVideoDocument(finalDocument)) {
                        AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSendMediaAlert(8, fragment, null));
                        return;
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    SendMessagesHelper.SendMessageParams params2 = null;
                    if (finalDocument != null) {
                        if (precahcedThumb[0] != null && precachedKey[0] != null) {
                            ImageLoader.getInstance().putImageToCache(new BitmapDrawable(precahcedThumb[0]), precachedKey[0], false);
                        }
                        params2 = SendMessagesHelper.SendMessageParams.of(finalDocument, null, finalPathFinal, dialogId, replyToMsg, replyToTopMsg, result.send_message.message, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod, 0, result, null, false);
                    } else if (finalPhoto != null) {
                        params2 = SendMessagesHelper.SendMessageParams.of(finalPhoto, result.content != null ? result.content.url : null, dialogId, replyToMsg, replyToTopMsg, result.send_message.message, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod, 0, result, false);
                    } else if (finalGame != null) {
                        params2 = SendMessagesHelper.SendMessageParams.of(finalGame, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod);
                    }
                    if (params2 != null) {
                        params2.sendMessageChatArguments = sendMessageChatArguments;
                        params2.replyToStoryItem = storyItem;
                        params2.replyQuote = quote;
                        params2.payStars = stars;
                        params2.monoForumPeer = monoForumPeerId;
                        accountInstance.getSendMessagesHelper().sendMessage(params2);
                    }
                });
            }).run();
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageText) {
            TLRPC.WebPage webPage = null;
            if (DialogObject.isEncryptedDialog(dialogId)) {
                for (int a = 0; a < result.send_message.entities.size(); a++) {
                    TLRPC.MessageEntity entity = result.send_message.entities.get(a);
                    if (entity instanceof TLRPC.TL_messageEntityUrl) {
                        webPage = new TLRPC.TL_webPagePending();
                        webPage.url = result.send_message.message.substring(entity.offset, entity.offset + entity.length);
                        break;
                    }
                }
            }
            final SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(result.send_message.message, dialogId, replyToMsg, replyToTopMsg, webPage, !result.send_message.no_webpage, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod, null, false);
            params2.sendMessageChatArguments = sendMessageChatArguments;
            params2.replyQuote = quote;
            params2.payStars = stars;
            params2.monoForumPeer = monoForumPeerId;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageRichMessage) {
            final SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.ofRichMessage(result.send_message.rich_message, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod);
            params2.sendMessageChatArguments = sendMessageChatArguments;
            params2.replyQuote = quote;
            params2.payStars = stars;
            params2.monoForumPeer = monoForumPeerId;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageWidget) {
            // A widget result (widget-sandbox docs/sdk-v1.md §5.11): the local copy shows the widget with the result's
            // state and no flags until the server's message, with its own flags, replaces it.
            TLRPC.TL_botInlineMessageWidget inline = (TLRPC.TL_botInlineMessageWidget) result.send_message;
            TLRPC.TL_messageMediaWidget widget = new TLRPC.TL_messageMediaWidget();
            widget.width = inline.width;
            widget.height = inline.height;
            widget.payload = inline.payload;
            widget.public_data = inline.public_data;
            widget.public_data_hash = inline.public_data_hash;
            widget.chat_data = inline.chat_data;
            widget.widget_id = inline.widget_id;
            final SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(widget, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod);
            // The result's caption goes with the widget.
            params2.caption = inline.message;
            params2.entities = inline.entities;
            params2.invert_media = inline.invert_media;
            params2.sendMessageChatArguments = sendMessageChatArguments;
            params2.replyQuote = quote;
            params2.payStars = stars;
            params2.monoForumPeer = monoForumPeerId;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaVenue) {
            TLRPC.TL_messageMediaVenue venue = new TLRPC.TL_messageMediaVenue();
            venue.geo = result.send_message.geo;
            venue.address = result.send_message.address;
            venue.title = result.send_message.title;
            venue.provider = result.send_message.provider;
            venue.venue_id = result.send_message.venue_id;
            venue.venue_type = venue.venue_id = result.send_message.venue_type;
            if (venue.venue_type == null) {
                venue.venue_type = "";
            }
            final SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(venue, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod);
            params2.sendMessageChatArguments = sendMessageChatArguments;
            params2.replyQuote = quote;
            params2.payStars = stars;
            params2.monoForumPeer = monoForumPeerId;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaGeo) {
            final SendMessagesHelper.SendMessageParams params2;
            if (result.send_message.period != 0 || result.send_message.proximity_notification_radius != 0) {
                TLRPC.TL_messageMediaGeoLive location = new TLRPC.TL_messageMediaGeoLive();
                location.period = result.send_message.period != 0 ? result.send_message.period : 900;
                location.geo = result.send_message.geo;
                location.heading = result.send_message.heading;
                location.proximity_notification_radius = result.send_message.proximity_notification_radius;
                params2 = SendMessagesHelper.SendMessageParams.of(location, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod);
            } else {
                TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                location.geo = result.send_message.geo;
                location.heading = result.send_message.heading;
                params2 = SendMessagesHelper.SendMessageParams.of(location, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod);
            }
            params2.sendMessageChatArguments = sendMessageChatArguments;
            params2.replyQuote = quote;
            params2.payStars = stars;
            params2.monoForumPeer = monoForumPeerId;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaContact) {
            TLRPC.User user = new TLRPC.TL_user();
            user.phone = result.send_message.phone_number;
            user.first_name = result.send_message.first_name;
            user.last_name = result.send_message.last_name;
            TLRPC.RestrictionReason reason = new TLRPC.RestrictionReason();
            reason.text = result.send_message.vcard;
            reason.platform = "";
            reason.reason = "";
            user.restriction_reason.add(reason);
            final SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(user, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod);
            params2.sendMessageChatArguments = sendMessageChatArguments;
            params2.replyQuote = quote;
            params2.payStars = stars;
            params2.monoForumPeer = monoForumPeerId;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaInvoice) {
            if (DialogObject.isEncryptedDialog(dialogId)) {
                return; //doesn't work in secret chats for now
            }
            TLRPC.TL_botInlineMessageMediaInvoice invoice = (TLRPC.TL_botInlineMessageMediaInvoice) result.send_message;
            TLRPC.TL_messageMediaInvoice messageMediaInvoice = new TLRPC.TL_messageMediaInvoice();
            messageMediaInvoice.shipping_address_requested = invoice.shipping_address_requested;
            messageMediaInvoice.test = invoice.test;
            messageMediaInvoice.title = invoice.title;
            messageMediaInvoice.description = invoice.description;
            if (invoice.photo != null) {
                messageMediaInvoice.webPhoto = invoice.photo;
                messageMediaInvoice.flags |= 1;
            }
            messageMediaInvoice.currency = invoice.currency;
            messageMediaInvoice.total_amount = invoice.total_amount;
            messageMediaInvoice.start_param = "";
            final SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(messageMediaInvoice, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod);
            params2.sendMessageChatArguments = sendMessageChatArguments;
            params2.replyQuote = quote;
            params2.payStars = stars;
            params2.monoForumPeer = monoForumPeerId;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaWebPage) {
            TLRPC.TL_botInlineMessageMediaWebPage request = (TLRPC.TL_botInlineMessageMediaWebPage) result.send_message;
            TLRPC.WebPage webPage = new TLRPC.TL_webPagePending();
            webPage.url = request.url;
            final SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(result.send_message.message, dialogId, replyToMsg, replyToTopMsg, webPage, !result.send_message.no_webpage, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, scheduleRepeatPeriod, null, false);
            params2.sendMessageChatArguments = sendMessageChatArguments;
            params2.replyQuote = quote;
            params2.payStars = stars;
            params2.monoForumPeer = monoForumPeerId;
            params2.invert_media = result.send_message.invert_media;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        }
    }

    // MurkaGram: dual UI, core-13 SendMessagesHelper.requestUrlAuth
    public static void requestUrlAuth(SendMessagesHelper self, String url, ChatActivity parentFragment, boolean ask) {
        final TLRPC.TL_messages_requestUrlAuth req = new TLRPC.TL_messages_requestUrlAuth();
        req.url = url;
        req.flags |= 4;
        self.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                // MurkaGram: 12.2.10 url auth alerts instead of 13.2.2 OAuthSheet (core-13)
                if (response instanceof TLRPC.TL_urlAuthResultRequest) {
                    parentFragment.showRequestUrlAlert((TLRPC.TL_urlAuthResultRequest) response, req, url, ask);
                } else if (response instanceof TLRPC.TL_urlAuthResultAccepted) {
                    AlertsCreator.showOpenUrlAlert(parentFragment, ((TLRPC.TL_urlAuthResultAccepted) response).url, false, false);
                } else if (response instanceof TLRPC.TL_urlAuthResultDefault) {
                    AlertsCreator.showOpenUrlAlert(parentFragment, url, false, ask);
                }
            } else {
                AlertsCreator.showOpenUrlAlert(parentFragment, url, false, ask);
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    // MurkaGram: dual UI, core-13 SendMessagesHelper.sendCallback
    public static void sendCallback(SendMessagesHelper self, final boolean cache, final MessageObject messageObject, final TL_keyboard.KeyboardButtonProto button, final ChatActivity parentFragment) {
        sendCallback(self, cache, messageObject, button, null, null, parentFragment);
    }

    // MurkaGram: dual UI, core-13 SendMessagesHelper.sendCallback
    public static void sendCallback(SendMessagesHelper self, final boolean cache, final MessageObject messageObject, final TL_keyboard.KeyboardButtonProto button, TLRPC.InputCheckPasswordSRP srp, TwoStepVerificationActivity passwordFragment, final ChatActivity parentFragment) {
        if (messageObject == null || button == null || parentFragment == null) {
            return;
        }
        final boolean cacheFinal;
        int type;
        if (TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeUrlAuth.class)) {
            cacheFinal = false;
            type = 3;
        } else if (TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeGame.class)) {
            cacheFinal = false;
            type = 1;
        } else {
            cacheFinal = cache;
            if (TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeBuy.class)) {
                type = 2;
            } else {
                type = 0;
            }
        }
        final String key = messageObject.getDialogId() + "_" + messageObject.getId() + "_" + Utilities.bytesToHex(button.getData()) + "_" + type;
        self.waitingForCallback.put(key, true);

        List<String> keys = self.waitingForCallbackMap.get(messageObject.getDialogId() + "_" + messageObject.getId());
        if (keys == null) {
            self.waitingForCallbackMap.put(messageObject.getDialogId() + "_" + messageObject.getId(), keys = new ArrayList<>());
        }
        keys.add(key);

        TLObject[] request = new TLObject[1];
        List<String> finalKeys = keys;
        RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            self.waitingForCallback.remove(key);
            finalKeys.remove(key);
            if (cacheFinal && response == null) {
                sendCallback(self, false, messageObject, button, parentFragment);
            } else if (response != null) {
                if (passwordFragment != null) {
                    passwordFragment.needHideProgress();
                    passwordFragment.finishFragment();
                }

                long uid = messageObject.getFromChatId();
                if (messageObject.messageOwner.via_bot_id != 0) {
                    uid = messageObject.messageOwner.via_bot_id;
                }
                String name = null;
                if (uid > 0) {
                    TLRPC.User user = self.getMessagesController().getUser(uid);
                    if (user != null) {
                        name = ContactsController.formatName(user.first_name, user.last_name);
                    }
                } else {
                    TLRPC.Chat chat = self.getMessagesController().getChat(-uid);
                    if (chat != null) {
                        name = chat.title;
                    }
                }
                if (name == null) {
                    name = "bot";
                }

                final TL_keyboard.TL_inlineButtonTypeUrlAuth buttonTypeUrlAuth =
                        TLKeyboardHelper.getType(button, TL_keyboard.TL_inlineButtonTypeUrlAuth.class);
                if (buttonTypeUrlAuth != null) {
                    // MurkaGram: 12.2.10 url auth alerts instead of 13.2.2 OAuthSheet (core-13)
                    if (response instanceof TLRPC.TL_urlAuthResultRequest) {
                        parentFragment.showRequestUrlAlert((TLRPC.TL_urlAuthResultRequest) response, (TLRPC.TL_messages_requestUrlAuth) request[0], buttonTypeUrlAuth.url, false);
                    } else if (response instanceof TLRPC.TL_urlAuthResultAccepted) {
                        AlertsCreator.showOpenUrlAlert(parentFragment, ((TLRPC.TL_urlAuthResultAccepted) response).url, false, false);
                    } else if (response instanceof TLRPC.TL_urlAuthResultDefault) {
                        AlertsCreator.showOpenUrlAlert(parentFragment, buttonTypeUrlAuth.url, false, true);
                    }
                } else if (TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeBuy.class)) {
                    if (response instanceof TLRPC.TL_payments_paymentFormStars) {
                        TLRPC.InputInvoice inputInvoice = ((TLRPC.TL_payments_getPaymentForm) request[0]).invoice;
                        StarsController.getInstance(self.currentAccount).openPaymentForm(messageObject, inputInvoice, (TLRPC.TL_payments_paymentFormStars) response, () -> {
                            self.waitingForCallback.remove(key);
                            finalKeys.remove(key);
                        }, status -> {});
                    } else if (response instanceof TLRPC.PaymentForm) {
                        final TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                        self.getMessagesController().putUsers(form.users, false);
                        parentFragment.presentFragment(new PaymentFormActivity(form, messageObject, parentFragment));
                    } else if (response instanceof TLRPC.TL_payments_paymentReceiptStars) {
                        StarsIntroActivity.showTransactionSheet(LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext, false, self.currentAccount, (TLRPC.TL_payments_paymentReceiptStars) response, null);
                    } else if (response instanceof TLRPC.PaymentReceipt) {
                        parentFragment.presentFragment(new PaymentFormActivity((TLRPC.PaymentReceipt) response));
                    }
                } else {
                    TLRPC.TL_messages_botCallbackAnswer res = (TLRPC.TL_messages_botCallbackAnswer) response;
                    final TL_keyboard.TL_inlineButtonTypeCallback buttonTypeCallback = TLKeyboardHelper.getType(button, TL_keyboard.TL_inlineButtonTypeCallback.class);
                    if (!cacheFinal && res.cache_time != 0 && !(buttonTypeCallback != null && buttonTypeCallback.requires_password)) {
                        self.getMessagesStorage().saveBotCache(key, res);
                    }

                    if (res.message != null) {
                        if (res.alert) {
                            if (parentFragment.getParentActivity() == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
                            builder.setTitle(name);
                            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                            builder.setMessage(res.message);
                            parentFragment.showDialog(builder.create());
                        } else {
                            parentFragment.showAlert(name, res.message);
                        }
                    } else if (res.url != null) {
                        if (parentFragment.getParentActivity() == null) {
                            return;
                        }
                        TLRPC.User user = self.getMessagesController().getUser(uid);
                        boolean verified = user != null && user.verified;
                        if (TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeGame.class)) {
                            TLRPC.TL_game game = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame ? messageObject.messageOwner.media.game : null;
                            if (game == null) {
                                return;
                            }
                            parentFragment.showOpenGameAlert(game, messageObject, res.url, !verified && MessagesController.getNotificationsSettings(self.currentAccount).getBoolean("askgame_" + uid, true), uid);
                        } else {
                            AlertsCreator.showOpenUrlAlert(parentFragment, res.url, false, false);
                        }
                    }
                }
            } else if (error != null) {
                if (parentFragment.getParentActivity() == null) {
                    return;
                }
                if ("PASSWORD_HASH_INVALID".equals(error.text)) {
                    if (srp == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
                        builder.setTitle(LocaleController.getString(R.string.BotOwnershipTransfer));
                        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BotOwnershipTransferReadyAlertText", R.string.BotOwnershipTransferReadyAlertText)));
                        builder.setPositiveButton(LocaleController.getString(R.string.BotOwnershipTransferChangeOwner), (dialogInterface, i) -> {
                            TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                            fragment.setDelegate(0, password -> sendCallback(self, cache, messageObject, button, password, fragment, parentFragment));
                            parentFragment.presentFragment(fragment);
                        });
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                        parentFragment.showDialog(builder.create());
                    }
                } else if ("PASSWORD_MISSING".equals(error.text) || error.text.startsWith("PASSWORD_TOO_FRESH_") || error.text.startsWith("SESSION_TOO_FRESH_")) {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
                    builder.setTitle(LocaleController.getString(R.string.EditAdminTransferAlertTitle));

                    LinearLayout linearLayout = new LinearLayout(parentFragment.getParentActivity());
                    linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(2), AndroidUtilities.dp(24), 0);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    TextView messageTextView = new TextView(parentFragment.getParentActivity());
                    ThemeBind.textColor(messageTextView, Theme.key_dialogTextBlack);
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BotOwnershipTransferAlertText", R.string.BotOwnershipTransferAlertText)));
                    linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    LinearLayout linearLayout2 = new LinearLayout(parentFragment.getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    ImageView dotImageView = new ImageView(parentFragment.getParentActivity());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    ThemeBind.colorFilter(dotImageView, Theme.key_dialogTextBlack, PorterDuff.Mode.MULTIPLY);

                    messageTextView = new TextView(parentFragment.getParentActivity());
                    ThemeBind.textColor(messageTextView, Theme.key_dialogTextBlack);
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText1)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    linearLayout2 = new LinearLayout(parentFragment.getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    dotImageView = new ImageView(parentFragment.getParentActivity());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    ThemeBind.colorFilter(dotImageView, Theme.key_dialogTextBlack, PorterDuff.Mode.MULTIPLY);

                    messageTextView = new TextView(parentFragment.getParentActivity());
                    ThemeBind.textColor(messageTextView, Theme.key_dialogTextBlack);
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText2)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    if ("PASSWORD_MISSING".equals(error.text)) {
                        builder.setPositiveButton(LocaleController.getString(R.string.EditAdminTransferSetPassword), (dialogInterface, i) -> parentFragment.presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null)));
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    } else {
                        messageTextView = new TextView(parentFragment.getParentActivity());
                        ThemeBind.textColor(messageTextView, Theme.key_dialogTextBlack);
                        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                        messageTextView.setText(LocaleController.getString(R.string.EditAdminTransferAlertText3));
                        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                        builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
                    }
                    parentFragment.showDialog(builder.create());
                } else if ("SRP_ID_INVALID".equals(error.text)) {
                    TL_account.getPassword getPasswordReq = new TL_account.getPassword();
                    ConnectionsManager.getInstance(self.currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TL_account.Password currentPassword = (TL_account.Password) response2;
                            passwordFragment.setCurrentPasswordInfo(null, currentPassword);
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            sendCallback(self, cache, messageObject, button, passwordFragment.getNewSrpPassword(), passwordFragment, parentFragment);
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                } else {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                        passwordFragment.finishFragment();
                    }
                }
            }
        });
        if (cacheFinal) {
            self.getMessagesStorage().getBotCache(key, requestDelegate);
        } else {
            final TL_keyboard.TL_inlineButtonTypeUrlAuth buttonTypeUrlAuth =
                TLKeyboardHelper.getType(button, TL_keyboard.TL_inlineButtonTypeUrlAuth.class);
            if (buttonTypeUrlAuth != null) {
                TLRPC.TL_messages_requestUrlAuth req = new TLRPC.TL_messages_requestUrlAuth();
                req.peer = self.getMessagesController().getInputPeer(messageObject.getDialogId());
                req.msg_id = messageObject.getId();
                req.button_id = buttonTypeUrlAuth.button_id;
                req.flags |= 2;
                request[0] = req;
                self.getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
            } else if (TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeBuy.class)) {
                if ((messageObject.messageOwner.media.flags & 4) == 0) {
                    TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
                    TLRPC.TL_inputInvoiceMessage inputInvoice = new TLRPC.TL_inputInvoiceMessage();
                    inputInvoice.msg_id = messageObject.getId();
                    inputInvoice.peer = self.getMessagesController().getInputPeer(messageObject.messageOwner.peer_id);
                    req.invoice = inputInvoice;
                    final JSONObject themeParams = BotWebViewSheet.makeThemeParams(null);
                    if (themeParams != null) {
                        req.theme_params = new TLRPC.TL_dataJSON();
                        req.theme_params.data = themeParams.toString();
                        req.flags |= 1;
                    }
                    request[0] = req;
                    self.getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
                } else {
                    TLRPC.TL_payments_getPaymentReceipt req = new TLRPC.TL_payments_getPaymentReceipt();
                    req.msg_id = messageObject.messageOwner.media.receipt_msg_id;
                    req.peer = self.getMessagesController().getInputPeer(messageObject.messageOwner.peer_id);
                    request[0] = req;
                    self.getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
                }
            } else {
                if (messageObject.isEphemeral()) {
                    TL_ephemeral.TL_getCallbackAnswer req = new TL_ephemeral.TL_getCallbackAnswer();
                    req.peer = self.getMessagesController().getInputPeer(messageObject.getDialogId());
                    req.id = messageObject.getEphemeralId();
                    req.data = button.getData();
                    self.getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
                } else {
                    TLRPC.TL_messages_getBotCallbackAnswer req = new TLRPC.TL_messages_getBotCallbackAnswer();
                    req.peer = self.getMessagesController().getInputPeer(messageObject.getDialogId());
                    req.msg_id = messageObject.getId();
                    req.game = TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeGame.class);
                    final TL_keyboard.TL_inlineButtonTypeCallback buttonTypeCallback = TLKeyboardHelper.getType(button, TL_keyboard.TL_inlineButtonTypeCallback.class);
                    if (buttonTypeCallback != null && buttonTypeCallback.requires_password) {
                        req.password = req.password = srp != null ? srp : new TLRPC.TL_inputCheckPasswordEmpty();;
                        req.flags |= 4;
                    }
                    if (button.getData() != null) {
                        req.flags |= 1;
                        req.data = button.getData();
                    }
                    self.getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
                }
            }
        }
    }

    // MurkaGram: dual UI, core-13 SendMessagesHelper.sendReaction
    public static void sendReaction(SendMessagesHelper self, 
        MessageObject messageObject,
        ArrayList<ReactionsLayoutInBubble.VisibleReaction> visibleReactions,
        ReactionsLayoutInBubble.VisibleReaction addedReaction,
        boolean big,
        boolean addToRecent,
        BaseFragment parentFragment,
        Runnable callback
    ) {
        if (messageObject == null || parentFragment == null) {
            return;
        }
        TLRPC.TL_messages_sendReaction req = new TLRPC.TL_messages_sendReaction();
        if (messageObject.messageOwner.isThreadMessage && messageObject.messageOwner.fwd_from != null) {
            req.peer = self.getMessagesController().getInputPeer(messageObject.getFromChatId());
            req.msg_id = messageObject.messageOwner.fwd_from.saved_from_msg_id;
        } else {
            req.peer = self.getMessagesController().getInputPeer(messageObject.getDialogId());
            req.msg_id = messageObject.getId();
        }
        req.add_to_recent = addToRecent;
        if (addToRecent && addedReaction != null) {
            MediaDataController.getInstance(self.currentAccount).recentReactions.add(0, ReactionsUtils.toTLReaction(addedReaction));
        }
        if (visibleReactions != null && !visibleReactions.isEmpty()) {
            for (int i = 0; i < visibleReactions.size(); i++ ) {
                ReactionsLayoutInBubble.VisibleReaction visibleReaction = visibleReactions.get(i);
                if (visibleReaction.documentId != 0) {
                    TLRPC.TL_reactionCustomEmoji reactionCustomEmoji = new TLRPC.TL_reactionCustomEmoji();
                    reactionCustomEmoji.document_id = visibleReaction.documentId;
                    req.reaction.add(reactionCustomEmoji);
                    req.flags |= 1;
                } else if (visibleReaction.emojicon != null) {
                    TLRPC.TL_reactionEmoji defaultReaction = new TLRPC.TL_reactionEmoji();
                    defaultReaction.emoticon = visibleReaction.emojicon;
                    req.reaction.add(defaultReaction);
                    req.flags |= 1;
                }
            }
        }
        if (big) {
            req.flags |= 2;
            req.big = true;
        }
        self.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                self.getMessagesController().processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> self.getMessagesController().murkaScheduleGhostOffline()); // MurkaGram: режим призрака — офлайн после реакции
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback);
                }
            }
        });
    }

}
