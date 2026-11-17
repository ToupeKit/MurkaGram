/*
 * MurkaGram: «Защита от мошенников» — предупреждение, если ссылка или @юзернейм похожи на официальные
 * (NFT-маркеты @mrkt, @portals, @Tonnel_Network_bot и т.п.), но отличаются парой символов.
 *
 * Список официальных юзернеймов = встроенный BUILTIN + Remote Config «murka_safe_usernames»
 * (JSON-массив строк, например ["mrkt", "@portals"]). Все они открываются без предупреждения
 * и служат образцами для сравнения.
 *
 * Точки проверки: LaunchActivity.runLinkRequest (t.me / tg://resolve) и MessagesController.openByUserName (@упоминания).
 */

package org.telegram.messenger;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.widget.TextView;

import org.json.JSONArray;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class MurkaAntiScam {

    public static final String PARAM = "murka_safe_usernames";
    private static final String PREF_KEY = "murkaSafeUsernamesJson";

    private static final String[] BUILTIN = {"main_mrkt_bot", "mrkt", "portals", "Tonnel_Network_bot"};

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,32}");

    private static volatile Set<String> cache;
    private static volatile String rawJson;
    private static final Object sync = new Object();

    public static boolean isEnabled() {
        return MurkaConfig.antiScam;
    }

    private static Set<String> getOfficial() {
        Set<String> set = cache;
        if (set != null) {
            return set;
        }
        synchronized (sync) {
            if (cache == null) {
                String raw = null;
                try {
                    raw = MurkaConfig.getPreferences().getString(PREF_KEY, null);
                } catch (Throwable e) {
                    FileLog.e(e, false);
                }
                List<String> parsed = parse(raw);
                rawJson = raw;
                cache = build(parsed);
            }
            return cache;
        }
    }

    private static Set<String> build(List<String> remote) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        Collections.addAll(set, BUILTIN);
        if (remote != null) {
            set.addAll(remote);
        }
        return Collections.unmodifiableSet(set);
    }

    // null — ошибка разбора или пустое значение
    private static List<String> parse(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        try {
            JSONArray array = new JSONArray(raw);
            ArrayList<String> result = new ArrayList<>();
            for (int a = 0; a < array.length(); a++) {
                String name = array.optString(a, null);
                if (name == null) {
                    continue;
                }
                name = name.trim();
                if (name.startsWith("@")) {
                    name = name.substring(1).trim();
                }
                if (USERNAME.matcher(name).matches()) {
                    result.add(name);
                }
            }
            return result;
        } catch (Exception e) {
            FileLog.e(e, false);
            return null;
        }
    }

    /**
     * Значение из Remote Config. Пустое (параметр не задан / нет сети) — кэш не трогаем, "[]" — только встроенный список.
     */
    public static void applyRemote(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return;
        }
        raw = raw.trim();
        synchronized (sync) {
            getOfficial();
            if (raw.equals(rawJson)) {
                return;
            }
            List<String> parsed = parse(raw);
            if (parsed == null) {
                return;
            }
            rawJson = raw;
            cache = build(parsed);
            try {
                MurkaConfig.getPreferences().edit().putString(PREF_KEY, raw).apply();
            } catch (Throwable e) {
                FileLog.e(e, false);
            }
        }
    }

    /**
     * @return официальный юзернейм (в исходном написании), под который маскируется raw, или null.
     */
    public static String findImpersonated(String raw) {
        try {
            String username = cleanUsername(raw);
            if (username == null) {
                return null;
            }
            String lower = username.toLowerCase(Locale.US);
            if (lower.isEmpty() || Character.isDigit(lower.charAt(0))) {
                return null;
            }
            Set<String> official = getOfficial();
            for (String o : official) {
                if (o.equalsIgnoreCase(lower)) {
                    return null; // сам официальный
                }
            }
            String u = norm(lower);
            if (u.isEmpty()) {
                return null;
            }
            String[] tokens = lower.split("[_0-9]+");

            String best = null;
            int bestScore = Integer.MAX_VALUE;
            for (String o : official) {
                String on = norm(o.toLowerCase(Locale.US));
                if (on.isEmpty()) {
                    continue;
                }
                int score = score(u, on, tokens);
                if (score < bestScore) {
                    bestScore = score;
                    best = o;
                }
            }
            return best;
        } catch (Throwable e) {
            FileLog.e(e, false);
            return null;
        }
    }

    // Integer.MAX_VALUE — не похож; меньше — сильнее похож.
    private static int score(String u, String o, String[] tokens) {
        if (u.equals(o)) {
            return 0;
        }
        int ol = o.length();
        int ul = u.length();
        // до 8 символов — одна правка, иначе две (portable/portraits не должны совпадать с portals)
        int limit = ol <= 8 ? 1 : 2;
        if (Math.abs(ul - ol) <= 2) {
            int d = distance(u, o);
            if (d <= limit) {
                return d;
            }
            // короткие имена: две правки, но только из тех же букв, с той же первой буквой и длиной ±1 (mkrrt → mrkt)
            if (ol <= 8 && d <= 2 && Math.abs(ul - ol) <= 1 && u.charAt(0) == o.charAt(0) && sameLetters(u, o)) {
                return d;
            }
        }
        if (ol >= 6) {
            if (u.contains(o)) {
                return 10;
            }
        } else {
            // mrktbot, getmrkt — короткое имя с небольшой приставкой/хвостом
            if (ol >= 4 && ul - ol <= 4 && (u.startsWith(o) || u.endsWith(o))) {
                return 10;
            }
            for (String token : tokens) {
                if (!token.isEmpty() && norm(token).equals(o)) {
                    return 10;
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private static boolean sameLetters(String a, String b) {
        for (int i = 0; i < a.length(); i++) {
            if (b.indexOf(a.charAt(i)) < 0) {
                return false;
            }
        }
        for (int i = 0; i < b.length(); i++) {
            if (a.indexOf(b.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    // Убирает @, домен и всё после / ? #; null — не похоже на юзернейм.
    private static String cleanUsername(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        while (s.startsWith("@")) {
            s = s.substring(1);
        }
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                cut = i;
                break;
            }
        }
        s = s.substring(0, cut).trim();
        if (s.isEmpty() || s.length() > 64) {
            return null;
        }
        boolean allDigits = true;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                allDigits = false;
                break;
            }
        }
        return allDigits ? null : s;
    }

    // Приводит похожие символы к одному виду: кириллица → латиница, цифры → буквы, rn → m, vv → w, без «_».
    static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            switch (c) {
                case 'а': c = 'a'; break;
                case 'е': c = 'e'; break;
                case 'о': c = 'o'; break;
                case 'р': c = 'p'; break;
                case 'с': c = 'c'; break;
                case 'у': c = 'y'; break;
                case 'х': c = 'x'; break;
                case 'к': c = 'k'; break;
                case 'м': c = 'm'; break;
                case 'т': c = 't'; break;
                case 'н': c = 'h'; break;
                case 'в': c = 'b'; break;
                case 'і': c = 'l'; break;
                case 'ј': c = 'j'; break;
                case 'ѕ': c = 's'; break;
                case 'ԁ': c = 'd'; break;
                case '0': c = 'o'; break;
                case '3': c = 'e'; break;
                case '5': c = 's'; break;
                case '4': c = 'a'; break;
                case '7': c = 't'; break;
                case '8': c = 'b'; break;
                case '1':
                case 'i':
                case 'l':
                case '|': c = 'l'; break;
                case '_': continue;
            }
            b.append(c);
        }
        String r = b.toString();
        if (r.contains("rn")) {
            r = r.replace("rn", "m");
        }
        if (r.contains("vv")) {
            r = r.replace("vv", "w");
        }
        return r;
    }

    // Расстояние Дамерау-Левенштейна (optimal string alignment).
    static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
            }
        }
        return d[n][m];
    }

    /**
     * Диалог-предупреждение. openOfficial — «Перейти к @official», proceed — «Всё равно перейти».
     */
    public static void showWarning(BaseFragment fragment, String official, String clicked, Runnable openOfficial, Runnable proceed) {
        android.content.Context context = fragment != null ? fragment.getParentActivity() : null;
        if (context == null) {
            context = LaunchActivity.instance;
        }
        if (context == null) {
            if (proceed != null) {
                proceed.run();
            }
            return;
        }
        AlertDialog dialog;
        try {
            String shown = cleanUsername(clicked);
            if (shown == null) {
                shown = clicked != null ? clicked : "";
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment != null ? fragment.getResourceProvider() : null);
            builder.setTitle(LocaleController.getString(R.string.MurkaAntiScamTitle));
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MurkaAntiScamText, official, shown)));
            builder.setPositiveButton(LocaleController.formatString(R.string.MurkaAntiScamOpenOfficial, official), (d, which) -> {
                if (openOfficial != null) {
                    openOfficial.run();
                }
            });
            builder.setNeutralButton(LocaleController.getString(R.string.MurkaAntiScamOpenAnyway), (d, which) -> {
                if (proceed != null) {
                    proceed.run();
                }
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            dialog = builder.create();
            boolean shownInFragment = fragment != null && fragment.getParentActivity() != null && fragment.showDialog(dialog) != null;
            if (!shownInFragment) {
                dialog.show(); // фрагмент в анимации перехода или его нет
            }
        } catch (Exception e) {
            // MurkaGram: предупреждение показать не удалось — ссылку не блокируем, открываем как в стоке
            FileLog.e(e);
            if (proceed != null) {
                proceed.run();
            }
            return;
        }
        try {
            if (dialog.getButton(DialogInterface.BUTTON_NEUTRAL) instanceof TextView) {
                ((TextView) dialog.getButton(DialogInterface.BUTTON_NEUTRAL)).setTextColor(Theme.getColor(Theme.key_text_RedBold, fragment != null ? fragment.getResourceProvider() : null));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
