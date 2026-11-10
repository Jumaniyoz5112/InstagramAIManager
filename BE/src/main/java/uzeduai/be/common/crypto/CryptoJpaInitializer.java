package uzeduai.be.common.crypto;

import org.springframework.stereotype.Component;

/**
 * Ensures Hibernate-instantiated {@link EncryptedStringConverter} instances
 * (created via {@code @Convert(converter=...)}) share the Spring-managed encryptor.
 */
@Component
public class CryptoJpaInitializer {

    public CryptoJpaInitializer(AesGcmStringEncryptor encryptor) {
        EncryptedStringConverter.setEncryptor(encryptor);
    }
}
