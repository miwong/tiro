package tiro.target.constraint;

import tiro.Output;
import tiro.target.StaticAnalysisTimeoutException;

import soot.*;

import com.google.gson.JsonObject;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.*;

public class Z3ConstraintGenerator {
    private final Predicate _constraint;
    private final String _z3Constraint;
    private final String _z3VariableDeclarations;
    private final Map<SymbolicVariable, String> _variableMap =
            new LinkedHashMap<SymbolicVariable, String>();
    private int _nextVariableNum = 0;
    //private final Map<String, Integer> _stringMap = new LinkedHashMap<String, Integer>();
    //private final Set<String> _stringVariables = new HashSet<String>();
    //int _stringNum = 7000;

    public Z3ConstraintGenerator(Predicate constraint) {
        _constraint = constraint;
        _z3Constraint = generateZ3Constraint(constraint);
        _z3VariableDeclarations = generateZ3VariableDeclarations();
    }

    public String getZ3ConstraintString() {
        return _z3Constraint;
    }

    public JsonObject getZ3VariableMapJson() {
        JsonObject jsonObject = new JsonObject();

        for (SymbolicVariable variable : _variableMap.keySet()) {
            //if (!expr.isReturnVariable() && !expr.isHeapVariable()) {
            if (!variable.isHeapVariable()) {
                jsonObject.addProperty(variable.toString(), _variableMap.get(variable));
            }
        }

        return jsonObject;
    }

    //public JsonObject getStringJsonObject() {
    //    JsonObject jsonObject = new JsonObject();

    //    for (String stringVar : _stringVariables) {
    //        jsonObject.addProperty(stringVar, "");
    //    }

    //    return jsonObject;
    //}

    //public JsonObject getStringMapJsonObject() {
    //    JsonObject jsonObject = new JsonObject();

    //    for (String string : _stringMap.keySet()) {
    //        jsonObject.addProperty(Integer.toString(_stringMap.get(string)), string);
    //    }

    //    return jsonObject;
    //}

    public String getZ3ConstraintCode() {
        StringBuilder code = new StringBuilder();
        code.append(_z3VariableDeclarations);
        code.append("\n");
        //code.append("s = Solver()");
        //code.append("\n\n");
        code.append("s.add(");
        code.append(_z3Constraint);
        code.append(")");
        code.append("\n\n");

        return code.toString();
    }

    private String getZ3Variable(VariableExpression expr) {
        Variable variable = expr.getVariable();

        // If constant, just return the constant value.
        if (variable.isConstant()) {
            if (variable.getType() instanceof BooleanType) {
                return ((BooleanVariable)variable).getValue().booleanValue() ? "1" : "0";
            } else {
                return variable.toString();
            }
        }

        // Not a constant, so obtain from symbolic variable map (or generate if needed).
        return _variableMap.computeIfAbsent((SymbolicVariable)expr.getVariable(),
                k -> getNewZ3VariableName());
    }

    private String getNewZ3VariableName() {
        return "IAAv" + (_nextVariableNum++);
    }

    private String getZ3LogicOperatorString(Predicate.Operator operator) {
        switch (operator) {
            case AND: return "And";
            case OR:  return "Or";
            case NOT: return "Not";
            default:  return "";
        }
    }

    private String getZ3OperatorString(Expression.Operator operator) {
        switch (operator) {
            case ADD: return "+";
            case SUB: return "-";
            case MUL: return "*";
            case DIV: return "/";
            case REM: return "%";
            case GT:  return ">";
            case GE:  return ">=";
            case LT:  return "<";
            case LE:  return "<=";
            case EQ:  return "==";
            case NE:  return "!=";
            case AND: return "&";
            case OR:  return "|";
            case XOR: return "^";
            case SHL: return "<<";
            case SHR: return ">>";
            default:  return "";
        }
    }

    private String getZ3StringOperationName(Expression.Operator operator) {
        switch (operator) {
            case STR_EQ:    return "Contains";
            case STR_NE:    return "Contains";
            case CONTAINS:  return "Contains";
            case INDEX_OF:  return "IndexOf";
            case PREFIX_OF: return "PrefixOf";
            case SUFFIX_OF: return "SuffixOf";
            default:  return "";
        }
    }

    private String generateZ3Constraint(Predicate constraint) {
        // In cases where the constraints are complex (e.g. in a long method with many loops),
        // make sure that we detect timeouts and stop analysis in for the current path.
        if (Thread.interrupted()) {
            throw new StaticAnalysisTimeoutException("ConstraintAnalysis");
        }

        StringBuilder z3Constraint = new StringBuilder();

        if (constraint.isExpression()) {
            z3Constraint.append(generateZ3Expression(
                    ((ExpressionPredicate)constraint).getExpression()));
        } else if (constraint.isUnary()) {
            z3Constraint.append(getZ3LogicOperatorString(constraint.getOperator()));
            z3Constraint.append("(");
            z3Constraint.append(generateZ3Constraint(((UnaryPredicate)constraint).getChild()));
            z3Constraint.append(")");
        } else if (constraint.isBinary()) {
            z3Constraint.append(getZ3LogicOperatorString(constraint.getOperator()));
            z3Constraint.append("(");
            z3Constraint.append(generateZ3Constraint(
                    ((BinaryPredicate)constraint).getLeftChild()));
            z3Constraint.append(", ");
            z3Constraint.append(generateZ3Constraint(
                    ((BinaryPredicate)constraint).getRightChild()));
            z3Constraint.append(")");
        }

        return z3Constraint.toString();
    }

    private String generateZ3Expression(Expression expr) {
        StringBuilder z3Expression = new StringBuilder();

        if (expr.isVariable()) {
            z3Expression.append(getZ3Variable(expr.toVariableExpression()));
        } else if (expr.isStringExpression()) {
            StringExpression stringExpr = expr.toStringExpression();
            if (stringExpr.getOperator().equals(Expression.Operator.STR_NE)) {
                z3Expression.append("Not(Contains(");
                z3Expression.append(generateZ3Expression(stringExpr.getLeft()));
                z3Expression.append(",");
                z3Expression.append(generateZ3Expression(stringExpr.getRight()));
                z3Expression.append("))");
            } else {
                z3Expression.append(getZ3StringOperationName(stringExpr.getOperator()));
                z3Expression.append("(");
                z3Expression.append(generateZ3Expression(stringExpr.getLeft()));
                z3Expression.append(",");
                z3Expression.append(generateZ3Expression(stringExpr.getRight()));
                z3Expression.append(")");
            }
        } else if (expr.isArithmeticExpression()) {
            ArithmeticExpression arithExpr = expr.toArithmeticExpression();
            z3Expression.append("(");
            z3Expression.append(generateZ3Expression(arithExpr.getLeft()));
            z3Expression.append(" ");
            z3Expression.append(getZ3OperatorString(arithExpr.getOperator()));
            z3Expression.append(" ");
            z3Expression.append(generateZ3Expression(arithExpr.getRight()));
            z3Expression.append(")");
        }

        return z3Expression.toString();
    }

    private String generateZ3VariableDeclarations() {
        final StringBuilder declarations = new StringBuilder();

        for (SymbolicVariable variable : _variableMap.keySet()) {
            final String z3Variable = _variableMap.get(variable);
            declarations.append(z3Variable);

            variable.getType().apply(new TypeSwitch() {
                @Override
                public void caseArrayType(ArrayType t) {
                    handleObjectType(t);
                }
                @Override
                public void caseBooleanType(BooleanType t) {
                    declarations.append(" = BitVec(\'");
                    declarations.append(z3Variable);
                    declarations.append("\',1)");
                }
                @Override
                public void caseByteType(ByteType t) {
                    declarations.append(" = BitVec(\'");
                    declarations.append(z3Variable);
                    declarations.append("\',32)");
                }
                @Override
                public void caseCharType(CharType t) {
                    declarations.append(" = BitVec(\'");
                    declarations.append(z3Variable);
                    declarations.append("\',8)");
                }
                @Override
                public void caseDoubleType(DoubleType t) {
                    declarations.append(" = Real(\'");
                    declarations.append(z3Variable);
                    declarations.append("\')");
                }
                @Override
                public void caseFloatType(FloatType t) {
                    declarations.append(" = Real(\'");
                    declarations.append(z3Variable);
                    declarations.append("\')");
                }
                @Override
                public void caseIntType(IntType t) {
                    declarations.append(" = BitVec(\'");
                    declarations.append(z3Variable);
                    declarations.append("\',32)");
                }
                @Override
                public void caseLongType(LongType t) {
                    declarations.append(" = BitVec(\'");
                    declarations.append(z3Variable);
                    declarations.append("\',64)");
                }
                @Override
                public void caseNullType(NullType t) {
                    handleObjectType(t);
                }
                @Override
                public void caseRefType(RefType t) {
                    if (Variable.isStringType(t)) {
                        declarations.append(" = String(\'");
                        declarations.append(z3Variable);
                        declarations.append("\')");
                    } else {
                        handleObjectType(t);
                    }
                }
                @Override
                public void caseShortType(ShortType t) {
                    declarations.append(" = BitVec(\'");
                    declarations.append(z3Variable);
                    declarations.append("\',16)");
                }
                @Override
                public void caseVoidType(VoidType t) {
                    handleObjectType(t);
                }
                @Override
                public void defaultCase(Type t) {
                    Output.error("Unsupported constraint variable type: " + t);
                    handleObjectType(t);
                }

                private void handleObjectType(Type t) {
                    declarations.append(" = BitVec(\'");
                    declarations.append(z3Variable);
                    declarations.append("\',32)");
                }
            });

            declarations.append("    # ");
            declarations.append(variable.toString());
            declarations.append("\n");
        }

        return declarations.toString();
    }
}

