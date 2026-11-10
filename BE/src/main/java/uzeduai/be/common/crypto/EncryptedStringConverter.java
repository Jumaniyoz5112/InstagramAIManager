package uzeduai.be.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile AesGcmStringEncryptor encryptor;

    /** Called by {@link CryptoJpaInitializer} so Hibernate-created instances can encrypt. */
    public static void setEncryptor(AesGcmStringEncryptor encryptor) {
        EncryptedStringConverter.encryptor = encryptor;
    }

    public EncryptedStringConverter() {
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return requireEncryptor().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return requireEncryptor().decrypt(dbData);
    }

    private static AesGcmStringEncryptor requireEncryptor() {
        if (encryptor == null) {
            throw new IllegalStateException("EncryptedStringConverter not initialized");
        }
        return encryptor;
    }
}
