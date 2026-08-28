package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.BookmarkEntryInfo;
import com.atcrew.artwork.BookmarkFolderInfo;
import com.atcrew.artwork.BookmarkService;
import com.atcrew.artwork.internal.web.dto.CreateBookmarkFolderRequest;
import com.atcrew.artwork.internal.web.dto.MoveBookmarkRequest;
import com.atcrew.artwork.internal.web.dto.RenameBookmarkFolderRequest;
import com.atcrew.artwork.internal.web.dto.SaveBookmarkRequest;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    @Operation(summary = "북마크 폴더 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/folders")
    public ApiResponse<List<BookmarkFolderInfo>> getFolders() {
        return ApiResponse.success(bookmarkService.getFolders(securityUtils.getCurrentMemberId()));
    }

    @Operation(summary = "북마크 폴더 생성")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공")
    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookmarkFolderInfo> createFolder(
            @RequestBody @Valid CreateBookmarkFolderRequest request) {
        return ApiResponse.success(
                bookmarkService.createFolder(securityUtils.getCurrentMemberId(), request.name()));
    }

    @Operation(summary = "북마크 폴더 이름 변경")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공")
    @PatchMapping("/folders/{folderId}")
    public ApiResponse<BookmarkFolderInfo> renameFolder(
            @Parameter(description = "폴더 ID") @PathVariable String folderId,
            @RequestBody @Valid RenameBookmarkFolderRequest request) {
        return ApiResponse.success(
                bookmarkService.renameFolder(securityUtils.getCurrentMemberId(), folderId, request.name()));
    }

    @Operation(summary = "북마크 폴더 삭제", description = "폴더 내 북마크는 기본 폴더로 이동됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/folders/{folderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@Parameter(description = "폴더 ID") @PathVariable String folderId) {
        bookmarkService.deleteFolder(securityUtils.getCurrentMemberId(), folderId);
    }

    @Operation(summary = "북마크 목록", description = "folderId 미지정 시 기본 폴더 조회. 삭제/비공개 작품은 미노출.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ApiResponse<CursorPage<BookmarkEntryInfo>> getBookmarks(
            @Parameter(description = "폴더 ID (미지정 시 기본 폴더)") @RequestParam(required = false) String folderId,
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        int pageSize = size != null ? Math.min(size, 50) : 20;
        return ApiResponse.success(bookmarkService.getBookmarks(memberId, folderId, cursor, pageSize));
    }

    @Operation(summary = "북마크 저장")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "저장 성공")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookmarkEntryInfo> saveBookmark(@RequestBody @Valid SaveBookmarkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(
                bookmarkService.saveBookmark(memberId, request.artworkId(), request.folderId()));
    }

    @Operation(summary = "북마크 해제")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "해제 성공")
    @DeleteMapping("/{artworkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBookmark(@Parameter(description = "작품 ID") @PathVariable String artworkId) {
        bookmarkService.removeBookmark(securityUtils.getCurrentMemberId(), artworkId);
    }

    @Operation(summary = "북마크 폴더 이동")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "이동 성공")
    @PatchMapping("/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moveBookmarks(@RequestBody @Valid MoveBookmarkRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        bookmarkService.moveBookmarks(memberId, request.artworkIds(), request.targetFolderId());
    }
}
