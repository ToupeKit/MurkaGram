/*
 * MurkaGram: собственные настройки клиента MurkaGram (хранятся в SharedPreferences "murkaconfig").
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;

public class MurkaConfig {

    public static boolean saveDeletedMessages;
    public static boolean saveEditHistory;
    // Режим призрака: клиент не сообщает серверу о статусе «в сети».
    public static boolean ghostMode;
    // Сохранять дату прочтения исходящих сообщений (личные чаты).
    public static boolean saveReadDates;
    // Прозрачность сохранённых удалённых сообщений в процентах (10..100, по умолчанию 70).
    public static int deletedMessageTransparency;

    // Непрозрачность ячейки удалённого сообщения (1.0 — непрозрачно, 0.0 — полностью прозрачно).
    public static float getDeletedMessageAlpha() {
        return (100 - deletedMessageTransparency) / 100f;
    }

    // Показывать ли себя принудительно «в сети» (в режиме призрака — нет, показываем реальный статус).
    public static boolean forceSelfOnline(int account, long userId) {
        return !ghostMode && userId == UserConfig.getInstance(account).getClientUserId();
    }

    // Раздел «Дизайн».
    public static final int FONT_DEFAULT = 0;
    public static final int FONT_SYSTEM = 1;
    public static final int FONT_CUSTOM = 2;

    public static boolean centerTitle;
    public static boolean noNumberRounding;
    public static boolean timeWithSeconds;
    public static boolean springAnimation; // MurkaGram: spring-анимация переходов
    public static boolean noBubbleTail; // MurkaGram: «Убрать хвост сообщений»
    public static boolean messageMenuIconBar; // MurkaGram: «Быстрые действия иконками»
    public static boolean md3Design; // MurkaGram: «MD3 дизайн» (скруглённые карточки, меню, кнопки)
    // MurkaGram: категория «Material Design 3»
    public static final int FAB_CIRCLE = 0;
    public static final int FAB_SQUARE = 1;
    public static final int DIVIDERS_HIDE = 0;
    public static final int DIVIDERS_LINES = 1;
    public static final int DIVIDERS_SEGMENTS = 2;
    public static int md3FabShape;
    public static int md3DividerMode;
    public static boolean md3Containers;
    public static boolean md3SeparateHeaders;
    public static boolean md3Switches;
    public static boolean editedIcon; // MurkaGram: «Заменять «изменено» иконкой»
    public static int fontMode;
    public static String customFontPath;
    public static boolean hidePhone; // MurkaGram: «Скрыть номер телефона»

    // MurkaGram: «Эмодзи» — стандартные / системные / свой шрифт.
    public static final int EMOJI_DEFAULT = 0;
    public static final int EMOJI_SYSTEM = 1;
    public static final int EMOJI_CUSTOM = 2;
    public static int emojiMode;
    public static String customEmojiPath;

    // MurkaGram: «Настройки бокового меню» — порядок пунктов (ключи через запятую, "div" — разделитель).
    public static final String DRAWER_ITEMS_DEFAULT = "profile,status,div,newgroup,contacts,calls,saved,settings,div,invite,features"; // MurkaGram: + "status"
    public static String drawerItems;

    // MurkaGram: «Предупреждение призрака» перед просмотром одноразовых/самоуничтожающихся медиа.
    public static boolean ghostWarning;

    // MurkaGram: «Подтверждение отправки голосовых и кружков».
    public static boolean confirmVoiceSend;

    // MurkaGram: «Безлимитное закрепление чатов» — локальные закрепы сверх лимита (только список «Все чаты»).
    public static boolean unlimitedPins;
    // Ключ — id пользователя (не индекс аккаунта); значения неизменяемые (copy-on-write), индекс 0 — верх.
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.List<Long>> localPins = new java.util.concurrent.ConcurrentHashMap<>();

    // MurkaGram: «Исключения» мониторинга — по аккаунту; неизменяемые наборы (copy-on-write), читаются с разных потоков.
    private static final java.util.concurrent.atomic.AtomicReferenceArray<java.util.Set<Long>> monitorExceptions = new java.util.concurrent.atomic.AtomicReferenceArray<>(UserConfig.MAX_ACCOUNT_COUNT);

    // MurkaGram: «Бейджики» — порядок и скрытые бейджики своего аккаунта (по аккаунту, неизменяемые, copy-on-write).
    private static final java.util.concurrent.atomic.AtomicReferenceArray<java.util.List<String>> badgeOrder = new java.util.concurrent.atomic.AtomicReferenceArray<>(UserConfig.MAX_ACCOUNT_COUNT);
    private static final java.util.concurrent.atomic.AtomicReferenceArray<java.util.Set<String>> badgeHidden = new java.util.concurrent.atomic.AtomicReferenceArray<>(UserConfig.MAX_ACCOUNT_COUNT);

    private static final Object sync = new Object();
    private static boolean configLoaded;

    static {
        loadConfig();
    }

    static SharedPreferences getPreferences() { // MurkaGram: package-private для MurkaBadges
        return ApplicationLoader.applicationContext.getSharedPreferences("murkaconfig", Activity.MODE_PRIVATE);
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded || ApplicationLoader.applicationContext == null) {
                return;
            }
            SharedPreferences preferences = getPreferences();
            saveDeletedMessages = preferences.getBoolean("saveDeletedMessages", false);
            saveEditHistory = preferences.getBoolean("saveEditHistory", false);
            ghostMode = preferences.getBoolean("ghostMode", false);
            saveReadDates = preferences.getBoolean("saveReadDates", false);
            deletedMessageTransparency = Math.max(10, Math.min(100, preferences.getInt("deletedMessageTransparency", 70)));
            centerTitle = preferences.getBoolean("centerTitle", false);
            noNumberRounding = preferences.getBoolean("noNumberRounding", false);
            timeWithSeconds = preferences.getBoolean("timeWithSeconds", false);
            springAnimation = preferences.getBoolean("springAnimation", false);
            noBubbleTail = preferences.getBoolean("noBubbleTail", false);
            messageMenuIconBar = preferences.getBoolean("messageMenuIconBar", false); // MurkaGram
            md3Design = preferences.getBoolean("md3Design", false); // MurkaGram
            md3FabShape = preferences.getInt("md3FabShape", FAB_CIRCLE); // MurkaGram
            md3DividerMode = preferences.getInt("md3DividerMode", DIVIDERS_SEGMENTS); // MurkaGram
            md3Containers = preferences.getBoolean("md3Containers", true); // MurkaGram
            md3SeparateHeaders = preferences.getBoolean("md3SeparateHeaders", true); // MurkaGram
            md3Switches = preferences.getBoolean("md3Switches", true); // MurkaGram
            editedIcon = preferences.getBoolean("editedIcon", false); // MurkaGram
            fontMode = preferences.getInt("fontMode", FONT_DEFAULT);
            customFontPath = preferences.getString("customFontPath", null);
            hidePhone = preferences.getBoolean("hidePhone", false); // MurkaGram
            emojiMode = preferences.getInt("emojiMode", EMOJI_DEFAULT); // MurkaGram
            customEmojiPath = preferences.getString("customEmojiPath", null); // MurkaGram
            drawerItems = preferences.getString("drawerItems", DRAWER_ITEMS_DEFAULT); // MurkaGram
            ghostWarning = preferences.getBoolean("ghostWarning", true); // MurkaGram
            // MurkaGram: пункт «status» в боковом меню — добавить в уже сохранённые списки
            if (preferences.getInt("drawerItemsVer", 0) < 1) {
                SharedPreferences.Editor editor = preferences.edit();
                String saved = preferences.getString("drawerItems", null);
                if (saved != null && !saved.isEmpty() && !("," + saved + ",").contains(",status,")) {
                    java.util.ArrayList<String> list = new java.util.ArrayList<>();
                    for (String k : saved.split(",")) {
                        k = k.trim();
                        if (!k.isEmpty()) {
                            list.add(k);
                        }
                    }
                    int profile = list.indexOf("profile");
                    list.add(profile >= 0 ? profile + 1 : 0, "status");
                    drawerItems = android.text.TextUtils.join(",", list);
                    editor.putString("drawerItems", drawerItems);
                }
                editor.putInt("drawerItemsVer", 1).apply();
            }
            confirmVoiceSend = preferences.getBoolean("confirmVoiceSend", true); // MurkaGram
            unlimitedPins = preferences.getBoolean("unlimitedPins", false); // MurkaGram
            configLoaded = true;
        }
    }

    public static void toggleSaveDeletedMessages() {
        saveDeletedMessages = !saveDeletedMessages;
        getPreferences().edit().putBoolean("saveDeletedMessages", saveDeletedMessages).apply();
    }

    public static void toggleSaveEditHistory() {
        saveEditHistory = !saveEditHistory;
        getPreferences().edit().putBoolean("saveEditHistory", saveEditHistory).apply();
    }

    public static void toggleGhostMode() {
        ghostMode = !ghostMode;
        getPreferences().edit().putBoolean("ghostMode", ghostMode).apply();
    }

    // MurkaGram: переключить режим призрака и сообщить всем аккаунтам (общее для «Мониторинга» и бокового меню). Вызывать с UI-потока.
    public static void toggleGhostModeAndApply() {
        toggleGhostMode();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                MessagesController.getInstance(a).murkaOnGhostModeChanged();
            }
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.murkaDrawerItemsChanged);
    }

    // MurkaGram: «Настройки бокового меню».
    public static java.util.ArrayList<String> getDrawerItems() {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        String value = drawerItems == null ? DRAWER_ITEMS_DEFAULT : drawerItems;
        for (String s : value.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        return list;
    }

    public static void setDrawerItems(java.util.List<String> keys) {
        drawerItems = android.text.TextUtils.join(",", keys); // "" = пустое меню (не сброс)
        getPreferences().edit().putString("drawerItems", drawerItems).apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.murkaDrawerItemsChanged);
    }

    public static void resetDrawerItems() {
        drawerItems = DRAWER_ITEMS_DEFAULT;
        getPreferences().edit().remove("drawerItems").apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.murkaDrawerItemsChanged);
    }

    // MurkaGram: «Предупреждение призрака»
    public static void toggleGhostWarning() {
        ghostWarning = !ghostWarning;
        getPreferences().edit().putBoolean("ghostWarning", ghostWarning).apply();
    }

    // MurkaGram: «Исключения» мониторинга — в этих чатах удалённые сообщения и изменения не сохраняются.
    private static java.util.Set<Long> getMonitorExceptionsSet(int account) {
        if (account < 0 || account >= monitorExceptions.length()) {
            return java.util.Collections.emptySet();
        }
        java.util.Set<Long> set = monitorExceptions.get(account);
        if (set != null) {
            return set;
        }
        synchronized (sync) {
            set = monitorExceptions.get(account);
            if (set == null) {
                java.util.HashSet<Long> loaded = new java.util.HashSet<>();
                String value = getPreferences().getString("monitorExceptions" + account, "");
                if (value != null) {
                    for (String s : value.split(",")) {
                        s = s.trim();
                        if (!s.isEmpty()) {
                            try {
                                loaded.add(Long.parseLong(s));
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }
                set = java.util.Collections.unmodifiableSet(loaded);
                monitorExceptions.set(account, set);
            }
            return set;
        }
    }

    public static boolean isMonitorExcluded(int account, long dialogId) {
        return dialogId != 0 && getMonitorExceptionsSet(account).contains(dialogId);
    }

    public static java.util.ArrayList<Long> getMonitorExceptions(int account) {
        return new java.util.ArrayList<>(getMonitorExceptionsSet(account));
    }

    public static void addMonitorException(int account, long dialogId) {
        if (dialogId == 0) {
            return;
        }
        synchronized (sync) {
            java.util.HashSet<Long> set = new java.util.HashSet<>(getMonitorExceptionsSet(account));
            if (set.add(dialogId)) {
                saveMonitorExceptions(account, set);
            }
        }
    }

    public static void removeMonitorException(int account, long dialogId) {
        synchronized (sync) {
            java.util.HashSet<Long> set = new java.util.HashSet<>(getMonitorExceptionsSet(account));
            if (set.remove(dialogId)) {
                saveMonitorExceptions(account, set);
            }
        }
    }

    private static void saveMonitorExceptions(int account, java.util.HashSet<Long> set) {
        monitorExceptions.set(account, java.util.Collections.unmodifiableSet(set));
        getPreferences().edit().putString("monitorExceptions" + account, android.text.TextUtils.join(",", set)).apply();
    }

    // MurkaGram: «Бейджики» — сохранённый порядок (id через запятую).
    private static java.util.List<String> getBadgeOrderList(int account) {
        if (account < 0 || account >= badgeOrder.length()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<String> list = badgeOrder.get(account);
        if (list != null) {
            return list;
        }
        synchronized (sync) {
            list = badgeOrder.get(account);
            if (list == null) {
                list = java.util.Collections.unmodifiableList(splitIds(getPreferences().getString("badgeOrder" + account, "")));
                badgeOrder.set(account, list);
            }
            return list;
        }
    }

    private static java.util.Set<String> getBadgeHiddenSet(int account) {
        if (account < 0 || account >= badgeHidden.length()) {
            return java.util.Collections.emptySet();
        }
        java.util.Set<String> set = badgeHidden.get(account);
        if (set != null) {
            return set;
        }
        synchronized (sync) {
            set = badgeHidden.get(account);
            if (set == null) {
                set = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(splitIds(getPreferences().getString("badgeHidden" + account, ""))));
                badgeHidden.set(account, set);
            }
            return set;
        }
    }

    private static java.util.ArrayList<String> splitIds(String value) {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        if (value != null) {
            for (String s : value.split(",")) {
                s = s.trim();
                if (!s.isEmpty() && !list.contains(s)) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    public static java.util.ArrayList<String> getBadgeOrder(int account) {
        return new java.util.ArrayList<>(getBadgeOrderList(account));
    }

    public static void setBadgeOrder(int account, java.util.List<String> ids) {
        if (account < 0 || account >= badgeOrder.length()) {
            return;
        }
        synchronized (sync) {
            java.util.ArrayList<String> list = new java.util.ArrayList<>(ids);
            badgeOrder.set(account, java.util.Collections.unmodifiableList(list));
            getPreferences().edit().putString("badgeOrder" + account, android.text.TextUtils.join(",", list)).apply();
        }
        MurkaBadges.onPrefsChanged(account);
    }

    // MurkaGram: «Бейджики» — восстановить из общей базы (после переустановки), без выгрузки обратно
    static void restoreBadgePrefs(int account, java.util.List<String> order, java.util.Set<String> hidden) {
        if (account < 0 || account >= badgeOrder.length()) {
            return;
        }
        synchronized (sync) {
            java.util.ArrayList<String> list = new java.util.ArrayList<>(order);
            java.util.HashSet<String> set = new java.util.HashSet<>(hidden);
            badgeOrder.set(account, java.util.Collections.unmodifiableList(list));
            badgeHidden.set(account, java.util.Collections.unmodifiableSet(set));
            getPreferences().edit()
                    .putString("badgeOrder" + account, android.text.TextUtils.join(",", list))
                    .putString("badgeHidden" + account, android.text.TextUtils.join(",", set))
                    .apply();
        }
    }

    public static boolean isBadgeHidden(int account, String id) {
        return id != null && getBadgeHiddenSet(account).contains(id);
    }

    public static void setBadgeHidden(int account, String id, boolean hidden) {
        if (id == null || account < 0 || account >= badgeHidden.length()) {
            return;
        }
        synchronized (sync) {
            java.util.HashSet<String> set = new java.util.HashSet<>(getBadgeHiddenSet(account));
            if (!(hidden ? set.add(id) : set.remove(id))) {
                return;
            }
            badgeHidden.set(account, java.util.Collections.unmodifiableSet(set));
            getPreferences().edit().putString("badgeHidden" + account, android.text.TextUtils.join(",", set)).apply();
        }
        MurkaBadges.onPrefsChanged(account);
    }

    public static void toggleSaveReadDates() {
        saveReadDates = !saveReadDates;
        getPreferences().edit().putBoolean("saveReadDates", saveReadDates).apply();
    }

    public static void setDeletedMessageTransparency(int value) {
        deletedMessageTransparency = Math.max(10, Math.min(100, value));
        getPreferences().edit().putInt("deletedMessageTransparency", deletedMessageTransparency).apply();
    }

    public static void toggleCenterTitle() {
        centerTitle = !centerTitle;
        getPreferences().edit().putBoolean("centerTitle", centerTitle).apply();
    }

    public static void toggleNoNumberRounding() {
        noNumberRounding = !noNumberRounding;
        getPreferences().edit().putBoolean("noNumberRounding", noNumberRounding).apply();
    }

    public static void toggleTimeWithSeconds() {
        timeWithSeconds = !timeWithSeconds;
        getPreferences().edit().putBoolean("timeWithSeconds", timeWithSeconds).apply();
    }

    public static void toggleSpringAnimation() {
        springAnimation = !springAnimation;
        getPreferences().edit().putBoolean("springAnimation", springAnimation).apply();
    }

    public static void toggleNoBubbleTail() {
        noBubbleTail = !noBubbleTail;
        getPreferences().edit().putBoolean("noBubbleTail", noBubbleTail).apply();
    }

    // MurkaGram: «Быстрые действия иконками»
    public static void toggleMessageMenuIconBar() {
        messageMenuIconBar = !messageMenuIconBar;
        getPreferences().edit().putBoolean("messageMenuIconBar", messageMenuIconBar).apply();
    }

    // MurkaGram: «MD3 дизайн». commit(), т.к. сразу после переключения приложение может быть перезапущено.
    public static void toggleMd3Design() {
        md3Design = !md3Design;
        getPreferences().edit().putBoolean("md3Design", md3Design).commit();
    }

    // MurkaGram: категория «Material Design 3»
    public static void setMd3FabShape(int shape) {
        md3FabShape = shape;
        getPreferences().edit().putInt("md3FabShape", md3FabShape).apply();
    }

    public static void setMd3DividerMode(int mode) {
        md3DividerMode = mode;
        getPreferences().edit().putInt("md3DividerMode", md3DividerMode).apply();
    }

    public static void toggleMd3Containers() {
        md3Containers = !md3Containers;
        getPreferences().edit().putBoolean("md3Containers", md3Containers).commit(); // MurkaGram: commit(), т.к. следом может быть перезапуск
    }

    public static void toggleMd3SeparateHeaders() {
        md3SeparateHeaders = !md3SeparateHeaders;
        getPreferences().edit().putBoolean("md3SeparateHeaders", md3SeparateHeaders).apply();
    }

    // commit(), т.к. сразу после переключения приложение может быть перезапущено.
    public static void toggleMd3Switches() {
        md3Switches = !md3Switches;
        getPreferences().edit().putBoolean("md3Switches", md3Switches).commit();
    }

    // MurkaGram: «Заменять «изменено» иконкой»
    public static void toggleEditedIcon() {
        editedIcon = !editedIcon;
        getPreferences().edit().putBoolean("editedIcon", editedIcon).apply();
    }

    public static void setFontMode(int mode, String path) {
        fontMode = mode;
        customFontPath = path;
        // commit(), т.к. сразу после смены шрифта приложение может быть перезапущено (killProcess)
        getPreferences().edit().putInt("fontMode", fontMode).putString("customFontPath", customFontPath).commit();
    }

    // MurkaGram: «Скрыть номер телефона»
    public static void toggleHidePhone() {
        hidePhone = !hidePhone;
        getPreferences().edit().putBoolean("hidePhone", hidePhone).apply();
    }

    // MurkaGram: номер телефона для показа в профиле (или «Номер скрыт»). rawDigits — номер без «+».
    public static String formatPhone(String rawDigits) {
        if (hidePhone) {
            return LocaleController.getString(R.string.MurkaPhoneHidden);
        }
        return org.telegram.PhoneFormat.PhoneFormat.getInstance().format("+" + rawDigits);
    }

    // MurkaGram: «Эмодзи». commit(), т.к. сразу после смены приложение может быть перезапущено.
    public static void setEmojiMode(int mode, String path) {
        emojiMode = mode;
        customEmojiPath = path;
        getPreferences().edit().putInt("emojiMode", emojiMode).putString("customEmojiPath", customEmojiPath).commit();
    }

    // MurkaGram: «Подтверждение отправки голосовых и кружков»
    public static void toggleConfirmVoiceSend() {
        confirmVoiceSend = !confirmVoiceSend;
        getPreferences().edit().putBoolean("confirmVoiceSend", confirmVoiceSend).apply();
    }

    // MurkaGram: «Безлимитное закрепление чатов»
    public static void toggleUnlimitedPins() {
        unlimitedPins = !unlimitedPins;
        getPreferences().edit().putBoolean("unlimitedPins", unlimitedPins).apply();
    }

    private static long getPinsUserId(int account) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            return 0;
        }
        return UserConfig.getInstance(account).getClientUserId();
    }

    // Локально закреплённые чаты (неизменяемый список, индекс 0 — верх), независимо от переключателя.
    public static java.util.List<Long> getLocalPins(int account) {
        long userId = getPinsUserId(account);
        if (userId == 0) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Long> list = localPins.get(userId);
        if (list != null) {
            return list;
        }
        synchronized (sync) {
            list = localPins.get(userId);
            if (list == null) {
                java.util.ArrayList<Long> loaded = new java.util.ArrayList<>();
                for (String s : splitIds(getPreferences().getString("localPins_" + userId, ""))) {
                    try {
                        loaded.add(Long.parseLong(s));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                list = java.util.Collections.unmodifiableList(loaded);
                localPins.put(userId, list);
            }
            return list;
        }
    }

    public static boolean isLocalPinned(int account, long dialogId) {
        return dialogId != 0 && getLocalPins(account).contains(dialogId);
    }

    // Активен ли локальный закреп для диалога основного списка (не архив, не серверный закреп).
    public static boolean isLocalPinActive(int account, org.telegram.tgnet.TLRPC.Dialog dialog) {
        return unlimitedPins && dialog != null && !dialog.pinned && dialog.folder_id == 0 && isLocalPinned(account, dialog.id);
    }

    // Новый локальный закреп — наверх блока локальных закрепов (как у Telegram: новый закреп сверху).
    public static void addLocalPin(int account, long dialogId) {
        if (dialogId == 0) {
            return;
        }
        synchronized (sync) {
            java.util.ArrayList<Long> list = new java.util.ArrayList<>(getLocalPins(account));
            list.remove(dialogId);
            list.add(0, dialogId);
            saveLocalPins(account, list);
        }
    }

    public static void removeLocalPin(int account, long dialogId) {
        synchronized (sync) {
            java.util.ArrayList<Long> list = new java.util.ArrayList<>(getLocalPins(account));
            if (list.remove(dialogId)) {
                saveLocalPins(account, list);
            }
        }
    }

    public static void setLocalPins(int account, java.util.List<Long> ids) {
        synchronized (sync) {
            saveLocalPins(account, new java.util.ArrayList<>(ids));
        }
    }

    private static void saveLocalPins(int account, java.util.ArrayList<Long> list) {
        long userId = getPinsUserId(account);
        if (userId == 0) {
            return;
        }
        localPins.put(userId, java.util.Collections.unmodifiableList(list));
        getPreferences().edit().putString("localPins_" + userId, android.text.TextUtils.join(",", list)).apply();
    }
}
