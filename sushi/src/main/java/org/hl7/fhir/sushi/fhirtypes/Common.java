package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.utils.FshLogger.logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.export.InstanceExporter;
import org.hl7.fhir.sushi.fshtypes.AllowedRules;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.FshReference;
import org.hl7.fhir.sushi.fshtypes.FshStructure;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Mapping;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.Resource;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ConceptRule;
import org.hl7.fhir.sushi.fshtypes.rules.InsertRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.fshtypes.rules.ValueSetConceptComponentRule;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.js.JsonSerializable;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FishingUtils;
import org.hl7.fhir.sushi.utils.Metadata;
import org.hl7.fhir.sushi.utils.ValidUrl;

// port of sushi/src/fhirtypes/common.ts (SUSHI 3.21.1)
/**
 * Free functions of fhirtypes/common.ts, plus the generic property helpers ({@link #getProp} etc.) that stand in for
 * TS's untyped {@code obj[key]} on the JS value model and on {@link PropertyHolder}s.
 */
public final class Common {
  private Common() {}

  // List of Conformance and Terminology resources from http://hl7.org/fhir/R4/resourcelist.html
  // and https://hl7.org/fhir/R5/resourcelist.html
  public static final Set<String> CONFORMANCE_AND_TERMINOLOGY_RESOURCES = Collections.unmodifiableSet(
      new LinkedHashSet<>(List.of("CapabilityStatement", "CapabilityStatement2", "StructureDefinition",
          "ImplementationGuide", "SearchParameter", "MessageDefinition", "OperationDefinition",
          "CompartmentDefinition", "StructureMap", "GraphDefinition", "ExampleScenario", "CodeSystem", "ValueSet",
          "ConceptMap", "ConceptMap2", "NamingSystem", "TerminologyCapabilities")));

  // characteristics are set using the structuredefinition-type-characteristics extension
  public static final String TYPE_CHARACTERISTICS_EXTENSION =
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics";
  // the allowed codes to use with that extension are in the type-characteristics-code code system.
  public static final String TYPE_CHARACTERISTICS_CODE = "http://hl7.org/fhir/type-characteristics-code";
  public static final String LOGICAL_TARGET_EXTENSION =
      "http://hl7.org/fhir/tools/StructureDefinition/logical-target";
  public static final String IMPOSE_PROFILE_EXTENSION =
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile";

  // ---------------------------------------------------------------------------------------------------------------
  // JS helpers (generic property access over the value model and PropertyHolders)
  // ---------------------------------------------------------------------------------------------------------------

  /** A JS TypeError thrown where TS code would throw one (e.g. reading a property of null). */
  public static final class JsTypeError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JsTypeError(String message) {
      super(message);
    }
  }

  /** TS {@code obj[key]} (null for absent / undefined / non-objects). Lists accept array-index keys. */
  public static Object getProp(Object o, String key) {
    Object v = getRaw(o, key);
    return v == Js.UNDEFINED ? null : v;
  }

  /** TS {@code obj[key]} keeping the distinction: {@link Js#UNDEFINED} for absent, null for a JS null value. */
  public static Object getRaw(Object o, String key) {
    if (o instanceof Map<?, ?> m) {
      if (!m.containsKey(key)) return Js.UNDEFINED;
      return m.get(key);
    }
    if (o instanceof PropertyHolder p) {
      if (!p.hasProperty(key)) return Js.UNDEFINED;
      return p.getProperty(key);
    }
    if (o instanceof List<?> l) {
      if (Js.isArrayIndex(key)) {
        long i = Long.parseLong(key);
        if (i < l.size()) return l.get((int) i);
      } else if (key.equals("length")) {
        return (double) l.size();
      }
      return Js.UNDEFINED;
    }
    return Js.UNDEFINED;
  }

  /** TS {@code obj[key] !== undefined}. */
  public static boolean hasProp(Object o, String key) {
    return getRaw(o, key) != Js.UNDEFINED;
  }

  /** TS {@code obj[key] = value}. Lists: array-index keys (extending with nulls, as JS holes print as null). */
  @SuppressWarnings("unchecked")
  public static void setProp(Object o, String key, Object value) {
    if (o instanceof Map<?, ?> m) {
      ((Map<String, Object>) m).put(key, value);
    } else if (o instanceof PropertyHolder p) {
      p.setProperty(key, value);
    } else if (o instanceof List<?> l) {
      if (!Js.isArrayIndex(key)) throw new IllegalArgumentException("non-index key on array: " + key);
      listSet((List<Object>) l, Integer.parseInt(key), value);
    } else if (o == null) {
      throw new JsTypeError("Cannot set properties of " + (o == null ? "null" : "undefined") + " (setting '" + key
          + "')");
    }
    // setting a property on a JS primitive is a no-op in sloppy mode; SUSHI never relies on it
  }

  /** TS {@code delete obj[key]}. */
  public static void deleteProp(Object o, String key) {
    if (o instanceof Map<?, ?> m) {
      m.remove(key);
    } else if (o instanceof PropertyHolder p) {
      p.deleteProperty(key);
    } else if (o instanceof List<?> l && Js.isArrayIndex(key)) {
      @SuppressWarnings("unchecked") List<Object> ll = (List<Object>) l;
      int i = Integer.parseInt(key);
      if (i < ll.size()) ll.set(i, null);
    }
  }

  /** TS {@code Object.keys(obj)} / {@code for (const k in obj)} over own data properties. */
  public static List<String> keysOf(Object o) {
    if (o instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked") Map<String, ?> mm = (Map<String, ?>) m;
      return Js.ownKeys(mm);
    }
    if (o instanceof PropertyHolder p) return new ArrayList<>(p.propertyKeys());
    if (o instanceof List<?> l) {
      List<String> keys = new ArrayList<>(l.size());
      for (int i = 0; i < l.size(); i++) keys.add(Integer.toString(i));
      return keys;
    }
    return new ArrayList<>();
  }

  /** JS {@code typeof v === 'object'} (true for null, maps, arrays and class instances). */
  public static boolean isJsObjectType(Object v) {
    if (v == Js.UNDEFINED) return false;
    return !(v instanceof String || v instanceof Number || v instanceof Boolean
        || v instanceof java.math.BigInteger);
  }

  /** lodash isObjectLike: {@code typeof v === 'object' && v !== null}. */
  public static boolean isObjectLike(Object v) {
    return v != null && isJsObjectType(v);
  }

  /** lodash isEmpty over the value model. */
  public static boolean isEmpty(Object v) {
    if (v == null || v == Js.UNDEFINED) return true;
    if (v instanceof String s) return s.isEmpty();
    if (v instanceof List<?> l) return l.isEmpty();
    if (v instanceof Map<?, ?> m) return m.isEmpty();
    if (v instanceof PropertyHolder p) return p.propertyKeys().isEmpty();
    return true;
  }

  /** JS {@code a === b} over the value model (numbers by value, NaN !== NaN, objects by identity). */
  public static boolean strictEquals(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
    if (a instanceof String || a instanceof Boolean) return a.equals(b);
    return a == b;
  }

  /** {@code arr[i] = v} on a JS array: extends with null holes if needed. */
  public static void listSet(List<Object> l, int i, Object v) {
    while (l.size() < i) l.add(null);
    if (i == l.size()) l.add(v);
    else l.set(i, v);
  }

  /** lodash upperFirst. */
  public static String upperFirst(String s) {
    if (s == null || s.isEmpty()) return s == null ? "" : s;
    int n = Character.charCount(s.codePointAt(0));
    return s.substring(0, n).toUpperCase(Locale.ROOT) + s.substring(n);
  }

  /** The text TS logs at debug level for {@code e.stack}. */
  public static String jsStack(Throwable e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString().stripTrailing();
  }

  /** TS {@code obj.rules = rules} for any FSH definition with rules. */
  @SuppressWarnings("unchecked")
  public static void setRules(FshEntity def, List<? extends Rule> rules) {
    List<Rule> r = (List<Rule>) rules;
    if (def instanceof FshStructure s) s.rules = r;
    else if (def instanceof Instance i) i.rules = r;
    else if (def instanceof FshValueSet v) v.rules = r;
    else if (def instanceof FshCodeSystem c) c.rules = r;
    else if (def instanceof Invariant inv) inv.rules = r;
    else if (def instanceof RuleSet rs) rs.rules = r;
    else if (def instanceof Mapping m) m.rules = r;
    else throw new IllegalArgumentException("no rules on " + def.getClass().getSimpleName());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // common.ts
  // ---------------------------------------------------------------------------------------------------------------

  private static final Pattern PATH_PERIOD = Pattern.compile("\\.(?![^\\[]*\\])");

  /** Splits on periods that aren't within square brackets. */
  public static List<String> splitOnPathPeriods(String path) {
    return new ArrayList<>(List.of(PATH_PERIOD.split(path, -1)));
  }

  /** A value of the rule maps passed to createUsefulSlices / determineKnownSlices (TS {@code { pathParts }}). */
  public interface HasPathParts {
    List<PathPart> pathParts();
  }

  public record PathPartsEntry(List<PathPart> pathParts) implements HasPathParts {}

  /** Anything with {@code getOwnStructureDefinition(fisher)}: SD, ED, CodeSystem, ValueSet. */
  public static StructureDefinition ownStructureDefinition(Object instance, Fishable fisher) {
    if (instance instanceof StructureDefinition sd) return sd.getOwnStructureDefinition(fisher);
    if (instance instanceof ElementDefinition ed) return ed.getOwnStructureDefinition(fisher);
    if (instance instanceof CodeSystem cs) return cs.getOwnStructureDefinition(fisher);
    if (instance instanceof ValueSet vs) return vs.getOwnStructureDefinition(fisher);
    throw new IllegalArgumentException("no getOwnStructureDefinition on " + instance.getClass().getSimpleName());
  }

  public static void setPropertyOnDefinitionInstance(Object instance, String path, Object value, Fishable fisher) {
    setPropertyOnDefinitionInstance(instance, path, value, fisher, new ArrayList<>(), null);
  }

  public static void setPropertyOnDefinitionInstance(Object instance, String path, Object value, Fishable fisher,
      List<String> inlineResourceTypes) {
    setPropertyOnDefinitionInstance(instance, path, value, fisher, inlineResourceTypes, null);
  }

  /**
   * Sets an instance property of a StructureDefinition, ElementDefinition, CodeSystem or ValueSet if possible.
   * {@code instanceSD} null means "instance.getOwnStructureDefinition(fisher)" (TS default parameter); it is modified
   * in place as paths are validated, so share it only between calls on the same instance.
   */
  public static void setPropertyOnDefinitionInstance(Object instance, String path, Object value, Fishable fisher,
      List<String> inlineResourceTypes, StructureDefinition instanceSD) {
    if (inlineResourceTypes == null) inlineResourceTypes = new ArrayList<>();
    if (instanceSD == null) instanceSD = ownStructureDefinition(instance, fisher);
    StructureDefinition.ValidateValueResult r =
        instanceSD.validateValueAtPath(path, value, fisher, inlineResourceTypes);
    List<PathPart> pathParts = r.pathParts();
    if (instance instanceof ElementDefinition ed) {
      ed.clearOriginalProperty(pathParts);
    }
    if (!(instance instanceof CodeSystem || instance instanceof ValueSet)) {
      Map<String, PathPartsEntry> ruleMap = new LinkedHashMap<>();
      ruleMap.put(path, new PathPartsEntry(pathParts));
      Map<String, Integer> knownSlices = determineKnownSlices(instanceSD, ruleMap, fisher);
      setImpliedPropertiesOnInstance(instance, instanceSD, new ArrayList<>(List.of(path)), new ArrayList<>(), fisher,
          knownSlices);
    }
    setPropertyOnInstance(instance, pathParts, r.assignedValue(), fisher);
  }

  private static final Pattern NUMERIC_BRACKET = Pattern.compile("\\[[-+]?\\d+\\]");

  private static String sliceNameOf(Object el) {
    Object sn = getProp(el, "_sliceName");
    return sn instanceof String s ? s : (sn == null ? null : Js.toStr(sn));
  }

  private static List<Integer> sliceIndices(Object arr, String sliceName, String base, Fishable fisher) {
    List<Integer> sliceIndices = new ArrayList<>();
    Metadata meta = fisher.fishForMetadata(sliceName);
    String sliceExtensionUrl = meta == null ? null : meta.url;
    if (arr instanceof List<?> l) {
      for (int i = 0; i < l.size(); i++) {
        Object el = l.get(i);
        Object url = getProp(el, "url");
        if (strictEquals(getProp(el, "_sliceName"), sliceName)
            || (isExtension(base) && Js.truthy(url) && strictEquals(url, sliceExtensionUrl))) {
          sliceIndices.add(i);
        }
      }
    }
    return sliceIndices;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> arr(Object current, String key) {
    Object v = getProp(current, key);
    if (v instanceof List<?> l) return (List<Object>) l;
    throw new JsTypeError("Cannot read properties of " + (v == null ? "undefined" : "non-array") + " (" + key + ")");
  }

  private static JsObject sliceObj(String sliceName) {
    return new JsObject().with("_sliceName", sliceName);
  }

  /**
   * Adds placeholder elements with slice names to array elements on instance definition. Returns the path of each
   * slice including the slice name and the quantity of the slice.
   */
  public static Map<String, Integer> createUsefulSlices(Object instanceDef,
      StructureDefinition instanceOfStructureDefinition, Map<String, ? extends HasPathParts> ruleMap,
      Fishable fisher) {
    Map<String, Integer> knownSlices = new LinkedHashMap<>();
    for (Map.Entry<String, ? extends HasPathParts> entry : ruleMap.entrySet()) {
      String path = entry.getKey();
      List<PathPart> pathParts = entry.getValue().pathParts();
      String nonNumericPath = NUMERIC_BRACKET.matcher(path).replaceAll("");
      ElementDefinition element = instanceOfStructureDefinition.findElementByPath(nonNumericPath, fisher);
      if (element == null) continue;
      // go through the parts, and make sure that we have a useful index, and maybe a named slice
      Object current = instanceDef;
      String currentPath = "";
      for (int i = 0; i < pathParts.size(); i++) {
        PathPart pathPart = pathParts.get(i);
        currentPath += (currentPath.isEmpty() ? "" : ".") + pathPart.base;
        // If this is a primitive and the path continues to a nested element of the primitive,
        // then we need to look at the special property that starts with _ instead.
        String key = pathPart.isPrimitive() && i < pathParts.size() - 1 ? "_" + pathPart.base : pathPart.base;
        Integer ruleIndex = getArrayIndex(pathPart);
        Integer effectiveIndex = ruleIndex;
        String sliceName = null;
        if (ruleIndex != null) {
          // If the array doesn't exist, create it
          if (pathPart.isPrimitive()) {
            if (getProp(current, pathPart.base) == null) setProp(current, pathPart.base, new ArrayList<>());
            if (getProp(current, "_" + pathPart.base) == null) setProp(current, "_" + pathPart.base, new ArrayList<>());
          } else if (getProp(current, key) == null) {
            setProp(current, key, new ArrayList<>());
          }
          sliceName = pathPart.brackets != null ? getSliceName(pathPart) : null;
          if (Js.truthy(sliceName)) {
            // Determine the path to the slice
            String slicePath = currentPath + "[" + sliceName.replace("/", "][") + "]";
            knownSlices.put(slicePath, Math.max(ruleIndex + 1, knownSlices.getOrDefault(slicePath, 0)));
            List<Integer> sliceIndices = sliceIndices(getProp(current, pathPart.base), sliceName, pathPart.base,
                fisher);
            // Convert the index in terms of the slice to the corresponding index in the overall array
            if (ruleIndex >= sliceIndices.size()) {
              effectiveIndex = ruleIndex - sliceIndices.size() + arr(current, pathPart.base).size();
            } else {
              effectiveIndex = sliceIndices.get(ruleIndex);
            }
          } else {
            // This is an array entry that does not have a named slice (so a typical numeric index)
            knownSlices.put(currentPath, Math.max(effectiveIndex + 1, knownSlices.getOrDefault(currentPath, 0)));
          }
          if (pathPart.brackets != null) {
            StringBuilder sb = new StringBuilder(currentPath);
            for (String b : pathPart.brackets) if (!b.equals("0")) sb.append('[').append(b).append(']');
            currentPath = sb.toString();
          }
          // If the index doesn't exist in the array, add it and lesser indices
          // Empty elements should be null, not undefined, according to https://www.hl7.org/fhir/json.html#primitive
          for (int j = 0; j <= effectiveIndex; j++) {
            List<Object> cur = arr(current, key);
            if (j < cur.size() && j == effectiveIndex && cur.get(effectiveIndex) == null) {
              if (pathPart.isPrimitive()) {
                listSet(arr(current, pathPart.base), effectiveIndex, new JsObject());
                listSet(arr(current, "_" + pathPart.base), effectiveIndex, new JsObject());
              } else {
                cur.set(effectiveIndex, new JsObject());
              }
            } else if (j >= cur.size()) {
              if (Js.truthy(sliceName)) {
                // _sliceName is used to later differentiate which slice an element represents
                if (pathPart.isPrimitive()) {
                  arr(current, pathPart.base).add(sliceObj(sliceName));
                  arr(current, "_" + pathPart.base).add(sliceObj(sliceName));
                } else {
                  cur.add(sliceObj(sliceName));
                }
              } else if (j == effectiveIndex) {
                if (pathPart.isPrimitive()) {
                  arr(current, pathPart.base).add(new JsObject());
                  arr(current, "_" + pathPart.base).add(new JsObject());
                } else {
                  cur.add(new JsObject());
                }
              } else {
                if (pathPart.isPrimitive()) {
                  arr(current, pathPart.base).add(null);
                  arr(current, "_" + pathPart.base).add(null);
                } else {
                  cur.add(null);
                }
              }
            }
          }
          // If it isn't the last element, move on
          if (i < pathParts.size() - 1) {
            current = arr(current, key).get(effectiveIndex);
          }
        } else if (i < pathParts.size() - 1) {
          // if we're not dealing with an array element, just traverse the element tree.
          if (getProp(current, key) == null) {
            setProp(current, key, new JsObject());
          }
          current = getProp(current, key);
        }
      }
    }
    return knownSlices;
  }

  /**
   * Looks through the rules on an instance to determine what slices will be created when the instance is exported.
   */
  public static Map<String, Integer> determineKnownSlices(StructureDefinition instanceOfStructureDefinition,
      Map<String, ? extends HasPathParts> ruleMap, Fishable fisher) {
    Map<String, Integer> knownSlices = new LinkedHashMap<>();
    for (Map.Entry<String, ? extends HasPathParts> entry : ruleMap.entrySet()) {
      String path = entry.getKey();
      List<PathPart> pathParts = entry.getValue().pathParts();
      String nonNumericPath = NUMERIC_BRACKET.matcher(path).replaceAll("");
      ElementDefinition element = instanceOfStructureDefinition.findElementByPath(nonNumericPath, fisher);
      if (element == null) continue;
      String currentPath = "";
      for (PathPart pathPart : pathParts) {
        currentPath += (currentPath.isEmpty() ? "" : ".") + pathPart.base;
        Integer ruleIndex = getArrayIndex(pathPart);
        if (ruleIndex != null) {
          String sliceName = pathPart.brackets != null ? getSliceName(pathPart) : null;
          if (Js.truthy(sliceName)) {
            String slicePath = currentPath + "[" + sliceName.replace("/", "][") + "]";
            knownSlices.put(slicePath, Math.max(ruleIndex + 1, knownSlices.getOrDefault(slicePath, 0)));
          } else {
            knownSlices.put(currentPath, Math.max(ruleIndex + 1, knownSlices.getOrDefault(currentPath, 0)));
          }
          if (pathPart.brackets != null) {
            StringBuilder sb = new StringBuilder(currentPath);
            for (String b : pathPart.brackets) if (!b.equals("0")) sb.append('[').append(b).append(']');
            currentPath = sb.toString();
          }
        }
      }
    }
    return knownSlices;
  }

  /** TS ElementTrace. */
  private static final class ElementTrace {
    final ElementDefinition def;
    final List<String> history;
    final boolean ghost; // element won't appear on the instance but its fixed value may be needed
    final String requirementRoot;

    ElementTrace(ElementDefinition def, List<String> history, boolean ghost, String requirementRoot) {
      this.def = def;
      this.history = history;
      this.ghost = ghost;
      this.requirementRoot = requirementRoot;
    }
  }

  private static final Pattern X_AND_REST = Pattern.compile("\\[x].*");
  private static final Pattern COLON_TO_END = Pattern.compile(":(.*)\\z");
  private static final Pattern X = Pattern.compile("\\[x]");

  private static String normalizeReslice(String s) {
    return COLON_TO_END.matcher(s).replaceFirst("[$1]").replace("/", "][");
  }

  /** JS {@code a > b} where a may be undefined (NaN comparisons are false). */
  private static boolean gt(Double a, double b) {
    return a != null && a > b;
  }

  private static String last(List<String> l) {
    return l.isEmpty() ? null : l.get(l.size() - 1);
  }

  private static String firstFixedOrPatternKey(ElementDefinition def) {
    for (String k : keysOf(def)) {
      if (k.startsWith("fixed") || k.startsWith("pattern")) return k;
    }
    return null;
  }

  public static void setImpliedPropertiesOnInstance(Object instanceDef,
      StructureDefinition instanceOfStructureDefinition, List<String> paths, List<String> assignedResourcePaths,
      Fishable fisher) {
    setImpliedPropertiesOnInstance(instanceDef, instanceOfStructureDefinition, paths, assignedResourcePaths, fisher,
        new LinkedHashMap<>(), false);
  }

  public static void setImpliedPropertiesOnInstance(Object instanceDef,
      StructureDefinition instanceOfStructureDefinition, List<String> paths, List<String> assignedResourcePaths,
      Fishable fisher, Map<String, Integer> knownSlices) {
    setImpliedPropertiesOnInstance(instanceDef, instanceOfStructureDefinition, paths, assignedResourcePaths, fisher,
        knownSlices, false);
  }

  /**
   * NOTE: There is a thorough explanation of this function in sushi's src/fhirtypes/common-README.md that will
   * hopefully explain some of the intricacies of function. Good luck out there.
   */
  public static void setImpliedPropertiesOnInstance(Object instanceDef,
      StructureDefinition instanceOfStructureDefinition, List<String> pathsIn, List<String> assignedResourcePaths,
      Fishable fisher, Map<String, Integer> knownSlices, boolean manualSliceOrdering) {
    if (knownSlices == null) knownSlices = new LinkedHashMap<>();
    // normalize reslice style to multiple brackets
    List<String> paths = new ArrayList<>(pathsIn.size());
    for (String p : pathsIn) paths.add(p.replace("/", "]["));
    // Record the values implied by the structure definition in sdRuleMap
    Map<String, Object> sdRuleMap = new LinkedHashMap<>();
    Map<String, String> requirementRoots = new LinkedHashMap<>();
    // implied values may be applicable to slices
    Map<String, Object> assignedValueStorage = new LinkedHashMap<>();
    List<ElementDefinition> topLevelElements = instanceOfStructureDefinition.elements.get(0).children(true);
    java.util.ArrayDeque<ElementTrace> elementsToCheck = new java.util.ArrayDeque<>();
    for (ElementDefinition el : topLevelElements) {
      String requirementRoot;
      if (gt(el.getMin(), 0)) {
        requirementRoot = "";
      } else {
        requirementRoot = last(splitOnPathPeriods(el.getId()));
        if (requirementRoot.contains("[x]") && el.getType() != null && el.getType().size() == 1) {
          requirementRoot = X_AND_REST.matcher(requirementRoot)
              .replaceFirst(Matcher.quoteReplacement(upperFirst(el.getType().get(0).getCode())));
        }
        // normalize reslice style to multiple brackets
        requirementRoot = normalizeReslice(requirementRoot);
      }
      elementsToCheck.add(new ElementTrace(el, new ArrayList<>(), false, requirementRoot));
    }
    Map<String, Double> effectiveMins = new LinkedHashMap<>();
    while (!elementsToCheck.isEmpty()) {
      ElementTrace currentElement = elementsToCheck.removeFirst();
      ElementDefinition def = currentElement.def;
      String nextTracePart = last(splitOnPathPeriods(def.getId()));
      if (nextTracePart.contains("[x]") && def.getType() != null && def.getType().size() == 1) {
        // if the type slice exists, and we end with [x], don't change it. otherwise, change it.
        if (Js.truthy(def.getSliceName()) || def.getSlices().isEmpty()) {
          nextTracePart = X_AND_REST.matcher(nextTracePart)
              .replaceFirst(Matcher.quoteReplacement(upperFirst(def.getType().get(0).getCode())));
        }
      }
      // normalize reslice style to multiple brackets
      nextTracePart = normalizeReslice(nextTracePart);
      List<String> traceParts = new ArrayList<>(currentElement.history);
      traceParts.add(nextTracePart);
      String tracePath = String.join(".", traceParts);
      if (!effectiveMins.containsKey(tracePath)) {
        SliceTree.SliceNode sliceTree = SliceTree.buildSliceTree(def);
        String keyStart = String.join(".", currentElement.history);
        if (keyStart.length() > 0) {
          keyStart += ".";
        }
        SliceTree.calculateSliceTreeCounts(sliceTree, knownSlices, keyStart);
        java.util.ArrayDeque<SliceTree.SliceNode> visitList = new java.util.ArrayDeque<>();
        visitList.add(sliceTree);
        while (!visitList.isEmpty()) {
          SliceTree.SliceNode next = visitList.removeFirst();
          String traceKey = tracePath;
          // add the slice name for non-choice slices
          String nextSliceName = next.element.getSliceName();
          if (Js.truthy(nextSliceName) && !String.valueOf(getProp(next.element.getBase(), "path")).endsWith("[x]")) {
            traceKey += "[" + nextSliceName.replace("/", "][") + "]";
          }
          effectiveMins.put(traceKey, next.count);
          visitList.addAll(next.children);
        }
      }
      Double finalMinBoxed = effectiveMins.get(tracePath);
      double finalMin = finalMinBoxed == null ? Double.NaN : finalMinBoxed;
      // does a rule path match the trace path?
      String matchingRule = null;
      for (String p : paths) {
        if (p.equals(tracePath) || p.startsWith(tracePath + ".")) {
          matchingRule = p;
          break;
        }
      }
      // check for assigned values regardless of this element's effective min,
      // since it may have required slices that will need to know about the assigned value
      String assignedValueKey = firstFixedOrPatternKey(def);
      Object foundAssignedValue = assignedValueKey == null ? null : Js.cloneDeep(def.getProperty(assignedValueKey));
      List<ElementDefinition> connectedElements = def.findConnectedElements();
      if (foundAssignedValue == null) {
        // check assigned value storage
        foundAssignedValue = assignedValueStorage.get(def.getId());
      } else {
        // add to assigned value storage
        for (ElementDefinition connectedEl : connectedElements) {
          assignedValueStorage.put(connectedEl.getId(), foundAssignedValue);
        }
      }
      // if our def has a min > 0, potentially update min on connected defs by reapplying our own cardinality
      if (gt(def.getMin(), 0)) {
        for (ElementDefinition ce : connectedElements) {
          if (ce.getMin() != null && ce.getMin() < def.getMin() && !ce.getId().startsWith(def.getId())) {
            ce.constrainCardinality(def.getMin(), "");
            if (ce.children().isEmpty()) {
              ce.unfold(fisher);
            }
          }
        }
      }
      if (finalMin > 0) {
        if (foundAssignedValue != null && !currentElement.ghost) {
          String ip = tracePath;
          if (X.matcher(ip).find()) {
            // Fix any single-type choices to be type-specific (e.g., value[x] -> valueString)
            List<String> parts = splitOnPathPeriods(ip);
            for (int i = 0; i < parts.size(); i++) {
              if (parts.get(i).endsWith("[x]")) {
                ElementDefinition partEl = instanceOfStructureDefinition
                    .findElementByPath(String.join(".", parts.subList(0, i + 1)), fisher);
                if (partEl != null && partEl.getType() != null && partEl.getType().size() == 1) {
                  parts.set(i, parts.get(i).replaceFirst(Pattern.quote("[x]"),
                      Matcher.quoteReplacement(upperFirst(partEl.getType().get(0).getCode()))));
                }
              }
            }
            ip = String.join(".", parts);
            // If there is still a [x], we couldn't fix it, so skip it
            if (X.matcher(ip).find()) {
              ip = null;
            }
          }
          if (Js.truthy(ip)) {
            // set it for each instance of this element
            for (int idx = 0; idx < finalMin; idx++) {
              String numericPath = ip + (idx > 0 ? "[" + idx + "]" : "");
              sdRuleMap.put(numericPath, foundAssignedValue);
              requirementRoots.put(numericPath, currentElement.requirementRoot);
            }
          }
        }
        // check the children for instance of this element
        List<ElementDefinition> children = def.children(true);
        // special handling: if our current element has no slice name, we need guarantee the defined minimum
        int existingSliceCount = 0;
        if (def.getMin() != null && finalMin < def.getMin() && def.getSliceName() == null) {
          // our final min was lowered by slices, so add that to our indices.
          for (ElementDefinition el : def.getSlices()) {
            String slicePath = tracePath + "[" + el.getSliceName() + "]";
            if (knownSlices.containsKey(slicePath)) {
              existingSliceCount += knownSlices.get(slicePath);
            }
          }
        }
        for (int idx = 0; idx < finalMin; idx++) {
          int effectiveIdx = idx + existingSliceCount;
          String newHistory = last(traceParts) + (effectiveIdx > 0 ? "[" + effectiveIdx + "]" : "");
          List<String> history = new ArrayList<>(currentElement.history);
          history.add(newHistory);
          for (ElementDefinition child : children) {
            elementsToCheck.add(new ElementTrace(child, history, currentElement.ghost,
                gt(def.getMin(), idx) ? currentElement.requirementRoot : String.join(".", history)));
          }
        }
      } else if (matchingRule != null || gt(def.getMin(), 0)) {
        // the definition min could be > 0 when the final min is 0 if slices fill it all the way up
        if (matchingRule != null && foundAssignedValue != null && !currentElement.ghost) {
          sdRuleMap.put(tracePath, foundAssignedValue);
          requirementRoots.put(tracePath, currentElement.requirementRoot);
        }
        List<ElementDefinition> children = def.children(true);
        // if the matching rule assigns a resource, we don't need to dig into it any deeper
        if (children.isEmpty() && !assignedResourcePaths.contains(matchingRule)) {
          def.unfold(fisher);
          children = def.children(true);
        }
        String newHistory = last(traceParts);
        List<String> history = new ArrayList<>(currentElement.history);
        history.add(newHistory);
        for (ElementDefinition child : children) {
          elementsToCheck.add(new ElementTrace(child, history, matchingRule == null,
              gt(child.getMin(), 0) ? currentElement.requirementRoot : String.join(".", history)));
        }
      }
    }

    // we mostly want to assign rules in the order we get them, with one exception:
    // a path must come before its ancestors.
    List<PathNode> rulePaths = new ArrayList<>();
    for (String path : sdRuleMap.keySet()) rulePaths.add(new PathNode(path));
    List<PathNode> pathTree = buildPathTree(rulePaths);
    List<String> sortedRulePaths = traverseRulePathTree(pathTree);
    if (!manualSliceOrdering) {
      // This sort function simulates the original implementation of setImpliedPropertiesOnInstance
      final List<String> fpaths = paths;
      jsSort(sortedRulePaths, (a, b) -> {
        String aRoot = requirementRoots.get(a);
        String bRoot = requirementRoots.get(b);
        if (java.util.Objects.equals(aRoot, bRoot)) {
          // the winner is whoever has more path overlap on the first rule appearance
          String firstRule = null;
          for (String path : fpaths) {
            if (path.equals(aRoot) || path.startsWith(aRoot + ".")) {
              firstRule = path;
              break;
            }
          }
          if (firstRule != null) {
            List<String> firstRuleSplit = splitOnPathPeriods(firstRule);
            List<String> splitA = splitOnPathPeriods(a);
            List<String> splitB = splitOnPathPeriods(b);
            int n = Math.max(firstRuleSplit.size(), Math.max(splitA.size(), splitB.size()));
            for (int i = 0; i < n; i++) {
              String firstPart = i < firstRuleSplit.size() ? firstRuleSplit.get(i) : null;
              String aPart = i < splitA.size() ? splitA.get(i) : null;
              String bPart = i < splitB.size() ? splitB.get(i) : null;
              if (firstPart == null) {
                return 0;
              }
              boolean fa = firstPart.equals(aPart);
              boolean fb = firstPart.equals(bPart);
              if (fa && !fb) return -1;
              if (!fa && fb) return 1;
              if (!fa && !fb) return 0;
            }
          }
          return 0;
        }
        // if one is an ancestor of the other, use whichever appears first in the list of rules.
        // if the first appearance is the same rule for both, use the deeper element first
        int firstA = indexOfRoot(fpaths, aRoot);
        int firstB = indexOfRoot(fpaths, bRoot);
        if (firstA == firstB) {
          return bRoot.length() - aRoot.length();
        }
        // if a and b have different requirement roots, but neither is an ancestor of the other, use rule order
        return firstA - firstB;
      });
    }
    for (String path : sortedRulePaths) {
      StructureDefinition.ValidateValueResult r = instanceOfStructureDefinition.validateValueAtPath(path, null, fisher);
      setPropertyOnInstance(instanceDef, r.pathParts(), sdRuleMap.get(path), fisher);
    }
  }

  private static int indexOfRoot(List<String> paths, String root) {
    for (int i = 0; i < paths.size(); i++) {
      String path = paths.get(i);
      if (path.equals(root) || path.startsWith(root + ".")) return i;
    }
    return -1;
  }

  /**
   * Array.prototype.sort: V8 and Java both use a stable TimSort, so results match for consistent comparators. Java
   * throws "Comparison method violates its general contract" where V8 just produces some order; fall back to a
   * stable binary insertion sort (what both TimSorts do for short arrays) in that case.
   */
  public static <T> void jsSort(List<T> list, Comparator<? super T> c) {
    List<T> copy = new ArrayList<>(list);
    try {
      list.sort(c);
    } catch (IllegalArgumentException e) {
      list.clear();
      for (T x : copy) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
          int mid = (lo + hi) >>> 1;
          if (c.compare(x, list.get(mid)) < 0) hi = mid;
          else lo = mid + 1;
        }
        list.add(lo, x);
      }
    }
  }

  /** JS String(v) of a raw property value (undefined prints "undefined"). */
  static String toStrUndef(Object raw) {
    return raw == Js.UNDEFINED ? "undefined" : Js.toStr(raw);
  }

  /** TS PathNode. */
  private static final class PathNode {
    final String path;
    List<PathNode> children;

    PathNode(String path) {
      this.path = path;
    }
  }

  private static List<PathNode> buildPathTree(List<PathNode> paths) {
    List<PathNode> topLevelChildren = new ArrayList<>();
    for (PathNode p : paths) insertIntoTree(topLevelChildren, p);
    return topLevelChildren;
  }

  private static void insertIntoTree(List<PathNode> currentElements, PathNode el) {
    // if we find something that could be this element's parent, we traverse downwards
    PathNode parent = null;
    for (PathNode current : currentElements) {
      if (el.path.startsWith(current.path)) {
        parent = current;
        break;
      }
    }
    if (parent != null) {
      insertIntoTree(parent.children, el);
    } else {
      // otherwise, we will add at the current level
      // the current level could contain elements that should be the new element's children
      List<PathNode> children = new ArrayList<>();
      for (Iterator<PathNode> it = currentElements.iterator(); it.hasNext();) {
        PathNode current = it.next();
        if (current.path.startsWith(el.path)) {
          children.add(current);
          it.remove();
        }
      }
      el.children = children;
      currentElements.add(el);
    }
  }

  private static List<String> traverseRulePathTree(List<PathNode> elements) {
    List<String> result = new ArrayList<>();
    for (PathNode el : elements) {
      result.addAll(traverseRulePathTree(el.children));
      result.add(el.path);
    }
    return result;
  }

  /** Sets the value at the given path parts on an instance (SD, ED, InstanceDefinition, CodeSystem, ValueSet). */
  public static void setPropertyOnInstance(Object instance, List<PathPart> pathParts, Object assignedValue,
      Fishable fisher) {
    if (assignedValue == null) return;
    // If we can assign the value on the StructureDefinition StructureDefinition, then we can set the
    // instance property here
    Object current = instance;
    for (int i = 0; i < pathParts.size(); i++) {
      PathPart pathPart = pathParts.get(i);
      // When a primitive has child elements, a _ is appended to the name of the primitive
      String key = pathPart.isPrimitive() && i < pathParts.size() - 1 ? "_" + pathPart.base : pathPart.base;
      // If this part of the path indexes into an array, the index will be the last bracket
      Integer index = getArrayIndex(pathPart);
      if (index != null) {
        String under = "_" + pathPart.base;
        if (pathPart.isPrimitive()) {
          // we may need to create or update one or both arrays
          if (getProp(current, pathPart.base) == null) {
            Object u = getProp(current, under);
            List<Object> created = new ArrayList<>();
            if (u instanceof List<?> ul) {
              for (Object x : ul) created.add(sliceNameOf(x) != null ? sliceObj(sliceNameOf(x)) : null);
            }
            setProp(current, pathPart.base, created);
          }
          if (getProp(current, under) == null) {
            List<Object> created = new ArrayList<>();
            for (Object x : arr(current, pathPart.base)) {
              created.add(sliceNameOf(x) != null ? sliceObj(sliceNameOf(x)) : null);
            }
            setProp(current, under, created);
          }
        } else if (getProp(current, key) == null) {
          // if the array doesn't exist, create it
          setProp(current, key, new ArrayList<>());
        }
        String sliceName = getSliceName(pathPart);
        if (Js.truthy(sliceName)) {
          List<Integer> sliceIndices = sliceIndices(getProp(current, pathPart.base), sliceName, pathPart.base, fisher);
          // Convert the index in terms of the slice to the corresponding index in the overall array
          if (index >= sliceIndices.size()) {
            index = index - sliceIndices.size() + arr(current, key).size();
          } else {
            index = sliceIndices.get(index);
          }
        }
        List<Object> cur = arr(current, key);
        if (index >= cur.size()) {
          // Add only the missing elements, starting from the current length
          for (int j = cur.size(); j <= index; j++) {
            if (Js.truthy(sliceName)) {
              // _sliceName is used to later differentiate which slice an element represents
              if (pathPart.isPrimitive()) {
                arr(current, pathPart.base).add(sliceObj(sliceName));
                arr(current, under).add(sliceObj(sliceName));
              } else {
                cur.add(sliceObj(sliceName));
              }
            } else if (j == index) {
              if (pathPart.isPrimitive()) {
                arr(current, pathPart.base).add(new JsObject());
                arr(current, under).add(new JsObject());
              } else {
                cur.add(new JsObject());
              }
            } else {
              if (pathPart.isPrimitive()) {
                arr(current, pathPart.base).add(null);
                arr(current, under).add(null);
              } else {
                cur.add(null);
              }
            }
          }
        } else if (cur.get(index) == null) {
          // the index exists but is empty, so fill it in
          if (pathPart.isPrimitive()) {
            // a value may already exist on one of the arrays, so only assign an empty object if it is nullish
            List<Object> a = arr(current, pathPart.base);
            if (index >= a.size() || a.get(index) == null) listSet(a, index, new JsObject());
            List<Object> b = arr(current, under);
            if (index >= b.size() || b.get(index) == null) listSet(b, index, new JsObject());
          } else {
            cur.set(index, new JsObject());
          }
        }
        // If it isn't the last element, move on, if it is, set the value
        if (i < pathParts.size() - 1) {
          current = arr(current, key).get(index);
          if (Js.truthy(sliceName)) {
            setProp(current, "_sliceName", sliceName);
          }
        } else {
          Object value = assignedValue;
          if (Js.truthy(sliceName) && !isJsObjectType(value)) {
            // When an assignedValue is a primitive but also a slice, we convert to an object so that
            // the sliceName field can be tracked on the object. The _primitive field marks the object
            // to later be converted back to a primitive by replaceField in cleanResource
            value = new JsObject().with("assignedValue", value).with("_primitive", true);
          }
          List<Object> target = arr(current, key);
          if (isJsObjectType(value)) {
            Object dest = target.get(index);
            for (String k : keysOf(value)) setProp(dest, k, getRaw(value, k));
          } else {
            target.set(index, value);
          }
        }
      } else {
        // If it isn't the last element, move on, if it is, set the value
        if (i < pathParts.size() - 1) {
          if (getProp(current, key) == null) setProp(current, key, new JsObject());
          current = getProp(current, key);
        } else {
          // Check if the instance already has the element being defined
          Object existing = getProp(current, key);
          if (existing != null && isJsObjectType(existing)) {
            // If the instance already has the element, we should merge it
            assignComplexValue(existing, assignedValue);
          } else {
            if (pathPart.isPrimitive() && isJsObjectType(assignedValue)) {
              // If setting a primitive value that is an object (an inline instance), set
              // the value directly and any other values on the _ property
              if (getProp(assignedValue, "value") != null) {
                setProp(current, key, getProp(assignedValue, "value"));
                deleteProp(assignedValue, "value");
              }
              if (!isEmpty(assignedValue)) {
                setProp(current, "_" + key, assignedValue);
              }
            } else {
              setProp(current, key, assignedValue);
            }
          }
        }
      }
    }
  }

  /** TS {@code typeof x === 'object' && x._primitive === true} (throws on null, like TS). */
  private static boolean isPrimitiveMarker(Object raw) {
    if (raw == Js.UNDEFINED || !isJsObjectType(raw)) return false;
    if (raw == null) throw new JsTypeError("Cannot read properties of null (reading '_primitive')");
    return Boolean.TRUE.equals(getProp(raw, "_primitive"));
  }

  private static List<String> objectKeys(Object o) {
    if (o == null || o == Js.UNDEFINED) throw new JsTypeError("Cannot convert undefined or null to object");
    return keysOf(o);
  }

  @SuppressWarnings("unchecked")
  private static void assignComplexValue(Object current, Object assignedValue) {
    // checking that current is an array is a little redundant, but is useful for the type checker
    if (assignedValue instanceof List<?> assignedList && current instanceof List<?> currentList0) {
      List<Object> currentList = (List<Object>) currentList0;
      // for each element of assignedValue, make a compatible element on current
      for (Object assignedElement : assignedList) {
        if (!isJsObjectType(assignedElement)) {
          // if assignedElement is not an object: is there an existing element that is equal? if not, append
          boolean found = false;
          for (Object currentElement : currentList) {
            if ((isPrimitiveMarker(currentElement)
                && getProp(currentElement, "assignedValue") == assignedValue)
                || strictEquals(currentElement, assignedElement)) {
              found = true;
              break;
            }
          }
          if (!found) {
            currentList.add(assignedElement);
          }
        } else {
          // if assignedElement is an object: is there an existing element that has all the attributes?
          List<String> assignedKeys = null;
          boolean perfectMatch = false;
          for (Object currentElement : currentList) {
            if (currentElement == null) continue;
            if (assignedKeys == null) assignedKeys = objectKeys(assignedElement);
            boolean all = true;
            for (String assignedKey : assignedKeys) {
              if (!Js.isEqual(reversePrimitive(getRaw(assignedElement, assignedKey)),
                  reversePrimitive(getRaw(currentElement, assignedKey)))) {
                all = false;
                break;
              }
            }
            if (all) {
              perfectMatch = true;
              break;
            }
          }
          if (!perfectMatch) {
            int partialMatch = -1;
            for (int idx = 0; idx < currentList.size(); idx++) {
              Object currentElement = currentList.get(idx);
              if (currentElement == null) {
                partialMatch = idx;
                break;
              }
              if (assignedKeys == null) assignedKeys = objectKeys(assignedElement);
              boolean all = true;
              for (String assignedKey : assignedKeys) {
                if (!(getProp(currentElement, assignedKey) == null || Js.isEqual(
                    reversePrimitive(getRaw(assignedElement, assignedKey)),
                    reversePrimitive(getRaw(currentElement, assignedKey))))) {
                  all = false;
                  break;
                }
              }
              if (all) {
                partialMatch = idx;
                break;
              }
            }
            if (partialMatch > -1) {
              // we may have found a partial match at a null element. if so, create an empty object
              if (currentList.get(partialMatch) == null) {
                currentList.set(partialMatch, new JsObject());
              }
              assignComplexValue(currentList.get(partialMatch), assignedElement);
            } else {
              currentList.add(assignedElement);
            }
          }
        }
      }
    } else {
      // assignedValue is a non-array object, so assign recursively
      for (String key : objectKeys(assignedValue)) {
        Object av = getRaw(assignedValue, key);
        if (isJsObjectType(av)) {
          if (getProp(current, key) == null) {
            setProp(current, key, av instanceof List<?> ? new ArrayList<>() : new JsObject());
          }
          assignComplexValue(getProp(current, key), av);
        } else {
          Object cur = getRaw(current, key);
          if (isPrimitiveMarker(cur)) {
            setProp(cur, "assignedValue", av);
          } else {
            setProp(current, key, av);
          }
        }
      }
    }
  }

  /** Turns an assigned-primitive back into its primitive value if and only if it has no other properties. */
  private static Object reversePrimitive(Object element) {
    if (isPrimitiveMarker(element)) {
      List<String> keys = keysOf(element);
      if (keys.contains("assignedValue") && keys.size() == 2) {
        return getRaw(element, "assignedValue");
      }
    }
    return element;
  }

  private static final Pattern SIGNED_INT = Pattern.compile("^[-+]?\\d+\\z");

  /** The index if the last bracket is a non-negative integer, else null. */
  public static Integer getArrayIndex(PathPart pathPart) {
    if (pathPart.brackets == null || pathPart.brackets.isEmpty()) return null;
    String lastBracket = pathPart.brackets.get(pathPart.brackets.size() - 1);
    if (lastBracket == null || !SIGNED_INT.matcher(lastBracket).find()) return null;
    double arrayIndex = Double.parseDouble(lastBracket.startsWith("+") ? lastBracket.substring(1) : lastBracket);
    if (!(arrayIndex >= 0)) return null;
    return (int) Math.min(arrayIndex, Integer.MAX_VALUE);
  }

  /**
   * Replaces a reference to an item by name or id with the correct relative path to that item. Replaces a reference
   * to a local code system by the url for that code system. Returns a clone of the rule if replacing is done,
   * otherwise the original rule.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Rule> T replaceReferences(T rule, FSHTank tank, Fishable fisher) {
    T clone = null;
    Object value = ruleValue(rule);
    if (value instanceof FshReference ref) {
      String type = null;
      String id = null;
      Metadata instanceMeta = null;
      // Prefer resolving to instances, so look them up first
      Object instanceObj = tank.fish(ref.reference, FishableType.Instance);
      if (instanceObj instanceof Instance instance) {
        instanceMeta = fisher.fishForMetadata(instance.instanceOf, FishableType.Resource, FishableType.Logical,
            FishableType.Type, FishableType.Profile, FishableType.Extension);
        if (instanceMeta != null) {
          type = instanceMeta.sdType;
          id = instance.getId();
        }
      }
      // If we didn't find an instance, check other definitions in the tank
      if (type == null) {
        Object definition = tank.fish(ref.reference, FishableType.Profile, FishableType.Extension,
            FishableType.Logical, FishableType.Resource, FishableType.CodeSystem, FishableType.ValueSet);
        if (definition != null) {
          if (definition instanceof Profile || definition instanceof Extension || definition instanceof Logical
              || definition instanceof Resource) {
            type = "StructureDefinition";
            id = ((FshStructure) definition).getId();
          } else if (definition instanceof FshCodeSystem cs) {
            type = "CodeSystem";
            id = cs.getId();
          } else if (definition instanceof FshValueSet vs) {
            type = "ValueSet";
            id = vs.getId();
          }
        }
      }
      // If we still didn't find anything, broaden the scope to everything
      if (type == null) {
        Object fhir = fisher.fishForFHIR(ref.reference, FishableType.Instance, FishableType.Profile,
            FishableType.Extension, FishableType.Logical, FishableType.Resource, FishableType.CodeSystem,
            FishableType.ValueSet);
        if (fhir != null && Js.truthy(getProp(fhir, "resourceType")) && Js.truthy(getProp(fhir, "id"))) {
          type = Js.toStr(getProp(fhir, "resourceType"));
          id = Js.toStr(getProp(fhir, "id"));
        }
      }
      if (type != null && id != null) {
        clone = rule.<T>cloneDeep();
        FshReference assignedReference = (FshReference) ruleValue(clone);
        // Set the sdType which will be used for type-checking during assignment
        assignedReference.sdType = type;
        // Only replace references that are specified using name or id.
        if (!ref.reference.contains("/")) {
          assignedReference.reference = type + "/" + id;
        }
        // if type is a logical, it needs to have can-be-target characteristic.
        Metadata typeMeta = instanceMeta != null ? instanceMeta : fisher.fishForMetadata(type);
        if (typeMeta == null) {
          throw new JsTypeError("Cannot read properties of undefined (reading 'canBeTarget')");
        }
        if (Boolean.FALSE.equals(typeMeta.canBeTarget)) {
          String typeName = typeMeta.name != null ? typeMeta.name : typeMeta.id != null ? typeMeta.id : type;
          logger.warn("Referenced type " + typeName + " for logical instance " + ref.reference
              + " does not specify that it can be the target of a reference.", rule.sourceInfo);
        }
      } else {
        // if we still haven't found anything, there's one more possibility:
        // the reference includes a version, which it doesn't need.
        int firstPipe = ref.reference.indexOf('|');
        if (firstPipe > -1) {
          logger.warn("Reference assignments should not include a version.", rule.sourceInfo);
          clone = rule.<T>cloneDeep();
          ((FshReference) ruleValue(clone)).reference = ref.reference.substring(0, firstPipe);
          clone = replaceReferences(clone, tank, fisher);
        } else if (type == null && id == null && Js.truthy(ref.reference)) {
          // if the reference to an entity is provided but is unable to be resolved
          if (!(ref.reference.startsWith("urn:") || ref.reference.contains("/"))) {
            logger.warn("Cannot find the entity referenced at " + ref.reference
                + ". The provided reference value will be used, but this reference does not conform to the FHIR Reference format.",
                rule.sourceInfo);
          }
        }
      }
    } else if (value instanceof FshCode code) {
      // the version on a CodeSystem resource is not the same as the system's actual version out in the world.
      // so, they don't need to match.
      if (code.system != null) {
        String baseSystem = code.system.split("\\|", -1)[0];
        Metadata codeSystemMeta = fisher.fishForMetadata(baseSystem, FishableType.CodeSystem);
        if (codeSystemMeta != null) {
          clone = rule.<T>cloneDeep();
          FshCode assignedCode = (FshCode) ruleValue(clone);
          assignedCode.system = code.system.replaceFirst("^[^|]+",
              Matcher.quoteReplacement(Js.toStr(codeSystemMeta.url)));

          // Find the code system using the returned metadata to avoid duplicate warnings if version mismatches
          String matchedCanonical = Js.truthy(codeSystemMeta.url)
              ? codeSystemMeta.url + (Js.truthy(codeSystemMeta.version) ? "|" + codeSystemMeta.version : "")
              : code.system;
          Object codeSystem = FishingUtils.fishInTankBestVersion(tank, matchedCanonical, rule.sourceInfo,
              FishableType.CodeSystem);
          if (codeSystem instanceof FshCodeSystem || codeSystem instanceof Instance) {
            // if a local system was used, check to make sure the code is actually in that system
            listUndefinedLocalCodes((FshEntity) codeSystem, new ArrayList<>(List.of(assignedCode.code)), tank, rule);
          }
        }
      }
    }
    return clone != null ? clone : rule;
  }

  private static Object ruleValue(Rule rule) {
    if (rule instanceof AssignmentRule a) return a.value;
    if (rule instanceof CaretValueRule c) return c.value;
    return null;
  }

  private static final Pattern CONCEPT_RULE_PATH = Pattern.compile(
      "^(concept(\\[" + org.hl7.fhir.sushi.fshtypes.Common.JS_WS + "*(\\d+|\\+|=)"
          + org.hl7.fhir.sushi.fshtypes.Common.JS_WS + "*\\])?\\.)+code\\z");

  private static boolean conceptRulePathTest(String s) {
    return s != null && CONCEPT_RULE_PATH.matcher(s).find();
  }

  /** Logs an error if any of the codes is not defined by the local (complete) code system. */
  public static void listUndefinedLocalCodes(FshEntity codeSystem, List<String> codes, FSHTank tank,
      FshEntity sourceEntity) {
    List<String> undefinedCodes = new ArrayList<>();
    applyInsertRules(codeSystem, tank);
    if (codeSystem instanceof FshCodeSystem fcs) {
      boolean notComplete = false;
      for (Rule rule : fcs.rules) {
        if (rule instanceof CaretValueRule c && "".equals(c.path) && "content".equals(c.caretPath)
            && c.value instanceof FshCode fc && !"complete".equals(fc.code)) {
          notComplete = true;
          break;
        }
      }
      if (!notComplete) {
        // a concept may have been added by a ConceptRule or by a CaretValueRule.
        for (String code : codes) {
          boolean defined = false;
          for (Rule rule : fcs.rules) {
            if ((rule instanceof ConceptRule cr && java.util.Objects.equals(cr.code, code))
                || (rule instanceof CaretValueRule c && "".equals(c.path) && conceptRulePathTest(c.caretPath)
                    && c.value instanceof FshCode fc && java.util.Objects.equals(fc.code, code))) {
              defined = true;
              break;
            }
          }
          if (!defined) undefinedCodes.add(code);
        }
      }
    } else if (codeSystem instanceof Instance inst && inst.usage == InstanceUsage.Definition) {
      boolean complete = false;
      for (Rule rule : inst.rules) {
        if (rule instanceof AssignmentRule a && "content".equals(a.path) && a.value instanceof FshCode fc
            && "complete".equals(fc.code)) {
          complete = true;
          break;
        }
      }
      if (complete) {
        for (String code : codes) {
          boolean defined = false;
          for (Rule rule : inst.rules) {
            if (rule instanceof AssignmentRule a && conceptRulePathTest(a.path) && a.value instanceof FshCode fc
                && java.util.Objects.equals(fc.code, code)) {
              defined = true;
              break;
            }
          }
          if (!defined) undefinedCodes.add(code);
        }
      }
    }
    if (!undefinedCodes.isEmpty()) {
      List<String> quoted = new ArrayList<>();
      for (String code : undefinedCodes) quoted.add("\"" + code + "\"");
      String name = codeSystem instanceof FshCodeSystem f ? f.name : ((Instance) codeSystem).name;
      logger.error("Code" + (undefinedCodes.size() > 1 ? "s" : "") + " " + String.join(", ", quoted) + " "
          + (undefinedCodes.size() > 1 ? "are" : "is") + " not defined for system " + name + ".",
          sourceEntity.sourceInfo);
    }
  }

  /** Returns the sliceName for a path part (its non-numeric brackets joined with "/"). */
  public static String getSliceName(PathPart pathPart) {
    Integer arrayIndex = getArrayIndex(pathPart);
    if (pathPart.brackets == null) {
      throw new JsTypeError("Cannot read properties of undefined (reading 'join')");
    }
    List<String> nonNumericBrackets =
        arrayIndex == null ? pathPart.brackets : pathPart.brackets.subList(0, pathPart.brackets.size() - 1);
    return String.join("/", nonNumericBrackets);
  }

  /** Removes extensions (and optionally modifierExtensions) with any of the given URLs. */
  public static void removeMatchingExtensions(Object object, List<String> urls, boolean checkModifiers) {
    if (object == null) return;
    List<String> props = checkModifiers ? List.of("extension", "modifierExtension") : List.of("extension");
    for (String ext : props) {
      Object v = getProp(object, ext);
      if (v instanceof List<?> l && !l.isEmpty()) {
        // lodash pullAllWith
        l.removeIf(e -> {
          Object url = getProp(e, "url");
          for (String u : urls) if (strictEquals(url, u)) return true;
          return false;
        });
        if (l.isEmpty()) {
          deleteProp(object, ext);
        }
      }
    }
  }

  /** Replaces fields in an object that match a certain condition (recursively). */
  public static void replaceField(Object object, BiPredicate<Object, String> matchFn,
      BiConsumer<Object, String> replaceFn, Predicate<String> skipFn) {
    for (String prop : keysOf(object)) {
      // for..in skips properties deleted during the iteration
      if (!hasPropOrNull(object, prop)) continue;
      if (matchFn.test(object, prop)) {
        replaceFn.accept(object, prop);
      } else if (isJsObjectType(getRaw(object, prop)) && !skipFn.test(prop)) {
        Object child = getProp(object, prop);
        replaceField(child, matchFn, replaceFn, skipFn);

        // If the object[prop] was an array and all items were replaced by null using the replaceFn, get rid of the
        // whole array. Repeating FHIR primitives are two parallel arrays (value and _value). Keep a null-padded value
        // array when the companion _value array still has id or extension data so the arrays stay aligned.
        Object now = getProp(object, prop);
        if (now instanceof List<?> l && l.stream().allMatch(v -> v == null || v == Js.UNDEFINED)) {
          Object companion = prop.startsWith("_") ? null : getProp(object, "_" + prop);
          boolean companionHasData = companion instanceof List<?> cl
              && cl.stream().anyMatch(v -> v != null && v != Js.UNDEFINED && !isEmpty(v));
          if (!companionHasData) {
            deleteProp(object, prop);
          }
        }

        // Since an array could have been deleted, the whole object[prop] could have ended up as empty.
        if (matchFn.test(object, prop)) {
          replaceFn.accept(object, prop);
        }
      }
    }
    // TS for..in also walks an ElementDefinition's private own property _original (a full ElementDefinition), so
    // cleanResource cleans the stored original the same way as the element; otherwise calculateDiff sees e.g. an
    // `_aggregation: [{}]` left only in the original (corpus fut-infrastructure__implementation-guide).
    if (object instanceof ElementDefinition ed && ed.getOriginal() != null && !skipFn.test("_original")) {
      replaceField(ed.getOriginal(), matchFn, replaceFn, skipFn);
    }
  }

  private static boolean hasPropOrNull(Object o, String prop) {
    if (o instanceof List<?> l) return Integer.parseInt(prop) < l.size();
    return getRaw(o, prop) != Js.UNDEFINED || (o instanceof Map<?, ?> m && m.containsKey(prop));
  }

  public static void cleanResource(Object resourceDef) {
    cleanResource(resourceDef, p -> false);
  }

  /** Cleans up temporary properties that were added to the resource definition during processing. */
  public static void cleanResource(Object resourceDef, Predicate<String> skipFn) {
    // Remove all _sliceName fields
    replaceField(resourceDef, (o, p) -> p.equals("_sliceName"), (o, p) -> deleteProp(o, p), skipFn);
    // Change any {} to null
    replaceField(resourceDef, (o, p) -> {
      Object v = getRaw(o, p);
      return v != null && isJsObjectType(v) && isEmpty(v);
    }, (o, p) -> setProp(o, p, null), skipFn);

    // Change back any primitives that have been converted into objects by setPropertyOnInstance
    replaceField(resourceDef, (o, p) -> {
      Object v = getRaw(o, p);
      return v != null && isJsObjectType(v) && Js.truthy(getProp(v, "_primitive"));
    }, (o, p) -> setProp(o, p, getProp(getProp(o, p), "assignedValue")), skipFn);

    // Update references to any contained resources to be #id instead of resourceType/id
    Object contained = getProp(resourceDef, "contained");
    if (contained instanceof List<?> containedList) {
      for (Object containedResource : new ArrayList<>(containedList)) {
        String referenceString = containedResource != null
            ? toStrUndef(getRaw(containedResource, "resourceType")) + "/"
                + toStrUndef(getRaw(containedResource, "id"))
            : null;
        Object referenceUrl = getProp(containedResource, "url");
        String newRef = "#" + toStrUndef(getRaw(containedResource, "id"));
        replaceField(resourceDef, (o, p) -> {
          if (!REFERENCE_PROP.matcher(p).find()) return false;
          Object v = getProp(o, p);
          return strictEquals(v, referenceString) || (Js.truthy(referenceUrl) && strictEquals(v, referenceUrl));
        }, (o, p) -> setProp(o, p, newRef), skipFn);
      }
    }
  }

  private static final Pattern REFERENCE_PROP = Pattern.compile("(^|\\.)reference\\z");

  /** Adds insert rules onto a Profile, Extension, Logical, Resource, Instance, ValueSet, CodeSystem, ... */
  public static void applyInsertRules(FshEntity fshDefinition, FSHTank tank) {
    applyInsertRules(fshDefinition, tank, new ArrayList<>());
  }

  public static void applyInsertRules(FshEntity fshDefinition, FSHTank tank, List<String> seenRuleSets) {
    List<Rule> expandedRules = new ArrayList<>();
    for (Rule rule : org.hl7.fhir.sushi.fshtypes.Common.rulesOf(fshDefinition)) {
      if (!(rule instanceof InsertRule insertRule)) {
        expandedRules.add(rule);
        continue;
      }

      List<Object> idParts = new ArrayList<>();
      idParts.add(insertRule.ruleSet);
      idParts.addAll(insertRule.params);
      String ruleSetIdentifier = Json.stringify(idParts);
      RuleSet ruleSet;
      if (!insertRule.params.isEmpty()) {
        ruleSet = tank.fishForAppliedRuleSet(ruleSetIdentifier);
      } else {
        Object found = tank.fish(insertRule.ruleSet, FishableType.RuleSet);
        ruleSet = found instanceof RuleSet rs ? rs : null;
      }

      if (ruleSet == null) {
        logger.error("Unable to find definition for RuleSet " + insertRule.ruleSet + ".", insertRule.sourceInfo);
        continue;
      }
      if (seenRuleSets.contains(ruleSetIdentifier)) {
        logger.error("Inserting " + ruleSet.name + " will cause a circular dependency, so the rule will be ignored",
            insertRule.sourceInfo);
        continue;
      }
      // RuleSets may contain other RuleSets via insert rules on themselves, so before applying the rules
      // from a RuleSet, we must first recursively expand any insert rules on that RuleSet
      List<String> seen = new ArrayList<>(seenRuleSets);
      seen.add(ruleSetIdentifier);
      applyInsertRules(ruleSet, tank, seen);
      String context = insertRule.path;
      boolean firstRule = true;
      for (Rule original : ruleSet.rules) {
        Rule ruleSetRule = original;
        ruleSetRule.sourceInfo.appliedFile = insertRule.sourceInfo.file;
        ruleSetRule.sourceInfo.appliedLocation = insertRule.sourceInfo.location;
        // On the import side, there are some rules that syntactically match both ConceptRule and
        // ValueSetConceptComponentRule. When this happens, a ConceptRule is created with a value
        // set on its system. If we are applying rules to a ValueSet, and the ConceptRule has a
        // system, create a ValueSetConceptComponent that corresponds to the ConceptRule, and use that.
        // BUT! If we have a ConceptRule with a system, and we are applying rules to a CodeSystem,
        // log an error to let the author know to not do that.
        if (ruleSetRule instanceof ConceptRule cr && Js.truthy(cr.system)) {
          if (fshDefinition instanceof FshValueSet) {
            FshCode relatedCode = new FshCode(cr.code, cr.system, cr.display);
            ValueSetConceptComponentRule vsRule = new ValueSetConceptComponentRule(true);
            vsRule.concepts = new ArrayList<>(List.of(relatedCode));
            vsRule.from.system = relatedCode.system;
            ruleSetRule = vsRule;
          } else if (fshDefinition instanceof FshCodeSystem) {
            logger.error("Do not include the system when listing concepts for a code system.",
                ruleSetRule.sourceInfo);
          }
        }
        if (AllowedRules.isAllowedRule(fshDefinition, ruleSetRule)) {
          Rule ruleSetRuleClone = ruleSetRule.<Rule>cloneDeep();
          if (Js.truthy(context)) {
            String newPath = context;
            if (".".equals(ruleSetRuleClone.path)) {
              logger.error(
                  "The special '.' path is only allowed in top-level rules. The rule will be processed as if it is not indented.",
                  ruleSetRule.sourceInfo);
              newPath = ruleSetRuleClone.path;
            } else if (Js.truthy(ruleSetRuleClone.path)) {
              newPath += "." + ruleSetRuleClone.path;
            }
            ruleSetRuleClone.path = newPath;
          }
          if (!insertRule.pathArray.isEmpty()) {
            if (ruleSetRuleClone instanceof ConceptRule crc) {
              // strip the leading # when adding to the hierarchy
              List<String> stripped = new ArrayList<>();
              for (String code : insertRule.pathArray) stripped.add(code.substring(Math.min(1, code.length())));
              crc.hierarchy.addAll(0, stripped);
            } else if (ruleSetRuleClone instanceof CaretValueRule cvr) {
              cvr.pathArray.addAll(0, insertRule.pathArray);
            }
          }
          if (ruleSetRuleClone instanceof ConceptRule && fshDefinition instanceof FshCodeSystem
              && Js.truthy(context)) {
            // ConceptRules should not have a path context, so if one exists, show an error.
            // The concept is still added to the CodeSystem.
            logger.error("Do not insert a RuleSet at a path when the RuleSet adds a concept.",
                ruleSetRuleClone.sourceInfo);
          }
          expandedRules.add(ruleSetRuleClone);
          if (firstRule) {
            // Once one rule has been applied, all future rules should inherit the index used on that rule
            // rather than continuing to increment the index with the [+] operator
            context = context.replace("[+]", "[=]");
            firstRule = false;
          }
        } else {
          logger.error("Rule of type " + ruleSetRule.constructorName() + " cannot be applied to entity of type "
              + fshDefinition.constructorName(), ruleSetRule.sourceInfo);
        }
      }
    }
    setRules(fshDefinition, expandedRules);
  }

  public static boolean isInheritedResource(String resourceType, String type, Fishable fisher) {
    return isInheritedResource(resourceType, type, fisher, false);
  }

  /**
   * Tests if resourceType is a valid FHIR resource that is a subtype of type (Resource, DomainResource, or equal).
   */
  public static boolean isInheritedResource(String resourceType, String type, Fishable fisher,
      boolean allowProfile) {
    FishableType[] types = allowProfile ? new FishableType[] {FishableType.Resource, FishableType.Profile}
        : new FishableType[] {FishableType.Resource};
    Object resource = fisher.fishForFHIR(resourceType, types);
    if (resource == null) return false;
    String rt = resourceType;
    if (allowProfile) {
      Object v = getProp(resource, "resourceType");
      rt = v == null ? null : Js.toStr(v);
    }
    return "Resource".equals(type)
        || ("DomainResource".equals(type)
            // These are the only 3 resources not inherited from DomainResource
            && !List.of("Bundle", "Parameters", "Binary").contains(rt))
        || java.util.Objects.equals(type, rt);
  }

  /**
   * Determines the formal FHIR URL to use to refer to this entity: a caret value rule on url if present, else the
   * default url based on the configured canonical url.
   */
  public static String getUrlFromFshDefinition(FshEntity fshDefinition, String canonical) {
    org.hl7.fhir.sushi.fshtypes.Common.ValueFromRules urlFromFshRules =
        org.hl7.fhir.sushi.fshtypes.Common.getValueFromRules(fshDefinition, "url", "", "url");
    if (urlFromFshRules != null && urlFromFshRules.value() instanceof String s && !urlFromFshRules.isInstance()) {
      return s;
    }
    String fhirType;
    if (fshDefinition instanceof FshValueSet) {
      fhirType = "ValueSet";
    } else if (fshDefinition instanceof FshCodeSystem) {
      fhirType = "CodeSystem";
    } else if (fshDefinition instanceof Instance i
        && ("ValueSet".equals(i.instanceOf) || "CodeSystem".equals(i.instanceOf))) {
      // match the default URL that the InstanceExporter assigns to definitional instances
      fhirType = i.instanceOf;
    } else {
      fhirType = "StructureDefinition";
    }
    return canonical + "/" + fhirType + "/" + idOf(fshDefinition);
  }

  static String idOf(FshEntity def) {
    if (def instanceof FshStructure s) return s.getId();
    if (def instanceof FshValueSet v) return v.getId();
    if (def instanceof FshCodeSystem c) return c.getId();
    if (def instanceof Instance i) return i.getId();
    if (def instanceof RuleSet r) return r.name;
    if (def instanceof Invariant inv) return inv.name;
    if (def instanceof Mapping m) return m.getId();
    return null;
  }

  static String nameOf(FshEntity def) {
    if (def instanceof FshStructure s) return s.name;
    if (def instanceof FshValueSet v) return v.name;
    if (def instanceof FshCodeSystem c) return c.name;
    if (def instanceof Instance i) return i.name;
    if (def instanceof RuleSet r) return r.name;
    if (def instanceof Invariant inv) return inv.name;
    if (def instanceof Mapping m) return m.name;
    return null;
  }

  /** Determines the version of this entity: a caret value rule on version if present, else {@code version}. */
  public static String getVersionFromFshDefinition(FshEntity fshDefinition, String version) {
    org.hl7.fhir.sushi.fshtypes.Common.ValueFromRules versionFromFshRules =
        org.hl7.fhir.sushi.fshtypes.Common.getValueFromRules(fshDefinition, "version", "", "version");
    if (versionFromFshRules != null && versionFromFshRules.value() instanceof String s
        && !versionFromFshRules.isInstance()) {
      return s;
    }
    return version;
  }

  /**
   * Determines the formal FHIR type for logical models and resources (profiles and extensions keep the parent's
   * type): the last caret value rule on type if any, else the url (Logical) or id (Resource).
   */
  public static String getTypeFromFshDefinitionOrParent(FshStructure fshDefinition, StructureDefinition parentSD) {
    if (fshDefinition instanceof Profile || fshDefinition instanceof Extension) {
      return parentSD.getType();
    }
    CaretValueRule lastCaretValueRule = null;
    for (Rule rule : fshDefinition.rules) {
      if (rule instanceof CaretValueRule c && "".equals(c.path) && "type".equals(c.caretPath)) {
        lastCaretValueRule = c;
      }
    }
    if (lastCaretValueRule != null) {
      // Select last CaretValueRule with caretPath === 'type' because rules processing
      // ends up applying the last rule in the processing order
      String type = Js.toStr(lastCaretValueRule.value);
      if (lastCaretValueRule.value instanceof FshEntity) type = lastCaretValueRule.value.toString();
      // this value should only be a string, but that might change at some point
      if (fshDefinition instanceof Logical && !ValidUrl.isUri(type)) {
        logger.warn(type + " is an invalid type. Logical models require that the type be an absolute URL.",
            lastCaretValueRule.sourceInfo);
      }
      return type;
    }
    // Default type for logical model to the StructureDefinition url; otherwise default to the id meta property.
    return fshDefinition instanceof Logical ? parentSD.getUrl() : fshDefinition.getId();
  }

  public static boolean isExtension(String path) {
    return "modifierExtension".equals(path) || "extension".equals(path);
  }

  /** True if the extension's root element (id "Extension") in its snapshot is a modifier. */
  public static boolean isModifierExtension(Object extension) {
    Object snapshot = getProp(extension, "snapshot");
    if (snapshot == null) return false;
    Object elements = getProp(snapshot, "element");
    if (!(elements instanceof List<?> l)) {
      if (elements == null) throw new JsTypeError("Cannot read properties of undefined (reading 'find')");
      return false;
    }
    for (Object el : l) {
      Object id = el instanceof ElementDefinition ed ? ed.getId() : getProp(el, "id");
      if ("Extension".equals(id)) {
        Object isModifier = el instanceof ElementDefinition ed ? ed.getProperty("isModifier")
            : getProp(el, "isModifier");
        return Boolean.TRUE.equals(isModifier);
      }
    }
    return false;
  }

  /** Compares two canonical URLs for equality, ignoring a |version on either side. */
  public static boolean canonicalsAreEqualIgnoringVersion(String a, String b) {
    if (java.util.Objects.equals(a, b)) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.split("\\|", -1)[0].equals(b.split("\\|", -1)[0]);
  }

  /** Checks if a provided type can be treated as a Reference. */
  public static boolean isReferenceType(String type) {
    return "Reference".equals(type) || "CodeableReference".equals(type);
  }

  /** TS {@code { instance, pathParts }}. */
  public record InstanceAndPathParts(InstanceDefinition instance, List<PathPart> pathParts) {}

  /**
   * Use the raw value from a CaretValueRule to try to find an Instance to assign. This is useful in cases where the
   * Instance id is numeric or boolean. {@code originalErr} is the MismatchedTypeError from the first attempt.
   */
  public static InstanceAndPathParts validateInstanceFromRawValue(Object target, CaretValueRule rule,
      InstanceExporter instanceExporter, Fishable fisher, RuntimeException originalErr,
      List<String> inlineResourceTypes) {
    if (inlineResourceTypes == null) inlineResourceTypes = new ArrayList<>();
    InstanceDefinition instance = instanceExporter.fishForFHIR(rule.rawValue);
    if (instance == null) {
      logger.error(originalErr.getMessage(), rule.sourceInfo);
      logger.debug(jsStack(originalErr));
    } else {
      try {
        StructureDefinition targetSD = ownStructureDefinition(target, fisher);
        String path = rule.path.length() > 1 ? rule.path + "." + rule.caretPath : rule.caretPath;
        StructureDefinition.ValidateValueResult r =
            targetSD.validateValueAtPath(path, instance, fisher, inlineResourceTypes);
        return new InstanceAndPathParts(instance, r.pathParts());
      } catch (RuntimeException instanceErr) {
        if (instanceErr.getClass().getSimpleName().equals("MismatchedTypeError")) {
          logger.error(originalErr.getMessage(), rule.sourceInfo);
          logger.debug(jsStack(originalErr));
        } else {
          logger.error(instanceErr.getMessage(), rule.sourceInfo);
          logger.debug(jsStack(instanceErr));
        }
      }
    }
    return new InstanceAndPathParts(null, new ArrayList<>());
  }

  /**
   * Make a deep clone recursively, adding properties in the order expected for exported JSON: each "_x" key right
   * after "x", remaining underscore keys last. {@code keys} null = all of the input's keys.
   */
  public static Object orderedCloneDeep(Object input) {
    return orderedCloneDeep(input, null);
  }

  public static Object orderedCloneDeep(Object input, List<String> keys) {
    if (input instanceof JsonSerializable js && !(input instanceof Map<?, ?>)) {
      // a class instance with toJSON (e.g. ElementDefinitionType): lodash clones its own fields; JSON output only
      // sees toJSON(), so clone that
      input = js.toJSON();
    }
    // non-objects should be cloned normally
    // arrays should get a recursive call on their elements, but don't need reordering
    if (!isObjectLike(input)) {
      return Js.cloneDeep(input);
    } else if (input instanceof List<?> l) {
      List<Object> out = new ArrayList<>(l.size());
      for (Object element : l) out.add(orderedCloneDeep(element));
      return out;
    } else {
      List<String> ks = keys == null ? keysOf(input) : new ArrayList<>(keys);
      List<String> underscoreKeys = new ArrayList<>();
      for (Iterator<String> it = ks.iterator(); it.hasNext();) {
        String key = it.next();
        if (key.startsWith("_")) {
          underscoreKeys.add(key);
          it.remove();
        }
      }
      List<String> orderedKeys = new ArrayList<>();
      for (String key : ks) {
        orderedKeys.add(key);
        if (underscoreKeys.contains("_" + key)) {
          orderedKeys.add("_" + key);
          underscoreKeys.removeIf(k -> k.equals("_" + key));
        }
      }
      orderedKeys.addAll(underscoreKeys);
      JsObject result = new JsObject();
      for (String key : orderedKeys) {
        Object raw = getRaw(input, key);
        result.put(key, raw == Js.UNDEFINED ? Js.UNDEFINED : orderedCloneDeep(raw));
      }
      return result;
    }
  }

  /** All codes of a CodeSystem (depth-first, pre-order). */
  public static List<String> getAllConcepts(Object cs) {
    List<String> allConcepts = new ArrayList<>();
    Object concept = getProp(cs, "concept");
    if (concept == null) {
      return allConcepts;
    }
    java.util.ArrayDeque<Object> conceptList = new java.util.ArrayDeque<>((List<?>) concept);
    while (!conceptList.isEmpty()) {
      Object nextConcept = conceptList.removeFirst();
      Object code = getProp(nextConcept, "code");
      allConcepts.add(code == null ? null : Js.toStr(code));
      Object children = getProp(nextConcept, "concept");
      if (children instanceof List<?> cl) {
        for (int i = cl.size() - 1; i >= 0; i--) conceptList.addFirst(cl.get(i));
      }
    }
    return allConcepts;
  }

  /** The profiles declared via the imposeProfile extension, or null if there are none. */
  public static List<Object> findImposeProfiles(Object sd) {
    Object ext = getProp(sd, "extension");
    if (!(ext instanceof List<?> l)) return null;
    List<Object> imposeProfiles = new ArrayList<>();
    for (Object e : l) {
      if (e != null && IMPOSE_PROFILE_EXTENSION.equals(getProp(e, "url")) && getProp(e, "valueCanonical") != null) {
        imposeProfiles.add(getProp(e, "valueCanonical"));
      }
    }
    return imposeProfiles.isEmpty() ? null : imposeProfiles;
  }

  /** TS {@code { pathParts, assignedValue }}, an element of the contained-resources list. */
  public interface PathPartsAndValue {
    List<PathPart> pathParts();

    Object assignedValue();
  }

  public record ValidatedContainedRule(List<PathPart> pathParts, Object assignedValue) implements PathPartsAndValue {}

  /**
   * Checks if the entity used in Canonical() references a contained resource; returns the matching resource's id or
   * null.
   */
  public static Object getMatchingContainedReferenceId(String value,
      List<? extends PathPartsAndValue> containedResources) {
    for (PathPartsAndValue r : containedResources) {
      Object av = r.assignedValue();
      if (strictEquals(getProp(av, "url"), value) || strictEquals(getProp(av, "name"), value)
          || strictEquals(getProp(av, "id"), value)) {
        return getProp(av, "id");
      }
    }
    PathPartsAndValue byId = findByLastBase(containedResources, "id", value);
    PathPartsAndValue byName = findByLastBase(containedResources, "name", value);
    PathPartsAndValue byUrl = findByLastBase(containedResources, "url", value);
    if (byId != null || byName != null || byUrl != null) {
      List<PathPart> pathParts = byId != null ? byId.pathParts() : byName != null ? byName.pathParts()
          : byUrl.pathParts();
      List<PathPart> prefix = pathParts.subList(0, Math.max(0, pathParts.size() - 1));
      for (PathPartsAndValue r : containedResources) {
        List<PathPart> pp = r.pathParts();
        if (!pp.isEmpty() && "id".equals(pp.get(pp.size() - 1).base)
            && pp.subList(0, pp.size() - 1).equals(prefix)) {
          return r.assignedValue();
        }
      }
    }
    return null;
  }

  private static PathPartsAndValue findByLastBase(List<? extends PathPartsAndValue> list, String base,
      String value) {
    for (PathPartsAndValue r : list) {
      List<PathPart> pp = r.pathParts();
      if (!pp.isEmpty() && base.equals(pp.get(pp.size() - 1).base) && strictEquals(r.assignedValue(), value)) {
        return r;
      }
    }
    return null;
  }

  /** Logs an error for every choice element with more than one of its choices assigned. */
  public static void checkForMultipleChoice(FshEntity fshDef, Object fhirDef, StructureDefinition structDef) {
    checkChildrenForMultipleChoice(fshDef, fhirDef, structDef.elements.get(0));
  }

  private static void checkChildrenForMultipleChoice(FshEntity fshDef, Object instance, ElementDefinition element) {
    for (ElementDefinition child : element.children(true)) {
      // does this child represent a choice element, such as value[x]? if so, check for choices
      if (child.getId().endsWith("[x]")) {
        String lastPart = last(splitOnPathPeriods(child.getId()));
        String idStart = lastPart.substring(0, lastPart.length() - 3);
        List<String> availableChoices = new ArrayList<>();
        for (ElementDefinitionType edType : child.getType()) {
          availableChoices.add(idStart + upperFirst(edType.getCode()));
        }
        if (availableChoices.size() > 1) {
          int existing = 0;
          for (String choice : availableChoices) {
            if (getProp(instance, choice) != null || getProp(instance, "_" + choice) != null) existing++;
          }
          if (existing > 1) {
            logger.error(nameOf(fshDef) + " contains multiple choice value assignments for choice element "
                + child.getId() + ".", fshDef.sourceInfo);
          }
        }
      }
      // does the instance have an object value for this element? if so, recursively check that object.
      // since there may also be children of primitives, also check underscore properties
      String[] pathSplit = child.getPath().split("\\.", -1);
      String childPathEnd = pathSplit[pathSplit.length - 1];
      for (String k : List.of(childPathEnd, "_" + childPathEnd)) {
        Object v = getProp(instance, k);
        if (v != null && isJsObjectType(v)) {
          if (v instanceof List<?> l) {
            for (Object childProperty : l) {
              if (childProperty != null && isJsObjectType(childProperty)) {
                checkChildrenForMultipleChoice(fshDef, childProperty, child);
              }
            }
          } else {
            checkChildrenForMultipleChoice(fshDef, v, child);
          }
        }
      }
    }
  }
}
