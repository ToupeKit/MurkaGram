/*
 * MurkaGram: «MD3 дизайн» — параметры карточек секций (поверх нативных секций 13.2.2) и прочие MD3-элементы.
 * Всё работает только при MurkaConfig.md3Design; при выключенной настройке поведение стандартное.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MurkaConfig;

public class MurkaMD3 {

    public static boolean enabled() {
        return MurkaConfig.md3Design;
    }

    // MurkaGram: категория «Material Design 3»
    /** MD3-переключатели (крупный трек). */
    public static boolean switchStyle() {
        return enabled() && MurkaConfig.md3Switches;
    }

    /** Карточки с боковыми отступами и скруглением. Иначе — строки на всю ширину. */
    public static boolean containers() {
        return MurkaConfig.md3Containers;
    }

    /** Заголовки секций над карточкой (на сером). Без контейнеров заголовок всегда внутри секции. */
    public static boolean separateHeaders() {
        return containers() && MurkaConfig.md3SeparateHeaders;
    }

    /** MurkaConfig.DIVIDERS_HIDE / DIVIDERS_LINES / DIVIDERS_SEGMENTS. */
    public static int dividerMode() {
        return MurkaConfig.md3DividerMode;
    }

    /** Счётчик изменений вида карточек: списки из стека пересчитывают отступы при следующей отрисовке (RecyclerListView.murkaCheckMD3Version). */
    public static int version;

    public static void bump() {
        version++;
    }

    // dp
    public static final int SIDE = 12;
    public static final int GAP = 2;
    public static final int R_BIG = 20;
    public static final int R_SMALL = 5;
    public static final int POPUP_RADIUS = 20;

    // MD3-переключатель (dp): трек 52x32, круг 8dp (выкл) / 12dp (вкл)
    public static final int SWITCH_W = 52;
    public static final int SWITCH_H = 32;
    public static final int SWITCH_MARGIN = 16;
    /** На сколько dp нужно сдвинуть текст от края в MD3, чтобы он не налезал на большой переключатель. */
    public static final int SWITCH_TEXT_EXTRA = 10;

    /** Ширина вида Switch в ячейке (dp). */
    public static int switchWidth() {
        return switchStyle() ? SWITCH_W : 37; // MurkaGram: «Переключатели в стиле MD3»
    }

    /** Высота вида Switch в ячейке (dp). */
    public static int switchHeight(int stock) {
        return switchStyle() ? Math.max(stock, SWITCH_H) : stock;
    }

    /** Отступ Switch от края ячейки (dp). */
    public static int switchMargin(int stock) {
        return switchStyle() ? SWITCH_MARGIN : stock;
    }

    /** Отступ текста от края со стороны Switch (dp). */
    public static int switchTextMargin(int stock) {
        return switchStyle() ? stock + SWITCH_TEXT_EXTRA : stock;
    }

    /** InsetDrawable без padding: View.setBackground() не перезаписывает отступы вида (иначе пункты меню «съезжают»). */
    public static Drawable insetNoPadding(Drawable d, int l, int t, int r, int b) {
        return new android.graphics.drawable.InsetDrawable(d, l, t, r, b) {
            @Override
            public boolean getPadding(@NonNull Rect padding) {
                padding.set(0, 0, 0, 0);
                return false;
            }
        };
    }

    // MurkaGram: ui-13 — карточки настроек: MD3 настраивает нативные секции 13.2.2 (RecyclerListView.setSections(),
    // ListSectionsDecoration / BitmapSectionsItemDecoration) вместо своей декорации. При выключенном MD3 — сток 13.2.2.

    /** MurkaGram: «Классические списки» (12.2.10) — плоские блоки на всю ширину, линии-разделители, заголовки внутри. */
    public static boolean classicLists() {
        return MurkaConfig.classicLists;
    }

    /** Боковой отступ карточек секций (px): с «Контейнерами» — SIDE, без — во всю ширину. stock — значение экрана. */
    public static int sectionPadding(int stock) {
        if (classicLists() && stock > 0) { // MurkaGram: «Классические списки»
            return 0;
        }
        if (!enabled() || stock <= 0) {
            return stock;
        }
        return containers() ? AndroidUtilities.dp(SIDE) : 0;
    }

    /** Радиус карточек секций (px): с «Контейнерами» — R_BIG, без — прямые углы. */
    public static float sectionRadius(float stock) {
        if (classicLists() && stock > 0) { // MurkaGram: «Классические списки»
            return 0;
        }
        if (!enabled() || stock <= 0) {
            return stock;
        }
        return containers() ? AndroidUtilities.dp(R_BIG) : 0;
    }

    /** «Сегменты»: каждая строка — отдельная плитка с зазором GAP и малыми углами R_SMALL. */
    public static boolean sectionSegments() {
        return enabled() && !classicLists() && dividerMode() == MurkaConfig.DIVIDERS_SEGMENTS;
    }

    /** Зазор между плитками «Сегментов» (px). */
    public static int sectionGap() {
        return sectionSegments() ? AndroidUtilities.dp(GAP) : 0;
    }

    /** Малый радиус между плитками «Сегментов» (px); без «Контейнеров» углы прямые. */
    public static float sectionSmallRadius() {
        return sectionSegments() && containers() ? AndroidUtilities.dp(R_SMALL) : 0;
    }

    /** Срезать нижние разделители ячеек внутри карточки (режимы «Скрыть» и «Сегменты»). «Линии» — как в 13.2.2. */
    public static boolean hideSectionDividers() {
        return enabled() && !classicLists() && dividerMode() != MurkaConfig.DIVIDERS_LINES;
    }

    /** Сколько px снизу строки срезать, чтобы скрыть её разделитель (линия рисуется на h-1 и задевает h-2). */
    public static int dividerInset() {
        return Math.max(2, AndroidUtilities.dp(1));
    }

    /** Заголовки секций (HeaderCell) вне карточек. */
    public static boolean separateSectionHeaders() {
        return enabled() && !classicLists() && separateHeaders();
    }

    /** Тонкие разделители между чатами в списке чатов (13.2.2 их убрал) — только в режиме «Линии». */
    public static boolean chatSeparators() {
        return classicLists() || enabled() && dividerMode() == MurkaConfig.DIVIDERS_LINES; // MurkaGram: + «Классические списки»
    }

    /** Фон всплывающего меню: скруглённый прямоугольник 20dp с мягкой тенью; отступы как у исходного 9-patch. */
    public static class PopupBackground extends Drawable {

        private final Rect padding = new Rect();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int color = 0xFFFFFFFF;
        private int alpha = 0xFF;

        public PopupBackground(Drawable original) {
            if (original != null) {
                try {
                    original.getPadding(padding);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            updatePaint();
        }

        public void setColor(int color) {
            this.color = color;
            updatePaint();
            invalidateSelf();
        }

        private void updatePaint() {
            int a = (int) (((color >>> 24) / 255f) * alpha);
            paint.setColor((color & 0x00FFFFFF) | (a << 24));
            paint.setShadowLayer(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(1), ((int) (0x30 * (alpha / 255f))) << 24);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            rect.set(b.left + padding.left, b.top + padding.top, b.right - padding.right, b.bottom - padding.bottom);
            if (rect.width() <= 0 || rect.height() <= 0) {
                return;
            }
            float r = Math.min(AndroidUtilities.dp(POPUP_RADIUS), Math.min(rect.width(), rect.height()) / 2f);
            canvas.drawRoundRect(rect, r, r, paint);
        }

        @Override
        public boolean getPadding(@NonNull Rect out) {
            out.set(padding);
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            if (this.alpha != alpha) {
                this.alpha = alpha;
                updatePaint();
                invalidateSelf();
            }
        }

        @Override
        public int getAlpha() {
            return alpha;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            // цвет задаётся через setColor
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
