package tiro.target.dependency;

import tiro.target.*;
import tiro.target.constraint.Predicate;
import tiro.target.event.CallPath;
import tiro.target.event.Event;
import tiro.target.event.SupportingEvent;
import tiro.target.traversal.CallGraphTraversal;

import java.util.List;

abstract class DependencyResolver<T extends Dependence> {
    public abstract List<CallGraphTraversal.Plugin> getCallGraphPlugins();

    public abstract void computeEventDependencies(Event event);
    public abstract SupportingEvent resolveDependence(Event event, T dependence);
}
