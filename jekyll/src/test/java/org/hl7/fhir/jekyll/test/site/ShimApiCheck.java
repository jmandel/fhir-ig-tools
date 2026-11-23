package org.hl7.fhir.jekyll.test.site;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.jekyll.IncludeProvider;
import org.hl7.fhir.jekyll.Shim;
import org.hl7.fhir.jekyll.ShimException;

/**
 * Checks of the in-process API (org.hl7.fhir.jekyll.Shim) and the IncludeProvider hook on small
 * generated sites: provider content is used (raw-wrapped or with Liquid), null falls back to disk,
 * each name is asked for once even with parallel pages, a name neither the provider nor the disk has
 * fails with Jekyll's message, include_relative into _includes consults the provider, a provider
 * exception fails the build, and late static files are copied. Exits 1 on the first failure.
 * usage: java -cp classes:test-classes org.hl7.fhir.jekyll.test.site.ShimApiCheck
 */
public final class ShimApiCheck {
  private static int checks;

  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("shim-api");
    Path src = root.resolve("src");
    write(src.resolve("_config.yml"), "title: T\n");
    write(src.resolve("_includes/disk.html"), "<b>disk {{ include.x }}</b>");
    for (int i = 0; i < 40; i++) {
      write(src.resolve("p" + i + ".html"), "---\n---\n{% include shared.xhtml %}|{% include frag-" + i + ".xhtml %}|{% include disk.html x=\"" + i + "\" %}");
    }
    write(src.resolve("rel.html"), "---\n---\n{% include_relative _includes/relfrag.xhtml %}");
    write(src.resolve("liquid.html"), "---\n---\n{% include liq.xhtml %}");
    write(src.resolve("asset.txt"), "static");

    Map<String, AtomicInteger> asked = new ConcurrentHashMap<>();
    IncludeProvider provider = name -> {
      asked.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
      if (name.equals("shared.xhtml")) {
        Thread.sleep(20); // widen the race between pages asking at once
        return "{% raw %}<i>shared</i>{% endraw %}";
      }
      if (name.startsWith("frag-")) {
        Files.writeString(src.resolve("tbl_" + name + ".png"), "png"); // generation writes a site file
        return "{% raw %}<p>" + name + "</p>{% endraw %}";
      }
      if (name.equals("relfrag.xhtml")) {
        return "﻿rel-provided";
      }
      if (name.equals("liq.xhtml")) {
        return "{{ site.title }}-{% raw %}{{x}}{% endraw %}";
      }
      return null;
    };
    List<String> log = new ArrayList<>();
    Path dest = root.resolve("out");
    Shim.Result r = Shim.build(src, dest, new Shim.Options().log(log::add).includeProvider(provider).renderThreads(8).rescanStaticsAfterRender(true));
    check(read(dest.resolve("p7.html")).equals("<i>shared</i>|<p>frag-7.xhtml</p>|<b>disk 7</b>"), "provider + disk content: " + read(dest.resolve("p7.html")));
    check(asked.get("shared.xhtml").get() == 1, "shared asked once, was " + asked.get("shared.xhtml"));
    check(asked.values().stream().allMatch(a -> a.get() == 1), "every name asked once: " + asked);
    check(asked.get("disk.html").get() == 1, "disk include offered to the provider first");
    check(read(dest.resolve("rel.html")).equals("rel-provided"), "include_relative into _includes uses the provider, BOM dropped");
    check(read(dest.resolve("liquid.html")).equals("T-{{x}}"), "Liquid in provided content renders");
    check(r.includesFromProvider == 43 && r.includesUsed == 44, "counts: " + r);
    check(r.lateStaticFiles.size() == 40 && Files.isRegularFile(dest.resolve("tbl_frag-3.xhtml.png")), "late statics copied: " + r.lateStaticFiles.size());
    check(Files.isRegularFile(dest.resolve("asset.txt")), "static copied");
    check(log.get(1).equals("            Source: " + src.toAbsolutePath().normalize()) && log.get(log.size() - 2).contains("done in "), "console lines: " + log);

    // missing everywhere: Jekyll's error, as a render failure, after the Liquid Exception line
    write(src.resolve("missing.html"), "---\n---\n{% include nowhere.xhtml %}");
    log.clear();
    try {
      Shim.build(src, root.resolve("out2"), new Shim.Options().log(log::add).includeProvider(provider));
      check(false, "missing include must fail");
    } catch (ShimException e) {
      check(e.isRenderFailure() && e.getMessage().startsWith("Could not locate the included file 'nowhere.xhtml' in any of ["), "missing message: " + e.getMessage());
      check(log.stream().anyMatch(l -> l.startsWith("  Liquid Exception: Could not locate the included file 'nowhere.xhtml'")), "Liquid Exception logged");
    }
    Files.delete(src.resolve("missing.html"));

    // provider failure: a render failure naming the include
    try {
      Shim.build(src, root.resolve("out3"), new Shim.Options().log(l -> { }).includeProvider(n -> {
        throw new IllegalStateException("boom");
      }));
      check(false, "provider exception must fail");
    } catch (ShimException e) {
      check(e.isRenderFailure() && e.getMessage().contains("Include provider failed for '") && e.getMessage().contains("boom"), "provider failure: " + e.getMessage());
    }
    failedBuildLeavesTheDestinationAsItWas(root.resolve("atomic"));
    System.out.println("ShimApiCheck: " + checks + " checks passed");
  }

  /**
   * As Jekyll (site.rb#process renders everything before cleaner.rb and the writes): a failed build changes nothing
   * in an existing destination and creates no destination; a successful one cleans and writes as before, and no
   * staging folder is left behind either way.
   */
  private static void failedBuildLeavesTheDestinationAsItWas(Path root) throws Exception {
    Path src = root.resolve("src");
    write(src.resolve("_config.yml"), "keep_files: [keep]\n");
    for (int i = 0; i < 200; i++) { // many pages, rendered in parallel: most are written before the bad one fails
      write(src.resolve("p" + i + ".html"), "---\n---\nnew " + i);
    }
    write(src.resolve("img/a.png"), "png");
    write(src.resolve("zz-bad.html"), "---\n---\n{% include nowhere.xhtml %}");
    Path dest = root.resolve("out");
    write(dest.resolve("p1.html"), "old 1");
    write(dest.resolve("obsolete.html"), "old");
    write(dest.resolve("keep/k.txt"), "kept");
    Map<String, String> before = snapshot(dest);
    try {
      Shim.build(src, dest, new Shim.Options().log(l -> { }).renderThreads(8));
      check(false, "bad include must fail");
    } catch (ShimException e) {
      check(e.isRenderFailure(), "render failure");
    }
    check(snapshot(dest).equals(before), "failed build: destination unchanged: " + snapshot(dest).keySet());
    Path fresh = root.resolve("fresh/deeper/out");
    try {
      Shim.build(src, fresh, new Shim.Options().log(l -> { }));
      check(false, "bad include must fail");
    } catch (ShimException e) {
      check(!Files.exists(root.resolve("fresh")), "failed build: no destination (or parent) created");
    }
    Files.delete(src.resolve("zz-bad.html"));
    Shim.build(src, dest, new Shim.Options().log(l -> { }).renderThreads(8));
    Map<String, String> after = snapshot(dest);
    check(after.get("p1.html").equals("new 1") && after.get("p199.html").equals("new 199") && after.get("img/a.png").equals("png"), "success: written");
    check(!after.containsKey("obsolete.html") && after.get("keep/k.txt").equals("kept"), "success: cleaned, keep_files kept");
    check(after.size() == 204, "success: nothing else (no staging folder): " + after.size());
    try (var s = Files.list(dest)) {
      check(s.noneMatch(p -> p.getFileName().toString().startsWith(".jekyll-shim-staging")), "no staging folder left");
    }
  }

  private static Map<String, String> snapshot(Path dir) throws Exception {
    Map<String, String> m = new java.util.TreeMap<>();
    if (!Files.isDirectory(dir)) {
      return m;
    }
    try (var w = Files.walk(dir)) {
      for (Path p : w.toList()) {
        String rel = dir.relativize(p).toString().replace('\\', '/');
        m.put(rel + (Files.isDirectory(p) ? "/" : ""), Files.isDirectory(p) ? "" : read(p));
      }
    }
    m.remove("/");
    return m;
  }

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) {
      System.out.println("FAIL: " + what);
      System.exit(1);
    }
  }

  private static void write(Path p, String s) throws Exception {
    Files.createDirectories(p.getParent());
    Files.writeString(p, s, StandardCharsets.UTF_8);
  }

  private static String read(Path p) throws Exception {
    return Files.readString(p, StandardCharsets.UTF_8);
  }
}
