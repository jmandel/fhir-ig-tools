package org.hl7.fhir.sushi.utils;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.InMemoryVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.LoadStatus;
import org.hl7.fhir.sushi.fhirdefs.R5DefsForR4;
import org.hl7.fhir.sushi.fhirtypes.FhirResource;
import org.hl7.fhir.sushi.fhirtypes.PropertyHolder;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.FshStructure;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Mapping;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.importer.ImportText;
import org.hl7.fhir.sushi.importer.RawFSH;
import org.hl7.fhir.sushi.importer.config.EnsureConfigurationFile;
import org.hl7.fhir.sushi.importer.config.ImportConfiguration;
import org.hl7.fhir.sushi.importer.config.LoadConfigurationFromIgResource;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.utilities.http.HTTPResult;
import org.hl7.fhir.utilities.http.ManagedWebAccess;
import org.hl7.fhir.common.js.JsObject;

// port of sushi/src/utils/Processing.ts (SUSHI 3.21.1) -- WP-8 part: dependency loading
// (loadExternalDependencies, loadAutomaticDependencies, fixCrossVersionDependencies, AUTOMATIC_DEPENDENCIES).
// WP-10 part (below the marker): the rest of Processing.ts.
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

  // ============================================================================================ WP-10 part
  // port of the rest of sushi/src/utils/Processing.ts (SUSHI 3.21.1): input/output folders, configuration, raw FSH,
  // writing fsh-generated (resources, fsh-index.txt/json), preprocessed FSH, SUSHI version checks, init.
  // Plus the fs-extra helpers every output file goes through (UTF-8, "\n", exactly the bytes TS writes).

  /** The SUSHI version this port reproduces (TS reads it from package.json). */
  public static final String SUSHI_VERSION = "3.21.1";

  /**
   * TS {@code throw Error} (the Error constructor itself, thrown by readConfig/getRawFSHes after logging): app.ts
   * then logs {@code e.message ?? e}, i.e. "function Error() { [native code] }", only when no error was counted.
   */
  public static final class ThrownErrorConstructor extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ThrownErrorConstructor() {
      super(null, null, false, false);
    }

    @Override public String toString() {
      return "function Error() { [native code] }";
    }
  }

  private static final Pattern SUPPORTED_FHIR_VERSION = Pattern.compile("^(current|[456]\\.\\d+.\\d+(-.+)?)\\z");

  public static boolean isSupportedFHIRVersion(String version) {
    // For now, allow current or any 4.x/5.x/6.x version of FHIR except 4.0.0. This is a quick check; not a guarantee.
    // If a user passes in an invalid version that passes this test (e.g., 4.99.0), it is still expected to fail when
    // we load dependencies.
    String v = version == null ? "undefined" : version;
    return !"4.0.0".equals(v) && SUPPORTED_FHIR_VERSION.matcher(v).find();
  }

  public static String ensureInputDir(String input) {
    // If no input folder is specified, set default to current directory
    if (input == null || input.isEmpty()) {
      input = ".";
      FshLogger.logger.info("path-to-fsh-defs defaulted to current working directory");
    }
    return input;
  }

  public static boolean hasFshFiles(String path) {
    if (!exists(path)) return false;
    for (String file : getFilesRecursive(path)) {
      if (file.endsWith(".fsh")) return true;
    }
    return false;
  }

  public static String findInputDir(String input) {
    String originalInput = input;

    String inputFshSubdirectoryPath = NodePath.join(originalInput, "input", "fsh");
    String fshSubdirectoryPath = NodePath.join(originalInput, "fsh");
    String rootIgDataPath = NodePath.join(originalInput, "ig-data");
    boolean currentTankWithNoFsh = !exists(inputFshSubdirectoryPath) && !exists(fshSubdirectoryPath)
        && !exists(rootIgDataPath) && !hasFshFiles(originalInput);

    // Use input/fsh/ subdirectory if not already specified and present
    // or when in the current tank configuration without FSH files
    if (exists(inputFshSubdirectoryPath) || currentTankWithNoFsh) {
      input = NodePath.join(originalInput, "input", "fsh");
    }

    // TODO (TS): Error about unsupported features. Remove when message no longer needed.
    // Use fsh/ subdirectory if not already specified and present
    if (!exists(inputFshSubdirectoryPath) && !currentTankWithNoFsh) {
      String sep = NodePath.SEP;
      String msg = "\n\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! IMPORTANT !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n";
      if (exists(fshSubdirectoryPath)) {
        msg += "\nSUSHI detected a \"fsh\" directory that will be used in the input path.\n"
            + "Use of this folder is NO LONGER SUPPORTED.\n"
            + "To migrate to the new folder structure, make the following changes:\n"
            + "  - move fsh" + sep + "config.yaml to ." + sep + "sushi-config.yaml\n"
            + "  - move fsh" + sep + "*.fsh files to ." + sep + "input" + sep + "fsh" + sep + "*.fsh\n";
        if (exists(NodePath.join(input, "fsh", "ig-data"))) {
          msg += "  - move fsh" + sep + "ig-data" + sep + "* files and folders to ." + sep + "*\n";
        }
      } else {
        msg += "\nSUSHI has adopted a new folder structure for FSH tanks (a.k.a. SUSHI projects).\n"
            + "Support for other folder structures is NO LONGER SUPPORTED.\n"
            + "To migrate to the new folder structure, make the following changes:\n"
            + "  - rename ." + sep + "config.yaml to ." + sep + "sushi-config.yaml\n"
            + "  - move ." + sep + "*.fsh files to ." + sep + "input" + sep + "fsh" + sep + "*.fsh\n";
        if (exists(NodePath.join(input, "ig-data"))) {
          msg += "  - move ." + sep + "ig-data" + sep + "* files and folders to ." + sep + "*\n";
        }
      }
      if (!exists(NodePath.join(input, "ig-data", "ig.ini"))) {
        msg += "  - if you used the \"template\" property in your config, remove it and manage ." + sep
            + "ig.ini directly\n";
      }
      if (!exists(NodePath.join(input, "ig-data", "package-list.json"))) {
        msg += "  - if you used the \"history\" property in your config, remove it and manage ." + sep
            + "package-list.json directly\n";
      }
      msg += "  - ensure your .gitignore file is not configured to ignore the sources in their new locations\n"
          + "  - add /fsh-generated to your .gitignore file to prevent SUSHI output from being checked into source control\n\n"
          + "NOTE: After you make these changes, the default output folder for SUSHI will change to ." + sep
          + "fsh-generated.\n\n"
          + "For detailed migration instructions, see: https://fshschool.org/docs/sushi/migration/\n\n";
      FshLogger.logger.error(msg);
    }
    return input;
  }

  public static String ensureOutputDir(String input, String output) {
    String outDir = output;
    if (output == null || output.isEmpty()) {
      // Default output is the parent folder of the input/fsh folder
      outDir = NodePath.join(input, "..", "..");
      FshLogger.logger.info("No output path specified. Output to " + outDir);
    }
    ensureDir(outDir);
    // If the outDir contains a fsh-generated folder, we ensure that folder is empty
    String fshGeneratedFolder = NodePath.join(outDir, "fsh-generated");
    if (exists(fshGeneratedFolder)) {
      try {
        emptyDir(fshGeneratedFolder);
      } catch (IOException | RuntimeException e) {
        FshLogger.logger.error("Unable to empty existing fsh-generated folder because of the following error: "
            + nodeMessage(e));
        FshLogger.logger.debug(stack(e));
      }
    }
    return outDir;
  }

  public static Configuration readConfig(String input) {
    String configPath = EnsureConfigurationFile.ensureConfiguration(input);
    Configuration config;
    if (configPath == null || !exists(configPath)) {
      config = LoadConfigurationFromIgResource.loadConfigurationFromIgResource(input);
    } else {
      String configYaml = readFile(configPath);
      config = ImportConfiguration.importConfiguration(configYaml, configPath);
    }
    if (config == null) {
      FshLogger.logger.error("No sushi-config.yaml in " + input + " folder, and no configuration could"
          + " be extracted from an ImplementationGuide resource.");
      throw new ThrownErrorConstructor();
    }
    if (config.fhirVersion == null || config.fhirVersion.stream().noneMatch(Processing::isSupportedFHIRVersion)) {
      String base = basename(config.filePath);
      FshLogger.logger.error("The " + base + " must specify a supported version of FHIR. Found "
          + (config.fhirVersion == null ? "undefined" : String.join(",", config.fhirVersion)) + ". Be sure to"
          + " add \"fhirVersion: 4.0.1\" (or 4.x.y, 5.0.0-snapshot1, etc., as appropriate) to the " + base
          + " file.");
      throw new ThrownErrorConstructor();
    }
    return config;
  }

  /** TS updateConfig(config, program): program.config is the parsed {@code -c key:value} map (or null). */
  public static void updateConfig(Configuration config, Map<String, String> programConfig) {
    if (programConfig != null) {
      if (Js.truthy(programConfig.get("version"))) {
        config.version = programConfig.get("version");
      }
      if (Js.truthy(programConfig.get("status"))) {
        config.status = programConfig.get("status");
      }
      if (Js.truthy(programConfig.get("releaselabel"))) {
        String label = programConfig.get("releaselabel");
        int labelIndex = -1;
        for (int i = 0; i < config.parameters.size(); i++) {
          if ("releaselabel".equals(config.parameters.get(i).get("code"))) {
            labelIndex = i;
            break;
          }
        }
        if (labelIndex != -1) {
          config.parameters.get(labelIndex).put("value", label);
        } else {
          JsObject p = new JsObject();
          p.put("code", "releaselabel");
          p.put("value", label);
          config.parameters.add(p);
        }
      }
    }
  }

  /** fhir-package-loader's RegistryClient, as used by updateExternalDependencies. */
  public interface RegistryClient {
    /** The concrete version for a label like "latest"; throws when it cannot be determined. */
    String resolveVersion(String name, String version) throws Exception;
  }

  /** readline-sync's keyInYNStrict (tests substitute an answer). */
  public interface YesNoPrompt {
    boolean keyInYNStrict(String question);
  }

  /** The interactive prompt used by updateExternalDependencies; replaceable for tests. */
  public static YesNoPrompt yesNoPrompt = Processing::consoleKeyInYNStrict;

  private static boolean consoleKeyInYNStrict(String question) {
    java.io.Console c = System.console();
    while (true) {
      System.out.print(question + " [y/n]: ");
      System.out.flush();
      String line = c != null ? c.readLine() : readStdinLine();
      if (line == null) return false;
      String t = line.trim().toLowerCase(java.util.Locale.ROOT);
      if (t.equals("y")) return true;
      if (t.equals("n")) return false;
    }
  }

  private static String readStdinLine() {
    try {
      StringBuilder sb = new StringBuilder();
      int c;
      while ((c = System.in.read()) != -1 && c != '\n') sb.append((char) c);
      return c == -1 && sb.length() == 0 ? null : sb.toString();
    } catch (IOException e) {
      return null;
    }
  }

  private static final Pattern NPM_ALIAS_TEST = Pattern.compile("@npm");

  public static boolean updateExternalDependencies(Configuration config, RegistryClient registryClient) {
    // only try to update if we got the config from sushi-config.yaml, and not from an IG
    Map<String, String> changedVersions = new LinkedHashMap<>();
    if (config.filePath == null) {
      FshLogger.logger.info("Cannot update dependencies: no sushi-config.yaml file available.");
      return true;
    }
    if (config.dependencies == null || config.dependencies.isEmpty()) {
      FshLogger.logger.info("Cannot update dependencies: no dependencies present in configuration.");
      return true;
    }
    for (JsObject dep : config.dependencies) {
      String packageId = str(dep.get("packageId"));
      String version = str(dep.get("version"));
      if (NPM_ALIAS_TEST.matcher(packageId == null ? "undefined" : packageId).find()) {
        FshLogger.logger.info("Skipping dependency version check for NPM aliased package: " + packageId);
      } else if (!"current".equals(version) && !"dev".equals(version)) {
        // current and dev have special meanings, so don't try to update those dependencies
        try {
          String latestVersion = registryClient.resolveVersion(packageId, "latest");
          if (!java.util.Objects.equals(version, latestVersion)) {
            dep.put("version", latestVersion);
            changedVersions.put(packageId, latestVersion);
          }
        } catch (Exception e) {
          FshLogger.logger.warn("Could not determine latest version for package " + packageId);
        }
      }
    }
    if (!changedVersions.isEmpty()) {
      // before changing the file, check with the user
      List<String> lines = new ArrayList<>();
      lines.add("Updates to dependency versions detected:");
      changedVersions.forEach((id, v) -> lines.add("- " + id + ": " + v));
      lines.add("SUSHI can apply these updates to your sushi-config.yaml file.");
      lines.add("This may affect the formatting of your file.");
      lines.add("Do you want to apply these updates?");
      lines.add("- [Y]es, apply updates to sushi-config.yaml");
      lines.add("- [N]o, quit without applying updates");
      lines.add("Choose one: ");
      boolean continuationChoice = yesNoPrompt.keyInYNStrict(String.join("\n", lines));
      if (continuationChoice) {
        String configText = readFile(config.filePath);
        String updated = ConfigDependencyEditor.updateDependencyVersions(configText, config.dependencies);
        if (updated != null) {
          outputFile(config.filePath, updated);
          FshLogger.logger.info("Updated dependency versions in configuration to latest available versions.");
        }
      } else {
        FshLogger.logger.info("Dependencies not updated.");
        return false;
      }
    } else {
      FshLogger.logger.info("No dependency updates available.");
    }
    return true;
  }

  public static List<RawFSH> getRawFSHes(String input) {
    List<String> files;
    if (!exists(input)) {
      FshLogger.logger.error("Invalid path to FSH definition folder.");
      throw new ThrownErrorConstructor();
    }
    files = getFilesRecursive(input);
    List<RawFSH> rawFSHes = new ArrayList<>();
    for (String file : files) {
      if (!file.endsWith(".fsh")) continue;
      String filePath = NodePath.resolve(file);
      String fileContent = readFile(filePath);
      rawFSHes.add(new RawFSH(fileContent, filePath));
    }
    return rawFSHes;
  }

  public static FSHTank fillTank(List<RawFSH> rawFSHes, Configuration config) {
    FshLogger.logger.info("Importing FSH text...");
    List<FSHDocument> docs = ImportText.importText(rawFSHes);
    return new FSHTank(docs, config);
  }

  public static void checkNullValuesOnArray(Object resource) {
    checkNullValuesOnArray(resource, "", "");
  }

  public static void checkNullValuesOnArray(Object resource, String parentName, String priorPath) {
    Object resourceName;
    if (parentName != null && !parentName.isEmpty()) {
      resourceName = parentName;
    } else {
      Object id = ownProperty(resource, "id");
      resourceName = id != null && id != Js.UNDEFINED ? id : ownProperty(resource, "name");
    }
    String resourceNameStr = Js.toStr(resourceName == null ? Js.UNDEFINED : resourceName);
    for (String propertyKey : ownPropertyKeys(resource)) {
      Object property = ownProperty(resource, propertyKey);
      String currentPath = priorPath == null || priorPath.isEmpty() ? propertyKey : priorPath + "." + propertyKey;
      // If a property's key begins with "_", we'll want to ignore null values on it's top level
      // but still check any nested objects for null values
      if (propertyKey.startsWith("_")) {
        if (isPlainObject(property)) {
          // If we encounter an object property, we'll want to check its properties as well
          checkNullValuesOnArray(property, resourceNameStr, currentPath);
        }
        if (property instanceof List<?> list) {
          for (int index = 0; index < list.size(); index++) {
            Object element = list.get(index);
            if (isPlainObject(element)) {
              checkNullValuesOnArray(element, resourceNameStr, currentPath + "[" + index + "]");
            }
          }
        }
      } else {
        if (isPlainObject(property)) {
          checkNullValuesOnArray(property, resourceNameStr, currentPath);
        }
        if (property instanceof List<?> list) {
          List<String> nullIndexes = new ArrayList<>();
          Object underscore = ownProperty(resource, "_" + propertyKey);
          boolean hasUnderscoreArray = underscore instanceof List<?>;
          for (int index = 0; index < list.size(); index++) {
            Object element = list.get(index);
            // if property is a primitive array, also check the corresponding index in the underscore property
            if (element == null && (!hasUnderscoreArray || looseNull(underscore, index))) {
              nullIndexes.add(String.valueOf(index));
            }
            if (isPlainObject(element)) {
              checkNullValuesOnArray(element, resourceNameStr, currentPath + "[" + index + "]");
            }
          }
          if (!nullIndexes.isEmpty()) {
            FshLogger.logger.warn("The array '" + currentPath + "' in " + resourceNameStr
                + " is missing values at the following indices: " + String.join(",", nullIndexes));
          }
        }
      }
    }
  }

  private static boolean looseNull(Object list, int index) {
    List<?> l = (List<?>) list;
    if (index >= l.size()) return true;
    Object v = l.get(index);
    return v == null || v == Js.UNDEFINED;
  }

  /** lodash isPlainObject over the value model: a JSON object, not one of SUSHI's resource classes. */
  private static boolean isPlainObject(Object v) {
    return v instanceof Map<?, ?> && !(v instanceof FhirResource);
  }

  private static List<String> ownPropertyKeys(Object o) {
    if (o instanceof PropertyHolder ph) return ph.propertyKeys();
    if (o instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked") Map<String, ?> mm = (Map<String, ?>) m;
      return Js.ownKeys(mm);
    }
    return List.of();
  }

  private static Object ownProperty(Object o, String key) {
    if (o instanceof PropertyHolder ph) return ph.getProperty(key);
    if (o instanceof Map<?, ?> m) return m.get(key);
    return null;
  }

  /** TS writeFHIRResources result. */
  public record WriteResult(List<String> skippedResources) {}

  public static WriteResult writeFHIRResources(String outDir, Package outPackage, FHIRDefinitions defs,
      boolean snapshot) {
    FshLogger.logger.info("Exporting FHIR resources as JSON...");
    int[] count = {0};
    List<String> skippedResources = new ArrayList<>();
    List<Object> predefinedResources = defs.allPredefinedResources();
    String exportDir = NodePath.join(outDir, "fsh-generated", "resources");
    java.util.function.Consumer<List<? extends FhirResource>> writeResources = resources -> {
      for (FhirResource resource : resources) {
        Object url = resourceProp(resource, "url");
        Object resourceType = resourceProp(resource, "resourceType");
        Object id = resourceProp(resource, "id");
        boolean duplicatesPredefined = false;
        for (Object predef : predefinedResources) {
          if (sameValue(jsonProp(predef, "url"), url) && sameValue(jsonProp(predef, "resourceType"), resourceType)
              && sameValue(jsonProp(predef, "id"), id)) {
            duplicatesPredefined = true;
            break;
          }
        }
        if (!duplicatesPredefined) {
          checkNullValuesOnArray(resource);
          outputJson(NodePath.join(exportDir, resource.getFileName()), resource.toJSON(snapshot));
          count[0]++;
        } else {
          String what = url != Js.UNDEFINED && url != null ? Js.toStr(url)
              : Js.toStr(resourceType) + "/" + Js.toStr(id);
          FshLogger.logger.error("Ignoring FSH definition for " + what
              + " since it duplicates existing pre-defined resource. "
              + "To use the FSH definition, remove the conflicting file from \"input\". "
              + "If you do want the FSH definition to be ignored, please comment the definition out "
              + "to remove this error.");
          skippedResources.add(resource.getFileName());
        }
      }
    };
    writeResources.accept(outPackage.profiles);
    writeResources.accept(outPackage.extensions);
    writeResources.accept(outPackage.logicals);
    // WARNING: While custom resources are written to disk, the IG Publisher does not
    //          accept newly defined resources. However, it is configured to automatically
    //          search the fsh-generated directory for StructureDefinitions rather than using
    //          the StructureDefinitions defined in the exported implementation guide. So, be
    //          aware that the IG Publisher will attempt to process custom resources.
    //          NOTE: To mitigate against this, the parameter 'autoload-resources = false' is
    //          injected automatically into the IG array pf parameters if the parameter was
    //          not already defined and only if the custom resources were generated by Sushi.
    writeResources.accept(outPackage.resources);
    List<FhirResource> terminology = new ArrayList<>(outPackage.valueSets);
    terminology.addAll(outPackage.codeSystems);
    writeResources.accept(terminology);

    // Filter out inline instances
    writeResources.accept(outPackage.instances.stream()
        .filter(i -> !"Inline".equals(i._instanceMeta.usage)).toList());

    FshLogger.logger.info("Exported " + count[0] + " FHIR resources as JSON.");
    return new WriteResult(skippedResources);
  }

  /** url / resourceType / id of an exported resource as TS reads them (Js.UNDEFINED when absent). */
  private static Object resourceProp(FhirResource r, String key) {
    Object v;
    if (r instanceof StructureDefinition sd) {
      v = switch (key) {
        case "url" -> sd.getUrl();
        case "id" -> sd.getId();
        case "resourceType" -> sd.getResourceType();
        default -> sd.getProperty(key);
      };
      return v == null ? Js.UNDEFINED : v;
    }
    if (r instanceof Map<?, ?> m) return m.containsKey(key) ? m.get(key) : Js.UNDEFINED;
    return Js.UNDEFINED;
  }

  private static Object jsonProp(Object json, String key) {
    if (json instanceof Map<?, ?> m) return m.containsKey(key) ? m.get(key) : Js.UNDEFINED;
    return Js.UNDEFINED;
  }

  /** JS === on JSON leaves (null and undefined are different). */
  private static boolean sameValue(Object a, Object b) {
    if (a == Js.UNDEFINED || b == Js.UNDEFINED) return a == b;
    if (a == null || b == null) return a == b;
    if (a instanceof String || a instanceof Double || a instanceof Boolean) return a.equals(b);
    return a == b;
  }

  public static void writeFSHIndex(String outDir, Package outPackage, String inputDir,
      List<String> skippedResources) {
    List<String> skipped = skippedResources == null ? List.of() : skippedResources;
    List<List<String>> textIndex = new ArrayList<>();
    List<Object> jsonIndex = new ArrayList<>();
    List<String> fileNames = new ArrayList<>(outPackage.fshMap.keySet());
    fileNames.removeIf(skipped::contains);
    fileNames.sort(null); // JS default sort: UTF-16 code unit order
    for (String fileName : fileNames) {
      Package.FshMapEntry fshInfo = outPackage.fshMap.get(fileName);
      String relativeInput = NodePath.relative(inputDir, fshInfo.sourceInfo().file);
      int startLine = fshInfo.sourceInfo().location.startLine;
      int endLine = fshInfo.sourceInfo().location.endLine;
      textIndex.add(List.of(fileName, fshInfo.fshName(), fshInfo.fshType(), relativeInput,
          startLine + " - " + endLine));
      JsObject entry = new JsObject();
      entry.put("outputFile", fileName);
      entry.put("fshName", fshInfo.fshName());
      entry.put("fshType", fshInfo.fshType());
      entry.put("fshFile", relativeInput);
      entry.put("startLine", (double) startLine);
      entry.put("endLine", (double) endLine);
      jsonIndex.add(entry);
    }
    // write txt with nice formatting
    textIndex.add(0, List.of("Output File", "Name", "Type", "FSH File", "Lines"));
    outputFile(NodePath.join(outDir, "fsh-generated", "fsh-index.txt"), TextTable.table(textIndex));
    // write json for machine usage. Use the fsh-generated/data folder for the template to pick it up.
    ensureDir(NodePath.join(outDir, "fsh-generated", "data"));
    outputJson(NodePath.join(outDir, "fsh-generated", "data", "fsh-index.json"), jsonIndex);
  }

  public static void writeFSHIndex(String outDir, Package outPackage, String inputDir) {
    writeFSHIndex(outDir, outPackage, inputDir, List.of());
  }

  private static final String EOL = "\n";

  public static void writePreprocessedFSH(String outDir, String inDir, FSHTank tank) {
    String preprocessedPath = NodePath.join(outDir, "_preprocessed");
    ensureDir(preprocessedPath);
    // Because this is the FSH that exists after processing, some entities from the original FSH are gone.
    // Specifically, RuleSets have already been applied.
    // Aliases have already been resolved for most cases, but since they may still
    // be used in a slice name, they are included.
    for (FSHDocument doc : tank.docs) {
      StringBuilder fileContent = new StringBuilder();
      // First, get all Aliases. They don't have source information.
      if (!doc.aliases.isEmpty()) {
        doc.aliases.forEach((alias, url) -> fileContent.append("Alias: ").append(alias).append(" = ").append(url)
            .append(EOL));
        fileContent.append(EOL);
      }
      // Then, get all other applicable entities. They will have source information.
      List<FshEntity> entities = new ArrayList<>();
      entities.addAll(doc.profiles.values());
      entities.addAll(doc.extensions.values());
      entities.addAll(doc.logicals.values());
      entities.addAll(doc.resources.values());
      entities.addAll(doc.instances.values());
      entities.addAll(doc.valueSets.values());
      entities.addAll(doc.codeSystems.values());
      entities.addAll(doc.invariants.values());
      entities.addAll(doc.mappings.values());
      // Sort entities by original line number, then write them out (lodash sortBy: stable, undefined last).
      entities.sort((a, b) -> {
        Integer la = a.sourceInfo.location == null ? null : a.sourceInfo.location.startLine;
        Integer lb = b.sourceInfo.location == null ? null : b.sourceInfo.location.startLine;
        if (la == null) return lb == null ? 0 : 1;
        if (lb == null) return -1;
        return Integer.compare(la, lb);
      });
      for (FshEntity entity : entities) {
        fileContent.append("// Originally defined on lines ").append(Js.toStr(lineOf(entity, true))).append(" - ")
            .append(Js.toStr(lineOf(entity, false))).append(EOL);
        fileContent.append(toFSH(entity)).append(EOL).append(EOL);
      }
      String content = fileContent.length() == 0 ? "// This file has no content after preprocessing."
          : fileContent.toString();
      String outPath = NodePath.relative(inDir, doc.file);
      outputFile(NodePath.join(preprocessedPath, outPath), content);
    }
    FshLogger.logger.info("Wrote preprocessed FSH to " + preprocessedPath);
  }

  private static Object lineOf(FshEntity e, boolean start) {
    TextLocation loc = e.sourceInfo.location;
    if (loc == null) throw new NullPointerException("Cannot read properties of undefined (reading 'startLine')");
    return (double) (start ? loc.startLine : loc.endLine);
  }

  /** entity.toFSH() for the entity kinds a document holds. */
  static String toFSH(FshEntity e) {
    if (e instanceof FshStructure s) return s.toFSH();
    if (e instanceof Instance i) return i.toFSH();
    if (e instanceof FshValueSet v) return v.toFSH();
    if (e instanceof FshCodeSystem c) return c.toFSH();
    if (e instanceof Invariant inv) return inv.toFSH();
    if (e instanceof Mapping m) return m.toFSH();
    throw new IllegalArgumentException("no toFSH for " + e.getClass().getSimpleName());
  }

  /** getFilesRecursive: absolute paths of all files under dir (stat follows symlinks; entries in strcmp order). */
  public static List<String> getFilesRecursive(String dir) {
    // always return absolute paths
    String absPath = NodePath.resolve(dir);
    List<String> out = new ArrayList<>();
    collectFiles(absPath, out);
    return out;
  }

  private static void collectFiles(String absPath, List<String> out) {
    if (Fs.isDirectory(absPath)) {
      for (String f : readdir(absPath)) {
        collectFiles(NodePath.join(absPath, f), out);
      }
    } else if (Fs.exists(absPath)) {
      out.add(absPath);
    }
  }

  /**
   * fs.readdirSync(dir): entry names sorted like libuv's scandir (strcmp on the UTF-8 bytes); empty when the
   * directory cannot be read.
   */
  public static List<String> readdir(String dir) {
    List<String> l;
    if (dir.isEmpty()) return new ArrayList<>(); // as java.io.File("").list() before: null
    try {
      l = Fs.listNames(dir);
    } catch (IOException e) {
      return new ArrayList<>();
    }
    l.sort(Processing::compareUtf8);
    return l;
  }

  private static int compareUtf8(String a, String b) {
    byte[] x = a.getBytes(StandardCharsets.UTF_8), y = b.getBytes(StandardCharsets.UTF_8);
    return Arrays.compareUnsigned(x, y);
  }

  public static String getLocalSushiVersion() {
    return SUSHI_VERSION;
  }

  /**
   * Where getLatestSushiVersion looks: TS runs {@code npm view fsh-sushi version} and falls back to GET
   * https://registry.npmjs.org/fsh-sushi. The Java port has no npm (PORTING D7): the default {@code npmView} fails,
   * so the registry fallback is used. Tests substitute both (TS mocks execSync and axios).
   */
  public interface VersionSources {
    /** stdout of {@code npm view fsh-sushi version} */
    String npmView() throws Exception;

    /** the parsed JSON body (axios res.data) of a GET; null when there is none */
    Object registryGet(String url) throws Exception;
  }

  public static VersionSources versionSources = new VersionSources() {
    @Override public String npmView() throws Exception {
      throw new UnsupportedOperationException("npm is not used by the Java port of SUSHI");
    }

    @Override public Object registryGet(String url) throws Exception {
      return org.hl7.fhir.common.js.Json.parse(webGet(url));
    }
  };

  /**
   * axios GET of a text body through org.hl7.fhir.core's {@link ManagedWebAccess} (its access policy, fhir-settings.json
   * servers, proxy, SSRF protection; redirects followed, core's 15 s timeout): the error for an HTTP error status is
   * axios's "Request failed with status code N".
   */
  static String webGet(String url) throws IOException {
    HTTPResult res = ManagedWebAccess.get(List.of("web"), url);
    if (res.getCode() >= 400) throw new IOException("Request failed with status code " + res.getCode());
    return res.getContent() == null ? "" : res.getContentAsString();
  }

  private static final Pattern SEMVERISH = Pattern.compile("^[0-9.]*\\z");

  private static String getLatestSushiVersionFallback() {
    FshLogger.logger.info("Attempting fallback to determine version of sushi.");
    try {
      Object data = versionSources.registryGet("https://registry.npmjs.org/fsh-sushi");
      if (!(data instanceof Map<?, ?> m)) {
        throw new NullPointerException("Cannot read properties of undefined (reading 'dist-tags')");
      }
      if (!(m.get("dist-tags") instanceof Map<?, ?> tags)) {
        throw new NullPointerException("Cannot read properties of undefined (reading 'latest')");
      }
      Object latestVer = tags.get("latest");
      if (!(latestVer instanceof String lv)) {
        throw new NullPointerException("Cannot read properties of undefined (reading 'match')");
      }
      if (SEMVERISH.matcher(lv).find()) {
        return lv;
      } else {
        FshLogger.logger.warn("Unable to determine the latest version of sushi.");
      }
    } catch (Exception e) {
      FshLogger.logger.warn("Unable to determine the latest version of sushi: " + nodeMessage(e));
      FshLogger.logger.debug(stack(e));
    }
    return null;
  }

  /** TS getLatestSushiVersion (see {@link VersionSources}); only called when the version check is enabled. */
  public static String getLatestSushiVersion() {
    String latestVer = null;
    try {
      String execResult = versionSources.npmView();
      execResult = execResult == null ? null : execResult.replaceFirst("\\s*\\z", "");
      if (execResult == null) {
        throw new NullPointerException("Cannot read properties of undefined (reading 'match')");
      }
      if (SEMVERISH.matcher(execResult).find()) {
        latestVer = execResult;
      }
    } catch (Exception e) {
      FshLogger.logger.info("Unable to determine the latest version of sushi: " + nodeMessage(e));
      FshLogger.logger.debug(stack(e));
    }
    if (latestVer == null) {
      latestVer = getLatestSushiVersionFallback();
    }
    return latestVer;
  }

  /** {latest, current}; latest null when undetermined. */
  public record SushiVersions(String latest, String current) {}

  /**
   * TS checkSushiVersion. Off by default in the Java port (PORTING D7): then latest = current, so the summary shows no
   * version block (what TS prints when its npm check succeeds on the latest release). Enabled with
   * {@code -Dsushi.versionCheck=true} or env {@code SUSHI_VERSION_CHECK=true}, or by {@code --require-latest}.
   */
  public static SushiVersions checkSushiVersion(boolean enabled) {
    String current = getLocalSushiVersion();
    if (!enabled) return new SushiVersions(current, current);
    return new SushiVersions(getLatestSushiVersion(), current);
  }

  public static boolean versionCheckEnabled() {
    String p = System.getProperty("sushi.versionCheck", System.getenv("SUSHI_VERSION_CHECK"));
    return p != null && (p.equalsIgnoreCase("true") || p.equals("1"));
  }

  /** A registry client over core's package client (resolveLatest), for update-dependencies. */
  public static RegistryClient defaultRegistryClient() {
    org.hl7.fhir.sushi.fhirdefs.CorePackageProvider provider = org.hl7.fhir.sushi.fhirdefs.CorePackageProvider.userCache();
    return (name, version) -> {
      if (!"latest".equals(version)) return version;
      String v = provider.resolveLatest(name);
      if (v == null) throw new IOException("Not found: " + name);
      return v;
    };
  }

  // -------------------------------------------------------------------------------------------- init

  /** readline-sync / console / HTTP used by init; replaceable in tests (TS tests spy on these). */
  public interface InitIo {
    String question(String query);

    boolean keyInYN(String query);

    void consoleLog(String message);

    /** axios GET text body; throws on failure */
    String httpGet(String url) throws Exception;
  }

  public static InitIo initIo = new InitIo() {
    @Override public String question(String query) {
      System.out.print(query);
      System.out.flush();
      java.io.Console c = System.console();
      String line = c != null ? c.readLine() : readStdinLine();
      return line == null ? "" : line;
    }

    @Override public boolean keyInYN(String query) {
      System.out.print(query + " [y/n]: ");
      System.out.flush();
      java.io.Console c = System.console();
      String line = c != null ? c.readLine() : readStdinLine();
      return line != null && line.trim().toLowerCase(java.util.Locale.ROOT).startsWith("y");
    }

    @Override public void consoleLog(String message) {
      System.out.println(message);
    }

    @Override public String httpGet(String url) throws Exception {
      return webGet(url);
    }
  };

  private static String initResource(String name) {
    try (java.io.InputStream in = Processing.class.getResourceAsStream("init-project/" + name)) {
      if (in == null) throw new IllegalStateException("missing resource init-project/" + name);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static final Map<String, String> START_CASE = Map.of("name", "Name", "id", "Id", "canonical", "Canonical",
      "status", "Status", "version", "Version", "releaseLabel", "Release Label");

  /**
   * Initializes an empty sample FSH project within a user specified subdirectory of the current working directory.
   * The sushi-config.yaml is the init-project template as the yaml library re-serialises it (TS: YAML.parseDocument,
   * set, toString), with the answered values substituted in place.
   */
  public static void init(String name, Map<String, String> config, boolean useDefaults, boolean autoInitialize) {
    initIo.consoleLog("\n╭───────────────────────────────────────────────────────────╮\n"
        + "│ This interactive tool will use your answers to create a   │\n"
        + "│ working SUSHI project configured with your project's      │\n"
        + "│ basic information. Any answers provided as command line   │\n"
        + "│ options will be used automatically.                       │\n"
        + "╰───────────────────────────────────────────────────────────╯\n");

    InitConfigDoc configDoc = new InitConfigDoc(initResource("sushi-config.normalized.yaml"));
    // Accept user input for certain fields directly or from CLI options
    for (String field : List.of("name", "id", "canonical", "status", "version", "releaseLabel")) {
      String userValue = null;
      String key = field.toLowerCase(java.util.Locale.ROOT);
      if (config != null && config.get(key) != null) {
        userValue = config.get(key);
        initIo.consoleLog(START_CASE.get(field) + ": " + config.get(key));
      } else if (field.equals("name") && name != null) {
        // name is an argument on the CLI (not an option) so handle it separately
        userValue = name;
        initIo.consoleLog("Name: " + name);
      } else if (useDefaults) {
        initIo.consoleLog(START_CASE.get(field) + ": " + configDoc.get(field));
      } else {
        userValue = initIo.question(START_CASE.get(field) + " (Default: " + configDoc.get(field) + "): ");
      }
      if (userValue != null && !userValue.isEmpty()) {
        configDoc.set(field, userValue);
      }
    }

    // And for nested publisher fields
    for (String field : List.of("name", "url")) {
      String userValue = null;
      String label = "Publisher " + Js.upperFirst(field);
      if (config != null && config.get("publisher-" + field) != null) {
        userValue = config.get("publisher-" + field);
        initIo.consoleLog(label + ": " + userValue);
      } else if (useDefaults) {
        initIo.consoleLog(label + ": " + configDoc.get("publisher." + field));
      } else {
        userValue = initIo.question(label + " (Default: " + configDoc.get("publisher." + field) + "): ");
      }
      if (userValue != null && !userValue.isEmpty()) {
        configDoc.set("publisher." + field, userValue);
      }
    }

    // Ensure copyrightYear is accurate
    configDoc.set("copyrightYear", java.time.Year.now().getValue() + "+");
    String projectName = configDoc.get("name");

    // Write init directory out, including user made sushi-config.yaml and files in utils/init-project
    String outputDir = NodePath.resolve(".", projectName);
    if (autoInitialize) {
      initIo.consoleLog("SUSHI project will be created in " + outputDir);
    } else if (!initIo.keyInYN("SUSHI project will be created in " + outputDir + ". Proceed?")) {
      initIo.consoleLog("\nAborting Initialization.\n");
      return;
    }

    // Add index.md content, updating to reflect the user given name
    String indexPageContent = initResource("index.md").replaceFirst(Pattern.quote("ExampleIG"),
        Matcher.quoteReplacement(projectName));
    ensureDir(NodePath.join(outputDir, "input", "pagecontent"));
    outputFile(NodePath.join(outputDir, "input", "pagecontent", "index.md"), indexPageContent);
    // Add ig.ini, updating to reflect the user given id
    String iniContent = initResource("ig.ini").replaceFirst(Pattern.quote("fhir.example"),
        Matcher.quoteReplacement(configDoc.get("id")));
    outputFile(NodePath.join(outputDir, "ig.ini"), iniContent);
    // Add the config
    outputFile(NodePath.join(outputDir, "sushi-config.yaml"), configDoc.toString());
    // Copy over remaining static files
    ensureDir(NodePath.join(outputDir, "input", "fsh"));
    outputFile(NodePath.join(outputDir, "input", "fsh", "patient.fsh"), initResource("patient.fsh"));
    outputFile(NodePath.join(outputDir, ".gitignore"), initResource("init-gitignore.txt"));
    outputFile(NodePath.join(outputDir, "input", "ignoreWarnings.txt"), initResource("ignoreWarnings.txt"));
    // Add the _build script
    initIo.consoleLog("Downloading _build scripts from https://github.com/HL7/ig-publisher-scripts");
    for (String script : List.of("_build.bat", "_build.sh")) {
      String url = "https://raw.githubusercontent.com/HL7/ig-publisher-scripts/main/" + script;
      try {
        String data = initIo.httpGet(url);
        outputFile(NodePath.join(outputDir, script), data);
        if (script.endsWith(".sh")) {
          try {
            Fs.setPosixPermissions(NodePath.join(outputDir, script), "rwxr-xr-x");
          } catch (UnsupportedOperationException ignored) {
            // non-POSIX file system
          }
        }
      } catch (Exception e) {
        FshLogger.logger.error("Unable to download " + script + " from " + url + ": " + nodeMessage(e));
        FshLogger.logger.debug(stack(e));
      }
    }
    int maxLength = 32;
    String printName = projectName.length() > maxLength ? projectName.substring(0, maxLength - 3) + "..."
        : projectName;
    initIo.consoleLog("\n╭───────────────────────────────────────────────────────────╮\n"
        + "│ Project initialized at: ./" + Js.padEnd(printName, maxLength) + "│\n"
        + "├───────────────────────────────────────────────────────────┤\n"
        + "│ Now try this:                                             │\n"
        + "│                                                           │\n"
        + "│ > cd " + Js.padEnd(printName, maxLength + 21) + "│\n"
        + "│ > sushi                                                   │\n"
        + "│                                                           │\n"
        + "│ For guidance on project structure and configuration see   │\n"
        + "│ the SUSHI documentation: https://fshschool.org/docs/sushi │\n"
        + "╰───────────────────────────────────────────────────────────╯\n");
  }

  /**
   * The few YAML document edits init makes (top-level scalars and publisher.name/url), in place on the template
   * text, keeping each line's trailing comment (TS sets a comment node only for status, the template's own).
   */
  static final class InitConfigDoc {
    private final List<String> lines;

    InitConfigDoc(String text) {
      lines = new ArrayList<>(List.of(text.split("\n", -1)));
    }

    private int find(String dotted) {
      String[] p = dotted.split("\\.");
      if (p.length == 1) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith(p[0] + ":")) return i;
        return -1;
      }
      int parent = find(p[0]);
      if (parent < 0) return -1;
      for (int i = parent + 1; i < lines.size(); i++) {
        String l = lines.get(i);
        if (!l.startsWith(" ") && !l.isEmpty() && !l.startsWith("#")) break;
        if (l.startsWith("  " + p[1] + ":")) return i;
      }
      return -1;
    }

    private static final Pattern VALUE = Pattern.compile("^(\\s*[^:#]+:\\s*)(.*?)(\\s+#.*)?$");

    String get(String dotted) {
      int i = find(dotted);
      if (i < 0) return "undefined";
      Matcher m = VALUE.matcher(lines.get(i));
      return m.find() ? m.group(2) : "undefined";
    }

    void set(String dotted, String value) {
      int i = find(dotted);
      if (i < 0) return;
      Matcher m = VALUE.matcher(lines.get(i));
      if (!m.find()) return;
      lines.set(i, m.group(1) + yamlScalar(value) + (m.group(3) == null ? "" : m.group(3)));
    }

    /** yaml 1.10 plain scalar when it reads back as the same string, else a double-quoted one. */
    static String yamlScalar(String v) {
      boolean plain = !v.isEmpty() && v.equals(Js.trim(v))
          && !Pattern.compile("^[-?:,\\[\\]{}#&*!|>'\"%@`]").matcher(v).find()
          && !v.contains(": ") && !v.contains(" #") && !v.endsWith(":")
          && !Pattern.compile("^(true|false|null|~|[-+]?(\\d[\\d_]*)?(\\.\\d*)?([eE][-+]?\\d+)?|0x[0-9a-fA-F]+|0o[0-7]+|[-+]?\\.inf|\\.nan)$",
              Pattern.CASE_INSENSITIVE).matcher(v).matches();
      if (plain) return v;
      return org.hl7.fhir.common.js.Json.stringify(v);
    }

    @Override public String toString() {
      return String.join("\n", lines);
    }
  }

  // -------------------------------------------------------------------------------------------- fs-extra helpers

  /** fs.existsSync (follows symlinks; "" is false). */
  public static boolean exists(String p) {
    if (p == null || p.isEmpty()) return false;
    return Fs.exists(p);
  }

  /** fs.readFileSync(p, 'utf8'): lenient UTF-8 (U+FFFD for malformed input), BOM kept. */
  public static String readFile(String p) {
    try {
      return Fs.readUtf8(p);
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(nodeMessage(e), e);
    }
  }

  /** fs-extra ensureDirSync. */
  public static void ensureDir(String dir) {
    try {
      Fs.createDirectories(dir);
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(nodeMessage(e), e);
    }
  }

  /** fs-extra emptyDirSync: remove everything inside dir (create it when missing). */
  public static void emptyDir(String dir) throws IOException {
    if (!Fs.exists(dir)) {
      Fs.createDirectories(dir);
      return;
    }
    for (Path c : Fs.list(dir)) deleteRecursively(c.toString());
  }

  private static void deleteRecursively(String p) throws IOException {
    if (Fs.isDirectoryNoFollow(p)) {
      for (Path c : Fs.list(p)) deleteRecursively(c.toString());
    }
    Fs.delete(p);
  }

  /** fs-extra outputFileSync(p, text): create parent folders, write UTF-8 text as is. */
  public static void outputFile(String p, String content) {
    try {
      Path parent = Path.of(p).toAbsolutePath().getParent();
      if (parent != null) Fs.createDirectories(parent.toString());
      Fs.write(p, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(nodeMessage(e), e);
    }
  }

  /** fs-extra outputJSONSync(p, value, {spaces: 2}): {@link org.hl7.fhir.common.js.Json#outputJson}. */
  public static void outputJson(String p, Object value) {
    outputFile(p, org.hl7.fhir.common.js.Json.outputJson(value));
  }

  /** e.message as node would show it for the common filesystem errors. */
  static String nodeMessage(Throwable e) {
    if (e instanceof java.nio.file.AccessDeniedException a) {
      return "EACCES: permission denied, " + a.getFile();
    }
    if (e instanceof java.nio.file.NoSuchFileException n) {
      return "ENOENT: no such file or directory, " + n.getFile();
    }
    if (e instanceof java.io.UncheckedIOException u && u.getCause() != null) return u.getMessage();
    return e.getMessage();
  }

}
