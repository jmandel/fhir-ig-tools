package org.hl7.fhir.sushi.importer;

/**
 * A JS TypeError as V8 words it. Thrown where SUSHI's TS would dereference a null/undefined CST node (left
 * incomplete by syntax-error recovery), so the "Error in parsing: ..." message the importer logs is the same.
 */
final class JsTypeError extends RuntimeException {
  JsTypeError(String message) {
    super(message);
  }

  /** {@code null.prop}: "Cannot read properties of null (reading 'prop')". */
  static JsTypeError readNull(String prop) {
    return new JsTypeError("Cannot read properties of null (reading '" + prop + "')");
  }

  /** {@code undefined.prop}: "Cannot read properties of undefined (reading 'prop')". */
  static JsTypeError readUndefined(String prop) {
    return new JsTypeError("Cannot read properties of undefined (reading '" + prop + "')");
  }
}
