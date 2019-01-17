package dx.android.plugin;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import android.content.res.Resources;

class OverlayResources extends PluginResources {
    
    static {
        System.loadLibrary("idmap_jni");
    }
    
    private Map<String, Long> mMaps = new TreeMap<String, Long>();

    OverlayResources(Resources base, File path, String pkgName) {
        super(base, path, pkgName);
    }
    
    OverlayResources(PluginResources base) {
        super(base);
    }
    
    void addTarget(TargetResources target, File idmap) {
        long map = open(target.mPath.getAbsolutePath(), mPath.getAbsolutePath(), 
                target.mPackageName, idmap.getAbsolutePath());
        if (map != 0) {
            mMaps.put(target.mPackageName, map);
        }
    }
    
    public int map(String pkgName, int id) {
        Long map = mMaps.get(pkgName);
        return map == null ? 0 : map(map, id);
    }
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        for (long map : mMaps.values())
            close(map);
        mMaps.clear();
    }
    
    private static native long open(String target, String overlay, 
            String pkgName, String idmap);
    
    private static native int map(long map, int id);
    
    private static native void close(long map);
    
}
