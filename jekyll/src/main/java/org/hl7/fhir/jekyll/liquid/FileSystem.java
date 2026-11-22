package org.hl7.fhir.jekyll.liquid;

import java.util.Map;

/**
 * Port of liquid-4.0.4/lib/liquid/file_system.rb. Liquid's own {@code include} reads partials
 * through {@code context.registers["file_system"]}, falling back to the environment's file system
 * (default {@link #BLANK}).
 */
public interface FileSystem {
  /** Ruby {@code read_template_file(template_path)}. */
  String readTemplateFile(Object templatePath);

  /** Ruby {@code BlankFileSystem}. */
  FileSystem BLANK = path -> {
    throw new LiquidException(LiquidException.Kind.FILE_SYSTEM, "This liquid context does not allow includes.");
  };

  /** In-memory file system (what the oracle uses for test cases). */
  static FileSystem ofMap(Map<String, String> files) {
    return path -> {
      Object src = files.get(path);
      if (src == null) {
        throw new LiquidException(LiquidException.Kind.FILE_SYSTEM, "No such template '" + Values.rubyToS(path) + "'");
      }
      return (String) src;
    };
  }
}
