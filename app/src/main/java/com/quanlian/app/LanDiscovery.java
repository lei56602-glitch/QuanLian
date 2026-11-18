package com.quanlian.app;
import android.util.Log;
import org.json.JSONObject;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class LanDiscovery {
 private static final String TAG="QuanLianDiscovery",APP="QUANLIAN",GROUP="224.0.0.167";
 private static final int VERSION=3,PORT=38889;
 public static final String PROBE="QUANLIAN_DISCOVERY_PROBE",PROBE_OK="QUANLIAN_DISCOVERY_OK";
 private LanDiscovery(){}
 public static final class Announcement {
  public final String id,name,fingerprint;public final InetAddress host;public final int port;
  Announcement(String i,String n,String fp,InetAddress h,int p){id=i;name=n;fingerprint=fp;host=h;port=p;}
 }
 public static final class Listener implements AutoCloseable {
  private final AtomicBoolean running=new AtomicBoolean(true);private final MulticastSocket socket;private final Thread thread;
  Listener(MulticastSocket s,Consumer<Announcement> cb){socket=s;thread=new Thread(()->{byte[] b=new byte[2048];while(running.get()&&!socket.isClosed()){try{DatagramPacket p=new DatagramPacket(b,b.length);socket.receive(p);JSONObject j=new JSONObject(new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.UTF_8));if(!APP.equals(j.optString("app"))||j.optInt("v")!=VERSION)continue;String id=j.optString("id",""),name=j.optString("name",""),fp=j.optString("fp","");int port=j.optInt("port",-1);if(id.length()<8||id.length()>128||name.isEmpty()||name.length()>128||!fp.matches("(?i)[0-9a-f]{64}")||port<1||port>65535)continue;cb.accept(new Announcement(id,name,fp,p.getAddress(),port));}catch(SocketTimeoutException ignored){}catch(Exception e){if(running.get())Log.w(TAG,"UDP discovery receive error",e);}}},"QuanLian-UDP-Listener");thread.setDaemon(true);thread.start();}
  public void close(){if(running.compareAndSet(true,false)){try{socket.leaveGroup(InetAddress.getByName(GROUP));}catch(Exception ignored){}socket.close();thread.interrupt();}}
 }
 public static Listener listen(Consumer<Announcement> callback){try{MulticastSocket s=new MulticastSocket(null);s.setReuseAddress(true);s.bind(new InetSocketAddress(PORT));s.setSoTimeout(1500);s.joinGroup(InetAddress.getByName(GROUP));return new Listener(s,callback);}catch(Exception e){Log.w(TAG,"Could not start UDP multicast listener; NSD remains active",e);return null;}}
 public static boolean verify(InetAddress host,int port,String fingerprint){if(host==null||port<1||port>65535||fingerprint==null)return false;try(javax.net.ssl.SSLSocket s=TlsIdentity.connect(host.getHostAddress(),port,fingerprint)){DataOutputStream out=new DataOutputStream(s.getOutputStream());DataInputStream in=new DataInputStream(s.getInputStream());out.writeUTF(PROBE);out.flush();return PROBE_OK.equals(in.readUTF());}catch(Exception e){return false;}}
 public static void announce(String id,String name,int port){try{announce(id,name,port,TlsIdentity.fingerprint());}catch(Exception e){Log.w(TAG,"TLS identity unavailable",e);}}
 public static void announce(String id,String name,int port,String fp){if(id==null||id.length()<8||name==null||name.isEmpty()||fp==null||!fp.matches("(?i)[0-9a-f]{64}")||port<1||port>65535)return;try{JSONObject j=new JSONObject();j.put("app",APP);j.put("v",VERSION);j.put("id",id);j.put("name",name);j.put("port",port);j.put("fp",fp);byte[] data=j.toString().getBytes(StandardCharsets.UTF_8);try(MulticastSocket s=new MulticastSocket()){s.setTimeToLive(1);s.setBroadcast(true);s.send(new DatagramPacket(data,data.length,InetAddress.getByName(GROUP),PORT));s.send(new DatagramPacket(data,data.length,InetAddress.getByName("255.255.255.255"),PORT));try{java.util.Enumeration<NetworkInterface> ns=NetworkInterface.getNetworkInterfaces();while(ns!=null&&ns.hasMoreElements()){NetworkInterface n=ns.nextElement();if(!n.isUp()||n.isLoopback())continue;for(InterfaceAddress a:n.getInterfaceAddresses()){InetAddress b=a.getBroadcast();if(b!=null)try{s.send(new DatagramPacket(data,data.length,b,PORT));}catch(Exception ignored){}}}}catch(Exception ignored){}}}catch(Exception e){Log.w(TAG,"Could not send UDP discovery announcement",e);}}
}
