package org.hl7.fhir.sushi.export;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.CannotResolvePathError;
import org.hl7.fhir.sushi.errors.MismatchedTypeError;
import org.hl7.fhir.sushi.fhirtypes.CodeSystem;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.PathPart;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ConceptRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.utils.MasterFisher;
import org.hl7.fhir.sushi.utils.PathUtils;

import static org.hl7.fhir.sushi.utils.FshLogger.logger;

// port of sushi/src/export/CodeSystemExporter.ts (SUSHI 3.21.1) -- owned by WP-7
public class CodeSystemExporter {
  private static final Pattern LEADING_ZERO_INDEX = Pattern.compile("\\[0+\\]");

  private final FSHTank tank;
  private final Package pkg;
  private final MasterFisher fisher;

  public CodeSystemExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.tank = tank;
    this.pkg = pkg;
    this.fisher = fisher;
  }

  /**
   * TS ConceptIndex: the position of each concept within its list of concepts, by code, keyed by the concept lists
   * themselves (identity). setConcepts only appends and never adds a code twice, so a position stays valid.
   */
  private static final class ConceptIndex extends IdentityHashMap<List<Object>, Map<String, Integer>> {
    private static final long serialVersionUID = 1L;
  }

  private void setMetadata(CodeSystem codeSystem, FshCodeSystem fshDefinition) {
    codeSystem.setName(fshDefinition);
    codeSystem.setId(fshDefinition);
    if ("".equals(fshDefinition.title)) {
      logger.warn("Code system " + fshDefinition.name + " has a title field that should not be empty.");
    }
    if ("".equals(fshDefinition.description)) {
      logger.warn("Code system " + fshDefinition.name + " has a description field that should not be empty.");
    }
    if (Js.truthy(fshDefinition.title)) codeSystem.put("title", fshDefinition.title);
    if (Js.truthy(fshDefinition.description)) codeSystem.put("description", fshDefinition.description);
    if (Boolean.TRUE.equals(tank.config.FSHOnly)) {
      putOrUndefined(codeSystem, "version", tank.config.version);
    } else {
      codeSystem.remove("version"); // deleting to allow the IG Publisher default to take hold
    }
    putOrUndefined(codeSystem, "status", tank.config.status);
    codeSystem.put("url", tank.config.canonical + "/CodeSystem/" + ExporterLog.str(codeSystem.get("id")));
  }

  /** TS {@code obj.x = value} where value may be undefined: the key exists (in place) but is not serialized. */
  static void putOrUndefined(JsObject obj, String key, Object value) {
    obj.put(key, value == null ? Js.UNDEFINED : value);
  }

  /**
   * Adds the concepts to the CodeSystem.
   *
   * @return an index of the added concepts, so that neither finding an ancestor here nor resolving the path of a
   *     code caret rule later requires scanning a list of concepts
   */
  @SuppressWarnings("unchecked")
  private ConceptIndex setConcepts(CodeSystem codeSystem, List<ConceptRule> concepts) {
    ConceptIndex conceptIndex = new ConceptIndex();
    if (!concepts.isEmpty()) {
      List<Object> topLevel = new ArrayList<>();
      codeSystem.put("concept", topLevel);
      Map<String, ConceptRule> existingConcepts = new LinkedHashMap<>();
      conceptLoop:
      for (ConceptRule concept : concepts) {
        ConceptRule existingConcept = existingConcepts.get(concept.code);
        if (existingConcept != null) {
          // if this concept has only a code (and optionally, a hierarchy),
          // and the existing concept has the same code and hierarchy,
          // this concept may just be used to establish path context, which is fine.
          if (!(concept.display == null && concept.definition == null
              && concept.hierarchy.equals(existingConcept.hierarchy))) {
            // duplicates are prohibited: http://hl7.org/fhir/codesystem.html#invs
            logger.error("CodeSystem " + ExporterLog.str(codeSystem.get("id")) + " already contains code " + concept.code
                + ".", concept.sourceInfo);
          }
        } else {
          List<Object> conceptContainer = topLevel;
          JsObject newConcept = new JsObject();
          newConcept.put("code", concept.code);
          if (Js.truthy(concept.display)) {
            newConcept.put("display", concept.display);
          }
          if (Js.truthy(concept.definition)) {
            newConcept.put("definition", concept.definition);
          }
          for (String ancestorCode : concept.hierarchy) {
            Map<String, Integer> siblings = conceptIndex.get(conceptContainer);
            Integer ancestorIndex = siblings == null ? null : siblings.get(ancestorCode);
            if (ancestorIndex != null) {
              JsObject ancestorConcept = (JsObject) conceptContainer.get(ancestorIndex);
              if (!(ancestorConcept.get("concept") instanceof List)) {
                ancestorConcept.put("concept", new ArrayList<>());
              }
              conceptContainer = (List<Object>) ancestorConcept.get("concept");
            } else {
              logger.error("Could not find " + ancestorCode + " in concept hierarchy to use as ancestor of "
                  + concept.code + ".", concept.sourceInfo);
              continue conceptLoop;
            }
          }
          Map<String, Integer> siblingIndex = conceptIndex.computeIfAbsent(conceptContainer,
              k -> new LinkedHashMap<>());
          siblingIndex.put(concept.code, conceptContainer.size());
          conceptContainer.add(newConcept);
          existingConcepts.put(concept.code, concept);
        }
      }
    }
    return conceptIndex;
  }

  private record RuleWithTypes(CaretValueRule rule, List<String> inlineResourceTypes) {}

  private record InlineResourcePath(String path, String caretPath, String instanceOf) {}

  private void setCaretPathRules(CodeSystem codeSystem, FshCodeSystem fshDefinition,
      StructureDefinition codeSystemSD, ConceptIndex conceptIndex) {
    List<CaretValueRule> rules = new ArrayList<>();
    for (Rule r : fshDefinition.rules) {
      if (r instanceof CaretValueRule c) rules.add(c);
    }
    // soft index resolution relies on the rule's path attribute.
    // a CaretValueRule is created with an empty path, so first
    // transform its arrayPath into a path.
    // Because this.findConceptPath can potentially throw an error,
    // build a list of successful rules that will actually be applied.
    List<CaretValueRule> successfulRules = new ArrayList<>();
    for (CaretValueRule rule : rules) {
      try {
        rule.path = findConceptPath(codeSystem, rule.pathArray, conceptIndex);
        successfulRules.add(rule);
        if (!rule.path.isEmpty()) {
          rule.isCodeCaretRule = true;
        }
      } catch (RuntimeException e) {
        ExporterLog.errorWithStack(e, rule.sourceInfo);
      }
    }
    PathUtils.resolveSoftIndexing(successfulRules);
    // the paths of these rules were changed in place, and a rule whose path was reset to an empty path may
    // newly match a cached lookup (e.g., a ^url rule inserted with a path context), which the cache can not
    // detect. resolving soft indexes can not make a rule match a lookup, but clear the cache after both.
    org.hl7.fhir.sushi.fshtypes.Common.clearRuleLookupCache(fshDefinition);

    // a codesystem is a specific case where the only implied values are going to be extension urls.
    // so, we only need to track rules that involve an extension.
    Map<String, Common.HasPathParts> ruleMap = new LinkedHashMap<>();
    // in order to validate rules that set values on contained resources, we need to track information from rules
    // that define the types of those resources. those types could be defined by rules on the "resourceType"
    // element, or they could be defined by the existing resource that is being assigned.
    List<InlineResourcePath> inlineResourcePaths = new ArrayList<>();
    // first, collect the information we can from rules that set a resourceType
    // if instances are directly assigned, we'll get information from them when we fish up the instance.
    for (CaretValueRule r : successfulRules) {
      if (r.caretPath.endsWith(".resourceType") && r.value instanceof String s && !r.isInstance) {
        List<String> parts = Common.splitOnPathPeriods(r.caretPath);
        inlineResourcePaths.add(new InlineResourcePath(r.path,
            String.join(".", parts.subList(0, parts.size() - 1)), s));
      }
    }
    List<RuleWithTypes> successfulRulesWithInstances = new ArrayList<>();
    for (CaretValueRule rule : successfulRules) {
      if (rule.isInstance) {
        InstanceExporter instanceExporter = new InstanceExporter(tank, pkg, fisher);
        InstanceDefinition instance = instanceExporter.fishForFHIR(ExporterLog.str(rule.value));
        if (instance == null) {
          logger.error("Cannot find definition for Instance: " + ExporterLog.str(rule.value) + ". Skipping rule.",
              rule.sourceInfo);
          continue;
        }
        if (InstanceUsage.Example.name().equals(instance._instanceMeta.usage)) {
          logger.warn("Contained instance \"" + ExporterLog.str(rule.value)
              + "\" is an example and probably should not be included in a conformance resource.", rule.sourceInfo);
        }
        rule.value = instance;
        // since we found a resource, save its type in our list of inline resource paths.
        inlineResourcePaths.add(new InlineResourcePath(rule.path, rule.caretPath, instance.resourceType()));
      }
      // the relevant inline resource paths for the current rule are rules with:
      // - the same path
      // - a caret path that is an ancestor of the current rule's path
      // - and also, the current rule's caret path can not be this other rule's caret path followed by
      //   "resourceType".
      List<String> inlineResourceTypes = new ArrayList<>();
      for (InlineResourcePath match : inlineResourcePaths) {
        if (rule.path.equals(match.path()) && rule.caretPath.startsWith(match.caretPath() + ".")
            && !rule.caretPath.equals(match.caretPath() + ".resourceType")) {
          setSparse(inlineResourceTypes, Common.splitOnPathPeriods(match.caretPath()).size() - 1,
              match.instanceOf());
        }
      }
      String path = rule.path.length() > 1 ? rule.path + "." + rule.caretPath : rule.caretPath;
      try {
        CaretValueRule replacedRule = Common.replaceReferences(rule, tank, fisher);
        List<PathPart> pathParts = codeSystemSD
            .validateValueAtPath(path, replacedRule.value, fisher, inlineResourceTypes).pathParts();
        if (pathParts.stream().anyMatch(part -> Common.isExtension(part.base))) {
          ruleMap.put(LEADING_ZERO_INDEX.matcher(PathUtils.assembleFSHPath(pathParts)).replaceAll(""),
              () -> pathParts);
        }
        successfulRulesWithInstances.add(new RuleWithTypes(replacedRule, inlineResourceTypes));
      } catch (RuntimeException originalErr) {
        // if an Instance has an id that looks like a number, bigint, or boolean,
        // we may have tried to assign that value instead of an Instance.
        // try to fish up an Instance with the rule's raw value.
        // if we find one, try assigning that instead.
        if (originalErr instanceof MismatchedTypeError mte && isNumberBigintOrBoolean(rule.value)) {
          InstanceExporter instanceExporter = new InstanceExporter(tank, pkg, fisher);
          Common.InstanceAndPathParts result = Common.validateInstanceFromRawValue(codeSystem, rule,
              instanceExporter, fisher, mte, inlineResourceTypes);
          InstanceDefinition instance = result.instance();
          if (instance != null && InstanceUsage.Example.name().equals(instance._instanceMeta.usage)) {
            logger.warn("Contained instance \"" + rule.rawValue
                + "\" is an example and probably should not be included in a conformance resource.",
                rule.sourceInfo);
          }
          rule.value = instance;
          if (instance != null) {
            // this rule ended up assigning an instance, so save its type in our list of inline resource paths.
            inlineResourcePaths.add(new InlineResourcePath(rule.path, rule.caretPath, instance.resourceType()));
          }
          List<PathPart> pathParts = result.pathParts();
          if (pathParts.stream().anyMatch(part -> Common.isExtension(part.base))) {
            ruleMap.put(LEADING_ZERO_INDEX.matcher(PathUtils.assembleFSHPath(pathParts)).replaceAll(""),
                () -> pathParts);
          }
          successfulRulesWithInstances.add(new RuleWithTypes(rule, inlineResourceTypes));
        } else {
          ExporterLog.errorWithStack(originalErr, rule.sourceInfo);
        }
      }
    }
    Map<String, Integer> knownSlices = Common.determineKnownSlices(codeSystemSD, ruleMap, fisher);
    List<String> assignedResourcePaths = new ArrayList<>();
    for (InlineResourcePath i : inlineResourcePaths) assignedResourcePaths.add(i.caretPath());
    Common.setImpliedPropertiesOnInstance(codeSystem, codeSystemSD, new ArrayList<>(ruleMap.keySet()),
        assignedResourcePaths, fisher, knownSlices, false);

    for (RuleWithTypes rwt : successfulRulesWithInstances) {
      CaretValueRule rule = rwt.rule();
      try {
        Common.setPropertyOnDefinitionInstance(codeSystem,
            rule.path.length() > 1 ? rule.path + "." + rule.caretPath : rule.caretPath, rule.value, fisher,
            rwt.inlineResourceTypes(), codeSystemSD);
      } catch (RuntimeException err) {
        ExporterLog.errorWithStack(err, rule.sourceInfo);
      }
    }
  }

  /** TS {@code arr[i] = v} on a (possibly sparse) array: pads with holes (null). */
  static void setSparse(List<String> list, int index, String value) {
    while (list.size() <= index) list.add(null);
    list.set(index, value);
  }

  /** TS {@code ['number', 'bigint', 'boolean'].includes(typeof value)}. */
  static boolean isNumberBigintOrBoolean(Object value) {
    return value instanceof Double || value instanceof java.math.BigInteger || value instanceof Boolean;
  }

  /**
   * Finds the FSH path to the concept identified by codePath. For example, if #a is the third top-level concept
   * and #b is its first child, ['#a', '#b'] becomes concept[2].concept[0]. An empty codePath (a caret rule that is
   * not on a concept) returns an empty path.
   *
   * @throws CannotResolvePathError when a step in codePath is not a code, or its code is not found at that level
   *     of the hierarchy
   */
  @SuppressWarnings("unchecked")
  private String findConceptPath(CodeSystem codeSystem, List<String> codePath, ConceptIndex conceptIndex) {
    List<Integer> conceptIndices = new ArrayList<>();
    List<Object> conceptList = codeSystem.get("concept") instanceof List<?> l ? (List<Object>) l : List.of();
    for (String codeStep : codePath) {
      // a code has a leading #. a step without one comes from a caret rule with an element path that was
      // inserted from a RuleSet, and it never identifies a concept.
      Integer stepIndex = null;
      if (codeStep.startsWith("#")) {
        Map<String, Integer> siblings = conceptIndex.get(conceptList);
        stepIndex = siblings == null ? null : siblings.get(codeStep.substring(1));
      }
      if (stepIndex == null) {
        throw new CannotResolvePathError(String.join(" ", codePath));
      }
      conceptIndices.add(stepIndex);
      Object children = ((JsObject) conceptList.get(stepIndex)).get("concept");
      conceptList = children instanceof List<?> l ? (List<Object>) l : List.of();
    }
    List<String> parts = new ArrayList<>();
    for (Integer i : conceptIndices) parts.add("concept[" + i + "]");
    return String.join(".", parts);
  }

  private int countConcepts(Object concepts) {
    if (concepts instanceof List<?> list) {
      int sum = list.size();
      for (Object concept : list) {
        sum += countConcepts(concept instanceof JsObject o ? o.get("concept") : null);
      }
      return sum;
    }
    return 0;
  }

  private void updateCount(CodeSystem codeSystem, FshCodeSystem fshDefinition) {
    // We can only derive a true count if the content is #complete
    if ("complete".equals(codeSystem.get("content"))) {
      int counted = countConcepts(codeSystem.get("concept"));
      Double actualCount = counted == 0 ? null : (double) counted;
      Object count = codeSystem.get("count");
      boolean countIsNull = count == null || count == Js.UNDEFINED;
      if (countIsNull && actualCount != null) {
        codeSystem.put("count", actualCount);
      } else if (!strictEqualsCount(codeSystem.containsKey("count"), count, actualCount)) {
        SourceInfo sourceInfo = fshDefinition.sourceInfo;
        for (Rule r : fshDefinition.rules) {
          if (r instanceof CaretValueRule c && "count".equals(c.caretPath)) {
            sourceInfo = c.sourceInfo;
            break;
          }
        }
        logger.warn("The user-specified ^count (" + ExporterLog.str(count) + ") does not match the specified number of "
            + "concepts (" + (actualCount == null ? "0" : ExporterLog.str(actualCount)) + "). If this is not a \"complete\" "
            + "CodeSystem, set the ^content property to the appropriate value; otherwise fix or remove the ^count.",
            sourceInfo);
      }
    }
  }

  /** TS {@code codeSystem.count === actualCount}; actualCount null = undefined, which a JSON null is not. */
  private static boolean strictEqualsCount(boolean present, Object count, Double actualCount) {
    if (actualCount == null) {
      return !present || count == Js.UNDEFINED;
    }
    return count instanceof Double d && d.doubleValue() == actualCount.doubleValue();
  }

  public void applyInsertRules() {
    for (FshCodeSystem cs : tank.getAllCodeSystems()) {
      Common.applyInsertRules(cs, tank);
    }
  }

  /** Returns null (TS undefined) when a code system with the same name was already exported. */
  public CodeSystem exportCodeSystem(FshCodeSystem fshDefinition) {
    for (CodeSystem cs : pkg.codeSystems) {
      if (java.util.Objects.equals(cs.get("name"), fshDefinition.name)) {
        return null;
      }
    }
    CodeSystem codeSystem = new CodeSystem();
    StructureDefinition codeSystemSD = codeSystem.getOwnStructureDefinition(fisher);
    setMetadata(codeSystem, fshDefinition);
    List<ConceptRule> conceptRules = new ArrayList<>();
    for (Rule r : fshDefinition.rules) {
      if (r instanceof ConceptRule c) conceptRules.add(c);
    }
    ConceptIndex conceptIndex = setConcepts(codeSystem, conceptRules);
    setCaretPathRules(codeSystem, fshDefinition, codeSystemSD, conceptIndex);

    // check for another code system with the same id
    // see https://www.hl7.org/fhir/resource.html#id
    for (CodeSystem cs : pkg.codeSystems) {
      if (java.util.Objects.equals(codeSystem.get("id"), cs.get("id"))) {
        logger.error("Multiple code systems with id " + ExporterLog.str(codeSystem.get("id"))
            + ". Each code system must have a unique id.", fshDefinition.sourceInfo);
        break;
      }
    }

    Common.cleanResource(codeSystem, prop -> prop.equals("_sliceName") || prop.equals("_primitive"));
    Common.checkForMultipleChoice(fshDefinition, codeSystem, codeSystemSD);
    updateCount(codeSystem, fshDefinition);
    pkg.codeSystems.add(codeSystem);
    pkg.fshMap.put(codeSystem.getFileName(),
        new Package.FshMapEntry(fshDefinition.sourceInfo, fshDefinition.name, "CodeSystem"));
    return codeSystem;
  }

  public Package export() {
    List<FshCodeSystem> codeSystems = tank.getAllCodeSystems();
    for (FshCodeSystem cs : codeSystems) {
      try {
        exportCodeSystem(cs);
      } catch (RuntimeException e) {
        ExporterLog.errorWithStack(e, cs.sourceInfo);
      }
    }
    if (!codeSystems.isEmpty()) {
      logger.info("Converted " + codeSystems.size() + " FHIR CodeSystems.");
    }
    return pkg;
  }
}
