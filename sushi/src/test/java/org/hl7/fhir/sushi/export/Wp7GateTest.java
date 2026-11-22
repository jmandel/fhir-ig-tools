package org.hl7.fhir.sushi.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hl7.fhir.sushi.fhirdefs.DiskBasedVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.FhirResource;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.importer.ImportText;
import org.hl7.fhir.sushi.importer.RawFSH;
import org.hl7.fhir.sushi.importer.config.ImportConfiguration;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.Processing;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * WP-7 gate driver (not a unit test; skipped unless -Dsushi.wp7gate.ig=<IG source dir> is set): runs the SUSHI
 * pipeline as app.ts does for {@code sushi --out <out> <ig>} up to writeFHIRResources -- readConfig,
 * loadExternalDependencies, loadPredefinedResources, fillTank, checkDuplicateNameEntities, exportFHIR -- and writes
 * fsh-generated/resources/*.json (all resource kinds, as Processing.writeFHIRResources) to -Dsushi.wp7gate.out, plus
 * the log to sushi.log there. A stand-in until WP-10's App exists; scripts/ts2junit/wp7-gate.sh compares the output
 * with TS SUSHI 3.21.1's reference output.
 */
class Wp7GateTest {
  @Test
  void runGate() throws IOException {
    String ig = System.getProperty("sushi.wp7gate.ig");
    Assumptions.assumeTrue(ig != null && !ig.isEmpty(), "set -Dsushi.wp7gate.ig to run the WP-7 gate driver");
    Path root = Path.of(ig).toAbsolutePath().normalize();
    Path out = Path.of(System.getProperty("sushi.wp7gate.out", root.resolve("..").resolve("java-out").toString()));
    Files.createDirectories(out);
    StringBuilder log = new StringBuilder();
    FshLogger.logger.addTap(e -> log.append(String.format("%-5s", e.level().name())).append(" ").append(e.message()).append('\n'));

    // readConfig(originalInput)
    Path configPath = Files.exists(root.resolve("sushi-config.yaml")) ? root.resolve("sushi-config.yaml")
        : root.resolve("sushi-config.yml");
    Configuration config = ImportConfiguration.importConfiguration(Files.readString(configPath, StandardCharsets.UTF_8),
        configPath.toString());
    // getRawFSHes(input/fsh) in getFilesRecursive order (libuv scandir: entries sorted by name)
    Path fshDir = root.resolve("input").resolve("fsh");
    List<RawFSH> raw = new ArrayList<>();
    for (Path f : filesRecursive(fshDir)) {
      if (f.toString().endsWith(".fsh")) raw.add(new RawFSH(Files.readString(f, StandardCharsets.UTF_8), f.toString()));
    }
    FSHTank tank = new FSHTank(ImportText.importText(raw), config);
    tank.checkDuplicateNameEntities();

    FHIRDefinitions defs = FHIRDefinitions.createFHIRDefinitions();
    Processing.loadExternalDependencies(defs, config);
    // loadPredefinedResources(defs, input/.., originalInput, config.parameters)
    List<String> paths = predefinedResourcePaths(root.resolve("input"), root, config.parameters);
    JsObject packageJSON = new JsObject();
    packageJSON.put("name", FHIRDefinitions.PREDEFINED_PACKAGE_NAME);
    packageJSON.put("version", FHIRDefinitions.PREDEFINED_PACKAGE_VERSION);
    defs.loadVirtualPackage(new DiskBasedVirtualPackage(packageJSON, paths, FshLogger.logger::logMessage, true, true));
    defs.optimize();

    Package pkg = ExportFHIR.exportFHIR(tank, defs);

    // writeFHIRResources (snapshot = false)
    Path resources = out.resolve("fsh-generated").resolve("resources");
    Files.createDirectories(resources);
    List<Object> predefined = defs.allPredefinedResources();
    List<FhirResource> all = new ArrayList<>();
    all.addAll(pkg.profiles);
    all.addAll(pkg.extensions);
    all.addAll(pkg.logicals);
    all.addAll(pkg.resources);
    all.addAll(pkg.valueSets);
    all.addAll(pkg.codeSystems);
    for (InstanceDefinition i : pkg.instances) {
      if (!"Inline".equals(i._instanceMeta.usage)) all.add(i);
    }
    for (FhirResource r : all) {
      Map<?, ?> json = (Map<?, ?>) r.toJSON(false);
      boolean dup = predefined.stream().anyMatch(p -> p instanceof Map<?, ?> pm
          && Objects.equals(pm.get("url"), json.get("url")) && Objects.equals(pm.get("resourceType"),
              json.get("resourceType")) && Objects.equals(pm.get("id"), json.get("id")));
      if (!dup) {
        Files.writeString(resources.resolve(r.getFileName()), Json.outputJson(r.toJSON(false)), StandardCharsets.UTF_8);
      }
    }
    Files.writeString(out.resolve("sushi.log"), log.toString(), StandardCharsets.UTF_8);
  }

  /** TS getFilesRecursive: depth first, entries in name order. */
  static List<Path> filesRecursive(Path dir) throws IOException {
    List<Path> result = new ArrayList<>();
    if (!Files.isDirectory(dir)) {
      if (Files.exists(dir)) result.add(dir);
      return result;
    }
    List<Path> entries;
    try (Stream<Path> s = Files.list(dir)) {
      entries = new ArrayList<>(s.toList());
    }
    entries.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
    for (Path e : entries) result.addAll(filesRecursive(e));
    return result;
  }

  private static final Pattern TRAILING_STAR = Pattern.compile("/\\*$");

  /** TS getPredefinedResourcePaths. */
  static List<String> predefinedResourcePaths(Path resourceDir, Path projectDir, List<JsObject> configParameters)
      throws IOException {
    Set<String> result = new LinkedHashSet<>();
    for (String end : List.of("capabilities", "extensions", "models", "operations", "profiles", "resources",
        "vocabulary", "examples")) {
      Path p = resourceDir.resolve(end);
      if (Files.exists(p)) result.add(p.toString());
    }
    if (configParameters != null && projectDir != null) {
      for (JsObject parameter : configParameters) {
        Object value = parameter.get("value");
        if (!(value instanceof String pathResource) || pathResource.isEmpty()
            || !"path-resource".equals(parameter.get("code"))) {
          continue;
        }
        Path fullPath = projectDir;
        for (String part : TRAILING_STAR.matcher(pathResource).replaceAll("").split("/", -1)) {
          fullPath = fullPath.resolve(part);
        }
        fullPath = fullPath.normalize();
        if (Files.exists(fullPath)) {
          result.add(fullPath.toString());
          if (pathResource.endsWith("/*")) addRecursiveChildFolders(fullPath, result);
        }
      }
    }
    return new ArrayList<>(result);
  }

  private static void addRecursiveChildFolders(Path folder, Set<String> result) throws IOException {
    if (!Files.isDirectory(folder)) return;
    List<Path> entries;
    try (Stream<Path> s = Files.list(folder)) {
      entries = new ArrayList<>(s.toList());
    }
    entries.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
    for (Path e : entries) {
      if (Files.isDirectory(e)) {
        result.add(e.toAbsolutePath().normalize().toString());
        addRecursiveChildFolders(e, result);
      }
    }
  }
}
