package streaming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import core.DTNHost;
import core.SimClock;

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
	private TreeMap<Long, Double> chunksReceived= new TreeMap<Long, Double>();;
	private LinkedHashMap<Double, ArrayList<DTNHost>> unchoked = new LinkedHashMap<Double, ArrayList<DTNHost>>();
	private LinkedHashMap<Double, ArrayList<DTNHost>> interested = new LinkedHashMap<Double, ArrayList<DTNHost>>();
	private LinkedHashMap<Double,ArrayList<DTNHost>> availableH = new LinkedHashMap<Double,ArrayList<DTNHost>>();
	private ArrayList<Long> requested = new ArrayList<Long>();
	private long ack;
	
	
	private ArrayList<Long> couldHaveRequested = new ArrayList<Long>();
	
//	public ArrayList<DTNHost> hostNames = new ArrayList<DTNHost>();
//	public ArrayList<ArrayList<Long>> toSearch =  new ArrayList<ArrayList<Long>>();

	public HashMap<DTNHost, ArrayList<Long>> toSearch = new HashMap<DTNHost, ArrayList<Long>>();
	
	public void addChunk(long chunk){
		chunksReceived.put(chunk, SimClock.getTime());
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
//		return nrofDuplicateRequest;
		
		Collections.sort(requested);
		int ctr=0;
		long prev=requested.get(0)-1;
		for (long id: requested){
			if (id == prev){
				ctr++;
				prev=id;
			}
		}
		return ctr;
	}

	public int getNrofTimesInterrupted(){
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
	
	public ArrayList<Long> getRequested(){
		return requested;
	}

	public void addRequested(ArrayList<Long> id){
		requested.addAll(id);
	}
	
	public void setAck(long ack){
		this.ack = ack;
	}
	
	public long getAck(){
		return ack;
	}

	public void addCouldHaveRequested(ArrayList<Long> couldHaveRequested){
		this.couldHaveRequested.addAll(couldHaveRequested);
	}
	
	public ArrayList<Long> getCouldHaveRequested(){
		return couldHaveRequested;
	}
	
	
}
