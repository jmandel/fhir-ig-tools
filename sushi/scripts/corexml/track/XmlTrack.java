package org.hl7.fhir.common.js;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Harness only (scripts/corexml/track.sh): records how SUSHI uses resources it loaded from predefined XML files.
 * The XML converter is wrapped so that its result is a {@link Root}; the harness's patched copy of {@link Frozen}
 * keeps the label on the frozen copy and reports every property read here, and the patched FHIRDefinitions reports
 * every metadata (identity) lookup that resolves to an XML file. Output (-Dxmltrack.out): one TSV line per distinct
 * (kind, resource, property path, calling SUSHI code).
 */
public final class XmlTrack {
  private XmlTrack() {}

  /** a converted XML resource, labelled resourceType/id */
  public static final class Root extends JsObject {
    private static final long serialVersionUID = 1L;
    public final String label;

    public Root(String label, Map<String, Object> m) {
      super(m);
      this.label = label;
    }
  }

  private static final Set<String> SEEN = new HashSet<>();
  private static PrintWriter out;

  private static synchronized void emit(String kind, String label, String path) {
    String purpose = StackWalker.getInstance().walk(s -> s.map(f -> f.getClassName() + "." + f.getMethodName())
        .filter(n -> n.startsWith("org.hl7.fhir.sushi.") && !n.startsWith("org.hl7.fhir.sushi.fhirdefs.")
            && !n.startsWith("org.hl7.fhir.sushi.utils.MasterFisher.") && !n.startsWith("org.hl7.fhir.sushi.utils.FishingUtils."))
        .map(n -> n.substring("org.hl7.fhir.sushi.".length())).distinct().limit(3).collect(Collectors.joining(" < ")));
    String key = kind + "\t" + label + "\t" + path + "\t" + purpose;
    if (!SEEN.add(key)) return;
    try {
      if (out == null) {
        out = new PrintWriter(new FileOutputStream(System.getProperty("xmltrack.out"), true), true, StandardCharsets.UTF_8);
      }
      out.println(key);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /** a property read on a frozen XML-sourced object; track = label + U+0001 + property path */
  static void read(String track, String key) {
    int sep = track.indexOf('\u0001');
    String label = track.substring(0, sep);
    String path = track.substring(sep + 1) + "." + key;
    if (path.startsWith(".")) path = path.substring(1);
    String[] seg = path.split("\\.");
    if (seg.length > 3) path = seg[0] + "." + seg[1] + "." + seg[2] + ".*";
    emit("read", label, path);
  }

  /** a metadata lookup (FHIRDefinitions.convertInfoToMetadata) that resolved to a resource from an XML file */
  public static void meta(String resourceType, String id, String resourcePath) {
    if (resourcePath != null && resourcePath.toLowerCase().endsWith(".xml")) emit("meta", resourceType + "/" + id, "-");
  }
}
