package app.model;


import app.model.card.CardInfo;
import app.model.log.ModelObject;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
/**
 * Carries InformationBundle between adjacent stages of the log-processing pipeline.
 *
 * <p>It is a transport model rather than an owner of parsing, projection, or presentation behavior.</p>
 *
 * <p>The type should remain focused on data required at that architectural boundary.</p>
 * <p><strong>Architectural role:</strong> This type is a transport model at an explicit pipeline boundary and does not own parsing, projection, or presentation behavior.</p>
 */
public class InformationBundle implements ModelObject {
    private final Map<Long, CardInfo> cards = new LinkedHashMap<>();
    private final Map<String, CardInfo> relatedCards = new LinkedHashMap<>();
}
