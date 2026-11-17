package com.quanlian.app;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private static final int PICK=1001, BUF=65536;
 private static final String TYPE="_quanlian._tcp.";
 private final ArrayList<Uri> uris=new ArrayList<>();
 private final ArrayList<String> names=new ArrayList<>();
 private final ArrayList<Long> sizes=new ArrayList<>();
 private final Map<String,Device> devices=new LinkedHashMap<>();
 private final ExecutorService workers=Executors.newCachedThreadPool();
 private NsdManager nsd; private NsdManager.RegistrationListener registration;
 private NsdManager.DiscoveryListener discovery; private WifiManager.MulticastLock lock;
 private ServerSocket server; private volatile boolean alive=true; private int port=-1, page=0;
 private LinearLayout deviceList; private TextView deviceStatus, receiveStatus;
 static class Device { String name,key; InetAddress host; int port; Device(String n,String k,InetAddress h,int p){name=n;key=k;host=h;port=p;} }
 private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable shape(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
 private TextView txt(String s,int z,int c,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(b)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(20),dp(22),dp(20),dp(20));return l;}
 private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(16),dp(16),dp(16),dp(16));l.setBackground(shape(Color.rgb(28,45,75),18));l.setFocusable(true);return l;}
 private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 private Button btn(String s,int c){Button b=new Button(this);b.setText(s);b.setTextColor(-1);b.setAllCaps(false);b.setTextSize(16);b.setBackground(shape(c,16));b.setMinHeight(dp(54));return b;}
 private void install(LinearLayout l){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(l);setContentView(s);}
 private void header(LinearLayout l,String title,String sub){TextView back=txt("‹  返回首页",15,0xffa0c3e6,true);back.setPadding(0,0,0,dp(18));back.setOnClickListener(v->home());l.addView(back);l.addView(txt(title,25,-1,true));gap(l,6);l.addView(txt(sub,14,0xffabc6e4,false));gap(l,20);}
 private String myName(){String m=android.os.Build.MODEL;if(m==null||m.trim().isEmpty())m="Android";return "小李全联-"+m.replaceAll("[^\\p{L}\\p{N}_-]","");}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff0a1428);getWindow().setNavigationBarColor(0xff0a1428);nsd=(NsdManager)getSystemService(NSD_SERVICE);startLan();home();}
 private void startLan(){
  try{WifiManager w=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);lock=w.createMulticastLock("QuanLianLAN");lock.setReferenceCounted(false);lock.acquire();}catch(Exception ignored){}
  workers.execute(()->{try{server=new ServerSocket(0);port=server.getLocalPort();runOnUiThread(()->{if(receiveStatus!=null)receiveStatus.setText("接收服务已启动 · 端口 "+port);});publish();while(alive){Socket s=server.accept();workers.execute(()->receive(s));}}catch(Exception e){if(alive)runOnUiThread(()->Toast.makeText(this,"接收服务启动失败："+e.getMessage(),Toast.LENGTH_LONG).show());}});
 }
 private void publish(){
  NsdServiceInfo info=new NsdServiceInfo();info.setServiceName(myName());info.setServiceType(TYPE);info.setPort(port);
  registration=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo s){discover();}public void onRegistrationFailed(NsdServiceInfo s,int e){runOnUiThread(()->Toast.makeText(MainActivity.this,"设备广播失败",Toast.LENGTH_SHORT).show());}public void onServiceUnregistered(NsdServiceInfo s){}public void onUnregistrationFailed(NsdServiceInfo s,int e){}};
  try{nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);}catch(Exception ignored){}
 }
 private void discover(){
  if(discovery!=null)return;
  discovery=new NsdManager.DiscoveryListener(){
   public void onDiscoveryStarted(String t){}
   public void onServiceFound(NsdServiceInfo s){if(!TYPE.equals(s.getServiceType())||myName().equals(s.getServiceName()))return;try{nsd.resolveService(s,new NsdManager.ResolveListener(){public void onResolveFailed(NsdServiceInfo x,int e){}public void onServiceResolved(NsdServiceInfo x){Device d=new Device(x.getServiceName(),x.getServiceName(),x.getHost(),x.getPort());synchronized(devices){devices.put(d.key,d);}runOnUiThread(()->refreshDevices());}});}catch(Exception ignored){}}
   public void onServiceLost(NsdServiceInfo s){synchronized(devices){devices.remove(s.getServiceName());}runOnUiThread(()->refreshDevices());}
   public void onDiscoveryStopped(String t){} public void onStartDiscoveryFailed(String t,int e){try{nsd.stopServiceDiscovery(this);}catch(Exception ignored){}} public void onStopDiscoveryFailed(String t,int e){}
  };
  try{nsd.discoverServices(TYPE,NsdManager.PROTOCOL_DNS_SD,discovery);}catch(Exception ignored){}
 }
 private void refreshDevices(){
  if(deviceList==null)return;deviceList.removeAllViews();ArrayList<Device> list;
  synchronized(devices){list=new ArrayList<>(devices.values());}
  if(deviceStatus!=null)deviceStatus.setText("发现 "+list.size()+" 台附近设备");
  if(list.isEmpty()){deviceList.addView(txt("暂未发现设备。请让双方打开小李全联并连接同一 Wi-Fi。",14,0xffb7c7de,false));return;}
  for(Device d:list){LinearLayout item=card();item.setOrientation(0);item.setGravity(Gravity.CENTER_VERTICAL);LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);labels.addView(txt(d.name,16,-1,true));labels.addView(txt("局域网在线设备",12,0xffa0c3e6,false));item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));item.addView(txt("发送 ›",15,0xff64beff,true));item.setOnClickListener(v->send(d));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);deviceList.addView(item,p);}
 }
 private void receive(Socket socket){
  try(Socket s=socket;DataInputStream in=new DataInputStream(new BufferedInputStream(s.getInputStream()));DataOutputStream out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))){
   s.setSoTimeout(120000);if(!"QUANLIAN1".equals(in.readUTF()))return;String sender=in.readUTF();int count=in.readInt();if(count<1||count>500)return;
   ArrayList<String> fileNames=new ArrayList<>();ArrayList<Long> fileSizes=new ArrayList<>();long total=0;
   for(int i=0;i<count;i++){String n=in.readUTF();long z=in.readLong();if(z<0)return;fileNames.add(n);fileSizes.add(z);total+=z;}
   final long totalBytes=total;CountDownLatch latch=new CountDownLatch(1);final boolean[] yes={false};
   runOnUiThread(()->new AlertDialog.Builder(this).setTitle("接收文件请求").setMessage(sender+" 要发送 "+count+" 个文件（"+sizeText(totalBytes)+"）。是否接收？").setPositiveButton("接收",(a,b)->{yes[0]=true;latch.countDown();}).setNegativeButton("拒绝",(a,b)->latch.countDown()).setOnCancelListener(a->latch.countDown()).show());
   boolean accepted=latch.await(90,TimeUnit.SECONDS)&&yes[0];out.writeBoolean(accepted);out.flush();if(!accepted)return;
   File dir=new File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),"QuanLianReceived");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建接收目录");
   byte[] buf=new byte[BUF];
   for(int i=0;i<count;i++){String safe=fileNames.get(i).replaceAll("[\\\\/:*?\"<>|]","_");File f=new File(dir,System.currentTimeMillis()+"_"+safe);long left=fileSizes.get(i);try(FileOutputStream fos=new FileOutputStream(f)){while(left>0){int n=in.read(buf,0,(int)Math.min(buf.length,left));if(n<0)throw new EOFException("文件未传完");fos.write(buf,0,n);left-=n;}}}
   out.writeBoolean(true);out.flush();runOnUiThread(()->Toast.makeText(this,"接收完成，文件保存在小李全联接收目录",Toast.LENGTH_LONG).show());
  }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"接收失败："+e.getMessage(),Toast.LENGTH_LONG).show());}
 }
 private void send(Device d){
  if(uris.isEmpty()){Toast.makeText(this,"请先选择文件",Toast.LENGTH_SHORT).show();return;}
  Toast.makeText(this,"正在连接 "+d.name,Toast.LENGTH_SHORT).show();
  workers.execute(()->{try(Socket s=new Socket(d.host,d.port)){s.setSoTimeout(120000);DataOutputStream out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));DataInputStream in=new DataInputStream(new BufferedInputStream(s.getInputStream()));out.writeUTF("QUANLIAN1");out.writeUTF(myName());out.writeInt(uris.size());for(int i=0;i<uris.size();i++){out.writeUTF(names.get(i));out.writeLong(sizes.get(i));}out.flush();if(!in.readBoolean())throw new IOException("对方拒绝接收");byte[] buf=new byte[BUF];for(Uri u:new ArrayList<>(uris)){try(InputStream is=getContentResolver().openInputStream(u)){if(is==null)throw new IOException("无法读取文件");int n;while((n=is.read(buf))!=-1)out.write(buf,0,n);}out.flush();}if(!in.readBoolean())throw new IOException("接收端未确认完成");runOnUiThread(()->Toast.makeText(this,"发送成功："+d.name,Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"发送失败："+e.getMessage(),Toast.LENGTH_LONG).show());}});
 }
 private void home(){
  page=0;deviceList=null;deviceStatus=null;LinearLayout l=col();l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a1833,0xff0d3052,0xff0a1930}));
  l.addView(txt("小李全联",30,-1,true));TextView sub=txt("让所有设备，自由交换文件",14,0xffabc6e4,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=dp(4);sp.bottomMargin=dp(22);l.addView(sub,sp);
  LinearLayout row=new LinearLayout(this);Button send=btn("↑  发送文件",0xff1f77dc),recv=btn("↓  接收文件",0xff1f9e8f);LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(62),1);a.rightMargin=dp(6);row.addView(send,a);LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(62),1);b.leftMargin=dp(6);row.addView(recv,b);l.addView(row);send.setOnClickListener(v->pick());recv.setOnClickListener(v->receivePage());
  gap(l,24);l.addView(txt("附近设备",20,-1,true));gap(l,8);deviceStatus=txt("正在搜索局域网设备…",13,0xffabc6e4,false);l.addView(deviceStatus);gap(l,8);deviceList=new LinearLayout(this);deviceList.setOrientation(1);l.addView(deviceList);refreshDevices();gap(l,24);TextView foot=txt("Wi-Fi 局域网直连优先 · 无广告 · 无强制账号",12,0xff96b1cf,false);foot.setGravity(Gravity.CENTER);l.addView(foot);install(l);
 }
 private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK);}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r!=PICK||c!=RESULT_OK||d==null)return;uris.clear();names.clear();sizes.clear();if(d.getClipData()!=null){for(int i=0;i<d.getClipData().getItemCount();i++)addFile(d.getClipData().getItemAt(i).getUri());}else if(d.getData()!=null)addFile(d.getData());selectedPage();}
 private void addFile(Uri u){String n="文件";long z=-1;try(Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),si=c.getColumnIndex(OpenableColumns.SIZE);if(ni>=0)n=c.getString(ni);if(si>=0&&!c.isNull(si))z=c.getLong(si);}}catch(Exception ignored){}uris.add(u);names.add(n);sizes.add(z);}
 private String sizeText(long n){if(n<0)return "大小未知";if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.getDefault(),"%.1f KB",n/1024d);if(n<1073741824)return String.format(Locale.getDefault(),"%.1f MB",n/1048576d);return String.format(Locale.getDefault(),"%.2f GB",n/1073741824d);}
 private void selectedPage(){page=1;LinearLayout l=col();l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a1833,0xff0d3052,0xff0a1930}));header(l,"已选择文件","选择接收设备，开始局域网发送");long total=0;for(long z:sizes)if(z>0)total+=z;l.addView(txt("已选择 "+uris.size()+" 个文件 · "+sizeText(total),15,0xffbed7f2,true));gap(l,12);for(int i=0;i<names.size();i++){LinearLayout c=card();c.addView(txt(names.get(i),15,-1,true));gap(c,4);c.addView(txt(sizeText(sizes.get(i)),12,0xffb7c7de,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);l.addView(c,p);}Button choose=btn("选择接收设备",0xff1f77dc);choose.setOnClickListener(v->devicePage());l.addView(choose);Button more=btn("继续添加文件",0xff304b70);more.setOnClickListener(v->pick());LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(8);l.addView(more,mp);install(l);}
 private void devicePage(){page=2;LinearLayout l=col();l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a1833,0xff0d3052,0xff0a1930}));header(l,"选择接收设备","点击设备名称即可发起文件传输");deviceStatus=txt("正在搜索…",13,0xffabc6e4,false);l.addView(deviceStatus);gap(l,8);deviceList=new LinearLayout(this);deviceList.setOrientation(1);l.addView(deviceList);refreshDevices();Button refresh=btn("刷新设备列表",0xff304b70);refresh.setOnClickListener(v->refreshDevices());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);l.addView(refresh,p);install(l);}
 private void receivePage(){page=3;LinearLayout l=col();l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a1833,0xff0d3052,0xff0a1930}));header(l,"接收文件","打开接收页面，等待附近设备发送");LinearLayout c=card();c.setGravity(Gravity.CENTER_HORIZONTAL);TextView icon=txt("◎",54,0xff46beae,true);icon.setGravity(Gravity.CENTER);c.addView(icon);gap(c,8);c.addView(txt("正在等待连接",20,-1,true));gap(c,8);receiveStatus=txt(port>0?"接收服务已启动 · 端口 "+port:"正在启动接收服务…",13,0xffb7c7de,false);c.addView(receiveStatus);l.addView(c);gap(l,12);LinearLayout note=card();note.addView(txt("使用方法",16,-1,true));gap(note,6);note.addView(txt("发送设备打开小李全联后，会自动发现本设备。发送方点击本机名称后，本机将弹出接收确认。双方需要连接同一 Wi-Fi。",13,0xffb7c7de,false));l.addView(note);install(l);}
 @Override protected void onDestroy(){alive=false;try{if(server!=null)server.close();}catch(Exception ignored){}try{if(nsd!=null&&discovery!=null)nsd.stopServiceDiscovery(discovery);}catch(Exception ignored){}try{if(nsd!=null&&registration!=null)nsd.unregisterService(registration);}catch(Exception ignored){}try{if(lock!=null&&lock.isHeld())lock.release();}catch(Exception ignored){}workers.shutdownNow();super.onDestroy();}
 @Override public void onBackPressed(){if(page==0)super.onBackPressed();else if(page==1)home();else if(page==2)selectedPage();else home();}
}