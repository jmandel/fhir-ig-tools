package org.hl7.fhir.sushi.fhirtypes;

// port of sushi/src/fhirtypes/InstanceDefinition.ts -- CONTRACT STUB, owned by WP-5 (instances).
// TS InstanceDefinition is a dynamic JS object (arbitrary FHIR properties added by path) plus _instanceMeta.
// Java: an insertion-ordered JS object (org.hl7.fhir.sushi.js.JsObject) plus typed _instanceMeta.
public class InstanceDefinition extends org.hl7.fhir.sushi.js.JsObject {
  private static final long serialVersionUID = 1L;

  /** TS _instanceMeta: { name, title, description, usage, sdKind, sdType, instanceOfUrl } */
  public static class InstanceMeta {
    public String name;
    public String title;
    public String description;
    /** "Example" | "Definition" | "Inline" */
    public String usage;
    public String sdKind;
    public String sdType;
    public String instanceOfUrl;
  }

  public InstanceMeta _instanceMeta = new InstanceMeta();

  public String id() {
    Object v = get("id");
    return v instanceof String s ? s : null;
  }

  public String resourceType() {
    Object v = get("resourceType");
    return v instanceof String s ? s : null;
  }

  // TODO WP-5: fromJSON, toJSON (key order!), getFileName, setInstancePropertyByPath interplay
}
