package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이미지 직접 업로드용 Presigned URL 정보")
public record PresignedUrlInfo(

        @Schema(description = "업로드될 이미지의 R2 저장 key. 업로드 완료 후 작품 업로드·수정 요청의 imageKeys에 그대로 사용합니다",
                example = "raw/019ff383-2c63-765b-af15-8a2c870a29ec.jpg")
        String key, // R2 저장 key

        @Schema(description = "R2에 직접 PUT 업로드할 Presigned URL. 발급 후 10분간 유효하며, "
                + "요청 시 Content-Type 헤더를 발급 때 지정한 값과 동일하게 보내야 합니다",
                example = "https://at-crew-storage.r2.cloudflarestorage.com/raw/019ff383-2c63-765b-af15-8a2c870a29ec.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=600")
        String uploadUrl // Presigned PUT URL
) {
}
