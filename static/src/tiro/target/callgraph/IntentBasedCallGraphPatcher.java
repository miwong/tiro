package tiro.target.callgraph;

import tiro.Output;
import tiro.target.ManifestAnalysis;
import tiro.target.methods.IntentMethods;

import soot.*;
import soot.jimple.ClassConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.CallGraphPatchingTag;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.LocalUses;
import soot.toolkits.scalar.UnitValueBoxPair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class IntentBasedCallGraphPatcher extends CallGraphPatcher {
    protected static final SootClass _contextClass = Scene.v().getSootClass(
            "android.content.Context");

    // Note: The manifest analysis will be used when we add support for identifying the intent
    // target via the action flag.
    protected final ManifestAnalysis _manifestAnalysis;

    protected IntentBasedCallGraphPatcher(CallGraphPatchingTag.Kind kind, SootClass patchClass,
            ManifestAnalysis manifestAnalysis) {
        super(kind, patchClass);
        _manifestAnalysis = manifestAnalysis;
    }

    protected List<SootClass> findTargetClassesFromIntent(final Body body, UnitGraph cfg,
            LocalDefs localDefs, LocalUses localUses, Stmt invokeStmt, Value intentValue) {
        if (!(intentValue instanceof Local)) {
            return null;
        }

        //InvokeExpr invoke = invokeStmt.getInvokeExpr();
        final List<SootClass> targetClasses = new ArrayList<SootClass>();
        Local intentLocal = (Local)intentValue;

        processDefUseForBuilderPattern(
                body, localDefs, localUses, invokeStmt, intentLocal, stmt -> {
                if (!stmt.containsInvokeExpr()) {
                    return;
                }

                InvokeExpr invoke = stmt.getInvokeExpr();
                SootMethod invokedMethod = invoke.getMethod();

                if (!IntentMethods.isIntentTargetMethod(invokedMethod)) {
                    return;
                }

                int targetIndex = IntentMethods.getIntentTargetParameterIndex(invokedMethod);
                Value targetValue = invoke.getArg(targetIndex);

                switch (IntentMethods.getIntentTargetType(invokedMethod)) {
                    case CLASS:
                        targetClasses.addAll(findClassesFromIntentClass(
                                localDefs, localUses, stmt, targetValue));
                        break;
                    case STRING:
                        targetClasses.addAll(findClassesFromIntentClassName(
                                localDefs, localUses, stmt, targetValue));
                        break;
                    case COMPONENT_NAME:
                        targetClasses.addAll(findClassesFromIntentComponentName(
                                localDefs, localUses, stmt, targetValue));
                        break;
                    default:
                        break;
                }
            }
        );

        return targetClasses;
    }

    private List<SootClass> findClassesFromIntentClass(LocalDefs localDefs,
            LocalUses localUses, Stmt setClassInvokeStmt, Value classValue) {
        if (classValue instanceof ClassConstant) {
            ClassConstant classConstant = (ClassConstant)classValue;
            String className = classConstant.getValue().replace('/', '.');
            if (Scene.v().containsClass(className)) {
                return Collections.singletonList(Scene.v().getSootClass(className));
            }
        }

        return Collections.emptyList();
    }

    private List<SootClass> findClassesFromIntentClassName(LocalDefs localDefs,
            LocalUses localUses, Stmt setClassNameInvokeStmt, Value classNameValue) {
        if (classNameValue instanceof StringConstant) {
            String className = classNameValue.toString();
            if (Scene.v().containsClass(className)) {
                return Collections.singletonList(Scene.v().getSootClass(className));
            }
        }

        return Collections.emptyList();
    }

    private List<SootClass> findClassesFromIntentComponentName(LocalDefs localDefs,
            LocalUses localUses, Stmt setComponentInvokeStmt, Value componentValue) {
        if (componentValue == null || !(componentValue instanceof Local)) {
            return Collections.emptyList();
        }

        final List<SootClass> targetClasses = new ArrayList<SootClass>();
        Local componentLocal = (Local)componentValue;

        for (Unit defUnit : localDefs.getDefsOfAt(componentLocal, setComponentInvokeStmt)) {
            for (UnitValueBoxPair useUnitValue : localUses.getUsesOf(defUnit)) {
                Stmt useStmt = (Stmt)useUnitValue.getUnit();
                if (!useStmt.containsInvokeExpr()) {
                    continue;
                }

                InvokeExpr useInvoke = useStmt.getInvokeExpr();
                SootMethod invokedMethod = useInvoke.getMethod();

                if (!IntentMethods.isIntentTargetMethod(invokedMethod)) {
                    continue;
                }

                int targetIndex = IntentMethods.getIntentTargetParameterIndex(invokedMethod);
                Value targetValue = useInvoke.getArg(targetIndex);

                switch (IntentMethods.getIntentTargetType(invokedMethod)) {
                    case CLASS:
                        targetClasses.addAll(findClassesFromIntentClass(
                                localDefs, localUses, useStmt, targetValue));
                        break;
                    case STRING:
                        targetClasses.addAll(findClassesFromIntentClassName(
                                localDefs, localUses, useStmt, targetValue));
                        break;
                    default:
                        break;
                }
            }
        }

        return targetClasses;
    }

    //private List<SootClass> findClassesFromIntentAction(LocalDefs localDefs,
    //        LocalUses localUses, Stmt setActionInvokeStmt, Value actionValue) {
    //    return Collections.emptyList();
    //}
}
