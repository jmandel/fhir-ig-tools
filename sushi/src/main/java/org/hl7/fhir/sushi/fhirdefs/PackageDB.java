package org.hl7.fhir.sushi.fhirdefs;

import java.util.List;

// port of fhir-package-loader 2.2.4 src/db/PackageDB.ts
/** The index of loaded packages and resources that every lookup goes through. */
public interface PackageDB {
  void clear();

  void optimize();

  void savePackageInfo(PackageInfo info);

  void saveResourceInfo(ResourceInfo info);

  /** {@code name} "*" = all packages; load order ascending. */
  List<PackageInfo> findPackageInfos(String name);

  PackageInfo findPackageInfo(String name, String version);

  /** {@code key}: id, name or url, optionally {@code |version}; "*" = all. */
  List<ResourceInfo> findResourceInfos(String key, FindResourceInfoOptions options);

  ResourceInfo findResourceInfo(String key, FindResourceInfoOptions options);

  /** null when the package is not loaded */
  PackageStats getPackageStats(String name, String version);
}
