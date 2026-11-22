package org.hl7.fhir.jekyll.kramdown;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small helpers reproducing Ruby String semantics used by kramdown (strip/chomp/split/gsub/sub/tr)
 * and the regex compilation conventions of this port.
 *
 * <p>Regex convention: every pattern is compiled with MULTILINE (Ruby's {@code ^}/{@code $} are
 * always line anchors) and UNIX_LINES (Ruby's {@code .} only excludes {@code \n}). Ruby's
 * {@code /m} is Java's DOTALL, added explicitly via {@code (?s:...)} or {@link #rxm}.
 */
final class RubyStr {
  private RubyStr() {}

  static final int FLAGS = Pattern.MULTILINE | Pattern.UNIX_LINES;

  /** Compile a Ruby regex without flags. */
  static Pattern rx(String src) {
    return Pattern.compile(src, FLAGS);
  }

  /** Compile a Ruby regex with /m. */
  static Pattern rxm(String src) {
    return Pattern.compile(src, FLAGS | Pattern.DOTALL);
  }

  /** Compile a Ruby regex with /i. */
  static Pattern rxi(String src) {
    return Pattern.compile(src, FLAGS | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  /** Compile a Ruby regex with /mi. */
  static Pattern rxmi(String src) {
    return Pattern.compile(src, FLAGS | Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  /** The Ruby source of a regex embedded into another one, as Ruby's #{re} does ((?-mix:...)). */
  static String emb(Pattern p) {
    int f = p.flags();
    StringBuilder on = new StringBuilder();
    StringBuilder off = new StringBuilder();
    (((f & Pattern.DOTALL) != 0) ? on : off).append('s');
    (((f & Pattern.CASE_INSENSITIVE) != 0) ? on : off).append('i');
    if ((f & Pattern.CASE_INSENSITIVE) != 0) {
      on.append('u');
    }
    return "(?" + on + (off.length() > 0 ? "-" + off : "") + ":" + p.pattern() + ")";
  }

  static boolean isRubySpace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r' || c == 0;
  }

  static String strip(String s) {
    return rstrip(lstrip(s));
  }

  static String lstrip(String s) {
    int i = 0;
    int n = s.length();
    while (i < n && isRubySpace(s.charAt(i))) {
      i++;
    }
    return i == 0 ? s : s.substring(i);
  }

  static String rstrip(String s) {
    int n = s.length();
    while (n > 0 && isRubySpace(s.charAt(n - 1))) {
      n--;
    }
    return n == s.length() ? s : s.substring(0, n);
  }

  /** Ruby chomp (no arg): removes one trailing \n, \r\n or \r. */
  static String chomp(String s) {
    if (s.endsWith("\r\n")) {
      return s.substring(0, s.length() - 2);
    }
    if (s.endsWith("\n") || s.endsWith("\r")) {
      return s.substring(0, s.length() - 1);
    }
    return s;
  }

  static boolean isEmptyStrip(String s) {
    return strip(s).isEmpty();
  }

  /** Ruby String#split(regex, limit): captures included, trailing empties removed when limit == 0
   * (patterns used by kramdown never match the empty string). */
  static List<String> split(String s, Pattern p, int limit) {
    List<String> out = new ArrayList<>();
    if (s.isEmpty()) {
      return out;
    }
    Matcher m = p.matcher(s);
    int start = 0;
    while (m.find()) {
      if (m.end() == m.start()) {
        continue;
      }
      if (limit > 0 && out.size() >= limit - 1) {
        break;
      }
      out.add(s.substring(start, m.start()));
      addGroups(out, m);
      start = m.end();
    }
    out.add(s.substring(start));
    if (limit == 0) {
      while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
        out.remove(out.size() - 1);
      }
    }
    return out;
  }

  private static void addGroups(List<String> out, Matcher m) {
    for (int g = 1; g <= m.groupCount(); g++) {
      if (m.group(g) != null) {
        out.add(m.group(g));
      }
    }
  }

  /** Ruby String#split(String) with a literal separator. */
  static List<String> split(String s, String sep, int limit) {
    return split(s, Pattern.compile(Pattern.quote(sep)), limit);
  }

  /** Ruby gsub with a block. */
  static String gsub(String s, Pattern p, Function<Matcher, String> f) {
    Matcher m = p.matcher(s);
    if (!m.find()) {
      return s;
    }
    StringBuilder b = new StringBuilder(s.length() + 16);
    int last = 0;
    do {
      b.append(s, last, m.start());
      b.append(f.apply(m));
      last = m.end();
    } while (m.find());
    b.append(s, last, s.length());
    return b.toString();
  }

  /** Ruby gsub with a replacement string using \\1 style back references. */
  static String gsub(String s, Pattern p, String repl) {
    return gsub(s, p, m -> expand(repl, m));
  }

  /** Ruby sub with block; returns null if no match (like sub! returning nil) when strict. */
  static String sub(String s, Pattern p, Function<Matcher, String> f) {
    Matcher m = p.matcher(s);
    if (!m.find()) {
      return s;
    }
    return s.substring(0, m.start()) + f.apply(m) + s.substring(m.end());
  }

  static String sub(String s, Pattern p, String repl) {
    return sub(s, p, m -> expand(repl, m));
  }

  /** Ruby replacement string expansion: \\0, \\1..\\9, \\\\. */
  static String expand(String repl, Matcher m) {
    if (repl.indexOf('\\') < 0) {
      return repl;
    }
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < repl.length(); i++) {
      char c = repl.charAt(i);
      if (c == '\\' && i + 1 < repl.length()) {
        char d = repl.charAt(i + 1);
        if (d >= '0' && d <= '9') {
          int g = d - '0';
          if (g <= m.groupCount() && m.group(g) != null) {
            b.append(m.group(g));
          }
          i++;
          continue;
        } else if (d == '\\') {
          b.append('\\');
          i++;
          continue;
        } else if (d == '&') {
          b.append(m.group());
          i++;
          continue;
        }
      }
      b.append(c);
    }
    return b.toString();
  }

  static boolean find(Pattern p, String s) {
    return s != null && p.matcher(s).find();
  }

  static String repeat(char c, int n) {
    if (n <= 0) {
      return "";
    }
    return String.valueOf(c).repeat(n);
  }

  static String spaces(int n) {
    return n <= 0 ? "" : " ".repeat(n);
  }

  /** Ruby downcase (full Unicode). */
  static String downcase(String s) {
    return s.toLowerCase(java.util.Locale.ROOT);
  }

  /** Ruby Integer(str) (decimal, optional sign/underscores, 0x/0b/0o/0 prefixes); throws on error. */
  static long rubyInteger(String str) {
    String s = strip(str).replace("_", "");
    if (s.isEmpty()) {
      throw new NumberFormatException("invalid value for Integer(): \"" + str + "\"");
    }
    boolean neg = false;
    if (s.startsWith("-") || s.startsWith("+")) {
      neg = s.startsWith("-");
      s = s.substring(1);
    }
    long v;
    String l = s.toLowerCase(java.util.Locale.ROOT);
    if (l.startsWith("0x")) {
      v = Long.parseLong(s.substring(2), 16);
    } else if (l.startsWith("0b")) {
      v = Long.parseLong(s.substring(2), 2);
    } else if (l.startsWith("0o")) {
      v = Long.parseLong(s.substring(2), 8);
    } else if (l.startsWith("0d")) {
      v = Long.parseLong(s.substring(2), 10);
    } else if (l.length() > 1 && l.startsWith("0")) {
      v = Long.parseLong(s.substring(1), 8);
    } else {
      if (!s.chars().allMatch(Character::isDigit)) {
        throw new NumberFormatException("invalid value for Integer(): \"" + str + "\"");
      }
      v = Long.parseLong(s);
    }
    return neg ? -v : v;
  }

  /** Ruby String#to_i (leading integer, 0 if none). */
  static long toI(String s) {
    int i = 0;
    int n = s.length();
    while (i < n && isRubySpace(s.charAt(i))) {
      i++;
    }
    int st = i;
    if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
      i++;
    }
    int ds = i;
    while (i < n && (Character.isDigit(s.charAt(i)) || (s.charAt(i) == '_' && i > ds))) {
      i++;
    }
    if (i == ds) {
      return 0;
    }
    try {
      return Long.parseLong(s.substring(st, i).replace("_", ""));
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
