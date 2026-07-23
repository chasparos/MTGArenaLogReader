package app.coaching.application;

import app.coaching.model.CoachingConversation;
import app.coaching.model.CoachingConversationSummary;
import app.coaching.model.CoachingMessage;
import app.coaching.model.CoachingGame;
import app.coaching.model.CoachingContext;
import app.coaching.persistence.CoachingRepository;
import app.coaching.persistence.CoachingGameSnapshotCodec;
import app.export.MatchAiExporter;
import app.export.GameTextExporter;
import app.model.session.MatchSession;

import java.util.List;
import java.util.Objects;

/**
 * Application boundary for explicit coaching persistence.
 *
 * <p>No match is saved unless the user opens it for coaching.</p>
 */
public final class CoachingService {
    public static final String RECONSTRUCTION_SCHEMA = "MTGA_MATCH_V4";

    private final CoachingRepository repository;
    private final MatchAiExporter exporter;
    private final GameTextExporter gameTextExporter;
    private final CoachingGameSnapshotCodec gameSnapshotCodec;
    private final ManualCoachingPromptBuilder promptBuilder;
    private final CoachingReferenceTextResolver referenceResolver;

    public CoachingService(CoachingRepository repository, MatchAiExporter exporter) {
        this(repository, exporter, new GameTextExporter(), new CoachingGameSnapshotCodec());
    }

    public CoachingService(
            CoachingRepository repository,
            MatchAiExporter exporter,
            GameTextExporter gameTextExporter) {
        this(repository, exporter, gameTextExporter, new CoachingGameSnapshotCodec());
    }

    public CoachingService(
            CoachingRepository repository,
            MatchAiExporter exporter,
            GameTextExporter gameTextExporter,
            CoachingGameSnapshotCodec gameSnapshotCodec) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        this.gameTextExporter = Objects.requireNonNull(gameTextExporter, "gameTextExporter");
        this.gameSnapshotCodec = Objects.requireNonNull(gameSnapshotCodec, "gameSnapshotCodec");
        this.promptBuilder = new ManualCoachingPromptBuilder();
        this.referenceResolver = new CoachingReferenceTextResolver();
    }

    public CoachingConversation saveForCoaching(MatchSession match) {
        Objects.requireNonNull(match, "match");
        List<CoachingGame> games = match.gameSnapshot().stream()
                .sorted(java.util.Comparator.comparingInt(game -> game.getGameNumber()))
                .map(game -> new CoachingGame(
                        game.getGameNumber(),
                        gameTextExporter.export(game),
                        gameSnapshotCodec.encode(game)))
                .toList();
        return repository.saveReconstruction(
                match.matchState().getMatchId(),
                RECONSTRUCTION_SCHEMA,
                exporter.export(match),
                games);
    }

    public app.model.session.GameModel richGame(CoachingGame game) {
        Objects.requireNonNull(game, "game");
        return gameSnapshotCodec.decode(game.richSnapshot());
    }


    public String manualPrompt(
            CoachingConversation conversation,
            app.replay.GameView.CoachingScope scope,
            Integer gameNumber,
            java.util.Set<Integer> turns,
            String question) {
        Objects.requireNonNull(conversation, "conversation");
        return promptBuilder.build(
                conversation.reconstruction(),
                scope,
                gameNumber,
                turns,
                question);
    }

    public String resolveReferences(CoachingConversation conversation, String response) {
        Objects.requireNonNull(conversation, "conversation");
        return referenceResolver.resolve(conversation.reconstruction(), response);
    }

    public CoachingMessage saveUserDraft(long conversationId, String content) {
        return repository.appendMessage(conversationId, CoachingMessage.Role.USER, content);
    }

    public CoachingMessage saveUserDraft(
            long conversationId,
            String content,
            CoachingContext context) {
        return repository.appendMessage(
                conversationId,
                CoachingMessage.Role.USER,
                content,
                context);
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
