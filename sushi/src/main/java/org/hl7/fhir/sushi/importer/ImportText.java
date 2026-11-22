package org.hl7.fhir.sushi.importer;

import java.util.List;

// port of sushi/src/import/importText.ts (SUSHI 3.21.1)
public final class ImportText {
  private ImportText() {}

  /**
   * Parses various text strings into individual FSHDocuments.
   *
   * @param rawFSHes the list of RawFSH to parse into FSHDocuments
   * @return the FSH documents representing each parsed text
   */
  public static List<FSHDocument> importText(List<RawFSH> rawFSHes) {
    FSHImporter importer = new FSHImporter();
    return importer.importFsh(rawFSHes);
  }
}
