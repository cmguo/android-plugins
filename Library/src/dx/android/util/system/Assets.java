package dx.android.util.system;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

public class Assets {

    private static final String TAG = "Assets";

    public static void extract(Context context, String file, File dst, File lockFile) {
        FileLock lock = FileLock.lock(lockFile);
        extract(context, file, dst);
        lock.release();
    }
    
    public static void extract(Context context, String src, File dst) {
        long apkTime = getApkTime(context);
        extract(context.getAssets(), apkTime, src, dst);
    }

    public static void extract(AssetManager manager, long apkTime, 
            String src, File dst) {
        if (dst.lastModified() == apkTime)
            return;
        Log.d(TAG, "extract " + src + " -> " + dst);
        try {
            InputStream in = manager.open(src);
            File tmp = File.createTempFile("xxx", "yyy", dst.getParentFile());
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buffer = new byte[2048];
            for (int n = in.read(buffer); n >= 0; n = in.read(buffer))
                out.write(buffer, 0, n);
            out.getFD().sync();
            out.close();
            dst.delete();
            tmp.renameTo(dst);
            dst.setLastModified(apkTime);
        } catch (Exception e) {
            Log.w(TAG, "extract", e);
        }
    }
    
    public static File[] extractAll(Context context, String astPath, File dstPath, 
            File lockFile) {
        File[] files = null;
        FileLock lock = FileLock.lock(lockFile);
        long apkTime = getApkTime(context);
        try {
            AssetManager manager = context.getAssets();
            // AssetManager.list wan't throw FileNotFoundException
            //   but return empty array
            // Zip archive drop empty directories, so empty means none
            String[] list = manager.list(astPath);
            if (list.length == 0) {
                Files.rmdirs(dstPath);
            } else {
                dstPath.mkdirs();
                Files.cleanOthers(dstPath, list);
            }
            files = new File[list.length];
            int i = 0;
            for (String name : list) {
                String src = astPath + "/" + name;
                File dst = new File(dstPath, name);
                files[i++] = dst;
                Assets.extract(manager, apkTime, src, dst);
            }
        } catch (Exception e) {
            Log.w(TAG, "extractAll", e);
        }
        lock.release();
        return files;
    }

    private static long getApkTime(Context context) {
        String apkPath = context.getPackageCodePath();
        long apkTime = new File(apkPath).lastModified();
        if (apkPath.startsWith("/system/")
                && Build.TIME > apkTime)
            apkTime = Build.TIME;
        return apkTime;
    }
    
}
