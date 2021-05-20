package main.com.pyratron.pugmatt.bedrockconnect.dns;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.stream.Collectors;

public class DNSResolver {
    private Thread thread = null;
    private volatile boolean isLive = false;
    private final int port;

    private boolean recursive = true;
    private ArrayList<InetAddress> recursiveServers;

    private final Map<DNSKey, Record> localEntries = new HashMap<>();
    private final Map<DNSKey, List<CachedRecord>> cachedEntries = new HashMap<>();
    private final Deque<DNSKey> cacheList = new LinkedList<DNSKey>();

    private final Cache dnsCache = new Cache();

    public DNSResolver(int port) {
        this.port = port;
    }

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

    private List<Record> hardRecurse(DNSKey key) {
        Lookup lookup = new Lookup(key.name, key.type);
        lookup.setCache(dnsCache);
        lookup.run();

        if(lookup.getResult() != Lookup.SUCCESSFUL) {
            return null;
        } else {
            new Thread(() -> cacheResult(key, lookup.getAnswers())).start();
        }
        return Arrays.asList(lookup.getAnswers());
    }

    private void cacheResult(DNSKey key, Record[] result) {
        cacheList.remove(key);

        int MAX_CACHE_ENTRIES = 1000;
        if(cacheList.size() >= MAX_CACHE_ENTRIES) {
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

    public void start() {
        isLive = true;
        thread = new Thread(()->{
           try {
               serve();
           }
           catch(IOException e) {
               System.out.println("DNS Resolver generated an error. Restarting...");
               restart();
           }
        });
        thread.start();
    }

    public void stop() {
        isLive = false;
        thread.interrupt();
        thread = null;
    }

    private void restart() {
        thread.interrupt();
        thread = null;
        start();
    }

    private void serve() throws IOException {
        DatagramSocket socket = new DatagramSocket(port);
        System.out.printf("DNS Server started: 0.0.0.0:%d\n", port);
        while(isLive) {
            int UDP_SIZE = 512;
            byte[] inBuffer = new byte[UDP_SIZE];
            DatagramPacket reqPacket = new DatagramPacket(inBuffer, UDP_SIZE);
            socket.receive(reqPacket); // This method is BLOCKING.
            Thread handlerThread = new Thread(() -> handle(reqPacket, socket));
            handlerThread.start();
        }
    }

    private void handle(DatagramPacket reqPacket, DatagramSocket socket) {
        try {
            Message request = new Message(reqPacket.getData());
            Record requestRecord = request.getQuestion();
            DNSKey currKey = new DNSKey(requestRecord.getType(), requestRecord.getName());

            if(currKey.name.toString().endsWith(".lan.")) {
                String modifiedName = currKey.name.toString();
                modifiedName = modifiedName.substring(0, modifiedName.length() - 4);
                currKey.name = Name.fromString(modifiedName);
            }

            if(localEntries.containsKey(currKey)) {
                Message response = new Message(request.getHeader().getID());
                response.addRecord(requestRecord, Section.QUESTION);
                response.addRecord(localEntries.get(currKey), Section.ANSWER);

                byte[] resp = response.toWire();
                DatagramPacket resPacket =
                    new DatagramPacket(resp, resp.length, reqPacket.getAddress(), reqPacket.getPort());
                socket.send(resPacket);
            } else if (recursive) {
                List<Record> answers = recurse(currKey);
                if(answers != null) {
                    Message response = new Message(request.getHeader().getID());
                    response.addRecord(requestRecord, Section.QUESTION);
                    for(Record r : answers) {
                        response.addRecord(r, Section.ANSWER);
                    }
                    byte[] resp = response.toWire();
                    DatagramPacket resPacket =
                        new DatagramPacket(resp, resp.length, reqPacket.getAddress(), reqPacket.getPort());
                    socket.send(resPacket);
                    return;
                }
            }

            // Send an empty response.
            Message response = new Message(request.getHeader().getID());
            response.addRecord(requestRecord, Section.QUESTION);

            byte[] resp = response.toWire();
            DatagramPacket resPacket =
                new DatagramPacket(resp, resp.length, reqPacket.getAddress(), reqPacket.getPort());
            socket.send(resPacket);
        } catch (IOException ex) {
            System.out.println("An IO Exception happened in DNS resolver.");
        }
    }

    public boolean putLocalEntry(int type, String domain, String record) {
        return putLocalEntry(type, domain, record, 86400, DClass.IN);
    }

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

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
}
