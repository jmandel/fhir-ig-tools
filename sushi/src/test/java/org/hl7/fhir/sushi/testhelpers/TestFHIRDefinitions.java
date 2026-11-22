package org.hl7.fhir.sushi.testhelpers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirdefs.DiskBasedVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.InMemoryVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.PackageProvider;
import org.hl7.fhir.sushi.fhirdefs.R5DefsForR4;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.utils.FshLogger;

// port of sushi/test/testhelpers/TestFHIRDefinitions.ts (SUSHI 3.21.1)
/**
 * FHIRDefinitions with the package provider replaced by a fake (no network, no real FHIR cache), as the jest mocks
 * in TS. Definitions come from local folders via virtual packages
 * ({@link #getTestFHIRDefinitions}, {@link #loadLocalPaths}); the TS fixtures are read in place from the SUSHI
 * checkout ({@code -Dsushi.ts.dir}, see {@link #testDefsPath}).
 */
public class TestFHIRDefinitions extends FHIRDefinitions {
  private static int VP_COUNTER = 0;

  public static final BiConsumer<String, String> LOG_MESSAGE = (l, m) -> FshLogger.logger.logMessage(l, m);

  public final FakePackageProvider packageProviderMock;

  public TestFHIRDefinitions() {
    this(new FakePackageProvider());
  }

  private TestFHIRDefinitions(FakePackageProvider pp) {
    super(override(pp));
    this.packageProviderMock = pp;
  }

  private static Override override(PackageProvider pp) {
    Override o = new Override();
    o.packageProvider = pp;
    return o;
  }

  public void loadLocalPaths(String... localPaths) {
    for (DiskBasedVirtualPackage vp : getLocalVirtualPackages(localPaths)) {
      loadVirtualPackage(vp);
    }
  }

  public void loadCustomResources(String... paths) {
    loadVirtualPackage(new DiskBasedVirtualPackage(
        InMemoryVirtualPackage.packageJSON(PREDEFINED_PACKAGE_NAME, PREDEFINED_PACKAGE_VERSION), List.of(paths),
        LOG_MESSAGE, true, true));
  }

  public static TestFHIRDefinitions getTestFHIRDefinitions() {
    return getTestFHIRDefinitions(false);
  }

  public static TestFHIRDefinitions getTestFHIRDefinitions(boolean includeR5forR4, String... localPaths) {
    TestFHIRDefinitions defs = new TestFHIRDefinitions();
    defs.initialize();
    if (includeR5forR4) {
      // This mirrors what happens in Processing.ts for R4 and R4B
      Map<String, Object> r5forR4Map = new LinkedHashMap<>();
      R5DefsForR4.R5_DEFINITIONS_NEEDED_IN_R4.forEach(def -> r5forR4Map.put((String) def.get("id"), def));
      defs.loadVirtualPackage(new InMemoryVirtualPackage(
          InMemoryVirtualPackage.packageJSON("sushi-r5forR4", "1.0.0"), r5forR4Map, LOG_MESSAGE, true));
    }
    // Then load the specifically requested resource paths
    if (localPaths.length > 0) {
      defs.loadLocalPaths(localPaths);
    }
    return defs;
  }

  @SuppressWarnings("unchecked")
  public static DiskBasedVirtualPackage getLocalVirtualPackage(String localPath) {
    Map<String, Object> packageJSON;
    Path p1 = Path.of(localPath, "package.json"), p2 = Path.of(localPath, "package", "package.json");
    try {
      if (Files.exists(p1)) {
        packageJSON = (Map<String, Object>) Json.parseFile(Files.readString(p1));
      } else if (Files.exists(p2)) {
        packageJSON = (Map<String, Object>) Json.parseFile(Files.readString(p2));
      } else {
        packageJSON = InMemoryVirtualPackage.packageJSON("sushi-test-" + (++VP_COUNTER), "0.0.1");
      }
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
    return new DiskBasedVirtualPackage(packageJSON, List.of(localPath), LOG_MESSAGE, true, true);
  }

  public static List<DiskBasedVirtualPackage> getLocalVirtualPackages(String... localPaths) {
    List<DiskBasedVirtualPackage> virtualPackages = new ArrayList<>();
    List<String> leftoverPaths = new ArrayList<>();
    for (String p : localPaths) {
      if (Files.exists(Path.of(p, "package.json")) || Files.exists(Path.of(p, "package", "package.json"))) {
        virtualPackages.add(getLocalVirtualPackage(p));
      } else {
        leftoverPaths.add(p);
      }
    }
    if (!leftoverPaths.isEmpty()) {
      // NOTE: as in TS, the combined package gets ALL localPaths (not just the leftovers)
      virtualPackages.add(new DiskBasedVirtualPackage(
          InMemoryVirtualPackage.packageJSON("sushi-test-" + (++VP_COUNTER), "0.0.1"), List.of(localPaths),
          LOG_MESSAGE, true, true));
    }
    return virtualPackages;
  }

  /** SUSHI checkout with tests and fixtures (default: the VM copy). */
  public static String tsDir() {
    String d = System.getProperty("sushi.ts.dir");
    return d == null || d.isEmpty() ? "/srv/ig/sushi-spike/sushi" : d;
  }

  /** TS testDefsPath(...): test/testhelpers/testdefs/... in the SUSHI checkout */
  public static String testDefsPath(String... subpathPart) {
    return Path.of(Path.of(tsDir(), "test", "testhelpers", "testdefs").toString(), subpathPart).toString();
  }

  /** true when the TS checkout (fixtures) is available; tests use it with Assumptions.assumeTrue */
  public static boolean tsFixturesAvailable() {
    return Files.isDirectory(Path.of(testDefsPath()));
  }

  // ---------------------------------------------------------------------------------------------- fake (jest mocks)

  /**
   * The TS test mocks of PackageCache/RegistryClient/CurrentBuildClient folded into one provider: latest -> 9.9.9,
   * x.y.x -> x.y.9, every package "downloads" and loads with no resources and a {name, version} package.json.
   */
  public static class FakePackageProvider implements PackageProvider {
    public final List<String> cachedPackages = new ArrayList<>();
    /** name#version of every loadPackage call (TS: registryClientMock.download / cachePackageTarball calls) */
    public final List<String> loadCalls = new ArrayList<>();
    /** set to make resolution fail (jest mockRejectedValue on resolveVersion) */
    public RuntimeException resolveError;
    /** set to make loading fail for every package (download/cache failure) */
    public RuntimeException loadError;
    private static final Pattern PJ = Pattern.compile("/mock/path/to/([^#]+)#([^/]+)/package/package.json");

    @java.lang.Override
    public String resolveLatest(String name) {
      if (resolveError != null) throw resolveError;
      return "9.9.9";
    }

    @java.lang.Override
    public boolean isCached(String name, String version) {
      return cachedPackages.contains(name + "#" + version);
    }

    @java.lang.Override
    public CachedPackage loadPackage(String name, String version) {
      if (version.endsWith(".x")) {
        if (resolveError != null) throw resolveError;
        version = version.replaceFirst("\\.x", ".9");
      }
      loadCalls.add(name + "#" + version);
      if (loadError != null) throw loadError;
      if (!cachedPackages.contains(name + "#" + version)) cachedPackages.add(name + "#" + version);
      String path = "/mock/path/to/" + name + "#" + version;
      return new CachedPackage(name, version, path, path + "/package/package.json", List.of());
    }

    @java.lang.Override
    public Object readResource(String resourcePath) {
      Matcher m = PJ.matcher(resourcePath);
      if (m.find()) {
        return InMemoryVirtualPackage.packageJSON(m.group(1), m.group(2));
      }
      return null;
    }
  }
}
