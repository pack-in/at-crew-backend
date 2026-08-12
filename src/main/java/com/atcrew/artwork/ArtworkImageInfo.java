package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "작품 이미지 한 장의 원본·변환본 R2 key 정보")
public record ArtworkImageInfo(

        @Schema(description = "업로드한 원본 이미지 R2 key. Presigned URL 발급 응답의 key와 동일합니다",
                example = "raw/019ff383-2c63-765b-af15-8a2c870a29ec.jpg")
        String originalKey, // 원본 이미지 key

        @Schema(description = "변환된 썸네일 R2 key. 처리 완료(DONE) 전에는 null",
                example = "thumb/019ff383-2c63-765b-af15-8a2c870a29ec.webp", nullable = true)
        String thumbKey, // 썸네일 key

        @Schema(description = "성인물 블러 처리 썸네일 R2 key. 처리 완료 전이거나 블러본을 생성하지 않았으면 null",
                example = "thumb-adult/019ff383-2c63-765b-af15-8a2c870a29ec.webp", nullable = true)
        String thumbAdultKey, // 블러 썸네일 key

        @Schema(description = "AVIF로 변환한 원본 R2 key. 처리 완료 전에는 null",
                example = "avif/019ff383-2c63-765b-af15-8a2c870a29ec.avif", nullable = true)
        String originalAvifKey, // AVIF 원본 key

        @Schema(description = "이미지 처리 상태 (PENDING: 처리 대기·진행 중, DONE: 처리 완료, FAILED: 처리 실패). "
                + "모든 이미지가 PENDING을 벗어나고 하나 이상 DONE이면 작품 상태가 READY로 전이합니다",
                example = "DONE")
        ImageProcessingStatus processingStatus // 이미지 처리 상태
) {
}
