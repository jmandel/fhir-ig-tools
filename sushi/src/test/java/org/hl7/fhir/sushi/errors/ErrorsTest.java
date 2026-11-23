package org.hl7.fhir.sushi.errors;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.fshtypes.VsOperator;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.common.js.Json;
import org.junit.jupiter.api.Test;

/** Message text, class name, specReferences of every error class vs TS (scripts/oracle/errors-vectors.js). */
class ErrorsTest {
  record T(String getCode, List<String> getProfile, List<String> getTargetProfile)
      implements InvalidTypeError.AllowedType {}

  static Map<String, Object> obj(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  static Map<String, RuntimeException> cases() {
    SourceInfo si = new SourceInfo("a.fsh", new TextLocation(1, 1, 2, 3));
    Map<String, RuntimeException> c = new LinkedHashMap<>();
    c.put("AbstractInstanceOf", new AbstractInstanceOfError("Inst", "DomainResource", si));
    c.put("BindingStrength", new BindingStrengthError("required", "example"));
    c.put("CannotResolvePath", new CannotResolvePathError("foo.bar"));
    c.put("ClosedSlicing", new ClosedSlicingError("Observation.component", List.of("a", "b")));
    c.put("CodeAndSystemMismatch", new CodeAndSystemMismatchError(obj("code", "mm", "system", "s1"), obj("code", "cm")));
    c.put("CodedTypeNotFound", new CodedTypeNotFoundError(List.of("boolean", "integer")));
    c.put("ConstrainingCardinality", new ConstrainingCardinalityError(0, "1", 2, "*"));
    c.put("DuplicateSlice", new DuplicateSliceError("SD", "el", "sl"));
    c.put("ElementAlreadyDefined", new ElementAlreadyDefinedError("LM.x", "LM"));
    c.put("FixedToPattern", new FixedToPatternError("fixedCode"));
    c.put("InstanceOfNotDefined", new InstanceOfNotDefinedError("Inst", "Foo", si));
    c.put("InvalidCanonicalUrl", new InvalidCanonicalUrlError("Ent"));
    c.put("InvalidCardinality", new InvalidCardinalityError(3, "2"));
    c.put("InvalidChoiceTypeRulePath", new InvalidChoiceTypeRulePathError(new CardRule("value")));
    c.put("InvalidDateTime", new InvalidDateTimeError("2020-13"));
    c.put("InvalidElementAccess", new InvalidElementAccessError("snapshot"));
    c.put("InvalidElementForSlicing", new InvalidElementForSlicingError("Patient.gender"));
    c.put("InvalidExtensionParent", new InvalidExtensionParentError("Ext", "Patient", si));
    c.put("InvalidFHIRId", new InvalidFHIRIdError("bad id"));
    c.put("InvalidLogicalParent", new InvalidLogicalParentError("LM", "Foo", si));
    c.put("InvalidMapping", new InvalidMappingError());
    c.put("InvalidMustSupport", new InvalidMustSupportError("SD", "el"));
    c.put("InvalidPeriod", new InvalidPeriodError(obj("start", "2020", "end", "2019")));
    c.put("InvalidPeriodUndef", new InvalidPeriodError(obj("start", "2020")));
    c.put("InvalidRangeValue", new InvalidRangeValueError(1.5, 0.25));
    c.put("InvalidResourceParent", new InvalidResourceParentError("R", "Patient", si));
    c.put("InvalidResourceType", new InvalidResourceTypeError("Patient", "Observation"));
    c.put("InvalidSumOfSliceMins", new InvalidSumOfSliceMinsError(5, "3", "Obs.component"));
    c.put("InvalidTypeAccess", new InvalidTypeAccessError());
    c.put("InvalidTypeNone", new InvalidTypeError("Foo", List.of()));
    c.put("InvalidTypeMixed", new InvalidTypeError("Foo", List.of(new T("Condition", null, null),
        new T("Procedure", List.of("http://e.org/SP"), null),
        new T("Reference", null, List.of("http://a/Patient", "http://a/Pract")),
        new T("CodeableReference", null, List.of("http://a/X")), new T("canonical", null, null),
        new T("Quantity", List.of("p1", "p2"), null))));
    c.put("InvalidUri", new InvalidUriError("not a uri"));
    c.put("MismatchedBindingTypeCS", new MismatchedBindingTypeError("X", "code", "CodeSystem"));
    c.put("MismatchedBindingTypeVS", new MismatchedBindingTypeError("X", "code", "ValueSet"));
    c.put("MismatchedTypeNum", new MismatchedTypeError("Number", 1.5e-7, "string"));
    c.put("MismatchedTypeBool", new MismatchedTypeError("Boolean", true, "string"));
    c.put("MismatchedTypeStr", new MismatchedTypeError("String", "hello", "boolean"));
    c.put("MissingSnapshot", new MissingSnapshotError("http://x"));
    c.put("MultipleStandardsStatus", new MultipleStandardsStatusError("el"));
    c.put("NarrowingRootCardinality", new NarrowingRootCardinalityError("root", "sl", 0, "1", 1, "2"));
    c.put("NoSingleType", new NoSingleTypeError("Code"));
    c.put("NonAbstractParentOfSpecialization", new NonAbstractParentOfSpecializationError("Spec", "Parent"));
    c.put("ParentDeclaredAsIdUrl",
        new ParentDeclaredAsIdError("Profile", "N", "i", si, "http://hl7.org/fhir/StructureDefinition/i"));
    c.put("ParentDeclaredAsIdNoUrl", new ParentDeclaredAsIdError("Profile", "N", "i", si, null));
    c.put("ParentDeclaredAsNameUrl", new ParentDeclaredAsNameError("Extension", "N", si, "http://u"));
    c.put("ParentDeclaredAsNameNoUrl", new ParentDeclaredAsNameError("Extension", "N", si, ""));
    c.put("ParentNotDefined", new ParentNotDefinedError("C", "P", si));
    c.put("ParentNotProvided", new ParentNotProvidedError("D", si));
    c.put("RequiredMetadata", new RequiredMetadataError("url", "Mapping", "M"));
    c.put("SliceTypeRemoval", new SliceTypeRemovalError("value[x]", "value[x]:valueString"));
    c.put("SlicingDefinition", new SlicingDefinitionError("ordered", true, false));
    c.put("SlicingNotDefined", new SlicingNotDefinedError("Obs.component", "sys"));
    c.put("TypeNotFound", new TypeNotFoundError("Foo"));
    c.put("UnitMismatch", new UnitMismatchError(obj("unit", "mm"), obj("unit", "cm")));
    c.put("ValidationErrorDefault", new ValidationError("is bad", "a.b"));
    c.put("ValidationErrorWarn", new ValidationError("is iffy", "a.c", "warn"));
    c.put("ValueAlreadyAssigned", new ValueAlreadyAssignedError(2.0, "integer", 1.0));
    c.put("ValueConflictsWithClosedSlicing", new ValueConflictsWithClosedSlicingError("x"));
    c.put("ValueSetCompose", new ValueSetComposeError("VS"));
    c.put("ValueSetFilterMissingValue", new ValueSetFilterMissingValueError(VsOperator.IS_A));
    c.put("ValueSetFilterOperator", new ValueSetFilterOperatorError("nope"));
    c.put("ValueSetFilterValueType", new ValueSetFilterValueTypeError(VsOperator.EQUALS, List.of("string")));
    c.put("ValueSetFilterValueTypeAllowed",
        new ValueSetFilterValueTypeError(VsOperator.EXISTS, List.of("boolean", "code"), List.of("true", "false")));
    return c;
  }

  @SuppressWarnings("unchecked")
  @Test void messagesMatchTsOracle() throws Exception {
    Map<String, Object> vectors = (Map<String, Object>) Json.parse(
        Files.readString(Path.of(getClass().getResource("/errors/vectors.json").toURI())));
    Map<String, RuntimeException> cases = cases();
    assertEquals(vectors.keySet(), cases.keySet());
    for (var e : cases.entrySet()) {
      Map<String, Object> v = (Map<String, Object>) vectors.get(e.getKey());
      RuntimeException ex = e.getValue();
      assertEquals(v.get("name"), ex.getClass().getSimpleName(), e.getKey());
      assertEquals(v.get("message"), ex.getMessage(), e.getKey());
      Object refs = v.get("specReferences");
      if (refs == null) assertFalse(ex instanceof Annotated, e.getKey());
      else assertEquals(refs, ((Annotated) ex).getSpecReferences(), e.getKey());
      if (v.get("severity") != null) assertEquals(v.get("severity"), ((ValidationError) ex).severity);
    }
  }

  @Test void everyErrorClassIsCovered() throws Exception {
    // 54 error classes (TS errors/*.ts minus Annotated, WithSource, index)
    assertEquals(54, cases().values().stream().map(e -> e.getClass()).distinct().count());
  }
}
