package com.atcrew.artwork;

import com.atcrew.common.response.CursorPage;

import java.util.List;

public interface BookmarkService {

    List<BookmarkFolderInfo> getFolders(String memberId);

    BookmarkFolderInfo createFolder(String memberId, String name);

    void deleteFolder(String memberId, String folderId);

    CursorPage<BookmarkEntryInfo> getBookmarks(String memberId, String folderId, String cursor, int size);

    BookmarkEntryInfo saveBookmark(String memberId, String artworkId, String folderId);

    void removeBookmark(String memberId, String artworkId);

    void moveBookmarks(String memberId, List<String> artworkIds, String targetFolderId);
}
