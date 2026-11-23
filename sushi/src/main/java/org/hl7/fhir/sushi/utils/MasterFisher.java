package org.hl7.fhir.sushi.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.common.js.Js;

// port of sushi/src/utils/MasterFisher.ts (SUSHI 3.21.1)
/**
 * Fishes predefined resources, then the package (exported so far), then the tank (metadata only), then the external
 * FHIR definitions, resolving aliases first. Preferring local definitions first when there are naming clashes.
 *
 * <p>The MasterFisher also fixes up tank metadata: the tank cannot know an item's sdType (or an Instance's
 * resourceType), so they are derived from the other fishables.
 */
public class MasterFisher implements Fishable {
  public FSHTank tank;
  public FHIRDefinitions fhir;
  public Package pkg;
  public String defaultFHIRVersion;
  /** Only the FHIR definitions support Type.Type, and they don't change while fishing, so cache it. */
  private final Map<String, Object> typeCache = new LinkedHashMap<>();

  public MasterFisher() {
    this(null, null, null);
  }

  public MasterFisher(FSHTank tank, FHIRDefinitions fhir, Package pkg) {
    this.tank = tank;
    this.fhir = fhir;
    this.pkg = pkg;
    Object sd = fhir == null ? null : fhir.fishForFHIR("StructureDefinition");
    Object fhirVersion = sd instanceof Map<?, ?> m ? m.get("fhirVersion") : null;
    if (fhirVersion instanceof String s) {
      this.defaultFHIRVersion = s;
    } else if (tank != null && tank.config != null && tank.config.fhirVersion != null
        && !tank.config.fhirVersion.isEmpty()) {
      this.defaultFHIRVersion = tank.config.fhirVersion.get(0);
    }
  }

  private String resolve(String item) {
    if (tank == null) {
      return item;
    }
    String resolved = tank.resolveAlias(item);
    return resolved != null ? resolved : item;
  }

  /**
   * Searches for the FHIR JSON by name/id/url. It will first search through the local package (which contains FHIR
   * artifacts exported so far), then through the external FHIR definitions.
   */
  @Override public Object fishForFHIR(String item, FishableType... types) {
    // Resolve the alias if necessary
    item = resolve(item);

    String cacheKey = types.length == 1 && types[0] == FishableType.Type ? item : null;
    if (cacheKey != null && typeCache.containsKey(cacheKey)) {
      return typeCache.get(cacheKey);
    }

    Object result = fhir.fishForPredefinedResource(item, types);
    if (result != null) {
      if (cacheKey != null) {
        typeCache.put(cacheKey, result);
      }
      return result;
    }

    // First check for it in the package
    result = pkg == null ? null : pkg.fishForFHIR(item, types);
    if (result == null) {
      // If it is in the tank, return undefined. We don't want to return the external FHIR definition, even if it
      // exists -- because it won't match what is in the tank. This ensures consistency between the outputs of
      // fishForFHIR and fishForMetadata.
      if (tank != null && tank.fish(item, types) != null) {
        return null;
      }
      result = fhir == null ? null : fhir.fishForFHIR(item, types);
    }
    if (cacheKey != null) {
      typeCache.put(cacheKey, result);
    }
    return result;
  }

  private List<Fishable> fishables() {
    List<Fishable> fishables = new ArrayList<>(3);
    if (pkg != null) fishables.add(pkg);
    if (tank != null) fishables.add(tank);
    if (fhir != null) fishables.add(fhir);
    return fishables;
  }

  /**
   * Searches for the Metadata associated with the passed in name/id/url: package, then tank, then the external FHIR
   * definitions. Useful because it gets commonly used information without having to force an export.
   */
  @Override public Metadata fishForMetadata(String item, FishableType... types) {
    // Resolve the alias if necessary
    item = resolve(item);

    Metadata result = fhir.fishForPredefinedResourceMetadata(item, types);
    if (result != null) {
      return result;
    }

    List<Fishable> fishables = fishables();
    for (Fishable fishable : fishables) {
      result = fishable.fishForMetadata(item, types);
      if (result != null) {
        return fixMetadata(result, item, types, fishable, fishables);
      }
    }
    return null;
  }

  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
    // Resolve the alias if necessary
    item = resolve(item);

    List<Metadata> metadatas = new ArrayList<>(fhir.fishForPredefinedResourceMetadatas(item, types));

    List<Fishable> fishables = fishables();
    for (Fishable fishable : fishables) {
      for (Metadata result : fishable.fishForMetadatas(item, types)) {
        metadatas.add(fixMetadata(result, item, types, fishable, fishables));
      }
    }
    // It's possible to get duplicates for predefined resource or resources in package and tank, so de-dupe them
    // (lodash uniqWith(isEqual): first occurrence kept, order preserved)
    List<Metadata> unique = new ArrayList<>(metadatas.size());
    for (Metadata m : metadatas) {
      boolean seen = false;
      for (Metadata u : unique) {
        if (metadataEquals(u, m)) {
          seen = true;
          break;
        }
      }
      if (!seen) {
        unique.add(m);
      }
    }
    return unique;
  }

  /**
   * lodash isEqual on two Metadata objects. Java cannot tell an absent key from an undefined-valued one, so a key
   * explicitly set to undefined in TS compares equal to an absent key here (TS: not equal). The only such key SUSHI
   * sets is Package's {@code instanceUsage: undefined} on StructureDefinition metadata, which differs from tank
   * metadata in other keys anyway.
   */
  static boolean metadataEquals(Metadata a, Metadata b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    return Objects.equals(a.id, b.id) && Objects.equals(a.name, b.name) && Objects.equals(a.sdType, b.sdType)
        && Objects.equals(a.resourceType, b.resourceType) && Objects.equals(a.url, b.url)
        && Objects.equals(a.parent, b.parent) && Js.isEqual(a.imposeProfiles, b.imposeProfiles)
        && Objects.equals(a._abstract, b._abstract) && Objects.equals(a.version, b.version)
        && Objects.equals(a.instanceUsage, b.instanceUsage) && Objects.equals(a.canBeTarget, b.canBeTarget)
        && Objects.equals(a.canBind, b.canBind) && Objects.equals(a.resourcePath, b.resourcePath);
  }

  private Metadata fixMetadata(Metadata metadata, String item, FishableType[] types, Fishable fishable,
      List<Fishable> fishables) {
    if (metadata == null) {
      return null;
    }
    if (fishable instanceof FSHTank fshTank) {
      // If it came from the tank, we need to get the sdType because the tank doesn't know.
      metadata.sdType = findSdType(metadata, types, fishables);
      // When an Instance comes from the FSHTank, the FSHTank doesn't know its resourceType, only its InstanceOf.
      // But here we have access to the other fishers, so we can try to figure that resourceType out here
      if (!Js.truthy(metadata.resourceType)) {
        FshEntity fshDefinition = null;
        for (FshEntity e : fshTank.fishAll(item, types)) {
          // TS: e.id == metadata.id && e.name == metadata.name (loose equality: null == undefined)
          if (Objects.equals(FishingUtils.entityId(e), metadata.id)
              && Objects.equals(FishingUtils.entityName(e), metadata.name)) {
            fshDefinition = e;
            break;
          }
        }
        if (fshDefinition instanceof Instance inst) {
          Metadata m = fishForMetadata(inst.instanceOf);
          metadata.resourceType = m == null ? null : m.sdType;
        }
      }
    }
    // Add url to metadata for non-inline Instances
    if (!Js.truthy(metadata.url) && !"Inline".equals(metadata.instanceUsage)) {
      metadata.url = undef(pkg.config.canonical) + "/" + undef(metadata.resourceType) + "/" + undef(metadata.id);
    }
    return metadata;
  }

  private static String undef(Object o) {
    return o == null ? "undefined" : Js.toStr(o);
  }

  private String findSdType(Metadata meta, FishableType[] types, List<Fishable> fishables) {
    List<Metadata> history = new ArrayList<>();
    history.add(meta);
    String sdType = meta.sdType;
    String parent = meta.parent;
    while (sdType == null && parent != null) {
      // Resolve the alias if necessary
      parent = resolve(parent);

      Metadata parentResult = null;
      for (Fishable fishable : fishables) {
        parentResult = fishable.fishForMetadata(parent, types);
        if (parentResult != null) {
          final String parentUrl = parentResult.url;
          if (history.stream().anyMatch(md -> Objects.equals(md.url, parentUrl))) {
            List<String> names = new ArrayList<>();
            // Array.join renders undefined as ""
            for (Metadata md : history) names.add(md.name == null ? "" : md.name);
            names.add(parentResult.name == null ? "" : parentResult.name);
            String message = "Circular dependency detected on parent relationships: " + String.join(" < ", names);
            Metadata fhirMeta = null;
            if (fhir != null) {
              fhirMeta = fhir.fishForMetadata(parentResult.name);
              if (fhirMeta == null) {
                fhirMeta = fhir.fishForMetadata(parentResult.id);
              }
            }
            if (fhirMeta != null) {
              message += "\n  If the parent " + undef(parentResult.name)
                  + " is intended to refer to the FHIR resource, use its URL: " + undef(fhirMeta.url);
            }
            FshEntity parentEntity = fishable instanceof FSHTank t ? t.fish(parent) : null;
            FshLogger.logger.error(message, parentEntity == null ? null : parentEntity.sourceInfo);
            return null;
          }
          history.add(parentResult);
          break; // break out of fishables loop
        }
      }
      sdType = parentResult == null ? null : parentResult.sdType;
      parent = parentResult == null ? null : parentResult.parent;
    }
    return sdType;
  }
}
