package vn.weathergis.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Token generation and verifier hashing (contract §2.2/§3.1, D2): 256-bit SecureRandom token,
 * base64url without padding (43 chars), server stores only the SHA-256 hex verifier.
 */
class ChatTokenServiceTest {

    private final ChatTokenService service = new ChatTokenService();

    @Test
    void generatedTokenIs43CharBase64UrlWithoutPadding() {
        for (int i = 0; i < 200; i++) {
            String token = service.generateToken();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        }
    }

    @Test
    void generatedTokensAreUniqueAcrossLargeSample() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            tokens.add(service.generateToken());
        }
        assertThat(tokens).hasSize(10_000);
    }

    @Test
    void hashIs64CharLowercaseHex() {
        assertThat(service.hash("abc")).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void hashMatchesKnownSha256Vector() {
        assertThat(service.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashCoversBase64UrlDashAndUnderscoreCharacters() {
        assertThat(service.hash("a-_b"))
                .isEqualTo("0eb1004d621a301d41475a7bc842ef8d5506950f9f1c41d5e1c665e4af6773ec");
    }

    @Test
    void hashOfGeneratedTokenIsStableAcrossCalls() {
        String token = service.generateToken();
        assertThat(service.hash(token)).isEqualTo(service.hash(token));
    }
}
