package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.RegexLexer;

/**
 * Performance-only pre-check for the Java lexer's method-signature rule (rouge java.rb):
 * <pre>
 *   (\s*(?:[a-zA-Z_][a-zA-Z0-9_.\[\]&lt;&gt;]*\s+)+?)   # group 1, return arguments
 *   ([a-zA-Z_][a-zA-Z0-9_]*)                    # group 2, method name
 *   (\s*)(\()                                   # groups 3, 4
 * </pre>
 * Both engines retry this regex at every lexer position and it scans to the end of the run of
 * identifier/space characters each time, which is quadratic on long runs (TRACKER P3.16). This
 * class decides in O(1) per position (after one O(n) pass over the string) whether the regex can
 * match, so the regex runs only where it succeeds.
 *
 * <p><b>Why the decision is exact.</b> Let S = {@code \s} (ASCII: space \t \n \v \f \r) plus
 * {@code [a-zA-Z0-9_.\[\]<>]}. Every part before {@code \(} matches only S characters and
 * '(' is not in S, so a match from p must end at q = the first index &ge; p holding a non-S
 * character, and needs s[q] == '('. Let T = s[p, q); "words" are maximal runs of non-whitespace
 * characters of T. The leading {@code \s*} must take all leading whitespace (the next element
 * starts with a letter or '_'); each {@code (?:I\s+)} iteration must consume one whole word plus
 * all whitespace after it (I cannot end inside a word because {@code \s+} must follow, and the
 * next element cannot start with whitespace); N plus {@code \s*} must reach q, so N is the whole
 * last word. Hence the regex matches iff, after the leading whitespace, T has at least two words,
 * every word but the last starts with [a-zA-Z_], and the last word matches
 * [a-zA-Z_][a-zA-Z0-9_]* entirely (the lazy loop then stops at exactly that split, so the regex,
 * which still produces the groups, finds it). Checked by {@code JavaSignatureEquivalence} at every
 * position of random and mined inputs.
 */
public final class JavaSignaturePrecheck implements RegexLexer.Precheck {
  static final JavaSignaturePrecheck INSTANCE = new JavaSignaturePrecheck();

  private JavaSignaturePrecheck() {}

  static boolean ws(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r';
  }

  static boolean wordChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '.'
        || c == '[' || c == ']' || c == '<' || c == '>';
  }

  static boolean startChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
  }

  static boolean nameChar(char c) {
    return startChar(c) || (c >= '0' && c <= '9');
  }

  /** Positions precomputed for one string. */
  public static final class Info {
    final String s;
    final int n;
    /** runEnd[i]: first index &ge; i whose char is not in S */
    final int[] runEnd;
    /** nonWs[i]: first index &ge; i whose char is not whitespace */
    final int[] nonWs;
    /** per run end q (s[q] == '('): {L = start of the last word or -1, lastOk, lastBad} */
    final Map<Integer, int[]> runs = new HashMap<>();

    Info(String s) {
      this.s = s;
      n = s.length();
      runEnd = new int[n + 1];
      nonWs = new int[n + 1];
      runEnd[n] = n;
      nonWs[n] = n;
      for (int i = n - 1; i >= 0; i--) {
        char c = s.charAt(i);
        runEnd[i] = ws(c) || wordChar(c) ? runEnd[i + 1] : i;
        nonWs[i] = ws(c) ? nonWs[i + 1] : i;
      }
    }

    int[] analyze(int q) {
      int i = q - 1;
      while (i >= 0 && ws(s.charAt(i))) {
        i--;
      }
      if (i < 0 || !wordChar(s.charAt(i))) {
        return new int[] {-1, 0, -1}; // no word before q in this run
      }
      int end = i + 1;
      while (i >= 0 && wordChar(s.charAt(i))) {
        i--;
      }
      int l = i + 1;
      boolean lastOk = startChar(s.charAt(l));
      for (int k = l + 1; k < end && lastOk; k++) {
        lastOk = nameChar(s.charAt(k));
      }
      // last word start b < L (b preceded by whitespace) whose first char is not [a-zA-Z_]
      int lastBad = -1;
      while (i >= 0 && (ws(s.charAt(i)) || wordChar(s.charAt(i)))) {
        char c = s.charAt(i);
        if (!ws(c) && i > 0 && ws(s.charAt(i - 1)) && !startChar(c) && lastBad < 0) {
          lastBad = i;
        }
        i--;
      }
      return new int[] {l, lastOk ? 1 : 0, lastBad};
    }

    public boolean mayMatch(int p) {
      int q = runEnd[p];
      if (q >= n || s.charAt(q) != '(') {
        return false;
      }
      int[] r = runs.computeIfAbsent(q, this::analyze);
      int p0 = nonWs[p];
      if (r[0] < 0 || p0 >= r[0] || r[1] == 0) {
        return false; // fewer than two words, or the last one is not a name
      }
      if (!startChar(s.charAt(p0))) {
        return false;
      }
      return r[2] <= p0; // no later word (before the last) starting with a non-[a-zA-Z_] char
    }
  }

  private static final Object KEY = new Object();

  @Override
  public boolean mayMatch(String str, int pos, Map<Object, Object> cache) {
    Info info = (Info) cache.get(KEY);
    if (info == null) {
      info = new Info(str);
      cache.put(KEY, info);
    }
    return info.mayMatch(pos);
  }

  /** for the equivalence test */
  public static Info info(String s) {
    return new Info(s);
  }
}
