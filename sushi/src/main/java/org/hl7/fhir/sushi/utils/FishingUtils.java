package org.hl7.fhir.sushi.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.FshStructure;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Mapping;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.importer.FSHTank;

// port of sushi/src/utils/FishingUtils.ts (SUSHI 3.21.1)
public final class FishingUtils {
  private FishingUtils() {}

  /** item "base|v1|v2" -> {base, "v1|v2" or null} (TS: [base, ...versionParts] = item.split('|')). */
  private static String[] splitVersion(String item) {
    List<String> parts = Arrays.asList(item.split("\\|", -1));
    String version = String.join("|", parts.subList(1, parts.size()));
    return new String[] {parts.get(0), version.isEmpty() ? null : version};
  }

  private static String versionOf(Object fhir) {
    return fhir instanceof Map<?, ?> m && m.get("version") instanceof String s ? s : null;
  }

  /**
   * Use the provided fisher to fish for the item, which might have a |version appended. If found, return it. If not
   * found, try to remove any |version info and fish for any available version.
   */
  public static Object fishForFHIRBestVersion(Fishable fisher, String item, SourceInfo sourceInfo,
      FishableType... types) {
    Object result = fisher.fishForFHIR(item, types);

    // If the item was not found, try slicing off a version and searching for any version
    if (result == null && item != null && item.contains("|")) {
      String[] bv = splitVersion(item);
      result = fisher.fishForFHIR(bv[0], types);
      String resultVersion = versionOf(result);
      if (bv[1] != null && resultVersion != null && !bv[1].equals(resultVersion)) {
        FshLogger.logger.warn(item + " was requested, but SUSHI found " + bv[0] + "|" + resultVersion, sourceInfo);
      }
    }
    return result;
  }

  /**
   * Use the provided fisher to fish for the item's metadata. If found, return it. If not found, try to remove any
   * |version info and fish for any available version.
   */
  public static Metadata fishForMetadataBestVersion(Fishable fisher, String item, SourceInfo sourceInfo,
      FishableType... types) {
    if (fisher == null) {
      return null;
    }
    Metadata metadata = fisher.fishForMetadata(item, types);

    // If the item was not found, try slicing off a version and searching for any version
    if (metadata == null && item != null && item.contains("|")) {
      String[] bv = splitVersion(item);
      metadata = fisher.fishForMetadata(bv[0], types);
      if (bv[1] != null && metadata != null && metadata.version != null && !bv[1].equals(metadata.version)) {
        FshLogger.logger.warn(item + " was requested, but SUSHI found " + bv[0] + "|" + metadata.version,
            sourceInfo);
      }
    }
    return metadata;
  }

  /**
   * Use the provided tank to fish for the item, which might have a |version appended. If the specified version is
   * found, return it. If not found, try to remove any |version info and fish for any available version.
   */
  public static FshEntity fishInTankBestVersion(FSHTank tank, String item, SourceInfo sourceInfo,
      FishableType... types) {
    FshEntity result = tank.fish(item, types);

    // If the item was not found, try slicing off a version and searching for any version
    if (result == null && item != null && item.contains("|")) {
      String[] bv = splitVersion(item);
      result = tank.fish(bv[0], types);
      if (result != null) {
        String resultVersion = tank.config.version;
        if (!(result instanceof Invariant || result instanceof RuleSet || result instanceof Mapping)) {
          resultVersion = Common.getVersionFromFshDefinition(result, tank.config.version);
        }
        if (bv[1] != null && resultVersion != null && !bv[1].equals(resultVersion)) {
          FshLogger.logger.warn(item + " was requested, but SUSHI found " + bv[0] + "|" + resultVersion,
              sourceInfo);
        }
      }
    }
    return result;
  }

  /** TS {@code entity.id} for anything the tank can return (null when the entity has none). */
  public static String entityId(FshEntity e) {
    if (e instanceof FshStructure s) return s.getId();
    if (e instanceof Instance i) return i.getId();
    if (e instanceof FshValueSet v) return v.getId();
    if (e instanceof FshCodeSystem c) return c.getId();
    if (e instanceof Invariant i) return i.getId();
    if (e instanceof RuleSet r) return r.getId();
    if (e instanceof Mapping m) return m.id;
    return null;
  }

  /** TS {@code entity.name} for anything the tank can return (null when the entity has none). */
  public static String entityName(FshEntity e) {
    if (e instanceof FshStructure s) return s.name;
    if (e instanceof Instance i) return i.name;
    if (e instanceof FshValueSet v) return v.name;
    if (e instanceof FshCodeSystem c) return c.name;
    if (e instanceof Invariant i) return i.name;
    if (e instanceof RuleSet r) return r.name;
    if (e instanceof Mapping m) return m.name;
    return null;
  }
}
