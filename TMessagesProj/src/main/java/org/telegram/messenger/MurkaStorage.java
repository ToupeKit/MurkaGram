/*
 * MurkaGram: хранилище раздела «Мониторинг» — сохранённые удалённые сообщения и история изменений.
 * Работает поверх базы MessagesStorage (cache4.db), все обращения к БД — только на storageQueue.
 *
 * Таблицы:
 *   murka_deleted(uid, mid, date, data)            — сообщения, удаление которых перехвачено (сами сообщения остаются в messages_v2);
 *   murka_edit_history(uid, mid, version_date, saved_at, text, data) — предыдущие версии изменённых сообщений.
 */

package org.telegram.messenger;

import android.text.TextUtils;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

public class MurkaStorage {

    public static class EditVersion {
        public int date;
        public String text;
    }

    // Запись о дате прочтения диапазона исходящих сообщений (prevMax < mid <= maxId прочитаны в момент date).
    public static class ReadRecord {
        public int prevMax;
        public int maxId;
        public int date;
    }

    public static void createTables(SQLiteDatabase database) throws SQLiteException {
        database.executeFast("CREATE TABLE IF NOT EXISTS murka_deleted(uid INTEGER, mid INTEGER, date INTEGER, data BLOB, PRIMARY KEY(uid, mid));").stepThis().dispose();
        database.executeFast("CREATE TABLE IF NOT EXISTS murka_edit_history(uid INTEGER, mid INTEGER, version_date INTEGER, saved_at INTEGER, text TEXT, data BLOB);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS murka_edit_history_idx ON murka_edit_history(uid, mid);").stepThis().dispose();
        database.executeFast("CREATE TABLE IF NOT EXISTS murka_read_dates(uid INTEGER, max_id INTEGER, prev_max INTEGER, date INTEGER, PRIMARY KEY(uid, max_id));").stepThis().dispose();
    }

    /**
     * Вызывается из MessagesController.processUpdateArray вместо настоящего удаления.
     * Ключи карты как в processUpdateArray: 0 — личные чаты и обычные группы (без uid), -channel_id — каналы/супергруппы.
     *
     * @param localDeleted id сообщений, удаляемых локально самим клиентом (CallLogActivity) — их не перехватываем.
     * @return карта сообщений, которые нужно удалить по-настоящему (или null).
     */
    public static LongSparseArray<ArrayList<Integer>> interceptDeletedMessages(int currentAccount, LongSparseArray<ArrayList<Integer>> deletedMessages, ArrayList<Integer> localDeleted) {
        LongSparseArray<ArrayList<Integer>> toSave = new LongSparseArray<>();
        LongSparseArray<ArrayList<Integer>> realDelete = null;
        for (int a = 0, size = deletedMessages.size(); a < size; a++) {
            long key = deletedMessages.keyAt(a);
            ArrayList<Integer> ids = deletedMessages.valueAt(a);
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            if (key != 0 && MurkaConfig.isMonitorExcluded(currentAccount, key)) { // MurkaGram: «Исключения» — канал/супергруппа удаляется по-настоящему
                if (realDelete == null) {
                    realDelete = new LongSparseArray<>();
                }
                realDelete.put(key, new ArrayList<>(ids));
                continue;
            }
            if (key == 0 && localDeleted != null && !localDeleted.isEmpty()) {
                ArrayList<Integer> save = new ArrayList<>();
                ArrayList<Integer> real = new ArrayList<>();
                for (int b = 0, size2 = ids.size(); b < size2; b++) {
                    Integer id = ids.get(b);
                    if (localDeleted.contains(id)) {
                        real.add(id);
                    } else {
                        save.add(id);
                    }
                }
                if (!real.isEmpty()) {
                    if (realDelete == null) {
                        realDelete = new LongSparseArray<>();
                    }
                    realDelete.put(key, real);
                }
                if (!save.isEmpty()) {
                    toSave.put(key, save);
                }
            } else {
                toSave.put(key, new ArrayList<>(ids));
            }
        }
        if (toSave.size() > 0) {
            markMessagesDeleted(currentAccount, toSave);
        }
        return realDelete;
    }

    /**
     * Помечает сообщения как удалённые (сохраняет в murka_deleted), не удаляя их из messages_v2.
     * Сообщения с истёкшим ttl_period (автоудаление) удаляются по-настоящему.
     */
    public static void markMessagesDeleted(int currentAccount, LongSparseArray<ArrayList<Integer>> byKey) {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            if (database == null) {
                return;
            }
            final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            LongSparseArray<ArrayList<Integer>> savedByUid = new LongSparseArray<>();
            for (int a = 0, size = byKey.size(); a < size; a++) {
                long key = byKey.keyAt(a);
                ArrayList<Integer> ids = byKey.valueAt(a);
                if (ids == null || ids.isEmpty()) {
                    continue;
                }
                ArrayList<Integer> ttlExpired = null;
                SQLiteCursor cursor = null;
                SQLitePreparedStatement state = null;
                try {
                    String idsStr = TextUtils.join(",", ids);
                    if (key == 0) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, mid, date, data FROM messages_v2 WHERE mid IN(%s) AND is_channel = 0", idsStr));
                    } else {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, mid, date, data FROM messages_v2 WHERE mid IN(%s) AND uid = %d", idsStr, key));
                    }
                    state = database.executeFast("REPLACE INTO murka_deleted VALUES(?, ?, ?, ?)");
                    while (cursor.next()) {
                        long uid = cursor.longValue(0);
                        int mid = cursor.intValue(1);
                        int date = cursor.intValue(2);
                        NativeByteBuffer data = cursor.byteBufferValue(3);
                        if (data == null) {
                            continue;
                        }
                        boolean expired = false;
                        try {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            if (message != null && message.ttl_period > 0 && message.date + message.ttl_period <= now + 60) {
                                expired = true;
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (!expired && MurkaConfig.isMonitorExcluded(currentAccount, uid)) { // MurkaGram: «Исключения» — удаляем по-настоящему
                            expired = true;
                        }
                        if (expired) {
                            data.reuse();
                            if (ttlExpired == null) {
                                ttlExpired = new ArrayList<>();
                            }
                            ttlExpired.add(mid);
                            continue;
                        }
                        state.requery();
                        state.bindLong(1, uid);
                        state.bindInteger(2, mid);
                        state.bindInteger(3, date);
                        state.bindByteBuffer(4, data);
                        state.step();
                        data.reuse();

                        ArrayList<Integer> mids = savedByUid.get(uid);
                        if (mids == null) {
                            mids = new ArrayList<>();
                            savedByUid.put(uid, mids);
                        }
                        mids.add(mid);
                    }
                    state.dispose();
                    state = null;
                    cursor.dispose();
                    cursor = null;
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (state != null) {
                        state.dispose();
                    }
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
                if (ttlExpired != null) {
                    // Автоудаление по таймеру и чаты из «Исключений» — удаляем по-настоящему, как это сделал бы Telegram.
                    final ArrayList<Integer> expiredFinal = ttlExpired;
                    final long channelId = -key;
                    final long keyFinal = key;
                    ArrayList<Long> dialogIds = storage.markMessagesAsDeleted(key, expiredFinal, false, true, 0, 0);
                    storage.updateDialogsWithDeletedMessages(key, channelId, expiredFinal, dialogIds, false);
                    AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(currentAccount).murkaOnRealDeleted(keyFinal, expiredFinal)); // MurkaGram
                }
            }
            if (savedByUid.size() > 0) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0, size = savedByUid.size(); a < size; a++) {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.murkaMessagesDeleted, savedByUid.keyAt(a), savedByUid.valueAt(a));
                    }
                });
            }
        });
    }

    /**
     * Вызывается на storageQueue из MessagesStorage.getMessagesInternal: проставляет флаги murkaDeleted / murkaHasHistory.
     */
    public static void applyFlags(SQLiteDatabase database, long dialogId, ArrayList<TLRPC.Message> messages) {
        if (database == null || messages == null || messages.isEmpty() || DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        ArrayList<Integer> mids = new ArrayList<>();
        for (int a = 0, size = messages.size(); a < size; a++) {
            TLRPC.Message message = messages.get(a);
            if (message != null && message.id > 0) {
                mids.add(message.id);
            }
        }
        if (mids.isEmpty()) {
            return;
        }
        String idsStr = TextUtils.join(",", mids);
        HashSet<Integer> deleted = queryMids(database, String.format(Locale.US, "SELECT mid FROM murka_deleted WHERE uid = %d AND mid IN(%s)", dialogId, idsStr));
        HashSet<Integer> withHistory = queryMids(database, String.format(Locale.US, "SELECT DISTINCT mid FROM murka_edit_history WHERE uid = %d AND mid IN(%s)", dialogId, idsStr));
        if (deleted.isEmpty() && withHistory.isEmpty()) {
            return;
        }
        for (int a = 0, size = messages.size(); a < size; a++) {
            TLRPC.Message message = messages.get(a);
            if (message == null || message.id <= 0) {
                continue;
            }
            if (deleted.contains(message.id)) {
                message.murkaDeleted = true;
            }
            if (withHistory.contains(message.id)) {
                message.murkaHasHistory = true;
            }
        }
    }

    private static HashSet<Integer> queryMids(SQLiteDatabase database, String sql) {
        HashSet<Integer> result = new HashSet<>();
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(sql);
            while (cursor.next()) {
                result.add(cursor.intValue(0));
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return result;
    }

    /**
     * Вызывается из MessagesController.processUpdateArray ПЕРЕД putMessages(..., -2, ...) — на той же storageQueue,
     * поэтому старая версия сообщения читается из messages_v2 до перезаписи.
     * Сохраняются только чужие сообщения (!out) и только при реальном изменении текста или медиа.
     */
    public static void saveEditHistory(int currentAccount, ArrayList<TLRPC.Message> newMessages) {
        if (newMessages == null || newMessages.isEmpty()) {
            return;
        }
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            if (database == null) {
                return;
            }
            final long clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            LongSparseArray<ArrayList<Integer>> updatedByUid = new LongSparseArray<>();
            SQLitePreparedStatement state = null;
            try {
                for (int a = 0, size = newMessages.size(); a < size; a++) {
                    TLRPC.Message message = newMessages.get(a);
                    if (message == null || message.id <= 0 || message.out) {
                        continue;
                    }
                    long uid = MessageObject.getDialogId(message);
                    if (DialogObject.isEncryptedDialog(uid) || MurkaConfig.isMonitorExcluded(currentAccount, uid)) { // MurkaGram: «Исключения»
                        continue;
                    }
                    TLRPC.Message oldMessage = null;
                    SQLiteCursor cursor = null;
                    try {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND uid = %d", message.id, uid));
                        if (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                oldMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                if (oldMessage != null) {
                                    oldMessage.readAttachPath(data, clientUserId);
                                }
                                data.reuse();
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        if (cursor != null) {
                            cursor.dispose();
                        }
                    }
                    if (oldMessage == null || oldMessage.out) {
                        continue;
                    }
                    boolean textChanged = !TextUtils.equals(oldMessage.message, message.message);
                    boolean mediaChanged = getMediaId(oldMessage) != getMediaId(message);
                    if (!textChanged && !mediaChanged) {
                        continue;
                    }

                    if (state == null) {
                        state = database.executeFast("INSERT INTO murka_edit_history VALUES(?, ?, ?, ?, ?, ?)");
                    }
                    NativeByteBuffer data = new NativeByteBuffer(oldMessage.getObjectSize());
                    oldMessage.serializeToStream(data);
                    state.requery();
                    state.bindLong(1, uid);
                    state.bindInteger(2, message.id);
                    state.bindInteger(3, oldMessage.edit_date != 0 ? oldMessage.edit_date : oldMessage.date);
                    state.bindInteger(4, now);
                    state.bindString(5, oldMessage.message != null ? oldMessage.message : "");
                    state.bindByteBuffer(6, data);
                    state.step();
                    data.reuse();

                    message.murkaHasHistory = true;
                    ArrayList<Integer> mids = updatedByUid.get(uid);
                    if (mids == null) {
                        mids = new ArrayList<>();
                        updatedByUid.put(uid, mids);
                    }
                    if (!mids.contains(message.id)) {
                        mids.add(message.id);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
            if (updatedByUid.size() > 0) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0, size = updatedByUid.size(); a < size; a++) {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.murkaEditHistoryUpdated, updatedByUid.keyAt(a), updatedByUid.valueAt(a));
                    }
                });
            }
        });
    }

    private static long getMediaId(TLRPC.Message message) {
        if (message == null || message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty || message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return 0;
        }
        TLRPC.Photo photo = MessageObject.getPhoto(message);
        if (photo != null) {
            return photo.id;
        }
        TLRPC.Document document = MessageObject.getDocument(message);
        if (document != null) {
            return document.id;
        }
        return 0;
    }

    /**
     * Загружает сохранённые версии сообщения (от старой к новой) и возвращает их на UI-поток.
     */
    public static void getEditHistory(int currentAccount, long uid, int mid, Utilities.Callback<ArrayList<EditVersion>> callback) {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<EditVersion> result = new ArrayList<>();
            SQLiteDatabase database = storage.getDatabase();
            if (database != null) {
                SQLiteCursor cursor = null;
                try {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT version_date, text FROM murka_edit_history WHERE uid = %d AND mid = %d ORDER BY saved_at ASC, rowid ASC", uid, mid));
                    while (cursor.next()) {
                        EditVersion version = new EditVersion();
                        version.date = cursor.intValue(0);
                        version.text = cursor.isNull(1) ? "" : cursor.stringValue(1);
                        result.add(version);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    /**
     * MurkaGram: «Сохранять дату прочтения» — запоминает, когда собеседник прочитал исходящие сообщения.
     * Вызывается с UI-потока (из MessagesController при TL_updateReadHistoryOutbox), сама запись — на storageQueue.
     * prevMax — предыдущий read_outbox_max, maxId — новый, date — время прочтения.
     */
    public static void saveReadDate(int currentAccount, long uid, int prevMax, int maxId, int date) {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            if (database == null) {
                return;
            }
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("REPLACE INTO murka_read_dates VALUES(?, ?, ?, ?)");
                state.requery();
                state.bindLong(1, uid);
                state.bindInteger(2, maxId);
                state.bindInteger(3, prevMax);
                state.bindInteger(4, date);
                state.step();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.murkaReadDateSaved, uid, prevMax, maxId, date));
        });
    }

    /**
     * Загружает все сохранённые даты прочтения для диалога и возвращает их на UI-поток.
     */
    public static void loadReadDates(int currentAccount, long uid, Utilities.Callback<ArrayList<ReadRecord>> callback) {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<ReadRecord> result = new ArrayList<>();
            SQLiteDatabase database = storage.getDatabase();
            if (database != null) {
                SQLiteCursor cursor = null;
                try {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT prev_max, max_id, date FROM murka_read_dates WHERE uid = %d", uid));
                    while (cursor.next()) {
                        ReadRecord record = new ReadRecord();
                        record.prevMax = cursor.intValue(0);
                        record.maxId = cursor.intValue(1);
                        record.date = cursor.intValue(2);
                        result.add(record);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    /**
     * Возвращает минимальную дату прочтения сообщения mid среди записей (prevMax < mid && maxId >= mid), или 0.
     */
    public static int findReadDate(ArrayList<ReadRecord> records, int mid) {
        if (records == null) {
            return 0;
        }
        int result = 0;
        for (int a = 0, size = records.size(); a < size; a++) {
            ReadRecord record = records.get(a);
            if (record.prevMax < mid && record.maxId >= mid && record.date != 0) {
                if (result == 0 || record.date < result) {
                    result = record.date;
                }
            }
        }
        return result;
    }

    /**
     * Вызывается на storageQueue из MessagesStorage.markMessagesAsDeletedInternal при настоящем удалении сообщений.
     */
    public static void onMessagesDeleted(SQLiteDatabase database, long uid, String idsStr) {
        try {
            database.executeFast(String.format(Locale.US, "DELETE FROM murka_deleted WHERE uid = %d AND mid IN(%s)", uid, idsStr)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "DELETE FROM murka_edit_history WHERE uid = %d AND mid IN(%s)", uid, idsStr)).stepThis().dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * Вызывается на storageQueue из MessagesStorage.deleteDialog при удалении чата / очистке истории пользователем.
     * keepMids — сообщения, которые остаются в чате (через запятую), или null.
     */
    public static void onDialogCleared(SQLiteDatabase database, long uid, String keepMids) {
        try {
            if (keepMids == null) {
                database.executeFast(String.format(Locale.US, "DELETE FROM murka_deleted WHERE uid = %d", uid)).stepThis().dispose();
                database.executeFast(String.format(Locale.US, "DELETE FROM murka_edit_history WHERE uid = %d", uid)).stepThis().dispose();
                database.executeFast(String.format(Locale.US, "DELETE FROM murka_read_dates WHERE uid = %d", uid)).stepThis().dispose();
            } else {
                database.executeFast(String.format(Locale.US, "DELETE FROM murka_deleted WHERE uid = %d AND mid NOT IN(%s)", uid, keepMids)).stepThis().dispose();
                database.executeFast(String.format(Locale.US, "DELETE FROM murka_edit_history WHERE uid = %d AND mid NOT IN(%s)", uid, keepMids)).stepThis().dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
