package org.hl7.fhir.jekyll.liquid;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hl7.fhir.jekyll.liquid.tags.Assign;
import org.hl7.fhir.jekyll.liquid.tags.Break;
import org.hl7.fhir.jekyll.liquid.tags.Capture;
import org.hl7.fhir.jekyll.liquid.tags.Case;
import org.hl7.fhir.jekyll.liquid.tags.Comment;
import org.hl7.fhir.jekyll.liquid.tags.Continue;
import org.hl7.fhir.jekyll.liquid.tags.Cycle;
import org.hl7.fhir.jekyll.liquid.tags.Decrement;
import org.hl7.fhir.jekyll.liquid.tags.For;
import org.hl7.fhir.jekyll.liquid.tags.If;
import org.hl7.fhir.jekyll.liquid.tags.Ifchanged;
import org.hl7.fhir.jekyll.liquid.tags.Include;
import org.hl7.fhir.jekyll.liquid.tags.Increment;
import org.hl7.fhir.jekyll.liquid.tags.Raw;
import org.hl7.fhir.jekyll.liquid.tags.TableRow;
import org.hl7.fhir.jekyll.liquid.tags.Unless;

/**
 * The registries Ruby keeps globally ({@code Template.tags}, the global Strainer's filter modules,
 * {@code Template.file_system}), as an object so each site can have its own mutable copy.
 */
public class Environment {
  private final Map<String, TagFactory> tags = new LinkedHashMap<>();
  private final Map<String, Filter> filters = new LinkedHashMap<>();
  private FileSystem fileSystem = FileSystem.BLANK;

  private static final Environment DEFAULT = standard();

  /** Shared default (used when ParseOptions has no environment). Do not mutate. */
  static Environment defaultEnvironment() {
    return DEFAULT;
  }

  /** A fresh environment with the standard tags and filters of Liquid 4.0.4. */
  public static Environment standard() {
    Environment e = new Environment();
    e.registerTag("assign", Assign::new);
    e.registerTag("break", Break::new);
    e.registerTag("capture", Capture::new);
    e.registerTag("case", Case::new);
    e.registerTag("comment", Comment::new);
    e.registerTag("continue", Continue::new);
    e.registerTag("cycle", Cycle::new);
    e.registerTag("decrement", Decrement::new);
    e.registerTag("for", For::new);
    e.registerTag("if", If::new);
    e.registerTag("ifchanged", Ifchanged::new);
    e.registerTag("include", Include::new);
    e.registerTag("increment", Increment::new);
    e.registerTag("raw", Raw::new);
    e.registerTag("tablerow", TableRow::new);
    e.registerTag("unless", Unless::new);
    StandardFilters.register(e);
    return e;
  }

  /** A mutable copy. */
  public Environment copy() {
    Environment e = new Environment();
    e.tags.putAll(tags);
    e.filters.putAll(filters);
    e.fileSystem = fileSystem;
    return e;
  }

  /** Ruby {@code Template.register_tag(name, klass)}. */
  public Environment registerTag(String name, TagFactory factory) {
    tags.put(name, factory);
    return this;
  }

  public Environment unregisterTag(String name) {
    tags.remove(name);
    return this;
  }

  /** Ruby {@code Template.register_filter(mod)}, one method at a time. */
  public Environment registerFilter(String name, Filter filter) {
    filters.put(name, filter);
    return this;
  }

  public TagFactory tag(String name) {
    return tags.get(name);
  }

  public Filter filter(String name) {
    return filters.get(name);
  }

  public Map<String, TagFactory> tags() {
    return Collections.unmodifiableMap(tags);
  }

  public Map<String, Filter> filters() {
    return Collections.unmodifiableMap(filters);
  }

  /** Ruby {@code Template.file_system} (used when registers has no "file_system"). */
  public FileSystem fileSystem() {
    return fileSystem;
  }

  public Environment fileSystem(FileSystem fs) {
    this.fileSystem = fs;
    return this;
  }
}
