package org.hl7.fhir.sushi.fhirdefs.probe;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.sushi.fhirdefs.DiskBasedVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.FindResourceInfoOptions;
import org.hl7.fhir.sushi.fhirdefs.InMemoryVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.LoadStatus;
import org.hl7.fhir.sushi.fhirdefs.PackageInfo;
import org.hl7.fhir.sushi.fhirdefs.ResourceInfo;
import org.hl7.fhir.sushi.fhirdefs.VirtualPackage;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.Processing;

/**
 * Java half of the WP-8 lookup probe (scripts/probe/probe.js is the TS half). Reads the TS probe NDJSON of one IG,
 * loads the same configuration with the Java FHIRDefinitions on core's package layer (dependencies through
 * Processing.loadExternalDependencies, predefined resources from the same folders), replays every recorded lookup and
 * writes the Java results in the same format. scripts/probe/compare.py diffs the two.
 *
 * <p>usage: ProbeReplay &lt;ts.ndjson&gt; &lt;java.ndjson&gt;
 */
public final class ProbeReplay {
  private ProbeReplay() {}

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    List<Map<String, Object>> events = new ArrayList<>();
    try (BufferedReader r = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
      String line;
      while ((line = r.readLine()) != null) {
        if (!line.isBlank()) events.add((Map<String, Object>) Json.parse(line));
      }
    }
    List<String> logLines = new ArrayList<>();
    FshLogger.logger.setSink(e -> {
      logLines.add(e.level() + " " + e.message());
      System.out.println(e.level() + " " + e.message());
    });
    List<Map<String, Object>> loadEvents = new ArrayList<>();
    FHIRDefinitions defs = new FHIRDefinitions() {
      @Override
      public LoadStatus loadPackage(String name, String version) {
        JsObject ev = new JsObject().with("ev", "load").with("name", name).with("version", version);
        loadEvents.add(ev);
        try {
          LoadStatus s = super.loadPackage(name, version);
          ev.put("status", s.name());
          return s;
        } catch (RuntimeException e) {
          ev.put("status", "THREW");
          ev.put("error", e.getMessage());
          throw e;
        }
      }

      @Override
      public LoadStatus loadVirtualPackage(VirtualPackage pkg) {
        JsObject ev = new JsObject().with("ev", "vload").with("name", pkg.getPackageJSON().get("name"))
            .with("version", pkg.getPackageJSON().get("version"));
        loadEvents.add(ev);
        LoadStatus s = super.loadVirtualPackage(pkg);
        ev.put("status", s.name());
        return s;
      }
    };
    defs.initialize();

    for (Map<String, Object> ev : events) {
      if ("config".equals(ev.get("ev"))) {
        Configuration config = new Configuration();
        config.filePath = (String) ev.get("filePath");
        config.fhirVersion = new ArrayList<>();
        for (Object v : (List<Object>) ev.get("fhirVersion")) config.fhirVersion.add((String) v);
        if (ev.get("dependencies") instanceof List<?> deps) {
          config.dependencies = new ArrayList<>();
          for (Object d : deps) config.dependencies.add(new JsObject((Map<String, Object>) d));
        }
        try {
          Processing.loadExternalDependencies(defs, config);
        } catch (RuntimeException e) {
          logLines.add("THREW " + e);
        }
      } else if ("predef".equals(ev.get("ev"))) {
        // TS ig/predefinedResources.ts loadPredefinedResources (WP-10 owns the real port)
        List<String> paths = getPredefinedResourcePaths((String) ev.get("resourceDir"), (String) ev.get("projectDir"),
            (List<Object>) ev.get("parameters"));
        if (!paths.equals(ev.get("paths"))) {
          logLines.add("PROBE predefined paths differ: java " + paths + " ts " + ev.get("paths"));
        }
        defs.loadVirtualPackage(new DiskBasedVirtualPackage(
            InMemoryVirtualPackage.packageJSON(FHIRDefinitions.PREDEFINED_PACKAGE_NAME,
                FHIRDefinitions.PREDEFINED_PACKAGE_VERSION),
            paths, (l, m) -> FshLogger.logger.logMessage(l, m), true, true));
      }
    }

    try (BufferedWriter w = Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8)) {
      for (Map<String, Object> ev : loadEvents) w.write(Json.stringify(ev) + "\n");
      for (Map<String, Object> ev : events) {
        if (!"q".equals(ev.get("ev"))) continue;
        String fn = (String) ev.get("fn");
        String key = (String) ev.get("key");
        Object res;
        switch (fn) {
          case "info" -> {
            ResourceInfo info = defs.findResourceInfo(key, options((Map<String, Object>) ev.get("opt")));
            List<Object> l = new ArrayList<>();
            if (info != null) l.add(row(info));
            res = l;
          }
          case "infos" -> {
            List<Object> l = new ArrayList<>();
            for (ResourceInfo info : defs.findResourceInfos(key, options((Map<String, Object>) ev.get("opt")))) {
              l.add(row(info));
            }
            res = l;
          }
          case "findPackageInfos" -> {
            List<Object> l = new ArrayList<>();
            for (PackageInfo p : defs.findPackageInfos(key)) l.add(p.name + "#" + p.version);
            res = l;
          }
          case "findPackageInfo", "findPackageJSON" -> {
            int h = key.lastIndexOf('#');
            PackageInfo p = defs.findPackageInfo(key.substring(0, h), key.substring(h + 1));
            res = p == null ? null : p.name + "#" + p.version;
          }
          default -> res = "UNKNOWN fn";
        }
        JsObject out = new JsObject().with("ev", "q").with("fn", fn).with("key", key).with("opt", ev.get("opt"))
            .with("n", ev.get("n")).with("res", res);
        w.write(Json.stringify(out) + "\n");
      }
      for (String l : logLines) w.write(Json.stringify(new JsObject().with("ev", "log").with("line", l)) + "\n");
    }
    System.exit(0);
  }

  private static List<Object> row(ResourceInfo info) {
    String rp = info.resourcePath == null ? "" : info.resourcePath;
    String file = rp.startsWith("virtual:")
        ? last(rp.replaceFirst("^virtual:[^:]+:", ""), '/')
        : last(rp, File.separatorChar);
    List<Object> l = new ArrayList<>();
    l.add(info.packageName + "#" + info.packageVersion);
    l.add(info.resourceType);
    l.add(info.id);
    l.add(info.url);
    l.add(info.version);
    l.add(info.sdFlavor);
    l.add(file);
    return l;
  }

  private static String last(String s, char sep) {
    int i = s.lastIndexOf(sep);
    return i < 0 ? s : s.substring(i + 1);
  }

  @SuppressWarnings("unchecked")
  private static FindResourceInfoOptions options(Map<String, Object> o) {
    if (o == null) return null;
    List<String> type = o.get("type") instanceof List<?> l ? (List<String>) l : null;
    String scope = (String) o.get("scope");
    List<FindResourceInfoOptions.SortBy> sort = null;
    if (o.get("sort") instanceof List<?> sl) {
      sort = new ArrayList<>();
      for (Object s : sl) {
        String str = (String) s;
        if (str.startsWith("LoadOrder:")) sort.add(FindResourceInfoOptions.byLoadOrder(str.endsWith("true")));
        else sort.add(FindResourceInfoOptions.byType(List.of(str.substring(5).split(","))));
      }
    }
    Integer limit = o.get("limit") instanceof Number n ? n.intValue() : null;
    return new FindResourceInfoOptions(type, scope, sort, limit);
  }

  /** port of sushi/src/ig/predefinedResources.ts getPredefinedResourcePaths (for the probe only) */
  static List<String> getPredefinedResourcePaths(String resourceDir, String projectDir, List<Object> configParameters)
      throws Exception {
    String[] pathEnds = {"capabilities", "extensions", "models", "operations", "profiles", "resources", "vocabulary",
        "examples"};
    Set<String> predefinedResourcePaths = new LinkedHashSet<>();
    for (String pathEnd : pathEnds) {
      Path p = Path.of(resourceDir, pathEnd);
      if (Files.exists(p)) predefinedResourcePaths.add(p.normalize().toString());
    }
    if (configParameters != null && projectDir != null) {
      for (Object o : configParameters) {
        @SuppressWarnings("unchecked")
        Map<String, Object> parameter = (Map<String, Object>) o;
        if (!Js.truthy(parameter.get("value")) || !"path-resource".equals(parameter.get("code"))) continue;
        String pathResource = Js.toStr(parameter.get("value"));
        String rel = pathResource.replaceFirst("/\\*\\z", "");
        Path full = Path.of(projectDir);
        for (String part : rel.split("/", -1)) full = full.resolve(part);
        String fullPath = full.normalize().toString();
        if (Files.exists(full)) {
          predefinedResourcePaths.add(fullPath);
          if (pathResource.endsWith("/*")) addRecursiveChildFolders(full, predefinedResourcePaths);
        }
      }
    }
    return new ArrayList<>(predefinedResourcePaths);
  }

  private static void addRecursiveChildFolders(Path folder, Set<String> out) throws Exception {
    if (!Files.isDirectory(folder)) return;
    try (var s = Files.list(folder)) {
      // readdirSync order (sorted on Linux ext4? no: directory order) -- sorted here; set order only affects
      // which folder is listed first, and DiskBasedVirtualPackage sorts all file paths anyway
      for (Path e : (Iterable<Path>) s.sorted()::iterator) {
        if (Files.isDirectory(e, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
          out.add(e.toAbsolutePath().normalize().toString());
          addRecursiveChildFolders(e, out);
        }
      }
    }
  }
}
