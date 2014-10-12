package com.jay.rtspclient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.R.integer;
import android.R.string;
import android.util.Base64;
import android.util.Log;
import br.com.voicetechnology.rtspclient.RTSPClient;
import br.com.voicetechnology.rtspclient.concepts.Client;
import br.com.voicetechnology.rtspclient.concepts.ClientListener;
import br.com.voicetechnology.rtspclient.concepts.Header;
import br.com.voicetechnology.rtspclient.concepts.Request;
import br.com.voicetechnology.rtspclient.concepts.Response;
import br.com.voicetechnology.rtspclient.headers.TransportHeader;
import br.com.voicetechnology.rtspclient.transport.PlainTCP;


public class Rtspclient implements ClientListener
{
	private String request_uri;
	private String controlURI;
	
	private RTSPClient client;
	private LanVideoPlay mLanVideoPlay;
	
	private final List<String> resourceList;
	
	private int port=9009;
	private int server_port;

	private final int width = 320;
	private final int height = 240;
	private String str_SPS;
	private String str_PPS;
	private DatagramSocket udp_socket;
	
	private boolean fstream=false;
	
	public  Rtspclient() throws Exception
	{
		client = new RTSPClient();
		client.setTransport(new PlainTCP());
		client.setClientListener(this);
		udp_socket = new DatagramSocket(port);
		Log.i("localport",udp_socket.getLocalPort()+"");
		resourceList = Collections.synchronizedList(new LinkedList<String>());
	}

	public void do_option(String uri)
	{
		request_uri=uri ; 
		try {
			client.options(request_uri, new URI(request_uri));
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void do_describe(String uri)
	{
		try {
			client.describe(new URI(uri));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void do_play()
	{
		try {
			client.play();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void do_pause()
	{
		client.teardown();
	}
	@Override
	public void requestFailed(Client client, Request request, Throwable cause)
	{
		System.out.println("Request failed \n" + request);
	}

	@Override
	public void response(Client client, Request request, Response response)
	{
		try
		{
			System.out.println("Got response: \n" + response);
			System.out.println("for the request: \n" + request);
			if(response.getStatusCode() == 200)
			{
				switch(request.getMethod())
				{
				case OPTIONS:
					System.out.println("option");
					do_describe(request_uri);
					break;
				case DESCRIBE:
					System.out.println(resourceList);
					controlURI = request.getURI();
//					if(resourceList.get(0).equals("*"))
//					{
//						controlURI = request.getURI();
//						resourceList.remove(0);
//					}
					if(resourceList.size() > 0)
						client.setup(new URI(controlURI), nextPort(), resourceList
								.remove(0));
					else
						client.setup(new URI(controlURI), nextPort());
					break;

				case SETUP:
					//sets up next session or ends everything.
					if(resourceList.size() > 0)
						client.setup(new URI(controlURI), nextPort(), resourceList
								.remove(0));
					
					else
						sessionSet(client);
					String sport = response.getHeader("Transport").getRawValue();
					String temp = "server_port=";
					sport = sport.substring(sport.indexOf(temp));
					sport = sport.substring(temp.length(), sport.indexOf("-"));
					server_port = Integer.parseInt(sport);
					
					break;
				case PLAY:
					new Thread(new Runnable() {
						
						@Override
						public void run() {
							// TODO Auto-generated method stub
							do_stream(port);
						}
					}).start();
				break;
				case TEARDOWN:
					mLanVideoPlay.stopThread();
					break;
				}
			} 
			else
				client.teardown();
		} catch(Throwable t)
		{
			generalError(client, t);
		}
	}

	@Override
	public void generalError(Client client, Throwable error)
	{
		error.printStackTrace();
	}

	@Override
	public void mediaDescriptor(Client client, String descriptor)
	{
		// searches for control: session and media arguments.
		final String target = "trackID=";
		System.out.println("Session Descriptor\n" + descriptor);
		parseParameterSet(descriptor);
		int position = -1;
		while((position = descriptor.indexOf(target)) > -1)
		{
			descriptor = descriptor.substring(position+target.length());  //此处因为VLC而改，VLC会在control段附带URI
			resourceList.add(target+descriptor.substring(0, descriptor.indexOf('\r')));
		}
	}
	
	protected void sessionSet(Client client) throws IOException
	{
		client.play();
	}

	private int nextPort()
	{
		return (port += 2) - 2;
	}
	
	private void do_stream(int serverport)
	{
			mLanVideoPlay = new LanVideoPlay(udp_socket, width, height,str_SPS,str_PPS);
			mLanVideoPlay.startThread();
			Log.d("Rtspclient","Start LanVideoPlay");
	}
	
	private void parseParameterSet(String SDP)
	{
		String paraStr ="sprop-parameter-sets=";
		int findSpsPos = SDP.indexOf(paraStr);
		String SDP_SUB = SDP.substring(findSpsPos+paraStr.length());
		str_SPS=SDP_SUB.substring(0,SDP_SUB.indexOf(","));
		str_PPS=SDP_SUB.substring(SDP_SUB.indexOf(",")+1,SDP_SUB.indexOf(";"));
	}
	
	public LanVideoPlay getLVP()
	{
		return mLanVideoPlay;
	}
	
	public boolean isStream()
	{
		if(mLanVideoPlay!=null)
		return mLanVideoPlay.isDecoder();
		else return false;
	}
	
}