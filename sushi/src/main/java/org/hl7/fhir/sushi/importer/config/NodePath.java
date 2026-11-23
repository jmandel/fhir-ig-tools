package org.hl7.fhir.sushi.importer.config;

import java.util.ArrayList;
import java.util.List;

/** Node's {@code path.join} (POSIX), for paths that appear in log messages. */
final class NodePath {
  private NodePath() {}

  static String join(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (p.isEmpty()) continue;
      if (sb.length() > 0) sb.append('/');
      sb.append(p);
    }
    return sb.length() == 0 ? "." : normalize(sb.toString());
  }

  /** path.posix.normalize */
  static String normalize(String path) {
    if (path.isEmpty()) return ".";
    boolean absolute = path.startsWith("/");
    boolean trailing = path.endsWith("/");
    List<String> out = new ArrayList<>();
    for (String seg : path.split("/")) {
      if (seg.isEmpty() || seg.equals(".")) continue;
      if (seg.equals("..")) {
        if (!out.isEmpty() && !out.get(out.size() - 1).equals("..")) out.remove(out.size() - 1);
        else if (!absolute) out.add("..");
      } else {
        out.add(seg);
      }
    }
    String r = String.join("/", out);
    if (r.isEmpty() && !absolute) r = ".";
    if (trailing && !r.isEmpty()) r += "/";
    return absolute ? "/" + r : r;
  }

  /** path.extname */
  static String extname(String path) {
    String base = path.substring(path.lastIndexOf('/') + 1);
    int dot = base.lastIndexOf('.');
    return dot <= 0 ? "" : base.substring(dot);
  }

  /** path.basename */
  static String basename(String path) {
    String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    return p.substring(p.lastIndexOf('/') + 1);
  }
}
