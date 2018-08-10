/*******************************************************************************
 * Some of the code in this class was borrowed from the FlowDroid project
 * (soot-infoflow and soot-infoflow-android).
 *
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 ******************************************************************************/

package tiro.target.entrypoint;

import tiro.*;
import tiro.target.*;

import soot.*;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.callbacks.AbstractCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.DefaultCallbackAnalyzer;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.ARSCFileParser.AbstractResource;
import soot.jimple.infoflow.android.resources.ARSCFileParser.StringResource;
import soot.jimple.infoflow.android.resources.LayoutControl;
import soot.jimple.infoflow.android.resources.LayoutFileParser;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.options.Options;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.Map.Entry;

// This classes uses the entrypoint extraction code from FlowDroid
// (i.e. the soot-infoflow and soot-infoflow-android projects)

// Note: The soot scene will be reset after the entry-point analysis has completed.
// Do not pass soot objects out of this class directly (use class/method names instead).

public class EntryPointAnalysis {
    private final ManifestAnalysis _manifestAnalysis;
    private final ResourceAnalysis _resourceAnalysis;

    private CustomEntryPointCreator _entryPointCreator = null;
    private Set<String> _entryPointClasses = null;
    private Map<String, Set<SootMethodAndClass>> _callbackMethods =
            new HashMap<String, Set<SootMethodAndClass>>(10000);
    private Set<String> _additionalEntryPoints = new HashSet<String>(100);
    private Map<Integer, List<String>> _xmlCallbackMethods =
            new HashMap<Integer, List<String>>();

    public EntryPointAnalysis(ManifestAnalysis manifestAnalysis,
            ResourceAnalysis resourceAnalysis)
            throws Exception {
        _manifestAnalysis = manifestAnalysis;
        _resourceAnalysis = resourceAnalysis;
        _entryPointClasses = _manifestAnalysis.getEntryPointClasses();

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        if (!TIROStaticAnalysis.Config.PrintSootOutput) {
            // Do not want excessive output from FlowDroid so temporariy disable stdout
            System.setOut(new PrintStream(new FileOutputStream("/dev/null")));
            System.setErr(new PrintStream(new FileOutputStream("/dev/null")));
        }

        try {
            calculateEntryPoints();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    public SootMethod getDummyMainMethod() {
        SootMethod entryPoint = _entryPointCreator.createDummyMain();

        // Always update the Scene to reflect the newest set of callback methods
        if (Scene.v().containsClass(entryPoint.getDeclaringClass().getName())) {
            Scene.v().removeClass(entryPoint.getDeclaringClass());
        }
        Scene.v().addClass(entryPoint.getDeclaringClass());

        // Need to set declaring class as an application (not library) class
        entryPoint.getDeclaringClass().setApplicationClass();

        //System.out.println(entryPoint.getActiveBody());
        return entryPoint;
    }

    public Set<MethodOrMethodContext> getEntryPoints() {
        //return _entryPointCreator.getEntryPoints();
        Set<MethodOrMethodContext> entrypoints = _entryPointCreator.getEntryPoints();
        return entrypoints;
    }

    public Map<Integer, List<String>> getXmlCallbackMethods() {
        return _xmlCallbackMethods;
    }

    private void calculateEntryPoints() throws Exception {
        // Parse the resource and layout files
        LayoutFileParser lfp = new LayoutFileParser(_manifestAnalysis.getPackageName(),
                _resourceAnalysis.getResourceParser());

        // Find callback methods
        calculateCallbackMethods(_resourceAnalysis.getResourceParser(), lfp,
                _manifestAnalysis.getEntryPointClasses());

        // Clean up everything we no longer need
        //soot.G.reset();

        generateNewEntryPointCreator();
    }

    private void generateNewEntryPointCreator() {
        _entryPointCreator = new CustomEntryPointCreator(
                new ArrayList<String>(_entryPointClasses), _additionalEntryPoints);

        Map<String, List<String>> callbackMethodSigs = new HashMap<String, List<String>>();
        for (String className : _callbackMethods.keySet()) {
            List<String> methodSigs = new ArrayList<String>();
            callbackMethodSigs.put(className, methodSigs);

            _callbackMethods.get(className).forEach(am -> {
                methodSigs.add(am.getSignature());
            });
        }

        _entryPointCreator.setCallbackFunctions(callbackMethodSigs);
    }

    private void calculateCallbackMethods(ARSCFileParser resParser,
                                          LayoutFileParser lfp,
                                          Set<String> entrypointClasses)
                                         throws IOException {

        AbstractCallbackAnalyzer jimpleClass = null;
        Set<String> callbackClasses = null;
        InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();

        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = false;

            // Create a new entrypoint creator with updated callback methods
            generateNewEntryPointCreator();

            // Create the new iteration of the main method
            TIROStaticAnalysis.initializeSoot();
            Scene.v().setEntryPoints(Collections.singletonList(getDummyMainMethod()));

            if (jimpleClass == null) {
                // Collect the callback interfaces implemented in the app's source code
                jimpleClass = callbackClasses == null
                        ? new DefaultCallbackAnalyzer(config, _entryPointClasses,
                                  "./AndroidCallbacks.txt")
                        : new DefaultCallbackAnalyzer(config, _entryPointClasses,
                                  callbackClasses);
                jimpleClass.collectCallbackMethods();

                // Find the user-defined sources in the layout XML files. This
                // only needs to be done once, but is a Soot phase.
                lfp.parseLayoutFile(TIROStaticAnalysis.Config.ApkFile);
            } else {
                jimpleClass.collectCallbackMethodsIncremental();
            }

            // Run the soot-based operations
            PackManager.v().getPack("wjpp").apply();
            PackManager.v().getPack("cg").apply();
            PackManager.v().getPack("wjtp").apply();

            // Collect the results of the soot-based phases
            for (Entry<String, Set<SootMethodAndClass>> entry
                    : jimpleClass.getCallbackMethods().entrySet()) {
                Set<SootMethodAndClass> curCallbacks = _callbackMethods.get(entry.getKey());
                if (curCallbacks != null) {
                    if (curCallbacks.addAll(entry.getValue())) {
                        hasChanged = true;
                    }
                } else {
                    _callbackMethods.put(entry.getKey(), new HashSet<>(entry.getValue()));
                    hasChanged = true;
                }
            }

            if (_entryPointClasses.addAll(jimpleClass.getDynamicManifestComponents())) {
                hasChanged = true;
            }

            // Specifically handle unresolvable broadcast receiver registrations by
            // heuristically adding all receivers that are inner classes of a registration.
            Set<SootClass> unresolvedRegistrations =
                    jimpleClass.getUnresolvableDynamicRegistrations();
            SootClass receiverClass =
                    Scene.v().getSootClass("android.content.BroadcastReceiver");
            for (SootClass receiverSubclass
                    : Scene.v().getActiveHierarchy().getSubclassesOf(receiverClass)) {
                if (!receiverSubclass.isApplicationClass()
                        || !receiverSubclass.hasOuterClass()) {
                    continue;
                }
                if (unresolvedRegistrations.contains(receiverSubclass.getOuterClass())) {
                    if (_entryPointClasses.add(receiverSubclass.getName())) {
                        Output.debug("Heuristically adding receiver: " + receiverSubclass);
                        hasChanged = true;
                    }
                }
            }

            // Handle view-based callback methods for custom views.
            hasChanged |= calculateViewBasedCallbackMethods();
        }

        // Collect the XML-based callback methods
        collectXmlBasedCallbackMethods(resParser, lfp, jimpleClass);
    }

    /**
     * Collects the XML-based callback methods, e.g., Button.onClick() declared
     * in layout XML files
     * @param resParser The ARSC resource parser
     * @param lfp The layout file parser
     * @param jimpleClass The analysis class that gives us a mapping between
     * layout IDs and components
     */
    private void collectXmlBasedCallbackMethods(ARSCFileParser resParser,
            LayoutFileParser lfp, AbstractCallbackAnalyzer jimpleClass) {
        // Collect the XML-based callback methods
        for (Entry<String, Set<Integer>> lcentry : jimpleClass.getLayoutClasses().entrySet()) {
            final SootClass callbackClass = Scene.v().getSootClass(lcentry.getKey());

            for (Integer classId : lcentry.getValue()) {
                AbstractResource resource = resParser.findResource(classId);
                if (resource instanceof StringResource) {
                    final String layoutFileName = ((StringResource) resource).getValue();

                    // Add the callback methods for the given class
                    Set<String> callbackMethods = lfp.getCallbackMethods().get(layoutFileName);
                    if (callbackMethods != null) {
                        for (String methodName : callbackMethods) {
                            final String subSig = "void " + methodName + "(android.view.View)";

                            // The callback may be declared directly in the
                            // class
                            // or in one of the superclasses
                            SootClass currentClass = callbackClass;
                            while (true) {
                                SootMethod callbackMethod =
                                        currentClass.getMethodUnsafe(subSig);
                                if (callbackMethod != null) {
                                    addCallbackMethod(callbackClass.getName(),
                                            new AndroidMethod(callbackMethod));
                                    _xmlCallbackMethods.computeIfAbsent(classId,
                                            k -> new ArrayList<String>()).add(
                                                callbackMethod.getSignature());
                                    break;
                                }
                                if (!currentClass.hasSuperclass()) {
                                    System.err.println("Callback method " + methodName
                                            + " not found in class "
                                            + callbackClass.getName());
                                    break;
                                }
                                currentClass = currentClass.getSuperclass();
                            }
                        }
                    }

                    // For user-defined views, we need to emulate their
                    // callbacks
                    Set<LayoutControl> controls = lfp.getUserControls().get(layoutFileName);
                    if (controls != null) {
                        for (LayoutControl lc : controls) {
                            registerCallbackMethodsForView(classId, callbackClass, lc);
                        }
                    }
                } else {
                    System.err.println("Unexpected resource type for layout class");
                }
            }
        }

        // Add the callback methods as sources and sinks
        //{
        //  Set<SootMethodAndClass> callbacksPlain = new HashSet<SootMethodAndClass>();
        //  for (Set<SootMethodAndClass> set : _callbackMethods.values()) {
        //      callbacksPlain.addAll(set);
        //    }

        //  System.out.println("Found " + callbacksPlain.size() + " callback methods for "
        //          + this.callbackMethods.size() + " components");
        //}
    }

    /**
     * Registers the callback methods in the given layout control so that they
     * are included in the dummy main method
     * @param classId The class ID with which to associate the layout
     * callbacks
     * @param callbackClass The class with which to associate the layout
     * callbacks
     * @param lc The layout control whose callbacks are to be associated with
     * the given class
     */
    private void registerCallbackMethodsForView(int classId, SootClass callbackClass,
            LayoutControl lc) {
        // Ignore system classes
        if (callbackClass.getName().startsWith("android.")) {
            return;
        }
        if (lc.getViewClass().getName().startsWith("android.")) {
            return;
        }

        // Check whether the current class is actually a view
        {
            SootClass sc = lc.getViewClass();
            boolean isView = false;
            while (sc.hasSuperclass()) {
                if (sc.getName().equals("android.view.View")) {
                    isView = true;
                    break;
                }
                sc = sc.getSuperclass();
            }
            if (!isView) {
                return;
            }
        }

        // There are also some classes that implement interesting callback
        // methods.
        // We model this as follows: Whenever the user overwrites a method in an
        // Android OS class, we treat it as a potential callback.
        SootClass sc = lc.getViewClass();
        Set<String> systemMethods = new HashSet<String>(10000);
        for (SootClass parentClass : Scene.v().getActiveHierarchy().getSuperclassesOf(sc)) {
            if (parentClass.getName().startsWith("android.")) {
                for (SootMethod sm : parentClass.getMethods()) {
                    if (!sm.isConstructor()) {
                        systemMethods.add(sm.getSubSignature());
                    }
                }
            }
        }

        // Scan for methods that overwrite parent class methods
        for (SootMethod sm : sc.getMethods()) {
            if (!sm.isConstructor()) {
                if (systemMethods.contains(sm.getSubSignature())) {
                    // This is a real callback method
                    addCallbackMethod(callbackClass.getName(), new AndroidMethod(sm));
                    _xmlCallbackMethods.computeIfAbsent(classId, k -> new ArrayList<String>())
                            .add(sm.getSignature());
                }
            }
        }
    }

    private void addCallbackMethod(String layoutClass, AndroidMethod callbackMethod) {
        if (!_callbackMethods.containsKey(layoutClass)) {
            _callbackMethods.put(layoutClass, new HashSet<SootMethodAndClass>());
        }

        _callbackMethods.get(layoutClass).add(new AndroidMethod(callbackMethod));
    }

    private boolean calculateViewBasedCallbackMethods() {
        boolean hasChanged = false;

        SootClass viewClass = Scene.v().getSootClass("android.view.View");
        Set<String> viewMethods = new HashSet<String>(50);
        for (SootMethod viewMethod : viewClass.getMethods()) {
            if (!viewMethod.isConstructor() && !viewMethod.getName().equals("<clinit>")) {
                viewMethods.add(viewMethod.getSubSignature());
            }
        }

        // Scan for methods that overwrite parent view class methods
        for (MethodOrMethodContext entryPoint : getEntryPoints()) {
            SootClass entryPointClass = entryPoint.method().getDeclaringClass();
            if (!Scene.v().getActiveHierarchy().isClassSubclassOf(entryPointClass,
                    viewClass)) {
                continue;
            }

            for (SootMethod viewSubclassMethod : entryPointClass.getMethods()) {
                if (viewMethods.contains(viewSubclassMethod.getSubSignature())) {
                    if (!_additionalEntryPoints.contains(viewSubclassMethod.getSignature())) {
                        Output.debug("Adding view callback: " + viewSubclassMethod);
                        _additionalEntryPoints.add(viewSubclassMethod.getSignature());
                        hasChanged = true;
                    }
                }
            }
        }

        return hasChanged;
    }
}
