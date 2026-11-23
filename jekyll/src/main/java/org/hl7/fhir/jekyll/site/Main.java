package org.hl7.fhir.jekyll.site;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Drop-in for `jekyll build --destination X`, as the IG Publisher invokes it (`-jekyll <cmd>`, run
 * with the working directory set to temp/pages). Prints Jekyll's console lines; the publisher
 * keys on "Source:", "Liquid Exception:" and "done in" (PublisherGenerator#passJekyllFilter).
 *
 * usage: build [--source DIR] (--destination|-d) DIR
 */
public final class Main {
  private Main() {}

  static int renderThreads() {
    String v = System.getenv("SHIM_RENDER_THREADS");
    return v != null ? Integer.parseInt(v) : Math.max(1, Runtime.getRuntime().availableProcessors());
  }

  /** Renders pages in parallel (each thread has its own include-template cache); errors surface in page order. */
  static void renderAll(Site site, PageRenderer renderer) throws Exception {
    renderAll(site, renderer, renderThreads());
  }

  static void renderAll(Site site, PageRenderer renderer, int threads) throws Exception {
    if (threads <= 1 || !(renderer instanceof JekyllRenderer)) {
      for (Site.Page p : site.pages) {
        renderOne(site, renderer, p);
      }
      return;
    }
    java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(threads, r -> {
      Thread t = new Thread(null, r, "render", 64L << 20); // deep Liquid/kramdown recursion headroom
      t.setDaemon(true);
      return t;
    });
    try {
      java.util.List<java.util.concurrent.Future<?>> fs = new java.util.ArrayList<>();
      for (Site.Page p : site.pages) {
        fs.add(ex.submit(() -> {
          renderOne(site, renderer, p);
          return null;
        }));
      }
      for (java.util.concurrent.Future<?> f : fs) {
        try {
          f.get();
        } catch (java.util.concurrent.ExecutionException e) {
          if (e.getCause() instanceof PageRenderer.RenderException) {
            throw (PageRenderer.RenderException) e.getCause();
          }
          throw e;
        }
      }
    } finally {
      ex.shutdownNow();
      // pages already rendering finish (a render isn't interruptible): wait, so nothing writes into the staging
      // folder after the caller has removed it (Site#abortOutput)
      while (!ex.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES)) {
        // keep waiting
      }
    }
  }

  private static void renderOne(Site site, PageRenderer renderer, Site.Page p) {
    java.util.List<String> w = new java.util.ArrayList<>();
    JekyllRenderer.CURRENT_PAGE_WARNINGS.set(w);
    try {
      p.output = renderer.render(site, p);
      site.writePage(p); // stream: write now, release the output string
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    } finally {
      JekyllRenderer.CURRENT_PAGE_WARNINGS.remove();
      p.warnings = w;
    }
  }

  /**
   * The CLI over {@link Builder} (the in-process API, {@link org.hl7.fhir.jekyll.Shim}): console lines
   * to stdout; exit status 1 after a Liquid Exception, as Jekyll's.
   */
  public static void main(String[] args) throws Exception {
    Path source = Path.of(".");
    Path dest = null;
    int i = 0;
    if (args.length > 0 && args[0].equals("build")) {
      i = 1;
    }
    for (; i < args.length; i++) {
      switch (args[i]) {
        case "--destination": case "-d": dest = Path.of(args[++i]); break;
        case "--source": case "-s": source = Path.of(args[++i]); break;
        case "--renderer": // only the Jekyll renderer exists
          if (!args[++i].equals(JekyllRenderer.class.getName())) {
            System.err.println("unsupported renderer: " + args[i]);
            System.exit(2);
          }
          break;
        case "--quiet": case "-q": case "--trace": case "-t": break;
        default:
          System.err.println("unknown argument: " + args[i]);
          System.exit(2);
      }
    }
    if (dest == null) {
      dest = source.resolve("_site");
    }
    try {
      Builder.build(source, dest, new org.hl7.fhir.jekyll.Shim.Options().log(System.out::println));
    } catch (org.hl7.fhir.jekyll.ShimException e) {
      if (e.isRenderFailure()) {
        System.exit(1); // Builder printed Jekyll's error lines
      }
      Throwable c = e.getCause();
      if (c instanceof Exception) {
        throw (Exception) c; // as before: uncaught, stack trace, exit 1
      }
      throw e;
    }
  }

  /**
   * SHIM_INCLUDE_STATS=&lt;file&gt;: how many _includes files were actually included (by `include`; not
   * include_relative), with the unused names written to the file. Measures what generating fragments
   * on demand would save.
   */
  static void reportIncludeUse(Site site, JekyllRenderer r, String outFile) throws java.io.IOException {
    java.nio.file.Path dir = site.source.resolve("_includes");
    if (!java.nio.file.Files.isDirectory(dir)) {
      return;
    }
    java.util.Set<String> used = r.inclusionsByName.keySet();
    long[] n = new long[4]; // files, bytes, used files, used bytes
    java.util.List<String> unused = new java.util.ArrayList<>();
    try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.walk(dir)) {
      for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) s.filter(java.nio.file.Files::isRegularFile).sorted()::iterator) {
        String rel = dir.relativize(p).toString().replace('\\', '/');
        long size = java.nio.file.Files.size(p);
        n[0]++;
        n[1] += size;
        if (used.contains(rel)) {
          n[2]++;
          n[3] += size;
        } else {
          unused.add(rel);
        }
      }
    }
    java.nio.file.Files.write(java.nio.file.Path.of(outFile), unused);
    System.err.printf(Locale.ROOT, "SHIM_INCLUDES files=%d bytes=%d used_files=%d used_bytes=%d unused_files=%d unused_bytes=%d%n",
        n[0], n[1], n[2], n[3], n[0] - n[2], n[1] - n[3]);
  }
}
