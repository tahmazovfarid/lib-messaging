package az.ailab.lib.messaging.annotation;

import az.ailab.lib.messaging.util.ProxyClassGenerationChecker;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ProxyClassGenerationChecker.class)
public @interface CheckProxyClassGeneration {

}
