package dx.android.plugin;

import java.io.File;

import dx.android.util.reflect.ClassWrapper;
import dx.android.util.reflect.ObjectWrapper;

import android.content.res.AssetManager;
import android.content.res.Resources;

public class PluginResources extends Resources {

    protected File mPath;
    protected String mPackageName;
    protected Resources mBase;

    public PluginResources(Resources base, File path, String pkgName, int unused) {
        super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        mPath = path;
        mPackageName = pkgName;
        mBase = base;
    }

    public PluginResources(Resources base, File path, String pkgName) {
        super(createAssetManager(path), base.getDisplayMetrics(), base.getConfiguration());
        mPath = path;
        mPackageName = pkgName;
        mBase = this;
    }
    
    protected PluginResources(PluginResources base) {
        super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        mPath = base.mPath;
        mPackageName = base.mPackageName;
        mBase = base.mBase;
    }
    
    private static AssetManager createAssetManager(File path) {
        AssetManager assets = ClassWrapper.wrap(AssetManager.class).newInstance();
        ObjectWrapper.wrap(assets).invoke("addAssetPath", path.getAbsolutePath());
        return assets;
    }

    private static native boolean create(File target, File overlay, File idmap);
    
    private static native long open(File target, File overlay, File idmap);
    
    private static native int map(long map, int id);

}
