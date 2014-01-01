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

import gnu.trove.list.TFloatList;
import gnu.trove.list.TIntList;

import org.spoutcraft.client.Interface.mesh.Mesh.MeshAttribute;
import org.spoutcraft.client.universe.Chunk;
import org.spoutcraft.client.universe.block.BlockFace;
import org.spoutcraft.client.universe.block.BlockFaces;
import org.spoutcraft.client.universe.block.material.Material;

/**
 * The standard chunk mesher. Voxels are meshed as blocks. Occludes any block not visible, including the edge blocks. Can mesh a chunk with 3n^2(n+2) block access operations, n being the size of the
 * chunk.
 */
public class StandardChunkMesher implements ChunkMesher {
    @Override
    public Mesh mesh(ChunkSnapshotGroup chunk) {
        // TODO: add textures
        final Mesh mesh = new Mesh(MeshAttribute.POSITIONS, MeshAttribute.NORMALS);
        final TFloatList positions = mesh.getAttribute(MeshAttribute.POSITIONS);
        final TIntList indices = mesh.getIndices();
        int index = 0;
        // Mesh the faces on the x axis
        for (int zz = 0; zz < Chunk.BLOCKS.SIZE; zz++) {
            for (int yy = 0; yy < Chunk.BLOCKS.SIZE; yy++) {
                Material backMaterial = chunk.getMaterial(-1, yy, zz);
                for (int xx = 0; xx < Chunk.BLOCKS.SIZE + 1; xx++) {
                    final Material frontMaterial = chunk.getMaterial(xx, yy, zz);
                    final BlockFace face = getFace(backMaterial, frontMaterial, BlockFaces.NS);
                    if (face == BlockFace.NORTH) {
                        add(indices, index + 3, index + 2, index + 1, index + 2, index, index + 1);
                    } else if (face == BlockFace.SOUTH) {
                        add(indices, index + 3, index + 1, index + 2, index + 2, index + 1, index);
                    } else {
                        backMaterial = frontMaterial;
                        continue;
                    }
                    add(positions, xx, yy + 1, zz + 1);
                    add(positions, xx, yy + 1, zz);
                    add(positions, xx, yy, zz + 1);
                    add(positions, xx, yy, zz);
                    index += 4;
                    backMaterial = frontMaterial;
                }
            }
        }
        // Mesh the faces on the y axis
        for (int xx = 0; xx < Chunk.BLOCKS.SIZE; xx++) {
            for (int zz = 0; zz < Chunk.BLOCKS.SIZE; zz++) {
                Material backMaterial = chunk.getMaterial(xx, -1, zz);
                for (int yy = 0; yy < Chunk.BLOCKS.SIZE + 1; yy++) {
                    final Material frontMaterial = chunk.getMaterial(xx, yy, zz);
                    final BlockFace face = getFace(backMaterial, frontMaterial, BlockFaces.BT);
                    if (face == BlockFace.BOTTOM) {
                        add(indices, index + 3, index + 2, index + 1, index + 2, index, index + 1);
                    } else if (face == BlockFace.TOP) {
                        add(indices, index + 3, index + 1, index + 2, index + 2, index + 1, index);
                    } else {
                        backMaterial = frontMaterial;
                        continue;
                    }
                    add(positions, xx, yy, zz);
                    add(positions, xx + 1, yy, zz);
                    add(positions, xx, yy, zz + 1);
                    add(positions, xx + 1, yy, zz + 1);
                    index += 4;
                    backMaterial = frontMaterial;
                }
            }
        }
        // Mesh the faces on the z axis
        for (int xx = 0; xx < Chunk.BLOCKS.SIZE; xx++) {
            for (int yy = 0; yy < Chunk.BLOCKS.SIZE; yy++) {
                Material backMaterial = chunk.getMaterial(xx, yy, -1);
                for (int zz = 0; zz < Chunk.BLOCKS.SIZE + 1; zz++) {
                    final Material frontMaterial = chunk.getMaterial(xx, yy, zz);
                    final BlockFace face = getFace(backMaterial, frontMaterial, BlockFaces.EW);
                    if (face == BlockFace.EAST) {
                        add(indices, index + 3, index + 2, index + 1, index + 2, index, index + 1);
                    } else if (face == BlockFace.WEST) {
                        add(indices, index + 3, index + 1, index + 2, index + 2, index + 1, index);
                    } else {
                        backMaterial = frontMaterial;
                        continue;
                    }
                    add(positions, xx, yy + 1, zz);
                    add(positions, xx + 1, yy + 1, zz);
                    add(positions, xx, yy, zz);
                    add(positions, xx + 1, yy, zz);
                    index += 4;
                    backMaterial = frontMaterial;
                }
            }
        }
        return mesh;
    }

    private BlockFace getFace(Material back, Material front, BlockFaces axis) {
        if (back.isVisible() && !front.occludes(back, axis.get(0))) {
            return axis.get(1);
        }
        if (front.isVisible() && !back.occludes(front, axis.get(1))) {
            return axis.get(0);
        }
        return null;
    }

    private static void add(TFloatList list, float x, float y, float z) {
        list.add(x);
        list.add(y);
        list.add(z);
    }

    private static void add(TIntList list, int i0, int i1, int i2, int i3, int i4, int i5) {
        list.add(i0);
        list.add(i1);
        list.add(i2);
        list.add(i3);
        list.add(i4);
        list.add(i5);
    }
}
