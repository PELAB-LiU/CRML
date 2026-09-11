package crml.compiler.crmlcv2.util;

import crml.model.language.BuiltinType;
import crml.model.language.BuiltinTypeReference;
import crml.model.language.ConstructorValue;
import crml.model.language.IndirectTypeReference;
import crml.model.language.TypeReference;
import crml.model.language.UserTypereference;
import crml.model.language.Value;
import crml.model.language.Variable;
import crml.model.language.VariableReference;

public class TypeResolver {
    public static String resolve(TypeReference type){
        if(type instanceof IndirectTypeReference){
            return resolve(((IndirectTypeReference) type).getReferredType());
        } else if(type instanceof BuiltinTypeReference) {
            BuiltinTypeReference btr = (BuiltinTypeReference) type;
            return resolve(btr.getBuiltinType());
        } else if(type instanceof UserTypereference) {
            UserTypereference utr = (UserTypereference) type;
            if(utr.getDomain() instanceof crml.model.language.Class) {
                crml.model.language.Class clazz =  (crml.model.language.Class) utr.getDomain();
                return clazz.getName();
            }
            throw new RuntimeException("Unimplemented.");
        } else if (type == null) {
            throw new RuntimeException("No type reference to resolve.");
        } else {
            throw new RuntimeException("Unable to resolve type reference: " + type.eClass().getName());
        }
    }

    // Resolves a TypeReference down to its BuiltinType, or null if it isn't
    // one (unresolved / not yet inferred, or a user-defined class type).
    public static BuiltinType resolveBuiltin(TypeReference type){
        if(type == null){
            return null;
        } else if(type instanceof IndirectTypeReference){
            return resolveBuiltin(((IndirectTypeReference) type).getReferredType());
        } else if(type instanceof BuiltinTypeReference){
            return ((BuiltinTypeReference) type).getBuiltinType();
        } else {
            return null;
        }
    }

    /**
     * The builtin type of a value, looking past the object model's own gaps.
     *
     * <p>{@code Value#getReturnType()} is only populated for constants today, so
     * a reference to a variable of a known domain still reports "unknown". Where
     * a decision genuinely depends on the type - not merely on it being
     * compatible - this looks through the reference to the variable's declared
     * domain, and through a constructor to the domain it constructs.
     *
     * <p>Deliberately not used by the operator transformers: their type dispatch
     * is written around "known-or-unknown" predicates and was verified against
     * typeinference.csv in that form. Making inference sharper there is a
     * separate change with its own consequences.
     */
    public static BuiltinType inferBuiltin(Value value){
        if (value == null) {
            return null;
        }
        BuiltinType declared = resolveBuiltin(value.getReturnType());
        if (declared != null) {
            return declared;
        }
        if (value instanceof VariableReference) {
            Variable variable = ((VariableReference) value).getVariable();
            return variable == null ? null : resolveBuiltin(variable.getDomain());
        }
        if (value instanceof ConstructorValue) {
            return resolveBuiltin(((ConstructorValue) value).getDomain());
        }
        return null;
    }

    public static String resolve(BuiltinType type){
        switch (type) {
            case BOOLEAN: return "CRMLtoModelica.Types.Boolean4";
            case PERIOD: return "CRMLtoModelica.Types.CRMLPeriod";
            case PERIODS: return "CRMLtoModelica.Types.CRMLPeriods";
            case EVENT: return "CRMLtoModelica.Types.Event";
            case REQUIREMENT: return "CRMLtoModelica.Types.Boolean4";
            case CLOCK: return "CRMLtoModelica.Types.CRMLClock";
            case REAL: return "Real";
            case STRING: return "String";
            case INTEGER: return "Integer";
            default:
                throw new RuntimeException("Unable to resolve type: "+type);
        }
    }
}
