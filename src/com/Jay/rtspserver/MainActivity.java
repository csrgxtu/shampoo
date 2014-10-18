package com.Jay.rtspserver;



import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import br.com.voicetechnology.rtspclient.RTSPClient;

import com.hnebang.android.ebplayerlib.IEBPlayer;
import com.hnebang.android.ebplayerlib.PlayerCallback;
import com.hnebang.android.ebplayerlib.SoftDecoderPlayer;
import com.jay.rtspclient.Rtspclient;
import com.jay.server.RtspServer;
import com.jay.server.Session;
import com.jay.video.VideoQuality;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.text.format.Formatter;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener, PlayerCallback{


	private SurfaceView camera;
  private SurfaceHolder holder;
	private VideoQuality defaultVideoQuality = new VideoQuality();
	private SharedPreferences settings;
	private PowerManager.WakeLock wl;
	private RtspServer rtspServer;
  private TextView console, ip;
    
    //client
  private Rtspclient rtspClient;
  private SurfaceView sfv;
	private SurfaceHolder sfh;
	private Bitmap bitmap;
	private Canvas canvas ;
	private int width=320;
	private int height=240;
	public static int Surface_width;
	public static int Surface_height;
	private Draw_Image mDraw_Image;
	private String s_uri="rtsp://192.168.0.5:9113/";
	private int rtsp_port=9113;
	
	private IEBPlayer player;
	
	private TextView ONLINE_LST;

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      requestWindowFeature(Window.FEATURE_NO_TITLE);
      setContentView(R.layout.activity_main);
        
      console = (TextView) findViewById(R.id.console);
      ip = (TextView) findViewById(R.id.ip);
      camera = (SurfaceView)findViewById(R.id.surface_local);
      camera.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
      holder=camera.getHolder();
      initParameters();
      initSession();
        
      rtspServer = new RtspServer(rtsp_port, handler);
        
      try {
        rtspClient= new Rtspclient();
      } catch (Exception e) {
        e.printStackTrace();
      }
       //initDrawPannel();
       
      sfv = (SurfaceView)this.findViewById(R.id.surface_remote);
      
      ONLINE_LST = (TextView)this.findViewById(R.id.online_list);
      new Thread(new ClientThread()).start();
    }
    
    class ClientThread implements Runnable {
      private int SERVER_PORT = 9113;
      //private String[] Servers = new String[2];
      
      private WifiManager wifiMgr = (WifiManager) getSystemService(WIFI_SERVICE);
      private WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
      private int ip = wifiInfo.getIpAddress();
      private String localIP = Formatter.formatIpAddress(ip);
      
      //private String ipPrefix = localIP.substring(0, localIP.lastIndexOf(":") + 1);
      //private String ipPrefix = "192.168.1.";
      
      @Override
      public void run() {
        /*Log.i("localIP", localIP);
        Log.i("localIP", String.valueOf(localIP.lastIndexOf(".")));
        Log.i("localIP", localIP.substring(0, 10));*/
        /*Servers[0] = "192.168.1.100";
        Servers[1] = "192.168.1.101";*/
        String ipPrefix = localIP.substring(0, localIP.lastIndexOf(".") + 1);
        
        for (int i = 125; i < 127; i++) {
          String server = ipPrefix + String.valueOf(i);
          try {
            InetAddress serverAddr = InetAddress.getByName(server);
  
            Socket socket = new Socket(serverAddr, SERVER_PORT);
            Log.i("ClientThread", "Connected");
            socket.close();
            /*String tmp = (String)ONLINE_LST.getText();
            tmp += "\n" + server + ":UP";
            ONLINE_LST.setText(tmp);*/
            handler.sendMessage(handler.obtainMessage(110, server));
          } catch (UnknownHostException e1) {
            Log.e("ClientThread", "Unknow host");
            Log.i("ClientThread", "Not Connected");
            Log.i("ConcateIP", ipPrefix);
            Log.i("ConcateIP", server);
            /*String tmp = (String)ONLINE_LST.getText();
            tmp += "\n" + server + ":OFF";
            ONLINE_LST.setText(tmp);*/
          } catch (IOException e1) {
            //e1.printStackTrace();
            Log.e("ClientThread", "IOException");
            Log.i("ClientThread", "Not Connected");
            /*String tmp = (String)ONLINE_LST.getText();
            tmp += "\n" + server + ":OFF";
            ONLINE_LST.setText(tmp);*/
          }
        }

      }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    
    //Step1  init parameters from sharepreference
   void initParameters()
   {
	   settings = PreferenceManager.getDefaultSharedPreferences(this);
       defaultVideoQuality.resX = settings.getInt("video_resX", 640);
       defaultVideoQuality.resY = settings.getInt("video_resY", 480);
       defaultVideoQuality.frameRate = Integer.parseInt(settings.getString("video_framerate", "15"));
       defaultVideoQuality.bitRate = Integer.parseInt(settings.getString("video_bitrate", "500"))*1000; // 500 kb/s
       s_uri = settings.getString("video_dst", "rtsp://192.168.0.2:8086/");
       settings.registerOnSharedPreferenceChangeListener(this);
       
       PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
       wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "net.majorkernelpanic.spydroid.wakelock");
   
   }

   //step 2
   void initSession()
   {
	    Session.setSurfaceHolder(holder);
        Session.setDefaultVideoQuality(defaultVideoQuality);
//        Session.setDefaultAudioEncoder(settings.getBoolean("stream_audio", true)?Integer.parseInt(settings.getString("audio_encoder", "1")):0);
        Session.setDefaultVideoEncoder(settings.getBoolean("stream_video", true)?Integer.parseInt(settings.getString("video_encoder", "1")):0);
      
   }

   
   //step 3
@Override
public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
	if (key.equals("video_resX")) {
		defaultVideoQuality.resX = sharedPreferences.getInt("video_resX", 640);
		Session.setDefaultVideoQuality(defaultVideoQuality);
	}
	else if (key.equals("video_resY"))  {
		defaultVideoQuality.resY = sharedPreferences.getInt("video_resY", 480);
		Session.setDefaultVideoQuality(defaultVideoQuality);
	}
	else if (key.equals("video_framerate")) {
		defaultVideoQuality.frameRate = Integer.parseInt(sharedPreferences.getString("video_framerate", "15"));
		Session.setDefaultVideoQuality(defaultVideoQuality);
	}
	else if (key.equals("video_bitrate")) {
		defaultVideoQuality.bitRate = Integer.parseInt(sharedPreferences.getString("video_bitrate", "500"))*1000;
		Session.setDefaultVideoQuality(defaultVideoQuality);
	}
	else if (key.equals("stream_audio") || key.equals("audio_encoder")) { 
		Session.setDefaultAudioEncoder(sharedPreferences.getBoolean("stream_audio", false)?Integer.parseInt(sharedPreferences.getString("audio_encoder", "1")):0);
	}
	else if (key.equals("stream_video") || key.equals("video_encoder")) {
		Session.setDefaultVideoEncoder(sharedPreferences.getBoolean("stream_video", true)?Integer.parseInt(sharedPreferences.getString("video_encoder", "1")):0);
	}
	else if (key.equals("enable_http")) {
		
	}
	else if (key.equals("enable_rtsp")) {
		if (sharedPreferences.getBoolean("enable_rtsp", true)) {
			rtspServer =  new RtspServer(rtsp_port, handler);
		} else {
			if (rtspServer != null) rtspServer = null;
		}
	}	
}

//step 4  handler
// The Handler that gets information back from the RtspServer
private final Handler handler = new Handler() {
	
	public void handleMessage(Message msg) {
		
		switch (msg.what) {
			
		case RtspServer.MESSAGE_LOG:
			log((String)msg.obj);
			break;

		case RtspServer.MESSAGE_ERROR:
			log((String)msg.obj);
			break;
			
		case Session.MESSAGE_START:
			// Sent when streaming starts
//			logo.setAlpha(100);
			camera.setBackgroundDrawable(null);
			break;
			
		case Session.MESSAGE_STOP:
			// Sent when streaming ends
			camera.setBackgroundDrawable(null);
//			logo.setAlpha(255);
			break;

		case Session.MESSAGE_ERROR:
			log((String)msg.obj);
			break;
		
		case 110:
		  Log.i("Handler", (String)msg.obj);
		  String tmp = (String)ONLINE_LST.getText();
		  tmp += "\n" + (String)msg.obj + ":ON";
		  ONLINE_LST.setText(tmp);
		  break;

		}
	}
	
};

//step 5 log
public void log(String s) {
	String t = console.getText().toString();
	if (t.split("\n").length>8) {
		console.setText(t.substring(t.indexOf("\n")+1, t.length()));
	}
	console.append(Html.fromHtml(s+"<br />"));
}
private void displayIpAddress(WifiInfo wifiInfo) {
	if (wifiInfo!=null && wifiInfo.getNetworkId()>-1) {
    	int i = wifiInfo.getIpAddress();
    	ip.setText("rtsp://");
    	ip.append(String.format("%d.%d.%d.%d", i & 0xff, i >> 8 & 0xff,i >> 16 & 0xff,i >> 24 & 0xff));
    	ip.append(":8086/");
	} else {
		ip.setText("Wifi should be enabled !");
	}
}
//step 6 options

@Override
public boolean onOptionsItemSelected(MenuItem item) {
	Intent intent;
	
    switch (item.getItemId()) {
    case R.id.option:
        // Starts QualityListActivity where user can change the streaming quality
        intent = new Intent(this.getBaseContext(),OptionsActivity.class);
        startActivityForResult(intent, 0);
        return true;
    case R.id.do_play:
    	/*
    	new Thread(new Runnable() 
    	{
			public void run() {
				rtspClient.do_option(s_uri);
//				rtspClient.do_describe(s_uri);
//				rtspClient.do_play();
				mDraw_Image.startThread();
			}
		}
    	).start();*/
    	
		if(player != null){
			player.release();
		}
		player = new SoftDecoderPlayer(true, true);
		player.setPlayerCallback(MainActivity.this);
		player.prepare(MainActivity.this, sfv);
		player.playAsync("rtsp://192.168.1.149:9113");
    	
    	return true;
    case R.id.do_stop:
    	new Thread(new Runnable() 
    	{
			public void run() {
				rtspClient.do_pause();
				mDraw_Image.stopThread();
			}
		}
    	).start();
    	return true;
    default:
        return super.onOptionsItemSelected(item);
    }
}

//step 7 do start
public void onStart() {
	super.onStart();
	// Lock screen
	wl.acquire();
}

public void onStop() {
	super.onStop();
	wl.release();
}

public void onResume() {
	super.onResume();
	
	// Determines if user is connected to a wireless network & displays ip 
	WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
	WifiInfo wifiInfo = wifiManager.getConnectionInfo();
	displayIpAddress(wifiInfo);
	
	startServers();
	
	//registerReceiver(wifiStateReceiver,new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
	
}

public void onPause() {
	super.onPause();
	stopServers();
//	unregisterReceiver(wifiStateReceiver);
}

private void stopServers() {
	if (rtspServer != null) rtspServer.stop();
}

private void startServers() {
	if (rtspServer != null) {
		try {
			rtspServer.start();
		} catch (IOException e) {
			log("RtspServer could not be started : "+e.getMessage());
		}
	}
//	if (httpServe!= null) {
//		try {
//			httpServer.start();
//		} catch (IOException e) {
//			log("HttpServer could not be started : "+e.getMessage());
//		}
//	}
}

private void initDrawPannel()
{
	 sfv = (SurfaceView)this.findViewById(R.id.surface_remote);
     sfh = sfv.getHolder();
     sfh.addCallback(new SurfaceCallBack());
     bitmap=Bitmap.createBitmap(width, height, Config.RGB_565);
     mDraw_Image = new Draw_Image();
}

class SurfaceCallBack implements SurfaceHolder.Callback {

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int format, int width,
			int height) {
		MainActivity.this.Surface_width = width;
		MainActivity.this.Surface_height = height;
		
	}
	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
	}
	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
	}
}

class Draw_Image extends Thread
{
	private volatile Thread runner;
	public void startThread(){
		if(runner == null){
		    runner = new Thread(this);
		    runner.start();
		  }
	}
	
	public void stopThread(){
		 if(runner != null){
			    Thread moribund = runner;
			    runner = null;
			    moribund.interrupt();
		  }
	}
	
	public void run() {
        while (Thread.currentThread() == runner) {
        	if(rtspClient.isStream()){
        		if(rtspClient.getLVP().getDecoder().isGetData())
        		{
	            	ByteBuffer buffer = ByteBuffer.wrap(rtspClient.getLVP().getDecoder().getData());
	                bitmap.copyPixelsFromBuffer(buffer);
	                Bitmap bmp = Bitmap.createScaledBitmap(bitmap, Surface_width, Surface_height, false);
	                canvas = sfh.lockCanvas(new Rect(0,0,Surface_width,Surface_height));
	                canvas.drawBitmap(bmp, 0,0,new Paint());
	                sfh.unlockCanvasAndPost(canvas);
        		}
        	}
    	}
	};
}

@Override
public void onError(final String arg0) {
	handler.post(new Runnable(){

		@Override
		public void run() {
			log(arg0);
			
		}	
		
	});
	
	
}

@Override
public void onPlayerProgressUpdate(long arg0, long arg1) {
	
}
    
}
