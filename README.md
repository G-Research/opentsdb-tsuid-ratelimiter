# opentsdb-tsuid-ratelimiter

Provides an implementation of the OpenTSDB meta cache interface as discussed on the OpenTSDB [mailing list](https://groups.google.com/d/msg/opentsdb/oMLi0GCNF30/I32rpm8cCgAJ).

Read our [blog post](https://www.gresearch.co.uk/article/opentsdb-meta-cache-trade-offs-for-performance/) for details on how it works and why you'd want to use it.

It works by allowing TSD instances to cache what tsmeta combinations have been seen and only writing a tsmeta
entry in tsdb-meta if a data point write misses this cache. The cache can obviously be bounded to limit memory consumption
and and is an LRU. This allows you to trade TSD memory consumption against tsdb-meta write rate.

Directions for use:

* Enable tsuid tracking if not already done so:
```
tsd.core.meta.enable_realtime_ts=true
tsd.core.meta.enable_tsuid_tracking=true
```
* Add the plugin:
```
tsd.core.meta.cache.enable=true
tsd.core.meta.cache.plugin=uk.co.gresearch.opentsdb.RateLimitedTsuidTracking
```
* Ensure the plugin Jar is in your classpath
* Set optional configuration for max cache size:
```
tsd.meta.cache.max_size=100000
```
* Set optional configuration for ttl:
```
tsd.meta.cache.ttl_seconds=3600
```
* To enable stats collection from the plugin we need a little config fudging due to a bug in OpenTSDB (unless we're running a version containing this [fix](https://github.com/OpenTSDB/opentsdb/pull/1649) - expected in 2.4.1):
```
tsd.startup.enable=true
tsd.startup.plugin=uk.co.gresearch.opentsdb.RateLimitedTsuidTrackingStatsCollector
```

If you can afford the memory, to get the maximum benefit, it's recommended to set the cache size to the maximum number of unique time series you expect to see within the TTL period.
