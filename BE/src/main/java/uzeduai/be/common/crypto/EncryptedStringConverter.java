package uzeduai.be.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static AesGcmStringEncryptor encryptor;

    public EncryptedStringConverter(AesGcmStringEncryptor encryptor) {
        EncryptedStringConverter.encryptor = encryptor;
    }

    /** JPA may instantiate without Spring; fall back to the injected static. */
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
