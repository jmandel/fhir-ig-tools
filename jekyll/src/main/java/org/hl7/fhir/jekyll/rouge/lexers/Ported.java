package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import java.util.function.Function;
import org.hl7.fhir.jekyll.rouge.Lexer;

/** The ported Rouge lexers, by Ruby class name (see NOTES-rouge.md). */
public final class Ported {
  private Ported() {}

  public static void register(Map<String, Function<Map<String, Object>, Lexer>> m) {
    m.put("PlainText", PlainText::new);
    m.put("JSON", JSON::new);
    m.put("JSONDOC", JSONDOC::new);
    m.put("JSON5", JSON5::new);
    m.put("XML", XML::new);
    m.put("HTML", HTML::new);
    m.put("CSS", CSS::new);
    m.put("Javascript", Javascript::new);
    m.put("Typescript", Typescript::new);
    m.put("HTTP", HTTP::new);
    m.put("Shell", Shell::new);
    m.put("Batchfile", Batchfile::new);
    m.put("SQL", SQL::new);
    m.put("Python", Python::new);
    m.put("Java", Java::new);
    m.put("YAML", YAML::new);
    m.put("Markdown", Markdown::new);
    m.put("Diff", Diff::new);
    m.put("SPARQL", SPARQL::new);
    m.put("Turtle", Turtle::new);
    m.put("Properties", Properties::new);
    m.put("Actionscript", Actionscript::new);
    m.put("Ruby", Ruby::new);
  }
}
