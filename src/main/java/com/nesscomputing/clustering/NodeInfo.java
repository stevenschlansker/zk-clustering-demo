package com.nesscomputing.clustering;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeInfo
{
    private final UUID nodeId;

    @JsonCreator
    NodeInfo(@JsonProperty("nodeId") UUID nodeId)
    {
        this.nodeId = nodeId;
    }

    public UUID getNodeId()
    {
        return nodeId;
    }

    @Override
    public String toString()
    {
        return "Node " + nodeId;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((nodeId == null) ? 0 : nodeId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        NodeInfo other = (NodeInfo) obj;
        if (nodeId == null) {
            if (other.nodeId != null)
                return false;
        } else if (!nodeId.equals(other.nodeId))
            return false;
        return true;
    }
}
