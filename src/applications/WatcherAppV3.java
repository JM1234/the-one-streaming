package applications;

import java.util.ArrayList;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

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

	private ArrayList<Long> listOfRequested;
	private DTNHost broadcasterAddress;
	
	private ArrayList<Long> temp ;
	
	public WatcherAppV3(Settings s) {
		super(s);
		
		this.watcherType = s.getInt(WATCHER_TYPE);
		props = new StreamProperties("");
		neighborData = new HashMap<DTNHost, ArrayList<Long>>();
		availableNeighbors = new HashMap<DTNHost, ArrayList<Long>>();
		chunkRequest = new HashMap<Long, Double>();
		listOfRequested = new ArrayList<Long>();
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
		listOfRequested = new ArrayList<Long>();
		initUnchoke();
	}
	
	@Override
	public Message handle(Message msg, DTNHost host) {
		
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
		if (type.equals(APP_TYPE)){
			System.out.println("-------------------------------------------------------------------------------");

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

				}
				
				///for uninterested watcher, just save
				broadcastMsg = msg.replicate();
				broadcasterAddress = msg.getFrom();
//				System.out.println("BROADCASTER ADDRESS: " + broadcasterAddress);
				lastChokeInterval = SimClock.getTime();
				lastOptimalInterval = SimClock.getTime();
				sendEventToListeners(StreamAppReport.BROADCAST_RECEIVED, SimClock.getTime(), host);
			}
			else if (msg_type.equals(HELLO)){
//				try{
					System.out.println(host + " received hello from "+ msg.getFrom());
					
//					DTNHost sender = msg.getHops().get(msg.getHopCount()-1);
					
					int otherStatus = (int) msg.getProperty("status");
					long otherAck = (long) msg.getProperty("ack");
					ArrayList<Long> otherBuffermap = (ArrayList<Long>) msg.getProperty("buffermap");
					
					System.out.println(host + " received buffermap : " + otherBuffermap);
					
//					System.out.println("otherStatus: "+otherStatus + " OtherAck: "+otherAck);
					if (broadcastMsg!=null && otherAck==-1 && otherStatus==-1
							&& otherBuffermap.isEmpty() ){ //if watcher does not know someone has a stream
//						System.out.println(host + "Other node has no broadcast, sending a broadcast.");
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
						
//						System.out.println("@received new. Available neighbors: " + availableNeighbors);
						
						//if we are unchoked from this node and we can get something from it
						if (availableNeighbors.containsKey(msg.getFrom()) && isInterested(otherAck,  otherBuffermap)){
//							System.out.println(host + " Immediately requesting.");
//							sendEventToListeners("hostname", msg.getFrom(), host);
//							sendEventToListeners("update", availableNeighbors.get(msg.getFrom()).clone(), host);
							evaluateRequest(host, msg, isFirst); //evaluate what we can get based on latest updates
						}
						else{
							ArrayList<Long> temp = (ArrayList<Long>) neighborData.get(msg.getFrom()).clone();
							if (isInterested(otherAck,  temp)){ //send interested
//								System.out.println(host  + " Interested.");
								sendInterested(host, msg.getFrom(), true);
							}
							else if(availableNeighbors.containsKey(msg.getFrom())){ //if not interested but it previously unchoked us
								sendInterested(host, msg.getFrom(), false);
								availableNeighbors.remove(msg.getFrom());
							}
						}
					}
//				}catch(NullPointerException e){
//					e.printStackTrace();
//				}
			}
			else if(msg_type.equalsIgnoreCase(BROADCAST_CHUNK_SENT)){ //received chunks				
				
				StreamChunk chunk = (StreamChunk) msg.getProperty("chunk");
				
				if (props.getBuffermap().size()==0){
//					System.out.println("FIRST CHUNK RECEIVED");
					sendEventToListeners(StreamAppReport.FIRST_TIME_RECEIVED, SimClock.getTime(), host);
				}
				
				if (props.getChunk(chunk.getChunkID())==null){ //if this is not a duplicate
					props.addChunk(chunk);			
					props.setAck(chunk.getChunkID());
					sendEventToListeners(StreamAppReport.UPDATE_ACK, props.getAck(), host);
					sendEventToListeners(StreamAppReport.RECEIVED_CHUNK, chunk.getChunkID(), host);
					updateHello(host, chunk.getChunkID());
					System.out.println(host + " updated:  " + props.getBuffermap());
				}
				else{
					sendEventToListeners(StreamAppReport.RECEIVED_DUPLICATE, chunk.getChunkID(), host);
				}
				
				System.out.println(host+ " received: "+chunk.getChunkID() + " for " + msg.getTo());
				chunkRequest.remove(chunk.getChunkID()); //remove granted requests

				System.out.println("Ack Now: "+props.getAck()  +  " Received: "+chunk.getChunkID());
				
//					System.out.println("Props start time:" + props.getStartTime());
				if ( (chunk.getCreationTime() <= props.getStartTime())  && (props.getStartTime() < (chunk.getCreationTime() + Stream.getStreamInterval()))
					&& props.getAck()==-1){
//						System.out.println("First chunk received by " + host + ":" +chunk.getChunkID());
					props.setChunkStart(chunk.getChunkID());
					status = PLAYING;
					this.lastTimePlayed=SimClock.getTime();
				}
			}
			else if(msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){
				System.out.println(host + " received request from " +msg.getFrom());
			
				long chunkNeeded = (long) msg.getProperty("chunk");
				
//				System.out.println("ReceivedRequest from "+msg.getFrom());
				if (props.getChunk(chunkNeeded)!=null){
					sendChunk(props.getChunk(chunkNeeded), host, msg.getFrom()); //simply sending. no buffer limit yet
				}
			}
			else if (msg_type.equals(INTERESTED)){
				System.out.println(host + " received INTERESTED from " + msg.getFrom());
				//evaluate response if choke or unchoke
				interestedNeighbors.put(msg.getFrom(), (int) msg.getCreationTime());
//				System.out.println("Interested: " + interestedNeighbors);
				evaluateResponse(host, msg.getFrom());
				
				System.out.println( host + "INTERESTED NODES: " +interestedNeighbors + " Unchoked: " + unchoked);
			}
			else if (msg_type.equals(UNCHOKE)){
				System.out.println(host + " received UNCHOKE from " + msg.getFrom());
				ArrayList<Long> temp = (ArrayList<Long>) msg.getProperty("buffermap");
				availableNeighbors.put(msg.getFrom(), (ArrayList<Long>) temp.clone());//(ArrayList<Long>) neighborData.get(msg.getFrom()).clone()); //add this to available neighbors
				
				/*
				 * 
				 */
//				sendEventToListeners("hostname", msg.getFrom(), host);
//				sendEventToListeners("update", availableNeighbors.get(msg.getFrom()).clone(), host);
				//end
				
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
			List<Connection> hostConnection = host.getConnections(); 
			hostConnection.removeAll(sentHello); //get hosts we haven't said hello to yet
			for (Connection c: hostConnection){
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
//					System.out.println(host + " playing: " + props.getPlaying() + " time: "+lastTimePlayed);
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
					
//					System.out.println(host+ " waiting: "+ props.getNext());
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
//			System.out.println("CHOKE INTERVALTRIGGERED!" + curTime);
			ArrayList<DTNHost> recognized =  new ArrayList<DTNHost>(interestedNeighbors.keySet());
			
			if (hasNewInterested()){
				
//				System.out.println("Interested Nodes: " + recognized + " Unchoked: " + unchoked);
				ArrayList<DTNHost> prevUnchokedList = (ArrayList<DTNHost>) unchoked.clone();
				
				if (curTime-lastOptimalInterval >= 15){ //optimistic interval = every 15 seconds
					recognized.removeAll(unchoked); //remove first an nagrequest bisan unchoked na kanina [pa]. to avoid duplicates
					recognized.addAll(unchoked); 

					for (Iterator r = recognized.iterator(); r.hasNext(); ){
						if(r.next() == null){
							r.remove();
						}
					}
//					System.out.println(host + " Recognized " + recognized);
					recognized = sortNeighborsByBandwidth(recognized);

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
		}

	}

	private boolean isInterested(long ack, ArrayList<Long> otherHas){
		if (props.getAck() < ack){ //naive, not yet final
			return true;
		}
		ArrayList<Long> temp = otherHas;
		temp.removeAll(props.getBuffermap());
		temp.removeAll(listOfRequested);

//		for (long has: otherHas){
//			if (!props.getBuffermap().contains(has)){ //if we don't have this
//				return true;
//			}
//		}
//		return false;
		return !temp.isEmpty();
	}
	
	/*
	 * Evaluates what we should get from available neighbors.
	 */
	private void evaluateRequest(DTNHost host, Message msg, boolean isFirst){
		requestFromNeighbors(host,msg.getFrom());
	}
	
	/*
	 * called everytime availableNeighbors is updated
	 */
	private void requestFromNeighbors(DTNHost host, DTNHost otherNode){ 
//		System.out.println("@request from neighbors");
//		long mostUrgent = urgentChunk+1; //request is in sequential form
		
//		this.maximumRequestPerNode = MAXIMUM_PENDING_REQUEST/availableNeighbors.size(); //be sure what really happens with this
		ArrayList<Long> otherAvailable = new ArrayList (availableNeighbors.get(otherNode)); //.clone();
		
		sendEventToListeners("hostname", otherNode, host);
		sendEventToListeners("update", otherAvailable.clone(), host);
		
//		System.out.println(host + "original buffermap list: " + otherAvailable);
		otherAvailable.removeAll(props.getBuffermap());
		otherAvailable.removeAll(listOfRequested); //remove chunks that we already requested
		Collections.sort(otherAvailable);
		
//		System.out.println(host + " requesting from this list: " + otherAvailable);
//		System.out.println(host +  " ack: " + props.getAck());
//		System.out.println(host  + " THIS IS OLAAA: " + otherAvailable);
		
		int ctr=0;
		for (long chunk: otherAvailable){
			if (chunkRequest.size()<MAXIMUM_PENDING_REQUEST){ //ctr<maximumRequestPerNode && 
				sendRequest(host, otherNode, chunk);
				chunkRequest.put(chunk, (double) (SimClock.getIntTime()+WAITING_THRESHOLD)); //add to requested chunks
				listOfRequested.add(chunk);
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
//		System.out.println(host + " sending interested to " + to);
		
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
		String id = APP_TYPE + ":request_" + chunkNeeded  + "-" + host.getAddress()+"-"+ to;
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_REQUEST);
		m.addProperty("chunk", chunkNeeded);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);
//		m.setTtl(7 + MAX_REQUEST_PER_NODE);
		
//		lastTimeRequested = SimClock.getTime();
//		System.out.println("Sent request to " + to);
		sendEventToListeners(StreamAppReport.SENT_REQUEST, chunkNeeded,host);
	}
	
//	private void resendExpiredRequest(DTNHost host){
//		double curTime = SimClock.getTime();
//		for (long chunkID : chunkRequest.keySet()){
//			if (chunkRequest.get(chunkID) <= curTime ){ // if request we sent is expired
//				//request again
//				
//				for (DTNHost otherHost: sortNeighborsByBandwidth(availableNeighbors.keySet())){
//					if (availableNeighbors.get(otherHost).contains(chunkID)) {
//						sendRequest(host, otherHost, chunkID);
//						//must put limit hanggang kailan dapat adi didi
//						chunkRequest.put(chunkID, curTime + WAITING_THRESHOLD); //deadline is something seconds before it will be played
//						sendEventToListeners(StreamAppReport.RESENT_REQUEST, null, host);
//						break;
//					}
//				}
//			}
//		}
//	}
	
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
		int ctrNeighbors=0;
		ArrayList<Long> latest = new ArrayList<Long>();
		latest.add(newChunk);
		
//		long currAck=props.getAck();
//		System.out.println(host + " RECEIVED NEW: " + newChunk);
//		for (DTNHost h : sentHello){
//			long lastChunkSent = sentHello.get(h);
//			if (!h.equals(broadcasterAddress)){
//				sendBuffermap(host, h, latest);
//				int firstIndex = stream.getBuffermap().indexOf(lastChunkSent)+1;
//				int lastIndex = stream.getBuffermap().size();
//				ArrayList<Long> latestUpdates = new ArrayList<Long> (stream.getBuffermap().subList(firstIndex, lastIndex));
//				sentHello.put(h, stream.getChunk(lastIndex-1).getChunkID());
//				System.out.println("Last hello sent: " + stream.getChunk(lastIndex-1).getChunkID());
//			}
//		}
		
		for (DTNHost h: unchoked){
			if (h!=null){
				sendBuffermap(host, h, latest);
				ctrNeighbors++;
			}
		}
		
		
		for (DTNHost h: sentHello){
			if (!h.equals(broadcasterAddress) && !unchoked.contains(h)){
				sendBuffermap(host, h, latest);
				ctrNeighbors++;
//				if (ctrNeighbors>=8)
//					break;
			}
		}
	}		

	
	public void updateChunksAvailable(DTNHost from, ArrayList<Long> newBuffermap){
//		System.out.println("newbuffermap" + newBuffermap);
		ArrayList<Long> buffermap = null;
		
		if (neighborData.containsKey(from)){
			buffermap = (ArrayList<Long>) neighborData.get(from).clone();
//			System.out.println("OLD BUFFERMAP: " + buffermap);
			buffermap.addAll(newBuffermap); //put new buffermap
//			System.out.println("UPDATED BUFFERMAP: " + buffermap);
			neighborData.put(from, (ArrayList<Long>) buffermap.clone()); 
		}
		else{
			neighborData.put(from, (ArrayList<Long>) newBuffermap.clone());
		}
		
		if (availableNeighbors.containsKey(from)){
			availableNeighbors.put(from, buffermap);
		}
//		System.out.println(host + "NEIGHBOR DATA: " + neighborData);
		updateChunkCount(newBuffermap); //increase count of chunks
		
	}

	/*
	 * 
	 * CHOKE/UNCHOKE STARTS HERE ---------------------->
	 * 
	 */
	public void evaluateResponse(DTNHost host, DTNHost to){ //evaluate if we should choke or unchoke this node that sent INTERESTED at time before chokeInterval
//		System.out.println("@ evaluating response");
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
//			System.out.println(host +" ADDED TO UNCHOKED: "+ to);
			interestedNeighbors.remove(to); //remove from interestedNeighbors since granted
		}
	}
	
	public void sendResponse(DTNHost host, DTNHost to, boolean isOkay){
		
		String id;
		String msgType; 

		if (isOkay){
			id = APP_TYPE + ":UNCHOKE_" + SimClock.getIntTime() + "-" + host.getAddress()  + "-" + to;
			msgType = UNCHOKE;
//			System.out.println(host + " sending unchoke to " + to);
		}
		else{
			id = APP_TYPE + ":CHOKE_" + SimClock.getIntTime() + "-" + host.getAddress()  +"-" + to;
			msgType = CHOKE;
//			System.out.println(host + " sending choke to " + to);
		}
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", msgType);
		m.addProperty("buffermap", props.getBuffermap());
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 4);
		host.createNewMessage(m);
	}
	
	private boolean hasNewInterested(){
		// count if an sulod han interested is same la ha mga unchoked
		if (interestedNeighbors.isEmpty()) return false;
			
		ArrayList<DTNHost> nh = new ArrayList<DTNHost> (interestedNeighbors.keySet());
		nh.removeAll(unchoked);
		
		return !nh.isEmpty();
//		for (DTNHost node : interestedNeighbors.keySet()){
//			if (!unchoked.contains(node)){
//				return true;
//			}
//		}
//		return false;
	}

	/*
	 * called every 15 seconds.
	 * after this method, unchoked list is updated. recognized includes those na diri na api ha top3
	 */
	private void unchokeTop3(DTNHost host, ArrayList<DTNHost> recognized){
//		System.out.println("@top3---->");
//		System.out.println("INITIAL    Recognized: " + recognized + " Unchoked: " + unchoked);
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
//		System.out.println("@rand---->");
//		System.out.println("INITIAL    Recognized: " + recognized + " Unchoked: " + unchoked);
		if (recognized.isEmpty()) return;

		Random r = new Random();
		DTNHost prevRand;
		recognized.removeAll(unchoked.subList(0, 3)); //remove pagpili random an ada na ha unchoked list
		prevRand = unchoked.get(3);
//		System.out.println("Recognized now without all those at unchoked:" + recognized);
		
		int index = r.nextInt(recognized.size()); //possible ini maging same han last random
		DTNHost randNode = recognized.get(index);
//		System.out.println("index chosen: " + index);

		if (prevRand!=randNode){
			sendResponse(host, prevRand, false); //send CHOKE to this random node if it is not the same with new node
			recognized.remove(prevRand);
			if (!prevUnchoked.contains(randNode)){
				sendResponse(host, randNode, true); //sendUnchoke to this random node
			}
		}
		updateUnchoked(3, randNode);
//		System.out.println("UNCHOOOOOKKKKEEED=== " + unchoked + " @ time: " + SimClock.getIntTime());
		interestedNeighbors.remove(randNode);  //notification granted. remove on original interested list
		recognized.remove(randNode); //notification granted. remove on recognized list
	}
	
	private void chokeOthers(DTNHost host, ArrayList<DTNHost> recognized){
		//sendChoke to tanan na nabilin
		for (DTNHost r : recognized){
			if (r!=null)
				sendResponse(host, r, false); 
		}
	}
	
	private void initUnchoke(){
		temp = new ArrayList<Long>();
		temp.add(null);
		
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
