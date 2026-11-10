package ir.hw2.spell;

import ir.hw2.index.IndexReader;

/**
 * 拼字校正：查詢詞不在詞典時，找編輯距離最近的詞。（工作四）
 *
 * <p>先用長度差過濾候選，再算有上限的 Damerau-Levenshtein 距離；距離相同時取 df 最高者（課本 Sec. 3.3）。
 */
public final class SpellCorrector {

    private final IndexReader index;

    public SpellCorrector(IndexReader index) {
        this.index = index;
    }

    /** @return 建議的詞；找不到（或 term 太短、含非字母）時回傳 null */
    public String suggest(String term) {
        if (term.length() < 3 || !term.chars().allMatch(Character::isLetter)) {
            return null;
        }
        int limit = term.length() <= 4 ? 1 : 2;
        String best = null;
        int bestDistance = limit + 1;
        int bestDf = 0;
        for (String candidate : index.terms()) {
            if (Math.abs(candidate.length() - term.length()) > limit) {
                continue;
            }
            int distance = editDistance(term, candidate, Math.min(limit, bestDistance));
            if (distance > limit) {
                continue;
            }
            int df = index.df(candidate);
            if (distance < bestDistance || (distance == bestDistance
                    && (df > bestDf || (df == bestDf && candidate.compareTo(best) < 0)))) {
                best = candidate;
                bestDistance = distance;
                bestDf = df;
            }
        }
        return best;
    }

    /**
     * Damerau-Levenshtein 距離（相鄰字元換位算一次編輯）；超過 limit 時提早結束並回傳 limit + 1。
     */
    static int editDistance(String a, String b, int limit) {
        int[] beforePrevious = new int[b.length() + 1];
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                int best = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, beforePrevious[j - 2] + 1);
                }
                current[j] = best;
                rowMin = Math.min(rowMin, best);
            }
            // 換位可能讓下一列比這一列小，所以要連續兩列都超過才能提早結束
            if (rowMin > limit + 1) {
                return limit + 1;
            }
            int[] swap = beforePrevious;
            beforePrevious = previous;
            previous = current;
            current = swap;
        }
        return Math.min(previous[b.length()], limit + 1);
    }
}
