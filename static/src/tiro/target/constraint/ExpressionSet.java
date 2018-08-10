package tiro.target.constraint;

import tiro.Output;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

class ExpressionSet {
    public static interface ExpressionTransformer {
        public Expression apply(Expression expr);
    }

    private final Set<Expression> _expressions = new LinkedHashSet<Expression>();

    public ExpressionSet() {
    }

    public ExpressionSet(Expression expr) {
        //_expressions.add(expr);
        this.add(expr);
    }

    public ExpressionSet(ExpressionSet exprSet) {
        this.addAll(exprSet);
    }

    public void add(Expression expr) {
        if (expr == null) {
            Output.error("Adding null expr to ExpressionSet");
            (new Exception()).printStackTrace();
        }

        if (!_expressions.contains(expr)) {
            _expressions.add(expr);
        }
    }

    public void addAll(ExpressionSet exprSet) {
        _expressions.addAll(exprSet.getExpressions());
    }

    public boolean contains(Expression expr) {
        return _expressions.contains(expr);
    }

    public Set<Variable> getAllVariables() {
        Set<Variable> result = new HashSet<Variable>();
        _expressions.forEach(e -> { e.getAllVariables(result); });
        return result;
    }

    public boolean isEmpty() {
        return _expressions.isEmpty();
    }

    public boolean isHeapReference() {
        if (_expressions.size() == 1
                && _expressions.iterator().next().isHeapReference()) {
            return true;
        }

        return false;
    }

    public Expression getFirstExpression() {
        return _expressions.iterator().next();
    }

    public final Collection<Expression> getExpressions() {
        return _expressions;
    }

    public Predicate toPredicate() {
        Predicate pred = null;

        for (Expression expr : _expressions) {
            pred = Predicate.combine(Predicate.Operator.OR,
                                     pred, new ExpressionPredicate(expr));
        }

        return pred;
    }

    public Predicate toNotPredicate() {
        Predicate pred = null;

        for (Expression expr : _expressions) {
            pred = Predicate.combine(Predicate.Operator.OR,
                    pred, new UnaryPredicate(Predicate.Operator.NOT,
                                             new ExpressionPredicate(expr)));
        }

        return pred;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof ExpressionSet)) {
            return false;
        }

        ExpressionSet other = (ExpressionSet)obj;
        return this.getExpressions().equals(other.getExpressions());
    }

    public int hashCode() {
        return _expressions.hashCode();
    }

    public String toString() {
        return StringUtils.join(_expressions, " || ");
    }

    public static ExpressionSet transform(ExpressionSet exprSet,
            ExpressionTransformer transformer) {
        if (exprSet == null) {
            return null;
        }

        ExpressionSet result = new ExpressionSet();
        exprSet.getExpressions().forEach(e -> { result.add(transformer.apply(e)); });
        return result;
    }

    public static ExpressionSet merge(List<ExpressionSet> exprSets) {
        ExpressionSet result = new ExpressionSet();
        exprSets.forEach(e -> { result.addAll(e); });
        return result;

        //List<Expression> exprList = new ArrayList<Expression>();
        //exprSets.forEach(e -> { exprList.addAll(e.getExpressions()); });
        //return ExpressionSet.reduce(exprList);
    }

    public static ExpressionSet combine(Expression.Operator operator,
            ExpressionSet leftExprSet, Expression rightExpr) {
        if (leftExprSet == null && rightExpr == null) {
            return null;
        } else if (leftExprSet == null) {
            return new ExpressionSet(rightExpr);
        } else if (rightExpr == null) {
            return leftExprSet;
        }

        ExpressionSet result = new ExpressionSet();

        for (Expression leftExpr : leftExprSet.getExpressions()) {
            result.add(Expression.combine(operator, leftExpr, rightExpr));
        }

        return result;
    }

    public static ExpressionSet combine(Expression.Operator operator,
            Expression leftExpr, ExpressionSet rightExprSet) {
        if (leftExpr == null && rightExprSet == null) {
            return null;
        } else if (leftExpr == null) {
            return rightExprSet;
        } else if (rightExprSet == null) {
            return new ExpressionSet(leftExpr);
        }

        ExpressionSet result = new ExpressionSet();

        for (Expression rightExpr : rightExprSet.getExpressions()) {
            result.add(Expression.combine(operator, leftExpr, rightExpr));
        }

        return result;
    }

    public static ExpressionSet combine(Expression.Operator operator,
            ExpressionSet leftExprSet, ExpressionSet rightExprSet) {
        if (leftExprSet == null && rightExprSet == null) {
            return null;
        } else if (leftExprSet == null) {
            return rightExprSet;
        } else if (rightExprSet == null) {
            return leftExprSet;
        }

        ExpressionSet result = new ExpressionSet();

        for (Expression leftExpr : leftExprSet.getExpressions()) {
            for (Expression rightExpr : rightExprSet.getExpressions()) {
                result.add(Expression.combine(operator, leftExpr, rightExpr));
            }
        }

        return result;
    }

    // This function handles convergence issues by checking whether the expressions forming
    // a new ExpressionSet (after an operation has been performed) contains lists the same
    // operation being performed repeatedly.  If so, reduce the list of expressions so that
    // the constraint analysis can converge.
    //private static ExpressionSet reduce(List<Expression> exprs) {
    //    if (exprs.size() <= 2) {
    //        exprs.forEach(e -> { result.add(e); });
    //        return result;
    //    }

    //    // We assume that the expressions sets passed in are already in order.
    //    Map<Expression, List<Expression>> baseExprMap =
    //            new LinkedHashMap<Expression, List<Expression>>();
    //    for (Expression expr : exprs) {
    //        boolean foundBase = false;
    //        for (Expression base : baseExprMap.keySet()) {
    //            List<Expression> baseExprs = baseExprMap.get(base);
    //            Expression lastExpr = baseExprs.get(baseExprs.size() - 1);

    //            //if (expr.contains(lastExpr)) {
    //            if (expr.contains(base)) {
    //                baseExprs.add(expr);
    //                foundBase = true;
    //                break;
    //            }

    //            //if (lastExpr.isExpression()) {
    //            //    Expression lastLeft = lastExpr.getLeft();
    //            //    Expression lastRight = lastExpr.getRight();

    //            //    if (expr.contains(lastLeft) && expr.contains(lastRight)) {
    //            //        baseExprs.add(expr);
    //            //        foundBase = true;
    //            //        break;
    //            //    }
    //            //}
    //        }

    //        if (!foundBase) {
    //            List<Expression> baseExprs  = new ArrayList<Expression>();
    //            baseExprs.add(expr);
    //            baseExprMap.put(expr, baseExprs);
    //        }
    //    }

    //    Output.debug("reduce:");
    //    // Check for in-order lists.
    //    for (Expression base : baseExprMap.keySet()) {
    //        List<Expression> containingExprs = baseExprMap.get(base);

    //        // If there is an in-order list, always ensure that the result expression set
    //        // only contains the first 2 of this list (subsequent merges will add a third to
    //        // this list, allowing us to detect the list again).
    //        result.add(base);
    //        Output.debug("  " + base);

    //        if (containingExprs.size() > 1) {
    //            result.add(containingExprs.get(1));
    //            Output.debug("    " + containingExprs.get(1));
    //        }
    //    }

    //    return result;
    //}
}

