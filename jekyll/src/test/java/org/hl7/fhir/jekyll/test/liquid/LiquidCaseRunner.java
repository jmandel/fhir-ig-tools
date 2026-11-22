package org.hl7.fhir.jekyll.test;

import org.hl7.fhir.jekyll.test.liquid.LiquidCases;

/** Forwarder loaded by name from DiffRunner; registers the "liquid" case type. */
public final class LiquidCaseRunner {
  private LiquidCaseRunner() {}

  static {
    DiffRunner.IMPLS.put("liquid", LiquidCases::run);
  }
}
