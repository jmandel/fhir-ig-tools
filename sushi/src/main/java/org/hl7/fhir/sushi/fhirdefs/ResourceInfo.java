package org.hl7.fhir.sushi.fhirdefs;

import java.util.List;

// port of fhir-package-loader 2.2.4 src/package/ResourceInfo.ts (a row of SQLJSPackageDB's `resource` table)
/** Fields are null where the SQL column is NULL (SQLJSPackageDB only binds truthy values). */
public class ResourceInfo {
  /** load order (SQL rowid, 1-based) */
  public int rowid;
  public String resourceType;
  public String id;
  public String url;
  public String name;
  public String version;
  public String sdKind;
  public String sdDerivation;
  public String sdType;
  public String sdBaseDefinition;
  public Boolean sdAbstract;
  public List<Object> sdImposeProfiles;
  public List<Object> sdCharacteristics;
  /** Extension | Logical | Profile | Resource | Type, or null */
  public String sdFlavor;
  public String packageName;
  public String packageVersion;
  public String resourcePath;

  ResourceInfo copy() {
    ResourceInfo r = new ResourceInfo();
    r.rowid = rowid;
    r.resourceType = resourceType;
    r.id = id;
    r.url = url;
    r.name = name;
    r.version = version;
    r.sdKind = sdKind;
    r.sdDerivation = sdDerivation;
    r.sdType = sdType;
    r.sdBaseDefinition = sdBaseDefinition;
    r.sdAbstract = sdAbstract;
    r.sdImposeProfiles = sdImposeProfiles == null ? null : List.copyOf(sdImposeProfiles);
    r.sdCharacteristics = sdCharacteristics == null ? null : List.copyOf(sdCharacteristics);
    r.sdFlavor = sdFlavor;
    r.packageName = packageName;
    r.packageVersion = packageVersion;
    r.resourcePath = resourcePath;
    return r;
  }

  @Override
  public String toString() {
    return resourceType + "/" + id + " (" + url + ") from " + packageName + "#" + packageVersion;
  }
}
