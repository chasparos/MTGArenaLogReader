## The vision
I want the user to be able to find cards that fit together. Explore possible build arounds
and trace interactions between cards. I imagine a user browsing the cards in their collection
and finding a card they like, or two (or more) that seems to work together.
I the want to give those cards as input to an analysis "engine" and find and rate cards that
supports those cards.
To do this we should define a set of interactions and construct a traversable search graph where we have defined
groups of cards and made interaction relationships between the groups.

This would require som form of categorisation of cards to be able to group them.

Lets think of this from the perspective of an example:

If we have a card that cares about creatures entering the battlefield.
That should have an interaction link to the group "all-creatures".  
The link to "creatures with ETB" should represent a stronger link.
Creatures that have a repeatable ETB. like graveyard recurence should be stronger still.
Other spells that recur creatures, blinks them or that kind of thing, should also be linked to the card.

To make this type of analysis/index of all cards and all possible interactions would be, if not
impossible, extremly costly and complex. So lets define a subset of common MTG themes.  
Recursion, milling, card advantage, tribal, burn, aggro, value and so on.
We also want a list of tags/relations/groups of "cards that cares about thing X" where X can be
things like ETB, creatures dying, Tribes, card type, permanent count.
Cards that change other cards like "permanents you own have: X and are Y".
Ygra for example makes all creatures food artifacts. Any card that cares about the number of artifacts
or when artifacts leave the battle field become stronger in conjuction with Ygra.

The dream would be to ask about a card and get back a set of cards that become stronger width the card or
makes tha card stronger.

The language of magic cards are pretty well defined so there should be a way to "tokenize" the rules texts
without building an entire LLM.
What kind of simpler AI could we implement to generate this kind of analysis graph?

I have some quick and dirty research in /docs/DPP2 - research.md file  

## Manual discovery/search/filters
The pure click filters in the current filter tab are good but incomplete. The tag cloud that is present at the moment 
should take advantage of the analysis graph and publish a "manual search graph traversal" functionality rather 
than be a pure/simple "contains string" matcher. One problem with the current approach is that we categorise 
many tags that are just flavor text and points to just one card in the entire catalog. The ablilty "Excalibur - Equip {1} add counter X" 
Should not lead to a filter tag "Excalibur" pointing to just one card. That card should end up under "Equipment" and "Counter:X" tags.

We should also keep tags/groups for things like: ramp, removal, "cheat cards into play" ie without paying its mana cost or 
return to battlefield. 
We should also define a basic query language against the analysis graph.
where we can define And,Or,Not, with sub expressions with ().

We should then use any Filtering UI to compile a search expression.
That gives us freedom to design more than one kind of filter ui.



