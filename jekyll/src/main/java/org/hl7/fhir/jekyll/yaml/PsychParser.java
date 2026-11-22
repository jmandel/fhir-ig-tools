package org.hl7.fhir.jekyll.yaml;

import java.nio.charset.StandardCharsets;
import org.hl7.fhir.common.yaml.Event;
import org.hl7.fhir.common.yaml.LibyamlError;
import org.hl7.fhir.common.yaml.Parser;

/**
 * Port of psych-5.0.1/ext/psych/psych_parser.c ({@code Psych::Parser#_native_parse}): drives
 * libyaml's {@code yaml_parser_parse} and calls the handler for each event, then raises
 * Psych::SyntaxError from the parser's error state ({@code make_exception}). Strings handed to the
 * handler are UTF-8 (values keep their full length; anchors and tags are C strings).
 *
 * <p>SafeYAML's handler returns from {@code SafeYAML.load} inside {@code end_document} (a non-local
 * {@code return} from the block), so parsing stops at the end of the first document;
 * {@link PsychHandler#finished()} models that.
 */
final class PsychParser {
  private PsychParser() {}

  /**
   * @param yaml the input bytes (a Ruby String's bytes, or an IO's remaining bytes)
   * @param parserEncoding the encoding Psych sets on the libyaml parser ({@code transcode_string}
   *     / {@code transcode_io}); {@link Parser#ANY_ENCODING} lets libyaml detect a BOM
   * @param path the file name for error messages (nil: "&lt;unknown&gt;")
   */
  static void parse(PsychHandler handler, byte[] yaml, int parserEncoding, String path) {
    Parser parser = new Parser(yaml, parserEncoding);
    boolean done = false;
    while (!done) {
      Event event;
      try {
        event = parser.parse();
      } catch (LibyamlError e) {
        throw makeException(e, path);
      }
      if (event == null) {
        // yaml_parser_parse yields YAML_NO_EVENT after the end: Psych calls handler.empty (a no-op)
        return;
      }
      switch (event.type) {
        case STREAM_START:
        case DOCUMENT_START:
          break;
        case DOCUMENT_END:
          handler.endDocument(event.implicit);
          break;
        case ALIAS:
          handler.alias(cstr(event.anchor));
          break;
        case SCALAR:
          handler.scalar(new String(event.value, StandardCharsets.UTF_8), cstr(event.anchor), cstr(event.tag), event.plainImplicit,
              event.quotedImplicit, event.style);
          break;
        case SEQUENCE_START:
          handler.startSequence(cstr(event.anchor), cstr(event.tag), event.implicit, event.style);
          break;
        case SEQUENCE_END:
          handler.endSequence();
          break;
        case MAPPING_START:
          handler.startMapping(cstr(event.anchor), cstr(event.tag), event.implicit, event.style);
          break;
        case MAPPING_END:
          handler.endMapping();
          break;
        case STREAM_END:
          done = true;
          break;
        default:
          break;
      }
      if (handler.finished()) {
        return;
      }
    }
  }

  /** rb_str_new2 of a NUL-terminated C string (null for a NULL pointer). */
  private static String cstr(byte[] b) {
    if (b == null) {
      return null;
    }
    int n = 0;
    while (n < b.length && b[n] != 0) {
      n++;
    }
    return new String(b, 0, n, StandardCharsets.UTF_8);
  }

  /** make_exception */
  private static PsychSyntaxError makeException(LibyamlError e, String path) {
    long line = e.contextMark.line + 1;
    long column = e.contextMark.column + 1;
    return new PsychSyntaxError(path, line, column, e.problemOffset, e.problem, e.context);
  }
}
