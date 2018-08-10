package tiro.target.dependency;

import tiro.*;
import tiro.target.*;
import tiro.target.constraint.*;
import tiro.target.entrypoint.EntryPointAnalysis;
import tiro.target.event.CallPath;
import tiro.target.event.Event;
import tiro.target.event.SupportingEvent;
import tiro.target.traversal.CallGraphTraversal;

import soot.IntType;
import soot.MethodOrMethodContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyAnalysis {
    private static final int MAX_DEPENDENCY_DEPTH = 1;

    private final HeapDependencyResolver _heapDependencyResolver;
    private final ResourceDependencyResolver _resourceDependencyResolver;

    public DependencyAnalysis(ResourceAnalysis resourceAnalysis,
            EntryPointAnalysis entryPointAnalysis) {
        _heapDependencyResolver = new HeapDependencyResolver();
        _resourceDependencyResolver = new ResourceDependencyResolver(resourceAnalysis);
    }

    public List<CallGraphTraversal.Plugin> getCallGraphPlugins() {
        List<CallGraphTraversal.Plugin> plugins = new ArrayList<CallGraphTraversal.Plugin>();

        plugins.addAll(_heapDependencyResolver.getCallGraphPlugins());
        plugins.addAll(_resourceDependencyResolver.getCallGraphPlugins());

        return plugins;
    }

    public List<SupportingEvent> resolveEventDependencies(Event event) {
        return resolveEventDependencies(event, 0);
    }

    private List<SupportingEvent> resolveEventDependencies(Event event, int dependencyDepth) {
        if (dependencyDepth > MAX_DEPENDENCY_DEPTH) {
            return Collections.<SupportingEvent>emptyList();
        }

        computeEventDependencies(event);

        List<SupportingEvent> supportingEvents = new ArrayList<SupportingEvent>();
        for (Dependence dependence : event.getDependencies()) {
            supportingEvents.addAll(resolveDependence(event, dependence, dependencyDepth));
        }

        return supportingEvents;
    }

    private void computeEventDependencies(Event event) {
        // Note: heap dependencies should be added to the event when constraint analysis is
        // performed.
        //_heapDependencyResolver.computeEventDependencies(event);

        // Resource dependencies
        _resourceDependencyResolver.computeEventDependencies(event);
    }

    private List<SupportingEvent> resolveDependence(Event event, Dependence dependence,
            int dependencyDepth) {
        List<SupportingEvent> supportingEvents = new ArrayList<SupportingEvent>();

        boolean resolved = false;

        if (dependence instanceof HeapVariable) {
            HeapVariable heapDependence = (HeapVariable)dependence;
            SupportingEvent supportingEvent =
                    _heapDependencyResolver.resolveDependence(event, heapDependence);
            if (supportingEvent != null) {
                resolved = true;
                supportingEvents.add(supportingEvent);

                if (TIROStaticAnalysis.Config.PrintOutput) {
                    printSupportingEvent("heap dependence", supportingEvent);
                }
            }

        } else if (dependence instanceof KeyValueAccessVariable) {
            KeyValueAccessVariable resourceDependence = (KeyValueAccessVariable)dependence;
            _resourceDependencyResolver.resolveDependence(event, resourceDependence);
            resolved = true;

        } else {
            Output.warn("Unsupported dependence: " + dependence);
            return supportingEvents;
        }

        if (!resolved) {
            Output.warn("Could not resolve dependence: " + dependence);
            return supportingEvents;
        }

        // TODO: resolve the dependencies of the supporting event recursively

        for (SupportingEvent supportingEvent : supportingEvents) {
            List<SupportingEvent> recursedSupportingEvents =
                    resolveEventDependencies(supportingEvent, dependencyDepth + 1);
            supportingEvents.addAll(recursedSupportingEvents);
        }

        // Need to make sure events are resolved in order by the deepest dependency.
        Collections.reverse(supportingEvents);
        return supportingEvents;
    }

    private void printSupportingEvent(String dependencyName, SupportingEvent event) {
        Output.printSubtitle(dependencyName);
        Output.printPath("Event type: " + event.getTypeString());
        CallPath path = event.getPath();
        path.print();

        if (TIROStaticAnalysis.Config.PrintConstraints) {
            Predicate constraints = event.getConstraints();
            if (constraints != null) {
                constraints.print(1);
            }

            Predicate targetConstraint = event.getDependenceConstraint();
            if (targetConstraint != null) {
                Output.printConstraint("Target constraint: " + targetConstraint.toString());
            }
        }
    }
}
