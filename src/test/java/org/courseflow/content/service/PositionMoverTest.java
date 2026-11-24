package org.courseflow.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.courseflow.common.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;

class PositionMoverTest {

    private static class Item {
        int position;

        Item(int position) {
            this.position = position;
        }
    }

    private void move(List<Item> items, Item chosen, int target, int max, Runnable flush) {
        PositionMover.move(chosen, chosen.position, target, items, (i) -> i.position, (i, p) -> i.position = p,
                () -> max, flush);
    }

    private void assertUnique(List<Item> items) {
        assertEquals(items.size(), items.stream().map((i) -> i.position).distinct().count());
    }

    @Test
    void movesDownWithoutTransientDuplicates() {
        Item a = new Item(1);
        Item b = new Item(2);
        Item c = new Item(3);
        var items = List.of(a, b, c);
        move(items, a, 3, 3, () -> assertUnique(items));

        assertEquals(List.of(3, 1, 2), items.stream().map((i) -> i.position).toList());
    }

    @Test
    void movesUpWithoutTransientDuplicates() {
        Item a = new Item(1);
        Item b = new Item(2);
        Item c = new Item(3);
        var items = List.of(a, b, c);
        move(items, c, 1, 3, () -> assertUnique(items));

        assertEquals(List.of(2, 3, 1), items.stream().map((i) -> i.position).toList());
    }

    @Test
    void noOpDoesNotFlush() {
        Item a = new Item(1);
        Runnable flush = mock(Runnable.class);
        move(List.of(a), a, 1, 1, flush);

        verifyNoInteractions(flush);
    }

    @Test
    void movesToFreePosition() {
        Item a = new Item(1);
        Runnable flush = mock(Runnable.class);
        move(List.of(a), a, 5, 1, flush);

        assertEquals(5, a.position);
        verify(flush).run();
    }

    @Test
    void rejectsPositionOverflow() {
        Item a = new Item(1);
        Item b = new Item(2);
        Runnable flush = mock(Runnable.class);

        assertThrows(BusinessConflictException.class, () -> move(List.of(a, b), a, 2, Integer.MAX_VALUE, flush));
        assertEquals(1, a.position);
        verifyNoInteractions(flush);
    }
}
