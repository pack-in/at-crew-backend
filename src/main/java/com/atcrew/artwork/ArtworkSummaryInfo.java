package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "작품 목록용 요약 정보")
public record ArtworkSummaryInfo(

        @Schema(description = "작품 ID (UUIDv7)", example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
        String id, // 작품 ID

        @Schema(description = "작가 회원 ID", example = "019ff383-2bfe-7b37-b543-bccd47c2c1fc")
        String authorId, // 작가 회원 ID

        @Schema(description = "작가 이름·작가명. 작가 정보를 조회할 수 없으면 null", example = "김작가", nullable = true)
        String authorName, // 작가 이름

        @Schema(description = "작가 핸들(@ 제외). 작가 정보를 조회할 수 없으면 null", example = "creator_kim", nullable = true)
        String authorHandle, // 작가 핸들

        @Schema(description = "작품 제목", example = "겨울 숲의 아이")
        String title, // 작품 제목

        @Schema(description = "목록 썸네일 R2 key. 작가가 지정한 썸네일이 있으면 그 값, 없으면 대표 이미지의 처리 결과 썸네일. "
                + "이미지 처리가 끝나지 않았으면(status=PROCESSING) null",
                example = "thumb/019ff383-2c63-765b-af15-8a2c870a29ec.webp", nullable = true)
        String thumbKey, // 목록 썸네일 key

        @Schema(description = "성인물 블러 처리 썸네일 R2 key. 작가 지정 썸네일을 쓰거나 이미지 처리 전이면 null",
                example = "thumb-adult/019ff383-2c63-765b-af15-8a2c870a29ec.webp", nullable = true)
        String thumbAdultKey, // 블러 썸네일 key

        @Schema(description = "작품 분야 (ILLUSTRATION: 일러스트, WEBTOON: 웹툰, PRINT_COMIC: 출판만화, "
                + "ANIMATION: 애니메이션, ETC: 기타)", example = "ILLUSTRATION")
        ArtworkField artworkField, // 작품 분야

        @ArraySchema(arraySchema = @Schema(description = "태그 목록 (최대 7개). 가나다순 정렬",
                example = "[\"겨울\", \"판타지\"]"))
        List<String> tags, // 태그 목록

        @Schema(description = "연령 등급 (ALL: 전체연령가, R18: 성인물-성적 콘텐츠, G18: 성인물-고어/폭력)", example = "ALL")
        AgeRating ageRating, // 연령 등급

        @Schema(description = "공개 범위 (PUBLIC: 전체 공개, LINK_ONLY: 링크 공개, PRIVATE: 비공개). "
                + "휴지통 작품은 항상 PRIVATE", example = "PUBLIC")
        Visibility visibility, // 공개 범위

        @Schema(description = "작품 상태 (PROCESSING: 이미지 처리 중, READY: 공개 가능, DELETED: 휴지통)", example = "READY")
        ArtworkStatus status, // 작품 상태

        @Schema(description = "업로드 시각 (UTC ISO-8601). 커서 페이지네이션의 cursor 값은 이 시각의 epoch milli입니다",
                example = "2026-08-12T01:08:07.918972Z")
        Instant createdAt // 업로드 시각
) {
}
