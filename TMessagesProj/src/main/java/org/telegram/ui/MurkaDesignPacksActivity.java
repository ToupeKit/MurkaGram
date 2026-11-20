/*
 * MurkaGram: экран «Пакеты дизайна» — импорт/экспорт .mgdesign, возврат предыдущего дизайна,
 * список установленных пакетов и окно предпросмотра пакета (также открывается из чата).
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MurkaConfig;
import org.telegram.messenger.MurkaDesignPack;
import org.telegram.messenger.MurkaEdition;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MurkaDesignPacksActivity extends UniversalFragment {

    private static final int ID_IMPORT = 1;
    private static final int ID_EXPORT = 2;
    private static final int ID_RESTORE = 3;
    private static final int ID_PACK_BASE = 1000;

    private static final int REQUEST_IMPORT = 7704;
    private static final int REQUEST_EXPORT = 7705;

    private final ArrayList<MurkaDesignPack> packs = new ArrayList<>();
    private boolean hasBackup;
    private byte[] pendingExport;
    private boolean pendingExportCustomSkipped;

    @Override
    public boolean onFragmentCreate() {
        loadPacks();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPacks();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MurkaDesignPacks);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_IMPORT, R.drawable.msg_add, LocaleController.getString(R.string.MurkaDesignPackImport)));
        items.add(UItem.asButton(ID_EXPORT, R.drawable.msg_share, LocaleController.getString(R.string.MurkaDesignPackExport)));
        items.add(UItem.asButton(ID_RESTORE, R.drawable.msg_reset, LocaleController.getString(R.string.MurkaDesignPackRestore)).setEnabled(hasBackup));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MurkaDesignPacksInfo)));
        if (packs.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.MurkaDesignPackEmpty)));
            return;
        }
        items.add(UItem.asHeader(LocaleController.getString(R.string.MurkaDesignPackInstalled)));
        for (int i = 0; i < packs.size(); i++) {
            final MurkaDesignPack pack = packs.get(i);
            final UItem item = UItem.asButton(ID_PACK_BASE + i, pack.verified ? R.drawable.msg_murka_badge_cat : R.drawable.msg_theme, pack.getDisplayName(), pack.author); // MurkaGram: значок проверенного пакета
            item.object2 = pack;
            items.add(item);
        }
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_IMPORT) {
            openImportPicker();
        } else if (item.id == ID_EXPORT) {
            showExportDialog();
        } else if (item.id == ID_RESTORE) {
            if (!hasBackup) {
                return;
            }
            hasBackup = false;
            updateList();
            final boolean restart = MurkaDesignPack.restoreBackup(this);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MurkaDesignPackRestored)).show();
            if (restart) {
                showRestartDialog(this);
            }
        } else if (item.id >= ID_PACK_BASE && item.object2 instanceof MurkaDesignPack) {
            openInstalled((MurkaDesignPack) item.object2);
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

    private void loadPacks() {
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<MurkaDesignPack> list = MurkaDesignPack.list();
            final boolean backup = MurkaDesignPack.hasBackup();
            AndroidUtilities.runOnUIThread(() -> {
                packs.clear();
                packs.addAll(list);
                hasBackup = backup;
                updateList();
            });
        });
    }

    private void showError(int error) {
        if (getParentActivity() == null || isFinished) {
            return;
        }
        BulletinFactory.of(this).createErrorBulletin(MurkaDesignPack.getErrorText(error)).show();
    }

    // Установленный пакет: перечитать с превью (файл мог быть подменён — всё равно проверяется) и показать окно.
    private void openInstalled(MurkaDesignPack listed) {
        final File file = listed.file;
        final String id = listed.id;
        Utilities.globalQueue.postRunnable(() -> {
            MurkaDesignPack pack = null;
            int error = MurkaDesignPack.ERROR_INVALID;
            try {
                pack = MurkaDesignPack.read(file, true);
                pack.file = file;
                pack.id = id;
                pack.loadInstalledHash(); // MurkaGram: бейджик по хэшу исходного файла
            } catch (MurkaDesignPack.PackException e) {
                error = e.error;
            }
            final MurkaDesignPack result = pack;
            final int finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (result == null) {
                    showError(finalError);
                    loadPacks();
                    return;
                }
                showPreview(this, result, true, this::loadPacks);
            });
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Импорт

    private void openImportPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); // у .mgdesign нет своего MIME-типа
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (Exception e) {
            FileLog.e(e);
            showError(MurkaDesignPack.ERROR_INVALID);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMPORT) {
            if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
                return;
            }
            final Uri uri = data.getData();
            Utilities.globalQueue.postRunnable(() -> {
                MurkaDesignPack pack = null;
                int error = MurkaDesignPack.ERROR_INVALID;
                File tmp = null;
                try {
                    tmp = MurkaDesignPack.copyFromUri(uri);
                    pack = MurkaDesignPack.read(tmp, true);
                    if (pack.isEdition()) {
                        // MurkaGram: пакет-редакция не копируется в установленные — лежит один в приватном каталоге до установки
                        // (уникальное имя: повторный импорт не подменит файл, который сейчас проверяется)
                        final File importFile = MurkaEdition.createImportFile();
                        if (importFile == null) {
                            pack = null;
                        } else {
                            if (tmp.renameTo(importFile)) {
                                tmp = null;
                                pack.file = importFile;
                            } else {
                                MurkaEdition.deleteQuietly(importFile);
                                pack = null;
                            }
                        }
                    } else if (!MurkaDesignPack.install(pack)) {
                        pack = null;
                    }
                } catch (MurkaDesignPack.PackException e) {
                    error = e.error;
                    pack = null;
                } finally {
                    if (tmp != null) {
                        try {
                            tmp.delete();
                        } catch (Throwable ignore) {

                        }
                    }
                }
                final MurkaDesignPack result = pack;
                final int finalError = error;
                AndroidUtilities.runOnUIThread(() -> {
                    loadPacks();
                    if (result == null) {
                        showError(finalError);
                        return;
                    }
                    if (getParentActivity() != null && !isFinished) {
                        showPreview(this, result, true, this::loadPacks);
                    }
                });
            });
        } else if (requestCode == REQUEST_EXPORT) {
            final byte[] bytes = pendingExport;
            final boolean customSkipped = pendingExportCustomSkipped;
            pendingExport = null;
            if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
                return;
            }
            if (bytes == null) {
                showExportError();
                return;
            }
            final Uri uri = data.getData();
            Utilities.globalQueue.postRunnable(() -> {
                boolean ok = false;
                try (OutputStream out = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(uri)) {
                    if (out != null) {
                        out.write(bytes);
                        ok = true;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                final boolean success = ok;
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() == null || isFinished) {
                        return;
                    }
                    if (!success) {
                        showExportError();
                        return;
                    }
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.done, LocaleController.getString(customSkipped ? R.string.MurkaDesignPackSavedNoCustom : R.string.MurkaDesignPackSaved)).show();
                });
            });
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Экспорт

    private void showExportError() {
        if (getParentActivity() == null || isFinished) {
            return;
        }
        BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.MurkaDesignPackExportError)).show();
    }

    private void showExportDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MurkaDesignPackExport));
        builder.setItems(new CharSequence[]{
            LocaleController.getString(R.string.MurkaDesignPackSaveFile),
            LocaleController.getString(R.string.MurkaDesignPackShare)
        }, new int[]{R.drawable.msg_download, R.drawable.msg_share}, (dialog, which) -> exportCurrent(which == 1));
        showDialog(builder.create());
    }

    private void exportCurrent(boolean share) {
        final MurkaDesignPack pack = MurkaDesignPack.exportCurrent(); // UI-поток: снимок текущих цветов
        final boolean customSkipped = MurkaConfig.fontMode == MurkaConfig.FONT_CUSTOM || MurkaConfig.emojiMode == MurkaConfig.EMOJI_CUSTOM;
        String baseName = FileLoader.fixFileName(pack.getDisplayName());
        if (TextUtils.isEmpty(baseName)) {
            baseName = "design";
        }
        final String fileName = baseName + MurkaDesignPack.EXTENSION;
        Utilities.globalQueue.postRunnable(() -> {
            byte[] bytes = null;
            try {
                bytes = pack.toBytes();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            Uri shareUri = null;
            if (bytes != null && share) {
                // MurkaGram: кэш медиа может оказаться внутренним (нет внешнего хранилища) — он не покрыт provider_paths.xml,
                // тогда пишем в files/cache (files-path "cache").
                shareUri = writeShareFile(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName, bytes);
                if (shareUri == null) {
                    shareUri = writeShareFile(ApplicationLoader.getFilesDirFixed("cache"), fileName, bytes);
                }
            }
            final byte[] result = bytes;
            final Uri finalShareUri = shareUri;
            AndroidUtilities.runOnUIThread(() -> {
                if (getParentActivity() == null || isFinished) {
                    return;
                }
                if (result == null) {
                    showExportError();
                    return;
                }
                if (share) {
                    shareFile(finalShareUri);
                } else {
                    try {
                        pendingExport = result;
                        pendingExportCustomSkipped = customSkipped;
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/octet-stream");
                        intent.putExtra(Intent.EXTRA_TITLE, fileName);
                        startActivityForResult(intent, REQUEST_EXPORT);
                    } catch (Throwable e) {
                        FileLog.e(e);
                        pendingExport = null;
                        showExportError();
                    }
                }
            });
        });
    }

    // Записывает файл и выдаёт content:// ссылку FileProvider; null — каталог недоступен или не покрыт provider_paths.xml.
    private static Uri writeShareFile(File dir, String fileName, byte[] bytes) {
        if (dir == null) {
            return null;
        }
        File file = null;
        try {
            file = new File(dir, fileName);
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            return FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", file);
        } catch (Throwable e) {
            FileLog.e(e);
            if (file != null) {
                try {
                    file.delete();
                } catch (Throwable ignore) {

                }
            }
            return null;
        }
    }

    // Нет ссылки на файл — показываем ошибку, без падения.
    private void shareFile(Uri uri) {
        if (uri == null) {
            showExportError();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)), 500);
        } catch (Throwable e) {
            FileLog.e(e);
            showExportError();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Окно предпросмотра пакета (общее для списка и чата)

    // Открыть пакет из файла (документ в чате). Ошибка — понятный тост, без падения.
    public static void openFile(BaseFragment fragment, File file) {
        if (fragment == null || file == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            MurkaDesignPack pack = null;
            int error = MurkaDesignPack.ERROR_INVALID;
            try {
                pack = MurkaDesignPack.read(file, true);
                if (pack.isEdition()) {
                    pack.file = file; // MurkaGram: сборка распаковывается из этого файла при установке
                }
            } catch (MurkaDesignPack.PackException e) {
                error = e.error;
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final MurkaDesignPack result = pack;
            final int finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (result == null) {
                    Toast.makeText(ApplicationLoader.applicationContext, MurkaDesignPack.getErrorText(finalError), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (fragment.getParentActivity() == null || fragment.isFinished) {
                    return;
                }
                showPreview(fragment, result, false, null);
            });
        });
    }

    // installed = true: кнопки «Применить» / «Удалить»; false (из чата): «Применить» / «Установить».
    public static void showPreview(BaseFragment fragment, MurkaDesignPack pack, boolean installed, Runnable onChanged) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (pack.isEdition()) {
            showEditionPreview(fragment, pack); // MurkaGram: пакет-редакция — своё окно
            return;
        }
        final LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        if (pack.preview != null && !pack.preview.isRecycled()) {
            final ImageView imageView = new ImageView(activity);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);
            imageView.setImageBitmap(pack.preview);
            layout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 200, 24, 0, 24, 12));
        }
        final StringBuilder text = new StringBuilder();
        if (!TextUtils.isEmpty(pack.author)) {
            text.append(LocaleController.formatString(R.string.MurkaDesignPackAuthor, pack.author));
        }
        if (!TextUtils.isEmpty(pack.version)) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(LocaleController.formatString(R.string.MurkaDesignPackVersion, pack.version));
        }
        if (!TextUtils.isEmpty(pack.description)) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(pack.description);
        }
        if (text.length() > 0) {
            text.append("\n\n");
        }
        text.append(LocaleController.getString(R.string.MurkaDesignPackContains));
        if (!pack.design.isEmpty()) {
            text.append("\n• ").append(LocaleController.formatString(R.string.MurkaDesignPackSettingsCount, pack.design.size()));
        }
        if (pack.themeText != null) {
            text.append("\n• ").append(LocaleController.formatString(R.string.MurkaDesignPackThemeColors, pack.themeKeysCount));
        }
        text.append("\n\n").append(LocaleController.getString(R.string.MurkaDesignPackBackupNote));

        if (pack.verified) {
            addVerifiedRow(activity, layout); // MurkaGram: бейджик «Проверенный пакет MurkaGram»
        }
        final TextView textView = new TextView(activity);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setText(text.toString()); // обычный текст: без ссылок и разметки
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 4));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(pack.getDisplayName());
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.MurkaDesignPackApply), (dialog, which) -> {
            applyPack(fragment, pack, !installed);
            if (onChanged != null) {
                onChanged.run();
            }
        });
        if (installed) {
            builder.setNegativeButton(LocaleController.getString(R.string.MurkaDesignPackDelete), (dialog, which) -> confirmDelete(fragment, pack, onChanged));
            builder.setNeutralButton(LocaleController.getString(R.string.Cancel), null);
        } else {
            builder.setNeutralButton(LocaleController.getString(R.string.MurkaDesignPackInstall), (dialog, which) -> installPack(fragment, pack, onChanged));
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        }
        final AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        if (installed) {
            final View button = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (button instanceof TextView) {
                ThemeBind.textColor((TextView) button, Theme.key_text_RedBold);
            }
        }
    }

    // MurkaGram: строка «Проверенный пакет MurkaGram» со значком
    private static void addVerifiedRow(Activity activity, LinearLayout layout) {
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.msg_murka_badge_cat);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(icon, LayoutHelper.createLinear(22, 22, 0, 0, 8, 0));
        final TextView label = new TextView(activity);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        label.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        label.setText(LocaleController.getString(R.string.MurkaDesignPackVerified));
        row.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));
    }

    // MurkaGram: окно пакета-редакции. «Применить» — только для проверенного пакета другой редакции.
    private static void showEditionPreview(BaseFragment fragment, MurkaDesignPack pack) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        if (pack.preview != null && !pack.preview.isRecycled()) {
            final ImageView imageView = new ImageView(activity);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);
            imageView.setImageBitmap(pack.preview);
            layout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 200, 24, 0, 24, 12));
        }
        final String editionName = MurkaEdition.getEditionName(pack.edition);
        final boolean current = MurkaEdition.isCurrent(pack.edition);
        // MurkaGram: dual UI, with both editions in this app an edition pack just switches the interface (no APK)
        final boolean dualUi = org.telegram.messenger.UiEdition.classicAvailable();
        final boolean canApply = (dualUi || pack.verified && pack.file != null) && !current;
        if (pack.verified) {
            addVerifiedRow(activity, layout);
        }
        final StringBuilder text = new StringBuilder();
        text.append(LocaleController.formatString(R.string.MurkaEditionInfo, editionName));
        if (!TextUtils.isEmpty(pack.author)) {
            text.append('\n').append(LocaleController.formatString(R.string.MurkaDesignPackAuthor, pack.author));
        }
        if (!TextUtils.isEmpty(pack.version)) {
            text.append('\n').append(LocaleController.formatString(R.string.MurkaDesignPackVersion, pack.version));
        }
        if (!TextUtils.isEmpty(pack.description)) {
            text.append("\n\n").append(pack.description);
        }
        final TextView textView = new TextView(activity);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setText(text.toString()); // обычный текст: без ссылок и разметки
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 4));
        if (!canApply) {
            final TextView warning = new TextView(activity);
            warning.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            warning.setTextColor(Theme.getColor(pack.verified ? Theme.key_dialogTextGray2 : Theme.key_text_RedBold));
            warning.setText(LocaleController.getString(!pack.verified ? R.string.MurkaEditionUnverified : current ? R.string.MurkaEditionSame : R.string.MurkaDesignPackInvalid));
            layout.addView(warning, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 10, 24, 4));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(pack.getDisplayName());
        builder.setView(layout);
        if (canApply) {
            builder.setPositiveButton(LocaleController.getString(R.string.MurkaDesignPackApply), (dialog, which) -> {
                if (dualUi) { // MurkaGram: dual UI
                    org.telegram.messenger.UiEdition.setForNextLaunch(pack.edition);
                    showRestartDialog(fragment);
                } else {
                    confirmEdition(fragment, pack.file, editionName);
                }
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        }
        fragment.showDialog(builder.create());
    }

    private static void confirmEdition(BaseFragment fragment, File packFile, String editionName) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(LocaleController.getString(R.string.MurkaEditionInstallTitle));
        builder.setMessage(LocaleController.formatString(R.string.MurkaEditionInstallConfirm, editionName));
        builder.setPositiveButton(LocaleController.getString(R.string.MurkaDesignPackInstall), (dialog, which) -> installEdition(fragment, packFile));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    // Проверка (хэш, подпись, версия, редакция) и распаковка в фоне, затем системный установщик (как для APK из чата).
    private static void installEdition(BaseFragment fragment, File packFile) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        try {
            progress.show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        Utilities.globalQueue.postRunnable(() -> {
            MurkaEdition.Result prepared;
            try {
                prepared = MurkaEdition.prepare(packFile);
            } catch (Throwable e) { // MurkaGram: спиннер закрывается при любой ошибке
                FileLog.e(e);
                prepared = new MurkaEdition.Result();
            }
            final MurkaEdition.Result result = prepared;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    progress.dismiss();
                } catch (Throwable ignore) {

                }
                final Activity parent = fragment.getParentActivity();
                if (parent == null || fragment.isFinished) {
                    MurkaEdition.cleanupApk(result); // MurkaGram: окно закрыто — распакованная сборка не нужна
                    return;
                }
                if (result.error != MurkaEdition.OK || result.apk == null) {
                    BulletinFactory.of(fragment).createErrorBulletin(MurkaEdition.getErrorText(result.error)).show();
                    return;
                }
                try {
                    AndroidUtilities.openForView(result.apk, MurkaEdition.ENTRY_APK, "application/vnd.android.package-archive", parent, null, false);
                } catch (Throwable e) {
                    FileLog.e(e);
                    MurkaEdition.cleanupApk(result);
                    BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.MurkaDesignPackInvalid)).show();
                }
            });
        });
    }

    private static void applyPack(BaseFragment fragment, MurkaDesignPack pack, boolean install) {
        boolean restart = false;
        try {
            restart = MurkaDesignPack.apply(pack, fragment);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (install) {
            Utilities.globalQueue.postRunnable(() -> MurkaDesignPack.install(pack));
        }
        if (fragment.getParentActivity() == null || fragment.isFinished) {
            return;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MurkaDesignPackApplied)).show();
        if (restart) {
            showRestartDialog(fragment);
        }
    }

    private static void installPack(BaseFragment fragment, MurkaDesignPack pack, Runnable onChanged) {
        Utilities.globalQueue.postRunnable(() -> {
            final boolean ok = MurkaDesignPack.install(pack);
            AndroidUtilities.runOnUIThread(() -> {
                if (onChanged != null) {
                    onChanged.run();
                }
                if (fragment.getParentActivity() == null || fragment.isFinished) {
                    return;
                }
                if (ok) {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MurkaDesignPackInstallDone)).show();
                } else {
                    BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.MurkaDesignPackInvalid)).show();
                }
            });
        });
    }

    private static void confirmDelete(BaseFragment fragment, MurkaDesignPack pack, Runnable onChanged) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(LocaleController.getString(R.string.MurkaDesignPackDelete));
        builder.setMessage(LocaleController.formatString(R.string.MurkaDesignPackDeleteConfirm, pack.getDisplayName()));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            final String id = pack.id;
            Utilities.globalQueue.postRunnable(() -> {
                MurkaDesignPack.delete(id);
                if (onChanged != null) {
                    AndroidUtilities.runOnUIThread(onChanged);
                }
            });
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        final AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        final View button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button instanceof TextView) {
            ThemeBind.textColor((TextView) button, Theme.key_text_RedBold);
        }
    }

    public static void showRestartDialog(BaseFragment fragment) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(LocaleController.getString(R.string.MurkaDesignPackRestart));
        builder.setPositiveButton(LocaleController.getString(R.string.MurkaRestart), (dialog, which) -> MurkaDesignPack.restartApp(fragment.getParentActivity()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }
}
