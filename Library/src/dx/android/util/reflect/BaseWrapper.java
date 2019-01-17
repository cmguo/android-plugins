package dx.android.util.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;

public class BaseWrapper<T> {

    protected static final String TAG = "BaseWrapper";
    
    protected static final int FL_THROW_EXCEPTION = 1;
    protected static final int FL_FORCE_ACCESSBIE = 2;
    
    protected int mFlags;
    protected Class<?> mCls;
    
    protected BaseWrapper(Class<?> cls) {
        mCls = cls;
    }
    
    public Class<?> getClazz() {
        return mCls;
    }
    
    @SuppressWarnings("unchecked")
    public <E> E getObject() {
        return (E) mCls;
    }
    
    @SuppressWarnings("unchecked")
    public T setThrowException(boolean set) {
        setFlags(FL_THROW_EXCEPTION, set);
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T setForceAccessible(boolean set) {
        setFlags(FL_FORCE_ACCESSBIE, set);
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    protected <E> E get(Object object, String name) {
        try {
            Field f = getField(name);
            return (E) f.get(object);
        } catch (Throwable e) {
            Log.d(TAG, "get mCls " + mCls);
            Log.d(TAG, "get object " + object);
            Log.e(TAG, "get", e);
            throwException(e);
            return null;
        }
    }

    private void throwException(Throwable e) {
        if ((mFlags & FL_THROW_EXCEPTION) != 0)
            throw new RuntimeException(TAG, e);
    }

    @SuppressWarnings("unchecked")
    public <E> Class<E> getType(String name) {
        try {
            Field f = mCls.getField(name);
            return (Class<E>) f.getType();
        } catch (Throwable e) {
            Log.d(TAG, "getType mCls " + mCls);
            Log.e(TAG, "getType", e);
            throwException(e);
            return null;
        }
    }
    
    protected <E> void set(Object object, String name, E value) {
        try {
            Field f = getField(name);
            f.set(object, value);
        } catch (Throwable e) {
            Log.d(TAG, "set mCls " + mCls);
            Log.d(TAG, "set object " + object);
            Log.e(TAG, "set", e);
            throwException(e);
        }
    }
    
    public boolean has(String name) {
        try {
            Field f = mCls.getField(name);
            return f != null;
        } catch (NoSuchFieldException e) {
            return false;
        } catch (NoSuchFieldError e) {
            return false;
        } catch (Throwable e) {
            Log.e(TAG, "has", e);
            throwException(e);
            return false;
        }
    }
    
    private static Map<Class<?>, Class<?>> sPrimitiveMap = new HashMap<Class<?>, Class<?>>();
    
    static {
        sPrimitiveMap.put(Boolean.class, boolean.class);
        sPrimitiveMap.put(Character.class, char.class);
        sPrimitiveMap.put(Byte.class, byte.class);
        sPrimitiveMap.put(Short.class, char.class);
        sPrimitiveMap.put(Integer.class, int.class);
        sPrimitiveMap.put(Long.class, long.class);
        sPrimitiveMap.put(Float.class, float.class);
        sPrimitiveMap.put(Double.class, double.class);
    }
    
    public boolean hasConstructor(Class<?>... params) {
        try {
            Constructor<?> c = mCls.getConstructor(params);
            return c != null;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (NoSuchMethodError e) {
            return false;
        } catch (Throwable e) {
            Log.e(TAG, "hasConstructor", e);
            throwException(e);
            return false;
        }
    }
    
    public boolean hasMethod(String name, Class<?>... params) {
        try {
            Method m = mCls.getMethod(name, params);
            return m != null;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (NoSuchMethodError e) {
            return false;
        } catch (Throwable e) {
            Log.e(TAG, "hasMethod", e);
            throwException(e);
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
    protected <E> E invoke(Object object, String name, Object[] args) {
        try {
            Class<?>[] params = null;
            if (args != null) {
                params = new Class<?>[args.length];
                for (int i = 0; i < args.length; ++i) {
                    if (args[i] instanceof BaseWrapper) {
                        params[i] = ((BaseWrapper<?>) args[i]).getClazz();
                        args[i] = ((BaseWrapper<?>) args[i]).getObject();
                    } else {
                        Class<?> cls = args[i].getClass();
                        params[i] = sPrimitiveMap.get(cls);
                        if (params[i] == null)
                            params[i] = cls;
                    }
                }
            }
            if (name == null) {
                Constructor<?> c = getConstructor(params);
                return (E) c.newInstance(args);
            } else {
                Method m = getMethod(name, params);
                return (E) m.invoke(object, args);
            }
        } catch (Throwable e) {
            Log.d(TAG, "invoke mCls " + mCls);
            Log.d(TAG, "invoke object " + object);
            Log.e(TAG, "invoke", e);
            throwException(e);
            return null;
        }
    }
    
    protected void setFlags(int flags, boolean set) {
        if (set)
            mFlags |= flags;
        else
            mFlags &= ~flags;
    }

    private Field getField(String name) throws NoSuchFieldException {
        Field f;
        if ((mFlags & FL_FORCE_ACCESSBIE) != 0) {
            f = mCls.getDeclaredField(name);
            f.setAccessible(true);
        } else {
            f = mCls.getField(name);
        }
        return f;
    }
    
    private Constructor<?> getConstructor(Class<?>[] params) throws NoSuchMethodException {
        Constructor<?> c = mCls.getConstructor(params);
        if ((mFlags & FL_FORCE_ACCESSBIE) != 0) {
            c.setAccessible(true);
        }
        return c;
    }
    
    private Method getMethod(String name, Class<?>[] params) throws NoSuchMethodException {
        Method m;
        if ((mFlags & FL_FORCE_ACCESSBIE) != 0) {
            m = mCls.getDeclaredMethod(name, params);
            m.setAccessible(true);
        } else {
            m = mCls.getMethod(name, params);
        }
        return m;
    }
    
}
