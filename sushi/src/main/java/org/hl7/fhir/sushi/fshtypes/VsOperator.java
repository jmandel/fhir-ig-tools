package org.hl7.fhir.sushi.fshtypes;

// port of sushi/src/fshtypes/ValueSetComponentTypes.ts enum VsOperator (SUSHI 3.21.1)
/** {@link #toString()} / {@link #value} is the FHIR filter operator code (the TS enum value). */
public enum VsOperator {
  EQUALS("="), IS_A("is-a"), DESCENDENT_OF("descendent-of"), IS_NOT_A("is-not-a"), REGEX("regex"), IN("in"),
  NOT_IN("not-in"), GENERALIZES("generalizes"), EXISTS("exists");

  public final String value;

  VsOperator(String value) {
    this.value = value;
  }

  /** The operator for a FHIR code, or null (TS: Object.values(VsOperator).includes(x)). */
  public static VsOperator fromValue(String v) {
    for (VsOperator o : values()) {
      if (o.value.equals(v)) return o;
    }
    return null;
  }

  @Override public String toString() {
    return value;
  }
}
