package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import java.util.List;

// port of sushi/src/fshtypes/Extension.ts (SUSHI 3.21.1)
/** rules: SdRule only. */
public class Extension extends FshStructure {
  public List<ExtensionContext> contexts = new ArrayList<>();

  public Extension(String name) {
    super(name);
    // Init the parent to 'Extension', as this is what 99% of extensions do.
    this.parent = "Extension";
  }

  @Override public String constructorName() {
    return "Extension";
  }

  @Override public String metadataToFSH() {
    String sdMetadata = super.metadataToFSH();
    List<String> vals = new ArrayList<>();
    for (ExtensionContext c : contexts) {
      vals.add(c.isQuoted ? "\"" + Common.fshifyString(c.value) + "\"" : c.value);
    }
    String contextValue = String.join(", ", vals);
    if (!contextValue.isEmpty()) {
      return sdMetadata + Common.EOL + "Context: " + contextValue;
    }
    return sdMetadata;
  }
}
