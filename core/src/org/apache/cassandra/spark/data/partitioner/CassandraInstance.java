package org.apache.cassandra.spark.data.partitioner;

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

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

@SuppressWarnings("WeakerAccess")
public class CassandraInstance implements Serializable
{
    private final String token, node, dc;

    public CassandraInstance(final String token, final String node, final String dc)
    {
        this.token = token;
        this.node = node;
        this.dc = dc.toUpperCase();
    }

    public String token()
    {
        return this.token;
    }

    public String nodeName()
    {
        return this.node;
    }

    public String dataCenter()
    {
        return this.dc;
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (obj == null)
        {
            return false;
        }
        if (obj == this)
        {
            return true;
        }
        if (obj.getClass() != getClass())
        {
            return false;
        }

        final CassandraInstance rhs = (CassandraInstance) obj;
        return new EqualsBuilder()
               .append(token, rhs.token)
               .append(node, rhs.node)
               .append(dc, rhs.dc)
               .isEquals();
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder(29, 31)
               .append(token)
               .append(node)
               .append(dc)
               .build();
    }

    public static class Serializer extends com.esotericsoftware.kryo.Serializer<CassandraInstance>
    {
        @Override
        public CassandraInstance read(final Kryo kryo, final Input in, final Class type)
        {
            return new CassandraInstance(in.readString(), in.readString(), in.readString());
        }

        @Override
        public void write(final Kryo kryo, final Output out, final CassandraInstance instance)
        {
            out.writeString(instance.token());
            out.writeString(instance.nodeName());
            out.writeString(instance.dataCenter());
        }
    }
}
