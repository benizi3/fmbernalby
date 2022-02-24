package com.virjar.hermes.hermesagent.util;

import android.util.Log;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.DexClass;
import net.dongliu.apk.parser.parser.DexParser;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import dalvik.system.PathClassLoader;

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
        scan(subClassVisitor, Lists.<String>newArrayList(), null);
    }

    public static <T> void scan(ClassVisitor<T> subClassVisitor, String basePackage) {
        scan(subClassVisitor, Lists.<String>newArrayList(basePackage), null);
    }

    public static <T> void scan(ClassVisitor<T> subClassVisitor, Collection<String> basePackages, File sourceLocation) {
        scan(subClassVisitor, basePackages, sourceLocation, null);
    }

    public static <T> void scan(ClassVisitor<T> subClassVisitor, Collection<String> basePackages, File sourceLocation, ClassLoader baseClassLoader) {

        PackageSearchNode packageSearchNode = new PackageSearchNode();
        for (String packageName : basePackages) {
            packageSearchNode.addToTree(packageName);
        }
        scan(subClassVisitor, packageSearchNode, sourceLocation, baseClassLoader);
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

    public static final int ITEM_SIZE = 40;

    private static DexClass[] hermesAgentApkClasses() {
        Object dex = ReflectUtil.callMethod(ClassScanner.class, "getDex");
        if (dex == null) {
            return null;
        }
        byte[] buffer = (byte[]) ReflectUtil.callMethod(dex, "getBytes");

        if (verifyDexMagic(buffer, 0)) {
            DexParser dexParser = new DexParser(ByteBuffer.wrap(buffer));
            return dexParser.parse();
        }
        if (verifyOdexMagic(buffer, 0)) {
            try {
                ByteArrayInputStream is = new ByteArrayInputStream(buffer);
                verifyOdexHeader(is);
                is.reset();
                byte[] odexBuf = new byte[ITEM_SIZE];
                ByteStreams.readFully(is, odexBuf);
                int dexOffset = getDexOffset(odexBuf);
                if (dexOffset > ITEM_SIZE) {
                    ByteStreams.skipFully(is, dexOffset - ITEM_SIZE);
                }
                DexParser dexParser = new DexParser(ByteBuffer.wrap(ByteStreams.toByteArray(is)));
                return dexParser.parse();
            } catch (IOException e) {
                //while not happen
                throw new RuntimeException(e);
            }
        }
        return null;
    }


    @SuppressWarnings("unchecked")
    public static <T> void scan(ClassVisitor<T> classVisitor, PackageSearchNode packageSearchNode, File sourceLocation, ClassLoader baseClassLoader) {
        ClassLoader classLoader;
        DexClass[] classes;
        if (sourceLocation == null) {
            classes = hermesAgentApkClasses();
            classLoader = ClassScanner.class.getClassLoader();
        } else {
            try (ApkFile apkFile = new ApkFile(sourceLocation)) {
                classes = apkFile.getDexClasses();
                if (baseClassLoader == null) {
                    baseClassLoader = ClassScanner.class.getClassLoader();
                }
                classLoader = new PathClassLoader(sourceLocation.getAbsolutePath(), baseClassLoader);
            } catch (IOException e) {
                throw new IllegalStateException("the filed not a apk filed format", e);
            }
        }
        if (classes == null) {
            Log.w("weijia", "failed to get classes  info,class scanner will skip");
            return;
        }
        for (DexClass dexClass : classes) {
            String className = descriptorToDot(dexClass.getClassType());
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

    private static final byte[] DEX_MAGIC_VALUE = new byte[]{0x64, 0x65, 0x78, 0x0a, 0x00, 0x00, 0x00, 0x00};

    /**
     * Verifies the magic value at the beginning of a dex file
     *
     * @param buf    A byte array containing at least the first 8 bytes of a dex file
     * @param offset The offset within the buffer to the beginning of the dex header
     * @return True if the magic value is valid
     */
    public static boolean verifyDexMagic(byte[] buf, int offset) {
        if (buf.length - offset < 8) {
            return false;
        }

        for (int i = 0; i < 4; i++) {
            if (buf[offset + i] != DEX_MAGIC_VALUE[i]) {
                return false;
            }
        }
        for (int i = 4; i < 7; i++) {
            if (buf[offset + i] < '0' ||
                    buf[offset + i] > '9') {
                return false;
            }
        }
        if (buf[offset + 7] != DEX_MAGIC_VALUE[7]) {
            return false;
        }

        return true;
    }

    private static final byte[] ODEX_MAGIC_VALUE = new byte[]{0x64, 0x65, 0x79, 0x0A, 0x00, 0x00, 0x00, 0x00};


    /**
     * Verifies the magic value at the beginning of an odex file
     *
     * @param buf    A byte array containing at least the first 8 bytes of an odex file
     * @param offset The offset within the buffer to the beginning of the odex header
     * @return True if the magic value is valid
     */
    public static boolean verifyOdexMagic(byte[] buf, int offset) {
        if (buf.length - offset < 8) {
            return false;
        }

        for (int i = 0; i < 4; i++) {
            if (buf[offset + i] != ODEX_MAGIC_VALUE[i]) {
                return false;
            }
        }
        for (int i = 4; i < 7; i++) {
            if (buf[offset + i] < '0' ||
                    buf[offset + i] > '9') {
                return false;
            }
        }
        if (buf[offset + 7] != ODEX_MAGIC_VALUE[7]) {
            return false;
        }

        return true;
    }

    /**
     * Reads in the odex header from the given input stream and verifies that it is valid and a supported version
     * <p>
     * The inputStream must support mark(), and will be reset to initial position upon exiting the method
     */
    public static void verifyOdexHeader(@Nonnull InputStream inputStream) throws IOException {
        if (!inputStream.markSupported()) {
            throw new IllegalArgumentException("InputStream must support mark");
        }
        inputStream.mark(8);
        byte[] partialHeader = new byte[8];
        try {
            ByteStreams.readFully(inputStream, partialHeader);
        } catch (EOFException ex) {
            throw new IllegalStateException("File is too short");
        } finally {
            inputStream.reset();
        }
    }

    public static final int DEX_OFFSET = 8;

    public static int readSmallUint(int offset, byte[] buf, int baseOffset) {

        offset += baseOffset;
        int result = (buf[offset] & 0xff) |
                ((buf[offset + 1] & 0xff) << 8) |
                ((buf[offset + 2] & 0xff) << 16) |
                ((buf[offset + 3]) << 24);
        if (result < 0) {
            throw new IllegalStateException(String.format("Encountered small uint that is out of range at offset 0x%x", offset));
        }
        return result;
    }


    public static int getDexOffset(byte[] buf) {
        return readSmallUint(DEX_OFFSET, buf, 0);
    }
}
