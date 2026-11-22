package org.hl7.fhir.sushi.fhirdefs.xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.common.js.Js;

// port of xml-js 1.6.11 lib/xml2js.js and lib/js2xml.js (non-compact mode, default options) + fhir-tool 5.0.2
// xmlHelper.js escapeInvalidCharacters -- only what fhir-tool's xmlToObj uses
final class XmlJs {
  private XmlJs() {}

  /** An xml-js non-compact node: {type, name, attributes, elements} / {type:'text', text} / ... */
  static final class XNode {
    final String type;
    String name;
    /** null when the element had no attributes (xml-js omits the key) */
    Map<String, String> attributes;
    /** null until the first child (xml-js creates the array lazily) */
    List<XNode> elements;
    /** text / comment / cdata / doctype / instruction value */
    String value;
    XNode parent;

    XNode(String type) {
      this.type = type;
    }
  }

  /** xml2js(xml) with default options; throws {@link Sax124.SaxError} like xml-js (sax close() rethrows) */
  static XNode xml2js(String xml) {
    XNode result = new XNode("root");
    XNode[] current = {result};
    Sax124 parser = new Sax124(new Sax124.Handler() {
      @Override
      public void opentag(String name, Map<String, String> attributes) {
        XNode cur = current[0];
        if (cur.elements == null) cur.elements = new ArrayList<>();
        XNode element = new XNode("element");
        element.name = name;
        if (attributes != null && !attributes.isEmpty()) element.attributes = attributes;
        cur.elements.add(element);
        element.parent = cur;
        current[0] = element;
      }

      @Override
      public void closetag(String name) {
        XNode parentElement = current[0].parent;
        current[0].parent = null;
        current[0] = parentElement;
      }

      @Override
      public void text(String text) {
        if (Js.trim(text).isEmpty()) return; // !captureSpacesBetweenElements
        addField(current[0], "text", null, text);
      }

      @Override
      public void comment(String comment) {
        addField(current[0], "comment", null, comment);
      }

      @Override
      public void cdata(String cdata) {
        addField(current[0], "cdata", null, cdata);
      }

      @Override
      public void doctype(String doctype) {
        addField(current[0], "doctype", null, doctype.replaceFirst("^ ", ""));
      }

      @Override
      public void processinginstruction(String name, String body) {
        if (name.toLowerCase(java.util.Locale.ROOT).equals("xml")) {
          return; // the declaration: kept on the current element as `declaration`, never read by fhir-tool
        }
        addField(current[0], "instruction", name, body);
      }
    });
    parser.write(xml).close();
    return result;
  }

  private static void addField(XNode cur, String type, String name, String value) {
    if (cur == null) {
      // a node after the root element was closed with nothing open: xml-js' currentElement is undefined
      throw new IllegalStateException("Cannot read properties of undefined (reading 'elements')");
    }
    if (cur.elements == null) cur.elements = new ArrayList<>();
    XNode n = new XNode(type);
    n.name = name;
    n.value = value;
    cur.elements.add(n);
  }

  // ------------------------------------------------------------------------------------------------ js2xml

  /** js2xml({elements: [element]}) with default options */
  static String js2xml(XNode element) {
    StringBuilder sb = new StringBuilder();
    writeNode(sb, element);
    return sb.toString();
  }

  private static void writeNode(StringBuilder sb, XNode n) {
    switch (n.type) {
      case "element" -> writeElement(sb, n);
      case "comment" -> sb.append("<!--").append(n.value).append("-->");
      case "doctype" -> sb.append("<!DOCTYPE ").append(n.value).append('>');
      case "cdata" -> sb.append("<![CDATA[").append(n.value.replaceFirst("\\]\\]>", "]]]]><![CDATA[>")).append("]]>");
      case "text" -> sb.append(writeText(n.value));
      case "instruction" -> sb.append("<?").append(n.name)
          .append(n.value != null && !n.value.isEmpty() ? " " + n.value : "").append("?>");
      default -> { }
    }
  }

  private static void writeElement(StringBuilder sb, XNode e) {
    sb.append('<').append(e.name);
    if (e.attributes != null) {
      for (Map.Entry<String, String> a : e.attributes.entrySet()) {
        if (a.getValue() == null) continue;
        sb.append(' ').append(a.getKey()).append("=\"").append(a.getValue().replace("\"", "&quot;")).append('"');
      }
    }
    boolean withClosingTag = (e.elements != null && !e.elements.isEmpty())
        || (e.attributes != null && "preserve".equals(e.attributes.get("xml:space")));
    if (withClosingTag) {
      sb.append('>');
      if (e.elements != null) {
        for (XNode c : e.elements) writeNode(sb, c);
      }
      sb.append("</").append(e.name).append('>');
    } else {
      sb.append("/>");
    }
  }

  private static String writeText(String text) {
    text = text.replace("&amp;", "&"); // desanitize to avoid double sanitization
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  // fhir-tool XmlHelper.escapeInvalidCharacters (mutates the tree, as in JS)
  private static final Pattern INVALID_AMP = Pattern.compile("&(?!(?:apos|quot|[gl]t|amp);|#)");

  static XNode escapeInvalidCharacters(XNode element) {
    if (element == null) return null;
    if (element.attributes != null) {
      Map<String, String> m = new LinkedHashMap<>();
      element.attributes.forEach((k, v) -> m.put(k, INVALID_AMP.matcher(v).replaceAll("&amp;")));
      element.attributes = m;
    }
    if (element.type.equals("text") && element.value != null && !element.value.isEmpty()) {
      element.value = INVALID_AMP.matcher(element.value).replaceAll("&amp;");
    }
    if (element.elements != null) {
      for (XNode c : element.elements) escapeInvalidCharacters(c);
    }
    return element;
  }
}
