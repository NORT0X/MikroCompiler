package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatCodePointException;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.*;
import org.apache.log4j.Logger;

public class CodeGenerator extends VisitorAdaptor {
	Logger log = Logger.getLogger(getClass());
	
	private int mainPC = -1;
	
	private Map<String, Integer> mapClassVFTAddr = new HashMap<String, Integer>();
	
	private List<Obj> virtualMethList = new ArrayList<Obj>();
	
	private Stack<Obj> funcNodesInCallStack = new Stack<Obj>();
	
	private List<Obj> classNodesList = new ArrayList<Obj>();
	private Obj currClassObj = MJTab.noObj;
	
	// TODO: not needed for this project
	// There are usages of this flag but it is always false so that code won't execute
	private boolean superMethFlag = false;
	
	// Here we have static addrs to static inits;
	private List<Integer> staticAddrs = new ArrayList<Integer>();
	
	// Address to fix jmp from static
	private Stack<Integer> staticInitsJMPToFix = new Stack<Integer>();
	 
	public int getMainPC() {
		return mainPC;
	}
	
	// =============
  	// VIRTUAL TABLE
  	// =============
	
	private void createVFT() {
		int staticDataAreaTop = SemanticAnalyzer.getProgramNumVariables();
		
		for (Obj classNode : classNodesList) {
			mapClassVFTAddr.put(classNode.getName(), staticDataAreaTop);
			
			for (Obj classMember : classNode.getType().getMembers()) {
				if (classMember.getKind() == Obj.Meth) {
					for (int i = 0; i < classMember.getName().length(); ++i) {
						Code.loadConst(classMember.getName().charAt(i));
						
						Code.put(Code.putstatic);
						Code.put2(staticDataAreaTop);
						staticDataAreaTop++;
					}
					
					Code.loadConst(-1);
					
					Code.put(Code.putstatic);
					Code.put2(staticDataAreaTop);
					staticDataAreaTop++;
					
					Code.loadConst(classMember.getAdr());
					
					Code.put(Code.putstatic);
					Code.put2(staticDataAreaTop);
					staticDataAreaTop++;
				}
			}
			Code.loadConst(-2);
			Code.put(Code.putstatic);
			Code.put2(staticDataAreaTop);
			staticDataAreaTop++;
		}
		
		SemanticAnalyzer.setProgramNumVariables(staticDataAreaTop);
	}
	
	private boolean isMethVirtual(Obj meth) {
		for(Obj virtual : virtualMethList) {
			if(virtual.equals(meth)) {
				return true;
			}
		}
		return false;
	}
	
	// =============
  	// CLASS
  	// =============
	
	// TODO: constructors
	
	public void visit(ClassDecl cDecl) {
		currClassObj = MJTab.noObj;
	}
	
	public void visit(ClassName cName) {
		classNodesList.add(cName.obj);
		currClassObj = cName.obj;
	}
	
	// =============
  	// METHOD
  	// =============
	
	public void visit(MethodName method) {
		method.obj.setAdr(Code.pc);
		
		if (currClassObj != MJTab.noObj) {
			virtualMethList.add(method.obj);
		}
		
		if("main".equals(method.obj.getName())) {
			// First let's check if there is fix for static inits block
			if (!staticInitsJMPToFix.empty()) {
				// log.info(staticInitsJMPToFix.peek());
				Code.fixup(staticInitsJMPToFix.peek());
				staticInitsJMPToFix.pop();
				
				// Set mainPC as first element of staticAddrs
				mainPC = staticAddrs.get(0);
			} else {
				mainPC = Code.pc;
			}
			// log.info("main " + mainPC);
		}
		
		// Code for entering func
		Code.put(Code.enter);
		Code.put(method.obj.getLevel()); // level = number of formal args
		Code.put(method.obj.getLocalSymbols().size()); // local symbol size = form args + local vars
		// log.info(Code.pc);
		if (mainPC != -1) {
			createVFT();
		}
	}
	
	public void visit(MethodDecl method) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	// =============
  	// STATIC INIT
  	// =============
	
	public void visit(StaticStmtStart staticStart) {
		// Add start of static stmts to list of addresses
		staticAddrs.add(Code.pc);
		// log.info(Code.pc);
		
		// If there is element in stack of addrs to be fixed in static inits
		// Then fix it
		if (!staticInitsJMPToFix.empty()) {
			Code.fixup(staticInitsJMPToFix.peek());
			staticInitsJMPToFix.pop();
		}
	}
	
	public void visit(StaticStmtEnd staticEnd) {
		// Jump to another static init or main
		Code.putJump(0);
		staticInitsJMPToFix.push(Code.pc-2);
	}
	
	// =============
  	// STATEMENTS
  	// =============

	public void visit(MatchedReturnEmpty ret) {
		// No value on expr stack
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	public void visit(MatchedReturnExpr ret) {
		// Return value is on expr stack
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	public void visit(MatchedPrintNoWidth print) {
		if (print.getExpr().struct == MJTab.intType) {
			Code.loadConst(5);
			Code.put(Code.print);
		} else if (print.getExpr().struct == MJTab.boolType) {
			Code.loadConst(5);
			Code.put(Code.print);
		} else {
			Code.loadConst(1);
			Code.put(Code.bprint);
		}
	}
	
	public void visit(MatchedPrintWithWidth print) {
		Code.loadConst(print.getWidth());
		if (print.getExpr().struct == MJTab.intType) {
			Code.put(Code.print);
		} else if (print.getExpr().struct == MJTab.boolType) {
			Code.put(Code.print);
		} else {
			Code.put(Code.print);
		}
	}
	
	public void visit(MatchedRead read) {
		if (read.getDesignator().obj.getType() == MJTab.charType) {
			Code.put(Code.bread);
		} else {
			Code.put(Code.read);
		}
		
		Code.store(read.getDesignator().obj);
	}
	
	// =============
  	// FUNCTION CALLS
  	// =============
	
	public void visit(FuncCallName funcName) {
		funcNodesInCallStack.push(funcName.obj);
		
		if (isMethVirtual(funcName.obj)) {
			Code.put(Code.dup);
		}
	}
	
	private void factorFuncCall(Obj factor) {
		Obj methObj = factor;
		
		if ("len".equals(methObj.getName())) {
			callLenMeth();
			return;
		}
		
		if ("ord".equals(methObj.getName())) {
			return;
		}
		
		if ("chr".equals(methObj.getName())) {
			return;
		}
		
		if (isMethVirtual(methObj)) {
			if (superMethFlag) {
				Code.put(Code.pop);
				
				int overridenAddr = methObj.getAdr();
				int offset = overridenAddr - Code.pc;
				
				Code.put(Code.call);
				Code.put2(offset);
			} else {
				// Get vft that is always on position 0 inside class
				Code.put(Code.getfield);
				Code.put2(0); 
				
				Code.put(Code.invokevirtual);
				
				for (int i = 0; i < methObj.getName().length(); ++i) {
					Code.put4(methObj.getName().charAt(i));
				}
				Code.put4(-1);
			}
		} else {
			int offset = methObj.getAdr() - Code.pc;
			
			Code.put(Code.call);
			Code.put2(offset);
		}
		
		funcNodesInCallStack.pop();
		superMethFlag = false;
	}
	
	private void designatorFuncCall(Obj designator) {
		Obj methObj = designator;
		
		if ("len".equals(methObj.getName())) {
			callLenMeth();
			return;
		}
		
		if (isMethVirtual(methObj)) {
			if (superMethFlag) {
				Code.put(Code.pop);
				
				int overridenAddr = methObj.getAdr();
				int offset = overridenAddr - Code.pc;
				
				Code.put(Code.call);
				Code.put2(offset);
			} else {
				// Get vft that is always on position 0 inside class
				Code.put(Code.getfield);
				Code.put2(0); 
				
				Code.put(Code.invokevirtual);
				
				for (int i = 0; i < methObj.getName().length(); ++i) {
					Code.put4(methObj.getName().charAt(i));
				}
				Code.put4(-1);
			}
			
			if(methObj.getType() != MJTab.noType) {
				Code.put(Code.pop);
			}
		} else {
			int offset = methObj.getAdr() - Code.pc;
			
			Code.put(Code.call);
			Code.put2(offset);
			
			if (methObj.getType() != MJTab.noType) {
				Code.put(Code.pop);
			}
		}
		
		funcNodesInCallStack.pop();
		superMethFlag = false;
	}
	
	public void visit(FactorFunctionCallNoPar factor) {
		factorFuncCall(factor.getFuncCallName().obj);
	}
	
	public void visit(FactorFunctionCallWithPar factor) {
		factorFuncCall(factor.getFuncCallName().obj);
	}
	
	public void visit(DesignatorStatementExprs designator) {
		designatorFuncCall(designator.getFuncCallName().obj);
	}
	
	public void visit(DesignatorStatementExprsActPars designator) {
		designatorFuncCall(designator.getFuncCallName().obj);
	}
	
	// =============
  	// PARAMETERS
  	// =============
	
	public void visit(OneActPar param) {
		if (isMethVirtual(funcNodesInCallStack.peek())) {
			Code.put(Code.dup_x1);
			Code.put(Code.pop);
		}
	}
	
	public void visit(ActParams param) {
		if (isMethVirtual(funcNodesInCallStack.peek())) {
			Code.put(Code.dup_x1);
			Code.put(Code.pop);
		}
	}
	
	// =============
  	// DESIGNATOR
  	// =============
	
	public void visit(DesignatorField designator) {
		if ("this".equals(designator.getDesignator().obj.getName())) {
			Code.load(designator.getDesignator().obj);
			return;
		}
		
		if (designator.obj.getName().contains("::")) {
			// log.info(designator.obj.getName() + " " + Code.pc);
			Code.load(designator.obj);
			return;
		}  
		// log.info(designator.getDesignator().obj.getName() + " " + Code.pc);
		Code.load(designator.getDesignator().obj);
	}
	
	public void visit(ArrayName designator) {
		// log.info(designator.getDesignator().obj.getName() + " " + designator.getDesignator().obj.getAdr());
		Code.load(designator.getDesignator().obj);
		
	}
	
	public void visit(DesignatorNameInside designator) {
		if (designator.obj.getKind() == Obj.Fld) {
			// TODO: fix code syntax
			if (designator.getParent() instanceof DesignatorStatement) {
				Code.put(Code.load_n + 0);
			}
			if (designator.getParent() instanceof FactorDesignator) {
				Code.put(Code.load_n + 0);
			}
		}
		
		if (isMethVirtual(designator.obj)) {
			
			if (designator.getParent() instanceof FuncCallName) {
				Code.put(Code.load_n + 0);
			}
		}
	}
	
	public void visit(DesignatorNameOut designator) {
		if (designator.obj.getKind() == Obj.Fld) {
			// TODO: fix code syntax
			if (designator.getParent() instanceof DesignatorStatement) {
				Code.put(Code.load_n + 0);
			}
			if (designator.getParent() instanceof FactorDesignator) {
				Code.put(Code.load_n + 0);
			}
		}
		
		if (isMethVirtual(designator.obj)) {
			if (designator.getParent() instanceof FuncCallName) {
				Code.put(Code.load_n + 0);
			}
		}
	}
	
	public void visit(DesignatorStatementExprsInc designator) {
		if (designator.getDesignator().obj.getKind() == Obj.Var) {
			Code.load(designator.getDesignator().obj);
		} else if (designator.getDesignator().obj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
			Code.load(designator.getDesignator().obj);
		} else if (designator.getDesignator().obj.getKind() == Obj.Fld) {
			Code.put(Code.dup);
			Code.load(designator.getDesignator().obj);
		}
		
		Code.loadConst(1);
		Code.put(Code.add);
		
		Code.store(designator.getDesignator().obj);
	}
	
	public void visit(DesignatorStatementExprsDec designator) {
		if (designator.getDesignator().obj.getKind() == Obj.Var) {
			Code.load(designator.getDesignator().obj);
		} else if (designator.getDesignator().obj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
			Code.load(designator.getDesignator().obj);
		} else if (designator.getDesignator().obj.getKind() == Obj.Fld) {
			Code.put(Code.dup);
			Code.load(designator.getDesignator().obj);
		}
		
		Code.loadConst(1);
		Code.put(Code.sub);
		
		Code.store(designator.getDesignator().obj);
	}
	
	public void visit(DesignatorStatementExprsAssign designator) {
		// Everything should already be on expr stack
		// log.info(designator.getDesignator().obj.getName() + " " + designator.getDesignator().obj.getKind());
		Code.store(designator.getDesignator().obj);
	}
	
	// =============
  	// DESIGNATOR ARRAY ASSIGNMENT
  	// =============
	
	private Obj arrayIndex1 = MJTab.noObj;
	private Obj arrayIndex2 = MJTab.noObj;
	
	public void visit(ProgName prog) {
		Obj program = MJTab.find(prog.getProgName());
		for (Obj local : program.getLocalSymbols()) {
			if ("$arrayIndex1".equals(local.getName())) {
				arrayIndex1 = local;
			}
			if ("$arrayIndex2".equals(local.getName())) {
				arrayIndex2 = local;
			}
		}
		
	}
	
	private List<Obj> designatorsInAarrayAssignment = new ArrayList<Obj>();
	
	public void visit(OneDesignators designatorElem) {
		designatorsInAarrayAssignment.add(designatorElem.getDesignator().obj);
	}
	public void visit(DesignatorStatementArrayAssignment designator) {
		// There is global variable $arrayIndex that is used for calculating current element
		
		// First let's store elements of designators
		// For that we can use staticIndex because we know number of elements before runtime
		
		Obj elemType = new Obj(Obj.Elem, "$dummy", designator.getDesignator().obj.getType().getElemType());
		
		// Init arrayIndex1 and arrayIndex2 to zero
		loadConstInt(0);
		Code.store(arrayIndex1);
		loadConstInt(0);
		Code.store(arrayIndex2);
		
		for (Obj element : designatorsInAarrayAssignment) {
			Code.load(designator.getDesignator1().obj);
			Code.load(arrayIndex2);
			Code.load(elemType);
			Code.store(element);
			
			// Increment arrayIndex2
			loadConstInt(1);
			Code.load(arrayIndex2);
			Code.put(Code.add);
			Code.store(arrayIndex2);
		}
		
		// Now we should make some iteration through array from left side
		if (designator.getDesignator().obj != MJTab.noObj) {
			// Now we should make some iteration through array from left side
			
			Code.load(designator.getDesignator().obj);
			Code.load(arrayIndex1);
			
			Code.load(designator.getDesignator1().obj);
			Code.load(arrayIndex2);
			Code.load(elemType);
			
			Code.store(elemType);
			
			// Increment arrayIndex1 and arrayIndex2
			loadConstInt(1);
			Code.load(arrayIndex1);
			Code.put(Code.add);
			Code.store(arrayIndex1);
			
			loadConstInt(1);
			Code.load(arrayIndex2);
			Code.put(Code.add);
			Code.store(arrayIndex2);
			
			// Compare arrayIndex1 and len
			Code.load(arrayIndex1);
			Code.load(designator.getDesignator().obj);
			Code.put(Code.arraylength);
			Code.put(Code.jcc+Code.lt);
			Code.put2(-37);
		}
		
		// If arrayIndex1 is not equal to arraylen-1 of getDesignator1() than trap
		Code.load(arrayIndex2);
		Code.load(designator.getDesignator1().obj);
		Code.put(Code.arraylength);
		Code.put(Code.jcc+Code.le);
		Code.put2(5);
		Code.put(Code.trap);
		Code.put(1);
		
		
		designatorsInAarrayAssignment.clear();
	}

	// =============
  	// FACTOR
  	// =============	
	
	public void visit(FactorDesignator factor) {
		// Load method of code will process Obj accordingly
		// log.info(factor.getDesignator().obj.getName() + " " + factor.getDesignator().obj.getKind());
		// log.info(Code.pc + " " + factor.getDesignator().obj.getName() + " " + factor.getDesignator().obj.getKind());
		Code.load(factor.getDesignator().obj);
	}

	public void visit(FactorNewArray factor) {
		// Assuming that number of elements on expr stack was already put on expr stack
		Code.put(Code.newarray);
		
		// If character then size of element = 1B
		// Else then size of element = 4B
		if (factor.struct.getElemType() == MJTab.charType) {
			Code.put(0);
		} else {
			Code.put(1);
		}
	}
	
	public void visit(FactorNewObject factor) {
		Code.put(Code.new_);
		Code.put2(factor.struct.getNumberOfFields() * 4);
		
		Code.put(Code.dup);

		if (factor.getType() instanceof TypeOutside) {
			String typeName = ((TypeOutside)(factor.getType())).getSpaceName() + "::" + ((TypeOutside)(factor.getType())).getTypeName();

			if (mapClassVFTAddr.get(typeName) == null) {
				Code.loadConst(0);
			} else {
				Code.loadConst(mapClassVFTAddr.get(typeName));
			}
		} else if (factor.getType() instanceof OrdinaryType) {
			String typeName = ((OrdinaryType)(factor.getType())).getTypeName();
			
			if (mapClassVFTAddr.get(typeName) == null) {
				Code.loadConst(0);
			} else {
				Code.loadConst(mapClassVFTAddr.get(typeName));
			}
		}
		
		Code.put(Code.putfield);
		Code.put2(0);
		
	}
	
	// =============
  	// CONST VALUES
  	// =============
	
	private void loadConstInt(int number) {
		Obj con = MJTab.insert(Obj.Con, "$const", MJTab.intType);
		con.setLevel(0);
		con.setAdr(number);
		
		Code.load(con);
	}
	
	public void visit(ConstNumber number) {
		Obj con = MJTab.insert(Obj.Con, "$const", number.struct);
		con.setLevel(0);
		con.setAdr(number.getConstNumber());
		
		Code.load(con);
	}
	
	public void visit(ConstBool bool) {
		Obj con = MJTab.insert(Obj.Con, "$const", bool.struct);
		con.setLevel(0);
		con.setAdr(bool.getConstBool() ? 1 : 0);
		
		Code.load(con);
	}
	
	public void visit(ConstChar character) {
		Obj con = MJTab.insert(Obj.Con, "$const", character.struct);
		con.setLevel(0);
		con.setAdr(character.getConstChar());
		
		Code.load(con);
	}

	// =============
  	// EXPR
  	// =============
	
	public void visit(NegativeTerm expr) {
		// Neg value from expr stack and put it back
		Code.put(Code.neg);
	}
	
	public void visit(TermWithAdds expr) {
		// Use two values from expr stack and add/sub them and put result to the expr stack
		if (expr.getAddop() instanceof  Plus) {
			Code.put(Code.add);
		} else {
			Code.put(Code.sub);
		}
	}
	
	public void visit(MullFactorsWith factor) {
		// Use two values from expr stack and mul/div/mod them and put result to the expr stack
		if (factor.getMulop() instanceof Mul) {
			Code.put(Code.mul);
		} else if (factor.getMulop() instanceof Div) {
			Code.put(Code.div);
		} else {
			Code.put(Code.rem); // Mod op
		}
	}
	
	// =============
  	// IF-STATEMENT AND FOR-LOOP
  	// =============
	
	private Stack<List<Integer>> dstAddrToFixORStack = new Stack<List<Integer>>();
	
	private Stack<List<Integer>> dstAddrToFixANDStack = new Stack<List<Integer>>();
	
	private Stack<List<Integer>> dstAddrToFixJMPFromThenBlockStack = new Stack<List<Integer>>();
	
	

	private Stack<List<Integer>> addrToFixFromBreakStack = new Stack<List<Integer>>();
	
	private Stack<Integer> forAddrCondStart = new Stack<Integer>();
	
	private Stack<Integer> forAddrUpdateStart = new Stack<Integer>();
	
	private Stack<Integer> forAddrFIXToUpdateEnd = new Stack<Integer>();
	
	public void visit(IfCondStart ifstmt) {
		dstAddrToFixORStack.push(new ArrayList<Integer>());
		dstAddrToFixANDStack.push(new ArrayList<Integer>());
		dstAddrToFixJMPFromThenBlockStack.push(new ArrayList<Integer>());
	}
	
	public void visit(CondFactExpr cond) {
		// Expr stack has bool value on top
		Code.loadConst(1);
		Code.putFalseJump(Code.eq, 0);
		dstAddrToFixANDStack.peek().add(Code.pc - 2);
	}
	
	public void visit(CondFactWithRelop cond) {
		// Expr stack looks like
		// int
		// int
		
		if (cond.getRelop() instanceof Eq) {
			Code.putFalseJump(Code.eq, 0);
		} else if(cond.getRelop() instanceof Neq) {
			Code.putFalseJump(Code.ne, 0);
		} else if(cond.getRelop() instanceof Lt) {
			Code.putFalseJump(Code.lt, 0);
		} else if(cond.getRelop() instanceof Gt) {
			Code.putFalseJump(Code.gt, 0);
		} else if(cond.getRelop() instanceof Geq) {
			Code.putFalseJump(Code.ge, 0);
		} else if(cond.getRelop() instanceof Leq) {
			Code.putFalseJump(Code.le, 0);
		}
		
		dstAddrToFixANDStack.peek().add(Code.pc - 2);
	}
	
	public void visit(IfCondEnd ifstmt) {
		for (int placeToFix : dstAddrToFixORStack.peek()) {
			Code.fixup(placeToFix);
		}
		
		dstAddrToFixORStack.peek().clear();
	}
	
	public void visit(OREnd orEnd) {
		Code.putJump(0);
		dstAddrToFixORStack.peek().add(Code.pc-2);
		
		for (int placeToFix : dstAddrToFixANDStack.peek()) {
			Code.fixup(placeToFix);
		}
		
		dstAddrToFixANDStack.peek().clear();
	}
	
	
	public void visit(ElseStart elseStart) {
		
		if (elseStart.getParent() instanceof MatchedIfElseMultiple) {
			Code.putJump(0);
			dstAddrToFixJMPFromThenBlockStack.peek().add(Code.pc - 2);
		}
		 
		for (int placeToFix : dstAddrToFixANDStack.peek()) {
			Code.fixup(placeToFix);
		}
		
		dstAddrToFixANDStack.peek().clear();
	}
	
	public void visit(MatchedIfElseMultiple ifstmt) {
		for (int placeToFix : dstAddrToFixJMPFromThenBlockStack.peek()) {
			Code.fixup(placeToFix);
		}
		
		dstAddrToFixJMPFromThenBlockStack.peek().clear();
		dstAddrToFixJMPFromThenBlockStack.pop();
		
		
		dstAddrToFixANDStack.pop();
		dstAddrToFixORStack.pop();
	}
	
	public void visit(MatchedIfMultiple ifstmt) {	
		dstAddrToFixJMPFromThenBlockStack.pop();
		dstAddrToFixANDStack.pop();
		dstAddrToFixORStack.pop();
	}
	
	public void visit(ForStart forStart) {
		
		// New list of address to be fixed for CondFact inside for condition
		dstAddrToFixANDStack.push(new ArrayList<Integer>());
		
		// Add new lists to store address to fix jumps for break
		addrToFixFromBreakStack.push(new ArrayList<Integer>());
	}
	
	public void visit(ForCondStart forCond) {
		// Save address of the current pc to repeat for loop
		// Also used for continue
		
		forAddrCondStart.push(Code.pc);
		
	}
	
	public void visit(ForCondEnd forCond) {
		// Skip update part because it should be executed after for body
		Code.putJump(0);
		forAddrFIXToUpdateEnd.push(Code.pc-2);
	}
	
	public void visit(ForUpdateStart forUpdate) {
		// Save address for this so after end of loop we can jump here
		forAddrUpdateStart.push(Code.pc);
	}
	
	public void visit(ForUpdateEnd forUpdate) {
		// Here jump to forCond
		Code.putJump(forAddrCondStart.peek());
		// And fix forCondEnd part where they skip this part
		Code.fixup(forAddrFIXToUpdateEnd.peek());
	}
	
	public void visit(ForEnd forEnd) {
		// First we should jump back to ForCondStart
		Code.putJump(forAddrUpdateStart.peek());
		
		
		
		// This is place where it should jump CondFact with relop is false
		// It should be only one element in the list
		if (!dstAddrToFixANDStack.peek().isEmpty()) {
			for (int placeToFix : dstAddrToFixANDStack.peek()) {
				Code.fixup(placeToFix);
			}
		}
		
		// Also here is where break shold go
		for (int placeToFix : addrToFixFromBreakStack.peek()) {
			Code.fixup(placeToFix);
		}
		
		dstAddrToFixANDStack.pop();
		addrToFixFromBreakStack.pop();
		
		forAddrCondStart.pop();
		forAddrUpdateStart.pop();
		forAddrFIXToUpdateEnd.pop();
		
	}
	
	public void visit(MatchedBreak mBreak) {
		Code.putJump(0);
		
		addrToFixFromBreakStack.peek().add(Code.pc -2);
	}
	
	public void visit(MatchedContinue mContinue) {
		Code.putJump(forAddrUpdateStart.peek());
	}
	
	
	private void callLenMeth() {
		// there is an array address on exprStack
		Code.put(Code.arraylength);
	}
}
