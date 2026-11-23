package org.hl7.fhir.jekyll.rouge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 * Ruby (Onigmo, UTF-8) regular expression source to java.util.regex translation, so that lexers can
 * keep Rouge's regex sources verbatim. Each construct is mapped deliberately:
 * <ul>
 * <li>Ruby <code>^</code>/<code>$</code> are always line anchors and only <code>\n</code> ends a line:
 * Java MULTILINE|UNIX_LINES (UNIX_LINES also makes <code>.</code> exclude only <code>\n</code>).</li>
 * <li>Ruby <code>/m</code> = Java DOTALL (also inside <code>(?m:..)</code> groups → <code>(?s:..)</code>);
 * Ruby <code>/i</code> folds Unicode case → CASE_INSENSITIVE|UNICODE_CASE.</li>
 * <li>Ruby <code>/x</code> is applied here (whitespace and <code>#</code> comments dropped outside
 * character classes, kept inside them, unlike Java's COMMENTS mode).</li>
 * <li><code>\h</code> = hex digit (Java's <code>\h</code> is horizontal space), <code>\v</code> = VT
 * (Java's is a class), <code>\b</code>/<code>\B</code> are Unicode word boundaries (Onigmo's Word =
 * Alphabetic + Mark + Nd + Pc) while <code>\w</code>/<code>\d</code>/<code>\s</code> stay ASCII as in
 * Ruby; POSIX brackets and <code>\p{Word}</code>/<code>\p{Alpha}</code> are Unicode.</li>
 * <li>A <code>{</code> that does not start a valid interval is a literal; <code>{,n}</code> = <code>{0,n}</code>.</li>
 * <li><code>(?#...)</code> comments are dropped; named groups are renamed to Java-legal names via
 * {@link #groupName}.</li>
 * </ul>
 * Unsupported constructs throw, so a lexer port fails loudly instead of silently diverging.
 */
public final class RRegex {
  private RRegex() {}

  /** Onigmo's Unicode Word ctype (Nl yes; Join_Control: no in Ruby 3.2, yes in Ruby 3.4). */
  // Ruby 3.4's Onigmo also counts Join_Control (U+200C, U+200D) as Word (Ruby 3.2 didn't); the set equals Ruby 3.4.13's
  // \\p{Word} on all 1,112,064 code points (139,612 of them)
  static final String WORD = "\\p{IsAlphabetic}\\p{M}\\p{Nd}\\p{Pc}\\u200C\\u200D";
  static final String WB = "(?:(?<=[" + WORD + "])(?![" + WORD + "])|(?<![" + WORD + "])(?=[" + WORD + "]))";
  static final String NWB = "(?:(?<=[" + WORD + "])(?=[" + WORD + "])|(?<![" + WORD + "])(?![" + WORD + "]))";

  /** Compile a Ruby regex source with Ruby option letters (subset of "mix"). */
  public static Pattern compile(String src, String flags) {
    boolean m = flags.indexOf('m') >= 0, i = flags.indexOf('i') >= 0, x = flags.indexOf('x') >= 0;
    int jf = Pattern.MULTILINE | Pattern.UNIX_LINES;
    if (m) {
      jf |= Pattern.DOTALL;
    }
    if (i) {
      jf |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    }
    String j = translate(src, x);
    try {
      return Pattern.compile(j, jf);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("cannot compile translated Ruby regex /" + src + "/" + flags + " as " + j, e);
    }
  }

  /** Ruby Regexp#to_s, used when a Regexp is interpolated into another regex literal. */
  public static String toS(String src, String flags) {
    StringBuilder on = new StringBuilder(), off = new StringBuilder();
    for (char c : new char[] {'m', 'i', 'x'}) {
      (flags.indexOf(c) >= 0 ? on : off).append(c);
    }
    return "(?" + on + (off.length() > 0 ? "-" + off : "") + ":" + src + ")";
  }

  /** Regexp.escape(str) (Ruby 3.2 re.c rb_reg_quote). */
  public static String escape(String str) {
    StringBuilder b = new StringBuilder(str.length() + 8);
    for (int k = 0; k < str.length(); k++) {
      char c = str.charAt(k);
      switch (c) {
        case '[': case ']': case '{': case '}': case '(': case ')': case '|': case '-': case '*': case '.':
        case '\\': case '?': case '+': case '^': case '$': case '#':
          b.append('\\').append(c);
          break;
        case ' ': b.append("\\ "); break;
        case '\t': b.append("\\t"); break;
        case '\f': b.append("\\f"); break;
        case 0x0B: b.append("\\v"); break;
        case '\n': b.append("\\n"); break;
        case '\r': b.append("\\r"); break;
        default: b.append(c);
      }
    }
    return b.toString();
  }

  /** Java-legal name for a Ruby named group (Java allows only [a-zA-Z][a-zA-Z0-9]*). */
  public static String groupName(String ruby) {
    StringBuilder b = new StringBuilder("g");
    for (char c : ruby.toCharArray()) {
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        b.append(c);
      } else {
        b.append("Z").append(Integer.toHexString(c)).append("Z");
      }
    }
    return b.toString();
  }

  static String translate(String s, boolean extended) {
    return new Translator(s, extended).run();
  }

  private static final class Translator {
    final String s;
    int i;
    final StringBuilder out = new StringBuilder();
    final Deque<Boolean> xStack = new ArrayDeque<>();
    boolean x;

    Translator(String s, boolean x) {
      this.s = s;
      this.x = x;
    }

    IllegalArgumentException unsupported(String what) {
      return new IllegalArgumentException("unsupported Ruby regex construct " + what + " at " + i + " in /" + s + "/");
    }

    String run() {
      int n = s.length();
      while (i < n) {
        char c = s.charAt(i);
        if (x && isSpace(c)) {
          i++;
          continue;
        }
        if (x && c == '#') {
          while (i < n && s.charAt(i) != '\n') {
            i++;
          }
          continue;
        }
        switch (c) {
          case '\\':
            escape(false);
            break;
          case '[':
            charClass();
            break;
          case '(':
            group();
            break;
          case ')':
            out.append(')');
            i++;
            x = xStack.isEmpty() ? x : xStack.pop();
            break;
          case '{':
            brace();
            break;
          default:
            out.append(c);
            i++;
        }
      }
      return out.toString();
    }

    static boolean isSpace(char c) {
      return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == 0x0B;
    }

    void brace() {
      int j = i + 1, n = s.length();
      int a = j;
      while (j < n && Character.isDigit(s.charAt(j)) && s.charAt(j) < 128) {
        j++;
      }
      String lo = s.substring(a, j);
      String hi = null;
      boolean comma = false;
      if (j < n && s.charAt(j) == ',') {
        comma = true;
        j++;
        int b = j;
        while (j < n && Character.isDigit(s.charAt(j)) && s.charAt(j) < 128) {
          j++;
        }
        hi = s.substring(b, j);
      }
      boolean valid = j < n && s.charAt(j) == '}' && (!lo.isEmpty() || (comma && !hi.isEmpty()));
      if (!valid) {
        out.append("\\{");
        i++;
        return;
      }
      out.append('{').append(lo.isEmpty() ? "0" : lo);
      if (comma) {
        out.append(',').append(hi);
      }
      out.append('}');
      i = j + 1;
      // Ruby: {n,m}+ is a repeat of a repeat, Java: possessive. Not reproduced.
      if (i < n && s.charAt(i) == '+') {
        throw unsupported("{n,m}+");
      }
    }

    void group() {
      int n = s.length();
      if (s.startsWith("(?#", i)) {
        int e = s.indexOf(')', i);
        if (e < 0) {
          throw unsupported("unterminated (?#");
        }
        i = e + 1;
        return;
      }
      if (!s.startsWith("(?", i)) {
        out.append('(');
        i++;
        xStack.push(x);
        return;
      }
      char d = i + 2 < n ? s.charAt(i + 2) : 0;
      if (d == ':' || d == '=' || d == '!' || d == '>') {
        out.append(s, i, i + 3);
        i += 3;
        xStack.push(x);
        return;
      }
      if (d == '<' && i + 3 < n && (s.charAt(i + 3) == '=' || s.charAt(i + 3) == '!')) {
        out.append(s, i, i + 4);
        i += 4;
        xStack.push(x);
        return;
      }
      if (d == '<' || d == '\'') {
        char close = d == '<' ? '>' : '\'';
        int e = s.indexOf(close, i + 3);
        if (e < 0) {
          throw unsupported("named group");
        }
        out.append("(?<").append(groupName(s.substring(i + 3, e))).append('>');
        i = e + 1;
        xStack.push(x);
        return;
      }
      // option group (?imx-imx) / (?imx-imx:...)
      int j = i + 2;
      boolean neg = false;
      StringBuilder on = new StringBuilder(), off = new StringBuilder();
      boolean nx = x;
      while (j < n && "imx-".indexOf(s.charAt(j)) >= 0) {
        char f = s.charAt(j);
        if (f == '-') {
          neg = true;
        } else if (f == 'x') {
          nx = !neg;
        } else {
          String jf = f == 'm' ? "s" : (neg ? "i" : "iu");
          (neg ? off : on).append(jf);
        }
        j++;
      }
      if (j >= n || (s.charAt(j) != ':' && s.charAt(j) != ')')) {
        throw unsupported("group (?" + (j < n ? s.charAt(j) : ""));
      }
      String flags = on + (off.length() > 0 ? "-" + off : "");
      if (s.charAt(j) == ':') {
        out.append("(?").append(flags).append(':');
        xStack.push(x);
        x = nx;
      } else {
        if (!flags.isEmpty()) {
          out.append("(?").append(flags).append(')');
        }
        x = nx;
      }
      i = j + 1;
    }

    void charClass() {
      int n = s.length();
      out.append('[');
      i++;
      if (i < n && s.charAt(i) == '^') {
        out.append('^');
        i++;
      }
      if (i < n && s.charAt(i) == ']') {
        out.append("\\]");
        i++;
      }
      while (i < n) {
        char c = s.charAt(i);
        if (c == ']') {
          out.append(']');
          i++;
          return;
        }
        if (c == '[') {
          if (s.startsWith("[:", i)) {
            int e = s.indexOf(":]", i + 2);
            if (e > 0) {
              String name = s.substring(i + 2, e);
              boolean negp = name.startsWith("^");
              if (negp) {
                name = name.substring(1);
              }
              String cls = posix(name);
              if (cls != null) {
                out.append(negp ? "[^" + cls + "]" : cls);
                i = e + 2;
                continue;
              }
            }
          }
          charClass();
          continue;
        }
        if (c == '\\') {
          escape(true);
          continue;
        }
        if (c == '&' && i + 1 < n && s.charAt(i + 1) == '&') {
          out.append("&&");
          i += 2;
          continue;
        }
        out.append(c);
        i++;
      }
      throw unsupported("unterminated character class");
    }

    /** POSIX bracket contents, Unicode as Onigmo does for UTF-8 regexes. */
    String posix(String name) {
      switch (name) {
        case "alpha": return "\\p{IsAlphabetic}";
        case "digit": return "\\p{Nd}";
        case "alnum": return "\\p{IsAlphabetic}\\p{Nd}";
        case "upper": return "\\p{IsUppercase}";
        case "lower": return "\\p{IsLowercase}";
        case "space": return "\\p{IsWhite_Space}";
        case "word": return WORD;
        case "xdigit": return "0-9a-fA-F";
        case "punct": return "\\p{P}\\$\\+<=>\\^`\\|~";
        case "cntrl": return "\\p{Cc}";
        case "blank": return "\\p{Zs}\\t";
        default: throw unsupported("[[:" + name + ":]]");
      }
    }

    void escape(boolean inClass) {
      int n = s.length();
      if (i + 1 >= n) {
        throw unsupported("trailing backslash");
      }
      char d = s.charAt(i + 1);
      i += 2;
      switch (d) {
        case 'h': out.append("\\p{XDigit}"); return;
        case 'H': out.append("\\P{XDigit}"); return;
        case 'v': out.append("\\x0B"); return;
        case 'b':
          out.append(inClass ? "\\x08" : WB);
          return;
        case 'B':
          if (inClass) {
            throw unsupported("\\B in class");
          }
          out.append(NWB);
          return;
        case 'A': case 'z': case 'Z': case 'G':
          if (inClass) {
            throw unsupported("\\" + d + " in class");
          }
          out.append('\\').append(d);
          return;
        case 'd': case 'D': case 'w': case 'W': case 's': case 'S':
        case 'n': case 't': case 'r': case 'f': case 'e': case 'a':
          out.append('\\').append(d);
          return;
        case '0': {
          int j = i, v = 0;
          while (j < n && j < i + 2 && s.charAt(j) >= '0' && s.charAt(j) <= '7') {
            v = v * 8 + (s.charAt(j) - '0');
            j++;
          }
          i = j;
          out.append(String.format(java.util.Locale.ROOT, "\\x{%x}", v));
          return;
        }
        case 'p': case 'P': {
          if (i >= n || s.charAt(i) != '{') {
            throw unsupported("\\p without {");
          }
          int e = s.indexOf('}', i);
          String name = s.substring(i + 1, e);
          i = e + 1;
          boolean neg = d == 'P';
          if (name.startsWith("^")) {
            neg = !neg;
            name = name.substring(1);
          }
          out.append(property(name, neg));
          return;
        }
        case 'u': {
          if (i < n && s.charAt(i) == '{') {
            int e = s.indexOf('}', i);
            String[] cps = s.substring(i + 1, e).trim().split("\\s+");
            i = e + 1;
            for (String cp : cps) {
              out.append("\\x{").append(cp).append('}');
            }
          } else {
            out.append("\\x{").append(s, i, i + 4).append('}');
            i += 4;
          }
          return;
        }
        case 'x': {
          if (i < n && s.charAt(i) == '{') {
            int e = s.indexOf('}', i);
            out.append("\\x{").append(s, i + 1, e).append('}');
            i = e + 1;
            return;
          }
          int j = i;
          while (j < n && j < i + 2 && Character.digit(s.charAt(j), 16) >= 0 && s.charAt(j) < 128) {
            j++;
          }
          out.append("\\x{").append(s, i, j).append('}');
          i = j;
          return;
        }
        case 'k': {
          if (i < n && s.charAt(i) == '<') {
            int e = s.indexOf('>', i);
            out.append("\\k<").append(groupName(s.substring(i + 1, e))).append('>');
            i = e + 1;
            return;
          }
          throw unsupported("\\k");
        }
        case 'c':
          out.append("\\c").append(s.charAt(i));
          i++;
          return;
        default:
          if (d >= '1' && d <= '9') {
            if (inClass) {
              // octal escape inside a class
              int j = i - 1, v = 0;
              while (j < n && j < i + 2 && s.charAt(j) >= '0' && s.charAt(j) <= '7') {
                v = v * 8 + (s.charAt(j) - '0');
                j++;
              }
              i = j;
              out.append(String.format(java.util.Locale.ROOT, "\\x{%x}", v));
              return;
            }
            out.append('\\').append(d);
            return;
          }
          if ((d >= 'a' && d <= 'z') || (d >= 'A' && d <= 'Z')) {
            throw unsupported("\\" + d);
          }
          out.append('\\').append(d);
      }
    }

    /** \p{..}: general categories pass through; Onigmo's derived properties are mapped. */
    String property(String name, boolean neg) {
      String key = name.replace("_", "").replace(" ", "").toLowerCase(java.util.Locale.ROOT);
      String inner;
      switch (key) {
        case "word": inner = WORD; break;
        case "alpha": case "alphabetic": inner = "\\p{IsAlphabetic}"; break;
        case "alnum": inner = "\\p{IsAlphabetic}\\p{Nd}"; break;
        case "digit": inner = "\\p{Nd}"; break;
        case "space": inner = "\\p{IsWhite_Space}"; break;
        case "upper": case "uppercase": inner = "\\p{IsUppercase}"; break;
        case "lower": case "lowercase": inner = "\\p{IsLowercase}"; break;
        case "xdigit": inner = "0-9a-fA-F"; break;
        case "punct": inner = "\\p{P}\\$\\+<=>\\^`\\|~"; break;
        default:
          if (name.matches("[A-Z][a-z]?")) {
            return (neg ? "\\P{" : "\\p{") + name + "}";
          }
          if (name.matches("[A-Z][a-z]+")) { // scripts: Latin, Greek, Han, ...
            return (neg ? "\\P{Is" : "\\p{Is") + name + "}";
          }
          throw unsupported("\\p{" + name + "}");
      }
      return (neg ? "[^" : "[") + inner + "]";
    }
  }
}
