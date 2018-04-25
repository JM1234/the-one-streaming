package applications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import core.Application;
import core.CBRConnection;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import fragmentation.Fragment;
import fragmentation.SADFragmentation;
import routing.MessageRouter;
import routing.TVProphetRouter;
import streaming.Stream;
import streaming.StreamChunk;
import streaming.StreamProperties;
import util.Tuple;

public class BroadcasterApp extends StreamingApplication {

	public static final String STREAM_TIME = "streamTime";

	private boolean broadcasted=false;
	private static double sTime;
	
	private int		seed = 0;
	private String 	streamID;
	
	private Random	r;
	private Stream 	stream;
	private SADFragmentation fragment;
	private int connectionSize;
	private ArrayList<Integer> sentBroadcast = new ArrayList<Integer> ();
	
	public BroadcasterApp(Settings s) {
		super(s);
		
		fragment = new SADFragmentation();
		r=new Random();
		sTime = 0; //s.getDouble("streamTime") * r.nextDouble(); //time to start broadcasting
		System.out.println("STIME: "+sTime);
	}

	public BroadcasterApp(BroadcasterApp a) {
		super(a);
		
		this.seed = a.getSeed();
		this.streamID=a.getStreamID();
		
		fragment = new SADFragmentation();
		sTime = 0;//a.getSTime();
		
	}

	@Override
	public Message handle(Message msg, DTNHost host) {
		String type = (String) msg.getProperty("type");
		if (type==null) return msg;
		
		if(type.contains(APP_TYPE)){
			String msg_type = (String) msg.getProperty("msg_type");
			
			if (msg_type.equalsIgnoreCase(BROADCAST_REQUEST)  && msg.getTo()==host){
				//register new listener
				StreamProperties props = new StreamProperties(streamID);
//				stream.registerListener(msg.getFrom(), props);

				double time = msg.getCreationTime();
				StreamChunk chunkNeeded = stream.getChunk(time);
				
				System.out.println("Broadcast request @ time: "+time);
				System.out.println("Chunk needed: " + stream.getChunk(time).getChunkID());
				
//				if(fragment.doesExist(chunkNeeded.getFragmentIndex())){ //send fragment
//					sendFragment(chunkNeeded.getFragmentIndex(), host, msg.getFrom());
//				}

//				else{ //send chunk
				
				ArrayList<StreamChunk> missingC = getMissingChunks(null, chunkNeeded.getChunkID()-1);
				System.out.println("Found Missing for "+msg.getFrom());
//					sendChunk(chunkNeeded, host, msg.getFrom(), true);
//				for(StreamChunk c: missingC){
//					System.out.println("CURRENTLY SENDING: "+c);
//					sendChunk(c, host, msg.getFrom(), false);
//				}
//				}
			}
			
			else if (msg_type.equalsIgnoreCase(HELLO)){
				System.out.println("Received hello from "+msg.getFrom());
				
				long otherAck = (long) msg.getProperty("ack");
				int otherStatus = (int) msg.getProperty("status");
				ArrayList<Long> chunks = ((ArrayList<Long>) msg.getProperty("buffermap"));
			
				if (broadcasted && otherStatus==-1 && otherAck==-1){
					stream.setTo(msg.getFrom());
					Message m = stream.replicate();
					((TVProphetRouter) host.getRouter()).addUrgentMessage(m, false);
				}
				else{
					ArrayList<StreamChunk> missingC;
					
					//if nahulat first ever chunk
					if (otherStatus == WatcherApp.WAITING && otherAck==-1){
						double t = (double) msg.getProperty("startTime");
						System.out.println("T: "+t);
						System.out.println("Chunk needed: " + stream.getChunk(t).getChunkID());
						long needId= stream.getChunk(t).getChunkID();
						missingC = getMissingChunks(chunks, needId-1);
					}
					else{
						missingC = getMissingChunks(chunks, otherAck);
					}
					
					System.out.println("br sending the missing:" +missingC);

					/////ha chunk pala ini
					try{
						Connection curCon = null;
						for (Connection con: host.getConnections()){
							if (con.getOtherNode(host).equals(msg.getFrom())){
								curCon = con;
								break;
							}
						}
			
//						System.out.println("Found Missing at  "+msg.getFrom() + " : " + missingC);
						for(StreamChunk c: missingC){
//							System.out.println("CURRENTLY SENDING: "+c);
							sendChunk(c, host, msg.getFrom());
						}
						
					}catch(IndexOutOfBoundsException i){}
					
					
//					ArrayList<Integer> wF = (ArrayList<Integer>) msg.getProperty("fragments");
//					ArrayList<Integer> mF = (ArrayList<Integer>) getMissingFragments(wF);

//					for(int fId:mF){
//						System.out.println("Missing: "+fId + " sending now.");
//						sendFragment(fId, host, msg.getFrom());
//					}

				}
			}
	
		}
		
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
			if (curTime - stream.getTimeLastStream() >= Stream.getStreamInterval()){
				
				stream.generateChunks(getStreamID(), fragment.getCurrIndex());
				
				StreamChunk latestChunk = stream.getLatestChunk();
							
				String id = APP_TYPE + ":chunk-" + latestChunk.getChunkID()+  " " + latestChunk.getCreationTime() + "-" +host.getAddress();
				Message m = new Message(host, null, id, (int) latestChunk.getSize());		
				m.addProperty("type", APP_TYPE);
				m.setAppID(APP_ID);
//				m.addProperty("msg_type", CHUNK_SENT);
				m.addProperty("chunk", latestChunk);	
				host.createNewMessage(m);
				System.out.println("Has message? "+id + " : " + host.getRouter().hasMessage(id));
				
//				((TVProphetRouter) host.getRouter()).addUrgentMessage(m, true);  
				
//				sendEventToListeners(CHUNK_DELIVERED, chunk, host);
				if ( (stream.getNoOfChunks()%SADFragmentation.NO_OF_CHUNKS_PER_FRAG) == 0){ /////number of chunks dapat
					long latestID = stream.getLatestChunk().getChunkID();
					manageFragments(latestID, stream.getAccumChunkSize());
				}
				
				try{
//						if (((TVProphetRouter)host.getRouter()).getMessagesForConnected().size()>20
//								&& !host.getConnections().get(0).isTransferring()){ //if damo na duro an ada ha buffer
//							System.out.println("have to delete some messages.");
					if (host.getConnections().size()>0){
//						host.requestDeliverableMessages(host.getConnections().get(0));
						sendUpdateToListeners(host, host.getConnections().get(0).getOtherNode(host)); ////didto ada ini dapat ha con.isUp()? diri pwd per second lang.
					}	
				}catch(NullPointerException e){}		
			}
			
			try{
				ArrayList<Connection> con = (ArrayList<Connection>) host.getConnections();
				if (con.size() >= connectionSize){
					for (int i=0; i< con.size(); i++){
						Connection curr = con.get(i);
						if (curr.isUp() && sentBroadcast.get(i)!=1){
//								( !curr.getOtherNode(host).getRouter().hasMessage(stream.getId()))){//&& con.isInitiator(host)) { /////////dzae ha router diri permanent it message. daapt check ha application
							//handle node
							stream.setTo(curr.getOtherNode(host));
							Message m = stream.replicate();
							((TVProphetRouter) host.getRouter()).addUrgentMessage(m, false);
							System.out.println("BROADCAST sent." + stream.getTo());
							sentBroadcast.add(i, 1);
						}
						else{
							//do something so that it will send hello
							host.getRouter().requestDeliverableMessages(curr); ///?
						}
					}
				}
			}catch(IndexOutOfBoundsException i){
				connectionSize=0;
				sentBroadcast.clear();
			}
		}
		
	}

	private void startBroadcast(DTNHost host){
		stream= new Stream(host, randomHost(), APP_TYPE + ":broadcast" + 
				SimClock.getIntTime() + "-" + host.getAddress(),
				SIMPLE_MSG_SIZE);
		stream.addProperty("type", APP_TYPE);
		stream.addProperty("msg_type", BROADCAST_LIVE);
		stream.addProperty("streamID", getStreamID());
		stream.setAppID(APP_ID);
		stream.startLiveStream();
		host.createNewMessage(stream); //must override, meaning start a broadcast that a stream is initiated from this peer
		//////set response size
		super.sendEventToListeners(BROADCAST_LIVE, null, host);

	}
	
	public void sendUpdateToListeners(DTNHost host, DTNHost listener){ //
//		HashMap<DTNHost, Long> listeners = stream.getAllListener();
		
//		for(DTNHost listener :listeners.keySet()){
//			long lastID= listeners.get(listener); //get last sent to this host

			//send next chunk to listener based on last sent
//			StreamChunk chunk = stream.getChunk(lastID+1);
//			if (chunk!=null){
//				sendChunk(chunk, host, listener);
//				stream.setLastUpdate(host, lastID+1);
//		}
//		}
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

	public void sendFragment(int fID, DTNHost host, DTNHost to){
		Fragment f = fragment.getFragment(fID);
		String mID = APP_TYPE + ":fragment " + f.getTimeCreated() + "-" +host.getAddress();
		
		Message m = new Message(host, to, mID, fragment.getFragSize());
		m.addProperty("type", APP_TYPE);
		m.setAppID(APP_ID);
//		m.addProperty("msg_type", FRAGMENT_SENT);
		m.addProperty("fragment", f);
		if (mID.contains("first")) m.addProperty("streamID", streamID); //streamID dapat
		
		host.createNewMessage(m);
		sendEventToListeners(FRAGMENT_DELIVERED, fragment, host); //not yet handled on report
		System.out.println("Sent fragment " + fID + "to "+to);
	}

	protected void sendChunk(StreamChunk chunk, DTNHost host, DTNHost to){
		
		String id = APP_TYPE + ":chunk-" + chunk.getChunkID()+  " " + chunk.getCreationTime(); //+ "-" +chunk.;
		
//		if (first) //di na ada ini kailangan 
//			id = APP_TYPE + ":first " + chunk.getCreationTime() + "-" +host.getAddress();
		
		System.out.println("HAS MESSAGE? " + id + ": "+ host.getRouter().hasMessage(id));
		
		if (host.getRouter().hasMessage(id)){//&& !con.isTransferring()){
			System.out.println("Message already exist.");
			Message m =  ((TVProphetRouter) host.getRouter()).getStoredMessage(id); ///////////returns null. kailangan ayuson
			Message repM = m.replicate();
			repM.setReceiveTime(0);
			repM.setTo(to);
			((TVProphetRouter) host.getRouter()).addUrgentMessage(repM, false);

//			host.getRouter().deleteMessage(m.getId(), false);
//			host.getRouter().sendMessage(id, to); ///////di pa ngayan ak sure if ginuuna na ini pagsend
//			System.out.println("Start sending: "+chunk.getChunkID());
		}
		
		else{	
			System.out.println("Created new." +chunk.getChunkID());
			Message m = new Message(host, to, id, (int) chunk.getSize());		
			m.addProperty("type", APP_TYPE);
			m.setAppID(APP_ID);
//			m.addProperty("msg_type", CHUNK_SENT);
			m.addProperty("chunk", chunk);	
//			m.setReceiveTime(0);
			host.createNewMessage(m);
			
//			((TVProphetRouter) host.getRouter()).addUrgentMessage(m, true);  
			
			sendEventToListeners(CHUNK_DELIVERED, chunk, host);
		}

	}
	
	@Override
	public Application replicate() {
		return new BroadcasterApp(this);
	}
	
	public double getSTime(){
		return sTime;
	}
	
	private ArrayList<StreamChunk> getMissingChunks(ArrayList<Long> chunks, long ack){
		System.out.println("@ getting missing chunks");
		
		ArrayList<StreamChunk> missing = new ArrayList<StreamChunk>();
		ArrayList<StreamChunk> has = (	ArrayList<StreamChunk>) stream.getChunks();

		System.out.println("has: "+has.size());
		System.out.println("from ack: "+ ack);
		
//		//nag out of bounds. why?
//		if (ack<stream.getLatestChunk().getChunkID()){ ///if has.contains(ack+1)
//			missing.addAll((int) ack, has);
//		}
	
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
		return missing;
	}
	
	private ArrayList<Integer> getMissingFragments(ArrayList<Integer> f, int ack){
		ArrayList<Integer> missing = new ArrayList<Integer>();
		ArrayList<Integer> temp = new ArrayList<Integer>(fragment.getFragments());
	
		int i;
		if (ack==-1) i=0;
		else i=ack;
		for (; i<temp.size(); i++){
			int b= temp.get(i);
//			if (!f.contains(b)){
				missing.addAll(ack, temp);
//				missing.add(b);
//			}
		}
		return missing;
	}
	
	private void handleNode(DTNHost otherNode){

		/**
		 * Upon meeting, one of the nodes will send his/her stream information
		 * then whoever needs a more urgent (previous chunks) or
		 * in a waiting status (meaning, it's stuck), it will request
		 * or send stuff first
		 *
		 */
		
		//weigh each needs. if you are more urgent, send your need
		
		/*
		 * If initiator, requestDeliverableMessage
		 */
		
		if (otherNode.getRouter() instanceof TVProphetRouter){
			TVProphetRouter router = (TVProphetRouter) otherNode.getRouter();
			router.getTransSize(otherNode);
		}		
			
			//calculate sizes and type of fragment to send
			
			//if you are the ultimateSource
				//send the needs
		
			//else
				//if you have the chunks needed,
					//call fragmentation and retrieve this need
				//else
					//send you have nothing. send what you want
		
	}
}
