package dx.android.plugin;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import dx.android.util.system.Assets;
import dx.android.util.system.Files;
import dx.android.util.system.Mount;

public class PluginManager {

    private final static String TAG = "PluginManager";

    private static PluginManager sInstance;

    private static Map<Class<? extends PluginBase>, String[]> sEmbedPlugins = 
            new HashMap<Class<? extends PluginBase>, String[]>();
    
    public static PluginManager getInstance() {
        return sInstance;
    }
    
    public static <E extends PluginBase> void registerPlugins(Class<E> plugin) {
        registerPlugins(plugin, null);
    }
    
    public static <E extends PluginBase> void registerPlugins(Class<E> plugin, String[] depends) {
        if (!sEmbedPlugins.containsKey(plugin))
            sEmbedPlugins.put(plugin, depends);
    }
    
    public synchronized static PluginManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PluginManager(context);
        }
        return sInstance;
    }
    
    private Context mContext;
    private File mPluginCache;
    
    private Plugin mAppPlugin;
    
    private final Map<String, Plugin> mPlugins = new TreeMap<String, Plugin>();

    private PluginComponentCallbacks mComponentCallbacks;

    PluginManager(Context context) {
        Log.d(TAG, "<init> SDK " + Build.VERSION.SDK_INT);
        Log.d(TAG, "<init> ABI " + Arrays.toString(PluginClassLoader.sAbiList));
        mContext = context;
        mComponentCallbacks = new PluginComponentCallbacks();
        if (mContext instanceof Application) {
            ((Application) mContext).registerComponentCallbacks(mComponentCallbacks);
        }
    }
    
    public void importPlugins(File sysCache, File usbCache) {
        Log.d(TAG, "importPlugins");
        File[] dirs = new File[5];
        File appCache = new File(mContext.getCacheDir(), "plugins");
        File extCache = Files.getExternalCacheDir(mContext, "plugins");
        File assCache = new File(appCache, "assets");
        // fix old name error
        Files.rmdirs(new File(mContext.getCacheDir(), "asserts"));
        // dir order
        mPluginCache = appCache;
        int idx = 0;
        dirs[idx++] = appCache;
        dirs[idx++] = extCache;
        dirs[idx++] = assCache;
        if (sysCache != null) {
            File datCache = new File(
                    sysCache.getAbsolutePath().replace("/system/", "/data/"));
            dirs[idx++] = datCache;
            dirs[idx++] = sysCache;
        }
        if (usbCache != null)
            copyFromUsb(usbCache, extCache);
        appCache.mkdirs();
        File lockFile = new File(appCache, Plugin.FILE_LOCK);
        extractAssets(assCache, lockFile);
        mAppPlugin = new Plugin(mContext, mPluginCache) {
            @Override
            boolean check(Class<? extends PluginBase>[] templates) {
                return true;
            }
        };
        mAppPlugin.impt(mContext);
        mPlugins.put(mAppPlugin.mPackageName, mAppPlugin);
        importPlugins(dirs, dirs[3]);
    }

    public void loadPlugins(Class<? extends PluginBase> ...templates) {
        loadPlugins(false, templates);
    }

    public Runnable loadPlugins(boolean delay, Class<? extends PluginBase> ...templates) {
        Log.d(TAG, "loadPlugins");
        final Map<String, Plugin> plugins = new TreeMap<String, Plugin>();
        for (Plugin info : mPlugins.values()) {
            if (info.check(templates)) {
                Log.d(TAG, "loadPlugins: add " + info.mPackageName);
                plugins.put(info.mPackageName, info);
            }
        }
        Iterator<Plugin> iter = plugins.values().iterator();
        while (iter.hasNext()) {
            Plugin info = iter.next();
            Log.d(TAG, "loadPlugins: check " + info .mPackageName);
            if (!info.check(plugins))
                iter.remove();
        }
        for (Plugin info : plugins.values()) {
            Log.d(TAG, "loadPlugins: start " + info.mPackageName);
            info.start(delay);
        }
        if (delay) {
            return new Runnable() {
                @Override
                public void run() {
                    for (Plugin info : plugins.values()) {
                        if (info.mStatus == Plugin.Status.CHECKED) {
                            Log.d(TAG, "loadPlugins: start delayed " + info.mPackageName);
                            info.start(false);
                        }
                    }
                    mComponentCallbacks.loadFinished();
                }
            };
        }
        mComponentCallbacks.loadFinished();
        return null;
    }
    
    public void cleanPlugins() {
        cleanPlugins(false);
    }
    
    // 整理
    public synchronized void cleanPlugins(boolean keep) {
        Iterator<Entry<String, Plugin>> iter = mPlugins.entrySet().iterator();
        Map<String, ClassLoader> loaders = new LinkedHashMap<String, ClassLoader>();
        while (iter.hasNext()) {
            Entry<String, Plugin> e = iter.next();
            Plugin plugin = e.getValue();
            if (plugin.clean()) {
                Log.d(TAG, "cleanup: " + e.getKey());
                if (!keep)
                    iter.remove();
            } else if (plugin.mClassLoader != getClass().getClassLoader()) {
                loaders.put(e.getKey(), plugin.mClassLoader);
            }
        }
    }
    
    public File getCacheDir() {
        return mPluginCache;
    }

    public synchronized Plugin getPlugin(String packageName) {
        return mPlugins.get(packageName);
    }

    public synchronized Plugin getPlugin(Class<?> cls) {
        ClassLoader classLoader = cls.getClassLoader();
        for (Plugin plugin : mPlugins.values()) {
            if (plugin.mClassLoader == classLoader)
                return plugin;
        }
        return null;
    }

    public ClassLoader getClassLoader(String name) {
        Plugin info = getPlugin(name);
        if (info != null) {
            return info.mClassLoader;
        }
        return null;
    }
    
    public Context getContext(Class<?> cls) {
        Plugin info = getPlugin(cls);
        if (info != null) {
            return info.mContext;
        }
        return mContext;
    }
    
    public PluginContext getAppContext() {
        return (mAppPlugin.mContext instanceof PluginContext)
                ? (PluginContext) mAppPlugin.mContext : null;
    }
    
    public TargetResources getAppResources() {
        Resources resc = mAppPlugin.mContext.getResources();
        if (resc instanceof TargetResources) {
            return ((TargetResources) resc);
        }
        return null;
    }
    
    public Collection<Plugin> getPlugins() {
        return mPlugins.values();
    }

    private void importPlugins(File[] dirs, File globalCacheDir) {
        Log.d(TAG, "importPlugins " + Arrays.toString(dirs));
        File cacheDir = mPluginCache;
        final List<String> localKeeps = new ArrayList<String>();
        final List<String> globalKeeps = globalCacheDir == null
                ? null : new ArrayList<String>();
        final List<String> overlayKeeps = new ArrayList<String>();
        List<String> keeps = localKeeps;
        for (File dir : dirs) {
            if (dir == null) continue;
            // start use global cache dir
            if (dir.getParentFile().equals(mPluginCache))
                localKeeps.add(dir.getName());
            if (dir == globalCacheDir) {
                cacheDir = globalCacheDir;
                keeps = globalKeeps;
            }
            Log.d(TAG, "importPlugins: search in " + dir.getAbsolutePath());
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                Plugin p = null;
                if (file.isDirectory()) {
                    File apk = new File(file, file.getName() + ".apk");
                    if (apk.isFile()) {
                        if (dir == cacheDir) // special keep
                            keeps.add(file.getName());
                        p = importPlugin(apk, cacheDir, cacheDir == globalCacheDir);
                    }
                } else if (file.getName().endsWith(".apk")) {
                    p = importPlugin(file, cacheDir, cacheDir == globalCacheDir);
                }
                if (p != null) {
                    keeps.add(p.mPackageName);
                    // overlay always use local cache dir
                    if (p.mInfo.mOverlayTargets != null) {
                        for (String t : p.mInfo.mOverlayTargets) {
                            if (!overlayKeeps.contains(t))
                                overlayKeeps.add(t);
                        }
                    }
                }
            }
        }
        // import embed plugins at last
        for (Entry<Class<? extends PluginBase>, String[]> e : sEmbedPlugins.entrySet()) {
            Log.d(TAG, "importPlugins: embed plugin " + e.getKey());
            Plugin plugin = new Plugin(mContext, mPluginCache, e.getKey(), e.getValue());
            String packageName = plugin.mPackageName;
            localKeeps.add(packageName);
            if (mPlugins.containsKey(packageName)) {
                Log.e(TAG, "importPlugins: embed plugin " + packageName 
                        + ": override by " + mPlugins.get(packageName).mApkPath);
                continue;
            }
            plugin.impt(mContext);
            mPlugins.put(plugin.mPackageName, plugin);
        }
        localKeeps.add(mAppPlugin.mPackageName);
        localKeeps.add(Plugin.FILE_LOCK);
        if (Build.VERSION.SDK_INT >= 26)
            localKeeps.add("oat");
        // overlay always use local cache dir
        for (String t : overlayKeeps) {
            if (!localKeeps.contains(t) && globalKeeps.contains(t))
                localKeeps.add(t);
        }
        // clean unused content in cache dir
        Files.clean(mPluginCache, new FilenameFilter() {
            @Override
            public boolean accept(File arg0, String arg1) {
                return !arg1.endsWith(".apk") && !localKeeps.contains(arg1);
            }
        });
        if (globalCacheDir != null) {
            if (Build.VERSION.SDK_INT >= 26)
                globalKeeps.add("oat");
            Files.clean(globalCacheDir, new FilenameFilter() {
                @Override
                public boolean accept(File arg0, String arg1) {
                    return !arg1.endsWith(".apk") && !globalKeeps.contains(arg1);
                }
            });
        }
    }
    
    private Plugin importPlugin(File file, File cacheDir, boolean shared) {
        Log.d(TAG, "importPlugin: " + file);
        Plugin plugin = new Plugin(file, cacheDir, shared);
        boolean result = plugin.impt(mContext);
        if (!result) {
            return null;
        }
        if (plugin.mPackageName == null) {
            return null;
        }
        String packageName = plugin.mPackageName;
        if (mPlugins.containsKey(packageName)) {
            Log.e(TAG, "importPlugin " + packageName 
                    + ": multiple version, old one is " + mPlugins.get(packageName).mApkPath);
            return plugin;
        }
        // We never try load plugin with same name but different path
        mPlugins.put(packageName, plugin);
        return plugin;
    }

    public Plugin updatePlugin(Plugin old, File file) throws PluginException {
        String name = null;
        Plugin info = new Plugin(file, mPluginCache, false);
        boolean result = info.impt(mContext);
        if (!result) {
            throw new PluginException(info, "import failed");
        }
        if (old == null) {
            name = info.mPackageName;
            old = mPlugins.get(name);
        } else {
            name = old.mPackageName;
            if (!name.equals(info.mPackageName)) {
                Log.w(TAG, "updatePlugin " + name + ": package name changed!");
                throw new PluginException(info, "package name changed");
            }
        }
        if (old != null) {
            /*
            if (old.mStatus == Plugin.Status.IMPORTED) {
                Log.w(TAG, "updatePlugin " + name + ": not start before!");
                throw new PluginException(info, "not start before");
            }
            if (!old.stop()) {
                throw new PluginException(info, "stop old failed");
            }
            mPlugins.remove(name);
            */
            throw new PluginException(info, "not stop old");
        }
        try {
            result = info.check(mPlugins);
            if (!result) {
                throw new PluginException(info, "check failed");
            }
            result = info.start(false);
            if (!result) {
                throw new PluginException(info, "start failed");
            }
            old = info;
            return old;
        } finally {
            if (old != null) {
                mPlugins.put(name, old);
            }
        }
    }
    private void copyFromUsb(File usbCache, File dstPath) {
        Log.d(TAG, "copyFromUsb");
        try {
            dstPath.mkdirs();
            List<File> paths = Mount.getMountPaths(mContext, usbCache);
            for (File dir : paths) {
                if (!dir.isDirectory()) continue;
                Log.d(TAG, "copyFromUsb list directory " + dir.getAbsolutePath());
                for (File file : dir.listFiles()) {
                    if (!file.isFile() || !file.getName().endsWith(".apk"))
                        continue;
                    Log.d(TAG, "copyFromUsb check file " + file.getName());
                    File dst = new File(dstPath, file.getName());
                    if (dst.isFile()) {
                        if (dst.lastModified() < file.lastModified()) {
                            Log.d(TAG, "copyFromUsb update ");
                            dst.delete();
                        } else {
                            Log.d(TAG, "copyFromUsb skip old");
                            continue;
                        }
                    } else {
                        Log.d(TAG, "copyFromUsb new");
                    }
                    Files.copyFile(file, dst);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "copyFromUsb", e);
        }
    }

    private void extractAssets(File dstPath, File lockFile) {
        Assets.extractAll(mContext, "plugins", dstPath, lockFile);
    }
    
    private class PluginComponentCallbacks implements ComponentCallbacks {
        
        private boolean mLoadFinish;
        private Configuration mConfiguration;

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            if (!mLoadFinish) {
                mConfiguration = newConfig;
                return;
            }
            mConfiguration = null;
            for (Plugin plugin : mPlugins.values()) {
                if (plugin.mStatus == Plugin.Status.STARTED)
                    plugin.mContext.getResources().updateConfiguration(newConfig, null);
            }
        }

        public void loadFinished() {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    mLoadFinish = true;
                    if (mConfiguration != null)
                        onConfigurationChanged(mConfiguration);
                }
            });
        }

        @Override
        public void onLowMemory() {
        }
        
    }

}
