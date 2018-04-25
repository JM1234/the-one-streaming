package applications;

import java.util.ArrayList;
import java.util.HashMap;
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
import fragmentation.SADFragmentation;
import routing.TVProphetRouter;
import routing.TVProphetRouterV2;
import streaming.Stream;
import streaming.StreamChunk;
import streaming.StreamProperties;
import util.Tuple;

public class BroadcasterAppV2 extends StreamingApplication{

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
	private ArrayList<DTNHost> receivedHello;
	private boolean isListener=false;
	private HashMap<DTNHost, Integer> bufferCtr; //counter for buffer per node

	public BroadcasterAppV2(Settings s) {
		super(s);
		
		fragment = new SADFragmentation();
		r=new Random();
		sTime = 0; //s.getDouble("streamTime") * r.nextDouble(); //time to start broadcasting
		System.out.println("STIME: "+sTime);
		receivedHello = new ArrayList<DTNHost>();
		bufferCtr = new HashMap<DTNHost, Integer>();
	}

	public BroadcasterAppV2(BroadcasterAppV2 a) {
		super(a);
		
		this.seed = a.getSeed();
		this.streamID=a.getStreamID();
		
		fragment = new SADFragmentation();
		sTime = a.getSTime();
		receivedHello = new ArrayList<DTNHost>();
		bufferCtr = new HashMap<DTNHost, Integer>();
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
					ArrayList<Long> chunks = ((ArrayList<Long>) msg.getProperty("buffermap"));
				
					if (broadcasted && otherStatus==-1 && otherAck==-1){ //if otherNode is not listening yet
						stream.setTo(msg.getFrom());
						Message m = stream.replicate();
						((TVProphetRouterV2) host.getRouter()).addUrgentMessage(m, false);
					}
					else{ //tell the otherNode what I have
						sendBuffermap(host, msg.getFrom());
					}
					receivedHello.add(msg.getFrom());
				}
				
				else if (msg_type.equalsIgnoreCase(BROADCAST_REQUEST)){
					long sInterest = (long) msg.getProperty("sInterest");
					long eInterest = (long) msg.getProperty("eInterest");
					
					System.out.println("ReceivedRequest from "+msg.getFrom());
					//evaluate if fragment or chunk it isesend
					
					//send chunks
					for(long i=sInterest; i<=eInterest; i++){
						StreamChunk c = stream.getChunk(i);
						sendChunk(c, host, msg.getFrom()); //simply sending. no buffer limit yet
					}
				}
				
			}
		}catch(NullPointerException e){}
		return msg;
	}

	@Override
	public void update(DTNHost host) {
		double curTime = SimClock.getTime();
		
		if (curTime >= sTime && !broadcasted){
			startBroadcast(host);
			broadcasted = true;
		}

		if(broadcasted){
			if (curTime - stream.getTimeLastStream() >= Stream.getStreamInterval()){ //for every interval
				stream.generateChunks(getStreamID(), fragment.getCurrIndex());
				
				checkHelloedConnection(host);
				try{
					for (Connection c : host.getConnections()){
						DTNHost otherHost = c.getOtherNode(host);

						if (c.isUp() && hasHelloed(c.getOtherNode(host))) {
							updateHello(host, otherHost);
						}
					}
				}catch(IndexOutOfBoundsException e){
				}
				
				if ( (stream.getNoOfChunks()%SADFragmentation.NO_OF_CHUNKS_PER_FRAG) == 0){ /////number of chunks dapat
					long latestID = stream.getLatestChunk().getChunkID();
					manageFragments(latestID, stream.getAccumChunkSize());
				}
			}
		}
	}
	
	private void startBroadcast(DTNHost host){
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
		super.sendEventToListeners(BROADCAST_LIVE, null, host);

	}
	
	public void updateHello(DTNHost host, DTNHost otherHost){
		int curTime = SimClock.getIntTime();
		
		if (curTime - sentHello.get(otherHost) >= HELLO_UPDATE){ //if it's time to send an updated HELLO
			System.out.println(host +" sending an updated hello to "+ otherHost + " @ " + curTime);
			sendBuffermap(host, otherHost);  
			sentHello.put(otherHost, SimClock.getIntTime());
		}
	}
	
	private Message sendBuffermap(DTNHost host, DTNHost to){
		String id = APP_TYPE+ ":hello" + + SimClock.getIntTime() +"-" +host.getAddress(); //+ SimClock.getIntTime()
		
			Message m = new Message(host, to, id, BUFFERMAP_SIZE); //buffermap size must be defined.
			m.addProperty("type", APP_TYPE);
			m.setAppID(APP_ID);
			m.addProperty("msg_type", HELLO);
			m.addProperty("status", -1);
//			m.addProperty("buffermap", chunks); //////should be full buffermap
			m.addProperty("ack", stream.getLatestChunk().getChunkID());
			m.addProperty("watcherType", -1); //this should not be sent?
			m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 1);
			host.createNewMessage(m);
			m.setTtl(5);
			
			sentHello.put(to, SimClock.getIntTime());
			return m;
	}
	
	private void manageFragments(long id, int fSize){
		ArrayList<StreamChunk> bundle = new ArrayList<StreamChunk>();
		long boundary=id;
		
		for(int i=0; i<fragment.NO_OF_CHUNKS_PER_FRAG; i++, boundary--){
			bundle.add(stream.getChunk(id-boundary));
		}
		
		//sort fragments
		fragment.setFragmentSize(fSize);
		fragment.createFragment(bundle);
		stream.resetAccumChunkSize();
	}
	
	public void sendFragment(int fID, DTNHost host, DTNHost to, int fragmentLevel){
		Fragment f = fragment.getFragment(fID);
		String mID = APP_TYPE + ":fragment " + f.getTimeCreated() + "-" +host.getAddress();
		
		Message m = new Message(host, to, mID, fragment.getFragSize());
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
		m.addProperty("msg_type", BROADCAST_FRAGMENT_SENT);
		m.addProperty("fragment", f);
		m.addProperty("f_level", fragmentLevel);
		host.createNewMessage(m);
		
		sendEventToListeners(FRAGMENT_DELIVERED, fragment, host); //not yet handled on report
		System.out.println("Sent fragment " + fID + "to "+to);
	}

	public void sendUpdateToListeners(StreamChunk chunk, DTNHost host, DTNHost listener){
		sendChunk(chunk, host, listener);
	}
	
	@Override
	protected void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to) {
		
		String id = APP_TYPE + ":chunk-" + chunk.getChunkID()+  " " + chunk.getCreationTime(); //+ "-" +chunk.;
	
//		if(bufferCtr.get(to) < 10){
//			if (host.getRouter().hasMessage(id)){
//				Message m =  ((TVProphetRouterV2) host.getRouter()).getStoredMessage(id);
//				m.setTo(to);
//				System.out.println(m + "exists for "+ m.getTo() + " . Rtime: "+m.getReceiveTime());
//	
//				Message repM = m.replicate();
//				repM.setTo(to);
//				host.getRouter().deleteMessage(m.getId(), false);
////				((TVProphetRouterV2) host.getRouter()).addUrgentMessage(repM, false);
//				System.out.println(repM + "replicated for "+ m.getTo() + " . Rtime: "+repM.getReceiveTime());
//			}
//	
//			else if (!host.getRouter().hasMessage(id)){
				System.out.println("Created new message to send." +chunk.getChunkID());
				Message m = new Message(host, to, id, (int) chunk.getSize());		
				m.addProperty("type", APP_TYPE);
				m.setAppID(APP_ID);
				m.addProperty("msg_type", BROADCAST_CHUNK_SENT);
				m.addProperty("chunk", chunk);	
				m.addProperty(TVProphetRouterV2.MESSAGE_WEIGHT, 3);
				host.createNewMessage(m);
	
				sendEventToListeners(CHUNK_DELIVERED, chunk, host);
//			}
//		}
	}

	private ArrayList<StreamChunk> getMissingChunks(ArrayList<Long> chunks, long ack){
		
		ArrayList<StreamChunk> missing = new ArrayList<StreamChunk>();
		ArrayList<StreamChunk> has = (	ArrayList<StreamChunk>) stream.getChunks();

		int i=0;
		while(has.get(i).getChunkID()<=ack){
			i++;
		}

		for (int ctr = 0; i<has.size() && ctr<10 ; i++, ctr++){ //nag aassume na tanan na missing is consecutive from ack
			StreamChunk c = has.get(i);
			try{
				if (c.getChunkID() > ack && !chunks.contains(c)){
					missing.add(c);
				}
			}catch(NullPointerException e){
				missing.add(c);
			}
		}
		return missing;
	}
	
	private void evaluateMissing(ArrayList<StreamChunk> chunks, DTNHost host, DTNHost to){
		System.out.println("Evaluating fragments.");
		
		ArrayList<Long> cIds = new ArrayList<Long>();
		
		for (StreamChunk ch : chunks){
			cIds.add(ch.getChunkID());
		}
		
		for(int i=0; i<chunks.size(); i++){
			StreamChunk c  = chunks.get(i);
			int fID = c.getFragmentIndex();
			System.out.println("Ev Missing: "  + c.getChunkID() + " fID: "+fID);
			if (fragment.doesExist(fID)){
				sendFragment(fID, host, to, 0); //no fragment level yet
				
				Fragment f = fragment.getFragment(fID);	
				System.out.println(fID + f.getEndChunk());
				System.out.println("If missing contains: " +cIds.contains(f.getEndChunk()+1) + 
						" index of missing: " + cIds.indexOf(f.getEndChunk() + 1) );
				
				if ( stream.getChunk(f.getEndChunk()+1)!= null && 
						cIds.contains(f.getEndChunk() + 1) ){ 	
					i = cIds.indexOf(f.getEndChunk());
				}
				else{	
					break;
				}
			}
			else{ //fragment does not exist, send chunk as usual
				this.sendChunk(c, host, to);
				i++;
			}
		}
	}
	
	@Override
	public Application replicate() {
		return new BroadcasterAppV2(this);
	}

	public double getSTime(){
		return sTime;
	}	
}
