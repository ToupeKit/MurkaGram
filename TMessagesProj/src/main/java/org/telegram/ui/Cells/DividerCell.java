/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class DividerCell extends View {

    private boolean forceDarkTheme;
    private Paint paint = new Paint();
    private Theme.ResourcesProvider resourcesProvider;
    private boolean murkaHideInMD3; // MurkaGram: MD3 — пустой промежуток вместо линии

    public DividerCell(Context context) {
        this(context, null);
    }

    public DividerCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), getPaddingTop() + getPaddingBottom() + 1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (murkaHideInMD3 && !forceDarkTheme && org.telegram.ui.Components.MurkaMD3.hideSectionDividers()) {
            return; // MurkaGram: MD3 (ui-13: только в режимах «Скрыть»/«Сегменты»)
        }
        if (forceDarkTheme) {
            paint.setColor(ColorUtils.blendARGB(Color.BLACK, Theme.getColor(Theme.key_voipgroup_dialogBackground, resourcesProvider),  0.2f));
        } else {
            paint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        }

        canvas.drawLine(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getPaddingTop(), paint);
    }

    // MurkaGram: MD3 — не рисовать линию, когда разделители в карточках скрыты (MurkaMD3.hideSectionDividers).
    public void setMurkaHideInMD3(boolean value) {
        murkaHideInMD3 = value;
        invalidate();
    }

    public void setForceDarkTheme(boolean forceDarkTheme) {
        this.forceDarkTheme = forceDarkTheme;
    }
}
