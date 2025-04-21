package az.ailab.lib.messaging.core;

import az.ailab.lib.messaging.annotation.RabbitEventPublisher;
import az.ailab.lib.messaging.core.proxy.DynamicRabbitProxyFactoryBean;
import az.ailab.lib.messaging.core.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.core.resolver.RoutingKeyResolver;
import java.beans.Introspector;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Registrar that scans for interfaces annotated with {@link az.ailab.lib.messaging.annotation.RabbitEventPublisher}
 * and registers a dynamic proxy bean for each discovered interface. This allows for publishing events
 * to RabbitMQ using an interface-based approach.
 * <p>
 * Usage: Place this registrar in a configuration class via {@code @Import(RabbitPublisherRegistrar.class)}.
 * </p>
 *
 * @author tahmazovfarid
 */
@Slf4j
public class RabbitPublisherRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, BeanFactoryAware {

    private ConfigurableListableBeanFactory beanFactory;
    private Environment environment;

    /**
     * Sets the {@link BeanFactory} and casts it to {@link ConfigurableListableBeanFactory} for internal use.
     *
     * @param beanFactory the Spring BeanFactory injected by the container
     * @throws IllegalArgumentException if the provided beanFactory is not configurable
     */
    @Override
    public void setBeanFactory(@NonNull final BeanFactory beanFactory) {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    /**
     * Sets the {@link Environment} used to resolve properties such as the application name.
     *
     * @param environment the Spring Environment injected by the container
     */
    @Override
    public void setEnvironment(@NonNull final Environment environment) {
        this.environment = environment;
    }

    /**
     * Scans the base package (determined by the importing class metadata) for interfaces annotated with
     * {@link az.ailab.lib.messaging.annotation.RabbitEventPublisher} and registers a proxy bean definition
     * for each one.
     *
     * @param importingClassMetadata metadata of the class that imported this registrar (e.g., the main Spring Boot application class)
     * @param registry               registry used to register new bean definitions
     * @throws BeanCreationException if any proxy fails to be created or registered
     */
    @Override
    public void registerBeanDefinitions(@NonNull final AnnotationMetadata importingClassMetadata,
                                        @NonNull final BeanDefinitionRegistry registry) {
        log.debug("Starting scan for @RabbitEventPublisher interfaces in base package");

        // Create a component scanner to detect independent types (interfaces) annotated with @RabbitEventPublisher
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDef) {
                        return beanDef.getMetadata().isIndependent();
                    }
                };
        scanner.addIncludeFilter(new AnnotationTypeFilter(RabbitEventPublisher.class));

        // Derive base package from the importing class name
        String fullClassName = importingClassMetadata.getClassName();
        String basePackage = fullClassName.substring(0, fullClassName.lastIndexOf('.'));

        // Scan and register each discovered interface
        scanner.findCandidateComponents(basePackage)
                .forEach(beanDef -> {
                    String ifaceName = beanDef.getBeanClassName();
                    try {
                        Class<?> iface = Class.forName(ifaceName);
                        registerPublisherBean(registry, iface);
                        log.debug("Registered publisher bean for interface {}", ifaceName);
                    } catch (ClassNotFoundException e) {
                        log.error("Failed to load interface class {}", ifaceName, e);
                        throw new BeanCreationException("Interface class not found: " + ifaceName, e);
                    }
                });
    }

    /**
     * Registers a single dynamic proxy bean for the given interface.
     *
     * @param registry the bean definition registry
     * @param interfaceClass the interface annotated with {@link az.ailab.lib.messaging.annotation.RabbitEventPublisher}
     * @throws IllegalStateException if a bean with the same name already exists
     */
    private void registerPublisherBean(BeanDefinitionRegistry registry, Class<?> interfaceClass) {
        String beanName = Introspector.decapitalize(interfaceClass.getSimpleName());
        if (registry.containsBeanDefinition(beanName)) {
            throw new IllegalStateException("Bean with name " + beanName + " already exists");
        }

        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(interfaceClass)
                .getRawBeanDefinition();

        beanDefinition.setInstanceSupplier(() -> getProxyObject(interfaceClass));

        registry.registerBeanDefinition(beanName, beanDefinition);

        log.debug("Registered dynamic proxy bean for interface: {}", interfaceClass.getName());
    }

    /**
     * Creates the dynamic proxy instance for the specified interface via
     * {@link az.ailab.lib.messaging.core.proxy.DynamicRabbitProxyFactoryBean}.
     *
     * @param interfaceClass the interface to proxy
     * @return the proxy instance
     * @throws BeanCreationException if the proxy factory fails
     */
    private Object getProxyObject(Class<?> interfaceClass) {
        try {
            DynamicRabbitProxyFactoryBean<?> factory =
                    new DynamicRabbitProxyFactoryBean<>(
                            interfaceClass.getName(),
                            beanFactory.getBean(RoutingKeyResolver.class),
                            beanFactory.getBean(ExchangeNameResolver.class),
                            beanFactory.getBean(RabbitInfrastructure.class),
                            beanFactory.getBean(RabbitTemplate.class),
                            beanFactory.getBean(AmqpAdmin.class),
                            resolveApplicationName()
                    );
            return factory.getObject();
        } catch (Exception ex) {
            throw new BeanCreationException("Failed to create proxy for " + interfaceClass.getName(), ex);
        }
    }

    /**
     * Retrieves the application name from the environment property 'spring.application.name',
     * defaulting to 'unknown' if not set.
     *
     * @return the resolved application name
     */
    private String resolveApplicationName() {
        String appName = environment.getProperty("spring.application.name", "unknown");
        log.debug("Using application name '{}' for source of Event", appName);
        return appName;
    }

}