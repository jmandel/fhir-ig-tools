package org.hl7.fhir.sushi.ig;

// port of sushi/test/ig/fixtures/menuXMLContent.ts (SUSHI 3.21.1)
final class MenuXMLContent {
  private MenuXMLContent() {}

  static final String simpleMenuXMLContent = "\n<ul xmlns=\"http://www.w3.org/1999/xhtml\" class=\"nav navbar-nav\">\n  <li>\n    <a href=\"animals.html\">Animals</a>\n  </li>\n  <li>\n    <a href=\"plants.html\">Plants</a>\n  </li>\n  <li>\n    Other\n  </li>\n</ul>";

  static final String simpleMenuXMLWithOpenInNewTabContent = "\n<ul xmlns=\"http://www.w3.org/1999/xhtml\" class=\"nav navbar-nav\">\n  <li>\n    <a target=\"_blank\" href=\"animals.html\">Animals</a>\n  </li>\n  <li>\n    <a href=\"plants.html\">Plants</a>\n  </li>\n  <li>\n    Other\n  </li>\n</ul>";

  static final String subMenuXMLContent = "\n<ul xmlns=\"http://www.w3.org/1999/xhtml\" class=\"nav navbar-nav\">\n  <li>\n    <a href=\"animals.html\">Animals</a>\n  </li>\n  <li class=\"dropdown\">\n    <a data-toggle=\"dropdown\" href=\"index.html\" class=\"dropdown-toggle\">Plants\n      <b class=\"caret\"></b>\n    </a>\n    <ul class=\"dropdown-menu\">\n      <li>\n        <a href=\"plants.html#trees\">Trees</a>\n      </li>\n      <li>\n        <a href=\"buds.html\">Flowers</a>\n      </li>\n      <li>\n        <a target=\"_blank\" href=\"prickly.com\">Cacti</a>\n      </li>\n    </ul>\n  </li>\n</ul>";

  static final String subMenuWithWarningXMLContent = "\n<ul xmlns=\"http://www.w3.org/1999/xhtml\" class=\"nav navbar-nav\">\n  <li>\n    <a href=\"animals.html\">Animals</a>\n  </li>\n  <li class=\"dropdown\">\n    <a data-toggle=\"dropdown\" href=\"index.html\" class=\"dropdown-toggle\">Plants\n      <b class=\"caret\"></b>\n    </a>\n    <ul class=\"dropdown-menu\">\n      <li>\n        <a href=\"plants.html#trees\">Trees</a>\n      </li>\n      <li class=\"dropdown\">\n        <a data-toggle=\"dropdown\" href=\"index.html\" class=\"dropdown-toggle\">Flowers\n          <b class=\"caret\"></b>\n        </a>\n        <ul class=\"dropdown-menu\">\n          <li>\n            <a href=\"buds.html#roses\">Roses</a>\n          </li>\n          <li>\n            Tulips\n          </li>\n        </ul>\n      </li>\n    </ul>\n  </li>\n</ul>";

  static final String menuWithEscapedCharacters = "\n<ul xmlns=\"http://www.w3.org/1999/xhtml\" class=\"nav navbar-nav\">\n  <li>\n    <a href=\"animals.html\">Animals &lt;3</a>\n  </li>\n  <li class=\"dropdown\">\n    <a data-toggle=\"dropdown\" href=\"index.html\" class=\"dropdown-toggle\">Plants &gt;:)\n      <b class=\"caret\"></b>\n    </a>\n    <ul class=\"dropdown-menu\">\n      <li>\n        <a href=\"trees.html\">Trees and &quot;Leaves&quot;</a>\n      </li>\n      <li>\n        <a href=\"buds.html\">Flowers&apos; Blossoms</a>\n      </li>\n      <li>\n        <a target=\"_blank\" href=\"prickly.com\">Cacti &amp; Succulents</a>\n      </li>\n    </ul>\n  </li>\n</ul>";

}
