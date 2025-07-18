package net.minestom.ca;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.AddEntityToInstanceEvent;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import static net.kyori.adventure.text.format.TextColor.color;

/**
 * Tools to use during development.
 */
public final class AutomataDebugImpl {
    private static final class PerformanceTracker {
        private final List<Double> msptHistory = new ArrayList<>();
        private final int maxHistorySize;

        public PerformanceTracker(int maxHistorySize) {
            this.maxHistorySize = maxHistorySize;
        }

        public void addMspt(double mspt) {
            msptHistory.add(mspt);
            if (msptHistory.size() > maxHistorySize) msptHistory.removeFirst();
        }

        public double getAverage() {
            if (msptHistory.isEmpty()) return 0.0;
            return msptHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getPercentile(int percentile) {
            if (msptHistory.isEmpty()) return 0.0;
            double[] sorted = msptHistory.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            int index = (int) Math.ceil((percentile / 100.0) * sorted.length) - 1;
            index = Math.max(0, Math.min(index, sorted.length - 1));
            return sorted[index];
        }

        public int getSampleCount() {
            return msptHistory.size();
        }
    }

    private static final class AutomataWorldDebugState {
        PerformanceTracker performanceTracker = new PerformanceTracker(200);
        // First boss bar - Real-time metrics
        BossBar realtimeBossBar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        // Second boss bar - Performance statistics
        BossBar statsBossBar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);
    }

    private static final WeakHashMap<Instance, AutomataWorldDebugState> DEBUG_STATES = new WeakHashMap<>();

    private static AutomataWorldDebugState state(Instance instance) {
        return DEBUG_STATES.computeIfAbsent(instance, inst -> {
          AutomataWorldDebugState debugState = new AutomataWorldDebugState();
          inst.showBossBar(debugState.realtimeBossBar);
          inst.showBossBar(debugState.statsBossBar);
          return debugState;
        });
    }

    public static EventNode<InstanceEvent> eventNodeDebug() {
        return EventNode.type("ca:node", EventFilter.INSTANCE)
                .addListener(AutomataEvent.Tick.class, event -> {
                    final Automata.Metrics metrics = event.metrics();
                    final double ms = event.ms();

                    AutomataWorldDebugState debugState = state(event.getInstance());

                    PerformanceTracker performanceTracker = debugState.performanceTracker;
                    performanceTracker.addMspt(ms);

                    // Calculate modification ratio (percentage of processed blocks that were modified)
                    final int modRatio = metrics.processedBlocks() > 0 ? (metrics.modifiedBlocks() * 100) / metrics.processedBlocks() : 100;
                    final float ratioValue = modRatio / 100.0f;
                    final int red = (int) (255 * (1 - ratioValue));
                    final int green = (int) (255 * ratioValue);
                    final int color = (red << 16) | (green << 8);
                    final Component ratioComponent = Component.text(modRatio + "%").color(color(color));

                    debugState.realtimeBossBar.name(Component.text()
                                    .append(Component.text("§6⚡ §a" + String.format("%.2f", ms) + "ms"))
                                    .append(Component.text(" §6◆ §f" + String.format("%,d", metrics.processedSections()) + " §7section"))
                                    .append(Component.text(" §6◆ §f" + String.format("%,d", metrics.processedBlocks()) + " §7block"))
                                    .append(Component.text(" §6◆ §a" + String.format("%,d", metrics.modifiedBlocks()) + " §f(§a"))
                                    .append(ratioComponent)
                                    .append(Component.text("§f)"))
                                    .build()
                            )
                            .progress(Math.min(1.0f, (float) (ms / 50.0)))
                            .color(ms >= 50 ? BossBar.Color.RED : (ms >= 25 ? BossBar.Color.YELLOW : BossBar.Color.GREEN));

                    // Update statistics boss bar
                    final double avgMspt = performanceTracker.getAverage();
                    final double p90 = performanceTracker.getPercentile(90);
                    final double p95 = performanceTracker.getPercentile(95);
                    final double p99 = performanceTracker.getPercentile(99);
                    final int samples = performanceTracker.getSampleCount();

                    debugState.statsBossBar.name(Component.text()
                                    .append(Component.text("§3📊 §fAvg: §b" + String.format("%.2f", avgMspt) + "ms"))
                                    .append(Component.text(" §3◈ §f90th: §b" + String.format("%.1f", p90)))
                                    .append(Component.text(" §3◈ §f95th: §b" + String.format("%.1f", p95)))
                                    .append(Component.text(" §3◈ §f99th: §b" + String.format("%.1f", p99)))
                                    .append(Component.text(" §7(§b" + samples + "§7)"))
                                    .build()
                            )
                            .progress(Math.min(1.0f, (float) (avgMspt / 50.0)))
                            .color(avgMspt >= 50 ? BossBar.Color.RED : (avgMspt >= 25 ? BossBar.Color.YELLOW : BossBar.Color.BLUE));
                });
    }
}
