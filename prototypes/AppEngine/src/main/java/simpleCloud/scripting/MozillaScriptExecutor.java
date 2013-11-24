// MozillaScriptExecutor.java
//

package simpleCloud.scripting;

import java.io.*;
import java.util.*;
import org.mozilla.javascript.*;
import simpleCloud.*;
import simpleCloud.scripting.api.*;
import simpleCloud.services.*;

public final class MozillaScriptExecutor implements ScriptExecutor {

    private HashMap<ScriptName, Script> _scripts;

    private ContextFactory _contextFactory;
    private ScriptableObject _sharedGlobal;

    public MozillaScriptExecutor(Application app) {
        _contextFactory = new SandboxContextFactory();
        _sharedGlobal = createGlobalObject(app);

        _scripts = loadScripts();
    }

    private ScriptableObject createGlobalObject(final Application app) {
        return (ScriptableObject)_contextFactory.call(new ContextAction() {
            @Override
            public Object run(Context scriptContext) {
                ScriptableObject global = new TopLevel();

                scriptContext.initStandardObjects(global, true);

                ScriptApplication appObject = new ScriptApplication(app);
                ScriptableObject.putProperty(global, "app", Context.javaToJS(appObject, global));

                global.sealObject();
                return global;
            }
        });
    }

    @Override
    public String executeScript(final ScriptName name) throws ScriptException {
        final Script script = _scripts.get(name);
        if (script == null) {
            throw new ScriptException("The specified script was not found.");
        }

        try {
            return (String)_contextFactory.call(new ContextAction() {
                @Override
                public Object run(Context scriptContext) {
                    Scriptable scope = scriptContext.newObject(_sharedGlobal);
                    scope.setPrototype(_sharedGlobal);
                    scope.setParentScope(null);

                    ScriptableObject.putProperty(scope, "request", name.getName());

                    Object result = script.exec(scriptContext, scope);
                    return Context.toString(result);
                }
            });
        }
        catch (RhinoException e) {
            throw new ScriptException("Unable to execute script.", e);
        }
    }

    private Script loadScript(Context context, String name, String filePath) throws IOException {
        Reader reader = new FileReader(filePath);
        try {
            return context.compileReader(reader, name, 1, null);
        }
        finally {
            reader.close();
        }
    }

    @SuppressWarnings("unchecked")
    private HashMap<ScriptName, Script> loadScripts() {
        return (HashMap<ScriptName, Script>)_contextFactory.call(new ContextAction() {
            @Override
            public Object run(Context scriptContext) {
                HashMap<ScriptName, Script> scripts = new HashMap<ScriptName, Script>();

                File appFolder = new File("app");
                for (File scriptFile : appFolder.listFiles()) {
                    try {
                        String fileName = scriptFile.getName();
                        fileName = fileName.substring(0, fileName.indexOf('.'));

                        ScriptName name = new ScriptName(fileName);
                        Script script = loadScript(scriptContext, fileName, scriptFile.getPath());

                        scripts.put(name, script);
                    }
                    catch (IOException ioe) {
                    }
                }

                return scripts;
            }
        });
    }

    private final class SandboxContextFactory extends ContextFactory {

        @Override
        protected Context makeContext() {
            final Context context = super.makeContext();

            String apiPackage = ScriptApplication.class.getPackage().getName();
            context.setClassShutter(new SandboxClassShutter(apiPackage));

            context.setWrapFactory(new SandboxWrapFactory());

            return context;
        }
    }

    private final class SandboxClassShutter implements ClassShutter {

        private String _allowedPackagePrefix;
        private HashSet<String> _allowedNames;

        public SandboxClassShutter(String allowedPackage) {
            _allowedPackagePrefix = allowedPackage + ".";

            _allowedNames = new HashSet<String>();
            _allowedNames.add(String.class.getName());
        }

        public boolean visibleToScripts(String name) {
            // Only allow access to classes within the allowed package
            return name.startsWith(_allowedPackagePrefix) ||
                   _allowedNames.contains(name);
        }
    }

    public static class SandboxWrapFactory extends WrapFactory {

        @Override
        public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object object, Class<?> objectType) {
            return new SandboxNativeJavaObject(scope, object, objectType);
        }
    }

    @SuppressWarnings("serial")
    public static class SandboxNativeJavaObject extends NativeJavaObject {

        public SandboxNativeJavaObject(Scriptable scope, Object object, Class<?> objectType) {
            super(scope, object, objectType);
        }

        @Override
        public Object get(String name, Scriptable scriptable) {
            if (name.equals("getClass")) {
                // Prevent access to reflection, which would allow script to
                // get to the class, then package, and all other classes.
                return Scriptable.NOT_FOUND;
            }

            return super.get(name, scriptable);
        }
    }
}
