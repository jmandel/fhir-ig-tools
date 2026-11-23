package org.hl7.fhir.sushi.utils;

import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.*;
import static org.hl7.fhir.sushi.utils.Puns.getPuns;
import static org.hl7.fhir.sushi.utils.Puns.getRandomPun;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.*;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.common.js.*;
import org.hl7.fhir.sushi.testhelpers.*;
import org.hl7.fhir.sushi.utils.*;
import org.junit.jupiter.api.*;

// port of sushi/test/utils/puns.test.ts (SUSHI 3.21.1)
@DisplayName("puns")
class PunsTest {
  static final String CLEAN_PUN = "You are dolphinitely doing great!";
  static final String WARN_PUN = "Not bad, but you cod do batter!";
  static final String ERR_PUN = "This was a turtle disaster.";

  @DisplayName("#getPuns")
  @Nested
  class GetPuns {

    @Test
    @DisplayName("puns #getPuns should get all puns when no arguments are provided")
    void shouldGetAllPunsWhenNoArgumentsAreProvided() {
      var puns = getPuns();
      assertContains(CLEAN_PUN, puns);
      assertContains(WARN_PUN, puns);
      assertContains(ERR_PUN, puns);
    }

    @Test
    @DisplayName("puns #getPuns should get only clean puns when asked for")
    void shouldGetOnlyCleanPunsWhenAskedFor() {
      var puns = getPuns(false, false, true);
      assertContains(CLEAN_PUN, puns);
      assertNotContains(WARN_PUN, puns);
      assertNotContains(ERR_PUN, puns);
    }

    @Test
    @DisplayName("puns #getPuns should get only warning puns when asked for")
    void shouldGetOnlyWarningPunsWhenAskedFor() {
      var puns = getPuns(false, true, false);
      assertNotContains(CLEAN_PUN, puns);
      assertContains(WARN_PUN, puns);
      assertNotContains(ERR_PUN, puns);
    }

    @Test
    @DisplayName("puns #getPuns should get only error puns when asked for")
    void shouldGetOnlyErrorPunsWhenAskedFor() {
      var puns = getPuns(true, false, false);
      assertNotContains(CLEAN_PUN, puns);
      assertNotContains(WARN_PUN, puns);
      assertContains(ERR_PUN, puns);
    }

    @Test
    @DisplayName("puns #getPuns should get no puns when none are asked for")
    void shouldGetNoPunsWhenNoneAreAskedFor() {
      var puns = getPuns(false, false, false);
      assertEquals(0, len(puns));
    }

  }
  @DisplayName("#getRandomPun")
  @Nested
  class GetRandomPun {

    @Test
    @DisplayName("puns #getRandomPun should get a clean run pun when there are no errors or warnings")
    void shouldGetACleanRunPunWhenThereAreNoErrorsOrWarnings() {
      var pun = getRandomPun(0, 0);
      // a little fishy since it uses getPuns, but it still seems the best way
      assertContains(pun, getPuns(false, false, true));
    }

    @Test
    @DisplayName("puns #getRandomPun should get a warning pun when there are no errors but there are warnings")
    void shouldGetAWarningPunWhenThereAreNoErrorsButThereAreWarnings() {
      var pun = getRandomPun(0, 1);
      // a little fishy since it uses getPuns, but it still seems the best way
      assertContains(pun, getPuns(false, true, false));
    }

    @Test
    @DisplayName("puns #getRandomPun should get an error pun when there are errors")
    void shouldGetAnErrorPunWhenThereAreErrors() {
      var pun = getRandomPun(1, 1);
      // a little fishy since it uses getPuns, but it still seems the best way
      assertContains(pun, getPuns(true, false, false));
    }

  }
}
