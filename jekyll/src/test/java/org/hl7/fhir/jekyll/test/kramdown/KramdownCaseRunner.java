package org.hl7.fhir.jekyll.test;

import java.util.LinkedHashMap;
import java.util.Map;
import org.hl7.fhir.jekyll.kramdown.Kramdown;

/**
 * Forwarding runner loaded by name from {@link DiffRunner}: registers the "kramdown" case type
 * (see jekyll/oracle/oracle.rb) and renders it with {@link Kramdown#toHtml}.
 */
public final class KramdownCaseRunner {
  private KramdownCaseRunner() {}

  static {
    DiffRunner.IMPLS.put("kramdown", KramdownCaseRunner::run);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> run(Map<String, Object> c) {
    Map<String, Object> res = new LinkedHashMap<>();
    try {
      res.put("output", Kramdown.toHtml((String) c.get("text"), (Map<String, Object>) c.get("config")));
    } catch (RuntimeException e) {
      res.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    return res;
  }
}
