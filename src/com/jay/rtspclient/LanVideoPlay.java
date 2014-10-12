package com.jay.rtspclient;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import org.sipdroid.net.RtpPacket;
import org.sipdroid.net.RtpSocket;

import android.util.Base64;
import android.util.Log;

public  class LanVideoPlay extends Thread{
	private volatile Thread runner;
	protected DatagramSocket udp_socket;
	protected RtpPacket rtp_packet;
	protected RtpSocket rtp_socket;
	protected H264Decoder decoder;
	protected String str_SPS;
	protected String str_PPS;
	protected int width;
	protected int height;
	protected int listenport;
	protected final int MTU = 1500;
	protected final int GO_TIMEOUT=1000;
	protected int codectype=96;
	protected byte[] m_out_bytes;
	protected static int m;
	protected int vm=1;
	private int Naloffset=4 ;  //加上起始玛4个
	private byte [] NalBuf = new byte[40980]; // 40k
	private byte [] SockBuf = new byte[2048];
	private byte [] paraBuf = new byte[100];   //store sps pps
	private int ppsLen=0,spsLen=0;
	private byte [] StartCode = {0x00,0x00,0x00,0x01};
	//speech preprocessor
	protected int gseq=0,currentseq=0,getseq,expseq,gap;
	public static float good, late, lost, loss, loss2;
	
	//used for echo calc
	
    public LanVideoPlay(DatagramSocket socket,int width,int height,String SPS,String PPS){
    	try{
    		this.width = width;
    		this.height = height;
    		this.str_SPS=SPS;
    		this.str_PPS=PPS;
    		byte[] bSPS = Base64.decode(str_SPS, Base64.DEFAULT);
    		byte[] bPPS = Base64.decode(str_PPS, Base64.DEFAULT);
    		ppsLen=bPPS.length;
    		spsLen=bSPS.length;
    		System.arraycopy(StartCode, 0, paraBuf, 0, 4);
    		System.arraycopy(bSPS, 0,paraBuf, 4,spsLen);
    		System.arraycopy(StartCode, 0, paraBuf, spsLen+4, 4);
    		System.arraycopy(bPPS, 0, paraBuf,spsLen+8, ppsLen);
    		System.arraycopy(StartCode, 0, NalBuf, 0, 4);
    		udp_socket = socket;
    	}
    	catch (Exception e) {
    		e.printStackTrace();
		}
    }
    
    public void startThread(){
		if(runner == null){
		    runner = new Thread(this);
		    runner.start();
		  }
	}
	
	public void stopThread(){
		 if(runner != null){
			 free();
			    Thread moribund = runner;
			    runner = null;
			    moribund.interrupt();
		  }
	}

	public void run(){
		byte[] buffer = new byte[MTU];
		rtp_packet = new RtpPacket(buffer, MTU);
		rtp_packet.setPayloadType(codectype);
		try {
			rtp_socket = new RtpSocket(this.udp_socket);
		} catch (Exception e) {
			e.printStackTrace();
		}
		//此处可以初始化解码器
    	decoder = new H264Decoder(width,height);
    	decoder.startThread();
    	while(!decoder.isIdle());
    	decoder.putData(paraBuf,spsLen+ppsLen+8);   //加上TYPE和起始码
    	
//    	try {
//    		rtp_socket.receive(rtp_packet);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
    	//System.gc();
    	//empty();

    	
    	//开始接受数据包
    	while(Thread.currentThread() == runner){
			try {
				rtp_socket.receive(rtp_packet);
			} catch (IOException e) {
				e.printStackTrace();
			}
    		
    		 gseq = rtp_packet.getSequenceNumber();
			 if (currentseq == gseq) {
				 m++;
				 continue;
			 }
			 lostandgood();
		//	 Log.d("LanVideoPlay","lost:"+ lost);
			 //此处向解码器投放数据包
			 MergeNalUnit(buffer[1],rtp_packet);
    	}
    }

	void  MergeNalUnit(byte buffer,RtpPacket rtpPacket)
	{
		byte[] payload = rtpPacket.getPayload().clone();
		
		if((payload[0]&0x1f)==28)  //FU-A
		{
			if((payload[1]&0xC0)==0x80) //start
			{
				byte unitHeader = (byte) ((payload[0]&0x60)|(payload[1]&0x1f));
				if((payload[1]&0x1f)==5)   //I
				{
					Naloffset=0;
					System.arraycopy(paraBuf, 0, NalBuf, 0,8+ppsLen+spsLen );
					Naloffset+=8+ppsLen+spsLen;
					System.arraycopy(StartCode, 0, NalBuf, Naloffset, 4);
					Naloffset+=4;
					NalBuf[Naloffset]=unitHeader;
					Naloffset+=1;
				}
				else if((payload[1]&0x1f)==1)  
				{
					NalBuf[Naloffset]=unitHeader;
					Naloffset+=1; //设置SLICE HEADER
				}
			}
			System.arraycopy(payload,2,
					NalBuf,Naloffset, rtpPacket.getPayloadLength()-2);
			Naloffset+=rtpPacket.getPayloadLength()-2;
			if((payload[1]&0xC0)==0x40) //stop
			{
				if(decoder.isIdle())
					decoder.putData(NalBuf,Naloffset);
					Naloffset=4;
			}
		}
		else if((payload[0]&0x1f)<24)   //single nal unit
		{
			if((payload[1]&0x1f)==5)   //I
			{
				Naloffset=0;
				System.arraycopy(paraBuf, 0, NalBuf, 0,8+ppsLen+spsLen );
				Naloffset+=8+ppsLen+spsLen;
				System.arraycopy(StartCode, 0, NalBuf, Naloffset, 4);
				Naloffset+=4;
			}
			System.arraycopy(payload,0,
					NalBuf,Naloffset, rtpPacket.getPayloadLength());
			Naloffset+=rtpPacket.getPayloadLength();
			if(decoder.isIdle())
			decoder.putData(NalBuf,Naloffset);
			Naloffset=4;
		}
		
	}
	void empty() {
		try {
			rtp_socket.getDatagramSocket().setSoTimeout(1);
			for (;;)
				rtp_socket.receive(rtp_packet);
		} catch (IOException e) {
		}
		try {
			rtp_socket.getDatagramSocket().setSoTimeout(GO_TIMEOUT);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		currentseq = 0;
	}
	
	
	void lostandgood(){
		if (currentseq != 0) {
			 getseq = gseq&0xff;
			 expseq = ++currentseq&0xff;
			 if (m == LanVideoPlay.m) vm = m;
			 gap = (getseq - expseq) & 0xff;
			 if (gap > 0) {
				 if (gap > 100) gap = 1;
				 loss += gap;
				 lost += gap;
				 good += gap - 1;
				 loss2++;
			 } else {
				 if (m < vm) {
					 loss++;
					 loss2++;
				 }
			 }
			 good++;
			 if (good > 110) {
				 good *= 0.99;
				 lost *= 0.99;
				 loss *= 0.99;
				 loss2 *= 0.99;
				 late *= 0.99;
			 }
		 }
		 m = 1;
		 currentseq = gseq;
	}
	
	public H264Decoder getDecoder()
	{
		return decoder;
	}
	
	public boolean isDecoder()
	{
		 //此处获取解码器解码的图像数据，绘图
		if(decoder!=null)
			return decoder.isGetData();
		else return false;
		
	}
	
    public void free(){
    	//此处释放资源
//    	m_out_trk.stop();

    	rtp_socket.close();
    	decoder.stopThread();
	}	
	
    
    
}