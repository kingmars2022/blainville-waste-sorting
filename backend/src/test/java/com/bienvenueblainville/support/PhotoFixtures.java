package com.bienvenueblainville.support;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * Builds the kind of photo this feature exists to defend against: a real JPEG
 * with a real EXIF APP1 segment holding GPS coordinates.
 *
 * <p>Shared by the unit test of the image processing and the integration test
 * of the upload pipeline, so both assert against the same fixture rather than
 * two subtly different ideas of what a resident's photo looks like.
 */
public final class PhotoFixtures {
    private PhotoFixtures() {
    }

    /**
     * A real JPEG with a real EXIF APP1 segment holding GPS coordinates —
     * roughly Blainville's own, which is the point: this is what a resident's
     * photo actually carries.
     */
    public static byte[] jpegWithGps(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(120, 160, 130));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);

            ImageWriteParam params = writer.getDefaultWriteParam();
            IIOMetadata metadata = writer.getDefaultImageMetadata(
                    javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image), params);

            // An EXIF APP1 segment, built by hand and attached as an unknown
            // marker - ImageIO has no API for writing EXIF, so the bytes are
            // assembled directly.
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode markerSequence = findOrCreate(root, "markerSequence");

            IIOMetadataNode app1 = new IIOMetadataNode("unknown");
            app1.setAttribute("MarkerTag", "225");
            app1.setUserObject(exifWithGps());
            markerSequence.appendChild(app1);

            metadata.setFromTree(format, root);
            writer.write(null, new IIOImage(image, null, metadata), params);
        } finally {
            writer.dispose();
        }

        return out.toByteArray();
    }

    private static IIOMetadataNode findOrCreate(IIOMetadataNode parent, String name) {
        for (int i = 0; i < parent.getLength(); i++) {
            if (parent.item(i).getNodeName().equals(name)) {
                return (IIOMetadataNode) parent.item(i);
            }
        }
        IIOMetadataNode created = new IIOMetadataNode(name);
        parent.appendChild(created);
        return created;
    }

    /** Minimal TIFF structure: an empty IFD0 pointing at a GPS IFD. */
    private static byte[] exifWithGps() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(256)
                .order(java.nio.ByteOrder.BIG_ENDIAN);

        buffer.put("Exif".getBytes()).put((byte) 0).put((byte) 0);   // APP1 identifier
        int tiffStart = buffer.position();

        buffer.put((byte) 'M').put((byte) 'M').putShort((short) 42); // big-endian TIFF header
        buffer.putInt(8);                                            // offset of IFD0

        buffer.putShort((short) 1);                                  // IFD0: one entry
        buffer.putShort((short) 0x8825);                             // GPSInfoIFDPointer
        buffer.putShort((short) 4);                                  // LONG
        buffer.putInt(1);
        buffer.putInt(26);                                           // offset of the GPS IFD
        buffer.putInt(0);                                            // no IFD1

        buffer.putShort((short) 2);                                  // GPS IFD: two entries
        buffer.putShort((short) 2);                                  // GPSLatitudeRef
        buffer.putShort((short) 2);                                  // ASCII
        buffer.putInt(2);
        buffer.put((byte) 'N').put((byte) 0).putShort((short) 0);

        buffer.putShort((short) 4);                                  // GPSLongitudeRef
        buffer.putShort((short) 2);
        buffer.putInt(2);
        buffer.put((byte) 'W').put((byte) 0).putShort((short) 0);
        buffer.putInt(0);                                            // end of the GPS IFD

        byte[] exif = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(exif);
        return exif;
    }
}
