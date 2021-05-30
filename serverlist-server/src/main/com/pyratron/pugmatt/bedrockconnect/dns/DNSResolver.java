package main.com.pyratron.pugmatt.bedrockconnect.dns;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of A DNS server.
 *
 * Treat local records with a higher priority. Perform recursive queries on unknown domains.
 *
 */
public class DNSResolver {
    private Thread thread = null;
    private volatile boolean isLive = false;
    private final int port;
    private final int cache_size;
    /**
     * Whether or not to perform recusive queries.
     * If false, server will return empty response to all non-local records.
     */
    private boolean recursive = true;

    /**
     * Locally stored DNS entries. Has priority.
     */
    private final Map<DNSKey, Record> localEntries = new HashMap<>();
    /**
     * Cached DNS entries.
     */
    private final Map<DNSKey, List<CachedRecord>> cachedEntries = new HashMap<>();
    /**
     * A queue tracking the time each entry is cached.
     * Old entries are removed when limit is exceeded.
     */
    private final Deque<DNSKey> cacheList = new LinkedList<DNSKey>();

    /**
     * A internal cache for the lookup process to use.
     */
    private final Cache dnsCache = new Cache();

    /**
     * Construct a DNS server to listen on <code>port</code>
     * @param port The port to listen on
     */
    public DNSResolver(int port) {
        this(port, 1000);
    }

    public DNSResolver(int port, int cache_size) {
        this.port = port;
        this.cache_size = cache_size;
    }
    /**
     * Fetch a non-local record from Internet. Use system DNS servers and resolvers.
     * Will use cache if possible
     * @param key The entry to look up for
     * @return A list of completed DNS records.
     */
    private List<Record> recurse(DNSKey key) {
        if(cachedEntries.containsKey(key)) {
            for(CachedRecord r : cachedEntries.get(key)) {
                if(r.expired()) return hardRecurse(key);
            }
            return cachedEntries.get(key).stream().map(CachedRecord::getRecord).collect(Collectors.toList());
        } else {
            return hardRecurse(key);
        }
    }

    /**
     * Fetch a non-local record from Internet. Use system DNS server and resolvers.
     * Will NOT use cache.
     * @param key The entry to look up for.
     * @return A list of completed DNS records.
     */
    private List<Record> hardRecurse(DNSKey key) {
        Lookup lookup = new Lookup(key.name, key.type);
        lookup.setCache(dnsCache);
        lookup.run();
        LinkedList<Record> result = new LinkedList<>();
        if(lookup.getResult() == Lookup.SUCCESSFUL) {
            result.addAll(Arrays.asList(lookup.getAnswers()));
        }

        if(lookup.getAliases().length != 0)
            result.addAll(
                Arrays.stream(lookup.getAliases()).parallel()
                    .map((name) -> {
                        Lookup lkup = new Lookup(name, Type.CNAME);
                        lkup.run();
                        return lkup.getAnswers();
                    }).flatMap(Arrays::stream)
                    .collect(Collectors.toList())
            );

        new Thread(() -> cacheResult(key, result)).start();
        return result;
    }

    /**
     * Cache the result of a DNS lookup.
     * Should be done async.
     * @param key The DNS entry.
     * @param result A list of completed DNS records.
     */
    private void cacheResult(DNSKey key, List<Record> result) {
        cacheList.remove(key);

        if(cacheList.size() >= cache_size) {
            DNSKey keyToRemove = cacheList.removeFirst();
            cacheList.remove(keyToRemove);
            cachedEntries.remove(keyToRemove);
        }

        cachedEntries.put(key, new ArrayList<>());
        cacheList.addLast(key);
        for(Record r : result) {
            cachedEntries.get(key).add(new CachedRecord(r));
        }
    }

    /**
     * Start the DNS server.
     */
    public void start() {
        isLive = true;
        thread = new Thread(()->{
           try {
               serve();
           }
           catch(IOException e) {
               System.out.println("DNS Resolver generated an error. Restarting in 5s...");
               e.printStackTrace();
               try {
                   Thread.sleep(5000);
               } catch (InterruptedException interruptedException) {
                   interruptedException.printStackTrace();
               }
               restart();
           }
        });
        thread.start();
    }

    /**
     * Stop the DNS server
     */
    public void stop() {
        isLive = false;
        thread.interrupt();
        thread = null;
    }

    /**
     * Restart the DNS server
     */
    private void restart() {
        thread.interrupt();
        thread = null;
        start();
    }

    /**
     * Listen to the UDP socket, and send packets to handler.
     * @throws IOException When the UDP socket generates an error. i.e. Cannot bind to port.
     */
    private void serve() throws IOException {
        DatagramSocket socket = new DatagramSocket(port);
        System.out.printf("DNS Server started: 0.0.0.0:%d%n", port);
        System.out.printf("DNS Settings: [Recursive=%s, Cache Size=%d] %n", recursive, cache_size);
        while(isLive) {
            int UDP_SIZE = 512;
            byte[] inBuffer = new byte[UDP_SIZE];
            DatagramPacket reqPacket = new DatagramPacket(inBuffer, UDP_SIZE);
            socket.receive(reqPacket); // This method is BLOCKING.
            Thread handlerThread = new Thread(() -> handle(reqPacket, socket));
            handlerThread.start();
        }
    }

    /**
     * Handle a UDP packet of DNS request, and send it via a socket
     * @param reqPacket The incoming DNS request packet.
     * @param socket The socket to send response to.
     */
    private void handle(DatagramPacket reqPacket, DatagramSocket socket) {
        try {
            Message request = new Message(reqPacket.getData());
            Record requestRecord = request.getQuestion();
            DNSKey currKey = new DNSKey(requestRecord.getType(), requestRecord.getName());

            System.out.printf("Received DNS request for %s, Type %d\n",currKey.name, currKey.type);

//            if(currKey.name.toString().endsWith(".lan.")) {
//                String modifiedName = currKey.name.toString();
//                modifiedName = modifiedName.substring(0, modifiedName.length() - 4);
//                currKey.name = Name.fromString(modifiedName);
//            }

            if(localEntries.containsKey(currKey)) {
                Message response = constructResponse(request, Collections.singletonList(localEntries.get(currKey)));

                byte[] resp = response.toWire();
                DatagramPacket resPacket =
                    new DatagramPacket(resp, resp.length, reqPacket.getAddress(), reqPacket.getPort());
                socket.send(resPacket);
            } else if (recursive) {
                List<Record> answers = recurse(currKey);
                if(answers != null) {
                    Message response = constructResponse(request, answers);
                    byte[] resp = response.toWire();
                    DatagramPacket resPacket =
                        new DatagramPacket(resp, resp.length, reqPacket.getAddress(), reqPacket.getPort());
                    socket.send(resPacket);
                    return;
                }
            }

            // Send a NXDOMAIN response.
            Message response = constructNXDomainResponse(request);
            byte[] resp = response.toWire();
            DatagramPacket resPacket =
                new DatagramPacket(resp, resp.length, reqPacket.getAddress(), reqPacket.getPort());
            socket.send(resPacket);
        } catch (IOException ex) {
            System.out.println("An IO Exception happened in DNS resolver.");
            ex.printStackTrace();
        }
    }

    /**
     * Add a local entry with reduced parameters. Default TTL is 86400s, and dtype is DClass.IN.
     * @param type The type of record. e.g. Type.A
     * @param domain The string domain, including zone and subdomain. e.g. p5mc.vworks.cc
     * @param record The record, could different depending on the type of record.
     * @return If the operation was successful.
     */
    public boolean putLocalEntry(int type, String domain, String record) {
        return putLocalEntry(type, domain, record, 86400, DClass.IN);
    }

    /**
     * Add a local entry.
     * @param type The type of record. e.g. Type.A
     * @param domain The string domain, including zone and subdomain. e.g. p5mc.vworks.cc
     * @param record The record, could different depending on the type of record.
     * @param ttl TTL of the record. It will never expire on this server though.
     * @param dtype The class of this record. DClass.IN, DClass.CHAOS, etc.
     * @return If the operation was successful.
     */
    public boolean putLocalEntry(int type, String domain, String record, int ttl, int dtype) {
        try {
            Name domain_name = new Name(domain);
            DNSKey key = new DNSKey(type, domain_name);
            Record rec = Record.fromString(domain_name, type, dtype, ttl, record, Name.root);
            localEntries.put(key, rec);
        } catch (IOException e) {
            e.printStackTrace();
            return false;

        }
        return true;
    }

    /**
     * Control whether recursive lookups shall be performed.
     * @param recursive True or False.
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * Generate a NXDOMAIN Response.
     * @return
     */
    public Message constructNXDomainResponse(Message request) {
        Header respHeader = new Header();
        respHeader.setFlag(Flags.QR);
        if(recursive) respHeader.setFlag(Flags.RA);
        if(request.getHeader().getFlag(Flags.RD)) respHeader.setFlag(Flags.RD);
        respHeader.setID(request.getHeader().getID());
        respHeader.setRcode(Rcode.NXDOMAIN);
        respHeader.setOpcode(Opcode.QUERY);

        Message response = new Message();
        response.setHeader(respHeader);
        response.addRecord(request.getQuestion(), Section.QUESTION);

        return response;
    }

    public Message constructResponse(Message request, List<Record> records) {
        Header respHeader = new Header();
        respHeader.setFlag(Flags.QR);
        if(recursive) respHeader.setFlag(Flags.RA);
        if(request.getHeader().getFlag(Flags.RD)) respHeader.setFlag(Flags.RD);
        respHeader.setID(request.getHeader().getID());
        respHeader.setRcode(Rcode.NOERROR);

        Message response = new Message();
        response.setHeader(respHeader);
        response.addRecord(request.getQuestion(), Section.QUESTION);
        for(Record r : records) response.addRecord(r, Section.ANSWER);

        return response;
    }
}
