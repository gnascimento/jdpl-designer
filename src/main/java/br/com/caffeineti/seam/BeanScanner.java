package br.com.caffeineti.seam;

import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans a project directory (recursively) and/or JAR files for JBoss Seam 2
 * and CDI annotated beans.
 */
public class BeanScanner {

    // Seam 2 annotations
    private static final String SEAM_NAME        = "Lorg/jboss/seam/annotations/Name;";
    private static final String SEAM_SCOPE        = "Lorg/jboss/seam/annotations/Scope;";
    private static final String SEAM_FACTORY      = "Lorg/jboss/seam/annotations/Factory;";
    private static final String SEAM_OBSERVER     = "Lorg/jboss/seam/annotations/Observer;";

    // CDI / javax.inject annotations
    private static final String CDI_NAMED_JAVAX   = "Ljavax/inject/Named;";
    private static final String CDI_NAMED_JAKARTA = "Ljakarta/inject/Named;";
    private static final String CDI_APP_JAVAX     = "Ljavax/enterprise/context/ApplicationScoped;";
    private static final String CDI_SES_JAVAX     = "Ljavax/enterprise/context/SessionScoped;";
    private static final String CDI_REQ_JAVAX     = "Ljavax/enterprise/context/RequestScoped;";
    private static final String CDI_CON_JAVAX     = "Ljavax/enterprise/context/ConversationScoped;";
    private static final String CDI_APP_JAKARTA   = "Ljakarta/enterprise/context/ApplicationScoped;";
    private static final String CDI_SES_JAKARTA   = "Ljakarta/enterprise/context/SessionScoped;";
    private static final String CDI_REQ_JAKARTA   = "Ljakarta/enterprise/context/RequestScoped;";
    private static final String CDI_CON_JAKARTA   = "Ljakarta/enterprise/context/ConversationScoped;";

    public List<SeamBean> scan(File projectRoot) {
        List<SeamBean> beans = new ArrayList<>();
        if (!projectRoot.exists()) return beans;
        scanDirectory(projectRoot, beans);
        return beans;
    }

    private void scanDirectory(File dir, List<SeamBean> beans) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(f, beans);
            } else if (f.getName().endsWith(".class")) {
                scanClassFile(f, beans);
            } else if (f.getName().endsWith(".jar")) {
                scanJar(f, beans);
            }
        }
    }

    private void scanClassFile(File classFile, List<SeamBean> beans) {
        try (InputStream is = classFile.toURI().toURL().openStream()) {
            SeamBean bean = analyzeClass(is);
            if (bean != null) beans.add(bean);
        } catch (IOException ignored) {}
    }

    private void scanJar(File jarFile, List<SeamBean> beans) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        SeamBean bean = analyzeClass(is);
                        if (bean != null) beans.add(bean);
                    } catch (IOException ignored) {}
                }
            }
        } catch (IOException ignored) {}
    }

    private SeamBean analyzeClass(InputStream is) throws IOException {
        ClassReader reader = new ClassReader(is);
        BeanClassVisitor visitor = new BeanClassVisitor();
        reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return visitor.build();
    }

    // -------------------------------------------------------------------------
    // ASM Visitors
    // -------------------------------------------------------------------------

    private static class BeanClassVisitor extends ClassVisitor {
        private String className;
        private String beanName;
        private SeamBean.BeanType beanType;
        private String scope = "DEFAULT";
        private boolean isSeamBean = false;
        private boolean isCdiBean = false;
        private final List<String> publicMethods = new ArrayList<>();

        BeanClassVisitor() { super(Opcodes.ASM9); }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name.replace('/', '.');
            // derive a simple default bean name from class name
            String simple = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
            this.beanName = Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return switch (descriptor) {
                case SEAM_NAME -> {
                    isSeamBean = true;
                    beanType = SeamBean.BeanType.SEAM;
                    yield new StringValueCapture("value", v -> beanName = v);
                }
                case CDI_NAMED_JAVAX, CDI_NAMED_JAKARTA -> {
                    isCdiBean = true;
                    beanType = SeamBean.BeanType.CDI;
                    yield new StringValueCapture("value", v -> { if (!v.isBlank()) beanName = v; });
                }
                case CDI_APP_JAVAX, CDI_APP_JAKARTA -> { scope = "APPLICATION"; isCdiBean = true; yield null; }
                case CDI_SES_JAVAX, CDI_SES_JAKARTA -> { scope = "SESSION";     isCdiBean = true; yield null; }
                case CDI_REQ_JAVAX, CDI_REQ_JAKARTA -> { scope = "REQUEST";     isCdiBean = true; yield null; }
                case CDI_CON_JAVAX, CDI_CON_JAKARTA -> { scope = "CONVERSATION";isCdiBean = true; yield null; }
                default -> null;
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if ((access & Opcodes.ACC_PUBLIC) != 0
                    && (access & Opcodes.ACC_STATIC) == 0
                    && !name.equals("<init>") && !name.equals("<clinit>")) {
                publicMethods.add(name);
            }
            return null;
        }

        SeamBean build() {
            if (!isSeamBean && !isCdiBean) return null;
            if (beanType == null) beanType = SeamBean.BeanType.CDI;
            SeamBean bean = new SeamBean(beanName, className, beanType, scope);
            // Remove duplicates while preserving order
            new LinkedHashSet<>(publicMethods).forEach(bean::addMethod);
            return bean;
        }
    }

    /** Captures a single string annotation attribute. */
    private static class StringValueCapture extends AnnotationVisitor {
        private final String targetKey;
        private final java.util.function.Consumer<String> consumer;

        StringValueCapture(String targetKey, java.util.function.Consumer<String> consumer) {
            super(Opcodes.ASM9);
            this.targetKey = targetKey;
            this.consumer = consumer;
        }

        @Override
        public void visit(String name, Object value) {
            if (targetKey.equals(name) && value instanceof String s) consumer.accept(s);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            // capture Seam ScopeType enum value
            if ("value".equals(name)) consumer.accept(value);
        }
    }
}
