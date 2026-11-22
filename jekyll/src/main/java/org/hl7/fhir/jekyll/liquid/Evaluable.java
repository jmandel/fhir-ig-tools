package org.hl7.fhir.jekyll.liquid;

/** Ruby duck type {@code respond_to?(:evaluate)}: VariableLookup, RangeLookup. */
public interface Evaluable {
  Object evaluate(Context context);
}
