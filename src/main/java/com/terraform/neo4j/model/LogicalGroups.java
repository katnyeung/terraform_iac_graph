package com.terraform.neo4j.model;

import java.util.List;
import java.util.Map;

/**
 * Model representing logical groupings of Terraform resources
 */
public class LogicalGroups {
    private Map<String, List<String>> groupedResources;
    private Map<String, String> groupDescriptions;
    private List<String> groupOrder;
    private Map<String, String> resourceToGroup;

    public LogicalGroups() {}

    public LogicalGroups(Map<String, List<String>> groupedResources,
                        Map<String, String> groupDescriptions,
                        List<String> groupOrder) {
        this.groupedResources = groupedResources;
        this.groupDescriptions = groupDescriptions;
        this.groupOrder = groupOrder;
        this.resourceToGroup = buildResourceToGroupMap();
    }

    private Map<String, String> buildResourceToGroupMap() {
        Map<String, String> resourceToGroup = new java.util.HashMap<>();
        if (groupedResources != null) {
            for (Map.Entry<String, List<String>> entry : groupedResources.entrySet()) {
                String groupName = entry.getKey();
                List<String> resources = entry.getValue();
                for (String resource : resources) {
                    resourceToGroup.put(resource, groupName);
                }
            }
        }
        return resourceToGroup;
    }

    // Getters and setters
    public Map<String, List<String>> getGroupedResources() {
        return groupedResources;
    }

    public void setGroupedResources(Map<String, List<String>> groupedResources) {
        this.groupedResources = groupedResources;
        this.resourceToGroup = buildResourceToGroupMap();
    }

    public Map<String, String> getGroupDescriptions() {
        return groupDescriptions;
    }

    public void setGroupDescriptions(Map<String, String> groupDescriptions) {
        this.groupDescriptions = groupDescriptions;
    }

    public List<String> getGroupOrder() {
        return groupOrder;
    }

    public void setGroupOrder(List<String> groupOrder) {
        this.groupOrder = groupOrder;
    }

    public Map<String, String> getResourceToGroup() {
        return resourceToGroup;
    }

    /**
     * Get the group name for a specific resource
     */
    public String getGroupForResource(String resource) {
        return resourceToGroup != null ? resourceToGroup.get(resource) : null;
    }

    /**
     * Get all resources in a specific group
     */
    public List<String> getResourcesInGroup(String groupName) {
        return groupedResources != null ? groupedResources.get(groupName) : null;
    }

    /**
     * Get the total number of groups
     */
    public int getTotalGroups() {
        return groupedResources != null ? groupedResources.size() : 0;
    }

    /**
     * Get the total number of resources across all groups
     */
    public int getTotalResources() {
        return groupedResources != null ? 
            groupedResources.values().stream().mapToInt(List::size).sum() : 0;
    }
}