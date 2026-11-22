package org.hl7.fhir.jekyll.liquid;

/**
 * A lazily computed value stored in a scope/environment hash (Ruby {@code Proc} values, which
 * {@code Context#lookup_and_evaluate} calls once and memoizes back into the hash).
 */
@FunctionalInterface
public interface LiquidProc {
  Object call(Context context);
}
