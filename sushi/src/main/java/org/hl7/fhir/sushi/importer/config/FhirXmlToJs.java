package org.hl7.fhir.sushi.importer.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

// port of fhir-tool 5.0.2 convertToJs.ts (ConvertToJs.convert, as Fhir.xmlToObj calls it), R4 model
/**
 * FHIR XML to the JS object fhir-tool's {@code xmlToObj} gives (loadConfigurationFromIgResource reads legacy
 * ImplementationGuide XML resources with it). Driven by fhir-tool's own R4 type model ({@code profiles/types.json}),
 * reduced to the types an ImplementationGuide reaches ({@code fhir-tool-r4-ig-model.json}, made by
 * scripts/oracle/fhir-tool-model.js): same property order, arrays for repeating elements, {@code _x} objects for
 * ids/extensions on primitives, XML comments as {@code fhir_comments}, booleans and integers converted, decimals kept
 * as strings. Other resource types are only recognised by name (enough for "is this the IG?"): {@link #convert}
 * returns just their resourceType. Contained resources and xhtml are not reproduced (unused by SUSHI's config).
 */
final class FhirXmlToJs {
  private FhirXmlToJs() {}

  private static final Map<String, Object> TYPES;
  private static final Set<String> RESOURCE_TYPES = new HashSet<>();

  static {
    try (InputStream in = FhirXmlToJs.class.getResourceAsStream("fhir-tool-r4-ig-model.json")) {
      @SuppressWarnings("unchecked") Map<String, Object> m =
          (Map<String, Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      @SuppressWarnings("unchecked") Map<String, Object> types = (Map<String, Object>) m.get("types");
      TYPES = types;
      for (Object o : (List<?>) m.get("resourceTypes")) RESOURCE_TYPES.add((String) o);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final List<String> PRIMITIVE_TYPES = List.of("instant", "time", "date", "dateTime", "decimal",
      "boolean", "integer", "base64Binary", "string", "uri", "url", "unsignedInt", "positiveInt", "code", "id", "oid",
      "markdown", "canonical", "Element");

  /** A JS Error fhir-tool throws (uncaught in SUSHI). */
  static final class FhirToolError extends RuntimeException {
    FhirToolError(String message) {
      super(message);
    }
  }

  /** xml-js's view of a node: element (with name, attributes, child "elements"), or comment/text. */
  private record XNode(String type, String name, Map<String, String> attributes, List<XNode> elements, String text) {}

  static JsObject convert(String xml) {
    XNode doc = parse(xml);
    XNode first = doc.elements().stream().filter(e -> e.type().equals("element")).findFirst().orElse(null);
    if (first == null) return null;
    if (!first.name().equals("ImplementationGuide") && RESOURCE_TYPES.contains(first.name())) {
      return new JsObject().with("resourceType", first.name());
    }
    return resourceToJS(first);
  }

  @SuppressWarnings("unchecked")
  private static JsObject resourceToJS(XNode xmlObj) {
    Map<String, Object> typeDefinition = (Map<String, Object>) TYPES.get(xmlObj.name());
    JsObject resource = new JsObject().with("resourceType", xmlObj.name());
    if (typeDefinition == null) throw new FhirToolError("Unknown resource type: " + xmlObj.name());
    for (Object p : (List<Object>) typeDefinition.get("_properties")) {
      propertyToJS(xmlObj, resource, (Map<String, Object>) p);
    }
    return resource;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> findReferenceType(String relativeType) {
    String resourceType = relativeType.substring(1, relativeType.indexOf('.'));
    String path = relativeType.substring(resourceType.length() + 2);
    Map<String, Object> current = (Map<String, Object>) TYPES.get(resourceType);
    if (current == null) throw new FhirToolError("Could not find resource definition for " + resourceType);
    for (String next : path.split("\\.", -1)) {
      Map<String, Object> found = null;
      for (Object p : (List<Object>) current.get("_properties")) {
        if (next.equals(((Map<String, Object>) p).get("_name"))) {
          found = (Map<String, Object>) p;
          break;
        }
      }
      if (found == null) return null;
      current = found;
    }
    return new JsObject(current);
  }

  @SuppressWarnings("unchecked")
  private static void propertyToJS(XNode xmlObj, JsObject obj, Map<String, Object> property) {
    String name = (String) property.get("_name");
    List<XNode> xmlProperty = new ArrayList<>();
    if (xmlObj.elements() != null) {
      for (XNode e : xmlObj.elements()) if (e.type().equals("element") && name.equals(e.name())) xmlProperty.add(e);
    }
    if (xmlObj.attributes() != null) {
      for (Map.Entry<String, String> a : xmlObj.attributes().entrySet()) {
        if (a.getKey().equals(name)) {
          xmlProperty.add(new XNode("attribute", a.getKey(), Map.of("value", a.getValue()), null, null));
        }
      }
    }
    if (xmlProperty.isEmpty()) return;
    String type = (String) property.get("_type");
    if (type != null && type.startsWith("#")) {
      Map<String, Object> relativeType = findReferenceType(type);
      if (relativeType == null) throw new FhirToolError("Could not find reference to element definition undefined");
      relativeType.put("_name", name);
      relativeType.put("_multiple", property.get("_multiple"));
      property = relativeType;
      type = (String) property.get("_type");
    }
    boolean multiple = Boolean.TRUE.equals(property.get("_multiple"));
    if (multiple) obj.put(name, new ArrayList<Object>());
    for (int i = 0; i < xmlProperty.size(); i++) {
      XNode xp = xmlProperty.get(i);
      List<String> comments = new ArrayList<>();
      int idx = xmlObj.elements() == null ? -1 : indexOfIdentity(xmlObj.elements(), xp);
      while (true) {
        int nextIndex = comments.size() + 1;
        if (idx - nextIndex < 0) break;
        XNode prev = idx > 0 ? xmlObj.elements().get(idx - nextIndex) : null;
        if (prev == null || !prev.type().equals("comment")) break;
        comments.add(prev.text());
      }
      pushValue(obj, property, name, type, multiple, xp, i);
      if (!comments.isEmpty()) {
        List<Object> fc = new ArrayList<>();
        for (int k = comments.size() - 1; k >= 0; k--) fc.add(comments.get(k));
        if (PRIMITIVE_TYPES.contains(type)) {
          String extra = "_" + name;
          if (multiple) {
            if (!(obj.get(extra) instanceof List<?>)) obj.put(extra, new ArrayList<Object>());
            List<Object> l = (List<Object>) obj.get(extra);
            JsObject slot = slot(l, i);
            slot.put("fhir_comments", fc);
          } else {
            if (!(obj.get(extra) instanceof JsObject)) obj.put(extra, new JsObject());
            ((JsObject) obj.get(extra)).put("fhir_comments", fc);
          }
        } else if (multiple) {
          if (!(obj.get(name) instanceof List<?>)) obj.put(name, new ArrayList<Object>());
          slot((List<Object>) obj.get(name), i).put("fhir_comments", fc);
        } else {
          if (!(obj.get(name) instanceof JsObject)) obj.put(name, new JsObject());
          ((JsObject) obj.get(name)).put("fhir_comments", fc);
        }
      }
    }
  }

  /** {@code if (!arr[i]) arr[i] = {}; return arr[i]} (holes become null like JSON shows them). */
  private static JsObject slot(List<Object> l, int i) {
    while (l.size() <= i) l.add(null);
    if (!(l.get(i) instanceof JsObject)) l.set(i, new JsObject());
    return (JsObject) l.get(i);
  }

  private static int indexOfIdentity(List<XNode> l, XNode n) {
    for (int i = 0; i < l.size(); i++) if (l.get(i) == n) return i;
    return -1;
  }

  private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");
  private static final Pattern DECIMAL = Pattern.compile("-?([0]|([1-9][0-9]*))(\\.[0-9]+)?");

  @SuppressWarnings("unchecked")
  private static void pushValue(JsObject obj, Map<String, Object> property, String name, String type,
      boolean multiple, XNode value, int index) {
    String v = value.attributes() == null ? null : value.attributes().get("value");
    switch (type == null ? "" : type) {
      case "string", "base64Binary", "code", "id", "markdown", "uri", "url", "canonical", "oid", "date", "dateTime",
          "time", "instant" -> {
        addExtra(obj, name, value, index);
        if (v != null && !v.isEmpty()) set(obj, name, v);
      }
      case "decimal" -> {
        addExtra(obj, name, value, index);
        if (v != null && !v.isEmpty()) {
          if (!DECIMAL.matcher(v).matches()) throw new FhirToolError("Value should be a decimal number but got: " + v);
          set(obj, name, v);
        }
      }
      case "boolean" -> {
        addExtra(obj, name, value, index);
        if (v != null && !v.isEmpty()) {
          if (v.equals("true")) set(obj, name, true);
          else if (v.equals("false")) set(obj, name, false);
          else throw new FhirToolError("Value should be a boolean but got: " + v);
        }
      }
      case "integer", "unsignedInt", "positiveInt" -> {
        addExtra(obj, name, value, index);
        if (v != null && !v.isEmpty()) {
          if (!INTEGER.matcher(v).matches()) throw new FhirToolError("Value should be a number but got: " + v);
          set(obj, name, Double.parseDouble(v));
        }
      }
      case "xhtml" -> {
        // the serialized div; SUSHI's configuration never reads it
        if (value.elements() != null && !value.elements().isEmpty()) set(obj, name, "<div/>");
      }
      case "Element", "BackboneElement" -> {
        JsObject newValue = new JsObject();
        Object props = property.get("_properties");
        if (props instanceof List<?> pl) {
          for (Object p : pl) propertyToJS(value, newValue, (Map<String, Object>) p);
        }
        set(obj, name, newValue);
      }
      case "Resource" -> {
        if (value.elements() != null && !value.elements().isEmpty()) {
          XNode first = value.elements().stream().filter(e -> e.type().equals("element")).findFirst().orElse(null);
          if (first != null) set(obj, name, resourceToJS(first));
        }
      }
      default -> {
        Map<String, Object> nextType = (Map<String, Object>) TYPES.get(type);
        if (nextType != null) {
          JsObject newValue = new JsObject();
          for (Object p : (List<Object>) nextType.get("_properties")) {
            propertyToJS(value, newValue, (Map<String, Object>) p);
          }
          set(obj, name, newValue);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void set(JsObject obj, String name, Object v) {
    if (obj.get(name) instanceof List<?> l) ((List<Object>) l).add(v);
    else obj.put(name, v);
  }

  private static final Map<String, Object> EXTENSION_PROPERTY =
      new JsObject().with("_name", "extension").with("_type", "Extension").with("_multiple", true);

  @SuppressWarnings("unchecked")
  private static void addExtra(JsObject obj, String name, XNode element, int index) {
    String id = element.attributes() == null ? null : element.attributes().get("id");
    boolean hasId = id != null && !id.isEmpty();
    boolean hasExtensions = element.elements() != null
        && element.elements().stream().anyMatch(n -> n.type().equals("element") && n.name().equals("extension"));
    String extra = "_" + name;
    if (hasId || hasExtensions) {
      if (obj.get(extra) == null) {
        obj.put(extra, obj.get(name) instanceof List<?> ? new ArrayList<Object>() : new JsObject());
      }
    }
    Object dest = obj.get(extra);
    if (hasId || hasExtensions) {
      if (dest instanceof List<?> l) {
        List<Object> dl = (List<Object>) l;
        while (dl.size() < index) dl.add(null);
        if (dl.size() == index) dl.add(new JsObject());
        else dl.set(index, new JsObject());
      }
    }
    JsObject target = dest instanceof List<?> l ? (hasId || hasExtensions ? (JsObject) l.get(index) : null)
        : (JsObject) dest;
    if (hasId) target.put("id", id);
    if (hasExtensions) propertyToJS(element, target, EXTENSION_PROPERTY);
  }

  // ---------------------------------------------------------------- xml-js

  private static XNode parse(String xml) {
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setNamespaceAware(false);
      f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setExpandEntityReferences(false);
      DocumentBuilder b = f.newDocumentBuilder();
      b.setErrorHandler(null);
      Document d = b.parse(new InputSource(new StringReader(xml)));
      return toXNode(d);
    } catch (FhirToolError e) {
      throw e;
    } catch (Exception e) {
      throw new FhirToolError(String.valueOf(e.getMessage()));
    }
  }

  private static XNode toXNode(Node n) {
    List<XNode> children = new ArrayList<>();
    for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
      switch (c.getNodeType()) {
        case Node.ELEMENT_NODE -> children.add(toXNode(c));
        case Node.COMMENT_NODE -> children.add(new XNode("comment", null, null, null, c.getNodeValue()));
        case Node.TEXT_NODE -> {
          // xml-js drops whitespace-only text between elements (captureSpacesBetweenElements: false)
          if (!c.getNodeValue().isBlank()) children.add(new XNode("text", null, null, null, c.getNodeValue()));
        }
        case Node.CDATA_SECTION_NODE -> children.add(new XNode("cdata", null, null, null, c.getNodeValue()));
        case Node.PROCESSING_INSTRUCTION_NODE -> children.add(new XNode("instruction", c.getNodeName(), null, null,
            c.getNodeValue()));
        default -> {}
      }
    }
    if (n.getNodeType() == Node.DOCUMENT_NODE) return new XNode("document", null, null, children, null);
    JsObjectAttrs attrs = new JsObjectAttrs();
    NamedNodeMap nm = n.getAttributes();
    for (int i = 0; nm != null && i < nm.getLength(); i++) {
      Attr a = (Attr) nm.item(i);
      attrs.put(a.getName(), a.getValue());
    }
    return new XNode("element", ((Element) n).getTagName(), attrs, children.isEmpty() ? null : children, null);
  }

  /** attribute name -> value, in DOM order (only lookups by name matter). */
  private static final class JsObjectAttrs extends java.util.LinkedHashMap<String, String> {
    private static final long serialVersionUID = 1L;
  }
}
