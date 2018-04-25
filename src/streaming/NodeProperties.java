package streaming;

import java.util.ArrayList;

public class NodeProperties {

	private double timeBroadcastReceived=0;
	private double timeStartedPlaying=0;
	private double timeLastPlayed=0;
	private double timeFirstRequested=0;
	private double timeFirstChunkReceived=0;
	private int nrofTimesInterrupted=0;
	private int nrofDuplicateChunks=0;
	private int nrofDuplicateRequest=0;
	private int nrofTimesRequested=0;
	private ArrayList<Long> chunksReceived= new ArrayList<Long>();;
	
	public void addChunk(long chunk){
		chunksReceived.add(chunk);
	}
	
	public void setTimeBroadcastReceived(double timeBroadcastReceived){
		this.timeBroadcastReceived = timeBroadcastReceived;
	}
	 
	public void setTimeStartedPlaying(double timeStartedPlaying){
		this.timeStartedPlaying = timeStartedPlaying;
	}
	
	public void setTimeLastPlayed(double timeLastPlayed){
		this.timeLastPlayed = timeLastPlayed;
	}
	
	public void setTimeFirstRequested(double timeFirstRequested){
		this.timeFirstRequested=timeFirstRequested;
	}

	public void setTimeFirstChunkReceived(double timeFirstChunkReceived){
		this.timeFirstChunkReceived=timeFirstChunkReceived;
	}

	public void setNrofTimesRequested(int nrofTimesRequested){
		this.nrofTimesRequested = nrofTimesRequested;
	}
	
	public void setNrofDuplicateChunks(int nrofDuplicateChunks){
		this.nrofDuplicateChunks=nrofDuplicateChunks;
	}

	public void setNrofDuplicateRequest(int nrofDuplicateRequest){
		this.nrofDuplicateRequest=nrofDuplicateRequest;
	}
	
	public void setNrofTimesInterrupted(int nrofTimesInterrupted){
		this.nrofTimesInterrupted=nrofTimesInterrupted;
	}

	public double getTimeBroadcastReceived(){
		return timeBroadcastReceived;
	}
	 
	public double getTimeStartedPlaying(){
		return timeStartedPlaying;
	}
	
	public double getTimeLastPlayed(){
		return timeLastPlayed;
	}
	
	public double getTimeFirstRequested(){
		return timeFirstRequested;
	}
	
	public double getTimeFirstChunkReceived(){
		return timeFirstChunkReceived;
	}
	
	public int getNrofTimesRequested(){
		return nrofTimesRequested;
	}
	
	public int getNrofDuplicateChunks(){
		return nrofDuplicateChunks;
	}

	public int getNrofDuplicateRequest(){
		return nrofDuplicateRequest;
	}

	public int getNrofTimesInterrupted(){
		return nrofTimesInterrupted;
	}
	
	public ArrayList<Long> getChunksReceived(){
		return chunksReceived;
	}	

	public int getNrofChunksReceived(){
		return chunksReceived.size();
	}
}
