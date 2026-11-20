/*
 * MurkaGram: экран настроек клиента MurkaGram.
 * Открывается из главного экрана настроек (ProfileActivity, пункт «MurkaGram настройки»).
 * Сверху — иконка приложения, название и версия; ниже — категории настроек и ссылки.
 */

package org.telegram.classic.ui;


import org.telegram.ui.LauncherIconController;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.classic.ui.ActionBar.Theme;
import org.telegram.classic.ui.Components.LayoutHelper;
import org.telegram.classic.ui.Components.UItem;
import org.telegram.classic.ui.Components.UniversalAdapter;
import org.telegram.classic.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Locale;

public class MurkaGramSettingsActivity extends UniversalFragment {

    public static final String CHANNEL_URL = "https://t.me/KittenBoxStudio";
    public static final String CHAT_URL = "https://t.me/kittenboxstudio_chat";
    public static final String GITHUB_URL = "https://github.com/ToupeKit/MurkaGram"; // MurkaGram

    private static final int ID_MONITORING = 1;
    private static final int ID_DESIGN = 2;
    private static final int ID_CHANNEL = 3;
    private static final int ID_CHAT = 4;
    private static final int ID_BADGES = 5; // MurkaGram: «Бейджики»
    private static final int ID_GITHUB = 6; // MurkaGram: ссылка на GitHub

    private View headerView;

    @Override
    protected CharSequence getTitle() {
        return ""; // название показывается в шапке экрана
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (headerView == null) {
            headerView = createHeaderView(getContext());
        }
        items.add(UItem.asCustom(headerView));
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaCategories)));
        items.add(UItem.asButton(ID_MONITORING, R.drawable.classic_msg_media, LocaleController.getString(R.string.MurkaMonitoring)));
        items.add(UItem.asButton(ID_DESIGN, R.drawable.classic_msg_palette, LocaleController.getString(R.string.MurkaDesign)));
        items.add(UItem.asButton(ID_BADGES, R.drawable.msg_murka_badge_cat, LocaleController.getString(R.string.MurkaBadges)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaLinks)));
        items.add(UItem.asButton(ID_CHANNEL, R.drawable.classic_msg_channel, LocaleController.getString(R.string.MurkaLinkChannel), "@KittenBoxStudio"));
        items.add(UItem.asButton(ID_CHAT, R.drawable.classic_msg_groups, LocaleController.getString(R.string.MurkaLinkChats), "@kittenboxstudio_chat"));
        items.add(UItem.asButton(ID_GITHUB, R.drawable.classic_msg_link2, "GitHub", "ToupeKit/MurkaGram")); // MurkaGram
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MONITORING) {
            presentFragment(new MurkaMonitoringActivity());
        } else if (item.id == ID_DESIGN) {
            presentFragment(new MurkaDesignActivity());
        } else if (item.id == ID_BADGES) {
            presentFragment(new MurkaBadgesActivity());
        } else if (item.id == ID_CHANNEL) {
            Browser.openUrl(getParentActivity(), CHANNEL_URL);
        } else if (item.id == ID_CHAT) {
            Browser.openUrl(getParentActivity(), CHAT_URL);
        } else if (item.id == ID_GITHUB) { // MurkaGram
            Browser.openUrl(getParentActivity(), GITHUB_URL);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private View createHeaderView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(0, AndroidUtilities.dp(24), 0, AndroidUtilities.dp(20));

        layout.addView(new IconView(context), LayoutHelper.createLinear(90, 90, Gravity.CENTER_HORIZONTAL));

        TextView nameView = new TextView(context);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setText("MurkaGram"); // MurkaGram: одно название для обоих интерфейсов
        layout.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

        String version;
        try {
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);
        } catch (Exception e) {
            version = BuildVars.BUILD_VERSION_STRING;
        }
        TextView versionView = new TextView(context);
        versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        versionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        versionView.setText(version);
        layout.addView(versionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
        return layout;
    }

    // Иконка приложения: слои адаптивной иконки, обрезанные скруглённым квадратом
    private static class IconView extends View {

        private final Drawable background;
        private final Drawable foreground;
        private final Path path = new Path();
        private final RectF rect = new RectF();

        public IconView(Context context) {
            super(context);
            LauncherIconController.LauncherIcon icon = LauncherIconController.LauncherIcon.MURKAGRAM; // градиентная иконка
            background = ContextCompat.getDrawable(context, icon.background);
            foreground = ContextCompat.getDrawable(context, icon.foreground);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            path.rewind();
            rect.set(0, 0, w, h);
            path.addRoundRect(rect, AndroidUtilities.dp(22), AndroidUtilities.dp(22), Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            // слой адаптивной иконки 108dp, видимая часть — центральные 72dp
            int padX = w / 4;
            int padY = h / 4;
            canvas.save();
            canvas.clipPath(path);
            if (background != null) {
                background.setBounds(-padX, -padY, w + padX, h + padY);
                background.draw(canvas);
            }
            if (foreground != null) {
                foreground.setBounds(-padX, -padY, w + padX, h + padY);
                foreground.draw(canvas);
            }
            canvas.restore();
        }
    }
}
