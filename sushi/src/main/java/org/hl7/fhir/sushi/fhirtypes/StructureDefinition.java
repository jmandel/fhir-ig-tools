package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.Common.getProp;
import static org.hl7.fhir.sushi.fhirtypes.Common.upperFirst;
import static org.hl7.fhir.sushi.utils.FshLogger.logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.hl7.fhir.sushi.errors.CannotResolvePathError;
import org.hl7.fhir.sushi.errors.ElementAlreadyDefinedError;
import org.hl7.fhir.sushi.errors.InvalidElementAccessError;
import org.hl7.fhir.sushi.errors.InvalidResourceTypeError;
import org.hl7.fhir.sushi.errors.InvalidTypeAccessError;
import org.hl7.fhir.sushi.errors.MissingSnapshotError;
import org.hl7.fhir.sushi.errors.ValidationError;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshReference;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.Metadata;
import org.hl7.fhir.sushi.utils.PathUtils;
import org.hl7.fhir.sushi.utils.SanitizeFilename;
import org.hl7.fhir.sushi.utils.ValidUrl;

// port of sushi/src/fhirtypes/StructureDefinition.ts (SUSHI 3.21.1)
/**
 * SUSHI's own StructureDefinition model (not org.hl7.fhir.r5.model: see PORTING.md D2). Each allowable top-level
 * property is the FHIR JSON value (JS value model) under its JSON name; typed accessors are provided for the common
 * ones, and {@link PropertyHolder} gives the generic access TS's {@code sd[prop]} has. The snapshot and differential
 * are represented by {@link #elements}. Top-level properties serialize in {@link #PROPS} order (with each "_prop"
 * right after "prop"), then snapshot/differential.
 */
public class StructureDefinition implements FhirResource, PropertyHolder, HasName, HasId {
  /** TS PROPS, verbatim: the serialization order of the top-level properties. */
  public static final List<String> PROPS = List.of("id", "meta", "implicitRules", "language", "text", "contained",
      "extension", "modifierExtension", "url", "identifier", "version", "name", "title", "status", "experimental",
      "date", "publisher", "contact", "description", "useContext", "jurisdiction", "purpose", "copyright", "keyword",
      "fhirVersion", "mapping", "kind", "abstract", "context", "contextInvariant", "type", "baseDefinition",
      "derivation");

  /** TS PROPS_AND_UNDERPROPS: each prop followed by "_prop". */
  public static final List<String> PROPS_AND_UNDERPROPS;

  static {
    List<String> l = new ArrayList<>();
    for (String p : PROPS) {
      l.add(p);
      l.add("_" + p);
    }
    PROPS_AND_UNDERPROPS = Collections.unmodifiableList(l);
  }

  private String resourceType = "StructureDefinition";
  /** The FHIR properties (PROPS and anything else assigned by path), in assignment order. */
  private final JsObject props = new JsObject();
  /**
   * The StructureDefinition's elements. Should not be added to directly: use {@link #addElement} /
   * {@link #addElements}.
   */
  public final List<ElementDefinition> elements = new ArrayList<>();
  /** A StructureDefinition instance of StructureDefinition itself. Needed for supporting escape syntax. */
  private StructureDefinition sdStructureDefinition;
  private List<Object> originalMapping = new ArrayList<>();
  /**
   * A flag indicating if the StructureDefinition is currently being processed. This allows us to log messages when
   * processing might be affected by circular dependencies. Null = never set (TS undefined).
   */
  public Boolean inProgress;

  /** Constructs a StructureDefinition with a root element. */
  public StructureDefinition() {
    // Every structure definition needs a root element
    ElementDefinition root = new ElementDefinition("");
    root.structDef = this;
    root.setMin(0.0);
    root.setMax("*");
    root.setMustSupport(false);
    root.setIsModifier(false);
    root.setIsSummary(false);
    this.elements.add(root);
  }

  // ---- PropertyHolder: TS sd[key] ------------------------------------------------------------------------------

  @Override public Object getProperty(String key) {
    return switch (key) {
      case "resourceType" -> resourceType;
      case "elements" -> elements;
      case "originalMapping" -> originalMapping;
      case "_sdStructureDefinition" -> sdStructureDefinition;
      case "inProgress" -> inProgress;
      default -> {
        Object v = props.get(key);
        yield v == Js.UNDEFINED ? null : v;
      }
    };
  }

  @Override public boolean hasProperty(String key) {
    return switch (key) {
      case "resourceType", "elements" -> true;
      case "originalMapping" -> originalMapping != null;
      case "_sdStructureDefinition" -> sdStructureDefinition != null;
      case "inProgress" -> inProgress != null;
      default -> props.containsKey(key) && props.get(key) != Js.UNDEFINED;
    };
  }

  @SuppressWarnings("unchecked")
  @Override public void setProperty(String key, Object value) {
    switch (key) {
      case "resourceType" -> resourceType = (String) value;
      case "elements" -> {
        elements.clear();
        if (value != null) elements.addAll((List<ElementDefinition>) value);
      }
      case "originalMapping" -> originalMapping = (List<Object>) value;
      case "_sdStructureDefinition" -> sdStructureDefinition = (StructureDefinition) value;
      case "inProgress" -> inProgress = (Boolean) value;
      default -> props.put(key, value);
    }
  }

  @Override public void deleteProperty(String key) {
    switch (key) {
      case "originalMapping" -> originalMapping = null;
      case "_sdStructureDefinition" -> sdStructureDefinition = null;
      case "inProgress" -> inProgress = null;
      case "resourceType", "elements" -> {}
      default -> props.remove(key);
    }
  }

  /** TS Object.keys(sd): initialised fields (resourceType, originalMapping, elements), then assignment order. */
  @Override public List<String> propertyKeys() {
    List<String> keys = new ArrayList<>();
    keys.add("resourceType");
    if (originalMapping != null) keys.add("originalMapping");
    keys.add("elements");
    for (String k : Js.ownKeys(props)) if (props.get(k) != Js.UNDEFINED) keys.add(k);
    if (sdStructureDefinition != null) keys.add("_sdStructureDefinition");
    if (inProgress != null) keys.add("inProgress");
    return keys;
  }

  // ---- typed accessors ----------------------------------------------------------------------------------------

  private String str(String key) {
    Object v = getProperty(key);
    return v == null ? null : (v instanceof String s ? s : Js.toStr(v));
  }

  @SuppressWarnings("unchecked")
  private List<Object> list(String key) {
    Object v = getProperty(key);
    return v instanceof List<?> l ? (List<Object>) l : null;
  }

  private void set(String key, Object v) {
    props.put(key, v);
  }

  public String getResourceType() { return resourceType; }
  @Override public String getId() { return str("id"); }
  @Override public void setId(String id) { set("id", id); }
  public Object getMeta() { return getProperty("meta"); }
  public void setMeta(Object meta) { set("meta", meta); }
  public Object getText() { return getProperty("text"); }
  public void setText(Object text) { set("text", text); }
  public List<Object> getContained() { return list("contained"); }
  public void setContained(List<Object> v) { set("contained", v); }
  public List<Object> getExtension() { return list("extension"); }
  public void setExtension(List<Object> v) { set("extension", v); }
  public List<Object> getModifierExtension() { return list("modifierExtension"); }
  public void setModifierExtension(List<Object> v) { set("modifierExtension", v); }
  public String getUrl() { return str("url"); }
  public void setUrl(String url) { set("url", url); }
  public List<Object> getIdentifier() { return list("identifier"); }
  public void setIdentifier(List<Object> v) { set("identifier", v); }
  public String getVersion() { return str("version"); }
  public void setVersion(String version) { set("version", version); }
  @Override public String getName() { return str("name"); }
  @Override public void setName(String name) { set("name", name); }
  public String getTitle() { return str("title"); }
  public void setTitle(String title) { set("title", title); }
  public String getStatus() { return str("status"); }
  public void setStatus(String status) { set("status", status); }
  public Boolean getExperimental() { return (Boolean) getProperty("experimental"); }
  public void setExperimental(Boolean v) { set("experimental", v); }
  public String getDate() { return str("date"); }
  public void setDate(String date) { set("date", date); }
  public String getPublisher() { return str("publisher"); }
  public void setPublisher(String v) { set("publisher", v); }
  public List<Object> getContact() { return list("contact"); }
  public void setContact(List<Object> v) { set("contact", v); }
  public String getDescription() { return str("description"); }
  public void setDescription(String v) { set("description", v); }
  public List<Object> getUseContext() { return list("useContext"); }
  public void setUseContext(List<Object> v) { set("useContext", v); }
  public List<Object> getJurisdiction() { return list("jurisdiction"); }
  public void setJurisdiction(List<Object> v) { set("jurisdiction", v); }
  public String getPurpose() { return str("purpose"); }
  public void setPurpose(String v) { set("purpose", v); }
  public String getCopyright() { return str("copyright"); }
  public void setCopyright(String v) { set("copyright", v); }
  public List<Object> getKeyword() { return list("keyword"); }
  public void setKeyword(List<Object> v) { set("keyword", v); }
  public String getFhirVersion() { return str("fhirVersion"); }
  public void setFhirVersion(String v) { set("fhirVersion", v); }
  /** The mapping array (mutable value-model list of mapping objects), or null. */
  public List<Object> getMapping() { return list("mapping"); }
  public void setMapping(List<Object> v) { set("mapping", v); }
  public String getKind() { return str("kind"); }
  public void setKind(String v) { set("kind", v); }
  /** TS {@code abstract}. */
  public Boolean getAbstract() { return (Boolean) getProperty("abstract"); }
  public void setAbstract(Boolean v) { set("abstract", v); }
  public List<Object> getContext() { return list("context"); }
  public void setContext(List<Object> v) { set("context", v); }
  public List<Object> getContextInvariant() { return list("contextInvariant"); }
  public void setContextInvariant(List<Object> v) { set("contextInvariant", v); }
  public String getType() { return str("type"); }
  public void setType(String type) { set("type", type); }
  public String getBaseDefinition() { return str("baseDefinition"); }
  public void setBaseDefinition(String v) { set("baseDefinition", v); }
  public String getDerivation() { return str("derivation"); }
  public void setDerivation(String v) { set("derivation", v); }

  // ---- behaviour ----------------------------------------------------------------------------------------------

  private static final Pattern NON_WS_COLON = Pattern.compile(
      "([^" + jsWsInner() + "]):([^" + jsWsInner() + "]+)");

  private static String jsWsInner() {
    String ws = org.hl7.fhir.sushi.fshtypes.Common.JS_WS;
    return ws.substring(1, ws.length() - 1);
  }

  public List<ValidationError> validate() {
    List<ValidationError> validationErrors = new ArrayList<>();
    for (ElementDefinition e : elements) {
      for (ValidationError err : e.validate()) {
        String transformedPath = NON_WS_COLON.matcher(e.getId()).replaceAll("$1[$2]");
        if (err.fshPath != null && err.fshPath.length() > 0) {
          transformedPath += " ^" + err.fshPath;
        }
        validationErrors.add(new ValidationError(err.issue, transformedPath, err.severity));
      }
    }
    return validationErrors;
  }

  /** The file name for serializing to disk. */
  @Override public String getFileName() {
    return SanitizeFilename.sanitize("StructureDefinition-" + Js.toStr(getRawId()) + ".json", "-");
  }

  private Object getRawId() {
    return props.containsKey("id") && props.get("id") != Js.UNDEFINED ? props.get("id") : Js.UNDEFINED;
  }

  /** TS get pathType(): the type, or its last URL segment for URL types (logical models). */
  public String getPathType() {
    String type = getType();
    return type.startsWith("http") ? type.substring(type.lastIndexOf('/') + 1) : type;
  }

  /** The StructureDefinition of StructureDefinition (cached). */
  public StructureDefinition getOwnStructureDefinition(Fishable fisher) {
    if (sdStructureDefinition == null) {
      sdStructureDefinition = StructureDefinition.fromJSON(
          fisher.fishForFHIR("StructureDefinition", FishableType.Resource));
    }
    return sdStructureDefinition;
  }

  /** Builds a regex from TS's partially-escaped id (only . [ ] / are escaped, the rest stays regex syntax). */
  private static Pattern idRegex(String escapedId, String tail) {
    try {
      return Pattern.compile("^" + escapedId + tail);
    } catch (PatternSyntaxException e) {
      // JS accepts some lone metacharacters ({, }) that Java rejects; they mean themselves there
      return Pattern.compile("^" + escapedId.replace("{", "\\{").replace("}", "\\}") + tail);
    }
  }

  private static boolean test(String escapedId, String tail, String s) {
    return idRegex(escapedId, tail).matcher(s).find();
  }

  /**
   * Adds an ElementDefinition to the StructureDefinition's elements, inserting it into the proper location based on
   * its ID and sliceName.
   */
  public void addElement(ElementDefinition element) {
    element.structDef = this;
    this.addElementToTree(element);
    if (element.treeParent != null && element.treeParent.treeChildren != null) {
      if (element.getSliceName() != null) {
        // a newly added element with a slicename should have at least one sibling: its sliced element
        // all slices come after all child elements.
        // so, insert a slice before the sliced element's next non-slice sibling that comes after it
        ElementDefinition slicedElement = element.slicedElement();
        int i = slicedElement != null ? this.elements.indexOf(slicedElement) : 0;
        String lastMatchId = this.elements.get(i).getId();
        for (; i < this.elements.size(); i++) {
          String currentId = this.elements.get(i).getId();
          if (test(escapePath(currentId), "[.:/]", element.getId())) {
            lastMatchId = currentId;
          } else {
            String lastMatchRegex = escapePath(lastMatchId);
            if (!test(lastMatchRegex, "[./:]", currentId)
                // If element is not a slice at this level, and the currentId is a slice, break to add children
                // before slices
                || (test(lastMatchRegex, "[.]", element.getId()) && test(lastMatchRegex, "[:/]", currentId))) {
              break;
            }
          }
        }
        this.elements.add(i, element);
      } else {
        // if this is the only child, splice in after parent
        // if this is not the only child, splice in after your next-older sibling's last child
        List<ElementDefinition> siblings = element.treeParent.treeChildren;
        if (siblings.size() == 1) {
          int parentIndex = this.elements.indexOf(element.treeParent);
          this.elements.add(parentIndex + 1, element);
        } else {
          ElementDefinition olderSibling = siblings.get(siblings.size() - 2);
          List<ElementDefinition> olderSiblingChildren = olderSibling.children();
          if (olderSiblingChildren.isEmpty()) {
            int olderSiblingIndex = this.elements.indexOf(olderSibling);
            this.elements.add(olderSiblingIndex + 1, element);
          } else {
            ElementDefinition lastChild = olderSiblingChildren.get(olderSiblingChildren.size() - 1);
            int lastChildIndex = this.elements.indexOf(lastChild);
            this.elements.add(lastChildIndex + 1, element);
          }
        }
      }
    }
  }

  public void addElementToTree(ElementDefinition element) {
    element.treeParent = element.parent();
    if (element.treeParent != null) {
      if (element.treeParent.treeChildren == null) {
        element.treeParent.treeChildren = new ArrayList<>();
      }
      element.treeParent.treeChildren.add(element);
    }
  }

  private static final Pattern PATH_META = Pattern.compile("[.\\[\\]/]");

  private static String escapePath(String id) {
    return PATH_META.matcher(id).replaceAll("\\\\$0");
  }

  /** Adds ElementDefinitions, each inserted into the proper location based on its ID. */
  public void addElements(List<ElementDefinition> elements) {
    if (elements == null) return;
    for (ElementDefinition e : elements) this.addElement(e);
  }

  /** Finds an element by its id (null if not found or id is empty). */
  public ElementDefinition findElement(String id) {
    if (id == null || id.isEmpty()) {
      return null;
    }
    for (ElementDefinition e : elements) {
      if (id.equals(e.getId())) return e;
    }
    return null;
  }

  private static boolean pathEquals(ElementDefinition e, String p) {
    return p.equals(e.getPath());
  }

  private static boolean pathStarts(ElementDefinition e, String p) {
    return e.getPath() != null && e.getPath().startsWith(p);
  }

  /** Finds an element by a FSH-compatible path (null if not found). */
  public ElementDefinition findElementByPath(String path, Fishable fisher) {
    // If the path already exists, get it and return the match
    // If !path just return the base parent element
    String fullPath = path != null && !path.isEmpty() && !path.equals(".") ? getPathType() + "." + path
        : getPathType();
    for (ElementDefinition e : elements) {
      if (pathEquals(e, fullPath) && !e.getId().contains(":")) return e;
    }

    // Parse the FSH Path into a form we can work with
    List<PathPart> parsedPath = PathUtils.parseFSHPath(path);

    String fhirPathString = getPathType();
    String previousPathPart = "";
    List<ElementDefinition> matchingElements = elements;
    List<ElementDefinition> newMatchingElements;
    // Iterate over the path, filtering out elements that do not match
    for (PathPart pathPart : parsedPath) {
      // Add the next part to the path, and see if we have matches on it
      fhirPathString += "." + pathPart.base;
      newMatchingElements = new ArrayList<>();
      for (ElementDefinition e : matchingElements) {
        if (pathStarts(e, fhirPathString + ".") || pathStarts(e, fhirPathString + ":")
            || pathEquals(e, fhirPathString)) {
          newMatchingElements.add(e);
        }
      }

      // Array for tracking newly added unfolded elements
      List<ElementDefinition> unfoldedElements = new ArrayList<>();
      if (newMatchingElements.isEmpty() && matchingElements.size() == 1) {
        // If there was previously only one match, we want to unfold that match and dig deeper into it
        ElementDefinition only = matchingElements.get(0);
        unfoldedElements = only.unfold(fisher);
        if (!unfoldedElements.isEmpty()) {
          // Only get the children that match our path
          newMatchingElements = filterPathStarts(unfoldedElements, fhirPathString);
          // If none of those matched, try unfolding the choice elements
          if (newMatchingElements.isEmpty() && only.getId().endsWith("[x]:" + previousPathPart)) {
            unfoldedElements = only.unfoldChoiceElementTypes(fisher);
            newMatchingElements = filterPathStarts(unfoldedElements, fhirPathString);
          }
        } else if (only.getId().endsWith("[x]")
            || (only.getPath().endsWith("[x]") && only.getSliceName() != null)) {
          // The element is a choice element or a slice of a choice element with multiple types.
          // Find the common ancestor of all types to navigate into sub-paths (e.g., .extension).
          unfoldedElements = only.unfoldChoiceElementTypes(fisher);
          newMatchingElements = filterPathStarts(unfoldedElements, fhirPathString);
        }
      }

      if (newMatchingElements.isEmpty()) {
        // If we fail to find any matches, try to find the appropriate [x] element
        // from previous matches or from newly unfolded elements
        // Ex: valueString -> value[x]
        List<ElementDefinition> candidates = new ArrayList<>(matchingElements);
        candidates.addAll(unfoldedElements);
        ElementDefinition matchingSlice = this.sliceMatchingValueX(fhirPathString, candidates);
        if (matchingSlice != null) {
          newMatchingElements.add(matchingSlice);
          newMatchingElements.addAll(matchingSlice.children());
          newMatchingElements.addAll(matchingSlice.getSlices());
          fhirPathString = matchingSlice.getPath();
        }
      }

      if (!newMatchingElements.isEmpty()) {
        // We succeeded in finding some matches, set them and keep going
        matchingElements = newMatchingElements;
      } else {
        // We got to a point where we couldn't find any matches, just return
        return null;
      }

      // After getting matches based on the 'base' part, we now filter according to 'brackets'
      if (pathPart.brackets != null) {
        ElementDefinition sliceElement = this.findMatchingSlice(fhirPathString, pathPart, matchingElements, fisher);
        if (sliceElement != null) {
          matchingElements = new ArrayList<>();
          matchingElements.add(sliceElement);
          matchingElements.addAll(sliceElement.children());
        } else {
          // If we didn't find a matching sliceElement, there must be a reference
          ElementDefinition matchingRefElement = this.findMatchingRefOrCanonical(pathPart, matchingElements);
          if (matchingRefElement != null) {
            matchingElements = new ArrayList<>();
            matchingElements.add(matchingRefElement);
            matchingElements.addAll(matchingRefElement.children());
          } else {
            // The bracket parts couldn't be resolved to a slice or a ref, so we failed to find an element
            return null;
          }
        }
      }

      // If there are no brackets, remove any slices that don't match exactly
      if (pathPart.brackets == null) {
        // Get the end of the current fhirPath (e.g. path1.path2.path3 -> path3)
        String[] fhirSplit = fhirPathString.split("\\.", -1);
        int pathDepth = fhirSplit.length - 1;
        String pathEnd = fhirSplit[pathDepth];
        List<ElementDefinition> filtered = new ArrayList<>();
        for (ElementDefinition e : matchingElements) {
          String[] idSplit = e.getId().split("\\.", -1);
          if (pathDepth >= idSplit.length) {
            throw new Common.JsTypeError("Cannot read properties of undefined (reading 'includes')");
          }
          String idEnd = idSplit[pathDepth];
          // If matchingElement id ends with pathEnd:, then it is a slice
          // pathPart.brackets is null, so keep nonslices (no ":" in idEnd)
          // and choice slices since valueString is equivalent to value[x]:valueString
          // but, make sure to not be fooled by slices where the slice name is the same as the element name
          if (!idEnd.contains(pathEnd + ":")
              || (idEnd.equals(pathEnd + ":" + pathPart.base) && !pathEnd.equals(pathPart.base))) {
            filtered.add(e);
          }
        }
        matchingElements = filtered;
      }
      previousPathPart = pathPart.base;
    }

    // We could still have child elements that are matching, if so filter them out now
    List<ElementDefinition> finalMatches = new ArrayList<>();
    for (ElementDefinition e : matchingElements) if (pathEquals(e, fhirPathString)) finalMatches.add(e);
    // If we have one and only one match, return it, else return undefined
    return finalMatches.size() == 1 ? finalMatches.get(0) : null;
  }

  private static List<ElementDefinition> filterPathStarts(List<ElementDefinition> els, String prefix) {
    List<ElementDefinition> out = new ArrayList<>();
    for (ElementDefinition e : els) if (pathStarts(e, prefix)) out.add(e);
    return out;
  }

  private static final Pattern HAS_X = Pattern.compile("\\[x\\]");

  /**
   * Changes a path's choice elements that are given as [x] but only have one type (value[x] constrained to string
   * becomes valueString). Choices with more than one type are left as is.
   */
  public String updatePathWithChoices(String path) {
    // if there are no [x] elements, there's nothing to update
    if (!HAS_X.matcher(path).find()) {
      return path;
    }
    List<PathPart> parsedPath = PathUtils.parseFSHPath(path);
    String buildPath = getPathType();
    for (PathPart pathPart : parsedPath) {
      buildPath += "." + pathPart.base;
      ElementDefinition matchingElement = this.findElement(buildPath);
      if (pathPart.base.endsWith("[x]") && matchingElement != null && matchingElement.getType() != null
          && matchingElement.getType().size() == 1) {
        pathPart.base = pathPart.base.replaceFirst(Pattern.quote("[x]"),
            Matcher.quoteReplacement(upperFirst(matchingElement.getType().get(0).getCode())));
      }
    }
    return PathUtils.assembleFSHPath(parsedPath);
  }

  public void setInstancePropertyByPath(String path, Object value, Fishable fisher) {
    setInstancePropertyByPath(path, value, fisher, new ArrayList<>());
  }

  /** Sets an instance property of an SD if possible. */
  public void setInstancePropertyByPath(String path, Object value, Fishable fisher,
      List<String> inlineResourceTypes) {
    if (path.startsWith("snapshot") || path.startsWith("differential")) {
      throw new InvalidElementAccessError(path);
    }
    if (path.equals("type") && !Common.strictEquals(value, getProperty("type"))) {
      throw new InvalidTypeAccessError();
    }
    Common.setPropertyOnDefinitionInstance(this, path, value, fisher, inlineResourceTypes);
  }

  public ElementDefinition newElement() {
    return newElement("$UNKNOWN");
  }

  /** Creates a new element and adds it to the StructureDefinition elements. */
  public ElementDefinition newElement(String name) {
    // Check if there already exists an element that is defined by an ancestor
    String id = getPathType() + "." + name;
    for (ElementDefinition e : elements) {
      if (id.equals(e.getId())) {
        throw new ElementAlreadyDefinedError(name, getId());
      }
    }
    ElementDefinition el = this.elements.get(0).newChildElement(name);
    this.addElement(el);
    return el;
  }

  /** Captures the current state of each element as the "original" (for differentials). */
  public void captureOriginalElements() {
    for (ElementDefinition e : elements) e.captureOriginal();
  }

  /** Clears the stored "original" state for each ElementDefinition. */
  public void clearOriginalElements() {
    for (ElementDefinition e : elements) e.clearOriginal();
  }

  /** Captures the current state of the mapping array (to tell added mappings from inherited ones). */
  public void captureOriginalMapping() {
    Object mapping = getProperty("mapping");
    @SuppressWarnings("unchecked") List<Object> cloned = (List<Object>) Js.cloneDeep(mapping);
    this.originalMapping = cloned != null ? cloned : new ArrayList<>();
  }

  private void buildMappingJSON(JsObject j, boolean snapshot) {
    // originalMapping might be undefined (if it was empty), so default to [] in that case
    // see: https://github.com/FHIR/sushi/issues/1616
    List<Object> original = this.originalMapping != null ? this.originalMapping : new ArrayList<>();
    List<Object> newMappings = new ArrayList<>();
    Object mappingV = getProperty("mapping");
    if (mappingV instanceof List<?> mapping) {
      // lodash differenceWith(mapping, original, isEqual)
      for (Object m : mapping) {
        boolean inOriginal = false;
        for (Object o : original) {
          if (Js.isEqual(m, o)) {
            inOriginal = true;
            break;
          }
        }
        if (!inOriginal) newMappings.add(m);
      }
    }
    List<Object> result;
    if (snapshot) {
      result = new ArrayList<>(newMappings);
      for (Object m : original) {
        boolean overridden = false;
        for (Object nm : newMappings) {
          if (Common.strictEquals(getProp(nm, "identity"), getProp(m, "identity"))
              && Common.strictEquals(getProp(nm, "name"), getProp(m, "name"))
              && Common.strictEquals(getProp(nm, "uri"), getProp(m, "uri"))) {
            overridden = true;
            break;
          }
        }
        if (!overridden) result.add(m);
      }
    } else {
      result = newMappings;
    }
    if (!result.isEmpty()) {
      j.put("mapping", result);
    }
  }

  @Override public Object toJSON() {
    return toJSON(true);
  }

  /** Exports the StructureDefinition to a properly formatted FHIR JSON representation. */
  @Override public Object toJSON(boolean snapshot) {
    JsObject j = new JsObject();
    j.put("resourceType", resourceType);
    // First handle properties that are just straight translations to JSON
    for (String prop : PROPS_AND_UNDERPROPS) {
      if (props.containsKey(prop) && props.get(prop) != Js.UNDEFINED) {
        if (prop.equals("mapping")) {
          this.buildMappingJSON(j, snapshot);
        } else {
          j.put(prop, props.get(prop));
        }
      }
    }

    // Now handle snapshot and differential
    if (snapshot) {
      List<Object> snapshotElements = new ArrayList<>(elements.size());
      for (ElementDefinition e : elements) snapshotElements.add(e.toJSON());
      j.put("snapshot", new JsObject().with("element", snapshotElements));
    }

    // Populate the differential
    List<Object> diffElements = new ArrayList<>();
    for (int idx = 0; idx < elements.size(); idx++) {
      ElementDefinition e = elements.get(idx);
      // If this is a logical model or a resource (derivation = 'specialization'),
      // we need to make sure the root element is included in the differential.
      if (e.hasDiff() || ("specialization".equals(getProperty("derivation")) && idx == 0)) {
        diffElements.add(e.calculateDiff().toJSON());
      }
    }

    // Never have an empty differential. It makes the IG Publisher mad.
    if (diffElements.isEmpty()) {
      diffElements.add(new JsObject().with("id", elements.get(0).getId()).with("path", elements.get(0).getPath()));
    }
    j.put("differential", new JsObject().with("element", diffElements));

    // If the StructureDefinition is in progress, persist that in the JSON so that when the Fisher retrieves it
    // from a package and converts to JSON, the inProgress state will be preserved. But NOT when it is false.
    if (Boolean.TRUE.equals(inProgress)) {
      j.put("inProgress", true);
    }

    return Common.orderedCloneDeep(j);
  }

  public static StructureDefinition fromJSON(Object json) {
    return fromJSON(json, true);
  }

  /**
   * Constructs a new StructureDefinition representing the passed in JSON (which must have a snapshot).
   * {@code captureOriginalElements}: capture original elements for purposes of detecting differentials.
   */
  public static StructureDefinition fromJSON(Object json, boolean captureOriginalElements) {
    // NOTE: Clone property assignment instead of cloning full JSON (to avoid cloning snapshot/differential twice)
    StructureDefinition sd = new StructureDefinition();
    // First handle properties that are just straight translations from JSON
    for (String prop : PROPS_AND_UNDERPROPS) {
      Object raw = Common.getRaw(json, prop);
      if (raw != Js.UNDEFINED) {
        sd.props.put(prop, Js.cloneDeep(raw));
      }
    }
    // Now handle the snapshots and (for now) just throw away the differential
    sd.elements.clear();
    Object snapshotObj = getProp(json, "snapshot");
    Object snapshotElements = getProp(snapshotObj, "element");
    if (Js.truthy(snapshotObj) && Js.truthy(snapshotElements)) {
      for (Object el : (List<?>) snapshotElements) {
        // No need to clone since ElementDefinition.fromJSON will clone el
        ElementDefinition ed = ElementDefinition.fromJSON(el, captureOriginalElements);
        ed.structDef = sd;
        sd.elements.add(ed);
        sd.addElementToTree(ed);
      }
    } else {
      throw new MissingSnapshotError(sd.getUrl());
    }
    // And finally add back the inProgress field if it's there
    if (Js.truthy(getProp(json, "inProgress"))) {
      sd.inProgress = true;
    }
    return sd;
  }

  /** TS {@code { assignedValue, pathParts, childPath? }}. */
  public record ValidateValueResult(Object assignedValue, List<PathPart> pathParts, String childPath) {
    public ValidateValueResult(Object assignedValue, List<PathPart> pathParts) {
      this(assignedValue, pathParts, null);
    }
  }

  public ValidateValueResult validateValueAtPath(String path, Object value, Fishable fisher) {
    return validateValueAtPath(path, value, fisher, new ArrayList<>(), null, false);
  }

  public ValidateValueResult validateValueAtPath(String path, Object value, Fishable fisher,
      List<String> inlineResourceTypes) {
    return validateValueAtPath(path, value, fisher, inlineResourceTypes, null, false);
  }

  public ValidateValueResult validateValueAtPath(String path, Object value, Fishable fisher,
      List<String> inlineResourceTypes, SourceInfo sourceInfo) {
    return validateValueAtPath(path, value, fisher, inlineResourceTypes, sourceInfo, false);
  }

  private static final Pattern DIGITS = Pattern.compile("^\\d+\\z");
  private static final Pattern PRIMITIVE_CODE = Pattern.compile("^[a-z][a-zA-Z0-9]*\\z");

  /**
   * Tests if it is possible to assign value to a path, but does not actually assign it. Use null as value to
   * validate just the path.
   *
   * @throws CannotResolvePathError when the path cannot be resolved to an element
   * @throws InvalidResourceTypeError when setting resourceType to an invalid value
   */
  public ValidateValueResult validateValueAtPath(String path, Object value, Fishable fisher,
      List<String> inlineResourceTypes, SourceInfo sourceInfo, boolean manualSliceOrdering) {
    if (inlineResourceTypes == null) inlineResourceTypes = new ArrayList<>();
    List<PathPart> pathParts = PathUtils.parseFSHPath(path);
    String currentPath = "";
    String previousPath;
    ElementDefinition currentElement = null;
    ElementDefinition previousElement = null;
    for (int i = 0; i < pathParts.size(); i++) {
      PathPart pathPart = pathParts.get(i);
      // Construct the path up to this point
      previousPath = currentPath;
      currentPath += (currentPath.isEmpty() ? "" : ".") + pathPart.base;
      // If we are indexing into an array, the last bracket should be numeric
      Integer arrayIndex = Common.getArrayIndex(pathPart);
      String sliceName = arrayIndex != null ? Common.getSliceName(pathPart) : null;
      if (arrayIndex != null) {
        // If it is a number, add all bracket info besides it back to path
        for (String p : pathPart.brackets.subList(0, pathPart.brackets.size() - 1)) currentPath += "[" + p + "]";
      } else if (pathPart.brackets != null) {
        // If it is not a number, then add all bracket info back to path
        for (String p : pathPart.brackets) currentPath += "[" + p + "]";
      }
      currentElement = this.findElementByPath(currentPath, fisher);
      // If the current element is sliced and the element is being accessed by numeric indices, warn to use the
      // sliceName in the following cases:
      // 1. The slicing is closed in which case a slice is certainly being accessed numerically
      // 2. The numeric index references a slice that will be preloaded - only applies when not enforcing named
      //    slice references
      if (currentElement != null && Js.truthy(currentElement.getSlicing()) && !Js.truthy(sliceName)
          && ("closed".equals(getProp(currentElement.getSlicing(), "rules"))
              || (!manualSliceOrdering
                  && currentElement.isPreloadedSlice(arrayIndex != null && arrayIndex != 0 ? arrayIndex : 0)))) {
        logger.warn("Sliced element " + currentElement.getId()
            + " is being accessed via numeric index. Use slice names in rule paths when possible.", sourceInfo);
      }
      // Allow for adding extension elements to the instance that are not on the SD
      if (currentElement == null && Common.isExtension(pathPart.base)) {
        // Get extension element (if currentPath is A.B.extension[C], get A.B.extension)
        String extensionPath = (previousPath.isEmpty() ? "" : previousPath + ".") + pathPart.base;
        ElementDefinition extensionElement = this.findElementByPath(extensionPath, fisher);
        // Get the extension being referred to
        String firstBracket = pathPart.brackets != null && !pathPart.brackets.isEmpty() ? pathPart.brackets.get(0)
            : null;
        Metadata extension = fisher.fishForMetadata(firstBracket, FishableType.Extension);
        if (extension != null && extensionElement != null) {
          // If the extension exists, add it as a slice to the SD so that we can assign it
          // This function is only called by InstanceExporter on copies of SDs, not those being exported
          if (!Js.truthy(extensionElement.getSlicing())) {
            extensionElement.sliceIt("value", "url");
          }
          // If an extension is referenced by url, we'll want to add the slice with it's id instead
          String extSliceName = ValidUrl.isUri(pathPart.brackets.get(0)) ? extension.id
              : pathPart.brackets.get(0);
          ElementDefinition slice = extensionElement.addSlice(extSliceName);
          ElementDefinitionType t0 = slice.getType().get(0);
          if (t0.getProfile() == null) {
            t0.setProfile(new ArrayList<>());
          }
          if (!t0.getProfile().contains(extension.url)) {
            t0.getProfile().add(extension.url);
          }
          // Search again for the desired element now that the extension is added
          currentElement = this.findElementByPath(currentPath, fisher);
        }
      }

      // If the element is an extension, and we found that extension via some other identifier than the sliceName
      // we want to replace the path to use the sliceName, since we can assume that was the user's intent
      if (Common.isExtension(pathPart.base) && pathPart.slices != null && currentElement != null
          && Js.truthy(currentElement.getSliceName())
          && !currentElement.getSliceName().equals(String.join("/", pathPart.slices))) {
        pathPart.slices = new ArrayList<>(List.of(currentElement.getSliceName().split("/", -1)));
        List<String> nb = new ArrayList<>(pathPart.slices);
        for (String b : pathPart.brackets) if (DIGITS.matcher(b).find()) nb.add(b);
        pathPart.brackets = nb;
      }

      if (currentElement == null && pathPart.base.equals("resourceType") && previousElement != null
          && previousElement.getType() != null && previousElement.getType().size() == 1) {
        String code = previousElement.getType().get(0).getCode();
        if (Common.isInheritedResource(valueAsString(value), code, fisher)) {
          return new ValidateValueResult(value, pathParts);
        } else {
          throw new InvalidResourceTypeError(valueAsString(value), code);
        }
      }

      if (currentElement == null || "0".equals(currentElement.getMax())
          || (arrayIndex != null && !"*".equals(currentElement.getMax())
              && (arrayIndex >= parseIntJs(currentElement.getMax()) || "1".equals(currentElement.getMax())))) {
        // We throw an error if the currentElement doesn't exist, has been zeroed out,
        // or is being incorrectly accessed as an array
        throw new CannotResolvePathError(path);
      }

      // Determine if base and/or current are arrays. Note that this is not perfect (if base or current max is
      // missing), but in practice, it appears to be sufficient.
      Object baseMaxV = getProp(currentElement.getBase(), "max");
      String baseMax = baseMaxV == null ? null : Js.toStr(baseMaxV);
      boolean baseIsArray = baseMax != null && !baseMax.equals("0") && !baseMax.equals("1");
      String curMax = currentElement.getMax();
      boolean currentIsArray = curMax != null && !curMax.equals("0") && !curMax.equals("1");
      // If the base is an array and we don't yet have an index or the currentElement is singular, make this index 0.
      if (baseIsArray && (arrayIndex == null || !currentIsArray)) {
        if (pathPart.brackets == null) pathPart.brackets = new ArrayList<>();
        pathPart.brackets.add("0");
      }

      // Types starting with a lowercase letter and with only alphanumeric characters are primitives.
      // Logical models can start with a lowercase letter, but they are URLs (so have non-alphanumerics).
      List<ElementDefinitionType> curTypes = currentElement.getType();
      if (curTypes != null && curTypes.size() == 1 && curTypes.get(0).getCode() != null
          && PRIMITIVE_CODE.matcher(curTypes.get(0).getCode()).find()) {
        pathPart.primitive = true;
      }

      // If we have inlineResourceTypes at this pathPart, we need to validate recursively using that
      // inline resource type, not the original type of the currentElement
      String inlineType = i < inlineResourceTypes.size() ? inlineResourceTypes.get(i) : null;
      if (!inlineResourceTypes.isEmpty() && Js.truthy(inlineType) && curTypes != null && curTypes.size() == 1
          && Common.isInheritedResource(inlineType, curTypes.get(0).getCode(), fisher, true)) {
        Object inlineResourceJSON = fisher.fishForFHIR(inlineType, FishableType.Resource, FishableType.Profile);
        if (inlineResourceJSON != null) {
          StructureDefinition inlineResourceStructDef = StructureDefinition.fromJSON(inlineResourceJSON);
          String inlinePath = PathUtils.assembleFSHPath(pathParts.subList(i + 1, pathParts.size()));
          try {
            ValidateValueResult validatedInlineResource = inlineResourceStructDef.validateValueAtPath(inlinePath,
                value, fisher, new ArrayList<>(inlineResourceTypes.subList(i + 1, inlineResourceTypes.size())));
            List<PathPart> combined = new ArrayList<>(pathParts.subList(0, i + 1));
            combined.addAll(validatedInlineResource.pathParts());
            return new ValidateValueResult(validatedInlineResource.assignedValue(), combined);
          } catch (CannotResolvePathError e) {
            // Catch the error from the recursive call so the original path can be logged
            throw new CannotResolvePathError(path);
          }
        }
      }

      previousElement = currentElement;
    }

    Object assignedValue = null;
    // Assigned resources cannot be assigned by pattern[x]/fixed[x], so we must set assignedValue directly
    if (value instanceof InstanceDefinition inst) {
      // checkAssignInlineInstance will throw if it fails
      assignedValue = currentElement.checkAssignInlineInstance(inst, fisher).toJSON();
    } else {
      String originalKey = firstFixedOrPatternKey(currentElement);
      Object originalValue = originalKey == null ? null : Common.getRaw(currentElement, originalKey);
      // assignValue will throw if it fails, but skip the check if value is null
      if (value != null) {
        // exactly must be true so that we always test assigning with the more strict fixed[x] approach
        currentElement.assignValue(value, true, fisher);
      }
      // If there is a fixedValue or patternValue, find it and return it
      String key = firstFixedOrPatternKey(currentElement);
      if (key != null) {
        assignedValue = currentElement.getProperty(key);
        currentElement.deleteProperty(key);
        if (originalKey != null && originalValue != Js.UNDEFINED) {
          currentElement.setProperty(originalKey, originalValue);
        }
      }
      // when key ends with CodeableReference, and the original value is a FshReference or FshCode (and not the
      // entire CodeableReference object) we add an extra field to the return value that indicates that we are
      // doing CodeableReference tricks
      if (key != null && key.endsWith("CodeableReference")) {
        if (value instanceof FshReference) {
          return new ValidateValueResult(assignedValue, pathParts, "reference");
        } else if (value instanceof FshCode) {
          return new ValidateValueResult(assignedValue, pathParts, "concept");
        }
      }
    }

    return new ValidateValueResult(assignedValue, pathParts);
  }

  private static String valueAsString(Object value) {
    return value == null ? null : (value instanceof String s ? s : Js.toStr(value));
  }

  private static String firstFixedOrPatternKey(ElementDefinition e) {
    for (String k : e.propertyKeys()) {
      if (k.startsWith("pattern") || k.startsWith("fixed")) return k;
    }
    return null;
  }

  /** JS parseInt for a cardinality max ("5" -> 5; non-numeric -> NaN, which compares false). */
  private static double parseIntJs(String s) {
    if (s == null) return Double.NaN;
    Matcher m = Pattern.compile("^[\\s]*([+-]?\\d+)").matcher(s);
    return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
  }

  /**
   * Looks for a matching choice element. If the choice has no existing slice, we slice it and add to the existing
   * StructureDefinition. If the choice has an existing slice, we return that.
   */
  private ElementDefinition sliceMatchingValueX(String fhirPath, List<ElementDefinition> elements) {
    ElementDefinitionType matchingType = null;
    List<ElementDefinition> xElements = new ArrayList<>();
    for (ElementDefinition e : elements) if (e.getPath().endsWith("[x]")) xElements.add(e);
    List<ElementDefinition> matchingXElements = new ArrayList<>();
    for (ElementDefinition e : xElements) {
      List<ElementDefinitionType> types = e.getType() == null ? List.of() : e.getType();
      for (ElementDefinitionType t : types) {
        String p = e.getPath();
        if ((p.substring(0, p.length() - 3) + upperFirst(t.getCode())).equals(fhirPath)) {
          matchingType = t;
          matchingXElements.add(e);
          break;
        }
      }
    }
    // If the only match is the choice[x] element itself, and it's already been restricted to just a single type,
    // and there are no existing slices (for this type or otherwise), just return that instead of creating an
    // unnecessary slice.
    if (matchingXElements.size() == 1 && matchingXElements.get(0).getSliceName() == null
        && matchingXElements.get(0).getType() != null && matchingXElements.get(0).getType().size() == 1
        && xElements.stream().filter(e -> e.getPath().equals(matchingXElements.get(0).getPath())).count() == 1) {
      return matchingXElements.get(0);
    } else if (!matchingXElements.isEmpty()) {
      // Otherwise we want a slice representing the specific type
      String sliceName = fhirPath.substring(fhirPath.lastIndexOf('.') + 1);
      for (ElementDefinition c : matchingXElements) {
        // if we have already have a matching slice, we want to return it
        if (sliceName.equals(c.getSliceName())) return c;
      }
      // if we do not have a matching slice, we want to slice the first match
      ElementDefinition matchingXElement = matchingXElements.get(0);
      // If we find a matching [x] element, we need to slice it to create the child element
      // NOTE: The spec is somewhat inconsistent on handling choice slicing, we decided on this approach per
      // consistency with 4.0.1 observation-vitalsigns profiles and per this post
      // https://blog.fire.ly/2019/09/13/type-slicing-in-fhir-r4/.
      Object slicing = matchingXElement.getSlicing();
      Object ordered = getProp(slicing, "ordered");
      Object rules = getProp(slicing, "rules");
      matchingXElement.sliceIt("type", "$this", (Boolean) ordered, rules == null ? null : Js.toStr(rules));
      // Get the sliceName for the new element
      return matchingXElement.addSlice(sliceName, matchingType);
    }
    return null;
  }

  /** Looks for a slice within the set of elements that matches the fhirPath. */
  private ElementDefinition findMatchingSlice(String fhirPathString, PathPart pathPart,
      List<ElementDefinition> elements, Fishable fisher) {
    ElementDefinition matchingSlice = null;
    String joined = String.join("/", pathPart.brackets);
    for (ElementDefinition e : elements) {
      if (pathEquals(e, fhirPathString) && joined.equals(e.getSliceName())) {
        matchingSlice = e;
        break;
      }
    }
    if (matchingSlice == null && pathPart.brackets.size() == 1) {
      // If the current element is a child of a slice, the match may exist on the original
      // sliced element, search for that here
      for (ElementDefinition e : new ArrayList<>(elements)) {
        ElementDefinition connectedSliceElement = e.findConnectedSliceElement();
        ElementDefinition matchingConnectedSlice = null;
        if (connectedSliceElement != null) {
          for (ElementDefinition s : connectedSliceElement.getSlices()) {
            if (pathEquals(s, fhirPathString) && joined.equals(s.getSliceName())) {
              matchingConnectedSlice = s;
              break;
            }
          }
        }
        if (matchingConnectedSlice != null) {
          ElementDefinition newSlice = matchingConnectedSlice.clone(false);
          newSlice.setId(e.getId() + ":" + matchingConnectedSlice.getSliceName());
          newSlice.structDef = this;
          if (!Js.truthy(e.getSlicing())) e.setSlicing(connectedSliceElement.getSlicing());
          this.addElement(newSlice);
          return newSlice;
        }
      }

      // If we don't find a match, search predefined extensions for a match
      Object sliceDefinition = fisher.fishForFHIR(pathPart.brackets.get(0), FishableType.Extension);
      Object sliceUrl = getProp(sliceDefinition, "url");
      if (Js.truthy(sliceUrl)) {
        for (ElementDefinition e : elements) {
          List<ElementDefinitionType> types = e.getType();
          String profile0 = types != null && !types.isEmpty() && types.get(0) != null
              && types.get(0).getProfile() != null && !types.get(0).getProfile().isEmpty()
                  ? types.get(0).getProfile().get(0)
                  : null;
          if (profile0 != null && Common.canonicalsAreEqualIgnoringVersion(profile0, Js.toStr(sliceUrl))
              && e.getSliceName() != null) {
            matchingSlice = e;
            break;
          }
        }
        // If the path explicitly requested a version and the matched slice pins a different one,
        // the author asked for something the profile does not allow, so warn. Stay silent when the
        // path is unversioned, since the author expressed no version preference and the version
        // pinned by a published package is usually not something they can change.
        if (matchingSlice != null) {
          String requestedVersion = afterFirstPipe(pathPart.brackets.get(0));
          String pinnedVersion = afterFirstPipe(matchingSlice.getType().get(0).getProfile().get(0));
          if (!requestedVersion.isEmpty() && !pinnedVersion.isEmpty() && !requestedVersion.equals(pinnedVersion)) {
            logger.warn("Extension slice " + matchingSlice.getSliceName() + " on " + matchingSlice.getPath()
                + " pins version " + pinnedVersion + " of " + Js.toStr(sliceUrl) + ", but version "
                + requestedVersion + " was requested.");
          }
        }
      }
    }
    if (matchingSlice == null && pathPart.brackets.stream().allMatch(p -> p.equals(JsUri.decodeURIComponent(p)))
        && pathPart.brackets.stream().anyMatch(p -> !p.equals(JsUri.encodeURIComponent(p)))) {
      PathPart encodedPathPart = pathPart.copy();
      List<String> enc = new ArrayList<>();
      for (String p : encodedPathPart.brackets) enc.add(JsUri.encodeURIComponent(p));
      encodedPathPart.brackets = enc;
      return this.findMatchingSlice(fhirPathString, encodedPathPart, elements, fisher);
    }
    // NOTE: This function will assume the 'brackets' field contains information about slices. Even if you search
    // for foo[sliceName][refName], this will try to find a re-slice sliceName/refName. To find the matching element
    // for foo[sliceName][refName], you must use the findMatchingRef function.
    return matchingSlice;
  }

  /** {@code s.split('|').slice(1).join('|')}. */
  private static String afterFirstPipe(String s) {
    int i = s.indexOf('|');
    return i < 0 ? "" : s.substring(i + 1);
  }

  /** The slice names of choices made obsolete by constraining baseElement's types (that have rules applied). */
  public List<String> findObsoleteChoices(ElementDefinition baseElement, List<ElementDefinitionType> oldTypes) {
    // first, find all the elements representing choices for the same choice element
    ElementDefinition baseParent = baseElement.parent();
    String parentSlice = baseParent == null ? null : baseParent.getSliceName();
    String baseSliceName = baseElement.getSliceName();
    List<ElementDefinition> choiceElements = new ArrayList<>();
    for (ElementDefinition e : elements) {
      ElementDefinition ep = e.parent();
      String eParentSlice = ep == null ? null : ep.getSliceName();
      if (e.getPath().equals(baseElement.getPath())
          && (parentSlice == null || parentSlice.equals(eParentSlice)
              || (eParentSlice != null && eParentSlice.startsWith(parentSlice + "/")))
          // When baseElement is itself a named slice, only consider its sub-slices, not sibling slices
          && (baseSliceName == null || (e.getSliceName() != null && e.getSliceName().startsWith(baseSliceName + "/")))) {
        choiceElements.add(e);
      }
    }
    List<ElementDefinition> matchedThings = new ArrayList<>();
    String basePath = baseElement.getPath();
    String lastPart = basePath.substring(basePath.lastIndexOf('.') + 1);
    String desiredSliceName = lastPart.substring(0, Math.max(0, lastPart.length() - 3));
    // an obsolete choice is one where:
    // 1. the choice represents a type being constrained out, and
    // 2. a rule has been applied to that choice
    for (ElementDefinitionType oldType : oldTypes) {
      for (ElementDefinition e : choiceElements) {
        if ((desiredSliceName + upperFirst(oldType.getCode())).equals(e.getSliceName()) && e.hasDiff()) {
          matchedThings.add(e);
        }
      }
    }
    List<String> names = new ArrayList<>();
    for (ElementDefinition e : matchedThings) names.add(e.getSliceName());
    return names;
  }

  /** Looks for a Reference or canonical type element within the set of elements that matches the path part. */
  private ElementDefinition findMatchingRefOrCanonical(PathPart pathPart, List<ElementDefinition> elements) {
    for (ElementDefinition e : elements) {
      // If we have foo[a][b][c], and c is the ref, we need to find an element with sliceName = a/b
      if (pathPart.brackets.size() == 1 || String.join("/", pathPart.brackets.subList(0, pathPart.brackets.size()
          - 1)).equals(e.getSliceName())) {
        List<ElementDefinitionType> types = e.getType();
        // TS returns from inside the for..of: only the first type is considered
        if (types != null && !types.isEmpty()) {
          ElementDefinitionType t = types.get(0);
          if (("Reference".equals(t.getCode()) || "canonical".equals(t.getCode())) && t.getTargetProfile() != null) {
            String name = pathPart.brackets.get(pathPart.brackets.size() - 1);
            for (String tp : t.getTargetProfile()) {
              // http://hl7.org/fhir/us/core/StructureDefinition/profile|3.0.0 -> profile|3.0.0 -> profile
              String[] slashParts = tp.split("/", -1);
              String tpName = slashParts[slashParts.length - 1].split("\\|", -1)[0];
              // find() returns the matching string: an empty name is falsy
              if (tpName.equals(name) && !tp.isEmpty()) return e;
            }
          }
        }
      }
    }
    return null;
  }

  /** The name of the reference/canonical referred to by a path with brackets, or null. */
  public String getReferenceOrCanonicalName(String path, ElementDefinition element) {
    List<PathPart> parsedPath = PathUtils.parseFSHPath(path);
    PathPart pathEnd = parsedPath.get(parsedPath.size() - 1);
    if (pathEnd.brackets != null) {
      ElementDefinition refElement = this.findMatchingRefOrCanonical(pathEnd, List.of(element));
      if (refElement != null) {
        return pathEnd.brackets.get(pathEnd.brackets.size() - 1);
      }
    }
    return null;
  }

  /** TS StructureDefinitionMapping / StructureDefinitionContext are plain value-model objects. */
  public static Map<String, Object> mapping(String identity, String uri, String name, String comment) {
    JsObject m = new JsObject().with("identity", identity);
    if (uri != null) m.put("uri", uri);
    if (name != null) m.put("name", name);
    if (comment != null) m.put("comment", comment);
    return m;
  }
}
