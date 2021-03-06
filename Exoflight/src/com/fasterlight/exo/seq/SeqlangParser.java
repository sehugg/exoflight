/* Generated By:JavaCC: Do not edit this line. SeqlangParser.java */
package com.fasterlight.exo.seq;

import java.util.*;

import com.fasterlight.exo.orbit.Constants;
import com.fasterlight.util.*;
import com.fasterlight.vecmath.*;

public class SeqlangParser
implements Constants, SeqlangParserConstants {
        private Sequencer seq;
        private String cur_desc = null;
        private Map labels = new HashMap();
        private Map branchnodes = new HashMap();
        private boolean goteof;
        private List imports = new ArrayList();

        public void addImport(String s)
        {
                if (s == null)
                        throw new IllegalArgumentException();
                imports.add(s);
        }

        private void addDefaultImports()
        {
                if (imports.size() == 0)
                {
                        imports.add("com.fasterlight.exo.orbit.*");
                        imports.add("com.fasterlight.exo.orbit.traj.*");
                        imports.add("com.fasterlight.exo.game.*");
                        imports.add("com.fasterlight.exo.ship.*");
                        imports.add("com.fasterlight.exo.ship.progs.*");
                        imports.add("com.fasterlight.exo.crew.*");
                        imports.add("com.fasterlight.exo.strategy.*");
                        imports.add("com.fasterlight.math.*");
                        imports.add("com.fasterlight.game.*");
                }
        }

        public Class resolveClassFromImports(String s)
        {
                addDefaultImports();

                Iterator it = imports.iterator();
                while (it.hasNext())
                {
                        String cn = null;
                        String impdecl = (String)it.next();
                        if (impdecl.endsWith("*"))
                        {
                                cn = impdecl.substring(0, impdecl.length()-1) + s;
                        } else if (impdecl.endsWith(s))
                        {
                                cn = impdecl;
                        }
                        if (cn != null)
                        {
                                try {
                                        cn = cn.replace('/','.');
                                        return Class.forName(cn); // if class found, return
                                } catch (Exception e) {
                                }
                        }
                }
                return null; // class not found
        }

        public boolean eof()
        {
                return goteof;
        }

        public void setSequencer(Sequencer seq)
        {
                this.seq = seq;
        }

        public Sequencer getSequencer()
        {
                return seq;
        }

        void addNode(SequencerNode node)
        {
                node.setDescription(cur_desc);
                seq.addNode(node);
        }

        void addBranchNode(BranchNode bnode, String label)
        {
                branchnodes.put(bnode, label);
                addNode(bnode);
        }

        void addLabel(String labelid)
        throws ParseException
        {
                if (labels.get(labelid) != null)
                        throw new ParseException("Label `" + labelid + "' is already defined");
                labels.put(labelid, new Integer(seq.getNodeCount()));
        }

        void resolveLabels()
        throws ParseException
        {
                Iterator it = branchnodes.entrySet().iterator();
                while (it.hasNext())
                {
                        Map.Entry entry = (Map.Entry)it.next();
                        BranchNode bnode = (BranchNode)entry.getKey();
                        String labelid = (String)entry.getValue();
                        if (labelid != null)
                        {
                                int nodenum = getLabel(labelid);
                                if (nodenum < 0)
                                        throw new ParseException("Could not resolve label `" + labelid + "'");
                                bnode.setNodeIndex(nodenum);
                        }
                }
        }

        int getLabel(String labelid)
        {
                Integer i = (Integer)labels.get(labelid);
                if (i == null)
                        return -1;
                else
                        return i.intValue();
        }

        public void parse()
        throws ParseException
        {
                if (seq == null)
                        throw new IllegalArgumentException("Must setSequencer() first");
                Sequence();
                resolveLabels();
        }

        class PropertyObj
        {
                String pn;
                PropertyObj(String pn)
                {
                        this.pn = pn;
                }
        }

        class NewObject
        extends PropertyObj
        {
                NewObject(String cname)
                throws ParseException
                {
                        super(cname);
                        Class clazz = resolveClassFromImports(cname);
                        if (clazz == null)
                                throw new ParseException("Could not resolve class '" + cname + "'");
                        // todo: make an arg. of propertynode
                        this.pn = "game.classloader." + clazz.getName().replace('.','/');
                }
        }

/*****************************************
 *****************************************/
  final public void Sequence() throws ParseException {
        String name;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case 0:
      jj_consume_token(0);
                goteof = true;
      break;
    case SEQUENCE:
      jj_consume_token(SEQUENCE);
      name = QuoteStr();
                        seq.setName(name);
      jj_consume_token(LBRACE);
      label_1:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case WAIT:
        case GOTO:
        case STOP:
        case SET:
        case CANCEL:
        case IDENT_LITERAL:
        case QUOTED_LITERAL:
        case IDENTIFIER:
        case ATSIGN:
          ;
          break;
        default:
          jj_la1[0] = jj_gen;
          break label_1;
        }
        SequenceStatement();
      }
      jj_consume_token(RBRACE);
      break;
    default:
      jj_la1[1] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void SequenceStatement() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case QUOTED_LITERAL:
      DescStmt();
      break;
    case WAIT:
    case GOTO:
    case STOP:
    case SET:
    case CANCEL:
    case IDENT_LITERAL:
    case IDENTIFIER:
    case ATSIGN:
      NotDescStmt();
      break;
    default:
      jj_la1[2] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void NotDescStmt() throws ParseException {
    if (jj_2_1(2)) {
      SetStmt();
    } else if (jj_2_2(3)) {
      WaitStmt();
    } else if (jj_2_3(3)) {
      CondWaitStmt();
    } else if (jj_2_4(2)) {
      LabelStmt();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case GOTO:
        GotoStmt();
        break;
      case STOP:
        StopStmt();
        break;
      case SET:
      case CANCEL:
        SetAbortStmt();
        break;
      default:
        jj_la1[3] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
                        cur_desc = null;
  }

  final public void DescStmt() throws ParseException {
        String s;
    s = QuoteStr();
                        cur_desc = s;
  }

  final public void SetStmt() throws ParseException {
        PropertyObj lexpr;
        Object rexpr;
        boolean opt=false;
    lexpr = Lexpr();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ASSIGN:
      jj_consume_token(ASSIGN);
      break;
    case ASSIGNOPT:
      jj_consume_token(ASSIGNOPT);
                       opt=true;
      break;
    default:
      jj_la1[4] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    rexpr = Rexpr();
    jj_consume_token(SEMICOLON);
                        if (rexpr instanceof PropertyObj)
                                addNode(new PropertySetNode(lexpr.pn, ((PropertyObj)rexpr).pn, null, true, opt));
                        else
                                addNode(new PropertySetNode(lexpr.pn, rexpr, null, false, opt));
  }

  final public void LabelStmt() throws ParseException {
        String labelid;
    labelid = IdentStr();
    jj_consume_token(COLON);
                addLabel(labelid);
  }

  final public void GotoStmt() throws ParseException {
        String labelid;
    jj_consume_token(GOTO);
    labelid = IdentStr();
    jj_consume_token(SEMICOLON);
                addBranchNode(new BranchNode(), labelid);
  }

  final public void SetAbortStmt() throws ParseException {
        String abortid,labelid;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case SET:
      jj_consume_token(SET);
      jj_consume_token(ABORT);
      abortid = QuoteStr();
      jj_consume_token(GOTO);
      labelid = IdentStr();
      jj_consume_token(SEMICOLON);
                addBranchNode(new SetAbortNode(abortid), labelid);
      break;
    case CANCEL:
      jj_consume_token(CANCEL);
      jj_consume_token(ABORT);
      abortid = QuoteStr();
      jj_consume_token(SEMICOLON);
                addBranchNode(new SetAbortNode(abortid), null);
      break;
    default:
      jj_la1[5] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void StopStmt() throws ParseException {
    jj_consume_token(STOP);
    jj_consume_token(SEMICOLON);
                addNode(new StopNode());
  }

  final public void WaitStmt() throws ParseException {
        long t;
        boolean dur=false;
    jj_consume_token(WAIT);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case UNTIL:
      jj_consume_token(UNTIL);
      break;
    case FOR:
      jj_consume_token(FOR);
                                  dur=true;
      break;
    default:
      jj_la1[6] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    t = Duration();
    jj_consume_token(SEMICOLON);
                        addNode(new TimeWaitNode(t, dur ? TimeWaitNode.DURATION : TimeWaitNode.RELATIVE));
  }

  final public void CondWaitStmt() throws ParseException {
        Condition cond;
        long interval, timeout=0;
    jj_consume_token(WAIT);
    jj_consume_token(FOR);
    jj_consume_token(CONDITION);
    jj_consume_token(LPAREN);
    cond = Cond();
    jj_consume_token(RPAREN);
    jj_consume_token(INTERVAL);
    interval = Duration();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case TIMEOUT:
      jj_consume_token(TIMEOUT);
      timeout = Duration();
      break;
    default:
      jj_la1[7] = jj_gen;
      ;
    }
    jj_consume_token(SEMICOLON);
                        addNode(new ConditionWaitNode(cond, interval, timeout));
  }

  final public Condition Cond() throws ParseException {
        Condition c,d;
    c = Cond2();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case OR:
      jj_consume_token(OR);
      d = Cond();
                                    c=new OrCondition(c,d);
      break;
    default:
      jj_la1[8] = jj_gen;
      ;
    }
                        {if (true) return c;}
    throw new Error("Missing return statement in function");
  }

  final public Condition Cond2() throws ParseException {
        Condition c,d;
    c = Cond3();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case AND:
      jj_consume_token(AND);
      d = Cond2();
                                      c=new AndCondition(c,d);
      break;
    default:
      jj_la1[9] = jj_gen;
      ;
    }
                        {if (true) return c;}
    throw new Error("Missing return statement in function");
  }

  final public Condition Cond3() throws ParseException {
        Condition c;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case NOT:
      jj_consume_token(NOT);
      c = Cond3();
                        {if (true) return new NotCondition(c);}
      break;
    case IDENT_LITERAL:
    case IDENTIFIER:
    case LPAREN:
    case ATSIGN:
      c = Cond4();
                        {if (true) return c;}
      break;
    default:
      jj_la1[10] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

  final public Condition Cond4() throws ParseException {
        PropertyObj lexpr;
        Object rexpr;
        int op;
        Condition c;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LPAREN:
      jj_consume_token(LPAREN);
      c = Cond();
      jj_consume_token(RPAREN);
                        {if (true) return c;}
      break;
    case IDENT_LITERAL:
    case IDENTIFIER:
    case ATSIGN:
      lexpr = Lexpr();
      op = RelOp();
      rexpr = Rexpr();
                        if (rexpr instanceof PropertyObj)
                                {if (true) return new PropertyCondition(lexpr.pn, ((PropertyObj)rexpr).pn, op, true);}
                        else
                                {if (true) return new PropertyCondition(lexpr.pn, rexpr, op);}
      break;
    default:
      jj_la1[11] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

  final public int RelOp() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case EQ:
      jj_consume_token(EQ);
                       {if (true) return Condition.OP_EQ;}
      break;
    case NE:
      jj_consume_token(NE);
                       {if (true) return Condition.OP_NE;}
      break;
    case LT:
      jj_consume_token(LT);
                       {if (true) return Condition.OP_LT;}
      break;
    case GT:
      jj_consume_token(GT);
                       {if (true) return Condition.OP_GT;}
      break;
    case LE:
      jj_consume_token(LE);
                       {if (true) return Condition.OP_LE;}
      break;
    case GE:
      jj_consume_token(GE);
                       {if (true) return Condition.OP_GE;}
      break;
    default:
      jj_la1[12] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

///
  final public long Duration() throws ParseException {
        Number n;
        double x;
    n = NumLiteral();
                        x = n.doubleValue();
                        {if (true) return (long)(x*TICKS_PER_SEC);}
    throw new Error("Missing return statement in function");
  }

  final public PropertyObj Lexpr() throws ParseException {
        PropertyObj o;
    o = Property();
                        {if (true) return o;}
    throw new Error("Missing return statement in function");
  }

  final public Object Rexpr() throws ParseException {
        Object o;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IDENT_LITERAL:
    case IDENTIFIER:
    case ATSIGN:
      o = Lexpr();
      break;
    case TRUE:
    case FALSE:
    case NULL:
    case NEW:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case QUOTED_LITERAL:
    case LBRACKET:
    case LT:
    case 61:
      o = Literal();
      break;
    default:
      jj_la1[13] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
                        {if (true) return o;}
    throw new Error("Missing return statement in function");
  }

  final public PropertyObj Property() throws ParseException {
        PropertyObj o1,o2;
    o1 = Property2();
    label_2:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case DOT:
        ;
        break;
      default:
        jj_la1[14] = jj_gen;
        break label_2;
      }
      jj_consume_token(DOT);
      o2 = Property2();
                  o1 = new PropertyObj(o1.pn+'.'+o2.pn);
    }
                        {if (true) return o1;}
    throw new Error("Missing return statement in function");
  }

  final public PropertyObj Property2() throws ParseException {
        String s;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IDENT_LITERAL:
    case IDENTIFIER:
      s = IdentStr();
                        {if (true) return new PropertyObj(s);}
      break;
    case ATSIGN:
      jj_consume_token(ATSIGN);
      s = IdentStr();
                        // todo?
                        {if (true) return new PropertyObj("vars." + s);}
      break;
    default:
      jj_la1[15] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

  final public Number NumLiteral() throws ParseException {
        Number n;
    n = NumLiteral2();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case DEG:
      jj_consume_token(DEG);
                        n = new Double(Util.toRadians(n.doubleValue()));
      break;
    default:
      jj_la1[16] = jj_gen;
      ;
    }
                        {if (true) return n;}
    throw new Error("Missing return statement in function");
  }

  final public Number NumLiteral2() throws ParseException {
        Token t;
        int neg = 1;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case 61:
      jj_consume_token(61);
                        neg=-1;
      break;
    default:
      jj_la1[17] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case INTEGER_LITERAL:
      t = jj_consume_token(INTEGER_LITERAL);
                                {if (true) return new Long( Long.parseLong(t.image) * neg );}
      break;
    case FLOATING_POINT_LITERAL:
      t = jj_consume_token(FLOATING_POINT_LITERAL);
                                {if (true) return new Double( Util.parseDouble(t.image) * neg );}
      break;
    default:
      jj_la1[18] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

  final public Boolean BoolLiteral() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case FALSE:
      jj_consume_token(FALSE);
                        {if (true) return Boolean.FALSE;}
      break;
    case TRUE:
      jj_consume_token(TRUE);
                        {if (true) return Boolean.TRUE;}
      break;
    default:
      jj_la1[19] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

  final public Object Literal() throws ParseException {
        Object o;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case 61:
      o = NumLiteral();
      break;
    case TRUE:
    case FALSE:
      o = BoolLiteral();
      break;
    case LBRACKET:
      o = VectorLiteral();
      break;
    case NEW:
      o = NewObject();
      break;
    case QUOTED_LITERAL:
      o = QuoteStr();
      break;
    case LT:
      o = FileLiteral();
      break;
    case NULL:
      jj_consume_token(NULL);
                         o=null;
      break;
    default:
      jj_la1[20] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
                        {if (true) return o;}
    throw new Error("Missing return statement in function");
  }

  final public Object NewObject() throws ParseException {
        String s;
    jj_consume_token(NEW);
    s = IdentStr();
                        {if (true) return new NewObject(s);}
    throw new Error("Missing return statement in function");
  }

  final public Object VectorLiteral() throws ParseException {
        Number xl,yl,zl=null,wl=null;
    jj_consume_token(LBRACKET);
    xl = NumLiteral();
    jj_consume_token(COMMA);
    yl = NumLiteral();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case COMMA:
      jj_consume_token(COMMA);
      zl = NumLiteral();
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COMMA:
        jj_consume_token(COMMA);
        wl = NumLiteral();
        break;
      default:
        jj_la1[21] = jj_gen;
        ;
      }
      break;
    default:
      jj_la1[22] = jj_gen;
      ;
    }
    jj_consume_token(RBRACKET);
                        if (wl != null)
                                {if (true) return new Vector4d(xl.doubleValue(), yl.doubleValue(), zl.doubleValue(), wl.doubleValue());}
                        else if (zl != null)
                                {if (true) return new Vec3d(xl.doubleValue(), yl.doubleValue(), zl.doubleValue());}
                        else
                                {if (true) return new Vector2d(xl.doubleValue(), yl.doubleValue());}
    throw new Error("Missing return statement in function");
  }

  final public String QuoteStr() throws ParseException {
        Token t;
    t = jj_consume_token(QUOTED_LITERAL);
                        String s = t.image;
                        {if (true) return StringUtil.unescape(s.substring(1,s.length()-1));}
    throw new Error("Missing return statement in function");
  }

  final public String IdentStr() throws ParseException {
        Token t;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IDENT_LITERAL:
      t = jj_consume_token(IDENT_LITERAL);
                        String s = t.image;
                        {if (true) return s.substring(1,s.length()-1);}
      break;
    case IDENTIFIER:
      t = jj_consume_token(IDENTIFIER);
                        {if (true) return t.image;}
      break;
    default:
      jj_la1[23] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

  final public String FileLiteral() throws ParseException {
        Token t;
    jj_consume_token(LT);
    t = jj_consume_token(QUOTED_LITERAL);
    jj_consume_token(GT);
                        String path = t.image;
                        try {
                                path = path.substring(1,path.length()-1);
                                {if (true) return com.fasterlight.io.IOUtil.readString(path);}
                        } catch (java.io.IOException ioe) {
                                {if (true) throw new ParseException("Could not include file \"" + path + "\"");}
                        }
    throw new Error("Missing return statement in function");
  }

  final private boolean jj_2_1(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_1();
    jj_save(0, xla);
    return retval;
  }

  final private boolean jj_2_2(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_2();
    jj_save(1, xla);
    return retval;
  }

  final private boolean jj_2_3(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_3();
    jj_save(2, xla);
    return retval;
  }

  final private boolean jj_2_4(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_4();
    jj_save(3, xla);
    return retval;
  }

  final private boolean jj_3R_25() {
    if (jj_scan_token(FLOATING_POINT_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_22() {
    if (jj_scan_token(ATSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_13()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_19() {
    if (jj_scan_token(DOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_5() {
    if (jj_scan_token(WAIT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(FOR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(CONDITION)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_24() {
    if (jj_scan_token(INTEGER_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_10() {
    if (jj_scan_token(UNTIL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_21() {
    if (jj_3R_13()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_18() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_21()) {
    jj_scanpos = xsp;
    if (jj_3R_22()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_3() {
    if (jj_3R_5()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_12() {
    if (jj_3R_15()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_2() {
    if (jj_3R_4()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_1() {
    if (jj_3R_3()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_17() {
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_4() {
    if (jj_3R_6()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_4() {
    if (jj_scan_token(WAIT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_10()) {
    jj_scanpos = xsp;
    if (jj_3R_11()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_12()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_16() {
    if (jj_scan_token(IDENT_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_13() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_16()) {
    jj_scanpos = xsp;
    if (jj_3R_17()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_6() {
    if (jj_3R_13()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(COLON)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_23() {
    if (jj_scan_token(61)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_20() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_23()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    xsp = jj_scanpos;
    if (jj_3R_24()) {
    jj_scanpos = xsp;
    if (jj_3R_25()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_14() {
    if (jj_3R_18()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_19()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_9() {
    if (jj_scan_token(ASSIGNOPT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_8() {
    if (jj_scan_token(ASSIGN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_3() {
    if (jj_3R_7()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_8()) {
    jj_scanpos = xsp;
    if (jj_3R_9()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_15() {
    if (jj_3R_20()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_7() {
    if (jj_3R_14()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_11() {
    if (jj_scan_token(FOR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  public SeqlangParserTokenManager token_source;
  ASCII_UCodeESC_CharStream jj_input_stream;
  public Token token, jj_nt;
  private int jj_ntk;
  private Token jj_scanpos, jj_lastpos;
  private int jj_la;
  public boolean lookingAhead = false;
  private boolean jj_semLA;
  private int jj_gen;
  final private int[] jj_la1 = new int[24];
  final private int[] jj_la1_0 = {0x2e000800,0x401,0x2e000800,0x2e000000,0x0,0x28000000,0x3000,0x10000,0x100000,0x200000,0x400000,0x0,0x0,0xc10c0000,0x0,0x0,0x800000,0x0,0x80000000,0xc0000,0xc10c0000,0x0,0x0,0x0,};
  final private int[] jj_la1_1 = {0x80000e0,0x0,0x80000e0,0x0,0x10100000,0x0,0x0,0x0,0x0,0x0,0x80004a0,0x80004a0,0x7e00000,0x284040e8,0x40000,0x80000a0,0x0,0x20000000,0x8,0x0,0x20404048,0x20000,0x20000,0xa0,};
  final private JJCalls[] jj_2_rtns = new JJCalls[4];
  private boolean jj_rescan = false;
  private int jj_gc = 0;

  public SeqlangParser(java.io.InputStream stream) {
    jj_input_stream = new ASCII_UCodeESC_CharStream(stream, 1, 1);
    token_source = new SeqlangParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 24; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(java.io.InputStream stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 24; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public SeqlangParser(java.io.Reader stream) {
    jj_input_stream = new ASCII_UCodeESC_CharStream(stream, 1, 1);
    token_source = new SeqlangParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 24; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 24; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public SeqlangParser(SeqlangParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 24; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(SeqlangParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 24; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  final private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      if (++jj_gc > 100) {
        jj_gc = 0;
        for (int i = 0; i < jj_2_rtns.length; i++) {
          JJCalls c = jj_2_rtns[i];
          while (c != null) {
            if (c.gen < jj_gen) c.first = null;
            c = c.next;
          }
        }
      }
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  final private boolean jj_scan_token(int kind) {
    if (jj_scanpos == jj_lastpos) {
      jj_la--;
      if (jj_scanpos.next == null) {
        jj_lastpos = jj_scanpos = jj_scanpos.next = token_source.getNextToken();
      } else {
        jj_lastpos = jj_scanpos = jj_scanpos.next;
      }
    } else {
      jj_scanpos = jj_scanpos.next;
    }
    if (jj_rescan) {
      int i = 0; Token tok = token;
      while (tok != null && tok != jj_scanpos) { i++; tok = tok.next; }
      if (tok != null) jj_add_error_token(kind, i);
    }
    return (jj_scanpos.kind != kind);
  }

  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

  final public Token getToken(int index) {
    Token t = lookingAhead ? jj_scanpos : token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  final private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.Vector jj_expentries = new java.util.Vector();
  private int[] jj_expentry;
  private int jj_kind = -1;
  private int[] jj_lasttokens = new int[100];
  private int jj_endpos;

  private void jj_add_error_token(int kind, int pos) {
    if (pos >= 100) return;
    if (pos == jj_endpos + 1) {
      jj_lasttokens[jj_endpos++] = kind;
    } else if (jj_endpos != 0) {
      jj_expentry = new int[jj_endpos];
      for (int i = 0; i < jj_endpos; i++) {
        jj_expentry[i] = jj_lasttokens[i];
      }
      boolean exists = false;
      for (java.util.Enumeration e = jj_expentries.elements(); e.hasMoreElements();) {
        int[] oldentry = (int[])(e.nextElement());
        if (oldentry.length == jj_expentry.length) {
          exists = true;
          for (int i = 0; i < jj_expentry.length; i++) {
            if (oldentry[i] != jj_expentry[i]) {
              exists = false;
              break;
            }
          }
          if (exists) break;
        }
      }
      if (!exists) jj_expentries.addElement(jj_expentry);
      if (pos != 0) jj_lasttokens[(jj_endpos = pos) - 1] = kind;
    }
  }

  final public ParseException generateParseException() {
    jj_expentries.removeAllElements();
    boolean[] la1tokens = new boolean[62];
    for (int i = 0; i < 62; i++) {
      la1tokens[i] = false;
    }
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 24; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
          if ((jj_la1_1[i] & (1<<j)) != 0) {
            la1tokens[32+j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 62; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.addElement(jj_expentry);
      }
    }
    jj_endpos = 0;
    jj_rescan_token();
    jj_add_error_token(0, 0);
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = (int[])jj_expentries.elementAt(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  final public void enable_tracing() {
  }

  final public void disable_tracing() {
  }

  final private void jj_rescan_token() {
    jj_rescan = true;
    for (int i = 0; i < 4; i++) {
      JJCalls p = jj_2_rtns[i];
      do {
        if (p.gen > jj_gen) {
          jj_la = p.arg; jj_lastpos = jj_scanpos = p.first;
          switch (i) {
            case 0: jj_3_1(); break;
            case 1: jj_3_2(); break;
            case 2: jj_3_3(); break;
            case 3: jj_3_4(); break;
          }
        }
        p = p.next;
      } while (p != null);
    }
    jj_rescan = false;
  }

  final private void jj_save(int index, int xla) {
    JJCalls p = jj_2_rtns[index];
    while (p.gen > jj_gen) {
      if (p.next == null) { p = p.next = new JJCalls(); break; }
      p = p.next;
    }
    p.gen = jj_gen + xla - jj_la; p.first = token; p.arg = xla;
  }

  static final class JJCalls {
    int gen;
    Token first;
    int arg;
    JJCalls next;
  }

}
