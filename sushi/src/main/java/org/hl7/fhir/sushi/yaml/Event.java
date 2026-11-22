// Vendored from fhir-ig-jekyll@ecd3c065 shim/src/main/java/org/hl7/fhir/jekyll/yaml/Event.java (libyaml 0.2.5 event layer),
// unchanged except the package line, until the monorepo move puts it in common (PORTING.md section 7).
package org.hl7.fhir.sushi.yaml;

import java.util.List;

/** Port of {@code yaml_event_t} / {@code yaml_event_type_t} (libyaml-0.2.5/include/yaml.h). */
final class Event {
  enum Type {
    NO, STREAM_START, STREAM_END, DOCUMENT_START, DOCUMENT_END, ALIAS, SCALAR, SEQUENCE_START, SEQUENCE_END,
    MAPPING_START, MAPPING_END
  }

  /** yaml_sequence_style_t / yaml_mapping_style_t */
  static final int BLOCK_STYLE = 1, FLOW_STYLE = 2;

  final Type type;
  final Mark startMark;
  final Mark endMark;
  int encoding;
  /** document_start: version directive {major, minor} or null; tag directives {handle, prefix} */
  int[] versionDirective;
  List<byte[][]> tagDirectives;
  /** document_start / document_end / sequence_start / mapping_start implicit flag */
  boolean implicit;
  byte[] anchor;
  byte[] tag;
  byte[] value;
  boolean plainImplicit;
  boolean quotedImplicit;
  int style;

  Event(Type type, Mark startMark, Mark endMark) {
    this.type = type;
    this.startMark = startMark;
    this.endMark = endMark;
  }
}
