package kr.bi.go_to;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GotoApplication {

    public static void main(String[] args) {
        loadAwsSystemPropertiesFromDotenv();
        SpringApplication.run(GotoApplication.class, args);
    }

    private static void loadAwsSystemPropertiesFromDotenv() {
        Path dotenvPath = findDotenvPath();
        if (dotenvPath == null) {
            return;
        }

        Map<String, String> awsPropertyNames = Map.of(
                "AWS_ACCESS_KEY_ID", "aws.accessKeyId",
                "AWS_SECRET_ACCESS_KEY", "aws.secretAccessKey",
                "AWS_SESSION_TOKEN", "aws.sessionToken",
                "AWS_REGION", "aws.region"
        );

        try {
            for (String line : Files.readAllLines(dotenvPath)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }

                String[] parts = trimmed.split("=", 2);
                String envName = parts[0].trim().replaceFirst("^export\\s+", "");
                String propertyName = awsPropertyNames.get(envName);
                if (propertyName == null || hasText(System.getProperty(propertyName)) || hasText(System.getenv(envName))) {
                    continue;
                }

                String value = unquote(parts[1].trim());
                if (hasText(value)) {
                    System.setProperty(propertyName, value);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load AWS credentials from .env", ex);
        }
    }

    private static Path findDotenvPath() {
        for (Path path : new Path[]{Path.of(".env"), Path.of("backend/.env")}) {
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

}
