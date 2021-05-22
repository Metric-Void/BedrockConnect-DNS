package main.com.pyratron.pugmatt.bedrockconnect.dns;

import org.xbill.DNS.Name;
import java.util.Objects;

/**
 * The Key of A DNS query. Includes name and type.
 */
public class DNSKey {
    public int type; // A, NS, AAAA, TXT, etc.
    public Name name; // The record.

    DNSKey() {
    }

    DNSKey(int type, Name rec) {
        this.type = type;
        this.name = rec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSKey dnsRecord = (DNSKey) o;
        return (type == dnsRecord.type) && name.equals(dnsRecord.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }
}
