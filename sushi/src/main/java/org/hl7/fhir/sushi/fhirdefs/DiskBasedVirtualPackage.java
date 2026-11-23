package org.hl7.fhir.sushi.fhirdefs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.Fs;

// port of fhir-package-loader 2.2.4 src/virtual/DiskBasedVirtualPackage.ts
/** Resources from local files/folders (SUSHI: predefined resources in input/, test fixtures). */
public class DiskBasedVirtualPackage implements VirtualPackage {
  /**
   * FHIR XML -> JS value model. FPL (TypeScript SUSHI) uses fhir-tool's xmlToObj; the port uses org.hl7.fhir.core's
   * parser for the IG's FHIR version ({@link FhirXml}; docs/core-divergences.md C6).
   */
  @FunctionalInterface
  public interface XmlConverter {
    Object xmlToObj(String xml, String fhirVersion) throws Exception;
  }

  private static volatile XmlConverter xmlConverter = FhirXml::xmlToObj;

  /** Replaces the XML converter (harnesses); null restores the default, {@link FhirXml#xmlToObj}. */
  public static void setXmlConverter(XmlConverter c) {
    xmlConverter = c != null ? c : FhirXml::xmlToObj;
  }

  /** an XML file that the converter could not read: skipped with a warning */
  static final class XmlConversionException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    XmlConversionException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final Pattern JSON_NAME = Pattern.compile("\\.json$", Pattern.CASE_INSENSITIVE);
  private static final Pattern SPREADSHEET_NAME = Pattern.compile("-spreadsheet\\.xml$", Pattern.CASE_INSENSITIVE);
  private static final Pattern XML_NAME = Pattern.compile("\\.xml$", Pattern.CASE_INSENSITIVE);
  // JS /.xml$/i and /.json$/i in getResourceAtPath: unescaped '.' (any char)
  private static final Pattern XML_KEY = Pattern.compile(".xml$", Pattern.CASE_INSENSITIVE);
  private static final Pattern JSON_KEY = Pattern.compile(".json$", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXCEL = Pattern.compile("<\\?mso-application progid=\"Excel\\.Sheet\"\\?>", Pattern.MULTILINE);

  private final Map<String, Object> packageJSON;
  private final List<String> paths;
  private final BiConsumer<String, String> log;
  private final boolean allowNonResources;
  private final boolean recursive;
  private final String fhirVersion;
  private final Set<String> registeredKeys = new HashSet<>();

  public DiskBasedVirtualPackage(Map<String, Object> packageJSON, List<String> paths) {
    this(packageJSON, paths, null, false, false, null);
  }

  public DiskBasedVirtualPackage(
      Map<String, Object> packageJSON, List<String> paths, BiConsumer<String, String> log, boolean allowNonResources,
      boolean recursive) {
    this(packageJSON, paths, log, allowNonResources, recursive, null);
  }

  /** @param fhirVersion the IG's FHIR version, which selects the model that reads XML files (null: R4) */
  public DiskBasedVirtualPackage(
      Map<String, Object> packageJSON, List<String> paths, BiConsumer<String, String> log, boolean allowNonResources,
      boolean recursive, String fhirVersion) {
    this.fhirVersion = fhirVersion;
    this.packageJSON = packageJSON;
    this.paths = paths == null ? List.of() : paths;
    this.log = log != null ? log : (l, m) -> {};
    this.allowNonResources = allowNonResources;
    this.recursive = recursive;
  }

  @Override
  public void registerResources(Register register) throws IOException {
    Map<String, Integer> spreadSheetCounts = new LinkedHashMap<>();
    Map<String, Integer> invalidFileCounts = new LinkedHashMap<>();
    // Since every OS may load paths in a different order, ensure consistency by sorting the paths
    List<String> filePaths = getFilePaths(paths, recursive);
    filePaths.sort(null); // JS default sort = UTF-16 code unit order = String.compareTo
    for (String filePath : filePaths) {
      try {
        String name = basename(filePath);
        String parent = dirname(filePath);
        if (JSON_NAME.matcher(name).find()) {
          register.register(filePath, getResourceAtPath(filePath), allowNonResources);
          registeredKeys.add(filePath);
        } else if (SPREADSHEET_NAME.matcher(name).find()) {
          spreadSheetCounts.merge(parent, 1, Integer::sum);
          log.accept("debug", "Skipped spreadsheet XML file: " + filePath);
        } else if (XML_NAME.matcher(name).find()) {
          String xml = Fs.readUtf8Strict(filePath);
          if (EXCEL.matcher(xml).find()) {
            spreadSheetCounts.merge(parent, 1, Integer::sum);
            log.accept("debug", "Skipped spreadsheet XML file: " + filePath);
            // FPL still tries to register it (fhir-tool fails with an unknown type, silently); the port skips it,
            // since core's parser would fail too and it is already reported as a skipped spreadsheet
            continue;
          }
          register.register(filePath, getResourceAtPath(filePath), allowNonResources);
          registeredKeys.add(filePath);
        } else {
          invalidFileCounts.merge(parent, 1, Integer::sum);
          log.accept("debug", "Skipped non-JSON / non-XML file: " + filePath);
        }
      } catch (XmlConversionException e) {
        // Not FHIR XML that core's parser for this FHIR version reads (instances of logical models, CDA documents,
        // resource types of another FHIR version, invalid FHIR XML). FPL skips unknown resource types silently and
        // reads the rest with fhir-tool's lenient R4 converter; the port says so (core-divergences.md C6).
        log.accept("warn", "Skipped predefined XML file " + filePath + ": the FHIR " + FhirXml.model(fhirVersion)
            + " XML parser could not read it (" + e.getCause().getMessage() + ")");
        log.accept("debug", BasePackageLoader.stack(e));
      } catch (Exception e) {
        log.accept("error", "Failed to register resource at path: " + filePath);
        log.accept("debug", BasePackageLoader.stack(e));
      }
    }
    spreadSheetCounts.forEach((folder, count) -> {
      if (count > 0) {
        log.accept("info", "Found " + count + " spreadsheet(s) in directory: " + folder + ". Spreadsheets are not "
            + "supported, so any resources in the spreadsheets will be ignored. To see the skipped files in the logs, "
            + "use debug logging.");
      }
    });
    invalidFileCounts.forEach((folder, count) -> {
      if (count > 0) {
        log.accept("info", "Found " + count + " non-JSON / non-XML file(s) in directory: " + folder + ". Only resource "
            + "files with JSON or XML extensions are supported. To see the skipped files in the logs, use debug "
            + "logging.");
      }
    });
  }

  @Override
  public Map<String, Object> getPackageJSON() {
    return packageJSON;
  }

  @Override
  public Object getResourceByKey(String key) {
    if (registeredKeys.contains(key)) {
      return getResourceAtPath(key);
    }
    throw new IllegalStateException("Unregistered resource key: " + key);
  }

  private Object getResourceAtPath(String key) {
    if (XML_KEY.matcher(key).find()) {
      String xml;
      try {
        xml = Fs.readUtf8Strict(key);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to get XML resource at path " + key);
      }
      try {
        return xmlConverter.xmlToObj(xml, fhirVersion);
      } catch (Exception e) {
        throw new XmlConversionException("Failed to convert XML resource at path " + key + ": " + e.getMessage(), e);
      }
    } else if (JSON_KEY.matcher(key).find()) {
      try {
        return Json.parseFile(readUtf8(key));
      } catch (Exception e) {
        throw new IllegalStateException("Failed to get JSON resource at path " + key);
      }
    }
    throw new IllegalStateException("Failed to find XML or JSON file at path " + key);
  }

  static String readUtf8(String path) throws IOException {
    // Buffer.toString('utf8'): malformed sequences become U+FFFD (as does new String(bytes, UTF_8))
    return Fs.readUtf8(path);
  }

  /** node path.resolve(p): absolute + normalized, no symlink resolution */
  static String resolve(String p) {
    return Path.of(p).toAbsolutePath().normalize().toString();
  }

  static String basename(String p) {
    int i = p.lastIndexOf(File.separatorChar);
    return i < 0 ? p : p.substring(i + 1);
  }

  static String dirname(String p) {
    int i = p.lastIndexOf(File.separatorChar);
    return i <= 0 ? (i == 0 ? File.separator : ".") : p.substring(0, i);
  }

  private static List<String> getFilePaths(List<String> paths, boolean recursive) throws IOException {
    Set<String> filePaths = new LinkedHashSet<>();
    for (String p : paths) {
      // fs.statSync follows symlinks (and throws when missing: FPL lets that propagate)
      if (Fs.isFile(p)) {
        filePaths.add(resolve(p));
      } else if (Fs.isDirectory(p)) {
        List<Path> entries = Fs.list(p);
        entries.sort(null); // as Files.list(...).sorted() before: Path order
        for (Path entry : entries) {
          // Dirent.isFile()/isDirectory() describe the entry itself (a symlink is neither)
          if (Fs.isRegularFileNoFollow(entry.toString())) {
            filePaths.add(resolve(entry.toString()));
          } else if (recursive && Fs.isDirectoryNoFollow(entry.toString())) {
            filePaths.addAll(getFilePaths(List.of(resolve(entry.toString())), recursive));
          }
        }
      } else if (!Fs.exists(p)) {
        throw new IOException("ENOENT: no such file or directory, stat '" + p + "'");
      }
    }
    return new ArrayList<>(filePaths);
  }
}
