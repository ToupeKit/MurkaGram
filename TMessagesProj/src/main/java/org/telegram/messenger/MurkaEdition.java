/*
 * MurkaGram: пакеты-редакции (.mgdesign с "type": "edition") — внутри полная сборка MurkaGram (edition.apk)
 * другой редакции интерфейса: «Classic» (ядро 13.2.2 + интерфейс 12.2.10) или «Modern» (интерфейс 13.2.2).
 * Применение = установка сборки поверх системным установщиком (тот же applicationId и подпись — данные сохраняются).
 *
 * Установка разрешена, только если выполнены ВСЕ условия:
 *  a) SHA-256 всего файла .mgdesign есть в Remote Config "murka_verified_designs" ([{"sha256":"…","name":"…"}]);
 *  b) APK: packageName совпадает, сертификат подписи совпадает с установленным, versionCode равен текущему;
 *  c) редакция пакета отличается от редакции этой сборки (MurkaConfig.UI_EDITION).
 * Логика без UI — её же переносим в ветку core-13.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.text.TextUtils;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MurkaEdition {

    public static final String PARAM = "murka_verified_designs";

    public static final String CLASSIC = "classic";
    public static final String MODERN = "modern";

    public static final String TYPE_EDITION = "edition";
    public static final String ENTRY_APK = "edition.apk";

    public static final long MAX_APK_SIZE = 200L * 1024 * 1024;
    public static final long MAX_FILE_SIZE = 220L * 1024 * 1024;

    private static final int MAX_VERIFIED_JSON = 256 * 1024;
    private static final int MAX_VERIFIED_COUNT = 1000;
    private static final int MAX_VERIFIED_NAME = 64;
    private static final long CLEANUP_AGE = 10 * 60 * 1000L;
    private static final long SPACE_RESERVE = 32L * 1024 * 1024; // запас свободного места сверх размера файла
    private static final String IMPORT_PREFIX = "import_";
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public static final int OK = 0;
    public static final int ERROR_INVALID = 1;
    public static final int ERROR_UNVERIFIED = 2;
    public static final int ERROR_SAME_EDITION = 3;
    public static final int ERROR_PACKAGE = 4;
    public static final int ERROR_SIGNATURE = 5;
    public static final int ERROR_VERSION = 6;
    public static final int ERROR_TOO_LARGE = 7;
    public static final int ERROR_NO_SPACE = 8;

    public static class Result {
        public int error = ERROR_INVALID;
        public File apk;
        public String edition;
    }

    public static boolean isValidEdition(String edition) {
        return CLASSIC.equals(edition) || MODERN.equals(edition);
    }

    public static boolean isCurrent(String edition) {
        return MurkaConfig.UI_EDITION.equals(edition);
    }

    public static String getEditionName(String edition) {
        return CLASSIC.equals(edition) ? "Classic" : MODERN.equals(edition) ? "Modern" : "?";
    }

    public static String getErrorText(int error) {
        switch (error) {
            case ERROR_UNVERIFIED:
                return LocaleController.getString(R.string.MurkaEditionUnverified);
            case ERROR_SAME_EDITION:
                return LocaleController.getString(R.string.MurkaEditionSame);
            case ERROR_PACKAGE:
            case ERROR_SIGNATURE:
                return LocaleController.getString(R.string.MurkaEditionBadSignature);
            case ERROR_VERSION:
                return LocaleController.getString(R.string.MurkaEditionBadVersion);
            case ERROR_TOO_LARGE:
                return LocaleController.getString(R.string.MurkaDesignPackTooLarge);
            case ERROR_NO_SPACE:
                return LocaleController.getString(R.string.MurkaEditionNoSpace);
        }
        return LocaleController.getString(R.string.MurkaDesignPackInvalid);
    }

    // ---------------------------------------------------------------------------------------------
    // Список проверенных пакетов из Remote Config (значение активирует MurkaBadges.fetch)

    private static String verifiedRaw;
    private static Map<String, String> verified = Collections.emptyMap();

    private static synchronized Map<String, String> getVerified() {
        String raw = null;
        try {
            raw = FirebaseRemoteConfig.getInstance().getString(PARAM);
        } catch (Throwable e) {
            FileLog.e(e, false);
        }
        if (raw == null) {
            raw = "";
        }
        if (!raw.equals(verifiedRaw)) {
            verified = parseVerified(raw);
            verifiedRaw = raw;
        }
        return verified;
    }

    // Некорректный JSON — пустой список.
    static Map<String, String> parseVerified(String raw) {
        if (TextUtils.isEmpty(raw) || raw.length() > MAX_VERIFIED_JSON || !MurkaDesignPack.checkJsonDepth(raw, 3)) {
            return Collections.emptyMap();
        }
        try {
            final JSONArray array = new JSONArray(raw);
            final HashMap<String, String> result = new HashMap<>();
            for (int i = 0; i < array.length() && i < MAX_VERIFIED_COUNT; i++) {
                final JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final Object hash = item.opt("sha256");
                if (!(hash instanceof String)) {
                    continue;
                }
                final String hex = ((String) hash).trim().toLowerCase(Locale.ROOT);
                if (!SHA256_PATTERN.matcher(hex).matches()) {
                    continue;
                }
                final Object name = item.opt("name");
                String nameText = name instanceof String ? ((String) name).trim() : "";
                if (nameText.length() > MAX_VERIFIED_NAME) {
                    nameText = nameText.substring(0, MAX_VERIFIED_NAME);
                }
                result.put(hex, nameText);
            }
            return Collections.unmodifiableMap(result);
        } catch (Throwable e) {
            FileLog.e(e, false);
            return Collections.emptyMap();
        }
    }

    public static boolean isVerified(String sha256) {
        try {
            return sha256 != null && getVerified().containsKey(sha256.toLowerCase(Locale.ROOT));
        } catch (Throwable e) {
            FileLog.e(e, false);
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Файлы

    // SHA-256 файла потоком (не вызывать с UI-потока). null — ошибка.
    public static String sha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return Utilities.bytesToHex(digest.digest()).toLowerCase(Locale.ROOT);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Только распакованная и проверенная сборка (edition.apk) — этот каталог открыт FileProvider'ом ("mgdesign_edition", только чтение).
    public static File getEditionDir() {
        return ApplicationLoader.getFilesDirFixed("mgdesign/edition");
    }

    // Приватные копии пакетов (импорт и копия для проверки). Не покрыт provider_paths.xml, пишет сюда только MurkaGram.
    public static File getStagingDir() {
        return ApplicationLoader.getFilesDirFixed("mgdesign/staging");
    }

    // Импортированный из файла пакет-редакция хранится тут под уникальным именем (в список установленных не копируется).
    // Имя не переиспользуется: повторный импорт не может подменить файл, который уже проверяется. Старые импорты удаляются.
    public static File createImportFile() {
        try {
            final File dir = getStagingDir();
            if (dir == null) {
                return null;
            }
            final File[] old = dir.listFiles();
            if (old != null) {
                for (File f : old) {
                    if (f.getName().startsWith(IMPORT_PREFIX)) {
                        f.delete();
                    }
                }
            }
            return File.createTempFile(IMPORT_PREFIX, MurkaDesignPack.EXTENSION, dir);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Удаляет старые распакованные сборки, копии и импортированные пакеты (вызывается при запуске).
    public static void cleanup() {
        cleanupDir(getEditionDir());
        cleanupDir(getStagingDir());
    }

    private static void cleanupDir(File dir) {
        try {
            final File[] files = dir == null ? null : dir.listFiles();
            if (files == null) {
                return;
            }
            final long now = System.currentTimeMillis();
            for (File f : files) {
                if (Math.abs(now - f.lastModified()) > CLEANUP_AGE) {
                    f.delete();
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static boolean isInside(File file, File dir) {
        try {
            final File parent = file.getCanonicalFile().getParentFile();
            return dir != null && parent != null && parent.equals(dir.getCanonicalFile());
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean hasSpace(File dir, long bytes) {
        try {
            return dir.getUsableSpace() >= bytes + SPACE_RESERVE;
        } catch (Throwable e) {
            return true; // неизвестно — пусть решит запись (ошибка записи тоже даёт ERROR_NO_SPACE)
        }
    }

    // Копирует файл с подсчётом SHA-256 за один проход: хэш — ровно от байтов приватной копии.
    private static String copyWithHash(File src, File dst, long limit) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            final byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    return null;
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        return Utilities.bytesToHex(digest.digest()).toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------------------------------------
    // Подготовка к установке (не вызывать с UI-потока)

    // Порядок: приватная копия (хэш считается при копировании) → проверка хэша → чтение и распаковка ЭТОЙ копии →
    // проверка APK. Исходный файл (кэш чата, внешнее хранилище) после копирования больше не читается — подмена не влияет.
    // synchronized: распаковка всегда в один и тот же edition.apk.
    public static synchronized Result prepare(File packFile) {
        final Result result = new Result();
        File copy = null;
        boolean ownCopy = false;
        File apk = null;
        File tmp = null;
        try {
            if (packFile == null || !packFile.isFile()) {
                return result;
            }
            final long length = packFile.length();
            if (length > MAX_FILE_SIZE) {
                result.error = ERROR_TOO_LARGE;
                return result;
            }
            final File staging = getStagingDir();
            final File dir = getEditionDir();
            if (staging == null || dir == null) {
                return result;
            }
            // a) хэш всего файла — заново (превью не доверяем), по приватной копии
            final String hash;
            if (isInside(packFile, staging)) {
                copy = packFile; // уже приватный файл с уникальным именем (createImportFile)
                hash = sha256(copy);
            } else {
                if (!hasSpace(staging, length)) {
                    result.error = ERROR_NO_SPACE;
                    return result;
                }
                copy = File.createTempFile("check", ".tmp", staging);
                ownCopy = true;
                try {
                    hash = copyWithHash(packFile, copy, MAX_FILE_SIZE);
                } catch (IOException e) {
                    FileLog.e(e);
                    result.error = hasSpace(staging, 0) ? ERROR_INVALID : ERROR_NO_SPACE;
                    return result;
                }
                if (hash == null) {
                    result.error = ERROR_TOO_LARGE;
                    return result;
                }
            }
            if (hash == null) {
                return result;
            }
            if (!isVerified(hash)) {
                result.error = ERROR_UNVERIFIED;
                return result;
            }
            final MurkaDesignPack pack;
            try {
                pack = MurkaDesignPack.read(copy, false, false);
            } catch (MurkaDesignPack.PackException e) {
                result.error = e.error == MurkaDesignPack.ERROR_TOO_LARGE ? ERROR_TOO_LARGE : ERROR_INVALID;
                return result;
            }
            if (!pack.isEdition()) {
                return result;
            }
            result.edition = pack.edition;
            // c) другая редакция
            if (isCurrent(pack.edition)) {
                result.error = ERROR_SAME_EDITION;
                return result;
            }
            apk = new File(dir, ENTRY_APK);
            tmp = new File(dir, ENTRY_APK + ".tmp");
            deleteQuietly(tmp);
            // распаковка потоком из приватной копии с подсчётом реальных байт; имя фиксированное — zip-slip невозможен
            try (ZipFile zip = new ZipFile(copy)) {
                MurkaDesignPack.checkEntries(zip); // дубликаты/странные имена — отказ
                final ZipEntry entry = zip.getEntry(ENTRY_APK);
                if (entry == null || entry.isDirectory() || !ENTRY_APK.equals(entry.getName())) {
                    return result;
                }
                final long hint = entry.getSize();
                if (hint > MAX_APK_SIZE) {
                    result.error = ERROR_TOO_LARGE;
                    return result;
                }
                if (!hasSpace(dir, hint > 0 ? hint : 0)) {
                    result.error = ERROR_NO_SPACE;
                    return result;
                }
                try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(tmp)) {
                    final byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_APK_SIZE) {
                            result.error = ERROR_TOO_LARGE;
                            return result;
                        }
                        try {
                            out.write(buffer, 0, read);
                        } catch (IOException e) {
                            FileLog.e(e);
                            result.error = hasSpace(dir, 0) ? ERROR_INVALID : ERROR_NO_SPACE;
                            return result;
                        }
                    }
                }
            }
            // только чтение (владельцем): каталог files/ приватный, на запись файл закрыт и для самого MurkaGram
            tmp.setReadable(false, false);
            tmp.setReadable(true, true);
            tmp.setWritable(false, false);
            deleteQuietly(apk);
            if (!tmp.renameTo(apk)) {
                return result;
            }
            tmp = null;
            // b) пакет, подпись, версия
            final int apkError = checkApk(apk);
            if (apkError != OK) {
                result.error = apkError;
                return result;
            }
            result.apk = apk;
            result.error = OK;
            apk = null; // не удалять
            return result;
        } catch (Throwable e) {
            FileLog.e(e);
            result.error = ERROR_INVALID;
            return result;
        } finally {
            deleteQuietly(tmp);
            deleteQuietly(apk);
            if (ownCopy) {
                deleteQuietly(copy);
            }
        }
    }

    // Удалить распакованную сборку, если её не передали установщику.
    public static void cleanupApk(Result result) {
        if (result != null && result.apk != null) {
            final File apk = result.apk;
            result.apk = null;
            Utilities.globalQueue.postRunnable(() -> deleteQuietly(apk));
        }
    }

    public static void deleteQuietly(File file) {
        if (file != null) {
            try {
                file.delete();
            } catch (Throwable ignore) {

            }
        }
    }

    @SuppressWarnings("deprecation")
    static int checkApk(File apk) {
        try {
            final Context context = ApplicationLoader.applicationContext;
            final PackageManager pm = context.getPackageManager();
            final int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            final PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            if (archive == null || !context.getPackageName().equals(archive.packageName)) {
                return ERROR_PACKAGE;
            }
            final PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            if (installed == null) {
                return ERROR_SIGNATURE;
            }
            final HashSet<String> archiveCerts = getCertHashes(archive);
            final HashSet<String> installedCerts = getCertHashes(installed);
            // пустой набор никогда не совпадает (пустое == пустое не считается)
            if (archiveCerts == null || installedCerts == null || archiveCerts.isEmpty() || installedCerts.isEmpty()
                    || !archiveCerts.equals(installedCerts)) {
                return ERROR_SIGNATURE;
            }
            if (getVersionCode(archive) != getVersionCode(installed)) {
                return ERROR_VERSION;
            }
            return OK;
        } catch (Throwable e) {
            FileLog.e(e);
            return ERROR_INVALID;
        }
    }

    // SHA-256 сертификатов, которыми подписан APK сейчас. API 28+: signingInfo.getApkContentsSigners() — при нескольких
    // подписантах все, при ротации ключа — только текущий (история ротации не учитывается: нужен тот же текущий ключ).
    // API < 28: GET_SIGNATURES (все подписанты). null/пустой массив/null-элемент — null (отказ).
    @SuppressWarnings("deprecation")
    private static HashSet<String> getCertHashes(PackageInfo info) throws Exception {
        final Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            final SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) {
                return null;
            }
            signatures = signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            return null;
        }
        final HashSet<String> result = new HashSet<>();
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            if (signature == null) {
                return null;
            }
            final byte[] bytes = signature.toByteArray();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            result.add(Utilities.bytesToHex(digest.digest(bytes)).toLowerCase(Locale.ROOT));
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("deprecation")
    private static long getVersionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }
}
