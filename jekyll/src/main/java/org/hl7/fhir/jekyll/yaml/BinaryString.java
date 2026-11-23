package org.hl7.fhir.jekyll.yaml;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A Ruby String tagged UTF-8 whose bytes are not valid UTF-8: the result of decoding a
 * {@code !binary} scalar (SafeYAML force-encodes the decoded bytes as UTF-8). Valid UTF-8 results
 * are plain {@link String}s; only invalid ones become a BinaryString. {@code JSON.generate} raises
 * on such strings; Liquid would output the raw bytes.
 */
public final class BinaryString {
  private final byte[] bytes;

  private BinaryString(byte[] bytes) {
    this.bytes = bytes;
  }

  /** The decoded bytes as a String when they are valid UTF-8, else a BinaryString. */
  static Object utf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    } catch (CharacterCodingException e) {
      return new BinaryString(bytes);
    }
  }

  public byte[] bytes() {
    return bytes.clone();
  }

  /** Lossy (replacement characters); use {@link #bytes()} for the data. */
  @Override
  public String toString() {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof BinaryString && Arrays.equals(bytes, ((BinaryString) o).bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
