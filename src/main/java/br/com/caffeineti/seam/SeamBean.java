package br.com.caffeineti.seam;

import java.util.ArrayList;
import java.util.List;

public class SeamBean {
    public enum BeanType { SEAM, CDI }

    private final String beanName;       // @Name value or @Named value or simple class name
    private final String className;      // fully qualified class name
    private final BeanType beanType;
    private final String scope;          // SESSION, REQUEST, CONVERSATION, APPLICATION, DEPENDENT
    private final List<String> methods = new ArrayList<>();

    public SeamBean(String beanName, String className, BeanType beanType, String scope) {
        this.beanName = beanName;
        this.className = className;
        this.beanType = beanType;
        this.scope = scope;
    }

    public void addMethod(String methodName) { methods.add(methodName); }

    public String getBeanName() { return beanName; }
    public String getClassName() { return className; }
    public BeanType getBeanType() { return beanType; }
    public String getScope() { return scope; }
    public List<String> getMethods() { return methods; }

    /** EL expression to call a method of this bean */
    public String getMethodExpression(String method) {
        return "#{" + beanName + "." + method + "}";
    }

    @Override
    public String toString() {
        return beanName + " [" + beanType + ", " + scope + "]";
    }
}
