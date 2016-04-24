package com.android.tools.ndk.run;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class JavaCalls {
//    private static final String LOG_TAG = "JavaCalls";
    private static Map<Class<?>,Class<?>> PRIMITIVE_MAP = null;
    
    public class JavaParam{
    	Class<?> clazz;
    	Object obj;
    	public JavaParam(Class<?> cls,Object o){
    		clazz = cls;
    		obj = o;
    	}
    }

    static  {
        JavaCalls.PRIMITIVE_MAP = new HashMap<Class<?>,Class<?>>();
        JavaCalls.PRIMITIVE_MAP.put(Boolean.class, Boolean.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Byte.class, Byte.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Character.class, Character.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Short.class, Short.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Integer.class, Integer.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Float.class, Float.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Long.class, Long.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Double.class, Double.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Boolean.TYPE, Boolean.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Byte.TYPE, Byte.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Character.TYPE, Character.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Short.TYPE, Short.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Integer.TYPE, Integer.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Float.TYPE, Float.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Long.TYPE, Long.TYPE);
        JavaCalls.PRIMITIVE_MAP.put(Double.TYPE, Double.TYPE);
    }

    public JavaCalls() {
        super();
    }

    public static Object callMethod(Object clsObject, String methodName, Object[] params) {
        Object result;
        try {
            result = JavaCalls.callMethodOrThrow(clsObject,methodName,params);
        }
        catch(Exception v0) {
            result = null;
        }

        return result;
    }

    public static Object callMethodOrThrow(Object clsObject, String methodName, Object[] params) 
    		throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        return JavaCalls.getDeclaredMethod(clsObject.getClass(), methodName, JavaCalls.getParameterTypes(params))
                .invoke(clsObject, JavaCalls.getParameters(params));
    }

    public static Object callStaticMethod(String clsName, String method, Object[] params) {
        Object obj = null;
        try {
            obj = JavaCalls.callStaticMethodOrThrow(Class.forName(clsName), method, params);
        }
        catch(Exception v0) {
        }
        return obj;
    }

    public static Object callStaticMethodOrThrow(Class<?> cls, String method, Object[] params) 
    		throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        return JavaCalls.getDeclaredMethod(cls, method, JavaCalls.getParameterTypes(params)).invoke(null
                , JavaCalls.getParameters(params));
    }

    public static Object callStaticMethodOrThrow(String clsName, String method, Object[] params) 
    		throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
        return JavaCalls.getDeclaredMethod(Class.forName(clsName), method, JavaCalls.getParameterTypes(params
                )).invoke(null, JavaCalls.getParameters(params));
    }

    /*
     * 比较类方法参数类型
     */
    private static boolean compareClassLists(Class<?>[] paramTypes1, Class<?>[] paramTypes2) {
        if(paramTypes1 == null || paramTypes1.length == 0) {
            if(paramTypes2 == null || paramTypes2.length == 0) {
                return true;
            }
			else{
				return false;
			}
        }
        else {
        	if(paramTypes2 == null || paramTypes2.length == 0){
        		return false;
        	}
        	if(paramTypes1.length != paramTypes2.length){
        		return false;
        	}
        	for(int i=0;i<paramTypes1.length;i++){
        		if(!paramTypes1[i].isAssignableFrom(paramTypes2[i])) {
                    if(JavaCalls.PRIMITIVE_MAP.containsKey(paramTypes1[i])) {
                        if(!JavaCalls.PRIMITIVE_MAP.get(paramTypes1[i]).equals(JavaCalls.PRIMITIVE_MAP.get(
                                paramTypes2[i]))) {
                        	return false;
                        }
                    }
                }
        	}
        	return true;
        }
    }

    private static Method findMethodByName(Method[] methodArr, String methodName, Class<?>[] methodParamCls) {
        Method method = null;
        if(methodName == null) {
            throw new NullPointerException("Method name must not be null.");
        }
        for(int i = 0; i < methodArr.length; ++i) {
            method = methodArr[i];
            if((method.getName().equals(methodName)) && (JavaCalls.compareClassLists(method.getParameterTypes(), methodParamCls
                    ))) {
                return method;
            }
        }
        return null;
    }

    private static Method getDeclaredMethod(Class<?> cls, String methodName, Class<?>[] methodParamCls) {
        Method method = null;
        try{
            while(method == null) {
                method = JavaCalls.findMethodByName(cls.getDeclaredMethods(), methodName, methodParamCls);
                if(method == null) {
                    if(cls.getSuperclass() == null) {
                    	throw new NoSuchMethodException();
                    }
                    cls = cls.getSuperclass();
                }
            }
            method.setAccessible(true);	
        }
        catch(Exception e){
        	
        }
        return method;
    }

    private static Object getDefaultValue(Class<?> cls) {
        if((Integer.TYPE.equals(cls)) || (Integer.class.equals(cls)) || (Byte.TYPE.equals(cls)) || 
                (Byte.class.equals(cls)) || (Short.TYPE.equals(cls)) || (Short.class.equals(cls))
                 || (Long.TYPE.equals(cls)) || (Long.class.equals(cls)) || (Double.TYPE.equals(cls
                )) || (Double.class.equals(cls)) || (Float.TYPE.equals(cls)) || (Float.class.equals
                (cls))) {
            return Integer.valueOf(0);
        }
        else {
            if(!Boolean.TYPE.equals(cls) && !Boolean.class.equals(cls)) {
                if(!Character.TYPE.equals(cls) && !Character.class.equals(cls)) {
                    return null;
                }
                return Character.valueOf('\u0000');
            }
            return Boolean.valueOf(false);
        }
    }

    public static Object getField(Object clsObject, String fieldName) {
        try {
			return JavaCalls.getFieldOrThrow(clsObject, fieldName);
		} 
		catch (IllegalArgumentException e) {
		
		} 
		catch (IllegalAccessException e) {
		
		} 
		catch (NoSuchFieldException e) {
		
		}
		return null;
    }

    public static Object getFieldOrThrow(Object clsObj, String fieldName) 
    throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
        Class<?> cls = clsObj.getClass();
        Field field = null;
        do {
            if(field != null) {
            	field.setAccessible(true);
        		return field.get(clsObj);
            }

            try {
                field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
            }
            catch(NoSuchFieldException v2) {
                cls = cls.getSuperclass();
            }
        }
        while(cls != null);

        throw new NoSuchFieldException();
    }

    private static Class<?>[] getParameterTypes(Object[] clsObject) {
        Class<?>[] cls = null;
        if(clsObject != null && clsObject.length > 0) {
            cls = new Class[clsObject.length];
            for(int i = 0; i < clsObject.length; ++i) {
                Object cur = clsObject[i];
                if(cur != null && ((cur instanceof JavaParam))) {
                    cls[i] = ((JavaParam)cur).clazz;
                }
                else if(cur == null) {
                    cls[i] = null;
                }
                else {
                	cls[i] = cur.getClass();
                }
            }
        }
        return cls;
    }

    private static Object[] getParameters(Object[] params) {
        Object[] objs = null;
        if(params != null && params.length > 0) {
            objs = new Object[params.length];
            for(int i = 0; i < params.length; ++i) {
                Object cur = params[i];
                if(cur == null || !(cur instanceof JavaParam)) {
                    objs[i] = cur;
                }
                else {
                    objs[i] = ((JavaParam)cur).obj;
                }
            }
        }
        return objs;
    }

    public static Object newEmptyInstance(Class<?> cls) {
        Object o = null;
        try {
            o = JavaCalls.newEmptyInstanceOrThrow(cls);
        }
        catch(Exception v0) {
        }
        return o;
    }

    public static Object newEmptyInstanceOrThrow(Class<?> cls) 
    	throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Object o;
        Constructor<?>[] cons = cls.getDeclaredConstructors();
        if(cons != null && cons.length != 0) {
            Constructor<?> cur = cons[0];
            cur.setAccessible(true);
            Class<?>[] paramcls = cur.getParameterTypes();
            if(paramcls == null || paramcls.length == 0) {
                o = cur.newInstance();
            }
            else {
                Object[] tmp = new Object[paramcls.length];
                for(int i=0;i<paramcls.length;i++){
                	tmp[i] = JavaCalls.getDefaultValue(paramcls[i]);
                }
                o = cur.newInstance(tmp);
            }
            return o;
        }

        throw new IllegalArgumentException("Can\'t get even one available constructor for " + cls);
    }

    public static Object newInstance(Class<?> cls, Object[] params) {
        Object o = null;
        try {
            o = JavaCalls.newInstanceOrThrow(cls, params);
        }
        catch(Exception v0) {
        }
        return o;
    }

    public static Object newInstance(String clsName, Object[] params) {
        Object o = null;
        try {
            o = JavaCalls.newInstanceOrThrow(clsName, params);
        }
        catch(Exception v0) {

        }
        return o;
    }

    public static Object newInstanceOrThrow(Class<?> cls, Object[] params) 
    throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        return cls.getConstructor(JavaCalls.getParameterTypes(params)).newInstance(JavaCalls.getParameters
                (params));
    }

    public static Object newInstanceOrThrow(String clsName, Object[] params) 
    throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException {
        return JavaCalls.newInstanceOrThrow(Class.forName(clsName), JavaCalls.getParameters(params));
    }

    public static void setField(Object clsObj, String fieldName, Object dataToset) {
        try {
            JavaCalls.setFieldOrThrow(clsObj, fieldName, dataToset);
        }
        catch(IllegalAccessException v0) {
        }
        catch(NoSuchFieldException v0_1) {
        }
    }

    public static void setFieldOrThrow(Object clsObj, String fieldName, Object dataToset) throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Class<?> cls = clsObj.getClass();
        Field field = null;
        do {
            if(field != null) {
                field.setAccessible(true);
                field.set(clsObj, dataToset);
                return;
            }
            try {
                field = cls.getDeclaredField(fieldName);
            }
            catch(NoSuchFieldException v2) {
                cls = cls.getSuperclass();
            }
        }
        while(cls != null);

        throw new NoSuchFieldException();
    }
}