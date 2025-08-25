package az.ailab.lib.messaging.core.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing supported serialization formats used for message payload encoding.
 *
 * <p>Each format is associated with a unique {@code byte marker} that serves as a compact identifier
 * in serialized binary streams. This allows efficient resolution of payload format during deserialization.</p>
 *
 * <p>Use {@link #fromMarker(byte)} to determine the corresponding format based on the stored marker byte.</p>
 *
 * <h2>Supported Formats</h2>
 * <ul>
 *   <li>{@link #JSON} – Standard plain-text JSON format (currently the <b>only</b> format supported)</li>
 *   <li>{@link #AVRO} – Avro binary format (planned for future support)</li>
 *   <li>{@link #JSON_COMPRESSED} – Compressed JSON using GZIP or similar (planned)</li>
 *   <li>{@link #PROTOBUF} – Protobuf format (planned)</li>
 * </ul>
 *
 * <p><b>Note:</b> At present, only {@link #JSON} is actively supported and handled by the system.
 * Other formats are reserved for future extensibility and will be integrated in upcoming releases.</p>
 *
 * <h2>Default Fallback</h2>
 * If the marker byte does not match any known format, {@link #fromMarker(byte)} will default to {@link #JSON}
 * to prevent runtime deserialization errors.
 *
 * @author tahmazovfarid
 */
@Getter
@RequiredArgsConstructor
public enum SerializationFormat {

    /**
     * Standard JSON serialization.
     */
    JSON((byte) 0x01),

    /**
     * Avro binary serialization (not yet supported).
     */
    AVRO((byte) 0x02),

    /**
     * Compressed JSON (e.g., GZIP) (not yet supported).
     */
    JSON_COMPRESSED((byte) 0x03),

    /**
     * Protobuf serialization (not yet supported).
     */
    PROTOBUF((byte) 0x04);

    private final byte marker;

    /**
     * Resolves the serialization format based on its byte marker.
     *
     * @param marker the marker byte stored in the serialized payload
     * @return corresponding {@code SerializationFormat}, or {@link #JSON} if unknown
     */
    public static SerializationFormat fromMarker(byte marker) {
        for (SerializationFormat format : values()) {
            if (format.marker == marker) {
                return format;
            }
        }
        return JSON; // Default fallback
    }

}