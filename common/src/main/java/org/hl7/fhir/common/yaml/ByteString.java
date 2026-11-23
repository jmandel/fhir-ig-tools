package org.hl7.fhir.common.yaml;

import java.util.Arrays;

/**
 * Port of libyaml's {@code yaml_string_t} with the STRING_EXTEND / CLEAR / JOIN macros
 * (libyaml-0.2.5/src/yaml_private.h). libyaml strings are zero-filled, so {@code start[0]} of an
 * empty string reads as NUL; {@link #first()} mirrors that. (JOIN leaves the joined-from string's
 * bytes in place, but every JOIN in the scanner is followed by CLEAR, so "empty reads NUL" holds.)
 */
final class ByteString {
  byte[] b = new byte[16];
  int len;

  void add(int c) {
    if (len == b.length) {
      b = Arrays.copyOf(b, b.length * 2);
    }
    b[len++] = (byte) c;
  }

  /** JOIN(a, this-is-a, other): append other to this and reset other. */
  void join(ByteString other) {
    if (len + other.len > b.length) {
      b = Arrays.copyOf(b, Math.max(b.length * 2, len + other.len));
    }
    System.arraycopy(other.b, 0, b, len, other.len);
    len += other.len;
    other.len = 0;
  }

  void clear() {
    len = 0;
  }

  /** {@code *string.start}: the first byte, or NUL for an empty string. */
  int first() {
    return len > 0 ? b[0] & 0xFF : 0;
  }

  boolean isEmpty() {
    return len == 0;
  }

  byte[] toBytes() {
    return Arrays.copyOf(b, len);
  }

  /** The bytes as a C string: up to (not including) the first NUL. */
  byte[] toCString() {
    for (int k = 0; k < len; k++) {
      if (b[k] == 0) {
        return Arrays.copyOf(b, k);
      }
    }
    return toBytes();
  }
}
