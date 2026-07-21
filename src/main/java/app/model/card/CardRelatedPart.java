package app.model.card;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/** A related Scryfall card/token/emblem object from all_parts. */
@Data
/**
 * Represents CardRelatedPart in the card-information model shared by enrichment, projection, and replay presentation.
 *
 * <p>It preserves Arena identity while optionally carrying Scryfall metadata through the processing pipeline.</p>
 *
 * <p>External metadata may enrich this type but must not overwrite contradictory Arena-observed facts.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the shared card-information model, preserving Arena identity while carrying optional enrichment metadata.</p>
 */
public class CardRelatedPart {
    private String id;
    private String component;
    private String name;
    @SerializedName("type_line") private String typeLine;
    private String uri;
}
