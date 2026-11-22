// Vendored from fhir-ig-jekyll@ecd3c065 shim/src/main/java/org/hl7/fhir/jekyll/yaml/Token.java (libyaml 0.2.5 event layer),
// unchanged except the package line, until the monorepo move puts it in common (PORTING.md section 7).
package org.hl7.fhir.sushi.yaml;

/** Port of {@code yaml_token_t} / {@code yaml_token_type_t} (libyaml-0.2.5/include/yaml.h). */
final class Token {
  enum Type {
    NO, STREAM_START, STREAM_END, VERSION_DIRECTIVE, TAG_DIRECTIVE, DOCUMENT_START, DOCUMENT_END,
    BLOCK_SEQUENCE_START, BLOCK_MAPPING_START, BLOCK_END, FLOW_SEQUENCE_START, FLOW_SEQUENCE_END,
    FLOW_MAPPING_START, FLOW_MAPPING_END, BLOCK_ENTRY, FLOW_ENTRY, KEY, VALUE, ALIAS, ANCHOR, TAG, SCALAR
  }

  /** yaml_scalar_style_t */
  static final int ANY_SCALAR_STYLE = 0, PLAIN_SCALAR_STYLE = 1, SINGLE_QUOTED_SCALAR_STYLE = 2,
      DOUBLE_QUOTED_SCALAR_STYLE = 3, LITERAL_SCALAR_STYLE = 4, FOLDED_SCALAR_STYLE = 5;

  final Type type;
  final Mark startMark;
  final Mark endMark;
  /** stream_start.encoding */
  int encoding;
  /** alias.value / anchor.value / scalar.value (scalar keeps its full length, NULs included) */
  byte[] value;
  int style;
  /** tag.handle / tag_directive.handle (C strings: no NUL) */
  byte[] handle;
  /** tag.suffix / tag_directive.prefix (C strings: truncated at the first NUL) */
  byte[] suffix;
  int major;
  int minor;

  Token(Type type, Mark startMark, Mark endMark) {
    this.type = type;
    this.startMark = startMark;
    this.endMark = endMark;
  }
}
