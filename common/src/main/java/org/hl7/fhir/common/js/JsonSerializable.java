package org.hl7.fhir.common.js;

/** A Java object with a JS {@code toJSON()} method: {@link Json#stringify} calls it, as JSON.stringify does. */
public interface JsonSerializable {
  /** Returns a value in the JS value model (Map, List, String, Number, Boolean, null, or another JsonSerializable). */
  Object toJSON();
}
