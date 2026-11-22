package org.hl7.fhir.sushi.fshtypes;

import java.util.List;

// port of sushi/src/fshtypes/ValueSetComponentTypes.ts type ValueSetComponentFrom (SUSHI 3.21.1)
/** Both optional; null = undefined (distinct from an empty list, which TS code checks with `!= null`). */
public class ValueSetComponentFrom {
  public String system;
  public List<String> valueSets;
}
