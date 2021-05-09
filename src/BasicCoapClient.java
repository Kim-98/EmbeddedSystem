package org.ws4d.coap.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.server.ServerCloneException;
import java.util.Random;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.ws4d.coap.Constants;
import org.ws4d.coap.connection.BasicCoapChannelManager;
import org.ws4d.coap.connection.BasicCoapClientChannel;
import org.ws4d.coap.interfaces.CoapChannelManager;
import org.ws4d.coap.interfaces.CoapClient;
import org.ws4d.coap.interfaces.CoapClientChannel;
import org.ws4d.coap.interfaces.CoapRequest;
import org.ws4d.coap.interfaces.CoapResponse;
import org.ws4d.coap.messages.CoapBlockOption;
import org.ws4d.coap.messages.CoapBlockOption.CoapBlockSize;


import org.ws4d.coap.messages.CoapMediaType;
import org.ws4d.coap.messages.CoapRequestCode;
import org.ws4d.coap.server.CoapSampleResourceServer;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

/**
 * @author	Christian Lerche <christian.lerche@uni-rostock.de>
 * 			Bjoern Konieczek <bjoern.konieczek@uni-rostock.de>
 */
public class BasicCoapClient extends JFrame implements CoapClient, ActionListener
{
	private String SERVER_ADDRESS;
	private int PORT; 

	static int counter = 0;
	static int movement = 0;
	static int movement_cnt = 1;
	static int lock = 0;
		
	private CoapChannelManager channelManager = null;
	private BasicCoapClientChannel clientChannel = null;
	private Random tokenGen = null;


	//UI
	private JTextField setField;
	private JButton postBtn, getBtn, off;
	private JTextArea area;

	public double setTem;
	public double temperature;

	public BasicCoapClient(String server_addr, int port ){
		super();
		this.SERVER_ADDRESS = server_addr;
		this.PORT = port;
		this.channelManager = BasicCoapChannelManager.getInstance();
		this.tokenGen = new Random();
	}
	public boolean connect(){
		try {
			clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
		} catch( UnknownHostException e ){
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public boolean connect( String server_addr, int port ){
		this.SERVER_ADDRESS = server_addr;
		this.PORT = port;
		return this.connect();
	}


	public CoapRequest createRequest( boolean reliable, CoapRequestCode reqCode ) {
		return clientChannel.createRequest( reliable, reqCode );
	}

	public byte[] generateRequestToken(int tokenLength ){
		byte[] token = new byte[tokenLength];
		tokenGen.nextBytes(token);
		return token;
	}

	public void onConnectionFailed(CoapClientChannel channel, boolean notReachable, boolean resetByServer) {
		System.out.println("Connection Failed");
	}


	public void onResponse(CoapClientChannel channel, CoapResponse response) {
		System.out.println("Received response");
		if(response.getPayload() != null)
		{
			String responseData = new String(response.getPayload()); 
			String value = responseData.substring(0,1);
			String S_responseData = responseData.substring(1, 6);
			
			if(responseData.matches(".*well-known/core.*"))
			{
				String[] tempData = responseData.split(",");
				for(String data : tempData)
				{
					if(!data.equals("</.well-known/core>"))
					{
						area.append(data+"\n");
					}
				}
			}else
			{
				if(!responseData.equals("success"))
				{
					lock++;
					if(lock > 10) {
						area.append("현재 온도는 [" + S_responseData +"˚C] 입니다.\n");
						if(movement < 3) {
							SetTemperature(0);
						}
					} else {
						area.append("현재 온도는 [" + S_responseData +"˚C] 입니다.(" + lock + ")\n");						
					}
					GetTemperature(Double.parseDouble(S_responseData));
					MovementNum(Integer.parseInt(value));
					if(movement == movement_cnt) {
						if(movement > 3) {
							
						} else {
							movement_cnt++;
							area.append("움직임 감지 [ " + movement + " / 3 ]\n");
						}
					}
				}
					
			}
		}else
		{
			System.out.println("response payload null");
			area.append("Response:\n");
			area.append("response payload null\n");
			area.append("--------------------------------\n");
		}
	}

	@Override
	public void onMCResponse(CoapClientChannel channel, CoapResponse response, InetAddress srcAddress, int srcPort) {
		System.out.println("MCReceived response");
	}

	public void resourceDiscoveryExample()
	{
		try {
			clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
			CoapRequest coapRequest = clientChannel.createRequest(true, CoapRequestCode.GET);
			byte [] token = generateRequestToken(3);
			coapRequest.setUriPath("/.well-known/core");
			coapRequest.setToken(token);
			clientChannel.sendMessage(coapRequest);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	public void postExample()
	{
		try {
			clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
			CoapRequest coapRequest = clientChannel.createRequest(true, CoapRequestCode.POST);
			byte [] token = generateRequestToken(3);
			coapRequest.setUriPath("/led/red");
			coapRequest.setToken(token);
			coapRequest.setPayload("ON".getBytes());
			clientChannel.sendMessage(coapRequest);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	public void getExample()
	{
		try {
			clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
			CoapRequest coapRequest = clientChannel.createRequest(true, CoapRequestCode.GET);
			byte [] token = generateRequestToken(3);
			coapRequest.setUriPath("/led/red");
			coapRequest.setToken(token);
			clientChannel.sendMessage(coapRequest);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	public void observeExample()
	{
		try {
			clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
			CoapRequest coapRequest = clientChannel.createRequest(true, CoapRequestCode.GET);
			byte [] token = generateRequestToken(3);
			coapRequest.setUriPath("/test/light");
			coapRequest.setToken(token);
			coapRequest.setObserveOption(1);
			clientChannel.sendMessage(coapRequest);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}
	
	public void Up() {
		try {
			clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
			CoapRequest coapRequest = clientChannel.createRequest(true, CoapRequestCode.POST);
			byte [] token = generateRequestToken(3);
			coapRequest.setUriPath("/led/red");
			coapRequest.setPayload("ON".getBytes());
			coapRequest.setToken(token);
			clientChannel.sendMessage(coapRequest);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	public void Down() {
		try {
			clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
			CoapRequest coapRequest = clientChannel.createRequest(true, CoapRequestCode.POST);
			byte [] token = generateRequestToken(3);
			coapRequest.setUriPath("/led/green");
			coapRequest.setPayload("ON".getBytes());
			coapRequest.setToken(token);
			clientChannel.sendMessage(coapRequest);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	public void Move() {
		try {
			clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
			CoapRequest coapRequest = clientChannel.createRequest(true, CoapRequestCode.POST);
			byte [] token = generateRequestToken(3);
			coapRequest.setUriPath("/led/blue");
			coapRequest.setPayload("ON".getBytes());
			coapRequest.setToken(token);
			clientChannel.sendMessage(coapRequest);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}


	public void GetTemperature(double a) {
		temperature = a;
	}
	
	public void SetTemperature(double a) {
		setTem = a;
	}
	
	public void MovementNum(int a) {
		movement = a;
	}
	
	//client Ui
	public void clientUi()
	{
		
		JLabel mainlb = new JLabel("Smart Boiler");
		mainlb.setFont(new Font("Dialog",Font.BOLD, 30));
		mainlb.setBounds(150, 10, 500, 50);
		add(mainlb);
		
		setTitle("Boiler");
		setSize(500, 410);
		setLocation(500,200);
		setLayout(null);
		setResizable(false);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		//수정전
		JLabel label = new JLabel("목표 온도");
		label.setBounds(150, 250, 100, 23);
		label.setFont(new Font("Serif", Font.PLAIN, 15));
		add(label);
		//
		
		getBtn = new JButton("ON");
		getBtn.setBounds(30, 250, 80, 30);
		getBtn.setFont(new Font("Serif", Font.PLAIN, 15));
		getBtn.addActionListener(this);
		add(getBtn);

		postBtn = new JButton("SET");
		postBtn.setBounds(200, 280, 60, 25);
		postBtn.setFont(new Font("Serif", Font.PLAIN, 15));
		postBtn.addActionListener(this);
		add(postBtn);

		area = new JTextArea();
		area.setFont(new Font("Serif", Font.PLAIN, 15));
		JScrollPane sc = new JScrollPane(area);
		sc.setBounds(16, 70, 450, 150);
		add(sc);

		off = new JButton("OFF");
		off.setBounds(375, 250, 80, 30);
		off.addActionListener(this);
		add(off);

		setField = new JTextField();
		setField.setColumns(2);
		setField.setBounds(220, 250, 100, 23);
		add(setField);		
		
		JLabel sub = new JLabel("온도를             감지할 동안           의 움직임이 없으면 보일러의 온도는 낮아집니다.");
		JLabel sub2 = new JLabel("[10번]                        [3번]");
		JLabel sublb1 = new JLabel("사용자의 움직임을 감지하여                                           할 수 있습니다.\n");
		JLabel sublb1_point = new JLabel("자동으로 온도를 제어");
		JLabel sublb2 = new JLabel("가 가능하며,                         에 많은 도움이 됩니다.\n");
		JLabel sublb2_point = new JLabel("간편한 온도제어                         가스비 절약");
		sublb1.setBounds(20, 320, 450, 20);
		sublb1_point.setBounds(188, 320, 450, 20);
		sublb2.setBounds(113, 340, 450, 20);
		sub.setBounds(15, 220, 500, 20);
		sub2.setBounds(58, 220, 450, 20);
		sublb2_point.setBounds(20, 340, 450, 20);
		sublb1_point.setForeground(Color.BLUE);
		sublb2_point.setForeground(Color.RED);
		sub2.setForeground(Color.MAGENTA);
		
		add(sublb1);
		add(sublb1_point);
		add(sublb2);
		add(sublb2_point);
		add(sub);
		add(sub2);
		
		setVisible(true);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		
		if(e.getSource() == getBtn)
		{ //ON버튼제어
			if(counter == 0) {
				counter++;
				String uriPath= "/temperature";
				
				area.append("[ON]스마트 보일러를 가동합니다. >> 시작 온도 : 20˚C\n");
				setTem = 20;
				
				if(uriPath.equals(".well-known/core"))
				{
					area.append("**Resource Discovery**\n");
				}else
				{
					
				}
				try {
					String option = "observe";
					
					if(option.equals("observe")) {
						try {
							clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
							CoapRequest coapRequest = clientChannel.createRequest(true, CoapRequestCode.GET);
							byte [] token = generateRequestToken(3);
							coapRequest.setUriPath(uriPath);
							coapRequest.setToken(token);
							coapRequest.setObserveOption(1);
							clientChannel.sendMessage(coapRequest);
						} catch (UnknownHostException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}else {
						clientChannel = (BasicCoapClientChannel) channelManager.connect(this, InetAddress.getByName(SERVER_ADDRESS), PORT);
						CoapRequest coapRequest = clientChannel.createRequest(true, CoapRequestCode.GET);
						byte [] token = generateRequestToken(3);
						coapRequest.setUriPath(uriPath);
						coapRequest.setToken(token);
						clientChannel.sendMessage(coapRequest);
					}
				}catch (UnknownHostException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} else {
				area.append("[error]이미 보일러가 가동 중입니다.\n");
			}
		}
		else if(e.getSource() == postBtn)
		{
			if(counter == 1) {
				String setTem = setField.getText();
				SetTemperature(Double.parseDouble(setTem));
				area.append("[SET]목표 온도를 " + setTem + "˚C로 설정하였습니다.\n");
			} else {
				area.append("[error]보일러 전원이 꺼져있습니다.\n");
			}
		}
		else if(e.getSource() == off)
		{
			if(counter == 1) {
				setTem = 0;
				area.append("[OFF]스마트 보일러를 종료합니다.\n");
				System.exit(0);
			} else {
				area.append("[error]보일러 전원이 꺼져있습니다.\n");
			}
		}
	}
	
	
	
	public static void main(String[] args)
	{
		System.out.println("Start CoAP Client");
		String serverIp = "raspberrypi.mshome.net";
		//Constants.COAP_DEFAULT_PORT (5683)
		BasicCoapClient client = new BasicCoapClient(serverIp, Constants.COAP_DEFAULT_PORT);
		client.channelManager = BasicCoapChannelManager.getInstance();
		
		//UI
		client.clientUi();
		
		while(true) {
			if(client.movement < 4)
				client.Move();
			if(client.setTem > client.temperature) {
				client.Up();
			} else if(client.setTem < client.temperature){
				client.Down();
			}else {
				
			}
			
		}
	}
}