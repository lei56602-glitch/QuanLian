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
import android.content.ContentValues;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {
 private static final int PICK=1001, REQ_NOTIFICATIONS=1002, BUF=65536;
 private static final String TYPE="_quanlian._tcp.";
 private final ArrayList<Uri> uris=new ArrayList<>();
 private final ArrayList<String> names=new ArrayList<>();
 private final ArrayList<Long> sizes=new ArrayList<>();
 private final Map<String,Device> devices=new LinkedHashMap<>();
 private final ExecutorService workers=Executors.newCachedThreadPool();
 private NsdManager nsd; private NsdManager.RegistrationListener registration; private LanDiscovery.Listener udpDiscovery;
 private NsdManager.DiscoveryListener discovery; private WifiManager.MulticastLock lock; private final android.os.Handler discoveryHandler=new android.os.Handler(android.os.Looper.getMainLooper());
 private ServerSocket server; private volatile boolean alive=true; private int port=-1, page=0;
 private LinearLayout deviceList; private TextView deviceStatus, receiveStatus;
 private ProgressDialog sendProgress; private BroadcastReceiver sendReceiver, receiveReceiver; private Device retryDevice;
 static class Device { String name,key; InetAddress host; int port; long udpLastSeen; boolean udpSeen,nsdSeen; Device(String n,String k,InetAddress h,int p){name=n;key=k;host=h;port=p;} }
 private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable shape(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
 private android.graphics.drawable.Drawable focusBackground(int normal,int focused,int radius){android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();GradientDrawable on=shape(focused,radius);on.setStroke(dp(2),0xff72c5ff);GradientDrawable off=shape(normal,radius);states.addState(new int[]{android.R.attr.state_focused},on);states.addState(new int[]{android.R.attr.state_pressed},on);states.addState(new int[]{},off);return states;}
 private TextView txt(String s,int z,int c,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(b)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);int widthDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);int horizontal=widthDp<360?14:(widthDp<600?20:32);l.setPadding(dp(horizontal),dp(widthDp<600?18:24),dp(horizontal),dp(widthDp<600?18:24));return l;}
 private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(16),dp(16),dp(16));l.setBackground(focusBackground(Color.rgb(28,45,75),0xff345b86,18));l.setFocusable(true);l.setFocusableInTouchMode(false);l.setClickable(true);return l;}
 private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 private Button btn(String s,int c){Button b=new Button(this);b.setText(s);b.setTextColor(-1);b.setAllCaps(false);b.setTextSize(16);b.setBackground(focusBackground(c,0xff2869a5,16));b.setMinHeight(dp(54));b.setFocusable(true);b.setFocusableInTouchMode(false);return b;}
 private void install(LinearLayout l){int widthDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setFocusable(false);if(widthDp>=700){FrameLayout frame=new FrameLayout(this);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.min(widthDp,1200)*((int)getResources().getDisplayMetrics().density),-1,Gravity.CENTER);s.addView(l,new ScrollView.LayoutParams(-1,-2));frame.addView(s,p);setContentView(frame);}else{s.addView(l);setContentView(s);}if(widthDp>=700){l.setPadding(dp(32),dp(28),dp(32),dp(28));}l.post(()->{ArrayList<View> focusables=l.getFocusables(View.FOCUS_FORWARD);if(!focusables.isEmpty()&&!focusables.get(0).hasFocus())focusables.get(0).requestFocus();});}
 private void header(LinearLayout l,String title,String sub){TextView back=txt("‹  返回首页",15,0xffa0c3e6,true);back.setPadding(0,0,0,dp(18));back.setOnClickListener(v->home());l.addView(back);l.addView(txt(title,25,-1,true));gap(l,6);l.addView(txt(sub,14,0xffabc6e4,false));gap(l,20);}
 private String myName(){return DeviceIdentity.name(this);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff0a1428);getWindow().setNavigationBarColor(0xff0a1428);nsd=(NsdManager)getSystemService(NSD_SERVICE);requestNotificationPermissionIfNeeded();if(b!=null){try{ArrayList<android.os.Parcelable> saved=b.getParcelableArrayList("selected_uris");if(saved!=null)for(android.os.Parcelable item:saved)if(item instanceof Uri)uris.add((Uri)item);ArrayList<String> savedNames=b.getStringArrayList("selected_names");if(savedNames!=null)names.addAll(savedNames);ArrayList<Long> savedSizes=(ArrayList<Long>)b.getSerializable("selected_sizes");if(savedSizes!=null)sizes.addAll(savedSizes);page=b.getInt("current_page",0);}catch(Exception ignored){page=0;}}startLan();restorePage();}
 @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putParcelableArrayList("selected_uris",new ArrayList<>(uris));out.putStringArrayList("selected_names",new ArrayList<>(names));out.putSerializable("selected_sizes",new ArrayList<>(sizes));out.putInt("current_page",page);}
 private void restorePage(){switch(page){case 1:if(!uris.isEmpty())selectedPage();else home();break;case 2:if(!uris.isEmpty())devicePage();else home();break;case 3:receivePage();break;case 4:historyPage();break;default:home();}}
 @Override public void onConfigurationChanged(android.content.res.Configuration newConfig){super.onConfigurationChanged(newConfig);restorePage();}
 private void requestNotificationPermissionIfNeeded(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);}}
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQ_NOTIFICATIONS&&grantResults.length>0&&grantResults[0]!=android.content.pm.PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"通知权限未开启，传输仍可尝试进行，但系统可能不会正常显示传输通知。",Toast.LENGTH_LONG).show();}}
 private void startLan(){
  ReceiveForegroundService.start(this);
  discoveryHandler.post(pruneDevices);
  try{WifiManager w=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);lock=w.createMulticastLock("QuanLianLAN");lock.setReferenceCounted(false);lock.acquire();}catch(Exception ignored){}
  udpDiscovery=LanDiscovery.listen(announcement->{
   if(!alive||announcement==null||announcement.host==null||announcement.port<1||myName().equals(announcement.name))return;
   workers.execute(()->{
    if(!LanDiscovery.verify(announcement.host,announcement.port))return;
    runOnUiThread(()->{if(!alive)return; synchronized(devices){Device device=devices.get(announcement.id);if(device==null){device=new Device(announcement.name,announcement.id,announcement.host,announcement.port);devices.put(device.key,device);}device.name=announcement.name;device.host=announcement.host;device.port=announcement.port;device.udpSeen=true;device.udpLastSeen=android.os.SystemClock.elapsedRealtime();}refreshDevices();});
   });
  });
  new android.os.Handler(getMainLooper()).postDelayed(()->{if(alive&&nsd!=null)discover();},900);
 }
 @Override protected void onResume(){super.onResume();registerReceiveReceiver();}
 @Override protected void onPause(){unregisterReceiveReceiver();super.onPause();}
 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 private void registerReceiveReceiver(){
  if(receiveReceiver!=null)return;
  receiveReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
   if(i==null)return;
   if(ReceiveForegroundService.ACTION_STATUS.equals(i.getAction())){
    port=i.getIntExtra(ReceiveForegroundService.EXTRA_PORT,port);
    if(receiveStatus!=null){String t=i.getStringExtra(ReceiveForegroundService.EXTRA_TEXT);receiveStatus.setText(t==null?"接收服务运行中":t);}
   }else if(ReceiveForegroundService.ACTION_INCOMING.equals(i.getAction())){
    String id=i.getStringExtra(ReceiveForegroundService.EXTRA_ID),sender=i.getStringExtra(ReceiveForegroundService.EXTRA_SENDER);
    int count=i.getIntExtra(ReceiveForegroundService.EXTRA_COUNT,0);long total=i.getLongExtra(ReceiveForegroundService.EXTRA_TOTAL,0);
    new AlertDialog.Builder(MainActivity.this).setTitle("接收文件请求").setMessage(sender+" 要发送 "+count+" 个文件（"+sizeText(total)+"）。是否接收？")
     .setPositiveButton("接收",(d,w)->ReceiveForegroundService.decide(MainActivity.this,id,true))
     .setNegativeButton("拒绝",(d,w)->ReceiveForegroundService.decide(MainActivity.this,id,false))
     .setOnCancelListener(d->ReceiveForegroundService.decide(MainActivity.this,id,false)).show();
   }
  }};
  IntentFilter f=new IntentFilter();f.addAction(ReceiveForegroundService.ACTION_STATUS);f.addAction(ReceiveForegroundService.ACTION_INCOMING);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(receiveReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiveReceiver,f);
 }
 private void unregisterReceiveReceiver(){if(receiveReceiver!=null){try{unregisterReceiver(receiveReceiver);}catch(Exception ignored){}receiveReceiver=null;}}
 private void publish(){
  NsdServiceInfo info=new NsdServiceInfo();info.setServiceName(myName());info.setServiceType(TYPE);info.setPort(port);info.setAttribute("id",DeviceIdentity.id(this));
  registration=new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo s){discover();}public void onRegistrationFailed(NsdServiceInfo s,int e){runOnUiThread(()->Toast.makeText(MainActivity.this,"设备广播失败",Toast.LENGTH_SHORT).show());}public void onServiceUnregistered(NsdServiceInfo s){}public void onUnregistrationFailed(NsdServiceInfo s,int e){}};
  try{nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);}catch(Exception ignored){}
 }
 private boolean isQuanLianService(String serviceType){
  return serviceType!=null && serviceType.toLowerCase(Locale.US).startsWith(TYPE);
 }
 private void discover(){
  if(discovery!=null||!alive||nsd==null)return;
  NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
   public void onDiscoveryStarted(String t){}
   public void onServiceFound(NsdServiceInfo service){
    if(!isQuanLianService(service.getServiceType())||myName().equals(service.getServiceName()))return;
    try{nsd.resolveService(service,new NsdManager.ResolveListener(){
     public void onResolveFailed(NsdServiceInfo info,int error){}
     public void onServiceResolved(NsdServiceInfo info){
      InetAddress chosen=info.getHost();
      if(Build.VERSION.SDK_INT>=34){try{for(InetAddress candidate:info.getHostAddresses()){if(candidate instanceof Inet4Address){chosen=candidate;break;}if(chosen==null)chosen=candidate;}}catch(Exception ignored){}}
      if(chosen==null||info.getPort()<1)return;
      String deviceId=null;try{byte[] raw=info.getAttributes().get("id");if(raw!=null)deviceId=new String(raw,java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ignored){}
      if(deviceId==null||deviceId.length()<8)deviceId="nsd:"+info.getServiceName();
      final String key=deviceId;
      synchronized(devices){Device device=devices.get(key);if(device==null){device=new Device(info.getServiceName(),key,chosen,info.getPort());devices.put(key,device);}device.name=info.getServiceName();device.host=chosen;device.port=info.getPort();device.nsdSeen=true;}
      runOnUiThread(()->refreshDevices());
     }
    });}catch(Exception ignored){}
   }
   public void onServiceLost(NsdServiceInfo service){synchronized(devices){Iterator<Map.Entry<String,Device>> it=devices.entrySet().iterator();while(it.hasNext()){Device d=it.next().getValue();if(d.name.equals(service.getServiceName())){d.nsdSeen=false;if(!d.udpSeen)it.remove();}}}runOnUiThread(()->refreshDevices());}
   public void onDiscoveryStopped(String type){if(discovery==this)discovery=null;}
   public void onStartDiscoveryFailed(String type,int error){try{nsd.stopServiceDiscovery(this);}catch(Exception ignored){}if(discovery==this)discovery=null;new android.os.Handler(getMainLooper()).postDelayed(()->{if(alive)discover();},2000);}
   public void onStopDiscoveryFailed(String type,int error){try{nsd.stopServiceDiscovery(this);}catch(Exception ignored){}if(discovery==this)discovery=null;new android.os.Handler(getMainLooper()).postDelayed(()->{if(alive)discover();},2000);}
  };
  discovery=listener;
  try{nsd.discoverServices(TYPE,NsdManager.PROTOCOL_DNS_SD,listener);}catch(Exception error){if(discovery==listener)discovery=null;new android.os.Handler(getMainLooper()).postDelayed(()->{if(alive)discover();},2000);}
 }
 private void refreshDiscovery(){
  synchronized(devices){devices.clear();}
  refreshDevices();
  if(deviceStatus!=null)deviceStatus.setText("正在重新搜索局域网设备…");
  NsdManager.DiscoveryListener old=discovery;
  discovery=null;
  if(old!=null){try{nsd.stopServiceDiscovery(old);}catch(Exception ignored){}}
  new android.os.Handler(getMainLooper()).postDelayed(()->{
   if(!alive||nsd==null)return;
   discover();
  },600);
 }
 private final Runnable pruneDevices=new Runnable(){@Override public void run(){if(!alive)return;long now=android.os.SystemClock.elapsedRealtime();synchronized(devices){Iterator<Map.Entry<String,Device>> it=devices.entrySet().iterator();while(it.hasNext()){Device d=it.next().getValue();if(d.udpSeen&&now-d.udpLastSeen>9000)d.udpSeen=false;if(!d.udpSeen&&!d.nsdSeen)it.remove();}}refreshDevices();discoveryHandler.postDelayed(this,3000);}};
 private void refreshDevices(){
  if(deviceList==null)return;deviceList.removeAllViews();ArrayList<Device> list;
  synchronized(devices){list=new ArrayList<>(devices.values());}
  if(deviceStatus!=null)deviceStatus.setText("发现 "+list.size()+" 台附近设备");
  if(list.isEmpty()){deviceList.addView(txt("暂未发现设备。请让双方打开小李全联并连接同一 Wi-Fi。",14,0xffb7c7de,false));return;}
  for(Device d:list){LinearLayout item=card();item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER_VERTICAL);LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(txt(d.name,16,-1,true));labels.addView(txt(d.udpSeen?"UDP 验证在线":"NSD 发现设备",12,0xffa0c3e6,false));item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));item.addView(txt("发送 ›",15,0xff64beff,true));item.setOnClickListener(v->send(d));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);deviceList.addView(item,p);}
 }
 private void receive(Socket socket){
  try(Socket s=socket;DataInputStream in=new DataInputStream(new BufferedInputStream(s.getInputStream()));DataOutputStream out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))){
   s.setSoTimeout(180000);runOnUiThread(()->Toast.makeText(this,"收到设备连接，正在读取发送请求…",Toast.LENGTH_SHORT).show());if(!"QUANLIAN2".equals(in.readUTF()))throw new IOException("传输协议不匹配");String sender=in.readUTF();int count=in.readInt();if(count<1||count>500)return;
   ArrayList<String> fileNames=new ArrayList<>();ArrayList<Long> fileSizes=new ArrayList<>();ArrayList<String> hashes=new ArrayList<>();long total=0;
   for(int i=0;i<count;i++){String name=in.readUTF();long size=in.readLong();String hash=in.readUTF();if(size<0||hash.length()!=64||total>Long.MAX_VALUE-size)return;fileNames.add(name);fileSizes.add(size);hashes.add(hash);total+=size;}
   final long totalBytes=total;CountDownLatch latch=new CountDownLatch(1);final boolean[] yes={false};
   runOnUiThread(()->new AlertDialog.Builder(this).setTitle("接收文件请求").setMessage(sender+" 要发送 "+count+" 个文件（"+sizeText(totalBytes)+"）。是否接收？").setPositiveButton("接收",(a,b)->{yes[0]=true;latch.countDown();}).setNegativeButton("拒绝",(a,b)->latch.countDown()).setOnCancelListener(a->latch.countDown()).show());
   boolean accepted=latch.await(90,TimeUnit.SECONDS)&&yes[0];out.writeBoolean(accepted);out.flush();if(!accepted){if(latch.getCount()==0&&!yes[0])runOnUiThread(()->Toast.makeText(this,"已拒绝或取消接收",Toast.LENGTH_SHORT).show());else runOnUiThread(()->Toast.makeText(this,"等待接收确认超时",Toast.LENGTH_LONG).show());return;}
   File dir=new File(getCacheDir(),"QuanLianIncoming");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建临时接收目录");
   byte[] buf=new byte[BUF];
   for(int i=0;i<count;i++){
    String safe=fileNames.get(i).replaceAll("[\\\\/:*?\"<>|]","_").trim();if(safe.isEmpty()||safe.equals(".")||safe.equals(".."))safe="received_file_"+i;
    File temp=new File(dir,".quanlian_"+UUID.randomUUID()+".part");MessageDigest digest=MessageDigest.getInstance("SHA-256");long left=fileSizes.get(i);
    try(FileOutputStream fos=new FileOutputStream(temp)){while(left>0){int n=in.read(buf,0,(int)Math.min(buf.length,left));if(n<0)throw new EOFException("文件未传完");fos.write(buf,0,n);digest.update(buf,0,n);left-=n;}fos.getFD().sync();}
    String actual=hex(digest.digest());if(!actual.equalsIgnoreCase(hashes.get(i))){temp.delete();throw new IOException("文件校验失败："+safe);}
    Uri saved=publishReceivedFile(temp,safe);temp.delete();if(saved==null)throw new IOException("无法保存到手机 Download/小李全联 目录："+safe);addHistory("接收",safe,fileSizes.get(i),sender,saved.toString());
   }
   out.writeBoolean(true);out.flush();runOnUiThread(()->Toast.makeText(this,"接收完成，文件已通过完整性校验",Toast.LENGTH_LONG).show());
  }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"接收失败："+e.getMessage(),Toast.LENGTH_LONG).show());}
 }
 private static class TransferFile { File file; String name,hash; long size; TransferFile(File f,String n,long z,String h){file=f;name=n;size=z;hash=h;} }
 private void send(Device d){
  if(uris.isEmpty()){Toast.makeText(this,"请先选择文件",Toast.LENGTH_SHORT).show();return;}
  sendProgress=new ProgressDialog(this);sendProgress.setTitle("小李全联正在发送");sendProgress.setMessage("正在准备文件并计算校验值…");sendProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);sendProgress.setMax(1000);sendProgress.setProgress(0);
  sendProgress.setButton(ProgressDialog.BUTTON_NEGATIVE,"取消",(dialog,which)->TransferForegroundService.cancelTransfer(this));
  sendProgress.setCancelable(false);sendProgress.show();
  retryDevice=d;
  registerSendReceiver(d);
  TransferForegroundService.sendFiles(this,new ArrayList<>(uris),new ArrayList<>(names),d.host.getHostAddress(),d.port,d.name);
 }

 @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
 private void registerSendReceiver(Device target){
  unregisterSendReceiver();
  sendReceiver=new BroadcastReceiver(){
   @Override public void onReceive(Context context,Intent intent){
    if(intent==null)return;
    if(TransferForegroundService.ACTION_PROGRESS.equals(intent.getAction())){
     if(sendProgress!=null&&sendProgress.isShowing()){
      sendProgress.setMessage(intent.getStringExtra(TransferForegroundService.EXTRA_TEXT));
      sendProgress.setProgress(intent.getIntExtra(TransferForegroundService.EXTRA_PROGRESS,0));
     }
    }else if(TransferForegroundService.ACTION_RESULT.equals(intent.getAction())){
     boolean success=intent.getBooleanExtra(TransferForegroundService.EXTRA_SUCCESS,false);
     boolean cancelled=intent.getBooleanExtra(TransferForegroundService.EXTRA_CANCELLED,false);
     String error=intent.getStringExtra(TransferForegroundService.EXTRA_ERROR);
     if(sendProgress!=null){sendProgress.dismiss();sendProgress=null;}
     unregisterSendReceiver();
     if(success){uris.clear();names.clear();sizes.clear();retryDevice=null;page=0;home();Toast.makeText(MainActivity.this,"发送成功："+target.name+"，已完成传输并清空待发送列表",Toast.LENGTH_LONG).show();}
     else if(cancelled)Toast.makeText(MainActivity.this,"已取消发送",Toast.LENGTH_SHORT).show();
     else new AlertDialog.Builder(MainActivity.this).setTitle("发送失败").setMessage(error==null?"传输中断":error).setNegativeButton("关闭",null).setPositiveButton("重新发送",(dialog,which)->send(target)).show();
    }
   }
  };
  IntentFilter filter=new IntentFilter();
  filter.addAction(TransferForegroundService.ACTION_PROGRESS);
  filter.addAction(TransferForegroundService.ACTION_RESULT);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(sendReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
  else registerReceiver(sendReceiver,filter);
 }

 private void unregisterSendReceiver(){
  if(sendReceiver!=null){try{unregisterReceiver(sendReceiver);}catch(Exception ignored){}sendReceiver=null;}
 }
 private Uri publishReceivedFile(File source,String name)throws Exception{
  if(Build.VERSION.SDK_INT>=29){
   ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/小李全联");values.put(MediaStore.MediaColumns.IS_PENDING,1);
   Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)return null;
   try(OutputStream out=getContentResolver().openOutputStream(uri);InputStream in=new FileInputStream(source)){if(out==null)throw new IOException("无法打开下载目录");byte[] b=new byte[BUF];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
   values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,values,null,null);return uri;
  }
  File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"小李全联");if(!dir.exists()&&!dir.mkdirs())return null;File dst=new File(dir,System.currentTimeMillis()+"_"+name);try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(dst)){byte[] b=new byte[BUF];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return Uri.fromFile(dst);
 }
 private void addHistory(String direction,String name,long size,String peer,String uri){
  try{android.content.SharedPreferences p=getSharedPreferences("transfer_history",MODE_PRIVATE);JSONArray old=new JSONArray(p.getString("items","[]"));JSONArray next=new JSONArray();JSONObject item=new JSONObject();item.put("direction",direction);item.put("name",name);item.put("size",size);item.put("peer",peer);item.put("time",System.currentTimeMillis());item.put("uri",uri);next.put(item);for(int i=0;i<old.length()&&i<199;i++)next.put(old.getJSONObject(i));p.edit().putString("items",next.toString()).apply();}catch(Exception ignored){}
 }
 private void historyPage(){
  page=4;LinearLayout l=col();l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a1833,0xff0d3052,0xff0a1930}));header(l,"传输历史记录","查看已发送和已接收的文件");JSONArray items;try{items=new JSONArray(getSharedPreferences("transfer_history",MODE_PRIVATE).getString("items","[]"));}catch(Exception e){items=new JSONArray();}
  if(items.length()==0)l.addView(txt("暂时没有传输记录",14,0xffb7c7de,false));
  for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);if(o==null)continue;LinearLayout item=card();String name=o.optString("name","文件");String direction=o.optString("direction","");String peer=o.optString("peer","");long size=o.optLong("size",0);long time=o.optLong("time",0);item.addView(txt((direction.equals("接收")?"↓ 已接收  ":"↑ 已发送  ")+name,15,-1,true));gap(item,5);item.addView(txt(sizeText(size)+" · "+peer,12,0xffb7c7de,false));gap(item,4);item.addView(txt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(time)),11,0xff91aac7,false));
   if(direction.equals("接收")){Button open=btn("打开文件",0xff1f77dc);String uri=o.optString("uri","");open.setOnClickListener(v->{try{Uri u=Uri.parse(uri);Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(u,getContentResolver().getType(u));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(intent);}catch(Exception e){Toast.makeText(this,"暂时无法打开，请到手机文件管理的 Download/小李全联 查找",Toast.LENGTH_LONG).show();}});item.addView(open);}
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);l.addView(item,p);
  }
  Button clear=btn("清空历史记录",0xff304b70);clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("清空历史记录").setMessage("只清除记录，不会删除已传输的文件。").setNegativeButton("取消",null).setPositiveButton("清空",(a,b)->{getSharedPreferences("transfer_history",MODE_PRIVATE).edit().clear().apply();historyPage();}).show());l.addView(clear);install(l);
 }

 private String etaText(long seconds){if(seconds<60)return seconds+" 秒";if(seconds<3600)return (seconds/60)+" 分钟 "+(seconds%60)+" 秒";return (seconds/3600)+" 小时 "+((seconds%3600)/60)+" 分钟";}
 private static String hex(byte[] bytes){StringBuilder b=new StringBuilder(bytes.length*2);for(byte v:bytes)b.append(String.format(Locale.US,"%02x",v&0xff));return b.toString();}
 private void home(){
  page=0;deviceList=null;deviceStatus=null;LinearLayout l=col();l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a1833,0xff0d3052,0xff0a1930}));
  l.addView(txt("小李全联",30,-1,true));TextView sub=txt("让所有设备，自由交换文件",14,0xffabc6e4,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=dp(4);sp.bottomMargin=dp(22);l.addView(sub,sp);
  LinearLayout row=new LinearLayout(this);Button send=btn("↑  发送文件",0xff1f77dc),recv=btn("↓  接收文件",0xff1f9e8f);LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(62),1);a.rightMargin=dp(6);row.addView(send,a);LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(62),1);b.leftMargin=dp(6);row.addView(recv,b);l.addView(row);send.setOnClickListener(v->pick());recv.setOnClickListener(v->receivePage());
  gap(l,16);Button history=btn("传输历史记录",0xff304b70);history.setOnClickListener(v->historyPage());l.addView(history);gap(l,24);l.addView(txt("附近设备",20,-1,true));gap(l,8);deviceStatus=txt("正在搜索局域网设备…",13,0xffabc6e4,false);l.addView(deviceStatus);gap(l,8);deviceList=new LinearLayout(this);deviceList.setOrientation(LinearLayout.VERTICAL);l.addView(deviceList);refreshDevices();gap(l,24);TextView foot=txt("Wi-Fi 局域网直连优先 · 无广告 · 无强制账号",12,0xff96b1cf,false);foot.setGravity(Gravity.CENTER);l.addView(foot);install(l);
 }
 private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK);}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r!=PICK||c!=RESULT_OK||d==null)return;uris.clear();names.clear();sizes.clear();if(d.getClipData()!=null){for(int i=0;i<d.getClipData().getItemCount();i++)addFile(d.getClipData().getItemAt(i).getUri());}else if(d.getData()!=null)addFile(d.getData());selectedPage();}
 private void addFile(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}String n="文件";long z=-1;try(Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),si=c.getColumnIndex(OpenableColumns.SIZE);if(ni>=0)n=c.getString(ni);if(si>=0&&!c.isNull(si))z=c.getLong(si);}}catch(Exception ignored){}uris.add(u);names.add(n);sizes.add(z);}
 private String sizeText(long n){if(n<0)return "大小未知";if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.getDefault(),"%.1f KB",n/1024d);if(n<1073741824)return String.format(Locale.getDefault(),"%.1f MB",n/1048576d);return String.format(Locale.getDefault(),"%.2f GB",n/1073741824d);}
 private void selectedPage(){page=1;LinearLayout l=col();l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a1833,0xff0d3052,0xff0a1930}));header(l,"已选择文件","选择接收设备，开始局域网发送");long total=0;for(long z:sizes)if(z>0)total+=z;l.addView(txt("已选择 "+uris.size()+" 个文件 · "+sizeText(total),15,0xffbed7f2,true));gap(l,12);for(int i=0;i<names.size();i++){LinearLayout c=card();c.addView(txt(names.get(i),15,-1,true));gap(c,4);c.addView(txt(sizeText(sizes.get(i)),12,0xffb7c7de,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);l.addView(c,p);}Button choose=btn("选择接收设备",0xff1f77dc);choose.setOnClickListener(v->devicePage());l.addView(choose);Button more=btn("继续添加文件",0xff304b70);more.setOnClickListener(v->pick());LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(8);l.addView(more,mp);install(l);}
 private void devicePage(){page=2;LinearLayout l=col();l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a1833,0xff0d3052,0xff0a1930}));header(l,"选择接收设备","点击设备名称即可发起文件传输");deviceStatus=txt("正在搜索…",13,0xffabc6e4,false);l.addView(deviceStatus);gap(l,8);deviceList=new LinearLayout(this);deviceList.setOrientation(LinearLayout.VERTICAL);l.addView(deviceList);refreshDevices();Button refresh=btn("刷新设备列表",0xff304b70);refresh.setOnClickListener(v->refreshDiscovery());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);l.addView(refresh,p);install(l);}
 private void receivePage(){page=3;LinearLayout l=col();l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0a1833,0xff0d3052,0xff0a1930}));header(l,"接收文件","打开接收页面，等待附近设备发送");LinearLayout c=card();c.setGravity(Gravity.CENTER_HORIZONTAL);TextView icon=txt("◎",54,0xff46beae,true);icon.setGravity(Gravity.CENTER);c.addView(icon);gap(c,8);c.addView(txt("正在等待连接",20,-1,true));gap(c,8);receiveStatus=txt(port>0?"接收服务已启动 · 端口 "+port:"正在启动接收服务…",13,0xffb7c7de,false);c.addView(receiveStatus);l.addView(c);gap(l,12);LinearLayout note=card();note.addView(txt("使用方法",16,-1,true));gap(note,6);note.addView(txt("发送设备打开小李全联后，会自动发现本设备。发送方点击本机名称后，本机将弹出接收确认。双方需要连接同一 Wi-Fi。",13,0xffb7c7de,false));l.addView(note);install(l);}
 @Override protected void onDestroy(){unregisterSendReceiver();if(sendProgress!=null){try{sendProgress.dismiss();}catch(Exception ignored){}sendProgress=null;}alive=false;discoveryHandler.removeCallbacks(pruneDevices);unregisterReceiveReceiver();if(udpDiscovery!=null){udpDiscovery.close();udpDiscovery=null;}try{if(nsd!=null&&discovery!=null)nsd.stopServiceDiscovery(discovery);}catch(Exception ignored){}try{if(nsd!=null&&registration!=null)nsd.unregisterService(registration);}catch(Exception ignored){}try{if(lock!=null&&lock.isHeld())lock.release();}catch(Exception ignored){}workers.shutdownNow();super.onDestroy();}
 @Override public void onBackPressed(){if(page==0)super.onBackPressed();else if(page==1)home();else if(page==2)selectedPage();else if(page==4)home();else home();}
}