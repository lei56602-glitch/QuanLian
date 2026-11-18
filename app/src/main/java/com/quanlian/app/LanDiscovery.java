package com.quanlian.app;

import android.util.Log;
import org.json.JSONObject;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** App-specific UDP discovery supplement. QUANLIAN2 remains the transfer protocol. */
public final class LanDiscovery {
    private static final String TAG = "QuanLianDiscovery";
    private static final String APP = "QUANLIAN";
    private static final int VERSION = 1;
    private static final int PORT = 38889;
    private static final String GROUP = "239.255.77.77";
    private LanDiscovery() {}

    public static final class Announcement {
        public final String name;
        public final InetAddress host;
        public final int port;
        Announcement(String name, InetAddress host, int port) {
            this.name=name; this.host=host; this.port=port;
        }
    }

    public static final class Listener implements AutoCloseable {
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final MulticastSocket socket;
        private final Thread thread;
        private Listener(MulticastSocket socket, Consumer<Announcement> callback) {
            this.socket=socket;
            thread=new Thread(()->{
                byte[] buffer=new byte[2048];
                while(running.get()&&!socket.isClosed()){
                    try{
                        DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                        socket.receive(packet);
                        JSONObject json=new JSONObject(new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8));
                        if(!APP.equals(json.optString("app"))||json.optInt("v")!=VERSION)continue;
                        String name=json.optString("name","");
                        int transferPort=json.optInt("port",-1);
                        if(name.isEmpty()||name.length()>128||transferPort<1||transferPort>65535)continue;
                        callback.accept(new Announcement(name,packet.getAddress(),transferPort));
                    }catch(SocketTimeoutException ignored){
                    }catch(Exception e){
                        if(running.get())Log.w(TAG,"UDP discovery receive error",e);
                    }
                }
            },"QuanLian-UDP-Listener");
            thread.setDaemon(true);
            thread.start();
        }
        @Override public void close(){
            if(running.compareAndSet(true,false)){
                try{socket.leaveGroup(InetAddress.getByName(GROUP));}catch(Exception ignored){}
                socket.close();
                thread.interrupt();
            }
        }
    }

    public static Listener listen(Consumer<Announcement> callback){
        try{
            MulticastSocket socket=new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(PORT));
            socket.setSoTimeout(1500);
            socket.joinGroup(InetAddress.getByName(GROUP));
            return new Listener(socket,callback);
        }catch(Exception e){
            Log.w(TAG,"Could not start UDP multicast listener; NSD remains active",e);
            return null;
        }
    }

    public static void announce(String name,int transferPort){
        if(name==null||name.isEmpty()||transferPort<1||transferPort>65535)return;
        try(DatagramSocket socket=new DatagramSocket()){
            socket.setTimeToLive(1);
            JSONObject json=new JSONObject();
            json.put("app",APP);json.put("v",VERSION);json.put("name",name);json.put("port",transferPort);
            byte[] data=json.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet=new DatagramPacket(data,data.length,InetAddress.getByName(GROUP),PORT);
            socket.send(packet);
        }catch(Exception e){
            Log.w(TAG,"Could not send UDP discovery announcement",e);
        }
    }
}
