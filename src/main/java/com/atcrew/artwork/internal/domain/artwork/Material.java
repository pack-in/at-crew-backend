package com.atcrew.artwork.internal.domain.artwork;

import java.util.List;

public class Material {

    private String name;
    private List<String> targets;
    private List<String> attachmentKeys;

    protected Material() {
    }

    public Material(String name, List<String> targets, List<String> attachmentKeys) {
        this.name = name;
        this.targets = targets;
        this.attachmentKeys = attachmentKeys;
    }

    public String getName() { return name; }
    public List<String> getTargets() { return targets; }
    public List<String> getAttachmentKeys() { return attachmentKeys; }
}
