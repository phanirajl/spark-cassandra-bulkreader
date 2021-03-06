package org.apache.cassandra.spark.data.partitioner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import org.apache.cassandra.spark.TestSchema;
import org.apache.cassandra.spark.TestUtils;
import org.apache.cassandra.spark.data.CqlField;
import org.apache.cassandra.spark.data.CqlSchema;
import org.apache.cassandra.spark.data.CqlUdt;
import org.apache.cassandra.spark.data.PartitionedDataLayer;
import org.apache.cassandra.spark.reader.CassandraBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.arbitrary;

/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
public class JDKSerializationTests
{
    @Test
    public void testCassandraRing()
    {
        qt().forAll(TestUtils.partitioners(), arbitrary().pick(Arrays.asList(1, 3, 6, 12, 128)))
            .checkAssert(((partitioner, numInstances) -> {
                final CassandraRing ring;
                if (numInstances > 4)
                {
                    ring = TestUtils.createRing(partitioner, ImmutableMap.of("DC1", numInstances / 2, "DC2", numInstances / 2));
                }
                else
                {
                    ring = TestUtils.createRing(partitioner, numInstances);
                }
                final byte[] ar = serialize(ring);
                final CassandraRing deserialized = deserialize(ar, CassandraRing.class);
                assertNotNull(deserialized);
                assertNotNull(deserialized.rangeMap());
                assertNotNull(deserialized.tokenRanges());
                assertEquals(ring, deserialized);
            }));
    }

    @Test
    public void testTokenPartitioner()
    {
        qt().forAll(TestUtils.partitioners(), arbitrary().pick(Arrays.asList(1, 3, 6, 12, 128)), arbitrary().pick(Arrays.asList(1, 4, 8, 16, 32, 1024)))
            .checkAssert(((partitioner, numInstances, numCores) -> {
                final CassandraRing ring = TestUtils.createRing(partitioner, numInstances);
                final TokenPartitioner tokenPartitioner = new TokenPartitioner(ring, 4, numCores);
                final byte[] ar = serialize(tokenPartitioner);
                final TokenPartitioner deserialized = deserialize(ar, TokenPartitioner.class);
                assertEquals(tokenPartitioner.ring(), deserialized.ring());
                assertEquals(tokenPartitioner.numPartitions(), deserialized.numPartitions());
                assertEquals(tokenPartitioner.subRanges(), deserialized.subRanges());
                assertEquals(tokenPartitioner.partitionMap(), deserialized.partitionMap());
                assertEquals(tokenPartitioner.reversePartitionMap(), deserialized.reversePartitionMap());
                for (int i = 0; i < tokenPartitioner.numPartitions(); i++)
                {
                    assertEquals(tokenPartitioner.getTokenRange(i), deserialized.getTokenRange(i));
                }
            }));
    }

    @Test
    public void testPartitionedDataLayer()
    {
        final CassandraRing ring = TestUtils.createRing(Partitioner.Murmur3Partitioner, 1024);
        final TestSchema schema = TestSchema.basic();
        final CqlSchema cqlSchema = new CqlSchema(schema.keyspace, schema.table, schema.createStmt, ring.replicationFactor(), Collections.emptyList());
        final PartitionedDataLayer partitionedDataLayer = new TestPartitionedDataLayer(4, 16, null, ring, cqlSchema);
        final byte[] ar = serialize(partitionedDataLayer);
        final TestPartitionedDataLayer deserialized = deserialize(ar, TestPartitionedDataLayer.class);
        assertNotNull(deserialized);
        assertNotNull(deserialized.ring());
        assertNotNull(deserialized.partitioner());
        assertNotNull(deserialized.tokenPartitioner());
        assertEquals(Partitioner.Murmur3Partitioner, deserialized.partitioner());
    }

    @Test
    public void testCqlFieldSet() {
        final CqlField.CqlSet setType = CqlField.set(CqlField.NativeCql3Type.TEXT);
        final CqlField field = new CqlField(true, false, false, RandomStringUtils.randomAlphanumeric(5, 20), setType, 10);
        final byte[] ar = serialize(field);
        final CqlField deserialized = deserialize(ar, CqlField.class);
        assertEquals(field, deserialized);
        assertEquals(field.name(), deserialized.name());
        assertEquals(field.type(), deserialized.type());
        assertEquals(field.pos(), deserialized.pos());
        assertEquals(field.isPartitionKey(), deserialized.isPartitionKey());
        assertEquals(field.isClusteringColumn(), deserialized.isClusteringColumn());
    }

    @Test
    public void testCqlUdt() {
        final CqlUdt udt1 = CqlUdt
                            .builder("udt_keyspace", "udt_table")
                            .withField("c", CqlField.NativeCql3Type.TEXT)
                            .withField("b", CqlField.NativeCql3Type.TIMESTAMP)
                            .withField("a", CqlField.NativeCql3Type.BIGINT)
                            .build();
        final CqlUdt udt2 = CqlUdt
                            .builder("udt_keyspace", "udt_table")
                            .withField("a", CqlField.NativeCql3Type.BIGINT)
                            .withField("b", CqlField.NativeCql3Type.TIMESTAMP)
                            .withField("c", CqlField.NativeCql3Type.TEXT)
                            .build();
        assertNotEquals(udt2, udt1);
        final byte[] b = serialize(udt1);
        final CqlUdt deserialized = deserialize(b, CqlUdt.class);
        assertEquals(udt1, deserialized);
        assertNotEquals(udt2, deserialized);
        for (int i = 0; i < deserialized.fields().size(); i++) {
            assertEquals(udt1.field(i), deserialized.field(i));
        }
    }

    public static class TestPartitionedDataLayer extends PartitionedDataLayer
    {
        private final CassandraRing ring;
        private final CqlSchema cqlSchema;
        private final TokenPartitioner tokenPartitioner;

        public TestPartitionedDataLayer(final int defaultParallelism, final int numCores, @Nullable final String dc,
                                        final CassandraRing ring, final CqlSchema cqlSchema)
        {
            super(ConsistencyLevel.LOCAL_QUORUM, dc);
            this.ring = ring;
            this.cqlSchema = cqlSchema;
            this.tokenPartitioner = new TokenPartitioner(ring, defaultParallelism, numCores);
        }

        public CompletableFuture<Stream<SSTable>> listInstance(int partitionId, @NotNull Range<BigInteger> range, @NotNull CassandraInstance instance)
        {
            return CompletableFuture.completedFuture(Stream.of());
        }

        public CassandraRing ring()
        {
            return ring;
        }

        public TokenPartitioner tokenPartitioner()
        {
            return tokenPartitioner;
        }

        protected ExecutorService executorService()
        {
            return SingleReplicaTests.EXECUTOR;
        }

        public CassandraBridge.CassandraVersion version()
        {
            return CassandraBridge.CassandraVersion.FOURZERO;
        }

        public CqlSchema cqlSchema()
        {
            return cqlSchema;
        }
    }

    private static <T> T deserialize(final byte[] ar, final Class<T> cType)
    {
        final ObjectInputStream in;
        try
        {
            in = new ObjectInputStream(new ByteArrayInputStream(ar));
            return cType.cast(in.readObject());
        }
        catch (final IOException | ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static byte[] serialize(final Serializable serializable)
    {
        try
        {
            final ByteArrayOutputStream arOut = new ByteArrayOutputStream(512);
            try (final ObjectOutputStream out = new ObjectOutputStream(arOut))
            {
                out.writeObject(serializable);
            }
            return arOut.toByteArray();
        }
        catch (final IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
