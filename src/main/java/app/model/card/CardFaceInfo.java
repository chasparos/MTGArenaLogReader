package app.model.card;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * Represents CardFaceInfo in the card-information model shared by enrichment, projection, and replay presentation.
 *
 * <p>It preserves Arena identity while optionally carrying Scryfall metadata through the processing pipeline.</p>
 *
 * <p>External metadata may enrich this type but must not overwrite contradictory Arena-observed facts.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the shared card-information model, preserving Arena identity while carrying optional enrichment metadata.</p>
 */
public class CardFaceInfo {
    private String name;
    @SerializedName("mana_cost") private String manaCost;
    @SerializedName("type_line") private String typeLine;
    @SerializedName("oracle_text") private String oracleText;
    private String power;
    private String toughness;
    private String loyalty;
    private String defense;
    private List<String> colors = new ArrayList<>();
    @SerializedName("color_indicator") private List<String> colorIndicator = new ArrayList<>();
    @SerializedName("image_uris") private CardImageUris imageUris;
    @SerializedName("artist") private String artist;
    @SerializedName("artist_id") private String artistId;
    @SerializedName("illustration_id") private String illustrationId;
    private String watermark;
    @SerializedName("flavor_text") private String flavorText;
    @SerializedName("flavor_name") private String flavorName;

    public String previewImageUrl() {
        return imageUris == null ? null : imageUris.preferredPreviewUrl();
    }
}
