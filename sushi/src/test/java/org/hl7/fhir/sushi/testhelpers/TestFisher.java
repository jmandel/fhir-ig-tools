package org.hl7.fhir.sushi.testhelpers;

import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.MasterFisher;

// port of sushi/test/testhelpers/TestFisher.ts (SUSHI 3.21.1)
public class TestFisher extends MasterFisher {
  public TestFisher() {
    super();
  }

  public TestFisher(FSHTank tank, FHIRDefinitions fhir, Package pkg) {
    super(tank, fhir, pkg);
  }

  /**
   * TEMPORARY (until WP-2's FSHTank implementation is on main): skips the calling test while FSHTank is still the
   * day-1 stub, so WP-6's exporter tests can merge before it. Remove with its call sites once FSHTank lands.
   */
  public static void assumeTankImplemented() {
    try {
      new FSHTank(new java.util.ArrayList<>(), new org.hl7.fhir.sushi.fshtypes.Configuration()).resolveAlias("x");
    } catch (UnsupportedOperationException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(false, "FSHTank not implemented on this branch yet (WP-2)");
    }
  }

  public TestFisher withTank(FSHTank tank) {
    this.tank = tank;
    return this;
  }

  public TestFisher withFHIR(FHIRDefinitions fhir) {
    this.fhir = fhir;
    return this;
  }

  public TestFisher withPackage(Package pkg) {
    this.pkg = pkg;
    return this;
  }

  /** Fishes Resource, Type, Profile, Extension, Logical (or the given types) and returns StructureDefinition.fromJSON. */
  public StructureDefinition fishForStructureDefinition(String item, FishableType... types) {
    if (types == null || types.length == 0) {
      types = new FishableType[] {FishableType.Resource, FishableType.Type, FishableType.Profile,
        FishableType.Extension, FishableType.Logical};
    }
    Object json = fishForFHIR(item, types);
    return json != null ? StructureDefinition.fromJSON(json) : null;
  }
}
