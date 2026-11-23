package org.hl7.fhir.jekyll.yaml;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.hl7.fhir.jekyll.liquid.RubyTime;

/**
 * A Ruby {@code Date} (what SafeYAML yields for a plain {@code YYYY-MM-DD} scalar): a civil date in
 * Ruby's default calendar (Julian before 1582-10-15, Gregorian after; {@code start} = ITALY), with
 * its chronological Julian Day Number.
 *
 * <p>Ruby semantics the glue should keep: {@code to_s} is "YYYY-MM-DD" ({@link #toS()}); Liquid's
 * {@code date} filter calls {@code Date#strftime} directly ({@link #strftime(String)}: midnight,
 * offset +00:00); Jekyll's {@code date_to_xmlschema} and friends call {@code Date#to_time}
 * ({@link #toTime()}: local midnight).
 */
public final class RubyDate implements Comparable<RubyDate>, org.hl7.fhir.jekyll.liquid.RubyStrftime {
  /** chronological JD of 1970-01-01 */
  static final long UNIX_EPOCH_IN_JD = 2440588;
  /** Date::ITALY */
  static final long ITALY = 2299161;

  private final int year;
  private final int month;
  private final int day;
  private final long jd;

  RubyDate(int year, int month, int day, long jd) {
    this.year = year;
    this.month = month;
    this.day = day;
    this.jd = jd;
  }

  public int year() {
    return year;
  }

  public int month() {
    return month;
  }

  public int day() {
    return day;
  }

  /** Date#jd */
  public long jd() {
    return jd;
  }

  /** The same day in the proleptic Gregorian calendar (java.time). */
  public LocalDate toLocalDate() {
    return LocalDate.ofEpochDay(jd - UNIX_EPOCH_IN_JD);
  }

  /** Date#to_s ("%Y-%m-%d") */
  public String toS() {
    String y = year < 0 ? "-" + String.format(java.util.Locale.ROOT, "%04d", -(long) year) : String.format(java.util.Locale.ROOT, "%04d", year);
    return y + String.format(java.util.Locale.ROOT, "-%02d-%02d", month, day);
  }

  /** Date#inspect */
  public String inspect() {
    return "#<Date: " + toS() + " ((" + jd + "j,0s,0n),+0s," + ITALY + "j)>";
  }

  /** Date#to_time: Time.local(year, mon, mday) of the Gregorian date (local midnight). */
  public RubyTime toTime() {
    return RubyTime.ofInstant(toLocalDate().atStartOfDay(RubyTime.localZone).toInstant());
  }

  /**
   * Date#strftime: formats midnight of this day at offset +00:00 (Date's %Z is "+00:00"; the
   * conversion handles that one directive and defers the rest to the Liquid port's strftime).
   */
  public String strftime(String format) {
    StringBuilder f = new StringBuilder();
    for (int i = 0; i < format.length(); i++) {
      char c = format.charAt(i);
      if (c == '%' && i + 1 < format.length()) {
        char n = format.charAt(i + 1);
        if (n == 'Z') {
          f.append("+00:00");
          i++;
          continue;
        }
        f.append(c).append(n);
        i++;
        continue;
      }
      f.append(c);
    }
    Instant midnight = Instant.ofEpochSecond((jd - UNIX_EPOCH_IN_JD) * 86400);
    return RubyTime.fixed(midnight, ZoneOffset.UTC).strftime(f.toString());
  }

  @Override
  public String toString() {
    return toS();
  }

  @Override
  public int compareTo(RubyDate o) {
    return Long.compare(jd, o.jd);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RubyDate && ((RubyDate) o).jd == jd;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(jd);
  }
}
