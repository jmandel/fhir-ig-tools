package org.hl7.fhir.sushi.fhirdefs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirdefs.FindResourceInfoOptions.SortBy;
import org.hl7.fhir.sushi.utils.FHIRVersionUtils;
import org.hl7.fhir.sushi.utils.Fishable;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.Metadata;

// port of sushi/src/fhirdefs/FHIRDefinitions.ts (SUSHI 3.21.1)
/**
 * External FHIR definitions: core + dependency packages + virtual packages (R5-for-R4 definitions, predefined
 * resources from input/). Lookups go through the package DB (FPL's query semantics, {@link InMemoryPackageDB}) with
 * SUSHI's DEFAULT_SORT: by type in FISHING_ORDER, then most recently loaded first. Which packages and versions get
 * loaded, and the package files themselves, come from org.hl7.fhir.core's package layer as is (D1a).
 */
public class FHIRDefinitions extends BasePackageLoader implements Fishable {
  // the special package (name, version) that holds predefined resources (TS: ig/predefinedResources.ts)
  public static final String PREDEFINED_PACKAGE_NAME = "sushi-local";
  public static final String PREDEFINED_PACKAGE_VERSION = "LOCAL";

  static final List<String> FISHING_ORDER = List.of(
      FishableType.Resource.name(),
      FishableType.Logical.name(),
      FishableType.Type.name(),
      FishableType.Profile.name(),
      FishableType.Extension.name(),
      FishableType.ValueSet.name(),
      FishableType.CodeSystem.name());

  static final List<SortBy> DEFAULT_SORT =
      List.of(FindResourceInfoOptions.byType(FISHING_ORDER), FindResourceInfoOptions.byLoadOrder(false));

  private static final Pattern XVER_EXTENSION_REGEX =
      Pattern.compile("^http://hl7\\.org/fhir/(\\d+\\.\\d+)/StructureDefinition/extension-[^./]+\\..+$");

  private BiPredicate<String, String> fplLogInterceptor;

  /** Test/embedding overrides (TS constructor `override` argument); null fields = defaults. */
  public static class Override {
    public PackageDB packageDB;
    public PackageProvider packageProvider;
    public Integer resourceCacheSize;
  }

  /** Defaults: in-memory DB, ~/.fhir/packages through core's FilesystemPackageCacheManager. */
  public FHIRDefinitions() {
    this(null);
  }

  public FHIRDefinitions(Override o) {
    super(
        o != null && o.packageDB != null ? o.packageDB : new InMemoryPackageDB(),
        o != null && o.packageProvider != null ? o.packageProvider : CorePackageProvider.userCache(),
        null,
        // Analysis of 500 projects shows most only need a cache of 100. Double it for the others.
        o != null && o.resourceCacheSize != null ? o.resourceCacheSize : 200);
    // Use the same logger as SUSHI uses
    this.log = (level, message) -> {
      // if there is an interceptor, invoke it and suppress the log if appropriate
      if (fplLogInterceptor != null && !fplLogInterceptor.test(level, message)) {
        return;
      }
      FshLogger.logger.logMessage(level, message);
    };
  }

  /** TS createFHIRDefinitions(override?) (async there). */
  public static FHIRDefinitions createFHIRDefinitions() {
    return createFHIRDefinitions(null);
  }

  public static FHIRDefinitions createFHIRDefinitions(Override override) {
    FHIRDefinitions defs = new FHIRDefinitions(override);
    defs.initialize();
    return defs;
  }

  /** TS initialize(): initializes the SQL.js DB; nothing to do for the in-memory DB. */
  public void initialize() {}

  /**
   * An interceptor that can suppress FPL log messages based on level or message. This is primarily used to suppress
   * error logs when loading automatic dependencies.
   *
   * @param interceptor (level, message) -> true to continue logging, false to suppress; null to unset
   */
  public void setFHIRPackageLoaderLogInterceptor(BiPredicate<String, String> interceptor) {
    this.fplLogInterceptor = interceptor;
  }

  public List<Object> allPredefinedResources() {
    // Return in FIFO order to match previous SUSHI behavior
    return findResourceJSONs("*", new FindResourceInfoOptions(null, PREDEFINED_PACKAGE_NAME,
        List.of(FindResourceInfoOptions.byLoadOrder(true)), null));
  }

  public Object fishForPredefinedResource(String item, FishableType... types) {
    return findResourceJSON(item, new FindResourceInfoOptions(normalizeTypes(types), PREDEFINED_PACKAGE_NAME,
        DEFAULT_SORT, null));
  }

  public Metadata fishForPredefinedResourceMetadata(String item, FishableType... types) {
    ResourceInfo info = findResourceInfo(item, new FindResourceInfoOptions(normalizeTypes(types),
        PREDEFINED_PACKAGE_NAME, DEFAULT_SORT, null));
    return convertInfoToMetadata(info);
  }

  public List<Metadata> fishForPredefinedResourceMetadatas(String item, FishableType... types) {
    List<ResourceInfo> infos = findResourceInfos(item, new FindResourceInfoOptions(normalizeTypes(types),
        PREDEFINED_PACKAGE_NAME, DEFAULT_SORT, null));
    return infos.stream().map(FHIRDefinitions::convertInfoToMetadata).toList();
  }

  @java.lang.Override
  public Object fishForFHIR(String item, FishableType... types) {
    Object def = findResourceJSON(item, new FindResourceInfoOptions(normalizeTypes(types), null, DEFAULT_SORT, null));
    if (def != null) {
      return def;
    }
    // If it's a cross-version extension, attempt to fix it and/or provide guidance re: xver packages
    if (item != null && XVER_EXTENSION_REGEX.matcher(item).find() && hasExtension(types)) {
      String newURL = fixXverURL(item);
      if (!newURL.equals(item)) {
        // We corrected the URL, so try fishing again
        return fishForFHIR(newURL, FishableType.Extension);
      }
      logXverExtensionDependencyError(item);
    }
    return null;
  }

  @java.lang.Override
  public Metadata fishForMetadata(String item, FishableType... types) {
    ResourceInfo info = findResourceInfo(item, new FindResourceInfoOptions(normalizeTypes(types), null, DEFAULT_SORT,
        null));
    if (info != null) {
      return convertInfoToMetadata(info);
    }
    if (item != null && XVER_EXTENSION_REGEX.matcher(item).find() && hasExtension(types)) {
      String newURL = fixXverURL(item);
      if (!newURL.equals(item)) {
        return fishForMetadata(newURL, FishableType.Extension);
      }
      logXverExtensionDependencyError(item);
    }
    return null;
  }

  @java.lang.Override
  public List<Metadata> fishForMetadatas(String item, FishableType... types) {
    List<ResourceInfo> infos = findResourceInfos(item, new FindResourceInfoOptions(normalizeTypes(types), null,
        DEFAULT_SORT, null));
    if (!infos.isEmpty()) {
      return new ArrayList<>(infos.stream().map(FHIRDefinitions::convertInfoToMetadata).toList());
    }
    if (item != null && XVER_EXTENSION_REGEX.matcher(item).find() && hasExtension(types)) {
      String newURL = fixXverURL(item);
      if (!newURL.equals(item)) {
        return fishForMetadatas(newURL, FishableType.Extension);
      }
      logXverExtensionDependencyError(item);
    }
    return new ArrayList<>();
  }

  private static boolean hasExtension(FishableType[] types) {
    return types != null && Arrays.asList(types).contains(FishableType.Extension);
  }

  private void logXverExtensionDependencyError(String url) {
    Matcher match = XVER_EXTENSION_REGEX.matcher(decodeURI(url));
    // TS destructures the match result; a decoded URL that no longer matches throws a TypeError there
    if (!match.find()) {
      throw new IllegalStateException("match is not iterable");
    }
    String version = match.group(1);
    String source = xverVersionToReleaseTag(version);
    Object sd = fishForFHIR("StructureDefinition", FishableType.Resource);
    Object fhirVersion = sd instanceof Map<?, ?> m ? m.get("fhirVersion") : null;
    FHIRVersionUtils.FHIRVersionInfo vi =
        FHIRVersionUtils.getFHIRVersionInfo(fhirVersion == null ? null : String.valueOf(fhirVersion));
    String target = vi == null || vi.name() == null ? null : vi.name().replaceFirst("D?STU", "r").toLowerCase();
    String xverPackage = "hl7.fhir.uv.xver-" + source + "." + target;
    List<PackageInfo> xverPackageInfos = findPackageInfos(xverPackage);
    if (!xverPackageInfos.isEmpty()) {
      FshLogger.logger.error("The extension " + url + " was not found in the extension package " + xverPackage + ". "
          + "Please check the xver package documentation to ensure you are using the correct URL.\n"
          + "  See: https://hl7.org/fhir/uv/xver-" + source + "." + target + "/" + xverPackageInfos.get(0).version + "/");
    } else {
      FshLogger.logger.error("The extension " + url + " requires the cross-version extension package " + xverPackage
          + " to be declared in your sushi-config.yaml file.\n"
          + "  See: https://confluence.hl7.org/spaces/FHIRI/pages/413256623/FAQs");
    }
  }

  /** JS decodeURI: decodes %XX escapes (UTF-8), keeping escapes of the reserved characters ;/?:@&=+$,# */
  static String decodeURI(String s) {
    StringBuilder out = new StringBuilder();
    int k = 0, len = s.length();
    while (k < len) {
      char c = s.charAt(k);
      if (c != '%') {
        out.append(c);
        k++;
        continue;
      }
      int start = k;
      int b = hexByte(s, k);
      k += 3;
      if (b < 0x80) {
        char ch = (char) b;
        if (";/?:@&=+$,#".indexOf(ch) >= 0) out.append(s, start, k);
        else out.append(ch);
        continue;
      }
      int n = (b & 0xE0) == 0xC0 ? 2 : (b & 0xF0) == 0xE0 ? 3 : (b & 0xF8) == 0xF0 ? 4 : 0;
      if (n == 0) throw new IllegalArgumentException("URI malformed");
      byte[] bytes = new byte[n];
      bytes[0] = (byte) b;
      for (int j = 1; j < n; j++) {
        if (k >= len || s.charAt(k) != '%') throw new IllegalArgumentException("URI malformed");
        int bb = hexByte(s, k);
        if ((bb & 0xC0) != 0x80) throw new IllegalArgumentException("URI malformed");
        bytes[j] = (byte) bb;
        k += 3;
      }
      try {
        out.append(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)));
      } catch (java.nio.charset.CharacterCodingException e) {
        throw new IllegalArgumentException("URI malformed");
      }
    }
    return out.toString();
  }

  private static int hexByte(String s, int k) {
    if (k + 2 >= s.length()) throw new IllegalArgumentException("URI malformed");
    int hi = Character.digit(s.charAt(k + 1), 16), lo = Character.digit(s.charAt(k + 2), 16);
    if (hi < 0 || lo < 0) throw new IllegalArgumentException("URI malformed");
    return hi * 16 + lo;
  }

  /** Instance is like a wildcard, allowing anything -- so treat it like no types are passed in at all */
  static List<String> normalizeTypes(FishableType[] types) {
    if (types == null) return null;
    for (FishableType t : types) {
      if (t == FishableType.Instance) return null;
    }
    List<String> res = new ArrayList<>(types.length);
    for (FishableType t : types) res.add(t.name());
    return res;
  }

  static Metadata convertInfoToMetadata(ResourceInfo info) {
    if (info == null) return null;
    Metadata m = new Metadata();
    m.id = emptyToNull(info.id);
    m.name = emptyToNull(info.name);
    m.sdType = emptyToNull(info.sdType);
    m.url = emptyToNull(info.url);
    m.parent = emptyToNull(info.sdBaseDefinition);
    m.imposeProfiles = info.sdImposeProfiles; // `|| undefined`: an array is always truthy
    m._abstract = info.sdAbstract;
    m.version = emptyToNull(info.version);
    m.resourceType = emptyToNull(info.resourceType);
    m.canBeTarget = logicalCharacteristic(info, "can-be-target");
    m.canBind = logicalCharacteristic(info, "can-bind");
    m.resourcePath = emptyToNull(info.resourcePath);
    return m;
  }

  private static String emptyToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }

  private static Boolean logicalCharacteristic(ResourceInfo info, String characteristic) {
    // return true or false for logicals, otherwise leave it undefined
    if ("logical".equals(info.sdKind)) {
      return info.sdCharacteristics != null && info.sdCharacteristics.contains(characteristic);
    }
    return null;
  }

  private static final Pattern XVER_CHOICE = Pattern.compile("^(.+)(\\[x\\]|%5Bx%5D)$", Pattern.DOTALL);

  static String fixXverURL(String url) {
    Matcher match = XVER_CHOICE.matcher(url);
    if (match.find()) {
      String newURL = match.group(1);
      FshLogger.logger.warn("Cross-version extensions for choice elements should omit the [x] suffix.\n"
          + "  Found URL:     " + url + "\n"
          + "  Corrected URL: " + newURL + "\n"
          + "  SUSHI will use the corrected URL, but authors should fix the URL in their FSH source.");
      return newURL;
    }
    return url;
  }

  static String xverVersionToReleaseTag(String xverVersion) {
    return switch (xverVersion) {
      case "1.0" -> "r2";
      case "4.3" -> "r4b";
      default -> {
        Matcher m = Pattern.compile("^(\\d+)").matcher(xverVersion);
        m.find();
        yield "r" + m.group(1);
      }
    };
  }

}
