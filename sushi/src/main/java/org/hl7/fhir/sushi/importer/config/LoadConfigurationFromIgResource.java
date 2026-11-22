package org.hl7.fhir.sushi.importer.config;

import org.hl7.fhir.sushi.fhirdefs.xml.FhirXmlToJs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.FshLogger;

// port of sushi/src/import/loadConfigurationFromIgResource.ts (SUSHI 3.21.1)
public final class LoadConfigurationFromIgResource {
  private LoadConfigurationFromIgResource() {}

  private static final FshLogger logger = FshLogger.logger;

  /**
   * Attempts to find an ig resource file and extract the required configuration properties from it.
   *
   * @param igRoot path to the root of the IG project
   * @return the extracted configuration, or null (TS null)
   */
  public static Configuration loadConfigurationFromIgResource(String igRoot) {
    JsObject js = loadConfigurationFromIgResourceJs(igRoot);
    return js == null ? null : ConfigurationJs.fromJs(js);
  }

  /** As {@link #loadConfigurationFromIgResource}, returning the TS configuration object. */
  @SuppressWarnings("unchecked")
  public static JsObject loadConfigurationFromIgResourceJs(String igRoot) {
    // First, look in the ig.ini file for a path to the IG resource
    String igPath = null;
    String igIniPath = NodePath.join(igRoot, "ig.ini");
    if (Files.exists(Path.of(igIniPath))) {
      try {
        String ig = iniIgValue(Files.readString(Path.of(igIniPath), StandardCharsets.UTF_8));
        if (Js.truthy(ig)) igPath = NodePath.join(igRoot, ig);
      } catch (IOException | RuntimeException e) {
        // ignored, like TS
      }
    }
    // Make a list of possible path, if ig.ini exists and points to a ig file, add just that
    // otherwise consider all files in the input folder of the ig
    String igInputPath = NodePath.join(igRoot, "input");
    List<String> possibleIgPaths = new ArrayList<>();
    if (igPath != null && Files.exists(Path.of(igPath))) {
      possibleIgPaths.add(igPath);
    } else if (Files.exists(Path.of(igInputPath))) {
      for (String file : readdirSorted(igInputPath)) possibleIgPaths.add(NodePath.join(igInputPath, file));
    }
    // Go through each possible path, and check each xml/json file to see if they are an IG resource
    Map<String, Object> igResource = null;
    boolean multipleIgs = false;
    for (String filePath : possibleIgPaths) {
      Object fileContent = null;
      String ext = NodePath.extname(filePath);
      try {
        if (ext.equals(".json")) {
          fileContent = Json.parseFile(Files.readString(Path.of(filePath), StandardCharsets.UTF_8));
        } else if (ext.equals(".xml")) {
          fileContent = FhirXmlToJs.xmlToObj(Files.readString(Path.of(filePath), StandardCharsets.UTF_8));
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      if (fileContent instanceof Map<?, ?> fc && "ImplementationGuide".equals(fc.get("resourceType"))) {
        // If 2 possible IG resources are found, we cannot tell which to use, so return
        if (igResource != null) {
          multipleIgs = true;
          logger.error("Multiple possible ImplementationGuide resources in \"input\" folder, so no configuration "
              + "can be extracted. Ensure only one ImplementationGuide resource is in the \"input\" folder, or give "
              + "the path to the desired resource in ig.ini.");
        }
        igResource = (Map<String, Object>) fc;
        igPath = filePath;
      }
    }
    // Extract the configuration from the resource
    if (igResource != null && Js.truthy(igResource.get("url")) && !multipleIgs) {
      logger.info("Extracting FSHOnly configuration from " + igPath + "...");
      Object url = igResource.get("url");
      JsObject config = new JsObject();
      config.put("id", undef(igResource, "id"));
      config.put("canonical", JsOps.requireStringForCall(url, "igResource.url.replace")
          .replaceFirst("/ImplementationGuide[^\\n\\r\\u2028\\u2029]*", ""));
      config.put("url", url);
      config.put("name", undef(igResource, "name"));
      config.put("packageId", undef(igResource, "packageId"));
      config.put("version", undef(igResource, "version"));
      // Default status to 'draft' on FSHOnly IGs so exported resources inherit the property
      config.put("status", JsOps.coalesce(undef(igResource, "status"), "draft"));
      config.put("fhirVersion", JsOps.coalesce(undef(igResource, "fhirVersion"), new ArrayList<Object>()));
      Object dependsOn = undef(igResource, "dependsOn");
      Object dependencies = Js.UNDEFINED;
      if (dependsOn instanceof List<?> deps) {
        List<Object> kept = new ArrayList<>();
        for (Object dep : deps) {
          if (Js.truthy(JsOps.get(dep, "packageId")) && Js.truthy(JsOps.get(dep, "version"))) kept.add(dep);
        }
        dependencies = kept;
      }
      config.put("dependencies", dependencies);
      Object definition = undef(igResource, "definition");
      config.put("parameters", JsOps.coalesce(JsOps.getOpt(definition, "parameter"), new ArrayList<Object>()));
      config.put("FSHOnly", true);
      if (!Js.truthy(igResource.get("id"))) config.remove("id");
      if (dependencies instanceof List<?> deps) {
        for (Object d : deps) {
          Map<String, Object> dep = (Map<String, Object>) d;
          String packageId = JsOps.templ(dep.get("packageId"));
          if (Pattern.compile("[A-Z]").matcher(packageId).find()) {
            String lowercasePackageId = packageId.toLowerCase(Locale.ROOT);
            logger.warn("Dependency " + packageId + " contains uppercase characters, which is discouraged. SUSHI will "
                + "use " + lowercasePackageId + " as the package name.");
            dep.put("packageId", lowercasePackageId);
          }
        }
      }
      logger.info("Extracted configuration:");
      for (String k : Js.ownKeys(config)) {
        Object v = config.get(k);
        if (v instanceof List<?> l) {
          for (int i = 0; i < l.size(); i++) logger.info("  " + k + "[" + i + "]: " + stringify(l.get(i)));
        } else {
          logger.info("  " + k + ": " + stringify(v));
        }
      }
      return config;
    }
    return null;
  }

  /** JSON.stringify(v) in a template literal: undefined prints "undefined". */
  private static String stringify(Object v) {
    return v == Js.UNDEFINED ? "undefined" : Json.stringify(v);
  }

  private static Object undef(Map<String, Object> m, String k) {
    return m.containsKey(k) ? m.get(k) : Js.UNDEFINED;
  }

  /** fs.readdirSync order (libuv scandir sorts by strcmp on the UTF-8 names). */
  private static List<String> readdirSorted(String dir) {
    try (Stream<Path> s = Files.list(Path.of(dir))) {
      List<String> names = new ArrayList<>(s.map(p -> p.getFileName().toString()).toList());
      names.sort((a, b) -> Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)));
      return names;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * {@code ini.parse(text).IG?.ig} with the npm ini package's rules: {@code [section]} headers, {@code key = value}
   * lines, {@code ;}/{@code #} comment lines and inline comments in unquoted values, quoted values unquoted, the last
   * assignment wins.
   */
  static String iniIgValue(String text) {
    String section = null;
    String value = null;
    for (String raw : text.split("\\r\\n|\\n|\\r", -1)) {
      String line = raw;
      if (line.isBlank() || line.strip().startsWith(";") || line.strip().startsWith("#")) continue;
      String t = line.strip();
      if (t.startsWith("[") && t.endsWith("]")) {
        section = t.substring(1, t.length() - 1);
        continue;
      }
      int eq = line.indexOf('=');
      String key = unsafe(eq < 0 ? line : line.substring(0, eq));
      String val = eq < 0 ? "true" : unsafe(line.substring(eq + 1));
      if ("IG".equals(section) && key.equals("ig")) value = val;
    }
    return value;
  }

  /** ini's unsafe(): trim, unquote, strip an inline comment from an unquoted value. */
  private static String unsafe(String val) {
    val = val.strip();
    if (val.length() >= 2 && ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))) {
      if (val.startsWith("'")) val = val.substring(1, val.length() - 1);
      else {
        try {
          val = (String) Json.parse(val);
        } catch (RuntimeException e) {
          val = val.substring(1, val.length() - 1);
        }
      }
      return val;
    }
    StringBuilder sb = new StringBuilder();
    boolean esc = false;
    for (int i = 0; i < val.length(); i++) {
      char c = val.charAt(i);
      if (esc) {
        sb.append("\\;#".indexOf(c) >= 0 ? String.valueOf(c) : "\\" + c);
        esc = false;
      } else if (c == ';' || c == '#') {
        break;
      } else if (c == '\\') {
        esc = true;
      } else {
        sb.append(c);
      }
    }
    if (esc) sb.append('\\');
    return sb.toString().strip();
  }
}
