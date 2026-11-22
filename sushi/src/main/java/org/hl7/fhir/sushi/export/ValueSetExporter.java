package org.hl7.fhir.sushi.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.InvalidUriError;
import org.hl7.fhir.sushi.errors.MismatchedTypeError;
import org.hl7.fhir.sushi.errors.ValueSetComposeError;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.PathPart;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fhirtypes.ValueSet;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.ValueSetFilter;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.fshtypes.rules.ValueSetComponentRule;
import org.hl7.fhir.sushi.fshtypes.rules.ValueSetConceptComponentRule;
import org.hl7.fhir.sushi.fshtypes.rules.ValueSetFilterComponentRule;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FishingUtils;
import org.hl7.fhir.sushi.utils.MasterFisher;
import org.hl7.fhir.sushi.utils.Metadata;
import org.hl7.fhir.sushi.utils.PathUtils;
import org.hl7.fhir.sushi.utils.ValidUrl;

import static org.hl7.fhir.sushi.export.ExporterLog.str;
import static org.hl7.fhir.sushi.utils.FshLogger.logger;

// port of sushi/src/export/ValueSetExporter.ts (SUSHI 3.21.1) -- owned by WP-7
/**
 * The compose elements and concepts are JsObjects whose keys are created in TS assignment order
 * (system, _system, version, valueSet, concept, filter). {@code composeElement.version = x || undefined} keeps an
 * undefined-valued key, as TS does; comparisons treat it like an absent version ({@link #same}).
 */
public class ValueSetExporter {
  private static final Pattern LEADING_ZERO_INDEX = Pattern.compile("\\[0+\\]");
  private static final String PIPE = Pattern.quote("|");

  private final FSHTank tank;
  private final Package pkg;
  private final MasterFisher fisher;

  public ValueSetExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.tank = tank;
    this.pkg = pkg;
    this.fisher = fisher;
  }

  private void setMetadata(ValueSet valueSet, FshValueSet fshDefinition) {
    valueSet.setName(fshDefinition);
    valueSet.setId(fshDefinition);
    if ("".equals(fshDefinition.title)) {
      logger.warn("Value set " + fshDefinition.name + " has a title field that should not be empty.");
    }
    if ("".equals(fshDefinition.description)) {
      logger.warn("Value set " + fshDefinition.name + " has a description field that should not be empty.");
    }
    if (Js.truthy(fshDefinition.title)) {
      valueSet.put("title", fshDefinition.title);
    }
    if (Js.truthy(fshDefinition.description)) {
      valueSet.put("description", fshDefinition.description);
    }
    if (Boolean.TRUE.equals(tank.config.FSHOnly)) {
      CodeSystemExporter.putOrUndefined(valueSet, "version", tank.config.version);
    } else {
      valueSet.remove("version"); // deleting to allow the IG Publisher default to take hold
    }
    CodeSystemExporter.putOrUndefined(valueSet, "status", tank.config.status);
    valueSet.put("url", tank.config.canonical + "/ValueSet/" + str(valueSet.get("id")));
  }

  /** TS {@code a === b} where both undefined-valued and absent keys read as undefined. */
  private static boolean same(Object a, Object b) {
    if (a == Js.UNDEFINED) a = null;
    if (b == Js.UNDEFINED) b = null;
    return Objects.equals(a, b);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(JsObject o, String key) {
    return o.get(key) instanceof List<?> l ? (List<Object>) l : null;
  }

  private static boolean hasConcepts(JsObject compose) {
    List<Object> concept = list(compose, "concept");
    return concept != null && !concept.isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static List<Object> containedOf(ValueSet valueSet) {
    return valueSet.get("contained") instanceof List<?> l ? (List<Object>) l : null;
  }

  private static Object prop(Object resource, String key) {
    return resource instanceof Map<?, ?> m ? m.get(key) : null;
  }

  @SuppressWarnings("unchecked")
  private void setCompose(ValueSet valueSet, List<ValueSetComponentRule> components) {
    if (components.isEmpty()) {
      return;
    }
    JsObject compose = new JsObject();
    List<Object> include = new ArrayList<>();
    List<Object> exclude = new ArrayList<>();
    compose.put("include", include);
    compose.put("exclude", exclude);
    valueSet.put("compose", compose);
    for (ValueSetComponentRule component : components) {
      JsObject composeElement = new JsObject();
      if (Js.truthy(component.from.system)) {
        String[] systemParts = component.from.system.split(PIPE, -1);
        Metadata csMetadata = fisher.fishForMetadata(component.from.system, FishableType.CodeSystem);
        // if we found metadata, use it.
        // if we didn't find any matching metadata, the code system might be defined directly on the valueset.
        boolean isContainedSystem;
        boolean systemIsInlineInstance = false;
        Object systemId = null;
        List<Object> contained = containedOf(valueSet);
        if (csMetadata != null) {
          composeElement.put("system", csMetadata.url != null ? csMetadata.url : systemParts[0]);
          isContainedSystem = contained != null && contained.stream()
              .anyMatch(resource -> Objects.equals(prop(resource, "id"), csMetadata.id)
                  && "CodeSystem".equals(prop(resource, "resourceType")));
          systemIsInlineInstance = "Inline".equals(csMetadata.instanceUsage);
          systemId = csMetadata.id;
        } else {
          String system = component.from.system;
          Object directSystem = contained == null ? null : contained.stream()
              .filter(resource -> (system.equals(prop(resource, "id")) || system.equals(prop(resource, "name"))
                  || system.equals(prop(resource, "url"))) && "CodeSystem".equals(prop(resource, "resourceType")))
              .findFirst().orElse(null);
          if (directSystem != null) {
            isContainedSystem = true;
            composeElement.put("system", prop(directSystem, "url"));
            systemId = prop(directSystem, "id");
          } else {
            isContainedSystem = false;
            composeElement.put("system", systemParts[0]);
          }
        }
        // if the code system is also a contained resource, add the valueset-system extension
        // this zulip thread contains a discussion of the issue and an example using this extension:
        // https://chat.fhir.org/#narrow/stream/215610-shorthand/topic/Contained.20code.20system.20in.20the.20value.20set/near/424938537
        // additionally, if it's not a contained resource, and the system we found is an inline instance, that's a
        // problem
        if (isContainedSystem) {
          JsObject ext = new JsObject();
          ext.put("url", "http://hl7.org/fhir/StructureDefinition/valueset-system");
          ext.put("valueCanonical", "#" + str(systemId));
          JsObject systemElement = new JsObject();
          systemElement.put("extension", new ArrayList<>(List.of(ext)));
          composeElement.put("_system", systemElement);
        } else if (systemIsInlineInstance) {
          logger.error("Can not reference CodeSystem " + component.from.system
              + ": this CodeSystem is an inline instance, but it is not present in the list of contained resources.",
              component.sourceInfo);
          continue;
        }

        // if the rule specified a version, use that version.
        String version = String.join("|", Arrays.asList(systemParts).subList(1, systemParts.length));
        composeElement.put("version", version.isEmpty() ? Js.UNDEFINED : version);
        Object system = composeElement.get("system");
        if (!ValidUrl.isUri(system instanceof String s ? s : null)) {
          throw new InvalidUriError(str(system));
        }
      }
      if (component.from.valueSets != null) {
        List<Object> valueSets = new ArrayList<>();
        for (String vs : component.from.valueSets) {
          Metadata vsMetadata = FishingUtils.fishForMetadataBestVersion(fisher, vs, null, FishableType.ValueSet);
          if (vsMetadata != null && Js.truthy(vsMetadata.url)) {
            String[] parts = vs.split(PIPE, -1);
            String version = String.join("|", Arrays.asList(parts).subList(1, parts.length));
            valueSets.add(!version.isEmpty() ? vsMetadata.url + "|" + version : vsMetadata.url);
          } else {
            valueSets.add(vs);
          }
        }
        Object selfUrl = valueSet.get("url");
        List<Object> filtered = new ArrayList<>();
        for (Object vs : valueSets) {
          if (Objects.equals(vs, selfUrl)) {
            logger.error("Value set with id " + str(valueSet.get("id"))
                + " has component rule with self-referencing value set (by id, value set name, or url). Removing "
                + "self-reference.");
          } else {
            filtered.add(vs);
          }
        }
        composeElement.put("valueSet", filtered);
        for (Object vs : filtered) {
          // Canonical URI may include | to specify version: https://www.hl7.org/fhir/references.html#canonical
          if (!ValidUrl.isUri(((String) vs).split(PIPE, -1)[0])) {
            throw new InvalidUriError((String) vs);
          }
        }
      }
      if (component instanceof ValueSetConceptComponentRule conceptRule && !conceptRule.concepts.isEmpty()) {
        List<Object> concepts = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (FshCode concept : conceptRule.concepts) {
          JsObject composeConcept = new JsObject();
          composeConcept.put("code", concept.code);
          if (Js.truthy(concept.display)) {
            composeConcept.put("display", concept.display);
          }
          concepts.add(composeConcept);
          codes.add(concept.code);
        }
        composeElement.put("concept", concepts);
        // if we can fish up the system in the tank, it's local, and we should check the listed concepts
        Object codeSystem = fishLocalCodeSystem(composeElement.get("system"));
        if (codeSystem instanceof FshCodeSystem || codeSystem instanceof Instance) {
          Common.listUndefinedLocalCodes((FshEntity) codeSystem, codes, tank, component);
        }
      } else if (component instanceof ValueSetFilterComponentRule filterRule && !filterRule.filters.isEmpty()) {
        List<Object> filters = new ArrayList<>();
        for (ValueSetFilter filter : filterRule.filters) {
          // if filter.value is a FshCode, perform the local code system check here as well
          if (filter.value instanceof FshCode code) {
            Object codeSystem = fishLocalCodeSystem(composeElement.get("system"));
            if (codeSystem instanceof FshCodeSystem || codeSystem instanceof Instance) {
              Common.listUndefinedLocalCodes((FshEntity) codeSystem, List.of(code.code), tank, component);
            }
          }
          JsObject f = new JsObject();
          f.put("property", filter.property);
          f.put("op", filter.operator.toString());
          f.put("value", filterValueToString(filter.value));
          filters.add(f);
        }
        composeElement.put("filter", filters);
      }
      if (component.inclusion) {
        if (hasConcepts(composeElement)) {
          // warn the user if they have already included a concept in this component
          // concept, system, and version must all match to be considered equal
          List<Object> potentialMatches = new ArrayList<>();
          for (Object c : include) {
            JsObject compose2 = (JsObject) c;
            if (same(compose2.get("system"), composeElement.get("system"))
                && same(compose2.get("version"), composeElement.get("version")) && hasConcepts(compose2)) {
              for (Object concept : list(compose2, "concept")) {
                potentialMatches.add(prop(concept, "code"));
              }
            }
          }
          List<Object> currentConcepts = list(composeElement, "concept");
          List<Object> kept = new ArrayList<>();
          for (int idx = 0; idx < currentConcepts.size(); idx++) {
            Object code = prop(currentConcepts.get(idx), "code");
            boolean duplicate = potentialMatches.contains(code);
            for (int j = 0; j < idx && !duplicate; j++) {
              duplicate = Objects.equals(prop(currentConcepts.get(j), "code"), code);
            }
            if (duplicate) {
              Object version = composeElement.get("version");
              logger.warn("ValueSet " + str(valueSet.get("name")) + " already includes "
                  + str(composeElement.get("system")) + (Js.truthy(version) ? "|" + str(version) : "") + "#"
                  + str(code), component.sourceInfo);
            } else {
              kept.add(currentConcepts.get(idx));
            }
          }
          composeElement.put("concept", kept);
          if (!kept.isEmpty()) {
            addConceptComposeElement(composeElement, include);
          }
        } else {
          List<Object> vsList = list(composeElement, "valueSet");
          Object system = composeElement.get("system");
          if ((vsList == null || !vsList.isEmpty()) || (system != null && system != Js.UNDEFINED)) {
            include.add(composeElement);
          }
        }
      } else {
        addConceptComposeElement(composeElement, exclude);
      }
    }
    if (exclude.isEmpty()) {
      compose.remove("exclude");
    }
  }

  /** TS {@code this.tank.fish(composeElement.system, Type.CodeSystem)}. */
  private Object fishLocalCodeSystem(Object system) {
    return tank.fish(system instanceof String s ? s : null, FishableType.CodeSystem);
  }

  private void addConceptComposeElement(JsObject freshElement, List<Object> composeList) {
    if (hasConcepts(freshElement)) {
      int matchingFromIndex = -1;
      for (int i = 0; i < composeList.size(); i++) {
        JsObject compose = (JsObject) composeList.get(i);
        if (same(compose.get("system"), freshElement.get("system"))
            && same(compose.get("version"), freshElement.get("version")) && hasConcepts(compose)
            && xorIsEmpty(list(compose, "valueSet"), list(freshElement, "valueSet"))) {
          matchingFromIndex = i;
          break;
        }
      }
      if (matchingFromIndex > -1) {
        list((JsObject) composeList.get(matchingFromIndex), "concept").addAll(list(freshElement, "concept"));
      } else {
        composeList.add(freshElement);
      }
    } else {
      composeList.add(freshElement);
    }
  }

  /** lodash {@code xor(a ?? [], b ?? []).length === 0}: same set of values (SameValueZero). */
  private static boolean xorIsEmpty(List<Object> a, List<Object> b) {
    Set<Object> sa = new LinkedHashSet<>(a == null ? List.of() : a);
    Set<Object> sb = new LinkedHashSet<>(b == null ? List.of() : b);
    return sa.equals(sb);
  }

  private record RuleWithTypes(CaretValueRule rule, List<String> inlineResourceTypes) {}

  private record InlineResourcePath(String caretPath, String instanceOf) {}

  private void setCaretRules(ValueSet valueSet, List<CaretValueRule> rules, StructureDefinition valueSetSD) {
    PathUtils.resolveSoftIndexing(rules);

    Map<String, Common.HasPathParts> ruleMap = new LinkedHashMap<>();
    // in order to validate rules that set values on contained resources, we need to track information from rules
    // that define the types of those resources. those types could be defined by rules on the "resourceType"
    // element, or they could be defined by the existing resource that is being assigned.
    // the path is always empty for these rules, so we don't need to track or check those.
    List<InlineResourcePath> inlineResourcePaths = new ArrayList<>();
    // first, collect the information we can from rules that set a resourceType
    // if instances are directly assigned, we'll get information from them when we fish up the instance.
    for (CaretValueRule r : rules) {
      if (r.caretPath.endsWith(".resourceType") && r.value instanceof String s && !r.isInstance) {
        List<String> parts = Common.splitOnPathPeriods(r.caretPath);
        inlineResourcePaths.add(new InlineResourcePath(String.join(".", parts.subList(0, parts.size() - 1)), s));
      }
    }
    List<RuleWithTypes> rulesWithInstances = new ArrayList<>();
    for (CaretValueRule rule : rules) {
      if (rule.isInstance) {
        InstanceExporter instanceExporter = new InstanceExporter(tank, pkg, fisher);
        InstanceDefinition instance = instanceExporter.fishForFHIR(str(rule.value));
        if (instance == null) {
          logger.error("Cannot find definition for Instance: " + str(rule.value) + ". Skipping rule.",
              rule.sourceInfo);
          continue;
        }
        if (InstanceUsage.Example.name().equals(instance._instanceMeta.usage)) {
          logger.warn("Contained instance \"" + str(rule.value)
              + "\" is an example and probably should not be included in a conformance resource.", rule.sourceInfo);
        }
        rule.value = instance;
        // since we found a resource, save its type in our list of inline resource paths.
        inlineResourcePaths.add(new InlineResourcePath(rule.caretPath, instance.getResourceType()));
      }
      // the relevant inline resource paths for the current rule are rules with:
      // - a caret path that is an ancestor of the current rule's path
      // - and also, the current rule's caret path can not be this other rule's caret path followed by
      //   "resourceType".
      // for each of those matches, we build up the inline resource types array.
      // this is a sparse array that is parallel to an array of the parts of the current rule's caret path.
      List<String> inlineResourceTypes = new ArrayList<>();
      for (InlineResourcePath match : inlineResourcePaths) {
        if (rule.caretPath.startsWith(match.caretPath() + ".")
            && !rule.caretPath.equals(match.caretPath() + ".resourceType")) {
          CodeSystemExporter.setSparse(inlineResourceTypes,
              Common.splitOnPathPeriods(match.caretPath()).size() - 1, match.instanceOf());
        }
      }
      try {
        List<PathPart> pathParts = valueSetSD
            .validateValueAtPath(rule.caretPath, rule.value, fisher, inlineResourceTypes).pathParts();
        ruleMap.put(LEADING_ZERO_INDEX.matcher(PathUtils.assembleFSHPath(pathParts)).replaceAll(""),
            () -> pathParts);
        rulesWithInstances.add(new RuleWithTypes(rule, inlineResourceTypes));
      } catch (RuntimeException originalErr) {
        // if an Instance has an id that looks like a number, bigint, or boolean,
        // we may have tried to assign that value instead of an Instance.
        // try to fish up an Instance with the rule's raw value.
        // if we find one, try assigning that instead.
        if (originalErr instanceof MismatchedTypeError mte
            && CodeSystemExporter.isNumberBigintOrBoolean(rule.value)) {
          InstanceExporter instanceExporter = new InstanceExporter(tank, pkg, fisher);
          Common.InstanceAndPathParts result = Common.validateInstanceFromRawValue(valueSet, rule,
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
            inlineResourcePaths.add(new InlineResourcePath(rule.caretPath, instance.getResourceType()));
          }
          List<PathPart> pathParts = result.pathParts();
          ruleMap.put(LEADING_ZERO_INDEX.matcher(PathUtils.assembleFSHPath(pathParts)).replaceAll(""),
              () -> pathParts);
          rulesWithInstances.add(new RuleWithTypes(rule, inlineResourceTypes));
        } else {
          ExporterLog.errorWithStack(originalErr, rule.sourceInfo);
        }
      }
    }
    Map<String, Integer> knownSlices = Common.determineKnownSlices(valueSetSD, ruleMap, fisher);
    List<String> assignedResourcePaths = new ArrayList<>();
    for (InlineResourcePath i : inlineResourcePaths) assignedResourcePaths.add(i.caretPath());
    Common.setImpliedPropertiesOnInstance(valueSet, valueSetSD, new ArrayList<>(ruleMap.keySet()),
        assignedResourcePaths, fisher, knownSlices, false);

    for (RuleWithTypes rwt : rulesWithInstances) {
      try {
        Common.setPropertyOnDefinitionInstance(valueSet, rwt.rule().caretPath, rwt.rule().value, fisher,
            rwt.inlineResourceTypes(), valueSetSD);
      } catch (RuntimeException err) {
        ExporterLog.errorWithStack(err, rwt.rule().sourceInfo);
      }
    }
  }

  private record ConceptRuleEntry(List<PathPart> pathParts, CaretValueRule rule) implements Common.HasPathParts {}

  /** TS findIndex over an optional list: null (undefined) when the list is absent. */
  private static Integer findConceptIndex(JsObject composeElement, String code) {
    List<Object> concepts = composeElement == null ? null : list(composeElement, "concept");
    if (concepts == null) {
      return null;
    }
    for (int i = 0; i < concepts.size(); i++) {
      if (Objects.equals(prop(concepts.get(i), "code"), code)) {
        return i;
      }
    }
    return -1;
  }

  /** TS {@code vs.compose?.[array]?.findIndex(...) ?? -1} for the non-filter element of the given system. */
  private static int findComposeIndex(ValueSet vs, String array, String baseSystem, String version,
      Metadata systemMeta) {
    if (!(vs.get("compose") instanceof JsObject compose)) {
      return -1;
    }
    List<Object> elements = list(compose, array);
    if (elements == null) {
      return -1;
    }
    for (int i = 0; i < elements.size(); i++) {
      JsObject composeElement = (JsObject) elements.get(i);
      Object system = composeElement.get("system");
      Object v = composeElement.get("version");
      boolean systemMatches = (same(system, baseSystem) && same(v, version))
          || (same(system, systemMeta == null ? null : systemMeta.url) && same(v, version));
      Object filter = composeElement.get("filter");
      if (systemMatches && (filter == null || filter == Js.UNDEFINED)) {
        return i;
      }
    }
    return -1;
  }

  private void setConceptCaretRules(ValueSet vs, List<CaretValueRule> rules, StructureDefinition valueSetSD) {
    PathUtils.resolveSoftIndexing(rules);
    Map<String, ConceptRuleEntry> ruleMap = new LinkedHashMap<>();
    for (CaretValueRule rule : rules) {
      String[] splitConcept = rule.pathArray.get(0).split("#", -1);
      String system = splitConcept[0];
      String[] systemParts = system.split(PIPE, -1);
      String baseSystem = systemParts[0];
      String version = systemParts.length > 1 ? systemParts[1] : null;
      String code = String.join("#", Arrays.asList(splitConcept).subList(1, splitConcept.length));
      Metadata systemMeta = fisher.fishForMetadata(baseSystem, FishableType.CodeSystem);
      int composeIndex = findComposeIndex(vs, "include", baseSystem, version, systemMeta);
      String composeArray = null;
      Integer conceptIndex = -1;
      if (composeIndex != -1) {
        composeArray = "include";
        JsObject composeElement = (JsObject) list((JsObject) vs.get("compose"), "include").get(composeIndex);
        conceptIndex = findConceptIndex(composeElement, code);
      }
      if (conceptIndex != null && conceptIndex == -1) {
        composeIndex = findComposeIndex(vs, "exclude", baseSystem, version, systemMeta);
        if (composeIndex != -1) {
          composeArray = "exclude";
          JsObject composeElement = (JsObject) list((JsObject) vs.get("compose"), "exclude").get(composeIndex);
          conceptIndex = findConceptIndex(composeElement, code);
        }
      }
      if (conceptIndex == null) {
        logger.error("Cannot process caret assignment rule for code " + system + "#" + code + " because "
            + "this value set does not explicitly include or exclude this code in its "
            + "rules. To fix this error, add a rule that specifically includes or excludes "
            + "this code for the value set.");
        return;
      }
      if (conceptIndex != -1) {
        if (rule.isInstance) {
          InstanceExporter instanceExporter = new InstanceExporter(tank, pkg, fisher);
          InstanceDefinition instance = instanceExporter.fishForFHIR(str(rule.value));
          if (instance == null) {
            logger.error("Cannot find definition for Instance: " + str(rule.value) + ". Skipping rule.",
                rule.sourceInfo);
            continue;
          }
          rule.value = instance;
        }
        String fullPath = "compose." + composeArray + "[" + composeIndex + "].concept[" + conceptIndex + "]."
            + rule.caretPath;
        try {
          List<PathPart> pathParts = valueSetSD.validateValueAtPath(fullPath, rule.value, fisher, List.of())
              .pathParts();
          ruleMap.put(fullPath, new ConceptRuleEntry(pathParts, rule));
        } catch (RuntimeException e) {
          ExporterLog.errorWithStack(e, rule.sourceInfo);
        }
      } else {
        logger.error("Could not find concept " + rule.pathArray.get(0) + ", skipping rule.", rule.sourceInfo);
      }
    }

    Map<String, Integer> knownSlices = Common.determineKnownSlices(valueSetSD, ruleMap, fisher);
    Common.setImpliedPropertiesOnInstance(vs, valueSetSD, new ArrayList<>(ruleMap.keySet()), List.of(), fisher,
        knownSlices, false);

    for (Map.Entry<String, ConceptRuleEntry> e : ruleMap.entrySet()) {
      Common.setPropertyOnDefinitionInstance(vs, e.getKey(), e.getValue().rule().value, fisher, List.of(),
          valueSetSD);
    }
  }

  private static String filterValueToString(Object value) {
    if (value instanceof Pattern p) {
      return p.pattern();
    } else if (value instanceof FshCode c) {
      return c.code;
    } else {
      return Js.toStr(value);
    }
  }

  public void applyInsertRules() {
    for (FshValueSet vs : tank.getAllValueSets()) {
      Common.applyInsertRules(vs, tank);
    }
  }

  public Package export() {
    List<FshValueSet> valueSets = tank.getAllValueSets();
    for (FshValueSet valueSet : valueSets) {
      try {
        exportValueSet(valueSet);
      } catch (RuntimeException e) {
        ExporterLog.errorWithStack(e, valueSet.sourceInfo);
      }
    }
    if (!valueSets.isEmpty()) {
      logger.info("Converted " + valueSets.size() + " FHIR ValueSets.");
    }
    return pkg;
  }

  /** Returns null (TS undefined) when a value set with the same name was already exported. */
  public ValueSet exportValueSet(FshValueSet fshDefinition) {
    for (ValueSet existing : pkg.valueSets) {
      if (Objects.equals(existing.get("name"), fshDefinition.name)) {
        return null;
      }
    }
    ValueSet vs = new ValueSet();
    StructureDefinition valueSetSD = vs.getOwnStructureDefinition(fisher);
    setMetadata(vs, fshDefinition);
    List<CaretValueRule> conceptCaretRules = new ArrayList<>();
    List<CaretValueRule> otherCaretRules = new ArrayList<>();
    List<ValueSetComponentRule> componentRules = new ArrayList<>();
    for (Rule rule : fshDefinition.rules) {
      if (rule instanceof CaretValueRule caretRule) {
        (caretRule.pathArray.isEmpty() ? otherCaretRules : conceptCaretRules).add(caretRule);
      } else if (rule instanceof ValueSetComponentRule componentRule) {
        componentRules.add(componentRule);
      }
    }
    setCaretRules(vs, otherCaretRules, valueSetSD);
    setCompose(vs, componentRules);
    conceptCaretRules.forEach(rule -> rule.isCodeCaretRule = true);
    setConceptCaretRules(vs, conceptCaretRules, valueSetSD);
    if (vs.get("compose") instanceof JsObject compose && list(compose, "include").isEmpty()) {
      throw new ValueSetComposeError(fshDefinition.name);
    }

    // check for another value set with the same id
    // see https://www.hl7.org/fhir/resource.html#id
    for (ValueSet existing : pkg.valueSets) {
      if (Objects.equals(vs.get("id"), existing.get("id"))) {
        logger.error("Multiple value sets with id " + str(vs.get("id"))
            + ". Each value set must have a unique id.", fshDefinition.sourceInfo);
        break;
      }
    }

    Common.cleanResource(vs, prop -> prop.equals("_sliceName") || prop.equals("_primitive"));
    Common.checkForMultipleChoice(fshDefinition, vs, valueSetSD);
    pkg.valueSets.add(vs);
    pkg.fshMap.put(vs.getFileName(), new Package.FshMapEntry(fshDefinition.sourceInfo, fshDefinition.name,
        "ValueSet"));
    return vs;
  }
}
