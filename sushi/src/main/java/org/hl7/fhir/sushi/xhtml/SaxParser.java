package org.hl7.fhir.sushi.xhtml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// port of sax 1.6.1 lib/sax.js (SAXParser with no event handlers and default options; only what SUSHI reaches:
// one write() call on a fresh parser, then reading .error)
/**
 * The sax state machine, ported faithfully for the observable result of {@code parser(strict).write(chunk)}: the
 * {@link #error()} text (sax keeps the LAST error, each error message carrying the
 * {@code "\nLine: ..\nColumn: ..\nChar: .."} suffix) and the cases where sax throws. No events are emitted (SUSHI
 * attaches no handlers), so text/attribute/cdata buffers are only kept as far as they influence state or the
 * end-of-write buffer-length check. Strings are processed by UTF-16 code unit, like JS.
 */
public final class SaxParser {
  /** sax.MAX_BUFFER_LENGTH */
  static final int MAX_BUFFER_LENGTH = 64 * 1024;

  private enum S {
    BEGIN, BEGIN_WHITESPACE, TEXT, TEXT_ENTITY, OPEN_WAKA, SGML_DECL, SGML_DECL_QUOTED, DOCTYPE, DOCTYPE_QUOTED,
    DOCTYPE_DTD, DOCTYPE_DTD_QUOTED, COMMENT_STARTING, COMMENT, COMMENT_ENDING, COMMENT_ENDED, CDATA, CDATA_ENDING,
    CDATA_ENDING_2, PROC_INST, PROC_INST_BODY, PROC_INST_ENDING, OPEN_TAG, OPEN_TAG_SLASH, ATTRIB, ATTRIB_NAME,
    ATTRIB_NAME_SAW_WHITE, ATTRIB_VALUE, ATTRIB_VALUE_QUOTED, ATTRIB_VALUE_CLOSED, ATTRIB_VALUE_UNQUOTED,
    ATTRIB_VALUE_ENTITY_Q, ATTRIB_VALUE_ENTITY_U, CLOSE_TAG, CLOSE_TAG_SAW_WHITE, SCRIPT, SCRIPT_ENDING
  }

  private static final Pattern CDATA_RE = Pattern.compile("^\\[CDATA\\[\\z", Pattern.CASE_INSENSITIVE);
  private static final Pattern DOCTYPE_RE = Pattern.compile("^DOCTYPE\\z", Pattern.CASE_INSENSITIVE);

  private static final class Tag {
    final String name;
    /** JS plain object `attributes`; "__proto__" assignments are no-ops on a plain object */
    final Map<String, String> attributes = new LinkedHashMap<>();
    Tag(String name) { this.name = name; }
  }

  final boolean strict;
  private final boolean unquotedAttributeValues;
  private String q = "", c = "";
  private String comment = "", sgmlDecl = "", textNode = "", tagName = "", procInstName = "", procInstBody = "",
      entity = "", attribName = "", attribValue = "", cdata = "", script = "";
  /** sax's parser.doctype: '' / a string / `true` (doctypeSeen) */
  private String doctype = "";
  private boolean doctypeTrue;
  private int bufferCheckPosition = MAX_BUFFER_LENGTH;
  private final List<Tag> tags = new ArrayList<>();
  private Tag tag;
  private boolean closed, closedRoot, sawRoot;
  private String error;
  private final boolean noscript;
  private S state = S.BEGIN;
  private int position, line, column, startTagPosition;

  SaxParser(boolean strict) {
    this.strict = strict;
    this.noscript = strict;
    this.unquotedAttributeValues = !strict;
  }

  /** null when no error; else sax's error message incl. its "\nLine: ..\nColumn: ..\nChar: .." suffix. */
  public String error() {
    return error;
  }

  /** Like sax's write(): may throw (e.g. "Cannot write after error...") where sax throws. */
  public SaxParser write(String chunk) {
    if (error != null) throw new SaxException(error);
    if (closed) return error("Cannot write after close. Assign an onready handler.");
    if (chunk == null) throw new UnsupportedOperationException("sax end() is not used by SUSHI");
    int i = 0;
    String ch;
    while (true) {
      ch = charAt(chunk, i++);
      c = ch;
      if (ch.isEmpty()) break;
      track(ch);
      char cc = ch.charAt(0);
      switch (state) {
        case BEGIN -> {
          state = S.BEGIN_WHITESPACE;
          if (cc == '﻿') continue;
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
          if (ch.equals("<") && !(sawRoot && closedRoot && !strict)) {
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
          if (ch.equals("/")) state = S.CLOSE_TAG;
          else {
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
          } else if (isNameStart(cc)) {
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
          if ((sgmlDecl + ch).equals("--")) {
            state = S.COMMENT;
            comment = "";
            sgmlDecl = "";
            continue;
          }
          if (doctypeTruthyString() && !sgmlDecl.isEmpty()) {
            state = S.DOCTYPE_DTD;
            doctype += "<!" + sgmlDecl + ch;
            sgmlDecl = "";
          } else if (CDATA_RE.matcher(sgmlDecl + ch).find()) {
            state = S.CDATA;
            sgmlDecl = "";
            cdata = "";
          } else if (DOCTYPE_RE.matcher(sgmlDecl + ch).find()) {
            state = S.DOCTYPE;
            if (doctypeTrue || !doctype.isEmpty() || sawRoot) {
              strictFail("Inappropriately located doctype declaration");
            }
            doctype = "";
            doctypeTrue = false;
            sgmlDecl = "";
          } else if (ch.equals(">")) {
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
          // sax never sets parser.q on entering this state, so q is whatever it was (usually '')
          if (ch.equals(q)) {
            state = S.SGML_DECL;
            q = "";
          }
          sgmlDecl += ch;
        }
        case DOCTYPE -> {
          if (ch.equals(">")) {
            state = S.TEXT;
            doctypeTrue = true;
            doctype = "";
          } else {
            doctype += ch;
            if (ch.equals("[")) state = S.DOCTYPE_DTD;
            else if (isQuote(ch)) {
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
          if (ch.equals("]")) {
            doctype += ch;
            state = S.DOCTYPE;
          } else if (ch.equals("<")) {
            state = S.OPEN_WAKA;
            startTagPosition = position;
          } else if (isQuote(ch)) {
            doctype += ch;
            state = S.DOCTYPE_DTD_QUOTED;
            q = ch;
          } else {
            doctype += ch;
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
          } else if (doctypeTruthyString()) {
            state = S.DOCTYPE_DTD;
          } else {
            state = S.TEXT;
          }
        }
        case CDATA -> {
          int starti = i - 1;
          while (!ch.isEmpty() && !ch.equals("]")) {
            ch = charAt(chunk, i++);
            if (!ch.isEmpty()) track(ch);
          }
          cdata += chunk.substring(starti, i - 1);
          if (ch.equals("]")) state = S.CDATA_ENDING;
        }
        case CDATA_ENDING -> {
          if (ch.equals("]")) state = S.CDATA_ENDING_2;
          else {
            cdata += "]" + ch;
            state = S.CDATA;
          }
        }
        case CDATA_ENDING_2 -> {
          if (ch.equals(">")) {
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
          if (procInstBody.isEmpty() && isWhitespace(ch)) continue;
          else if (ch.equals("?")) state = S.PROC_INST_ENDING;
          else procInstBody += ch;
        }
        case PROC_INST_ENDING -> {
          if (ch.equals(">")) {
            // validateXmlDeclarationEncoding: parser.encoding is only set by SAXStream, never here
            procInstName = procInstBody = "";
            state = S.TEXT;
          } else {
            procInstBody += "?" + ch;
            state = S.PROC_INST_BODY;
          }
        }
        case OPEN_TAG -> {
          if (isNameBody(cc)) {
            tagName += ch;
          } else {
            newTag();
            if (ch.equals(">")) openTag(false);
            else if (ch.equals("/")) state = S.OPEN_TAG_SLASH;
            else {
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
          if (isWhitespace(ch)) continue;
          else if (ch.equals(">")) openTag(false);
          else if (ch.equals("/")) state = S.OPEN_TAG_SLASH;
          else if (isNameStart(cc)) {
            attribName = ch;
            attribValue = "";
            state = S.ATTRIB_NAME;
          } else strictFail("Invalid attribute name");
        }
        case ATTRIB_NAME -> {
          if (ch.equals("=")) state = S.ATTRIB_VALUE;
          else if (ch.equals(">")) {
            strictFail("Attribute without value");
            attribValue = attribName;
            attrib();
            openTag(false);
          } else if (isWhitespace(ch)) state = S.ATTRIB_NAME_SAW_WHITE;
          else if (isNameBody(cc)) attribName += ch;
          else strictFail("Invalid attribute name");
        }
        case ATTRIB_NAME_SAW_WHITE -> {
          if (ch.equals("=")) state = S.ATTRIB_VALUE;
          else if (isWhitespace(ch)) continue;
          else {
            strictFail("Attribute without value");
            setAttribute(attribName, "");
            attribValue = "";
            attribName = "";
            if (ch.equals(">")) openTag(false);
            else if (isNameStart(cc)) {
              attribName = ch;
              state = S.ATTRIB_NAME;
            } else {
              strictFail("Invalid attribute name");
              state = S.ATTRIB;
            }
          }
        }
        case ATTRIB_VALUE -> {
          if (isWhitespace(ch)) continue;
          else if (isQuote(ch)) {
            q = ch;
            state = S.ATTRIB_VALUE_QUOTED;
          } else {
            if (!unquotedAttributeValues) error("Unquoted attribute value");
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
          if (isWhitespace(ch)) state = S.ATTRIB;
          else if (ch.equals(">")) openTag(false);
          else if (ch.equals("/")) state = S.OPEN_TAG_SLASH;
          else if (isNameStart(cc)) {
            strictFail("No whitespace between attributes");
            attribName = ch;
            attribValue = "";
            state = S.ATTRIB_NAME;
          } else strictFail("Invalid attribute name");
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
            if (isWhitespace(ch)) continue;
            else if (!isNameStart(cc)) {
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
          } else if (isNameBody(cc)) {
            tagName += ch;
          } else if (!script.isEmpty()) {
            script += "</" + tagName + ch;
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
          S returnState;
          boolean toText;
          switch (state) {
            case TEXT_ENTITY -> { returnState = S.TEXT; toText = true; }
            case ATTRIB_VALUE_ENTITY_Q -> { returnState = S.ATTRIB_VALUE_QUOTED; toText = false; }
            default -> { returnState = S.ATTRIB_VALUE_UNQUOTED; toText = false; }
          }
          if (ch.equals(";")) {
            String parsed = parseEntity();
            append(toText, parsed);
            entity = "";
            state = returnState;
          } else if (entity.isEmpty() ? isEntityStart(cc) : (cc == '#' || isNameBody(cc))) {
            entity += ch;
          } else {
            strictFail("Invalid character in entity name");
            append(toText, "&" + entity + ch);
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

  private void append(boolean toText, String s) {
    if (toText) textNode += s;
    else attribValue += s;
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

  private static String charAt(String chunk, int i) {
    return i < chunk.length() ? String.valueOf(chunk.charAt(i)) : "";
  }

  private boolean doctypeTruthyString() {
    return !doctypeTrue && !doctype.isEmpty();
  }

  private void checkBufferLength() {
    int maxAllowed = Math.max(MAX_BUFFER_LENGTH, 10);
    double maxActual = 0;
    String[] names = {"comment", "sgmlDecl", "textNode", "tagName", "doctype", "procInstName", "procInstBody",
        "entity", "attribName", "attribValue", "cdata", "script"};
    for (String n : names) {
      // parser.doctype === true has no length: `undefined > max` is false and Math.max(.., undefined) is NaN
      Integer len = switch (n) {
        case "comment" -> comment.length();
        case "sgmlDecl" -> sgmlDecl.length();
        case "textNode" -> textNode.length();
        case "tagName" -> tagName.length();
        case "doctype" -> doctypeTrue ? null : doctype.length();
        case "procInstName" -> procInstName.length();
        case "procInstBody" -> procInstBody.length();
        case "entity" -> entity.length();
        case "attribName" -> attribName.length();
        case "attribValue" -> attribValue.length();
        case "cdata" -> cdata.length();
        default -> script.length();
      };
      if (len != null && len > maxAllowed) {
        switch (n) {
          case "textNode" -> textNode = "";
          case "cdata" -> cdata = "";
          case "script" -> script = "";
          default -> error("Max buffer length exceeded: " + n);
        }
      }
      maxActual = len == null ? Double.NaN : Math.max(maxActual, len);
    }
    // bufferCheckPosition only matters for later writes, which SUSHI never makes
  }

  private SaxParser error(String er) {
    textNode = ""; // closeText
    er += "\nLine: " + line + "\nColumn: " + column + "\nChar: " + c;
    error = er;
    return this;
  }

  private void strictFail(String message) {
    if (strict) error(message);
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

  private void newTag() {
    if (!strict) tagName = Js.looseCaseUpper(tagName);
    tag = new Tag(tagName);
  }

  /** a JS object's `hasOwnProperty(name)` call on tag.attributes, which throws once an own property shadows it */
  private boolean attributesHasOwn(String name) {
    if (tag.attributes.containsKey("hasOwnProperty")) {
      throw new SaxException("parser.tag.attributes.hasOwnProperty is not a function");
    }
    return tag.attributes.containsKey(name);
  }

  private void setAttribute(String name, String value) {
    if (name.equals("__proto__")) return; // assigning a string to __proto__ is a no-op
    tag.attributes.put(name, value);
  }

  private void attrib() {
    if (!strict) attribName = Js.looseCaseUpper(attribName);
    // attribList is always empty without the xmlns option
    if (attributesHasOwn(attribName)) {
      attribName = attribValue = "";
      return;
    }
    setAttribute(attribName, attribValue);
    attribName = attribValue = "";
  }

  private void openTag(boolean selfClosing) {
    sawRoot = true;
    tags.add(tag);
    if (!selfClosing) {
      if (!noscript && tagName.toLowerCase(java.util.Locale.ROOT).equals("script")) state = S.SCRIPT;
      else state = S.TEXT;
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
      script = "";
    }
    int t = tags.size();
    String tn = tagName;
    if (!strict) tn = Js.looseCaseUpper(tn);
    String closeTo = tn;
    while (t-- > 0) {
      Tag close = tags.get(t);
      if (!close.name.equals(closeTo)) strictFail("Unexpected close tag");
      else break;
    }
    if (t < 0) {
      strictFail("Unmatched closing tag: " + tagName);
      textNode += "</" + tagName + ">";
      state = S.TEXT;
      return;
    }
    tagName = tn;
    int s = tags.size();
    while (s-- > t) {
      tag = tags.remove(tags.size() - 1);
      tagName = tag.name;
    }
    if (t == 0) closedRoot = true;
    tagName = attribValue = attribName = "";
    state = S.TEXT;
  }

  private String parseEntity() {
    String ent = entity;
    String entLC = ent.toLowerCase(java.util.Locale.ROOT);
    String v = SaxEntities.ENTITIES.get(ent);
    if (v != null) return v;
    v = SaxEntities.ENTITIES.get(entLC);
    if (v != null) return v;
    ent = entLC;
    long num = -1;
    boolean isNum = false;
    String numStr = "";
    if (ent.length() > 0 && ent.charAt(0) == '#') {
      int radix = ent.length() > 1 && ent.charAt(1) == 'x' ? 16 : 10;
      ent = ent.substring(radix == 16 ? 2 : 1);
      // parseInt(ent, radix) followed by num.toString(radix): succeeds below only when ent is a plain digit string
      Js.ParsedInt p = Js.parseInt(ent, radix);
      if (p != null) {
        isNum = true;
        num = p.value();
        numStr = p.canonical();
      }
    }
    ent = ent.replaceFirst("^0+", "");
    if (!isNum || !numStr.equals(ent) || num < 0 || num > 0x10ffff || !isXmlChar((int) num)) {
      strictFail("Invalid character entity");
      return "&" + entity + ";";
    }
    return new String(Character.toChars((int) num));
  }

  private static boolean isXmlChar(int num) {
    return num == 0x9 || num == 0xa || num == 0xd || (num >= 0x20 && num <= 0xd7ff)
        || (num >= 0xe000 && num <= 0xfffd) || (num >= 0x10000 && num <= 0x10ffff);
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

  private static boolean isNameStartRange(char c) {
    return c == ':' || c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
        || (c >= 'À' && c <= 'Ö') || (c >= 'Ø' && c <= 'ö') || (c >= 'ø' && c <= '˿')
        || (c >= 'Ͱ' && c <= 'ͽ') || (c >= 'Ϳ' && c <= '῿') || (c >= '‌' && c <= '‍')
        || (c >= '⁰' && c <= '↏') || (c >= 'Ⰰ' && c <= '⿯') || (c >= '、' && c <= '퟿')
        || (c >= '豈' && c <= '﷏') || (c >= 'ﷰ' && c <= '�');
  }

  static boolean isNameStart(char c) {
    return isNameStartRange(c);
  }

  /** nameBody (= entityBody) */
  static boolean isNameBody(char c) {
    return isNameStartRange(c) || c == '·' || (c >= '̀' && c <= 'ͯ') || (c >= '‿' && c <= '⁀')
        || c == '.' || (c >= '0' && c <= '9') || c == '-';
  }

  static boolean isEntityStart(char c) {
    return c == '#' || isNameStartRange(c);
  }

  /** small JS helpers local to this package */
  static final class Js {
    private Js() {}

    record ParsedInt(long value, String canonical) {}

    /**
     * JS parseInt(s, radix) for the inputs sax can produce here (entity-body characters only, so no whitespace or
     * '+'), returning null for NaN. Only results that can pass sax's `numStr === entity` check need an exact value;
     * anything longer than 8 digits is out of the code point range anyway, so it is reported with a canonical text
     * that never matches.
     */
    static ParsedInt parseInt(String s, int radix) {
      int i = 0;
      boolean neg = false;
      if (i < s.length() && s.charAt(i) == '-') {
        neg = true;
        i++;
      }
      if (radix == 16 && i + 1 < s.length() && s.charAt(i) == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
        i += 2;
      }
      int start = i;
      while (i < s.length() && Character.digit(s.charAt(i), radix) >= 0 && s.charAt(i) < 128) i++;
      if (i == start) return null;
      String digits = s.substring(start, i).replaceFirst("^0+(?=.)", "");
      if (digits.length() > 8) return new ParsedInt(Long.MAX_VALUE, "\u0000big");
      long v = Long.parseLong(digits, radix);
      if (neg) {
        // (-n).toString(radix) = "-" + digits ("-0" prints "0"); negative values fail the range check regardless
        return new ParsedInt(-v, v == 0 ? "0" : "-" + Long.toString(v, radix));
      }
      return new ParsedInt(v, Long.toString(v, radix));
    }

    /** loose mode only (not used by SUSHI, strict=true) */
    static String looseCaseUpper(String s) {
      return s.toUpperCase(java.util.Locale.ROOT);
    }
  }

  /** an exception sax throws out of write() */
  public static final class SaxException extends RuntimeException {
    public SaxException(String message) { super(message); }
  }
}
