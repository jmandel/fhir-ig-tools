package org.hl7.fhir.sushi.fhirtypes;

import java.util.IdentityHashMap;
import java.util.Map;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.SanitizeFilename;

// port of sushi/src/fhirtypes/ValueSet.ts (SUSHI 3.21.1)
/**
 * A FHIR ValueSet, backed by an insertion-ordered JS object (see {@link CodeSystem}): "resourceType", "status",
 * then every other property in assignment order.
 */
public class ValueSet extends JsObject implements FhirResource, HasId, HasName, Js.DeepCloneable {
  private static final long serialVersionUID = 1L;

  public ValueSet() {
    put("resourceType", "ValueSet");
    put("status", "draft");
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

  /** The StructureDefinition of ValueSet (rebuilt from JSON on every call, as in TS). */
  public StructureDefinition getOwnStructureDefinition(Fishable fisher) {
    return StructureDefinition.fromJSON(fisher.fishForFHIR("ValueSet", FishableType.Resource));
  }

  @Override public String getFileName() {
    return SanitizeFilename.sanitize("ValueSet-" + CodeSystem.idForFileName(this) + ".json", "-");
  }

  @Override public Object toJSON() {
    return Common.orderedCloneDeep(this);
  }

  @Override public Object cloneDeep(IdentityHashMap<Object, Object> seen) {
    ValueSet c = new ValueSet();
    c.clear();
    seen.put(this, c);
    for (Map.Entry<String, Object> e : entrySet()) c.put(e.getKey(), Js.cloneDeep(e.getValue(), seen));
    return c;
  }
}
