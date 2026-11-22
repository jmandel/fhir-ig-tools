package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import java.util.List;

// port of sushi/src/fshtypes/Logical.ts (SUSHI 3.21.1)
/** rules: LrRule only. */
public class Logical extends FshStructure {
  public List<String> characteristics = new ArrayList<>();

  public Logical(String name) {
    super(name);
    // Init the parent to 'Base' (can be overridden with Parent:).
    this.parent = "Base";
  }

  @Override public String constructorName() {
    return "Logical";
  }

  @Override public String metadataToFSH() {
    String sdMetadata = super.metadataToFSH();
    if (!characteristics.isEmpty()) {
      List<String> cs = new ArrayList<>();
      for (String c : characteristics) cs.add("#" + c);
      return sdMetadata + Common.EOL + "Characteristics: " + String.join(", ", cs);
    }
    return sdMetadata;
  }
}
