package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/FixedToPatternError.ts (SUSHI 3.21.1)
public class FixedToPatternError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.fixed_x_",
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.pattern_x_");
  public final String fixedProperty;

  public FixedToPatternError(String fixedProperty) {
    super("Cannot assign this element using a pattern; as it is already assigned in the StructureDefinition using " + s(fixedProperty) + ". Since fixed[x] requires exact matches, while pattern[x] allows for variation in unspecified properties, fixed[x] cannot be replaced by pattern[x] since it would loosen the constraint.");
    this.fixedProperty = fixedProperty;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
