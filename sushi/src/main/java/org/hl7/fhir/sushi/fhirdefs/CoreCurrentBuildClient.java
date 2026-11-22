package org.hl7.fhir.sushi.fhirdefs;

import java.io.InputStream;

/**
 * {@link CurrentBuildClient} placeholder for production: with {@link CorePackageCache} (a {@link CurrentRefresher}),
 * current builds are refreshed by core's {@code FilesystemPackageCacheManager.loadPackage(id, "current")} and this
 * client is never asked. It only exists so a custom {@link PackageCache} without its own refresh fails clearly.
 */
final class CoreCurrentBuildClient implements CurrentBuildClient {
  @SuppressWarnings("unused")
  private final CoreServices core;

  CoreCurrentBuildClient(CoreServices core) {
    this.core = core;
  }

  @Override
  public InputStream downloadCurrentBuild(String name, String branch) {
    throw new FplErrors.CurrentPackageLoadError(name + "#" + (branch != null ? "current$" + branch : "current"));
  }

  @Override
  public String getCurrentBuildDate(String name, String branch) {
    return null;
  }
}
