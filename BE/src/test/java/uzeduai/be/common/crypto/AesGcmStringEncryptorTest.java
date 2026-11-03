package uzeduai.be.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmStringEncryptorTest {

    @Test
    void roundTripsSecretValues() {
        CryptoProperties props = new CryptoProperties();
        props.setSecret("test-only-crypto-key-instagram-ai-manager-32b");
        AesGcmStringEncryptor encryptor = new AesGcmStringEncryptor(props);

        String cipher = encryptor.encrypt("ig-access-token-value");
        assertThat(cipher).isNotEqualTo("ig-access-token-value");
        assertThat(encryptor.decrypt(cipher)).isEqualTo("ig-access-token-value");
    }

    @Test
    void rejectsShortSecret() {
        CryptoProperties props = new CryptoProperties();
        props.setSecret("too-short");
        assertThatThrownBy(() -> new AesGcmStringEncryptor(props))
                .isInstanceOf(IllegalStateException.class);
    }
}
