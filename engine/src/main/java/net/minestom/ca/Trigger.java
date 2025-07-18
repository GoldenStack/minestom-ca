package net.minestom.ca;

import net.minestom.server.network.packet.server.ServerPacket;

public sealed interface Trigger {
    record ProximityPacket(ServerPacket packet) implements Trigger {
    }

    non-sealed interface Custom extends Trigger {
    }
}
