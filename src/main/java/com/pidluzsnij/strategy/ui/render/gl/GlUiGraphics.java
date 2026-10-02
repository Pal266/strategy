package com.pidluzsnij.strategy.ui.render.gl;

import com.pidluzsnij.strategy.ui.asset.GlyphAtlas;
import com.pidluzsnij.strategy.ui.asset.UiImage;
import com.pidluzsnij.strategy.ui.definition.TextAlign;
import com.pidluzsnij.strategy.ui.definition.UiColor;
import com.pidluzsnij.strategy.ui.layout.ScreenRect;
import com.pidluzsnij.strategy.ui.render.TextPlacement;
import com.pidluzsnij.strategy.ui.render.UiGraphics;
import com.pidluzsnij.strategy.ui.render.UiTextFace;
import com.pidluzsnij.strategy.ui.render.UiTexture;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glScissor;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glBindAttribLocation;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glDetachShader;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

/**
 * {@link UiGraphics} drawing textured quads through the application's current OpenGL context, batching quads
 * that share a texture. Requires OpenGL 2.0 / GLSL 1.20. The window system requests no particular OpenGL
 * version, so this requirement applies only once the UI foundation loads a UI definition and initializes this
 * renderer; a context without OpenGL 2.0 then fails rendering initialization. Shaders are part of the
 * application, not of the UI resources.
 */
public final class GlUiGraphics implements UiGraphics {

    private static final int FLOATS_PER_VERTEX = 8;
    private static final int VERTICES_PER_QUAD = 6;
    private static final int MAX_QUADS = 2048;

    private static final int POSITION = 0;
    private static final int TEXCOORD = 1;
    private static final int COLOR = 2;

    private static final String VERTEX_SHADER = """
            #version 120
            attribute vec2 a_position;
            attribute vec2 a_texcoord;
            attribute vec4 a_color;
            uniform vec2 u_viewport;
            varying vec2 v_texcoord;
            varying vec4 v_color;
            void main() {
                v_texcoord = a_texcoord;
                v_color = a_color;
                gl_Position = vec4(a_position.x / u_viewport.x * 2.0 - 1.0,
                                   1.0 - a_position.y / u_viewport.y * 2.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D u_texture;
            varying vec2 v_texcoord;
            varying vec4 v_color;
            void main() {
                gl_FragColor = texture2D(u_texture, v_texcoord) * v_color;
            }
            """;

    private int program;
    private int vertexBuffer;
    private int viewportUniform = -1;
    private FloatBuffer vertices;

    private boolean drawing;
    private boolean blendWasEnabled;
    private boolean scissorWasEnabled;
    private int batchTexture;
    private int batchQuads;

    @Override
    public void initialize() {
        try {
            createRenderingResources();
        } catch (RuntimeException | LinkageError e) {
            try {
                close();
            } catch (RuntimeException | LinkageError suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    private void createRenderingResources() {
        vertices = MemoryUtil.memAllocFloat(MAX_QUADS * VERTICES_PER_QUAD * FLOATS_PER_VERTEX);
        int vertex = compile(GL_VERTEX_SHADER, VERTEX_SHADER, "vertex");
        int fragment = 0;
        try {
            fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT_SHADER, "fragment");
            program = glCreateProgram();
            if (program == 0) {
                throw new IllegalStateException("glCreateProgram returned 0");
            }
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glBindAttribLocation(program, POSITION, "a_position");
            glBindAttribLocation(program, TEXCOORD, "a_texcoord");
            glBindAttribLocation(program, COLOR, "a_color");
            glLinkProgram(program);
            glDetachShader(program, vertex);
            glDetachShader(program, fragment);
            if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("UI shader program could not be linked: " + glGetProgramInfoLog(program));
            }
        } finally {
            glDeleteShader(vertex);
            if (fragment != 0) {
                glDeleteShader(fragment);
            }
        }
        viewportUniform = glGetUniformLocation(program, "u_viewport");
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "u_texture"), 0);
        glUseProgram(0);
        vertexBuffer = glGenBuffers();
        if (vertexBuffer == 0) {
            throw new IllegalStateException("glGenBuffers returned 0");
        }
    }

    private static int compile(int type, String source, String description) {
        int shader = glCreateShader(type);
        if (shader == 0) {
            throw new IllegalStateException("glCreateShader returned 0 for the " + description + " shader");
        }
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("UI " + description + " shader could not be compiled: " + log);
        }
        return shader;
    }

    @Override
    public UiTexture createTexture(String name, UiImage image) {
        return new GlTexture(name, upload(image.width(), image.height(), image.rgba()), image.width(), image.height());
    }

    @Override
    public UiTextFace createTextFace(GlyphAtlas atlas) {
        byte[] coverage = atlas.coverage();
        byte[] rgba = new byte[coverage.length * 4];
        for (int i = 0; i < coverage.length; i++) {
            rgba[i * 4] = (byte) 0xFF;
            rgba[i * 4 + 1] = (byte) 0xFF;
            rgba[i * 4 + 2] = (byte) 0xFF;
            rgba[i * 4 + 3] = coverage[i];
        }
        return new GlTextFace(atlas, upload(atlas.width(), atlas.height(), rgba));
    }

    private static int upload(int width, int height, byte[] rgba) {
        ByteBuffer pixels = MemoryUtil.memAlloc(rgba.length);
        try {
            pixels.put(rgba).flip();
            int texture = glGenTextures();
            if (texture == 0) {
                throw new IllegalStateException("glGenTextures returned 0");
            }
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glBindTexture(GL_TEXTURE_2D, 0);
            return texture;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    @Override
    public void beginFrame(int framebufferWidth, int framebufferHeight, ScreenRect visibleArea) {
        if (program == 0) {
            throw new IllegalStateException("UI rendering is not initialized");
        }
        drawing = true;
        batchTexture = 0;
        batchQuads = 0;
        vertices.clear();
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        blendWasEnabled = glIsEnabled(GL_BLEND);
        scissorWasEnabled = glIsEnabled(GL_SCISSOR_TEST);
        int left = Math.round(visibleArea.x());
        int top = Math.round(visibleArea.y());
        int right = Math.round(visibleArea.x() + visibleArea.width());
        int bottom = Math.round(visibleArea.y() + visibleArea.height());
        glEnable(GL_SCISSOR_TEST);
        // OpenGL window coordinates start at the bottom-left corner.
        glScissor(left, framebufferHeight - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(program);
        glUniform2f(viewportUniform, framebufferWidth, framebufferHeight);
        glActiveTexture(GL_TEXTURE0);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glEnableVertexAttribArray(POSITION);
        glEnableVertexAttribArray(TEXCOORD);
        glEnableVertexAttribArray(COLOR);
        glVertexAttribPointer(POSITION, 2, GL_FLOAT, false, stride, 0L);
        glVertexAttribPointer(TEXCOORD, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glVertexAttribPointer(COLOR, 4, GL_FLOAT, false, stride, 4L * Float.BYTES);
    }

    @Override
    public void drawImage(UiTexture texture, ScreenRect rect) {
        GlTexture gl = (GlTexture) Objects.requireNonNull(texture, "texture");
        quad(gl.handle(), rect.x(), rect.y(), rect.width(), rect.height(), 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
    }

    @Override
    public void drawText(UiTextFace face, String text, ScreenRect rect, TextAlign align, UiColor color,
                         float pixelSize) {
        GlTextFace gl = (GlTextFace) Objects.requireNonNull(face, "face");
        float r = color.red() / 255f;
        float g = color.green() / 255f;
        float b = color.blue() / 255f;
        float a = color.alpha() / 255f;
        for (TextPlacement.GlyphQuad q : TextPlacement.place(gl.atlas(), text, rect, align, pixelSize)) {
            quad(gl.handle(), q.x(), q.y(), q.width(), q.height(), q.u0(), q.v0(), q.u1(), q.v1(), r, g, b, a);
        }
    }

    private void quad(int texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a) {
        if (!drawing) {
            throw new IllegalStateException("drawing outside beginFrame/endFrame");
        }
        if (texture != batchTexture || batchQuads == MAX_QUADS) {
            flush();
            batchTexture = texture;
        }
        vertex(x, y, u0, v0, r, g, b, a);
        vertex(x, y + h, u0, v1, r, g, b, a);
        vertex(x + w, y + h, u1, v1, r, g, b, a);
        vertex(x, y, u0, v0, r, g, b, a);
        vertex(x + w, y + h, u1, v1, r, g, b, a);
        vertex(x + w, y, u1, v0, r, g, b, a);
        batchQuads++;
    }

    private void vertex(float x, float y, float u, float v, float r, float g, float b, float a) {
        vertices.put(x).put(y).put(u).put(v).put(r).put(g).put(b).put(a);
    }

    private void flush() {
        if (batchQuads == 0) {
            return;
        }
        vertices.flip();
        glBindTexture(GL_TEXTURE_2D, batchTexture);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, batchQuads * VERTICES_PER_QUAD);
        vertices.clear();
        batchQuads = 0;
    }

    @Override
    public void endFrame() {
        if (!drawing) {
            return;
        }
        try {
            flush();
        } finally {
            drawing = false;
            glDisableVertexAttribArray(POSITION);
            glDisableVertexAttribArray(TEXCOORD);
            glDisableVertexAttribArray(COLOR);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindTexture(GL_TEXTURE_2D, 0);
            glUseProgram(0);
            if (!blendWasEnabled) {
                glDisable(GL_BLEND);
            }
            if (!scissorWasEnabled) {
                glDisable(GL_SCISSOR_TEST);
            }
        }
    }

    @Override
    public void close() {
        try {
            if (vertexBuffer != 0) {
                int buffer = vertexBuffer;
                vertexBuffer = 0;
                glDeleteBuffers(buffer);
            }
        } finally {
            try {
                if (program != 0) {
                    int handle = program;
                    program = 0;
                    glDeleteProgram(handle);
                }
            } finally {
                if (vertices != null) {
                    FloatBuffer buffer = vertices;
                    vertices = null;
                    MemoryUtil.memFree(buffer);
                }
            }
        }
    }

    private static final class GlTexture implements UiTexture {
        private final String name;
        private final int width;
        private final int height;
        private int handle;

        GlTexture(String name, int handle, int width, int height) {
            this.name = name;
            this.handle = handle;
            this.width = width;
            this.height = height;
        }

        int handle() {
            if (handle == 0) {
                throw new IllegalStateException("the texture has been released");
            }
            return handle;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void close() {
            if (handle != 0) {
                int released = handle;
                handle = 0;
                glDeleteTextures(released);
            }
        }
    }

    private static final class GlTextFace implements UiTextFace {
        private final GlyphAtlas atlas;
        private int handle;

        GlTextFace(GlyphAtlas atlas, int handle) {
            this.atlas = atlas;
            this.handle = handle;
        }

        int handle() {
            if (handle == 0) {
                throw new IllegalStateException("the text face has been released");
            }
            return handle;
        }

        @Override
        public GlyphAtlas atlas() {
            return atlas;
        }

        @Override
        public void close() {
            if (handle != 0) {
                int released = handle;
                handle = 0;
                glDeleteTextures(released);
            }
        }
    }
}
