package streaming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import core.DTNHost;
import core.SimClock;

public class NodeProperties {

	private double timeBroadcastReceived=-1;
	private double timeStartedPlaying=-1;
	private double timeLastPlayed=-1;
	private double timeFirstRequested=-1;
	private double timeFirstChunkReceived=-1;
	private double nrofTimesInterrupted=0;
	private int nrofDuplicateChunks=0;
	private int nrofDuplicateRequest=0;
	private int nrofTimesRequested=0;
	private int nrofTimesSentIndex=0;
	private int nrofTimesSentTrans=0;
	private int nrofTimesSentChunk=0;
	private int nrofFragmentsCreated=0;
	
	private TreeMap<Long, Double> chunksReceived= new TreeMap<Long, Double>();;
	private LinkedHashMap<Double, ArrayList<DTNHost>> unchoked = new LinkedHashMap<Double, ArrayList<DTNHost>>();
	private LinkedHashMap<Double, ArrayList<DTNHost>> interested = new LinkedHashMap<Double, ArrayList<DTNHost>>();
	private LinkedHashMap<Double,ArrayList<DTNHost>> availableH = new LinkedHashMap<Double,ArrayList<DTNHost>>();
	private	HashMap<Long, Double> requested = new HashMap<Long, Double>();
	private TreeMap<Long, Double> chunkWaitTime = new TreeMap<Long, Double>();
	public HashMap<DTNHost, ArrayList<Long>> toSearch = new HashMap<DTNHost, ArrayList<Long>>();
	public ArrayList<Long> chunksSkipped = new ArrayList<Long>();
	
	private long ack;
	private int sizeAdjustedCount=0;
	
	public void addChunk(long chunk){
		chunksReceived.put(chunk, SimClock.getTime());
		double waitTime = chunksReceived.get(chunk) - requested.get(chunk);
		chunkWaitTime.put(chunk, waitTime);
	}
	
	public double getAverageWaitTime(){
		double average = 0;
		for(long id : chunkWaitTime.keySet()){
			average +=chunkWaitTime.get(id);
		}
		average/=chunkWaitTime.size();
		return average;
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
	
	public void incNrOfDuplicateChunks(){
		this.nrofDuplicateChunks++;
	}

	public void setNrofDuplicateRequest(int nrofDuplicateRequest){
		this.nrofDuplicateRequest=nrofDuplicateRequest;
	}
	
	public void setNrofTimesInterrupted(double nrofTimesInterrupted){
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

	public double getNrofTimesInterrupted(){
		return nrofTimesInterrupted;
	}
	
	public TreeMap<Long, Double> getChunksReceived(){
		return chunksReceived;
	}	

	public int getNrofChunksReceived(){
		return chunksReceived.size();
	}
	
	public void updateUnchoke(double curTime, ArrayList<DTNHost> hosts){
		unchoked.put(curTime, hosts);
	}
	
	public void updateInterested(double curTime, ArrayList<DTNHost> hosts){
		interested.put(curTime, hosts);
	}
	
	public void updateAvailable(double curTime, ArrayList<DTNHost> hosts){
		availableH.put(curTime, hosts);
	}
	
	public HashMap<Double, ArrayList<DTNHost>> getUnchokeList(){
		return unchoked;
	}

	public HashMap<Double, ArrayList<DTNHost>> getInterestedList(){
		return interested;
	}
	
	public HashMap<Double, ArrayList<DTNHost>> getAvailableList(){
		return availableH;
	}
	
	public HashMap<Long, Double> getRequested(){
		return requested;
	}

	public void addRequested(ArrayList<Long> newIds){
		for(long newId: newIds){
			if (requested.containsKey(newId)){
				nrofDuplicateRequest++;
			}
			else{
				requested.put(newId, SimClock.getTime()); //put the first time this was requested
			}
		}
	}
	
	public void setAck(long ack){
		this.ack = ack;
	}
	
	public long getAck(){
		return ack;
	}
	
	public void setSizeAdjustedCount(int sizeAdjustedCount){
		this.sizeAdjustedCount = sizeAdjustedCount;
	}
	
	public int getSizeAdjustedCount(){
		return sizeAdjustedCount;
	}
	
	public void incNrOfTimesSentIndex(){
		nrofTimesSentIndex++;
	}
	
	public void incNrOfTimesSentTrans(){
		nrofTimesSentTrans++;
	}
	
	public void incNrOfTimesSentChunk(){
		nrofTimesSentChunk++;
	}

	public void incNrOfFragmentsCreated(){
		nrofFragmentsCreated++;
	}
	
	public int getNrOfTimesSentIndex(){
		return nrofTimesSentIndex;
	}
	
	public int getNrofTimesSentTrans(){
		return nrofTimesSentTrans;
	}
	
	public int getNrOfTimesSentChunk(){
		return nrofTimesSentChunk;
	}
	
	public int getNrOfFragmentsCreated(){
		return nrofFragmentsCreated;
	}
	
	public long getLastChunkReceived(){
		try{
			return chunksReceived.lastKey();
		}catch(NoSuchElementException e){
			return -1;
		}
		
	}

	public void addSkippedChunk(long id){
		chunksSkipped.add(id);
	}
	
	public int getNrOfSkippedChunks(){
		return chunksSkipped.size();
	}
}