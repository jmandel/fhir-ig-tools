package org.hl7.fhir.sushi.fhirtypes;

import java.util.List;

/**
 * A Java class whose JS own properties SUSHI's generic code reads and writes by name (TS {@code obj[key]},
 * {@code delete obj[key]}, {@code Object.keys(obj)}, {@code for (const p in obj)}): the path-setting functions in
 * {@link Common} (setPropertyOnInstance, createUsefulSlices, replaceField/cleanResource, ...) work on
 * {@code Map}s (JsObject, InstanceDefinition, CodeSystem, ValueSet), {@code List}s, and PropertyHolders
 * (StructureDefinition, ElementDefinition, ElementDefinitionType). Use {@link Common#getProp} and friends rather than
 * dispatching by hand.
 *
 * <p>Semantics: {@code getProperty} returns null for an absent key (JS undefined and null are both "== null" for
 * every caller of this interface); {@code hasProperty} is {@code key in obj} for own data properties with a value
 * other than undefined; {@code propertyKeys} is {@code Object.keys} order (TS: the order in which the properties were
 * first assigned, since SUSHI compiles without useDefineForClassFields).
 */
public interface PropertyHolder {
  Object getProperty(String key);

  boolean hasProperty(String key);

  void setProperty(String key, Object value);

  void deleteProperty(String key);

  List<String> propertyKeys();
}
