package fragmentation;

import java.util.ArrayList;
import java.util.List;

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
	
	private ArrayList<StreamChunk> subChunk;
	
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
	
	public long getFirstChunkID(){
		return bChunks.get(0).getChunkID();
	}
	
	public int startPosition(){
		return startPosition;
	}
	
	public void setStartPosition(int startPosition){
		this.startPosition = startPosition;
	}
	
	public void setEndPosition(int endPosition){
		this.endPosition = endPosition;
	}
	
	public int getStartPosition(){
		return startPosition;
	}
	
	public int getEndPosition(){
		return endPosition;
	}
	
	public boolean isInterrupted(){
		return (interrupted == 1? true: false);
	}
	
	public long getEndChunk(){
		return bChunks.get(bChunks.size()-1).getChunkID();
	}
	
	public double getSize(){
//		return size;
		return (StreamChunk.m480p * getNoOfChunks());
	}
	
	public int getNoOfChunks(){
		return bChunks.size();
	}
	
	public List<StreamChunk> setSubChunk(int startPosition, int endPosition){
		return bChunks.subList(startPosition, endPosition);
	}
	
	public int indexOf(long id){
		for(int i=0; i<bChunks.size(); i++){
			if (bChunks.get(i).getChunkID() == id){
				return i;
			}
		}
		return -1;
	}
	
}
