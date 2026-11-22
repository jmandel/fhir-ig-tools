package org.hl7.fhir.sushi.utils;

import java.util.ArrayList;
import java.util.List;

// Node's path.posix (node 22), the subset SUSHI uses: join, resolve, normalize, relative, basename, dirname, parse.
/**
 * String-level path arithmetic with Node's exact results (SUSHI prints these paths in messages and uses them as
 * map keys, so java.nio's normalisation, which differs on trailing slashes, "." results and ".." handling, is not
 * used). POSIX only: SUSHI's reference runs are on Linux, where {@code path.sep} is "/". {@link #resolve} uses the
 * process working directory ({@code user.dir}) like {@code process.cwd()}.
 */
public final class NodePath {
  private NodePath() {}

  public static final String SEP = "/";

  /** Process working directory (process.cwd()). */
  public static String cwd() {
    return System.getProperty("user.dir");
  }

  // Resolves . and .. elements in a path with directory names (node's normalizeString, posix)
  private static String normalizeString(String path, boolean allowAboveRoot) {
    StringBuilder res = new StringBuilder();
    int lastSegmentLength = 0;
    int lastSlash = -1;
    int dots = 0;
    char code = 0;
    for (int i = 0; i <= path.length(); ++i) {
      if (i < path.length()) code = path.charAt(i);
      else if (code == '/') break;
      else code = '/';
      if (code == '/') {
        if (lastSlash == i - 1 || dots == 1) {
          // NOOP
        } else if (dots == 2) {
          if (res.length() < 2 || lastSegmentLength != 2 || res.charAt(res.length() - 1) != '.'
              || res.charAt(res.length() - 2) != '.') {
            if (res.length() > 2) {
              int lastSlashIndex = res.lastIndexOf("/");
              if (lastSlashIndex == -1) {
                res.setLength(0);
                lastSegmentLength = 0;
              } else {
                res.setLength(lastSlashIndex);
                lastSegmentLength = res.length() - 1 - res.lastIndexOf("/");
              }
              lastSlash = i;
              dots = 0;
              continue;
            } else if (res.length() != 0) {
              res.setLength(0);
              lastSegmentLength = 0;
              lastSlash = i;
              dots = 0;
              continue;
            }
          }
          if (allowAboveRoot) {
            res.append(res.length() > 0 ? "/.." : "..");
            lastSegmentLength = 2;
          }
        } else {
          if (res.length() > 0) res.append('/').append(path, lastSlash + 1, i);
          else res.append(path, lastSlash + 1, i);
          lastSegmentLength = i - lastSlash - 1;
        }
        lastSlash = i;
        dots = 0;
      } else if (code == '.' && dots != -1) {
        ++dots;
      } else {
        dots = -1;
      }
    }
    return res.toString();
  }

  public static boolean isAbsolute(String p) {
    return !p.isEmpty() && p.charAt(0) == '/';
  }

  public static String normalize(String path) {
    if (path.isEmpty()) return ".";
    boolean isAbsolute = path.charAt(0) == '/';
    boolean trailingSeparator = path.charAt(path.length() - 1) == '/';
    String p = normalizeString(path, !isAbsolute);
    if (p.isEmpty()) {
      if (isAbsolute) return "/";
      return trailingSeparator ? "./" : ".";
    }
    if (trailingSeparator) p += "/";
    return isAbsolute ? "/" + p : p;
  }

  public static String join(String... args) {
    if (args.length == 0) return ".";
    StringBuilder joined = null;
    for (String arg : args) {
      if (arg == null) throw new IllegalArgumentException(
          "The \"path\" argument must be of type string. Received undefined");
      if (!arg.isEmpty()) {
        if (joined == null) joined = new StringBuilder(arg);
        else joined.append('/').append(arg);
      }
    }
    if (joined == null) return ".";
    return normalize(joined.toString());
  }

  public static String resolve(String... args) {
    String resolvedPath = "";
    boolean resolvedAbsolute = false;
    for (int i = args.length - 1; i >= -1 && !resolvedAbsolute; i--) {
      String path = i >= 0 ? args[i] : cwd();
      if (path == null) throw new IllegalArgumentException(
          "The \"paths[" + i + "]\" argument must be of type string. Received undefined");
      if (path.isEmpty()) continue;
      resolvedPath = path + "/" + resolvedPath;
      resolvedAbsolute = path.charAt(0) == '/';
    }
    resolvedPath = normalizeString(resolvedPath, !resolvedAbsolute);
    if (resolvedAbsolute) return "/" + resolvedPath;
    return !resolvedPath.isEmpty() ? resolvedPath : ".";
  }

  public static String relative(String from, String to) {
    if (from.equals(to)) return "";
    from = resolve(from);
    to = resolve(to);
    if (from.equals(to)) return "";
    int fromStart = 1;
    int fromEnd = from.length();
    int fromLen = fromEnd - fromStart;
    int toStart = 1;
    int toLen = to.length() - toStart;
    int length = Math.min(fromLen, toLen);
    int lastCommonSep = -1;
    int i = 0;
    for (; i < length; i++) {
      char fromCode = from.charAt(fromStart + i);
      if (fromCode != to.charAt(toStart + i)) break;
      else if (fromCode == '/') lastCommonSep = i;
    }
    if (i == length) {
      if (toLen > length) {
        if (to.charAt(toStart + i) == '/') return to.substring(toStart + i + 1);
        if (i == 0) return to.substring(toStart + i);
      } else if (fromLen > length) {
        if (from.charAt(fromStart + i) == '/') lastCommonSep = i;
        else if (i == 0) lastCommonSep = 0;
      }
    }
    StringBuilder out = new StringBuilder();
    for (i = fromStart + lastCommonSep + 1; i <= fromEnd; ++i) {
      if (i == fromEnd || from.charAt(i) == '/') out.append(out.length() == 0 ? ".." : "/..");
    }
    return out + to.substring(toStart + lastCommonSep);
  }

  public static String dirname(String path) {
    if (path.isEmpty()) return ".";
    boolean hasRoot = path.charAt(0) == '/';
    int end = -1;
    boolean matchedSlash = true;
    for (int i = path.length() - 1; i >= 1; --i) {
      if (path.charAt(i) == '/') {
        if (!matchedSlash) {
          end = i;
          break;
        }
      } else {
        matchedSlash = false;
      }
    }
    if (end == -1) return hasRoot ? "/" : ".";
    if (hasRoot && end == 1) return "//";
    return path.substring(0, end);
  }

  public static String basename(String path) {
    int start = 0;
    int end = -1;
    boolean matchedSlash = true;
    for (int i = path.length() - 1; i >= 0; --i) {
      if (path.charAt(i) == '/') {
        if (!matchedSlash) {
          start = i + 1;
          break;
        }
      } else if (end == -1) {
        matchedSlash = false;
        end = i + 1;
      }
    }
    if (end == -1) return "";
    return path.substring(start, end);
  }

  /** path.extname. */
  public static String extname(String path) {
    int startDot = -1;
    int startPart = 0;
    int end = -1;
    boolean matchedSlash = true;
    int preDotState = 0;
    for (int i = path.length() - 1; i >= 0; --i) {
      char code = path.charAt(i);
      if (code == '/') {
        if (!matchedSlash) {
          startPart = i + 1;
          break;
        }
        continue;
      }
      if (end == -1) {
        matchedSlash = false;
        end = i + 1;
      }
      if (code == '.') {
        if (startDot == -1) startDot = i;
        else if (preDotState != 1) preDotState = 1;
      } else if (startDot != -1) {
        preDotState = -1;
      }
    }
    if (startDot == -1 || end == -1 || preDotState == 0
        || (preDotState == 1 && startDot == end - 1 && startDot == startPart + 1)) {
      return "";
    }
    return path.substring(startDot, end);
  }

  /** path.parse(p).name: the base name without its extension. */
  public static String parseName(String path) {
    String base = basename(path);
    String ext = extname(path);
    return base.substring(0, base.length() - ext.length());
  }

  /** Splits on "/" keeping empty parts (JS split). */
  static List<String> split(String s) {
    List<String> out = new ArrayList<>();
    int from = 0;
    for (int i; (i = s.indexOf('/', from)) >= 0; from = i + 1) out.add(s.substring(from, i));
    out.add(s.substring(from));
    return out;
  }
}
