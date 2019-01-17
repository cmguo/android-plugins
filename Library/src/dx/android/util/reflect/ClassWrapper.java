package dx.android.util.reflect;

import android.util.Log;

public class ClassWrapper<E> extends BaseWrapper<ClassWrapper<E>> {

    public static <E> ClassWrapper<E> wrap(Class<E> cls) {
        return new ClassWrapper<E>(cls);
    }
       
    public static <E> ClassWrapper<E> wrap(String string) {
        return new ClassWrapper<E>(string);
    }
    
    public static <E> ClassWrapper<E> wrap(ClassLoader loader, String string) {
        return new ClassWrapper<E>(loader, string);
    }
    
    public ClassWrapper(Class<E> cls) {
        super(cls);
    }
    
    public ClassWrapper(String name) {
        super(forName(null, name));
    }
    
     public ClassWrapper(ClassLoader loader, String name) {
        super(forName(loader, name));
    }
    
   public <E1> E1 get(String name) {
        return super.get(mCls, name);
    }
    
    public <E1> void set(String name, E1 value) {
        super.set(mCls, name, value);
    }
    
    public <E1> E1 invoke(String name, Object... args) {
        return super.invoke(mCls, name, args);
    }
    
    public E newInstance(Object... args) {
        return super.invoke(mCls, null, args);
    }
    
    private static Class<?> forName(ClassLoader loader, String name) {
        try {
            if (loader == null)
                return Class.forName(name);
            else
                return Class.forName(name, true, loader);
        } catch (Throwable e) {
            Log.d(TAG, "forName", e);
            return null;
        }
    }
}
