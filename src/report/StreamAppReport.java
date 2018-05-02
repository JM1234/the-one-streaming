package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import applications.StreamingApplication;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;
import core.SimClock;
import streaming.NodeProperties;
import streaming.StreamChunk;

public class StreamAppReport extends Report implements ApplicationListener{

	public static final String STARTED_PLAYING = "startedPlaying";
	public static final String LAST_PLAYED = "lastPlayed";
	public static final String INTERRUPTED = "interrupted";
	public static final String RECEIVED_CHUNK = "receivedChunk";
	public static final String RECEIVED_DUPLICATE = "receivedDuplicate";
	public static final String FIRST_TIME_REQUESTED = "firstTimeRequest";
	public static final String FIRST_TIME_RECEIVED = "firstTimeReceived";
	public static final String BROADCAST_RECEIVED = "broadcastReceived";
	public static final String RESENT_REQUEST = "resentRequest";
	public static final String SENT_REQUEST = "sentRequest";
	public static final String UNCHOKED = "unchoked";
	public static final String INTERESTED = "INTERESTED";
	public static final String CHUNK_CREATED = "chunkCreated";
	public static final String UPDATE_AVAILABLE_NEIGHBORS = "updateAvailableNeighbor";
	public static final String UPDATE_ACK = "updateAck";
	
	private HashMap<DTNHost, NodeProperties> nodeRecord = new HashMap<DTNHost, NodeProperties>();
	private int createdChunks=0;
	
	public void gotEvent(String event, Object params, Application app, DTNHost host) {
		
		if (!(app instanceof StreamingApplication)) return;
		
		NodeProperties nodeProps = nodeRecord.get(host);
		if (nodeProps == null){
			nodeRecord.put(host, new NodeProperties());
			nodeProps = nodeRecord.get(host);
		}
		
		if (event.equalsIgnoreCase(BROADCAST_RECEIVED)){
			double time=(double) params;
			nodeProps.setTimeBroadcastReceived(time);
		}
		else if (event.equalsIgnoreCase(CHUNK_CREATED)){
			createdChunks++;
		}
		else if (event.equalsIgnoreCase(STARTED_PLAYING)){
			double time=(double) params;
			nodeProps.setTimeStartedPlaying(time);
		}
		else if (event.equalsIgnoreCase(LAST_PLAYED)){
			double time=(double) params;
			nodeProps.setTimeLastPlayed(time);
		}
		else if (event.equalsIgnoreCase(INTERRUPTED)){
			int ctr = nodeProps.getNrofTimesInterrupted()+1;
			nodeProps.setNrofTimesInterrupted(ctr);
		}
		else if (event.equalsIgnoreCase(RECEIVED_CHUNK)){
			long id = (long) params;
			nodeProps.addChunk(id);
		}
		else if (event.equalsIgnoreCase(RECEIVED_DUPLICATE)){
			int ctr = nodeProps.getNrofDuplicateChunks()+1;
			nodeProps.setNrofDuplicateChunks(ctr);
		}
		else if (event.equalsIgnoreCase(FIRST_TIME_REQUESTED)){
			double time = (double) params;
			nodeProps.setTimeFirstRequested(time);
		}
		else if (event.equalsIgnoreCase(FIRST_TIME_RECEIVED)){
			double time = (double) params;
			nodeProps.setTimeFirstChunkReceived(time);
		}
		else if (event.equalsIgnoreCase(RESENT_REQUEST)){
			int ctr = nodeProps.getNrofDuplicateRequest()+1;
			nodeProps.setNrofDuplicateRequest(ctr);
		}
		else if (event.equalsIgnoreCase(SENT_REQUEST)){
			int ctr= nodeProps.getNrofTimesRequested()+1;
			long id = (long) params;
			nodeProps.addRequested(id);
			nodeProps.setNrofTimesRequested(ctr);
		}
		else if (event.equalsIgnoreCase(UNCHOKED)){
			ArrayList<DTNHost> unchokedH = (ArrayList<DTNHost>) params;
			nodeProps.updateUnchoke(SimClock.getTime(), unchokedH);
		}
		else if (event.equalsIgnoreCase(INTERESTED)){
			ArrayList<DTNHost> interestedH = (ArrayList<DTNHost>) params;
			nodeProps.updateInterested(SimClock.getTime(), interestedH);
		}
		else if (event.equals(UPDATE_AVAILABLE_NEIGHBORS)){
			ArrayList<DTNHost> availableH = (ArrayList<DTNHost>) params;
			nodeProps.updateAvailable(SimClock.getTime(), availableH);
		}
		else if (event.equals(UPDATE_ACK)){
			long ack = (long) params;
			nodeProps.setAck(ack);
		}
		else if (event.equalsIgnoreCase("THIS WILL WORK T_T.")){
			ArrayList<Long> couldHaveRequested = (ArrayList<Long>) params;
			nodeProps.addCouldHaveRequested(couldHaveRequested);
		}
		nodeRecord.put(host, nodeProps);
	}

	public void done(){
		String eol = System.getProperty("line.separator");
		String chunkRecord="";
		String nodesUnchoked;
//		String nodesInterested;
		String nodesAvailable;
		
		String chunksCreated = "Total Chunks Created: " + createdChunks;
		write(chunksCreated);
		

		for (DTNHost h: nodeRecord.keySet()){
				chunkRecord+= " --------" + h + "---------->" + eol 
				 + "time_started_playing: " +  nodeRecord.get(h).getTimeStartedPlaying() + eol
				 + "time_last_played: " + nodeRecord.get(h).getTimeLastPlayed() + eol
				 + "number_of_times_interrupted: " + nodeRecord.get(h).getNrofTimesInterrupted() + eol
				 + "number_of_chunks_received (total): " + nodeRecord.get(h).getNrofChunksReceived() + eol
				 + "ack: " + nodeRecord.get(h).getAck() + eol
				 + "number_of_duplicate_chunks_received: " + nodeRecord.get(h).getNrofDuplicateChunks() + eol
				 + "chunks received: " + nodeRecord.get(h).getChunksReceived() +eol
				 + "time_first_requested: " + nodeRecord.get(h).getTimeFirstRequested() + eol
				 + "time_first_chunk_received: " + nodeRecord.get(h).getTimeFirstChunkReceived() + eol
				 + "number_of_times_requested: " + nodeRecord.get(h).getNrofTimesRequested() + eol
				 + "number_of_chunks_requested_again: " + nodeRecord.get(h).getNrofDuplicateChunks() + eol
//				 + "all_chunks_requested: " + nodeRecord.get(h).getRequested();
				 + "could_have_requested: " + nodeRecord.get(h).getCouldHaveRequested();
				
// 				nodesUnchoked =  " Unchoked nodes: " + eol;
//				
////				Iterator<Entry<Integer, ArrayList<DTNHost>>> iterator= (Iterator<Entry<Integer, ArrayList<DTNHost>>>) nodeRecord.get(h).getUnchokeList().entrySet();
//				Set<Entry<Double,ArrayList<DTNHost>>> entryIt = nodeRecord.get(h).getUnchokeList().entrySet();
//				
//				for (Entry<Double, ArrayList<DTNHost>> entry : entryIt){
//					nodesUnchoked += "     at " + entry.getKey() + ": " + entry.getValue() + eol;
//				}
				
//				nodesInterested =  " Interested nodes: " + eol;
//				Iterator<Entry<Integer, ArrayList<DTNHost>>> iterator= (Iterator<Entry<Integer, ArrayList<DTNHost>>>) nodeRecord.get(h).getUnchokeList().entrySet();
//				Set<Entry<Double,ArrayList<DTNHost>>> entryInt = nodeRecord.get(h).getInterestedList().entrySet();
//				
//				for (Entry<Double, ArrayList<DTNHost>> entry : entryInt){
//					nodesInterested += "     at " + entry.getKey() + ": " + entry.getValue() + eol;
//				}
				
				nodesAvailable =  " Available nodes: " + eol;
				
//				Iterator<Entry<, Set<DTNHost>>> iterator= (Iterator<Entry<Integer, Set<DTNHost>>>) nodeRecord.get(h).getAvailableList().entrySet();
				Set<Entry<Double,ArrayList<DTNHost>>> entryInt = nodeRecord.get(h).getAvailableList().entrySet();
				
				for (Entry<Double, ArrayList<DTNHost>> entry : entryInt){
					nodesAvailable += "     at " + entry.getKey() + ": " + entry.getValue() + eol;
				}
				
				write(chunkRecord);
//				write(nodesUnchoked);
//				write(nodesInterested);
				write(nodesAvailable);
				chunkRecord="";
				nodesAvailable="";
				nodesUnchoked="";
//					nodesInterested="";
		}
		super.done();
	}
}
