package az.ailab.lib.messaging.config;

import java.lang.management.ManagementFactory;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.env.ConfigurableEnvironment;

@Slf4j
public class ProxyClassGenerationChecker implements SpringApplicationRunListener {

    private static final String JAVA_VERSION_PROPERTY = "java.version";
    private static final String JAVA_8_PREFIX = "1.8";
    private static final String JAVA_16_OPTION = "--illegal-access=permit";
    private static final String JAVA_17_OPTION = "--add-opens=java.base/java.lang=ALL-UNNAMED";

    public ProxyClassGenerationChecker(SpringApplication application, String[] args) {

    }

    @Override
    public void environmentPrepared(ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
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

    private int parseMajorVersion(String version) {
        try {
            return version.startsWith(JAVA_8_PREFIX) ?
                    8 : Integer.parseInt(version.split("\\.")[0]);
        } catch (NumberFormatException e) {
            log.warn("Unable to parse Java version: {}", version);
            return 0;
        }
    }

    private void verifyJvmOptionOrExit(String requiredOption, int javaVersion) {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        boolean hasOption = jvmArgs.stream().anyMatch(arg -> arg.contains(requiredOption));

        if (!hasOption) {
            log.error("Required JVM option for Java {} is missing: {}", javaVersion, requiredOption);
            log.error("➡ Add this to your VM options and restart the app: {}", requiredOption);
            log.error("➡ Example: java {} -jar your-app.jar", requiredOption);
            System.exit(1);
        } else {
            log.debug("Proxy generation succeeded for Java {} (option present)", javaVersion);
        }
    }

}