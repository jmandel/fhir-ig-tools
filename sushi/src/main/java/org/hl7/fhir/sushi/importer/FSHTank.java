package org.hl7.fhir.sushi.importer;

import java.util.List;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.Metadata;

// port of sushi/src/import/FSHTank.ts -- CONTRACT SKELETON, owned by WP-2 (importer).
/** All parsed FSH documents. getAll* return entities in document order, then declaration order within a document. */
public class FSHTank implements Fishable {
  public final List<FSHDocument> docs;
  public final Configuration config;

  public FSHTank(List<FSHDocument> docs, Configuration config) {
    this.docs = docs;
    this.config = config;
  }

  public List<Profile> getAllProfiles() { throw todo(); }
  public List<Extension> getAllExtensions() { throw todo(); }
  public List<Logical> getAllLogicals() { throw todo(); }
  public List<Resource> getAllResources() { throw todo(); }
  public List<Instance> getAllInstances() { throw todo(); }
  /** Profiles, Extensions, Logicals, Resources (TS order). */
  public List<FshStructure> getAllStructureDefinitions() { throw todo(); }
  public List<FshValueSet> getAllValueSets() { throw todo(); }
  public List<FshCodeSystem> getAllCodeSystems() { throw todo(); }
  public List<Invariant> getAllInvariants() { throw todo(); }
  public List<RuleSet> getAllRuleSets() { throw todo(); }
  public List<Mapping> getAllMappings() { throw todo(); }
  public String resolveAlias(String name) { throw todo(); }
  public void checkDuplicateNameEntities() { throw todo(); }
  public RuleSet fishForAppliedRuleSet(String item) { throw todo(); }

  /** Returns the FSH entity (not FHIR JSON), as TS does. */
  @Override public Object fishForFHIR(String item, FishableType... types) { throw todo(); }
  @Override public Metadata fishForMetadata(String item, FishableType... types) { throw todo(); }
  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) { throw todo(); }

  private static UnsupportedOperationException todo() {
    return new UnsupportedOperationException("TODO WP-2");
  }
}
