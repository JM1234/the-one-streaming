package streaming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;

import fragmentation.Fragment;

public class StreamProperties {

	private int playing=-1; //index currently playing
	private double startTime=-1;
	private long chunkStart=-1;
	
	private String streamID;
	private ArrayList<Fragment> receivedFragments;
	private LinkedHashMap<Long, StreamChunk> receivedChunks;
	
	private long ack=-1; //last consecutive sent

	public StreamProperties(String streamID){
		this.streamID = streamID;
		receivedChunks = new LinkedHashMap<Long, StreamChunk>();
		receivedFragments = new ArrayList<Fragment>();
	}
	
	public void addChunk(StreamChunk chunk){
		receivedChunks.put(chunk.getChunkID(), chunk);
	}
	
	public void setAck(long curr){
		if (curr-ack == 1 || curr == 0){
			ack=curr;
		}
		while (receivedChunks.containsKey(ack+1)){
			ack++;
		}
		System.out.println("Ack: "+ack);
	}
	
	public long getAck(){
		return ack;
	}

	public String getStreamID(){
		return streamID;
	}
	
	public void setStreamID(String streamID){
		this.streamID = streamID;
	}
	
	public void addFragment(Fragment fragment){
		receivedFragments.add(fragment);
	}
	
	public ArrayList<Fragment> getFragments(){
		return receivedFragments;
	}
	
	public Fragment getFragment(int i){
		return receivedFragments.get(i);
	}
	
	//simply sending all the chunks it already has, whether bundled or not
	public ArrayList<Long> getBufferMap(){
		ArrayList<Long> temp = new ArrayList<Long>();
		
		for (StreamChunk c: receivedChunks.values()){
			temp.add(c.getChunkID());
		}
		Collections.sort(temp);
		return temp; //arrange chunks based on id
	}

	public void playNext(){
		playing = playing+1;
	}
	
	public int getPlaying(){
		return playing;
	}
	
	public int getNext(){
		return playing+1;
	}
	
	public boolean isReady(long i){
		try{
			if(receivedChunks.get(i) !=null){
				return true;
			}
		}
		catch(IndexOutOfBoundsException e){}
		return false;
	}
	
	/////function pagkuha pinakauna na sulod han hashmap
	public long getStartChunk(){
		return chunkStart;
	}
	
	public ArrayList<StreamChunk> getReceived(){
		ArrayList<StreamChunk> coll = new ArrayList<StreamChunk> (receivedChunks.values());
		return coll;
	}

	public StreamChunk getChunk(long id){
		return receivedChunks.get(id);
	}
	
	public void setStartTime(double startTime){
		this.startTime = startTime;
	}
	
	public double getStartTime(){
		return startTime;
	}
	
	public void setChunkStart(long chunkStart){
		this.chunkStart = chunkStart;
		if (playing<chunkStart) playing=(int) (chunkStart);
		this.ack=chunkStart;
	}
	
	public StreamChunk getChunk(double time){
	////within boundary	
		for(long key : receivedChunks.keySet()){
			StreamChunk chunk = receivedChunks.get(key);
			
			double stime = chunk.getCreationTime();
			if ((stime<=time) && time<stime+Stream.getStreamInterval())
				return chunk;
		}
		return null;
	}
}