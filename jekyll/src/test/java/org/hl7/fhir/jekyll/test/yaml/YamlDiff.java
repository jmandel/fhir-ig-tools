package org.hl7.fhir.jekyll.test.yaml;

/**
 * DiffRunner with the YAML port's case types registered ("yaml", "data-file", "yaml-typed",
 * "yaml-typed-file"; see {@code org.hl7.fhir.jekyll.test.YamlCaseRunner}).
 *
 * usage: BUILD=build-yaml jekyll/run.sh org.hl7.fhir.jekyll.test.yaml.YamlDiff &lt;cases&gt; &lt;expected&gt; [DiffRunner options]
 */
public final class YamlDiff {
  private YamlDiff() {}

  public static void main(String[] args) throws Exception {
    Class.forName("org.hl7.fhir.jekyll.test.YamlCaseRunner");
    org.hl7.fhir.jekyll.test.DiffRunner.main(args);
  }
}
