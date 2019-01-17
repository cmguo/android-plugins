package dx.android.plugin;

import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import dx.android.plugin.OverlayStyleable.IStyleable;
import dx.android.plugin.OverlayStyleable.StyleableSet;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;

public class OverlayLayoutFactory implements LayoutInflater.Factory {

    private static final String TAG = "OverlayLayoutFactory";
    
    private TargetResources mResources;
    private LayoutInflater mInflater;
    
    private boolean mTrack;
    
    private Map<View, StyleSet<View>> mStyledViews = new WeakHashMap<View, StyleSet<View>>();
    private SparseArray<StyleSet<?>> mIdStyleSets = new SparseArray<StyleSet<?>>();

    OverlayLayoutFactory(TargetResources resources, LayoutInflater inflater, boolean track) {
        mResources = resources;
        mInflater = inflater;
        mTrack = track;
    }
    
    void updateViews() {
        for (Entry<View, StyleSet<View>> view : mStyledViews.entrySet()) {
            view.getValue().apply(view.getKey(), true);
        }
    }

    private static final String[] sClassPrefixList = {
        "android.widget.",
        "android.webkit.", 
        "android.view.", 
        null, 
    };

    private static final String nsAndroid = "http://schemas.android.com/apk/res/android";
    
    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        if (!mTrack && !mResources.hasSelectedOverlays())
            return null;
        if (context != mInflater.getContext())
            return null;
        if (attrs == null)
            return null;
        int prefixIndex = 3;
        if (-1 == name.indexOf('.'))
            prefixIndex = 0;
        View view = null;
        for (int i = prefixIndex; i < sClassPrefixList.length; ++i) {
            try {
                view = mInflater.createView(name, sClassPrefixList[i], attrs);
                break;
            } catch (ClassNotFoundException e) {
                continue;
            } catch (InflateException e) {
                Log.w(TAG, "onCreateView InflateException with " 
                        + name + " in context " + mInflater.getContext(), e.getCause());
                Log.w(TAG, "onCreateView InflateException more cause", 
                        e.getCause().getCause());
                return null;
            } catch (Exception e) {
                Log.w(TAG, "onCreateView " + name, e);
                return null;
            }
        }
        if (view == null)
            return null;
        StyleSet<View> styleSet = createStyleSet(view, attrs);
        if (!styleSet.mStyles.isEmpty()) {
            if (mTrack)
                mStyledViews.put(view, styleSet);
            if (mResources.hasSelectedOverlays())
                styleSet.apply(view, false);
        }
        return view;
    }
    
    @SuppressWarnings("unchecked")
    private <E extends View> StyleSet<E> createStyleSet(E view, AttributeSet attrs) {
        StyleSet<E> styleSet = null;
        int viewId = view.getId();
        if (viewId > 0) {
            styleSet = (StyleSet<E>) mIdStyleSets.get(viewId);
            if (styleSet != null)
                return styleSet;
        }
        styleSet = new StyleSet<E>();
        Class<? extends View> cls = view.getClass();
        while (true) {
            StyleableSet<?> styles = 
                    OverlayStyleable.sOverlayStyleables.get(cls);
            if (styles == null) {
                cls = (Class<View>) cls.getSuperclass();
                continue;
            }
            for (Entry<String, ?> os 
                    : styles.mStyleables.entrySet()) {
                String attr = os.getKey();
                IStyleable<View> style = (IStyleable<View>) os.getValue();
                int id = attrs.getAttributeResourceValue(nsAndroid, attr, 0);
                if (id == 0)
                    continue;
                styleSet.put(attr, style, id);
            }
            if (cls == View.class)
                break;
            cls = (Class<? extends View>) cls.getSuperclass();
        }
        if (viewId > 0) {
            mIdStyleSets.put(viewId, styleSet);
        }
        return styleSet;
    }
    
    private static class Style<E extends View> {
        IStyleable<E> mStyleable;
        int mId;
        static <E extends View> Style<E> make(IStyleable<E> styleable, int id) {
            Style<E> style = new Style<E>();
            style.mStyleable = styleable;
            style.mId = id;
            return style;
        }
        @Override
        public String toString() {
            return Integer.toHexString(mId) + ": " + mStyleable;
        }
    }
    
    class StyleSet<E extends View> {
        
        final Map<String, Style<? super E>> mStyles = 
            new TreeMap<String, Style<? super E>>();
        
        void put(String attr, IStyleable<? super E> styleable, int id) {
            mStyles.put(attr, Style.make(styleable, id));
        }
        
        void apply(E view, boolean self) {
            for (Entry<String, Style<? super E>> style : mStyles.entrySet()) {
                String name = view.getClass().getName();
                String attr = style.getKey();
                IStyleable<? super E> styleable = style.getValue().mStyleable;
                int id = style.getValue().mId;
                Pair<Resources, Integer> o = mResources.map(id, self);
                if (o != null) {
                    Log.v(TAG, name + ": apply " + attr 
                            + " with 0x" + Integer.toHexString(o.second));
                    styleable.apply(view, o.first, o.second);
                }
            }
        }

    }

}
