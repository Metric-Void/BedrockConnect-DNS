package main.com.pyratron.pugmatt.bedrockconnect.dns;

import org.xbill.DNS.Record;

import java.time.LocalDateTime;

public class CachedRecord {
    public LocalDateTime expireAt;
    private final Record record;

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
