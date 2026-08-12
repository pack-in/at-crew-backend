package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.BookmarkEntryInfo;
import com.atcrew.artwork.BookmarkFolderInfo;
import com.atcrew.artwork.BookmarkService;
import com.atcrew.artwork.internal.web.dto.CreateBookmarkFolderRequest;
import com.atcrew.artwork.internal.web.dto.MoveBookmarkRequest;
import com.atcrew.artwork.internal.web.dto.SaveBookmarkRequest;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "북마크", description = "북마크 폴더 및 작품 저장 관리 API")
@RestController
@RequestMapping("/api/bookmarks")
class BookmarkController {

    private final BookmarkService bookmarkService;
    private final SecurityUtils securityUtils;

    BookmarkController(BookmarkService bookmarkService, SecurityUtils securityUtils) {
        this.bookmarkService = bookmarkService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "북마크 폴더 목록",
            description = """
                    내가 만든 북마크 폴더를 sortOrder 오름차순(생성 순)으로 조회합니다.

                    폴더를 하나도 만들지 않았으면 빈 배열입니다. 폴더에 넣지 않은 북마크가 담기는 기본 폴더(미분류)는
                    실제 폴더가 아니어서 이 목록에 나오지 않으며, 북마크 목록 API에서 folderId를 생략해 조회합니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/folders")
    public ApiResponse<List<BookmarkFolderInfo>> getFolders() {
        return ApiResponse.success(bookmarkService.getFolders(securityUtils.getCurrentMemberId()));
    }

    @Operation(summary = "북마크 폴더 생성",
            description = """
                    북마크 폴더를 만듭니다. 폴더명은 앞뒤 공백을 제거해 저장하며 같은 회원 안에서 중복될 수 없습니다.

                    sortOrder에는 생성 시점의 보유 폴더 수가 부여됩니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "COMMON_INVALID_INPUT(폴더명이 비었거나 공백만 있거나 20자를 초과)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "BOOKMARK_FOLDER_DUPLICATE_NAME(이미 존재하는 폴더명)")
    })
    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookmarkFolderInfo> createFolder(
            @RequestBody @Valid CreateBookmarkFolderRequest request) {
        return ApiResponse.success(
                bookmarkService.createFolder(securityUtils.getCurrentMemberId(), request.name()));
    }

    @Operation(summary = "북마크 폴더 삭제",
            description = """
                    내 북마크 폴더를 삭제합니다. 응답 본문은 없습니다.

                    폴더에 들어 있던 북마크는 함께 삭제되지 않고 기본 폴더(미분류)로 이동합니다.
                    내 폴더가 아니거나 이미 삭제된 폴더는 404 BOOKMARK_FOLDER_NOT_FOUND입니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "BOOKMARK_FOLDER_NOT_FOUND(존재하지 않거나 내 폴더가 아님)")
    })
    @DeleteMapping("/folders/{folderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(
            @Parameter(description = "폴더 ID", example = "019ff383-2ce0-725e-98b5-8be4f4764838")
            @PathVariable String folderId) {
        bookmarkService.deleteFolder(securityUtils.getCurrentMemberId(), folderId);
    }

    @Operation(summary = "북마크 목록",
            description = """
                    내 북마크를 저장 시각 내림차순으로 조회합니다. folderId를 생략하면 기본 폴더(미분류)의 북마크만 조회됩니다.

                    이 목록에는 READY이면서 공개 범위가 PUBLIC인 작품만 나옵니다 — 북마크한 뒤 작품이 휴지통으로 가거나
                    LINK_ONLY·PRIVATE로 바뀌면 북마크 자체는 남아 있지만(해제도 가능) 목록에서는 빠집니다.
                    이 필터링은 페이지를 자른 뒤에 적용되므로 items 길이가 size보다 짧을 수 있습니다 — 페이지 종료 판단은 items 길이가 아니라
                    hasNext/nextCursor로 하세요.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_CURSOR(커서 형식 오류)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<CursorPage<BookmarkEntryInfo>> getBookmarks(
            @Parameter(description = "폴더 ID. 생략하면 기본 폴더(미분류)를 조회합니다",
                    example = "019ff383-2ce0-725e-98b5-8be4f4764838")
            @RequestParam(required = false) String folderId,
            @Parameter(description = "커서 — 직전 페이지 응답의 nextCursor(마지막 북마크 savedAt의 epoch milli). "
                    + "첫 페이지는 생략합니다", example = "1786496888061")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50 — 초과 값은 50으로 잘립니다)", example = "20")
            @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        int pageSize = size != null ? Math.min(size, 50) : 20;
        return ApiResponse.success(bookmarkService.getBookmarks(memberId, folderId, cursor, pageSize));
    }

    @Operation(summary = "북마크 저장",
            description = """
                    작품을 북마크에 저장합니다. folderId를 생략하면 기본 폴더(미분류)에 저장됩니다.

                    저장 대상은 이미지 처리가 끝나(READY) 열람 가능한 작품만 가능합니다 — 본인 작품이라도 처리 중이거나 휴지통에 있으면
                    404 ARTWORK_NOT_FOUND입니다. 한 작품은 폴더와 무관하게 회원당 한 번만 북마크할 수 있고, 중복 저장은 409입니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "저장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ARTWORK_NOT_FOUND(존재하지 않거나 북마크할 수 없는 작품), BOOKMARK_FOLDER_NOT_FOUND(존재하지 않는 폴더)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "BOOKMARK_ALREADY_EXISTS(이미 북마크한 작품)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookmarkEntryInfo> saveBookmark(@RequestBody @Valid SaveBookmarkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(
                bookmarkService.saveBookmark(memberId, request.artworkId(), request.folderId()));
    }

    @Operation(summary = "북마크 해제",
            description = """
                    작품 ID로 내 북마크를 해제합니다. 응답 본문은 없습니다. 경로 변수는 북마크 ID가 아니라 작품 ID입니다.

                    북마크하지 않은 작품이거나 이미 해제한 경우 404 BOOKMARK_NOT_FOUND입니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "해제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "BOOKMARK_NOT_FOUND(북마크하지 않은 작품)")
    })
    @DeleteMapping("/{artworkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBookmark(
            @Parameter(description = "작품 ID (북마크 ID가 아님)", example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
            @PathVariable String artworkId) {
        bookmarkService.removeBookmark(securityUtils.getCurrentMemberId(), artworkId);
    }

    @Operation(summary = "북마크 폴더 이동",
            description = """
                    선택한 작품들의 북마크를 다른 폴더로 한 번에 옮깁니다. 응답 본문은 없습니다.

                    targetFolderId를 생략하거나 null로 보내면 기본 폴더(미분류)로 이동합니다.
                    내 북마크에 없는 작품 ID는 오류 없이 무시되므로, 요청한 전부가 옮겨졌는지 확인하려면 북마크 목록을 다시 조회하세요.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "이동 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "COMMON_INVALID_INPUT(artworkIds가 비어 있음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "BOOKMARK_FOLDER_NOT_FOUND(존재하지 않거나 내 폴더가 아님)")
    })
    @PatchMapping("/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moveBookmarks(@RequestBody @Valid MoveBookmarkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        bookmarkService.moveBookmarks(memberId, request.artworkIds(), request.targetFolderId());
    }
}
