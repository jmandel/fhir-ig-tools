package org.hl7.fhir.sushi.fhirdefs;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

// port of fhir-package-loader 2.2.4 src/virtual/InMemoryVirtualPackage.ts
public class InMemoryVirtualPackage implements VirtualPackage {
  private final Map<String, Object> packageJSON;
  private final Map<String, ?> resources;
  private final BiConsumer<String, String> log;
  private final boolean allowNonResources;
  private final Set<String> registeredResources = new HashSet<>();

  /** {@code resources}: key -> resource JSON, registered in the map's iteration order (use a LinkedHashMap). */
  public InMemoryVirtualPackage(Map<String, Object> packageJSON, Map<String, ?> resources) {
    this(packageJSON, resources, null, false);
  }

  public InMemoryVirtualPackage(
      Map<String, Object> packageJSON, Map<String, ?> resources, BiConsumer<String, String> log,
      boolean allowNonResources) {
    this.packageJSON = packageJSON;
    this.resources = resources;
    this.log = log != null ? log : (l, m) -> {};
    this.allowNonResources = allowNonResources;
  }

  /** {name, version} package.json */
  public static Map<String, Object> packageJSON(String name, String version) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", name);
    m.put("version", version);
    return m;
  }

  @Override
  public void registerResources(Register register) {
    resources.forEach((key, resource) -> {
      try {
        register.register(key, resource, allowNonResources);
        registeredResources.add(key);
      } catch (RuntimeException e) {
        log.accept("error", "Failed to register resource with key: " + key);
        log.accept("debug", BasePackageLoader.stack(e));
      }
    });
  }

  @Override
  public Map<String, Object> getPackageJSON() {
    return packageJSON;
  }

  @Override
  public Object getResourceByKey(String key) {
    if (registeredResources.contains(key)) {
      Object resource = resources.get(key);
      if (resource == null) {
        throw new IllegalStateException("Could not find in-memory resource with key: " + key);
      }
      return resource;
    }
    throw new IllegalStateException("Unregistered resource key: " + key);
  }
}
