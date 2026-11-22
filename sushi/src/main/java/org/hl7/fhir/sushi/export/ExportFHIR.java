package org.hl7.fhir.sushi.export;

import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.utils.MasterFisher;

// port of sushi/src/export/exportFHIR.ts (SUSHI 3.21.1)
public final class ExportFHIR {
  private ExportFHIR() {}

  /**
   * Processes a set of FSH definitions into FHIR artifacts.
   *
   * @param tank the tank containing the FSH definitions to process
   * @param fhirDefs any externally imported FHIRDefinitions
   * @return the Package structure returned from processing the FSH definitions
   */
  public static Package exportFHIR(FSHTank tank, FHIRDefinitions fhirDefs) {
    Package pkg = new Package(tank.config);
    MasterFisher fisher = new MasterFisher(tank, fhirDefs, pkg);
    FHIRExporter exporter = new FHIRExporter(tank, pkg, fisher);
    return exporter.export();
  }
}
