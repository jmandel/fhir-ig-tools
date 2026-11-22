package org.hl7.fhir.sushi.fhirdefs;

import java.io.InputStream;

// port of fhir-package-loader 2.2.4 src/current/CurrentBuildClient.ts (interface; production: CoreCurrentBuildClient)
public interface CurrentBuildClient {
  /** {@code branch} null = the main/master build. */
  InputStream downloadCurrentBuild(String name, String branch) throws Exception;

  /** The current build's date (yyyyMMddHHmmss, as in package.json `date`), or null. */
  String getCurrentBuildDate(String name, String branch) throws Exception;
}
