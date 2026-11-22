package org.hl7.fhir.sushi.utils;

import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.importer.FSHTank;

// port of sushi/src/utils/FishingUtils.ts (SUSHI 3.21.1) -- WP-6 signatures (day 1); implementation on branch wp6.
public final class FishingUtils {
  private FishingUtils() {}

  /** Fish for item (maybe "x|version"); if not found, retry without the version (warns on a version mismatch). */
  public static Object fishForFHIRBestVersion(Fishable fisher, String item, SourceInfo sourceInfo,
      FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  /** Metadata variant of {@link #fishForFHIRBestVersion}; null fisher -&gt; null. */
  public static Metadata fishForMetadataBestVersion(Fishable fisher, String item, SourceInfo sourceInfo,
      FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  /** Tank variant: returns the FSH entity. */
  public static FshEntity fishInTankBestVersion(FSHTank tank, String item, SourceInfo sourceInfo,
      FishableType... types) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  /** TS {@code entity.id} for anything the tank can return (null when the entity has none). */
  public static String entityId(FshEntity e) {
    throw new UnsupportedOperationException("TODO WP-6");
  }

  /** TS {@code entity.name} for anything the tank can return (null when the entity has none). */
  public static String entityName(FshEntity e) {
    throw new UnsupportedOperationException("TODO WP-6");
  }
}
