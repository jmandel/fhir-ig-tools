# Third-party notices

The Java code under `jekyll/src/main/java`, `common/src/main/java` and `sushi/src/main/java` contains ports
(translations into Java) of the projects below. Their copyright notices and licenses follow. The upstream sources
themselves are not redistributed here, except for the SUSHI files listed in its section; `third_party/` holds the
Ruby sources as optional git submodules for developers.

| Project | Version | License | Ported into |
|---|---|---|---|
| Liquid (Shopify) | 4.0.4 | MIT | `org.hl7.fhir.jekyll.liquid` |
| Jekyll | 4.4.1 | MIT | `org.hl7.fhir.jekyll.site` |
| kramdown | 2.6.1 | MIT | `org.hl7.fhir.jekyll.kramdown` |
| kramdown-parser-gfm | 1.1.0 | MIT | `org.hl7.fhir.jekyll.kramdown` |
| Rouge (incl. Pygments-derived lexers) | 4.7.0 | MIT; Pygments BSD-2-Clause | `org.hl7.fhir.jekyll.rouge` |
| SafeYAML | 1.0.5 | MIT | `org.hl7.fhir.jekyll.yaml` |
| Psych | 5.0.1 | MIT | `org.hl7.fhir.jekyll.yaml` |
| libyaml | 0.2.5 | MIT | `org.hl7.fhir.common.yaml` (event layer), `org.hl7.fhir.jekyll.yaml` |
| Ruby date | 3.3.3 | BSD-2-Clause (Ruby BSDL) | `org.hl7.fhir.jekyll.yaml` |
| Ruby (numeric parsing/formatting: object.c, numeric.c, missing/dtoa.c, util.c, string.c, bignum.c, pack.c, time.c) | 3.2.3 | Ruby License / BSD-2-Clause | `org.hl7.fhir.jekyll.yaml` (RubyNumbers, RubyBase64) |
| Addressable (URI unescape) | 2.9.1 | Apache-2.0 | `org.hl7.fhir.jekyll.site.JekyllStrings` |
| yaml-test-suite (test data only) | data-2022-01-17 | MIT | `jekyll/tests/yaml` |
| SUSHI (FHIR Shorthand compiler) | 3.21.1 | Apache-2.0 | `org.hl7.fhir.sushi`, `org.hl7.fhir.common.js`; grammar and data files, see below |
| ANTLR 4 (JavaScript runtime 4.13.2; the tool's ATN construction) | 4.13 | BSD-3-Clause | `org.hl7.fhir.sushi.importer.parser` (the hand-written parser reproduces the runtime's prediction, error messages and recovery) |
| fhir-package-loader | 2.2.4 | Apache-2.0 | `org.hl7.fhir.sushi.fhirdefs` (the rules ported where core resolves differently, and log messages) |
| sax | 1.6.1 | BlueOak-1.0.0 | `org.hl7.fhir.sushi.xhtml` |
| html-minifier-terser (the options SUSHI uses) | 5.1.1 | MIT | `org.hl7.fhir.sushi.xhtml` |
| yaml (eemeli/yaml, the composer and messages) | 1.10.3 | ISC | `org.hl7.fhir.sushi.yaml` |
| lodash (the functions SUSHI uses) | 4.18.1 | MIT | `org.hl7.fhir.common.js` |
| ajv (error message texts) | 8.20.0 | MIT | `org.hl7.fhir.sushi.importer.config` |
| commander (option parsing and help text) | 13.1.0 | MIT | `org.hl7.fhir.sushi.App` |
| winston, chalk (logging behaviour SUSHI relies on) | 3.19.0, 4.1.2 | MIT | `org.hl7.fhir.sushi.utils.FshLogger` |
| text-table, title-case, valid-url, junk | 0.2.0, 3.0.3, 1.0.9, 3.1.0 | MIT | `org.hl7.fhir.sushi.utils` |
| sanitize-filename | 1.6.4 | WTFPL or ISC (used under ISC) | `org.hl7.fhir.sushi.utils` |
| semver | 7.8.5 | ISC | `org.hl7.fhir.sushi.ig`, `org.hl7.fhir.sushi.utils` |

`sushi.jar` also bundles, unchanged, the libraries listed in
[Libraries bundled in sushi.jar](#libraries-bundled-in-sushijar) below. `jekyll.jar` bundles nothing but this
repository's own code.

---

## Liquid 4.0.4

```
Copyright (c) 2005, 2006 Tobias Luetke

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

---

## Jekyll 4.4.1

```
The MIT License (MIT)

Copyright (c) 2008-present Tom Preston-Werner and Jekyll contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## kramdown 2.6.1

```
kramdown - fast, pure-Ruby Markdown-superset converter
Copyright (C) 2009-2013 Thomas Leitner <t_leitner@gmx.at>

Permission is hereby granted, free of charge, to any person obtaining a
copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


Some test cases and the benchmark files are based on test cases from
the MDTest test suite:

    MDTest
    Copyright (c) 2007 Michel Fortin
    <http://www.michelf.com/>

```

---

## kramdown-parser-gfm 1.1.0

```
kramdown-parser-gfm
Copyright (C) 2019 Thomas Leitner <t_leitner@gmx.at>

Permission is hereby granted, free of charge, to any person obtaining a
copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

---

## Rouge 4.7.0 (includes the Pygments license)

```
# MIT license.  See http://www.opensource.org/licenses/mit-license.php

Copyright (c) 2012 Jeanine Adkisson.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

# SPECIAL NOTE:

Many of the lexers in this project are adaptations of those in Pygments
(pygments.org). The license for Pygments is as follows:

# BEGIN pygments/LICENSE #

Copyright (c) 2006-2012 by the respective authors (see AUTHORS file).
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# END pygments/LICENSE #

The contents of the AUTHORS file at the time of porting was:

# BEGIN pygments/AUTHORS #

Pygments is written and maintained by Georg Brandl <georg@python.org>.

Major developers are Tim Hatch <tim@timhatch.com> and Armin Ronacher
<armin.ronacher@active-4.com>.

Other contributors, listed alphabetically, are:

* Sam Aaron -- Ioke lexer
* Kumar Appaiah -- Debian control lexer
* Ali Afshar -- image formatter
* Andreas Amann -- AppleScript lexer
* Jeffrey Arnold -- R/S lexer, BUGS lexers
* Jeremy Ashkenas -- CoffeeScript lexer
* Stefan Matthias Aust -- Smalltalk lexer
* Ben Bangert -- Mako lexers
* Max Battcher -- Darcs patch lexer
* Paul Baumgart, 280 North, Inc. -- Objective-J lexer
* Michael Bayer -- Myghty lexers
* John Benediktsson -- Factor lexer
* Christopher Bertels -- Fancy lexer
* Jarrett Billingsley -- MiniD lexer
* Adam Blinkinsop -- Haskell, Redcode lexers
* Frits van Bommel -- assembler lexers
* Pierre Bourdon -- bugfixes
* Hiram Chirino -- Scaml and Jade lexers
* Leaf Corcoran -- MoonScript lexer
* Christopher Creutzig -- MuPAD lexer
* Pete Curry -- bugfixes
* Owen Durni -- haXe lexer
* Nick Efford -- Python 3 lexer
* Sven Efftinge -- Xtend lexer
* Artem Egorkine -- terminal256 formatter
* James H. Fisher -- PostScript lexer
* Carlos Galdino -- Elixir and Elixir Console lexers
* Naveen Garg -- Autohotkey lexer
* Laurent Gautier -- R/S lexer
* Alex Gaynor -- PyPy log lexer
* Bertrand Goetzmann -- Groovy lexer
* Krzysiek Goj -- Scala lexer
* Matt Good -- Genshi, Cheetah lexers
* Patrick Gotthardt -- PHP namespaces support
* Olivier Guibe -- Asymptote lexer
* Martin Harriman -- SNOBOL lexer
* Matthew Harrison -- SVG formatter
* Steven Hazel -- Tcl lexer
* Aslak HellesÃ¸y -- Gherkin lexer
* Greg Hendershott -- Racket lexer
* Jordi GutiÃ©rrez Hermoso -- Octave lexer
* David Hess, Fish Software, Inc. -- Objective-J lexer
* Varun Hiremath -- Debian control lexer
* Doug Hogan -- Mscgen lexer
* Ben Hollis -- Mason lexer
* Tim Howard -- BlitzMax lexer
* Ivan Inozemtsev -- Fantom lexer
* Brian R. Jackson -- Tea lexer
* Dennis Kaarsemaker -- sources.list lexer
* Igor Kalnitsky -- vhdl lexer
* Eric Knibbe -- Lasso lexer
* Adam Koprowski -- Opa lexer
* Benjamin Kowarsch -- Modula-2 lexer
* Alexander Kriegisch -- Kconfig and AspectJ lexers
* Marek Kubica -- Scheme lexer
* Jochen Kupperschmidt -- Markdown processor
* Gerd Kurzbach -- Modelica lexer
* Olov Lassus -- Dart lexer
* Sylvestre Ledru -- Scilab lexer
* Mark Lee -- Vala lexer
* Ben Mabey -- Gherkin lexer
* Simone Margaritelli -- Hybris lexer
* Kirk McDonald -- D lexer
* Gordon McGregor -- SystemVerilog lexer
* Stephen McKamey -- Duel/JBST lexer
* Brian McKenna -- F# lexer
* Lukas Meuser -- BBCode formatter, Lua lexer
* Paul Miller -- LiveScript lexer
* Hong Minhee -- HTTP lexer
* Michael Mior -- Awk lexer
* Jon Morton -- Rust lexer
* Paulo Moura -- Logtalk lexer
* Mher Movsisyan -- DTD lexer
* Ana Nelson -- Ragel, ANTLR, R console lexers
* Nam T. Nguyen -- Monokai style
* Jesper Noehr -- HTML formatter "anchorlinenos"
* Mike Nolta -- Julia lexer
* Jonas Obrist -- BBCode lexer
* David Oliva -- Rebol lexer
* Jon Parise -- Protocol buffers lexer
* Ronny Pfannschmidt -- BBCode lexer
* Benjamin Peterson -- Test suite refactoring
* Dominik Picheta -- Nimrod lexer
* ClÃ©ment PrÃ©vost -- UrbiScript lexer
* Kashif Rasul -- CUDA lexer
* Justin Reidy -- MXML lexer
* Norman Richards -- JSON lexer
* Lubomir Rintel -- GoodData MAQL and CL lexers
* Andre Roberge -- Tango style
* Konrad Rudolph -- LaTeX formatter enhancements
* Mario Ruggier -- Evoque lexers
* Stou Sandalski -- NumPy, FORTRAN, tcsh and XSLT lexers
* Matteo Sasso -- Common Lisp lexer
* Joe Schafer -- Ada lexer
* Ken Schutte -- Matlab lexers
* Tassilo Schweyer -- Io, MOOCode lexers
* Joerg Sieker -- ABAP lexer
* Robert Simmons -- Standard ML lexer
* Kirill Simonov -- YAML lexer
* Steve Spigarelli -- XQuery lexer
* Jerome St-Louis -- eC lexer
* James Strachan -- Kotlin lexer
* Tiberius Teng -- default style overhaul
* Jeremy Thurgood -- Erlang, Squid config lexers
* Erick Tryzelaar -- Felix lexer
* Daniele Varrazzo -- PostgreSQL lexers
* Abe Voelker -- OpenEdge ABL lexer
* Whitney Young -- ObjectiveC lexer
* Matthias Vallentin -- Bro lexer
* Nathan Weizenbaum -- Haml and Sass lexers
* Dietmar Winkler -- Modelica lexer
* Nils Winter -- Smalltalk lexer
* Davy Wybiral -- Clojure lexer
* Diego Zamboni -- CFengine3 lexer
* Alex Zimin -- Nemerle lexer

Many thanks for all contributions!

# END pygments/AUTHORS #
```

---

## SafeYAML 1.0.5

```
Copyright (c) 2013 Dan Tao

MIT License

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

---

## Psych 5.0.1

```
MIT License

Copyright (c) 2009 Aaron Patterson, et al.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## libyaml 0.2.5

```
Copyright (c) 2017-2020 Ingy döt Net
Copyright (c) 2006-2016 Kirill Simonov

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Ruby date 3.3.3

```
Copyright (C) 1993-2013 Yukihiro Matsumoto. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
SUCH DAMAGE.
```

---

## Ruby 3.2.3: COPYING

```
Ruby is copyrighted free software by Yukihiro Matsumoto <matz@netlab.jp>.
You can redistribute it and/or modify it under either the terms of the
2-clause BSDL (see the file BSDL), or the conditions below:

1. You may make and give away verbatim copies of the source form of the
   software without restriction, provided that you duplicate all of the
   original copyright notices and associated disclaimers.

2. You may modify your copy of the software in any way, provided that
   you do at least ONE of the following:

   a. place your modifications in the Public Domain or otherwise
      make them Freely Available, such as by posting said
      modifications to Usenet or an equivalent medium, or by allowing
      the author to include your modifications in the software.

   b. use the modified software only within your corporation or
      organization.

   c. give non-standard binaries non-standard names, with
      instructions on where to get the original software distribution.

   d. make other distribution arrangements with the author.

3. You may distribute the software in object code or binary form,
   provided that you do at least ONE of the following:

   a. distribute the binaries and library files of the software,
      together with instructions (in the manual page or equivalent)
      on where to get the original distribution.

   b. accompany the distribution with the machine-readable source of
      the software.

   c. give non-standard binaries non-standard names, with
      instructions on where to get the original software distribution.

   d. make other distribution arrangements with the author.

4. You may modify and include the part of the software into any other
   software (possibly commercial).  But some files in the distribution
   are not written by the author, so that they are not under these terms.

   For the list of those files and their copying conditions, see the
   file LEGAL.

5. The scripts and library files supplied as input to or produced as
   output from the software do not automatically fall under the
   copyright of the software, but belong to whomever generated them,
   and may be sold commercially, and may be aggregated with this
   software.

6. THIS SOFTWARE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR
   IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED
   WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
   PURPOSE.
```

---

## Ruby 3.2.3: BSDL

```
Copyright (C) 1993-2013 Yukihiro Matsumoto. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
SUCH DAMAGE.
```

---

## Addressable 2.9.1 (Apache License 2.0)

```

                              Apache License
                        Version 2.0, January 2004
                     http://www.apache.org/licenses/

TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

1. Definitions.

   "License" shall mean the terms and conditions for use, reproduction,
   and distribution as defined by Sections 1 through 9 of this document.

   "Licensor" shall mean the copyright owner or entity authorized by
   the copyright owner that is granting the License.

   "Legal Entity" shall mean the union of the acting entity and all
   other entities that control, are controlled by, or are under common
   control with that entity. For the purposes of this definition,
   "control" means (i) the power, direct or indirect, to cause the
   direction or management of such entity, whether by contract or
   otherwise, or (ii) ownership of fifty percent (50%) or more of the
   outstanding shares, or (iii) beneficial ownership of such entity.

   "You" (or "Your") shall mean an individual or Legal Entity
   exercising permissions granted by this License.

   "Source" form shall mean the preferred form for making modifications,
   including but not limited to software source code, documentation
   source, and configuration files.

   "Object" form shall mean any form resulting from mechanical
   transformation or translation of a Source form, including but
   not limited to compiled object code, generated documentation,
   and conversions to other media types.

   "Work" shall mean the work of authorship, whether in Source or
   Object form, made available under the License, as indicated by a
   copyright notice that is included in or attached to the work
   (an example is provided in the Appendix below).

   "Derivative Works" shall mean any work, whether in Source or Object
   form, that is based on (or derived from) the Work and for which the
   editorial revisions, annotations, elaborations, or other modifications
   represent, as a whole, an original work of authorship. For the purposes
   of this License, Derivative Works shall not include works that remain
   separable from, or merely link (or bind by name) to the interfaces of,
   the Work and Derivative Works thereof.

   "Contribution" shall mean any work of authorship, including
   the original version of the Work and any modifications or additions
   to that Work or Derivative Works thereof, that is intentionally
   submitted to Licensor for inclusion in the Work by the copyright owner
   or by an individual or Legal Entity authorized to submit on behalf of
   the copyright owner. For the purposes of this definition, "submitted"
   means any form of electronic, verbal, or written communication sent
   to the Licensor or its representatives, including but not limited to
   communication on electronic mailing lists, source code control systems,
   and issue tracking systems that are managed by, or on behalf of, the
   Licensor for the purpose of discussing and improving the Work, but
   excluding communication that is conspicuously marked or otherwise
   designated in writing by the copyright owner as "Not a Contribution."

   "Contributor" shall mean Licensor and any individual or Legal Entity
   on behalf of whom a Contribution has been received by Licensor and
   subsequently incorporated within the Work.

2. Grant of Copyright License. Subject to the terms and conditions of
   this License, each Contributor hereby grants to You a perpetual,
   worldwide, non-exclusive, no-charge, royalty-free, irrevocable
   copyright license to reproduce, prepare Derivative Works of,
   publicly display, publicly perform, sublicense, and distribute the
   Work and such Derivative Works in Source or Object form.

3. Grant of Patent License. Subject to the terms and conditions of
   this License, each Contributor hereby grants to You a perpetual,
   worldwide, non-exclusive, no-charge, royalty-free, irrevocable
   (except as stated in this section) patent license to make, have made,
   use, offer to sell, sell, import, and otherwise transfer the Work,
   where such license applies only to those patent claims licensable
   by such Contributor that are necessarily infringed by their
   Contribution(s) alone or by combination of their Contribution(s)
   with the Work to which such Contribution(s) was submitted. If You
   institute patent litigation against any entity (including a
   cross-claim or counterclaim in a lawsuit) alleging that the Work
   or a Contribution incorporated within the Work constitutes direct
   or contributory patent infringement, then any patent licenses
   granted to You under this License for that Work shall terminate
   as of the date such litigation is filed.

4. Redistribution. You may reproduce and distribute copies of the
   Work or Derivative Works thereof in any medium, with or without
   modifications, and in Source or Object form, provided that You
   meet the following conditions:

   (a) You must give any other recipients of the Work or
       Derivative Works a copy of this License; and

   (b) You must cause any modified files to carry prominent notices
       stating that You changed the files; and

   (c) You must retain, in the Source form of any Derivative Works
       that You distribute, all copyright, patent, trademark, and
       attribution notices from the Source form of the Work,
       excluding those notices that do not pertain to any part of
       the Derivative Works; and

   (d) If the Work includes a "NOTICE" text file as part of its
       distribution, then any Derivative Works that You distribute must
       include a readable copy of the attribution notices contained
       within such NOTICE file, excluding those notices that do not
       pertain to any part of the Derivative Works, in at least one
       of the following places: within a NOTICE text file distributed
       as part of the Derivative Works; within the Source form or
       documentation, if provided along with the Derivative Works; or,
       within a display generated by the Derivative Works, if and
       wherever such third-party notices normally appear. The contents
       of the NOTICE file are for informational purposes only and
       do not modify the License. You may add Your own attribution
       notices within Derivative Works that You distribute, alongside
       or as an addendum to the NOTICE text from the Work, provided
       that such additional attribution notices cannot be construed
       as modifying the License.

   You may add Your own copyright statement to Your modifications and
   may provide additional or different license terms and conditions
   for use, reproduction, or distribution of Your modifications, or
   for any such Derivative Works as a whole, provided Your use,
   reproduction, and distribution of the Work otherwise complies with
   the conditions stated in this License.

5. Submission of Contributions. Unless You explicitly state otherwise,
   any Contribution intentionally submitted for inclusion in the Work
   by You to the Licensor shall be under the terms and conditions of
   this License, without any additional terms or conditions.
   Notwithstanding the above, nothing herein shall supersede or modify
   the terms of any separate license agreement you may have executed
   with Licensor regarding such Contributions.

6. Trademarks. This License does not grant permission to use the trade
   names, trademarks, service marks, or product names of the Licensor,
   except as required for reasonable and customary use in describing the
   origin of the Work and reproducing the content of the NOTICE file.

7. Disclaimer of Warranty. Unless required by applicable law or
   agreed to in writing, Licensor provides the Work (and each
   Contributor provides its Contributions) on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
   implied, including, without limitation, any warranties or conditions
   of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
   PARTICULAR PURPOSE. You are solely responsible for determining the
   appropriateness of using or redistributing the Work and assume any
   risks associated with Your exercise of permissions under this License.

8. Limitation of Liability. In no event and under no legal theory,
   whether in tort (including negligence), contract, or otherwise,
   unless required by applicable law (such as deliberate and grossly
   negligent acts) or agreed to in writing, shall any Contributor be
   liable to You for damages, including any direct, indirect, special,
   incidental, or consequential damages of any character arising as a
   result of this License or out of the use or inability to use the
   Work (including but not limited to damages for loss of goodwill,
   work stoppage, computer failure or malfunction, or any and all
   other commercial damages or losses), even if such Contributor
   has been advised of the possibility of such damages.

9. Accepting Warranty or Additional Liability. While redistributing
   the Work or Derivative Works thereof, You may choose to offer,
   and charge a fee for, acceptance of support, warranty, indemnity,
   or other liability obligations and/or rights consistent with this
   License. However, in accepting such obligations, You may act only
   on Your own behalf and on Your sole responsibility, not on behalf
   of any other Contributor, and only if You agree to indemnify,
   defend, and hold each Contributor harmless for any liability
   incurred by, or claims asserted against, such Contributor by reason
   of your accepting any such warranty or additional liability.

END OF TERMS AND CONDITIONS

APPENDIX: How to apply the Apache License to your work.

   To apply the Apache License to your work, attach the following
   boilerplate notice, with the fields enclosed by brackets "[]"
   replaced with your own identifying information. (Don't include
   the brackets!)  The text should be enclosed in the appropriate
   comment syntax for the file format. We also recommend that a
   file or class name and description of purpose be included on the
   same "printed page" as the copyright notice for easier
   identification within third-party archives.

Copyright [yyyy] [name of copyright owner]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## yaml-test-suite

```
MIT License

Copyright (c) 2016-2020 Ingy döt Net

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## SUSHI 3.21.1 (Apache License 2.0)

Copyright 2019-2025 Health Level Seven International. SUSHI is licensed under the Apache License, Version 2.0; the
full text is in the Addressable section above (and in `sushi/src/test/resources/sushi-ts/LICENSE`). Source:
https://github.com/FHIR/sushi. SUSHI ships no NOTICE file.

Besides the Java port, these files are taken from SUSHI 3.21.1 unchanged (or, for the project template,
as SUSHI writes it):
- `sushi/src/test/antlr4/**/*.g4`: SUSHI's ANTLR grammar (FSH, MiniFSH and their lexers). It is used **only in
  tests**, to generate the oracle parser the hand-written parser is compared with; no ANTLR code or grammar is
  in the built jar.
- `sushi/src/test/resources/sushi-ts/test/**`: test fixtures from SUSHI 3.21.1's test tree (IG folders, configs,
  FHIR definition packages; about 23 MB), used only by the JUnit tests; see its `README.md`.
- `sushi/src/main/resources/org/hl7/fhir/sushi/fhirdefs/R5DefsForR4/*.json`,
  `.../importer/config/YAMLschema.json`, `.../utils/init-project/*` (the `sushi init` template).

fhir-package-loader 2.2.4 is also Apache-2.0 (same text).

---

## MIT-licensed npm packages ported in `sushi`

```
lodash 4.18.1:              Copyright OpenJS Foundation and other contributors <https://openjsf.org/>
                            Based on Underscore.js, copyright Jeremy Ashkenas, DocumentCloud and
                            Investigative Reporters & Editors <http://underscorejs.org/>
html-minifier-terser 5.1.1: Copyright (c) 2010-2019 Juriy "kangax" Zaytsev
ajv 8.20.0:                 Copyright (c) 2015-2021 Evgeny Poberezkin
commander 13.1.0:           Copyright (c) 2011 TJ Holowaychuk <tj@vision-media.ca>
winston 3.19.0:             Copyright (c) 2010 Charlie Robbins
chalk 4.1.2, junk 3.1.0:    Copyright (c) Sindre Sorhus <sindresorhus@gmail.com> (sindresorhus.com)
title-case 3.0.3:           Copyright (c) 2014 Blake Embrey (hello@blakeembrey.com)
valid-url 1.0.9:            Copyright (c) 2013 Odysseas Tsatalos and oDesk Corporation
text-table 0.2.0:           released under the MIT license (no copyright line in its LICENSE)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## ISC-licensed npm packages ported in `sushi`

```
yaml 1.10.3:                Copyright 2018 Eemeli Aro <eemeli@gmail.com>
semver 7.8.5:               Copyright (c) Isaac Z. Schlueter and Contributors
sanitize-filename 1.6.4:    (WTFPL or ISC; used under ISC) Copyright (C) 2004 Sam Hocevar <sam@hocevar.net>

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

---

## sax 1.6.1 (Blue Oak Model License 1.0.0)

The parts of sax 1.6.1 ported in `org.hl7.fhir.sushi.xhtml` are licensed under the Blue Oak Model License 1.0.0,
https://blueoakcouncil.org/license/1.0.0.

---

## ANTLR 4.13 (BSD 3-Clause)

```
Copyright (c) 2012-2022 The ANTLR Project. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

3. Neither name of copyright holders nor the names of its contributors
may be used to endorse or promote products derived from this software
without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

The ANTLR Java runtime (`org.antlr:antlr4-runtime` 4.13.2, same license) is a test-scope dependency only: it runs
the oracle parser generated from SUSHI's grammar in the tests and is not part of any built jar.

---

## Libraries bundled in sushi.jar

`sushi/target/sushi.jar` (the `sushi.jar` release asset) contains, unchanged, the runtime dependencies of the `sushi`
module: org.hl7.fhir.core's `org.hl7.fhir.utilities` and `org.hl7.fhir.r4`/`r4b`/`r5`, xpp3, commons-compress, and
what they depend on. The list below is
the module's runtime dependency set as Maven resolves it (`license-maven-plugin add-third-party`), checked against
the packages present in the jar. Their Apache `NOTICE` files are merged into the jar's `META-INF/NOTICE`; this file
and the repository's license are in the jar under `META-INF/fhir-ig-tools/`.

| Library | Version | License |
|---|---|---|
| HL7 FHIR core: org.hl7.fhir.utilities, org.hl7.fhir.r4, org.hl7.fhir.r4b, org.hl7.fhir.r5 (`ca.uhn.hapi.fhir`) | 7.0.6 | Apache-2.0 |
| XPP3 / MXP1 (`org.codelibs:xpp3`, the XML pull parser core's XML parsers use) | 1.1.4c.0 | MXP1 (`org.xmlpull.mxp1`): Indiana University Extreme! Lab Software License 1.2 (text below; its POM declares "The Apache Software License, Version 1.1"); XMLPULL V1 API (`org.xmlpull.v1`): its sources refer to the LICENSE.txt at http://www.xmlpull.org/, which places the API in the public domain |
| HAPI FHIR base (`ca.uhn.hapi.fhir:hapi-fhir-base`) | 8.10.0 | Apache-2.0 |
| Apache Commons Compress, Codec, IO, Lang, Net, Text | 1.26.0, 1.15, 2.15.1, 3.14.0, 3.9.0, 1.10.0 | Apache-2.0 |
| Jackson annotations, core, databind, datatype-jsr310 | 2.21, 2.21.1, 2.21.1, 2.21.1 | Apache-2.0 (jackson-core includes FastDoubleParser, MIT; its notices are in the jar's `META-INF/FastDoubleParser-*`) |
| Guava, failureaccess, listenablefuture | 33.2.1-jre, 1.0.2, 9999.0 | Apache-2.0 |
| error-prone annotations, J2ObjC annotations | 2.26.1, 3.0.0 | Apache-2.0 |
| FindBugs jsr305 | 3.0.2 | Apache-2.0 (as its POM declares) |
| OkHttp (okhttp-jvm), Okio (okio, okio-jvm) | 5.3.2, 3.18.2, 3.16.4 | Apache-2.0 |
| Kotlin stdlib | 2.2.21 | Apache-2.0 |
| OpenTelemetry API, common, context, instrumentation annotations | 1.60.1, 1.60.1, 1.60.1, 2.33.1 | Apache-2.0 |
| XML Resolver (xmlresolver, xmlresolver data) | 6.0.23 | Apache-2.0 |
| JCL 1.2 implemented over SLF4J (`jcl-over-slf4j`) | 2.0.16 | Apache-2.0 |
| SLF4J API | 2.0.17 | MIT |
| Checker Qual | 3.42.0 | MIT |
| Jakarta Annotations API | 2.1.1 | EPL-2.0 or GPL-2.0 with Classpath Exception (used under EPL-2.0); source: https://github.com/jakartaee/common-annotations-api |
| Saxon-HE | 13.0 | MPL-2.0; source: https://github.com/Saxonica/Saxon-HE (license text: https://www.mozilla.org/MPL/2.0/) |
| ICU4J | 78.1 | Unicode-3.0 (text below; ICU's full LICENSE, with the notices of the data it includes: https://github.com/unicode-org/icu/blob/release-78.1/LICENSE) |

The Apache License 2.0 text is in the Addressable section above and the MIT text in the SUSHI npm section. The
EPL-2.0 text is at https://www.eclipse.org/legal/epl-2.0/.

### Indiana University Extreme! Lab Software License 1.2 (XPP3 / MXP1)

This product includes software developed by the Indiana University Extreme! Lab. For further information please
visit http://www.extreme.indiana.edu/. (From the license header of `org/xmlpull/mxp1/MXParser.java` in
`org.codelibs:xpp3` 1.1.4c.0.)

```
Indiana University Extreme! Lab Software License, Version 1.2

Copyright (C) 2003 The Trustees of Indiana University.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

1) All redistributions of source code must retain the above
   copyright notice, the list of authors in the original source
   code, this list of conditions and the disclaimer listed in this
   license;

2) All redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the disclaimer
   listed in this license in the documentation and/or other
   materials provided with the distribution;

3) Any documentation included with all redistributions must include
   the following acknowledgement:

     "This product includes software developed by the Indiana
     University Extreme! Lab.  For further information please visit
     http://www.extreme.indiana.edu/"

   Alternatively, this acknowledgment may appear in the software
   itself, and wherever such third-party acknowledgments normally
   appear.

4) The name "Indiana University" or "Indiana University
   Extreme! Lab" shall not be used to endorse or promote
   products derived from this software without prior written
   permission from Indiana University.  For written permission,
   please contact http://www.extreme.indiana.edu/.

5) Products derived from this software may not use "Indiana
   University" name nor may "Indiana University" appear in their name,
   without prior written permission of the Indiana University.

Indiana University provides no reassurances that the source code
provided does not infringe the patent or any other intellectual
property rights of any other entity.  Indiana University disclaims any
liability to any recipient for claims brought by any other entity
based on infringement of intellectual property rights or otherwise.

LICENSEE UNDERSTANDS THAT SOFTWARE IS PROVIDED "AS IS" FOR WHICH
NO WARRANTIES AS TO CAPABILITIES OR ACCURACY ARE MADE. INDIANA
UNIVERSITY GIVES NO WARRANTIES AND MAKES NO REPRESENTATION THAT
SOFTWARE IS FREE OF INFRINGEMENT OF THIRD PARTY PATENT, COPYRIGHT, OR
OTHER PROPRIETARY RIGHTS.  INDIANA UNIVERSITY MAKES NO WARRANTIES THAT
SOFTWARE IS FREE FROM "BUGS", "VIRUSES", "TROJAN HORSES", "TRAP
DOORS", "WORMS", OR OTHER HARMFUL CODE.  LICENSEE ASSUMES THE ENTIRE
RISK AS TO THE PERFORMANCE OF SOFTWARE AND/OR ASSOCIATED MATERIALS,
AND TO THE PERFORMANCE AND VALIDITY OF INFORMATION GENERATED USING
SOFTWARE.
```

### Unicode License v3 (ICU4J)

```
UNICODE LICENSE V3

COPYRIGHT AND PERMISSION NOTICE

Copyright © 1991-2026 Unicode, Inc.

NOTICE TO USER: Carefully read the following legal agreement. BY
DOWNLOADING, INSTALLING, COPYING OR OTHERWISE USING DATA FILES, AND/OR
SOFTWARE, YOU UNEQUIVOCALLY ACCEPT, AND AGREE TO BE BOUND BY, ALL OF THE
TERMS AND CONDITIONS OF THIS AGREEMENT. IF YOU DO NOT AGREE, DO NOT
DOWNLOAD, INSTALL, COPY, DISTRIBUTE OR USE THE DATA FILES OR SOFTWARE.

Permission is hereby granted, free of charge, to any person obtaining a
copy of data files and any associated documentation (the "Data Files") or
software and any associated documentation (the "Software") to deal in the
Data Files or Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, and/or sell
copies of the Data Files or Software, and to permit persons to whom the
Data Files or Software are furnished to do so, provided that either (a)
this copyright and permission notice appear with all copies of the Data
Files or Software, or (b) this copyright and permission notice appear in
associated Documentation.

THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
THIRD PARTY RIGHTS.

IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE
BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES,
OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THE DATA
FILES OR SOFTWARE.

Except as contained in this notice, the name of a copyright holder shall
not be used in advertising or otherwise to promote the sale, use or other
dealings in these Data Files or Software without prior written
authorization of the copyright holder.
```
