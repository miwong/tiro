package tiro.target.callgraph;

import tiro.Output;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraphPatchingTag;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.LocalUses;
import soot.util.Chain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class AsyncTaskPatcher extends CallGraphPatcher {
    private static final SootClass _asyncTaskClass = Scene.v().getSootClass(
            "android.os.AsyncTask");
    private static final String _executeMethodSingature =
            "android.os.AsyncTask execute(java.lang.Object[])";

    private static final SootMethodRef _asyncTaskOnPreExecuteMethod =
            Scene.v().makeMethodRef(_asyncTaskClass, "onPreExecute",
                Collections.<Type>emptyList(), VoidType.v(), false);
    private static final SootMethodRef _asyncTaskDoInBackgroundMethod =
            Scene.v().makeMethodRef(_asyncTaskClass, "doInBackground",
                Arrays.asList(new Type[] { ArrayType.v(RefType.v("java.lang.Object"), 1) }),
                RefType.v("java.lang.Object"), false);
    private static final SootMethodRef _asyncTaskOnPostExecuteMethod =
            Scene.v().makeMethodRef(_asyncTaskClass, "onPostExecute",
                Arrays.asList(new Type[] { RefType.v("java.lang.Object") }),
                    VoidType.v(), false);

    public AsyncTaskPatcher(SootClass patchClass) {
        super(CallGraphPatchingTag.Kind.AsyncTask, patchClass);
    }

    @Override
    public boolean shouldPatch(final Body body, Stmt invokeStmt) {
        if (_asyncTaskClass.isPhantom()) {
            return false;
        }

        SootMethod invokedMethod = invokeStmt.getInvokeExpr().getMethod();
        if (!_cha.isClassSuperclassOfIncluding(_asyncTaskClass,
                invokedMethod.getDeclaringClass())) {
            return false;
        }

        if (invokedMethod.getSubSignature().equals(_executeMethodSingature)) {
            return true;
        }

        return false;
    }

    @Override
    public void patch(final Body body, UnitGraph cfg, LocalDefs localDefs,
            LocalUses localUses, Stmt invokeStmt) {
        InvokeExpr invoke = invokeStmt.getInvokeExpr();
        List<SootClass> targetAsyncTasks = findTargetAsyncTasks(localDefs, localUses,
                invokeStmt);
        if (targetAsyncTasks == null) {
            return;
        }

        for (SootClass targetAsyncTask : targetAsyncTasks) {
            SootMethod bridgeMethod = getBridgeMethod(invoke.getMethod(), targetAsyncTask);
            constructAsyncTaskBridge(bridgeMethod, targetAsyncTask);

            //Output.debug("patching AsyncTask: " + bridgeMethod);
            applyCallGraphPatch(invokeStmt, bridgeMethod);
        }
    }

    private List<SootClass> findTargetAsyncTasks(LocalDefs localDefs, LocalUses localUses,
            Stmt asyncTaskExecuteStmt) {
        InstanceInvokeExpr invoke =
                (InstanceInvokeExpr)asyncTaskExecuteStmt.getInvokeExpr();
        Value asyncTaskValue = invoke.getBase();
        if (!(asyncTaskValue instanceof Local)) {
            return null;
        }

        List<SootClass> targetClasses = new ArrayList<SootClass>();
        Local asyncTaskLocal = (Local)asyncTaskValue;

        for (Unit defUnit : localDefs.getDefsOfAt(asyncTaskLocal, asyncTaskExecuteStmt)) {
            if (defUnit instanceof AssignStmt) {
                AssignStmt defAssign = (AssignStmt)defUnit;
                if (defAssign.getRightOp() instanceof NewExpr) {
                    NewExpr defNewExpr = (NewExpr)defAssign.getRightOp();
                    if (!Scene.v().containsClass(defNewExpr.getType().toString())) {
                        continue;
                    }

                    SootClass targetClass = Scene.v().getSootClass(
                            defNewExpr.getType().toString());
                    if (_cha.isClassSuperclassOf(_asyncTaskClass, targetClass)) {
                        targetClasses.add(targetClass);
                    }
                }

                // TODO: also support cases where the type of async task is given in the as
                // the receiver of the invoke statement.
            }
        }

        return targetClasses;
    }

    private void constructAsyncTaskBridge(SootMethod method, SootClass targetAsyncTask) {
        JimpleBody body = Jimple.v().newBody(method);
        PatchingChain<Unit> units = body.getUnits();
        Chain<Local> locals = body.getLocals();

        // Handle the parameters.
        Local asyncTaskLocal = Jimple.v().newLocal("asyncTask",
                RefType.v("android.os.AsyncTask"));
        locals.add(asyncTaskLocal);
        ParameterRef asyncTaskParameterRef = Jimple.v().newParameterRef(
                RefType.v("android.os.AsyncTask"), 0);
        units.add(Jimple.v().newIdentityStmt(asyncTaskLocal, asyncTaskParameterRef));

        Local paramsLocal = Jimple.v().newLocal("params",
                ArrayType.v(RefType.v("java.lang.Object"), 1));
        locals.add(paramsLocal);
        ParameterRef paramsParameterRef = Jimple.v().newParameterRef(
                ArrayType.v(RefType.v("java.lang.Object"), 1), 1);
        units.add(Jimple.v().newIdentityStmt(paramsLocal, paramsParameterRef));

        // Call onPreExecute()
        InvokeExpr onPreExecuteInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                asyncTaskLocal, _asyncTaskOnPreExecuteMethod);
        units.add(Jimple.v().newInvokeStmt(onPreExecuteInvokeExpr));

        // Call doInBackground()
        Local resultsLocal = Jimple.v().newLocal("results", RefType.v("java.lang.Object"));
        locals.add(resultsLocal);
        InvokeExpr doInBackgroundInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                asyncTaskLocal, _asyncTaskDoInBackgroundMethod,
                Arrays.asList(new Value[] { paramsLocal }));
        units.add(Jimple.v().newAssignStmt(resultsLocal, doInBackgroundInvokeExpr));

        // Call onPostExecute()
        InvokeExpr onPostExecuteInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                asyncTaskLocal, _asyncTaskOnPostExecuteMethod,
                Arrays.asList(new Value[] { resultsLocal }));
        units.add(Jimple.v().newInvokeStmt(onPostExecuteInvokeExpr));

        // Return AsyncTask
        units.add(Jimple.v().newReturnStmt(asyncTaskLocal));

        method.setActiveBody(body);
    }
}
