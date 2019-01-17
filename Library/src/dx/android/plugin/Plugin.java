package dx.android.plugin;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import dx.android.util.system.FileLock;
import dx.android.util.system.Files;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Plugin {

    static final String SUB_DIR_DEX = "dex";
    static final String SUB_DIR_LIBS = "libs";
    static final String SUB_DIR_PROPS = "props";
    static final String SUB_DIR_IDMAPS = "idmaps";
    static final String FILE_LOCK = "lock";
    
    private static final String TAG = "Plugin";
    private static final String META_PLUGIN = Plugin.class.getPackage().getName();

    private static final List<Plugin> sEmptyPluginList = new ArrayList<Plugin>();

    static enum Status {
        FAILED, 
        IMPORTED, 
        CHECKED, 
        STARTED
    }

    Status mStatus;
    File mApkPath;
    File mCacheDir;
    boolean mSharedCache;
    long mApkTime;
    PluginInfo mInfo;
    String mPackageName;
    List<Plugin> mOverlays = sEmptyPluginList;
    List<Plugin> mOverlayTargets = sEmptyPluginList;
    Context mContext;
    List<Plugin> mDepends = sEmptyPluginList;
    ClassLoader mClassLoader;
    Class<? extends PluginBase> mClass;
    File mConfigFile;
    PluginBase mPlugin;
    
    Plugin(File file, File cacheDir, boolean shared) {
        mInfo = new PluginInfo();
        mApkPath = file.getAbsoluteFile();
        mCacheDir = cacheDir;
        mSharedCache = shared;
        mApkTime = mApkPath.lastModified();
        if (mApkPath.getAbsolutePath().startsWith("/system/")
                && Build.TIME > mApkTime)
            mApkTime = Build.TIME;
    }
    
    static class AppPlugin implements PluginBase {
        @Override
        public int start(Context context) { return 0; }
        @Override
        public void stop() {}
    }
    
    // stand for application
    Plugin(Context context, File cacheDir) {
        this(new File(context.getPackageCodePath()), cacheDir, false);
        mPackageName = context.getPackageName();
        mClass = AppPlugin.class;
        mClassLoader = context.getClassLoader();
        mInfo.mName = "虚拟宿主插件";
    }

    // embeded plugin
    <E extends PluginBase> Plugin(Context context, File cacheDir, 
            Class<E> plugin, String[] depends) {
        this(context, cacheDir);
        mPackageName = plugin.getPackage().getName();
        mClass = plugin;
        mInfo.mName = "虚拟内嵌插件"; 
        mInfo.mDepends = depends;
    }

    boolean impt(Context context) {
        Log.d(TAG, "impt: " + mApkPath);
        mStatus = Status.FAILED;
        // embeded plugin
        if (mClassLoader != null) {
            mContext = context;
            mStatus = Status.IMPORTED;
            mCacheDir = new File(mCacheDir, mPackageName);
            return true;
        }
        mContext = new PluginContext(context, this);
        try {
            Bundle appMeta = null;
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageArchiveInfo(
                    mApkPath.getAbsolutePath(), PackageManager.GET_META_DATA);
            if (null != packageInfo && null != packageInfo.applicationInfo) {
                appMeta = packageInfo.applicationInfo.metaData;
                mPackageName = packageInfo.packageName;
            }
            if (appMeta == null) {
                Log.d(TAG, "impt: " + mPackageName + " no metadata in plugin's application");
                return false;
            }
            int infoRes = appMeta.getInt(META_PLUGIN);
            if (infoRes == 0) {
                Log.d(TAG, "impt: " + mPackageName + " no \"" + META_PLUGIN + "\" in plugin's metadata");
                return true;
            }
            if (!mInfo.impt(mContext.getResources(), infoRes)) {
                Log.d(TAG, "impt: " + mPackageName + " import metadata failed");
                return false;
            }
            mCacheDir = new File(mCacheDir, mPackageName);
            mStatus = Status.IMPORTED;
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "impt", e);
            return false;
        }
    }
    
    void addOverlay(Plugin overlay) {
        if (mOverlays == sEmptyPluginList)
            mOverlays = new ArrayList<Plugin>();
        mOverlays.add(overlay);
        // upgrade to plugin context
        if (!(mContext instanceof PluginContext))
            mContext = new PluginContext(mContext, this);
    }
    
    public boolean isTemplateFrom(Class<? extends PluginBase>... templates) {
        if (mInfo.mTemplates != null) {
            for (Class<? extends PluginBase> template : templates) {
                if (Arrays.asList(mInfo.mTemplates).contains(template.getName()))
                    return true;
            }
        } else if (mClass != null) {
            for (Class<? extends PluginBase> template : templates) {
                if (template.isAssignableFrom(mClass))
                    return true;
            }
        } else {
            Log.d(TAG, "check: " + mPackageName + " no template info");
            return false;
        }
        return false;
    }
    
    boolean check(Class<? extends PluginBase>[] templates) {
        if (mStatus != Status.IMPORTED)
            return false;
        if (isTemplateFrom(templates))
            return true;
        Log.d(TAG, "check: " + mPackageName 
                + " not template from all templates");
        return false;
    }
    
    boolean check(Map<String, Plugin> plugins) {
        if (mStatus == Status.CHECKED)
            return true;
        if (mStatus != Status.IMPORTED)
            return false;
        if (mInfo.mDepends != null) {
            mStatus = Status.FAILED;
            List<Plugin> depends = new ArrayList<Plugin>();
            for (String dep : mInfo.mDepends) {
                boolean weak = false;
                if (dep.startsWith("?")) {
                    weak = true;
                    dep = dep.substring(1);
                }
                Plugin depend = plugins.get(dep);
                if (depend == null) {
                    Log.w(TAG, "check: depend " + dep + " not found");
                    if (weak)
                        continue;
                    return false;
                }
                if ((mInfo.mOverlayTargets == null) != 
                        (depend.mInfo.mOverlayTargets == null)) {
                    if (mInfo.mOverlayTargets == null)
                        Log.w(TAG, "check: depend " + dep + " is overlay");
                    else
                        Log.w(TAG, "check: depend " + dep + " not overlay");
                    return false;
                }
                if (depend.mStatus == Status.FAILED) {
                    Log.w(TAG, "check: depend " + dep + " failed");
                    return false;
                }
                if (depend.mStatus == Status.IMPORTED) {
                    if (!depend.check(plugins)) {
                        Log.w(TAG, "check: depend " + dep + " failed");
                        return false;
                    }
                }
                depends.add(depend);
            }
            mDepends = depends;
        }
        if (mInfo.mOverlayTargets != null) {
            mOverlayTargets = new ArrayList<Plugin>();
            for (String tgt : mInfo.mOverlayTargets) {
                Plugin target = plugins.get(tgt);
                if (target != null) {
                    mOverlayTargets.add(target);
                    target.addOverlay(this);
                }
            }
        }
        mStatus = Status.CHECKED;
        return true;
    }

    private static String[] sFileList = {
        FILE_LOCK, SUB_DIR_LIBS, SUB_DIR_DEX, SUB_DIR_PROPS
    };
    
    @SuppressWarnings("unchecked")
    boolean start(boolean delay) {
        if (mStatus == Status.STARTED)
            return true;
        if (mStatus != Status.CHECKED)
            return false;
        // delay start normal plugins
        if (delay && mClassLoader == null 
                && mOverlayTargets == sEmptyPluginList
                && !isTemplateFrom(PluginNoDelay.class)) {
            return true;
        }
        mStatus = Status.FAILED;
        // clean unused files
        List<String> listKeep = new ArrayList<String>(Arrays.asList(sFileList));
        if (mCacheDir.getParentFile().equals(PluginManager.getInstance().getCacheDir()))
            listKeep.add(SUB_DIR_IDMAPS);
        Files.cleanOthers(mCacheDir, listKeep);
        // start depends & overlays
        for (Plugin dep : mDepends) {
            if (!dep.start(delay)) {
                Log.w(TAG, "start: depend " + dep.mPackageName + " failed");
                return false;
            }
        }
        for (Plugin oly : mOverlays) {
            if (!oly.start(delay)) {
                Log.w(TAG, "start: overlay " + oly.mPackageName + " failed");
                return false;
            }
        }
        try {
            if (mContext instanceof PluginContext) {
                // not use common cache
                File cacheDir = new File(PluginManager.getInstance().getCacheDir(), 
                        mPackageName);
                ((PluginContext) mContext).upgradeResources(cacheDir);
            }
            if (mOverlayTargets != sEmptyPluginList) {
                Files.cleanOthers(mCacheDir);
                mStatus = Status.STARTED;
                return true;
            }
            mCacheDir.mkdirs();
            File lockFile = new File(mCacheDir, FILE_LOCK);
            // not embeded plugin
            if (mClassLoader == null) {
                FileLock lock = FileLock.lock(lockFile);
                mClassLoader = new PluginClassLoader(this);
                lock.release();
                String className = mInfo.mPluginClass;
                if (className == null)
                    className = mPackageName + ".Plugin";
                Class<?> pluginClass = mClassLoader.loadClass(className);
                if (pluginClass == null) {
                    Log.e(TAG, "start: " + mPackageName + " plugin class not found");
                    return false;
                }
                mClass = (Class<? extends PluginBase>) pluginClass;
            }
            mPlugin = mClass.newInstance();
            if (mPlugin == null)
                return false;
            int result = mPlugin.start(mContext);
            if (result != 0) {
                Log.e(TAG, "start: failed with " + result);
                return false;
            }
            mStatus = Status.STARTED;
            return true;
        } catch (Error e) {
            Log.w(TAG, "start", e);
        } catch (Exception e) {
            Log.w(TAG, "start", e);
        }
        return false;
    }

    boolean stop() {
        if (mStatus != Status.STARTED)
            return true;
        Log.d(TAG, "stop: " + mPackageName);
        try {
            mPlugin.stop();
            mPlugin = null;
            mClass = null;
            mClassLoader = null;
            mStatus = Status.IMPORTED;
            return true;
        } catch (Error e) {
            Log.w(TAG, "stop", e);
        } catch (Exception e) {
            Log.w(TAG, "stop", e);
        }
        return false;
    }

    boolean clean() {
        if (mStatus == Status.STARTED)
            return false;
        mDepends = null;
        mOverlays = null;
        mOverlayTargets = null;
        mContext = null;
        return true;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public File getApkPath() {
        return mApkPath;
    }

    public String getName() {
        return mInfo.mName;
    }

    public String getVersion() {
        return mInfo.mVersion;
    }

    public Context getContext() {
        return mContext;
    }

    @Override
    public String toString() {
        return "Plugin-" + mInfo.mName;
    }
    
}
