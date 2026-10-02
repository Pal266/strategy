package com.pidluzsnij.strategy.ui.asset;

import com.pidluzsnij.strategy.ui.UiException;
import com.pidluzsnij.strategy.ui.resource.ResolvedUiResource;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_info_from_memory;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;

/**
 * Decodes PNG image resources with LWJGL STB. Bundled and external resources take the same path; all native
 * memory used for decoding is released before the method returns.
 */
public final class UiImageDecoder {

    public static final String STAGE = "decode UI image";

    /** Largest accepted width or height, which also bounds the decoded size. */
    public static final int MAX_DIMENSION = 8192;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private UiImageDecoder() {
    }

    /** @throws UiException when the resource is not a decodable PNG image within the size limits */
    public static UiImage decode(ResolvedUiResource resource) throws UiException {
        ByteBuffer bytes = resource.buffer();
        if (!isPng(bytes)) {
            throw new UiException(STAGE, resource.name(), "the resource is not a PNG image");
        }
        ByteBuffer encoded = MemoryUtil.memAlloc(bytes.remaining());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoded.put(bytes).flip();
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            if (!stbi_info_from_memory(encoded, width, height, channels)) {
                throw new UiException(STAGE, resource.name(), "the PNG image is malformed (" + reason() + ")");
            }
            if (width.get(0) <= 0 || height.get(0) <= 0
                    || width.get(0) > MAX_DIMENSION || height.get(0) > MAX_DIMENSION) {
                throw new UiException(STAGE, resource.name(),
                        "the image dimensions must be within 1.." + MAX_DIMENSION + " pixels");
            }
            ByteBuffer pixels = stbi_load_from_memory(encoded, width, height, channels, 4);
            if (pixels == null) {
                throw new UiException(STAGE, resource.name(), "the PNG image is malformed (" + reason() + ")");
            }
            try {
                byte[] rgba = new byte[width.get(0) * height.get(0) * 4];
                pixels.get(0, rgba);
                return new UiImage(width.get(0), height.get(0), rgba);
            } finally {
                stbi_image_free(pixels);
            }
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }

    /** @return whether {@code bytes} start with the PNG signature */
    static boolean isPng(ByteBuffer bytes) {
        if (bytes.remaining() < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes.get(bytes.position() + i) != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /** STB failure reasons are fixed library strings and never contain resource content. */
    private static String reason() {
        String reason = stbi_failure_reason();
        return reason == null ? "unknown decoder failure" : reason;
    }
}
