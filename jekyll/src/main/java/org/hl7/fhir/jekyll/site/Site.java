package org.hl7.fhir.jekyll.site;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The parts of Jekyll's Site the IG Publisher actually exercises (see TRACKER.md "Key facts"):
 * configuration (jekyll configuration.rb defaults + _config.yml), reading (reader.rb +
 * entry_filter.rb: pages = files with YAML front matter, everything else static), site.data
 * (readers/data_reader.rb), page URLs/destinations (page.rb, permalink style "date"), writing, and
 * the Cleaner (cleaner.rb). No collections, posts, layouts-from-front-matter, Sass, or plugins.
 */
public final class Site {
  static final List<String> DEFAULT_EXCLUDES = List.of(".sass-cache", ".jekyll-cache", "gemfiles", "Gemfile",
      "Gemfile.lock", "node_modules", "vendor/bundle/", "vendor/cache/", "vendor/gems/", "vendor/ruby/");
  static final List<String> MARKDOWN_EXTS = List.of("markdown", "mkdown", "mkdn", "mkd", "md");
  /** utils.rb has_yaml_header? on the first line. */
  private static final Pattern YAML_HEADER_LINE = Pattern.compile("\\A---\\s*\\r?\\n", Pattern.UNIX_LINES);
  /** document.rb YAML_FRONT_MATTER_REGEXP (Ruby ^/$ are line anchors → MULTILINE; /m → DOTALL). */
  private static final Pattern FRONT_MATTER = Pattern.compile("\\A(---\\s*\\n.*?\\n?)^((---|\\.\\.\\.)\\s*$\\n?)",
      Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNIX_LINES);

  public final Path source;
  public final Path dest;
  public final Map<String, Object> config;
  public final Map<String, Object> data;
  public final List<Page> pages = new ArrayList<>();
  public final List<StaticFile> staticFiles = new ArrayList<>();
  private final List<String> exclude;
  private final List<String> include;
  private final List<String> keepFiles;

  /** A file with front matter: rendered through Liquid (and kramdown for Markdown extensions). */
  public static final class Page {
    public final String relativePath; // e.g. "en/index.html" (forward slashes)
    public final Path path;
    public final Map<String, Object> frontMatter;
    public final String content; // after the front matter, BOM stripped
    public final String url;
    public final String outputExt;
    public String output; // filled by the renderer
    public java.util.List<String> warnings = java.util.List.of(); // Liquid warnings while rendering

    Page(String relativePath, Path path, Map<String, Object> fm, String content, String url, String outputExt) {
      this.relativePath = relativePath;
      this.path = path;
      this.frontMatter = fm;
      this.content = content;
      this.url = url;
      this.outputExt = outputExt;
    }

    public String name() {
      int k = relativePath.lastIndexOf('/');
      return k < 0 ? relativePath : relativePath.substring(k + 1);
    }

    /** page.rb#dir: url if it ends with "/", else url's directory with trailing slash. */
    public String dir() {
      if (url.endsWith("/")) {
        return url;
      }
      int k = url.lastIndexOf('/');
      return k <= 0 ? "/" : url.substring(0, k + 1);
    }

    public boolean isMarkdown() {
      String ext = extOf(relativePath);
      return !ext.isEmpty() && MARKDOWN_EXTS.contains(ext.substring(1).toLowerCase(Locale.ROOT));
    }

    /** page.rb#destination. */
    public String destinationRelative() {
      String p = url;
      if (p.endsWith("/")) {
        p = p + "index";
      }
      if (!p.endsWith(outputExt)) {
        p = p + outputExt;
      }
      return p.substring(1);
    }
  }

  /** A file without front matter: copied verbatim (mtime preserved). */
  public static final class StaticFile {
    public final String relativePath;
    public final Path path;
    /** static_file.rb#url → destination: cleaned_relative_path + extname, URL.unescape_path'd (no leading "/"). */
    public final String destinationRelative;

    StaticFile(String relativePath, Path path) {
      this.relativePath = relativePath;
      this.path = path;
      this.destinationRelative = staticDestination(relativePath);
    }
  }

  /** Ruby File.extname (POSIX): leading dots ignored; from the last remaining "."; "foo." gives "."; else "". */
  static String rubyExtname(String name) {
    String base = name.substring(name.lastIndexOf('/') + 1);
    int i = 0;
    while (i < base.length() && base.charAt(i) == '.') {
      i++;
    }
    String rest = base.substring(i);
    int dot = rest.lastIndexOf('.');
    return dot < 0 ? "" : rest.substring(dot);
  }

  static String staticDestination(String relativePath) {
    String rel = "/" + relativePath;
    String ext = rubyExtname(rel);
    String cleaned = rel.substring(0, rel.length() - ext.length()).replaceAll("\\.*\\z", "");
    if (cleaned.endsWith("/")) {
      cleaned = cleaned.substring(0, cleaned.length() - 1);
    }
    return unescapePath(cleaned + ext).substring(1);
  }

  /** Jekyll::URL.unescape_path (Addressable::URI.unencode when the path contains "%"). */
  static String unescapePath(String p) {
    if (p.indexOf('%') < 0) {
      return p;
    }
    byte[] in = p.getBytes(StandardCharsets.UTF_8);
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(in.length);
    for (int i = 0; i < in.length; i++) {
      if (in[i] == '%' && i + 2 < in.length && Character.digit(in[i + 1], 16) >= 0 && Character.digit(in[i + 2], 16) >= 0) {
        out.write(Character.digit(in[i + 1], 16) * 16 + Character.digit(in[i + 2], 16));
        i += 2;
      } else {
        out.write(in[i]);
      }
    }
    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }

  /**
   * Static files to write, resolved as Jekyll's write order would: static_files sorted by
   * relative_path, later ones overwriting earlier at the same destination.
   */
  List<StaticFile> staticWriteSet() {
    List<StaticFile> sorted = new ArrayList<>(staticFiles);
    sorted.sort((a, b) -> Arrays.compare(("/" + a.relativePath).getBytes(StandardCharsets.UTF_8),
        ("/" + b.relativePath).getBytes(StandardCharsets.UTF_8)));
    Map<String, StaticFile> byDest = new LinkedHashMap<>();
    for (StaticFile f : sorted) {
      byDest.put(f.destinationRelative, f);
    }
    return new ArrayList<>(byDest.values());
  }

  /** Pages whose destination a static file also writes are overwritten by it (pages are written first). */
  Set<String> staticDestinations() {
    Set<String> out = new HashSet<>();
    for (StaticFile f : staticFiles) {
      out.add(f.destinationRelative);
    }
    return out;
  }

  public Site(Path source, Path dest) {
    this(source, dest, null, null);
  }

  /**
   * @param userConfig if non-null, used instead of reading _config.yml (tests)
   * @param dataOverride if non-null, used as site.data instead of reading _data (tests)
   */
  @SuppressWarnings("unchecked")
  public Site(Path source, Path dest, Map<String, Object> userConfig, Map<String, Object> dataOverride) {
    this.source = source.toAbsolutePath().normalize();
    this.dest = dest.toAbsolutePath().normalize();
    Map<String, Object> user = userConfig != null ? userConfig : new LinkedHashMap<>();
    Path cfgFile = this.source.resolve("_config.yml");
    if (userConfig == null && Files.isRegularFile(cfgFile)) {
      Object y = org.hl7.fhir.jekyll.yaml.Yaml.loadFile(cfgFile); // SafeYAML.load_file(file) || {}
      if (y instanceof Map) {
        user = (Map<String, Object>) y;
      } else if (y != null && y != Boolean.FALSE) {
        throw new IllegalArgumentException("Configuration file: (INVALID) " + cfgFile);
      }
    }
    Map<String, Object> cfg = JekyllConfig.build(user, this.source.toString(), this.dest.toString());
    this.config = cfg;
    this.exclude = new ArrayList<>(stringList(cfg.get("exclude")));
    this.include = stringList(cfg.get("include"));
    this.keepFiles = stringList(cfg.get("keep_files"));
    this.data = dataOverride != null ? dataOverride
        : DataFiles.read(this.source.resolve(String.valueOf(cfg.getOrDefault("data_dir", "_data"))));
  }

  @SuppressWarnings("unchecked")
  private static List<String> stringList(Object o) {
    List<String> out = new ArrayList<>();
    if (o instanceof List) {
      for (Object x : (List<Object>) o) {
        out.add(String.valueOf(x));
      }
    } else if (o != null) {
      out.add(String.valueOf(o));
    }
    return out;
  }

  public static String readUtf8(Path p) {
    try {
      String s = Files.readString(p, StandardCharsets.UTF_8);
      return s.startsWith("﻿") ? s.substring(1) : s;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String extOf(String name) {
    int slash = name.lastIndexOf('/');
    int dot = name.lastIndexOf('.');
    return dot > slash + 1 ? name.substring(dot) : "";
  }

  // ---------------------------------------------------------------- reading (reader.rb)

  public void read() {
    // 1) walk in Jekyll's order (cheap: names only), 2) classify/read files in parallel,
    // 3) assemble pages/static files back in walk order (deterministic).
    List<String> files = new ArrayList<>();
    listDirectory("", files);
    Object[] results = new Object[files.size()];
    List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
    int chunk = 64;
    for (int start = 0; start < files.size(); start += chunk) {
      final int s0 = start, s1 = Math.min(files.size(), start + chunk);
      jobs.add(() -> {
        for (int i = s0; i < s1; i++) {
          String rel = files.get(i);
          Path p = source.resolve(rel);
          results[i] = hasYamlHeader(p) ? readPage(rel, p) : new StaticFile(rel, p);
        }
        return null;
      });
    }
    try {
      runParallel(jobs, ioThreads());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    for (Object r : results) {
      if (r instanceof Page) {
        pages.add((Page) r);
      } else {
        staticFiles.add((StaticFile) r);
      }
    }
  }

  private void listDirectory(String dir, List<String> files) {
    Path base = dir.isEmpty() ? source : source.resolve(dir);
    String[] entries;
    try (Stream<Path> s = Files.list(base)) {
      entries = s.map(p -> p.getFileName().toString()).sorted().toArray(String[]::new);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    for (String e : filter(dir, Arrays.asList(entries))) {
      String rel = dir.isEmpty() ? e : dir + "/" + e;
      Path p = source.resolve(rel);
      if (p.equals(dest)) {
        continue;
      }
      if (Files.isDirectory(p)) {
        listDirectory(rel, files);
      } else {
        files.add(rel);
      }
    }
  }

  /** Sequential reader (kept for reference/testing; read() is the parallel equivalent). */
  void readSequential() {
    readDirectory("");
  }

  private void readDirectory(String dir) {
    Path base = dir.isEmpty() ? source : source.resolve(dir);
    String[] entries;
    try (Stream<Path> s = Files.list(base)) {
      entries = s.map(p -> p.getFileName().toString()).sorted().toArray(String[]::new);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    for (String e : filter(dir, Arrays.asList(entries))) {
      String rel = dir.isEmpty() ? e : dir + "/" + e;
      Path p = source.resolve(rel);
      if (p.equals(dest) || dest.startsWith(p) && Files.isDirectory(p) && p.equals(dest)) {
        continue;
      }
      if (Files.isDirectory(p)) {
        if (!p.equals(dest)) {
          readDirectory(rel);
        }
      } else if (hasYamlHeader(p)) {
        pages.add(readPage(rel, p));
      } else {
        staticFiles.add(new StaticFile(rel, p));
      }
    }
  }

  /** entry_filter.rb#filter. */
  List<String> filter(String baseDir, List<String> entries) {
    List<String> out = new ArrayList<>();
    for (String e : entries) {
      if (e.endsWith(".")) {
        continue;
      }
      String rel = baseDir.isEmpty() ? e : baseDir + "/" + e;
      boolean included = globInclude(include, e) || globInclude(include, rel);
      if (globInclude(minus(exclude, include), rel) && !included) {
        continue;
      }
      if (included) {
        out.add(e);
        continue;
      }
      char c = e.charAt(0);
      if (c == '.' || c == '_' || c == '#' || c == '~' || e.endsWith("~")) {
        continue;
      }
      out.add(e);
    }
    return out;
  }

  private static List<String> minus(List<String> a, List<String> b) {
    List<String> out = new ArrayList<>(a);
    out.removeAll(b);
    return out;
  }

  /** entry_filter.rb#glob_include? for String patterns (fnmatch, prefix, or dir-with-slash). */
  private boolean globInclude(List<String> patterns, String entry) {
    // Jekyll compares forward-slash paths on every OS (File.join); normalize Windows separators
    String entryWithSource = source.resolve(entry).toString().replace('\\', '/');
    boolean isDir = Files.isDirectory(source.resolve(entry));
    for (String pattern : patterns) {
      String patternWithSource = source.resolve(pattern).toString().replace('\\', '/') + (pattern.endsWith("/") ? "/" : "");
      if (fnmatch(patternWithSource, entryWithSource) || entryWithSource.startsWith(patternWithSource)
          || (isDir && patternWithSource.equals(entryWithSource + "/"))) {
        return true;
      }
    }
    return false;
  }

  /** Ruby File.fnmatch? without flags: '*' and '?' match any char including '/', [..] classes. */
  static boolean fnmatch(String pattern, String s) {
    StringBuilder re = new StringBuilder();
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      switch (c) {
        case '*': re.append(".*"); break;
        case '?': re.append('.'); break;
        case '[': {
          int j = pattern.indexOf(']', i + 1);
          if (j < 0) {
            re.append("\\[");
          } else {
            re.append('[').append(pattern, i + 1, j).append(']');
            i = j;
          }
          break;
        }
        case '\\':
          if (i + 1 < pattern.length()) {
            re.append(Pattern.quote(String.valueOf(pattern.charAt(++i))));
          }
          break;
        default: re.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return Pattern.compile(re.toString(), Pattern.DOTALL | Pattern.UNIX_LINES).matcher(s).matches();
  }

  static boolean hasYamlHeader(Path p) {
    try (var in = Files.newInputStream(p)) {
      byte[] head = in.readNBytes(512);
      String s = new String(head, StandardCharsets.UTF_8);
      int nl = s.indexOf('\n');
      String firstLine = nl < 0 ? s : s.substring(0, nl + 1);
      return nl >= 0 && YAML_HEADER_LINE.matcher(firstLine).lookingAt();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private Page readPage(String rel, Path p) {
    String text = readUtf8(p);
    Map<String, Object> fm = new LinkedHashMap<>();
    String content = text;
    Matcher m = FRONT_MATTER.matcher(text);
    if (m.find()) {
      content = text.substring(m.end());
      Object y = org.hl7.fhir.jekyll.yaml.Yaml.load(m.group(1)); // SafeYAML.load(front matter)
      if (y instanceof Map) {
        fm.putAll((Map<String, Object>) y);
      } else if (y != null && y != Boolean.FALSE) {
        throw new IllegalArgumentException("Invalid YAML front matter in " + rel);
      }
    }
    String ext = extOf(rel);
    boolean md = !ext.isEmpty() && MARKDOWN_EXTS.contains(ext.substring(1).toLowerCase(Locale.ROOT));
    String outputExt = md ? ".html" : ext;
    return new Page(rel, p, fm, content, pageUrl(rel, outputExt, fm), outputExt);
  }

  /** page.rb#url with the default permalink style ("date"): "/:path/:basename:output_ext". */
  static String pageUrl(String rel, String outputExt, Map<String, Object> fm) {
    Object permalink = fm.get("permalink");
    if (permalink != null) {
      String pl = String.valueOf(permalink);
      return pl.startsWith("/") ? pl : "/" + pl;
    }
    int slash = rel.lastIndexOf('/');
    String dir = slash < 0 ? "" : rel.substring(0, slash);
    String name = slash < 0 ? rel : rel.substring(slash + 1);
    String ext = extOf(name);
    String basename = ext.isEmpty() ? name : name.substring(0, name.length() - ext.length());
    boolean html = outputExt.equals(".html") || outputExt.equals(".xhtml") || outputExt.equals(".htm");
    String prefix = "/" + (dir.isEmpty() ? "" : dir + "/");
    if (!html) {
      return prefix + basename + outputExt;
    }
    if (basename.equals("index")) {
      return prefix;
    }
    return prefix + basename + outputExt;
  }

  // ---------------------------------------------------------------- writing + cleaning

  /**
   * Cleaner first (cleaner.rb: remove destination files this build won't produce), then create all
   * output directories, so pages can be written as soon as each is rendered (writePage).
   */
  public void prepareOutput() throws IOException {
    Set<Path> newFiles = new HashSet<>();
    java.util.Set<Path> dirs = new java.util.TreeSet<>();
    for (Page p : pages) {
      Path out = dest.resolve(p.destinationRelative());
      newFiles.add(out);
      dirs.add(out.getParent());
    }
    for (StaticFile s : staticFiles) {
      Path out = dest.resolve(s.destinationRelative);
      newFiles.add(out);
      dirs.add(out.getParent());
    }
    staticDests = staticDestinations();
    cleanup(newFiles);
    for (Path d : dirs) {
      Files.createDirectories(d);
    }
  }

  /** Writes one rendered page and releases its output string (streaming; bounds memory). */
  private Set<String> staticDests = Set.of();

  public void writePage(Page p) throws IOException {
    if (staticDests.contains(p.destinationRelative())) { // a static file overwrites it (Jekyll writes pages first)
      p.output = null;
      return;
    }
    Files.writeString(dest.resolve(p.destinationRelative()), p.output == null ? "" : p.output, StandardCharsets.UTF_8);
    p.output = null;
  }

  /** Copies static files (mtime preserved), in parallel. */
  public void writeStatics() throws IOException {
    List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
    for (StaticFile s : staticWriteSet()) {
      jobs.add(() -> {
        Files.copy(s.path, dest.resolve(s.destinationRelative), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return null;
      });
    }
    runParallel(jobs, ioThreads());
  }

  /**
   * Static files that appeared in the source after {@link #read} (written while pages rendered, e.g.
   * by a lazy include provider): copied like the others and added to {@link #staticFiles}. Files with
   * front matter are not pages this late, so they are left alone. Returns the new relative paths.
   */
  public List<String> copyLateStatics() throws IOException {
    Set<String> known = new HashSet<>();
    pages.forEach(p -> known.add(p.relativePath));
    staticFiles.forEach(s -> known.add(s.relativePath));
    List<String> files = new ArrayList<>();
    listDirectory("", files);
    List<String> late = new ArrayList<>();
    for (String rel : files) {
      Path p = source.resolve(rel);
      if (known.contains(rel) || hasYamlHeader(p)) {
        continue;
      }
      StaticFile s = new StaticFile(rel, p);
      Path out = dest.resolve(s.destinationRelative);
      Files.createDirectories(out.getParent());
      Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
      staticFiles.add(s);
      late.add(rel);
    }
    return late;
  }

  /** Non-streaming equivalent (tests / callers that render first): prepare, write all pages, copy statics. */
  public void write() throws IOException {
    prepareOutput();
    for (Page p : pages) {
      if (p.output != null) {
        writePage(p);
      }
    }
    writeStatics();
  }

  static int ioThreads() {
    String v = System.getenv("SHIM_IO_THREADS");
    return v != null ? Integer.parseInt(v) : Math.max(4, Math.min(32, Runtime.getRuntime().availableProcessors() * 2));
  }

  static void runParallel(List<java.util.concurrent.Callable<Void>> jobs, int threads) throws IOException {
    java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(threads);
    try {
      for (java.util.concurrent.Future<Void> f : ex.invokeAll(jobs)) {
        try {
          f.get();
        } catch (java.util.concurrent.ExecutionException e) {
          Throwable c = e.getCause();
          if (c instanceof IOException) {
            throw (IOException) c;
          }
          if (c instanceof RuntimeException) {
            throw (RuntimeException) c;
          }
          throw new IOException(c);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    } finally {
      ex.shutdownNow();
    }
  }

  private void cleanup(Set<Path> newFiles) throws IOException {
    if (!Files.isDirectory(dest)) {
      return;
    }
    Set<Path> newDirs = new HashSet<>();
    for (Path f : newFiles) {
      for (Path d = f.getParent(); d != null && !d.equals(dest) && d.startsWith(dest); d = d.getParent()) {
        newDirs.add(d);
      }
    }
    Set<Path> keepDirs = new HashSet<>();
    for (String k : keepFiles) {
      for (Path d = dest.resolve(k).getParent(); d != null && !d.equals(dest) && d.startsWith(dest); d = d.getParent()) {
        keepDirs.add(d);
      }
    }
    List<Path> obsolete = new ArrayList<>();
    Files.walkFileTree(dest, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
        if (!d.equals(dest)) {
          check(d);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
        check(f);
        return FileVisitResult.CONTINUE;
      }

      private void check(Path f) {
        String rel = dest.relativize(f).toString().replace('\\', '/');
        for (String k : keepFiles) {
          if (rel.startsWith(k)) {
            return;
          }
        }
        if (keepDirs.contains(f)) {
          return;
        }
        boolean isNewDirAsFile = newDirs.contains(f) && Files.isRegularFile(f);
        if ((!newFiles.contains(f) && !newDirs.contains(f)) || isNewDirAsFile) {
          obsolete.add(f);
        }
      }
    });
    for (Path f : obsolete) {
      deleteRecursively(f);
    }
  }

  private static void deleteRecursively(Path p) throws IOException {
    if (!Files.exists(p)) {
      return;
    }
    if (Files.isDirectory(p)) {
      try (Stream<Path> s = Files.walk(p)) {
        for (Path q : s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
          Files.deleteIfExists(q);
        }
      }
    } else {
      Files.deleteIfExists(p);
    }
  }
}
