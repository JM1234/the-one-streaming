package applications;

import java.util.ArrayList;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

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

public class BroadcasterAppV3 extends StreamingApplication{

	public static final String STREAM_TIME = "streamTime";

	
	private Stream 	stream;
	private SADFragmentation sadf;
	
	private boolean broadcasted=false;
	private double sTime;
	private String 	streamID;
	
	private double lastChokeInterval = 0;
	private double lastOptimalInterval =0;
	
	public BroadcasterAppV3(Settings s) {
		super(s);
		sadf = new SADFragmentation();
//		r=new Random();
		sTime = 0;	//s.getDouble("streamTime") * r.nextDouble(); //time to start broadcasting
		initUnchoke();
	}

	public BroadcasterAppV3(BroadcasterAppV3 a) {
		super(a);
		
		this.streamID=a.getStreamID();
		this.noOfChunksPerFrag = a.getNumberOfChunksPerFrag();
		this.bitrate = a.getBitrate();
		this.durationPerChunk = a.getDurationPerChunk();
		
		sadf = new SADFragmentation();
		sTime = a.getSTime();
		initUnchoke();
	}

	@Override
	public Message handle(Message msg, DTNHost host) {	
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
			if(type.equals(APP_TYPE)){
				String msg_type = (String) msg.getProperty("msg_type");
				
				if (msg_type.equalsIgnoreCase(HELLO)){
					long otherAck = (long) msg.getProperty("ack");
					int otherStatus = (int) msg.getProperty("status");
					ArrayList<Long> otherBuffermap = (ArrayList<Long>) msg.getProperty("buffermap");
					
					if (!stream.isRegistered(msg.getFrom())){ //save that we met this node for the first time
						stream.registerListener(msg.getFrom());
					}
					
					updateChunkCount(otherBuffermap); //update records of neighbor's data
				
					if (broadcasted && otherStatus==-1 && otherAck==-1){ //if otherNode is not listening to any stream yet
						sendBroadcast(host, msg.getFrom());
						sendBuffermap(host, msg.getFrom(), stream.getBuffermap());
						helloSent.put(msg.getFrom(), stream.getBuffermap());
					}
					
					else {
						sendBuffermap(host, msg.getFrom(), stream.getBuffermap());
						helloSent.put(msg.getFrom(), stream.getBuffermap());
					}
				}
				
				else if (msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){ //evaluate here if fragment or chunk it isesend

					if (noOfChunksPerFrag == 0 ){
						sendWithoutFrag(host, msg);
					}
					else{
						evaluateToSend(host, msg);
					}
				}
				
				else if (msg_type.equals(INTERESTED)){
					interestedNeighbors.put(msg.getFrom(), (int) msg.getCreationTime());
					evaluateResponse(host, msg.getFrom());	
				}
				
				else if (msg_type.equalsIgnoreCase(UNINTERESTED)){
					interestedNeighbors.remove(msg.getFrom());
					if (unchoked.contains(msg.getFrom())){
						updateUnchoked(unchoked.indexOf(msg.getFrom()), null);
					}
				}
			}
		return msg;
	}
	
	@Override
	public void update(DTNHost host) {
		double curTime = SimClock.getTime();
		
		if (!broadcasted && curTime>=sTime){ //startBroadcast here, once
			startBroadcast(host);
			broadcasted =  true;
		}
		
		checkHelloedConnection(host); //remove data of disconnected nodes
		
		if (broadcasted){
			//generate chunks here
			if (curTime - stream.getTimeLastStream() >= Stream.getStreamInterval()){ //for every interval
				stream.generateChunks(getStreamID(), sadf.getCurrIndex());
				sendEventToListeners(StreamAppReporter.CHUNK_CREATED, null, host);
				updateHello(host);
				
//				System.out.println("Generated chunk " + stream.getLatestChunk().getChunkID() + "Fragment: " + stream.getLatestChunk().getFragmentIndex());
			
				//create fragments here
				if ((stream.getLatestChunk().getChunkID()+1) % SADFragmentation.NO_OF_CHUNKS_PER_FRAG == 0){
					int sIndex = sadf.getCurrIndex() * SADFragmentation.NO_OF_CHUNKS_PER_FRAG;
					sadf.createFragment(new ArrayList(stream.getChunks().subList(sIndex,sIndex+SADFragmentation.NO_OF_CHUNKS_PER_FRAG)));
					try{
						sadf.getFragment(sadf.getCurrIndex()-1).setIndexComplete();
					}catch(NullPointerException e){
						sadf.getFragment(0).setIndexComplete();						
					}
					sendEventToListeners(StreamAppReporter.FRAGMENT_CREATED, null, host);
				}
			}
		}
		
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
			sendEventToListeners(StreamAppReporter.INTERESTED, recognized.clone(), host);
		}
	}
	
	public void startBroadcast(DTNHost host){
		stream= new Stream("streamID");
		stream.startLiveStream();

		lastChokeInterval = SimClock.getTime();
		lastOptimalInterval = SimClock.getTime();
		super.sendEventToListeners(BROADCAST_LIVE, null, host);
	}
		
	public void sendBroadcast(DTNHost host, DTNHost to){
		String id = APP_TYPE + ":broadcast" + SimClock.getIntTime() + "-" + host.getAddress() + "-" + to;
		
		Message m= new Message(host, to, id, SIMPLE_MSG_SIZE);
		m.addProperty("type", APP_TYPE);
		m.addProperty("msg_type", BROADCAST_LIVE);
		m.addProperty("streamID", getStreamID());
		m.addProperty("stream_name", "tempstream");
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 1);
		m.addProperty("time_started", SimClock.getIntTime());
		m.addProperty("source", host);
		m.setAppID(APP_ID);
		host.createNewMessage(m); //must override, meaning start a broadcast that a stream is initiated from this peer
		
		lastChokeInterval = SimClock.getTime();
		lastOptimalInterval = SimClock.getTime();
		super.sendEventToListeners(BROADCAST_LIVE, null, host);
	}
	
	public void sendBuffermap(DTNHost host, DTNHost to, Collection<Long> list){
		String id = APP_TYPE+ ":hello_" + SimClock.getTime() +"-" + host.getAddress() +"-" + to;
		
		long ack =-1;
		if(!stream.getBuffermap().isEmpty()){
			ack = stream.getLatestChunk().getChunkID();
		}
		
		Message m = new Message(host, to, id, BUFFERMAP_SIZE); //buffermap size must have basis
		m.setAppID(APP_ID);
		m.addProperty("type", APP_TYPE);
		m.addProperty("msg_type", HELLO);
		m.addProperty("status", 2);
		m.addProperty("ack", ack);
		m.addProperty("buffermap", list); // this is full buffermap
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 5);
		host.createNewMessage(m);
	
	}
	
	public void updateHello(DTNHost host){
		long currAck=-1;
		
		if (stream.getLatestChunk()!=null){
			currAck = stream.getLatestChunk().getChunkID();
		}
		
		ArrayList<Long> latest = new ArrayList<Long>();
		latest.add(currAck);
		
		for (DTNHost h: unchoked){
			if (h!=null){
				sendBuffermap(host, h, latest);
				helloSent.get(h).addAll(latest);
			}
		}
		
		for (DTNHost h: helloSent.keySet()){
			if (!unchoked.contains(h)){
				sendBuffermap(host, h, latest);
				helloSent.get(h).addAll(latest);
			}
		}
	}	
	
	private void sendWithoutFrag(DTNHost host, Message msg){
		ArrayList<Long> request = (ArrayList<Long>) msg.getProperty("chunk");
		
		for (long rChunk: request){
			sendChunk(stream.getChunk(rChunk), host, msg.getFrom());
		}
	}
	
	private void evaluateToSend(DTNHost host, Message msg) {
		ArrayList<Long> request = (ArrayList<Long>) msg.getProperty("chunk");
		ArrayList<Long> bundled = new ArrayList<Long>();
		
		long rChunk;
		int currFrag;
		
		while(!request.isEmpty()){
			rChunk = request.get(0);
			currFrag = stream.getChunk(rChunk).getFragmentIndex();
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
					
						if(currFrag == stream.getChunk(currChunk).getFragmentIndex() && (bundled.isEmpty() || currChunk==prevChunk+1)
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
						sendChunk(stream.getChunk(bundled.get(0)), host, msg.getFrom());
					}
					else{
						sendChunk(stream.getChunk(rChunk), host, msg.getFrom());
					}
				}
			}
			
			else{ // if fragment does not exists
				sendChunk(stream.getChunk(rChunk), host, msg.getFrom());
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

				if(currFrag == stream.getChunk(currChunk).getFragmentIndex() && (toSend.isEmpty() || currChunk==prevChunk+1)
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
				sendChunk(stream.getChunk(toSend.get(0)), host, to);
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
		
		Message m = new Message(host, to, id, (int) (frag.getSize()+HEADER_SIZE));		
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
		String id = APP_TYPE + ":fragment_"  + SimClock.getTime() + "-" + frag.getId() + "-" + to + "-" + host.getAddress();

		Message m = new Message(host, to, id, (int) (frag.getSize()+HEADER_SIZE));		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_FRAGMENT_SENT);
		m.addProperty("frag_type", SADFragmentation.TRANS_LEVEL);
		m.addProperty("fragment", frag);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);

		sendEventToListeners(StreamAppReporter.SENT_TRANS_FRAGMENT, null, host);
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

	
	/*
	 * 
	 * @param isOkay true if response is unchoke. false if choke
	 * 
	 */
	public void sendResponse(DTNHost host, DTNHost to, boolean isOkay){
		
		String id;
		String msgType; 

		if (isOkay){
			id = APP_TYPE + ":UNCHOKE_" +  SimClock.getIntTime() + "-" + host.getAddress()  +"-" + to;
			msgType = UNCHOKE;
		}
		else{
			id = APP_TYPE + ":CHOKE_" + SimClock.getIntTime()+ "-" + host.getAddress()  + "-" + to;
			msgType = CHOKE;
		}

		ArrayList<Long> unsentUpdate = (ArrayList<Long>) stream.getBuffermap().clone();
	
		unsentUpdate.removeAll(helloSent.get(to));
		
		Message m = new Message(host, to, id, SIMPLE_MSG_SIZE);		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", msgType);
		m.addProperty("buffermap", unsentUpdate);
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 4);
		host.createNewMessage(m);
	}

	/*
	 * count if an sulod han interested is same la ha mga unchoked
	 */
	private boolean hasNewInterested(){

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
				if (!unchoked.contains(other) ){ //if diri hya api ha kanina na group of unchoked
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
	private void unchokeRand(DTNHost host, ArrayList<DTNHost> recognized, ArrayList<DTNHost> prevUnchoked){
		if (recognized.isEmpty()) return;

		Random r = new Random();
		DTNHost prevRand;
		recognized.removeAll(unchoked.subList(0, 3)); //remove pagpili random an ada na ha unchoked list
		prevRand = unchoked.get(3);
		
		int index = r.nextInt(recognized.size()); //possible ini maging same han last random
		DTNHost randNode = recognized.get(index);

		if (prevRand!=randNode){
			if (prevRand!=null)  sendResponse(host, prevRand, false); //send CHOKE to this random node if it is not the same with new node
			recognized.remove(prevRand);
			if (!prevUnchoked.contains(randNode)){
				sendResponse(host, randNode, true); //sendUnchoke to this random node
			}
		}
		updateUnchoked(3, randNode);
		interestedNeighbors.remove(randNode);  //notification granted. remove on original interested list
		recognized.remove(randNode); //notification granted. remove on recognized list
	}
	
	private void chokeOthers(DTNHost host, ArrayList<DTNHost> recognized){ 	//sendChoke to tanan na nabilin
		for (DTNHost r : recognized){
			if (r!=null){
				sendResponse(host, r, false); 
			}
		}
	}
	
	private void initUnchoke(){
		for (int i=0; i<4; i++){
			unchoked.add(i, null);
		}
	}

	@Override
	public Application replicate() {
		return new BroadcasterAppV3(this);
	}

	public double getSTime(){
		return sTime;
	}
	
	private int getDurationPerChunk() {
		return durationPerChunk;
	}

	private int getBitrate() {
		return bitrate;
	}

	private int getNumberOfChunksPerFrag() {
		return durationPerChunk;
	}
}
