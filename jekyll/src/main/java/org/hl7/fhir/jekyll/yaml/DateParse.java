package org.hl7.fhir.jekyll.yaml;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.RubyTime;

/**
 * {@code Date.parse(s)} and {@code DateTime.parse(s).to_time} (date 3.3.3, ext/date/date_parse.c
 * {@code date__parse} and date_core.c {@code d_new_by_frags} / {@code dt_new_by_frags} /
 * {@code datetime_to_time}) for the strings SafeYAML hands them: those matching
 * {@code Parse::Date::DATE_MATCHER} / {@code TIME_MATCHER}. For these, {@code date__parse} reduces
 * to its whitespace normalisation, {@code parse_time} (+ {@code parse_time_cb}), {@code parse_iso}
 * and {@code date_zone_to_diff}; the other sub-parsers cannot match (no month or day names, no
 * '/' or '.' dates). Validation follows the C code: Julian/Gregorian with ITALY, sec 60 → 59, hour
 * 24 only at 24:00:00, offsets beyond ±1 day ignored, then {@code Time.new}'s ±86400 check.
 */
final class DateParse {
  private DateParse() {}

  private static final int LIMIT = 128;
  private static final Pattern GSUB = Pattern.compile("[^-+',./:@\\p{Alnum}\\[\\]]+");
  private static final Pattern PARSE_TIME = Pattern.compile(
      "("
          + "(?<!\\d)\\d+\\s*"
          + "(?:"
          + "(?:"
          + ":\\s*\\d+"
          + "(?:"
          + "\\s*:\\s*\\d+(?:[,.]\\d*)?"
          + ")?"
          + "|"
          + "h(?:\\s*\\d+m?(?:\\s*\\d+s?)?)?"
          + ")"
          + "(?:"
          + "\\s*"
          + "[ap](?:m\\b|\\.m\\.)"
          + ")?"
          + "|"
          + "[ap](?:m\\b|\\.m\\.)"
          + ")"
          + ")"
          + "(?:"
          + "\\s*"
          + "("
          + "(?:gmt|utc?)?[-+]\\d+(?:[,.:]\\d+(?::\\d+)?)?"
          + "|"
          + "(?-i:[\\p{Alpha}.\\s]+)(?:standard|daylight)\\stime\\b"
          + "|"
          + "(?-i:\\p{Alpha}+)(?:\\sdst)?\\b"
          + ")"
          + ")?",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern PARSE_TIME_CB = Pattern.compile(
      "\\A(\\d+)h?(?:\\s*:?\\s*(\\d+)m?(?:\\s*:?\\s*(\\d+)(?:[,.](\\d+))?s?)?)?(?:\\s*([ap])(?:m\\b|\\.m\\.))?",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern PARSE_ISO = Pattern.compile("('?[-+]?(?<!\\d)\\d+)-(\\d+)-('?-?\\d+)");

  /** the fragments hash of Date._parse */
  private static final class Frags {
    Integer year;
    Integer mon;
    Integer mday;
    Integer hour;
    Integer min;
    Integer sec;
    BigInteger fracNum;
    BigInteger fracDen;
    String zone;
  }

  private static YamlException invalidDate() {
    return new YamlException("ArgumentError", "invalid date"); // Date::Error < ArgumentError
  }

  private static void checkLimit(String s) {
    int len = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    if (len > LIMIT) {
      throw new YamlException("ArgumentError", "string length (" + len + ") exceeds the limit " + LIMIT);
    }
  }

  private static int num(String digits) {
    BigInteger b = new BigInteger(digits);
    return b.bitLength() < 31 ? b.intValue() : Integer.MAX_VALUE;
  }

  /** date__parse(str, comp=true), restricted as described above. */
  private static Frags parse(String str) {
    str = GSUB.matcher(str).replaceAll(" ");
    Frags h = new Frags();
    Matcher m = PARSE_TIME.matcher(str);
    if (m.find()) {
      String s1 = m.group(1);
      String s2 = m.group(2);
      str = str.substring(0, m.start()) + " " + str.substring(m.end());
      if (s2 != null) {
        h.zone = s2;
      }
      Matcher t = PARSE_TIME_CB.matcher(s1);
      if (t.find()) {
        int hour = num(t.group(1));
        if (t.group(5) != null) {
          hour %= 12;
          if (Character.toLowerCase(t.group(5).charAt(0)) == 'p') {
            hour += 12;
          }
        }
        h.hour = hour;
        if (t.group(2) != null) {
          h.min = num(t.group(2));
        }
        if (t.group(3) != null) {
          h.sec = num(t.group(3));
        }
        if (t.group(4) != null) {
          h.fracNum = new BigInteger(t.group(4));
          h.fracDen = BigInteger.TEN.pow(t.group(4).length());
        }
      }
    }
    Matcher iso = PARSE_ISO.matcher(str);
    if (iso.find()) {
      // s3e(hash, y, mon, d, 0): plain digit strings here (year 4 digits: no 2-digit completion)
      h.year = num(iso.group(1).replace("'", "").replace("+", ""));
      if (iso.group(1).contains("-")) {
        h.year = -h.year;
      }
      h.mon = num(iso.group(2));
      String d = iso.group(3).replace("'", "");
      h.mday = d.startsWith("-") ? -num(d.substring(1)) : num(d);
    }
    return h;
  }

  /** Date.parse(value) */
  static RubyDate dateParse(String value) {
    checkLimit(value);
    Frags h = parse(value);
    if (h.year == null || h.mon == null || h.mday == null) {
      throw invalidDate();
    }
    long[] civil = validCivil(h.year, h.mon, h.mday);
    if (civil == null) {
      throw invalidDate();
    }
    return new RubyDate(h.year, (int) civil[1], (int) civil[2], civil[0]);
  }

  /** DateTime.parse(value).to_time */
  static RubyTime dateTimeParseToTime(String value) {
    checkLimit(value);
    Frags h = parse(value);
    if (h.year == null || h.mon == null || h.mday == null) {
      throw invalidDate();
    }
    long[] civil = validCivil(h.year, h.mon, h.mday);
    int hour = h.hour == null ? 0 : h.hour;
    int min = h.min == null ? 0 : h.min;
    int sec = h.sec == null ? 0 : (h.sec == 60 ? 59 : h.sec);
    if (civil == null) {
      throw invalidDate();
    }
    // c_valid_time_p (negative values cannot occur here)
    if (hour > 24 || min > 59 || sec > 59 || (hour == 24 && (min > 0 || sec > 0))) {
      throw invalidDate();
    }
    long df = hour * 3600L + min * 60L + sec;
    long nanos = 0;
    if (h.fracNum != null) {
      nanos = h.fracNum.multiply(BigInteger.valueOf(1_000_000_000L)).divide(h.fracDen).longValue();
    }
    int of = 0;
    if (h.zone != null) {
      Long off = zoneToDiff(h.zone);
      if (off != null) {
        of = off < Integer.MIN_VALUE || off > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) (long) off;
        if (of < -86400 || of > 86400) {
          of = 0; // "invalid offset is ignored"
        }
      }
    }
    // Time.new(year, mon, mday, hour, min, sec + sf, of): validate_utc_offset
    if (of <= -86400 || of >= 86400) {
      throw new YamlException("ArgumentError", "utc_offset out of range");
    }
    long localEpoch = (civil[0] - RubyDate.UNIX_EPOCH_IN_JD) * 86400 + df;
    Instant instant = Instant.ofEpochSecond(localEpoch - of, nanos);
    ZoneOffset zo;
    try {
      zo = ZoneOffset.ofTotalSeconds(of);
    } catch (java.time.DateTimeException e) {
      // RubyTime (java.time) cannot hold offsets beyond ±18:00; Ruby allows up to ±23:59:59.
      zo = ZoneOffset.UTC;
    }
    return RubyTime.fixed(instant, zo);
  }

  /** date_zone_to_diff for the zones TIME_MATCHER admits ("Z", "±h", "±hh", "±hmm", "±hhmm", "±h:mm"). */
  static Long zoneToDiff(String zone) {
    if (zone.equalsIgnoreCase("z") || zone.equalsIgnoreCase("utc") || zone.equalsIgnoreCase("gmt")) {
      return 0L; // zonetab
    }
    String s = zone;
    if (s.length() > 3 && (s.regionMatches(true, 0, "gmt", 0, 3) || s.regionMatches(true, 0, "utc", 0, 3))) {
      s = s.substring(3);
    }
    if (s.isEmpty() || (s.charAt(0) != '+' && s.charAt(0) != '-')) {
      return null;
    }
    boolean sign = s.charAt(0) == '-';
    s = s.substring(1);
    int l = s.length();
    int p = 0;
    while (p < l && Character.isDigit(s.charAt(p))) {
      p++;
    }
    long hour = p == 0 ? 0 : strtoul(s.substring(0, p));
    long min = 0;
    long sec = 0;
    if (p < l && s.charAt(p) == ':') {
      if (hour < 0 || hour > 23) {
        return null;
      }
      int q = ++p;
      while (p < l && Character.isDigit(s.charAt(p))) {
        p++;
      }
      min = p == q ? 0 : strtoul(s.substring(q, p));
      if (min < 0 || min > 59) {
        return null;
      }
      if (p < l && s.charAt(p) == ':') {
        q = ++p;
        while (p < l && Character.isDigit(s.charAt(p))) {
          p++;
        }
        sec = p == q ? 0 : strtoul(s.substring(q, p));
        if (sec < 0 || sec > 59) {
          return null;
        }
      }
    } else if (p < l && (s.charAt(p) == ',' || s.charAt(p) == '.')) {
      return null; // fractional hours: not admitted by TIME_MATCHER
    } else if (l > 2) {
      int hl = 2 - l % 2;
      hour = scanDigits(s, 0, hl);
      if (l >= 3) {
        min = scanDigits(s, hl, 2);
      }
      if (l >= 5) {
        sec = scanDigits(s, 4 - l % 2, 2);
      }
    }
    sec += min * 60 + hour * 3600;
    return sign ? -sec : sec;
  }

  private static long strtoul(String digits) {
    BigInteger b = new BigInteger(digits);
    return b.bitLength() < 62 ? b.longValue() : Long.MAX_VALUE / 4;
  }

  /** ruby_scan_digits(&s[from], n, 10, ...) */
  private static long scanDigits(String s, int from, int n) {
    long v = 0;
    for (int k = from; k < Math.min(s.length(), from + n); k++) {
      char c = s.charAt(k);
      if (c < '0' || c > '9') {
        break;
      }
      v = v * 10 + (c - '0');
    }
    return v;
  }

  // ---- date_core.c: civil date validation and JD conversion (start = ITALY) ----

  private static final int[][] MONTHTAB = {
    {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31},
    {0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31},
  };
  private static final int REFORM_BEGIN_YEAR = 1582;
  private static final int REFORM_END_YEAR = 1930;

  /** rt__valid_civil_p(y, m, d, ITALY): {jd, mon, mday} or null. */
  static long[] validCivil(int y, int m, int d) {
    if (y < REFORM_BEGIN_YEAR) {
      return validJulianOrGregorian(y, m, d, true);
    }
    if (y > REFORM_END_YEAR) {
      return validJulianOrGregorian(y, m, d, false);
    }
    // c_valid_civil_p with sg = ITALY
    if (m < 0) {
      m += 13;
    }
    if (m < 1 || m > 12) {
      return null;
    }
    if (d < 0) {
      // c_find_ldom: the last day of the month that exists in the calendar
      long ldom = -1;
      for (int dd = 31; dd >= 1; dd--) {
        long jd = civilToJd(y, m, dd, ITALY_D);
        int[] c = jdToCivil(jd, ITALY_D);
        if (c[0] == y && c[1] == m && c[2] == dd) {
          ldom = jd;
          break;
        }
      }
      if (ldom < 0) {
        return null;
      }
      int[] c = jdToCivil(ldom + d + 1, ITALY_D);
      if (c[0] != y || c[1] != m) {
        return null;
      }
      d = c[2];
    }
    long jd = civilToJd(y, m, d, ITALY_D);
    int[] c = jdToCivil(jd, ITALY_D);
    if (c[0] != y || c[1] != m || c[2] != d) {
      return null;
    }
    return new long[] {jd, m, d};
  }

  private static final double ITALY_D = RubyDate.ITALY;

  private static boolean julianLeap(int y) {
    return Math.floorMod(y, 4) == 0;
  }

  private static boolean gregorianLeap(int y) {
    return (Math.floorMod(y, 4) == 0 && y % 100 != 0) || Math.floorMod(y, 400) == 0;
  }

  /** c_valid_julian_p / c_valid_gregorian_p + c_civil_to_jd with style ±infinity */
  private static long[] validJulianOrGregorian(int y, int m, int d, boolean julian) {
    if (m < 0) {
      m += 13;
    }
    if (m < 1 || m > 12) {
      return null;
    }
    int last = MONTHTAB[(julian ? julianLeap(y) : gregorianLeap(y)) ? 1 : 0][m];
    if (d < 0) {
      d = last + d + 1;
    }
    if (d < 1 || d > last) {
      return null;
    }
    long jd = civilToJd(y, m, d, julian ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
    return new long[] {jd, m, d};
  }

  /** c_civil_to_jd */
  static long civilToJd(int y, int m, int d, double sg) {
    if (m <= 2) {
      y -= 1;
      m += 12;
    }
    double a = Math.floor(y / 100.0);
    double b = 2 - a + Math.floor(a / 4.0);
    double jd = Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524;
    if (jd < sg) {
      jd -= b;
    }
    return (long) jd;
  }

  /** c_jd_to_civil: {year, mon, mday} */
  static int[] jdToCivil(long jd, double sg) {
    double a;
    if (jd < sg) {
      a = jd;
    } else {
      double x = Math.floor((jd - 1867216.25) / 36524.25);
      a = jd + 1 + x - Math.floor(x / 4.0);
    }
    double b = a + 1524;
    double c = Math.floor((b - 122.1) / 365.25);
    double dd = Math.floor(365.25 * c);
    double e = Math.floor((b - dd) / 30.6001);
    double dom = b - dd - Math.floor(30.6001 * e);
    double m;
    double y;
    if (e <= 13) {
      m = e - 1;
      y = c - 4716;
    } else {
      m = e - 13;
      y = c - 4715;
    }
    return new int[] {(int) y, (int) m, (int) dom};
  }
}
