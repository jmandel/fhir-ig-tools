package org.hl7.fhir.sushi.fhirtypes;

import java.util.ArrayList;
import java.util.List;

// port of sushi/src/fhirtypes/StructureDefinition.ts -- CONTRACT SKELETON, owned by WP-3 (fhirtypes core).
/**
 * SUSHI's own StructureDefinition model (not org.hl7.fhir.r5.model: see PORTING.md D2). Top-level properties are
 * serialized in {@link #PROPS} order (with each "_prop" right after "prop"), then snapshot/differential.
 */
public class StructureDefinition implements FhirResource {
  /** TS PROPS, verbatim: the serialization order of the top-level properties. */
  public static final List<String> PROPS = List.of("id", "meta", "implicitRules", "language", "text", "contained",
      "extension", "modifierExtension", "url", "identifier", "version", "name", "title", "status", "experimental",
      "date", "publisher", "contact", "description", "useContext", "jurisdiction", "purpose", "copyright", "keyword",
      "fhirVersion", "mapping", "kind", "abstract", "context", "contextInvariant", "type", "baseDefinition",
      "derivation");

  public final List<ElementDefinition> elements = new ArrayList<>();

  public static StructureDefinition fromJSON(Object json) {
    return fromJSON(json, true);
  }

  public static StructureDefinition fromJSON(Object json, boolean captureOriginalElements) {
    throw new UnsupportedOperationException("TODO WP-3");
  }

  @Override public Object toJSON() {
    return toJSON(true);
  }

  @Override public Object toJSON(boolean snapshot) {
    throw new UnsupportedOperationException("TODO WP-3");
  }

  @Override public String getFileName() {
    throw new UnsupportedOperationException("TODO WP-3");
  }
}
