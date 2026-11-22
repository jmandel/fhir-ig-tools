package org.hl7.fhir.sushi.testhelpers;

import org.hl7.fhir.sushi.utils.FshLogger;

// port of sushi/test/testhelpers/withDebugLogging.ts (SUSHI 3.21.1)
public final class WithDebugLogging {
  private WithDebugLogging() {}

  public static void withDebugLogging(Runnable fn) {
    String originalLevel = FshLogger.logger.level();
    FshLogger.logger.setLevel("debug");
    try {
      fn.run();
    } finally {
      // make sure we set the logger back so we don't mess up other tests
      FshLogger.logger.setLevel(originalLevel);
    }
  }
}
