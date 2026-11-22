package org.hl7.fhir.sushi.fshtypes;

import java.util.List;
import org.hl7.fhir.sushi.js.JsObject;

// port of sushi/src/fshtypes/Configuration.ts (SUSHI 3.21.1)
/**
 * The normalized SUSHI configuration (from sushi-config.yaml via importConfiguration, or from an IG resource).
 * Fields typed with FHIR datatypes in TS (Meta, Narrative, ContactDetail, ImplementationGuide* backbone elements, ...)
 * are JS-model objects ({@link JsObject}; lists are {@code List<JsObject>}), since IGExporter copies them into the IG
 * JSON as-is. null = undefined throughout. Field names match TS exactly (including {@code FSHOnly}).
 */
public class Configuration {
  public String filePath;
  public String canonical;
  public String id;
  /** Meta */
  public JsObject meta;
  public String implicitRules;
  public String language;
  /** Narrative */
  public JsObject text;
  public List<Object> contained;
  /** Extension[] */
  public List<JsObject> extension;
  public List<JsObject> modifierExtension;
  public String url;
  public String version;
  public String name;
  public String title;
  /** ImplementationGuideStatus: draft | active | retired | unknown (kept as text; validated by importConfiguration) */
  public String status;
  public Boolean experimental;
  public String date;
  /** The first publisher's name; other publishers/contact details become contact entries. */
  public String publisher;
  /** ContactDetail[] */
  public List<JsObject> contact;
  public String description;
  /** UsageContext[] */
  public List<JsObject> useContext;
  /** CodeableConcept[] */
  public List<JsObject> jurisdiction;
  public String copyright;
  /** R5 IG */
  public String copyrightLabel;
  /** R5 IG */
  public String versionAlgorithmString;
  /** R5 IG; Coding */
  public JsObject versionAlgorithmCoding;
  /** id is used as packageId unless set */
  public String packageId;
  public String license;
  public List<String> fhirVersion;
  /** ImplementationGuideDependsOn[] */
  public List<JsObject> dependencies;
  /** ImplementationGuideGlobal[] */
  public List<JsObject> global;
  public List<ConfigurationGroup> groups;
  public ConfigurationDefinition definition;
  /** ConfigurationResource[] = ImplementationGuideDefinitionResource plus optional boolean "omit" key. */
  public List<JsObject> resources;
  /** ImplementationGuideDefinitionPage[] */
  public List<JsObject> pages;
  /** ImplementationGuideDefinitionParameter[] */
  public List<JsObject> parameters;
  /** ImplementationGuideDefinitionTemplate[] */
  public List<JsObject> templates;
  /** No longer supported; kept so IGExporter can report it. */
  public String template;
  public List<ConfigurationMenuItem> menu;
  /** No longer supported; kept so IGExporter can report it. */
  public ConfigurationHistory history;
  public String indexPageContent;
  public Boolean FSHOnly;
  public Boolean applyExtensionMetadataToRoot;
  public ConfigurationInstanceOptions instanceOptions;

  public static class ConfigurationGroup {
    public String id;
    public String name;
    public String description;
    public List<String> resources;
  }

  /** Other IG.definition properties have top-level configuration properties. */
  public static class ConfigurationDefinition {
    /** Extension[] */
    public List<JsObject> extension;
  }

  public static class ConfigurationMenuItem {
    public String name;
    public String url;
    public Boolean openInNewTab;
    public List<ConfigurationMenuItem> subMenu;
  }

  public static class ConfigurationHistory {
    /** TS key 'package-id' */
    public String packageId;
    public String canonical;
    public String title;
    public String introduction;
    public List<ConfigurationHistoryItem> list;
  }

  public static class ConfigurationHistoryItem {
    public String version;
    public String date;
    public String desc;
    public String path;
    public String changes;
    /** ci-build | preview | ballot | trial-use | update | normative | trial-use+normative */
    public String status;
    public String sequence;
    public String fhirversion;
    public Boolean current;
  }

  public static class ConfigurationInstanceOptions {
    /** always (default) | never | inline-only | standalone-only */
    public String setMetaProfile;
    /** always (default) | standalone-only */
    public String setId;
    public Boolean manualSliceOrdering;
  }
}
