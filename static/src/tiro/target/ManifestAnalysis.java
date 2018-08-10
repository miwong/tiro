package tiro.target;

import soot.jimple.infoflow.android.axml.AXmlAttribute;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

import org.xmlpull.v1.XmlPullParserException;
import pxb.android.axml.AxmlVisitor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Note: The soot scene will be reset after the entry-point analysis has completed.
// Do not pass soot objects out of this class directly (use class/method names instead).

public class ManifestAnalysis extends ProcessManifest {
    private final Map<String, Set<String>> _receiverActions =
            new HashMap<String, Set<String>>();

    public ManifestAnalysis(String apkPath) throws IOException, XmlPullParserException {
        super(apkPath);
    }

    public String getMainActivity() {
        for (AXmlNode activity : this.activities) {
            if (isMainActivity(activity)) {
                //String activityName =
                //        resolveClassName((String)activity.getAttribute("name").getValue());
                String activityName = resolveClassName(getComponentName(activity));
                return activityName;
            }
        }

        // Also check activity aliases that may have added the MAIN intent filter
        for (AXmlNode alias : this.axml.getNodesWithTag("activity-alias")) {
            if (isMainActivity(alias)) {
                String activityName = resolveClassName(
                        (String)alias.getAttribute("targetActivity").getValue());
                return activityName;
            }
        }

        return "";
    }

    public Set<String> getReceiverActions(String receiverClassName) {
        // Check cached receiver actions.
        if (_receiverActions.containsKey(receiverClassName)) {
            return _receiverActions.get(receiverClassName);
        }

        // Compute receiver actions from manifest and store into cache.
        Set<String> actions = new HashSet<String>();

        for (AXmlNode receiver : this.receivers) {
            if (!receiverClassName.equals(getComponentName(receiver))) {
                continue;
            }

            for (AXmlNode intentFilter : receiver.getChildrenWithTag("intent-filter")) {
                for (AXmlNode intentFilterAction
                        : intentFilter.getChildrenWithTag("action")) {
                    if (intentFilterAction.hasAttribute("name")) {
                        String actionName =
                                (String)intentFilterAction.getAttribute("name").getValue();
                        actions.add(actionName);
                    }
                }
            }
        }

        _receiverActions.put(receiverClassName, actions);
        return actions;
    }

    private boolean isMainActivity(AXmlNode activity) {
        for (AXmlNode intentFilter : activity.getChildrenWithTag("intent-filter")) {
            boolean hasMainAction = false;
            boolean hasLauncherCategory = false;

            for (AXmlNode intentFilterAction : intentFilter.getChildrenWithTag("action")) {
                if (intentFilterAction.hasAttribute("name")) {
                    String name = (String)intentFilterAction.getAttribute("name").getValue();
                    if (name.equals("android.intent.action.MAIN")) {
                        hasMainAction = true;
                        break;
                        //return true;
                    }
                }
            }

            for (AXmlNode intentFilterAction : intentFilter.getChildrenWithTag("category")) {
                if (intentFilterAction.hasAttribute("name")) {
                    String name = (String)intentFilterAction.getAttribute("name").getValue();
                    if (name.equals("android.intent.category.LAUNCHER")) {
                        hasLauncherCategory = true;
                        break;
                    }
                }
            }

            if (hasMainAction && hasLauncherCategory) {
                return true;
            }
        }

        return false;
    }

    private String getComponentName(AXmlNode component) {
        if (component.hasAttribute("name")) {
            return (String)component.getAttribute("name").getValue();
        }

        // This component does not have a name (likely obfuscated), so check all attributes.
        for (AXmlAttribute<?> attribute : component.getAttributes().values()) {
            if ((attribute.getName() == null || attribute.getName().isEmpty())
                    && attribute.getType() == AxmlVisitor.TYPE_STRING) {
                String name = (String)attribute.getValue();
                if (isValidComponentName(name)) {
                    return name;
                }
            }
        }

        return "";
    }

    private String resolveClassName(String className) {
        String packageName = getPackageName();
        if (className.startsWith(".")) {
            return packageName + className;
        } else if (className.substring(0, 1).equals(
                    className.substring(0, 1).toUpperCase())) {
            return packageName + "." + className;
        } else {
            return className;
        }
    }

    private boolean isValidComponentName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        if (name.equals("true") || name.equals("false")) {
            return false;
        }
        if (Character.isDigit(name.charAt(0))) {
            return false;
        }
        if (name.startsWith(".")) {
            return true;
        }
        // Be conservative
        return false;
    }
}
