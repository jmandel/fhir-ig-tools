package org.hl7.fhir.sushi.export;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.AbstractInstanceOfError;
import org.hl7.fhir.sushi.errors.InstanceOfNotDefinedError;
import org.hl7.fhir.sushi.errors.MismatchedTypeError;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinitionType;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.PathPart;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.FshCanonical;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.PathRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.common.js.Lodash;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FishingUtils;
import org.hl7.fhir.sushi.utils.MasterFisher;
import org.hl7.fhir.sushi.utils.Metadata;
import org.hl7.fhir.sushi.utils.PathUtils;

import static org.hl7.fhir.sushi.export.ExporterLog.str;
import static org.hl7.fhir.sushi.utils.FshLogger.logger;

// port of sushi/src/export/InstanceExporter.ts (SUSHI 3.21.1) -- owned by WP-7
/**
 * Exports FSH Instances. Also a Fishable for instances (TS {@code fishForFHIR(item)} takes no types): fishing exports
 * the FSH instance on demand, so other exporters (caret rules assigning instances) create one per use.
 *
 * <p>Instances are {@link InstanceDefinition}s (insertion-ordered JS objects): their key order is the order in which
 * this exporter and setPropertyOnInstance create keys, and lodash merge appends the keys the rules added.
 */
public class InstanceExporter implements Fishable {
  private static final Pattern LEADING_ZEROS_INDEX = Pattern.compile("\\[0+\\]");
  private static final Pattern ZERO_INDEX = Pattern.compile("\\[0\\]");
  private static final Pattern CONTAINED_PATH = Pattern.compile("^contained(\\[[^\\]]+\\])*(\\.url|\\.name|\\.id)?");
  private static final Pattern EXTENSION_PATH = Pattern.compile("(^|\\.)(extension|modifierExtension)\\[");

  /** TS sdCache: instanceOf url -> StructureDefinition (parsed once per exporter). */
  public final Map<String, StructureDefinition> sdCache = new LinkedHashMap<>();

  private final FSHTank tank;
  private final Package pkg;
  private final MasterFisher fisher;

  public InstanceExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.tank = tank;
    this.pkg = pkg;
    this.fisher = fisher;
  }

  /**
   * chalk 4 {@code chalk.rgb(179, 98, 0)(s)} (logger.warn's colour, truecolor level), only when chalk would colour
   * (FshLogger.colorEnabled). Like chalk, closes and re-opens the style around each line break.
   */
  private static String clr(String s) {
    if (!logger.colorEnabled() || s.isEmpty()) {
      return s;
    }
    String open = "\u001b[38;2;179;98;0m";
    String close = "\u001b[39m";
    return open + s.replace("\n", close + "\n" + open) + close;
  }

  /** lodash padEnd(s, n) with spaces (UTF-16 length). */
  private static String padEnd(String s, int n) {
    StringBuilder sb = new StringBuilder(s);
    while (sb.length() < n) sb.append(' ');
    return sb.toString();
  }

  /**
   * Checks if there exist Instances of Custom Resources, which are not supported by the IG Publisher and are
   * therefore not included in the IG.
   */
  private void warnOnInstanceOfCustomResource() {
    List<InstanceDefinition> instancesOfCustomResource = new ArrayList<>();
    for (InstanceDefinition instance : pkg.instances) {
      for (StructureDefinition r : pkg.resources) {
        if (Objects.equals(r.getType(), instance.getResourceType())) {
          instancesOfCustomResource.add(instance);
          break;
        }
      }
    }
    if (!instancesOfCustomResource.isEmpty()) {
      int maxLength = 61;
      List<String> message = new ArrayList<>();
      message.add(clr("\n╭─────────────────────────────────────────────────────────────────") + "" + clr("╮"));
      message.add(clr("│") + " Detected the following instance(s) of custom resources:         " + clr("│"));
      for (InstanceDefinition r : instancesOfCustomResource) {
        String id = r.getId();
        String printableName = id.length() > maxLength ? id.substring(0, maxLength - 3) + "..." : id;
        message.add(clr("│") + padEnd(" - " + printableName, 65) + clr("│"));
      }
      message.add(clr("│") + "                                                                 " + clr("│"));
      message.add(clr("│") + " Since non-conformant resources and their instances aren't       " + clr("│"));
      message.add(clr("│") + " supported by standard FHIR tooling, these instances will        " + clr("│"));
      message.add(clr("│") + " not be included in the generated IG resource.                   " + clr("│"));
      message.add(clr("╰─────────────────────────────────────────────────────────────────") + "" + clr("╯\n"));
      logger.warn(String.join("\n", message));
    }
  }

  /** A validated rule in the TS ruleMap: { pathParts, assignedValue, sourceInfo }. */
  private record RuleMapEntry(List<PathPart> pathParts, Object assignedValue, SourceInfo sourceInfo)
      implements Common.HasPathParts {}

  private record InlineResourcePath(String path, String instanceOf) {}

  private static boolean isDefinition(InstanceDefinition def) {
    return InstanceUsage.Definition.name().equals(def._instanceMeta.usage);
  }

  private static boolean isExample(InstanceDefinition def) {
    return InstanceUsage.Example.name().equals(def._instanceMeta.usage);
  }

  @SuppressWarnings("unchecked")
  private InstanceDefinition setAssignedValues(Instance fshInstanceDef, InstanceDefinition instanceDef,
      StructureDefinition instanceOfStructureDefinition) {
    Configuration.ConfigurationInstanceOptions options = tank.config.instanceOptions;
    boolean manualSliceOrdering = options != null && Boolean.TRUE.equals(options.manualSliceOrdering);

    // The fshInstanceDef.rules list may contain insert rules, which will be expanded to AssignmentRules
    Common.applyInsertRules(fshInstanceDef, tank);
    PathUtils.resolveSoftIndexing(fshInstanceDef.rules, manualSliceOrdering);
    List<Rule> rules = new ArrayList<>();
    for (Rule r : fshInstanceDef.rules) {
      rules.add(Js.cloneDeep(r));
    }
    // Normalize all rules to not use the optional [0] index
    for (Rule r : rules) {
      r.path = LEADING_ZEROS_INDEX.matcher(r.path).replaceAll("");
    }
    List<Rule> replaced = new ArrayList<>();
    for (Rule r : rules) {
      replaced.add(r instanceof PathRule ? r : Common.replaceReferences((AssignmentRule) r, tank, fisher));
    }
    rules = replaced;
    // Convert strings in AssignmentRules to instances
    List<Rule> kept = new ArrayList<>();
    for (Rule r : rules) {
      if (r instanceof AssignmentRule ar && ar.isInstance) {
        InstanceDefinition instance = fishForFHIR(str(ar.value));
        if (instance != null) {
          if (isExample(instance) && isDefinition(instanceDef)) {
            logger.warn("Contained instance \"" + str(ar.value)
                + "\" is an example and probably should not be included in a conformance resource.", ar.sourceInfo);
          }
          ar.value = instance;
          kept.add(r);
        } else {
          logger.error("Cannot find definition for Instance: " + str(ar.value) + ". Skipping rule.", ar.sourceInfo);
        }
      } else {
        kept.add(r);
      }
    }
    rules = kept;
    // Collect all paths that indicate the sub-paths of that path should be of a given resourceType
    // for example, if a.b = SomePatientInstance, then any subpath (a.b.x) must ensure that when validating
    // Patient is used for the type of a.b
    List<InlineResourcePath> inlineResourcePaths = new ArrayList<>();
    for (Rule rule : rules) {
      if (!(rule instanceof AssignmentRule r)) continue;
      if (r.isInstance && r.value instanceof InstanceDefinition inst) {
        // We only use the first element of the meta.profile array, if a need arises for a more
        // comprehensive approach, we can come back to this later
        Object firstProfile = null;
        if (inst.get("meta") instanceof Map<?, ?> meta && meta.get("profile") instanceof List<?> profiles
            && !profiles.isEmpty()) {
          firstProfile = profiles.get(0);
        }
        inlineResourcePaths.add(new InlineResourcePath(r.path,
            firstProfile != null && firstProfile != Js.UNDEFINED ? str(firstProfile) : inst.getResourceType()));
      }
      if (r.path.endsWith(".resourceType") && r.value instanceof String s) {
        // Only get the part of the path before resourceType, aka if path is a.b.resourceType
        // the relevant element is a.b, since it is the actual Resource element
        List<String> parts = Common.splitOnPathPeriods(r.path);
        inlineResourcePaths.add(new InlineResourcePath(String.join(".", parts.subList(0, parts.size() - 1)), s));
      }
    }

    // When assigning values, things happen in the order:
    // 1 - Validate values for rules that are on the instance
    // 2 - Determine all rules implied by the Structure Definition
    // 3 - Set values from rules implied by the Structure Definition
    // 4 - Set values from rules directly on the instance
    // This order is required due to the fact that validateValueAtPath changes instanceOfStructureDefinition
    // in certain cases that must happen before setting rules from the Structure Definition. In the future
    // we may want to refactor validateValueAtPath, but for now things should happen in this order
    Map<String, RuleMapEntry> ruleMap = new LinkedHashMap<>();
    // Keep track specifically of the rules on contained (path could be contained[index], contained.some-path, or
    // contained)
    List<Common.ValidatedContainedRule> containedRules = new ArrayList<>();
    for (Rule rule : rules) {
      List<String> inlineResourceTypes = new ArrayList<>();
      try {
        for (InlineResourcePath i : inlineResourcePaths) {
          if (rule.path.startsWith(i.path() + ".") && !rule.path.equals(i.path() + ".resourceType")) {
            // Generate an array of resourceTypes that matches the path, so if path is
            // a.b.c.d.e, and b is a Bundle and D is a Patient,
            // inlineResourceTypes = [undefined, "Bundle", undefined, "Patient", undefined]
            CodeSystemExporter.setSparse(inlineResourceTypes, Common.splitOnPathPeriods(i.path()).size() - 1,
                i.instanceOf());
          }
        }
        // if a rule has choice elements in the path that are constrained to a single type,
        // change the path to reflect the available type for each choice element.
        // if the path changes, warn the user that it is a better idea to use the more specific element name
        String updatedPath = instanceOfStructureDefinition.updatePathWithChoices(rule.path);
        if (!rule.path.equals(updatedPath)) {
          logger.warn("When assigning values on instances, use the choice element's type. Rule path changed from '"
              + rule.path + "' to '" + updatedPath + "'.", rule.sourceInfo);
          rule.path = updatedPath;
        }
        doRuleValidation(rule, rule instanceof AssignmentRule ar ? ar.value : null, instanceOfStructureDefinition,
            inlineResourceTypes, manualSliceOrdering, ruleMap, containedRules);
      } catch (RuntimeException originalErr) {
        // if an Instance has an id that looks like a number, bigint, or boolean,
        // we may have tried to validate with that value instead of an Instance.
        // try to fish up an Instance with the rule's raw value.
        // if we find one, try validating with that instead.
        if (rule instanceof AssignmentRule ar && originalErr instanceof MismatchedTypeError
            && CodeSystemExporter.isNumberBigintOrBoolean(ar.value)) {
          InstanceDefinition instanceToAssign = fishForFHIR(ar.rawValue);
          if (instanceToAssign == null) {
            ExporterLog.errorWithStack(originalErr, rule.sourceInfo);
          } else {
            try {
              if (isExample(instanceToAssign) && isDefinition(instanceDef)) {
                logger.warn("Contained instance \"" + ar.rawValue
                    + "\" is an example and probably should not be included in a conformance resource.",
                    rule.sourceInfo);
              }
              doRuleValidation(rule, instanceToAssign, instanceOfStructureDefinition, inlineResourceTypes,
                  manualSliceOrdering, ruleMap, containedRules);
            } catch (RuntimeException instanceErr) {
              if (instanceErr instanceof MismatchedTypeError) {
                ExporterLog.errorWithStack(originalErr, rule.sourceInfo);
              } else {
                ExporterLog.errorWithStack(instanceErr, rule.sourceInfo);
              }
            }
          }
        } else {
          ExporterLog.errorWithStack(originalErr, rule.sourceInfo);
        }
      }
    }

    List<String> paths = new ArrayList<>();
    paths.add("");
    for (Rule rule : rules) {
      RuleMapEntry entry = ruleMap.get(rule.path);
      paths.add(ZERO_INDEX.matcher(PathUtils.assembleFSHPath(entry != null ? entry.pathParts() : List.of()))
          .replaceAll(""));
    }
    // To correctly assign properties, we need to:
    // 1 - Create useful slices for rules so that properties are assigned in the correct places in arrays
    // 2 - Assign implied properties on the original instance
    // 3 - Assign rule properties on a copy of the result of 2, so that rule assignment can build on implied
    //     assignment
    // 4 - Merge the result of 3 with the result of 2, so that any implied properties which may have been
    //     overwritten in step 3 are maintained...don't worry I'm confused too
    Map<String, Integer> knownSlices;
    if (manualSliceOrdering) {
      knownSlices = Common.createUsefulSlices(instanceDef, instanceOfStructureDefinition, ruleMap, fisher);
    } else {
      // Don't create slices, just determine what will be created later
      knownSlices = Common.determineKnownSlices(instanceOfStructureDefinition, ruleMap, fisher);
    }

    List<String> assignedResourcePaths = new ArrayList<>();
    for (InlineResourcePath p : inlineResourcePaths) assignedResourcePaths.add(p.path());
    // for core defs, only extension slices can appear
    if (Objects.equals(instanceOfStructureDefinition.getUrl(),
        "http://hl7.org/fhir/StructureDefinition/" + str(instanceOfStructureDefinition.getType()))) {
      List<String> secretPaths = new ArrayList<>();
      for (String path : paths) {
        if (EXTENSION_PATH.matcher(path).find()) secretPaths.add(path);
      }
      Common.setImpliedPropertiesOnInstance(instanceDef, instanceOfStructureDefinition, secretPaths,
          assignedResourcePaths, fisher, knownSlices, manualSliceOrdering);
    } else {
      Common.setImpliedPropertiesOnInstance(instanceDef, instanceOfStructureDefinition, paths,
          assignedResourcePaths, fisher, knownSlices, manualSliceOrdering);
    }
    InstanceDefinition ruleInstance = Js.cloneDeep(instanceDef);
    for (RuleMapEntry rule : ruleMap.values()) {
      Common.setPropertyOnInstance(ruleInstance, rule.pathParts(), rule.assignedValue(), fisher);
      // was an instance of an extension used correctly with respect to modifiers?
      PathPart lastPart = rule.pathParts().get(rule.pathParts().size() - 1);
      Object assignedValue = rule.assignedValue();
      // (Java null is TS undefined here: validateValueAtPath leaves assignedValue undefined, never null)
      if (Common.isExtension(lastPart.base) && (assignedValue instanceof Map || assignedValue instanceof List)) {
        Object url = assignedValue instanceof Map<?, ?> m ? m.get("url") : null;
        Object extension = fisher.fishForFHIR(url instanceof String s ? s : null, FishableType.Extension);
        if (extension != null) {
          String pathBase = lastPart.base;
          boolean isModifier = Common.isModifierExtension(extension);
          if (isModifier && pathBase.equals("extension")) {
            logger.error("Instance of modifier extension " + nameOrId(extension)
                + " assigned to extension path. Modifier extensions should only be assigned to modifierExtension "
                + "paths.", rule.sourceInfo());
          } else if (!isModifier && pathBase.equals("modifierExtension")) {
            logger.error("Instance of non-modifier extension " + nameOrId(extension)
                + " assigned to modifierExtension path. Non-modifier extensions should only be assigned to "
                + "extension paths.", rule.sourceInfo());
          }
        }
      }
      // were extensions used correctly along the path?
      List<PathPart> pathParts = rule.pathParts();
      for (int index = 0; index < pathParts.size(); index++) {
        PathPart pathPart = pathParts.get(index);
        if (Common.isExtension(pathPart.base)) {
          String sliceName = Common.getSliceName(pathPart);
          if (Js.truthy(sliceName)) {
            Object extension = resolveSliceExtension(instanceOfStructureDefinition, pathParts, index);
            if (extension != null) {
              boolean isModifier = Common.isModifierExtension(extension);
              if (isModifier && pathPart.base.equals("extension")) {
                logger.error("Modifier extension " + nameOrId(extension)
                    + " used on extension element. Modifier extensions should only be used with modifierExtension "
                    + "elements.", rule.sourceInfo());
              } else if (!isModifier && pathPart.base.equals("modifierExtension")) {
                logger.error("Non-modifier extension " + nameOrId(extension)
                    + " used on modifierExtension element. Non-modifier extensions should only be used with "
                    + "extension elements.", rule.sourceInfo());
              }
            }
          }
        }
      }
    }
    return Lodash.merge(instanceDef, ruleInstance);
  }

  /** TS {@code typeof v === 'object'}: objects, arrays and null. */
  private static boolean isTypeofObject(Object v) {
    return v == null || v instanceof Map || v instanceof List;
  }

  /** TS {@code extension.name ?? extension.id}. */
  private static String nameOrId(Object extension) {
    Map<?, ?> m = (Map<?, ?>) extension;
    Object name = m.get("name");
    return str(name != null && name != Js.UNDEFINED ? name : m.get("id"));
  }

  /** The TS doRuleValidation closure of setAssignedValues. */
  private void doRuleValidation(Rule rule, Object value, StructureDefinition instanceOfStructureDefinition,
      List<String> inlineResourceTypes, boolean manualSliceOrdering, Map<String, RuleMapEntry> ruleMap,
      List<Common.ValidatedContainedRule> containedRules) {
    // Before validating the rule, check if the Canonical keyword was used to reference a contained value set
    if (value instanceof FshCanonical canonical) {
      Object matchingContainedReferenceId = Common.getMatchingContainedReferenceId(canonical.entityName,
          containedRules);
      if (Js.truthy(matchingContainedReferenceId)) {
        value = "#" + str(matchingContainedReferenceId);
      }
    }
    StructureDefinition.ValidateValueResult validatedRule = instanceOfStructureDefinition.validateValueAtPath(
        rule.path, value, fisher, inlineResourceTypes, rule.sourceInfo, manualSliceOrdering);
    // Record each valid rule in a map
    // Choice elements on an instance must use a specific type, so if the path still has an unchosen choice element,
    // the rule can't be used. See http://hl7.org/fhir/R4/formats.html#choice
    String finalRulePath = rule.path;
    if (validatedRule.childPath() != null) {
      finalRulePath = rule.path + "." + validatedRule.childPath();
    }
    if (validatedRule.pathParts().stream().anyMatch(part -> part.base.endsWith("[x]"))) {
      logger.error("Unable to assign value at " + rule.path + ": choice elements on an instance must use a specific "
          + "type", rule.sourceInfo);
    } else if (!(isNullish(validatedRule.assignedValue()) && ruleMap.containsKey(finalRulePath))) {
      // If a validateRule doesn't have an assignedValue, it was a PathRule that has
      // no implied required values, so we don't need to set anything for this rule
      ruleMap.put(finalRulePath, new RuleMapEntry(validatedRule.pathParts(), validatedRule.assignedValue(),
          rule.sourceInfo));
      // Check if the rule we just validated was at a valid contained path to keep track for resolving Canonical
      // references. Only check if the rule was a directly contained resources (aka 'contained' or
      // 'contained.name/url/id', optionally with slice names or indices)
      if (CONTAINED_PATH.matcher(rule.path).matches()) {
        containedRules.add(new Common.ValidatedContainedRule(validatedRule.pathParts(), validatedRule.assignedValue()));
      }
    }
  }

  private static boolean isNullish(Object v) {
    return v == null || v == Js.UNDEFINED;
  }

  /**
   * Resolves the Extension definition bound to a sliced (modifier)extension path part using the matched slice's
   * type.profile. The bracketed token may name a slice declared on the profile or identify an extension by its id,
   * name, or url; findElementByPath resolves either, adding the slice to the (copied) StructureDefinition when the
   * profile does not declare it.
   *
   * @return the resolved Extension definition, or null if it cannot be authoritatively resolved
   */
  private Object resolveSliceExtension(StructureDefinition instanceOfStructureDefinition, List<PathPart> pathParts,
      int index) {
    // Drop each part's trailing numeric occurrence index, since findElementByPath treats brackets
    // as slice identifiers rather than array positions.
    List<PathPart> parts = new ArrayList<>();
    for (PathPart pp : pathParts.subList(0, index + 1)) {
      if (Common.getArrayIndex(pp) == null) {
        parts.add(pp);
      } else {
        PathPart copy = pp.copy();
        copy.brackets = new ArrayList<>(pp.brackets.subList(0, pp.brackets.size() - 1));
        parts.add(copy);
      }
    }
    String path = PathUtils.assembleFSHPath(parts);
    ElementDefinition element = instanceOfStructureDefinition.findElementByPath(path, fisher);
    String profileUrl = null;
    List<ElementDefinitionType> types = element == null ? null : element.getType();
    if (types != null && !types.isEmpty()) {
      ElementDefinitionType t = types.get(0);
      if (t != null && t.getProfile() != null && !t.getProfile().isEmpty()) {
        profileUrl = t.getProfile().get(0);
      }
    }
    if (!Js.truthy(profileUrl)) {
      return null;
    }
    return FishingUtils.fishForFHIRBestVersion(fisher, profileUrl, null, FishableType.Extension);
  }

  /** JS {@code obj[key]} on a value-model value; null for non-objects and absent keys. */
  private static Object prop(Object obj, String key) {
    if (obj instanceof Map<?, ?> m) {
      Object v = m.get(key);
      return v == Js.UNDEFINED ? null : v;
    }
    return null;
  }

  /** lodash isEmpty. */
  private static boolean isEmpty(Object v) {
    if (v instanceof Map<?, ?> m) return m.isEmpty();
    if (v instanceof List<?> l) return l.isEmpty();
    if (v instanceof String s) return s.isEmpty();
    return true; // null, undefined, numbers, booleans
  }

  /** lodash at(object, indices) on the value (array of a primitive's values, or undefined). */
  private static List<Object> at(Object object, List<Integer> indices) {
    List<Object> result = new ArrayList<>();
    for (int i : indices) {
      if (object instanceof List<?> l) {
        result.add(i < l.size() ? l.get(i) : null);
      } else if (object instanceof String s) {
        result.add(i < s.length() ? String.valueOf(s.charAt(i)) : null);
      } else {
        result.add(null);
      }
    }
    return result;
  }

  /** lodash upperFirst. */
  private static String upperFirst(String s) {
    if (s == null || s.isEmpty()) return "";
    return s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
  }

  private static String sliceNameOf(Object arrayEl) {
    Object v = prop(arrayEl, "_sliceName");
    return v == null ? null : str(v);
  }

  /**
   * Check that all required elements are present on an InstanceDefinition or a sub-part of an InstanceDefinition
   * for all children of an element. An element is required if it has minimum cardinality greater than 1.
   *
   * @param instance the InstanceDefinition or subsection of an InstanceDefinition we are validating
   * @param element the element we are trying to validate all children of
   * @param fshDefinition the FSH definition that we built the original InstanceDefinition from
   */
  private void validateRequiredChildElements(Object instance, ElementDefinition element, Instance fshDefinition,
      Object parentPrimitive) {
    // Get only direct children of the element
    List<ElementDefinition> children = element.children(true);
    childLoop:
    for (ElementDefinition c : children) {
      ElementDefinition child = c;
      // Get the last part of the path, A.B.C => C
      String[] childPathParts = child.getPath().split(Pattern.quote("."), -1);
      String childPathEnd = childPathParts[childPathParts.length - 1];
      // Note that in most cases the _ prefixed element will not exist. But if it does exist, we validate
      // using the _ prefixed element, since it will contain any children of the primitive
      // if the primitive value exists, store it, since child element validation may need it
      Object instanceChild;
      Object instanceChildPrimitive = null;
      boolean isChildTypePrimitive;
      if (prop(instance, "_" + childPathEnd) != null) {
        instanceChild = prop(instance, "_" + childPathEnd);
        instanceChildPrimitive = prop(instance, childPathEnd);
        isChildTypePrimitive = true;
      } else {
        instanceChild = prop(instance, childPathEnd);
        if (child.isPrimitive(fisher)) {
          instanceChildPrimitive = prop(instance, childPathEnd);
          isChildTypePrimitive = true;
        } else {
          isChildTypePrimitive = false;
        }
      }
      // If the element is a choice, we will fail to find it, we need to use the choice name
      if (instanceChild == null && childPathEnd.endsWith("[x]")) {
        List<ElementDefinition> possibleChoiceSlices = new ArrayList<>(children);
        for (ElementDefinition ce : element.findConnectedElements()) {
          possibleChoiceSlices.addAll(ce.children(true));
        }
        List<ElementDefinition> choiceSlices = new ArrayList<>();
        for (ElementDefinition cs : possibleChoiceSlices) {
          if (Objects.equals(cs.getPath(), child.getPath())
              && (Js.truthy(cs.getSliceName()) || (cs.getType() != null && cs.getType().size() == 1))) {
            choiceSlices.add(cs);
          }
        }
        for (ElementDefinition choiceSlice : choiceSlices) {
          // for each of these elements, we either have a sliceName, or we build a sliceName from the type
          String sliceName = choiceSlice.getSliceName() != null ? choiceSlice.getSliceName()
              : childPathEnd.replace("[x]", upperFirst(choiceSlice.getType().get(0).getCode()));
          // as above, we use the _ prefixed element if it exists
          Object underscored = prop(instance, "_" + sliceName);
          instanceChild = underscored != null ? underscored : prop(instance, sliceName);
          List<String> splitChoicePath = Common.splitOnPathPeriods(choiceSlice.getId());
          // If the element we're assigning to has a sliceName, use it to ensure that we're validating
          // against the correct choice slice
          String instanceSliceName = sliceNameOf(instance);
          boolean sliceMatches = true;
          if (Js.truthy(instanceSliceName)) {
            String[] idParts = splitChoicePath.get(splitChoicePath.size() - 2).split(":", -1);
            sliceMatches = idParts.length > 1 && idParts[1].equals(instanceSliceName);
          }
          if (instanceChild != null && sliceMatches) {
            // Once we find the the choiceSlice that matches, use it as the child
            child = choiceSlice;
            break;
          }
        }
        // If we don't have instanceChild yet, it may be due to a rule that refers to a named slice using a numeric
        // index somewhere in the path. AssignmentRules on an Instance cause elements to be unfolded on the
        // InstanceOf StructureDefinition, because that allows the AssignmentRule's value to be validated.
        // But, if the rule on the Instance didn't have a slice name, the element without a slice name is the one
        // that gets unfolded. So, if we're currently on a part of the Instance that does have a slice name, try to
        // find the ElementDefinition that has the same path.
        // All of this confusion arises because the author used a numeric slice index instead of a slice name,
        // so warn the user that it is preferable to use the slice name whenever possible.
        if (instanceChild == null) {
          ElementDefinition connectedSlice = element.findConnectedSliceElement();
          List<ElementDefinition> namelessChoiceSlices = new ArrayList<>();
          if (connectedSlice != null) {
            for (ElementDefinition cs : connectedSlice.children(true)) {
              if (Objects.equals(cs.getPath(), child.getPath()) && Js.truthy(cs.getSliceName())) namelessChoiceSlices.add(cs);
            }
          }
          for (ElementDefinition choiceSlice : namelessChoiceSlices) {
            instanceChild = prop(instance, choiceSlice.getSliceName());
            if (instanceChild != null) {
              // Once we find the the choiceSlice that matches, use it as the child, but warn the user about their
              // rule-writing
              logger.warn("Element " + child.getId() + " has its cardinality satisfied by a rule that does not include "
                  + "the slice name. Use slice names in rule paths when possible.");
              child = choiceSlice;
              break;
            }
          }
        }
        // If we still haven't found it, it's possible that a type slice just wasn't created. In that case, there
        // would be a type in the choice element's type array that would be a match if it were type-sliced.
        if (instanceChild == null) {
          for (ElementDefinitionType type : child.getType()) {
            String name = childPathEnd.endsWith("[x]")
                ? childPathEnd.substring(0, childPathEnd.length() - 3) + upperFirst(type.getCode()) : childPathEnd;
            Object underscored = prop(instance, "_" + name);
            instanceChild = underscored != null ? underscored : prop(instance, name);
            if (instanceChild != null) {
              break;
            }
          }
        }
      }
      // Recursively validate children of the current element
      if (instanceChild instanceof List<?> arrayChild) {
        // If the child is a slice, and there are no array elements with matching slice names
        // but there are array elements that could match (since they use numerical indices)
        // we can go no further in validation, since we can't know which slice is represented
        if (Js.truthy(child.getSliceName())) {
          final String childSliceName = child.getSliceName();
          boolean anyNamed = false;
          boolean anyUnnamed = false;
          for (Object arrayEl : arrayChild) {
            if (Objects.equals(prop(arrayEl, "_sliceName"), childSliceName)) anyNamed = true;
            if (!Js.truthy(prop(arrayEl, "_sliceName"))) anyUnnamed = true;
          }
          if (!anyNamed && anyUnnamed) {
            continue childLoop;
          }
        }
        // Filter so that if the child is a slice, we only count relevant slices
        // A slice is relevant if it is the child slice or is a reslice of child.
        // Typically, a sliceName that starts with child.sliceName + '/' is a reslice.
        List<Integer> matchingIndices = new ArrayList<>();
        List<Object> filtered = new ArrayList<>();
        for (int idx = 0; idx < arrayChild.size(); idx++) {
          Object arrayEl = arrayChild.get(idx);
          String elSliceName = sliceNameOf(arrayEl);
          if (!Js.truthy(child.getSliceName()) || Objects.equals(prop(arrayEl, "_sliceName"), child.getSliceName())
              || (elSliceName != null && elSliceName.startsWith(child.getSliceName() + "/"))) {
            matchingIndices.add(idx);
            filtered.add(arrayEl);
          }
        }
        instanceChild = filtered;
        if (isChildTypePrimitive) {
          instanceChildPrimitive = at(instanceChildPrimitive, matchingIndices);
        }
        for (int idx = 0; idx < filtered.size(); idx++) {
          Object arrayEl = filtered.get(idx);
          if (arrayEl != null && !isEmpty(arrayEl)) {
            validateRequiredChildElements(arrayEl, child, fshDefinition,
                isChildTypePrimitive && instanceChildPrimitive instanceof List<?> l ? l.get(idx) : null);
          }
        }
      } else if (instanceChild != null) {
        validateRequiredChildElements(instanceChild, child, fshDefinition, instanceChildPrimitive);
      }
      // Log an error if:
      // 1 - The child element is 1.., but not on the instance
      // 2 - The child element is n..m, but it has k < n elements
      // there's a special exception for the "value" child of a primitive,
      // since the actual value may be present on the parent primitive element.
      // if the parent primitive is an object, the value will be in the "assignedValue" attribute.
      // otherwise, the value is the parent primitive itself.
      double min = child.getMin() == null ? Double.NaN : child.getMin();
      Object primitiveValue = isTypeofObject(parentPrimitive) ? prop(parentPrimitive, "assignedValue")
          : parentPrimitive;
      if ((min > 0 && instanceChild == null && !(child.getId().endsWith(".value") && primitiveValue != null))
          || (instanceChild instanceof List<?> l && l.size() < min)) {
        // Can't point to any specific rule, so give sourceInfo of entire instance
        logger.error("Element " + child.getId() + " has minimum cardinality " + str(child.getMin()) + " but occurs "
            + (instanceChild instanceof List<?> l2 ? l2.size() : 0) + " time(s).", fshDefinition.sourceInfo);
      }
    }
  }

  /** Check that all required elements are present on an instance. */
  private void validateRequiredElements(InstanceDefinition instanceDef, List<ElementDefinition> elements,
      Instance fshDefinition) {
    validateRequiredChildElements(instanceDef, elements.get(0), fshDefinition, null);
  }

  /** TS {@code el.type?.[0].code}. */
  private static String firstTypeCode(ElementDefinition el) {
    List<ElementDefinitionType> types = el.getType();
    if (types == null || types.isEmpty() || types.get(0) == null) return null;
    return types.get(0).getCode();
  }

  /** TS {@code el.max === '1' && (el.base?.max == null || el.base.max === '1')}. */
  private static boolean isSingleton(ElementDefinition el) {
    Map<String, Object> base = el.getBase();
    Object baseMax = base == null ? null : base.get("max");
    return "1".equals(el.getMax()) && (baseMax == null || baseMax == Js.UNDEFINED || "1".equals(baseMax));
  }

  private boolean shouldSetMetaProfile(InstanceDefinition instanceDef,
      StructureDefinition instanceOfStructureDefinition) {
    Configuration.ConfigurationInstanceOptions options = tank.config.instanceOptions;
    String configSetMeta = options == null ? null : options.setMetaProfile;
    boolean isInline = InstanceUsage.Inline.name().equals(instanceDef._instanceMeta.usage);
    if ("never".equals(configSetMeta) || ("inline-only".equals(configSetMeta) && !isInline)
        || ("standalone-only".equals(configSetMeta) && isInline)) {
      return false;
    }
    // Config allows it, so set the meta.profile as long as the instance is
    // a profile and the meta element exists with the right type and card
    String metaPath = instanceOfStructureDefinition.getPathType() + ".meta";
    return "constraint".equals(instanceOfStructureDefinition.getDerivation())
        && instanceOfStructureDefinition.elements.stream().anyMatch(el -> Objects.equals(el.getPath(), metaPath)
            && "Meta".equals(firstTypeCode(el)) && isSingleton(el));
  }

  private boolean shouldSetId(InstanceDefinition instanceDef, StructureDefinition instanceOfStructureDefinition) {
    Configuration.ConfigurationInstanceOptions options = tank.config.instanceOptions;
    if (options != null && "standalone-only".equals(options.setId)
        && InstanceUsage.Inline.name().equals(instanceDef._instanceMeta.usage)) {
      return false;
    }
    // Config allows it, so set the id as long as the id element exists with the
    // right type and card (note: FHIR resource ids are actually 'string' type)
    String idPath = instanceOfStructureDefinition.getPathType() + ".id";
    return instanceOfStructureDefinition.elements.stream().anyMatch(el -> Objects.equals(el.getPath(), idPath)
        && ("string".equals(firstTypeCode(el)) || "id".equals(firstTypeCode(el))) && isSingleton(el));
  }

  /**
   * This function analyzes an exported instance looking at all sliced paths and attempting to detect cases where an
   * array item matches a required (implied) slice but was not assigned using a slice name. We want to detect and
   * warn on this because it very often results in undesirable outputs like repeated and/or incomplete array items.
   * Before manualSliceOrdering, most of these cases just overwrote the implied slice instead of being prepended
   * before the slice.
   */
  @SuppressWarnings("unchecked")
  private void checkForNamelessSlices(Instance fshDef, InstanceDefinition instanceDef,
      StructureDefinition instanceOfStructureDefinition) {
    List<ElementDefinition> nonChoiceSlicedElements = new ArrayList<>();
    for (ElementDefinition e : instanceOfStructureDefinition.elements) {
      if (Js.truthy(e.getSlicing()) && !e.getPath().endsWith("[x]")) nonChoiceSlicedElements.add(e);
    }
    for (ElementDefinition slicingEl : nonChoiceSlicedElements) {
      // Use the sliced element's path to get all potential element values from the instance
      List<Object> values = PathUtils.collectValuesAtElementIdOrPath(slicingEl.getId(), instanceDef).values();
      // Find the values that do not have named slices
      List<Object> unnamedValues = new ArrayList<>();
      for (Object v : values) {
        if (v != null && v != Js.UNDEFINED && prop(v, "_sliceName") == null) unnamedValues.add(v);
      }
      if (unnamedValues.isEmpty()) continue;
      // Find the values that do have named slices (for comparison)
      List<Object[]> namedValues = new ArrayList<>();
      for (Object v : values) {
        if (prop(v, "_sliceName") != null) {
          Object cleanValue = Js.cloneDeep(v);
          if (cleanValue instanceof Map<?, ?> m) ((Map<String, Object>) m).remove("_sliceName");
          namedValues.add(new Object[] {prop(v, "_sliceName"), cleanValue});
        }
      }
      if (namedValues.isEmpty()) continue;
      for (Object value : unnamedValues) {
        // It's a match if the unnamed value is a compatible superset of the named value (i.e., slice)
        List<Object[]> matchedNamedValues = new ArrayList<>();
        for (Object[] nv : namedValues) {
          if (Lodash.isMatch(value, nv[1])) matchedNamedValues.add(nv);
        }
        if (matchedNamedValues.isEmpty()) continue;
        String valueString = Json.stringify(value);
        if (valueString.length() > 300) {
          valueString = valueString.substring(0, 285) + "... (truncated)";
        }
        List<String> matchedNames = new ArrayList<>();
        for (Object[] nv : matchedNamedValues) matchedNames.add(str(nv[0]));
        boolean allEqual = matchedNamedValues.stream().allMatch(nm -> Js.isEqual(value, nm[1]));
        if (allEqual) {
          logger.warn(instanceDef._instanceMeta.name + " has an array item that is exactly the same as a required "
              + "slice, but does not use the slice name in its path. This may result in unexpected items in your "
              + "array. Usually you can remove this item from your FSH, since FSH instances inherit required "
              + "assigned values from their profile. Alternately, you can modify your FSH to use the slice name in "
              + "the item's path.  See: https://hl7.org/fhir/uv/shorthand/reference.html#sliced-array-paths\n"
              + "  Path:  " + slicingEl.getPath() + "\n"
              + "  Slice: " + String.join(" or ", matchedNames) + "\n"
              + "  Value: " + valueString, fshDef.sourceInfo);
        } else {
          logger.warn(instanceDef._instanceMeta.name + " has an array item that matches a required slice, but does "
              + "not use the slice name in its path. This may result in unexpected items in your array. If the item "
              + "is intended to be in the slice, modify your FSH to use the slice name in the item's path. See: "
              + "https://hl7.org/fhir/uv/shorthand/reference.html#sliced-array-paths\n"
              + "  Path:  " + slicingEl.getPath() + "\n"
              + "  Slice: " + String.join(" or ", matchedNames) + "\n"
              + "  Value: " + valueString, fshDef.sourceInfo);
        }
      }
    }
  }

  /** TS fishForFHIR(item): the types argument is ignored (always Instance, then any FHIR resource). */
  @Override public InstanceDefinition fishForFHIR(String item, FishableType... types) {
    InstanceDefinition result = (InstanceDefinition) pkg.fish(item, FishableType.Instance);
    if (result == null) {
      // If we find a FSH definition, then we can export and fish for it again
      Object fshDefinition = tank.fish(item, FishableType.Instance);
      if (fshDefinition instanceof Instance instance) {
        exportInstance(instance);
        result = (InstanceDefinition) pkg.fish(item, FishableType.Instance);
      } else {
        // If we don't find any Instances with the name, fish for other resources
        Object fishedFHIR = fisher.fishForFHIR(item);
        if (fishedFHIR instanceof Map<?, ?> json && Js.truthy(json.get("resourceType"))) {
          result = InstanceDefinition.fromJSON((Map<String, ?>) json);
        }
      }
    }
    return result;
  }

  @Override public Metadata fishForMetadata(String item, FishableType... types) {
    // If it's in the tank, it can get the metadata from there (no need to export like in fishForFHIR)
    return fisher.fishForMetadata(item, FishableType.Instance);
  }

  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
    // If it's in the tank, it can get the metadata from there (no need to export like in fishForFHIR)
    return fisher.fishForMetadatas(item, FishableType.Instance);
  }

  public void applyInsertRules() {
    for (Instance instance : tank.getAllInstances()) {
      Common.applyInsertRules(instance, tank);
    }
  }

  /** Returns null (TS undefined) when an instance with the same name was already exported. */
  @SuppressWarnings("unchecked")
  public InstanceDefinition exportInstance(Instance fshDefinition) {
    for (InstanceDefinition i : pkg.instances) {
      if (Objects.equals(i._instanceMeta.name, fshDefinition.name)) {
        return null;
      }
    }

    boolean isResource = true;
    Map<String, Object> json = (Map<String, Object>) fisher.fishForFHIR(fshDefinition.instanceOf,
        FishableType.Resource, FishableType.Profile, FishableType.Extension, FishableType.Type,
        FishableType.Logical);

    if (json == null) {
      throw new InstanceOfNotDefinedError(fshDefinition.name, fshDefinition.instanceOf, fshDefinition.sourceInfo);
    }

    Object kind = json.get("kind");
    if (!"resource".equals(kind) && !"logical".equals(kind)) {
      // If the instance is not a resource, it should be inline, since it cannot exist as a standalone instance
      isResource = false;
      if (fshDefinition.usage != InstanceUsage.Inline) {
        logger.warn("Instance " + fshDefinition.name + " is not an instance of a resource or logical model, so it "
            + "should only be used inline on other instances, and it will not be exported to a standalone file. "
            + "Specify \"Usage: #inline\" to remove this warning.", fshDefinition.sourceInfo);
        fshDefinition.usage = InstanceUsage.Inline;
      }
    }

    // an instance can't be created if the specialization it is created from is abstract.
    // see also the FHIR documentation for StructureDefinition.abstract
    Map<String, Object> ancestor = json;
    while (ancestor != null && !"specialization".equals(ancestor.get("derivation"))) {
      Object baseDefinition = ancestor.get("baseDefinition");
      ancestor = (Map<String, Object>) fisher.fishForFHIR(baseDefinition instanceof String s ? s : null);
    }
    if (ancestor != null && Boolean.TRUE.equals(ancestor.get("abstract"))) {
      throw new AbstractInstanceOfError(fshDefinition.name, str(ancestor.get("name")), fshDefinition.sourceInfo);
    }

    StructureDefinition instanceOfStructureDefinition;
    Object jsonUrl = json.get("url");
    if (jsonUrl instanceof String u && sdCache.containsKey(u)) {
      instanceOfStructureDefinition = sdCache.get(u);
    } else {
      instanceOfStructureDefinition = StructureDefinition.fromJSON(json);
      sdCache.put(instanceOfStructureDefinition.getUrl(), instanceOfStructureDefinition);
    }

    InstanceDefinition instanceDef = new InstanceDefinition();
    instanceDef._instanceMeta.name = fshDefinition.name; // This is name of the instance in the FSH
    if ("".equals(fshDefinition.title)) {
      logger.warn("Instance " + fshDefinition.name + " has a title field that should not be empty.");
    }
    if ("".equals(fshDefinition.description)) {
      logger.warn("Instance " + fshDefinition.name + " has a description field that should not be empty.");
    }
    if (Js.truthy(fshDefinition.title)) {
      instanceDef._instanceMeta.title = fshDefinition.title;
    }
    if (Js.truthy(fshDefinition.description)) {
      instanceDef._instanceMeta.description = fshDefinition.description;
    }
    String pathType = instanceOfStructureDefinition.getPathType();
    if (fshDefinition.usage != null) {
      instanceDef._instanceMeta.usage = fshDefinition.usage.name();
      if (fshDefinition.usage == InstanceUsage.Definition) {
        if (hasElementWithId(instanceOfStructureDefinition, pathType + ".url")) {
          instanceDef.put("url", tank.config.canonical + "/" + pathType + "/" + fshDefinition.getId());
        }
        if (Js.truthy(fshDefinition.title) && hasElementWithId(instanceOfStructureDefinition, pathType + ".title")) {
          instanceDef.put("title", fshDefinition.title);
        }
        if (Js.truthy(fshDefinition.description)
            && hasElementWithId(instanceOfStructureDefinition, pathType + ".description")) {
          instanceDef.put("description", fshDefinition.description);
        }
      }
    }
    if (isResource) {
      // ResourceType is determined by the StructureDefinition of the type
      CodeSystemExporter.putOrUndefined(instanceDef, "resourceType", instanceOfStructureDefinition.getType());
      if (shouldSetId(instanceDef, instanceOfStructureDefinition)) {
        CodeSystemExporter.putOrUndefined(instanceDef, "id", fshDefinition.getId());
      }
    }
    instanceDef._instanceMeta.sdType = instanceOfStructureDefinition.getType();
    instanceDef._instanceMeta.sdKind = instanceOfStructureDefinition.getKind();

    // Set Assigned values based on the FSH rules and the Structure Definition
    instanceDef = setAssignedValues(fshDefinition, instanceDef, instanceOfStructureDefinition);
    // should we add the instanceOf to meta.profile?
    // if the exact url is not in there, and a versioned url is also not in there, add it to the front.
    // otherwise, add it at the front.
    if (shouldSetMetaProfile(instanceDef, instanceOfStructureDefinition)) {
      setMetaProfile(fshDefinition, instanceDef, instanceOfStructureDefinition.getUrl());
    }
    // regardless of what we did with meta.profile, we may need the instanceOfStructureDefinition url later
    instanceDef._instanceMeta.instanceOfUrl = instanceOfStructureDefinition.getUrl();
    instanceDef.validateId(fshDefinition);
    validateRequiredElements(instanceDef, instanceOfStructureDefinition.elements, fshDefinition);
    checkForNamelessSlices(fshDefinition, instanceDef, instanceOfStructureDefinition);
    Common.cleanResource(instanceDef, prop -> false);
    Common.checkForMultipleChoice(fshDefinition, instanceDef, instanceOfStructureDefinition);
    pkg.instances.add(instanceDef);
    if (fshDefinition.usage != InstanceUsage.Inline) {
      pkg.fshMap.put(instanceDef.getFileName(), new Package.FshMapEntry(fshDefinition.sourceInfo, fshDefinition.name,
          fshDefinition.constructorName()));
    }

    // Once all rules are set, we should ensure that we did not add a duplicate profile URL anywhere
    // if any of the duplicates have child elements, such as extension, merge them
    dedupeMetaProfiles(instanceDef);

    // check for another instance of the same type with the same id
    // see https://www.hl7.org/fhir/resource.html#id
    // instanceDef has already been added to the package, so it's fine if it matches itself
    // inline instances are not written to their own files, so those can be skipped in this
    boolean thisInline = InstanceUsage.Inline.name().equals(instanceDef._instanceMeta.usage);
    if (!thisInline) {
      Object thisId = idOrName(instanceDef);
      for (InstanceDefinition instance : pkg.instances) {
        if (!InstanceUsage.Inline.name().equals(instance._instanceMeta.usage)
            && Objects.equals(instanceDef.getResourceType(), instance.getResourceType())
            && Objects.equals(thisId, idOrName(instance)) && instanceDef != instance) {
          logger.error("Multiple instances of type " + str(instanceDef.get("resourceType")) + " with id "
              + str(thisId) + ". Each non-inline instance of a given type must have a unique id.",
              fshDefinition.sourceInfo);
          break;
        }
      }
    }

    return instanceDef;
  }

  /** TS {@code instance.id ?? instance._instanceMeta.name}. */
  private static Object idOrName(InstanceDefinition instance) {
    Object id = instance.get("id");
    return id != null && id != Js.UNDEFINED ? id : instance._instanceMeta.name;
  }

  private static boolean hasElementWithId(StructureDefinition sd, String id) {
    return sd.elements.stream().anyMatch(element -> Objects.equals(element.getId(), id));
  }

  @SuppressWarnings("unchecked")
  private void setMetaProfile(Instance fshDefinition, InstanceDefinition instanceDef, String sdUrl) {
    // elements of instanceDef.meta.profile may be objects if they are provided by slices,
    // since they have to keep track of the _sliceName property.
    // this is technically not a match for the defined type of instanceDef.meta.profile,
    // so give the parameter a union type to handle both cases.
    Object metaObj = instanceDef.get("meta");
    Map<String, Object> meta = metaObj instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    List<Object> profile = meta != null && meta.get("profile") instanceof List<?> p ? (List<Object>) p : null;
    if (profile != null) {
      for (Object p : profile) {
        Object profileUrl = p instanceof Map<?, ?> pm ? pm.get("assignedValue") : p;
        if (profileUrl instanceof String s && (s.equals(sdUrl) || s.startsWith(sdUrl + "|"))) {
          return;
        }
      }
    }
    // Ref: StructureDefinition.version
    // "This is an arbitrary value managed by the structure definition author"
    // Convention: Technically, a version might have a literal | in it. So when we split out the version, we usually
    // do it like this: const [name, ...versionParts] = currentType.split('|');
    String[] split = fshDefinition.instanceOf.split(Pattern.quote("|"), -1);
    String instanceOfVersion = String.join("|", java.util.Arrays.asList(split).subList(1, split.length));
    String metaProfileUrl = !instanceOfVersion.isEmpty() ? sdUrl + "|" + instanceOfVersion : sdUrl;

    // we might have to create meta or meta.profile first, if no rules already created those
    if (meta == null) {
      JsObject newMeta = new JsObject();
      newMeta.put("profile", new ArrayList<>(List.of(metaProfileUrl)));
      instanceDef.put("meta", newMeta);
    } else if (profile == null) {
      meta.put("profile", new ArrayList<>(List.of(metaProfileUrl)));
    } else {
      List<Object> underscoreProfile = meta.get("_profile") instanceof List<?> up ? (List<Object>) up : null;
      if (underscoreProfile != null && !underscoreProfile.isEmpty()) {
        // If an AssignmentRule adds `meta.profile`, the setAssignedValues() function call above
        // will also add `meta._profile`. Therefore, the if block will always be executed.
        if (isEmpty(profile.isEmpty() ? null : profile.get(0))) {
          if (profile.isEmpty()) profile.add(metaProfileUrl);
          else profile.set(0, metaProfileUrl);
        } else {
          profile.add(0, metaProfileUrl);
          underscoreProfile.add(0, null);
        }
      } else {
        // Apparently not reachable, but just in case
        profile.add(metaProfileUrl);
      }
    }
  }

  /** TS dedupe of meta.profile (zip/uniqWith/differenceWith/mergeWith/unzip, or uniq). */
  @SuppressWarnings("unchecked")
  private static void dedupeMetaProfiles(InstanceDefinition instanceDef) {
    if (!(instanceDef.get("meta") instanceof Map<?, ?> m)) return;
    Map<String, Object> meta = (Map<String, Object>) m;
    if (!Js.truthy(meta.get("profile"))) return;
    List<Object> profile = meta.get("profile") instanceof List<?> p ? (List<Object>) p : null;
    if (profile == null) return; // TS: a non-array profile would throw; not produced by setPropertyOnInstance
    Object underscore = meta.get("_profile");
    if (Js.truthy(underscore) && underscore instanceof List<?> up) {
      List<Object> underscoreProfile = (List<Object>) up;
      // zip pads the shorter array with undefined
      int n = Math.max(profile.size(), underscoreProfile.size());
      List<Object[]> complexProfiles = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        complexProfiles.add(new Object[] {i < profile.size() ? profile.get(i) : Js.UNDEFINED,
            i < underscoreProfile.size() ? underscoreProfile.get(i) : Js.UNDEFINED});
      }
      List<Object[]> uniqueProfiles = new ArrayList<>();
      for (Object[] a : complexProfiles) {
        boolean dup = false;
        for (Object[] b : uniqueProfiles) {
          if (!isNullish(a[0]) && sameValueZero(a[0], b[0])) {
            dup = true;
            break;
          }
        }
        if (!dup) uniqueProfiles.add(a);
      }
      // differenceWith(complexProfiles, uniqueProfiles, isEqual)
      List<Object[]> removedProfiles = new ArrayList<>();
      for (Object[] c : complexProfiles) {
        boolean inUnique = false;
        for (Object[] u : uniqueProfiles) {
          if (Js.isEqual(pairList(c), pairList(u))) {
            inUnique = true;
            break;
          }
        }
        if (!inUnique) removedProfiles.add(c);
      }
      for (Object[] rp : removedProfiles) {
        int homeIndex = -1;
        for (int i = 0; i < uniqueProfiles.size(); i++) {
          if (sameValueZero(uniqueProfiles.get(i)[0], rp[0])) {
            homeIndex = i;
            break;
          }
        }
        Object home = uniqueProfiles.get(homeIndex)[1];
        // mergeWith(null/undefined, ...) merges into a throwaway object
        if (home instanceof Map || home instanceof List) {
          Lodash.mergeWith(home, rp[1], InstanceExporter::profileCustomizer);
        }
      }
      if (uniqueProfiles.isEmpty()) {
        meta.put("profile", Js.UNDEFINED);
        meta.put("_profile", Js.UNDEFINED);
      } else {
        List<Object> newProfile = new ArrayList<>();
        List<Object> newUnderscore = new ArrayList<>();
        for (Object[] u : uniqueProfiles) {
          newProfile.add(u[0] == Js.UNDEFINED ? null : u[0]);
          newUnderscore.add(u[1] == Js.UNDEFINED ? null : u[1]);
        }
        meta.put("profile", newProfile);
        meta.put("_profile", newUnderscore);
      }
    } else {
      List<Object> unique = new ArrayList<>();
      for (Object p : profile) {
        boolean seen = false;
        for (Object u : unique) {
          if (sameValueZero(p, u)) {
            seen = true;
            break;
          }
        }
        if (!seen) unique.add(p);
      }
      meta.put("profile", unique);
    }
  }

  private static List<Object> pairList(Object[] pair) {
    List<Object> l = new ArrayList<>(2);
    l.add(pair[0] == Js.UNDEFINED ? null : pair[0]);
    l.add(pair[1] == Js.UNDEFINED ? null : pair[1]);
    return l;
  }

  /**
   * The meta._profile mergeWith customizer: arrays are concatenated, primitives prefer the existing value if both
   * values are non-null (this makes use of the fact that typeof null === 'object').
   */
  private static Object profileCustomizer(Object a, Object b, String key) {
    if (a instanceof List<?> x && b instanceof List<?> y) {
      List<Object> r = new ArrayList<>(x);
      r.addAll(y);
      return r;
    } else if (!isTypeofObject(a) || !isTypeofObject(b)) {
      return !isNullish(a) ? a : b;
    }
    return Js.UNDEFINED;
  }

  /** lodash SameValueZero on value-model values (objects by identity). */
  private static boolean sameValueZero(Object a, Object b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    if (a instanceof Double x && b instanceof Double y) {
      return x.doubleValue() == y.doubleValue() || (x.isNaN() && y.isNaN());
    }
    if (a instanceof String || a instanceof Boolean || a instanceof BigInteger) return a.equals(b);
    return false;
  }

  /** Exports Instances. */
  public Package export() {
    List<Instance> instances = tank.getAllInstances();
    for (Instance instance : instances) {
      try {
        exportInstance(instance);
      } catch (RuntimeException e) {
        ExporterLog.errorWithStack(e, instance.sourceInfo);
      }
    }
    warnOnInstanceOfCustomResource();
    if (!instances.isEmpty()) {
      logger.info("Converted " + instances.size() + " FHIR instances.");
    }
    return pkg;
  }
}
