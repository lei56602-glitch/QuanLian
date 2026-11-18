package com.quanlian.app;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bounded IPv4 subnet fallback scan; uses the same QuanLian TCP probe as discovery. */
public final class LanScanner {
    private LanScanner() {}
    public static void scan(Context context,int port,Consumer<LanDiscovery.Announcement> found,Consumer<String> status) {
        ExecutorService pool=Executors.newFixedThreadPool(32);
        try {
            WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(wifi==null||wifi.getDhcpInfo()==null){status.accept("无法读取 Wi-Fi 子网信息");pool.shutdownNow();return;}
            android.net.DhcpInfo dhcp=wifi.getDhcpInfo();
            int ip=Integer.reverseBytes(dhcp.ipAddress), mask=Integer.reverseBytes(dhcp.netmask);
            if(ip==0||mask==0){status.accept("当前网络不支持读取 IPv4 子网");pool.shutdownNow();return;}
            int network=ip&mask, broadcast=network|~mask;
            long hosts=(broadcast&0xffffffffL)-(network&0xffffffffL)-1;
            if(hosts<1){status.accept("当前 IPv4 子网没有可扫描地址");return;}
            int first=network+1;
            if(hosts>1022){status.accept("子网范围较大，优先扫描本机附近的 1022 个地址");first=Math.max(network+1,Math.min(ip-511,broadcast-1022));hosts=1022;}
            final int scanPort=port;
            CountDownLatch done=new CountDownLatch((int)hosts);
            for(int i=0;i<hosts;i++){
                final int candidate=first+i;
                pool.execute(()->{
                    try{
                        InetAddress address=InetAddress.getByAddress(new byte[]{(byte)(candidate>>>24),(byte)(candidate>>>16),(byte)(candidate>>>8),(byte)candidate});
                        if(address.isLoopbackAddress()||address.isAnyLocalAddress())return;
                        String fp=TlsIdentity.inspectFingerprint(address.getHostAddress(),scanPort);
                        if(LanDiscovery.verify(address,scanPort,fp)){
                            String id="manual:"+address.getHostAddress()+":"+scanPort;
                            found.accept(new LanDiscovery.Announcement(id,"小李全联设备 "+address.getHostAddress(),fp,address,scanPort));
                        }
                    }catch(Exception ignored){}finally{done.countDown();}
                });
            }
            done.await(25,TimeUnit.SECONDS);
            status.accept("主动扫描完成");
        }catch(Exception e){status.accept("主动扫描失败："+e.getMessage());}
        finally{pool.shutdownNow();}
    }
}
