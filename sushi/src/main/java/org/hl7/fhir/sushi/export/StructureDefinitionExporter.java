package org.hl7.fhir.sushi.export;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.ClosedSlicingError;
import org.hl7.fhir.sushi.errors.DuplicateSliceError;
import org.hl7.fhir.sushi.errors.InvalidElementForSlicingError;
import org.hl7.fhir.sushi.errors.InvalidExtensionParentError;
import org.hl7.fhir.sushi.errors.InvalidFHIRIdError;
import org.hl7.fhir.sushi.errors.InvalidLogicalParentError;
import org.hl7.fhir.sushi.errors.InvalidResourceParentError;
import org.hl7.fhir.sushi.errors.MismatchedBindingTypeError;
import org.hl7.fhir.sushi.errors.MismatchedTypeError;
import org.hl7.fhir.sushi.errors.ParentDeclaredAsIdError;
import org.hl7.fhir.sushi.errors.ParentDeclaredAsNameError;
import org.hl7.fhir.sushi.errors.ParentNotDefinedError;
import org.hl7.fhir.sushi.errors.ParentNotProvidedError;
import org.hl7.fhir.sushi.errors.ValidationError;
import org.hl7.fhir.sushi.errors.WithSource;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.AllowedRules;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.ExtensionContext;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.FshStructure;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.Resource;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.BindingRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.FlagRule;
import org.hl7.fhir.sushi.fshtypes.rules.ObeysRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FishingUtils;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.MasterFisher;
import org.hl7.fhir.sushi.utils.Metadata;
import org.hl7.fhir.sushi.utils.PathUtils;
import org.hl7.fhir.sushi.utils.ValidUrl;

// port of sushi/src/export/StructureDefinitionExporter.ts (SUSHI 3.21.1)
/**
 * The StructureDefinitionExporter is the class for exporting Logical models, Profiles, Extensions, and Resources. The
 * operations and structure of these exporters are very similar, so they currently share an exporter.
 */
public class StructureDefinitionExporter implements Fishable {
  private static final FshLogger logger = FshLogger.logger;

  // Extensions that should not be inherited by derived profiles.
  // See: https://jira.hl7.org/browse/FHIR-28441
  static final List<String> UNINHERITED_SD_EXTENSIONS = List.of(
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm-no-warnings",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-interface",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-normative-version",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-applicable-version",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-category",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-security-category",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-summary",
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-wg",
      "http://hl7.org/fhir/StructureDefinition/replaces",
      "http://hl7.org/fhir/StructureDefinition/resource-approvalDate",
      "http://hl7.org/fhir/StructureDefinition/resource-effectivePeriod",
      "http://hl7.org/fhir/StructureDefinition/resource-lastReviewDate");

  /** fhirtypes/primitiveTypes.ts idRegex. */
  private static final Pattern ID_REGEX = Pattern.compile("^[A-Za-z0-9\\-.]{1,64}\\z");
  /** eld-19: JS /^[^\s.,:;\\'"\/|?!@#$%&*()\[\]{}]+(\[x\])?$/ (JS \s = Unicode whitespace). */
  private static final Pattern ELD19 = Pattern.compile("^(?:(?!" + org.hl7.fhir.sushi.fshtypes.Common.JS_WS
      + ")[^.,:;\\\\'\"/|?!@#$%&*()\\[\\]{}])+(\\[x\\])?\\z");
  /** eld-20 */
  private static final Pattern ELD20 = Pattern.compile("^[A-Za-z][A-Za-z0-9]*(\\[x\\])?\\z");

  /** One deferred caret rule (TS {rule, tryFish, originalErr?}). */
  public record DeferredCaretRule(CaretValueRule rule, boolean tryFish, MismatchedTypeError originalErr) {}

  /** One known binding rule (TS {rule, isInline, url?}). */
  public record KnownBindingRule(BindingRule rule, boolean isInline, String url) {}

  /** TS Map keyed by StructureDefinition object identity; StructureDefinition does not override equals. */
  public final Map<StructureDefinition, List<DeferredCaretRule>> deferredCaretRules = new LinkedHashMap<>();
  public final Map<StructureDefinition, List<KnownBindingRule>> knownBindingRules = new LinkedHashMap<>();
  private List<String> typeCharacteristicCodes;
  private String commaSeparatedCharacteristics;

  private final FSHTank tank;
  private final Package pkg;
  private final MasterFisher fisher;

  public StructureDefinitionExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.tank = tank;
    this.pkg = pkg;
    this.fisher = fisher;
    Object typeCharacteristicCodeSystem = fishForFHIR(Common.TYPE_CHARACTERISTICS_CODE, FishableType.CodeSystem);
    if (typeCharacteristicCodeSystem != null) {
      typeCharacteristicCodes = Common.getAllConcepts(typeCharacteristicCodeSystem);
      List<String> supportedCharacteristics = new ArrayList<>(typeCharacteristicCodes);
      // special case to make sure that we report that #can-be-target is a supported code,
      // even if it's not in the code system.
      if (!supportedCharacteristics.contains("can-be-target")) {
        supportedCharacteristics.add("can-be-target");
      }
      List<String> quoted = new ArrayList<>();
      for (String code : supportedCharacteristics) {
        quoted.add("\"#" + code + "\"");
      }
      commaSeparatedCharacteristics = String.join(", ", quoted);
    }
  }

  /**
   * Checks generated resources for any custom resources which are not in the http://hl7.org/fhir/StructureDefinition
   * namespace, and are therefore not conformant.
   *
   * <p>TS colours the box with chalk; chalk emits no escape codes when stdout is not a TTY (how the IG Publisher
   * runs SUSHI), so the box is built uncoloured here.
   */
  private void warnOnNonConformantResourceDefinitions() {
    List<StructureDefinition> nonConformantResources = new ArrayList<>();
    for (StructureDefinition r : pkg.resources) {
      if (!r.getUrl().startsWith("http://hl7.org/fhir/StructureDefinition/")) {
        nonConformantResources.add(r);
      }
    }
    if (!nonConformantResources.isEmpty()) {
      int maxLength = 61;
      List<String> message = new ArrayList<>();
      message.add("\n╭─────────────────────────────────────────────────────────────────╮");
      message.add("│ Detected the following non-conformant Resource definitions:     │");
      for (StructureDefinition r : nonConformantResources) {
        String printableName = r.getName().length() > maxLength ? r.getName().substring(0, maxLength - 3) + "..." : r.getName();
        message.add("│" + padEnd(" - " + printableName, 65) + "│");
      }
      message.add("│                                                                 │");
      message.add("│ Resources not in the \"http://hl7.org/fhir/StructureDefinition\"  │");
      message.add("│ namespace are not conformant to the FHIR specification.         │");
      message.add("│ These resources are incompatible with standard XML/JSON         │");
      message.add("│ schemas, public test servers, public registries, standard       │");
      message.add("│ validation, and standard publication tooling. These resources   │");
      message.add("│ should not be used for inter-organizational data exchange.      │");
      message.add("╰─────────────────────────────────────────────────────────────────╯\n");
      logger.warn(String.join("\n", message));
    }
  }

  /** lodash padEnd (string length in UTF-16 units; lodash counts astral symbols as one -- names are ASCII here). */
  private static String padEnd(String s, int length) {
    StringBuilder sb = new StringBuilder(s);
    while (sb.length() < length) {
      sb.append(' ');
    }
    return sb.toString();
  }

  /**
   * Processes the fshDefinition's parent, validating it according to its type. Returns the parent's
   * StructureDefinition as the basis for the new StructureDefinition for the provided fshDefinition.
   */
  private StructureDefinition getStructureDefinition(FshStructure fshDefinition) {
    if (fshDefinition.parent == null || fshDefinition.parent.isEmpty()) {
      // Handle cases where the parent is not specified by throwing an error.
      // - Profile has a hard requirement to define a parent and does not have a default parent defined in the
      //   Profile class. Therefore, we need to throw this error.
      // - Extension, Logical, and Resource all have a default parent, so we should never get to this point where it
      //   is not defined.
      throw new ParentNotProvidedError(fshDefinition.name, fshDefinition.sourceInfo);
    }

    if (Objects.equals(fshDefinition.name, fshDefinition.parent)
        || Objects.equals(fshDefinition.getId(), fshDefinition.parent)) {
      // We want to ensure that the FSH definition does not set either the name or the id to be the same value as
      // the parent, and suggest the parent's URL if we can (fishing only for resources and types: fishing for the
      // definition's own type would only find itself).
      Metadata possibleParentMeta = fishForMetadata(fshDefinition.parent, FishableType.Resource,
          FishableType.Type);
      String possibleUrl = possibleParentMeta == null ? null : possibleParentMeta.url;

      if (Objects.equals(fshDefinition.name, fshDefinition.parent)) {
        throw new ParentDeclaredAsNameError(fshDefinition.constructorName(), fshDefinition.name,
            fshDefinition.sourceInfo, possibleUrl);
      }

      if (Objects.equals(fshDefinition.getId(), fshDefinition.parent)) {
        throw new ParentDeclaredAsIdError(fshDefinition.constructorName(), fshDefinition.name,
            fshDefinition.getId(), fshDefinition.sourceInfo, possibleUrl);
      }
    }

    // Now that we have a usable fshDefinition.parent, retrieve its StructureDefinition.
    // Then make sure it is a valid StructureDefinition based on the type of the fshDefinition.
    Object parentFhir = fishForFHIR(fshDefinition.parent, FishableType.Resource, FishableType.Logical,
        FishableType.Type, FishableType.Profile, FishableType.Extension);
    if (!(parentFhir instanceof Map<?, ?> parentJson)) {
      throw new ParentNotDefinedError(fshDefinition.name, fshDefinition.parent, fshDefinition.sourceInfo);
    }

    boolean timeTraveler = truthy(parentJson.get("_timeTraveler"));
    if (timeTraveler && !("Base".equals(parentJson.get("type")) && fshDefinition instanceof Logical)) {
      // R5 resources cannot be parent resources in R4 IGs, with the exception of Base for Logicals.
      // Treat all other time-traveling resources as if they were not found.
      throw new ParentNotDefinedError(fshDefinition.name, fshDefinition.parent, fshDefinition.sourceInfo);
    }

    Object parentType = parentJson.get("type");
    String parentName = str(parentJson.get("name"));
    if (fshDefinition instanceof Extension && !"Extension".equals(parentType)) {
      // An extension can only have an Extension as a parent
      throw new InvalidExtensionParentError(fshDefinition.name, parentName, fshDefinition.sourceInfo);
    } else if (fshDefinition instanceof Logical
        && !((List.of("logical", "resource", "complex-type").contains(parentJson.get("kind"))
            && "specialization".equals(parentJson.get("derivation")))
            || List.of("Base", "Element").contains(parentType))) {
      // A logical model can only have another logical model, a resource, or a complex-type as a parent or it can
      // have the Base or Element resource as a parent
      throw new InvalidLogicalParentError(fshDefinition.name, parentName, fshDefinition.sourceInfo);
    } else if (fshDefinition instanceof Resource && !List.of("Resource", "DomainResource").contains(parentType)) {
      // A resource can only have the 'Resource' or 'DomainResource' as a parent
      throw new InvalidResourceParentError(fshDefinition.name, parentName, fshDefinition.sourceInfo);
    }

    StructureDefinition structDef = StructureDefinition.fromJSON(parentJson);

    // Ref: StructureDefinition.version: "This is an arbitrary value managed by the structure definition author".
    // Technically, a version might have a literal | in it, so split out the version like this.
    List<String> versionParts = Arrays.asList(fshDefinition.parent.split("\\|", -1));
    String joined = String.join("|", versionParts.subList(1, versionParts.size()));
    String parentCanonicalVersion = joined.isEmpty() ? null : joined;

    // Since the structDef is from the parent, set the URL to be the baseDefinition, including the canonical version
    // if it exists.
    structDef.setBaseDefinition(parentCanonicalVersion != null
        ? jsString(structDef.getUrl()) + "|" + parentCanonicalVersion
        : structDef.getUrl());

    // Now, define the url and type here since these are core properties use by subsequent methods
    structDef.setUrl(Common.getUrlFromFshDefinition(fshDefinition, tank.config.canonical));
    structDef.setType(Common.getTypeFromFshDefinitionOrParent(fshDefinition, structDef));

    // Fix fhirVersion for R4/R4B logicals whose parent is the time-traveling Base
    if (timeTraveler && "http://hl7.org/fhir/StructureDefinition/Base".equals(parentJson.get("url"))
        && truthy(fisher.defaultFHIRVersion) && !fisher.defaultFHIRVersion.equals(structDef.getFhirVersion())) {
      structDef.setFhirVersion(fisher.defaultFHIRVersion);
    }

    resetParentElements(structDef, fshDefinition);

    return structDef;
  }

  /**
   * Sets the metadata for the StructureDefinition. This includes clearing metadata that was copied from the parent
   * that may not be relevant to the child StructureDefinition (the approach Forge uses), plus setting extra metadata
   * provided by FSH.
   */
  private void setMetadata(StructureDefinition structDef, FshStructure fshDefinition) {
    // Set/clear elements in order of their appearance in Resource/DomainResource/StructureDefinition definitions
    structDef.setId(fshDefinition);
    Common.deleteProp(structDef, "meta");
    Common.deleteProp(structDef, "implicitRules");
    Common.deleteProp(structDef, "language");
    Common.deleteProp(structDef, "text");
    Common.deleteProp(structDef, "contained");
    Common.removeMatchingExtensions(structDef, UNINHERITED_SD_EXTENSIONS, true);
    // keep url since it was already defined when the StructureDefinition was initially created
    Common.deleteProp(structDef, "identifier");

    structDef.setName(fshDefinition);
    if ("".equals(fshDefinition.title)) {
      logger.warn(fshDefinition.constructorName() + " " + fshDefinition.name
          + " has a title field that should not be empty.");
    }
    if ("".equals(fshDefinition.description)) {
      logger.warn(fshDefinition.constructorName() + " " + fshDefinition.name
          + " has a description field that should not be empty.");
    }
    boolean applyToRoot = !Boolean.FALSE.equals(tank.config.applyExtensionMetadataToRoot);
    if (truthy(fshDefinition.title)) {
      structDef.setTitle(fshDefinition.title);
      if (fshDefinition instanceof Extension && applyToRoot) {
        Common.setProp(structDef.elements.get(0), "short", fshDefinition.title);
      }
    } else {
      Common.deleteProp(structDef, "title");
    }
    structDef.setStatus(tank.config.status);
    if (Boolean.TRUE.equals(tank.config.FSHOnly)) {
      structDef.setVersion(tank.config.version);
    } else {
      Common.deleteProp(structDef, "version"); // deleting to allow the IG Publisher default to take hold
    }
    Common.deleteProp(structDef, "experimental");
    Common.deleteProp(structDef, "date");
    Common.deleteProp(structDef, "publisher");
    Common.deleteProp(structDef, "contact");
    if (truthy(fshDefinition.description)) {
      structDef.setDescription(fshDefinition.description);
      if (fshDefinition instanceof Extension && applyToRoot) {
        Common.setProp(structDef.elements.get(0), "definition", fshDefinition.description);
      }
    } else {
      Common.deleteProp(structDef, "description");
    }
    Common.deleteProp(structDef, "useContext");
    Common.deleteProp(structDef, "jurisdiction");
    Common.deleteProp(structDef, "purpose");
    Common.deleteProp(structDef, "copyright");
    Common.deleteProp(structDef, "keyword");
    // keep structDef.getFhirVersion() as that ought not change from parent to child (except R4/R4B Base, which has
    // already been fixed)
    // keep mapping since existing elements refer to the mapping and we're not removing those
    // keep kind since it should not change except for logical models
    if (fshDefinition instanceof Logical logical) {
      structDef.setKind("logical");
      if (!logical.characteristics.isEmpty()) {
        setCharacteristics(structDef, logical);
      }
    }
    // always reset to false, assuming most children of abstracts aren't abstract; can be overridden w/ rule
    Common.setProp(structDef, "abstract", Boolean.FALSE);
    // keep baseDefinition and type since they were already defined when the StructureDefinition was created
    structDef.setDerivation(fshDefinition instanceof Logical || fshDefinition instanceof Resource
        ? "specialization"
        : "constraint");

    if (fshDefinition instanceof Extension) {
      // Automatically set url.fixedUri on Extensions unless it is already set
      ElementDefinition url = structDef.findElement("Extension.url");
      if (Common.getProp(url, "fixedUri") == null) {
        Common.setProp(url, "fixedUri", structDef.getUrl());
      }
      // NOTE: Context is set later to avoid infinitely recursive fishing and to ensure sub-extension elements are
      // already defined.
    } else {
      // Should not be defined for non-extensions, but clear just to be sure
      Common.deleteProp(structDef, "context");
      Common.deleteProp(structDef, "contextInvariant");
    }

    // Remove all top-level properties that start with _, as the top-level primitive extensions on the parent may not
    // be valid for the child.
    for (String key : new ArrayList<>(Common.keysOf(structDef))) {
      if (key.startsWith("_")) {
        Common.deleteProp(structDef, key);
      }
    }
  }

  private void setContext(StructureDefinition structDef, Extension fshDefinition) {
    // Set context to everything by default, but users can override w/ Context keyword or rules, e.g.
    // Context: Observation
    // ^context[0].type = #element
    // ^context[0].expression = "Patient"
    if (!fshDefinition.contexts.isEmpty()) {
      structDef.setContext(new ArrayList<>());
      for (ExtensionContext extContext : fshDefinition.contexts) {
        if (extContext.isQuoted) {
          structDef.getContext().add(new JsObject().with("expression", extContext.value).with("type", "fhirpath"));
        } else {
          // this could be the path to an element on some StructureDefinition, specified either as a url, a path, or
          // a url with a path:
          //   url: http://example.org/Some/Url
          //   path: resource-name-or-id.some.path
          //   url with path: http://example.org/Some/Url#some.path
          // we split on # to make contextItem and contextPath; since # can appear in a url, assume that the last #
          // is what separates the url from the FSH path
          List<String> splitContext = new ArrayList<>(Arrays.asList(extContext.value.split("#", -1)));
          String contextItem;
          String contextPath;
          if (splitContext.size() == 1) {
            contextPath = "";
            contextItem = splitContext.get(0);
          } else {
            contextPath = splitContext.remove(splitContext.size() - 1);
            contextItem = String.join("#", splitContext);
          }

          if (contextPath.isEmpty() && !ValidUrl.isUri(contextItem)) {
            List<String> splitPath = Common.splitOnPathPeriods(contextItem);
            contextItem = splitPath.get(0);
            contextPath = String.join(".", splitPath.subList(1, splitPath.size()));
          }
          Object targetResource = fishForFHIR(contextItem, FishableType.Extension, FishableType.Profile,
              FishableType.Resource, FishableType.Logical, FishableType.Type);
          if (targetResource != null) {
            StructureDefinition resourceSD = StructureDefinition.fromJSON(targetResource);
            ElementDefinition targetElement = resourceSD.findElementByPath(contextPath, this);
            if (targetElement != null) {
              // we want to represent the context using "extension" type whenever possible. if the resource is an
              // Extension, and every element along the path to the target has type "extension", then this context
              // can be represented with type "extension". otherwise, this is "element" context.
              if (targetElement.isPartOfComplexExtension()) {
                setContextForComplexExtension(structDef, targetElement, extContext);
              } else {
                String contextExpression;
                String contextType = "element";
                if ("Extension".equals(resourceSD.getType()) && targetElement.parent() == null) {
                  contextExpression = resourceSD.getUrl();
                  contextType = "extension";
                } else if (!"constraint".equals(resourceSD.getDerivation())
                    && resourceSD.getUrl().startsWith("http://hl7.org/fhir/StructureDefinition/")) {
                  contextExpression = targetElement.getId();
                } else {
                  contextExpression = jsString(resourceSD.getUrl()) + "#" + jsString(targetElement.getId());
                }
                structDef.getContext().add(new JsObject().with("expression", contextExpression).with("type", contextType));
              }
            } else {
              logger.error("Could not find element " + contextPath + " on resource " + contextItem
                  + " to use as extension context.", extContext.sourceInfo);
            }
          } else {
            logger.error("Could not find resource " + contextItem + " to use as extension context.",
                extContext.sourceInfo);
          }
        }
      }
    } else if (structDef.getContext() == null) {
      // only set default context if there's no inherited context
      List<Object> context = new ArrayList<>();
      context.add(new JsObject().with("type", "element").with("expression", "Element"));
      structDef.setContext(context);
    }
  }

  /**
   * When setting context for a complex extension, the path to the contained extension is based on the urls for each
   * contained extension, like this: extensionUrl#childExtension.grandchildExtension
   */
  private void setContextForComplexExtension(StructureDefinition structDef, ElementDefinition targetElement,
      ExtensionContext extContext) {
    List<ElementDefinition> allParents = targetElement.getAllParents();
    List<ElementDefinition> chain = new ArrayList<>(allParents.subList(0, Math.max(0, allParents.size() - 1)));
    java.util.Collections.reverse(chain);
    chain.add(targetElement);
    List<String> urls = new ArrayList<>();
    for (ElementDefinition ed : chain) {
      ElementDefinition myUrl = ed.structDef.findElement(ed.getId() + ".url");
      Object fixedUri = myUrl == null ? null : Common.getProp(myUrl, "fixedUri");
      if (fixedUri != null) {
        urls.add(org.hl7.fhir.sushi.js.Js.toStr(fixedUri));
      } else {
        logger.error("Could not find URL for extension " + ed.getId() + " as part of extension context.",
            extContext.sourceInfo);
        urls.add("");
      }
    }
    String extensionHierarchy = String.join(".", urls);
    structDef.getContext().add(new JsObject()
        .with("expression", jsString(targetElement.structDef.getUrl()) + "#" + extensionHierarchy)
        .with("type", "extension"));
  }

  private void setCharacteristics(StructureDefinition structDef, Logical fshDefinition) {
    // characteristics are set using the structuredefinition-type-characteristics extension; the allowed codes are
    // in the type-characteristics-code value set, composed of all codes in the type-characteristics-code code
    // system. however, the can-be-target code may be missing. if so, use the logical-target extension.
    if (typeCharacteristicCodes == null) {
      logger.warn("Type characteristics code system not found. Skipping validation of characteristics for "
          + fshDefinition.name + ".", fshDefinition.sourceInfo);
    }
    List<Object> characteristicExtensions = new ArrayList<>();
    for (String characteristic : fshDefinition.characteristics) {
      if (typeCharacteristicCodes != null) {
        if (typeCharacteristicCodes.contains(characteristic)) {
          characteristicExtensions.add(new JsObject().with("url", Common.TYPE_CHARACTERISTICS_EXTENSION)
              .with("valueCode", characteristic));
        } else if ("can-be-target".equals(characteristic)) {
          characteristicExtensions.add(new JsObject().with("url", Common.LOGICAL_TARGET_EXTENSION)
              .with("valueBoolean", Boolean.TRUE));
        } else {
          logger.warn("Unrecognized characteristic: " + characteristic + ". Supported characteristic codes are "
              + commaSeparatedCharacteristics + ".", fshDefinition.sourceInfo);
          characteristicExtensions.add(new JsObject().with("url", Common.TYPE_CHARACTERISTICS_EXTENSION)
              .with("valueCode", characteristic));
        }
      } else {
        characteristicExtensions.add(new JsObject().with("url", Common.TYPE_CHARACTERISTICS_EXTENSION)
            .with("valueCode", characteristic));
      }
    }
    if (!characteristicExtensions.isEmpty()) {
      if (structDef.getExtension() == null) {
        structDef.setExtension(new ArrayList<>());
      }
      structDef.getExtension().addAll(characteristicExtensions);
    }
  }

  /**
   * At this point, 'structDef' contains the parent's ElementDefinitions. For profiles and extensions, these are
   * mostly correct. For logical models and resources, the id and path attributes need to be changed to reflect the
   * type of the logical model/resource.
   */
  private void resetParentElements(StructureDefinition structDef, FshStructure fshDefinition) {
    // In some cases, uninherited extensions may be on the elements, so filter them out
    for (ElementDefinition el : structDef.elements) {
      el.removeUninheritedExtensions();
    }
    structDef.captureOriginalMapping();
    structDef.captureOriginalElements();

    // The remaining logic only pertains to logicals and resources, so return here if otherwise
    if (fshDefinition instanceof Profile || fshDefinition instanceof Extension) {
      return;
    }

    List<ElementDefinition> elements = new ArrayList<>();
    for (ElementDefinition e : structDef.elements) {
      elements.add(e.clone());
    }

    // The Elements will have the same values for both 'id' and 'path'. Therefore, use the 'id' as the source of the
    // conversion and reset the 'id' value with the new base value. The 'id' mutator will automatically reset the
    // 'path' value.
    String pathType = structDef.getPathType();
    for (ElementDefinition e : elements) {
      e.setId(replaceFirstSegment(e.getId(), pathType));
    }

    // The root element's base.path should be the same as root element's path
    Common.setProp(elements.get(0).getBase(), "path", elements.get(0).getPath());

    if ("Element".equals(fshDefinition.parent)
        || "http://hl7.org/fhir/StructureDefinition/Element".equals(fshDefinition.parent)) {
      // The FHIR Element StructureDefinition contains extensions related to being defined as 'normative'. These
      // extensions should not be carried forward in the new StructureDefinition.
      Common.deleteProp(elements.get(0), "extension");
    }

    structDef.elements.clear();
    structDef.elements.addAll(elements);
    for (ElementDefinition e : elements) {
      structDef.addElementToTree(e);
    }
    structDef.captureOriginalElements();

    // The following changes to the root element will be included in the differential root element.

    // Reset the root element's short and definition
    ElementDefinition root = structDef.elements.get(0);
    Common.setProp(root, "short", fshDefinition.title != null ? fshDefinition.title : fshDefinition.name);
    Common.setProp(root, "definition",
        fshDefinition.description != null ? fshDefinition.description : Common.getProp(root, "short"));
  }

  /** JS id.replace(/^[^.]+/, replacement) (replacement has no $ patterns: it is a type name or url). */
  private static String replaceFirstSegment(String id, String replacement) {
    int dot = id.indexOf('.');
    int end = dot < 0 ? id.length() : dot;
    if (end == 0) {
      return id; // /^[^.]+/ needs at least one character
    }
    return replacement + id.substring(end);
  }

  /** Sets the rules for the StructureDefinition. */
  private void setRules(StructureDefinition structDef, FshStructure fshDefinition) {
    PathUtils.resolveSoftIndexing(fshDefinition.rules);
    // When we process obeys rules, we may add rules we don't want reflected in preprocessed output, so make a
    // shallow copy of the array and iterate over that instead of the original
    List<Rule> rules = new ArrayList<>(fshDefinition.rules);
    // in order to validate rules that set values on contained resources, we need to track information from rules
    // that define the types of those resources. those types could be defined by rules on the "resourceType"
    // element, or they could be defined by the existing resource that is being assigned. rules that assign
    // contained instances will be deferred, therefore rules that assign values within those instances must also be
    // deferred.
    List<String> directResourcePaths = new ArrayList<>();
    record InlineResourcePath(String path, String caretPath, String instanceOf) {}
    List<InlineResourcePath> inlineResourcePaths = new ArrayList<>();
    // first, collect the information we can from rules that set a resourceType. if instances are directly assigned,
    // we'll get information from them when we fish up the instance.
    for (Rule r0 : rules) {
      if (r0 instanceof CaretValueRule r) {
        if (r.path.isEmpty() && r.isInstance) {
          directResourcePaths.add(r.caretPath);
        } else if (r.caretPath.endsWith(".resourceType") && r.value instanceof String v && !r.isInstance) {
          List<String> parts = Common.splitOnPathPeriods(r.caretPath);
          inlineResourcePaths.add(new InlineResourcePath(r.path,
              String.join(".", parts.subList(0, Math.max(0, parts.size() - 1))), v));
        }
      }
    }
    for (int i = 0; i < rules.size(); i++) {
      Rule rule = rules.get(i);

      // Specific rules are permitted for each structure definition type (i.e., Profile, Logical, etc.). Log an
      // error for disallowed rules and continue to next rule.
      if (!AllowedRules.isAllowedRule(fshDefinition, rule)) {
        logger.error("Use of '" + rule.constructorName() + "' is not permitted for '"
            + fshDefinition.constructorName() + "'. Skipping '" + rule.constructorName() + "' at path '"
            + rule.path + "' for '" + fshDefinition.name + "'.", rule.sourceInfo);
        continue;
      }

      if (rule instanceof AddElementRule addRule) {
        try {
          // Note: newElement() method automatically adds the new element to its structDef.elements
          ElementDefinition newElement = structDef.newElement(addRule.path);
          newElement.applyAddElementRule(addRule, this);
          String newId = newElement.getId();
          String lastIdPart = newId.substring(newId.lastIndexOf('.') + 1);
          // see http://hl7.org/fhir/elementdefinition-definitions.html#ElementDefinition eld-19 and eld-20
          List<String> nameErrors = new ArrayList<>();
          if (!ELD19.matcher(lastIdPart).find()) {
            nameErrors.add("cannot include some special characters");
          }
          int trueLength = lastIdPart.endsWith("[x]") ? lastIdPart.length() - 3 : lastIdPart.length();
          if (trueLength > 64) {
            nameErrors.add("must be at most 64 characters long");
          }
          if (!nameErrors.isEmpty()) {
            logger.error("Invalid path " + addRule.path + ". Element names " + String.join(" and ", nameErrors)
                + ". See eld-19 at https://hl7.org/fhir/elementdefinition.html#ElementDefinition-inv.",
                addRule.sourceInfo);
          } else if (!ELD20.matcher(lastIdPart).find()) {
            logger.warn("Inadvisable path " + addRule.path
                + ". Element names should be simple alphanumerics. See eld-20 at"
                + " https://hl7.org/fhir/elementdefinition.html#ElementDefinition-inv.", addRule.sourceInfo);
          }
        } catch (RuntimeException e) {
          logError(e, rule.sourceInfo);
        }
        continue;
      }

      ElementDefinition element = structDef.findElementByPath(rule.path, this);
      if (element != null) {
        try {
          if (rule instanceof CardRule cardRule) {
            // TS passes undefined through; WP-4's signature takes NaN / "" for unset
            element.constrainCardinality(cardRule.min == null ? Double.NaN : cardRule.min,
                cardRule.max == null ? "" : cardRule.max);
          } else if (rule instanceof AssignmentRule assignmentRule) {
            if (!applyAssignmentRule(element, assignmentRule)) {
              continue;
            }
          } else if (rule instanceof FlagRule flagRule) {
            element.applyFlags(flagRule.mustSupport, flagRule.summary, flagRule.modifier, flagRule.trialUse,
                flagRule.normative, flagRule.draft);
          } else if (rule instanceof OnlyRule onlyRule) {
            String target = structDef.getReferenceOrCanonicalName(onlyRule.path, element);
            element.constrainType(onlyRule, this, target);
          } else if (rule instanceof BindingRule bindingRule) {
            applyBindingRule(structDef, element, bindingRule);
          } else if (rule instanceof ContainsRule containsRule) {
            applyContainsRule(fshDefinition, structDef, element, containsRule);
          } else if (rule instanceof CaretValueRule caretRule) {
            CaretValueRule replacedRule = Common.replaceReferences(caretRule, tank, this);
            if (!replacedRule.path.isEmpty()) {
              element.setInstancePropertyByPath(replacedRule.caretPath, replacedRule.value, this);
            } else {
              // the relevant inline resource paths for the current rule are rules with:
              // - the same path
              // - a caret path that is an ancestor of the current rule's path
              // - and also, the current rule's caret path can not be this other rule's caret path followed by
              //   "resourceType".
              // for each of those matches, we build up the inline resource types array: a sparse array parallel to
              // the parts of the current rule's caret path. e.g. a caret path of "contained.interpretation" which
              // sets a value on a contained Observation gives ["Observation"]; "contained.entry.resource.
              // interpretation" can give ["Bundle", undefined, "Observation"].
              List<String> inlineResourceTypes = new ArrayList<>();
              for (InlineResourcePath ir : inlineResourcePaths) {
                if (replacedRule.path.equals(ir.path())
                    && replacedRule.caretPath.startsWith(ir.caretPath() + ".")
                    && !replacedRule.caretPath.equals(ir.caretPath() + ".resourceType")) {
                  setSparse(inlineResourceTypes, Common.splitOnPathPeriods(ir.caretPath()).size() - 1,
                      ir.instanceOf());
                }
              }
              boolean matchesDirectResourcePath = false;
              for (String d : directResourcePaths) {
                if (replacedRule.caretPath.startsWith(d + ".")) {
                  matchesDirectResourcePath = true;
                  break;
                }
              }
              if (replacedRule.isInstance) {
                defer(structDef, new DeferredCaretRule(replacedRule, true, null));
              } else if (matchesDirectResourcePath) {
                // we may be assigning a caret rule of a non-instance value on top of an assigned instance; if so,
                // defer the rule so that we can assign the instance first. we don't need to fish up anything.
                defer(structDef, new DeferredCaretRule(replacedRule, false, null));
              } else {
                try {
                  structDef.setInstancePropertyByPath(replacedRule.caretPath, replacedRule.value, this,
                      inlineResourceTypes);
                } catch (MismatchedTypeError originalErr) {
                  if (isNumberBigintOrBoolean(caretRule.value)) {
                    // retry like with an assignment rule, but we have to defer it.
                    defer(structDef, new DeferredCaretRule(replacedRule, true, originalErr));
                  } else {
                    throw originalErr;
                  }
                }
              }
            }
          } else if (rule instanceof ObeysRule obeysRule) {
            FshEntity fished = tank.fish(obeysRule.invariant, FishableType.Invariant);
            if (!(fished instanceof Invariant invariant)) {
              logger.error("Cannot apply " + obeysRule.invariant + " constraint on " + jsString(structDef.getId())
                  + " because it was never defined.", obeysRule.sourceInfo);
            } else {
              // First apply the standard metadata from the keywords
              int cstIdx = element.applyConstraint(invariant, structDef.getUrl());
              if (!ID_REGEX.matcher(jsString(invariant.name)).find()) { // JS regex.test(undefined) tests "undefined"
                throw new InvalidFHIRIdError(invariant.name);
              }
              // Invariant rules are just assignments on the constraint paths in the StructureDefinition. As such,
              // the most robust way to handle them is to convert them all to caret rules and insert them in the
              // rules to be processed.
              if (!invariant.rules.isEmpty()) {
                List<CaretValueRule> constraintCaretRules = new ArrayList<>();
                for (Rule invRule0 : invariant.rules) {
                  if (invRule0 instanceof AssignmentRule invRule) {
                    // Top-level obeys rule (no path) actually applies to root element (.)
                    CaretValueRule caretRule = new CaretValueRule(obeysRule.path.isEmpty() ? "." : obeysRule.path)
                        .<CaretValueRule>withFile(invRule.sourceInfo.file)
                        .<CaretValueRule>withLocation(invRule.sourceInfo.location)
                        .<CaretValueRule>withAppliedFile(obeysRule.sourceInfo.file)
                        .withAppliedLocation(obeysRule.sourceInfo.location);
                    caretRule.caretPath = "constraint[" + cstIdx + "]." + invRule.path;
                    caretRule.value = invRule.value;
                    caretRule.rawValue = invRule.rawValue;
                    caretRule.isInstance = invRule.isInstance;
                    constraintCaretRules.add(caretRule);
                  }
                }
                PathUtils.resolveSoftIndexing(constraintCaretRules);
                rules.addAll(i + 1, constraintCaretRules);
              }
            }
          }
        } catch (RuntimeException e) {
          // TS: logger.error(e.message ?? e, ...)
          logError(e, rule.sourceInfo);
        }
      } else {
        logger.error("No element found at path " + rule.path + " for " + rule.constructorName() + " in "
            + fshDefinition.name + ", skipping rule", rule.sourceInfo);
      }
    }
  }

  /** The AssignmentRule branch of setRules; returns false where TS `continue`s (rule skipped). */
  private boolean applyAssignmentRule(ElementDefinition element, AssignmentRule rule) {
    if (rule.isInstance) {
      InstanceExporter instanceExporter = new InstanceExporter(tank, pkg, fisher);
      InstanceDefinition instance = instanceExporter.fishForFHIR((String) rule.value);
      if (instance == null) {
        if (element.getType() != null && element.getType().size() == 1) {
          logger.error("Cannot assign Instance at path " + rule.path + " to element of type "
              + element.getType().get(0).getCode() + ". Definition not found for Instance: " + jsString(rule.value)
              + ".", rule.sourceInfo);
        } else {
          logger.error("Cannot find definition for Instance: " + jsString(rule.value) + ". Skipping rule.",
              rule.sourceInfo);
        }
        return false;
      }
      if ("Example".equals(instance._instanceMeta.usage)) {
        logger.warn("Contained instance \"" + jsString(rule.value)
            + "\" is an example and probably should not be included in a conformance resource.", rule.sourceInfo);
      }
      rule.value = instance;
    }
    AssignmentRule replacedRule = Common.replaceReferences(rule, tank, this);
    try {
      element.assignValue(replacedRule.value, replacedRule.exactly, this);
    } catch (MismatchedTypeError originalErr) {
      // if an Instance has an id that looks like a number, bigint, or boolean, we may have tried to assign that
      // value instead of an Instance. try to fish up an Instance with the rule's raw value. if we find one, try
      // assigning that instead.
      if (!isNumberBigintOrBoolean(rule.value)) {
        throw originalErr;
      }
      InstanceExporter instanceExporter = new InstanceExporter(tank, pkg, fisher);
      InstanceDefinition instance = instanceExporter.fishForFHIR(rule.rawValue);
      if (instance == null) {
        throw originalErr;
      }
      try {
        element.assignValue(instance, rule.exactly, this);
        if ("Example".equals(instance._instanceMeta.usage)) {
          logger.warn("Contained instance \"" + jsString(rule.value)
              + "\" is an example and probably should not be included in a conformance resource.",
              rule.sourceInfo);
        }
        rule.value = instance;
      } catch (MismatchedTypeError instanceErr) {
        // if it's still the wrong type, the assignment will fail. but, we want to log that error with the
        // original type, not the instance type.
        throw originalErr;
      }
    }
    return true;
  }

  private void applyBindingRule(StructureDefinition structDef, ElementDefinition element, BindingRule rule) {
    Metadata vsMetadata = fishForMetadata(rule.valueSet, FishableType.ValueSet);
    if (vsMetadata != null && "Inline".equals(vsMetadata.instanceUsage)) {
      // if we're binding to an inline ValueSet, it won't be available until after deferred caret rules are
      // processed.
      rule.valueSet = vsMetadata.id;
      knownBindingRules.computeIfAbsent(structDef, k -> new ArrayList<>())
          .add(new KnownBindingRule(rule, true, null));
    } else {
      String vsURI = replaceLeadingBase(rule.valueSet, vsMetadata == null ? null : vsMetadata.url);
      Metadata csMetadata = fishForMetadata(rule.valueSet, FishableType.CodeSystem);
      String csURI = csMetadata == null ? null : csMetadata.url;
      if (truthy(csURI) && !ValidUrl.isUri(vsURI)) {
        throw new MismatchedBindingTypeError(rule.valueSet, rule.path, "ValueSet");
      }
      element.bindToVS(vsURI, rule.strength, rule.sourceInfo, fisher);
      String versionlessURI = vsURI.replaceFirst("\\|.*", "");
      knownBindingRules.computeIfAbsent(structDef, k -> new ArrayList<>())
          .add(new KnownBindingRule(rule, false, versionlessURI));
    }
  }

  /**
   * JS {@code s.replace(/^([^|]+)/, url ?? '$1')}: replaces the part before the first "|" with url (no-op when url is
   * undefined). A '$' in url would be a JS replacement pattern; canonical URLs don't contain '$$', '$&amp;' etc.,
   * and Java treats it literally.
   */
  private static String replaceLeadingBase(String s, String url) {
    if (url == null) {
      return s;
    }
    Matcher m = Pattern.compile("^([^|]+)").matcher(s);
    return m.find() ? url + s.substring(m.end()) : s;
  }

  private void applyContainsRule(FshStructure fshDefinition, StructureDefinition structDef, ElementDefinition element,
      ContainsRule rule) {
    if (element.hasInheritedClosedSlicing()) {
      // Only new slices are a problem. Slices that already exist are left to the usual duplicate slice handling.
      List<String> existingSliceNames = new ArrayList<>();
      for (ElementDefinition slice : element.getSlices()) {
        existingSliceNames.add(slice.getSliceName());
      }
      List<String> newSliceNames = new ArrayList<>();
      for (ContainsRuleItem item : rule.items) {
        String sliceName = truthy(element.getSliceName()) ? element.getSliceName() + "/" + item.name : item.name;
        if (!existingSliceNames.contains(sliceName)) {
          newSliceNames.add(sliceName);
        }
      }
      if (!newSliceNames.isEmpty()) {
        throw new ClosedSlicingError(element.getId(), newSliceNames);
      }
    }
    boolean isExtension = element.getType() != null && element.getType().size() == 1
        && "Extension".equals(element.getType().get(0).getCode()) && !truthy(element.getSliceName());
    if (isExtension) {
      handleExtensionContainsRule(fshDefinition, rule, structDef, element);
    } else {
      if (!element.isArrayOrChoice()) {
        throw new InvalidElementForSlicingError(rule.path);
      }
      // Not an extension -- just add a slice for each item
      for (ContainsRuleItem item : rule.items) {
        if (truthy(item.type)) {
          logger.error("Cannot specify type on " + item.name + " slice since " + rule.path
              + " is not an extension path.", rule.sourceInfo);
        }
        try {
          element.addSlice(item.name);
        } catch (RuntimeException e) {
          logError(e, rule.sourceInfo);
        }
      }
    }
  }

  private void defer(StructureDefinition sd, DeferredCaretRule d) {
    deferredCaretRules.computeIfAbsent(sd, k -> new ArrayList<>()).add(d);
  }

  /** JS sparse-array assignment {@code arr[idx] = v} (holes are undefined = null). */
  private static void setSparse(List<String> list, int idx, String value) {
    while (list.size() <= idx) {
      list.add(null);
    }
    list.set(idx, value);
  }

  private static boolean isNumberBigintOrBoolean(Object v) {
    return v instanceof Double || v instanceof BigInteger || v instanceof Boolean;
  }

  public void applyDeferredRules() {
    // keyed by identity, as TS Map/Set of objects
    Map<StructureDefinition, List<String[]>> successfulInstanceAssignments = new LinkedHashMap<>();
    Set<StructureDefinition> sdsToCleanAgain = new LinkedHashSet<>();
    for (Map.Entry<StructureDefinition, List<DeferredCaretRule>> entry : deferredCaretRules.entrySet()) {
      StructureDefinition sd = entry.getKey();
      for (DeferredCaretRule deferred : entry.getValue()) {
        CaretValueRule rule = deferred.rule();
        MismatchedTypeError originalErr = deferred.originalErr();
        // if the rule wanted to assign an instance, it should be available now, so try to fish for it. if the rule
        // wanted to assign a non-instance (presumably within an instance), we don't need to fish for anything.
        if (deferred.tryFish()) {
          String fishItem = null;
          if (rule.value instanceof String s) {
            fishItem = s;
          } else if (isNumberBigintOrBoolean(rule.value)) {
            fishItem = rule.rawValue;
          }

          InstanceExporter instanceExporter = new InstanceExporter(tank, pkg, fisher);
          InstanceDefinition fishedValue = instanceExporter.fishForFHIR(fishItem);
          if (fishedValue == null) {
            Object result = fishForFHIR(fishItem);
            if (!(result instanceof InstanceDefinition) && (result instanceof Map || result instanceof List)) {
              // TS: InstanceDefinition.fromJSON(fishedValue) with fishedValue == null, which throws a TypeError
              // (Object.keys(undefined)) out of applyDeferredRules. Preserved as an uncaught error.
              throw new Common.JsTypeError("Cannot convert undefined or null to object");
            }
          }

          if (fishedValue != null) {
            try {
              if ("Example".equals(fishedValue._instanceMeta.usage)) {
                logger.warn("Contained instance \"" + jsString(rule.value)
                    + "\" is an example and probably should not be included in a conformance resource.",
                    rule.sourceInfo);
              }
              sd.setInstancePropertyByPath(rule.caretPath, fishedValue, this);
              List<String[]> successes = successfulInstanceAssignments.get(sd);
              if (successes != null) {
                // TS: on later assignments only resourceType is recorded
                successes.add(new String[] {rule.caretPath, str(fishedValue.get("resourceType"))});
              } else {
                String resourceType = firstProfile(fishedValue);
                if (resourceType == null) resourceType = fishedValue._instanceMeta.instanceOfUrl;
                if (resourceType == null) resourceType = fishedValue._instanceMeta.sdType;
                if (resourceType == null) resourceType = str(fishedValue.get("resourceType"));
                List<String[]> list = new ArrayList<>();
                list.add(new String[] {rule.caretPath, resourceType});
                successfulInstanceAssignments.put(sd, list);
              }
            } catch (RuntimeException e) {
              if (e instanceof MismatchedTypeError && originalErr != null) {
                logError(originalErr, rule.sourceInfo);
              } else {
                logError(e, rule.sourceInfo);
              }
            }
          } else {
            if (originalErr != null) {
              logError(originalErr, rule.sourceInfo);
            } else {
              logger.error("Could not find a resource named " + jsString(rule.value), rule.sourceInfo);
            }
          }
        } else {
          // when assigning a non-instance value within the contained resource, we expect the resource type to be
          // in place
          List<String> inlineResourceTypes = new ArrayList<>();
          for (String[] match : successfulInstanceAssignments.getOrDefault(sd, List.of())) {
            if (rule.caretPath.startsWith(match[0] + ".") && !rule.caretPath.equals(match[0] + ".resourceType")) {
              setSparse(inlineResourceTypes, Common.splitOnPathPeriods(match[0]).size() - 1, match[1]);
            }
          }
          try {
            if (!inlineResourceTypes.isEmpty()) {
              // the resource was cleaned during export, but since we are going to modify it, now we have to clean
              // it again.
              sdsToCleanAgain.add(sd);
            }
            sd.setInstancePropertyByPath(rule.caretPath, rule.value, this, inlineResourceTypes);
          } catch (RuntimeException e) {
            logError(e, rule.sourceInfo);
          }
        }
      }
    }

    // for any sd that has contained instances assigned and then modified, we need to re-clean. this cleans all
    // contained instances, not just modified ones.
    for (StructureDefinition sd : sdsToCleanAgain) {
      if (sd.getContained() != null) {
        for (Object containedResource : sd.getContained()) {
          Common.cleanResource(containedResource);
        }
      }
    }
    // we need to double-check all our bindings in case we now contain the bound valueset. for inline instances, we
    // should give a special error if they're not contained. for anything else, it's okay if they're not contained.
    // but if they are, use a relative reference.
    for (Map.Entry<StructureDefinition, List<KnownBindingRule>> entry : knownBindingRules.entrySet()) {
      StructureDefinition sd = entry.getKey();
      for (KnownBindingRule known : entry.getValue()) {
        BindingRule rule = known.rule();
        Map<?, ?> containedValueSet = null;
        if (known.isInline()) {
          containedValueSet = findContained(sd, r -> Objects.equals(r.get("id"), rule.valueSet)
              && "ValueSet".equals(r.get("resourceType")));
        } else if (truthy(known.url())) {
          // we may have a value set with a real url that we can turn into a relative reference
          containedValueSet = findContained(sd, r -> Objects.equals(r.get("url"), known.url()));
        }
        if (containedValueSet != null) {
          ElementDefinition element = sd.findElementByPath(rule.path, this);
          try {
            element.bindToVS("#" + jsString(containedValueSet.get("id")), rule.strength, rule.sourceInfo, fisher);
          } catch (RuntimeException e) {
            logError(e, rule.sourceInfo);
          }
        } else if (known.isInline()) {
          logger.error("Can not bind " + rule.path + " to ValueSet " + rule.valueSet
              + ": this ValueSet is an inline instance, but it is not present in the list of contained resources.",
              rule.sourceInfo);
        }
      }
    }
  }

  private static String firstProfile(InstanceDefinition inst) {
    if (inst.get("meta") instanceof Map<?, ?> meta && meta.get("profile") instanceof List<?> profiles
        && !profiles.isEmpty() && profiles.get(0) instanceof String p) {
      return p;
    }
    return null;
  }

  private static Map<?, ?> findContained(StructureDefinition sd, java.util.function.Predicate<Map<?, ?>> p) {
    if (sd.getContained() == null) {
      return null;
    }
    for (Object r : sd.getContained()) {
      if (r instanceof Map<?, ?> m && p.test(m)) {
        return m;
      }
    }
    return null;
  }

  /**
   * Handles a ContainsRule that is on an extension path, appropriately exporting it as a reference to a standalone
   * extension or an inline extension.
   */
  private void handleExtensionContainsRule(FshStructure fshDefinition, ContainsRule rule,
      StructureDefinition structDef, ElementDefinition element) {
    if (element.getSlicing() == null) {
      element.sliceIt("value", "url");
    }
    for (ContainsRuleItem item : rule.items) {
      if (truthy(item.type)) {
        // there might be a |version appended to the type, so try to use the version but fall back to any version
        // if necessary
        Object extension = FishingUtils.fishForFHIRBestVersion(this, item.type, rule.sourceInfo,
            FishableType.Extension);
        if (extension == null) {
          logger.error("Cannot create " + item.name + " extension; unable to locate extension definition for: "
              + item.type + ".", rule.sourceInfo);
          continue;
        }
        Object extUrl = extension instanceof Map<?, ?> m ? m.get("url") : null;
        String profileUrl = replaceLeadingSegment(item.type, jsString(extUrl));
        try {
          ElementDefinition slice = element.addSlice(item.name);
          if (slice.getType().get(0).getProfile() == null) {
            slice.getType().get(0).setProfile(new ArrayList<>());
          }
          slice.getType().get(0).getProfile().add(profileUrl);
        } catch (RuntimeException e) {
          // If this is a DuplicateSliceError, and it references the same extension definition, then it is most
          // likely a harmless no-op. In this case, treat it as a warning.
          if (e instanceof DuplicateSliceError) {
            ElementDefinition slice = null;
            for (ElementDefinition el : element.getSlices()) {
              if (Objects.equals(el.getSliceName(), item.name)) {
                slice = el;
                break;
              }
            }
            if (slice != null && slice.getType() != null && !slice.getType().isEmpty() && slice.getType().get(0) != null
                && slice.getType().get(0).getProfile() != null
                && slice.getType().get(0).getProfile().contains(profileUrl)) {
              logger.warn(e.getMessage(), rule.sourceInfo);
              logger.debug(stack(e));
              continue;
            }
          }
          // Otherwise it is a conflicting duplicate extension or some other error
          logError(e, rule.sourceInfo);
        }
        // check if we have used modifier extensions correctly
        boolean isModifier = Common.isModifierExtension(extension);
        boolean isModifierPath = element.getPath().endsWith(".modifierExtension");
        if (isModifier && !isModifierPath) {
          logger.error("Modifier extension " + item.type + " assigned to extension path. Modifier extensions should"
              + " only be assigned to modifierExtension paths.", rule.sourceInfo);
        } else if (!isModifier && isModifierPath) {
          logger.error("Non-modifier extension " + item.type + " assigned to modifierExtension path. Non-modifier"
              + " extensions should only be assigned to extension paths.", rule.sourceInfo);
        }
      } else {
        try {
          // If the extension is inline, assign its url element automatically to the sliceName
          ElementDefinition slice = element.addSlice(item.name);
          ElementDefinition urlElement = structDef.findElementByPath(
              rule.path + "[" + jsString(slice.getSliceName()) + "].url", this);
          urlElement.assignValue(slice.getSliceName(), true, null);
          // Inline extensions should only be used in extensions
          if (!(fshDefinition instanceof Extension)) {
            logger.error("Inline extensions should only be defined in Extensions.", rule.sourceInfo);
          }
        } catch (RuntimeException e) {
          // Unlike the case above, redeclaring an inline extension is more likely a problem, as inline extensions
          // require further definition outside of the contains rule, so there is more likely to be conflict.
          logError(e, rule.sourceInfo);
        }
      }
    }
  }

  /** JS {@code s.replace(/^[^|]+/, replacement)} with a literal replacement. */
  private static String replaceLeadingSegment(String s, String replacement) {
    int bar = s.indexOf('|');
    int end = bar < 0 ? s.length() : bar;
    if (end == 0) {
      return s;
    }
    return replacement + s.substring(end);
  }

  /** Does any necessary preprocessing of profiles, extensions, logical models, and resources. */
  private void preprocessStructureDefinition(FshStructure fshDefinition, boolean isExtension) {
    // key is the rule path, value is a boolean of whether it should be set
    Map<String, Boolean> inferredCardRulesMap = new LinkedHashMap<>();
    for (Rule rule : fshDefinition.rules) {
      List<String> rulePathParts = Common.splitOnPathPeriods(rule.path);
      for (int i = 0; i < rulePathParts.size(); i++) {
        String pathPart = rulePathParts.get(i);
        String previousPathPart = i > 0 ? rulePathParts.get(i - 1) : null;
        // A rule is related to an extension if it is directly on a FSH defined extension or if it is defined inline
        // on a profile or extension. Check the section before the current pathPart to see if it is an inline
        // extension.
        boolean isOnExtension = (isExtension && rulePathParts.size() == 1)
            || (previousPathPart != null && previousPathPart.startsWith("extension"));

        // If we are not looking at a rule on an extension, don't infer anything.
        if (!isOnExtension) {
          continue;
        }

        String initialPath = String.join(".", rulePathParts.subList(0, i));
        String basePath = initialPath + (initialPath.isEmpty() ? "" : ".");
        boolean isZeroedCard = rule instanceof CardRule c && "0".equals(c.max);

        // See if we can infer any rules about an extension (inline or FSH defined)
        if (pathPart.startsWith("extension")) {
          String relevantContradictoryRule = basePath + "extension";
          Boolean entry = inferredCardRulesMap.get(relevantContradictoryRule);
          if (!isZeroedCard) {
            if (Boolean.TRUE.equals(entry)) {
              logger.error("Extension on " + fshDefinition.name + " cannot have both a value and sub-extensions",
                  rule.sourceInfo);
              inferredCardRulesMap.put(relevantContradictoryRule, false);
            } else {
              // If we don't already have a contradiction, add new rule to infer value[x] constraints
              inferredCardRulesMap.put(basePath + "value[x]", true);
            }
          } else if (Boolean.TRUE.equals(entry)) {
            inferredCardRulesMap.put(relevantContradictoryRule, false);
          }
        } else if (pathPart.startsWith("value")) {
          String relevantContradictoryRule = basePath + "value[x]";
          Boolean entry = inferredCardRulesMap.get(relevantContradictoryRule);
          if (!isZeroedCard) {
            if (Boolean.TRUE.equals(entry)) {
              logger.error("Extension on " + fshDefinition.name + " cannot have both a value and sub-extensions",
                  rule.sourceInfo);
              inferredCardRulesMap.put(relevantContradictoryRule, false);
            } else {
              // If we don't already have a contradiction, add new rule to infer extension constraints
              inferredCardRulesMap.put(basePath + "extension", true);
            }
          } else if (Boolean.TRUE.equals(entry)) {
            inferredCardRulesMap.put(relevantContradictoryRule, false);
          }
        }
      }
    }

    // If only value[x] or extension is used, constrain cardinality of the other to 0..0.
    // If both are used, an error has been logged, but the rules will still be applied.
    for (Map.Entry<String, Boolean> e : inferredCardRulesMap.entrySet()) {
      if (e.getValue()) {
        CardRule inferredCardRule = new CardRule(e.getKey());
        inferredCardRule.min = 0.0;
        inferredCardRule.max = "0";
        fshDefinition.rules.add(inferredCardRule);
      }
    }
  }

  @Override public Object fishForFHIR(String item, FishableType... types) {
    Object result = fisher.fishForFHIR(item, types);
    if (result == null && (types.length == 0 || Arrays.stream(types).anyMatch(t -> t == FishableType.Profile
        || t == FishableType.Extension || t == FishableType.Logical || t == FishableType.Resource))) {
      // If we find a FSH definition, then we can export and fish for it again
      FshEntity fshDefinition = tank.fish(item, FishableType.Profile, FishableType.Extension, FishableType.Logical,
          FishableType.Resource);
      if (fshDefinition instanceof Profile || fshDefinition instanceof Extension || fshDefinition instanceof Logical
          || fshDefinition instanceof Resource) {
        exportStructDef((FshStructure) fshDefinition);
        result = fisher.fishForFHIR(item, types);
      } else if (fshDefinition instanceof Instance instance) {
        // (unreachable in practice: the tank is not asked for Instances above; kept for parity)
        InstanceExporter instanceExporter = new InstanceExporter(tank, pkg, fisher);
        instanceExporter.exportInstance(instance);
        result = fisher.fishForFHIR(item, types);
      }
    }
    return result;
  }

  @Override public Metadata fishForMetadata(String item, FishableType... types) {
    // If it's in the tank, it can get the metadata from there (no need to export like in fishForFHIR)
    return fisher.fishForMetadata(item, types);
  }

  @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
    // If it's in the tank, it can get the metadata from there (no need to export like in fishForFHIR)
    return fisher.fishForMetadatas(item, types);
  }

  public void applyInsertRules() {
    for (Invariant inv : tank.getAllInvariants()) {
      Common.applyInsertRules(inv, tank);
    }
    for (FshStructure sd : tank.getAllStructureDefinitions()) {
      Common.applyInsertRules(sd, tank);
    }
  }

  /**
   * Exports Profile, Extension, Logical model, and custom Resource to StructureDefinition. Returns null when a
   * definition with the same name was already exported.
   *
   * @throws ParentDeclaredAsNameError when the fshDefinition declares itself as the parent
   * @throws ParentDeclaredAsIdError when the fshDefinition declares itself as the parent
   * @throws ParentNotDefinedError when the fshDefinition's parent is not found
   * @throws InvalidExtensionParentError when Extension does not have valid parent
   * @throws InvalidLogicalParentError when Logical does not have valid parent
   */
  public StructureDefinition exportStructDef(FshStructure fshDefinition) {
    if (hasName(pkg.profiles, fshDefinition.name) || hasName(pkg.extensions, fshDefinition.name)
        || hasName(pkg.logicals, fshDefinition.name) || hasName(pkg.resources, fshDefinition.name)) {
      return null;
    }

    StructureDefinition structDef = getStructureDefinition(fshDefinition);

    if (Boolean.TRUE.equals(structDef.inProgress)) {
      logger.warn("The definition of " + fshDefinition.name + " may be incomplete because there is a circular "
          + "dependency with its parent " + fshDefinition.parent + " causing the parent to be used before the "
          + "parent has been fully processed.", fshDefinition.sourceInfo);
    }

    structDef.inProgress = true;

    // At this point, 'structDef' contains the parent's metadata and elements; therefore, transform the current
    // 'structDef' from the parent to the new StructureDefinition to be exported.

    // Reset the original parent's metadata to that for the new StructureDefinition
    setMetadata(structDef, fshDefinition);

    // These are being pushed now in order to allow for incomplete definitions to be used to resolve circular
    // reference issues.
    if ("Extension".equals(structDef.getType())) {
      pkg.extensions.add(structDef);
    } else if ("logical".equals(structDef.getKind()) && "specialization".equals(structDef.getDerivation())) {
      pkg.logicals.add(structDef);
    } else if ("resource".equals(structDef.getKind()) && "specialization".equals(structDef.getDerivation())) {
      pkg.resources.add(structDef);
    } else {
      pkg.profiles.add(structDef);
    }
    pkg.fshMap.put(structDef.getFileName(), new Package.FshMapEntry(fshDefinition.sourceInfo.copy(),
        fshDefinition.name, fshDefinition.constructorName()));

    preprocessStructureDefinition(fshDefinition, "Extension".equals(structDef.getType()));

    setRules(structDef, fshDefinition);

    if (fshDefinition instanceof Extension extension) {
      // Set the context last so that contexts referring to self work properly and have all necessary
      // sub-extensions already. This also allows us to avoid recursive fishing.
      setContext(structDef, extension);
    }

    // The recursive structDef, treeParent, and treeChildren fields on elements should be ignored to avoid infinite
    // looping. And, the _sliceName and _primitive properties added by SUSHI should be skipped.
    Common.cleanResource(structDef, prop -> List.of("structDef", "treeParent", "treeChildren", "_sliceName",
        "_primitive").contains(prop));
    structDef.inProgress = false;

    for (ValidationError err : structDef.validate()) {
      logger.log(FshLogger.Level.valueOf(err.severity), err.getMessage(), fshDefinition.sourceInfo);
    }

    Common.checkForMultipleChoice(fshDefinition, structDef, structDef.getOwnStructureDefinition(fisher));

    // check for another structure definition with the same id (see https://www.hl7.org/fhir/resource.html#id).
    // the structure definition has already been added to the package, so it's fine if it matches itself
    if (hasOtherWithId(pkg.profiles, structDef) || hasOtherWithId(pkg.extensions, structDef)
        || hasOtherWithId(pkg.logicals, structDef) || hasOtherWithId(pkg.resources, structDef)) {
      logger.error("Multiple structure definitions with id " + jsString(structDef.getId())
          + ". Each structure definition must have a unique id.", fshDefinition.sourceInfo);
    }

    return structDef;
  }

  private static boolean hasName(List<StructureDefinition> sds, String name) {
    for (StructureDefinition sd : sds) {
      if (Objects.equals(sd.getName(), name)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasOtherWithId(List<StructureDefinition> sds, StructureDefinition structDef) {
    for (StructureDefinition sd : sds) {
      if (Objects.equals(structDef.getId(), sd.getId()) && structDef != sd) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks invariants to ensure they have the required values (human and severity) and that the values are
   * appropriate. In order to avoid excessive logging, this is done once, as a group, rather than each time an
   * invariant is applied.
   */
  private void checkInvariants() {
    for (Invariant invariant : tank.getAllInvariants()) {
      org.hl7.fhir.sushi.fshtypes.Common.ValueFromRules human =
          org.hl7.fhir.sushi.fshtypes.Common.getValueFromRules(invariant, "human", "", "human");
      Object description = human != null && human.value() != null ? human.value() : invariant.description;
      if (description == null) {
        logger.error("Invariant " + invariant.name + " is missing its human description. To set the description,"
            + " add the \"Description:\" keyword or add a rule assigning \"human\" to a string value.",
            invariant.sourceInfo);
      }
      Rule severityRule =
          org.hl7.fhir.sushi.fshtypes.Common.findAssignmentByPath(invariant, "severity", "", "severity");
      Object ruleValue = severityRule instanceof AssignmentRule a ? a.value
          : severityRule instanceof CaretValueRule c ? c.value : null;
      Object severity = ruleValue != null ? ruleValue : invariant.severity;
      // TS: (severity instanceof FshEntity && severity.sourceInfo) ?? severityRule.sourceInfo ?? invariant.sourceInfo
      // -- `false ?? x` is false, so a non-FshEntity severity logs without a source location.
      SourceInfo severitySource = severity instanceof FshEntity fe ? fe.sourceInfo : null;
      if (severity == null) {
        logger.error("Invariant " + invariant.name + " is missing its severity level. To set the severity, add the"
            + " \"Severity:\" keyword or add a rule assigning \"severity\" to #error or #warning.",
            invariant.sourceInfo);
      } else if (!(severity instanceof FshCode code && List.of("error", "warning").contains(code.code))) {
        logger.error("Invariant " + invariant.name + " has an invalid severity level. Supported values are #error"
            + " and #warning.", severitySource);
      } else if (((FshCode) severity).system != null) {
        logger.warn("Invariant " + invariant.name + " has a severity level including a code system. Remove the code"
            + " system from the value.", severitySource);
      }
    }
  }

  /** Exports Profiles, Extensions, Logical models, and Resources to StructureDefinitions. */
  public Package export() {
    checkInvariants();
    List<FshStructure> structureDefinitions = tank.getAllStructureDefinitions();
    for (FshStructure sd : structureDefinitions) {
      try {
        exportStructDef(sd);
      } catch (RuntimeException e) {
        SourceInfo src = e instanceof WithSource ws && ws.getSourceInfo() != null ? ws.getSourceInfo()
            : sd.sourceInfo;
        logger.error(e.getMessage(), src);
        logger.debug(stack(e));
      }
    }
    warnOnNonConformantResourceDefinitions();
    if (!structureDefinitions.isEmpty()) {
      logger.info("Converted " + structureDefinitions.size() + " FHIR StructureDefinitions.");
    }
    return pkg;
  }

  // ---- small helpers ----

  /** TS: logger.error(e.message, sourceInfo); if (e.stack) logger.debug(e.stack). */
  private static void logError(RuntimeException e, SourceInfo sourceInfo) {
    logger.error(e.getMessage(), sourceInfo);
    logger.debug(stack(e));
  }

  /** The Java stack trace stands in for the JS e.stack (debug level only). */
  static String stack(Throwable e) {
    return Common.jsStack(e);
  }

  private static boolean truthy(Object o) {
    return org.hl7.fhir.sushi.js.Js.truthy(o);
  }

  private static String str(Object o) {
    return o instanceof String s ? s : null;
  }

  /** Template-literal interpolation (null = undefined). */
  private static String jsString(Object o) {
    return org.hl7.fhir.sushi.fshtypes.Common.jsString(o);
  }
}
