package tiro.target.constraint;

import tiro.Output;

import soot.Type;
import soot.jimple.*;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Set;

abstract class ConstantVariable<T> extends Variable {
    private final T _value;

    protected ConstantVariable(T value, Type type) {
        super(type);
        _value = value;
    }

    @Override public boolean isConstant() { return true; }
    @Override public boolean isSymbolic() { return false; }

    public T getValue() {
        return _value;
    }

    @Override
    public String toString() {
        return _value.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ConstantVariable)) {
            return false;
        }

        ConstantVariable other = (ConstantVariable)obj;
        return this.getType().equals(other.getType()) && _value.equals(other.getValue());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(_value)
                .toHashCode();
    }

    @Override
    public boolean dependsOnInput() {
        return false;
    }

    @Override
    public boolean dependsOnInput(int inputNumber) {
        return false;
    }

    @Override
    public Set<Variable> getAllVariables(Set<Variable> set) {
        return set;
    }

    static ConstantVariable generateFromSootConstant(Constant constant) {
        constant.apply(_constantVariableGenerator);
        return _constantVariableGenerator.getResult();
    }

    private static ConstantVariableSwitch _constantVariableGenerator =
            new ConstantVariableSwitch();

    private static class ConstantVariableSwitch extends AbstractConstantSwitch {
        private ConstantVariable _result;

        public ConstantVariable getResult() { return _result; }

        @Override
        public void caseDoubleConstant(DoubleConstant v) {
            _result = new NumberVariable(v.value);
        }
        @Override
        public void caseFloatConstant(FloatConstant v) {
            _result = new NumberVariable(v.value);
        }
        @Override
        public void caseIntConstant(IntConstant v) {
            // In soot, boolean constants also fall into this case.  Differentiate when we
            // use the constant in an expression.
            _result = new NumberVariable(v.value);
        }
        @Override
        public void caseLongConstant(LongConstant v) {
            _result = new NumberVariable(v.value);
        }
        @Override
        public void caseNullConstant(NullConstant v) {
            // TODO: make this into a special NULL type to distinguish from the integer 0
            _result = NullVariable.getInstance();
        }
        @Override
        public void caseStringConstant(StringConstant v) {
            // StringConstant is surrounded by unnecessary " quotations
            String stringConstant = v.toString();
            stringConstant = stringConstant.substring(1, stringConstant.length() - 1);
            _result = new StringVariable(stringConstant);
        }
        @Override
        public void caseClassConstant(ClassConstant v) {
            _result = new StringVariable(v.toString());
        }
        @Override public void defaultCase(Object object) {
            Output.error("Unsupported constant value (" + object.getClass().getName() + "): "
                    + object);
            _result = null;
        }
    }
}
