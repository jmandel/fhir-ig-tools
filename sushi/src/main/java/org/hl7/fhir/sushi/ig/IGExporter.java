package org.hl7.fhir.sushi.ig;

import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;

// port of sushi/src/ig/IGExporter.ts + predefinedResources.ts -- CONTRACT SKELETON, owned by WP-8 (IG + CLI).
/** Writes fsh-generated/resources/ImplementationGuide-*.json, includes/menu.xml, fsh-link-references.md, etc. */
public class IGExporter {
  public IGExporter(Package pkg, FHIRDefinitions fhirDefs, String igDataPath) {}

  public void export(String outPath) {
    throw new UnsupportedOperationException("TODO WP-8");
  }
}
