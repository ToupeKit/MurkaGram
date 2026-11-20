package org.telegram.messenger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.classic.ui.ActionBar.BaseFragment;
import org.telegram.classic.ui.ActionBar.BottomSheet;
import org.telegram.classic.ui.ActionBar.Theme;
import org.telegram.classic.ui.Components.LayoutHelper;
import org.telegram.classic.ui.Components.StickerImageView;

import java.io.File;
import java.util.ArrayList;

// MurkaGram: dual UI. The files-migration sheet (legacy external storage -> scoped storage, Android 11) of 12.2.10 built
// from the classic UI: FilesMigrationService.checkBottomSheet / FilesMigrationBottomSheet take modern UI types. The
// migration itself (FilesMigrationService.start) and its prefs/state are the shared ones.
public final class ClassicFilesMigrationService {

    private ClassicFilesMigrationService() {
    }

    public static FilesMigrationBottomSheet filesMigrationBottomSheet;

    @SuppressLint("NewApi")
    public static void checkBottomSheet(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
        if (!Environment.isExternalStorageLegacy() || sharedPreferences.getBoolean("migration_to_scoped_storage_finished", false) || sharedPreferences.getInt("migration_to_scoped_storage_count", 0) >= 5 || FilesMigrationService.wasShown || filesMigrationBottomSheet != null || FilesMigrationService.filesMigrationBottomSheet != null || FilesMigrationService.isRunning) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            File path = Environment.getExternalStorageDirectory();
            if (!TextUtils.isEmpty(SharedConfig.storageCacheDir)) {
                ArrayList<File> dirs = AndroidUtilities.getRootDirs();
                if (dirs != null) {
                    for (int a = 0, N = dirs.size(); a < N; a++) {
                        File dir = dirs.get(a);
                        if (dir.getAbsolutePath().startsWith(SharedConfig.storageCacheDir)) {
                            path = dir;
                            break;
                        }
                    }
                }
            }
            File oldDirectory = new File(path, "Telegram");
            FilesMigrationService.hasOldFolder = oldDirectory.exists();
        }
        if (FilesMigrationService.hasOldFolder) {
            filesMigrationBottomSheet = new FilesMigrationBottomSheet(fragment);
            filesMigrationBottomSheet.show();
            FilesMigrationService.wasShown = true;
            sharedPreferences.edit().putInt("migration_to_scoped_storage_count", sharedPreferences.getInt("migration_to_scoped_storage_count", 0) + 1).apply();
        } else {
            sharedPreferences.edit().putBoolean("migration_to_scoped_storage_finished", true).apply();
        }
    }

    // 12.2.10: FilesMigrationService.filesMigrationBottomSheet.migrateOldFolder() after the storage permissions are granted
    public static void onPermissionsGranted() {
        if (filesMigrationBottomSheet != null) {
            filesMigrationBottomSheet.migrateOldFolder();
        }
    }

    public static class FilesMigrationBottomSheet extends BottomSheet {

        BaseFragment fragment;

        @Override
        protected boolean canDismissWithSwipe() {
            return false;
        }

        @Override
        protected boolean canDismissWithTouchOutside() {
            return false;
        }

        public FilesMigrationBottomSheet(BaseFragment fragment) {
            super(fragment.getParentActivity(), false);
            this.fragment = fragment;
            setCanceledOnTouchOutside(false);
            Context context = fragment.getParentActivity();
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            StickerImageView imageView = new StickerImageView(context, currentAccount);
            imageView.setStickerNum(7);
            imageView.getImageReceiver().setAutoRepeat(1);
            linearLayout.addView(imageView, LayoutHelper.createLinear(144, 144, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

            TextView title = new TextView(context);
            title.setGravity(Gravity.START);
            title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.bold());
            title.setText(LocaleController.getString(R.string.MigrateOldFolderTitle));
            linearLayout.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 30, 21, 0));

            TextView description = new TextView(context);
            description.setGravity(Gravity.START);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            description.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MigrateOldFolderDescription)));
            linearLayout.addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 15, 21, 16));

            TextView buttonTextView = new TextView(context);
            buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
            buttonTextView.setGravity(Gravity.CENTER);
            buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            buttonTextView.setTypeface(AndroidUtilities.bold());
            buttonTextView.setText(LocaleController.getString(R.string.MigrateOldFolderButton));
            buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            buttonTextView.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 6));
            linearLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 16, 15, 16, 16));

            buttonTextView.setOnClickListener(view -> migrateOldFolder());

            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(linearLayout);
            setCustomView(scrollView);
        }

        @SuppressLint("NewApi")
        public void migrateOldFolder() {
            Activity activity = fragment.getParentActivity();
            if (activity == null) {
                return;
            }
            boolean canWrite = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            boolean canRead = (
                Build.VERSION.SDK_INT >= 33 && (
                    activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                    activity.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                ) ||
                Build.VERSION.SDK_INT < 33 && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            );

            if (!canRead || !canWrite) {
                ArrayList<String> permissions = new ArrayList<>();
                if (!canRead) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
                        permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
                        permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
                    } else {
                        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                    }
                }
                if (!canWrite) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                String[] string = new String[permissions.size()];
                activity.requestPermissions(permissions.toArray(string), 4);
                return;
            }
            FilesMigrationService.start();
            dismiss();
        }

        @Override
        public void dismiss() {
            super.dismiss();
            filesMigrationBottomSheet = null;
        }
    }
}
