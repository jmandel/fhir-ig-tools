package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/NonAbstractParentOfSpecializationError.ts (SUSHI 3.21.1)
public class NonAbstractParentOfSpecializationError extends RuntimeException {
  public final String invalidType;
  public final String nonAbstractParent;

  public NonAbstractParentOfSpecializationError(String invalidType, String nonAbstractParent) {
    super("The type " + s(nonAbstractParent) + " is not abstract, so it cannot be constrained to the specialization " + s(invalidType) + ".");
    this.invalidType = invalidType;
    this.nonAbstractParent = nonAbstractParent;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
