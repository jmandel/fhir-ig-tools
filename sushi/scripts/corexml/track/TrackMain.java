import java.util.Map;
import org.hl7.fhir.common.js.XmlTrack;
import org.hl7.fhir.sushi.fhirdefs.DiskBasedVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.FhirXml;

/** Harness only: runs SUSHI (App.main) with the default XML conversion, its results labelled for XmlTrack. */
public class TrackMain {
  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    DiskBasedVirtualPackage.setXmlConverter((xml, fhirVersion, partial) -> {
      Object o = FhirXml.xmlToObj(xml, fhirVersion, partial);
      if (!(o instanceof Map<?, ?> m)) return o;
      return new XmlTrack.Root(m.get("resourceType") + "/" + m.get("id"), (Map<String, Object>) m);
    });
    org.hl7.fhir.sushi.App.main(args);
  }
}
