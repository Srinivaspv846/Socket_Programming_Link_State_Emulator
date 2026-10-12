import java.util.Scanner;

import javax.xml.crypto.Data;

import java.io.IOException;
import java.net.*;

public class vn{
    public static void main(String[] args) throws Exception{

        Scanner sc=new Scanner(System.in);
        String ONIP,MyUdpIP;int ONPort,MyUdpPort;
        ONIP=sc.next();ONPort=sc.nextInt();
        MyUdpIP="0.0.0.0";MyUdpPort=sc.nextInt();

        DatagramSocket us=null;Socket ts=null;

        try{
            us=new DatagramSocket();
            us.bind(new InetSocketAddress(MyUdpIP,MyUdpPort));
        } catch(IOException e){
            System.err.println("Failed to bind UDP Socket");
            sc.close();
            if(us!=null) us.close();
            System.exit(1);
            return;
        }
        try{
            ts=new Socket();
            ts.bind(new InetSocketAddress(0));
            ts.connect(new InetSocketAddress(ONIP,ONPort));
        } catch(IOException e){
            System.err.println("Falied to initialize tcp connection with ON");
            sc.close();us.close();
            if(ts!=null) ts.close();
            System.exit(1);
            return;
        }
        us.close();
        ts.close();
        sc.close();
    }
}