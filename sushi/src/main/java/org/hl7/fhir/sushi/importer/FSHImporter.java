package org.hl7.fhir.sushi.importer;

import java.util.List;

// port of sushi/src/import/FSHImporter.ts -- CONTRACT SKELETON, owned by WP-2 (importer).
/**
 * Parses RawFSH into FSHDocuments with a HAND-WRITTEN lexer + recursive-descent parser (WP-1, package
 * importer.parser; no ANTLR at runtime), checked against the test-scope ANTLR oracle (importer.antlr). Syntax errors are logged
 * through FSHErrorListener with SUSHI's exact messages; entities carry SourceInfo (file + TextLocation from tokens).
 * Note: SUSHI's JS runtime indexes the input by UTF-16 code unit, the Java CharStreams by code point; columns differ
 * only after astral characters -- WP-2 must reproduce the JS columns (CharStreams.fromString vs a UTF-16 stream).
 */
public class FSHImporter {
  /** TS import(rawFSHes): one FSHDocument per RawFSH, in input order (inputs are sorted by the caller). */
  public List<FSHDocument> importFsh(List<RawFSH> rawFSHes) {
    throw new UnsupportedOperationException("TODO WP-2");
  }
}
