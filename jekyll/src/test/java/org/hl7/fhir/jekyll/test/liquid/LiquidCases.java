package org.hl7.fhir.jekyll.test.liquid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.ErrorMode;
import org.hl7.fhir.jekyll.liquid.FileSystem;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseOptions;
import org.hl7.fhir.jekyll.liquid.RubyError;
import org.hl7.fhir.jekyll.liquid.Template;

/** Runs one {@code type: liquid} oracle case exactly like jekyll/oracle/oracle_liquid.rb. */
public final class LiquidCases {
  private LiquidCases() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> run(Map<String, Object> c) {
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("id", c.get("id"));
    try {
      ErrorMode mode = ErrorMode.of((String) c.get("error_mode"));
      Template t = Template.parse((String) c.get("template"), new ParseOptions().errorMode(mode).lineNumbers(true));
      if (!t.warnings().isEmpty()) {
        List<Object> w = new ArrayList<>();
        for (LiquidException e : t.warnings()) {
          w.add(e.toS(true));
        }
        res.put("warnings", w);
      }
      Map<String, Object> regs = new HashMap<>();
      Map<String, String> files = new HashMap<>();
      if (c.get("files") instanceof Map) {
        for (Map.Entry<String, Object> e : ((Map<String, Object>) c.get("files")).entrySet()) {
          files.put(e.getKey(), (String) e.getValue());
        }
      }
      regs.put("file_system", FileSystem.ofMap(files));
      boolean bang = Boolean.TRUE.equals(c.get("render_bang"));
      Map<String, Object> assigns = c.get("assigns") instanceof Map ? (Map<String, Object>) c.get("assigns") : new LinkedHashMap<>();
      Context ctx = new Context(List.of(assigns), new LinkedHashMap<>(), regs, bang, null, null);
      if (Boolean.TRUE.equals(c.get("strict_variables"))) {
        ctx.strictVariables(true);
      }
      if (Boolean.TRUE.equals(c.get("strict_filters"))) {
        ctx.strictFilters(true);
      }
      res.put("output", bang ? t.renderStrict(ctx) : t.render(ctx));
      if (!t.errors().isEmpty()) {
        List<Object> errs = new ArrayList<>();
        for (LiquidException e : t.errors()) {
          errs.add(e.rubyClassName() + ": " + e.getMessage());
        }
        res.put("render_errors", errs);
      }
    } catch (LiquidException e) {
      res.put("error", e.rubyClassName() + ": " + e.getMessage());
    } catch (RubyError e) {
      res.put("error", e.rubyClass() + ": " + e.getMessage());
    }
    return res;
  }
}
