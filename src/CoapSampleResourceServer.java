package org.ws4d.coap.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.ws4d.coap.connection.BasicCoapClientChannel;
import org.ws4d.coap.connection.BasicCoapServerChannel;
import org.ws4d.coap.connection.BasicCoapSocketHandler;
import org.ws4d.coap.interfaces.CoapChannel;
import org.ws4d.coap.interfaces.CoapClientChannel;
import org.ws4d.coap.interfaces.CoapResponse;
import org.ws4d.coap.messages.CoapMediaType;
import org.ws4d.coap.rest.BasicCoapResource;
import org.ws4d.coap.rest.CoapResourceServer;
import org.ws4d.coap.rest.ResourceHandler;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.spi.SpiChannel;
import com.pi4j.io.spi.SpiDevice;
import com.pi4j.io.spi.SpiFactory;
import com.pi4j.util.CommandArgumentParser;

import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * @author Christian Lerche <christian.lerche@uni-rostock.de>
 * 
 */
public class CoapSampleResourceServer 
{
	public static final GpioController gpio1 = GpioFactory.getInstance();
	public static Pin pin = null;
	public static SpiDevice spi = null;
	public static short ADC_CHANNEL_COUNT = 4;  // MCP3204 = 4
	public static GpioPinPwmOutput pwm = null;
	
	
	//Steinhart Parameters
	public static String a0 = "1.131786e-003";
	public static String a1 = "2.336422e-004";
	public static String a3 = "8.985024e-008";
	public static String T0 = "273.15";

	private static CoapSampleResourceServer sampleServer;
	private CoapResourceServer resourceServer;
	private static Logger logger = Logger
			.getLogger(CoapSampleResourceServer.class.getName());

	private GpioController gpio;
	private GpioPinDigitalOutput redLed, greenLed, blueLed;
	
	public static int count = 0;
	public static int cnt_ctrl = 1;
	// create gpio controller           
	private GpioController gpioSensor; 
	private GpioPinDigitalInput sensor;          

	// create another gpio controller                           
	private GpioController gpioLED;           
	private GpioPinDigitalOutput led;

	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static int read() throws IOException, InterruptedException {
		for(short channel = 0; channel < ADC_CHANNEL_COUNT; channel++){
			if(channel == 0){
				int conversion_value = getConversionValue(channel);
				//System.out.println("adc value: "+conversion_value);
				Thread.sleep(250);
				return conversion_value;
			}
		}
		Thread.sleep(250);
		return 0;
	}

	public static int getConversionValue(short channel) throws IOException {

		// create a data buffer and initialize a conversion request payload
		byte data[] = new byte[] {
				(byte) 0b00000001,                              
				// first byte, start bit
				(byte)(0b10000000 |( ((channel & 7) << 4))),    
				// second byte transmitted -> (SGL/DIF = 1, D2=D1=D0=0)
				(byte) 0b00000000                               
				// third byte transmitted....don't care
		};

		// send conversion request to ADC chip via SPI channel
		byte[] result = spi.write(data);

		// calculate and return conversion value from result bytes
		int value = (result[1]<< 8) & 0b1100000000; //merge data[1] & data[2] to get 10-bit result
		value |=  (result[2] & 0xff);
		return value;
	}
	public String getTemp() throws InterruptedException, IOException{
		// create SPI object instance for SPI for communication
		
		double u = read()/1023.0;
		double R = (1/u-1)*10000;
		double InR = Math.log(R);
		double temp = Double.parseDouble(a0)+Double.parseDouble(a1)*InR
				+Double.parseDouble(a3)*Math.pow(InR, 3);
		double inv = 1/temp;
		double temper = inv - Double.parseDouble(T0);
		
		System.out.println("온도: "+ String.format("%.2f", temper));
		
		return String.format("%.2f", temper);
	}
	public static void main(String[] args) throws IOException {
        logger.addAppender(new ConsoleAppender(new SimpleLayout()));
		logger.setLevel(Level.INFO);
		logger.info("Start Sample Resource Server");
		sampleServer = new CoapSampleResourceServer();
		sampleServer.gpioInit();
		sampleServer.run();
	}
	
	public void gpioInit() throws IOException {
		gpio = GpioFactory.getInstance();
		redLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_29, PinState.LOW);
		greenLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_28, PinState.LOW);
		blueLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_27, PinState.LOW);
		
		pin = CommandArgumentParser.getPin(RaspiPin.class, RaspiPin.GPIO_04);
		
		pwm = gpio.provisionSoftPwmOutputPin(pin);
		
		pwm.setPwmRange(1023);

	    spi = SpiFactory.getInstance(SpiChannel.CS0,
				SpiDevice.DEFAULT_SPI_SPEED, // default spi speed 1 MHz
				SpiDevice.DEFAULT_SPI_MODE); // default spi mode 0
	    
	    gpioSensor = GpioFactory.getInstance();
	    sensor = gpioSensor.provisionDigitalInputPin(RaspiPin.GPIO_25, PinPullResistance.PULL_DOWN);
	    
	    gpioLED = GpioFactory.getInstance();
	    led = gpioLED.provisionDigitalOutputPin(RaspiPin.GPIO_24, "LED", PinState.LOW);
	}
	
	private void run() throws IOException 
	{
		if (resourceServer != null)
			resourceServer.stop();
		resourceServer = new CoapResourceServer();
		
		/* Show detailed logging of Resource Server*/
		Logger resourceLogger = Logger.getLogger(CoapResourceServer.class.getName());
		resourceLogger.setLevel(Level.ALL);
		
		/* add resources */
//		BasicCoapResource light = new BasicCoapResource("/test/light", "light Actuator".getBytes(), CoapMediaType.text_plain);
		
		BasicCoapResource red = new BasicCoapResource("/led/red", "OFF".getBytes(), CoapMediaType.text_plain);
		red.registerResourceHandler(new ResourceHandler() {
			@Override
			public void onPost(byte[] data) {
				System.out.println("Post to /led/red");
				String payload = new String(data);
				if(payload.equals("ON")) {
					redLed.high();
					greenLed.low();
					red.setValue("ON".getBytes());
					pwm.setPwm(300);
				}else if(payload.equals("OFF")) {
					redLed.low();
					red.setValue("OFF".getBytes());
				}else {
					
				}
				red.changed();
			}
		});
		BasicCoapResource green = new BasicCoapResource("/led/green", "OFF".getBytes(), CoapMediaType.text_plain);
		green.registerResourceHandler(new ResourceHandler() {
			@Override
			public void onPost(byte[] data) {
				System.out.println("Post to /led/green");
				String payload = new String(data);
				if(payload.equals("ON")) {
					greenLed.high();
					redLed.low();
					green.setValue("ON".getBytes());
					pwm.setPwm(0);
				}else if(payload.equals("OFF")) {
					greenLed.low();
					green.setValue("OFF".getBytes());
				}else {
					
				}
				green.changed();
			}
		});
		BasicCoapResource blue = new BasicCoapResource("/led/blue", "OFF".getBytes(), CoapMediaType.text_plain);
		blue.registerResourceHandler(new ResourceHandler() {
			@Override
			public void onPost(byte[] data) {
				System.out.println("Post to /led/blue");				
				sensor.addListener(new GpioPinListenerDigital() {           
		    	    public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
		    	        if(event.getState().isHigh()){  
		    	        	System.out.println("움직임 감지!");
		    	        	if(count+1 == cnt_ctrl)
		    	        		count++;
		    	            led.high();
							blue.setValue("ON".getBytes());
		    	        }   

		    	        if(event.getState().isLow()){   
		    	        	System.out.println(count + " / 3\n");
		    	            led.low();
							blue.setValue("OFF".getBytes());
		    	        }
		    	        
		    	    }       

		    	});
				blue.changed();
			}
		});

		BasicCoapResource temp = new BasicCoapResource("/temperature", "".getBytes(), CoapMediaType.text_plain);
		temp.registerResourceHandler(new ResourceHandler() {
			@Override
			public void onPost(byte[] data) {
				System.out.println("Post to /temperature");
//				System.out.println(new String(data));
//				temp.setValue(data);
//				temp.changed();
			}
		});		
		
		red.setObservable(false);
		green.setObservable(false);
		blue.setObservable(false);
		temp.setObservable(false);
		
		resourceServer.createResource(red);
		resourceServer.createResource(green);
		resourceServer.createResource(blue);
		resourceServer.createResource(temp);
		
		try {
			resourceServer.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//observe example

		while(true){
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				String getTemp = getTemp();
				temp.setValue(((String)"" + count + getTemp).getBytes());
				if(count == cnt_ctrl)
					cnt_ctrl++;
			}catch (InterruptedException e) {
				e.printStackTrace();
			}
			temp.changed();
		}
	}
}