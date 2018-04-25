package applications;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import core.Application;
import core.Connection;
import core.ConnectionListener;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import fragmentation.Fragment;
import routing.TVProphetRouter;
import routing.TVProphetRouterV2;
import streaming.Stream;
import streaming.StreamChunk;
import streaming.StreamProperties;
import util.Tuple;

public class WatcherAppV2 extends StreamingApplication {

	public static final String WATCHER_TYPE = "watcherType"; 
	
	public static final int PLAYING = 1;
	public static final int WAITING = 0;
	public static final int MAXIMUM_WAITING_REQUEST=15;
	
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private Random	rng;
	
	private int 	status =-1;
	private int 	watcherType; //1 if listener. 0 if just a hop
	private boolean isWatching=false;
	private double lastTimePlayed=0;
//	private boolean helloed=false;
	
	private StreamProperties props; ////////properties of current stream channel. what i received, etc.
	private Message broadcastMsg;
	private int urgentRequest=0;
	private Message m; //temp urgent msg
	private boolean isListener=false;
	
	private long lastChunkRequested=-1;
	private double lastTimeRequested = 0;
	
	public WatcherAppV2(Settings s) {
		super(s);
		
		this.watcherType = s.getInt(WATCHER_TYPE);
		props = new StreamProperties("");
	}

	public WatcherAppV2(WatcherAppV2 a) {
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
		
		if (type.equals(APP_TYPE)){
			String msg_type = (String) msg.getProperty("msg_type");
			
			if (msg_type.equals(BROADCAST_LIVE)){
				System.out.println(host +" Received a broadcast.");
				
				String id = APP_TYPE+":register" + SimClock.getIntTime() + "-" +host.getAddress();
				if (!isWatching && watcherType==1){
					//handleNode() -- sendRequest
					
					isWatching=true;
					status = WAITING;

					int timeStarted = (int) msg.getProperty("time_started");
					
					String streamID=(String) msg.getProperty("streamID");
					props.setStreamID(streamID);
					props.setStartTime(timeStarted);
//					props.setStartTime(SimClock.getIntTime());

					if (lastChunkRequested==-1){
						this.sendRequest(host, msg.getHops().get(msg.getHopCount()-1), 0, 14);
//						handleNode(msg, host, msg.getHops().get(msg.getHopCount()-1));
					}
				}
				///for uninterested watcher, just save
				broadcastMsg = msg.replicate();
			}
			
			else if (msg_type.equals(HELLO)){ //used only for evaluating if i can get something from this node
			try{
				System.out.println(host + " received hello from "+msg.getFrom());
				
				DTNHost sender = msg.getHops().get(msg.getHopCount()-1); //if an naghatag hini na message == broadcastMsg.getFrom
				Connection curCon = getCurrConnection(host, sender);

				int otherStatus = (int) msg.getProperty("status");
				long otherAck = (long) msg.getProperty("ack");
				int otherType = (int) msg.getProperty("watcherType");
				
				if (isWatching && otherType==-1){
					System.out.println("Met a broadcaster. Evaluate what we can get.");
					handleNode(msg, host, msg.getFrom());
				}
				else if (broadcastMsg!=null &&  otherAck == -1 && otherStatus == -1 && otherType==1){ //meaning we haven't sent a broadcast_live to this node yet
					System.out.println(host + "Other node has no broadcast, sending a broadcast.");
					broadcastMsg.setTo(msg.getFrom());
					((TVProphetRouterV2) host.getRouter()).addUrgentMessage(broadcastMsg.replicate(), false);
				}	
				else if ( (props.getAck()<otherAck) && isWatching){ //if my status is waiting and i am a legit watcher  && !curCon.isInitiator(host)
//					System.out.println("Mas importante ak ganap." + host + "to: "+msg.getFrom());
//					sendBuffermap(host, msg.getFrom(), props.getBufferMap(), props.getFragments());
					System.out.println("I think this node have my needs. Requesting...");
					handleNode(msg, host, msg.getFrom());
				}
				
				//if urgent talaga iya need and mayda ko han iya kailangan
//					else if (otherStatus == WAITING && props.getBufferMap().contains(otherAck+1) && otherType==1){ //&& otherAck<=props.getAck() ){
//						System.out.println("HANDLING NODE! It's an emergency for it and I have what it needs!");
////						handleNode(msg, host, msg.getFrom());
//						
//						long sInterest = (long) msg.getProperty("sInterest");
//						long eInterest = (long) msg.getProperty("eInterest");
//						
//						for(long i=sInterest; i<=eInterest  ;i++){
//							try{
//								sendChunk(props.getChunk(i), host, msg.getFrom());
//							}catch(NullPointerException e){}
//						}
//					}
									
//					else{ //handle its needs
//						System.out.println("Nothing left to give.");
//
//						if(otherType == 0){
//							System.out.println("Handling.");
//							handleNode(msg, host, msg.getFrom());
//						}
//						
////						System.out.println("i can give what i have. just handling node.");
////						handleNode(msg, host, msg.getFrom());
//						//send hello after all this
//					}
			}catch(NullPointerException e){}
			}
			
			else if (msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){
				System.out.println(host +" received a broadcast request from "+msg.getFrom());
				
				long sInterest = (long) msg.getProperty("sInterest");
				long eInterest = (long) msg.getProperty("eInterest");
				
				for(long i=sInterest; i<=eInterest  ;i++){
					try{
						this.sendChunk(props.getChunk(i), host, msg.getFrom());
						System.out.println("replying BROADCAST_REQUEST");
					}catch(NullPointerException e){}
				}
				
//				//send first ever waiting
//				double t = msg.getCreationTime();
//				StreamChunk need = props.getChunk(t);
//				
//				if (need!=null){
//					ArrayList<StreamChunk> missingC = getMissingChunks(null, need.getChunkID()-1);
//
//					System.out.print("@ register _ found missing: ");
//					System.out.println("Chunk needed: " + need.getChunkID());
//				
////					this.handleNode(msg, host, msg.getFrom());
//					/////modify handle node. dapat didto ini ghap
//					for(StreamChunk c: missingC){
//						System.out.print(c.getChunkID() + ",");
//						sendChunk(c, host, msg.getFrom());
//					}
//					System.out.println("");
//				}
//				else{
//					System.out.println("I don't have what you need.");
//				}
			}
			
			else if(msg_type.equalsIgnoreCase(BROADCAST_CHUNK_SENT)){ //received chunks
				
				StreamChunk chunk = (StreamChunk) msg.getProperty("chunk");
				props.addChunk(chunk);
				
				System.out.println(host+ " received: "+chunk.getChunkID() + " for " + msg.getTo());
				
				DTNHost sender = msg.getHops().get(msg.getHopCount()-1); //if an naghatag hini na message == broadcastMsg.getFrom
				Connection curCon = getCurrConnection(host, sender);
				
				System.out.println("Ack Now: "+props.getAck()  +  " Received: "+chunk.getChunkID());
				System.out.println("Sender " + sender);
				
				if (status==WAITING && (props.getAck()+1)!=chunk.getChunkID()  
						&& (sender.equals(broadcastMsg.getFrom()) && props.getAck()!=-1) ){ // && urgentRequest<2 
					System.out.println("Waiting for "+ props.getAck() + " but received " + chunk.getChunkID());
				}
				
				if (msg.getTo() == host){
					System.out.println("Props start time:" + props.getStartTime());
					if ( (chunk.getCreationTime() <= props.getStartTime())  && (props.getStartTime() < (chunk.getCreationTime() + Stream.getStreamInterval()))
						&& props.getAck()==-1){
						System.out.println("First chunk received by " + host + ":" +chunk.getChunkID());
						props.setChunkStart(chunk.getChunkID());
						status = PLAYING;
						this.lastTimePlayed=SimClock.getTime();
					}
					else{
						props.setAck(chunk.getChunkID());
					}
				}
				
				System.out.println(host + " updated:  " + props.getBufferMap());
				System.out.println("size: " +props.getBufferMap().size());	
			}
			
			else if (msg_type.equals(BROADCAST_FRAGMENT_SENT)){
				Fragment f = (Fragment) msg.getProperty("fragment");
				System.out.println("Received fragment: " + f.getId());
				
				int fLevel =(int) msg.getProperty("f_level");
				
				decodeFragment(f, fLevel);
			}
		}
			
		return msg;
	}

	@Override
	public void update(DTNHost host) {

		double curTime = SimClock.getTime();

		try{
			checkHelloedConnection(host);
			for (Connection c : host.getConnections()){
				DTNHost otherHost = c.getOtherNode(host);
				if (c.isUp()){
					if (!hasHelloed(otherHost)){
						System.out.println(host + " is sending hello to "+ otherHost);
						sendBuffermap(host, c.getOtherNode(host), props.getBufferMap(), null);
						System.out.println(host + " added to hello: "+c.getOtherNode(host));
					}		
					else{
						updateHello(host, otherHost);
					}
				}
			}
		}
		catch(NullPointerException e){}
		
		try{
			if (isWatching && (curTime-this.lastTimePlayed >= Stream.getStreamInterval())){
				System.out.println("++++++++++++++MUST BE PLAYING +++++++++++++++++");
				if(props.isReady(props.getNext())){
					props.playNext();
					status = PLAYING;
					this.lastTimePlayed = curTime;
					System.out.println(host + " playing: " + props.getPlaying() + " time: "+lastTimePlayed);
				}
				else{ // if (status==PLAYING)
					status = WAITING;
					System.out.println(host+ " waiting: "+ props.getNext());
					
					//send request here again. because last chunk requested did not arrive
					if (host.getConnections().size() <=0 ){ 
						lastChunkRequested = props.getAck();
					}
					
//					if (curTime-this.lastTimeRequested >= MAXIMUM_WAITING_REQUEST && props.getChunk(lastChunkRequested)==null){ //if I waited long enough pero hindi parin nila nasend
//						for (Connection c: host.getConnections()){ 
//							System.out.println("Updating my request @ "+ props.getNext()+ " Prev LastChunkRequested: "+lastChunkRequested);
//							if (lastChunkRequested < props.getPlaying()+1 && sentHello.equals(c.getOtherNode(host))){
//								this.sendRequest(host, c.getOtherNode(host), props.getNext(), props.getNext()+14);
//							}
//						}
//					}
					
//					else {//(host.getConnections().size() > 0){
//						for (Connection c: host.getConnections()){ /////if waray mag disconnect pero waray man ka grant ak request kanina
//							System.out.println("Updating my request @ "+ props.getPlaying() + " Prev LastChunkRequested: "+lastChunkRequested);
//							if (lastChunkRequested < props.getPlaying()+1+14 && sentHello.equals(c.getOtherNode(host))){
//								this.sendRequest(host, c.getOtherNode(host), props.getPlaying()+1, props.getPlaying()+1+14);
//								lastChunkRequested = props.getPlaying()+1+14;
//							}
//						}
//					}
				}
				
				/*
				 * if we are currently playing, and the chunk after the last chunk we requested is not yet here and there is
				 * 5 seconds(or less) time left before it will be played, send request
				 */
//	
//				if (status==PLAYING && !props.isReady(lastChunkRequested+1) && (lastChunkRequested-props.getPlaying()<=5)){ //&& curTime-lastTimeRequested>=MAXIMUM_WAITING_REQUEST){ //status==PLAYING && 
//					///interval for sending request again
//					System.out.println("here @ future not ready");
//					for (Connection c: host.getConnections()){
////						StreamChunk futureNeed = props.getChunk(props.getPlaying()+5);
//						if (hasHelloed(c.getOtherNode(host))){
//							System.out.println("Updating my request @ "+ props.getPlaying() + " LastChunkRequested: "+lastChunkRequested);
//							this.sendRequest(host, c.getOtherNode(host), lastChunkRequested+1, lastChunkRequested+15);
//						}
//					}
//				}
				
				System.out.println("+++++++++++++++++++++++++++++++++++++++++++++");
			}
		}catch(NullPointerException e){
			e.printStackTrace();
		}
		catch(ArrayIndexOutOfBoundsException i){
		}	
	}
	
	public void updateHello(DTNHost host, DTNHost otherHost){
		int curTime = SimClock.getIntTime();
		
		if (curTime - sentHello.get(otherHost) >= HELLO_UPDATE){ //if it's time to send an updated HELLO
			System.out.println(host +" sending an updated hello to "+ otherHost + " @ " + curTime);
			sendBuffermap(host, otherHost, props.getBufferMap(), null);
			sentHello.put(otherHost, SimClock.getIntTime());
		}
	}
	
	private void decodeFragment(Fragment f, int fLevel){
		if (fLevel==1) props.addFragment(f);
		
		//decode for playing
		//save fragment for sending
		for (StreamChunk c : f.getBundled()){
			props.addChunk(c);
		}
		
	}
	
	private Message sendBuffermap(DTNHost host, DTNHost to, ArrayList<Long> chunks, ArrayList<Fragment> fragments){
		String id = APP_TYPE+ ":hello" + + SimClock.getIntTime() +"-" +host.getAddress(); //+ SimClock.getIntTime()
		
			Message m = new Message(host, to, id, BUFFERMAP_SIZE); //buffermap size must be defined.
//			m.setReceiveTime(1);
			m.addProperty("type", APP_TYPE);
			m.setAppID(APP_ID);
			m.addProperty("msg_type", HELLO);
			m.addProperty("status", this.status);
			m.addProperty("buffermap", chunks); //////should be full buffermap
			m.addProperty("ack", props.getAck());
			m.addProperty("watcherType", this.watcherType);
			m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 1);
			host.createNewMessage(m);
			m.setTtl(5);
			sentHello.put(to, SimClock.getIntTime());
			return m;
	}
	
	/*
	 * responsible for requesting chunks
	 */
	private void handleNode(Message msg, DTNHost src, DTNHost to){
		
//		ArrayList<Long> c = (ArrayList<Long>) msg.getProperty("buffermap");
//		ArrayList<StreamChunk> missingC = getMissingChunks(c, (long) msg.getProperty("ack"));
//
//		for (StreamChunk m : missingC){
//			sendChunk(m, src, to);
//		}

		int watcherType = (int) msg.getProperty("watcherType");
		long sInterest = props.getAck()+1;
		long eInterest = props.getAck()+15;
		long latest = (long) msg.getProperty("ack");
		
		if (latest >= sInterest && lastChunkRequested<sInterest ) { //|| || status ==WAITING){//){ //if latest ack of otherNode > what I need && we haven't requested it yet
			System.out.println(src + " requesting to " + to +" SInterest: "+sInterest + " EInterest: "+eInterest);
		
			if (eInterest <=latest){
				sendRequest(src, to, sInterest, eInterest);
			}
			else{ //based han iya last na mayda
				sendRequest(src, to, sInterest, latest);
			}
		}
	}

	private void sendRequest(DTNHost host, DTNHost to, long sInterest, long eInterest){
		String id = APP_TYPE + ":request " + sInterest + ":" + eInterest + "-" + host.getAddress();
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_REQUEST);
		m.addProperty("sInterest", sInterest);
		m.addProperty("eInterest", eInterest);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);
		
		lastChunkRequested = eInterest;
		lastTimeRequested = SimClock.getTime();
		System.out.println("Sent request to " + to);
	}
	
	
	private ArrayList<StreamChunk> getMissingChunks(ArrayList<Long> chunks, long ack) throws NullPointerException{ ////optimize
		
		ArrayList<StreamChunk> missing = new ArrayList<StreamChunk>();
		try{
			ArrayList<StreamChunk> has = props.getReceived();
			
			int i=0;
			while(has.get(i).getChunkID()<=ack){
				i++;
			}
			for (; i<has.size(); i++){
				StreamChunk c = has.get(i);
				try{
					if (c.getChunkID() > ack && !chunks.contains(c)){
						missing.add(c);
					}
				}catch(NullPointerException e){
					missing.add(c);
				}
			}
		}catch(IndexOutOfBoundsException i){
			System.out.println("Error. No chunks received yet.");
		}
		return missing;
	}
	
	@Override
	protected void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to) {
		String id = APP_TYPE + ":chunk-" + chunk.getChunkID()+  " " + chunk.getCreationTime(); //+ "-" +chunk.;
		
		Message m = new Message(host, to, id, (int) chunk.getSize());		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_CHUNK_SENT);
		m.addProperty("chunk", chunk);	
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 3);
		host.createNewMessage(m);
			
		sendEventToListeners(CHUNK_DELIVERED, chunk, host);
	}

	public int getWatcherType(){
		return watcherType;
	}

	@Override
	public Application replicate() {
		return new WatcherAppV2(this);
	}

}