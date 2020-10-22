# Apache Cassandra-Spark Bulk Reader [WIP]

This project provides a library for reading raw Cassandra SSTables into SparkSQL along the principles of ’streaming compaction’.

By reading the raw SSTables directly, the Cassandra-Spark Bulk Reader enables efficient and fast massive-scale analytics queries without impacting the performance of a production Cassandra cluster. 

**This is project is still WIP.**

Requirements
------------
  1. Java >= 1.8 (OpenJDK or Oracle), or Java 11
  2. Apache Cassandra 3.0+
  3. Apache Spark 2.4

Overview
--------------------------------------

The primary interface for reading SSTables is through the DataLayer abstraction. A simple example 'LocalDataLayer' implementation is provided for reading SSTables from a local file system.

The role of the DataLayer is to:
 - return a SchemaStruct, mapping the Cassandra CQL table schema to the SparkSQL schema.
 - a list of sstables available for reading.
 - a method to open an InputStream on any file component of an sstable (e.g. data, compression, summary etc).

The PartitionedDataLayer abstraction builds on the DataLayer interface for partitioning Spark workers across a Cassandra token ring - allowing the Spark job to scale linearly - and reading from sufficient Cassandra replicas to achieve a user-specified consistency level.

At the core, the Bulk Reader uses the Apache Cassandra [CompactionIterator](https://github.com/mariusae/cassandra/blob/master/src/java/org/apache/cassandra/io/CompactionIterator.java) to perform the streaming compaction. The SparkRowIterator and SparkCellIterator iterate through the CompactionIterator, deserialize the ByteBuffers, convert into the appropriate SparkSQL data type, and finally pivot each cell into a SparkSQL row.
  
Testing
---------

The project is robustly tested using a bespoke property-based testing system that uses [QuickTheories](https://github.com/quicktheories/QuickTheories) to enumerate many Cassandra CQL schemas, write random data using the Cassandra [CQLSSTableWriter](https://github.com/apache/cassandra/blob/trunk/src/java/org/apache/cassandra/io/sstable/CQLSSTableWriter.java), read the SSTables into SparkSQL and verify the resulting SparkSQL rows match the expected.  

For examples tests see org.apache.cassandra.spark.EndToEndTests.