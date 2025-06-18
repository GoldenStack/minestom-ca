# Cellular Automata Rule Tutorial

## Core Concepts

Rules in the cellular automata system are created by implementing the `Automata.CellRule` interface. Each rule processes blocks that were marked as "dirty" (needing update) in the previous tick.

### Key Components

1. **States**: Custom data fields attached to blocks
2. **Rules**: Logic that determines how blocks evolve
3. **Actions**: Changes applied to blocks as a result of rules
4. **Queries**: Methods to examine the current block and its neighbors

## Creating a Rule

To create a cellular automata rule:

1. Implement the `CellRule` interface
2. Define custom states (if needed)
3. Implement the `process` method to define behavior
4. Implement supporting methods: `init`, `tracked`, and `states`

### Basic Structure

```java
import static net.minestom.ca.Automata.*;

public final class MyRule implements CellRule {
    // Define custom states
    private static final State MY_COUNTER = new State("my_counter", 8);
    
    // State index fields (populated in init)
    private int counterIndex;
    
    @Override
    public void init(Map<State, Integer> mapping) {
        // Get indexes for our states
        counterIndex = mapping.get(MY_COUNTER);
    }
    
    @Override
    public List<Action> process(Context context, Query query) {
        // Main rule logic goes here
        // Return a list of actions or null
    }
    
    @Override
    public boolean tracked(Block block) {
        // Return true if this block should be tracked
        return block.compare(Block.SOME_BLOCK);
    }
    
    @Override
    public Set<State> states() {
        // Return all custom states used by this rule
        return Set.of(MY_COUNTER);
    }
}
```

## Block manipulation
Block properties represent visual information (liquid level, block orientation, etc.) and can be modified using the `withProperty` method. This allows you to change how a block appears without altering its fundamental type.
```java
import net.minestom.server.instance.block.Block;

Block block = Block.WATER;
block = block.withProperty("level", "3");
block = block.withProperty("level", "0");
String level = block.getProperty("level");
boolean solid = block.isSolid();
boolean air = block.isAir();

block = Block.REDSTONE_WIRE.withProperties(Map.of(
    "east", "up",
    "north", "side",
    "south", "side",
    "west", "none"
));
```

## Coordinate API
```java
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;

Point point = new Vec(1, 1, 1);
double x = point.x();
int blockX = point.blockX();
```

## States

States are custom data fields attached to blocks:

```java
private static final State MY_STATE = new State("name", bitSize);
```

- `name`: String identifier
- `bitSize`: Number of bits (1-64) to allocate for this state

Common use cases for states:
- Counters (growth, decay)
- Direction values
- Custom properties

## The Process Method

The `process` method is the heart of your rule:

It can only modify the current block, it cannot mutate blocks at any other coordinate, only wake them up.
Just like a shader.

```java
@Override
public List<Action> process(Context context, Query query) {
    // 1. Get current block state
    Block currentBlock = query.block();
    
    // 2. Query necessary data
    long myStateValue = query.state(counterIndex);
    
    // 3. Query neighboring blocks
    Block blockAbove = query.blockAt(0, 1, 0);
    
    // 4. Apply rule logic
    if (someCondition) {
        // 5. Return an action
        return List.of(Action.action(b -> {
            b.state(counterIndex, newValue);
            b.wakeup(Neighbors.MOORE_3D_SELF);
        }));
    }
    
    // No action needed
    return null;
}
```

## Creating Actions

Actions define what changes should occur to the current block:

```java
Action.action(b -> {
    // Update a state value
    b.state(stateIndex, newValue);
    
    // Change the block type
    b.state(0, Block.DIRT.stateId());
    
    // Neighboring blocks to process next tick
    b.wakeup(Neighbors.NEUMANN_3D_SELF);
    
    // Schedule this block for processing in the future
    b.schedule(5);
    
    // Add a condition for the action
    b.condition(0, Block.GRASS_BLOCK.stateId());
})
```

## Querying Neighbors

The `Query` interface provides methods to examine neighboring blocks:

```java
// Check a specific neighbor
Block blockAbove = query.blockAt(0, 1, 0);

// Check relative position using predefined neighbors
Block blockNorth = query.blockAt(Neighbors.NORTH.blockX(), 
                                Neighbors.NORTH.blockY(), 
                                Neighbors.NORTH.blockZ());

// Count blocks matching a condition
int airCount = query.count(0, Neighbors.MOORE_3D, 
                         state -> state == Block.AIR.stateId());

// Find first block matching criteria
Direction dir = query.findFirstPresent(directionIndex, Neighbors.NEUMANN_3D,
                         (x, y, z, value) -> {
                             // Logic to find and return a value
                         });
```

## Waking Up Blocks

After modifying a block, you typically need to wake up neighboring blocks:

```java
b.wakeup(Neighbors.MOORE_3D_SELF); // Wake all 26 surrounding blocks + self
```

Common neighbor patterns:
- `Neighbors.SELF` - Just the current block
- `Neighbors.NEUMANN_3D` - 6 orthogonal neighbors (N,S,E,W,Up,Down)
- `Neighbors.NEUMANN_3D_SELF` - 6 orthogonal neighbors + self
- `Neighbors.MOORE_3D` - All 26 surrounding blocks
- `Neighbors.MOORE_3D_SELF` - All 26 surrounding blocks + self

## Spatial Rules

Rules that only apply to certain areas or conditions:

## Best Practices

1. **Minimize state changes**: Only make necessary modifications
2. **Be careful with wakeups**: Excessive wakeups can cause performance issues
3. **Use appropriate neighbor patterns**: Only wake up blocks that need updating
4. **Avoid infinite loops**: Ensure rules eventually stabilize
5. **Use seed values**: For deterministic randomness in rules that need variation
6. **Use constant `Action`**: Constant actions can improve performance by avoiding object creation

## Example: Simple Water Flow Rule

```java
public final class SimpleWaterRule implements CellRule {
    @Override
    public void init(Map<State, Integer> mapping) {
        // No custom states needed
    }
    
    @Override
    public List<Action> process(Context context, Query query) {
        Block currentBlock = query.block();
        
        // Water flows downward
        if (currentBlock.compare(Block.WATER)) {
            Block blockBelow = query.blockAt(0, -1, 0);
            if (blockBelow.isAir()) {
                return List.of(Action.action(b -> {
                    b.state(0, Block.AIR.stateId());
                    b.wakeup(List.of(Neighbors.SELF, Neighbors.DOWN));
                }));
            }
        }
        
        // Air becomes water if water is above
        if (currentBlock.isAir()) {
            Block blockAbove = query.blockAt(0, 1, 0);
            if (blockAbove.compare(Block.WATER)) {
                return List.of(Action.action(b -> {
                    b.state(0, Block.WATER.stateId());
                    b.wakeup(Neighbors.NEUMANN_3D_SELF);
                }));
            }
        }
        
        return null;
    }
    
    @Override
    public boolean tracked(Block block) {
        return block.compare(Block.WATER) || block.isAir();
    }
    
    @Override
    public Set<State> states() {
        return Set.of();
    }
}
```

By following these guidelines, you can create complex and efficient cellular automata rules that model sophisticated systems like liquids, plant growth, or physical simulations.