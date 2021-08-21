package com.virjar.hermes.hermesagent.util;

import android.os.Build;
import android.util.Log;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.virjar.baksmalisrc.dexlib2.Opcodes;
import com.virjar.baksmalisrc.dexlib2.dexbacked.DexBackedDexFile;
import com.virjar.baksmalisrc.dexlib2.dexbacked.DexBackedOdexFile;
import com.virjar.baksmalisrc.dexlib2.dexbacked.raw.HeaderItem;
import com.virjar.baksmalisrc.dexlib2.dexbacked.raw.OdexHeaderItem;
import com.virjar.baksmalisrc.dexlib2.iface.ClassDef;
import com.virjar.baksmalisrc.dexlib2.util.DexUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Set;



/*
 * Created by virjar on 2018/8/8.<br>
 * 类扫描器，该扫描器无法扫描有壳的class和系统的class
*/

public class ClassScanner {

    public static <T> List<Class<? extends T>> scan(Class<T> pclazz) {
        SubClassVisitor<T> subClassVisitor = new SubClassVisitor<T>(false, pclazz);
        scan(subClassVisitor);
        return subClassVisitor.getSubClass();
    }

    public static <T> void scan(ClassVisitor<T> subClassVisitor) {
        scan(subClassVisitor, Lists.<String>newArrayList());
    }

    public static <T> void scan(ClassVisitor<T> subClassVisitor, Collection<String> basePackages) {

        PackageSearchNode packageSearchNode = new PackageSearchNode();
        for (String packageName : basePackages) {
            packageSearchNode.addToTree(packageName);
        }
        scan(subClassVisitor, packageSearchNode);
    }

    public interface ClassVisitor<T> {
        void visit(Class<? extends T> clazz);
    }

    public static class AnnotationClassVisitor implements ClassScanner.ClassVisitor {
        private Class annotationClazz;
        private Set<Class> classSet = Sets.newHashSet();

        public AnnotationClassVisitor(Class annotationClazz) {
            this.annotationClazz = annotationClazz;
        }

        @Override
        public void visit(Class clazz) {
            try {
                if (clazz.getAnnotation(annotationClazz) != null) {
                    classSet.add(clazz);
                }
            } catch (Throwable e) {
                // do nothing 可能有classNotFoundException
            }
        }

        public Set<Class> getClassSet() {
            return classSet;
        }
    }

    public static class AnnotationMethodVisitor implements ClassScanner.ClassVisitor {
        private Class annotationClazz;
        private Set<Method> methodSet = Sets.newHashSet();

        public AnnotationMethodVisitor(Class annotationClazz) {
            this.annotationClazz = annotationClazz;
        }

        @Override
        public void visit(Class clazz) {
            try {
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.getAnnotation(annotationClazz) != null) {
                        methodSet.add(method);
                    }
                }
            } catch (Throwable e) {
                // do nothing 可能有classNotFoundException
            }
        }

        public Set<Method> getMethodSet() {
            return methodSet;
        }
    }

    public static class SubClassVisitor<T> implements ClassVisitor {

        private boolean mustCanInstance = false;
        private List<Class<? extends T>> subClass = Lists.newArrayList();
        private Class<T> parentClass;

        public SubClassVisitor(boolean mustCanInstance, Class<T> parentClass) {
            this.mustCanInstance = mustCanInstance;
            this.parentClass = parentClass;
        }

        public List<Class<? extends T>> getSubClass() {
            return subClass;
        }

        @Override
        public void visit(Class clazz) {
            if (clazz != null && parentClass.isAssignableFrom(clazz)) {
                if (mustCanInstance) {
                    if (clazz.isInterface())
                        return;

                    if (Modifier.isAbstract(clazz.getModifiers()))
                        return;
                }
                subClass.add(clazz);
            }
        }

    }

    private static DexBackedDexFile createDexFile() {
        Object dex = ReflectUtil.callMethod(ClassScanner.class, "getDex");
        if (dex == null) {
            return null;
        }
        byte[] buffer = (byte[]) ReflectUtil.callMethod(dex, "getBytes");

        if (HeaderItem.verifyMagic(buffer, 0)) {
            return new DexBackedDexFile(Opcodes.forApi(Build.VERSION.SDK_INT), buffer);
            //a normal dex file
        }
        if (OdexHeaderItem.verifyMagic(buffer, 0)) {
            //this is a odex file
            try {
                ByteArrayInputStream is = new ByteArrayInputStream(buffer);
                DexUtil.verifyOdexHeader(is);
                is.reset();
                byte[] odexBuf = new byte[OdexHeaderItem.ITEM_SIZE];
                ByteStreams.readFully(is, odexBuf);
                int dexOffset = OdexHeaderItem.getDexOffset(odexBuf);
                if (dexOffset > OdexHeaderItem.ITEM_SIZE) {
                    ByteStreams.skipFully(is, dexOffset - OdexHeaderItem.ITEM_SIZE);
                }
                return new DexBackedOdexFile(Opcodes.forApi(Build.VERSION.SDK_INT), odexBuf, ByteStreams.toByteArray(is));
            } catch (IOException e) {
                //while not happen
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> void scan(ClassVisitor<T> classVisitor, PackageSearchNode packageSearchNode) {
        DexBackedDexFile dexFile = createDexFile();
        if (dexFile == null) {
            Log.e("weijia", "无法定位dex文件，无法读取class列表");
            return;
        }
        ClassLoader classLoader = ClassScanner.class.getClassLoader();
        for (ClassDef classDef : dexFile.getClasses()) {
            String className = descriptorToDot(classDef.getType());
            if (className.contains("$")) {
                //忽略内部类
                continue;
            }
            if (!packageSearchNode.isSubPackage(className)) {
                continue;
            }
            try {
                Class<T> aClass = (Class<T>) classLoader.loadClass(className);
                classVisitor.visit(aClass);
            } catch (Throwable t) {
                //ignore
            }
        }
    }

    private static String primitiveTypeLabel(char typeChar) {
        switch (typeChar) {
            case 'B':
                return "byte";
            case 'C':
                return "char";
            case 'D':
                return "double";
            case 'F':
                return "float";
            case 'I':
                return "int";
            case 'J':
                return "long";
            case 'S':
                return "short";
            case 'V':
                return "void";
            case 'Z':
                return "boolean";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Converts a type descriptor to human-readable "dotted" form.  For
     * example, "Ljava/lang/String;" becomes "java.lang.String", and
     * "[I" becomes "int[]".  Also converts '$' to '.', which means this
     * form can't be converted back to a descriptor.
     * 这段代码是虚拟机里面抠出来的，c++转java
     */
    public static String descriptorToDot(String str) {
        int targetLen = str.length();
        int offset = 0;
        int arrayDepth = 0;


        /* strip leading [s; will be added to end */
        while (targetLen > 1 && str.charAt(offset) == '[') {
            offset++;
            targetLen--;
        }
        arrayDepth = offset;

        if (targetLen == 1) {
            /* primitive type */
            str = primitiveTypeLabel(str.charAt(offset));
            offset = 0;
            targetLen = str.length();
        } else {
            /* account for leading 'L' and trailing ';' */
            if (targetLen >= 2 && str.charAt(offset) == 'L' &&
                    str.charAt(offset + targetLen - 1) == ';') {
                targetLen -= 2;
                offset++;
            }
        }
        StringBuilder newStr = new StringBuilder(targetLen + arrayDepth * 2);
        /* copy class name over */
        int i;
        for (i = 0; i < targetLen; i++) {
            char ch = str.charAt(offset + i);
            newStr.append((ch == '/') ? '.' : ch);
            //do not convert "$" to ".", ClassLoader.loadClass use "$"
            //newStr.append((ch == '/' || ch == '$') ? '.' : ch);
        }
        /* add the appropriate number of brackets for arrays */
        //感觉源代码这里有bug？？？？，arrayDepth会被覆盖，之后的assert应该有问题
        int tempArrayDepth = arrayDepth;
        while (tempArrayDepth-- > 0) {
            newStr.append('[');
            newStr.append(']');
        }
        return new String(newStr);
    }
}
