package org.hl7.fhir.sushi.fshtypes;

// port of sushi/src/fshtypes/Resource.ts (SUSHI 3.21.1)
/** rules: LrRule only. */
public class Resource extends FshStructure {
  public Resource(String name) {
    super(name);
    // Init the parent to 'DomainResource', as this is what 99% of resources do.
    this.parent = "DomainResource";
  }

  @Override public String constructorName() {
    return "Resource";
  }
}
