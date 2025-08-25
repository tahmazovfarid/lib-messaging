package az.ailab.lib.messaging.infra.serializer;

public interface EventSerializer {

    byte[] serialize(Object event);

    <T> T deserialize(byte[] data, Class<T> eventClass);

    boolean canHandle(Object event);

    String getStrategyName();

}