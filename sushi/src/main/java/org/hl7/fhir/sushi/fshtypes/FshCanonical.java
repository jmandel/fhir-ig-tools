package org.hl7.fhir.sushi.fshtypes;

// port of sushi/src/fshtypes/FshCanonical.ts (SUSHI 3.21.1)
public class FshCanonical extends FshEntity {
  public String entityName;
  public String version;

  public FshCanonical(String entityName) {
    this.entityName = entityName;
  }

  @Override public String toString() {
    return "Canonical(" + entityName + (Common.truthy(version) ? "|" + version : "") + ")";
  }
}
