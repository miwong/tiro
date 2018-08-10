/*******************************************************************************
 * This class was borrowed from the FlowDroid project (soot-infoflow-android).
 * Modifications:
 * - Track unresolved registrations of broadcast receivers.
 *
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/

package soot.jimple.infoflow.android.callbacks;

import soot.Body;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.util.SootMethodRepresentationParser;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SmartLocalDefs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Analyzes the classes in the APK file to find custom implementations of the
 * well-known Android callback and handler interfaces.
 * 
 * @author Steven Arzt
 *
 */
public abstract class AbstractCallbackAnalyzer {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final InfoflowAndroidConfiguration config;
    protected final Set<String> entryPointClasses;
    protected final Set<String> androidCallbacks;

    protected final Map<String, Set<SootMethodAndClass>> callbackMethods =
            new HashMap<String, Set<SootMethodAndClass>>();
    protected final Map<String, Set<Integer>> layoutClasses =
            new HashMap<String, Set<Integer>>();
    protected final Set<String> dynamicManifestComponents =
            new HashSet<>();
    protected final Set<SootClass> unresolvableDynamicRegistrations =
            new HashSet<>();

    public AbstractCallbackAnalyzer(InfoflowAndroidConfiguration config,
            Set<String> entryPointClasses) throws IOException {
        this(config, entryPointClasses, "AndroidCallbacks.txt");
    }

    public AbstractCallbackAnalyzer(InfoflowAndroidConfiguration config,
            Set<String> entryPointClasses,
            String callbackFile) throws IOException {
        this.config = config;
        this.entryPointClasses = entryPointClasses;
        this.androidCallbacks = loadAndroidCallbacks(callbackFile);
    }

    public AbstractCallbackAnalyzer(InfoflowAndroidConfiguration config,
            Set<String> entryPointClasses,
            Set<String> androidCallbacks) throws IOException {
        this.config = config;
        this.entryPointClasses = entryPointClasses;
        this.androidCallbacks = androidCallbacks;
    }

    /**
     * Loads the set of interfaces that are used to implement Android callback
     * handlers from a file on disk
     * @param androidCallbackFile The file from which to load the callback definitions
     * @return A set containing the names of the interfaces that are used to
     * implement Android callback handlers
     */
    private Set<String> loadAndroidCallbacks(String androidCallbackFile) throws IOException {
        Set<String> androidCallbacks = new HashSet<String>();
        BufferedReader rdr = null;
        try {
            String fileName = androidCallbackFile;
            if (!new File(fileName).exists()) {
                fileName = "../soot-infoflow-android/AndroidCallbacks.txt";
                if (!new File(fileName).exists()) {
                    throw new RuntimeException("Callback definition file not found");
                }
            }
            rdr = new BufferedReader(new FileReader(fileName));
            String line;
            while ((line = rdr.readLine()) != null) {
                if (!line.isEmpty()) {
                    androidCallbacks.add(line);
                }
            }
        } finally {
            if (rdr != null) {
                rdr.close();
            }
        }
        return androidCallbacks;
    }

    /**
     * Collects the callback methods for all Android default handlers
     * implemented in the source code.
     */
    public abstract void collectCallbackMethods();

    /**
     * Collects the callback methods that have been added since the last run.
     * The semantics of this method depends on the concrete implementation. For
     * non-incremental analyses, this method does nothing.
     */
    public abstract void collectCallbackMethodsIncremental();

    /**
     * Analyzes the given method and looks for callback registrations
     * @param lifecycleElement The lifecycle element (activity, etc.) with which
     * to associate the found callbacks
     * @param method The method in which to look for callbacks
     */
    protected void analyzeMethodForCallbackRegistrations(SootClass lifecycleElement,
            SootMethod method) {
        // Do not analyze system classes
        if (method.getDeclaringClass().getName().startsWith("android.")
                || method.getDeclaringClass().getName().startsWith("java.")) {
            return;
        }
        if (!method.isConcrete()) {
            return;
        }

        ExceptionalUnitGraph graph = new ExceptionalUnitGraph(method.retrieveActiveBody());
        SmartLocalDefs smd = new SmartLocalDefs(graph, new SimpleLiveLocals(graph));

        // Iterate over all statement and find callback registration methods
        Set<SootClass> callbackClasses = new HashSet<SootClass>();
        for (Unit u : method.retrieveActiveBody().getUnits()) {
            Stmt stmt = (Stmt) u;
            // Callback registrations are always instance invoke expressions
            if (stmt.containsInvokeExpr()
                    && stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
                InstanceInvokeExpr iinv = (InstanceInvokeExpr) stmt.getInvokeExpr();

                String[] parameters =
                        SootMethodRepresentationParser.v().getParameterTypesFromSubSignature(
                                iinv.getMethodRef().getSubSignature().getString());
                for (int i = 0; i < parameters.length; i++) {
                    String param = parameters[i];
                    if (androidCallbacks.contains(param)) {
                        Value arg = iinv.getArg(i);

                        // We have a formal parameter type that corresponds to one of the
                        // Android callback interfaces. Look for definitions of the parameter
                        // to estimate the actual type.
                        if (arg.getType() instanceof RefType && arg instanceof Local) {
                            for (Unit def : smd.getDefsOfAt((Local) arg, u)) {
                                assert def instanceof DefinitionStmt;
                                Type tp = ((DefinitionStmt) def).getRightOp().getType();
                                if (tp instanceof RefType) {
                                    SootClass callbackClass = ((RefType) tp).getSootClass();
                                    if (callbackClass.isInterface()) {
                                        for (SootClass impl : Scene.v().getActiveHierarchy()
                                                .getImplementersOf(callbackClass)) {
                                            for (SootClass c : Scene.v().getActiveHierarchy()
                                                    .getSubclassesOfIncluding(impl)) {
                                                callbackClasses.add(c);
                                            }
                                        }
                                    } else {
                                        for (SootClass c : Scene.v().getActiveHierarchy()
                                                .getSubclassesOfIncluding(callbackClass)) {
                                            callbackClasses.add(c);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Analyze all found callback classes
        for (SootClass callbackClass : callbackClasses) {
            analyzeClass(callbackClass, lifecycleElement);
        }
    }

    /**
     * Checks whether the given method dynamically registers a new broadcast
     * receiver
     * @param method The method to check
     */
    protected void analyzeMethodForDynamicBroadcastReceiver(SootMethod method) {
        if (!method.isConcrete() || !method.hasActiveBody()) {
            return;
        }
        // stmt.getInvokeExpr().getMethod().getDeclaringClass().getName().equals(
        //         "android.content.Context")

        for (Unit u : method.getActiveBody().getUnits()) {
            Stmt stmt = (Stmt) u;
            if (stmt.containsInvokeExpr()) {
                if (stmt.getInvokeExpr().getMethod().getName().equals("registerReceiver")
                        && stmt.getInvokeExpr().getArgCount() > 0
                        && isInheritedMethod(stmt, "android.content.ContextWrapper",
                                "android.content.Context")) {
                    Value br = stmt.getInvokeExpr().getArg(0);
                    if (br.getType() instanceof RefType) {
                        RefType rt = (RefType) br.getType();
                        if (rt.getClassName().equals("android.content.BroadcastReceiver")) {
                            unresolvableDynamicRegistrations.add(method.getDeclaringClass());
                        } else {
                            dynamicManifestComponents.add(rt.getClassName());
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets whether the call in the given statement can end up in the respective method
     * inherited from one of the given classes.
     * @param stmt The statement containing the call sites
     * @param classNames The base classes in which the call can potentially end up
     * @return True if the given call can end up in a method inherited from one of
     * the given classes, otherwise falae
     */
    private boolean isInheritedMethod(Stmt stmt, String... classNames) {
        if (!stmt.containsInvokeExpr()) {
            return false;
        }

        // Look at the direct callee
        SootMethod tgt = stmt.getInvokeExpr().getMethod();
        for (String className : classNames) {
            if (className.equals(tgt.getDeclaringClass().getName())) {
                return true;
            }
        }

        // If we have a callgraph, we can use that.
        if (Scene.v().hasCallGraph()) {
            Iterator<Edge> edgeIt = Scene.v().getCallGraph().edgesOutOf(stmt);
            while (edgeIt.hasNext()) {
                Edge edge = edgeIt.next();
                String targetClass = edge.getTgt().method().getDeclaringClass().getName();
                for (String className : classNames) {
                    if (className.equals(targetClass)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether this invocation calls Android's Activity.setContentView
     * method
     * @param inv The invocaton to check
     * @return True if this invocation calls setContentView, otherwise false
     */
    protected boolean invokesSetContentView(InvokeExpr inv) {
        String methodName = SootMethodRepresentationParser.v().getMethodNameFromSubSignature(
                inv.getMethodRef().getSubSignature().getString());
        if (!methodName.equals("setContentView")) {
            return false;
        }

        // In some cases, the bytecode points the invocation to the current
        // class even though it does not implement setContentView, instead
        // of using the superclass signature
        SootClass curClass = inv.getMethod().getDeclaringClass();
        while (curClass != null) {
            if (curClass.getName().equals("android.app.Activity")
                    || curClass.getName().equals("android.support.v7.app.ActionBarActivity")) {
                return true;
            }
            if (curClass.declaresMethod("void setContentView(int)")) {
                return false;
            }
            curClass = curClass.hasSuperclass() ? curClass.getSuperclass() : null;
        }
        return false;
    }

    /**
     * Analyzes the given class to find callback methods
     * @param sootClass The class to analyze
     * @param lifecycleElement The lifecycle element (activity, service, etc.)
     * to which the callback methods belong
     */
    private void analyzeClass(SootClass sootClass, SootClass lifecycleElement) {
        // Do not analyze system classes
        if (sootClass.getName().startsWith("android.")
                || sootClass.getName().startsWith("java.")) {
            return;
        }

        // Check for callback handlers implemented via interfaces
        analyzeClassInterfaceCallbacks(sootClass, sootClass, lifecycleElement);
    }

    protected void analyzeMethodOverrideCallbacks(SootClass sootClass) {
        if (!sootClass.isConcrete()) {
            return;
        }
        if (sootClass.isInterface()) {
            return;
        }

        // Do not start the search in system classes
        if (config.getIgnoreFlowsInSystemPackages()
                && SystemClassHandler.isClassInSystemPackage(sootClass.getName())) {
            return;
        }

        // There are also some classes that implement interesting callback methods.
        // We model this as follows: Whenever the user overwrites a method in an
        // Android OS class, we treat it as a potential callback.
        Set<String> systemMethods = new HashSet<String>(10000);
        for (SootClass parentClass
                : Scene.v().getActiveHierarchy().getSuperclassesOf(sootClass)) {
            if (SystemClassHandler.isClassInSystemPackage(parentClass.getName())) {
                for (SootMethod sm : parentClass.getMethods()) {
                    if (!sm.isConstructor()) {
                        systemMethods.add(sm.getSubSignature());
                    }
                }
            }
        }

        // Iterate over all user-implemented methods. If they are inherited
        // from a system class, they are callback candidates.
        for (SootClass parentClass
                : Scene.v().getActiveHierarchy().getSubclassesOfIncluding(sootClass)) {
            if (parentClass.getName().startsWith("android.")) {
                continue;
            }
            for (SootMethod method : parentClass.getMethods()) {
                if (!systemMethods.contains(method.getSubSignature())) {
                    continue;
                }

                // This is a real callback method
                checkAndAddMethod(method, sootClass);
            }
        }
    }

    private SootMethod getMethodFromHierarchyEx(SootClass c, String methodSignature) {
        if (c.declaresMethod(methodSignature)) {
            return c.getMethod(methodSignature);
        }
        if (c.hasSuperclass()) {
            return getMethodFromHierarchyEx(c.getSuperclass(), methodSignature);
        }
        throw new RuntimeException("Could not find method");
    }

    private void analyzeClassInterfaceCallbacks(SootClass baseClass, SootClass sootClass,
            SootClass lifecycleElement) {
        // We cannot create instances of abstract classes anyway, so there is no
        // reason to look for interface implementations
        if (!baseClass.isConcrete()) {
            return;
        }

        // For a first take, we consider all classes in the android.* packages
        // to be part of the operating system
        if (baseClass.getName().startsWith("android.")) {
            return;
        }

        // If we are a class, one of our superclasses might implement an Android
        // interface
        if (sootClass.hasSuperclass()) {
            analyzeClassInterfaceCallbacks(
                    baseClass, sootClass.getSuperclass(), lifecycleElement);
        }

        // Do we implement one of the well-known interfaces?
        for (SootClass i : collectAllInterfaces(sootClass)) {
            if (androidCallbacks.contains(i.getName())) {
                for (SootMethod sm : i.getMethods()) {
                    checkAndAddMethod(getMethodFromHierarchyEx(baseClass,
                            sm.getSubSignature()), lifecycleElement);
                }
            }
        }
    }

    /**
     * Checks whether the given Soot method comes from a system class. If not,
     * it is added to the list of callback methods.
     * @param method The method to check and add
     * @param baseClass The base class (activity, service, etc.) to which this
     * callback method belongs
     * @return True if the method is new, i.e., has not been seen before, otherwise
     * false
     */
    protected boolean checkAndAddMethod(SootMethod method, SootClass baseClass) {
        AndroidMethod am = new AndroidMethod(method);

        // Do not call system methods
        if (am.getClassName().startsWith("android.")
                || am.getClassName().startsWith("java.")) {
            return false;
        }

        // Skip empty methods
        if (method.isConcrete() && isEmpty(method.retrieveActiveBody())) {
            return false;
        }

        String componentName = baseClass == null ? "" : baseClass.getName();
        Set<SootMethodAndClass> methods = this.callbackMethods.get(componentName);
        if (methods == null) {
            methods = new HashSet<SootMethodAndClass>();
            this.callbackMethods.put(componentName, methods);
        }
        return methods.add(am);
    }

    private boolean isEmpty(Body activeBody) {
        for (Unit u : activeBody.getUnits()) {
            if (!(u instanceof IdentityStmt || u instanceof ReturnVoidStmt)) {
                return false;
            }
        }
        return true;
    }

    private Set<SootClass> collectAllInterfaces(SootClass sootClass) {
        Set<SootClass> interfaces = new HashSet<SootClass>(sootClass.getInterfaces());
        for (SootClass i : sootClass.getInterfaces()) {
            interfaces.addAll(collectAllInterfaces(i));
        }
        return interfaces;
    }

    public Map<String, Set<SootMethodAndClass>> getCallbackMethods() {
        return this.callbackMethods;
    }

    public Map<String, Set<Integer>> getLayoutClasses() {
        return this.layoutClasses;
    }

    public Set<String> getDynamicManifestComponents() {
        return this.dynamicManifestComponents;
    }

    public Set<SootClass> getUnresolvableDynamicRegistrations() {
        return this.unresolvableDynamicRegistrations;
    }
}
