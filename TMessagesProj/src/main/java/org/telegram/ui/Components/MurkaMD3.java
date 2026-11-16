/*
 * MurkaGram: «MD3 дизайн» — секции настроек в виде скруглённых карточек и прочие MD3-элементы.
 * Всё работает только при MurkaConfig.md3Design; при выключенной настройке поведение стандартное.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MurkaConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FixedHeightEmptyCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

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

    /** Счётчик изменений вида карточек: списки из стека пересчитывают отступы при следующей отрисовке. */
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

    public static final int KIND_CARD = 0;
    public static final int KIND_SEPARATOR = 1;
    public static final int KIND_SKIP = 2;

    /** Адаптеры, которые точно знают тип строки (например, UniversalAdapter). */
    public interface SectionProvider {
        int md3RowKind(int adapterPosition);
    }

    /** Переопределение типа строки для конкретного экрана: вернуть KIND_* или -1 (по умолчанию). */
    public interface KindOverride {
        int kind(int adapterPosition);
    }

    /** Подключает карточки к списку. Ничего не делает, если MD3 выключен или уже подключено. */
    public static Decoration apply(RecyclerListView listView) {
        if (!enabled() || listView == null) {
            return null;
        }
        if (listView.murkaMD3 != null) {
            return listView.murkaMD3;
        }
        try {
            Decoration decoration = new Decoration();
            listView.addItemDecoration(decoration);
            listView.murkaMD3 = decoration;
            return decoration;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static class Decoration extends RecyclerView.ItemDecoration {

        private final SparseIntArray typeKind = new SparseIntArray();
        private final SparseBooleanArray headerTypes = new SparseBooleanArray(); // MurkaGram: типы строк-заголовков (HeaderCell)
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final float[] radii = new float[8];
        public int colorKey = Theme.key_windowBackgroundWhite;
        public KindOverride kindOverride;
        private int seenVersion = MurkaMD3.version; // MurkaGram: см. MurkaMD3.bump()

        public static int kindOfView(View v) {
            if (v == null) {
                return KIND_SEPARATOR;
            }
            if (v instanceof SharedMediaLayout) {
                return KIND_SKIP;
            }
            if (v instanceof HeaderCell) { // MurkaGram: «Отделить заголовки»
                return separateHeaders() ? KIND_SEPARATOR : KIND_CARD;
            }
            if (v instanceof ShadowSectionCell || v instanceof TextInfoPrivacyCell
                    || v instanceof GraySectionCell || v instanceof EmptyCell || v instanceof FixedHeightEmptyCell
                    || v instanceof DividerCell || v.getClass() == View.class) {
                return KIND_SEPARATOR;
            }
            return KIND_CARD;
        }

        private int kindAt(RecyclerView rv, int pos, View view) {
            RecyclerView.Adapter adapter = rv.getAdapter();
            if (adapter == null || pos < 0 || pos >= adapter.getItemCount()) {
                return KIND_SEPARATOR;
            }
            if (kindOverride != null) {
                int k = kindOverride.kind(pos);
                if (k >= 0) {
                    return k;
                }
            }
            if (adapter instanceof SectionProvider) {
                return ((SectionProvider) adapter).md3RowKind(pos);
            }
            if (view != null) {
                return kindOfView(view);
            }
            int k = typeKind.get(adapter.getItemViewType(pos), -1);
            return k < 0 ? KIND_CARD : k;
        }

        // MurkaGram: строка — заголовок секции (HeaderCell)
        private boolean isHeaderAt(RecyclerView rv, int pos, View view) {
            if (view != null) {
                return view instanceof HeaderCell;
            }
            RecyclerView.Adapter adapter = rv.getAdapter();
            if (adapter == null || pos < 0 || pos >= adapter.getItemCount()) {
                return false;
            }
            RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(pos);
            if (holder != null) {
                return holder.itemView instanceof HeaderCell;
            }
            return headerTypes.get(adapter.getItemViewType(pos), false);
        }

        /** MurkaGram: заголовок внутри карточки («Отделить заголовки» выкл.) сливается со следующей строкой: без зазора и скруглений между ними. */
        private boolean joinedWithNext(RecyclerView rv, int pos, View view) {
            return pos != RecyclerView.NO_POSITION && isHeaderAt(rv, pos, view) && kindAt(rv, pos + 1, null) == KIND_CARD;
        }

        private static int positionOf(RecyclerView rv, View c) {
            RecyclerView.ViewHolder holder = rv.getChildViewHolder(c);
            if (holder == null) {
                return RecyclerView.NO_POSITION;
            }
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                pos = holder.getLayoutPosition();
            }
            return pos;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.setEmpty();
            if (!enabled()) {
                return;
            }
            try {
                RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
                if (holder != null) {
                    typeKind.put(holder.getItemViewType(), kindOfView(view));
                    headerTypes.put(holder.getItemViewType(), view instanceof HeaderCell); // MurkaGram
                }
                int pos = positionOf(parent, view);
                int k = pos == RecyclerView.NO_POSITION ? kindOfView(view) : kindAt(parent, pos, view);
                sanitize(view, k);
                if (k == KIND_CARD) {
                    // MurkaGram: боковые отступы только с контейнерами, зазор между строками только в режиме «Сегменты»
                    final int side = containers() ? AndroidUtilities.dp(SIDE) : 0;
                    final int gap = dividerMode() == MurkaConfig.DIVIDERS_SEGMENTS && !joinedWithNext(parent, pos, view) ? AndroidUtilities.dp(GAP) : 0; // MurkaGram: заголовок слит с первой строкой
                    outRect.set(side, pos == 0 ? side : 0, side, gap); // MurkaGram: верхний отступ, если список начинается с карточки
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        /** У разделителей (заголовки и т.п.) убираем белый фон, чтобы они лежали на сером. */
        private static void sanitize(View v, int k) {
            if (k == KIND_SEPARATOR && v.getBackground() instanceof ColorDrawable && ((ColorDrawable) v.getBackground()).getColor() != 0) {
                v.setBackground(new ColorDrawable(0));
            }
        }

        /** Строит форму карточки для дочернего view (в координатах списка). false — не карточка. */
        public boolean buildPath(RecyclerView rv, View c, Path out, int bottomInsetPx) {
            return buildPath(rv, c, out, bottomInsetPx, false);
        }

        /** MurkaGram: форма для обрезки самой ячейки: в режиме «Линии» разделитель ячейки виден везде, кроме последней строки. */
        public boolean buildPathForChild(RecyclerView rv, View c, Path out) {
            return buildPath(rv, c, out, 1, true);
        }

        private boolean buildPath(RecyclerView rv, View c, Path out, int bottomInsetPx, boolean forChild) {
            if (!enabled() || c == null || c.getVisibility() != View.VISIBLE || c.getWidth() <= 0 || c.getHeight() <= 0) {
                return false;
            }
            int pos = positionOf(rv, c);
            boolean first, last;
            boolean joinedTop = false, joinedBottom = false; // MurkaGram: стык заголовка и первой строки секции
            if (pos == RecyclerView.NO_POSITION) {
                if (kindOfView(c) != KIND_CARD) {
                    return false;
                }
                first = last = false;
            } else {
                if (kindAt(rv, pos, c) != KIND_CARD) {
                    return false;
                }
                first = kindAt(rv, pos - 1, null) != KIND_CARD;
                last = kindAt(rv, pos + 1, null) != KIND_CARD;
                joinedTop = !first && isHeaderAt(rv, pos - 1, null);
                joinedBottom = !last && isHeaderAt(rv, pos, c);
            }
            // MurkaGram: радиусы зависят от «Контейнеров» и типа разделителей
            final int big = containers() ? R_BIG : 0;
            final int small = containers() && dividerMode() == MurkaConfig.DIVIDERS_SEGMENTS ? R_SMALL : 0;
            float t = AndroidUtilities.dp(first ? big : joinedTop ? 0 : small);
            float b = AndroidUtilities.dp(last ? big : joinedBottom ? 0 : small);
            if (forChild && !last && dividerMode() == MurkaConfig.DIVIDERS_LINES) {
                bottomInsetPx = 0;
            }
            radii[0] = radii[1] = radii[2] = radii[3] = t;
            radii[4] = radii[5] = radii[6] = radii[7] = b;
            float bottom = c.getY() + c.getHeight() - bottomInsetPx;
            rect.set(c.getX(), c.getY(), c.getX() + c.getWidth(), bottom);
            if (rect.height() <= 0) {
                return false;
            }
            out.rewind();
            out.addRoundRect(rect, radii, Path.Direction.CW);
            return true;
        }

        @Override
        public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            if (!enabled()) {
                return;
            }
            // MurkaGram: настройки карточек поменялись — пересчитать отступы (RecyclerView кэширует их)
            if (seenVersion != MurkaMD3.version) {
                seenVersion = MurkaMD3.version;
                typeKind.clear();
                headerTypes.clear();
                parent.post(() -> {
                    if (!parent.isComputingLayout()) {
                        parent.invalidateItemDecorations();
                    }
                });
            }
            try {
                Theme.ResourcesProvider resourcesProvider = parent instanceof RecyclerListView ? ((RecyclerListView) parent).resourcesProvider : null;
                int defaultColor = Theme.getColor(colorKey, resourcesProvider);
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View c = parent.getChildAt(i);
                    if (!buildPath(parent, c, path, 0)) {
                        continue;
                    }
                    int color = defaultColor;
                    Drawable bg = c.getBackground();
                    if (bg instanceof ColorDrawable && (((ColorDrawable) bg).getColor() >>> 24) == 0xFF) {
                        color = ((ColorDrawable) bg).getColor();
                    }
                    paint.setColor(color);
                    paint.setAlpha((int) (0xFF * Math.max(0f, Math.min(1f, c.getAlpha()))));
                    canvas.drawPath(path, paint);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
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
