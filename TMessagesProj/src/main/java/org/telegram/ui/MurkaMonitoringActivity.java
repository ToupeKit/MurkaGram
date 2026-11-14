/*
 * MurkaGram: экран «Мониторинг» — режим призрака, сохранение удалённых сообщений,
 * сохранение изменений, сохранение даты прочтения, прозрачность удалённых сообщений.
 */

package org.telegram.ui;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MurkaConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MurkaMonitoringActivity extends UniversalFragment {

    private static final int ID_GHOST_MODE = 1;
    private static final int ID_SAVE_DELETED = 2;
    private static final int ID_SAVE_EDITS = 3;
    private static final int ID_SAVE_READ_DATES = 4;
    private static final int ID_DELETED_TRANSPARENCY = 5;
    private static final int ID_EXCEPTIONS = 6; // MurkaGram: «Исключения»
    private static final int ID_GHOST_WARNING = 7; // MurkaGram: «Предупреждение призрака»

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MurkaMonitoring);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaGeneral))); // MurkaGram: заголовок «Общие»
        items.add(UItem.asCheck(ID_GHOST_MODE, LocaleController.getString(R.string.MurkaGhostMode)).setChecked(MurkaConfig.ghostMode));
        items.add(UItem.asCheck(ID_SAVE_DELETED, LocaleController.getString(R.string.MurkaSaveDeleted)).setChecked(MurkaConfig.saveDeletedMessages));
        items.add(UItem.asCheck(ID_SAVE_EDITS, LocaleController.getString(R.string.MurkaSaveEdits)).setChecked(MurkaConfig.saveEditHistory));
        items.add(UItem.asCheck(ID_SAVE_READ_DATES, LocaleController.getString(R.string.MurkaSaveReadDates)).setChecked(MurkaConfig.saveReadDates));
        int exceptionsCount = MurkaConfig.getMonitorExceptions(currentAccount).size(); // MurkaGram: «Исключения»
        items.add(UItem.asButton(ID_EXCEPTIONS, LocaleController.getString(R.string.MurkaExceptions), exceptionsCount > 0 ? String.valueOf(exceptionsCount) : ""));
        items.add(UItem.asButton(ID_DELETED_TRANSPARENCY, LocaleController.getString(R.string.MurkaDeletedTransparency), MurkaConfig.deletedMessageTransparency + "%"));
        items.add(UItem.asShadow(null));
        // MurkaGram: «Предупреждение призрака» — отдельный блок с пояснением
        items.add(UItem.asCheck(ID_GHOST_WARNING, LocaleController.getString(R.string.MurkaGhostWarning)).setChecked(MurkaConfig.ghostWarning));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MurkaGhostWarningInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_GHOST_MODE) {
            MurkaConfig.toggleGhostModeAndApply(); // MurkaGram: заодно обновляет подпись в боковом меню
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.ghostMode);
            }
        } else if (item.id == ID_SAVE_DELETED) {
            MurkaConfig.toggleSaveDeletedMessages();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.saveDeletedMessages);
            }
        } else if (item.id == ID_SAVE_EDITS) {
            MurkaConfig.toggleSaveEditHistory();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.saveEditHistory);
            }
        } else if (item.id == ID_SAVE_READ_DATES) {
            MurkaConfig.toggleSaveReadDates();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.saveReadDates);
            }
        } else if (item.id == ID_DELETED_TRANSPARENCY) {
            showTransparencyDialog();
        } else if (item.id == ID_EXCEPTIONS) { // MurkaGram
            presentFragment(new MurkaExceptionsActivity());
        } else if (item.id == ID_GHOST_WARNING) { // MurkaGram
            MurkaConfig.toggleGhostWarning();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.ghostWarning);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateList(); // MurkaGram: обновить счётчик «Исключений» после возврата
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void updateList() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void showTransparencyDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MurkaDeletedTransparency));
        builder.setMessage(LocaleController.getString(R.string.MurkaDeletedTransparencyHint));

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(String.valueOf(MurkaConfig.deletedMessageTransparency));
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setGravity(Gravity.CENTER);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String str = s.toString();
                    if (str.length() != 0) {
                        int value = Utilities.parseInt(str);
                        if (value > 100) {
                            editText.setText("100");
                            editText.setSelection(editText.length());
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
        builder.setView(editText);

        final Runnable apply = () -> {
            String str = editText.getText().toString();
            if (str.length() == 0) {
                return;
            }
            try {
                MurkaConfig.setDeletedMessageTransparency(Utilities.parseInt(str));
                updateList();
            } catch (Exception e) {
                FileLog.e(e);
            }
        };

        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> apply.run());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        editText.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                apply.run();
                AndroidUtilities.hideKeyboard(textView);
                return true;
            }
            return false;
        });
        showDialog(builder.create());
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
        if (layoutParams != null) {
            if (layoutParams instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
            }
            layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.height = AndroidUtilities.dp(36);
            editText.setLayoutParams(layoutParams);
        }
        editText.setSelection(editText.getText().length());
    }
}
