package org.hl7.fhir.sushi.fhirdefs;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.utilities.npm.NpmPackage;

/**
 * {@link PackageCache} on org.hl7.fhir.core's {@code FilesystemPackageCacheManager} and {@code NpmPackage}, used as
 * is (D1a): which cached package satisfies {@code name#version} is core's {@code loadPackageFromCacheOnly} (so e.g.
 * {@code dev} falls back to a cached {@code current}), and the potential resources of a package are core's index of
 * its {@code package/} folder ({@code NpmPackage.listResources()}, from {@code .index.json}). FPL lists every
 * {@code package/*.json} file instead; the lookup probe measures whether that ever matters (see
 * docs/package-resolution-differences.md).
 */
final class CorePackageCache implements PackageCache, CurrentRefresher {
  private final CoreServices core;
  // NpmPackage per name#version as returned by core (null = not cached), refreshed after caching a tarball
  private final Map<String, NpmPackage> loaded = new HashMap<>();

  CorePackageCache(CoreServices core) {
    this.core = core;
  }

  private NpmPackage pkg(String name, String version) {
    String key = name + "#" + version;
    if (loaded.containsKey(key)) return loaded.get(key);
    NpmPackage p;
    try {
      p = core.pcm().loadPackageFromCacheOnly(name, version);
    } catch (Exception e) {
      p = null;
    }
    if (p != null) loaded.put(key, p);
    return p;
  }

  @Override
  public String cachePackageTarball(String name, String version, InputStream data) throws Exception {
    String packageLabel = name + "#" + version;
    NpmPackage p = core.pcm().addPackageToCache(name, version, data, packageLabel);
    core.log.accept("info", "Downloaded " + packageLabel);
    loaded.put(packageLabel, p);
    String targetDirectory = p.getPath();
    core.log.accept("info", "Cached " + packageLabel + " to " + targetDirectory);
    return targetDirectory;
  }

  @Override
  public void refreshCurrent(String name, String version) throws Exception {
    // core decides staleness against build.fhir.org and downloads when needed
    NpmPackage p = core.pcm().loadPackage(name, version);
    if (p != null) loaded.put(name + "#" + version, p);
  }

  @Override
  public boolean isPackageInCache(String name, String version) {
    return pkg(name, version) != null;
  }

  @Override
  public String getPackagePath(String name, String version) {
    NpmPackage p = pkg(name, version);
    return p == null ? null : p.getPath();
  }

  @Override
  public String getPackageJSONPath(String name, String version) {
    NpmPackage p = pkg(name, version);
    if (p == null) return null;
    Path json = Path.of(p.getPath(), "package", "package.json");
    return Files.exists(json) ? json.toString() : null;
  }

  @Override
  public List<String> getPotentialResourcePaths(String name, String version) {
    NpmPackage p = pkg(name, version);
    if (p == null) return List.of();
    List<String> res = new ArrayList<>();
    try {
      NpmPackage.NpmPackageFolder folder = p.getFolders().get("package");
      if (folder == null) return List.of();
      String dir = folder.getFolderPath() != null ? folder.getFolderPath() : p.getPath() + File.separator + "package";
      for (String file : p.listResources()) {
        res.add(Path.of(dir, file).toString());
      }
    } catch (Exception e) {
      return List.of();
    }
    return res;
  }

  @Override
  public Object getResourceAtPath(String resourcePath) {
    try {
      return Json.parseFile(DiskBasedVirtualPackage.readUtf8(resourcePath));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to get JSON resource at path " + resourcePath);
    }
  }
}
