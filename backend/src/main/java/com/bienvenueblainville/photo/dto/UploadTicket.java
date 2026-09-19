package com.bienvenueblainville.photo.dto;

/**
 * @param photoId    the handle the resident uses afterwards
 * @param uploadUrl  presigned; the browser PUTs the bytes straight to S3
 * @param expiresIn  seconds the URL stays valid
 */
public record UploadTicket(String photoId, String uploadUrl, long expiresIn) {
}
