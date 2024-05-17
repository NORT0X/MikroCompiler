package rs.ac.bg.etf.pp1.util;

import org.apache.log4j.Logger;
import rs.ac.bg.etf.pp1.MJTab;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.List;

public class Analyzer {
	private boolean errorDetected = false;
	private final Logger logger;
	
	public Analyzer(Class clazz) {
		logger = Logger.getLogger(clazz);
	}
	
	private String formatMessage(String message, SyntaxNode info) {
		return "Line " + info.getLine() + " [" + info.getClass().getSimpleName() + "]: " + message;
	}
	
	private String parenthood(SyntaxNode node) {
		node = node.getParent();
		
		StringBuilder parenthood = new StringBuilder();
		while (node != null) {
			parenthood.append(node.getClass().getSimpleName());
			
			node = node.getParent();
			
			if (node != null) {
				parenthood.append(" -> ");
			}
		}
		
		return parenthood.toString();
	}
	
	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		
		logger.error(formatMessage(message, info));
		// logger.error(parenthood(info));
	}
	
	public void report_info(String message, SyntaxNode info) {
		logger.info(formatMessage(message, info));
		// logger.info(parenthood(info));
	}
	
	public boolean isErrorDetected() {
		return errorDetected;
	}
	
	public boolean errorNotExists(SyntaxNode node, String name) {
		Obj obj = MJTab.find(name);
		
		if (obj == MJTab.noObj) {
			report_error("Symbol '" + name + "' is not declared", node);
			return true;
		}
		
		return false;
	}
	
	public boolean errorAlreadyExists(SyntaxNode node, String name) {
		Obj obj = MJTab.find(name);
		
		if (obj != MJTab.noObj) {
			report_error("Symbol '" + name + "' is already declared as " + objKindToString(obj), node);
			return true;
		}
		
		return false;
	}
	
	public boolean errorObjWrongKind(SyntaxNode node, String name, int kind) {
		Obj obj = MJTab.find(name);
		
		if (obj.getKind() != kind) {
			report_error("Symbol '" + name + "' is not a " + objKindToString(obj), node);
			return true;
		}
		
		return false;
	}
	
	public boolean errorStructWrongKind(SyntaxNode node, Struct struct, Struct kind) {
		if (struct != kind) {
			report_error("Symbol '" + structKindToString(struct) + "' is not a " + structKindToString(kind), node);
			return true;
		}
		
		return false;
	}
	
	public boolean errorNotAssignable(SyntaxNode node, Struct nodeStruct, Struct typeStruct) {
		if (!nodeStruct.assignableTo(typeStruct)) {
			String message = "Cannot assign " + nodeStruct.getKind() + " to " + typeStruct.getKind();
			report_error(message, node);
			return true;
		}
		
		return false;
	}
	
	public boolean errorParameterNumberNotMatch(SyntaxNode node, List<Struct> one, List<Struct> two) {
		if (one.size() != two.size()) {
			report_error("Number of parameters does not match " + one.size() + " != " + two.size(), node);
			return true;
		}
		
		return false;
	}
	
	public boolean errorParameterTypesNotMatch(SyntaxNode node, List<Struct> one, List<Struct> two) {
		for (int i = 0; i < one.size(); i++) {
			if (!one.get(i).equals(two.get(i))) {
				report_error("Parameter types do not match " + structKindToString(one.get(i)) + " != " + structKindToString(two.get(i)), node);
				return true;
			}
		}
		
		return false;
	}
	
	public boolean errorParameterNotMatch(SyntaxNode node, List<Struct> one, List<Struct> two) {
		return errorParameterNumberNotMatch(node, one, two) || errorParameterTypesNotMatch(node, one, two);
	}
	
	public Obj infoInsert(SyntaxNode node, int kind, String name, Struct type) {
		if (errorAlreadyExists(node, name)) return MJTab.noObj;
		
		Obj obj = MJTab.insert(kind, name, type);
		obj.setLevel(0);
		
		report_info("Inserted " + objKindToString(obj) + " '" + name + "'", node);
		
		return obj;
	}
	
	private String objKindToString(Obj obj) {
		switch (obj.getKind()) {
			case Obj.Con:
				return "Constant";
			case Obj.Var:
				return "Variable";
			case Obj.Type:
				return "Type";
			case Obj.Meth:
				return "Method";
			case Obj.Fld:
				return "Field";
			case Obj.Prog:
				return "Program";
			case Obj.Elem:
				return "Element";
			case Obj.NO_VALUE:
				return "NO_VALUE";
			default:
				return "UNKNOWN";
		}
	}
	
	private String structKindToString(Struct struct) {
		switch (struct.getKind()) {
			case Struct.None:
				return "None";
			case Struct.Int:
				return "Int";
			case Struct.Char:
				return "Char";
			case Struct.Array:
				return "Array";
			case Struct.Class:
				return "Class";
			case Struct.Interface:
				return "Interface";
			case Struct.Enum:
				return "Enum";
			default:
				return "UNKNOWN";
		}
	}
}