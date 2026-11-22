package org.hl7.fhir.sushi.fshtypes;

import java.util.regex.Pattern;

// port of sushi/src/fshtypes/ValueSetComponentTypes.ts types ValueSetFilter / ValueSetFilterValue (SUSHI 3.21.1)
public class ValueSetFilter {
  public String property;
  public VsOperator operator;
  /**
   * TS ValueSetFilterValue: String | RegExp | Boolean | FshCode. A JS RegExp is represented as
   * {@link java.util.regex.Pattern} holding the JS source text (FSH regex filters carry no flags); TS prints it
   * with RegExp.toString(), i.e. {@code /source/}.
   */
  public Object value;

  public ValueSetFilter(String property, VsOperator operator, Object value) {
    this.property = property;
    this.operator = operator;
    this.value = value;
  }

  /** The value as it appears in FSH ({@code "string"}, {@code /regex/}, {@code true}, {@code #code}). */
  public String valueToFSH() {
    if (value instanceof String s) return "\"" + s + "\"";
    if (value instanceof Pattern p) return "/" + p.pattern() + "/";
    return String.valueOf(value);
  }
}
