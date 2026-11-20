/*
 * MurkaGram: экран «Исключения» мониторинга — чаты, в которых удалённые сообщения и изменения не сохраняются.
 */

package org.telegram.classic.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.MurkaConfig;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.classic.ui.ActionBar.AlertDialog;
import org.telegram.classic.ui.ActionBar.Theme;
import org.telegram.classic.ui.Components.UItem;
import org.telegram.classic.ui.Components.UniversalAdapter;
import org.telegram.classic.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MurkaExceptionsActivity extends UniversalFragment {

    private static final int ID_ADD = 1;

    @Override
    public boolean onFragmentCreate() {
        loadUnknownDialogs();
        return super.onFragmentCreate();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MurkaExceptions);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_ADD, R.drawable.classic_msg2_chats_add, LocaleController.getString(R.string.MurkaExceptionsAdd)).accent());
        ArrayList<Long> dids = MurkaConfig.getMonitorExceptions(currentAccount);
        for (int i = 0; i < dids.size(); i++) {
            long did = dids.get(i);
            if (getMessagesController().getUserOrChat(did) == null) {
                continue; // ещё не загружен из базы — строка была бы пустой
            }
            items.add(UItem.asFilterChat(true, did));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MurkaExceptionsInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            openPicker();
        } else if (item.viewType == UniversalAdapter.VIEW_TYPE_FILTER_CHAT && item.dialogId != 0) {
            showRemoveDialog(item.dialogId);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void updateList() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void openPicker() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("checkCanWrite", false);
        // Режим выбора нескольких чатов: нажатие отмечает чат, кнопка с галочкой подтверждает выбор.
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_WIDGET);
        args.putBoolean("allowGlobalSearch", false);
        DialogsActivity activity = new DialogsActivity(args);
        activity.setDelegate((fragment, dids, message, param, notify, scheduleDate, topicsFragment) -> {
            for (int i = 0; i < dids.size(); i++) {
                MessagesStorage.TopicKey key = dids.get(i);
                if (key == null || key.dialogId == 0 || DialogObject.isEncryptedDialog(key.dialogId)) {
                    continue;
                }
                MurkaConfig.addMonitorException(currentAccount, key.dialogId);
            }
            fragment.finishFragment();
            updateList();
            return true;
        });
        presentFragment(activity);
    }

    private void showRemoveDialog(long did) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MurkaExceptions));
        builder.setMessage(LocaleController.getString(R.string.MurkaExceptionsRemove));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            MurkaConfig.removeMonitorException(currentAccount, did);
            updateList();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    // После перезапуска MessagesController может не знать пользователя/чат — подгружаем из базы.
    private void loadUnknownDialogs() {
        ArrayList<Long> unknownUsers = new ArrayList<>();
        ArrayList<Long> unknownChats = new ArrayList<>();
        ArrayList<Long> dids = MurkaConfig.getMonitorExceptions(currentAccount);
        for (int i = 0; i < dids.size(); i++) {
            long did = dids.get(i);
            if (getMessagesController().getUserOrChat(did) != null) {
                continue;
            }
            if (did > 0) {
                unknownUsers.add(did);
            } else {
                unknownChats.add(-did);
            }
        }
        if (unknownUsers.isEmpty() && unknownChats.isEmpty()) {
            return;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            ArrayList<TLRPC.User> users = new ArrayList<>();
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            if (!unknownUsers.isEmpty()) {
                try {
                    getMessagesStorage().getUsersInternal(unknownUsers, users);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (!unknownChats.isEmpty()) {
                try {
                    getMessagesStorage().getChatsInternal(TextUtils.join(",", unknownChats), chats);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                getMessagesController().putUsers(users, true);
                getMessagesController().putChats(chats, true);
                updateList();
            });
        });
    }
}
