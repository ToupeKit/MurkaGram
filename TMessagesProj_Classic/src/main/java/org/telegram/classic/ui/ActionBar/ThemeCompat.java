package org.telegram.classic.ui.ActionBar;

import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

// MurkaGram: dual UI. Color keys of the classic Theme are unified with the modern one (theme_unify.py),
// so a modern ResourcesProvider coming from a SHARED class can be used by classic code through this adapter.
public final class ThemeCompat {

    private ThemeCompat() {
    }

    public static Theme.ResourcesProvider wrap(org.telegram.ui.ActionBar.Theme.ResourcesProvider provider) {
        if (provider == null) {
            return null;
        }
        if (provider instanceof Theme.ResourcesProvider) {
            return (Theme.ResourcesProvider) provider;
        }
        return new Wrapped(provider);
    }

    // MurkaGram: dual UI, copy of a classic wallpaper description for the core (MessagesController uploads it)
    public static org.telegram.ui.ActionBar.Theme.OverrideWallpaperInfo toModern(Theme.OverrideWallpaperInfo info) {
        if (info == null) {
            return null;
        }
        org.telegram.ui.ActionBar.Theme.OverrideWallpaperInfo m = new org.telegram.ui.ActionBar.Theme.OverrideWallpaperInfo();
        m.fileName = info.fileName;
        m.originalFileName = info.originalFileName;
        m.slug = info.slug;
        m.color = info.color;
        m.gradientColor1 = info.gradientColor1;
        m.gradientColor2 = info.gradientColor2;
        m.gradientColor3 = info.gradientColor3;
        m.rotation = info.rotation;
        m.isBlurred = info.isBlurred;
        m.isMotion = info.isMotion;
        m.intensity = info.intensity;
        m.wallpaperId = info.wallpaperId;
        m.accessHash = info.accessHash;
        m.dialogId = info.dialogId;
        m.forBoth = info.forBoth;
        m.uploadingProgress = info.uploadingProgress;
        m.requestIds = info.requestIds;
        m.prevUserWallpaper = info.prevUserWallpaper;
        return m;
    }

    // MurkaGram: dual UI, the classic counterpart of a modern theme (EmojiThemes items keep modern ThemeInfo): same key,
    // both Theme classes read the same theme list from prefs
    public static Theme.ThemeInfo toClassic(org.telegram.ui.ActionBar.Theme.ThemeInfo info) {
        if (info == null) {
            return null;
        }
        Theme.ThemeInfo themeInfo = Theme.getTheme(info.getKey());
        if (themeInfo == null) {
            themeInfo = Theme.getTheme(info.isDark() ? "Dark Blue" : "Blue");
        }
        return themeInfo;
    }

    private static final class Wrapped implements Theme.ResourcesProvider {

        private final org.telegram.ui.ActionBar.Theme.ResourcesProvider modern;

        Wrapped(org.telegram.ui.ActionBar.Theme.ResourcesProvider modern) {
            this.modern = modern;
        }

        @Override
        public int getColor(int key) {
            return modern.getColor(key);
        }

        @Override
        public int getColorOrDefault(int key) {
            return modern.getColorOrDefault(key);
        }

        @Override
        public int getCurrentColor(int key) {
            return modern.getCurrentColor(key);
        }

        @Override
        public void setAnimatedColor(int key, int color) {
            modern.setAnimatedColor(key, color);
        }

        @Override
        public Drawable getDrawable(String drawableKey) {
            return modern.getDrawable(drawableKey);
        }

        @Override
        public Paint getPaint(String paintKey) {
            return modern.getPaint(paintKey);
        }

        @Override
        public boolean hasGradientService() {
            return modern.hasGradientService();
        }

        @Override
        public boolean isDark() {
            return modern.isDark();
        }

        @Override
        public void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
            modern.applyServiceShaderMatrix(w, h, translationX, translationY);
        }

        @Override
        public ColorFilter getAnimatedEmojiColorFilter() {
            return modern.getAnimatedEmojiColorFilter();
        }

        @Override
        public boolean isCompactMode() {
            return modern.isCompactMode();
        }
    }
}
