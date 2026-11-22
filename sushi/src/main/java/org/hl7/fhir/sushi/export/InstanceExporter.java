package org.hl7.fhir.sushi.export;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.MasterFisher;
import org.hl7.fhir.sushi.utils.Metadata;

// port of sushi/src/export/InstanceExporter.ts (SUSHI 3.21.1) -- owned by WP-7
/**
 * Exports FSH Instances. Also a Fishable for instances (TS {@code fishForFHIR(item)} takes no types): fishing exports
 * the FSH instance on demand, so other exporters (caret rules assigning instances) create one per use.
 */
public class InstanceExporter implements Fishable {
  /** TS sdCache: instanceOf url -> StructureDefinition (parsed once per exporter). */
  public final Map<String, StructureDefinition> sdCache = new LinkedHashMap<>();

  private final FSHTank tank;
  private final Package pkg;
  private final MasterFisher fisher;

  public InstanceExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.tank = tank;
    this.pkg = pkg;
    this.fisher = fisher;
  }

  /** TS fishForFHIR(item): the types argument is ignored (always Instance, then any FHIR resource). */
  @Override public InstanceDefinition fishForFHIR(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-7");
  }

  @Override public Metadata fishForMetadata(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-7");
  }

  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-7");
  }

  public void applyInsertRules() {
    throw new UnsupportedOperationException("TODO WP-7");
  }

  /** Returns null (TS undefined) when an instance with the same name was already exported. */
  public InstanceDefinition exportInstance(Instance fshDefinition) {
    throw new UnsupportedOperationException("TODO WP-7");
  }

  public Package export() {
    throw new UnsupportedOperationException("TODO WP-7");
  }
}
