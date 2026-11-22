package org.hl7.fhir.sushi.fhirtypes;

import java.util.IdentityHashMap;
import java.util.Map;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.utils.SanitizeFilename;

// port of sushi/src/fhirtypes/ImplementationGuide.ts (SUSHI 3.21.1)
/**
 * In TS, ImplementationGuide (and its parts: DependsOn, Global, Definition, Resource, Page, Parameter, Template,
 * Manifest) are only type aliases over plain objects that IGExporter builds and writes as is: the JSON key order is
 * exactly the assignment order (NO underscore reordering, unlike CodeSystem/ValueSet). So this is an ordered JS
 * object whose {@code toJSON()} is a plain deep copy.
 */
public class ImplementationGuide extends JsObject implements FhirResource, Js.DeepCloneable {
  private static final long serialVersionUID = 1L;

  public ImplementationGuide() {}

  public ImplementationGuide(Map<String, Object> m) {
    super(m);
  }

  @Override public Object toJSON() {
    JsObject c = new JsObject();
    for (Map.Entry<String, Object> e : entrySet()) c.put(e.getKey(), Js.cloneDeep(e.getValue()));
    return c;
  }

  /** "ImplementationGuide-{id}.json" (IGExporter writes the IG under this name). */
  @Override public String getFileName() {
    return SanitizeFilename.sanitize("ImplementationGuide-" + CodeSystem.idForFileName(this) + ".json", "-");
  }

  @Override public Object cloneDeep(IdentityHashMap<Object, Object> seen) {
    ImplementationGuide c = new ImplementationGuide();
    seen.put(this, c);
    for (Map.Entry<String, Object> e : entrySet()) c.put(e.getKey(), Js.cloneDeep(e.getValue(), seen));
    return c;
  }
}
