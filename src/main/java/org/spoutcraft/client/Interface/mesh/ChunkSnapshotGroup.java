/**
 * This file is part of Client, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2013 Spoutcraft <http://spoutcraft.org/>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spoutcraft.client.Interface.mesh;

import org.spout.math.vector.Vector3i;

import org.spoutcraft.client.universe.Chunk;
import org.spoutcraft.client.universe.block.material.Material;
import org.spoutcraft.client.universe.block.material.Materials;
import org.spoutcraft.client.universe.snapshot.ChunkSnapshot;
import org.spoutcraft.client.universe.snapshot.WorldSnapshot;

/**
 * A chunk and it's immediate neighbours (BTNESW), used for meshing the chunk including it's edge blocks with proper occlusion.
 */
public class ChunkSnapshotGroup {
    private final ChunkSnapshot middle;
    private final ChunkSnapshot top;
    private final ChunkSnapshot bottom;
    private final ChunkSnapshot north;
    private final ChunkSnapshot east;
    private final ChunkSnapshot south;
    private final ChunkSnapshot west;

    /**
     * Constructs a new snapshot group from the middle chunk snapshot and the world snapshot. The world snapshot from the chunk will be used to source the neighbouring chunks (if they exist).
     *
     * @param middle The middle chunk
     */
    public ChunkSnapshotGroup(ChunkSnapshot middle) {
        this.middle = middle;
        final Vector3i position = middle.getPosition();
        final WorldSnapshot world = middle.getWorld();
        top = world.getChunk(position.add(Vector3i.UP));
        bottom = world.getChunk(position.sub(Vector3i.UP));
        north = world.getChunk(position.sub(Vector3i.RIGHT));
        south = world.getChunk(position.add(Vector3i.RIGHT));
        east = world.getChunk(position.sub(Vector3i.FORWARD));
        west = world.getChunk(position.add(Vector3i.FORWARD));
    }

    /**
     * Returns the material at the position, looking at the directly neighbouring chunks if the position is outside the chunk. Will return {@link
     * org.spoutcraft.client.universe.block.material.Materials#AIR} if the neighbour is missing.
     *
     * @param position The position to lookup the material at
     * @return The material
     */
    public Material getMaterial(Vector3i position) {
        return getMaterial(position.getX(), position.getY(), position.getZ());
    }

    /**
     * Returns the material at the position, looking at the directly neighbouring chunks if the position is outside the chunk. Will return {@link
     * org.spoutcraft.client.universe.block.material.Materials#AIR} if the neighbour is missing.
     *
     * @param x The x coordinate of the position
     * @param y The y coordinate of the position
     * @param z The z coordinate of the position
     * @return The material
     */
    public Material getMaterial(int x, int y, int z) {
        if (x < 0) {
            return north != null ? north.getMaterial(x, y, z) : Materials.AIR;
        } else if (x >= Chunk.BLOCKS.SIZE) {
            return south != null ? south.getMaterial(x, y, z) : Materials.AIR;
        } else if (y < 0) {
            return bottom != null ? bottom.getMaterial(x, y, z) : Materials.AIR;
        } else if (y >= Chunk.BLOCKS.SIZE) {
            return top != null ? top.getMaterial(x, y, z) : Materials.AIR;
        } else if (z < 0) {
            return east != null ? east.getMaterial(x, y, z) : Materials.AIR;
        } else if (z >= Chunk.BLOCKS.SIZE) {
            return west != null ? west.getMaterial(x, y, z) : Materials.AIR;
        }
        return middle.getMaterial(x, y, z);
    }
}
