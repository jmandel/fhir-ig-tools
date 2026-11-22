package org.hl7.fhir.sushi.errors;

import org.hl7.fhir.sushi.js.Js;

/** Helpers shared by the error classes (no TS counterpart). */
final class Errors {
  private Errors() {}

  /**
   * A value as a JS template literal interpolates it. Java {@code null} stands for JS {@code undefined} (PORTING.md
   * conventions), so it prints {@code "undefined"}; numbers use JS Number::toString.
   */
  static String str(Object v) {
    return v == null ? "undefined" : Js.toStr(v);
  }

  /** Array.prototype.join: null/undefined elements render as the empty string. */
  static String join(String sep, java.util.List<?> items) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) sb.append(sep);
      Object o = items.get(i);
      if (o != null && o != Js.UNDEFINED) sb.append(Js.toStr(o));
    }
    return sb.toString();
  }
}
