package com.quanlian.app;

import android.util.Log;
import org.json.JSONObject;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Per-interface UDP multicast discovery. */
public final class LanDiscovery {
 private static final String TAG="QuanLianDiscovery",APP="QUANLIAN",GROUP="224.0.0.167";
 private static final int VERSION=3,PORT=38889;
 public static final String PROBE="QUANLIAN_DISCOVERY_PROBE",PROBE_OK="QUANLIAN_DISCOVERY_OK";
 private LanDiscovery(){}

 public static final class Announcement {
  public final String id,name,fingerprint; public final InetAddress host; public final int port;
  Announcement(String i,String n,String fp,InetAddress h,int p){id=i;name=n;fingerprint=fp;host=h;port=p;}
 }

 public static final class Listener implements AutoCloseable {
  private final AtomicBoolean running=new AtomicBoolean(true);
  private final List<MulticastSocket> sockets=new ArrayList<>();
  private final List<Thread> threads=new ArrayList<>();
  Listener(Consumer<Announcement> cb){
   try{
    Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
    while(all!=null&&all.hasMoreElements()){
     NetworkInterface ni=all.nextElement();
     try{if(!ni.isUp()||ni.isLoopback()||!ni.supportsMulticast())continue;}catch(Exception e){continue;}
     boolean hasV4=false;
     for(InterfaceAddress a:ni.getInterfaceAddresses())if(a.getAddress() instanceof Inet4Address){hasV4=true;break;}
     if(!hasV4)continue;
     try{
      MulticastSocket s=new MulticastSocket(null);s.setReuseAddress(true);s.bind(new InetSocketAddress(PORT));
      s.setNetworkInterface(ni);s.setTimeToLive(1);s.setSoTimeout(1500);
      s.joinGroup(new InetSocketAddress(InetAddress.getByName(GROUP),PORT),ni);
      sockets.add(s);
      Thread t=new Thread(()->receiveLoop(s,cb),"QuanLian-UDP-Listener-"+ni.getName());t.setDaemon(true);t.start();threads.add(t);
      Log.i(TAG,"Listening multicast on "+ni.getName()+" ("+ni.getIndex()+")");
     }catch(Exception e){Log.w(TAG,"Cannot join multicast on "+ni.getName(),e);}
    }
   }catch(Exception e){Log.w(TAG,"Cannot enumerate multicast interfaces",e);}
   if(sockets.isEmpty())Log.w(TAG,"No usable IPv4 multicast interface; NSD and active scan remain available");
  }
  private void receiveLoop(MulticastSocket socket,Consumer<Announcement> cb){
   byte[] b=new byte[4096];
   while(running.get()&&!socket.isClosed()){
    try{
     DatagramPacket p=new DatagramPacket(b,b.length);socket.receive(p);
     JSONObject j=new JSONObject(new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.UTF_8));
     if(!APP.equals(j.optString("app"))||j.optInt("v")!=VERSION)continue;
     String id=j.optString("id",""),name=j.optString("name",""),fp=j.optString("fp","");
     int port=j.optInt("port",-1);
     if(id.length()<8||id.length()>128||name.isEmpty()||name.length()>128||!fp.matches("(?i)[0-9a-f]{64}")||port<1||port>65535)continue;
     cb.accept(new Announcement(id,name,fp,p.getAddress(),port));
    }catch(SocketTimeoutException ignored){}
    catch(Exception e){if(running.get())Log.w(TAG,"UDP discovery receive error",e);}
   }
  }
  public void close(){
   if(!running.compareAndSet(true,false))return;
   for(MulticastSocket s:sockets){try{s.leaveGroup(new InetSocketAddress(InetAddress.getByName(GROUP),PORT),s.getNetworkInterface());}catch(Exception ignored){}try{s.close();}catch(Exception ignored){}}
   for(Thread t:threads)t.interrupt();
  }
 }

 public static Listener listen(Consumer<Announcement> callback){return new Listener(callback);}

 public static boolean verify(InetAddress host,int port,String fingerprint){
  if(host==null||port<1||port>65535||fingerprint==null)return false;
  try(javax.net.ssl.SSLSocket s=TlsIdentity.connect(host.getHostAddress(),port,fingerprint)){
   DataOutputStream out=new DataOutputStream(s.getOutputStream());DataInputStream in=new DataInputStream(s.getInputStream());
   out.writeUTF(PROBE);out.flush();return PROBE_OK.equals(in.readUTF());
  }catch(Exception e){Log.w(TAG,"TLS discovery verification failed for "+host+":"+port,e);return false;}
 }

 public static void announce(String id,String name,int port){try{announce(id,name,port,TlsIdentity.fingerprint());}catch(Exception e){Log.w(TAG,"TLS identity unavailable",e);}}

 public static void announce(String id,String name,int port,String fp){
  if(id==null||id.length()<8||name==null||name.isEmpty()||fp==null||!fp.matches("(?i)[0-9a-f]{64}")||port<1||port>65535)return;
  try{
   JSONObject j=new JSONObject();j.put("app",APP);j.put("v",VERSION);j.put("id",id);j.put("name",name);j.put("port",port);j.put("fp",fp);
   byte[] data=j.toString().getBytes(StandardCharsets.UTF_8);
   Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();boolean sent=false;
   while(all!=null&&all.hasMoreElements()){
    NetworkInterface ni=all.nextElement();
    try{if(!ni.isUp()||ni.isLoopback()||!ni.supportsMulticast())continue;}catch(Exception e){continue;}
    boolean hasV4=false;for(InterfaceAddress a:ni.getInterfaceAddresses())if(a.getAddress() instanceof Inet4Address){hasV4=true;break;}
    if(!hasV4)continue;
    try(MulticastSocket s=new MulticastSocket()){
     s.setNetworkInterface(ni);s.setTimeToLive(1);s.setLoopbackMode(false);
     s.send(new DatagramPacket(data,data.length,InetAddress.getByName(GROUP),PORT));sent=true;
     for(InterfaceAddress a:ni.getInterfaceAddresses()){
      InetAddress b=a.getBroadcast();
      if(b!=null)try{s.send(new DatagramPacket(data,data.length,b,PORT));}catch(Exception e){Log.d(TAG,"Broadcast failed on "+ni.getName(),e);}
     }
     Log.i(TAG,"Announced on "+ni.getName());
    }catch(Exception e){Log.w(TAG,"Could not announce on "+ni.getName(),e);}
   }
   if(!sent)Log.w(TAG,"No interface accepted multicast announcement");
  }catch(Exception e){Log.w(TAG,"Could not send UDP discovery announcement",e);}
 }
}
