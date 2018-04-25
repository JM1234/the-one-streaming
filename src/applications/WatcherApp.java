package applications;

import java.util.ArrayList;

import java.util.Collection;
import java.util.HashMap;
import java.util.Random;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import fragmentation.Fragment;
import routing.TVProphetRouter;
import streaming.Stream;
import streaming.StreamChunk;
import streaming.StreamProperties;

public class WatcherApp extends StreamingApplication{

	public static final String WATCHER_TYPE = "watcherType"; 
	public static final int PLAYING = 1;
	public static final int WAITING = 0;
	
	private int hello_interval=Stream.getStreamInterval()+1; //to send needs, just enough time for source to create
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private int		streamSize=500;
	private int 	status =-1;
	
	private int 	watcherType; //1 if listener. 0 if just a hop
	private boolean isWatching=false;
	private double lastTimePlayed=0;
	private boolean helloed=false;
	
	private StreamProperties props; ////////properties of current stream channel. what i received, etc.
	private Random	rng;
		
	Message broadcastMsg;
	
	public WatcherApp(Settings s) {
		super(s);
		
		this.watcherType = s.getInt(WATCHER_TYPE);
		props = new StreamProperties("");
	}

	public WatcherApp(WatcherApp a) {
		super(a);
		
		this.destMax = a.getDestMax();
		this.destMin = a.getDestMin();
		this.seed = a.getSeed();
//		this.streamSize = a.getStreamSize();
		this.watcherType = a.getWatcherType();
		this.rng = new Random(this.seed);
		props = new StreamProperties("");
	}

	@Override
	public Message handle(Message msg, DTNHost host) {
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
		// If we are the receiver
		if (type.contains(APP_TYPE)){
			String msg_type = (String) msg.getProperty("msg_type");
			
			//someone is live
			if (msg_type.equalsIgnoreCase(BROADCAST_LIVE) && broadcastMsg==null){ 
				String id = APP_TYPE+":register" + SimClock.getIntTime() + "-" +host.getAddress();
				if (!isWatching && watcherType==1){
					Message m = new Message(host, msg.getFrom(), id, StreamingApplication.SIMPLE_MSG_SIZE);
					m.addProperty("type", APP_TYPE);
					m.addProperty("msg_type", BROADCAST_REQUEST);
					m.setAppID(APP_ID);
					
					isWatching=true;
					status = WAITING;
					
					String streamID=(String) msg.getProperty("streamID");
					props.setStreamID(streamID);
					props.setStartTime(m.getCreationTime());
					
					sendBuffermap(host,msg.getHops().get(msg.getHopCount()-1), props.getBufferMap(), props.getFragments());
					System.out.println("Sending buffermap to : "+ msg.getHops().get(msg.getHopCount()-1));
					helloed=true;
					
					host.createNewMessage(m);
//					m.setTtl(30);
				
				}
				System.out.println(host + "RECEIVED BROADCAST " + msg.getFrom());
				broadcastMsg = msg.replicate();
				
			}
			else if(msg_type.equalsIgnoreCase(BROADCAST_CHUNK_SENT)){ //received chunks
				System.out.println("@WATCHER--------------------");
				StreamChunk chunk = (StreamChunk) msg.getProperty("chunk");
				props.addChunk(chunk);
				System.out.println(host+ " i received: "+chunk.getChunkID());
				/*
				 * dapat makausa la hya na setChunkStart
				 */
				if (msg.getTo() == host){
					System.out.println("Props start time:" + props.getStartTime());
					System.out.println("Chunk  t: "+chunk.getCreationTime());
					if (msg.getId().contains(":first") || (chunk.getCreationTime() == props.getStartTime() && props.getAck()==-1)){
						System.out.println("First chunk received by " + host + ":" +chunk.getChunkID());
						props.setChunkStart(chunk.getChunkID());
						status = PLAYING;
						this.lastTimePlayed=SimClock.getTime();
					}
					else{
						props.setAck(chunk.getChunkID());
					}

					System.out.print(host + " updated: " + props.getBufferMap());
//					System.out.println("ChunksReceived: "+props.getReceived().size());
				}
				
			}
			
			
//			else if (msg_type.equalsIgnoreCase(CHUNK_REQUEST)){
//				//send a requested chunk
//			}

			else if(msg_type.equalsIgnoreCase(BROADCAST_FRAGMENT_SENT)){
				////handle fragments here
				System.out.println("Received fragments.");
				
				Fragment f = (Fragment) msg.getProperty("fragment");
				props.addFragment(f);
//				props.sync(f);
				
				System.out.print("Updated: ");
				for(long c : props.getBufferMap()){
					System.out.print(c + " ,");
				}
				System.out.println("");
			}
			
			/**
			 * If the other node is the initiator, 
			 * 
			 * 1. send broadcast if it hasn't received yet
			 * 2. if i don't have anything, i'll say hello as well
			 * 3. check if in urgent status
			 * 		a. if status is WAITING, send the node it needs.
			 * 4. check if other node has lesser chunks than i
			 * 		a. if yes, send missingChunks
			 **/
			else if(msg_type.equalsIgnoreCase(HELLO)){
				System.out.println("Received HELLO at " +host);

				try{
					Connection curCon = null;
					for (Connection con: host.getConnections()){
						if (con.getOtherNode(host).equals(msg.getFrom())){
							curCon = con;
							break;
						}
					}
					long otherAck = (long) msg.getProperty("ack");
					int otherStatus = (int) msg.getProperty("status");
					
					System.out.println("OtherAck: "+otherAck + " OtherStatus: "+otherStatus);
					System.out.println("Props needed exists: "+props.getReceived().contains(otherAck+1));
					if (broadcastMsg!=null && otherStatus==-1 && otherAck==-1){ //di ak sure if amo na ghap ini ha uninterested watcher
						System.out.println(host + "Other node has no broadcast, sending a broadcast.");
						broadcastMsg.setTo(msg.getFrom());
						((TVProphetRouter) host.getRouter()).addUrgentMessage(broadcastMsg.replicate(), false);
					}
					else if (otherStatus==WAITING && props.getBufferMap().contains(otherAck+1) && otherAck<=props.getAck()) { //if nahulat first ever chunk || iya ack < mine
						System.out.println("HANDLING NODE! It's an emergency for it and I have what it needs!");
						handleNode(msg, host, msg.getFrom());
					}
					else if (otherStatus!=WAITING || otherAck > props.getAck()){ //mas importante iya ganap, di ko na kailanngan i evaluate
						System.out.println("Mas importante ak ganap." + host + "to: "+msg.getFrom());
						sendBuffermap(host, msg.getFrom(), props.getBufferMap(), props.getFragments());
					}
					else{
						System.out.println("Waray ko anything na im kailangan.");
					}
					
//					else{ //meaning ako nag una pag send, na weight na niya so i'm left with nothing
//						System.out.println("Waray ko tim kailangan pero mayda ko mahahatag.");
//						handleNode(msg, host, msg.getFrom());
//					}
				}catch(NullPointerException e){	}
			}
		}
		return msg;
	}
	
	@Override
	public void update(DTNHost host) {
		double curTime = SimClock.getTime();

		try{
	
			if (isWatching && (curTime-this.lastTimePlayed >= Stream.getStreamInterval())){
				System.out.println("+++++++++++++++++++++++MUST BE PLAYING ");
				if(props.isReady(props.getNext())){
					props.playNext();
					status = PLAYING;
					this.lastTimePlayed = curTime;
					System.out.println(host + " playing: " + props.getPlaying() + " time: "+lastTimePlayed);
				}
				else{ // if (status==PLAYING)
					status = WAITING;
					System.out.println(host+ " waiting: "+ props.getNext());
//					Connection con = host.getConnections().get(0); /////save connections
//					if (con.isTransferring() && con.getMessage().){
//						con.abortTransfer(); 
//						sendBuffermap(host, con.getOtherNode(host), props.getBufferMap(), props.getFragments());
//						System.out.println("Sent buffermap");
//					}
				}
				System.out.println("+++++++++++++++++++++++++++++++++++++++++++++");
			}
		}catch(NullPointerException e){
			e.printStackTrace();
		}	
		
		try{
			Connection con = host.getConnections().get(0); /////save connections
//			System.out.println(host + " is initiator? " + con.isInitiator(host));
			
			if((con.isUp() && !helloed && con.isInitiator(host)) || (con.isReadyForTransfer() && !helloed)){ 
				sendBuffermap(host, con.getOtherNode(host), props.getBufferMap(), props.getFragments());
				helloed=true;
				System.out.println(host + " : SENT HELLO!!!!!!!!!!!!" );
			}	
			if(status == WAITING){
				sendBuffermap(host, con.getOtherNode(host), props.getBufferMap(), props.getFragments());
			}
		}catch(IndexOutOfBoundsException e){
			helloed=false; //still not sure if it works in multilink
		}catch(NullPointerException e){}
	}

	@Override
	public Application replicate() {
		return new WatcherApp(this);
	}
	
	public int getWatcherType(){
		return watcherType;
	}
	
	public StreamProperties getStreamProps(){
		return props;
	}
	
	private void sendBuffermap(DTNHost host, DTNHost to, ArrayList<Long> chunks, ArrayList<Fragment> fragments){
		String id = APP_TYPE+ ":hello" + SimClock.getIntTime() + "-" +host.getAddress();
		
		if(!host.getRouter().hasMessage(id)){
			Message m = new Message(host, to, id, BUFFERMAP_SIZE); //buffermap size must be defined.
			m.addProperty("type", APP_TYPE);
			m.setAppID(APP_ID);
			m.addProperty("msg_type", HELLO);
			m.addProperty("status", this.status);
			m.addProperty("buffermap", chunks); //////should be full buffermap
	//		m.addProperty("fragments", fragments);
			m.addProperty("ack", props.getAck());
//			m.setReceiveTime(1);
			m.addProperty("startTime", props.getStartTime());
			host.createNewMessage(m);
			m.setTtl(3);
		}
	}
	
	private void handleNode(Message msg, DTNHost src, DTNHost to){
		System.out.println("@handle node");
		
		ArrayList<Long> c = (ArrayList<Long>) msg.getProperty("buffermap");
		ArrayList<StreamChunk> missingC = getMissingChunks(c, (long) msg.getProperty("ack"));

		
		System.out.println("@ " + to + "missing: "+missingC);
		for (StreamChunk m : missingC){
			sendChunk(m, src, to);
		}

		
	}
	
	private ArrayList<StreamChunk> getMissingChunks(ArrayList<Long> chunks, long ack){ ////optimize
		System.out.println("@ getting missing chunks");
		
		ArrayList<StreamChunk> missing = new ArrayList<StreamChunk>();
		System.out.println("HAS: ");
		ArrayList<StreamChunk> has = props.getReceived();
		System.out.print(has.size() + "\n");
		
		System.out.println("Ack: "+ ack);
		System.out.print("Props start chunk: ");
		System.out.println(props.getStartChunk());
		
		int i=0;
		while(has.get(i).getChunkID()<=ack){
			i++;
		}
		for (; i<has.size(); i++){
			StreamChunk c = has.get(i);
			if (c.getChunkID() > ack && !chunks.contains(c)){
				missing.add(c);
			}
		}
		return missing;
	}
	
	
	/////paano ma send an watcher, same id, or not? 
	
	protected void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to){
//		System.out.println("@ WATCHER_SENDING");
		
		String id = APP_TYPE + ":chunk-" + chunk.getChunkID()+  " " + chunk.getCreationTime(); //+ "-" +chunk.;

		if (host.getRouter().hasMessage(id)){
			Message m =  ((TVProphetRouter) host.getRouter()).getStoredMessage(id);
			m.setTo(to);
			((TVProphetRouter) host.getRouter()).addUrgentMessage(m, false);
//			System.out.println("Added message as urgent.");
//			host.getRouter().sendMessage(id, to);
		}
		else{	
//			System.out.println("Message created . Sending "+chunk.getChunkID() + " to " +to);
			
			Message m = new Message(host, to, id, (int) chunk.getSize());		
			m.addProperty("type", APP_TYPE);
			m.setAppID(APP_ID);
//			m.addProperty("msg_type", CHUNK_SENT);
			m.addProperty("chunk", chunk);	
			host.createNewMessage(m);
			
			sendEventToListeners(CHUNK_DELIVERED, chunk, host);
		}
	}
	
	private Connection getConnection(DTNHost host, DTNHost to){
		Connection curCon = null;
		for (Connection con: host.getConnections()){
			if (con.getOtherNode(host).equals(to)){
				curCon = con;
				break;
			}
		}
		return curCon;
	}

}

