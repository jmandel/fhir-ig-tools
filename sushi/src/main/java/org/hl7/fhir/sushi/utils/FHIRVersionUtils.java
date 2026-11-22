package org.hl7.fhir.sushi.utils;

import java.util.List;
import java.util.regex.Pattern;

// port of sushi/src/utils/FHIRVersionUtils.ts (SUSHI 3.21.1)
public final class FHIRVersionUtils {
  private FHIRVersionUtils() {}

  /** 'DSTU1' | 'DSTU2' | 'STU3' | 'R4' | 'R4B' | 'R5' | 'R6' | '??' */
  public record FHIRVersionInfo(
      String name, String packageId, String version, String packageString, boolean isPreRelease,
      boolean isSupported) {}

  private record VersionMatcher(Pattern regex, String name, String packageId, boolean isPreRelease,
      boolean isSupported) {}

  // JS regex.test = find (the alternations below are not all anchored on both sides, exactly as in TS);
  // JS `$` without the m flag = Java `\\z`
  private static VersionMatcher m(String re, String name, String packageId, boolean pre, boolean supported) {
    return new VersionMatcher(Pattern.compile(re), name, packageId, pre, supported);
  }

  private static final List<VersionMatcher> VERSIONS = List.of(
      // Supported Official Releases
      m("^4\\.0\\.\\d\\z", "R4", "hl7.fhir.r4.core", false, true),
      m("^4\\.3\\.\\d+\\z", "R4B", "hl7.fhir.r4b.core", false, true),
      m("^5\\.\\d+\\.\\d+\\z", "R5", "hl7.fhir.r5.core", false, true),
      // Supported Pre-Releases
      m("^4\\.0\\.\\d+-\\S+\\z", "R4", "hl7.fhir.r4.core", true, true),
      m("^(4\\.1\\.\\d+|4\\.[13]\\.\\d+-\\S+)\\z", "R4B", "hl7.fhir.r4b.core", true, true),
      m("^4\\.[^013]\\.\\d+|5\\.\\d+\\.\\d+-\\S+\\z", "R5", "hl7.fhir.r5.core", true, true),
      m("^dev|current(\\$\\S+)?\\z", "R5", "hl7.fhir.r5.core", true, true),
      // Supported Future Pre-Releases and Releases (well, we'll try)
      m("^6\\.\\d+\\.\\d+\\z", "R6", "hl7.fhir.r6.core", false, true),
      m("^6\\.\\d+\\.\\d+-\\S+\\z", "R6", "hl7.fhir.r6.core", true, true),
      // Unsupported Pre-Releases and Releases
      m("^3\\.[^0][\\da]*\\.\\d+\\z", "R4", "hl7.fhir.r4.core", true, false),
      m("^3\\.0\\.\\d+\\z", "STU3", "hl7.fhir.r3.core", false, false),
      m("^1\\.[^0]\\d*\\.\\d+|3\\.0\\.\\d+-\\S+\\z", "STU3", "hl7.fhir.r3.core", true, false),
      m("^1\\.0\\.[^0]\\d*\\z", "DSTU2", "hl7.fhir.r2.core", false, false),
      m("^0\\.[^01]\\d*\\.\\d+|1\\.0\\.0|1\\.0\\.\\d+-\\S+\\z", "DSTU2", "hl7.fhir.r2.core", true, false),
      m("^0.0.8\\d*\\z", "DSTU1", "hl7.fhir.r1.core", false, false),
      m("^0\\.\\d+|0\\.0\\.[^8]\\d*|0\\.0\\.8\\d*-\\S+\\z", "DSTU1", "hl7.fhir.r1.core", true, false),
      // Catch-All
      m(".*", "??", "hl7.fhir.??.core", false, false));

  /** {@code version} null behaves like JS `undefined` coerced by RegExp.test: the string "undefined". */
  public static FHIRVersionInfo getFHIRVersionInfo(String version) {
    String tested = version == null ? "undefined" : version;
    VersionMatcher match = null;
    for (VersionMatcher v : VERSIONS) {
      if (jsTest(v.regex, tested)) {
        match = v;
        break;
      }
    }
    return new FHIRVersionInfo(match.name, match.packageId, version, match.packageId + "#" + Js_toStr(version),
        match.isPreRelease, match.isSupported);
  }

  private static String Js_toStr(String s) {
    return s == null ? "undefined" : s;
  }

  private static boolean jsTest(Pattern p, String s) {
    return p.matcher(s).find();
  }
}
