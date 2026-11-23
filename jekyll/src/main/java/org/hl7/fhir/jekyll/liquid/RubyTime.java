package org.hl7.fhir.jekyll.liquid;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A Ruby {@code Time}: an instant plus a UTC offset, flagged UTC (Time#utc?) or local, with a zone
 * abbreviation (nil for fixed-offset times). Local times use {@link #localZone} (the process TZ,
 * like Ruby's ENV['TZ']).
 */
public final class RubyTime implements Comparable<RubyTime> {
  /** Ruby's local time zone (ENV["TZ"]); defaults to the JVM default zone. */
  public static volatile ZoneId localZone = ZoneId.systemDefault();

  private final long epochSecond;
  private final int nano;
  private final ZoneOffset offset;
  private final boolean utc;
  private final String zone;

  private RubyTime(long epochSecond, int nano, ZoneOffset offset, boolean utc, String zone) {
    this.epochSecond = epochSecond;
    this.nano = nano;
    this.offset = offset;
    this.utc = utc;
    this.zone = zone;
  }

  public static RubyTime now() {
    return ofInstant(Instant.now());
  }

  /** Ruby {@code Time.at(seconds)} (local). */
  public static RubyTime at(long seconds) {
    return ofInstant(Instant.ofEpochSecond(seconds));
  }

  /** A local time for the instant. */
  public static RubyTime ofInstant(Instant i) {
    ZonedDateTime z = i.atZone(localZone);
    return new RubyTime(i.getEpochSecond(), i.getNano(), z.getOffset(), false, zoneAbbrev(z));
  }

  public static RubyTime utc(Instant i) {
    return new RubyTime(i.getEpochSecond(), i.getNano(), ZoneOffset.UTC, true, "UTC");
  }

  /** A time with a fixed offset and no zone name (Ruby {@code Time.new(..., "+02:00")}). */
  public static RubyTime fixed(Instant i, ZoneOffset off) {
    return new RubyTime(i.getEpochSecond(), i.getNano(), off, false, null);
  }

  /** From a ZonedDateTime: its zone becomes the zone name. */
  public static RubyTime of(ZonedDateTime z) {
    Instant i = z.toInstant();
    if (z.getZone().equals(ZoneOffset.UTC) || z.getZone().getId().equals("UTC")) {
      return utc(i);
    }
    return new RubyTime(i.getEpochSecond(), i.getNano(), z.getOffset(), false, z.getZone() instanceof ZoneOffset ? null : zoneAbbrev(z));
  }

  static String zoneAbbrev(ZonedDateTime z) {
    String id = z.getZone().getId();
    if (id.equals("UTC") || id.equals("Z") || id.equals("Etc/UTC") || id.equals("GMT") && false) {
      return "UTC";
    }
    return DateTimeFormatter.ofPattern("zzz", Locale.US).format(z);
  }

  public long epochSecond() {
    return epochSecond;
  }

  public int nano() {
    return nano;
  }

  public ZoneOffset offset() {
    return offset;
  }

  public boolean isUtc() {
    return utc;
  }

  /** Zone abbreviation or null. */
  public String zone() {
    return zone;
  }

  public Instant instant() {
    return Instant.ofEpochSecond(epochSecond, nano);
  }

  /** Wall-clock fields in this time's offset. */
  public LocalDateTime wall() {
    return LocalDateTime.ofEpochSecond(epochSecond, nano, offset);
  }

  public String strftime(String format) {
    return Strftime.format(this, format);
  }

  /** Ruby {@code Time#to_s}. */
  public String toS() {
    return strftime(utc ? "%Y-%m-%d %H:%M:%S UTC" : "%Y-%m-%d %H:%M:%S %z");
  }

  /** Ruby {@code Time#inspect} (adds fractional seconds). */
  public String inspect() {
    String base = strftime("%Y-%m-%d %H:%M:%S");
    if (nano != 0) {
      String f = String.format(java.util.Locale.ROOT, "%09d", nano).replaceAll("0+$", "");
      base += "." + f;
    }
    return base + (utc ? " UTC" : strftime(" %z"));
  }

  @Override
  public String toString() {
    return toS();
  }

  @Override
  public int compareTo(RubyTime o) {
    int c = Long.compare(epochSecond, o.epochSecond);
    return c != 0 ? c : Integer.compare(nano, o.nano);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RubyTime && compareTo((RubyTime) o) == 0;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(epochSecond) * 31 + nano;
  }
}
