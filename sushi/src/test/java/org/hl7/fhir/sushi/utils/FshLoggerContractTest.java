package org.hl7.fhir.sushi.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Semantics observed from SUSHI 3.21.1's FSHLogger under node (see FshLogger Javadoc). Full test port: WP-10. */
class FshLoggerContractTest {
  final FshLogger log = FshLogger.logger;
  final List<String> printed = new ArrayList<>();

  @BeforeEach void setUp() {
    log.resetForTest();
    log.setSink(e -> printed.add(e.level().label() + " " + e.message()));
  }

  @AfterEach void tearDown() {
    log.resetForTest();
  }

  @Test void countsEvenUnprintedLevelsAndFormatsLocations() {
    SourceInfo src = new SourceInfo("a.fsh", new TextLocation(1, 1, 2, 3));
    log.debug("dbg");
    log.info("inf");
    log.warn("w", src);
    log.error("e");
    assertEquals(List.of("info  inf", "warn  w\n  File: a.fsh\n  Line: 1 - 2", "error e"), printed);
    assertEquals(1, log.stats.numDebug);
    assertEquals(1, log.stats.numInfo);
    assertEquals(1, log.stats.numWarn);
    assertEquals(1, log.stats.numError);
    log.setLevel("emerg");
    log.error("hidden");
    assertEquals(2, log.stats.numError);
    assertEquals(3, printed.size());
  }

  @Test void ignoresExactAndRegexLines() {
    log.setIgnoredWarnings("# comment\n  exact warning  \n/^Pattern \\d+/\n");
    log.warn("exact warning");
    log.warn("Pattern 42 matched");
    log.warn("other");
    assertEquals(List.of("warn  other"), printed);
    assertEquals(2, log.stats.numIgnoredWarn);
    assertEquals(1, log.stats.numWarn);
  }

  @Test void secretLoggerTracksAndRestores() {
    log.error("before");
    FshLogger.LoggerData saved = log.switchToSecretLogger();
    log.warn("secret w");
    log.error("secret e");
    assertEquals(1, printed.size());
    FshLogger.ErrorsAndWarnings secret = log.restoreMainLogger(saved);
    assertEquals("secret e", secret.errors.get(0).message());
    assertEquals("secret w", secret.warnings.get(0).message());
    assertEquals(1, log.stats.numError);
    assertEquals(0, log.stats.numWarn);
  }
}
