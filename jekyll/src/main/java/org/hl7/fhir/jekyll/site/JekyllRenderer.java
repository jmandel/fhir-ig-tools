package org.hl7.fhir.jekyll.site;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.hl7.fhir.jekyll.kramdown.Kramdown;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Environment;
import org.hl7.fhir.jekyll.liquid.ErrorMode;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseOptions;
import org.hl7.fhir.jekyll.liquid.RubyTime;
import org.hl7.fhir.jekyll.liquid.Template;

/**
 * Jekyll's page rendering (renderer.rb#render_document + liquid_renderer.rb + the unified payload)
 * for the subset IG builds use: Liquid with Jekyll's tags/filters in `warn` error mode, render!,
 * then kramdown for Markdown pages, no layouts.
 */
public final class JekyllRenderer implements PageRenderer {
  Site site;
  Environment env;
  Map<String, Object> kramdownConfig;
  Object siteDrop;
  /** Located include partials by name (shared, like Jekyll's site.inclusions); each slot resolves once. */
  final Map<String, JekyllTags.IncludeSlot> inclusionsByName = new ConcurrentHashMap<>();
  /** Optional in-memory `_includes` (consulted before the disk); null = disk only, as Jekyll. */
  org.hl7.fhir.jekyll.IncludeProvider includeProvider;
  final java.util.concurrent.atomic.AtomicInteger includesFromProvider = new java.util.concurrent.atomic.AtomicInteger();
  private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());

  /** Warnings for the page being rendered on this thread (Main prints them in page order). */
  static final ThreadLocal<List<String>> CURRENT_PAGE_WARNINGS = new ThreadLocal<>();

  static final String REG_SITE = "site";
  static final String REG_PAGE = "page";
  static final String REG_RENDERER = "jekyll_renderer";

  @Override
  @SuppressWarnings("unchecked")
  public void prepare(Site site) {
    this.site = site;
    Object k = site.config.get("kramdown");
    this.kramdownConfig = k instanceof Map ? (Map<String, Object>) k : Map.of();
    this.env = Environment.standard().copy();
    env.registerTag("include", JekyllTags.IncludeTag::new);
    env.registerTag("include_relative", JekyllTags.IncludeRelativeTag::new);
    env.registerTag("highlight", JekyllTags.HighlightBlock::new);
    JekyllFilters.register(env, this);
    this.siteDrop = buildSiteDrop(site);
    readLiquidConfig(site);
  }

  /** SiteDrop: config as fallback data, plus data/time and lazily computed pages/static_files/... */
  private Object buildSiteDrop(Site site) {
    Map<String, Object> s = new LinkedHashMap<>(site.config);
    s.put("data", site.data);
    s.put("time", RubyTime.ofInstant(Instant.now()));
    s.put("posts", new ArrayList<>());
    s.put("documents", new ArrayList<>());
    s.put("collections", new ArrayList<>());
    s.put("categories", new LinkedHashMap<>());
    s.put("tags", new LinkedHashMap<>());
    s.put("related_posts", null);
    Map<String, java.util.function.Supplier<Object>> lazy = new LinkedHashMap<>();
    // reader.rb#sort_files!: site.pages.sort_by!(&:name); site.static_files.sort_by!(&:relative_path)
    List<Site.Page> byName = new ArrayList<>(site.pages);
    RubySort.msort(byName, (a, b) -> java.util.Arrays.compare(a.name().getBytes(java.nio.charset.StandardCharsets.UTF_8),
        b.name().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    List<Site.StaticFile> byPath = new ArrayList<>(site.staticFiles);
    RubySort.msort(byPath, (a, b) -> java.util.Arrays.compare(("/" + a.relativePath).getBytes(java.nio.charset.StandardCharsets.UTF_8),
        ("/" + b.relativePath).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    lazy.put("pages", () -> {
      List<Object> pages = new ArrayList<>();
      for (Site.Page p : byName) {
        pages.add(pageDrop(p));
      }
      return pages;
    });
    lazy.put("html_pages", () -> {
      List<Object> htmlPages = new ArrayList<>();
      for (Site.Page p : byName) {
        if (p.outputExt.equals(".html") || p.outputExt.equals(".htm") || p.outputExt.equals(".xhtml") || p.url.endsWith("/")) {
          htmlPages.add(pageDrop(p));
        }
      }
      return htmlPages;
    });
    lazy.put("static_files", () -> {
      List<Object> statics = new ArrayList<>();
      for (Site.StaticFile f : byPath) {
        statics.add(staticFileDrop(f));
      }
      return statics;
    });
    return new SiteDrop(s, lazy);
  }

  /** Convertible#to_liquid for a Page: front matter + content, dir, excerpt, name, path, url. */
  static Map<String, Object> pageDrop(Site.Page p) {
    Map<String, Object> d = new LinkedHashMap<>(p.frontMatter);
    d.put("content", p.content);
    d.put("dir", p.dir());
    d.put("excerpt", p.frontMatter.containsKey("excerpt") ? String.valueOf(p.frontMatter.get("excerpt")) : null);
    d.put("name", p.name());
    d.put("path", p.frontMatter.containsKey("path") ? p.frontMatter.get("path") : p.relativePath);
    d.put("url", p.url);
    return d;
  }

  /** StaticFileDrop: name, extname, modified_time, basename, path (= relative_path, leading "/"), collection. */
  private static Map<String, Object> staticFileDrop(Site.StaticFile f) {
    Map<String, Object> d = new LinkedHashMap<>();
    String name = f.relativePath.substring(f.relativePath.lastIndexOf('/') + 1);
    String ext = Site.extOf(name);
    d.put("name", name);
    d.put("extname", ext);
    try {
      d.put("modified_time", RubyTime.ofInstant(Files.getLastModifiedTime(f.path).toInstant()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    d.put("basename", (ext.isEmpty() ? name : name.substring(0, name.length() - ext.length())).replaceAll("\\.*\\z", ""));
    d.put("path", "/" + f.relativePath);
    d.put("collection", null);
    return d;
  }

  /** site.config["liquid"]: error_mode (default warn), strict_filters / strict_variables (default false). */
  ErrorMode errorMode = ErrorMode.WARN;
  boolean strictFilters;
  boolean strictVariables;

  @SuppressWarnings("unchecked")
  private void readLiquidConfig(Site site) {
    Object l = site.config.get("liquid");
    if (l instanceof Map) {
      Map<String, Object> m = (Map<String, Object>) l;
      Object em = m.get("error_mode");
      if (em != null) {
        switch (String.valueOf(em)) {
          case "lax": errorMode = ErrorMode.LAX; break;
          case "strict": errorMode = ErrorMode.STRICT; break;
          default: errorMode = ErrorMode.WARN; // Jekyll validates and falls back to warn
        }
      }
      strictFilters = Boolean.TRUE.equals(m.get("strict_filters"));
      strictVariables = Boolean.TRUE.equals(m.get("strict_variables"));
    }
  }

  static ParseOptions parseOptions(Context ctx) {
    JekyllRenderer r = renderer(ctx);
    return new ParseOptions().errorMode(r != null ? r.errorMode : ErrorMode.WARN).lineNumbers(true).environment(ctx.environment());
  }

  /** LiquidRenderer::File#parse: Liquid::Template.parse(content, line_numbers: true) under the global :warn mode. */
  Template parse(String content, String path) {
    return Template.parse(content, new ParseOptions().errorMode(errorMode).lineNumbers(true).environment(env));
  }

  static Site site(Context ctx) {
    return (Site) ctx.registers().get(REG_SITE);
  }

  static JekyllRenderer renderer(Context ctx) {
    return (JekyllRenderer) ctx.registers().get(REG_RENDERER);
  }

  @Override
  public String render(Site site, Site.Page page) {
    String output = page.content;
    if (output.contains("{%") || output.contains("{{")) { // Utils.has_liquid_construct?
      output = renderTemplate(output, pageDrop(page), page.relativePath, Map.of());
    }
    if (page.isMarkdown()) {
      output = Kramdown.toHtml(output, kramdownConfig);
    }
    return output;
  }

  /**
   * Renderer#render_liquid with the unified payload: `page` = pageData, `site` = the site drop,
   * extra top-level assigns merged in (used by tests mirroring the oracle).
   */
  public String renderTemplate(String source, Map<String, Object> pageData, String path, Map<String, Object> extraAssigns) {
    {
      String output;
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("content", null);
      payload.put("page", pageData);
      payload.put("layout", null);
      payload.put("paginator", null);
      payload.put("highlighter_prefix", null);
      payload.put("highlighter_suffix", null);
      Map<String, Object> jekyll = new LinkedHashMap<>();
      jekyll.put("version", "4.4.1");
      jekyll.put("environment", "development");
      payload.put("jekyll", jekyll);
      payload.put("site", siteDrop);
      payload.putAll(extraAssigns);
      Map<String, Object> registers = new LinkedHashMap<>();
      registers.put(REG_SITE, site);
      registers.put(REG_PAGE, pageData);
      registers.put(REG_RENDERER, this);
      try {
        Template t = parse(source, path);
        for (LiquidException w : t.warnings()) {
          String msg = w.getMessage() + " in " + path;
          warnings.add(msg);
          if (CURRENT_PAGE_WARNINGS.get() != null) {
            CURRENT_PAGE_WARNINGS.get().add(msg);
          }
        }
        Context ctx = new Context(payload, registers, env);
        ctx.strictVariables(strictVariables);
        ctx.strictFilters(strictFilters);
        output = t.renderStrict(ctx);
      } catch (LiquidException | JekyllTags.JekyllError e) {
        throw new RenderException(e.getMessage() + " in " + path, e);
      }
      return output;
    }
  }

  /** Warnings recorded since the last call (tests use this per case). */
  public List<String> drainWarnings() {
    synchronized (warnings) {
      List<String> out = new ArrayList<>(warnings);
      warnings.clear();
      return out;
    }
  }

  @Override
  public List<String> warnings() {
    return warnings;
  }
}
