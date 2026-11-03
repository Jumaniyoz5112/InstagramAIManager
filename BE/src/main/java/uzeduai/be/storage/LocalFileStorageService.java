package uzeduai.be.storage;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalFileStorageService implements StoragePort {

    private final Path root;

    public LocalFileStorageService(StorageProperties properties) throws IOException {
        this.root = Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public StoredObject store(UUID tenantId, UUID workspaceId, String folder, String filename, InputStream data, long size) {
        String safeFolder = sanitize(folder);
        String safeName = sanitize(filename);
        String key = tenantId + "/" + workspaceId + "/" + safeFolder + "/" + UUID.randomUUID() + "-" + safeName;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject(key, Files.size(target));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store object: " + key, e);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open object: " + storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete object: " + storageKey, e);
        }
    }

    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "file";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
