package org.hl7.fhir.sushi.fhirtypes;

import org.hl7.fhir.sushi.js.JsObject;

// port of sushi/src/fhirtypes/CodeSystem.ts -- CONTRACT SKELETON.
/**
 * Backed by an insertion-ordered JS object, because TS compiles to ES2018 (no useDefineForClassFields): the JSON key
 * order is the initialised fields first ("resourceType", then defaults such as status/content), then every other
 * property in the order the exporter ASSIGNS it -- not FHIR order (verified on real fsh-generated output, PORTING.md
 * "Key order"). Typed accessors may be added; storage must stay ordered.
 */
public class CodeSystem extends JsObject implements FhirResource {
  private static final long serialVersionUID = 1L;

  @Override public Object toJSON() {
    throw new UnsupportedOperationException("TODO: orderedCloneDeep(this)");
  }

  @Override public String getFileName() {
    throw new UnsupportedOperationException("TODO");
  }
}
