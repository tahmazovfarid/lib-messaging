package az.ailab.lib.messaging.core.vo;

import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.NonNull;

@Builder
public record EventRegistrationRequest(@NonNull String mId,
                                       @NonNull String listenerName,
                                       @NonNull Object payload,
                                       long maxAttempt,
                                       long initDelay,
                                       long fixedDelay,
                                       @NonNull TimeUnit timeUnit) {

}
