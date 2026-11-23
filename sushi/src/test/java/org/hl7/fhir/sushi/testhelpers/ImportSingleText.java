package org.hl7.fhir.sushi.testhelpers;

import java.util.List;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.ImportText;
import org.hl7.fhir.sushi.importer.RawFSH;

// port of sushi/test/testhelpers/importSingleText.ts (SUSHI 3.21.1)
public final class ImportSingleText {
  private ImportSingleText() {}

  public static FSHDocument importSingleText(String content) {
    return importSingleText(content, null);
  }

  public static FSHDocument importSingleText(String content, String path) {
    return ImportText.importText(List.of(new RawFSH(content, path))).get(0);
  }
}
