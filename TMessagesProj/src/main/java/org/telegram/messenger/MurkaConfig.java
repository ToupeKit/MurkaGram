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
    public static final String DRAWER_ITEMS_DEFAULT = "profile,div,newgroup,contacts,calls,saved,settings,div,invite,features";
    public static String drawerItems;

    // MurkaGram: «Предупреждение призрака» перед просмотром одноразовых/самоуничтожающихся медиа.
    public static boolean ghostWarning;

    // MurkaGram: «Исключения» мониторинга — по аккаунту; неизменяемые наборы (copy-on-write), читаются с разных потоков.
    private static final java.util.concurrent.atomic.AtomicReferenceArray<java.util.Set<Long>> monitorExceptions = new java.util.concurrent.atomic.AtomicReferenceArray<>(UserConfig.MAX_ACCOUNT_COUNT);

    private static final Object sync = new Object();
    private static boolean configLoaded;

    static {
        loadConfig();
    }

    private static SharedPreferences getPreferences() {
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
            fontMode = preferences.getInt("fontMode", FONT_DEFAULT);
            customFontPath = preferences.getString("customFontPath", null);
            hidePhone = preferences.getBoolean("hidePhone", false); // MurkaGram
            emojiMode = preferences.getInt("emojiMode", EMOJI_DEFAULT); // MurkaGram
            customEmojiPath = preferences.getString("customEmojiPath", null); // MurkaGram
            drawerItems = preferences.getString("drawerItems", DRAWER_ITEMS_DEFAULT); // MurkaGram
            ghostWarning = preferences.getBoolean("ghostWarning", true); // MurkaGram
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
}
