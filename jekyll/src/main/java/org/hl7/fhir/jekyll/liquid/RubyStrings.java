package org.hl7.fhir.jekyll.liquid;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ruby {@code String} methods used by Liquid, with Ruby's (code point based) semantics. */
public final class RubyStrings {
  private RubyStrings() {}

  /** Ruby strip/lstrip/rstrip whitespace: NUL, \t, \n, \v, \f, \r, space. */
  static boolean isStripSpace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == 0x0b || c == '\f' || c == '\r' || c == 0;
  }

  /** ASCII isspace (Ruby {@code \s}, awk-style split). */
  static boolean isSpace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == 0x0b || c == '\f' || c == '\r';
  }

  public static String strip(String s) {
    return rstrip(lstrip(s));
  }

  public static String lstrip(String s) {
    int i = 0, n = s.length();
    while (i < n && isStripSpace(s.charAt(i))) {
      i++;
    }
    return i == 0 ? s : s.substring(i);
  }

  public static String rstrip(String s) {
    int n = s.length();
    while (n > 0 && isStripSpace(s.charAt(n - 1))) {
      n--;
    }
    return n == s.length() ? s : s.substring(0, n);
  }

  /** Character count in code points (Ruby String#length for UTF-8). */
  public static int length(String s) {
    return s.codePointCount(0, s.length());
  }

  private static boolean hasSurrogates(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isSurrogate(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  /** Ruby {@code str[offset, length]} / {@code str.slice(offset, length)}; null for out of range. */
  public static String slice(String s, long offset, long length) {
    int len = length(s);
    if (offset < 0) {
      offset += len;
    }
    if (offset < 0 || offset > len || length < 0) {
      return null;
    }
    long end = Math.min((long) len, offset + length);
    return substringCp(s, (int) offset, (int) end);
  }

  /** Substring by code point indices [from, to). */
  public static String substringCp(String s, int from, int to) {
    if (!hasSurrogates(s)) {
      return s.substring(from, to);
    }
    int a = s.offsetByCodePoints(0, from);
    int b = s.offsetByCodePoints(a, to - from);
    return s.substring(a, b);
  }

  /** Ruby {@code String#split(pattern)} with a String pattern (limit 0). */
  public static List<Object> split(String s, String pattern) {
    List<Object> out = new ArrayList<>();
    if (s.isEmpty()) {
      return out;
    }
    if (pattern.equals(" ")) {
      return awkSplit(s);
    }
    if (pattern.isEmpty()) {
      s.codePoints().forEach(cp -> out.add(new String(Character.toChars(cp))));
      return out;
    }
    int start = 0;
    while (true) {
      int idx = s.indexOf(pattern, start);
      if (idx < 0) {
        out.add(s.substring(start));
        break;
      }
      out.add(s.substring(start, idx));
      start = idx + pattern.length();
    }
    while (!out.isEmpty() && ((String) out.get(out.size() - 1)).isEmpty()) {
      out.remove(out.size() - 1);
    }
    return out;
  }

  /** Ruby {@code String#split} with no argument / " " (awk mode). */
  public static List<Object> awkSplit(String s) {
    List<Object> out = new ArrayList<>();
    int i = 0, n = s.length();
    while (i < n) {
      while (i < n && isSpace(s.charAt(i))) {
        i++;
      }
      if (i >= n) {
        break;
      }
      int st = i;
      while (i < n && !isSpace(s.charAt(i))) {
        i++;
      }
      out.add(s.substring(st, i));
    }
    return out;
  }

  /** Ruby {@code String#to_i}. */
  public static Object toI(String s) {
    int i = 0, n = s.length();
    while (i < n && isSpace(s.charAt(i))) {
      i++;
    }
    boolean neg = false;
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
      neg = s.charAt(i) == '-';
      i++;
    }
    StringBuilder digits = new StringBuilder();
    while (i < n) {
      char c = s.charAt(i);
      if (c >= '0' && c <= '9') {
        digits.append(c);
        i++;
      } else if (c == '_' && digits.length() > 0 && i + 1 < n && s.charAt(i + 1) >= '0' && s.charAt(i + 1) <= '9') {
        i++;
      } else {
        break;
      }
    }
    if (digits.length() == 0) {
      return 0L;
    }
    if (digits.length() < 18) {
      long v = Long.parseLong(digits.toString());
      return neg ? -v : v;
    }
    BigInteger b = new BigInteger(digits.toString());
    return Values.normInt(neg ? b.negate() : b);
  }

  /** Ruby {@code String#to_f} (decimal prefix). */
  public static double toF(String s) {
    int i = 0, n = s.length();
    while (i < n && isSpace(s.charAt(i))) {
      i++;
    }
    StringBuilder b = new StringBuilder();
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
      b.append(s.charAt(i++));
    }
    int digitsStart = b.length();
    while (i < n && (Character.isDigit(s.charAt(i)) && s.charAt(i) < 128 || s.charAt(i) == '_' && b.length() > digitsStart && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
      if (s.charAt(i) != '_') {
        b.append(s.charAt(i));
      }
      i++;
    }
    if (b.length() == digitsStart) {
      return 0.0;
    }
    if (i + 1 < n && s.charAt(i) == '.' && Character.isDigit(s.charAt(i + 1))) {
      b.append('.');
      i++;
      while (i < n && Character.isDigit(s.charAt(i)) && s.charAt(i) < 128) {
        b.append(s.charAt(i++));
      }
    }
    if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
      int save = b.length();
      int j = i + 1;
      StringBuilder e = new StringBuilder("e");
      if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
        e.append(s.charAt(j++));
      }
      int ds = j;
      while (j < n && Character.isDigit(s.charAt(j)) && s.charAt(j) < 128) {
        e.append(s.charAt(j++));
      }
      if (j > ds) {
        b.append(e);
      } else {
        b.setLength(save);
      }
    }
    return Double.parseDouble(b.toString());
  }

  /** Ruby {@code Kernel#Integer(str)} (strict); throws ::ArgumentError. */
  public static Object strictInteger(String str) {
    String s = str;
    int i = 0, n = s.length();
    while (i < n && isSpace(s.charAt(i))) {
      i++;
    }
    while (n > i && (isSpace(s.charAt(n - 1)) || s.charAt(n - 1) == 0)) {
      n--;
    }
    boolean neg = false;
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
      neg = s.charAt(i) == '-';
      i++;
    }
    int radix = 10;
    if (i + 1 < n && s.charAt(i) == '0') {
      char p = Character.toLowerCase(s.charAt(i + 1));
      if (p == 'x') {
        radix = 16;
        i += 2;
      } else if (p == 'b') {
        radix = 2;
        i += 2;
      } else if (p == 'o') {
        radix = 8;
        i += 2;
      } else if (p == 'd') {
        radix = 10;
        i += 2;
      } else if (p == '_' || Character.isDigit(p)) {
        radix = 8;
        i += 1;
      }
    }
    StringBuilder digits = new StringBuilder();
    boolean lastUnderscore = true;
    for (; i < n; i++) {
      char c = s.charAt(i);
      if (c == '_') {
        if (lastUnderscore) {
          throw RubyError.argumentError("invalid value for Integer(): " + Values.inspect(str));
        }
        lastUnderscore = true;
        continue;
      }
      if (Character.digit(c, radix) < 0 || c > 127) {
        throw RubyError.argumentError("invalid value for Integer(): " + Values.inspect(str));
      }
      digits.append(c);
      lastUnderscore = false;
    }
    if (digits.length() == 0 || lastUnderscore) {
      throw RubyError.argumentError("invalid value for Integer(): " + Values.inspect(str));
    }
    BigInteger b = new BigInteger(digits.toString(), radix);
    return Values.normInt(neg ? b.negate() : b);
  }

  public static String upcase(String s) {
    return s.toUpperCase(Locale.ROOT);
  }

  public static String downcase(String s) {
    return s.toLowerCase(Locale.ROOT);
  }

  /** Ruby {@code String#capitalize}: first character titlecased, the rest downcased. */
  public static String capitalize(String s) {
    if (s.isEmpty()) {
      return s;
    }
    int cp = s.codePointAt(0);
    int w = Character.charCount(cp);
    String first;
    if (cp == 0xDF) {
      first = "Ss";
    } else {
      int t = Character.toTitleCase(cp);
      first = t != cp ? new String(Character.toChars(t)) : new String(Character.toChars(cp)).toUpperCase(Locale.ROOT);
    }
    return first + s.substring(w).toLowerCase(Locale.ROOT);
  }

  /** Ruby {@code String#casecmp}: ASCII-only case folding, byte order. */
  public static int casecmp(String a, String b) {
    int n = Math.min(a.length(), b.length());
    for (int i = 0; i < n; i++) {
      char ca = a.charAt(i), cb = b.charAt(i);
      if (ca >= 'A' && ca <= 'Z') {
        ca += 32;
      }
      if (cb >= 'A' && cb <= 'Z') {
        cb += 32;
      }
      if (ca != cb) {
        boolean sa = Character.isSurrogate(ca), sb = Character.isSurrogate(cb);
        if (sa != sb) {
          return sa ? 1 : -1;
        }
        return ca < cb ? -1 : 1;
      }
    }
    return Integer.compare(a.length(), b.length());
  }

  /** Ruby {@code gsub(string, replacement)} with literal string pattern. */
  public static String gsubLiteral(String s, String pat, String rep) {
    if (pat.isEmpty()) {
      // Ruby: "abc".gsub("", "-") -> "-a-b-c-"
      StringBuilder sb = new StringBuilder();
      sb.append(rep);
      s.codePoints().forEach(cp -> sb.appendCodePoint(cp).append(rep));
      return sb.toString();
    }
    return s.replace(pat, rep);
  }

  /** Ruby {@code sub(string, replacement)} with literal string pattern. */
  public static String subLiteral(String s, String pat, String rep) {
    int idx = s.indexOf(pat);
    if (idx < 0) {
      return s;
    }
    return s.substring(0, idx) + rep + s.substring(idx + pat.length());
  }

  /**
   * Ruby replacement-string expansion for gsub/sub with a String pattern: {@code \\0}, {@code \\&},
   * {@code \\\\} etc. are interpreted in the replacement even when the pattern is a string.
   */
  public static String expandReplacement(String rep, String matched, String pre, String post) {
    if (rep.indexOf('\\') < 0) {
      return rep;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < rep.length(); i++) {
      char c = rep.charAt(i);
      if (c == '\\' && i + 1 < rep.length()) {
        char d = rep.charAt(i + 1);
        switch (d) {
          case '0':
          case '&':
            sb.append(matched);
            i++;
            continue;
          case '`':
            sb.append(pre);
            i++;
            continue;
          case '\'':
            sb.append(post);
            i++;
            continue;
          case '\\':
            sb.append('\\');
            i++;
            continue;
          default:
            if (d >= '1' && d <= '9') {
              i++; // non-existent group -> empty
              continue;
            }
            if (d == 'k') {
              sb.append(c);
              continue;
            }
            sb.append(c);
            continue;
        }
      }
      sb.append(c);
    }
    return sb.toString();
  }

  /** Ruby gsub(String, String) including backslash expansion in the replacement. */
  public static String gsub(String s, String pat, String rep) {
    if (rep.indexOf('\\') < 0) {
      return gsubLiteral(s, pat, rep);
    }
    StringBuilder sb = new StringBuilder();
    if (pat.isEmpty()) {
      int[] cps = s.codePoints().toArray();
      int off = 0;
      for (int k = 0; k <= cps.length; k++) {
        sb.append(expandReplacement(rep, "", s.substring(0, off), s.substring(off)));
        if (k < cps.length) {
          sb.appendCodePoint(cps[k]);
          off += Character.charCount(cps[k]);
        }
      }
      return sb.toString();
    }
    int start = 0;
    while (true) {
      int idx = s.indexOf(pat, start);
      if (idx < 0) {
        sb.append(s, start, s.length());
        return sb.toString();
      }
      sb.append(s, start, idx);
      sb.append(expandReplacement(rep, pat, s.substring(0, idx), s.substring(idx + pat.length())));
      start = idx + pat.length();
    }
  }

  /** Ruby sub(String, String) including backslash expansion in the replacement. */
  public static String sub(String s, String pat, String rep) {
    int idx = s.indexOf(pat);
    if (idx < 0) {
      return s;
    }
    return s.substring(0, idx) + expandReplacement(rep, pat, s.substring(0, idx), s.substring(idx + pat.length())) + s.substring(idx + pat.length());
  }
}
