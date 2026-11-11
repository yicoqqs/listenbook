package com.voicetxt.reader;

import android.app.*;
import android.net.wifi.WifiManager;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Local-network DLNA audio sender. No book files are exposed by the HTTP endpoint. */
final class DlnaCast implements AutoCloseable {
    static final class Device {final String name,type;final URL control;Device(String n,String t,URL c){name=n;type=t;control=c;}}
    final Activity activity;
    final ExecutorService discovery=Executors.newSingleThreadExecutor();
    final java.util.concurrent.atomic.AtomicInteger searchVersion=new java.util.concurrent.atomic.AtomicInteger();
    volatile boolean remotePlaying;
    volatile String playbackMessage="";
    final ExecutorService commands=Executors.newSingleThreadExecutor();
    volatile Device device;volatile boolean closed;volatile int version;
    ServerSocket server;final ExecutorService clients=Executors.newFixedThreadPool(2);
    volatile InetAddress receiverAddress;
    volatile byte[] wave;volatile String path="";String address;
    DlnaCast(Activity a){activity=a;}
    void show(){
        MainActivity reader=(MainActivity)activity;
        if(device!=null){
            String status=playbackMessage.isEmpty()?activity.getString(remotePlaying?R.string.cast_tv_playing:R.string.cast_tv_selected):playbackMessage;
            CastMenu.sheet(reader,device.name,status,(dialog,panel)->{
                CastMenu.choice(reader,panel,activity.getString(reader.playing?R.string.cast_pause:R.string.cast_play),activity.getString(R.string.cast_controls_summary),android.R.drawable.ic_media_play,()->{dialog.dismiss();if(reader.playing){reader.stop(false);if(PlaybackService.active!=null)PlaybackService.active.parkPaused();}else reader.startReading();});
                CastMenu.choice(reader,panel,activity.getString(R.string.cast_change_device),activity.getString(R.string.cast_same_wifi),R.drawable.reader_cast,()->{dialog.dismiss();search();});
                CastMenu.choice(reader,panel,activity.getString(R.string.cast_disconnect),activity.getString(R.string.cast_disconnect_summary),android.R.drawable.ic_menu_close_clear_cancel,()->{dialog.dismiss();reader.stop();close();reader.cast=null;if(reader.offline!=null)reader.offline.cast=null;});
            });return;
        }search();
    }
    void search(){
        if(closed||activity.isFinishing())return;final int search=searchVersion.incrementAndGet();
        ProgressDialog dialog=new ProgressDialog(activity);dialog.setMessage(activity.getString(R.string.cast_searching));dialog.setCancelable(true);dialog.setOnCancelListener(d->searchVersion.compareAndSet(search,search+1));dialog.show();
        discovery.execute(()->{try{List<Device> found=discover(()->!closed&&searchVersion.get()==search);activity.runOnUiThread(()->{
            if(!dialog.isShowing()||activity.isFinishing()||activity.isDestroyed()||closed||searchVersion.get()!=search)return;dialog.dismiss();
            if(found.isEmpty()){searchFailure(R.string.cast_no_devices);return;}
            MainActivity reader=(MainActivity)activity;
            CastMenu.sheet(reader,activity.getString(R.string.cast_choose),activity.getString(R.string.cast_same_wifi),(picker,panel)->{
                for(Device candidate:found)CastMenu.choice(reader,panel,candidate.name,activity.getString(R.string.cast_device_summary),R.drawable.reader_cast,()->{
                    picker.dismiss();reader.stop();resetServer();device=candidate;remotePlaying=false;playbackMessage="";
                    new AlertDialog.Builder(activity).setMessage(R.string.cast_ready).setPositiveButton(R.string.cast_play_now,(d,w)->reader.startReading()).setNegativeButton(android.R.string.cancel,null).show();
                });
                TextViewButton.add(reader,panel,activity.getString(R.string.cast_retry),()->{picker.dismiss();search();});
            });
        });}catch(Exception e){android.util.Log.w("ReaderDlna","Search failed",e);activity.runOnUiThread(()->{if(dialog.isShowing())dialog.dismiss();if(!activity.isFinishing()&&!activity.isDestroyed()&&!closed&&searchVersion.get()==search)searchFailure(R.string.cast_network_error);});}});
    }
    void searchFailure(int message){new AlertDialog.Builder(activity).setMessage(message).setPositiveButton(R.string.cast_retry,(d,w)->search()).setNegativeButton(android.R.string.cancel,null).show();}
    // Small neutral action used below the discovered-device cards.
    static final class TextViewButton {static void add(MainActivity a,android.widget.LinearLayout panel,String label,Runnable action){android.widget.TextView t=CastMenu.text(a,label,15,a.RED);t.setGravity(android.view.Gravity.CENTER);t.setMinimumHeight(CastMenu.dp(a,48));panel.addView(t,new android.widget.LinearLayout.LayoutParams(-1,-2));t.setOnClickListener(v->action.run());}}
    List<Device> discover()throws Exception{return discover(()->!closed);}
    List<Device> discover(BooleanSupplier searching)throws Exception{
        LinkedHashSet<String> locations=new LinkedHashSet<>();ArrayList<Device> result=new ArrayList<>();WifiManager wifi=(WifiManager)activity.getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);WifiManager.MulticastLock lock=wifi.createMulticastLock("reader-dlna-discovery");lock.setReferenceCounted(false);lock.acquire();
        try(DatagramSocket socket=new DatagramSocket()){
            android.net.ConnectivityManager connectivity=activity.getSystemService(android.net.ConnectivityManager.class);
            for(android.net.Network network:connectivity.getAllNetworks()){
                android.net.NetworkCapabilities caps=connectivity.getNetworkCapabilities(network);
                if(caps!=null&&caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)){network.bindSocket(socket);break;}
            }
            socket.setSoTimeout(500);long until=SystemClock.elapsedRealtime()+8000,nextSearch=0;
            while(searching.getAsBoolean()&&SystemClock.elapsedRealtime()<until){
                if(SystemClock.elapsedRealtime()>=nextSearch){
                    for(String target:new String[]{"urn:schemas-upnp-org:device:MediaRenderer:1","urn:schemas-upnp-org:service:AVTransport:1","ssdp:all"}){
                        byte[] request=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: "+target+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                        socket.send(new DatagramPacket(request,request.length,InetAddress.getByName("239.255.255.250"),1900));
                    }nextSearch=SystemClock.elapsedRealtime()+2500;
                }
                try{DatagramPacket reply=new DatagramPacket(new byte[16384],16384);socket.receive(reply);String text=new String(reply.getData(),0,reply.getLength(),StandardCharsets.UTF_8);
                    for(String line:text.split("\r?\n"))if(line.toLowerCase(Locale.ROOT).startsWith("location:")&&locations.size()<48)locations.add(line.substring(9).trim());
                }catch(SocketTimeoutException ignored){}
            }
            android.util.Log.i("ReaderDlna","Discovery descriptions="+locations.size());
        }finally{lock.release();}
        for(String location:locations){if(!searching.getAsBoolean()||result.size()>=24)break;try{URL url=new URL(location);Device candidate=describe(url,localRequest(url,"GET",null,null));if(candidate!=null){boolean duplicate=false;for(Device old:result)if(old.control.equals(candidate.control)){duplicate=true;break;}if(!duplicate)result.add(candidate);}}catch(Exception error){android.util.Log.w("ReaderDlna","Device description rejected: "+error.getClass().getSimpleName()+": "+error.getMessage());}}
        android.util.Log.i("ReaderDlna","Playable receivers="+result.size());return result;
    }
    static Device describe(URL url,byte[] description)throws Exception{
        Document doc=parse(new ByteArrayInputStream(description));String base=value(doc.getDocumentElement(),"URLBase");URL root=base.isEmpty()?url:new URL(base);
        NodeList services=doc.getElementsByTagNameNS("*","service");for(int i=0;i<services.getLength();i++){
            Element service=(Element)services.item(i);String type=value(service,"serviceType");if(!type.matches("urn:schemas-upnp-org:service:AVTransport:[1-3]"))continue;
            String endpoint=value(service,"controlURL");if(endpoint.isEmpty())continue;URL control=new URL(root,endpoint);if(!localAddress(control).equals(localAddress(url)))continue;
            Node owner=service;while(owner!=null&&!(owner instanceof Element&&"device".equals(owner.getLocalName())))owner=owner.getParentNode();
            String name=value(owner instanceof Element?(Element)owner:doc.getDocumentElement(),"friendlyName");if(name.isEmpty())name=control.getHost();return new Device(name,type,control);
        }return null;
    }
    // DLNA uses local HTTP. This transport is restricted to private IPv4 endpoints;
    // the app-wide Android cleartext policy remains disabled.
    static InetAddress localAddress(URL url)throws IOException{
        if(!url.getProtocol().equals("http")||url.getUserInfo()!=null)throw new IOException("Local HTTP only");
        InetAddress address=InetAddress.getByName(url.getHost());
        if(!(address instanceof Inet4Address)||!address.isSiteLocalAddress()||address.isLoopbackAddress())throw new IOException("Private LAN address required");
        return address;
    }
    static String httpLine(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(c==10)break;if(out.size()>8192)throw new IOException("HTTP line too long");if(c!=13)out.write(c);}if(c==-1&&out.size()==0)throw new EOFException();return out.toString("US-ASCII");}
    static byte[] localRequest(URL url,String method,String action,byte[] body)throws Exception{
        InetAddress address=localAddress(url);int port=url.getPort()<0?80:url.getPort();
        try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(address,port),4000);socket.setSoTimeout(5000);String target=url.getFile().isEmpty()?"/":url.getFile();if(target.contains("\r")||target.contains("\n"))throw new IOException("Invalid URL");
            String headers=method+" "+target+" HTTP/1.1\r\nHost: "+address.getHostAddress()+":"+port+"\r\nConnection: close\r\n";
            if(body!=null)headers+="Content-Type: text/xml; charset=utf-8\r\nSOAPAction: \""+action+"\"\r\nContent-Length: "+body.length+"\r\n";
            OutputStream out=socket.getOutputStream();out.write((headers+"\r\n").getBytes(StandardCharsets.US_ASCII));if(body!=null)out.write(body);out.flush();InputStream in=socket.getInputStream();String status=httpLine(in);if(!status.matches("HTTP/1\\.[01] 200.*"))throw new IOException("DLNA HTTP: "+status);int length=-1;boolean chunked=false;for(int n=0;;n++){String line=httpLine(in);if(line.isEmpty())break;if(n>64)throw new IOException("Too many headers");String lower=line.toLowerCase(Locale.ROOT);if(lower.startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());if(lower.startsWith("transfer-encoding:")&&lower.contains("chunked"))chunked=true;}
            ByteArrayOutputStream result=new ByteArrayOutputStream();if(chunked){while(true){String size=httpLine(in).split(";",2)[0];int count=Integer.parseInt(size.trim(),16);if(count==0)break;if(count<0||result.size()+count>1024*1024)throw new IOException("Response too large");for(int n=0;n<count;n++){int c=in.read();if(c<0)throw new EOFException();result.write(c);}if(!httpLine(in).isEmpty())throw new IOException("Invalid chunk");}return result.toByteArray();}
            if(length>=0){if(length>1024*1024)throw new IOException("Response too large");for(int n=0;n<length;n++){int c=in.read();if(c<0)throw new EOFException();result.write(c);}return result.toByteArray();}return read(in,1024*1024);
        }
    }
    static Document parse(InputStream in)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setExpandEntityReferences(false);return f.newDocumentBuilder().parse(in);}
    static String value(Element e,String name){NodeList nodes=e.getElementsByTagNameNS("*",name);return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();}
    static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    synchronized String soap(Device d,String action,String args)throws Exception{
        String envelope="<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:"+action+" xmlns:u=\""+d.type+"\"><InstanceID>0</InstanceID>"+args+"</u:"+action+"></s:Body></s:Envelope>";
        return new String(localRequest(d.control,"POST",d.type+"#"+action,envelope.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8);
    }

    static byte[] read(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Response too large");out.write(b,0,n);}return out.toByteArray();}
    void startServer(Device d)throws Exception{if(server!=null)return;try(DatagramSocket route=new DatagramSocket()){receiverAddress=localAddress(d.control);route.connect(receiverAddress,d.control.getPort()>0?d.control.getPort():80);address=route.getLocalAddress().getHostAddress();}server=new ServerSocket(0,4,InetAddress.getByName(address));ServerSocket listening=server;Thread thread=new Thread(()->{while(!closed&&!listening.isClosed()){try{Socket socket=listening.accept();clients.execute(()->serve(socket));}catch(Exception e){if(closed||listening.isClosed())break;}}},"dlna-audio-http");thread.setDaemon(true);thread.start();}
    void serve(Socket socket){try(Socket s=socket){if(!s.getInetAddress().equals(receiverAddress))return;s.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));String line=in.readLine();if(line==null)return;String[] req=line.split(" ");String range="";int count=0;while((line=in.readLine())!=null&&!line.isEmpty()){if(++count>64)return;if(line.toLowerCase(Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();}byte[] data=wave;OutputStream out=s.getOutputStream();if(req.length<2||(!req[0].equals("GET")&&!req[0].equals("HEAD"))||!req[1].equals(path)||data==null){out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));return;}int start=0,end=data.length-1;boolean partial=!range.isEmpty();if(partial){try{String[] parts=range.replaceFirst("^bytes=","").split("-",-1);if(parts.length!=2)throw new Exception();if(parts[0].isEmpty())start=Math.max(0,data.length-Integer.parseInt(parts[1]));else{start=Integer.parseInt(parts[0]);if(!parts[1].isEmpty())end=Math.min(end,Integer.parseInt(parts[1]));}if(start<0||start>end)throw new Exception();}catch(Exception e){out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */"+data.length+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));return;}}String header="HTTP/1.1 "+(partial?"206 Partial Content":"200 OK")+"\r\nContent-Type: audio/wav\r\nAccept-Ranges: bytes\r\ntransferMode.dlna.org: Streaming\r\nContent-Length: "+(end-start+1)+"\r\n"+(partial?"Content-Range: bytes "+start+"-"+end+"/"+data.length+"\r\n":"")+"Connection: close\r\n\r\n";out.write(header.getBytes(StandardCharsets.US_ASCII));if(req[0].equals("GET"))out.write(data,start,end-start+1);out.flush();}catch(Exception ignored){}}
    static byte[] wav(short[] pcm,int rate){ByteBuffer b=ByteBuffer.allocate(44+pcm.length*2).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+pcm.length*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(rate).putInt(rate*2).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length*2);for(short s:pcm)b.putShort(s);return b.array();}
    void play(short[] pcm,int rate,BooleanSupplier current,Runnable started)throws Exception{
        WifiManager wifi=(WifiManager)activity.getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
        WifiManager.WifiLock lock=wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"reader-dlna-audio");lock.setReferenceCounted(false);
        try(CpuWork processing=new CpuWork(activity)){lock.acquire();try{playClip(pcm,rate,current,started);}finally{if(lock.isHeld())lock.release();}}
    }
    void playClip(short[] pcm,int rate,BooleanSupplier current,Runnable started)throws Exception{
        Device d=device;if(d==null||closed)throw new IOException("DLNA disconnected");playbackMessage=activity.getString(R.string.cast_tv_buffering);int token=++version;startServer(d);if(!current.getAsBoolean()||version!=token)return;wave=wav(pcm,rate);path="/"+UUID.randomUUID()+".wav";String uri="http://"+address+":"+server.getLocalPort()+path;
        String metadata="<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"1\" parentID=\"0\" restricted=\"1\"><dc:title>"+xml(activity.getString(R.string.app_name))+"</dc:title><upnp:class>object.item.audioItem</upnp:class><res protocolInfo=\"http-get:*:audio/wav:*\">"+xml(uri)+"</res></item></DIDL-Lite>";
        soap(d,"SetAVTransportURI","<CurrentURI>"+xml(uri)+"</CurrentURI><CurrentURIMetaData>"+xml(metadata)+"</CurrentURIMetaData>");if(!current.getAsBoolean()||version!=token)return;soap(d,"Play","<Speed>1</Speed>");boolean seen=false;long began=SystemClock.elapsedRealtime(),limit=began+Math.max(30000,(long)pcm.length*1000/rate+30000);
        while(current.getAsBoolean()&&version==token&&!closed){String reply=soap(d,"GetTransportInfo","");String state=value(parse(new ByteArrayInputStream(reply.getBytes(StandardCharsets.UTF_8))).getDocumentElement(),"CurrentTransportState");if(state.equals("PLAYING")&&!seen){seen=true;remotePlaying=true;playbackMessage="";started.run();}if(seen&&state.equals("STOPPED")){remotePlaying=false;return;}if(!seen&&state.equals("STOPPED")&&SystemClock.elapsedRealtime()-began>=1000){
                String positionReply=soap(d,"GetPositionInfo","");Element position=parse(new ByteArrayInputStream(positionReply.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
                if(playedToEnd(value(position,"RelTime"),value(position,"TrackDuration"),(long)pcm.length*1000/rate)){started.run();remotePlaying=false;return;}
            }if(state.equals("PAUSED_PLAYBACK"))throw new IOException("Playback paused on TV");if(SystemClock.elapsedRealtime()>limit||(!seen&&SystemClock.elapsedRealtime()-began>20000))throw new IOException("DLNA playback timeout");Thread.sleep(250);}
    }
    static long timeMillis(String value){
        try{String[] parts=value.split(":");if(parts.length!=3)return -1;long hours=Long.parseLong(parts[0]),minutes=Long.parseLong(parts[1]);double seconds=Double.parseDouble(parts[2]);if(hours<0||minutes<0||minutes>=60||seconds<0||seconds>=60||!Double.isFinite(seconds))return -1;return Math.addExact(Math.multiplyExact(hours,3600000),minutes*60000+(long)(seconds*1000));}catch(Exception e){return -1;}
    }
    static boolean playedToEnd(String elapsed,String duration,long expected){long at=timeMillis(elapsed),total=timeMillis(duration);return at>0&&total>0&&at>=total&&Math.abs(total-expected)<=1000;}
    void stop(){remotePlaying=false;playbackMessage="";int stopped=++version;Device d=device;if(d!=null&&!commands.isShutdown())commands.execute(()->{try{if(version==stopped)soap(d,"Stop","");}catch(Exception ignored){}if(version==stopped)wave=null;});}
    void resetServer(){wave=null;path="";if(server!=null)try{server.close();}catch(IOException ignored){}server=null;receiverAddress=null;}
    void playbackFailed(Throwable error){remotePlaying=false;playbackMessage=activity.getString(R.string.cast_playback_failed);android.util.Log.w("ReaderDlna","Playback failed",error);}
    @Override public void close(){stop();closed=true;searchVersion.incrementAndGet();device=null;resetServer();clients.shutdownNow();discovery.shutdownNow();commands.shutdown();}
}
