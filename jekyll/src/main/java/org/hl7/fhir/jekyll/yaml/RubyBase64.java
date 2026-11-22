package org.hl7.fhir.jekyll.yaml;

import java.io.ByteArrayOutputStream;

/**
 * {@code Base64.decode64(str)} = {@code str.unpack1("m")}: the non-strict branch of the 'm'
 * directive in ruby-3.2.3/pack.c (characters outside the alphabet are skipped, '=' ends the data,
 * a trailing partial group yields 1 or 2 bytes).
 */
final class RubyBase64 {
  private RubyBase64() {}

  private static final int[] XTABLE = new int[256];

  static {
    java.util.Arrays.fill(XTABLE, -1);
    String table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    for (int i = 0; i < 64; i++) {
      XTABLE[table.charAt(i)] = i;
    }
  }

  static byte[] decode64(byte[] str) {
    // a Ruby string is NUL-terminated: reading *send gives 0, which maps to -1
    byte[] in = new byte[str.length + 1];
    System.arraycopy(str, 0, in, 0, str.length);
    int s = 0;
    int send = str.length;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int a = -1;
    int b = -1;
    int c = 0;
    int d;
    while (s < send) {
      a = b = c = d = -1;
      while ((a = XTABLE[in[s] & 0xFF]) == -1 && s < send) {
        s++;
      }
      if (s >= send) {
        break;
      }
      s++;
      while ((b = XTABLE[in[s] & 0xFF]) == -1 && s < send) {
        s++;
      }
      if (s >= send) {
        break;
      }
      s++;
      while ((c = XTABLE[in[s] & 0xFF]) == -1 && s < send) {
        if (in[s] == '=') {
          break;
        }
        s++;
      }
      if (in[s] == '=' || s >= send) {
        break;
      }
      s++;
      while ((d = XTABLE[in[s] & 0xFF]) == -1 && s < send) {
        if (in[s] == '=') {
          break;
        }
        s++;
      }
      if (in[s] == '=' || s >= send) {
        break;
      }
      s++;
      out.write((a << 2 | b >> 4) & 0xFF);
      out.write((b << 4 | c >> 2) & 0xFF);
      out.write((c << 6 | d) & 0xFF);
      a = -1;
    }
    if (a != -1 && b != -1) {
      if (c == -1) {
        out.write((a << 2 | b >> 4) & 0xFF);
      } else {
        out.write((a << 2 | b >> 4) & 0xFF);
        out.write((b << 4 | c >> 2) & 0xFF);
      }
    }
    return out.toByteArray();
  }
}
