package com.atcrew.artwork.internal.domain.artwork;

import java.util.List;

public class Material {

    private String name;
    private List<String> targets;
    private List<String> attachmentKeys;
    private List<String> links;

    protected Material() {
    }

    public Material(String name, List<String> targets, List<String> attachmentKeys, List<String> links) {
        this.name = name;
        this.targets = targets;
        this.attachmentKeys = attachmentKeys;
        this.links = links;
    }

    public String getName() { return name; }
    public List<String> getTargets() { return targets; }
    public List<String> getAttachmentKeys() { return attachmentKeys; }
    public List<String> getLinks() { return links; }
}
