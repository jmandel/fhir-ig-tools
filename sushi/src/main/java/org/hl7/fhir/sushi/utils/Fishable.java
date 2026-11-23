package org.hl7.fhir.sushi.utils;

import java.util.List;

/**
 * port of the `Fishable` interface in sushi/src/utils/Fishable.ts. Implemented by FSHTank (FSH-side lookups),
 * FHIRDefinitions (packages), Package (exported FHIR in progress) and MasterFisher (all three, in SUSHI's order).
 *
 * <p>{@code fishForFHIR} returns the FHIR JSON in the JS value model (see org.hl7.fhir.common.js.Json): a
 * LinkedHashMap&lt;String,Object&gt;, or null when nothing matches. An empty {@code types} array means "any type", in
 * the order of {@link FishableType}'s declaration unless the implementation documents otherwise (mirror the TS
 * implementation exactly: the order in which types are tried decides which of two same-named artifacts wins).
 */
public interface Fishable {
  Object fishForFHIR(String item, FishableType... types);

  Metadata fishForMetadata(String item, FishableType... types);

  List<Metadata> fishForMetadatas(String item, FishableType... types);
}
