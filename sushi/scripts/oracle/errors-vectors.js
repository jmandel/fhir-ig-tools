// Node oracle for the error classes (WP-3): message text of every TS error class for fixed arguments.
// usage (reference image, VM): node scripts/oracle/errors-vectors.js /srv/ig/sushi-spike/sushi > src/test/resources/errors/vectors.json
// ErrorsTest constructs the same cases in Java (case ids must match).
const path = require('path');
const E = { ...require(path.join(process.argv[2], 'dist/errors')),
  ...require(path.join(process.argv[2], 'dist/errors/InstanceOfNotDefinedError')) }; // not in errors/index
const { CardRule } = require(path.join(process.argv[2], 'dist/fshtypes/rules'));
const { VsOperator } = require(path.join(process.argv[2], 'dist/fshtypes'));
const si = { file: 'a.fsh', location: { startLine: 1, startColumn: 1, endLine: 2, endColumn: 3 } };
const t = (code, profile, targetProfile) => ({ code, profile, targetProfile });
const cases = {
  AbstractInstanceOf: new E.AbstractInstanceOfError('Inst', 'DomainResource', si),
  BindingStrength: new E.BindingStrengthError('required', 'example'),
  CannotResolvePath: new E.CannotResolvePathError('foo.bar'),
  ClosedSlicing: new E.ClosedSlicingError('Observation.component', ['a', 'b']),
  CodeAndSystemMismatch: new E.CodeAndSystemMismatchError({ code: 'mm', system: 's1' }, { code: 'cm' }),
  CodedTypeNotFound: new E.CodedTypeNotFoundError(['boolean', 'integer']),
  ConstrainingCardinality: new E.ConstrainingCardinalityError(0, '1', 2, '*'),
  DuplicateSlice: new E.DuplicateSliceError('SD', 'el', 'sl'),
  ElementAlreadyDefined: new E.ElementAlreadyDefinedError('LM.x', 'LM'),
  FixedToPattern: new E.FixedToPatternError('fixedCode'),
  InstanceOfNotDefined: new E.InstanceOfNotDefinedError('Inst', 'Foo', si),
  InvalidCanonicalUrl: new E.InvalidCanonicalUrlError('Ent'),
  InvalidCardinality: new E.InvalidCardinalityError(3, '2'),
  InvalidChoiceTypeRulePath: new E.InvalidChoiceTypeRulePathError(new CardRule('value')),
  InvalidDateTime: new E.InvalidDateTimeError('2020-13'),
  InvalidElementAccess: new E.InvalidElementAccessError('snapshot'),
  InvalidElementForSlicing: new E.InvalidElementForSlicingError('Patient.gender'),
  InvalidExtensionParent: new E.InvalidExtensionParentError('Ext', 'Patient', si),
  InvalidFHIRId: new E.InvalidFHIRIdError('bad id'),
  InvalidLogicalParent: new E.InvalidLogicalParentError('LM', 'Foo', si),
  InvalidMapping: new E.InvalidMappingError(),
  InvalidMustSupport: new E.InvalidMustSupportError('SD', 'el'),
  InvalidPeriod: new E.InvalidPeriodError({ start: '2020', end: '2019' }),
  InvalidPeriodUndef: new E.InvalidPeriodError({ start: '2020' }),
  InvalidRangeValue: new E.InvalidRangeValueError(1.5, 0.25),
  InvalidResourceParent: new E.InvalidResourceParentError('R', 'Patient', si),
  InvalidResourceType: new E.InvalidResourceTypeError('Patient', 'Observation'),
  InvalidSumOfSliceMins: new E.InvalidSumOfSliceMinsError(5, '3', 'Obs.component'),
  InvalidTypeAccess: new E.InvalidTypeAccessError(),
  InvalidTypeNone: new E.InvalidTypeError('Foo', []),
  InvalidTypeMixed: new E.InvalidTypeError('Foo', [
    t('Condition'), t('Procedure', ['http://e.org/SP']), t('Reference', undefined, ['http://a/Patient', 'http://a/Pract']),
    t('CodeableReference', undefined, ['http://a/X']), t('canonical'), t('Quantity', ['p1', 'p2'])
  ]),
  InvalidUri: new E.InvalidUriError('not a uri'),
  MismatchedBindingTypeCS: new E.MismatchedBindingTypeError('X', 'code', 'CodeSystem'),
  MismatchedBindingTypeVS: new E.MismatchedBindingTypeError('X', 'code', 'ValueSet'),
  MismatchedTypeNum: new E.MismatchedTypeError('Number', 1.5e-7, 'string'),
  MismatchedTypeBool: new E.MismatchedTypeError('Boolean', true, 'string'),
  MismatchedTypeStr: new E.MismatchedTypeError('String', 'hello', 'boolean'),
  MissingSnapshot: new E.MissingSnapshotError('http://x'),
  MultipleStandardsStatus: new E.MultipleStandardsStatusError('el'),
  NarrowingRootCardinality: new E.NarrowingRootCardinalityError('root', 'sl', 0, '1', 1, '2'),
  NoSingleType: new E.NoSingleTypeError('Code'),
  NonAbstractParentOfSpecialization: new E.NonAbstractParentOfSpecializationError('Spec', 'Parent'),
  ParentDeclaredAsIdUrl: new E.ParentDeclaredAsIdError('Profile', 'N', 'i', si, 'http://hl7.org/fhir/StructureDefinition/i'),
  ParentDeclaredAsIdNoUrl: new E.ParentDeclaredAsIdError('Profile', 'N', 'i', si, undefined),
  ParentDeclaredAsNameUrl: new E.ParentDeclaredAsNameError('Extension', 'N', si, 'http://u'),
  ParentDeclaredAsNameNoUrl: new E.ParentDeclaredAsNameError('Extension', 'N', si, ''),
  ParentNotDefined: new E.ParentNotDefinedError('C', 'P', si),
  ParentNotProvided: new E.ParentNotProvidedError('D', si),
  RequiredMetadata: new E.RequiredMetadataError('url', 'Mapping', 'M'),
  SliceTypeRemoval: new E.SliceTypeRemovalError('value[x]', 'value[x]:valueString'),
  SlicingDefinition: new E.SlicingDefinitionError('ordered', true, false),
  SlicingNotDefined: new E.SlicingNotDefinedError('Obs.component', 'sys'),
  TypeNotFound: new E.TypeNotFoundError('Foo'),
  UnitMismatch: new E.UnitMismatchError({ unit: 'mm' }, { unit: 'cm' }),
  ValidationErrorDefault: new E.ValidationError('is bad', 'a.b'),
  ValidationErrorWarn: new E.ValidationError('is iffy', 'a.c', 'warn'),
  ValueAlreadyAssigned: new E.ValueAlreadyAssignedError(2, 'integer', 1),
  ValueConflictsWithClosedSlicing: new E.ValueConflictsWithClosedSlicingError('x'),
  ValueSetCompose: new E.ValueSetComposeError('VS'),
  ValueSetFilterMissingValue: new E.ValueSetFilterMissingValueError(VsOperator.IS_A),
  ValueSetFilterOperator: new E.ValueSetFilterOperatorError('nope'),
  ValueSetFilterValueType: new E.ValueSetFilterValueTypeError(VsOperator.EQUALS, ['string']),
  ValueSetFilterValueTypeAllowed: new E.ValueSetFilterValueTypeError(VsOperator.EXISTS, ['boolean', 'code'], ['true', 'false'])
};
const out = {};
for (const [k, e] of Object.entries(cases)) {
  out[k] = { name: e.constructor.name, message: e.message, specReferences: e.specReferences ?? null, severity: e.severity ?? null };
}
process.stdout.write(JSON.stringify(out, null, 2) + '\n');
