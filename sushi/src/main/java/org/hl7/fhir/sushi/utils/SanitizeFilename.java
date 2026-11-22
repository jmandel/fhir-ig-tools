package org.hl7.fhir.sushi.utils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

// port of sanitize-filename 1.6.x + truncate-utf8-bytes (node_modules), used for every output file name
public final class SanitizeFilename {
  private SanitizeFilename() {}

  private static final Pattern ILLEGAL = Pattern.compile("[/?<>\\\\:*|\"]");
  private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x1f\\x80-\\x9f]");
  private static final Pattern RESERVED = Pattern.compile("^\\.+\\z");
  private static final Pattern WINDOWS_RESERVED =
      Pattern.compile("^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?\\z", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /** sanitize(input) (replacement ""). */
  public static String sanitize(String input) {
    return sanitize(input, "");
  }

  /** sanitize(input, {replacement}). As in JS, a non-empty replacement result is sanitized again with "". */
  public static String sanitize(String input, String replacement) {
    String out = once(input, replacement);
    return replacement.isEmpty() ? out : once(out, "");
  }

  private static String once(String input, String r) {
    String q = java.util.regex.Matcher.quoteReplacement(r);
    String s = ILLEGAL.matcher(input).replaceAll(q);
    s = CONTROL.matcher(s).replaceAll(q);
    s = RESERVED.matcher(s).replaceAll(q);
    s = WINDOWS_RESERVED.matcher(s).replaceAll(q);
    int end = s.length();
    while (end > 0 && (s.charAt(end - 1) == '.' || s.charAt(end - 1) == ' ')) end--;
    if (end < s.length()) s = s.substring(0, end) + r;
    return truncateUtf8(s, 255);
  }

  /** truncate-utf8-bytes: cut at a code point boundary so the UTF-8 length is <= max (lone surrogates: 3 bytes). */
  static String truncateUtf8(String s, int max) {
    int bytes = 0;
    for (int i = 0; i < s.length(); i++) {
      int start = i;
      char c = s.charAt(i);
      String seg;
      if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
        seg = s.substring(i, i + 2);
        i++;
      } else {
        seg = String.valueOf(c);
      }
      // Buffer.byteLength encodes a lone surrogate as U+FFFD (3 bytes), as does Java's encoder
      bytes += seg.getBytes(StandardCharsets.UTF_8).length;
      if (bytes == max) return s.substring(0, i + 1);
      if (bytes > max) return s.substring(0, start);
    }
    return s;
  }
}
