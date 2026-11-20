/*
 * MurkaGram: экран «Меню «⋮» в списке чатов» (интерфейс 13.2.2) — показать/скрыть и упорядочить
 * (перетаскивание долгим нажатием) пункты меню, которое в 13.2.2 заменяет боковое меню.
 * Пункты повторяют боковое меню MurkaGram из 12.2.10.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MurkaConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class MurkaDialogsMenuActivity extends UniversalFragment {

    private static final int ID_RESET = 1;
    private static final int ID_ITEM_BASE = 1000;

    private HashSet<String> enabled;
    private ArrayList<String> order;
    private int orderSectionId = -1;

    @Override
    public View createView(Context context) {
        load();
        View view = super.createView(context);
        // MurkaGram: ui-13 — нативные карточки секций 13.2.2 (MD3-настройки применяет RecyclerListView)
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.listenReorder(this::whenReordered);
        listView.allowReorder(true);
        return view;
    }

    private void load() {
        enabled = MurkaConfig.getDialogsMenuItems(UserConfig.getInstance(currentAccount).showCallsTab);
        order = MurkaConfig.getDialogsMenuOrder();
    }

    private void save() {
        MurkaConfig.setDialogsMenuItems(order, enabled);
    }

    private static int getItemId(String key) {
        // id привязан к ключу (а не к позиции), чтобы не меняться при перестановке
        return ID_ITEM_BASE + Arrays.asList(MurkaConfig.DIALOGS_MENU_ALL).indexOf(key);
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MurkaDialogsMenuTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (enabled == null || order == null) {
            load();
        }
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaDrawerItems)));
        orderSectionId = adapter.reorderSectionStart();
        for (String key : order) {
            UItem item = UItem.asCheck(getItemId(key), getName(key)).setChecked(enabled.contains(key));
            item.object2 = key;
            items.add(item);
        }
        adapter.reorderSectionEnd();
        items.add(UItem.asShadow(LocaleController.getString(R.string.MurkaDialogsMenuInfo)));
        items.add(UItem.asButton(ID_RESET, R.drawable.msg_reset, LocaleController.getString(R.string.MurkaDrawerReset)));
        items.add(UItem.asShadow(null));
    }

    private void whenReordered(int id, ArrayList<UItem> sectionItems) {
        if (id != orderSectionId) {
            return;
        }
        order.clear();
        for (UItem it : sectionItems) {
            if (it.object2 instanceof String) {
                order.add((String) it.object2);
            }
        }
        save();
        // обновить разделители между ячейками после перестановки
        AndroidUtilities.runOnUIThread(this::updateList);
    }

    private void updateList() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_RESET) {
            MurkaConfig.resetDialogsMenuItems();
            load();
            updateList();
        } else if (item.object2 instanceof String) {
            String key = (String) item.object2;
            boolean checked = !enabled.contains(key);
            if (checked) {
                enabled.add(key);
            } else {
                enabled.remove(key);
            }
            save();
            item.setChecked(checked);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(checked);
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false; // долгое нажатие уходит в ItemTouchHelper (перетаскивание)
    }

    private static String getName(String key) {
        switch (key) {
            case "night": return LocaleController.getString(R.string.MurkaDialogsMenuNight);
            case "profile": return LocaleController.getString(R.string.MyProfile);
            case "newgroup": return LocaleController.getString(R.string.NewGroup);
            case "secret": return LocaleController.getString(R.string.NewSecretChat);
            case "channel": return LocaleController.getString(R.string.NewChannel);
            case "contacts": return LocaleController.getString(R.string.Contacts);
            case "calls": return LocaleController.getString(R.string.Calls);
            case "saved": return LocaleController.getString(R.string.SavedMessages);
            case "bots": return LocaleController.getString(R.string.MurkaDialogsMenuBots);
            case "settings": return LocaleController.getString(R.string.Settings);
            case "murka": return LocaleController.getString(R.string.MurkaGramSettings);
            case "invite": return LocaleController.getString(R.string.InviteFriends);
            case "features": return LocaleController.getString(R.string.TelegramFeatures);
            case "ghost": return LocaleController.getString(R.string.MurkaDrawerGhost);
            default: return key;
        }
    }
}
