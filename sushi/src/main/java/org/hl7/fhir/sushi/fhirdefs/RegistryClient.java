package org.hl7.fhir.sushi.fhirdefs;

import java.io.InputStream;

// port of fhir-package-loader 2.2.4 src/registry/RegistryClient.ts (interface; production: CoreRegistryClient)
public interface RegistryClient {
  /** Resolves {@code latest} and {@code x.y.x} to a concrete version; other versions are returned unchanged. */
  String resolveVersion(String name, String version) throws Exception;

  /** The package tarball (.tgz). */
  InputStream download(String name, String version) throws Exception;
}
