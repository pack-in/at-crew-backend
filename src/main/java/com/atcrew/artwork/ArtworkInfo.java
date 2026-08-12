package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "작품 상세 정보")
public record ArtworkInfo(

        @Schema(description = "작품 ID (UUIDv7)", example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
        String id, // 작품 ID

        @Schema(description = "작가 회원 ID", example = "019ff383-2bfe-7b37-b543-bccd47c2c1fc")
        String authorId, // 작가 회원 ID

        @Schema(description = "작가 이름·작가명. 작가 정보를 조회할 수 없으면 null", example = "김작가", nullable = true)
        String authorName, // 작가 이름

        @Schema(description = "작가 핸들(@ 제외). 작가 정보를 조회할 수 없으면 null", example = "creator_kim", nullable = true)
        String authorHandle, // 작가 핸들

        @Schema(description = "작품 제목 (최대 100자)", example = "겨울 숲의 아이")
        String title, // 작품 제목

        @Schema(description = "작품 설명 (최대 500자). 미입력 시 null", example = "겨울 숲을 배경으로 그린 일러스트입니다.", nullable = true)
        String description, // 작품 설명

        @ArraySchema(arraySchema = @Schema(description = "작품 이미지 목록. 업로드 순서대로 정렬됩니다"))
        List<ArtworkImageInfo> images, // 작품 이미지 목록

        @Schema(description = "대표 이미지 인덱스 (images 배열 기준, 0부터 시작)", example = "0")
        int representativeImageIndex, // 대표 이미지 인덱스

        @Schema(description = "작가가 직접 지정한 썸네일 R2 key. 미지정 시 null이며, 이 경우 대표 이미지의 thumbKey를 사용합니다",
                example = "raw/019ff383-2c63-765b-af15-8a2c870a29ec.jpg", nullable = true)
        String thumbnailKey, // 직접 지정 썸네일 key

        @Schema(description = "이미지 배치 방식 (VERTICAL_SCROLL: 세로 스크롤, HORIZONTAL_SWIPE: 가로 스와이프)",
                example = "VERTICAL_SCROLL")
        ImageLayoutType imageLayoutType, // 이미지 배치 방식

        @Schema(description = "작품 분야 (ILLUSTRATION: 일러스트, WEBTOON: 웹툰, PRINT_COMIC: 출판만화, "
                + "ANIMATION: 애니메이션, ETC: 기타)", example = "ILLUSTRATION")
        ArtworkField artworkField, // 작품 분야

        @Schema(description = "창작 유형 (ORIGINAL: 1차 창작, SECONDARY: 2차 창작, FAN_ART: 팬아트, "
                + "OC: 오리지널 캐릭터, COMMISSION: 커미션)", example = "ORIGINAL")
        CreativeType creativeType, // 창작 유형

        @ArraySchema(arraySchema = @Schema(
                description = "참여 역할 목록 (TOTAL_ARTWORK: 총작화, ADAPTATION_STORYBOARD: 각색콘티, STORYBOARD: 콘티, "
                        + "DIRECTION: 연출, LINEART: 선화, SKETCH: 스케치, COLORING: 채색, BASE_COLOR: 밑색, TONE_WORK: 1도명암, "
                        + "POST_PROCESSING: 후보정, FULL_COLOR: 풀채색, PANEL_DECORATION: 컷꾸미기, THREE_D_MODELING: 3D모델링, "
                        + "MATERIAL_MAKING: 소재제작, MATERIAL_PLACEMENT: 소재배치, BACKGROUND: 배경, WEBNOVEL_COVER: 웹소설표지, "
                        + "CHARACTER_DESIGN: 캐릭터디자인, CHARACTER_SHEET: 캐릭터시트, TYPOGRAPHY: 타이포, "
                        + "BROADCAST_THUMBNAIL: 방송썸네일, ETC: 직접입력). 중복은 제거되며 enum 선언 순서로 정렬됩니다",
                example = "[\"TOTAL_ARTWORK\", \"COLORING\"]"))
        List<ArtworkRole> roles, // 참여 역할 목록

        @ArraySchema(arraySchema = @Schema(description = "장르 목록. 중복 제거 후 가나다순 정렬되며, 미입력 시 빈 배열",
                example = "[\"판타지\"]"))
        List<String> genres, // 장르 목록

        @ArraySchema(arraySchema = @Schema(description = "태그 목록 (최대 7개). 중복 제거 후 가나다순 정렬되며, 미입력 시 빈 배열",
                example = "[\"겨울\", \"판타지\"]"))
        List<String> tags, // 태그 목록

        @ArraySchema(arraySchema = @Schema(description = "사용 툴 목록. 중복 제거 후 가나다순 정렬되며, 미입력 시 빈 배열",
                example = "[\"Photoshop\", \"Clip Studio Paint\"]"))
        List<String> tools, // 사용 툴 목록

        @Schema(description = "작업 소요 시간. 미입력 시 null", nullable = true)
        WorkDuration workDuration, // 작업 소요 시간

        @Schema(description = "컷 수. 미입력 시 null", example = "12", nullable = true)
        Integer cutCount, // 컷 수

        @ArraySchema(arraySchema = @Schema(description = "영상 링크 목록 (최대 5개). 미입력 시 빈 배열",
                example = "[\"https://youtu.be/abcdefg\"]"))
        List<String> videoLinks, // 영상 링크 목록

        @Schema(description = "연령 등급 (ALL: 전체연령가, R18: 성인물-성적 콘텐츠, G18: 성인물-고어/폭력)",
                example = "ALL")
        AgeRating ageRating, // 연령 등급

        @Schema(description = "공개 범위 (PUBLIC: 전체 공개, LINK_ONLY: 링크 공개, PRIVATE: 비공개). "
                + "휴지통으로 이동하면 PRIVATE로 강제 변경되고, 복구 시 삭제 직전 값으로 되돌아갑니다",
                example = "PUBLIC")
        Visibility visibility, // 공개 범위

        @ArraySchema(arraySchema = @Schema(description = "사용 소재 목록. 미입력 시 빈 배열"))
        List<MaterialInfo> materials, // 사용 소재 목록

        @Schema(description = "작품 상태 (PROCESSING: 이미지 처리 중, READY: 공개 가능, DELETED: 휴지통). "
                + "업로드 직후 PROCESSING으로 시작해 모든 이미지 처리가 끝나면 READY로 전이하고, "
                + "이미지를 교체하면 다시 PROCESSING으로 돌아갑니다", example = "READY")
        ArtworkStatus status, // 작품 상태

        @Schema(description = "업로드 시각 (UTC ISO-8601)", example = "2026-08-12T01:08:07.918972Z")
        Instant createdAt, // 업로드 시각

        @Schema(description = "최종 수정 시각 (UTC ISO-8601)", example = "2026-08-12T01:08:07.972520Z")
        Instant updatedAt // 최종 수정 시각
) {
}
