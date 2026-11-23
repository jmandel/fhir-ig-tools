package org.hl7.fhir.sushi.importer.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.FshLogger;

/**
 * importConfiguration's {@code detectPotentialMistakes}: validates the parsed YAML against SUSHI's
 * {@code YAMLschema.json} the way ajv 8 does with {@code {allowUnionTypes, allowMatchingProperties, allErrors}}, and
 * logs SUSHI's warnings from ajv's errors (grouped by instancePath in first-error order). ajv's own message texts
 * are never logged; what must match is the set of errors, their instancePaths, keywords and order. This implements
 * exactly the keywords the schema uses ({@code type, $ref, properties, patternProperties, additionalProperties,
 * items, oneOf}) in ajv's evaluation order: $ref, oneOf, then the array group (items), then the object group
 * (additionalProperties, properties, patternProperties); a single declared type with rules for it reports its type
 * error inside that group, other declared types are checked first.
 */
final class ConfigSchema {
  private ConfigSchema() {}

  /** An ajv error, reduced to the parts SUSHI uses. */
  record ValidationError(String instancePath, String keyword, String additionalProperty) {}

  private static final Map<String, Object> SCHEMA = load();

  @SuppressWarnings("unchecked")
  private static Map<String, Object> load() {
    try (InputStream in = ConfigSchema.class.getResourceAsStream("YAMLschema.json")) {
      return (Map<String, Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static void detectPotentialMistakes(Map<String, Object> yaml, FshLogger logger) {
    List<ValidationError> errors = validate(yaml);
    if (errors.isEmpty()) return;
    // lodash groupBy: keys in first-occurrence order (instancePaths start with '/' or are '', never array indexes)
    Map<String, List<ValidationError>> errorCollection = new LinkedHashMap<>();
    for (ValidationError e : errors) errorCollection.computeIfAbsent(e.instancePath(), k -> new ArrayList<>()).add(e);
    for (Map.Entry<String, List<ValidationError>> entry : errorCollection.entrySet()) {
      String instancePath = entry.getKey();
      // a validation error on additionalProperties usually represents incorrect spelling or indentation.
      // for other types of validation errors, just report the name of the property.
      List<ValidationError> additionalPropertiesErrors =
          entry.getValue().stream().filter(e -> e.keyword().equals("additionalProperties")).toList();
      if (!additionalPropertiesErrors.isEmpty()) {
        String parentProperty = instancePath.isEmpty() ? "" : "property " + instancePath.substring(1) + " ";
        boolean singular = additionalPropertiesErrors.size() == 1;
        String additionalPropertyNames = String.join(", ",
            additionalPropertiesErrors.stream().map(ValidationError::additionalProperty).toList());
        String recommendation = "Check that " + (singular ? "this property is" : "these properties are")
            + " spelled, capitalized, and indented correctly.";
        if (instancePath.startsWith("/pages")) {
          recommendation += " If " + (singular ? "this is a page, it" : "these are pages, they")
              + " should end with .md, .xml, or .html.";
        } else if (instancePath.startsWith("/definition")) {
          recommendation = "Only the extension property is allowed under definition. All other definition "
              + "properties are represented at the top-level of the configuration.";
        }
        logger.warn("Configuration " + parentProperty + "contains unexpected "
            + (singular ? "property" : "properties") + ": " + additionalPropertyNames + ". " + recommendation);
      } else {
        logger.warn("Configuration property " + instancePath.substring(1) + " has a value with an unexpected type.");
      }
    }
  }

  /** ajv validate(YAML_SCHEMA)(yaml) with allErrors: the errors in ajv's order. */
  static List<ValidationError> validate(Object data) {
    List<ValidationError> errors = new ArrayList<>();
    validate(SCHEMA, data, "", errors);
    return errors;
  }

  @SuppressWarnings("unchecked")
  private static void validate(Object schemaObj, Object data, String path, List<ValidationError> errors) {
    if (schemaObj instanceof Boolean b) {
      if (!b) errors.add(new ValidationError(path, "false schema", null));
      return;
    }
    Map<String, Object> s = (Map<String, Object>) schemaObj;
    List<String> types = types(s.get("type"));
    boolean arrayRules = s.containsKey("items");
    boolean objectRules =
        s.containsKey("properties") || s.containsKey("patternProperties") || s.containsKey("additionalProperties");
    if (s.containsKey("$ref") && !arrayRules && !objectRules && !s.containsKey("oneOf") && types.isEmpty()) {
      validate(resolveRef((String) s.get("$ref")), data, path, errors);
      return;
    }
    boolean checkTypes = !types.isEmpty() && !(types.size() == 1
        && ((types.get(0).equals("array") && arrayRules) || (types.get(0).equals("object") && objectRules)));
    if (checkTypes && types.stream().noneMatch(t -> hasType(t, data))) {
      errors.add(new ValidationError(path, "type", null));
    }
    boolean typeErrorInGroup = !checkTypes && types.size() == 1;
    // untyped group: $ref (core), then oneOf (applicator)
    if (s.containsKey("$ref")) validate(resolveRef((String) s.get("$ref")), data, path, errors);
    if (s.containsKey("oneOf")) oneOf((List<Object>) s.get("oneOf"), data, path, errors);
    // array group
    if (arrayRules) {
      if (data instanceof List<?> l) {
        Object items = s.get("items");
        for (int i = 0; i < l.size(); i++) validate(items, l.get(i), path + "/" + i, errors);
      } else if (typeErrorInGroup && types.get(0).equals("array")) {
        errors.add(new ValidationError(path, "type", null));
      }
    }
    // object group: additionalProperties, properties, patternProperties
    if (objectRules) {
      if (hasType("object", data)) {
        Map<String, Object> obj = (Map<String, Object>) data;
        Map<String, Object> props = (Map<String, Object>) s.get("properties");
        Map<String, Object> patternProps = (Map<String, Object>) s.get("patternProperties");
        List<Pattern> patterns = new ArrayList<>();
        if (patternProps != null) for (String p : patternProps.keySet()) patterns.add(jsRegex(p));
        if (s.containsKey("additionalProperties")) {
          Object additional = s.get("additionalProperties");
          for (String key : Js.ownKeys(obj)) {
            boolean isAdditional = (props == null || !props.containsKey(key) || key.equals("__proto__"))
                && patterns.stream().noneMatch(p -> p.matcher(key).find());
            if (!isAdditional) continue;
            if (Boolean.FALSE.equals(additional)) {
              errors.add(new ValidationError(path, "additionalProperties", key));
            } else if (!Boolean.TRUE.equals(additional)) {
              validate(additional, obj.get(key), path + "/" + escape(key), errors);
            }
          }
        }
        if (props != null) {
          for (Map.Entry<String, Object> p : props.entrySet()) {
            if (obj.containsKey(p.getKey())) {
              validate(p.getValue(), obj.get(p.getKey()), path + "/" + escape(p.getKey()), errors);
            }
          }
        }
        if (patternProps != null) {
          int i = 0;
          for (Map.Entry<String, Object> p : patternProps.entrySet()) {
            Pattern pat = patterns.get(i++);
            for (String key : Js.ownKeys(obj)) {
              if (pat.matcher(key).find()) validate(p.getValue(), obj.get(key), path + "/" + escape(key), errors);
            }
          }
        }
      } else if (typeErrorInGroup && types.get(0).equals("object")) {
        errors.add(new ValidationError(path, "type", null));
      }
    }
  }

  /** ajv oneOf: every branch runs (allErrors); valid per ajv's passing logic; branch errors kept unless valid. */
  private static void oneOf(List<Object> branches, Object data, String path, List<ValidationError> errors) {
    int errsCount = errors.size();
    boolean valid = false;
    for (int i = 0; i < branches.size(); i++) {
      int before = errors.size();
      validate(branches.get(i), data, path, errors);
      boolean schValid = errors.size() == before;
      if (i > 0 && schValid && valid) {
        valid = false;
      } else if (schValid) {
        valid = true;
      }
    }
    if (valid) {
      errors.subList(errsCount, errors.size()).clear();
    } else {
      errors.add(new ValidationError(path, "oneOf", null));
    }
  }

  @SuppressWarnings("unchecked")
  private static Object resolveRef(String ref) {
    if (!ref.startsWith("#/definitions/")) throw new IllegalArgumentException("unsupported $ref " + ref);
    return ((Map<String, Object>) SCHEMA.get("definitions")).get(ref.substring("#/definitions/".length()));
  }

  @SuppressWarnings("unchecked")
  private static List<String> types(Object t) {
    if (t == null) return List.of();
    if (t instanceof String s) return List.of(s);
    return (List<String>) t;
  }

  /** ajv's data type checks (strictNumbers: NaN and Infinity are not numbers). */
  private static boolean hasType(String type, Object v) {
    return switch (type) {
      case "string" -> v instanceof String;
      case "number" -> v instanceof Double d && Double.isFinite(d);
      case "integer" -> v instanceof Double d && Double.isFinite(d) && d == Math.floor(d);
      case "boolean" -> v instanceof Boolean;
      case "null" -> v == null;
      case "array" -> v instanceof List<?>;
      case "object" -> v instanceof Map<?, ?>;
      default -> false;
    };
  }

  /** JSON Pointer escaping of a property name in instancePath. */
  private static String escape(String key) {
    return key.replace("~", "~0").replace("/", "~1");
  }

  /** new RegExp(pattern, "u"): the schema's patterns only need JS `$` (end of input) translated. */
  private static Pattern jsRegex(String p) {
    String j = p.endsWith("$") ? p.substring(0, p.length() - 1) + "\\z" : p;
    return Pattern.compile(j);
  }
}
