package org.hl7.fhir.sushi.fhirtypes;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.utils.SanitizeFilename;

// port of sushi/src/fhirtypes/InstanceDefinition.ts (SUSHI 3.21.1)
/**
 * A FHIR instance: a dynamic JS object (arbitrary FHIR properties added by path, in assignment order) plus the
 * non-FHIR {@link #_instanceMeta}, which TS keeps as the first own property but never serializes.
 */
public class InstanceDefinition extends JsObject implements FhirResource, HasId, Js.DeepCloneable {
  private static final long serialVersionUID = 1L;

  /** TS InstanceMeta: { name, title, description, usage, sdType, sdKind, instanceOfUrl }. */
  public static class InstanceMeta {
    public String name;
    public String title;
    public String description;
    /** "Example" | "Definition" | "Inline" (TS InstanceUsage string values) */
    public String usage;
    public String sdType;
    public String sdKind;
    public String instanceOfUrl;

    public InstanceMeta copy() {
      InstanceMeta m = new InstanceMeta();
      m.name = name;
      m.title = title;
      m.description = description;
      m.usage = usage;
      m.sdType = sdType;
      m.sdKind = sdKind;
      m.instanceOfUrl = instanceOfUrl;
      return m;
    }
  }

  public InstanceMeta _instanceMeta = new InstanceMeta();

  private String str(String key) {
    Object v = get(key);
    return v == null || v == Js.UNDEFINED ? null : v instanceof String s ? s : Js.toStr(v);
  }

  @Override public String getId() { return str("id"); }
  @Override public void setId(String id) { put("id", id); }
  public String getResourceType() { return str("resourceType"); }
  public void setResourceType(String resourceType) { put("resourceType", resourceType); }
  /** Skeleton-era accessors (same as getId()/getResourceType()). */
  public String id() { return getId(); }
  public String resourceType() { return getResourceType(); }

  /** Logical instances use the Binary type (https://fshschool.org/docs/sushi/tips/#instances-of-logical-models). */
  @Override public String getFileName() {
    Object type = "logical".equals(_instanceMeta.sdKind) ? "Binary" : rawOrUndefined("resourceType");
    Object id = get("id") != null && get("id") != Js.UNDEFINED ? get("id")
        : (_instanceMeta.name == null ? Js.UNDEFINED : _instanceMeta.name);
    return SanitizeFilename.sanitize(Common.toStrUndef(type) + "-" + Common.toStrUndef(id) + ".json", "-");
  }

  private Object rawOrUndefined(String key) {
    return containsKey(key) ? get(key) : Js.UNDEFINED;
  }

  @Override public Object toJSON() {
    List<String> orderedKeys = new ArrayList<>();
    for (String key : List.of("resourceType", "_resourceType", "id", "_id", "meta", "_meta")) {
      Object v = get(key);
      if (v != null && v != Js.UNDEFINED) orderedKeys.add(key);
    }
    // _instanceMeta is only needed for lookup and IG config - not a FHIR property (it is a Java field here)
    List<String> keys = new ArrayList<>(orderedKeys);
    for (String k : Js.ownKeys(this)) if (!orderedKeys.contains(k)) keys.add(k);
    return Common.orderedCloneDeep(this, keys);
  }

  public static InstanceDefinition fromJSON(Map<String, ?> json) {
    @SuppressWarnings("unchecked") Map<String, Object> clone = (Map<String, Object>) Js.cloneDeep(json);
    InstanceDefinition instanceDefinition = new InstanceDefinition();
    for (String key : Js.ownKeys(clone)) {
      instanceDefinition.put(key, clone.get(key));
    }
    // Default the meta name to the id
    Object id = clone.get("id");
    if (id != null && id != Js.UNDEFINED) {
      instanceDefinition._instanceMeta.name = id instanceof String s ? s : Js.toStr(id);
    }
    return instanceDefinition;
  }

  /** lodash cloneDeep keeps the prototype and _instanceMeta. */
  @Override public Object cloneDeep(IdentityHashMap<Object, Object> seen) {
    InstanceDefinition c = new InstanceDefinition();
    seen.put(this, c);
    c._instanceMeta = _instanceMeta == null ? null : _instanceMeta.copy();
    for (Map.Entry<String, Object> e : entrySet()) c.put(e.getKey(), Js.cloneDeep(e.getValue(), seen));
    return c;
  }
}
