package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.telegram.ui.ModernUiBridge;

// MurkaGram: dual UI. Which UI edition (modern 13.2.2 / classic 12.2.10) runs in this process.
// The choice is stored in prefs and read once per process; switching = setForNextLaunch() + restart.
// Without a registered classic bridge (other app flavors, or stage E3 where nothing registers it) the edition is modern.
public final class UiEdition {

    public static final String MODERN = "modern";
    public static final String CLASSIC = "classic";

    private static final String PREFS = "murka_edition";
    private static final String KEY = "edition";
    // MurkaGram: the classic interface is hidden until the user applies the "MurkaGram Classic" edition pack
    private static final String KEY_UNLOCKED = "classic_unlocked";

    private static volatile UiBridge classicBridge;
    private static volatile String current;

    private UiEdition() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // the stored edition, read once per process
    public static String current() {
        String value = current;
        if (value == null) {
            synchronized (UiEdition.class) {
                value = current;
                if (value == null) {
                    value = unlocked() && CLASSIC.equals(prefs().getString(KEY, MODERN)) ? CLASSIC : MODERN;
                    current = value;
                }
            }
        }
        return value;
    }

    // the edition stored for the next start (differs from current() after a switch until the restart)
    public static String next() {
        return unlocked() && CLASSIC.equals(prefs().getString(KEY, MODERN)) ? CLASSIC : MODERN;
    }

    // the classic code is bundled into this build (edition packs switch the interface without an APK)
    public static boolean hasClassicCode() {
        return classicBridge != null;
    }

    // the interface switch is shown: classic code is bundled and the classic edition pack was applied once
    public static boolean classicAvailable() {
        return classicBridge != null && unlocked();
    }

    private static boolean unlocked() {
        final SharedPreferences prefs = prefs();
        if (!prefs.contains(KEY_UNLOCKED)) {
            // builds before the unlock: whoever already uses the classic interface keeps it
            final boolean wasClassic = CLASSIC.equals(prefs.getString(KEY, MODERN));
            prefs.edit().putBoolean(KEY_UNLOCKED, wasClassic).commit();
            return wasClassic;
        }
        return prefs.getBoolean(KEY_UNLOCKED, false);
    }

    // called when the classic edition pack is applied
    public static void unlockClassic() {
        prefs().edit().putBoolean(KEY_UNLOCKED, true).commit();
    }

    public static boolean isClassic() {
        return classicBridge != null && CLASSIC.equals(current()); // current() is modern while locked
    }

    // called by the app module (ApplicationLoaderImpl) before any UI starts
    public static void registerClassic(UiBridge bridge) {
        classicBridge = bridge;
    }

    // stored for the next process start; the caller restarts the app
    public static void setForNextLaunch(String edition) {
        prefs().edit().putString(KEY, CLASSIC.equals(edition) ? CLASSIC : MODERN).commit();
    }

    public static UiBridge bridge() {
        final UiBridge classic = classicBridge;
        return classic != null && CLASSIC.equals(current()) ? classic : ModernUiBridge.getInstance();
    }

    // trampoline: `from` (an activity of the other edition, already past super.onCreate) passes its intent to `to` and finishes
    public static void handOver(Activity from, Class<? extends Activity> to) {
        final Intent intent = from.getIntent() != null ? new Intent(from.getIntent()) : new Intent();
        intent.setClass(from, to);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            from.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
        }
        from.finish();
        from.overridePendingTransition(0, 0);
    }

    // the main activity of the active edition (LaunchActivity.instance)
    public static Activity activity() {
        return bridge().activity();
    }
}
