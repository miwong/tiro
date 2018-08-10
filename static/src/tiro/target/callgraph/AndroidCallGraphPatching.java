package tiro.target.callgraph;

import tiro.Output;
import tiro.target.ManifestAnalysis;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraphPatchingTag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.UnitValueBoxPair;

import java.util.*;

// For a given Android-specific invocation, create a fake method in a fake class that
// instantiates the targeted class and calls the relevant method(s) in it.  Also pass
// any parameters (e.g. Bundle) through to the target class.  This fake method will serve
// as a bridge between the invocation and the Java-specific or Android-specific methods
// that are invoked as a result.
// Also, create a tag (e.g. CallGraphPatchingTag) that records the name of the bridge
// method.  When generating the call graph, these tags will be used to create the
// Android-specific call edges for caller -> bridge method -> callees.

public class AndroidCallGraphPatching extends SceneTransformer {
    private static final boolean DEBUG = false;

    private final ManifestAnalysis _manifestAnalysis;
    private final List<CallGraphPatcher> _patchers = new ArrayList<CallGraphPatcher>();
    private final SootClass _fakeClass;

    public AndroidCallGraphPatching(ManifestAnalysis manifestAnalysis) {
        _manifestAnalysis = manifestAnalysis;

        // Create the fake class for adding bridge methods.
        _fakeClass = createFakePatchClass();

        // Instantiate the classes that actually perform the patching.
        _patchers.add(new ActivityPatcher(_fakeClass, _manifestAnalysis));
        _patchers.add(new ServicePatcher(_fakeClass, _manifestAnalysis));
        _patchers.add(new AsyncTaskPatcher(_fakeClass));
        //_patchers.add(new ExecutorPatcher(_fakeClass));
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        for (SootClass klass : Scene.v().getClasses()) {
            if (!klass.isApplicationClass() || klass.isInterface()) {
                continue;
            }

            if (klass.equals(_fakeClass)) {
                continue;
            }

            try {
                for (SootMethod method : klass.getMethods()) {
                    if (!method.isConcrete()) {
                        continue;
                    }

                    Body body = method.retrieveActiveBody();
                    if (body != null) {
                        processMethod(body);
                    }
                }
            } catch (ConcurrentModificationException e) {
                Output.error("ConcurrentModificationException in " + klass);
                //e.printStackTrace();
            }
        }

        if (DEBUG) {
            for (SootMethod bridgeMethod : _fakeClass.getMethods()) {
                Output.debug("bridge method: " + bridgeMethod);
                for (Unit unit : bridgeMethod.getActiveBody().getUnits()) {
                    Output.debug("    " + unit);
                }
            }
        }
    }

    private void processMethod(final Body body) {
        class PatchingInfo {
            public final Stmt InvokeStmt;
            public final CallGraphPatcher Patcher;

            public PatchingInfo(Stmt invokeStmt, CallGraphPatcher patcher) {
                InvokeStmt = invokeStmt;
                Patcher = patcher;
            }
        }

        List<PatchingInfo> patches = new ArrayList<PatchingInfo>();

        // For each instruction, check if it is an invoke instruction and if so, whether it's
        // an invocation of interest.
        for (Unit unit : body.getUnits()) {
            Stmt stmt = (Stmt)unit;
            if (!stmt.containsInvokeExpr()) {
                continue;
            }

            _patchers.forEach(patcher -> {
                if (patcher.shouldPatch(body, stmt)) {
                    patches.add(new PatchingInfo(stmt, patcher));
                }
            });
        }

        // If there are any invocations that need to be patched in the call graph, have the
        // appropriate patcher patch them.
        if (!patches.isEmpty()) {
            BriefUnitGraph cfg = new BriefUnitGraph(body);
            SimpleLocalDefs localDefs = new SimpleLocalDefs(cfg);
            SimpleLocalUses localUses = new SimpleLocalUses(body, localDefs);

            patches.forEach(p -> {
                p.Patcher.patch(body, cfg, localDefs, localUses, p.InvokeStmt);
            });
        }
    }

    private SootClass createFakePatchClass() {
        SootClass patchClass = new SootClass(
                "tiro.patching.FakeAndroidCallGraphPatching", Modifier.PUBLIC);
        patchClass.setSuperclass(Scene.v().getSootClass("java.lang.Object"));
        Scene.v().addClass(patchClass);

        // Set this as an application class to make sure we traverse through it.
        patchClass.setApplicationClass();

        return patchClass;
    }
}
