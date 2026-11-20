package uzeduai.be.analysis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uzeduai.be.analysis.client.AiAnalysisClient;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;

import java.util.Set;

@Service
public class SpeechService {

    private static final Logger log = LoggerFactory.getLogger(SpeechService.class);
    static final String DEFAULT_VOICE = "uz-mms";
    private static final Set<String> VOICES = Set.of(DEFAULT_VOICE, "uz-girl");

    private final AiAnalysisClient client;

    public SpeechService(AiAnalysisClient client) {
        this.client = client;
    }

    public byte[] speak(String text, String voiceId) {
        String voice = voiceId == null || voiceId.isBlank() ? DEFAULT_VOICE : voiceId.trim();
        if (!VOICES.contains(voice)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.SPEECH_FAILED);
        }
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.SPEECH_TEXT_REQUIRED);
        }
        if (cleaned.length() > 500) {
            cleaned = cleaned.substring(0, 500);
        }
        try {
            return client.speak(cleaned, voice);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Speech failed: {}", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.SPEECH_FAILED);
        }
    }
}
