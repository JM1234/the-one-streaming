package fragmentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import streaming.StreamChunk;

public class SADFragmentation {
	
	// equated to per chunk size and index level size
	public static final int NO_OF_CHUNKS_PER_FRAG = 60;
	public static final int INDEX_LEVEL =1;
	public static final int TRANS_LEVEL =2;
	
	/*
	 * bluetooth: 3200 kBps = 25Mbps
	 * wifidirect: 32000 kBps = 250Mbps
	 */
	private static int INDEX_SIZE =  3200 * 20; //kBps*seconds //must get this from setting	
	private static int id=0;

	private HashMap<Integer, Fragment> fragments;
	
	public SADFragmentation(){
		fragments = new HashMap<Integer, Fragment>();
	}
	
	public void createFragment(ArrayList<StreamChunk> chunks) {
		System.out.println("CREATED FRAGMENT "+ id + " with last chunk: " + chunks.get(chunks.size()-1));
		fragments.put(id, new Fragment(id++, chunks));
	}
	
	public void setFragmentSize(int size){
		SADFragmentation.INDEX_SIZE = size;
	}
	
	public int getFragSize(){
		return SADFragmentation.INDEX_SIZE;
	}
	
	public Fragment getFragment(int id){
		return fragments.get(id);
	}
	
	public boolean doesExist(int id){
		if(fragments.get(id) != null){
			return true;
		}
		return false;
	}
	
	public double getTimeCreated(int id){
		return fragments.get(id).getTimeCreated();
	}
	
	public ArrayList<Integer> getFragments(){
		ArrayList<Integer> fIds = new ArrayList<Integer>();
		for(int key: fragments.keySet()){
			fIds.add(fragments.get(key).getId());
		}
		Collections.sort(fIds);
		return fIds;
	}
	
	public int getCurrIndex(){
		return fragments.size();
	}
}
