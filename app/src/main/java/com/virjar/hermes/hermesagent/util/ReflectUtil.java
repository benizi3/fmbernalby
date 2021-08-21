package com.virjar.hermes.hermesagent.util;

import android.annotation.SuppressLint;
import android.app.Application;

import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by virjar on 2018/8/28.<br>
 * 反射工具
 */

public class ReflectUtil {
    private static final HashMap<String, Field> fieldCache = new HashMap<>();
    private static final HashMap<String, Method> methodCache = new HashMap<>();
    private static final HashMap<String, Constructor<?>> constructorCache = new HashMap<>();
    private static final WeakHashMap<Object, HashMap<String, Object>> additionalFields = new WeakHashMap<>();
    private static final HashMap<String, ThreadLocal<AtomicInteger>> sMethodDepth = new HashMap<>();

    @SuppressLint("PrivateApi")
    public static Application getApplicationUsingReflection() throws Exception {
        return (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null,
                (Object[]) null);
    }

    private static void makeAccessible(Field field) {
        if (!Modifier.isPublic(field.getModifiers())) {
            field.setAccessible(true);
        }
    }

    private static Field getDeclaredField(Object object, String filedName) {
        for (Class<?> superClass = object.getClass(); superClass != Object.class; superClass = superClass
                .getSuperclass()) {
            try {
                return superClass.getDeclaredField(filedName);
            } catch (NoSuchFieldException e) {
                // Field 不在当前类定义, 继续向上转型
            }
        }
        return null;
    }

    public static void setFieldValue(Object object, String fieldName, Object value) {
        Field field = getDeclaredField(object, fieldName);

        if (field == null)
            throw new IllegalArgumentException("Could not find field [" + fieldName + "] on target [" + object + "]");

        makeAccessible(field);

        try {
            field.set(object, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object object, String fieldName) {
        Field field = getDeclaredField(object, fieldName);
        if (field == null)
            throw new IllegalArgumentException("Could not find field [" + fieldName + "] on target [" + object + "]");

        makeAccessible(field);

        Object result;
        try {
            result = field.get(object);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        return (T) result;
    }

    //#################################################################################################

    /**
     * Calls an instance or static method of the given object.
     * The method is resolved using {@link #findMethodBestMatch(Class, String, Object...)}.
     *
     * @param obj        The object instance. A class reference is not sufficient!
     * @param methodName The method name.
     * @param args       The arguments for the method call.
     * @throws NoSuchMethodError In case no suitable method was found.
     */
    public static Object callMethod(Object obj, String methodName, Object... args) {
        try {
            return findMethodBestMatch(obj.getClass(), methodName, args).invoke(obj, args);
        } catch (IllegalAccessException e) {
            // should not happen
            e.printStackTrace();
            throw new IllegalAccessError(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /**
     * Calls an instance or static method of the given object.
     * See {@link #callMethod(Object, String, Object...)}.
     * <p>
     * <p>This variant allows you to specify parameter types, which can help in case there are multiple
     * methods with the same name, especially if you call it with {@code null} parameters.
     */
    public static Object callMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            return findMethodBestMatch(obj.getClass(), methodName, parameterTypes, args).invoke(obj, args);
        } catch (IllegalAccessException e) {
            // should not happen
            e.printStackTrace();
            throw new IllegalAccessError(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /**
     * Calls a static method of the given class.
     * The method is resolved using {@link #findMethodBestMatch(Class, String, Object...)}.
     *
     * @param clazz      The class reference.
     * @param methodName The method name.
     * @param args       The arguments for the method call.
     * @throws NoSuchMethodError In case no suitable method was found.
     */
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        try {
            return findMethodBestMatch(clazz, methodName, args).invoke(null, args);
        } catch (IllegalAccessException e) {
            // should not happen
            e.printStackTrace();
            throw new IllegalAccessError(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /**
     * Calls a static method of the given class.
     * See {@link #callStaticMethod(Class, String, Object...)}.
     * <p>
     * <p>This variant allows you to specify parameter types, which can help in case there are multiple
     * methods with the same name, especially if you call it with {@code null} parameters.
     */
    public static Object callStaticMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            return findMethodBestMatch(clazz, methodName, parameterTypes, args).invoke(null, args);
        } catch (IllegalAccessException e) {
            // should not happen
            e.printStackTrace();
            throw new IllegalAccessError(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /**
     * Look up a method in a class and set it to accessible.
     * See {@link #findMethodExact(String, ClassLoader, String, Object...)} for details.
     */
    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) {
        return findMethodExact(clazz, methodName, getParameterClasses(clazz.getClassLoader(), parameterTypes));
    }

    /**
     * Look up and return a method if it exists.
     * See {@link #findMethodExactIfExists(String, ClassLoader, String, Object...)} for details.
     */
    public static Method findMethodExactIfExists(Class<?> clazz, String methodName, Object... parameterTypes) {
        try {
            return findMethodExact(clazz, methodName, parameterTypes);
        } catch (IllegalStateException | NoSuchMethodError e) {
            return null;
        }
    }

    /**
     * Look up a method in a class and set it to accessible.
     * The method must be declared or overridden in the given class.
     * <p>
     * the method and parameter type resolution.
     *
     * @param className      The name of the class which implements the method.
     * @param classLoader    The class loader for resolving the target and parameter classes.
     * @param methodName     The target method name.
     * @param parameterTypes The parameter types of the target method.
     * @return A reference to the method.
     * @throws NoSuchMethodError In case the method was not found.
     */
    public static Method findMethodExact(String className, ClassLoader classLoader, String methodName, Object... parameterTypes) {
        return findMethodExact(findClass(className, classLoader), methodName, getParameterClasses(classLoader, parameterTypes));
    }

    /**
     * Look up and return a method if it exists.
     * Like {@link #findMethodExact(String, ClassLoader, String, Object...)}, but doesn't throw an
     * exception if the method doesn't exist.
     *
     * @param className      The name of the class which implements the method.
     * @param classLoader    The class loader for resolving the target and parameter classes.
     * @param methodName     The target method name.
     * @param parameterTypes The parameter types of the target method.
     * @return A reference to the method, or {@code null} if it doesn't exist.
     */
    public static Method findMethodExactIfExists(String className, ClassLoader classLoader, String methodName, Object... parameterTypes) {
        try {
            return findMethodExact(className, classLoader, methodName, parameterTypes);
        } catch (IllegalStateException | NoSuchMethodError e) {
            return null;
        }
    }

    /**
     * Look up a method in a class and set it to accessible.
     * See {@link #findMethodExact(String, ClassLoader, String, Object...)} for details.
     * <p>
     * <p>This variant requires that you already have reference to all the parameter types.
     */
    public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String fullMethodName = clazz.getName() + '#' + methodName + getParametersString(parameterTypes) + "#exact";

        if (methodCache.containsKey(fullMethodName)) {
            Method method = methodCache.get(fullMethodName);
            if (method == null)
                throw new NoSuchMethodError(fullMethodName);
            return method;
        }

        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            methodCache.put(fullMethodName, method);
            return method;
        } catch (NoSuchMethodException e) {
            methodCache.put(fullMethodName, null);
            throw new NoSuchMethodError(fullMethodName);
        }
    }

    /**
     * Returns an array of all methods declared/overridden in a class with the specified parameter types.
     * <p>
     * <p>The return type is optional, it will not be compared if it is {@code null}.
     * Use {@code void.class} if you want to search for methods returning nothing.
     *
     * @param clazz          The class to look in.
     * @param returnType     The return type, or {@code null} (see above).
     * @param parameterTypes The parameter types.
     * @return An array with matching methods, all set to accessible already.
     */
    public static Method[] findMethodsByExactParameters(Class<?> clazz, Class<?> returnType, Class<?>... parameterTypes) {
        List<Method> result = new LinkedList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (returnType != null && returnType != method.getReturnType())
                continue;

            Class<?>[] methodParameterTypes = method.getParameterTypes();
            if (parameterTypes.length != methodParameterTypes.length)
                continue;

            boolean match = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i] != methodParameterTypes[i]) {
                    match = false;
                    break;
                }
            }

            if (!match)
                continue;

            method.setAccessible(true);
            result.add(method);
        }
        return result.toArray(new Method[result.size()]);
    }

    /**
     * Look up a method in a class and set it to accessible.
     * <p>
     * <p>This does'nt only look for exact matches, but for the best match. All considered candidates
     * must be compatible with the given parameter types, i.e. the parameters must be assignable
     * to the method's formal parameters. Inherited methods are considered here.
     *
     * @param clazz          The class which declares, inherits or overrides the method.
     * @param methodName     The method name.
     * @param parameterTypes The types of the method's parameters.
     * @return A reference to the best-matching method.
     * @throws NoSuchMethodError In case no suitable method was found.
     */
    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String fullMethodName = clazz.getName() + '#' + methodName + getParametersString(parameterTypes) + "#bestmatch";

        if (methodCache.containsKey(fullMethodName)) {
            Method method = methodCache.get(fullMethodName);
            if (method == null)
                throw new NoSuchMethodError(fullMethodName);
            return method;
        }

        try {
            Method method = findMethodExact(clazz, methodName, parameterTypes);
            methodCache.put(fullMethodName, method);
            return method;
        } catch (NoSuchMethodError ignored) {
        }

        Method bestMatch = null;
        Class<?> clz = clazz;
        boolean considerPrivateMethods = true;
        do {
            for (Method method : clz.getDeclaredMethods()) {
                // don't consider private methods of superclasses
                if (!considerPrivateMethods && Modifier.isPrivate(method.getModifiers()))
                    continue;

                // compare name and parameters
                if (method.getName().equals(methodName) && ClassUtils.isAssignable(parameterTypes, method.getParameterTypes(), true)) {
                    // get accessible version of method
                    if (bestMatch == null || MemberUtils.compareParameterTypes(
                            method.getParameterTypes(),
                            bestMatch.getParameterTypes(),
                            parameterTypes) < 0) {
                        bestMatch = method;
                    }
                }
            }
            considerPrivateMethods = false;
        } while ((clz = clz.getSuperclass()) != null);

        if (bestMatch != null) {
            bestMatch.setAccessible(true);
            methodCache.put(fullMethodName, bestMatch);
            return bestMatch;
        } else {
            NoSuchMethodError e = new NoSuchMethodError(fullMethodName);
            methodCache.put(fullMethodName, null);
            throw e;
        }
    }

    /**
     * Look up a method in a class and set it to accessible.
     * <p>
     * <p>See {@link #findMethodBestMatch(Class, String, Class...)} for details. This variant
     * determines the parameter types from the classes of the given objects.
     */
    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Object... args) {
        return findMethodBestMatch(clazz, methodName, getParameterTypes(args));
    }

    /**
     * Look up a method in a class and set it to accessible.
     * <p>
     * <p>See {@link #findMethodBestMatch(Class, String, Class...)} for details. This variant
     * determines the parameter types from the classes of the given objects. For any item that is
     * {@code null}, the type is taken from {@code parameterTypes} instead.
     */
    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object[] args) {
        Class<?>[] argsClasses = null;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] != null)
                continue;
            if (argsClasses == null)
                argsClasses = getParameterTypes(args);
            parameterTypes[i] = argsClasses[i];
        }
        return findMethodBestMatch(clazz, methodName, parameterTypes);
    }

    /**
     * Returns an array with the classes of the given objects.
     */
    public static Class<?>[] getParameterTypes(Object... args) {
        Class<?>[] clazzes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            clazzes[i] = (args[i] != null) ? args[i].getClass() : null;
        }
        return clazzes;
    }

    /**
     * Retrieve classes from an array, where each element might either be a Class
     * already, or a String with the full class name.
     */
    private static Class<?>[] getParameterClasses(ClassLoader classLoader, Object[] parameterTypesAndCallback) {
        Class<?>[] parameterClasses = null;
        for (int i = parameterTypesAndCallback.length - 1; i >= 0; i--) {
            Object type = parameterTypesAndCallback[i];
            if (type == null)
                throw new IllegalStateException("parameter type must not be null", null);


            if (parameterClasses == null)
                parameterClasses = new Class<?>[i + 1];

            if (type instanceof Class)
                parameterClasses[i] = (Class<?>) type;
            else if (type instanceof String)
                parameterClasses[i] = findClass((String) type, classLoader);
            else
                throw new IllegalStateException("parameter type must either be specified as Class or String", null);
        }

        // if there are no arguments for the method
        if (parameterClasses == null)
            parameterClasses = new Class<?>[0];

        return parameterClasses;
    }

    /**
     * Returns an array of the given classes.
     */
    public static Class<?>[] getClassesAsArray(Class<?>... clazzes) {
        return clazzes;
    }

    private static String getParametersString(Class<?>... clazzes) {
        StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (Class<?> clazz : clazzes) {
            if (first)
                first = false;
            else
                sb.append(",");

            if (clazz != null)
                sb.append(clazz.getCanonicalName());
            else
                sb.append("null");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Look up a constructor of a class and set it to accessible.
     * See {@link #findMethodExact(String, ClassLoader, String, Object...)} for details.
     */
    public static Constructor<?> findConstructorExact(Class<?> clazz, Object... parameterTypes) {
        return findConstructorExact(clazz, getParameterClasses(clazz.getClassLoader(), parameterTypes));
    }

    /**
     * Look up and return a constructor if it exists.
     * See {@link #findMethodExactIfExists(String, ClassLoader, String, Object...)} for details.
     */
    public static Constructor<?> findConstructorExactIfExists(Class<?> clazz, Object... parameterTypes) {
        try {
            return findConstructorExact(clazz, parameterTypes);
        } catch (IllegalStateException | NoSuchMethodError e) {
            return null;
        }
    }

    /**
     * Look up a constructor of a class and set it to accessible.
     * See {@link #findMethodExact(String, ClassLoader, String, Object...)} for details.
     */
    public static Constructor<?> findConstructorExact(String className, ClassLoader classLoader, Object... parameterTypes) {
        return findConstructorExact(findClass(className, classLoader), getParameterClasses(classLoader, parameterTypes));
    }

    /**
     * Look up and return a constructor if it exists.
     * See {@link #findMethodExactIfExists(String, ClassLoader, String, Object...)} for details.
     */
    public static Constructor<?> findConstructorExactIfExists(String className, ClassLoader classLoader, Object... parameterTypes) {
        try {
            return findConstructorExact(className, classLoader, parameterTypes);
        } catch (IllegalStateException | NoSuchMethodError e) {
            return null;
        }
    }

    /**
     * Look up a constructor of a class and set it to accessible.
     * See {@link #findMethodExact(String, ClassLoader, String, Object...)} for details.
     */
    public static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>... parameterTypes) {
        String fullConstructorName = clazz.getName() + getParametersString(parameterTypes) + "#exact";

        if (constructorCache.containsKey(fullConstructorName)) {
            Constructor<?> constructor = constructorCache.get(fullConstructorName);
            if (constructor == null)
                throw new NoSuchMethodError(fullConstructorName);
            return constructor;
        }

        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            constructorCache.put(fullConstructorName, constructor);
            return constructor;
        } catch (NoSuchMethodException e) {
            constructorCache.put(fullConstructorName, null);
            throw new NoSuchMethodError(fullConstructorName);
        }
    }


    /**
     * Look up a class with the specified class loader.
     * <p>
     * <p>There are various allowed syntaxes for the class name, but it's recommended to use one of
     * these:
     * <ul>
     * <li>{@code java.lang.String}
     * <li>{@code java.lang.String[]} (array)
     * <li>{@code android.app.ActivityThread.ResourcesKey}
     * <li>{@code android.app.ActivityThread$ResourcesKey}
     * </ul>
     *
     * @param className   The class name in one of the formats mentioned above.
     * @param classLoader The class loader, or {@code null} for the boot class loader.
     * @return A reference to the class.
     */
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        if (classLoader == null)
            classLoader = ReflectUtil.class.getClassLoader();
        try {
            return ClassUtils.getClass(classLoader, className, false);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Look up and return a class if it exists.
     * Like {@link #findClass}, but doesn't throw an exception if the class doesn't exist.
     *
     * @param className   The class name.
     * @param classLoader The class loader, or {@code null} for the boot class loader.
     * @return A reference to the class, or {@code null} if it doesn't exist.
     */
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            return findClass(className, classLoader);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /**
     * Look up a field in a class and set it to accessible.
     *
     * @param clazz     The class which either declares or inherits the field.
     * @param fieldName The field name.
     * @return A reference to the field.
     * @throws NoSuchFieldError In case the field was not found.
     */
    public static Field findField(Class<?> clazz, String fieldName) {
        String fullFieldName = clazz.getName() + '#' + fieldName;

        if (fieldCache.containsKey(fullFieldName)) {
            Field field = fieldCache.get(fullFieldName);
            if (field == null)
                throw new NoSuchFieldError(fullFieldName);
            return field;
        }

        try {
            Field field = findFieldRecursiveImpl(clazz, fieldName);
            field.setAccessible(true);
            fieldCache.put(fullFieldName, field);
            return field;
        } catch (NoSuchFieldException e) {
            fieldCache.put(fullFieldName, null);
            throw new NoSuchFieldError(fullFieldName);
        }
    }

    /**
     * Look up and return a field if it exists.
     * Like {@link #findField}, but doesn't throw an exception if the field doesn't exist.
     *
     * @param clazz     The class which either declares or inherits the field.
     * @param fieldName The field name.
     * @return A reference to the field, or {@code null} if it doesn't exist.
     */
    public static Field findFieldIfExists(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName);
        } catch (NoSuchFieldError e) {
            return null;
        }
    }

    private static Field findFieldRecursiveImpl(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            while (true) {
                clazz = clazz.getSuperclass();
                if (clazz == null || clazz.equals(Object.class))
                    break;

                try {
                    return clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw e;
        }
    }

    /**
     * Returns the first field of the given type in a class.
     * Might be useful for Proguard'ed classes to identify fields with unique types.
     *
     * @param clazz The class which either declares or inherits the field.
     * @param type  The type of the field.
     * @return A reference to the first field of the given type.
     * @throws NoSuchFieldError In case no matching field was not found.
     */
    public static Field findFirstFieldByExactType(Class<?> clazz, Class<?> type) {
        Class<?> clz = clazz;
        do {
            for (Field field : clz.getDeclaredFields()) {
                if (field.getType() == type) {
                    field.setAccessible(true);
                    return field;
                }
            }
        } while ((clz = clz.getSuperclass()) != null);

        throw new NoSuchFieldError("Field of type " + type.getName() + " in class " + clazz.getName());
    }

}
