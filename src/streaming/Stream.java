package streaming;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;

import core.DTNHost;

/**
 * Works like a MessageListener, only with a stream of chunks.
 * Handles generation of chunks.
 * @author janz
 *
 */
public class Stream {
	
	private int chunkNo=0000;
	public boolean isStreaming = true;

	private int streamInterval;
	private int accumChunkSize =0;
	private double byterate;

	private LinkedHashMap<Long, StreamChunk> streamSet;
	private HashMap<DTNHost, Integer> listener;
	private ArrayList<Long> temp;
	private StreamChunk latestChunk;
	
	protected DTNHost from;
	protected DTNHost to;
	protected String id;
	private double timeLastStream;	
	private String streamID;
	private double timeStarted;
	
	//variable for limitTime. randomize variable on how long the live stream is gonna last
	public Stream(String streamID, int streamInterval, double byterate, double timeStarted) {
		this.streamID = streamID;
		this.byterate = byterate;
		this.timeStarted = timeStarted;
		
		this.streamInterval = streamInterval;
		streamSet= new LinkedHashMap<Long,StreamChunk>() ;
		listener = new HashMap<DTNHost, Integer>();
		temp = new ArrayList<Long>();
	}
	
	public int getStreamInterval(){
		return streamInterval;
	}
	
	public double getTimeStarted(){
		return timeStarted;
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
		chunk.setSize(byterate);
		chunk.setFragmentIndex(fID);
		streamSet.put(chunkID, chunk);
		latestChunk = chunk; 
		
		try{
			timeLastStream = getLatestChunk().getCreationTime();
		}catch(NullPointerException e){
			timeLastStream= 0;
		}
	
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
			if ((stime<=time) && time<stime+streamInterval)
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

	///used by broadcaster to maintain record of what it has sent as buffermap
	public int getLastUpdate(DTNHost host){
		return listener.get(host);
	}
	
	public void setLastUpdate(DTNHost host, int lastIndex){
		listener.put(host, lastIndex);
	}
	
	public int getNoOfChunks(){ 
		return streamSet.size();
	}

	public Collection<StreamChunk> getChunks(){
		return streamSet.values();
	}
	
	public ArrayList<Long> getBuffermap(){
		temp.clear();
		for (StreamChunk c: getChunks()){
			temp.add(c.getChunkID());
		}
		return temp;
	}
	
	public double getByterate(){
		return byterate;
	}
}

