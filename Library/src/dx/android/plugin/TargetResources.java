package dx.android.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;

public class TargetResources extends PluginResources {

    static final String SUFFIX_IDMAP = ".idmap";

    private static final String TAG = "TargetResources";
    
    private Map<String, OverlayResources> mOverlayResources = 
            new TreeMap<String, OverlayResources>();
    private List<OverlayResources> mSelectedOverlays = new ArrayList<OverlayResources>();
    private Map<OverlayLayoutFactory, Integer> mTrackedFactories = 
            new WeakHashMap<OverlayLayoutFactory, Integer>();

    TargetResources(Resources base, File path, String pkgName) {
        super(base, path, pkgName);
    }
    
    TargetResources(PluginResources base) {
        super(base);
    }
    
    void addOverlay(File cache, OverlayResources overlay) {
        File idmap = new File(cache, overlay.mPackageName + SUFFIX_IDMAP);
        Log.d(TAG, "addOverlay " + idmap);
        overlay.addTarget(this, idmap);
        mOverlayResources.put(overlay.mPackageName, overlay);
    }
    
    public void selectOverlays(String... names) {
        mSelectedOverlays.clear();
        for (String n : names) {
            OverlayResources o = mOverlayResources.get(n);
            if (o != null)
                mSelectedOverlays.add(o);
        }
        for (OverlayLayoutFactory factory : mTrackedFactories.keySet()) {
            factory.updateViews();
        }
    }
    
    public boolean hasSelectedOverlays() {
        return !mSelectedOverlays.isEmpty();
    }

    public void applyOverlay(Context context, boolean track) {
        Log.d(TAG, "applyOverlay " + context);
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        OverlayLayoutFactory factory = new OverlayLayoutFactory(this, layoutInflater, track);
        layoutInflater.setFactory(factory);
        if (track)
            mTrackedFactories.put(factory, 0);
    }

    @Override
    public void updateConfiguration(Configuration config, DisplayMetrics metrics) {
        if (mOverlayResources != null) {
            for (Resources o : mOverlayResources.values()) {
                o.updateConfiguration(config, metrics);
            }
        }
        super.updateConfiguration(config, metrics);
    }
    
    Pair<Resources, Integer> map(int id, boolean self) {
        for (OverlayResources o : mSelectedOverlays) {
            int id1 = o.map(mPackageName, id);
            if (id1 > 0) {
                //Log.d(TAG, "map: " + o + " 0x" + Integer.toHexString(id) 
                //        + " -> 0x" + Integer.toHexString(id1));
                return Pair.create((Resources) o, id1);
            }
        }
        return self ? Pair.create(mBase, id) : null;
    }
    
    /*
     * (non-Javadoc)
     * @see android.content.res.Resources#getValue(int, android.util.TypedValue, boolean)
     * Called by:
     *   getAnimation(int id)
     *   getBoolean(int id)
     *   getColor(int id)
     *   getColorStateList(int id)
     *   getDimension(int id)
     *   getDimensionPixelOffset(int id)
     *   getDimensionPixelSize(int id)
     *   getDrawable(int id, @Nullable Theme theme)
     *   getFraction(int id, int base, int pbase)
     *   getInteger(int id)
     *   getFloat(int id)
     *   getLayout(int id)
     *   openRawResource(int id, TypedValue value)
     */
    /*
    @Override
    public void getValue(int id, TypedValue outValue, boolean resolveRefs)
            throws NotFoundException {
        Pair<OverlayResources, Integer> overlay = map(id);
        if (overlay == null)
            super.getValue(id, outValue, resolveRefs);
        else
            overlay.first.getValue(overlay.second, outValue, resolveRefs);
    }
    */
    
    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        //Log.d(TAG, "getDrawable: id=0x" + Integer.toHexString(id));
        Pair<Resources, Integer> overlay = map(id, true);
        return overlay.first.getDrawable(overlay.second);
    }

    @Override
    public Drawable getDrawable(int id, Theme theme) throws NotFoundException {
        //Log.d(TAG, "getDrawable: id=0x" + Integer.toHexString(id));
        Pair<Resources, Integer> overlay = map(id, true);
        return overlay.first.getDrawable(overlay.second, theme);
    }
    
    @Override
    public Drawable getDrawableForDensity(int id, int density)
            throws NotFoundException {
        //Log.d(TAG, "getDrawableForDensity: id=0x" + Integer.toHexString(id));
        Pair<Resources, Integer> overlay = map(id, true);
        return overlay.first.getDrawableForDensity(overlay.second, density);
    }
    
    @Override
    public Drawable getDrawableForDensity(int id, int density, Theme theme) {
        //Log.d(TAG, "getDrawableForDensity: id=0x" + Integer.toHexString(id));
        Pair<Resources, Integer> overlay = map(id, true);
        return overlay.first.getDrawableForDensity(overlay.second, density, theme);
    }
    
    /*
     * (non-Javadoc)
     * @see android.content.res.Resources#getValueForDensity(int, int, android.util.TypedValue, boolean)
     * Called by:
     *   getDrawableForDensity(int id, int density, @Nullable Theme theme)
     */
    @Override
    public void getValueForDensity(int id, int density, TypedValue outValue,
            boolean resolveRefs) throws NotFoundException {
        Pair<Resources, Integer> overlay = map(id, true);
        overlay.first.getValueForDensity(overlay.second, density, outValue, resolveRefs);
    }
    
    @Override
    public int[] getIntArray(int id) throws NotFoundException {
        Pair<Resources, Integer> overlay = map(id, true);
        return overlay.first.getIntArray(overlay.second);
    }
    
    @Override
    public CharSequence getText(int id) throws NotFoundException {
        Pair<Resources, Integer> overlay = map(id, true);
        return overlay.first.getText(overlay.second);
    }
    
    @Override
    public CharSequence getText(int id, CharSequence def) {
        Pair<Resources, Integer> overlay = map(id, true);
        return overlay.first.getText(overlay.second, def);
    }
    
    @Override
    public CharSequence[] getTextArray(int id) throws NotFoundException {
        Pair<Resources, Integer> overlay = map(id, true);
        return overlay.first.getTextArray(overlay.second);
    }
    
    @Override
    public String[] getStringArray(int id) throws NotFoundException {
        Pair<Resources, Integer> overlay = map(id, true);
        return overlay.first.getStringArray(overlay.second);
    }
    
}
