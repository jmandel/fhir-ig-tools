package org.hl7.fhir.sushi.fhirdefs.xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// port of sax 1.2.4 lib/sax.js (the copy fhir-tool 5.0.2's xml-js uses), strict mode, no xmlns, positions tracked
/**
 * The sax state machine with events, exactly as xml-js drives it: {@code sax.parser(true, {})}, then
 * {@code parser.opt = {strictEntities: true}} (after construction, so the full HTML entity table stays in use and
 * only {@code opt.trim/normalize} — both off — are read later), handlers for opentag/text/comment/closetag/cdata/
 * doctype/processinginstruction, an onerror that only records, then {@code write(xml).close()}. Errors don't stop
 * parsing; {@code close()} throws the last one. Strings are processed by UTF-16 code unit, like JS.
 * (A separate port from xhtml.SaxParser, which is sax 1.6.1 without events for SUSHI's xhtml check.)
 */
final class Sax124 {
  interface Handler {
    void opentag(String name, Map<String, String> attributes);

    void closetag(String name);

    void text(String text);

    void comment(String comment);

    void cdata(String cdata);

    void doctype(String doctype);

    void processinginstruction(String name, String body);
  }

  /** a JS Error thrown by sax (message as in JS) */
  static final class SaxError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SaxError(String message) {
      super(message);
    }
  }

  static final int MAX_BUFFER_LENGTH = 64 * 1024;

  private enum S {
    BEGIN, BEGIN_WHITESPACE, TEXT, TEXT_ENTITY, OPEN_WAKA, SGML_DECL, SGML_DECL_QUOTED, DOCTYPE, DOCTYPE_QUOTED,
    DOCTYPE_DTD, DOCTYPE_DTD_QUOTED, COMMENT_STARTING, COMMENT, COMMENT_ENDING, COMMENT_ENDED, CDATA, CDATA_ENDING,
    CDATA_ENDING_2, PROC_INST, PROC_INST_BODY, PROC_INST_ENDING, OPEN_TAG, OPEN_TAG_SLASH, ATTRIB, ATTRIB_NAME,
    ATTRIB_NAME_SAW_WHITE, ATTRIB_VALUE, ATTRIB_VALUE_QUOTED, ATTRIB_VALUE_CLOSED, ATTRIB_VALUE_UNQUOTED,
    ATTRIB_VALUE_ENTITY_Q, ATTRIB_VALUE_ENTITY_U, CLOSE_TAG, CLOSE_TAG_SAW_WHITE, SCRIPT, SCRIPT_ENDING
  }

  private static final String NAME_START_CHARS = ":_A-Za-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D"
      + "\\u037F-\\u1FFF\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD";
  private static final Pattern NAME_START = Pattern.compile("[" + NAME_START_CHARS + "]");
  private static final Pattern NAME_BODY =
      Pattern.compile("[" + NAME_START_CHARS + "\\u00B7\\u0300-\\u036F\\u203F-\\u2040.\\d-]");
  private static final Pattern ENTITY_START = Pattern.compile("[#" + NAME_START_CHARS + "]");
  private static final Pattern ENTITY_BODY =
      Pattern.compile("[#" + NAME_START_CHARS + "\\u00B7\\u0300-\\u036F\\u203F-\\u2040.\\d-]");

  private static final class Tag {
    final String name;
    final Map<String, String> attributes = new LinkedHashMap<>();

    Tag(String name) {
      this.name = name;
    }
  }

  private final Handler h;
  private String q = "", c = "";
  private String comment = "", sgmlDecl = "", textNode = "", tagName = "", procInstName = "", procInstBody = "",
      entity = "", attribName = "", attribValue = "", cdata = "", script = "";
  private String doctype = "";
  private boolean doctypeSeen;
  private int bufferCheckPosition = MAX_BUFFER_LENGTH;
  private final List<Tag> tags = new ArrayList<>();
  private Tag tag;
  private boolean closed, closedRoot, sawRoot;
  private String error;
  private S state = S.BEGIN;
  private int position, line, column, startTagPosition;

  Sax124(Handler h) {
    this.h = h;
  }

  private static boolean isWhitespace(String c) {
    return c.equals(" ") || c.equals("\n") || c.equals("\r") || c.equals("\t");
  }

  private static boolean isQuote(String c) {
    return c.equals("\"") || c.equals("'");
  }

  private static boolean isAttribEnd(String c) {
    return c.equals(">") || isWhitespace(c);
  }

  private static boolean isMatch(Pattern p, String c) {
    return p.matcher(c).find();
  }

  // ------------------------------------------------------------------------------------------------ emit

  private void closeText() {
    // textopts: opt.trim / opt.normalize are off
    if (!textNode.isEmpty()) h.text(textNode);
    textNode = "";
  }

  private void error(String er) {
    closeText();
    er += "\nLine: " + line + "\nColumn: " + column + "\nChar: " + c;
    error = er; // onerror (xml-js) only notes it
  }

  private void strictFail(String message) {
    error(message);
  }

  private void end() {
    if (sawRoot && !closedRoot) strictFail("Unclosed root tag");
    if (state != S.BEGIN && state != S.BEGIN_WHITESPACE && state != S.TEXT) {
      error("Unexpected end");
    }
    closeText();
    c = "";
    closed = true;
    // (sax then re-initialises the parser; nothing observable for xml-js)
  }

  private void newTag() {
    tag = new Tag(tagName);
    if (!textNode.isEmpty()) closeText(); // emitNode onopentagstart
  }

  private void attrib() {
    if (tag.attributes.containsKey(attribName) || "__proto__".equals(attribName)) {
      // duplicate: ignored. ("__proto__" on a plain object sets its prototype: the key never appears)
      attribName = attribValue = "";
      return;
    }
    tag.attributes.put(attribName, attribValue);
    attribName = attribValue = "";
  }

  private void openTag(boolean selfClosing) {
    sawRoot = true;
    tags.add(tag);
    if (!textNode.isEmpty()) closeText();
    h.opentag(tag.name, tag.attributes);
    if (!selfClosing) {
      // noscript is true in strict mode
      state = S.TEXT;
      tag = null;
      tagName = "";
    }
    attribName = attribValue = "";
  }

  private void closeTag() {
    if (tagName.isEmpty()) {
      strictFail("Weird empty close tag.");
      textNode += "</>";
      state = S.TEXT;
      return;
    }
    if (!script.isEmpty()) {
      if (!tagName.equals("script")) {
        script += "</" + tagName + ">";
        tagName = "";
        state = S.SCRIPT;
        return;
      }
      script = ""; // onscript: no handler
    }
    int t = tags.size();
    String closeTo = tagName;
    while (t-- > 0) {
      Tag close = tags.get(t);
      if (!close.name.equals(closeTo)) {
        strictFail("Unexpected close tag");
      } else {
        break;
      }
    }
    if (t < 0) {
      strictFail("Unmatched closing tag: " + tagName);
      textNode += "</" + tagName + ">";
      state = S.TEXT;
      return;
    }
    int s = tags.size();
    while (s-- > t) {
      tag = tags.remove(tags.size() - 1);
      tagName = tag.name;
      if (!textNode.isEmpty()) closeText();
      h.closetag(tagName);
    }
    if (t == 0) closedRoot = true;
    tagName = attribValue = attribName = "";
    state = S.TEXT;
  }

  private static final String[] PROTO_KEYS = {"constructor", "__defineGetter__", "__defineSetter__",
    "hasOwnProperty", "__lookupGetter__", "__lookupSetter__", "isPrototypeOf", "propertyIsEnumerable", "toString",
    "valueOf", "__proto__", "toLocaleString"};

  private String parseEntity() {
    String ent = entity;
    String entityLC = ent.toLowerCase(java.util.Locale.ROOT);
    String v = SaxEntities124.ENTITIES.get(ent);
    if (v != null) return v;
    v = SaxEntities124.ENTITIES.get(entityLC);
    if (v != null) return v;
    for (String k : PROTO_KEYS) {
      if (k.equals(ent) || k.equals(entityLC)) {
        // Object.prototype members are found on the ENTITIES prototype chain (JS quirk)
        throw new UnsupportedOperationException("sax entity resolves to Object.prototype." + k);
      }
    }
    ent = entityLC;
    double num = Double.NaN;
    String numStr = "";
    if (ent.length() > 0 && ent.charAt(0) == '#') {
      if (ent.length() > 1 && ent.charAt(1) == 'x') {
        ent = ent.substring(2);
        num = parseIntJs(ent, 16);
        numStr = Double.isNaN(num) ? "NaN" : toRadix(num, 16);
      } else {
        ent = ent.substring(1);
        num = parseIntJs(ent, 10);
        numStr = Double.isNaN(num) ? "NaN" : toRadix(num, 10);
      }
    }
    ent = ent.replaceFirst("^0+", "");
    if (Double.isNaN(num) || !numStr.toLowerCase(java.util.Locale.ROOT).equals(ent)) {
      strictFail("Invalid character entity");
      return "&" + entity + ";";
    }
    if (num < 0 || num > 0x10FFFF || num != Math.floor(num)) {
      throw new SaxError("Invalid code point " + toRadix(num, 10));
    }
    return new String(Character.toChars((int) num));
  }

  /** JS parseInt(s, radix) for the strings sax passes (no sign/whitespace handling needed beyond JS's) */
  private static double parseIntJs(String s, int radix) {
    int i = 0;
    String t = s.strip();
    boolean neg = false;
    if (i < t.length() && (t.charAt(i) == '+' || t.charAt(i) == '-')) {
      neg = t.charAt(i) == '-';
      i++;
    }
    if (radix == 16 && t.startsWith("0x", i) || radix == 16 && t.startsWith("0X", i)) i += 2;
    double v = 0;
    int start = i;
    while (i < t.length()) {
      int d = Character.digit(t.charAt(i), radix);
      if (d < 0 || t.charAt(i) > 0x7f) break;
      v = v * radix + d;
      i++;
    }
    if (i == start) return Double.NaN;
    if (i - start > 15) {
      // exact for long digit strings
      v = new java.math.BigInteger(t.substring(start, i), radix).doubleValue();
    }
    return neg ? -v : v;
  }

  private static String toRadix(double num, int radix) {
    if (num == Math.rint(num) && Math.abs(num) < 9.007199254740992E15) {
      return Long.toString((long) num, radix);
    }
    if (radix == 10) return org.hl7.fhir.sushi.js.JsNumber.toString(num);
    return new java.math.BigDecimal(num).toBigInteger().toString(radix);
  }

  private void beginWhiteSpace(String ch) {
    if (ch.equals("<")) {
      state = S.OPEN_WAKA;
      startTagPosition = position;
    } else if (!isWhitespace(ch)) {
      strictFail("Non-whitespace before first tag.");
      textNode = ch;
      state = S.TEXT;
    }
  }

  private static String charAt(String chunk, int i) {
    return i < chunk.length() ? String.valueOf(chunk.charAt(i)) : "";
  }

  private void track(String ch) {
    position++;
    if (ch.equals("\n")) {
      line++;
      column = 0;
    } else {
      column++;
    }
  }

  /** sax close() = write(null): throws the last error, if any; else ends the document. */
  void close() {
    if (error != null) throw new SaxError(error);
    if (closed) {
      error("Cannot write after close. Assign an onready handler.");
      return;
    }
    end();
  }

  Sax124 write(String chunk) {
    if (error != null) throw new SaxError(error);
    if (closed) {
      error("Cannot write after close. Assign an onready handler.");
      return this;
    }
    int i = 0;
    String ch;
    while (true) {
      ch = charAt(chunk, i++);
      c = ch;
      if (ch.isEmpty()) break;
      track(ch);
      switch (state) {
        case BEGIN -> {
          state = S.BEGIN_WHITESPACE;
          if (ch.equals("﻿")) continue;
          beginWhiteSpace(ch);
        }
        case BEGIN_WHITESPACE -> beginWhiteSpace(ch);
        case TEXT -> {
          if (sawRoot && !closedRoot) {
            int starti = i - 1;
            while (!ch.isEmpty() && !ch.equals("<") && !ch.equals("&")) {
              ch = charAt(chunk, i++);
              if (!ch.isEmpty()) track(ch);
            }
            textNode += chunk.substring(starti, i - 1);
          }
          if (ch.equals("<")) { // strict: `&& !(sawRoot && closedRoot && !strict)` is always true
            state = S.OPEN_WAKA;
            startTagPosition = position;
          } else {
            if (!isWhitespace(ch) && (!sawRoot || closedRoot)) strictFail("Text data outside of root node.");
            if (ch.equals("&")) state = S.TEXT_ENTITY;
            else textNode += ch;
          }
        }
        case SCRIPT -> {
          if (ch.equals("<")) state = S.SCRIPT_ENDING;
          else script += ch;
        }
        case SCRIPT_ENDING -> {
          if (ch.equals("/")) {
            state = S.CLOSE_TAG;
          } else {
            script += "<" + ch;
            state = S.SCRIPT;
          }
        }
        case OPEN_WAKA -> {
          if (ch.equals("!")) {
            state = S.SGML_DECL;
            sgmlDecl = "";
          } else if (isWhitespace(ch)) {
            // wait for it...
          } else if (isMatch(NAME_START, ch)) {
            state = S.OPEN_TAG;
            tagName = ch;
          } else if (ch.equals("/")) {
            state = S.CLOSE_TAG;
            tagName = "";
          } else if (ch.equals("?")) {
            state = S.PROC_INST;
            procInstName = procInstBody = "";
          } else {
            strictFail("Unencoded <");
            String cv = ch;
            if (startTagPosition + 1 < position) {
              int pad = position - startTagPosition;
              cv = " ".repeat(pad - 1) + cv;
            }
            textNode += "<" + cv;
            state = S.TEXT;
          }
        }
        case SGML_DECL -> {
          if ((sgmlDecl + ch).toUpperCase(java.util.Locale.ROOT).equals("[CDATA[")) {
            if (!textNode.isEmpty()) closeText(); // emitNode onopencdata
            state = S.CDATA;
            sgmlDecl = "";
            cdata = "";
          } else if ((sgmlDecl + ch).equals("--")) {
            state = S.COMMENT;
            comment = "";
            sgmlDecl = "";
          } else if ((sgmlDecl + ch).toUpperCase(java.util.Locale.ROOT).equals("DOCTYPE")) {
            state = S.DOCTYPE;
            if (doctypeSeen || !doctype.isEmpty() || sawRoot) {
              strictFail("Inappropriately located doctype declaration");
            }
            doctype = "";
            doctypeSeen = false;
            sgmlDecl = "";
          } else if (ch.equals(">")) {
            if (!textNode.isEmpty()) closeText(); // onsgmldeclaration: no handler
            sgmlDecl = "";
            state = S.TEXT;
          } else if (isQuote(ch)) {
            state = S.SGML_DECL_QUOTED;
            sgmlDecl += ch;
          } else {
            sgmlDecl += ch;
          }
        }
        case SGML_DECL_QUOTED -> {
          if (ch.equals(q)) {
            state = S.SGML_DECL;
            q = "";
          }
          sgmlDecl += ch;
        }
        case DOCTYPE -> {
          if (ch.equals(">")) {
            state = S.TEXT;
            if (!textNode.isEmpty()) closeText();
            h.doctype(doctype);
            doctype = "";
            doctypeSeen = true; // just remember that we saw it.
          } else {
            doctype += ch;
            if (ch.equals("[")) {
              state = S.DOCTYPE_DTD;
            } else if (isQuote(ch)) {
              state = S.DOCTYPE_QUOTED;
              q = ch;
            }
          }
        }
        case DOCTYPE_QUOTED -> {
          doctype += ch;
          if (ch.equals(q)) {
            q = "";
            state = S.DOCTYPE;
          }
        }
        case DOCTYPE_DTD -> {
          doctype += ch;
          if (ch.equals("]")) {
            state = S.DOCTYPE;
          } else if (isQuote(ch)) {
            state = S.DOCTYPE_DTD_QUOTED;
            q = ch;
          }
        }
        case DOCTYPE_DTD_QUOTED -> {
          doctype += ch;
          if (ch.equals(q)) {
            state = S.DOCTYPE_DTD;
            q = "";
          }
        }
        case COMMENT -> {
          if (ch.equals("-")) state = S.COMMENT_ENDING;
          else comment += ch;
        }
        case COMMENT_ENDING -> {
          if (ch.equals("-")) {
            state = S.COMMENT_ENDED;
            if (!comment.isEmpty()) {
              if (!textNode.isEmpty()) closeText();
              h.comment(comment);
            }
            comment = "";
          } else {
            comment += "-" + ch;
            state = S.COMMENT;
          }
        }
        case COMMENT_ENDED -> {
          if (!ch.equals(">")) {
            strictFail("Malformed comment");
            comment += "--" + ch;
            state = S.COMMENT;
          } else {
            state = S.TEXT;
          }
        }
        case CDATA -> {
          if (ch.equals("]")) state = S.CDATA_ENDING;
          else cdata += ch;
        }
        case CDATA_ENDING -> {
          if (ch.equals("]")) {
            state = S.CDATA_ENDING_2;
          } else {
            cdata += "]" + ch;
            state = S.CDATA;
          }
        }
        case CDATA_ENDING_2 -> {
          if (ch.equals(">")) {
            if (!cdata.isEmpty()) {
              if (!textNode.isEmpty()) closeText();
              h.cdata(cdata);
            }
            if (!textNode.isEmpty()) closeText(); // onclosecdata
            cdata = "";
            state = S.TEXT;
          } else if (ch.equals("]")) {
            cdata += "]";
          } else {
            cdata += "]]" + ch;
            state = S.CDATA;
          }
        }
        case PROC_INST -> {
          if (ch.equals("?")) state = S.PROC_INST_ENDING;
          else if (isWhitespace(ch)) state = S.PROC_INST_BODY;
          else procInstName += ch;
        }
        case PROC_INST_BODY -> {
          if (procInstBody.isEmpty() && isWhitespace(ch)) {
            continue;
          } else if (ch.equals("?")) {
            state = S.PROC_INST_ENDING;
          } else {
            procInstBody += ch;
          }
        }
        case PROC_INST_ENDING -> {
          if (ch.equals(">")) {
            if (!textNode.isEmpty()) closeText();
            h.processinginstruction(procInstName, procInstBody);
            procInstName = procInstBody = "";
            state = S.TEXT;
          } else {
            procInstBody += "?" + ch;
            state = S.PROC_INST_BODY;
          }
        }
        case OPEN_TAG -> {
          if (isMatch(NAME_BODY, ch)) {
            tagName += ch;
          } else {
            newTag();
            if (ch.equals(">")) {
              openTag(false);
            } else if (ch.equals("/")) {
              state = S.OPEN_TAG_SLASH;
            } else {
              if (!isWhitespace(ch)) strictFail("Invalid character in tag name");
              state = S.ATTRIB;
            }
          }
        }
        case OPEN_TAG_SLASH -> {
          if (ch.equals(">")) {
            openTag(true);
            closeTag();
          } else {
            strictFail("Forward-slash in opening tag not followed by >");
            state = S.ATTRIB;
          }
        }
        case ATTRIB -> {
          if (isWhitespace(ch)) {
            continue;
          } else if (ch.equals(">")) {
            openTag(false);
          } else if (ch.equals("/")) {
            state = S.OPEN_TAG_SLASH;
          } else if (isMatch(NAME_START, ch)) {
            attribName = ch;
            attribValue = "";
            state = S.ATTRIB_NAME;
          } else {
            strictFail("Invalid attribute name");
          }
        }
        case ATTRIB_NAME -> {
          if (ch.equals("=")) {
            state = S.ATTRIB_VALUE;
          } else if (ch.equals(">")) {
            strictFail("Attribute without value");
            attribValue = attribName;
            attrib();
            openTag(false);
          } else if (isWhitespace(ch)) {
            state = S.ATTRIB_NAME_SAW_WHITE;
          } else if (isMatch(NAME_BODY, ch)) {
            attribName += ch;
          } else {
            strictFail("Invalid attribute name");
          }
        }
        case ATTRIB_NAME_SAW_WHITE -> {
          if (ch.equals("=")) {
            state = S.ATTRIB_VALUE;
          } else if (isWhitespace(ch)) {
            continue;
          } else {
            strictFail("Attribute without value");
            if (!"__proto__".equals(attribName)) tag.attributes.put(attribName, "");
            attribValue = "";
            attribName = "";
            if (ch.equals(">")) {
              openTag(false);
            } else if (isMatch(NAME_START, ch)) {
              attribName = ch;
              state = S.ATTRIB_NAME;
            } else {
              strictFail("Invalid attribute name");
              state = S.ATTRIB;
            }
          }
        }
        case ATTRIB_VALUE -> {
          if (isWhitespace(ch)) {
            continue;
          } else if (isQuote(ch)) {
            q = ch;
            state = S.ATTRIB_VALUE_QUOTED;
          } else {
            strictFail("Unquoted attribute value");
            state = S.ATTRIB_VALUE_UNQUOTED;
            attribValue = ch;
          }
        }
        case ATTRIB_VALUE_QUOTED -> {
          if (!ch.equals(q)) {
            if (ch.equals("&")) state = S.ATTRIB_VALUE_ENTITY_Q;
            else attribValue += ch;
            continue;
          }
          attrib();
          q = "";
          state = S.ATTRIB_VALUE_CLOSED;
        }
        case ATTRIB_VALUE_CLOSED -> {
          if (isWhitespace(ch)) {
            state = S.ATTRIB;
          } else if (ch.equals(">")) {
            openTag(false);
          } else if (ch.equals("/")) {
            state = S.OPEN_TAG_SLASH;
          } else if (isMatch(NAME_START, ch)) {
            strictFail("No whitespace between attributes");
            attribName = ch;
            attribValue = "";
            state = S.ATTRIB_NAME;
          } else {
            strictFail("Invalid attribute name");
          }
        }
        case ATTRIB_VALUE_UNQUOTED -> {
          if (!isAttribEnd(ch)) {
            if (ch.equals("&")) state = S.ATTRIB_VALUE_ENTITY_U;
            else attribValue += ch;
            continue;
          }
          attrib();
          if (ch.equals(">")) openTag(false);
          else state = S.ATTRIB;
        }
        case CLOSE_TAG -> {
          if (tagName.isEmpty()) {
            if (isWhitespace(ch)) {
              continue;
            } else if (!isMatch(NAME_START, ch)) {
              if (!script.isEmpty()) {
                script += "</" + ch;
                state = S.SCRIPT;
              } else {
                strictFail("Invalid tagname in closing tag.");
              }
            } else {
              tagName = ch;
            }
          } else if (ch.equals(">")) {
            closeTag();
          } else if (isMatch(NAME_BODY, ch)) {
            tagName += ch;
          } else if (!script.isEmpty()) {
            script += "</" + tagName;
            tagName = "";
            state = S.SCRIPT;
          } else {
            if (!isWhitespace(ch)) strictFail("Invalid tagname in closing tag");
            state = S.CLOSE_TAG_SAW_WHITE;
          }
        }
        case CLOSE_TAG_SAW_WHITE -> {
          if (isWhitespace(ch)) continue;
          if (ch.equals(">")) closeTag();
          else strictFail("Invalid characters in closing tag");
        }
        case TEXT_ENTITY, ATTRIB_VALUE_ENTITY_Q, ATTRIB_VALUE_ENTITY_U -> {
          S returnState = switch (state) {
            case TEXT_ENTITY -> S.TEXT;
            case ATTRIB_VALUE_ENTITY_Q -> S.ATTRIB_VALUE_QUOTED;
            default -> S.ATTRIB_VALUE_UNQUOTED;
          };
          boolean toText = state == S.TEXT_ENTITY;
          if (ch.equals(";")) {
            String v = parseEntity();
            if (toText) textNode += v;
            else attribValue += v;
            entity = "";
            state = returnState;
          } else if (isMatch(entity.isEmpty() ? ENTITY_START : ENTITY_BODY, ch)) {
            entity += ch;
          } else {
            strictFail("Invalid character in entity name");
            String v = "&" + entity + ch;
            if (toText) textNode += v;
            else attribValue += v;
            entity = "";
            state = returnState;
          }
        }
        default -> throw new IllegalStateException("Unknown state: " + state);
      }
    }
    if (position >= bufferCheckPosition) checkBufferLength();
    return this;
  }

  private void checkBufferLength() {
    int maxAllowed = Math.max(MAX_BUFFER_LENGTH, 10);
    int maxActual = 0;
    String[][] buffers = {
      {"comment", comment}, {"sgmlDecl", sgmlDecl}, {"textNode", textNode}, {"tagName", tagName},
      {"doctype", doctypeSeen ? "true" : doctype}, {"procInstName", procInstName}, {"procInstBody", procInstBody},
      {"entity", entity}, {"attribName", attribName}, {"attribValue", attribValue}, {"cdata", cdata},
      {"script", script}
    };
    for (String[] b : buffers) {
      int len = b[1].length();
      if (len > maxAllowed) {
        switch (b[0]) {
          case "textNode" -> closeText();
          case "cdata" -> {
            if (!textNode.isEmpty()) closeText();
            h.cdata(cdata);
            cdata = "";
          }
          case "script" -> script = "";
          default -> error("Max buffer length exceeded: " + b[0]);
        }
      }
      maxActual = Math.max(maxActual, len);
    }
    bufferCheckPosition = MAX_BUFFER_LENGTH - maxActual + position;
  }
}
