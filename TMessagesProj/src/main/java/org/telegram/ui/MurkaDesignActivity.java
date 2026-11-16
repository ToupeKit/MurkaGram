/*
 * MurkaGram: экран «Дизайн» — центрирование названия, отключение округления чисел,
 * время с секундами и выбор шрифта.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MurkaConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.MurkaFabShapeCell;
import org.telegram.ui.Components.MurkaMD3;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

public class MurkaDesignActivity extends UniversalFragment {

    private static final int ID_CENTER_TITLE = 1;
    private static final int ID_NO_ROUNDING = 2;
    private static final int ID_TIME_SECONDS = 3;
    private static final int ID_FONT = 4;
    private static final int ID_SPRING = 5; // MurkaGram
    private static final int ID_MD3 = 6; // MurkaGram: «MD3 дизайн»
    private static final int ID_NO_BUBBLE_TAIL = 8; // MurkaGram: «Убрать хвост сообщений»
    private static final int ID_MENU_ICON_BAR = 9; // MurkaGram: «Быстрые действия иконками»
    private static final int ID_HIDE_PHONE = 10; // MurkaGram: «Скрыть номер телефона»
    private static final int ID_EMOJI = 11; // MurkaGram: «Эмодзи»
    private static final int ID_CONFIRM_VOICE = 12; // MurkaGram: «Подтверждение отправки голосовых и кружков»
    private static final int ID_UNLIMITED_PINS = 13; // MurkaGram: «Безлимитное закрепление чатов»
    // MurkaGram: категория «Material Design 3»
    private static final int ID_FAB_SHAPE = 14;
    private static final int ID_DIVIDERS = 15;
    private static final int ID_MD3_CONTAINERS = 16;
    private static final int ID_MD3_HEADERS = 17;
    private static final int ID_MD3_SWITCHES = 18;
    private static final int ID_EDITED_ICON = 19; // MurkaGram: «Заменять «изменено» иконкой»
    private static final int ID_DRAWER = 50; // MurkaGram: «Настройки бокового меню»

    private static final int REQUEST_FONT_FILE = 7701;
    private static final int REQUEST_EMOJI_FILE = 7702; // MurkaGram
    private static final long MAX_FONT_FILE_SIZE = 64L * 1024 * 1024;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MurkaDesign);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaGeneral))); // MurkaGram: заголовок «Общие»
        items.add(UItem.asCheck(ID_CENTER_TITLE, LocaleController.getString(R.string.MurkaCenterTitle)).setChecked(MurkaConfig.centerTitle));
        items.add(UItem.asCheck(ID_NO_ROUNDING, LocaleController.getString(R.string.MurkaNoRounding)).setChecked(MurkaConfig.noNumberRounding));
        items.add(UItem.asCheck(ID_TIME_SECONDS, LocaleController.getString(R.string.MurkaTimeSeconds)).setChecked(MurkaConfig.timeWithSeconds));
        items.add(UItem.asCheck(ID_SPRING, LocaleController.getString(R.string.MurkaSpringAnimation)).setChecked(MurkaConfig.springAnimation));
        items.add(UItem.asCheck(ID_MD3, LocaleController.getString(R.string.MurkaMD3Design)).setChecked(MurkaConfig.md3Design)); // MurkaGram
        items.add(UItem.asButton(ID_FONT, LocaleController.getString(R.string.MurkaFont), getFontModeName(MurkaConfig.fontMode)));
        items.add(UItem.asButton(ID_DRAWER, LocaleController.getString(R.string.MurkaDrawerSettings))); // MurkaGram
        items.add(UItem.asShadow(null));
        // MurkaGram: категория «Material Design 3» (только при включённом MD3 дизайне)
        if (MurkaConfig.md3Design) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaMD3Category)));
            items.add(MurkaFabShapeCell.Factory.as(ID_FAB_SHAPE));
            items.add(UItem.asButton(ID_DIVIDERS, LocaleController.getString(R.string.MurkaMD3Dividers), getDividerModeName(MurkaConfig.md3DividerMode)));
            items.add(UItem.asCheck(ID_MD3_CONTAINERS, LocaleController.getString(R.string.MurkaMD3Containers)).setChecked(MurkaConfig.md3Containers));
            if (MurkaConfig.md3Containers) {
                items.add(UItem.asCheck(ID_MD3_HEADERS, LocaleController.getString(R.string.MurkaMD3SeparateHeaders)).setChecked(MurkaConfig.md3SeparateHeaders));
            }
            items.add(UItem.asCheck(ID_MD3_SWITCHES, LocaleController.getString(R.string.MurkaMD3Switches)).setChecked(MurkaConfig.md3Switches));
            items.add(UItem.asCheck(ID_NO_BUBBLE_TAIL, LocaleController.getString(R.string.MurkaNoBubbleTail)).setChecked(MurkaConfig.noBubbleTail));
            items.add(UItem.asShadow(null));
        }
        // MurkaGram: категория «Чаты»
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaDesignChats)));
        if (!MurkaConfig.md3Design) { // MurkaGram: при MD3 пункт в категории «Material Design 3»
            items.add(UItem.asCheck(ID_NO_BUBBLE_TAIL, LocaleController.getString(R.string.MurkaNoBubbleTail)).setChecked(MurkaConfig.noBubbleTail));
        }
        items.add(UItem.asCheck(ID_MENU_ICON_BAR, LocaleController.getString(R.string.MurkaMessageMenuIconBar)).setChecked(MurkaConfig.messageMenuIconBar)); // MurkaGram
        items.add(UItem.asCheck(ID_EDITED_ICON, LocaleController.getString(R.string.MurkaEditedIcon)).setChecked(MurkaConfig.editedIcon)); // MurkaGram
        items.add(UItem.asButton(ID_EMOJI, LocaleController.getString(R.string.MurkaEmoji), getEmojiModeName(MurkaConfig.emojiMode))); // MurkaGram
        items.add(UItem.asCheck(ID_CONFIRM_VOICE, LocaleController.getString(R.string.MurkaConfirmVoiceSend)).setChecked(MurkaConfig.confirmVoiceSend)); // MurkaGram
        items.add(UItem.asCheck(ID_UNLIMITED_PINS, LocaleController.getString(R.string.MurkaUnlimitedPins)).setChecked(MurkaConfig.unlimitedPins)); // MurkaGram
        items.add(UItem.asShadow(LocaleController.getString(R.string.MurkaUnlimitedPinsInfo))); // MurkaGram
        // MurkaGram: категория «Профиль»
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaDesignProfile)));
        items.add(UItem.asCheck(ID_HIDE_PHONE, LocaleController.getString(R.string.MurkaHidePhone)).setChecked(MurkaConfig.hidePhone));
        items.add(UItem.asShadow(null));
    }

    private static String getFontModeName(int mode) {
        if (mode == MurkaConfig.FONT_SYSTEM) {
            return LocaleController.getString(R.string.MurkaFontSystem);
        } else if (mode == MurkaConfig.FONT_CUSTOM) {
            return LocaleController.getString(R.string.MurkaFontCustom);
        }
        return LocaleController.getString(R.string.MurkaFontDefault);
    }

    // MurkaGram: «Тип разделителей»
    private static String getDividerModeName(int mode) {
        if (mode == MurkaConfig.DIVIDERS_HIDE) {
            return LocaleController.getString(R.string.MurkaMD3DividersHide);
        } else if (mode == MurkaConfig.DIVIDERS_LINES) {
            return LocaleController.getString(R.string.MurkaMD3DividersLines);
        }
        return LocaleController.getString(R.string.MurkaMD3DividersSegments);
    }

    // MurkaGram: «Эмодзи»
    private static String getEmojiModeName(int mode) {
        if (mode == MurkaConfig.EMOJI_SYSTEM) {
            return LocaleController.getString(R.string.MurkaEmojiSystem);
        } else if (mode == MurkaConfig.EMOJI_CUSTOM) {
            return LocaleController.getString(R.string.MurkaEmojiCustom);
        }
        return LocaleController.getString(R.string.MurkaEmojiDefault);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CENTER_TITLE) {
            MurkaConfig.toggleCenterTitle();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.centerTitle);
            }
            if (actionBar != null) { // MurkaGram: сразу передвинуть заголовок этого экрана
                actionBar.requestLayout();
            }
        } else if (item.id == ID_NO_ROUNDING) {
            MurkaConfig.toggleNoNumberRounding();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.noNumberRounding);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        } else if (item.id == ID_TIME_SECONDS) {
            MurkaConfig.toggleTimeWithSeconds();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.timeWithSeconds);
            }
            LocaleController.getInstance().recreateFormatters();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        } else if (item.id == ID_SPRING) {
            MurkaConfig.toggleSpringAnimation();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.springAnimation);
            }
        } else if (item.id == ID_MD3) { // MurkaGram: «MD3 дизайн» применяется после перезапуска
            MurkaConfig.toggleMd3Design();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.md3Design);
            }
            updateList(); // MurkaGram: показать/скрыть категорию «Material Design 3»
            showRestartDialog(LocaleController.getString(R.string.MurkaMD3Restart));
        } else if (item.id == ID_DIVIDERS) { // MurkaGram: «Тип разделителей»
            showDividersDialog();
        } else if (item.id == ID_MD3_CONTAINERS) { // MurkaGram: ширина строк меняется — на уже открытых экранах ячейки могут остаться со старой разметкой, поэтому предлагаем перезапуск
            MurkaConfig.toggleMd3Containers();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.md3Containers);
            }
            applyMd3Change();
            showRestartDialog(LocaleController.getString(R.string.MurkaMD3Restart));
        } else if (item.id == ID_MD3_HEADERS) { // MurkaGram: применяется сразу
            MurkaConfig.toggleMd3SeparateHeaders();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.md3SeparateHeaders);
            }
            applyMd3Change();
        } else if (item.id == ID_MD3_SWITCHES) { // MurkaGram: размеры переключателей задаются при создании ячеек
            MurkaConfig.toggleMd3Switches();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.md3Switches);
            }
            showRestartDialog(LocaleController.getString(R.string.MurkaMD3Restart));
        } else if (item.id == ID_EDITED_ICON) { // MurkaGram: «Заменять «изменено» иконкой»
            MurkaConfig.toggleEditedIcon();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.editedIcon);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        } else if (item.id == ID_NO_BUBBLE_TAIL) { // MurkaGram
            MurkaConfig.toggleNoBubbleTail();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.noBubbleTail);
            }
        } else if (item.id == ID_MENU_ICON_BAR) { // MurkaGram: «Быстрые действия иконками»
            MurkaConfig.toggleMessageMenuIconBar();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.messageMenuIconBar);
            }
        } else if (item.id == ID_CONFIRM_VOICE) { // MurkaGram
            MurkaConfig.toggleConfirmVoiceSend();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.confirmVoiceSend);
            }
        } else if (item.id == ID_UNLIMITED_PINS) { // MurkaGram: пересортировать списки чатов всех аккаунтов
            MurkaConfig.toggleUnlimitedPins();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.unlimitedPins);
            }
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    MessagesController.getInstance(a).sortDialogs(null);
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload);
                }
            }
        } else if (item.id == ID_HIDE_PHONE) { // MurkaGram: «Скрыть номер телефона» — применяется сразу
            MurkaConfig.toggleHidePhone();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.hidePhone);
            }
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_PHONE | MessagesController.UPDATE_MASK_NAME);
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.mainUserInfoChanged);
                }
            }
        } else if (item.id == ID_EMOJI) { // MurkaGram
            showEmojiDialog();
        } else if (item.id == ID_FONT) {
            showFontDialog();
        } else if (item.id == ID_DRAWER) { // MurkaGram
            presentFragment(new MurkaDrawerSettingsActivity());
        }
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

    // MurkaGram: пересчитать карточки на этом экране и (при следующей отрисовке) на остальных
    private void applyMd3Change() {
        MurkaMD3.bump();
        updateList();
        if (listView != null) {
            if (!listView.isComputingLayout()) {
                listView.invalidateItemDecorations();
            }
            listView.invalidate();
        }
    }

    // MurkaGram: «Тип разделителей»
    private void showDividersDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final String[] options = new String[]{
            LocaleController.getString(R.string.MurkaMD3DividersHide),
            LocaleController.getString(R.string.MurkaMD3DividersLines),
            LocaleController.getString(R.string.MurkaMD3DividersSegments)
        };
        showDialog(AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, LocaleController.getString(R.string.MurkaMD3Dividers), MurkaConfig.md3DividerMode, (dialog, which) -> {
            if (which == MurkaConfig.md3DividerMode) {
                return;
            }
            MurkaConfig.setMd3DividerMode(which);
            applyMd3Change();
        }));
    }

    private void showFontDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final String[] options = new String[]{
            LocaleController.getString(R.string.MurkaFontDefault),
            LocaleController.getString(R.string.MurkaFontSystem),
            LocaleController.getString(R.string.MurkaFontCustom)
        };
        showDialog(AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, LocaleController.getString(R.string.MurkaFont), MurkaConfig.fontMode, (dialog, which) -> {
            if (which == MurkaConfig.FONT_CUSTOM) {
                openFontPicker(REQUEST_FONT_FILE);
                return;
            }
            if (which == MurkaConfig.fontMode) {
                return;
            }
            MurkaConfig.setFontMode(which, MurkaConfig.customFontPath);
            updateList();
            showRestartDialog();
        }));
    }

    // MurkaGram: «Эмодзи» — выбор стандартных / системных / своего шрифта эмодзи (после перезапуска).
    private void showEmojiDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final String[] options = new String[]{
            LocaleController.getString(R.string.MurkaEmojiDefault),
            LocaleController.getString(R.string.MurkaEmojiSystem),
            LocaleController.getString(R.string.MurkaEmojiCustom)
        };
        showDialog(AlertsCreator.createSingleChoiceDialog(getParentActivity(), options, LocaleController.getString(R.string.MurkaEmoji), MurkaConfig.emojiMode, (dialog, which) -> {
            if (which == MurkaConfig.EMOJI_CUSTOM) {
                openFontPicker(REQUEST_EMOJI_FILE);
                return;
            }
            if (which == MurkaConfig.emojiMode) {
                return;
            }
            MurkaConfig.setEmojiMode(which, MurkaConfig.customEmojiPath);
            updateList();
            showRestartDialog(LocaleController.getString(R.string.MurkaEmojiRestart));
        }));
    }

    private void openFontPicker(int requestCode) { // MurkaGram: requestCode — шрифт или эмодзи
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt", "application/octet-stream"});
            startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            FileLog.e(e);
            showFontError();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_FONT_FILE && requestCode != REQUEST_EMOJI_FILE) { // MurkaGram
            return;
        }
        final boolean emoji = requestCode == REQUEST_EMOJI_FILE;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            final String path = importFont(uri, emoji ? "murka_emoji" : "murka_font"); // MurkaGram
            AndroidUtilities.runOnUIThread(() -> {
                if (path == null) {
                    showFontError();
                    return;
                }
                if (emoji) { // MurkaGram
                    MurkaConfig.setEmojiMode(MurkaConfig.EMOJI_CUSTOM, path);
                    updateList();
                    showRestartDialog(LocaleController.getString(R.string.MurkaEmojiRestart));
                    return;
                }
                MurkaConfig.setFontMode(MurkaConfig.FONT_CUSTOM, path);
                updateList();
                showRestartDialog();
            });
        });
    }

    // Копирует выбранный файл шрифта во внутреннее хранилище и проверяет его. Возвращает путь или null при ошибке.
    private static String importFont(Uri uri, String baseName) { // MurkaGram: baseName — имя файла без расширения
        File tmp = null;
        try {
            final ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
            String name = null;
            try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    name = cursor.getString(0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (name == null) {
                name = uri.getLastPathSegment();
            }
            if (name == null) {
                return null;
            }
            name = name.toLowerCase(Locale.ROOT);
            final String ext;
            if (name.endsWith(".ttf")) {
                ext = ".ttf";
            } else if (name.endsWith(".otf")) {
                ext = ".otf";
            } else {
                return null;
            }

            final File dir = ApplicationLoader.getFilesDirFixed();
            tmp = new File(dir, baseName + "_tmp" + ext);
            if (tmp.exists()) {
                tmp.delete();
            }
            try (InputStream in = resolver.openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) {
                    return null;
                }
                final byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_FONT_FILE_SIZE) {
                        return null;
                    }
                    out.write(buffer, 0, read);
                }
            }
            if (!isFontSignature(tmp)) {
                return null;
            }
            final Typeface typeface = Typeface.createFromFile(tmp);
            if (typeface == null || typeface == Typeface.DEFAULT) {
                return null;
            }

            final File ttf = new File(dir, baseName + ".ttf");
            final File otf = new File(dir, baseName + ".otf");
            if (ttf.exists()) {
                ttf.delete();
            }
            if (otf.exists()) {
                otf.delete();
            }
            final File target = ".otf".equals(ext) ? otf : ttf;
            if (!tmp.renameTo(target)) {
                return null;
            }
            tmp = null;
            return target.getAbsolutePath();
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            if (tmp != null && tmp.exists()) {
                try {
                    tmp.delete();
                } catch (Throwable ignore) {

                }
            }
        }
    }

    // Проверка сигнатуры TrueType/OpenType (0x00010000, "OTTO", "true", "ttcf").
    private static boolean isFontSignature(File file) {
        try (InputStream in = new FileInputStream(file)) {
            final byte[] header = new byte[4];
            if (in.read(header) != 4) {
                return false;
            }
            if (header[0] == 0 && header[1] == 1 && header[2] == 0 && header[3] == 0) {
                return true;
            }
            final String tag = new String(header, "ISO-8859-1");
            return "OTTO".equals(tag) || "true".equals(tag) || "ttcf".equals(tag);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    private void showFontError() {
        if (getParentActivity() == null || isFinished) {
            return;
        }
        BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.MurkaFontError)).show();
    }

    private void showRestartDialog() {
        showRestartDialog(LocaleController.getString(R.string.MurkaFontRestart)); // MurkaGram
    }

    // MurkaGram: общий диалог перезапуска с произвольным текстом.
    private void showRestartDialog(CharSequence message) {
        if (getParentActivity() == null || isFinished) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setMessage(message);
        builder.setPositiveButton(LocaleController.getString(R.string.MurkaRestart), (dialog, which) -> restartApp());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void restartApp() {
        try {
            AndroidUtilities.murkaResetTypefaces();
            final Context context = ApplicationLoader.applicationContext;
            final Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                final Activity activity = getParentActivity();
                if (activity != null) {
                    activity.startActivity(intent);
                } else {
                    context.startActivity(intent);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
