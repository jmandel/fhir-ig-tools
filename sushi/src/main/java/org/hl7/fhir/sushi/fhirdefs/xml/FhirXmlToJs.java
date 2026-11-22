package org.hl7.fhir.sushi.fhirdefs.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.hl7.fhir.sushi.fhirdefs.xml.XmlJs.XNode;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;

// port of fhir-tool 5.0.2 convertToJs.js (ConvertToJs.convert, i.e. `new Fhir().xmlToObj(xml)`) with
// ParseConformance(true) (its bundled R4 type data: resources/.../fhirtool-types.json.gz, generated from fhir-tool's
// own profiles/types.json by scripts/fhirtool/gen-data.js)
/**
 * FHIR XML to the JS value model the way fhir-package-loader 2.2.4 reads predefined XML resources: R4 type data
 * regardless of the IG's FHIR version, key order as fhir-tool builds the object (`_x` before `x` for single
 * primitives with id/extensions), decimals as strings, integers as numbers, comments as {@code fhir_comments}.
 * Errors carry fhir-tool's/sax's/V8's message texts.
 */
public final class FhirXmlToJs {
  private FhirXmlToJs() {}

  /** A parsed property (fhir-tool's {_name, _type, _multiple, _properties}). */
  record Prop(String name, String type, boolean multiple, List<Prop> properties) {}

  /** A parsed type ({_properties}); properties null = the key exists without _properties. */
  record TypeDef(List<Prop> properties) {}

  private static final Map<String, TypeDef> DEFS = load();

  // Object.prototype members found by `obj[name]` lookups on plain objects (JS quirk)
  private static final Set<String> PROTO = Set.of("constructor", "__defineGetter__", "__defineSetter__",
      "hasOwnProperty", "__lookupGetter__", "__lookupSetter__", "isPrototypeOf", "propertyIsEnumerable", "toString",
      "valueOf", "__proto__", "toLocaleString");

  private static final List<String> PRIMITIVE_TYPES = List.of("instant", "time", "date", "dateTime", "decimal",
      "boolean", "integer", "base64Binary", "string", "uri", "url", "unsignedInt", "positiveInt", "code", "id", "oid",
      "markdown", "canonical", "Element");

  @SuppressWarnings("unchecked")
  private static Map<String, TypeDef> load() {
    try (InputStream in = FhirXmlToJs.class.getResourceAsStream("fhirtool-types.json.gz")) {
      if (in == null) throw new IOException("missing fhirtool-types.json.gz");
      Map<String, Object> data = (Map<String, Object>) Json.parse(
          new String(new GZIPInputStream(in).readAllBytes(), StandardCharsets.UTF_8));
      List<Object> defs = (List<Object>) data.get("defs");
      List<TypeDef> parsed = new ArrayList<>();
      for (Object d : defs) {
        Map<String, Object> m = (Map<String, Object>) d;
        parsed.add(new TypeDef(m.containsKey("noProps") ? null : props((List<Object>) m.get("p"))));
      }
      Map<String, Object> index = (Map<String, Object>) data.get("index");
      Map<String, TypeDef> res = new HashMap<>();
      index.forEach((k, v) -> res.put(k, parsed.get(((Number) v).intValue())));
      return Collections.unmodifiableMap(res);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Prop> props(List<Object> l) {
    List<Prop> res = new ArrayList<>(l.size());
    for (Object o : l) {
      Map<String, Object> m = (Map<String, Object>) o;
      res.add(new Prop((String) m.get("n"), (String) m.get("t"), m.containsKey("m"),
          m.containsKey("p") ? props((List<Object>) m.get("p")) : null));
    }
    return Collections.unmodifiableList(res);
  }

  /** parsedStructureDefinitions[key] incl. the prototype-chain quirk (null = undefined) */
  private static Object def(String key) {
    if (key == null) return null;
    TypeDef d = DEFS.get(key);
    if (d != null) return d;
    return PROTO.contains(key) ? PROTO : null;
  }

  /** fhir-tool xmlToObj(xml) */
  public static Object xmlToObj(String xml) {
    XNode xmlObj = XmlJs.xml2js(xml);
    if (xmlObj.elements == null) {
      throw new IllegalStateException("Cannot read properties of undefined (reading 'find')");
    }
    for (XNode e : xmlObj.elements) {
      if (e.type.equals("element")) return resourceToJS(e);
    }
    return null;
  }

  private static JsObject resourceToJS(XNode xmlObj) {
    if (xmlObj == null) throw new IllegalStateException("Cannot read properties of undefined (reading 'name')");
    Object typeDefinition = def(xmlObj.name);
    JsObject resource = new JsObject();
    resource.put("resourceType", xmlObj.name);
    if (typeDefinition == null) {
      throw new IllegalStateException("Unknown resource type: " + xmlObj.name);
    }
    List<Prop> props = typeDefinition instanceof TypeDef td ? td.properties() : null;
    if (props == null) throw new IllegalStateException("Cannot read properties of undefined (reading 'forEach')");
    for (Prop property : props) propertyToJS(xmlObj, resource, property);
    return resource;
  }

  private static Prop findReferenceType(String relativeType) {
    if (relativeType == null || !relativeType.startsWith("#")) return null;
    int dot = relativeType.indexOf('.');
    // JS substring(1, -1) = substring(0, 1) swapped -> substring(1, 1)... keep JS semantics for indexOf = -1
    String resourceType = dot < 0 ? jsSubstring(relativeType, 1, -1) : relativeType.substring(1, dot);
    String path = jsSubstring(relativeType, resourceType.length() + 2, relativeType.length());
    Object resourceDefinition = def(resourceType);
    String[] pathSplit = path.split(Pattern.quote("."), -1);
    if (resourceDefinition == null) {
      throw new IllegalStateException("Could not find resource definition for " + resourceType);
    }
    List<Prop> current = resourceDefinition instanceof TypeDef td ? td.properties() : null;
    Prop cur = null;
    for (String nextPath : pathSplit) {
      if (current == null) throw new IllegalStateException("Cannot read properties of undefined (reading 'find')");
      cur = null;
      for (Prop p : current) {
        if (p.name().equals(nextPath)) {
          cur = p;
          break;
        }
      }
      if (cur == null) return null;
      current = cur.properties();
    }
    return cur;
  }

  /** JS String.prototype.substring (negative -> 0, swapped bounds) */
  private static String jsSubstring(String s, int a, int b) {
    int start = Math.min(Math.max(a, 0), s.length()), end = Math.min(Math.max(b, 0), s.length());
    return start <= end ? s.substring(start, end) : s.substring(end, start);
  }

  private static boolean isArray(Object o) {
    return o instanceof List<?>;
  }

  @SuppressWarnings("unchecked")
  private static void propertyToJS(XNode xmlObj, JsObject obj, Prop property) {
    List<XNode> xmlProperty = new ArrayList<>();
    if (xmlObj.elements != null) {
      for (XNode element : xmlObj.elements) {
        if (property.name().equals(element.name)) xmlProperty.add(element);
      }
    }
    if (xmlObj.attributes != null) {
      for (Map.Entry<String, String> a : xmlObj.attributes.entrySet()) {
        if (a.getKey().equals(property.name())) {
          XNode attr = new XNode("attribute");
          attr.name = a.getKey();
          attr.attributes = new java.util.LinkedHashMap<>();
          attr.attributes.put("value", a.getValue());
          xmlProperty.add(attr);
        }
      }
    }
    if (xmlProperty.isEmpty()) return;

    if (property.type() != null && property.type().indexOf('#') == 0) {
      Prop relativeType = findReferenceType(property.type());
      if (relativeType == null) {
        throw new IllegalStateException("Could not find reference to element definition undefined");
      }
      property = new Prop(property.name(), relativeType.type(), property.multiple(), relativeType.properties());
    }
    final Prop prop = property;
    final String name = prop.name();
    final String extraName = "_" + name;

    if (prop.multiple()) obj.put(name, new ArrayList<Object>());

    for (int i = 0; i < xmlProperty.size(); i++) {
      List<XNode> xmlCommentElements = null;
      XNode nextXmlComment = null;
      int xmlPropertyIndex = xmlObj.elements == null ? -1 : indexOf(xmlObj.elements, xmlProperty.get(i));
      while (nextXmlComment != null || xmlCommentElements == null) {
        if (xmlCommentElements == null) xmlCommentElements = new ArrayList<>();
        int nextIndex = xmlCommentElements.size() + 1;
        if (xmlPropertyIndex - nextIndex < 0) break;
        nextXmlComment = xmlPropertyIndex > 0 && xmlObj.elements.get(xmlPropertyIndex - nextIndex).type.equals("comment")
            ? xmlObj.elements.get(xmlPropertyIndex - nextIndex) : null;
        if (nextXmlComment != null) xmlCommentElements.add(nextXmlComment);
      }
      pushValue(obj, prop, xmlProperty.get(i), i);
      if (xmlCommentElements != null && !xmlCommentElements.isEmpty()) {
        List<Object> comments = new ArrayList<>();
        for (int k = xmlCommentElements.size() - 1; k >= 0; k--) comments.add(xmlCommentElements.get(k).value);
        String target = PRIMITIVE_TYPES.contains(prop.type()) ? extraName : name;
        if (prop.multiple()) {
          if (!Js.truthy(obj.get(target))) obj.put(target, new ArrayList<Object>());
          List<Object> arr = (List<Object>) obj.get(target);
          Object at = i < arr.size() ? arr.get(i) : null;
          if (!Js.truthy(at) || at == Js.UNDEFINED) {
            while (arr.size() <= i) arr.add(Js.UNDEFINED); // JS assignment past the end leaves holes
            arr.set(i, new JsObject());
          }
          ((Map<String, Object>) arr.get(i)).put("fhir_comments", comments);
        } else {
          if (!Js.truthy(obj.get(target))) obj.put(target, new JsObject());
          Object t = obj.get(target);
          if (t instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).put("fhir_comments", comments);
          } else {
            // convertToJs.js is strict-mode code: a property on a primitive (e.g. the xhtml div string) throws
            String kind = t instanceof String ? "string" : t instanceof Boolean ? "boolean" : "number";
            throw new IllegalStateException("Cannot create property 'fhir_comments' on " + kind + " '" + Js.toStr(t)
                + "'");
          }
        }
      }
    }
  }

  private static int indexOf(List<XNode> l, XNode n) {
    for (int i = 0; i < l.size(); i++) if (l.get(i) == n) return i;
    return -1;
  }

  @SuppressWarnings("unchecked")
  private static void addExtra(JsObject obj, Prop property, XNode element, int index) {
    String extraName = "_" + property.name();
    boolean hasId = element.attributes != null && Js.truthy(element.attributes.get("id"));
    boolean hasExtensions = false;
    if (element.elements != null) {
      for (XNode n : element.elements) {
        if ("extension".equals(n.name)) {
          hasExtensions = true;
          break;
        }
      }
    }
    if (hasId || hasExtensions) {
      if (!Js.truthy(obj.get(extraName))) {
        obj.put(extraName, isArray(obj.get(property.name())) ? new ArrayList<Object>() : new JsObject());
      }
    }
    Object dest = obj.get(extraName);
    if (hasId || hasExtensions) {
      if (dest instanceof List<?> dl) {
        List<Object> d = (List<Object>) dl;
        if (d.size() < index + 1) {
          for (int i = 0; i < index; i++) {
            if (i >= d.size()) d.add(null);
            else if (!Js.truthy(d.get(i)) || d.get(i) == Js.UNDEFINED) d.set(i, null);
          }
        }
        while (d.size() <= index) d.add(Js.UNDEFINED);
        d.set(index, new JsObject());
      }
    }
    if (hasId) {
      Object target = dest instanceof List<?> dl ? dl.get(index) : dest;
      setProp(target, "id", element.attributes.get("id"));
    }
    if (hasExtensions) {
      Prop extensionProperty = new Prop("extension", "Extension", true, null);
      Object target = dest instanceof List<?> dl ? dl.get(index) : dest;
      if (target instanceof JsObject jo) {
        propertyToJS(element, jo, extensionProperty);
      } else {
        // a non-object `_x` (can't happen with fhir-tool's own data): JS would write to a wrapper and lose it
        propertyToJS(element, new JsObject(), extensionProperty);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void setProp(Object target, String key, Object value) {
    if (target instanceof Map<?, ?> m) ((Map<String, Object>) m).put(key, value);
  }

  @SuppressWarnings("unchecked")
  private static void put(JsObject obj, String name, Object v) {
    Object cur = obj.get(name);
    if (cur instanceof List<?> l) ((List<Object>) l).add(v);
    else obj.put(name, v);
  }

  private static String valueAttr(XNode value, boolean guarded) {
    if (value.attributes == null) {
      if (guarded) return null;
      throw new IllegalStateException("Cannot read properties of undefined (reading 'value')");
    }
    return value.attributes.get("value");
  }

  private static void pushValue(JsObject obj, Prop property, XNode value, int index) {
    if (value == null) return;
    String type = property.type() == null ? "undefined" : property.type();
    switch (type) {
      case "string", "base64Binary", "code", "id", "markdown", "uri", "url", "canonical", "oid", "date", "dateTime",
          "time", "instant" -> {
        addExtra(obj, property, value, index);
        String v = valueAttr(value, true);
        if (Js.truthy(v)) put(obj, property.name(), v);
      }
      case "decimal" -> {
        addExtra(obj, property, value, index);
        String v = valueAttr(value, false);
        if (Js.truthy(v)) put(obj, property.name(), convertDecimal(v));
      }
      case "boolean" -> {
        addExtra(obj, property, value, index);
        String v = valueAttr(value, false);
        if (Js.truthy(v)) put(obj, property.name(), toBoolean(v));
      }
      case "integer", "unsignedInt", "positiveInt" -> {
        addExtra(obj, property, value, index);
        String v = valueAttr(value, true);
        if (Js.truthy(v)) put(obj, property.name(), toNumber(v));
      }
      case "xhtml" -> {
        if (value.elements != null && !value.elements.isEmpty()) {
          put(obj, property.name(), XmlJs.js2xml(XmlJs.escapeInvalidCharacters(value)));
        }
      }
      case "Element", "BackboneElement" -> {
        JsObject newValue = new JsObject();
        if (property.properties() != null) {
          for (Prop next : property.properties()) propertyToJS(value, newValue, next);
        }
        put(obj, property.name(), newValue);
      }
      case "Resource" -> {
        if (value.elements != null && !value.elements.isEmpty()) {
          int elementIndex = -1;
          for (int k = 0; k < value.elements.size(); k++) {
            if (value.elements.get(k).type.equals("element")) {
              elementIndex = k;
              break;
            }
          }
          JsObject newJS = resourceToJS(elementIndex < 0 ? null : value.elements.get(elementIndex));
          if (value.elements.size() > 1) {
            List<Object> comments = new ArrayList<>();
            for (XNode e : value.elements) if (e.type.equals("comment")) comments.add(e.value);
            if (!comments.isEmpty()) {
              if (!Js.truthy(newJS.get("fhir_comments"))) newJS.put("fhir_comments", new ArrayList<Object>());
              @SuppressWarnings("unchecked")
              List<Object> fc = (List<Object>) newJS.get("fhir_comments");
              fc.addAll(comments);
            }
          }
          put(obj, property.name(), newJS);
        }
      }
      default -> {
        Object nextType = def(type);
        if (nextType == null) {
          System.out.println("do something"); // fhir-tool's console.log
        } else {
          List<Prop> props = nextType instanceof TypeDef td ? td.properties() : null;
          if (props == null) {
            throw new IllegalStateException("Cannot read properties of undefined (reading 'forEach')");
          }
          JsObject newValue = new JsObject();
          for (Prop next : props) propertyToJS(value, newValue, next);
          put(obj, property.name(), newValue);
        }
      }
    }
  }

  private static Boolean toBoolean(String value) {
    if (value.equals("true")) return true;
    if (value.equals("false")) return false;
    throw new IllegalStateException("Value should be a boolean but got: " + value);
  }

  private static final Pattern INT = Pattern.compile("^-?\\d+\\z");
  private static final Pattern DEC = Pattern.compile("^-?([0]|([1-9][0-9]*))(\\.[0-9]+)?\\z");

  private static Double toNumber(String value) {
    if (!INT.matcher(value).find()) throw new IllegalStateException("Value should be a number but got: " + value);
    return Double.parseDouble(value); // parseInt(value, 10) on an all-digit string: the correctly rounded double
  }

  private static String convertDecimal(String value) {
    if (!DEC.matcher(value).find()) {
      throw new IllegalStateException("Value should be a decimal number but got: " + value);
    }
    return value;
  }
}
