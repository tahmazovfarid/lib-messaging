package az.ailab.lib.messaging.util;

import lombok.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Utility class that provides access to the Spring ApplicationContext.
 *
 * <p>This is useful for scenarios where the application context is needed
 * but dependency injection is not available, such as in dynamically created beans.</p>
 */
public class ApplicationContextUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    /**
     * Sets the application context.
     *
     * @param applicationContext the application context
     * @throws BeansException if an error occurs when setting the application context
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        ApplicationContextUtil.applicationContext = applicationContext;
    }

    /**
     * Returns the application context.
     *
     * @return the application context
     * @throws IllegalStateException if the application context has not been set
     */
    public static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext has not been set yet");
        }
        return applicationContext;
    }

    /**
     * Returns a bean of the specified type.
     *
     * @param <T>       the type of the bean
     * @param beanClass the class of the bean
     * @return the bean
     * @throws BeansException if the bean could not be found or created
     */
    public static <T> T getBean(Class<T> beanClass) throws BeansException {
        return getApplicationContext().getBean(beanClass);
    }

    /**
     * Returns a bean with the specified name and type.
     *
     * @param <T>       the type of the bean
     * @param name      the name of the bean
     * @param beanClass the class of the bean
     * @return the bean
     * @throws BeansException if the bean could not be found or created
     */
    public static <T> T getBean(String name, Class<T> beanClass) throws BeansException {
        return getApplicationContext().getBean(name, beanClass);
    }

}