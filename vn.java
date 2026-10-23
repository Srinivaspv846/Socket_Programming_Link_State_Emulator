import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class vn{

    static class NeighborInfo {
        char id;
        InetAddress ip;
        int port;
        short cost;

        public NeighborInfo(char id,InetAddress ip,int port,short cost) {
            this.id = id;
            this.ip = ip;
            this.port = port;
            this.cost = cost;
        }
    }

    static char myId = '#';
    static int mySeqNum = 0;
    static Map<Character,NeighborInfo> myNeighbors = new TreeMap<>();
    static int[][] adjMatrix = new int[26][26];
    static int[] maxSeqNum = new int[26];
    static boolean[] knownNodes = new boolean[26];


    public static void main(String[] args) throws Exception{
        for(int i=0;i<26;i++){
            Arrays.fill(adjMatrix[i], -1);
            adjMatrix[i][i] = 0;
            maxSeqNum[i] = -1;
        }

        String ONIP;int ONPort,MyUdpPort;
        ONIP=args[0];ONPort=Integer.parseInt(args[1]);
        MyUdpPort=Integer.parseInt(args[2]);

        DatagramSocket us=null;Socket ts=null;
        SocketChannel tsCh = null;
        DatagramChannel usCh = null;

        if(MyUdpPort < 1024 || MyUdpPort > 65535){
            System.err.println("Error: UDP port must be between 1024 and 65535");
            System.exit(1);
        }
        if(ONPort < 1024 || ONPort > 65535){
            System.err.println("Error: UDP port must be between 1024 and 65535");
            System.exit(1);
        }
        try{
            tsCh = SocketChannel.open();
            ts = tsCh.socket();
            ts.bind(new InetSocketAddress(0));
            ts.connect(new InetSocketAddress(ONIP,ONPort));
        } catch(IOException e){
            System.err.println("Falied to initialize tcp connection with ON"+e);
            if(ts!=null) ts.close();
            System.exit(1);
            return;
        }

        String MyUdpIp = ts.getLocalAddress().getHostAddress();

        try{
            usCh = DatagramChannel.open();
            us = usCh.socket();
            us.bind(new InetSocketAddress(MyUdpIp,MyUdpPort));
        } catch(IOException e){
            System.err.println("Failed to bind UDP Socket");
            ts.close();
            if(us!=null) us.close();
            System.exit(1);
            return;
        }
        
        byte[] ipBytes = InetAddress.getByName(MyUdpIp).getAddress();
        if(ipBytes.length != 4){
            System.err.println("Expected IPV4 Address");
        }

        byte[] msg = new byte[6];

        System.arraycopy(ipBytes,0,msg,0,4);
        msg[4] = (byte) (MyUdpPort >> 8); 
        msg[5] = (byte) MyUdpPort;

        ts.getOutputStream().write(msg);
        ts.getOutputStream().flush();


        tsCh.configureBlocking(false);
        usCh.configureBlocking(false);

        Selector sel = Selector.open(); // using Selector to implement on a single thread

        tsCh.register(sel, SelectionKey.OP_READ);
        usCh.register(sel, SelectionKey.OP_READ);

        ByteBuffer tcpAcumBuff = ByteBuffer.allocate(4096);
        ByteBuffer udpReadBuff = ByteBuffer.allocate(2048);

        long lsp_period = 10000;
        long print_period = 10000;
        long last_lsp_time = System.currentTimeMillis();
        long last_print_time = System.currentTimeMillis();
        Boolean is_running = true;
        while(is_running){
            sel.select(500);
            Iterator<SelectionKey> iter = sel.selectedKeys().iterator();
            while(iter.hasNext()){
                SelectionKey key = iter.next();
                iter.remove();

                if(!key.isValid()) continue;

                if(key.isReadable() && key.channel() == tsCh){
                    int bytesRead = tsCh.read(tcpAcumBuff);
                    if(bytesRead == -1){
                        System.out.println("Oracle node closed the tcp connection.");
                        is_running = false;
                        break;
                    }
                    else if(bytesRead > 0){
                        tcpAcumBuff.flip();
                        while(tcpAcumBuff.remaining()>=1){
                            tcpAcumBuff.mark();
                            int numTuples = tcpAcumBuff.get() & 0xFF;
                            int expectedbytes = numTuples * 9;
                            if(tcpAcumBuff.remaining() < expectedbytes){
                                tcpAcumBuff.reset();
                                break;
                            }
                            handleLinkStatefromON(tcpAcumBuff, numTuples, usCh);
                        }
                        tcpAcumBuff.compact();
                    }
                }

                if(key.isReadable() && key.channel() == usCh){
                    udpReadBuff.clear();
                    SocketAddress sender = usCh.receive(udpReadBuff);
                    if(sender != null) {
                        udpReadBuff.flip();
                        handleIncomingLSP(udpReadBuff, sender, usCh);
                    }
                }
            }
            long now = System.currentTimeMillis();
            if(myId != '#' && (now - last_lsp_time >= lsp_period)){
                originateAndFloodLSP(usCh);
                last_lsp_time = now;
            }
            if(myId != '#' && (now - last_print_time >= print_period)){
                printGraph();
                last_print_time = now;
            }
        }

        sel.close();
        usCh.close();
        tsCh.close();
    }

    public static void handleLinkStatefromON(ByteBuffer buf, int numTuples, DatagramChannel usCh) throws IOException {
        Map<Character,NeighborInfo> updatedNeighbors = new TreeMap<>();
        List<String> printmsg = new ArrayList<>();
        String self = null;

        for(int i=0;i< numTuples;i++){
            char nodeChar = (char) (buf.get() & 0xFF);
            byte[] rawip = new byte[4];
            buf.get(rawip);
            InetAddress ip = InetAddress.getByAddress(rawip);
            int port = buf.getShort() & 0xFFFF;
            short cost = buf.getShort();

            if(cost == 0){
                myId = nodeChar;
                knownNodes[myId - 'A'] = true;
                self = nodeChar + "=0";
            } else if(cost >0){
                updatedNeighbors.put(nodeChar, new NeighborInfo(nodeChar, ip,port,cost));
                printmsg.add(nodeChar+"="+cost);
            }
        }

        if(self!= null){
            printmsg.add(0,self);
        }
        System.out.println(String.join(", ",printmsg));
        myNeighbors = updatedNeighbors;

        if (myId >= 'A' && myId <= 'Z') {
            int u = myId - 'A';
            for (int v = 0; v < 26; v++) {
                if (u != v) {
                    adjMatrix[u][v] = -1;
                    adjMatrix[v][u] = -1;
                }
            }
            for (NeighborInfo nb : myNeighbors.values()) {
                if (nb.id >= 'A' && nb.id <= 'Z') {
                    int v = nb.id - 'A';
                    knownNodes[v] = true;
                    adjMatrix[u][v] = nb.cost;
                    adjMatrix[v][u] = nb.cost;
                }
            }
            originateAndFloodLSP(usCh);
        }
    }

    public static void originateAndFloodLSP(DatagramChannel usCh) throws IOException {
        if (myId < 'A' || myId > 'Z') return;

        mySeqNum++;
        maxSeqNum[myId - 'A'] = mySeqNum;

        int numNb = myNeighbors.size();
        ByteBuffer lsp = ByteBuffer.allocate(6 + numNb * 3);

        lsp.put((byte) myId);
        lsp.putInt(mySeqNum);
        lsp.put((byte) numNb);

        for (NeighborInfo nb : myNeighbors.values()) {
            lsp.put((byte) nb.id);
            lsp.putShort(nb.cost);
        }
        lsp.flip();
        System.out.println("[LSP TX] own LSP seq=" + mySeqNum + " to " + myNeighbors.keySet());
        for (int repeat = 0; repeat < 2; repeat++) {
            for (NeighborInfo nb : myNeighbors.values()) {
                lsp.rewind();
                usCh.send(lsp, new InetSocketAddress(nb.ip, nb.port));
            }
        }
    }

    public static void handleIncomingLSP(ByteBuffer buf, SocketAddress sender, DatagramChannel usCh) throws IOException {
        if(buf.remaining() < 6) return;

        ByteBuffer rawcopy = buf.duplicate();
        char origin = (char) (buf.get() & 0xFF);
        if(origin < 'A' || origin > 'Z' || origin == myId) return;

        int seq = buf.getInt();
        int numNb = buf.get() & 0xFF;
        if(buf.remaining() < (numNb * 3)) return;

        int x = origin-'A';
        if(seq<=maxSeqNum[x]) return;
        maxSeqNum[x] = seq;
        knownNodes[x] = true;
        System.out.println("[LSP RX] origin=" + origin + " seq=" + seq + " from " + sender);

        for(int y=0;y<26;y++){
            if(x!=y){
                adjMatrix[x][y] = -1;
                adjMatrix[y][x] = -1;
            }
        }

        for(int i=0;i<numNb;i++){
            char c = (char) (buf.get() & 0xFF);
            short cost = buf.getShort();
            if(c >= 'A' && c<= 'Z'){
                int z = c - 'A';
                knownNodes[z] = true;
                adjMatrix[x][z] = cost;
                adjMatrix[z][x] = cost;
            }
        }

        InetSocketAddress senderInet = (InetSocketAddress) sender;
        for( NeighborInfo nb : myNeighbors.values()){
            if(senderInet != null && senderInet.getPort() == nb.port && senderInet.getAddress().equals(nb.ip)){
                continue;
            }
            if(nb.id == origin) continue;
            rawcopy.rewind();
            System.out.println("[LSP FWD] origin=" + origin + " seq=" + seq + " -> " + nb.id);
            usCh.send(rawcopy, new InetSocketAddress(nb.ip,nb.port));
        }
    }


    public  static void printGraph(){
        int maxNode = -1;
        for(int i=0;i<26;i++){
            if(knownNodes[i]) maxNode = i;
        }
        if(maxNode == -1) return;
        int n = maxNode +1;
        System.out.println("Printing Known Graph at Node "+ myId + ":");
        for(int i=0;i<n-1;i++){
            for (int t = 0; t < i; t++) {
                System.out.print("\t");
            }
            for (int j = i + 1; j < n; j++) {
                System.out.print(adjMatrix[i][j]);
                if (j < n - 1) System.out.print("\t");
            }
            System.out.println(); 
        }
        System.out.println();
    } 

}