package tiro.target.callgraph;

import tiro.Output;
import tiro.target.ManifestAnalysis;
import tiro.target.methods.ActivityMethods;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraphPatchingTag;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.LocalUses;
import soot.util.Chain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ActivityPatcher extends IntentBasedCallGraphPatcher {
    private static final SootClass _activityClass = Scene.v().getSootClass(
            "android.app.Activity");
    private static final String _startActivityMethodSignature =
            "void startActivity(android.content.Intent)";
    //private static final String _startActivityWithBundleMethodSignature =
    //        "<android.content.Context: void startActivity(android.content.Intent," +
    //        "android.os.Bundle)>");

    private static final SootMethod _intentGetExtrasMethod = Scene.v().getMethod(
            "<android.content.Intent: android.os.Bundle getExtras()>");

    public ActivityPatcher(SootClass patchClass, ManifestAnalysis manifestAnalysis) {
        super(CallGraphPatchingTag.Kind.Activity, patchClass, manifestAnalysis);
    }

    @Override
    public boolean shouldPatch(final Body body, Stmt invokeStmt) {
        SootMethod invokedMethod = invokeStmt.getInvokeExpr().getMethod();
        if (!_cha.isClassSuperclassOfIncluding(_contextClass,
                invokedMethod.getDeclaringClass())) {
            return false;
        }

        if (invokedMethod.getSubSignature().equals(_startActivityMethodSignature)) {
            return true;
        }

        return false;
    }

    @Override
    public void patch(final Body body, UnitGraph cfg, LocalDefs localDefs, LocalUses localUses,
                      Stmt invokeStmt) {
        InvokeExpr invoke = invokeStmt.getInvokeExpr();
        List<SootClass> targetActivities = findTargetClassesFromIntent(
                body, cfg, localDefs, localUses, invokeStmt, invoke.getArg(0));
        if (targetActivities == null) {
            return;
        }

        for (SootClass targetActivity : targetActivities) {
            if (!_cha.isClassSuperclassOf(_activityClass, targetActivity)) {
                continue;
            }

            //Output.debug("patching startActivity in: " + body.getMethod().getSignature());

            // Get bridge method/patch and add tag for call graph generation.
            // TODO: currently, method body is beign re-constructed, even if the patch class
            // already has a version of this bridge method.
            SootMethod bridgeMethod = getBridgeMethod(invoke.getMethod(), targetActivity);
            constructActivityBridge(bridgeMethod, targetActivity);

            applyCallGraphPatch(invokeStmt, bridgeMethod);
        }
    }

    private void constructActivityBridge(SootMethod method, SootClass targetActivity) {
        JimpleBody body = Jimple.v().newBody(method);
        PatchingChain<Unit> units = body.getUnits();
        Chain<Local> locals = body.getLocals();

        // Handle the parameters.
        List<Local> parameterLocals = handleBridgeMethodParameters(method, units, locals);
        Local intentLocal = parameterLocals.get(1);

        // Retrieve the bundle from the intent.
        Local bundleLocal = Jimple.v().newLocal("bundle", RefType.v("android.os.Bundle"));
        locals.add(bundleLocal);
        InvokeExpr getExtrasInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                intentLocal, _intentGetExtrasMethod.makeRef());
        units.add(Jimple.v().newAssignStmt(bundleLocal, getExtrasInvokeExpr));

        // Instantiate the activity.
        Local activityLocal = Jimple.v().newLocal("activity", targetActivity.getType());
        locals.add(activityLocal);
        NewExpr newActivityExpr = Jimple.v().newNewExpr(targetActivity.getType());
        units.add(Jimple.v().newAssignStmt(activityLocal, newActivityExpr));

        // Call the activity's constructor.
        SootMethod initMethod = targetActivity.getMethod("void <init>()");
        InvokeExpr initInvokeExpr = Jimple.v().newSpecialInvokeExpr(
                activityLocal, initMethod.makeRef());
        units.add(Jimple.v().newInvokeStmt(initInvokeExpr));

        // Call the lifecycle methods.
        for (SootMethodRef lifecycleMethod : ActivityMethods.getLifecycleMethods()) {
            // Handle onCreate specially due to arguments.
            if (lifecycleMethod.name().equals("onCreate")) {
                if (lifecycleMethod.parameterTypes().size() == 1) {
                    InvokeExpr onCreateInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                            activityLocal, lifecycleMethod, bundleLocal);
                    units.add(Jimple.v().newInvokeStmt(onCreateInvokeExpr));
                } else {
                    InvokeExpr onCreateInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                            activityLocal, lifecycleMethod, bundleLocal, bundleLocal);
                    units.add(Jimple.v().newInvokeStmt(onCreateInvokeExpr));
                }
            } else {
                InvokeExpr lifecycleInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                        activityLocal, lifecycleMethod);
                units.add(Jimple.v().newInvokeStmt(lifecycleInvokeExpr));
            }
        }

        // TODO: call parent activity's onActivityResult method when result is required.

        // Return
        units.add(Jimple.v().newReturnVoidStmt());

        method.setActiveBody(body);
    }
}
