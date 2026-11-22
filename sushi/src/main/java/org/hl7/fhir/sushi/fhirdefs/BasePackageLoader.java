package org.hl7.fhir.sushi.fhirdefs;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirdefs.FplErrors.InvalidResourceError;
import org.hl7.fhir.sushi.js.Js;

// port of fhir-package-loader 2.2.4 src/loader/BasePackageLoader.ts
/**
 * Loads packages into a {@link PackageDB} and answers lookups. The orchestration and every log message are FPL's;
 * the package cache, registry and current-build access are pluggable ({@link PackageCache}, {@link RegistryClient},
 * {@link CurrentBuildClient}). In production they are backed by org.hl7.fhir.core's package layer as is (D1a, see
 * {@link CorePackageCache}, {@link CoreRegistryClient}, {@link CoreCurrentBuildClient}).
 *
 * <p>Differences from FPL by design: SafeMode.FREEZE is not emulated (callers get the cached object, as FPL; they
 * must not mutate it — FPL would throw a TypeError); debug stack traces are Java's.
 */
public class BasePackageLoader {
  public static final int DEFAULT_RESOURCE_CACHE_SIZE = 200;

  static final String CERTIFICATE_MESSAGE =
      "\n\nSometimes this error occurs in corporate or educational environments that use proxies and/or SSL "
          + "inspection.\nTroubleshooting tips:\n"
          + "  1. If a non-proxied network is available, consider connecting to that network instead.\n"
          + "  2. Set NODE_EXTRA_CA_CERTS as described at https://bit.ly/3ghJqJZ (RECOMMENDED).\n"
          + "  3. Disable certificate validation as described at https://bit.ly/3syjzm7 (NOT RECOMMENDED).\n";

  private static final Pattern CURRENT_VERSION = Pattern.compile("^current(\\$.+)?$");
  private static final Pattern VIRTUAL_PATH = Pattern.compile("^virtual:([^:]+):(.*)$", Pattern.DOTALL);
  private static final Pattern CERTIFICATE = Pattern.compile("certificate");

  protected final PackageDB packageDB;
  protected final PackageCache packageCache;
  protected final RegistryClient registryClient;
  protected final CurrentBuildClient currentBuildClient;
  protected BiConsumer<String, String> log;
  private final Map<String, VirtualPackage> virtualPackages = new HashMap<>();
  private final Map<String, Object> resourceCache;

  public BasePackageLoader(
      PackageDB packageDB, PackageCache packageCache, RegistryClient registryClient,
      CurrentBuildClient currentBuildClient, BiConsumer<String, String> log, int resourceCacheSize) {
    this.packageDB = packageDB;
    this.packageCache = packageCache;
    this.registryClient = registryClient;
    this.currentBuildClient = currentBuildClient;
    this.log = log != null ? log : (l, m) -> {};
    this.resourceCache = resourceCacheSize > 0 ? lru(resourceCacheSize) : null;
  }

  private static Map<String, Object> lru(int size) {
    // mnemonist LRUCache semantics: get() refreshes recency; set() evicts the least recently used when full
    return new LinkedHashMap<>(16, 0.75f, true) {
      private static final long serialVersionUID = 1L;

      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
        return size() > size;
      }
    };
  }

  /**
   * Loads a package (resolving {@code latest}/{@code x.y.x}, downloading when needed). Exceptions from version
   * resolution propagate (TS: the promise rejects); the caller logs {@code e.getMessage()}.
   */
  public LoadStatus loadPackage(String name, String version) {
    String packageLabel = name + "#" + version;
    String originalVersion = version;
    try {
      version = registryClient.resolveVersion(name, version);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    if (!java.util.Objects.equals(version, originalVersion)) {
      log.accept("info", "Resolved " + packageLabel + " to concrete version " + version);
      packageLabel = name + "#" + version;
    }

    // If it's already loaded, then there's nothing to do
    if (getPackageLoadStatus(name, version) == LoadStatus.LOADED) {
      log.accept("info", packageLabel + " already loaded");
      return LoadStatus.LOADED;
    }

    // If it's a "dev" version, but it wasn't found in the cache, fall back to using the current version
    if ("dev".equals(version) && !packageCache.isPackageInCache(name, version)) {
      log.accept("info", "Falling back to " + name + "#current since " + packageLabel + " is not locally cached. To "
          + "avoid this, add " + packageLabel + " to your local FHIR cache by building it locally with the HL7 FHIR "
          + "IG Publisher.");
      version = "current";
      packageLabel = name + "#" + version;
    }

    String downloadErrorMessage = null;
    // If it's a "current" version, download the latest version from the build server (if applicable)
    if (isCurrentVersion(version)) {
      String branch = version.indexOf('$') != -1 ? version.split("\\$", -1)[1] : null;
      if (packageCache instanceof CurrentRefresher refresher) {
        try {
          refresher.refreshCurrent(name, version);
        } catch (Exception e) {
          downloadErrorMessage = "Failed to download most recent " + packageLabel + " from current builds";
          if (e.getMessage() != null && CERTIFICATE.matcher(e.getMessage()).find()) {
            downloadErrorMessage += CERTIFICATE_MESSAGE;
          }
        }
      } else if (isCurrentVersionMissingOrStale(name, branch)) {
        try {
          InputStream tarballStream = currentBuildClient.downloadCurrentBuild(name, branch);
          packageCache.cachePackageTarball(name, version, tarballStream);
        } catch (Exception e) {
          downloadErrorMessage = "Failed to download most recent " + packageLabel + " from current builds";
          if (e.getMessage() != null && CERTIFICATE.matcher(e.getMessage()).find()) {
            downloadErrorMessage += CERTIFICATE_MESSAGE;
          }
        }
      }
    }
    // Else if it isn't "current" and isn't in the cache, download it from the registry
    else if (!packageCache.isPackageInCache(name, version)) {
      try {
        InputStream tarballStream = registryClient.download(name, version);
        packageCache.cachePackageTarball(name, version, tarballStream);
      } catch (Exception e) {
        downloadErrorMessage = "Failed to download " + packageLabel + " from the registry";
        if (e.getMessage() != null && CERTIFICATE.matcher(e.getMessage()).find()) {
          downloadErrorMessage += CERTIFICATE_MESSAGE;
        }
      }
    }

    // Finally attempt to load it from the cache
    PackageStats stats;
    try {
      stats = loadPackageFromCache(name, version);
    } catch (Exception e) {
      log.accept("error", "Failed to load " + packageLabel
          + (downloadErrorMessage != null ? ": " + downloadErrorMessage : ""));
      return LoadStatus.FAILED;
    }
    if (downloadErrorMessage != null) {
      // Loading succeeded despite a download error. This might happen if a current build is stale,
      // but the download fails, in which case the stale build will be loaded instead.
      log.accept("warn", downloadErrorMessage + ". Using most recent cached package instead.");
    }
    log.accept("info", "Loaded " + stats.name() + "#" + stats.version() + " with " + stats.resourceCount()
        + " resources");
    return LoadStatus.LOADED;
  }

  public LoadStatus loadVirtualPackage(VirtualPackage pkg) {
    // Ensure package.json has at least the name and version
    Map<String, Object> packageJSON = pkg.getPackageJSON();
    Object pName = packageJSON == null ? null : packageJSON.get("name");
    Object pVersion = packageJSON == null ? null : packageJSON.get("version");
    if (pName == null || Js.trim(Js.toStr(pName)).isEmpty() || pVersion == null
        || Js.trim(Js.toStr(pVersion)).isEmpty()) {
      log.accept("error", "Failed to load virtual package " + (pName != null ? Js.toStr(pName) : "<unknown>") + "#"
          + (pVersion != null ? Js.toStr(pVersion) : "<unknown>")
          + " because the provided packageJSON did not have a valid name and/or version");
      return LoadStatus.FAILED;
    }
    String name = Js.toStr(pName), version = Js.toStr(pVersion);

    // If it's already loaded, then there's nothing to do
    if (getPackageLoadStatus(name, version) == LoadStatus.LOADED) {
      log.accept("info", "Virtual package " + name + "#" + version + " already loaded");
      return LoadStatus.LOADED;
    }

    // Store the virtual package by its name#version key
    String packageKey = name + "#" + version;
    virtualPackages.put(packageKey, pkg);

    // Save the package info
    packageDB.savePackageInfo(new PackageInfo(name, version, "virtual:" + packageKey,
        "virtual:" + packageKey + ":package.json"));

    // Register the resources
    try {
      pkg.registerResources((key, resource, allowNonResources) ->
          loadResource("virtual:" + packageKey + ":" + key, resource, name, version, allowNonResources));
    } catch (Exception e) {
      log.accept("error", "Virtual package " + packageKey + " threw an exception while registering resources, so it "
          + "was only partially loaded.");
      log.accept("debug", stack(e));
      return LoadStatus.FAILED;
    }

    PackageStats stats = packageDB.getPackageStats(name, version);
    log.accept("info", "Loaded virtual package " + packageKey + " with " + stats.resourceCount() + " resources");
    return LoadStatus.LOADED;
  }

  private PackageStats loadPackageFromCache(String name, String version) {
    // Ensure the package is cached
    if (!packageCache.isPackageInCache(name, version)) {
      throw new IllegalStateException(name + "#" + version + " cannot be loaded from the package cache");
    }
    // Get the cached package path and package.json path
    String packagePath = packageCache.getPackagePath(name, version);
    String packageJSONPath = packageCache.getPackageJSONPath(name, version);
    PackageInfo info = new PackageInfo(name, version, resolvePath(packagePath), resolvePath(packageJSONPath));
    packageDB.savePackageInfo(info);
    // Load the resources within the package
    loadResourcesFromCache(name, version);
    return packageDB.getPackageStats(name, version);
  }

  private static String resolvePath(String p) {
    // path.resolve(undefined) throws in node; FPL only reaches here with paths from the cache
    return p == null ? null : Path.of(p).toAbsolutePath().normalize().toString();
  }

  private void loadResourcesFromCache(String packageName, String packageVersion) {
    for (String resourcePath : packageCache.getPotentialResourcePaths(packageName, packageVersion)) {
      try {
        Object resourceJSON = packageCache.getResourceAtPath(resourcePath);
        loadResource(resourcePath, resourceJSON, packageName, packageVersion, false);
      } catch (Exception e) {
        // swallow this error because some JSON files will not be resources
        // and don't log it if it is package.json (since every package should have one)
        if (!DiskBasedVirtualPackage.basename(resourcePath).equals("package.json")) {
          log.accept("debug", "JSON file at path " + resourcePath + " was not FHIR resource");
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  protected void loadResource(String resourcePath, Object resource, String packageName, String packageVersion,
      boolean allowNonResources) {
    if (!(resource instanceof Map<?, ?>)) {
      // TS: resourceJSON.resourceType on a non-object; null/undefined throw a TypeError, primitives have no property
      if (resource == null) {
        throw new IllegalStateException("Cannot read properties of " + resource + " (reading 'resourceType')");
      }
      if (allowNonResources) {
        resource = Map.of();
      } else {
        throw new InvalidResourceError(resourcePath, "resource does not specify its resourceType");
      }
    }
    Map<String, Object> json = (Map<String, Object>) resource;
    // We require at least a resourceType in order to know it is FHIR
    Object rt = json.get("resourceType");
    String resourceType;
    if (!(rt instanceof String s) || s.isEmpty()) {
      if (allowNonResources) {
        // SUSHI needs to support registering instances of logical models, but some code expects resourceType
        resourceType = "Unknown";
      } else {
        throw new InvalidResourceError(resourcePath, "resource does not specify its resourceType");
      }
    } else {
      resourceType = s;
    }
    ResourceInfo info = new ResourceInfo();
    info.resourceType = resourceType;
    if (json.get("id") instanceof String s) info.id = s;
    if (json.get("url") instanceof String s) info.url = s;
    if (json.get("name") instanceof String s) info.name = s;
    if (json.get("version") instanceof String s) info.version = s;
    if (resourceType.equals("StructureDefinition")) {
      if (json.get("kind") instanceof String s) info.sdKind = s;
      // In R4, some things don't have derivation (e.g. Resource) so default to specialization
      info.sdDerivation = json.get("derivation") instanceof String s ? s : "specialization";
      if (json.get("type") instanceof String s) info.sdType = s;
      if (json.get("baseDefinition") instanceof String s) info.sdBaseDefinition = s;
      info.sdAbstract = Boolean.TRUE.equals(json.get("abstract"));
      List<Object> imposeProfiles = new ArrayList<>();
      List<Object> characteristics = new ArrayList<>();
      Object exts = json.get("extension");
      if (exts instanceof List<?> extList) {
        for (Object e : extList) {
          if (!(e instanceof Map<?, ?> ext)) continue;
          Object url = ext.get("url");
          if ("http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile".equals(url)) {
            imposeProfiles.add(ext.get("valueCanonical"));
          } else if ("http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics".equals(url)) {
            characteristics.add(ext.get("valueCode"));
          } else if ("http://hl7.org/fhir/tools/StructureDefinition/logical-target".equals(url)
              && Js.truthy(ext.get("valueBoolean"))) {
            // logical-target is a temporary alternate representation for can-be-target because
            // FHIR missed can-be-target in early versions of its characteristics value set
            characteristics.add("can-be-target");
          }
        }
      } else if (exts != null && !(exts instanceof Map<?, ?>) && !(exts instanceof String)) {
        // (?.forEach on a non-array object throws a TypeError in TS)
      }
      if (!imposeProfiles.isEmpty()) info.sdImposeProfiles = imposeProfiles;
      if (!characteristics.isEmpty()) info.sdCharacteristics = characteristics;
      info.sdFlavor = getSDFlavor(json);
    }
    if (packageName != null && !packageName.isEmpty()) info.packageName = packageName;
    if (packageVersion != null && !packageVersion.isEmpty()) info.packageVersion = packageVersion;
    info.resourcePath = resourcePath;
    packageDB.saveResourceInfo(info);
  }

  private boolean isCurrentVersionMissingOrStale(String name, String branch) {
    boolean isStale = true;
    String version = branch != null ? "current$" + branch : "current";
    String packageJSONPath = packageCache.getPackageJSONPath(name, version);
    if (packageJSONPath != null) {
      try {
        Object packageJSON = packageCache.getResourceAtPath(packageJSONPath);
        Object cachedPackageDate = packageJSON instanceof Map<?, ?> m ? m.get("date") : null;
        if (Js.truthy(cachedPackageDate)) {
          String packageLabel = name + "#" + version;
          String latestBuildDate = currentBuildClient.getCurrentBuildDate(name, branch);
          isStale = !cachedPackageDate.equals(latestBuildDate);
          if (isStale) {
            log.accept("debug", "Cached package date for " + packageLabel + " (" + formatDate(Js.toStr(cachedPackageDate))
                + ") does not match last build date (" + formatDate(latestBuildDate) + ")");
            log.accept("info", "Cached package " + packageLabel + " is out of date and will be replaced by the most "
                + "recent current build.");
          } else {
            log.accept("debug", "Cached package date for " + packageLabel + " (" + formatDate(Js.toStr(cachedPackageDate))
                + ") matches last build date (" + formatDate(latestBuildDate) + "), so the cached package will be "
                + "used");
          }
        }
      } catch (Exception e) {
        // do nothing -- will fall back to stale if we couldn't determine staleness
      }
    }
    return isStale;
  }

  public Object getResourceAtPath(String resourcePath) {
    Object resource = resourceCache != null ? resourceCache.get(resourcePath) : null;
    if (resource == null) {
      Matcher m = VIRTUAL_PATH.matcher(resourcePath);
      if (resourcePath.startsWith("virtual:")) {
        if (m.matches()) {
          String packageKey = m.group(1), resourceKey = m.group(2);
          if (!packageKey.isEmpty() && !resourceKey.isEmpty()) {
            VirtualPackage pkg = virtualPackages.get(packageKey);
            resource = pkg == null ? null
                : resourceKey.equals("package.json") ? pkg.getPackageJSON() : pkg.getResourceByKey(resourceKey);
          }
        }
      } else {
        resource = packageCache.getResourceAtPath(resourcePath);
      }
      if (resourceCache != null) resourceCache.put(resourcePath, resource);
    }
    return resource;
  }

  public LoadStatus getPackageLoadStatus(String name, String version) {
    return packageDB.findPackageInfo(name, version) != null ? LoadStatus.LOADED : LoadStatus.NOT_LOADED;
  }

  public List<PackageInfo> findPackageInfos(String name) {
    return packageDB.findPackageInfos(name);
  }

  public PackageInfo findPackageInfo(String name, String version) {
    return packageDB.findPackageInfo(name, version);
  }

  public List<Object> findPackageJSONs(String name) {
    List<Object> res = new ArrayList<>();
    for (PackageInfo info : findPackageInfos(name)) {
      if (info.packageJSONPath != null) res.add(getResourceAtPath(info.packageJSONPath));
    }
    return res;
  }

  public Object findPackageJSON(String name, String version) {
    PackageInfo info = findPackageInfo(name, version);
    return info != null && info.packageJSONPath != null ? getResourceAtPath(info.packageJSONPath) : null;
  }

  public List<ResourceInfo> findResourceInfos(String key, FindResourceInfoOptions options) {
    return packageDB.findResourceInfos(key, options);
  }

  public ResourceInfo findResourceInfo(String key, FindResourceInfoOptions options) {
    return packageDB.findResourceInfo(key, options);
  }

  public List<Object> findResourceJSONs(String key, FindResourceInfoOptions options) {
    List<Object> res = new ArrayList<>();
    for (ResourceInfo info : findResourceInfos(key, options)) {
      if (info.resourcePath != null) res.add(getResourceAtPath(info.resourcePath));
    }
    return res;
  }

  public Object findResourceJSON(String key, FindResourceInfoOptions options) {
    ResourceInfo info = findResourceInfo(key, options);
    return info != null && info.resourcePath != null ? getResourceAtPath(info.resourcePath) : null;
  }

  public void optimize() {
    packageDB.optimize();
  }

  public void clear() {
    packageDB.clear();
  }

  static boolean isCurrentVersion(String version) {
    return version != null && CURRENT_VERSION.matcher(version).matches();
  }

  /** Takes a date in format YYYYMMDDHHmmss and converts to YYYY-MM-DDTHH:mm:ss */
  static String formatDate(String date) {
    return date != null && !date.isEmpty()
        ? date.replaceFirst("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})", "$1-$2-$3T$4:$5:$6")
        : "";
  }

  static String getSDFlavor(Map<String, Object> json) {
    if ("StructureDefinition".equals(json.get("resourceType"))) {
      Object type = json.get("type"), kind = json.get("kind");
      if ("Extension".equals(type)
          && !"http://hl7.org/fhir/StructureDefinition/Element".equals(json.get("baseDefinition"))) {
        return "Extension";
      } else if ("constraint".equals(json.get("derivation"))) {
        return "Profile";
      } else if (Js.toStr(kind).contains("type")) { // /type/.test(kind): String(kind)
        return "Type";
      } else if ("resource".equals(kind)) {
        return "Resource";
      } else if ("logical".equals(kind)) {
        return "Logical";
      }
    }
    return null;
  }

  /** e.stack equivalent for debug logs (Java's trace, not V8's) */
  static String stack(Throwable e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString().stripTrailing();
  }
}
