/*
 * Copyright 2011 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 * The Original PRoPHET code updated to PRoPHETv2 router
 * by Samo Grasic(samo@grasic.net) - Jun 2011
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import routing.util.RoutingInfo;


import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import util.Tuple;

/**
 * Implementation of PRoPHETv2" router as described in
 * http://tools.ietf.org/html/draft-irtf-dtnrg-prophet-09
 */
public class TVProphetRouter extends ActiveRouter {

	/** delivery predictability initialization constant*/
	public static final double PEncMax = 0.5;
	/** typical interconnection time in seconds*/
	public static final double I_TYP = 1800;
	/** delivery predictability transitivity scaling constant default value */
	public static final double DEFAULT_BETA = 0.9;
	/** delivery predictability aging constant */
	public static final double GAMMA = 0.999885791;
	Random randomGenerator = new Random();

	/** Prophet router's setting namespace ({@value})*/
	public static final String PROPHET_NS = "TVProphetRouter";
	/**
	 * Number of seconds in time unit -setting id ({@value}).
	 * How many seconds one time unit is when calculating aging of
	 * delivery predictions. Should be tweaked for the scenario.*/
	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";

	/*
	 * TV_PROPHET CONSTANTS
	 */
	public static final double T_OLD = 20; //seconds
	public static final double V_OLD = 100; //KBps
	public static final double TV_ALPHA = 0.5;
	public static final double TV_GAMMA = 0.98;
	
	/**
	 * Transitivity scaling constant (beta) -setting id ({@value}).
	 * Default value for setting is {@link #DEFAULT_BETA}.
	 */
	public static final String BETA_S = "beta";

	
	/** the value of nrof seconds in time unit -setting */
	private int secondsInTimeUnit;
	/** value of beta setting */
	private double beta;

	/** delivery predictabilities */
	private Map<DTNHost, Double> preds;
	/** transmission time preds */
	private Map<DTNHost, Double> transmissionPreds; 
	/** last encouter timestamp (sim)time */
	private Map<DTNHost, Double> lastEncouterTime;

	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;

	/** historical connection duration */
	private Map<DTNHost, Double> tava;
	/**historical transmission duration */
	private Map<DTNHost, Double> vava;
	
	private static double indexSize;
	private static double transferRate = 100; //KBps. bluetooth
	private static double connectionDuration = 60; //seconds. distribution of connection duration
	private static double startTime;
	private static double endTime;
	
	private double transSize; 
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	
	public TVProphetRouter(Settings s) {
		super(s);
		Settings prophetSettings = new Settings(PROPHET_NS);
		secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
		if (prophetSettings.contains(BETA_S)) {
			beta = prophetSettings.getDouble(BETA_S);
		}
		else {
			beta = DEFAULT_BETA;
		}

		initPreds();
		initEncTimes();
		initTava();
		initVava();
		initTransmissionPreds();
	}

	/**
	 * Copyc onstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected TVProphetRouter(TVProphetRouter r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.beta = r.beta;
		initPreds();
		initEncTimes();
		initTava();
		initVava();
		initTransmissionPreds();
	}
	
	/*
	 * TV IMPLEMENTATION STARTS HERE
	 */
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
			
			//add transitive sizes per host here
			
			startTime = SimClock.getTime();
			updateTransmissionPreds(otherHost);
			
//			System.out.println("CONNECTION UP");
//			this.getMessageCollection().removeAll(this.getMessagesForConnected());
//			System.out.println("Removed buffered messages for this destination!");
		}
		else{
			DTNHost otherHost = con.getOtherNode(getHost()); //this host is same with the host in up()
			
//			this.getMessageCollection().removeAll(this.getMessagesForConnected());
//			System.out.println(getHost() + " removed remaining message for " + otherHost);
//			System.out.println("CONNECTION DOWN");
			
			endTime = SimClock.getTime();
		
			double duration = endTime-startTime;
			updateTava(otherHost, duration);
			updateVava(otherHost, con.getSpeed());
		}
		
	}
	
	private void updateTava(DTNHost host, double tCurrent){
//		System.out.println("Updating tava");
		double tOld;
		try{
			tOld= tava.get(host); 
		}catch(NullPointerException e){
			tOld = T_OLD;
		}
		
		double t = tOld*TV_ALPHA*TV_GAMMA + tCurrent*(1-TV_ALPHA*TV_GAMMA);	
		tava.put(host, t);
	}
	
	private void updateVava(DTNHost host, double vCurrent){
		double vOld;
//		System.out.println("Updating tava");
		try{
			vOld = vava.get(host);
		}catch(NullPointerException e){
			vOld = V_OLD;
		}
		
		double v = vOld*TV_ALPHA*TV_GAMMA + vCurrent*(1-TV_ALPHA*TV_GAMMA);
		vava.put(host, v);
	}
	
	private void setIndexSize(){
		indexSize = transferRate * connectionDuration;
	}
	
	public double getIndexSize(){
		return indexSize;
	}
	
	public double getTransSize(DTNHost host){
		try{
			transSize = tava.get(host) * vava.get(host) * 0.4;
		}catch(NullPointerException e){}
		return transSize;
	}
	
	/** updates transmission predictions */
	private void updateTransmissionPreds(DTNHost host){
		transmissionPreds.put(host,  getTransSize(host));
	}
	
	public double getTransmissionPreds(DTNHost host){
		return transmissionPreds.get(host);
	}
	
	/**
	 * Initializes historical contact durations
	 */
	private void initTava(){
		this.tava = new HashMap<DTNHost, Double>();
	}
	
	/**
	 * Initializes historical transmission durations
	 */
	private void initVava(){
		this.vava = new HashMap<DTNHost, Double>();
	}
	
	/**
	 * Initializes transmission predictions
	 */
	private void initTransmissionPreds(){
		this.transmissionPreds = new HashMap<DTNHost, Double>();
	}
	
	/**
	 * Initializes lastEncouterTime hash
	 */
	private void initEncTimes() {
		this.lastEncouterTime = new HashMap<DTNHost, Double>();
	}

		/**
	 * Initializes predictability hash
	 */
	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
	}

	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * PEnc
	 * PEnc(intvl) =
     *        P_encounter_max * (intvl / I_typ) for 0<= intvl <= I_typ
     *        P_encounter_max for intvl > I_typ</CODE>
	 * @param host The host we just met
	 */
	protected void updateDeliveryPredFor(DTNHost host) {
		double PEnc;
		double simTime = SimClock.getTime();
		double lastEncTime=getEncTimeFor(host);
		if(lastEncTime==0)
			PEnc=PEncMax;
		else
			if((simTime-lastEncTime)<I_TYP)
			{
				PEnc=PEncMax*((simTime-lastEncTime)/I_TYP);
			}
			else
				PEnc=PEncMax;

		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * PEnc;
		preds.put(host, newValue);
		lastEncouterTime.put(host, simTime);
	}

	/**
	 * Returns the timestamp of the last encouter of with the host or -1 if
	 * entry for the host doesn't exist.
	 * @param host The host to look the timestamp for
	 * @return the last timestamp of encouter with the host
	 */
	public double getEncTimeFor(DTNHost host) {
		if (lastEncouterTime.containsKey(host)) {
			return lastEncouterTime.get(host);
		}
		else {
			return 0;
		}
	}

		/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		}
		else {
			return 0;
		}
	}

	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 * @param host The B host who we just met
	 */
	protected void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof TVProphetRouter :
			"PRoPHETv2 only works with other routers of same type";

		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds =
			((TVProphetRouter)otherRouter).getDeliveryPreds();

		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}

//ProphetV2 max(old,new)
			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pForHost * e.getValue() * beta;
			if(pNew>pOld)
				preds.put(e.getKey(), pNew);

		}
	}

	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) /
			secondsInTimeUnit;	

		if (timeDiff == 0) {
			return;
		}

		double mult = Math.pow(GAMMA, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue()*mult);
		}

		this.lastAgeUpdate = SimClock.getTime();
	}

	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
	}

	@Override
	public void update() {
		super.update();
	
//		if (!canStartTransfer() ||isTransferring()) {
//			return; // nothing to transfer or is currently transferring
//		}

		//call exchangeUrgentMessages
		
		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
			
		}
		tryOtherMessages();
		
	}

	////////////////////UNDERSTAND THIS///////////////////////////////

	
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages =
			new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();

		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			
			DTNHost other = con.getOtherNode(getHost());
			TVProphetRouter othRouter = (TVProphetRouter)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				//if prediction for this destination on other's router >= prediction for this destination on this host
				if((othRouter.getPredFor(m.getTo()) >= getPredFor(m.getTo()))) 
				{
					messages.add(new Tuple<Message, Connection>(m,con));
				}
			}
		}

		if (messages.size() == 0) {
			return null;
		}

		// sort the message-connection tuples
		Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}

	
	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the
	 * connection (GRTRMax)
	 */
	private class TupleComparator implements Comparator
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((TVProphetRouter)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((TVProphetRouter)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple2.getKey().getTo());

			// bigger probability should come first
			if (p2-p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			}
			else if (p2-p1 < 0) {
				return -1;
			}
			else {
			
				return 1;
			}
		}
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() +
				" delivery prediction(s)");
		RoutingInfo transSize = new RoutingInfo(transmissionPreds.size() + " transmission prediction(s)");
		
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();

			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
					host, value)));
		}
		for (Entry<DTNHost, Double> t : transmissionPreds.entrySet()){
			DTNHost host = t.getKey();
			Double value = t.getValue();

			transSize.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", host, value)));
		}

		
		top.addMoreInfo(ri);
		top.addMoreInfo(transSize);
		return top;
	}

	@Override
	public MessageRouter replicate() {
		TVProphetRouter r = new TVProphetRouter(this);
		return r;
	}
	
	public void addUrgentMessage(Message m, boolean newMessage){ //butngi another parameter, pwd man didi nala magdelete mga diri kailangan
		addToMessages(m, newMessage);
	}

	public Message getStoredMessage(String id) {
		for (Message m : getMessageCollection()){
			if(m.getId().equals(id)){
				return m;
			}
		}
		return null;
	}
	
	
//	///////////////////////From FirstContactRouter
//	@ time 3585, make sure not to receive a message we already have
//	@Override
//	protected int checkReceiving(Message m, DTNHost from) {
//		int recvCheck = super.checkReceiving(m, from);
////		System.out.println("Checking if exists @@@@: "+ this.getHost()+ ": "+hasMessage(m.getId()));
//		
////		if(hasMessage(m.getId())){
////			recvCheck = DENIED_OLD; //do not recieve already received message.
////			System.out.println("denied at " + getHost() + ": "+m.getId());
////		}
//		if (recvCheck == RCV_OK) {
//			
//			/* don't accept a message that has already traversed this node */
//			if (m.getHops().contains(getHost())) {
//				System.out.println("Hop contains this host. denied.");
//				recvCheck = DENIED_OLD;
//			}
//		}
//		return recvCheck;
//	}
	
	public List<Tuple<Message, Connection>> getMessagesForConnected(){
		return super.getMessagesForConnected();		
	}
	
	public void deleteAllBuffered(){
		this.getMessageCollection().removeAll(this.getMessagesForConnected());
		System.out.println("Removed buffered messages for this destination!");
	}
	
//	public void removeSendingConnections(ArrayList<Connection> conn){
//		sendingConnections.removeAll(conn);
//		System.out.println("Remove all sendingConnections.");
//	}
	
	
	public void transferAborted(Connection con) {
		super.transferAborted(con);
//		sendingConnections.clone();
		System.out.println("Removing sending connections.");
//		sendingConnections.remove(con);
	}

//	@Override
	public void addToSendingConnections(Connection con) {
		super.addToSendingConnections(con);
	}

	@Override	
	protected void transferDone(Connection con) {
		/* don't leave a copy for the sender */
//		if (this.getMessagesForConnected().contains(con.getMessage())){
		if(getMessage(con.getMessage().getId()) !=null ){
			this.deleteMessage(con.getMessage().getId(), false);
		}
	}
}
