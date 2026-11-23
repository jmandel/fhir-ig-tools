package org.hl7.fhir.sushi.fhirdefs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.hl7.fhir.sushi.fhirdefs.FindResourceInfoOptions.ByLoadOrder;
import org.hl7.fhir.sushi.fhirdefs.FindResourceInfoOptions.ByType;
import org.hl7.fhir.sushi.fhirdefs.FindResourceInfoOptions.SortBy;
import org.hl7.fhir.common.js.Json;

// port of fhir-package-loader 2.2.4 src/db/SQLJSPackageDB.ts (SQLite via sql.js) as plain in-memory tables
/**
 * The query semantics of SQLJSPackageDB, without SQLite: rows keep their insertion order (rowid); a key matches when
 * {@code id = key OR name = key OR url = key} (SQL equality: case-sensitive, NULL never matches); a {@code |version}
 * suffix adds {@code version = v}; types select by resourceType or, for the SD flavors, by sdFlavor; scope selects
 * {@code packageName[|packageVersion]}; ORDER BY is built from the sort list ({@code byType(t1..tn)} puts rows whose
 * resourceType/sdFlavor equals t1 first, then t2, ..., then the rest; {@code byLoadOrder} sorts by rowid), default
 * rowid ASC. The hash maps below are lookup indexes only; results are always ordered by rowid/sort, never by hash
 * iteration.
 */
public class InMemoryPackageDB implements PackageDB {
  static final Set<String> SD_FLAVORS = Set.of("Extension", "Logical", "Profile", "Resource", "Type");

  private final List<PackageInfo> packages = new ArrayList<>();
  private final List<ResourceInfo> resources = new ArrayList<>();
  // indexes (rowid lists, ascending)
  private final Map<String, List<ResourceInfo>> byKey = new HashMap<>();
  private final Map<String, List<ResourceInfo>> byPackage = new HashMap<>();

  @Override
  public void clear() {
    packages.clear();
    resources.clear();
    byKey.clear();
    byPackage.clear();
  }

  @Override
  public void optimize() {
    // nothing to do: SQLite's PRAGMA optimize has no observable effect on results
  }

  @Override
  public void savePackageInfo(PackageInfo info) {
    PackageInfo row = new PackageInfo(info.name, info.version, truthy(info.packagePath), truthy(info.packageJSONPath));
    row.rowid = packages.size() + 1;
    packages.add(row);
  }

  @Override
  public void saveResourceInfo(ResourceInfo info) {
    // SQLJSPackageDB binds only truthy values; everything else stays NULL
    ResourceInfo r = new ResourceInfo();
    r.resourceType = info.resourceType;
    r.id = truthy(info.id);
    r.url = truthy(info.url);
    r.name = truthy(info.name);
    r.version = truthy(info.version);
    if ("StructureDefinition".equals(info.resourceType)) {
      r.sdKind = truthy(info.sdKind);
      r.sdDerivation = truthy(info.sdDerivation);
      r.sdType = truthy(info.sdType);
      r.sdBaseDefinition = truthy(info.sdBaseDefinition);
      r.sdAbstract = info.sdAbstract;
      // stored as JSON text and parsed back on read (so e.g. undefined entries come back as null)
      if (info.sdImposeProfiles != null && !info.sdImposeProfiles.isEmpty()) {
        r.sdImposeProfiles = roundTrip(info.sdImposeProfiles);
      }
      if (info.sdCharacteristics != null && !info.sdCharacteristics.isEmpty()) {
        r.sdCharacteristics = roundTrip(info.sdCharacteristics);
      }
      r.sdFlavor = truthy(info.sdFlavor);
    }
    r.packageName = truthy(info.packageName);
    r.packageVersion = truthy(info.packageVersion);
    r.resourcePath = truthy(info.resourcePath);
    r.rowid = resources.size() + 1;
    resources.add(r);
    if (r.id != null) index(byKey, r.id, r);
    if (r.name != null && !r.name.equals(r.id)) index(byKey, r.name, r);
    if (r.url != null && !r.url.equals(r.id) && !r.url.equals(r.name)) index(byKey, r.url, r);
    index(byPackage, r.packageName + "\u0000" + r.packageVersion, r);
  }

  private static void index(Map<String, List<ResourceInfo>> m, String k, ResourceInfo r) {
    m.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> roundTrip(List<Object> l) {
    return (List<Object>) Json.parse(Json.stringify(l));
  }

  private static String truthy(String s) {
    return s == null || s.isEmpty() ? null : s;
  }

  @Override
  public List<PackageInfo> findPackageInfos(String name) {
    List<PackageInfo> res = new ArrayList<>();
    for (PackageInfo p : packages) {
      if ("*".equals(name) || (name != null && name.equals(p.name))) res.add(p.copy());
    }
    return res;
  }

  @Override
  public PackageInfo findPackageInfo(String name, String version) {
    for (PackageInfo p : packages) {
      if (name != null && name.equals(p.name) && version != null && version.equals(p.version)) return p.copy();
    }
    return null;
  }

  @Override
  public List<ResourceInfo> findResourceInfos(String key, FindResourceInfoOptions options) {
    if (options == null) options = FindResourceInfoOptions.NONE;
    if (key == null) key = "";
    // key.split('|'): first part is the key, the rest (re-joined) the version
    int bar = key.indexOf('|');
    String keyText = bar < 0 ? key : key.substring(0, bar);
    String keyVersion = bar < 0 ? null : key.substring(bar + 1);

    // candidate rows (ascending rowid)
    List<ResourceInfo> candidates;
    if ("*".equals(keyText)) {
      candidates = resources;
    } else {
      candidates = byKey.getOrDefault(keyText, List.of());
    }
    String scopeName = null, scopeVersion = null;
    if (options.scope() != null && !options.scope().isEmpty()) {
      int sb = options.scope().indexOf('|');
      scopeName = sb < 0 ? options.scope() : options.scope().substring(0, sb);
      scopeVersion = sb < 0 ? null : options.scope().substring(sb + 1);
      if ("*".equals(keyText) && scopeVersion != null) {
        candidates = byPackage.getOrDefault(scopeName + "\u0000" + scopeVersion, List.of());
      }
    }
    List<String> types = options.type();
    Set<String> rtTypes = new TreeSet<>(), flavorTypes = new TreeSet<>();
    if (types != null) {
      for (String t : types) (SD_FLAVORS.contains(t) ? flavorTypes : rtTypes).add(t);
    }
    boolean hasTypes = types != null && !types.isEmpty();

    List<ResourceInfo> matches = new ArrayList<>();
    for (ResourceInfo r : candidates) {
      if (keyVersion != null && !keyVersion.equals(r.version)) continue;
      if (hasTypes) {
        boolean ok;
        if (!rtTypes.isEmpty()) {
          ok = rtTypes.contains(r.resourceType) || (r.sdFlavor != null && flavorTypes.contains(r.sdFlavor));
        } else {
          ok = "StructureDefinition".equals(r.resourceType) && r.sdFlavor != null && flavorTypes.contains(r.sdFlavor);
        }
        if (!ok) continue;
      }
      if (scopeName != null) {
        if (!scopeName.equals(r.packageName)) continue;
        if (scopeVersion != null && !scopeVersion.equals(r.packageVersion)) continue;
      }
      matches.add(r);
    }

    matches.sort(comparator(options.sort()));
    int limit = options.limit() == null ? 0 : options.limit();
    List<ResourceInfo> res = new ArrayList<>();
    for (ResourceInfo r : matches) {
      if (limit > 0 && res.size() >= limit) break;
      res.add(r.copy());
    }
    return res;
  }

  private static Comparator<ResourceInfo> comparator(List<SortBy> sort) {
    Comparator<ResourceInfo> byRowAsc = Comparator.comparingInt(r -> r.rowid);
    if (sort == null) return byRowAsc;
    Comparator<ResourceInfo> c = (a, b) -> 0;
    for (SortBy s : sort) {
      if (s instanceof ByLoadOrder lo) {
        c = c.thenComparing(lo.ascending() ? byRowAsc : byRowAsc.reversed());
      } else if (s instanceof ByType bt) {
        for (String t : bt.types()) {
          boolean flavor = SD_FLAVORS.contains(t);
          // `(field = :t OR NULL) DESC`: rows where field = t (value 1) before the rest (NULL)
          c = c.thenComparing((a, b) -> Boolean.compare(matchesType(b, t, flavor), matchesType(a, t, flavor)));
        }
      }
    }
    // SQLite gives no order for complete ties; in practice the scan is in rowid order
    return c.thenComparing(byRowAsc);
  }

  private static boolean matchesType(ResourceInfo r, String t, boolean flavor) {
    return t.equals(flavor ? r.sdFlavor : r.resourceType);
  }

  @Override
  public ResourceInfo findResourceInfo(String key, FindResourceInfoOptions options) {
    FindResourceInfoOptions o = options == null ? FindResourceInfoOptions.NONE : options;
    List<ResourceInfo> res = findResourceInfos(key, o.withLimit(1));
    return res.isEmpty() ? null : res.get(0);
  }

  @Override
  public PackageStats getPackageStats(String name, String version) {
    if (findPackageInfo(name, version) == null) return null;
    int count = byPackage.getOrDefault(name + "\u0000" + version, List.of()).size();
    return new PackageStats(name, version, count);
  }
}
