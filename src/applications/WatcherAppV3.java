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
					
//					System.out.println("otherStatus: "+otherStatus + " OtherAck: "+otherAck);
					if (broadcastMsg!=null && otherAck==-1 && otherStatus==-1
							&& otherBuffermap.isEmpty() ){ //if watcher does not know someone has a stream
						System.out.println(host + "Other node has no broadcast, sending a broadcast.");
						broadcastMsg.setTo(msg.getFrom());
						((TVProphetRouterV2) host.getRouter()).addUrgentMessage(broadcastMsg.replicate(), false);
						
						if (!sentHello.containsKey(msg.getFrom())){
							sendBuffermap(host, msg.getFrom(), props.getBufferMap()); 
							sentHello.put(msg.getFrom(), props.getAck());
						}
					}
					
					else if (!otherBuffermap.isEmpty()){ //if othernode has chunks
//						System.out.println("@NOT OTHERBUFFERMAP EMPTY");
						if (isFirst){ //if first time mkareceive hello
							isFirst = false; //nangalimot ak han gamit hini
						}
						updateChunksAvailable(msg.getFrom(), otherBuffermap); // save the buffermap received from this neighbor
						
//						System.out.println("Availableneighbors: " + availableNeighbors);
						
						//if we are unchoked from this node and we can get something from it
						if (availableNeighbors.containsKey(msg.getFrom()) && isInterested(otherAck,  otherBuffermap)){
							evaluateRequest(host, msg, isFirst); //evaluate what we can get based on latest updates
						}
						else{
							if (isInterested(otherAck,  otherBuffermap)){ //send interested for the first time
								sendInterested(host, msg.getFrom(), true);
							}
							else if(availableNeighbors.containsKey(msg.getFrom())){ //if not interested but it previously unchoked us
								sendInterested(host, msg.getFrom(), false);
							}
						}
					}
				}catch(NullPointerException e){
					e.printStackTrace();
				}
			}
			
			else if(msg_type.equalsIgnoreCase(BROADCAST_CHUNK_SENT)){ //received chunks				
				
				StreamChunk chunk = (StreamChunk) msg.getProperty("chunk");
				
				if (props.getBufferMap().size()==0){
//					System.out.println("FIRST CHUNK RECEIVED");
					sendEventToListeners(StreamAppReport.FIRST_TIME_RECEIVED, SimClock.getTime(), host);
				}
				
				if (props.getChunk(chunk.getChunkID())==null){
					props.addChunk(chunk);					
				}
				else{
//					System.out.println("RECEIVED duplicate!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!1");
					sendEventToListeners(StreamAppReport.RECEIVED_DUPLICATE, chunk.getChunkID(), host);
				}
				
				System.out.println(host+ " received: "+chunk.getChunkID() + " for " + msg.getTo());
				chunkRequest.remove(chunk.getChunkID()); //remove granted requests
				
//				DTNHost sender = msg.getHops().get(msg.getHopCount()-1); //if an naghatag hini na message == broadcastMsg.getFrom
//				Connection curCon = getCurrConnection(host, sender);
//				
//				System.out.println("Ack Now: "+props.getAck()  +  " Received: "+chunk.getChunkID());
//				System.out.println("Sender " + sender);
//				
				if (msg.getTo() == host){
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
				}
				System.out.println(host + " updated:  " + props.getBufferMap());
				sendEventToListeners(StreamAppReport.RECEIVED_CHUNK, chunk.getChunkID(), host);
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
				evaluateResponse(host, msg.getFrom());	
			}
			else if (msg_type.equals(UNCHOKE)){
				System.out.println(host + " received UNCHOKE from " + msg.getFrom());
				availableNeighbors.put(msg.getFrom(), neighborData.get(msg.getFrom())); //add this to available neighbors
				evaluateRequest(host, msg, isFirst);
			}
			else if (msg_type.equals(CHOKE)){
				System.out.println(host + " received CHOKE from " + msg.getFrom());
				//remove didi an neighbors na dati nag unchoke ha at. diri na hya api ha mga dapat aruan
				availableNeighbors.remove(msg.getFrom());
			}
		}
		return msg;
	}

	@Override
	public void update(DTNHost host) {
		double curTime = SimClock.getTime();
		
		try{
			checkHelloedConnection(host);
			for (Connection c: host.getConnections()){
				DTNHost otherNode = c.getOtherNode(host);
				if (c.isUp() && !hasHelloed(otherNode)){
					sendBuffermap(host, otherNode, props.getBufferMap()); //say hello to new connections
				}
			}
		}catch(NullPointerException e){}
		
		try{
			if (isWatching && (curTime-this.lastTimePlayed >= Stream.getStreamInterval())){
				System.out.println("++++++++++++++MUST BE PLAYING +++++++++++++++++");
				if(props.isReady(props.getNext())){ // && !stalled){ //if interrupted, wait for 10 seconds before playing again
					props.playNext();
					status = PLAYING;
					this.lastTimePlayed = curTime;
					System.out.println(host + " playing: " + props.getPlaying() + " time: "+lastTimePlayed);
					if (props.getPlaying() == 1) {
						sendEventToListeners(StreamAppReport.STARTED_PLAYING, lastTimePlayed, host);
					}
				}
				else {
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
				System.out.println("+++++++++++++++++++++++++++++++++++++++++++++");
			}
			
//			updateHello(host);
//			checkExpiredRequest(host);
			
			//for maintaining -- choking and unchoking
//			if ( ((curTime - lastChokeInterval) % 5) == 0){
				
//				System.out.println("INTERESTED NEIGHBORS: " + interestedNeighbors.keySet());
				
//				if (hasNewInterested()){
//					
//					ArrayList<DTNHost> recognized = getRecognized();
//					removeUninterested(host, recognized);
//					
////					System.out.println("RECOGNIZED NODES: " + recognized);
//					
//					if(!recognized.isEmpty()){
//						if (curTime-lastChokeInterval >= 15){
//							unchokeTop3(host, recognized);
//							unchokeRand(host, recognized);
//							chokeOthers(host, recognized);
//							lastChokeInterval = curTime;
//						}
//						else if (!recognized.isEmpty()){
//							unchokeRand(host, recognized);
//						}
//					}
//				}

//				System.out.println("UNCHOKED: "+ unchoked);
//			}
			
		}catch(NullPointerException e){
		}catch(ArrayIndexOutOfBoundsException i){ }
	}

	private boolean isInterested(long ack, ArrayList<Long> otherHas){
		if (props.getAck() < ack || lastChunkRequested < ack){ //naive, not yet final
			return true;
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
		
//		//Chunk requested per node: 5. Starting from start of need
//		for (int counter=0, h=0; counter<4 && chunkRequest.size()<MAXIMUM_PENDING_REQUEST; counter++) { //allowed to ask only up to 4 hosts
//			ArrayList<Long> curr = (ArrayList<Long>) availableNeighbors.get(currHosts.get(h)).clone();
//			Collections.sort(curr);
//			int reqCount=0;
//			
//			for (long id: curr){
//
//				if (lastChunkRequested < id){
//					//sendRequestforthisChunk that is available from the node
//					System.out.println("Requested: " + id + " to: " + currHosts.get(h));
//					sendRequest(host, currHosts.get(h), id);
//					lastChunkRequested = id;
//					reqCount++;
//				}
//				if (reqCount==MAX_REQUEST_PER_NODE) {
////					System.out.println("sent 5. next.");
//					break;
//				}
//			}
//			h = h<currHosts.size()-1 ? h+1:0;
//		}
		
		//aggregate buffermaps of currently "unchoked" nodes
		//tapos didto makuha han rarest.

//		ArrayList<Long> chunksAvailable = (ArrayList<Long>) msg.getProperty("buffermap");
//		long sID = chunksAvailable.get(0);
//		long eID = chunksAvailable.get(chunksAvailable.size()-1);
//		
//		if (!isWatching){ //if not yet watching, request for the rarest chunk
//			//while combinedchunksoftop3 has no rarest, getNextRarest
//			
//			if (super.getChunkCount().size()==1) {
//				ArrayList<DTNHost> sortedHost= super.sortNeighborsByBandwidth(neighborData.keySet());
//				//get a random chunk on the node with the highest bandwidth
//				getRandomID(neighborData.get(sortedHost.get(0)));
//				
//			}
//			else{
//				long rarest = getRarest(sID, eID).get(0); //////and something we need
//				System.out.println("Rarest chunk requested: "  + rarest);
//				sendRequest(host, msg.getFrom(), rarest);	//get rarest chunk
//				/////after getting rarest, check if the top3 neighbors have these
//			}	
//		}
//		
//		else{
//			//get sliding window
//			//get rarest chunk from sliding window
//		}
	}
	
//	/*
//	 * Get the rarest chunk within this range (inclusive)
//	 * @param sID the first id of the range to search for
//	 * @param eID the last id of the range to search for
//	 * 
//	 */

//	private long getRarest(ArrayList<Long> arrayList){
//		
//		NavigableMap<Long, Integer> map = getChunkCount().descendingMap();
//		for(Entry<Long, Integer> entry : map.entrySet()) {
//			  long key = entry.getKey();
//			  if (key<lastChunkRequested){
//				  continue;
//			  }
//			  if (arrayList.contains(key)){
//				  return key;
//			  }
//			}
//		//if wala nang choice
//		return getRandomID(arrayList);
//	}
//
////	/*
////	 * @param arrayList chunks of nodes with highest bandwidth
////	 */
//	private long getRandomID(ArrayList<Long> arrayList){ 
////		//get a random chunk from the buffermap of nodes that we are currently in connection with
//		Random r = new Random();
//		int temp = r.nextInt(arrayList.size());
//////		temp = ThreadLocalRandom.current().nextLong(sID, eID + 1);
//		return arrayList.get(temp);
//	}

	/*
	 * called everytime availableNeighbors is updated
	 */
	private void requestFromNeighbors(DTNHost host, long urgentChunk, DTNHost otherNode){ 
		System.out.println("@request from neighbors");
		long mostUrgent = urgentChunk;
		
//		HashMap<DTNHost, ArrayList<Long>> neighborsCopy = (HashMap<DTNHost, ArrayList<Long>>) availableNeighbors.clone();
//		HashMap<DTNHost, Integer> requestCountPerNode = new HashMap<DTNHost, Integer>();
		this.maximumRequestPerNode = MAXIMUM_PENDING_REQUEST/availableNeighbors.size(); //be sure what really happens with this
		
		ArrayList<Long> otherAvailable= availableNeighbors.get(otherNode);
		
		for (int ctr=0; ctr<maximumRequestPerNode && mostUrgent<=getChunkCount().lastKey() && chunkRequest.size()<MAXIMUM_PENDING_REQUEST; mostUrgent++){
			
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
		try{
			sentHello.put(to, buffermap.get(buffermap.size()-1));
		}catch(ArrayIndexOutOfBoundsException e){
			sentHello.put(to, props.getAck());
		}
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
		String id = APP_TYPE + ":chunk_" + chunk.getChunkID()+  " " + chunk.getCreationTime() +"-" +to;
		
		Message m = new Message(host, to, id, (int) chunk.getSize());		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_CHUNK_SENT);
		m.addProperty("chunk", chunk);	
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);
			
		sendEventToListeners(CHUNK_DELIVERED, chunk, host);
	}

//	public void updateHello(DTNHost host){
//		int curTime = SimClock.getIntTime();
//		
//		for (DTNHost h : sentHello.keySet()){
//			if (curTime - sentHello.get(h) >= HELLO_UPDATE && //if it's time to send an updated HELLO
//					(!getCurrConnection(host, h).isTransferring())){ //or if nothing is happening in the connection (i.e, we are not sending anything)
//				System.out.println(host +" sending an updated hello to "+ h + " @ " + curTime);
//				sendBuffermap(host, h);  
//				sentHello.put(h, props.getAck());
//			}
//		}
//	}
	
	public void updateChunksAvailable(DTNHost from, ArrayList<Long> newBuffermap){
//		System.out.println("newbuffermap" + newBuffermap);
		ArrayList<Long> buffermap = null;
		try{
			buffermap = (ArrayList<Long>) neighborData.get(from);
			buffermap.addAll(newBuffermap); //put new buffermap
//			neighborData.put(from, buffermap); 
		}catch(Exception e){
			neighborData.put(from, newBuffermap);
		}
		
		System.out.println("Neighbor Data: "+neighborData);
		
//		if (buffermap!=null){ //used when full buffermap was always sent
//			newBuffermap.removeAll(buffermap); //extract new buffermap only
//			System.out.println(from + " oldbuffermap: "+buffermap);
//			System.out.println(from + " now: " + newBuffermap);
//		}
//		System.out.println("HERE");
		updateChunkCount(newBuffermap); //increase count of chunks 
//		System.out.println("Neighbordata: "+neighborData);
	}

	public void sendResponse(DTNHost host, DTNHost to, boolean isOkay){
		String id;
		String msgType; 

		if (isOkay){
			id = APP_TYPE + ":UNCHOKE_" + SimClock.getIntTime() + "-" + to;
			msgType = UNCHOKE;
		}
		else{
			id = APP_TYPE + ":CHOKE_" + SimClock.getIntTime() + "-" + to;
			msgType = CHOKE;
		}
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", msgType);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 4);
		host.createNewMessage(m);
	}
	
	/*
	 * 	
	 * CHOKE/UNCHOKE STARTS HERE ---------------------->
	 * 
	 */
	public void evaluateResponse(DTNHost host, DTNHost to){ //evaluate if we should choke or unchoke this node that sent INTERESTED
		System.out.println("@ evaluating response");
		int ctr=0;
		while(unchoked.get(ctr)!=null && ctr<3){
			ctr++;
		}
		if (interestedNeighbors.size()<3 && ctr<3 && !unchoked.contains(to)){
			sendResponse(host, to, true);
			unchoked.set(ctr,to);
			System.out.println(host +" ADDED TO UNCHOKED: "+ to);
			interestedNeighbors.remove(to); //remove from interested neighbors since we already sent unchoke to it
		}
	}

	private void removeUninterested(DTNHost host, ArrayList<DTNHost> recognized){
		
		for (int i=0; i<4; i++){
			if (!recognized.contains(unchoked.get(i)) && unchoked.get(i)!=null){
				sendResponse(host, unchoked.get(i), false); //send CHOKE to nodes that are uninterested but in our unchoked before
				unchoked.set(i, null);
			}
		}
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
	
	private ArrayList<DTNHost> getRecognized(){
		int curTime = SimClock.getIntTime();
		ArrayList<DTNHost> recognized = new ArrayList<DTNHost>(); //save here the recent INTERESTED requests
	
		//extract recent INTERESTED messages, delete an diri recent
		Iterator<Map.Entry<DTNHost, Integer>> entryIt = interestedNeighbors.entrySet().iterator();
		while(entryIt.hasNext()){
			Entry<DTNHost, Integer> entry = entryIt.next();
			if ( (curTime - entry.getValue()) <= 10 ){ //irerecognize ko la an mga nagsend interested for the past 10 seconds
				recognized.add(entry.getKey());
			}
			else{
				entryIt.remove();
			}
		}
		sortNeighborsByBandwidth(recognized);
		return recognized;
	}
	
	/*
	 * called every 15 seconds.
	 */
	private void unchokeTop3(DTNHost host, ArrayList<DTNHost> recognized){
		if (recognized.isEmpty()) return;

		Iterator<DTNHost> i = recognized.iterator();
		for (int ctr=0; ctr<3; ctr++){ //send UNCHOKE to top 3
			DTNHost other=null;
			try{
				other = i.next();	
				sendResponse(host, other, true); //send UNCHOKE
				i.remove(); //notification granted, remove
			}catch(NoSuchElementException e){}
			System.out.println("ctr: "+ctr);
			unchoked.set(ctr, other);	
		}
	}	
	
	/*
	 * called every 5 seconds. Get a random node to be unchoked that is not included in the top3
	 * @param recognized interestedNeighbors that are not included in the top 3
	 * 
	 */
	private void unchokeRand(DTNHost host, ArrayList<DTNHost> recognized){ 	//every 5 seconds. i-sure na diri same han last //tas diri dapat api ha top3
		if (recognized.isEmpty()) return;

		Random r = new Random();
		int index = r.nextInt(recognized.size()); //possible ini maging same han last random
		DTNHost randNode = recognized.get(index);
		DTNHost prevRand;
		
		try{
			recognized.removeAll(unchoked.subList(0, 3)); //remove pagpili random an ada na ha unchoked list
			prevRand = unchoked.get(3);
			
			if (prevRand!=randNode){
				sendResponse(host, prevRand, false); //send CHOKE to this random node if it is not the same with new node
			}
		}catch(IndexOutOfBoundsException i){}

		updateUnchoked(3, randNode);
		sendResponse(host, randNode, true); //sendUnchoke to this random node
		interestedNeighbors.remove(randNode);  //notification granted. remove
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
