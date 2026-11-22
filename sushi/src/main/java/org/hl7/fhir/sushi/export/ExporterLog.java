package org.hl7.fhir.sushi.export;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.utils.FshLogger;

/**
 * The TS idiom {@code logger.error(e.message, sourceInfo); if (e.stack) logger.debug(e.stack);} used by every
 * exporter's catch blocks. The debug line is only printed at debug level but is always counted (FshLogger, D5).
 */
final class ExporterLog {
  private ExporterLog() {}

  static void errorWithStack(Throwable e, SourceInfo sourceInfo) {
    FshLogger.logger.error(e.getMessage(), sourceInfo);
    FshLogger.logger.debug(stack(e));
  }

  /**
   * Template-literal interpolation where Java null stands for TS undefined (absent key, unset optional field):
   * "undefined", not Js.toStr's "null". BigInteger (TS bigint) prints exactly.
   */
  static String str(Object v) {
    if (v == null) return "undefined";
    if (v instanceof java.math.BigInteger b) return b.toString();
    return org.hl7.fhir.sushi.js.Js.toStr(v);
  }

  /** JS Error.stack: "Name: message\n    at ..."; the Java trace plays that role. */
  static String stack(Throwable e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
