package com.bienvenueblainville.photo.lambda;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * The image work a resident's photo needs before anyone looks at it.
 *
 * <p>Kept free of AWS types on purpose. Everything here is
 * {@code byte[] in, byte[] out}, so it is testable as a plain function and the
 * Lambda wrapper around it stays thin enough to be obviously correct.
 *
 * <p><b>The EXIF strip is the point, not a nicety.</b> A photo taken at the
 * curb outside someone's house carries GPS coordinates in its metadata — the
 * resident's home address, to within a few metres — along with the device
 * model and the exact timestamp. Serving that image back, or keeping it, would
 * publish an address nobody agreed to share. Decoding to pixels and re-encoding
 * drops every metadata segment, which is why this is a re-encode rather than a
 * copy.
 */
public final class PhotoProcessor {
    /**
     * Long edge of the stored image. Large enough for a model to identify a
     * material and for a person to recognise their own photo; small enough that
     * the original's several megabytes are not what gets served.
     */
    static final int MAX_EDGE = 1024;

    private PhotoProcessor() {
    }

    public static byte[] process(byte[] original) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
        if (source == null) {
            // Not an image this JVM can decode. Throwing here means the Lambda
            // fails visibly and the object stays unprocessed, which is better
            // than writing a corrupt "processed" copy that looks fine until
            // someone opens it.
            throw new IOException("Unsupported or corrupt image");
        }

        BufferedImage resized = resize(source);
        return encodeJpeg(resized);
    }

    private static BufferedImage resize(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longEdge = Math.max(width, height);

        if (longEdge <= MAX_EDGE) {
            // Still re-encoded below: a small photo carries exactly the same
            // GPS tags as a large one, so "already small enough" is not a
            // reason to skip the strip.
            return toRgb(source);
        }

        double scale = (double) MAX_EDGE / longEdge;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        // TYPE_INT_RGB, not the source type: a PNG with alpha would otherwise
        // encode to JPEG with inverted colours, which is a classic and very
        // visible bug.
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", out)) {
            throw new IOException("No JPEG writer available");
        }
        return out.toByteArray();
    }
}
