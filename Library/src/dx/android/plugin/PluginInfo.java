package dx.android.plugin;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

public class PluginInfo {

    private static final String TAG = "PluginInfo";

    String mName;
    String mAuthor;
    String mVersion;
    String mDescription;
    int mThumbnail;
    String mPluginClass;
    String[] mTemplates;
    String[] mDepends;
    String[] mOverlayTargets;

    PluginInfo() {
    }
    
    // embeded plugin
    <E extends PluginBase> PluginInfo(String[] depends) {
        mDepends = depends;
    }
    
    private static final String nsAndroid = "http://schemas.android.com/apk/res/android";
    
    boolean impt(Resources resources, int infoRes) {
        try {
            XmlResourceParser infoXml = resources.getXml(infoRes);
            int event = infoXml.getEventType();//产生第一个事件  
            while (event != XmlResourceParser.END_DOCUMENT) {
                switch (event) {  
                case XmlResourceParser.START_DOCUMENT:
                    break;
                case XmlResourceParser.START_TAG:
                    int resName = infoXml.getAttributeResourceValue(nsAndroid, "name", 0);
                    if (resName > 0)
                        mName = resources.getString(resName);
                    int resAuthor = infoXml.getAttributeResourceValue(nsAndroid, "author", 0);
                    if (resAuthor > 0)
                        mAuthor = resources.getString(resAuthor);
                    int resVersion = infoXml.getAttributeResourceValue(null, "version", 0);
                    if (resVersion > 0)
                        mVersion = resources.getString(resVersion);
                    else
                        mVersion = infoXml.getAttributeValue(null, "version");
                    int resDesc = infoXml.getAttributeResourceValue(nsAndroid, "description", 0);
                    if (resDesc > 0)
                        mDescription = resources.getString(resDesc);
                    mThumbnail = infoXml.getAttributeResourceValue(nsAndroid, "thumbnail", 0);
                    mPluginClass = infoXml.getAttributeValue(null, "class");
                    int resTemplate = infoXml.getAttributeResourceValue(null, "templates", 0);
                    if (resTemplate > 0)
                        mTemplates = resources.getStringArray(resTemplate);
                    int resDepend = infoXml.getAttributeResourceValue(null, "depends", 0);
                    if (resDepend > 0)
                        mDepends = resources.getStringArray(resDepend);
                    int resOverlays = infoXml.getAttributeResourceValue(null, "overlays", 0);
                    if (resOverlays > 0)
                        mOverlayTargets = resources.getStringArray(resOverlays);
                    break;
                }
                event = infoXml.next();
            }
            return mTemplates != null;
        } catch (Exception e) {
            Log.w(TAG, "impt", e);
            return false;
        }
    }

}
