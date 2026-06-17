package com.atcrew.artwork.internal.domain.artwork;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document(collection = "orphanedImageKeys")
public class OrphanedImageKey {

    @Id
    private String id;

    private List<String> keys;
    private Instant markedAt;

    protected OrphanedImageKey() {
    }

    public static OrphanedImageKey from(ArtworkImage image) {
        OrphanedImageKey orphan = new OrphanedImageKey();
        orphan.id = UUID.randomUUID().toString();
        orphan.keys = new ArrayList<>();
        if (image.getOriginalKey() != null) orphan.keys.add(image.getOriginalKey());
        if (image.getThumbKey() != null) orphan.keys.add(image.getThumbKey());
        if (image.getThumbAdultKey() != null) orphan.keys.add(image.getThumbAdultKey());
        if (image.getOriginalAvifKey() != null) orphan.keys.add(image.getOriginalAvifKey());
        orphan.markedAt = Instant.now();
        return orphan;
    }

    public static OrphanedImageKey ofKeys(List<String> rawKeys) {
        OrphanedImageKey orphan = new OrphanedImageKey();
        orphan.id = UUID.randomUUID().toString();
        orphan.keys = rawKeys.stream().filter(k -> k != null && !k.isBlank()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        orphan.markedAt = Instant.now();
        return orphan;
    }

    public String getId() { return id; }
    public List<String> getKeys() { return keys; }
    public Instant getMarkedAt() { return markedAt; }
}
