package org.hl7.fhir.jekyll.liquid;

import java.util.LinkedHashMap;

/** The trailing keyword-argument hash of a filter call ({@code {{ x | f: a, key: v }}}). A Ruby Hash. */
public class KeywordArgs extends LinkedHashMap<String, Object> {
  private static final long serialVersionUID = 1L;
}
