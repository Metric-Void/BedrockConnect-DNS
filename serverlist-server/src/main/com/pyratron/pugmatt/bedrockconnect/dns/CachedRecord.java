package main.com.pyratron.pugmatt.bedrockconnect.dns;

import org.xbill.DNS.Record;
import java.time.LocalDateTime;

/**
 * A cached DNS record, with TTL in mind.
 */
public class CachedRecord {
    public LocalDateTime expireAt;
    private final Record record;

    /**
     * Construct a cached record from a record.
     * A cached record contains a expiry time.
     * @param r The original record.
     */
    CachedRecord(Record r) {
        record = r;
        this.expireAt = LocalDateTime.now().plusSeconds(r.getTTL());
    }

    public boolean expired() {
        return LocalDateTime.now().isAfter(this.expireAt);
    }

    public Record getRecord() {
        return record;
    }
}
