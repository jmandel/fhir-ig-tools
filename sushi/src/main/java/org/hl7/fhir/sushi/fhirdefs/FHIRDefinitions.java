package org.hl7.fhir.sushi.fhirdefs;

import java.util.List;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.Metadata;

// port of sushi/src/fhirdefs/FHIRDefinitions.ts + the parts of fhir-package-loader 2.2.4 SUSHI uses
// -- CONTRACT SKELETON, owned by WP-6 (packages).
/**
 * External FHIR definitions (core + dependency packages + predefined resources from input/). Lookup semantics are
 * FPL's: a SQL query over loaded resources ordered by load order (later-loaded packages win? -- WP-6 must pin this
 * down from FPL's SQLJSPackageDB and byLoadOrder, not guess), then predefined resources, then the R5-for-R4 extras.
 * Package files/cache/registry I/O may reuse org.hl7.fhir.utilities.npm (same ~/.fhir/packages layout); the lookup
 * order and every log message must be SUSHI/FPL's.
 */
public class FHIRDefinitions implements Fishable {
  /** TS loadPackage(name, version) -> LoadStatus; async in TS, synchronous here. */
  public String loadPackage(String name, String version) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  public List<Object> allPredefinedResources() { throw new UnsupportedOperationException("TODO WP-6"); }
  public Object fishForPredefinedResource(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
  public Metadata fishForPredefinedResourceMetadata(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }

  @Override public Object fishForFHIR(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
  @Override public Metadata fishForMetadata(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
}
