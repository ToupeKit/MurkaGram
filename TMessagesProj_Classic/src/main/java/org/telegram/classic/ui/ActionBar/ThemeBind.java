package org.telegram.classic.ui.ActionBar;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.classic.ui.Components.EditTextBoldCursor;

// MurkaGram: stub (core-13) — 13.2.2 binds view colors to theme keys and re-applies them on theme change.
// Here colors are only applied once, like the 12.2.10 code did with setTextColor(Theme.getColor(...)).
public final class ThemeBind {

    private ThemeBind() {
    }

    public static final class Spec {
        final int key;
        final float multAlpha;

        private Spec(int key, float multAlpha) {
            this.key = key;
            this.multAlpha = multAlpha;
        }

        int getColor(Theme.ResourcesProvider resourcesProvider) {
            final int color = Theme.getColor(key, resourcesProvider);
            return multAlpha >= 0 ? Theme.multAlpha(color, multAlpha) : color;
        }
    }

    public static Spec multAlpha(int key, float multiplier) {
        return new Spec(key, multiplier);
    }

    public static Spec of(int key) {
        return new Spec(key, -1);
    }

    public static void textColor(TextView view, int key) {
        textColor(view, key, null);
    }

    public static void textColor(TextView view, int key, Theme.ResourcesProvider resourcesProvider) {
        view.setTextColor(Theme.getColor(key, resourcesProvider));
    }

    public static void textColor(TextView view, Spec spec) {
        textColor(view, spec, null);
    }

    public static void textColor(TextView view, Spec spec, Theme.ResourcesProvider resourcesProvider) {
        view.setTextColor(spec.getColor(resourcesProvider));
    }

    public static void textColor(SimpleTextView view, int key) {
        textColor(view, key, null);
    }

    public static void textColor(SimpleTextView view, int key, Theme.ResourcesProvider resourcesProvider) {
        view.setTextColor(Theme.getColor(key, resourcesProvider));
    }

    public static void linkTextColor(TextView view, int key) {
        linkTextColor(view, key, null);
    }

    public static void linkTextColor(TextView view, int key, Theme.ResourcesProvider resourcesProvider) {
        view.setLinkTextColor(Theme.getColor(key, resourcesProvider));
    }

    public static void backgroundColor(View view, int key) {
        backgroundColor(view, key, null);
    }

    public static void backgroundColor(View view, int key, Theme.ResourcesProvider resourcesProvider) {
        view.setBackgroundColor(Theme.getColor(key, resourcesProvider));
    }

    public static void colorFilter(ImageView view, int key, PorterDuff.Mode mode) {
        colorFilter(view, key, null, mode);
    }

    public static void colorFilter(ImageView view, int key, Theme.ResourcesProvider resourcesProvider, PorterDuff.Mode mode) {
        view.setColorFilter(new PorterDuffColorFilter(Theme.getColor(key, resourcesProvider), mode));
    }

    public static void editHintColor(EditTextBoldCursor editText, int key) {
        editHintColor(editText, key, null);
    }

    public static void editHintColor(EditTextBoldCursor editText, int key, Theme.ResourcesProvider resourcesProvider) {
        editText.setHintColor(Theme.getColor(key, resourcesProvider));
    }

    public static void lineColors(EditTextBoldCursor editText, int key, int activeKey, int errorKey) {
        lineColors(editText, key, activeKey, errorKey, null);
    }

    public static void lineColors(EditTextBoldCursor editText, int key, int activeKey, int errorKey, Theme.ResourcesProvider resourcesProvider) {
        editText.setLineColors(Theme.getColor(key, resourcesProvider), Theme.getColor(activeKey, resourcesProvider), Theme.getColor(errorKey, resourcesProvider));
    }

    public static void selectorBackground(View view, Drawable drawable, Spec content, Spec ripple) {
        selectorBackground(view, drawable, content, ripple, null);
    }

    public static void selectorBackground(View view, Drawable drawable, Spec content, Spec ripple, Theme.ResourcesProvider resourcesProvider) {
        view.setBackground(drawable);
    }
}
