package net.goldenstack.minestom_ca.backends.lazy;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HashedWheelTimer<T> {
    private final List<Set<ScheduledTask>> wheel;
    private final int wheelSize;
    private int currentTick = 0;
    private final AtomicLong taskIdGen = new AtomicLong();

    public HashedWheelTimer(int wheelSize) {
        this.wheelSize = wheelSize;
        this.wheel = new ArrayList<>(wheelSize);
        for (int i = 0; i < wheelSize; i++) {
            wheel.add(new HashSet<>());
        }
    }

    public ScheduledTask schedule(Supplier<T> task, int delayTicks) {
        int scheduledTick = (currentTick + delayTicks) % wheelSize;
        ScheduledTask scheduledTask = new ScheduledTask(taskIdGen.incrementAndGet(), scheduledTick, task);
        wheel.get(scheduledTick).add(scheduledTask);
        return scheduledTask;
    }

    public void cancel(ScheduledTask task) {
        wheel.get(task.scheduledTick).remove(task);
    }

    public void tick(Consumer<T> consumer) {
        Set<ScheduledTask> tasks = wheel.get(currentTick);
        if (!tasks.isEmpty()) {
            for (ScheduledTask task : new HashSet<>(tasks)) {
                final T value = task.run();
                if (value != null) consumer.accept(value);
            }
            tasks.clear();
        }
        currentTick = (currentTick + 1) % wheelSize;
    }

    public final class ScheduledTask {
        private final long id;
        private final int scheduledTick;
        private final Supplier<T> task;

        public ScheduledTask(long id, int scheduledTick, Supplier<T> task) {
            this.id = id;
            this.scheduledTick = scheduledTick;
            this.task = task;
        }

        public T run() {
            return task.get();
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ScheduledTask other = (ScheduledTask) obj;
            return id == other.id;
        }
    }
}
