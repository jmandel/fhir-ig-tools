package org.hl7.fhir.sushi.fhirtypes;

import java.util.IdentityHashMap;
import java.util.Map;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.SanitizeFilename;

// port of sushi/src/fhirtypes/CodeSystem.ts (SUSHI 3.21.1)
/**
 * A FHIR CodeSystem, backed by an insertion-ordered JS object, because TS compiles to ES2018 (no
 * useDefineForClassFields): the JSON key order is the initialised fields first ("resourceType", "status",
 * "content"), then every other property in the order the exporter ASSIGNS it -- not FHIR order (PORTING.md D2).
 * Typed accessors are conveniences over the map.
 */
public class CodeSystem extends JsObject implements FhirResource, HasId, HasName, Js.DeepCloneable {
  private static final long serialVersionUID = 1L;

  public CodeSystem() {
    put("resourceType", "CodeSystem");
    put("status", "draft");
    put("content", "complete");
  }

  private String str(String key) {
    Object v = get(key);
    return v == null || v == Js.UNDEFINED ? null : v instanceof String s ? s : Js.toStr(v);
  }

  public String getResourceType() { return str("resourceType"); }
  @Override public String getId() { return str("id"); }
  @Override public void setId(String id) { put("id", id); }
  @Override public String getName() { return str("name"); }
  @Override public void setName(String name) { put("name", name); }
  public String getUrl() { return str("url"); }
  public void setUrl(String url) { put("url", url); }
  public String getVersion() { return str("version"); }
  public void setVersion(String version) { put("version", version); }
  public String getTitle() { return str("title"); }
  public void setTitle(String title) { put("title", title); }
  public String getStatus() { return str("status"); }
  public void setStatus(String status) { put("status", status); }
  public String getDescription() { return str("description"); }
  public void setDescription(String description) { put("description", description); }
  public String getContent() { return str("content"); }
  public void setContent(String content) { put("content", content); }

  /** The StructureDefinition of CodeSystem (rebuilt from JSON on every call, as in TS). */
  public StructureDefinition getOwnStructureDefinition(Fishable fisher) {
    return StructureDefinition.fromJSON(fisher.fishForFHIR("CodeSystem", FishableType.Resource));
  }

  @Override public String getFileName() {
    return SanitizeFilename.sanitize("CodeSystem-" + idForFileName(this) + ".json", "-");
  }

  static String idForFileName(Map<String, Object> m) {
    return m.containsKey("id") && m.get("id") != Js.UNDEFINED ? Js.toStr(m.get("id")) : "undefined";
  }

  @Override public Object toJSON() {
    return Common.orderedCloneDeep(this);
  }

  /** lodash cloneDeep keeps the prototype. */
  @Override public Object cloneDeep(IdentityHashMap<Object, Object> seen) {
    CodeSystem c = new CodeSystem();
    c.clear();
    seen.put(this, c);
    for (Map.Entry<String, Object> e : entrySet()) c.put(e.getKey(), Js.cloneDeep(e.getValue(), seen));
    return c;
  }
}
