package org.hl7.fhir.sushi.importer;

import java.util.LinkedHashMap;
import org.hl7.fhir.sushi.fshtypes.*;

// port of sushi/src/import/FSHDocument.ts (SUSHI 3.21.1)
/** The definitions parsed from one FSH file, by name. Maps are insertion-ordered (= source order), which is load-bearing. */
public class FSHDocument {
  public final String file;
  public final LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
  public final LinkedHashMap<String, Profile> profiles = new LinkedHashMap<>();
  public final LinkedHashMap<String, Extension> extensions = new LinkedHashMap<>();
  public final LinkedHashMap<String, Resource> resources = new LinkedHashMap<>();
  public final LinkedHashMap<String, Logical> logicals = new LinkedHashMap<>();
  public final LinkedHashMap<String, Instance> instances = new LinkedHashMap<>();
  public final LinkedHashMap<String, FshValueSet> valueSets = new LinkedHashMap<>();
  public final LinkedHashMap<String, FshCodeSystem> codeSystems = new LinkedHashMap<>();
  public final LinkedHashMap<String, Invariant> invariants = new LinkedHashMap<>();
  /** RuleSets without parameters. */
  public final LinkedHashMap<String, RuleSet> ruleSets = new LinkedHashMap<>();
  /** Parameterized RuleSets with substitutions applied, keyed by JSON.stringify([name, ...params]). */
  public final LinkedHashMap<String, RuleSet> appliedRuleSets = new LinkedHashMap<>();
  public final LinkedHashMap<String, Mapping> mappings = new LinkedHashMap<>();

  public FSHDocument(String file) {
    this.file = file;
  }
}
