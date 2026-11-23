package org.hl7.fhir.sushi.utils;

import java.util.List;

// port of the `Metadata` interface in sushi/src/utils/Fishable.ts. Fields are null when TS leaves them undefined.
public class Metadata {
  public String id;
  public String name;
  public String sdType;
  public String resourceType;
  public String url;
  public String parent;
  /** String or org.hl7.fhir.sushi.fshtypes.FshCanonical entries */
  public List<Object> imposeProfiles;
  public Boolean _abstract; // TS `abstract`
  public String version;
  /** Instance['usage']: "Example" | "Definition" | "Inline" */
  public String instanceUsage;
  public Boolean canBeTarget;
  public Boolean canBind;
  public String resourcePath;
}
