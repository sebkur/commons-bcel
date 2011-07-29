/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 *
 */
/* Generated By:JJTree: Do not edit this line. ASTFunDecl.java */
/* JJT: 0.3pre1 */

package Mini;
import java.io.PrintWriter;
import java.util.Iterator;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.ASTORE;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BranchHandle;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.GETSTATIC;
import org.apache.bcel.generic.GOTO;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.TargetLostException;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.InstructionFinder;

/**
 *
 * @version $Id$
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class ASTFunDecl extends SimpleNode
implements MiniParserTreeConstants, org.apache.bcel.Constants {
  private ASTIdent    name;
  private ASTIdent[]  argv;
  private ASTExpr     body;
  private int         type = T_UNKNOWN;
  private int         line, column; 
  private boolean     is_simple;  // true, if simple expression like `12 + f(a)'
  private boolean     is_recursive; // Not used yet, TODO
//  private int         max_depth; // max. expression tree depth
  private Environment env;

  // Generated methods
  ASTFunDecl(int id) {
    super(id);
  }

  ASTFunDecl(MiniParser p, int id) {
    super(p, id);
  }

  public static Node jjtCreate(MiniParser p, int id) {
    return new ASTFunDecl(p, id);
  }

  ASTFunDecl(ASTIdent name, ASTIdent[] argv, ASTExpr body, int type) {
    this(JJTFUNDECL);
    
    this.name = name;
    this.argv = argv;
    this.body = body;
    this.type = type;
  }
  
  /**
   * Overrides SimpleNode.closeNode()
   * Cast children to appropiate type.
   */
  @Override
  public void closeNode() {
    name = (ASTIdent)children[0];
    body = (ASTExpr)children[children.length - 1];

    argv = new ASTIdent[children.length - 2]; // May be 0-size array
    for(int i = 1; i < children.length - 1; i++) {
        argv[i - 1] = (ASTIdent)children[i];
    }

    children=null; // Throw away old reference
  }

  /**
   * First pass of parse tree.
   */
  public ASTFunDecl traverse(Environment env) {
    this.env = env;

    // Put arguments into hash table aka environment
    for(int i=0; i < argv.length; i++) {
      EnvEntry entry = env.get(argv[i].getName());

      if(entry != null) {
        MiniC.addError(argv[i].getLine(), argv[i].getColumn(),
                       "Redeclaration of " + entry + ".");
    } else {
        env.put(new Variable(argv[i]));
    }
    }

    /* Update entry of this function, i.e. set argument references.
     * The entry is already in there by garantuee, but may be of wrong type,
     * i.e. the user defined a function `TRUE', e.g. and `TRUE' is of type `Variable'.
     */   
    try {
      Function fun = (Function)env.get(name.getName());
      fun.setArgs(argv);
    } catch(ClassCastException e) {} // Who cares?
    
    body = body.traverse(env); // Traverse expression body

    return this;
  }

  /** Second pass
   * @return type of expression
   */
  public int eval(int pass) {
    int expected = name.getType(); // Maybe other function has already called us
    type = body.eval(expected);    // And updated the env

    if((expected != T_UNKNOWN) && (type != expected)) {
        MiniC.addError(line, column,
                     "Function f ist not of type " + TYPE_NAMES[expected] + 
                     " as previously assumed, but " + TYPE_NAMES[type]);
    }

    name.setType(type);

    is_simple = body.isSimple();

    if(pass == 2 && type == T_UNKNOWN) {
        is_recursive = true;
    }

    return type;
  }

  /**
   * Fourth pass, produce Java code.
   */
  public void code(PrintWriter out) {
    String expr;
    boolean main=false, ignore=false;

    String fname = name.getName();

    if(fname.equals("main")) {
      out.println("  public static void main(String[] _argv) {");
      main = true;
    }
    else if(fname.equals("READ") || fname.equals("WRITE")) { // Do nothing
      ignore = true;
    }
    else {
      out.print("  public static final " + "int" + // type_names[type] +
                " " + fname + "(");
      
      for(int i = 0; i < argv.length; i++) {
        out.print("int " + argv[i].getName());
        
        if(i < argv.length - 1) {
        out.print(", ");
    }
      }
      
      out.println(")\n    throws IOException\n  {");
    }
    
    if(!ignore) {
      StringBuffer buf = new StringBuffer();

      body.code(buf);
      out.println(getVarDecls());

      expr = buf.toString();

      if(main) {
        out.println("    try {");
    }

      out.println(expr);

      if(main) {
        out.println("    } catch(Exception e) { System.err.println(e); }\n  }\n");
    } else {
        out.println("\n    return " + pop() + ";\n  }\n");
    }
    }

    reset();
  }

  /**
   * Fifth pass, produce Java byte code.
   */
  public void byte_code(ClassGen class_gen, ConstantPoolGen cp) {
    MethodGen method=null;
    boolean main=false, ignore=false;
    String class_name = class_gen.getClassName();
    String fname      = name.getName();
    InstructionList il = new InstructionList();

    Type[] args = { new ArrayType(Type.STRING, 1) }; // default for `main'
    String[] arg_names = { "$argv" };

    if(fname.equals("main")) {
      method = new MethodGen(ACC_STATIC | ACC_PUBLIC,
                             Type.VOID, args, arg_names,
                             "main", class_name, il, cp);

      main = true;
    } else if(fname.equals("READ") || fname.equals("WRITE")) { // Do nothing
      ignore = true;
    } else {
      int    size  = argv.length;

      arg_names = new String[size];
      args      = new Type[size];

      for(int i = 0; i < size; i++) {
        args[i] = Type.INT;
        arg_names[i] =  argv[i].getName();
      }

      method = new MethodGen(ACC_STATIC | ACC_PRIVATE | ACC_FINAL,
                             Type.INT, args, arg_names,
                             fname, class_name, il, cp);

      LocalVariableGen[] lv = method.getLocalVariables();
      for(int i = 0; i < size; i++) {
        Variable entry = (Variable)env.get(arg_names[i]);
        entry.setLocalVariable(lv[i]);
      }

      method.addException("java.io.IOException");
    }

    if(!ignore) {
      body.byte_code(il, method, cp);

      if(main) {
        ObjectType e_type = new ObjectType("java.lang.Exception");
        InstructionHandle start = il.getStart(), end, handler, end_handler;
        LocalVariableGen exc = method.addLocalVariable("$e",
                                                       e_type,
                                                       null, null);
        int slot = exc.getIndex();

        il.append(InstructionConstants.POP); pop(); // Remove last element on stack
        end = il.append(InstructionConstants.RETURN); // Use instruction constants, if possible

        // catch
        handler = il.append(new ASTORE(slot)); // save exception object
        il.append(new GETSTATIC(cp.addFieldref("java.lang.System", "err",
                                               "Ljava/io/PrintStream;")));
        il.append(new ALOAD(slot)); push(2);
        il.append(new INVOKEVIRTUAL(cp.addMethodref("java.io.PrintStream",
                                                "println",
                                                "(Ljava/lang/Object;)V")));
        pop(2);
        end_handler = il.append(InstructionConstants.RETURN);
        method.addExceptionHandler(start, end, handler, e_type);
        exc.setStart(handler); exc.setEnd(end_handler);
      } else {
        il.append(InstructionConstants.IRETURN); // Reuse object to save memory
    }

      method.removeNOPs(); // First optimization pass, provided by MethodGen
      optimizeIFs(il);     // Second optimization pass, application-specific
      method.setMaxStack(max_size);
      class_gen.addMethod(method.getMethod());
    }

    il.dispose(); // Dispose instruction handles for better memory utilization

    reset();
  }

  private static final InstructionFinder.CodeConstraint my_constraint =
    new InstructionFinder.CodeConstraint() {
      public boolean checkCode(InstructionHandle[] match) {
        BranchInstruction if_icmp = (BranchInstruction)match[0].getInstruction();
        GOTO              goto_   = (GOTO)match[2].getInstruction();
        return (if_icmp.getTarget() == match[3]) && (goto_.getTarget() == match[4]);
      }
    };

  /**
   * Replaces instruction sequences (typically generated by ASTIfExpr) of the form
   *
   * IF_ICMP__, ICONST_1, GOTO, ICONST_0, IFEQ, Instruction
   *
   * where the IF_ICMP__ branches to the ICONST_0 (else part) and the GOTO branches
   * to the IFEQ with the simpler expression
   *
   * IF_ICMP__, Instruction
   *
   * where the IF_ICMP__ now branches to the target of the previous IFEQ instruction.
   */
  private static final void optimizeIFs(InstructionList il) {
    InstructionFinder f   = new InstructionFinder(il);
    String      pat = "IF_ICMP ICONST_1 GOTO ICONST_0 IFEQ Instruction";

    for(Iterator<InstructionHandle[]> it = f.search(pat, my_constraint); it.hasNext();) {
      InstructionHandle[] match = it.next();
      // Everything ok, update code
      BranchInstruction ifeq    = (BranchInstruction)(match[4].getInstruction());
      BranchHandle      if_icmp = (BranchHandle)match[0];

      if_icmp.setTarget(ifeq.getTarget());

      try {
        il.delete(match[1], match[4]);
      } catch(TargetLostException e) {
        InstructionHandle[] targets = e.getTargets();

        System.err.println(targets[0]);

        for(int i=0; i < targets.length; i++) {
          InstructionTargeter[] targeters = targets[i].getTargeters();

          for(int j=0; j < targeters.length; j++) {
        if((targets[i] != match[4]) || (targeters[j] != match[2])) {
            System.err.println("Ooops: " + e);
        }
    }
        }
      }
    }
  }

  /**
   * Overrides SimpleNode.toString()
   */
  @Override
  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append(jjtNodeName[id] + " " + name + "(");

    for(int i = 0; i < argv.length; i++) {
      buf.append(argv[i].getName());
      if(i < argv.length - 1) {
        buf.append(", ");
    }
    }

    buf.append(")");
    return buf.toString();
  }

  public boolean    isrecursive()         { return is_recursive; }
  public boolean    isSimple()            { return is_simple; }
  public ASTIdent   getName()             { return name; }
  public int        getNoArgs()           { return argv.length; }
  public ASTIdent[] getArgs()             { return argv; }
  public int        getType()             { return type; }
  public void       setType(int type)     { this.type = type; }
  public void       setLine(int line)     { this.line = line; }
  public int        getLine()             { return line; }
  public void       setColumn(int column) { this.column = column; }
  public int        getColumn()           { return column; }
  public void       setPosition(int line, int column) {
    this.line = line;
    this.column = column;
  }

  /**
   * Overrides SimpleNode.dump()
   */
  @Override
  public void dump(String prefix) {
    System.out.println(toString(prefix));

    for(int i = 0; i < argv.length; i++) {
        argv[i].dump(prefix + " ");
    }

    body.dump(prefix + " ");
  }

  /* Used to simpulate stack with local vars and compute maximum stack size.
   */
  static int size, max_size;

  static final void reset() { size = max_size = 0; }

  private static final String getVarDecls() {
    StringBuffer buf = new StringBuffer("    int ");

    for(int i=0; i < max_size; i++) {
      buf.append("_s" + i);

      if(i < max_size - 1) {
        buf.append(", ");
    }
    }

    buf.append(";\n");
    return buf.toString();
  }

  /** Used by byte_code()
   */
  static final void pop(int s) { size -= s; }
  static final void push(int s) {
    size += s;

    if(size > max_size) {
        max_size = size;
    }
  }
  static final void push() { push(1); }

  /** Used byte code()
   */
  static final void push(StringBuffer buf, String str) {
    buf.append("    _s" + size + " = " + str + ";\n");
    push(1);
  }

  static final String pop() {
    return "_s" + (--size);
  }
}
