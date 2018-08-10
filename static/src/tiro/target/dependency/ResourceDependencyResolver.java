package tiro.target.dependency;

import tiro.*;
import tiro.target.*;
import tiro.target.constraint.*;
import tiro.target.event.*;
import tiro.target.traversal.CallGraphTraversal;

import soot.IntType;

import java.util.*;

class ResourceDependencyResolver extends DependencyResolver<KeyValueAccessVariable> {
    private final ResourceAnalysis _resourceAnalysis;

    public ResourceDependencyResolver(ResourceAnalysis resourceAnalysis) {
        _resourceAnalysis = resourceAnalysis;
    }

    @Override
    public List<CallGraphTraversal.Plugin> getCallGraphPlugins() {
        return Collections.<CallGraphTraversal.Plugin>emptyList();
    }

    @Override
    public void computeEventDependencies(Event event) {
        Predicate constraints = event.getConstraints();
        if (constraints != null) {
            Set<Variable> resourceVariables = constraints.searchVariables(v -> {
                if (!(v instanceof KeyValueAccessVariable)) {
                    return false;
                }

                KeyValueAccessVariable keyValue = (KeyValueAccessVariable)v;
                if (!keyValue.getDatabaseType().equals(
                            KeyValueAccessVariable.DatabaseType.STRING_TABLE)) {
                    return false;
                }

                // Only resolve resource dependencies where we know the resource ID.
                return keyValue.getKeyVariable().isConstant()
                        && keyValue.getKeyVariable().getType() instanceof IntType;
            });

            resourceVariables.forEach(v -> event.addDependence((KeyValueAccessVariable)v));
        }
    }

    @Override
    public SupportingEvent resolveDependence(Event event, KeyValueAccessVariable dependence) {
        Predicate constraints = event.getConstraints();
        if (constraints == null) {
            return null;
        }

        int resourceId = ((NumberVariable)dependence.getKeyVariable()).getValue().intValue();
        String resourceValue = _resourceAnalysis.getStringResource(resourceId);
        if (resourceValue != null) {
            Predicate resourceConstraint = new ExpressionPredicate(new StringExpression(
                    Expression.Operator.STR_EQ, new VariableExpression(dependence),
                    new VariableExpression(new StringVariable(resourceValue))));

            if (TIROStaticAnalysis.Config.PrintOutput) {
                Output.printConstraint("Resource dependence: "
                        + resourceConstraint.toString());
            }

            constraints = Predicate.combine(Predicate.Operator.AND,
                    constraints, resourceConstraint);
        }

        event.updateConstraints(constraints);
        return null;
    }
}
