package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import java.util.Stack;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import rs.ac.bg.etf.pp1.util.Analyzer;
import rs.ac.bg.etf.pp1.ast.*;

import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {
	private final Analyzer analyzer = new Analyzer(MJParser.class);
	public int printCallCount = 0;
	public int varDeclCount = 0;
	
	private String currNamespace;
	
	private boolean errorDetected;
	
	private Obj requiredType = MJTab.noObj;
	
	private Obj currClassObj = MJTab.noObj;
	private Struct currClassStruct = null;
	private Obj currClassExtendObj = MJTab.noObj;
	
	private Obj currMethod = MJTab.noObj;
	private int methodParNum = 0;
	private int methodParAddr = 0;
	
	private boolean fieldsFinished = false;
	private boolean staticVarDeclared = false;
	private boolean staticProgressing = false;
	
	private Obj designatorVar = MJTab.noObj;
	
	private boolean classMethod = false;
	private Stack<List<Struct>> stackOfActualParameters = new Stack<List<Struct>>();
	
	enum RelOpEnum { EQUAL, NOT_EQUAL, LESS, GREATER, GREATER_EQUAL, LESS_EQUAL }
	
	private RelOpEnum relationalOperation = null;
	
	private List<Obj> arrayAssignmentDesignators = new ArrayList<Obj>();
	
	private int forDepth = 0;
	
	private boolean returnFound = false;
	
	private static int programNumVariables = 0;
	
	Logger log = Logger.getLogger(getClass());
	
	public boolean passed() {
		return !errorDetected;
	}
	
	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" (on line ").append(line).append(")");
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message); 
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" (on line ").append(line).append(")");
		log.info(msg.toString());
	}
	
	public static int getProgramNumVariables() {
		return programNumVariables;
	}
	
	public static void setProgramNumVariables(int newProgNumVar) {
		programNumVariables = newProgNumVar;
	}


	public void visit(VarDecls vardecl){
		varDeclCount++;
	}
    
    public void visit(ProgName progName) {
    	progName.obj = infoInsert(progName, Obj.Prog, progName.getProgName(), MJTab.noType);
    	MJTab.openScope();
    	
    	// Add one global variable $arrayIndex just for array assignment operator
    	MJTab.insert(Obj.Var, "$arrayIndex1", MJTab.intType);
    	MJTab.insert(Obj.Var, "$arrayIndex2", MJTab.intType);
    }
    
    public void visit(Program program) {
    	
    	programNumVariables = MJTab.currentScope.getnVars();
    	
    	MJTab.chainLocalSymbols(program.getProgName().obj);
    	
    	Obj main = MJTab.findInCurrentScope("main");
    	
    	if (main == MJTab.noObj || main.getKind() != Obj.Meth) {
    		report_error("No main function defined.", program);
    	}
    	
    	MJTab.closeScope();
    }
    
    // =============
    // NAMESPACE
    // =============
    public void visit(NamespaceName namespaceName)
    {
    	currNamespace = namespaceName.getNamespace();
    }
    
    public void visit(Namespace namespace)
    {
    	currNamespace = null;
    }
    
    
    // =============
    // CLASS
    // =============
    public void visit(ClassName className) {
    	String spaceName = "";
    	if (currNamespace != null) {
    		spaceName += currNamespace + "::";
    	}
    	
    	currClassStruct = new Struct(Struct.Class);
    	
    	Struct superClassStruct = currClassExtendObj.getType();
    	
    	currClassStruct.setElementType(superClassStruct);
    	
    	className.obj = infoInsert(className, Obj.Type, spaceName + className.getClassName(), currClassStruct);
    	currClassObj = className.obj;
    	
    	// Start of static variables
    	staticVarDeclared = false;
    }
    
    public void visit(ClassDecl classDecl) {
    	MJTab.chainLocalSymbols(classDecl.getClassName().obj.getType());
    	report_info("Finishing class " + currClassObj.getName(), classDecl);
    	currClassObj = MJTab.noObj;
    	currClassStruct = null;
    	currClassExtendObj = MJTab.noObj;
    	
    	MJTab.closeScope();
    	report_info("Number of fields: " + classDecl.getClassName().obj.getType().getNumberOfFields(), classDecl);
    }
    
    public void visit(ClassExtending extension) {
    	Obj superclassObj = extension.getType().obj;
    	report_info("Extending with " + superclassObj.getName(), extension);
    	Struct superclassStruct = superclassObj.getType();
    	
    	currClassExtendObj = superclassObj;
    }
    
    /*
    * This section is after static variable declarations
    * But we need to stop static variable decleartion here
    */
    public void visit(NoClassStaticInits staticVars) { 
    	report_info("Finished static decleration!", staticVars);
    	staticVarDeclared = true;
    	MJTab.openScope();
    	
    	MJTab.insert(Obj.Fld, "VTF_address", MJTab.intType);
    	
    	// If currClass is extended from other class than copy fields
    	if (currClassExtendObj != MJTab.noObj) {
	    	for (Obj classMember : currClassExtendObj.getType().getMembers()) {
	    		if (classMember.getKind() == Obj.Fld && !"VTF_address".equals(classMember.getName())) {
	    			MJTab.insert(Obj.Fld, classMember.getName(), classMember.getType());
	    		}
	    	}
    	}
    }
    
    /*
     * After fields are finished copy methods from super class
     */
    public void visit(NoClassListVarDeclaration extendMethod) {
    	// If currClass is extended copy methods
    	if (currClassExtendObj != MJTab.noObj) {
    		for (Obj classMember : currClassExtendObj.getType().getMembers()) {
	    		if (classMember.getKind() == Obj.Meth) {
	    			MJTab.currentScope.addToLocals(classMember);
	    		}
	    	}
    	}
    }
    
    // =============
 	// STATIC START END
 	// =============
    
    public void visit(StaticStart start) {
    	staticProgressing = true;
    }
    
    public void visit(StaticEnd end) {
    	staticProgressing = false;
    }
 
	// =============
	// TYPE
	// =============
    public void visit(TypeOutside type) {
    	String name = type.getSpaceName() + "::" + type.getTypeName();
    	Obj typeNode = MJTab.find(name);
    	type.obj = MJTab.noObj;
    	
    	if (typeNode == MJTab.noObj) {
    		// report_error("Nije pronadjen tip " + type.getTypeName(), type);
    	}
    	
    	type.obj = typeNode;
    	requiredType = type.obj;
    }
    
    public void visit(OrdinaryType type) {
    	String name = type.getTypeName();
    	Obj typeNode = MJTab.find(name);
    	type.obj = MJTab.noObj;
    	
    	// If type is inside namespace try to find with current namespace
    	// We are sure that only current name space will be checked, because TypeOutside covers for outside namespaces
    	if (typeNode == MJTab.noObj && currNamespace != null) {
    		typeNode = MJTab.find(currNamespace + "::" + name);
    	}
    	
    	if (typeNode == MJTab.noObj) {
    		// report_error("Nije pronadjen tip " + type.getTypeName() + " untar ili van namespace-a!", type);
    	}
    	
    	type.obj = typeNode;
    	requiredType = type.obj;
    }
    
    // =============
    // Variables (fields)
    // =============
    public void visit(VarVariable variable) {
    	if (currClassObj != MJTab.noObj && currMethod == MJTab.noObj && staticVarDeclared == true) {
    		// FIELD
    		infoInsert(variable, Obj.Fld, variable.getVarName(), requiredType.getType());
    	}
    	if (currClassObj != MJTab.noObj && currMethod == MJTab.noObj && staticVarDeclared == false) {
    		// STATIC FIELD
    		String name = "";
    		if (currNamespace != null) {
    			name += currNamespace + "::";
    		}
    		name += currClassObj.getName() + "::";
    		name += variable.getVarName();
    		report_info("Found static variable " + name, variable);
    		
    		infoInsert(variable, Obj.Var, name, requiredType.getType());
    	}
    	if (currMethod != MJTab.noObj) { // Method formal parametar
    		// report_info("Test", variable);
    		Obj obj = infoInsert(variable, Obj.Var, variable.getVarName(), requiredType.getType());
    		obj.setLevel(2);
    		obj.setAdr(methodParAddr);
    		methodParNum++;
    		methodParAddr++;
    	}
    	
    	if (currClassObj == MJTab.noObj && currMethod == MJTab.noObj) {
    		// GLOBAL VARIABLE
    		String name = "";
    		if (currNamespace != null) {
    			name += currNamespace + "::";
    		}
    		name += variable.getVarName();
    		infoInsert(variable, Obj.Var, name, requiredType.getType());
    	}
    }
    
    public void visit(VarArray variable) {
    	if (currClassObj != MJTab.noObj && currMethod == MJTab.noObj && staticVarDeclared == true) {
    		// FIELD
    		infoInsert(variable, Obj.Fld, variable.getVarName(), new Struct(Struct.Array, requiredType.getType()));
    	}
    	if (currClassObj != MJTab.noObj && currMethod == MJTab.noObj && staticVarDeclared == false) {
    		// STATIC FIELD
    		String name = "";
    		if (currNamespace != null) {
    			name += currNamespace + "::";
    		}
    		name += currClassObj.getName() + "::";
    		name += variable.getVarName();
    		
    		report_info("Found static variable " + name, variable);
    		
    		infoInsert(variable, Obj.Var, name, new Struct(Struct.Array, requiredType.getType()));
    	}
    	if (currMethod != MJTab.noObj) { // Method formal parametar
    		infoInsert(variable, Obj.Var, variable.getVarName(), new Struct(Struct.Array, requiredType.getType()));
    	}
    	if (currClassObj == MJTab.noObj && currMethod == MJTab.noObj) {
    		// GLOBAL VARIABLE
    		String name = "";
    		if (currNamespace != null) {
    			name += currNamespace + "::";
    		}
    		name += variable.getVarName();
    		
    		infoInsert(variable, Obj.Var, name, new Struct(Struct.Array, requiredType.getType()));
    	}
    }
    
    
    public void visit(ConstVar variable) {
    	if (currClassObj == MJTab.noObj && currMethod == MJTab.noObj) {
    		String name = "";
    		if (currNamespace != null) {
    			name += currNamespace + "::";
    		}
    		name += variable.getConstName();
    		
    		if (variable.getConstValue().struct != requiredType.getType()) {
    			report_error("Wrong type.", variable);
    			return;
    		}
    		
    		Obj constObj = infoInsert(variable, Obj.Con, name, requiredType.getType());

    		if (variable.getConstValue() instanceof ConstBool) {
    			constObj.setAdr(((ConstBool) variable.getConstValue()).getConstBool() ? 1 : 0);
    		} else if (variable.getConstValue() instanceof ConstChar) {
    			constObj.setAdr(((ConstChar) variable.getConstValue()).getConstChar());
    		} else if (variable.getConstValue() instanceof ConstNumber) {
    			constObj.setAdr(((ConstNumber) variable.getConstValue()).getConstNumber());
    		}
    	}
    }
    
    // =============
  	// METHODDS
  	// =============
    
    public void visit(MethodName method) {
    	report_info("Found method: " + method.getMethodName(), method);
    	
    	Obj overrided = MJTab.findInCurrentScope(method.getMethodName());
    	
    	if (currClassObj != MJTab.noObj && overrided != MJTab.noObj) {
    		// This is overriding
    		
    		MJTab.currentScope().getLocals().deleteKey(method.getMethodName());
    		currMethod = infoInsert(method, Obj.Meth, method.getMethodName(), method.getMethodType().struct);
    		
    	} else {
    		// This is not overriding
    		// But it can still be class meth so no namespace added to name
    		String name = "";
    		if (currClassObj == MJTab.noObj) {
	    		if (currNamespace != null) {
	    			name += currNamespace + "::";
	    		}
    		}
    		name += method.getMethodName();
    		currMethod = infoInsert(method, Obj.Meth, name, method.getMethodType().struct);
    	}
    	
    	method.obj = currMethod;
    	
    	MJTab.openScope();
    	
    	methodParNum = 0;
    	methodParAddr = 0;
    	
    	// If it is method in class add this as argument
    	if (currClassObj != MJTab.noObj) {
    		Obj th = MJTab.insert(Obj.Var, "this", currClassObj.getType());
    		th.setLevel(2);
    		methodParAddr++;
    		methodParNum++;
    	}
    }
    
    public void visit(MethodVoid methVoid) {
    	methVoid.struct = MJTab.noType;
    }
    
    public void visit(MethodTypeNoVoid methType) {
    	methType.struct = requiredType.getType();
    }
    
    public void visit(MethodDecl method) {
    	MJTab.chainLocalSymbols(currMethod);
    	currMethod = MJTab.noObj;
    	MJTab.closeScope();
    	report_info("Finished method: " + method.getMethodName().getMethodName(), method);
    }
    
    public void visit(MethodFormParametars methodParams) {
    	currMethod.setLevel(methodParNum);
    	methodParNum = 0;
    }
    
    public void visit(NoMethodFormPars methodParams) {
    	currMethod.setLevel(methodParNum);
    	methodParNum = 0;
    }
    
    public void visit(MatchedReturnExpr matchedReturn) {
    	returnFound = true;
    	Struct declMethType = currMethod.getType();
    	
    	if (declMethType == MJTab.noType) {
    		report_error("Function " + currMethod.getName() + " must return value", matchedReturn);
    		return;
    	}
    	
    	if (!declMethType.equals(matchedReturn.getExpr().struct)) {
    		report_error("Type from expression in return is not compatible with return type of function " + currMethod.getName(), matchedReturn);
    		return;
    	}
    }
    
	// =============
	// CONST VALUE
	// =============
	  
	public void visit(ConstNumber constNumber) {
		constNumber.struct = MJTab.intType;
	}
	  
	public void visit(ConstChar constChar) {
	  	constChar.struct = MJTab.charType;
	}
	  
	public void visit(ConstBool constBool) {
		constBool.struct = MJTab.boolType;
	}
	
	// =============
	// DESIGNATOR
	// =============
	/*
	 * This visit is for calling fields from some class
	 */
	public void visit(DesignatorField designator) {
		String fieldName = designator.getCallerName();
		
		if (designator.getDesignator().obj == null) {
			report_error("Access faild for " + fieldName, designator);
			return;
		}
		
		if (currClassObj.getType() == designator.getDesignator().obj.getType()) {
			// If we are trying to access member of class inside class method
			Obj currClassField = MJTab.currentScope().getOuter().findSymbol(fieldName);
			
			if (currClassField != null) {
				report_info("Accessing field in this class, this." + currClassField.getName(), designator);
				designator.obj = currClassField;
				return;
			} else {
				report_error(fieldName + " does not exist in this class.", designator);
				designator.obj = MJTab.noObj;
				return;
			}
		} else {
			// Maybe it is static field inside class
			fieldName = designator.getDesignator().obj.getName() + "::" + designator.getCallerName();
			Obj staticField = MJTab.find(fieldName);
			if (staticField != MJTab.noObj) {
				designator.obj = staticField;
				report_info("Accessing static field " + fieldName, designator);
				return;
			}

			
			// Else if we are trying to access field using reference on the created class obj
			fieldName = designator.getCallerName();
			for (Obj classMember : designator.getDesignator().obj.getType().getMembers()) {
				if (classMember.getName().equals(fieldName)) {
					report_info("Access to field " + designator.getDesignator().obj.getName() + "." + fieldName, designator );
					designator.obj = classMember;
					return;
				}
			}
		}
	}
	
	/*
	 * Accesing element of array at index
	 */
	public void visit(DesignatorArray arrayDesignator) {
		if (arrayDesignator.getArrayName().getDesignator().obj.getType().getKind() != Struct.Array) {
			report_error("Variable " + arrayDesignator.getArrayName().getDesignator().obj.getName() + " is not array", arrayDesignator);
			arrayDesignator.obj = MJTab.noObj;
			return;
		}
		
		report_info("Access to element of array " + designatorVar.getName(), arrayDesignator);
		arrayDesignator.obj = new Obj(Obj.Elem, arrayDesignator.getArrayName().getDesignator().obj.getName(), arrayDesignator.getArrayName().getDesignator().obj.getType().getElemType());
	}
	
	public void visit(DesignatorNameOut designator) {
		String name = designator.getSpaceName() + "::" + designator.getVarName();
		
		Obj designatorObj = MJTab.find(name);
		
		if (designatorObj != MJTab.noObj) {
			if (staticProgressing == true && designatorObj.getKind() == Obj.Var) {
				report_error("Only static variables may be used inside static initializer", designator);
				designator.obj = MJTab.noObj;
				designatorVar = MJTab.noObj;
				return;
			}
			
			designator.obj = designatorObj;
			designatorVar = designatorObj;
			return;
		}
		
		// If it's static variable
		if (currClassObj != MJTab.noObj) {
			name = designator.getSpaceName() + "::" + currClassObj.getName() + "::" + designator.getVarName();
			
			designatorObj = MJTab.find(name);
			
			if (designatorObj != MJTab.noObj) {
				designator.obj = designatorObj;
				designatorVar = designatorObj;
				return;
			}
		}
		
		report_error("Couldn't find definition of " + name, designator);
	}
	
	public void visit(DesignatorNameInside designator) {
		String name = "";
		
		name += designator.getVarName();
		
		Obj designatorObj = MJTab.find(name);
		
		if (designatorObj != MJTab.noObj) {
			if (staticProgressing == true && designatorObj.getKind() == Obj.Var) {
				report_error("Only static variables may be used inside static initializer", designator);
				designator.obj = MJTab.noObj;
				designatorVar = MJTab.noObj;
				return;
			}
			
			designator.obj = designatorObj;
			designatorVar = designatorObj;
			return;
		}
		// Try once more with namespace if it is inside currNamespace or static fields used inside class
		
		if (currNamespace != null) {
			name = "";
			name += currNamespace + "::" + designator.getVarName();
			
			designatorObj = MJTab.find(name);
			
			if (designatorObj != MJTab.noObj) {
				if (staticProgressing == true && designatorObj.getKind() == Obj.Var) {
					report_error("Only static variables may be used inside static initializer", designator);
					designator.obj = MJTab.noObj;
					designatorVar = MJTab.noObj;
					return;
				}
				
				designator.obj = designatorObj;
				designatorVar = designatorObj;
				return;
			}
		}
		
		// if it's static variable
		if (currClassObj != MJTab.noObj) {
			name = "";
			name += currClassObj.getName() + "::" + designator.getVarName();
			
			designatorObj = MJTab.find(name);
			
			if (designatorObj != MJTab.noObj) {
				designator.obj = designatorObj;
				designatorVar = designatorObj;
				return;
			} 
		}
		
		report_error("Couldn't find definition of " + name, designator);
	}
	
	// =============
	// DESIGNATOR STATEMENT
	// =============
	
	public void visit(DesignatorStatementExprsAssign designator) {
		Obj dst = designator.getDesignator().obj;
		Struct src = designator.getExpr().struct;
		
		if (!checkDstRValueConstraint(dst, designator, 0))  {
			return;
		}
		
		if (!MJTab.assignableTo(src, dst.getType())) {
			report_error("Left " + structDescription(src) + " side is not compatible with right side " + structDescription(dst.getType()), designator);
		}
	}
	
	public void visit(DesignatorStatementExprsInc designator) {
		if (!checkDstRValueConstraint(designator.getDesignator().obj, designator, 1)) {
			return;
		}
		
		if (designator.getDesignator().obj.getType() != MJTab.intType) {
			report_error("Increment must be done only on type of int", designator);
		}
	}
	
	public void visit(DesignatorStatementExprsDec designator) {
		if (!checkDstRValueConstraint(designator.getDesignator().obj, designator, 1)) {
			return;
		}
		
		if (designator.getDesignator().obj.getType() != MJTab.intType) {
			report_error("Decrement must be done only on type of int", designator);
		}
	}
	
	public void visit(DesignatorStatementExprsActPars designator) {
		checkArgumentsMapping(designator.getFuncCallName().obj, designator);
	}
	
	public void visit(OneDesignators designator) {
		arrayAssignmentDesignators.add(designator.getDesignator().obj);
	}
	
	public void visit(DesignatorStatementArrayAssignment designator) {
		Obj rightDesignator = designator.getDesignator1().obj;
		Obj leftArrayDesignator = designator.getDesignator().obj;
		
		// reightDesignator must be Array
		if (rightDesignator.getType().getKind() != Struct.Array) {
			report_error("Right side of array assignment must be an array", designator);
			return;
		}

		// All elements i arrayAssignmentDesignators list must be var, fld or elements of array
		for (Obj element : arrayAssignmentDesignators) {
			if (!checkDstRValueConstraint(element, designator, 0)) {
				return;
			}
		}
		
		// leftArrayDesigntaor must be array
		if (leftArrayDesignator.getType().getKind() != Struct.Array) {
			report_error("Designator after * must be an array", designator);
			return;
		}
		
		// elements of designator array from right side must be compatible with arrayAssignmentDesignators elements of list
		for (Obj element : arrayAssignmentDesignators) {
			if (!element.getType().compatibleWith(rightDesignator.getType().getElemType())) {
				report_error("In array assignment left element type " + structDescription(element.getType()) + " is not compatible with right side type " + structDescription(rightDesignator.getType().getElemType()), designator);
				return;
			}
		}
		
		// leftArrayDesignators elements must be compatible with rightDesignator elements
		
		if (!leftArrayDesignator.getType().getElemType().compatibleWith(rightDesignator.getType().getElemType())) {
			report_error("Array elements from left side of assignment with type " + structDescription(leftArrayDesignator.getType().getElemType()) + " are not compatible with right side array elements " + structDescription(rightDesignator.getType().getElemType()), designator);
			return;
		}
	}
	
	// =============
 	// ACT PARS
 	// =============
	
	public void visit(OneActPar parameter) {
		// Before adding first parameter to list create new List on stack
		stackOfActualParameters.push(new ArrayList<Struct>());
		
		stackOfActualParameters.peek().add(parameter.getExpr().struct);
	}
	
	public void visit(ActParams parameter) {
		stackOfActualParameters.peek().add(parameter.getExpr().struct);
	}
	
	// =============
 	// FACTOR
 	// =============
	
	public void visit(FuncCallName funcCall) {
		funcCall.obj = funcCall.getDesignator().obj;
	}
	
	public void visit(FactorDesignator factor) {
		factor.struct = factor.getDesignator().obj.getType();
	}
	
	public void visit(FactorFunctionCallNoPar factor) {
		if (factor.getFuncCallName().obj.getKind() != Obj.Meth) {
			report_error(factor.getFuncCallName().obj.getName() + " is not a method", factor);
		}
		
		report_info("Calling function " + factor.getFuncCallName().obj.getName(), factor);
		factor.struct = factor.getFuncCallName().obj.getType();
	}
	
	public void visit(FactorFunctionCallWithPar factor) {
		if (factor.getFuncCallName().obj.getKind() != Obj.Meth) {
			report_error(factor.getFuncCallName().obj.getName() + " is not a method", factor);
		}
		
		factor.struct = checkArgumentsMapping(factor.getFuncCallName().obj, factor);
	}
	
	public void visit(FactorConst factor) {
		factor.struct = factor.getConstValue().struct;
	}
	
	public void visit(FactorNewObject factor) {
		if (factor.getType().obj.getType().getKind() != Struct.Class) {
			report_error("Object " + factor.getType().obj.getName() + " must be user defined", factor);
			return;
		}
		factor.struct = factor.getType().obj.getType();
	}
	
	public void visit(FactorNewObjectActPars factor) {
		// First we should find constructor and then process it with checkArgumentsMapping since it uses ActPars
		
		Obj constructor = MJTab.noObj;
		
		String constructorName = factor.getType().obj.getName();
		
		if (constructorName.contains("::")) {
			String[] parts = constructorName.split("::");
			constructorName = parts[1];
		}
		
		for (Obj classMember : factor.getType().obj.getType().getMembers()) {
    		if (classMember.getKind() == Obj.Meth && classMember.getName() == constructorName) {
    			factor.struct = checkArgumentsMapping(classMember, factor);
    			factor.struct = factor.getType().obj.getType();
    			return;
    		}
    	}
		
		report_error("Couldn't find constructor " + constructorName + " for class " + factor.getType().obj.getName(), factor);
		
	}
	
	public void visit(FactorNewArray factor) {
		if (factor.getExpr().struct != MJTab.intType) {
			factor.struct = MJTab.noType;
			
			report_error("Number of elements for creating an array must be of type int", factor);
			return;
		}
		
		factor.struct = new Struct(Struct.Array, factor.getType().obj.getType());
	}
	
	public void visit(FactorExprInside factor) {
		factor.struct = factor.getExpr().struct;
	}
	
	// =============
 	// EXPR AND TERM
 	// =============

	public void visit(MullOneFactor mullFactors) {
		mullFactors.struct = mullFactors.getFactor().struct;
	}
	
	public void visit(MullFactorsWith mullFactors) {
		if(mullFactors.getFactor().struct != MJTab.intType || mullFactors.getMullFactors().struct != MJTab.intType) {
			report_error("Type of factors must be int", mullFactors);
			mullFactors.struct = MJTab.noType;
			return;
		}
		
		mullFactors.struct = MJTab.intType;
	}
	
	public void visit(Term term) {
		term.struct = term.getMullFactors().struct;
	}
	
	public void visit(NegativeTerm expr) {
		if (expr.getTerm().struct != MJTab.intType) {
			report_error("Type negative expr must be int", expr);
			expr.struct = MJTab.noType;
			return;
		}
		
		expr.struct = MJTab.intType;
	}
	
	public void visit(TermWithAdds expr) {
		if (!expr.getTerm().struct.compatibleWith(expr.getExpr().struct)) {
			report_error("Tip of all adders must be int", expr);
			expr.struct = MJTab.noType;
			return;
		}
		if (expr.getTerm().struct != MJTab.intType || expr.getExpr().struct != MJTab.intType) {
			report_error("Type of all adders must be int", expr);
			expr.struct = MJTab.noType;
			return;
		}
		
		expr.struct = MJTab.intType;
	}
	
	public void visit(SingleTerm expr) {
		expr.struct = expr.getTerm().struct;
	}

	// =============
 	// CONDITIONS
 	// =============
	
	public void visit(Eq op) {
		relationalOperation = RelOpEnum.EQUAL;
	}
	
	public void visit(Neq op) {
		relationalOperation = RelOpEnum.NOT_EQUAL;
	}
	
	public void visit(Lt op) {
		relationalOperation = RelOpEnum.LESS;
	}
	
	public void visit(Leq op) {
		relationalOperation = RelOpEnum.LESS_EQUAL;
	}
	
	public void visit(Gt op) {
		relationalOperation = RelOpEnum.GREATER;
	}
	
	public void visit(Geq op) {
		relationalOperation = RelOpEnum.GREATER_EQUAL;
	}
	
	public void visit(CondFactExpr cond) {
		if (cond.getExpr().struct != MJTab.boolType) {
			report_error("Type of condition must be bool", cond);
			return;
		}
	}
	
	public void visit(CondFactWithRelop cond) {
		if (!cond.getExpr().struct.compatibleWith(cond.getExpr1().struct)) {
			report_error("Expressions in condition are not compatible", cond);
		}
		
		if (cond.getExpr().struct.getKind() == Struct.Array
				|| cond.getExpr1().struct.getKind() == Struct.Array
				|| cond.getExpr().struct.getKind() == Struct.Class
				|| cond.getExpr1().struct.getKind() == Struct.Class) {
			
			if (!relationalOperation.equals(RelOpEnum.EQUAL) && !relationalOperation.equals(RelOpEnum.NOT_EQUAL)) {
				report_error("Comparison between reference can only be with == and != operations", cond);
			}
		}
	}
	
	// =============
 	// FOR LOOP
 	// =============

	public void visit(ForStart forStart) {
		++forDepth;
	}
	
	public void visit(ForEnd forEnd) {
		--forDepth;
	}
	
	public void visit(MatchedBreak mBreak) {
		if (forDepth == 0) {
			report_error("Keyword break can only be used inside for loop", mBreak);
		}
	}
	
	public void visit(MatchedContinue mContinue) {
		if (forDepth == 0) {
			report_error("Keyword continue can only be used inside for loop", mContinue);
		}
	}
	
	// =============
 	// READ | PRINT | ORD
 	// =============
	
	public void visit(MatchedRead read) {
		if (!checkDstRValueConstraint(read.getDesignator().obj, read, 3)) {
			return;
		}
		
		if (read.getDesignator().obj.getType() != MJTab.intType &&
				read.getDesignator().obj.getType() != MJTab.charType &&
				read.getDesignator().obj.getType() != MJTab.boolType) {
			report_error("Standard output can only accpet int, char and bool types", read);
		}
	}
	
	public void visit(MatchedPrintNoWidth print) {
		if (print.getExpr().struct != MJTab.intType && print.getExpr().struct != MJTab.charType && print.getExpr().struct != MJTab.boolType) {
			report_error("Function print only accepts int, bool and char types", print);
		}
	}
	
	public void visit(MatchedPrintWithWidth print) {
		if (print.getExpr().struct != MJTab.intType && print.getExpr().struct != MJTab.charType && print.getExpr().struct != MJTab.boolType) {
			report_error("Function print only accepts int, bool and char types", print);
		}
	}
	
    // =============
 	// HELPERS
 	// =============
	public String structDescription(Struct s) {
		switch (s.getKind()) {
			case Struct.None: return "none";
			case Struct.Int: return "int";
			case Struct.Char: return "char";
			case Struct.Array: return "array";
			case Struct.Class: return "class";
			case Struct.Bool: return "bool";
			case Struct.Enum: return "enum";
			case Struct.Interface: return "interface";
			default: return "";
		}
	
	}
    
    private Obj infoInsert(SyntaxNode node, int kind, String name, Struct type) {
		if (errorAlreadyExists(node, name)) return MJTab.noObj;
		
		Obj obj = MJTab.insert(kind, name, type);
		obj.setLevel(0);
		
		report_info("Inserted " + obj.getKind() + " '" + name + "'", node);
		
		return obj;
	}
    
    private boolean errorAlreadyExists(SyntaxNode node, String name) {
    	Obj obj = MJTab.findInCurrentScope(name);
    	
    	if (obj != MJTab.noObj) {
    		report_error("Symbol '" + name + "' is alredy decleared", node);
    		return true;
    	}
    	
    	return false;
    }
    
    // ===============
    // Static Init helpers
    /*
     * Notice that
     * inNamespace == 0 - static variable not in namespace
     * inNamespace == 1 - static variable inside namepsace
     * Not used currently
     */
    private boolean checkFldUsageInStaticInit(Obj check, int inNamespace ,SyntaxNode info) {
		if (!check.getName().contains("::")) {
			report_error(check.getName() + " must be static", info);
			return false;
		}
		
		String temp[] = check.getName().split("::");
		
		if (!temp[inNamespace].equals(currClassObj.getName())) {
			report_error(check.getName() + " must be static inside current class", info);
			return false;
		}
    	
    	return true;
    }
    
    // ===============
    // Methods helpers
    private boolean methodRedefinitionCheck(String methodName, SyntaxNode info) {
    	Obj method = MJTab.findInCurrentScope(methodName);
    	
    	if (method == MJTab.noObj) {
    		return false;
    	}
    	
    	if (currClassObj == MJTab.noObj) {
    		report_error("Method '" + methodName + "' is already decleared!", info);
    		return true;
    	}
    	
    	return false;
    } 
    
    // ===============
    // Function call helpers
    private Struct checkArgumentsMapping(Obj func, SyntaxNode info) {
    	List<Struct> actualArgs = stackOfActualParameters.pop();
    	int numOfArgs = actualArgs.size();
    	
    	// We should check args size comp and get list of actual params
    	List<Obj> formalArgs = checkArgsNumEQ(func, numOfArgs, info);
    	
    	if (formalArgs == null) {
    		return MJTab.noType;
    	}
    	
    	// Now we should check types for each argument
   
    	for (int i = 0; i < numOfArgs; ++i) {
    		if (!MJTab.assignableTo(actualArgs.get(i), formalArgs.get(i).getType())) {
    			report_error("Type problem in formal args", info);
    			return MJTab.noType;
    		}
    	}
    	
    	if (classMethod) {
    		report_info("Calling class method " + func.getName(), info);
    	} else {
    		report_info("Calling function " + func.getName(), info);
    	}
    	
    	classMethod = false;
    	
    	return func.getType();
    }
    
    private List<Obj> checkArgsNumEQ(Obj func, int numOfArgs, SyntaxNode info) {
    	List<Obj> formalArgs = new ArrayList<Obj>();
    	
    	int currIndex = 0;
    	
    	for (Iterator<Obj> formalArgIt = func.getLocalSymbols().iterator(); currIndex < func.getLevel() && formalArgIt.hasNext();) {
    		Obj formParam = formalArgIt.next();
    		formalArgs.add(formParam);
    		++currIndex;
    	}
    	
    	int numOfFormalArgs = func.getLevel();
    	
    	if (formalArgs.size() > 0 && "this".equals(formalArgs.get(0).getName())) {
    		classMethod = true;
    	}
    	
    	if (numOfArgs == numOfFormalArgs) {
    		return formalArgs;
    	}
    	
    	if (numOfArgs == numOfFormalArgs - 1 && "this".equals(formalArgs.get(0).getName())) {
    		formalArgs.remove(0);
    		return formalArgs;
    	}
    	
    	report_error("Number of formal args is not valid!", info);
    	
    	return null;
    }
    
    // ===============
    // Designator constraint
    
    /*
     * RValue has to be variable, lcass field or element of the array
     */
    private boolean checkDstRValueConstraint(Obj dst, SyntaxNode info, int errorFlag) {
    	if (dst.getKind() != Obj.Var && dst.getKind() != Obj.Fld && dst.getKind() != Obj.Elem) {
    		if (errorFlag == 0) {
    			report_error("Left side of assign operation must be variable, field or element of array", info);
    		} else if (errorFlag == 1) {
    			report_error("Increment is only available for variable, field or element of array", info);
    		} else if (errorFlag == 2) {
    			report_error("Decrement is only available for variable, field or element of array", info);
    		} else {
    			report_error("Loading from standard input must be inside variable, field or element of array", info);
    		}
    		
    		return false;
    	}
    	
    	return true;
    }
}
