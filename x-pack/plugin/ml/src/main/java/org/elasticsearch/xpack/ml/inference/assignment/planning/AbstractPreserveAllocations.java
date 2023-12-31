/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.inference.assignment.planning;

import org.elasticsearch.core.Tuple;
import org.elasticsearch.xpack.ml.inference.assignment.planning.AssignmentPlan.Deployment;
import org.elasticsearch.xpack.ml.inference.assignment.planning.AssignmentPlan.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract class AbstractPreserveAllocations {

    private final List<Node> nodes;
    private final List<Deployment> deployments;

    protected AbstractPreserveAllocations(List<Node> nodes, List<Deployment> deployments) {
        this.nodes = Objects.requireNonNull(nodes);
        this.deployments = Objects.requireNonNull(deployments);
    }

    List<Node> nodesPreservingAllocations() {
        return nodes.stream().map(n -> modifyNodePreservingAllocations(n)).toList();
    }

    private Node modifyNodePreservingAllocations(Node n) {
        long bytesUsed = 0;
        int coresUsed = 0;
        for (Deployment m : deployments) {
            if (m.currentAllocationsByNodeId().containsKey(n.id())) {
                bytesUsed += m.memoryBytes();
                coresUsed += calculateUsedCores(n, m);
            }
        }

        return new Node(n.id(), n.availableMemoryBytes() - bytesUsed, n.cores() - coresUsed);
    }

    List<Deployment> modelsPreservingAllocations() {
        return deployments.stream().map(m -> modifyModelPreservingPreviousAssignments(m)).toList();
    }

    Deployment modifyModelPreservingPreviousAssignments(Deployment m) {
        if (m.currentAllocationsByNodeId().isEmpty()) {
            return m;
        }

        return new Deployment(
            m.id(),
            m.memoryBytes(),
            m.allocations() - calculatePreservedAllocations(m),
            m.threadsPerAllocation(),
            calculateAllocationsPerNodeToPreserve(m),
            m.maxAssignedAllocations()
        );
    }

    AssignmentPlan mergePreservedAllocations(AssignmentPlan assignmentPlan) {
        // As the model/node objects the assignment plan are the modified ones,
        // they will not match the models/nodes members we have in this class.
        // Therefore, we build a lookup table based on the ids so we can merge the plan
        // with its preserved allocations.
        final Map<Tuple<String, String>, Integer> assignmentsByModelNodeIdPair = new HashMap<>();
        for (Deployment m : assignmentPlan.models()) {
            Map<Node, Integer> assignments = assignmentPlan.assignments(m).orElse(Map.of());
            for (Map.Entry<Node, Integer> nodeAssignment : assignments.entrySet()) {
                assignmentsByModelNodeIdPair.put(Tuple.tuple(m.id(), nodeAssignment.getKey().id()), nodeAssignment.getValue());
            }
        }

        AssignmentPlan.Builder mergedPlanBuilder = AssignmentPlan.builder(nodes, deployments);
        for (Deployment m : deployments) {
            for (Node n : nodes) {
                int allocations = assignmentsByModelNodeIdPair.getOrDefault(Tuple.tuple(m.id(), n.id()), 0);
                if (m.currentAllocationsByNodeId().containsKey(n.id())) {
                    if (mergedPlanBuilder.getRemainingMemory(n) >= m.memoryBytes()) {
                        allocations += addPreservedAllocations(n, m);
                        // As the node has all its available memory we need to manually account memory of models with
                        // current allocations.
                        mergedPlanBuilder.accountMemory(m, n);
                    }
                }
                if (allocations > 0) {
                    mergedPlanBuilder.assignModelToNode(m, n, allocations);
                }
            }
        }
        return mergedPlanBuilder.build();
    }

    protected abstract int calculateUsedCores(Node n, Deployment m);

    protected abstract Map<String, Integer> calculateAllocationsPerNodeToPreserve(Deployment m);

    protected abstract int calculatePreservedAllocations(Deployment m);

    protected abstract int addPreservedAllocations(Node n, Deployment m);
}
