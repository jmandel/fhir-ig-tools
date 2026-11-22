package org.hl7.fhir.sushi.fhirtypes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.BindingStrengthError;
import org.hl7.fhir.sushi.errors.CannotResolvePathError;
import org.hl7.fhir.sushi.errors.CodedTypeNotFoundError;
import org.hl7.fhir.sushi.errors.ConstrainingCardinalityError;
import org.hl7.fhir.sushi.errors.DuplicateSliceError;
import org.hl7.fhir.sushi.errors.FixedToPatternError;
import org.hl7.fhir.sushi.errors.InvalidCanonicalUrlError;
import org.hl7.fhir.sushi.errors.InvalidCardinalityError;
import org.hl7.fhir.sushi.errors.InvalidChoiceTypeRulePathError;
import org.hl7.fhir.sushi.errors.InvalidFHIRIdError;
import org.hl7.fhir.sushi.errors.InvalidMappingError;
import org.hl7.fhir.sushi.errors.InvalidMustSupportError;
import org.hl7.fhir.sushi.errors.InvalidSumOfSliceMinsError;
import org.hl7.fhir.sushi.errors.InvalidTypeError;
import org.hl7.fhir.sushi.errors.InvalidUriError;
import org.hl7.fhir.sushi.errors.MismatchedBindingTypeError;
import org.hl7.fhir.sushi.errors.MismatchedTypeError;
import org.hl7.fhir.sushi.errors.MultipleStandardsStatusError;
import org.hl7.fhir.sushi.errors.NarrowingRootCardinalityError;
import org.hl7.fhir.sushi.errors.NoSingleTypeError;
import org.hl7.fhir.sushi.errors.NonAbstractParentOfSpecializationError;
import org.hl7.fhir.sushi.errors.SliceTypeRemovalError;
import org.hl7.fhir.sushi.errors.SlicingDefinitionError;
import org.hl7.fhir.sushi.errors.SlicingNotDefinedError;
import org.hl7.fhir.sushi.errors.TypeNotFoundError;
import org.hl7.fhir.sushi.errors.ValidationError;
import org.hl7.fhir.sushi.errors.ValueAlreadyAssignedError;
import org.hl7.fhir.sushi.errors.ValueConflictsWithClosedSlicingError;
import org.hl7.fhir.sushi.fshtypes.FshCanonical;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshQuantity;
import org.hl7.fhir.sushi.fshtypes.FshRatio;
import org.hl7.fhir.sushi.fshtypes.FshReference;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsNumber;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.js.JsonSerializable;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FishingUtils;
import org.hl7.fhir.sushi.utils.Metadata;
import org.hl7.fhir.sushi.utils.ValidUrl;
import org.hl7.fhir.sushi.xhtml.HtmlMinifier;
import org.hl7.fhir.sushi.xhtml.Sax;

import static org.hl7.fhir.sushi.utils.FshLogger.logger;

// port of sushi/src/fhirtypes/ElementDefinition.ts (SUSHI 3.21.1)
/**
 * SUSHI's ElementDefinition: the FHIR properties as JS own properties (an insertion-ordered {@link JsObject}, in TS
 * assignment order, which decides e.g. which {@code fixed*} key {@code Object.keys} finds first) plus the tree/diff
 * bookkeeping. JSON output follows {@link #PROPS} (with {@code _x} right after {@code x}); {@code [x]} entries resolve
 * to the first own key matching {@code ^prefix[A-Z]}.
 *
 * <p>Conventions: a stored {@link Js#UNDEFINED} is a key TS assigned undefined (it counts for {@code Object.keys} and
 * lodash isEqual, and is skipped on output); typed getters return null for it. {@code id} is a TS accessor (stored in
 * {@code _privateId}, setting it recomputes {@code path}); it is not an own key. Typed setters translate TS
 * {@code this.x = v}: a Java null stores undefined. {@link #setProperty} stores Java null as JS null.
 *
 * <p>Deviation (no observable effect on output): {@link #clone} does not copy the cached own StructureDefinition (TS
 * deep-clones it); the clone fishes it afresh on first use.
 */
public class ElementDefinition implements PropertyHolder, JsonSerializable, Js.DeepCloneable {
  // ED extensions that should not be inherited by derived profiles
  // See: https://github.com/hapifhir/org.hl7.fhir.core/blob/master/org.hl7.fhir.r5/src/main/java/org/hl7/fhir/r5/conformance/profile/ProfileUtilities.java
  static final List<String> UNINHERITED_ED_EXTENSIONS = List.of(
      "http://hl7.org/fhir/tools/StructureDefinition/binding-definition",
      "http://hl7.org/fhir/tools/StructureDefinition/no-binding",
      "http://hl7.org/fhir/StructureDefinition/elementdefinition-isCommonBinding",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-category",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-implements",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-explicit-type-name",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-security-category",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-wg",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-normative-version",
      "http://hl7.org/fhir/tools/StructureDefinition/obligation-profile",
      "http://hl7.org/fhir/StructureDefinition/obligation-profile",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status-reason",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-summary");

  static final String PROFILE_ELEMENT_EXTENSION =
      "http://hl7.org/fhir/StructureDefinition/elementdefinition-profile-element";

  static final String STANDARDS_STATUS_EXTENSION =
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status";

  /** The list of ElementDefinition properties used when importing/exporting FHIR JSON. */
  public static final List<String> PROPS = List.of("id", "extension", "modifierExtension", "path", "representation",
      "sliceName", "sliceIsConstraining", "label", "code", "slicing", "short", "definition", "comment",
      "requirements", "alias", "min", "max", "base", "contentReference", "type", "defaultValue[x]",
      "meaningWhenMissing", "orderMeaning", "fixed[x]", "pattern[x]", "example", "minValue[x]", "maxValue[x]",
      "maxLength", "condition", "constraint", "mustSupport", "isModifier", "isModifierReason", "isSummary",
      "binding", "mapping");

  public static final List<String> PROPS_AND_UNDERPROPS;

  static {
    List<String> l = new ArrayList<>();
    for (String p : PROPS) {
      l.add(p);
      l.add("_" + p);
    }
    PROPS_AND_UNDERPROPS = List.copyOf(l);
  }

  /**
   * These list properties are considered to be additive in the differential. Thus, the differential should contain
   * only new entries in these lists. See http://hl7.org/fhir/elementdefinition.html#interpretation.
   */
  public static final List<String> ADDITIVE_PROPS = List.of("mapping", "constraint");

  /**
   * These list properties are replaced in child profiles. If they are modified in a profile, the snapshot should
   * contain only entries in that profile, and not in the parent profile. Each property is given as a list of path
   * parts.
   */
  static final List<List<String>> REPLACEMENT_PROPS = List.of(List.of("type", "aggregation"));

  static final List<String> ALLOWED_SLICING_RULES = List.of("closed", "open", "openAtEnd");
  static final List<String> ALLOWED_DISCRIMINATOR_TYPES = List.of("value", "exists", "pattern", "type", "profile");
  // R5 introduced the 'position' discriminator
  static final List<String> ALLOWED_DISCRIMINATOR_TYPES_R5 =
      List.of("value", "exists", "pattern", "type", "profile", "position");

  private static final Map<String, Pattern> CHOICE_PATTERNS = new LinkedHashMap<>();

  static {
    for (String p : PROPS_AND_UNDERPROPS) {
      if (p.endsWith("[x]")) {
        CHOICE_PATTERNS.put(p, Pattern.compile(Pattern.quote(p.substring(0, p.length() - 3)) + "[A-Z].*"));
      }
    }
  }

  private String _privateId;
  private JsObject props = new JsObject();
  /** The StructureDefinition this element belongs to (TS structDef). */
  public StructureDefinition structDef;
  /** TS treeParent / treeChildren: caches maintained by StructureDefinition.addElement/addElementToTree. */
  public ElementDefinition treeParent;
  public List<ElementDefinition> treeChildren;
  private ElementDefinition _original;
  private StructureDefinition _edStructureDefinition;
  private List<List<String>> _replacementProps;

  /** Constructs a new ElementDefinition with the given ID (default ''). */
  public ElementDefinition() {
    this("");
  }

  public ElementDefinition(String id) {
    setId(id);
    _replacementProps = new ArrayList<>();
    for (List<String> rp : REPLACEMENT_PROPS) _replacementProps.add(new ArrayList<>(rp));
  }

  private ElementDefinition(boolean blank) {}

  public String getId() {
    return _privateId;
  }

  /**
   * Sets the id of the ElementDefinition and updates the path accordingly. NOTE: This does not automatically update
   * child ids/paths. That is currently up to the library user.
   */
  public void setId(String id) {
    _privateId = id;
    // After setting the id, we should re-set the path, which is based on the id but with all
    // slice names removed (e.g., Observation.component:abc.code --> Observation.component.code)
    props.put("path", id == null ? "undefined" : SLICE_IN_ID.matcher(id).replaceAll("$1"));
  }

  private static final Pattern SLICE_IN_ID = Pattern.compile("(\\.[^.:]+):[^.]+");

  // ================================================================================================ PropertyHolder

  @Override public Object getProperty(String key) {
    if ("id".equals(key)) return _privateId;
    return nn(props.get(key));
  }

  @Override public boolean hasProperty(String key) {
    if ("id".equals(key)) return true;
    return props.containsKey(key) && props.get(key) != Js.UNDEFINED;
  }

  /** TS {@code ed[key] = value} ({@code "id"} goes through the id setter). Java null is JS null. */
  @Override public void setProperty(String key, Object value) {
    if ("id".equals(key)) {
      setId((String) nn(value));
      return;
    }
    props.put(key, value);
  }

  @Override public void deleteProperty(String key) {
    if ("id".equals(key)) return; // delete of a prototype accessor: no effect
    props.remove(key);
  }

  @Override public List<String> propertyKeys() {
    return Js.ownKeys(props);
  }

  /** The raw own properties (values may be {@link Js#UNDEFINED}). */
  public JsObject rawProperties() {
    return props;
  }

  /** TS {@code ed[key]} with JS undefined kept as {@link Js#UNDEFINED} (absent keys too). */
  Object raw(String key) {
    if ("id".equals(key)) return _privateId == null ? Js.UNDEFINED : _privateId;
    return props.containsKey(key) ? props.get(key) : Js.UNDEFINED;
  }

  private Object g(String key) {
    return nn(props.get(key));
  }

  private void set(String key, Object value) {
    props.put(key, value == null ? Js.UNDEFINED : value);
  }

  static Object nn(Object v) {
    return v == Js.UNDEFINED ? null : v;
  }

  // ================================================================================================ typed accessors

  public String getPath() {
    return (String) g("path");
  }

  public void setPath(String v) {
    set("path", v);
  }

  @SuppressWarnings("unchecked")
  public List<Object> getExtension() {
    return (List<Object>) g("extension");
  }

  public void setExtension(List<Object> v) {
    set("extension", v);
  }

  @SuppressWarnings("unchecked")
  public List<Object> getModifierExtension() {
    return (List<Object>) g("modifierExtension");
  }

  public void setModifierExtension(List<Object> v) {
    set("modifierExtension", v);
  }

  public String getSliceName() {
    return (String) g("sliceName");
  }

  public void setSliceName(String v) {
    set("sliceName", v);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getSlicing() {
    return (Map<String, Object>) g("slicing");
  }

  public void setSlicing(Map<String, Object> v) {
    set("slicing", v);
  }

  public String getShort() {
    return (String) g("short");
  }

  public void setShort(String v) {
    set("short", v);
  }

  public String getDefinition() {
    return (String) g("definition");
  }

  public void setDefinition(String v) {
    set("definition", v);
  }

  public String getComment() {
    return (String) g("comment");
  }

  public void setComment(String v) {
    set("comment", v);
  }

  /** JS number (null = undefined). */
  public Double getMin() {
    Object v = g("min");
    return v == null ? null : ((Number) v).doubleValue();
  }

  public void setMin(Double v) {
    set("min", v);
  }

  public String getMax() {
    return (String) g("max");
  }

  public void setMax(String v) {
    set("max", v);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getBase() {
    return (Map<String, Object>) g("base");
  }

  public void setBase(Map<String, Object> v) {
    set("base", v);
  }

  public String getContentReference() {
    return (String) g("contentReference");
  }

  public void setContentReference(String v) {
    set("contentReference", v);
  }

  @SuppressWarnings("unchecked")
  public List<ElementDefinitionType> getType() {
    return (List<ElementDefinitionType>) g("type");
  }

  public void setType(List<ElementDefinitionType> v) {
    set("type", v);
  }

  @SuppressWarnings("unchecked")
  public List<Object> getConstraint() {
    return (List<Object>) g("constraint");
  }

  public void setConstraint(List<Object> v) {
    set("constraint", v);
  }

  public Boolean getMustSupport() {
    return (Boolean) g("mustSupport");
  }

  public void setMustSupport(Boolean v) {
    set("mustSupport", v);
  }

  public Boolean getIsModifier() {
    return (Boolean) g("isModifier");
  }

  public void setIsModifier(Boolean v) {
    set("isModifier", v);
  }

  public String getIsModifierReason() {
    return (String) g("isModifierReason");
  }

  public void setIsModifierReason(String v) {
    set("isModifierReason", v);
  }

  public Boolean getIsSummary() {
    return (Boolean) g("isSummary");
  }

  public void setIsSummary(Boolean v) {
    set("isSummary", v);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getBinding() {
    return (Map<String, Object>) g("binding");
  }

  public void setBinding(Map<String, Object> v) {
    set("binding", v);
  }

  @SuppressWarnings("unchecked")
  public List<Object> getMapping() {
    return (List<Object>) g("mapping");
  }

  public void setMapping(List<Object> v) {
    set("mapping", v);
  }

  /** The stored "original" (TS private _original; exposed for StructureDefinition and tests). */
  public ElementDefinition getOriginal() {
    return _original;
  }

  // ================================================================================================ validation

  public List<ValidationError> validate() {
    List<ValidationError> errors = new ArrayList<>();
    Map<String, Object> slicing = getSlicing();
    if (Js.truthy(slicing)) {
      if (Js.truthy(getSliceName())) {
        errors.add(new ValidationError(
            "An element with a slice name should not define its own slicing. Instead, append additional discriminators to the original slicing on the base element.",
            "", "warn"));
      }
      errors.addAll(validateSlicing(slicing));
    }
    return errors;
  }

  private ValidationError validateRequired(Object value, String fshPath) {
    if (!Js.truthy(value)) {
      return new ValidationError("Missing required value", fshPath);
    }
    return null;
  }

  private ValidationError validateIncludes(Object value, List<String> allowedValues, String fshPath) {
    if (Js.truthy(value) && !allowedValues.contains(value)) {
      List<String> hashed = new ArrayList<>();
      for (String v : allowedValues) hashed.add("#" + v);
      return new ValidationError("Invalid value: #" + Js.toStr(value)
          + ". Value must be selected from one of the following: " + String.join(", ", hashed), fshPath);
    }
    return null;
  }

  public boolean isArrayOrChoice() {
    String max = getMax();
    Map<String, Object> base = getBase();
    Object baseMax = nn(base.get("max"));
    return "*".equals(max) || parseInt(max) > 1 || "*".equals(baseMax)
        || parseInt(baseMax == null ? null : Js.toStr(baseMax)) > 1 || getId().endsWith("[x]");
  }

  public boolean isPrimitive(Fishable fisher) {
    List<ElementDefinitionType> type = getType();
    if (type == null && getContentReference() != null) {
      ElementDefinition referencedElement = structDef.findElement(getContentReferenceId());
      if (referencedElement != null && referencedElement.getType() != null
          && referencedElement.getType().size() == 1) {
        Object typeSD = fisher.fishForFHIR(referencedElement.getType().get(0).getCode(), FishableType.Type);
        return typeSD instanceof Map<?, ?> m && "primitive-type".equals(m.get("kind"));
      }
    }
    if (type != null && type.size() == 1) {
      Object typeSD = fisher.fishForFHIR(type.get(0).getCode(), FishableType.Type);
      return typeSD instanceof Map<?, ?> m && "primitive-type".equals(m.get("kind"));
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private List<ValidationError> validateSlicing(Map<String, Object> slicing) {
    List<ValidationError> validationErrors = new ArrayList<>();
    validationErrors.add(validateRequired(nn(slicing.get("rules")), "slicing.rules"));
    validationErrors.add(validateIncludes(nn(slicing.get("rules")), ALLOWED_SLICING_RULES, "slicing.rules"));
    boolean isR4 = structDef.getFhirVersion().startsWith("4.");
    Object discriminator = nn(slicing.get("discriminator"));
    if (discriminator instanceof List<?> ds) {
      for (int i = 0; i < ds.size(); i++) {
        Map<String, Object> d = (Map<String, Object>) ds.get(i);
        String discriminatorPath = "slicing.discriminator[" + i + "]";
        Object dType = nn(d.get("type"));
        validationErrors.add(validateRequired(dType, discriminatorPath + ".type"));
        if (isR4) {
          validationErrors.add(validateIncludes(dType, ALLOWED_DISCRIMINATOR_TYPES, discriminatorPath + ".type"));
        } else {
          validationErrors.add(validateIncludes(dType, ALLOWED_DISCRIMINATOR_TYPES_R5, discriminatorPath + ".type"));
          if ("position".equals(dType) && !Boolean.TRUE.equals(slicing.get("ordered"))) {
            validationErrors.add(new ValidationError(
                "Slicing ordering must be true when a position discriminator is used.", "slicing.ordered"));
          }
        }
        validationErrors.add(validateRequired(nn(d.get("path")), discriminatorPath + ".path"));
      }
    }
    validationErrors.removeIf(Objects::isNull);
    return validationErrors;
  }

  public String getPathWithoutBase() {
    return getPath().substring(structDef.getPathType().length() + 1);
  }

  /** Get the StructureDefinition for ElementDefinition. */
  public StructureDefinition getOwnStructureDefinition(Fishable fisher) {
    if (_edStructureDefinition == null) {
      _edStructureDefinition =
          StructureDefinition.fromJSON(fisher.fishForFHIR("ElementDefinition", FishableType.Type));
    }
    return _edStructureDefinition;
  }

  /** Returns the Types that have the given code(s). */
  public List<ElementDefinitionType> findTypesByCode(String... codes) {
    List<ElementDefinitionType> type = getType();
    List<ElementDefinitionType> out = new ArrayList<>();
    if (type == null) {
      return out;
    }
    List<String> codeList = List.of(codes);
    for (ElementDefinitionType t : type) if (codeList.contains(t.getCode())) out.add(t);
    return out;
  }

  /**
   * Creates a new element with an id/path indicating it is a child of the current element. Defaults to '$UNKNOWN'
   * if no name is passed in. NOTE: This function does not automatically add the child element to the
   * StructureDefinition.
   */
  public ElementDefinition newChildElement() {
    return newChildElement("$UNKNOWN");
  }

  public ElementDefinition newChildElement(String name) {
    ElementDefinition el = new ElementDefinition(getId() + "." + (name == null ? "$UNKNOWN" : name));
    el.structDef = structDef;
    return el;
  }

  // ================================================================================================ diff

  /**
   * Captures the current state as the "original", so any further changes made would be captured in the generated
   * differential. The structDef reference isn't used in the differential, so it can be removed.
   */
  public void captureOriginal() {
    _original = clone();
    _original.structDef = null;
  }

  /** Clears the stored "original", so every property is considered new and reflected in the differential. */
  public void clearOriginal() {
    _original = null;
  }

  public void clearOriginalProperty(List<PathPart> pathParts) {
    Object currentOriginalElement = _original;
    Object currentActualElement = this;
    List<PathPart> clearPath = calculateClearPath(pathParts);
    for (int i = 0; i < clearPath.size(); i++) {
      PathPart pathPart = clearPath.get(i);
      if (i < clearPath.size() - 1) {
        String key = pathPart.isPrimitive() ? "_" + pathPart.base : pathPart.base;
        currentOriginalElement = propOf(currentOriginalElement, key);
        currentActualElement = propOf(currentActualElement, key);
        Integer currentIndex = Common.getArrayIndex(pathPart);
        if (currentIndex != null) {
          if (currentOriginalElement instanceof List<?> l) {
            currentOriginalElement = currentIndex < l.size() ? l.get(currentIndex) : null;
          }
          if (currentActualElement instanceof List<?> l) {
            currentActualElement = currentIndex < l.size() ? l.get(currentIndex) : null;
          }
        }
      } else {
        if (propOf(currentOriginalElement, pathPart.base) != null) {
          setUndefined(currentOriginalElement, pathPart.base);
          setUndefined(currentActualElement, pathPart.base);
        }
        if (pathPart.isPrimitive() && propOf(currentOriginalElement, "_" + pathPart.base) != null) {
          setUndefined(currentOriginalElement, "_" + pathPart.base);
          setUndefined(currentActualElement, "_" + pathPart.base);
        }
      }
    }
  }

  private List<PathPart> calculateClearPath(List<PathPart> pathParts) {
    int replacementIndex = -1;
    for (int r = 0; r < _replacementProps.size(); r++) {
      List<String> replacementPath = _replacementProps.get(r);
      boolean every = replacementPath.size() <= pathParts.size();
      for (int index = 0; every && index < replacementPath.size(); index++) {
        every = replacementPath.get(index).equals(pathParts.get(index).base);
      }
      if (every) {
        replacementIndex = r;
        break;
      }
    }
    if (replacementIndex >= 0) {
      // Build the clearPath from the actual pathParts, since we want to know about primitive types.
      List<PathPart> clearPath =
          new ArrayList<>(pathParts.subList(0, _replacementProps.get(replacementIndex).size()));
      // each replacement property only has to get cleared once, so remove it from the list.
      _replacementProps.remove(replacementIndex);
      return clearPath;
    }
    return new ArrayList<>();
  }

  /** The own key {@code Object.keys(obj).find(re)} picks for a {@code [x]} prop, else null. */
  private String resolveChoice(String prop) {
    Pattern re = CHOICE_PATTERNS.get(prop);
    for (String k : propertyKeys()) {
      if (re.matcher(k).matches()) return k;
    }
    return null;
  }

  private static String resolveChoiceIn(Map<String, ?> json, String prop) {
    Pattern re = CHOICE_PATTERNS.get(prop);
    for (String k : Js.ownKeys(json)) {
      if (re.matcher(k).matches()) return k;
    }
    return null;
  }

  /** Determines if the state of the current element differs from the stored "original". */
  public boolean hasDiff() {
    ElementDefinition original = _original != null ? _original : new ElementDefinition();
    for (String p : PROPS_AND_UNDERPROPS) {
      String prop = p;
      if (prop.endsWith("[x]")) {
        prop = resolveChoice(p);
        if (prop == null) {
          prop = original.resolveChoice(p);
        }
      }
      if (prop != null && !Js.isEqual(raw(prop), original.raw(prop))) {
        return true;
      }
    }
    // When a slice or a sliced element has children that changed, we must treat the slice or sliced element
    // as if it differs from the original. The IG Publisher requires slices or sliced elements with changed
    // children to be in the differential, or the snapshot is incorrectly generated
    if (Js.truthy(getSliceName()) || !getSlices().isEmpty()) {
      for (ElementDefinition c : children()) {
        if (c.hasDiff()) return true;
      }
    }
    return false;
  }

  /**
   * Calculates the differential based on changes in data from the stored "original" state and returns the
   * differential as a new ElementDefinition containing only the id, path, and changed data.
   */
  public ElementDefinition calculateDiff() {
    ElementDefinition original = _original != null ? _original : new ElementDefinition();
    ElementDefinition diff = new ElementDefinition(getId());
    diff.structDef = structDef;
    for (String p : PROPS_AND_UNDERPROPS) {
      String prop = p;
      if (prop.endsWith("[x]")) {
        prop = resolveChoice(p);
        if (prop == null) {
          prop = original.resolveChoice(p);
        }
      }
      if (prop != null && !Js.isEqual(raw(prop), original.raw(prop))) {
        if (ADDITIVE_PROPS.contains(prop)) {
          List<Object> d = differenceWith(raw(prop), original.raw(prop));
          diff.props.put(prop, d);
          if (Js.isEmpty(d)) {
            diff.props.remove(prop);
          }
        } else {
          diff.putRaw(prop, Js.cloneDeep(raw(prop)));
        }
      } else if ("type".equals(prop) && Js.truthy(getSliceName()) && getPath().endsWith("[x]")) {
        // the IG publisher always requires that the type attribute is present on a slice of a choice element,
        // even if this slice's type is equal to the choice element's type.
        diff.putRaw(prop, Js.cloneDeep(raw(prop)));
      }
    }
    // If the original has a sliceName, the diff needs to have a sliceName, so use the original.
    if (Js.truthy(original.getSliceName()) && diff.getSliceName() == null) {
      diff.setSliceName(original.getSliceName());
    }
    return diff;
  }

  /** TS {@code this[prop] = v} keeping undefined as undefined ({@code id} goes through the setter). */
  private void putRaw(String prop, Object v) {
    if ("id".equals(prop)) {
      setId((String) nn(v));
    } else {
      props.put(prop, v);
    }
  }

  /** lodash differenceWith(array, values, isEqual): elements of array (if an array) not isEqual to any value. */
  private static List<Object> differenceWith(Object array, Object values) {
    List<Object> out = new ArrayList<>();
    if (!(array instanceof List<?> a)) return out;
    List<?> vs = values instanceof List<?> v ? v : List.of();
    outer:
    for (Object x : a) {
      for (Object y : vs) {
        if (Js.isEqual(x, y)) continue outer;
      }
      out.add(x);
    }
    return out;
  }

  // ================================================================================================ add element

  /**
   * Apply the AddElementRule to this new element using the appropriate methods for specific rules for the
   * AddElementRule's implied rules (i.e., cardinality, type constraints, and flags).
   */
  @SuppressWarnings("unchecked")
  public void applyAddElementRule(AddElementRule rule, Fishable fisher) {
    // Verify the rule path. When adding a new element, a parent will always be defined,
    // even it the parent element is the root element. If parent is not defined, there
    // is something wrong with the rule.path value.
    if (parent() == null) {
      throw new CannotResolvePathError(rule.path);
    }

    // Add the base attribute
    JsObject base = new JsObject();
    base.put("path", structDef.getPathType() + "." + rule.path);
    base.put("min", rule.min == null ? Js.UNDEFINED : rule.min);
    base.put("max", rule.max == null ? Js.UNDEFINED : rule.max);
    setBase(base);

    // Add the root element's constraints to this element
    Map<String, Object> elementSD = (Map<String, Object>) fisher.fishForFHIR("Element", FishableType.Type);
    // The root element's constraint does not define the source property
    // because it is the source. So, we need to add the missing source property.
    Map<String, Object> snapshot = (Map<String, Object>) elementSD.get("snapshot");
    Map<String, Object> rootEl = (Map<String, Object>) ((List<Object>) snapshot.get("element")).get(0);
    List<Object> constraints = (List<Object>) Js.cloneDeep(rootEl.get("constraint"));
    for (Object c : constraints) {
      ((Map<String, Object>) c).put("source", elementSD.get("url"));
    }
    setConstraint(constraints);

    // Capture the current state as the original element definition.
    // All changes after this will be a part of the differential.
    captureOriginal();

    if (!rule.types.isEmpty()) {
      // The constrainType() method applies a type constraint to an existing
      // ElementDefinition.type. Since this is a new ElementDefinition, it
      // does not yet have a 'type', so we need to assign an "initial" value
      // that the constrainType() method can process.
      setType(initializeElementType(rule, fisher));
      String target = structDef.getReferenceOrCanonicalName(rule.path, this);
      constrainType(rule, fisher, target);
    } else {
      // An element without a type has a contentReference instead
      setContentReference(rule.contentReference);
    }

    constrainCardinality(rule.min, rule.max);

    applyFlags(rule.mustSupport, rule.summary, rule.modifier, rule.trialUse, rule.normative, rule.draft);

    // Since 'short' and 'definition' are plain string values, directly assign the values.
    if (!Js.isEmpty(rule.short_)) {
      setShort(rule.short_);
    }
    if (Js.isEmpty(rule.definition)) {
      if (!Js.isEmpty(rule.short_)) {
        // According to sdf-3 in http://hl7.org/fhir/structuredefinition.html#invs,
        // definition should be provided. Default it here.
        setDefinition(rule.short_);
      }
    } else {
      setDefinition(rule.definition);
    }
  }

  /** Define and return the initial base ElementDefinition.type from the AddElementRule. */
  private List<ElementDefinitionType> initializeElementType(AddElementRule rule, Fishable fisher) {
    if (rule.types.size() > 1 && !rule.path.endsWith("[x]")) {
      // Reference/Canonical/CodeableReference data type with multiple targets is not considered a choice data type.
      if (!rule.types.stream().allMatch(t -> Js.truthy(t.isReference))
          && !rule.types.stream().allMatch(t -> Js.truthy(t.isCanonical))
          && !rule.types.stream().allMatch(t -> Js.truthy(t.isCodeableReference))) {
        throw new InvalidChoiceTypeRulePathError(rule);
      }
    }

    int refTypeCnt = 0;
    int canTypeCnt = 0;
    int codeableRefTypeCnt = 0;

    List<ElementDefinitionType> initialTypes = new ArrayList<>();
    for (OnlyRuleType t : rule.types) {
      if (Js.truthy(t.isReference)) {
        refTypeCnt++;
      } else if (Js.truthy(t.isCanonical)) {
        canTypeCnt++;
      } else if (Js.truthy(t.isCodeableReference)) {
        codeableRefTypeCnt++;
      } else {
        Metadata metadata = fisher.fishForMetadata(t.type);
        // TS: metadata.sdType on undefined (type not found) -> V8 TypeError (corpus WHO smart-trust, KD-3)
        if (metadata == null) throw jsTypeError("Cannot read properties of undefined (reading 'sdType')");
        initialTypes.add(new ElementDefinitionType(metadata.sdType));
      }
    }

    List<ElementDefinitionType> finalTypes = uniqWith(initialTypes);
    if (refTypeCnt > 0) {
      // We only need to capture a single Reference type. The targetProfiles attribute
      // will contain the URLs for each reference.
      finalTypes.add(new ElementDefinitionType("Reference"));
    }
    if (canTypeCnt > 0) {
      // We only need to capture a single canonical type. The targetProfiles attribute
      // will contain the URLs for each canonical.
      finalTypes.add(new ElementDefinitionType("canonical"));
    }
    if (codeableRefTypeCnt > 0) {
      // We only need to capture a single CodeableReference type. The targetProfiles attribute
      // will contain the URLs for each CodeableReference reference.
      finalTypes.add(new ElementDefinitionType("CodeableReference"));
    }

    int refCheckCnt = refTypeCnt > 0 ? refTypeCnt - 1 : 0;
    int canCheckCnt = canTypeCnt > 0 ? canTypeCnt - 1 : 0;
    int codableRefCheckCnt = codeableRefTypeCnt > 0 ? codeableRefTypeCnt - 1 : 0;
    if (rule.types.size() != finalTypes.size() + refCheckCnt + canCheckCnt + codableRefCheckCnt) {
      logger.warn(rule.path + " includes duplicate types. Duplicates have been ignored.", rule.sourceInfo);
    }

    return finalTypes;
  }

  /** lodash uniqWith(list, isEqual): first occurrences, in order. */
  private static <T> List<T> uniqWith(List<T> list) {
    List<T> out = new ArrayList<>();
    outer:
    for (T x : list) {
      for (T y : out) {
        if (Js.isEqual(x, y) || Objects.equals(x, y)) continue outer;
      }
      out.add(x);
    }
    return out;
  }

  /**
   * Apply invariant to the Element.constraint.
   *
   * @return the index of the constraint that was applied
   */
  public int applyConstraint(Invariant invariant) {
    return applyConstraint(invariant, null);
  }

  @SuppressWarnings("unchecked")
  public int applyConstraint(Invariant invariant, String source) {
    JsObject constraint = new JsObject();
    if (Js.truthy(invariant.name)) constraint.put("key", invariant.name);
    if (invariant.severity != null) {
      constraint.put("severity", invariant.severity.code == null ? Js.UNDEFINED : invariant.severity.code);
    }
    if (Js.truthy(invariant.description)) constraint.put("human", invariant.description);
    if (Js.truthy(invariant.expression)) constraint.put("expression", invariant.expression);
    if (Js.truthy(invariant.xpath)) constraint.put("xpath", invariant.xpath);
    if (Js.truthy(source)) constraint.put("source", source);
    List<Object> existing = getConstraint();
    if (Js.truthy(existing)) {
      existing.add(constraint);
    } else {
      List<Object> l = new ArrayList<>();
      l.add(constraint);
      setConstraint(l);
    }
    // return the index of the constraint that was applied
    return getConstraint().size() - 1;
  }

  /** Sets an instance property of an ED (caret rule) if possible. */
  public void setInstancePropertyByPath(String path, Object value, Fishable fisher) {
    Common.setPropertyOnDefinitionInstance(this, path, value, fisher);
  }

  // ================================================================================================ slices

  public List<ElementDefinition> getSlices() {
    ElementDefinition parent = parent();
    List<ElementDefinition> candidates = parent != null ? parent.children(true) : structDef.elements;
    String prefix = getId() + (Js.truthy(getSliceName()) ? "/" : ":");
    List<ElementDefinition> out = new ArrayList<>();
    for (ElementDefinition e : candidates) {
      if (!e.getId().equals(getId()) && Objects.equals(e.getPath(), getPath()) && e.getId().startsWith(prefix)) {
        out.add(e);
      }
    }
    return out;
  }

  /**
   * Returns an array of slices that will be pre-loaded. A slice is pre-loaded if it has a min of 1 and contains a
   * fixed or pattern value on itself or its descendents.
   */
  public List<ElementDefinition> getPreloadedSlices() {
    List<ElementDefinition> out = new ArrayList<>();
    for (ElementDefinition slice : getSlices()) {
      if (gt(slice.getMin(), 0) && (hasAssignedKey(slice)
          || slice.getAssignableDescendents().stream().anyMatch(ElementDefinition::hasAssignedKey))) {
        out.add(slice);
      }
    }
    return out;
  }

  private static boolean hasAssignedKey(ElementDefinition e) {
    return e.assignedKey() != null;
  }

  /** {@code Object.keys(ed).find(k => k.startsWith('fixed') || k.startsWith('pattern'))}. */
  String assignedKey() {
    for (String k : propertyKeys()) {
      if (k.startsWith("fixed") || k.startsWith("pattern")) return k;
    }
    return null;
  }

  /** Determines if an array index references a slice that will be preloaded. */
  public boolean isPreloadedSlice(int sliceIndex) {
    return sliceIndex <= getPreloadedSlices().size() - 1;
  }

  // ================================================================================================ cardinality

  /** Constrains the cardinality; NaN min / "" max keep the current value (TS: isNaN(min), max === ''). */
  public void constrainCardinality(double min, String max) {
    constrainCardinality(Double.isNaN(min) ? null : Double.valueOf(min), max);
  }

  /**
   * Constrains the cardinality of this element. Cardinality constraints can only narrow cardinality. Attempts to
   * constrain to a wider cardinality will throw. A null min (JS undefined or NaN) or "" max keeps the current value;
   * a null max is JS undefined.
   *
   * @throws InvalidCardinalityError when min > max
   * @throws ConstrainingCardinalityError when new cardinality is wider than existing cardinality
   * @throws InvalidSumOfSliceMinsError when the mins of slice elements > max of sliced element
   * @throws NarrowingRootCardinalityError when the new cardinality on an element is narrower than the cardinality
   *     on a connected element
   */
  public void constrainCardinality(Double min, String max) {
    // If only one side of the cardinality is set by the rule, use element's current cardinality
    if (min == null || min.isNaN()) min = getMin();
    if ("".equals(max)) max = getMax();

    boolean isUnbounded = "*".equals(max);
    double maxInt = !isUnbounded ? parseInt(max) : Double.NaN;

    // Check to ensure it is valid (min <= max)
    if (!isUnbounded && d(min) > maxInt) {
      throw new InvalidCardinalityError(min, max);
    }

    // Check to ensure min >= existing min
    if (getMin() != null && d(min) < getMin()) {
      throw new ConstrainingCardinalityError(getMin(), getMax(), min, max);
    }

    // Check to ensure max <= existing max
    if (getMax() != null && !"*".equals(getMax()) && (maxInt > parseInt(getMax()) || isUnbounded)) {
      throw new ConstrainingCardinalityError(getMin(), getMax(), min, max);
    }

    // Sliced elements and slices have special card rules described here:
    // http://www.hl7.org/fhiR/profiling.html#slice-cardinality
    // If element is slice definition
    if (Js.truthy(getSlicing()) && !isUnbounded) {
      // Check that new max >= sum of mins of children
      checkSumOfSliceMins(max, 0);
      // Check that new max >= every individual child max
      List<ElementDefinition> slices = getSlices();
      List<String> overMaxChildren = new ArrayList<>();
      for (ElementDefinition child : slices) {
        if ("*".equals(child.getMax()) || parseInt(child.getMax()) > maxInt) {
          child.setMax(max);
          overMaxChildren.add(child.getSliceName());
        }
      }
      if (!overMaxChildren.isEmpty()) {
        logger.warn("At least one slice of " + getId()
            + " has a max greater than the overall element max. The max of the following slice(s) has been reduced to match the max of "
            + getId() + ": " + joinJs(overMaxChildren, ","));
      }
    }

    List<ElementDefinition> connectedElements = new ArrayList<>();
    for (ElementDefinition ce : findConnectedElements()) {
      if (!(Objects.equals(ce.getPath(), getPath()) && ce.getId().startsWith(getId()))) connectedElements.add(ce);
    }
    // check to see if the card constraint would actually be a problem for the connected element
    // that is to say, if the new card is incompatible with the connected card
    // Filter out elements that are directly slices of this, since they may have min < this.min
    for (ElementDefinition ce : connectedElements) {
      // the cardinality is incompatible if:
      // the new min is greater than the connected element's max, or
      // the new max is less than the connected element's min
      if ((ce.getMax() != null && !"*".equals(ce.getMax()) && d(min) > parseInt(ce.getMax()))
          || (ce.getMin() != null && !isUnbounded && maxInt < ce.getMin())) {
        throw new NarrowingRootCardinalityError(getPath(), ce.getId(), min, max, ce.getMin(),
            ce.getMax() != null ? ce.getMax() : "*");
      }
    }

    // If element is a slice
    ElementDefinition slicedElement = slicedElement();
    if (slicedElement != null) {
      ElementDefinition parentSlice = findParentSlice();
      double newParentMin = d(min);
      for (ElementDefinition el : parent().children(true)) {
        if (this != el && slicedElement == el.slicedElement() && parentSlice == el.findParentSlice()) {
          newParentMin += d(el.getMin());
        }
      }
      // if this is a reslice, the parent element will also be a slice of the sliced element.
      // if this is not a reslice, the parent element is the sliced element.
      ElementDefinition parentElement = parentSlice != null ? parentSlice : slicedElement;
      // Check that parentElement max >= new sum of mins
      if (!"*".equals(parentElement.getMax()) && newParentMin > parseInt(parentElement.getMax())) {
        throw new InvalidSumOfSliceMinsError(newParentMin, parentElement.getMax(), parentElement.getId());
      }
      // If new sum of mins > parentElement min, increase parentElement min
      if (newParentMin > d(parentElement.getMin())) {
        parentElement.constrainCardinality(newParentMin, "");
      }
    }

    // apply the cardinality to connected elements, but don't try to widen cardinalities
    for (ElementDefinition ce : connectedElements) {
      double newMin = Math.max(d(min), d(ce.getMin()));
      String newMax = max;
      if (isUnbounded) {
        newMax = ce.getMax();
      } else if (!"*".equals(ce.getMax())) {
        newMax = JsNumber.toString(Math.min(maxInt, parseInt(ce.getMax())));
      }
      ce.constrainCardinality(newMin, newMax);
    }
    setMin(min);
    setMax(max);
  }

  /**
   * Tries to find all connected elements based on slicing: elements that have the same path, but are slicier (at or
   * inside slices of this element or of its ancestors).
   */
  public List<ElementDefinition> findConnectedElements() {
    return findConnectedElements("");
  }

  public List<ElementDefinition> findConnectedElements(String postPath) {
    List<ElementDefinition> connectedElements = new ArrayList<>();
    for (ElementDefinition slice : getSlices()) {
      if ("0".equals(slice.getMax())) continue;
      ElementDefinition e = structDef.findElement(slice.getId() + postPath);
      if (e != null) connectedElements.add(e);
    }
    ElementDefinition parent = parent();
    if (parent != null) {
      List<String> parts = Common.splitOnPathPeriods(getId());
      String parentPath = parts.get(parts.size() - 1);
      connectedElements.addAll(parent.findConnectedElements("." + parentPath + postPath));
    }
    return connectedElements;
  }

  public ElementDefinition findConnectedSliceElement() {
    return findConnectedSliceElement("");
  }

  public ElementDefinition findConnectedSliceElement(String postPath) {
    ElementDefinition slicingRoot = slicedElement();
    if (slicingRoot != null) {
      return structDef.findElement(slicingRoot.getId() + postPath);
    } else if (parent() != null) {
      String[] parts = getPath().split("\\.", -1);
      return parent().findConnectedSliceElement("." + parts[parts.length - 1] + postPath);
    }
    return null;
  }

  public ElementDefinition findParentSlice() {
    String sliceName = getSliceName();
    if (Js.truthy(sliceName)) {
      ElementDefinition slicedElement = slicedElement();
      String[] nameParts = sliceName.split("/", -1);
      List<String> parentNameParts = List.of(nameParts).subList(0, nameParts.length - 1);
      List<String> potentialParentNames = new ArrayList<>();
      for (int i = 0; i < parentNameParts.size(); i++) {
        potentialParentNames.add(String.join("/", parentNameParts.subList(0, i + 1)));
      }
      java.util.Collections.reverse(potentialParentNames);
      ElementDefinition parent = parent();
      List<ElementDefinition> elementsToSearch = parent != null ? parent.children(true) : structDef.elements;
      for (String parentName : potentialParentNames) {
        for (ElementDefinition el : elementsToSearch) {
          if (Objects.equals(el.getSliceName(), parentName) && el.slicedElement() == slicedElement) {
            return el;
          }
        }
      }
    }
    return null;
  }

  /**
   * Checks if the sum of slice mins exceeds the max of sliced element, and returns the sum if not.
   *
   * @throws InvalidSumOfSliceMinsError when the sum of mins of the slices exceeds max of sliced element
   */
  private double checkSumOfSliceMins(String newSlicedElementMax, double sliceMinIncrease) {
    double sumOfMins = sliceMinIncrease;
    for (ElementDefinition s : getSlices()) sumOfMins += d(s.getMin());
    if (!"*".equals(newSlicedElementMax) && sumOfMins > parseInt(newSlicedElementMax)) {
      throw new InvalidSumOfSliceMinsError(sumOfMins, newSlicedElementMax, getId());
    }
    return sumOfMins;
  }

  // ================================================================================================ types

  /** Info about how one rule type matched one of the element's types (TS interface ElementTypeMatchInfo). */
  private static final class ElementTypeMatchInfo {
    String code;
    Metadata metadata;
    String typeName;

    ElementTypeMatchInfo(Metadata metadata, String code, String typeName) {
      this.metadata = metadata;
      this.code = code;
      this.typeName = typeName;
    }
  }

  private static List<OnlyRuleType> ruleTypes(Rule rule) {
    if (rule instanceof OnlyRule o) return o.types;
    if (rule instanceof AddElementRule a) return a.types;
    throw new IllegalArgumentException("constrainType needs an OnlyRule or AddElementRule, got "
        + rule.constructorName());
  }

  public void constrainType(Rule rule, Fishable fisher) {
    constrainType(rule, fisher, null);
  }

  /**
   * Constrains the type of this element to the requested type(s) (an OnlyRule or AddElementRule). When this
   * element's type is a choice, this reduces the choice to only those types provided -- unless a target is provided,
   * in which case only that type is affected and other options in the choice are left unchanged.
   *
   * @throws TypeNotFoundError when a passed in type's definition cannot be found
   * @throws InvalidTypeError when a passed in type or the targetType doesn't match any existing types
   * @throws SliceTypeRemovalError when a rule would eliminate all types on a slice
   */
  public void constrainType(Rule rule, Fishable fisher, String target) {
    List<OnlyRuleType> ruleTypes = ruleTypes(rule);
    List<OnlyRuleType> types = uniqWith(ruleTypes);
    // Establish the target types (if applicable)
    ElementDefinitionType targetType = getTargetType(target, fisher);
    List<ElementDefinitionType> targetTypes = targetType != null ? List.of(targetType) : getType();

    // If the target type is a CodeableReference but the rule types were set via the Reference() keyword,
    // log a warning to use CodeableReference keyword
    if (targetTypes.stream().anyMatch(t -> "CodeableReference".equals(t.getCode()))
        && targetTypes.stream().noneMatch(t -> "Reference".equals(t.getCode()))
        && ruleTypes.stream().anyMatch(t -> Js.truthy(t.isReference))) {
      logger.warn("The CodeableReference() keyword should be used to constrain references of a CodeableReference",
          rule.sourceInfo);
    }

    // Check if a CodeableReference is attempting to constraint directly to the reference element
    List<ElementDefinitionType> myType = getType();
    if (myType.size() == 1 && "Reference".equals(myType.get(0).getCode()) && getPath().endsWith(".reference")
        && "CodeableReference".equals(firstTypeCode(parent()))) {
      logger.error(
          "Constraining references on a CodeableReference element's underlying .reference path is not allowed. Instead, constrain the references directly on the CodeableReference element.",
          rule.sourceInfo);
    }

    // Setup a map to store how each existing element type maps to the input types
    Map<String, List<ElementTypeMatchInfo>> typeMatches = new LinkedHashMap<>();
    for (ElementDefinitionType t : targetTypes) typeMatches.put(t.getCode(), new ArrayList<>());

    // Loop through the input types and associate them to the element types in the map
    for (OnlyRuleType type : types) {
      ElementTypeMatchInfo typeMatch = findTypeMatch(type, targetTypes, fisher);
      // if the type is Canonical, it may have a version. preserve it in the match's metadata.
      if ((Js.truthy(type.isCanonical) || Js.truthy(type.isReference) || Js.truthy(type.isCodeableReference))
          && type.type.indexOf('|') > -1) {
        typeMatch.metadata = copyMetadata(typeMatch.metadata);
        typeMatch.metadata.url = Js.toStr(nullToUndef(typeMatch.metadata.url)) + "|"
            + type.type.split("\\|", -1)[1];
      }
      typeMatches.get(typeMatch.code).add(typeMatch);
    }
    // check Reference and CodeableReference type matches for logical targetProfiles.
    // logical models should have an extension set to indicate that they can be used as reference targets.
    List<ElementTypeMatchInfo> logicalReferenceTargets = new ArrayList<>();
    if (typeMatches.containsKey("Reference")) {
      logicalReferenceTargets.addAll(typeMatches.get("Reference"));
    }
    if (typeMatches.containsKey("CodeableReference")) {
      logicalReferenceTargets.addAll(typeMatches.get("CodeableReference"));
    }
    List<ElementTypeMatchInfo> invalidReferenceTargets = new ArrayList<>();
    for (ElementTypeMatchInfo tm : logicalReferenceTargets) {
      if (Boolean.FALSE.equals(tm.metadata.canBeTarget)) invalidReferenceTargets.add(tm);
    }
    if (invalidReferenceTargets.size() > 1) {
      List<String> names = new ArrayList<>();
      for (ElementTypeMatchInfo info : invalidReferenceTargets) names.add(info.typeName);
      logger.warn("Referenced types " + joinJs(names, ", ")
          + " do not specify that they can be the targets of a reference.", rule.sourceInfo);
    } else if (invalidReferenceTargets.size() == 1) {
      logger.warn("Referenced type " + invalidReferenceTargets.get(0).typeName
          + " does not specify that it can be the target of a reference.", rule.sourceInfo);
    }

    // Loop through the existing element types building the new set of element types w/ constraints
    List<ElementDefinitionType> newTypes = new ArrayList<>();
    List<ElementDefinitionType> oldTypes = new ArrayList<>();
    for (ElementDefinitionType type : getType()) {
      // If the typeMatches map doesn't have the type code at all, this means that a target was
      // specified, and this element type wasn't the target.  In this case, we want to keep it.
      if (!typeMatches.containsKey(type.getCode())) {
        newTypes.add(type.cloneDeep());
        continue;
      }

      // Get the associated input type matches.  If no input types matched against it, then this
      // element type should be filtered out of the results, so just skip to the next one.
      List<ElementTypeMatchInfo> matches = typeMatches.get(type.getCode());
      if (matches.isEmpty()) {
        oldTypes.add(type);
        continue;
      }
      newTypes.addAll(applyTypeIntersection(type, targetType, matches, fisher));
    }

    // Loop through the new types and for each one, update any _profile and _targetProfile arrays
    // that need it. In short, if a single profile or targetProfile is unchanged, preserve its
    // corresponding _profile or _targetProfile entry. If a profile or targetProfile is changed,
    // and there is a _profile or _targetProfile array, make the corresponding entry null. If a
    // _profile or _targetProfile is added, and there is a _profile or _targetProfile array, add
    // a null entry for it.
    for (ElementDefinitionType newType : newTypes) {
      ElementDefinitionType originalType = null;
      for (ElementDefinitionType t : getType()) {
        if (Objects.equals(t.getCode(), newType.getCode())) {
          originalType = t;
          break;
        }
      }
      newType.deleteProperty("_profile");
      if (originalType != null && originalType.get_profile() != null && !originalType.get_profile().isEmpty()) {
        List<Object> np = mapParallel(newType.getProfile(), originalType.getProfile(), originalType.get_profile());
        newType.setProperty("_profile", np);
        if (np.isEmpty() || np.stream().allMatch(Objects::isNull)) {
          newType.deleteProperty("_profile");
        }
      }
      newType.deleteProperty("_targetProfile");
      if (originalType != null && originalType.get_targetProfile() != null
          && !originalType.get_targetProfile().isEmpty()) {
        List<Object> np = mapParallel(newType.getTargetProfile(), originalType.getTargetProfile(),
            originalType.get_targetProfile());
        newType.setProperty("_targetProfile", np);
        if (np.isEmpty() || np.stream().allMatch(Objects::isNull)) {
          newType.deleteProperty("_targetProfile");
        }
      }
    }

    // Let user know if other rules have been made obsolete
    List<String> obsoleteChoices = structDef.findObsoleteChoices(this, oldTypes);
    if (!obsoleteChoices.isEmpty()) {
      logger.error("Type constraint on " + getPath() + " makes rules in " + Js.toStr(nullToUndef(structDef.getName()))
          + " obsolete for choices: " + joinJs(obsoleteChoices, ", "), rule.sourceInfo);
    }

    // new types for this element have been determined
    // if there are any connected elements, make sure that nothing invalid will happen
    List<ElementDefinition> connectedElements = findConnectedElements();
    // however, we don't need to apply this to elements representing a choice of types
    // for example, if this is being applied to value[x], and valueString exists, we can remove the string type.
    if (getPath().endsWith("[x]")) {
      connectedElements.removeIf(ce -> !ce.getId().endsWith("[x]"));
    }
    if (!connectedElements.isEmpty()) {
      // if all connected elements have a non-empty intersection, we can safely apply the rule
      Map<ElementDefinition, List<ElementDefinitionType>> connectedTypeChanges = new LinkedHashMap<>();
      for (ElementDefinition ce : connectedElements) {
        List<ElementDefinitionType> intersection = findTypeIntersection(newTypes, ce.getType(), targetType, fisher);
        if (!intersection.isEmpty()) {
          connectedTypeChanges.put(ce, intersection);
        } else {
          List<String> obsoleteConnections = ce.structDef.findObsoleteChoices(ce, oldTypes);
          if (!obsoleteConnections.isEmpty()) {
            logger.error("Type constraint on " + rule.path + " makes rules in "
                + Js.toStr(nullToUndef(ce.structDef.getName())) + " obsolete for choices: "
                + joinJs(obsoleteConnections, ", "), rule.sourceInfo);
          } else {
            throw new SliceTypeRemovalError(rule.path, ce.getId());
          }
        }
      }
      if (connectedElements.size() == connectedTypeChanges.size()) {
        connectedTypeChanges.forEach((ce, ceType) -> ce.setType(ceType));
      }
    }

    // Finally, reset this element's types to the new types
    setType(newTypes);
    // extra check for modifier extension usage
    List<ElementTypeMatchInfo> extensionMatches = typeMatches.get("Extension");
    if (extensionMatches != null && !extensionMatches.isEmpty()) {
      // fish up each specific profile by url to see if it is a modifier extension
      boolean isModifierPath = getPath().endsWith(".modifierExtension");
      for (ElementTypeMatchInfo typeMatch : extensionMatches) {
        Object fullExtension = fisher.fishForFHIR(typeMatch.metadata.url, FishableType.Extension);
        if (fullExtension != null) {
          boolean isModifier = Common.isModifierExtension(fullExtension);
          if (isModifier && !isModifierPath) {
            logger.error("Modifier extension " + Js.toStr(nullToUndef(typeMatch.metadata.name))
                + " used to constrain extension element. Modifier extensions should only be used with modifierExtension elements.",
                rule.sourceInfo);
          } else if (!isModifier && isModifierPath) {
            logger.error("Non-modifier extension " + Js.toStr(nullToUndef(typeMatch.metadata.name))
                + " used to constrain modifierExtension element. Non-modifier extensions should only be used with extension elements.",
                rule.sourceInfo);
          }
        }
      }
    }
  }

  /** {@code newList.map(p => origUnder[origList.indexOf(p)] ?? null)}. */
  private static List<Object> mapParallel(List<String> newList, List<String> origList, List<Object> origUnder) {
    List<Object> out = new ArrayList<>();
    for (String p : newList) {
      int idx = origList == null ? -1 : origList.indexOf(p);
      Object v = idx >= 0 && idx < origUnder.size() ? origUnder.get(idx) : null;
      out.add(nn(v));
    }
    return out;
  }

  private static String firstTypeCode(ElementDefinition e) {
    if (e == null || e.getType() == null || e.getType().isEmpty() || e.getType().get(0) == null) return null;
    return e.getType().get(0).getCode();
  }

  /**
   * Given a target type or profile, returns this element's matching type with all other profiles or targetProfiles
   * removed.
   */
  private ElementDefinitionType getTargetType(String target, Fishable fisher) {
    ElementDefinitionType targetType = null;
    if (Js.truthy(target)) {
      Metadata targetSD = fisher.fishForMetadata(target, FishableType.Resource, FishableType.Logical,
          FishableType.Type, FishableType.Profile, FishableType.Extension);
      if (targetSD == null) {
        throw new TypeNotFoundError(target);
      }
      // Try to match on types by an exact match on the code (applies to resources),
      // the profiles (applies to profiles), or targetProfiles (applies to references).
      // Clone it since we will filter out the non-target profiles/targetProfiles.
      for (ElementDefinitionType t : getType()) {
        if (Objects.equals(t.getCode(), targetSD.id) // TODO: Should this be on type (for LMs?)
            || (t.getProfile() != null && t.getProfile().contains(targetSD.url))
            || (t.getTargetProfile() != null && t.getTargetProfile().contains(targetSD.url))) {
          targetType = t.cloneDeep();
          break;
        }
      }

      if (targetType == null) {
        throw new InvalidTypeError(target, getType());
      }

      // Re-assign the targetProfiles/profiles as appropriate to remove non-targets
      if (targetType.getProfile() != null && targetType.getProfile().contains(targetSD.url)) {
        targetType.setProfile(new ArrayList<>(List.of(targetSD.url)));
      } else if (targetType.getTargetProfile() != null && targetType.getTargetProfile().contains(targetSD.url)) {
        targetType.setTargetProfile(new ArrayList<>(List.of(targetSD.url)));
      }
    }
    return targetType;
  }

  /**
   * Given an input type (the constraint) and a set of target types, find the match and return information about it.
   *
   * @throws TypeNotFoundError when the type's definition cannot be found
   * @throws InvalidTypeError when the type doesn't match any of the targetTypes
   */
  private ElementTypeMatchInfo findTypeMatch(OnlyRuleType type, List<ElementDefinitionType> targetTypes,
      Fishable fisher) {
    ElementDefinitionType matchedType = null;
    String typeName = type.type;

    // Get the lineage (type hierarchy) so we can walk up it when attempting to match
    List<Metadata> lineage = getTypeLineage(typeName, fisher, true, new ArrayList<>());
    if (lineage.isEmpty()) {
      throw new TypeNotFoundError(type.type);
    }

    // Walk up the lineage, one StructureDefinition at a time.  We can potentially match on the
    // type itself or any of its parents.
    boolean specializationOfNonAbstractType = false;
    for (Metadata md : lineage) {
      String versionedMdUrl = Js.toStr(nullToUndef(md.url)) + "|" + Js.toStr(nullToUndef(md.version));
      if (Js.truthy(type.isReference)) {
        // When 'Reference' keyword is used, prefer to match on the 'Reference' type over the
        // 'CodeableReference' type if they both exist on the element.
        matchedType = findRefLike(targetTypes, "Reference", md.url, versionedMdUrl);
        if (matchedType == null) {
          matchedType = findRefLike(targetTypes, "CodeableReference", md.url, versionedMdUrl);
        }
      } else if (Js.truthy(type.isCanonical)) {
        matchedType = findRefLike(targetTypes, "canonical", md.url, versionedMdUrl);
      } else if (Js.truthy(type.isCodeableReference)) {
        matchedType = findRefLike(targetTypes, "CodeableReference", md.url, versionedMdUrl);
      } else {
        // Look for exact match on the code (w/ no profile) or a match on an allowed base type with
        // a matching profile
        for (ElementDefinitionType t2 : targetTypes) {
          boolean matchesUnprofiledResource = Objects.equals(t2.getCode(), md.id) && Js.isEmpty(t2.getProfile());
          boolean matchesProfile = false;
          boolean lineageHit = false;
          for (Metadata ancestor : getTypeLineage(md.sdType, fisher, false, new ArrayList<>())) {
            if (Objects.equals(ancestor.sdType, t2.getCode())) {
              lineageHit = true;
              break;
            }
          }
          if (lineageHit) {
            List<String> p = t2.getProfile();
            matchesProfile = p != null && (p.contains(md.url) || p.contains(versionedMdUrl));
          }
          boolean matchesLogicalType = false;
          if ("logical".equals(structDef.getKind())) {
            matchesLogicalType = Js.truthy(t2.getCode()) && t2.getCode().equals(md.sdType);
          }
          // True if we match an unprofiled type that is not abstract, is a parent, and that we are
          // specializing (the type does not match the sdType of the type to match)
          specializationOfNonAbstractType = matchesUnprofiledResource && !Js.truthy(md._abstract)
              && !Objects.equals(md.id, lineage.get(0).id) && !Objects.equals(md.id, lineage.get(0).sdType);
          if (matchesUnprofiledResource || matchesProfile || matchesLogicalType) {
            matchedType = t2;
            break;
          }
        }
      }

      if (matchedType != null) {
        break;
      }
    }

    if (matchedType == null) {
      throw new InvalidTypeError(org.hl7.fhir.sushi.fshtypes.Common.typeString(List.of(type)), targetTypes);
    } else if (specializationOfNonAbstractType) {
      throw new NonAbstractParentOfSpecializationError(type.type, matchedType.getCode());
    }

    return new ElementTypeMatchInfo(lineage.get(0), matchedType.getCode(), typeName);
  }

  private static ElementDefinitionType findRefLike(List<ElementDefinitionType> targetTypes, String code, String url,
      String versionedUrl) {
    for (ElementDefinitionType t2 : targetTypes) {
      if (code.equals(t2.getCode()) && (t2.getTargetProfile() == null || t2.getTargetProfile().contains(url)
          || t2.getTargetProfile().contains(versionedUrl))) {
        return t2;
      }
    }
    return null;
  }

  /**
   * Gets the full lineage of the type, w/ the item at index 0 being the type's own Metadata, the item at index 1
   * being its parent's, etc. If a definition can't be found, it stops and returns as much lineage as is found.
   */
  private List<Metadata> getTypeLineage(String type, Fishable fisher) {
    return getTypeLineage(type, fisher, false, new ArrayList<>());
  }

  private List<Metadata> getTypeLineage(String type, Fishable fisher, boolean includeImposeProfiles,
      List<String> seenUrls) {
    List<Metadata> results = new ArrayList<>();

    // Start with the current type and walk up the base definitions.
    // Stop when we can't find a definition, the base definition is blank, or we've already seen the url
    String currentType = type;
    while (currentType != null) {
      if (seenUrls.contains(currentType)) {
        break;
      }
      // fishForMetadataBestVersion is not used here in order to provide additional details in the warning
      Metadata result = fisher.fishForMetadata(currentType, FishableType.Resource, FishableType.Logical,
          FishableType.Type, FishableType.Profile, FishableType.Extension);
      if (result == null) {
        String[] parts = currentType.split("\\|", -1);
        String name = parts[0];
        String version = parts.length > 1 ? String.join("|", List.of(parts).subList(1, parts.length)) : "";
        if (version.isEmpty()) version = null;
        result = fisher.fishForMetadata(name, FishableType.Resource, FishableType.Logical, FishableType.Type,
            FishableType.Profile, FishableType.Extension);
        if (result != null && version != null && result.version != null && !result.version.equals(version)) {
          logger.warn(type + " is based on " + name + " version " + version + ", but SUSHI found version "
              + result.version);
        }
      }
      if (result != null) {
        if (Js.truthy(result.url)) {
          if (seenUrls.contains(result.url)) {
            break;
          }
          seenUrls.add(result.url);
        }
        results.add(result);
      }
      currentType = result != null ? result.parent : null;
    }

    if (includeImposeProfiles) {
      // Collect all the unseen impose profiles from the results
      List<String> imposeProfiles = new ArrayList<>();
      for (Metadata md : results) {
        if (md.imposeProfiles == null) continue;
        for (Object p : md.imposeProfiles) {
          String url;
          if (p instanceof FshCanonical c) {
            Metadata m = fisher.fishForMetadata(c.entityName, FishableType.Resource, FishableType.Logical,
                FishableType.Type, FishableType.Profile, FishableType.Extension);
            url = m == null ? null : m.url;
          } else {
            url = (String) p;
          }
          if (Js.truthy(url) && !imposeProfiles.contains(url) && !seenUrls.contains(url)) {
            imposeProfiles.add(url);
          }
        }
      }
      // Get the lineage of each impose profile and add it to the results
      for (String p : imposeProfiles) {
        results.addAll(getTypeLineage(p, fisher, true, seenUrls));
      }
    }

    return results;
  }

  /**
   * Determines if the given metadata is the definition of the named type itself, rather than a profile of it (the
   * canonical URLs are compared, since a profile may share the type's id).
   */
  private boolean isBaseTypeDefinition(Metadata metadata, String code, Fishable fisher) {
    Metadata base = fisher.fishForMetadata(code, FishableType.Resource, FishableType.Type, FishableType.Logical);
    String baseUrl = base == null ? null : base.url;
    return baseUrl != null && baseUrl.equals(metadata.url);
  }

  /**
   * Given a new ElementTypeDefinition (based on the existing one), applies the matching profiles and targetProfiles.
   * If a targetType was specified, filters out the other profiles or targetProfiles.
   */
  private void applyProfiles(ElementDefinitionType newType, ElementDefinitionType targetType,
      List<ElementTypeMatchInfo> matches, Fishable fisher) {
    List<String> matchedProfiles = new ArrayList<>();
    List<String> matchedTargetProfiles = new ArrayList<>();
    for (ElementTypeMatchInfo match : matches) {
      if (Objects.equals(match.metadata.id, newType.getCode()) && matches.size() == 1
          && isBaseTypeDefinition(match.metadata, newType.getCode(), fisher)) {
        // the constraint is the type itself, so there is no profile to record
        continue;
      } else if (Common.isReferenceType(match.code) && !Common.isReferenceType(match.metadata.sdType)) {
        matchedTargetProfiles.add(match.metadata.url);
      } else if ("canonical".equals(match.code) && !"canonical".equals(match.metadata.sdType)) {
        matchedTargetProfiles.add(match.metadata.url);
      } else if ("logical".equals(structDef.getKind()) && Objects.equals(newType.getCode(), match.metadata.sdType)
          && Objects.equals(match.metadata.sdType, match.metadata.url)) {
        // The logical model's newType has a code that is a URL. This should NOT be
        // included in newType.targetProfile or newType.profile.
        continue;
      } else {
        matchedProfiles.add(match.metadata.url);
      }
    }

    if (targetType != null) {
      if (!matchedTargetProfiles.isEmpty()) {
        List<String> tp = newType.getTargetProfile();
        int targetIdx = tp == null ? -2 : tp.indexOf(targetType.getTargetProfile().get(0));
        if (targetIdx > -1) {
          tp.remove(targetIdx);
          tp.addAll(targetIdx, matchedTargetProfiles);
        } else {
          // NOTE: TS assigns newType.profile here (sharing the array), reproduced as is
          List<String> np = newType.getProfile() != null ? newType.getProfile() : new ArrayList<>();
          newType.setTargetProfile(np);
          np.addAll(matchedTargetProfiles);
        }
      }
      if (!matchedProfiles.isEmpty()) {
        List<String> p = newType.getProfile();
        int targetIdx = p == null ? -2 : p.indexOf(targetType.getProfile().get(0));
        if (targetIdx > -1) {
          p.remove(targetIdx);
          p.addAll(targetIdx, matchedProfiles);
        } else {
          List<String> np = newType.getProfile() != null ? newType.getProfile() : new ArrayList<>();
          newType.setProfile(np);
          np.addAll(matchedProfiles);
        }
      }
    } else {
      if (!matchedTargetProfiles.isEmpty()) {
        newType.setTargetProfile(matchedTargetProfiles);
      }
      if (!matchedProfiles.isEmpty()) {
        newType.setProfile(matchedProfiles);
      }
    }
  }

  private List<ElementDefinitionType> findTypeIntersection(List<ElementDefinitionType> leftTypes,
      List<ElementDefinitionType> rightTypes, ElementDefinitionType targetType, Fishable fisher) {
    List<ElementDefinitionType> intersection = new ArrayList<>();
    for (ElementDefinitionType left : leftTypes) {
      // if the left type has profiles, check each of them.
      // otherwise, just try with the code.
      List<ElementTypeMatchInfo> matches = new ArrayList<>();
      List<String> typesToTry =
          left.getProfile() != null && !left.getProfile().isEmpty() ? left.getProfile() : List.of(left.getCode());
      for (String typeToTry : typesToTry) {
        try {
          matches.add(findTypeMatch(new OnlyRuleType(typeToTry), rightTypes, fisher));
        } catch (RuntimeException e) {
          // it's okay if a given type doesn't have any matches.
        }
      }
      intersection.addAll(applyTypeIntersection(left, targetType, matches, fisher));
    }
    return intersection;
  }

  private static final Pattern FHIRPATH_PRIMITIVE = Pattern.compile("^http://hl7\\.org/fhirpath/System\\.");

  // In the case of an element type whose code is a supertype (e.g., 'Resource'), we need to
  // break that up into a new set of element types corresponding to the subtypes.
  private List<ElementDefinitionType> applyTypeIntersection(ElementDefinitionType type,
      ElementDefinitionType targetType, List<ElementTypeMatchInfo> matches, Fishable fisher) {
    List<ElementDefinitionType> intersection = new ArrayList<>();
    Map<String, List<ElementTypeMatchInfo>> currentTypeMatches = new LinkedHashMap<>();
    for (ElementTypeMatchInfo match : matches) {
      // If the original element type is a Reference/canonical, keep it as is, otherwise take on the
      // input type's type code (as represented in its StructureDefinition.type).
      String typeString = Common.isReferenceType(match.code) || "canonical".equals(match.code)
          ? match.code
          : match.metadata.sdType;
      currentTypeMatches.computeIfAbsent(typeString, k -> new ArrayList<>()).add(match);
    }
    for (Map.Entry<String, List<ElementTypeMatchInfo>> e : currentTypeMatches.entrySet()) {
      ElementDefinitionType newType = type.cloneDeep();
      // never change the code of a FHIRPath primitive
      String actual = type.getActualCode();
      if (!FHIRPATH_PRIMITIVE.matcher(actual == null ? "undefined" : actual).find()) {
        newType.setCode(e.getKey());
      }
      applyProfiles(newType, targetType, e.getValue(), fisher);
      intersection.add(newType);
    }
    return intersection;
  }

  // ================================================================================================ flags

  /**
   * Sets flags on this element as specified in a profile or extension. Don't change a flag when the incoming
   * argument is undefined or false.
   */
  public void applyFlags(Boolean mustSupport, Boolean summary, Boolean modifier, Boolean trialUse,
      Boolean normative, Boolean draft) {
    JsObject newStatusExtension = null;
    if (Js.truthy(trialUse)) {
      newStatusExtension = statusExtension("trial-use");
    }
    if (Js.truthy(normative)) {
      if (newStatusExtension != null) {
        throw new MultipleStandardsStatusError(getId());
      }
      newStatusExtension = statusExtension("normative");
    }
    if (Js.truthy(draft)) {
      if (newStatusExtension != null) {
        throw new MultipleStandardsStatusError(getId());
      }
      newStatusExtension = statusExtension("draft");
    }

    List<ElementDefinition> connectedElements = findConnectedElements();
    if (Boolean.TRUE.equals(mustSupport)) {
      if ("specialization".equals(structDef.getDerivation())) {
        // MustSupport is only allowed in profiles
        throw new InvalidMustSupportError(structDef.getName(), getId());
      }

      setMustSupport(true);
      // MS only gets applied to connected elements that are not themselves slices,
      // unless they're the same slice name as this.
      for (ElementDefinition ce : connectedElements) {
        if (ce.getSliceName() == null || ce.getSliceName().equals(getSliceName())) {
          ce.setMustSupport(true);
        }
      }
    }
    if (Boolean.TRUE.equals(summary)) {
      setIsSummary(true);
      for (ElementDefinition ce : connectedElements) ce.setIsSummary(true);
    }
    if (Boolean.TRUE.equals(modifier)) {
      setIsModifier(true);
      for (ElementDefinition ce : connectedElements) ce.setIsModifier(true);
    }
    if (newStatusExtension != null) {
      List<Object> extension = getExtension();
      if (extension != null) {
        int oldStatus = -1;
        for (int i = 0; i < extension.size(); i++) {
          if (extension.get(i) instanceof Map<?, ?> m && STANDARDS_STATUS_EXTENSION.equals(m.get("url"))) {
            oldStatus = i;
            break;
          }
        }
        if (oldStatus > -1) {
          extension.set(oldStatus, newStatusExtension);
        } else {
          extension.add(newStatusExtension);
        }
      } else {
        List<Object> l = new ArrayList<>();
        l.add(newStatusExtension);
        setExtension(l);
      }
    }
  }

  private static JsObject statusExtension(String code) {
    JsObject ext = new JsObject();
    ext.put("url", STANDARDS_STATUS_EXTENSION);
    ext.put("valueCode", code);
    return ext;
  }

  // ================================================================================================ binding

  public void bindToVS(String vsURI, String strength) {
    bindToVS(vsURI, strength, null, null);
  }

  public void bindToVS(String vsURI, String strength, SourceInfo ruleSourceInfo) {
    bindToVS(vsURI, strength, ruleSourceInfo, null);
  }

  /**
   * Binds a value set with a specific strength to this element. The type must be coded (code, Coding,
   * CodeableConcept, Quantity), or the data types (string, uri). The strength must be the same or stricter than the
   * current strength (if a binding already exists).
   *
   * @throws BindingStrengthError when the binding can't be applied because it is looser than the existing binding
   * @throws CodedTypeNotFoundError when the binding can't be applied because the element is the wrong type
   * @throws InvalidUriError when the value set uri is not valid
   */
  public void bindToVS(String vsURI, String strength, SourceInfo ruleSourceInfo, Fishable fisher) {
    // Check if this is a valid type to be bound against
    List<ElementDefinitionType> validTypes =
        findTypesByCode("code", "Coding", "CodeableConcept", "CodeableReference", "Quantity", "string", "uri");
    List<ElementDefinitionType> type = getType();
    boolean isBindable = !validTypes.isEmpty();
    if (!isBindable && type != null) {
      for (ElementDefinitionType t : type) {
        Metadata md = fisher == null ? null : fisher.fishForMetadata(t.getCode(), FishableType.Logical);
        if (md != null && Js.truthy(md.canBind)) {
          isBindable = true;
          break;
        }
      }
    }
    if (!isBindable) {
      boolean anyLogical = false;
      if (type != null) {
        for (ElementDefinitionType t : type) {
          if (fisher != null && fisher.fishForMetadata(t.getCode(), FishableType.Logical) != null) {
            anyLogical = true;
            break;
          }
        }
      }
      if (anyLogical) {
        // Only warn if it was from a logical model that doesn't have #can-bind characteristic
        logger.warn(
            "Bindings can only be applied to logical model types with the #can-bind characteristic. Update the target logical model to declare the #can-bind characteristic or remove the binding from "
                + getId() + ".",
            ruleSourceInfo);
      } else {
        List<String> codes = new ArrayList<>();
        if (type != null) for (ElementDefinitionType t : type) codes.add(t.getCode());
        throw new CodedTypeNotFoundError(codes);
      }
    }
    // Check if a CodeableReference is attempting to bind directly to the concept element
    if (type.size() == 1 && "CodeableConcept".equals(type.get(0).getCode()) && getPath().endsWith(".concept")
        && "CodeableReference".equals(firstTypeCode(parent()))) {
      logger.error(
          "Applying value set bindings to a CodeableReference element's underlying .concept path is not allowed. Instead, apply the binding directly to the CodeableReference element.",
          ruleSourceInfo);
    }
    // (a mutable list: List.of(...).indexOf(null) throws, JS indexOf(undefined) is -1)
    List<String> strengths = new ArrayList<>(List.of("example", "preferred", "extensible", "required"));
    // Check if this is a valid strength (if the binding.strength already exists)
    Map<String, Object> binding = getBinding();
    if (Js.truthy(binding) && Js.truthy(binding.get("strength"))) {
      String current = (String) binding.get("strength");
      if (strengths.indexOf(strength) < strengths.indexOf(current)) {
        throw new BindingStrengthError(current, strength);
      }
    }

    List<ElementDefinition> connectedElements = findConnectedElements();
    for (ElementDefinition ce : connectedElements) {
      Map<String, Object> ceBinding = ce.getBinding();
      Object ceVs = ceBinding == null ? null : nn(ceBinding.get("valueSet"));
      if (Objects.equals(vsURI, ceVs)) {
        try {
          ce.bindToVS(vsURI, strength, null, fisher);
        } catch (RuntimeException e) {
          // receiving a BindingStrengthError on a slice is not a problem, because
          // it is fine if the slice has a stronger binding than the list element.
        }
      }
    }
    // check to make sure we are not applying an explicitly weaker binding of the same value set of a slice's list
    // element
    ElementDefinition listElement = slicedElement();
    Map<String, Object> listBinding = listElement == null ? null : listElement.getBinding();
    Object listVs = listBinding == null ? null : nn(listBinding.get("valueSet"));
    String listStrength = listBinding == null ? null : (String) nn(listBinding.get("strength"));
    if (Objects.equals(listVs, vsURI) && strengths.indexOf(strength) < strengths.indexOf(listStrength)) {
      throw new BindingStrengthError(listStrength, strength);
    }

    JsObject newBinding = new JsObject();
    if (vsURI == null) {
      // Just bind the strength since valueSet is allowed to be 0..1
      newBinding.put("strength", strength == null ? Js.UNDEFINED : strength);
    } else {
      // Canonical URLs may include | to specify version: https://www.hl7.org/fhir/references.html#canonical
      // The URI may be a fragment starting with #
      if (!ValidUrl.isUri(vsURI.split("\\|", -1)[0]) && !vsURI.startsWith("#")) {
        throw new InvalidUriError(vsURI);
      }

      // We're good.  Bind it.
      newBinding.put("strength", strength == null ? Js.UNDEFINED : strength);
      newBinding.put("valueSet", vsURI);
    }
    setBinding(newBinding);
  }

  // ================================================================================================ assignment

  public void assignValue(Object value) {
    assignValue(value, false, null);
  }

  public void assignValue(Object value, boolean exactly) {
    assignValue(value, exactly, null);
  }

  /**
   * Assigns a value (TS AssignmentValueType: Boolean, Double, BigInteger, String, FshCode, FshQuantity, FshRatio,
   * FshReference, FshCanonical, InstanceDefinition) to this ElementDefinition.
   *
   * @param exactly true if fixed[x] should be used, otherwise pattern[x] is used
   * @throws NoSingleTypeError when the ElementDefinition does not have a single type
   * @throws ValueAlreadyAssignedError when the value is already assigned to a different value
   * @throws MismatchedTypeError when the value does not match the type of the ElementDefinition
   */
  @SuppressWarnings("unchecked")
  public void assignValue(Object value, boolean exactly, Fishable fisher) {
    String type;
    if (value instanceof FshCode) {
      type = "Code";
    } else if (value instanceof FshQuantity) {
      type = "Quantity";
    } else if (value instanceof FshRatio) {
      type = "Ratio";
    } else if (value instanceof FshReference) {
      type = "Reference";
    } else if (value instanceof FshCanonical) {
      type = "Canonical";
    } else if (value instanceof InstanceDefinition) {
      type = "InstanceDefinition";
    } else if (value instanceof BigInteger) {
      type = "number";
    } else {
      type = typeOf(value);
    }

    // We can only assign elements that have a single type, else it is ambiguous
    if (!hasSingleType()) {
      throw new NoSingleTypeError(type);
    }

    // If assigning by pattern, ensure that it's not already assigned by fixed[x], because We can't override
    // fixed[x] with pattern[x] since pattern[x] is looser
    if (!exactly) {
      for (String k : propertyKeys()) {
        if (k.startsWith("fixed") && nn(props.get(k)) != null) {
          throw new FixedToPatternError(k);
        }
      }
    }

    // The approach to assigning may differ based on type...
    switch (type) {
      case "boolean" -> assignFHIRValue(value.toString(), value, exactly, "boolean", null);
      case "number" -> assignNumber(value, exactly);
      case "string" -> assignString((String) value, exactly);
      case "Code" -> assignFshCode((FshCode) value, exactly, fisher);
      case "Quantity" -> {
        FshQuantity q = (FshQuantity) value;
        // Special case quantity to support compatible specializations (like Age), but try to do it
        // in a flexible way (without hard-coding every specialization here).
        String providedType = "Quantity";
        String actualType = getType().get(0).getCode();
        if (!"Quantity".equals(actualType)) {
          Object actualTypeSD = fisher == null ? null : fisher.fishForFHIR(actualType, FishableType.Type);
          if (actualTypeSD instanceof Map<?, ?> m
              && "http://hl7.org/fhir/StructureDefinition/Quantity".equals(m.get("baseDefinition"))) {
            // We treat every quantity instance as compatible w/ every specialization. This is not
            // strictly true, but in order to validate it, we'd need to either support/process the
            // FHIRPath rules or hard-code a bunch of special case logic here. Instead, let the IG
            // Publisher deal with this extra validation and just pass it through here.
            providedType = actualType;
          }
        }
        assignFHIRValue(q.toString(), q.toFHIRQuantity(), exactly, providedType, null);
      }
      case "Ratio" -> {
        FshRatio r = (FshRatio) value;
        assignFHIRValue(r.toString(), r.toFHIRRatio(), exactly, "Ratio", null);
      }
      case "Reference" -> {
        FshReference ref = (FshReference) value;
        // It is possible the reference constraints do not come from the current element itself, so find the
        // element which is constraining the current element, and check the assignment against it
        ElementDefinition referenceConstrainingElement =
            "Reference".equals(getType().get(0).getCode()) && "CodeableReference".equals(firstTypeCode(parent()))
                ? parent()
                : this;
        if (!referenceConstrainingElement.typeSatisfiesTargetProfile(ref.sdType, ref.sourceInfo, fisher)) {
          throw new InvalidTypeError("Reference(" + Js.toStr(nullToUndef(ref.sdType)) + ")",
              referenceConstrainingElement.getType());
        }
        if ("CodeableReference".equals(getType().get(0).getCode())) {
          // If we are assigning to a CodeableReference, we assign a value of a complex object containing the
          // reference. If there's already a coding on the element, we want to keep it.
          Object existing = firstNonNull(getProperty("fixedCodeableReference"),
              getProperty("patternCodeableReference"));
          if (existing == null) existing = assignedByAnyParent();
          JsObject codeableReference = new JsObject();
          codeableReference.put("reference", ref.toFHIRReference());
          Object concept = existing instanceof Map<?, ?> m ? nn(m.get("concept")) : null;
          if (concept != null) {
            codeableReference.put("concept", concept);
          }
          assignFHIRValue(ref.toString(), codeableReference, exactly, "CodeableReference", null);
        } else {
          assignFHIRValue(ref.toString(), ref.toFHIRReference(), exactly, "Reference", null);
        }
      }
      case "Canonical" -> {
        FshCanonical can = (FshCanonical) value;
        // Get the canonical url of the entity
        FishableType[] canonicalTypes = {FishableType.Resource, FishableType.Logical, FishableType.Type,
            FishableType.Profile, FishableType.Extension, FishableType.ValueSet, FishableType.CodeSystem,
            FishableType.Instance};
        Metadata canonicalDefinition = fisher.fishForMetadata(can.entityName, canonicalTypes);
        String canonicalUrl;
        if (!typeSatisfiesTargetProfile(canonicalDefinition == null ? null : canonicalDefinition.resourceType,
            can.sourceInfo, fisher)) {
          // The first fishing result didn't work. Now check all results in case the fishing matches multiple
          // results.
          List<Metadata> allMatchingMetadatas = fisher.fishForMetadatas(can.entityName, canonicalTypes);
          Metadata otherCanonicalDefinition = null;
          for (Metadata md : allMatchingMetadatas) {
            if (typeSatisfiesTargetProfile(md == null ? null : md.resourceType, can.sourceInfo, fisher)) {
              otherCanonicalDefinition = md;
              break;
            }
          }
          if (otherCanonicalDefinition != null) {
            canonicalDefinition = otherCanonicalDefinition;
          } else {
            throw new InvalidTypeError("Canonical(" + Js.toStr(nullToUndef(canonicalDefinition.resourceType)) + ")",
                getType());
          }
        }
        if (canonicalDefinition != null && Js.truthy(canonicalDefinition.url)) {
          canonicalUrl = canonicalDefinition.url;
        } else if (canonicalDefinition != null && Js.truthy(canonicalDefinition.id)
            && "Inline".equals(canonicalDefinition.instanceUsage)) {
          canonicalUrl = "#" + canonicalDefinition.id;
        } else {
          throw new InvalidCanonicalUrlError(can.entityName);
        }
        if (Js.truthy(can.version)) {
          canonicalUrl += "|" + can.version;
        }
        assignString(canonicalUrl, exactly);
      }
      case "InstanceDefinition" -> {
        InstanceDefinition inst = (InstanceDefinition) value;
        String stringVal = Json.stringify(inst);
        assignFHIRValue(stringVal, inst.toJSON(), exactly,
            inst._instanceMeta.sdType != null ? inst._instanceMeta.sdType : inst.resourceType(), fisher);
      }
      default -> {
        String t = value instanceof Map<?, ?> || value instanceof List<?> || (value != null && !isPrimitiveJava(value))
            ? constructorName(value)
            : "false"; // TS: (typeof value === 'object' && value.constructor?.name) ?? type
        throw new MismatchedTypeError(t, value, getType().get(0).getCode());
      }
    }

    // If the element is found in a discriminator.path, then we enforce that it has minimum cardinality 1
    // since its value is assigned. It has to be there, otherwise it's not that slice. The one exception
    // is if you are fixing the value of the sliced element itself (e.g., discriminator path is $this),
    // in which case we don't want to force the whole slice to be required.
    List<ElementDefinition> chain = new ArrayList<>();
    chain.add(this);
    List<ElementDefinition> parents = getAllParents();
    java.util.Collections.reverse(parents);
    chain.addAll(parents);
    for (ElementDefinition parentSlice : chain) {
      if (!Js.truthy(parentSlice.getSliceName())) continue;
      ElementDefinition slicedElement = parentSlice.slicedElement();
      Object discriminator = nn(slicedElement.getSlicing().get("discriminator"));
      boolean some = false;
      if (discriminator instanceof List<?> ds) {
        for (Object o : ds) {
          Map<String, Object> d = (Map<String, Object>) o;
          Object dPath = nn(d.get("path"));
          if (!"$this".equals(dPath) && (slicedElement.getPath() + "." + Js.toStr(nullToUndef(dPath))).equals(getPath())
              && ("value".equals(d.get("type")) || "pattern".equals(d.get("type")))) {
            some = true;
            break;
          }
        }
      }
      if (some && getMin() != null && getMin() == 0) {
        constrainCardinality(1, "");
      }
    }
  }

  private static boolean isPrimitiveJava(Object v) {
    return v instanceof String || v instanceof Number || v instanceof Boolean;
  }

  private static String typeOf(Object v) {
    if (v == null || v == Js.UNDEFINED) return "undefined";
    if (v instanceof Boolean) return "boolean";
    if (v instanceof Number) return "number";
    if (v instanceof String) return "string";
    return "object";
  }

  private static String constructorName(Object v) {
    if (v instanceof Map<?, ?>) return "Object";
    if (v instanceof List<?>) return "Array";
    return v.getClass().getSimpleName();
  }

  private static Object firstNonNull(Object a, Object b) {
    return a != null ? a : b;
  }

  /**
   * Checks if a FHIR value can be assigned and then assigns it if so. A FHIR value can be assigned on an element
   * if the element isn't already assigned to something else (by fixed[x], pattern[x], or from a parent), or the
   * element is already assigned to something that is the same or a subset of the new value.
   *
   * @param fshValue the FSH-syntax-formatted value (usually the FSH class .toString())
   * @param fhirValue the FHIR representation of the FSH value
   * @param type the FHIR type that is being assigned; used to construct fixed[x] and pattern[x] names
   */
  private void assignFHIRValue(String fshValue, Object fhirValue, boolean exactly, String type, Fishable fisher) {
    List<String> lineage = new ArrayList<>();
    if (fisher != null) {
      for (Metadata meta : getTypeLineage(type, fisher)) lineage.add(meta.sdType);
    }
    boolean typeOk = false;
    for (ElementDefinitionType t : getType()) {
      if (Objects.equals(t.getCode(), type) || lineage.contains(t.getCode())) {
        typeOk = true;
        break;
      }
    }
    if (!typeOk) {
      throw new MismatchedTypeError(type, fshValue, getType().get(0).getCode());
    }

    // Find the fixed[x] and pattern[x] variables to use
    String fixedX = "fixed" + Js.upperFirst(type);
    String patternX = "pattern" + Js.upperFirst(type);

    // Find any currently assigned values
    Object currentElementValue = firstNonNull(getProperty(fixedX), getProperty(patternX));
    if (currentElementValue == null) currentElementValue = assignedByAnyParent();
    // For complex types, use isMatch to check if they are a subset, otherwise use isEqual
    boolean isObject = isJsObject(fhirValue);
    if (currentElementValue != null && currentElementValue != Js.UNDEFINED && !(currentElementValue instanceof List<?>)
        && !(isObject ? Js.isMatch(fhirValue, currentElementValue) : Js.isEqual(fhirValue, currentElementValue))) {
      // It's a different value and not a compatible subset (e.g., the new value doesn't contain the old)
      throw new ValueAlreadyAssignedError(fshValue, type, Json.stringify(currentElementValue));
    }

    // Children of elements with complex types such as Quantity may already have assigned values
    checkAssignedValueAgainstChildren(this, fhirValue);

    // if this is a slice, make sure that nothing on this.slicedElement() is being violated
    ElementDefinition slicedElement = slicedElement();
    if (slicedElement != null) {
      slicedElement.checkAssignedValueAgainstChildren(slicedElement, fhirValue);
    }

    // If we made it this far, assign the value using fixed[x] or pattern[x] as appropriate
    if (exactly) {
      props.put(fixedX, fhirValue);
      props.remove(patternX);
    } else {
      props.put(patternX, fhirValue);
      // NOTE: No need to delete fixed[x], as changing from fixed[x] to pattern[x] is not allowed
    }
  }

  /** JS {@code typeof v === 'object'} for a value-model value (null counts, as in JS). */
  private static boolean isJsObject(Object v) {
    return v == null || !(v instanceof String || v instanceof Number || v instanceof Boolean || v == Js.UNDEFINED);
  }

  private void checkAssignedValueAgainstChildren(ElementDefinition currentChild, Object fhirValue) {
    List<ElementDefinition> directChildren = currentChild.children(true);

    int i = 0;
    while (i < directChildren.size()) {
      ElementDefinition child = directChildren.get(i);

      checkAssignedValueAgainstChild(child, fhirValue);
      checkAssignedValueAgainstChildren(child, fhirValue);

      // If the slicing is closed, only throw an error if all slices conflict
      Map<String, Object> slicing = child.getSlicing();
      if (slicing != null && "closed".equals(slicing.get("rules"))) {
        int numInvalid = 0;
        for (ElementDefinition slice : child.getSlices()) {
          try {
            checkAssignedValueAgainstChild(slice, fhirValue);
            checkAssignedValueAgainstChildren(slice, fhirValue);
          } catch (RuntimeException e) {
            numInvalid += 1;
          }
        }
        if (numInvalid >= child.getSlices().size()) {
          throw new ValueConflictsWithClosedSlicingError(Json.stringify(fhirValue));
        }
      }

      // If there is any slicing, skip past the slices. Closed slices are handled above
      // And open slices need not be validated against
      i += Js.truthy(child.getSlicing()) ? child.getSlices().size() + 1 : 1;
    }
  }

  private void checkAssignedValueAgainstChild(ElementDefinition child, Object fhirValue) {
    List<ElementDefinitionType> ct = child.getType();
    if (ct == null) {
      throw jsTypeError("Cannot read properties of " + (child.raw("type") == null ? "null" : "undefined")
          + " (reading '0')");
    }
    String childType = ct.get(0).getCode();
    String fixedX = "fixed" + Js.upperFirst(childType);
    String patternX = "pattern" + Js.upperFirst(childType);
    Object currentChildValue = firstNonNull(child.getProperty(fixedX), child.getProperty(patternX));
    if (currentChildValue != null) {
      // find the element on fhirValue that would get assigned to the child.
      // sometimes, there may be arrays inside complex object types, such as CodeableConcept.
      // therefore, flatten those arrays as the path is traversed so that all possible new values are found.
      String[] childPath = replaceFirstLiteral(child.getPath(), getPath() + ".", "").split("\\.", -1);
      List<Object> newChildValue = new ArrayList<>();
      newChildValue.add(fhirValue);
      for (String pathPart : childPath) {
        List<Object> next = new ArrayList<>();
        for (Object value : newChildValue) {
          Object v = propOf(value, pathPart);
          if (v instanceof List<?> l) next.addAll(l);
          else next.add(v);
        }
        newChildValue = next;
      }
      for (Object value : newChildValue) {
        if (value != null && value != Js.UNDEFINED) {
          boolean ok = isJsObject(value) ? Js.isMatch(value, currentChildValue) : Js.isEqual(value, currentChildValue);
          if (!ok) {
            throw new ValueAlreadyAssignedError(value, childType, Json.stringify(currentChildValue));
          }
        }
      }
    }
  }

  /**
   * @return false if the type does not satisfy the targetProfile, true otherwise (or if it can't be determined)
   */
  private boolean typeSatisfiesTargetProfile(String sdType, SourceInfo sourceInfo, Fishable fisher) {
    // If no targetProfile is present, there is nothing to check the value against, so just allow it
    if (Js.truthy(sdType) && getType().get(0).getTargetProfile() != null) {
      List<String> validTypes = new ArrayList<>();
      for (String tp : getType().get(0).getTargetProfile()) {
        // target profile may have a version after a | character.
        // fish for matching version, but fall back to any version if necessary.
        Metadata md = FishingUtils.fishForMetadataBestVersion(fisher, tp, sourceInfo);
        String tpType = md == null ? null : md.sdType;
        if (Js.truthy(tpType)) {
          validTypes.add(tpType);
        }
      }

      List<Metadata> typeLineage = getTypeLineage(sdType, fisher);
      boolean any = false;
      for (Metadata md : typeLineage) {
        if (validTypes.contains(md.sdType)) {
          any = true;
          break;
        }
      }
      if (!any) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if an element is assigned by a fixed[x] or pattern[x] on its direct parent.
   *
   * @return the value the element is assigned to by its parent, null if value is not assigned
   */
  public Object assignedByDirectParent() {
    ElementDefinition parent = parent();
    String assignedKey = parent != null ? parent.assignedKey() : null;
    if (assignedKey != null) {
      Object assignedValue = parent.raw(assignedKey);
      if (assignedValue == null || assignedValue == Js.UNDEFINED) {
        throw jsTypeError("Cannot read properties of " + (assignedValue == null ? "null" : "undefined")
            + " (reading '" + replaceFirstLiteral(getPath(), parent.getPath() + ".", "") + "')");
      }
      return nn(propOf(assignedValue, replaceFirstLiteral(getPath(), parent.getPath() + ".", "")));
    }
    return null;
  }

  /**
   * Checks if an element is assigned by a pattern[x] on any of its parents.
   *
   * @return the value the element is assigned to by its parent, null if value is not assigned
   */
  public Object assignedByAnyParent() {
    ElementDefinition parent = parent();
    if (parent == null) {
      return null;
    }
    Object assignedValue = assignedByDirectParent();
    if (assignedValue == null) {
      // Get the value from the parent, and index into that value
      Object parentValue = parent.assignedByAnyParent();
      String childIndex = replaceFirstLiteral(getPath(), parent.getPath() + ".", "");
      if (parentValue instanceof List<?> pv) {
        // If the value is an array, there are two cases
        // 1 - All the assigned values in the array match => return the value
        // 2 - The assigned values in the array don't match => return [val1, val2]
        // since values do exist, but they conflict so no value should be allowed to be set
        // and for any value [val1, val2] != value
        boolean every = true;
        for (Object v : pv) {
          if (!strictEquals(propOf(v, childIndex), propOf(pv.get(0), childIndex))) {
            every = false;
            break;
          }
        }
        if (every && !pv.isEmpty()) {
          assignedValue = nn(propOf(pv.get(0), childIndex));
        } else {
          List<Object> l = new ArrayList<>();
          for (Object v : pv) l.add(nn(propOf(v, childIndex)));
          assignedValue = l;
        }
      } else {
        assignedValue = parentValue == null ? null : nn(propOf(parentValue, childIndex));
      }
    }
    return assignedValue;
  }

  /** JS ===: primitives by value (NaN !== NaN), objects by identity. */
  private static boolean strictEquals(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
    if (a instanceof String || a instanceof Boolean) return a.equals(b);
    if (a == null) return b == null;
    return a == b;
  }

  /**
   * Assigns a number (Double, or BigInteger for FSH integers beyond 2^53) to this element.
   *
   * @throws MismatchedTypeError when the value does not match the type of the ElementDefinition
   */
  private void assignNumber(Object value, boolean exactly) {
    String type = getType().get(0).getCode();
    double valueAsNumber = ((Number) value).doubleValue();
    String valueString = value instanceof BigInteger b ? b.toString() : JsNumber.toString(valueAsNumber);
    boolean isInteger = Double.isFinite(valueAsNumber) && Math.floor(valueAsNumber) == valueAsNumber;
    if ("decimal".equals(type) || ("integer".equals(type) && isInteger)
        || ("unsignedInt".equals(type) && isInteger && valueAsNumber >= 0)
        || ("positiveInt".equals(type) && isInteger && valueAsNumber > 0)) {
      assignFHIRValue(valueString, valueAsNumber, exactly, type, null);
    } else if ("integer64".equals(type) && value instanceof BigInteger) {
      // integer64 is dealt with separately, since it is represented as a string in FHIR
      // see: http://hl7.org/fhir/2020Sep/datatypes.html#integer64
      assignFHIRValue(valueString, valueString, exactly, type, null);
    } else {
      throw new MismatchedTypeError("number", value, type);
    }
  }

  private static final Pattern BASE64_PART =
      Pattern.compile("(" + js("\\s") + "*([0-9a-zA-Z\\+/=]){4}" + js("\\s") + "*)");

  private boolean isValidBase64(String value) {
    Matcher m = BASE64_PART.matcher(value);
    int lastIndex = 0;
    while (lastIndex < value.length()) {
      m.region(lastIndex, value.length());
      if (!m.lookingAt()) {
        return false;
      }
      lastIndex = m.end();
    }
    return true;
  }

  /** A JS regex fragment with JS {@code \s} / {@code \S} semantics (Unicode whitespace) in Java syntax. */
  private static String js(String re) {
    String ws = org.hl7.fhir.sushi.fshtypes.Common.JS_WS;
    String inner = ws.substring(1, ws.length() - 1);
    return re.replace("\\S", "[^" + inner + "]").replace("\\s", ws);
  }

  private static final Pattern NO_WS = Pattern.compile(js("\\S") + "*");
  private static final Pattern INSTANT = Pattern.compile(
      "([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))");
  private static final Pattern DATE = Pattern.compile(
      "([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?");
  private static final Pattern DATE_TIME = Pattern.compile(
      "([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?");
  private static final Pattern TIME = Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?");
  private static final Pattern OID = Pattern.compile("urn:oid:[0-2](\\.(0|[1-9][0-9]*))+");
  private static final Pattern ID = Pattern.compile("[A-Za-z0-9\\-\\.]{1,64}");
  private static final Pattern INTEGER64 = Pattern.compile("[-]?\\d+");

  /**
   * Assigns a string to this element.
   *
   * @throws MismatchedTypeError when the value does not match the type of the ElementDefinition
   */
  private void assignString(String value, boolean exactly) {
    String type = getType().get(0).getCode();
    if ("string".equals(type)
        || ("uri".equals(type) && NO_WS.matcher(value).matches())
        || ("url".equals(type) && NO_WS.matcher(value).matches())
        || ("canonical".equals(type) && NO_WS.matcher(value).matches())
        || ("base64Binary".equals(type) && (isValidBase64(value) || value.startsWith("ig-loader-")))
        || ("instant".equals(type) && INSTANT.matcher(value).matches())
        || ("date".equals(type) && DATE.matcher(value).matches())
        || ("dateTime".equals(type) && DATE_TIME.matcher(value).matches())
        || ("time".equals(type) && TIME.matcher(value).matches())
        || ("oid".equals(type) && OID.matcher(value).matches())
        || ("id".equals(type) && ID.matcher(value).matches())
        // TS /^\s*(\S|\s)*$/ matches every string
        || "markdown".equals(type)
        || "uuid".equals(type)
        || ("integer64".equals(type) && INTEGER64.matcher(value).matches())) {
      assignFHIRValue("\"" + value + "\"", value, exactly, type, null);
    } else if ("xhtml".equals(type) && checkXhtml(value)) {
      assignFHIRValue("\"" + value + "\"", value, exactly, type, null);
      // If we got here, the assigned value is valid. Replace the XML with a minimized version.
      // For minimizer options, see: https://www.npmjs.com/package/html-minifier#options-quick-reference
      props.put(exactly ? "fixedXhtml" : "patternXhtml", HtmlMinifier.minify(value, HtmlMinifier.Options.SUSHI));
    } else {
      throw new MismatchedTypeError("string", value, type);
    }
  }

  private static final Pattern STARTS_WITH_DIV = Pattern.compile("^" + js("\\s") + "*<" + js("\\s") + "*div[" +
      org.hl7.fhir.sushi.fshtypes.Common.JS_WS.substring(1, org.hl7.fhir.sushi.fshtypes.Common.JS_WS.length() - 1)
      + ">]");
  private static final Pattern ENDS_WITH_DIV =
      Pattern.compile("<" + js("\\s") + "*/" + js("\\s") + "*div" + js("\\s") + "*>" + js("\\s") + "*\\z");

  private boolean checkXhtml(String value) {
    try {
      if (getPath().endsWith(".div")) {
        boolean startsWithDiv = STARTS_WITH_DIV.matcher(value).find();
        boolean endsWithDiv = ENDS_WITH_DIV.matcher(value).find();
        if (!startsWithDiv || !endsWithDiv) {
          logger.warn("xhtml div elements should start and end with <div> tags for " + getId());
        }
      }
      return Sax.parser(true).write(value).error() == null;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * Checks if a resource can be assigned to this element.
   *
   * @throws MismatchedTypeError when the ElementDefinition is not of type Resource
   * @return the input value when it can be assigned
   */
  public InstanceDefinition checkAssignInlineInstance(InstanceDefinition value, Fishable fisher) {
    String inlineInstanceType = value.resourceType() != null ? value.resourceType() : value._instanceMeta.sdType;
    List<String> lineage = new ArrayList<>();
    for (Metadata md : getTypeLineage(inlineInstanceType, fisher)) lineage.add(md.sdType);
    List<ElementDefinitionType> type = getType();
    if (type != null && type.stream().anyMatch(t -> lineage.contains(t.getCode()))) {
      // capture original value to restore after assignment
      String originalKey = assignedKeyPatternFirst();
      Object originalValue = originalKey == null ? Js.UNDEFINED : raw(originalKey);
      // try assigning it to test for value conflicts
      String stringVal = Json.stringify(value);
      assignFHIRValue(stringVal, value.toJSON(), true,
          value._instanceMeta.sdType != null ? value._instanceMeta.sdType : value.resourceType(), fisher);
      // if the assignment is successful, undo it and return the value
      String key = assignedKeyPatternFirst();
      if (key != null) {
        props.remove(key);
        // TS: this[originalKey] = originalValue (originalKey may be undefined, which sets a key "undefined")
        props.put(originalKey == null ? "undefined" : originalKey, originalValue);
      }
      return value;
    } else {
      // In this case neither the type of the inline instance nor the type of any of its parents matches the
      // ED.type, so we cannot assign the inline instance to this ED.
      List<String> codes = new ArrayList<>();
      if (type != null) for (ElementDefinitionType t : type) codes.add(t.getCode());
      throw new MismatchedTypeError(inlineInstanceType,
          value._instanceMeta.name != null ? value._instanceMeta.name : value.id(),
          type == null ? null : joinJs(codes, ", "));
    }
  }

  /** {@code Object.keys(this).find(k => k.startsWith('pattern') || k.startsWith('fixed'))}. */
  private String assignedKeyPatternFirst() {
    for (String k : propertyKeys()) {
      if (k.startsWith("pattern") || k.startsWith("fixed")) return k;
    }
    return null;
  }

  /**
   * Assigns a code to this element, formatting it in the way the element expects for the type.
   *
   * @throws MismatchedTypeError when the type of the value cannot be assigned to the element
   * @throws ValueAlreadyAssignedError when the code is already assigned to a different code
   * @throws InvalidUriError when the system being assigned is not a valid uri
   */
  private void assignFshCode(FshCode code, boolean exactly, Fishable fisher) {
    String type = getType().get(0).getCode();

    if (Js.truthy(code.system)) {
      String csURI = code.system.split("\\|", -1)[0];
      Metadata vs = FishingUtils.fishForMetadataBestVersion(fisher, code.system, code.sourceInfo,
          FishableType.ValueSet);
      String vsURI = vs != null && vs.url != null ? vs.url : "";
      boolean simple = "code".equals(type) || "string".equals(type) || "uri".equals(type);
      if (Js.truthy(vsURI)) {
        if (simple) {
          logger.warn("The fully qualified code " + code.system + "#" + code.code
              + " is invalid because the specified system is a ValueSet. "
              + "Since " + getPath() + " is a " + type + ", the system will not be used, but this issue should be corrected by "
              + "updating the system to refer to a proper CodeSystem or by specifying a code only (e.g., #" + code.code + ").");
        } else {
          throw new MismatchedBindingTypeError(code.system, getPath(), "CodeSystem");
        }
      } else if (!ValidUrl.isUri(csURI)) {
        if (simple) {
          logger.warn("The fully qualified code " + code.system + "#" + code.code
              + " is invalid because the specified system is not a URI. "
              + "Since " + getPath() + " is a " + type + ", the system will not be used, but this issue should be corrected by "
              + "updating the system to refer to a proper CodeSystem or by specifying a code only (e.g., #" + code.code + ").");
        } else {
          throw new InvalidUriError(code.system);
        }
      }
    }

    if ("code".equals(type) || "string".equals(type) || "uri".equals(type)) {
      assignFHIRValue(code.toString(), code.code, exactly, type, null);
    } else if ("CodeableConcept".equals(type)) {
      assignFHIRValue(code.toString(), code.toFHIRCodeableConcept(), exactly, "CodeableConcept", null);
    } else if ("Coding".equals(type)) {
      assignFHIRValue(code.toString(), code.toFHIRCoding(), exactly, "Coding", null);
    } else if (isQuantityType(type, fisher)) {
      // Since code only maps to part of Quantity, we want to ensure that if there are other (non-code) parts
      // already assigned, we take them on too -- as we don't want to overwrite them with blanks.
      Object existing = firstNonNull(getProperty("fixedQuantity"), getProperty("patternQuantity"));
      if (existing == null) existing = assignedByAnyParent();
      JsObject quantity = code.toFHIRQuantity();
      if (existing instanceof Map<?, ?> m) {
        if (nn(m.get("value")) != null) {
          quantity.put("value", m.get("value"));
        }
        if (nn(m.get("comparator")) != null) {
          quantity.put("comparator", m.get("comparator"));
        }
      }
      assignFHIRValue(code.toString(), quantity, exactly, type, null);
    } else if ("CodeableReference".equals(type)) {
      // Similar to Quantity, we want to ensure that if a reference already exists, we keep it.
      Object existing = firstNonNull(getProperty("fixedCodeableReference"), getProperty("patternCodeableReference"));
      if (existing == null) existing = assignedByAnyParent();
      JsObject codeableReference = new JsObject();
      codeableReference.put("concept", code.toFHIRCodeableConcept());
      if (existing instanceof Map<?, ?> m && nn(m.get("reference")) != null) {
        codeableReference.put("reference", m.get("reference"));
      }
      assignFHIRValue(code.toString(), codeableReference, exactly, "CodeableReference", null);
    } else {
      throw new MismatchedTypeError("code", code, type);
    }
  }

  /** Checks if a provided type is a specialization of Quantity. */
  private boolean isQuantityType(String type, Fishable fisher) {
    if ("Quantity".equals(type)) {
      return true;
    }
    Object actualTypeSD = fisher == null ? null : fisher.fishForFHIR(type, FishableType.Type);
    return actualTypeSD instanceof Map<?, ?> m
        && "http://hl7.org/fhir/StructureDefinition/Quantity".equals(m.get("baseDefinition"));
  }

  /** Checks if the ElementDefinition has exactly one type. */
  private boolean hasSingleType() {
    List<ElementDefinitionType> types = getType();
    return types != null && types.size() == 1;
  }

  // ================================================================================================ mapping

  private static final Pattern ID_REGEX = Pattern.compile("[A-Za-z0-9\\-\\.]{1,64}");

  /**
   * Sets mapping on an element.
   *
   * @throws InvalidMappingError when attempting to set mapping with identity or map undefined
   * @throws InvalidFHIRIdError when setting mapping.identity to an invalid FHIR ID
   */
  public void applyMapping(String identity, String map, String comment, FshCode language) {
    if (identity == null || map == null) {
      throw new InvalidMappingError();
    }
    if (!ID_REGEX.matcher(identity).matches()) {
      throw new InvalidFHIRIdError(identity);
    }
    JsObject mapping = new JsObject();
    mapping.put("identity", identity);
    mapping.put("map", map);
    if (Js.truthy(comment)) mapping.put("comment", comment);
    if (language != null) mapping.put("language", language.code == null ? Js.UNDEFINED : language.code);
    List<Object> existing = getMapping();
    if (Js.truthy(existing)) {
      existing.add(mapping);
    } else {
      List<Object> l = new ArrayList<>();
      l.add(mapping);
      setMapping(l);
    }
  }

  // ================================================================================================ tree

  /** Finds and returns the parent element (e.g. {@code Foo.bar} for {@code Foo.bar.one}), or null for the root. */
  public ElementDefinition parent() {
    if (treeParent == null) {
      String id = getId();
      String parentId = jsSlice(id, id.lastIndexOf('.'));
      if (!parentId.isEmpty() && structDef != null) {
        treeParent = structDef.findElement(parentId);
      } else if (structDef == null) {
        treeParent = null;
      }
    }
    return treeParent;
  }

  /** {@code s.slice(0, end)} with JS negative-end semantics. */
  private static String jsSlice(String s, int end) {
    if (end < 0) end = Math.max(0, s.length() + end);
    return s.substring(0, Math.min(end, s.length()));
  }

  /** Finds and returns all parent elements, nearest first. */
  public List<ElementDefinition> getAllParents() {
    List<ElementDefinition> parents = new ArrayList<>();
    ElementDefinition parent = parent();
    while (parent != null) {
      parents.add(parent);
      parent = parent.parent();
    }
    return parents;
  }

  public List<ElementDefinition> children() {
    return children(false);
  }

  /**
   * Finds and returns all child elements of this element that already exist in the structure definition (no
   * unfolding). With directOnly, returns the live treeChildren list.
   */
  public List<ElementDefinition> children(boolean directOnly) {
    if (treeChildren == null) {
      treeChildren = new ArrayList<>();
      String prefix = getId() + ".";
      int depth = countParts(getPath()) + 1;
      for (ElementDefinition e : structDef.elements) {
        if (e != this && e.getId().startsWith(prefix) && countParts(e.getPath()) == depth) {
          treeChildren.add(e);
        }
      }
    }
    if (directOnly) {
      return treeChildren;
    } else {
      List<ElementDefinition> descendents = new ArrayList<>();
      for (ElementDefinition child : new ArrayList<>(treeChildren)) {
        descendents.add(child);
        descendents.addAll(child.children());
      }
      return descendents;
    }
  }

  /** {@code path.split('.').length}. */
  private static int countParts(String path) {
    int n = 1;
    for (int i = 0; i < path.length(); i++) if (path.charAt(i) == '.') n++;
    return n;
  }

  /**
   * Finds and returns all assignable descendents of the element: direct children with min > 0, and all assignable
   * descendents of such children.
   */
  public List<ElementDefinition> getAssignableDescendents() {
    List<ElementDefinition> assignableChildren = new ArrayList<>();
    for (ElementDefinition e : children(true)) if (gt(e.getMin(), 0)) assignableChildren.add(e);
    List<ElementDefinition> assignableDescendents = new ArrayList<>();
    for (ElementDefinition ac : assignableChildren) assignableDescendents.addAll(ac.getAssignableDescendents());
    List<ElementDefinition> out = new ArrayList<>(assignableChildren);
    out.addAll(assignableDescendents);
    return out;
  }

  /** Finds and returns the element being sliced, or null if the element is not a slice. */
  public ElementDefinition slicedElement() {
    if (Js.truthy(getSliceName())) {
      ElementDefinition parent = parent();
      List<ElementDefinition> candidates = parent != null ? parent.children(true) : structDef.elements;
      String target = jsSlice(getId(), getId().lastIndexOf(':'));
      for (ElementDefinition e : candidates) {
        if (e.getId().equals(target)) return e;
      }
    }
    return null;
  }

  // ================================================================================================ unfold

  /**
   * If the element has a single type, graft the type's elements into this StructureDefinition as child elements. If
   * the element is sliced, unfold from the sliced element on the StructureDefinition. If the element is a content
   * reference, unfold from the referenced element on the StructureDefinition.
   *
   * @return the unfolded elements or an empty list if the type is multi-value or type can't be resolved.
   */
  @SuppressWarnings("unchecked")
  public List<ElementDefinition> unfold(Fishable fisher) {
    // if we have a choice element where id ends with [x], proceed if there is at most one profile, since it
    // expects to get [] back in that case; if we don't have a choice element, proceed with any number of profiles
    boolean isChoiceElement = getId().endsWith("[x]");
    List<ElementDefinitionType> type = getType();
    boolean proceedToUnfold = (type != null && type.size() == 1
        && (!isChoiceElement || (type.get(0).getProfile() == null ? 0 : type.get(0).getProfile().size()) <= 1))
        || Js.truthy(getContentReference());
    if (proceedToUnfold) {
      String profileToUse = null;
      List<String> availableProfiles = type != null && type.size() == 1 ? type.get(0).getProfile() : List.of();
      // if more than one profile is available, none of them can be used, since there's no way to decide.
      if (availableProfiles != null && availableProfiles.size() > 1) {
        logger.warn("Multiple profiles present on element " + getId()
            + ". Base element type will be used instead of any profiles.");
      } else if (availableProfiles != null && availableProfiles.size() == 1) {
        profileToUse = availableProfiles.get(0);
      }
      List<ElementDefinition> newElements = new ArrayList<>();
      if (Js.truthy(getContentReference())) {
        // Get the original resource JSON so we unfold unconstrained reference
        String sdType = structDef.getType();
        Object json = fisher.fishForFHIR(sdType, FishableType.Resource, FishableType.Logical);
        // contentReference elements will not contain a type field, so we must fish for the StructDef and
        // check the differential
        Object profileJson = fisher.fishForFHIR(structDef.getId(), FishableType.Profile);
        if (profileJson != null && hasProfileElementExtension((Map<String, Object>) profileJson)) {
          StructureDefinition def = structDef;
          // Content references start with #, slice that off to get the id of referenced element
          String contentRefId = getContentReferenceId();
          ElementDefinition referencedElement = def.findElement(contentRefId);
          newElements = requireList(cloneChildren(referencedElement, true));
          if (!newElements.isEmpty()) {
            // If we successfully unfolded, this element is no longer a content reference
            props.put("type", referencedElement.raw("type"));
            props.remove("contentReference");
          }
        } else if (json != null) {
          StructureDefinition def = StructureDefinition.fromJSON(json);
          // Content references start with #, slice that off to id of referenced element
          String contentRefId = getContentReferenceId();
          ElementDefinition referencedElement = def.findElement(contentRefId);
          newElements = requireList(cloneChildren(referencedElement, true));
          if (!newElements.isEmpty()) {
            // If we successfully unfolded, this element is no longer a content reference
            props.put("type", referencedElement.raw("type"));
            props.remove("contentReference");
          }
        }
      } else if (Js.truthy(getSliceName())) {
        // if this has a different profile than slicedElement, prefer to use that profile.
        // if this doesn't have a profile, or it has the same profile as slicedElement, then copy from
        // slicedElement.
        ElementDefinition slicedElement = slicedElement();
        if (slicedElement != null) {
          if (!Js.truthy(profileToUse)) {
            newElements = requireList(cloneChildren(slicedElement, false));
          } else {
            List<ElementDefinitionType> st = slicedElement.getType();
            if (st.size() == 1 && st.get(0).getProfile() != null && st.get(0).getProfile().size() == 1
                && st.get(0).getProfile().get(0).equals(profileToUse)) {
              newElements = requireList(cloneChildren(slicedElement, false));
            }
          }
        } else {
          // This can happen in the case of incomplete StructureDefinitions.
          // When this happens, all we can do is continue to the next step to try and add the child elements.
          logger.debug("Could not get sliced element for " + getId() + ": StructureDefinition "
              + Js.toStr(nullToUndef(structDef.getId())) + " may be missing slicing information");
        }
      }
      if (newElements.isEmpty()) {
        // If we have exactly one profile to use, use that, otherwise use the code
        String typeToFish = profileToUse != null ? profileToUse : getType().get(0).getCode();
        // Fish on standard types, but if this is a logical model, also fish on logical models
        List<FishableType> typesToFish = new ArrayList<>(List.of(FishableType.Resource, FishableType.Type,
            FishableType.Profile, FishableType.Extension));
        if (structDef != null && "logical".equals(structDef.getKind())) {
          typesToFish.add(0, FishableType.Logical);
        }
        FishableType[] typesArr = typesToFish.toArray(new FishableType[0]);
        // There could possibly be a |version appended to the type, so try to fish
        // for that version but fall back to any version if necessary
        Object json = FishingUtils.fishForFHIRBestVersion(fisher, typeToFish, null, typesArr);
        if (json == null && profileToUse != null) {
          logger.warn("SUSHI tried to find profile " + typeToFish
              + " but could not find it and instead will try to use " + getType().get(0).getCode());
          // If we tried to fish based on a profile and didn't find anything, fall back to the type
          json = fisher.fishForFHIR(getType().get(0).getCode(), typesArr);
        }
        if (json != null) {
          StructureDefinition def = StructureDefinition.fromJSON(json);
          if (Boolean.TRUE.equals(def.inProgress)) {
            String myName = structDef == null ? "undefined" : Js.toStr(nullToUndef(structDef.getName()));
            logger.debug("Warning: Circular relationship detected between " + myName + " and "
                + Js.toStr(nullToUndef(def.getName())) + ". As a result, it is possible that the definition of "
                + myName + " may be based on incomplete components of " + Js.toStr(nullToUndef(def.getName()))
                + ".");
          }
          newElements = new ArrayList<>();
          for (ElementDefinition e : def.elements.subList(1, def.elements.size())) {
            ElementDefinition eClone = e.clone();
            eClone.setId(replaceFirstLiteral(eClone.getId(), def.getPathType(), getId()));
            eClone.structDef = structDef;
            eClone.removeUninheritedExtensions();
            // Capture the original so that diffs only show what changed *after* unfolding
            eClone.captureOriginal();
            newElements.add(eClone);
          }
        }
      }
      if (!newElements.isEmpty()) {
        structDef.addElements(newElements);
        return newElements;
      }
    }
    return new ArrayList<>();
  }

  /** TS {@code newElements.length} on an undefined cloneChildren result throws a TypeError. */
  private static List<ElementDefinition> requireList(List<ElementDefinition> l) {
    if (l == null) throw jsTypeError("Cannot read properties of undefined (reading 'length')");
    return l;
  }

  /**
   * Returns an ElementDefinition's unfolded children, with the IDs altered and the original re-captured (null when
   * targetElement is null, as TS returns undefined).
   */
  private List<ElementDefinition> cloneChildren(ElementDefinition targetElement, boolean recaptureSliceExtensions) {
    if (targetElement == null) return null;
    List<ElementDefinition> out = new ArrayList<>();
    for (ElementDefinition e : targetElement.children()) {
      // Sometimes we want to avoid recapturing extensions, but if an element is not a slice
      // extension, we always capture it
      boolean shouldCaptureOriginal =
          recaptureSliceExtensions || e.getSliceName() == null || !e.getPath().endsWith(".extension");
      ElementDefinition eClone = e.clone(shouldCaptureOriginal);
      eClone.setId(replaceFirstLiteral(eClone.getId(), targetElement.getId(), getId()));
      eClone.structDef = structDef;
      eClone.removeUninheritedExtensions();
      if (shouldCaptureOriginal) {
        eClone.captureOriginal();
      } else if (eClone._original != null) {
        // When not recapturing, we need to update the _original.id to match the new id,
        // otherwise hasDiff() will return true due to the id difference alone
        eClone._original.setId(eClone.getId());
      }
      out.add(eClone);
    }
    return out;
  }

  /**
   * Checks a StructureDefinition's differential to determine if the profile element extension has been used on an
   * element.
   */
  @SuppressWarnings("unchecked")
  private boolean hasProfileElementExtension(Map<String, Object> profileJson) {
    String elementName = getContentReferenceId();

    Map<String, Object> differential = (Map<String, Object>) profileJson.get("differential");
    Map<String, Object> elementType = null;
    for (Object o : (List<Object>) differential.get("element")) {
      Map<String, Object> element = (Map<String, Object>) o;
      if (Objects.equals(element.get("id"), elementName)) {
        Object t = element.get("type");
        if (t instanceof List<?> tl && !tl.isEmpty()) elementType = (Map<String, Object>) tl.get(0);
        break;
      }
    }

    // If the type property is not present in the differential, the profile-element extension is not present
    if (elementType == null) {
      return false;
    }

    String profileCanonical;
    String extensionUrl;
    String targetElement;

    // If the profile and _profile arrays are not present, the profile-element extension is not present
    if (Js.truthy(elementType.get("profile")) && Js.truthy(elementType.get("_profile"))) {
      List<Object> underProfile = (List<Object>) elementType.get("_profile");
      int profileIndex = -1;
      for (int i = 0; i < underProfile.size(); i++) {
        Object profile = underProfile.get(i);
        if (Js.truthy(profile)) {
          List<Object> exts = (List<Object>) ((Map<String, Object>) profile).get("extension");
          if (exts == null) throw jsTypeError("Cannot read properties of undefined (reading 'some')");
          if (exts.stream().anyMatch(ElementDefinition::isProfileElementExt)) {
            profileIndex = i;
            break;
          }
        }
      }

      if (profileIndex == -1) {
        return false;
      }

      List<Object> exts = (List<Object>) ((Map<String, Object>) underProfile.get(profileIndex)).get("extension");
      int extensionIndex = -1;
      for (int i = 0; i < exts.size(); i++) {
        if (isProfileElementExt(exts.get(i))) {
          extensionIndex = i;
          break;
        }
      }
      Map<String, Object> ext = (Map<String, Object>) exts.get(extensionIndex);
      extensionUrl = (String) ext.get("url");
      targetElement = (String) ext.get("valueString");
      profileCanonical = (String) ((List<Object>) elementType.get("profile")).get(profileIndex);
    } else {
      return false;
    }

    return Objects.equals(profileCanonical, structDef.getUrl()) && PROFILE_ELEMENT_EXTENSION.equals(extensionUrl)
        && Objects.equals(targetElement, elementName);
  }

  private static boolean isProfileElementExt(Object o) {
    return o instanceof Map<?, ?> m && m.containsKey("url") && m.containsKey("valueString")
        && PROFILE_ELEMENT_EXTENSION.equals(m.get("url"));
  }

  private String getContentReferenceId() {
    String cr = getContentReference();
    if (Js.truthy(cr)) {
      return cr.substring(cr.indexOf('#') + 1);
    }
    return null;
  }

  /**
   * Unfolds a choice element's typed choices. The elements added to this element's structure definition are those
   * that are on the common ancestor of the available types (all types share Element).
   */
  public List<ElementDefinition> unfoldChoiceElementTypes(Fishable fisher) {
    List<String> allTypes = new ArrayList<>();
    for (ElementDefinitionType t : getType()) {
      if (t.getProfile() != null && !t.getProfile().isEmpty()) {
        allTypes.addAll(t.getProfile());
      } else {
        allTypes.add(t.getCode());
      }
    }
    List<List<String>> allTypeAncestry = new ArrayList<>();
    for (String t : allTypes) {
      List<String> urls = new ArrayList<>();
      for (Metadata l : getTypeLineage(t, fisher)) urls.add(l.url);
      allTypeAncestry.add(urls);
    }
    List<String> sharedAncestry = intersection(allTypeAncestry);
    if (!sharedAncestry.isEmpty()) {
      StructureDefinition commonAncestor = StructureDefinition.fromJSON(fisher.fishForFHIR(sharedAncestry.get(0)));
      List<ElementDefinition> newElements = new ArrayList<>();
      for (ElementDefinition e : commonAncestor.elements.subList(1, commonAncestor.elements.size())) {
        ElementDefinition eClone = e.clone();
        eClone.setId(replaceFirstLiteral(eClone.getId(), commonAncestor.getPathType(), getId()));
        eClone.structDef = structDef;
        eClone.removeUninheritedExtensions();
        // Capture the original so that diffs only show what changed *after* unfolding
        eClone.captureOriginal();
        newElements.add(eClone);
      }
      structDef.addElements(newElements);
      return newElements;
    } else {
      // this should not be reachable if the rest of the software is working correctly
      logger.error("Could not unfold choice element " + getId() + ": choices have no common ancestor.");
    }
    return new ArrayList<>();
  }

  /** lodash intersectionWith(...arrays) with no comparator: unique values of the first array present in all. */
  private static List<String> intersection(List<List<String>> arrays) {
    List<String> out = new ArrayList<>();
    if (arrays.isEmpty()) return out;
    outer:
    for (String v : arrays.get(0)) {
      if (out.contains(v)) continue;
      for (int i = 1; i < arrays.size(); i++) {
        if (!arrays.get(i).contains(v)) continue outer;
      }
      out.add(v);
    }
    return out;
  }

  public void removeUninheritedExtensions() {
    Common.removeMatchingExtensions(this, UNINHERITED_ED_EXTENSIONS, true);
    // NOTE: (TS) type and binding extension removal is commented out until the extension lists are confirmed
  }

  // ================================================================================================ slicing

  public Map<String, Object> sliceIt(String discriminatorType, String discriminatorPath) {
    return sliceIt(discriminatorType, discriminatorPath, null, null);
  }

  /**
   * Sets up slicing on an element by adding or modifying the element's slicing. If a matching slicing discriminator
   * already exists, it will be used.
   *
   * @param ordered null defaults to false for new slicings or keeps the existing value
   * @param rules ('closed' | 'open' | 'openAtEnd') null defaults to 'open' for new slicings or keeps the existing
   *     value
   * @throws SlicingDefinitionError if ordered is changed from true to false or rules are changed from closed to
   *     open/openAtEnd or openAtEnd is changed to open
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> sliceIt(String discriminatorType, String discriminatorPath, Boolean ordered,
      String rules) {
    if (Js.truthy(getSliceName())) {
      // slices should not have slicing info added to them
      logger.warn(getId() + " is a slice. Slices should not have slicing info added to them.");
    }
    Map<String, Object> slicing = getSlicing();
    if (!Js.truthy(slicing) || !Js.truthy(slicing.get("discriminator"))) {
      JsObject s = new JsObject();
      List<Object> discriminators = new ArrayList<>();
      discriminators.add(discriminator(discriminatorType, discriminatorPath));
      s.put("discriminator", discriminators);
      s.put("ordered", ordered != null ? ordered : false);
      s.put("rules", rules != null ? rules : "open");
      setSlicing(s);
    } else {
      // Ordered cannot be changed from true to false
      if (Js.truthy(slicing.get("ordered")) && Boolean.FALSE.equals(ordered)) {
        throw new SlicingDefinitionError("ordered", true, false);
      }
      // Rules cannot be changed from closed to open/openAtEnd or openAtEnd to open
      Object currentRules = nn(slicing.get("rules"));
      if (("closed".equals(currentRules) && !"closed".equals(rules))
          || ("openAtEnd".equals(currentRules) && "open".equals(rules))) {
        throw new SlicingDefinitionError("rules", currentRules, rules);
      }

      if (ordered != null && !ordered.equals(slicing.get("ordered"))) {
        slicing.put("ordered", ordered);
      }

      if (rules != null && !rules.equals(slicing.get("rules"))) {
        slicing.put("rules", rules);
      }

      List<Object> ds = (List<Object>) slicing.get("discriminator");
      boolean exists = false;
      for (Object o : ds) {
        Map<String, Object> d = (Map<String, Object>) o;
        if (Objects.equals(d.get("type"), discriminatorType) && Objects.equals(d.get("path"), discriminatorPath)) {
          exists = true;
          break;
        }
      }
      if (!exists) {
        ds.add(discriminator(discriminatorType, discriminatorPath));
      }
    }

    return getSlicing();
  }

  private static JsObject discriminator(String type, String path) {
    JsObject d = new JsObject();
    d.put("type", type == null ? Js.UNDEFINED : type);
    d.put("path", path == null ? Js.UNDEFINED : path);
    return d;
  }

  /**
   * Determines if this element's slicing was already closed before any rules were applied to it in the current
   * definition (for example, because a parent profile closed it).
   */
  public boolean hasInheritedClosedSlicing() {
    if (_original == null) return false;
    Map<String, Object> s = _original.getSlicing();
    return s != null && "closed".equals(s.get("rules"));
  }

  public ElementDefinition addSlice(String name) {
    return addSlice(name, null);
  }

  /**
   * Creates a new slice on the element.
   *
   * @param type the type of the new slice; if null it copies over this element's types
   * @return the new element representing the slice
   */
  @SuppressWarnings("unchecked")
  public ElementDefinition addSlice(String name, ElementDefinitionType type) {
    // to add a slice, an element must either have slicing information, or must itself be a slice.
    if (!Js.truthy(getSlicing()) && !Js.truthy(getSliceName())) {
      throw new SlicingNotDefinedError(getId(), name);
    }

    ElementDefinition slice = clone(true);
    slice.props.remove("slicing");
    slice.setId(Js.truthy(getSliceName()) ? getId() + "/" + name : getId() + ":" + name);

    // if a slice with the same id already exists, don't add it again
    ElementDefinition existingSlice = structDef.findElement(slice.getId());
    if (existingSlice != null) {
      throw new DuplicateSliceError(structDef.getName(), getId(), name);
    }

    // On a new slice, delete slice.min, slice.max, and slice.mustSupport. Then, reset slice.min and slice.max
    // so that they are always captured in diff
    slice.props.remove("min");
    slice.props.remove("max");
    slice.props.remove("mustSupport");

    // Capture the original so that the differential only contains changes from this point on.
    slice.captureOriginal();
    slice.setSliceName(Js.truthy(getSliceName()) ? getSliceName() + "/" + name : name);

    // Usually, when we slice, we do not inherit min cardinality, but rather make it 0.
    // BUT... if it's a choice that has already been constrained to a single type (thus, not really a choice),
    // and this is a type slice, then the slice is essentially the *same* as what is being sliced and therefore
    // should retain min card. See: https://github.com/FHIR/sushi/issues/596
    Map<String, Object> slicing = getSlicing();
    Map<String, Object> d0 = null;
    if (slicing != null && slicing.get("discriminator") instanceof List<?> ds) {
      if (ds.isEmpty()) throw jsTypeError("Cannot read properties of undefined (reading 'type')");
      d0 = (Map<String, Object>) ds.get(0);
    }
    if (getPath().endsWith("[x]") && getType().size() == 1 && d0 != null && "type".equals(d0.get("type"))
        && "$this".equals(d0.get("path"))) {
      slice.props.put("min", raw("min"));
    } else {
      slice.setMin(0.0);
    }

    slice.props.put("max", raw("max"));
    if (type != null) {
      List<ElementDefinitionType> l = new ArrayList<>();
      l.add(type);
      slice.setType(l);
    }
    structDef.addElement(slice);
    return slice;
  }

  public boolean isPartOfComplexExtension() {
    if (!"Extension".equals(structDef.getType())) return false;
    List<ElementDefinition> chain = new ArrayList<>();
    chain.add(this);
    List<ElementDefinition> parents = getAllParents();
    chain.addAll(parents.subList(0, Math.max(0, parents.size() - 1)));
    for (ElementDefinition ed : chain) {
      List<ElementDefinitionType> t = ed.getType();
      if (!(t != null && t.size() == 1 && "Extension".equals(t.get(0).getCode()) && ed.getSliceName() != null)) {
        return false;
      }
    }
    return true;
  }

  // ================================================================================================ clone / JSON

  /** Clones the current ElementDefinition, clearing the stored "original". */
  @Override public ElementDefinition clone() {
    return clone(true);
  }

  /**
   * Clones the current ElementDefinition, optionally clearing the stored "original". The clone shares the
   * StructureDefinition reference but has no tree relationships.
   */
  public ElementDefinition clone(boolean clearOriginal) {
    ElementDefinition clone = cloneDeep(new IdentityHashMap<>());
    clone.structDef = structDef;
    // Clear original if applicable
    if (clearOriginal) {
      clone.clearOriginal();
    }
    return clone;
  }

  /**
   * lodash cloneDeep of the element as {@link #clone} prepares it: deep copies of the own properties, the original
   * and the replacement-props bookkeeping; no structDef / tree references (TS nulls them before cloning).
   */
  @Override public ElementDefinition cloneDeep(IdentityHashMap<Object, Object> seen) {
    ElementDefinition c = new ElementDefinition(true);
    seen.put(this, c);
    c._privateId = _privateId;
    for (Map.Entry<String, Object> e : props.entrySet()) c.props.put(e.getKey(), Js.cloneDeep(e.getValue(), seen));
    c._original = _original == null ? null : (ElementDefinition) Js.cloneDeep(_original, seen);
    c._replacementProps = new ArrayList<>();
    for (List<String> rp : _replacementProps) c._replacementProps.add(new ArrayList<>(rp));
    return c;
  }

  /** Provides the FHIR-conformant JSON representation of this ElementDefinition. */
  @Override public JsObject toJSON() {
    JsObject j = new JsObject();
    for (String p : PROPS_AND_UNDERPROPS) {
      String prop = p;
      if (prop.endsWith("[x]")) {
        prop = resolveChoice(p);
      }
      if (prop != null && raw(prop) != Js.UNDEFINED) {
        if ("type".equals(prop)) {
          List<Object> types = new ArrayList<>();
          for (ElementDefinitionType t : getType()) types.add(t.toJSON());
          j.put("type", types);
        } else {
          j.put(prop, Js.cloneDeep(raw(prop)));
        }
      }
    }
    return j;
  }

  public static ElementDefinition fromJSON(Object json) {
    return fromJSON(json, true);
  }

  /**
   * Instantiates a new ElementDefinition from a FHIR-conformant JSON representation.
   *
   * @param captureOriginal indicate if original element should be captured for purposes of detecting
   *     differential.
   */
  @SuppressWarnings("unchecked")
  public static ElementDefinition fromJSON(Object jsonObj, boolean captureOriginal) {
    Map<String, Object> json = (Map<String, Object>) Js.cloneDeep(jsonObj);
    ElementDefinition ed = new ElementDefinition();
    for (String p : PROPS_AND_UNDERPROPS) {
      String prop = p;
      if (prop.endsWith("[x]")) {
        prop = resolveChoiceIn(json, p);
      }
      if (prop != null && json.containsKey(prop) && json.get(prop) != Js.UNDEFINED) {
        if ("type".equals(prop)) {
          List<ElementDefinitionType> types = new ArrayList<>();
          for (Object t : (List<Object>) json.get(prop)) types.add(ElementDefinitionType.fromJSON(t));
          ed.props.put("type", types);
        } else {
          ed.putRaw(prop, json.get(prop));
        }
      }
    }
    if (captureOriginal) {
      ed.captureOriginal();
    }
    return ed;
  }

  @Override public String toString() {
    return "ElementDefinition(" + _privateId + ")";
  }

  // ================================================================================================ JS helpers

  /** JS parseInt(string) (radix 10, or 16 with a 0x prefix): leading whitespace, sign, digits; else NaN. */
  static double parseInt(String s) {
    if (s == null) s = "undefined";
    int i = 0;
    int n = s.length();
    while (i < n && Js.isJsWhitespace(s.charAt(i))) i++;
    boolean neg = false;
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
      neg = s.charAt(i) == '-';
      i++;
    }
    int radix = 10;
    if (i + 1 < n && s.charAt(i) == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
      radix = 16;
      i += 2;
    }
    int start = i;
    while (i < n && Character.digit(s.charAt(i), radix) >= 0 && s.charAt(i) < 128) i++;
    if (i == start) return Double.NaN;
    double v = new java.math.BigInteger(s.substring(start, i), radix).doubleValue();
    return neg ? -v : v;
  }

  /** JS number coercion of a nullable Double (undefined → NaN). */
  private static double d(Double v) {
    return v == null ? Double.NaN : v;
  }

  private static boolean gt(Double v, double x) {
    return v != null && v > x;
  }

  private static Object nullToUndef(Object v) {
    return v == null ? Js.UNDEFINED : v;
  }

  /** Array.prototype.join (null elements render as ""). */
  private static String joinJs(List<String> l, String sep) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < l.size(); i++) {
      if (i > 0) sb.append(sep);
      if (l.get(i) != null) sb.append(l.get(i));
    }
    return sb.toString();
  }

  /** JS {@code s.replace(str, repl)} with a string pattern: the first occurrence, literally. */
  static String replaceFirstLiteral(String s, String target, String replacement) {
    int i = s.indexOf(target);
    if (i < 0) return s;
    return s.substring(0, i) + replacement + s.substring(i + target.length());
  }

  /** TS {@code obj?.[key]} over ElementDefinition / PropertyHolder / Map values (others: undefined). */
  private static Object propOf(Object obj, String key) {
    if (obj instanceof ElementDefinition ed) return nn(ed.raw(key));
    if (obj instanceof PropertyHolder ph) return ph.getProperty(key);
    if (obj instanceof Map<?, ?> m) return nn(m.get(key));
    return null;
  }

  /** TS {@code obj[key] = undefined}. */
  @SuppressWarnings("unchecked")
  private static void setUndefined(Object obj, String key) {
    if (obj instanceof ElementDefinition ed) ed.props.put(key, Js.UNDEFINED);
    else if (obj instanceof ElementDefinitionType t) t.rawProperties().put(key, Js.UNDEFINED);
    else if (obj instanceof PropertyHolder ph) ph.setProperty(key, Js.UNDEFINED);
    else if (obj instanceof Map<?, ?> m) ((Map<String, Object>) m).put(key, Js.UNDEFINED);
  }

  private static Metadata copyMetadata(Metadata m) {
    Metadata c = new Metadata();
    c.id = m.id;
    c.name = m.name;
    c.sdType = m.sdType;
    c.resourceType = m.resourceType;
    c.url = m.url;
    c.parent = m.parent;
    c.imposeProfiles = m.imposeProfiles;
    c._abstract = m._abstract;
    c.version = m.version;
    c.instanceUsage = m.instanceUsage;
    c.canBeTarget = m.canBeTarget;
    c.canBind = m.canBind;
    c.resourcePath = m.resourcePath;
    return c;
  }

  /** A JS TypeError (the message V8 would produce), for code paths where TS dereferences undefined. */
  static RuntimeException jsTypeError(String message) {
    return new Common.JsTypeError(message);
  }
}
