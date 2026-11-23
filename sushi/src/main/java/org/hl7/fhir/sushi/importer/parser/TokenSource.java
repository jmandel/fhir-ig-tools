package org.hl7.fhir.sushi.importer.parser;

/** A lexer: produces tokens on demand, ending with an EOF token (repeated on further calls). */
interface TokenSource {
  Token nextToken();
}
