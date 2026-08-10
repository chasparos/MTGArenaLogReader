 A series of inline documents and ideas. May contain inconsistancies and contradictions between "documents"
## Document 1
------------------------ Begin document 1
### Architecture Design Document: In-Memory Synergy Analysis Engine

### 1. Executive Summary

The Synergy Analysis Engine is a lightweight, high-performance, deterministic component designed to run locally within a Java 21 desktop application. It eliminates the need for expensive Large Language Models (LLMs) or heavy graph databases (like Neo4j) by leveraging Magic: The Gathering’s highly structured rule text (*Rulesese*). By combining Regex-based intent extraction (Symbolic AI) with an In-Memory Knowledge Graph, the engine tokenizes cards into Triggers, Conditions, and Effects, and maps their relationships dynamically with weighted scoring. 

### 2. Core Architecture Components

### A. Data Layer (Java 21 Records)

To maximize memory efficiency and immutability under a desktop footprint, cards and their relations are modeled using Java 21 records. 

java

public record CardTag(String name, TagType type) {}

public enum TagType {
TRIGGER,    // e.g., ETB, DIES, CAST, SACRIFICE
EFFECT,     // e.g., GAIN_LIFE, DRAW, BLINK, DESTROY, CREATE_TOKEN
MODIFIER,   // e.g., TYPE_CHANGER (Ygra, Eater of All)
SUBTYPE     // e.g., FOOD, ARTIFACT, CREATURE, ELF
}

public record MtgCard(
String id,
String name,
List<String> types,
List<CardTag> tags
) {}

Använd koden med försiktighet.

### B. In-Memory Knowledge Graph

The engine indexes cards using multi-mappers inside RAM, consuming less than 50MB for the entire MTG card history. Relationships are scored dynamically using modern pattern matching. 

java

import java.util.*;

public class SynergyGraph {
private final Map<CardTag, Set<String>> tagToCardIds = new HashMap<>();
private final Map<String, MtgCard> cardsIndex = new HashMap<>();

    public void registerCard(MtgCard card) {
        cardsIndex.put(card.id(), card);
        for (CardTag tag : card.tags()) {
            tagToCardIds.computeIfAbsent(tag, k -> new HashSet<>()).add(card.id());
        }
    }

    /**
     * Dynamically resolves link weights using Java 21 Pattern Matching for Switch
     */
    public double calculateLinkStrength(CardTag tag, MtgCard targetCard) {
        return switch (tag.type()) {
            case TRIGGER -> targetCard.tags().stream().anyMatch(t -> t.type() == TagType.EFFECT) ? 1.0 : 0.2;
            case MODIFIER -> targetCard.types().contains("Creature") ? 0.8 : 0.4;
            default -> 0.3;
        };
    }
    
    public MtgCard getCard(String id) { return cardsIndex.get(id); }
    public Map<CardTag, Set<String>> getTagToCardIds() { return tagToCardIds; }
}

Använd koden med försiktighet.

### C. Rule Parsing & Tokenization (Symbolic AI)

Instead of semantic LLM parsing, standard Java Pattern compilation extracts explicit card text templates deterministically on a millisecond scale. 

java

import java.util.regex.*;
import java.util.ArrayList;
import java.util.List;

public class CardParser {
private static final Pattern ETB_PATTERN = Pattern.compile(
"(?i)whenever.*enters the battlefield"
);
private static final Pattern TYPE_MODIFIER_PATTERN = Pattern.compile(
"(?i)are (\\w+) (\\w+) in addition to their other types"
);

    public static List<CardTag> parseText(String oracleText) {
        List<CardTag> tags = new ArrayList<>();

        if (ETB_PATTERN.matcher(oracleText).find()) {
            tags.add(new CardTag("ETB", TagType.TRIGGER));
        }

        Matcher modifierMatcher = TYPE_MODIFIER_PATTERN.matcher(oracleText);
        if (modifierMatcher.find()) {
            tags.add(new CardTag(modifierMatcher.group(1).toUpperCase(), TagType.SUBTYPE));
            tags.add(new CardTag(modifierMatcher.group(2).toUpperCase(), TagType.SUBTYPE));
            tags.add(new CardTag("TYPE_CHANGER", TagType.MODIFIER));
        }

        return tags;
    }
}

Använd koden med försiktighet.

### 3. Synergy Traversal Mechanics

When a user selects multiple input cards, a multi-source Breadth-First Search (BFS) or intersection lookup executes using Java's Streams API. This operation is designed to run asynchronously inside **Java 21 Virtual Threads** to keep the desktop UI fluid and highly responsive. 

java

import java.util.*;

public class SynergyEngine {
private final SynergyGraph graph;

    public SynergyEngine(SynergyGraph graph) {
        this.graph = graph;
    }

    public record SynergyResult(String cardName, double score) {}

    public List<SynergyResult> findSynergies(List<String> inputCardIds, int limit) {
        Map<String, Double> scores = new HashMap<>();

        for (String id : inputCardIds) {
            MtgCard card = graph.getCard(id);
            if (card == null) continue;

            for (CardTag tag : card.tags()) {
                Set<String> relatedCardIds = graph.getTagToCardIds().getOrDefault(tag, Collections.emptySet());
                
                for (String relatedId : relatedCardIds) {
                    if (inputCardIds.contains(relatedId)) continue; 
                    
                    MtgCard relatedCard = graph.getCard(relatedId);
                    double strength = graph.calculateLinkStrength(tag, relatedCard);
                    
                    scores.merge(relatedCard.name(), strength, Double::sum);
                }
            }
        }

        return scores.entrySet().stream()
            .map(e -> new SynergyResult(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingDouble(SynergyResult::score).reversed())
            .limit(limit)
            .toList();
        }
}

Använd koden med försiktighet.

### 4. Key Strategic Advantages

* **Zero Infrastructure Overhead:** Run completely client-side without spinning up database servers or calling cloud-based AI endpoints.
* **Pre-compiled Graph Assets:** Tokenization can be performed once on the development pipeline. The output graph can then be serialized into a slim JSON file packed directly inside the client application binary, enabling immediate, near-instant load times on startup.
* **Local Inventory Integration:** The output of findSynergies can be trivially cross-referenced against the player's parsed MTGArena log data to instantly filter by cards "Owned", "Missing", or "Craftable via Wildcards".
------------------------ End document 1

## Document 2

------------------------ Begin document 2

Building an In-Memory Knowledge Graph changes the entire goal. You do not need to generate complex Java code that alters game states. Instead, you need to parse MTG "Ruleese" into structural entities, predicates, and properties that represent how cards interact. [1, 2]
To make this highly maintainable and optimized for semantic search, you should parse Ruleese into a strongly-typed Java Semantic Graph rather than a raw, hardcoded graph.
Here is the blueprint for mapping Ruleese to a maintainable Java-based Knowledge Graph.
------------------------------
## 1. The Core Graph Schema
Your Java code should model the rules text as an object graph of Nodes (Entities/Concepts) and Edges (Relationships/Predicates). [3, 4, 5, 6]
## Core Nodes

* Card: The root object.
* Zone: BATTLEFIELD, GRAVEYARD, HAND, LIBRARY.
* Action: SACRIFICE, DESTROY, DRAW, DISCARD, COUNTER.
* Type/Subtype: CREATURE, ELF, ARTIFACT, ENCHANTMENT.
* Variable: POWER, TOUGHNESS, MANA_VALUE, LIFE_TOTAL. [7, 8, 9, 10, 11]

## Core Edges (The Synergies)

* MUTATES_PROPERTY (e.g., "+1/+1 Counters")
* TRIGGERS_ON (e.g., "Whenever an Elf enters...")
* COSTS_RESOURCE (e.g., "Sacrifice a creature:")
* TARGETS_ENTITY (e.g., "Destroy target Artifact") [12]

------------------------------
## 2. Lexing Ruleese to Graph Predicates
Instead of a heavy language compiler (like ANTLR), you can use a Tokeniser with a Central Pattern Repository (Regex + Named Capture Groups). This is because you only care about extracting nouns and verbs to link nodes together.
You can organize your pattern repository by Interaction Triggers and Interaction Effects.
## Pattern A: The Trigger Extractor

* Ruleese: "Whenever an Artifact enters the battlefield under your control..."
* Pattern: (?i)whenever\s+(?:an?|target)\s+(?<Subject>[\w\s]+)\s+enters\s+the\s+battlefield
* Graph Output: Create a TriggerNode. Link Card → TRIGGERS_ON → Artifact_Enters_Event.

## Pattern B: The Cost Extractor

* Ruleese: "{T}, Sacrifice a creature: Draw a card."
* Pattern: (?i)Sacrifice\s+(?:an?|target)\s+(?<CostItem>[\w\s]+):
* Graph Output: Link Card → COSTS_RESOURCE → Creature_Concept.

## Pattern C: The Modifier Extractor

* Ruleese: "Goblins you control get +1/+1."
* Pattern: (?i)(?<Subject>[\w\s]+)\s+you\s+control\s+get\s+(?<Modifier>[\+\-]\d+\/[\+\-]\d+)
* Graph Output: Link Card → MUTATES_PROPERTY → Goblin_Subtype.

------------------------------
## 3. Maintainable Java Architecture (No Code Generation)
Do not auto-generate new .java files for every card. Instead, generate an In-Memory Configurator Pattern. Your engine should read the card text, run it through the pattern repository, and build an anonymous structural graph using Java Records.
## Example of the In-Memory Java Structure

// Immutable records keep the graph memory-efficient and thread-safepublic record CardNode(String name, List<GraphEdge> edges) {}public record GraphEdge(Predicate predicate, Node targetNode) {}
public enum Predicate {
TRIGGERS_ON,
COSTS_RESOURCE,
BENEFITS_FROM,
COUNTERS_ACTION,
MODIFIES_STAT
}

## How the Graph Resolves Synergy (The Main Benefit)
By standardizing Ruleese into these predicates, discovering synergies becomes a simple graph traversal.
For example, if you want to find cards that synergize with Aristocrats (Sacrifice decks):

1. Card A (Sacrifice Outlet): Has edge COSTS_RESOURCE → Creature_Node.
2. Card B (Payoff): Has edge TRIGGERS_ON → Creature_Dies_Node.
3. The Deck Planner Query: "Find all cards where CardA.COSTS_RESOURCE matches CardB.TRIGGERS_ON input condition."

The graph immediately links them together as a Synergy Pair without needing a rules engine to "play" the game. [13]
------------------------------
## 4. Alternative: Graph Databases
If you do not want to manage memory pointers manually in raw Java, look at [Neo4j Embedded](https://neo4j.com/docs/java-reference/current/) or [TinkerPop/JanusGraph](https://janusgraph.org/). You can parse the text using your Java pattern repository, then stream the data directly into a local Graph engine via Cypher queries: [14]

// How the parsed text looks inside a graph query
CREATE (c:Card {name: "Blood Artist"})
CREATE (e:Event {type: "Creature_Dies"})
CREATE (c)-[:TRIGGERS_ON]->(e)

------------------------ End document 2

## Document 3
------------------------ Begin document 3
For a data analysis of card text (Oracle text), these terms are crucial because they serve as syntactic anchors. They define exactly how a card functions and can be easily parsed using regular expressions (Regex) or string matching to categorize cards into functional groups (e.g., triggers, replacements, costs).Triggered Abilities (The "When" Words)Triggered abilities always start with one of three specific words. Identifying these allows you to isolate cards that react to game events."When [event]": Used for one-time, discrete events (e.g., "When this creature enters the battlefield")."Whenever [event]": Used for events that can happen multiple times (e.g., "Whenever you cast a spell")."At [time/phase]": Used for turn-based structure and phases (e.g., "At the beginning of your upkeep", "At the beginning of combat").Static & Replacement Effects (The "Modifier" Words)These words alter the rules of the game, modify characteristics, or replace one event with another without using the stack."Instead": The universal indicator of a replacement effect. Essential for finding copy, replacement, or prevention logic (e.g., "If you would draw a card, mill two cards instead")."As long as": Defines a conditional static ability or continuous modifier (e.g., "As long as it's your turn, this creature has first strike")."As [event]": Used for choices made during the resolution of a permanent entering the battlefield, which do not use the stack (e.g., "As this enters the battlefield, choose a color")."Doesn't/Don't": Modifies standard game rules or restrictions (e.g., "Creatures you control don't untap during your untap step").Activated Abilities & Costs (The Syntax Patterns)Activated abilities always follow a strict structural template: [Cost] : [Effect].":" (The Colon): This is the single most important punctuation mark in MTG data analysis. Anything before the colon is a cost; anything after is the effect."Pay [X]": Indicates a resource cost, usually life, energy, or mana (e.g., "Pay 2 life:")."Sacrifice": Indicates a permanent-based cost or specific removal effect (e.g., "Sacrifice a creature:")."Tap" / "Untap": Often represented by symbols ({T} / {Q} in data strings), indicating a state-change cost.Targets and Choice Restrictions (The "Filter" Words)These words dictate what a spell or ability can legally interact with. They are vital for identifying single-target removal, mass wipes, or modal spells."Target": The legal definition of a targeted effect. If this word is missing, the effect does not target (important for mechanics like Hexproof)."Up to [number]": Indicates flexibility in targeting, including choosing zero targets."Choose one —": The universal indicator of a modal card (e.g., Charms, Commands). In raw data, this is usually followed by bullet points or dash separators."Each" / "All": Indicators of global or symmetrical effects (e.g., "Each player loses 1 life", "Destroy all creatures")."You control" / "An opponent controls": Defines perspective and symmetry restrictions.Zone Changes (The "Location" Words)MTG is a game of moving cards between distinct zones. Tracking these words helps you categorize cards by mechanical archetypes (e.g., Reanimator, Mill, Ramp)."Enters the battlefield": (Often abbreviated as ETB in community shorthand, but always spelled out on the card)."Dies": Specifically means "is put into a graveyard from the battlefield" for creatures."Put into a graveyard from anywhere": Used to catch discard or mill triggers rather than just field deaths."Exile": Moving a card to the face-up (usually) removed-from-game zone."Reveal": Hidden information being made public.Pro-Tip for Data Analysis: The Keyword MechanicsWhile the words above represent the structural foundation of the rules, don't forget Keyword Abilities (e.g., Flying, Trample, Haste, Ward, Scry).In modern MTG JSON data sets (like MTGJSON), these are often broken out into their own data arrays, but if you are parsing raw text, remember that keywords are usually standalone words separated by commas or newlines, occasionally followed by a number (e.g., Ward 2, Scry 1).
------------------------ End document 3

