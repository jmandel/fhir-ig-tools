package org.hl7.fhir.sushi.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/utils/FHIRVersionUtils.test.ts (SUSHI 3.21.1)
@DisplayName("FHIRVersionUtils")
class FHIRVersionUtilsTest {
  static void check(List<String> versions, String name, String packageId, boolean pre, boolean supported) {
    for (String v : versions) {
      assertEquals(new FHIRVersionUtils.FHIRVersionInfo(name, packageId, v, packageId + "#" + v, pre, supported),
          FHIRVersionUtils.getFHIRVersionInfo(v), v);
    }
  }

  @Nested
  @DisplayName("#getFHIRVersionInfo")
  class GetFHIRVersionInfo {
    // See: https://hl7.org/fhir/directory.html

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get DSTU1 info for DSTU1 pre-releases")
    void t0() {
      check(List.of("0.00", "0.01", "0.05", "0.06", "0.11", "0.0.83-snapshot1"), "DSTU1", "hl7.fhir.r1.core", true, false);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get DSTU1 info for DSTU1 final releases")
    void t1() {
      check(List.of("0.0.80", "0.0.81", "0.0.82", "0.0.83"), "DSTU1", "hl7.fhir.r1.core", false, false);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get DSTU2 info for DSTU2 pre-releases")
    void t2() {
      check(List.of("0.4.0", "0.5.0", "1.0.0", "1.0.3-snapshot1"), "DSTU2", "hl7.fhir.r2.core", true, false);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get DSTU2 info for DSTU2 final releases")
    void t3() {
      check(List.of("1.0.1", "1.0.2", "1.0.3"), "DSTU2", "hl7.fhir.r2.core", false, false);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get STU3 info for STU3 pre-releases")
    void t4() {
      check(List.of("1.1.0", "1.2.0", "1.4.0", "1.6.0", "1.8.0", "3.0.3-snapshot1"), "STU3", "hl7.fhir.r3.core", true, false);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get STU3 info for STU3 final releases")
    void t5() {
      check(List.of("3.0.0", "3.0.1", "3.0.2", "3.0.3"), "STU3", "hl7.fhir.r3.core", false, false);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get R4 info for R4 pre-releases")
    void t6() {
      check(List.of("3.2.0", "3.3.0", "3.5.0", "3.5a.0"), "R4", "hl7.fhir.r4.core", true, false);
      check(List.of("4.0.2-snapshot1"), "R4", "hl7.fhir.r4.core", true, true);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get R4 info for R4 final releases")
    void t7() {
      check(List.of("4.0.0", "4.0.1", "4.0.2"), "R4", "hl7.fhir.r4.core", false, true);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get R4B info for R4B pre-releases")
    void t8() {
      check(List.of("4.1.0", "4.3.0-snapshot1"), "R4B", "hl7.fhir.r4b.core", true, true);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get R4B info for R4B final releases")
    void t9() {
      check(List.of("4.3.0", "4.3.1"), "R4B", "hl7.fhir.r4b.core", false, true);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get R5 info for R5 pre-releases")
    void t10() {
      check(List.of("4.2.0", "4.4.0", "4.5.0", "4.6.0", "5.0.0-snapshot1", "5.0.0-ballot", "5.0.0-snapshot3", "5.0.0-draft-final"), "R5", "hl7.fhir.r5.core", true, true);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get R5 info for R5 final releases")
    void t11() {
      check(List.of("5.0.0", "5.0.1"), "R5", "hl7.fhir.r5.core", false, true);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get R5 info for current and development releases")
    void t12() {
      check(List.of("current", "current$branch1", "dev"), "R5", "hl7.fhir.r5.core", true, true);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get R6 info for future R6 releases")
    void t13() {
      check(List.of("6.0.0", "6.0.1"), "R6", "hl7.fhir.r6.core", false, true);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should get R6 info for future R6 pre-releases")
    void t14() {
      check(List.of("6.0.0-snapshot1", "6.0.0-ballot"), "R6", "hl7.fhir.r6.core", true, true);
    }

    @Test
    @DisplayName("FHIRVersionUtils #getFHIRVersionInfo should default ?? info for unknown versions")
    void t15() {
      check(List.of("2.0.0", "funky", "999"), "??", "hl7.fhir.??.core", false, false);
    }
  }
}
