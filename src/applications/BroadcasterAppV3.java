package applications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import fragmentation.SADFragmentation;
import report.StreamAppReport;
import routing.TVProphetRouterV2;
import streaming.Stream;
import streaming.StreamChunk;

public class BroadcasterAppV3 extends StreamingApplication{

	public static final String STREAM_TIME = "streamTime";
	
	private boolean broadcasted=false;
	private static double sTime;
	
	private int		seed = 0;
	private String 	streamID;
	
	private Random	r;
	private Stream 	stream;
	private SADFragmentation fragment;
	private int connectionSize;
	
	private DTNHost curHost;
//	private ArrayList<DTNHost> receivedHello;
	private boolean isListener=false;
	private double lastChokeInterval = 0;
	private double lastOptimalInterval =0;
	
	private ArrayList<DTNHost> temp ;
	
//	private HashMap<DTNHost, Long> latestHello;
	
	public BroadcasterAppV3(Settings s) {
		super(s);
		
		fragment = new SADFragmentation();
		r=new Random();
		sTime = 0; //s.getDouble("streamTime") * r.nextDouble(); //time to start broadcasting
		System.out.println("STIME: "+sTime);
//		receivedHello = new ArrayList<DTNHost>();
		initUnchoke();
	}

	public BroadcasterAppV3(BroadcasterAppV3 a) {
		super(a);
		
		this.seed = a.getSeed();
		this.streamID=a.getStreamID();
		
		fragment = new SADFragmentation();
		sTime = a.getSTime();
//		receivedHello = new ArrayList<DTNHost>();
		initUnchoke();
	}

	@Override
	public Message handle(Message msg, DTNHost host) {	
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
		try{
			if(type.equals(APP_TYPE)){
				String msg_type = (String) msg.getProperty("msg_type");
				
				if (msg_type.equalsIgnoreCase(HELLO)){
					System.out.println(host + " Received a hello! :D");
					
					long otherAck = (long) msg.getProperty("ack");
					int otherStatus = (int) msg.getProperty("status");
					ArrayList<Long> otherBuffermap = (ArrayList<Long>) msg.getProperty("buffermap");
					
					if (!stream.isRegistered(msg.getFrom())){ //save that we met this node for the first time
						stream.registerListener(msg.getFrom());
					}
					
					updateChunkCount(otherBuffermap); //update records of neighbor's data
				
//					//if otherNode is not listening to any stream yet
					System.out.println("otherStatus: "+otherStatus + " OtherAck: "+otherAck);
					if (broadcasted && otherStatus==-1 && otherAck==-1){
						stream.setTo(msg.getFrom());
						Message m = stream.replicate();
						((TVProphetRouterV2) host.getRouter()).addUrgentMessage(m, false);
						System.out.println(host + " SENDING BROADCAST to " + m.getTo());
						
						if (stream.getBuffermap() != null){
							sendBuffermap(host, msg.getFrom(), stream.getBuffermap());
							sentHello.add(msg.getFrom());
						}
					}
					
					else if (!hasHelloed(msg.getFrom())) {
						sendBuffermap(host, msg.getFrom(), stream.getBuffermap());
						sentHello.add(msg.getFrom());
					}
				}
				
				else if (msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){
					long chunkNeeded = (long) msg.getProperty("chunk");
					
					System.out.println("ReceivedRequest from "+msg.getFrom() + " requesting from: " +  chunkNeeded);
					//evaluate if fragment or chunk it isesend
					if (stream.getChunk(chunkNeeded)!=null){
						sendChunk(stream.getChunk(chunkNeeded), host, msg.getFrom()); //simply sending. no buffer limit [yet?]
					}
				}
				else if (msg_type.equals(INTERESTED)){
					System.out.println(host + " received INTERESTED from " + msg.getFrom());
					//evaluate response if choke or unchoke
					interestedNeighbors.put(msg.getFrom(), (int) msg.getCreationTime());
					System.out.println("Interested: " + interestedNeighbors);
					evaluateResponse(host, msg.getFrom());	
				
				}
				else if (msg_type.equalsIgnoreCase(UNINTERESTED)){
//					if (interestedNeighbors.containsKey(msg.getFrom())){
						interestedNeighbors.remove(msg.getFrom());
//					}
					if (unchoked.contains(msg.getFrom())){
						updateUnchoked(unchoked.indexOf(msg.getFrom()), null);
					}
				}
			}
		}catch(NullPointerException e){}
		return msg;
	}

	@Override
	public void update(DTNHost host) {
		if (this.host==null){
			this.host = host;
		}
		double curTime = SimClock.getTime();
		
		//startBroadcast here, once
		if (!broadcasted && curTime>=sTime){
			startBroadcast(host);
			broadcasted =  true;
		}
		checkHelloedConnection(host); //remove data of disconnected nodes
		
		if (broadcasted){
			//generate chunks here
			if (curTime - stream.getTimeLastStream() >= Stream.getStreamInterval()){ //for every interval
				stream.generateChunks(getStreamID(), fragment.getCurrIndex());
				sendEventToListeners(StreamAppReport.CHUNK_CREATED, null, host);
				updateHello(host);
			}
			
		}
		
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
					
//					recognized.removeAll(Collections.singleton(null));
//					System.out.println(host + " Recognized " + recognized);
					recognized = sortNeighborsByBandwidth(recognized);
							
					unchokeTop3(host, recognized);
					prevUnchokedList.removeAll(unchoked.subList(0, 3)); //remove an api na yana ha unchoke la gihap
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
			sendEventToListeners(StreamAppReport.UNCHOKED, unchoked.clone(), host);
			sendEventToListeners(StreamAppReport.INTERESTED, recognized.clone(), host);
//			System.out.println("Interested Nodes Now: " + recognized + " Unchoked Now: " + unchoked);
		}
	}
	
	public void startBroadcast(DTNHost host){
		stream= new Stream(host, null, APP_TYPE + ":broadcast" + 
				SimClock.getIntTime() + "-" + host.getAddress(),
				SIMPLE_MSG_SIZE);
		stream.addProperty("type", APP_TYPE);
		stream.addProperty("msg_type", BROADCAST_LIVE);
		stream.addProperty("streamID", getStreamID());
		stream.addProperty("stream_name", "tempstream");
		stream.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 1);
		stream.addProperty("time_started", SimClock.getIntTime());
		stream.setAppID(APP_ID);
		stream.startLiveStream();
		host.createNewMessage(stream); //must override, meaning start a broadcast that a stream is initiated from this peer
		//////set response size
		
		lastChokeInterval = SimClock.getTime();
		lastOptimalInterval = SimClock.getTime();
		super.sendEventToListeners(BROADCAST_LIVE, null, host);
	}
	
	public void sendBuffermap(DTNHost host, DTNHost to, Collection<Long> list){
		String id = APP_TYPE+ ":hello_" + SimClock.getIntTime() +"-" + host.getAddress() +"-" + to;
		
		long ack =-1;
		if(!stream.getBuffermap().isEmpty()){
			ack = stream.getLatestChunk().getChunkID();
		}
		
		System.out.println(host+ " sending HELLO at time " + SimClock.getIntTime() + " to " + to);
		
		Message m = new Message(host, to, id, BUFFERMAP_SIZE); //buffermap size must have basis
		m.setAppID(APP_ID);
		m.addProperty("type", APP_TYPE);
		m.addProperty("msg_type", HELLO);
		m.addProperty("status", 2);
		m.addProperty("ack", ack);
		m.addProperty("buffermap", list); // this is full buffermap
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 5);
		host.createNewMessage(m);
//		m.setTtl(5);
	
//		sentHello.put(to, SimClock.getIntTime());
	}
	
	public void updateHello(DTNHost host){
		long currAck=-1;
		
		if (stream.getLatestChunk()!=null){
			currAck = stream.getLatestChunk().getChunkID();
		}
		
		for (DTNHost h : sentHello){
//			long lastChunkSent = sentHello.get(h);
//			if (lastChunkSent<currAck){
//				int firstIndex = stream.getBuffermap().indexOf(lastChunkSent)+1;
//				int lastIndex = stream.getBuffermap().size();
//				ArrayList<Long> latestUpdates = new ArrayList<Long> (stream.getBuffermap().subList(firstIndex, lastIndex));
				ArrayList<Long> latest = new ArrayList<Long>();
				latest.add(currAck);
				sendBuffermap(host, h, latest);
//				sentHello.put(h, stream.getChunk(lastIndex-1).getChunkID());
//				System.out.println("Last hello sent: " + stream.getChunk(lastIndex-1).getChunkID());
//			}
		}
	}	
	
	@Override
	protected void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to) {
		String id = APP_TYPE + ":chunk_" + chunk.getChunkID() +  " " + chunk.getCreationTime() +"-" +to + "-" + host.getAddress();
		
		System.out.println("Chunk to send." +chunk.getChunkID());
		Message m = new Message(host, to, id, (int) chunk.getSize());		
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_CHUNK_SENT);
		m.addProperty("chunk", chunk);	
		m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 2);
		host.createNewMessage(m);

		sendEventToListeners(CHUNK_DELIVERED, chunk, host);
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
//		else if (unchoked.contains(to)){ ummmm?
//			sendResponse(host, to, true);
//		}
	}
	
	public void sendResponse(DTNHost host, DTNHost to, boolean isOkay){
		
		String id;
		String msgType; 

		if (isOkay){
			id = APP_TYPE + ":UNCHOKE_" +  SimClock.getIntTime() + "-" + host.getAddress()  +"-" + to;
			msgType = UNCHOKE;
			System.out.println(host + " sending unchoke to " + to);
		}
		else{
			id = APP_TYPE + ":CHOKE_" + SimClock.getIntTime()+ "-" + host.getAddress()  + "-" + to;
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

//	private void removeUninterested(DTNHost host){
//	
//		unchoked.remove(host);
//		interestedNeighbors.remove(host);
//		
////		for (int i=0; i<4; i++){
////			if (recognized.contains(unchoked.get(i)) && unchoked.get(i)!=null){
////				sendResponse(host, unchoked.get(i), false); //send CHOKE to nodes that are uninterested but in our unchoked before
////				unchoked.set(i, null);
////			}
////		}
//	}
	
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
	
//	private ArrayList<DTNHost> getRecognized(){
//		int curTime = SimClock.getIntTime();
//		ArrayList<DTNHost> recognized = new ArrayList<DTNHost>(); //save here the recent INTERESTED requests
//	
//		//extract recent INTERESTED messages, delete an diri recent
//		Iterator<Map.Entry<DTNHost, Integer>> entryIt = interestedNeighbors.entrySet().iterator();
//		while(entryIt.hasNext()){
//			Entry<DTNHost, Integer> entry = entryIt.next();
//		
//			//irerecognize ko la an mga nagsend interested for the past 10 seconds
////			if ( (curTime - entry.getValue()) <= 10 ){ 
//				recognized.add(entry.getKey());
////			}
////			else{
////				entryIt.remove();
////			}
//		}
//		sortNeighborsByBandwidth(recognized);
//		return recognized;
//	}
	
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
		prevRand = unchoked.get(3);
		System.out.println("Recognized now without all those at unchoked:" + recognized);
		
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
		temp = new ArrayList<DTNHost>();
		temp.add(null);
		
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
}
