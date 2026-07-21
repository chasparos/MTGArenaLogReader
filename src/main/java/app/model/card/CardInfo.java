package app.model.card;


import app.model.log.ModelObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scryfall Card object used throughout the replay domain model.
 *
 * The application deliberately keeps a broad, useful subset of the Scryfall
 * response instead of reducing a card to only its Arena ID and name. This lets
 * replay events carry images, Oracle text, faces, type information, links, and
 * analysis metadata without another lookup.
 */
@Data
/**
 * Represents CardInfo in the card-information model shared by enrichment, projection, and replay presentation.
 *
 * <p>It preserves Arena identity while optionally carrying Scryfall metadata through the processing pipeline.</p>
 *
 * <p>External metadata may enrich this type but must not overwrite contradictory Arena-observed facts.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the shared card-information model, preserving Arena identity while carrying optional enrichment metadata.</p>
 */
public class CardInfo implements ModelObject {
    private String object;
    private String id;
    @SerializedName("oracle_id") private String oracleId;
    @SerializedName("multiverse_ids") private List<Long> multiverseIds = new ArrayList<>();
    @SerializedName("mtgo_id") private Long mtgoId;
    @SerializedName("mtgo_foil_id") private Long mtgoFoilId;
    @SerializedName("tcgplayer_id") private Long tcgplayerId;
    @SerializedName("cardmarket_id") private Long cardmarketId;
    @SerializedName("arena_id") private Long arenaId;

    private String lang;
    @SerializedName("released_at") private String releasedAt;
    private String uri;
    @SerializedName("scryfall_uri") private String scryfallUri;

    private String name;
    private String layout;
    @SerializedName("highres_image") private Boolean highresImage;
    @SerializedName("image_status") private String imageStatus;
    @SerializedName("image_uris") private CardImageUris imageUris;
    @SerializedName("card_faces") private List<CardFaceInfo> cardFaces = new ArrayList<>();

    @SerializedName("mana_cost") private String manaCost;
    private Double cmc;
    @SerializedName("type_line") private String typeLine;
    @SerializedName("oracle_text") private String oracleText;
    private String power;
    private String toughness;
    private String loyalty;
    private String defense;
    private List<String> colors = new ArrayList<>();
    @SerializedName("color_identity") private List<String> colorIdentity = new ArrayList<>();
    @SerializedName("color_indicator") private List<String> colorIndicator = new ArrayList<>();
    private List<String> keywords = new ArrayList<>();
    @SerializedName("produced_mana") private List<String> producedMana = new ArrayList<>();

    private Map<String, String> legalities = new LinkedHashMap<>();
    private List<String> games = new ArrayList<>();
    private Boolean reserved;
    private Boolean foil;
    private Boolean nonfoil;
    private List<String> finishes = new ArrayList<>();
    private Boolean oversized;
    private Boolean promo;
    private Boolean reprint;
    private Boolean variation;
    @SerializedName("set_id") private String setId;
    private String set;
    @SerializedName("set_name") private String setName;
    @SerializedName("set_type") private String setType;
    @SerializedName("collector_number") private String collectorNumber;
    private Boolean digital;
    private String rarity;
    @SerializedName("flavor_text") private String flavorText;
    @SerializedName("card_back_id") private String cardBackId;
    private String artist;
    @SerializedName("artist_ids") private List<String> artistIds = new ArrayList<>();
    @SerializedName("illustration_id") private String illustrationId;
    @SerializedName("border_color") private String borderColor;
    private String frame;
    @SerializedName("frame_effects") private List<String> frameEffects = new ArrayList<>();
    @SerializedName("security_stamp") private String securityStamp;
    @SerializedName("full_art") private Boolean fullArt;
    private Boolean textless;
    private Boolean booster;
    @SerializedName("story_spotlight") private Boolean storySpotlight;
    @SerializedName("edhrec_rank") private Integer edhrecRank;
    @SerializedName("penny_rank") private Integer pennyRank;

    private Map<String, String> prices = new LinkedHashMap<>();
    @SerializedName("related_uris") private Map<String, String> relatedUris = new LinkedHashMap<>();
    @SerializedName("purchase_uris") private Map<String, String> purchaseUris = new LinkedHashMap<>();
    @SerializedName("all_parts") private List<CardRelatedPart> allParts = new ArrayList<>();

    public String previewImageUrl() {
        if (imageUris != null) {
            String url = imageUris.preferredPreviewUrl();
            if (url != null) return url;
        }
        if (cardFaces != null) {
            for (CardFaceInfo face : cardFaces) {
                String url = face == null ? null : face.previewImageUrl();
                if (url != null) return url;
            }
        }
        return null;
    }

    /** True for a modern, fully-enriched Scryfall cache entry. */
    public boolean hasReplayMetadata() {
        return id != null && !id.isBlank()
                && name != null && !name.isBlank()
                && (typeLine != null || (cardFaces != null && !cardFaces.isEmpty()))
                && previewImageUrl() != null;
    }

    /** Combined rules text for cards whose top-level Oracle text is absent. */
    public String effectiveOracleText() {
        if (oracleText != null && !oracleText.isBlank()) return oracleText;
        if (cardFaces == null || cardFaces.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (CardFaceInfo face : cardFaces) {
            if (face == null || face.getOracleText() == null || face.getOracleText().isBlank()) continue;
            if (!out.isEmpty()) out.append("\n\n");
            if (face.getName() != null && !face.getName().isBlank()) out.append(face.getName()).append("\n");
            out.append(face.getOracleText());
        }
        return out.isEmpty() ? null : out.toString();
    }

    public String effectiveTypeLine() {
        if (typeLine != null && !typeLine.isBlank()) return typeLine;
        if (cardFaces == null || cardFaces.isEmpty()) return null;
        return cardFaces.stream()
                .filter(face -> face != null && face.getTypeLine() != null && !face.getTypeLine().isBlank())
                .map(CardFaceInfo::getTypeLine)
                .reduce((left, right) -> left + " // " + right)
                .orElse(null);
    }
}
