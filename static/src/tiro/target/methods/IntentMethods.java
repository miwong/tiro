package tiro.target.methods;

import tiro.Output;

import soot.SootMethod;
import soot.Value;

import java.util.HashMap;
import java.util.Map;

public class IntentMethods extends Methods {
    public enum IntentTargetType { NONE, CLASS, STRING, COMPONENT_NAME }

    private static class IntentTargetInfo {
        public final IntentTargetType TargetType;
        public final int ParameterIndex;

        public IntentTargetInfo(IntentTargetType type, int index) {
            TargetType = type;
            ParameterIndex = index;
        }
    }

    private static final Map<SootMethod, IntentTargetInfo> _intentTargetInfo =
            new HashMap<SootMethod, IntentTargetInfo>();

    static {
        // Intent
        safeAddMethod(_intentTargetInfo, "<android.content.Intent: void <init>("
                + "android.content.Context,java.lang.Class)>",
                new IntentTargetInfo(IntentTargetType.CLASS, 1));
        safeAddMethod(_intentTargetInfo, "<android.content.Intent: void <init>("
                + "java.lang.String,android.net.Uri,android.content.Context,java.lang.Class)>",
                new IntentTargetInfo(IntentTargetType.CLASS, 3));
        safeAddMethod(_intentTargetInfo, "<android.content.Intent: android.content.Intent "
                + "setClass(android.content.Context,java.lang.Class)>",
                new IntentTargetInfo(IntentTargetType.CLASS, 1));
        safeAddMethod(_intentTargetInfo, "<android.content.Intent: android.content.Intent "
                + "setClassName(java.lang.String,java.lang.String)>",
                new IntentTargetInfo(IntentTargetType.STRING, 1));
        safeAddMethod(_intentTargetInfo, "<android.content.Intent: android.content.Intent "
                + "setClassName(android.content.Context,java.lang.String)>",
                new IntentTargetInfo(IntentTargetType.STRING, 1));
        safeAddMethod(_intentTargetInfo, "<android.content.Intent: android.content.Intent "
                + "setComponent(android.content.ComponentName)>",
                new IntentTargetInfo(IntentTargetType.COMPONENT_NAME, 0));

        // ComponentName
        safeAddMethod(_intentTargetInfo, "<android.content.ComponentName: void <init>("
                + "android.content.Context,java.lang.Class)>",
                new IntentTargetInfo(IntentTargetType.CLASS, 1));
        safeAddMethod(_intentTargetInfo, "<android.content.ComponentName: void <init>("
                + "android.content.Context,java.lang.String)>",
                new IntentTargetInfo(IntentTargetType.STRING, 1));
        safeAddMethod(_intentTargetInfo, "<android.content.ComponentName: void <init>("
                + "java.lang.String,java.lang.String)>",
                new IntentTargetInfo(IntentTargetType.STRING, 1));
    }

    public static boolean isIntentTargetMethod(SootMethod method) {
        return _intentTargetInfo.containsKey(method);
    }

    public static IntentTargetType getIntentTargetType(SootMethod method) {
        if (_intentTargetInfo.containsKey(method)) {
            return _intentTargetInfo.get(method).TargetType;
        }
        return IntentTargetType.NONE;
    }

    public static int getIntentTargetParameterIndex(SootMethod method) {
        if (_intentTargetInfo.containsKey(method)) {
            return _intentTargetInfo.get(method).ParameterIndex;
        }
        return -1;
    }
}
