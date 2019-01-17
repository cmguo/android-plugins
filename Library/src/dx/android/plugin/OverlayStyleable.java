package dx.android.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import android.content.res.Resources;
import android.util.TypedValue;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

public class OverlayStyleable {

    public interface IStyleable<E extends View> {
        void apply(E view, Resources resc, int id);
    }
    
    static class StyleableSet<E extends View> {
        final Map<String, IStyleable<E>> mStyleables = 
            new TreeMap<String, IStyleable<E>>();
    }
    
    static final Map<Class<? extends View>, StyleableSet<? extends View>> sOverlayStyleables = 
            new HashMap<Class<? extends View>, StyleableSet<? extends View>>();
    
    static {
        
        /*
         * View
         */
        StyleableSet<View> view = new StyleableSet<View>(); 
        view.mStyleables.put("background", new IStyleable<View>() {
            @Override
            public void apply(View view, Resources resc, int id) {
                view.setBackground(resc.getDrawable(id));
            }
        });
        view.mStyleables.put("alpha", new IStyleable<View>() {
            @Override
            public void apply(View view, Resources resc, int id) {
                view.setAlpha(getFloat(resc, id));
            }
        });
        sOverlayStyleables.put(View.class, view);
        /*
         * TextView
         */
        StyleableSet<TextView> textView = new StyleableSet<TextView>(); 
        textView.mStyleables.put("textSize", new IStyleable<TextView>() {
            @Override
            public void apply(TextView view, Resources resc, int id) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, resc.getDimensionPixelSize(id));
            }
        });
        textView.mStyleables.put("textColor", new IStyleable<TextView>() {
            @Override
            public void apply(TextView view, Resources resc, int id) {
                view.setTextColor(resc.getColorStateList(id));
            }
        });
        textView.mStyleables.put("text", new IStyleable<TextView>() {
            @Override
            public void apply(TextView view, Resources resc, int id) {
                view.setText(resc.getText(id));
            }
        });
        sOverlayStyleables.put(TextView.class, textView);
        /*
         * ListView
         */
        StyleableSet<ListView> lisView = new StyleableSet<ListView>(); 
        lisView.mStyleables.put("listSelector", new IStyleable<ListView>() {
            @Override
            public void apply(ListView view, Resources resc, int id) {
                view.setSelector(resc.getDrawable(id));
            }
        });
        lisView.mStyleables.put("divider", new IStyleable<ListView>() {
            @Override
            public void apply(ListView view, Resources resc, int id) {
                view.setDivider(resc.getDrawable(id));
            }
        });
        lisView.mStyleables.put("dividerHeight", new IStyleable<ListView>() {
            @Override
            public void apply(ListView view, Resources resc, int id) {
                view.setDividerHeight(resc.getDimensionPixelSize(id));
            }
        });
        sOverlayStyleables.put(ListView.class, lisView);
    };
    
    private static final TypedValue sTypeValue = new TypedValue();
    
    private static float getFloat(Resources resc, int id) {
        TypedValue value = sTypeValue;
        resc.getValue(id, value , true);
        if (value.type == TypedValue.TYPE_FLOAT) {
            return sTypeValue.getFloat();
        } else if (value.type >= TypedValue.TYPE_FIRST_INT 
                && value.type <= TypedValue.TYPE_LAST_INT) {
            return sTypeValue.data;
        }
        throw new IllegalStateException();
    }
}
