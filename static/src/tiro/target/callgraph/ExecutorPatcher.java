package tiro.target.callgraph;

import tiro.Output;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraphPatchingTag;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.LocalUses;
import soot.util.Chain;

import java.util.Collections;
import java.util.List;

class ExecutorPatcher extends CallGraphPatcher {
    private static final SootClass _executorClass = Scene.v().getSootClass(
            "java.util.concurrent.Executor");
    private static final String _executeMethodSingature =
            "void execute(java.lang.Runnable)";

    private static final SootClass _runnableClass = Scene.v().getSootClass(
            "java.lang.Runnable");
    private static final SootMethodRef _runnableRunMethod = Scene.v().makeMethodRef(
            _runnableClass, "run", Collections.<Type>emptyList(), VoidType.v(), false);

    public ExecutorPatcher(SootClass patchClass) {
        super(CallGraphPatchingTag.Kind.Executor, patchClass);
    }

    @Override
    public boolean shouldPatch(final Body body, Stmt invokeStmt) {
        SootMethod invokedMethod = invokeStmt.getInvokeExpr().getMethod();
        // TODO: do we need to handle sub-interfaces and implementor classes?
        if (!invokedMethod.getDeclaringClass().equals(_executorClass)) {
            return false;
        }

        if (invokedMethod.getSubSignature().equals(_executeMethodSingature)) {
            return true;
        }

        return false;
    }

    @Override
    public void patch(final Body body, UnitGraph cfg, LocalDefs localDefs, LocalUses localUses,
                      Stmt invokeStmt) {
        InvokeExpr invoke = invokeStmt.getInvokeExpr();
        List<SootClass> targetRunnables = findTargetRunnables(localDefs, localUses,
                invokeStmt);
        if (targetRunnables == null) {
            return;
        }

        for (SootClass targetRunnable : targetRunnables) {
            SootMethod bridgeMethod = getBridgeMethod(invoke.getMethod(), targetRunnable);
            constructExecutorBridge(bridgeMethod);

            //Output.debug("patching Executor: " + bridgeMethod);
            applyCallGraphPatch(invokeStmt, bridgeMethod);
        }
    }

    private List<SootClass> findTargetRunnables(LocalDefs localDefs, LocalUses localUses,
            Stmt executeInvokeStmt) {
        return null;
    }

    private void constructExecutorBridge(SootMethod method) {
        JimpleBody body = Jimple.v().newBody(method);
        PatchingChain<Unit> units = body.getUnits();
        Chain<Local> locals = body.getLocals();

        // Handle the parameters.
        Local executorLocal = Jimple.v().newLocal("executor",
                RefType.v("java.util.concurrent.Executor"));
        locals.add(executorLocal);
        ParameterRef executorParameterRef = Jimple.v().newParameterRef(
                RefType.v("java.util.concurrent.Executor"), 0);
        units.add(Jimple.v().newIdentityStmt(executorLocal, executorParameterRef));

        Local runnableLocal = Jimple.v().newLocal("runnable",
                RefType.v("java.lang.Runnable"));
        locals.add(runnableLocal);
        ParameterRef runnableParameterRef = Jimple.v().newParameterRef(
                RefType.v("java.lang.Runnable"), 1);
        units.add(Jimple.v().newIdentityStmt(runnableLocal, runnableParameterRef));

        // Call run()
        InvokeExpr runInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                runnableLocal, _runnableRunMethod);
        units.add(Jimple.v().newInvokeStmt(runInvokeExpr));

        // Return
        units.add(Jimple.v().newReturnVoidStmt());

        method.setActiveBody(body);
    }
}
