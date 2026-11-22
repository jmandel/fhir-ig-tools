package org.hl7.fhir.jekyll.site;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.hl7.fhir.jekyll.Shim;
import org.hl7.fhir.jekyll.ShimException;

/**
 * One `jekyll build`: read, render (parallel), write; Jekyll's console lines go to the log callback.
 * Behind {@link Shim#build} and the CLI ({@link Main}).
 */
public final class Builder {
  private Builder() {}

  /** Runs {@link #buildHere} on a thread with a large stack (deep Liquid/kramdown recursion needs no -Xss). */
  public static Shim.Result build(Path source, Path dest, Shim.Options options) throws ShimException {
    Object[] out = new Object[1];
    Throwable[] failure = new Throwable[1];
    Thread t = new Thread(null, () -> {
      try {
        out[0] = buildHere(source, dest, options);
      } catch (Throwable e) {
        failure[0] = e;
      }
    }, "jekyll-shim", 512L << 20);
    t.start();
    try {
      t.join();
    } catch (InterruptedException e) {
      t.interrupt();
      Thread.currentThread().interrupt();
      throw new ShimException("interrupted", e, false);
    }
    if (failure[0] instanceof ShimException) {
      throw (ShimException) failure[0];
    }
    if (failure[0] != null) {
      throw new ShimException(String.valueOf(failure[0].getMessage()), failure[0], false);
    }
    return (Shim.Result) out[0];
  }

  /**
   * The build on the calling thread. Logs "Liquid Exception: …" and the --trace hint, then throws a
   * render-failure {@link ShimException}, where Jekyll exits 1; other failures propagate as thrown.
   */
  static Shim.Result buildHere(Path source, Path dest, Shim.Options options) throws Exception {
    Consumer<String> log = options.log();
    long t0 = System.nanoTime();
    Site site = new Site(source, dest);
    Path cfg = site.source.resolve("_config.yml");
    log.accept("Configuration file: " + (cfg.toFile().isFile() ? cfg : "none"));
    log.accept("            Source: " + site.source);
    log.accept("       Destination: " + site.dest);
    log.accept(" Incremental build: disabled. Enable with --incremental");
    log.accept("      Generating... ");
    JekyllRenderer renderer = new JekyllRenderer();
    renderer.includeProvider = options.includeProvider();
    boolean timing = System.getenv("SHIM_TIMING") != null;
    long a, b, c, d, e;
    List<String> warnings = new ArrayList<>();
    List<String> late = List.of();
    try {
      a = System.nanoTime();
      site.read();
      b = System.nanoTime();
      renderer.prepare(site);
      site.prepareOutput();
      c = System.nanoTime();
      Thread statics = new Thread(() -> {
        try {
          site.writeStatics();
        } catch (java.io.IOException ex) {
          throw new java.io.UncheckedIOException(ex);
        }
      }, "statics");
      java.util.concurrent.atomic.AtomicReference<Throwable> staticsFailure = new java.util.concurrent.atomic.AtomicReference<>();
      statics.setUncaughtExceptionHandler((th, ex) -> staticsFailure.set(ex));
      statics.start(); // static copies overlap with rendering
      try {
        Main.renderAll(site, renderer, options.renderThreads() > 0 ? options.renderThreads() : Main.renderThreads());
      } finally {
        statics.join(); // never leave the copier running behind a failure
      }
      d = System.nanoTime();
      for (Site.Page p : site.pages) {
        for (String w : p.warnings) {
          warnings.add(w);
          log.accept("    Liquid Warning: " + w);
        }
      }
      if (staticsFailure.get() != null) {
        throw new RuntimeException(staticsFailure.get());
      }
      if (options.rescanStaticsAfterRender()) {
        late = site.copyLateStatics();
      }
      e = System.nanoTime();
      if (timing) {
        System.err.printf(Locale.ROOT, "SHIM_TIMING config=%.2fs read=%.2fs prepare=%.2fs render=%.2fs write=%.2fs pages=%d static=%d%n",
            (a - t0) / 1e9, (b - a) / 1e9, (c - b) / 1e9, (d - c) / 1e9, (e - d) / 1e9, site.pages.size(), site.staticFiles.size());
      }
      String includeStats = System.getenv("SHIM_INCLUDE_STATS");
      if (includeStats != null) {
        Main.reportIncludeUse(site, renderer, includeStats);
      }
    } catch (PageRenderer.RenderException ex) {
      log.accept("  Liquid Exception: " + ex.getMessage());
      log.accept("             Error: Run jekyll build --trace for more information.");
      throw new ShimException(ex.getMessage(), ex, true);
    }
    long end = System.nanoTime();
    log.accept(String.format(Locale.ROOT, "                    done in %.3f seconds.", (end - t0) / 1e9));
    log.accept(" Auto-regeneration: disabled. Use --watch to enable.");
    return new Shim.Result(site.pages.size(), site.staticFiles.size(), warnings, renderer.inclusionsByName.size(),
        renderer.includesFromProvider.get(), late, b - a, c - b, d - c, e - d, end - t0);
  }
}
