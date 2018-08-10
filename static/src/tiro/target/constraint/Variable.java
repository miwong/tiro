package tiro.target.constraint;

import tiro.Output;

import soot.*;

import java.util.HashSet;
import java.util.Set;

public abstract class Variable {
    private static final Set<Type> _stringTypes = new HashSet<Type>();

    static {
        _stringTypes.add(RefType.v("java.lang.String"));
        _stringTypes.add(RefType.v("java.lang.StringBuffer"));
        _stringTypes.add(RefType.v("java.lang.StringBuilder"));
        _stringTypes.add(RefType.v("java.lang.CharSequence"));
    }

    private final Type _type;

    protected Variable(Type type) {
        _type = type;
    }

    public Type getType() {
        return _type;
    }

    @Override public abstract String toString();
    //@Override public abstract Expression clone();
    @Override public abstract boolean equals(Object obj);
    @Override public abstract int hashCode();

    public abstract boolean isConstant();
    public abstract boolean isSymbolic();
    public abstract boolean dependsOnInput();
    public abstract boolean dependsOnInput(int inputNumber);
    public abstract Set<Variable> getAllVariables(Set<Variable> set);

    public static boolean convertibleToStringType(Type type) {
        // For null checks.
        if (type instanceof NullType) {
            return true;
        }
        if (type instanceof IntType || type instanceof LongType
                || type instanceof ShortType) {
            return true;
        }
        // For casting.
        if (type instanceof RefType) {
            return true;
        }

        return false;
    }

    public static boolean isFloatingPointType(Type type) {
        if (type instanceof FloatType || type instanceof DoubleType) {
            return true;
        }

        return false;
    }

    public static boolean isStringType(Type type) {
        return _stringTypes.contains(type);
    }
}
