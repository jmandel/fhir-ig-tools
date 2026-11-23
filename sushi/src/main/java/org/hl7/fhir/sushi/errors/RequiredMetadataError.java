package org.hl7.fhir.sushi.errors;

// port of sushi/src/errors/RequiredMetadataError.ts (SUSHI 3.21.1)
public class RequiredMetadataError extends RuntimeException {
  public final String field;
  public final String entityType;
  public final String entityName;

  public RequiredMetadataError(String field, String entityType, String entityName) {
    super("Metadata field " + s(field) + " required when declaring " + s(entityType) + " " + s(entityName) + ".");
    this.field = field;
    this.entityType = entityType;
    this.entityName = entityName;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}
