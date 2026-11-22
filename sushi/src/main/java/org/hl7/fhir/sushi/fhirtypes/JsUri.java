package org.hl7.fhir.sushi.fhirtypes;

import java.nio.charset.StandardCharsets;

/** JS encodeURIComponent / decodeURIComponent (ECMA-262 Encode/Decode). */
public final class JsUri {
  private JsUri() {}

  /** JS URIError. */
  public static final class URIError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public URIError() {
      super("URI malformed");
    }
  }

  private static final String UNRESERVED_COMPONENT =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()";
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  public static String encodeURIComponent(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (UNRESERVED_COMPONENT.indexOf(c) >= 0) {
        sb.append(c);
        continue;
      }
      int cp;
      if (Character.isHighSurrogate(c)) {
        if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) throw new URIError();
        cp = Character.toCodePoint(c, s.charAt(++i));
      } else if (Character.isLowSurrogate(c)) {
        throw new URIError();
      } else {
        cp = c;
      }
      for (byte b : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8)) {
        sb.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
      }
    }
    return sb.toString();
  }

  /** decodeURIComponent: reserved set is empty, so every valid escape is decoded. */
  public static String decodeURIComponent(String s) {
    StringBuilder sb = new StringBuilder();
    int n = s.length();
    for (int k = 0; k < n; k++) {
      char c = s.charAt(k);
      if (c != '%') {
        sb.append(c);
        continue;
      }
      int b = hexByte(s, k);
      k += 2;
      if ((b & 0x80) == 0) {
        sb.append((char) b);
        continue;
      }
      int len;
      if ((b & 0xE0) == 0xC0) len = 2;
      else if ((b & 0xF0) == 0xE0) len = 3;
      else if ((b & 0xF8) == 0xF0) len = 4;
      else throw new URIError();
      byte[] octets = new byte[len];
      octets[0] = (byte) b;
      for (int j = 1; j < len; j++) {
        k++;
        if (k >= n || s.charAt(k) != '%') throw new URIError();
        int bj = hexByte(s, k);
        if ((bj & 0xC0) != 0x80) throw new URIError();
        k += 2;
        octets[j] = (byte) bj;
      }
      // validate (overlong / surrogates / range) by decoding strictly
      int cp = decodeUtf8(octets);
      sb.appendCodePoint(cp);
    }
    return sb.toString();
  }

  private static int hexByte(String s, int k) {
    if (k + 2 >= s.length() + 0 && k + 2 > s.length() - 1 + 1) throw new URIError();
    if (k + 2 >= s.length() + 1) throw new URIError();
    int h = Character.digit(s.charAt(k + 1), 16);
    int l = Character.digit(s.charAt(k + 2), 16);
    if (h < 0 || l < 0) throw new URIError();
    return (h << 4) | l;
  }

  private static int decodeUtf8(byte[] o) {
    int cp;
    int min;
    switch (o.length) {
      case 2 -> { cp = o[0] & 0x1F; min = 0x80; }
      case 3 -> { cp = o[0] & 0x0F; min = 0x800; }
      default -> { cp = o[0] & 0x07; min = 0x10000; }
    }
    for (int i = 1; i < o.length; i++) cp = (cp << 6) | (o[i] & 0x3F);
    if (cp < min || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) throw new URIError();
    return cp;
  }
}
