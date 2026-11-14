/*
 * MurkaGram: экран настроек клиента MurkaGram.
 * Открывается из главного экрана настроек (ProfileActivity, пункт «MurkaGram настройки»).
 * Сюда будут добавляться собственные настройки MurkaGram (в fillItems).
 */

package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MurkaGramSettingsActivity extends UniversalFragment {

    private static final int ID_MONITORING = 1;
    private static final int ID_DESIGN = 2;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MurkaGramSettings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_MONITORING, R.drawable.msg_log, LocaleController.getString(R.string.MurkaMonitoring)));
        items.add(UItem.asButton(ID_DESIGN, R.drawable.msg_theme, LocaleController.getString(R.string.MurkaDesign)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MONITORING) {
            presentFragment(new MurkaMonitoringActivity());
        } else if (item.id == ID_DESIGN) {
            presentFragment(new MurkaDesignActivity());
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
