package com.quanlian.app;

import android.util.Log;
import org.json.JSONObject;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** App-specific UDP multicast discovery supplement. QUANLIAN2 remains the transfer protocol. */
public final class LanDiscovery {
    private static final String TAG = "QuanLianDiscovery";
    private static final String APP = "QUANLIAN";
    private static final int VERSION = 2;
    private static final int PORT = 38889;
    private static final String GROUP = "224.0.0.167";
    public static final String PROBE = "QUANLIAN_DISCOVERY_PROBE";
    public static final String PROBE_OK = "QUANLIAN_DISCOVERY_OK";
    private LanDiscovery() {}

    public static final class Announcement {
        public final String id, name;
        public final InetAddress host;
        public final int port;
        Announcement(String id, String name, InetAddress host, int port) {
            this.id=id; this.name=name; this.host=host; this.port=port;
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
                        String id=json.optString("id",""), name=json.optString("name","");
                        int transferPort=json.optInt("port",-1);
                        if(id.length()<8||id.length()>128||name.isEmpty()||name.length()>128||transferPort<1||transferPort>65535)continue;
                        callback.accept(new Announcement(id,name,packet.getAddress(),transferPort));
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
            socket.bind(new InetSocketAddress(PORT));
            socket.setSoTimeout(1500);
            socket.joinGroup(InetAddress.getByName(GROUP));
            return new Listener(socket,callback);
        }catch(Exception e){
            Log.w(TAG,"Could not start UDP multicast listener; NSD remains active",e);
            return null;
        }
    }

    public static boolean verify(InetAddress host,int port){
        if(host==null||port<1||port>65535)return false;
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(host,port),1400);
            socket.setSoTimeout(1400);
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            DataInputStream in=new DataInputStream(socket.getInputStream());
            out.writeUTF(PROBE);out.flush();
            return PROBE_OK.equals(in.readUTF());
        }catch(Exception e){return false;}
    }

    public static void announce(String id,String name,int transferPort){
        if(id==null||id.length()<8||name==null||name.isEmpty()||transferPort<1||transferPort>65535)return;
        try(MulticastSocket socket=new MulticastSocket()){
            socket.setTimeToLive(1);
            JSONObject json=new JSONObject();
            json.put("app",APP);json.put("v",VERSION);json.put("id",id);json.put("name",name);json.put("port",transferPort);
            byte[] data=json.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet=new DatagramPacket(data,data.length,InetAddress.getByName(GROUP),PORT);
            socket.send(packet);
        }catch(Exception e){
            Log.w(TAG,"Could not send UDP discovery announcement",e);
        }
    }
}
