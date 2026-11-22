package org.hl7.fhir.sushi.utils;

// port of the `Type` enum in sushi/src/utils/Fishable.ts (SUSHI 3.21.1). Named FishableType to avoid clashing with
// java.lang.reflect.Type and the FSH `Type` keyword. Declaration order is load-bearing: when no types are passed to a
// fish call, TS iterates Object.values(Type) / the explicit list in the order given here.
public enum FishableType {
  Profile, Extension, ValueSet, CodeSystem, Instance,
  /** only defined in FSHTanks, not FHIR defs */ Invariant,
  /** only defined in FSHTanks, not FHIR defs */ RuleSet,
  /** only defined in FSHTanks, not FHIR defs */ Mapping,
  Resource,
  /** only defined in FHIR defs, not FSHTanks */ Type,
  Logical
}
