# hand fixes on top of ts2junit output for InstanceExporter.test.ts: the two jest.spyOn tests
import re, sys
p = sys.argv[1]
s = open(p).read()
for m, ret in (('fishForMetadata', 'Metadata'), ('fishForMetadatas', 'List<Metadata>')):
    old = f'''    Object fisherSpy;

    @BeforeEach
    void beforeEach() {{
      fisherSpy = jest.spyOn(fisher, "{m}");
    }}

    @AfterEach
    void afterEach() {{
      fisherSpy.mockReset();
    }}
'''
    new = f'''    /** jest.spyOn(fisher, '{m}'): the calls (item, types) made through a recording TestFisher. */
    List<Object[]> fisherSpy = new ArrayList<>();

    @BeforeEach
    void beforeEach() {{
      TestFisher spy = new TestFisher(tank, defs, pkg) {{
        @Override public {ret} {m}(String item, FishableType... types) {{
          fisherSpy.add(new Object[] {{item, types}});
          return super.{m}(item, types);
        }}
      }};
      exporter = new InstanceExporter(tank, pkg, spy);
    }}
'''
    assert old in s, m
    s = s.replace(old, new)
s = s.replace("/* TODO-TS2J matcher: expect(fisherSpy).toHaveBeenCalledWith('some-item', Type.Instance) */;",
              'assertTrue(fisherSpy.stream().anyMatch(c -> c[0].equals("some-item")\n'
              '          && Arrays.equals((FishableType[]) c[1], new FishableType[] {FishableType.Instance})));')
open(p, 'w').write(s)
