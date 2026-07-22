package app.coaching.application;

import app.coaching.model.CoachingConversation;
import app.coaching.model.CoachingConversationSummary;
import app.coaching.model.CoachingMessage;
import app.coaching.persistence.CoachingRepository;
import app.export.MatchAiExporter;
import app.model.session.MatchSession;

import java.util.List;
import java.util.Objects;

/**
 * Application boundary for explicit coaching persistence.
 *
 * <p>No match is saved unless the user opens it for coaching.</p>
 */
public final class CoachingService {
    public static final String RECONSTRUCTION_SCHEMA = "MTGA_MATCH_V3";

    private final CoachingRepository repository;
    private final MatchAiExporter exporter;

    public CoachingService(CoachingRepository repository, MatchAiExporter exporter) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    public CoachingConversation saveForCoaching(MatchSession match) {
        Objects.requireNonNull(match, "match");
        return repository.saveReconstruction(
                match.matchState().getMatchId(),
                RECONSTRUCTION_SCHEMA,
                exporter.export(match));
    }

    public CoachingMessage saveUserDraft(long conversationId, String content) {
        return repository.appendMessage(conversationId, CoachingMessage.Role.USER, content);
    }

    public CoachingMessage saveMessage(
            long conversationId,
            CoachingMessage.Role role,
            String content) {
        return repository.appendMessage(conversationId, role, content);
    }

    public CoachingConversation conversation(long id) {
        return repository.find(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown coaching conversation " + id));
    }

    public List<CoachingConversationSummary> conversations() {
        return repository.listConversations();
    }
}
