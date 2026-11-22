package org.hl7.fhir.sushi.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fhirtypes.*;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.Metadata;

// port of sushi/src/export/Package.ts -- CONTRACT SKELETON, owned by WP-4 (export core).
/** The FHIR artifacts exported so far, in export order (list order = write order = fsh-index order). */
public class Package implements Fishable {
  public final List<StructureDefinition> profiles = new ArrayList<>();
  public final List<StructureDefinition> extensions = new ArrayList<>();
  public final List<StructureDefinition> logicals = new ArrayList<>();
  public final List<StructureDefinition> resources = new ArrayList<>();
  public final List<InstanceDefinition> instances = new ArrayList<>();
  public final List<ValueSet> valueSets = new ArrayList<>();
  public final List<CodeSystem> codeSystems = new ArrayList<>();

  /** TS fshMap: file name -> SourceInfo & {fshName, fshType}. */
  public record FshMapEntry(SourceInfo sourceInfo, String fshName, String fshType) {}

  public final Map<String, FshMapEntry> fshMap = new LinkedHashMap<>();
  public final Configuration config;

  public Package(Configuration config) {
    this.config = config;
  }

  @Override public Object fishForFHIR(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-4");
  }

  @Override public Metadata fishForMetadata(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-4");
  }

  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-4");
  }
}
