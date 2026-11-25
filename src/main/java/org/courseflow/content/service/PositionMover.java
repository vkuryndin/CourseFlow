package org.courseflow.content.service;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;
import org.courseflow.common.exception.BusinessConflictException;

/** Move within a locked parent, preserving unique positions at every SQL flush. */
final class PositionMover {
  private PositionMover() {}

  static <T> void move(
      T selected,
      int oldPosition,
      int newPosition,
      List<T> affected,
      ToIntFunction<T> position,
      BiConsumer<T, Integer> setPosition,
      IntSupplier maxPosition,
      Runnable flush) {
    if (oldPosition == newPosition) return;
    // The range includes the selected entity. Exclude it before changing positions.
    List<T> others =
        affected.stream().filter(item -> position.applyAsInt(item) != oldPosition).toList();
    if (!others.isEmpty()) {
      int max = maxPosition.getAsInt();
      // Reserve a free position outside both the occupied range and the target.
      int upper = Math.max(max, newPosition);
      if (upper == Integer.MAX_VALUE) {
        throw new BusinessConflictException("Cannot reorder: position limit reached");
      }
      setPosition.accept(selected, upper + 1);
      flush.run();
      if (oldPosition < newPosition) {
        for (T item : others) {
          setPosition.accept(item, position.applyAsInt(item) - 1);
          flush.run();
        }
      } else {
        for (int i = others.size() - 1; i >= 0; i--) {
          T item = others.get(i);
          setPosition.accept(item, position.applyAsInt(item) + 1);
          flush.run();
        }
      }
    }
    setPosition.accept(selected, newPosition);
    flush.run();
  }
}
