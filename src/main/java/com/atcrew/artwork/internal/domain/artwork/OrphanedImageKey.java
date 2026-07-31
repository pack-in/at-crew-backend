package com.atcrew.artwork.internal.domain.artwork;

import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orphaned_image_keys")
public class OrphanedImageKey implements Persistable<String> {

    @Id
    private String id;

    // 정리 작업 큐 성격 — 통째로 읽고 통째로 지우므로 JSON 컬럼으로 저장(§3.2)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keys_json")
    private List<String> keys;

    private Instant markedAt;

    // MariaDB 전환(docs/design/mariadb-migration-design.md §3.1) — 애플리케이션이 ID를 직접 할당하므로
    // Persistable로 신규 여부를 명시해 신규 엔티티는 persist(INSERT)로 바로 처리되게 한다.
    @Transient
    private boolean isNew = false;

    protected OrphanedImageKey() {
    }

    public static OrphanedImageKey from(ArtworkImage image) {
        OrphanedImageKey orphan = new OrphanedImageKey();
        orphan.id = UuidV7Generator.generate();
        orphan.keys = new ArrayList<>();
        if (image.getOriginalKey() != null) orphan.keys.add(image.getOriginalKey());
        if (image.getThumbKey() != null) orphan.keys.add(image.getThumbKey());
        if (image.getThumbAdultKey() != null) orphan.keys.add(image.getThumbAdultKey());
        if (image.getOriginalAvifKey() != null) orphan.keys.add(image.getOriginalAvifKey());
        orphan.markedAt = Instant.now();
        orphan.isNew = true;
        return orphan;
    }

    public static OrphanedImageKey ofKeys(List<String> rawKeys) {
        OrphanedImageKey orphan = new OrphanedImageKey();
        orphan.id = UuidV7Generator.generate();
        orphan.keys = rawKeys.stream().filter(k -> k != null && !k.isBlank()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        orphan.markedAt = Instant.now();
        orphan.isNew = true;
        return orphan;
    }

    @Override
    public String getId() { return id; }
    public List<String> getKeys() { return keys; }
    public Instant getMarkedAt() { return markedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
