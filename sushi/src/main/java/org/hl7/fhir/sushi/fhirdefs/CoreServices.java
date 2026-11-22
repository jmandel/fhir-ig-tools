package org.hl7.fhir.sushi.fhirdefs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;

/**
 * The org.hl7.fhir.core package layer SUSHI runs on (D1a): one {@link FilesystemPackageCacheManager} over
 * ~/.fhir/packages (the user cache, like FPL's {@code path.join(os.homedir(), '.fhir', 'packages')}), created on
 * first use so that tests with overrides never touch it.
 */
final class CoreServices {
  private FilesystemPackageCacheManager pcm;
  private final String cacheFolder;
  /** FPL logs through the loader's log function (SUSHI logger + interceptor); set by FHIRDefinitions. */
  BiConsumer<String, String> log = (l, m) -> {};

  private CoreServices(String cacheFolder) {
    this.cacheFolder = cacheFolder;
  }

  static CoreServices lazy() {
    String override = System.getProperty("sushi.fhirCache");
    return new CoreServices(override != null ? override
        : Path.of(System.getProperty("user.home"), ".fhir", "packages").toString());
  }

  synchronized FilesystemPackageCacheManager pcm() {
    if (pcm == null) {
      try {
        pcm = new FilesystemPackageCacheManager.Builder().withCacheFolder(cacheFolder).build();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return pcm;
  }

  String cacheFolder() {
    return cacheFolder;
  }

  PackageCache packageCache() {
    return new CorePackageCache(this);
  }

  RegistryClient registryClient() {
    return new CoreRegistryClient(this);
  }

  CurrentBuildClient currentBuildClient() {
    return new CoreCurrentBuildClient(this);
  }
}
