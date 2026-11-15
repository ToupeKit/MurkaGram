/*
 * MurkaGram: «Бейджики» — значки у имени пользователя, канала или группы.
 * Список выдаётся ТОЛЬКО через Firebase Remote Config (параметр murka_badges, JSON {"<id>":["cat_badge","cat_crown"]}),
 * ключи: "123" — пользователь, "-1001234567890" — канал/супергруппа (формат Bot API), "-123" — обычная группа.
 * Внутри хранится dialogId в формате Telegram Android (+userId, -chatId).
 * Кэшируется в SharedPreferences "murkaconfig", поэтому виден и без сети.
 * Для своих аккаунтов учитываются порядок и скрытие из MurkaConfig (экран «Бейджики»); они же выгружаются
 * в Firebase Realtime Database (/badgePrefs/<userId>), чтобы другие видели тот же порядок/скрытие.
 * Общие префы только фильтруют и упорядочивают выданное конфигом — выдать бейджик через базу нельзя.
 */

package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MurkaBadges {

    public static final String PARAM = "murka_badges";
    private static final int MAX_BADGES = 5;

    // реестр известных бейджиков; неизвестные id из конфига игнорируются
    public static final String[] IDS = {"cat_badge", "cat_crown", "cat_designer"}; // новые id — только в конец (стабильные id пунктов в MurkaBadgesActivity)

    public static int getIcon(String id) {
        switch (id) {
            case "cat_badge": return R.drawable.msg_murka_badge_cat;
            case "cat_crown": return R.drawable.msg_murka_badge_crown;
            case "cat_designer": return R.drawable.msg_murka_badge_designer;
            default: return 0;
        }
    }

    public static int getName(String id) {
        switch (id) {
            case "cat_badge": return R.string.MurkaBadgeCat;
            case "cat_crown": return R.string.MurkaBadgeCrown;
            case "cat_designer": return R.string.MurkaBadgeDesigner;
            default: return 0;
        }
    }

    public static int getDescription(String id) {
        switch (id) {
            case "cat_badge": return R.string.MurkaBadgeCatDesc;
            case "cat_crown": return R.string.MurkaBadgeCrownDesc;
            case "cat_designer": return R.string.MurkaBadgeDesignerDesc;
            default: return 0;
        }
    }

    public static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    // неизменяемая карта (copy-on-write), публикуется через volatile
    private static volatile Map<Long, List<String>> map = Collections.emptyMap();
    private static volatile String rawJson;
    private static volatile boolean loaded;
    private static final Object sync = new Object();

    // увеличивается при каждом изменении (конфиг или порядок/скрытие) — ячейки по нему понимают, что пора пересобраться
    public static volatile int version;

    public static void init() {
        if (loaded) {
            return;
        }
        synchronized (sync) {
            if (loaded || ApplicationLoader.applicationContext == null) {
                return;
            }
            try {
                String raw = MurkaConfig.getPreferences().getString("murkaBadgesJson", null);
                Map<Long, List<String>> parsed = parse(raw);
                if (parsed != null) {
                    map = parsed;
                    rawJson = raw;
                }
                String sharedJson = MurkaConfig.getPreferences().getString("murkaBadgePrefsJson", null);
                Map<Long, SharedPrefs> parsedShared = parseShared(sharedJson);
                if (parsedShared != null) {
                    shared = parsedShared;
                    sharedRaw = sharedJson;
                }
                dbUrl = sanitizeDbUrl(MurkaConfig.getPreferences().getString("murkaBadgesDbUrl", null));
            } catch (Throwable e) {
                FileLog.e(e, false);
            }
            loaded = true;
        }
    }

    public static void fetch() {
        init();
        try {
            FirebaseRemoteConfig rc = FirebaseRemoteConfig.getInstance();
            apply(rc.getString(PARAM)); // значение, активированное раньше (если есть)
            applyDbUrl(rc.getString(PARAM_DB));
            fetchSharedPrefs(false);
            uploadPending();
            // глобальные настройки Remote Config не трогаем: инстанс общий с ConnectionsManager
            rc.fetch(BuildVars.DEBUG_VERSION ? 60 : 3600).addOnCompleteListener(task -> {
                try {
                    if (!task.isSuccessful()) {
                        return;
                    }
                    MurkaConfig.getPreferences().edit().putLong("murkaBadgesFetchTime", System.currentTimeMillis()).apply();
                    // activate() может вернуть false (уже активировано) — это не ошибка, значение всё равно читаем
                    rc.activate().addOnCompleteListener(task2 -> Utilities.globalQueue.postRunnable(() -> {
                        try {
                            apply(rc.getString(PARAM));
                            applyDbUrl(rc.getString(PARAM_DB));
                            fetchSharedPrefs(false);
                            uploadPending();
                        } catch (Throwable e) {
                            FileLog.e(e, false);
                        }
                    }));
                } catch (Throwable e) {
                    FileLog.e(e, false);
                }
            });
        } catch (Throwable e) {
            FileLog.e(e, false);
        }
    }

    // повторная загрузка при возврате в приложение, если с прошлой успешной прошло больше интервала
    private static long lastFetchRequest;

    public static void fetchIfNeeded() {
        long now = System.currentTimeMillis();
        long interval = (BuildVars.DEBUG_VERSION ? 60 : 3600) * 1000L;
        if (lastFetchRequest == 0) {
            lastFetchRequest = now; // при запуске загрузку уже запускает ApplicationLoader
            return;
        }
        // общие префы обновляются чаще конфига (внутри своё ограничение по времени)
        fetchSharedPrefs(false);
        uploadPending();
        if (now - lastFetchRequest < interval) {
            return;
        }
        lastFetchRequest = now;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (ApplicationLoader.applicationContext == null) {
                    return;
                }
                long last = MurkaConfig.getPreferences().getLong("murkaBadgesFetchTime", 0);
                if (Math.abs(System.currentTimeMillis() - last) >= interval) {
                    fetch();
                }
            } catch (Throwable e) {
                FileLog.e(e, false);
            }
        });
    }

    private static void apply(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return; // параметр не задан или нет сети — кэш не трогаем
        }
        synchronized (sync) {
            if (raw.equals(rawJson)) {
                return;
            }
            Map<Long, List<String>> parsed = parse(raw);
            if (parsed == null) {
                return;
            }
            map = parsed;
            rawJson = raw;
            MurkaConfig.getPreferences().edit().putString("murkaBadgesJson", raw).apply();
            version++;
        }
        AndroidUtilities.runOnUIThread(MurkaBadges::notifyChanged);
    }

    // null — ошибка разбора (оставляем старую карту)
    private static Map<Long, List<String>> parse(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(raw);
            HashMap<Long, List<String>> result = new HashMap<>();
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                long dialogId;
                try {
                    dialogId = Long.parseLong(key.trim());
                } catch (Exception e) {
                    continue;
                }
                if (dialogId == 0) {
                    continue;
                }
                if (dialogId <= -1000000000000L) {
                    dialogId = -(-dialogId - 1000000000000L); // -100XXXXXXXXXX (Bot API) -> -XXXXXXXXXX (dialogId Android)
                    if (dialogId == 0) {
                        continue;
                    }
                }
                ArrayList<String> ids = new ArrayList<>();
                Object value = json.opt(key);
                if (value instanceof JSONArray) {
                    JSONArray array = (JSONArray) value;
                    for (int i = 0; i < array.length(); i++) {
                        addId(ids, array.optString(i, null));
                    }
                } else if (value instanceof String) {
                    addId(ids, (String) value);
                }
                if (!ids.isEmpty()) {
                    result.put(dialogId, Collections.unmodifiableList(ids));
                }
            }
            return Collections.unmodifiableMap(result);
        } catch (Throwable e) {
            FileLog.e(e, false);
            return null;
        }
    }

    private static void addId(ArrayList<String> ids, String id) {
        if (id == null) {
            return;
        }
        id = id.trim();
        if (ids.size() < MAX_BADGES && indexOf(id) >= 0 && !ids.contains(id)) {
            ids.add(id);
        }
    }

    // вызывается из MurkaConfig после изменения порядка/скрытия
    static void onPrefsChanged(int account) {
        synchronized (sync) {
            version++;
        }
        AndroidUtilities.runOnUIThread(MurkaBadges::notifyChanged);
        uploadPrefs(account);
    }

    public static void notifyChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.murkaBadgesUpdated);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_EMOJI_STATUS);
            }
        }
    }

    // бейджики из конфига, в исходном порядке (dialogId: +userId или -chatId)
    public static List<String> getConfigBadges(long dialogId) {
        init();
        if (dialogId == 0) {
            return Collections.emptyList();
        }
        List<String> list = map.get(dialogId);
        return list == null ? Collections.emptyList() : list;
    }

    // пользователь — с учётом порядка/скрытия; чат/канал — порядок конфига
    public static List<String> getBadgesForDialog(long dialogId) {
        if (dialogId > 0) {
            return getBadges(dialogId);
        }
        return getConfigBadges(dialogId);
    }

    // бейджики пользователя с учётом порядка/скрытия: свои аккаунты — локальные префы, остальные — общие из базы
    public static List<String> getBadges(long userId) {
        List<String> config = getConfigBadges(userId);
        if (config.isEmpty()) {
            return config;
        }
        int account = -1;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig userConfig = UserConfig.getInstance(a);
            if (userConfig.isClientActivated() && userConfig.getClientUserId() == userId) {
                account = a;
                break;
            }
        }
        if (account < 0) {
            SharedPrefs p = shared.get(userId);
            if (p == null) {
                return config;
            }
            ArrayList<String> result = new ArrayList<>();
            for (String id : orderBy(p.order, config)) {
                if (!p.hidden.contains(id)) {
                    result.add(id);
                }
            }
            return result;
        }
        ArrayList<String> result = new ArrayList<>();
        for (String id : getOrderedBadges(account, config)) {
            if (!MurkaConfig.isBadgeHidden(account, id)) {
                result.add(id);
            }
        }
        return result;
    }

    // порядок для своего аккаунта, включая скрытые: сначала сохранённый порядок, затем новые из конфига
    public static ArrayList<String> getOrderedBadges(int account, List<String> config) {
        return orderBy(MurkaConfig.getBadgeOrder(account), config);
    }

    // сначала id из order, которые есть в config, затем остальные из config в порядке конфига
    private static ArrayList<String> orderBy(List<String> order, List<String> config) {
        ArrayList<String> result = new ArrayList<>();
        for (String id : order) {
            if (config.contains(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        for (String id : config) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    // null, если бейджиков нет; reuse — прежний экземпляр, возвращается, если набор не изменился
    public static BadgesDrawable getDrawable(long dialogId, int sizeDp, Drawable reuse) {
        List<String> ids = getBadgesForDialog(dialogId);
        if (ids.isEmpty()) {
            return null;
        }
        String key = TextUtils.join(",", ids);
        if (reuse instanceof BadgesDrawable) {
            BadgesDrawable prev = (BadgesDrawable) reuse;
            if (prev.sizeDp == sizeDp && key.equals(prev.key)) {
                return prev;
            }
        }
        return new BadgesDrawable(ids, key, sizeDp);
    }

    // ---------------------------------------------------------------------
    // Описание по нажатию

    public static void showBadgeInfo(BaseFragment fragment, Drawable badges, int index) {
        try {
            if (!(badges instanceof BadgesDrawable) || ((BadgesDrawable) badges).getCount() == 0) {
                return;
            }
            BadgesDrawable drawable = (BadgesDrawable) badges;
            BulletinFactory factory = BulletinFactory.canShowBulletin(fragment) ? BulletinFactory.of(fragment) : BulletinFactory.global(); // MurkaGram: фрагмент без контейнера (закрыт) — глобальный Bulletin
            if (factory == null) {
                return;
            }
            if (index < 0 || index >= drawable.getCount()) {
                index = 0; // запасной вариант — первый бейджик
            }
            String id = drawable.getIdAt(index);
            int name = getName(id);
            int desc = getDescription(id);
            if (name == 0) {
                return;
            }
            BadgesDrawable icon = createSingle(id, 28);
            icon.setColor(Theme.getColor(Theme.key_undo_infoColor, fragment != null ? fragment.getResourceProvider() : null));
            factory.createSimpleBulletin(icon, LocaleController.getString(name), desc != 0 ? LocaleController.getString(desc) : "").show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---------------------------------------------------------------------
    // Общие префы (порядок/скрытие) через Firebase Realtime Database (REST)

    public static final String PARAM_DB = "murka_badges_db";
    private static final String DEFAULT_DB = "https://murkagramtg-default-rtdb.europe-west1.firebasedatabase.app";
    private static final int MAX_RESPONSE = 512 * 1024;
    private static final int MAX_PREF_LENGTH = 255;

    private static final class SharedPrefs {
        final List<String> order;
        final Set<String> hidden;

        SharedPrefs(List<String> order, Set<String> hidden) {
            this.order = order;
            this.hidden = hidden;
        }
    }

    // неизменяемая карта userId -> префы (copy-on-write)
    private static volatile Map<Long, SharedPrefs> shared = Collections.emptyMap();
    private static volatile String sharedRaw;
    private static volatile String dbUrl;
    private static volatile long lastPrefsFetch;
    private static volatile DispatchQueue netQueue;
    private static final Runnable[] uploadRunnables = new Runnable[UserConfig.MAX_ACCOUNT_COUNT];

    // своя очередь для сети — globalQueue блокировать нельзя; создаётся лениво (поток стартует в конструкторе)
    private static DispatchQueue getNetQueue() {
        if (netQueue == null) {
            synchronized (sync) {
                if (netQueue == null) {
                    netQueue = new DispatchQueue("murkaBadgesNet");
                }
            }
        }
        return netQueue;
    }

    private static String sanitizeDbUrl(String url) {
        if (url == null) {
            return null;
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("https://") || url.length() <= "https://".length() || url.contains("?") || url.contains("#")) {
            return null;
        }
        return url;
    }

    private static void applyDbUrl(String value) {
        String url = sanitizeDbUrl(value);
        if (url == null || url.equals(dbUrl)) {
            return;
        }
        dbUrl = url;
        MurkaConfig.getPreferences().edit().putString("murkaBadgesDbUrl", url).apply();
    }

    private static String getDbUrl() {
        String url = dbUrl;
        return url != null ? url : DEFAULT_DB;
    }

    private static boolean isEnabledAccount(int account) {
        return account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT && UserConfig.getInstance(account).isClientActivated();
    }

    public static void fetchSharedPrefs(boolean force) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        long interval = (BuildVars.DEBUG_VERSION ? 60 : 600) * 1000L;
        long now = System.currentTimeMillis();
        if (!force && Math.abs(now - lastPrefsFetch) < interval) {
            return;
        }
        lastPrefsFetch = now;
        getNetQueue().postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                init();
                if (!force) {
                    long last = MurkaConfig.getPreferences().getLong("murkaBadgePrefsFetchTime", 0);
                    if (Math.abs(System.currentTimeMillis() - last) < interval) {
                        return;
                    }
                }
                connection = (HttpURLConnection) new URL(getDbUrl() + "/badgePrefs.json").openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() != 200) {
                    return;
                }
                String body = readLimited(connection.getInputStream());
                if (body == null) {
                    return;
                }
                applyShared(body.trim());
            } catch (Throwable e) {
                FileLog.e(e, false);
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Throwable ignore) {
                    }
                }
            }
        });
    }

    // null — ответ больше лимита
    private static String readLimited(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > MAX_RESPONSE) {
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try {
                in.close();
            } catch (Throwable ignore) {
            }
        }
    }

    private static void applyShared(String raw) {
        Map<Long, SharedPrefs> parsed = parseShared(raw);
        if (parsed == null) {
            return; // ошибка разбора — кэш не трогаем
        }
        boolean changed;
        synchronized (sync) {
            MurkaConfig.getPreferences().edit().putLong("murkaBadgePrefsFetchTime", System.currentTimeMillis()).apply();
            changed = !raw.equals(sharedRaw);
            if (changed) {
                shared = parsed;
                sharedRaw = raw;
                MurkaConfig.getPreferences().edit().putString("murkaBadgePrefsJson", raw).apply();
                version++;
            }
        }
        if (changed) {
            restoreOwnPrefs(parsed);
            AndroidUtilities.runOnUIThread(MurkaBadges::notifyChanged);
        }
    }

    // после переустановки: локальных префов нет, а в базе есть — тихо восстановить без выгрузки
    private static void restoreOwnPrefs(Map<Long, SharedPrefs> parsed) {
        try {
            android.content.SharedPreferences prefs = MurkaConfig.getPreferences();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (!isEnabledAccount(a)) {
                    continue;
                }
                long userId = UserConfig.getInstance(a).getClientUserId();
                SharedPrefs p = parsed.get(userId);
                if (p == null || prefs.contains("badgeOrder" + a) || prefs.contains("badgeHidden" + a) || prefs.getBoolean("badgePrefsDirty" + a, false)) {
                    continue;
                }
                MurkaConfig.restoreBadgePrefs(a, p.order, p.hidden);
            }
        } catch (Throwable e) {
            FileLog.e(e, false);
        }
    }

    // null — ошибка разбора; "null" от базы — пустая карта
    private static Map<Long, SharedPrefs> parseShared(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        if ("null".equals(raw.trim())) {
            return Collections.emptyMap();
        }
        try {
            JSONObject json = new JSONObject(raw);
            HashMap<Long, SharedPrefs> result = new HashMap<>();
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key == null || key.length() > 20) {
                    continue;
                }
                long userId;
                try {
                    userId = Long.parseLong(key.trim());
                } catch (Exception e) {
                    continue;
                }
                if (userId <= 0) {
                    continue;
                }
                JSONObject value = json.optJSONObject(key);
                if (value == null) {
                    continue;
                }
                Object order = value.opt("order");
                Object hidden = value.opt("hidden");
                List<String> orderList = Collections.unmodifiableList(splitKnownIds(order instanceof String ? (String) order : null));
                Set<String> hiddenSet = Collections.unmodifiableSet(new HashSet<>(splitKnownIds(hidden instanceof String ? (String) hidden : null)));
                if (orderList.isEmpty() && hiddenSet.isEmpty()) {
                    continue;
                }
                result.put(userId, new SharedPrefs(orderList, hiddenSet));
            }
            return Collections.unmodifiableMap(result);
        } catch (Throwable e) {
            FileLog.e(e, false);
            return null;
        }
    }

    // только известные id, без повторов, с ограничением длины
    private static ArrayList<String> splitKnownIds(String value) {
        ArrayList<String> list = new ArrayList<>();
        if (value == null || value.length() > MAX_PREF_LENGTH) {
            return list;
        }
        for (String s : value.split(",")) {
            s = s.trim();
            if (indexOf(s) >= 0 && !list.contains(s) && list.size() < IDS.length) {
                list.add(s);
            }
        }
        return list;
    }

    // выгрузить порядок/скрытие своего аккаунта (с задержкой: при перетаскивании вызывается часто)
    static void uploadPrefs(int account) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT || ApplicationLoader.applicationContext == null) {
            return;
        }
        DispatchQueue queue = getNetQueue();
        Runnable runnable;
        synchronized (uploadRunnables) {
            // MurkaGram: флаг ставим под тем же замком, что и проверку в doUploadPrefs, иначе завершившийся запрос мог сбросить его до появления нового runnable
            try {
                MurkaConfig.getPreferences().edit().putBoolean("badgePrefsDirty" + account, true).apply();
            } catch (Throwable e) {
                FileLog.e(e, false);
            }
            if (uploadRunnables[account] != null) {
                queue.cancelRunnable(uploadRunnables[account]);
            }
            runnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (uploadRunnables) {
                        if (uploadRunnables[account] == this) {
                            uploadRunnables[account] = null;
                        }
                    }
                    doUploadPrefs(account);
                }
            };
            uploadRunnables[account] = runnable;
        }
        queue.postRunnable(runnable, 1500);
    }

    private static void doUploadPrefs(int account) {
        HttpURLConnection connection = null;
        try {
            if (!isEnabledAccount(account)) {
                return;
            }
            long userId = UserConfig.getInstance(account).getClientUserId();
            if (userId <= 0) {
                return;
            }
            ArrayList<String> order = new ArrayList<>();
            for (String id : MurkaConfig.getBadgeOrder(account)) {
                if (indexOf(id) >= 0 && !order.contains(id)) {
                    order.add(id);
                }
            }
            ArrayList<String> hidden = new ArrayList<>();
            for (String id : IDS) {
                if (MurkaConfig.isBadgeHidden(account, id)) {
                    hidden.add(id);
                }
            }
            String orderStr = TextUtils.join(",", order);
            String hiddenStr = TextUtils.join(",", hidden);
            JSONObject body = new JSONObject();
            body.put("order", orderStr);
            body.put("hidden", hiddenStr);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

            connection = (HttpURLConnection) new URL(getDbUrl() + "/badgePrefs/" + userId + ".json").openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(false);
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            if (connection.getResponseCode() != 200) {
                return;
            }
            synchronized (uploadRunnables) {
                if (uploadRunnables[account] == null) { // за время запроса не появилось новых изменений
                    MurkaConfig.getPreferences().edit().putBoolean("badgePrefsDirty" + account, false).apply();
                }
            }
            // своя запись в общей карте (без notify — свой аккаунт рендерится из локальных префов)
            synchronized (sync) {
                HashMap<Long, SharedPrefs> copy = new HashMap<>(shared);
                copy.put(userId, new SharedPrefs(Collections.unmodifiableList(splitKnownIds(orderStr)), Collections.unmodifiableSet(new HashSet<>(splitKnownIds(hiddenStr)))));
                shared = Collections.unmodifiableMap(copy);
            }
        } catch (Throwable e) {
            FileLog.e(e, false);
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    // повторить незавершённые выгрузки
    private static void uploadPending() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return;
            }
            android.content.SharedPreferences prefs = MurkaConfig.getPreferences();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (isEnabledAccount(a) && prefs.getBoolean("badgePrefsDirty" + a, false)) {
                    uploadPrefs(a);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e, false);
        }
    }

    // иконка одного бейджика (для Bulletin)
    public static BadgesDrawable createSingle(String id, int sizeDp) {
        return new BadgesDrawable(Collections.singletonList(id), id, sizeDp);
    }

    public static class BadgesDrawable extends Drawable {

        private final Drawable[] icons;
        private final String[] ids; // параллельно icons
        public final String key;
        private final int sizeDp;
        private final int size;
        private final int spacing;
        private int color = 0xffffffff;
        private int alpha = 255;

        private BadgesDrawable(List<String> ids, String key, int sizeDp) {
            this.key = key;
            this.sizeDp = sizeDp;
            size = AndroidUtilities.dp(sizeDp);
            spacing = AndroidUtilities.dp(2);
            ArrayList<Drawable> list = new ArrayList<>();
            ArrayList<String> idList = new ArrayList<>();
            for (String id : ids) {
                int icon = getIcon(id);
                Drawable d = icon == 0 ? null : ContextCompat.getDrawable(ApplicationLoader.applicationContext, icon);
                if (d != null) {
                    d = d.mutate();
                    d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                    list.add(d);
                    idList.add(id);
                }
            }
            icons = list.toArray(new Drawable[0]);
            this.ids = idList.toArray(new String[0]);
        }

        public int getCount() {
            return icons.length;
        }

        public String getIdAt(int index) {
            return index >= 0 && index < ids.length ? ids[index] : null;
        }

        // индекс иконки по x внутри нарисованной ширины (промежуток относится к левой иконке); -1 — мимо
        public int indexAt(float x, int drawnWidth) {
            int n = icons.length;
            int intrinsic = getIntrinsicWidth();
            if (n == 0 || intrinsic <= 0 || drawnWidth <= 0 || x < 0 || x > drawnWidth) {
                return -1;
            }
            float scale = drawnWidth / (float) intrinsic;
            float step = (size + spacing) * scale;
            int idx = step > 0 ? (int) (x / step) : 0;
            return Math.max(0, Math.min(n - 1, idx));
        }

        public void setColor(int color) {
            if (this.color == color) {
                return;
            }
            this.color = color;
            for (Drawable d : icons) {
                d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (icons.length == 0) {
                return;
            }
            Rect bounds = getBounds();
            // SimpleTextView масштабирует bounds — опираемся на них, а не на intrinsic-размеры
            float scale = getIntrinsicWidth() > 0 && bounds.width() > 0 ? bounds.width() / (float) getIntrinsicWidth() : 1f;
            int s = Math.round(size * scale);
            int sp = Math.round(spacing * scale);
            int x = bounds.left;
            int top = bounds.centerY() - s / 2;
            for (Drawable d : icons) {
                int w = d.getIntrinsicWidth();
                int h = d.getIntrinsicHeight();
                // вписываем в квадрат s x s с сохранением пропорций
                int dw = s, dh = s;
                if (w > 0 && h > 0) {
                    if (w >= h) {
                        dh = Math.round(s * h / (float) w);
                    } else {
                        dw = Math.round(s * w / (float) h);
                    }
                }
                int l = x + (s - dw) / 2;
                int t = top + (s - dh) / 2;
                d.setBounds(l, t, l + dw, t + dh);
                d.setAlpha(alpha);
                d.draw(canvas);
                x += s + sp;
            }
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public int getAlpha() {
            return alpha;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return icons.length == 0 ? 0 : icons.length * size + (icons.length - 1) * spacing;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }
    }
}
