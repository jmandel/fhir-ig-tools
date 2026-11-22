package org.hl7.fhir.sushi.fhirdefs;

import java.util.Map;

// port of fhir-package-loader 2.2.4 src/virtual/VirtualPackage.ts
/** A package that is not in the package cache (in-memory resources, or local files). */
public interface VirtualPackage {
  /** Registration callback: (key, resource JSON, allowNonResources). Throws when the resource is invalid. */
  @FunctionalInterface
  interface Register {
    void register(String key, Object resource, boolean allowNonResources);
  }

  void registerResources(Register register) throws Exception;

  /** package.json; must have a name and version (JS value model). */
  Map<String, Object> getPackageJSON();

  Object getResourceByKey(String key);
}
