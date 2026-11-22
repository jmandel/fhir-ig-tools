package org.hl7.fhir.jekyll.yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.hl7.fhir.common.yaml.Parser;

/**
 * YAML loading exactly as Jekyll 4.4.1 does it: {@code SafeYAML.load} / {@code SafeYAML.load_file}
 * (safe_yaml-1.0.5/lib/safe_yaml/load.rb, the Psych branch with no whitelisted tags) over Psych
 * 5.0.1 and libyaml 0.2.5 (Ruby 3.2.3). Used for {@code _config.yml}, {@code _data/*.yml|yaml|json}
 * (Jekyll loads JSON through SafeYAML too) and front matter.
 *
 * <h2>Values</h2>
 * <ul>
 *   <li>mapping → {@code LinkedHashMap<Object,Object>} (insertion order; a duplicate key keeps its
 *       first position and takes the last value). Keys are {@code String} for ordinary YAML; a key
 *       that types to something else stays that Java value ({@code Long}, {@code BigInteger},
 *       {@code Double}, {@code Boolean}, {@link RubyDate}, RubyTime, a List/Map for complex
 *       {@code ?} keys) exactly like the Ruby Hash key, so a lookup by String misses as in Ruby.
 *       A key that loads as nil is not stored (SafeYAML quirk: the next scalar becomes the key).
 *   <li>sequence → {@code ArrayList<Object>}
 *   <li>scalars → {@code String}, {@code Long} (or {@code BigInteger} beyond 64 bits),
 *       {@code Double}, {@code Boolean}, {@code null}, {@link RubyDate} (YYYY-MM-DD),
 *       {@code org.hl7.fhir.jekyll.liquid.RubyTime} (timestamps; fixed offset, utc? false),
 *       {@link BinaryString} ({@code !binary} data that is not UTF-8).
 *   <li>an empty stream (no document: empty or only comments) → {@code Boolean.FALSE}; only the
 *       first document of a stream is loaded.
 * </ul>
 *
 * Errors are {@link YamlException}s carrying the Ruby class ({@link PsychSyntaxError} with
 * Psych's exact message, TypeError, ArgumentError).
 */
public final class Yaml {
  private Yaml() {}

  /** {@code SafeYAML.load(text)}: text is a UTF-8 Ruby String (Psych sets the UTF-8 encoding; no BOM sniffing). */
  public static Object load(String text) {
    return load(text.getBytes(StandardCharsets.UTF_8), Parser.UTF8_ENCODING, null);
  }

  /**
   * {@code SafeYAML.load(str)} for a Ruby String given as its raw bytes (tagged UTF-8, possibly
   * invalid: libyaml then raises its reader error, as Ruby would).
   */
  public static Object loadBytes(byte[] utf8) {
    return load(utf8, Parser.UTF8_ENCODING, null);
  }

  /**
   * {@code SafeYAML.load_file(path)}: {@code File.open(path, 'r:bom|utf-8')} (a UTF-8 BOM is
   * dropped; a UTF-16/UTF-32 BOM makes Ruby raise ArgumentError "ASCII incompatible encoding needs
   * binmode"), with the path in error messages.
   */
  public static Object loadFile(Path path) {
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return loadFileBytes(bytes, path.toString());
  }

  /** {@link #loadFile} for already-read file contents; {@code fileName} is used in error messages. */
  public static Object loadFileBytes(byte[] bytes, String fileName) {
    int n = bytes.length;
    int b1 = n > 0 ? bytes[0] & 0xFF : -1;
    int b2 = n > 1 ? bytes[1] & 0xFF : -1;
    int b3 = n > 2 ? bytes[2] & 0xFF : -1;
    int b4 = n > 3 ? bytes[3] & 0xFF : -1;
    int skip = 0;
    int encoding = Parser.UTF8_ENCODING; // transcode_io: external encoding UTF-8 (or US-ASCII)
    if (b1 == 0xEF && b2 == 0xBB && b3 == 0xBF) {
      skip = 3;
    } else if ((b1 == 0xFE && b2 == 0xFF) || (b1 == 0xFF && b2 == 0xFE) || (b1 == 0 && b2 == 0 && b3 == 0xFE && b4 == 0xFF)) {
      // 'bom|utf-8' switches the IO to UTF-16/32, which text-mode File.open rejects
      throw new YamlException("ArgumentError", "ASCII incompatible encoding needs binmode");
    }
    return load(skip == 0 ? bytes : Arrays.copyOfRange(bytes, skip, n), encoding, fileName);
  }

  private static Object load(byte[] yaml, int encoding, String path) {
    PsychHandler handler = new PsychHandler();
    PsychParser.parse(handler, yaml, encoding, path);
    return handler.result();
  }
}
