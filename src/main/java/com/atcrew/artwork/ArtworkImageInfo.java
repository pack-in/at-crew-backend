package com.atcrew.artwork;

public record ArtworkImageInfo(
        /**
         * 업로드된 raw 원본의 key. 처리가 끝나면 R2에서 실제 객체가 삭제되므로(변환 결과가 원본을 대체한다)
         * 이미지를 불러오는 데 쓰면 안 된다 — 표시용은 originalAvifKey(상세)·thumbKey(카드)다.
         */
        String originalKey,
        String thumbKey,
        String thumbAdultKey,
        String originalAvifKey,
        ImageProcessingStatus processingStatus
) {
}
