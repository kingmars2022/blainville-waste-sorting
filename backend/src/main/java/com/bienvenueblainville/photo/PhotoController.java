package com.bienvenueblainville.photo;

import com.bienvenueblainville.assistant.AssistantRateLimiter;
import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.photo.dto.PhotoAnswer;
import com.bienvenueblainville.photo.dto.UploadRequest;
import com.bienvenueblainville.photo.dto.UploadTicket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {
    /**
     * An allow-list, not a block-list, and checked against the value that gets
     * signed into the URL. Anything else is rejected before a signature exists,
     * so the application never issues permission to upload something it will
     * not process.
     */
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** Photo ids are generated here, so anything that is not one is a probe. */
    private static final Pattern PHOTO_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final PhotoStorage storage;
    private final PhotoProperties properties;
    private final AssistantRateLimiter rateLimiter;
    private final PhotoSortingService sorting;

    public PhotoController(
            PhotoStorage storage,
            PhotoProperties properties,
            AssistantRateLimiter rateLimiter,
            PhotoSortingService sorting
    ) {
        this.storage = storage;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.sorting = sorting;
    }

    /**
     * Hands back a URL the browser uploads to directly. The bytes never pass
     * through this application.
     */
    @PostMapping("/upload-url")
    public UploadTicket uploadUrl(@Valid @RequestBody UploadRequest request, HttpServletRequest http) {
        // Signing an upload URL is cheap, but each one issued is storage
        // someone else pays for, so it shares the assistant's per-IP quota.
        rateLimiter.check(http.getRemoteAddr());

        if (!ALLOWED_TYPES.contains(request.contentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Upload a JPEG, PNG or WebP photo.");
        }
        if (request.contentLength() > properties.maxUploadBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "That photo is larger than " + (properties.maxUploadBytes() / 1_000_000) + " MB.");
        }

        String photoId = UUID.randomUUID().toString();
        return new UploadTicket(
                photoId,
                storage.presignUpload(photoId, request.contentType(), request.contentLength()),
                properties.uploadTtl());
    }

    /**
     * What is this, and which bin does it go in?
     *
     * <p>Two steps, deliberately separate: the vision model names the object,
     * and the municipal guide decides the bin. See {@link PhotoSortingService}
     * for why a vision model must not be asked the second question.
     */
    @PostMapping("/{photoId}/identify")
    public PhotoAnswer identify(
            @PathVariable String photoId,
            @RequestParam LanguageCode language,
            HttpServletRequest http
    ) {
        requirePhotoId(photoId);
        // A vision call is the most expensive request this application can
        // make, so it shares the assistant's per-IP quota.
        rateLimiter.check(http.getRemoteAddr());

        return sorting.identify(photoId, language)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "That photo is not ready yet, or does not exist."));
    }

    /**
     * Serves the processed copy — never the original.
     *
     * <p>That distinction is the whole privacy story: the original still holds
     * the GPS coordinates of wherever the resident was standing. Only the
     * Lambda's output, with the metadata stripped, is reachable.
     *
     * <p>In the deployed architecture this route is API Gateway's job, reading
     * from S3 without waking the application at all; it is here so the feature
     * works before any of that is provisioned.
     */
    @GetMapping("/{photoId}")
    public ResponseEntity<byte[]> processed(@PathVariable String photoId) {
        requirePhotoId(photoId);

        return storage.readProcessed(photoId)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(bytes))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "That photo is not ready yet, or does not exist."));
    }

    private static void requirePhotoId(String photoId) {
        if (!PHOTO_ID.matcher(photoId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a photo id.");
        }
    }
}
