/*
 * MurkaGram: посимвольное сравнение двух версий текста для «Истории изменений».
 * Возвращает диапазоны [start, end) в новой версии, которые были вставлены или заменены.
 * Чистая логика без Android-зависимостей; вызывать не на UI-потоке (на длинных текстах — до ~1.5M операций).
 */

package org.telegram.messenger;

import java.util.ArrayList;

public class MurkaTextDiff {

    // Ограничение на размер таблицы LCS (n * m) — ~3 МБ short-ов.
    private static final int MAX_CELLS = 1_500_000;
    // Сколько токенов максимум сравниваем на уровне слов.
    private static final int MAX_TOKENS = 3000;

    public static ArrayList<int[]> changedRanges(String prev, String cur) {
        ArrayList<int[]> result = new ArrayList<>();
        if (cur == null || cur.isEmpty()) {
            return result;
        }
        if (prev == null) {
            prev = "";
        }
        if (prev.equals(cur)) {
            return result;
        }
        int prevLen = prev.length();
        int curLen = cur.length();

        // 1. Общие префикс и суффикс.
        int prefix = 0;
        int maxPrefix = Math.min(prevLen, curLen);
        while (prefix < maxPrefix && prev.charAt(prefix) == cur.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        int maxSuffix = Math.min(prevLen, curLen) - prefix;
        while (suffix < maxSuffix && prev.charAt(prevLen - 1 - suffix) == cur.charAt(curLen - 1 - suffix)) {
            suffix++;
        }
        int aStart = prefix, aEnd = prevLen - suffix;
        int bStart = prefix, bEnd = curLen - suffix;

        ArrayList<int[]> raw = new ArrayList<>();
        if (bStart < bEnd) {
            if (aStart >= aEnd) {
                // Чистая вставка.
                raw.add(new int[]{bStart, bEnd});
            } else {
                int n = aEnd - aStart;
                int m = bEnd - bStart;
                if ((long) n * m <= MAX_CELLS) {
                    charDiff(prev, aStart, aEnd, cur, bStart, bEnd, raw);
                } else if (!wordDiff(prev.substring(aStart, aEnd), cur.substring(bStart, bEnd), bStart, raw)) {
                    raw.add(new int[]{bStart, bEnd});
                }
            }
        }

        // 2. Не разрываем суррогатные пары, затем склеиваем соседние диапазоны.
        for (int a = 0; a < raw.size(); a++) {
            int[] r = raw.get(a);
            if (r[0] > 0 && r[0] < curLen && Character.isLowSurrogate(cur.charAt(r[0])) && Character.isHighSurrogate(cur.charAt(r[0] - 1))) {
                r[0]--;
            }
            if (r[1] > 0 && r[1] < curLen && Character.isHighSurrogate(cur.charAt(r[1] - 1)) && Character.isLowSurrogate(cur.charAt(r[1]))) {
                r[1]++;
            }
        }
        int[] last = null;
        for (int a = 0; a < raw.size(); a++) {
            int[] r = raw.get(a);
            if (r[1] <= r[0]) {
                continue;
            }
            if (last != null && r[0] <= last[1]) {
                last[1] = Math.max(last[1], r[1]);
            } else {
                last = new int[]{r[0], r[1]};
                result.add(last);
            }
        }

        // 3. Выкидываем диапазоны только из пробелов.
        for (int a = result.size() - 1; a >= 0; a--) {
            int[] r = result.get(a);
            boolean blank = true;
            for (int i = r[0]; i < r[1]; i++) {
                if (!Character.isWhitespace(cur.charAt(i))) {
                    blank = false;
                    break;
                }
            }
            if (blank) {
                result.remove(a);
            }
        }
        return result;
    }

    // LCS по символам на отрезках a[aStart, aEnd) и b[bStart, bEnd); добавляет изменённые позиции b.
    private static void charDiff(String a, int aStart, int aEnd, String b, int bStart, int bEnd, ArrayList<int[]> out) {
        int n = aEnd - aStart;
        int m = bEnd - bStart;
        int w = m + 1;
        // dp[i * w + j] = длина LCS суффиксов a[i..], b[j..]; min(n, m) <= 1224, поэтому short хватает.
        short[] dp = new short[(n + 1) * w];
        for (int i = n - 1; i >= 0; i--) {
            char ca = a.charAt(aStart + i);
            for (int j = m - 1; j >= 0; j--) {
                if (ca == b.charAt(bStart + j)) {
                    dp[i * w + j] = (short) (dp[(i + 1) * w + j + 1] + 1);
                } else {
                    short down = dp[(i + 1) * w + j];
                    short right = dp[i * w + j + 1];
                    dp[i * w + j] = down >= right ? down : right;
                }
            }
        }
        int i = 0, j = 0;
        int runStart = -1;
        while (j < m) {
            boolean changed;
            if (i < n && a.charAt(aStart + i) == b.charAt(bStart + j)) {
                i++;
                j++;
                changed = false;
            } else if (i < n && dp[(i + 1) * w + j] >= dp[i * w + j + 1]) {
                i++;
                continue;
            } else {
                j++;
                changed = true;
            }
            if (changed) {
                if (runStart < 0) {
                    runStart = bStart + j - 1;
                }
            } else if (runStart >= 0) {
                out.add(new int[]{runStart, bStart + j - 1});
                runStart = -1;
            }
        }
        if (runStart >= 0) {
            out.add(new int[]{runStart, bEnd});
        }
    }

    // LCS по словам (буквенно-цифровые серии и одиночные прочие символы). false — слишком большой текст.
    private static boolean wordDiff(String a, String b, int bOffset, ArrayList<int[]> out) {
        ArrayList<String> ta = new ArrayList<>();
        tokenize(a, ta, null);
        ArrayList<String> tb = new ArrayList<>();
        ArrayList<Integer> tbPos = new ArrayList<>();
        tokenize(b, tb, tbPos);
        int n = ta.size();
        int m = tb.size();
        if (n > MAX_TOKENS || m > MAX_TOKENS || (long) n * m > MAX_CELLS) {
            return false;
        }
        int w = m + 1;
        short[] dp = new short[(n + 1) * w];
        for (int i = n - 1; i >= 0; i--) {
            String sa = ta.get(i);
            for (int j = m - 1; j >= 0; j--) {
                if (sa.equals(tb.get(j))) {
                    dp[i * w + j] = (short) (dp[(i + 1) * w + j + 1] + 1);
                } else {
                    short down = dp[(i + 1) * w + j];
                    short right = dp[i * w + j + 1];
                    dp[i * w + j] = down >= right ? down : right;
                }
            }
        }
        int i = 0, j = 0;
        while (j < m) {
            if (i < n && ta.get(i).equals(tb.get(j))) {
                i++;
                j++;
            } else if (i < n && dp[(i + 1) * w + j] >= dp[i * w + j + 1]) {
                i++;
            } else {
                int start = bOffset + tbPos.get(j);
                out.add(new int[]{start, start + tb.get(j).length()});
                j++;
            }
        }
        return true;
    }

    private static void tokenize(String s, ArrayList<String> tokens, ArrayList<Integer> positions) {
        int len = s.length();
        int i = 0;
        while (i < len) {
            int start = i;
            if (Character.isLetterOrDigit(s.charAt(i))) {
                while (i < len && Character.isLetterOrDigit(s.charAt(i))) {
                    i++;
                }
            } else {
                i++;
            }
            tokens.add(s.substring(start, i));
            if (positions != null) {
                positions.add(start);
            }
        }
    }
}
