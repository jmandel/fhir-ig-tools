#!/usr/bin/env python3
"""Build tests/inventory.tsv: every Jest test of TS SUSHI 3.21.1 with the work package that owns its port.

usage (in the reference image, VM paths): python3 scripts/test-inventory.py /srv/ig/sushi-spike/jest.json > tests/inventory.tsv
       python3 scripts/test-inventory.py --coverage tests/inventory.tsv <surefire-reports-dir>   (ported/passing per WP)
A Java test claims a TS test by its JUnit display name == the TS `fullName` (describe titles + it title, space-joined),
see PORTING.md "Tests"."""
import json, sys, os, re, glob, collections
WP = [  # (regex on test file path relative to test/, WP) -- must match PORTING.md section 5; first match wins
    (r'^import/FSHErrorListener', 'WP-1'),
    (r'^import/(FSHImporter|FSHTank)', 'WP-2'),
    (r'^import/', 'WP-3'), (r'^fshtypes/', 'WP-3'),
    (r'^fhirtypes/ElementDefinition', 'WP-4'),
    (r'^fhirtypes/', 'WP-5'), (r'^utils/PathUtils', 'WP-5'),
    (r'^export/(StructureDefinition|Package|FHIRExporter)', 'WP-6'), (r'^utils/(MasterFisher|FishingUtils)', 'WP-6'),
    (r'^export/', 'WP-7'),
    (r'^fhirdefs/', 'WP-8'), (r'^utils/(FHIRVersionUtils|axiosUtils)', 'WP-8'),
    (r'^(ig|run)/', 'WP-10'), (r'^utils/', 'WP-10'),
]
def wp(rel):
    return next((w for r, w in WP if re.search(r, rel)), '?')
def inventory(path):
    d = json.load(open(path))
    print('wp\tfile\tfullName\tstatus')
    for t in sorted(d['testResults'], key=lambda t: t['name']):
        rel = t['name'].split('/test/', 1)[1]
        for a in t['assertionResults']:
            print('\t'.join([wp(rel), rel, a['fullName'].replace('\t', ' ').replace('\n', ' '), a['status']]))
def coverage(inv, reports):
    import xml.etree.ElementTree as ET
    names = set()
    for f in glob.glob(os.path.join(reports, 'TEST-*.xml')):
        for tc in ET.parse(f).getroot().iter('testcase'):
            if tc.find('failure') is None and tc.find('error') is None and tc.find('skipped') is None:
                names.add(tc.get('name').strip())
    tot, done = collections.Counter(), collections.Counter()
    for line in open(inv).read().splitlines()[1:]:
        w, rel, name, status = line.split('\t')
        tot[w] += 1; done[w] += name.strip() in names  # surefire trims display names
    for w in sorted(tot): print(f'{w}\t{done[w]}/{tot[w]}')
if __name__ == '__main__':
    coverage(sys.argv[2], sys.argv[3]) if sys.argv[1] == '--coverage' else inventory(sys.argv[1])
