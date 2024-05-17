package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.*;

public class MJTab extends Tab {
	public static final Struct boolType = new Struct(Struct.Bool);
	public static final Struct classType = new Struct(Struct.Class);
	
	public static void init() {
		if (currentScope != null) return;
		
		rs.etf.pp1.symboltable.Tab.init();
		currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));
		currentScope.addToLocals(new Obj(Obj.Type, "class", classType));
	}
	
	
	public static Obj findInCurrentScope(String name) {
		Obj resultObj = null;
		
		if (currentScope.getLocals() != null) {
			resultObj = currentScope.getLocals().searchKey(name);
		}
		
		return (resultObj != null) ? resultObj : noObj;
	}
	
	public static boolean assignableTo(Struct src, Struct dst) {
		// check if they are assignable without classes extensions:
		if(src.assignableTo(dst)) {
			return true;
		}
		// we cannot declare those two struct nodes as non-assignable until we check its class extensions tree
		if(src.getKind() == Struct.Class && dst.getKind() == Struct.Class) {
			// both of them are classes
			Struct curr = src;
            while (curr != null) {

            	if (curr.equals(dst)) {
            		// one of the super classes is equal to destination
                    return true;
                }

                curr = curr.getElemType();
            }
		}
		
		return false;
	}
}
