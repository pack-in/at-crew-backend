package com.atcrew.media.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    // 아직 참조 중이라 지울 수 없는 key만 남긴다 — 나머지는 이미 R2에서 지웠으므로 다음 배치가
    // 남은 key만 다시 판정한다(RetainedMediaKeyProvider). markedAt을 재판정 시점으로 갱신해 이 행을
    // 큐 뒤로 보낸다 — 보존 대상이 오래 쌓여도 뒤의 행이 밀리지 않는다.
    public void keepOnly(Set<String> retainedKeys) {
        keys = keys.stream().filter(retainedKeys::contains)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        markedAt = Instant.now();
    }
    @Override public String getId() { return id; }
    public List<String> getKeys() { return keys; }
    @Override public boolean isNew() { return isNew; }
    @PostPersist @PostLoad void markNotNew() { isNew = false; }
}
