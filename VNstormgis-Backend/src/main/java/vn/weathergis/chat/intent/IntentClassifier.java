package vn.weathergis.chat.intent;

import java.util.UUID;

/**
 * Deterministic intent classification for anonymous chat. Runs after W5-03 validation/session/rate
 * limit and before any retrieval or Gemini call (contract §2.3). The question is data, never an
 * instruction.
 */
public interface IntentClassifier {

    IntentClassification classify(String message, UUID referencedAlertId);
}
