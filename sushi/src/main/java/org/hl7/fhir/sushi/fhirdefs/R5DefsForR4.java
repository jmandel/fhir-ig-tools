package org.hl7.fhir.sushi.fhirdefs;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.js.Json;

// port of sushi/src/fhirdefs/R5DefsForR4/index.ts (SUSHI 3.21.1); the JSON files are copied verbatim into resources
public final class R5DefsForR4 {
  private R5DefsForR4() {}

  private static final String[] FILES = {
    // Base is needed as a parent of R4 logical models
    "StructureDefinition-Base.json",
    // The following resources are "Special Types" allowed to be instantiated, but not profiled, in R4
    // See: https://chat.fhir.org/#narrow/stream/215610-shorthand/topic/using.20R5.20resources.20in.20FSH/near/377870473
    "StructureDefinition-ActorDefinition.json",
    "StructureDefinition-Requirements.json",
    "StructureDefinition-SubscriptionTopic.json",
    "StructureDefinition-TestPlan.json",
    // CodeableReference is needed because the above "SpecialTypes" depend on it
    "StructureDefinition-CodeableReference.json",
    // DataType is needed because CodeableReference declares it as its baseDefinition
    "StructureDefinition-DataType.json"
  };

  /**
   * The definitions, each flagged {@code _timeTraveler: true} (so we know they're from the future!). Like the TS
   * module-level array, the same objects are shared by every user.
   */
  public static final List<Map<String, Object>> R5_DEFINITIONS_NEEDED_IN_R4 = load();

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> load() {
    List<Map<String, Object>> defs = new ArrayList<>();
    for (String f : FILES) {
      try (InputStream in = R5DefsForR4.class.getResourceAsStream("R5DefsForR4/" + f)) {
        if (in == null) throw new IOException("missing resource R5DefsForR4/" + f);
        Map<String, Object> def = (Map<String, Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        def.put("_timeTraveler", true);
        defs.add(def);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return Collections.unmodifiableList(defs);
  }
}
