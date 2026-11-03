package uzeduai.be.storage;

import java.io.InputStream;
import java.util.UUID;

public interface StoragePort {

    StoredObject store(UUID tenantId, UUID workspaceId, String folder, String filename, InputStream data, long size);

    InputStream open(String storageKey);

    boolean exists(String storageKey);

    void delete(String storageKey);
}
