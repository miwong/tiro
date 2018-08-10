package tiro.target.dependency;

import tiro.Output;
import tiro.target.*;
import tiro.target.constraint.*;
import tiro.target.event.*;
import tiro.target.traversal.CallGraphTraversal;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.*;

import java.util.*;
import java.util.stream.Collectors;

class HeapDependencyResolver extends DependencyResolver<HeapVariable> {
    // Cache of previously resolved heap writes
    private final Map<HeapVariable, List<CachedHeapWriteEvent>> _cachedHeapWrites =
            new HashMap<HeapVariable, List<CachedHeapWriteEvent>>();

    private HeapCallGraphPlugin _callGraphPlugin = new HeapCallGraphPlugin(_cachedHeapWrites);

    @Override
    public List<CallGraphTraversal.Plugin> getCallGraphPlugins() {
        return Collections.<CallGraphTraversal.Plugin>singletonList(_callGraphPlugin);
    }

    @Override
    public void computeEventDependencies(Event event) {
        // Heap dependencies should be explicitly added to the event after constraint
        // analysis.
        return;
    }

    @Override
    public SupportingEvent resolveDependence(Event event, HeapVariable dependence) {
        SupportingEvent cachedEvent = findCachedSupportingEvent(event, dependence);
        if (cachedEvent == null) {
            return null;
        }

        // Update event constraints to account for stored heap value.
        Predicate constraints = event.getConstraints();
        constraints = Predicate.combine(Predicate.Operator.AND,
                constraints, cachedEvent.getDependenceConstraint());
        event.updateConstraints(constraints);

        // Clone the cached supporting event, since its constraints can depend on the
        // constraints of the enclosing event chain.
        return cachedEvent.clone();
    }

    private SupportingEvent findCachedSupportingEvent(Event event, HeapVariable dependence) {
        // Instead of using equality, determine resolution by checking whether points-to sets
        // intersect with the specified heap variable dependence.
        Set<HeapVariable> heapIntersections = _cachedHeapWrites.keySet().stream()
                .filter(x -> x.intersects(dependence))
                .collect(Collectors.toSet());

        for (HeapVariable heapIntersection : heapIntersections) {
            for (CachedHeapWriteEvent cached : _cachedHeapWrites.get(heapIntersection)) {
                SupportingEvent supportingEvent = cached.getSupportingEvent(dependence);
                if (supportingEvent == null) {
                    // This cached dependency is invalid and cannot be resolved.
                    continue;
                }

                // Check if resolved dependence contradicts the target path's constraints
                if (supportingEvent.canResolveDependencyForEvent(event, dependence)) {
                    return supportingEvent;
                }
            }
        }

        return null;
    }

    private class HeapCallGraphPlugin implements CallGraphTraversal.Plugin {
        private final Map<HeapVariable, List<CachedHeapWriteEvent>> _cachedHeapWrites;

        public HeapCallGraphPlugin(Map<HeapVariable, List<CachedHeapWriteEvent>> cache) {
            _cachedHeapWrites = cache;
        }

        @Override
        public boolean processUnit(SootMethod method, Unit unit) {
            if (!(unit instanceof AssignStmt)) {
                return false;
            }

            AssignStmt assignStmt = (AssignStmt)unit;
            if (assignStmt.getLeftOp() instanceof FieldRef) {
                return true;
            }

            return false;
        }

        @Override
        public void onTargetPath(CallPath path) {
            AssignStmt assignStmt = (AssignStmt)path.getTargetUnit();
            FieldRef fieldRef = (FieldRef)assignStmt.getLeftOp();
            HeapVariable heapVariable = new HeapVariable(fieldRef);

            CachedHeapWriteEvent cached = new CachedHeapWriteEvent(path);
            _cachedHeapWrites.computeIfAbsent(heapVariable,
                    k -> new ArrayList<CachedHeapWriteEvent>()).add(cached);
        }
    }

    private class CachedHeapWriteEvent {
        private final CallPath _callPath;
        private SupportingEvent _supportingEvent = null;

        public CachedHeapWriteEvent(CallPath callPath) {
            _callPath = callPath;
        }

        public CallPath getPath() {
            return _callPath;
        }

        public SupportingEvent getSupportingEvent(HeapVariable dependence) {
            if (_supportingEvent == null) {
                resolve(dependence);
            }

            return _supportingEvent;
        }

        private synchronized void resolve(HeapVariable dependence) {
            if (_supportingEvent != null) {
                return;
            }

            HeapDependenceConstraintAnalysis heapConstraintAnalysis =
                    new HeapDependenceConstraintAnalysis(_callPath, dependence);
            Predicate pathConstraints = heapConstraintAnalysis.getConstraints();
            Predicate storeConstraint = heapConstraintAnalysis.getHeapStoreConstraint();

            if (storeConstraint == null) {
                Output.error("Heap dependence analysis did not retrieve stored heap value "
                             + "for: " + dependence);
                return;
            }

            _supportingEvent = new SupportingEvent(
                    _callPath, pathConstraints, storeConstraint);
            _supportingEvent.addDependencies(heapConstraintAnalysis.getHeapDependencies());
        }
    }
}
