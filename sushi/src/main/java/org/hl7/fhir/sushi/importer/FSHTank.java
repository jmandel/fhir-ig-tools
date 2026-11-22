package org.hl7.fhir.sushi.importer;

import static org.hl7.fhir.sushi.importer.FhirCommonBridge.getUrlFromFshDefinition;
import static org.hl7.fhir.sushi.importer.FhirCommonBridge.getVersionFromFshDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.Metadata;

// port of sushi/src/import/FSHTank.ts (SUSHI 3.21.1)
/** All parsed FSH documents. getAll* return entities in document order, then declaration order within a document. */
public class FSHTank implements Fishable {
  private static final String HL7_SD_BASE_URL = "http://hl7.org/fhir/StructureDefinition/";
  /** Types searched when none are given, in TS order (not FishableType's declaration order). */
  private static final FishableType[] ALL_TANK_TYPES = {FishableType.Profile, FishableType.Extension,
      FishableType.Logical, FishableType.Resource, FishableType.ValueSet, FishableType.CodeSystem,
      FishableType.Instance, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping};

  public final List<FSHDocument> docs;
  public final Configuration config;

  public FSHTank(List<FSHDocument> docs, Configuration config) {
    this.docs = docs;
    this.config = config;
  }

  private <T> List<T> all(Function<FSHDocument, Map<String, T>> f) {
    List<T> out = new ArrayList<>();
    for (FSHDocument doc : docs) out.addAll(f.apply(doc).values());
    return out;
  }

  public List<Profile> getAllProfiles() { return all(d -> d.profiles); }
  public List<Extension> getAllExtensions() { return all(d -> d.extensions); }
  public List<Logical> getAllLogicals() { return all(d -> d.logicals); }
  public List<Resource> getAllResources() { return all(d -> d.resources); }
  public List<Instance> getAllInstances() { return all(d -> d.instances); }

  /** Profiles, Extensions, Logicals, Resources (TS order). */
  public List<FshStructure> getAllStructureDefinitions() {
    List<FshStructure> out = new ArrayList<>();
    out.addAll(getAllProfiles());
    out.addAll(getAllExtensions());
    out.addAll(getAllLogicals());
    out.addAll(getAllResources());
    return out;
  }

  public List<FshValueSet> getAllValueSets() { return all(d -> d.valueSets); }
  public List<FshCodeSystem> getAllCodeSystems() { return all(d -> d.codeSystems); }
  public List<Invariant> getAllInvariants() { return all(d -> d.invariants); }
  public List<RuleSet> getAllRuleSets() { return all(d -> d.ruleSets); }
  public List<Mapping> getAllMappings() { return all(d -> d.mappings); }

  /** The alias value from the first document that defines it with a truthy value, or null. */
  public String resolveAlias(String name) {
    for (FSHDocument doc : docs) {
      String foundAlias = doc.aliases.get(name);
      if (Common.truthy(foundAlias)) return foundAlias;
    }
    return null;
  }

  public void checkDuplicateNameEntities() {
    List<FshEntity> allEntities = new ArrayList<>();
    allEntities.addAll(getAllStructureDefinitions());
    allEntities.addAll(getAllInstances());
    allEntities.addAll(getAllMappings());
    allEntities.addAll(getAllInvariants());
    allEntities.addAll(getAllValueSets());
    allEntities.addAll(getAllCodeSystems());
    allEntities.addAll(getAllRuleSets());
    allEntities.addAll(getAllExtensions());

    LinkedHashSet<String> duplicateEntities = new LinkedHashSet<>();
    for (FshEntity entity : allEntities) {
      String name = nameOf(entity);
      boolean dup = docs.stream().anyMatch(doc -> otherWithName(doc.profiles, name, entity)
          || otherWithName(doc.extensions, name, entity) || otherWithName(doc.logicals, name, entity)
          || otherWithName(doc.resources, name, entity) || otherWithName(doc.instances, name, entity)
          || otherWithName(doc.mappings, name, entity) || otherWithName(doc.invariants, name, entity)
          || otherWithName(doc.valueSets, name, entity) || otherWithName(doc.codeSystems, name, entity)
          || otherWithName(doc.ruleSets, name, entity));
      if (dup) duplicateEntities.add(Common.jsString(name));
    }
    if (!duplicateEntities.isEmpty()) {
      FshLogger.logger.warn("Detected FSH entity definitions with duplicate names. While FSH allows for duplicate "
          + "names across entity types, they can lead to ambiguous results when referring to these "
          + "entities by name elsewhere (e.g., in references). Consider using unique names in FSH "
          + "declarations and assigning duplicated names using caret assignment rules instead. "
          + "Detected duplicate names: " + String.join(", ", duplicateEntities) + ".");
    }
  }

  private static boolean otherWithName(Map<String, ? extends FshEntity> m, String name, FshEntity entity) {
    return m.containsKey(name) && m.get(name) != entity;
  }

  /**
   * The first FSH entity matching item (name, id, url; alias-resolved; optional |version), trying types in the given
   * order (all tank types when empty: Profile, Extension, Logical, Resource, ValueSet, CodeSystem, Instance,
   * Invariant, RuleSet, Mapping), or null. Returns Profile | Extension | Logical | Resource | FshValueSet |
   * FshCodeSystem | Instance | Invariant | RuleSet | Mapping.
   */
  public FshEntity fish(String item, FishableType... types) {
    List<FshEntity> r = internalFish(item, types, true);
    return r.isEmpty() ? null : r.get(0);
  }

  /** All matching FSH entities, in the order fish would try them. */
  public List<FshEntity> fishAll(String item, FishableType... types) {
    return internalFish(item, types, false);
  }

  private List<FshEntity> internalFish(String item, FishableType[] types, boolean stopOnFirstMatch) {
    List<FshEntity> results = new ArrayList<>();
    // Resolve alias if necessary
    String resolved = resolveAlias(item);
    if (resolved != null) item = resolved;
    // version needs to be checked separately from the base
    String base;
    String version;
    if (item == null) {
      base = "";
      version = null;
    } else {
      String[] parts = item.split("\\|", -1);
      base = parts[0];
      String v = String.join("|", Arrays.asList(parts).subList(1, parts.length));
      version = v.isEmpty() ? null : v;
    }
    if (types == null || types.length == 0) types = ALL_TANK_TYPES;

    String canonical = config == null ? null : config.canonical;
    String cfgVersion = config == null ? null : config.version;
    Predicate<FshEntity> versionOk =
        e -> version == null || version.equals(getVersionFromFshDefinition(e, cfgVersion));
    Predicate<FshEntity> nameIdUrl = e -> base.equals(nameOf(e)) || base.equals(FhirCommonBridge.idOf(e))
        || base.equals(getUrlFromFshDefinition(e, canonical));

    List<Instance> allInstances = getAllInstances();
    for (FishableType t : types) {
      List<? extends FshEntity> entitiesToSearch;
      Predicate<FshEntity> entityMatcher;
      Predicate<Instance> instanceMatcher = null;
      switch (t) {
        case Profile -> {
          entitiesToSearch = getAllProfiles();
          entityMatcher = nameIdUrl.and(versionOk);
          instanceMatcher = i -> sdDefinition(i, base, canonical, version, cfgVersion)
              && hasCodeRule(i, "derivation", "constraint") && !hasStringRule(i, "type", "Extension");
        }
        case Extension -> {
          entitiesToSearch = getAllExtensions();
          entityMatcher = nameIdUrl.and(versionOk);
          instanceMatcher = i -> sdDefinition(i, base, canonical, version, cfgVersion)
              && hasCodeRule(i, "derivation", "constraint") && hasStringRule(i, "type", "Extension");
        }
        case Logical -> {
          entitiesToSearch = getAllLogicals();
          entityMatcher = nameIdUrl.and(versionOk);
          instanceMatcher = i -> sdDefinition(i, base, canonical, version, cfgVersion)
              && hasCodeRule(i, "derivation", "specialization") && hasCodeRule(i, "kind", "logical");
        }
        case Resource -> {
          entitiesToSearch = getAllResources();
          entityMatcher = nameIdUrl.and(versionOk);
          instanceMatcher = i -> sdDefinition(i, base, canonical, version, cfgVersion)
              && hasCodeRule(i, "derivation", "specialization") && hasCodeRule(i, "kind", "resource");
        }
        case ValueSet -> {
          entitiesToSearch = getAllValueSets();
          entityMatcher = nameIdUrl.or(e -> base.equals(Common.getNonInstanceValueFromRules(e, "name", "", "name")))
              .and(versionOk);
          instanceMatcher = i -> "ValueSet".equals(i.instanceOf)
              && (i.usage == InstanceUsage.Definition || i.usage == InstanceUsage.Inline)
              && (base.equals(i.name) || base.equals(i.getId()) || base.equals(getUrlFromFshDefinition(i, canonical))
                  || base.equals(Common.getNonInstanceValueFromRules(i, "name", "", "name")))
              && versionOk.test(i);
        }
        case CodeSystem -> {
          entitiesToSearch = getAllCodeSystems();
          entityMatcher = nameIdUrl.or(e -> base.equals(Common.getNonInstanceValueFromRules(e, "name", "", "name")))
              .and(versionOk);
          instanceMatcher = i -> "CodeSystem".equals(i.instanceOf)
              && (i.usage == InstanceUsage.Definition || i.usage == InstanceUsage.Inline)
              && (base.equals(i.name) || base.equals(i.getId()) || base.equals(getUrlFromFshDefinition(i, canonical))
                  || base.equals(Common.getNonInstanceValueFromRules(i, "name", "", "name")))
              && versionOk.test(i);
        }
        case Instance -> {
          entitiesToSearch = allInstances;
          entityMatcher = e -> (base.equals(((Instance) e).name) || base.equals(((Instance) e).getId()))
              && versionOk.test(e);
        }
        case Invariant -> {
          entitiesToSearch = getAllInvariants();
          entityMatcher = e -> base.equals(((Invariant) e).name);
        }
        case RuleSet -> {
          entitiesToSearch = getAllRuleSets();
          entityMatcher = e -> base.equals(((RuleSet) e).name);
        }
        case Mapping -> {
          entitiesToSearch = getAllMappings();
          entityMatcher = e -> base.equals(((Mapping) e).name);
        }
        default -> {
          // Tank doesn't support these types
          continue;
        }
      }
      if (stopOnFirstMatch) {
        for (FshEntity e : entitiesToSearch) {
          if (entityMatcher.test(e)) return List.of(e);
        }
        if (instanceMatcher != null) {
          for (Instance i : allInstances) {
            if (instanceMatcher.test(i)) return List.of(i);
          }
        }
      } else {
        for (FshEntity e : entitiesToSearch) {
          if (entityMatcher.test(e)) results.add(e);
        }
        if (instanceMatcher != null) {
          for (Instance i : allInstances) {
            if (instanceMatcher.test(i)) results.add(i);
          }
        }
      }
    }
    return results;
  }

  private static boolean sdDefinition(Instance i, String base, String canonical, String version, String cfgVersion) {
    return "StructureDefinition".equals(i.instanceOf) && i.usage == InstanceUsage.Definition
        && (base.equals(i.name) || base.equals(i.getId()) || base.equals(getUrlFromFshDefinition(i, canonical)))
        && (version == null || version.equals(getVersionFromFshDefinition(i, cfgVersion)));
  }

  private static boolean hasCodeRule(Instance i, String path, String code) {
    for (Rule rule : i.rules) {
      if (rule instanceof AssignmentRule a && path.equals(a.path) && a.value instanceof FshCode c
          && code.equals(c.code)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasStringRule(Instance i, String path, String value) {
    for (Rule rule : i.rules) {
      if (rule instanceof AssignmentRule a && path.equals(a.path) && value.equals(a.value)) return true;
    }
    return false;
  }

  public RuleSet fishForAppliedRuleSet(String item) {
    for (FSHDocument doc : docs) {
      RuleSet ruleSet = doc.appliedRuleSets.get(item);
      if (ruleSet != null) return ruleSet;
    }
    return null;
  }

  /** Always null: the tank cannot return FHIR definitions (TS returns undefined). */
  @Override public Object fishForFHIR(String item, FishableType... types) {
    return null;
  }

  @Override public Metadata fishForMetadata(String item, FishableType... types) {
    return extractMetadataFromEntity(fish(item, types));
  }

  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
    List<Metadata> out = new ArrayList<>();
    for (FshEntity e : fishAll(item, types)) out.add(extractMetadataFromEntity(e));
    return out;
  }

  private Metadata extractMetadataFromEntity(FshEntity entity) {
    if (entity == null) return null;
    FhirCommonBridge.applyInsertRules.accept(entity, this);
    Metadata meta = new Metadata();
    meta.id = FhirCommonBridge.idOf(entity);
    meta.name = nameOf(entity);
    String canonical = config == null ? null : config.canonical;
    String cfgVersion = config == null ? null : config.version;
    if (entity instanceof FshStructure sd) {
      meta.url = getUrlFromFshDefinition(entity, canonical);
      meta.version = getVersionFromFshDefinition(entity, cfgVersion);
      meta.parent = sd.parent;
      meta.resourceType = "StructureDefinition";
      List<Object> imposeProfiles = findExtensionValues(sd, FhirCommonBridge.IMPOSE_PROFILE_EXTENSION,
          "structuredefinition-imposeProfile", "SDImposeProfile");
      if (!imposeProfiles.isEmpty()) meta.imposeProfiles = imposeProfiles;
      if (entity instanceof Logical logical) {
        // Logical models should always use an absolute URL as their StructureDefinition.type
        // unless HL7 published them. In that case, the URL is relative to http://hl7.org/fhir/StructureDefinition/.
        meta.sdType = meta.url.startsWith(HL7_SD_BASE_URL) ? meta.url.substring(HL7_SD_BASE_URL.length()) : meta.url;
        meta.canBeTarget = hasLogicalCharacteristic(logical, "can-be-target");
        meta.canBind = hasLogicalCharacteristic(logical, "can-bind");
      }
    } else if (entity instanceof FshValueSet || entity instanceof FshCodeSystem) {
      meta.url = getUrlFromFshDefinition(entity, canonical);
      meta.version = getVersionFromFshDefinition(entity, cfgVersion);
      meta.resourceType = entity instanceof FshValueSet ? "ValueSet" : "CodeSystem";
    } else if (entity instanceof Instance inst) {
      meta.instanceUsage = inst.usage == null ? null : inst.usage.name();
      Object assignedUrl = Common.getNonInstanceValueFromRules(inst, "url", "", "url");
      if (assignedUrl instanceof String s) meta.url = s;
      meta.version = getVersionFromFshDefinition(inst, cfgVersion);
      if ("StructureDefinition".equals(inst.instanceOf)) {
        meta.resourceType = "StructureDefinition";
        Object assignedBaseDefinition =
            Common.getNonInstanceValueFromRules(inst, "baseDefinition", "", "baseDefinition");
        if (assignedBaseDefinition instanceof String s) {
          meta.parent = s.startsWith(HL7_SD_BASE_URL) ? s.substring(HL7_SD_BASE_URL.length()) : s;
        }
        Object assignedKind = Common.getNonInstanceValueFromRules(inst, "kind", "", "kind");
        if (assignedKind instanceof FshCode c && "logical".equals(c.code) && meta.url != null) {
          meta.sdType =
              meta.url.startsWith(HL7_SD_BASE_URL) ? meta.url.substring(HL7_SD_BASE_URL.length()) : meta.url;
        }
      } else if ("ValueSet".equals(inst.instanceOf)) {
        meta.resourceType = "ValueSet";
      } else if ("CodeSystem".equals(inst.instanceOf)) {
        meta.resourceType = "CodeSystem";
      }
    }
    return meta;
  }

  private boolean hasLogicalCharacteristic(Logical logical, String code) {
    if (logical.characteristics.contains(code)) return true;
    List<Object> charValues = findExtensionValues(logical, FhirCommonBridge.TYPE_CHARACTERISTICS_EXTENSION,
        "structuredefinition-type-characteristics", "SDTypeCharacteristics");
    if (charValues.stream().anyMatch(v -> v instanceof FshCode c && code.equals(c.code))) return true;
    if ("can-be-target".equals(code)) {
      List<Object> targetValues = findExtensionValues(logical, FhirCommonBridge.LOGICAL_TARGET_EXTENSION,
          "logical-target", "LogicalTarget");
      if (targetValues.stream().anyMatch(v -> Boolean.TRUE.equals(v))) return true;
    }
    return false;
  }

  private static final Pattern NUMERIC_EXT = Pattern.compile("^extension(\\[\\d+\\])?\\.(url|value[A-Z][A-Za-z0-9]*)\\z");
  private static final Pattern NAMED_EXT =
      Pattern.compile("^extension(\\[([^\\]]+)\\](\\[\\d+\\])?)\\.value[A-Z][A-Za-z0-9]*\\z");

  private List<Object> findExtensionValues(FshStructure item, String extensionUrl, String extensionId,
      String extensionName) {
    // Iterate the rules and collect the indices for rules regarding the desired extension
    // and the values for all rules that assign a simple extension value
    List<String> matchingExtensionIndexers = new ArrayList<>();
    Map<String, Object> indexerToValueMap = new LinkedHashMap<>();
    for (Rule rule : item.rules) {
      if ("".equals(rule.path) && rule instanceof CaretValueRule cr && Common.truthy(cr.caretPath)) {
        // Match on simple extension url or value rules using a numeric index
        Matcher numericMatch = NUMERIC_EXT.matcher(cr.caretPath);
        if (numericMatch.find()) {
          String indexer = numericMatch.group(1) != null ? numericMatch.group(1) : "[0]";
          if ("url".equals(numericMatch.group(2))) {
            // It's an extension url, but is it the one we want?
            if (Common.jsString(cr.value).equals(extensionUrl)) matchingExtensionIndexers.add(indexer);
          } else {
            // We might not know if it's the extension indexer we want, so store it in case
            indexerToValueMap.put(indexer, cr.value);
          }
        } else {
          // If it wasn't a numeric indexer, check for a url/id/name indexer w/ a value
          Matcher namedMatch = NAMED_EXT.matcher(cr.caretPath);
          if (namedMatch.find()) {
            String resolved = resolveAlias(namedMatch.group(2));
            String name = resolved != null ? resolved : namedMatch.group(2);
            if (List.of(extensionUrl, extensionId, extensionName).contains(name)) {
              String indexer = "[" + extensionUrl + "]" + (namedMatch.group(3) != null ? namedMatch.group(3) : "[0]");
              matchingExtensionIndexers.add(indexer);
              indexerToValueMap.put(indexer, cr.value);
            }
          }
        }
      }
    }
    List<Object> out = new ArrayList<>();
    for (String index : matchingExtensionIndexers) {
      Object v = indexerToValueMap.get(index);
      if (v != null) out.add(v);
    }
    return out;
  }

  static String nameOf(FshEntity e) {
    if (e instanceof FshStructure s) return s.name;
    if (e instanceof Instance i) return i.name;
    if (e instanceof FshValueSet v) return v.name;
    if (e instanceof FshCodeSystem c) return c.name;
    if (e instanceof Invariant i) return i.name;
    if (e instanceof RuleSet r) return r.name;
    if (e instanceof Mapping m) return m.name;
    if (e instanceof ParamRuleSet p) return p.name;
    return null;
  }
}
