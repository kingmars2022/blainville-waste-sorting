package com.bienvenueblainville.common;

import java.util.List;

/**
 * One slice of a listing, plus what the caller needs to page through the rest.
 *
 * <p>Deliberately not Spring Data's {@code Page}: that one serialises with a
 * dozen fields whose shape Spring itself warns is unstable, and this project's
 * mappers are hand-written MyBatis rather than repositories. Four fields is
 * what the admin console reads.
 *
 * @param total how many rows exist, not how many are in {@code items} - the
 *              console says "26-50 of 118", which needs both
 */
public record Page<T>(List<T> items, int page, int size, long total) {
}
