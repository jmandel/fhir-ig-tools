package org.hl7.fhir.jekyll.yaml;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * The bits of Ruby 3.2.3's numeric parsing SafeYAML relies on:
 *
 * <ul>
 *   <li>{@link #kernelFloat}: {@code Kernel#Float(String)} = {@code rb_str_to_dbl(str, TRUE)}
 *       (object.c: {@code rb_cstr_to_dbl_raise} with its underscore-removal buffer of
 *       {@code DBL_DIG*4+10} chars) over {@code ruby_strtod} (missing/dtoa.c: digits after the 60th
 *       significant fraction digit are ignored; the rest is correctly rounded);
 *   <li>{@link #integer}: {@code Kernel#Integer(String)} for the shapes ToInteger's regexes admit
 *       (decimal, {@code 0}-octal, {@code 0x}, {@code 0b}, optional sign, trailing whitespace).
 * </ul>
 */
final class RubyNumbers {
  private RubyNumbers() {}

  private static final int DBL_DIG = 15;

  private static boolean isSpace(int c) {
    return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r';
  }

  private static boolean isDigit(int c) {
    return c >= '0' && c <= '9';
  }

  /** Kernel#Float(String): raises ArgumentError like Ruby. */
  static double kernelFloat(String str) {
    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
    for (byte b : bytes) {
      if (b == 0) {
        throw new YamlException("ArgumentError", "string for Float contains null byte");
      }
    }
    byte[] p = new byte[bytes.length + 1];
    System.arraycopy(bytes, 0, p, 0, bytes.length);
    return cstrToDbl(p, str);
  }

  /** rb_cstr_to_dbl_raise(p, badcheck=TRUE, raise=TRUE) on a NUL-terminated buffer. */
  private static double cstrToDbl(byte[] s, String original) {
    int p = 0;
    while (isSpace(s[p] & 0xFF)) {
      p++;
    }
    Strtod r = strtod(s, p);
    double d = r.value;
    int end = r.end;
    if (p == end) {
      throw bad(original);
    }
    if (s[end] != 0) {
      byte[] buf = new byte[DBL_DIG * 4 + 10];
      int n = 0;
      final int initE = DBL_DIG * 4;
      int e = initE;
      int prev = 0;
      int dotSeen = 0;
      switch (s[p]) {
        case '+':
        case '-':
          prev = buf[n++] = s[p++];
          break;
        default:
          break;
      }
      if (s[p] == '0') {
        prev = buf[n++] = '0';
        while (s[++p] == '0') {
          // skip
        }
      }
      while (p < end && n < e) {
        prev = buf[n++] = s[p++];
      }
      while (s[p] != 0) {
        if (s[p] == '_') {
          // remove an underscore between digits
          if (n == 0 || !isDigit(prev & 0xFF) || !isDigit(s[++p] & 0xFF)) {
            throw bad(original);
          }
        }
        prev = s[p++];
        if (e == initE && (prev == 'e' || prev == 'E' || prev == 'p' || prev == 'P')) {
          e = buf.length - 1;
          buf[n++] = (byte) prev;
          switch (s[p]) {
            case '+':
            case '-':
              prev = buf[n++] = s[p++];
              break;
            default:
              break;
          }
          if (s[p] == '0') {
            prev = buf[n++] = '0';
            while (s[++p] == '0') {
              // skip
            }
          }
          continue;
        } else if (isSpace(prev & 0xFF)) {
          while (isSpace(s[p] & 0xFF)) {
            ++p;
          }
          if (s[p] != 0) {
            throw bad(original);
          }
        } else if (prev == '.' ? dotSeen++ != 0 : !isDigit(prev & 0xFF)) {
          throw bad(original);
        }
        if (n < e) {
          buf[n++] = (byte) prev;
        }
      }
      byte[] b2 = new byte[n + 1];
      System.arraycopy(buf, 0, b2, 0, n);
      Strtod r2 = strtod(b2, 0);
      d = r2.value;
      int end2 = r2.end;
      if (end2 == 0) {
        throw bad(original);
      }
      while (b2[end2] != 0 && isSpace(b2[end2] & 0xFF)) {
        end2++;
      }
      if (b2[end2] != 0) {
        throw bad(original);
      }
    }
    return d;
  }

  private static YamlException bad(String original) {
    // rb_invalid_str(q, "Float()"): the C string (up to a NUL) inspected
    return new YamlException("ArgumentError", "invalid value for Float(): " + RubyValues.inspect(original));
  }

  private static final class Strtod {
    final double value;
    final int end;

    Strtod(double value, int end) {
      this.value = value;
      this.end = end;
    }
  }

  /** ruby_strtod's scanning front end; the value of the kept digits is computed correctly rounded. */
  private static Strtod strtod(byte[] b, int s00) {
    int s = s00;
    boolean sign = false;
    boolean nz0 = false;
    int nz = 0;
    sign:
    for (;; s++) {
      switch (b[s]) {
        case '-':
          sign = true;
          // fall through
        case '+':
          if (b[++s] != 0) {
            break sign;
          }
          // fall through
        case 0:
          return new Strtod(0.0, s00);
        case '\t':
        case '\n':
        case 0x0B:
        case '\f':
        case '\r':
        case ' ':
          continue;
        default:
          break sign;
      }
    }
    if (b[s] == '0') {
      if (b[s + 1] == 'x' || b[s + 1] == 'X') {
        // hexadecimal floats: not reachable from SafeYAML's matchers
        throw new IllegalStateException("hex float");
      }
      nz0 = true;
      while (b[++s] == '0') {
        // skip
      }
      if (b[s] == 0) {
        return new Strtod(sign ? -0.0 : 0.0, s);
      }
    }
    StringBuilder digits = new StringBuilder();
    int nd = 0;
    int nf = 0;
    int c;
    for (; isDigit(c = b[s]); nd++, s++) {
      digits.append((char) c);
    }
    boolean digDone = false;
    if (c == '.') {
      if (!isDigit(b[s + 1])) {
        digDone = true;
      } else {
        c = b[++s];
        boolean haveDig = false;
        if (nd == 0) {
          for (; c == '0'; c = b[++s]) {
            nz++;
          }
          if (c > '0' && c <= '9') {
            nf += nz;
            nz = 0;
            haveDig = true;
          } else {
            digDone = true;
          }
        }
        if (!digDone) {
          for (; haveDig || isDigit(c); c = b[++s]) {
            haveDig = false;
            nz++;
            if (nd > DBL_DIG * 4) {
              continue;
            }
            if (c != '0') {
              nf += nz;
              for (int i = 1; i < nz; i++) {
                nd++;
                digits.append('0');
              }
              nd++;
              digits.append((char) c);
              nz = 0;
            }
          }
        }
      }
    }
    // dig_done:
    long e = 0;
    if (c == 'e' || c == 'E') {
      if (nd == 0 && nz == 0 && !nz0) {
        return new Strtod(0.0, s00);
      }
      int sExp = s;
      boolean esign = false;
      c = b[++s];
      if (c == '-') {
        esign = true;
        c = b[++s];
      } else if (c == '+') {
        c = b[++s];
      }
      if (isDigit(c)) {
        while (c == '0') {
          c = b[++s];
        }
        if (c > '0' && c <= '9') {
          long l = c - '0';
          int s1 = s;
          while (isDigit(c = b[++s])) {
            l = 10 * l + c - '0';
            if (l > 1_000_000_000L) {
              l = 1_000_000_000L;
            }
          }
          if (s - s1 > 8 || l > 19999) {
            e = 19999;
          } else {
            e = l;
          }
          if (esign) {
            e = -e;
          }
        } else {
          e = 0;
        }
      } else {
        s = sExp;
      }
    }
    if (nd == 0) {
      if (nz == 0 && !nz0) {
        return new Strtod(0.0, s00);
      }
      return new Strtod(sign ? -0.0 : 0.0, s);
    }
    e -= nf;
    double v = Double.parseDouble(digits + "E" + e);
    return new Strtod(sign ? -v : v, s);
  }

  /** Kernel#Integer(String) for SafeYAML's integer shapes; Long when it fits, else BigInteger. */
  static Object integer(String value) {
    String t = value;
    int end = t.length();
    while (end > 0 && isSpace(t.charAt(end - 1))) {
      end--;
    }
    t = t.substring(0, end);
    boolean neg = false;
    int i = 0;
    if (i < t.length() && (t.charAt(i) == '+' || t.charAt(i) == '-')) {
      neg = t.charAt(i) == '-';
      i++;
    }
    int radix = 10;
    if (t.length() - i >= 2 && t.charAt(i) == '0') {
      char x = t.charAt(i + 1);
      if (x == 'x' || x == 'X') {
        radix = 16;
        i += 2;
      } else if (x == 'b' || x == 'B') {
        radix = 2;
        i += 2;
      } else if (x == 'o' || x == 'O') {
        radix = 8;
        i += 2;
      } else if (x == 'd' || x == 'D') {
        i += 2;
      } else {
        radix = 8;
        i += 1;
      }
    }
    BigInteger v = new BigInteger(t.substring(i).replace("_", ""), radix);
    if (neg) {
      v = v.negate();
    }
    return v.bitLength() < 64 ? (Object) v.longValue() : v;
  }

  /** Ruby Float#to_s (flo_to_s). */
  static String floatToS(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    if (Double.isInfinite(value)) {
      return value > 0 ? "Infinity" : "-Infinity";
    }
    if (value == 0) {
      return (1 / value < 0) ? "-0.0" : "0.0";
    }
    // shortest round-trip digits, like Ruby's ruby_dtoa mode 0. JDK 19+ Double.toString is shortest
    // except that it never prints fewer than 2 significant digits: when a 1-digit decimal also
    // round-trips (only near the subnormal minimum, e.g. 5.0e-324), Ruby uses it.
    double abs = Math.abs(value);
    java.math.BigDecimal bd = new java.math.BigDecimal(Double.toString(abs)).stripTrailingZeros();
    if (bd.precision() == 2) {
      java.math.BigDecimal one = new java.math.BigDecimal(abs).round(new java.math.MathContext(1, java.math.RoundingMode.HALF_EVEN));
      if (Double.parseDouble(one.toString()) == abs) {
        bd = one.stripTrailingZeros();
      }
    }
    String digits = bd.unscaledValue().toString();
    int digs = digits.length();
    int decpt = digs - bd.scale();
    StringBuilder sb = new StringBuilder();
    if (value < 0) {
      sb.append('-');
    }
    if (decpt > 0 && decpt < digs) {
      sb.append(digits, 0, decpt).append('.').append(digits, decpt, digs);
    } else if (decpt > 0 && decpt <= DBL_DIG) {
      sb.append(digits).append("0".repeat(decpt - digs)).append(".0");
    } else if (decpt <= 0 && decpt > -4) {
      sb.append("0.").append("0".repeat(-decpt)).append(digits);
    } else {
      sb.append(digits.charAt(0)).append('.');
      sb.append(digs > 1 ? digits.substring(1) : "0");
      int exp = decpt - 1;
      sb.append('e').append(exp < 0 ? '-' : '+');
      String es = Integer.toString(Math.abs(exp));
      if (es.length() < 2) {
        sb.append('0');
      }
      sb.append(es);
    }
    return sb.toString();
  }
}
