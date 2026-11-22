package org.hl7.fhir.sushi.fhirdefs;

import java.io.InputStream;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirdefs.FplErrors.IncorrectWildcardVersionFormatError;
import org.hl7.fhir.sushi.fhirdefs.FplErrors.LatestVersionUnavailableError;
import org.hl7.fhir.utilities.npm.PackageClient;
import org.hl7.fhir.utilities.npm.PackageServer;

/**
 * {@link RegistryClient} on org.hl7.fhir.core as is (D1a): {@code latest} is core's
 * {@code FilesystemPackageCacheManager.getLatestVersion(id, false)} (highest version on the configured package
 * servers, packages2.fhir.org then packages.fhir.org, falling back to the newest cached version when offline);
 * {@code x.y.x} is core's {@code getLatestVersion(id, "x.y.x")}; downloads use core's {@code PackageClient} over the
 * same servers. FPL's error types and messages are kept for the failures SUSHI reports.
 */
final class CoreRegistryClient implements RegistryClient {
  private static final Pattern PATCH_WILDCARD = Pattern.compile("^\\d+\\.\\d+\\.x$");
  private static final Pattern MINOR_WILDCARD = Pattern.compile("^\\d+\\.x$");

  private final CoreServices core;

  CoreRegistryClient(CoreServices core) {
    this.core = core;
  }

  @Override
  public String resolveVersion(String name, String version) {
    if ("latest".equals(version)) {
      try {
        String v = core.pcm().getLatestVersion(name, false);
        if (v == null || v.isEmpty()) throw new LatestVersionUnavailableError(name, false);
        return v;
      } catch (LatestVersionUnavailableError e) {
        throw e;
      } catch (Exception e) {
        throw new LatestVersionUnavailableError(name, false);
      }
    } else if (version != null && PATCH_WILDCARD.matcher(version).matches()) {
      try {
        String v = core.pcm().getLatestVersion(name, version);
        if (v == null || v.isEmpty()) throw new LatestVersionUnavailableError(name, true);
        return v;
      } catch (LatestVersionUnavailableError e) {
        throw e;
      } catch (Exception e) {
        throw new LatestVersionUnavailableError(name, true);
      }
    } else if (version != null && MINOR_WILDCARD.matcher(version).matches()) {
      throw new IncorrectWildcardVersionFormatError(name, version);
    }
    return version;
  }

  @Override
  public InputStream download(String name, String version) throws Exception {
    for (PackageServer server : core.pcm().getPackageServers()) {
      try {
        PackageClient pc = new PackageClient(server);
        core.log.accept("info", "Attempting to download " + name + "#" + version + " from " + pc.url(name, version));
        InputStream s = pc.fetch(name, version);
        if (s != null) return s;
      } catch (Exception e) {
        // Do nothing. Fallback to the next one.
      }
    }
    throw new IllegalStateException("Failed to download " + name + "#" + version);
  }
}
