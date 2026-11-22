package org.hl7.fhir.sushi.fshtypes;

// port of sushi/src/fshtypes/Profile.ts (SUSHI 3.21.1)
/** rules: SdRule only (enforced by the importer and AllowedRules, not the type). */
public class Profile extends FshStructure {
  public Profile(String name) {
    super(name);
  }

  @Override public String constructorName() {
    return "Profile";
  }
}
