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
	public static final String WAITING_THRESHOLD = "waitingThreshold";
	public static final String PREBUFFER = "prebuffer";
	
	public static final int PLAYING = 1;
	public static final int WAITING = 0;

	private int 	status =-1;
	private int 	watcherType; //1 if listener. 0 if just a hop
	private int maxRequestPerNode;
	private int maxPendingRequest;
	private int waitingThreshold;
	private int prebuffer;
	private double lastTimePlayed=0;
	private double lastChokeInterval = 0;
	private double lastOptimalInterval=0;
	private double streamStartTime;
	private boolean isWatching=false;
	
	private StreamProperties props; ////////properties of current stream channel. what i received, etc.
	
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
		this.waitingThreshold = s.getInt(WAITING_THRESHOLD);
		this.prebuffer = s.getInt(PREBUFFER);
		
		props = new StreamProperties("");
		neighborData = new HashMap<DTNHost, ArrayList<Long>>();
		availableNeighbors = new HashMap<DTNHost, ArrayList<Long>>();
		toFragment = new HashMap<Integer, ArrayList<StreamChunk>>();
		chunkRequest = new HashMap<Long, Double>();
		listOfRequested = new ArrayList<Long>();
		
		sadf = new SADFragmentation();
	
		initUnchoke();
	}

	public WatcherAppV3(WatcherAppV3 a) {
		super(a);
		
		this.watcherType = a.getWatcherType();
		this.waitingThreshold = a.getWaitingThreshold();
		this.prebuffer = a.getPrebuffer();
		this.maxPendingRequest = a.getMaxPendingRequest();
				
		props = new StreamProperties("");
		neighborData = new HashMap<DTNHost, ArrayList<Long>>();
		availableNeighbors = new HashMap<DTNHost, ArrayList<Long>>();
		toFragment = new HashMap<Integer, ArrayList<StreamChunk>>();
		chunkRequest = new HashMap<Long, Double>();
		listOfRequested = new ArrayList<Long>();
		
		sadf = new SADFragmentation();
		
		initUnchoke();
	}
	
	private int getMaxPendingRequest() {
		return maxPendingRequest;
	}

	private int getPrebuffer() {
		return prebuffer;
	}

	private int getWaitingThreshold() {
		return waitingThreshold;
	}

	@Override
	public Message handle(Message msg, DTNHost host) {
		
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
		if (type.equals(APP_TYPE)){
			String msg_type = (String) msg.getProperty("msg_type");
			
			if (msg_type.equals(BROADCAST_LIVE)){
				System.out.println(host + " received broadcast_live " );
				if (!isWatching && watcherType==1){
					isWatching=true;
					status = WAITING;
					
					String streamID=(String) msg.getProperty("streamID");
					int timeStarted = (int) msg.getProperty("time_started");
					int byterate = (int) msg.getProperty(BYTERATE);
					int durationPerChunk = (int) msg.getProperty(DURATION_PER_CHUNK);
					int noOfChunksPerFrag = (int) msg.getProperty(CHUNKS_PER_FRAG);
					
					sadf.setNoOfChunksPerFrag(noOfChunksPerFrag);					
					props.initProps(durationPerChunk, byterate);
					props.setStreamID(streamID);
					props.setStartTime(timeStarted);
					
					this.maxPendingRequest = (noOfChunksPerFrag*2)+50;
					streamStartTime = SimClock.getTime() +prebuffer;
				}
				
				///for uninterested watcher, just save
				broadcasterAddress = (DTNHost) msg.getProperty("source");
				lastChokeInterval = SimClock.getTime();
				lastOptimalInterval = SimClock.getTime();
				sendEventToListeners(StreamAppReporter.BROADCAST_RECEIVED, SimClock.getTime(), host);
			}
			
			else if (msg_type.equals(HELLO)){
				System.out.println(host  + " received hello " + msg.getFrom());
				
				int otherStatus = (int) msg.getProperty("status");
				long otherAck = (long) msg.getProperty("ack");
				ArrayList<Long> otherBuffermap = (ArrayList<Long>) msg.getProperty("buffermap");
				
				if (broadcasterAddress!=null && otherAck==-1 && otherStatus==-1
						&& otherBuffermap.isEmpty() ){ //if watcher does not know someone has a stream
					
					sendBroadcast(host, msg.getFrom(), broadcasterAddress);
					sendBuffermap(host, msg.getFrom(), props.getBuffermap()); 
					helloSent.put(msg.getFrom(), props.getBuffermap());
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
				System.out.println(host  + " received chunk ");
				
				StreamChunk chunk = (StreamChunk) msg.getProperty("chunk");
				int fragId = chunk.getFragmentIndex();
				
				//for fragmenting chunks that are individually received
				if (props.getChunk(chunk.getChunkID())==null){
					
					System.out.println(" noOfChuksPerfrag " + sadf.getNoOfChunksPerFrag());
					if (sadf.getNoOfChunksPerFrag() > 0 ){
						System.out.println("@toFramgment");
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
							sadf.initTransLevelFrag(fragId);
						}
						//check if we should include it on fragment now
						if (sadf.doesExist(fragId) && !sadf.getFragment(fragId).isComplete()){
							ArrayList<StreamChunk> hold = toFragment.get(fragId);
							for (StreamChunk c: hold){
								sadf.addChunkToFragment(fragId, ((int)c.getChunkID())%sadf.getNoOfChunksPerFrag(), c);
							}
							
							if (sadf.getFragment(fragId).isComplete()){
								sendEventToListeners(StreamAppReporter.FRAGMENT_CREATED, null, host);
							}
							
							toFragment.remove(fragId);
						}		
					}
				}
				
				interpretChunks(host, chunk, msg.getFrom());
			}
			
			else if(msg_type.equalsIgnoreCase(BROADCAST_FRAGMENT_SENT)){
				Fragment frag = (Fragment) msg.getProperty("fragment");
				int fragType = (int) msg.getProperty("frag_type");
				System.out.println(host + " received fragment " + " type: " + fragType);
				
				decodeFragment(host, frag, fragType, msg.getFrom());
			}
			
			else if(msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){
				System.out.println(host + " received request ");
				if (sadf.getNoOfChunksPerFrag() == 0 ){
					sendWithoutFrag(host, msg);
				}
				else{
					evaluateToSend(host, msg);
				}
			}

			else if (msg_type.equals(INTERESTED)){ 	//evaluate response if choke or unchoke
				System.out.println(host + " received interested " + msg.getFrom());
				interestedNeighbors.put(msg.getFrom(), (int) msg.getCreationTime());
				evaluateResponse(host, msg.getFrom());
			}
			
			
			else if (msg_type.equalsIgnoreCase(UNINTERESTED)){
				System.out.println(host + " received uninterested " + msg.getFrom());
				interestedNeighbors.remove(msg.getFrom());
				if (unchoked.contains(msg.getFrom())){
					unchoked.set(unchoked.indexOf(msg.getFrom()), null);
				}
			}
			
			else if (msg_type.equals(UNCHOKE)){
				System.out.println(host +" received unchoke from " + msg.getFrom());
				
				ArrayList<Long> temp = (ArrayList<Long>) msg.getProperty("buffermap");
				updateChunksAvailable(msg.getFrom(), temp);
				availableNeighbors.put(msg.getFrom(), (ArrayList<Long>) neighborData.get(msg.getFrom()).clone()); //add this to available neighbors
				
				evaluateRequest(host, msg);
				ArrayList<DTNHost> availableH = new ArrayList<DTNHost>(availableNeighbors.keySet());
				sendEventToListeners(StreamAppReporter.UPDATE_AVAILABLE_NEIGHBORS, availableH, host);
			}

			else if (msg_type.equals(CHOKE)){
				System.out.println(host + " received choke from " + msg.getFrom());
				//remove didi an neighbors na dati nag unchoke ha at. diri na hya api ha mga dapat aruan
				availableNeighbors.remove(msg.getFrom());
				ArrayList<DTNHost> availableH = new ArrayList<DTNHost>(availableNeighbors.keySet());
				sendEventToListeners(StreamAppReporter.UPDATE_AVAILABLE_NEIGHBORS, availableH, host);
			}

		}
		return msg;
	}

	private void decodeFragment(DTNHost host, Fragment frag, int fragType, DTNHost from) {

		if (fragType == SADFragmentation.INDEX_LEVEL){
			
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
			
			if (!sadf.doesExist(currFrag)){
				sadf.initTransLevelFrag(frag.getId());
	
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
		if (props.getBuffermap().size()==0){ //first time received
			sendEventToListeners(StreamAppReporter.FIRST_TIME_RECEIVED, SimClock.getTime(), host);
		}
		
		if (props.getChunk(chunk.getChunkID())==null){ //if this is not a duplicate
			props.addChunk(chunk);			
			props.setAck(chunk.getChunkID());
			sendEventToListeners(StreamAppReporter.UPDATE_ACK, props.getAck(), host);
			sendEventToListeners(StreamAppReporter.RECEIVED_CHUNK, chunk.getChunkID(), host);
			updateHello(host, chunk.getChunkID(), from);
		}
		else{
			sendEventToListeners(StreamAppReporter.RECEIVED_DUPLICATE, chunk.getChunkID(), host);
		}

		chunkRequest.remove(chunk.getChunkID()); //remove granted requests
		if (chunkRequest.size() <= maxPendingRequest/2 && !availableNeighbors.isEmpty()){
			timeToAskNew(host);
		}
		
		//receivedFirstPlayable
		if ( (chunk.getCreationTime() <= props.getStartTime())  && (props.getStartTime() < (chunk.getCreationTime() + props.getStreamInterval()))
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
					helloSent.put(otherNode, props.getBuffermap());
				}
			}
		}catch(NullPointerException e){}
		
		try{
			if (isWatching && (curTime-this.lastTimePlayed >= props.getStreamInterval()) && curTime>=streamStartTime){
				
				if (prebuffer>0 && props.isBufferReady(props.getNext()) && !props.isReady(props.getNext())){
					sendEventToListeners(StreamAppReporter.SKIPPED_CHUNK, props.getNext(), host);
					System.out.println(host + " skipped chunk: " + props.getNext());
					props.skipNext();
				}

				if(props.isReady(props.getNext())){
					props.playNext();
					status = PLAYING;
					this.lastTimePlayed = curTime;
					sendEventToListeners(StreamAppReporter.LAST_PLAYED, lastTimePlayed, host);
					System.out.println(host + " playing: " + props.getPlaying());
				}
				else {
					//hope for the best na aaruon utro ini na missing
					status = WAITING;
					sendEventToListeners(StreamAppReporter.INTERRUPTED, null, host);
					System.out.println(host+ " waiting: " + props.getNext());
				}
			}
			
		}catch(NullPointerException e){
		}catch(ArrayIndexOutOfBoundsException i){ }
		
		//for maintaining -- choking and unchoking
		if (curTime-lastChokeInterval >= rechokeInterval){
			ArrayList<DTNHost> recognized =  new ArrayList<DTNHost>(interestedNeighbors.keySet());
			
			if (hasNewInterested()){
				
				ArrayList<DTNHost> prevUnchokedList = (ArrayList<DTNHost>) unchoked.clone();
				
				if (curTime-lastOptimalInterval >= optimisticUnchokeInterval){ //optimistic interval = every 15 seconds
					recognized.removeAll(unchoked); //remove first an nagrequest bisan unchoked na kanina [pa]. to avoid duplicates
					recognized.addAll(unchoked); 
					
					for (Iterator r = recognized.iterator(); r.hasNext(); ){ //remove null values, unchoked may have null values
						if(r.next() == null){
							r.remove();
						}
					}
					recognized = sortNeighborsByBandwidth(recognized);
					System.out.println(host + " interested nodes: " +recognized);
					
					unchokeTop3(host, recognized);
					prevUnchokedList.removeAll(unchoked.subList(0, 3)); //remove an api kanina na diri na api yana ha top3
					
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
			System.out.println(host + " unchoked: " + unchoked);
			sendEventToListeners(StreamAppReporter.INTERESTED, recognized.clone(), host);
			System.out.println(host + " recognized: " + recognized);
		}

	}
	
	public void sendBroadcast(DTNHost host, DTNHost to, DTNHost broadcasterAddress){
		String id = APP_TYPE + ":broadcast" + SimClock.getIntTime() + "-" + host.getAddress() + "-" + to;
		
		Message m= new Message(host, to, id, SIMPLE_MSG_SIZE);
		m.addProperty("type", APP_TYPE);
		m.addProperty("msg_type", BROADCAST_LIVE);
		m.addProperty("streamID", getStreamID());
		m.addProperty("stream_name", "tempstream");
		m.addProperty(BYTERATE, props.getByterate());
		m.addProperty(DURATION_PER_CHUNK, props.getStreamInterval());
		m.addProperty(CHUNKS_PER_FRAG, sadf.getNoOfChunksPerFrag());
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 1);
		m.addProperty("time_started", props.getStartTime() - prebuffer);
		m.addProperty("source", broadcasterAddress);
		m.setAppID(APP_ID);
		host.createNewMessage(m); //must override, meaning start a broadcast that a stream is initiated from this peer
		
		lastChokeInterval = SimClock.getTime();
		lastOptimalInterval = SimClock.getTime();
		super.sendEventToListeners(BROADCAST_LIVE, null, host);
	}

	private void timeToAskNew(DTNHost host){
		
		ArrayList<DTNHost> hosts = new ArrayList<DTNHost>(availableNeighbors.keySet());
		Collections.sort(hosts, StreamingApplication.BandwidthComparator);
		
		int maxRequestPerNode = maxPendingRequest/hosts.size();
		ArrayList<Long> toRequest = new ArrayList<Long>();
		
		for (DTNHost to: hosts){
		
			ArrayList<Long> aChunks = new ArrayList<Long> (availableNeighbors.get(to));
			aChunks.removeAll(props.getBuffermap());
			aChunks.removeAll(listOfRequested);
			toRequest.clear();
			
			for (int i=0; toRequest.size()<maxRequestPerNode && i<aChunks.size() && chunkRequest.size()<maxPendingRequest; i++){
				long chunkId = aChunks.get(i);
				
				toRequest.add(chunkId);
				
				//expiry = 10 seconds before this will be played
				double expiry = (((chunkId*StreamChunk.getDuration()) - (props.getPlaying()*StreamChunk.getDuration()))+
						SimClock.getTime()) - waitingThreshold;
				if (expiry <= SimClock.getTime() ){
					expiry = SimClock.getTime() + waitingThreshold;
				}
				chunkRequest.put(chunkId, expiry); //add to requested chunks
				listOfRequested.add(chunkId);
			}
			
			if (!toRequest.isEmpty()){
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
		if (props.getBuffermap().isEmpty() && chunkRequest.isEmpty() && status==WAITING){
			sendEventToListeners(StreamAppReporter.FIRST_TIME_REQUESTED, SimClock.getTime(), host);
		}
		requestFromNeighbors(host,msg.getFrom());
	}
	
	/*
	 * called everytime availableNeighbors is updated
	 */
	private void requestFromNeighbors(DTNHost host, DTNHost otherNode){ 
		ArrayList<Long> toRequest = new ArrayList<>();
		
		maxRequestPerNode = maxPendingRequest/availableNeighbors.size(); //be sure what really happens with this.
		ArrayList<Long> otherAvailable = new ArrayList (availableNeighbors.get(otherNode));
		
		sendEventToListeners("hostname", otherNode, host);
		sendEventToListeners("update", otherAvailable.clone(), host);
		
		otherAvailable.removeAll(props.getBuffermap());
		otherAvailable.removeAll(listOfRequested); //remove chunks that we already requested
		Collections.sort(otherAvailable);
		
		int ctr=0;
		for (long chunk: otherAvailable){
			if (chunkRequest.size()<maxPendingRequest && ctr<maxRequestPerNode){ 
				toRequest.add(chunk);

				//expiry = 10 seconds before this will be played
				double expiry = (((chunk*StreamChunk.getDuration()) - (props.getPlaying()*StreamChunk.getDuration()))+
						SimClock.getTime()) - waitingThreshold;
				if (expiry <= SimClock.getTime() ){
					expiry = SimClock.getTime() + waitingThreshold;
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
		System.out.println(host + " sent hello to "+to);
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
	}
	
	private void removeExpiredRequest(){
		double curTime = SimClock.getTime();
		Iterator<Long> i = chunkRequest.keySet().iterator();
		while(i.hasNext()){
			long chunkID = i.next();
			if (chunkRequest.get(chunkID)<= curTime){ //if the request we sent is expired
				listOfRequested.remove(chunkID);
				i.remove();
			}
		}
	}

	private void sendWithoutFrag(DTNHost host, Message msg){
		ArrayList<Long> request = (ArrayList<Long>) msg.getProperty("chunk");
		
		for (long rChunk: request){
			sendChunk(props.getChunk(rChunk), host, msg.getFrom());
		}
	}
	
	private void evaluateToSend(DTNHost host, Message msg) {
		ArrayList<Long> request = (ArrayList<Long>) msg.getProperty("chunk");
		ArrayList<Long> bundled = new ArrayList<Long>();
		
		long rChunk;
		int currFrag;
		
		while(!request.isEmpty()){
			rChunk = request.get(0);
			currFrag = props.getChunk(rChunk).getFragmentIndex();
			bundled.clear();
			
			if (sadf.doesExist(currFrag) && sadf.getFragment(currFrag).isComplete()){ //if diri pa complete an index level, cannot send transmission level
				long fIndex = sadf.getFragment(currFrag).getFirstChunkID();
				long eIndex = sadf.getFragment(currFrag).getEndChunk();
				
				if (request.contains(fIndex) && request.contains(eIndex) && //for full index request
					request.subList(request.indexOf(fIndex), request.indexOf(eIndex)+1).size() == sadf.getFragment(currFrag).getNoOfChunks()){
						
						if (isGreaterThanTrans(host, msg.getFrom(), sadf.getFragment(currFrag).getSize())){ //fragment with respect to trans size
							subFragment(host, msg.getFrom(), (ArrayList<StreamChunk>) sadf.getFragment(currFrag).getBundled().clone(), currFrag);
							request.removeAll(request.subList(request.indexOf(fIndex), request.indexOf(eIndex)+1));
						}
						else{
							sendIndexFragment(host, msg.getFrom(), sadf.getFragment(currFrag));
							request.removeAll(request.subList(request.indexOf(fIndex), request.indexOf(eIndex)+1));
						}
				}
				
				else{
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
							if (currSize > transSize){
								sendEventToListeners(StreamAppReporter.SIZE_ADJUSTED, null, host);
							}
						}
					}
					
					if (!bundled.isEmpty() && bundled.size()>1){ //nasend fragment bisan usa la it sulod
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
					else{
						sendChunk(props.getChunk(rChunk), host, msg.getFrom());
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
		sendEventToListeners(StreamAppReporter.SIZE_ADJUSTED, null, host);
		ArrayList<Long> toSend = new ArrayList<Long>();
		
		double transSize = 	getTransSize(host, to);
		double byteRate = StreamChunk.getByterate();
		double currSize;
		long prevChunk;
		
		while(!bundle.isEmpty()){
			Iterator<StreamChunk> iter = bundle.iterator();
			toSend.clear();
			prevChunk = bundle.get(0).getChunkID();
			
			while(iter.hasNext()){
				long currChunk = iter.next().getChunkID();
				currSize = ((toSend.size()+1)*byteRate) + HEADER_SIZE;

				if(currFrag == props.getChunk(currChunk).getFragmentIndex() && (toSend.isEmpty() || currChunk==prevChunk+1)
						&& currSize < transSize){
					toSend.add(currChunk);
					prevChunk = currChunk;
					iter.remove();
				}
				else{
					if (currSize > transSize){
						sendEventToListeners(StreamAppReporter.SIZE_ADJUSTED, null, host);
					}
				}
			}
			
			if (!toSend.isEmpty() && toSend.size()>1){ //nasend fragment bisan usa la it sulod
				//if tapos na, send this part of request_response 
				int start = sadf.getFragment(currFrag).indexOf(toSend.get(0));
				int end = sadf.getFragment(currFrag).indexOf(toSend.get(toSend.size()-1));
				
				ArrayList<StreamChunk> subFrag= new ArrayList<StreamChunk> (sadf.getFragment(currFrag).getBundled().subList(start, end+1));					
				Fragment fragment = new Fragment(currFrag, subFrag);
				fragment.setStartPosition(start);
				fragment.setEndPosition(end);
				sendTransFragment(host, to, fragment);
			}
			else if (toSend.size()==1){ //limit trans level == 2 chunks fragmented
				sendChunk(props.getChunk(toSend.get(0)), host, to);
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
	}
	
	private void sendTransFragment(DTNHost host, DTNHost to, Fragment frag) {
		///dapat nacreate ini new fragment na instance tas an sulod la is an firstPos to lastPos
		
		String id = APP_TYPE + ":fragment_"  + SimClock.getTime() + "-" + frag.getId() + "-" + to + "-" + host.getAddress();
		
		Message m = new Message(host, to, id,  (int) (frag.getSize()+HEADER_SIZE));		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_FRAGMENT_SENT);
		m.addProperty("frag_type", SADFragmentation.TRANS_LEVEL);
		m.addProperty("fragment", frag);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);

		sendEventToListeners(StreamAppReporter.SENT_TRANS_FRAGMENT, null, host);
	}
	
	
	public void updateHello(DTNHost host, long newChunk, DTNHost from){
		checkHelloedConnection(host);
		
		ArrayList<Long> latest = new ArrayList<Long>();
		latest.add(newChunk);
		
		for (DTNHost h: unchoked){
			if (h!=null && !h.equals(from)) {
				sendBuffermap(host, h, (ArrayList<Long>) latest.clone());
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
		int ctr=0;
		try{
			while(unchoked.get(ctr)!=null && ctr<4){
				ctr++;
			}
		}catch(IndexOutOfBoundsException e){}
		
		if (ctr<4 && !unchoked.contains(to)){
			sendResponse(host, to, true);
			unchoked.set(ctr,to);
			sendEventToListeners(StreamAppReporter.UNCHOKED, unchoked, host);
			interestedNeighbors.remove(to); //remove from interestedNeighbors since granted
		}
	}
	
	public void sendResponse(DTNHost host, DTNHost to, boolean isOkay){
		
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
			if (prevRand!=null) sendResponse(host, prevRand, false); //send CHOKE to this random node if it is not the same with new node
			recognized.remove(prevRand);
			if (!prevUnchoked.contains(randNode)){
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
