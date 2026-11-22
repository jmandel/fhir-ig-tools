package org.hl7.fhir.sushi.fhirdefs;

// port of fhir-package-loader 2.2.4 src/package/PackageStats.ts
public record PackageStats(String name, String version, int resourceCount) {}
