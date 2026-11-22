package org.hl7.fhir.sushi.fhirdefs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.js.Json;
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
 * Created lazily, so tests with a fake provider never touch the user's cache.
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
        pcm = new FilesystemPackageCacheManager.Builder().withCacheFolder(cacheFolder).build();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return pcm;
  }

  @Override
  public String resolveLatest(String name) throws Exception {
    return pcm().getLatestVersion(name, false);
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
      return Files.isDirectory(Path.of(pcm().getFolder(), name + "#dev"));
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
    return new CachedPackage(npm.name(), npm.version(), path, Files.exists(pj) ? pj.toString() : null, files);
  }

  @Override
  public Object readResource(String path) {
    try {
      return Json.parseFile(DiskBasedVirtualPackage.readUtf8(path));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to get JSON resource at path " + path);
    }
  }

  private Object qas; // build.fhir.org/ig/qas.json, fetched at most once per process (FPL fetches it per lookup)

  /** Port of FPL 2.2.4 BuildDotFhirDotOrgClient.getCurrentBuildBaseURL, HTTP via core's ManagedWebAccess. */
  @Override
  public synchronized String currentBuildBaseUrl(String name, String branch) throws Exception {
    String baseUrl = "https://build.fhir.org/ig";
    if (qas == null) {
      org.hl7.fhir.utilities.http.HTTPResult res =
          org.hl7.fhir.utilities.http.ManagedWebAccess.get(java.util.List.of("web"), baseUrl + "/qas.json");
      res.checkThrowException();
      qas = org.hl7.fhir.sushi.js.Json.parse(res.getContentAsString());
    }
    Map<String, Object> newest = null;
    if (qas instanceof List<?> list && !list.isEmpty()) {
      List<Map<String, Object>> matching = new ArrayList<>();
      for (Object o : list) {
        if (!(o instanceof Map<?, ?> m) || !name.equals(m.get("package-id"))) continue;
        @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) m;
        String repo = p.get("repo") instanceof String r ? r : "";
        boolean branchOk = branch == null ? java.util.regex.Pattern.compile("/(master|main)/qa\\.json$").matcher(repo).find()
            : repo.endsWith("/" + branch + "/qa.json");
        if (branchOk) matching.add(p);
      }
      // JS sort by Date.parse(p2.date) - Date.parse(p1.date): newest first, stable
      matching.sort((a, b) -> Double.compare(jsDate(b.get("date")), jsDate(a.get("date"))));
      if (!matching.isEmpty()) newest = matching.get(0);
    }
    if (newest != null && newest.get("repo") instanceof String repo && !repo.isEmpty()) {
      return baseUrl + "/" + repo.substring(0, Math.max(0, repo.length() - 8));
    }
    return "";
  }

  /** JS Date.parse for qas.json's "date" values (RFC 1123, e.g. "Sat, 18 Nov, 2023 01:23:45 +0000"); NaN sorts as 0. */
  private static double jsDate(Object v) {
    if (!(v instanceof String s)) return 0;
    for (java.time.format.DateTimeFormatter f : new java.time.format.DateTimeFormatter[] {
        java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM, yyyy HH:mm:ss Z", java.util.Locale.ENGLISH),
        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME}) {
      try {
        return java.time.ZonedDateTime.parse(s, f).toInstant().toEpochMilli();
      } catch (Exception e) {
        // try next
      }
    }
    return 0;
  }
}
