package az.ailab.lib.messaging.aop;

import az.ailab.lib.messaging.core.EventMessage;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.slf4j.MDC;

public class MessagingAspect {

    private static final ThreadLocal<String> correlationIdHolder = new ThreadLocal<>();

    @Around("@annotation(az.ailab.lib.messaging.annotation.Routing)")
    public Object tracePublishing(ProceedingJoinPoint joinPoint) throws Throwable {
        MDC.put("correlation_id", getCorrelationId());
        
        try {
            return joinPoint.proceed();
        } finally {
            MDC.remove("correlation_id");
        }
    }
    
    @Around("@annotation(az.ailab.lib.messaging.annotation.RabbitEventHandler)")
    public Object traceHandling(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof EventMessage<?> message) {
            MDC.put("message_id", message.id());
            MDC.put("correlation_id", message.correlationId());
        }
        
        try {
            return joinPoint.proceed();
        } finally {
            MDC.clear();
        }
    }

    /**
     * Gets the current correlation ID from the thread context or generates a new one if not present.
     *
     * @return the correlation ID
     */
    private String getCorrelationId() {
        String correlationId = correlationIdHolder.get();

        if (correlationId == null) {
            correlationId = MDC.get("correlation_id");
        }

        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();

            correlationIdHolder.set(correlationId);
        }

        return correlationId;
    }

}