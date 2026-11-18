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
            android.net.wifi.DhcpInfo dhcp=wifi.getDhcpInfo();
            int ip=dhcp.ipAddress, mask=dhcp.netmask;
            if(ip==0||mask==0){status.accept("当前网络不支持读取 IPv4 子网");pool.shutdownNow();return;}
            int network=ip&mask, broadcast=network|~mask;
            long hosts=(broadcast&0xffffffffL)-(network&0xffffffffL)-1;
            if(hosts<1||hosts>1022){status.accept("子网范围过大，已限制扫描（最多 1022 个地址）");hosts=Math.min(1022,Math.max(0,hosts));}
            final int scanPort=port;
            CountDownLatch done=new CountDownLatch((int)hosts);
            for(int i=1;i<=hosts;i++){
                final int candidate=network+i;
                pool.execute(()->{
                    try{
                        InetAddress address=InetAddress.getByAddress(new byte[]{(byte)candidate,(byte)(candidate>>>8),(byte)(candidate>>>16),(byte)(candidate>>>24)});
                        if(address.isLoopbackAddress()||address.isAnyLocalAddress())return;
                        if(LanDiscovery.verify(address,scanPort)){
                            String id="manual:"+address.getHostAddress()+":"+scanPort;
                            found.accept(new LanDiscovery.Announcement(id,"小李全联设备 "+address.getHostAddress(),address,scanPort));
                        }
                    }catch(Exception ignored){}finally{done.countDown();}
                });
            }
            done.await(12,TimeUnit.SECONDS);
            status.accept("主动扫描完成");
        }catch(Exception e){status.accept("主动扫描失败："+e.getMessage());}
        finally{pool.shutdownNow();}
    }
}
