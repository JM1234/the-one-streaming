package applications;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import report.StreamAppReport;
import routing.TVProphetRouterV2;
import streaming.Stream;
import streaming.StreamChunk;
import streaming.StreamProperties;

public class WatcherAppV3 extends StreamingApplication{

	public static final String WATCHER_TYPE = "watcherType"; 
	
	public static final int PLAYING = 1;
	public static final int WAITING = 0;
	public static final int MAXIMUM_PENDING_REQUEST=12;
	public static final int PREBUFFER = 10;
	
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private Random	rng;
	
	private int 	status =-1;
	private int 	watcherType; //1 if listener. 0 if just a hop
	private boolean isWatching=false;
	private double lastTimePlayed=0;
	
	private StreamProperties props; ////////properties of current stream channel. what i received, etc.
	private Message broadcastMsg;
	private int urgentRequest=0;
	private Message m; //temp urgent msg
	private boolean isListener=false;
	
	private long lastChunkRequested=-1;
	private double lastTimeRequested = 0;
	private boolean isFirst=true;
	private double lastChokeInterval = 0;
	private double lastOptimalInterval=0;
	private int maximumRequestPerNode;
	private int bufferring = 0;
	private boolean stalled=true;
	
	private HashMap<DTNHost, ArrayList<Long>> neighborData;
	private HashMap<DTNHost, ArrayList<Long>> availableNeighbors; //neighbors that we can request from
	private HashMap<Long, Double> chunkRequest;
	private DTNHost broadcasterAddress;
	
	public WatcherAppV3(Settings s) {
		super(s);
		
		this.watcherType = s.getInt(WATCHER_TYPE);
		props = new StreamProperties("");
		neighborData = new HashMap<DTNHost, ArrayList<Long>>();
		availableNeighbors = new HashMap<DTNHost, ArrayList<Long>>();
		chunkRequest = new HashMap<Long, Double>();
		initUnchoke();
	}

	public WatcherAppV3(WatcherAppV3 a) {
		super(a);
		
		this.watcherType = a.getWatcherType();
		this.rng = new Random(this.seed);
		props = new StreamProperties("");
		neighborData = new HashMap<DTNHost, ArrayList<Long>>();
		availableNeighbors = new HashMap<DTNHost, ArrayList<Long>>();
		chunkRequest = new HashMap<Long, Double>();
		initUnchoke();
	}
	
	@Override
	public Message handle(Message msg, DTNHost host) {
		System.out.println("-----------------------------------------------------------------");
		
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
		if (type.equals(APP_TYPE)){
			String msg_type = (String) msg.getProperty("msg_type");
			
			if (msg_type.equals(BROADCAST_LIVE)){
				System.out.println(host +" Received a broadcast." + msg.getFrom());
//				String id = APP_TYPE+":register" + SimClock.getIntTime() + "-" +host.getAddress();
				
				if (!isWatching && watcherType==1){
					isWatching=true;
					status = WAITING;

					int timeStarted = (int) msg.getProperty("time_started");
					
					String streamID=(String) msg.getProperty("streamID");
					props.setStreamID(streamID);
					props.setStartTime(timeStarted);
//					props.setStartTime(SimClock.getIntTime());

					//send first requests here
					
//					if (lastChunkRequested==-1){
//						sendRequest(host, msg.getHops().get(msg.getHopCount()-1), 0, 14);
//						handleNode(msg, host, msg.getHops().get(msg.getHopCount()-1));
//					}
				}
				
				///for uninterested watcher, just save
				broadcastMsg = msg.replicate();
				broadcasterAddress = msg.getFrom();
				System.out.println("BROADCASTER ADDRESS: " + broadcasterAddress);
				lastChokeInterval = SimClock.getTime();
				lastOptimalInterval = SimClock.getTime();
				sendEventToListeners(StreamAppReport.BROADCAST_RECEIVED, SimClock.getTime(), host);
			}
			else if (msg_type.equals(HELLO)){
				try{
					System.out.println(host + " received hello from "+ msg.getFrom());
					
//					DTNHost sender = msg.getHops().get(msg.getHopCount()-1);
					
					int otherStatus = (int) msg.getProperty("status");
					long otherAck = (long) msg.getProperty("ack");
					ArrayList<Long> otherBuffermap = (ArrayList<Long>) msg.getProperty("buffermap");
					
					System.out.println(host + " received buffermap : " + otherBuffermap);
					
					System.out.println("otherStatus: "+otherStatus + " OtherAck: "+otherAck);
					if (broadcastMsg!=null && otherAck==-1 && otherStatus==-1
							&& otherBuffermap.isEmpty() ){ //if watcher does not know someone has a stream
						System.out.println(host + "Other node has no broadcast, sending a broadcast.");
						broadcastMsg.setTo(msg.getFrom());
						((TVProphetRouterV2) host.getRouter()).addUrgentMessage(broadcastMsg.replicate(), false);
						
						if (!sentHello.contains(msg.getFrom())){
							sendBuffermap(host, msg.getFrom(), props.getBuffermap()); 
							sentHello.add(msg.getFrom());
						}
					}
					
					else if (!otherBuffermap.isEmpty()){ //if othernode has chunks
//						System.out.println("@NOT OTHERBUFFERMAP EMPTY");
//						if (isFirst){ //if first time mkareceive hello
//							isFirst = false; //kun may rarest na
//						}
						
						updateChunksAvailable(msg.getFrom(), otherBuffermap); // save the buffermap received from this neighbor
						
						System.out.println("@received new. Available neighbors: " + availableNeighbors);
						
						//if we are unchoked from this node and we can get something from it
						if (availableNeighbors.containsKey(msg.getFrom()) && isInterested(otherAck,  otherBuffermap)){
							System.out.println(host + " Immediately requesting.");
							evaluateRequest(host, msg, isFirst); //evaluate what we can get based on latest updates
						}
						else{
							if (isInterested(otherAck,  otherBuffermap)){ //send interested for the first time
								System.out.println(host  + " Interested.");
								sendInterested(host, msg.getFrom(), true);
							}
							else if(availableNeighbors.containsKey(msg.getFrom())){ //if not interested but it previously unchoked us
								sendInterested(host, msg.getFrom(), false);
								availableNeighbors.remove(msg.getFrom());
								
							}
						}
					}
				}catch(NullPointerException e){
					e.printStackTrace();
				}
			}
			else if(msg_type.equalsIgnoreCase(BROADCAST_CHUNK_SENT)){ //received chunks				
				
				StreamChunk chunk = (StreamChunk) msg.getProperty("chunk");
				
				if (props.getBuffermap().size()==0){
//					System.out.println("FIRST CHUNK RECEIVED");
					sendEventToListeners(StreamAppReport.FIRST_TIME_RECEIVED, SimClock.getTime(), host);
				}
				
				if (props.getChunk(chunk.getChunkID())==null){ //if this is not a duplicate
					props.addChunk(chunk);			
					updateHello(host, chunk.getChunkID());
					sendEventToListeners(StreamAppReport.RECEIVED_CHUNK, chunk.getChunkID(), host);
					System.out.println(host + " updated:  " + props.getBuffermap());
				}
				else{
					sendEventToListeners(StreamAppReport.RECEIVED_DUPLICATE, chunk.getChunkID(), host);
				}
				
				System.out.println(host+ " received: "+chunk.getChunkID() + " for " + msg.getTo());
				chunkRequest.remove(chunk.getChunkID()); //remove granted requests
				
//				DTNHost sender = msg.getHops().get(msg.getHopCount()-1); //if an naghatag hini na message == broadcastMsg.getFrom
//				Connection curCon = getCurrConnection(host, sender);

				System.out.println("Ack Now: "+props.getAck()  +  " Received: "+chunk.getChunkID());
//				System.out.println("Sender " + sender);
				
//				if (msg.getTo() == host){
//					System.out.println("Props start time:" + props.getStartTime());
					if ( (chunk.getCreationTime() <= props.getStartTime())  && (props.getStartTime() < (chunk.getCreationTime() + Stream.getStreamInterval()))
						&& props.getAck()==-1){
//						System.out.println("First chunk received by " + host + ":" +chunk.getChunkID());
						props.setChunkStart(chunk.getChunkID());
						status = PLAYING;
						this.lastTimePlayed=SimClock.getTime();
					}
					else{	
						props.setAck(chunk.getChunkID());
					}
//				}
			}
			else if(msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){
				System.out.println(host + " received request from " +msg.getFrom());
			
				long chunkNeeded = (long) msg.getProperty("chunk");
				
				System.out.println("ReceivedRequest from "+msg.getFrom());
				if (props.getChunk(chunkNeeded)!=null){
					sendChunk(props.getChunk(chunkNeeded), host, msg.getFrom()); //simply sending. no buffer limit yet
				}
			}
			else if (msg_type.equals(INTERESTED)){
				System.out.println(host + " received INTERESTED from " + msg.getFrom());
				//evaluate response if choke or unchoke
				interestedNeighbors.put(msg.getFrom(), (int) msg.getCreationTime());
				System.out.println("Interested: " + interestedNeighbors);
				evaluateResponse(host, msg.getFrom());	
			}
			else if (msg_type.equals(UNCHOKE)){
				System.out.println(host + " received UNCHOKE from " + msg.getFrom());
				availableNeighbors.put(msg.getFrom(), (ArrayList<Long>) neighborData.get(msg.getFrom()).clone()); //add this to available neighbors
				evaluateRequest(host, msg, isFirst);
				ArrayList<DTNHost> availableH = new ArrayList<DTNHost>(availableNeighbors.keySet());
				sendEventToListeners(StreamAppReport.UPDATE_AVAILABLE_NEIGHBORS, availableH, host);
			}
			else if (msg_type.equals(CHOKE)){
				System.out.println(host + " received CHOKE from " + msg.getFrom());
				//remove didi an neighbors na dati nag unchoke ha at. diri na hya api ha mga dapat aruan
				availableNeighbors.remove(msg.getFrom());
				ArrayList<DTNHost> availableH = new ArrayList<DTNHost>(availableNeighbors.keySet());
				sendEventToListeners(StreamAppReport.UPDATE_AVAILABLE_NEIGHBORS, availableH, host);
			}
			else if (msg_type.equalsIgnoreCase(UNINTERESTED)){
				interestedNeighbors.remove(msg.getFrom());
				if (unchoked.contains(msg.getFrom())){
					unchoked.set(unchoked.indexOf(msg.getFrom()), null);
				}
			}
		}
		return msg;
	}

	@Override
	public void update(DTNHost host) {
		if (this.host ==null){
			this.host = host;
		}
		double curTime = SimClock.getTime();
		
		try{
			checkHelloedConnection(host);
			for (Connection c: host.getConnections()){
				DTNHost otherNode = c.getOtherNode(host);
				if (c.isUp() && !hasHelloed(otherNode)){
					sendBuffermap(host, otherNode, props.getBuffermap()); //say hello to new connections
					sentHello.add(otherNode);
				}
			}
		}catch(NullPointerException e){}
		
		try{
			if (isWatching && (curTime-this.lastTimePlayed >= Stream.getStreamInterval())){
//				System.out.println("++++++++++++++MUST BE PLAYING +++++++++++++++++");
				if(props.isReady(props.getNext())){ // && !stalled){ //if interrupted, wait for 10 seconds before playing again
					props.playNext();
					status = PLAYING;
					this.lastTimePlayed = curTime;
					System.out.println(host + " playing: " + props.getPlaying() + " time: "+lastTimePlayed);
					if (props.getPlaying() == 1) {
						sendEventToListeners(StreamAppReport.STARTED_PLAYING, lastTimePlayed, host);
					}
				}
				else if (status==PLAYING){
//					if (bufferring==0) sendEventToListeners(StreamAppReporter.INTERRUPTED, null, host);
//					stalled=true;
//					bufferring++;
					status = WAITING;
					sendEventToListeners(StreamAppReport.INTERRUPTED, null, host);
					
					System.out.println(host+ " waiting: "+ props.getNext());
//					//send request here again if request is expired. because last chunk requested did not arrive
//				
//					if (bufferring==10){
//						stalled=false;
//						bufferring=0;
//					}
				}
//				System.out.println("+++++++++++++++++++++++++++++++++++++++++++++");
			}
			
		}catch(NullPointerException e){
		}catch(ArrayIndexOutOfBoundsException i){ }
		
		//for maintaining -- choking and unchoking
		if (curTime-lastChokeInterval >=5){
			System.out.println("CHOKE INTERVALTRIGGERED!" + curTime);
			ArrayList<DTNHost> recognized =  new ArrayList<DTNHost>(interestedNeighbors.keySet());
			
			if (hasNewInterested()){
				
				System.out.println("Interested Nodes: " + recognized + " Unchoked: " + unchoked);
				ArrayList<DTNHost> prevUnchokedList = (ArrayList<DTNHost>) unchoked.clone();
				
				if (curTime-lastOptimalInterval >= 15){ //optimistic interval = every 15 seconds
					recognized.removeAll(unchoked); //remove first an nagrequest bisan unchoked na kanina [pa]. to avoid duplicates
					recognized.addAll(unchoked); 

					for (Iterator r = recognized.iterator(); r.hasNext(); ){
						if(r.next() == null){
							r.remove();
						}
					}
					
					recognized = sortNeighborsByBandwidth(recognized);
					for(DTNHost h : recognized){
						int speed = h.getInterface(1).getTransmitSpeed(h.getInterface(1));
						System.out.println(h + " : " + speed);
					}
							
					unchokeTop3(host, recognized);
					prevUnchokedList.removeAll(unchoked.subList(0, 3)); //remove an api na yana ha unchoke la gihap
			 		recognized.addAll(prevUnchokedList); //iapi an dati na nakaunchoke na diri na api ha top3 ha pag randomize
					
			 		/*
			 		 * api ha pag random yana an naapi ha unchoke kanina tapos diri na api yana ha newly unchoked
			 		 */
					unchokeRand(host, recognized, prevUnchokedList);
					prevUnchokedList.removeAll(unchoked); //remove utro an tanan na previous na api na ha newly unchoked
					
					/*
					 * nahibilin ha recognized an mga waray ka unchoke. 
					 * send CHOKE to mga api ha previous unchoked kanina na diri na api yana
					 */
					chokeOthers(host, prevUnchokedList); //-- don't  choke if it's not on curr unchokedList
					lastOptimalInterval = curTime;
				}
				
				//choke interval == 5 seconds for random
				else{
					recognized.add(unchoked.get(3));
					unchokeRand(host, recognized, prevUnchokedList); //an mga bag-o an pilii random
				}
			}
			
			lastChokeInterval = curTime;
			sendEventToListeners(StreamAppReport.UNCHOKED, unchoked.clone(), host);
			sendEventToListeners(StreamAppReport.INTERESTED, recognized.clone(), host);
//			System.out.println("Interested Nodes Now: " + recognized + " Unchoked Now: " + unchoked);
		}

	}

	private boolean isInterested(long ack, ArrayList<Long> otherHas){
		if (props.getAck() < ack || lastChunkRequested < ack){ //naive, not yet final
			return true;
		}
		for (long has: otherHas){
			if (!props.getBuffermap().contains(has)){ //if we don't have this
				return true;
			}
		}
		return false;
	}
	
	/*
	 * Evaluates what we should get from available neighbors.
	 */
	private void evaluateRequest(DTNHost host, Message msg, boolean isFirst){
//		System.out.println(host + "@ evaluate request----------------");
//		System.out.println(host + "TREEMAP OF CHUNK COUNT: " +getChunkCount());

//		ArrayList<DTNHost> currHosts = sortNeighborsByBandwidth(availableNeighbors.keySet());
		requestFromNeighbors(host, lastChunkRequested, msg.getFrom());
	}
	
	/*
	 * called everytime availableNeighbors is updated
	 */
	private void requestFromNeighbors(DTNHost host, long urgentChunk, DTNHost otherNode){ 
		System.out.println("@request from neighbors");
		long mostUrgent = urgentChunk+1; //request is in sequential form
		
		this.maximumRequestPerNode = MAXIMUM_PENDING_REQUEST/availableNeighbors.size(); //be sure what really happens with this
		ArrayList<Long> otherAvailable = availableNeighbors.get(otherNode);
		
		System.out.println("MaxRequestPerNode: " + maximumRequestPerNode+ " MostUrgent: " + mostUrgent+ " LastKey@Chunk: " + getChunkCount().lastKey() +"ChunkRequestSize: " +chunkRequest.size());

		for (int ctr=0; ctr<maximumRequestPerNode && mostUrgent<=getChunkCount().lastKey() && chunkRequest.size()<MAXIMUM_PENDING_REQUEST;
				mostUrgent++){
			
			if (otherAvailable.contains(mostUrgent)){
				sendRequest(host, otherNode, mostUrgent);
				ctr++;
			}
		}
		
		/*
		Map<DTNHost, ArrayList<Long>> map = new ConcurrentHashMap<DTNHost, ArrayList<Long>>(neighborsCopy);
	
		for (int ctr = 0 ; mostUrgent<=getChunkCount().lastKey() && ctr<MAXIMUM_PENDING_REQUEST && 
				!neighborsCopy.isEmpty() && chunkRequest.size()<MAXIMUM_PENDING_REQUEST; mostUrgent++){
	
			if (chunkRequest.containsKey(mostUrgent) || props.getChunk(mostUrgent)!=null){
				continue;
			}
			
//			System.out.println("Most urgent::: " + mostUrgent);
			for (DTNHost temp : map.keySet()){
//				System.out.println("Current available @ " + temp );
//				System.out.println(map.get(temp));
				
				if (map.get(temp).contains(mostUrgent)){
					sendRequest(host, temp, mostUrgent);

					try{
						requestCountPerNode.put(temp, requestCountPerNode.get(temp)+1);
					}catch(NullPointerException e){
						requestCountPerNode.put(temp, 1);
					}
					ctr++;
					
					if (requestCountPerNode.get(temp) >=maximumRequestPerNode){
						map.remove(temp); //remove from neighbors copy
					}
				}
			}
		}
		*/
	}

	private void sendBuffermap(DTNHost host, DTNHost to, ArrayList<Long> buffermap){  // ArrayList<Fragment> fragments){
		String id = APP_TYPE+ ":hello_" + SimClock.getIntTime() +"-" +host.getAddress() +"-" + to; // + SimClock.getIntTime();
		
//		System.out.println(host+ " sending HELLO!!!!!!!!!!!!!!!!!!!!!!!! at time " + SimClock.getIntTime() + " to " + to);
		Message m = new Message(host, to, id, BUFFERMAP_SIZE); //buffermap size must be defined.
		m.setAppID(APP_ID);
		m.addProperty("type", APP_TYPE);
		m.addProperty("msg_type", HELLO);
		m.addProperty("status", status);
		m.addProperty("buffermap", buffermap); 
		m.addProperty("ack", props.getAck());
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 5);
		host.createNewMessage(m);
//		m.setTtl(5);
		
//		try{
//			sentHello.put(to, buffermap.get(buffermap.size()-1));
//		}catch(ArrayIndexOutOfBoundsException e){
//			sentHello.put(to, props.getAck());
//		}
	}
	
	private void sendInterested(DTNHost host, DTNHost to, boolean isInterested) {
		System.out.println(host + " sending interested to " + to);
		
		String id;
		String msgType;
		if (isInterested){
			id = APP_TYPE + ":interested_" + SimClock.getIntTime() +"-" + host + "-" +to;
			msgType= INTERESTED;
		}
		else{
			id = APP_TYPE + ":uninterested_" + SimClock.getIntTime() + "-" + host + "-" +to;
			msgType=UNINTERESTED;
		}
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type",msgType);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 3);
		host.createNewMessage(m);

		sendEventToListeners(INTERESTED, null, host);
	}
	
	private void sendRequest(DTNHost host, DTNHost to, long chunkNeeded){
		String id = APP_TYPE + ":request_" + chunkNeeded  + "-" + host.getAddress();
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_REQUEST);
		m.addProperty("chunk", chunkNeeded);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);
//		m.setTtl(7 + MAX_REQUEST_PER_NODE);
		
		chunkRequest.put(chunkNeeded, (double) (SimClock.getIntTime()+WAITING_THRESHOLD)); //add to requested chunks
		lastChunkRequested = chunkNeeded;
		lastTimeRequested = SimClock.getTime();
//		System.out.println("Sent request to " + to);
		sendEventToListeners(StreamAppReport.SENT_REQUEST, null,host);
	}
	
	private void resendExpiredRequest(DTNHost host){
		double curTime = SimClock.getTime();
		for (long chunkID : chunkRequest.keySet()){
			if (chunkRequest.get(chunkID) <= curTime ){ // if request we sent is expired
				//request again
				
				for (DTNHost otherHost: sortNeighborsByBandwidth(availableNeighbors.keySet())){
					if (availableNeighbors.get(otherHost).contains(chunkID)) {
						sendRequest(host, otherHost, chunkID);
						//must put limit hanggang kailan dapat adi didi
						chunkRequest.put(chunkID, curTime + WAITING_THRESHOLD); //deadline is something seconds before it will be played
						sendEventToListeners(StreamAppReport.RESENT_REQUEST, null, host);
						break;
					}
				}
			}
		}
	}
	
	@Override
	protected void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to) {
		String id = APP_TYPE + ":chunk_" + chunk.getChunkID() +  " " + chunk.getCreationTime() +"-" +to + "-" + host.getAddress();
		
		Message m = new Message(host, to, id, (int) chunk.getSize());		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_CHUNK_SENT);
		m.addProperty("chunk", chunk);	
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);
			
		sendEventToListeners(CHUNK_DELIVERED, chunk, host);
	}

	public void updateHello(DTNHost host, long newChunk){
//		long currAck=props.getAck();
		
		for (DTNHost h : sentHello){
//			long lastChunkSent = sentHello.get(h);
			if (!h.equals(broadcasterAddress)){
				ArrayList<Long> latest = new ArrayList<Long>();
				latest.add(newChunk);
				sendBuffermap(host, h, latest);
				
//				int firstIndex = stream.getBuffermap().indexOf(lastChunkSent)+1;
//				int lastIndex = stream.getBuffermap().size();
//				ArrayList<Long> latestUpdates = new ArrayList<Long> (stream.getBuffermap().subList(firstIndex, lastIndex));
//				sentHello.put(h, stream.getChunk(lastIndex-1).getChunkID());
//				System.out.println("Last hello sent: " + stream.getChunk(lastIndex-1).getChunkID());
			}
		}
	}		

	
	public void updateChunksAvailable(DTNHost from, ArrayList<Long> newBuffermap){
		System.out.println("newbuffermap" + newBuffermap);
		ArrayList<Long> buffermap = null;
		try{
			buffermap = (ArrayList<Long>) neighborData.get(from);
			buffermap.addAll(newBuffermap); //put new buffermap
			System.out.println("BUFFERMAP updated: " + buffermap);
			neighborData.put(from, buffermap); 
			if (availableNeighbors.containsKey(from)){
				availableNeighbors.put(from, buffermap);
			}
			System.out.println("NEIGHBOR DATA: " + neighborData);
		}catch(Exception e){
			neighborData.put(from, newBuffermap);
		}
		updateChunkCount(newBuffermap); //increase count of chunks 
	}

	/*
	 * 
	 * CHOKE/UNCHOKE STARTS HERE ---------------------->
	 * 
	 */
	public void evaluateResponse(DTNHost host, DTNHost to){ //evaluate if we should choke or unchoke this node that sent INTERESTED at time before chokeInterval
		System.out.println("@ evaluating response");
		int ctr=0;
		try{
			while(unchoked.get(ctr)!=null && ctr<4){
				ctr++;
			}
		}catch(IndexOutOfBoundsException e){}
		
		if (ctr<4 && !unchoked.contains(to)){
			sendResponse(host, to, true);
			unchoked.set(ctr,to);
			sendEventToListeners(StreamAppReport.UNCHOKED, unchoked, host);
			System.out.println(host +" ADDED TO UNCHOKED: "+ to);
			interestedNeighbors.remove(to); //remove from interestedNeighbors since granted
		}
	}
	
	public void sendResponse(DTNHost host, DTNHost to, boolean isOkay){
		
		String id;
		String msgType; 

		if (isOkay){
			id = APP_TYPE + ":UNCHOKE_" + SimClock.getIntTime() + "-" + host.getAddress()  + "-" + to;
			msgType = UNCHOKE;
			System.out.println(host + " sending unchoke to " + to);
		}
		else{
			id = APP_TYPE + ":CHOKE_" + SimClock.getIntTime() + "-" + host.getAddress()  +"-" + to;
			msgType = CHOKE;
			System.out.println(host + " sending choke to " + to);
		}
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", msgType);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 4);
		host.createNewMessage(m);
	}
	
	private boolean hasNewInterested(){
		// count if an sulod han interested is same la ha mga unchoked
		if (interestedNeighbors.isEmpty()) return false;
			
		for (DTNHost node : interestedNeighbors.keySet()){
			if (!unchoked.contains(node)){
				return true;
			}
		}
		return false;
	}

	/*
	 * called every 15 seconds.
	 * after this method, unchoked list is updated. recognized includes those na diri na api ha top3
	 */
	private void unchokeTop3(DTNHost host, ArrayList<DTNHost> recognized){
		System.out.println("@top3---->");
		System.out.println("INITIAL    Recognized: " + recognized + " Unchoked: " + unchoked);
		if (recognized.isEmpty()) return;

		Iterator<DTNHost> i = recognized.iterator();
		
		
 		for (int ctr=0; ctr<3; ctr++){ //send UNCHOKE to top 3
			DTNHost other=null;
			try{
				other = i.next();	
				if (!unchoked.contains(other)){ //if diri hya api ha kanina na group of unchoked
					sendResponse(host, other, true); //send UNCHOKE
					interestedNeighbors.remove(other); //for those new interested
				}
				i.remove(); //notification granted, remove (for those at interested
			}catch(NoSuchElementException e){}
			updateUnchoked(ctr, other);
		}
	}	
	
	/*
	 * called every 5 seconds. Get a random node to be unchoked that is not included in the top3
	 * @param recognized interestedNeighbors that are not included in the top 3
	 * 
	 */
	private void unchokeRand(DTNHost host, ArrayList<DTNHost> recognized, ArrayList<DTNHost> prevUnchoked){ 	//every 5 seconds. i-sure na diri same han last //tas diri dapat api ha top3
		System.out.println("@rand---->");
		System.out.println("INITIAL    Recognized: " + recognized + " Unchoked: " + unchoked);
		if (recognized.isEmpty()) return;

		Random r = new Random();
		DTNHost prevRand;
		recognized.removeAll(unchoked.subList(0, 3)); //remove pagpili random an ada na ha unchoked list
		System.out.println("Recognized now without all those at unchoked:" + recognized);
		prevRand = unchoked.get(3);

		int index = r.nextInt(recognized.size()); //possible ini maging same han last random
		DTNHost randNode = recognized.get(index);
		System.out.println("index chosen: " + index);

		if (prevRand!=randNode){
			sendResponse(host, prevRand, false); //send CHOKE to this random node if it is not the same with new node
			recognized.remove(prevRand);
			if (!prevUnchoked.contains(randNode)){
				sendResponse(host, randNode, true); //sendUnchoke to this random node
			}
		}
		updateUnchoked(3, randNode);
		System.out.println("UNCHOOOOOKKKKEEED=== " + unchoked + " @ time: " + SimClock.getIntTime());
		interestedNeighbors.remove(randNode);  //notification granted. remove on original interested list
		recognized.remove(randNode); //notification granted. remove on recognized list
	}
	
	private void chokeOthers(DTNHost host, ArrayList<DTNHost> recognized){
		//sendChoke to tanan na nabilin
		for (DTNHost r : recognized){
			sendResponse(host, r, false); 
		}
	}
	
	private void initUnchoke(){
		for (int i=0; i<4; i++){
			unchoked.add(i, null);
		}
	}
	
	public int getWatcherType(){
		return watcherType;
	}
	
	@Override
	public Application replicate() {
		return new WatcherAppV3(this);
	}

}
