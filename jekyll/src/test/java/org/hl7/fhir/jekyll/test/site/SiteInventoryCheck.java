package org.hl7.fhir.jekyll.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.hl7.fhir.jekyll.site.Site;

/**
 * Checks the Java site reader (entry filter, page vs static, URLs/destinations) against real
 * Jekyll: the set of output paths predicted from jekyll-in must equal the files in jekyll-out.
 *
 * usage: SiteInventoryCheck &lt;golden-dir&gt; [...]
 */
public final class SiteInventoryCheck {
  public static void main(String[] args) throws IOException {
    int bad = 0;
    for (String g : args) {
      Path gd = Path.of(g);
      Path in = gd.resolve("jekyll-in");
      Path out = gd.resolve("jekyll-out");
      Site site = new Site(in, gd.resolve("shim-out-unused"));
      long t0 = System.nanoTime();
      site.read();
      long ms = (System.nanoTime() - t0) / 1_000_000;
      Set<String> predicted = new TreeSet<>();
      site.pages.forEach(p -> predicted.add(p.destinationRelative()));
      site.staticFiles.forEach(s -> predicted.add(s.destinationRelative));
      Set<String> actual = new TreeSet<>();
      try (Stream<Path> s = Files.walk(out)) {
        s.filter(Files::isRegularFile).forEach(p -> actual.add(out.relativize(p).toString().replace('\\', '/')));
      }
      Set<String> missing = new TreeSet<>(actual);
      missing.removeAll(predicted);
      Set<String> extra = new TreeSet<>(predicted);
      extra.removeAll(actual);
      System.out.printf("%s: pages=%d static=%d data-keys=%d read=%dms | missing=%d extra=%d%n", gd.getFileName(),
          site.pages.size(), site.staticFiles.size(), site.data.size(), ms, missing.size(), extra.size());
      missing.stream().limit(5).forEach(m -> System.out.println("   missing: " + m));
      extra.stream().limit(5).forEach(m -> System.out.println("   extra:   " + m));
      if (!missing.isEmpty() || !extra.isEmpty()) {
        bad++;
      }
    }
    System.exit(bad == 0 ? 0 : 1);
  }
}
