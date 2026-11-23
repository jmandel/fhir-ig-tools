package org.hl7.fhir.jekyll.rouge;

/**
 * Port of Rouge::Token::Tokens (rouge-4.7.0/lib/rouge/token.rb). Ruby's nested constants
 * (<code>Name::Builtin::Pseudo</code>) are spelled with underscores (<code>Name_Builtin_Pseudo</code>);
 * <code>Str</code>/<code>Num</code> are the convenience aliases of Literal::String/Literal::Number.
 * Lexers implement this interface the way Ruby lexers <code>include Token::Tokens</code>.
 */
public interface Tokens {
  Token Text = Token.make(null, "Text", "");
  Token Text_Whitespace = Token.make(Text, "Whitespace", "w");

  Token Escape = Token.make(null, "Escape", "esc");
  Token Error = Token.make(null, "Error", "err");
  Token Other = Token.make(null, "Other", "x");

  Token Keyword = Token.make(null, "Keyword", "k");
  Token Keyword_Constant = Token.make(Keyword, "Constant", "kc");
  Token Keyword_Declaration = Token.make(Keyword, "Declaration", "kd");
  Token Keyword_Namespace = Token.make(Keyword, "Namespace", "kn");
  Token Keyword_Pseudo = Token.make(Keyword, "Pseudo", "kp");
  Token Keyword_Reserved = Token.make(Keyword, "Reserved", "kr");
  Token Keyword_Type = Token.make(Keyword, "Type", "kt");
  Token Keyword_Variable = Token.make(Keyword, "Variable", "kv");

  Token Name = Token.make(null, "Name", "n");
  Token Name_Attribute = Token.make(Name, "Attribute", "na");
  Token Name_Builtin = Token.make(Name, "Builtin", "nb");
  Token Name_Builtin_Pseudo = Token.make(Name_Builtin, "Pseudo", "bp");
  Token Name_Class = Token.make(Name, "Class", "nc");
  Token Name_Constant = Token.make(Name, "Constant", "no");
  Token Name_Decorator = Token.make(Name, "Decorator", "nd");
  Token Name_Entity = Token.make(Name, "Entity", "ni");
  Token Name_Exception = Token.make(Name, "Exception", "ne");
  Token Name_Function = Token.make(Name, "Function", "nf");
  Token Name_Function_Magic = Token.make(Name_Function, "Magic", "fm");
  Token Name_Property = Token.make(Name, "Property", "py");
  Token Name_Label = Token.make(Name, "Label", "nl");
  Token Name_Namespace = Token.make(Name, "Namespace", "nn");
  Token Name_Other = Token.make(Name, "Other", "nx");
  Token Name_Tag = Token.make(Name, "Tag", "nt");
  Token Name_Variable = Token.make(Name, "Variable", "nv");
  Token Name_Variable_Class = Token.make(Name_Variable, "Class", "vc");
  Token Name_Variable_Global = Token.make(Name_Variable, "Global", "vg");
  Token Name_Variable_Instance = Token.make(Name_Variable, "Instance", "vi");
  Token Name_Variable_Magic = Token.make(Name_Variable, "Magic", "vm");

  Token Literal = Token.make(null, "Literal", "l");
  Token Literal_Date = Token.make(Literal, "Date", "ld");

  Token Literal_String = Token.make(Literal, "String", "s");
  Token Literal_String_Affix = Token.make(Literal_String, "Affix", "sa");
  Token Literal_String_Backtick = Token.make(Literal_String, "Backtick", "sb");
  Token Literal_String_Char = Token.make(Literal_String, "Char", "sc");
  Token Literal_String_Delimiter = Token.make(Literal_String, "Delimiter", "dl");
  Token Literal_String_Doc = Token.make(Literal_String, "Doc", "sd");
  Token Literal_String_Double = Token.make(Literal_String, "Double", "s2");
  Token Literal_String_Escape = Token.make(Literal_String, "Escape", "se");
  Token Literal_String_Heredoc = Token.make(Literal_String, "Heredoc", "sh");
  Token Literal_String_Interpol = Token.make(Literal_String, "Interpol", "si");
  Token Literal_String_Other = Token.make(Literal_String, "Other", "sx");
  Token Literal_String_Regex = Token.make(Literal_String, "Regex", "sr");
  Token Literal_String_Single = Token.make(Literal_String, "Single", "s1");
  Token Literal_String_Symbol = Token.make(Literal_String, "Symbol", "ss");

  Token Literal_Number = Token.make(Literal, "Number", "m");
  Token Literal_Number_Bin = Token.make(Literal_Number, "Bin", "mb");
  Token Literal_Number_Float = Token.make(Literal_Number, "Float", "mf");
  Token Literal_Number_Hex = Token.make(Literal_Number, "Hex", "mh");
  Token Literal_Number_Integer = Token.make(Literal_Number, "Integer", "mi");
  Token Literal_Number_Integer_Long = Token.make(Literal_Number_Integer, "Long", "il");
  Token Literal_Number_Oct = Token.make(Literal_Number, "Oct", "mo");
  Token Literal_Number_Other = Token.make(Literal_Number, "Other", "mx");

  Token Operator = Token.make(null, "Operator", "o");
  Token Operator_Word = Token.make(Operator, "Word", "ow");

  Token Punctuation = Token.make(null, "Punctuation", "p");
  Token Punctuation_Indicator = Token.make(Punctuation, "Indicator", "pi");

  Token Comment = Token.make(null, "Comment", "c");
  Token Comment_Hashbang = Token.make(Comment, "Hashbang", "ch");
  Token Comment_Doc = Token.make(Comment, "Doc", "cd");
  Token Comment_Multiline = Token.make(Comment, "Multiline", "cm");
  Token Comment_Preproc = Token.make(Comment, "Preproc", "cp");
  Token Comment_PreprocFile = Token.make(Comment, "PreprocFile", "cpf");
  Token Comment_Single = Token.make(Comment, "Single", "c1");
  Token Comment_Special = Token.make(Comment, "Special", "cs");

  Token Generic = Token.make(null, "Generic", "g");
  Token Generic_Deleted = Token.make(Generic, "Deleted", "gd");
  Token Generic_Emph = Token.make(Generic, "Emph", "ge");
  Token Generic_EmphStrong = Token.make(Generic, "EmphStrong", "ges");
  Token Generic_Error = Token.make(Generic, "Error", "gr");
  Token Generic_Heading = Token.make(Generic, "Heading", "gh");
  Token Generic_Inserted = Token.make(Generic, "Inserted", "gi");
  Token Generic_Lineno = Token.make(Generic, "Lineno", "gl");
  Token Generic_Output = Token.make(Generic, "Output", "go");
  Token Generic_Prompt = Token.make(Generic, "Prompt", "gp");
  Token Generic_Strong = Token.make(Generic, "Strong", "gs");
  Token Generic_Subheading = Token.make(Generic, "Subheading", "gu");
  Token Generic_Traceback = Token.make(Generic, "Traceback", "gt");

  // convenience: Num = Literal::Number, Str = Literal::String
  Token Num = Literal_Number;
  Token Num_Bin = Literal_Number_Bin;
  Token Num_Float = Literal_Number_Float;
  Token Num_Hex = Literal_Number_Hex;
  Token Num_Integer = Literal_Number_Integer;
  Token Num_Integer_Long = Literal_Number_Integer_Long;
  Token Num_Oct = Literal_Number_Oct;
  Token Num_Other = Literal_Number_Other;
  Token Str = Literal_String;
  Token Str_Affix = Literal_String_Affix;
  Token Str_Backtick = Literal_String_Backtick;
  Token Str_Char = Literal_String_Char;
  Token Str_Delimiter = Literal_String_Delimiter;
  Token Str_Doc = Literal_String_Doc;
  Token Str_Double = Literal_String_Double;
  Token Str_Escape = Literal_String_Escape;
  Token Str_Heredoc = Literal_String_Heredoc;
  Token Str_Interpol = Literal_String_Interpol;
  Token Str_Other = Literal_String_Other;
  Token Str_Regex = Literal_String_Regex;
  Token Str_Single = Literal_String_Single;
  Token Str_Symbol = Literal_String_Symbol;
}
