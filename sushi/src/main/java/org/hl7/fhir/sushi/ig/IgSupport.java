package org.hl7.fhir.sushi.ig;

import java.math.BigInteger;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.js.Js;

/**
 * Small npm-library equivalents IGExporter needs: semver 7 {@code maxSatisfying} for x-ranges, junk 3.1
 * {@code is}, and String.prototype.localeCompare (ICU root collation, approximated; see {@link #localeCompare}).
 */
final class IgSupport {
  private IgSupport() {}

  // ------------------------------------------------------------------------------------------------ junk 3.1.0

  private static final Pattern JUNK = Pattern.compile(String.join("|", List.of(
      "^npm-debug\\.log$", "^\\..*\\.swp$", "^\\.DS_Store$", "^\\.AppleDouble$", "^\\.LSOverride$", "^Icon\\r$",
      "^\\._.*", "^\\.Spotlight-V100(?:$|\\/)", "\\.Trashes", "^__MACOSX$", "~$", "^Thumbs\\.db$",
      "^ehthumbs\\.db$", "^Desktop\\.ini$", "@eaDir$")).replace("$", "\\z"));

  /** junk.is(filename) */
  static boolean isJunk(String filename) {
    return JUNK.matcher(filename).find();
  }

  // ------------------------------------------------------------------------------------------------ semver 7

  private static final Pattern FULL = Pattern.compile(
      "^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
          + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][a-zA-Z0-9-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][a-zA-Z0-9-]*))*))?"
          + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?\\z");
  private static final Pattern XRANGE = Pattern.compile(
      "^\\s*(?:v|=\\s*)?(0|[1-9]\\d*|x|X|\\*)(?:\\.(0|[1-9]\\d*|x|X|\\*)(?:\\.(0|[1-9]\\d*|x|X|\\*))?)?\\s*\\z");

  /**
   * semver.maxSatisfying(versions, range) for the plain x-ranges SUSHI passes ("1.2.x", "1.x", "x"): the highest
   * valid, non-prerelease version matching the fixed parts, or null (also null for any other range syntax).
   */
  static String maxSatisfying(List<String> versions, String range) {
    Matcher rm = XRANGE.matcher(range);
    if (!rm.find()) return null;
    String[] want = {rm.group(1), rm.group(2), rm.group(3)};
    String max = null;
    BigInteger[] maxV = null;
    for (String v : versions) {
      if (v == null) continue;
      Matcher m = FULL.matcher(Js.trim(v));
      if (!m.find() || m.group(4) != null) continue;
      BigInteger[] parts = {new BigInteger(m.group(1)), new BigInteger(m.group(2)), new BigInteger(m.group(3))};
      boolean ok = true;
      for (int i = 0; i < 3 && ok; i++) {
        String w = want[i];
        if (w == null || w.equals("x") || w.equals("X") || w.equals("*")) break;
        ok = parts[i].equals(new BigInteger(w));
      }
      if (!ok) continue;
      if (maxV == null || compare(maxV, parts) < 0) {
        max = v;
        maxV = parts;
      }
    }
    return max;
  }

  private static int compare(BigInteger[] a, BigInteger[] b) {
    for (int i = 0; i < 3; i++) {
      int c = a[i].compareTo(b[i]);
      if (c != 0) return c;
    }
    return 0;
  }

  // ------------------------------------------------------------------------------------------------ localeCompare

  // CLDR root collation order of the ASCII non-alphanumerics (alternate=non-ignorable): whitespace, punctuation,
  // symbols, currency, then digits, then letters.
  private static final String ASCII_ORDER = "\t\n\u000b\f\r _-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$0123456789";

  /**
   * String.prototype.localeCompare(a, b) as node 22 (ICU, root/en collation) answers it for the page file names it is
   * used on: primary = letters case-insensitive and without accents (after NFD), with ASCII punctuation and digits
   * in CLDR root order before letters, other characters after letters by code point; secondary = accents;
   * tertiary = lowercase before uppercase. Returns -1, 0 or 1.
   */
  static int localeCompare(String a, String b) {
    String na = Normalizer.normalize(a, Normalizer.Form.NFD);
    String nb = Normalizer.normalize(b, Normalizer.Form.NFD);
    int c = compareLevel(na, nb, 1);
    if (c == 0) c = compareLevel(na, nb, 2);
    if (c == 0) c = compareLevel(na, nb, 3);
    return Integer.signum(c);
  }

  private static boolean isMark(char ch) {
    int t = Character.getType(ch);
    return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK
        || t == Character.ENCLOSING_MARK;
  }

  private static int primary(char ch) {
    int i = ASCII_ORDER.indexOf(ch);
    if (i >= 0) return i;
    if (Character.isLetter(ch)) {
      char l = Character.toLowerCase(ch);
      if (l >= 'a' && l <= 'z') return 100 + (l - 'a');
      return 1000 + l;
    }
    return 0x20000 + ch;
  }

  private static int compareLevel(String a, String b, int level) {
    int[] wa = weights(a, level), wb = weights(b, level);
    int n = Math.min(wa.length, wb.length);
    for (int i = 0; i < n; i++) {
      if (wa[i] != wb[i]) return Integer.compare(wa[i], wb[i]);
    }
    return Integer.compare(wa.length, wb.length);
  }

  private static int[] weights(String s, int level) {
    int[] out = new int[s.length()];
    int n = 0;
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (isMark(ch)) {
        if (level == 2) out[n++] = 1 + ch;
        continue;
      }
      switch (level) {
        case 1 -> out[n++] = primary(ch);
        case 2 -> {
          // a base character without a following mark has the lowest secondary weight
          if (i + 1 >= s.length() || !isMark(s.charAt(i + 1))) out[n++] = 0;
        }
        default -> out[n++] = Character.isUpperCase(ch) ? 1 : 0;
      }
    }
    return java.util.Arrays.copyOf(out, n);
  }
}
