package org.hl7.fhir.sushi.utils;

import java.util.List;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.importer.FSHTank;

// port of sushi/src/utils/MasterFisher.ts -- CONTRACT SKELETON, owned by WP-4 (export core).
/** Fishes package (exported so far) -> tank (metadata only) -> external FHIR definitions, resolving aliases first. */
public class MasterFisher implements Fishable {
  public final FSHTank tank;
  public final FHIRDefinitions fhir;
  public final Package pkg;
  public String defaultFHIRVersion;

  public MasterFisher(FSHTank tank, FHIRDefinitions fhir, Package pkg) {
    this.tank = tank;
    this.fhir = fhir;
    this.pkg = pkg;
  }

  @Override public Object fishForFHIR(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-4"); }
  @Override public Metadata fishForMetadata(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-4"); }
  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-4"); }
}
