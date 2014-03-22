/**
 * This file is part of Client, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2013-2014 Spoutcraft <http://spoutcraft.org/>
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
package org.spoutcraft.client.nterface.render.graph.node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.flowpowered.math.vector.Vector2i;

import org.spout.renderer.api.Material;
import org.spout.renderer.api.Pipeline;
import org.spout.renderer.api.Pipeline.PipelineBuilder;
import org.spout.renderer.api.gl.Context;
import org.spout.renderer.api.gl.Context.BlendFunction;
import org.spout.renderer.api.gl.Context.Capability;
import org.spout.renderer.api.gl.FrameBuffer;
import org.spout.renderer.api.gl.FrameBuffer.AttachmentPoint;
import org.spout.renderer.api.gl.Texture;
import org.spout.renderer.api.gl.Texture.FilterMode;
import org.spout.renderer.api.gl.Texture.InternalFormat;
import org.spout.renderer.api.model.Model;
import org.spout.renderer.api.util.Rectangle;

import org.spoutcraft.client.nterface.render.graph.RenderGraph;

/**
 *
 */
public class RenderTransparentModelsNode extends GraphNode {
    private final Texture weightedColors;
    private final Texture layerCounts;
    private final FrameBuffer weightedSumFrameBuffer;
    private final FrameBuffer frameBuffer;
    private Texture colors;
    private final List<Model> models = new ArrayList<>();
    private final Rectangle outputSize = new Rectangle();
    private final Pipeline pipeline;

    public RenderTransparentModelsNode(RenderGraph graph, String name) {
        super(graph, name);

        final Context context = graph.getContext();
        // Create the weighted colors texture
        weightedColors = context.newTexture();
        weightedColors.create();
        weightedColors.setFormat(InternalFormat.RGBA16F);
        weightedColors.setFilters(FilterMode.LINEAR, FilterMode.LINEAR);
        // Create the layer counts texture
        layerCounts = context.newTexture();
        layerCounts.create();
        layerCounts.setFormat(InternalFormat.R16F);
        layerCounts.setFilters(FilterMode.LINEAR, FilterMode.LINEAR);
        // Create the weighted sum frame buffer
        weightedSumFrameBuffer = context.newFrameBuffer();
        weightedSumFrameBuffer.create();
        weightedSumFrameBuffer.attach(AttachmentPoint.COLOR0, weightedColors);
        weightedSumFrameBuffer.attach(AttachmentPoint.COLOR1, layerCounts);
        // Create the frame buffer
        frameBuffer = context.newFrameBuffer();
        frameBuffer.create();
        // Create the material
        final Material material = new Material(graph.getProgram("transparencyBlending"));
        material.addTexture(0, weightedColors);
        material.addTexture(1, layerCounts);
        // Create the screen model
        final Model model = new Model(graph.getScreen(), material);
        // Create the pipeline
        pipeline = new PipelineBuilder()
                .useViewPort(outputSize)
                .disableDepthMask().disableCapabilities(Capability.CULL_FACE).enableCapabilities(Capability.BLEND)
                .setBlendingFunctions(BlendFunction.GL_ONE, BlendFunction.GL_ONE)
                .bindFrameBuffer(weightedSumFrameBuffer).clearBuffer().renderModels(models)
                .enableCapabilities(Capability.CULL_FACE).enableDepthMask()
                .setBlendingFunctions(BlendFunction.GL_ONE_MINUS_SRC_ALPHA, BlendFunction.GL_SRC_ALPHA)
                .bindFrameBuffer(frameBuffer).renderModels(Arrays.asList(model)).unbindFrameBuffer(frameBuffer)
                .disableCapabilities(Capability.BLEND).enableDepthMask()
                .build();
    }

    @Override
    public void destroy() {
        weightedColors.destroy();
        layerCounts.destroy();
        weightedSumFrameBuffer.destroy();
        frameBuffer.destroy();
    }

    @Override
    public void render() {
        pipeline.run(graph.getContext());
    }

    @Input("depths")
    public void setDepthsInput(Texture texture) {
        texture.checkCreated();
        weightedSumFrameBuffer.attach(AttachmentPoint.DEPTH, texture);
    }

    @Input("colors")
    public void setColorsInput(Texture texture) {
        texture.checkCreated();
        colors = texture;
        frameBuffer.attach(AttachmentPoint.COLOR0, texture);
        final Vector2i size = texture.getSize();
        // Update the size of the texture to match in input, if necessary
        if (!size.equals(outputSize.getSize())) {
            outputSize.setSize(size);
            weightedColors.setImageData(null, size.getX(), size.getY());
            layerCounts.setImageData(null, size.getX(), size.getY());
        }
    }

    @Output("colors")
    public Texture getColorsOutput() {
        return colors;
    }

    /**
     * Adds a model to the renderer.
     *
     * @param model The model to add
     */
    public void addModel(Model model) {
        models.add(model);
    }

    /**
     * Removes a model from the renderer.
     *
     * @param model The model to remove
     */
    public void removeModel(Model model) {
        models.remove(model);
    }

    /**
     * Removes all the models from the renderer.
     */
    public void clearModels() {
        models.clear();
    }

    public List<Model> getModels() {
        return models;
    }
}
