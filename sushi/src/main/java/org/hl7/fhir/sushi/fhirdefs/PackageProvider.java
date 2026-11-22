package org.hl7.fhir.sushi.fhirdefs;

import java.util.List;

/**
 * Where packages come from. Production: {@link CorePackageProvider}, i.e. org.hl7.fhir.core's
 * {@code FilesystemPackageCacheManager} as is (cache, registry, download, version resolution incl. {@code x.y.x},
 * {@code current}, {@code dev}). Tests: a fake (testhelpers.TestFHIRDefinitions).
 */
public interface PackageProvider {
  /** A package in the local cache, as core returned it. */
  record CachedPackage(String name, String version, String packagePath, String packageJSONPath,
      List<String> resourcePaths) {}

  /** The concrete version {@code latest} stands for. Throws when it cannot be determined. */
  String resolveLatest(String name) throws Exception;

  /** Whether {@code name#version} is in the local cache now (used only to word FPL's download messages). */
  boolean isCached(String name, String version);

  /** Loads (downloading/refreshing as needed) {@code name#version}; throws when it cannot be loaded. */
  CachedPackage loadPackage(String name, String version) throws Exception;

  /** The JSON (JS value model) of a package file; throws when it cannot be read or parsed. */
  Object readResource(String path);

  /**
   * FPL BuildDotFhirDotOrgClient.getCurrentBuildBaseURL: the build.fhir.org base URL of the newest current build of
   * {@code name} (branch null = master/main), "" when build.fhir.org lists none; throws when qas.json can't be read.
   * Returns null when the provider can't check (test fakes): then no freshness warning is derived.
   */
  default String currentBuildBaseUrl(String name, String branch) throws Exception {
    return null;
  }
}
