package org.hl7.fhir.sushi.errors;

import org.hl7.fhir.sushi.fshtypes.SourceInfo;

// port of sushi/src/errors/WithSource.ts (SUSHI 3.21.1)
/** An error that carries the FSH source location it should be logged with (TS field {@code sourceInfo}). */
public interface WithSource {
  SourceInfo getSourceInfo();
}
