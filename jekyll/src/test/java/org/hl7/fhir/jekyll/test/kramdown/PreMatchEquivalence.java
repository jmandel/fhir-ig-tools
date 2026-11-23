package org.hl7.fhir.jekyll.kramdown;

import java.util.Random;

/**
 * Checks that the O(1) pre_match helpers in KramdownParser agree with the regexes they replace,
 * on every prefix of many random strings over the characters those regexes care about.
 * usage: PreMatchEquivalence [strings] [maxLen]
 */
public final class PreMatchEquivalence {
  public static void main(String[] args) {
    int n = args.length > 0 ? Integer.parseInt(args[0]) : 200_000;
    int maxLen = args.length > 1 ? Integer.parseInt(args[1]) : 12; // 2.6.1's 50-character window: run with maxLen > 50 too
    String alphabet = "_-aZé\u00df\u4e2d9 \t\n\r\u000B\f*`.\u00a0\ud835\udc00x";
    Random r = new Random(42);
    long checked = 0, bad = 0;
    for (int k = 0; k < n; k++) {
      StringBuilder b = new StringBuilder();
      int len = r.nextInt(maxLen + 1);
      for (int i = 0; i < len; i++) {
        int c = r.nextInt(alphabet.length());
        if (Character.isHighSurrogate(alphabet.charAt(c)) && c + 1 < alphabet.length()) {
          b.append(alphabet, c, c + 2);
        } else if (!Character.isLowSurrogate(alphabet.charAt(c))) {
          b.append(alphabet.charAt(c));
        }
      }
      String s = b.toString();
      for (int end = 0; end <= s.length(); end++) {
        if (end > 0 && end < s.length() && Character.isLowSurrogate(s.charAt(end))) {
          continue; // pre_match never ends inside a code point
        }
        String prefix = s.substring(0, end);
        checked++;
        boolean em1 = KramdownParser.emIntraword(s, end), em2 = KramdownParser.emIntrawordRegex(prefix);
        boolean cs1 = end <= 0 || KramdownParser.isRubyWs(s.charAt(end - 1)), cs2 = KramdownParser.csPreRegex(prefix);
        if (em1 != em2 || cs1 != cs2) {
          if (bad++ < 10) {
            System.out.printf("MISMATCH prefix=%s em(new=%s regex=%s) cs(new=%s regex=%s)%n",
                prefix.replace("\n", "\\n").replace("\t", "\\t"), em1, em2, cs1, cs2);
          }
        }
      }
    }
    System.out.printf("checked %d prefixes, %d mismatches%n", checked, bad);
    System.exit(bad == 0 ? 0 : 1);
  }
}
