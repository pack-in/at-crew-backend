package com.atcrew.search;

public interface SearchService {

    SearchPage<SearchResultItem> search(SearchQuery query);
}
