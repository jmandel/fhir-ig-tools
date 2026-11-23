package org.hl7.fhir.sushi.ig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.sushi.fhirdefs.DiskBasedVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.LoadStatus;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.utils.Fs;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.NodePath;
import org.hl7.fhir.sushi.utils.Processing;

// port of sushi/src/ig/predefinedResources.ts (SUSHI 3.21.1)
public final class PredefinedResources {
  private PredefinedResources() {}

  public static final String PREDEFINED_PACKAGE_NAME = FHIRDefinitions.PREDEFINED_PACKAGE_NAME;
  public static final String PREDEFINED_PACKAGE_VERSION = FHIRDefinitions.PREDEFINED_PACKAGE_VERSION;

  private static final List<String> PATH_ENDS = List.of(
      "capabilities", "extensions", "models", "operations", "profiles", "resources", "vocabulary", "examples");

  /**
   * Gets the local resource directory paths corresponding to the typical locations in an IG as well as those
   * configured in the IG parameter path-resource. Only those directories that exist will be returned.
   *
   * @param resourceDir the path to the directory containing the resource subdirs
   * @param projectDir user's specified project directory (may be null)
   * @param configParameters config parameters (may be null) that may add resource paths (path-resource)
   */
  public static List<String> getPredefinedResourcePaths(String resourceDir, String projectDir,
      List<? extends Map<String, Object>> configParameters) {
    Set<String> predefinedResourcePaths = new LinkedHashSet<>();
    for (String pathEnd : PATH_ENDS) {
      String p = NodePath.join(resourceDir, pathEnd);
      if (Processing.exists(p)) predefinedResourcePaths.add(p);
    }
    if (configParameters != null && projectDir != null) {
      List<String> pathResources = new ArrayList<>();
      for (Map<String, Object> parameter : configParameters) {
        if (Js.truthy(parameter.get("value")) && "path-resource".equals(parameter.get("code"))) {
          pathResources.add(Js.toStr(parameter.get("value")));
        }
      }
      for (String pathResource : pathResources) {
        List<String> parts = new ArrayList<>();
        parts.add(projectDir);
        String trimmed = pathResource.endsWith("/*") ? pathResource.substring(0, pathResource.length() - 2)
            : pathResource;
        for (String s : trimmed.split("/", -1)) parts.add(s);
        String fullPath = NodePath.join(parts.toArray(String[]::new));
        if (Processing.exists(fullPath)) {
          predefinedResourcePaths.add(fullPath);
          // path-resource paths ending with /* should recursively include subfolders
          if (pathResource.endsWith("/*")) {
            addRecursiveChildFolders(fullPath, predefinedResourcePaths);
          }
        }
      }
    }
    return new ArrayList<>(predefinedResourcePaths);
  }

  public static List<String> getPredefinedResourcePaths(String resourceDir) {
    return getPredefinedResourcePaths(resourceDir, null, null);
  }

  private static void addRecursiveChildFolders(String folderPath, Set<String> out) {
    if (!Fs.isDirectory(folderPath)) return;
    // fs.readdirSync(withFileTypes): libuv scandir order; entry.isDirectory() is the dirent type (no symlink follow)
    for (String name : Processing.readdir(folderPath)) {
      if (Fs.isDirectoryNoFollow(Path.of(folderPath, name).toString())) {
        String childFolder = NodePath.resolve(folderPath, name);
        out.add(childFolder);
        addRecursiveChildFolders(childFolder, out);
      }
    }
  }

  /**
   * Loads predefined resources from the typical locations in an IG as well as those configured in the IG parameter
   * path-resource, as the virtual package sushi-local#LOCAL.
   */
  public static LoadStatus loadPredefinedResources(FHIRDefinitions defs, String resourceDir, String projectDir,
      List<? extends Map<String, Object>> configParameters) {
    List<String> localResourcePaths = getPredefinedResourcePaths(resourceDir, projectDir, configParameters);
    JsObject packageJSON = new JsObject();
    packageJSON.put("name", PREDEFINED_PACKAGE_NAME);
    packageJSON.put("version", PREDEFINED_PACKAGE_VERSION);
    return defs.loadVirtualPackage(new DiskBasedVirtualPackage(packageJSON, localResourcePaths,
        FshLogger.logger::logMessage, true /* support for logical instances */, true));
  }

  public static LoadStatus loadPredefinedResources(FHIRDefinitions defs, String resourceDir) {
    return loadPredefinedResources(defs, resourceDir, null, null);
  }
}
