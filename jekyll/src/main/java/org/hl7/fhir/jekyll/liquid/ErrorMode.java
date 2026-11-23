package org.hl7.fhir.jekyll.liquid;

/** Liquid's {@code error_mode}: :lax, :warn, :strict. Jekyll uses WARN. */
public enum ErrorMode {
  LAX, WARN, STRICT;

  public static ErrorMode of(String s) {
    return s == null ? LAX : valueOf(s.toUpperCase(java.util.Locale.ROOT));
  }
}
