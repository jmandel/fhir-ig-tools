package org.hl7.fhir.sushi.fhirtypes;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.errors.InvalidTypeError;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.common.js.JsonSerializable;

// port of class ElementDefinitionType in sushi/src/fhirtypes/ElementDefinition.ts (SUSHI 3.21.1)
/**
 * One entry of ElementDefinition.type. Like the TS class instance, it is a JS object whose own properties are, in
 * assignment order, {@code _actualCode} (always first) and whatever else was assigned ({@code profile},
 * {@code _profile}, {@code targetProfile}, ...). {@link #fromJSON} assigns {@code profile}, {@code targetProfile},
 * {@code aggregation}, {@code versioning} and {@code extension} even when the JSON lacks them (JS undefined values;
 * they count for {@link #equals}, which is lodash isEqual, and are skipped on output). {@code code} is an accessor:
 * the fhir-type extension's valueUrl/valueUri if present, else {@code _actualCode}.
 */
public class ElementDefinitionType implements PropertyHolder, JsonSerializable, Js.DeepCloneable,
    InvalidTypeError.AllowedType {
  static final String FHIR_TYPE_EXTENSION = "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type";

  private final JsObject props = new JsObject();

  public ElementDefinitionType(String code) {
    props.put("_actualCode", code == null ? Js.UNDEFINED : code);
  }

  private ElementDefinitionType() {}

  // ------------------------------------------------------------------------------------------------ code accessor

  /**
   * Element.id, Extension.url, and primitive types are specified in the valueUrl of an extension. This returns the
   * fhir-type extension's valueUrl if available, else the code.
   */
  @Override public String getCode() {
    List<Object> ext = getExtension();
    if (ext != null) {
      for (Object e : ext) {
        if (e instanceof Map<?, ?> m && FHIR_TYPE_EXTENSION.equals(m.get("url"))) {
          // R4 uses valueUrl; R5 uses valueUri
          Object url = nn(m.get("valueUrl"));
          if (url != null) return (String) url;
          Object uri = nn(m.get("valueUri"));
          if (uri != null) return (String) uri;
          return getActualCode();
        }
      }
    }
    return getActualCode();
  }

  public void setCode(String c) {
    props.put("_actualCode", c == null ? Js.UNDEFINED : c);
  }

  public String getActualCode() {
    return (String) nn(props.get("_actualCode"));
  }

  public ElementDefinitionType withProfiles(String... profiles) {
    setProfile(new ArrayList<>(List.of(profiles)));
    return this;
  }

  public ElementDefinitionType withTargetProfiles(String... targetProfiles) {
    setTargetProfile(new ArrayList<>(List.of(targetProfiles)));
    return this;
  }

  // ------------------------------------------------------------------------------------------------ typed accessors

  @SuppressWarnings("unchecked")
  @Override public List<String> getProfile() {
    return (List<String>) nn(props.get("profile"));
  }

  public void setProfile(List<String> v) {
    set("profile", v);
  }

  @SuppressWarnings("unchecked")
  @Override public List<String> getTargetProfile() {
    return (List<String>) nn(props.get("targetProfile"));
  }

  public void setTargetProfile(List<String> v) {
    set("targetProfile", v);
  }

  @SuppressWarnings("unchecked")
  public List<Object> get_profile() {
    return (List<Object>) nn(props.get("_profile"));
  }

  public void set_profile(List<Object> v) {
    set("_profile", v);
  }

  @SuppressWarnings("unchecked")
  public List<Object> get_targetProfile() {
    return (List<Object>) nn(props.get("_targetProfile"));
  }

  public void set_targetProfile(List<Object> v) {
    set("_targetProfile", v);
  }

  @SuppressWarnings("unchecked")
  public List<String> getAggregation() {
    return (List<String>) nn(props.get("aggregation"));
  }

  public void setAggregation(List<String> v) {
    set("aggregation", v);
  }

  public String getVersioning() {
    return (String) nn(props.get("versioning"));
  }

  public void setVersioning(String v) {
    set("versioning", v);
  }

  @SuppressWarnings("unchecked")
  public List<Object> getExtension() {
    return (List<Object>) nn(props.get("extension"));
  }

  public void setExtension(List<Object> v) {
    set("extension", v);
  }

  // ------------------------------------------------------------------------------------------------ PropertyHolder

  @Override public Object getProperty(String key) {
    if ("code".equals(key)) return getCode();
    return nn(props.get(key));
  }

  @Override public boolean hasProperty(String key) {
    if ("code".equals(key)) return true; // accessor on the prototype: `'code' in type` is true
    return props.containsKey(key) && props.get(key) != Js.UNDEFINED;
  }

  /**
   * TS {@code type[key] = value}. A Java null is a JS null (kept on output); pass {@link Js#UNDEFINED} for JS
   * undefined. (The typed setters, by contrast, translate TS assignments of possibly-undefined values: null there
   * stores undefined.)
   */
  @Override public void setProperty(String key, Object value) {
    if ("code".equals(key)) {
      setCode((String) nn(value));
      return;
    }
    props.put(key, value);
  }

  private void set(String key, Object value) {
    props.put(key, value == null ? Js.UNDEFINED : value);
  }

  @Override public void deleteProperty(String key) {
    props.remove(key);
  }

  @Override public List<String> propertyKeys() {
    return Js.ownKeys(props);
  }

  /** The raw own properties (values may be {@link Js#UNDEFINED}); for code that needs exact JS key semantics. */
  public JsObject rawProperties() {
    return props;
  }

  // ------------------------------------------------------------------------------------------------ JSON / clone

  /** TS toJSON(): {@code {code: actualCode, ...ownPropsWithout_actualCode}} (undefined members are skipped later). */
  @Override public JsObject toJSON() {
    JsObject j = new JsObject();
    j.put("code", props.get("_actualCode"));
    for (String k : Js.ownKeys(props)) {
      if (k.equals("_actualCode")) continue;
      j.put(k, Js.cloneDeep(props.get(k)));
    }
    return j;
  }

  public static ElementDefinitionType fromJSON(Object jsonObj) {
    @SuppressWarnings("unchecked")
    Map<String, Object> json = (Map<String, Object>) Js.cloneDeep(jsonObj);
    ElementDefinitionType t = new ElementDefinitionType((String) nn(json.get("code")));
    // TS assigns profile/targetProfile/aggregation/versioning/extension unconditionally (possibly undefined), but
    // _code/_profile/_targetProfile/_aggregation/_versioning only when truthy.
    if (Js.truthy(json.get("_code"))) t.props.put("_code", json.get("_code"));
    t.props.put("profile", undef(json, "profile"));
    if (Js.truthy(json.get("_profile"))) t.props.put("_profile", json.get("_profile"));
    t.props.put("targetProfile", undef(json, "targetProfile"));
    if (Js.truthy(json.get("_targetProfile"))) t.props.put("_targetProfile", json.get("_targetProfile"));
    t.props.put("aggregation", undef(json, "aggregation"));
    if (Js.truthy(json.get("_aggregation"))) t.props.put("_aggregation", json.get("_aggregation"));
    t.props.put("versioning", undef(json, "versioning"));
    if (Js.truthy(json.get("_versioning"))) t.props.put("_versioning", json.get("_versioning"));
    t.props.put("extension", undef(json, "extension"));
    return t;
  }

  private static Object undef(Map<String, Object> json, String key) {
    return json.containsKey(key) ? json.get(key) : Js.UNDEFINED;
  }

  /** lodash cloneDeep of the instance (same class, same own properties in the same order). */
  @Override public ElementDefinitionType cloneDeep(IdentityHashMap<Object, Object> seen) {
    ElementDefinitionType c = new ElementDefinitionType();
    seen.put(this, c);
    for (Map.Entry<String, Object> e : props.entrySet()) c.props.put(e.getKey(), Js.cloneDeep(e.getValue(), seen));
    return c;
  }

  public ElementDefinitionType cloneDeep() {
    return cloneDeep(new IdentityHashMap<>());
  }

  /** lodash isEqual: same class and deep-equal own properties (key order ignored; undefined-valued keys count). */
  @Override public boolean equals(Object o) {
    return o instanceof ElementDefinitionType t && Js.isEqual(props, t.props);
  }

  @Override public int hashCode() {
    return String.valueOf(getActualCode()).hashCode();
  }

  @Override public String toString() {
    return "ElementDefinitionType" + props;
  }

  static Object nn(Object v) {
    return v == Js.UNDEFINED ? null : v;
  }
}
