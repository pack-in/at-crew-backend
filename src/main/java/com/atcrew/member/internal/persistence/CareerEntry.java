package com.atcrew.member.internal.persistence;

class CareerEntry {

    private String id;
    private String workTitle;
    private String episodeCount;
    private String startDate;
    private String endDate;    // 연재중이면 null
    private boolean ongoing;
    private String description;

    protected CareerEntry() {
    }

    CareerEntry(String id, String workTitle, String episodeCount,
                String startDate, String endDate, boolean ongoing, String description) {
        this.id = id;
        this.workTitle = workTitle;
        this.episodeCount = episodeCount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.ongoing = ongoing;
        this.description = description;
    }

    String getId() { return id; }
    String getWorkTitle() { return workTitle; }
    String getEpisodeCount() { return episodeCount; }
    String getStartDate() { return startDate; }
    String getEndDate() { return endDate; }
    boolean isOngoing() { return ongoing; }
    String getDescription() { return description; }
}
