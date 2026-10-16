package com.example.einthusantv;

/** A single native catalogue entry. */
public final class Film {
    public final String id, language, title, poster, synopsis, detailUrl, quality;
    Film(String id, String language, String title, String poster, String synopsis, String detailUrl, String quality) {
        this.id = id; this.language = language; this.title = title;
        this.poster = poster; this.synopsis = synopsis;
        this.detailUrl = detailUrl; this.quality = quality;
    }
}
