// Application.java
//

package simpleCloud;

import java.lang.reflect.*;
import java.util.*;
import javax.servlet.*;
import simpleCloud.scripting.*;
import simpleCloud.services.*;
import simpleCloud.storage.*;

public final class Application implements ServletContextListener, ApplicationFeature, ScriptFeature {

    private static final String FeatureName = "code";

    private ArrayList<ApplicationFeature> _features;
    private ScriptExecutor _scriptExecutor;

    public Application() {
        _features = createFeatures();
        _features.add(this);

        _scriptExecutor = createScriptExecutor();
    }

    @Override
    public void contextDestroyed(ServletContextEvent e) {
    }

    @Override
    public void contextInitialized(ServletContextEvent e) {
        ServletContext context = e.getServletContext();
        context.setAttribute(Application.class.getName(), this);
    }

    @SuppressWarnings("unchecked")
    private ArrayList<ApplicationFeature> createFeatures() {
        ArrayList<ApplicationFeature> features = new ArrayList<ApplicationFeature>();

        Properties props = System.getProperties();
        for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
            String name = (String)e.nextElement();
            if (!name.startsWith("simpleCloud.feature")) {
                continue;
            }

            String featureClassName = props.getProperty(name);
            try {
                Class<ApplicationFeature> featureClass = (Class<ApplicationFeature>)Class.forName(featureClassName);
                Constructor<ApplicationFeature> featureCtor = featureClass.getConstructor(Application.class);

                features.add(featureCtor.newInstance(this));
            }
            catch (Exception ex) {
                // TODO: Logging, rather than silently ignoring
            }
        }

        return features;
    }

    private ScriptExecutor createScriptExecutor() {
        ScriptLoader loader = new LocalScriptLoader(this);
        return new MozillaScriptExecutor(this, loader);
    }

    public List<ApplicationFeature> getFeatures() {
        return _features;
    }

    @Override
    public String getName() {
        return Application.FeatureName;
    }

    public ScriptExecutor getScriptExecutor() {
        return _scriptExecutor;
    }

    @Override
    public boolean usesGroupedScriptFiles() {
        return false;
    }
}
