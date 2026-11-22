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
