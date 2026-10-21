import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.util.Scanner;

public class vn{
    public static void main(String[] args) throws Exception{

        Scanner sc=new Scanner(System.in);
        String ONIP;int ONPort,MyUdpPort;
        ONIP=sc.next();ONPort=sc.nextInt();
        MyUdpPort=sc.nextInt();

        DatagramSocket us=null;Socket ts=null;
        SocketChannel tsCh = null;
        DatagramChannel usCh = null;

        if(MyUdpPort < 1024 || MyUdpPort > 65535){
            System.err.println("Error: UDP port must be between 1024 and 65535");
            sc.close();
            System.exit(1);
        }
        if(ONPort < 1024 || ONPort > 65535){
            System.err.println("Error: UDP port must be between 1024 and 65535");
            sc.close();
            System.exit(1);
        }
        try{
            tsCh = SocketChannel.open();
            ts = tsCh.socket();
            ts.bind(new InetSocketAddress(0));
            ts.connect(new InetSocketAddress(ONIP,ONPort));
        } catch(IOException e){
            System.err.println("Falied to initialize tcp connection with ON");
            sc.close();
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
            sc.close();ts.close();
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

        //while(true){}

        us.close();
        ts.close();
        sc.close();
    }
}