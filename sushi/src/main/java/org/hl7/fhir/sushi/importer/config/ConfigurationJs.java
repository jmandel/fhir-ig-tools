package org.hl7.fhir.sushi.importer.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.Configuration.ConfigurationDefinition;
import org.hl7.fhir.sushi.fshtypes.Configuration.ConfigurationGroup;
import org.hl7.fhir.sushi.fshtypes.Configuration.ConfigurationHistory;
import org.hl7.fhir.sushi.fshtypes.Configuration.ConfigurationHistoryItem;
import org.hl7.fhir.sushi.fshtypes.Configuration.ConfigurationInstanceOptions;
import org.hl7.fhir.sushi.fshtypes.Configuration.ConfigurationMenuItem;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;

/**
 * Between the TS configuration object (a {@link JsObject} with the keys importConfiguration builds, in its order) and
 * the typed {@link Configuration}. {@link #toJs} gives the object TS would hold (key order of importConfiguration's
 * object literal; JSON.stringify of it equals TS's), e.g. for logging or comparisons.
 *
 * <p>Where TS keeps a raw YAML value of an unexpected type in a typed field (e.g. {@code title: 2023} is the number
 * 2023 in TS), the typed field gets the JS string of a scalar ({@code "2023"}) and null for a collection, and a
 * non-boolean in a boolean field its JS truthiness. Valid configs never hit this (all 266 corpus configs keep their
 * types); the mutation report lists it.
 */
public final class ConfigurationJs {
  private ConfigurationJs() {}

  private static final List<String> ORDER = List.of("filePath", "canonical", "id", "meta", "implicitRules",
      "language", "text", "contained", "extension", "modifierExtension", "url", "version", "name", "title", "status",
      "experimental", "date", "publisher", "contact", "description", "useContext", "jurisdiction", "copyright",
      "copyrightLabel", "versionAlgorithmString", "versionAlgorithmCoding", "packageId", "license", "fhirVersion",
      "dependencies", "global", "groups", "definition", "resources", "pages", "parameters", "templates", "template",
      "menu", "history", "indexPageContent", "FSHOnly", "applyExtensionMetadataToRoot", "instanceOptions");

  // ---------------------------------------------------------------- JS -> typed

  public static Configuration fromJs(Map<String, Object> js) {
    Configuration c = new Configuration();
    c.filePath = str(js.get("filePath"));
    c.canonical = str(js.get("canonical"));
    c.id = str(js.get("id"));
    c.meta = obj(js.get("meta"));
    c.implicitRules = str(js.get("implicitRules"));
    c.language = str(js.get("language"));
    c.text = obj(js.get("text"));
    c.contained = list(js.get("contained"));
    c.extension = objList(js.get("extension"));
    c.modifierExtension = objList(js.get("modifierExtension"));
    c.url = str(js.get("url"));
    c.version = str(js.get("version"));
    c.name = str(js.get("name"));
    c.title = str(js.get("title"));
    c.status = str(js.get("status"));
    c.experimental = bool(js.get("experimental"));
    c.date = str(js.get("date"));
    c.publisher = str(js.get("publisher"));
    c.contact = objList(js.get("contact"));
    c.description = str(js.get("description"));
    c.useContext = objList(js.get("useContext"));
    c.jurisdiction = objList(js.get("jurisdiction"));
    c.copyright = str(js.get("copyright"));
    c.copyrightLabel = str(js.get("copyrightLabel"));
    c.versionAlgorithmString = str(js.get("versionAlgorithmString"));
    c.versionAlgorithmCoding = obj(js.get("versionAlgorithmCoding"));
    c.packageId = str(js.get("packageId"));
    c.license = str(js.get("license"));
    c.fhirVersion = strList(js.get("fhirVersion"));
    c.dependencies = objList(js.get("dependencies"));
    c.global = objList(js.get("global"));
    if (js.get("groups") instanceof List<?> groups) {
      c.groups = new ArrayList<>();
      for (Object o : groups) {
        Map<String, Object> g = asMap(o);
        ConfigurationGroup cg = new ConfigurationGroup();
        cg.id = str(g.get("id"));
        cg.name = str(g.get("name"));
        cg.description = str(g.get("description"));
        cg.resources = strList(g.get("resources"));
        c.groups.add(cg);
      }
    }
    if (js.get("definition") instanceof Map<?, ?> d) {
      c.definition = new ConfigurationDefinition();
      c.definition.extension = objList(d.get("extension"));
    }
    c.resources = objList(js.get("resources"));
    c.pages = objList(js.get("pages"));
    c.parameters = objList(js.get("parameters"));
    c.templates = objList(js.get("templates"));
    c.template = str(js.get("template"));
    c.menu = menu(js.get("menu"));
    if (js.get("history") instanceof Map<?, ?> h) {
      ConfigurationHistory ch = new ConfigurationHistory();
      ch.packageId = str(h.get("package-id"));
      ch.canonical = str(h.get("canonical"));
      ch.title = str(h.get("title"));
      ch.introduction = str(h.get("introduction"));
      if (h.get("list") instanceof List<?> items) {
        ch.list = new ArrayList<>();
        for (Object o : items) {
          Map<String, Object> i = asMap(o);
          ConfigurationHistoryItem hi = new ConfigurationHistoryItem();
          hi.version = str(i.get("version"));
          hi.date = str(i.get("date"));
          hi.desc = str(i.get("desc"));
          hi.path = str(i.get("path"));
          hi.changes = str(i.get("changes"));
          hi.status = str(i.get("status"));
          hi.sequence = str(i.get("sequence"));
          hi.fhirversion = str(i.get("fhirversion"));
          hi.current = bool(i.get("current"));
          ch.list.add(hi);
        }
      }
      c.history = ch;
    }
    c.indexPageContent = str(js.get("indexPageContent"));
    c.FSHOnly = bool(js.get("FSHOnly"));
    c.applyExtensionMetadataToRoot = bool(js.get("applyExtensionMetadataToRoot"));
    if (js.get("instanceOptions") instanceof Map<?, ?> io) {
      ConfigurationInstanceOptions o = new ConfigurationInstanceOptions();
      o.setMetaProfile = str(io.get("setMetaProfile"));
      o.setId = str(io.get("setId"));
      o.manualSliceOrdering = bool(io.get("manualSliceOrdering"));
      c.instanceOptions = o;
    }
    return c;
  }

  private static List<ConfigurationMenuItem> menu(Object v) {
    if (!(v instanceof List<?> l)) return null;
    List<ConfigurationMenuItem> out = new ArrayList<>();
    for (Object o : l) {
      Map<String, Object> m = asMap(o);
      ConfigurationMenuItem item = new ConfigurationMenuItem();
      item.name = str(m.get("name"));
      item.url = str(m.get("url"));
      item.openInNewTab = bool(m.get("openInNewTab"));
      item.subMenu = menu(m.get("subMenu"));
      out.add(item);
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object o) {
    return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new JsObject();
  }

  private static boolean absent(Object v) {
    return v == null || v == Js.UNDEFINED;
  }

  private static String str(Object v) {
    if (absent(v)) return null;
    if (v instanceof String s) return s;
    if (v instanceof Map<?, ?> || v instanceof List<?>) return null;
    return JsOps.templ(v);
  }

  private static Boolean bool(Object v) {
    if (absent(v)) return null;
    if (v instanceof Boolean b) return b;
    return Js.truthy(v);
  }

  private static JsObject obj(Object v) {
    if (v instanceof JsObject j) return j;
    if (v instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) m;
      return new JsObject(mm);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object v) {
    return v instanceof List<?> l ? (List<Object>) l : null;
  }

  /** A list of JS objects; non-object members (malformed YAML) are dropped. */
  private static List<JsObject> objList(Object v) {
    if (!(v instanceof List<?> l)) return null;
    List<JsObject> out = new ArrayList<>();
    for (Object o : l) {
      JsObject j = obj(o);
      if (j != null) out.add(j);
    }
    return out;
  }

  private static List<String> strList(Object v) {
    if (!(v instanceof List<?> l)) return null;
    List<String> out = new ArrayList<>();
    for (Object o : l) out.add(str(o));
    return out;
  }

  // ---------------------------------------------------------------- typed -> JS

  /** The TS configuration object for {@code c}: importConfiguration's key order, undefined fields omitted. */
  public static JsObject toJs(Configuration c) {
    JsObject js = new JsObject();
    put(js, "filePath", c.filePath);
    put(js, "canonical", c.canonical);
    put(js, "id", c.id);
    put(js, "meta", c.meta);
    put(js, "implicitRules", c.implicitRules);
    put(js, "language", c.language);
    put(js, "text", c.text);
    put(js, "contained", c.contained);
    put(js, "extension", c.extension);
    put(js, "modifierExtension", c.modifierExtension);
    put(js, "url", c.url);
    put(js, "version", c.version);
    put(js, "name", c.name);
    put(js, "title", c.title);
    put(js, "status", c.status);
    put(js, "experimental", c.experimental);
    put(js, "date", c.date);
    put(js, "publisher", c.publisher);
    put(js, "contact", c.contact);
    put(js, "description", c.description);
    put(js, "useContext", c.useContext);
    put(js, "jurisdiction", c.jurisdiction);
    put(js, "copyright", c.copyright);
    put(js, "copyrightLabel", c.copyrightLabel);
    put(js, "versionAlgorithmString", c.versionAlgorithmString);
    put(js, "versionAlgorithmCoding", c.versionAlgorithmCoding);
    put(js, "packageId", c.packageId);
    put(js, "license", c.license);
    put(js, "fhirVersion", c.fhirVersion);
    put(js, "dependencies", c.dependencies);
    put(js, "global", c.global);
    if (c.groups != null) {
      List<Object> groups = new ArrayList<>();
      for (ConfigurationGroup g : c.groups) {
        // parseGroups keeps undefined description/resources keys
        groups.add(new JsObject().with("id", g.id).with("name", g.name).with("description", undef(g.description))
            .with("resources", undef(g.resources)));
      }
      js.put("groups", groups);
    }
    if (c.definition != null) {
      js.put("definition", new JsObject().with("extension", c.definition.extension));
    }
    put(js, "resources", c.resources);
    put(js, "pages", c.pages);
    put(js, "parameters", c.parameters);
    put(js, "templates", c.templates);
    put(js, "template", c.template);
    put(js, "menu", menuJs(c.menu));
    if (c.history != null) {
      ConfigurationHistory h = c.history;
      JsObject hj = new JsObject().with("package-id", undef(h.packageId)).with("canonical", undef(h.canonical))
          .with("title", undef(h.title)).with("introduction", undef(h.introduction));
      if (h.list != null) {
        List<Object> list = new ArrayList<>();
        for (ConfigurationHistoryItem i : h.list) {
          JsObject ij = new JsObject();
          put(ij, "version", i.version);
          put(ij, "date", i.date);
          put(ij, "desc", i.desc);
          put(ij, "path", i.path);
          put(ij, "changes", i.changes);
          put(ij, "status", i.status);
          put(ij, "sequence", i.sequence);
          put(ij, "fhirversion", i.fhirversion);
          put(ij, "current", i.current);
          list.add(ij);
        }
        hj.put("list", list);
      }
      js.put("history", hj);
    }
    put(js, "indexPageContent", c.indexPageContent);
    put(js, "FSHOnly", c.FSHOnly);
    put(js, "applyExtensionMetadataToRoot", c.applyExtensionMetadataToRoot);
    if (c.instanceOptions != null) {
      JsObject io = new JsObject();
      put(io, "setMetaProfile", c.instanceOptions.setMetaProfile);
      put(io, "setId", c.instanceOptions.setId);
      put(io, "manualSliceOrdering", c.instanceOptions.manualSliceOrdering);
      js.put("instanceOptions", io);
    }
    return js;
  }

  /** The keys of the TS configuration object, in importConfiguration's order. */
  public static List<String> keyOrder() {
    return ORDER;
  }

  private static Object undef(Object v) {
    return v == null ? Js.UNDEFINED : v;
  }

  private static void put(JsObject js, String k, Object v) {
    if (v != null) js.put(k, v);
  }

  private static List<Object> menuJs(List<ConfigurationMenuItem> menu) {
    if (menu == null) return null;
    List<Object> out = new ArrayList<>();
    for (ConfigurationMenuItem i : menu) {
      JsObject m = new JsObject().with("name", i.name);
      put(m, "openInNewTab", i.openInNewTab);
      put(m, "url", i.url);
      // parseMenu assigns subMenu for every non-string value, even when it is undefined
      if (i.url == null) m.put("subMenu", undef(menuJs(i.subMenu)));
      out.add(m);
    }
    return out;
  }
}
