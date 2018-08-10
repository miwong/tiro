package tiro.target.constraint;

import tiro.Output;

import soot.BooleanType;
import soot.IntType;
import soot.NullType;
import soot.Type;

import java.util.Set;

// Immutable object to hold a boolean expression

public abstract class Expression {
    public enum Operator {
        NONE,
        ADD, SUB, MUL, DIV, REM,
        GT, GE, LT, LE, EQ, NE,
        AND, OR, XOR,
        SHL, SHR,
        CMP,
        STR_EQ, STR_NE, APPEND, CONTAINS, LENGTH, INDEX_OF, PREFIX_OF, SUFFIX_OF
    }

    private final Type _type;

    protected Expression(Type type) {
        _type = type;
    }

    private static final VariableExpression TRUE =
            new VariableExpression(new BooleanVariable(true));
    private static final VariableExpression FALSE =
            new VariableExpression(new BooleanVariable(false));
    private static final VariableExpression NULL =
            new VariableExpression(NullVariable.getInstance());
    private static final VariableExpression EMPTY_STRING =
            new VariableExpression(new StringVariable(""));

    static VariableExpression getTrue() {
        return TRUE;
    }

    static VariableExpression getFalse() {
        return FALSE;
    }

    static VariableExpression getNull() {
        return NULL;
    }

    static VariableExpression getEmptyString() {
        return EMPTY_STRING;
    }

    public boolean isTrue() {
        return this.equals(TRUE);
    }

    public boolean isFalse() {
        return this.equals(FALSE);
    }

    public boolean isNull() {
        return this.equals(NULL);
    }

    public abstract Expression.Operator getOperator();
    public abstract boolean isVariable();
    public abstract boolean isExpression();
    public abstract boolean isArithmeticExpression();
    public abstract boolean isStringExpression();

    public VariableExpression toVariableExpression() { return (VariableExpression)this; }
    public ArithmeticExpression toArithmeticExpression() { return (ArithmeticExpression)this; }
    public StringExpression toStringExpression() { return (StringExpression)this; }

    @Override public abstract String toString();
    @Override public abstract Expression clone();
    @Override public abstract boolean equals(Object obj);
    @Override public abstract int hashCode();

    public abstract boolean contains(Expression other);
    public abstract boolean isEquivalentTo(Expression other);
    public abstract boolean implies(Expression other);
    public abstract boolean isOppositeOf(Expression other);
    public abstract boolean dependsOnInput();
    public abstract Set<Variable> getAllVariables(Set<Variable> set);
    public abstract Set<Variable> searchVariables(Predicate.VariablePredicate predicate,
                                                  Set<Variable> result);

    public Type getType() {
        return _type;
    }

    public Variable getVariable() {
        return this.toVariableExpression().getVariable();
    }

    public boolean isHeapVariable() {
        if (!isVariable()) {
            return false;
        }

        Variable variable = this.toVariableExpression().getVariable();
        if (!variable.isSymbolic()) {
            return false;
        }

        return ((SymbolicVariable)variable).isHeapVariable();
        //return variable.startsWith("Heap<") || variable.startsWith("New<");
    }

    //public boolean isReturnVariable() {
    //    if (!isVariable()) {
    //        return false;
    //    }

    //    String variable = this.toVariableExpression().getVariable();
    //    return variable.contains("<return>");
    //}

    public boolean isSystemVariable() {
        if (!isVariable()) {
            return false;
        }

        Variable variable = this.toVariableExpression().getVariable();
        if (!variable.isSymbolic()) {
            return false;
        }

        return ((SymbolicVariable)variable).isSystemVariable();
        //return variable.contains("<System") || variable.contains("<CurrentDate>");
    }

    public boolean isDependencyVariable() {
        if (!isVariable()) {
            return false;
        }

        String variable = this.toVariableExpression().getVariable().toString();
        return variable.startsWith("Heap<") || variable.startsWith("SharedPreferences<");
    }

    public boolean isHeapReference() {
        if (!isVariable()) {
            return false;
        }

        String variable = this.toVariableExpression().getVariable().toString();
        if (variable.startsWith("Heap<") && variable.endsWith(">")) {
            return true;
        }

        return false;
    }

    public String getHeapIdentifier() {
        String variable = this.toVariableExpression().getVariable().toString();
        return variable.substring(5, variable.length() - 1);
    }

    //public List<VariableExpression> getAllVariables() {
    //    List<VariableExpression> result = new ArrayList<VariableExpression>();
    //    this.getAllVariables(result);
    //    return result;
    //}

    public boolean isSimpleExpression() {
        if (isArithmeticExpression()) {
            ArithmeticExpression arithmeticExpr = this.toArithmeticExpression();
            if (arithmeticExpr.getLeft().isVariable()
                    && arithmeticExpr.getRight().isVariable()) {

                return true;
            }
        }

        return false;
    }

    public boolean isSimpleStringExpression() {
        if (isStringExpression()) {
            StringExpression stringExpr = this.toStringExpression();
            if (stringExpr.getLeft().isVariable() && stringExpr.getRight().isVariable()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------

    public static boolean isBitwiseOperator(Expression.Operator operator) {
        switch (operator) {
            case AND: // fall through
            case OR:  // fall through
            case XOR: // fall through
            case CMP: // fall through
            case SHL: // fall through
            case SHR:
                return true;
            default:
                return false;
        }
    }

    public static boolean isEqualOperator(Expression.Operator operator) {
        switch (operator) {
            case EQ: // fall through
            case GE: // fall through
            case LE:
                return true;
            default:
                return false;
        }
    }

    public static boolean isNotEqualOperator(Expression.Operator operator) {
        switch (operator) {
            case NE: // fall through
            case GT: // fall through
            case LT:
                return true;
            default:
                return false;
        }
    }

    protected static boolean isStringOperator(Expression.Operator operator) {
        switch (operator) {
            case STR_EQ:    // fall through
            case STR_NE:    // fall through
            case APPEND:    // fall through
            case CONTAINS:  // fall through
            case LENGTH:    // fall through
            case INDEX_OF:  // fall through
            case PREFIX_OF: // fall through
            case SUFFIX_OF:
                return true;
            default:
                return false;
        }
    }

    // Check if there is an inverse operator.  If so, return the opposite operator
    public static Expression.Operator getOppositeOperator(Expression.Operator operator) {
        switch (operator) {
            case EQ: return Operator.NE;
            case NE: return Operator.EQ;
            case GT: return Operator.LE;
            case GE: return Operator.LT;
            case LT: return Operator.GE;
            case LE: return Operator.GT;
            default: return Operator.NONE;
        }
    }

    public static Expression combine(Operator operator, Expression left, Expression right) {
        // Handle the CMP operation in a special case
        if (left != null && left.isArithmeticExpression()) {
            ArithmeticExpression arithmeticLeft = left.toArithmeticExpression();
            if (arithmeticLeft.getOperator().equals(Operator.CMP)) {
                ArithmeticExpression oldExpr = arithmeticLeft;
                left = oldExpr.getLeft();
                right = oldExpr.getRight();
            }
        }

        if (left == null && right == null) {
            return null;
        } else if (left == null) {
            return right;
        } else if (right == null) {
            return left;
        }

        // Handle string null checks in a special case.
        if (Variable.isStringType(left.getType()) && !Variable.isStringType(right.getType())
                && Variable.convertibleToStringType(right.getType())) {
            operator = (operator == Expression.Operator.EQ)
                    ? Expression.Operator.STR_EQ : Expression.Operator.STR_NE;
            right = Expression.getEmptyString();

        } else if (!Variable.isStringType(left.getType())
                && Variable.isStringType(right.getType())
                && Variable.convertibleToStringType(left.getType())) {
            operator = (operator == Expression.Operator.EQ)
                    ? Expression.Operator.STR_EQ : Expression.Operator.STR_NE;
            left = Expression.getEmptyString();
        }

        if (isStringOperator(operator)) {
            return new StringExpression(operator, left, right);
        } else {
            // Fix booleans here (Soot combines int and boolean constants into an IntConstant).
            if (left.getType() instanceof BooleanType
                    && right.isVariable() && right.getVariable().isConstant()
                    && right.getType() instanceof IntType) {

                int rightValue = ((NumberVariable)right.getVariable()).getValue().intValue();
                right = rightValue == 1 ? Expression.getTrue() : Expression.getFalse();
            }

            if (right.getType() instanceof BooleanType
                    && left.isVariable() && left.getVariable().isConstant()
                    && left.getType() instanceof IntType) {

                int leftValue = ((NumberVariable)left.getVariable()).getValue().intValue();
                left = leftValue == 1 ? Expression.getTrue() : Expression.getFalse();
            }

            return new ArithmeticExpression(operator, left, right);
        }
    }

    // ------------------------------------------------------------------------

    protected static String getOperatorString(Expression.Operator operator) {
        switch (operator) {
            case ADD: return "+";
            case SUB: return "-";
            case MUL: return "*";
            case DIV: return "/";
            case REM: return "%";
            case GT: return ">";
            case GE: return ">=";
            case LT: return "<";
            case LE: return "<=";
            case EQ: return "==";
            case NE: return "!=";
            case AND: return "&";
            case OR:  return "|";
            case XOR: return "^";
            case CMP: return "cmp";
            case SHL: return "<<";
            case SHR: return ">>";
            case APPEND: return "str.++";
            case STR_EQ: return "=";
            case STR_NE: return "!=";
            case CONTAINS: return "contains";
            case LENGTH: return "len";
            case INDEX_OF: return "indexof";
            case PREFIX_OF: return "prefixof";
            case SUFFIX_OF: return "suffixof";
            default:  return "";
        }
    }
}

