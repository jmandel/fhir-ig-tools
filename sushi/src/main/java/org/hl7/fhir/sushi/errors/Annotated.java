package org.hl7.fhir.sushi.errors;

import java.util.List;

// port of sushi/src/errors/Annotated.ts (SUSHI 3.21.1)
/** An error that points at the parts of the FHIR spec explaining it (TS field {@code specReferences}). */
public interface Annotated {
  List<String> getSpecReferences();
}
