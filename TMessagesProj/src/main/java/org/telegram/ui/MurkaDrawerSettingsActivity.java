/*
 * MurkaGram: экран «Боковое меню» — порядок (перетаскивание долгим нажатием), удаление и добавление пунктов бокового меню.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MurkaConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MurkaDrawerSettingsActivity extends UniversalFragment {

    private static final int ID_ADD = 1;
    private static final int ID_RESET = 2;
    private static final int ID_ITEM_BASE = 1000;

    // MurkaGram: + "status" (эмодзи-статус)
    private static final String[] ALL_KEYS = {"profile", "status", "newgroup", "secret", "channel", "contacts", "calls", "saved", "settings", "invite", "features", "ghost", "div"};

    private final ArrayList<String> keys = MurkaConfig.getDrawerItems();
    private int orderSectionId = -1;
    private int firstItemPosition;

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        // MurkaGram: ui-13 — нативные карточки секций 13.2.2 (MD3-настройки применяет RecyclerListView)
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.listenReorder(this::whenReordered);
        listView.allowReorder(true);
        return view;
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MurkaDrawerTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaDrawerItems)));
        firstItemPosition = items.size();
        if (!keys.isEmpty()) {
            orderSectionId = adapter.reorderSectionStart();
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                // ключ храним в object2: String в object для TextCell означает путь к стикеру
                UItem item = UItem.asButton(ID_ITEM_BASE + i, getIcon(key), getName(key));
                item.object2 = key;
                items.add(item);
            }
            adapter.reorderSectionEnd();
        } else {
            orderSectionId = -1;
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_ADD, R.drawable.msg_add, LocaleController.getString(R.string.MurkaDrawerAdd)).accent());
        items.add(UItem.asButton(ID_RESET, R.drawable.msg_reset, LocaleController.getString(R.string.MurkaDrawerReset)));
        items.add(UItem.asShadow(null));
    }

    private void whenReordered(int id, ArrayList<UItem> sectionItems) {
        if (id != orderSectionId) {
            return;
        }
        keys.clear();
        for (UItem it : sectionItems) {
            if (it.object2 instanceof String) {
                keys.add((String) it.object2);
            }
        }
        save();
        // пересчитать id пунктов (ID_ITEM_BASE + индекс) после перестановки
        AndroidUtilities.runOnUIThread(this::updateList);
    }

    private void save() {
        MurkaConfig.setDrawerItems(keys);
    }

    private void updateList() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            showAddDialog();
        } else if (item.id == ID_RESET) {
            MurkaConfig.resetDrawerItems();
            keys.clear();
            keys.addAll(MurkaConfig.getDrawerItems());
            updateList();
        } else if (item.object2 instanceof String) {
            if (getParentActivity() == null) {
                return;
            }
            final String key = (String) item.object2;
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(getName(key));
            builder.setMessage(LocaleController.getString(R.string.MurkaDrawerRemoveConfirm));
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                int index = position - firstItemPosition;
                if (index < 0 || index >= keys.size() || !key.equals(keys.get(index))) {
                    index = keys.indexOf(key);
                }
                if (index >= 0) {
                    keys.remove(index);
                    save();
                    updateList();
                }
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            showDialog(dialog);
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        }
    }

    private void showAddDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final ArrayList<String> available = new ArrayList<>();
        for (String k : ALL_KEYS) {
            if (k.equals("div") || !keys.contains(k)) {
                available.add(k);
            }
        }
        CharSequence[] names = new CharSequence[available.size()];
        int[] icons = new int[available.size()];
        for (int i = 0; i < available.size(); i++) {
            names[i] = getName(available.get(i));
            icons[i] = getIcon(available.get(i));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MurkaDrawerAdd));
        builder.setItems(names, icons, (dialog, which) -> {
            keys.add(available.get(which));
            save();
            updateList();
        });
        showDialog(builder.create());
    }

    private static int getIcon(String key) {
        switch (key) {
            case "profile": return R.drawable.left_status_profile;
            case "status": return R.drawable.msg_status_set; // MurkaGram
            case "newgroup": return R.drawable.msg_groups;
            case "secret": return R.drawable.msg_secret;
            case "channel": return R.drawable.msg_channel;
            case "contacts": return R.drawable.msg_contacts;
            case "calls": return R.drawable.msg_calls;
            case "saved": return R.drawable.msg_saved;
            case "settings": return R.drawable.msg_settings_old;
            case "invite": return R.drawable.msg_invite;
            case "features": return R.drawable.msg_help;
            case "ghost": return R.drawable.msg_murka_ghost;
            default: return 0;
        }
    }

    private static String getName(String key) {
        switch (key) {
            case "profile": return LocaleController.getString(R.string.MyProfile);
            case "status": return LocaleController.getString(R.string.MurkaDrawerStatus) + " (Premium)"; // MurkaGram
            case "newgroup": return LocaleController.getString(R.string.NewGroup);
            case "secret": return LocaleController.getString(R.string.NewSecretChat);
            case "channel": return LocaleController.getString(R.string.NewChannel);
            case "contacts": return LocaleController.getString(R.string.Contacts);
            case "calls": return LocaleController.getString(R.string.Calls);
            case "saved": return LocaleController.getString(R.string.SavedMessages);
            case "settings": return LocaleController.getString(R.string.Settings);
            case "invite": return LocaleController.getString(R.string.InviteFriends);
            case "features": return LocaleController.getString(R.string.TelegramFeatures);
            case "ghost": return LocaleController.getString(R.string.MurkaDrawerGhost);
            case "div": return LocaleController.getString(R.string.MurkaDrawerDivider);
            default: return key;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false; // долгое нажатие уходит в ItemTouchHelper (перетаскивание)
    }
}
