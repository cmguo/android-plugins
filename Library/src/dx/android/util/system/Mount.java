package dx.android.util.system;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

import dx.android.util.reflect.ObjectWrapper;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;

public class Mount {
    
    public static List<File> getMountPaths(Context context, final File withFile) {
        final String fileString = withFile.toString();
        List<File> paths = getMountPaths(context, new FileFilter() {
            @Override
            public boolean accept(File path) {
                File file = new File(path, fileString);
                return file.exists();
            }
        });
        for (int i = 0; i < paths.size(); ++i) {
            paths.set(i, new File(paths.get(i), fileString));
        }
        return paths;
    }
    
    public static List<File> getMountPaths(Context context) {
        return getMountPaths(context, (FileFilter) null);
    }
    
    public static List<File> getMountPaths(Context context, FileFilter filter) {
        List<File> paths = new ArrayList<File>();
        ObjectWrapper<StorageManager> sm = ObjectWrapper.wrap(
                (StorageManager) context.getSystemService(Context.STORAGE_SERVICE));
        for (String path : (String[]) sm.invoke("getVolumePaths")) {
            if (Environment.MEDIA_MOUNTED.equalsIgnoreCase(
                    (String) sm.invoke("getVolumeState", path))) {
                File file = new File(path);
                if (filter == null || filter.accept(file))
                    paths.add(file);
            }
        }
        return paths;
    }
    
    public static String getVolumeState(Context context, File path) {
        ObjectWrapper<StorageManager> sm = ObjectWrapper.wrap(
                (StorageManager) context.getSystemService(Context.STORAGE_SERVICE));
        return sm.invoke("getVolumeState", path.getAbsolutePath());
    }
    
    public static String getVolumeUserLabel(Context context, File path) {
        return getVolume(context, path, "getUserLabel");
    }
    
    public static boolean isVolumeRemovable(Context context, File path) {
        return getVolume(context, path, "isRemovable");
    }
    
    public static boolean isVolumeEmulated(Context context, File path) {
        return getVolume(context, path, "isEmulated");
    }
    
    @SuppressWarnings("unchecked")
    private static <E> E getVolume(Context context, File path, String method) {
        ObjectWrapper<StorageManager> sm = ObjectWrapper.wrap(
                (StorageManager) context.getSystemService(Context.STORAGE_SERVICE));
        Object[] volumes = sm.invoke("getVolumeList");
        for (Object vol : volumes) {
            ObjectWrapper<?> v = ObjectWrapper.wrap(vol);
            if (path.equals(v.invoke("getPathFile"))) {
                return (E) v.invoke(method);
            }
        }
        return null;
    }
    
}
