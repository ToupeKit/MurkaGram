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
import org.telegram.messenger.MurkaConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
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
    private static final int ID_DRAWER = 50; // MurkaGram: «Настройки бокового меню»

    private static final int REQUEST_FONT_FILE = 7701;
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
        // MurkaGram: категория «Чаты»
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaDesignChats)));
        items.add(UItem.asCheck(ID_NO_BUBBLE_TAIL, LocaleController.getString(R.string.MurkaNoBubbleTail)).setChecked(MurkaConfig.noBubbleTail));
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

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CENTER_TITLE) {
            MurkaConfig.toggleCenterTitle();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.centerTitle);
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
            showRestartDialog(LocaleController.getString(R.string.MurkaMD3Restart));
        } else if (item.id == ID_NO_BUBBLE_TAIL) { // MurkaGram
            MurkaConfig.toggleNoBubbleTail();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MurkaConfig.noBubbleTail);
            }
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
                openFontPicker();
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

    private void openFontPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt", "application/octet-stream"});
            startActivityForResult(intent, REQUEST_FONT_FILE);
        } catch (Exception e) {
            FileLog.e(e);
            showFontError();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_FONT_FILE) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            final String path = importFont(uri);
            AndroidUtilities.runOnUIThread(() -> {
                if (path == null) {
                    showFontError();
                    return;
                }
                MurkaConfig.setFontMode(MurkaConfig.FONT_CUSTOM, path);
                updateList();
                showRestartDialog();
            });
        });
    }

    // Копирует выбранный файл шрифта во внутреннее хранилище и проверяет его. Возвращает путь или null при ошибке.
    private static String importFont(Uri uri) {
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
            tmp = new File(dir, "murka_font_tmp" + ext);
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

            final File ttf = new File(dir, "murka_font.ttf");
            final File otf = new File(dir, "murka_font.otf");
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
