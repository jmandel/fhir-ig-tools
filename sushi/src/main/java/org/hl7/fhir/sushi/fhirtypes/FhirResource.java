package org.hl7.fhir.sushi.fhirtypes;

import org.hl7.fhir.sushi.js.JsonSerializable;

/**
 * CONTRACT: what SUSHI writes to fsh-generated/resources. Implemented by StructureDefinition, ValueSet, CodeSystem,
 * InstanceDefinition and ImplementationGuide. {@code toJSON()} must return the JS value model with keys in exactly
 * the order TS SUSHI produces (see PORTING.md "Key order"), so {@code Json.outputJson(r.toJSON())} is byte-identical.
 */
public interface FhirResource extends JsonSerializable {
  /** TS getFileName(): e.g. "StructureDefinition-my-profile.json" (sanitize-filename semantics). */
  String getFileName();

  /** For StructureDefinition: toJSON(snapshot). Others ignore the flag. */
  default Object toJSON(boolean snapshot) {
    return toJSON();
  }
}
