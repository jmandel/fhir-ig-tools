package org.hl7.fhir.sushi.fshtypes;

// port of sushi/src/fshtypes/InstanceUsage.ts (SUSHI 3.21.1)
/** TS string union 'Example' | 'Definition' | 'Inline'; {@link #toString()} is the exact TS string. */
public enum InstanceUsage {
  Example, Definition, Inline;

  /** TS isInstanceUsage(s): exact, case-sensitive match. */
  public static boolean isInstanceUsage(String s) {
    return fromString(s) != null;
  }

  /** The usage for the exact TS string, or null. */
  public static InstanceUsage fromString(String s) {
    for (InstanceUsage u : values()) {
      if (u.name().equals(s)) return u;
    }
    return null;
  }
}
