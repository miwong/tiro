package tiro.target.constraint;

import tiro.Output;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.*;

// TODO: create "changed" flag to determine when to stop minimizing (useful if/when
// iterative algorithm implemented)

class ConstraintMinimization {
    public static Predicate minimize(Predicate constraint) {
        if (constraint == null) {
            return null;
        }

        constraint = minimizePredicate(constraint);
        if (constraint == null) {
            Output.error("Minimized constraint to null");
            return null;
        }

        constraint = removeRedundancies(constraint);
        if (constraint == null) {
            Output.error("Minimized constraint to null");
            return null;
        }

        return minimizePredicate(constraint);
    }

    // Recursive method to minimize a predicate.
    private static Predicate minimizePredicate(Predicate pred) {
        if (pred.isExpression()) {
            ExpressionPredicate exprPred = (ExpressionPredicate)pred;
            Expression minExpr = minimizeExpression(exprPred.getExpression());
            if (!minExpr.equals(exprPred.getExpression())) {
                return new ExpressionPredicate(minExpr);
            } else {
                return exprPred;
            }
        } else if (pred.isUnary()) {
            return minimizeUnaryPredicate((UnaryPredicate)pred);
        } else if (pred.isBinary()) {
            return minimizeBinaryPredicate((BinaryPredicate)pred);
        }

        Output.error("minimizePredicate: ill-formed constraint");
        return pred;
    }

    private static Predicate minimizeUnaryPredicate(UnaryPredicate pred) {
        Predicate minChild = minimizePredicate(pred.getChild());

        if (pred.getOperator().equals(Predicate.Operator.NOT)) {
            if (minChild.isExpression()) {
                Expression leftExpr = ((ExpressionPredicate)minChild).getExpression();

                if (leftExpr.isTrue()) {
                    return Predicate.getFalse();
                } else if (leftExpr.isFalse()) {
                    return Predicate.getTrue();
                } else if (leftExpr.isArithmeticExpression()) {
                    ArithmeticExpression arithLeftExpr = leftExpr.toArithmeticExpression();
                    Expression.Operator oppositeOp =
                            Expression.getOppositeOperator(arithLeftExpr.getOperator());

                    if (!oppositeOp.equals(Expression.Operator.NONE)) {
                        return new ExpressionPredicate(new ArithmeticExpression(oppositeOp,
                                arithLeftExpr.getLeft(), arithLeftExpr.getRight()));
                    }
                }
            } else if (minChild.isUnary()
                        && minChild.getOperator().equals(Predicate.Operator.NOT)) {
                // NOT operators cancel themselves
                return ((UnaryPredicate)minChild).getChild();

            } else if (minChild.isBinary()
                        && minChild.getOperator().equals(Predicate.Operator.AND)) {
                BinaryPredicate minChildBinary = (BinaryPredicate)minChild;
                return new BinaryPredicate(Predicate.Operator.OR,
                        new UnaryPredicate(Predicate.Operator.NOT,
                                minChildBinary.getLeftChild()),
                        new UnaryPredicate(Predicate.Operator.NOT,
                                minChildBinary.getRightChild()));

            } else if (minChild.isBinary()
                        && minChild.getOperator().equals(Predicate.Operator.OR)) {
                BinaryPredicate minChildBinary = (BinaryPredicate)minChild;
                return new BinaryPredicate(Predicate.Operator.AND,
                        new UnaryPredicate(Predicate.Operator.NOT,
                                minChildBinary.getLeftChild()),
                        new UnaryPredicate(Predicate.Operator.NOT,
                                minChildBinary.getRightChild()));
            }
        }

        if (!minChild.equals(pred.getChild())) {
            return new UnaryPredicate(pred.getOperator(), minChild);
        }

        return pred;
    }

    private static Predicate minimizeBinaryPredicate(BinaryPredicate pred) {
        Predicate minLeft = minimizePredicate(pred.getLeftChild());
        Predicate minRight = minimizePredicate(pred.getRightChild());

        if (minLeft.equals(minRight)) {
            return minLeft;
        }

        if (pred.getOperator().equals(Predicate.Operator.AND)) {
            if (minLeft.isFalse() || minRight.isFalse()) {
                return Predicate.getFalse();
            } else if (minLeft.isTrue()) {
                return minRight;
            } else if (minRight.isTrue()) {
                return minLeft;
            }

        } else if (pred.getOperator().equals(Predicate.Operator.OR)) {
            if (minLeft.isTrue() || minRight.isTrue()) {
                return Predicate.getTrue();
            } else if (minLeft.isFalse()) {
                return minRight;
            } else if (minRight.isFalse()) {
                return minLeft;
            }
        }

        if (!minLeft.equals(pred.getLeftChild()) || !minRight.equals(pred.getRightChild())) {
            return new BinaryPredicate(pred.getOperator(), minLeft, minRight);
        }

        return pred;
    }

    private static Expression minimizeExpression(Expression expr) {
        if (expr.isArithmeticExpression()) {
            ArithmeticExpression arithExpr = expr.toArithmeticExpression();
            if (arithExpr.getLeft().equals(arithExpr.getRight())) {
                if (arithExpr.getOperator().equals(Expression.Operator.EQ)) {
                    return Expression.getTrue();
                } else if (Expression.isNotEqualOperator(arithExpr.getOperator())) {
                    return Expression.getFalse();
                }

            } else if (arithExpr.getLeft().isVariable()
                        && arithExpr.getLeft().getVariable().isConstant()
                        && arithExpr.getRight().isVariable()
                        && arithExpr.getRight().getVariable().isConstant()) {

                //if (NumberUtils.createNumber(arithExpr.getLeft().getVariable()).equals(
                //        NumberUtils.createNumber(arithExpr.getRight().getVariable()))) {
                if (arithExpr.getLeft().getVariable().equals(
                        arithExpr.getRight().getVariable())) {
                    if (Expression.isEqualOperator(arithExpr.getOperator())) {
                        return Expression.getTrue();
                    } else if (Expression.isNotEqualOperator(arithExpr.getOperator())) {
                        return Expression.getFalse();
                    }
                } else {
                    // Constraint are different numbers
                    if (arithExpr.getOperator().equals(Expression.Operator.EQ)) {
                        return Expression.getFalse();
                    }
                    //} else if (Expression.isNotEqualOperator(expr.getOperator())) {
                    //    return Expression.getTrue();
                }
            }
        } else if (expr.isStringExpression()) {
            StringExpression stringExpr = expr.toStringExpression();

            if (stringExpr.isSimpleExpression()) {
                Variable left = stringExpr.getLeft().getVariable();
                Variable right = stringExpr.getRight().getVariable();

                if (left.isConstant() && right.isConstant()) {
                    String leftValue = ((StringVariable)left).getValue();
                    String rightValue = ((StringVariable)right).getValue();

                    switch (expr.getOperator()) {
                        case STR_EQ:
                            return leftValue.equals(rightValue)
                                    ? Expression.getTrue() : Expression.getFalse();
                        case STR_NE:
                            return leftValue.equals(rightValue)
                                    ? Expression.getFalse() : Expression.getTrue();
                        case CONTAINS:
                            return leftValue.contains(rightValue)
                                    ? Expression.getTrue() : Expression.getFalse();
                        case PREFIX_OF: // fall through
                        case SUFFIX_OF: // fall through
                        default:
                            break;
                    }
                }
            }
        }

        return expr;
    }

    private static Predicate removeRedundancies(Predicate pred) {
        if (pred.isExpression()) {
            return pred;
        } else if (pred.isUnary()) {
            UnaryPredicate unaryPred = (UnaryPredicate)pred;
            Predicate minLeft = removeRedundancies(unaryPred.getChild());
            if (!minLeft.equals(unaryPred.getChild())) {
                return new UnaryPredicate(unaryPred.getOperator(), minLeft);
            }

            return unaryPred;

        } else if (pred.isBinary()) {
            BinaryPredicate binaryPred = (BinaryPredicate)pred;
            Predicate minLeft = removeRedundancies(binaryPred.getLeftChild());
            Predicate minRight = removeRedundancies(binaryPred.getRightChild());

            if (binaryPred.getOperator().equals(Predicate.Operator.AND)) {
                if (minRight.isExpression() && ((ExpressionPredicate)minRight).getExpression()
                        .isSimpleExpression()) {

                    minLeft = propagateAndConstraint(minLeft,
                            ((ExpressionPredicate)minRight).getExpression());

                } else if (minLeft.isExpression()
                            && ((ExpressionPredicate)minLeft).getExpression()
                                    .isSimpleExpression()) {
                    minRight = propagateAndConstraint(minRight,
                            ((ExpressionPredicate)minLeft).getExpression());

                }

                //if (minLeft.isBinary() && minLeft.getOperator().equals(
                //        Predicate.Operator.AND)) {
                //    if (minLeft.getRight().isExpression() &&
                //        minLeft.getRight().getExpression().isSimpleExpression()) {

                //        minRight = propagateAndConstraint(
                //                minRight, minLeft.getRight().getExpression());
                //    }
                //}
            }

            if (!minLeft.equals(binaryPred.getLeftChild())
                    || !minRight.equals(binaryPred.getRightChild())) {

                return new BinaryPredicate(binaryPred.getOperator(), minLeft, minRight);
            }

            return binaryPred;
        }

        Output.error("removeRedundancies: ill-formed constraint");
        return pred;
    }

    private static Predicate propagateAndConstraint(Predicate pred, Expression andExpr) {
        if (pred.isExpression()) {
            if (((ExpressionPredicate)pred).getExpression().isOppositeOf(andExpr)) {
                return Predicate.getFalse();
            }

            if (andExpr.implies(((ExpressionPredicate)pred).getExpression())) {
                return Predicate.getTrue();
            }

        } else if (pred.isUnary()) {
            UnaryPredicate unaryPred = (UnaryPredicate)pred;
            Predicate minLeft = propagateAndConstraint(unaryPred.getChild(), andExpr);

            if (minLeft.isExpression()
                    && unaryPred.getOperator().equals(Predicate.Operator.NOT)) {
                // isEquivalentTo?  implies?

                if (andExpr.implies(((ExpressionPredicate)minLeft).getExpression())) {
                    return Predicate.getFalse();
                } else if (((ExpressionPredicate)minLeft).getExpression().isOppositeOf(
                                   andExpr)) {
                    return Predicate.getTrue();
                }
            }

            if (!minLeft.equals(unaryPred.getChild())) {
                return new UnaryPredicate(unaryPred.getOperator(), minLeft);
            }

        } else if (pred.isBinary()) {
            BinaryPredicate binaryPred = (BinaryPredicate)pred;
            Predicate minLeft = propagateAndConstraint(binaryPred.getLeftChild(), andExpr);
            Predicate minRight = propagateAndConstraint(binaryPred.getRightChild(), andExpr);

            if (binaryPred.getOperator().equals(Predicate.Operator.AND)) {
                if (minLeft.isFalse() || minRight.isFalse()) {
                    return Predicate.getFalse();
                } else if (minLeft.isTrue()) {
                    return minRight;
                } else if (minRight.isTrue()) {
                    return minLeft;
                }

            } else if (binaryPred.getOperator().equals(Predicate.Operator.OR)) {
                if (minLeft.isTrue() || minRight.isTrue()) {
                    return Predicate.getTrue();
                } else if (minLeft.isFalse()) {
                    return minRight;
                } else if (minRight.isFalse()) {
                    return minLeft;
                }
            }

            if (!minLeft.equals(binaryPred.getLeftChild())
                    || !minRight.equals(binaryPred.getRightChild())) {

                return new BinaryPredicate(binaryPred.getOperator(), minLeft, minRight);
            }
        }

        return pred;
    }
}

