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

// port of sushi/src/export/Package.ts (SUSHI 3.21.1) -- WP-6 signatures (day 1); implementation on branch wp6.
/** The FHIR artifacts exported so far, in export order (list order = write order = fsh-index order). */
public class Package implements Fishable {
  public final List<StructureDefinition> profiles = new ArrayList<>();
  public final List<StructureDefinition> extensions = new ArrayList<>();
  public final List<StructureDefinition> logicals = new ArrayList<>();
  public final List<StructureDefinition> resources = new ArrayList<>();
  public final List<InstanceDefinition> instances = new ArrayList<>();
  public final List<ValueSet> valueSets = new ArrayList<>();
  public final List<CodeSystem> codeSystems = new ArrayList<>();

  /** TS fshMap value: SourceInfo &amp; {fshName, fshType} (the sourceInfo is a copy, as TS spreads it). */
  public record FshMapEntry(SourceInfo sourceInfo, String fshName, String fshType) {}

  /** TS fshMap: file name -&gt; entry, insertion ordered. */
  public final Map<String, FshMapEntry> fshMap = new LinkedHashMap<>();
  public final Configuration config;

  public Package(Configuration config) {
    this.config = config;
  }

  /**
   * First match (StructureDefinition, ValueSet, CodeSystem or InstanceDefinition), trying types in order; no types =
   * Profile, Extension, Logical, Resource, ValueSet, CodeSystem, Instance. "url|version" matches the version.
   */
  public FhirResource fish(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  /** All matches, one per type at most, in type order. */
  public List<FhirResource> fishAll(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  /** fish(...).toJSON(), or null. */
  @Override public Object fishForFHIR(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  @Override public Metadata fishForMetadata(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  /** Resets all the definition arrays to be zero-length. This is useful for re-using a package during testing. */
  public void clearAllDefinitions() {
    throw new UnsupportedOperationException("TODO WP-6");
  }
}
