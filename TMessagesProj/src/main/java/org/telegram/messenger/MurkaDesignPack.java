/*
 * MurkaGram: пакеты дизайна (.mgdesign) — ZIP только с данными:
 * manifest.json, design.json (белый список настроек MurkaConfig), необязательные theme.attheme и preview.png.
 * Пакет-редакция ("type": "edition", "edition": "classic"|"modern") вместо данных несёт edition.apk — см. MurkaEdition.
 * Никакого кода и сетевых ссылок: всё читается в память с лимитами и проверяется,
 * тема пересобирается из строк «ключ=цвет» для известных ключей, превью перекодируется.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;
import org.telegram.ui.Components.MurkaMD3;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MurkaDesignPack {

    public static final String EXTENSION = ".mgdesign";
    public static final int FORMAT = 1;

    // Лимиты (байты считаются при чтении, заголовкам архива не верим).
    public static final long MAX_FILE_SIZE = 8L * 1024 * 1024;
    private static final int MAX_ENTRIES = 16;
    private static final int MAX_MANIFEST_SIZE = 64 * 1024;
    static final int MAX_DESIGN_SIZE = 64 * 1024; // MurkaGram: dual UI, widened for the classic UI
    static final int MAX_THEME_SIZE = 512 * 1024; // MurkaGram: dual UI, widened for the classic UI
    private static final int MAX_PREVIEW_SIZE = 2 * 1024 * 1024;
    private static final int MAX_PREVIEW_SIDE = 1024;
    private static final int MAX_PREVIEW_SOURCE_SIDE = 4096;
    private static final int MAX_ENTRY_NAME_LENGTH = 255;
    private static final int MAX_JSON_DEPTH = 4; // manifest/design плоские; глубже — отказ до разбора (без StackOverflowError)
    private static final int MAX_DESCRIPTION_LINES = 8;
    private static final int MIN_THEME_KEYS = 10;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_AUTHOR_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final int MAX_VERSION_LENGTH = 16;
    private static final long ZIP_ENTRY_TIME = 1262304000000L; // фиксированное время записей — одинаковый пакет даёт одинаковый id

    private static final String ENTRY_MANIFEST = "manifest.json";
    private static final String ENTRY_DESIGN = "design.json";
    private static final String ENTRY_THEME = "theme.attheme";
    private static final String ENTRY_PREVIEW = "preview.png";

    private static final Pattern THEME_KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,95}");
    private static final Pattern THEME_HEX_PATTERN = Pattern.compile("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})");
    private static final Pattern THEME_INT_PATTERN = Pattern.compile("[+-]?[0-9]{1,10}");
    private static final Pattern ID_PATTERN = Pattern.compile("[0-9a-f]{16}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}"); // MurkaGram: хэш исходного файла (бейджик)

    public static final int ERROR_INVALID = 1;
    public static final int ERROR_TOO_LARGE = 2;
    public static final int ERROR_NEWER_FORMAT = 3;

    public static class PackException extends Exception {
        public final int error;

        public PackException(int error) {
            super("mgdesign error " + error);
            this.error = error;
        }
    }

    public String id;
    public File file;
    public String name = "";
    public String author = "";
    public String description = "";
    public String version = "";
    public final LinkedHashMap<String, Object> design = new LinkedHashMap<>();
    public String themeText; // нормализованная тема «ключ=int», без обоев; null — темы нет
    public int themeKeysCount;
    public Bitmap preview;
    // MurkaGram: пакет-редакция ("type": "edition") — edition = "classic"/"modern", иначе null (обычный пакет данных)
    public String edition;
    // MurkaGram: SHA-256 исходного файла пакета (для бейджика «Проверенный пакет»), у установленных — из файла <id>.sha
    public String sha256;
    public boolean verified; // хэш есть в Remote Config "murka_verified_designs" (считается в фоне вместе с sha256)

    public boolean isEdition() {
        return edition != null;
    }

    public void setSourceHash(String hash) {
        sha256 = hash;
        verified = MurkaEdition.isVerified(hash);
    }

    // MurkaGram: у установленного пакета — хэш исходного файла из <id>.sha (сам файл перепакован). Не вызывать с UI-потока.
    public void loadInstalledHash() {
        setSourceHash(file == null || id == null ? null : readSourceHash(file.getParentFile(), id));
    }

    public String getDisplayName() {
        return TextUtils.isEmpty(name) ? LocaleController.getString(R.string.MurkaDesignPackUntitled) : name;
    }

    public static String getErrorText(int error) {
        if (error == ERROR_TOO_LARGE) {
            return LocaleController.getString(R.string.MurkaDesignPackTooLarge);
        } else if (error == ERROR_NEWER_FORMAT) {
            return LocaleController.getString(R.string.MurkaDesignPackNewerFormat);
        }
        return LocaleController.getString(R.string.MurkaDesignPackInvalid);
    }

    // ---------------------------------------------------------------------------------------------
    // Чтение и проверка

    // Читает и проверяет пакет. Не вызывать с UI-потока. Любая ошибка (в т.ч. OOM) — PackException.
    public static MurkaDesignPack read(File file, boolean withPreview) throws PackException {
        return read(file, withPreview, true);
    }

    // MurkaGram: withHash = false — без SHA-256 файла (MurkaEdition.prepare уже посчитал хэш своей приватной копии)
    static MurkaDesignPack read(File file, boolean withPreview, boolean withHash) throws PackException {
        try {
            if (file == null || !file.isFile()) {
                throw new PackException(ERROR_INVALID);
            }
            final long fileLength = file.length();
            if (fileLength > MurkaEdition.MAX_FILE_SIZE) { // MurkaGram: лимит пакета-редакции; для данных — ниже
                throw new PackException(ERROR_TOO_LARGE);
            }
            try (ZipFile zip = new ZipFile(file)) {
                checkEntries(zip);

                final MurkaDesignPack pack = new MurkaDesignPack();

                byte[] manifestBytes = readEntry(zip, ENTRY_MANIFEST, MAX_MANIFEST_SIZE);
                if (manifestBytes == null) {
                    throw new PackException(ERROR_INVALID);
                }
                JSONObject manifest = parseJsonObject(manifestBytes);
                Object format = manifest.opt("format");
                if (!(format instanceof Number)) {
                    throw new PackException(ERROR_INVALID);
                }
                double formatValue = ((Number) format).doubleValue();
                if (formatValue > FORMAT && formatValue == Math.rint(formatValue) && !Double.isInfinite(formatValue)) {
                    throw new PackException(ERROR_NEWER_FORMAT);
                }
                if (formatValue != FORMAT) {
                    throw new PackException(ERROR_INVALID);
                }
                pack.name = cleanText(manifest.opt("name"), MAX_NAME_LENGTH, false);
                pack.author = cleanText(manifest.opt("author"), MAX_AUTHOR_LENGTH, false);
                pack.description = cleanText(manifest.opt("description"), MAX_DESCRIPTION_LENGTH, true);
                pack.version = cleanText(manifest.opt("version"), MAX_VERSION_LENGTH, false);

                // MurkaGram: пакет-редакция — только manifest, edition.apk (читается потоком при установке) и превью
                final Object type = manifest.opt("type");
                if (type != null) {
                    if (!(type instanceof String)) {
                        throw new PackException(ERROR_INVALID);
                    }
                    if (!MurkaEdition.TYPE_EDITION.equals(type)) {
                        throw new PackException(ERROR_NEWER_FORMAT);
                    }
                    final Object edition = manifest.opt("edition");
                    if (!(edition instanceof String) || !MurkaEdition.isValidEdition((String) edition)) {
                        throw new PackException(ERROR_INVALID);
                    }
                    final ZipEntry apkEntry = zip.getEntry(MurkaEdition.ENTRY_APK);
                    if (apkEntry == null || apkEntry.isDirectory() || !MurkaEdition.ENTRY_APK.equals(apkEntry.getName())) {
                        throw new PackException(ERROR_INVALID);
                    }
                    if (apkEntry.getSize() > MurkaEdition.MAX_APK_SIZE) { // подсказка заголовка; реальные байты считаются при распаковке
                        throw new PackException(ERROR_TOO_LARGE);
                    }
                    pack.edition = (String) edition;
                    if (withPreview) {
                        byte[] previewBytes = readEntry(zip, ENTRY_PREVIEW, MAX_PREVIEW_SIZE);
                        if (previewBytes != null) {
                            pack.preview = decodePreview(previewBytes);
                        }
                    }
                    if (withHash) {
                        pack.setSourceHash(MurkaEdition.sha256(file));
                    }
                    return pack;
                }
                if (fileLength > MAX_FILE_SIZE) {
                    throw new PackException(ERROR_TOO_LARGE);
                }
                if (zip.getEntry(MurkaEdition.ENTRY_APK) != null) {
                    throw new PackException(ERROR_INVALID);
                }

                byte[] designBytes = readEntry(zip, ENTRY_DESIGN, MAX_DESIGN_SIZE);
                if (designBytes != null) {
                    JSONObject design = parseJsonObject(designBytes);
                    for (String key : MurkaConfig.DESIGN_KEYS) {
                        Object value = MurkaConfig.validateDesignValue(key, design.opt(key), false);
                        if (value != null) {
                            pack.design.put(key, value);
                        }
                    }
                }

                byte[] themeBytes = readEntry(zip, ENTRY_THEME, MAX_THEME_SIZE);
                if (themeBytes != null) {
                    pack.sanitizeTheme(themeBytes);
                }

                if (pack.design.isEmpty() && pack.themeText == null) {
                    throw new PackException(ERROR_INVALID);
                }

                if (withPreview) {
                    byte[] previewBytes = readEntry(zip, ENTRY_PREVIEW, MAX_PREVIEW_SIZE);
                    if (previewBytes != null) {
                        pack.preview = decodePreview(previewBytes);
                    }
                }
                if (withHash) {
                    pack.setSourceHash(MurkaEdition.sha256(file)); // MurkaGram: бейджик «Проверенный пакет»
                }
                return pack;
            }
        } catch (PackException e) {
            throw e;
        } catch (Throwable e) {
            FileLog.e(e);
            throw new PackException(ERROR_INVALID);
        }
    }

    // MurkaGram: проверка каталога архива: число записей, имена без путей, без дубликатов
    // (у ZipFile при двух «design.json» неочевидно, какая запись вернётся из getEntry — такой пакет отклоняется).
    static void checkEntries(ZipFile zip) throws PackException {
        if (zip.size() > MAX_ENTRIES) {
            throw new PackException(ERROR_INVALID);
        }
        final HashSet<String> names = new HashSet<>();
        int count = 0;
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            if (++count > MAX_ENTRIES) {
                throw new PackException(ERROR_INVALID);
            }
            final String entryName = entry.getName();
            if (entryName == null || entryName.isEmpty() || entryName.length() > MAX_ENTRY_NAME_LENGTH
                    || entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\\")
                    || entryName.indexOf('\0') >= 0 || entryName.indexOf(':') >= 0) {
                throw new PackException(ERROR_INVALID);
            }
            if (!names.add(entryName.toLowerCase(Locale.ROOT))) {
                throw new PackException(ERROR_INVALID);
            }
        }
    }

    // MurkaGram: JSON-объект с ограничением вложенности (проверяется до разбора, рекурсивный парсер org.json не вызывается на глубоком входе).
    private static JSONObject parseJsonObject(byte[] bytes) throws Exception {
        final String text = new String(bytes, StandardCharsets.UTF_8);
        if (!checkJsonDepth(text, MAX_JSON_DEPTH)) {
            throw new PackException(ERROR_INVALID);
        }
        return new JSONObject(text);
    }

    // Глубина вложенности {}/[] вне строк не больше maxDepth.
    static boolean checkJsonDepth(String text, int maxDepth) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                if (++depth > maxDepth) {
                    return false;
                }
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        return true;
    }

    // Только точное имя записи; размер считается по факту распаковки.
    private static byte[] readEntry(ZipFile zip, String entryName, int limit) throws Exception {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            return null;
        }
        if (entry.isDirectory() || !entryName.equals(entry.getName())) {
            throw new PackException(ERROR_INVALID);
        }
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, 16 * 1024));
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new PackException(ERROR_TOO_LARGE);
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    // Строка из manifest: только String, без управляющих и bidi-символов, с обрезкой по длине.
    private static String cleanText(Object value, int maxLength, boolean multiline) {
        if (!(value instanceof String)) {
            return "";
        }
        final String s = (String) value;
        final StringBuilder sb = new StringBuilder(Math.min(s.length(), maxLength));
        int newlines = 0;
        int totalNewlines = 0;
        int marks = 0;
        for (int i = 0; i < s.length(); ) {
            final int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n' || cp == '\r' || cp == '\t' || cp == 0x2028 || cp == 0x2029) {
                marks = 0;
                if (multiline && (cp == '\n' || cp == 0x2028 || cp == 0x2029)) {
                    // MurkaGram: не больше 2 переводов подряд и MAX_DESCRIPTION_LINES всего (иначе окно растягивается)
                    if (++newlines > 2 || ++totalNewlines > MAX_DESCRIPTION_LINES) {
                        continue;
                    }
                    if (sb.length() + 1 > maxLength) {
                        break;
                    }
                    sb.append('\n');
                } else if (cp != '\r') {
                    if (sb.length() + 1 > maxLength) {
                        break;
                    }
                    sb.append(' ');
                }
                continue;
            }
            if (Character.isISOControl(cp) || isBidiControl(cp)) {
                continue;
            }
            final int type = Character.getType(cp);
            if (type == Character.SURROGATE || type == Character.UNASSIGNED || type == Character.PRIVATE_USE) {
                continue;
            }
            // MurkaGram: «Zalgo» — длинные цепочки диакритик рисуются поверх окна; оставляем не больше 4 подряд
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK) {
                if (++marks > 4) {
                    continue;
                }
            } else {
                marks = 0;
            }
            if (sb.length() + Character.charCount(cp) > maxLength) {
                break;
            }
            sb.appendCodePoint(cp);
            newlines = 0;
        }
        return sb.toString().trim();
    }

    private static boolean isBidiControl(int cp) {
        return cp == 0x061C || cp == 0x200E || cp == 0x200F || cp >= 0x202A && cp <= 0x202E || cp >= 0x2066 && cp <= 0x2069 || cp == 0xFEFF;
    }

    // Оставляет только строки «ключ=цвет» для известных ключей ThemeColors. WLS/WPS (обои, ссылки) и всё прочее выбрасывается.
    void sanitizeTheme(byte[] bytes) { // MurkaGram: dual UI, widened for the classic UI
        final String text = new String(bytes, StandardCharsets.ISO_8859_1);
        final LinkedHashMap<Integer, Integer> colors = new LinkedHashMap<>();
        int start = 0;
        final int length = text.length();
        while (start < length) {
            int end = start;
            while (end < length && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
                end++;
            }
            if (end - start <= 256) {
                parseThemeLine(text.substring(start, end).trim(), colors);
            }
            start = end + 1;
        }
        if (colors.size() < MIN_THEME_KEYS) {
            themeText = null;
            themeKeysCount = 0;
            return;
        }
        final StringBuilder sb = new StringBuilder(colors.size() * 40);
        for (java.util.Map.Entry<Integer, Integer> e : colors.entrySet()) {
            sb.append(ThemeColors.getStringName(e.getKey())).append('=').append(e.getValue()).append('\n');
        }
        themeText = sb.toString();
        themeKeysCount = colors.size();
    }

    private static void parseThemeLine(String line, LinkedHashMap<Integer, Integer> out) {
        if (line.isEmpty() || line.startsWith("WLS") || line.startsWith("WPS") || line.startsWith("WPE")) {
            return;
        }
        final int eq = line.indexOf('=');
        if (eq <= 0 || line.indexOf('=', eq + 1) >= 0) {
            return;
        }
        final String key = line.substring(0, eq).trim();
        final String value = line.substring(eq + 1).trim();
        if (!THEME_KEY_PATTERN.matcher(key).matches()) {
            return;
        }
        final int idx = ThemeColors.stringKeyToInt(key);
        if (idx < 0 || idx >= Theme.colorsCount || idx == Theme.key_wallpaperFileOffset) {
            return;
        }
        final String name = ThemeColors.getStringName(idx);
        if (name == null || !name.equals(key)) {
            return;
        }
        final int color;
        try {
            if (THEME_HEX_PATTERN.matcher(value).matches()) {
                final String hex = value.substring(1);
                long v = Long.parseLong(hex, 16);
                if (hex.length() == 6) {
                    v |= 0xff000000L;
                }
                color = (int) v;
            } else if (THEME_INT_PATTERN.matcher(value).matches()) {
                color = Integer.parseInt(value.startsWith("+") ? value.substring(1) : value);
            } else {
                return;
            }
        } catch (NumberFormatException e) {
            return;
        }
        out.put(idx, color);
    }

    private static Bitmap decodePreview(byte[] bytes) {
        try {
            if (bytes.length < 8 || (bytes[0] & 0xff) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G'
                    || bytes[4] != 0x0D || bytes[5] != 0x0A || bytes[6] != 0x1A || bytes[7] != 0x0A) {
                return null;
            }
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            final int w = options.outWidth;
            final int h = options.outHeight;
            if (w <= 0 || h <= 0 || w > MAX_PREVIEW_SOURCE_SIDE || h > MAX_PREVIEW_SOURCE_SIDE) {
                return null;
            }
            int sample = 1;
            while (Math.max(w, h) / sample > MAX_PREVIEW_SIDE) {
                sample *= 2;
            }
            final BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decode);
            if (bitmap == null) {
                return null;
            }
            final int bw = bitmap.getWidth();
            final int bh = bitmap.getHeight();
            if (Math.max(bw, bh) > MAX_PREVIEW_SIDE) {
                final float scale = MAX_PREVIEW_SIDE / (float) Math.max(bw, bh);
                final Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bw * scale)), Math.max(1, Math.round(bh * scale)), true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }
            return bitmap;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Запись

    public byte[] toBytes() throws Exception {
        final JSONObject manifest = new JSONObject();
        manifest.put("format", FORMAT);
        manifest.put("name", name == null ? "" : name);
        manifest.put("author", author == null ? "" : author);
        manifest.put("description", description == null ? "" : description);
        manifest.put("version", version == null ? "" : version);
        final JSONObject designJson = new JSONObject();
        for (java.util.Map.Entry<String, Object> e : design.entrySet()) {
            designJson.put(e.getKey(), e.getValue());
        }
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            putEntry(zos, ENTRY_MANIFEST, manifest.toString().getBytes(StandardCharsets.UTF_8));
            putEntry(zos, ENTRY_DESIGN, designJson.toString().getBytes(StandardCharsets.UTF_8));
            if (themeText != null) {
                putEntry(zos, ENTRY_THEME, themeText.getBytes(StandardCharsets.ISO_8859_1));
            }
            if (preview != null && !preview.isRecycled()) {
                final ByteArrayOutputStream png = new ByteArrayOutputStream();
                if (preview.compress(Bitmap.CompressFormat.PNG, 100, png) && png.size() <= MAX_PREVIEW_SIZE) {
                    putEntry(zos, ENTRY_PREVIEW, png.toByteArray());
                }
            }
        }
        return bos.toByteArray();
    }

    private static void putEntry(ZipOutputStream zos, String entryName, byte[] data) throws Exception {
        final ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(ZIP_ENTRY_TIME);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    public static String computeId(byte[] bytes) {
        return Utilities.bytesToHex(Utilities.computeSHA256(bytes)).substring(0, 16).toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------------------------------------
    // Хранилище установленных пакетов: files/mgdesign/packs/<id>.mgdesign (уже перепакованные)

    private static File getPacksDir() {
        return ApplicationLoader.getFilesDirFixed("mgdesign/packs");
    }

    static File getBackupDir() { // MurkaGram: dual UI, widened for the classic UI
        return ApplicationLoader.getFilesDirFixed("mgdesign/backup");
    }

    // Копирует выбранный файл во внутренний кэш с подсчётом байт. Не вызывать с UI-потока. Вызывающий удаляет файл.
    public static File copyFromUri(Uri uri) throws PackException {
        File tmp = null;
        boolean ok = false;
        try {
            tmp = File.createTempFile("mgdesign_import", ".tmp", ApplicationLoader.applicationContext.getCacheDir()); // MurkaGram: уникальное имя — параллельные импорты не мешают друг другу
            final ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
            try (InputStream in = resolver.openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) {
                    throw new PackException(ERROR_INVALID);
                }
                final byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MurkaEdition.MAX_FILE_SIZE) { // MurkaGram: пакет-редакция больше пакета данных; data-лимит проверит read()
                        throw new PackException(ERROR_TOO_LARGE);
                    }
                    out.write(buffer, 0, read);
                }
            }
            ok = true;
            return tmp;
        } catch (PackException e) {
            throw e;
        } catch (Throwable e) {
            FileLog.e(e);
            throw new PackException(ERROR_INVALID);
        } finally {
            if (!ok && tmp != null) {
                try {
                    tmp.delete();
                } catch (Throwable ignore) {

                }
            }
        }
    }

    // Сохраняет пакет в список установленных (перепакованным). Не вызывать с UI-потока.
    public static boolean install(MurkaDesignPack pack) {
        if (pack == null || pack.isEdition()) {
            return false; // MurkaGram: пакеты-редакции (большие) в список установленных не копируются
        }
        File tmp = null;
        try {
            final byte[] bytes = pack.toBytes();
            final String newId = computeId(bytes);
            final File dir = getPacksDir();
            if (dir == null) {
                return false;
            }
            final File target = new File(dir, newId + EXTENSION);
            tmp = new File(dir, newId + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(bytes);
            }
            if (target.exists()) {
                target.delete();
            }
            if (!tmp.renameTo(target)) {
                return false;
            }
            tmp = null;
            writeSourceHash(dir, newId, pack.sha256);
            pack.id = newId;
            pack.file = target;
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        } finally {
            if (tmp != null) {
                try {
                    tmp.delete();
                } catch (Throwable ignore) {

                }
            }
        }
    }

    // MurkaGram: хэш исходного файла рядом с перепакованным пакетом (<id>.sha) — чтобы бейджик сохранился после установки.
    // Проверенный хэш не перезаписывается непроверенным (одинаковое содержимое из разных источников).
    private static void writeSourceHash(File dir, String id, String sha256) {
        try {
            if (sha256 == null || !SHA256_PATTERN.matcher(sha256).matches()) {
                return;
            }
            final File file = new File(dir, id + ".sha");
            if (file.exists() && MurkaEdition.isVerified(readSourceHash(dir, id)) && !MurkaEdition.isVerified(sha256)) {
                return;
            }
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(sha256.getBytes(StandardCharsets.US_ASCII));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static String readSourceHash(File dir, String id) {
        try {
            final File file = new File(dir, id + ".sha");
            if (!file.isFile() || file.length() != 64) {
                return null;
            }
            final String hash = new String(readFile(file), StandardCharsets.US_ASCII);
            return SHA256_PATTERN.matcher(hash).matches() ? hash : null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Установленные пакеты (без превью; битые пропускаются). Не вызывать с UI-потока.
    public static ArrayList<MurkaDesignPack> list() {
        final ArrayList<MurkaDesignPack> result = new ArrayList<>();
        try {
            final File dir = getPacksDir();
            final File[] files = dir == null ? null : dir.listFiles();
            if (files == null) {
                return result;
            }
            final ArrayList<File> sorted = new ArrayList<>();
            for (File f : files) {
                final String fileName = f.getName();
                if (fileName.endsWith(EXTENSION) && ID_PATTERN.matcher(fileName.substring(0, fileName.length() - EXTENSION.length())).matches()) {
                    sorted.add(f);
                }
            }
            Collections.sort(sorted, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : sorted) {
                try {
                    final MurkaDesignPack pack = read(f, false);
                    pack.file = f;
                    pack.id = f.getName().substring(0, f.getName().length() - EXTENSION.length());
                    pack.loadInstalledHash(); // MurkaGram: хэш исходного файла, не перепакованного
                    result.add(pack);
                } catch (PackException e) {
                    FileLog.e(e);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    public static boolean delete(String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            return false;
        }
        try {
            final File dir = getPacksDir();
            if (dir != null) {
                new File(dir, id + ".sha").delete(); // MurkaGram: хэш исходного файла
            }
            return dir != null && new File(dir, id + EXTENSION).delete();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Экспорт текущего дизайна (UI-поток: читает текущие цвета)

    public static MurkaDesignPack exportCurrent() {
        final MurkaDesignPack pack = new MurkaDesignPack();
        pack.name = LocaleController.getString(R.string.MurkaDesignPackMyDesign);
        pack.author = "";
        pack.description = "";
        pack.version = "1";
        pack.design.putAll(MurkaConfig.getDesignValues(false));
        pack.themeText = currentThemeText();
        if (pack.themeText != null) {
            for (int i = 0; i < pack.themeText.length(); i++) {
                if (pack.themeText.charAt(i) == '\n') {
                    pack.themeKeysCount++;
                }
            }
        }
        return pack;
    }

    // Снимок текущих цветов в формате «ключ=int» (без обоев и ссылок).
    private static String currentThemeText() {
        try {
            final StringBuilder sb = new StringBuilder(64 * 1024);
            for (int key = 0; key < Theme.colorsCount; key++) {
                if (key == Theme.key_wallpaperFileOffset) {
                    continue;
                }
                final String keyName = ThemeColors.getStringName(key);
                if (keyName == null) {
                    continue;
                }
                sb.append(keyName).append('=').append(Theme.getNonAnimatedColor(key)).append('\n');
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Применение и резервная копия (UI-поток)

    // MurkaGram: имя файла темы — только буквы/цифры/пробел/_-() (без «/», «.», «..»), обрезка по кодовым точкам.
    static String safeThemeText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        final String safe = text.replaceAll("[^\\p{L}\\p{N} _()-]", "_").trim();
        if (safe.length() <= maxLength) {
            return safe;
        }
        int end = maxLength;
        if (Character.isLowSurrogate(safe.charAt(end)) && Character.isHighSurrogate(safe.charAt(end - 1))) {
            end--;
        }
        return safe.substring(0, end).trim();
    }

    // «MG <имя> (<тег>)»: тег — хэш имени и цветов, чтобы пакеты с одинаковым именем не перезаписывали тему друг друга.
    static String getThemeName(String packName, String tag) {
        String safe = safeThemeText(packName, 48);
        if (safe.isEmpty()) {
            safe = "Design";
        }
        return "MG " + safe + (tag == null ? "" : " (" + tag + ")");
    }

    static String getThemeTag(String packName, String themeText) { // MurkaGram: dual UI, widened for the classic UI
        try {
            final byte[] hash = Utilities.computeSHA256((packName == null ? "" : packName).getBytes(StandardCharsets.UTF_8), themeText.getBytes(StandardCharsets.ISO_8859_1));
            return Utilities.bytesToHex(hash).substring(0, 8).toLowerCase(Locale.ROOT);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Применяет нормализованный текст темы через временный файл с фиксированным именем; на сервер тема не отправляется.
    private static Theme.ThemeInfo applyThemeText(String text, String themeName) {
        final File tmp = new File(ApplicationLoader.applicationContext.getCacheDir(), "mgdesign_theme.attheme");
        try {
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(text.getBytes(StandardCharsets.ISO_8859_1));
            }
            final Theme.ThemeInfo themeInfo;
            Theme.murkaNoServerSave = true;
            try {
                themeInfo = Theme.applyThemeFile(tmp, themeName, null, false);
            } finally {
                Theme.murkaNoServerSave = false;
            }
            if (themeInfo != null) {
                saveLastDayTheme(themeInfo.getKey());
            }
            return themeInfo;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            try {
                tmp.delete();
            } catch (Throwable ignore) {

            }
        }
    }

    static void saveLastDayTheme(String key) { // MurkaGram: dual UI, widened for the classic UI
        SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE).edit();
        editor.putString("lastDayTheme", key);
        editor.commit();
    }

    static String restartState() { // MurkaGram: dual UI, widened for the classic UI
        return MurkaConfig.md3Design + "|" + MurkaConfig.md3Switches + "|" + MurkaConfig.fontMode + "|" + MurkaConfig.customFontPath
                + "|" + MurkaConfig.emojiMode + "|" + MurkaConfig.customEmojiPath
                + "|" + MurkaConfig.classicHeader // MurkaGram: «Классическая шапка» — с перезапуском
                + "|" + UiEdition.next(); // MurkaGram: dual UI, the interface edition — с перезапуском
    }

    // MurkaGram: ключи шапки и их значения в 13.2.2 (тема «Синяя»). Без classicHeader шапка 13.2.2 рисуется на белом/стекле,
    // а иконки и заголовок берутся из этих ключей — синяя палитра 12.2.10 дала бы белые иконки на белом фоне.
    private static final String[] HEADER_KEYS = new String[]{
        "actionBarDefault", "actionBarDefaultIcon", "actionBarDefaultTitle", "actionBarDefaultSubtitle",
        "actionBarDefaultSelector", "actionBarDefaultSearch", "actionBarDefaultSearchPlaceholder",
        "actionBarDefaultArchived", "actionBarDefaultArchivedIcon", "actionBarDefaultArchivedTitle",
        "actionBarDefaultArchivedSearch", "actionBarDefaultSearchArchivedPlaceholder",
        "actionBarTabActiveText", "actionBarTabUnactiveText", "actionBarTabLine", "actionBarTabSelector",
        "actionBarWhiteSelector", "avatar_actionBarIconBlue", "avatar_actionBarSelectorBlue",
        "avatar_backgroundActionBarBlue", "avatar_backgroundInProfileBlue", "avatar_subtitleInProfileBlue",
        "profile_title", "profile_status", "profile_verifiedBackground", "profile_verifiedCheck",
        "chat_status", "chat_lockIcon", "chat_muteIcon", "telegram_color_dialogsLogo"
    };
    private static final int[] HEADER_VALUES = new int[]{
        0xFFFFFFFF, 0xFF1A1D21, 0xFF1A1D21, 0xFF838C96,
        0xFFF2F2F2, 0xFF1A1D21, 0xFF79817E,
        0xFFFFFFFF, 0xFF1A1D21, 0xFF1A1D21,
        0xFF1A1D21, 0xFF838C96,
        0xFF298ACF, 0xFF777C7F, 0xFF298ACF, 0xFFF2F2F2,
        0xFFF2F2F2, 0xFF1A1D21, 0xFFF2F2F2,
        0xFFF5F5F5, 0xFFFFFFFF, 0xFF1A1D21,
        0xFF222222, 0xFF222222, 0xFF229AF0, 0xFFFFFFFF,
        0xFF229AF0, 0xFF222222, 0xFF79817E, 0xFF168BDB
    };

    // MurkaGram: если светлая тема пакета несёт светлые иконки шапки (классическая синяя шапка), а classicHeader выключен —
    // ключи шапки заменяются значениями 13.2.2. Тёмные темы и обычные светлые темы 13.2.2 не меняются.
    private static String fixHeaderColors(String themeText) {
        if (themeText == null || MurkaConfig.classicHeader) {
            return themeText;
        }
        final LinkedHashMap<String, Integer> colors = new LinkedHashMap<>();
        for (String line : themeText.split("\n")) {
            final int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                colors.put(line.substring(0, eq), Integer.parseInt(line.substring(eq + 1)));
            } catch (NumberFormatException ignore) {

            }
        }
        final Integer windowColor = colors.get("windowBackgroundWhite");
        final Integer iconColor = colors.get("actionBarDefaultIcon");
        final int window = windowColor != null ? windowColor : Theme.getDefaultColor(Theme.key_windowBackgroundWhite);
        final int icon = iconColor != null ? iconColor : Theme.getDefaultColor(Theme.key_actionBarDefaultIcon);
        if (AndroidUtilities.computePerceivedBrightness(window) < 0.7f || AndroidUtilities.computePerceivedBrightness(icon) < 0.7f) {
            return themeText;
        }
        for (int i = 0; i < HEADER_KEYS.length; i++) {
            if (ThemeColors.stringKeyToInt(HEADER_KEYS[i]) >= 0) {
                colors.put(HEADER_KEYS[i], HEADER_VALUES[i]);
            }
        }
        final StringBuilder sb = new StringBuilder(colors.size() * 40);
        for (java.util.Map.Entry<String, Integer> e : colors.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    // MurkaGram: ручное выключение «Классической шапки» — та же замена ключей шапки для текущей светлой темы со светлыми иконками
    // шапки (иначе белые иконки на белой шапке 13.2.2). Тема не отправляется на сервер. true — тема изменена.
    public static boolean fixCurrentHeaderColors() {
        if (MurkaConfig.classicHeader || Theme.isCurrentThemeDark()) {
            return false;
        }
        final String current = currentThemeText();
        final String fixed = fixHeaderColors(current);
        if (fixed == null || fixed == current) {
            return false;
        }
        final Theme.ThemeInfo currentTheme = Theme.getCurrentTheme();
        String name = currentTheme != null ? currentTheme.getName() : null;
        if (name != null) {
            if (name.startsWith("MG ")) {
                name = name.substring(3);
            }
            name = name.replaceFirst(" \\([0-9a-f]{8}\\)$", "");
        }
        // светлая тема становится дневной (saveLastDayTheme), авто-ночь не выключается
        return applyThemeText(fixed, getThemeName(name, getThemeTag(name, fixed))) != null;
    }

    static void afterApply() { // MurkaGram: dual UI, widened for the classic UI
        MurkaMD3.bump();
        LocaleController.getInstance().recreateFormatters();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
    }

    // Применяет пакет (перед этим сохраняет текущий дизайн). Возвращает true, если нужен перезапуск.
    public static boolean apply(MurkaDesignPack pack, BaseFragment fragment) {
        saveBackup();
        final String before = restartState();
        MurkaConfig.applyDesign(pack.design, false);
        final String themeText = fixHeaderColors(pack.themeText); // MurkaGram: без classicHeader — шапка 13.2.2
        if (themeText != null) {
            final Theme.ThemeInfo themeInfo = applyThemeText(themeText, getThemeName(pack.name, getThemeTag(pack.name, themeText)));
            if (themeInfo != null && fragment != null) {
                Theme.turnOffAutoNight(fragment); // иначе при авто-ночи тема может быть не видна
            }
        }
        afterApply();
        return !before.equals(restartState());
    }

    public static boolean hasBackup() {
        try {
            final File dir = getBackupDir();
            return dir != null && new File(dir, "backup.json").isFile();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void saveBackup() {
        try {
            final File dir = getBackupDir();
            if (dir == null) {
                return;
            }
            final JSONObject json = new JSONObject();
            final JSONObject designJson = new JSONObject();
            for (java.util.Map.Entry<String, Object> e : MurkaConfig.getDesignValues(true).entrySet()) {
                designJson.put(e.getKey(), e.getValue());
            }
            json.put("design", designJson);
            final Theme.ThemeInfo theme = Theme.getCurrentTheme();
            if (theme != null) {
                json.put("themeKey", theme.getKey());
                json.put("accentId", theme.currentAccentId);
            }
            json.put("autoNight", Theme.selectedAutoNightType);
            final String colors = currentThemeText();
            final File colorsFile = new File(dir, "backup.attheme");
            if (colors != null) {
                try (OutputStream out = new FileOutputStream(colorsFile)) {
                    out.write(colors.getBytes(StandardCharsets.ISO_8859_1));
                }
            } else {
                colorsFile.delete();
            }
            try (OutputStream out = new FileOutputStream(new File(dir, "backup.json"))) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Возвращает предыдущий дизайн. Возвращает true, если нужен перезапуск.
    public static boolean restoreBackup(BaseFragment fragment) {
        final File dir = getBackupDir();
        if (dir == null) {
            return false;
        }
        final File jsonFile = new File(dir, "backup.json");
        final File colorsFile = new File(dir, "backup.attheme");
        final String before = restartState();
        try {
            if (!jsonFile.isFile() || jsonFile.length() > MAX_DESIGN_SIZE || colorsFile.length() > MAX_THEME_SIZE) {
                return false;
            }
            final JSONObject json = new JSONObject(new String(readFile(jsonFile), StandardCharsets.UTF_8));
            final JSONObject designJson = json.optJSONObject("design");
            if (designJson != null) {
                final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                Iterator<String> keys = designJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    values.put(key, designJson.opt(key));
                }
                MurkaConfig.applyDesign(values, true);
            }

            final String themeKey = json.optString("themeKey", null);
            final int accentId = json.optInt("accentId", -1);
            final boolean ownTheme = themeKey != null && themeKey.startsWith("MG ") && themeKey.endsWith(".attheme");
            // MurkaGram: темы пакетов теперь с тегом в имени и не перезаписываются чужим пакетом — можно применить зарегистрированную
            Theme.ThemeInfo themeInfo = themeKey == null ? null : Theme.getTheme(themeKey);
            boolean themeApplied = false;
            if (themeInfo != null) {
                if (accentId >= 0 && themeInfo.themeAccentsMap != null && themeInfo.themeAccentsMap.get(accentId) != null) {
                    themeInfo.setCurrentAccentId(accentId);
                    Theme.saveThemeAccents(themeInfo, true, false, true, false);
                }
                Theme.murkaNoServerSave = true;
                try {
                    Theme.applyTheme(themeInfo);
                } finally {
                    Theme.murkaNoServerSave = false;
                }
                saveLastDayTheme(themeInfo.getKey());
                themeApplied = true;
            } else if (colorsFile.isFile()) {
                // Тема пропала или это тема из пакета (её файл мог быть перезаписан) — восстановить снимок цветов.
                final MurkaDesignPack snapshot = new MurkaDesignPack();
                snapshot.sanitizeTheme(readFile(colorsFile));
                if (snapshot.themeText != null) {
                    // имя из backup.json снова санитизируется — в путь к файлу темы попадает только безопасный текст
                    final String ownName = ownTheme ? safeThemeText(themeKey.substring(3, themeKey.length() - ".attheme".length()), 64) : "";
                    final String name = ownName.isEmpty() ? getThemeName("Backup", null) : "MG " + ownName;
                    themeApplied = applyThemeText(snapshot.themeText, name) != null;
                }
            }

            final int autoNight = json.optInt("autoNight", Theme.AUTO_NIGHT_TYPE_NONE);
            if (themeApplied && autoNight >= Theme.AUTO_NIGHT_TYPE_SCHEDULED && autoNight <= Theme.AUTO_NIGHT_TYPE_SYSTEM && Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE) {
                Theme.selectedAutoNightType = autoNight;
                Theme.saveAutoNightThemeConfig();
                Theme.checkAutoNightThemeConditions(true);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                jsonFile.delete();
                colorsFile.delete();
            } catch (Throwable ignore) {

            }
        }
        afterApply();
        return !before.equals(restartState());
    }

    static byte[] readFile(File file) throws Exception { // MurkaGram: dual UI, widened for the classic UI
        try (InputStream in = new java.io.FileInputStream(file)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_THEME_SIZE) {
                    throw new PackException(ERROR_TOO_LARGE);
                }
            }
            return out.toByteArray();
        }
    }

    // Перезапуск приложения (общий для «Дизайна» и пакетов дизайна).
    public static void restartApp(Activity activity) {
        try {
            AndroidUtilities.murkaResetTypefaces();
            final Context context = ApplicationLoader.applicationContext;
            final Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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
