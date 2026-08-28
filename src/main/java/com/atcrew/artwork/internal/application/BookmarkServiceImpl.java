package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkAccess;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.BookmarkEntryInfo;
import com.atcrew.artwork.BookmarkFolderInfo;
import com.atcrew.artwork.BookmarkService;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkEntry;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkFolder;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.artwork.internal.persistence.BookmarkEntryRepository;
import com.atcrew.artwork.internal.persistence.BookmarkFolderRepository;
import com.atcrew.common.response.CursorPage;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkFolderRepository folderRepository;
    private final BookmarkEntryRepository entryRepository;
    private final ArtworkRepository artworkRepository;
    private final MemberService memberService;

    BookmarkServiceImpl(BookmarkFolderRepository folderRepository,
                        BookmarkEntryRepository entryRepository,
                        ArtworkRepository artworkRepository,
                        MemberService memberService) {
        this.folderRepository = folderRepository;
        this.entryRepository = entryRepository;
        this.artworkRepository = artworkRepository;
        this.memberService = memberService;
    }

    @Override
    public List<BookmarkFolderInfo> getFolders(String memberId) {
        return folderRepository.findByMemberIdOrderBySortOrderAsc(memberId)
                .stream()
                .map(ArtworkMapper::toFolderInfo)
                .toList();
    }

    @Override
    @Transactional
    public BookmarkFolderInfo createFolder(String memberId, String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isBlank()) {
            throw new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NAME_BLANK);
        }
        if (trimmed.length() > 20) {
            throw new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NAME_BLANK, "폴더명은 최대 20자입니다");
        }
        if (folderRepository.existsByMemberIdAndName(memberId, trimmed)) {
            throw new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_DUPLICATE_NAME, trimmed);
        }
        int sortOrder = folderRepository.countByMemberId(memberId);
        BookmarkFolder folder = BookmarkFolder.create(memberId, trimmed, sortOrder);
        return ArtworkMapper.toFolderInfo(folderRepository.save(folder));
    }

    @Override
    @Transactional
    public void deleteFolder(String memberId, String folderId) {
        BookmarkFolder folder = folderRepository.findByIdAndMemberId(folderId, memberId)
                .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NOT_FOUND, folderId));
        folder.assertOwner(memberId);
        // 폴더에 속한 북마크는 기본 폴더(null)로 이동 — 설계: DB 데이터 보존
        List<BookmarkEntry> entries = entryRepository.findByMemberIdAndFolderIdOrderBySavedAtDesc(memberId, folderId);
        entries.forEach(e -> e.moveToFolder(null));
        entryRepository.saveAll(entries);
        folderRepository.delete(folder);
    }

    @Override
    public CursorPage<BookmarkEntryInfo> getBookmarks(String memberId, String folderId,
                                                       String cursor, int size) {
        int limit = size + 1;
        Instant parsedCursor = cursor != null ? parseCursor(cursor) : null;

        List<BookmarkEntry> entries;
        if (folderId != null) {
            entries = parsedCursor != null
                    ? entryRepository.findByMemberIdAndFolderIdAndSavedAtBeforeOrderBySavedAtDesc(
                            memberId, folderId, parsedCursor, PageRequest.of(0, limit))
                    : entryRepository.findByMemberIdAndFolderIdOrderBySavedAtDesc(
                            memberId, folderId, PageRequest.of(0, limit));
        } else {
            entries = parsedCursor != null
                    ? entryRepository.findByMemberIdAndFolderIdIsNullAndSavedAtBeforeOrderBySavedAtDesc(
                            memberId, parsedCursor, PageRequest.of(0, limit))
                    : entryRepository.findByMemberIdAndFolderIdIsNullOrderBySavedAtDesc(
                            memberId, PageRequest.of(0, limit));
        }

        if (entries.isEmpty()) return CursorPage.empty();
        boolean hasNext = entries.size() > size;
        List<BookmarkEntry> page = hasNext ? entries.subList(0, size) : entries;

        // 노출 기준은 저장 기준(saveBookmark)과 같아야 한다 — visibility == PUBLIC만 보여주면 포트폴리오
        // 한정 공개 작품처럼 저장은 되는데 목록에는 영원히 안 보이는 북마크가 생긴다.
        // 운영 차단 작품은 본인 작품이라도 목록에서 뺀다(마이페이지_작가-R39) — accessFor가 작성자
        // 본인에게는 차단 작품도 허용하므로 여기서 따로 제외한다.
        List<String> artworkIds = page.stream().map(BookmarkEntry::getArtworkId).toList();
        Map<String, Artwork> artworkMap = artworkRepository.findAllById(artworkIds)
                .stream()
                .filter(a -> a.getStatus() == ArtworkStatus.READY
                        && !a.isBlocked()
                        && a.accessFor(memberId) == ArtworkAccess.ALLOWED)
                .collect(Collectors.toMap(Artwork::getId, a -> a));

        Set<String> authorIds = artworkMap.values().stream()
                .map(Artwork::getAuthorId)
                .collect(Collectors.toSet());
        Map<String, MemberInfo> authorMap = authorIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try { return memberService.findById(id); } catch (Exception e) { return null; }
                        }
                ));

        List<BookmarkEntryInfo> items = page.stream()
                .filter(e -> artworkMap.containsKey(e.getArtworkId()))
                .map(e -> {
                    Artwork artwork = artworkMap.get(e.getArtworkId());
                    return ArtworkMapper.toEntryInfo(e,
                            ArtworkMapper.toSummaryInfo(artwork, authorMap.get(artwork.getAuthorId())));
                })
                .toList();

        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getSavedAt().toEpochMilli())
                : null;
        return CursorPage.of(items, nextCursor);
    }

    @Override
    @Transactional
    public BookmarkEntryInfo saveBookmark(String memberId, String artworkId, String folderId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND, artworkId));
        // 북마크는 GONE/PRIVATE를 구분해 보여줄 필요가 없어 접근 불가 사유를 묶어 404로 응답한다.
        // 상태 조건은 기존 isVisibleTo와 동일하게 유지한다 — 본인 작품이라도 처리 중·휴지통 작품은 북마크 대상이 아니다.
        if (artwork.getStatus() != ArtworkStatus.READY
                || artwork.accessFor(memberId) != ArtworkAccess.ALLOWED) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND, artworkId);
        }

        if (folderId != null) {
            folderRepository.findByIdAndMemberId(folderId, memberId)
                    .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NOT_FOUND, folderId));
        }
        if (entryRepository.existsByMemberIdAndArtworkId(memberId, artworkId)) {
            throw new ArtworkException(ArtworkErrorCode.BOOKMARK_ALREADY_EXISTS, artworkId);
        }
        BookmarkEntry entry = BookmarkEntry.create(memberId, artworkId, folderId, artwork.getVisibility());
        BookmarkEntry saved = entryRepository.save(entry);
        // 북마크순 정렬용 집계(이슈 #78) — 중복 저장은 위에서 이미 막았으므로 여기서는 항상 1 증가한다.
        artworkRepository.incrementBookmarkCount(artworkId);
        MemberInfo author = memberService.findById(artwork.getAuthorId());
        return ArtworkMapper.toEntryInfo(saved, ArtworkMapper.toSummaryInfo(artwork, author));
    }

    @Override
    @Transactional
    public void removeBookmark(String memberId, String artworkId) {
        BookmarkEntry entry = entryRepository.findByMemberIdAndArtworkId(memberId, artworkId)
                .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.BOOKMARK_NOT_FOUND, artworkId));
        entryRepository.delete(entry);
        // 작품이 이미 영구 삭제됐으면 갱신 대상이 없어 0행이 바뀐다 — 북마크 해제 자체는 성공시킨다.
        artworkRepository.decrementBookmarkCount(artworkId);
    }

    @Override
    @Transactional
    public void moveBookmarks(String memberId, List<String> artworkIds, String targetFolderId) {
        if (targetFolderId != null) {
            folderRepository.findByIdAndMemberId(targetFolderId, memberId)
                    .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NOT_FOUND, targetFolderId));
        }
        List<BookmarkEntry> entries = entryRepository.findByMemberIdAndArtworkIdIn(memberId, artworkIds);
        entries.forEach(e -> e.moveToFolder(targetFolderId));
        entryRepository.saveAll(entries);
    }

    private Instant parseCursor(String cursor) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(cursor));
        } catch (NumberFormatException e) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_CURSOR);
        }
    }
}
