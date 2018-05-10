package report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import applications.StreamingApplication;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;
import core.SimClock;
import streaming.NodeProperties;

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
	public static final String SIZE_ADJUSTED = "sizeAdjusted";
	public static final String SENT_INDEX_FRAGMENT = "sentIndexFragment";
	public static final String SENT_TRANS_FRAGMENT = "sentTransFragment";
	public static final String SENT_CHUNK = "sentChunk";
	
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
			nodeProps.incNrOfDuplicateChunks();
		}
		else if (event.equalsIgnoreCase(FIRST_TIME_REQUESTED)){
			double time = (double) params;
			nodeProps.setTimeFirstRequested(time);
		}
		else if (event.equalsIgnoreCase(FIRST_TIME_RECEIVED)){
			double time = (double) params;
			nodeProps.setTimeFirstChunkReceived(time);
		}
		else if (event.equalsIgnoreCase(SENT_REQUEST)){
			int ctr= nodeProps.getNrofTimesRequested()+1;
			ArrayList<Long> id = (ArrayList<Long>) params;
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
		else if (event.equalsIgnoreCase(SIZE_ADJUSTED)){
			nodeProps.setSizeAdjustedCount(nodeProps.getSizeAdjustedCount()+1);
		}
		else if (event.equalsIgnoreCase(SENT_INDEX_FRAGMENT)){
			nodeProps.incNrOfTimesSentIndex();
		}
		else if (event.equalsIgnoreCase(SENT_TRANS_FRAGMENT)){
			nodeProps.incNrOfTimesSentTrans();
		}
		else if (event.equalsIgnoreCase(SENT_CHUNK)){
			nodeProps.incNrOfTimesSentChunk();
		}
		nodeRecord.put(host, nodeProps);
	}

	public void done(){

//		String eol = System.getProperty("line.separator");
		String chunkRecord="";
//		String nodesUnchoked;
////		String nodesInterested;
//		String nodesAvailable;
//		String chunksReceived; 
		
		String chunksCreated = "Total Chunks Created: " + createdChunks;
		write(chunksCreated);
		
		for (DTNHost h: nodeRecord.keySet()){
//				chunkRecord+= " --------" + h + "---------->" + eol 
//				 + "time_started_playing: " +  nodeRecord.get(h).getTimeStartedPlaying() + eol
//				 + "time_last_played: " + nodeRecord.get(h).getTimeLastPlayed() + eol
//				 + "number_of_times_interrupted: " + nodeRecord.get(h).getNrofTimesInterrupted() + eol
//				 + "number_of_chunks_received (total): " + nodeRecord.get(h).getNrofChunksReceived() + eol
//				 + "ack: " + nodeRecord.get(h).getAck() + eol
//				 + "chunks_requested: " + nodeRecord.get(h).getChunksReceived().keySet() + eol
//				 + "number_of_duplicate_chunks_received: " + nodeRecord.get(h).getNrofDuplicateChunks() + eol
//				 + "time_first_requested: " + nodeRecord.get(h).getTimeFirstRequested() + eol
//				 + "time_first_chunk_received: " + nodeRecord.get(h).getTimeFirstChunkReceived() + eol
//				 + "number_of_times_requested: " + nodeRecord.get(h).getNrofTimesRequested() + eol
//				 + "number_of_chunks_requested_again: " + nodeRecord.get(h).getNrofDuplicateChunks() + eol
//				 + "number_of_times_size_adjusted: " + nodeRecord.get(h).getSizeAdjustedCount() + eol
//				 + "number_of_index_fragments_sent: " + nodeRecord.get(h).getNrOfTimesSentIndex() + eol
//				 + "number_of_trans_fragments_sent: " + nodeRecord.get(h).getNrOfTimesSentIndex() + eol
//				 + "total_chunks_sent: " + nodeRecord.get(h).getNrOfTimesSentChunk() + eol
//				 + "average_wait_time:" + nodeRecord.get(h).getAverageWaitTime();
				
				double timeStartedPlaying = round(nodeRecord.get(h).getTimeStartedPlaying());
				double timeLastPlayed =round(nodeRecord.get(h).getTimeLastPlayed());
				long ack = nodeRecord.get(h).getAck();
				int numberOfTimesInterrupted = nodeRecord.get(h).getNrofTimesInterrupted();
				int numberOfChunksReceived =  nodeRecord.get(h).getNrofChunksReceived();
				int numberOfDuplicateChunksReceived = nodeRecord.get(h).getNrofDuplicateChunks();
				double averageWaitTime = round(nodeRecord.get(h).getAverageWaitTime());
				double timeFirstRequested = round(nodeRecord.get(h).getTimeFirstRequested());
				double timeFirstChunkReceived = round(nodeRecord.get(h).getTimeFirstChunkReceived());
				int numberOfTimesRequested = nodeRecord.get(h).getNrofTimesRequested();
				int numberOfChunksRequestedAgain = nodeRecord.get(h).getNrofDuplicateChunks();
				int numberOfTimesAdjusted = nodeRecord.get(h).getSizeAdjustedCount();
				int totalIndexFragmentSent = nodeRecord.get(h).getNrOfTimesSentIndex();
				int totalTransFragmentSent = nodeRecord.get(h).getNrofTimesSentTrans();
				int totalChunksSent = nodeRecord.get(h).getNrOfTimesSentChunk();
						
//				chunkRecord = String.format("%8s %s %8s %s %5s %s %4s %s %4s %s %4s %8s %s %8s %s %8s %s %4s %s %4s %s %4s %s %4s %s %4s %s %4s", 
//						timeStartedPlaying, ' ', timeLastPlayed, ' ', ack, ' ', numberOfTimesInterrupted,' ',  numberOfChunksReceived,' ',
//						numberOfDuplicateChunksReceived, ' ',averageWaitTime, ' ',timeFirstRequested, ' ',timeFirstChunkReceived, ' ', 
//						numberOfTimesRequested, ' ', numberOfChunksRequestedAgain, ' ', numberOfTimesAdjusted,' ', totalIndexFragmentSent, ' ',
//						totalTransFragmentSent,' ', totalChunksSent );
//				
				chunkRecord = String.format("%3s%s %8s %8s %5s %4s %4s %4s %8s %8s %8s %4s %4s %4s %4s %4s %4s", 
						h, ":" , timeStartedPlaying, timeLastPlayed, ack, numberOfTimesInterrupted,numberOfChunksReceived,
						numberOfDuplicateChunksReceived, averageWaitTime, timeFirstRequested, timeFirstChunkReceived,
						numberOfTimesRequested, numberOfChunksRequestedAgain,  numberOfTimesAdjusted,totalIndexFragmentSent,
						totalTransFragmentSent,totalChunksSent );
				
				write(chunkRecord);
		}
		super.done();
	}
	
	public double round(double value) {
		return (double)Math.round(value * 100)/100;
	}
	
	
}
