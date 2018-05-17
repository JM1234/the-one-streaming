package streaming;

import java.util.ArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.TreeMap;

import fragmentation.Fragment;

public class StreamProperties {

	private long playing=-1; //index currently playing
	private double startTime=-1;
	private long chunkStart=-1;
	private int prebuffer;
	
	private String streamID;
	private ArrayList<Fragment> receivedFragments;
	private TreeMap<Long, StreamChunk> receivedChunks;
	
	private long ack=-1; //last consecutive sent
	private static double byterate;
	private static int durationPerChunk;
	private ArrayList<Long> temp;
	
	public StreamProperties(String streamID){
		this.streamID = streamID;
		
		receivedChunks = new TreeMap<Long, StreamChunk>();
		temp = new ArrayList<Long>();
	}

	public void initProps(int durationPerChunk, double byterate){
		StreamProperties.byterate = byterate;
		StreamProperties.durationPerChunk = durationPerChunk;
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
	public ArrayList<Long> getBuffermap(){
		temp.clear();
		for (StreamChunk c: receivedChunks.values()){
			temp.add(c.getChunkID());
		}
		Collections.sort(temp);
		return temp; //arrange chunks based on id
	}

	public void playNext(){
		playing = playing+1;
	}
	
	public long getPlaying(){
		return playing;
	}
	
	public long getNext(){
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
	
	public Collection<StreamChunk> getReceived(){
		return receivedChunks.values();
//		return coll;
	}
	
	public void skipNext(){
		playing++;
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
			if ((stime<=time) && time<stime+durationPerChunk)
				return chunk;
		}
		return null;
	}
	
	public void setPrebuffer(int prebuffer){
		this.prebuffer = prebuffer;
	}
	
	public boolean isBufferReady(long id){
		int ctr=0;
		
		for (long toPlay = id+1; toPlay<=receivedChunks.lastKey() && ctr< prebuffer/2 ; toPlay++, ctr++){
			if (!receivedChunks.containsKey(toPlay)){
				break;
			}
		}
		return (ctr==(prebuffer/2)? true:false);
	}

	public int getStreamInterval() {
		return durationPerChunk;
	}

	public double getByterate() {
		return byterate;
	}
}