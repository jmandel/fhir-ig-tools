package org.hl7.fhir.sushi.importer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.FshCode;

// port of sushi/src/import/parseCodeLexeme.ts (SUSHI 3.21.1)
public final class ParseCodeLexeme {
  private ParseCodeLexeme() {}

  /** JS /(^|[^\\])(\\\\)*#/: the first # not escaped by an odd number of backslashes. */
  private static final Pattern SPLIT_POINT = Pattern.compile("(^|[^\\\\])(\\\\\\\\)*#");

  /**
   * Parses the FSH code string and creates a FshCode, accommodating escaped # and quoted codes. Does not resolve
   * aliases for the code system and handles only the system and code (not display).
   *
   * @param conceptText a concept in FSH syntax but with display excluded
   */
  public static FshCode parseCodeLexeme(String conceptText) {
    Matcher splitPoint = SPLIT_POINT.matcher(conceptText);
    String system;
    String code;
    if (!splitPoint.find()) {
      system = "";
      code = conceptText.startsWith("#") ? conceptText.substring(1) : "";
    } else {
      String m = splitPoint.group();
      system = conceptText.substring(0, splitPoint.start()) + m.substring(0, m.length() - 1);
      code = conceptText.substring(splitPoint.start() + m.length());
    }
    system = system.replace("\\\\", "\\").replace("\\#", "#");
    if (code.startsWith("\"") && code.endsWith("\"")) {
      // JS code.slice(1, code.length - 1): "" for a lone quote
      code = (code.length() >= 2 ? code.substring(1, code.length() - 1) : "").replace("\\\\", "\\")
          .replace("\\\"", "\"");
    }
    FshCode concept = new FshCode(code);
    if (!system.isEmpty()) {
      concept.system = system;
    }
    return concept;
  }
}
