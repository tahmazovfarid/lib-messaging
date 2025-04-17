package az.ailab.lib.messaging.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

/**
 * Ensures JVM is properly configured for dynamic proxy generation (especially for Java 16+).
 * Exits the application if required options are missing and proxy generation fails.
 */
@Slf4j
@Component
public class ProxyClassGenerationChecker implements InitializingBean {

    private static final String JAVA_VERSION_PROPERTY = "java.version";
    private static final String JAVA_8_PREFIX = "1.8";
    private static final String JAVA_16_OPTION = "--illegal-access=permit";
    private static final String JAVA_17_OPTION = "--add-opens=java.base/java.lang=ALL-UNNAMED";
    private static final String[] SKIPPED_PROFILES = {"test", "dev"};

    private final Environment environment;

    public ProxyClassGenerationChecker(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (shouldSkip()) {
            log.info("Proxy generation check skipped for profile(s): {}", Arrays.toString(environment.getActiveProfiles()));
            return;
        }

        String javaVersion = System.getProperty(JAVA_VERSION_PROPERTY, "");
        log.debug("Detected Java version: {}", javaVersion);

        int majorVersion = parseMajorVersion(javaVersion);

        if (majorVersion <= 8) {
            log.debug("Java 8 or earlier detected, no special options needed.");
        } else if (majorVersion == 16) {
            verifyJvmOptionOrExit(JAVA_16_OPTION, majorVersion);
        } else if (majorVersion >= 17) {
            verifyJvmOptionOrExit(JAVA_17_OPTION, majorVersion);
        } else {
            log.debug("Java 9-15 detected. Proxy generation should work by default.");
        }
    }

    private boolean shouldSkip() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> Arrays.asList(SKIPPED_PROFILES).contains(p.toLowerCase()));
    }

    private int parseMajorVersion(String version) {
        try {
            // Java 1.8 -> 1, Java 11+ -> 11
            return version.startsWith(JAVA_8_PREFIX)
                    ? 8
                    : Integer.parseInt(version.split("\\.")[0]);
        } catch (NumberFormatException e) {
            log.warn("Unable to parse Java version: {}", version);
            return 0;
        }
    }

    private void verifyJvmOptionOrExit(String requiredOption, int javaVersion) {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        boolean hasOption = jvmArgs.stream().anyMatch(arg -> arg.contains(requiredOption));

        if (!hasOption && !canGenerateProxy()) {
            log.error("Required JVM option for Java {} is missing: {}", javaVersion, requiredOption);
            log.error("Add the following to your VM options and restart: {}", requiredOption);
            log.error("Example: java {} -jar your-app.jar", requiredOption);
            System.exit(1);
        } else {
            log.debug("Proxy generation succeeded for Java {} (with or without explicit option)", javaVersion);
        }
    }

    private boolean canGenerateProxy() {
        try {
            TestInterface proxy = (TestInterface) Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class[]{TestInterface.class},
                    (prx, method, args) -> method.getName().equals("toString") ? "ProxyTest" : null
            );
            proxy.toString();
            return true;
        } catch (Exception e) {
            log.debug("Proxy generation failed: {}", e.getMessage());
            return false;
        }
    }

    private interface TestInterface {}
}