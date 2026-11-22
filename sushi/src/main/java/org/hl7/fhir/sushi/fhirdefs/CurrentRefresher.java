package org.hl7.fhir.sushi.fhirdefs;

/**
 * A package cache that refreshes {@code current}/{@code current$branch} packages itself (core's
 * {@code FilesystemPackageCacheManager.loadPackage} checks the cached build against build.fhir.org and downloads a
 * newer one). When the cache implements this, {@link BasePackageLoader} uses it instead of FPL's own
 * staleness check + {@link CurrentBuildClient} download (D1a: core's current-build handling as is).
 */
interface CurrentRefresher {
  void refreshCurrent(String name, String version) throws Exception;
}
