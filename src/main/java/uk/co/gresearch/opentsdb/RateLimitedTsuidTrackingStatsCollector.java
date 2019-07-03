package uk.co.gresearch.opentsdb;

import com.stumbleupon.async.Deferred;
import net.opentsdb.core.TSDB;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.tools.StartupPlugin;
import net.opentsdb.utils.Config;

/**
 * Exists to collect stats as stats are not collected from the meta data plugin in version 2.3.0 (argh!).
 */
public class RateLimitedTsuidTrackingStatsCollector extends StartupPlugin {
    public Config initialize(Config config) {
        return config;
    }

    public void setReady(TSDB tsdb) {

    }

    public Deferred<Object> shutdown() {
        return Deferred.fromResult(null);
    }

    public String version()  {
        return "2.0.0";
    }

    public String getType() {
        return "Stats";
    }

    public void collectStats(StatsCollector statsCollector) {
        RateLimitedTsuidTracking.collectStatsFudge(statsCollector);
    }
}
