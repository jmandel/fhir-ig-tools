package org.hl7.fhir.sushi.importer.config;

import static org.hl7.fhir.sushi.importer.config.JsOps.U;
import static org.hl7.fhir.sushi.importer.config.JsOps.coalesce;
import static org.hl7.fhir.sushi.importer.config.JsOps.entries;
import static org.hl7.fhir.sushi.importer.config.JsOps.get;
import static org.hl7.fhir.sushi.importer.config.JsOps.getOpt;
import static org.hl7.fhir.sushi.importer.config.JsOps.nullish;
import static org.hl7.fhir.sushi.importer.config.JsOps.removeUndefinedValues;
import static org.hl7.fhir.sushi.importer.config.JsOps.spread;
import static org.hl7.fhir.sushi.importer.config.JsOps.templ;
import static org.hl7.fhir.sushi.importer.config.JsOps.truthy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.Common;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.yaml.Yaml;
import org.hl7.fhir.sushi.yaml.YamlError;

// port of sushi/src/import/importConfiguration.ts (SUSHI 3.21.1)
/**
 * Imports sushi-config.yaml (YAML text, or the already-parsed YAML as a JS-model object) into the normalized
 * {@link Configuration}. Logs errors/warnings like TS and throws {@link ConfigurationError} ("Invalid configuration
 * YAML", "Minimal config not met") where TS throws {@code new Error(...)}.
 *
 * <p>The port works on the untyped parsed YAML with JS semantics ({@link JsOps}), first building the configuration
 * object exactly as TS does ({@link #importConfigurationJs}: same keys, key order, values, undefined-valued keys where
 * TS keeps them), then mapping it to the typed {@link Configuration} ({@link ConfigurationJs#fromJs}). Malformed
 * configs on which TS crashes with a TypeError throw {@link JsOps.JsTypeError} with V8's message.
 */
public final class ImportConfiguration {
  private ImportConfiguration() {}

  private static final FshLogger logger = FshLogger.logger;

  // Minimal properties needed for any FSH project
  private static final List<String> MINIMAL_CONFIG_PROPERTIES = List.of("canonical", "fhirVersion");
  // Additional minimal properties needed for an IG-producing project (i.e. FSHOnly === false)
  private static final List<String> MINIMAL_IG_ONLY_PROPERTIES = List.of("id", "name", "status");
  // Allowed properties for FSH Only projects (all other properties are irrelevant)
  private static final List<String> ALLOWED_FSH_ONLY_PROPERTIES = List.of("canonical", "fhirVersion", "version",
      "status", "dependencies", "instanceOptions", "applyExtensionMetadataToRoot", "FSHOnly");

  /** TS importConfiguration(yaml: string, file). */
  public static Configuration importConfiguration(String yaml, String file) {
    return ConfigurationJs.fromJs(importConfigurationJs(yaml, file));
  }

  /** TS importConfiguration(yaml: YAMLConfiguration, file): {@code yaml} is the parsed YAML (JsObject). */
  public static Configuration importConfiguration(Map<String, Object> yaml, String file) {
    return ConfigurationJs.fromJs(importConfigurationJs(yaml, file));
  }

  /** As {@link #importConfiguration(String, String)}, returning the TS configuration object itself. */
  public static JsObject importConfigurationJs(String yaml, String file) {
    Object parsed;
    try {
      parsed = Yaml.parse(yaml);
    } catch (YamlError e) {
      logger.error("Error parsing configuration: " + e.getMessage() + ".", src(file));
      throw new ConfigurationError("Invalid configuration YAML");
    }
    if (!(parsed instanceof Map<?, ?>) && !(parsed instanceof List<?>)) {
      logger.error("Configuration is not a valid YAML object.", src(file));
      throw new ConfigurationError("Invalid configuration YAML");
    }
    if (parsed instanceof List<?>) {
      // typeof [] === 'object': TS goes on and finds none of the minimal properties on the array
      return importConfigurationJs(new JsObject(), file);
    }
    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) parsed;
    return importConfigurationJs(m, file);
  }

  /** As {@link #importConfiguration(Map, String)}, returning the TS configuration object itself. */
  public static JsObject importConfigurationJs(Map<String, Object> yaml, String file) {
    boolean fshOnly = truthy(get(yaml, "FSHOnly"));
    // There are a few properties that are absolutely required if we are to have *any* success at all
    List<String> minimalProperties = new ArrayList<>(MINIMAL_CONFIG_PROPERTIES);
    if (!fshOnly) minimalProperties.addAll(MINIMAL_IG_ONLY_PROPERTIES);
    List<String> missingProperties = minimalProperties.stream().filter(p -> {
      Object v = get(yaml, p);
      return nullish(v) || (v instanceof List<?> l && l.isEmpty());
    }).toList();
    if (!missingProperties.isEmpty()) {
      logger.error("SUSHI minimally requires the following configuration properties to "
          + (fshOnly ? "start processing FSH" : "generate an IG") + ": " + String.join(", ", minimalProperties) + ".",
          src(file));
      throw new ConfigurationError("Minimal config not met");
    }

    JsObject config = new JsObject();
    config.put("filePath", file);
    config.put("canonical", get(yaml, "canonical")); // minimum config property
    config.put("id", get(yaml, "id")); // minimum config property
    config.put("meta", parseMeta(get(yaml, "meta"), file));
    config.put("implicitRules", get(yaml, "implicitRules"));
    config.put("language", parseSimpleCode(get(yaml, "language"), "language", file));
    config.put("text", parseText(get(yaml, "text"), file));
    config.put("contained", get(yaml, "contained"));
    config.put("extension", get(yaml, "extension"));
    config.put("modifierExtension", get(yaml, "modifierExtension"));
    config.put("url", coalesce(get(yaml, "url"),
        templ(get(yaml, "canonical")) + "/ImplementationGuide/" + templ(get(yaml, "id"))));
    config.put("version", normalizeToString(get(yaml, "version"))); // minimum config property
    config.put("name", fshOnly ? get(yaml, "name") : required(get(yaml, "name"), "name", file));
    config.put("title", get(yaml, "title"));
    // Default status to 'draft' on FSHOnly IGs so exported resources inherit the property
    config.put("status", parseCodeWithRequiredValues(
        fshOnly ? coalesce(get(yaml, "status"), "draft")
            : coalesce(required(get(yaml, "status"), "status", file), "draft"),
        List.of("draft", "active", "retired", "unknown"), "status", file));
    config.put("experimental", get(yaml, "experimental"));
    config.put("date", normalizeToString(get(yaml, "date")));
    config.put("publisher", getOpt(getOpt(normalizeToArray(get(yaml, "publisher")), "0"), "name"));
    config.put("contact", parseContact(yaml, file));
    config.put("description", get(yaml, "description"));
    config.put("useContext", parseUsageContext(get(yaml, "useContext"), file));
    config.put("jurisdiction", parseJurisdiction(get(yaml, "jurisdiction"), file));
    config.put("copyright", get(yaml, "copyright"));
    config.put("copyrightLabel", get(yaml, "copyrightLabel"));
    config.put("versionAlgorithmString", get(yaml, "versionAlgorithmString"));
    config.put("versionAlgorithmCoding",
        parseCoding(get(yaml, "versionAlgorithmCoding"), "versionAlgorithmCoding", file));
    config.put("packageId", coalesce(get(yaml, "packageId"), get(yaml, "id")));
    config.put("license", parseSimpleCode(get(yaml, "license"), "license", file));
    config.put("fhirVersion", mapList(normalizeToArray(get(yaml, "fhirVersion")),
        v -> parseSimpleCode(v, "fhirVersion", file))); // minimum config property
    config.put("dependencies", parseDependencies(get(yaml, "dependencies")));
    config.put("global", parseGlobal(get(yaml, "global")));
    config.put("groups", parseGroups(get(yaml, "groups")));
    config.put("definition", parseDefinition(get(yaml, "definition")));
    config.put("resources", parseResources(get(yaml, "resources"), file));
    config.put("pages", parsePages(get(yaml, "pages"), file));
    config.put("parameters", parseParameters(yaml, get(yaml, "FSHOnly"), file));
    config.put("templates", parseTemplates(get(yaml, "templates"), file));
    config.put("template", get(yaml, "template"));
    config.put("menu", parseMenu(get(yaml, "menu")));
    config.put("history", parseHistory(yaml, file));
    config.put("indexPageContent", get(yaml, "indexPageContent"));
    config.put("FSHOnly", coalesce(get(yaml, "FSHOnly"), false));
    config.put("applyExtensionMetadataToRoot", coalesce(get(yaml, "applyExtensionMetadataToRoot"), true));
    config.put("instanceOptions", parseInstanceOptions(get(yaml, "instanceOptions"), file));

    detectPotentialMistakes(yaml);

    // Remove all undefined variables (mainly helpful for test assertions)
    removeUndefinedValues(config);

    if (fshOnly) {
      // If no IG is being generated, emit warning when IG specific properties are used in config
      List<String> unusedProperties =
          Js.ownKeys(yaml).stream().filter(p -> !ALLOWED_FSH_ONLY_PROPERTIES.contains(p)).toList();
      if (!unusedProperties.isEmpty()) {
        logger.warn("The FSHOnly property is set to true, so no output specific to IG creation will be generated. "
            + "The following properties are unused and only relevant for IG creation: "
            + String.join(", ", unusedProperties) + ". Consider removing these properties from sushi-config.yaml.",
            src(file));
      }
    }
    return config;
  }

  static SourceInfo src(String file) {
    return new SourceInfo(file, null);
  }

  private static Object required(Object value, String property, String file) {
    if (nullish(value) || (value instanceof List<?> l && l.isEmpty())) {
      logger.error("Configuration missing required property: " + property, src(file));
    }
    return value;
  }

  private static Object normalizeToString(Object yamlValue) {
    if (nullish(yamlValue)) return U;
    return templ(yamlValue);
  }

  /** undefined for null/undefined, the list itself, or a one-element list. */
  private static Object normalizeToArray(Object yamlValue) {
    if (nullish(yamlValue)) return U;
    if (yamlValue instanceof List<?>) return yamlValue;
    List<Object> l = new ArrayList<>();
    l.add(yamlValue);
    return l;
  }

  /** {@code arr?.map(f)} on a normalizeToArray result. */
  private static Object mapList(Object arr, java.util.function.Function<Object, Object> f) {
    if (nullish(arr)) return U;
    List<Object> out = new ArrayList<>();
    for (Object v : (List<?>) arr) out.add(f.apply(v));
    return out;
  }

  /** {@code arr.map(f)} without the optional chain: TypeError when arr is undefined. */
  private static List<Object> mapListRequired(Object arr, java.util.function.Function<Object, Object> f) {
    if (nullish(arr)) throw new JsOps.JsTypeError("Cannot read properties of undefined (reading 'map')");
    List<Object> out = new ArrayList<>();
    for (Object v : (List<?>) arr) out.add(f.apply(v));
    return out;
  }

  private static Object parseSimpleCode(Object yamlCode, String property, String file) {
    if (nullish(yamlCode)) return yamlCode;
    String s = JsOps.requireStringForCall(yamlCode, "yamlCode.startsWith");
    if (s.startsWith("#")) {
      FshCode c = parseFshCode(s, property, file);
      return c == null ? U : c.code;
    }
    return s;
  }

  private static final String WS = Common.JS_WS;
  private static final String NOT_WS = "[^\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F"
      + "\\u205F\\u3000\\uFEFF]";
  /** JS {@code .}: any UTF-16 unit but line terminators */
  private static final String DOT = "[^\\n\\r\\u2028\\u2029]";
  // /^(.*\S)(\s+"(([^"]|\\")*)")$/
  private static final Pattern CODE_WITH_DISPLAY =
      Pattern.compile("(" + DOT + "*" + NOT_WS + ")(" + WS + "+\"(([^\"]|\\\\\")*)\")");

  private static FshCode parseFshCode(String yamlCode, String property, String file) {
    // If it has a display, we need to split it out and handle it separately
    Matcher m = CODE_WITH_DISPLAY.matcher(yamlCode);
    if (m.matches()) {
      FshCode concept = parseCodeLexeme(m.group(1));
      concept.display = m.group(3).replace("\\\"", "\"");
      return concept;
    }
    FshCode concept = parseCodeLexeme(yamlCode);
    if (concept.system == null && concept.code.isEmpty()) {
      logger.error("Invalid code format for " + property + ": " + yamlCode, src(file));
      // don't return an invalid code
      return null;
    }
    return concept;
  }

  private static final Pattern CODE_SPLIT = Pattern.compile("(^|[^\\\\])(\\\\\\\\)*#");

  /** Same as {@code importer.ParseCodeLexeme.parseCodeLexeme} (WP-2); kept local until that lands on main. */
  static FshCode parseCodeLexeme(String conceptText) {
    Matcher splitPoint = CODE_SPLIT.matcher(conceptText);
    String system;
    String code;
    if (!splitPoint.find()) {
      system = "";
      code = conceptText.startsWith("#") ? conceptText.substring(1) : "";
    } else {
      String sp = splitPoint.group();
      system = conceptText.substring(0, splitPoint.start()) + sp.substring(0, sp.length() - 1);
      code = conceptText.substring(splitPoint.start() + sp.length());
    }
    system = system.replace("\\\\", "\\").replace("\\#", "#");
    if (code.startsWith("\"") && code.endsWith("\"") && code.length() >= 1) {
      code = code.substring(1, Math.max(1, code.length() - 1)).replace("\\\\", "\\").replace("\\\"", "\"");
    }
    FshCode concept = new FshCode(code);
    if (!system.isEmpty()) concept.system = system;
    return concept;
  }

  private static Object parseCodingArray(Object codings, String property, String file) {
    if (nullish(codings)) return U;
    if (!(codings instanceof List<?> list)) throw new JsOps.JsTypeError("codings.map is not a function");
    List<Object> result = new ArrayList<>();
    for (Object c : list) {
      Object r = parseCoding(c, property, file);
      if (!nullish(r)) result.add(r);
    }
    if (result.isEmpty() && !list.isEmpty()) {
      // in the case that all inputs were invalid, return undefined instead of empty array
      return U;
    }
    return result;
  }

  private static Object parseCoding(Object coding, String property, String file) {
    if (nullish(coding)) return U;
    if (coding instanceof String s) {
      FshCode c = parseFshCode(s, property, file);
      return c == null ? U : c.toFHIRCoding();
    }
    // It's a coding object
    JsObject fixed = new JsObject();
    spread(fixed, coding);
    fixed.put("version", normalizeToString(get(coding, "version")));
    fixed.put("code", parseSimpleCode(get(coding, "code"), property, file));
    return removeUndefinedValues(fixed);
  }

  private static Object parseCodeableConceptArray(Object concepts, String property, String file) {
    if (nullish(concepts)) return U;
    List<?> list = (List<?>) concepts;
    List<Object> result = new ArrayList<>();
    for (Object c : list) {
      Object r = parseCodeableConcept(c, property, file);
      if (!nullish(r)) result.add(r);
    }
    if (result.isEmpty() && !list.isEmpty()) {
      // in the case that all inputs were invalid, return undefined instead of empty array
      return U;
    }
    return result;
  }

  private static Object parseCodeableConcept(Object concept, String property, String file) {
    if (nullish(concept)) return U;
    if (concept instanceof String) {
      Object coding = parseCoding(concept, property, file);
      if (nullish(coding)) {
        // return undefined instead of an empty concept
        return U;
      }
      List<Object> codings = new ArrayList<>();
      codings.add(coding);
      return new JsObject().with("coding", codings);
    }
    // It's a CodeableConcept object
    JsObject fixed = new JsObject();
    spread(fixed, concept);
    fixed.put("coding", parseCodingArray(get(concept, "coding"), property, file));
    return removeUndefinedValues(fixed);
  }

  private static Object parseCodeWithRequiredValues(Object yamlCode, List<String> allowedValues, String property,
      String file) {
    if (nullish(yamlCode)) return U;
    Object code = parseSimpleCode(yamlCode, property, file);
    for (String c : allowedValues) {
      if (c.equals(code)) return c;
    }
    StringBuilder allowed = new StringBuilder();
    for (int i = 0; i < allowedValues.size(); i++) {
      if (i > 0) allowed.append(',');
      allowed.append('\'').append(allowedValues.get(i)).append('\'');
    }
    logger.error("Invalid " + property + " value: '" + templ(code) + "'. Must be one of: " + allowed + ".", src(file));
    return U;
  }

  // /^(\d*(\.\d+)?)(\s+'([^']+)')?$/
  private static final Pattern QUANTITY = Pattern.compile("([0-9]*(\\.[0-9]+)?)(" + WS + "+'([^']+)')?");

  private static Object parseQuantity(Object yamlQuantity, String property, String file) {
    if (nullish(yamlQuantity)) return U;
    if (yamlQuantity instanceof String s) {
      Matcher m = QUANTITY.matcher(s);
      if (!m.matches()) {
        logger.error("Invalid " + property + " value: " + s, src(file));
        return U;
      }
      JsObject quantity = new JsObject();
      quantity.put("value", m.group(1).isEmpty() ? Double.NaN : Double.parseDouble(m.group(1)));
      if (m.group(4) != null && !m.group(4).isEmpty()) {
        quantity.put("system", "http://unitsofmeasure.org");
        quantity.put("code", m.group(4));
      }
      return quantity;
    }
    JsObject quantity = new JsObject();
    spread(quantity, yamlQuantity);
    quantity.put("code", parseSimpleCode(get(yamlQuantity, "code"), property + ".code", file));
    quantity.put("comparator", parseCodeWithRequiredValues(get(yamlQuantity, "comparator"),
        List.of("<", "<=", ">=", ">"), property + ".comparator", file));
    return removeUndefinedValues(quantity);
  }

  private static Object parseRange(Object yamlRange, String property, String file) {
    if (nullish(yamlRange)) return U;
    JsObject range = new JsObject();
    spread(range, yamlRange);
    range.put("low", parseQuantity(get(yamlRange, "low"), property + ".low", file));
    range.put("high", parseQuantity(get(yamlRange, "high"), property + ".high", file));
    return removeUndefinedValues(range);
  }

  private static Object parseReference(Object yamlReference, String property, String file) {
    if (nullish(yamlReference)) return U;
    JsObject reference = new JsObject();
    spread(reference, yamlReference);
    reference.put("identifier", parseIdentifier(get(yamlReference, "identifier"), property + ".identifier", file));
    return removeUndefinedValues(reference);
  }

  private static Object parseIdentifier(Object yamlIdentifier, String property, String file) {
    if (nullish(yamlIdentifier)) return U;
    JsObject identifier = new JsObject();
    spread(identifier, yamlIdentifier);
    identifier.put("use", parseCodeWithRequiredValues(get(yamlIdentifier, "use"),
        List.of("usual", "official", "temp", "secondary", "old"), property + ".use", file));
    identifier.put("type", parseCodeableConcept(get(yamlIdentifier, "type"), property + ".type", file));
    identifier.put("assigner", parseReference(get(yamlIdentifier, "assigner"), property + ".assigner", file));
    return removeUndefinedValues(identifier);
  }

  private static Object parseMeta(Object yamlMeta, String file) {
    if (nullish(yamlMeta)) return U;
    JsObject fixed = new JsObject();
    spread(fixed, yamlMeta);
    fixed.put("security", parseCodingArray(get(yamlMeta, "security"), "meta.security", file));
    fixed.put("tag", parseCodingArray(get(yamlMeta, "tag"), "meta.tag", file));
    return removeUndefinedValues(fixed);
  }

  private static Object parseText(Object yamlText, String file) {
    if (nullish(yamlText)) return U;
    JsObject fixed = new JsObject();
    spread(fixed, yamlText);
    fixed.put("status", parseCodeWithRequiredValues(get(yamlText, "status"),
        List.of("generated", "extensions", "additional", "empty"), "text.status", file));
    return removeUndefinedValues(fixed);
  }

  private static Object parseContact(Map<String, Object> yamlConfig, String file) {
    List<Object> contacts = new ArrayList<>();
    Object publishers = normalizeToArray(get(yamlConfig, "publisher"));
    if (truthy(publishers)) {
      List<?> pubs = (List<?>) publishers;
      for (int i = 0; i < pubs.size(); i++) {
        Object p = pubs.get(i);
        JsObject contact = new JsObject().with("name", get(p, "name"));
        Object url = get(p, "url");
        Object email = get(p, "email");
        if (truthy(url) || truthy(email)) {
          List<Object> telecom = new ArrayList<>();
          contact.put("telecom", telecom);
          if (truthy(get(p, "url"))) {
            telecom.add(new JsObject().with("system", "url").with("value", get(p, "url")));
          }
          if (truthy(get(p, "email"))) {
            telecom.add(new JsObject().with("system", "email").with("value", get(p, "email")));
          }
        } else if (i == 0) {
          // This was the first publisher and there was no additional contact detail, so skip it
          continue;
        }
        contacts.add(contact);
      }
    }
    if (truthy(get(yamlConfig, "contact"))) {
      for (Object yamlContact : (List<?>) normalizeToArray(get(yamlConfig, "contact"))) {
        JsObject contact = new JsObject();
        spread(contact, yamlContact);
        List<Object> telecom = mapListRequired(normalizeToArray(get(yamlContact, "telecom")), yamlTelecom -> {
          JsObject contactPoint = new JsObject();
          spread(contactPoint, yamlTelecom);
          contactPoint.put("system", parseCodeWithRequiredValues(get(yamlTelecom, "system"),
              List.of("phone", "fax", "email", "pager", "url", "sms", "other"), "contact.telecom.system", file));
          contactPoint.put("use", parseCodeWithRequiredValues(get(yamlTelecom, "use"),
              List.of("home", "work", "temp", "old", "mobile"), "contact.telecom.use", file));
          return removeUndefinedValues(contactPoint);
        });
        contact.put("telecom", telecom);
        if (telecom.isEmpty()) contact.remove("telecom");
        contacts.add(contact);
      }
    }
    if (contacts.isEmpty()) return U;
    return contacts;
  }

  private static Object parseUsageContext(Object yamlUsageContext, String file) {
    return mapList(normalizeToArray(yamlUsageContext), yaml -> {
      JsObject usageContext = new JsObject();
      usageContext.put("code",
          parseCoding(required(get(yaml, "code"), "useContext.code", file), "useContext.code", file));
      usageContext.put("valueCodeableConcept", parseCodeableConcept(get(yaml, "valueCodeableConcept"),
          "useContext.valueCodeableConcept", file));
      usageContext.put("valueQuantity",
          parseQuantity(get(yaml, "valueQuantity"), "useContext.valueQuantity", file));
      usageContext.put("valueRange", parseRange(get(yaml, "valueRange"), "useContext.valueRange", file));
      usageContext.put("valueReference",
          parseReference(get(yaml, "valueReference"), "useContext.valueReference", file));
      List<String> valueFields = new ArrayList<>();
      for (String v : List.of("valueCodeableConcept", "valueQuantity", "valueRange", "valueReference")) {
        if (!nullish(get(yaml, v))) valueFields.add(v);
      }
      if (valueFields.isEmpty()) {
        // at least one is required, so force the 'required' error
        required(U, "useContext.value[x]", file);
      } else if (valueFields.size() > 1) {
        // more than one value is not allowed since it is a value[x] choice
        logger.error("Only one useContext.value[x] is allowed but found multiple: " + String.join(", ", valueFields),
            src(file));
      }
      return removeUndefinedValues(usageContext);
    });
  }

  private static Object parseJurisdiction(Object yamlJurisdiction, String file) {
    return parseCodeableConceptArray(normalizeToArray(yamlJurisdiction), "jurisdiction", file);
  }

  private static final Pattern UPPER = Pattern.compile("[A-Z]");

  private static Object parseDependencies(Object yamlDependencies) {
    if (nullish(yamlDependencies)) return U;
    List<Object> out = new ArrayList<>();
    for (Map.Entry<String, Object> e : entries(yamlDependencies)) {
      String packageId = e.getKey();
      Object versionOrDetails = e.getValue();
      if (UPPER.matcher(packageId).find()) {
        logger.warn(packageId + " contains uppercase characters, which is discouraged. SUSHI will use "
            + packageId.toLowerCase(Locale.ROOT) + " as the package name.");
        packageId = packageId.toLowerCase(Locale.ROOT);
      }
      if (versionOrDetails instanceof String || versionOrDetails instanceof Double) {
        out.add(new JsObject().with("packageId", packageId).with("version", templ(versionOrDetails)));
      } else if (versionOrDetails == null) {
        // this is an error condition, but we'll just make it an incomplete dependency and
        // handle the error later
        out.add(new JsObject().with("packageId", packageId));
      } else {
        // else it's the complex object
        Object version = get(versionOrDetails, "version");
        JsObject dep = new JsObject();
        dep.put("id", get(versionOrDetails, "id"));
        dep.put("packageId", packageId);
        dep.put("uri", get(versionOrDetails, "uri"));
        dep.put("version", version instanceof String || version instanceof Double ? templ(version) : U);
        dep.put("reason", get(versionOrDetails, "reason"));
        dep.put("extension", get(versionOrDetails, "extension"));
        out.add(removeUndefinedValues(dep));
      }
    }
    return out;
  }

  private static Object parseGlobal(Object yamlGlobal) {
    if (nullish(yamlGlobal)) return U;
    List<Object> global = new ArrayList<>();
    for (Map.Entry<String, Object> e : entries(yamlGlobal)) {
      Object profiles = normalizeToArray(e.getValue());
      if (nullish(profiles)) throw new JsOps.JsTypeError("Cannot read properties of undefined (reading 'forEach')");
      for (Object profile : (List<?>) profiles) {
        global.add(new JsObject().with("type", e.getKey()).with("profile", profile));
      }
    }
    return global;
  }

  @SuppressWarnings("unchecked")
  private static Object parseGroups(Object yamlGroups) {
    if (nullish(yamlGroups)) return U;
    List<Object> out = new ArrayList<>();
    for (Map.Entry<String, Object> e : entries(yamlGroups)) {
      String id = e.getKey();
      Object groupObj = e.getValue();
      Object name = get(groupObj, "name");
      if (name == U) {
        if (groupObj instanceof Map<?, ?> m) {
          ((Map<String, Object>) m).put("name", id);
        } else if (!(groupObj instanceof List<?>)) {
          throw JsOps.cannotCreateProperty("name", groupObj);
        }
        // (an array gets a non-index property TS can see but the value model cannot hold; its name is the id)
        name = id;
      }
      out.add(new JsObject().with("id", id).with("name", name).with("description", get(groupObj, "description"))
          .with("resources", get(groupObj, "resources")));
    }
    return out;
  }

  private static Object parseDefinition(Object yamlDefinition) {
    // NOTE: extension is the only property allowed to be set directly on definition in config file
    if (nullish(yamlDefinition) || nullish(get(yamlDefinition, "extension"))) return U;
    return new JsObject().with("extension", get(yamlDefinition, "extension"));
  }

  private static Object parseResources(Object yamlResources, String file) {
    if (nullish(yamlResources)) return U;
    List<Object> out = new ArrayList<>();
    for (Map.Entry<String, Object> e : entries(yamlResources)) {
      String reference = e.getKey();
      Object details = e.getValue();
      if ("omit".equals(details) || "#omit".equals(details)) {
        out.add(new JsObject().with("reference", new JsObject().with("reference", reference)).with("omit", true));
        continue;
      }
      JsObject r = new JsObject().with("reference", new JsObject().with("reference", reference));
      spread(r, details);
      r.put("profile", normalizeToArray(get(details, "profile")));
      r.put("fhirVersion", mapList(normalizeToArray(get(details, "fhirVersion")),
          v -> parseSimpleCode(v, "resource[" + reference + "].fhirVersion", file)));
      out.add(r);
    }
    return out;
  }

  private static Object parsePages(Object yamlPages, String file) {
    if (nullish(yamlPages)) return U;
    List<Object> out = new ArrayList<>();
    for (Map.Entry<String, Object> e : entries(yamlPages)) {
      out.add(parsePage(e.getKey(), e.getValue(), "pages[" + e.getKey() + "]", file));
    }
    return out;
  }

  private static final List<String> PAGE_PROPS = List.of("title", "generation", "sourceUrl", "sourceString",
      "sourceMarkdown", "extension", "modifierExtension");

  private static JsObject parsePage(String nameUrl, Object details, String property, String file) {
    JsObject page = new JsObject().with("nameUrl", nameUrl);
    if (truthy(getOpt(details, "title"))) page.put("title", get(details, "title"));
    if (truthy(getOpt(details, "generation"))) {
      page.put("generation", parseCodeWithRequiredValues(get(details, "generation"),
          List.of("html", "markdown", "xml", "generated"), property + ".generation", file));
    }
    if (truthy(getOpt(details, "sourceUrl"))) page.put("sourceUrl", get(details, "sourceUrl"));
    if (truthy(getOpt(details, "sourceString"))) page.put("sourceString", get(details, "sourceString"));
    if (truthy(getOpt(details, "sourceMarkdown"))) page.put("sourceMarkdown", get(details, "sourceMarkdown"));
    if (truthy(getOpt(details, "name"))) page.put("name", get(details, "name"));
    if (truthy(getOpt(details, "extension"))) page.put("extension", get(details, "extension"));
    if (truthy(getOpt(details, "modifierExtension"))) {
      page.put("modifierExtension", get(details, "modifierExtension"));
    }
    if (details != null) {
      for (Map.Entry<String, Object> e : entries(details)) {
        String key = e.getKey();
        if (PAGE_PROPS.contains(key)) continue;
        if (nullish(page.get("page"))) page.put("page", new ArrayList<Object>());
        // We only want to recursively parse the page if it defines another page
        if (JsOps.isObjectType(e.getValue())) {
          @SuppressWarnings("unchecked") List<Object> sub = (List<Object>) page.get("page");
          sub.add(parsePage(key, e.getValue(), property + "[" + key + "]", file));
        }
      }
    }
    return page;
  }

  private static Object parseParameters(Map<String, Object> yamlConfig, Object fshOnly, String file) {
    List<Object> parameters = new ArrayList<>();
    Object copyrightYear = coalesce(get(yamlConfig, "copyrightYear"), get(yamlConfig, "copyrightyear"));
    if (!truthy(fshOnly) && !truthy(copyrightYear)) {
      logger.warn("The copyrightYear configuration property is not set. Some publishers (e.g., HL7 IG Publisher) "
          + "require a copyright year.", src(file));
    }
    Object releaseLabel = coalesce(get(yamlConfig, "releaseLabel"), get(yamlConfig, "releaselabel"));
    if (!truthy(fshOnly) && !truthy(releaseLabel)) {
      logger.warn("The releaseLabel configuration property is not set. Some publishers (e.g., HL7 IG Publisher) "
          + "require a release label.", src(file));
    }
    if (truthy(copyrightYear)) {
      parameters.add(new JsObject().with("code", "copyrightyear").with("value", templ(copyrightYear)));
    }
    if (truthy(releaseLabel)) {
      parameters.add(new JsObject().with("code", "releaselabel").with("value", releaseLabel));
    }
    Object yamlParameters = get(yamlConfig, "parameters");
    if (truthy(yamlParameters)) {
      for (Map.Entry<String, Object> e : entries(yamlParameters)) {
        Object values = normalizeToArray(e.getValue());
        if (nullish(values)) continue;
        for (Object value : (List<?>) values) {
          parameters.add(new JsObject().with("code", e.getKey()).with("value", templ(value)));
        }
      }
    } else if (parameters.isEmpty()) {
      return U; // return undefined rather than an empty []
    }
    return parameters;
  }

  private static Object parseTemplates(Object yamlTemplates, String file) {
    return mapList(normalizeToArray(yamlTemplates), t -> {
      JsObject template = new JsObject();
      spread(template, t);
      template.put("code",
          parseSimpleCode(required(get(t, "code"), "templates.code", file), "templates.code", file));
      template.put("source", required(get(t, "source"), "templates.source", file));
      return removeUndefinedValues(template);
    });
  }

  private static Object parseMenu(Object yamlMenu) {
    if (nullish(yamlMenu)) return U;
    List<Object> out = new ArrayList<>();
    for (Map.Entry<String, Object> e : entries(yamlMenu)) {
      String name = e.getKey();
      Object value = e.getValue();
      JsObject item = new JsObject().with("name", name);
      if (value instanceof String s) {
        // Set menu item attributes based on keywords
        if (s.contains("new-tab ")) {
          item.put("openInNewTab", true);
          s = replaceFirst(s, "new-tab");
        }
        if (s.contains("external ")) {
          logger.warn("The \"external\" keyword in menu configuration has been deprecated. The IG Publisher"
              + " will detect external links and add the external link icon automatically. To resolve"
              + " this warning, remove the \"external\" keyword from the \"" + name + "\" menu item.");
          s = replaceFirst(s, "external");
        }
        // Trim off any white space left after using keywords. URL is remaining value.
        item.put("url", Js.trim(s));
      } else {
        item.put("subMenu", parseMenu(value));
      }
      out.add(item);
    }
    return out;
  }

  /** JS {@code s.replace(literal, '')}: the first occurrence only. */
  private static String replaceFirst(String s, String literal) {
    int i = s.indexOf(literal);
    return i < 0 ? s : s.substring(0, i) + s.substring(i + literal.length());
  }

  private static final List<String> ALLOWED_HISTORY_STATUS =
      List.of("ci-build", "preview", "ballot", "trial-use", "update", "normative", "trial-use+normative");

  private static Object parseHistory(Map<String, Object> yamlConfig, String file) {
    Object yamlHistory = get(yamlConfig, "history");
    if (nullish(yamlHistory)) return U;
    JsObject history = new JsObject();
    history.put("package-id",
        coalesce(coalesce(get(yamlHistory, "package-id"), get(yamlConfig, "packageId")), get(yamlConfig, "id")));
    history.put("canonical", coalesce(get(yamlHistory, "canonical"), get(yamlConfig, "canonical")));
    history.put("title", coalesce(get(yamlHistory, "title"), get(yamlConfig, "title")));
    history.put("introduction", coalesce(get(yamlHistory, "introduction"), get(yamlConfig, "description")));
    List<Object> list = new ArrayList<>();
    history.put("list", list);
    Object current = get(yamlHistory, "current");
    if (truthy(current)) {
      if (current instanceof String) {
        list.add(new JsObject().with("version", "current")
            .with("desc", "Continuous Integration Build (latest in version control)").with("path", current)
            .with("status", "ci-build").with("current", true));
      } else {
        JsObject item = new JsObject();
        item.put("version", "current");
        item.put("date", normalizeToString(get(current, "date")));
        item.put("desc", coalesce(get(current, "desc"), "Continuous Integration Build (latest in version control)"));
        item.put("path", required(get(current, "path"), "history[current].path", file));
        item.put("changes", get(current, "changes"));
        item.put("status", parseCodeWithRequiredValues(coalesce(get(current, "status"), "ci-build"),
            ALLOWED_HISTORY_STATUS, "history[current].status", file));
        item.put("sequence", get(current, "sequence"));
        item.put("fhirversion", get(current, "fhirversion"));
        item.put("current", coalesce(get(current, "current"), true));
        list.add(item);
      }
    }
    for (Map.Entry<String, Object> e : entries(yamlHistory)) {
      String key = e.getKey();
      if (List.of("package-id", "canonical", "title", "introduction", "current").contains(key)) continue;
      Object item = e.getValue();
      JsObject h = new JsObject();
      h.put("version", key);
      h.put("date", required(get(item, "date"), "history[" + key + "].date", file));
      h.put("desc", required(get(item, "desc"), "history[" + key + "].desc", file));
      h.put("path", required(get(item, "path"), "history[" + key + "].path", file));
      h.put("changes", get(item, "changes"));
      h.put("status", parseCodeWithRequiredValues(required(get(item, "status"), "history[" + key + "].status", file),
          ALLOWED_HISTORY_STATUS, "history[" + key + "].status", file));
      h.put("sequence", required(get(item, "sequence"), "history[" + key + "].sequence", file));
      h.put("fhirversion", required(get(item, "fhirversion"), "history[" + key + "].fhirVersion", file));
      h.put("current", get(item, "current"));
      list.add(h);
    }
    for (Object item : list) removeUndefinedValues((JsObject) item);
    return history;
  }

  private static Object parseInstanceOptions(Object yamlInstanceOptions, String file) {
    Object setMetaProfile = parseCodeWithRequiredValues(getOpt(yamlInstanceOptions, "setMetaProfile"),
        List.of("always", "never", "inline-only", "standalone-only"), "instanceOptions.setMetaProfile", file);
    JsObject options = new JsObject();
    options.put("setMetaProfile", truthy(setMetaProfile) ? setMetaProfile : "always");
    Object setId = parseCodeWithRequiredValues(getOpt(yamlInstanceOptions, "setId"),
        List.of("always", "standalone-only"), "instanceOptions.setId", file);
    options.put("setId", truthy(setId) ? setId : "always");
    options.put("manualSliceOrdering", coalesce(getOpt(yamlInstanceOptions, "manualSliceOrdering"), false));
    return options;
  }

  private static void detectPotentialMistakes(Map<String, Object> yaml) {
    ConfigSchema.detectPotentialMistakes(yaml, logger);
  }
}
