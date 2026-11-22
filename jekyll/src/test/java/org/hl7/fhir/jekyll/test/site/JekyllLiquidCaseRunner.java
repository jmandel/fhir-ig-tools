package org.hl7.fhir.jekyll.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.site.JekyllRenderer;
import org.hl7.fhir.jekyll.site.PageRenderer;
import org.hl7.fhir.jekyll.site.Site;

/**
 * "jekyll-liquid" cases (jekyll/oracle/oracle.rb): a site with the case's config, data and
 * _includes; the template rendered as a page with payload page = case page, plus case assigns.
 */
final class JekyllLiquidCaseRunner {
  private JekyllLiquidCaseRunner() {}

  @SuppressWarnings("unchecked")
  static Map<String, Object> run(Map<String, Object> c) {
    Path src;
    try {
      src = Files.createTempDirectory("jk-case");
      Map<String, Object> includes = (Map<String, Object>) c.getOrDefault("includes", Map.of());
      for (Map.Entry<String, Object> e : includes.entrySet()) {
        Path p = src.resolve("_includes").resolve(e.getKey());
        Files.createDirectories(p.getParent());
        Files.writeString(p, (String) e.getValue(), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Map<String, Object> cfg = (Map<String, Object>) c.getOrDefault("config", Map.of());
    Map<String, Object> data = (Map<String, Object>) c.getOrDefault("data", new LinkedHashMap<>());
    Site site = new Site(src, src.resolve("_site"), new LinkedHashMap<>(cfg), data);
    JekyllRenderer r = new JekyllRenderer();
    r.prepare(site);
    Map<String, Object> page = (Map<String, Object>) c.getOrDefault("page", Map.of("path", "index.html"));
    Map<String, Object> assigns = (Map<String, Object>) c.getOrDefault("assigns", Map.of());
    Map<String, Object> res = new LinkedHashMap<>();
    try {
      res.put("output", r.renderTemplate((String) c.get("template"), page, String.valueOf(page.get("path")), assigns));
      List<String> w = r.drainWarnings();
      if (!w.isEmpty()) {
        res.put("warnings", w);
      }
    } catch (PageRenderer.RenderException e) {
      res.put("error", e.getMessage());
    }
    return res;
  }
}
