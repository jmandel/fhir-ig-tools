package org.hl7.fhir.jekyll.rouge;

import java.util.ArrayList;
import java.util.List;

/** Ruby String methods used by Rouge lexers, with Ruby semantics. */
public final class RubyStrings {
  private RubyStrings() {}

  /** String#split(sep) with a String separator: trailing empty fields are removed. */
  public static List<String> split(String s, String sep) {
    List<String> out = new ArrayList<>();
    if (s.isEmpty()) {
      return out;
    }
    int start = 0;
    while (true) {
      int k = s.indexOf(sep, start);
      if (k < 0) {
        out.add(s.substring(start));
        break;
      }
      out.add(s.substring(start, k));
      start = k + sep.length();
    }
    while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
      out.remove(out.size() - 1);
    }
    return out;
  }

  /** Ruby whitespace for strip: NUL, \t \n \v \f \r and space. */
  static boolean isStripSpace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r' || c == 0;
  }

  /** String#strip (Ruby 3.2: NUL and ASCII whitespace on both ends). */
  public static String strip(String s) {
    int a = 0, b = s.length();
    while (a < b && isStripSpace(s.charAt(a))) {
      a++;
    }
    while (b > a && isStripSpace(s.charAt(b - 1))) {
      b--;
    }
    return s.substring(a, b);
  }

  /** String#downcase (full Unicode case mapping, as Ruby 2.4+). */
  public static String downcase(String s) {
    return s.toLowerCase(java.util.Locale.ROOT);
  }

  /** String#upcase (full Unicode case mapping, as Ruby 2.4+). */
  public static String upcase(String s) {
    return s.toUpperCase(java.util.Locale.ROOT);
  }

  /** str[a...b] (exclusive Range) with Ruby semantics: negative indices count from the end; null when a is out of range. */
  public static String slice(String s, int a, int b) {
    int n = s.codePointCount(0, s.length());
    if (a < 0) {
      a += n;
    }
    if (a < 0 || a > n) {
      return null;
    }
    if (b < 0) {
      b += n;
    }
    int e = Math.max(a, Math.min(b, n));
    return s.substring(s.offsetByCodePoints(0, a), s.offsetByCodePoints(0, e));
  }

  /** str[a..-1] */
  public static String from(String s, int a) {
    int n = s.codePointCount(0, s.length());
    if (a < 0) {
      a += n;
    }
    if (a < 0 || a > n) {
      return null;
    }
    return s.substring(s.offsetByCodePoints(0, a));
  }

  /** String#size in characters (code points) */
  public static int size(String s) {
    return s.codePointCount(0, s.length());
  }

  /** String#casecmp (ASCII-only case folding); null for incompatible encodings is not modeled. */
  public static int casecmp(String a, String b) {
    int n = Math.min(a.length(), b.length());
    for (int k = 0; k < n; k++) {
      char x = a.charAt(k), y = b.charAt(k);
      if (x >= 'A' && x <= 'Z') {
        x = (char) (x + 32);
      }
      if (y >= 'A' && y <= 'Z') {
        y = (char) (y + 32);
      }
      if (x != y) {
        return x < y ? -1 : 1;
      }
    }
    return Integer.compare(a.length(), b.length());
  }

  /** String#to_i: optional leading whitespace, sign and digits (underscores between digits); else 0. */
  public static long toI(String s) {
    int k = 0, n = s.length();
    while (k < n && isStripSpace(s.charAt(k)) && s.charAt(k) != 0) {
      k++;
    }
    boolean neg = false;
    if (k < n && (s.charAt(k) == '+' || s.charAt(k) == '-')) {
      neg = s.charAt(k) == '-';
      k++;
    }
    long v = 0;
    boolean any = false;
    while (k < n) {
      char c = s.charAt(k);
      if (c >= '0' && c <= '9') {
        v = v * 10 + (c - '0');
        any = true;
      } else if (c == '_' && any && k + 1 < n && s.charAt(k + 1) >= '0' && s.charAt(k + 1) <= '9') {
        // digit separator
      } else {
        break;
      }
      k++;
    }
    return neg ? -v : v;
  }
}
