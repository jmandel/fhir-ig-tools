package org.hl7.fhir.sushi.fhirdefs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.Fs;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;

/**
 * {@link PackageProvider} = org.hl7.fhir.core's package layer, used as is (PORTING.md D1a):
 * <ul>
 *   <li>cache: {@code FilesystemPackageCacheManager} over ~/.fhir/packages (same layout FPL uses);</li>
 *   <li>{@code latest}: {@code getLatestVersion(id, false)};</li>
 *   <li>everything else ({@code x.y.z}, {@code x.y.x}, {@code current}, {@code current$branch}, {@code dev}):
 *       {@code loadPackage(id, version)} (cache first, then the package servers / build.fhir.org);</li>
 *   <li>the resources of a package: {@code NpmPackage.listResources()} (the {@code package/.index.json} index).</li>
 * </ul>
 * Created lazily, so tests with a fake provider never touch the user's cache. Its own file and network access
 * goes through core's ManagedFileAccess / ManagedWebAccess; so does this class's (registry and build.fhir.org
 * lookups, via {@code utils.Fs} and ManagedWebAccess), PORTING.md "I/O policy".
 */
public final class CorePackageProvider implements PackageProvider {
  private final String cacheFolder;
  private FilesystemPackageCacheManager pcm;

  public CorePackageProvider(String cacheFolder) {
    this.cacheFolder = cacheFolder;
  }

  /** ~/.fhir/packages (FPL: path.join(os.homedir(), '.fhir', 'packages')); -Dsushi.fhirCache overrides. */
  public static CorePackageProvider userCache() {
    String override = System.getProperty("sushi.fhirCache");
    return new CorePackageProvider(override != null ? override
        : Path.of(System.getProperty("user.home"), ".fhir", "packages").toString());
  }

  public synchronized FilesystemPackageCacheManager pcm() {
    if (pcm == null) {
      try {
        // FPL creates the cache folder when it is missing (a machine that never had a FHIR package cache); core's
        // withCacheFolder throws "The folder ... could not be found" instead, which surfaced as every package
        // failing to download
        Fs.createDirectories(cacheFolder);
        pcm = new FilesystemPackageCacheManager.Builder().withCacheFolder(cacheFolder).build();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return pcm;
  }

  @Override
  public String resolveLatest(String name) throws Exception {
    // FPL rule R2 (corpus case HL7__cds-hooks-library, docs/package-resolution-differences.md): #latest is the
    // registry's dist-tags.latest (FPL registry/utils.js lookUpLatestVersion), tried on packages.fhir.org then
    // packages2.fhir.org (DefaultRegistryClient), or only $FPL_REGISTRY when set. core's getLatestVersion picks the
    // highest version and can return a prerelease (3.0.0-ballot instead of 2.0.1).
    String custom = System.getenv("FPL_REGISTRY");
    List<String> endpoints = custom != null && !custom.isEmpty() ? List.of(custom)
        : List.of("https://packages.fhir.org", "https://packages2.fhir.org/packages");
    for (String endpoint : endpoints) {
      try {
        org.hl7.fhir.utilities.http.HTTPResult res =
            org.hl7.fhir.utilities.http.ManagedWebAccess.get(List.of("web"), endpoint + "/" + name);
        res.checkThrowException();
        Object data = Json.parse(res.getContentAsString());
        if (data instanceof Map<?, ?> m && m.get("dist-tags") instanceof Map<?, ?> tags
            && tags.get("latest") instanceof String latest && !latest.isEmpty()) {
          return latest;
        }
      } catch (Exception e) {
        // FPL: fall back to the next registry
      }
    }
    return null;
  }

  /**
   * FPL rule R1 (probe case hl7-eu__imaging-api, docs/package-resolution-differences.md): {@code dev} counts as cached
   * only when the {@code name#dev} folder itself exists. core's loadPackageFromCacheOnly answers a {@code dev}
   * request with a cached {@code current} build, which would hide SUSHI's "Falling back to X#current ..." message
   * and register the package as {@code #dev} instead of {@code #current}.
   */
  @Override
  public boolean isCached(String name, String version) {
    if ("dev".equals(version)) {
      return Fs.isDirectory(Path.of(pcm().getFolder(), name + "#dev").toString());
    }
    try {
      return pcm().loadPackageFromCacheOnly(name, version) != null;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public CachedPackage loadPackage(String name, String version) throws Exception {
    NpmPackage npm = pcm().loadPackage(name, version);
    if (npm == null) throw new IOException("Unable to find package " + name + "#" + version);
    String path = npm.getPath();
    Path pj = Path.of(path, "package", "package.json");
    NpmPackage.NpmPackageFolder folder = npm.getFolders().get("package");
    String dir = folder != null && folder.getFolderPath() != null ? folder.getFolderPath()
        : Path.of(path, "package").toString();
    List<String> files = new ArrayList<>();
    for (String f : npm.listResources()) {
      files.add(Path.of(dir, f).toString());
    }
    return new CachedPackage(npm.name(), npm.version(), path, Fs.exists(pj.toString()) ? pj.toString() : null, files);
  }

  @Override
  public Object readResource(String path) {
    try {
      return Json.parseFile(DiskBasedVirtualPackage.readUtf8(path));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to get JSON resource at path " + path);
    }
  }
}
