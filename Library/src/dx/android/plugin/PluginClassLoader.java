package dx.android.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import dx.android.util.system.FileLock;
import dx.android.util.system.Files;

import dalvik.system.DexClassLoader;

public class PluginClassLoader extends DexClassLoader {

    static final String[] sAbiList = getAbiList();
    
    private final static String TAG = "PluginClassLoader";

    private static final String ZIP_SEPARATOR = "!/";
    
    Plugin mPlugin;
    File mLockPath;
    File mDexPath;
    NativeLibraryInfo mLibInfo;

    @SuppressWarnings("deprecation")
    private static String[] getAbiList() {
        String abi = Build.CPU_ABI;
        String abi2 = Build.CPU_ABI2;
        if (!TextUtils.isEmpty(abi))
            if (!TextUtils.isEmpty(abi2))
                return new String[] { abi, abi2 };
            else
                return new String[] { abi };
        else
            return new String[] { };
    }
    
    public PluginClassLoader(Plugin plugin) {
        this(plugin, getDexPath(plugin), getNativeLibraryInfo(plugin));
    }
    
    private PluginClassLoader(Plugin plugin, File dexPath, NativeLibraryInfo info) {
        super(plugin.mApkPath.getAbsolutePath(), 
                dexPath.getAbsolutePath(), 
                info.mLibPath, 
                plugin.getClass().getClassLoader());
        Log.d(TAG, "<init> " + this);
        mPlugin = plugin;
        mLockPath = new File(plugin.mCacheDir, Plugin.FILE_LOCK);
        mDexPath = dexPath;
        mLibInfo = info;
    }

    @Override
    protected Class<?> findClass(String className)
            throws ClassNotFoundException {
        try {
            return super.findClass(className);
        } catch (ClassNotFoundException e) {
            for (Plugin plugin : mPlugin.mDepends) {
                if (className.startsWith(plugin.mPackageName)) {
                    return plugin.mClassLoader.loadClass(className);
                }
            }
            throw e;
        }
    }
    
    @Override
    public String findLibrary(String name) {
        String result = super.findLibrary(name);
        FileLock lock = FileLock.lock(mLockPath);
        if (result == null || (!mLibInfo.mEmbeded 
                && lastModified(new File(result)) != mPlugin.mApkTime)) {
            if (!mLibInfo.mExtractLibs) {
                if (result != null) {
                    Log.w(TAG, "findLibrary found unmatch " + name + " -> " + result);
                }
                lock.release();
                return null;
            }
            Log.d(TAG, "findLibrary update " + name + " -> " + result);
            // extract all so, for indirect load
            extractLibs();
            result = super.findLibrary(name);
        }
        lock.release();
        Log.d(TAG, "findLibrary " + name + " -> " + result);
        return result;
    }
    
    // @Override
    public String getLdLibraryPath() {
        // prevent load direct which may load old library without update
        if (!mLibInfo.mExtractLibs)
            return mLibInfo.mLibPath;
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        for (StackTraceElement stack : stackTrace) {
            if (Runtime.class.getName().equals(stack.getClassName())
                    && "load".equals(stack.getMethodName())) {
                Log.d(TAG, "getLdLibraryPath from System.load");
                return null;
            }
        }
        return mLibInfo.mLibPath;
    }
    
    private void extractLibs() {
        Log.d(TAG, "extractLibs " + mLibInfo.mAbi);
        try {
            ZipFile zip = new ZipFile(mPlugin.mApkPath);
            String path = "lib/" + mLibInfo.mAbi + "/";
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().startsWith(path))
                    continue;
                String name = entry.getName().substring(path.length());
                extractLib(zip, entry, new File(mLibInfo.mLibPath, name));
            }
            zip.close();
        } catch (IOException e) {
            Log.w(TAG, "extractLibs", e);
        }
    }

    private void extractLib(ZipFile zip, ZipEntry entry, File dest) throws IOException {
        Log.d(TAG, "extractLib " + entry.getName());
        InputStream in = zip.getInputStream(entry);
        File file = File.createTempFile("xxx", "yyy", dest.getParentFile());
        FileOutputStream out = new FileOutputStream(file);
        byte buffer[] = new byte[4096];
        int nRead = 0;
        while ((nRead = in.read(buffer)) > 0) {
            out.write(buffer, 0, nRead);
        }
        out.getFD().sync();
        out.close();
        in.close();
        dest.delete();
        file.renameTo(dest);
        dest.setLastModified(mPlugin.mApkTime);
    }

    private static long lastModified(File file) {
        long time = file.lastModified();
        if (file.getAbsolutePath().startsWith("/system/")
                && Build.TIME > time) {
            time = Build.TIME;
        }
        return time;
    }

    private static class NativeLibraryInfo {
        List<String> mAbiList = new ArrayList<String>();
        boolean mZipAligned;
        String mAbi;
        boolean mExtractLibs;
        boolean mEmbeded;
        String mLibPath;
    }
    
    private static final String[] oatArchs = new String[] {
        "arm64", "arm"
    };
    
    private static File getDexPath(Plugin plugin) {
        // optimizedDirectory    String: 
        //   this parameter is deprecated and has no effect since API level 26.
        if (Build.VERSION.SDK_INT >= 26)
            return plugin.mCacheDir;
        File dexPath = new File(plugin.mCacheDir, Plugin.SUB_DIR_DEX);
        if (plugin.mSharedCache)
            dexPath = new File(dexPath, abiToArch(sAbiList[0]));
        dexPath.mkdirs();
        return dexPath;
    }
    
    private static NativeLibraryInfo getNativeLibraryInfo(Plugin plugin) {
        File apkPath = plugin.mApkPath;
        File cacheDir = plugin.mCacheDir;
        NativeLibraryInfo info = new NativeLibraryInfo();
        ZipFile zip = null;
        List<String> libs = new ArrayList<String>();
        try {
            zip = new ZipFile(apkPath);
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().startsWith("lib/"))
                    continue;
                libs.add(entry.getName().substring(4));
                String[] paths = entry.getName().split("/");
                if (info.mAbiList.isEmpty())
                    info.mZipAligned = entry.getMethod() == ZipEntry.STORED;
                if (!info.mAbiList.contains(paths[1]))
                    info.mAbiList.add(paths[1]);
            }
        } catch (IOException e) {
            Log.w(TAG, "getAbi", e);
        } finally {
            try {
                zip.close();
            } catch (IOException e) {
            }
        }
        for (String abi : sAbiList) {
            if (info.mAbiList.contains(abi)) {
                info.mAbi = abi;
                break;
            }
        }
        File libPath = new File(cacheDir, Plugin.SUB_DIR_LIBS);
        if (info.mAbi != null) {
            // Build.VERSION_CODES.M == 23
            if (Build.VERSION.SDK_INT >= 23 && info.mZipAligned
                    && !apkPath.getAbsolutePath().startsWith("/storage/")) {
                info.mEmbeded = true;
                info.mLibPath = apkPath.getPath() + ZIP_SEPARATOR + "lib/" + info.mAbi;
                Files.rmdirs(libPath);
                return info;
            }
            String abi = info.mAbi + "/";
            for (int i = 0; i < libs.size(); ++i) {
                if (libs.get(i).startsWith(abi))
                    libs.set(i, libs.get(i).substring(abi.length()));
                else
                    libs.set(i, null);
            }
            if (plugin.mSharedCache) {
                libPath = new File(libPath, abiToArch(info.mAbi));
            }
            libPath.mkdirs();
            Files.cleanOthers(libPath, libs);
            info.mExtractLibs = true;
            info.mLibPath = libPath.getAbsolutePath();
            return info;
        } else {
            Files.rmdirs(libPath);
        }
        if (apkPath.getAbsolutePath().startsWith("/system/") 
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.mAbi = sAbiList[0];
            String libArch = abiToArch(info.mAbi);
            String stem = apkPath.getName().substring(0, 
                    apkPath.getName().lastIndexOf('.'));
            File sysLibPath1 = new File(apkPath.getParentFile(), 
                    "lib/" + libArch);
            File sysLibPath2 = new File("/system/app/" + stem, 
                    "lib/" + libArch);
            if (sysLibPath1.exists())
                info.mLibPath = sysLibPath1.getAbsolutePath();
            else if (sysLibPath2.exists())
                info.mLibPath = sysLibPath2.getAbsolutePath();
        }
        return info;
    }

    private static String abiToArch(String abi) {
        for (String arch : oatArchs) {
            if (abi.startsWith(arch)) {
                return arch;
            }
        }
        return null;
    }

}
