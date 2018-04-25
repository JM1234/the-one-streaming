package streaming;

import core.DTNHost;
import core.Message;
import core.SimClock;

public class StreamChunk{

	//bitrate in kB
	public static final double m240p = 43.75; //350kbps
	public static final double m360p = 87.5; //700kbps
	public static final double m480p = 150; //1200kbps
	public static final double m720p = 312.5; //2500kbps
	public static final double m1080p = 625; //5000kbps
	public static final int CHUNK_TTL = 0; //infinite
	
	private static int duration=1;  //seconds per chunk
	
	//specific video properties
	private double byterate = m480p; //in bytes per second
	private int framerate;
	
	private String fileID;
	private long chunkID;
	private double size = (duration * byterate); // size of 1 chunk bytes
	
	private int fId=0;
	private double timeCreated; //+30 for end time?
	
	public StreamChunk(String fileID, long chunkID){
		this.fileID = fileID;
		this.chunkID = chunkID;
		timeCreated = SimClock.getTime();
	}
	
	public StreamChunk(String fileID, long chunkID, byte byterate){
		this.fileID = fileID;
		this.chunkID = chunkID;
		this.byterate = byterate;
		timeCreated = SimClock.getTime();
	}
	
	public String getFileID(){
		return fileID;
	}
	
	public long getChunkID(){
		return chunkID;
	}
	
	public double getSize(){
		return size;
	}
	
	public void setSize(double size){
		this.size = size;
	}
	
	public double getCreationTime(){
		return timeCreated;
	}
	
	public void setFragmentIndex(int fId){
		this.fId = fId;
	}
	
	public int getFragmentIndex(){
		return fId;
	}
	
	public void setByterate(int byterate){
		this.byterate=byterate;
	}
	
	public double getByterate(){
		return byterate;
	}
	
	public static int getDuration(){
		return duration;
	}
}
