package org.hl7.fhir.sushi.utils;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/utils/FishingUtils.test.ts (SUSHI 3.21.1)
// jest.spyOn(...).mockReturnValueOnce(...) -> a fisher/tank subclass that pops queued results and counts calls.
@DisplayName("FishingUtils")
class FishingUtilsTest {
  static final SourceInfo someSourceInfo = new SourceInfo("SomeFile.fsh", new TextLocation(12, 0, 12, 35));

  /** The spied fisher: queued return values (empty queue = undefined), call counts. */
  static final class SpyFisher extends TestFisher {
    final Deque<Object> fhirResults = new ArrayDeque<>();
    final Deque<Object> metadataResults = new ArrayDeque<>();
    int fishForFHIRCalls;
    int fishForMetadataCalls;

    SpyFisher(FSHTank tank, Package pkg) {
      super(tank, null, pkg);
    }

    @Override public Object fishForFHIR(String item, FishableType... types) {
      fishForFHIRCalls++;
      return fhirResults.isEmpty() ? null : unwrap(fhirResults.poll());
    }

    @Override public Metadata fishForMetadata(String item, FishableType... types) {
      fishForMetadataCalls++;
      return metadataResults.isEmpty() ? null : (Metadata) unwrap(metadataResults.poll());
    }
  }

  static final class SpyTank extends FSHTank {
    final Deque<Object> results = new ArrayDeque<>();
    int calls;

    SpyTank(List<FSHDocument> docs, Configuration config) {
      super(docs, config);
    }

    @Override public FshEntity fish(String item, FishableType... types) {
      calls++;
      return results.isEmpty() ? null : (FshEntity) unwrap(results.poll());
    }
  }

  /** ArrayDeque can't hold null: UNDEF stands for mockReturnValueOnce(undefined). */
  static final Object UNDEF = new Object();

  static Object unwrap(Object o) {
    return o == UNDEF ? null : o;
  }

  static Metadata md(String id, String resourceType, String version) {
    Metadata m = new Metadata();
    m.id = id;
    m.resourceType = resourceType;
    m.version = version;
    return m;
  }

  SpyTank tank;
  SpyFisher fisher;

  @BeforeEach
  void setUp() {
    // TS beforeAll builds these once; the spies are reset before each test, which a fresh instance equals
    FSHDocument doc = new FSHDocument("fileName");
    tank = new SpyTank(List.of(doc), minimalConfig());
    Package pkg = new Package(tank.config);
    fisher = new SpyFisher(tank, pkg);
    loggerSpy.reset();
  }

  @Nested
  @DisplayName("#fishForFHIRBestVersion")
  class FishForFHIRBestVersion {
    @Test
    @DisplayName("FishingUtils #fishForFHIRBestVersion should only fish once if result is found when no version is provided")
    void t1() {
      fisher.fhirResults.add(new JsObject().with("resourceType", "mock"));
      FishingUtils.fishForFHIRBestVersion(fisher, "item", null);
      assertEquals(1, fisher.fishForFHIRCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForFHIRBestVersion should only fish once if result is found when version is provided")
    void t2() {
      fisher.fhirResults.add(new JsObject().with("resourceType", "mock"));
      FishingUtils.fishForFHIRBestVersion(fisher, "item|1.0", null);
      assertEquals(1, fisher.fishForFHIRCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForFHIRBestVersion should only fish once if result is not found and no version is provided")
    void t3() {
      fisher.fhirResults.add(UNDEF);
      FishingUtils.fishForFHIRBestVersion(fisher, "item", null);
      assertEquals(1, fisher.fishForFHIRCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForFHIRBestVersion should fish twice if result is not found when version is provided and not log a warning when version matches")
    void t4() {
      fisher.fhirResults.add(UNDEF);
      fisher.fhirResults.add(new JsObject().with("resourceType", "mock").with("version", "1.0"));
      FishingUtils.fishForFHIRBestVersion(fisher, "item|1.0", null);
      assertEquals(2, fisher.fishForFHIRCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForFHIRBestVersion should fish twice if result is not found when version is provided and log a warning if different version is found")
    void t5() {
      fisher.fhirResults.add(UNDEF);
      fisher.fhirResults.add(new JsObject().with("resourceType", "mock").with("version", "2.0"));
      FishingUtils.fishForFHIRBestVersion(fisher, "item|1.0", null);
      assertEquals(2, fisher.fishForFHIRCalls);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertMatches("item\\|1\\.0 was requested, but SUSHI found item\\|2\\.0", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("FishingUtils #fishForFHIRBestVersion should fish twice if result is not found when version is provided and log a warning with source info if different version is found")
    void t6() {
      fisher.fhirResults.add(UNDEF);
      fisher.fhirResults.add(new JsObject().with("resourceType", "mock").with("version", "2.0"));
      FishingUtils.fishForFHIRBestVersion(fisher, "item|1.0", someSourceInfo);
      assertEquals(2, fisher.fishForFHIRCalls);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertMatches("item\\|1\\.0 was requested, but SUSHI found item\\|2\\.0.*File: SomeFile\\.fsh.*Line: 12\\D*", "s",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("FishingUtils #fishForFHIRBestVersion should fish twice if result is not found when version is provided and not log a warning when provided version is null")
    void t7() {
      fisher.fhirResults.add(UNDEF);
      fisher.fhirResults.add(new JsObject().with("resourceType", "mock").with("version", "1.0"));
      FishingUtils.fishForFHIRBestVersion(fisher, "item|", null); // no real version provided
      assertEquals(2, fisher.fishForFHIRCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForFHIRBestVersion should fish twice if result is not found when version is provided and not log a warning when found version is null")
    void t8() {
      fisher.fhirResults.add(UNDEF);
      fisher.fhirResults.add(new JsObject().with("resourceType", "mock")); // no version on result
      FishingUtils.fishForFHIRBestVersion(fisher, "item|1.0", null);
      assertEquals(2, fisher.fishForFHIRCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }
  }

  @Nested
  @DisplayName("#fishForMetadataBestVersion")
  class FishForMetadataBestVersion {
    @Test
    @DisplayName("FishingUtils #fishForMetadataBestVersion should only fishForMetadata once if result is found when no version is provided")
    void t1() {
      fisher.metadataResults.add(md("mock", null, null));
      FishingUtils.fishForMetadataBestVersion(fisher, "item", null);
      assertEquals(1, fisher.fishForMetadataCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForMetadataBestVersion should only fishForMetadata once if result is found when version is provided")
    void t2() {
      fisher.metadataResults.add(md("mock", null, null));
      FishingUtils.fishForMetadataBestVersion(fisher, "item|1.0", null);
      assertEquals(1, fisher.fishForMetadataCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForMetadataBestVersion should only fishForMetadata once if result is not found and no version is provided")
    void t3() {
      fisher.metadataResults.add(UNDEF);
      FishingUtils.fishForMetadataBestVersion(fisher, "item", null);
      assertEquals(1, fisher.fishForMetadataCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForMetadataBestVersion should fishForMetadata twice if result is not found when version is provided and not log a warning when version matches")
    void t4() {
      fisher.metadataResults.add(UNDEF);
      fisher.metadataResults.add(md("mock", null, "1.0"));
      FishingUtils.fishForMetadataBestVersion(fisher, "item|1.0", null);
      assertEquals(2, fisher.fishForMetadataCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForMetadataBestVersion should fishForMetadata twice if result is not found when version is provided and log a warning if different version is found")
    void t5() {
      fisher.metadataResults.add(UNDEF);
      fisher.metadataResults.add(md("mock", null, "2.0"));
      FishingUtils.fishForMetadataBestVersion(fisher, "item|1.0", null);
      assertEquals(2, fisher.fishForMetadataCalls);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertMatches("item\\|1\\.0 was requested, but SUSHI found item\\|2\\.0", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("FishingUtils #fishForMetadataBestVersion should fishForMetadata twice if result is not found when version is provided and log a warning with source info if different version is found")
    void t6() {
      fisher.metadataResults.add(UNDEF);
      fisher.metadataResults.add(md("mock", null, "2.0"));
      FishingUtils.fishForMetadataBestVersion(fisher, "item|1.0", someSourceInfo);
      assertEquals(2, fisher.fishForMetadataCalls);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertMatches("item\\|1\\.0 was requested, but SUSHI found item\\|2\\.0.*File: SomeFile\\.fsh.*Line: 12\\D*", "s",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("FishingUtils #fishForMetadataBestVersion should fish twice if result is not found when version is provided and not log a warning when provided version is null")
    void t7() {
      fisher.metadataResults.add(UNDEF);
      fisher.metadataResults.add(md(null, "mock", "1.0"));
      FishingUtils.fishForMetadataBestVersion(fisher, "item|", null); // no real version provided
      assertEquals(2, fisher.fishForMetadataCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishForMetadataBestVersion should fish twice if result is not found when version is provided and not log a warning when found version is null")
    void t8() {
      fisher.metadataResults.add(UNDEF);
      fisher.metadataResults.add(md(null, "mock", null)); // no version on result
      FishingUtils.fishForMetadataBestVersion(fisher, "item|1.0", null);
      assertEquals(2, fisher.fishForMetadataCalls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }
  }

  @Nested
  @DisplayName("#fishInTankBestVersion")
  class FishInTankBestVersion {
    Profile mockProfileWithVersion(String version) {
      Profile mockProfile = new Profile("mock");
      CaretValueRule mockProfileVersion = new CaretValueRule("");
      mockProfileVersion.caretPath = "version";
      mockProfileVersion.value = version;
      mockProfile.rules.add(mockProfileVersion);
      return mockProfile;
    }

    @Test
    @DisplayName("FishingUtils #fishInTankBestVersion should only fish in tank once if result is found when no version is provided")
    void t1() {
      tank.results.add(new Profile("mock"));
      FishingUtils.fishInTankBestVersion(tank, "item", null);
      assertEquals(1, tank.calls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishInTankBestVersion should only fish in tank once if result is found when version is provided")
    void t2() {
      tank.results.add(new Profile("mock"));
      FishingUtils.fishInTankBestVersion(tank, "item|1.0", null);
      assertEquals(1, tank.calls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishInTankBestVersion should only fish in tank once if result is not found and no version is provided")
    void t3() {
      tank.results.add(UNDEF);
      FishingUtils.fishInTankBestVersion(tank, "item", null);
      assertEquals(1, tank.calls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishInTankBestVersion should fish in tank twice if result is not found when version is provided and not log a warning when version matches")
    void t4() {
      tank.results.add(UNDEF);
      tank.results.add(mockProfileWithVersion("1.0"));
      FishingUtils.fishInTankBestVersion(tank, "item|1.0", null);
      assertEquals(2, tank.calls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishInTankBestVersion should fish in tank twice if result is not found when version is provided and log a warning if different version is found")
    void t5() {
      tank.results.add(UNDEF);
      tank.results.add(mockProfileWithVersion("2.0"));
      FishingUtils.fishInTankBestVersion(tank, "item|1.0", null);
      assertEquals(2, tank.calls);
      assertEquals(1, loggerSpy.getAllMessages().size());
      // (TS regex /item|1\.0 was requested, but SUSHI found item|2\.0/ -- unescaped | is alternation; kept as is)
      assertMatches("item|1\\.0 was requested, but SUSHI found item|2\\.0", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("FishingUtils #fishInTankBestVersion should fish in tank twice if result is not found when version is provided and log a warning with source info if different version is found")
    void t6() {
      tank.results.add(UNDEF);
      tank.results.add(mockProfileWithVersion("2.0"));
      FishingUtils.fishInTankBestVersion(tank, "item|1.0", someSourceInfo);
      assertEquals(2, tank.calls);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertMatches("item\\|1\\.0 was requested, but SUSHI found item\\|2\\.0.*File: SomeFile\\.fsh.*Line: 12\\D*", "s",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("FishingUtils #fishInTankBestVersion should fish twice if result is not found when version is provided and not log a warning when provided version is null")
    void t7() {
      tank.results.add(UNDEF);
      tank.results.add(new Profile("mock"));
      FishingUtils.fishInTankBestVersion(tank, "item|", null); // no version provided
      assertEquals(2, tank.calls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("FishingUtils #fishInTankBestVersion should fish twice if result is not found when version is provided and not log a warning when found version is null")
    void t8() {
      Configuration configWithoutVersion = minimalConfig();
      configWithoutVersion.version = null;
      SpyTank tankWithoutVersion = new SpyTank(List.of(new FSHDocument("no-version")), configWithoutVersion);
      tankWithoutVersion.results.add(UNDEF);
      // no version on result (not set with rule or in config)
      tankWithoutVersion.results.add(new Profile("mock"));
      FishingUtils.fishInTankBestVersion(tankWithoutVersion, "item|1.0", null);
      assertEquals(2, tankWithoutVersion.calls);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }
  }
}
