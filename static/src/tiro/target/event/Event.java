package tiro.target.event;

import tiro.Output;
import tiro.target.constraint.Predicate;
import tiro.target.constraint.Z3ConstraintGenerator;
import tiro.target.dependency.Dependence;

import soot.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Event {
    protected static final SootClass _activityClass =
            Scene.v().getSootClass("android.app.Activity");
    protected static final SootClass _serviceClass =
            Scene.v().getSootClass("android.app.Service");
    protected static final SootClass _receiverClass =
            Scene.v().getSootClass("android.content.BroadcastReceiver");
    protected static final SootClass _viewClass = Scene.v().getSootClass("android.view.View");

    public enum Type {
        NONE,
        ACTIVITY,
        SERVICE,
        RECEIVER,
        UI
    }

    protected final Event.Type _type;
    protected final CallPath _path;
    protected Predicate _constraints;
    protected final List<Dependence> _dependencies = new ArrayList<Dependence>();

    public Event(CallPath path, Predicate constraints) {
        _path = path;
        _constraints = constraints;
        _type = computeEventType(path.getEntryMethod());
    }

    public CallPath getPath() {
        return _path;
    }

    public Predicate getConstraints() {
        return _constraints;
    }

    public Event.Type getType() {
        return _type;
    }

    public String getTypeString() {
        switch (_type) {
            case ACTIVITY: return "activity";
            case SERVICE:  return "service";
            // TODO: implement intent injection
            case RECEIVER: return "sms";
            case UI:       return "ui";
            default:       return "";
        }
    }

    public void updateConstraints(Predicate constraints) {
        _constraints = constraints;
    }

    public List<Dependence> getDependencies() {
        return _dependencies;
    }

    public void addDependence(Dependence dependence) {
        _dependencies.add(dependence);
    }

    public void addDependencies(Collection<? extends Dependence> dependencies) {
        _dependencies.addAll(dependencies);
    }

    public JsonObject toJson(String eventChainDirectory, int eventId) {
        JsonObject eventJson = new JsonObject();

        eventJson.addProperty("Type", getTypeString());
        eventJson.addProperty("Component",
                _path.getEntryMethod().getDeclaringClass().getName());

        JsonArray pathJson = new JsonArray();
        _path.getNodes().forEach(n -> { pathJson.add(n.method().getSignature()); });
        pathJson.add(_path.getTargetUnit().toString());
        eventJson.add("Path", pathJson);

        if (_constraints != null) {
            Z3ConstraintGenerator z3Generator = new Z3ConstraintGenerator(_constraints);

            String constraintFileName = "constraints" + eventId + ".py";
            String constraintFilePath = eventChainDirectory + "/" + constraintFileName;
            writeConstraintFile(constraintFilePath, z3Generator.getZ3ConstraintCode());
            eventJson.addProperty("ConstraintFile", constraintFileName);

            eventJson.add("Variables", z3Generator.getZ3VariableMapJson());
        }

        // TODO
        // UI events
        //public String UIType = null;
        //public String Activities = null;
        //public String Listener = null;
        //public String ListenerMethod = null;
        //public String InDialog = null;

        return eventJson;
    }

    protected void writeConstraintFile(String constraintFilePath, String constraintsCode) {
        try {
            PrintWriter writer = new PrintWriter(constraintFilePath, "UTF-8");
            writer.println("# Start: " + _path.getEntryMethod().getSignature());
            writer.println("# Target: " + _path.getTargetUnit().toString());
            writer.println("");
            writer.print(constraintsCode);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Event.Type computeEventType(SootMethod entryMethod) {
        Hierarchy cha = Scene.v().getActiveHierarchy();
        SootClass entryClass = entryMethod.getDeclaringClass();

        // TODO: check if entry method is actually an activity/service entry-point.
        if (entryMethod.getName().equals("onClick")) {
            return Event.Type.UI;

        } else if (!entryClass.isInterface()) {
            if (cha.isClassSubclassOf(entryClass, _activityClass)) {
                return Event.Type.ACTIVITY;
            } else if (cha.isClassSubclassOf(entryClass, _serviceClass)) {
                return Event.Type.SERVICE;
            } else if (cha.isClassSubclassOf(entryClass, _receiverClass)) {
                // TODO: implement intent injection
                return Event.Type.RECEIVER;
            } else {
                if (cha.isClassSubclassOf(entryClass, _viewClass)) {
                    return Event.Type.UI;
                }

                // Also check if this is an implementor of a view-related inner interface.
                for (SootClass interfaceClass : entryClass.getInterfaces()) {
                    String interfaceName = interfaceClass.getName();
                    if (interfaceName.contains("$")) {
                        String outerClassName =
                                interfaceName.substring(0, interfaceName.indexOf('$'));
                        SootClass outerClass = Scene.v().getSootClassUnsafe(outerClassName);
                        if (outerClass != null && !outerClass.isInterface()
                                && cha.isClassSubclassOfIncluding(outerClass, _viewClass)) {

                            return Event.Type.UI;
                        }
                    }
                }
            }
        }

        return Event.Type.NONE;
    }
}
