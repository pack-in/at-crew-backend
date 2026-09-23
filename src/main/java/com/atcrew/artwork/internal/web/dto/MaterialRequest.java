package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.MaterialTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MaterialRequest(
        @NotBlank String name,
        List<MaterialTarget> targets,
        List<String> customTargets,
        // 상한이 없으면 한 요청으로 수천 개의 key를 넣을 수 있다 — 소유 검증(#190)도 그만큼 돌아간다.
        @Size(max = 30) List<@Size(max = 500) String> attachmentKeys,
        List<String> links
) {
}
