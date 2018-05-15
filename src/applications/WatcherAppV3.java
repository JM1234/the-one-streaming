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
import fragmentation.Fragment;
import fragmentation.SADFragmentation;
import report.StreamAppReporter;
import routing.TVProphetRouterV2;
import streaming.Stream;
import streaming.StreamChunk;
import streaming.StreamProperties;

public class WatcherAppV3 extends StreamingApplication{

	public static final String WATCHER_TYPE = "watcherType"; 
	
	public static final int PLAYING = 1;
	public static final int WAITING = 0;
	public static final int MAXIMUM_PENDING_REQUEST=SADFragmentation.NO_OF_CHUNKS_PER_FRAG*2 + 50;
	public static final int PREBUFFER = 10;
	public static final int WAITING_THRESHOLD = 7; //based on paper
	
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private Random	rng;
	
	private int 	status =-1;
	private int 	watcherType; //1 if listener. 0 if just a hop
	private boolean isWatching=false;
	private double lastTimePlayed=0;
	
	private StreamProperties props; ////////properties of current stream channel. what i received, etc.
//	private Message broadcastMsg;
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
	private HashMap<Integer, ArrayList<StreamChunk>> toFragment;
	
	private ArrayList<Long> listOfRequested;
	private DTNHost broadcasterAddress;
	private SADFragmentation sadf;
	
	private ArrayList<Long> temp ;
	
	public WatcherAppV3(Settings s) {
		super(s);
		
		this.watcherType = s.getInt(WATCHER_TYPE);
		props = new StreamProperties("");
		neighborData = new HashMap<DTNHost, ArrayList<Long>>();
		availableNeighbors = new HashMap<DTNHost, ArrayList<Long>>();
		chunkRequest = new HashMap<Long, Double>();
		listOfRequested = new ArrayList<Long>();
		sadf = new SADFragmentation();
		toFragment = new HashMap<Integer, ArrayList<StreamChunk>>();
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
		sadf = new SADFragmentation();
		toFragment = new HashMap<Integer, ArrayList<StreamChunk>>();
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
//				broadcastMsg = msg.replicate();
				broadcasterAddress = (DTNHost) msg.getProperty("source");
				System.out.println(" broadcaster address: " + broadcasterAddress);
				lastChokeInterval = SimClock.getTime();
				lastOptimalInterval = SimClock.getTime();
				sendEventToListeners(StreamAppReporter.BROADCAST_RECEIVED, SimClock.getTime(), host);
			}
			
			else if (msg_type.equals(HELLO)){
				System.out.println(host + " received hello from "+ msg.getFrom());
				
				int otherStatus = (int) msg.getProperty("status");
				long otherAck = (long) msg.getProperty("ack");
				ArrayList<Long> otherBuffermap = (ArrayList<Long>) msg.getProperty("buffermap");
				
				System.out.println(host + " received buffermap : " + otherBuffermap);
				
//				System.out.println("otherStatus: "+otherStatus + " OtherAck: "+otherAck);
				if (broadcasterAddress!=null && otherAck==-1 && otherStatus==-1
						&& otherBuffermap.isEmpty() ){ //if watcher does not know someone has a stream
//						System.out.println(host + "Other node has no broadcast, sending a broadcast.");
					
					sendBroadcast(host, msg.getFrom(), broadcasterAddress);
					
//					if (!sentHello.contains(msg.getFrom())){
//						sendBuffermap(host, msg.getFrom(), props.getBuffermap()); 
//						sentHello.add(msg.getFrom());
//					}


//					helloSent.get(msg.getFrom());
//					if (!helloSent.containsKey(msg.getFrom())){
						sendBuffermap(host, msg.getFrom(), props.getBuffermap()); 
						helloSent.put(msg.getFrom(), props.getBuffermap());
//					}
				}
				
				else if (!otherBuffermap.isEmpty() && broadcasterAddress!=null){ //if othernode has chunks
					updateChunksAvailable(msg.getFrom(), otherBuffermap); // save the buffermap received from this neighbor
				
					//if we are unchoked from this node and we can get something from it
					if (availableNeighbors.containsKey(msg.getFrom()) && isInterested(otherAck,  otherBuffermap)){
						evaluateRequest(host, msg); //evaluate what we can get based on latest updates
					}
					else{
						ArrayList<Long> temp = (ArrayList<Long>) neighborData.get(msg.getFrom()).clone();
						if (isInterested(otherAck,  temp)){ //send interested
							sendInterested(host, msg.getFrom(), true);
						}
						else if(availableNeighbors.containsKey(msg.getFrom())){ //if not interested but it previously unchoked us
							sendInterested(host, msg.getFrom(), false);
							availableNeighbors.remove(msg.getFrom());
						}
					}
				}
				else{
					if (!hasHelloed(msg.getFrom())){
						sendBuffermap(host, msg.getFrom(), props.getBuffermap()); 
						helloSent.put(msg.getFrom(), props.getBuffermap());
					}
				}
			}
			
			else if(msg_type.equalsIgnoreCase(BROADCAST_CHUNK_SENT)){ //received chunks				
				StreamChunk chunk = (StreamChunk) msg.getProperty("chunk");
				System.out.println(host + " received chunk" + chunk.getChunkID() + ":" + chunk.getFragmentIndex());
				int fragId = chunk.getFragmentIndex();
				
				//for fragmenting chunks that are individually received
				if (props.getChunk(chunk.getChunkID())==null){
					
					//save on toFragment
					if (toFragment.containsKey(fragId)){
						toFragment.get(chunk.getFragmentIndex()).add(chunk);
					}
					else{
						ArrayList<StreamChunk> temp  = new ArrayList<StreamChunk>();
						temp.add(chunk);
						toFragment.put(fragId, temp);
					}
					
					if (!sadf.doesExist(fragId)){
//						if (toFragment.get(fragId).size() == SADFragmentation.NO_OF_CHUNKS_PER_FRAG){
						sadf.initTransLevelFrag(fragId);
					}
					//check if we should include it on fragment now
					if (sadf.doesExist(fragId) && !sadf.getFragment(fragId).isComplete()){
						ArrayList<StreamChunk> hold = toFragment.get(fragId);
						for (StreamChunk c: hold){
							System.out.println(" pos: " + ((int)c.getChunkID())%SADFragmentation.NO_OF_CHUNKS_PER_FRAG);
							sadf.addChunkToFragment(fragId, ((int)c.getChunkID())%SADFragmentation.NO_OF_CHUNKS_PER_FRAG, c);
//							toFragment.get(fragId).remove(c);
						}
						
						if (sadf.getFragment(fragId).isComplete()){
							sendEventToListeners(StreamAppReporter.FRAGMENT_CREATED, null, host);
						}
						
						toFragment.remove(fragId);
					}		
				}
				
				interpretChunks(host, chunk, msg.getFrom());
			}
			
			else if(msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){
				System.out.println(host + " received request from " +msg.getFrom());
//
//				long chunkNeeded = (long) msg.getProperty("chunk");
//				
//				if (props.getChunk(chunkNeeded)!=null){
//					sendChunk(props.getChunk(chunkNeeded), host, msg.getFrom()); //simply sending. no buffer limit yet
//				}
				
				evaluateToSend(host, msg);
//				sendWithoutFrag(host, msg);
			}

			else if (msg_type.equals(INTERESTED)){ 	//evaluate response if choke or unchoke
				System.out.println(host + " received INTERESTED from " + msg.getFrom());
			
				interestedNeighbors.put(msg.getFrom(), (int) msg.getCreationTime());
				evaluateResponse(host, msg.getFrom());
			}
			
			else if (msg_type.equals(UNCHOKE)){
				System.out.println(host + " received UNCHOKE from " + msg.getFrom());

				ArrayList<Long> temp = (ArrayList<Long>) msg.getProperty("buffermap");
				updateChunksAvailable(msg.getFrom(), temp);
				availableNeighbors.put(msg.getFrom(), (ArrayList<Long>) neighborData.get(msg.getFrom()).clone()); //add this to available neighbors
				
				evaluateRequest(host, msg);
				ArrayList<DTNHost> availableH = new ArrayList<DTNHost>(availableNeighbors.keySet());
				sendEventToListeners(StreamAppReporter.UPDATE_AVAILABLE_NEIGHBORS, availableH, host);
			}

			else if (msg_type.equals(CHOKE)){
				System.out.println(host + " received CHOKE from " + msg.getFrom());
				//remove didi an neighbors na dati nag unchoke ha at. diri na hya api ha mga dapat aruan
				availableNeighbors.remove(msg.getFrom());
				ArrayList<DTNHost> availableH = new ArrayList<DTNHost>(availableNeighbors.keySet());
				sendEventToListeners(StreamAppReporter.UPDATE_AVAILABLE_NEIGHBORS, availableH, host);
			}
			
			else if (msg_type.equalsIgnoreCase(UNINTERESTED)){
				interestedNeighbors.remove(msg.getFrom());
				if (unchoked.contains(msg.getFrom())){
					unchoked.set(unchoked.indexOf(msg.getFrom()), null);
				}
			}
			
			else if(msg_type.equalsIgnoreCase(BROADCAST_FRAGMENT_SENT)){
				Fragment frag = (Fragment) msg.getProperty("fragment");
				int fragType = (int) msg.getProperty("frag_type");
				
				System.out.println(host + " received frag " + frag.getId() + " fragType: "+fragType);
				
				decodeFragment(host, frag, fragType, msg.getFrom());
				
			}
		}
		return msg;
	}

	private void decodeFragment(DTNHost host, Fragment frag, int fragType, DTNHost from) {

		if (fragType == SADFragmentation.INDEX_LEVEL){
			
			System.out.println(" full fragment received. " + frag.getId());
			sadf.createFragment(frag.getId(), frag.getBundled());
			sendEventToListeners(StreamAppReporter.FRAGMENT_CREATED, null, host);
			sadf.getFragment(frag.getId()).setIndexComplete();
			
			for (StreamChunk c: frag.getBundled()){
				interpretChunks(host, c, from);
			}
		}

		else{
			ArrayList<StreamChunk> bundle = frag.getBundled();
			int currFrag = frag.getId();
			
			System.out.println( host + " trans frag received " + currFrag + ": " + frag.getStartPosition() + " : " + frag.getEndPosition());

			if (!sadf.doesExist(currFrag)){
				sadf.initTransLevelFrag(frag.getId());
				System.out.println(host + " fragment not yet existing: " + currFrag + ". To frag:  " + toFragment.get(currFrag) );
//				for (StreamChunk c: toFragment.get(currFrag)){
//					sadf.addChunkToFragment(currFrag, ((int)c.getChunkID())%SADFragmentation.NO_OF_CHUNKS_PER_FRAG, c);
//				}
//				toFragment.remove(currFrag);
			}

			for (int i=0, pos = frag.getStartPosition(); pos<=frag.getEndPosition(); pos++, i++){
				sadf.addChunkToFragment(currFrag, pos, bundle.get(i));
				interpretChunks(host, bundle.get(i), from);
			}
			
			if (sadf.getFragment(frag.getId()).isComplete()){
				sendEventToListeners(StreamAppReporter.FRAGMENT_CREATED, null, host);
			}
		}
		
	}

	private void interpretChunks(DTNHost host, StreamChunk chunk, DTNHost from){
		System.out.println(host + " received chunk " + chunk.getChunkID());
		if (props.getBuffermap().size()==0){ //first time received
			sendEventToListeners(StreamAppReporter.FIRST_TIME_RECEIVED, SimClock.getTime(), host);
		}
		
		if (props.getChunk(chunk.getChunkID())==null){ //if this is not a duplicate
			props.addChunk(chunk);			
			props.setAck(chunk.getChunkID());
			sendEventToListeners(StreamAppReporter.UPDATE_ACK, props.getAck(), host);
			sendEventToListeners(StreamAppReporter.RECEIVED_CHUNK, chunk.getChunkID(), host);
			updateHello(host, chunk.getChunkID(), from);
			System.out.println(host + " updated:  " + props.getBuffermap());
		}
		else{
			sendEventToListeners(StreamAppReporter.RECEIVED_DUPLICATE, chunk.getChunkID(), host);
		}

		chunkRequest.remove(chunk.getChunkID()); //remove granted requests
		if (chunkRequest.size() <= MAXIMUM_PENDING_REQUEST/2 && !availableNeighbors.isEmpty()){
			timeToAskNew(host);
		}
		
//		System.out.println("Ack Now: "+props.getAck()  +  " Received: "+chunk.getChunkID());
		if ( (chunk.getCreationTime() <= props.getStartTime())  && (props.getStartTime() < (chunk.getCreationTime() + Stream.getStreamInterval()))
			&& props.getAck()==-1){
			props.setChunkStart(chunk.getChunkID());
			status = PLAYING;
			this.lastTimePlayed=SimClock.getTime();
			sendEventToListeners(StreamAppReporter.STARTED_PLAYING, lastTimePlayed, host);
		}
	}
	
	@Override
	public void update(DTNHost host) {

		double curTime = SimClock.getTime();
		
		checkHelloedConnection(host);
		removeExpiredRequest();
		
		try{
			List<Connection> hostConnection = host.getConnections(); 
			hostConnection.removeAll(helloSent.keySet()); //get hosts we haven't said hello to yet
			for (Connection c: hostConnection){
				DTNHost otherNode = c.getOtherNode(host);
				if (c.isUp() && !hasHelloed(otherNode)){
					sendBuffermap(host, otherNode, props.getBuffermap()); //say hello to new connections
//					sentHello.add(otherNode);
					helloSent.put(otherNode, props.getBuffermap());
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
					sendEventToListeners(StreamAppReporter.LAST_PLAYED, lastTimePlayed, host);
				}
				else { //status==PLAYING){
					//hope for the best na aaruon utro ini na missing
					System.out.println(" Waiting for: " + props.getNext());
					status = WAITING;
					sendEventToListeners(StreamAppReporter.INTERRUPTED, null, host);
					
//					//send request here again if request is expired. because last chunk requested did not arrive

				}
				System.out.println("+++++++++++++++++++++++++++++++++++++++++++++");
			}
			
		}catch(NullPointerException e){
		}catch(ArrayIndexOutOfBoundsException i){ }
		
		//for maintaining -- choking and unchoking
		if (curTime-lastChokeInterval >=5){
			ArrayList<DTNHost> recognized =  new ArrayList<DTNHost>(interestedNeighbors.keySet());
			
			if (hasNewInterested()){
				
				ArrayList<DTNHost> prevUnchokedList = (ArrayList<DTNHost>) unchoked.clone();
				
				if (curTime-lastOptimalInterval >= 15){ //optimistic interval = every 15 seconds
					recognized.removeAll(unchoked); //remove first an nagrequest bisan unchoked na kanina [pa]. to avoid duplicates
					recognized.addAll(unchoked); 
					
					for (Iterator r = recognized.iterator(); r.hasNext(); ){ //remove null values, unchoked may have null values
						if(r.next() == null){
							r.remove();
						}
					}
					recognized = sortNeighborsByBandwidth(recognized);
							
					unchokeTop3(host, recognized);
					prevUnchokedList.removeAll(unchoked.subList(0, 3)); //remove an api kanina na diri na api yana ha top3
//			 		recognized.addAll(prevUnchokedList); //iapi an dati na nakaunchoke na diri na api ha top3 ha pag randomize
					
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
			sendEventToListeners(StreamAppReporter.UNCHOKED, unchoked.clone(), host);
			sendEventToListeners(StreamAppReporter.INTERESTED, recognized.clone(), host);
//			System.out.println("Interested Nodes Now: " + recognized + " Unchoked Now: " + unchoked);
		}

	}

	public void sendBroadcast(DTNHost host, DTNHost to, DTNHost broadcasterAddress){
		String id = APP_TYPE + ":broadcast" + SimClock.getIntTime() + "-" + host.getAddress() + "-" + to;
		
		Message m= new Message(host, to, id, SIMPLE_MSG_SIZE);
		m.addProperty("type", APP_TYPE);
		m.addProperty("msg_type", BROADCAST_LIVE);
		m.addProperty("streamID", getStreamID());
		m.addProperty("stream_name", "tempstream");
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 1);
		m.addProperty("time_started", SimClock.getIntTime());
		m.addProperty("source", broadcasterAddress);
		m.setAppID(APP_ID);
		host.createNewMessage(m); //must override, meaning start a broadcast that a stream is initiated from this peer
		//////set response size
		
		lastChokeInterval = SimClock.getTime();
		lastOptimalInterval = SimClock.getTime();
		super.sendEventToListeners(BROADCAST_LIVE, null, host);
	}
	
	private void timeToAskNew(DTNHost host){
		System.out.println(host + " time to ask new. chunkrequestsize: " + chunkRequest.size());
		
		ArrayList<DTNHost> hosts = new ArrayList<DTNHost>(availableNeighbors.keySet());
		Collections.sort(hosts, StreamingApplication.BandwidthComparator);
		
		int maxRequestPerNode = MAXIMUM_PENDING_REQUEST/hosts.size();
		ArrayList<Long> toRequest = new ArrayList<Long>();
		
		for (DTNHost to: hosts){
		
			ArrayList<Long> aChunks = new ArrayList<Long> (availableNeighbors.get(to));
			aChunks.removeAll(props.getBuffermap());
			aChunks.removeAll(listOfRequested);
			toRequest.clear();
			System.out.println("achunks size: " + aChunks.size());
			
			for (int i=0; toRequest.size()<maxRequestPerNode && i<aChunks.size() && chunkRequest.size()<MAXIMUM_PENDING_REQUEST; i++){
				long chunkId = aChunks.get(i);
				
				toRequest.add(chunkId);
				
				//expiry = 10 seconds before this will be played
				double expiry = (((chunkId*StreamChunk.getDuration()) - (props.getPlaying()*StreamChunk.getDuration()))+
						SimClock.getTime()) - WAITING_THRESHOLD;
				if (expiry <= SimClock.getTime() ){
					expiry = SimClock.getTime() + PREBUFFER;
				}
				chunkRequest.put(chunkId, expiry); //add to requested chunks
				listOfRequested.add(chunkId);
			}
			
			if (!toRequest.isEmpty()){
				System.out.println(host + " asking to: " + to + " Chunks: " + toRequest);
				sendRequest(host,to, (ArrayList<Long>) (toRequest.clone()));
				sendEventToListeners(StreamAppReporter.SENT_REQUEST, toRequest.clone(),host); //di pa ak sure kun diin ini dapat
			}
		}
		
	}
	
	private boolean isInterested(long ack, ArrayList<Long> otherHas){
		if (props.getAck() < ack){ //naive, not yet final
			return true;
		}
		ArrayList<Long> temp = otherHas;
		temp.removeAll(props.getBuffermap());
		temp.removeAll(listOfRequested);
		return !temp.isEmpty();
	}
	
	/*
	 * Evaluates what we should get from available neighbors.
	 */
	private void evaluateRequest(DTNHost host, Message msg){
		requestFromNeighbors(host,msg.getFrom());
	}
	
	/*
	 * called everytime availableNeighbors is updated
	 */
	private void requestFromNeighbors(DTNHost host, DTNHost otherNode){ 
//		System.out.println("@request from neighbors");
		
		ArrayList<Long> toRequest = new ArrayList<>();
		
		this.maximumRequestPerNode = MAXIMUM_PENDING_REQUEST/availableNeighbors.size(); //be sure what really happens with this.
		ArrayList<Long> otherAvailable = new ArrayList (availableNeighbors.get(otherNode));
		
		sendEventToListeners("hostname", otherNode, host);
		sendEventToListeners("update", otherAvailable.clone(), host);
		
		otherAvailable.removeAll(props.getBuffermap());
		otherAvailable.removeAll(listOfRequested); //remove chunks that we already requested
		Collections.sort(otherAvailable);
		
		int ctr=0;
		for (long chunk: otherAvailable){
			if (chunkRequest.size()<MAXIMUM_PENDING_REQUEST && ctr<maximumRequestPerNode){ 
				toRequest.add(chunk);

				//expiry = 10 seconds before this will be played
				double expiry = (((chunk*StreamChunk.getDuration()) - (props.getPlaying()*StreamChunk.getDuration()))+
						SimClock.getTime()) - WAITING_THRESHOLD;
				if (expiry <= SimClock.getTime() ){
					expiry = SimClock.getTime() + PREBUFFER;
				}
				chunkRequest.put(chunk, expiry); //add to requested chunks
				listOfRequested.add(chunk);
				ctr++;
			}
		}
		if (!toRequest.isEmpty()){
			sendRequest(host,otherNode, toRequest);
			sendEventToListeners(StreamAppReporter.SENT_REQUEST, toRequest ,host); //di pa ak sure kun diin ini dapat
		}
	}

	private void sendBuffermap(DTNHost host, DTNHost to, ArrayList<Long> buffermap){  // ArrayList<Fragment> fragments){
		String id = APP_TYPE+ ":hello_" + SimClock.getTime() +"-" +host.getAddress() +"-" + to; // + SimClock.getIntTime();
		
		Message m = new Message(host, to, id, BUFFERMAP_SIZE); //buffermap size must be defined.
		m.setAppID(APP_ID);
		m.addProperty("type", APP_TYPE);
		m.addProperty("msg_type", HELLO);
		m.addProperty("status", status);
		m.addProperty("buffermap", buffermap); 
		m.addProperty("ack", props.getAck());
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 5);
		host.createNewMessage(m);
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
	
	private void sendRequest(DTNHost host, DTNHost to, ArrayList<Long> chunkNeeded){
		String id = APP_TYPE + ":request_" + chunkNeeded  + "-" + host.getAddress()+"-"+ to;
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_REQUEST);
		m.addProperty("chunk", chunkNeeded);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);
//		m.setTtl(7 + MAX_REQUEST_PER_NODE);
		
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
	
	private void removeExpiredRequest(){
		double curTime = SimClock.getTime();
		Iterator<Long> i = chunkRequest.keySet().iterator();
		while(i.hasNext()){
			long chunkID = i.next();
			if (chunkRequest.get(chunkID)<= curTime){ //if the request we sent is expired
				listOfRequested.remove(chunkID);
				i.remove();
				System.out.println(" expired request: " + chunkID);
			}
		}
	}
	

	private void sendWithoutFrag(DTNHost host, Message msg){
		ArrayList<Long> request = (ArrayList<Long>) msg.getProperty("chunk");
		ArrayList<Long> bundled = new ArrayList<Long>();
		
		for (long rChunk: request){
			sendChunk(props.getChunk(rChunk), host, msg.getFrom());
		}
	}
	
	private void evaluateToSend(DTNHost host, Message msg) {
		ArrayList<Long> request = (ArrayList<Long>) msg.getProperty("chunk");
		ArrayList<Long> bundled = new ArrayList<Long>();
		
		System.out.println("Request size " + request.size());
		
		long rChunk;
		int currFrag;
		
		while(!request.isEmpty()){
			rChunk = request.get(0);
			currFrag = props.getChunk(rChunk).getFragmentIndex();
			bundled.clear();
			
			if (sadf.doesExist(currFrag) && sadf.getFragment(currFrag).isComplete()){ //if diri pa complete an index level, cannot send transmission level
				
				long fIndex = sadf.getFragment(currFrag).getFirstChunkID();
				long eIndex = sadf.getFragment(currFrag).getEndChunk();
				
				System.out.println(host + " fragment exists " + currFrag);
				
				if (request.contains(fIndex) && request.contains(eIndex) && //for full index request
					request.subList(request.indexOf(fIndex), request.indexOf(eIndex)+1).size() == sadf.getFragment(currFrag).getNoOfChunks()){
						System.out.println("Full fragment requested.");	
						
						if (isGreaterThanTrans(host, msg.getFrom(), sadf.getFragment(currFrag).getSize())){
							//fragment with respect to trans size
							System.out.println( host + " GREATER THAN TRANS YEYYYYYYYYYY!!!!!");
							subFragment(host, msg.getFrom(), sadf.getFragment(currFrag).getBundled(), currFrag);
						}
						else{
							sendIndexFragment(host, msg.getFrom(), sadf.getFragment(currFrag));
							request.removeAll(request.subList(request.indexOf(fIndex), request.indexOf(eIndex)+1));
						}
				}
				else{
					System.out.println("Trans level requested");
					long prevChunk= rChunk;
					Iterator<Long> iter = request.iterator();
					
					double transSize = 	getTransSize(host, msg.getFrom());
					double byteRate = StreamChunk.getByterate();
					double currSize;
					
					while(iter.hasNext()){
						long currChunk = iter.next();
						currSize = (bundled.size()*byteRate) + HEADER_SIZE;
					
						if(currFrag == props.getChunk(currChunk).getFragmentIndex() && (bundled.isEmpty() || currChunk==prevChunk+1)
								&& currSize < transSize){
							bundled.add(currChunk);
							prevChunk = currChunk;
							iter.remove();
						}
						else{
							if (currSize < transSize){
								sendEventToListeners(StreamAppReporter.SIZE_ADJUSTED, null, host);
							}
							break;
						}
					}
					
//					System.out.println("CurrFrag: " + currFrag + " start: "+ start+ " end: " + end);
					if (!bundled.isEmpty() && bundled.size()>1){ //nasend fragment bisan usa la it sulod
						//if tapos na, send this part of request_response 
						int start = sadf.getFragment(currFrag).indexOf(bundled.get(0));
						int end = sadf.getFragment(currFrag).indexOf(bundled.get(bundled.size()-1));
						
						ArrayList<StreamChunk> bundle = new ArrayList<StreamChunk> (sadf.getFragment(currFrag).getBundled().subList(start, end+1));					
						Fragment fragment = new Fragment(currFrag, bundle);
						fragment.setStartPosition(start);
						fragment.setEndPosition(end);
						sendTransFragment(host, msg.getFrom(), fragment);
					}
					else if (bundled.size()==1){ //limit trans level == 2 chunks fragmented
						sendChunk(props.getChunk(bundled.get(0)), host, msg.getFrom());
					}
				}
			}
			
			else{ // if fragment does not exists
				sendChunk(props.getChunk(rChunk), host, msg.getFrom());
				request.remove(request.indexOf(rChunk));
			}
		}
	}
	
	/*
	 * called if total chunks to send is greater than translevel
	 */
	private void subFragment(DTNHost host, DTNHost to, ArrayList<StreamChunk> bundle, int currFrag){
		
		ArrayList<Long> bundled = new ArrayList<Long>();
		
		double transSize = 	getTransSize(host, to);
		double byteRate = StreamChunk.getByterate();
		double currSize;
		long prevChunk;
		
		while(!bundle.isEmpty()){
			Iterator<StreamChunk> iter = bundle.iterator();
			bundled.clear();
			prevChunk = bundle.get(0).getChunkID();
			
			while(iter.hasNext()){
				long currChunk = iter.next().getChunkID();
				currSize = ((bundled.size()+1)*byteRate) + HEADER_SIZE;
			
				if(currFrag == props.getChunk(currChunk).getFragmentIndex() && (bundled.isEmpty() || currChunk==prevChunk+1)
						&& currSize < transSize){
					bundled.add(currChunk);
					prevChunk = currChunk;
					iter.remove();
				}
				else{
					if (!bundled.isEmpty() && bundled.size()>1){ //nasend fragment bisan usa la it sulod
						//if tapos na, send this part of request_response 
						int start = sadf.getFragment(currFrag).indexOf(bundled.get(0));
						int end = sadf.getFragment(currFrag).indexOf(bundled.get(bundled.size()-1));
						
						ArrayList<StreamChunk> subFrag= new ArrayList<StreamChunk> (sadf.getFragment(currFrag).getBundled().subList(start, end+1));					
						Fragment fragment = new Fragment(currFrag, subFrag);
						fragment.setStartPosition(start);
						fragment.setEndPosition(end);
						sendTransFragment(host, to, fragment);
						sendEventToListeners(StreamAppReporter.SIZE_ADJUSTED, null, host);
					}
					else if (bundled.size()==1){ //limit trans level == 2 chunks fragmented
						sendChunk(props.getChunk(bundled.get(0)), host, to);
					}
				}
			}
		}
	}
	
	private boolean isGreaterThanTrans(DTNHost host, DTNHost to, double size){
		
		double transSize = ((TVProphetRouterV2) host.getRouter()).getTransmissionPreds(to);
		return size>transSize;
	}
	
	public double getTransSize(DTNHost host, DTNHost to){
		return 	((TVProphetRouterV2) host.getRouter()).getTransmissionPreds(to);
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
			
		sendEventToListeners(StreamAppReporter.SENT_CHUNK, chunk, host);
	}

	private void sendIndexFragment(DTNHost host, DTNHost to, Fragment frag) {
		System.out.println(host + " sending index level to " + to + " fragID: " + frag.getId());
		
		String id = APP_TYPE + ":fragment_" + SimClock.getTime() + "-"+  frag.getId() + "-" + to + "-" + host.getAddress();
		
		Message m = new Message(host, to, id,  (int) (frag.getSize()+HEADER_SIZE));		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_FRAGMENT_SENT);
		m.addProperty("frag_type", SADFragmentation.INDEX_LEVEL);
		m.addProperty("fragment", frag);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);

		sendEventToListeners(StreamAppReporter.SENT_INDEX_FRAGMENT, null, host);
//		sendEventToListeners(FRAGMENT_DELIVERED, null, host);
		
		System.out.println( host+ " transmission preds: " + ((TVProphetRouterV2) host.getRouter()).getTransmissionPreds(to));
		System.out.println(host + " frag size: " + frag.getSize());
	}
	
	private void sendTransFragment(DTNHost host, DTNHost to, Fragment frag) {
		///dapat nacreate ini new fragment na instance tas an sulod la is an firstPos to lastPos
		
//		System.out.println(host + " sending trans level to " + to + " fragId:" + fragId );
		
		String id = APP_TYPE + ":fragment_"  + SimClock.getTime() + "-" + frag.getId() + "-" + to + "-" + host.getAddress();

//		Fragment frag = fragment.getFragment(fragId);
//		frag.setStartPosition(firstPos);
//		frag.setEndPosition(lastPos);

		Message m = new Message(host, to, id,  (int) (frag.getSize()+HEADER_SIZE));		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_FRAGMENT_SENT);
		m.addProperty("frag_type", SADFragmentation.TRANS_LEVEL);
		m.addProperty("fragment", frag);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);

		System.out.println( host+ " transmission preds: " + ((TVProphetRouterV2) host.getRouter()).getTransmissionPreds(to));
		System.out.println(host + " frag size: " + frag.getSize());
		
//		sendEventToListeners(FRAGMENT_DELIVERED, null, host);
		sendEventToListeners(StreamAppReporter.SENT_TRANS_FRAGMENT, null, host);
	}
	
	
	public void updateHello(DTNHost host, long newChunk, DTNHost from){
		checkHelloedConnection(host);
		
		ArrayList<Long> latest = new ArrayList<Long>();
		latest.add(newChunk);
		
		for (DTNHost h: unchoked){
			if (h!=null && !h.equals(from)) {
				sendBuffermap(host, h, (ArrayList<Long>) latest.clone());
				System.out.println(" h: " + h);
				try{
					helloSent.get(h).addAll(latest);
				}catch(NullPointerException e){
					this.updateUnchoked(unchoked.indexOf(h), null);
				}
			}
		}
		
		for (DTNHost h: helloSent.keySet()){
			if (!h.equals(broadcasterAddress) && !unchoked.contains(h) && !h.equals(from)){
				sendBuffermap(host, h, (ArrayList<Long>) latest.clone());
				helloSent.get(h).addAll(latest);
			}
		}
	}		

	
	public void updateChunksAvailable(DTNHost from, ArrayList<Long> newBuffermap){
		ArrayList<Long> buffermap = null;
		
		if (neighborData.containsKey(from)){
			buffermap = (ArrayList<Long>) neighborData.get(from).clone();
			buffermap.addAll(newBuffermap); //put new buffermap
			neighborData.put(from, (ArrayList<Long>) buffermap.clone()); 
		}
		else{
			neighborData.put(from, (ArrayList<Long>) newBuffermap.clone());
		}
		
		if (availableNeighbors.containsKey(from)){
			availableNeighbors.put(from, buffermap);
		}
		updateChunkCount(newBuffermap); //increase count of chunks
	}

	/*
	 * 
	 * CHOKE/UNCHOKE STARTS HERE ---------------------->
	 * 
	 */
	public void evaluateResponse(DTNHost host, DTNHost to){ //evaluate if we should choke or unchoke this node that sent INTERESTED at time before chokeInterval
//		System.out.println("@ evaluating response " +to);
		
		int ctr=0;
		try{
			while(unchoked.get(ctr)!=null && ctr<4){
				ctr++;
			}
		}catch(IndexOutOfBoundsException e){}
		
		if (ctr<4 && !unchoked.contains(to)){
			System.out.println(" rand sending response to " + to);
			sendResponse(host, to, true);
			unchoked.set(ctr,to);
			sendEventToListeners(StreamAppReporter.UNCHOKED, unchoked, host);
			interestedNeighbors.remove(to); //remove from interestedNeighbors since granted
		}
	}
	
	public void sendResponse(DTNHost host, DTNHost to, boolean isOkay){
		System.out.println(" @sendResponse: " + to);
		
		String id;
		String msgType; 

		if (isOkay){
			id = APP_TYPE + ":UNCHOKE_" + SimClock.getIntTime() + "-" + host.getAddress()  + "-" + to;
			msgType = UNCHOKE;
		}
		else{
			id = APP_TYPE + ":CHOKE_" + SimClock.getIntTime() + "-" + host.getAddress()  +"-" + to;
			msgType = CHOKE;
		}
		
		ArrayList<Long> unsentUpdate = (ArrayList<Long>) props.getBuffermap().clone();
		unsentUpdate.removeAll(helloSent.get(to));
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", msgType);
		m.addProperty("buffermap", unsentUpdate);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 4);
		host.createNewMessage(m);
	}
	
	private boolean hasNewInterested(){ // count if an sulod han interested is same la ha mga unchoked
		if (interestedNeighbors.isEmpty()) return false;
			
		ArrayList<DTNHost> nh = new ArrayList<DTNHost> (interestedNeighbors.keySet());
		nh.removeAll(unchoked);
		
		return !nh.isEmpty();
	}

	/*
	 * called every 15 seconds.
	 * after this method, unchoked list is updated. recognized includes those na diri na api ha top3
	 */
	private void unchokeTop3(DTNHost host, ArrayList<DTNHost> recognized){
		if (recognized.isEmpty()) return;

		Iterator<DTNHost> i = recognized.iterator();
		
 		for (int ctr=0; ctr<3; ctr++){ //send UNCHOKE to top 3
			DTNHost other=null;
			try{
				other = i.next();	
				if (!unchoked.contains(other)){ //if diri hya api ha kanina na group of unchoked
					System.out.println(" top 3 sending response to " + other);
					sendResponse(host, other, true); //send UNCHOKE
					interestedNeighbors.remove(other); //for those new interested
				}
				i.remove(); //notification granted, remove at tempCurrInterested
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
		if (recognized.isEmpty()) return;

		Random r = new Random();
		DTNHost prevRand;
		recognized.removeAll(unchoked.subList(0, 3)); //remove pagpili random an ada na ha unchoked list
		prevRand = unchoked.get(3);
		
		int index = r.nextInt(recognized.size()); //possible ini maging same han last random
		DTNHost randNode = recognized.get(index); //choose random

		if (prevRand!=randNode){
			System.out.println(" prev rand sending response to " + prevRand);
			if (prevRand!=null) sendResponse(host, prevRand, false); //send CHOKE to this random node if it is not the same with new node
			recognized.remove(prevRand);
			if (!prevUnchoked.contains(randNode)){
				System.out.println(" new rand sending response to " + randNode);
				sendResponse(host, randNode, true); //sendUnchoke to this random node if it wasn't on previous list of unchoked
			}
		}
		updateUnchoked(3, randNode);
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
