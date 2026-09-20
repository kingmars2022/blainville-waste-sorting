package com.bienvenueblainville.photo;

import com.bienvenueblainville.photo.lambda.PhotoProcessor;
import com.bienvenueblainville.support.PhotoFixtures;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The privacy guarantee, tested against a real JPEG carrying real EXIF.
 *
 * <p>A photo taken at the curb outside someone's house carries their home
 * address in its GPS tags, to within a few metres. "We re-encode, so metadata
 * is dropped" is the kind of claim that is true until someone adds a
 * fast-path for images that are already small — so it is asserted by reading
 * the metadata back out, not by trusting the implementation.
 */
class PhotoProcessorTest {

    @Test
    void stripsGpsCoordinatesFromThePhoto() throws Exception {
        byte[] withGps = PhotoFixtures.jpegWithGps(2000, 1500);

        // The fixture has to be genuinely incriminating or the test proves
        // nothing.
        assertThat(gpsDirectories(withGps)).isNotEmpty();

        byte[] processed = PhotoProcessor.process(withGps);

        assertThat(gpsDirectories(processed)).isEmpty();
    }

    @Test
    void decodesTheWebPTheUploadAllowListPromisesToAccept() throws Exception {
        // PhotoController signs upload URLs for image/webp. A stock JVM has no
        // WebP reader, so every one of those uploads used to reach the
        // processor, fail to decode, and leave the photo permanently
        // unavailable - the allow-list promised a format the pipeline could
        // not handle. Asserting on a real WebP file is the only way to catch
        // that: it fails the moment the decoder is dropped from the classpath.
        byte[] webp;
        try (var in = PhotoProcessorTest.class.getResourceAsStream("/photo/sample.webp")) {
            webp = in.readAllBytes();
        }

        assertThat(ImageIO.read(new ByteArrayInputStream(webp)))
                .as("the fixture really is a WebP this JVM can now read")
                .isNotNull();

        byte[] processed = PhotoProcessor.process(webp);

        BufferedImage out = ImageIO.read(new ByteArrayInputStream(processed));
        assertThat(out).isNotNull();
        // Re-encoded as JPEG and resized to the long-edge limit, like any other
        // accepted format.
        assertThat(Math.max(out.getWidth(), out.getHeight())).isEqualTo(1024);
        assertThat(gpsDirectories(processed)).isEmpty();
    }

    @Test
    void stripsMetadataEvenWhenThePhotoIsAlreadySmallEnoughToSkipResizing() throws Exception {
        // The obvious optimisation - "already under the limit, return as-is" -
        // would quietly reintroduce the leak for every phone screenshot and
        // every already-resized upload.
        byte[] small = PhotoFixtures.jpegWithGps(320, 240);
        assertThat(gpsDirectories(small)).isNotEmpty();

        assertThat(gpsDirectories(PhotoProcessor.process(small))).isEmpty();
    }

    @Test
    void resizesDownToTheLongEdgeAndKeepsTheAspectRatio() throws Exception {
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(
                PhotoProcessor.process(PhotoFixtures.jpegWithGps(4000, 2000))));

        assertThat(result.getWidth()).isEqualTo(1024);
        assertThat(result.getHeight()).isEqualTo(512);
    }

    @Test
    void leavesASmallPhotoAtItsOriginalSize() throws Exception {
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(
                PhotoProcessor.process(PhotoFixtures.jpegWithGps(640, 480))));

        assertThat(result.getWidth()).isEqualTo(640);
        assertThat(result.getHeight()).isEqualTo(480);
    }

    @Test
    void rejectsSomethingThatIsNotAnImage() {
        // Writing a "processed" copy of an undecodable upload would produce a
        // file that looks fine until someone opens it.
        assertThatThrownBy(() -> PhotoProcessor.process("not a photo".getBytes()))
                .hasMessageContaining("Unsupported or corrupt image");
    }

    // ------------------------------------------------------------- helpers

    private static Iterable<GpsDirectory> gpsDirectories(byte[] jpeg) throws Exception {
        Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(jpeg));
        return metadata.getDirectoriesOfType(GpsDirectory.class);
    }
}
