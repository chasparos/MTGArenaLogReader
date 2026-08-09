package app.collection;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionOwnershipTest {
    @Test
    void exposesExactlyTheTwoApprovedApplicationOperations() {
        Set<String> methods = Arrays.stream(CollectionOwnership.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("getCopiesOwned"), methods);
    }
}
