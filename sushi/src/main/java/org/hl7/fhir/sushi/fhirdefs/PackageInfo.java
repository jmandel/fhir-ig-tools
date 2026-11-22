package org.hl7.fhir.sushi.fhirdefs;

// port of fhir-package-loader 2.2.4 src/package/PackageInfo.ts (a row of SQLJSPackageDB's `package` table)
/** Fields are null where the SQL column is NULL. */
public class PackageInfo {
  /** load order (SQL rowid, 1-based) */
  public int rowid;
  public String name;
  public String version;
  public String packagePath;
  public String packageJSONPath;

  public PackageInfo() {}

  public PackageInfo(String name, String version, String packagePath, String packageJSONPath) {
    this.name = name;
    this.version = version;
    this.packagePath = packagePath;
    this.packageJSONPath = packageJSONPath;
  }

  PackageInfo copy() {
    PackageInfo p = new PackageInfo(name, version, packagePath, packageJSONPath);
    p.rowid = rowid;
    return p;
  }

  @Override
  public String toString() {
    return name + "#" + version;
  }
}
