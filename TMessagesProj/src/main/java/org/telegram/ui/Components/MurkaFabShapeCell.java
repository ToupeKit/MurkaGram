/*
 * MurkaGram: выбор формы плавающей кнопки (MD3) — две мини-превью списка чатов: круглая и «квадратная» кнопка.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MurkaConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import static org.telegram.messenger.AndroidUtilities.dp;

public class MurkaFabShapeCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final OptionView circleView;
    private final OptionView squareView;
    private boolean needDivider;

    public MurkaFabShapeCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        circleView = new OptionView(context, false);
        circleView.setContentDescription(LocaleController.getString(R.string.MurkaFabShapeCircle));
        squareView = new OptionView(context, true);
        squareView.setContentDescription(LocaleController.getString(R.string.MurkaFabShapeSquare));
        layout.addView(circleView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 0, 0, 8, 0));
        layout.addView(squareView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 8, 0, 0, 0));
        addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 16, 21, 16));

        circleView.setOnClickListener(v -> select(MurkaConfig.FAB_CIRCLE));
        squareView.setOnClickListener(v -> select(MurkaConfig.FAB_SQUARE));

        setWillNotDraw(false);
        setChecked(MurkaConfig.md3FabShape == MurkaConfig.FAB_SQUARE, false);
    }

    private void select(int shape) {
        if (MurkaConfig.md3FabShape != shape) {
            MurkaConfig.setMd3FabShape(shape); // DialogsActivity применит форму в onResume
        }
        setChecked(shape == MurkaConfig.FAB_SQUARE, true);
    }

    public void setChecked(boolean square, boolean animated) {
        circleView.setSelected(!square, animated);
        squareView.setSelected(square, animated);
    }

    public void setNeedDivider(boolean divider) {
        if (needDivider != divider) {
            needDivider = divider;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(112), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            Paint paint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
            if (paint == null) {
                paint = Theme.dividerPaint;
            }
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - dp(21), getMeasuredHeight() - 1, paint);
            } else {
                canvas.drawLine(dp(21), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, paint);
            }
        }
    }

    private class OptionView extends View {

        private final boolean square;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Drawable pencil;
        private final AnimatedFloat selectedT = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        private boolean selected;

        OptionView(Context context, boolean square) {
            super(context);
            this.square = square;
            strokePaint.setStyle(Paint.Style.STROKE);
            pencil = ContextCompat.getDrawable(context, R.drawable.mini_checklist_edited).mutate();
            ScaleStateListAnimator.apply(this, .04f, 1.25f);
        }

        void setSelected(boolean value, boolean animated) {
            selected = value;
            if (!animated) {
                selectedT.set(value, true);
            }
            invalidate();
        }

        private int color(int key) {
            return Theme.getColor(key, resourcesProvider);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float t = selectedT.set(selected);
            final int white = color(Theme.key_windowBackgroundWhite);
            final int gray = color(Theme.key_windowBackgroundWhiteGrayText);
            final int accent = color(Theme.key_windowBackgroundWhiteBlueHeader);

            // фон превью
            final float half = dp(1);
            rect.set(half, half, getWidth() - half, getHeight() - half);
            fillPaint.setColor(ColorUtils.blendARGB(white, color(Theme.key_windowBackgroundGray), 0.45f));
            canvas.drawRoundRect(rect, dp(12), dp(12), fillPaint);

            // рамка: тонкая серая, у выбранного — акцентная 2dp
            strokePaint.setStrokeWidth(AndroidUtilities.lerp(dp(1), dp(2), t));
            strokePaint.setColor(ColorUtils.blendARGB(ColorUtils.setAlphaComponent(gray, 0x30), accent, t));
            canvas.drawRoundRect(rect, dp(12), dp(12), strokePaint);

            // две строки «чатов»: аватар + две полоски
            final float avatarR = dp(12);
            final float avatarX = dp(11) + avatarR;
            final float[] rowsY = new float[]{dp(22), dp(57)};
            for (float cy : rowsY) {
                paint.setColor(ColorUtils.setAlphaComponent(gray, 0x30));
                canvas.drawCircle(avatarX, cy, avatarR, paint);

                final float lineX = avatarX + avatarR + dp(8);
                final float lineH = dp(4);
                paint.setColor(ColorUtils.setAlphaComponent(gray, 0x60));
                rect.set(lineX, cy - dp(6) - lineH / 2f, lineX + dp(30), cy - dp(6) + lineH / 2f);
                canvas.drawRoundRect(rect, lineH / 2f, lineH / 2f, paint);
                paint.setColor(ColorUtils.setAlphaComponent(gray, 0x30));
                rect.set(lineX, cy + dp(5) - lineH / 2f, lineX + dp(46), cy + dp(5) + lineH / 2f);
                canvas.drawRoundRect(rect, lineH / 2f, lineH / 2f, paint);
            }

            // кнопка
            final float fabSize = dp(32);
            final float fabRight = getWidth() - dp(12);
            final float fabBottom = getHeight() - dp(12);
            rect.set(fabRight - fabSize, fabBottom - fabSize, fabRight, fabBottom);
            paint.setColor(color(Theme.key_chats_actionBackground));
            if (square) {
                canvas.drawRoundRect(rect, dp(9), dp(9), paint);
            } else {
                canvas.drawCircle(rect.centerX(), rect.centerY(), fabSize / 2f, paint);
            }
            final int iconSize = dp(18);
            pencil.setColorFilter(new PorterDuffColorFilter(color(Theme.key_chats_actionIcon), PorterDuff.Mode.SRC_IN));
            pencil.setBounds((int) (rect.centerX() - iconSize / 2f), (int) (rect.centerY() - iconSize / 2f), (int) (rect.centerX() + iconSize / 2f), (int) (rect.centerY() + iconSize / 2f));
            pencil.draw(canvas);
        }
    }

    public static final class Factory extends UItem.UItemFactory<MurkaFabShapeCell> {
        static { setup(new Factory()); }

        @Override
        public MurkaFabShapeCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) { // MurkaGram: 13.2.2 signature (listView)
            return new MurkaFabShapeCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final MurkaFabShapeCell cell = (MurkaFabShapeCell) view;
            cell.setNeedDivider(divider);
            cell.setChecked(MurkaConfig.md3FabShape == MurkaConfig.FAB_SQUARE, false);
        }

        @Override
        public boolean isClickable() {
            return false; // нажатия обрабатывают сами превью
        }

        public static UItem as(int id) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.checked = MurkaConfig.md3FabShape == MurkaConfig.FAB_SQUARE;
            return item;
        }
    }
}
