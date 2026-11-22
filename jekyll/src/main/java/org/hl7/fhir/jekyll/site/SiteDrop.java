package org.hl7.fhir.jekyll.site;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.hl7.fhir.jekyll.liquid.Drop;

/**
 * Jekyll::Drops::SiteDrop: config as fallback data plus computed members. The expensive lists
 * (pages, html_pages, static_files: one stat per static file) are computed on first access and
 * memoized thread-safely, since IG pages almost never touch them and pages render in parallel.
 */
final class SiteDrop implements Drop {
  private final Map<String, Object> eager;
  private final Map<String, Supplier<Object>> lazy;
  private final ConcurrentHashMap<String, Object> memo = new ConcurrentHashMap<>();
  private static final Object NULL = new Object();

  SiteDrop(Map<String, Object> eager, Map<String, Supplier<Object>> lazy) {
    this.eager = eager;
    this.lazy = lazy;
  }

  @Override
  public Object invokeDrop(Object key) {
    String k = String.valueOf(key);
    Supplier<Object> s = lazy.get(k);
    if (s != null) {
      Object v = memo.computeIfAbsent(k, x -> {
        Object r = s.get();
        return r == null ? NULL : r;
      });
      return v == NULL ? null : v;
    }
    return eager.get(k);
  }

  @Override
  public boolean hasKey(Object key) {
    String k = String.valueOf(key);
    return lazy.containsKey(k) || eager.containsKey(k);
  }

  @Override
  public String rubyClassName() {
    return "Jekyll::Drops::SiteDrop";
  }
}
