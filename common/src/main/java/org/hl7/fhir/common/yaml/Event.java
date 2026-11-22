package org.hl7.fhir.common.yaml;

import java.util.List;

/**
 * Port of {@code yaml_event_t} / {@code yaml_event_type_t} (libyaml-0.2.5/include/yaml.h). Like the C struct,
 * the fields are public and only the ones for {@link #type} are set; treat them as read-only. Strings are
 * UTF-8 bytes: {@code value} keeps its full length (NULs included), {@code anchor} and {@code tag} are C strings
 * (null when absent). {@code style} is a {@code yaml_scalar_style_t} for scalars ({@code PLAIN_SCALAR_STYLE} = 1 ..
 * {@code FOLDED_SCALAR_STYLE} = 5) or {@link #BLOCK_STYLE} / {@link #FLOW_STYLE}.
 */
public final class Event {
  public enum Type {
    NO, STREAM_START, STREAM_END, DOCUMENT_START, DOCUMENT_END, ALIAS, SCALAR, SEQUENCE_START, SEQUENCE_END,
    MAPPING_START, MAPPING_END
  }

  /** yaml_sequence_style_t / yaml_mapping_style_t */
  public static final int BLOCK_STYLE = 1, FLOW_STYLE = 2;

  /** yaml_scalar_style_t (the scanner's values, see Token) */
  public static final int ANY_SCALAR_STYLE = Token.ANY_SCALAR_STYLE, PLAIN_SCALAR_STYLE = Token.PLAIN_SCALAR_STYLE,
      SINGLE_QUOTED_SCALAR_STYLE = Token.SINGLE_QUOTED_SCALAR_STYLE, DOUBLE_QUOTED_SCALAR_STYLE = Token.DOUBLE_QUOTED_SCALAR_STYLE,
      LITERAL_SCALAR_STYLE = Token.LITERAL_SCALAR_STYLE, FOLDED_SCALAR_STYLE = Token.FOLDED_SCALAR_STYLE;

  public final Type type;
  public final Mark startMark;
  public final Mark endMark;
  public int encoding;
  /** document_start: version directive {major, minor} or null; tag directives {handle, prefix} */
  public int[] versionDirective;
  public List<byte[][]> tagDirectives;
  /** document_start / document_end / sequence_start / mapping_start implicit flag */
  public boolean implicit;
  public byte[] anchor;
  public byte[] tag;
  public byte[] value;
  public boolean plainImplicit;
  public boolean quotedImplicit;
  public int style;

  Event(Type type, Mark startMark, Mark endMark) {
    this.type = type;
    this.startMark = startMark;
    this.endMark = endMark;
  }
}
