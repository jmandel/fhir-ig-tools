package org.hl7.fhir.sushi.export;

import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.FshStructure;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.MasterFisher;
import org.hl7.fhir.sushi.utils.Metadata;

// port of sushi/src/export/StructureDefinitionExporter.ts (SUSHI 3.21.1) -- WP-6 signatures (day 1).
/** Exports Profiles, Extensions, Logicals and Resources. Fishing through it exports tank definitions on demand. */
public class StructureDefinitionExporter implements Fishable {
  public StructureDefinitionExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  public void applyInsertRules() { throw new UnsupportedOperationException("TODO WP-6"); }

  /** Returns null when a definition with the same name was already exported. */
  public StructureDefinition exportStructDef(FshStructure fshDefinition) { throw new UnsupportedOperationException("TODO WP-6"); }

  public Package export() { throw new UnsupportedOperationException("TODO WP-6"); }

  /** Called by FHIRExporter after instances are exported. */
  public void applyDeferredRules() { throw new UnsupportedOperationException("TODO WP-6"); }

  @Override public Object fishForFHIR(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
  @Override public Metadata fishForMetadata(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) { throw new UnsupportedOperationException("TODO WP-6"); }
}
