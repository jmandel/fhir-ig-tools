package org.hl7.fhir.sushi.fhirdefs;

import java.io.InputStream;
import java.util.List;

// port of fhir-package-loader 2.2.4 src/cache/PackageCache.ts (interface; production: CorePackageCache)
/** The local package cache (~/.fhir/packages). Paths are absolute strings, as in FPL. */
public interface PackageCache {
  /** Adds a downloaded package (.tgz stream) to the cache; returns the package directory. */
  String cachePackageTarball(String name, String version, InputStream data) throws Exception;

  boolean isPackageInCache(String name, String version);

  /** null when not cached */
  String getPackagePath(String name, String version);

  /** null when not cached / no package.json */
  String getPackageJSONPath(String name, String version);

  /** The files of the package that may be resources, in load order. */
  List<String> getPotentialResourcePaths(String name, String version);

  /** The resource (JS value model) at an absolute path; throws when it cannot be read/parsed. */
  Object getResourceAtPath(String resourcePath);
}
