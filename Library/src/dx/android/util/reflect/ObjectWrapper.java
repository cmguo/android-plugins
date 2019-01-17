package dx.android.util.reflect;


public class ObjectWrapper<E> extends BaseWrapper<ObjectWrapper<E>> {

    private E mObj;
    
    public static <E> ObjectWrapper<E> wrap(Class<E> cls, E obj) {
        return new ObjectWrapper<E>(cls, obj);
    }
    
    public static <E> ObjectWrapper<E> wrap(E obj) {
        return obj == null ? null : new ObjectWrapper<E>(obj);
    }
       
    public ObjectWrapper(Class<E> cls, E obj) {
        super(cls);
        mObj = obj;
    }
    
    public ObjectWrapper(Class<E> cls, Object... args) {
        super(cls);
        mObj = super.invoke(cls, null, args);
    }
    
    public ObjectWrapper(E obj) {
        super(obj.getClass());
        mObj = obj;
    }
    
    @SuppressWarnings("unchecked")
    public E getObject() {
        return mObj;
    }
    
    public <E1> E1 get(String name) {
        return super.get(mObj, name);
    }
    
    public <E1> void set(String name, E1 value) {
        super.set(mObj, name, value);
    }
    
    public <E1> E1 invoke(String name, Object... args) {
        return super.invoke(mObj, name, args);
    }
    
}
