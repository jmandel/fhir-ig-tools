package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.Common.canonicalsAreEqualIgnoringVersion;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/canonicalsAreEqualIgnoringVersion.test.ts (SUSHI 3.21.1)
@DisplayName("canonicalsAreEqualIgnoringVersion")
class CanonicalsAreEqualIgnoringVersionTest {
  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should return true for identical URLs with no version")
  void t1() {
    assertTrue(canonicalsAreEqualIgnoringVersion("http://example.org/Foo", "http://example.org/Foo"));
  }

  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should return true when one side is versioned and the other is not")
  void t2() {
    assertTrue(canonicalsAreEqualIgnoringVersion("http://example.org/Foo|0.1.0", "http://example.org/Foo"));
    assertTrue(canonicalsAreEqualIgnoringVersion("http://example.org/Foo", "http://example.org/Foo|0.1.0"));
  }

  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should return true when both sides carry the same version")
  void t3() {
    assertTrue(canonicalsAreEqualIgnoringVersion("http://example.org/Foo|0.1.0", "http://example.org/Foo|0.1.0"));
  }

  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should return true when both sides are versioned but the versions differ")
  void t4() {
    assertTrue(canonicalsAreEqualIgnoringVersion("http://example.org/Foo|0.1.0", "http://example.org/Foo|2.0.0"));
  }

  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should return false when the base URLs differ")
  void t5() {
    assertFalse(canonicalsAreEqualIgnoringVersion("http://example.org/Foo|1", "http://example.org/Bar|1"));
  }

  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should return false when the base URLs differ and neither is versioned")
  void t6() {
    assertFalse(canonicalsAreEqualIgnoringVersion("http://example.org/Foo", "http://example.org/Bar"));
  }

  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should return true for identical URLs that contain a literal pipe (exact-match path)")
  void t7() {
    assertTrue(canonicalsAreEqualIgnoringVersion("http://example.org/Foo|bar", "http://example.org/Foo|bar"));
  }

  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should match a literal-pipe URL against its versioned form, since the version is split at the first pipe")
  void t8() {
    assertTrue(canonicalsAreEqualIgnoringVersion("http://example.org/Foo|bar", "http://example.org/Foo|bar|0.1.0"));
  }

  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should return false (no throw) when exactly one input is null or undefined")
  void t9() {
    assertFalse(canonicalsAreEqualIgnoringVersion(null, "http://example.org/Foo"));
    assertFalse(canonicalsAreEqualIgnoringVersion("http://example.org/Foo", null));
  }

  @Test
  @DisplayName("canonicalsAreEqualIgnoringVersion should return true when both inputs are null (identical via exact match)")
  void t10() {
    assertTrue(canonicalsAreEqualIgnoringVersion(null, null));
  }
}
