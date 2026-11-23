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
 *   <li>{@code latest}: packages.fhir.org's {@code dist-tags.latest} (kept rule R2), else core's
 *       {@code getLatestVersion(id, false)};</li>
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
    // Kept rule R2 (corpus case HL7__cds-hooks-library): #latest is packages.fhir.org's dist-tags.latest, as FPL
    // takes it. core's getLatestVersion reads only the "versions" list and picks the highest, prereleases included
    // (hl7.fhir.uv.cds-hooks: 3.0.0-ballot instead of 2.0.1). When packages.fhir.org gives no dist-tags.latest,
    // core decides (its servers, build.fhir.org, then the newest cached version; core's error otherwise).
    try {
      org.hl7.fhir.utilities.http.HTTPResult res = org.hl7.fhir.utilities.http.ManagedWebAccess.get(List.of("web"),
          org.hl7.fhir.utilities.npm.PackageServer.SECONDARY_SERVER + "/" + name);
      res.checkThrowException();
      Object data = Json.parse(res.getContentAsString());
      if (data instanceof Map<?, ?> m && m.get("dist-tags") instanceof Map<?, ?> tags
          && tags.get("latest") instanceof String latest && !latest.isEmpty()) {
        return latest;
      }
    } catch (Exception e) {
      // core decides
    }
    return pcm().getLatestVersion(name, false);
  }

  @Override
  public boolean isCached(String name, String version) {
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
