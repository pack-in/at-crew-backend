package com.atcrew.artwork;

public record ArtworkImageInfo(
        /**
         * 업로드된 raw 원본의 key. 처리가 끝나면 R2에서 실제 객체가 삭제되므로(변환 결과가 원본을 대체한다)
         * 이미지를 불러오는 데 쓰면 안 된다 — 표시용은 본문이면 originalAvifKey, 썸네일이면 thumbKey다.
         */
        String originalKey,
        // 카드 썸네일(3:4) — 사용자 지정 썸네일(ArtworkInfo.thumbnailImage)에만 있다. 본문 이미지는 null이다
        // (역할 분리 이전에 올라온 본문 이미지에는 남아 있다).
        String thumbKey,
        String thumbAdultKey,
        // 본문 표시용 변환본 — 본문 이미지에만 있고 썸네일은 null이다.
        String originalAvifKey,
        ImageProcessingStatus processingStatus
) {
}
