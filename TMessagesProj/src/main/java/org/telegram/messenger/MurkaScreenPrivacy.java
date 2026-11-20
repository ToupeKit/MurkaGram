/*
 * MurkaGram: «Скрывать при записи экрана».
 * На Android 15+ (API 35) WindowManager сообщает, попадают ли окна приложения в запись экрана.
 * Пока идёт запись, аватары, названия чатов и тексты сообщений рисуются под спойлером
 * (тот же эффект частиц, что у скрытого текста Telegram). Используется в обоих интерфейсах.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Components.spoilers.SpoilerEffect;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class MurkaScreenPrivacy {

    private static boolean recording;
    private static final WeakHashMap<Activity, Consumer<Integer>> callbacks = new WeakHashMap<>();
    private static final WeakHashMap<View, SpoilerEffect> effects = new WeakHashMap<>();

    private static Paint fillPaint;
    private static Path clipPath;
    private static final RectF tmpRect = new RectF();

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 35;
    }

    /** true — идёт запись экрана и включена настройка: содержимое нужно прятать. */
    public static boolean isHiding() {
        return recording && MurkaConfig.hideOnScreenRecording;
    }

    // ---- Отслеживание записи ----

    public static void onActivityStarted(Activity activity) {
        if (activity == null || !isSupported()) {
            return;
        }
        MurkaConfig.loadConfig();
        if (!MurkaConfig.hideOnScreenRecording || callbacks.containsKey(activity)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 35) {
            register(activity);
        }
    }

    public static void onActivityStopped(Activity activity) {
        if (activity == null || !isSupported()) {
            return;
        }
        Consumer<Integer> callback = callbacks.remove(activity);
        if (callback != null && Build.VERSION.SDK_INT >= 35) {
            unregister(activity, callback);
        }
        if (callbacks.isEmpty()) {
            setRecording(false);
        }
    }

    /** Вызывается после переключения настройки (activity — текущая, видимая). */
    public static void onSettingChanged(Activity activity) {
        if (!isSupported()) {
            return;
        }
        if (MurkaConfig.hideOnScreenRecording) {
            onActivityStarted(activity);
        } else {
            ArrayList<Activity> activities = new ArrayList<>(callbacks.keySet());
            for (Activity a : activities) {
                onActivityStopped(a);
            }
        }
        refreshAll();
        if (activity != null && activity.getWindow() != null) {
            invalidateRecursive(activity.getWindow().getDecorView());
        }
    }

    @RequiresApi(35)
    private static void register(Activity activity) {
        try {
            Consumer<Integer> callback = state -> setRecording(state != null && state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE);
            int state = activity.getWindowManager().addScreenRecordingCallback(activity.getMainExecutor(), callback);
            callbacks.put(activity, callback);
            setRecording(state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @RequiresApi(35)
    private static void unregister(Activity activity, Consumer<Integer> callback) {
        try {
            activity.getWindowManager().removeScreenRecordingCallback(callback);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void setRecording(boolean value) {
        if (recording == value) {
            return;
        }
        recording = value;
        if (!value) {
            effects.clear(); // эффекты держат ссылку на View — отпускаем после записи
        }
        if (MurkaConfig.hideOnScreenRecording) {
            refreshAll();
        }
    }

    /** Перерисовать всё дерево View в окнах отслеживаемых активити. */
    private static void refreshAll() {
        AndroidUtilities.runOnUIThread(() -> {
            for (Map.Entry<Activity, Consumer<Integer>> e : new ArrayList<>(callbacks.entrySet())) {
                Activity activity = e.getKey();
                if (activity != null && activity.getWindow() != null) {
                    invalidateRecursive(activity.getWindow().getDecorView());
                }
            }
            if (!isHiding()) {
                effects.clear();
            }
        });
    }

    private static void invalidateRecursive(View view) {
        if (view == null) {
            return;
        }
        view.invalidate();
        if (view instanceof RecyclerView) {
            // Закэшированные/скрытые ячейки не являются детьми, но при возврате в список покажут старый кадр.
            RecyclerView rv = (RecyclerView) view;
            for (int i = 0; i < rv.getCachedChildCount(); i++) {
                invalidateRecursive(rv.getCachedChildAt(i));
            }
            for (int i = 0; i < rv.getHiddenChildCount(); i++) {
                invalidateRecursive(rv.getHiddenChildAt(i));
            }
            for (int i = 0; i < rv.getAttachedScrapChildCount(); i++) {
                invalidateRecursive(rv.getAttachedScrapChildAt(i));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateRecursive(group.getChildAt(i));
            }
        }
    }

    // ---- Рисование ----

    private static SpoilerEffect getEffect(View parent) {
        SpoilerEffect effect = parent == null ? null : effects.get(parent);
        if (effect == null) {
            effect = new SpoilerEffect();
            if (parent != null) {
                effect.setParentView(parent);
                effects.put(parent, effect);
            }
        }
        return effect;
    }

    /** Частицы спойлера в прямоугольнике (координаты текущего canvas). */
    public static void drawRectCover(View parent, Canvas canvas, float left, float top, float right, float bottom, int color) {
        if (right - left < 1 || bottom - top < 1) {
            return;
        }
        SpoilerEffect effect = getEffect(parent);
        effect.setColor(ColorUtils.setAlphaComponent(color, 0xFF));
        effect.setBounds((int) left, (int) top, (int) Math.ceil(right), (int) Math.ceil(bottom));
        effect.draw(canvas);
    }

    /** Вместо текста layout — спойлер по строкам (canvas уже сдвинут к началу layout). */
    public static void drawLayoutCover(View parent, Canvas canvas, Layout layout, int color) {
        if (layout == null) {
            return;
        }
        final int count = layout.getLineCount();
        for (int i = 0; i < count; i++) {
            float l = layout.getLineLeft(i);
            float r = layout.getLineRight(i);
            if (r - l < 1) {
                continue;
            }
            drawRectCover(parent, canvas, l, layout.getLineTop(i), r, layout.getLineBottom(i), color);
        }
    }

    /** Закрасить картинку (аватар/превью) непрозрачной подложкой и наложить спойлер. */
    public static void drawImageCover(View parent, Canvas canvas, ImageReceiver image, int backgroundColor, int color) {
        if (image == null) {
            return;
        }
        int[] radii = image.getRoundRadius();
        float radius = radii != null && radii.length > 0 ? radii[0] : 0;
        tmpRect.set(image.getImageX(), image.getImageY(), image.getImageX2(), image.getImageY2());
        drawRoundCover(parent, canvas, tmpRect, radius, backgroundColor, color);
    }

    public static void drawRoundCover(View parent, Canvas canvas, RectF rect, float radius, int backgroundColor, int color) {
        if (rect.width() < 1 || rect.height() < 1) {
            return;
        }
        if (fillPaint == null) {
            fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            clipPath = new Path();
        }
        // Непрозрачная подложка: фон с лёгким оттенком цвета частиц
        fillPaint.setColor(ColorUtils.blendARGB(ColorUtils.setAlphaComponent(backgroundColor, 0xFF), ColorUtils.setAlphaComponent(color, 0xFF), 0.12f));
        radius = Math.min(radius, Math.min(rect.width(), rect.height()) / 2f);
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        drawRectCover(parent, canvas, rect.left, rect.top, rect.right, rect.bottom, color);
        canvas.restore();
    }

    /** Круглая версия — для аватаров, нарисованных как View (BackupImageView). */
    public static void drawViewCircleCover(View parent, Canvas canvas, View child, int backgroundColor, int color) {
        tmpRect.set(child.getX(), child.getY(), child.getX() + child.getWidth(), child.getY() + child.getHeight());
        drawRoundCover(parent, canvas, tmpRect, Math.min(tmpRect.width(), tmpRect.height()) / 2f, backgroundColor, color);
    }
}
