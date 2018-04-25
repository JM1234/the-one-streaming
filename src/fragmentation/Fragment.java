package fragmentation;

import java.util.ArrayList;

import core.SimClock;
import streaming.StreamChunk;

public class Fragment {

	
	private int id; //index id
	private ArrayList<StreamChunk> bChunks;
	private double timeCreated;
	private String sourceId;
	private int startPosition; //starting chunk id of the fragment
	private int endPosition;
	private int interrupted;
	private int size; //in bytes
	
	public Fragment(int id, ArrayList<StreamChunk> bChunks){
		this.id = id;
		this.bChunks = new ArrayList<StreamChunk> (bChunks);
		timeCreated = SimClock.getTime();
	}
	
	public double getTimeCreated(){
		return timeCreated;
	}
	
	public ArrayList<StreamChunk> getBundled(){
		return bChunks;
	}
	
	public int getId(){
		return id;
	}
	
	public int startPosition(){
		return startPosition;
	}
	
	public void setStartPosition(int startPosition){
		this.startPosition = startPosition;
	}
	
	public void endPosition(int endPosition){
		this.endPosition = endPosition;
	}
	
	public boolean isInterrupted(){
		return (interrupted == 1? true: false);
	}
	
	public long getEndChunk(){
		return bChunks.get(bChunks.size()-1).getChunkID();
	}
	
	public int getSize(){
		return size;
	}
	
	public int getNoOfChunks(){
		return SADFragmentation.NO_OF_CHUNKS_PER_FRAG;
	}
}
