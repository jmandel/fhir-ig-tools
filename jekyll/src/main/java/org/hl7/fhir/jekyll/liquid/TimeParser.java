package org.hl7.fhir.jekyll.liquid;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ruby's {@code Time.parse} (time.rb {@code make_time}/{@code force_zone!}) on top of a subset of
 * {@code Date._parse} (date_parse.c): weekday, time-of-day with zone, and the eu / us / iso / vms /
 * sla / dot / mon / mday / ddd date forms, applied in Ruby's order with matched text blanked out.
 * Failures raise ::ArgumentError (which Liquid's {@code to_date} turns into "leave the input as is").
 */
final class TimeParser {
  private TimeParser() {}

  private static final int CI = Pattern.CASE_INSENSITIVE;
  private static final String MONTHS = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec";
  private static final String DAYS = "sun|mon|tue|wed|thu|fri|sat";
  private static final String ERA = "c(?:e|\\.e\\.)|b(?:ce|\\.c\\.e\\.)|a(?:d|\\.d\\.)|b(?:c|\\.c\\.)";

  private static final Pattern NOISE = Pattern.compile("[^-+',./:@\\p{Alnum}\\[\\]]+", Pattern.UNIX_LINES);
  private static final Pattern DAY = Pattern.compile("\\b(" + DAYS + ")[^-/\\d\\s]*", CI | Pattern.UNIX_LINES);
  private static final Pattern TIME = Pattern.compile(
      "((?:\\d+\\s*:\\s*\\d+(?:\\s*:\\s*\\d+(?:[,.]\\d*)?)?|\\d+\\s*h(?:\\s*\\d+m?(?:\\s*\\d+s?)?)?)(?:\\s*[ap](?:m\\b|\\.m\\.))?|\\d+\\s*[ap](?:m\\b|\\.m\\.))"
          + "(?:\\s*((?:gmt|utc?)?[-+]\\d+(?:[,.:]\\d+(?::\\d+)?)?|(?-i:[\\p{Alpha}.\\s]+)(?:standard|daylight)\\stime\\b|(?-i:[\\p{Alpha}]+)(?:\\sdst)?\\b))?",
      CI | Pattern.UNIX_LINES);
  private static final Pattern TIME2 = Pattern.compile(
      "\\A(\\d+)h?(?:\\s*:?\\s*(\\d+)m?(?:\\s*:?\\s*(\\d+)(?:[,.](\\d+))?s?)?)?(?:\\s*([ap])(?:m\\b|\\.m\\.))?", CI | Pattern.UNIX_LINES);
  private static final Pattern EU = Pattern.compile(
      "'?\\b(\\d+)[^-\\d\\s']*\\s*(" + MONTHS + ")[^-\\d\\s']*(?:\\s*(?:\\s*,\\s*)?(" + ERA + ")?\\s*('?-?\\d+(?:(?:st|nd|rd|th)\\b)?))?", CI | Pattern.UNIX_LINES);
  private static final Pattern US = Pattern.compile(
      "\\b(" + MONTHS + ")[^-\\d\\s']*\\s*('?\\d+)[^-\\d\\s']*(?:\\s*,?\\s*(" + ERA + ")?\\s*('?-?\\d+))?", CI | Pattern.UNIX_LINES);
  private static final Pattern ISO = Pattern.compile("('?[-+]?\\d+)-(\\d+)-('?-?\\d+)", Pattern.UNIX_LINES);
  private static final Pattern VMS = Pattern.compile("('?-?\\d+)-(" + MONTHS + ")[^-/.]*-('?-?\\d+)", CI | Pattern.UNIX_LINES);
  private static final Pattern SLA = Pattern.compile("('?-?\\d+)/\\s*('?\\d+)(?:\\D\\s*('?-?\\d+))?", Pattern.UNIX_LINES);
  private static final Pattern DOT = Pattern.compile("('?-?\\d+)\\.\\s*('?\\d+)\\.\\s*('?-?\\d+)", Pattern.UNIX_LINES);
  private static final Pattern MON = Pattern.compile("\\b(" + MONTHS + ")\\S*", CI | Pattern.UNIX_LINES);
  private static final Pattern MDAY = Pattern.compile("(\\d+)(st|nd|rd|th)\\b", CI | Pattern.UNIX_LINES);
  private static final Pattern DDD = Pattern.compile(
      "([-+]?)(\\d{2,14})(?:\\s*t?\\s*(\\d{2,6})?(?:[,.](\\d*))?)?(?:\\s*(z\\b|[-+]\\d{1,4}\\b|\\[[-+]?\\d[^\\]]*\\]))?", CI | Pattern.UNIX_LINES);

  private static final class H {
    Integer year, mon, mday, hour, min, sec;
    Long fracNanos;
    String zone;
    boolean comp = true;
    boolean any() {
      return year != null || mon != null || mday != null || hour != null || min != null || sec != null || fracNanos != null;
    }
  }

  static RubyTime parse(String date) {
    H h = new H();
    StringBuilder str = new StringBuilder(NOISE.matcher(date).replaceAll(" "));
    subx(str, DAY, m -> true);
    subx(str, TIME, m -> {
      parseTime2(h, m.group(1));
      if (m.group(2) != null) {
        h.zone = m.group(2);
      }
      return true;
    });
    if (!(subx(str, EU, m -> {
          s3e(h, m.group(4), String.valueOf(mon(m.group(2))), m.group(1), m.group(3) != null && m.group(3).toLowerCase(Locale.ROOT).startsWith("b"));
          return true;
        })
        || subx(str, US, m -> {
          s3e(h, m.group(4), String.valueOf(mon(m.group(1))), m.group(2), m.group(3) != null && m.group(3).toLowerCase(Locale.ROOT).startsWith("b"));
          return true;
        })
        || subx(str, ISO, m -> {
          s3e(h, m.group(1), m.group(2), m.group(3), false);
          return true;
        })
        || subx(str, VMS, m -> {
          s3e(h, m.group(3), String.valueOf(mon(m.group(2))), m.group(1), false);
          return true;
        })
        || subx(str, SLA, m -> {
          s3e(h, m.group(1), m.group(2), m.group(3), false);
          return true;
        })
        || subx(str, DOT, m -> {
          s3e(h, m.group(1), m.group(2), m.group(3), false);
          return true;
        })
        || subx(str, MON, m -> {
          h.mon = mon(m.group(1));
          return true;
        })
        || subx(str, MDAY, m -> {
          h.mday = Integer.parseInt(m.group(1));
          return true;
        })
        || subx(str, DDD, m -> parseDdd(h, m)))) {
      // nothing matched
    }
    if (h.comp && h.year != null && h.year >= 0 && h.year <= 99) {
      h.year += h.year >= 69 ? 1900 : 2000;
    }
    return makeTime(date, h);
  }

  private interface Cb {
    boolean apply(Matcher m);
  }

  /** Ruby {@code subx}: on match, run the callback and blank the matched text. */
  private static boolean subx(StringBuilder str, Pattern p, Cb cb) {
    Matcher m = p.matcher(str);
    if (!m.find()) {
      return false;
    }
    if (!cb.apply(m)) {
      return false;
    }
    int s = m.start(), e = m.end();
    StringBuilder rep = new StringBuilder();
    for (int k = s; k < e; k++) {
      rep.append(' ');
    }
    str.replace(s, e, rep.toString());
    return true;
  }

  private static int mon(String name) {
    return MONTHS.indexOf(name.substring(0, 3).toLowerCase(Locale.ROOT)) / 4 + 1;
  }

  private static void parseTime2(H h, String s) {
    Matcher m = TIME2.matcher(s);
    if (!m.find()) {
      return;
    }
    int hour = Integer.parseInt(m.group(1));
    if (m.group(5) != null) {
      hour %= 12;
      if (m.group(5).equalsIgnoreCase("p")) {
        hour += 12;
      }
    }
    h.hour = hour;
    if (m.group(2) != null) {
      h.min = Integer.parseInt(m.group(2));
    }
    if (m.group(3) != null) {
      h.sec = Integer.parseInt(m.group(3));
    }
    if (m.group(4) != null) {
      String f = (m.group(4) + "000000000").substring(0, 9);
      h.fracNanos = Long.parseLong(f);
    }
  }

  private static boolean parseDdd(H h, Matcher m) {
    String s2 = m.group(2);
    int l = s2.length();
    switch (l) {
      case 2:
        if (m.group(3) == null && m.group(4) != null) {
          h.sec = Integer.parseInt(s2);
        } else {
          h.mday = Integer.parseInt(s2);
        }
        break;
      case 4:
        if (m.group(3) == null && m.group(4) != null) {
          h.sec = Integer.parseInt(s2.substring(2, 4));
          h.min = Integer.parseInt(s2.substring(0, 2));
        } else {
          h.mon = Integer.parseInt(s2.substring(0, 2));
          h.mday = Integer.parseInt(s2.substring(2, 4));
        }
        break;
      case 6:
        if (m.group(3) == null && m.group(4) != null) {
          h.sec = Integer.parseInt(s2.substring(4, 6));
          h.min = Integer.parseInt(s2.substring(2, 4));
          h.hour = Integer.parseInt(s2.substring(0, 2));
        } else {
          h.year = Integer.parseInt(m.group(1) + s2.substring(0, 2));
          h.mon = Integer.parseInt(s2.substring(2, 4));
          h.mday = Integer.parseInt(s2.substring(4, 6));
        }
        break;
      case 8:
      case 10:
      case 12:
      case 14:
        if (m.group(3) == null && m.group(4) != null) {
          return false;
        }
        h.year = Integer.parseInt(m.group(1) + s2.substring(0, 4));
        h.mon = Integer.parseInt(s2.substring(4, 6));
        h.mday = Integer.parseInt(s2.substring(6, 8));
        if (l >= 10) {
          h.hour = Integer.parseInt(s2.substring(8, 10));
        }
        if (l >= 12) {
          h.min = Integer.parseInt(s2.substring(10, 12));
        }
        if (l >= 14) {
          h.sec = Integer.parseInt(s2.substring(12, 14));
        }
        h.comp = false;
        break;
      case 3:
      case 7:
        return false; // yday forms: not supported
      default:
        return false;
    }
    String s3 = m.group(3);
    if (s3 != null && !s3.isEmpty()) {
      int l3 = s3.length();
      if (m.group(4) != null) {
        // fraction applies to the last time unit
      }
      h.hour = Integer.parseInt(s3.substring(0, 2));
      if (l3 >= 4) {
        h.min = Integer.parseInt(s3.substring(2, 4));
      }
      if (l3 >= 6) {
        h.sec = Integer.parseInt(s3.substring(4, 6));
      }
    }
    if (m.group(4) != null && !m.group(4).isEmpty()) {
      h.fracNanos = Long.parseLong((m.group(4) + "000000000").substring(0, 9));
    }
    if (m.group(5) != null) {
      String z = m.group(5);
      if (z.startsWith("[")) {
        z = z.substring(1, z.length() - 1);
      }
      h.zone = z;
    }
    return true;
  }

  /** date_parse.c s3e: assign year/mon/mday from three strings with Ruby's swapping heuristics. */
  private static void s3e(H h, String y, String m, String d, boolean bc) {
    if (y != null && m != null && d == null) {
      String oy = y, om = m;
      y = null;
      m = oy;
      d = om;
      // (y, m, d) = (d, y, m)
    }
    if (y == null) {
      if (d != null && d.length() > 2) {
        y = d;
        d = null;
      }
      if (d != null && d.startsWith("'")) {
        y = d;
        d = null;
      }
    }
    if (y != null) {
      int s = 0;
      while (s < y.length() && !isSign(y.charAt(s)) && !Character.isDigit(y.charAt(s))) {
        s++;
      }
      int bp = s;
      if (s < y.length() && isSign(y.charAt(s))) {
        s++;
      }
      int e = s;
      while (e < y.length() && Character.isDigit(y.charAt(e))) {
        e++;
      }
      if (e < y.length()) {
        String od = d;
        d = y.substring(bp);
        y = od;
      }
    }
    if (m != null && (m.startsWith("'") || m.length() > 2)) {
      String oy = y, om = m, od = d;
      y = om;
      m = od;
      d = oy;
    }
    if (d != null && (d.startsWith("'") || d.length() > 2)) {
      String oy = y, od = d;
      y = od;
      d = oy;
    }
    if (y != null) {
      String s = y;
      boolean comp = true;
      int i = 0;
      while (i < s.length() && !isSign(s.charAt(i)) && !Character.isDigit(s.charAt(i))) {
        i++;
      }
      boolean neg = false;
      if (i < s.length() && isSign(s.charAt(i))) {
        neg = s.charAt(i) == '-';
        comp = false;
        i++;
      }
      int st = i;
      while (i < s.length() && Character.isDigit(s.charAt(i))) {
        i++;
      }
      if (i - st > 2) {
        comp = false;
      }
      if (i > st) {
        int iy = Integer.parseInt(s.substring(st, Math.min(i, st + 9)));
        if (neg) {
          iy = -iy;
        }
        if (bc) {
          iy = -iy + 1;
        }
        h.year = iy;
        h.comp = comp && h.comp;
      }
    }
    if (m != null) {
      Integer im = digitsOf(m);
      if (im != null) {
        h.mon = im;
      }
    }
    if (d != null) {
      Integer id = digitsOf(d);
      if (id != null) {
        h.mday = id;
      }
    }
  }

  private static Integer digitsOf(String s) {
    int i = 0;
    while (i < s.length() && !Character.isDigit(s.charAt(i))) {
      i++;
    }
    int st = i;
    while (i < s.length() && Character.isDigit(s.charAt(i))) {
      i++;
    }
    return i > st ? Integer.parseInt(s.substring(st, Math.min(i, st + 9))) : null;
  }

  private static boolean isSign(char c) {
    return c == '-' || c == '+';
  }

  private static final Map<String, Integer> ZONES = new HashMap<>();

  static {
    String[][] z = {
      {"ut", "0"}, {"gmt", "0"}, {"est", "-5"}, {"edt", "-4"}, {"cst", "-6"}, {"cdt", "-5"}, {"mst", "-7"}, {"mdt", "-6"},
      {"pst", "-8"}, {"pdt", "-7"}, {"utc", "0"}, {"z", "0"}, {"a", "1"}, {"b", "2"}, {"c", "3"}, {"d", "4"}, {"e", "5"},
      {"f", "6"}, {"g", "7"}, {"h", "8"}, {"i", "9"}, {"k", "10"}, {"l", "11"}, {"m", "12"}, {"n", "-1"}, {"o", "-2"},
      {"p", "-3"}, {"q", "-4"}, {"r", "-5"}, {"s", "-6"}, {"t", "-7"}, {"u", "-8"}, {"v", "-9"}, {"w", "-10"}, {"x", "-11"},
      {"y", "-12"}
    };
    for (String[] e : z) {
      ZONES.put(e[0], (int) Math.round(Double.parseDouble(e[1]) * 3600));
    }
  }

  /** time.rb zone_offset: [+-]hh:mm[:ss], [+-]hhmm[ss], [+-]hh. */
  private static final Pattern NUMZONE = Pattern.compile("\\A([+-])(\\d\\d)(?:(:?)(\\d\\d)(?:\\3(\\d\\d))?)?\\z", Pattern.UNIX_LINES);

  /** time.rb zone_offset / date_zone_to_diff; null when unknown. */
  static Integer zoneOffset(String zone) {
    String z = zone.trim().toLowerCase(Locale.ROOT);
    Integer v = ZONES.get(z);
    if (v != null) {
      return v;
    }
    Matcher m = NUMZONE.matcher(zone.trim());
    if (m.find()) {
      int sign = m.group(1).equals("-") ? -1 : 1;
      int hh = Integer.parseInt(m.group(2));
      int mm = m.group(4) != null ? Integer.parseInt(m.group(4)) : 0;
      int ss = m.group(5) != null ? Integer.parseInt(m.group(5)) : 0;
      return sign * (hh * 3600 + mm * 60 + ss);
    }
    return null;
  }

  private static final Pattern ZONE_UTC = Pattern.compile("\\A(?:-00:00|-0000|-00|UTC|Z|UT)\\z", CI | Pattern.UNIX_LINES);

  /** time.rb make_time + force_zone!. */
  private static RubyTime makeTime(String date, H h) {
    if (!h.any()) {
      throw RubyError.argumentError("no time information in " + Values.inspect(date));
    }
    Integer off = h.zone != null ? zoneOffset(h.zone) : null;
    LocalDateTime now;
    RubyTime nowT = RubyTime.now();
    if (off != null) {
      now = LocalDateTime.ofEpochSecond(nowT.epochSecond(), nowT.nano(), ZoneOffset.ofTotalSeconds(off));
    } else {
      now = nowT.wall();
    }
    Integer year = h.year, mon = h.mon, day = h.mday, hour = h.hour, min = h.min, sec = h.sec;
    Long nanos = h.fracNanos;
    do {
      if (year != null) break;
      year = now.getYear();
      if (mon != null) break;
      mon = now.getMonthValue();
      if (day != null) break;
      day = now.getDayOfMonth();
      if (hour != null) break;
      hour = now.getHour();
      if (min != null) break;
      min = now.getMinute();
      if (sec != null) break;
      sec = now.getSecond();
      if (nanos != null) break;
      nanos = (long) now.getNano() / 1000 * 1000;
    } while (false);
    if (mon == null) mon = 1;
    if (day == null) day = 1;
    if (hour == null) hour = 0;
    if (min == null) min = 0;
    if (sec == null) sec = 0;
    if (nanos == null) nanos = 0L;
    if (mon < 1 || mon > 12 || day < 1 || day > 31 || min < 0 || min > 59 || sec < 0 || sec > 60) {
      throw RubyError.argumentError("argument out of range");
    }
    if (hour < 0 || hour > 24 || hour == 24 && (min != 0 || sec != 0)) {
      throw RubyError.argumentError(hour > 24 ? "hour out of range" : "argument out of range");
    }
    LocalDateTime ldt = LocalDateTime.of(year, mon, 1, 0, 0, 0)
        .plusDays(day - 1).plusHours(hour).plusMinutes(min).plusSeconds(sec).plusNanos(nanos);
    if (h.zone != null && ZONE_UTC.matcher(h.zone.trim()).find()) {
      return RubyTime.utc(ldt.toInstant(ZoneOffset.UTC));
    }
    if (off != null) {
      Instant i = ldt.toInstant(ZoneOffset.ofTotalSeconds(off));
      RubyTime local = RubyTime.ofInstant(i);
      if (local.offset().getTotalSeconds() == off) {
        return local;
      }
      return RubyTime.fixed(i, ZoneOffset.ofTotalSeconds(off));
    }
    // local time; ambiguous/gap handling follows java.time's defaults
    Instant i = ldt.atZone(RubyTime.localZone).toInstant();
    return RubyTime.ofInstant(i);
  }
}
