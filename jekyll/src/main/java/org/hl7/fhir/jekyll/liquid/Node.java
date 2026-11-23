package org.hl7.fhir.jekyll.liquid;

/** A renderable node in a BlockBody nodelist (Tag or Variable); plain text nodes are Strings. */
public interface Node {
  Object render(Context context);

  Integer lineNumber();
}
