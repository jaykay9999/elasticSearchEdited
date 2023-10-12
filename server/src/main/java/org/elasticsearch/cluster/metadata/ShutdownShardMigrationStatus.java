/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.cluster.routing.allocation.ShardAllocationDecision;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ChunkedToXContentObject;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import static org.elasticsearch.common.xcontent.ChunkedToXContentHelper.endObject;
import static org.elasticsearch.common.xcontent.ChunkedToXContentHelper.singleChunk;
import static org.elasticsearch.common.xcontent.ChunkedToXContentHelper.startObject;

public class ShutdownShardMigrationStatus implements Writeable, ChunkedToXContentObject {
    private static final TransportVersion ALLOCATION_DECISION_ADDED_VERSION = TransportVersions.V_7_16_0;

    public static final String NODE_ALLOCATION_DECISION_KEY = "node_allocation_decision";

    private final SingleNodeShutdownMetadata.Status status;
    private final long shardsRemaining;
    @Nullable
    private final String explanation;
    @Nullable
    private final ShardAllocationDecision allocationDecision;

    public ShutdownShardMigrationStatus(SingleNodeShutdownMetadata.Status status, long shardsRemaining) {
        this(status, shardsRemaining, null, null);
    }

    public ShutdownShardMigrationStatus(SingleNodeShutdownMetadata.Status status, long shardsRemaining, @Nullable String explanation) {
        this(status, shardsRemaining, explanation, null);
    }

    public ShutdownShardMigrationStatus(
        SingleNodeShutdownMetadata.Status status,
        long shardsRemaining,
        @Nullable String explanation,
        @Nullable ShardAllocationDecision allocationDecision
    ) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.shardsRemaining = shardsRemaining;
        this.explanation = explanation;
        this.allocationDecision = allocationDecision;
    }

    public ShutdownShardMigrationStatus(StreamInput in) throws IOException {
        this.status = in.readEnum(SingleNodeShutdownMetadata.Status.class);
        this.shardsRemaining = in.readLong();
        this.explanation = in.readOptionalString();
        if (in.getTransportVersion().onOrAfter(ALLOCATION_DECISION_ADDED_VERSION)) {
            this.allocationDecision = in.readOptionalWriteable(ShardAllocationDecision::new);
        } else {
            this.allocationDecision = null;
        }
    }

    public long getShardsRemaining() {
        return shardsRemaining;
    }

    public String getExplanation() {
        return explanation;
    }

    public SingleNodeShutdownMetadata.Status getStatus() {
        return status;
    }

    @Override
    public Iterator<? extends ToXContent> toXContentChunked(ToXContent.Params params) {
        return Iterators.concat(
            startObject(),
            singleChunk((builder, p) -> buildHeader(builder)),
            Objects.nonNull(allocationDecision)
                ? Iterators.concat(startObject(NODE_ALLOCATION_DECISION_KEY), allocationDecision.toXContentChunked(params), endObject())
                : Collections.emptyIterator(),
            endObject()
        );
    }

    private XContentBuilder buildHeader(XContentBuilder builder) throws IOException {
        builder.field("status", status);
        builder.field("shard_migrations_remaining", shardsRemaining);
        if (Objects.nonNull(explanation)) {
            builder.field("explanation", explanation);
        }
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(status);
        out.writeLong(shardsRemaining);
        out.writeOptionalString(explanation);
        if (out.getTransportVersion().onOrAfter(ALLOCATION_DECISION_ADDED_VERSION)) {
            out.writeOptionalWriteable(allocationDecision);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ((o instanceof ShutdownShardMigrationStatus) == false) return false;
        ShutdownShardMigrationStatus that = (ShutdownShardMigrationStatus) o;
        return shardsRemaining == that.shardsRemaining
            && status == that.status
            && Objects.equals(explanation, that.explanation)
            && Objects.equals(allocationDecision, that.allocationDecision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, shardsRemaining, explanation, allocationDecision);
    }

    @Override
    public String toString() {
        return Strings.toString((b, p) -> buildHeader(b), false, false);
    }
}