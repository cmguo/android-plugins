package dx.android.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dx.android.util.system.FileLock;
import dx.android.util.system.Files;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;
import android.view.ContextThemeWrapper;

public class PluginContext extends ContextThemeWrapper {

    private static final String TAG = "PluginContext";
    
    private Plugin mPlugin;
    private PluginResources mResources;

    PluginContext(Context context, Plugin plugin) {
        super(context, 0);
        mPlugin = plugin;
        if (plugin.mPackageName != null) {
            mResources = new PluginResources(context.getResources(), 
                    plugin.mApkPath, plugin.mPackageName, 0);
        }
    }
    
    void upgradeResources(File cacheDir) {
        File idmapDir = new File(cacheDir, Plugin.SUB_DIR_IDMAPS);
        if (!mPlugin.mOverlays.isEmpty()) {
            File lockFile = new File(cacheDir, Plugin.FILE_LOCK);
            FileLock lock = FileLock.lock(lockFile);
            TargetResources targetResources = 
                    new TargetResources(mResources);
            idmapDir.mkdirs();
            List<String> listKeep = new ArrayList<String>();
            for (Plugin p : mPlugin.mOverlays) {
                targetResources.addOverlay(idmapDir, 
                        (OverlayResources) p.mContext.getResources());
                listKeep.add(p.mPackageName + TargetResources.SUFFIX_IDMAP);
            }
            Files.cleanOthers(idmapDir, listKeep);
            mResources = targetResources;
            targetResources.applyOverlay(this, false);
            lock.release();
        } else if (!mPlugin.mOverlayTargets.isEmpty()) {
            mResources = new OverlayResources(mResources);
            Files.rmdirs(idmapDir);
        } else {
            Files.rmdirs(idmapDir);
        }
    }
    
    public void getOverlayTitles(Map<String, String> titles) {
        for (Plugin o : mPlugin.mOverlays)
            titles.put(o.mPackageName, o.getName());
        for (Plugin dd : mPlugin.mDepends)
            ((PluginContext) dd.mContext).getOverlayTitles(titles);
    }
    
    public void selectOverlays(String... overlays) {
        Log.d(TAG, "selectOverlays " + Arrays.toString(overlays));
        Map<String, Plugin> overlays2 = new TreeMap<String, Plugin>();
        getOverlays(overlays2);
        List<Plugin> overlays3 = new ArrayList<Plugin>(overlays.length);
        for (String o : overlays) {
            Plugin o2 = overlays2.get(o);
            if (o2 != null && !overlays3.contains(o2)) {
                Log.d(TAG, "selectOverlays: add overlay " + o2.getPackageName());
                overlays3.add(o2);
            }
        }
        selectOverlays(overlays3);
    }
    
    public void selectOverlays(List<Plugin> overlays) {
        Map<Plugin, List<Plugin>> targets = new HashMap<Plugin, List<Plugin>>();
        while (!overlays.isEmpty()) {
            Plugin o = overlays.remove(0);
            Log.d(TAG, "selectOverlays: handle overlay " + o.getPackageName());
            for (Plugin t : o.mOverlayTargets) {
                List<Plugin> to = targets.get(t);
                if (to == null) {
                    to = new ArrayList<Plugin>();
                    targets.put(t, to);
                }
                // maybe duplicate o 
                if (!to.contains(o))
                    to.add(o);
            }
            // can't handle cycle depends
            for (Plugin od : o.mDepends) {
                if (!overlays.contains(od)) {
                    Log.d(TAG, "selectOverlays: add depend " + od.getPackageName());
                    overlays.add(od);
                }
            }
        }
        // use overlays as depend targets
        overlays.add(mPlugin);
        while (!overlays.isEmpty()) {
            Plugin t = overlays.remove(0);
            Log.d(TAG, "selectOverlays: handle target " + t.getPackageName());
            List<Plugin> to = targets.get(t);
            PluginContext c = ((PluginContext) t.getContext());
            if (to != null) {
                String[] os = new String[to.size()];
                for (int i = 0; i < to.size(); ++i)
                    os[i] = to.get(i).getPackageName();
                ((TargetResources) c.mResources).selectOverlays(os);
            } else if (c.mResources instanceof TargetResources) {
                ((TargetResources) c.mResources).selectOverlays();
            }
            // can't handle cycle depends
            for (Plugin td : t.mDepends) {
                if (!overlays.contains(td)) {
                    Log.d(TAG, "selectOverlays: add depend " + td.getPackageName());
                    overlays.add(td);
                }
            }
        }
    }
    
    private void getOverlays(Map<String, Plugin> overlays) {
        List<Plugin> depends = new ArrayList<Plugin>();
        depends.add(mPlugin);
        while (!depends.isEmpty()) {
            Plugin d = depends.remove(0);
            for (Plugin o : d.mOverlays) {
                Log.d(TAG, "getOverlays: add overlay " + o.getPackageName());
                overlays.put(o.getPackageName(), o);
            }
            // can't handle cycle depends
            for (Plugin dd : d.mDepends) {
                if (!depends.contains(dd)) {
                    Log.d(TAG, "getOverlays: add depend " + dd.getPackageName());
                    depends.add(dd);
                }
            }
        }
    }
    
    @Override
    public String getPackageCodePath() {
        return mPlugin.mApkPath.toString();
    }

    @Override
    public String getPackageName() {
    	return mPlugin.mPackageName;
    }
    
    @Override
    public ClassLoader getClassLoader() {
        return mPlugin.mClassLoader;
    }

    @Override
    public AssetManager getAssets() {
        return mResources.getAssets();
    }

    @Override
    public Resources getResources() {
        if (mResources == null) {
            mResources = new PluginResources(getBaseContext().getResources(), 
                    mPlugin.mApkPath, mPlugin.mPackageName);
        }
        return mResources;
    }

}
