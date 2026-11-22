package org.hl7.fhir.sushi.export;

import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.utils.MasterFisher;

// port of sushi/src/export/FHIRExporter.ts + exportFHIR.ts -- CONTRACT SKELETON, owned by WP-4.
/**
 * Orchestrates the exporters in TS order: applyInsertRules (SD, CS, VS, Instance), then SD export, CS, VS,
 * Instances, Mappings, then post-processing. The exporter classes below are the WP seams:
 * StructureDefinitionExporter (WP-4), InstanceExporter (WP-5), ValueSetExporter / CodeSystemExporter /
 * MappingExporter (WP-7). Each takes (FSHTank, Package, MasterFisher) and adds to the Package.
 */
public class FHIRExporter {
  public interface Exporter {
    void applyInsertRules();

    void export();
  }

  private final FSHTank tank;
  private final Package pkg;
  private final MasterFisher fisher;

  public FHIRExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.tank = tank;
    this.pkg = pkg;
    this.fisher = fisher;
  }

  public Package export() {
    throw new UnsupportedOperationException("TODO WP-4");
  }
}
