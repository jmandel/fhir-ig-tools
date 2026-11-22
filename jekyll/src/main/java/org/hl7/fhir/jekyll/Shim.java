package org.hl7.fhir.jekyll;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.hl7.fhir.jekyll.site.Builder;

/**
 * In-process entry point: what `jekyll build --destination DEST` does in SOURCE, without a process.
 *
 * <pre>
 * Shim.Result r = Shim.build(Path.of(tempDir), Path.of(outputDir), new Shim.Options()
 *     .log(line -&gt; log("Jekyll: " + line))         // Jekyll's console lines (default: System.out)
 *     .includeProvider(registry::include));          // optional: in-memory/lazy _includes
 * </pre>
 *
 * Console lines are exactly those the `jekyll-shim` CLI prints (the CLI is a thin wrapper over this),
 * including "Liquid Exception: …" before a {@link ShimException}. The build runs on its own
 * large-stack thread (deep Liquid/kramdown recursion needs no -Xss from the caller); the calling
 * thread waits. Not re-entrant per source folder; separate builds may run concurrently.
 */
public final class Shim {
  private Shim() {}

  public static Result build(Path source, Path destination, Options options) throws ShimException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");
    return Builder.build(source, destination, options == null ? new Options() : options);
  }

  /** Build options; every setter returns this. */
  public static final class Options {
    private Consumer<String> log = System.out::println;
    private IncludeProvider includeProvider;
    private int renderThreads; // 0 = SHIM_RENDER_THREADS or the CPU count
    private boolean rescanStatics;

    /** Receives each console line (no trailing newline), in Jekyll's order. */
    public Options log(Consumer<String> log) {
      this.log = Objects.requireNonNull(log);
      return this;
    }

    /** Supplies `_includes` content before the disk is consulted (see {@link IncludeProvider}). */
    public Options includeProvider(IncludeProvider p) {
      this.includeProvider = p;
      return this;
    }

    /** Page render parallelism (default: env SHIM_RENDER_THREADS, else the CPU count). */
    public Options renderThreads(int n) {
      this.renderThreads = n;
      return this;
    }

    /**
     * After rendering, look for static files that appeared in the source while pages were being
     * rendered and copy them too. Needed when an {@link IncludeProvider} generates content lazily and
     * that generation writes files into the site (the publisher's table renderers write
     * tbl_bck*.png). Jekyll itself never sees such files; they are reported in
     * {@link Result#lateStaticFiles}.
     */
    public Options rescanStaticsAfterRender(boolean on) {
      this.rescanStatics = on;
      return this;
    }

    public Consumer<String> log() {
      return log;
    }

    public IncludeProvider includeProvider() {
      return includeProvider;
    }

    public int renderThreads() {
      return renderThreads;
    }

    public boolean rescanStaticsAfterRender() {
      return rescanStatics;
    }
  }

  /** What a successful build did. Times are nanoseconds. */
  public static final class Result {
    public final int pages;
    public final int staticFiles;
    /** "Liquid Warning" texts, in page order (also logged). */
    public final List<String> warnings;
    /** Distinct include names resolved (by `include`; disk or provider). */
    public final int includesUsed;
    /** Of those, how many the {@link IncludeProvider} supplied. */
    public final int includesFromProvider;
    /** Static files found by {@link Options#rescanStaticsAfterRender} (source-relative paths). */
    public final List<String> lateStaticFiles;
    public final long readNanos, prepareNanos, renderNanos, writeNanos, totalNanos;

    public Result(int pages, int staticFiles, List<String> warnings, int includesUsed, int includesFromProvider,
        List<String> lateStaticFiles, long readNanos, long prepareNanos, long renderNanos, long writeNanos, long totalNanos) {
      this.pages = pages;
      this.staticFiles = staticFiles;
      this.warnings = List.copyOf(warnings);
      this.includesUsed = includesUsed;
      this.includesFromProvider = includesFromProvider;
      this.lateStaticFiles = List.copyOf(lateStaticFiles);
      this.readNanos = readNanos;
      this.prepareNanos = prepareNanos;
      this.renderNanos = renderNanos;
      this.writeNanos = writeNanos;
      this.totalNanos = totalNanos;
    }

    @Override
    public String toString() {
      return String.format(java.util.Locale.ROOT,
          "pages=%d static=%d includes=%d (provider %d) late_static=%d warnings=%d read=%.2fs prepare=%.2fs render=%.2fs write=%.2fs total=%.2fs",
          pages, staticFiles, includesUsed, includesFromProvider, lateStaticFiles.size(), warnings.size(),
          readNanos / 1e9, prepareNanos / 1e9, renderNanos / 1e9, writeNanos / 1e9, totalNanos / 1e9);
    }
  }
}
