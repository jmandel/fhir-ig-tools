package org.hl7.fhir.sushi.export;

import org.hl7.fhir.sushi.fshtypes.Mapping;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.utils.MasterFisher;

// port of sushi/src/export/MappingExporter.ts (SUSHI 3.21.1) -- owned by WP-7
public class MappingExporter {
  private final FSHTank tank;
  private final Package pkg;
  private final MasterFisher fisher;

  public MappingExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.tank = tank;
    this.pkg = pkg;
    this.fisher = fisher;
  }

  public void applyInsertRules() {
    throw new UnsupportedOperationException("TODO WP-7");
  }

  /** Finds the source StructureDefinition in the package and applies the mapping's rules to it. */
  public void exportMapping(Mapping fshDefinition) {
    throw new UnsupportedOperationException("TODO WP-7");
  }

  public void export() {
    throw new UnsupportedOperationException("TODO WP-7");
  }
}
