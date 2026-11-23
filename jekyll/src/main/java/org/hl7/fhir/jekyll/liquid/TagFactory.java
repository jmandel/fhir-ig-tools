package org.hl7.fhir.jekyll.liquid;

/** Creates a tag/block instance (Ruby {@code Tag.parse} = {@code new} + {@code parse(tokenizer)}). */
@FunctionalInterface
public interface TagFactory {
  Tag create(String tagName, String markup, ParseContext parseContext);
}
