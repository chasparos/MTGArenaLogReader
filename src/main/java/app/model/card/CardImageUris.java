package app.model.card;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
/**
 * Represents CardImageUris in the card-information model shared by enrichment, projection, and replay presentation.
 *
 * <p>It preserves Arena identity while optionally carrying Scryfall metadata through the processing pipeline.</p>
 *
 * <p>External metadata may enrich this type but must not overwrite contradictory Arena-observed facts.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the shared card-information model, preserving Arena identity while carrying optional enrichment metadata.</p>
 */
public class CardImageUris {
    private String small;
    private String normal;
    private String large;
    private String png;
    @SerializedName("art_crop") private String artCrop;
    @SerializedName("border_crop") private String borderCrop;

    public String preferredPreviewUrl() {
        if (normal != null && !normal.isBlank()) return normal;
        if (small != null && !small.isBlank()) return small;
        if (large != null && !large.isBlank()) return large;
        if (png != null && !png.isBlank()) return png;
        return null;
    }
}
