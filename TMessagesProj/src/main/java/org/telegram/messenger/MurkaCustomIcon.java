/*
 * MurkaGram: «Своя иконка» — ярлык MurkaGram на рабочем столе с картинкой пользователя.
 * Android не даёт менять иконку в лаунчере на произвольную картинку, поэтому создаём
 * закреплённый (pinned) ярлык, который открывает LaunchActivity.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;

public class MurkaCustomIcon {

    public static final String SHORTCUT_ID = "murka_custom_icon";
    public static final int SIZE = 432; // 108dp при xxxhdpi — размер слоя адаптивной иконки
    private static final int SAFE_INSET = 72; // безопасная зона 72dp из 108dp

    public static final int PIN_FAILED = 0;
    public static final int PIN_REQUESTED = 1;
    public static final int PIN_UPDATED = 2;

    private static volatile Bitmap previewCache;
    private static volatile boolean previewLoaded;

    public static File getFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), "murka_custom_icon.png");
    }

    public static boolean exists() {
        return getFile().exists();
    }

    public static Bitmap loadPreview() {
        if (!previewLoaded) {
            previewLoaded = true;
            File file = getFile();
            if (file.exists()) {
                try {
                    previewCache = BitmapFactory.decodeFile(file.getAbsolutePath());
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
        return previewCache;
    }

    // Обрезка по центру до квадрата и масштабирование до SIZE x SIZE. Всегда новый bitmap.
    public static Bitmap makeSquare(Bitmap src) {
        if (src == null || src.isRecycled() || src.getWidth() <= 0 || src.getHeight() <= 0) {
            return null;
        }
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            int side = Math.min(w, h);
            int left = (w - side) / 2;
            int top = (h - side) / 2;
            Bitmap result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            canvas.drawBitmap(src, new Rect(left, top, left + side, top + side), new Rect(0, 0, SIZE, SIZE), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            return result;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Вызывать в фоновом потоке.
    public static boolean save(Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        }
        File file = getFile();
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(tmp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                tmp.delete();
                return false;
            }
        } catch (Throwable e) {
            FileLog.e(e);
            tmp.delete();
            return false;
        }
        if (file.exists()) {
            file.delete();
        }
        if (!tmp.renameTo(file)) {
            tmp.delete();
            return false;
        }
        previewCache = bitmap;
        previewLoaded = true;
        return true;
    }

    private static Bitmap createAdaptiveBitmap(Bitmap src) {
        Bitmap result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        // фон на весь слой, чтобы маска лаунчера не оставляла пустых углов
        canvas.drawBitmap(src, null, new Rect(0, 0, SIZE, SIZE), paint);
        // сама картинка — в безопасной зоне, чтобы маска её не обрезала
        canvas.drawBitmap(src, null, new Rect(SAFE_INSET, SAFE_INSET, SIZE - SAFE_INSET, SIZE - SAFE_INSET), paint);
        return result;
    }

    public static boolean isSupported(Context context) {
        try {
            return ShortcutManagerCompat.isRequestPinShortcutSupported(context);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static int pin(Context context) {
        try {
            Bitmap src = loadPreview();
            if (src == null || src.isRecycled()) {
                return PIN_FAILED;
            }
            Intent intent = new Intent(context, LaunchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                    .setShortLabel(LocaleController.getString(R.string.AppName))
                    .setIcon(IconCompat.createWithAdaptiveBitmap(createAdaptiveBitmap(src)))
                    .setIntent(intent)
                    .build();
            List<ShortcutInfoCompat> list = Collections.singletonList(info);

            try {
                ShortcutManagerCompat.enableShortcuts(context, list); // мог быть отключён через «Удалить»
            } catch (Throwable e) {
                FileLog.e(e);
            }

            boolean pinned = false;
            try {
                List<ShortcutInfoCompat> pinnedList = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED);
                if (pinnedList != null) {
                    for (int i = 0; i < pinnedList.size(); i++) {
                        if (SHORTCUT_ID.equals(pinnedList.get(i).getId())) {
                            pinned = true;
                            break;
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            if (pinned) {
                if (ShortcutManagerCompat.updateShortcuts(context, list)) {
                    return PIN_UPDATED;
                }
            }
            if (ShortcutManagerCompat.requestPinShortcut(context, info, null)) {
                return PIN_REQUESTED;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return PIN_FAILED;
    }

    public static void remove(Context context) {
        try {
            getFile().delete();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        previewCache = null;
        previewLoaded = true;
        try {
            ShortcutManagerCompat.disableShortcuts(context, Collections.singletonList(SHORTCUT_ID), LocaleController.getString(R.string.MurkaCustomIconDisabled));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
