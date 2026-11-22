package org.hl7.fhir.sushi.export;

import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.importer.FSHTank;

// port of sushi/src/export/exportFHIR.ts (SUSHI 3.21.1) -- WP-6 signatures (day 1).
public final class ExportFHIR {
  private ExportFHIR() {}

  /** new Package(tank.config), new MasterFisher(tank, defs, pkg), new FHIRExporter(...).export(). */
  public static Package exportFHIR(FSHTank tank, FHIRDefinitions fhirDefs) {
    throw new UnsupportedOperationException("TODO WP-6");
  }
}
