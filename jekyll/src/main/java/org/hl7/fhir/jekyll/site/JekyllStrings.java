package org.hl7.fhir.jekyll.site;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure string helpers behind Jekyll filters (jekyll-4.4.1/lib/jekyll/filters.rb and utils.rb), plus
 * Addressable::URI.normalize_component (addressable-2.9.1) which `uri_escape` delegates to.
 */
public final class JekyllStrings {
  private JekyllStrings() {}

  // ---- uri_escape: Addressable::URI.normalize_component(input) with RESERVED_AND_UNRESERVED ----

  private static final boolean[] RESERVED_AND_UNRESERVED = new boolean[256];

  static {
    String ok = ":/?#[]@" + "!$&'()*+,;=" + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" + "-._~";
    for (char c : ok.toCharArray()) {
      RESERVED_AND_UNRESERVED[c] = true;
    }
  }

  public static String uriEscape(String component) {
    if (component == null) {
      return null;
    }
    // unencode: every %XX (any case) -> the byte
    byte[] in = component.getBytes(StandardCharsets.UTF_8);
    java.io.ByteArrayOutputStream un = new java.io.ByteArrayOutputStream(in.length);
    for (int i = 0; i < in.length; i++) {
      if (in[i] == '%' && i + 2 < in.length && hex(in[i + 1]) >= 0 && hex(in[i + 2]) >= 0) {
        un.write(hex(in[i + 1]) * 16 + hex(in[i + 2]));
        i += 2;
      } else {
        un.write(in[i]);
      }
    }
    byte[] bytes = un.toByteArray();
    // unicode_normalize(:nfc) when the bytes are valid UTF-8 (else Ruby raises and skips NFC)
    try {
      String s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
      bytes = Normalizer.normalize(s, Normalizer.Form.NFC).getBytes(StandardCharsets.UTF_8);
    } catch (CharacterCodingException e) {
      // keep raw bytes
    }
    StringBuilder out = new StringBuilder(bytes.length + 16);
    for (byte b : bytes) {
      int v = b & 0xff;
      if (RESERVED_AND_UNRESERVED[v]) {
        out.append((char) v);
      } else {
        out.append('%').append(Character.toUpperCase(Character.forDigit(v >> 4, 16)))
            .append(Character.toUpperCase(Character.forDigit(v & 15, 16)));
      }
    }
    // force_encoding(UTF-8): bytes that were percent-encoded are ASCII now; any raw high bytes were encoded
    return out.toString();
  }

  private static int hex(byte b) {
    if (b >= '0' && b <= '9') {
      return b - '0';
    }
    if (b >= 'a' && b <= 'f') {
      return b - 'a' + 10;
    }
    if (b >= 'A' && b <= 'F') {
      return b - 'A' + 10;
    }
    return -1;
  }

  // ---- cgi_escape: Ruby CGI.escape ----

  public static String cgiEscape(String s) {
    StringBuilder out = new StringBuilder();
    for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
      int v = b & 0xff;
      if ((v >= 'a' && v <= 'z') || (v >= 'A' && v <= 'Z') || (v >= '0' && v <= '9') || v == '_' || v == '.' || v == '-'
          || v == '~') {
        out.append((char) v);
      } else if (v == ' ') {
        out.append('+');
      } else {
        out.append('%').append(Character.toUpperCase(Character.forDigit(v >> 4, 16)))
            .append(Character.toUpperCase(Character.forDigit(v & 15, 16)));
      }
    }
    return out.toString();
  }

  // ---- xml_escape: input.to_s.encode(:xml => :attr).gsub(/\A"|"\Z/, "") ----

  public static String xmlEscape(String s) {
    StringBuilder out = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&': out.append("&amp;"); break;
        case '<': out.append("&lt;"); break;
        case '>': out.append("&gt;"); break;
        case '"': out.append("&quot;"); break;
        default: out.append(c);
      }
    }
    return out.toString();
  }

  // ---- normalize_whitespace / number_of_words ----

  private static final Pattern WS = Pattern.compile("\\s+", Pattern.UNIX_LINES);

  public static String normalizeWhitespace(String s) {
    return WS.matcher(s).replaceAll(" ").strip();
  }

  public static long numberOfWords(String s) {
    String t = s.strip();
    return t.isEmpty() ? 0 : WS.split(t).length;
  }

  // ---- slugify: Jekyll::Utils.slugify(string, mode:) (cased: false) ----

  private static final Pattern RAW = Pattern.compile("\\s+", Pattern.UNIX_LINES);
  private static final Pattern DEFAULT = Pattern.compile("[^\\p{M}\\p{L}\\p{Nd}]+", Pattern.UNIX_LINES);
  private static final Pattern PRETTY = Pattern.compile("[^\\p{M}\\p{L}\\p{Nd}._~!$&'()+,;=@]+", Pattern.UNIX_LINES);
  private static final Pattern ASCII = Pattern.compile("[^A-Za-z0-9]+", Pattern.UNIX_LINES);
  private static final Pattern EDGE_HYPHEN = Pattern.compile("^-|-$", Pattern.MULTILINE | Pattern.UNIX_LINES);

  public static String slugify(String s, String mode) {
    if (s == null) {
      return null;
    }
    if (mode == null) {
      mode = "default";
    }
    Pattern p;
    switch (mode) {
      case "raw": p = RAW; break;
      case "default": p = DEFAULT; break;
      case "pretty": p = PRETTY; break;
      case "ascii": p = ASCII; break;
      case "latin":
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", ""); // approximates I18n.transliterate
        p = DEFAULT;
        break;
      default:
        return s.toLowerCase(Locale.ROOT);
    }
    String slug = p.matcher(s).replaceAll("-");
    slug = EDGE_HYPHEN.matcher(slug).replaceAll("");
    return slug.toLowerCase(Locale.ROOT);
  }
}
