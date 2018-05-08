package fragmentation;

import java.util.ArrayList;
import java.util.List;

import javax.swing.plaf.synth.SynthScrollBarUI;

import core.SimClock;
import streaming.StreamChunk;

public class Fragment {
	private int id; //index id
	private ArrayList<StreamChunk> bChunks;
	private double timeCreated;
	private String sourceId;
	private int startPosition=-1; //starting chunk id of the fragment
	private int endPosition=-1;
	private int size; //in bytes
	private boolean isComplete = false;
	
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
	
	public boolean isComplete(){
		return isComplete;
	}
	
	public long getEndChunk(){
		return bChunks.get(bChunks.size()-1).getChunkID();
	}
	
	public double getSize(){
		return (StreamChunk.m480p * getNoOfChunks());
	}
	
	public int getNoOfChunks(){
		return bChunks.size();
	}
	
	public void updateBundle(int pos, StreamChunk c){ //mainly used by watcher. adding transmission level frags
		bChunks.set(pos, c);
		
		if (pos < startPosition || startPosition==-1){
			startPosition = pos;
		}
		else if (pos>endPosition || endPosition==-1){
			endPosition = pos;
		}
		
		setIndexComplete();
	}
	
	private void setIndexComplete(){
		for (StreamChunk c: bChunks){
			if (c==null){
				return;
			}
		}
		System.out.println(" created fragment: " + id);
		isComplete = true;
	}
	
	public int indexOf(long id){
		for(int i=0; i<bChunks.size(); i++){
			if (bChunks.get(i).getChunkID() == id){
				return i;
			}
		}
		System.out.println(id + " chunk does not exist.");
		return -1;
	}
	
}
