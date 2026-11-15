/*
 * MurkaGram: «Эмодзи» — отрисовка эмодзи системным или своим шрифтом вместо встроенных картинок.
 * Вызывается из Emoji.loadEmoji (Utilities.globalQueue). При любой ошибке возвращает null —
 * тогда используется стандартная картинка из assets.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;

import java.io.File;

public class MurkaEmoji {

    private static final Object sync = new Object();
    private static boolean typefaceLoaded;
    private static Typeface typeface;
    private static TextPaint paint;

    private static Typeface getTypeface() {
        if (typefaceLoaded) {
            return typeface;
        }
        typefaceLoaded = true;
        try {
            if (MurkaConfig.emojiMode == MurkaConfig.EMOJI_SYSTEM) {
                typeface = Typeface.DEFAULT;
            } else if (MurkaConfig.emojiMode == MurkaConfig.EMOJI_CUSTOM && !TextUtils.isEmpty(MurkaConfig.customEmojiPath)) {
                File file = new File(MurkaConfig.customEmojiPath);
                if (file.exists()) {
                    Typeface t = Typeface.createFromFile(file);
                    if (t != null && t != Typeface.DEFAULT) {
                        typeface = t;
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            typeface = null;
        }
        return typeface;
    }

    public static Bitmap render(String code) {
        if (MurkaConfig.emojiMode == MurkaConfig.EMOJI_DEFAULT || code == null) {
            return null;
        }
        try {
            synchronized (sync) {
                Typeface tf = getTypeface();
                if (tf == null) {
                    return null;
                }
                if (paint == null) {
                    paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    paint.setTypeface(tf);
                }
                final String text = Emoji.fixEmoji(code);
                if (Build.VERSION.SDK_INT >= 23 && !paint.hasGlyph(text)) {
                    return null;
                }
                final int size = AndroidUtilities.density <= 1f ? 33 : 66;
                paint.setTextSize(size);
                float w = paint.measureText(text);
                Paint.FontMetrics fm = paint.getFontMetrics();
                float h = fm.descent - fm.ascent;
                if (w <= 0 || h <= 0 || w > h * 1.5f) {
                    // последовательность не собралась в один глиф — лучше стандартная картинка
                    return null;
                }
                float scale = Math.min(size * 0.95f / w, size * 0.95f / h);
                paint.setTextSize(size * scale);
                w = paint.measureText(text);
                fm = paint.getFontMetrics();
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawText(text, (size - w) / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint);
                return bitmap;
            }
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}
