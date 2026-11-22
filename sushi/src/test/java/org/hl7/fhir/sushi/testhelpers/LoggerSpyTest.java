package org.hl7.fhir.sushi.testhelpers;

import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggerSpyTest {
  @BeforeEach void reset() {
    FshLogger.logger.resetForTest();
    loggerSpy.reset();
  }

  @Test void capturesAllLevelsWithLocations() {
    FshLogger.logger.debug("d");
    FshLogger.logger.error("e", new SourceInfo("x.fsh", new TextLocation(3, 1, 3, 9)));
    FshLogger.logger.switchToSecretLogger();
    FshLogger.logger.warn("w");
    assertEquals(List.of("d", "e\n  File: x.fsh\n  Line: 3", "w"), loggerSpy.getAllMessages());
    assertEquals("e\n  File: x.fsh\n  Line: 3", loggerSpy.getLastMessage("error"));
    assertEquals("w", loggerSpy.getLastLog().message());
    assertNull(loggerSpy.getFirstMessage("info"));
    assertEquals(1, loggerSpy.getAllLogs("warn").size());
  }
}
