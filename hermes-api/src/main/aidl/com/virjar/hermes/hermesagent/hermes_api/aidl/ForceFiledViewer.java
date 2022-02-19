package com.virjar.hermes.hermesagent.hermes_api.aidl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by virjar on 2018/9/25.<br>
 * 强制的，将一个对象的内部属性dump出来，忽略java pojo的getter规范，请注意这个功能是为了抓取使用，因为该转换不是幂等的，无法还原
 */
public class ForceFiledViewer {
    /**
     * build a plain object from any flex object,the output is a view ,witch access all private & public field,ignore getter on input object
     *
     * @param input any object
     * @return a view,just contain string,number,list,map;or combination
     */
    public static Object toView(Object input) {
        if (input == null) {
            return Collections.emptyMap();
        }
        return toView(input, Sets.newHashSet());
    }

    private static Object trimView(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof Map && ((Map) input).size() == 0) {
            return null;
        }
        if (input.getClass().isArray() && Array.getLength(input) == 0) {
            return null;
        }
        if (input instanceof Collection && ((Collection) input).size() == 0) {
            return null;
        }
        return input;
    }

    private static Object toView(Object input, Set<Object> accessedObjects) {
        if (input == null) {
            return null;
        }
        if (accessedObjects.contains(input)) {
            return null;
        }
        accessedObjects.add(input);
        if (input instanceof CharSequence || input instanceof Number) {
            return input;
        }
        if (input instanceof Date) {
            return ((Date) input).getTime();
        }

        if (input instanceof Collection) {
            List<Object> subRet = Lists.newArrayListWithCapacity(((Collection) input).size());
            boolean hint = false;
            for (Object o1 : (Collection) input) {
                Object view = trimView(toView(o1, accessedObjects));
                if (view != null) {
                    hint = true;
                }
                subRet.add(view);
            }
            if (!hint) {
                subRet.clear();
            }
            return subRet;
        }
        if (input instanceof byte[]) {
            //二进制数据，直接略过不处理
            return "byte stream data";
        }
        if (input.getClass().isArray()) {
            List<Object> subRet = Lists.newArrayListWithCapacity(Array.getLength(input));
            boolean hint = false;
            for (int i = 0; i < Array.getLength(input); i++) {
                Object o1 = Array.get(input, i);
                Object view = trimView(toView(o1, accessedObjects));
                if (view != null) {
                    hint = true;
                }
                subRet.add(view);
            }
            if (!hint) {
                subRet.clear();
            }
            return subRet;
        }


        if (input instanceof Map) {
            Map map = (Map) input;
            Map<String, Object> subRet = Maps.newHashMap();
            Set set = map.entrySet();
            for (Object entry : set) {
                if (!(entry instanceof Map.Entry)) {
                    continue;
                }
                Map.Entry entry1 = (Map.Entry) entry;
                Object key = entry1.getKey();
                Object value = entry1.getValue();
                if (!(key instanceof CharSequence)) {
                    continue;
                }
                Object view = trimView(toView(value, accessedObjects));
                if (view != null) {
                    subRet.put(key.toString(), view);
                }
            }
            return subRet;
        }
        String className = input.getClass().getName();
        if (className.startsWith("android.") || className.startsWith("com.android.")
                || className.startsWith("java.")) {
            //框架内部对象，不抽取，这可能导致递归过深，而且没有意义
            return null;
        }

        Map<String, Object> ret = Maps.newHashMap();

        Field[] fields = classFileds(input.getClass());
        for (Field field : fields) {
            Object o = null;
            try {
                o = field.get(input);
            } catch (IllegalAccessException e) {
                //ignore
            }
            if (o == null) {
                continue;
            }
            Object view = trimView(toView(o, accessedObjects));
            if (view != null) {
                ret.put(field.getName(), view);
            }
        }
        return ret;
    }


    private static Field[] classFileds(Class clazz) {
        if (clazz == Object.class) {
            return new Field[0];
        }
        Field[] fields = fieldCache.get(clazz);
        if (fields != null) {
            return fields;
        }
        synchronized (clazz) {
            fields = fieldCache.get(clazz);
            if (fields != null) {
                return fields;
            }
            ArrayList<Field> ret = Lists.newArrayList();
            ret.addAll(Arrays.asList(clazz.getDeclaredFields()));
            ret.addAll(Arrays.asList(classFileds(clazz.getSuperclass())));
            Iterator<Field> iterator = ret.iterator();
            while (iterator.hasNext()) {
                Field next = iterator.next();
                if (Modifier.isStatic(next.getModifiers())) {
                    iterator.remove();
                    continue;
                }
                if (next.isSynthetic()) {
                    iterator.remove();
                    continue;
                }
                if (!next.isAccessible()) {
                    next.setAccessible(true);
                }
            }
            fields = ret.toArray(new Field[0]);

            fieldCache.put(clazz, fields);
        }
        return fields;
    }

    private static final ConcurrentMap<Class, Field[]> fieldCache = Maps.newConcurrentMap();
}
