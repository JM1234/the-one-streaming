package streaming;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import core.DTNHost;
import core.Message;

/**
 * Works like a MessageListener, only with a stream of chunks.
 * Handles generation of chunks.
 * @author janz
 *
 */
public class Stream extends Message {
	
	
	private static int chunkNo=0000;
	public boolean isStreaming = true;

	private static int stream_interval;
	private static int accumChunkSize =0;

	private LinkedHashMap<Long, StreamChunk> streamSet;
	private HashMap<DTNHost, Integer> listener;
	private StreamChunk latestChunk;
	
	protected DTNHost from;
	protected DTNHost to;
	protected String id;
	private double timeLastStream;	

	//variable for limitTime. randomize variable on how long the live stream is gonna last
	public Stream(DTNHost from, DTNHost to, String id, int size) {
		super(from, to, id, size);
		this.from = from;
		this.to = to;
		this.id = id;

		Stream.stream_interval = StreamChunk.getDuration();
		streamSet= new LinkedHashMap<Long,StreamChunk>() ;
		listener = new HashMap<DTNHost, Integer>();
	}
	
	public void startLiveStream(){
		isStreaming = true;
	}

	public void stopStream(){
		streamSet.clear();
		isStreaming=false;
	}
	
	public StreamChunk getLatestChunk(){
		return latestChunk;
	}
	
	public void generateChunks(String fileID, int fID){

		//create chunks
		long chunkID = generateChunkID(fileID, chunkNo++);
		StreamChunk chunk = new StreamChunk(id, chunkID);				
		chunk.setSize(StreamChunk.m480p);
		chunk.setFragmentIndex(fID);
		streamSet.put(chunkID, chunk);
		latestChunk = chunk; 
		
		try{
			timeLastStream = getLatestChunk().getCreationTime();
		}catch(NullPointerException e){
			timeLastStream= 0;
		}
		System.out.println("Generated stream: " +chunk.getChunkID() + " Fragment: "+chunk.getFragmentIndex()
		+ " Time created: "+ chunk.getCreationTime());
		
		accumChunkSize +=chunk.getSize();
	}
	
	public void resetAccumChunkSize(){
		accumChunkSize = 0;
	}
	
	public int getAccumChunkSize(){
		return accumChunkSize;
	}
	
	private long generateChunkID(String fileID, int chunkNo){
//		long chunkID = (long) fileID+chunkNo;
		long chunkID = chunkNo;
		return chunkID;
	}
	
	public void registerListener(DTNHost host){
//		System.out.println("New listener: " + host.getAddress());
		listener.put(host, -1);
	}
	
	public void removeListener(DTNHost host){
		listener.remove(host);
	}
		
	public HashMap<DTNHost, Integer> getAllListener(){
		return listener;
	}

	public boolean isRegistered(DTNHost host){
		return listener.get(host)!=null? true:false;
	}
	
	public StreamChunk getChunk(double time){
	////within boundary	
		for(long key : streamSet.keySet()){
			StreamChunk chunk = streamSet.get(key);
			
			double stime = chunk.getCreationTime();
			if ((stime<=time) && time<stime+stream_interval)
				return chunk;
		}
		return null;
	}
	
	public StreamChunk getChunk(long chunkID){
		return streamSet.get(chunkID);
	}
	
	public double getTimeLastStream(){
		return timeLastStream;
	}
	
//	public ArrayList<Long> getChunks(){
//		ArrayList<Long> cId = new ArrayList<Long>();
//		
//		for(StreamChunk values: streamSet.values()){
//			cId.add(values.getChunkID());
//		}
////		Collections.sort(cId);
//		return cId;
//	}
	
	public static int getStreamInterval(){
		return stream_interval;
	}

	
	///used by broadcaster to maintain record of what it has sent as buffermap
	public int getLastUpdate(DTNHost host){
		return listener.get(host);
	}
	
	public void setLastUpdate(DTNHost host, int lastIndex){
		listener.put(host, lastIndex);
	}
	
	public int getNoOfChunks(){ //noofchunks
		return streamSet.size();
	}

	public ArrayList<StreamChunk> getChunks(){
		ArrayList<StreamChunk> s = new ArrayList<StreamChunk> (streamSet.values());
		return s;
	}
	
	public ArrayList<Long> getBuffermap(){
		ArrayList<Long> ids = new ArrayList<Long>();
		for (StreamChunk c: getChunks()){
			ids.add(c.getChunkID());
		}
		return ids;
	}
}

