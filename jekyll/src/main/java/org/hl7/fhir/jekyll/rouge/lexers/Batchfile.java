package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;

/** Port of rouge-4.7.0/lib/rouge/lexers/batchfile.rb */
public class Batchfile extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Batchfile");
  static final Definition DEF = new Definition(null);

  static final List<String> KEYWORDS = List.of(Words.w("if else for in do goto call exit"));
  static final List<String> OPERATOR_WORDS = List.of(Words.w("exist defined errorlevel cmdextversion not equ neq lss leq gtr geq"));
  static final List<String> DEVICES = List.of(Words.w("con prn aux nul com1 com2 com3 com4 com5 com6 com7 com8 com9 lpt1 lpt2 lpt3 lpt4 lpt5 lpt6"
      + " lpt7 lpt8 lpt9"));
  static final List<String> BUILTIN_COMMANDS = List.of(Words.w("assoc attrib break bcdedit cacls cd chcp chdir chkdsk chkntfs choice cls cmd color comp"
      + " compact convert copy date del dir diskpart doskey dpath driverquery echo endlocal erase fc"
      + " find findstr format fsutil ftype gpresult graftabl help icacls label md mkdir mklink mode"
      + " more move openfiles path pause popd print prompt pushd rd recover ren rename replace rmdir"
      + " robocopy setlocal sc schtasks shift shutdown sort start subst systeminfo takeown tasklist"
      + " taskkill time timeout title tree type ver verify vol xcopy waitfor wmic"));
  static final List<String> OTHER_COMMANDS = List.of(Words.w("addusers admodcmd ansicon arp at bcdboot bitsadmin browstat certreq certutil change cidiag"
      + " cipher cleanmgr clip cmdkey compress convertcp coreinfo csccmd csvde cscript curl debug"
      + " defrag delprof deltree devcon diamond dirquota diruse diskshadow diskuse dism dnscmd"
      + " dsacls dsadd dsget dsquery dsmod dsmove dsrm dsmgmt dsregcmd edlin eventcreate expand"
      + " extract fdisk fltmc forfiles freedisk ftp getmac gpupdate hostname ifmember inuse ipconfig"
      + " kill lgpo lodctr logman logoff logtime makecab mapisend mbsacli mem mountvol moveuser msg"
      + " mshta msiexec msinfo32 mstsc nbtstat net net1 netdom netsh netstat nlsinfo nltest now"
      + " nslookup ntbackup ntdsutil ntoskrnl ntrights nvspbind pathping perms ping portqry powercfg"
      + " pngout pnputil printbrm prncnfg prnmngr procdump psexec psfile psgetsid psinfo pskill"
      + " pslist psloggedon psloglist pspasswd psping psservice psshutdown pssuspend qbasic qgrep"
      + " qprocess query quser qwinsta rasdial reg reg1 regdump regedt32 regsvr32 regini reset"
      + " restore rundll32 rmtshare route rpcping run runas scandisk setspn setx sfc share"
      + " shellrunas shortcut sigcheck sleep slmgr strings subinacl sysmon telnet tftp tlist touch"
      + " tracerpt tracert tscon tsdiscon tskill tttracer typeperf tzutil undelete unformat verifier"
      + " vmconnect vssadmin w32tm wbadmin wecutil wevtutil wget where whoami windiff winrm winrs"
      + " wpeutil wpr wusa wuauclt wscript"));
  static final List<String> ATTRIBUTES = List.of(Words.w("on off disable enableextensions enabledelayedexpansion"));

  static {
    DEF.state("basic", s -> {
      // Comments
      s.rule(re("@?\\brem\\b.*$", "i"), Comment);

      // Empty Labels
      s.rule("^::.*$", Comment);

      // Labels
      s.rule(re(":[a-z]+", "i"), Name_Label);

      s.rule(re("([a-z]\\w*)(\\.exe|com|bat|cmd|msi)?", "i"), (l, m) -> {
        String w = m.get(1);
        if (DEVICES.contains(w)) {
          l.groups(Keyword_Reserved, Error);
        } else if (KEYWORDS.contains(w)) {
          l.groups(Keyword, Error);
        } else if (OPERATOR_WORDS.contains(w)) {
          l.groups(Operator_Word, Error);
        } else if (BUILTIN_COMMANDS.contains(w)) {
          l.token(Name_Builtin);
        } else if (OTHER_COMMANDS.contains(w)) {
          l.token(Name_Builtin);
        } else if (ATTRIBUTES.contains(w)) {
          l.groups(Name_Attribute, Error);
        } else {
          // `elsif "set".casecmp m[1]`: casecmp returns an Integer, which is always truthy
          l.groups(Keyword_Declaration, Error);
        }
      });

      s.rule(re("((?:[\\/\\+]|--?)[a-z]+)\\s*", "i"), Name_Attribute);

      s.mixin("expansions");

      s.rule("[<>&|(){}\\[\\]\\-+=;,~?*]", Operator);
    });

    DEF.state("escape", s -> {
      s.rule(re("\\^.", "m"), Str_Escape);
    });

    DEF.state("expansions", s -> {
      // Normal and Delayed expansion
      s.rule(re("[%!]+([a-z_$@#]+)[%!]+", "i"), Name_Variable);
      // For Variables
      s.rule(re("(\\%+~?[a-z]+\\d?)", "i"), Name_Variable_Magic);
    });

    DEF.state("double_quotes", s -> {
      s.mixin("escape");
      s.rule("[\"]", Str_Double, POP);
      s.mixin("expansions");
      s.rule("[^\\^\"%!]+", Str_Double);
    });

    DEF.state("single_quotes", s -> {
      s.mixin("escape");
      s.rule("[']", Str_Single, POP);
      s.mixin("expansions");
      s.rule("[^\\^'%!]+", Str_Single);
    });

    DEF.state("backtick", s -> {
      s.mixin("escape");
      s.rule("[`]", Str_Backtick, POP);
      s.mixin("expansions");
      s.rule("[^\\^`%!]+", Str_Backtick);
    });

    DEF.state("data", s -> {
      s.rule("\\s+", Text);
      s.rule(re("0x[0-9a-f]+", "i"), Literal_Number_Hex);
      s.rule("[0-9]", Literal_Number);
      s.rule("[\"]", Str_Double, "double_quotes");
      s.rule("[']", Str_Single, "single_quotes");
      s.rule("[`]", Str_Backtick, "backtick");
      s.rule("[^\\s&|()\\[\\]{}\\^=;!%+\\-,\"'`~?*]+", Text);
      s.mixin("escape");
    });

    DEF.state("root", s -> {
      s.mixin("basic");
      s.mixin("data");
    });
  }

  public Batchfile(Map<String, ?> opts) {
    super(opts);
  }

  @Override
  protected Definition definition() {
    return DEF;
  }

  @Override
  public LexerClass lexerClass() {
    return CLASS;
  }
}
