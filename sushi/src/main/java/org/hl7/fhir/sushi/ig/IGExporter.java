package org.hl7.fhir.sushi.ig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.FindResourceInfoOptions;
import org.hl7.fhir.sushi.fhirdefs.PackageInfo;
import org.hl7.fhir.sushi.fhirdefs.ResourceInfo;
import org.hl7.fhir.sushi.fhirtypes.CodeSystem;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.FhirResource;
import org.hl7.fhir.sushi.fhirtypes.ImplementationGuide;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fhirtypes.ValueSet;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.Configuration.ConfigurationGroup;
import org.hl7.fhir.sushi.fshtypes.Configuration.ConfigurationMenuItem;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.importer.ParseCodeLexeme;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.utils.FHIRVersionUtils;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.NodePath;
import org.hl7.fhir.sushi.utils.Processing;
import org.hl7.fhir.sushi.utils.SanitizeFilename;
import org.hl7.fhir.sushi.utils.TitleCase;

// port of sushi/src/ig/IGExporter.ts (SUSHI 3.21.1)
/**
 * The IG Exporter exports the FSH artifacts into a file structure supported by the IG Publisher: the
 * ImplementationGuide resource (fsh-generated/resources/ImplementationGuide-{id}.json), includes/menu.xml,
 * includes/fsh-link-references.md, a generated index page, plus checks of ig.ini and package-list.
 *
 * <p>The IG JSON is an ordered JS object built in TS assignment order: keys assigned {@code undefined} keep their
 * place ({@link Js#UNDEFINED}, skipped on output) and arrays/objects taken from the configuration are the SAME
 * objects (TS shares them, and later steps mutate them through either path).
 *
 * @see <a href="https://build.fhir.org/ig/FHIR/ig-guidance/index.html">IG guidance</a>
 */
public class IGExporter {
  // Deprecated but still supported in IG Publisher, so we'll support it too.
  private static final String DEPRECATED_RESOURCE_FORMAT_EXTENSION =
      "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format";
  private static final String CURRENT_RESOURCE_FORMAT_EXTENSION =
      "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format";
  private static final List<String> IG_RESOURCE_FORMAT_EXTENSIONS =
      List.of(DEPRECATED_RESOURCE_FORMAT_EXTENSION, CURRENT_RESOURCE_FORMAT_EXTENSION);
  // FHIR ImplementationGuide.name invariant: name.matches('[A-Z]([A-Za-z0-9_]){0,254}')
  private static final Pattern IG_NAME_REGEX = Pattern.compile("^[A-Z]([A-Za-z0-9_]){0,254}\\z");
  private static final String EOL = "\n";
  private static final String SEP = NodePath.SEP;

  private static boolean isR4(List<String> fhirVersion) {
    for (String v : fhirVersion) {
      String name = FHIRVersionUtils.getFHIRVersionInfo(v).name();
      if (name != null && name.matches("R4B?")) return true;
    }
    return false;
  }

  private ImplementationGuide ig;
  private final String configPath;
  private final Configuration config;
  private final String configName;
  private final Package pkg;
  private final FHIRDefinitions fhirDefs;
  private final String inputPath;

  public IGExporter(Package pkg, FHIRDefinitions fhirDefs, String inputPath) {
    this.pkg = pkg;
    this.fhirDefs = fhirDefs;
    this.inputPath = inputPath;
    this.config = pkg.config;
    this.configPath = NodePath.resolve(this.inputPath, "..", basename(this.config.filePath));
    this.configName = NodePath.basename(this.configPath);
  }

  private static String basename(String p) {
    if (p == null) {
      throw new IllegalArgumentException("The \"path\" argument must be of type string. Received undefined");
    }
    return NodePath.basename(p);
  }

  /** The IG JSON built so far (TS tests read the private {@code ig} field). */
  public ImplementationGuide getIg() {
    return ig;
  }

  /**
   * Export the IG structure to the location specified by the outPath argument.
   *
   * @see <a href="https://build.fhir.org/ig/FHIR/ig-guidance/using-templates.html#directory-structure">templates</a>
   */
  public void export(String outPath) {
    Processing.ensureDir(outPath);
    normalizeResourceReferences();
    initIG();
    addResources();
    addPredefinedResources();
    addConfiguredResources();
    sortResources();
    addConfiguredGroups();
    addIndex(outPath);
    addLinkReferences(outPath);
    if (config.pages == null || config.pages.isEmpty()) {
      addOtherPageContent();
    } else {
      addConfiguredPageContent();
    }
    addMenuXML(outPath);
    checkIgIni();
    checkPackageList();
    if (!isR4(config.fhirVersion)) {
      updateForR5();
    } else {
      translateR5PropertiesToR4();
    }
    addImplementationGuide(outPath);
  }

  // ---------------------------------------------------------------------------------------------- value helpers

  /** null for JS null/undefined. */
  private static Object nn(Object v) {
    return v == Js.UNDEFINED ? null : v;
  }

  private static Object undef(Object v) {
    return v == null ? Js.UNDEFINED : v;
  }

  private static String str(Object v) {
    v = nn(v);
    return v == null ? null : v instanceof String s ? s : Js.toStr(v);
  }

  /** TS stringOrElse: the value when it is a string, else undefined. */
  private static String stringOrElse(Object v) {
    return v instanceof String s ? s : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object v) {
    return v instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object v) {
    return v instanceof List<?> l ? (List<Object>) l : null;
  }

  /** a?.reference?.reference */
  private static String refOf(Map<String, Object> r) {
    if (r == null) return null;
    Map<String, Object> ref = map(r.get("reference"));
    return ref == null ? null : str(ref.get("reference"));
  }

  private static boolean isTrue(Object v) {
    return Boolean.TRUE.equals(v);
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> asObjects(List<? extends T> l) {
    return (List<T>) l;
  }

  private JsObject definition() {
    return (JsObject) ig.get("definition");
  }

  private List<Object> resources() {
    return list(definition().get("resource"));
  }

  private JsObject pageRoot() {
    return (JsObject) definition().get("page");
  }

  private List<Object> parameters() {
    return list(definition().get("parameter"));
  }

  // ---------------------------------------------------------------------------------------------- normalize

  /** Normalizes FSHy ids and names to the required relative URLs or canonicals. */
  public void normalizeResourceReferences() {
    if (config.global != null) {
      for (JsObject g : config.global) {
        if (Js.truthy(g.get("profile"))) {
          g.put("profile", normalizeResourceReference(str(g.get("profile")), false));
        }
      }
    }
    if (config.groups != null) {
      for (ConfigurationGroup g : config.groups) {
        if (g.resources != null) {
          List<String> normalized = new ArrayList<>();
          for (String r : g.resources) normalized.add(normalizeResourceReference(r, true));
          g.resources = normalized;
        }
      }
    }
    if (config.resources != null) {
      for (JsObject r : config.resources) {
        Map<String, Object> ref = map(r.get("reference"));
        if (ref != null && Js.truthy(ref.get("reference"))) {
          ref.put("reference", normalizeResourceReference(str(ref.get("reference")), true));
        }
        if (Js.truthy(r.get("exampleCanonical"))) {
          r.put("exampleCanonical", normalizeResourceReference(str(r.get("exampleCanonical")), false));
        }
      }
    }
    if (config.parameters != null) {
      for (JsObject p : config.parameters) {
        // parameter.code will always be a string coming from the config
        FshCode parsedCode = ParseCodeLexeme.parseCodeLexeme(str(p.get("code")));
        if (Js.truthy(parsedCode.system)) {
          // If a system and code is provided, normalize the system
          p.put("code", normalizeResourceReference(parsedCode.system, false) + "#" + Js.toStr(undef(parsedCode.code)));
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------- initIG

  /**
   * Initializes the ImplementationGuide JSON w/ data from the configuration YAML.
   *
   * @see <a href="https://confluence.hl7.org/pages/viewpage.action?pageId=35718629">NPM package spec</a>
   */
  public void initIG() {
    // first, properties that can be directly used without much trouble
    ig = new ImplementationGuide();
    ig.put("resourceType", "ImplementationGuide");
    ig.put("id", undef(config.id));
    ig.put("meta", undef(config.meta));
    ig.put("implicitRules", undef(config.implicitRules));
    ig.put("language", undef(config.language));
    ig.put("text", undef(config.text));
    ig.put("contained", undef(config.contained));
    ig.put("extension", undef(config.extension));
    ig.put("modifierExtension", undef(config.modifierExtension));
    ig.put("url", config.url != null ? config.url
        : Js.toStr(undef(config.canonical)) + "/ImplementationGuide/" + Js.toStr(undef(config.id)));
    ig.put("version", undef(config.version));
    ig.put("name", undef(config.name));
    ig.put("title", undef(config.title));
    ig.put("status", undef(config.status));
    ig.put("experimental", undef(config.experimental));
    ig.put("date", undef(config.date));
    ig.put("publisher", undef(config.publisher));
    ig.put("contact", undef(config.contact));
    ig.put("description", undef(config.description));
    ig.put("useContext", undef(config.useContext));
    ig.put("jurisdiction", undef(config.jurisdiction));
    ig.put("copyright", undef(config.copyright));
    ig.put("packageId", config.packageId != null ? config.packageId : undef(config.id));
    ig.put("license", undef(config.license));
    ig.put("fhirVersion", undef(config.fhirVersion));
    // put an empty dependsOn here to preserve the location of this property (delete later if not needed)
    ig.put("dependsOn", new ArrayList<Object>());
    ig.put("global", undef(config.global));
    JsObject definition = new JsObject();
    definition.put("extension", config.definition == null ? Js.UNDEFINED : undef(config.definition.extension));
    // put an empty grouping here to preserve the location of this property (delete later if not needed)
    definition.put("grouping", new ArrayList<Object>());
    definition.put("resource", new ArrayList<Object>());
    JsObject page = new JsObject();
    page.put("nameUrl", "toc.html");
    page.put("title", "Table of Contents");
    page.put("generation", "html");
    page.put("page", new ArrayList<Object>()); // index.[md|html] is required and added later
    definition.put("page", page);
    // required parameters are enforced in the configuration
    // default to empty array in case we want to add other parameters later
    definition.put("parameter", config.parameters != null ? config.parameters : new ArrayList<JsObject>());
    definition.put("template", undef(config.templates));
    ig.put("definition", definition);

    if (config.name != null && !IG_NAME_REGEX.matcher(config.name).find()) {
      SourceInfo src = new SourceInfo();
      src.file = config.filePath;
      FshLogger.logger.warn("The ImplementationGuide name \"" + config.name + "\" does not match the FHIR constraint "
          + "'[A-Z]([A-Za-z0-9_]){0,254}'. Valid names start with an upper-case ASCII letter ('A'..'Z') "
          + "followed by any combination of upper- or lower-case ASCII letters ('A'..'Z', and 'a'..'z'), "
          + "numerals ('0'-'9') and '_', with a length limit of 255 characters. "
          + "Update the \"name\" property in " + configName + ".", src);
    }
    // Add the path-history, if applicable (only applies to HL7 IGs)
    if (Pattern.compile("^https?://hl7.org/").matcher(Js.toStr(undef(config.canonical))).find()
        && parameters().stream().noneMatch(param -> "path-history".equals(map(param).get("code")))) {
      JsObject p = new JsObject();
      p.put("code", "path-history");
      p.put("value", Js.toStr(undef(config.canonical)) + "/history.html");
      parameters().add(p);
    }
    // Default 'autoload-resources' to false if it is not already defined
    // and only if custom resources are defined. This is done to counter
    // the IG Publisher behaving as if 'autoload-resources' is set to true
    // and allowing the IG Publisher to attempt processing of custom resources.
    if (pkg.resources != null && !pkg.resources.isEmpty()
        && parameters().stream().noneMatch(param -> "autoload-resources".equals(map(param).get("code")))) {
      JsObject p = new JsObject();
      p.put("code", "autoload-resources");
      p.put("value", "false");
      parameters().add(p);
      FshLogger.logger.info("The autoload-resources parameter has been set to false because this implementation "
          + "guide contains custom resources.");
    }
    // add dependencies, fixing old-style cross-version dependencies
    List<Map<String, Object>> dependencies = Processing.fixCrossVersionDependencies(config.dependencies, false);
    if (dependencies != null && !dependencies.isEmpty()) {
      List<Object> igs = fhirDefs.findResourceJSONs("*", FindResourceInfoOptions.ofType("ImplementationGuide"));
      for (Map<String, Object> dependency : dependencies) {
        JsObject dependsEntry = fixDependsOn(dependency, igs);
        if (dependsEntry != null) {
          list(ig.get("dependsOn")).add(dependsEntry);
        }
      }
    } else {
      ig.remove("dependsOn");
    }
    // delete grouping value if there are no groups in the config
    boolean anyGroupingId = config.resources != null
        && config.resources.stream().anyMatch(r -> nn(r.get("groupingId")) != null);
    if ((config.groups == null || config.groups.isEmpty()) && !anyGroupingId) {
      definition.remove("grouping");
    }
    // delete global if it is empty
    if (config.global == null || config.global.isEmpty()) {
      ig.remove("global");
    }
    // delete templates if it is empty
    if (config.templates == null || config.templates.isEmpty()) {
      definition.remove("template");
    }
  }

  private static final Pattern NPM_ALIAS = Pattern.compile("^([^@]+)@npm:(.+)\\z", Pattern.DOTALL);
  private static final Pattern RELEASE_SUFFIX = Pattern.compile("\\.r\\d+\\z");

  /**
   * Fixes a dependsOn entry by specifying its uri (if not yet specified) and generating an id (if not yet
   * specified). Will also ensure that required properties (uri/version) are available. If it cannot ensure a valid
   * dependsOn entry, it returns null.
   */
  private JsObject fixDependsOn(Map<String, Object> dependency, List<Object> igs) {
    // Clone it so we don't mutate the original
    JsObject dependsOn = new JsObject();
    for (Map.Entry<String, Object> e : dependency.entrySet()) dependsOn.put(e.getKey(), Js.cloneDeep(e.getValue()));

    // Get the alias and real package id (in cases where it uses an NPM alias)
    // See: https://chat.fhir.org/#narrow/channel/179239-tooling/topic/NPM.20Aliases/near/517985527
    String alias = null;
    String realPackageId = str(dependsOn.get("packageId")) == null ? "" : str(dependsOn.get("packageId"));
    java.util.regex.Matcher npmAliasMatcher = NPM_ALIAS.matcher(realPackageId);
    if (npmAliasMatcher.find()) {
      alias = npmAliasMatcher.group(1);
      realPackageId = npmAliasMatcher.group(2);
    }

    // By default, dependsOn.reason should not be supported because it is an R5 element
    dependsOn.remove("reason");

    if (nn(dependsOn.get("version")) == null) {
      // No need for the detailed log message since we already logged one in the package loader.
      FshLogger.logger.error("Failed to add " + Js.toStr(undef(dependency.get("packageId")))
          + " to ImplementationGuide instance because no version was specified in your " + configName + ".");
      return null;
    }

    String version = str(dependsOn.get("version"));
    String resolvedVersion = version;
    if ("latest".equals(version)) {
      // TODO (TS): This assumes only a single version of a package is in scope
      // NOTE: It replaces latest with the real version since IG Publisher doesn't support "latest"
      Map<String, Object> dependencyIG = null;
      for (Object o : igs) {
        if (Objects.equals(nn(map(o).get("packageId")), realPackageId)) {
          dependencyIG = map(o);
          break;
        }
      }
      if (dependencyIG != null && nn(dependencyIG.get("version")) != null) {
        resolvedVersion = str(dependencyIG.get("version"));
        dependsOn.put("version", dependencyIG.get("version"));
      } else {
        List<PackageInfo> packageInfos = new ArrayList<>();
        for (PackageInfo info : fhirDefs.findPackageInfos(realPackageId)) {
          if (info.version != null) packageInfos.add(info);
        }
        if (!packageInfos.isEmpty()) {
          resolvedVersion = packageInfos.get(0).version;
          dependsOn.put("version", resolvedVersion);
        }
      }
    }

    String depVersion = str(dependsOn.get("version"));
    if (depVersion != null && depVersion.endsWith(".x")) {
      List<String> matchingVersions = new ArrayList<>();
      for (Object o : igs) {
        Map<String, Object> m = map(o);
        if (Objects.equals(nn(m.get("packageId")), realPackageId) && nn(m.get("version")) != null) {
          matchingVersions.add(str(m.get("version")));
        }
      }
      String max = IgSupport.maxSatisfying(matchingVersions, depVersion);
      if (max != null) {
        // Only assign resolvedVersion because we want to keep dependsOn.version as-is
        resolvedVersion = max;
      }
    }

    if (nn(dependsOn.get("uri")) == null) {
      // Need to find the URI from the IG in the FHIR cache
      final String rpid = realPackageId;
      final String rv = resolvedVersion;
      Map<String, Object> dependencyIG = null;
      for (Object o : igs) {
        Map<String, Object> m = map(o);
        if (Objects.equals(nn(m.get("packageId")), rpid)
            && (Objects.equals(nn(m.get("version")), rv) || "current".equals(rv) || "dev".equals(rv))) {
          dependencyIG = m;
          break;
        }
      }
      dependsOn.put("uri", dependencyIG == null ? Js.UNDEFINED : undef(dependencyIG.get("url")));
      if (nn(dependsOn.get("uri")) == null && RELEASE_SUFFIX.matcher(realPackageId).find()) {
        // Some release-specific packages (e.g., hl7.fhir.eu.extensions.r4) don't include the correct packageId;
        // they might have a package id like hl7.fhir.eu.extensions.4.0.1 and an id like hl7.fhir.eu.extensions.
        // See: https://chat.fhir.org/#narrow/channel/215610-shorthand/topic/ImplementationGuide.20URL.20missing/near/576162330
        Map<String, Object> fallBackIG = null;
        String packageIdPrefix = RELEASE_SUFFIX.matcher(realPackageId).replaceFirst(".");
        for (Object o : igs) {
          Map<String, Object> m = map(o);
          String igPackageId = m.get("packageId") instanceof String s ? s : null;
          if (igPackageId != null && igPackageId.startsWith(packageIdPrefix)) {
            // Grab version suffix like 4.0.1
            String versionSuffix = igPackageId.substring(packageIdPrefix.length());
            // Convert version suffix to release suffix like r4 (also converting DSTU2 to r2 and STU3 to r3)
            String name = FHIRVersionUtils.getFHIRVersionInfo(versionSuffix).name();
            String releaseSuffix = name == null ? null : name.toLowerCase(Locale.ROOT).replaceFirst("^[^0-9]+", "r");
            // Put the package id back together with the fixed suffix
            String fixedPackageId = igPackageId.substring(0, packageIdPrefix.length())
                + (releaseSuffix == null ? "undefined" : releaseSuffix);
            if (fixedPackageId.equals(realPackageId)
                && (Objects.equals(nn(m.get("version")), rv) || "current".equals(rv) || "dev".equals(rv))) {
              fallBackIG = m;
              break;
            }
          }
        }
        dependsOn.put("uri", fallBackIG == null ? Js.UNDEFINED : undef(fallBackIG.get("url")));
      }
      if (nn(dependsOn.get("uri")) == null) {
        // there may be a package.json that can help us here
        Map<String, Object> dependencyPackageJson = map(fhirDefs.findPackageJSON(realPackageId, resolvedVersion));
        dependsOn.put("uri", dependencyPackageJson == null ? Js.UNDEFINED
            : undef(dependencyPackageJson.get("canonical")));
      }

      if (nn(dependsOn.get("uri")) == null) {
        // setting uri to required format as indicated in zulip discussion:
        // https://chat.fhir.org/#narrow/stream/179252-IG-creation/topic/fhir.2Edicom.20has.20no.20ImplementationGuide.20resource/near/265772431
        dependsOn.put("uri", "http://fhir.org/packages/" + realPackageId + "/ImplementationGuide/" + realPackageId);
      }
    }

    if (nn(dependsOn.get("id")) == null) {
      String dependsOnId = alias != null ? realPackageId + "_" + alias : realPackageId;
      // packageId should be "a..z, A..Z, 0..9, and _ and it must start with a..z | A..Z" per
      // https://chat.fhir.org/#narrow/stream/215610-shorthand/topic/SUSHI.200.2E12.2E7/near/199193333
      // depId should be [A-Za-z0-9\-\.]{1,64}, so we replace . and - with _ and prepend "id_" if it does not start
      // w/ a-z|A-Z
      boolean startsWithLetter = !dependsOnId.isEmpty() && Character.toString(dependsOnId.charAt(0))
          .matches("[A-Za-z]");
      dependsOn.put("id", startsWithLetter ? dependsOnId.replaceAll("[.-]", "_")
          : "id_" + dependsOnId.replaceAll("[.-]", "_"));
    }

    // Keep dependsOn.extension as configured, so no special handling is needed
    return dependsOn;
  }

  // ---------------------------------------------------------------------------------------------- index

  /**
   * Add the index.md file. Creates an index.md based on the "indexPageContent" in sushi-config.yaml. If the user
   * provided an index file, and no "indexPageContent" in sushi-config.yaml is specified, the provided file is used
   * instead. The provided file may be in one of two locations: ./input/pagecontent or ./input/pages
   */
  public void addIndex(String igPath) {
    String pageContentExportPath = NodePath.join(igPath, "input", "pagecontent");

    // If the user provided an index.md file, use that
    // There are four possible locations for it (two filenames in two directories)
    String inputIndexMarkdownPageContentPath = NodePath.join(inputPath, "input", "pagecontent", "index.md");
    String inputIndexXMLPageContentPath = NodePath.join(inputPath, "input", "pagecontent", "index.xml");
    String inputIndexMarkdownPagesPath = NodePath.join(inputPath, "input", "pages", "index.md");
    String inputIndexXMLPagesPath = NodePath.join(inputPath, "input", "pages", "index.xml");
    String generation = "markdown";
    String filePath = null;
    if (Processing.exists(inputIndexMarkdownPageContentPath)) filePath = inputIndexMarkdownPageContentPath;
    if (Processing.exists(inputIndexXMLPageContentPath)) filePath = inputIndexXMLPageContentPath;
    if (Processing.exists(inputIndexMarkdownPagesPath)) filePath = inputIndexMarkdownPagesPath;
    if (Processing.exists(inputIndexXMLPagesPath)) filePath = inputIndexXMLPagesPath;

    if (Js.truthy(config.indexPageContent)) {
      Processing.ensureDir(pageContentExportPath);

      if (filePath != null) {
        String filePathString = NodePath.join(NodePath.basename(inputPath), NodePath.relative(inputPath, filePath));

        String preferredFileMessage = "Since a " + filePathString + " file was found, the \"indexPageContent\" "
            + "property in the " + configName + " will be ignored and an index.md file will not be generated. "
            + "Remove the \"indexPageContent\" property in " + configName + " to resolve this warning.";
        SourceInfo src = new SourceInfo();
        src.file = filePath;
        FshLogger.logger.warn("Found both an \"indexPageContent\" property in " + configName
            + " and an index file at " + filePathString + ". " + preferredFileMessage, src);

        if (!Processing.exists(inputIndexMarkdownPagesPath) && !Processing.exists(inputIndexMarkdownPageContentPath)
            && (Processing.exists(inputIndexXMLPagesPath) || Processing.exists(inputIndexXMLPageContentPath))) {
          generation = "html";
        }
        // Add user-provided index file to IG definition
        list(pageRoot().get("page")).add(page("index.html", "Home", generation));
        return;
      }

      // Generate index file if indexPageContent is provided in config
      String filePathString = "input";
      String warning = warningBlock("<!-- index.md {% comment %}", "{% endcomment %} -->", List.of(
          "To change the contents of this file, edit the \"indexPageContent\" attribute in the tank " + configName
              + " file",
          "or provide your own index file in the " + filePathString + SEP + "pagecontent or " + filePathString + SEP
              + "pages folder.",
          "See: https://build.fhir.org/ig/FHIR/ig-guidance/using-templates.html#root.input"), true);
      String outputPath = NodePath.join(pageContentExportPath, "index.md");
      Processing.outputFile(outputPath, warning + config.indexPageContent);
      FshLogger.logger.info("Generated index.md based on \"indexPageContent\" in " + configName + ".");
    } else if (!Processing.exists(inputIndexMarkdownPagesPath) && !Processing.exists(inputIndexMarkdownPageContentPath)
        && (Processing.exists(inputIndexXMLPagesPath) || Processing.exists(inputIndexXMLPageContentPath))) {
      generation = "html";
    } else {
      // do nothing -- no indexPageContent in config, do nothing with index file if provided
    }

    // Add user-provided or generated index file to IG definition
    // If pages are defined in the configuration, this is the author's responsibility
    if ((config.pages == null || config.pages.isEmpty()) && (filePath != null || Js.truthy(config.indexPageContent))) {
      list(pageRoot().get("page")).add(page("index.html", "Home", generation));
    }
  }

  private static JsObject page(String nameUrl, String title, String generation) {
    JsObject p = new JsObject();
    p.put("nameUrl", nameUrl);
    p.put("title", title);
    p.put("generation", generation);
    return p;
  }

  // ---------------------------------------------------------------------------------------------- link references

  public void addLinkReferences(String igPath) {
    // no need to make this file if there are no resources
    if (resources().isEmpty()) {
      return;
    }
    String linkReferencesDir = NodePath.join(igPath, "fsh-generated", "includes");
    String linkReferencesExportPath = NodePath.join(linkReferencesDir, "fsh-link-references.md");
    Processing.ensureDir(linkReferencesDir);
    List<String> content = new ArrayList<>();
    for (Object o : resources()) {
      Map<String, Object> igResource = map(o);
      // FSH resources and predefined resources will have a _linkRef
      // a configured resource may lack a name
      // in that case, try to build a useful name from the reference
      String ref = refOf(igResource);
      Object linkName = nn(igResource.get("_linkRef"));
      if (linkName == null) linkName = nn(igResource.get("name"));
      if (linkName == null) linkName = ref == null ? null : ref.replaceFirst("^[^/]*/", "");
      // delete the _linkRef now that we've used it
      igResource.remove("_linkRef");
      content.add("[" + Js.toStr(undef(linkName)) + "]: "
          + (ref == null ? "undefined" : ref.replaceFirst("/", "-")) + ".html");
    }
    Processing.outputFile(linkReferencesExportPath, String.join("\n", content));
  }

  // ---------------------------------------------------------------------------------------------- pages

  private record PageMetadata(String originalName, Integer prefix, String[] name, String title, String fileType) {
    String nameValue() {
      return name[0];
    }
  }

  /**
   * Adds additional pages beyond index.md that are defined by the user. Only add formats that are supported by the IG
   * template. Intro and notes file contents are injected into relevant pages and should not be treated as their own
   * page. Three directories are checked for additional page content: pagecontent, pages, and resource-docs.
   */
  private void addOtherPageContent() {
    for (String contentFolder : List.of("pagecontent", "pages", "resource-docs")) {
      String inputPageContentPath = NodePath.join(inputPath, "input", contentFolder);
      if (Processing.exists(inputPageContentPath)) {
        List<PageMetadata> organizedPages = organizePageContent(Processing.readdir(inputPageContentPath));

        boolean invalidFileTypeIncluded = false;
        for (PageMetadata page : organizedPages) {
          // All user defined pages are included in input/${contentFolder}
          String pagePath = NodePath.join(inputPath, "input", contentFolder, page.originalName());
          boolean isSupportedFileType = page.fileType().equals("md") || page.fileType().equals("xml");
          boolean isIntroOrNotesFile = page.nameValue().endsWith("-intro") || page.nameValue().endsWith("-notes");
          if (isSupportedFileType) {
            // Intro and notes files will be in supported formats but are not separate pages, so they should not be
            // added to IG definition
            if (!isIntroOrNotesFile) {
              // Valid page files will be added to the IG definition
              list(pageRoot().get("page")).add(page(page.nameValue() + ".html", page.title(),
                  page.fileType().equals("md") ? "markdown" : "html"));
            }
          } else if (!IgSupport.isJunk(NodePath.basename(pagePath))) {
            invalidFileTypeIncluded = true;
          }
        }
        if (invalidFileTypeIncluded) {
          SourceInfo src = new SourceInfo();
          src.file = inputPageContentPath;
          FshLogger.logger.warn("Files not in the supported file types (.md and .xml) were detected.", src);
        }
      }
    }
  }

  /**
   * Adds additional pages to the IG based on user configuration. Only pages present in the configuration are added,
   * regardless of available files.
   */
  public void addConfiguredPageContent() {
    // only configured pages are added to the implementation guide,
    for (JsObject page : config.pages) {
      addConfiguredPage(page, list(pageRoot().get("page")));
    }
    // all files in page content folders are left alone but checked for supported file types
    for (String contentFolder : List.of("pagecontent", "pages", "resource-docs")) {
      boolean invalidFileTypeIncluded = false;
      String inputPageContentPath = NodePath.join(inputPath, "input", contentFolder);
      if (Processing.exists(inputPageContentPath)) {
        for (String contentFile : Processing.readdir(inputPageContentPath)) {
          String fileType = contentFile.substring(contentFile.lastIndexOf('.') + 1);
          if (!(fileType.equals("md") || fileType.equals("xml")) && !IgSupport.isJunk(NodePath.basename(contentFile))) {
            invalidFileTypeIncluded = true;
          }
        }
      }
      if (invalidFileTypeIncluded) {
        SourceInfo src = new SourceInfo();
        src.file = inputPageContentPath;
        FshLogger.logger.warn("Files not in the supported file types (.md and .xml) were detected. These files will "
            + "be included without any processing.", src);
      }
    }
  }

  /** Adds pages to the IG's list of pages, recursively, keeping the configured structure. */
  private void addConfiguredPage(Map<String, Object> page, List<Object> target) {
    String nameUrl = str(page.get("nameUrl"));
    if (Js.truthy(nameUrl)) {
      int lastPeriod = nameUrl.lastIndexOf('.');
      String name;
      String fileType = "";
      if (lastPeriod == -1) {
        name = nameUrl;
      } else {
        name = nameUrl.substring(0, lastPeriod);
        fileType = nameUrl.substring(lastPeriod + 1);
      }
      // Any page listed in configuration will be added, even if it does not exist.
      JsObject igPage = new JsObject();
      igPage.put("nameUrl", name + ".html");
      Object title = nn(page.get("title"));
      igPage.put("title", title != null ? title : TitleCase.titleCase(String.join(" ", Js.words(name))));
      Object generation = nn(page.get("generation"));
      igPage.put("generation", generation != null ? generation : (fileType.equals("md") ? "markdown" : "html"));
      if (Js.truthy(page.get("extension"))) igPage.put("extension", page.get("extension"));
      if (Js.truthy(page.get("modifierExtension"))) igPage.put("modifierExtension", page.get("modifierExtension"));
      List<Object> subpages = list(page.get("page"));
      if (subpages != null && !subpages.isEmpty()) {
        List<Object> igSubpages = new ArrayList<>();
        for (Object subpage : subpages) {
          addConfiguredPage(map(subpage), igSubpages);
        }
        if (!igSubpages.isEmpty()) {
          igPage.put("page", igSubpages);
        }
      }
      target.add(igPage);
    }
  }

  private static final Pattern NUMERIC_PREFIX = Pattern.compile("^(\\d+)_(.*)", Pattern.DOTALL);

  /** Sorts and renames pages based on numeric prefixes (which remain part of the file name). */
  private List<PageMetadata> organizePageContent(List<String> pagesIn) {
    // Remove any preexisting duplicate file names, and log an error
    List<String> pages = new ArrayList<>();
    for (String page : pagesIn) {
      String found = null;
      for (String p : pagesIn) {
        if (sliceToLastDot(p).equals(sliceToLastDot(page))) {
          found = p;
          break;
        }
      }
      if (!Objects.equals(found, page)) {
        SourceInfo src = new SourceInfo();
        src.file = page;
        FshLogger.logger.error("Duplicate file " + page + " will be ignored. Please rename to avoid collisions.", src);
      } else {
        pages.add(page);
      }
    }

    List<PageMetadata> pageData = new ArrayList<>();
    for (String page : pages) {
      java.util.regex.Matcher nameParts = NUMERIC_PREFIX.matcher(page);
      Integer prefix = null;
      String nameWithoutPrefix;
      String nameWithPrefix = sliceToLastDot(page);
      if (!nameParts.find()) {
        nameWithoutPrefix = sliceToLastDot(page);
      } else {
        prefix = parseIntJs(nameParts.group(1));
        nameWithoutPrefix = sliceToLastDot(nameParts.group(2));
      }
      pageData.add(new PageMetadata(page, prefix, new String[] {nameWithPrefix},
          TitleCase.titleCase(String.join(" ", Js.words(nameWithoutPrefix))),
          page.substring(page.lastIndexOf('.') + 1)));
    }
    boolean mightHaveDuplicates = true;
    while (mightHaveDuplicates) {
      mightHaveDuplicates = false;
      for (PageMetadata page : pageData) {
        List<PageMetadata> sameName = pageData.stream().filter(o -> o.nameValue().equals(page.nameValue())).toList();
        if (sameName.size() > 1) {
          mightHaveDuplicates = true;
          for (PageMetadata matchingPage : sameName) {
            matchingPage.name()[0] = sliceToLastDot(matchingPage.originalName());
          }
        }
      }
    }
    List<PageMetadata> out = new ArrayList<>(pageData.stream().filter(p -> !p.nameValue().equals("index")).toList());
    out.sort(IGExporter::compareIgFilenames);
    return out;
  }

  /** JS s.slice(0, s.lastIndexOf('.')) (lastIndexOf -1 slices off the last character). */
  private static String sliceToLastDot(String s) {
    int i = s.lastIndexOf('.');
    if (i == -1) return s.isEmpty() ? s : s.substring(0, s.length() - 1);
    return s.substring(0, i);
  }

  /** parseInt of a digit string (JS doubles beyond int range are not expected for page prefixes). */
  private static Integer parseIntJs(String digits) {
    try {
      return Integer.parseInt(digits);
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  /**
   * Compares two file names, each of which may be prefixed with a number. Neither prefixed: alphabetical
   * (localeCompare). One prefixed: that one first. Both: numerically, ties alphabetical.
   */
  private static int compareIgFilenames(PageMetadata pageA, PageMetadata pageB) {
    if (pageA.prefix() == null && pageB.prefix() == null) {
      return IgSupport.localeCompare(pageA.nameValue(), pageB.nameValue());
    } else if (pageA.prefix() == null) {
      return 1;
    } else if (pageB.prefix() == null) {
      return -1;
    } else {
      int prefixComparison = Integer.compare(pageA.prefix(), pageB.prefix());
      if (prefixComparison == 0) {
        return IgSupport.localeCompare(pageA.nameValue(), pageB.nameValue());
      } else {
        return prefixComparison;
      }
    }
  }

  // ---------------------------------------------------------------------------------------------- menu.xml

  /**
   * Adds menu.xml. A user can define a menu in sushi-config.yaml or provide one in ./input/includes. If neither is
   * provided, a warning is issued to the user.
   */
  public void addMenuXML(String igPath) {
    String menuXMLDefaultPath = NodePath.join(inputPath, "input", "includes", "menu.xml");
    String menuXMLOutputPath = NodePath.join(igPath, "fsh-generated", "includes", "menu.xml");
    boolean menuXMLExists = Processing.exists(menuXMLDefaultPath);

    // If user provided file and config, log a warning but prefer the file.
    if (menuXMLExists && config.menu != null) {
      String filePathString = NodePath.join(NodePath.basename(inputPath), "input", "includes", "menu.xml");
      String preferredFileMessage = "Since a " + filePathString + " file was found, the \"menu\" property in the "
          + configName + " will be ignored and a menu.xml file will not be generated. Remove the " + filePathString
          + " file to use the \"menu\" property in " + configName + " to generate a menu.xml file instead.";
      SourceInfo src = new SourceInfo();
      src.file = menuXMLDefaultPath;
      FshLogger.logger.warn("Found both a \"menu\" property in " + configName + " and a menu.xml file at "
          + filePathString + ". " + preferredFileMessage, src);
      return;
    }

    // Use config menu if defined and no file is provided.
    if (config.menu != null) {
      StringBuilder menu = new StringBuilder("<ul xmlns=\"http://www.w3.org/1999/xhtml\" class=\"nav navbar-nav\">"
          + EOL);
      for (ConfigurationMenuItem item : config.menu) {
        menu.append(buildMenuItem(item, 2));
      }
      menu.append("</ul>");

      String filePathString = NodePath.join("input", "includes");
      String warning = warningBlock("<!-- " + NodePath.basename(menuXMLOutputPath) + " {% comment %}",
          "{% endcomment %} -->", List.of(
              "To change the contents of this file, edit the \"menu\" attribute in the tank " + configName + " file",
              "or provide your own menu.xml in the " + filePathString + " folder"), true);
      Processing.outputFile(menuXMLOutputPath, warning + menu);
    }

    // If user did not provide file or config, log a warning.
    if (!menuXMLExists && config.menu == null) {
      String filePathString = NodePath.join("input", "includes");
      FshLogger.logger.warn("No \"menu\" property or file was found. Generate a menu.xml in the " + filePathString
          + " folder or specify a \"menu\" property in " + configName + ".");
    }
  }

  /** Build individual menu item for menu.xml file. An item could contain a submenu. */
  private String buildMenuItem(ConfigurationMenuItem item, int spaces) {
    String prefixSpaces = " ".repeat(spaces);
    StringBuilder menuItem = new StringBuilder();

    if (item.subMenu != null) {
      menuItem.append(prefixSpaces).append("<li class=\"dropdown\">").append(EOL);
      menuItem.append(buildSubMenu(item, spaces + 2));
      menuItem.append(prefixSpaces).append("</li>").append(EOL);
    } else {
      menuItem.append(prefixSpaces).append("<li>").append(EOL).append(prefixSpaces).append("  ");
      if (Js.truthy(item.url)) {
        menuItem.append("<a ");
        if (isTrue(item.openInNewTab)) menuItem.append("target=\"_blank\" ");
        menuItem.append("href=\"").append(item.url).append("\">");
      }
      menuItem.append(encodeMenuName(item.name));
      if (Js.truthy(item.url)) {
        menuItem.append("</a>");
      }
      menuItem.append(EOL).append(prefixSpaces).append("</li>").append(EOL);
    }
    return menuItem.toString();
  }

  /** Build a submenu for an item for menu.xml. */
  private String buildSubMenu(ConfigurationMenuItem item, int spaces) {
    String prefixSpaces = " ".repeat(spaces);
    StringBuilder subMenu = new StringBuilder();
    subMenu.append(prefixSpaces).append("<a data-toggle=\"dropdown\" href=\"index.html\" class=\"dropdown-toggle\">")
        .append(encodeMenuName(item.name)).append(EOL);
    subMenu.append(prefixSpaces).append("  ").append("<b class=\"caret\"></b>").append(EOL);
    subMenu.append(prefixSpaces).append("</a>").append(EOL);
    subMenu.append(prefixSpaces).append("<ul class=\"dropdown-menu\">").append(EOL);
    if (item.subMenu != null) {
      for (ConfigurationMenuItem subItem : item.subMenu) {
        if (subItem.subMenu != null) {
          FshLogger.logger.warn("The " + subItem.name + " menu item specifies a sub-menu. The IG template currently "
              + "only supports two levels of menus. The sub-menu for " + subItem.name + " is included in the "
              + "menu.xml file but it will not be rendered in the IG.");
        }
        subMenu.append(buildMenuItem(subItem, spaces + 2));
      }
    }
    subMenu.append(prefixSpaces).append("</ul>").append(EOL);
    return subMenu.toString();
  }

  private static final Pattern ESCAPED = Pattern.compile("&quot;|&apos;|&lt;|&gt;|&amp;");
  private static final Pattern BARE_AMP = Pattern.compile("&(?!quot;|apos;|lt;|gt;|amp;)");

  private String encodeMenuName(String name) {
    String escapedName = name;
    if (ESCAPED.matcher(Js.toStr(undef(name))).find()) {
      FshLogger.logger.warn("SUSHI now supports automatically escaping characters in XML. You can safely replace the "
          + "escaped character with the unescaped character in the following menu configuration item: " + name
          + ". SUSHI will escape it when generating the menu.xml file.");
    }
    if (escapedName == null) throw new NullPointerException("Cannot read properties of undefined (reading 'replace')");
    escapedName = escapedName.replace("\"", "&quot;");
    escapedName = escapedName.replace("'", "&apos;");
    escapedName = escapedName.replace("<", "&lt;");
    escapedName = escapedName.replace(">", "&gt;");
    // Replace & with &amp; but we don't want to replace any previously escaped characters, which will start with &.
    escapedName = BARE_AMP.matcher(escapedName).replaceAll("&amp;");
    return escapedName;
  }

  // ---------------------------------------------------------------------------------------------- resources

  /** Add each of the resources from the package to the IG JSON. Configuration may specify resources to omit. */
  private void addResources() {
    // NOTE: Custom resources are not included in the implementation guide
    //       because the IG Publisher will not except newly defined resources.
    //       This only prevents adding custom resources into the IG. It does
    //       NOT prevent custom resource StructureDefinitions from being
    //       written to disk.
    List<FhirResource> resources = new ArrayList<>();
    resources.addAll(pkg.profiles);
    resources.addAll(pkg.extensions);
    resources.addAll(pkg.logicals);
    resources.addAll(pkg.valueSets);
    resources.addAll(pkg.codeSystems);
    for (FhirResource r : resources) {
      String referenceKey = Js.toStr(undef(resourceType(r))) + "/" + Js.toStr(undef(id(r)));
      JsObject configResource = findConfigResource(referenceKey);
      if (configResource == null || !isTrue(configResource.get("omit"))) {
        addPackageResource(referenceKey, r, configResource);
      }
    }
    for (InstanceDefinition instance : pkg.instances) {
      if ("Inline".equals(instance._instanceMeta.usage)) continue;
      // Filter out instances that have a type that has the same type as a custom resource defined in the package
      if (pkg.resources.stream().anyMatch(r -> Objects.equals(r.getType(), instance.getResourceType()))) continue;
      // Logical instances should use Binary type. See: https://fshschool.org/docs/sushi/tips/#instances-of-logical-models
      Object idOrName = instance.getId() != null ? instance.getId() : undef(instance._instanceMeta.name);
      String referenceKey = ("logical".equals(instance._instanceMeta.sdKind) ? "Binary"
          : Js.toStr(undef(instance.get("resourceType")))) + "/" + Js.toStr(idOrName);
      JsObject configResource = findConfigResource(referenceKey);
      if (configResource == null || !isTrue(configResource.get("omit"))) {
        addPackageResource(referenceKey, instance, configResource);
      }
    }
  }

  private JsObject findConfigResource(String referenceKey) {
    if (config.resources == null) return null;
    for (JsObject resource : config.resources) {
      if (Objects.equals(refOf(resource), referenceKey)) return resource;
    }
    return null;
  }

  private static String resourceType(FhirResource r) {
    if (r instanceof StructureDefinition sd) return sd.getResourceType();
    if (r instanceof Map<?, ?> m) return str(m.get("resourceType"));
    return null;
  }

  private static String id(FhirResource r) {
    if (r instanceof StructureDefinition sd) return sd.getId();
    if (r instanceof Map<?, ?> m) return str(m.get("id"));
    return null;
  }

  private static Object sdOrMapProp(FhirResource r, String key) {
    if (r instanceof StructureDefinition sd) {
      return switch (key) {
        case "title" -> sd.getTitle();
        case "name" -> sd.getName();
        case "description" -> sd.getDescription();
        case "version" -> sd.getVersion();
        case "id" -> sd.getId();
        default -> nn(sd.getProperty(key));
      };
    }
    if (r instanceof Map<?, ?> m) return nn(m.get(key));
    return null;
  }

  private static List<Object> extensionsOf(Map<String, Object> configResource) {
    return configResource == null ? null : list(configResource.get("extension"));
  }

  private static boolean anyExtensionUrl(List<Object> exts, java.util.function.Predicate<Object> test) {
    if (exts == null) return false;
    for (Object e : exts) {
      Map<String, Object> m = map(e);
      if (m != null && test.test(nn(m.get("url")))) return true;
    }
    return false;
  }

  private void addPackageResource(String referenceKey, FhirResource pkgResource, JsObject configResource) {
    JsObject newResource = new JsObject();
    JsObject reference = new JsObject();
    reference.put("reference", referenceKey);
    newResource.put("reference", reference);
    Object cfgName = configResource == null ? null : nn(configResource.get("name"));
    Object cfgDescription = configResource == null ? null : nn(configResource.get("description"));
    if (pkgResource instanceof InstanceDefinition inst) {
      Object name = cfgName;
      if (name == null) name = inst._instanceMeta.title;
      if (name == null) name = stringOrElse(nn(inst.get("title")));
      if (name == null) name = inst._instanceMeta.name;
      newResource.put("name", undef(name));
      Object description = cfgDescription;
      if (description == null) description = inst._instanceMeta.description;
      if (description == null) description = stringOrElse(nn(inst.get("description")));
      newResource.put("description", undef(description));
      newResource.put("_linkRef", undef(inst.getId()));
    } else {
      Object name = cfgName;
      if (name == null) name = sdOrMapProp(pkgResource, "title");
      if (name == null) name = sdOrMapProp(pkgResource, "name");
      if (name == null) name = sdOrMapProp(pkgResource, "id");
      newResource.put("name", undef(name));
      Object description = cfgDescription != null ? cfgDescription : sdOrMapProp(pkgResource, "description");
      newResource.put("description", undef(description));
      newResource.put("_linkRef", undef(sdOrMapProp(pkgResource, "name")));
    }
    List<Object> cfgFhirVersion = configResource == null ? null : list(configResource.get("fhirVersion"));
    if (cfgFhirVersion != null && !cfgFhirVersion.isEmpty()) {
      newResource.put("fhirVersion", cfgFhirVersion);
    }
    Object groupingId = configResource == null ? null : nn(configResource.get("groupingId"));
    if (Js.truthy(groupingId)) {
      newResource.put("groupingId", groupingId);
      addGroup(str(groupingId));
    }
    Object exampleCanonical = configResource == null ? null : nn(configResource.get("exampleCanonical"));
    Object exampleBoolean = configResource == null ? null : nn(configResource.get("exampleBoolean"));
    if (Js.truthy(exampleCanonical)) {
      newResource.put("exampleCanonical", exampleCanonical);
    } else if (exampleBoolean instanceof Boolean b) {
      newResource.put("exampleBoolean", b);
    } else if (pkgResource instanceof InstanceDefinition inst && "Example".equals(inst._instanceMeta.usage)) {
      // so here's where we set exampleCanonical
      // but we need to be able to set it without there being anything in meta.profile
      List<String> candidates = new ArrayList<>();
      Map<String, Object> meta = map(inst.get("meta"));
      List<Object> metaProfileUrls = meta == null ? null : list(meta.get("profile"));
      if (metaProfileUrls != null) for (Object u : metaProfileUrls) candidates.add(Js.toStr(u));
      candidates.add(inst._instanceMeta.instanceOfUrl != null ? inst._instanceMeta.instanceOfUrl : "");
      String exampleUrl = null;
      for (String url : candidates) {
        String[] parts = splitPipe(url);
        String baseUrl = parts[0];
        String version = parts[1];
        FhirResource availableProfileOrLogical = pkg.fish(baseUrl, FishableType.Profile, FishableType.Logical);
        if (availableProfileOrLogical != null) {
          Object v = sdOrMapProp(availableProfileOrLogical, "version");
          Object effective = v != null ? v : config.version;
          if (version == null || version.equals(effective)) {
            exampleUrl = url;
            break;
          }
        }
      }
      if (Js.truthy(exampleUrl)) {
        newResource.put("exampleCanonical", splitPipe(exampleUrl)[0]);
      } else {
        newResource.put("exampleBoolean", true);
      }
    } else {
      newResource.put("exampleBoolean", false);
    }
    List<Object> cfgExtensions = extensionsOf(configResource);
    if (cfgExtensions != null && !cfgExtensions.isEmpty()) {
      newResource.put("extension", cfgExtensions);
    }
    if (pkgResource instanceof InstanceDefinition inst && "logical".equals(inst._instanceMeta.sdKind)) {
      if (!anyExtensionUrl(cfgExtensions, IG_RESOURCE_FORMAT_EXTENSIONS::contains)) {
        // Logical instances should add a special extension.
        // See: https://fshschool.org/docs/sushi/tips/#instances-of-logical-models
        List<Object> ext = list(nn(newResource.get("extension")));
        if (ext == null) {
          ext = new ArrayList<>();
          newResource.put("extension", ext);
        }
        JsObject e = new JsObject();
        e.put("url", CURRENT_RESOURCE_FORMAT_EXTENSION);
        e.put("valueCode", "application/fhir+json");
        ext.add(e);
      } else if (anyExtensionUrl(cfgExtensions, DEPRECATED_RESOURCE_FORMAT_EXTENSION::equals)) {
        String who = refOf(configResource) != null ? refOf(configResource) : Js.toStr(newResource.get("name"));
        FshLogger.logger.warn("The extension " + DEPRECATED_RESOURCE_FORMAT_EXTENSION + " has been deprecated. "
            + "Update the configuration for " + who + " to use the current extension, "
            + CURRENT_RESOURCE_FORMAT_EXTENSION + ".");
      }
    }
    resources().add(newResource);
  }

  /** url.split('|', 2): [base, version-or-null]. */
  private static String[] splitPipe(String url) {
    int i = url.indexOf('|');
    if (i < 0) return new String[] {url, null};
    int j = url.indexOf('|', i + 1);
    return new String[] {url.substring(0, i), j < 0 ? url.substring(i + 1) : url.substring(i + 1, j)};
  }

  private static final Pattern VIRTUAL_PREFIX = Pattern.compile("^virtual:[^#]+#[^:]+:");

  private void addPredefinedResources() {
    List<JsObject> configuredBinaryResources = new ArrayList<>();
    if (config.resources != null) {
      for (JsObject resource : config.resources) {
        String ref = refOf(resource);
        if (ref != null && ref.startsWith("Binary/")
            && anyExtensionUrl(extensionsOf(resource), IG_RESOURCE_FORMAT_EXTENSIONS::contains)) {
          configuredBinaryResources.add(resource);
        }
      }
    }

    List<String> localResourcePaths = PredefinedResources.getPredefinedResourcePaths(
        NodePath.join(inputPath, "input"), inputPath, config.parameters);
    List<ResourceInfo> predefinedResourceInfos = fhirDefs.findResourceInfos("*", FindResourceInfoOptions.NONE
        .withScope(PredefinedResources.PREDEFINED_PACKAGE_NAME)
        .withSort(List.of(FindResourceInfoOptions.byLoadOrder(true)))); // FIFO order to match previous SUSHI
    List<Object> predefinedResources = new ArrayList<>();
    for (Object r : fhirDefs.allPredefinedResources()) predefinedResources.add(Js.cloneDeep(r));
    List<String> deeplyNestedFiles = new ArrayList<>();
    for (int i = 0; i < predefinedResources.size(); i++) {
      // Since virtual resources start with 'virtual:{package}#{version}:', remove that part
      String file = VIRTUAL_PREFIX.matcher(predefinedResourceInfos.get(i).resourcePath).replaceFirst("");
      // If it's deeply nested, do not include it in the resource list
      if (!localResourcePaths.contains(NodePath.dirname(file))) {
        deeplyNestedFiles.add(file);
        continue;
      }
      Map<String, Object> resourceJSON = map(predefinedResources.get(i));
      if (resourceJSON == null) continue;
      String rjId = str(resourceJSON.get("id"));
      String rjType = str(resourceJSON.get("resourceType"));
      // For predefined examples of Logical Models, the user must provide an entry in config
      // that specifies the reference as Binary/[id], the extension that specifies the resource format,
      // and the exampleCanonical that references the LogicalModel the resource is an example of.
      // Note: the exampleCanonical should reference the resourceType because the resourceType should
      // be the absolute URL, but we previously supported using the logical model's id as the example's
      // resourceType, so support having an exampleCanonical in either form for now.
      // In that case, we do not want to add our own entry for the predefined resource - we just
      // want to use the resource entry from the sushi-config.yaml
      // For predefined examples of Logical Models that do not have a resourceType or id,
      // a Binary resource reference based on the file name can be used, based on Zulip:
      // https://chat.fhir.org/#narrow/stream/215610-shorthand/topic/How.20do.20I.20get.20SUSHI.20to.20ignore.20a.20binary.20JSON.20logical.20instance.3F/near/407861211
      JsObject configuredBinaryReference = null;
      for (JsObject resource : configuredBinaryResources) {
        String ref = refOf(resource);
        Object ec = nn(resource.get("exampleCanonical"));
        boolean byId = Objects.equals(ref, "Binary/" + Js.toStr(undef(rjId)))
            && (Objects.equals(ec, Js.toStr(undef(config.canonical)) + "/StructureDefinition/"
                + Js.toStr(undef(rjType))) || Objects.equals(ec, rjType));
        if (byId || Objects.equals(ref, "Binary/" + NodePath.parseName(file))) {
          configuredBinaryReference = resource;
          break;
        }
      }

      if (configuredBinaryReference != null) {
        if (anyExtensionUrl(extensionsOf(configuredBinaryReference), DEPRECATED_RESOURCE_FORMAT_EXTENSION::equals)) {
          String who = refOf(configuredBinaryReference) != null ? refOf(configuredBinaryReference)
              : Js.toStr(undef(configuredBinaryReference.get("name")));
          FshLogger.logger.warn("The extension " + DEPRECATED_RESOURCE_FORMAT_EXTENSION + " has been deprecated. "
              + "Update the configuration for " + who + " to use the current extension, "
              + CURRENT_RESOURCE_FORMAT_EXTENSION + ".");
        }
        continue;
      }

      if (nn(resourceJSON.get("resourceType")) == null || nn(resourceJSON.get("id")) == null) {
        boolean noType = nn(resourceJSON.get("resourceType")) == null;
        boolean noId = nn(resourceJSON.get("id")) == null;
        FshLogger.logger.warn("Resource at " + file + " is missing " + (noType ? "resourceType" : "")
            + (noType && noId ? " and " : "") + (noId ? "id" : "") + ".");
        continue;
      }

      String referenceKey = Js.toStr(resourceJSON.get("resourceType")) + "/" + Js.toStr(resourceJSON.get("id"));
      JsObject newResource = new JsObject();
      JsObject reference = new JsObject();
      reference.put("reference", referenceKey);
      newResource.put("reference", reference);
      JsObject configResource = findConfigResource(referenceKey);

      if (configResource == null || !isTrue(configResource.get("omit"))) {
        int existingIndex = -1;
        for (int k = 0; k < resources().size(); k++) {
          if (Objects.equals(refOf(map(resources().get(k))), referenceKey)) {
            existingIndex = k;
            break;
          }
        }
        // If the user has provided a resource, it should override the generated resource.
        // This can be helpful for working around cases where the generated resource has some incorrect values.
        Map<String, Object> existingResource = existingIndex >= 0 ? map(resources().get(existingIndex)) : null;
        boolean existingIsExample = existingResource != null
            && (Js.truthy(existingResource.get("exampleBoolean")) || Js.truthy(existingResource.get("exampleCanonical")));
        Object existingName = existingIsExample ? nn(existingResource.get("name")) : null;
        Object existingDescription = existingIsExample ? nn(existingResource.get("description")) : null;

        Object metaExtensionDescription = getMetaExtensionDescription(resourceJSON);
        Object metaExtensionName = getMetaExtensionName(resourceJSON);
        Object cfgName = configResource == null ? null : nn(configResource.get("name"));
        Object cfgDescription = configResource == null ? null : nn(configResource.get("description"));
        // On some resources (Patient for example) title, name, and description can be objects, avoid using them
        Object description = firstNonNull(cfgDescription, metaExtensionDescription, existingDescription,
            stringOrElse(nn(resourceJSON.get("description"))));
        newResource.put("description", undef(description));
        Object cfgFhirVersion = configResource == null ? null : nn(configResource.get("fhirVersion"));
        if (Js.truthy(cfgFhirVersion)) {
          newResource.put("fhirVersion", cfgFhirVersion);
        }
        Object groupingId = configResource == null ? null : nn(configResource.get("groupingId"));
        if (Js.truthy(groupingId)) {
          newResource.put("groupingId", groupingId);
          addGroup(str(groupingId));
        }
        Object exampleCanonical = configResource == null ? null : nn(configResource.get("exampleCanonical"));
        Object exampleBoolean = configResource == null ? null : nn(configResource.get("exampleBoolean"));
        if (NodePath.basename(NodePath.dirname(file)).equals("examples")) {
          newResource.put("name", undef(firstNonNull(cfgName, metaExtensionName, existingName,
              stringOrElse(nn(resourceJSON.get("title"))), stringOrElse(nn(resourceJSON.get("name"))),
              nn(resourceJSON.get("id")))));
          newResource.put("_linkRef", resourceJSON.get("id"));
          // set exampleCanonical or exampleBoolean, preferring configured values
          if (Js.truthy(exampleCanonical)) {
            newResource.put("exampleCanonical", exampleCanonical);
          } else if (exampleBoolean instanceof Boolean b) {
            newResource.put("exampleBoolean", b);
          } else {
            String exampleUrl = null;
            Map<String, Object> meta = map(resourceJSON.get("meta"));
            List<Object> profiles = meta == null ? null : list(meta.get("profile"));
            if (profiles != null) {
              for (Object u : profiles) {
                String url = Js.toStr(u);
                String[] parts = splitPipe(url);
                String baseUrl = parts[0];
                String version = parts[1];
                Object availableVersion;
                boolean available;
                FhirResource fromPkg = pkg.fish(baseUrl, FishableType.Profile);
                if (fromPkg != null) {
                  available = true;
                  availableVersion = sdOrMapProp(fromPkg, "version");
                } else {
                  Map<String, Object> fromDefs = map(fhirDefs.fishForFHIR(baseUrl, FishableType.Profile));
                  available = fromDefs != null;
                  availableVersion = fromDefs == null ? null : nn(fromDefs.get("version"));
                }
                Object effective = availableVersion != null ? availableVersion : config.version;
                if (available && (version == null || version.equals(effective))) {
                  exampleUrl = url;
                  break;
                }
              }
            }
            if (Js.truthy(exampleUrl)) {
              newResource.put("exampleCanonical", splitPipe(exampleUrl)[0]);
            } else {
              newResource.put("exampleBoolean", true);
            }
          }
        } else {
          if (Js.truthy(exampleCanonical)) {
            newResource.put("exampleCanonical", exampleCanonical);
          } else if (exampleBoolean instanceof Boolean b) {
            newResource.put("exampleBoolean", b);
          } else {
            newResource.put("exampleBoolean", false);
          }
          newResource.put("name", undef(firstNonNull(cfgName, metaExtensionName,
              existingResource == null ? null : nn(existingResource.get("name")),
              stringOrElse(nn(resourceJSON.get("title"))), stringOrElse(nn(resourceJSON.get("name"))),
              nn(resourceJSON.get("id")))));
          newResource.put("_linkRef", undef(firstNonNull(stringOrElse(nn(resourceJSON.get("name"))),
              nn(resourceJSON.get("id")))));
        }
        List<Object> cfgExtensions = extensionsOf(configResource);
        if (cfgExtensions != null && !cfgExtensions.isEmpty()) {
          newResource.put("extension", cfgExtensions);
        }

        if (existingIndex >= 0) {
          resources().set(existingIndex, newResource);
        } else {
          resources().add(newResource);
        }
      }
    }
    if (!deeplyNestedFiles.isEmpty()) {
      FshLogger.logger.warn("The following files were not added to the ImplementationGuide JSON because they are not "
          + "in one of the supported input folders or are nested too deep in one of those folders. While SUSHI "
          + "automatically supports resources in sub-folders, the IG Publisher does not, unless the folder is "
          + "explicitly added via the template or an IG parameter. To fix any issues you may encounter due to this, "
          + "adjust your IG parameters or template accordingly or move these files so they are directly under a "
          + "supported input folder (e.g., input/resources, input/profiles, etc.):\n  - "
          + String.join("\n  - ", deeplyNestedFiles));
    }
  }

  private static Object firstNonNull(Object... values) {
    for (Object v : values) {
      if (v != null && v != Js.UNDEFINED) return v;
    }
    return null;
  }

  /**
   * Adds resources that are present only in the configuration. If a configured resource is already in the IG, there
   * is no need to add it again.
   */
  private void addConfiguredResources() {
    if (config.resources == null) return;
    for (JsObject resource : config.resources) {
      if (!Js.truthy(resource.get("omit"))) {
        boolean present = false;
        for (Object r : resources()) {
          if (Objects.equals(refOf(resource), refOf(map(r)))) {
            present = true;
            break;
          }
        }
        if (!present) resources().add(resource);
      }
    }
  }

  /**
   * Sort the IG resources based on the configuration: the "resources" order if all are listed there, else the
   * "groups" order if all are listed there, else by name (reference.reference as backup).
   */
  private void sortResources() {
    if (!(trySortResourcesByConfig() || trySortResourcesByGroup())) {
      sortBy(resources(), o -> {
        Map<String, Object> resource = map(o);
        Object name = nn(resource.get("name"));
        Object key = name != null ? name : refOf(resource);
        return key == null ? null : Js.toStr(key).toUpperCase(Locale.ROOT);
      });
    }
  }

  /** lodash sortBy (stable, ascending, undefined/null keys last) with one iteratee, in place. */
  private static <K extends Comparable<K>> void sortBy(List<Object> list, Function<Object, K> key) {
    List<Object> sorted = new ArrayList<>(list);
    Map<Object, K> keys = new java.util.IdentityHashMap<>();
    for (Object o : sorted) keys.put(o, key.apply(o));
    sorted.sort((a, b) -> {
      K ka = keys.get(a), kb = keys.get(b);
      if (ka == null) return kb == null ? 0 : 1;
      if (kb == null) return -1;
      return ka.compareTo(kb);
    });
    list.clear();
    list.addAll(sorted);
  }

  private boolean trySortResourcesByConfig() {
    if (config.resources == null) {
      return false;
    }
    Map<String, Integer> resourceIndices = new LinkedHashMap<>();
    boolean allInConfig = true;
    for (Object o : resources()) {
      String ref = refOf(map(o));
      if (ref == null) {
        allInConfig = false;
        break;
      }
      int configIndex = -1;
      for (int i = 0; i < config.resources.size(); i++) {
        if (Objects.equals(refOf(config.resources.get(i)), ref)) {
          configIndex = i;
          break;
        }
      }
      if (configIndex >= 0) {
        resourceIndices.put(ref, configIndex);
      } else {
        allInConfig = false;
        break;
      }
    }
    if (allInConfig) {
      sortBy(resources(), o -> resourceIndices.get(refOf(map(o))));
    }
    return allInConfig;
  }

  private boolean trySortResourcesByGroup() {
    if (config.groups == null) {
      return false;
    }
    Map<String, int[]> resourceIndices = new LinkedHashMap<>();
    boolean allInGroups = true;
    for (Object o : resources()) {
      String ref = refOf(map(o));
      if (ref == null) {
        allInGroups = false;
        break;
      }
      int resourceIndex = -1;
      int groupIndex = -1;
      for (int g = 0; g < config.groups.size(); g++) {
        List<String> rs = config.groups.get(g).resources;
        resourceIndex = rs == null ? -1 : rs.indexOf(ref);
        if (resourceIndex >= 0) {
          groupIndex = g;
          break;
        }
      }
      if (groupIndex >= 0) {
        resourceIndices.put(ref, new int[] {groupIndex, resourceIndex});
      } else {
        allInGroups = false;
        break;
      }
    }
    if (allInGroups) {
      List<Object> sorted = new ArrayList<>(resources());
      sorted.sort(Comparator.comparingInt((Object o) -> resourceIndices.get(refOf(map(o)))[0])
          .thenComparingInt(o -> resourceIndices.get(refOf(map(o)))[1]));
      resources().clear();
      resources().addAll(sorted);
    }
    return allInGroups;
  }

  /** Adds or updates a group in the implementation guide. */
  private void addGroup(String id) {
    addGroup(id, id, null, false);
  }

  private void addGroup(String id, String name, String description, boolean nameGiven) {
    if (!Js.truthy(definition().get("grouping"))) {
      definition().put("grouping", new ArrayList<Object>());
    }
    // Initially `name` and `id` were derived from the same parameter. This allows
    // the function to still work with just a single parameter.
    Object groupName = nameGiven ? undef(name) : undef(id);
    List<Object> grouping = list(definition().get("grouping"));
    Map<String, Object> existingGroup = null;
    for (Object g : grouping) {
      if (Objects.equals(nn(map(g).get("id")), id)) {
        existingGroup = map(g);
        break;
      }
    }
    if (existingGroup != null) {
      existingGroup.put("name", groupName);
      if (Js.truthy(description)) {
        existingGroup.put("description", description);
      }
    } else {
      JsObject g = new JsObject();
      g.put("id", undef(id));
      g.put("name", groupName);
      if (Js.truthy(description)) g.put("description", description);
      grouping.add(g);
    }
  }

  /**
   * Adds groups listed in the configuration to the IG and updates the groupingId of their member resources; errors
   * for nonexistent resources and conflicting groupingIds, a warning for a redundant groupingId.
   */
  private void addConfiguredGroups() {
    if (config.groups == null) return;
    for (ConfigurationGroup group : config.groups) {
      // TS passes group.name (possibly undefined) explicitly, so name is not defaulted to the id
      addGroup(group.id, group.name, group.description, group.name != null);
      if (group.resources != null) {
        for (String resourceKey : group.resources) {
          Map<String, Object> existingResource = null;
          for (Object r : resources()) {
            if (Objects.equals(refOf(map(r)), resourceKey)) {
              existingResource = map(r);
              break;
            }
          }
          if (existingResource == null) {
            FshLogger.logger.error("Group " + group.id + " configured with nonexistent resource " + resourceKey);
          } else {
            Object existingGroupingId = nn(existingResource.get("groupingId"));
            if (Js.truthy(existingGroupingId)) {
              if (Objects.equals(existingGroupingId, group.id)) {
                FshLogger.logger.warn("Resource " + resourceKey + " is listed as a member of group " + group.id
                    + ", and does not need a groupingId.");
              } else {
                FshLogger.logger.error("Resource " + resourceKey + " configured with groupingId "
                    + Js.toStr(existingGroupingId) + ", but listed as member of group " + group.id + ".");
              }
            }
            existingResource.put("groupingId", undef(group.id));
          }
        }
      }
    }
  }

  private static Object metaExtension(Map<String, Object> resource, String url, String valueKey) {
    Map<String, Object> meta = map(resource.get("meta"));
    List<Object> exts = meta == null ? null : list(meta.get("extension"));
    if (exts == null) return null;
    for (Object e : exts) {
      Map<String, Object> m = map(e);
      if (m != null && url.equals(m.get("url"))) return nn(m.get(valueKey));
    }
    return null;
  }

  private Object getMetaExtensionDescription(Map<String, Object> resource) {
    Object description = metaExtension(resource,
        "http://hl7.org/fhir/StructureDefinition/instance-description", "valueMarkdown");
    if (!Common.CONFORMANCE_AND_TERMINOLOGY_RESOURCES.contains(str(resource.get("resourceType")))) {
      return description;
    }
    return null;
  }

  private Object getMetaExtensionName(Map<String, Object> resource) {
    Object name = metaExtension(resource, "http://hl7.org/fhir/StructureDefinition/instance-name", "valueString");
    if (!Common.CONFORMANCE_AND_TERMINOLOGY_RESOURCES.contains(str(resource.get("resourceType")))) {
      return name;
    }
    return null;
  }

  private static final Pattern REF_CHARS = Pattern.compile("[/:]");

  private String normalizeResourceReference(String resource, boolean useRelative) {
    String ref = null;
    // If it doesn't contain / or :, then it's not a relative URL or canonical yet
    if (!REF_CHARS.matcher(Js.toStr(undef(resource))).find()) {
      Map<String, Object> def = map(pkg.fishForFHIR(resource));
      if (def == null) {
        def = map(fhirDefs.fishForFHIR(resource));
      }
      if (useRelative && def != null && Js.truthy(def.get("resourceType")) && Js.truthy(def.get("id"))) {
        ref = Js.toStr(def.get("resourceType")) + "/" + Js.toStr(def.get("id"));
      } else if (def != null && Js.truthy(def.get("url"))) {
        ref = Js.toStr(def.get("url"));
      }
      if (ref == null) {
        FshLogger.logger.warn("Cannot determine " + (useRelative ? "relative URL" : "canonical") + " for \""
            + resource + "\" referenced in sushi-config.yaml.");
      }
    }
    // Fallback to resource if we didn't assign a fullRef
    return ref != null ? ref : resource;
  }

  /** Writes the in-memory ImplementationGuide JSON to the IG output folder. */
  public void addImplementationGuide(String igPath) {
    String igJSONFolder = NodePath.join("fsh-generated", "resources");
    String idStr = Js.toStr(ig.get("id") == null ? Js.UNDEFINED : ig.get("id"));
    String igJsonPath = NodePath.join(igPath, igJSONFolder,
        SanitizeFilename.sanitize("ImplementationGuide-" + idStr + ".json", "-"));
    Processing.outputJson(igJsonPath, ig);
    FshLogger.logger.info("Generated ImplementationGuide-" + idStr + ".json");
  }

  // ---------------------------------------------------------------------------------------------- ig.ini

  /** Logs an error if the no longer supported "template" property is used in sushi-config.yaml. */
  public void checkIgIni() {
    String inputIniPath = NodePath.join(inputPath, "ig.ini");
    if (config.template != null) {
      if (Processing.exists(inputIniPath)) {
        String filePathString = NodePath.join(NodePath.basename(inputPath), "ig.ini");
        String message = "Found both a \"template\" property in " + configName + " and an ig.ini file at "
            + filePathString + ". The \"template\" property in " + configName + " is no longer supported and will be "
            + "ignored. The existing " + filePathString + " file will be used instead. To resolve this error, remove "
            + "the \"template\" property in " + configName + " and manage the ig.ini file directly.";
        SourceInfo src = new SourceInfo();
        src.file = inputIniPath;
        FshLogger.logger.error(message, src);
      } else {
        String message = "The \"template\" property in " + configName + " is no longer supported. Please remove the "
            + "\"template\" property in " + configName + " and manage the ig.ini file directly."
            + " To resolve this error, create an ig.ini file in your project folder with the following contents:\n\n"
            + generateIgIniString();
        FshLogger.logger.error(message);
      }
    } else if (Processing.exists(inputIniPath)) {
      processIgIni(inputIniPath);
    } else {
      // do nothing -- no template in config, no ig.ini provided
    }
  }

  private static final Pattern TEMPLATE_ESCAPED = Pattern.compile("^template\\s*=\\s*.*?(\\\\#.+)?$",
      Pattern.MULTILINE);
  private static final Pattern TEMPLATE_HASH = Pattern.compile("^\\s*template\\s*=\\s*[^#]*(#.+)?$",
      Pattern.MULTILINE);

  /** Generates the contents of an ig.ini file using the information in the configuration. */
  public String generateIgIniString() {
    // Create an ig.ini object from the configuration
    Map<String, Object> iniObj = new LinkedHashMap<>();
    String igFolder = "fsh-generated/resources";
    iniObj.put("ig", igFolder + "/ImplementationGuide-" + Js.toStr(undef(config.id)) + ".json");
    iniObj.put("template", undef(config.template));
    String outputIniContents = Ini.encode(iniObj, "IG", true);

    // The encoder escapes '#' but FHIR doesn't like that, so if `#` is escaped in the template, then unescape it.
    outputIniContents = replaceFirstJs(TEMPLATE_ESCAPED, outputIniContents,
        (m0, m1) -> m1 != null ? m0.replace(m1, m1.substring(1)) : m0);
    return outputIniContents;
  }

  /** JS str.replace(regex-without-g, fn): replace the first match only. Java's multiline $ is fine here. */
  private static String replaceFirstJs(Pattern p, String s, java.util.function.BinaryOperator<String> fn) {
    java.util.regex.Matcher m = jsMultiline(p).matcher(s);
    if (!m.find()) return s;
    return s.substring(0, m.start()) + fn.apply(m.group(), m.group(1)) + s.substring(m.end());
  }

  /** JS /m: ^ and $ at \n, \r,  ,   (Java MULTILINE also stops at \r\n as one break; close enough). */
  private static Pattern jsMultiline(Pattern p) {
    return p;
  }

  /**
   * Process an existing ig.ini. When one of the required properties is missing, an error is logged but the file is
   * not changed.
   */
  public void processIgIni(String inputIniPath) {
    String inputIniContents = Processing.readFile(inputIniPath);
    // FHIR allows templates to have versions identified using #.  E.g.,
    //   template = hl7.fhir.template#0.1.0
    // The ini library, however, treats # as a comment unless it is escaped.  So if it exists, we need to escape it.
    inputIniContents = replaceFirstJs(TEMPLATE_HASH, inputIniContents,
        (m0, m1) -> m1 != null ? m0.replace(m1, "\\" + m1) : m0);
    Map<String, Object> inputIni = Ini.parse(inputIniContents);
    SourceInfo src = new SourceInfo();
    src.file = inputIniPath;
    if (inputIni.get("IG") == null) {
      FshLogger.logger.error("The ig.ini file must contain an [IG] section.", src);
    }
    String filePathString = NodePath.join(NodePath.basename(inputPath), "ig.ini");
    Object igSection = inputIni.get("IG");
    if (Js.truthy(igSection)) {
      Map<String, Object> igIni = igSection instanceof Map<?, ?> ? map(igSection) : new LinkedHashMap<>();
      String igValue = "fsh-generated/resources/ImplementationGuide-" + Js.toStr(undef(config.id)) + ".json";
      if (igIni.get("ig") == null) {
        igIni.put("ig", igValue);
        FshLogger.logger.error("The ig.ini file must have an \"ig\" property pointing to the IG file. Please add the "
            + "following line to " + filePathString + ":\n'" + "ig = " + igValue, src);
      } else if (!Pattern.compile("(.*[/\\\\])?fsh-generated[/\\\\]resources[/\\\\]ImplementationGuide-"
          + escapeRegExpForJava(Js.toStr(undef(config.id))) + ".json\\z").matcher(Js.toStr(igIni.get("ig"))).find()) {
        FshLogger.logger.warn("\n\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! IMPORTANT "
            + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n"
            + "Your ig.ini file does NOT point to the Implementation Guide resource that SUSHI generates. As a\n"
            + "result, the IG Publisher will IGNORE the SUSHI-generated ImplementationGuide resource. To fix this,\n"
            + "please do one of the following:\n"
            + "- Update your ig.ini file with 'ig = " + igValue + "' (recommended), or\n"
            + "- Update your sushi-config.yaml file with 'FSHOnly: true' to tell SUSHI NOT to generate the IG "
            + "resource\n\n");
      }
      if (igIni.get("template") == null) {
        String templateValue = "fhir2.base.template";
        igIni.put("template", templateValue);
        FshLogger.logger.error("The ig.ini file must have a \"template\" property. Please update " + filePathString
            + " to include your desired template value. Consider adding the following line:\n"
            + "template = " + templateValue, src);
      }
      List<String> deprecatedProps = new ArrayList<>();
      for (String p : List.of("copyrightyear", "license", "version", "ballotstatus", "fhirspec", "excludexml",
          "excludejson", "excludettl", "excludeMaps")) {
        if (igIni.get(p) != null) deprecatedProps.add(p);
      }
      if (!deprecatedProps.isEmpty()) {
        String propList = String.join(", ", deprecatedProps);
        FshLogger.logger.warn("Your " + filePathString + " file contains the following unsupported properties: "
            + propList + ". These are no longer supported in ig.ini and should be removed.  See the following link "
            + "for details: https://github.com/HL7/ig-template-base/releases/tag/0.0.2", src);
      }
    }
  }

  /** lodash escapeRegExp, for a Java pattern (the escaped characters mean the same in both). */
  private static String escapeRegExpForJava(String s) {
    return Js.escapeRegExp(s);
  }

  /** Logs an error if the no longer supported "history" property is used in sushi-config.yaml. */
  public void checkPackageList() {
    if (config.history != null) {
      FshLogger.logger.error("Detected \"history\" property in configuration. The use of \"history\" is no longer "
          + "supported. Please remove the property and provide a package-list.json directly. "
          + "The package-list.json corresponding to the \"history\" property in the configuration is:\n\n"
          + Json.stringify(historyJs(config.history), 2) + "\n");
    }
  }

  /** The TS history object (keys in configuration order, 'package-id' first). */
  private static JsObject historyJs(Configuration.ConfigurationHistory h) {
    JsObject o = new JsObject();
    o.put("package-id", undef(h.packageId));
    o.put("canonical", undef(h.canonical));
    o.put("title", undef(h.title));
    o.put("introduction", undef(h.introduction));
    if (h.list != null) {
      List<Object> l = new ArrayList<>();
      for (Configuration.ConfigurationHistoryItem i : h.list) {
        JsObject item = new JsObject();
        item.put("version", undef(i.version));
        item.put("date", undef(i.date));
        item.put("desc", undef(i.desc));
        item.put("path", undef(i.path));
        item.put("changes", undef(i.changes));
        item.put("status", undef(i.status));
        item.put("sequence", undef(i.sequence));
        item.put("fhirversion", undef(i.fhirversion));
        item.put("current", undef(i.current));
        l.add(item);
      }
      o.put("list", l);
    }
    return o;
  }

  // ---------------------------------------------------------------------------------------------- R5 / R4

  /** Supports necessary updates for R5 IGs. SUSHI only supports R4 or later, and as of now, we just target R5. */
  public void updateForR5() {
    // Update IG.definition.resource
    for (Object o : resources()) {
      Map<String, Object> resource = map(o);
      // Use IG.definition.resource.isExample
      if (Js.truthy(resource.get("exampleBoolean")) || Js.truthy(resource.get("exampleCanonical"))) {
        resource.put("isExample", true);
        if (Js.truthy(resource.get("exampleCanonical"))) {
          List<Object> profile = new ArrayList<>();
          profile.add(resource.get("exampleCanonical"));
          resource.put("profile", profile);
        }
        resource.remove("exampleBoolean");
        resource.remove("exampleCanonical");
      } else if (Boolean.FALSE.equals(resource.get("exampleBoolean"))) {
        resource.put("isExample", false);
        resource.remove("exampleBoolean");
      }

      // Assign IG.definition.resource.profile if provided
      JsObject configEntry = findConfigResource(refOf(resource));
      if (configEntry != null && nn(configEntry.get("profile")) != null) {
        resource.put("profile", configEntry.get("profile"));
      }
      // Assign IG.definition.resource.isExample if provided
      if (configEntry != null && nn(configEntry.get("isExample")) != null) {
        resource.put("isExample", configEntry.get("isExample"));
      }
    }

    // Update IG.definition.page.name
    JsObject root = pageRoot();
    root.put("name", root.get("nameUrl"));
    root.remove("nameUrl");
    for (Object page : list(root.get("page"))) {
      updatePageNameForR5(map(page), asObjects(config.pages));
    }

    // Add new IG.definition.page.source[x] property
    // this.ig.definition.page is the toc.html page we create
    // All configured pages are at the next level, so start at that level
    for (Object page : list(root.get("page"))) {
      addPageSourceForR5(map(page), asObjects(config.pages));
    }
    // Default IG.definition.page.source[x] on every page if not set
    defaultPageSourceUrlForR5(root);

    // Update IG.definition.parameter
    for (Object o : parameters()) {
      Map<String, Object> parameter = map(o);
      List<String> guideParameterCodes = new ArrayList<>();
      Map<String, Object> cs = map(fhirDefs.fishForFHIR("http://hl7.org/fhir/guide-parameter-code",
          FishableType.CodeSystem));
      if (cs != null && list(cs.get("concept")) != null) {
        for (Object c : list(cs.get("concept"))) guideParameterCodes.add(str(map(c).get("code")));
      }

      String code = str(parameter.get("code"));
      JsObject codeObj = new JsObject();
      codeObj.put("code", undef(code));
      parameter.put("code", codeObj);
      FshCode parsedCode = ParseCodeLexeme.parseCodeLexeme(code);

      if (Js.truthy(parsedCode.code) && Js.truthy(parsedCode.system)) {
        // If we can parse the code and we have a system and a code provided, we should use that.
        codeObj.put("code", parsedCode.code);
        codeObj.put("system", parsedCode.system);
      } else if (guideParameterCodes.stream().anyMatch(c -> Objects.equals(c, code))) {
        // Otherwise, only a code was provided, so we check if it is in the bound VS
        codeObj.put("system", "http://hl7.org/fhir/guide-parameter-code");
      } else {
        // If the code is not in the VS in the R5 IG resource, we default the system based on
        // https://chat.fhir.org/#narrow/stream/179252-IG-creation/topic/Unknown.20FHIRVersion.20code.20'5.2E0.2E0-ballot'/near/298697304
        codeObj.put("system", "http://hl7.org/fhir/tools/CodeSystem/ig-parameters");
      }
    }

    // Add new copyrightLabel property
    if (Js.truthy(config.copyrightLabel)) {
      ig.put("copyrightLabel", config.copyrightLabel);
    }

    // Add new versionAlgorithm property
    if (Js.truthy(config.versionAlgorithmString)) {
      ig.put("versionAlgorithmString", config.versionAlgorithmString);
    } else if (config.versionAlgorithmCoding != null) {
      ig.put("versionAlgorithmCoding", config.versionAlgorithmCoding);
    }

    // Add new dependsOn.reason property
    List<Object> dependsOn = list(ig.get("dependsOn"));
    if (dependsOn != null) {
      for (Object o : dependsOn) {
        Map<String, Object> dependency = map(o);
        Map<String, Object> configDependency = findConfigDependency(dependency.get("packageId"));
        if (configDependency == null) {
          throw new NullPointerException("Cannot read properties of undefined (reading 'reason')");
        }
        if (Js.truthy(configDependency.get("reason"))) {
          dependency.put("reason", configDependency.get("reason"));
        }
      }
    }
  }

  private Map<String, Object> findConfigDependency(Object packageId) {
    if (config.dependencies == null) return null;
    for (JsObject d : config.dependencies) {
      if (Objects.equals(nn(d.get("packageId")), nn(packageId))) return d;
    }
    return null;
  }

  /**
   * If an R4 IG uses any new properties from R5, they are included as extensions (or as the equivalent R4 property).
   * See http://hl7.org/fhir/2022Sep/versions.html#extensions
   */
  public void translateR5PropertiesToR4() {
    for (Object o : resources()) {
      Map<String, Object> resource = map(o);
      // Assign IG.definition.resource.profile to exampleCanonical if it is not set, otherwise add an extension
      JsObject configEntry = findConfigResource(refOf(resource));
      List<Object> cfgProfile = configEntry == null ? null : list(configEntry.get("profile"));
      if (nn(resource.get("exampleCanonical")) == null && cfgProfile != null && !cfgProfile.isEmpty()) {
        resource.put("exampleCanonical", cfgProfile.get(0));
        resource.remove("exampleBoolean");
      }
      if (nn(resource.get("exampleCanonical")) != null && cfgProfile != null
          && cfgProfile.stream().anyMatch(p -> !Objects.equals(p, resource.get("exampleCanonical")))) {
        JsObject ext = new JsObject();
        ext.put("url", "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.resource.profile");
        ext.put("valueCanonical", cfgProfile);
        resource.put("extension", concat(resource.get("extension"), ext));
      }
      // Assign isExample to exampleBoolean if it is set and neither exampleCanonical or exampleBoolean are set.
      if (configEntry != null && nn(configEntry.get("isExample")) != null && nn(resource.get("exampleCanonical")) == null) {
        resource.put("exampleBoolean", configEntry.get("isExample"));
      }
    }

    // Add new IG.definition.page.source[x] property to an extension if it is provided. No need to set a default
    // like R5 needs to. this.ig.definition.page is the toc.html page we create; all configured pages are at the
    // next level, so start at that level
    for (Object page : list(pageRoot().get("page"))) {
      addNameUrlAndPageSourceExtensionForR4(map(page), asObjects(config.pages));
    }

    // Update IG.definition.parameter.code
    for (Object o : parameters()) {
      Map<String, Object> parameter = map(o);
      String code = str(parameter.get("code"));
      FshCode parsedCode = ParseCodeLexeme.parseCodeLexeme(code);

      // If the code is parsed, use just the code portion
      if (Js.truthy(parsedCode.code)) {
        parameter.put("code", parsedCode.code);
      } else {
        parameter.put("code", undef(code));
      }

      // If the system is parsed, add an extension for the full Coding
      if (Js.truthy(parsedCode.system)) {
        JsObject coding = new JsObject();
        coding.put("code", undef(parsedCode.code));
        coding.put("system", parsedCode.system);
        JsObject ext = new JsObject();
        ext.put("url", "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.resource.parameter.code");
        ext.put("valueCoding", coding);
        parameter.put("extension", concat(parameter.get("extension"), ext));
      }
    }

    // Add new copyrightLabel property to an extension if provided
    if (Js.truthy(config.copyrightLabel)) {
      JsObject ext = new JsObject();
      ext.put("url", "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.copyrightLabel");
      ext.put("valueString", config.copyrightLabel);
      ig.put("extension", concat(ig.get("extension"), ext));
    }

    // Add new versionAlgorithm property to an extension if provided
    if (Js.truthy(config.versionAlgorithmString)) {
      JsObject ext = new JsObject();
      ext.put("url", "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.versionAlgorithm");
      ext.put("valueString", config.versionAlgorithmString);
      ig.put("extension", concat(ig.get("extension"), ext));
    } else if (config.versionAlgorithmCoding != null) {
      JsObject ext = new JsObject();
      ext.put("url", "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.versionAlgorithm");
      ext.put("valueCoding", config.versionAlgorithmCoding);
      ig.put("extension", concat(ig.get("extension"), ext));
    }

    // Add new dependsOn.reason property
    List<Object> dependsOn = list(ig.get("dependsOn"));
    if (dependsOn != null) {
      for (Object o : dependsOn) {
        Map<String, Object> dependency = map(o);
        Map<String, Object> configDependency = findConfigDependency(dependency.get("packageId"));
        if (configDependency != null && Js.truthy(configDependency.get("reason"))) {
          JsObject ext = new JsObject();
          ext.put("url", "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.dependsOn.reason");
          ext.put("valueMarkdown", configDependency.get("reason"));
          dependency.put("extension", concat(dependency.get("extension"), ext));
        }
      }
    }
  }

  /** (x ?? []).concat(item): a new array. */
  private static List<Object> concat(Object existing, Object item) {
    List<Object> out = new ArrayList<>();
    List<Object> l = list(nn(existing));
    if (l != null) out.addAll(l);
    out.add(item);
    return out;
  }

  private static String stripExt(String s) {
    return s.replaceFirst("\\.[^.]+\\z", "");
  }

  private static String nameUrlOrName(Map<String, Object> p) {
    Object v = Js.truthy(p.get("nameUrl")) ? p.get("nameUrl") : nn(p.get("name"));
    if (v == null) throw new NullPointerException("Cannot read properties of undefined (reading 'replace')");
    return Js.toStr(v);
  }

  private static Map<String, Object> findConfigPage(List<Object> configPages, String target) {
    if (configPages == null) return null;
    for (Object o : configPages) {
      Map<String, Object> p = map(o);
      if (stripExt(nameUrlOrName(p)).equals(target)) return p;
    }
    return null;
  }

  public void updatePageNameForR5(Map<String, Object> page, List<Object> configPages) {
    Map<String, Object> configPage = findConfigPage(configPages, stripExt(str(page.get("nameUrl"))));
    if (configPage != null && Js.truthy(configPage.get("name"))) {
      page.put("name", configPage.get("name"));
      // Regenerate the title based on the updated name
      Object title = nn(configPage.get("title"));
      page.put("title", title != null ? title
          : TitleCase.titleCase(String.join(" ", Js.words(stripExt(str(page.get("name")))))));
      // The default of sourceUrl will be the old nameUrl (for now).
      // If sourceUrl is included in the configuration, this value will be
      // overwritten in addPageSourceForR5, which is called after this function in updateForR5.
      page.put("sourceUrl", page.get("nameUrl"));
      page.remove("nameUrl");
    } else if (Js.truthy(page.get("nameUrl"))) {
      page.put("name", page.get("nameUrl"));
      page.remove("nameUrl");
    }
    List<Object> sub = list(page.get("page"));
    if (sub != null && !sub.isEmpty()) {
      for (Object subPage : sub) {
        List<Object> cp = configPage == null ? null : list(configPage.get("page"));
        updatePageNameForR5(map(subPage), cp != null ? cp : new ArrayList<>());
      }
    }
  }

  public void addPageSourceForR5(Map<String, Object> page, List<Object> configPages) {
    Map<String, Object> configPage = findConfigPage(configPages, stripExt(str(page.get("name"))));
    if (configPage != null) {
      if (Js.truthy(configPage.get("sourceUrl"))) {
        page.put("sourceUrl", configPage.get("sourceUrl"));
      } else if (Js.truthy(configPage.get("sourceString"))) {
        page.put("sourceString", configPage.get("sourceString"));
      } else if (Js.truthy(configPage.get("sourceMarkdown"))) {
        page.put("sourceMarkdown", configPage.get("sourceMarkdown"));
      }

      List<Object> sub = list(page.get("page"));
      if (sub != null && !sub.isEmpty()) {
        for (Object subPage : sub) {
          addPageSourceForR5(map(subPage), list(configPage.get("page")));
        }
      }
    }
  }

  public void defaultPageSourceUrlForR5(Map<String, Object> page) {
    if (nn(page.get("sourceUrl")) == null && nn(page.get("sourceString")) == null
        && nn(page.get("sourceMarkdown")) == null) {
      page.put("sourceUrl", page.containsKey("name") ? page.get("name") : Js.UNDEFINED);
    }
    List<Object> sub = list(page.get("page"));
    if (sub != null && !sub.isEmpty()) {
      for (Object subPage : sub) {
        defaultPageSourceUrlForR5(map(subPage));
      }
    }
  }

  /** Add an extension for the R5 property IG.definition.page.source[x] if provided in configuration. */
  public void addNameUrlAndPageSourceExtensionForR4(Map<String, Object> page, List<Object> configPages) {
    String sourceExtensionUrl =
        "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.source";
    String nameExtensionUrl =
        "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.name";

    // Find the corresponding configuration page
    // Configuration might use name or nameUrl and will have a filetype at the end that we want to ignore
    Map<String, Object> configPage = findConfigPage(configPages, stripExt(str(page.get("nameUrl"))));
    if (configPage != null) {
      // If nameUrl and name do not match, add the name value to an extension
      if (Js.truthy(configPage.get("name")) && !Objects.equals(nn(configPage.get("nameUrl")), configPage.get("name"))) {
        JsObject ext = new JsObject();
        ext.put("url", nameExtensionUrl);
        ext.put("valueUrl", configPage.get("name"));
        page.put("extension", concat(page.get("extension"), ext));
      }

      if (Js.truthy(configPage.get("sourceUrl"))) {
        // If nameUrl and sourceUrl do not match, add the sourceUrl value to an extension
        if (!Objects.equals(nn(configPage.get("nameUrl")), configPage.get("sourceUrl"))) {
          JsObject ext = new JsObject();
          ext.put("url", sourceExtensionUrl);
          ext.put("valueUrl", configPage.get("sourceUrl"));
          page.put("extension", concat(page.get("extension"), ext));
        }
      } else if (Js.truthy(configPage.get("sourceString"))) {
        JsObject ext = new JsObject();
        ext.put("url", sourceExtensionUrl);
        ext.put("valueString", configPage.get("sourceString"));
        page.put("extension", concat(page.get("extension"), ext));
      } else if (Js.truthy(configPage.get("sourceMarkdown"))) {
        JsObject ext = new JsObject();
        ext.put("url", sourceExtensionUrl);
        ext.put("valueMarkdown", configPage.get("sourceMarkdown"));
        page.put("extension", concat(page.get("extension"), ext));
      }

      List<Object> sub = list(page.get("page"));
      if (sub != null && !sub.isEmpty()) {
        for (Object subPage : sub) {
          addNameUrlAndPageSourceExtensionForR4(map(subPage), list(configPage.get("page")));
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------- warning block

  /**
   * Creates a block of comments indicating that a file is generated and should not be directly edited, allowing for
   * the block comment delimiters to be passed in as well as any extra text to include.
   */
  static String warningBlock(String blockPrefix, String blockPostfix, List<String> extra, boolean blankLineAfter) {
    List<String> a = new ArrayList<>();
    a.add(blockPrefix);
    a.addAll(warningTextArray("", extra));
    a.add(blockPostfix);
    a.add("");
    if (blankLineAfter) {
      a.add("");
    }
    return String.join(EOL, a);
  }

  /** The comment lines of {@link #warningBlock}, each prefixed. */
  static List<String> warningTextArray(String prefix, List<String> extra) {
    int msgLen = 85;
    if (extra != null) for (String e : extra) msgLen = Math.max(msgLen, e.length());
    final int len = msgLen;
    List<String> a = new ArrayList<>();
    java.util.function.BiConsumer<String, Boolean> msg = (text, center) ->
        a.add(prefix + "* " + (center ? Js.pad(text, len) : Js.padEnd(text, len)) + " *");

    a.add(prefix + "*".repeat(msgLen + 4));
    msg.accept("WARNING: DO NOT EDIT THIS FILE", true);
    msg.accept("", false);
    msg.accept("This file is generated by SUSHI. Any edits you make to this file will be overwritten.", false);
    if (extra != null && !extra.isEmpty()) {
      msg.accept("", false);
      extra.forEach(m -> msg.accept(m, false));
    }
    a.add(prefix + "*".repeat(msgLen + 4));
    return a;
  }
}
