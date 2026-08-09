package app.collection;

import java.util.Collection;
import java.util.Map;

/** The complete application-facing boundary for real collection ownership. */
public interface CollectionOwnership {
    /** Returns every requested ID mapped to -1 while unknown, or its known copies including zero. */
    Map<Long, Integer> getCopiesOwned(Collection<Long> arenaIds);
}
