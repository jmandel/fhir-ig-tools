// Vendored from fhir-ig-jekyll@ecd3c065 shim/src/main/java/org/hl7/fhir/jekyll/yaml/Reader.java (libyaml 0.2.5 event layer),
// unchanged except the package line and the opt-in yaml110 switch (marked "SUSHI:"), until the monorepo move puts
// it in common (PORTING.md section 7).
package org.hl7.fhir.sushi.yaml;

import java.util.Arrays;

/**
 * Port of libyaml-0.2.5/src/reader.c (plus the string read handler from api.c): decodes the input
 * (UTF-8 or UTF-16) into the internal UTF-8 buffer, checking it the way libyaml does.
 *
 * <p>The chunking is kept exactly: the raw buffer holds {@value #INPUT_RAW_BUFFER_SIZE} bytes and a
 * refill decodes the whole raw buffer, so a reader error ("invalid leading UTF-8 octet", "control
 * characters are not allowed", ...) surfaces at the same point of the token stream as in C.
 *
 * <p>Fields mirror {@code yaml_parser_t}; {@code buffer[pointer..last)} are the unread decoded bytes,
 * {@code unread} counts their characters (the NUL appended at EOF included).
 */
abstract class Reader {
  static final int INPUT_RAW_BUFFER_SIZE = 16384;
  /** yaml_encoding_t */
  static final int ANY_ENCODING = 0, UTF8_ENCODING = 1, UTF16LE_ENCODING = 2, UTF16BE_ENCODING = 3;

  /** the input string / IO contents (yaml_parser_set_input_string, Psych's io_reader) */
  private final byte[] input;
  private int inputPos;

  private final byte[] raw = new byte[INPUT_RAW_BUFFER_SIZE];
  private int rawPointer;
  private int rawLast;
  private boolean eof;

  int encoding;

  /**
   * SUSHI: yaml@1.10 (YAML 1.2) compatibility, off by default so the Jekyll shim's libyaml behaviour is unchanged.
   * Reader: any character is accepted (libyaml rejects C0/C1 controls and DEL), and only CR and LF are line breaks
   * (libyaml, YAML 1.1, also breaks on NEL, LS and PS). Scanner and Parser read it for their own relaxations.
   */
  boolean yaml110;

  byte[] buffer = new byte[INPUT_RAW_BUFFER_SIZE * 3 + 8];
  int pointer;
  int last;
  long unread;
  long offset;

  /** parser->mark */
  long markIndex;
  long markLine;
  long markColumn;

  Reader(byte[] input, int encoding) {
    this.input = input;
    this.encoding = encoding;
  }

  final Mark mark() {
    return new Mark(markIndex, markLine, markColumn);
  }

  private static LibyamlError readerError(String problem, long offset, int value) {
    return new LibyamlError(LibyamlError.READER_ERROR, problem, offset, value, Mark.ZERO, null, Mark.ZERO);
  }

  /** yaml_parser_determine_encoding */
  private void determineEncoding() {
    while (!eof && rawLast - rawPointer < 3) {
      updateRawBuffer();
    }
    int n = rawLast - rawPointer;
    if (n >= 2 && (raw[rawPointer] & 0xFF) == 0xFF && (raw[rawPointer + 1] & 0xFF) == 0xFE) {
      encoding = UTF16LE_ENCODING;
      rawPointer += 2;
      offset += 2;
    } else if (n >= 2 && (raw[rawPointer] & 0xFF) == 0xFE && (raw[rawPointer + 1] & 0xFF) == 0xFF) {
      encoding = UTF16BE_ENCODING;
      rawPointer += 2;
      offset += 2;
    } else if (n >= 3 && (raw[rawPointer] & 0xFF) == 0xEF && (raw[rawPointer + 1] & 0xFF) == 0xBB
        && (raw[rawPointer + 2] & 0xFF) == 0xBF) {
      encoding = UTF8_ENCODING;
      rawPointer += 3;
      offset += 3;
    } else {
      encoding = UTF8_ENCODING;
    }
  }

  /** yaml_parser_update_raw_buffer (with yaml_string_read_handler inlined) */
  private void updateRawBuffer() {
    if (rawPointer == 0 && rawLast == raw.length) {
      return;
    }
    if (eof) {
      return;
    }
    if (0 < rawPointer && rawPointer < rawLast) {
      System.arraycopy(raw, rawPointer, raw, 0, rawLast - rawPointer);
    }
    rawLast -= rawPointer;
    rawPointer = 0;
    int size = Math.min(raw.length - rawLast, input.length - inputPos);
    System.arraycopy(input, inputPos, raw, rawLast, size);
    inputPos += size;
    rawLast += size;
    if (size == 0) {
      eof = true;
    }
  }

  private void put(int b) {
    if (last == buffer.length) {
      buffer = Arrays.copyOf(buffer, buffer.length * 2);
    }
    buffer[last++] = (byte) b;
  }

  /** yaml_parser_update_buffer: ensure the buffer holds at least {@code length} characters. */
  final void updateBuffer(long length) {
    boolean first = true;
    if (eof && rawPointer == rawLast) {
      return;
    }
    if (unread >= length) {
      return;
    }
    if (encoding == ANY_ENCODING) {
      determineEncoding();
    }
    if (0 < pointer && pointer < last) {
      int size = last - pointer;
      System.arraycopy(buffer, pointer, buffer, 0, size);
      pointer = 0;
      last = size;
    } else if (pointer == last) {
      pointer = 0;
      last = 0;
    }
    while (unread < length) {
      if (!first || rawPointer == rawLast) {
        updateRawBuffer();
      }
      first = false;
      while (rawPointer != rawLast) {
        int value = 0;
        int value2;
        boolean incomplete = false;
        int octet;
        int width = 0;
        int rawUnread = rawLast - rawPointer;
        switch (encoding) {
          case UTF8_ENCODING:
            octet = raw[rawPointer] & 0xFF;
            width = (octet & 0x80) == 0x00 ? 1 : (octet & 0xE0) == 0xC0 ? 2 : (octet & 0xF0) == 0xE0 ? 3 : (octet & 0xF8) == 0xF0 ? 4 : 0;
            if (width == 0) {
              throw readerError("invalid leading UTF-8 octet", offset, octet);
            }
            if (width > rawUnread) {
              if (eof) {
                throw readerError("incomplete UTF-8 octet sequence", offset, -1);
              }
              incomplete = true;
              break;
            }
            value = (octet & 0x80) == 0x00 ? octet & 0x7F : (octet & 0xE0) == 0xC0 ? octet & 0x1F : (octet & 0xF0) == 0xE0 ? octet & 0x0F : (octet & 0xF8) == 0xF0 ? octet & 0x07 : 0;
            for (int k = 1; k < width; k++) {
              octet = raw[rawPointer + k] & 0xFF;
              if ((octet & 0xC0) != 0x80) {
                throw readerError("invalid trailing UTF-8 octet", offset + k, octet);
              }
              value = (value << 6) + (octet & 0x3F);
            }
            if (!(width == 1 || (width == 2 && value >= 0x80) || (width == 3 && value >= 0x800) || (width == 4 && value >= 0x10000))) {
              throw readerError("invalid length of a UTF-8 sequence", offset, -1);
            }
            if ((value >= 0xD800 && value <= 0xDFFF) || value > 0x10FFFF) {
              throw readerError("invalid Unicode character", offset, value);
            }
            break;
          case UTF16LE_ENCODING:
          case UTF16BE_ENCODING: {
            int low = encoding == UTF16LE_ENCODING ? 0 : 1;
            int high = encoding == UTF16LE_ENCODING ? 1 : 0;
            if (rawUnread < 2) {
              if (eof) {
                throw readerError("incomplete UTF-16 character", offset, -1);
              }
              incomplete = true;
              break;
            }
            value = (raw[rawPointer + low] & 0xFF) + ((raw[rawPointer + high] & 0xFF) << 8);
            if ((value & 0xFC00) == 0xDC00) {
              throw readerError("unexpected low surrogate area", offset, value);
            }
            if ((value & 0xFC00) == 0xD800) {
              width = 4;
              if (rawUnread < 4) {
                if (eof) {
                  throw readerError("incomplete UTF-16 surrogate pair", offset, -1);
                }
                incomplete = true;
                break;
              }
              value2 = (raw[rawPointer + low + 2] & 0xFF) + ((raw[rawPointer + high + 2] & 0xFF) << 8);
              if ((value2 & 0xFC00) != 0xDC00) {
                throw readerError("expected low surrogate area", offset + 2, value2);
              }
              value = 0x10000 + ((value & 0x3FF) << 10) + (value2 & 0x3FF);
            } else {
              width = 2;
            }
            break;
          }
          default:
            throw new IllegalStateException("encoding");
        }
        if (incomplete) {
          break;
        }
        if (!yaml110 && !(value == 0x09 || value == 0x0A || value == 0x0D || (value >= 0x20 && value <= 0x7E) || (value == 0x85)
            || (value >= 0xA0 && value <= 0xD7FF) || (value >= 0xE000 && value <= 0xFFFD) || (value >= 0x10000 && value <= 0x10FFFF))) {
          throw readerError("control characters are not allowed", offset, value);
        }
        rawPointer += width;
        offset += width;
        if (value <= 0x7F) {
          put(value);
        } else if (value <= 0x7FF) {
          put(0xC0 + (value >> 6));
          put(0x80 + (value & 0x3F));
        } else if (value <= 0xFFFF) {
          put(0xE0 + (value >> 12));
          put(0x80 + ((value >> 6) & 0x3F));
          put(0x80 + (value & 0x3F));
        } else {
          put(0xF0 + (value >> 18));
          put(0x80 + ((value >> 12) & 0x3F));
          put(0x80 + ((value >> 6) & 0x3F));
          put(0x80 + (value & 0x3F));
        }
        unread++;
      }
      if (eof) {
        put(0);
        unread++;
        return;
      }
    }
  }

  // ---- yaml_private.h character macros on the decoded buffer ----

  /** {@code buffer.pointer[k]} as an unsigned byte (0 past the decoded data). */
  final int at(int k) {
    int i = pointer + k;
    return i < last ? buffer[i] & 0xFF : 0;
  }

  final boolean check(char c) {
    return at(0) == c;
  }

  final boolean checkAt(char c, int k) {
    return at(k) == c;
  }

  final boolean isAlpha() {
    int c = at(0);
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || c == '-';
  }

  final boolean isDigit() {
    int c = at(0);
    return c >= '0' && c <= '9';
  }

  final int asDigit() {
    return at(0) - '0';
  }

  final boolean isHexAt(int k) {
    int c = at(k);
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
  }

  final int asHexAt(int k) {
    int c = at(k);
    return (c >= 'A' && c <= 'F') ? c - 'A' + 10 : (c >= 'a' && c <= 'f') ? c - 'a' + 10 : c - '0';
  }

  final boolean isZ(int k) {
    return at(k) == 0;
  }

  final boolean isZ() {
    return at(0) == 0;
  }

  final boolean isBom() {
    return at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF;
  }

  final boolean isSpace() {
    return at(0) == ' ';
  }

  final boolean isTab() {
    return at(0) == '\t';
  }

  final boolean isBlankAt(int k) {
    int c = at(k);
    return c == ' ' || c == '\t';
  }

  final boolean isBlank() {
    return isBlankAt(0);
  }

  final boolean isBreakAt(int k) {
    int c = at(k);
    return c == '\r' || c == '\n' || (!yaml110 && ((c == 0xC2 && at(k + 1) == 0x85)
        || (c == 0xE2 && at(k + 1) == 0x80 && (at(k + 2) == 0xA8 || at(k + 2) == 0xA9))));
  }

  final boolean isBreak() {
    return isBreakAt(0);
  }

  final boolean isCrlf() {
    return at(0) == '\r' && at(1) == '\n';
  }

  final boolean isBreakzAt(int k) {
    return isBreakAt(k) || isZ(k);
  }

  final boolean isBreakz() {
    return isBreakzAt(0);
  }

  final boolean isSpacez() {
    return isSpace() || isBreakz();
  }

  final boolean isBlankzAt(int k) {
    return isBlankAt(k) || isBreakzAt(k);
  }

  final boolean isBlankz() {
    return isBlankzAt(0);
  }

  final int widthAt(int k) {
    int c = at(k);
    return (c & 0x80) == 0x00 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 0;
  }
}
