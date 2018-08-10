package tiro.target;

import tiro.*;
import tiro.target.constraint.*;
import tiro.target.dependency.*;
import tiro.target.entrypoint.*;
import tiro.target.event.*;
import tiro.target.traversal.*;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TargetedPathsAnalysis extends SceneTransformer {
    private final ManifestAnalysis _manifestAnalysis;
    private final EntryPointAnalysis _entryPointAnalysis;
    private final DependencyAnalysis _dependencyAnalysis;
    private CallGraph _callGraph = null;

    private java.util.Timer _interruptTimer = null;

    private TargetedPathsCallGraphPlugin _callGraphPlugin =
            new TargetedPathsCallGraphPlugin();

    public TargetedPathsAnalysis(ManifestAnalysis manifestAnalysis,
                                 EntryPointAnalysis entryPointAnalysis,
                                 DependencyAnalysis dependencyAnalysis) {
        _manifestAnalysis = manifestAnalysis;
        _entryPointAnalysis = entryPointAnalysis;
        _dependencyAnalysis = dependencyAnalysis;
    }

    public CallGraphTraversal.Plugin getCallGraphPlugin() {
        return _callGraphPlugin;
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        _callGraph = Scene.v().getCallGraph();
        analyzePaths();
    }

    private class InterruptionTask extends TimerTask {
        private final Thread _thread;

        public InterruptionTask(Thread thread) {
            _thread = thread;
        }

        @Override
        public void run() {
            _thread.interrupt();
        }
    }

    private class TargetedPathTask implements Callable<Boolean> {
        private final AppInfoWriter _appInfoWriter;
        private final CallPath _callPath;

        public TargetedPathTask(AppInfoWriter appInfoWriter, CallPath path) {
            _appInfoWriter = appInfoWriter;
            _callPath = path;
        }

        @Override
        public Boolean call() {
            TimerTask timeout = new InterruptionTask(Thread.currentThread());
            _interruptTimer.schedule(timeout,
                                     TIROStaticAnalysis.Config.TargetedPathTimeout);

            Output.startBuffering();

            try {
                EventChain eventChain = analyzeTargetedPath(_callPath);
                if (eventChain != null) {
                    _appInfoWriter.addEventChain(eventChain);
                }
                return true;
            } catch (StaticAnalysisTimeoutException e) {
                if (TIROStaticAnalysis.Config.PrintOutput) {
                    Output.clearBuffer();
                    Output.printPath("Targeted Path [timeout]");
                    _callPath.print();
                    Output.printEventChainDivider();
                }
                return false;
            } finally {
                // Cancel the timeout timer and clear the interrupt flag in case this thread
                // is reused.
                timeout.cancel();
                Thread.interrupted();

                Output.flushBuffer();
            }
        }
    }

    private void analyzePaths() {
        Output.progress("Analyzing targeted paths");
        Output.debug("Number of paths: " + _callGraphPlugin.getTargetedPaths().size());

        AppInfoWriter appInfoWriter = new AppInfoWriter(_manifestAnalysis);
        Stream<CallPath> targetedPaths = _callGraphPlugin.getTargetedPaths().stream();

        if (!TIROStaticAnalysis.Config.MultiThreading) {
            targetedPaths.forEach(path -> {
                EventChain eventChain = analyzeTargetedPath(path);
                if (eventChain != null) {
                    appInfoWriter.addEventChain(eventChain);
                }
            });
        } else {
            // Create a thread pool to analyze the targeted paths.
            int numThreads = TIROStaticAnalysis.Config.NumberOfThreads - 1;
            final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<TargetedPathTask> targetedPathsTasks = targetedPaths.map(path -> {
                return new TargetedPathTask(appInfoWriter, path);
            }).collect(Collectors.toList());

            // Implement a per-path timeout as well as an overall timeout.
            _interruptTimer = new java.util.Timer("TargetedPathsAnalysisTimerThread");

            List<Future<Boolean>> results = null;
            try {
                if (TIROStaticAnalysis.Config.Timeout > 0) {
                    long remainingTime = TIROStaticAnalysis.Config.Timeout
                            - (System.currentTimeMillis()
                                    - TIROStaticAnalysis.Config.StartTime);
                    results = executor.invokeAll(targetedPathsTasks,
                                                 remainingTime, TimeUnit.MILLISECONDS);
                } else {
                    results = executor.invokeAll(targetedPathsTasks);
                }
            } catch (InterruptedException e) {
                Output.warn("Targeted paths analysis was interrupted.");
            }

            // Shut down the thread pool and wait until all remaining tasks have completed or
            // timed out.
            executor.shutdownNow();
            try {
                executor.awaitTermination(TIROStaticAnalysis.Config.TargetedPathTimeout,
                                          TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Output.warn("Targeted paths analysis was interrupted.");
            }

            _interruptTimer.cancel();
            _interruptTimer.purge();

            // Determine  whether the targeted path tasks completed successfully (constraints
            // were extracted and dependencies resolved).
            boolean success = (results == null) ? false : results.stream().map(future -> {
                try {
                    return future.get();
                } catch (CancellationException e) {
                    return false;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }).reduce(true, (a, b) -> a & b);

            if (!success) {
                Output.warn("Some targeted paths failed or timed out during analysis");
            }
        }

        Output.progress("Writing " + TIROStaticAnalysis.Config.OutputDirectory
                        + "/appInfo.json");
        appInfoWriter.writeFinalFile();
    }

    private EventChain analyzeTargetedPath(CallPath callPath) {
        ConstraintAnalysis constraintAnalysis = new ConstraintAnalysis(callPath);
        Predicate pathConstraints = constraintAnalysis.getConstraints();
        if (pathConstraints != null && pathConstraints.isFalse()) {
            // This path is a false positive.
            return null;
        }

        // Create event for targeted path
        Event targetedEvent = new Event(callPath, pathConstraints);

        // Construct an event chain to handle any path dependences.
        EventChain eventChain = new EventChain();

        if (TIROStaticAnalysis.Config.PrintOutput) {
            Output.printPath("Event Chain (" + eventChain.getId() + ")");
            Output.printPath("Event type: " + targetedEvent.getTypeString());
            callPath.print();

            if (TIROStaticAnalysis.Config.PrintConstraints) {
                if (pathConstraints != null) {
                    pathConstraints.print(1);
                }
            }
        }

        // We need to add heap dependencies explicitly since they're generated by the
        // constraint analysis.
        List<HeapVariable> heapDependencies = constraintAnalysis.getHeapDependencies();
        targetedEvent.addDependencies(heapDependencies);

        // Resolve dependencies and add their dependence constraint to the targeted event.
        List<SupportingEvent> supportingEvents =
                _dependencyAnalysis.resolveEventDependencies(targetedEvent);

        // Targeted path constraints may have changed while resolving dependencies.
        pathConstraints = targetedEvent.getConstraints();

        // Print updated targeted event constraints (due to dependencies).
        if (TIROStaticAnalysis.Config.PrintConstraints) {
            if (!supportingEvents.isEmpty()) {
                if (pathConstraints != null) {
                    Output.printSubtitle("final targeted path constraints");
                    pathConstraints.print(1);

                    // Sanity check
                    Z3Solver.inputValuesSanityCheck(pathConstraints);
                }
            }
        }

        // Add events to event chain.
        eventChain.addDependentEvent(targetedEvent);
        supportingEvents.forEach(e -> { eventChain.addDependentEvent(e); });

        if (TIROStaticAnalysis.Config.PrintOutput) {
            Output.printEventChainDivider();
        }

        return eventChain;
    }

    private class TargetedPathsCallGraphPlugin implements CallGraphTraversal.Plugin {
        private List<CallPath> _targetedPaths = new ArrayList<CallPath>();

        public List<CallPath> getTargetedPaths() {
            return _targetedPaths;
        }

        @Override
        public boolean processUnit(SootMethod method, Unit unit) {
            Stmt stmt = (Stmt)unit;
            if (!stmt.containsInvokeExpr()) {
                return false;
            }

            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            String invokeSignature = invokeExpr.getMethodRef().getSignature();
            boolean isTarget = false;

            // Analyze declared target of edge
            if (TIROStaticAnalysis.Config.TargetMethods.contains(invokeSignature)) {
                isTarget = true;
            }

            // Analyze resolved targets of edge
            Iterator<Edge> targetEdgeIter = Scene.v().getCallGraph().edgesOutOf(stmt);
            while (targetEdgeIter.hasNext()) {
                Edge targetEdge = targetEdgeIter.next();
                String tgtSignature = targetEdge.tgt().getSignature();

                if (TIROStaticAnalysis.Config.TargetMethods.contains(tgtSignature)) {
                    isTarget = true;
                }
            }

            return isTarget;
        }

        @Override
        public void onTargetPath(CallPath path) {
            _targetedPaths.add(path);
        }
    }
}
