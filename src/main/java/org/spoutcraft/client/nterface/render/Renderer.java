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
package org.spoutcraft.client.nterface.render;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.flowpowered.commons.TPSMonitor;
import com.flowpowered.commons.ViewFrustum;
import com.flowpowered.math.matrix.Matrix4f;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector4f;

import org.lwjgl.opengl.GLContext;

import org.spout.renderer.api.Camera;
import org.spout.renderer.api.GLImplementation;
import org.spout.renderer.api.GLVersioned.GLVersion;
import org.spout.renderer.api.Material;
import org.spout.renderer.api.data.Uniform.FloatUniform;
import org.spout.renderer.api.data.Uniform.Matrix4Uniform;
import org.spout.renderer.api.data.Uniform.Vector3Uniform;
import org.spout.renderer.api.data.Uniform.Vector4Uniform;
import org.spout.renderer.api.data.UniformHolder;
import org.spout.renderer.api.gl.Context;
import org.spout.renderer.api.gl.Context.Capability;
import org.spout.renderer.api.gl.Texture.Format;
import org.spout.renderer.api.gl.Texture.InternalFormat;
import org.spout.renderer.api.gl.VertexArray;
import org.spout.renderer.api.model.Model;
import org.spout.renderer.api.model.StringModel;
import org.spout.renderer.api.util.CausticUtil;
import org.spout.renderer.api.util.MeshGenerator;
import org.spout.renderer.api.util.Rectangle;
import org.spout.renderer.lwjgl.LWJGLUtil;

import org.spoutcraft.client.nterface.Interface;
import org.spoutcraft.client.nterface.render.graph.RenderGraph;
import org.spoutcraft.client.nterface.render.graph.node.BlurNode;
import org.spoutcraft.client.nterface.render.graph.node.CascadedShadowMappingNode;
import org.spoutcraft.client.nterface.render.graph.node.LightingNode;
import org.spoutcraft.client.nterface.render.graph.node.RenderGUINode;
import org.spoutcraft.client.nterface.render.graph.node.RenderModelsNode;
import org.spoutcraft.client.nterface.render.graph.node.RenderTransparentModelsNode;
import org.spoutcraft.client.nterface.render.graph.node.SSAONode;
import org.spoutcraft.client.nterface.render.graph.node.ShadowMappingNode;

/**
 * The default renderer. Support OpenGL 2.1 and 3.2. Can render fully textured models with normal and specular mapping, ambient occlusion (SSAO), shadow mapping, Phong shading, motion blur and edge
 * detection anti-aliasing. The default OpenGL version is 3.2.
 */
public class Renderer {
    private static final String WINDOW_TITLE = "Spoutcraft";
    private static final DateFormat SCREENSHOT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
    // Settings
    private Vector2i windowSize = new Vector2i(1200, 800);
    private boolean cullBackFaces = true;
    // Effect uniforms
    private final Vector3Uniform lightDirectionUniform = new Vector3Uniform("lightDirection", Vector3f.FORWARD);
    private final Matrix4Uniform previousViewMatrixUniform = new Matrix4Uniform("previousViewMatrix", new Matrix4f());
    private final Matrix4Uniform previousProjectionMatrixUniform = new Matrix4Uniform("previousProjectionMatrix", new Matrix4f());
    private final FloatUniform blurStrengthUniform = new FloatUniform("blurStrength", 1);
    // OpenGL version and context
    private Context context;
    // Included materials
    private Material solidMaterial;
    private Material transparencyMaterial;
    // Render graph
    private RenderGraph graph;
    // Graph nodes
    private RenderModelsNode renderModelsNode;
    private ShadowMappingNode shadowMappingNode;
    private LightingNode lightingNode;
    private RenderTransparentModelsNode renderTransparentModelsNode;
    private RenderGUINode renderGUINode;
    // FPS monitor
    private final TPSMonitor fpsMonitor = new TPSMonitor();
    private StringModel fpsMonitorModel;
    private boolean fpsMonitorStarted = false;

    public Renderer() {
        // Set the default OpenGL version to GL30
        setGLVersion(Interface.DEFAULT_VERSION);
    }

    /**
     * Creates the OpenGL context and initializes the internal resources for the renderer
     */
    public void init() {
        initContext();
        initGraph();
        initMaterials();
        addDefaultObjects();
    }

    private void initContext() {
        context.setWindowTitle(WINDOW_TITLE);
        context.setWindowSize(windowSize);
        context.create();
        context.setClearColor(Vector4f.ZERO);
        if (cullBackFaces) {
            context.enableCapability(Capability.CULL_FACE);
        }
        context.enableCapability(Capability.DEPTH_TEST);
        if (context.getGLVersion() == Interface.DEFAULT_VERSION || GLContext.getCapabilities().GL_ARB_depth_clamp) {
            context.enableCapability(Capability.DEPTH_CLAMP);
        }
        final UniformHolder uniforms = context.getUniforms();
        uniforms.add(previousViewMatrixUniform);
        uniforms.add(previousProjectionMatrixUniform);
    }

    private void initGraph() {
        graph = new RenderGraph(context, "/shaders/" + context.getGLVersion().toString().toLowerCase());
        graph.setWindowSize(windowSize);
        graph.setFieldOfView(60);
        graph.setNearPlane(0.1f);
        graph.setFarPlane(200);
        graph.create();
        final int blurSize = 2;
        // Render models
        renderModelsNode = new RenderModelsNode(graph, "models");
        renderModelsNode.create();
        graph.addNode(renderModelsNode);
        // Shadows
        shadowMappingNode = new CascadedShadowMappingNode(graph, "shadows");
        shadowMappingNode.connect("normals", "vertexNormals", renderModelsNode);
        shadowMappingNode.connect("depths", "depths", renderModelsNode);
        shadowMappingNode.setShadowMapSize(new Vector2i(1048, 1048));
        shadowMappingNode.create();
        shadowMappingNode.setKernelSize(8);
        shadowMappingNode.setNoiseSize(blurSize);
        shadowMappingNode.setBias(0.005f);
        shadowMappingNode.setRadius(0.05f);
        graph.addNode(shadowMappingNode);
        // Blur shadows
        final BlurNode blurShadowsNode = new BlurNode(graph, "blurShadows");
        blurShadowsNode.connect("colors", "shadows", shadowMappingNode);
        blurShadowsNode.create();
        blurShadowsNode.setKernelGenerator(BlurNode.BOX_KERNEL);
        blurShadowsNode.setKernelSize(blurSize + 1);
        graph.addNode(blurShadowsNode);
        // SSAO
        final SSAONode ssaoNode = new SSAONode(graph, "ssao");
        ssaoNode.connect("normals", "normals", renderModelsNode);
        ssaoNode.connect("depths", "depths", renderModelsNode);
        ssaoNode.create();
        ssaoNode.setKernelSize(8, 0.15f);
        ssaoNode.setNoiseSize(blurSize);
        ssaoNode.setRadius(0.5f);
        ssaoNode.setPower(2);
        graph.addNode(ssaoNode);
        // Blur occlusions
        final BlurNode blurOcclusionsNode = new BlurNode(graph, "blurOcclusions");
        blurOcclusionsNode.connect("colors", "occlusions", ssaoNode);
        blurOcclusionsNode.create();
        blurOcclusionsNode.setKernelGenerator(BlurNode.BOX_KERNEL);
        blurOcclusionsNode.setKernelSize(blurSize + 1);
        graph.addNode(blurOcclusionsNode);
        // Lighting
        lightingNode = new LightingNode(graph, "lighting");
        lightingNode.connect("colors", "colors", renderModelsNode);
        lightingNode.connect("normals", "normals", renderModelsNode);
        lightingNode.connect("depths", "depths", renderModelsNode);
        lightingNode.connect("materials", "materials", renderModelsNode);
        lightingNode.connect("occlusions", "colors", blurOcclusionsNode);
        lightingNode.connect("shadows", "colors", blurShadowsNode);
        lightingNode.create();
        graph.addNode(lightingNode);
        // Transparent models
        renderTransparentModelsNode = new RenderTransparentModelsNode(graph, "transparency");
        renderTransparentModelsNode.connect("depths", "depths", renderModelsNode);
        renderTransparentModelsNode.connect("colors", "colors", lightingNode);
        renderTransparentModelsNode.create();
        graph.addNode(renderTransparentModelsNode);
        // Render GUI
        renderGUINode = new RenderGUINode(graph, "gui");
        renderGUINode.connect("colors", "colors", renderTransparentModelsNode);
        renderGUINode.create();
        graph.addNode(renderGUINode);
        // Build graph
        graph.rebuild();
    }

    private void initMaterials() {
        UniformHolder uniforms;
        // Solid material
        solidMaterial = new Material(graph.getProgram("solid"));
        uniforms = solidMaterial.getUniforms();
        uniforms.add(new FloatUniform("diffuseIntensity", 0.8f));
        uniforms.add(new FloatUniform("specularIntensity", 0.5f));
        uniforms.add(new FloatUniform("ambientIntensity", 0.2f));
        uniforms.add(new FloatUniform("shininess", 0.15f));
        // Transparency material
        transparencyMaterial = new Material(graph.getProgram("weightedSum"));
        uniforms = transparencyMaterial.getUniforms();
        uniforms.add(lightDirectionUniform);
        uniforms.add(new FloatUniform("diffuseIntensity", 0.8f));
        uniforms.add(new FloatUniform("specularIntensity", 1));
        uniforms.add(new FloatUniform("ambientIntensity", 0.2f));
        uniforms.add(new FloatUniform("shininess", 0.8f));
    }

    private void addDefaultObjects() {
        addFPSMonitor();

        final VertexArray shape = context.newVertexArray();
        shape.create();
        shape.setData(MeshGenerator.generateCylinder(null, 2.5f, 5));
        final Model model1 = new Model(shape, transparencyMaterial);
        model1.setPosition(new Vector3f(0, 22, -6));
        model1.getUniforms().add(new Vector4Uniform("modelColor", new Vector4f(1, 1, 0, 0.3)));
        addTransparentModel(model1);
        final Model model2 = model1.getInstance();
        model2.setPosition(new Vector3f(0, 22, 6));
        model2.getUniforms().add(new Vector4Uniform("modelColor", new Vector4f(0, 1, 1, 0.7)));
        addTransparentModel(model2);
    }

    private void addFPSMonitor() {
        final Font ubuntu;
        try {
            ubuntu = Font.createFont(Font.TRUETYPE_FONT, Renderer.class.getResourceAsStream("/fonts/ubuntu-r.ttf"));
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
            return;
        }
        final StringModel sandboxModel = new StringModel(context, graph.getProgram("font"), "ClientWIPFS0123456789-: ", ubuntu.deriveFont(Font.PLAIN, 15), windowSize.getX());
        final float aspect = 1 / graph.getAspectRatio();
        sandboxModel.setPosition(new Vector3f(0.005, 0.97 * aspect, -0.1));
        sandboxModel.setString("Client - WIP");
        renderGUINode.addModel(sandboxModel);
        final StringModel fpsModel = sandboxModel.getInstance();
        fpsModel.setPosition(new Vector3f(0.005, 0.94 * aspect, -0.1));
        fpsModel.setString("FPS: " + fpsMonitor.getTPS());
        renderGUINode.addModel(fpsModel);
        fpsMonitorModel = fpsModel;
    }

    /**
     * Destroys the renderer internal resources and the OpenGL context.
     */
    public void dispose() {
        disposeGraph();
        disposeContext();
        fpsMonitorStarted = false;
    }

    private void disposeContext() {
        context.destroy();
    }

    private void disposeGraph() {
        graph.destroy();
    }

    /**
     * Renders the models to the window.
     */
    public void render() {
        if (!fpsMonitorStarted) {
            fpsMonitor.start();
            fpsMonitorStarted = true;
        }
        // Update the current frame uniforms
        final Camera camera = renderModelsNode.getCamera();
        blurStrengthUniform.set((float) fpsMonitor.getTPS() / Interface.TPS);
        // Render
        graph.render();
        // Update the previous frame uniforms
        setPreviousModelMatrices();
        previousViewMatrixUniform.set(camera.getViewMatrix());
        previousProjectionMatrixUniform.set(camera.getProjectionMatrix());
        // Update the FPS monitor
        updateFPSMonitor();
    }

    private void setPreviousModelMatrices() {
        for (Model model : renderModelsNode.getModels()) {
            model.getUniforms().getMatrix4("previousModelMatrix").set(model.getMatrix());
        }
        for (Model model : renderTransparentModelsNode.getModels()) {
            model.getUniforms().getMatrix4("previousModelMatrix").set(model.getMatrix());
        }
    }

    private void updateFPSMonitor() {
        fpsMonitor.update();
        fpsMonitorModel.setString("FPS: " + fpsMonitor.getTPS());
    }

    public GLVersion getGLVersion() {
        return context.getGLVersion();
    }

    /**
     * Sets the OpenGL version. Must be done before initializing the renderer.
     *
     * @param version The OpenGL version to use
     */
    public void setGLVersion(GLVersion version) {
        switch (version) {
            case GL20:
                context = GLImplementation.get(LWJGLUtil.GL20_IMPL);
                break;
            case GL30:
                context = GLImplementation.get(LWJGLUtil.GL30_IMPL);
        }
    }

    public Context getContext() {
        return context;
    }

    public RenderModelsNode getRenderModelsNode() {
        return renderModelsNode;
    }

    public RenderGUINode getRenderGUINode() {
        return renderGUINode;
    }

    /**
     * Sets whether or not to cull the back faces of the geometry.
     *
     * @param cull Whether or not to cull the back faces
     */
    public void setCullBackFaces(boolean cull) {
        cullBackFaces = cull;
    }

    /**
     * Updates the light direction and camera bounds to ensure that shadows are casted inside the frustum.
     *
     * @param direction The light direction
     * @param frustum The frustum in which to cast shadows
     */
    public void updateLight(Vector3f direction, ViewFrustum frustum) {
        direction = direction.normalize();
        lightDirectionUniform.set(direction);
        shadowMappingNode.updateLight(direction, frustum);
        lightingNode.setLightDirection(direction);
    }

    /**
     * Adds a model to be rendered as a solid.
     *
     * @param model The model
     */
    public void addSolidModel(Model model) {
        model.setMaterial(solidMaterial);
        model.getUniforms().add(new Vector4Uniform("modelColor", new Vector4f(Math.random(), Math.random(), Math.random(), 1)));
        renderModelsNode.addModel(model);
    }

    /**
     * Adds a model to be rendered as partially transparent.
     *
     * @param model The transparent model
     */
    public void addTransparentModel(Model model) {
        model.setMaterial(transparencyMaterial);
        renderTransparentModelsNode.addModel(model);
    }

    /**
     * Saves a screenshot (PNG) to the directory where the program is currently running, with the current date as the file name.
     *
     * @param outputDir The directory in which to output the file, can be null, which will output to the current working directory
     */
    public void saveScreenshot(File outputDir) {
        final Rectangle size = new Rectangle(Vector2i.ZERO, windowSize);
        final ByteBuffer buffer = context.readFrame(size, InternalFormat.RGB8);
        final BufferedImage image = CausticUtil.getImage(buffer, Format.RGB, size);
        try {
            ImageIO.write(image, "PNG", new File(outputDir, SCREENSHOT_DATE_FORMAT.format(Calendar.getInstance().getTime()) + ".png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
