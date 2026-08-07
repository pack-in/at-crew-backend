package com.atcrew.artwork;

import java.util.List;

public record MaterialData(
        String name,
        List<MaterialTarget> targets,
        List<String> attachmentKeys,  // R2 업로드 이미지 키
        List<String> links            // 외부 소재 URL (acon3d 등)
) {
}
