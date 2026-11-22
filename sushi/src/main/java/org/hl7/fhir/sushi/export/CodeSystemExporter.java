package org.hl7.fhir.sushi.export;

import org.hl7.fhir.sushi.fhirtypes.CodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.utils.MasterFisher;

// port of sushi/src/export/CodeSystemExporter.ts (SUSHI 3.21.1) -- owned by WP-7
public class CodeSystemExporter {
  private final FSHTank tank;
  private final Package pkg;
  private final MasterFisher fisher;

  public CodeSystemExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.tank = tank;
    this.pkg = pkg;
    this.fisher = fisher;
  }

  public void applyInsertRules() {
    throw new UnsupportedOperationException("TODO WP-7");
  }

  /** Returns null (TS undefined) when a code system with the same name was already exported. */
  public CodeSystem exportCodeSystem(FshCodeSystem fshDefinition) {
    throw new UnsupportedOperationException("TODO WP-7");
  }

  public Package export() {
    throw new UnsupportedOperationException("TODO WP-7");
  }
}
