package net.minestom.ca.backends.lazy;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class HashedWheelTimer<T> {
    private final Set<ScheduledTask<T>>[] wheel;
    private final int wheelSize;
    private int currentTick = 0;
    private final AtomicLong taskIdGen = new AtomicLong();

    public HashedWheelTimer(int wheelSize) {
        this.wheelSize = wheelSize;
        this.wheel = new Set[wheelSize];
        Arrays.setAll(wheel, _ -> new HashSet<>());
    }

    public void schedule(Supplier<T> task, int delayTicks) {
        int scheduledTick = (currentTick + delayTicks) % wheelSize;
        ScheduledTask<T> scheduledTask = new ScheduledTask<>(taskIdGen.incrementAndGet(), scheduledTick, task);
        wheel[scheduledTick].add(scheduledTask);
    }

    public void tick(Consumer<T> consumer) {
        Set<ScheduledTask<T>> tasks = wheel[currentTick];
        if (!tasks.isEmpty()) {
            for (ScheduledTask<T> task : tasks) {
                final T value = task.task.get();
                if (value != null) consumer.accept(value);
            }
            tasks.clear();
        }
        currentTick = (currentTick + 1) % wheelSize;
    }

    public Map<Integer, List<T>> drainAll() {
        Map<Integer, List<T>> result = new HashMap<>();
        for (int i = 0; i < wheelSize; i++) {
            final int ticksRemaining = (i - currentTick + wheelSize) % wheelSize;
            Set<ScheduledTask<T>> tasks = wheel[i];
            if (tasks.isEmpty()) continue;
            List<T> taskList = new ArrayList<>(tasks.size());
            for (ScheduledTask<T> task : new ArrayList<>(tasks)) {
                final T value = task.task.get();
                if (value != null) taskList.add(value);
            }
            if (!taskList.isEmpty()) {
                result.put(ticksRemaining, taskList);
            }
            tasks.clear();
        }
        return result;
    }

    public boolean isEmpty() {
        for (Set<ScheduledTask<T>> tasks : wheel) {
            if (!tasks.isEmpty()) return false;
        }
        return true;
    }

    record ScheduledTask<T>(long id, int scheduledTick, Supplier<T> task) {
    }
}
