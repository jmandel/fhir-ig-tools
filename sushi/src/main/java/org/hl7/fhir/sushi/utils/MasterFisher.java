package org.hl7.fhir.sushi.utils;

import java.util.List;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.importer.FSHTank;

// port of sushi/src/utils/MasterFisher.ts -- WP-6 signatures (day 1); implementation on branch wp6.
/** Fishes package (exported so far) -> tank (metadata only) -> external FHIR definitions, resolving aliases first. */
public class MasterFisher implements Fishable {
  public FSHTank tank;
  public FHIRDefinitions fhir;
  public Package pkg;
  public String defaultFHIRVersion;

  /** TS new MasterFisher() (all optional). */
  public MasterFisher() {
    this(null, null, null);
  }

  /** defaultFHIRVersion = fhir.fishForFHIR("StructureDefinition").fhirVersion, else tank.config.fhirVersion[0]. */
  public MasterFisher(FSHTank tank, FHIRDefinitions fhir, Package pkg) {
    this.tank = tank;
    this.fhir = fhir;
    this.pkg = pkg;
  }

  @Override public Object fishForFHIR(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
  @Override public Metadata fishForMetadata(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
}
