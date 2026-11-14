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

    // Раздел «Дизайн».
    public static final int FONT_DEFAULT = 0;
    public static final int FONT_SYSTEM = 1;
    public static final int FONT_CUSTOM = 2;

    public static boolean centerTitle;
    public static boolean noNumberRounding;
    public static boolean timeWithSeconds;
    public static boolean springAnimation; // MurkaGram: spring-анимация переходов
    public static int fontMode;
    public static String customFontPath;

    // MurkaGram: «Настройки бокового меню» — порядок пунктов (ключи через запятую, "div" — разделитель).
    public static final String DRAWER_ITEMS_DEFAULT = "profile,div,newgroup,contacts,calls,saved,settings,div,invite,features";
    public static String drawerItems;

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
            fontMode = preferences.getInt("fontMode", FONT_DEFAULT);
            customFontPath = preferences.getString("customFontPath", null);
            drawerItems = preferences.getString("drawerItems", DRAWER_ITEMS_DEFAULT); // MurkaGram
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

    public static void setFontMode(int mode, String path) {
        fontMode = mode;
        customFontPath = path;
        // commit(), т.к. сразу после смены шрифта приложение может быть перезапущено (killProcess)
        getPreferences().edit().putInt("fontMode", fontMode).putString("customFontPath", customFontPath).commit();
    }
}
