Profile: ExamplePatient
Parent: Patient
Id: example-patient
Title: "Example Patient"
Description: "A Patient with a required name and an optional favourite colour."
* name 1..* MS
* extension contains FavouriteColour named favouriteColour 0..1

Extension: FavouriteColour
Id: favourite-colour
Title: "Favourite colour"
Description: "The patient's favourite colour."
* value[x] only CodeableConcept
* valueCodeableConcept from ColourVS (extensible)

CodeSystem: ColourCS
Id: colour-cs
Title: "Colours"
Description: "A few colours."
* #red "Red"
* #green "Green"
* #blue "Blue"

ValueSet: ColourVS
Id: colour-vs
Title: "Colours"
Description: "All colours in ColourCS."
* include codes from system ColourCS

Instance: patient-1
InstanceOf: ExamplePatient
Usage: #example
* name.family = "Example"
* name.given = "Pat"
* extension[favouriteColour].valueCodeableConcept = ColourCS#blue "Blue"
