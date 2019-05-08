package uk.co.gresearch.opentsdb;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.stumbleupon.async.Deferred;
import net.opentsdb.core.TSDB;
import net.opentsdb.meta.MetaDataCache;
import net.opentsdb.meta.TSMeta;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.uid.UniqueId;
import net.opentsdb.utils.Config;
import org.hbase.async.Bytes;
import org.hbase.async.HBaseClient;
import org.hbase.async.PutRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class RateLimitedTsuidTracking extends MetaDataCache {
  
  private static final Logger LOG = LoggerFactory.getLogger(RateLimitedTsuidTracking.class);
  
  /** To write to meta table */
  private HBaseClient client;
  /** Google cache we use to apply rate limiting */
  private static Cache<String,String> cache;

  /** Fixed value, using the cache as a set */
  private static final String ENTRY = "";
  private byte[] meta_table;

  /**
   * Initialise the plugin. Looks for configuration settings for max size and ttl for entries. If not found
   * then they are not set (i.e. default = "off").
   */
  public void initialize(final TSDB tsdb) {
    this.client = tsdb.getClient();
    this.meta_table = tsdb.metaTable();

    Config config = tsdb.getConfig();

    CacheBuilder builder = CacheBuilder.newBuilder().recordStats();
    if (config.hasProperty("tsd.meta.cache.max_size")) {
      builder.maximumSize(config.getInt("tsd.meta.cache.max_size"));
    }
    if (config.hasProperty("tsd.meta.cache.ttl_seconds")) {
      builder.expireAfterWrite(config.getInt("tsd.meta.cache.ttl_seconds"), TimeUnit.SECONDS);
    }

    //noinspection unchecked
    cache = builder.build();
  }

  /**
   * No specialised shutdown required, so returns immediately.
   */
  public Deferred<Object> shutdown() {
    return Deferred.fromResult(null);
  }

  /**
   * Should return the version of this plugin in the format:
   * MAJOR.MINOR.MAINT, e.g. 2.0.1. The MAJOR version should match the major
   * version of OpenTSDB the plugin is meant to work with.
   * @return A version string used to log the loaded version
   */
  public String version() {
    return "2.0.0";
  }

  /**
   * Collect statistics on size of cache and number of interactions.
   * @param collector The collector used for emitting statistics
   */
  public void collectStats(final StatsCollector collector) {
    collectStatsFudge(collector);
  }

  /**
   * Collect statistics on size of cache and number of interactions.
   * @param collector The collector used for emitting statistics
   */
  public static void collectStatsFudge(final StatsCollector collector) {
    long size = cache.size();
    collector.record("meta.cache.size", size);

    CacheStats stats = cache.stats();
    collector.record("meta.cache.hits", stats.hitCount());
    collector.record("meta.cache.misses", stats.missCount());
    collector.record("meta.cache.evictions", stats.evictionCount());
  }

  /**
   * Writes a "1" to the meta table with rate limiting.
   * @param tsuid The tsuid to write
   */
  public void increment(final byte[] tsuid) {
    String key = UniqueId.uidToString(tsuid);
    try {
      cache.get(key, new Callable<String>() {
        public String call() throws Exception {
          writeIncrement(tsuid);
          return ENTRY;
        }
      });
    }
    catch (ExecutionException ee) {
      // v unlikely given it's async
      LOG.info("Error writing tsuid meta data to HBase for "+key, ee);
    }
  }

  /**
   * Actually perform the write to the meta table
   * @param tsuid  The tsuid to write
   */
  private void writeIncrement(final byte[] tsuid) {
    final PutRequest tracking = new PutRequest(meta_table, tsuid, TSMeta.FAMILY(), TSMeta.COUNTER_QUALIFIER(), Bytes.fromLong(1));
    // TSDB doesn't have any error handling on this, so we won't
    client.put(tracking);
  }
}
