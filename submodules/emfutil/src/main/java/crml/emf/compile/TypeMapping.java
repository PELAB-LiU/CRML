package crml.emf.compile;

import java.util.HashMap;

public class TypeMapping extends HashMap<Class<?>, String>{
    public TypeMapping(){
        put(String.class, "String");
        put(Integer.class, "Integer");
        put(Double.class, "Real");
    }
}
