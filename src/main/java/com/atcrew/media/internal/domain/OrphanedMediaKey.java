package com.atcrew.media.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orphaned_media_keys")
public class OrphanedMediaKey implements Persistable<String> {
    @Id private String id;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "keys_json") private List<String> keys;
    private Instant markedAt;
    @Transient private boolean isNew;
    protected OrphanedMediaKey() { }
    public static OrphanedMediaKey ofKeys(List<String> rawKeys) {
        OrphanedMediaKey orphan = new OrphanedMediaKey();
        orphan.id = UuidV7Generator.generate();
        orphan.keys = rawKeys.stream().filter(k -> k != null && !k.isBlank())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        orphan.markedAt = Instant.now(); orphan.isNew = true;
        return orphan;
    }
    @Override public String getId() { return id; }
    public List<String> getKeys() { return keys; }
    @Override public boolean isNew() { return isNew; }
    @PostPersist @PostLoad void markNotNew() { isNew = false; }
}
