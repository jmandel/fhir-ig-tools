package org.hl7.fhir.sushi.importer;

import org.hl7.fhir.sushi.fshtypes.FshCode;

// port of sushi/src/import/parseCodeLexeme.ts (SUSHI 3.21.1)
public final class ParseCodeLexeme {
  private ParseCodeLexeme() {}

  /**
   * Parses the FSH code string and creates a FshCode, accommodating escaped # and quoted codes. Does not resolve
   * aliases for the code system and handles only the system and code (not display).
   *
   * @param conceptText a concept in FSH syntax but with display excluded
   */
  public static FshCode parseCodeLexeme(String conceptText) {
    throw new UnsupportedOperationException("TODO WP-2");
  }
}
