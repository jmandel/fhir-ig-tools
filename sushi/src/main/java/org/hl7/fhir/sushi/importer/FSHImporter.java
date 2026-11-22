package org.hl7.fhir.sushi.importer;

import java.util.LinkedHashMap;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.ParamRuleSet;

// port of sushi/src/import/FSHImporter.ts (SUSHI 3.21.1)
/**
 * Parses RawFSH into FSHDocuments: a visitor over the CST of the hand-written parser (WP-1, package
 * importer.parser; no ANTLR at runtime). Syntax errors are logged through FSHErrorListener with SUSHI's exact
 * messages; entities carry SourceInfo (file + TextLocation from tokens, columns in UTF-16 code units as in JS).
 */
public class FSHImporter {
  /** Parameterized RuleSets seen so far, by name (public in TS; tests read it). */
  public final LinkedHashMap<String, ParamRuleSet> paramRuleSets = new LinkedHashMap<>();

  public FSHImporter() {}

  /**
   * TS import(rawFSHes): one FSHDocument per RawFSH, in input order (inputs are sorted by the caller). Logs
   * "Preprocessed N documents with M aliases." and "Imported N definitions and M instances." at info level.
   */
  public List<FSHDocument> importFsh(List<RawFSH> rawFSHes) {
    throw new UnsupportedOperationException("TODO WP-2");
  }
}
