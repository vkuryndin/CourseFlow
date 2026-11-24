package org.courseflow.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/** После стандартной инициализации Spring и системного администратора. */
@Component
@Profile("!test")
public class DemoDataInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(DemoDataInitializer.class);
    private final DemoDataService service;

    public DemoDataInitializer(DemoDataService service) { this.service = service; }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (service.isInitialized()) return;
        List<DemoAccounts.Account> accounts = DemoAccounts.create();
        Path credentials = null;
        try {
            Path directory = Path.of(System.getProperty("user.home"), ".courseflow");
            Files.createDirectories(directory);
            credentials = Files.getFileStore(directory).supportsFileAttributeView("posix")
                    ? Files.createTempFile(directory, "demo-credentials-", ".txt",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(directory, "demo-credentials-", ".txt");
            // Файл заранее записывается, чтобы ошибка файловой системы не оставила аккаунты без паролей.
            StringBuilder text = new StringBuilder("CourseFlow — демонстрационные учётные записи\n"
                    + "Не передавайте этот файл и не добавляйте его в Git.\n"
                    + "Последние три студента деактивированы.\n\n");
            for (DemoAccounts.Account account : accounts) {
                text.append(account.roles()).append(" | ").append(account.name()).append(" | ")
                        .append(account.email()).append(" | ").append(account.password()).append('\n');
            }
            Files.writeString(credentials, text.toString(), StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            if (service.seed(accounts)) {
                LOG.info("Демонстрационные данные CourseFlow созданы. Учётные данные: {}", credentials.toAbsolutePath());
            } else {
                Files.deleteIfExists(credentials);
            }
        } catch (IOException | RuntimeException exception) {
            if (credentials != null) {
                try { Files.deleteIfExists(credentials); }
                catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            }
            throw new IllegalStateException("Не удалось инициализировать демонстрационные данные CourseFlow", exception);
        }
    }
}
