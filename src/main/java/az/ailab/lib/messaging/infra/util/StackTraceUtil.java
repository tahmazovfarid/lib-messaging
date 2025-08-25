package az.ailab.lib.messaging.infra.util;

public final class StackTraceUtil {

    private StackTraceUtil() {

    }

    /**
     * Determines the origin of the exception by scanning the stack trace for the first element
     * whose class name starts with the first two segments of the listener method's package.
     * <p>
     * If no matching element is found, returns the first element of the stack trace or a placeholder.
     *
     * @param stack the stack trace elements from the thrown exception
     * @return a string representation of the most relevant stack trace element
     */
    public static String extractOriginException(StackTraceElement[] stack, String relatedPackage) {
        String[] parts = relatedPackage.split("\\.");
        String prefix = parts.length >= 2
                ? parts[0] + "." + parts[1]
                : relatedPackage;

        for (StackTraceElement el : stack) {
            if (el.getClassName().startsWith(prefix)) {
                return el.toString();
            }
        }
        return stack.length > 0 ? stack[0].toString() : "<no-stack-trace>";
    }

}
