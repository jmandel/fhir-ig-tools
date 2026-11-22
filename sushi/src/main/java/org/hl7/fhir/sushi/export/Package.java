package org.hl7.fhir.sushi.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.hl7.fhir.sushi.fhirtypes.CodeSystem;
import org.hl7.fhir.sushi.fhirtypes.FhirResource;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fhirtypes.ValueSet;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.Metadata;

// port of sushi/src/export/Package.ts (SUSHI 3.21.1)
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

  private static final FishableType[] ALL_TYPES = {
    FishableType.Profile, FishableType.Extension, FishableType.Logical, FishableType.Resource,
    FishableType.ValueSet, FishableType.CodeSystem, FishableType.Instance
  };

  public Package(Configuration config) {
    this.config = config;
  }

  /** First match, trying types in order (all supported types when none are given). */
  public FhirResource fish(String item, FishableType... types) {
    List<FhirResource> r = internalFish(item, types, true);
    return r.isEmpty() ? null : r.get(0);
  }

  public List<FhirResource> fishAll(String item, FishableType... types) {
    return internalFish(item, types, false);
  }

  private List<FhirResource> internalFish(String item, FishableType[] types, boolean stopOnFirstMatch) {
    List<FhirResource> results = new ArrayList<>();
    // No types passed in means to search ALL supported types
    if (types == null || types.length == 0) {
      types = ALL_TYPES;
    }
    // version needs to be checked separately from the base
    String base;
    String version;
    if (item == null) {
      base = "";
      version = null;
    } else {
      List<String> parts = Arrays.asList(item.split("\\|", -1));
      base = parts.get(0);
      String v = String.join("|", parts.subList(1, parts.size()));
      version = v.isEmpty() ? null : v;
    }

    for (FishableType type : types) {
      FhirResource def = null;
      switch (type) {
        case Profile -> {
          def = findSd(profiles, base, version);
          if (def == null) {
            def = findInstance(i -> isDefinitionSd(i) && "constraint".equals(i.get("derivation"))
                && !"Extension".equals(i.get("type")) && matchesCanonical(i, base, version));
          }
        }
        case Extension -> {
          def = findSd(extensions, base, version);
          if (def == null) {
            def = findInstance(i -> isDefinitionSd(i) && "constraint".equals(i.get("derivation"))
                && "Extension".equals(i.get("type")) && matchesCanonical(i, base, version));
          }
        }
        case Logical -> {
          def = findSd(logicals, base, version);
          if (def == null) {
            def = findInstance(i -> isDefinitionSd(i) && "specialization".equals(i.get("derivation"))
                && "logical".equals(i.get("kind")) && matchesCanonical(i, base, version));
          }
        }
        case Resource -> {
          def = findSd(resources, base, version);
          if (def == null) {
            def = findInstance(i -> isDefinitionSd(i) && "specialization".equals(i.get("derivation"))
                && "resource".equals(i.get("kind")) && matchesCanonical(i, base, version));
          }
        }
        case ValueSet -> {
          def = findJs(valueSets, base, version);
          if (def == null) {
            def = findInstance(i -> "Definition".equals(i._instanceMeta.usage)
                && "ValueSet".equals(i.get("resourceType")) && matchesCanonical(i, base, version));
          }
        }
        case CodeSystem -> {
          def = findJs(codeSystems, base, version);
          if (def == null) {
            def = findInstance(i -> "Definition".equals(i._instanceMeta.usage)
                && "CodeSystem".equals(i.get("resourceType")) && matchesCanonical(i, base, version));
          }
        }
        case Instance -> def = findInstance(i -> (Objects.equals(i.get("id"), base)
            || Objects.equals(i._instanceMeta.name, base)) && versionMatches(i.get("version"), version));
        default -> {
          // Package doesn't currently support types
        }
      }
      if (def != null) {
        if (stopOnFirstMatch) {
          return List.of(def);
        }
        results.add(def);
      }
    }
    return results;
  }

  private static boolean versionMatches(Object actual, String version) {
    return version == null || Objects.equals(actual, version);
  }

  private static StructureDefinition findSd(List<StructureDefinition> sds, String base, String version) {
    for (StructureDefinition sd : sds) {
      if ((Objects.equals(sd.getId(), base) || Objects.equals(sd.getName(), base) || Objects.equals(sd.getUrl(), base))
          && versionMatches(sd.getVersion(), version)) {
        return sd;
      }
    }
    return null;
  }

  private static <T extends Map<String, Object> & FhirResource> T findJs(List<T> list, String base,
      String version) {
    for (T r : list) {
      if ((Objects.equals(r.get("id"), base) || Objects.equals(r.get("name"), base)
          || Objects.equals(r.get("url"), base)) && versionMatches(r.get("version"), version)) {
        return r;
      }
    }
    return null;
  }

  private InstanceDefinition findInstance(Predicate<InstanceDefinition> p) {
    for (InstanceDefinition i : instances) {
      if (p.test(i)) {
        return i;
      }
    }
    return null;
  }

  private static boolean isDefinitionSd(InstanceDefinition i) {
    return "Definition".equals(i._instanceMeta.usage) && "StructureDefinition".equals(i.get("resourceType"));
  }

  private static boolean matchesCanonical(InstanceDefinition i, String base, String version) {
    return (Objects.equals(i.get("id"), base) || Objects.equals(i._instanceMeta.name, base)
        || Objects.equals(i.get("url"), base)) && versionMatches(i.get("version"), version);
  }

  @Override public Object fishForFHIR(String item, FishableType... types) {
    FhirResource r = fish(item, types);
    return r == null ? null : r.toJSON();
  }

  @Override public Metadata fishForMetadata(String item, FishableType... types) {
    FhirResource result = fish(item, types);
    if (result != null) {
      return extractMetadata(result);
    } else if (item != null && isPackageItself(item)) {
      // If nothing is returned, perhaps the Package itself is being referenced
      return extractImplementationGuideMetadataFromConfig();
    }
    return null;
  }

  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
    List<Metadata> metadatas = new ArrayList<>();
    for (FhirResource r : fishAll(item, types)) {
      metadatas.add(extractMetadata(r));
    }
    // Also handle cases where the Package itself is being referenced
    if (item != null && isPackageItself(item)) {
      metadatas.add(extractImplementationGuideMetadataFromConfig());
    }
    return metadatas;
  }

  private boolean isPackageItself(String item) {
    return item.equals(config.packageId) || item.equals(config.name) || item.equals(config.id);
  }

  /** template-literal interpolation of an optional (null = undefined) value */
  static String undef(Object o) {
    return o == null ? "undefined" : Js.toStr(o);
  }

  private static String str(Object o) {
    return o instanceof String s ? s : null;
  }

  private Metadata extractMetadata(FhirResource result) {
    if (result == null) {
      return null;
    }
    Metadata metadata = new Metadata();
    if (result instanceof StructureDefinition sd) {
      metadata.id = sd.getId();
      metadata.name = sd.getName();
      metadata.url = sd.getUrl();
      metadata.version = sd.getVersion();
      metadata.resourceType = sd.getResourceType();
      metadata.sdType = sd.getType();
      metadata.parent = sd.getBaseDefinition();
      List<Object> imposeProfiles = org.hl7.fhir.sushi.fhirtypes.Common.findImposeProfiles(sd);
      if (imposeProfiles != null) {
        metadata.imposeProfiles = new ArrayList<>(imposeProfiles);
      }
      if ("logical".equals(sd.getKind())) {
        metadata.canBeTarget = hasExtension(sd.getExtension(), ext ->
            (org.hl7.fhir.sushi.fhirtypes.Common.TYPE_CHARACTERISTICS_EXTENSION.equals(ext.get("url"))
                && "can-be-target".equals(ext.get("valueCode")))
            || (org.hl7.fhir.sushi.fhirtypes.Common.LOGICAL_TARGET_EXTENSION.equals(ext.get("url"))
                && Boolean.TRUE.equals(ext.get("valueBoolean"))));
        // if the export is still in progress, there may be unprocessed rules that make this a valid reference
        // target; return undefined to force the metadata to come from the tank
        if (!metadata.canBeTarget && Boolean.TRUE.equals(sd.getInProgress())) {
          return null;
        }
        metadata.canBind = hasExtension(sd.getExtension(), ext ->
            org.hl7.fhir.sushi.fhirtypes.Common.TYPE_CHARACTERISTICS_EXTENSION.equals(ext.get("url"))
                && "can-bind".equals(ext.get("valueCode")));
        // (TS repeats the canBeTarget check here; kept for parity, it is a no-op)
        if (!metadata.canBeTarget && Boolean.TRUE.equals(sd.getInProgress())) {
          return null;
        }
      }
    } else if (result instanceof InstanceDefinition inst) {
      metadata.id = str(inst.get("id"));
      metadata.name = inst._instanceMeta.name;
      metadata.instanceUsage = inst._instanceMeta.usage;
      metadata.url = str(inst.get("url"));
      metadata.version = str(inst.get("version"));
      metadata.resourceType = str(inst.get("resourceType"));
    } else if (result instanceof Map<?, ?> m) {
      // ValueSet / CodeSystem
      metadata.id = str(m.get("id"));
      metadata.name = str(m.get("name"));
      metadata.url = str(m.get("url"));
      metadata.version = str(m.get("version"));
      metadata.resourceType = str(m.get("resourceType"));
    }
    return metadata;
  }

  @SuppressWarnings("unchecked")
  private static boolean hasExtension(List<?> extensions, Predicate<Map<String, Object>> p) {
    if (extensions == null) {
      return false;
    }
    for (Object ext : extensions) {
      if (ext instanceof Map<?, ?> m && p.test((Map<String, Object>) m)) {
        return true;
      }
    }
    return false;
  }

  private Metadata extractImplementationGuideMetadataFromConfig() {
    Metadata metadata = new Metadata();
    String pkgId = Js.truthy(config.packageId) ? config.packageId : config.id;
    metadata.id = pkgId;
    metadata.name = config.name;
    metadata.url = Js.truthy(config.url) ? config.url
        : undef(config.canonical) + "/ImplementationGuide/" + undef(pkgId);
    metadata.version = config.version;
    metadata.resourceType = "ImplementationGuide";
    return metadata;
  }

  /** Resets all the definition arrays to be zero-length. This is useful for re-using a package during testing. */
  public void clearAllDefinitions() {
    profiles.clear();
    extensions.clear();
    logicals.clear();
    resources.clear();
    instances.clear();
    valueSets.clear();
    codeSystems.clear();
  }
}
