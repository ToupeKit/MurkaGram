/*
 * MurkaGram: экран «Бейджики» — порядок (перетаскивание долгим нажатием) и скрытие бейджиков текущего аккаунта.
 * Сами бейджики выдаются через Remote Config (см. MurkaBadges).
 */

package org.telegram.classic.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MurkaBadges;
import org.telegram.messenger.MurkaConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.classic.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.classic.ui.Components.UItem;
import org.telegram.classic.ui.Components.UniversalAdapter;
import org.telegram.classic.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MurkaBadgesActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_BADGE_BASE = 1000;

    private int orderSectionId = -1;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.murkaBadgesUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.murkaBadgesUpdated);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        listView.listenReorder(this::whenReordered);
        listView.allowReorder(true);
        return view;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.murkaBadgesUpdated) {
            updateList();
        }
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MurkaBadges);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaBadgesList)));
        ArrayList<String> ids = MurkaBadges.getOrderedBadges(currentAccount, MurkaBadges.getConfigBadges(getUserConfig().getClientUserId()));
        if (ids.isEmpty()) {
            orderSectionId = -1;
            items.add(UItem.asShadow(LocaleController.getString(R.string.MurkaBadgesEmpty)));
            return;
        }
        orderSectionId = adapter.reorderSectionStart();
        for (String badgeId : ids) {
            // id пункта — по индексу в реестре (стабильный при перестановке), сам id бейджика — в object2
            UItem item = UItem.asCheck(ID_BADGE_BASE + MurkaBadges.indexOf(badgeId), getBadgeText(badgeId)).setChecked(!MurkaConfig.isBadgeHidden(currentAccount, badgeId));
            item.object2 = badgeId;
            items.add(item);
        }
        adapter.reorderSectionEnd();
        items.add(UItem.asShadow(LocaleController.getString(R.string.MurkaBadgesInfo)));
    }

    private CharSequence getBadgeText(String badgeId) {
        SpannableStringBuilder text = new SpannableStringBuilder("d  ");
        Drawable drawable = ContextCompat.getDrawable(getContext() != null ? getContext() : ApplicationLoader.applicationContext, MurkaBadges.getIcon(badgeId));
        if (drawable != null) {
            text.setSpan(new ColoredImageSpan(drawable.mutate(), ColoredImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text.append(LocaleController.getString(MurkaBadges.getName(badgeId)));
        return text;
    }

    private void whenReordered(int id, ArrayList<UItem> sectionItems) {
        if (id != orderSectionId) {
            return;
        }
        ArrayList<String> order = new ArrayList<>();
        for (UItem it : sectionItems) {
            if (it.object2 instanceof String) {
                order.add((String) it.object2);
            }
        }
        MurkaConfig.setBadgeOrder(currentAccount, order); // список обновится по murkaBadgesUpdated
    }

    private void updateList() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object2 instanceof String) {
            String badgeId = (String) item.object2;
            boolean hidden = !MurkaConfig.isBadgeHidden(currentAccount, badgeId);
            MurkaConfig.setBadgeHidden(currentAccount, badgeId, hidden);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!hidden);
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false; // долгое нажатие уходит в ItemTouchHelper (перетаскивание)
    }
}
