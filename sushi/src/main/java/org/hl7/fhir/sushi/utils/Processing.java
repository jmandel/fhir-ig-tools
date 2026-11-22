package org.hl7.fhir.sushi.utils;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.InMemoryVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.LoadStatus;
import org.hl7.fhir.sushi.fhirdefs.R5DefsForR4;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;

// port of sushi/src/utils/Processing.ts (SUSHI 3.21.1) -- WP-8 part: dependency loading
// (loadExternalDependencies, loadAutomaticDependencies, fixCrossVersionDependencies, AUTOMATIC_DEPENDENCIES).
// The rest of Processing.ts is WP-10's and is added to this class.
public final class Processing {
  private Processing() {}

  public enum AutomaticDependencyPriority {
    /** load before configured dependencies / FHIR core (lowest resolution priority) */
    Low,
    /** load after configured dependencies / FHIR core (highest resolution priority) */
    High
  }

  /** fhirVersions: FHIRVersionName[] or null */
  public record AutomaticDependency(String packageId, String version, List<String> fhirVersions,
      AutomaticDependencyPriority priority) {
    /** the TS object literal (key order as declared), for lodash isEqual/uniqWith */
    JsObject toJs() {
      JsObject o = new JsObject();
      o.put("packageId", packageId);
      o.put("version", version);
      if (fhirVersions != null) o.put("fhirVersions", new ArrayList<Object>(fhirVersions));
      o.put("priority", priority.name());
      return o;
    }
  }

  // For some context on implicit packages, see:
  // https://chat.fhir.org/#narrow/stream/179239-tooling/topic/New.20Implicit.20Package/near/325318949
  public static final List<AutomaticDependency> AUTOMATIC_DEPENDENCIES = List.of(
      new AutomaticDependency("hl7.fhir.uv.tools.r4", "latest", List.of("R4", "R4B"), AutomaticDependencyPriority.Low),
      new AutomaticDependency("hl7.fhir.uv.tools.r5", "latest", List.of("R5", "R6"), AutomaticDependencyPriority.Low),
      new AutomaticDependency("hl7.terminology.r4", "latest", List.of("R4", "R4B"), AutomaticDependencyPriority.Low),
      new AutomaticDependency("hl7.terminology.r5", "latest", List.of("R5", "R6"), AutomaticDependencyPriority.Low),
      new AutomaticDependency("hl7.fhir.uv.extensions.r4", "latest", List.of("R4", "R4B"),
          AutomaticDependencyPriority.High),
      new AutomaticDependency("hl7.fhir.uv.extensions.r5", "latest", List.of("R5", "R6"),
          AutomaticDependencyPriority.High));

  private static final Pattern R_SUFFIX = Pattern.compile("\\.r[4-9]\\z");

  private static boolean configuredDependencyMatchesAutomaticDependency(Map<String, Object> cd, String adPackageId) {
    // hl7.some.package, hl7.some.package.r4, and hl7.some.package.r5 all represent the same content,
    // so they are essentially interchangeable and we should allow for any of them in the config.
    // See: https://chat.fhir.org/#narrow/stream/179239-tooling/topic/New.20Implicit.20Package/near/325488084
    String configRootId = rootId(str(cd.get("packageId")));
    String packageRootId = rootId(adPackageId);
    return java.util.Objects.equals(configRootId, packageRootId);
  }

  private static String rootId(String id) {
    if (id == null) return null;
    return R_SUFFIX.matcher(id).find() ? id.substring(0, id.length() - 3) : id;
  }

  private static String str(Object o) {
    return o == null || o == Js.UNDEFINED ? null : Js.toStr(o);
  }

  private static final Pattern NPM_ALIAS = Pattern.compile("^([^@]+)@npm:(.+)\\z", Pattern.DOTALL);
  private static final Pattern ALIAS_CHARS = Pattern.compile("^[A-Za-z0-9\\-\\.]+\\z");

  /** TS loadExternalDependencies(defs, config) (async there). */
  public static void loadExternalDependencies(FHIRDefinitions defs, Configuration config) {
    // Check for special npm alias syntax and fix the packageIds as necessary, sorting dependencies with
    // the same package id so the latest version is last (due to FPL's last in first out resolution strategy)
    // See: https://chat.fhir.org/#narrow/channel/179239-tooling/topic/NPM.20Aliases/near/517985527

    // First collect all the packages in a map, grouping multiple versions of the same package together
    Map<String, List<Map<String, Object>>> packageIdMap = new LinkedHashMap<>();
    List<JsObject> configured = config.dependencies != null ? config.dependencies : List.of();
    for (Map<String, Object> dep : configured) {
      String packageId = str(dep.get("packageId"));
      Matcher npmAliasMatcher = packageId == null ? null : NPM_ALIAS.matcher(packageId);
      if (npmAliasMatcher != null && npmAliasMatcher.find()) {
        String alias = npmAliasMatcher.group(1);
        String realId = npmAliasMatcher.group(2);
        if (!ALIAS_CHARS.matcher(alias).find()) {
          FshLogger.logger.warn("NPM aliases should contain only the following characters: upper- or lower-case "
              + "ASCII letters ('A'..'Z', and 'a'..'z'), numerals ('0'..'9'), '-' and '.'. Found '" + alias + "'.");
        }
        JsObject copy = new JsObject(dep);
        copy.put("packageId", realId);
        dep = copy;
      }
      packageIdMap.computeIfAbsent(str(dep.get("packageId")), k -> new ArrayList<>()).add(dep);
    }

    // Then iterate the map in insertion order, preserving original order except packages with multiple versions
    List<Map<String, Object>> dependencies = new ArrayList<>();
    packageIdMap.forEach((id, dependsOns) -> {
      if (dependsOns.size() > 1) {
        dependsOns.sort((a, b) -> Semver.compareLoose(str(a.get("version")), str(b.get("version"))));
      }
      dependencies.addAll(dependsOns);
    });

    // Add FHIR to the dependencies so it is loaded
    FHIRVersionUtils.FHIRVersionInfo fhirVersionInfo = null;
    for (String v : config.fhirVersion) {
      FHIRVersionUtils.FHIRVersionInfo info = FHIRVersionUtils.getFHIRVersionInfo(v);
      if (info.isSupported()) {
        fhirVersionInfo = info;
        break;
      }
    }
    if (fhirVersionInfo == null) {
      throw new NullPointerException("Cannot read properties of undefined (reading 'isPreRelease')");
    }
    if (fhirVersionInfo.isPreRelease()) {
      FshLogger.logger.warn("SUSHI support for pre-release versions of FHIR is experimental. Use at your own risk!");
    }
    JsObject fhirDep = new JsObject();
    fhirDep.put("packageId", fhirVersionInfo.packageId());
    fhirDep.put("version", fhirVersionInfo.version());
    dependencies.add(fhirDep);

    // First load automatic dependencies with the lowest priority (before configured dependencies and FHIR core)
    loadAutomaticDependencies(fhirVersionInfo.version(), dependencies, defs, AutomaticDependencyPriority.Low);

    // Then load configured dependencies and FHIR core (FHIR core is last so it has higher priority in resolution)
    loadConfiguredDependencies(dependencies, fhirVersionInfo.version(), config.filePath, defs);

    // Then load automatic dependencies with highest priority (taking precedence over even FHIR core)
    // See: https://chat.fhir.org/#narrow/channel/179239-tooling/topic/New.20Implicit.20Package/near/562477575
    loadAutomaticDependencies(fhirVersionInfo.version(), dependencies, defs, AutomaticDependencyPriority.High);
  }

  public static void loadAutomaticDependencies(String fhirVersion, List<? extends Map<String, Object>> configuredDependencies,
      FHIRDefinitions defs, AutomaticDependencyPriority priority) {
    String fhirVersionName = FHIRVersionUtils.getFHIRVersionInfo(fhirVersion).name();

    if (priority == AutomaticDependencyPriority.Low
        && (fhirVersionName.equals("R4") || fhirVersionName.equals("R4B"))) {
      // There are several R5 resources that are allowed for use in R4 and R4B.
      // Add them first so they're always available (but are lower priority than
      // any other version loaded from an official package).
      Map<String, Object> r5forR4Map = new LinkedHashMap<>();
      R5DefsForR4.R5_DEFINITIONS_NEEDED_IN_R4.forEach(def -> r5forR4Map.put(str(def.get("id")), def));
      InMemoryVirtualPackage virtualR5forR4Package = new InMemoryVirtualPackage(
          InMemoryVirtualPackage.packageJSON("sushi-r5forR4", "1.0.0"), r5forR4Map,
          (level, message) -> FshLogger.logger.logMessage(level, message), false);
      defs.loadVirtualPackage(virtualR5forR4Package);
    }

    // Gather all automatic dependencies matching this priority, substituting matching configured dependencies
    List<Map<String, Object>> gathered = new ArrayList<>();
    for (AutomaticDependency autoDep : AUTOMATIC_DEPENDENCIES) {
      if (autoDep.priority() != priority) continue;
      List<Map<String, Object>> configuredDeps = new ArrayList<>();
      for (Map<String, Object> cd : configuredDependencies) {
        if (configuredDependencyMatchesAutomaticDependency(cd, autoDep.packageId())) configuredDeps.add(cd);
      }
      if (!configuredDeps.isEmpty()) {
        // Prefer configured dependencies over automatic dependencies
        gathered.addAll(configuredDeps);
      } else if (autoDep.fhirVersions() != null && !autoDep.fhirVersions().contains(fhirVersionName)) {
        // Skip automatic dependencies not intended for this version of FHIR
      } else {
        gathered.add(autoDep.toJs());
      }
    }
    // uniqWith(..., isEqual): keep the first of each group of deep-equal entries
    List<Map<String, Object>> automaticDependencies = new ArrayList<>();
    for (Map<String, Object> d : gathered) {
      if (automaticDependencies.stream().noneMatch(x -> Js.isEqual(x, d))) automaticDependencies.add(d);
    }

    // Load automatic dependencies serially so dependency loading order is predictable and repeatable
    for (Map<String, Object> dep : automaticDependencies) {
      String packageId = str(dep.get("packageId"));
      String version = str(dep.get("version"));
      boolean isUserConfigured = AUTOMATIC_DEPENDENCIES.stream()
          .noneMatch(ad -> ad.packageId().equals(packageId) && ad.version().equals(version));
      LoadStatus status;
      try {
        // Suppress error logs when loading non-configured automatic dependencies because many IGs can succeed
        // without them
        if (!isUserConfigured) {
          defs.setFHIRPackageLoaderLogInterceptor((level, message) -> !level.equals("error"));
        }
        status = defs.loadPackage(packageId, version);
      } catch (RuntimeException e) {
        // This shouldn't happen, but just in case
        status = LoadStatus.FAILED;
        FshLogger.logger.debug(stack(e));
      } finally {
        // Unset the log interceptor for non-configured automatic dependencies so it behaves normally after this
        if (!isUserConfigured) {
          defs.setFHIRPackageLoaderLogInterceptor(null);
        }
      }
      if (status != LoadStatus.LOADED && !isUserConfigured) {
        String message = "Failed to load automatically-provided " + packageId + "#" + version;
        String registry = System.getenv("FPL_REGISTRY");
        if (registry != null && !registry.isEmpty()) {
          message += " from custom FHIR package registry " + registry + ".";
        }
        FshLogger.logger.warn(message);
      }
    }
  }

  private static void loadConfiguredDependencies(List<Map<String, Object>> dependencies, String fhirVersion,
      String configPath, FHIRDefinitions defs) {
    List<Map<String, Object>> fixedDependencies = fixCrossVersionDependencies(dependencies, true);

    // Load dependencies serially so dependency loading order is predictable and repeatable
    for (Map<String, Object> dep : fixedDependencies) {
      String packageId = str(dep.get("packageId"));
      String version = str(dep.get("version"));
      if (version == null) {
        String uri = str(dep.get("uri"));
        FshLogger.logger.error("Failed to load " + packageId + ": No version specified. To specify the version in your "
            + basename(configPath) + ", either use the simple dependency format:\n\n"
            + "dependencies:\n"
            + "  " + packageId + ": current\n\n"
            + "or use the detailed dependency format to specify other properties as well:\n\n"
            + "dependencies:\n"
            + "  " + packageId + ":\n"
            + "    uri: " + (uri != null ? uri : "http://my-fhir-ig.org/ImplementationGuide/123") + "\n"
            + "    version: current");
        continue;
      } else if (AUTOMATIC_DEPENDENCIES.stream()
          .anyMatch(ad -> configuredDependencyMatchesAutomaticDependency(dep, ad.packageId()))) {
        // skip configured dependencies that override automatic dependencies; they will be loaded at the end
        continue;
      } else {
        try {
          defs.loadPackage(packageId, version);
        } catch (RuntimeException e) {
          FshLogger.logger.error("Failed to load " + packageId + "#" + version + ": " + e.getMessage());
          FshLogger.logger.debug(stack(e));
        }
      }
    }
  }

  private static final Pattern OLD_XVER = Pattern.compile("^hl7\\.fhir\\.extensions\\.r\\d+b?\\z");
  private static final Pattern OLD_XVER_SOURCE = Pattern.compile("\\.(r\\d+b?)\\z");

  /**
   * Replace references to old-style dependencies (e.g., hl7.fhir.extensions.r5#4.0.1) with the latest version of the
   * relevant official xversion extension package (e.g., hl7.fhir.uv.xver-r5.r4#latest). null in -> null out.
   */
  public static List<Map<String, Object>> fixCrossVersionDependencies(List<? extends Map<String, Object>> dependencies,
      boolean logWarnings) {
    if (dependencies == null) return null;
    List<Map<String, Object>> res = new ArrayList<>();
    for (Map<String, Object> dep : dependencies) {
      String packageId = str(dep.get("packageId"));
      if (packageId != null && OLD_XVER.matcher(packageId).find()) {
        Matcher sm = OLD_XVER_SOURCE.matcher(packageId);
        String source = sm.find() ? sm.group(1) : null;
        String depVersion = str(dep.get("version"));
        String name = FHIRVersionUtils.getFHIRVersionInfo(depVersion).name();
        String target = name == null ? null : name.replaceFirst("D?STU", "r").toLowerCase();
        Map<String, Object> xverDep = Js.cloneDeep(dep);
        if (source != null && target != null) {
          String xverId = "hl7.fhir.uv.xver-" + source + "." + target;
          xverDep.put("packageId", xverId);
          xverDep.put("version", "latest");
          xverDep.put("uri", "http://hl7.org/fhir/uv/xver/ImplementationGuide/" + xverId);
          if (logWarnings) {
            FshLogger.logger.warn("Found old-style cross-version extensions package in dependencies: " + packageId
                + ".\n"
                + "  SUSHI will use the official xver package instead: " + xverId + ".\n"
                + "  Please update your sushi-config.yaml file to reference the new xver package.\n"
                + "  See: https://confluence.hl7.org/spaces/FHIRI/pages/413256623/FAQs");
          }
        }
        res.add(xverDep);
      } else {
        res.add(dep);
      }
    }
    return res;
  }

  public static List<Map<String, Object>> fixCrossVersionDependencies(List<? extends Map<String, Object>> deps) {
    return fixCrossVersionDependencies(deps, true);
  }

  private static String basename(String p) {
    if (p == null) throw new NullPointerException("The \"path\" argument must be of type string. Received undefined");
    Path f = Path.of(p).getFileName();
    return f == null ? "" : f.toString();
  }

  private static String stack(Throwable e) {
    java.io.StringWriter sw = new java.io.StringWriter();
    e.printStackTrace(new java.io.PrintWriter(sw));
    return sw.toString().stripTrailing();
  }

  /** The subset of npm semver used here (compareLoose); WP-10 may move this to a shared semver equivalent. */
  static final class Semver {
    private Semver() {}

    // semver's LOOSE regex: [v=\s]*(\d+)\.(\d+)\.(\d+)(?:-?((?:\d+|\d*[a-zA-Z-][a-zA-Z0-9-]*)(?:\.(?:...))*))?
    //   (?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?
    private static final String NUM_LOOSE = "[0-9]+";
    private static final String PRE_ID_LOOSE = "(?:[0-9]+|[0-9]*[a-zA-Z-][a-zA-Z0-9-]*)";
    private static final Pattern LOOSE = Pattern.compile("^[v=\\s]*(" + NUM_LOOSE + ")\\.(" + NUM_LOOSE + ")\\.("
        + NUM_LOOSE + ")(?:-?(" + PRE_ID_LOOSE + "(?:\\." + PRE_ID_LOOSE + ")*))?(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?\\z");

    static int compareLoose(String a, String b) {
      String[][] pa = parse(a), pb = parse(b);
      for (int i = 0; i < 3; i++) {
        int c = new BigInteger(pa[0][i]).compareTo(new BigInteger(pb[0][i]));
        if (c != 0) return Integer.signum(c);
      }
      String[] ra = pa[1], rb = pb[1];
      if (ra.length > 0 && rb.length == 0) return -1;
      if (ra.length == 0 && rb.length > 0) return 1;
      for (int i = 0; ; i++) {
        if (i >= ra.length && i >= rb.length) return 0;
        if (i >= rb.length) return 1;
        if (i >= ra.length) return -1;
        boolean na = ra[i].matches("[0-9]+"), nb = rb[i].matches("[0-9]+");
        int c;
        if (na && nb) c = new BigInteger(ra[i]).compareTo(new BigInteger(rb[i]));
        else if (na) c = -1;
        else if (nb) c = 1;
        else c = ra[i].compareTo(rb[i]);
        if (c != 0) return Integer.signum(c);
      }
    }

    private static String[][] parse(String v) {
      String t = v == null ? null : Js.trim(v);
      Matcher m = t == null ? null : LOOSE.matcher(t);
      if (m == null || !m.find()) {
        throw new IllegalArgumentException("Invalid Version: " + v);
      }
      String[] main = {m.group(1), m.group(2), m.group(3)};
      String[] pre = m.group(4) == null ? new String[0] : m.group(4).split("\\.", -1);
      return new String[][] {main, pre};
    }
  }
}
