package az.ailab.lib.messaging.infra.util;

import az.ailab.lib.messaging.core.enums.SerializationFormat;

// Format marker utilities
public class FormatUtils {
    
    public static byte[] addFormatMarker(byte[] data, SerializationFormat format) {
        byte[] result = new byte[data.length + 1];
        result[0] = format.getMarker();
        System.arraycopy(data, 0, result, 1, data.length);
        return result;
    }
    
    public static byte[] removeFormatMarker(byte[] data) {
        if (data.length > 0) {
            byte[] result = new byte[data.length - 1];
            System.arraycopy(data, 1, result, 0, data.length - 1);
            return result;
        }
        return data;
    }
    
    public static SerializationFormat detectFormat(byte[] data) {
        if (data.length > 0) {
            return SerializationFormat.fromMarker(data[0]);
        }
        return SerializationFormat.JSON;
    }

}